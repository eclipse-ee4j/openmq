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
 * @(#)MapMessageImpl.java	1.20 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.*;
import java.io.*;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

/** A MapMessage is used to send a set of name-value pairs where names are
  * Strings and values are Java primitive types. The entries can be accessed
  * sequentially or randomly by name. The order of the entries is undefined.
  * It inherits from <CODE>Message</CODE> and adds a map message body.
  *
  * <P>The primitive types can be read or written explicitly using methods
  * for each type. They may also be read or written generically as objects.
  * For instance, a call to <CODE>MapMessage.setInt("foo", 6)</CODE> is
  * equivalent to <CODE>MapMessage.setObject("foo", new Integer(6))</CODE>.
  * Both forms are provided because the explicit form is convenient for
  * static programming and the object form is needed when types are not known
  * at compile time.
  *
  * <P>When a client receives a MapMessage, it is in read-only mode. If a
  * client attempts to write to the message at this point, a
  * MessageNotWriteableException is thrown. If <CODE>clearBody</CODE> is
  * called, the message can now be both read from and written to.
  *
  * <P>Map messages support the following conversion table. The marked cases
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
  * conversion method with a null value. Since char does not support a
  * String conversion, attempting to read a null value as a char must
  * throw NullPointerException.
  *
  * @see         javax.jms.Session#createMapMessage()
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  */

public class MapMessageImpl extends MessageImpl implements MapMessage {

    //private Hashtable mapMessage = new Hashtable();
    private Map mapMessage = new HashMap();

    private byte[] messageBody = null;
    //private byte[] defaultBytes = new byte [32];

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    protected MapMessageImpl() throws JMSException {
        super();
        setPacketType (PacketType.MAP_MESSAGE);
    }


     //serialize message body
    //This is called when producing messages.
    protected void
    setMessageBodyToPacket() throws JMSException {

        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream ( byteArrayOutputStream );

            objectOutputStream.writeObject( mapMessage );
            objectOutputStream.flush();

            messageBody = byteArrayOutputStream.toByteArray();

            objectOutputStream.close();
            byteArrayOutputStream.close();
            setMessageBody ( messageBody );

        } catch (Exception e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_SERIALIZE);
        }

    }

    //deserialize message body
    //This is called after message is received in Session Reader.
    protected void
    getMessageBodyFromPacket() throws JMSException {

        try {
            messageBody = getMessageBody();
            byteArrayInputStream = new ByteArrayInputStream ( messageBody );
            objectInputStream = new FilteringObjectInputStream ( byteArrayInputStream );

            mapMessage = (Map) objectInputStream.readObject();

        } catch (Exception e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_DESERIALIZE);
        }
    }


    /*
     * Over writing super class method.
     */
    public void
    clearBody() throws JMSException {
        mapMessage.clear();
        setMessageReadMode (false);
    }

    /** Return the boolean value with the given name.
      *
      * @param name the name of the boolean
      *
      * @return the boolean value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public boolean
    getBoolean(String name) throws JMSException {
        return ValueConvert.toBoolean( mapMessage.get(name) );
    }


    /** Return the byte value with the given name.
      *
      * @param name the name of the byte
      *
      * @return the byte value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public byte
    getByte(String name) throws JMSException {
        return ValueConvert.toByte( mapMessage.get(name) );
    }

    /** Return the short value with the given name.
      *
      * @param name the name of the short
      *
      * @return the short value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public short
    getShort(String name) throws JMSException {
        return ValueConvert.toShort( mapMessage.get(name) );
    }


    /** Return the Unicode character value with the given name.
      *
      * @param name the name of the Unicode character
      *
      * @return the Unicode character value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public char
    getChar(String name) throws JMSException {
        return ValueConvert.toChar( mapMessage.get(name) );
    }


    /** Return the integer value with the given name.
      *
      * @param name the name of the integer
      *
      * @return the integer value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public int
    getInt(String name) throws JMSException {
        return ValueConvert.toInt( mapMessage.get(name) );
    }


    /** Return the long value with the given name.
      *
      * @param name the name of the long
      *
      * @return the long value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public long
    getLong(String name) throws JMSException {
        return ValueConvert.toLong( mapMessage.get(name) );
    }


    /** Return the float value with the given name.
      *
      * @param name the name of the float
      *
      * @return the float value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public float
    getFloat(String name) throws JMSException {
        return ValueConvert.toFloat( mapMessage.get(name) );
    }


    /** Return the double value with the given name.
      *
      * @param name the name of the double
      *
      * @return the double value with the given name.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public double
    getDouble(String name) throws JMSException {
        return ValueConvert.toDouble( mapMessage.get(name) );
    }


    /** Return the String value with the given name.
      *
      * @param name the name of the String
      *
      * @return the String value with the given name. If there is no item
      * by this name, a null value is returned.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public String
    getString(String name) throws JMSException {
        return ValueConvert.toString( mapMessage.get(name) );
    }


    /** Return the byte array value with the given name.
      *
      * @param name the name of the byte array
      *
      * @return a copy of the byte array value with the given name. If there is no
      * item by this name, a null value is returned.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if this type conversion is invalid.
      */

    public byte[]
    getBytes(String name) throws JMSException {
        Object obj = mapMessage.get(name);
        if (obj == null) {
            return null;
        }
        else if (obj instanceof byte[]) {
            return (byte[])obj;
        }
        else {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_MESSAGE_FORMAT);

            JMSException jmse =
                new MessageFormatException(errorString, ClientResources.X_MESSAGE_FORMAT);

            ExceptionHandler.throwJMSException(jmse);
        }

        return null;
    }


    /** Return the Java object value with the given name.
      *
      * <P>Note that this method can be used to return in objectified format,
      * an object that had been stored in the Map with the equivalent
      * <CODE>setObject</CODE> method call, or it's equivalent primitive
      * set<type> method.
      *
      * @param name the name of the Java object
      *
      * @return a copy of the Java object value with the given name, in objectified
      * format (ie. if it set as an int, then a Integer is returned).
      * Note that byte values are returned as byte[], not Byte[].
      * If there is no item by this name, a null value is returned.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public Object
    getObject(String name) throws JMSException {
        return mapMessage.get(name);
    }



    /** Return an Enumeration of all the Map message's names.
      *
      * @return an enumeration of all the names in this Map message.
      *
      * @exception JMSException if JMS fails to read message due to
      *                         some internal JMS error.
      */

    public Enumeration
    getMapNames() throws JMSException {
        //return mapMessage.keys();
        Set names = mapMessage.keySet();
        return new Vector(names).elements();
    }


    /** Set a boolean value with the given name, into the Map.
      *
      * @param name the name of the boolean
      * @param value the boolean value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setBoolean(String name, boolean value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Boolean.valueOf (value) );
    }


    /** Set a byte value with the given name, into the Map.
      *
      * @param name the name of the byte
      * @param value the byte value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setByte(String name, byte value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Byte.valueOf (value) );
    }


    /** Set a short value with the given name, into the Map.
      *
      * @param name the name of the short
      * @param value the short value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setShort(String name, short value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Short.valueOf (value) );
    }


    /** Set a Unicode character value with the given name, into the Map.
      *
      * @param name the name of the Unicode character
      * @param value the Unicode character value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setChar(String name, char value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Character.valueOf (value) );
    }

    /** Set an integer value with the given name, into the Map.
      *
      * @param name the name of the integer
      * @param value the integer value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setInt(String name, int value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Integer.valueOf (value) );
    }

    /** Set a long value with the given name, into the Map.
      *
      * @param name the name of the long
      * @param value the long value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setLong(String name, long value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Long.valueOf (value) );
    }

    /** Set a float value with the given name, into the Map.
      *
      * @param name the name of the float
      * @param value the float value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setFloat(String name, float value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Float.valueOf(value) );
    }


    /** Set a double value with the given name, into the Map.
      *
      * @param name the name of the double
      * @param value the double value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setDouble(String name, double value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, Double.valueOf (value) );
    }

    /** Set a String value with the given name, into the Map.
      *
      * @param name the name of the String
      * @param value the String value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setString(String name, String value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, value );
    }


    /** Set a byte array value with the given name, into the Map.
      *
      * @param name the name of the byte array
      * @param value the byte array value to set in the Map.
      *              The array is copied so the value for name will
      *              not be altered by future modifications.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setBytes(String name, byte[] value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        mapMessage.put( name, value );
    }


    /** Set a portion of the byte array value with the given name, into the Map.
      *
      * @param name the name of the byte array
      * @param value the byte array value to set in the Map.
      * @param offset the initial offset within the byte array.
      * @param length the number of bytes to use.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setBytes(String name, byte[] value, int offset, int length)
            throws JMSException {

        checkMessageAccess();
        checkName (name);

        byte[] dest = new byte[length];
        System.arraycopy(value, offset, dest, 0, length);

        mapMessage.put(name, dest);
    }


    /** Set a Java object value with the given name, into the Map.
      *
      * <P>Note that this method only works for the objectified primitive
      * object types (Integer, Double, Long ...), String's and byte arrays.
      *
      * @param name the name of the Java object
      * @param value the Java object value to set in the Map.
      *
      * @exception JMSException if JMS fails to write message due to
      *                         some internal JMS error.
      * @exception MessageFormatException if object is invalid
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setObject(String name, Object value) throws JMSException {
        checkMessageAccess();
        checkName (name);
        checkValidObjectType(value);
        mapMessage.put( name, value );
    }

    private void
    checkValidObjectType (Object value) throws MessageFormatException {

        if ( value instanceof Boolean  ||
             value instanceof Byte     ||
             value instanceof Short    ||
             value instanceof Character||
             value instanceof Integer  ||
             value instanceof Long     ||
             value instanceof Float    ||
             value instanceof Double   ||
             value instanceof String   ||
             value instanceof byte[])  {
            //ok, do nothing
        } else {
            //throw exception
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, ClientResources.X_MESSAGE_FORMAT);
        }
    }

    /**
     * check if map message name is valid. Name MUST NOT be null or empty.
     *
     * @throw java.lang.IllegalArgumentException - if the name is null or if
     * the name is an empty string.
     *
     */
    private void checkName (String name) throws IllegalArgumentException {

        if ( name == null || name.length() == 0 ) {
            throw new
            IllegalArgumentException ("Name MUST NOT be null or empty.");
        }

    }

    /** Check if an item exists in this MapMessage.
      *
      * @param name the name of the item to test
      *
      * @return true if the item does exist.
      *
      * @exception JMSException if a JMS error occurs.
      */

    public boolean
    itemExists(String name) throws JMSException {
        return (mapMessage.containsKey(name));
    }

     public void dump (PrintStream ps) {
        ps.println ("------ MapMessageImpl dump ------");
        super.dump (ps);
    }

    public String toString() {
        return super.toString();
    }
}
