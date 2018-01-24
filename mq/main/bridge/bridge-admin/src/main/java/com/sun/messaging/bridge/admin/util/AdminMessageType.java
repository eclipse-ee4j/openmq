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

package com.sun.messaging.bridge.admin.util;

import com.sun.messaging.jmq.util.admin.MessageType;
/**
 * This class describes MQ bridge admin protocol messages 
 */
public class AdminMessageType {

    public static final String JMQ_BRIDGE_ADMIN_DEST   = MessageType.JMQ_BRIDGE_ADMIN_DEST;

    public static enum PropName {   
        ;
        public static final String MESSAGE_TYPE    = MessageType.JMQ_MESSAGE_TYPE;  //Integer
        public static final String PROTOCOL_LEVEL  = MessageType.JMQ_PROTOCOL_LEVEL; //String
        public static final String INSTANCE_NAME   = MessageType.JMQ_INSTANCE_NAME; //String
        public static final String STATUS          = MessageType.JMQ_STATUS;         //Integer
        public static final String ERROR_STRING    = MessageType.JMQ_ERROR_STRING;   //String
       

        public static final String BRIDGE_NAME     = "JMQBridgeName";    //String
        public static final String BRIDGE_TYPE     = "JMQBridgeType";    //String
        public static final String LINK_NAME       = "JMQLinkName";      //String

        public static final String CMD_ARG         = "JMQCommandArg";    //String
        public static final String TARGET          = "JMQTarget";        //String

        public static final String LOCALE_LANG      = "JMQLocaleLanguage";  //String
        public static final String LOCALE_COUNTRY   = "JMQLocaleCountry";   //String
        public static final String LOCALE_VARIANT    = "JMQLocaleVariant";   //String

        //debug mode
        public static final String DEBUG    = "JMQDebug";   //Boolean
   
        public static final String ASYNC_STARTED    = "JMQAsyncStarted";   //Boolean

    }

    public static enum Type {
        ;
        public static final int NULL              = 0;

        public static final int DEBUG             = 16;
        public static final int DEBUG_REPLY       = 17;

        public static final int LIST              = 18;
        public static final int LIST_REPLY        = 19;

        public static final int PAUSE             = 20;
        public static final int PAUSE_REPLY       = 21;

        public static final int RESUME            = 22;
        public static final int RESUME_REPLY      = 23;

        public static final int START             = 24;
        public static final int START_REPLY       = 25;

        public static final int STOP              = 26;
        public static final int STOP_REPLY        = 27;

        public static final int HELLO             = MessageType.HELLO;      //28
        public static final int HELLO_REPLY       = MessageType.HELLO_REPLY; //29


        public static final int LAST              = 30;
    }

    private static final String[] names = {
        "NULL",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "TBD",
        "DEBUG",
        "DEBUG_REPLY",
        "LIST",
        "LIST_REPLY",
        "PAUSE",
        "PAUSE_REPLY",
        "RESUME",
        "RESUME_REPLY",
        "START",
        "START_REPLY",
        "STOP",
        "STOP_REPLY",
        "HELLO",
        "HELLO_REPLY",
    	"LAST"
    };

    /**
     */
    public static String getString(int type) {
        if (type < 0 || type >= Type.LAST) {
            return "INVALID_TYPE("+type+")";
        }
        return names[type] + "(" + type + ")";
    }

    /**
     *
     * The returned value can be used in the JMQProtocolLevel property of
     * the HELLO message 
     *
     * @return bridge admin protocol version
     */
    public static int getProtocolVersion() {
        return 440;
    }
}
