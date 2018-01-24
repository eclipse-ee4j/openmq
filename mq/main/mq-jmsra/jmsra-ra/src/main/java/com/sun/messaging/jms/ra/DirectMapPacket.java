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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotWriteableException;

import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsservice.JMSService;
//import java.io.DataInputStream;
//import java.io.DataOutputStream;
//import com.sun.messaging.jmq.io.JMQByteArrayOutputStream;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/**
 *  A <CODE>MapMessage</CODE> object is used to send a set of name-value pairs.
 *  The names are <CODE>String</CODE> objects, and the values are primitive 
 *  data types in the Java programming language. The names must have a value
 *  that is not null, and not an empty string. The entries can be accessed 
 *  sequentially or randomly by name. The order of the entries is undefined. 
 *  <CODE>MapMessage</CODE> inherits from the <CODE>Message</CODE> interface
 *  and adds a message body that contains a Map.
 *
 *  <P>The primitive types can be read or written explicitly using methods
 *  for each type. They may also be read or written generically as objects.
 *  For instance, a call to <CODE>MapMessage.setInt("foo", 6)</CODE> is 
 *  equivalent to <CODE>MapMessage.setObject("foo", new Integer(6))</CODE>.
 *  Both forms are provided, because the explicit form is convenient for
 *  static programming, and the object form is needed when types are not known
 *  at compile time.
 *
 *  <P>When a client receives a <CODE>MapMessage</CODE>, it is in read-only 
 *  mode. If a client attempts to write to the message at this point, a 
 *  <CODE>MessageNotWriteableException</CODE> is thrown. If 
 *  <CODE>clearBody</CODE> is called, the message can now be both read from and 
 *  written to.
 *
 *  <P><CODE>MapMessage</CODE> objects support the following conversion table. 
 *  The marked cases must be supported. The unmarked cases must throw a 
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
 *  @see         javax.jms.Session#createMapMessage()
 *  @see         javax.jms.BytesMessage
 *  @see         javax.jms.Message
 *  @see         javax.jms.ObjectMessage
 *  @see         javax.jms.StreamMessage
 *  @see         javax.jms.TextMessage
 */
public class DirectMapPacket
        extends DirectPacket
        implements javax.jms.MapMessage {

    private Map<String, Object> map = new HashMap<String, Object>();

    private byte[] messageBody = null;

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.DirectMapPacket";

    /**
     *  Create a new instance of DirectMapPacket.<p>
     *
     *  Used by the createMapMessage API.
     */
    public DirectMapPacket(DirectSession ds)
    throws JMSException {
        super(ds);
        if (_logFINE){
            Object params[] = new Object[2];
            params[0] = ds;
            _loggerOC.entering(_className, "constructor()", params);
        }
    }

    /**
     *  Create a new instance of DirectMapPacket.
     *  Used by Consumer.deliver.
     */
    public DirectMapPacket(JMSPacket jmsPacket, long consumerId,
            DirectSession ds, JMSService jmsservice)
    throws JMSException {
        super(jmsPacket, consumerId, ds, jmsservice);
        this._getMessageBodyFromPacket();
    }
    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.MapMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Clear out the message body.
     */
    public void clearBody()
    throws JMSException {
        super.clearBody();
        this.map.clear();
    }

    /** 
     *  Return the <CODE>boolean</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>boolean</CODE>
     *
     *  @return The <CODE>boolean</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public boolean getBoolean(String name)
    throws JMSException {
        return ConvertValue.toBoolean(this.map.get(name));
    }

    /** 
     *  Return the <CODE>byte</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>byte</CODE>
     *
     *  @return The <CODE>byte</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.
     */
    public byte getByte(String name)
    throws JMSException {
        return ConvertValue.toByte(this.map.get(name));
    }

    /** 
     *  Return the byte array value with the specified name.
     *
     *  @param name The name of the byte array
     *
     *  @return a copy of the byte array value with the specified name; if there
     *  is no item by this name, a null value is returned.
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public byte[] getBytes(String name)
    throws JMSException {
        Object obj = this.map.get(name);
        if (obj == null) {
            return null;
        } else {
            if (obj instanceof byte[]) {
                return (byte[])obj;
            } else {
                String errMsg = _lgrMID_EXC +
                        ":MapMessage:getBytes[]:Key="+name+
                        ":cannot be rea as a byte array.";
                _loggerJM.severe(errMsg);
                MessageFormatException mfe = new MessageFormatException(errMsg);
                throw mfe;
            }
        }
    }

    /** 
     *  Return the Unicode character value with the specified name.
     *
     *  @param  name The name of the Unicode character
     *
     *  @return the Unicode character value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.     
     */
    public char getChar(String name)
    throws JMSException {
        return ConvertValue.toChar(this.map.get(name));
    }

    /** 
     *  Return the <CODE>double</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>double</CODE>
     *
     *  @return The <CODE>double</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public double getDouble(String name)
    throws JMSException {
        return ConvertValue.toDouble(this.map.get(name));
    }

    /** 
     *  Return the <CODE>float</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>float</CODE>
     *
     *  @return The <CODE>float</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.     
     */
    public float getFloat(String name)
    throws JMSException {
        return ConvertValue.toFloat(this.map.get(name));
    }

    /** 
     *  Returns the <CODE>int</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>int</CODE>
     *
     *  @return The <CODE>int</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public int getInt(String name)
    throws JMSException {
        return ConvertValue.toInt(this.map.get(name));
    }

    /** 
     *  Return the <CODE>long</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>long</CODE>
     *
     *  @return The <CODE>long</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public long getLong(String name)
    throws JMSException {
        return ConvertValue.toLong(this.map.get(name));
    }

    /** 
     *  Return an <CODE>Enumeration</CODE> of all the names in the 
     *  <CODE>MapMessage</CODE> object.
     *
     *  @return an enumeration of all the names in this <CODE>MapMessage</CODE>
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     */
    public Enumeration getMapNames()
    throws JMSException {
        return Collections.enumeration(this.map.keySet());
    }

    /** 
     *  Return the value of the object with the specified name.
     *
     *  <P>This method can be used to return, in objectified format,
     *  an object in the Java programming language ("Java object") that had 
     *  been stored in the Map with the equivalent
     *  <CODE>setObject</CODE> method call, or its equivalent primitive
     *  <CODE>set<I>type</I></CODE> method.
     *
     *  <P>Note that byte values are returned as <CODE>byte[]</CODE>, not 
     *  <CODE>Byte[]</CODE>.
     *
     *  @param name the name of the Java object
     *
     *  @return a copy of the Java object value with the specified name, in 
     *  objectified format (for example, if the object was set as an 
     *  <CODE>int</CODE>, an <CODE>Integer</CODE> is returned); if there is no 
     *  item by this name, a null value is returned
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     */
    public Object getObject(String name)
    throws JMSException {
        return this.map.get(name);
    }

    /** 
     *  Return the <CODE>short</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>short</CODE>
     *
     *  @return The <CODE>short</CODE> value with the specified name
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public short getShort(String name)
    throws JMSException {
        return ConvertValue.toShort(this.map.get(name));
    }

    /** 
     *  Return the <CODE>String</CODE> value with the specified name.
     *
     *  @param  name The name of the <CODE>String</CODE>
     *
     *  @return The <CODE>String</CODE> value with the specified name; if there 
     *          is no item by this name, a null value is returned
     *
     *  @throws JMSException if the JMS provider fails to read the message
     *          due to some internal error.
     *  @throws MessageFormatException if this type conversion is invalid.      
     */
    public String getString(String name)
    throws JMSException {
        return ConvertValue.toString(this.map.get(name));
    }

    /** 
     *  Indicate whether an item exists in this <CODE>MapMessage</CODE> object.
     *
     *  @param  name The name of the item to test
     *
     *  @return true If the item exists
     *
     *  @throws JMSException if the JMS provider fails to determine if the 
     *          item exists due to some internal error.
     */
    public boolean itemExists(String name)
    throws JMSException {
        return this.map.containsKey(name);
    }

    /** 
     *  Set a <CODE>boolean</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>boolean</CODE>
     *  @param  value The <CODE>boolean</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setBoolean(String name, boolean value)
    throws JMSException {
        String methodName = "setBoolean()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a <CODE>byte</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>byte</CODE>
     *  @param  value The <CODE>byte</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setByte(String name, byte value)
    throws JMSException {
        String methodName = "setByte()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a byte array value with the specified name into the Map.
     *
     *  @param  name the name of the byte array
     *  @param  value the byte array value to set in the Map; the array
     *          is copied so that the value for <CODE>name</CODE> will
     *          not be altered by future modifications
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws NullPointerException if the name is null, or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setBytes(String name, byte[] value)
    throws JMSException {
        String methodName = "setBytes(byte[])";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /**
     *  Set a portion of the byte array value with the specified name into the 
     *  Map.
     *  
     *  @param  name The name of the byte array
     *  @param  value The byte array value to set in the Map
     *  @param  offset The initial offset within the byte array
     *  @param  length The number of bytes to use
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setBytes(String name, byte[] value, int offset, int length)
    throws JMSException {
        String methodName = "setBytes(byte[], offset, length)";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        byte[] dest = new byte[length];
        System.arraycopy(value, offset, dest, 0, length);
        this.map.put(name, dest);
    }

    /** 
     *  Set a Unicode character value with the specified name into the Map.
     *
     *  @param  name The name of the Unicode character
     *  @param  value The Unicode character value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setChar(String name, char value)
    throws JMSException {
        String methodName = "setChar()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a <CODE>double</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>double</CODE>
     *  @param  value The <CODE>double</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setDouble(String name, double value)
    throws JMSException {
        String methodName = "setDouble()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a <CODE>float</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>float</CODE>
     *  @param  value The <CODE>float</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setFloat(String name, float value)
    throws JMSException {
        String methodName = "setFloat()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set an <CODE>int</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>int</CODE>
     *  @param  value The <CODE>int</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setInt(String name, int value)
    throws JMSException {
        String methodName = "setInt()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a <CODE>long</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>long</CODE>
     *  @param  value The <CODE>long</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setLong(String name, long value)
    throws JMSException {
        String methodName = "setLong()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set an object value with the specified name into the Map.
     *
     *  <P>This method works only for the objectified primitive
     *  object types (<code>Integer</code>, <code>Double</code>, 
     *  <code>Long</code>&nbsp;...), <code>String</code> objects, and byte 
     *  arrays.
     *
     *  @param  name The name of the Java object
     *  @param  value The Java object value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageFormatException if the object is invalid.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setObject(String name, Object value)
    throws JMSException {
        String methodName = "setLong()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this._checkValidObjectType(value, name);
        this.map.put(name, value);
    }

    /**
     *  Set a <CODE>short</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>short</CODE>
     *  @param  value The <CODE>short</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setShort(String name, short value)
    throws JMSException {
        String methodName = "setShort()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }

    /** 
     *  Set a <CODE>String</CODE> value with the specified name into the Map.
     *
     *  @param  name The name of the <CODE>String</CODE>
     *  @param  value The <CODE>String</CODE> value to set in the Map
     *
     *  @throws JMSException if the JMS provider fails to write the message
     *          due to some internal error.
     *  @throws IllegalArgumentException if the name is null or if the name is
     *          an empty string.
     *  @throws MessageNotWriteableException if the message is in read-only 
     *          mode.
     */
    public void setString(String name, String value)
    throws JMSException {
        String methodName = "setString()";
        this._checkValidKeyAndReadOnlyBody(methodName, name);
        this.map.put(name, value);
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.MapMessage
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    //  MQ methods DirectMapPacket / javax.jms.MapMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Set the JMS default values on this JMS MapMessage
     */
    protected void _setDefaultValues()
    throws JMSException {
        super._setDefaultValues();
        this.pkt.setPacketType(PacketType.MAP_MESSAGE);
    }

    /**
     *  Set the JMS Message body into the packet
     */
    protected void _setBodyToPacket()
    throws JMSException {
        try {
            this.byteArrayOutputStream = new ByteArrayOutputStream();
            this.objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

            this.objectOutputStream.writeObject(map);
            this.objectOutputStream.flush();

            this.messageBody = byteArrayOutputStream.toByteArray();

            this.objectOutputStream.close();
            this.byteArrayOutputStream.close();

            super._setMessageBodyOfPacket(this.messageBody);
        } catch (Exception ex) {
            String errMsg = _lgrMID_EXC +
                    ":MapMessage:Exception setting MapMessage body on send:"+
                    ex.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(ex);
            throw jmse;
        }
    }

    /**
     *  Get the message body from the packet
     */
    protected void _getMessageBodyFromPacket()
    throws JMSException {
        try {
            this.messageBody = super._getMessageBodyByteArray();
            this.byteArrayInputStream = new ByteArrayInputStream(messageBody);
            this.objectInputStream = new FilteringObjectInputStream (byteArrayInputStream);
            this.map = (Map<String, Object>)objectInputStream.readObject();
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC +
                    ":MapMessage:Exception deserializing on deliver:"+
                    e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new javax.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
    }

    private void _checkValidKeyAndReadOnlyBody(String methodName, String key)
    throws IllegalArgumentException, JMSException {
        this.checkForReadOnlyMessageBody(methodName);
        if (key == null || "".equals(key)){
            throw new IllegalArgumentException(
                    "MapMessage:" + methodName +
                    ":name parameter is not allowed to be NULL or empty");
        }
    }

    private void _checkValidObjectType(Object value, String name)
    throws MessageFormatException {
        if ((value != null) && (
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
                )) {
            //valid
        } else {
            String errMsg = _lgrMID_EXC +
                    ":MapMessage:setObject():Invalid type" +
                    ":name="+name+
                    ":type="+ 
                    (value == null 
                    ? "NULL"
                    : value.getClass().getName());
            _loggerJM.severe(errMsg);
            MessageFormatException mfe = new MessageFormatException(errMsg);
            throw mfe;
        }
    }
}
