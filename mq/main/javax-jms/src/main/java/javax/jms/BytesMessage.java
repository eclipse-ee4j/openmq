/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;


/** A {@code BytesMessage} object is used to send a message containing a 
  * stream of uninterpreted bytes. It inherits from the {@code Message} 
  * interface and adds a bytes
  * message body. The receiver of the message supplies the interpretation
  * of the bytes.
  *
  * <P>The {@code BytesMessage} methods are based largely on those found in
  * {@code java.io.DataInputStream} and
  * {@code java.io.DataOutputStream}.
  *
  * <P>This message type is for client encoding of existing message formats. 
  * If possible, one of the other self-defining message types should be used 
  * instead.
  *
  * <P>Although the JMS API allows the use of message properties with byte 
  * messages, they are typically not used, since the inclusion of properties 
  * may affect the format.
  *
  * <P>The primitive types can be written explicitly using methods
  * for each type. They may also be written generically as objects.
  * For instance, a call to {@code BytesMessage.writeInt(6)} is
  * equivalent to {@code BytesMessage.writeObject(new Integer(6))}.
  * Both forms are provided, because the explicit form is convenient for
  * static programming, and the object form is needed when types are not known
  * at compile time.
  *
  * <P>When the message is first created, and when {@code clearBody}
  * is called, the body of the message is in write-only mode. After the 
  * first call to {@code reset} has been made, the message body is in 
  * read-only mode. 
  * When a {@code BytesMessage} is sent asynchronously, the provider 
  * must call {@code reset} on the {@code BytesMessage} passed to 
  * the {@code CompletionListener}. This means that the 
  * {@code CompletionListener} can read the message body without 
  * needing to call {@code reset}. 
  * After a message has been sent, the client that sent it can retain and 
  * modify it without affecting the message that has been sent. The same message
  * object can be sent multiple times.
  * When a message has been received, the provider has called 
  * {@code reset} so that the message body is in read-only mode for the client.
  *
  * <P>If {@code clearBody} is called on a message in read-only mode, 
  * the message body is cleared and the message is in write-only mode.
  *
  * <P>If a client attempts to read a message in write-only mode, a 
  * {@code MessageNotReadableException} is thrown.
  *
  * <P>If a client attempts to write a message in read-only mode, a 
  * {@code MessageNotWriteableException} is thrown.
  *
  * @see         javax.jms.Session#createBytesMessage()
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface BytesMessage extends Message {
    
     /** Gets the number of bytes of the message body when the message
       * is in read-only mode. The value returned can be used to allocate 
       * a byte array. The value returned is the entire length of the message
       *  body, regardless of where the pointer for reading the message 
       * is currently located.
       * 
       * @return number of bytes in the message 
       * @exception JMSException if the JMS provider fails to read the message 
       *                         due to some internal error.
       * @exception MessageNotReadableException if the message is in write-only
       *                         mode.
       * @since JMS 1.1 
       */
       
      long getBodyLength() throws JMSException;

    /** Reads a {@code boolean} from the bytes message stream.
      *
      * @return the {@code boolean} value read
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    
 
    boolean 
    readBoolean() throws JMSException;


    /** Reads a signed 8-bit value from the bytes message stream.
      *
      * @return the next byte from the bytes message stream as a signed 8-bit
      * {@code byte}
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    byte 
    readByte() throws JMSException;


    /** Reads an unsigned 8-bit number from the bytes message stream.
      *  
      * @return the next byte from the bytes message stream, interpreted as an
      * unsigned 8-bit number
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */

    int
    readUnsignedByte() throws JMSException;


    /** Reads a signed 16-bit number from the bytes message stream.
      *
      * @return the next two bytes from the bytes message stream, interpreted as
      * a signed 16-bit number
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    short 
    readShort() throws JMSException;


    /** Reads an unsigned 16-bit number from the bytes message stream.
      *  
      * @return the next two bytes from the bytes message stream, interpreted as
      * an unsigned 16-bit integer
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 
 
    int
    readUnsignedShort() throws JMSException;


    /** Reads a Unicode character value from the bytes message stream.
      *
      * @return the next two bytes from the bytes message stream as a Unicode
      * character
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    char 
    readChar() throws JMSException;


    /** Reads a signed 32-bit integer from the bytes message stream.
      *
      * @return the next four bytes from the bytes message stream, interpreted
      * as an {@code int}
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    int 
    readInt() throws JMSException;


    /** Reads a signed 64-bit integer from the bytes message stream.
      *
      * @return the next eight bytes from the bytes message stream, interpreted
      * as a {@code long}
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    long 
    readLong() throws JMSException;


    /** Reads a {@code float} from the bytes message stream.
      *
      * @return the next four bytes from the bytes message stream, interpreted
      * as a {@code float}
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    float 
    readFloat() throws JMSException;


    /** Reads a {@code double} from the bytes message stream.
      *
      * @return the next eight bytes from the bytes message stream, interpreted
      * as a {@code double}
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    double 
    readDouble() throws JMSException;


    /** Reads a string that has been encoded using a modified UTF-8
      * format from the bytes message stream.
      *
      * <P>For more information on the UTF-8 format, see "File System Safe
      * UCS Transformation Format (FSS_UTF)", X/Open Preliminary Specification,
      * X/Open Company Ltd., Document Number: P316. This information also
      * appears in ISO/IEC 10646, Annex P.
      *
      * @return a Unicode string from the bytes message stream
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of bytes stream has 
      *                                been reached.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    String 
    readUTF() throws JMSException;


    /** Reads a byte array from the bytes message stream.
      *
      * <P>If the length of array {@code value} is less than the number of 
      * bytes remaining to be read from the stream, the array should 
      * be filled. A subsequent call reads the next increment, and so on.
      * 
      * <P>If the number of bytes remaining in the stream is less than the 
      * length of 
      * array {@code value}, the bytes should be read into the array. 
      * The return value of the total number of bytes read will be less than
      * the length of the array, indicating that there are no more bytes left 
      * to be read from the stream. The next read of the stream returns -1.
      *
      * @param value the buffer into which the data is read
      *
      * @return the total number of bytes read into the buffer, or -1 if 
      * there is no more data because the end of the stream has been reached
      *
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    int
    readBytes(byte[] value) throws JMSException;


    /** Reads a portion of the bytes message stream.
      *
      * <P>If the length of array {@code value} is less than the number of
      * bytes remaining to be read from the stream, the array should 
      * be filled. A subsequent call reads the next increment, and so on.
      * 
      * <P>If the number of bytes remaining in the stream is less than the 
      * length of 
      * array {@code value}, the bytes should be read into the array. 
      * The return value of the total number of bytes read will be less than
      * the length of the array, indicating that there are no more bytes left 
      * to be read from the stream. The next read of the stream returns -1.
      *
      * <p> If {@code length} is negative, or
      * {@code length} is greater than the length of the array
      * {@code value}, then an {@code IndexOutOfBoundsException} is
      * thrown. No bytes will be read from the stream for this exception case.
      *  
      * @param value the buffer into which the data is read
      * @param length the number of bytes to read; must be less than or equal to
      *        {@code value.length}
      * 
      * @return the total number of bytes read into the buffer, or -1 if
      * there is no more data because the end of the stream has been reached
      *  
      * @exception JMSException if the JMS provider fails to read the message 
      *                         due to some internal error.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    int
    readBytes(byte[] value, int length) 
			throws JMSException;


    /** Writes a {@code boolean} to the bytes message stream as a 1-byte 
      * value.
      * The value {@code true} is written as the value 
      * {@code (byte)1}; the value {@code false} is written as 
      * the value {@code (byte)0}.
      *
      * @param value the {@code boolean} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */

    void 
    writeBoolean(boolean value) 
			throws JMSException;


    /** Writes a {@code byte} to the bytes message stream as a 1-byte 
      * value.
      *
      * @param value the {@code byte} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeByte(byte value) throws JMSException;


    /** Writes a {@code short} to the bytes message stream as two bytes,
      * high byte first.
      *
      * @param value the {@code short} to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeShort(short value) throws JMSException;


    /** Writes a {@code char} to the bytes message stream as a 2-byte
      * value, high byte first.
      *
      * @param value the {@code char} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeChar(char value) throws JMSException;


    /** Writes an {@code int} to the bytes message stream as four bytes, 
      * high byte first.
      *
      * @param value the {@code int} to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeInt(int value) throws JMSException;


    /** Writes a {@code long} to the bytes message stream as eight bytes, 
      * high byte first.
      *
      * @param value the {@code long} to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeLong(long value) throws JMSException;


    /** Converts the {@code float} argument to an {@code int} using 
      * the
      * {@code floatToIntBits} method in class {@code Float},
      * and then writes that {@code int} value to the bytes message
      * stream as a 4-byte quantity, high byte first.
      *
      * @param value the {@code float} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeFloat(float value) throws JMSException;


    /** Converts the {@code double} argument to a {@code long} using 
      * the
      * {@code doubleToLongBits} method in class {@code Double},
      * and then writes that {@code long} value to the bytes message
      * stream as an 8-byte quantity, high byte first.
      *
      * @param value the {@code double} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeDouble(double value) throws JMSException;


    /** Writes a string to the bytes message stream using UTF-8 encoding in a 
      * machine-independent manner.
      *
      * <P>For more information on the UTF-8 format, see "File System Safe 
      * UCS Transformation Format (FSS_UTF)", X/Open Preliminary Specification,       
      * X/Open Company Ltd., Document Number: P316. This information also 
      * appears in ISO/IEC 10646, Annex P. 
      *
      * @param value the {@code String} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeUTF(String value) throws JMSException;


    /** Writes a byte array to the bytes message stream.
      *
      * @param value the byte array to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void
    writeBytes(byte[] value) throws JMSException;


    /** Writes a portion of a byte array to the bytes message stream.
      *  
      * @param value the byte array value to be written
      * @param offset the initial offset within the byte array
      * @param length the number of bytes to use
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 
 
    void
    writeBytes(byte[] value, int offset, int length) 
			throws JMSException;


    /** Writes an object to the bytes message stream.
      *
      * <P>This method works only for the objectified primitive
      * object types ({@code Integer}, {@code Double}, 
      * {@code Long}&nbsp;...), {@code String} objects, and byte 
      * arrays.
      *
      * @param value the object in the Java programming language ("Java 
      *              object") to be written; it must not be null
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageFormatException if the object is of an invalid type.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      * @exception java.lang.NullPointerException if the parameter 
      *                                           {@code value} is null.
      */ 

    void 
    writeObject(Object value) throws JMSException;


    /** Puts the message body in read-only mode and repositions the stream of 
      * bytes to the beginning.
      *  
      * @exception JMSException if the JMS provider fails to reset the message
      *                         due to some internal error.
      * @exception MessageFormatException if the message has an invalid
      *                         format.
      */ 

    void
    reset() throws JMSException;
}
