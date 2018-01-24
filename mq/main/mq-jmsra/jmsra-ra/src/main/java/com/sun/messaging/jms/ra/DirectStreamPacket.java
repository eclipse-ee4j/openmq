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

package com.sun.messaging.jms.ra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsservice.JMSService;
//import com.sun.messaging.jmq.io.JMQByteArrayOutputStream;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/** 
 *  A <CODE>StreamMessage</CODE> object is used to send a stream of primitive
 *  types in the Java programming language. It is filled and read sequentially.
 *  It inherits from the <CODE>Message</CODE> interface
 *  and adds a stream message body. Its methods are based largely on those
 *  found in <CODE>java.io.DataInputStream</CODE> and
 *  <CODE>java.io.DataOutputStream</CODE>.
 *
 *  <P>The primitive types can be read or written explicitly using methods
 *  for each type. They may also be read or written generically as objects.
 *  For instance, a call to <CODE>StreamMessage.writeInt(6)</CODE> is
 *  equivalent to <CODE>StreamMessage.writeObject(new Integer(6))</CODE>.
 *  Both forms are provided, because the explicit form is convenient for
 *  static programming, and the object form is needed when types are not known
 *  at compile time.
 *
 *  <P>When the message is first created, and when <CODE>clearBody</CODE>
 *  is called, the body of the message is in write-only mode. After the 
 *  first call to <CODE>reset</CODE> has been made, the message body is in 
 *  read-only mode. 
 *  After a message has been sent, the client that sent it can retain and 
 *  modify it without affecting the message that has been sent. The same message
 *  object can be sent multiple times.
 *  When a message has been received, the provider has called 
 *  <CODE>reset</CODE> so that the message body is in read-only mode for the client.
 * 
 *  <P>If <CODE>clearBody</CODE> is called on a message in read-only mode, 
 *  the message body is cleared and the message body is in write-only mode.
 * 
 *  <P>If a client attempts to read a message in write-only mode, a 
 *  <CODE>MessageNotReadableException</CODE> is thrown.
 * 
 *  <P>If a client attempts to write a message in read-only mode, a 
 *  <CODE>MessageNotWriteableException</CODE> is thrown.
 *
 *  <P><CODE>StreamMessage</CODE> objects support the following conversion 
 *  table. The marked cases must be supported. The unmarked cases must throw a 
 *  <CODE>JMSException</CODE>. The <CODE>String</CODE>-to-primitive conversions 
 *  may throw a runtime exception if the primitive's <CODE>valueOf()</CODE> 
 *  method does not accept it as a valid <CODE>String</CODE> representation of 
 *  the primitive.
 *
 *  <P>A value written as the row type can be read as the column type.
 *
 *  <PRE>
 *  |        | boolean byte short char int long float double String byte[]
 *  |----------------------------------------------------------------------
 *  |boolean |    X                                            X
 *  |byte    |          X     X         X   X                  X   
 *  |short   |                X         X   X                  X   
 *  |char    |                     X                           X
 *  |int     |                          X   X                  X   
 *  |long    |                              X                  X   
 *  |float   |                                    X     X      X   
 *  |double  |                                          X      X   
 *  |String  |    X     X     X         X   X     X     X      X   
 *  |byte[]  |                                                        X
 *  |----------------------------------------------------------------------
 *  </PRE>
 *
 *  <P>Attempting to read a null value as a primitive type must be treated
 *  as calling the primitive's corresponding <code>valueOf(String)</code> 
 *  conversion method with a null value. Since <code>char</code> does not 
 *  support a <code>String</code> conversion, attempting to read a null value 
 *  as a <code>char</code> must throw a <code>NullPointerException</code>.
 *
 *  @see         javax.jms.Session#createStreamMessage()
 *  @see         javax.jms.BytesMessage
 *  @see         javax.jms.MapMessage
 *  @see         javax.jms.Message
 *  @see         javax.jms.ObjectMessage
 *  @see         javax.jms.TextMessage
 */
public class DirectStreamPacket
        extends DirectPacket
        implements javax.jms.StreamMessage {

    private byte[] messageBody = null;

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    /** Indicates whether this JMS BytesMessage has been written to or not */
    private boolean writePerformed = false;

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

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectStreamPacket";

    /**
     *  Create a new instance of DirectStreamPacket.<p>
     *
     *  Used by the createStreamMessage API
     */
    public DirectStreamPacket(DirectSession ds)
    throws JMSException {
        super(ds);
        if (_logFINE){
            Object params[] = new Object[2];
            params[0] = ds;
            _loggerOC.entering(_className, "constructor()", params);
        }
        this._initializeOutputStreams();
    }

    /**
     *  Create a new instance of DirectStreamPacket.<p>
     *
     *  Used by Consumer.deliver.
     */
    public DirectStreamPacket(JMSPacket jmsPacket, long consumerId,
            DirectSession ds, JMSService jmsservice)
    throws JMSException {
        super(jmsPacket, consumerId, ds, jmsservice);
        this._getMessageBodyFromPacket();
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.StreamMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Clear out the message body .
     */
    public void clearBody()
    throws JMSException {
        super.clearBody();
        this.messageBody = null;
        this._initializeOutputStreams();
    }

    /** 
     *  Read a <code>boolean</code> from the stream message.
     *
     *  @return The <code>boolean</code> value read
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of message stream has
     *          been reached.     
     *  @throws MessageFormatException if this type conversion is invalid.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */
    public boolean readBoolean()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readBoolean()");
        return ConvertValue.toBoolean(obj);
    }

    /** Reads a <code>byte</code> value from the stream message.
      *
      * @return the next byte from the stream message as a 8-bit
      * <code>byte</code>
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    public byte readByte()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readByte()");
        try {
            byte b = ConvertValue.toByte(obj);
            return b;
        } catch (NumberFormatException nfe) {
            notYetProcessedPrimitiveObject = obj;
            throw nfe;
        }
    }

    /** Reads a byte array field from the stream message into the 
      * specified <CODE>byte[]</CODE> object (the read buffer). 
      * 
      * <P>To read the field value, <CODE>readBytes</CODE> should be 
      * successively called 
      * until it returns a value less than the length of the read buffer.
      * The value of the bytes in the buffer following the last byte 
      * read is undefined.
      * 
      * <P>If <CODE>readBytes</CODE> returns a value equal to the length of the 
      * buffer, a subsequent <CODE>readBytes</CODE> call must be made. If there 
      * are no more bytes to be read, this call returns -1.
      * 
      * <P>If the byte array field value is null, <CODE>readBytes</CODE> 
      * returns -1.
      *
      * <P>If the byte array field value is empty, <CODE>readBytes</CODE> 
      * returns 0.
      * 
      * <P>Once the first <CODE>readBytes</CODE> call on a <CODE>byte[]</CODE>
      * field value has been made,
      * the full value of the field must be read before it is valid to read 
      * the next field. An attempt to read the next field before that has 
      * been done will throw a <CODE>MessageFormatException</CODE>.
      * 
      * <P>To read the byte field value into a new <CODE>byte[]</CODE> object, 
      * use the <CODE>readObject</CODE> method.
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
    public int readBytes(byte[] value)
    throws JMSException {
        int bytesRead = -1;

        /**
         * No message body in the stream message.
         */
        if (this.messageBody == null) {
            this.checkForWriteOnlyMessageBody("readBytes(byte[])");
            return -1;
        }

        if ( byteArrayReadState == false ) {
            Object obj = this._readPrimitiveObject("readBytes(byte[])");
            if (obj == null) {
                return -1;
            }

            //throws MessageFormatException if not byte[] type
            if ((obj instanceof byte[]) == false ) {
                String errorString = "Exception:StreamMessage:readBytes():" +
                        "Object read is not byte[]:Actual type="+
                        obj.getClass().getName();
                throw new MessageFormatException(errorString);
            }

            //cast to byte[]
            byte[] data = (byte[]) obj;

            /**
             * For empty byte[], the spec says to return 0
             */
            if (data.length == 0) {
                return 0;
            }

            //construct new input stream
            byteArrayFieldInputStream = new ByteArrayInputStream (data);

            /**
             * set flag to true so that no other read may be called
             */
            byteArrayReadState = true;
        }

        //read bytes from byte array stream
        bytesRead = byteArrayFieldInputStream.read(value, 0, value.length);

        if (bytesRead < value.length) {
            /**
             * Reset flag so that user can call other readXXX method.
             */
            byteArrayReadState = false;
            try {
                byteArrayFieldInputStream.close();
                byteArrayFieldInputStream = null;
            } catch (IOException e) {
                JMSException jmse = new JMSException("Exception:"+
                        "StreamMessage:readBytes()" + e.getMessage());
                jmse.initCause(e);
                throw jmse;
            }
        }
        return bytesRead;
    }

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
    public char readChar()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readChar()");
        return ConvertValue.toChar(obj);
    }

    /** Reads a <code>double</code> from the stream message.
      *
      * @return a <code>double</code> value from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    public double readDouble()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readDouble()");
        return ConvertValue.toDouble(obj);
    }

    /** Reads a <code>float</code> from the stream message.
      *
      * @return a <code>float</code> value from the stream message
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    public float readFloat()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readFloat()");
        return ConvertValue.toFloat(obj);
    }

    /** Reads a 32-bit integer from the stream message.
      *
      * @return a 32-bit integer value from the stream message, interpreted
      * as an <code>int</code>
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    public int readInt()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readInt()");
        return ConvertValue.toInt(obj);
    }

    /** Reads a 64-bit integer from the stream message.
      *
      * @return a 64-bit integer value from the stream message, interpreted as
      * a <code>long</code>
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageEOFException if unexpected end of message stream has
      *                                been reached.     
      * @exception MessageFormatException if this type conversion is invalid.
      * @exception MessageNotReadableException if the message is in write-only 
      *                                        mode.
      */
    public long readLong()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readLong()");
        return ConvertValue.toLong(obj);
    }

    /** Reads an object from the stream message.
      *
      * <P>This method can be used to return, in objectified format,
      * an object in the Java programming language ("Java object") that has 
      * been written to the stream with the equivalent
      * <CODE>writeObject</CODE> method call, or its equivalent primitive
      * <CODE>write<I>type</I></CODE> method.
      *  
      * <P>Note that byte values are returned as <CODE>byte[]</CODE>, not 
      * <CODE>Byte[]</CODE>.
      *
      * <P>An attempt to call <CODE>readObject</CODE> to read a byte field 
      * value into a new <CODE>byte[]</CODE> object before the full value of the
      * byte field has been read will throw a 
      * <CODE>MessageFormatException</CODE>.
      *
      * @return a Java object from the stream message, in objectified
      * format (for example, if the object was written as an <CODE>int</CODE>, 
      * an <CODE>Integer</CODE> is returned)
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
    public Object readObject()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readObject()");
        return obj;
    }

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
    public short readShort()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readShort()");
        return ConvertValue.toShort(obj);
    }

    /** Reads a <CODE>String</CODE> from the stream message.
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
    public String readString()
    throws JMSException {
        Object obj = this._readPrimitiveObject("readString()");
        return ConvertValue.toString(obj);
    }

    /** Puts the message body in read-only mode and repositions the stream
      * to the beginning.
      *  
      * @exception JMSException if the JMS provider fails to reset the message
      *                         due to some internal error.
      * @exception MessageFormatException if the message has an invalid
      *                                   format.
      */
    public void reset()
    throws JMSException {
        String methodName = "reset()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this._reset(methodName);
    }

    /** Writes a <code>boolean</code> to the stream message.
      * The value <code>true</code> is written as the value 
      * <code>(byte)1</code>; the value <code>false</code> is written as 
      * the value <code>(byte)0</code>.
      *
      * @param value the <code>boolean</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeBoolean(boolean value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeBoolean()");
    }

    /** Writes a <code>byte</code> to the stream message.
      *
      * @param value the <code>byte</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeByte(byte value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeByte()");
    }

    /** Writes a byte array field to the stream message.
      *
      * <P>The byte array <code>value</code> is written to the message
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
    public void writeBytes(byte[] value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeBytes(byte[])");
    }

    /** Writes a portion of a byte array as a byte array field to the stream 
      * message.
      *  
      * <P>The a portion of the byte array <code>value</code> is written to the
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
    public void writeBytes(byte[] value, int offset, int length)
    throws JMSException {
        byte[] out = new byte [length];
        System.arraycopy(value, offset, out, 0, length);
        this._writePrimitiveObject(out, "writeBytes(byte[], offset, length)");
    }

    /** Writes a <code>char</code> to the stream message.
      *
      * @param value the <code>char</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeChar(char value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeChar()");
    }

    /** Writes a <code>double</code> to the stream message.
      *
      * @param value the <code>double</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeDouble(double value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeDouble()");
    }

    /** Writes a <code>float</code> to the stream message.
      *
      * @param value the <code>float</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeFloat(float value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeFloat()");
    }

    /** Writes an <code>int</code> to the stream message.
      *
      * @param value the <code>int</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeInt(int value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeInt()");
    }

    /** Writes a <code>long</code> to the stream message.
      *
      * @param value the <code>long</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeLong(long value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeLong()");
    }

    /** Writes an object to the stream message.
      *
      * <P>This method works only for the objectified primitive
      * object types (<code>Integer</code>, <code>Double</code>, 
      * <code>Long</code>&nbsp;...), <code>String</code> objects, and byte 
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
    public void writeObject(Object value)
    throws JMSException {
        this._checkValidObjectType(value, "writeObject()");
        this._writePrimitiveObject(value, "writeObject()");
    }

    /** Writes a <code>short</code> to the stream message.
      *
      * @param value the <code>short</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeShort(short value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeShort()");
    }

    /** Writes a <code>String</code> to the stream message.
      *
      * @param value the <code>String</code> value to be written
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */
    public void writeString(String value)
    throws JMSException {
        this._writePrimitiveObject(value, "writeString()");
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.StreamMessage
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods DirectBytesPacket / javax.jms.StreamMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Set the JMS default values on this JMS StreamsMessage
     */
    protected void _setDefaultValues()
    throws JMSException {
        super._setDefaultValues();
        this.pkt.setPacketType(PacketType.STREAM_MESSAGE);
    }

    /**
     *  Set the JMS Message body into the packet
     */
    protected void _setBodyToPacket()
    throws JMSException {
        this._reset("_setBodyToPacket");
        if (this.messageBody != null) {
            try {
                super._setMessageBodyOfPacket(this.messageBody, 0, this.messageBody.length);
            } catch (Exception ex) {
                String errMsg = _lgrMID_EXC +
                        ":ERROR setting StreamMessage body"+
                        ":Exception="+ ex.getMessage();
                _loggerJM.severe(errMsg);
                JMSException jmse = new javax.jms.JMSException(errMsg);
                jmse.initCause(ex);
                throw jmse;
            }
        }
    }

    /**
     *  Get the message body from the packet
     */
    protected void _getMessageBodyFromPacket()
    throws JMSException {
        this.messageBody = super._getMessageBodyByteArray();
        this._reset("_getMessageBodyFromPacket");
    }

    /**
     *  Reset method for private use
     */
    private void _reset(String methodName)
    throws JMSException {
        super._setReadOnlyBody(true);
        try {
            if (this.writePerformed) {
                this.objectOutputStream.flush();
                this.messageBody = this.byteArrayOutputStream.toByteArray();

                this.objectOutputStream.close();
                this.byteArrayOutputStream.close();
                this.writePerformed = false;
            }
            if (this.messageBody != null) {
                this.byteArrayInputStream = new
                        ByteArrayInputStream(this.messageBody);
                this.objectInputStream = new
                    FilteringObjectInputStream(this.byteArrayInputStream);
            }
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:StreamMessage."+methodName+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        //reset flag
        this.byteArrayReadState = false;
        this.notYetProcessedPrimitiveObject = null;
    }

    /**
     *  Read a primitive object from the StreamMessage body
     */
    private Object _readPrimitiveObject(String methodName)
    throws JMSException {
        Object obj = null;
        this.checkForWriteOnlyMessageBody(methodName);
        this._checkReadBytesState(methodName);
        try {
            if (this.notYetProcessedPrimitiveObject != null ){
                obj = this.notYetProcessedPrimitiveObject;
                this.notYetProcessedPrimitiveObject = null;
            } else {
                obj = this.objectInputStream.readObject();
            }
        } catch (EOFException eofe) {
            MessageEOFException meofe = new MessageEOFException(
                    "Unexpected EOFException: " + eofe.getMessage());
            throw meofe;
        } catch (Exception e) {
            JMSException jmse =  new JMSException(
                    "Exception on readObject:" + e.getMessage());
            jmse.initCause(e);
            throw jmse;
        }
        return obj;
    }

    /**
     *  Write an object to the StreamMessage body
     */
    private void _writePrimitiveObject(Object obj, String methodName)
    throws JMSException {
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.objectOutputStream.writeObject(obj);
        } catch (Exception e){
            String errMsg = _lgrMID_EXC +
                    ":Exception:StreamMessage."+methodName+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /**
     *  Check the read bytes state.<p>
     *
     *  Called by every readXXXX() method except readBytes
     *
     *  @throws MessageFormatException if byteArrayReadState state is true.
     */
    private void _checkReadBytesState(String methodName)
    throws MessageFormatException {
        if (this.byteArrayReadState) {
            throw new MessageFormatException("MessageFormatException on" + 
                    methodName);
        }
    }

    /**
     * }
     *  Initialize buffers for output
     */
    private void _initializeOutputStreams()
    throws JMSException {
        try {
            this.byteArrayOutputStream = new ByteArrayOutputStream();
            this.objectOutputStream = new ObjectOutputStream (byteArrayOutputStream);
        } catch (Exception e) {
            JMSException jmse = new JMSException("Exception:"+
                    "StreamMessage:initializing output streams:" +
                    e.getMessage());
            jmse.initCause(e);
            throw jmse;
        }
    }

    private void _checkValidObjectType(Object value, String name)
    throws MessageFormatException {
        if (value == null){
            return;
        }
        if (
                value instanceof Boolean  ||
                value instanceof Byte     ||
                value instanceof Short    ||
                value instanceof Character||
                value instanceof Integer  ||
                value instanceof Long     ||
                value instanceof Float    ||
                value instanceof Double   ||
                value instanceof String   ||
                value instanceof byte[]
            ) {
            //valid
        } else {
            String errMsg = _lgrMID_EXC +
                    ":StreamMessage:setObject():Invalid type" +
                    ":name="+name+
                    ":type="+ value.getClass().getName();
            _loggerJM.severe(errMsg);
            MessageFormatException mfe = new MessageFormatException(errMsg);
            throw mfe;
        }
    }
}
