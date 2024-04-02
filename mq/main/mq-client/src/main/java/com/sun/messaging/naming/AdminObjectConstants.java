/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.naming;

/**
 * This interface defines all constants used for admin objects management.
 */
public interface AdminObjectConstants {

    /**
     * The following format is used for the reference object representing Destination objects.
     *
     * [0] = reserved for version [1] = reserved for topicName
     *
     *
     * The following format is used for the reference object representing ConnectionFactory objects.
     *
     * [0] = reserved for version [1] = reserved for securityPort [2] = reserved for JMSXUserID [3] = reserved for JMSXAppID
     * [4] = reserved for JMSXProducerTXID [5] = reserved for JMSXConsumerTXID [6] = reserved for JMSXRcvTimestamp [7] =
     * reserved for -- [8] = reserved for host [9] = reserved for subnet [10] = reserved for ackTimeout
     *
     */

    /** used by both Destination and ConnectionFactory reference objects */
    String REF_VERSION = "version";

    /** used only by Destination reference objects */
    String REF_DESTNAME = "destName";

    /** used only by ConnectionFactory reference objects */
    String REF_SECURITYPORT = "securityPort";
    String REF_JMSXUSERID = "JMSXUserID";
    String REF_JMSXAPPID = "JMSXAppID";
    String REF_JMSXPRODUCERTXID = "JMSXProducerTXID";
    String REF_JMSXCONSUMERTXID = "JMSXConsumerTXID";
    String REF_JMSXRCVTIMESTAMP = "JMSXRcvTimestamp";
    String REF_PARM = "parm";
    String REF_HOST = "host";
    String REF_SUBNET = "subnet";
    String REF_ACKTIMEOUT = "ackTimeout";

    /** the content of the parm, if the configuration object exists */
    String REF_PARM_CONTENT = "--";

    /** JMSXxxx properties */
    String JMSXUSERID = "JMSXUserID";
    String JMSXAPPID = "JMSXAppID";
    String JMSXPRODUCERTXID = "JMSXProducerTXID";
    String JMSXCONSUMERTXID = "JMSXConsumerTXID";
    String JMSXRCVTIMESTAMP = "JMSXRcvTimestamp";

    /**
     * generic default value: if value is not specified in the reference object, its value defaults to this value
     */
    String DEFAULT = "default";

    /** the prefix to the attributes of the ConnectionFactyory objects */
    String PREF_HOST = "-s";
    String PREF_SUBNET = "-n";
    String PREF_ACKTIMEOUT = "-t";

    /** default values for attributes */
    String DEFAULT_HOST = "localhost";
    int DEFAULT_SUBNET = 0;
    int DEFAULT_SECURITYPORT = 22000;
    int DEFAULT_ACKTIMEOUT = 30000;
}
