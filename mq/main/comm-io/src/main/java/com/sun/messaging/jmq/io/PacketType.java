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
 * @(#)PacketType.java	1.46 06/27/07
 */ 

package com.sun.messaging.jmq.io;

/**
 * This class enumerates all of the JMQ packet types and provides
 * some convenience routines
 */
public class PacketType {

    /* constant strings represent operations in access control
     * the strings are exposed in ACL properties
     */
    public static final String AC_PRODUCE            = "produce";
    public static final String AC_CONSUME            = "consume";
    public static final String AC_BROWSE             = "browse";
    public static final String AC_DESTCREATE         = "create";

    /* Body types used by some control packets */
    public static final int NONE                        = 0;
    public static final int CONSUMERID_I_SYSMESSAGEID   = 1;
    public static final int CONSUMERID_L_SYSMESSAGEID   = 2;
    public static final int SYSMESSAGEID                = 3;
    public static final int SESSIONID_SYSMESSAGEID =     4;

    /*
     * Our loose convention is that the first 8 types are reserved for
     * basic JMS message types (of which there are currently 6). After
     * that even types are requests and odd types are replies. That's why
     * you'll see some holes in the sequence for requests that don't
     * have replies.
     * 
     * Note that Packet.isReply() depends on this convention.
     * 
     */
    public static final int NULL                      =  0;

    public static final int TEXT_MESSAGE              =  1;
    public static final int BYTES_MESSAGE             =  2;
    public static final int MAP_MESSAGE               =  3;
    public static final int STREAM_MESSAGE            =  4;
    public static final int OBJECT_MESSAGE            =  5;
    public static final int MESSAGE                   =  6;

    public static final int SEND_REPLY                =  9;

    public static final int HELLO                     = 10;
    public static final int HELLO_REPLY               = 11;
    public static final int AUTHENTICATE              = 12;
    public static final int AUTHENTICATE_REPLY        = 13;
    public static final int ADD_CONSUMER              = 14;
    public static final int ADD_CONSUMER_REPLY        = 15;
    public static final int DELETE_CONSUMER           = 16;
    public static final int DELETE_CONSUMER_REPLY     = 17;
    public static final int ADD_PRODUCER              = 18;
    public static final int ADD_PRODUCER_REPLY        = 19;
    public static final int START                     = 20;

    public static final int STOP                      = 22;
    public static final int STOP_REPLY                = 23;
    public static final int ACKNOWLEDGE               = 24;
    public static final int ACKNOWLEDGE_REPLY         = 25;
    public static final int BROWSE                    = 26;
    public static final int BROWSE_REPLY              = 27;
    public static final int MESSAGE_SET               = 27; // for bkwds compat
    public static final int GOODBYE                   = 28;
    public static final int GOODBYE_REPLY             = 29;

    public static final int ERROR                     = 30;

    public static final int REDELIVER                 = 32;

    public static final int CREATE_DESTINATION        = 34;
    public static final int CREATE_DESTINATION_REPLY  = 35;
    public static final int DESTROY_DESTINATION       = 36;
    public static final int DESTROY_DESTINATION_REPLY = 37;
    public static final int AUTHENTICATE_REQUEST      = 38;

    public static final int VERIFY_DESTINATION        = 40;
    public static final int VERIFY_DESTINATION_REPLY  = 41;
    public static final int DELIVER                   = 42;
    public static final int DELIVER_REPLY             = 43;
    public static final int START_TRANSACTION         = 44;
    public static final int START_TRANSACTION_REPLY   = 45;
    public static final int COMMIT_TRANSACTION        = 46;
    public static final int COMMIT_TRANSACTION_REPLY  = 47;
    public static final int ROLLBACK_TRANSACTION      = 48;
    public static final int ROLLBACK_TRANSACTION_REPLY = 49;

    public static final int SET_CLIENTID              = 50;
    public static final int SET_CLIENTID_REPLY        = 51;

    public static final int RESUME_FLOW               = 52;

    public static final int PING                      = 54;
    public static final int PING_REPLY                = 55;

    public static final int PREPARE_TRANSACTION       = 56;
    public static final int PREPARE_TRANSACTION_REPLY = 57;

    public static final int END_TRANSACTION           = 58;
    public static final int END_TRANSACTION_REPLY     = 59;

    public static final int RECOVER_TRANSACTION       = 60;
    public static final int RECOVER_TRANSACTION_REPLY = 61;

    public static final int GENERATE_UID              = 62;
    public static final int GENERATE_UID_REPLY        = 63;

    public static final int FLOW_PAUSED               = 64;

    public static final int DELETE_PRODUCER           = 66;
    public static final int DELETE_PRODUCER_REPLY     = 67;

    public static final int CREATE_SESSION            = 68;
    public static final int CREATE_SESSION_REPLY      = 69;

    public static final int DESTROY_SESSION           = 70;
    public static final int DESTROY_SESSION_REPLY     = 71;

    public static final int INFO_REQUEST              = 72;
    public static final int INFO                      = 73;

    public static final int DEBUG                     = 74;

    public static final int GET_LICENSE               = 76;
    public static final int GET_LICENSE_REPLY         = 77;

    public static final int VERIFY_TRANSACTION        = 78;
    public static final int VERIFY_TRANSACTION_REPLY  = 79;

    public static final int LAST                      = 80;

    /* 2nd dimenssion is for access control, null means no accesscontrol check
       otherwise is a access control predefined operation string */
    private static final String[][] names = {
	{"NULL", null},
	{"TEXT_MESSAGE", null},
	{"BYTES_MESSAGE", null},
	{"MAP_MESSAGE", null},
	{"STREAM_MESSAGE", null},
	{"OBJECT_MESSAGE", null},
	{"MESSAGE", null},
	{"TBD", null},
	{"TBD", null},
	{"SEND_REPLY", null},
	{"HELLO", null},
	{"HELLO_REPLY", null},
	{"AUTHENTICATE", null},
	{"AUTHENTICATE_REPLY", null},
	{"ADD_CONSUMER", AC_CONSUME},
	{"ADD_CONSUMER_REPLY", null},
	{"DELETE_CONSUMER", null},
	{"DELETE_CONSUMER_REPLY", null},
	{"ADD_PRODUCER", AC_PRODUCE},
	{"ADD_PRODUCER_REPLY", null},
	{"START", null},
	{"TBD", null},
	{"STOP", null},
	{"STOP_REPLY", null},
	{"ACKNOWLEDGE", null},
	{"ACKNOWLEDGE_REPLY", null},
	{"BROWSE", AC_BROWSE},
	{"BROWSE_REPLY", null},
	{"GOODBYE", null},
	{"GOODBYE_REPLY", null},
	{"ERROR", null},
	{"TBD", null},
	{"REDELIVER", null},
	{"TBD", null},
	{"CREATE_DESTINATION", AC_DESTCREATE},
	{"CREATE_DESTINATION_REPLY", null},
	{"DESTROY_DESTINATION", null},
	{"DESTROY_DESTINATION_REPLY", null},
       {"AUTHENTICATE_REQUEST", null},
       {"TBD", null},
       {"VERIFY_DESTINATION", null},
       {"VERIFY_DESTINATION_REPLY", null},
       {"DELIVER", null},
       {"DELIVER_REPLY", null},
       {"START_TRANSACTION", null},
       {"START_TRANSACTION_REPLY", null},
       {"COMMIT_TRANSACTION", null},
       {"COMMIT_TRANSACTION_REPLY", null},
       {"ROLLBACK_TRANSACTION", null},
       {"ROLLBACK_REPLY", null},
       {"SET_CLIENTID", null},
       {"SET_CLIENTID_REPLY", null},
       {"RESUME_FLOW", null},
	{"TBD", null},
	{"PING", null},
	{"PING_REPLY", null},
	{"PREPARE_TRANSACTION", null},
	{"PREPARE_TRANSACTION_REPLY", null},
	{"END_TRANSACTION", null},
	{"END_TRANSACTION_REPLY", null},
	{"RECOVER_TRANSACTION", null},
	{"RECOVER_TRANSACTION_REPLY", null},
	{"GENERATE_UID", null},
	{"GENERATE_UID_REPLY", null},
	{"FLOW_PAUSED", null},
	{"TBD", null},
        {"DELETE_PRODUCER", null},
        {"DELETE_PRODUCER_REPLY", null},
        {"CREATE_SESSION", null},
        {"CREATE_SESSION_REPLY", null},
        {"DESTROY_SESSION", null},
        {"DESTROY_SESSION_REPLY", null},
        {"INFO_REQUEST", null},
        {"INFO", null},
        {"DEBUG", null},
        {"TBD", null},
        {"GET_LICENSE", null},
        {"GET_LICENSE_REPLY", null},
        {"VERIFY_TRANSACTION", null},
        {"VERIFY_TRANSACTION_REPLY", null},
	{"LAST", null}
	};

    /**
     * Return a string description of the specified packet type
     *
     * @param    n    Type to return description for
     */
    public static String getString(int n) {
	if (n < 0 || n >= LAST) {
	    return "UNKNOWN(" + n + ")";
	}
	return names[n][0] + "(" + n + ")";
    }

    /**
     * map a packet type index to a access control operation string
     * not all packet types will associate to an op string
     *
     * @param n packet type
     */
    public static String mapOperation(int n) {
	if (n < 0 || n >= LAST) {
	    return null;
	}
	return names[n][1];
    }

    /**
     * Return the current version of the protocol.
     * The returned value can be used in the JMQProtocolLevel property of
     * the HELLO message (101="1.0.1", 80="0.8.0", 218="2.1.8")
     *
     * We are currently at the 0.9.9 level of the protocol spec
     *
     * @return int  protocol version
     */
    public static int getProtocolVersion() {
    	return VERSION500;
    }

    // Protocol version used in iMQ2.0 (Swift and Hummingbird)
    public static final int VERSION1 = 100;

    // Protocol vesion used in Falcon
    public static final int VERSION2 = 200;

    // Protocol vesion used in Raptor
    public static final int VERSION350 = 350;

    // Protocol vesion used in Shrike
    public static final int VERSION360 = 360;

    // Protocol vesion used in Shrike sp4
    public static final int VERSION364 = 364;

    // Protocol vesion used in Hawk
    public static final int VERSION400 = 400;
    
    //protocol version used in Harrier
    public static final int VERSION410 = 410;

    //protocol version used in 4.5 
    public static final int VERSION450 = 450;

    //protocol version used in 5.0 
    public static final int VERSION500 = 500;
}
