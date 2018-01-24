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

package com.sun.messaging.bridge.service.jms.xml;

import java.util.List;
import java.util.Arrays;

/**
 * @author amyk
 */
public class JMSBridgeXMLConstant
{
    public enum Common {
        ;
        public static final String NAME = "name";
        public static final String VALUE = "vaule";
        public static final String REFNAME = "ref-name";
    };

    public enum JMSBRIDGE {
        ;
        public static final String TAG_BRIDGENAME = "message-transfer-tag-bridge-name";
        public static final String LOG_MESSAGE_TRANSFER = "log-message-transfer";

        public static final String TAG_BRIDGENAME_DEFAULT = "false";
        public static final String LOG_MESSAGE_TRANSFER_DEFAULT = "true";

    };

    public enum CF {
        ;
        public static final String CFREF = "connection-factory-ref";
        public static final String REFNAME = "ref-name";

        public static final String LOOKUPNAME = "lookup-name";
        public static final String MULTIRM = "multi-rm";
        public static final String CLIENTID = "clientid";
        public static final String CONNECTATTEMPTS  = "connect-attempts";
        public static final String CONNECTATTEMPTINTERVAL = "connect-attempt-interval-in-seconds";
        public static final String IDLETIMEOUT = "idle-timeout-in-seconds";
        public static final String USERNAME = "username";
        public static final String PASSWORD = "password";

        public static final String MULTIRM_DEFAULT = "false";
        public static final String CONNECTATTEMPTS_DEFAULT  = "-1";
        public static final String CONNECTATTEMPTINTERVAL_DEFAULT = "5";
        public static final String IDLETIMEOUT_DEFAULT = "1800";
    };

    public enum Link {
        ;
        public static final String NAME = "name";
        public static final String ENABLED = "enabled";
        public static final String TRANSACTED = "transacted";

        public static final String ENABLED_DEFAULT = "true";
        public static final String TRANSACTED_DEFAULT = "true";
    };

    public enum Source {
        ;
        public static final String CFREF = "connection-factory-ref";
        public static final String DESTINATIONREF = "destination-ref";

        // properties
        public static final String SELECTOR = "selector";
        public static final String DURABLESUB = "durable-sub";
        public static final String CLIENTID = "clientid";

    };

    public enum Target {
        ;
        public static final String CFREF = "connection-factory-ref";
        public static final String DESTINATIONREF = "destination-ref";

        public static final String STAYCONNECTED = "stay-connected";
        public static final String RETAINREPLYTO = "retain-replyto";
        public static final String MTFCLASS = "message-transformer-class";
        public static final String CONSUMEONTRANSFORMERROR = "consume-no-transfer-on-transform-error";
        public static final String CLIENTID = "clientid";

        public static final String STAYCONNECTED_DEFAULT = "true";
        public static final String RETAINREPLYTO_DEFAULT = "false";
        public static final String CONSUMEONTRANSFORMERROR_DEFAULT = "false";
        public static final String DESTINATIONREF_AS_SOURCE = "AS_SOURCE";
    };

    public enum Destination {
        ;
        public static final String TYPE = "type";
        public static final String NAME = "name";
        public static final String LOOKUPNAME = "lookup-name";
        public static final String REFNAME = "ref-name";

        // constants
        public static final String QUEUE = "queue";
        public static final String TOPIC = "topic";
    };

    public enum DMQ {
        ;
        public static final String CFREF = "connection-factory-ref";
        public static final String DESTINATIONREF = "destination-ref";
        public static final String TIMETOLIVE = "time-to-live-in-millis";
        public static final String STAYCONNECTED = "stay-connected";
        public static final String CLIENTID = "clientid";
        public static final String ENABLED = "enabled";
        public static final String MTFCLASS = "message-transformer-class";
        public static final String SENDATTEMPTS = "send-attempts";
        public static final String SENDATTEMPTINTERVAL = "send-attempt-interval-in-seconds";

        public static final String STAYCONNECTED_DEFAULT = "true";
        public static final String TIMETOLIVE_DEFAULT = "0";
        public static final String ENABLED_DEFAULT = "true";
        public static final String SENDATTEMPTS_DEFAULT = "3";
        public static final String SENDATTEMPTINTERVAL_DEFAULT = "5";
    }

    public enum Element {
        ;
        public static final String JMSBRIDGE = "jmsbridge";
        public static final String LINK = "link";
        public static final String SOURCE = "source";
        public static final String TARGET = "target";
        public static final String DMQ = "dmq";
        public static final String DESTINATION = "destination";
        public static final String CF = "connection-factory";
        public static final String PROPERTY = "property";
        public static final String DESCRIPTION = "description";
    };

    private static List<String> _reservedNames = Arrays.asList(
                                DMQElement.BUILTIN_DMQ_NAME, 
                                DMQElement.BUILTIN_DMQ_DESTNAME, Target.DESTINATIONREF_AS_SOURCE);

    public static void checkReserved(String name)  throws IllegalArgumentException {
        if (name == null) return;

        if (_reservedNames.contains(name.trim()) ||
            _reservedNames.contains(name.trim().toUpperCase()) ||
            _reservedNames.contains(name.trim().toLowerCase())) {
            throw new IllegalArgumentException(name+" is reserved");
        }
    }
}
