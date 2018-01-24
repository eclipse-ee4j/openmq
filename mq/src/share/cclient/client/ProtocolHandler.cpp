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
 * @(#)ProtocolHandler.cpp	1.31 10/23/07
 */ 

#include "ProtocolHandler.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"
#include "Connection.hpp"
#include "../io/PacketType.hpp"
#include "../io/Status.hpp"
#include "iMQConstants.hpp"
#include "ReceiveQueue.hpp"
#include "DestType.hpp"
#include "auth/JMQDigestAuthenticationHandler.hpp"
#include "auth/JMQBasicAuthenticationHandler.hpp"
#include "ProducerFlow.hpp"
#include "XIDObject.hpp"


/*
 *
 */
ProtocolHandler::ProtocolHandler(Connection * const connectionArg)
{
  CHECK_OBJECT_VALIDITY();

  init();

  ASSERT( connectionArg != NULL );
  this->connection = connectionArg;
  if (connectionArg == NULL) {
    return;
  }

  writeTimeout = this->connection->getWriteTimeoutMicroSec(); 

  // the connection is closed if the transport is NULL or is closed
  isClosed = (this->connection->getTransport() == NULL)     || 
              this->connection->getTransport()->isClosed();
}

/*
 *
 */
void
ProtocolHandler::init()
{
  CHECK_OBJECT_VALIDITY();

  isClosed        = PR_TRUE;
  isAuthenticated = PR_FALSE;
  connection      = NULL;
  clientIDSent    = PR_FALSE;
  hasActivity     = PR_FALSE;
}

/*
 *
 */
ProtocolHandler::~ProtocolHandler()
{
  CHECK_OBJECT_VALIDITY();

  isClosed        = PR_TRUE;
  isAuthenticated = PR_FALSE;
  connection      = NULL;
  clientIDSent    = PR_FALSE;
  hasActivity     = PR_FALSE;
}


/*
 *
 */
PRBool
ProtocolHandler::getHasActivity() const
{
  CHECK_OBJECT_VALIDITY();

  return hasActivity;
}


/*
 *
 */
void
ProtocolHandler::clearHasActivity()
{
  CHECK_OBJECT_VALIDITY();
  
  hasActivity = PR_FALSE;  
}


PRBool
ProtocolHandler::isConnectionClosed() const
{
  CHECK_OBJECT_VALIDITY();

  return isClosed;
}



/*
 *
 */
iMQError
ProtocolHandler::hello(const UTF8String * const username, 
                       const UTF8String * const password)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode;
  Packet * helloPacket   = NULL;
  Packet * authReqPacket = NULL;
  Properties * packetProps = NULL;
  char * userAgent = NULL; 

  RETURN_ERROR_IF_NULL( username );
  RETURN_ERROR_IF_NULL( password );

  // The connection is not authenticated yet
  this->isAuthenticated = PR_FALSE;

  // Create a new hello packet
  MEMCHK( helloPacket = new Packet() );
  helloPacket->setPacketType(PACKET_TYPE_HELLO);

  // Set the hello properties
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setIntegerProperty(IMQ_PROTOCOL_LEVEL_PROPERTY, PROTOCOL_VERSION) );
  ERRCHK( packetProps->setStringProperty(IMQ_PRODUCT_VERSION_PROPERTY, PRODUCT_VERSION) );
  ERRCHK( packetProps->setIntegerProperty(IMQ_SIZE_PROPERTY,
                              connection->getNumMessagesBeforePausing()) );

  userAgent =  Connection::getUserAgent();
  CNDCHK( userAgent == NULL, MQ_NULL_PTR_ARG ); //XXX add a error code
  ERRCHK( packetProps->setStringProperty(IMQ_USER_AGENT_PROPERTY, userAgent) );
  DELETE_ARR( userAgent );

  helloPacket->setProperties(packetProps);
  packetProps = NULL; // resumePacket is responsible now
  
  // Send the hello packet
  ERRCHK( writePacketWithAck(helloPacket,
                             PR_TRUE,
                             PACKET_TYPE_HELLO_REPLY,
                             &authReqPacket) );
  DELETE( helloPacket );

  // Make sure the second acknowledgement packet was an AUTHENTICATE_REQUEST
  ASSERT( authReqPacket != NULL );
  CNDCHK( authReqPacket->getPacketType() != PACKET_TYPE_AUTHENTICATE_REQUEST,
          IMQ_UNEXPECTED_ACKNOWLEDGEMENT );

  // Authenticate with the broker
  ERRCHK( authenticate(authReqPacket, username, password) );
  this->isAuthenticated = PR_TRUE;

  DELETE( authReqPacket );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, "Said hello to the broker" ));

  return IMQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( helloPacket );
  DELETE( authReqPacket );
  DELETE_ARR( userAgent );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_HELLO_FAILED,
                "Failed to say hello to broker because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::goodBye(const PRBool expectReply)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode     = IMQ_SUCCESS;
  Packet * goodbyePacket = NULL;
  Properties * packetProps = NULL;
  PRInt32 statusCode = 0;
  MEMCHK( goodbyePacket = new Packet() );

  goodbyePacket->setPacketType(PACKET_TYPE_GOODBYE);
  if (clientIDSent || LL_IS_ZERO(this->connection->id()) == 0) {
    MEMCHK( packetProps = new Properties(PR_TRUE) );
    ERRCHK( packetProps->getInitializationError() );
    if (LL_IS_ZERO(this->connection->id()) == 0) {
      ERRCHK( packetProps->setLongProperty(IMQ_CONNECTIONID_PROPERTY, this->connection->id()) );
    }
    if (clientIDSent) { 
      ERRCHK( packetProps->setBooleanProperty(IMQ_BLOCK_PROPERTY, PR_TRUE) );
    }
    goodbyePacket->setProperties(packetProps);
    packetProps = NULL;
  }

  if (expectReply) {
    ERRCHK( writePacketWithStatus(goodbyePacket, PACKET_TYPE_GOODBYE_REPLY, 
                               &statusCode) );
    ERRCHK( Status::toIMQError(statusCode) );							   
  } else {
    ERRCHK( writePacketNoAck(goodbyePacket) );
  }

Cleanup:
  DELETE( goodbyePacket );
  HANDLED_DELETE( packetProps );

  if (errorCode == IMQ_SUCCESS) {
    LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
               IMQ_SUCCESS, "Saying goodbye to the broker" ));
  } else if (errorCode == IMQ_BROKER_CONNECTION_CLOSED) {
    LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_GOODBYE_FAILED,
                  "Failed to say goodbye to the broker because '%s' (%d)", 
                  errorStr(errorCode), errorCode ));
  } else {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_GOODBYE_FAILED,
                  "Failed to say goodbye to the broker because '%s' (%d)", 
                  errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

/*
 *
 */
MQError
ProtocolHandler::ping()
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode   = MQ_SUCCESS;
  Packet * packet = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_PING);

  ERRCHK( writePacketNoAck(packet) );

Cleanup:
  DELETE( packet );

  if (errorCode == MQ_SUCCESS) {
    LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), MQ_SUCCESS,
                 "Sent PING to the broker" ));
  } else {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  errorCode,
                  "Failed to ping broker connection because '%s' (%d)",
                  errorStr(errorCode), errorCode ));
  }

  return errorCode;
}

/*
 *
 */
iMQError
ProtocolHandler::start(PRBool startSession, const Session * session)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode   = IMQ_SUCCESS;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_START);

  if (startSession == PR_TRUE) {
  NULLCHK( session );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, session->getSessionID()) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now
  }

  ERRCHK( writePacketNoAck(packet) );

Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  if (errorCode == IMQ_SUCCESS) {
    LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
               "Starting connection to the broker" ));
  } else {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_START_FAILED,
                  "Failed to start broker connection because '%s' (%d)", 
                  errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::stop(PRBool stopSession, const Session * session)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode   = IMQ_SUCCESS;
  Packet * packet  = NULL;
  Properties * packetProps = NULL;
  PRInt32      statusCode  = 0;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_STOP);

  if (stopSession == PR_TRUE) {
  NULLCHK( session );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, session->getSessionID()) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now
  }

  ERRCHK( writePacketWithStatus(packet, PACKET_TYPE_STOP_REPLY, &statusCode) );
  ERRCHK( Status::toIMQError(statusCode) );

Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  if (errorCode == IMQ_SUCCESS) {
    LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
               "Stopping connection to the broker" ));
  } else {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_STOP_FAILED,
                  "Failed to stop broker connection because '%s' (%d)", 
                  errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::setClientID(const UTF8String * const clientID)
{
  CHECK_OBJECT_VALIDITY();

  iMQError     errorCode       = IMQ_SUCCESS;
  PRInt32      statusCode      = 0;
  Packet *     clientIDPacket  = NULL;
  Properties * packetProps     = NULL;

  NULLCHK( clientID );
  MEMCHK( clientIDPacket = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );

  // Make the packet a client ID packet
  clientIDPacket->setPacketType(PACKET_TYPE_SET_CLIENTID);
  ERRCHK( packetProps->setStringProperty(IMQ_CLIENTID_PROPERTY, 
                                         clientID->getCharStr()) );
  clientIDPacket->setProperties(packetProps);
  packetProps = NULL; // clientIDPacket is responsible for packetProps now

  // Send the packet and get the broker's response
  ERRCHK( writePacketWithStatus(clientIDPacket, 
                                PACKET_TYPE_SET_CLIENTID_REPLY, 
                                &statusCode) );
  DELETE( clientIDPacket );

  if (statusCode == STATUS_CONFLICT) {
    ERRCHK( MQ_CLIENTID_IN_USE );
  }
  // Return an error if status isn't OK
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_INFO(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, "set clientID to '%s' succeeded", 
             clientID->getCharStr() ));
  
  if (clientIDSent != PR_TRUE) clientIDSent = PR_TRUE;
  return IMQ_SUCCESS;
  
Cleanup:
  DELETE( clientIDPacket );
  HANDLED_DELETE( packetProps );
  
  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_SET_CLIENTID_FAILED,
                "Setting clientID to '%s' failed  because '%s' (%d)", 
                (clientID == NULL) ? "<NULL>" : clientID->getCharStr(),
                errorStr(errorCode), errorCode ));

  return errorCode;
}

/*
 *
 */
iMQError 
ProtocolHandler::deleteDestination(const Destination * const dest)
{
  CHECK_OBJECT_VALIDITY();

  iMQError     errorCode   = IMQ_SUCCESS;
  PRInt32      statusCode  = 0;
  Packet *     packet      = NULL;
  Properties * packetProps = NULL;
  NULLCHK( dest );
  MEMCHK( packet = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );

  // Set the packet fields/properties to delete the destination
  packet->setPacketType(PACKET_TYPE_DESTROY_DESTINATION);
  ERRCHK( packetProps->setStringProperty(IMQ_DESTINATION_PROPERTY, 
                                         dest->getName()->getCharStr()) );
  ERRCHK( packetProps->setIntegerProperty(IMQ_DESTINATION_TYPE_PROPERTY, 
                                          this->getDestinationType(dest)) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible for packetProps now
  
  // Send the packet and get the broker's response
  ERRCHK( writePacketWithStatus(packet, 
                                PACKET_TYPE_DESTROY_DESTINATION_REPLY, 
                                &statusCode) );
  DELETE( packet );

  // Return an error if status isn't OK
  ERRCHK( Status::toIMQError(statusCode) );
  
  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, "Protocol::deleteDestination('%s') succeeded", 
             dest->getName()->getCharStr() ));
  
  return IMQ_SUCCESS;
  
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );
  
  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_DELETE_DESTINATION_FAILED,
                "Deleting destination '%s' failed because '%s' (%d)", 
                (dest == NULL) ? "<NULL>" : dest->getName()->getCharStr(),
                errorStr(errorCode), errorCode ));

  return errorCode;
}



/*
 *
 */
iMQError 
ProtocolHandler::createDestination(const Destination * const dest)
{
  CHECK_OBJECT_VALIDITY();

  iMQError     errorCode   = IMQ_SUCCESS;
  PRInt32      statusCode  = 0;
  Packet *     packet      = NULL;
  Properties * packetProps = NULL;
  NULLCHK( dest );

  if (dest->getIsTemporary() == PR_TRUE && dest->getConnection() == NULL) {
    if (dest->isValidObject() == PR_FALSE) {
      ERRCHK( MQ_HANDLED_OBJECT_INVALID_HANDLE_ERROR );
    }
    char * tmprefix = this->connection->getTemporaryDestinationPrefix(
                                                    dest->getIsQueue());
    CNDCHK( tmprefix == NULL, MQ_OUT_OF_MEMORY );
    if (STRNCMP(dest->getName()->getCharStr(), STRLEN(tmprefix), tmprefix) != 0) {
      DELETE_ARR( tmprefix );
      LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
      "Protocol::createDestination('%s') bypassed: temporary destination not owner",
                 dest->getName()->getCharStr() ));
      return MQ_SUCCESS;
    }
    DELETE_ARR( tmprefix );
  }

  MEMCHK( packet = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );

  // Set the packet fields/properties to delete the destination
  packet->setPacketType(PACKET_TYPE_CREATE_DESTINATION);
  ERRCHK( packetProps->setStringProperty(IMQ_DESTINATION_PROPERTY, 
                                         dest->getName()->getCharStr()) );
  ERRCHK( packetProps->setIntegerProperty(IMQ_DESTINATION_TYPE_PROPERTY, 
                                          this->getDestinationType(dest)) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  // Send the packet and get the broker's response
  ERRCHK( writePacketWithStatus(packet, 
                                PACKET_TYPE_CREATE_DESTINATION_REPLY, 
                                &statusCode) );
  DELETE( packet );

  // Return an error if status isn't OK or CONFLICT
  // Conflict means that it was already created.
  if ((statusCode != STATUS_OK) && (statusCode != STATUS_CONFLICT)) {
    ERRCHK( Status::toIMQError(statusCode) );
  }
  
  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, "Protocol::createDestination('%s') succeeded", 
             dest->getName()->getCharStr() ));
  
  return IMQ_SUCCESS;
  
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );
  
  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_DELETE_DESTINATION_FAILED,
                "Creating destination '%s' failed because '%s' (%d)", 
                (dest == NULL) ? "<NULL>" : dest->getName()->getCharStr(),
                errorStr(errorCode), errorCode ));

  return errorCode;
}

/*
 *
 */
PRInt32
ProtocolHandler::getDestinationType(const Destination * const dest) const
{
  CHECK_OBJECT_VALIDITY();

  PRInt32 type = 0;
  ASSERT( dest != NULL );
  if (dest == NULL) {
    return type;
  }
  
  if (dest->getIsQueue()) {
    type |= DEST_TYPE_QUEUE;
  } else {
    type |= DEST_TYPE_TOPIC;
  }
  
  if (dest->getIsTemporary()) {
    type |= DEST_TEMP;
  }

  return type;
}

/*
 *
 */
iMQError
ProtocolHandler::authenticate(const Packet * const authReqPacket,
                              const UTF8String * const username, 
                              const UTF8String * const password)
{
  static const char FUNCNAME[] = "authenticate";
  CHECK_OBJECT_VALIDITY();

  // Local variables
  iMQError           errorCode       = IMQ_SUCCESS;
  const char *       authTypeStr     = NULL;
  const char *       authTypeToLog   = NULL;
  PRBool             isChallenge     = PR_FALSE;
  Packet *           request         = NULL;         
  Packet *           response        = NULL;         
  Properties *       properties      = NULL;         
  const Properties * authProperties  = NULL;         
  const PRUint8 *    requestData     = NULL;         
  PRInt32            requestDataLen  = 0;
  PRUint8 *          responseData    = NULL;         
  PRInt32            responseDataLen = 0;
  const Properties * packetProps     = NULL;
  PRInt32            statusCode      = 0;
  AuthenticationProtocolHandler * authHandler = NULL; 

  // Validate the parameters
  NULLCHK( authReqPacket );
  NULLCHK( username );
  NULLCHK( password );

  // Get the type of authentication
  packetProps = authReqPacket->getProperties();
  CNDCHK( packetProps == NULL, IMQ_INVALID_AUTHENTICATE_REQUEST );
  CNDCHK( packetProps->getStringProperty(IMQ_AUTH_TYPE_PROPERTY, &authTypeStr) 
                                                                != IMQ_SUCCESS,
          IMQ_INVALID_AUTHENTICATE_REQUEST );
  authTypeToLog = authTypeStr;

  // Get the authentication handler
  if ((packetProps->getBooleanProperty(IMQ_CHALLENGE_PROPERTY, &isChallenge) 
                                                             == IMQ_SUCCESS) &&
      isChallenge)
  {
    ERRCHK( checkAdminKeyAuth(authTypeStr) );
    ERRCHK( getAuthHandlerInstance(authTypeStr, &authHandler) );
    ASSERT( authHandler != NULL );
    authHandler->init(username, password, authProperties);
    connection->setAuthenticationHandler(authHandler);
  } else {
    authHandler = connection->getAuthenticationHandler();
    CNDCHK( authHandler == NULL, IMQ_NO_AUTHENTICATION_HANDLER );
  }
  
  // Some authentication schemes may require multiple
  // AUTHENTICATE_REQUEST/AUTHENTICATE messages.  So we continue until we get
  // PACKET_TYPE_AUTHENTICATE_REPLY.
  request = (Packet*)authReqPacket; // this cast is safe
  while (request->getPacketType() != PACKET_TYPE_AUTHENTICATE_REPLY) {
    CNDCHK( STRCMP(authHandler->getType(), authTypeStr) != 0,
            IMQ_INVALID_AUTHENTICATE_REQUEST );

    // Let the authentication handler, handle the authentication 
    requestData    = request->getMessageBody();
    requestDataLen = request->getMessageBodySize();
    ERRCHK( authHandler->handleRequest( requestData,
                                        requestDataLen,
                                        &responseData,
                                        &responseDataLen,
                                        request->getSequence()) );
    
    // Delete every request packet other than the initial one
    if (request != authReqPacket) {
      DELETE(request);
    } 
    request = NULL;

    // Set up the response
    MEMCHK( response = new Packet() );
    response->setPacketType(PACKET_TYPE_AUTHENTICATE);
    MEMCHK( properties = new Properties(PR_TRUE) );
    ERRCHK( packetProps->getInitializationError() );

    properties->setStringProperty(IMQ_AUTH_TYPE_PROPERTY, authHandler->getType());
    response->setProperties(properties);
    properties = NULL;
    response->setMessageBody(responseData, responseDataLen);
    responseData = NULL; // owned by response

    // Send the response and get the reply
    ERRCHK( writePacketWithReply(response, 
                                 PACKET_TYPE_AUTHENTICATE_REPLY,
                                 PACKET_TYPE_AUTHENTICATE_REQUEST,
                                 &request) );
    ASSERT( request != NULL );
    DELETE( response );

    packetProps = request->getProperties();
    CNDCHK( packetProps == NULL, IMQ_INVALID_AUTHENTICATE_REQUEST );
    packetProps->getStringProperty(IMQ_AUTH_TYPE_PROPERTY, &authTypeStr);
  }
  ASSERT( request != NULL );
  ASSERT( (packetProps == request->getProperties()) && (packetProps != NULL) );
  ERRCHK( packetProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );
  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    errorCode = packetProps->getStringProperty(IMQ_REASON_PROPERTY, &reason);
    if (errorCode != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                   "[%s]:%s (%s)", PacketType::toString(request->getPacketType()), reason, 
                    username->getCharStr() ));
    }
    ERRCHK( Status::toIMQError(statusCode) );
  }

  ASSERT( request != authReqPacket );
  if (request != authReqPacket) {
    DELETE(request);
  }

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS,
             "Authenticated to the broker using '%s' authentication.", 
             authTypeToLog ));

  return IMQ_SUCCESS;

Cleanup:
  if (request != authReqPacket) {
    DELETE(request);
  }
  if ((response == NULL) || (response->getMessageBody() != responseData)) {
    DELETE(responseData);
  }
  if ((response == NULL) || (response->getProperties() != properties)) {
    HANDLED_DELETE(properties);
  }

  DELETE(response);

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_AUTHENTICATE_FAILED,
                "Failed to authenticate with the broker because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}



/*
 *
 */
iMQError
ProtocolHandler::getAuthHandlerInstance(const char * const authType,
                           AuthenticationProtocolHandler ** const handler) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( authType );
  RETURN_ERROR_IF_NULL( handler );
  *handler = NULL;

  if (STRCMP(authType, IMQ_AUTHTYPE_JMQDIGEST) == 0) {
    *handler = new JMQDigestAuthenticationHandler;
  }
  else if (STRCMP(authType, IMQ_AUTHTYPE_JMQBASIC) == 0) {
    *handler = new JMQBasicAuthenticationHandler;
  }
//  else if (STRCMP(authType, IMQ_AUTHTYPE_JMQADMINKEY) == 0) {
//    RETURN_IF_OUT_OF_MEMORY( handler = new JMQAdminKeyAuthenticationHandler );
//  }
  else {
    return MQ_UNSUPPORTED_AUTH_TYPE;
  }
  RETURN_IF_OUT_OF_MEMORY( *handler );
  return MQ_SUCCESS;
}


/*
 *
 */
iMQError
ProtocolHandler::checkAdminKeyAuth(const char * const authType) const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( authType );
  RETURN_ERROR_IF_NULL( connection );

  // The client and broker should match w.r.t. whether admin key is being used
  if (connection->isAdminKeyUsed() != 
      (STRCMP(authType, IMQ_AUTHTYPE_JMQADMINKEY) == 0)) 
  {
    return IMQ_ADMIN_KEY_AUTH_MISMATCH;
  }

  return IMQ_SUCCESS;
}

/*
 * PACKET_TYPE_ADD_CONSUMER should not call this one 
 */
iMQError
ProtocolHandler::writePacketWithAck(Packet * const packetToSend,
                                    const PRBool expectTwoReplies,
                                    PRUint32 expectedAckType1,
                                    Packet ** const replyPacket)
{
  static const char FUNCNAME[] = "writePacketWithAck";
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode;
  *replyPacket        = NULL;
  PRUint16 packetType = 0;
  PRInt32 statusCode  = 0;
  Packet * ackPacket      = NULL;       // the ack packet from the broker
  PRInt64  ackID          = 0;          // consumerID to put in the packet
  PRBool acking = PR_FALSE;
  ReceiveQueue * ackQueue = NULL;       // Queue to receive the ack

  NULLCHK( packetToSend );
  NULLCHK( replyPacket );
  *replyPacket = NULL;

  // Make sure the connection is still up
  updateConnectionState();   
  CNDCHK( this->isClosed, IMQ_BROKER_CONNECTION_CLOSED );


  if (expectTwoReplies == PR_FALSE) {
  MEMCHK( ackQueue = new ReceiveQueue(1) );
  } else {
  MEMCHK( ackQueue = new ReceiveQueue(2) );
  }
  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::writePacketWithAck allocated ackQueue=0x%p", ackQueue ));

  // Tell the broker that we expect and ack for this packet
  packetToSend->setSendAcknowledge(PR_TRUE);

  // associates ackID with ackQueue so ReadChannel we will know where
  // to put the acks we get back from the broker for this packet
  ERRCHK( connection->addToAckQTable(&ackID, ackQueue) );
  acking = PR_TRUE;
  packetToSend->setConsumerID(ackID);

  // write the packet to the broker
  ERRCHK( writePacketNoAck(packetToSend) );

  // Block until we get an ack, or the connection goes away
  MQ_CNDCHK_TRACE( connection->getIsClosed() && ackQueue->isEmpty(), 
          IMQ_BROKER_CONNECTION_CLOSED, FUNCNAME );
  ackPacket = (Packet*)ackQueue->dequeueWait(connection->getAckTimeoutMicroSec());
  if (ackPacket == NULL) {
    if (ackQueue->getIsClosed()) {
      ERRCHK( MQ_BROKER_CONNECTION_CLOSED );
    } else if ((PRUint32)connection->getAckTimeoutMicroSec() != TRANSPORT_NO_TIMEOUT) {
      ERRCHK( MQ_TIMEOUT_EXPIRED );
    } else {
      MQ_ERRCHK_TRACE( MQ_UNEXPECTED_NULL, FUNCNAME );
    }
  }

  // If we are expecting two replies, then get the second reply.
  // Currently this code is only used by the HELLO packet because it
  // is the only packet that expect 2 replies.
  if ((ackPacket != NULL) && expectTwoReplies) {
    const Properties * ackProperties  = ackPacket->getProperties();
    packetType = ackPacket->getPacketType();
    statusCode = STATUS_UNKNOWN;

    // get the status code
    if (ackProperties != NULL) {
      if (ackProperties->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) != IMQ_SUCCESS) {
        statusCode = STATUS_UNKNOWN;
      } else if (statusCode != STATUS_OK) { 
        const char * reason = NULL;
        MQError err = ackProperties->getStringProperty(IMQ_REASON_PROPERTY, &reason);
        if (err != MQ_NOT_FOUND && reason != NULL) {
          ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                       "[%s]:%s", PacketType::toString(ackPacket->getPacketType()), reason ));
        }
      }
    }
    
    // Make sure that the packet matches what we expected and that
    // everything is okay.
    CNDCHK( packetType != expectedAckType1, IMQ_UNEXPECTED_ACKNOWLEDGEMENT );
    ERRCHK( Status::toIMQError(statusCode) );
    
    // Block until we get the next ack, or the connection goes away.
    DELETE( ackPacket );
    MQ_CNDCHK_TRACE( connection->getIsClosed() && ackQueue->isEmpty(), 
            IMQ_BROKER_CONNECTION_CLOSED, FUNCNAME );
    LOG_FINER(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
                "ProtocolHandler::writePacketWithAck calling dequeueWait(%d microSec).", connection->getAckTimeoutMicroSec() ));

    ackPacket = (Packet*)ackQueue->dequeueWait(connection->getAckTimeoutMicroSec());

    LOG_FINER(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
                "ProtocolHandler::writePacketWithAck returned from dequeueWait(%d microSec).", connection->getAckTimeoutMicroSec() ));

    if (ackPacket == NULL) {
      LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                    IMQ_PROTOCOL_HANDLER_WRITE_ERROR,
                    "Didn't receive packet acknowledgement because %s (%d)",
                    errorStr(errorCode), errorCode ));
      if (ackQueue->getIsClosed()) {
        ERRCHK( MQ_BROKER_CONNECTION_CLOSED );
      } else if ((PRUint32)connection->getAckTimeoutMicroSec() != TRANSPORT_NO_TIMEOUT) {
        ERRCHK( MQ_TIMEOUT_EXPIRED );
      } else {
        MQ_ERRCHK_TRACE( MQ_UNEXPECTED_NULL, FUNCNAME );
      }
    }
  }

  *replyPacket = ackPacket;

  // Remove ackID from the ReadQTable and delete the ackQueue
  connection->removeFromAckQTable(ackID);
  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::writePacketWithAck deleting ackQueue=0x%p", ackQueue ));
  DELETE( ackQueue );


  return IMQ_SUCCESS;

Cleanup:
  ASSERT( errorCode != IMQ_SUCCESS );
  DELETE( ackPacket );

  // remove ackID from the ReadQTable and delete the ackQueue
  if (acking == PR_TRUE) {
    connection->removeFromAckQTable(ackID);
  }
  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::writePacketWithAck deleting ackQueue=0x%p", ackQueue ));
  DELETE( ackQueue );

  if (errorCode == IMQ_BROKER_CONNECTION_CLOSED) {
  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_WRITE_ERROR,
                "Failed to write packet with reply because '%s' (%d)", 
                errorStr(errorCode), errorCode ));
  } else {
  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_WRITE_ERROR,
                "Failed to write packet with reply because '%s' (%d)", 
                errorStr(errorCode), errorCode ));
  }

  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::writePacketNoAck(Packet * const packetToSend)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  NULLCHK( packetToSend );

  // set IP address and port 
  packetToSend->setIP(connection->getLocalIP());
  packetToSend->setPort(connection->getLocalPort());

  // send the packet
  writeMonitor.enter();
  errorCode = packetToSend->writePacket(connection->getTransport(), this->writeTimeout);
  writeMonitor.exit();

  ERRCHK( errorCode );
  this->hasActivity = PR_TRUE;

  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
               IMQ_SUCCESS, 
               "Wrote the following packet: %s", packetToSend->toString() ));
  
  return IMQ_SUCCESS;

Cleanup:

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             IMQ_PROTOCOL_HANDLER_WRITE_ERROR,
             "Failed to write the following packet because '%s' (%d): %s", 
             errorStr(errorCode), errorCode, 
             (packetToSend == NULL) ? 
             "NULL packet" : packetToSend->toString() ));

  return errorCode;
}

/*
 *
 */
iMQError
ProtocolHandler::writePacketWithStatus(      Packet *  const packetToSend,
                                       const PRUint32        expectedAckType,
                                             PRInt32 * const status)
{
  static const char FUNCNAME[] = "writePacketWithStatus";
  CHECK_OBJECT_VALIDITY();

  iMQError            errorCode      = IMQ_SUCCESS;
  Packet *            replyPacket    = NULL;
  const Properties *  replyProps     = NULL;
  PRInt32             statusCode     = STATUS_UNKNOWN;

  NULLCHK( packetToSend );
  NULLCHK( status );
  *status = STATUS_UNKNOWN;

  // Write the packet
  ERRCHK( writePacketWithReply(packetToSend, expectedAckType, &replyPacket) );
  
  // Get the status
  replyProps = replyPacket->getProperties();
  CNDCHK( replyProps == NULL, IMQ_PROPERTY_NULL );
  ERRCHK( replyProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );
  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    errorCode = replyProps->getStringProperty(IMQ_REASON_PROPERTY, &reason); 
    if (errorCode != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode), 
                   "[%s]:%s", PacketType::toString(replyPacket->getPacketType()), reason )); 
    }
  }

  *status = statusCode;
  
  DELETE( replyPacket );
  return IMQ_SUCCESS;

Cleanup:

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             IMQ_PROTOCOL_HANDLER_ERROR,
             "ProtocolHandler::writePacketWithStatus() FAILED "
             "because '%s' (%d)", errorStr(errorCode), errorCode ));

  DELETE( replyPacket );
  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::writePacketWithReply(Packet * const packetToSend,
                                      const PRUint32 expectedAckType,
                                      Packet ** const replyPacket)
{
  CHECK_OBJECT_VALIDITY();

  return writePacketWithReply(packetToSend, expectedAckType, 
                              expectedAckType, replyPacket);
}

/*
 *
 */
iMQError
ProtocolHandler::writePacketWithReply(Packet * const packetToSend,
                                      const PRUint32 expectedAckType1,
                                      const PRUint32 expectedAckType2,
                                      Packet ** const replyPacket)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  Packet * reply = NULL;

  NULLCHK( packetToSend );
  NULLCHK( replyPacket );
  *replyPacket = NULL;

  // Send the packet and get the response
  ERRCHK( writePacketWithAck(packetToSend, PR_FALSE, 0, &reply) );
  ASSERT( reply != NULL );
  CNDCHK( reply == NULL, IMQ_UNEXPECTED_ACKNOWLEDGEMENT );
  if ((reply->getPacketType() != expectedAckType1) && 
      (reply->getPacketType() != expectedAckType2))
  {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY,
                  "Received an unexpected reply packet.  "
                  "Expected %d or %d, and got %d", 
                  expectedAckType1, expectedAckType2, reply->getPacketType() ));
      
    ERRCHK( IMQ_UNEXPECTED_ACKNOWLEDGEMENT );
  }

  *replyPacket = reply;

  return IMQ_SUCCESS;

Cleanup:
  DELETE( reply );
  *replyPacket = NULL;

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY,
                "Failed to get reply for %d packet.",
                packetToSend->getPacketType() ));

  return errorCode;
}


/*
 *
 */
void
ProtocolHandler::updateConnectionState()
{
  CHECK_OBJECT_VALIDITY();

  if ((connection == NULL) || (connection->getIsClosed())) {
    isClosed = PR_TRUE;
  }
}


/*
 *
 */
iMQError
ProtocolHandler::readPacket(Packet ** const packet)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  Packet * pkt = NULL;

  NULLCHK( packet );
  *packet = NULL;

  // Allocate a new packet and then read it.
  MEMCHK( pkt = new Packet() );
  ERRCHK( pkt->readPacket(connection->getTransport()) );
  *packet = pkt;
  this->hasActivity = PR_TRUE;

  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
               IMQ_SUCCESS, "Read the following packet: %s", pkt->toString() ));

  return IMQ_SUCCESS;

Cleanup:
  DELETE( pkt );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_READ_ERROR,
                "Failed to read a packet because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError 
ProtocolHandler::registerMessageProducer(const Session * const session,
                                         const Destination * const destination,
                                         ProducerFlow * producerFlow)
{
  static const char FUNCNAME[] = "registerMessageProducer";
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Packet * replyPacket = NULL;
  Properties * packetProps = NULL;
  const Properties * replyPacketProps = NULL;

  NULLCHK( session );
  NULLCHK( destination );
  NULLCHK( producerFlow );
  ERRCHK( createDestination(destination) );
  

  MEMCHK( packet = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );

  // Make the packet an ADD_PRODUCER packet
  packet->setPacketType(PACKET_TYPE_ADD_PRODUCER);
  ERRCHK( packetProps->setStringProperty(IMQ_DESTINATION_PROPERTY, 
                                         destination->getName()->getCharStr()));
  ERRCHK( packetProps->setIntegerProperty(IMQ_DESTINATION_TYPE_PROPERTY, 
                                          getDestinationType(destination)) );
  ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, session->getSessionID()) );
  
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible for deleting packetProps now

  // Send the packet and get the broker's response
  ERRCHK( writePacketWithReply(packet, 
                                PACKET_TYPE_ADD_PRODUCER_REPLY, 
                                &replyPacket) );
  ASSERT( replyPacket != NULL );
  DELETE( packet );
  replyPacketProps = replyPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );

  // Return an error if status isn't OK or CONFLICT
  // Conflict means that it was already created.
  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    MQError err = replyPacketProps->getStringProperty(IMQ_REASON_PROPERTY, &reason);
    if (err != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                   "[%s]:%s (%s)", PacketType::toString(replyPacket->getPacketType()), reason, 
                    destination->getName()->getCharStr() ));
    }
    ERRCHK( Status::toIMQError(statusCode) );
  }

  {
  PRInt64 producerID = 0;
  PRInt64 chunkBytes = -1;
  PRInt32 chunkSize = -1;
  ERRCHK( replyPacketProps->getLongProperty(IMQ_PRODUCERID_PROPERTY, &producerID) );
  producerFlow->setProducerID(producerID);
  producerFlow->setChunkBytes(chunkBytes);
  producerFlow->setChunkSize(chunkSize);
  errorCode =  replyPacketProps->getLongProperty(IMQ_BYTES_PROPERTY, &chunkBytes); 
  if (errorCode == MQ_SUCCESS) {
    producerFlow->setChunkBytes(chunkBytes);
  } else if (errorCode != MQ_NOT_FOUND) {
    ERRCHK( errorCode );
  }
  errorCode = replyPacketProps->getIntegerProperty(IMQ_SIZE_PROPERTY, &chunkSize); 
  if (errorCode == MQ_SUCCESS) {
    producerFlow->setChunkSize(chunkSize);
  } else if (errorCode != MQ_NOT_FOUND) {
    ERRCHK( errorCode );
  }
  }

  DELETE( replyPacket );


  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, 
             "registerMessageProducer registered a producer for : %s", 
             destination->getName()->getCharStr() ));


  return IMQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( packet );
  DELETE( replyPacket );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Failed to register a producer"
                " because '%s' (%d)", 
                errorStr(errorCode), errorCode ));
  
  return errorCode;
}


MQError
ProtocolHandler::unregisterMessageProducer(PRInt64 producerID)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;
  ProducerFlow * producerFlow = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_DELETE_PRODUCER);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setLongProperty(IMQ_PRODUCERID_PROPERTY, producerID) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now


  ERRCHK( writePacketWithStatus(packet,
                               PACKET_TYPE_DELETE_PRODUCER_REPLY,
                               &statusCode) );
  DELETE( packet );

  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             MQ_SUCCESS, "Protocol::deleteProducer() succeeded" ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Failed to unregister a producer"
                " because '%s' (%d)",
                errorStr(errorCode), errorCode ));

  return errorCode;
}



/*
 *
 */
/* 
 *  does what writePacketWithReply() do for clarity than code re-use  
 */ 
iMQError 
ProtocolHandler::registerMessageConsumer(MessageConsumer * consumer)
{
  static const char FUNCNAME[] = "registerMessageConsumer";
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  const Destination * destination = NULL; 
  Packet * packet = NULL;
  Properties * packetProps = NULL;
  Packet * ackPacket      = NULL;
  const Properties * replyPacketProps = NULL;
  PRInt64  ackID  = 0; 
  PRBool pending = PR_FALSE, acking = PR_FALSE;
  ReceiveQueue * ackQueue = NULL;

  NULLCHK( consumer );

  // Make sure the destination is valid
  destination = consumer->getDestination();
  ERRCHK( createDestination(destination) );
  
  // Create an ADD_CONSUMER packet
  MEMCHK( packet = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  packet->setPacketType(PACKET_TYPE_ADD_CONSUMER);
  
  // If the consumer is durable, set its durable name 
  if (consumer->getIsDurable()) {
    ASSERT( consumer->getSubscriptionName() != NULL );
    CNDCHK( consumer->getSubscriptionName() == NULL, MQ_CONSUMER_NO_DURABLE_NAME );
    ERRCHK( packetProps->setStringProperty(IMQ_DURABLE_NAME_PROPERTY,
                         consumer->getSubscriptionName()->getCharStr()) );
  }
  if (consumer->getIsShared()) {
    ASSERT( consumer->getSubscriptionName() != NULL );
    CNDCHK( consumer->getSubscriptionName() == NULL, MQ_CONSUMER_NO_SUBSCRIPTION_NAME );
    ERRCHK( packetProps->setBooleanProperty(IMQ_JMS_SHARED_PROPERTY, consumer->getIsShared()) );
    if (consumer->getIsDurable() == PR_FALSE) {
      ERRCHK( packetProps->setStringProperty(IMQ_SHARED_SUBSCRIPTION_NAME_PROPERTY,
                           consumer->getSubscriptionName()->getCharStr()) );
    }
  }

  // Set the other properties
  ERRCHK( packetProps->setStringProperty(IMQ_DESTINATION_PROPERTY, 
                                         destination->getName()->getCharStr()));
  ERRCHK( packetProps->setIntegerProperty(IMQ_DESTINATION_TYPE_PROPERTY, 
                                          getDestinationType(destination)) );

  if (consumer->getMessageSelector() != NULL) {
    ERRCHK( packetProps->setStringProperty(IMQ_SELECTOR_PROPERTY,
                                           consumer->getMessageSelector()->getCharStr()) );
  }

  ERRCHK( packetProps->setBooleanProperty(IMQ_NOLOCAL_PROPERTY,
                                          consumer->getNoLocal()) );

  consumer->setPrefetchMaxMsgCount(connection->getConsumerPrefetchMaxMsgCount());
  consumer->setPrefetchThresholdPercent(connection->getConsumerPrefetchThresholdPercent());
  ERRCHK( packetProps->setIntegerProperty(IMQ_SIZE_PROPERTY, connection->getNumMessagesBeforePausing()) ); 

  {
  Session * session = consumer->getSession();
  NULLCHK( session );
  ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, session->getSessionID()) );
  }
  ERRCHK( packetProps->setBooleanProperty(IMQ_RECONNECT_PROPERTY, PR_FALSE) );
  ERRCHK( packetProps->setBooleanProperty(IMQ_SHARE_PROPERTY, PR_FALSE) );

  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible for deleting packetProps now  
  
  MEMCHK( ackQueue = new ReceiveQueue );
  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::registerMessageConsumer allocated ackQueue=0x%p", ackQueue ));

  /** ensure all ackIDs in pending qtable are in ack qtable 
   *  note the ordering in removing also **/
  ERRCHK( connection->addToAckQTable(&ackID, ackQueue) ); 
  acking = PR_TRUE;
  ERRCHK( connection->addToPendingConsumerTable(ackID, consumer) );
  pending = PR_TRUE;

  packet->setConsumerID(ackID); 
  packet->setSendAcknowledge(PR_TRUE);

  updateConnectionState();
  CNDCHK( this->isClosed, IMQ_BROKER_CONNECTION_CLOSED );
  ERRCHK( writePacketNoAck(packet) );
  DELETE( packet );

  MQ_CNDCHK_TRACE( connection->getIsClosed() && ackQueue->isEmpty(),
                   IMQ_BROKER_CONNECTION_CLOSED, FUNCNAME );
  ackPacket = (Packet*)ackQueue->dequeueWait(connection->getAckTimeoutMicroSec());
  if (ackPacket == NULL) {
    if (ackQueue->getIsClosed()) {
      ERRCHK( MQ_BROKER_CONNECTION_CLOSED );
    } else if ((PRUint32)connection->getAckTimeoutMicroSec() != TRANSPORT_NO_TIMEOUT) {
      ERRCHK( MQ_TIMEOUT_EXPIRED );
    } else {
      MQ_ERRCHK_TRACE( MQ_UNEXPECTED_NULL, FUNCNAME );
    }
  }

  connection->removeFromAckQTable(ackID);
  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::registerMessageConsumer deleting ackQueue=0x%p", ackQueue ));
  DELETE( ackQueue );

  if ((ackPacket->getPacketType() != PACKET_TYPE_ADD_CONSUMER_REPLY)) {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY,
    "RegisterMessageConsumer received an unexpected reply packet %d but expected %d",
                  ackPacket->getPacketType(), PACKET_TYPE_ADD_CONSUMER_REPLY ));
    ERRCHK( IMQ_UNEXPECTED_ACKNOWLEDGEMENT );
  }
  replyPacketProps = ackPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );
  DELETE( ackPacket );

  if (statusCode == STATUS_BAD_REQUEST && consumer->getMessageSelector() != NULL) {
    ERRCHK( MQ_INVALID_MESSAGE_SELECTOR );
  }
  if (statusCode == STATUS_CONFLICT ) {
    ERRCHK( MQ_DESTINATION_CONSUMER_LIMIT_EXCEEDED );
  }
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, 
             "registerMessageConsumer registered a consumer for : %s", 
             destination->getName()->getCharStr() ));
    
  return IMQ_SUCCESS;

Cleanup:
  ASSERT( errorCode != MQ_SUCCESS );
  if (pending == PR_TRUE) {
    connection->removeFromPendingConsumerTable(ackID, NULL);
  }
  if (acking == PR_TRUE) {
    connection->removeFromAckQTable(ackID);
  }
  LOG_FINER(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), IMQ_SUCCESS,
              "ProtocolHandler::registerMessageConsumer deleting ackQueue=0x%p", ackQueue ));
  DELETE( ackQueue );

  HANDLED_DELETE( packetProps );
  DELETE( packet );
  DELETE( ackPacket );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Failed to register a message consumer because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError
ProtocolHandler::unsubscribeDurableConsumer(const UTF8String * const durableName)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  NULLCHK( durableName );

  // Create the DELETE_CONSUMER packet
  MEMCHK( packet = new Packet() );
  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  packet->setPacketType(PACKET_TYPE_DELETE_CONSUMER);

  // Set the durable name
  ERRCHK( packetProps->setStringProperty(IMQ_DURABLE_NAME_PROPERTY,
                                         durableName->getCharStr()) );
  {
  const UTF8String * clientID = this->connection->getClientID();
  if ( clientID != NULL ) {
    ERRCHK( packetProps->setStringProperty(IMQ_CLIENTID_PROPERTY, clientID->getCharStr()) );
  }
  }

  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible for deleting packetProps now  
  
  // Send the packet and get the broker's response
  ERRCHK( writePacketWithStatus(packet, 
                                PACKET_TYPE_DELETE_CONSUMER_REPLY, 
                                &statusCode) );
  DELETE( packet );

  if (statusCode == STATUS_NOT_FOUND ) {
    ERRCHK( MQ_CONSUMER_NOT_FOUND );
  } else if (statusCode == STATUS_PRECONDITION_FAILED) {
    ERRCHK( MQ_CONSUMER_DESTINATION_NOT_FOUND );
  } else if (statusCode == STATUS_CONFLICT) {
    ERRCHK( MQ_CANNOT_UNSUBSCRIBE_ACTIVE_CONSUMER );
  }
  // Return an error of status isn't OK
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, 
             "unsubscribeDurableConsumer unsubscribed %s",
             durableName->getCharStr() ));
    
  return IMQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( packet );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Failed to unsubscribe \'%s\'"
                " because '%s' (%d)", 
                (durableName == NULL) ? "<NULL>" : durableName->getCharStr(), 
                errorStr(errorCode), errorCode ));

  return errorCode;
}


/*
 *
 */
iMQError 
ProtocolHandler::unregisterMessageConsumer(
                   const MessageConsumer * const consumer)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;
  const Destination * destination = NULL;

  NULLCHK( consumer );

  // Get the destination.  Only used for logging.
  destination = consumer->getDestination();
  
  // Create an ADD_CONSUMER packet
  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_DELETE_CONSUMER);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setLongProperty(IMQ_CONSUMERID_PROPERTY,
                                          consumer->getConsumerID()) );
  ERRCHK( packetProps->setBooleanProperty(IMQ_BLOCK_PROPERTY, PR_TRUE) );
  
    if (consumer->getHasLastDeliveredSysMessageID() == PR_TRUE) {
      SerialDataOutputStream stream;
      const PRUint8 * block;
      PRUint8 * blockClone;
      PRInt32 blockSize;
      ERRCHK( packetProps->setIntegerProperty(IMQ_BODY_TYPE_PROPERTY, BODY_TYPE_SYSMESSAGEID) );
      consumer->getLastDeliveredSysMessageID()->writeID(&stream); 
	  block = stream.getStreamBytes();
      blockSize = stream.numBytesWritten();
      MEMCHK( blockClone = new PRUint8[blockSize] );
	  memcpy( blockClone, block, blockSize);
	  packet->setMessageBody(blockClone, blockSize);
      blockClone = NULL;
    }

  packet->setProperties(packetProps);
  packetProps = NULL;

  // Send the packet and get the broker's response
  ERRCHK( writePacketWithStatus(packet, 
                                PACKET_TYPE_DELETE_CONSUMER_REPLY, 
                                &statusCode) );
  DELETE( packet );

  if (statusCode == STATUS_NOT_FOUND ) {
    ERRCHK( MQ_CONSUMER_NOT_FOUND );
  } else if (statusCode == STATUS_PRECONDITION_FAILED) {
    ERRCHK( MQ_CONSUMER_DESTINATION_NOT_FOUND );
  }

  // Return an error if status isn't OK
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
             IMQ_SUCCESS, 
             "unregisterMessageConsumer unregistered a consumer for : %s", 
             destination->getName()->getCharStr() ));
    
  return IMQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( packet );

  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Failed to unregister a consumer"
                " because '%s' (%d)", 
                errorStr(errorCode), errorCode ));

  return errorCode;
}

/*
 *
 */
iMQError
ProtocolHandler::writeJMSMessage(const Session * const session, Message * const message)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  PRBool produceAck = PR_FALSE;
  PRInt64 timeToLive = 0;
  PRInt64 deliveryTime = 0;

  NULLCHK( session );
  NULLCHK( message );
  ERRCHK( message->getJMSExpiration(&timeToLive) );
  if (LL_IS_ZERO(timeToLive) == 0) {
    PRInt64 tmp = 0; 
    LL_DIV(tmp, PR_Now(), (PRUint64)PR_USEC_PER_MSEC);
    LL_ADD(timeToLive, timeToLive, tmp);
    message->setJMSExpiration(timeToLive);
  }

  ERRCHK( message->getJMSDeliveryTime(&deliveryTime) );
  if (LL_GE_ZERO(deliveryTime) != 0 && LL_IS_ZERO(deliveryTime) == 0) {
    PRInt64 tmp = 0; 
    LL_DIV(tmp, PR_Now(), (PRUint64)PR_USEC_PER_MSEC);
    LL_ADD(deliveryTime, deliveryTime, tmp);
    message->setJMSDeliveryTime(deliveryTime);
  }

  // Get the packet from the JMS message and send it
  packet = message->getPacket();
  if (session->getAckMode() == SESSION_TRANSACTED) {
    packet->setTransactionID(session->getTransactionID());
  }

  // Check if we should block waiting for the message to be acknowledged
  if (packet->getPersistent()) {
    produceAck = connection->getAckOnPersistentProduce();
  } else {
    produceAck = connection->getAckOnNonPersistentProduce();
  }

  // Send the message
  if (produceAck) {
    ERRCHK( writePacketWithStatus(packet, 
                                  PACKET_TYPE_SEND_REPLY, 
                                  &statusCode) );
    if (statusCode == STATUS_NOT_FOUND) {
      ERRCHK( MQ_SEND_NOT_FOUND );
    } else if (statusCode == STATUS_ENTITY_TOO_LARGE) {
      ERRCHK( MQ_SEND_TOO_LARGE );
    } else if (statusCode == STATUS_RESOURCE_FULL) {
      ERRCHK( MQ_SEND_RESOURCE_FULL );
    }
    ERRCHK( Status::toIMQError(statusCode) );
  } else {
    packet->setSendAcknowledge(PR_FALSE);
    ERRCHK( writePacketNoAck(packet) );
  }

  LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
               IMQ_SUCCESS, "ProtocolHandler::writeJMSMessage succeeded" ));

  return IMQ_SUCCESS;
Cleanup:
  LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                IMQ_PROTOCOL_HANDLER_ERROR,
                "Send message failed because '%s' (%d). ",
                errorStr(errorCode), errorCode ));

  return errorCode;
}


iMQError
ProtocolHandler::acknowledge(const Session * const session,
                             const PRUint8 * const ackBlock,
                             const PRInt32   ackBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * ackPacket = NULL;
  PRUint8 * ackBlockClone = NULL;
  PRBool requireAckFromBroker = PR_FALSE;
  Properties * packetProps = NULL;

  NULLCHK( ackBlock );
  ASSERT( ackBlockSize > 0 );

  // Create a new acknowledgement packet
  MEMCHK( ackPacket = new Packet() );
  ackPacket->setPacketType(PACKET_TYPE_ACKNOWLEDGE);
  requireAckFromBroker = connection->getAckOnAcknowledge();
  //ackPacket->setSendAcknowledge(requireAckFromBroker);

  if (session->getAckMode() == SESSION_TRANSACTED) {
    ackPacket->setTransactionID(session->getTransactionID());
  }

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setIntegerProperty(IMQ_BODY_TYPE_PROPERTY, BODY_TYPE_CONSUMERID_L_SYSMESSAGEID) );
  ackPacket->setProperties(packetProps);
  packetProps = NULL; 

  // Set the packet body to the acknowledgement block
  MEMCHK( ackBlockClone = new PRUint8[ackBlockSize] );
  memcpy( ackBlockClone, ackBlock, ackBlockSize );
  ackPacket->setMessageBody(ackBlockClone, ackBlockSize);
  ackBlockClone = NULL;

  // Send the acknowledgement packet and get a response if required
  if (requireAckFromBroker == PR_TRUE 
      && session->getAckMode() != DUPS_OK_ACKNOWLEDGE) {
    ERRCHK( writePacketWithStatus(ackPacket,
                                  PACKET_TYPE_ACKNOWLEDGE_REPLY,
                                  &statusCode) );
    // Return an error if status isn't OK
    ERRCHK( Status::toIMQError(statusCode) );
  } else {
    ERRCHK( writePacketNoAck(ackPacket) );
  }

  DELETE( ackPacket );
  return IMQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( ackPacket );
  DELETE( ackBlockClone );

  return errorCode;
}


MQError
ProtocolHandler::acknowledgeExpired(const PRUint8 * const ackBlock,
                                    const PRInt32   ackBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * ackPacket = NULL;
  PRUint8 * ackBlockClone = NULL;
  Properties * packetProps = NULL;

  NULLCHK( ackBlock );
  ASSERT( ackBlockSize > 0 );

  // Create a new acknowledgement packet
  MEMCHK( ackPacket = new Packet() );
  ackPacket->setPacketType(PACKET_TYPE_ACKNOWLEDGE);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setIntegerProperty(IMQ_BODY_TYPE_PROPERTY, BODY_TYPE_CONSUMERID_L_SYSMESSAGEID) );
  ERRCHK( packetProps->setIntegerProperty(MQ_ACK_TYPE_PROPERTY, ACK_TYPE_DEAD_REQUEST) );
  ERRCHK( packetProps->setIntegerProperty(MQ_DEAD_REASON_PROPERTY, DEAD_REASON_EXPIRED) );
  ackPacket->setProperties(packetProps);
  packetProps = NULL; 

  // Set the packet body to the acknowledgement block
  MEMCHK( ackBlockClone = new PRUint8[ackBlockSize] );
  memcpy( ackBlockClone, ackBlock, ackBlockSize );
  ackPacket->setMessageBody(ackBlockClone, ackBlockSize);
  ackBlockClone = NULL;

  ERRCHK( writePacketNoAck(ackPacket) );

  DELETE( ackPacket );
  return MQ_SUCCESS;
Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( ackPacket );
  DELETE( ackBlockClone );

  return errorCode;
}


MQError
ProtocolHandler::redeliver(const Session * const session, PRBool setRedelivered,
                           const PRUint8 * const redeliverBlock,
                           const PRInt32   redeliverBlockSize)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Packet * redeliverPacket = NULL;
  PRUint8 * redeliverBlockClone = NULL;
  PRBool requireAckFromBroker = PR_FALSE;
  Properties * packetProps = NULL;

  NULLCHK( redeliverBlock );
  ASSERT( redeliverBlockSize > 0 );

  // Create a new acknowledgement packet
  MEMCHK( redeliverPacket = new Packet() );
  redeliverPacket->setPacketType(PACKET_TYPE_REDELIVER);

  if (session->getAckMode() == SESSION_TRANSACTED) {
    redeliverPacket->setTransactionID(session->getTransactionID());
  }

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setBooleanProperty(IMQ_SET_REDELIVERED_PROPERTY, setRedelivered) );
  ERRCHK( packetProps->setIntegerProperty(IMQ_BODY_TYPE_PROPERTY, BODY_TYPE_CONSUMERID_L_SYSMESSAGEID) );
  redeliverPacket->setProperties(packetProps);
  packetProps = NULL; // redeliverPacket is responsible now

  // Set the packet body to the acknowledgement block
  MEMCHK( redeliverBlockClone = new PRUint8[redeliverBlockSize] );
  memcpy( redeliverBlockClone, redeliverBlock, redeliverBlockSize );
  redeliverPacket->setMessageBody(redeliverBlockClone, redeliverBlockSize);
  redeliverBlockClone = NULL;

  ERRCHK( writePacketNoAck(redeliverPacket) );
 
  DELETE( redeliverPacket );
  return MQ_SUCCESS;
Cleanup:
  DELETE( redeliverPacket );
  HANDLED_DELETE( packetProps );
  DELETE( redeliverBlockClone );
  MQ_ERROR_TRACE( "redeliver", errorCode );
  return errorCode;
}


MQError
ProtocolHandler::registerSession(Session * session)
{
  static const char FUNCNAME[] = "registerSession";
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Packet * replyPacket   = NULL;
  Properties * packetProps = NULL;
  const Properties * replyPacketProps     = NULL;
  PRInt64 sessionID = 0;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_CREATE_SESSION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setIntegerProperty(IMQ_ACKMODE_PROPERTY, session->getAckMode()) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  ERRCHK( writePacketWithReply(packet,
                               PACKET_TYPE_CREATE_SESSION_REPLY,
                               &replyPacket) );
  ASSERT( replyPacket != NULL );
  DELETE( packet );
  replyPacketProps = replyPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );
  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    MQError err = replyPacketProps->getStringProperty(IMQ_REASON_PROPERTY, &reason);
    if (err != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                   "[%s]:%s", PacketType::toString(replyPacket->getPacketType()), reason ));
    }
    ERRCHK( Status::toIMQError(statusCode) );
  }

  ERRCHK( replyPacketProps->getLongProperty(IMQ_SESSIONID_PROPERTY, &sessionID) );
  DELETE( replyPacket );

  session->setSessionID(sessionID);

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );
  DELETE( replyPacket );

  return errorCode;
}


MQError
ProtocolHandler::unregisterSession(PRInt64 sessionID)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_DESTROY_SESSION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, sessionID) );
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now


  ERRCHK( writePacketWithStatus(packet,
                               PACKET_TYPE_DESTROY_SESSION_REPLY,
                               &statusCode) );
  DELETE( packet );

  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             MQ_SUCCESS, "Protocol::destorySession() succeeded" ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  return errorCode;
}


MQError
ProtocolHandler::startTransaction(PRInt64 sessionID, PRBool setSessionID, 
                                  XID *xid, long xaflags, PRInt64 * transactionID)
{
  static const char FUNCNAME[] = "startTransaction";
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Packet * replyPacket   = NULL;
  Properties * packetProps   = NULL;
  const Properties * replyPacketProps   = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_START_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  if (setSessionID == PR_TRUE) {
      ERRCHK( packetProps->setLongProperty(IMQ_SESSIONID_PROPERTY, sessionID) );
  }
  if (xid != NULL) {
      ERRCHK( packetProps->setIntegerProperty(MQ_XAFLAGS_PROPERTY, (PRInt32)xaflags) );
      SerialDataOutputStream out;
      const PRUint8 * xidbytes;
      PRUint8 * xidclone;
      PRInt32 xidsize;

      XIDObject xidobj;
      ERRCHK( xidobj.copy(xid) );
      ERRCHK( xidobj.write(&out) );
      xidbytes = out.getStreamBytes();
      xidsize = out.numBytesWritten();
      MEMCHK ( xidclone = new PRUint8[xidsize] );
      memcpy(xidclone, xidbytes, xidsize);
      packet->setMessageBody(xidclone, xidsize);
      xidclone = NULL;
  }
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  ERRCHK( writePacketWithReply(packet,
                               PACKET_TYPE_START_TRANSACTION_REPLY,
                               &replyPacket) );
  ASSERT( replyPacket != NULL );
  DELETE( packet );
  replyPacketProps = replyPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getLongProperty(IMQ_TRANSACTIONID_PROPERTY, transactionID) );  
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );

  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    MQError err = replyPacketProps->getStringProperty(IMQ_REASON_PROPERTY, &reason);
    if (err != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                   "[%s]:%s", PacketType::toString(replyPacket->getPacketType()), reason ));
    }
  }
  if (statusCode == STATUS_CONFLICT) {
    ERRCHK( MQ_TRANSACTION_ID_IN_USE );
  }
  ERRCHK( Status::toIMQError(statusCode) );
  DELETE( replyPacket );

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  DELETE( replyPacket );
  HANDLED_DELETE( packetProps );

  return errorCode;
}


MQError
ProtocolHandler::endTransaction(PRInt64 transactionID, XID *xid, long xaflags)
{
  static const char FUNCNAME[] = "endTransaction";
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Packet * replyPacket   = NULL;
  Properties * packetProps   = NULL;
  const Properties * replyPacketProps   = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_END_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  if (LL_IS_ZERO(transactionID) == 0) {
    ERRCHK( packetProps->setLongProperty(IMQ_TRANSACTIONID_PROPERTY, transactionID) );
  }
  if (xid != NULL) {
      ERRCHK( packetProps->setIntegerProperty(MQ_XAFLAGS_PROPERTY, (PRInt32)xaflags) );
      SerialDataOutputStream out;
      const PRUint8 * xidbytes;
      PRUint8 * xidclone;
      PRInt32 xidsize;

      XIDObject xidobj;
      ERRCHK( xidobj.copy(xid) );
      ERRCHK( xidobj.write( &out) );
      xidbytes = out.getStreamBytes();
      xidsize = out.numBytesWritten();
      MEMCHK ( xidclone = new PRUint8[xidsize] );
      memcpy(xidclone, xidbytes, xidsize);
      packet->setMessageBody(xidclone, xidsize);
      xidclone = NULL;
  }
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  ERRCHK( writePacketWithReply(packet,
                               PACKET_TYPE_END_TRANSACTION_REPLY,
                               &replyPacket) );
  ASSERT( replyPacket != NULL );
  DELETE( packet );
  replyPacketProps = replyPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );

  if (statusCode != STATUS_OK) {
    const char * reason = NULL;
    MQError err = replyPacketProps->getStringProperty(IMQ_REASON_PROPERTY, &reason);
    if (err != MQ_NOT_FOUND && reason != NULL) {
      ERROR_VTRACE(( FUNCNAME, __FILE__, __LINE__, "mq", Status::toIMQError(statusCode),
                   "[%s]:%s", PacketType::toString(replyPacket->getPacketType()), reason ));
    }
  }
  ERRCHK( Status::toIMQError(statusCode) );
  DELETE( replyPacket );

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  DELETE( replyPacket );
  HANDLED_DELETE( packetProps );

  return errorCode;
}


MQError
ProtocolHandler::prepareTransaction(PRInt64 transactionID, XID *xid)
{
  static const char FUNCNAME[] = "prepareTransaction";
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_PREPARE_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  if (LL_IS_ZERO(transactionID) == 0) {
    ERRCHK( packetProps->setLongProperty(IMQ_TRANSACTIONID_PROPERTY, transactionID) );
  }
  if (xid != NULL) {
    SerialDataOutputStream out;
    const PRUint8 * xidbytes;
    PRUint8 * xidclone;
    PRInt32 xidsize;

    XIDObject xidobj;
    ERRCHK( xidobj.copy(xid) );
    ERRCHK( xidobj.write(&out) );
    xidbytes = out.getStreamBytes();
    xidsize = out.numBytesWritten();
    MEMCHK ( xidclone = new PRUint8[xidsize] );
    memcpy(xidclone, xidbytes, xidsize);
    packet->setMessageBody(xidclone, xidsize);
    xidclone = NULL;
  }
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now


  ERRCHK( writePacketWithStatus(packet,
                                PACKET_TYPE_PREPARE_TRANSACTION_REPLY,
                                &statusCode) );
  DELETE( packet );

  if (statusCode == STATUS_BAD_REQUEST) {
    ERRCHK( MQ_INVALID_TRANSACTION_ID );
  }
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             MQ_SUCCESS, "Protocol::prepare() succeeded" ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  return errorCode;
}


MQError
ProtocolHandler::commitTransaction(PRInt64 transactionID,  XID *xid, 
                                   long xaflags, PRInt32 * const replyStatus)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_COMMIT_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  if (LL_IS_ZERO(transactionID) == 0) {
    ERRCHK( packetProps->setLongProperty(IMQ_TRANSACTIONID_PROPERTY, transactionID) );
  }
  if (xid != NULL) {
      ERRCHK( packetProps->setIntegerProperty(MQ_XAFLAGS_PROPERTY, (PRInt32)xaflags) );
      SerialDataOutputStream out;
      const PRUint8 * xidbytes;
      PRUint8 * xidclone;
      PRInt32 xidsize;

      XIDObject xidobj;
      ERRCHK( xidobj.copy(xid) );
      ERRCHK( xidobj.write(&out) );
      xidbytes = out.getStreamBytes();
      xidsize = out.numBytesWritten();
      MEMCHK ( xidclone = new PRUint8[xidsize] );
      memcpy(xidclone, xidbytes, xidsize);
      packet->setMessageBody(xidclone, xidsize);
      xidclone = NULL;
  }
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now


  ERRCHK( writePacketWithStatus(packet,
                               PACKET_TYPE_COMMIT_TRANSACTION_REPLY,
                               &statusCode) );
  DELETE( packet );

  *replyStatus = statusCode;
  if (statusCode == STATUS_BAD_REQUEST) {
    ERRCHK( MQ_INVALID_TRANSACTION_ID );
  }
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             MQ_SUCCESS, "Protocol::commit() succeeded" ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  return errorCode;
}


MQError
ProtocolHandler::rollbackTransaction(PRInt64 transactionID, XID * xid, PRBool setJMQRedeliver) 
{ 
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;

  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_ROLLBACK_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  if (LL_IS_ZERO(transactionID) == 0) {
    ERRCHK( packetProps->setLongProperty(IMQ_TRANSACTIONID_PROPERTY, transactionID) );
  }
  if (setJMQRedeliver == PR_TRUE) {
    ERRCHK( packetProps->setBooleanProperty(MQ_SET_REDELIVER_PROPERTY, PR_TRUE) );
  }
  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  if (xid != NULL) {
      SerialDataOutputStream out;
      const PRUint8 * xidbytes;
      PRUint8 * xidclone;
      PRInt32 xidsize;

      XIDObject xidobj;
      ERRCHK( xidobj.copy(xid) );
      ERRCHK( xidobj.write(&out) );
      xidbytes = out.getStreamBytes();
      xidsize = out.numBytesWritten();
      MEMCHK ( xidclone = new PRUint8[xidsize] );
      memcpy(xidclone, xidbytes, xidsize);
      packet->setMessageBody(xidclone, xidsize);
      xidclone = NULL;
  }

  ERRCHK( writePacketWithStatus(packet,
                               PACKET_TYPE_ROLLBACK_TRANSACTION_REPLY,
                               &statusCode) );
  DELETE( packet );

  if (statusCode == STATUS_BAD_REQUEST) {
    ERRCHK( MQ_INVALID_TRANSACTION_ID );
  }
  ERRCHK( Status::toIMQError(statusCode) );

  LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
             MQ_SUCCESS, "Protocol::rollback() succeeded" ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( packet );
  HANDLED_DELETE( packetProps );

  return errorCode;
}

/**
 *
 */
MQError
ProtocolHandler::recoverTransaction(long xaflags, ObjectVector ** const xidv) 
{ 
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRInt32 statusCode = 0;
  Packet * packet = NULL;
  Properties * packetProps = NULL;
  Packet * replyPacket = NULL;
  const Properties * replyPacketProps = NULL;
  const PRUint8 *    xids     = NULL;
  PRInt32            xidslen = 0;
  XIDObject * xidobj = NULL;
  ObjectVector * xidobjv = NULL;
  PRInt32 quantity = 0;

  if (xidv == NULL) return MQ_NULL_PTR_ARG;
  *xidv = NULL;
  MEMCHK( packet = new Packet() );
  packet->setPacketType(PACKET_TYPE_RECOVER_TRANSACTION);

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  ERRCHK( packetProps->getInitializationError() );
  ERRCHK( packetProps->setIntegerProperty(MQ_XAFLAGS_PROPERTY, (PRInt32)xaflags) );

  packet->setProperties(packetProps);
  packetProps = NULL; // packet is responsible now

  ERRCHK( writePacketWithReply(packet,
                               PACKET_TYPE_RECOVER_TRANSACTION_REPLY,
                               &replyPacket) );

  ASSERT( replyPacket != NULL );
  DELETE( packet );

  replyPacketProps = replyPacket->getProperties();
  CNDCHK( replyPacketProps == NULL,  MQ_PROTOCOL_HANDLER_UNEXPECTED_REPLY );
  ERRCHK( replyPacketProps->getIntegerProperty(IMQ_STATUS_PROPERTY, &statusCode) );
  ERRCHK( replyPacketProps->getIntegerProperty(MQ_QUANTITY_PROPERTY, &quantity) );
  ERRCHK( Status::toIMQError(statusCode) );

  xids    = replyPacket->getMessageBody();
  xidslen = replyPacket->getMessageBodySize(); 
  if ((xidslen%(XIDObject::xidSize())) != 0 || 
      (xidslen/(XIDObject::xidSize())) != quantity) {
    ERRCHK( MQ_INVALID_PACKET ); 
  }

  MEMCHK( xidobjv = new ObjectVector() );

  if (xids != NULL && xidslen != 0) {
    SerialDataInputStream xidsStream;
    ERRCHK( xidsStream.setNetOrderStream(xids, xidslen) );

    for (int cnt = 0; cnt < quantity; cnt++) { 
      MEMCHK ( xidobj = new XIDObject() );
      ERRCHK( xidobj->read(&xidsStream) );
      ERRCHK( xidobjv->add(xidobj) );
      LOG_FINE(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                 MQ_SUCCESS, "Protocol::recoverTransaction(%ld): %dth[%d]: %s", 
                 xaflags, cnt, quantity, xidobj->toString() ));
      xidobj = NULL;
    }
  }
  *xidv = xidobjv;

  DELETE( replyPacket );
  LOG_INFO(( CODELOC, PROTOCOL_HANDLER_LOG_MASK|XA_SWITCH_LOG_MASK,
             this->connection->id(), MQ_SUCCESS,
             "Protocol::recoverTransaction(%ld) succeeded: %d transactions recovered",
              xaflags, quantity ));

  return MQ_SUCCESS;
Cleanup:
  DELETE( xidobj );
  DELETE( xidobjv );
  DELETE( packet );
  DELETE( replyPacket );
  HANDLED_DELETE( packetProps );

  return errorCode;
}

/*
 *
 */
iMQError
ProtocolHandler::resumeFlow(PRBool consumerFlow, PRInt64 consumerID)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode     = IMQ_SUCCESS;
  Packet * resumePacket = NULL;
  Properties * packetProps = NULL;
  MEMCHK( resumePacket = new Packet() );

  resumePacket->setPacketType(PACKET_TYPE_RESUME_FLOW);
  

  MEMCHK( packetProps = new Properties(PR_TRUE) );
  if (consumerFlow == PR_TRUE) {
  ERRCHK( packetProps->setIntegerProperty(IMQ_SIZE_PROPERTY, connection->getNumMessagesBeforePausing()) );
  ERRCHK( packetProps->setLongProperty(IMQ_CONSUMERID_PROPERTY, consumerID) );
  } else {
  ERRCHK( packetProps->setIntegerProperty(IMQ_SIZE_PROPERTY,
                              connection->getNumMessagesBeforePausing()) );
  }
  resumePacket->setProperties(packetProps);
  packetProps = NULL; // resumePacket is responsible now
  
  ERRCHK( writePacketNoAck(resumePacket) );

Cleanup:
  HANDLED_DELETE( packetProps );
  DELETE( resumePacket );

  if (errorCode == IMQ_SUCCESS) {
    LOG_FINEST(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(), 
               IMQ_SUCCESS, "Resumed flow from the broker" ));
  } else {
    LOG_WARNING(( CODELOC, PROTOCOL_HANDLER_LOG_MASK, this->connection->id(),
                  IMQ_PROTOCOL_HANDLER_RESUME_FLOW_FAILED,
                  "Failed to resume flow from the broker because '%s' (%d)", 
                  errorStr(errorCode), errorCode ));
  }

  return errorCode;
}
