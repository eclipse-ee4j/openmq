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
 * @(#)ReadChannel.cpp	1.22 10/17/07
 */ 

#include "ReadChannel.hpp"
#include "Connection.hpp"
#include "ProtocolHandler.hpp"
#include "../util/UtilityMacros.h"
#include "../util/LogUtils.hpp"
#include "../io/PacketType.hpp"
#include "ProducerFlow.hpp"

/*
 *
 */
ReadChannel::ReadChannel(Connection * const connectionArg)
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = MQ_SUCCESS;

  this->init();
  ASSERT( connectionArg != NULL );
  this->connection = connectionArg;
  NULLCHK( this->connection );

  this->connectionID = this->connection->id();

   // Start the thread that reads, decodes, and dispatches packets.
  monitor.enter();
  errorCode = this->connection->startThread(this);
  if (errorCode == MQ_SUCCESS) {
    this->isAlive = PR_TRUE;
  }
  monitor.exit();

Cleanup:
  this->initializationError = errorCode;
}

/*
 *
 */
ReadChannel::~ReadChannel()
{
  CHECK_OBJECT_VALIDITY();
  LOG_FINE(( CODELOC, CONNECTION_LOG_MASK, this->connectionID, IMQ_SUCCESS,
             "ReadChannel::~ReadChannel() called" ));

  // If the destructor is called, then we assume that Connection has already
  // shutdown the reader thread.
  ASSERT( this->abortConnection || this->closeConnection);
  
  this->init();
}

/*
 * Connection::openConnection calls this method to make sure that the
 * constructor was able to successfully create the reader thread.
 */
iMQError
ReadChannel::getInitializationError() const
{
  return this->initializationError;
}

/*
 *
 */
void
ReadChannel::init()
{
  CHECK_OBJECT_VALIDITY();

  this->connection           = NULL;
  this->connectionID         = NULL_CONN_ID;
  this->receivedGoodBye      = PR_FALSE;
  this->abortConnection      = PR_FALSE;
  this->closeConnection      = PR_FALSE;
  this->isAlive              = PR_FALSE;
  this->initializationError  = MQ_SUCCESS; 
  this->readerThread = NULL;
}


/*
 *
 */
void
ReadChannel::run()
{
  CHECK_OBJECT_VALIDITY();
  MQError errorCode = IMQ_SUCCESS;

  this->readerThread = PR_GetCurrentThread();

  monitor.enter();
  if (connection == NULL) {
    this->isAlive = PR_FALSE;
    monitor.notifyAll();
    monitor.exit();
    return;
  }
  monitor.exit();

  // It's okay to keep a pointer to the ProtocolHandler because
  // Connection won't delete it until after this thread has exited.
  ProtocolHandler * protocolHandler = connection->getProtocolHandler();
  if (protocolHandler == NULL) {
    this->abortConnection = PR_TRUE;
  }

  LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
             "ReadChannel::run starting called;  isAlive=%d "
             "this->abortConnection=%d "
             "this->closeConnection=%d "
             "this->receivedGoodBye=%d ", 
             this->isAlive, 
             this->abortConnection,
             this->closeConnection,
             this->receivedGoodBye ));

  // Until the connection to the broker goes away (due to Connection closing
  // it or an exception), read a packet and then dispatch that packet.
  while (!this->receivedGoodBye && !this->abortConnection && !this->closeConnection) {
    Packet * packet = NULL;
    
    LOG_FINEST(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
                 IMQ_SUCCESS, "ReadChannel::run() trying to read a packet." ));
    errorCode = protocolHandler->readPacket(&packet);
    if (errorCode == IMQ_SUCCESS) {
      LOG_FINEST(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
                   IMQ_SUCCESS, "ReadChannel::run() dispatching a packet." ));

      // Dispatch the packet
      errorCode = dispatch(packet);
      
      // Abort the connection if there was some unexpected error.
      if ((errorCode != IMQ_SUCCESS)                  &&
          (errorCode != IMQ_UNRECOGNIZED_PACKET_TYPE) &&
          (errorCode != IMQ_UNSUPPORTED_MESSAGE_TYPE))
      {
        this->abortConnection = PR_TRUE;
      }
    } else {
      DELETE( packet );
      LOG_INFO(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
                 "Encountered exception while reading iMQ packet: '%s' (%d)", 
                 errorStr(errorCode), errorCode ));
      this->abortConnection = PR_TRUE;
    }
  }

  this->connection->exitConnection(errorCode, PR_TRUE, abortConnection);

  // The 'this' pointer cannot be used after exitConnection returns
  // because the ReadChannel object might have been deleted by Connection.
  
  LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
             "ReadChannel::run() exiting because '%s' (%d) ",
             errorStr(errorCode), errorCode ));

  CLEAR_ERROR_TRACE(PR_TRUE);

  monitor.enter();
  this->isAlive = PR_FALSE;
  monitor.notifyAll();
  monitor.exit();
  return;
}

PRThread * 
ReadChannel::getReaderThread() const
{
  CHECK_OBJECT_VALIDITY();
  return this->readerThread;
}

/*
 * This is only called from from Connection::exitConnection, which can
 * be called from ReadChannel::run.  Connection::exitConnection is
 * protected by a monitor so there is no reason to protect
 * ReadChannel::exitConnection too. */
void
ReadChannel::exitConnection()
{
  CHECK_OBJECT_VALIDITY();

  /** If Connection::exitConnection (which calls this method) was not
   * called by ReadChannel::run, then setting this->closeConnection
   * to true and wait for ReadChannel::run to stop running. 
   */
  monitor.enter();
  this->closeConnection = PR_TRUE;
  while(this->isAlive) {
    LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
              "ReadChannel::exitConnection() waiting for ReadChannel thread to finish .."
             "this->abortConnection=%d "
             "this->closeConnection=%d "
             "this->receivedGoodBye=%d ", 
              this->abortConnection,
              this->closeConnection,
              this->receivedGoodBye ));
    monitor.wait();
  }
  monitor.exit();

  LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
             "ReadChannel::exitConnection() return;  isAlive=%d "
             "this->abortConnection=%d "
             "this->closeConnection=%d "
             "this->receivedGoodBye=%d ", 
             this->isAlive, 
             this->abortConnection,
             this->closeConnection,
             this->receivedGoodBye ));
}


/*
 *
 */
const PRInt64 INVALID_CONSUMER_ID = 0; 
iMQError
ReadChannel::dispatch(Packet * const packet)
{
  CHECK_OBJECT_VALIDITY();

  iMQError errorCode = IMQ_SUCCESS;

  PRInt64 consumerID = INVALID_CONSUMER_ID;
  Long consumerIDLong;
  PRBool isReceiveQ = PR_FALSE;
  NULLCHK( packet );

  switch (packet->getPacketType()) {
  case PACKET_TYPE_PING:
    // do nothing until broker needs a reply for later release ...
    LOG_FINER(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
                "Ignoring PING packet" ));
    delete packet;

    return IMQ_SUCCESS;

  case PACKET_TYPE_DEBUG:
    LOG_FINER(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, MQ_SUCCESS,
                "Ignoring %s packet", PacketType::toString(packet->getPacketType()) ));
    delete packet;

    return MQ_SUCCESS;

  // These are all control packet replies that the ProtocolHandler waits on.
  case PACKET_TYPE_HELLO_REPLY:
    {
    const Properties * props = NULL;
    PRInt64 connid = 0;
    props = packet->getProperties();
    CNDCHK( props == NULL, MQ_PROPERTY_NULL );
    MQError ecode = props->getLongProperty(IMQ_CONNECTIONID_PROPERTY, &connid);
    if (ecode == MQ_SUCCESS) { 
      this->connection->setid(connid);
      this->connectionID = this->connection->id();
    }
    }
  case PACKET_TYPE_ACKNOWLEDGE_REPLY:
  case PACKET_TYPE_DELETE_CONSUMER_REPLY:
  case PACKET_TYPE_ADD_PRODUCER_REPLY:
  case PACKET_TYPE_DELETE_PRODUCER_REPLY:
  case PACKET_TYPE_STOP_REPLY:
  case PACKET_TYPE_AUTHENTICATE_REQUEST:
  case PACKET_TYPE_AUTHENTICATE_REPLY:
  case PACKET_TYPE_SEND_REPLY:
  case PACKET_TYPE_CREATE_DESTINATION_REPLY:
  case PACKET_TYPE_VERIFY_DESTINATION_REPLY:
  case PACKET_TYPE_START_TRANSACTION_REPLY:
  case PACKET_TYPE_END_TRANSACTION_REPLY:
  case PACKET_TYPE_PREPARE_TRANSACTION_REPLY:
  case PACKET_TYPE_COMMIT_TRANSACTION_REPLY:
  case PACKET_TYPE_ROLLBACK_TRANSACTION_REPLY:
  case PACKET_TYPE_RECOVER_TRANSACTION_REPLY:
  case PACKET_TYPE_BROWSE_REPLY:
  case PACKET_TYPE_DELIVER_REPLY:
  case PACKET_TYPE_DESTROY_DESTINATION_REPLY:
  case PACKET_TYPE_SET_CLIENTID_REPLY:
  case PACKET_TYPE_CREATE_SESSION_REPLY:
  case PACKET_TYPE_DESTROY_SESSION_REPLY:
    consumerID = packet->getConsumerID();
    break;

  case PACKET_TYPE_ADD_CONSUMER_REPLY:
    {
    ReceiveQueue * receiveQ = NULL;
    MessageConsumer * consumer = NULL;
    Session *session = NULL;
    const Properties * props = NULL;
    PRInt64 interestID = 0;
    props = packet->getProperties();
    CNDCHK( props == NULL, IMQ_PROPERTY_NULL );
    MQError ecode = props->getLongProperty(IMQ_CONSUMERID_PROPERTY, &interestID);

    consumerID = packet->getConsumerID();
    ERRCHK ( connection->removeFromPendingConsumerTable(consumerID, &consumer) );
    NULLCHK( consumer ); 
    if (ecode == MQ_SUCCESS) {
      consumer->setConsumerID(interestID);
      session = consumer->getSession();
      NULLCHK( session );  //XXX
	  if (consumer->getReceiveMode() == SESSION_SYNC_RECEIVE) {
	     receiveQ = consumer->getReceiveQueue();
	  } else {
	     Session *session = consumer->getSession();
	     receiveQ = session->getSessionQueue();
      }
      NULLCHK( receiveQ );
      ERRCHK ( connection->addToReceiveQTable(interestID, receiveQ) );
      ERRCHK( session->addConsumer(interestID, consumer) );
    } 

    }
    break;
  case PACKET_TYPE_GOODBYE_REPLY:
    consumerID = packet->getConsumerID();

    // Setting this to true informs ReadChannel::run to stop running because
    // the connection has been closed.
    receivedGoodBye = PR_TRUE;
    break;
  case PACKET_TYPE_RESUME_FLOW:
    {
    const Properties * props = NULL;
    PRInt64 producerID = 0;
    props = packet->getProperties();
    CNDCHK( props == NULL, IMQ_PROPERTY_NULL );
    errorCode = props->getLongProperty(IMQ_PRODUCERID_PROPERTY, &producerID);
    Long producerIDLong(producerID);
    if (errorCode == MQ_SUCCESS) {
       LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, MQ_SUCCESS,
               "ReadChannel::dispatch: received RESUME_FLOW for producerID=%s",
                  producerIDLong.toString() ));
       ProducerFlow * producerFlow = NULL;
       errorCode = connection->getProducerFlow(producerID, &producerFlow); 
       if (errorCode == MQ_SUCCESS) {
         PRInt64 chunkBytes = -1;
         PRInt32 chunkSize = -1;
         ASSERT( producerFlow  != NULL);
         errorCode = props->getLongProperty(IMQ_BYTES_PROPERTY, &chunkBytes);
         if (errorCode == MQ_NOT_FOUND) {
           chunkBytes = -1;
         } else if (errorCode != MQ_SUCCESS) {
           connection->releaseProducerFlow(&producerFlow);
           ERRCHK( errorCode );
         }
         errorCode = props->getIntegerProperty(IMQ_SIZE_PROPERTY, &chunkSize);
         if (errorCode == MQ_NOT_FOUND) {
           chunkSize = -1;
         } else if (errorCode != MQ_SUCCESS) {
           connection->releaseProducerFlow(&producerFlow);
           ERRCHK( errorCode );
         }
         LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, MQ_SUCCESS,
                    "ReadChannel::dispatch: RESUME_FLOW for producerID=%s, chunkSize=%d",
                     producerIDLong.toString(), chunkSize ));
         producerFlow->resumeFlow(chunkBytes, chunkSize);
         connection->releaseProducerFlow(&producerFlow);
       } else if (errorCode == MQ_NOT_FOUND) {
         LOG_INFO(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, MQ_SUCCESS,
                 "ReadChannel::dispatch: couldn't find producerFlow for producerID=%s for packet: %s",
                  producerIDLong.toString(), ((packet == NULL) ? "NULL-PACKET" : packet->toString()) ));
       } else { ERRCHK( errorCode ); } 
    } else if (errorCode != MQ_NOT_FOUND) {
      ERRCHK( errorCode );
    }

    }
    delete packet;
    return MQ_SUCCESS;

  case PACKET_TYPE_TEXT_MESSAGE:
  case PACKET_TYPE_BYTES_MESSAGE:
  case PACKET_TYPE_MESSAGE:
    consumerID = packet->getConsumerID();
    isReceiveQ = PR_TRUE;

    // Notify the flow control handler that a message was received
    connection->messageReceived();

    // If the broker is notifying us that message delivery is paused,
    // then try to resume the flow of messages.
    if (packet->getFlag(PACKET_FLAG_FLOW_PAUSED)) {
      connection->requestResume();
    }
    if (packet->getFlag(PACKET_FLAG_CONSUMER_FLOW_PAUSED)) {
      errorCode = connection->requestResumeConsumer(consumerID);
      if (errorCode != MQ_SUCCESS) {
      LOG_WARNING(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, errorCode,
                 "ReadChannel: process consumer resume flow request failed for message packet: %s",
                  packet->toString() ));
      }
    }
    break;
    
    
  case PACKET_TYPE_GOODBYE:
    // Setting this to true informs ReadChannel::run to stop running because
    // the connection has been closed.
    receivedGoodBye = PR_TRUE;
    LOG_INFO(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, MQ_SUCCESS,
              "ReadChannel:: received GOODBYE from broker" ));
    delete packet;
    return IMQ_BROKER_CONNECTION_CLOSED;
    
  case PACKET_TYPE_MAP_MESSAGE:
  case PACKET_TYPE_OBJECT_MESSAGE:
  case PACKET_TYPE_STREAM_MESSAGE:
    consumerID = packet->getConsumerID();
    ERRCHK( IMQ_UNSUPPORTED_MESSAGE_TYPE );
    break;
    
  default:
    consumerID = packet->getConsumerID();
    ERRCHK( IMQ_UNRECOGNIZED_PACKET_TYPE );
    break;
  };

  //CNDCHK( LL_EQ(consumerID,INVALID_CONSUMER_ID) != 0, IMQ_INVALID_CONSUMER_ID );

  LOG_FINER(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, IMQ_SUCCESS,
              "About to enqueue incoming %s packet for consumerID=%lld",
              PacketType::toString(packet->getPacketType()), consumerID ));

  if (isReceiveQ == PR_TRUE) {
  errorCode = connection->enqueueReceiveQPacket(consumerID, packet);
  } else {
  errorCode = connection->enqueueAckQPacket(consumerID, packet);
  }

  if (errorCode == IMQ_NOT_FOUND) { //consumer was closed or wait-ack thread timed out
     if (isReceiveQ) {
     LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
              IMQ_READ_CHANNEL_DISPATCH_ERROR,
             "Couldn't find message consumer (consumerID=%lld) for %s packet (type=%d)", 
             consumerID,
             (packet == NULL) ? "NULL-PACKET" : PacketType::toString(packet->getPacketType()),
             (packet == NULL) ? -1 : packet->getPacketType() ));
     }

     LOG_FINE(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
                  IMQ_READ_CHANNEL_DISPATCH_ERROR,
                 "ReadChannel::dispatch: couldn't find ReceiveQueue for consumerID=%lld for packet: %s", 
                  consumerID, ((packet == NULL) ? "NULL-PACKET" : packet->toString()) ));
  } else {
    ERRCHK ( errorCode );
  }

  return IMQ_SUCCESS;

Cleanup:

  // This below line comment obsolete, see above
  // This most likely happened because the consumer (e.g. subscriber) was closed
  {
  Long consumerIDLong(consumerID);
  LOG_INFO(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
             IMQ_READ_CHANNEL_DISPATCH_ERROR,
             "Couldn't dispatch %s packet (type=%d) because  '%s' (%d), consumerID=%s", 
             (packet == NULL) ? "NULL-PACKET" : PacketType::toString(packet->getPacketType()),
             (packet == NULL) ? -1 : packet->getPacketType(),
             errorStr(errorCode), errorCode, consumerIDLong.toString() ));
  }
  LOG_FINEST(( CODELOC, READ_CHANNEL_LOG_MASK, this->connectionID, 
             IMQ_READ_CHANNEL_DISPATCH_ERROR,
             "ReadChannel::dispatch packet failed: %s", 
             (packet == NULL) ? "NULL-PACKET" : packet->toString() ));

  delete packet;

  return errorCode;
}


