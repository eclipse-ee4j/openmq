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
 * @(#)Debug.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.Properties;
import java.util.Enumeration;
import java.lang.reflect.Field;

/**
 * Debug
 */
public class Debug {

    public final static String debugFieldName = "DEBUG";

    /**
     * Set the DEBUG flag on the specified class. The DEBUG field
     * is assumed to be declared:
     * <pre>
     * public static boolean DEBUG;
     * </pre>
     * @param className	Fully qualified classname to set DEBUG on
     * @param debug	Value to set DEBUG to
     *
     *
     * @throws ClassNotFoundException if class is not found
     * @throws NoSuchFieldExcetpion if DEBUG field is not found in class
     */
    public static void setDebug (String className, boolean debug) 
	throws ClassNotFoundException, NoSuchFieldException,
	       IllegalArgumentException, IllegalAccessException {

	Class cl = Class.forName(className);
    Field[] fields = cl.getDeclaredFields();
    for (int i = 0; i < fields.length; i++) {
        if (fields[i].getName().equals(debugFieldName)) {
            fields[i].setAccessible(true);
            fields[i].setBoolean(null, debug);
            return;
        }
    }
    throw new NoSuchFieldException(debugFieldName);

    }

    /**
     * Set the DEBUG flag on the classes specified by values in a
     * a Properties object. The Properties object should contain
     * a series of properties of the format:
     * <pre>
     * <prefix>.<classname>=true|false
     * </pre>
     * This method will set the DEBUG flag on <classname> to the specified
     * value. For example if "jmq.debug." is the prefix then
     * <pre>
     * jmq.debug.com.sun.messaging.jmq.jmsserver.data.AcknowledgeList=true
     * </pre>
     * Will set
     * com.sun.messaging.jmq.jmsserver.data.AcknowledgeList.DEBUG 
     * to true.
     * <p>
     * If an error occurs when processing the properties further processing
     * stops and the appropriate exception is thrown.
     *
     * @param props	Properties object containing entries to set DEBUG on
     * @param prefix	String that the prefixes each classname. If a property
     *			does not begin with this string then it is ignored.
     *
     * @throws ClassNotFoundException if class is not found
     * @throws NoSuchFieldExcetpion if DEBUG field is not found in class
     */
    public static void setDebug (Properties props, String prefix) 
	throws ClassNotFoundException, NoSuchFieldException,
	       IllegalAccessException {

	// Scan through properties
	for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {
	    String key = (String)e.nextElement();

	    // Find properties that match prefix
	    if (key.startsWith(prefix)) {

		// Get className and value and set debug
		String className = key.substring(prefix.length());
		if (className.length() != 0) {
		    String value = (String)props.getProperty(key);
		    if (value != null && value.length() != 0) {
			try {
		            setDebug(className,
				 (Boolean.valueOf(value)).booleanValue() );
			} catch (NoSuchFieldException ex) {
			    throw new NoSuchFieldException(className +
				"." + debugFieldName);
			} catch (IllegalAccessException ex) {
			    throw new IllegalAccessException(className +
				"." + debugFieldName);
			}
		    }
		}
	    }
	}
    }
}

