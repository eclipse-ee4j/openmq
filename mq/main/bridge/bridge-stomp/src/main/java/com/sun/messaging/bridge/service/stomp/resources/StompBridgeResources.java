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
 */ 

package com.sun.messaging.bridge.service.stomp.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class StompBridgeResources extends MQResourceBundle {

    private static StompBridgeResources resources = null;

    public static StompBridgeResources getResources() {
        return getResources(null);
    }

    public static synchronized StompBridgeResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

	    if (resources == null || !locale.equals(resources.getLocale())) { 
	        ResourceBundle b = ResourceBundle.getBundle(
            "com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources", locale);
            resources = new StompBridgeResources(b);
	    }
	    return resources;
    }

    private StompBridgeResources(ResourceBundle rb) {
        super(rb);
    }


    /***************** Start of message key constants *******************
     * We use numeric values as the keys because the MQ has a requirement
     * that each error message have an associated error code (for 
     * documentation purposes). We use numeric Strings instead of primitive
     * integers because that is what ListResourceBundles support. We could
     * write our own ResourceBundle to support integer keys, but since
     * we'd just be converting them back to strings (to display them)
     * it's unclear if that would be a big win. Also the performance of
     * ListResourceBundles under Java 2 is pretty good.
     * 
     *
     * Note To Translators: Do not copy these message key String constants
     * into the locale specific resource bundles. They are only required
     * in this default resource bundle.
     */

    // 0-999     Miscellaneous messages

    // 1000-1999 Informational Messages
    final public static String I_CREATE_JMS_CONN = "BSS1000";
    final public static String I_CREATE_JMS_CONN_WITH_CLIENTID = "BSS1001";
    final public static String I_STARTED_JMS_CONN = "BSS1002";
    final public static String I_STOMP_CONN_CLOSED = "BSS1003";
    final public static String I_STOMP_CONN_NOT_CONNECTED = "BSS1004";
    final public static String I_CURRENT_TXN = "BSS1005";
    final public static String I_TXN_ALREADY_ROLLEDBACK = "BSS1006";
    final public static String I_SENT_MSG_CANCEL_SELECTIONKEY = "BSS1007";
    final public static String I_CLOSE_STOMP_CONN = "BSS1008";
    final public static String I_PASS_HEADER_TO_TRANSFORMER = "BSS1009";
    final public static String I_LOG_DOMAIN = "BSS1010";
    final public static String I_LOG_FILE = "BSS1011";
    final public static String I_INIT_SSL = "BSS1012";
    final public static String I_START_TRANSPORT = "BSS1013";
    final public static String I_START_TRANSPORT_OK = "BSS1014";
    final public static String I_STOP_STOMP_SERVER = "BSS1015";
    final public static String I_STOMP_SERVER_STOPPED = "BSS1016";
    final public static String I_SELECTION_KEY_LOCAL_CLOSED = "BSS1017";
    final public static String I_SELECTION_KEY_REMOTE_CLOSED = "BSS1018";
    final public static String I_CLOSE_STOMP_HANDLER = "BSS1019";
    final public static String I_CREATED_TXN_SESSION = "BSS1020";
    final public static String I_CREATED_TXN_SUB = "BSS1021";
    final public static String I_WAITING_TXNSESSION_THREAD_STOP = "BSS1022";
    final public static String I_TXNSESSION_THREAD_EXIT = "BSS1023";
    final public static String I_INIT_GRIZZLY = "BSS1024";
    final public static String I_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR = "BSS1025";

    // 2000-2999 Warning Messages
    final public static String W_SEND_MSG_TO_CLIENT_FAILED = "BSS2000";
    final public static String W_IGNORE_START_OPTION = "BSS2001";
    final public static String W_EXCEPTION_ON_SEND_MSG = "BSS2002";
    final public static String W_CLOSE_STOMP_CONN_FAILED = "BSS2003";
    final public static String W_SET_JMS_PROPERTY_FAILED = "BSS2004";
    final public static String W_NO_SUBID_TXNACK = "BSS2005";
    final public static String W_NO_SUBID_NONTXNACK = "BSS2006";
    final public static String W_WAIT_FOR_START_INTERRUPTED = "BSS2007";
    final public static String W_EXCEPTION_STOP_SERVER = "BSS2008";
    final public static String W_UNABLE_DELIVER_MSG_TO_SUB = "BSS2009";
    final public static String W_UNABLE_ACK_MSG_ON_CLOSE_SUB = "BSS2010";
    final public static String W_TXNACK_MSG_ON_ROLLBACK_FAIL = "BSS2011";
    final public static String W_TXNACK_DELIVERED_MSG_ON_ROLLBACK_FAIL = "BSS2012";
    final public static String W_TXNACK_UNDELIVERED_MSG_ON_ROLLBACK_FAIL = "BSS2013";
    final public static String W_TXNSESSION_ROLLBACK_FAIL = "BSS2014";
    final public static String W_UNABLE_DELIVER_MSG_TO_TXNSUB = "BSS2015";
    final public static String W_PROPERTY_SETTING_OVERRIDE_BY_BROKER = "BSS2016";

    // 3000-3999 Error Messages
    final public static String E_ONEXCEPTION_JMS_CONN = "BSS3000";
    final public static String E_UNABLE_SEND_ERROR_MSG = "BSS3001";
    final public static String E_COMMAND_FAILED = "BSS3002";
    final public static String E_PARSE_INCOMING_DATA_FAILED = "BSS3003";
    final public static String E_UNABLE_CREATE_ERROR_MSG = "BSS3004";
    final public static String E_START_TRANSPORT_FAILED = "BSS3005";
    final public static String E_ONEXCEPTION_TRANSPORT = "BSS3006";
    final public static String E_COMMIT_FAIL_WILL_ROLLBACK = "BSS3007";

    // 4000-4999 Exception Messages
    final public static String X_OPERATION_NO_SUPPORT = "BSS4000";
    final public static String X_BRIDGE_NOT_INITED = "BSS4001";
    final public static String X_NESTED_TXN_NOT_ALLOWED = "BSS4002";
    final public static String X_TXN_NO_SESSION = "BSS4003";
    final public static String X_TXN_NOT_FOUND = "BSS4004";
    final public static String X_SUBSCRIBER_ID_EXIST = "BSS4005";
    final public static String X_UNSUBSCRIBE_NO_CLIENTID = "BSS4006";
    final public static String X_SUBSCRIBER_ID_NOT_FOUND = "BSS4007";
    final public static String X_ACK_CANNOT_DETERMINE_SUBSCRIBER = "BSS4008";
    final public static String X_NOT_CONNECTED = "BSS4009";
    final public static String X_CANNOT_PARSE_BODY_TO_TEXT = "BSS4010";
    final public static String X_HEADER_MISSING = "BSS4011";
    final public static String X_INVALID_HEADER_VALUE = "BSS4012";
    final public static String X_INVALID_HEADER = "BSS4013";
    final public static String X_MAX_HEADERS_EXCEEDED = "BSS4014";
    final public static String X_EXCEPTION_PARSE_HEADER = "BSS4015";
    final public static String X_NO_NULL_TERMINATOR = "BSS4016";
    final public static String X_MAX_LINELEN_EXCEEDED = "BSS4017";
    final public static String X_SUBID_ALREADY_EXISTS = "BSS4018";
    final public static String X_UNSUBSCRIBE_WITHOUT_HEADER = "BSS4019";
    final public static String X_HEADER_NOT_SPECIFIED_FOR = "BSS4020";
    final public static String X_SUBSCRIBE_NO_SESSION = "BSS4021";
    final public static String X_UNEXPECTED_PARSER_POSITION = "BSS4022";
    final public static String X_SESSION_CLOSED = "BSS4023";
    final public static String X_NO_PROTOCOL = "BSS4024";
    final public static String X_STOMP_SERVER_NO_INIT = "BSS4025";
    final public static String X_STOMP_SERVER_START_FAILED = "BSS4026";
    final public static String X_NOT_CLIENT_ACK_MODE = "BSS4027";
    final public static String X_ACK_MSG_NOT_FOUND_IN_SUB = "BSS4028";
    final public static String X_SUBID_ALREADY_EXIST_IN_TXN_SESSION = "BSS4029";
    final public static String X_TXNACK_NO_CURRENT_TRANSACTION = "BSS4030";
    final public static String X_SUBID_NOT_FOUND_IN_TXN = "BSS4031";
    final public static String X_ACK_CANNOT_DETERMINE_SUBSCRIBER_IN_TXN = "BSS4032";
    final public static String X_MSG_NOT_FOUND_IN_TXN = "BSS4033";
    final public static String X_TXN_SESSION_CLOSED = "BSS4034";
    final public static String X_UNEXPECTED_PARSER_POSITION_EXT = "BSS4035";
    final public static String X_INCOMPATIBLE_GRIZZLY_MAJOR_VERSION = "BSS4036";
    final public static String X_INCOMPATIBLE_GRIZZLY_MINOR_VERSION = "BSS4037";
    final public static String X_UNKNOWN_STOMP_CMD = "BSS4038";
    final public static String X_INVALID_MESSAGE_PROP_NAME = "BSS4039";
    final public static String X_PROTOCOL_VERSION_NO_SUPPORT = "BSS4040";

    /***************** End of message key constants *******************/
}
