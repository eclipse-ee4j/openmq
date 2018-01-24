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
 * @(#)TransactionInfo.java	1.3 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * This class contains constants/names for fields in the CompositeData
 * that is returned by the operations of the Transaction Manager Monitor 
 * MBean.
 */
public class TransactionInfo implements java.io.Serializable  {

    /** 
     * Client ID
     */
    public static final String		CLIENT_ID = "ClientID";

    /** 
     * Object name of Connection
     */
    public static final String		CONNECTION_STRING = "ConnectionString";

    /** 
     * Creation time
     */
    public static final String		CREATION_TIME = "CreationTime";

    /** 
     * Number of acknowledgements
     */
    public static final String		NUM_ACKS = "NumAcks";

    /** 
     * Number of messages
     */
    public static final String		NUM_MSGS = "NumMsgs";

    /** 
     * Transaction state.
     */
    public static final String		STATE = "State";

    /** 
     * String representation of transaction state.
     */
    public static final String		STATE_LABEL = "StateLabel";

    /** 
     * Transaction ID
     */
    public static final String		TRANSACTION_ID = "TransactionID";

    /** 
     * User name
     */
    public static final String		USER = "User";

    /** 
     * XID
     */
    public static final String		XID = "XID";

    /*
     * Class cannot be instantiated
     */
    private TransactionInfo() {
    }
}
