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
 * @(#)DiagManager.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.*;
import java.util.Vector;
import java.util.Set;
import java.util.Iterator;
import java.util.Enumeration;


/**
 * DiagManager: Diagnostic data manager.
 *
 * DiagManager gathers and displays diagnostic data via introspection
 * of classes that have registered themselves with the manager.
 *
 * A class that wishes to be inspected must implement the DiagManager.Data
 * interface. It then must register itself with DiagManager using the
 * register() method.
 *
 * A data class must also provide a data dictionary (via
 * DiagManager.Data.getDictionary()) in the form of a List of
 * DiagDictionaryEntry's -- one entry per field. An entry consists of the
 * string name of the field and an integer containing one of the data type
 * constants that describe how the field will be used:
 *
 *          VARIABLE    The field may change value in any direction by
 *                      any amount from sample to sample.
 *          CONSTANT    The field will not change value from sample to sample.
 *          COUNTER     The field will only increase in value from sample
 *                      to sample (with the understanding that it may
 *                      wrap or be set to 0 on occasion).
 *
 * These types may influence how the data is gathered displayed. For example
 * CONSTANTs may only be sampled and displayed once. VARIABLEs may always
 * display the sampled value. COUNTERs may display the delta between the
 * previous sample and the current sample.
 *
 * Once data classes are registered, DiagManager.allToString() can be called
 * to generate a formatted string representing data in all registered classes.
 */
public class DiagManager {

    // Data types
    public static final int VARIABLE = 1;   // Data that may change
    public static final int CONSTANT = 2;   // Data that will not change
    public static final int COUNTER  = 3;   // Data that increases

    // List of registered diagnostic data objects
    protected static final Vector regList = new Vector();

    // Table of diagnostic data classes we allow to register
    protected static final HashMap diagClasses = new HashMap();

    // True to allow any object to register.
    // False to allow only objects that are instances of a registered class
    // to register.
    private static boolean allowAllReg = false;

    // Create a new DiagManager. 
    public DiagManager() {
    }

    // Register diagnostic data with DiagManager. Returns false
    // if the object's class is not allowed to register.
    public static boolean register(DiagManager.Data diag) {
        if (allowAllReg || diagClasses.get(diag.getClass()) != null) {
            regList.add(diag);
            return true;
        } else {
            return false;
        }
    }

    // Register diagnostic data class with DiagManager.
    // Only objects that are instances of registered classes
    // are allowed to register as diagnostic data.
    public static void registerClass(String diagName) 
	throws ClassNotFoundException, 
	       IllegalArgumentException, IllegalAccessException {

	Class cl = Class.forName(diagName);
        diagClasses.put(cl, cl);
    }

    // If true allows any object to register
    public static void registerAllClasses(boolean b) {
        allowAllReg = b;
    }

    // Generate a string representing all registered data
    public static String allToString() {
        StringBuffer sb = new StringBuffer();

        sb.append("Diagnostics:");
        Enumeration e = regList.elements();
        while (e.hasMoreElements()) {
            sb.append("\n");
            DiagManager.Data o = (DiagManager.Data)e.nextElement();
            sb.append(toDiagString(o));
        }
        return sb.toString();
    }

    // Generate a string representing all data in the specified data
    public static String toDiagString(DiagManager.Data diag) {
        StringBuffer d = new StringBuffer();
        StringBuffer sb = new StringBuffer();
        int cols = 0;

        diag.update();

        String prefix = diag.getPrefix();
        String title  = diag.getTitle();

        // The dictionary contains a list of field names that we are
        // to display the values for.
        List dictionary = diag.getDictionary();
        if (dictionary == null) {
            return "";
        }

        sb.append("========== " + title + " (" + prefix + ") ========== \n");
        Iterator iter = dictionary.iterator();
        DiagDictionaryEntry entry = null;

        while (iter.hasNext()) {
            entry = (DiagDictionaryEntry)iter.next();
            String name = entry.name;
            int type = entry.type;
            String value;

            try {
                // Get the field
                Field f = diag.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Class classType = f.getType();

                // Get a string representation of the data in the field
                if (classType == Long.TYPE) {
                    value = String.valueOf(f.getLong(diag));
                } else if (classType == Integer.TYPE) {
                    value = String.valueOf(f.getInt(diag));
                } else if (classType == Float.TYPE) {
                    value = String.valueOf(f.getFloat(diag));
                } else if (classType == Short.TYPE) {
                    value = String.valueOf(f.getShort(diag));
                } else if (classType == Byte.TYPE) {
                    value = String.valueOf(f.getByte(diag));
                } else if (classType == Boolean.TYPE) {
                    value = String.valueOf(f.getBoolean(diag));
                } else if (classType == Character.TYPE) {
                    value = String.valueOf(f.getChar(diag));
                } else if (classType == Double.TYPE) {
                    value = String.valueOf(f.getDouble(diag));
                } else {
                    value = (f.get(diag)).toString();
                }
            } catch (Exception e) {
                value = "Exception getting field value for '" + name + "': " +
                    e.toString();
            }

            d.setLength(0);
            d.append(name + "=" + value);

            // Wrap data at 80 columns
            if (cols > 0 && cols + d.length() > 78) {
                sb.append("\n");
                cols = d.length();
            } else if (cols == 0) {
                cols += d.length();
            } else {
                sb.append(", ");
                cols += d.length();
            }

            // To cover ", "
            cols += 2;
            sb.append(d);
        }

        return sb.toString();
    }

    /**
     * Enable diagnostics for classes specified by values in a
     * a Properties object. The Properties object should contain
     * a series of properties of the format:
     * <pre>
     * <prefix>.<classname>=true|false
     * </pre>
     * This method will enable/disable diagnostics for <classname>.
     * For example if "jmq.diag." is the prefix then
     * <pre>
     * jmq.diag.com.sun.messaging.jmq.util.ByteBufferPool=true
     * </pre>
     * Will enable instances of the
     * com.sun.messaging.jmq.util.ByteBufferPool
     * to register themselves to produce diagnostics.
     * <p>
     * If an error occurs when processing the properties further processing
     * stops and the appropriate exception is thrown.
     *
     * @param props	Properties object containing entries to enable diag on
     * @param prefix	String that the prefixes each classname. If a property
     *			does not begin with this string then it is ignored.
     *
     * @throws ClassNotFoundException if class is not found
     * @throws NoSuchFieldExcetpion if DEBUG field is not found in class
     */
    public static void registerClasses(Properties props, String prefix) 
	throws ClassNotFoundException, IllegalAccessException {

        String key;
        String value;

        // If imq.diag.*=true then allow all classes to register
        value = props.getProperty(prefix + "all");
        if (value != null && value.equalsIgnoreCase("true") ) {
            registerAllClasses(true);
            return;
        }

	// Scan through properties
	for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {
	    key = (String)e.nextElement();

	    // Find properties that match prefix
	    if (key.startsWith(prefix)) {

		// Get className and value and set debug
		String className = key.substring(prefix.length());
		if (className.length() != 0) {
		    value = (String)props.getProperty(key);
                    if (value.equalsIgnoreCase("true")) {
                        registerClass(className);
                    }
		}
	    }
	}
    }


    /**
     * Diagnostic Data. Any class that is going to register with
     * the DiagnosticManager must implement this interface.
     */
    public interface Data {

        /**
         * Update the diagnostic fields because a sample is about to
         * be taken. This method could be a no-op if your diagnostic
         * fields are updated continuously, but if you need to compute
         * or set values this is your opportunity to do so.
         */
        public void update();

        /**
         * Provide a data dictionary that defines what fields in the
         * class should be gathered and displayed as diagnostic data.
         * The data dictionary is a List of DiagDictionaryEntry's.
         * The fields in the dictionary will be displayed in the order
         * that they are listed in the List.
         *
         * getDictionary() may be called as often as before every sample.
         */
        public List getDictionary();

        /**
         * Provide a prefix that should be used if this data is merged
         * with other diagnostic data. For example this prefix could be
         * used to avoid field name conflicts if this data is merged 
         * into a hashtable with over diagnostic data.
         */
        public String getPrefix();

        /**
         * Provide a title that should be used when displaying this data.
         */
        public String getTitle();
    }

}
