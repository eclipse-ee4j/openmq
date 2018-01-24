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

import java.util.Enumeration;

/** A {@code MapMessage} object is used to send a set of name-value pairs.
  * The names are {@code String} objects, and the values are primitive 
  * data types in the Java programming language. The names must have a value that
  * is not null, and not an empty string. The entries can be accessed 
  * sequentially or randomly by name. The order of the entries is undefined. 
  * {@code MapMessage} inherits from the {@code Message} interface
  * and adds a message body that contains a Map.
  *
  * <P>The primitive types can be read or written explicitly using methods
  * for each type. They may also be read or written generically as objects.
  * For instance, a call to {@code MapMessage.setInt("foo", 6)} is 
  * equivalent to {@code MapMessage.setObject("foo", new Integer(6))}.
  * Both forms are provided, because the explicit form is convenient for
  * static programming, and the object form is needed when types are not known
  * at compile time.
  *
  * <P>When a client receives a {@code MapMessage}, it is in read-only 
  * mode. If a client attempts to write to the message at this point, a 
  * {@code MessageNotWriteableException} is thrown. If 
  * {@code clearBody} is called, the message can now be both read from and 
  * written to.
  *
  * <P>{@code MapMessage} objects support the following conversion table. 
  * The marked cases must be supported. The unmarked cases must throw a 
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
  * @see         javax.jms.Session#createMapMessage()
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.Message
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */
 
public interface MapMessage extends Message { 


    /** Returns the {@code boolean} value with the specified name.
      *
      * @param name the name of the {@code boolean}
      *
      * @return the {@code boolean} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */

    boolean 
    getBoolean(String name) throws JMSException;


    /** Returns the {@code byte} value with the specified name.
      *
      * @param name the name of the {@code byte}
      *
      * @return the {@code byte} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    byte 
    getByte(String name) throws JMSException;


    /** Returns the {@code short} value with the specified name.
      *
      * @param name the name of the {@code short}
      *
      * @return the {@code short} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    short 
    getShort(String name) throws JMSException;


    /** Returns the Unicode character value with the specified name.
      *
      * @param name the name of the Unicode character
      *
      * @return the Unicode character value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.     
      */ 

    char 
    getChar(String name) throws JMSException;


    /** Returns the {@code int} value with the specified name.
      *
      * @param name the name of the {@code int}
      *
      * @return the {@code int} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    int 
    getInt(String name) throws JMSException;


    /** Returns the {@code long} value with the specified name.
      *
      * @param name the name of the {@code long}
      *
      * @return the {@code long} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    long 
    getLong(String name) throws JMSException;


    /** Returns the {@code float} value with the specified name.
      *
      * @param name the name of the {@code float}
      *
      * @return the {@code float} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.     
      */ 

    float 
    getFloat(String name) throws JMSException;


    /** Returns the {@code double} value with the specified name.
      *
      * @param name the name of the {@code double}
      *
      * @return the {@code double} value with the specified name
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    double 
    getDouble(String name) throws JMSException;


    /** Returns the {@code String} value with the specified name.
      *
      * @param name the name of the {@code String}
      *
      * @return the {@code String} value with the specified name; if there 
      * is no item by this name, a null value is returned
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    String 
    getString(String name) throws JMSException;


    /** Returns the byte array value with the specified name.
      *
      * @param name the name of the byte array
      *
      * @return a copy of the byte array value with the specified name; if there
      * is no
      * item by this name, a null value is returned.
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.      
      */ 

    byte[] 
    getBytes(String name) throws JMSException;


    /** Returns the value of the object with the specified name.
      *
      * <P>This method can be used to return, in objectified format,
      * an object in the Java programming language ("Java object") that had 
      * been stored in the Map with the equivalent
      * {@code setObject} method call, or its equivalent primitive
      * <code>set<I>type</I></code> method.
      *
      * <P>Note that byte values are returned as {@code byte[]}, not 
      * {@code Byte[]}.
      *
      * @param name the name of the Java object
      *
      * @return a copy of the Java object value with the specified name, in 
      * objectified format (for example, if the object was set as an 
      * {@code int}, an {@code Integer} is returned); if there is no 
      * item by this name, a null value is returned
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      */ 

    Object 
    getObject(String name) throws JMSException;



    /** Returns an {@code Enumeration} of all the names in the 
      * {@code MapMessage} object.
      *
      * @return an enumeration of all the names in this {@code MapMessage}
      *
      * @exception JMSException if the JMS provider fails to read the message
      *                         due to some internal error.
      */

    Enumeration
    getMapNames() throws JMSException;


    /** Sets a {@code boolean} value with the specified name into the Map.
      *
      * @param name the name of the {@code boolean}
      * @param value the {@code boolean} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */

    void 
    setBoolean(String name, boolean value) throws JMSException;


    /** Sets a {@code byte} value with the specified name into the Map.
      *
      * @param name the name of the {@code byte}
      * @param value the {@code byte} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
     * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setByte(String name, byte value) 
			throws JMSException;


    /** Sets a {@code short} value with the specified name into the Map.
      *
      * @param name the name of the {@code short}
      * @param value the {@code short} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
       * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setShort(String name, short value) 
			throws JMSException;


    /** Sets a Unicode character value with the specified name into the Map.
      *
      * @param name the name of the Unicode character
      * @param value the Unicode character value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
       * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setChar(String name, char value) 
			throws JMSException;


    /** Sets an {@code int} value with the specified name into the Map.
      *
      * @param name the name of the {@code int}
      * @param value the {@code int} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setInt(String name, int value) 
			throws JMSException;


    /** Sets a {@code long} value with the specified name into the Map.
      *
      * @param name the name of the {@code long}
      * @param value the {@code long} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setLong(String name, long value) 
			throws JMSException;


    /** Sets a {@code float} value with the specified name into the Map.
      *
      * @param name the name of the {@code float}
      * @param value the {@code float} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
       * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setFloat(String name, float value) 
			throws JMSException;


    /** Sets a {@code double} value with the specified name into the Map.
      *
      * @param name the name of the {@code double}
      * @param value the {@code double} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setDouble(String name, double value) 
			throws JMSException;


    /** Sets a {@code String} value with the specified name into the Map.
      *
      * @param name the name of the {@code String}
      * @param value the {@code String} value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setString(String name, String value) 
			throws JMSException;


    /** Sets a byte array value with the specified name into the Map.
      *
      * @param name the name of the byte array
      * @param value the byte array value to set in the Map; the array
      *              is copied so that the value for {@code name} will
      *              not be altered by future modifications
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null, or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void
    setBytes(String name, byte[] value) 
			throws JMSException;


    /** Sets a portion of the byte array value with the specified name into the 
      * Map.
      *  
      * @param name the name of the byte array
      * @param value the byte array value to set in the Map
      * @param offset the initial offset within the byte array
      * @param length the number of bytes to use
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
       * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 
 
    void
    setBytes(String name, byte[] value, 
		 int offset, int length) 
			throws JMSException;


    /** Sets an object value with the specified name into the Map.
      *
      * <P>This method works only for the objectified primitive
      * object types ({@code Integer}, {@code Double}, 
      * {@code Long}&nbsp;...), {@code String} objects, and byte 
      * arrays.
      *
      * @param name the name of the Java object
      * @param value the Java object value to set in the Map
      *
      * @exception JMSException if the JMS provider fails to write the message
      *                         due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageFormatException if the object is invalid.
      * @exception MessageNotWriteableException if the message is in read-only 
      *                                         mode.
      */ 

    void 
    setObject(String name, Object value) 
			throws JMSException;


    /** Indicates whether an item exists in this {@code MapMessage} object.
      *
      * @param name the name of the item to test
      *
      * @return true if the item exists
      *
      * @exception JMSException if the JMS provider fails to determine if the 
      *                         item exists due to some internal error.
      */ 
 
    boolean
    itemExists(String name) throws JMSException;
}
