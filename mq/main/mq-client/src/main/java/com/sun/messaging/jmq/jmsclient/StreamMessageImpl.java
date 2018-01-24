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
 * @(#)StreamMessageImpl.java	1.25 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.io.*;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/** A StreamMessage is used to send a stream of Java primitives.
  * It is filled and read sequentially. It inherits from <CODE>Message</CODE>
  * and adds a stream message body. It's methods are based largely on those
  * found in <CODE>java.io.DataInputStream</CODE> and
  * <CODE>java.io.DataOutputStream</CODE>.
  *
  * <P>The primitive types can be read or written explicitly using methods
  * for each type. They may also be read or written generically as objects.
  * For instance, a call to <CODE>StreamMessage.writeInt(6)</CODE> is
  * equivalent to <CODE>StreamMessage.writeObject(new Integer(6))</CODE>.
  * Both forms are provided because the explicit form is convenient for
  * static programming and the object form is needed when types are not known
  * at compile time.
  *
  * <P>When the message is first created, and when <CODE>clearBody</CODE>
  * is called, the body of the message is in write-only mode. After the
  * first call to <CODE>reset</CODE> has been made, the message body is in
  * read-only mode. When a message has been sent, by definition, the
  * provider calls <CODE>reset</CODE> in order to read it's content, and
  * when a message has been received, the provider has called
  * <CODE>reset</CODE> so that the message body is in read-only mode for the client.
  *
  * <P>If <CODE>clearBody</CODE> is called on a message in read-only mode,
  * the message body is cleared and the message body is in write-only mode.
  *
  * <P>If a client attempts to read a message in write-only mode, a
  * MessageNotReadableException is thrown.
  *
  * <P>If a client attempts to write a message in read-only mode, a
  * MessageNotWriteableException is thrown.
  *
  * <P>Stream messages support the following conversion table. The marked cases
  * must be supported. The unmarked cases must throw a JMSException. The
  * String to primitive conversions may throw a runtime exception if the
  * primitives <CODE>valueOf()</CODE> method does not accept it as a valid
  * String representation of the primitive.
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
  * <P>Attempting to read a null value as a Java primitive type must be treated
  * as calling the primitive's corresponding <code>valueOf(String)</code>
  * conversion method with a null value. Since char does not support a String
  * conversion, attempting to read a null value as a char must throw
  * NullPointerException.
  *
  * @see         javax.jms.Session#createStreamMessage()
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.TextMessage
  */

public class StreamMessageImpl extends MessageImpl implements StreamMessage {

    private byte[] messageBody = null;
    //private byte[] defaultBytes = new byte [32];

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    private boolean bufferIsDirty = false;

    /**
     * Flag to indicate readBytes (byte[] buf) call status.
     */
    private boolean byteArrayReadState = false;

    //for byte array field only
    private ByteArrayInputStream byteArrayFieldInputStream = null;

    /**
     *
     */
    private Object notYetProcessedPrimitiveObject = null;

    protected StreamMessageImpl() throws JMSException {
        super();
        setPacketType (PacketType.STREAM_MESSAGE);
    }

    protected StreamMessageImpl(boolean constructOutputStream) throws JMSException {
        this();
        if ( constructOutputStream ) {
            initOutputStream();
        }
    }

    protected void initOutputStream() throws JMSException {
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream (byteArrayOutputStream);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_CAUGHT_EXCEPTION);
        }
    }

    //serialize message body
    //This is called when producing messages.
    protected void
    setMessageBodyToPacket() throws JMSException {
        reset();
        setMessageBody ( messageBody );
    }

    //deserialize message body
    //This is called after message is received in Session Reader.
    protected void
    getMessageBodyFromPacket() throws JMSException {
        messageBody = getMessageBody();
        reset();
    }

    private Object readPrimitiveObject() throws JMSException {
        Object obj = null;

        checkReadAccess();

        checkReadBytesState();

        try {
            if ( notYetProcessedPrimitiveObject != null ) {
                obj = notYetProcessedPrimitiveObject;
                notYetProcessedPrimitiveObject = null;
            } else {
                obj = objectInputStream.readObject();
            }
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString, AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_CAUGHT_EXCEPTION);
        }

        return obj;
    }

    private void writePrimitiveObject (Object obj) throws JMSException {

        checkMessageAccess();

        try {
            objectOutputStream.writeObject (obj);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_CAUGHT_EXCEPTION);
        }

        setBufferIsDirty (true);
    }

    /**
     * This method is called by every readXXX() method except readBytes.
     *
     * throws MessageFormatException if byteArrayReadState state is true.
     */
    private void checkReadBytesState() throws MessageFormatException {

        if ( byteArrayReadState ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }

    }

    protected void setBufferIsDirty (boolean state) {
        bufferIsDirty = state;
    }

    protected boolean getBufferIsDirty() {
        return bufferIsDirty;
    }

    public void clearBody() throws JMSException {

        messageBody = null;
        setMessageBody (null);

        initOutputStream();

        setMessageReadMode (false);
    }

    /** Read a <code>boolean</code> from the stream message.
      *
      * @return the <code>boolean</code> value read.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public boolean
    readBoolean() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toBoolean( obj );
    }


    /** Read a byte value from the stream message.
      *
      * @return the next byte from the stream message as a 8-bit
      * <code>byte</code>.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public byte
    readByte() throws JMSException {

        Object obj = readPrimitiveObject();

        try {
            byte b = ValueConvert.toByte( obj );
            return b;
        } catch (NumberFormatException nfe) {
            notYetProcessedPrimitiveObject = obj;
            throw nfe;
        }

    }


    /** Read a 16-bit number from the stream message.
      *
      * @return a 16-bit number from the stream message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public short
    readShort() throws JMSException {

        Object obj = readPrimitiveObject();
        return ValueConvert.toShort( obj );
    }


    /** Read a Unicode character value from the stream message.
      *
      * @return a Unicode character from the stream message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public char
    readChar() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toChar( obj );
    }


    /** Read a 32-bit integer from the stream message.
      *
      * @return a 32-bit integer value from the stream message, interpreted
      * as a <code>int</code>.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public int
    readInt() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toInt( obj );
    }


    /** Read a 64-bit integer from the stream message.
      *
      * @return a 64-bit integer value from the stream message, interpreted as
      * a <code>long</code>.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public long
    readLong() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toLong( obj );
    }


    /** Read a <code>float</code> from the stream message.
      *
      * @return a <code>float</code> value from the stream message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public float
    readFloat() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toFloat( obj );
    }


    /** Read a <code>double</code> from the stream message.
      *
      * @return a <code>double</code> value from the stream message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public double
    readDouble() throws JMSException {
        Object obj = readPrimitiveObject();
        return ValueConvert.toDouble( obj );
    }


    /** Read in a string from the stream message.
      *
      * @return a Unicode string from the stream message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public String
    readString() throws JMSException {

        Object obj = readPrimitiveObject();
        return ValueConvert.toString( obj );
    }


    /** Read a byte array field from the stream message into the
      * specified byte[] object (the read buffer).
      *
      * <P>To read the field value, readBytes should be successively called
      * until it returns a value less than the length of the read buffer.
      * The value of the bytes in the buffer following the last byte
      * read are undefined.
      *
      * <P>If readBytes returns a value equal to the length of the buffer, a
      * subsequent readBytes call must be made. If there are no more bytes
      * to be read this call will return -1.
      *
      * <P>If the bytes array field value is null, readBytes returns -1.
      *
      * <P>If the bytes array field value is empty, readBytes returns 0.
      *
      * <P>Once the first readBytes call on a byte[] field value has been done,
      * the full value of the field must be read before it is valid to read
      * the next field. An attempt to read the next field before that has
      * been done will throw a MessageFormatException.
      *
      * <P>To read the byte field value into a new byte[] object, use
      * the readObject method.
      *
      * @param value the buffer into which the data is read.
      *
      * @return the total number of bytes read into the buffer, or -1 if
      * there is no more data because the end of the byte field has been
      * reached.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid
      * @exception MessageEOFException if an end of message stream
      * @exception MessageNotReadableException if message in write-only mode.
      *
      * @see #readObject()
      */

    public int
    readBytes(byte[] value) throws JMSException {

        int bytesRead = -1;

        /**
         * No message body in the stream message.
         */
        if ( messageBody == null ) {
            checkReadAccess();
            return -1;
        }

        if ( byteArrayReadState == false ) {
            Object obj = readPrimitiveObject();
            if ( obj == null ) {
                return -1;
            }

            //throws MessageFormatException if not byte[] type
            if ( (obj instanceof byte[]) == false ) {
                String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
                throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
            }

            //cast to byte[]
            byte[] data = (byte[]) obj;

            /**
             * For empty byte[], the spec says to return 0
             */
            if ( data.length == 0 ) {
                return 0;
            }

            //construct new input stream
            byteArrayFieldInputStream = new ByteArrayInputStream ( data );

            /**
             * set flag to true so that no other read may be called
             */
            byteArrayReadState = true;
        }

        //read bytes from byte array stream
        bytesRead = byteArrayFieldInputStream.read(value, 0, value.length);

        if ( bytesRead < value.length ) {
            /**
             * Reset flag so that user can call other readXXX method.
             */
            byteArrayReadState = false;
            try {
                byteArrayFieldInputStream.close();
                byteArrayFieldInputStream = null;
            } catch (IOException e) {
                ExceptionHandler.handleException(e, AdministeredObject.cr.X_CAUGHT_EXCEPTION);
            }
        }

        return bytesRead;
    }


    /** Read a Java object from the stream message.
      *
      * <P>Note that this method can be used to return in objectified format,
      * an object that had been written to the Stream with the equivalent
      * <CODE>writeObject</CODE> method call, or it's equivalent primitive
      * write<type> method.
      *
      * <P>Note that byte values are returned as byte[], not Byte[].
      *
      * @return a Java object from the stream message, in objectified
      * format (ie. if it set as an int, then a Integer is returned).
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if an end of message stream
      * @exception MessageNotReadableException if message in write-only mode.
      */

    public Object
    readObject() throws JMSException {
        Object obj = readPrimitiveObject();
        return obj;
    }



    /** Write a <code>boolean</code> to the stream message.
      * The value <code>true</code> is written out as the value
      * <code>(byte)1</code>; the value <code>false</code> is written out as
      * the value <code>(byte)0</code>.
      *
      * @param value the <code>boolean</code> value to be written.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeBoolean(boolean value) throws JMSException {
        writePrimitiveObject( Boolean.valueOf (value) );
    }



    /** Write out a <code>byte</code> to the stream message.
      *
      * @param value the <code>byte</code> value to be written.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeByte(byte value) throws JMSException {
        writePrimitiveObject( Byte.valueOf(value) );
    }


    /** Write a <code>short</code> to the stream message.
      *
      * @param value the <code>short</code> to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeShort(short value) throws JMSException {
        writePrimitiveObject( Short.valueOf(value) );
    }


    /** Write a <code>char</code> to the stream message.
      *
      * @param value the <code>char</code> value to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeChar(char value) throws JMSException {
        writePrimitiveObject( Character.valueOf(value) );
    }


    /** Write an <code>int</code> to the stream message.
      *
      * @param value the <code>int</code> to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeInt(int value) throws JMSException {
        writePrimitiveObject( Integer.valueOf(value) );
    }


    /** Write a <code>long</code> to the stream message.
      *
      * @param value the <code>long</code> to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeLong(long value) throws JMSException {
        writePrimitiveObject( Long.valueOf(value) );
    }


    /** Write a <code>float</code> to the stream message.
      *
      * @param value the <code>float</code> value to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeFloat(float value) throws JMSException {
        writePrimitiveObject( Float.valueOf(value) );
    }


    /** Write a <code>double</code> to the stream message.
      *
      * @param value the <code>double</code> value to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeDouble(double value) throws JMSException {
        writePrimitiveObject( Double.valueOf(value) );
    }


    /** Write a string to the stream message.
      *
      * @param value the <code>String</code> value to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeString(String value) throws JMSException {
        writePrimitiveObject( value );
    }


    /** Write a byte array field to the stream message.
      *
      * <P>The byte array <code>value</code> is written as a byte array field
      * into the StreamMessage. Consecutively written byte array fields are
      * treated as two distinct fields when reading byte array fields.
      *
      * @param value the byte array to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeBytes(byte[] value) throws JMSException {
        writePrimitiveObject( value );
    }


    /** Write a portion of a byte array as a byte array field to the stream message.
      *
      * <P>The a portion of the byte array <code>value</code> is written as a
      * byte array field into the StreamMessage. Consecutively written byte
      * array fields are  treated as two distinct fields when reading byte array
      * fields.
      *
      * @param value the byte array value to be written.
      * @param offset the initial offset within the byte array.
      * @param length the number of bytes to use.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    writeBytes(byte[] value, int offset, int length) throws JMSException {

        byte[] out = new byte [length];
        System.arraycopy(value, offset, out, 0, length);

        writePrimitiveObject( out );
    }


    /** Write a Java object to the stream message.
      *
      * <P>Note that this method only works for the objectified primitive
      * object types (Integer, Double, Long ...), String's and byte arrays.
      *
      * @param value the Java object to be written.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception MessageFormatException if the object is invalid
      */

    public void
    writeObject(Object value) throws JMSException {
        checkValidObjectType (value);
        writePrimitiveObject( value );
    }

    private void
    checkValidObjectType (Object value) throws MessageFormatException {

        if ( value == null ) {
            return;
        }

        if ( value instanceof Boolean  ||
             value instanceof Byte     ||
             value instanceof Short    ||
             value instanceof Character||
             value instanceof Integer  ||
             value instanceof Long     ||
             value instanceof Float    ||
             value instanceof Double   ||
             value instanceof String   ||
             value instanceof byte[]) {
            //ok, do nothing
        } else {
            //throw exception
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }


    /** Put the message body in read-only mode, and reposition the stream
      * to the beginning.
      *
      * @exception JMSException if JMS fails to reset the message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if message has an invalid
      *                         format
      */

    public void
    reset() throws JMSException {

        try {

            if ( bufferIsDirty ) {
                objectOutputStream.flush();
                messageBody = byteArrayOutputStream.toByteArray();

                objectOutputStream.close();
                byteArrayOutputStream.close();
            }

            if (messageBody != null) {
                byteArrayInputStream = new ByteArrayInputStream ( messageBody );
                objectInputStream = new FilteringObjectInputStream ( byteArrayInputStream );
            }

        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_RESET, true);
        }

        setBufferIsDirty (false);
        setMessageReadMode (true);

        //reset flag
        byteArrayReadState = false;
        notYetProcessedPrimitiveObject = null;
    }

     public void dump (PrintStream ps) {
        ps.println ("------ StreamMessageImpl dump ------");
        super.dump (ps);
    }

    public String toString() {
        return super.toString();
    }
}
