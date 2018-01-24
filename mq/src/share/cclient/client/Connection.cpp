/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

/*
 * @(#)Connection.cpp	1.46 10/17/07
 */ 

#include "../cshim/mqconnection-props.h"
#include "Connection.hpp"
#include "ProducerFlow.hpp"
#include "../util/UtilityMacros.h"
#include "protocol/TCPProtocolHandler.hpp"
#include "protocol/SSLProtocolHandler.hpp"
#include "../util/LogUtils.hpp"
#include "../cshim/iMQCallbackUtils.hpp"
#include "NSSInitCall.h"
#include <time.h>

PRBool Connection::nsprVersionChecked = PR_FALSE;

#if !defined(WIN32) 
extern "C" {
#endif // !defined(WIN32) 
  static void startThreadHelper(void *arg);
#if !defined(WIN32) 
}
#endif // !defined(WIN32)   

/*
 *
 */
Connection::Connection(): producerFlowTable(PR_TRUE, PR_FALSE)
{
  CHECK_OBJECT_VALIDITY();

  init();
  return;
}

/*
 *
 */
void
Connection::init()
{
  CHECK_OBJECT_VALIDITY();

  this->properties          = NULL;
  this->protocolHandler     = NULL;
  this->readChannel         = NULL;
  this->pingTimer           = NULL;
  this->flowControl         = NULL;
  this->transport           = NULL;
  this->username            = NULL;
  this->password            = NULL;
  this->authHandler         = NULL;
  this->isClosed            = PR_TRUE;
  this->isAborted           = PR_TRUE;
  this->isStopped           = PR_TRUE;
  
  this->tempDestSequence = INITIAL_TEMP_DEST_SEQUENCE;

  this->clientID         = NULL;

  // Optional properties
  this->transportConnectionType   = DEFAULT_CONNECTION_TYPE;
  this->ackTimeoutMicroSec        = msTimeoutToMicroSeconds(DEFAULT_ACK_TIMEOUT_MILLISEC);
  this->writeTimeoutMicroSec      = msTimeoutToMicroSeconds(DEFAULT_WRITE_TIMEOUT_MILLISEC);
  this->ackOnPersistentProduce    = DEFAULT_ACK_ON_PERSISTENT_PRODUCE;
  this->ackOnNonPersistentProduce = DEFAULT_ACK_ON_NON_PERSISTENT_PRODUCE;
  this->ackOnAcknowledge          = DEFAULT_ACK_ON_ACKNOWLEDGE;
  this->flowControlIsLimited      = DEFAULT_CONNECTION_FLOW_LIMIT_ENABLED;
  this->flowControlWaterMark      = DEFAULT_CONNECTION_FLOW_LIMIT;
  this->flowControlNumMessagesBeforePausing = DEFAULT_CONNECTION_FLOW_COUNT;

  this->pingIntervalSec           = DEFAULT_PING_INTERVAL_SEC; 

  this->consumerPrefetchMaxMsgCount = 
    DEFAULT_CONSUMER_PREFETCH_MAX_MESSAGE_COUNT;
  this->consumerPrefetchThresholdPercent =
    DEFAULT_CONSUMER_PREFETCH_THRESHOLD_PERCENT;

  // Callback info
  this->exceptionListenerCallback     = NULL;
  this->exceptionListenerCallbackData = NULL;  
  this->createThreadCallback          = NULL;
  this->createThreadCallbackData      = NULL;
  this->connectionID = NULL_CONN_ID;
}

/*
 *
 */
Connection::~Connection()
{
  CHECK_OBJECT_VALIDITY();

  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::~Connection() called" ));

  this->close();
  this->deleteMemberVariables();
  this->init();
}


void
Connection::setIsXA() {
  CHECK_OBJECT_VALIDITY();

  this->isXA = PR_TRUE;
}

PRBool
Connection::getIsXA() {
  CHECK_OBJECT_VALIDITY();

  return this->isXA;
}

/*
 *
 */
void
Connection::deleteMemberVariables()
{
  CHECK_OBJECT_VALIDITY();

  DELETE( this->flowControl );   // only called from Connection
  DELETE( this->readChannel );   // keeps a pointer to the protocolHandler
  DELETE( this->pingTimer );   
  DELETE( this->protocolHandler ); 
  DELETE( this->transport );     // cannot be deleted while protocolHandler is active

  DELETE( this->username );      // can safely be deleted
  DELETE( this->password );      // can safely be deleted
  DELETE( this->authHandler );   // cannot be deleted while protocolHandler is active
  DELETE( this->clientID );      // can safely be deleted

  HANDLED_DELETE( this->properties );
}

/*
 *
 */
MQError
Connection::openConnection(Properties * connectionProperties, 
                           UTF8String * usernameArg,
                           UTF8String * passwordArg,
                           UTF8String * clientIDArg,
                           MQConnectionExceptionListenerFunc exceptionListener,
                           void *              exceptionCallBackData, 
                           const MQCreateThreadFunc createThreadFunc,
                           void * createThreadFuncData)
{
  static const char FUNCNAME[] = "openConnection";
  CHECK_OBJECT_VALIDITY();

  MQError errorCode;
  this->isXA                = PR_FALSE;

  monitor.enter();
    ASSERT( this->isClosed );
    CNDCHK( !this->isClosed, MQ_CONNECTION_OPEN_ERROR );

    init();
    this->setCreateThreadCallback(createThreadFunc, createThreadFuncData);

    // We shouldn't get a null parameter, but if we do then return.
    // We're responsible for freeing these objects so do that too.
    ASSERT( connectionProperties != NULL );
    ASSERT( usernameArg != NULL );
    ASSERT( passwordArg != NULL );
    CNDCHK( connectionProperties == NULL, MQ_NULL_PTR_ARG );
    CNDCHK( usernameArg          == NULL, MQ_NULL_PTR_ARG );
    CNDCHK( passwordArg          == NULL, MQ_NULL_PTR_ARG );

    this->properties = connectionProperties;
    this->username   = usernameArg;
    this->password   = passwordArg;
    this->clientID   = clientIDArg;

    // These are owned by Connection now
    connectionProperties = NULL;
    usernameArg = NULL;
    passwordArg = NULL;
    clientIDArg = NULL;


    // Set the member variables based on the optional properties
    ERRCHK( this->setFieldsFromProperties() );
    if (STRCMP(this->transportConnectionType, TCP_CONNECTION_TYPE) == 0) {
      if (PR_GetEnv("MQ_NSS_5078380_WORKAROUND") == NULL) {
        NSPRCHK_TRACE( callOnceNSS_Init(NULL), "callOnceNSS_Init(NULL)", "mq" );
      }
    }

    // Open a socket connection to the broker.
    ERRCHK( this->connectToBroker() );

    // Create a protocol handler. 
    MEMCHK( this->protocolHandler = new ProtocolHandler(this) );

    // These must be set here, or ReadChannel will deadlock.
    this->isClosed  = PR_FALSE;
    this->isAborted = PR_FALSE;
    this->isStopped = PR_TRUE;

	this->setExceptionListenerCallback(exceptionListener, exceptionCallBackData);

    // Create a flow control handler.
    MEMCHK( this->flowControl = new FlowControl(this) );

    this->readChannel = new ReadChannel(this);
    MEMCHK( this->readChannel );
    ERRCHK( this->readChannel->getInitializationError() );
    if (this->pingIntervalSec > 0) {
      this->pingTimer = new PingTimer(this);
      MEMCHK( this->pingTimer );
      ERRCHK( this->pingTimer->getInitializationError() );
      LOG_INFO(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection ping enabled (ping interval = %d second).", this->pingIntervalSec ));
    }

    // This connects to the broker at the JMS protocol layer and
    // authenticates.
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection saying hello to the broker" ));

    errorCode = this->hello();
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), errorCode,
               "Connection::openConnection() returned from this->hello(). errorCode = %d", errorCode ));
  
    MQ_ERRCHK_TRACE( errorCode, FUNCNAME );
    
    // Set the clientID for this connection.
    if (this->clientID != NULL) {
      ERRCHK( setClientID(PR_FALSE) );
    }

    LOG_INFO(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection connected to broker" ));

  monitor.exit();
  return MQ_SUCCESS;

Cleanup:
    // Something failed so we must delete these parameters
    HANDLED_DELETE( connectionProperties );
    DELETE( usernameArg );
    DELETE( passwordArg );
    DELETE( clientIDArg );

    // Close the connection
    this->close();
  
    LOG_SEVERE(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                 MQ_COULD_NOT_CONNECT_TO_BROKER,
                 "Could not connect to broker " 
                 "because '%s' (%d).", 
                 errorStr(errorCode), errorCode ));

  monitor.exit();  
  MQ_ERROR_TRACE( FUNCNAME, errorCode );

  if (errorCode == PR_CONNECT_RESET_ERROR) {
      errorCode = MQ_COULD_NOT_CONNECT_TO_BROKER;
  }
  return errorCode;
}

/*
 *
 */
MQError
Connection::setClientID(PRBool ifnotNULL)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  
  if (this->clientID == NULL) {
     if (ifnotNULL) {
        return MQ_SUCCESS;
    }
    ERRCHK( MQ_INVALID_CLIENTID );
  }
  CNDCHK( this->clientID->length() == 0, MQ_INVALID_CLIENTID );

  // Set the clientID with the broker
  ERRCHK( protocolHandler->setClientID(clientID) );

  return MQ_SUCCESS;

Cleanup:
  MQ_ERROR_TRACE( "setClientID", errorCode );
  return errorCode;
}

/*
 *
 */
MQError
Connection::setFieldsFromProperties() 
{
  MQError errorCode = MQ_SUCCESS;
  PRInt32 intProp = 0;
  PRBool boolProp = PR_FALSE;
  const char * stringProp = NULL;
  NULLCHK( this->properties );

  // We could use more error checking on these properties to make sure
  // they are within bounds.

  
  // Option: the type of the connection (i.e. TCP or SSL).  
  if (this->properties->getStringProperty(MQ_CONNECTION_TYPE_PROPERTY,
                                          &stringProp) == MQ_SUCCESS)
  {
    this->transportConnectionType = stringProp;
  }

  // Option: the number of milliseconds to wait for an acknowledgement 
  // from the broker.  A value of 0 implies no timeout.
  if (this->properties->getIntegerProperty(MQ_ACK_TIMEOUT_PROPERTY, 
                                           &intProp) == MQ_SUCCESS)
  {
    if (intProp < 0) return MQ_UNSUPPORTED_ARGUMENT_VALUE;
    this->ackTimeoutMicroSec      = msTimeoutToMicroSeconds(intProp);
  }

  // Option: the number of milliseconds to wait for a Packet write
  // to complete the broker.  A value of 0 implies no timeout.
  if (this->properties->getIntegerProperty(MQ_WRITE_TIMEOUT_PROPERTY, 
                                           &intProp) == MQ_SUCCESS)
  {
    if (intProp < 0) return MQ_UNSUPPORTED_ARGUMENT_VALUE;
    this->writeTimeoutMicroSec      = msTimeoutToMicroSeconds(intProp);
  }

  // Option: the number of milliseconds to wait for read from portmapper 
  // A value of 0 implies no timeout.
  if (this->properties->getIntegerProperty(MQ_READ_PORTMAPPER_TIMEOUT_PROPERTY, 
                                           &intProp) == MQ_SUCCESS)
  {
    if (intProp < 0) return MQ_UNSUPPORTED_ARGUMENT_VALUE;
  }

  if (this->properties->getIntegerProperty(MQ_PING_INTERVAL_PROPERTY, 
                                           &intProp) == MQ_SUCCESS)
  {
    this->pingIntervalSec      = intProp;
  }

  // Option: whether the broker must acknowledge each message that is
  // sent by the client
  if (this->properties->getBooleanProperty(MQ_ACK_ON_PRODUCE_PROPERTY,
                                           &boolProp) == MQ_SUCCESS)
  {
    this->ackOnPersistentProduce    = boolProp;
    this->ackOnNonPersistentProduce = boolProp;
  }

  // Option: whether the broker must acknowledge each ack message that
  // is sent by the client
  if (this->properties->getBooleanProperty(MQ_ACK_ON_ACKNOWLEDGE_PROPERTY,
                                           &boolProp) == MQ_SUCCESS)
  {
    this->ackOnAcknowledge = boolProp;
  }

  // Option: flow control from broker is limited
  if (this->properties->getBooleanProperty(MQ_CONNECTION_FLOW_LIMIT_ENABLED_PROPERTY,
                                           &boolProp) == MQ_SUCCESS)
  {
    this->flowControlIsLimited = boolProp;
  }

  // Option: watermark for how many undelivered messages the client will buffer.
  // This is used only if flowControlIsLimited.
  if (this->properties->getIntegerProperty(MQ_CONNECTION_FLOW_LIMIT_PROPERTY,
                                           &intProp) == MQ_SUCCESS)
  {
    if (intProp <= 0) return MQ_UNSUPPORTED_ARGUMENT_VALUE;
    this->flowControlWaterMark = intProp;
  }

  // Option: the number of messages that the broker sends before asking the 
  // client if it should continue.
  if (this->properties->getIntegerProperty(MQ_CONNECTION_FLOW_COUNT_PROPERTY,
                                           &intProp) == MQ_SUCCESS)
  {
    if (intProp <= 0) return MQ_UNSUPPORTED_ARGUMENT_VALUE;
    this->flowControlNumMessagesBeforePausing = intProp;
  }

  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
              "Connection FlowCount=%d, FlowLimitEnabled=%d and FlowLimit=%d",
               this->flowControlNumMessagesBeforePausing,
               this->flowControlIsLimited,
               this->flowControlWaterMark ));
  
  return MQ_SUCCESS;
Cleanup:
  return errorCode;
}


/*
 *
 */
MQError
Connection::getProperties(const Properties ** const props)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( props );
  *props = NULL;
  *props = this->properties;

  return MQ_SUCCESS;
}


/*
 *
 */
MQError
Connection::createTransportHandler()
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( this->transportConnectionType != NULL );
  ASSERT( this->transport == NULL );

  if (this->transportConnectionType == NULL) {
    this->transportConnectionType = DEFAULT_CONNECTION_TYPE;
  }

  // Switch statement
  if (STRCMP(this->transportConnectionType, TCP_CONNECTION_TYPE) == 0) {
    RETURN_IF_OUT_OF_MEMORY( this->transport = new TCPProtocolHandler() );
  } 
  else if (STRCMP(this->transportConnectionType, SSL_CONNECTION_TYPE) == 0 ||
           STRCMP(this->transportConnectionType, TLS_CONNECTION_TYPE) == 0) { 
    RETURN_IF_OUT_OF_MEMORY( this->transport = new SSLProtocolHandler() );
  }
  else {
    return MQ_CONNECTION_UNSUPPORTED_TRANSPORT;
  }

  return MQ_SUCCESS;
}

/*
 *
 */
MQError
Connection::connectToBroker()
{
  CHECK_OBJECT_VALIDITY();

  // Create a transport handler (either TCP or SSL).
  RETURN_IF_ERROR( createTransportHandler() ); 

  // Connect to the broker at the transport layer
  MQError errorCode = transport->connect(this->properties);
  if (errorCode != MQ_SUCCESS) {
    DELETE(this->transport);

    LOG_SEVERE(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                 MQ_COULD_NOT_CONNECT_TO_BROKER,
                 "Transport protocol could not connect to the broker because '%s' (%d).", 
                 errorStr(errorCode), errorCode ));

    MQ_ERROR_TRACE( "connectToBroker", errorCode );
    return MQ_COULD_NOT_CONNECT_TO_BROKER;
  }

  return MQ_SUCCESS;
}

/*
 *
 */
TransportProtocolHandler *
Connection::getTransport() const
{
  CHECK_OBJECT_VALIDITY();

  return transport; 
}

/*
 *
 */
AuthenticationProtocolHandler *
Connection::getAuthenticationHandler() const
{
  CHECK_OBJECT_VALIDITY();

  return authHandler; 
}


/*
 *
 */
HandledObjectType
Connection::getObjectType() const
{
  CHECK_OBJECT_VALIDITY();

  return CONNECTION_OBJECT;
}

/*
 *
 */
void
Connection::setAuthenticationHandler(
              AuthenticationProtocolHandler * const handler) 
{
  CHECK_OBJECT_VALIDITY();

  DELETE( this->authHandler );
  this->authHandler = handler;
}

/*
 *
 */
MQError 
Connection::deleteDestination(Destination * const dest)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  RETURN_IF_ERROR( this->protocolHandler->deleteDestination(dest) );

  return MQ_SUCCESS;
}

MQError 
Connection::createDestination(Destination * const dest)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  RETURN_IF_ERROR( this->protocolHandler->createDestination(dest) );

  return MQ_SUCCESS;
}

/*
 *
 */
MQError
Connection::addProducerFlow(PRInt64 producerID, const ProducerFlow * const producerFlow)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  RETURN_ERROR_IF_NULL( producerFlow );

  Long * producerIDLong = new Long(producerID);
  if (producerIDLong != NULL) {

    producerFlowTableMonitor.enter();
    errorCode = this->producerFlowTable.addEntry(producerIDLong, (Object * const)producerFlow);
    producerFlowTableMonitor.exit();
    if (errorCode != MQ_SUCCESS) {
      DELETE( producerIDLong );
    }
  }
  else {
    errorCode = MQ_OUT_OF_MEMORY;
  }

  if (errorCode == MQ_SUCCESS) {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), MQ_SUCCESS,
               "Added producerID=%s (0x%p) to producerFlowTable",
                producerIDLong->toString(), producerFlow ));
  } else {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
               "Failed to add producerID=%s (0x%p) to producerFlowTable because '%s' (%d)",
               (producerIDLong == NULL) ? "NULL":producerIDLong->toString(), producerFlow,
               errorStr(errorCode), errorCode ));
  }

  return errorCode;

}


MQError
Connection::getProducerFlow(PRInt64 producerID, ProducerFlow ** const producerFlow)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = IMQ_SUCCESS;

  RETURN_ERROR_IF_NULL(producerFlow);


  Long producerIDLong(producerID);

  producerFlowTableMonitor.enter();
  errorCode = this->producerFlowTable.getValueFromKey(&producerIDLong,
                                             (const Object** const)producerFlow);
  if (errorCode == MQ_SUCCESS) {
    errorCode = (*producerFlow)->acquireReference();
  }
  producerFlowTableMonitor.exit();

  if (errorCode != MQ_SUCCESS) {
    LOG_FINER(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
                "Failed to get producerID=%s from producerFlowTable because '%s' (%d)",
                producerIDLong.toString(), errorStr(errorCode), errorCode ));
  } else {
   LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), MQ_SUCCESS,
               "Get producerID=%s (0x%p) from producerFlowTable success",
                producerIDLong.toString(), *producerFlow ));
  }

  return errorCode;

}

MQError
Connection::removeProducerFlow(PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;
  ProducerFlow * producerFlow = NULL;
  Long producerIDLong(producerID);

  producerFlowTableMonitor.enter();
  errorCode = this->getProducerFlow(producerID, &producerFlow);
  if (errorCode == MQ_SUCCESS) {
    errorCode = this->producerFlowTable.removeEntry(&producerIDLong);
    producerFlow->close(MQ_PRODUCER_CLOSED);
    this->releaseProducerFlow(&producerFlow);
  }
  producerFlowTableMonitor.exit();

  if (errorCode == MQ_SUCCESS) {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), IMQ_SUCCESS,
               "Removed producerID=%s (0x%p) from producerFlowTable",
                producerIDLong.toString(), producerFlow ));
  } else {
    LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
               "Failed to remove producerID=%s from producerFlowTable because '%s' (%d)",
                producerIDLong.toString(), errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

void
Connection::releaseProducerFlow(ProducerFlow ** producerFlow)
{
  ASSERT( producerFlow != NULL && (*producerFlow) != NULL );

  producerFlowTableMonitor.enter();
  if ((*producerFlow)->releaseReference() == PR_TRUE) {
    DELETE( (*producerFlow) );
  }
  producerFlowTableMonitor.exit();
  
}

MQError
Connection::closeAllProducerFlow()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Long * producerIDLong = NULL;
  ProducerFlow * producerFlow = NULL;
  
  LOG_FINEST(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), MQ_SUCCESS,
                 "In closeAllProducerFlow" ));

  producerFlowTableMonitor.enter();
  errorCode = this->producerFlowTable.keyIterationStart();
  if (errorCode == MQ_SUCCESS) {

  while (this->producerFlowTable.keyIterationHasNext()) {
    errorCode = this->producerFlowTable.keyIterationGetNext((const BasicType**)&producerIDLong);
    if (errorCode == MQ_SUCCESS) {
       errorCode = this->producerFlowTable.getValueFromKey(producerIDLong, (const Object** const)&producerFlow);
       if (errorCode == MQ_SUCCESS) {
         LOG_FINE(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), MQ_SUCCESS,
             "Closing ProducerFlow(producerID=%s)", producerIDLong->toString() ));

         producerFlow->close(MQ_BROKER_CONNECTION_CLOSED);

       } else {
         LOG_WARNING(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
            "Unable to get ProducerFlow(producerID=%s) from ProducerFlowTable because '%s' (%d)",
             producerIDLong->toString(), errorStr(errorCode), errorCode ));
       }
    } else {
      LOG_WARNING(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
        "Unable to get next ProducerFlow in ProducerFlowTable because '%s' (%d)",
         errorStr(errorCode), errorCode ));
    }
  } //while

  } else {
    LOG_WARNING(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), errorCode,
          "Unable to start iterating ProducerFlowTable because '%s' (%d)",
          errorStr(errorCode), errorCode ));
  }
  producerFlowTableMonitor.exit();

  LOG_FINEST(( CODELOC, PRODUCER_FLOWCONTROL_LOG_MASK, this->id(), MQ_SUCCESS,
                 "CloseAllProducerFlow return"));

  return errorCode;

}

/*
 *
 */
MQError 
Connection::addToReceiveQTable(PRInt64 consumerIDArg, ReceiveQueue * const receiveQ)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  RETURN_ERROR_IF_NULL( receiveQ );

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  errorCode = receiveQTable.add(consumerIDArg, receiveQ);
  if (errorCode == MQ_SUCCESS) {
      LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                  "Connection::addToReceiveQTable(%lld, 0x%p) succeeded",
                   consumerIDArg, receiveQ ));
  } else {
     LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), errorCode,
                 "Connection::addToReceiveQTable(%lld, 0x%p) failed because '%s' (%d)",
                  consumerIDArg, receiveQ, errorStr(errorCode), errorCode ));
  }
  return errorCode;
}

/*
 *
 */
MQError
Connection::addToAckQTable(PRInt64 * ackIDArg, ReceiveQueue * const receiveQ)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  RETURN_ERROR_IF_NULL( receiveQ );
  RETURN_ERROR_IF_NULL( ackIDArg );

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  errorCode = ackQTable.add(ackIDArg, receiveQ);
  if (errorCode == MQ_SUCCESS) {
      LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                  "Connection::addToAckQTable(%lld, 0x%p) succeeded",
                   *ackIDArg, receiveQ ));
  } else {
     LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), errorCode,
                 "Connection::addToAckQTable(%lld, 0x%p) failed because '%s' (%d)",
                  *ackIDArg, receiveQ, errorStr(errorCode), errorCode ));
  }
  return errorCode;
}

/*
 *
 */
MQError
Connection::addToPendingConsumerTable(PRInt64 ackIDArg, MessageConsumer * const consumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  RETURN_ERROR_IF_NULL( consumer );

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  errorCode = pendingConsumerTable.add(ackIDArg, consumer);
  if (errorCode == MQ_SUCCESS) {
      LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                  "Connection::addToPendingConsumerTable(%lld, 0x%p) succeeded",
                   ackIDArg, consumer ));
  } else {
     LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), errorCode,
                 "Connection::addToPendingConsumerTable(%lld, 0x%p) failed because '%s' (%d)",
                  ackIDArg, consumer, errorStr(errorCode), errorCode ));
  }
  return errorCode;
}


/*
 *
 */
MQError 
Connection::removeFromReceiveQTable(const PRInt64 consumerIDArg)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = receiveQTable.remove(consumerIDArg);
  if (errorCode == MQ_SUCCESS) { 
    LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::removed FromReceiveQTable(%lld) ", consumerIDArg ));
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), errorCode,
             "Connection::removed FromReceiveQTable(%lld) failed because '%s' (%d)",
              consumerIDArg, errorStr(errorCode), errorCode ));
  }
  return errorCode;

}

/*
 *
 */
MQError
Connection::removeFromAckQTable(const PRInt64 ackIDArg)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = ackQTable.remove(ackIDArg);
  if (errorCode == MQ_SUCCESS) { 
    LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::removed FromAckQTable(%lld) ", ackIDArg ));
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::removed FromAckQTable(%lld) failed because '%s' (%d)",
              ackIDArg, errorStr(errorCode), errorCode ));
  }
  return errorCode;
}

/*
 *
 */
MQError
Connection::removeFromPendingConsumerTable(const PRInt64 ackIDArg, MessageConsumer ** const consumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = pendingConsumerTable.remove(ackIDArg, consumer); 

  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                "Connection::removed FromPendingConsumerTable(%lld) ", ackIDArg ));
  } else {
  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
              "Connection::removed FromPendingConsumerTable(%lld) failed because '%s' (%d)",
               ackIDArg, errorStr(errorCode), errorCode ));
  }
  return errorCode;
}



/*
 *
 */
MQError
Connection::enqueueReceiveQPacket(const PRInt64 consumerIDArg, Packet * const packet)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( packet );
  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return ( receiveQTable.enqueue(consumerIDArg, packet) );
}



/*
 *
 */
MQError 
Connection::enqueueAckQPacket(const PRInt64 ackIDArg, Packet * const packet)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( packet ); 
  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return ( ackQTable.enqueue(ackIDArg, packet) );
}


/*
 *
 */
ProtocolHandler *
Connection::getProtocolHandler() const
{
  CHECK_OBJECT_VALIDITY();

  return protocolHandler;
}



/*
 *
 */
MQError
Connection::hello()
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( protocolHandler );

  return protocolHandler->hello(this->username, this->password);
}




/*
 *
 */
const IPAddress *
Connection::getLocalIP() const
{
  CHECK_OBJECT_VALIDITY();

  const IPAddress * ipAddr = NULL;
  if (transport != NULL) {
    transport->getLocalIP(&ipAddr);
  }

  return ipAddr;
}

/*
 *
 */
PRUint16 
Connection::getLocalPort() const
{
  CHECK_OBJECT_VALIDITY();

  PRUint16 localPort = 0;
  if (this->transport!= NULL) {
    this->transport->getLocalPort(&localPort);
  }
  return localPort;
}


/*
 *
 */
const UTF8String *
Connection::getClientID()
{

  CHECK_OBJECT_VALIDITY();

  return this->clientID;
}

/*
 *
 */
PRInt32
Connection::getTemporaryDestinationSequence()
{
  CHECK_OBJECT_VALIDITY();

  PRInt32 seq;
  monitor.enter();
    // This is what the Java code does.  But it seems like we would
    // worry about over-flow.  They might just assume that there won't
    // be that many temporary destinations created.
    seq = tempDestSequence;
    tempDestSequence++;
  monitor.exit();

  return seq;
}


/*
 * Caller is responsible to free the return string
 */
char *
Connection::getTemporaryDestinationPrefix(PRBool isQueue)
{
  MQError errorCode = MQ_SUCCESS;

  char * tempDestPrefix = NULL;
  UTF8String * destinationName = NULL;
  const char * cidCharStr = NULL;
  const UTF8String * cid = NULL;

  tempDestPrefix = new char[MAX_DESTINATION_NAME_LEN];
  CNDCHK( tempDestPrefix == NULL, MQ_OUT_OF_MEMORY );

  cid = this->getClientID();
  if (cid != NULL) {
    cidCharStr = cid->getCharStr();
  } else {
    const IPAddress * ipaddr = this->getLocalIP();
    cidCharStr = (ipaddr != NULL) ? ipaddr->toString() : "unknown";
  }

  SNPRINTF( tempDestPrefix, MAX_DESTINATION_NAME_LEN, "%s%s%s/%d",
            TEMPORARY_DESTINATION_URI_PREFIX,
            isQueue ? TEMPORARY_QUEUE_URI_NAME : TEMPORARY_TOPIC_URI_NAME,
            cidCharStr, this->getLocalPort() );

  tempDestPrefix[MAX_DESTINATION_NAME_LEN-1] = '\0'; // just to be safe
  return tempDestPrefix;

  Cleanup:
  return NULL;
}


/*
 *
 */
PRBool 
Connection::isAdminKeyUsed() const
{
  CHECK_OBJECT_VALIDITY();

  // Whenever adminKey is used, then this shouldn't be hardcoded.
  return PR_FALSE;
}

/*
 *
 */
const char * 
Connection::getTransportConnectionType() const
{
  CHECK_OBJECT_VALIDITY();

  return this->transportConnectionType;
}

/*
 *
 */
PRInt32
Connection::getAckTimeoutMicroSec() const
{
  CHECK_OBJECT_VALIDITY();

  return this->ackTimeoutMicroSec;
}

PRInt32
Connection::getWriteTimeoutMicroSec() const
{
  CHECK_OBJECT_VALIDITY();

  return this->writeTimeoutMicroSec;
}

/*
 *
 */
PRInt32
Connection::getPingIntervalSec() const
{
  CHECK_OBJECT_VALIDITY();

  return this->pingIntervalSec;
}


/*
 *
 */
PRBool
Connection::getAckOnPersistentProduce() const
{
  CHECK_OBJECT_VALIDITY();

  return this->ackOnPersistentProduce;
}

/*
 *
 */
PRBool
Connection::getAckOnNonPersistentProduce() const
{
  CHECK_OBJECT_VALIDITY();

  return this->ackOnNonPersistentProduce;
}

/*
 *
 */
PRBool
Connection::getAckOnAcknowledge() const
{
  CHECK_OBJECT_VALIDITY();

  return this->ackOnAcknowledge;
}

/*
 *
 */
PRBool
Connection::getFlowControlIsLimited() const
{
  CHECK_OBJECT_VALIDITY();

  return this->flowControlIsLimited;
}


/*
 *
 */
PRInt32
Connection::getFlowControlWaterMark() const
{
  CHECK_OBJECT_VALIDITY();

  return this->flowControlWaterMark;
}

/*
 *
 */
PRInt32
Connection::getNumMessagesBeforePausing() const
{
  CHECK_OBJECT_VALIDITY();

  return this->flowControlNumMessagesBeforePausing;
}

/*
 *
 */
PRInt32
Connection::getConsumerPrefetchMaxMsgCount() const
{
  CHECK_OBJECT_VALIDITY();

  return this->consumerPrefetchMaxMsgCount;
}

/*
 *
 */
PRFloat64
Connection::getConsumerPrefetchThresholdPercent() const
{
  CHECK_OBJECT_VALIDITY();

  return this->consumerPrefetchThresholdPercent;
}


PRBool
Connection::getIsConnectionClosed() const
{
  CHECK_OBJECT_VALIDITY();

  return isClosed;
}

/*
 *
 */
PRBool
Connection::getIsClosed() const
{
  CHECK_OBJECT_VALIDITY();

  return isClosed                || 
         isAborted               || 
         (transport == NULL)     || 
         transport->isClosed()   ||
         (protocolHandler == NULL);
}

PRInt64
Connection::id() const
{
  CHECK_OBJECT_VALIDITY();

  return this->connectionID;
}

void
Connection::setid(PRInt64 idArg) 
{
  CHECK_OBJECT_VALIDITY();
  this->connectionID = idArg;
}


// exitConnection is called whenever the connection should be aborted.
// This does a hard connection shutdown.  This does not send a GOODBYE
// message to the MQ Broker.  If an exception occurs at the broker,
// this can also be called from ReadChannel::run (see ReadChannel for
// more information).
void
Connection::exitConnection(const MQError errorCode, 
                           const PRBool calledFromReadChannel,
                           const PRBool abortConnection)
{
  CHECK_OBJECT_VALIDITY();

  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
  "Connection::exitConnection(error=%d, fromReadChannel=%d, abort=%d) waitting for exitMonitor",
             errorCode, calledFromReadChannel, abortConnection ));

  exitMonitor.enter();

  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::exitConnection() acquired exitMonitor " ));

  if (isAborted != PR_TRUE) {
    isAborted = PR_TRUE;

    if (this->pingTimer != NULL) {
      this->pingTimer->terminate();
    }

    /** This shuts down the TCP connection (but doesn't close the
     * socket).  This causes ProtocolHandler::readPacket to return,
     * which will cause ReadChannel::run to abort.
     */
    transport->shutdown();

    ackQTable.closeAll();

  }
  exitMonitor.exit();

  if (calledFromReadChannel) {
    receiveQTable.closeAll();
    this->closeAllProducerFlow();
    if (abortConnection == PR_TRUE) {
      this->notifyExceptionListener(errorCode);
    }
  } else {
    if (readChannel != NULL) {
      // Singal and wait the ReadChannel thread to exit
      readChannel->exitConnection();  
    }
  }
  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::exitConnection finished" ));
}

MQError
Connection::ping()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  if (protocolHandler->getHasActivity()) {
    ERRCHK( MQ_SUCCESS );
  }
  ERRCHK( protocolHandler->ping() );

Cleanup:
  protocolHandler->clearHasActivity();
  return errorCode;
}

/*
 *
 */
MQError
Connection::stop()
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  MQError errorCode = MQ_SUCCESS;

  monitor.enter();
  if (isStopped) {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Stopping a stopped connection" ));

    monitor.exit();
    return MQ_SUCCESS;
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Stopping the connection" ));
  }
 
  ERRCHK( protocolHandler->stop(PR_FALSE, NULL) );
  ERRCHK( this->stopSessions() );
  isStopped = PR_TRUE;

Cleanup:
  monitor.exit();

  if (errorCode != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                  MQ_CONNECTION_START_ERROR,
                  "Stopping the connection failed because '%s' (%d).", 
                  errorStr(errorCode), errorCode ));
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection stopped" ));
  }
  
  return errorCode;
}


PRBool
Connection::getIsStopped() const
{
  CHECK_OBJECT_VALIDITY();

  return this->isStopped;

}


/*
 *
 */
MQError
Connection::start()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  monitor.enter();
  if (!isStopped) {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Starting a started connection" ));

    monitor.exit();
    return MQ_SUCCESS;
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Starting the connection" ));
  }

  ERRCHK( protocolHandler->start(PR_FALSE, NULL) );
  ERRCHK( this->startSessions() );
  isStopped = PR_FALSE;

Cleanup:
  monitor.exit();
  if (errorCode != MQ_SUCCESS) {
    LOG_WARNING(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                  MQ_CONNECTION_START_ERROR,
                  "Starting the connection failed because '%s' (%d).", 
                  errorStr(errorCode), errorCode ));
  } else {
    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection started" ));
  }
  
  return errorCode;
}

/*
 *
 */
MQError
Connection::close()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  
  monitor.enter();
    if (isClosed) {
      LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                 "Skipping closing a closed connection" ));
      monitor.exit();
      return MQ_SUCCESS;
    }

    if (readChannel != NULL) {
      PRThread * readerThread = readChannel->getReaderThread();
      if (readerThread != NULL && PR_GetCurrentThread() == readerThread) {
        monitor.exit();
        return MQ_CONCURRENT_DEADLOCK;
      }
    }

    errorCode = this->stop();
    if (errorCode != MQ_SUCCESS && errorCode != MQ_BROKER_CONNECTION_CLOSED) {
      cleanupConnection();
      monitor.exit();
      return errorCode;
    }
  
    // Close all the sessions (this will close all producers and consumers too)
    errorCode = this->closeSessions();
    if (errorCode != MQ_SUCCESS && errorCode != MQ_BROKER_CONNECTION_CLOSED) {
      cleanupConnection();
      monitor.exit();
      return errorCode;
    }

    // Send GOODBYE to broker if we can
    if ((protocolHandler != NULL) && 
        (readChannel != NULL)     && 
        (readChannel->getInitializationError() == MQ_SUCCESS))
    {
      this->protocolHandler->goodBye(PR_TRUE);
    }

    // Shutdown the reader thread and the socket
    this->exitConnection(MQ_SUCCESS, PR_FALSE, PR_FALSE);

    // Close the transport connection to the broker.  This closes the socket.
    if (transport != NULL) {
      transport->close();
    }

    LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
               "Connection closed" ));
    
    this->isClosed = PR_TRUE;
    monitor.exit();

  return MQ_SUCCESS;
}

//must only be called in monitor
void
Connection::cleanupConnection() 
{
    // Shutdown the reader thread and the socket
    this->exitConnection(MQ_SUCCESS, PR_FALSE, PR_FALSE);

    // Close the transport connection to the broker.  This closes the socket.
    if (transport != NULL) {
      transport->close();
    }
}

MQError
Connection::getMetaData(Properties ** const metaProperties)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Properties * props = NULL;

  NULLCHK(metaProperties);
  *metaProperties = NULL; 

  MEMCHK( props = new Properties );
  ERRCHK( props->getInitializationError() );

  ERRCHK( props->setStringProperty( MQ_NAME_PROPERTY, PRODUCT_NAME) );
  ERRCHK( props->setStringProperty( MQ_VERSION_PROPERTY, PRODUCT_VERSION) );
  ERRCHK( props->setIntegerProperty( MQ_MAJOR_VERSION_PROPERTY, PRODUCT_MAJOR_VERSION) );
  ERRCHK( props->setIntegerProperty( MQ_MINOR_VERSION_PROPERTY, PRODUCT_MINOR_VERSION) );
  ERRCHK( props->setIntegerProperty( MQ_MICRO_VERSION_PROPERTY, PRODUCT_MICRO_VERSION) );
  ERRCHK( props->setIntegerProperty( MQ_SERVICE_PACK_PROPERTY,  PRODUCT_SERVICE_PACK) );
  ERRCHK( props->setIntegerProperty( MQ_UPDATE_RELEASE_PROPERTY, PRODUCT_UPDATE_RELEASE) );

  *metaProperties = props;

  return MQ_SUCCESS;
Cleanup:
  DELETE( props );
  MQ_ERROR_TRACE( "getMetaData", errorCode );
  return errorCode;
}


/*
 *
 */
MQError 
Connection::setExceptionListenerCallback(
        const MQConnectionExceptionListenerFunc exceptionListenerFunc,
                                         void * exceptionListenerFuncData)
{  
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  this->exceptionListenerCallback     = exceptionListenerFunc;
  this->exceptionListenerCallbackData = exceptionListenerFuncData;

  return MQ_SUCCESS;
}

/** Set the callback that allows the user to create the threads allocated by 
 *  this connection. */
MQError 
Connection::setCreateThreadCallback(const MQCreateThreadFunc createThreadFunc,
                                    void * createThreadFuncData)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  this->createThreadCallback     = createThreadFunc;
  this->createThreadCallbackData = createThreadFuncData;

  return MQ_SUCCESS;
}

/*
 *
 */
MQError
Connection::addSession(const Session * const session)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  sessionsMonitor.enter();
    errorCode = sessionVector.add((void*)session);
  sessionsMonitor.exit();
    
  return errorCode;
}

/*
 *
 */
MQError
Connection::removeSession(const Session * const session)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  sessionsMonitor.enter();
    errorCode = sessionVector.remove((void*)session);
  sessionsMonitor.exit();
    
  return errorCode;
}

/*
 *
 */
MQError
Connection::getSession(const PRInt32 index, Session ** const session)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  sessionsMonitor.enter();
    errorCode = sessionVector.get(index, (void**)session);
  sessionsMonitor.exit();
    
  return errorCode;
}

/*
 *
 */
PRInt32
Connection::numSessions()
{
  CHECK_OBJECT_VALIDITY();

  PRInt32 size = 0;
  
  sessionsMonitor.enter();
    size = (PRInt32)sessionVector.size();
  sessionsMonitor.exit();
    
  return size;
}

/*
 *
 */
MQError
Connection::createSession(const PRBool     isTransacted, 
                          const AckMode    ackMode,
                          const ReceiveMode receiveMode,
                          const PRBool     isXASession,
                          MQMessageListenerBAFunc beforeMessageListener,
                          MQMessageListenerBAFunc afterMessageListener,
                          void *  callbackData,
                          Session ** const session)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  if (isXASession == PR_TRUE && this->getIsXA() == PR_FALSE) {
      return MQ_NOT_XA_CONNECTION; 
  }
  monitor.enter();
    CNDCHK( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );
    NULLCHK( session );
    *session = NULL;

    // Create the session
    if (isXASession == PR_FALSE) {
        MEMCHK( *session = new Session(this, isTransacted, ackMode, receiveMode) );
    } else {
        MEMCHK( *session = new XASession(this, receiveMode, 
                      beforeMessageListener, afterMessageListener, callbackData) );
    }
    ERRCHK( (*session)->getInitializationError() );
   
    
    // Add it to the list of sessions
    ERRCHK( addSession(*session) );
  monitor.exit();

  return MQ_SUCCESS;

Cleanup:
  monitor.exit();
  HANDLED_DELETE(*session);

  LOG_WARNING(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                MQ_CONNECTION_CREATE_SESSION_ERROR,
                 "Creating a session failed because '%s' (%d).", 
                 errorStr(errorCode), errorCode ));

  return errorCode;
}

/*
 *
 */
MQError
Connection::startSessions()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS, error = MQ_SUCCESS;

  Session * session = NULL;

  sessionsMonitor.enter();
  for (int i = 0; i < this->numSessions(); i++) {
    session = NULL;
    this->getSession(i, &session);
    ASSERT( session != NULL );

    if (session != NULL) {
       error =  session->start();
  	   if (error != MQ_SUCCESS) errorCode = error;
    }
  }
  sessionsMonitor.exit();
  return  errorCode;
}

MQError
Connection::stopSessions()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS, error = MQ_SUCCESS;

  Session * session = NULL;

  sessionsMonitor.enter();
  for (int i = 0; i < this->numSessions(); i++) {
    session = NULL;
    this->getSession(i, &session);
    ASSERT( session != NULL );

    if (session != NULL) {
      error =  session->stop();
      if (error != MQ_SUCCESS) errorCode = error;
    }
  }
  sessionsMonitor.exit();
  return errorCode;
}


/*
 *
 */
MQError
Connection::closeSessions()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS, error = MQ_SUCCESS;

  Session * session = NULL;

  sessionsMonitor.enter();
  for (int i = 0; i < this->numSessions(); i++) {
    session = NULL;
    this->getSession(i, &session);
    ASSERT( session != NULL );
    if (session != NULL) {
      error = session->close(PR_FALSE);
      if (error != MQ_SUCCESS) errorCode = error;
    }
  }
  sessionsMonitor.exit();
  return errorCode;
}


/*
 * This function has not been tested.
 */
void
Connection::notifyExceptionListener(const MQError error) const
{
  CHECK_OBJECT_VALIDITY();

  if (exceptionListenerCallback != NULL) {
    invokeExceptionListenerCallback(this, error, this->exceptionListenerCallback,
                                    this->exceptionListenerCallbackData);
  }
}



/*
 *
 */
MQError
Connection::startThread(Runnable * const threadToRun)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( threadToRun );

  if (createThreadCallback == NULL) {
    PRThread * thread = PR_CreateThread(PR_SYSTEM_THREAD, 
                                        startThreadHelper, 
                                        threadToRun, 
                                        PR_PRIORITY_NORMAL, 
                                        PR_GLOBAL_THREAD, 
                                        PR_UNJOINABLE_THREAD, 
                                        0);
    if (thread == NULL) {
      LOG_SEVERE(( CODELOC, CONNECTION_LOG_MASK, this->id(), 
                    MQ_COULD_NOT_CREATE_THREAD,
                    "Creating a thread failed, error=%d, oserror=%d", PR_GetError(), PR_GetOSError() ));
    } else {
      PRThreadScope scope = PR_GetThreadScope(thread);

      if (scope == PR_GLOBAL_THREAD) {
        LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection started a thread with thread scope %s", "GLOBAL" ));
      } else if (scope == PR_GLOBAL_BOUND_THREAD) {
        LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection started a thread with thread scope %s", "GLOBAL_BOUND" ));
      } else {
        LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection started a thread with thread scope %s", "LOCAL" ));
      }
    }
    RETURN_ERROR_IF( thread == NULL, MQ_COULD_NOT_CREATE_THREAD );
  } else {
    PRBool success;
    success = invokeCreateThreadCallback(startThreadHelper, threadToRun,
                                         createThreadCallback,
                                         createThreadCallbackData);
    RETURN_ERROR_IF( !success, MQ_COULD_NOT_CREATE_THREAD );
  }


  return MQ_SUCCESS;
}

/*
 *
 */
static void 
startThreadHelper(void *arg)
{
  Runnable * runnable = (Runnable*)arg;
  if (runnable != NULL) {
    runnable->run();
  }
}

/*
 * static method 
 * Caller is responsible to free the return string
 */
char *
Connection::getUserAgent()
{
  MQError errorCode = MQ_SUCCESS;
  char * userAgent = NULL;

  userAgent = new char[SYS_INFO_BUFFER_LENGTH*4+4];

  CNDCHK( userAgent == NULL, MQ_OUT_OF_MEMORY );

  SNPRINTF(userAgent, SYS_INFO_BUFFER_LENGTH, "%s/%s (C; ", PRODUCT_NAME, PRODUCT_VERSION);
  NSPRCHK( PR_GetSystemInfo(PR_SI_SYSNAME, &userAgent[STRLEN(userAgent)], SYS_INFO_BUFFER_LENGTH) );
  STRCAT( userAgent, " " );
  NSPRCHK( PR_GetSystemInfo(PR_SI_RELEASE, &userAgent[STRLEN(userAgent)], SYS_INFO_BUFFER_LENGTH) );
  STRCAT( userAgent, " " );
  NSPRCHK( PR_GetSystemInfo(PR_SI_ARCHITECTURE, &userAgent[STRLEN(userAgent)], SYS_INFO_BUFFER_LENGTH) );
  STRCAT( userAgent, ")" );
  return userAgent;

Cleanup:
  //XXX logging errorCode
  return  NULL;
}


MQError
Connection::versionCheck(PRBool mq)
{
  MQError errorCode = MQ_SUCCESS;

  if (Connection::nsprVersionChecked) return MQ_SUCCESS;

  if (PR_VersionCheck(PR_VERSION) == PR_FALSE) { 
    LOG_SEVERE(( CODELOC, CONNECTION_LOG_MASK, NULL_CONN_ID, MQ_INCOMPATIBLE_LIBRARY,
               "The version of the NSPR library linked to by this application is not compatible with the version supported by the MQ API (NSPR %s)", PR_VERSION ));
    return MQ_INCOMPATIBLE_LIBRARY;
  }
  Connection::nsprVersionChecked = PR_TRUE;

  return errorCode;
}


/*
 *
 */
MQError 
Connection::registerMessageProducer(const Session * const session, 
                                    const Destination * const destination,
                                    PRInt64 * producerID)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;
  ProducerFlow * producerFlow = NULL;

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  NULLCHK( session );
  NULLCHK( producerID );

  MEMCHK( producerFlow =  new ProducerFlow() );
  ERRCHK( protocolHandler->registerMessageProducer(session, destination, producerFlow) );
  *producerID = producerFlow->getProducerID();
  ERRCHK( this->addProducerFlow(*producerID, producerFlow) );
  return MQ_SUCCESS;
Cleanup:
  DELETE( producerFlow );
  return errorCode;
}

/*
 *
 */
MQError
Connection::unregisterMessageProducer(PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  errorCode =  protocolHandler->unregisterMessageProducer(producerID);
  if (errorCode == MQ_SUCCESS || errorCode == MQ_BROKER_CONNECTION_CLOSED) {
    errorCode = this->removeProducerFlow(producerID); 
  }
  ERRCHK( errorCode );
  return MQ_SUCCESS;

Cleanup:
  return errorCode;
}


/*
 *
 */
MQError 
Connection::registerMessageConsumer(MessageConsumer * const messageConsumer)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  //PRBool addedToTable = PR_FALSE;

  CNDCHK( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );
  NULLCHK( messageConsumer );

  /*
  ERRCHK( this->addToReadQTable(messageConsumer->getConsumerID(),
                                messageConsumer->getReceiveQueue()) );
  addedToTable = PR_TRUE;
  LOG_FINER(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
              "Connection::registerMessageConsumer(%d, 0x%p) added consumer to readQueue",
              messageConsumer->getConsumerID(), messageConsumer->getReceiveQueue() ));

  */
  ERRCHK( protocolHandler->registerMessageConsumer(messageConsumer) );

  {
  Long consumerIDLong(messageConsumer->getConsumerID()); 
  LOG_FINER(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
              "Connection::registerMessageConsumer(%s, 0x%p) successful", 
              consumerIDLong.toString() ));
  }
  return MQ_SUCCESS;

Cleanup:
  /*
  if (addedToTable) {
    this->removeFromReadQTable(messageConsumer->getConsumerID());
    LOG_FINER(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                "Connection::registerMessageConsumer(%d, 0x%p) failed",
                messageConsumer->getConsumerID(), messageConsumer->getReceiveQueue() ));
  }
  */
  LOG_FINER(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
                "Connection::registerMessageConsumer(0x%p) failed", messageConsumer ));

  return errorCode;
}


/*
 *
 */
MQError 
Connection::unregisterMessageConsumer(MessageConsumer * const messageConsumer)
{

  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  MQError ecode = MQ_SUCCESS;
  Long consumerIDLong;

  NULLCHK( messageConsumer );
  consumerIDLong.setValue(messageConsumer->getConsumerID());

  errorCode = protocolHandler->unregisterMessageConsumer(messageConsumer); 
  ecode = this->removeFromReceiveQTable(messageConsumer->getConsumerID()); 
  ERRCHK( errorCode );

  LOG_FINER(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
              "Connection::unregisterMessageConsumer(0x%p) succeeded.  consumerID=%s", 
              messageConsumer, consumerIDLong.toString() ));

  return MQ_SUCCESS;
Cleanup:
  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->id(), MQ_SUCCESS,
             "Connection::unregisterMessageConsumer(0x%p) failed because %d.  consumerID=%s", 
             messageConsumer,
             errorCode,
             (messageConsumer == NULL) ? "-1" : consumerIDLong.toString() ));
  return errorCode;
}

/*
 *
 */
MQError 
Connection::unsubscribeDurableConsumer(const UTF8String * const durableName)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;

  CNDCHK( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );
  NULLCHK( durableName );

  ERRCHK( protocolHandler->unsubscribeDurableConsumer(durableName) );

  return MQ_SUCCESS;
Cleanup:

  return errorCode;
}

/*
 *
 */
MQError 
Connection::writeJMSMessage(const Session * const session,
                            Message * const message, PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;
  ProducerFlow * producerFlow = NULL;

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  ERRCHK( this->getProducerFlow(producerID, &producerFlow) );

  errorCode = producerFlow->checkFlowControl(message); 
  this->releaseProducerFlow(&producerFlow);
  ERRCHK( errorCode );

  ERRCHK( protocolHandler->writeJMSMessage(session, message) );

Cleanup:
  return errorCode;
}

/*
 *
 */
MQError
Connection::acknowledge(const Session * const session,
                        const PRUint8 * const ackBlock,
                        const PRInt32 ackBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->acknowledge(session,
                                      ackBlock, ackBlockSize); 
}

/*
 *
 */
MQError
Connection::acknowledgeExpired(const PRUint8 * const ackBlock,
                               const PRInt32 ackBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->acknowledgeExpired(ackBlock, ackBlockSize); 
}


MQError
Connection::redeliver(const Session * const session, PRBool setRedelivered,
                      const PRUint8 * const redeliverBlock,
                      const PRInt32   redeliverBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->redeliver(session, setRedelivered, 
                                    redeliverBlock, redeliverBlockSize); 
}


MQError
Connection::registerSession(Session * session)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->registerSession(session);
}


MQError
Connection::unregisterSession(PRInt64  sessionID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->unregisterSession(sessionID);
}

MQError
Connection::startSession(const Session  * session)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->start(PR_TRUE, session);
}


MQError
Connection::stopSession(const Session  * session)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->stop(PR_TRUE, session);
}


MQError
Connection::startTransaction(PRInt64 sessionID, PRInt64 * transactionID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->startTransaction(sessionID, PR_TRUE, NULL, 0L, transactionID); 
}


MQError
Connection::startTransaction(XID *xid, long xaflags, PRInt64 * transactionID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->startTransaction((PRInt64)0, PR_FALSE, xid, xaflags, transactionID); 
}


MQError
Connection::endTransaction(PRInt64 transactionID, XID *xid, long xaflags)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->endTransaction(transactionID, xid, xaflags);
}


MQError
Connection::prepareTransaction(PRInt64 transactionID, XID *xid)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->prepareTransaction(transactionID, xid);
}


MQError
Connection::commitTransaction(PRInt64 transactionID,  PRInt32 * const replyStatus)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->commitTransaction(transactionID, NULL, 0L, replyStatus);
}

MQError
Connection::commitTransaction(PRInt64 transactionID,  XID *xid, long xaflags,
                              PRInt32 * const replyStatus)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->commitTransaction(transactionID, xid, xaflags, replyStatus);
}


MQError
Connection::rollbackTransaction(PRInt64 transactionID)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->rollbackTransaction(transactionID, NULL, PR_FALSE);
}


MQError
Connection::rollbackTransaction(PRInt64 transactionID, XID *xid)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->rollbackTransaction(transactionID, xid, PR_TRUE);
}


MQError 
Connection::recoverTransaction(long xaflags, ObjectVector ** const xidv)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->recoverTransaction(xaflags, xidv);
}


/*
 *
 */
MQError
Connection::resumeFlow()

{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF( this->getIsClosed(), MQ_BROKER_CONNECTION_CLOSED );

  return protocolHandler->resumeFlow(PR_FALSE, 0);
}


/*
 *
 */
void 
Connection::messageReceived()
{
  CHECK_OBJECT_VALIDITY();

  if (this->getIsClosed()) {
    return;
  }
  
  flowControl->messageReceived();
}

/*
 *
 */
void 
Connection::messageDelivered()
{
  CHECK_OBJECT_VALIDITY();

  if (this->getIsClosed()) {
    return;
  }
  
  flowControl->messageDelivered();
}

/*
 *
 */
void
Connection::requestResume()
{
  CHECK_OBJECT_VALIDITY();

  if (this->getIsClosed()) {
    return;
  }
  
  flowControl->requestResume();
}


/*
 *
 */
MQError
Connection::requestResumeConsumer(PRInt64 consumerID)
{
  CHECK_OBJECT_VALIDITY();

  if (this->getIsClosed()) { //XXX amy 
    return MQ_SUCCESS;
  }

  return protocolHandler->resumeFlow(PR_TRUE, consumerID);
}

/**
 *
 */
PRInt32 
Connection::msTimeoutToMicroSeconds(const PRInt32 timeoutMS)
{
  if (timeoutMS == 0) {
    return TRANSPORT_NO_TIMEOUT;
  } else {
    return 1000 * timeoutMS;
  }
}



/*
 *
 */
static const char * CONFIG_FILE =  INPUT_FILE_DIR "config/connection.properties";
MQError
Connection::test(const PRInt32 simultaneousConnections)
{
  MQError errorCode = MQ_SUCCESS;
  Connection * connections = new Connection[simultaneousConnections];
  Properties * connectionProperties = NULL;
  PRBool deleteProperties = PR_TRUE;
  UTF8String * usernameStr = NULL;
  UTF8String * passwordStr = NULL;
  int i;
  MEMCHK( connections );

  for (i = 0; i < simultaneousConnections; i++) {
    MEMCHK( connectionProperties = new Properties );
    ERRCHK( connectionProperties->readFromFile(CONFIG_FILE) );
    
    static const char * username = "guest";
    static const char * password = "guest";

    usernameStr = new UTF8String(username);
    passwordStr = new UTF8String(password);
    if ((usernameStr == NULL) || (passwordStr == NULL)) {
      DELETE( usernameStr );
      DELETE( passwordStr );
      ERRCHK( MQ_OUT_OF_MEMORY );
    }

    deleteProperties = PR_FALSE; // connectionProperties is owned by connections[i] now
    ERRCHK( connections[i].openConnection(connectionProperties, 
                                          usernameStr,
                                          passwordStr,
                                          NULL, NULL, NULL,
                                          NULL,
                                          NULL) );

    Session * session = NULL;
    ERRCHK( connections[i].createSession(PR_FALSE, 
                                         CLIENT_ACKNOWLEDGE, 
                                         SESSION_SYNC_RECEIVE,
                                         PR_FALSE,
                                         NULL, NULL, NULL,
                                         &session) );

    // Test the session
    Session::test(session);
  }

  for (i = 0; i < simultaneousConnections; i++) {
    ERRCHK( connections[i].close() );
  }
  

Cleanup:
  if (deleteProperties) {
    HANDLED_DELETE( connectionProperties ); 
  }
  DELETE_ARR( connections );

  return errorCode;
}


