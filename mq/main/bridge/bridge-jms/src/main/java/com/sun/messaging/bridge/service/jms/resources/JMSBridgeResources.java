/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.bridge.service.jms.resources;

import java.util.ResourceBundle;
import java.util.Locale;
import com.sun.messaging.jmq.util.MQResourceBundle;

/**
 * This class wraps a PropertyResourceBundle, and provides constants to use as message keys. The reason we use constants
 * for the message keys is to provide some compile time checking when the key is used in the source.
 */

public class JMSBridgeResources extends MQResourceBundle {

    private static JMSBridgeResources resources = null;

    public static synchronized JMSBridgeResources getResources(Locale locale) {

        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (resources == null || !locale.equals(resources.getLocale())) {
            ResourceBundle b = ResourceBundle.getBundle("com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources", locale);
            resources = new JMSBridgeResources(b);
        }
        return resources;
    }

    private JMSBridgeResources(ResourceBundle rb) {
        super(rb);
    }

    /*****************
     * Start of message key constants ******************* We use numeric values as the keys because the MQ has a requirement
     * that each error message have an associated error code (for documentation purposes). We use numeric Strings instead of
     * primitive integers because that is what ListResourceBundles support. We could write our own ResourceBundle to support
     * integer keys, but since we'd just be converting them back to strings (to display them) it's unclear if that would be
     * a big win. Also the performance of ListResourceBundles under Java 2 is pretty good.
     *
     *
     * Note To Translators: Do not copy these message key String constants into the locale specific resource bundles. They
     * are only required in this default resource bundle.
     */

    // 0-999 Miscellaneous messages

    /**
     * commneted out M_WITH_CLIENTID=BSJ0000, no longer used
     */
    public static final String M_SOURCE = "BSJ0001";
    public static final String M_TARGET = "BSJ0002";
    public static final String M_SOURCE_1 = "BSJ0003";
    public static final String M_TARGET_1 = "BSJ0004";
    public static final String M_TRANSACTED = "BSJ0005";
    public static final String M_NONTRANSACTED = "BSJ0006";
    public static final String M_RESUMING_LINK = "BSJ0007";
    public static final String M_STARTING_SOURCE_CONN = "BSJ0008";
    public static final String M_RESUME = "BSJ0009";
    public static final String M_START = "BSJ0010";
    /**
     * commented out M_WITH_USERNAME = "BSJ0011", no longer used
     */
    public static final String M_BRIDGE = "BSJ0012";
    public static final String M_LINK = "BSJ0013";
    public static final String M_PAUSE = "BSJ0014";
    public static final String M_STOP = "BSJ0015";

    // 1000-1999 Informational Messages
    public static final String I_DMQ_ALREADY_STARTED = "BSJ1000";
    public static final String I_CREATE_DEDICATED_CONN = "BSJ1001";
    public static final String I_LOG_DOMAIN = "BSJ1002";
    public static final String I_LOG_FILE = "BSJ1003";
    public static final String I_INIT_JMSBRIDGE_WITH = "BSJ1004";
    public static final String I_CF_NOT_XA_NO_REGISTER = "BSJ1005";
    public static final String I_CREATE_LINK_FOR_JMSBRIDGE = "BSJ1006";
    public static final String I_CREATE_DMQ_FOR_JMSBRIDGE = "BSJ1007";
    public static final String I_CREATE_BUILTIN_DMQ = "BSJ1008";
    public static final String I_JNDI_LOOKUP_CF = "BSJ1009";
    public static final String I_JNDI_LOOKUP_DEST = "BSJ1010";
    public static final String I_CREATE_POOLED_CF = "BSJ1011";
    public static final String I_CREATE_SHARED_CF = "BSJ1012";
    public static final String I_GET_POOLED_CONN = "BSJ1013";
    public static final String I_GET_SHARED_CONN = "BSJ1014";
    public static final String I_RETURN_POOLED_CONN = "BSJ1015";
    public static final String I_RETURN_SHARED_CONN = "BSJ1016";
    public static final String I_SEND_MSG_TO_DMQ = "BSJ1017";
    public static final String I_SENT_MSG_TO_DMQ = "BSJ1018";
    public static final String I_CREATING_XA_CONN = "BSJ1019";
    public static final String I_CREATING_CONN = "BSJ1020";
    public static final String I_USE_TM_ADAPTER_CLASS = "BSJ1021";
    public static final String I_INIT_TM_WITH_PROPS = "BSJ1022";
    public static final String I_SET_TM_TIMEOUT = "BSJ1023";
    public static final String I_SKIP_REGISTER_MULTIRM_CF = "BSJ1024";
    public static final String I_REGISTER_RM = "BSJ1025";
    public static final String I_LINK_USE_TM = "BSJ1026";
    public static final String I_REGISTER_RMS_FOR = "BSJ1027";
    public static final String I_USE_XARESOURCE_WRAP_TARGET = "BSJ1028";
    public static final String I_REGISTERED_RMS_FOR = "BSJ1029";
    public static final String I_ALREADY_STARTED = "BSJ1030";
    public static final String I_ALREADY_PAUSED = "BSJ1031";
    public static final String I_ALREADY_RUNNING = "BSJ1032";
    public static final String I_IGNORE_START_SOURCE_REQUEST = "BSJ1033";
    public static final String I_STOPPING_LINK = "BSJ1034";
    public static final String I_CREATE_DEDICATED_SOURCE_CONN = "BSJ1035";
    public static final String I_CREATE_DEDICATED_TARGET_CONN = "BSJ1036";
    public static final String I_GET_TARGET_CONN = "BSJ1037";
    public static final String I_DEFER_GET_TARGET_CONN = "BSJ1038";
    public static final String I_RUNNING_XA_CONSUMER = "BSJ1039";
    public static final String I_RUNNING_NONTXN_CONSUMER = "BSJ1040";
    public static final String I_LINK_THREAD_EXIT = "BSJ1041";
    public static final String I_TXN_MESSAGE_EXPIRED = "BSJ1042";
    public static final String I_NONTXN_MESSAGE_EXPIRED = "BSJ1043";
    public static final String I_SCHEDULE_TIMEOUT_FOR_POOLCF = "BSJ1044";
    public static final String I_CLOSE_INVALID_CONN_IN_POOLCF = "BSJ1045";
    public static final String I_CLOSE_POOLCF = "BSJ1046";
    public static final String I_CLOSE_TIMEOUT_CONN_IN_POOLCF = "BSJ1047";
    public static final String I_CLOSE_INVALID_CONN_IN_SHAREDCF = "BSJ1048";
    public static final String I_SCHEDULE_TIMEOUT_FOR_SHAREDCF = "BSJ1049";
    public static final String I_CLOSE_TIMEOUT_CONN_IN_SHAREDCF = "BSJ1050";
    public static final String I_CLOSE_SHAREDCF = "BSJ1051";
    public static final String I_ALREADY_ROLLEDBACK = "BSJ1052";
    public static final String I_ALREADY_COMMITTED = "BSJ1053";
    public static final String I_SET_PROP_TM = "BSJ1054";
    public static final String I_TM_START_WITH = "BSJ1055";
    public static final String I_TM_ALREADY_SHUTDOWN = "BSJ1056";
    public static final String I_TM_CLEANUP_RECOVERED_GTXN = "BSJ1057";
    public static final String I_FILETXNLOG_SET_PROP = "BSJ1058";
    public static final String I_FILETXNLOG_INIT_WITH_RESET = "BSJ1059";
    public static final String I_FILETXNLOG_INIT = "BSJ1060";
    public static final String I_FILETXNLOG_LOADED = "BSJ1061";
    public static final String I_FILETXNLOG_CLOSE = "BSJ1062";
    public static final String I_JDBCTXNLOG_CLOSE = "BSJ1063";
    public static final String I_INITED_LINK_WITH = "BSJ1064";
    public static final String I_INSTRUCTED_NO_TRANSFER_AND_CONSUME = "BSJ1065";
    public static final String I_TRANSFER_TO_GETJMSDESTINATION = "BSJ1066";
    public static final String I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION = "BSJ1067";
    /**
     * removed I_TRANSFORMER_TRANSFER_TO_GETJMSDESTINATION_DIFF = "BSJ1068"; from here and JMSBridgeResources.properties,
     * not used
     */
    public static final String I_TRANSFORMER_BRANCHTO = "BSJ1069";
    public static final String I_UPDATE_RECOVER_INFO_GXIDS = "BSJ1070";
    public static final String I_UPDATE_RECOVER_INFO_FOR = "BSJ1071";
    public static final String I_MESSAGE_TRANSFER_SUCCESS = "BSJ1072";
    public static final String I_CLOSE_SOURCE_CONNECTION = "BSJ1073";
    public static final String I_CLOSE_TARGET_CONNECTION = "BSJ1074";
    public static final String I_CLOSE_DMQ_CONNECTION = "BSJ1075";
    public static final String I_START_ASYNC = "BSJ1076";
    public static final String I_WAITING_LINK_THREAD_EXIT = "BSJ1077";

    // 2000-2999 Warning Messages
    public static final String W_UNABLE_STOP_DMQ_AFTER_FAILED_START = "BSJ2000";
    public static final String W_ON_CONN_EXCEPTION = "BSJ2001";
    public static final String W_UNABLE_RETURN_CONN = "BSJ2002";
    public static final String W_UNABLE_CLOSE_CONN = "BSJ2003";
    public static final String W_EXCEPTION_DMQ_MSG = "BSJ2004";
    public static final String W_EXCEPTION_TRUNCATE_DMQ_MSG = "BSJ2005";
    public static final String W_STOP_BRIDGE_FAILED_AFTER_START_DMQ_FAILURE = "BSJ2006";
    public static final String W_STOP_BRIDGE_FAILED_AFTER_START_LINK_FAILURE = "BSJ2007";
    public static final String W_STOP_BRIDGE_FAILED_AFTER_POSTSTART_LINK_FAILURE = "BSJ2008";
    public static final String W_FAILED_GET_ALL_TXNS = "BSJ2009";
    public static final String W_FAILED_CLOSE_CF = "BSJ2010";
    public static final String W_EXCEPTION_SHUTDOWN_TM = "BSJ2011";
    public static final String W_SEND_MSG_TO_DMQ_FAILED = "BSJ2012";
    public static final String W_EXCEPTION_CREATING_CONN = "BSJ2013";
    public static final String W_REGISTER_RM_ATTEMPT_FAILED = "BSJ2014";
    public static final String W_CONN_EXCEPTION_OCCURRED = "BSJ2015";
    public static final String W_REGISTER_SOURCE_XARESOURCE_FAILED = "BSJ2016";
    public static final String W_REGISTER_TARGET_XARESOURCE_FAILED = "BSJ2017";
    public static final String W_FORCE_CLOSE_POOLCF = "BSJ2018";
    public static final String W_STOP_LINK_BECAUSE_OF = "BSJ2019";
    public static final String W_CONSUME_NO_TRANSFER = "BSJ2020";
    public static final String W_SOURCE_CONN_CLOSED = "BSJ2021";
    public static final String W_SOURCE_CONN_CLOSED_OR_RECEIVE_TIMEOUT = "BSJ2022";
    public static final String W_ASYNC_CMD_IN_PROCESS = "BSJ2023";
    public static final String W_ASYNC_CMD_CANCELED = "BSJ2024";

    // 3000-3999 Error Messages
    public static final String E_UNABLE_START_DMQ = "BSJ3000";
    public static final String E_FAIL_SEND_ATTEMPTS = "BSJ3001";
    public static final String E_EXCEPTION_START_DMQ = "BSJ3002";
    public static final String E_EXCEPTION_START_LINK = "BSJ3003";
    public static final String E_EXCEPTION_POSTSTART_LINK = "BSJ3004";
    public static final String E_EXCEPTION_PAUSE_LINK = "BSJ3005";
    public static final String E_EXCEPTION_RESUME_LINK = "BSJ3006";
    public static final String E_EXCEPTION_STOP_LINK = "BSJ3007";
    public static final String E_EXCEPTION_STOP_DMQ = "BSJ3008";
    public static final String E_EXCEPTION_CREATE_CF = "BSJ3009";
    public static final String E_UNABLE_START = "BSJ3010";
    public static final String E_UNABLE_STOP_AFTER_START_FAILURE = "BSJ3011";
    public static final String E_UNABLE_STOP_AFTER_POSTSTART_FAILURE = "BSJ3012";
    public static final String E_UNABLE_PAUSE_SOURCE_CONN = "BSJ3013";
    public static final String E_UNABLE_STOP_AFTER_PAUSE_FAILURE = "BSJ3014";
    public static final String E_UNABLE_RESUME_SOURCE_CONN = "BSJ3015";
    public static final String E_UNABLE_STOP_LINK_AFTER = "BSJ3016";
    public static final String E_UNABLE_START_SOURCE_CONN = "BSJ3017";

    // 4000-4999 Exception Messages
    public static final String X_LINKOP_ALLOWED_STATE = "BSJ4000";
    public static final String X_PAUSE_NOT_ALLOWED_STATE = "BSJ4001";
    public static final String X_RESUME_NOT_ALLOWED_STATE = "BSJ4002";
    public static final String X_BRIDGE_NOT_INITED = "BSJ4003";
    public static final String X_DMQ_NOT_SUPPORT = "BSJ4004";
    public static final String X_DMQ_NOT_INITED = "BSJ4005";
    public static final String X_STOPPED = "BSJ4006";
    public static final String X_EXCEPTION_SET_DMQ_PROPERTY = "BSJ4007";
    public static final String X_NOT_SPECIFIED = "BSJ4008";
    public static final String X_NOT_EXIST = "BSJ4009";
    public static final String X_JMSBRIDGE_NAME_MISMATCH = "BSJ4010";
    public static final String X_LINK_ALREADY_EXIST = "BSJ4011";
    public static final String X_LINK_FOREIGNERS_NO_SUPPORT = "BSJ4012";
    public static final String X_SOURCE_NONXA_TARGET_XA = "BSJ4013";
    public static final String X_SOURCE_XA_TARGET_NONXA = "BSJ4014";
    public static final String X_DMQ_ALREADY_EXIST = "BSJ4015";
    public static final String X_DMQ_XACF_NOT_SUPPORT = "BSJ4016";
    public static final String X_CF_TYPE_LOOKUP_MISMATCH = "BSJ4017";
    public static final String X_DEST_NO_NAME_NO_LOOKUP = "BSJ4018";
    public static final String X_LINK_NOT_FOUND = "BSJ4019";
    public static final String X_LOOKUP_RETURN_NULL = "BSJ4020";
    public static final String X_SOURCE_TARGET_NO_INFO = "BSJ4021";
    public static final String X_REQUIRED_FOR_LINK = "BSJ4022";
    public static final String X_HAS_STATE = "BSJ4023";
    public static final String X_PAUSE_NOT_ALLOWED_IN_STATE = "BSJ4024";
    public static final String X_NOT_ALLOWED_IN_STATE = "BSJ4025";
    public static final String X_LINK_IS_STOPPED = "BSJ4026";
    public static final String X_POOLED_CF_CLOSED = "BSJ4027";
    public static final String X_SHARED_CF_CLOSED = "BSJ4028";
    public static final String X_XML_IS_RESERVED = "BSJ4029";
    public static final String X_XML_NO_LOOKUP_NO_NAME_ELEMENT = "BSJ4030";
    public static final String X_XML_ATTR_NOT_SPECIFIED = "BSJ4031";
    public static final String X_XML_ELEMENT_ALREADY_EXIST = "BSJ4032";
    public static final String X_XML_ELEMENT_DONOT_EXIST = "BSJ4033";
    public static final String X_NULL_RETURN_FROM_FOR_MESSAGE = "BSJ4034";
    public static final String X_XML_INVALID_USERNAME_FOR_CF = "BSJ4035";
    public static final String X_XML_NAME_NOT_SPECIFIED_FOR = "BSJ4036";
    public static final String X_OPENCONNECTION_INTERRUPTED = "BSJ4037";
    public static final String X_DMQ_SENDRETRY_INTERRUPTED = "BSJ4038";
    public static final String X_LINK_INTERRUPTED = "BSJ4039";

    /***************** End of message key constants *******************/
}
