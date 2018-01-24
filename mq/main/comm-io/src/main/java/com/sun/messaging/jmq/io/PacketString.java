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
 * @(#)PacketString.java	1.7 06/27/07
 */ 

package com.sun.messaging.jmq.io;

/**
 * This class enumerates all of the JMQ header string field types
 * and provides some convenience routines.
 */
public class PacketString {
    public static final int NULL          = 0;
    public static final int DESTINATION   = 1;
    public static final int MESSAGEID     = 2;
    public static final int CORRELATIONID = 3;
    public static final int REPLYTO       = 4;
    public static final int TYPE          = 5;
    public static final int DESTINATION_CLASS = 6;
    public static final int REPLYTO_CLASS     = 7;
    public static final int TRANSACTIONID     = 8;
    public static final int PRODUCERID        = 9;
    public static final int DELIVERY_TIME     = 10;
    public static final int DELIVERY_COUNT    = 11;
    public static final int LAST              = 12;

    private static final String[] names = {
	"NULL", "JMSDestination", "JMSMessageID",
	"JMSCorrelationID", "JMSReplyTo", "JMSType", "DestinationClass",
	"ReplyToClass", "TransacionID", "ProducerID", "DeliveryTime" };

    /**
     * Return a string description of the specified string type
     *
     * @param    n    Type to return description for
     */
    public static String getString(int n) {
	if (n < 0 || n >= LAST) {
	    return "INVALID_STRING";
	}

	return names[n] + "(" + n + ")";
    }
}
