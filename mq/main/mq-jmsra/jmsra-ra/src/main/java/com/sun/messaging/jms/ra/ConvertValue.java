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

import com.sun.messaging.AdministeredObject;
import javax.jms.MessageFormatException;

/**
 *
 */
public class ConvertValue {
    
    /**
     * Creates a new instance of ConvertValue
     */
    public ConvertValue() {
    }

    /**
     * If possible, converts the given object to boolean value
     * @param obj the object to convert
     * @return the converted value
     */
    static boolean
    toBoolean(Object obj) throws MessageFormatException {
        if (obj == null) {
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
     * This method is part of the implementation of the 
     * JMS 1.1 API method getByteProperty on the javax.jms.Message 
     * interface. Attempting to read a null value as a primitive type 
     * must be treated as calling the primitive's corresponding 
     * valueOf(String) conversion method with a null value.
     * @param obj the object to convert
     * @return the coverted value
     **/
     static byte toByte(Object obj) throws MessageFormatException {
        if (obj == null) {
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
     * This method is part of the implementation of the 
     * JMS 1.1 API method getShortProperty on the javax.jms.Message 
     * interface. Attempting to read a null value as a primitive type 
     * must be treated as calling the primitive's corresponding 
     * valueOf(String) conversion method with a null value.
     * @param obj the object to convert
     * @return the coverted value
     **/
    static short
    toShort(Object obj) throws MessageFormatException {
        if (obj == null) {
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
     * This method is part of the implementation of the 
     * JMS 1.1 API method getIntProperty on the javax.jms.Message 
     * interface. Attempting to read a null value as a primitive type 
     * must be treated as calling the primitive's corresponding 
     * valueOf(String) conversion method with a null value.
     * @param obj the object to convert
     * @return the coverted value
     **/
    static int
    toInt(Object obj) throws MessageFormatException {
        if (obj == null) {
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
