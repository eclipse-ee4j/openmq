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

package com.sun.messaging.bridge.service.jms.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants
 * to use as message keys. The reason we use constants for the message
 * keys is to provide some compile time checking when the key is used
 * in the source.
 */

public class JMSBridgeResources extends MQResourceBundle {

    private static JMSBridgeResources resources = null;

    public static JMSBridgeResources getResources() {
        return getResources(null);
    }

    public static synchronized JMSBridgeResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

	    if (resources == null || !locale.equals(resources.getLocale())) { 
	        ResourceBundle b = ResourceBundle.getBundle(
            "com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources", locale);
            resources = new JMSBridgeResources(b);
	    }
	    return resources;
    }

    private JMSBridgeResources(ResourceBundle rb) {
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

    /**
     * commneted out M_WITH_CLIENTID=BSJ0000, no longer used 
     */
    final public static String M_SOURCE = "BSJ0001";
    final public static String M_TARGET = "BSJ0002";
    final public static String M_SOURCE_1 = "BSJ0003";
    final public static String M_TARGET_1 = "BSJ0004";
    final public static String M_TRANSACTED = "BSJ0005";
    final public static String M_NONTRANSACTED = "BSJ0006";
    final public static String M_RESUMING_LINK = "BSJ0007";
    final public static String M_STARTING_SOURCE_CONN = "BSJ0008";
    final public static String M_RESUME = "BSJ0009";
    final public static String M_START = "BSJ0010";
    /**
     * commented out M_WITH_USERNAME = "BSJ0011", no longer used
     */
    final public static String M_BRIDGE = "BSJ0012";
    final public static String M_LINK = "BSJ0013";
    final public static String M_PAUSE = "BSJ0014";
    final public static String M_STOP = "BSJ0015";

    // 1000-1999 Informational Messages
    final public static String I_DMQ_ALREADY_STARTED = "BSJ1000";
    final public static String I_CREATE_DEDICATED_CONN = "BSJ1001";
    final public static String I_LOG_DOMAIN = "BSJ1002";
    final public static String I_LOG_FILE = "BSJ1003";
    final public static String I_INIT_JMSBRIDGE_WITH = "BSJ1004";
    final public static String I_CF_NOT_XA_NO_REGISTER = "BSJ1005";
    final public static String I_CREATE_LINK_FOR_JMSBRIDGE = "BSJ1006";
    final public static String I_CREATE_DMQ_FOR_JMSBRIDGE = "BSJ1007";
    final public static String I_CREATE_BUILTIN_DMQ = "BSJ1008";
    final public static String I_JNDI_LOOKUP_CF = "BSJ1009";
    final public static String I_JNDI_LOOKUP_DEST = "BSJ1010";
    final public static String I_CREATE_POOLED_CF = "BSJ1011";
    final public static String I_CREATE_SHARED_CF = "BSJ1012";
    final public static String I_GET_POOLED_CONN = "BSJ1013";
    final public static String I_GET_SHARED_CONN = "BSJ1014";
    final public static String I_RETURN_POOLED_CONN = "BSJ1015";
    final public static String I_RETURN_SHARED_CONN = "BSJ1016";
    final public static String I_SEND_MSG_TO_DMQ = "BSJ1017";
    final public static String I_SENT_MSG_TO_DMQ = "BSJ1018";
    final public static String I_CREATING_XA_CONN = "BSJ1019";
    final public static String I_CREATING_CONN = "BSJ1020";
    final public static String I_USE_TM_ADAPTER_CLASS = "BSJ1021";
    final public static String I_INIT_TM_WITH_PROPS = "BSJ1022";
    final public static String I_SET_TM_TIMEOUT = "BSJ1023";
    final public static String I_SKIP_REGISTER_MULTIRM_CF = "BSJ1024";
    final public static String I_REGISTER_RM = "BSJ1025";
    final public static String I_LINK_USE_TM = "BSJ1026";
    final public static String I_REGISTER_RMS_FOR = "BSJ1027";
    final public static String I_USE_XARESOURCE_WRAP_TARGET = "BSJ1028";
    final public static String I_REGISTERED_RMS_FOR = "BSJ1029";
    final public static String I_ALREADY_STARTED = "BSJ1030";
    final public static String I_ALREADY_PAUSED = "BSJ1031";
    final public static String I_ALREADY_RUNNING = "BSJ1032";
    final public static String I_IGNORE_START_SOURCE_REQUEST = "BSJ1033";
    final public static String I_STOPPING_LINK = "BSJ1034";
    final public static String I_CREATE_DEDICATED_SOURCE_CONN = "BSJ1035";
    final public static String I_CREATE_DEDICATED_TARGET_CONN = "BSJ1036";
    final public static String I_GET_TARGET_CONN = "BSJ1037";
    final public static String I_DEFER_GET_TARGET_CONN = "BSJ1038";
    final public static String I_RUNNING_XA_CONSUMER = "BSJ1039";
    final public static String I_RUNNING_NONTXN_CONSUMER = "BSJ1040";
    final public static String I_LINK_THREAD_EXIT = "BSJ1041";
    final public static String I_TXN_MESSAGE_EXPIRED = "BSJ1042";
    final public static String I_NONTXN_MESSAGE_EXPIRED = "BSJ1043";
    final public static String I_SCHEDULE_TIMEOUT_FOR_POOLCF = "BSJ1044";
    final public static String I_CLOSE_INVALID_CONN_IN_POOLCF = "BSJ1045";
    final public static String I_CLOSE_POOLCF = "BSJ1046";
    final public static String I_CLOSE_TIMEOUT_CONN_IN_POOLCF = "BSJ1047";
    final public static String I_CLOSE_INVALID_CONN_IN_SHAREDCF = "BSJ1048";
    final public static String I_SCHEDULE_TIMEOUT_FOR_SHAREDCF = "BSJ1049";
    final public static String I_CLOSE_TIMEOUT_CONN_IN_SHAREDCF = "BSJ1050";
    final public static String I_CLOSE_SHAREDCF = "BSJ1051";
    final public static String I_ALREADY_ROLLEDBACK = "BSJ1052";
    final public static String I_ALREADY_COMMITTED = "BSJ1053";
    final public static String I_SET_PROP_TM = "BSJ1054";
    final public static String I_TM_START_WITH = "BSJ1055";
    final public static String I_TM_ALREADY_SHUTDOWN = "BSJ1056";
    final public static String I_TM_CLEANUP_RECOVERED_GTXN = "BSJ1057";
    final public static String I_FILETXNLOG_SET_PROP = "BSJ1058";
    final public static String I_FILETXNLOG_INIT_WITH_RESET = "BSJ1059";
    final public static String I_FILETXNLOG_INIT = "BSJ1060";
    final public static String I_FILETXNLOG_LOADED = "BSJ1061";
    final public static String I_FILETXNLOG_CLOSE = "BSJ1062";
    final public static String I_JDBCTXNLOG_CLOSE = "BSJ1063";
    final public static String I_INITED_LINK_WITH = "BSJ1064";
    final public static String I_INSTRUCTED_NO_TRANSFER_AND_CONSUME = "BSJ1065";
    final public static String I_TRANSFER_TO_GETJMSDESTINATION = "BSJ1066";
    final public static String I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION = "BSJ1067";
    /**
     * removed I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION_DIFF = "BSJ1068";
     * from here and JMSBridgeResources.properties, not used 
     */
    final public static String I_TRANSFORMER_BRANCHTO = "BSJ1069";
    final public static String I_UPDATE_RECOVER_INFO_GXIDS = "BSJ1070";
    final public static String I_UPDATE_RECOVER_INFO_FOR = "BSJ1071";
    final public static String I_MESSAGE_TRANSFER_SUCCESS = "BSJ1072";
    final public static String I_CLOSE_SOURCE_CONNECTION = "BSJ1073";
    final public static String I_CLOSE_TARGET_CONNECTION = "BSJ1074";
    final public static String I_CLOSE_DMQ_CONNECTION = "BSJ1075";
    final public static String I_START_ASYNC = "BSJ1076";
    final public static String I_WAITING_LINK_THREAD_EXIT = "BSJ1077";

    // 2000-2999 Warning Messages
    final public static String W_UNABLE_STOP_DMQ_AFTER_FAILED_START = "BSJ2000";
    final public static String W_ON_CONN_EXCEPTION = "BSJ2001";
    final public static String W_UNABLE_RETURN_CONN = "BSJ2002";
    final public static String W_UNABLE_CLOSE_CONN = "BSJ2003";
    final public static String W_EXCEPTION_DMQ_MSG = "BSJ2004";
    final public static String W_EXCEPTION_TRUNCATE_DMQ_MSG = "BSJ2005";
    final public static String W_STOP_BRIDGE_FAILED_AFTER_START_DMQ_FAILURE = "BSJ2006";
    final public static String W_STOP_BRIDGE_FAILED_AFTER_START_LINK_FAILURE = "BSJ2007";
    final public static String W_STOP_BRIDGE_FAILED_AFTER_POSTSTART_LINK_FAILURE = "BSJ2008";
    final public static String W_FAILED_GET_ALL_TXNS = "BSJ2009";
    final public static String W_FAILED_CLOSE_CF = "BSJ2010";
    final public static String W_EXCEPTION_SHUTDOWN_TM = "BSJ2011";
    final public static String W_SEND_MSG_TO_DMQ_FAILED = "BSJ2012";
    final public static String W_EXCEPTION_CREATING_CONN = "BSJ2013";
    final public static String W_REGISTER_RM_ATTEMPT_FAILED = "BSJ2014";
    final public static String W_CONN_EXCEPTION_OCCURRED = "BSJ2015";
    final public static String W_REGISTER_SOURCE_XARESOURCE_FAILED = "BSJ2016";
    final public static String W_REGISTER_TARGET_XARESOURCE_FAILED = "BSJ2017";
    final public static String W_FORCE_CLOSE_POOLCF = "BSJ2018";
    final public static String W_STOP_LINK_BECAUSE_OF = "BSJ2019";
    final public static String W_CONSUME_NO_TRANSFER = "BSJ2020";
    final public static String W_SOURCE_CONN_CLOSED = "BSJ2021";
    final public static String W_SOURCE_CONN_CLOSED_OR_RECEIVE_TIMEOUT = "BSJ2022";
    final public static String W_ASYNC_CMD_IN_PROCESS = "BSJ2023";
    final public static String W_ASYNC_CMD_CANCELED = "BSJ2024";

    // 3000-3999 Error Messages
    final public static String E_UNABLE_START_DMQ = "BSJ3000";
    final public static String E_FAIL_SEND_ATTEMPTS = "BSJ3001";
    final public static String E_EXCEPTION_START_DMQ = "BSJ3002";
    final public static String E_EXCEPTION_START_LINK = "BSJ3003";
    final public static String E_EXCEPTION_POSTSTART_LINK = "BSJ3004";
    final public static String E_EXCEPTION_PAUSE_LINK = "BSJ3005";
    final public static String E_EXCEPTION_RESUME_LINK = "BSJ3006";
    final public static String E_EXCEPTION_STOP_LINK = "BSJ3007";
    final public static String E_EXCEPTION_STOP_DMQ = "BSJ3008";
    final public static String E_EXCEPTION_CREATE_CF = "BSJ3009";
    final public static String E_UNABLE_START = "BSJ3010";
    final public static String E_UNABLE_STOP_AFTER_START_FAILURE = "BSJ3011";
    final public static String E_UNABLE_STOP_AFTER_POSTSTART_FAILURE = "BSJ3012";
    final public static String E_UNABLE_PAUSE_SOURCE_CONN = "BSJ3013";
    final public static String E_UNABLE_STOP_AFTER_PAUSE_FAILURE = "BSJ3014";
    final public static String E_UNABLE_RESUME_SOURCE_CONN = "BSJ3015";
    final public static String E_UNABLE_STOP_LINK_AFTER = "BSJ3016";
    final public static String E_UNABLE_START_SOURCE_CONN = "BSJ3017";

    // 4000-4999 Exception Messages
    final public static String X_LINKOP_ALLOWED_STATE = "BSJ4000";
    final public static String X_PAUSE_NOT_ALLOWED_STATE = "BSJ4001";
    final public static String X_RESUME_NOT_ALLOWED_STATE = "BSJ4002";
    final public static String X_BRIDGE_NOT_INITED = "BSJ4003";
    final public static String X_DMQ_NOT_SUPPORT = "BSJ4004";
    final public static String X_DMQ_NOT_INITED = "BSJ4005";
    final public static String X_STOPPED = "BSJ4006";
    final public static String X_EXCEPTION_SET_DMQ_PROPERTY = "BSJ4007";
    final public static String X_NOT_SPECIFIED = "BSJ4008";
    final public static String X_NOT_EXIST = "BSJ4009";
    final public static String X_JMSBRIDGE_NAME_MISMATCH = "BSJ4010";
    final public static String X_LINK_ALREADY_EXIST = "BSJ4011";
    final public static String X_LINK_FOREIGNERS_NO_SUPPORT = "BSJ4012";
    final public static String X_SOURCE_NONXA_TARGET_XA = "BSJ4013";
    final public static String X_SOURCE_XA_TARGET_NONXA = "BSJ4014";
    final public static String X_DMQ_ALREADY_EXIST = "BSJ4015";
    final public static String X_DMQ_XACF_NOT_SUPPORT = "BSJ4016";
    final public static String X_CF_TYPE_LOOKUP_MISMATCH = "BSJ4017";
    final public static String X_DEST_NO_NAME_NO_LOOKUP = "BSJ4018";
    final public static String X_LINK_NOT_FOUND = "BSJ4019";
    final public static String X_LOOKUP_RETURN_NULL = "BSJ4020";
    final public static String X_SOURCE_TARGET_NO_INFO = "BSJ4021";
    final public static String X_REQUIRED_FOR_LINK = "BSJ4022";
    final public static String X_HAS_STATE = "BSJ4023";
    final public static String X_PAUSE_NOT_ALLOWED_IN_STATE = "BSJ4024";
    final public static String X_NOT_ALLOWED_IN_STATE = "BSJ4025";
    final public static String X_LINK_IS_STOPPED = "BSJ4026";
    final public static String X_POOLED_CF_CLOSED = "BSJ4027";
    final public static String X_SHARED_CF_CLOSED = "BSJ4028";
    final public static String X_XML_IS_RESERVED = "BSJ4029";
    final public static String X_XML_NO_LOOKUP_NO_NAME_ELEMENT = "BSJ4030";
    final public static String X_XML_ATTR_NOT_SPECIFIED = "BSJ4031";
    final public static String X_XML_ELEMENT_ALREADY_EXIST = "BSJ4032";
    final public static String X_XML_ELEMENT_DONOT_EXIST = "BSJ4033";
    final public static String X_NULL_RETURN_FROM_FOR_MESSAGE = "BSJ4034";
    final public static String X_XML_INVALID_USERNAME_FOR_CF = "BSJ4035";
    final public static String X_XML_NAME_NOT_SPECIFIED_FOR = "BSJ4036";
    final public static String X_OPENCONNECTION_INTERRUPTED = "BSJ4037";
    final public static String X_DMQ_SENDRETRY_INTERRUPTED = "BSJ4038";
    final public static String X_LINK_INTERRUPTED = "BSJ4039";

    /***************** End of message key constants *******************/
}
