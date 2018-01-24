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
 * @(#)TransactionOperations.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on transaction operations.
 */
public class TransactionOperations {
    /** 
     * Get list of transaction IDs
     */
    public static final String		GET_TRANSACTION_IDS = "getTransactionIDs";

    /** 
     * Get info on all transactions
     */
    public static final String		GET_TRANSACTION_INFO = "getTransactionInfo";

    /** 
     * Get info on specified transaction (via ID)
     */
    public static final String		GET_TRANSACTION_INFO_BY_ID = "getTransactionInfoByID";

    /** 
     * Commit a transaction.
     */
    public static final String		COMMIT = "commit";

    /** 
     * Rollback a transaction.
     */
    public static final String		ROLLBACK = "rollback";

    /*
     * Class cannot be instantiated
     */
    private TransactionOperations() {
    }
    
}
