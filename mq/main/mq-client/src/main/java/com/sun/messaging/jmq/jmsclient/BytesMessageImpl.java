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
 * @(#)BytesMessageImpl.java	1.25 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.io.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;

/** A BytesMessage is used to send a message containing a stream of
  * uninterpreted bytes. It inherits <CODE>Message</CODE> and adds a bytes
  * message body. The receiver of the message supplies the interpretation
  * of the bytes.
  *
  * <P>It's methods are based largely on those found in
  * <CODE>java.io.DataInputStream</CODE> and
  * <CODE>java.io.DataOutputStream</CODE>.
  *
  * <P>This message type is for client encoding of existing message formats.
  * If possible, one of the other self-defining message types should be used
  * instead.
  *
  * <P>Although JMS allows the use of message properties with byte messages
  * it is typically not done since the inclusion of properties affects the
  * format.
  *
  * <P>The primitive types can be written explicitly using methods
  * for each type. They may also be written generically as objects.
  * For instance, a call to <CODE>BytesMessage.writeInt(6)</CODE> is
  * equivalent to <CODE>BytesMessage.writeObject(new Integer(6))</CODE>.
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
  * the message body is cleared and the message is in write-only mode.
  *
  * <P>If a client attempts to read a message in write-only mode, a
  * MessageNotReadableException is thrown.
  *
  * <P>If a client attempts to write a message in read-only mode, a
  * MessageNotWriteableException is thrown.
  *
  * @see         javax.jms.Session#createBytesMessage()
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  */

public class BytesMessageImpl extends MessageImpl implements BytesMessage {

    private byte[] messageBody = null;

    private JMQByteArrayOutputStream byteArrayOutputStream = null;
    private DataOutputStream dataOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private DataInputStream dataInputStream = null;

    private boolean bufferIsDirty = false;

    //the valid count of messageBody byte array.
    private int validCount = 0;

    protected BytesMessageImpl() throws JMSException {
        super();
        setPacketType (PacketType.BYTES_MESSAGE);
    }

    /**
     * Constructor called when message is created for produce.
     * For example, this is called by SessionImpl to create a
     * bytes message.
     */
    protected BytesMessageImpl(boolean constructOutputStream) throws JMSException {
        this();
        if ( constructOutputStream ) {
            initOutputStream();
        }
    }

    protected void initOutputStream(){
        byteArrayOutputStream = new JMQByteArrayOutputStream(new byte[32]);
        dataOutputStream = new DataOutputStream (byteArrayOutputStream);
        validCount = 0;
    }

    /**
     * This is called when producing messages.
     * XXX chiaming - optimize for all message types.
     */
    protected void
    setMessageBodyToPacket() throws JMSException {
        reset();
        pkt.setMessageBody(messageBody, 0, validCount);
    }

    //deserialize message body
    //This is called after message is received in Session Reader.
    protected void
    getMessageBodyFromPacket() throws JMSException {
        messageBody = getMessageBody();

        if ( messageBody == null ) {
            validCount = 0;
        } else {
            validCount = messageBody.length;
        }

        reset();
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


    /** Gets the entire length of the message body when the message
     * is in read-only mode. The value returned can be used to allocate
     * a bytes array. The value returned is the entire length of the message
     * body, regardless of where the pointer for reading the message is currently
     * located.
     *
     * @exception JMSException if the JMS provider fails to read the message
     *                         due to some internal error.
     * @exception MessageNotReadableException if the message is in write-only
     *                         mode.
     * @since 1.1
     */
     public long getBodyLength() throws JMSException {
        checkReadAccess();
        if (messageBody == null) {
            return 0;
        } else {
            //return messageBody.length;
            return this.validCount;
        }
    }


    /** Read a <code>boolean</code> from the bytes message stream.
      *
      * @return the <code>boolean</code> value read.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageEOFException if end of bytes stream
      */
    public boolean
    readBoolean() throws JMSException {
        boolean value = false;
        checkReadAccess();

        try {
            value = dataInputStream.readBoolean();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException (errorString, AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (IOException ioe) {
            ExceptionHandler.handleException(ioe, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a signed 8-bit value from the bytes message stream.
      *
      * @return the next byte from the bytes message stream as a signed 8-bit
      * <code>byte</code>.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public byte
    readByte() throws JMSException {

        byte value = (byte)0;
        checkReadAccess();

        try {
            value = dataInputStream.readByte();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read an unsigned 8-bit number from the bytes message stream.
      *
      * @return the next byte from the bytes message stream, interpreted as an
      * unsigned 8-bit number.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public int
    readUnsignedByte() throws JMSException {
        int value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readUnsignedByte();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a signed 16-bit number from the bytes message stream.
      *
      * @return the next two bytes from the bytes message stream, interpreted as a
      * signed 16-bit number.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public short
    readShort() throws JMSException {
        short value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readShort();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);


            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read an unsigned 16-bit number from the bytes message stream.
      *
      * @return the next two bytes from the bytes message stream, interpreted as an
      * unsigned 16-bit integer.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public int
    readUnsignedShort() throws JMSException {
        int value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readUnsignedShort();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);


            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a Unicode character value from the bytes message stream.
      *
      * @return the next two bytes from the bytes message stream as a Unicode
      * character.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public char
    readChar() throws JMSException {
        char value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readChar();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);


            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a signed 32-bit integer from the bytes message stream.
      *
      * @return the next four bytes from the bytes message stream, interpreted as
      * an <code>int</code>.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public int
    readInt() throws JMSException {
        int value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readInt();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);


            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a signed 64-bit integer from the bytes message stream.
      *
      * @return the next eight bytes from the bytes message stream, interpreted as
      * a <code>long</code>.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public long
    readLong() throws JMSException {
        long value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readLong();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a <code>float</code> from the bytes message stream.
      *
      * @return the next four bytes from the bytes message stream, interpreted as
      * a <code>float</code>.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public float
    readFloat() throws JMSException {
        float value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readFloat();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a <code>double</code> from the bytes message stream.
      *
      * @return the next eight bytes from the bytes message stream, interpreted as
      * a <code>double</code>.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public double
    readDouble() throws JMSException {
        double value = 0;
        checkReadAccess();

        try {
            value = dataInputStream.readDouble();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);


            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read in a string that has been encoded using a modified UTF-8
      * format from the bytes message stream.
      *
      * <P>For more information on the UTF-8 format, see "File System Safe
      * UCS Transformation Format (FSS_UFT)", X/Open Preliminary Specification,
      * X/Open Company Ltd., Document Number: P316. This information also
      * appears in ISO/IEC 10646, Annex P.
      *
      * @return a Unicode string from the bytes message stream.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception MessageEOFException if end of message stream
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public String
    readUTF() throws JMSException {
        String value = null;
        checkReadAccess();

        try {
            value = dataInputStream.readUTF();
        } catch (EOFException eofe) {
            //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_READ_EOF);

            String errorString =
            ExceptionHandler.getExceptionMessage(eofe, AdministeredObject.cr.X_MESSAGE_READ_EOF);

            MessageEOFException meofe =
            new com.sun.messaging.jms.MessageEOFException(errorString,AdministeredObject.cr.X_MESSAGE_READ_EOF);
            ExceptionHandler.handleException(eofe, meofe);
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return value;
    }


    /** Read a byte array from the bytes message stream.
      *
      * <P>If the length of array <code>value</code> is less than
      * the bytes remaining to be read from the stream, the array should
      * be filled. A subsequent call reads the next increment, etc.
      *
      * <P>If the bytes remaining in the stream is less than the length of
      * array <code>value</code>, the bytes should be read into the array.
      * The return value of the total number of bytes read will be less than
      * the length of the array, indicating that there are no more bytes left
      * to be read from the stream. The next read of the stream returns -1.
      *
      * @param value the buffer into which the data is read.
      *
      * @return the total number of bytes read into the buffer, or -1 if
      * there is no more data because the end of the stream has been reached.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public int
    readBytes(byte[] value) throws JMSException {
        int bytesRead = -1;

        checkReadAccess();

        try {
            bytesRead = dataInputStream.read (value);
        } catch ( Exception e ) {
            ExceptionHandler.handleException (e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return bytesRead;
    }


    /** Read a portion of the bytes message stream.
      *
      * <P>If the length of array <code>value</code> is less than
      * the bytes remaining to be read from the stream, the array should
      * be filled. A subsequent call reads the next increment, etc.
      *
      * <P>If the bytes remaining in the stream is less than the length of
      * array <code>value</code>, the bytes should be read into the array.
      * The return value of the total number of bytes read will be less than
      * the length of the array, indicating that there are no more bytes left
      * to be read from the stream. The next read of the stream returns -1.
      *
      * <p> If <code>length</code> is negative, or
      * <code>length</code> is greater than the length of the array
      * <code>value</code>, then an <code>IndexOutOfBoundsException</code> is
      * thrown. No bytes will be read from the stream for this exception case.
      *
      * @param value the buffer into which the data is read.
      * @param length the number of bytes to read. Must be less than or equal to value.length.
      *
      * @return the total number of bytes read into the buffer, or -1 if
      * there is no more data because the end of the stream has been reached.
      *
      * @exception MessageNotReadableException if message in write-only mode.
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error
      */

    public int
    readBytes(byte[] value, int length) throws JMSException {
        int bytesRead = -1;

        checkReadAccess();

        //IndexOutOfBoundsException is RuntimeException and it is not caught
        //below.  It will be propagate and throw out of this method.
        try {
            bytesRead = dataInputStream.read (value, 0, length);
        } catch ( IOException e ) {
            ExceptionHandler.handleException (e, AdministeredObject.cr.X_MESSAGE_READ, true);
        }

        return bytesRead;
    }


    /** Write a <code>boolean</code> to the bytes message stream as a 1-byte value.
      * The value <code>true</code> is written out as the value
      * <code>(byte)1</code>; the value <code>false</code> is written out as
      * the value <code>(byte)0</code>.
      *
      * @param value the <code>boolean</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeBoolean(boolean value) throws JMSException {

        checkMessageAccess();

        try {
            dataOutputStream.writeBoolean( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);

    }


    /** Write out a <code>byte</code> to the bytes message stream as a 1-byte value.
      *
      * @param value the <code>byte</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeByte(byte value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.writeByte( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a <code>short</code> to the bytes message stream as two bytes, high
      * byte first.
      *
      * @param value the <code>short</code> to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeShort(short value) throws JMSException {

        checkMessageAccess();

        try {
            dataOutputStream.writeShort( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a <code>char</code> to the bytes message stream as a 2-byte value,
      * high byte first.
      *
      * @param value the <code>char</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeChar(char value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.writeChar( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write an <code>int</code> to the bytes message stream as four bytes,
      * high byte first.
      *
      * @param value the <code>int</code> to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeInt(int value) throws JMSException {

        checkMessageAccess();

        try {
            dataOutputStream.writeInt( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a <code>long</code> to the bytes message stream as eight bytes,
      * high byte first.
      *
      * @param value the <code>long</code> to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeLong(long value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.writeLong( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Convert the float argument to an <code>int</code> using the
      * <code>floatToIntBits</code> method in class <code>Float</code>,
      * and then writes that <code>int</code> value to the bytes message
      * stream as a 4-byte quantity, high byte first.
      *
      * @param value the <code>float</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeFloat(float value) throws JMSException {

        checkMessageAccess();

        try {
            dataOutputStream.writeFloat( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Convert the double argument to a <code>long</code> using the
      * <code>doubleToLongBits</code> method in class <code>Double</code>,
      * and then writes that <code>long</code> value to the bytes message
      * stream as an 8-byte quantity, high byte first.
      *
      * @param value the <code>double</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeDouble(double value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.writeDouble( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a string to the bytes message stream using UTF-8 encoding in a
      * machine-independent manner.
      *
      * <P>For more information on the UTF-8 format, see "File System Safe
      * UCS Transformation Format (FSS_UFT)", X/Open Preliminary Specification,
      * X/Open Company Ltd., Document Number: P316. This information also
      * appears in ISO/IEC 10646, Annex P.
      *
      * @param value the <code>String</code> value to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeUTF(String value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.writeUTF( value );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a byte array to the bytes message stream.
      *
      * @param value the byte array to be written.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeBytes(byte[] value) throws JMSException {
        checkMessageAccess();

        try {
            dataOutputStream.write( value );
        } catch (NullPointerException nullpe) {
            throw nullpe;
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a portion of a byte array to the bytes message stream.
      *
      * @param value the byte array value to be written.
      * @param offset the initial offset within the byte array.
      * @param length the number of bytes to use.
      *
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeBytes(byte[] value, int offset, int length) throws JMSException {

        checkMessageAccess();

        try {
            dataOutputStream.write( value, offset, length );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_WRITE, true);
        }

        setBufferIsDirty (true);
    }


    /** Write a Java object to the bytes message stream.
      *
      * <P>Note that this method only works for the objectified primitive
      * object types (Integer, Double, Long ...), String's and byte arrays.
      *
      * @param value the Java object to be written. Must not be null.
      *
      * @exception NullPointerException if parameter <code>value</code>
      *                         is null.
      * @exception MessageNotWriteableException if message in read-only mode.
      * @exception MessageFormatException if object is invalid type.
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      */

    public void
    writeObject(Object value) throws JMSException {

        if ( value == null ) { //as java does
            throw new NullPointerException ();
        }

        if (value instanceof Integer) {
            writeInt( ((Integer)value).intValue() );
        } else if (value instanceof Short) {
            writeShort( ((Short)value).shortValue() );
        } else if (value instanceof Float) {
            writeFloat( ((Float)value).floatValue() );
        } else if (value instanceof Double) {
            writeDouble( ((Double)value).doubleValue() );
        } else if (value instanceof Long) {
            writeLong( ((Long)value).longValue() );
        } else if (value instanceof String)  {
            writeUTF( (String)value );
        } else if (value instanceof Character) {
            writeChar( ((Character)value).charValue() );
        } else if (value instanceof Byte) {
            writeByte( ((Byte)value).byteValue() );
        } else if (value instanceof byte[]) {
            writeBytes( (byte[])value );
        } else if (value instanceof Boolean) {
            writeBoolean( ((Boolean)value).booleanValue() );
        } else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);

            JMSException jmse =
                new com.sun.messaging.jms.MessageFormatException
                (errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);

            ExceptionHandler.throwJMSException(jmse);
        }

    }


    /** Put the message body in read-only mode, and reposition the stream of
      * bytes to the beginning.
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
                dataOutputStream.flush();

                messageBody = byteArrayOutputStream.getBuf();
                validCount = byteArrayOutputStream.getCount();

                dataOutputStream.close();
                byteArrayOutputStream.close();
            }

            /**
             * If message body is null, we construct an empty one.  This
             * would make the behavior of readXXX() matches the spec.
             */
            if ( messageBody == null ) {
                messageBody = new byte[0];
                validCount = 0;
            }

            byteArrayInputStream = new ByteArrayInputStream ( messageBody, 0, validCount );
            dataInputStream = new DataInputStream ( byteArrayInputStream );

        } catch (Exception e) {
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_MESSAGE_RESET, true);
        }

        setBufferIsDirty (false);
        setMessageReadMode (true);
    }

    public void dump (PrintStream ps) {
        ps.println ("------ ByteMessageImpl dump ------");
        super.dump (ps);
    }

    public String toString() {
        return super.toString();
    }
}
