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
 * @(#)ValueConvert.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.MessageFormatException;

import com.sun.messaging.AdministeredObject;

/** This class is used by
  *
  * MessageImpl       for property value conversion
  * MapMessageImpl    for mapped value read conversion (see MapMessageImpl.java)
  * StreamMessageImpl for data read conversion (see StreamMessageImpl.java)
  *
  * <P>Message properties support the following conversion table. The marked
  * cases must be supported. The unmarked cases must throw a JMSException. The
  * String to primitive conversions may throw a runtime exception if the
  * primitives <CODE>valueOf()</CODE> method does not accept it as a valid
  * String representation of the primitive.
  *
  * <P>A value written as the row type can be read as the column type.
  *
  * <PRE>
  * |        | boolean byte short int long float double String
  * |----------------------------------------------------------
  * |boolean |    X                                       X
  * |byte    |          X     X    X   X                  X
  * |short   |                X    X   X                  X
  * |int     |                     X   X                  X
  * |long    |                         X                  X
  * |float   |                               X     X      X
  * |double  |                                     X      X
  * |String  |    X     X     X    X   X     X     X      X
  * |----------------------------------------------------------
  * </PRE>
  *
  * <P>In addition to the type-specific set/get methods for properties, JMS
  * provides the <CODE>setObjectProperty</CODE> and
  * <CODE>getObjectProperty</CODE> methods. These support the same set of
  * property types using the objectified primitive values. Their purpose is
  * to allow the decision of property type to made at execution time rather
  * than at compile time. They support the same property value conversions.
  *
  * <P>The <CODE>setObjectProperty</CODE> method accepts values of class
  * Boolean, Byte, Short, Integer, Long, Float, Double and String. An attempt
  * to use any other class must throw a JMSException.
  *
  * <P>The <CODE>getObjectProperty</CODE> method only returns values of class
  * Boolean, Byte, Short, Integer, Long, Float, Double and String.
  */

class ValueConvert {

    /**
     * If possible, converts the given object to boolean value
     * @param obj the object to convert
     * @return the converted value
     */
    static boolean
    toBoolean(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// deliberately delegate the handling of this null value to the primitive's valueOf method
        	// in this case it will return false
            return Boolean.valueOf((String)null).booleanValue();
        }
        else if (obj instanceof Boolean) {
            return ((Boolean)obj).booleanValue();
        }
        else if (obj instanceof String) {
            return Boolean.valueOf((String)obj).booleanValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }

    /**
     * If possible, converts the given object to the byte value
     * @param obj the object to convert
     * @return the coverted value
     **/
     static byte toByte(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// deliberately delegate the handling of this null value to the primitive's valueOf method
        	// in this case it will throw a java.lang.NumberFormatException 
            return Byte.valueOf((String)null).byteValue();
        }
        else if (obj instanceof Byte) {
            return ((Byte)obj).byteValue();
        }
        else if (obj instanceof String) {
            return Byte.valueOf((String)obj).byteValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }

    /**
     * If possible, converts the given object to the short value
     * @param obj the object to convert
     * @return the coverted value
     **/
    static short
    toShort(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// deliberately delegate the handling of this null value to the primitive's valueOf method
        	// in this case it will throw a java.lang.NumberFormatException 
            return Short.valueOf((String)null).shortValue();
        }
        else if (obj instanceof Short) {
            return ((Short)obj).shortValue();
        }
        else if (obj instanceof String) {
            return Short.valueOf((String)obj).shortValue();
        }
        else if (obj instanceof Byte) {
            return ((Byte)obj).shortValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }


    /**
     * If possible, converts the given object to the int value
     * @param obj the object to convert
     * @return the coverted value
     **/
    static int
    toInt(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// must throw the same exception as if we had passed a null value to the primitive's valueOf method
            throw new NumberFormatException("null");
        }
        else if (obj instanceof Integer) {
            return ((Integer)obj).intValue();
        }
        else if (obj instanceof String) {
            return Integer.parseInt((String)obj);
        }
        else if (obj instanceof Byte) {
            return ((Byte)obj).intValue();
        }
        else if (obj instanceof Short) {
            return ((Short)obj).intValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }

    /**
     * If possible, converts the given object to the long value
     * @param obj the object to convert
     * @return the coverted value
     */
    static long
    toLong(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// must throw the same exception as if we had passed a null value to the primitive's valueOf method
            throw new NumberFormatException("null");
        }
        else if (obj instanceof Long) {
            return ((Long)obj).longValue();
        }
        else if (obj instanceof String) {
            return Long.parseLong((String)obj);
        }
        else if (obj instanceof Byte) {
            return ((Byte)obj).longValue();
        }
        else if (obj instanceof Short) {
            return ((Short)obj).longValue();
        }
        else if (obj instanceof Integer) {
            return ((Integer)obj).longValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }


    /**
     * If possible, converts the given object to the float value
     * @param obj the non null object to convert
     * @return the coverted value
     **/
    static float
    toFloat(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// CTS expects this exception type so will conform to it
        	throw new NullPointerException();
        }
        else if (obj instanceof Float) {
            return ((Float)obj).floatValue();
        }
        else if (obj instanceof String) {
            return Float.valueOf((String)obj).floatValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }

    /**
     * If possible, converts the given object to the double value
     * @param obj the non null object to convert
     * @return the coverted value
     */
    static double
    toDouble(Object obj) throws MessageFormatException {
        if (obj == null) {
        	// CTS expects this exception type so will conform to it
        	throw new NullPointerException();
        }
        else if (obj instanceof Float) {
            return ((Float)obj).doubleValue();
        }
        else if (obj instanceof Double) {
            return ((Double)obj).doubleValue();
        }
        else if (obj instanceof String) {
            return Double.valueOf((String)obj).doubleValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }

    /**
     * If possible, converts the given object to the String value
     * @param obj the object to convert
     * @return the coverted value
     */
    static String
    toString(Object obj) throws MessageFormatException {

        /**
         * Can not convert byte[] to string object.
         */
        if ( obj instanceof byte[] ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }

        if ( obj == null ) {
            return null;
        }
        else if (obj instanceof String) {
            return (String)obj;
        }
        else {
            return obj.toString();
        }
    }

    /**
     * If possible, converts the given object to the char value
     * @param obj the object to convert
     * @return the coverted value
     */
    static char
    toChar(Object obj) throws MessageFormatException  {
        if (obj == null) {
        	// explicitly throw a java.lang.NullPointerException: [C4017]: Invalid message format.
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new NullPointerException(errorString);
        }
        else if (obj instanceof Character) {
            return ((Character)obj).charValue();
        }
        else {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_FORMAT);
            throw new MessageFormatException(errorString, AdministeredObject.cr.X_MESSAGE_FORMAT);
        }
    }
}
