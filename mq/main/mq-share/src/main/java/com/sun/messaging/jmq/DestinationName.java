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
 * %W% %G%
 */ 

package com.sun.messaging.jmq;

/**
 * <code>DestinationName</code> encapsulates the validation
 * of the JMQ provider specific syntax for Destination Names.
 */
public class DestinationName {

    /* No public constructor needed */
    private DestinationName(){}

    /**
     * Internal destination name prefix
     * @since 3.5
     */
    public static final String INTERNAL_DEST_PREFIX = "mq.";


    /**
     * Validates whether a name conforms to the
     * JMQ provider specific syntax for Destination
     * Names.
     * 
     * @param name The name to be validated.
     *
     * @return <code>true</code> if the name is valid;
     *         <code>false</code> if the name is invalid.
     */
    public static final boolean isSyntaxValid(String name) {
        //Invalid if name is null or empty.
        if (name == null || "".equals(name)) {
            return false;
        }
        if (isInternal(name)) {
	    /*
            // remove .'s for validation
            StringBuffer tmp = new StringBuffer(name);
            for (int i=0; i < tmp.length(); i ++) {
                if (tmp.charAt(i) == '.') {
                    tmp.setCharAt(i, '_');
                }
            }
            name = tmp.toString();
	    */
	    
	    /*
	     * Relax syntax checking if name starts with "mq."
	     * This is to allow temporary destinations to be
	     * monitored. The previous syntax checking
	     * was preventing destination names such as
	     * the following from being created:
	     *  mq.metrics.destination.queue.temporary_destination://queue/192.18.116.222/48422/1
	     */
	    return (true);
        }
        //Verify identifier start character and part
        char[] namechars = name.toCharArray();
        if (Character.isJavaIdentifierStart(namechars[0]) ||
            (namechars[0]=='*' || namechars[0]=='>')) {
            for (int i = 1; i<namechars.length; i++) {
                if (namechars[i] == '.') { // valid for wildcards
                } else if (namechars[i] == '*') { // valid for wildcards
                } else if (namechars[i] == '>') { // valid for whildcards
                } else if (!Character.isJavaIdentifierPart(namechars[i])) {
                    //Invalid if body characters are not valid using isJavaIdentifierPart().
                    return false;
                }
            }   
        } else {
            //Invalid if first character is not valid using isJavaIdentifierStart().
            return false;
        }
        return true;
    }

    /**
     * @since 3.5
     */
    public static boolean isInternal(String destName) {
	if ((destName != null) &&
	    destName.startsWith(INTERNAL_DEST_PREFIX))  {
	    return (true);
	}

	return (false);
    }

}

