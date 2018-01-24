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
 * @(#)Packet.cpp	1.19 06/26/07
 */ 

#include "Packet.hpp"
#include "PacketProperties.hpp"
#include "../util/UtilityMacros.h"
#include "../client/protocol/StubProtocolHandler.hpp"
#include "../cshim/mqheader-props.h"
#include "PacketFlag.hpp"
#include "PacketType.hpp"

#include <ctype.h>


/*
 *
 */
Packet::Packet()
{
  CHECK_OBJECT_VALIDITY();

  this->init();
}

/*
 *
 */
Packet::~Packet()
{
  CHECK_OBJECT_VALIDITY();

  this->reset();
}

/*
 *
 */
void
Packet::reset()
{
  CHECK_OBJECT_VALIDITY();

  headerStream.reset();
  
  // Delete the buffer that stores the packet after the header
  DELETE_ARR( packetBuffer );

  // Reset all of the optional header fields
  for (int i = Packet::MIN_VALID_ID; i <= Packet::MAX_VALID_ID; i++) {
    DELETE( variableHeaders[i] );
  }    

  DELETE( msgProperties );
  
  // Delete the msg body if needed
  resetMsgBody();

  DELETE_ARR( packetStr );

  // Reinitialize all fields
  init();
}

/**
 *
 */
void
Packet::resetMsgBody()
{
  CHECK_OBJECT_VALIDITY();

  if (deleteMsgBody) {
    DELETE_ARR( msgBody );
  }
  msgBody       = NULL;
  msgBodySize   = 0;
  deleteMsgBody = PR_FALSE;
}

/**
 *
 */
void
Packet::init()
{
  CHECK_OBJECT_VALIDITY();

  magic            = PACKET_MAGIC;
  version          = PACKET_VERSION; 
  packetType       = PACKET_TYPE_INVALID;
  packetSize       = 0;
  transactionID    = 0;
  producerID       = 0;
  expiration       = 0;        // 64 bit
  deliveryTime     = 0;        // 64 bit
  deliveryCount    = 0;        // 32 bit
  propertiesOffset = 0;
  propertiesSize   = 0;
  encryption       = 0;
  priority         = PACKET_DEFAULT_PRIORITY;
  bitFlags         = 0;
  consumerID       = PACKET_NULL_CONSUMER_ID;

  sysMessageID.reset();


  msgProperties    = NULL;

  packetBuffer     = NULL;
  msgBody          = NULL;
  msgBodySize      = 0;
  deleteMsgBody    = PR_FALSE;

  for (int i = Packet::MIN_VALID_ID; i <= Packet::MAX_VALID_ID; i++) {
    variableHeaders[i] = NULL;
  }  

  packetStr = NULL;
}



/*
 *
 */
MQError
Packet::validateHeader() const
{
  CHECK_OBJECT_VALIDITY();

  if ((packetSize < PACKET_HEADER_SIZE)                       ||
      (propertiesOffset < PACKET_HEADER_SIZE)                 ||
      (propertiesSize + propertiesOffset > packetSize))
  {
    return MQ_INVALID_PACKET;
  }

  return MQ_SUCCESS;
}


/*
 *
 */
MQError
Packet::readPacket(TransportProtocolHandler * const transport)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode;
  PRInt32 bytesRemaining;
  PRUint32 variableHeaderSize;
  PRUint32 propertiesIndex;
  PRUint32 payloadIndex;

  RETURN_ERROR_IF_NULL( transport );

  reset();

  // Read in the header and validate it.
  ERRCHK( this->readHeader(transport) );
  ERRCHK( validateHeader() );

  // Now read in the rest of the packet
  bytesRemaining = this->packetSize - PACKET_HEADER_SIZE;
  ASSERT( bytesRemaining >= 0 );
  MEMCHK( this->packetBuffer = new PRUint8[bytesRemaining] );
  ERRCHK( readFully(transport, this->packetBuffer, bytesRemaining) );

  // Read in the variable headers.  If the properties do not start immediately
  // after the header, then there are some variable headers.
  variableHeaderSize = this->propertiesOffset - PACKET_HEADER_SIZE;
  if (this->propertiesOffset != PACKET_HEADER_SIZE) {
    ERRCHK( readVariableHeader(packetBuffer, variableHeaderSize) );
  }

  // Read in the properties 
  // OPTIMIZATION: We could delay this until a getProperty call is made.
  propertiesIndex = this->propertiesOffset - PACKET_HEADER_SIZE;
  if (this->propertiesSize != 0) {
    ERRCHK( readProperties(&(packetBuffer[propertiesIndex]),
                           this->propertiesSize) );
  }

  // Set the msg payload pointer to point into the remaining bytes
  msgBodySize              = bytesRemaining - (variableHeaderSize +
                                               this->propertiesSize);
  ASSERT( this->packetSize == PACKET_HEADER_SIZE + 
                              variableHeaderSize + 
                              this->propertiesSize +
                              this->msgBodySize );
  payloadIndex = propertiesIndex + this->propertiesSize;
  this->msgBody         = &(this->packetBuffer)[payloadIndex];
  this->deleteMsgBody   = PR_FALSE;

  return MQ_SUCCESS;

 Cleanup:
  reset();
  return errorCode;
}


/*
 *
 */
MQError
Packet::readHeader(TransportProtocolHandler * const transport)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( transport );
  RETURN_IF_ERROR( readFully(transport, headerBuffer, sizeof(headerBuffer)) );
  
  
  RETURN_IF_ERROR( headerStream.setNetOrderStream(headerBuffer, 
                                                  sizeof(headerBuffer)) );

  RETURN_IF_ERROR( headerStream.readUint32(&(this->magic)) );
  RETURN_IF_ERROR( headerStream.readUint16(&(this->version)) );
  RETURN_IF_ERROR( headerStream.readUint16(&(this->packetType)) );
  RETURN_IF_ERROR( headerStream.readUint32(&(this->packetSize)) );

  // Check the magic number and version
  RETURN_ERROR_IF( this->magic != PACKET_MAGIC, MQ_BAD_PACKET_MAGIC_NUMBER );
  RETURN_ERROR_IF( this->version != PACKET_VERSION, MQ_UNSUPPORTED_PACKET_VERSION );

  RETURN_IF_ERROR( headerStream.readUint64(&(this->expiration)) );

  RETURN_IF_ERROR( sysMessageID.readID(&headerStream) );
  RETURN_IF_ERROR( headerStream.readUint32(&(this->propertiesOffset)) );
  RETURN_IF_ERROR( headerStream.readUint32(&(this->propertiesSize)) );
  RETURN_IF_ERROR( headerStream.readUint8(&(this->priority)) );
  RETURN_IF_ERROR( headerStream.readUint8(&(this->encryption)) );
  RETURN_IF_ERROR( headerStream.readUint16(&(this->bitFlags)) );
  RETURN_IF_ERROR( headerStream.readUint64(&(this->consumerID)) );

  return MQ_SUCCESS;
}




/**
 *
 */
MQError
Packet::readVariableHeader(const PRUint8 * const varHeaderBuffer,
                           const PRUint32        varHeaderBufferSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( varHeaderBuffer );
  ASSERT( varHeaderBufferSize > 0 );

  // Put the buffer in an input stream
  SerialDataInputStream varHeaderStream;
  RETURN_IF_ERROR( varHeaderStream.setNetOrderStream(varHeaderBuffer, 
                                                     varHeaderBufferSize) );

  while (!varHeaderStream.endOfStream()) {
    // Read in the variable header id
    PRInt16 varHeaderID = 0;
    RETURN_IF_ERROR( varHeaderStream.readInt16(&varHeaderID) );

    if (varHeaderID == (PRInt16)Packet::MESSAGE_ID_ID) 
    {
      //message id should be sysMessageID on receiving and 
      //MQ client runtime does not send user set messageid to broker
      UTF8String skipMessageID;
      RETURN_IF_ERROR( skipMessageID.read(&varHeaderStream) );
    }
    else
    // If the header ID is valid, then read it in
    if ((varHeaderID >= (PRInt16)Packet::MIN_VALID_ID) &&
        (varHeaderID <= (PRInt16)Packet::MAX_VALID_ID))
    {
      DELETE( variableHeaders[varHeaderID] );
      RETURN_IF_OUT_OF_MEMORY( variableHeaders[varHeaderID] = new UTF8String() );
      RETURN_IF_ERROR( variableHeaders[varHeaderID]->read(&varHeaderStream) );
    } 
    else if ( varHeaderID == (PRUint16)Packet::TRANSACTION_ID )
    {
      //no packet from broker requires transaction-id - 3.5 protocol
      PRUint64 skipTranID = 0;
      RETURN_IF_ERROR( varHeaderStream.readInt16(&varHeaderID) ); //skip length
      RETURN_IF_ERROR( varHeaderStream.readUint64(&skipTranID) );
    }
    else if ( varHeaderID == (PRUint16)Packet::PRODUCER_ID )
    {
      RETURN_IF_ERROR( varHeaderStream.readInt16(&varHeaderID) ); //skip length
      RETURN_IF_ERROR( varHeaderStream.readUint64(&producerID) );
    }
    else if ( varHeaderID == (PRUint16)Packet::DELIVERY_TIME )
    {
      RETURN_IF_ERROR( varHeaderStream.readInt16(&varHeaderID) ); //skip length
      RETURN_IF_ERROR( varHeaderStream.readUint64(&deliveryTime) );
    }
    else if ( varHeaderID == (PRUint16)Packet::DELIVERY_COUNT )
    {
      RETURN_IF_ERROR( varHeaderStream.readInt16(&varHeaderID) ); //skip length
      RETURN_IF_ERROR( varHeaderStream.readUint32(&deliveryCount) );
    }
    // Otherwise if it's the header terminator, then we've successfully read the
    // variable headers.
    else if (varHeaderID == (PRInt16)Packet::HEADER_TERMINATOR_ID) {
      break;
    }
    // Otherwise it's a header that we don't recognize.  So consume it and ignore it.
    else {
      UTF8String unrecognizedVariableHeader;
      RETURN_IF_ERROR( unrecognizedVariableHeader.readLengthBytes(&varHeaderStream, PR_FALSE) );
      // LOG: log something here
    }
  }

  return MQ_SUCCESS;
}

/**
 *
 */
MQError
Packet::readProperties(const PRUint8 * const propertiesBuffer,
                       const PRUint32        propertiesBufferSize)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( propertiesBuffer );
  ASSERT( propertiesBufferSize > 0 );

  // Put the buffer in an input stream
  SerialDataInputStream propertiesStream;
  RETURN_IF_ERROR( propertiesStream.setNetOrderStream(propertiesBuffer, 
                                                      propertiesBufferSize) );

  // Read in the serialized Java hashtable
  ASSERT( this->msgProperties == NULL );
  RETURN_IF_OUT_OF_MEMORY( this->msgProperties = new Properties(PR_TRUE) );
  RETURN_IF_ERROR( this->msgProperties->getInitializationError() );
  RETURN_IF_ERROR( PacketProperties::readProperties(&propertiesStream, this->msgProperties) );

  ASSERT( propertiesStream.endOfStream() );

  return MQ_SUCCESS;
}

/*
 *
 */
MQError
Packet::readFully(TransportProtocolHandler * const transport, 
                  PRUint8 * const buffer,
                  const PRInt32 amountToRead)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( transport );
  RETURN_ERROR_IF_NULL( buffer );

  PRInt32 amountRead = 0;

  RETURN_IF_ERROR( transport->read( amountToRead, 
                                    TRANSPORT_NO_TIMEOUT, 
                                    buffer, 
                                    &amountRead) );


  
  ASSERT( amountRead == amountToRead );

  return MQ_SUCCESS;
}

// This method ensures that all packet fields are properly initialized
MQError
Packet::checkPacketFields() const
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF(      magic != PACKET_MAGIC,     MQ_INVALID_PACKET_FIELD );
  RETURN_ERROR_IF(    version != PACKET_VERSION,   MQ_INVALID_PACKET_FIELD );
  //? RETURN_ERROR_IF( packetType == PACKET_NULL_CONSUMER_ID, MQ_INVALID_PACKET_FIELD );

  // This check could be more restictive by checking for the holds in PacketType.  But
  // most of the problems will be with PacketType::INVALID.
  RETURN_ERROR_IF( (packetType <= PACKET_TYPE_INVALID) || 
                   (packetType >= PACKET_TYPE_LAST),
                   MQ_INVALID_PACKET_FIELD );

  RETURN_ERROR_IF( (msgBodySize != 0) && (msgBody == NULL),
                   MQ_INVALID_PACKET_FIELD );

  return MQ_SUCCESS;
}


MQError
Packet::writePacket(TransportProtocolHandler * const transport, PRUint32 writeTimeout)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  RETURN_ERROR_IF_NULL( transport );

  RETURN_IF_ERROR( checkPacketFields() );

  // An output stream to hold the entire packet
  SerialDataOutputStream headerOut;

  // An output stream to hold the variable headers and the properties field
  SerialDataOutputStream varHeaderOut;

  // The total size of the packet is the size of 
  //    the fixed header (HEADER_SIZE)   +
  //    the size of the variable headers +
  //    the size of the properties       +
  //    the size of the message body
  // We don't know the size of the variable headers or the properties so we
  // have to write them out varHeaderOut before writing them out to the transport.

  // write out the variable header and the properties
  ERRCHK( writeVariableHeaders(&varHeaderOut) );
  ERRCHK( writeProperties(&varHeaderOut) );

  // Assign the next sequence number
  this->sysMessageID.setSequence(getNextSequenceNumber());

  // Generate a timestamp.  PR_Now() returns MICROseconds since 1/1/1970.
  // The timestamp is MILLIseconds since 1/1/1970.
  this->sysMessageID.setTimestamp(PR_Now() / 1000);
  

  // Now we can determine how big the packet is
  this->packetSize = PACKET_HEADER_SIZE             +
                     varHeaderOut.numBytesWritten() +
                     msgBodySize;
                     
  this->setMessageID(NULL);
  this->setRedelivered(PR_FALSE);

  // write the header out to an output stream
  ERRCHK( writeHeader(&headerOut) );
  ASSERT( (PRUint32)headerOut.numBytesWritten() == PACKET_HEADER_SIZE );
  CNDCHK( (PRUint32)headerOut.numBytesWritten() != PACKET_HEADER_SIZE, 
	       MQ_PACKET_OUTPUT_ERROR );

  //
  // Now actually each part of the packet out to a buffer
  //

  PRInt32 bytesWritten; // the number of bytes written to the socket

  // Write the header first
  ERRCHK( transport->write(headerOut.numBytesWritten(),
                           headerOut.getStreamBytes(),
                           writeTimeout,
                           &bytesWritten) );
  CNDCHK( bytesWritten != headerOut.numBytesWritten(), MQ_PACKET_OUTPUT_ERROR );
                           

  // Then the variable header and properties
  ERRCHK( transport->write(varHeaderOut.numBytesWritten(),
                           varHeaderOut.getStreamBytes(),
                           writeTimeout,
                           &bytesWritten) );
  CNDCHK( bytesWritten != varHeaderOut.numBytesWritten(), MQ_PACKET_OUTPUT_ERROR );

  // Then the message body
  ERRCHK( transport->write(this->msgBodySize,
                           this->msgBody,
                           writeTimeout,
                           &bytesWritten) );
  CNDCHK( bytesWritten != msgBodySize, MQ_PACKET_OUTPUT_ERROR );
                           
  
  return MQ_SUCCESS;

Cleanup:

  return errorCode;
}

/*
 *
 */
MQError
Packet::writeHeader(SerialDataOutputStream * const out) const
{
  CHECK_OBJECT_VALIDITY();

  //
  RETURN_ERROR_IF_NULL(out);

  RETURN_IF_ERROR( out->writeUint32(this->magic) );
  RETURN_IF_ERROR( out->writeUint16(this->version) );
  RETURN_IF_ERROR( out->writeUint16(this->packetType) );
  RETURN_IF_ERROR( out->writeUint32(this->packetSize) );
  RETURN_IF_ERROR( out->writeUint64(this->expiration) );
  RETURN_IF_ERROR( sysMessageID.writeID(out) );
  RETURN_IF_ERROR( out->writeUint32(this->propertiesOffset) );
  RETURN_IF_ERROR( out->writeUint32(this->propertiesSize) );
  RETURN_IF_ERROR( out->writeUint8(this->priority) );
  RETURN_IF_ERROR( out->writeUint8(this->encryption) );
  RETURN_IF_ERROR( out->writeUint16(this->bitFlags) );
  RETURN_IF_ERROR( out->writeUint64(this->consumerID) );

  return MQ_SUCCESS;
}

/*
 *
 */
MQError
Packet::writeVariableHeaders(SerialDataOutputStream * const out)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  // NOTE: This code violates the "Swift (JMQ 2.0) Packet Format
  // Version 1.0.3" document, which says "If there are no variable
  // headers, then the property data must start immediately after the
  // fixed header.  I.e. property_offset would be 72 and there would
  // be no TYPE=0 list terminator."  The Java MQ code violates this,
  // and we would rather be consistent with their implementation than
  // their spec.
    
  if (LL_IS_ZERO(transactionID) == 0) {
  RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::TRANSACTION_ID) );
  RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::TRANSACTION_ID) );
  RETURN_IF_ERROR( out->writeUint64(transactionID) );
  }

  if (LL_IS_ZERO(producerID) == 0 && this->getConsumerFlow() == PR_TRUE) {
  RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::PRODUCER_ID) );
  RETURN_IF_ERROR( out->writeUint16(sizeof(PRUint64)) );
  RETURN_IF_ERROR( out->writeUint64(producerID) );
  }

  if (LL_IS_ZERO(deliveryTime) == 0) {
  RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::DELIVERY_TIME) );
  RETURN_IF_ERROR( out->writeUint16(sizeof(PRUint64)) );
  RETURN_IF_ERROR( out->writeUint64(deliveryTime) );
  }

  if (variableHeaders[(PRUint16)Packet::DESTINATION_ID] != NULL) {
    RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::DESTINATION_ID) );
    RETURN_IF_ERROR( variableHeaders[(PRUint16)Packet::DESTINATION_ID]->write(out) );
  }

  // Write out rest of the variable headers that are valid, except MESSAGE_ID
  for( PRUint16 headerID =  (PRUint16)Packet::CORRELATION_ID_ID; 
                headerID <= (PRUint16)Packet::MAX_VALID_ID; 
                headerID++ ) 
  {
    if (variableHeaders[headerID] != NULL) {
      RETURN_IF_ERROR( out->writeUint16(headerID) );
      RETURN_IF_ERROR( variableHeaders[headerID]->write(out) );
    }
  }
  
  // Null terminate the list
  RETURN_IF_ERROR( out->writeUint16((PRUint16)Packet::HEADER_TERMINATOR_ID) );

  // Pad the list to a 4 byte boundary
  PRUint32 numBytesToPad = out->numBytesWritten() % 4;
  if (numBytesToPad != 0) {
    numBytesToPad = 4 - numBytesToPad;
  }
  RETURN_IF_ERROR( out->writeUint8Array(PACKET_ZERO_BYTES, numBytesToPad) );

  return MQ_SUCCESS;
}


/*
 *
 */
MQError
Packet::writeProperties(SerialDataOutputStream * const out)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );
  PRUint32 prevBytesWritten = 0;

  // Calculate the propertyOffset and initialize propertiesSize 
  this->propertiesOffset = out->numBytesWritten() + PACKET_HEADER_SIZE;
  ASSERT( this->propertiesOffset % 4 == 0 ); // must be aligned on 4-byte boundary
  this->propertiesSize = 0;

  // Write properties out to the buffer only if there are properties to write.
  PRUint32 numProperties = 0;
  if (this->msgProperties != NULL) {
    this->msgProperties->getNumKeys(&numProperties);
    if (numProperties != 0) {
      prevBytesWritten = out->numBytesWritten();
      RETURN_IF_ERROR( PacketProperties::writeProperties(out, this->msgProperties) );
      this->propertiesSize = out->numBytesWritten() - prevBytesWritten;
    }
  }

  return MQ_SUCCESS;
}




/**
 * 
 * @return the next sequence number
 */
PRUint32 Packet::sequenceNumber = PACKET_MIN_SEQUENCE_NUMBER;
Monitor Packet::seqnumMonitor;

PRUint32 
Packet::getNextSequenceNumber()
{
  seqnumMonitor.enter();
    PRUint32 seqnum = sequenceNumber;
    sequenceNumber = (sequenceNumber == PACKET_MAX_SEQUENCE_NUMBER) ? 
                                        PACKET_MIN_SEQUENCE_NUMBER  :
                                        sequenceNumber + 1;
  seqnumMonitor.exit();

  return seqnum;
}


//------------------------------------------------------------------------------
// Get/Set
//------------------------------------------------------------------------------

/*
 *
 */
PRBool
Packet::getFlag(const PRUint16 flag) const
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( (flag == PACKET_FLAG_IS_QUEUE)            ||
          (flag == PACKET_FLAG_REDELIVERED)         ||
          (flag == PACKET_FLAG_PERSISTENT)          ||
          (flag == PACKET_FLAG_SELECTORS_PROCESSED) ||
          (flag == PACKET_FLAG_SEND_ACK)            ||
          (flag == PACKET_FLAG_LAST_MESSAGE)        ||
          (flag == PACKET_FLAG_FLOW_PAUSED)         ||
          (flag == PACKET_FLAG_CONSUMER_FLOW_PAUSED) );

  return ((bitFlags & flag) == flag);
}


PRUint16
Packet::getVersion() const
{
  CHECK_OBJECT_VALIDITY();

  return version;
}

PRUint32
Packet::getMagic() const
{
  CHECK_OBJECT_VALIDITY();

  return magic;
}

PRUint16
Packet::getPacketType() const
{
  CHECK_OBJECT_VALIDITY();

  return packetType;
}

PRUint32
Packet::getPacketSize() const
{
  CHECK_OBJECT_VALIDITY();

  return packetSize;
}

PRUint64
Packet::getTimestamp() const
{
  CHECK_OBJECT_VALIDITY();

  return sysMessageID.getTimestamp();
}

PRUint64
Packet::getExpiration() const
{
  CHECK_OBJECT_VALIDITY();

  return expiration;
}

PRUint64
Packet::getDeliveryTime() const
{
  CHECK_OBJECT_VALIDITY();

  return deliveryTime;
}

PRUint32
Packet::getDeliveryCount() const
{
  CHECK_OBJECT_VALIDITY();

  return deliveryCount;
}

PRUint32
Packet::getPort() const
{
  CHECK_OBJECT_VALIDITY();

  return sysMessageID.getPort();
}

void
Packet::getIP(PRUint8 * const ipv6Addr) const
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.getIPv6Address(ipv6Addr);
}

PRUint32
Packet::getSequence() const
{
  CHECK_OBJECT_VALIDITY();

  return sysMessageID.getSequence();
}

PRUint8
Packet::getEncryption() const
{
  CHECK_OBJECT_VALIDITY();

  return encryption;
}

PRUint8
Packet::getPriority() const
{
  CHECK_OBJECT_VALIDITY();

  return priority;
}

PRUint64
Packet::getConsumerID() const
{
  CHECK_OBJECT_VALIDITY();

  return consumerID;
}

PRBool
Packet::getPersistent() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_PERSISTENT);
}

PRBool
Packet::getRedelivered() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_REDELIVERED);
}

PRBool
Packet::getIsQueue() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_IS_QUEUE);
}

PRBool
Packet::getSelectorsProcessed() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_SELECTORS_PROCESSED);
}

PRBool
Packet::getSendAcknowledge() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_SEND_ACK);
}

PRBool
Packet::getIsLast() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_LAST_MESSAGE);
}

PRBool
Packet::getConsumerFlow() const
{
  CHECK_OBJECT_VALIDITY();

  return getFlag(PACKET_FLAG_CONSUMER_FLOW_PAUSED);
}


const UTF8String *
Packet::getDestination() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::DESTINATION_ID);
}

const UTF8String *
Packet::getDestinationClass() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::DESTINATION_CLASS_ID);
}

const UTF8String *
Packet::getMessageID() const
{
  CHECK_OBJECT_VALIDITY();

  // If the client has set a MessageID then that is what is returned.
  // Otherwise, the system message ID is returned.
  if (variableHeaders[Packet::MESSAGE_ID_ID] == NULL) {
    // toString caches the string value, so it is a mutator, but we
    // want to treat it as a const.
    SysMessageID * msgID = (SysMessageID*)&(this->sysMessageID);
    return msgID->toString();
  } else {
    return getVariableHeader(Packet::MESSAGE_ID_ID);
  }
}

const UTF8String *
Packet::getCorrelationID() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::CORRELATION_ID_ID);
}

const UTF8String *
Packet::getReplyTo() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::REPLY_TO_ID);
}

const UTF8String *
Packet::getReplyToClass() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::REPLY_TO_CLASS_ID);
}

const UTF8String *
Packet::getMessageType() const
{
  CHECK_OBJECT_VALIDITY();

  return getVariableHeader(Packet::MESSAGE_TYPE_ID);
}

const UTF8String *
Packet::getVariableHeader(const VariableHeaderID headerID) const
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( (headerID >= Packet::MIN_VALID_ID) && 
          (headerID <= Packet::MAX_VALID_ID) );
  return variableHeaders[headerID];
}

const SysMessageID *
Packet::getSystemMessageID() const
{
  CHECK_OBJECT_VALIDITY();

  return &(this->sysMessageID);
}

const PRUint8 * 
Packet::getMessageBody() const
{
  CHECK_OBJECT_VALIDITY();

  return msgBody;
}

PRInt32
Packet::getMessageBodySize() const
{
  CHECK_OBJECT_VALIDITY();

  return msgBodySize;
}


IMQDataInputStream *
Packet::getMessageBodyStream() const
{
  CHECK_OBJECT_VALIDITY();

  SerialDataInputStream * input = NULL;
  if ((msgBodySize > 0) && 
      ((input = new SerialDataInputStream()) != NULL))
  {
    ASSERT( msgBody != NULL );
    input->setNetOrderStream(msgBody, msgBodySize);
  }
  
  return input;
}


const Properties *
Packet::getProperties() const
{
  CHECK_OBJECT_VALIDITY();

  return msgProperties;
}




/*
 *
 */
void
Packet::setFlag(const PRUint16 flag, const PRBool value) 
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( (flag == PACKET_FLAG_IS_QUEUE)            ||
          (flag == PACKET_FLAG_REDELIVERED)         ||
          (flag == PACKET_FLAG_PERSISTENT)          ||
          (flag == PACKET_FLAG_SELECTORS_PROCESSED) ||
          (flag == PACKET_FLAG_SEND_ACK)            ||
          (flag == PACKET_FLAG_LAST_MESSAGE)        ||
          (flag == PACKET_FLAG_FLOW_PAUSED)         ||
		  (flag == PACKET_FLAG_CONSUMER_FLOW_PAUSED) );

#ifdef DEBUG
  /** see below comment
  PRBool prevValue = getFlag(flag); **/
#endif

  if (value) {
    bitFlags |= flag;   // or in the flag
  } else {
    bitFlags &= ~flag;  // and out the flag
  }

#ifdef DEBUG
  /**XXX revisit cause failures in situations like
   * non-persistent message for both values would be false
   * set message headers twice for a same property 
   * reuse messageHandle in 2 send()
  ASSERT( getFlag(flag) == !prevValue ); **/
#endif
}



void
Packet::setPacketType(const PRUint16 packetTypeArg)
{
  CHECK_OBJECT_VALIDITY();

  this->packetType = packetTypeArg;
}

void
Packet::setTimestamp(const PRUint64 timeStampArg)
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.setTimestamp(timeStampArg);
}

void
Packet::setExpiration(const PRUint64 expirationArg)
{
  CHECK_OBJECT_VALIDITY();

  this->expiration = expirationArg;
}

void
Packet::setDeliveryTime(const PRUint64 deliveryTimeArg)
{
  CHECK_OBJECT_VALIDITY();

  this->deliveryTime = deliveryTimeArg;
}

void
Packet::setPort(const PRUint32 portArg)
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.setPort(portArg);
}

void
Packet::setIP(const PRUint8 * const ipv6AddrArg) 
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.setIPv6Address(ipv6AddrArg);
}

void
Packet::setIP(const IPAddress * const ipv6AddrArg) 
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.setIPv6Address(ipv6AddrArg);
}

void
Packet::setSequence(const PRUint32 sequenceArg)
{
  CHECK_OBJECT_VALIDITY();

  sysMessageID.setSequence(sequenceArg);
}

void
Packet::setEncryption(const PRUint8 encryptionArg)
{
  CHECK_OBJECT_VALIDITY();

  this->encryption = encryptionArg;
}

void
Packet::setPriority(const PRUint8 priorityArg)
{
  CHECK_OBJECT_VALIDITY();

  this->priority = priorityArg;
}

void
Packet::setConsumerID(const PRUint64 consumerIDArg)
{
  CHECK_OBJECT_VALIDITY();

  this->consumerID = consumerIDArg;
}

void
Packet::setPersistent(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_PERSISTENT, value);
}

void
Packet::setRedelivered(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_REDELIVERED, value);
}

void
Packet::setIsQueue(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_IS_QUEUE, value);
}

void
Packet::setSelectorsProcessed(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_SELECTORS_PROCESSED, value);
}

void
Packet::setSendAcknowledge(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_SEND_ACK, value);
}

void
Packet::setIsLast(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_LAST_MESSAGE, value);
}

void
Packet::setConsumerFlow(const PRBool value)
{
  CHECK_OBJECT_VALIDITY();

  setFlag(PACKET_FLAG_CONSUMER_FLOW_PAUSED, value);
}


void
Packet::setDestination(UTF8String * const destination)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::DESTINATION_ID, destination);
}

void
Packet::setDestinationClass(UTF8String * const destinationClass)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::DESTINATION_CLASS_ID, destinationClass);
}

void
Packet::setMessageID(UTF8String * const messageID)
{
  CHECK_OBJECT_VALIDITY();
  setVariableHeader(Packet::MESSAGE_ID_ID, messageID);
}

void
Packet::setCorrelationID(UTF8String * const correlationID)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::CORRELATION_ID_ID, correlationID);
}

void
Packet::setReplyTo(UTF8String * const replyTo)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::REPLY_TO_ID, replyTo);
}

void
Packet::setReplyToClass(UTF8String * const replyToClass)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::REPLY_TO_CLASS_ID, replyToClass);
}

void
Packet::setMessageType(UTF8String * const messageType)
{
  CHECK_OBJECT_VALIDITY();

  setVariableHeader(Packet::MESSAGE_TYPE_ID, messageType);
}


void
Packet::setTransactionID(const PRUint64 transactionIDArg)
{
  CHECK_OBJECT_VALIDITY();

  this->transactionID = transactionIDArg;
}

PRUint64
Packet::getTransactionID() const
{
  CHECK_OBJECT_VALIDITY();

  return transactionID;
}

void
Packet::setProducerID(const PRUint64 producerIDArg)
{
  CHECK_OBJECT_VALIDITY();

  this->producerID = producerIDArg;
}

PRUint64
Packet::getProducerID() const
{
  CHECK_OBJECT_VALIDITY();

  return producerID;
}



void
Packet::setVariableHeader(const VariableHeaderID headerID, 
                          UTF8String * const headerValue) 
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( (headerID >= Packet::MIN_VALID_ID) && 
          (headerID <= Packet::MAX_VALID_ID) );
  
  DELETE( variableHeaders[headerID] );
  variableHeaders[headerID] = headerValue;
}

void
Packet::setMessageBody(PRUint8 * const msgBodyArg,
                       const PRUint32 msgBodySizeArg )
{
  CHECK_OBJECT_VALIDITY();

  ASSERT( msgBodyArg >= 0 );
  ASSERT( (msgBodyArg == NULL) == (msgBodySizeArg == 0) );
  resetMsgBody();
  
  this->msgBody       = msgBodyArg;
  this->msgBodySize   = msgBodySizeArg;
  this->deleteMsgBody = PR_TRUE;
}

void
Packet::setProperties(Properties * const properties)
{
  CHECK_OBJECT_VALIDITY();

  DELETE( this->msgProperties );
  this->msgProperties = properties;
}


// Caller is responsible for freeing headers
MQError
Packet::getHeaders(Properties ** const headers) const
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  Properties * headerProps = NULL;
  const UTF8String * headerStr = NULL;
  NULLCHK(headers);
  *headers = NULL;

  MEMCHK( headerProps = new Properties );
  ERRCHK( headerProps->getInitializationError() );

  // Fill in headerProps
  ERRCHK( headerProps->setBooleanProperty( MQ_PERSISTENT_HEADER_PROPERTY, 
                                           this->getPersistent()) );
  ERRCHK( headerProps->setBooleanProperty( MQ_REDELIVERED_HEADER_PROPERTY, 
                                           this->getRedelivered()) );
  ERRCHK( headerProps->setLongProperty( MQ_EXPIRATION_HEADER_PROPERTY, 
                                        this->getExpiration()) );
  ERRCHK( headerProps->setLongProperty( MQ_DELIVERY_TIME_HEADER_PROPERTY, 
                                        this->getDeliveryTime()) );
  ERRCHK( headerProps->setByteProperty( MQ_PRIORITY_HEADER_PROPERTY, 
                                        this->getPriority()) );
  ERRCHK( headerProps->setLongProperty( MQ_TIMESTAMP_HEADER_PROPERTY, 
                                        this->getTimestamp()) );
  headerStr = this->getMessageType();
  if (headerStr != NULL) {
    ERRCHK( headerProps->setStringProperty( MQ_MESSAGE_TYPE_HEADER_PROPERTY, 
                                            headerStr->getCharStr()) );
  }
  headerStr = this->getMessageID();
  if (headerStr != NULL) {
    ERRCHK( headerProps->setStringProperty( MQ_MESSAGE_ID_HEADER_PROPERTY, 
                                            headerStr->getCharStr()) );
  }
  headerStr = this->getCorrelationID();
  if (headerStr != NULL) {
    ERRCHK( headerProps->setStringProperty( MQ_CORRELATION_ID_HEADER_PROPERTY, 
                                            headerStr->getCharStr()) );
  }

  *headers = headerProps;
    
  return MQ_SUCCESS;
Cleanup:
  DELETE( headerProps );

  return errorCode;
}


/*
 * 
 */
MQError
Packet::setHeaders(const Properties * const headers)
{
  CHECK_OBJECT_VALIDITY();

  MQError errorCode = MQ_SUCCESS;
  PRBool   headerMismatch = PR_FALSE;
  PRBool   boolProp  = 0;
  PRInt64  longProp  = 0;
  PRInt8   byteProp  = 0;
  PRInt16  shortProp = 0;
  const char * stringProp = NULL;
  UTF8String * headerStr = NULL;

  NULLCHK(headers);

  //
  // Set the headers 
  //

  // Set persistent
  errorCode = headers->getBooleanProperty(MQ_PERSISTENT_HEADER_PROPERTY, 
                                          &boolProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    this->setPersistent(boolProp);
  } 

  // Set redelivered
  errorCode = headers->getBooleanProperty(MQ_REDELIVERED_HEADER_PROPERTY, 
                                          &boolProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    this->setRedelivered(boolProp);
  }

  // Set expiration
  errorCode = headers->getLongProperty(MQ_EXPIRATION_HEADER_PROPERTY, 
                                       &longProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    this->setExpiration(longProp);
  }

  // Set priority
  errorCode = headers->getByteProperty(MQ_PRIORITY_HEADER_PROPERTY, 
                                       &byteProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    this->setPriority(byteProp);
  }

  // Set timestamp
  errorCode = headers->getLongProperty(MQ_TIMESTAMP_HEADER_PROPERTY, 
                                       &longProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    this->setTimestamp(longProp);
  }
  
  // Set message type
  errorCode = headers->getStringProperty(MQ_MESSAGE_TYPE_HEADER_PROPERTY, 
                                        &stringProp);
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    MEMCHK( headerStr = new UTF8String(stringProp) );
    this->setMessageType(headerStr);
  }

  // Set message id
  errorCode = headers->getStringProperty( MQ_MESSAGE_ID_HEADER_PROPERTY, 
                                          &stringProp );
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    MEMCHK( headerStr = new UTF8String(stringProp) );
    this->setMessageID(headerStr);
  }

  // Set correlation id
  errorCode = headers->getStringProperty( MQ_CORRELATION_ID_HEADER_PROPERTY, 
                                          &stringProp );
  headerMismatch = headerMismatch || (errorCode == MQ_PROPERTY_WRONG_VALUE_TYPE);
  if (errorCode == MQ_SUCCESS) {
    MEMCHK( headerStr = new UTF8String(stringProp) );
    this->setCorrelationID(headerStr);
  }

  // Return an error if the type of one of the properties is wrong
  CNDCHK( headerMismatch, MQ_PROPERTY_WRONG_VALUE_TYPE );
    
  return MQ_SUCCESS;
Cleanup:

  return errorCode;
}


/*
 *
 */
MQError
Packet::print(FILE * out)
{
  CHECK_OBJECT_VALIDITY();

  RETURN_ERROR_IF_NULL( out );

  fprintf(out, "\n------------------------------------------------------------\n");
  fprintf(out, "Magic:              %u\n", magic);
  fprintf(out, "Version:            %u\n", version);
  fprintf(out, "Packet Type:        %u\n", packetType);
  fprintf(out, "Packet Size:        %u\n", packetSize);
  Long transactionIDLong(transactionID);
  fprintf(out, "Transaction ID:     %s\n", transactionIDLong.toString());
  fprintf(out, "Source port:        %u\n", sysMessageID.getPort());
  fprintf(out, "Sequence #:         %u\n", sysMessageID.getSequence());
  fprintf(out, "Properties Offset:  %u\n", propertiesOffset);
  fprintf(out, "Properties Size:    %u\n", propertiesSize);
  fprintf(out, "Priority:           %u\n", priority);
  fprintf(out, "Encryption:         %u\n", encryption);
  fprintf(out, "Bit flags:          0x%04x\n", bitFlags);
  Long consumerIDLong(consumerID);
  fprintf(out, "Consumer ID:        %s\n", consumerIDLong.toString());
  Long producerIDLong(producerID);
  fprintf(out, "Producer ID:        %s\n", producerIDLong.toString());
  Long deliveryTimeLong(deliveryTime);
  fprintf(out, "Delivery Time:        %s\n", deliveryTimeLong.toString());
  fprintf(out, "Delivery Count:        %d\n", deliveryCount);

  for (int i = Packet::MIN_VALID_ID; i <= Packet::MAX_VALID_ID; i++) {
    fprintf(out, "%-18s", PACKET_VARIABLE_HEADER_NAMES[i]);
    variableHeaders[i]->print(out);
    fprintf(out, "\n");
  }
  fprintf(out,"\nMESSAGE PROPERTIES:\n");
  msgProperties->print(out);

  fprintf(out, "\n");


  return MQ_SUCCESS;
}

/*
 *
 */
const char *
Packet::toString()
{
  CHECK_OBJECT_VALIDITY();

  if (packetStr != NULL) {
    return packetStr;
  }
  packetStr = new char[PACKET_MAX_STR_SIZE];
  if (packetStr == NULL) {
    return "<packet>\n";
  }
  Long consumerIDLong(consumerID);
  Long transactionIDLong(transactionID);
  Long producerIDLong(producerID);

  SNPRINTF(packetStr, PACKET_MAX_STR_SIZE,
           "\n********************************************\n"
           "Packet type   = %s (%d)\n"                      // packetType
           "TransactionID = %s\n"                           // transactionID
           "Sequence      = %d\n"                           // sequenceID
           "Priority      = %d\n"                           // priority
           "Bit flags     = %s%s%s%s%s%s%s%s%s%s\n"         // bit flags
           "Consumer ID   = %s\n"                           // consumer ID
           "Producer ID   = %s\n"                           // producer ID
           "Msg body size = %d\n",                          // message body size
           
           PacketType::toString(packetType), packetType,    // packetType
           transactionIDLong.toString(),                    // transactionID
           sysMessageID.getSequence(),                      // sequenceID
           priority,                                        // priority
		   PacketFlag::isQueueStr(bitFlags),                // bitflags
           PacketFlag::isRedeliveredStr(bitFlags),          // bitflags
           PacketFlag::isPersistentStr(bitFlags),           // bitflags
           PacketFlag::isSelectorsProcessedStr(bitFlags),   // bitflags
           PacketFlag::isSendAckStr(bitFlags),              // bitflags
           PacketFlag::isLastMessageStr(bitFlags),          // bitflags
           PacketFlag::isFlowPausedStr(bitFlags),           // bitflags
           PacketFlag::isPartOfTransactionStr(bitFlags),    // bitflags
           PacketFlag::isConsumerFlowPausedStr(bitFlags),   // bitflags
           PacketFlag::isServerPacketStr(bitFlags),         // bitflags
           consumerIDLong.toString(),
           producerIDLong.toString(),
           msgBodySize);                                    // message body size

  // Variable headers
  for (int varID = Packet::MIN_VALID_ID; varID <= Packet::MAX_VALID_ID; varID++) {
    if (variableHeaders[varID] != NULL) {
      STRNCAT(packetStr, PACKET_VARIABLE_HEADER_NAMES[varID], PACKET_MAX_STR_SIZE);
      STRNCAT(packetStr, " = ",                               PACKET_MAX_STR_SIZE);
      STRNCAT(packetStr, variableHeaders[varID]->toString(),  PACKET_MAX_STR_SIZE);
      STRNCAT(packetStr, "\n",                                PACKET_MAX_STR_SIZE);
    }
  }

  // Properties
  if (msgProperties != NULL) {
    STRNCAT(packetStr, "Properties:\n",                 PACKET_MAX_STR_SIZE);
    STRNCAT(packetStr, msgProperties->toString("    "), PACKET_MAX_STR_SIZE);
  }

  packetStr[PACKET_MAX_STR_SIZE-1] = '\0';  // Null terminate just to be safe

  // Hack to convert the message body to printable form.
  // For non-printable characters, _ is used.
  if (msgBodySize > 0) {
    size_t loc = STRLEN(packetStr);
    for (int i = 0; i < msgBodySize; i++) {
      if (loc < PACKET_MAX_STR_SIZE - 2) {
        packetStr[loc] = isprint(msgBody[i]) ? msgBody[i] : '_';
        loc++;
      }
    }
    packetStr[loc] = '\0';
    STRNCAT(packetStr, "\n", PACKET_MAX_STR_SIZE);
  }

  packetStr[PACKET_MAX_STR_SIZE-1] = '\0';

  // If we filled up the buffer, then stick a "..." at the end so we know there is more
  if (STRLEN(packetStr) == PACKET_MAX_STR_SIZE - 1) {
    ASSERT( PACKET_MAX_STR_SIZE >= 5 );
    packetStr[PACKET_MAX_STR_SIZE-5] = '.';
    packetStr[PACKET_MAX_STR_SIZE-4] = '.';
    packetStr[PACKET_MAX_STR_SIZE-3] = '.';
    packetStr[PACKET_MAX_STR_SIZE-2] = '.';
    packetStr[PACKET_MAX_STR_SIZE-1] = '\0';
  }


  return packetStr;
}


void
Packet::dumpProperties(FILE * const out)
{
  CHECK_OBJECT_VALIDITY();

  PRUint32 propertiesIndex = this->propertiesOffset - PACKET_HEADER_SIZE;
  if (packetBuffer != NULL) {
    fwrite(&(this->packetBuffer[propertiesIndex]), this->propertiesSize, 1, out);
  }
}

void 
Packet::printProperties(FILE * const out)
{
  CHECK_OBJECT_VALIDITY();

  fprintf(out,"\n");
  msgProperties->print(out);
  fprintf(out,"\n");
}


// Input packets to test reading and writing.
static const int NUM_TEST_PACKETS = 30;
static const char * TEST_PACKETS_FILES_IN[NUM_TEST_PACKETS] =
{ // These are Java MQ packets that were written out to a file
  "fullPackets/pub_1.packet",
  "fullPackets/pub_2.packet", 
  "fullPackets/pub_3.packet", 
  "fullPackets/pub_4.packet", 
  "fullPackets/pub_5.packet", 
  "fullPackets/pub_6.packet", 
  "fullPackets/pub_7.packet", 
  "fullPackets/pub_8.packet", 
  "fullPackets/pub_9.packet",
  "fullPackets/sub_1.packet",
  "fullPackets/sub_2.packet", 
  "fullPackets/sub_3.packet", 
  "fullPackets/sub_4.packet", 
  "fullPackets/sub_5.packet", 
  "fullPackets/sub_6.packet",

  // These are the packets that we read from the file and then wrote back out
  "fullPackets/pub_1.out.packet",
  "fullPackets/pub_2.out.packet", 
  "fullPackets/pub_3.out.packet", 
  "fullPackets/pub_4.out.packet", 
  "fullPackets/pub_5.out.packet", 
  "fullPackets/pub_6.out.packet", 
  "fullPackets/pub_7.out.packet", 
  "fullPackets/pub_8.out.packet", 
  "fullPackets/pub_9.out.packet",
  "fullPackets/sub_1.out.packet",
  "fullPackets/sub_2.out.packet", 
  "fullPackets/sub_3.out.packet", 
  "fullPackets/sub_4.out.packet", 
  "fullPackets/sub_5.out.packet", 
  "fullPackets/sub_6.out.packet"};


static const char * TEST_PACKETS_FILES_OUT[NUM_TEST_PACKETS] =
{ "fullPackets/pub_1.out.packet",
  "fullPackets/pub_2.out.packet", 
  "fullPackets/pub_3.out.packet", 
  "fullPackets/pub_4.out.packet", 
  "fullPackets/pub_5.out.packet", 
  "fullPackets/pub_6.out.packet", 
  "fullPackets/pub_7.out.packet", 
  "fullPackets/pub_8.out.packet", 
  "fullPackets/pub_9.out.packet",
  "fullPackets/sub_1.out.packet",
  "fullPackets/sub_2.out.packet", 
  "fullPackets/sub_3.out.packet", 
  "fullPackets/sub_4.out.packet", 
  "fullPackets/sub_5.out.packet", 
  "fullPackets/sub_6.out.packet",

  "fullPackets/pub_1.out2.packet",
  "fullPackets/pub_2.out2.packet", 
  "fullPackets/pub_3.out2.packet", 
  "fullPackets/pub_4.out2.packet", 
  "fullPackets/pub_5.out2.packet", 
  "fullPackets/pub_6.out2.packet", 
  "fullPackets/pub_7.out2.packet", 
  "fullPackets/pub_8.out2.packet", 
  "fullPackets/pub_9.out2.packet",
  "fullPackets/sub_1.out2.packet",
  "fullPackets/sub_2.out2.packet", 
  "fullPackets/sub_3.out2.packet", 
  "fullPackets/sub_4.out2.packet", 
  "fullPackets/sub_5.out2.packet", 
  "fullPackets/sub_6.out2.packet" };

/*
 *
 */
static const PRInt32 MAX_FILE_NAME = 1000;
MQError 
Packet::test(const char * const inputFileBase)
{
  RETURN_ERROR_IF_NULL( inputFileBase );

  char inFile[MAX_FILE_NAME];
  char outFile[MAX_FILE_NAME];
  Packet packet;

  // Test reading and writing each packet
  for (int i = 0; i < NUM_TEST_PACKETS; i++) {
    // Set up the protocol handler that reads and writes to files instead of
    // the network.
    sprintf( inFile, "%s%s", inputFileBase, TEST_PACKETS_FILES_IN[i] );
    sprintf( outFile, "%s%s", inputFileBase, TEST_PACKETS_FILES_OUT[i]);
    StubProtocolHandler stub(inFile, outFile);

    packet.reset();
    RETURN_IF_ERROR( packet.readPacket(&stub) );
    RETURN_IF_ERROR( packet.writePacket(&stub, 0xFFFFFFFF) );
  }

  return MQ_SUCCESS;
}


