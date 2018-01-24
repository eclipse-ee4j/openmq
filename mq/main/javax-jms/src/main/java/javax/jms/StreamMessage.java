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

/** A {@code StreamMessage} object is used to send a stream of primitive
  * types in the Java programming language. It is filled and read sequentially.
  * It inherits from the {@code Message} interface
  * and adds a stream message body. Its methods are based largely on those
  * found in {@code java.io.DataInputStream} and
  * {@code java.io.DataOutputStream}.
  *
  * <P>The primitive types can be read or written explicitly using methods
  * for each type. They may also be read or written generically as objects.
  * For instance, a call to {@code StreamMessage.writeInt(6)} is
  * equivalent to {@code StreamMessage.writeObject(new Integer(6))}.
  * Both forms are provided, because the explicit form is convenient for
  * static programming, and the object form is needed when types are not known
  * at compile time.
  *
  * <P>When the message is first created, and when {@code clearBody}
  * is called, the body of the message is in write-only mode. After the 
  * first call to {@code reset} has been made, the message body is in 
  * read-only mode. 
  * After a message has been sent, the client that sent it can retain and 
  * modify it without affecting the message that has been sent. The same message
  * object can be sent multiple times.
  * When a message has been received, the provider has called 
  * {@code reset} so that the message body is in read-only mode for the client.
  * 
  * <P>If {@code clearBody} is called on a message in read-only mode, 
  * the message body is cleared and the message body is in write-only mode.
  * 
  * <P>If a client attempts to read a message in write-only mode, a 
  * {@code MessageNotReadableException} is thrown.
  * 
  * <P>If a client attempts to write a message in read-only mode, a 
  * {@code MessageNotWriteableException} is thrown.
  *
  * <P>{@code StreamMessage} objects support the following conversion 
  * table. The marked cases must be supported. The unmarked cases must throw a 
  * {@code JMSException}. The {@code String}-to-primitive conversions 
  * may throw a runtime exception if the primitive's {@code valueOf()} 
  * method does not accept it as a valid {@code String} representation of 
  * the primitive.
  *
  * <P>A value written as the row type can be read as the column type.
  *
  * <PRE>
  * |        | boolean byte short char int long float double String byte[]
  * |----------------------------------------------------------------------
  * |boolean |    X                                            X
  * |byte    |          X     X         X   X                  X   
  * |short   |                X         X   X                  X   
  * |char    |                     X                           X
  * |int     |                          X   X                  X   
  * |long    |                              X                  X   
  * |float   |                                    X     X      X   
  * |double  |                                          X      X   
  * |String  |    X     X     X         X   X     X     X      X   
  * |byte[]  |                                                        X
  * |----------------------------------------------------------------------
  * </PRE>
  *
  * <P>Attempting to read a null value as a primitive type must be treated
  * as calling the primitive's corresponding {@code valueOf(String)} 
  * conversion method with a null value. Since {@code char} does not 
  * support a {@code String} conversion, attempting to read a null value 
  * as a {@code char} must throw a {@code NullPointerException}.
  *
  * @see         javax.jms.Session#createStreamMessage()
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.TextMessage
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface StreamMessage extends Message {


    /** Reads a {@code boolean} from the stream message.
      *
      * @return the {@code boolean} value read
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */

    boolean 
    readBoolean() throws JMSException;


    /** Reads a {@code byte} value from the stream message.
      *
      * @return the next byte from the stream message as a 8-bit
      * {@code byte}
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    byte 
    readByte() throws JMSException;


    /** Reads a 16-bit integer from the stream message.
      *
      * @return a 16-bit integer from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    short 
    readShort() throws JMSException;


    /** Reads a Unicode character value from the stream message.
      *
      * @return a Unicode character from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid      
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    char 
    readChar() throws JMSException;


    /** Reads a 32-bit integer from the stream message.
      *
      * @return a 32-bit integer value from the stream message, interpreted
      * as an {@code int}
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    int 
    readInt() throws JMSException;


    /** Reads a 64-bit integer from the stream message.
      *
      * @return a 64-bit integer value from the stream message, interpreted as
      * a {@code long}
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    long 
    readLong() throws JMSException;


    /** Reads a {@code float} from the stream message.
      *
      * @return a {@code float} value from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    float 
    readFloat() throws JMSException;


    /** Reads a {@code double} from the stream message.
      *
      * @return a {@code double} value from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    double 
    readDouble() throws JMSException;


    /** Reads a {@code String} from the stream message.
      *
      * @return a Unicode string from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */ 

    String 
    readString() throws JMSException;


    /** Reads a byte array field from the stream message into the 
      * specified {@code byte[]} object (the read buffer). 
      * 
      * <P>To read the field value, {@code readBytes} should be 
      * successively called 
      * until it returns a value less than the length of the read buffer.
      * The value of the bytes in the buffer following the last byte 
      * read is undefined.
      * 
      * <P>If {@code readBytes} returns a value equal to the length of the 
      * buffer, a subsequent {@code readBytes} call must be made. If there 
      * are no more bytes to be read, this call returns -1.
      * 
      * <P>If the byte array field value is null, {@code readBytes} 
      * returns -1.
      *
      * <P>If the byte array field value is empty, {@code readBytes} 
      * returns 0.
      * 
      * <P>Once the first {@code readBytes} call on a {@code byte[]}
      * field value has been made,
      * the full value of the field must be read before it is valid to read 
      * the next field. An attempt to read the next field before that has 
      * been done will throw a {@code MessageFormatException}.
      * 
      * <P>To read the byte field value into a new {@code byte[]} object, 
      * use the {@code readObject} method.
      *
      * @param value the buffer into which the data is read
      *
      * @return the total number of bytes read into the buffer, or -1 if 
      * there is no more data because the end of the byte field has been 
      * reached
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      * 
      * @see #readObject()
      */ 

    int
    readBytes(byte[] value) throws JMSException;


    /** Reads an object from the stream message.
      *
      * <P>This method can be used to return, in objectified format,
      * an object in the Java programming language ("Java object") that has 
      * been written to the stream with the equivalent
      * {@code writeObject} method call, or its equivalent primitive
      * <code>write<I>type</I></code> method.
      *  
      * <P>Note that byte values are returned as {@code byte[]}, not 
      * {@code Byte[]}.
      *
      * <P>An attempt to call {@code readObject} to read a byte field 
      * value into a new {@code byte[]} object before the full value of the
      * byte field has been read will throw a 
      * {@code MessageFormatException}.
      *
      * @return a Java object from the stream message, in objectified
      * format (for example, if the object was written as an {@code int}, 
      * an {@code Integer} is returned)
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      * 
      * @see #readBytes(byte[] value)
      */ 

    Object 
    readObject() throws JMSException;



    /** Writes a {@code boolean} to the stream message.
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


    /** Writes a {@code byte} to the stream message.
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


    /** Writes a {@code short} to the stream message.
      *
      * @param value the {@code short} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeShort(short value) throws JMSException;


    /** Writes a {@code char} to the stream message.
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


    /** Writes an {@code int} to the stream message.
      *
      * @param value the {@code int} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeInt(int value) throws JMSException;


    /** Writes a {@code long} to the stream message.
      *
      * @param value the {@code long} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeLong(long value) throws JMSException;


    /** Writes a {@code float} to the stream message.
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


    /** Writes a {@code double} to the stream message.
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


    /** Writes a {@code String} to the stream message.
      *
      * @param value the {@code String} value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeString(String value) throws JMSException;


    /** Writes a byte array field to the stream message.
      *
      * <P>The byte array {@code value} is written to the message
      * as a byte array field. Consecutively written byte array fields are 
      * treated as two distinct fields when the fields are read.
      * 
      * @param value the byte array value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void
    writeBytes(byte[] value) throws JMSException;


    /** Writes a portion of a byte array as a byte array field to the stream 
      * message.
      *  
      * <P>The a portion of the byte array {@code value} is written to the
      * message as a byte array field. Consecutively written byte 
      * array fields are treated as two distinct fields when the fields are 
      * read.
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


    /** Writes an object to the stream message.
      *
      * <P>This method works only for the objectified primitive
      * object types ({@code Integer}, {@code Double}, 
      * {@code Long}&nbsp;...), {@code String} objects, and byte 
      * arrays.
      *
      * @param value the Java object to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageFormatException if the object is invalid.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    writeObject(Object value) throws JMSException;


    /** Puts the message body in read-only mode and repositions the stream
      * to the beginning.
      *  
      * @exception JMSException if the JMS provider fails to reset the message
      *                         due to some internal error.
      * @exception MessageFormatException if the message has an invalid
      *                                   format.
      */ 
 
    void
    reset() throws JMSException;
}
