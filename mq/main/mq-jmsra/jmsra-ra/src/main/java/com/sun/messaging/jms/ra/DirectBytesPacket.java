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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.util.Arrays;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import com.sun.messaging.jmq.io.JMQByteArrayOutputStream;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsservice.JMSService;

/** 
 *
 */
public class DirectBytesPacket
        extends DirectPacket
        implements javax.jms.BytesMessage {

    /** The messageBody of this JMS BytesMessage */
    private byte[] messageBody = null;

    /** The valid length of the messageBody of this JMS BytesMessage */
    private int validLength = 0;

    /** Indicates whether this JMS BytesMessage has been written to or not */
    private boolean writePerformed = false;

    /** The OutputStream used to buffer the written data */
    private JMQByteArrayOutputStream byteArrayOutputStream = null;
    private DataOutputStream dataOutputStream = null;

    /** The InputStream used to buffer the data to be read */
    private ByteArrayInputStream byteArrayInputStream = null;
    private DataInputStream dataInputStream = null;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectBytesPacket";

    /**
     *  Create a new instance of DirectBytesPacket.<p>
     *
     *  Used by the createBytesMessage API
     */
    public DirectBytesPacket(DirectSession ds)
    throws JMSException {
        super(ds);
        if (_logFINE){
            Object params[] = new Object[2];
            params[0] = ds;
            _loggerOC.entering(_className, "constructor()", params);
        }
        //this has been created for sending - create output buffers
        //The default 32 byte buffer created by ByteArrayOutputStream is ok
        byteArrayOutputStream = new JMQByteArrayOutputStream(new byte[32]);
        dataOutputStream = new DataOutputStream (byteArrayOutputStream);
    }

    /**
     *  Create a new instance of DirectBytesPacket.
     *  Used by Consumer.deliver.
     */
    public DirectBytesPacket(JMSPacket jmsPacket, long consumerId,
            DirectSession ds, JMSService jmsservice)
    throws JMSException {
        super(jmsPacket, consumerId, ds, jmsservice);
        this._getMessageBodyFromPacket();
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.BytesMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Get the number of bytes of the message body when the message
     *  is in read-only mode. The value returned can be used to allocate 
     *  a byte array. The value returned is the entire length of the message
     *  body, regardless of where the pointer for reading the message 
     *  is currently located.
     * 
     *  @return number of bytes in the message 
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageNotReadableException if the message is in write-only
     *          mode.
     *  @since 1.1 
     */
    public long getBodyLength()
    throws JMSException {
        String methodName = "getBodyLength()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        //this.checkForReadOnlyMessageBody(methodName);
        if (this.messageBody != null) {
            return this.validLength;
        } else {
            return 0L;
        }
    }

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
     *  Read a <code>boolean</code> from the bytes message stream.
     *
     *  @return The <code>boolean</code> value read
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */
    public boolean readBoolean()
    throws JMSException {
        String methodName = "readBoolean()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        boolean value = false;
        try {
            value = dataInputStream.readBoolean();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read a signed 8-bit value from the bytes message stream.
     *
     *  @return The next byte from the bytes message stream as a signed 8-bit
     *          <code>byte</code>
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public byte readByte()
    throws JMSException {
        String methodName = "readByte()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        byte value = 0;
        try {
            value = dataInputStream.readByte();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /**
     *  Reads a byte array from the bytes message stream.
     *
     *  <P>If the length of array <code>value</code> is less than the number of 
     *  bytes remaining to be read from the stream, the array should 
     *  be filled. A subsequent call reads the next increment, and so on.
     * 
     *  <P>If the number of bytes remaining in the stream is less than the 
     *  length of 
     *  array <code>value</code>, the bytes should be read into the array. 
     *  The return value of the total number of bytes read will be less than
     *  the length of the array, indicating that there are no more bytes left 
     *  to be read from the stream. The next read of the stream returns -1.
     *
     *  @param  value the buffer into which the data is read
     *
     *  @return The total number of bytes read into the buffer, or -1 if 
     *          there is no more data because the end of the stream has been
     *          reached
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public int readBytes(byte[] value)
    throws JMSException {
        String methodName = "readBytes(byte[])";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+Arrays.toString(value));
        }
        this.checkForWriteOnlyMessageBody(methodName);
        int bytesRead = -1;
        try {
            bytesRead = dataInputStream.read(value);
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+":value="+Arrays.toString(value)+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+":value="+Arrays.toString(value)+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return bytesRead;
    }

    /**
     *  Read a portion of the bytes message stream.
     *
     *  <P>If the length of array <code>value</code> is less than the number of
     *  bytes remaining to be read from the stream, the array should 
     *  be filled. A subsequent call reads the next increment, and so on.
     * 
     *  <P>If the number of bytes remaining in the stream is less than the 
     *  length of 
     *  array <code>value</code>, the bytes should be read into the array. 
     *  The return value of the total number of bytes read will be less than
     *  the length of the array, indicating that there are no more bytes left 
     *  to be read from the stream. The next read of the stream returns -1.
     *
     *  <p> If <code>length</code> is negative, or
     *  <code>length</code> is greater than the length of the array
     *  <code>value</code>, then an <code>IndexOutOfBoundsException</code> is
     *  thrown. No bytes will be read from the stream for this exception case.
     *  
     *  @param  value the buffer into which the data is read
     *  @param  length the number of bytes to read; must be less than or equal to
     *          <code>value.length</code>
     * 
     *  @return The total number of bytes read into the buffer, or -1 if
     *          there is no more data because the end of the stream has been
     *          reached
     *  
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageNotReadableException if the message is in write-only 
     *              mode.
     *
     */
    public int readBytes(byte[] value, int length) 
    throws JMSException {
        String methodName = "readBytes(byte[], length)";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+Arrays.toString(value));
        }
        this.checkForWriteOnlyMessageBody(methodName);
        int bytesRead = -1;
        try {
            //IndexOutOfBoundsException (RuntimeException) is propogated
            bytesRead = dataInputStream.read(value, 0, length);
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+":value="+Arrays.toString(value)+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (IndexOutOfBoundsException iobe) {
            throw iobe;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+":value="+Arrays.toString(value)+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return bytesRead;
    }

    /**
     *  Reads a Unicode character value from the bytes message stream.
     *
     *  @return The next two bytes from the bytes message stream as a Unicode
     *          character
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public char readChar()
    throws JMSException {
        String methodName = "readChar()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        char value = 0;
        try {
            value = dataInputStream.readChar();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /**
     *  Read a <code>double</code> from the bytes message stream.
     *
     *  @return The next eight bytes from the bytes message stream, interpreted
     *          as a <code>double</code>
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public double readDouble()
    throws JMSException {
        String methodName = "readDouble()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        double value = 0;
        try {
            value = dataInputStream.readDouble();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read a <code>float</code> from the bytes message stream.
     *
     *  @return The next four bytes from the bytes message stream, interpreted
     *          as a <code>float</code>
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public float readFloat()
    throws JMSException {
        String methodName = "readFloat()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        float value = 0;
        try {
            value = dataInputStream.readFloat();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read a signed 32-bit integer from the bytes message stream.
     *
     *  @return The next four bytes from the bytes message stream, interpreted
     *          as an <code>int</code>
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public int readInt()
    throws JMSException {
        String methodName = "readInt()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        int value = 0;
        try {
            value = dataInputStream.readInt();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read a signed 64-bit integer from the bytes message stream.
     *
     *  @return The next eight bytes from the bytes message stream, interpreted
     *          as a <code>long</code>
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public long readLong()
    throws JMSException {
        String methodName = "readLong()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        long value = 0;
        try {
            value = dataInputStream.readLong();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /**
     *  Read a signed 16-bit number from the bytes message stream.
     *
     *  @return The next two bytes from the bytes message stream, interpreted as
     *          a signed 16-bit number
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public short readShort()
    throws JMSException {
        String methodName = "readShort()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        short value = 0;
        try {
            value = dataInputStream.readShort();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read a string that has been encoded using a modified UTF-8
     *  format from the bytes message stream.
     *
     *  <P>For more information on the UTF-8 format, see "File System Safe
     *  UCS Transformation Format (FSS_UTF)", X/Open Preliminary Specification,
     *  X/Open Company Ltd., Document Number: P316. This information also
     *  appears in ISO/IEC 10646, Annex P.
     *
     *  @return A Unicode string from the bytes message stream
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public String readUTF()
    throws JMSException {
        String methodName = "readUTF()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        String value = null;
        try {
            value = dataInputStream.readUTF();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read an unsigned 8-bit number from the bytes message stream.
     *  
     *  @return The next byte from the bytes message stream, interpreted as an
     *          unsigned 8-bit number
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */
    public int readUnsignedByte()
    throws JMSException {
        String methodName = "readUnsignedByte()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        int value = 0;
        try {
            value = dataInputStream.readUnsignedByte();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /** 
     *  Read an unsigned 16-bit number from the bytes message stream.
     *  
     *  @return The next two bytes from the bytes message stream, interpreted as
     *  an unsigned 16-bit integer
     *
     *  @throws JMSException if the JMS provider fails to read the message 
     *          due to some internal error.
     *  @throws MessageEOFException if unexpected end of bytes stream has 
     *          been reached.
     *  @throws MessageNotReadableException if the message is in write-only 
     *          mode.
     */ 
    public int readUnsignedShort()
    throws JMSException {
        String methodName = "readUnsignedShort()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForWriteOnlyMessageBody(methodName);
        int value = 0;
        try {
            value = dataInputStream.readUnsignedShort();
        } catch (EOFException eofe) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ eofe.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageEOFException(errMsg);
            jmse.initCause(eofe);
            throw jmse;
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
        return value;
    }

    /**
     *  Put the message body in read-only mode and repositions the stream of 
     *  bytes to the beginning.
     *  
     *  @throws JMSException if the JMS provider fails to reset the message
     *          due to some internal error.
     *  @throws MessageFormatException if the message has an invalid
     *          format.
     */
    public void reset()
    throws JMSException {
        String methodName = "reset()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this._reset("reset()");
    }

    /**
     *  Write a <code>boolean</code> to the bytes message stream as a 1-byte 
     *  value.
     *  The value <code>true</code> is written as the value 
     *  <code>(byte)1</code>; the value <code>false</code> is written as 
     *  the value <code>(byte)0</code>.
     *
     *  @param  value The <code>boolean</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void writeBoolean(boolean value)
    throws JMSException {
        String methodName = "writeBoolean()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeBoolean(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a <code>byte</code> to the bytes message stream as a 1-byte 
     *  value.
     *
     *  @param  value the <code>byte</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeByte(byte value)
    throws JMSException {
        String methodName = "writeByte()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeByte(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a <code>short</code> to the bytes message stream as two bytes,
     *  high byte first.
     *
     *  @param  value The <code>short</code> to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeShort(short value)
    throws JMSException {
        String methodName = "writeShort()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeShort(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a <code>char</code> to the bytes message stream as a 2-byte
     *  value, high byte first.
     *
     *  @param  value the <code>char</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeChar(char value)
    throws JMSException {
        String methodName = "writeChar()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeChar(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write an <code>int</code> to the bytes message stream as four bytes, 
     *  high byte first.
     *
     *  @param  value The <code>int</code> to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeInt(int value)
    throws JMSException {
        String methodName = "writeInt()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeInt(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a <code>long</code> to the bytes message stream as eight bytes, 
     *  high byte first.
     *
     *  @param  value The <code>long</code> to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeLong(long value)
    throws JMSException {
        String methodName = "writeLong()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeLong(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Convert the <code>float</code> argument to an <code>int</code> using 
     *  the <code>floatToIntBits</code> method in class <code>Float</code>,
     *  and then writes that <code>int</code> value to the bytes message
     *  stream as a 4-byte quantity, high byte first.
     *
     *  @param  value The <code>float</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeFloat(float value)
    throws JMSException {
        String methodName = "writeFloat()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeFloat(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Converts the <code>double</code> argument to a <code>long</code> using 
     *  the <code>doubleToLongBits</code> method in class <code>Double</code>,
     *  and then writes that <code>long</code> value to the bytes message
     *  stream as an 8-byte quantity, high byte first.
     *
     *  @param  value The <code>double</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeDouble(double value)
    throws JMSException {
        String methodName = "writeDouble()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeDouble(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a byte array to the bytes message stream.
     *
     *  @param  value The byte array to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeBytes(byte[] value)
    throws JMSException {
        String methodName = "writeBytes(byte[])";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+Arrays.toString(value));
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.write(value);
        } catch (NullPointerException npe) {
            throw npe;
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+Arrays.toString(value)+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write a portion of a byte array to the bytes message stream.
     *  
     *  @param  value The byte array value to be written
     *  @param  offset The initial offset within the byte array
     *  @param  length The number of bytes to use
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */ 
    public void writeBytes(byte[] value, int offset, int length) 
    throws JMSException {
        String methodName = "writeBytes(byte[], offset, length)";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+Arrays.toString(value)+
                    ":offset="+offset+":length="+length);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.write(value, offset, length);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+Arrays.toString(value)+
                    ":offset="+offset+":length="+length+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }

    /** 
     *  Write an object to the bytes message stream.
     *
     *  <P>This method works only for the objectified primitive
     *  object types (<code>Integer</code>, <code>Double</code>, 
     *  <code>Long</code>&nbsp;...), <code>String</code> objects, and byte 
     *  arrays.
     *
     *  @param  value The object in the Java programming language ("Java 
     *          object") to be written; it must not be null
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageFormatException if the object is of an invalid type.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     *  @throws java.lang.NullPointerException if the parameter 
     *          <code>value</code> is null.
     */ 
    public void writeObject(Object value)
    throws JMSException {
        String methodName = "writeObject()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName+":value="+value);
        }
        if (value == null) {
            String exErrMsg = _lgrMID_EXC+methodName+"value=NULL";
            throw new NullPointerException (exErrMsg);
        }
        if (value instanceof Integer) {
            writeInt(((Integer)value).intValue() );
        } else if (value instanceof Short) {
            writeShort(((Short)value).shortValue() );
        } else if (value instanceof Float) {
            writeFloat(((Float)value).floatValue() );
        } else if (value instanceof Double) {
            writeDouble(((Double)value).doubleValue() );
        } else if (value instanceof Long) {
            writeLong(((Long)value).longValue() );
        } else if (value instanceof String)  {
            writeUTF((String)value );
        } else if (value instanceof Character) {
            writeChar(((Character)value).charValue() );
        } else if (value instanceof Byte) {
            writeByte(((Byte)value).byteValue() );
        } else if (value instanceof byte[]) {
            writeBytes((byte[])value );
        } else if (value instanceof Boolean) {
            writeBoolean(((Boolean)value).booleanValue() );
        } else {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":Illegal object type="+ value.getClass().getName();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.MessageFormatException(errMsg);
            throw jmse;
        }
    }

    /**
     *  Write a string to the bytes message stream using UTF-8 encoding in a 
     *  machine-independent manner.
     *
     *  <P>For more information on the UTF-8 format, see "File System Safe 
     *  UCS Transformation Format (FSS_UTF)", X/Open Preliminary Specification,       
     *  X/Open Company Ltd., Document Number: P316. This information also 
     *  appears in ISO/IEC 10646, Annex P. 
     *
     *  @param  value the <code>String</code> value to be written
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     *
     */ 
    public void writeUTF(String value)
    throws JMSException {
        String methodName = "writeUTF()";
        if (_logFINE){
            _loggerJM.fine(_lgrMID_INF+/*"messageId="+messageId+":"+*/
                    methodName);
        }
        this.checkForReadOnlyMessageBody(methodName);
        try {
            this.dataOutputStream.writeUTF(value);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+"value="+value+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
        this.writePerformed = true;
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.BytesMessage
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods DirectBytesPacket / javax.jms.BytesMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Set the JMS default values on this JMS BytesMessage
     */
    protected void _setDefaultValues()
    throws JMSException {
        super._setDefaultValues();
        this.pkt.setPacketType(PacketType.BYTES_MESSAGE);
    }

    /**
     *  Set the JMS Message body into the packet
     */
    protected void _setBodyToPacket()
    throws JMSException {
        this._reset("_setBodyToPacket");
        if (this.messageBody != null) {
            try {
                super._setMessageBodyOfPacket(this.messageBody, 0, this.validLength);
            } catch (Exception ex) {
                String errMsg = _lgrMID_EXC +
                        ":ERROR setting BytesMessage body"+
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
        this.validLength = this.pkt.getMessageBodySize();
        //this.validLength = this.messageBody.length;
        this._reset("_getMessageBodyFromPacket()");
    }

    /**
     *  Reset method for private use
     */
    private void _reset(String methodName)
    throws JMSException {
        super._setReadOnlyBody(true);
        try {
            if (this.writePerformed){
                this.dataOutputStream.flush();
                this.messageBody = this.byteArrayOutputStream.getBuf();
                this.validLength = this.byteArrayOutputStream.getCount();
                this.dataOutputStream.close();
                this.byteArrayOutputStream.close();
                this.writePerformed = false;
            }
            if (this.messageBody == null){
                this.messageBody = new byte[0];
                this.validLength = 0;
            }
            this.byteArrayInputStream = new ByteArrayInputStream(
                    this.messageBody, 0, this.validLength);
            this.dataInputStream =
                    new DataInputStream(this.byteArrayInputStream);
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":Exception:BytesMessage."+methodName+
                    ":message="+ e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
    }

    /**
     *  Initialize buffers for output
     */
    private void _initializeOutputStreams(){
        //The default 32 byte buffer created by ByteArrayOutputStream is ok
        this.byteArrayOutputStream = new JMQByteArrayOutputStream(new byte[32]);
        this.dataOutputStream = new DataOutputStream (byteArrayOutputStream);
    }
}
