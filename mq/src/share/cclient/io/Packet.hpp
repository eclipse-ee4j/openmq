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
 * @(#)Packet.hpp	1.8 06/26/07
 */ 

#ifndef PACKET_HPP
#define PACKET_HPP

#include "../debug/DebugUtils.h"
#include "../error/ErrorCodes.h"
#include "../client/TransportProtocolHandler.hpp"
#include "../serial/SerialDataInputStream.hpp"
#include "../serial/SerialDataOutputStream.hpp"
#include "../containers/Properties.hpp"
#include "../util/PRTypesUtils.h"
#include "../basictypes/Monitor.hpp"
#include "../basictypes/Object.hpp"
#include "SysMessageID.hpp"
#include "PacketFlag.hpp"

#include <nspr.h>

static const char * PACKET_VARIABLE_HEADER_NAMES[] =  {"",
													   "Destination", 
													   "Message ID",
													   "Correlation ID",
													   "Reply To",
													   "Message Type",
													   "Destination Class",
													   "Reply To Class",
													   "Transaction ID",
                                                       "Producer ID" };


/** Pads output to 4 byte boundaries */
static const PRUint8  PACKET_ZERO_BYTES[8] = {0, 0, 0, 0, 0, 0, 0, 0};

/** Bounds of a packet sequence number */
static const PRUint32 PACKET_MAX_SEQUENCE_NUMBER = MAX_PR_INT32;
static const PRUint32 PACKET_MIN_SEQUENCE_NUMBER = 0;

/** Size of the packet header */
static const PRUint32 PACKET_HEADER_SIZE = 72;

/** Maximum size of a string representation of the packet */
static const PRInt32  PACKET_MAX_STR_SIZE = 2000;

/** Packet magic number. NEVER change this */
const static PRUint32  PACKET_MAGIC = 469754818;

/** Packet version number */
const static PRUint16  PACKET_VERSION = 301;

/** Default priority of the MQ message taken from ReadOnlyPacket.java */
const static PRUint8   PACKET_DEFAULT_PRIORITY = 5;

/** An invalid consumer id used for packet initialization */
const static PRUint64  PACKET_NULL_CONSUMER_ID = 0;

/**
 * This class is used to read and write packets from the transport.  It very
 * closely matches the Java implementation.
 */
class Packet : public Object {
protected:
  /**
   *
   */
  enum VariableHeaderID {
    HEADER_TERMINATOR_ID = 0,

    DESTINATION_ID       = 1,
    MESSAGE_ID_ID        = 2,
    CORRELATION_ID_ID    = 3,
    REPLY_TO_ID          = 4,
    MESSAGE_TYPE_ID      = 5,
    DESTINATION_CLASS_ID = 6,
    REPLY_TO_CLASS_ID    = 7,
    TRANSACTION_ID       = 8,
    PRODUCER_ID          = 9,
    DELIVERY_TIME        = 10,
    DELIVERY_COUNT       = 11,


    // ID of the smallest valid ID, ignore variable header items below this
    MIN_VALID_ID         = 1,  

    // ID of the largest valid ID for String variable Headers
    MAX_VALID_ID         = 7
  };
 

  static Monitor seqnumMonitor;
  static PRUint32 sequenceNumber;

  /** 
   * @return the next packet sequence number.  These are shared across
   * all connections.  
   */
  static PRUint32 getNextSequenceNumber();
protected:
  /**
   * headerBuffer is a byte array used to store the header.  This byte array is
   * passed to headerStream to create a SerialDataInputStream from which the
   * individual header fields are read.
   */
  PRUint8         headerBuffer[PACKET_HEADER_SIZE];


  /**
   * packetBuffer is a dynamically allocated array that stores the
   * contents of the packet after the fixed header.  
   */
  PRUint8 *       packetBuffer;

  /**
   * The message body bytes.
   *
   * If the packet was read from a transport, this is the bytes of the
   * message that occur after the properties field, and it is a pointer
   * into packetBuffer.  
   *
   * The only message body type that is initially supported is text
   * (i.e. a Java string).  
   */
  PRUint8 *       msgBody;

  /**
   * The number of bytes in the message body.
   */
  PRInt32         msgBodySize;

  /**
   * Controls whether msgBody is deleted.  True iff msgBody should be
   * deleted.  If msgBody is a pointer into packetBuffer (as will be
   * the case when a packet is read in), then msgBody will be a
   * pointer into packetBuffer and should not be deleted.  If msgBody
   * is set by the user, then it should be deleted.  
   */
  PRBool          deleteMsgBody;

  /** 
   * headerStream is used to read the header fields from the packet header.  It
   * handles converting from the network byte order stream headerBuffer to host
   * order fields.
   */
  SerialDataInputStream headerStream;

  //
  // The following fields appear in every header.
  //
  // TOOD:  maybe we should change these all to be signed because that's what Java does
  PRUint32        magic;
  PRUint16        version;
  PRUint16        packetType;
  PRUint32        packetSize;
  PRUint64        transactionID;
  PRUint64        producerID;
  PRUint64        expiration;
  PRUint64        deliveryTime;
  PRUint32        deliveryCount;
  PRUint32        propertiesOffset;
  PRUint32        propertiesSize;
  PRUint8         priority;
  PRUint8         encryption;
  PRUint16        bitFlags;
  PRUint64        consumerID;

  /** Holds sequence number, IP address, port, and timestamp */
  SysMessageID    sysMessageID;

  /**
   * The following are optional header values.  The index is a
   * Packet::VariableHeaderID.
   */
  UTF8String *    variableHeaders[MAX_VALID_ID+1];

  
  /**
   * The message properties.
   */
  Properties *    msgProperties;

  /**
   * This stores a string representation of the packet.  It is only used
   * for debugging.
   */
  char * packetStr;


  /**
   * This method ensures that all packet fields are properly initialized
   *
   * @return IMQ_SUCCESS if the packet fields have been correctly initialized
   * and an error otherwise
   */
  MQError checkPacketFields() const;

  /**
   * This method deallocates all memory associated with the packet.
   */
  void reset();

public:
  Packet();
  virtual ~Packet();


  /**
   * This method initializes this packet from the transport stream handler
   * transport.
   *
   * @param transport is the transport protocol used to read the packet
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError readPacket(TransportProtocolHandler * const transport);

  MQError writePacket(TransportProtocolHandler * const transport, PRUint32 writeTimeout);


  // Get/Set

  /**
   * This method returns PR_TRUE if the bit flag specified by flag is
   * set in the packet header and PR_FALSE otherwise.
   * 
   * @param flag to test for.  This should be one of the constants in PacketFlag. 
   * @returns PR_TRUE iff the bit flag specified by flag is set.  
   */
  PRBool getFlag(const PRUint16 flag) const;



  /**
   *
   */
  const UTF8String * getMessageID() const;

  /** 
   * This is not the JMS MessageID set by the client.  Rather this is
   * a system-wide unique message ID generated from the timestamp,
   * sequence number, port number, and IP address of the packet.
   *
   * WARNING! This returns a pointer to the Packet's SysMessageID not
   * a copy.  
   */
  const SysMessageID * getSystemMessageID() const; 

  /**
   * This method returns the message properties.
   *
   * WARNING! This returns a pointer to the properties not a copy.  Do
   * not change the properties class.  Specifically, take care when 
   * iterating through the keys because there can be only one active
   * iterator at a time.
   */
  const Properties * getProperties() const;

  /**
   * This method returns all JMS header properties in a Properties object.
   * The caller is responsible for freeing the returned object.
   *
   * @param headers the header properties that are returned
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError getHeaders(Properties ** const headers) const;

  /**
   * This method sets all JMS header properties based on the headers
   * Properties object. 
   *
   * @param headers the header properties to set
   * @returns IMQ_SUCCESS if successful and an error otherwise.  */
  MQError setHeaders(const Properties * const headers);

  /**
   * Return an input stream that contains the contents of the message
   * body.  
   *
   * @returns an input stream from which the message body can be read
   * or null if there is no message body.  
   */
  IMQDataInputStream * getMessageBodyStream() const;


  PRUint32 getMagic() const;
  PRUint16 getVersion() const;
  PRUint16 getPacketType() const;
  PRUint32 getPacketSize() const;
  PRUint64 getTimestamp() const;
  PRUint64 getExpiration() const;
  PRUint64 getDeliveryTime() const;
  PRUint32 getDeliveryCount() const;
  PRUint32 getPort() const;
  PRUint32 getSequence() const;
  PRUint8 getEncryption() const;
  PRUint8 getPriority() const;
  PRUint64 getConsumerID() const;
  PRBool getPersistent() const;
  PRBool getRedelivered() const;
  PRBool getIsQueue() const;
  PRBool getSelectorsProcessed() const;
  PRBool getSendAcknowledge() const;
  PRBool getIsLast() const;
  PRBool getConsumerFlow() const;
  const PRUint8 * getMessageBody() const;
  PRInt32 getMessageBodySize() const;
  void getIP(PRUint8 * const ipv6Addr) const;

  /**
   * For all methods that return a const object.  The caller should
   * not modify or delete the object.
   */
  const UTF8String * getDestination() const;
  const UTF8String * getDestinationClass() const;
  const UTF8String * getCorrelationID() const;
  const UTF8String * getReplyTo() const;
  const UTF8String * getReplyToClass() const;
  const UTF8String * getMessageType() const;

  const UTF8String * getVariableHeader(const VariableHeaderID headerID) const;
  

  // SET


  /**
   * This method sets the value for the bit flag in the packet header
   * specified by flag.
   * 
   * @param flag to set the value for.  This should be one of the
   * constants in PacketFlag.  
   */
  void setFlag(const PRUint16 flag, const PRBool value);

  
  void setPacketType(const PRUint16 packetType);
  void setTimestamp(const PRUint64 timeStamp);
  void setExpiration(const PRUint64 expiration);
  void setDeliveryTime(const PRUint64 deliverytime);
  void setPort(const PRUint32 port);
  
  /**
   * Sets the address of the message to ipv6Addr.  The ipv6Addr buffer
   * is copied.
   */
  void setIP(const PRUint8 * const ipv6Addr);
  void setIP(const IPAddress * const ipv6Addr);
  void setSequence(const PRUint32 sequence);
  void setEncryption(const PRUint8 encryption);
  void setPriority(const PRUint8 priority);
  void setConsumerID(const PRUint64 consumerID);
  void setPersistent(const PRBool value);
  void setRedelivered(const PRBool value);
  void setIsQueue(const PRBool value);
  void setSelectorsProcessed(const PRBool value);
  void setSendAcknowledge(const PRBool value);
  void setIsLast(const PRBool value);
  void setConsumerFlow(const PRBool value);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setDestination(UTF8String * const destination);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setDestinationClass(UTF8String * const destinationClass);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setMessageID(UTF8String * const messageID);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setCorrelationID(UTF8String * const correlationID);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setReplyTo(UTF8String * const replyTo);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setReplyToClass(UTF8String * const replyToClass);

  /**
   * This method calls setVariableHeader.  See that method's warning.
   * @see setVariableHeader
   */
  void setMessageType(UTF8String * const messageType);

  void setTransactionID(const PRUint64 transactionID);
  PRUint64 getTransactionID() const;

  void setProducerID(const PRUint64 producerID);
  PRUint64 getProducerID() const;

  /**
   * Set the variable header.
   *
   * @param headerID is the ID of the header to set
   * @param headerValue is the value for this header field.  
   *
   * WARNING! This object only stores the pointer, i.e. it does not
   * make a copy of headerValue.  This object is reponsible for
   * freeing headerValue.  The caller should not alter the headerValue
   * after calling this method.  
   */
  void setVariableHeader(const VariableHeaderID headerID, 
                         UTF8String * const headerValue);

  void setMessageBody(PRUint8 * const msgBody,
                      const PRUint32 msgBodySize);

  /**
   * Set the message properties. 
   *
   * @paran properties is what the message properties are set to.
   * This object only stores the pointer, i.e. it does not make a copy
   * of properties.  When setProperties is called again, the object is
   * reset, or deleted, it will delete the properties object.  The
   * caller should not alter the properties object.  
   */
  void setProperties(Properties * const properties);


  /**
   * This method prints the packet in text form to the file stream specified by
   * out.
   *
   * @param out is the output file to print the packet to.
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError print(FILE * out);

  /**
   * This method tests the other methods of this class.  
   *    
   * @param inputFileBase is the inputFile directory (e.g. "../../inputFiles")
   * @returns IMQ_SUCCESS if the test was successful and an error otherwise.  
   */
  static MQError test(const char * const inputFileBase);



  /** 
   * Primarily used for debugging.  This prints the properties.
   */
  void printProperties(FILE * const out);

  
  /** 
   * Primarily used for debugging.  Dumps the properties in serialized
   * binary format.  It can only be called on packets whose properties
   * were read off the wire and have not been changed.  
   */
  void dumpProperties(FILE * const out);

  // This is only used for debugging
  const char * toString();

protected:
  
  /**
   * This method initializes all fields of the packet.
   */
  void init();


  /**
   * This method resets the body of the message.
   */
  void resetMsgBody();

  /**
   * This method reads the header from the input stream specified by transport,
   * and initializes all header fields.
   *
   * @param transport is the transport protocol used to read the packet header
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError readHeader(TransportProtocolHandler * const transport);

  /**
   * This method reads in the variable headers from the packet.  The 
   * buffer varHeaderBuffer contains the variable headers.
   *
   * @param varHeaderBuffer is the buffer holding the variables headers
   * @param varHeaderBufferSize is the size of varHeaderBuffer
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError readVariableHeader(const PRUint8 * const varHeaderBuffer,
                              const PRUint32        varHeaderBufferSize);

  /**
   * This method reads in the properties field of the message.  This is a
   * Java serialized Hashtable.
   *
   * @param propertiesBuffer is the buffer holding the properties
   * @param propertiesBufferSize is the size of propertiesBuffer
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError readProperties(const PRUint8 * const propertiesBuffer,
                          const PRUint32        propertiesBufferSize);



  /**
   * This method reads amountToRead bytes from the tranport stream specified by
   * transport and places these bytes in buffer.  If the read is interrupted, it
   * is automatically restarted until amountToRead bytes are read or a more
   * severe error occurs.
   *
   * @param transport is the transport protocol read from
   * @param buffer is where the read bytes are placed
   * @param amountToRead is the number of bytes to read from transport
   * @returns IMQ_SUCCESS if successful and an error otherwise.  
   */
  MQError readFully(TransportProtocolHandler * const transport, 
                     PRUint8 * const buffer,
                     const PRInt32 amountToRead);

  /**
   * This method validates the fields in the header.  We are primarily
   * interested that the size and offset fields agree.  If an inconsistency is found,
   * an error is returned.
   * 
   * @returns IMQ_SUCCESS if the header is valid and an error otherwise. 
   */
  MQError validateHeader() const;


  MQError writeHeader(SerialDataOutputStream * const out) const;

  MQError writeVariableHeaders(SerialDataOutputStream * const out);
  MQError writeProperties(SerialDataOutputStream * const out);


//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Packet( const Packet& Packet );
  Packet& operator=( const Packet& Packet );
};

#endif // PACKET_HPP











