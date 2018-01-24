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
 * @(#)TransactionLogType.java	1.3 07/02/07
 */ 

package com.sun.messaging.jmq.io.txnlog;

/**
 * This class defines the bitmaps for setting the type of data
 * logged in the TransactionLogRecord. 
 *
 * @see TransactionLogRecord#setType
 * @see TransactionLogRecord#getType
 */
public class TransactionLogType {
	
    /**
     * Type definition for a transaction involves in producing messages only.
     */
    public static final int PRODUCE_TRANSACTION = 0x00000001;
    
    /**
     * Type definition for a transaction involves in consuming messages only.
     */
    public static final int CONSUME_TRANSACTION = 0x00000002;
    
    /**
     * Type definition for a transaction involves in both producing and consuming messages.
     */
    public static final int PRODUCE_AND_CONSUME_TRANSACTION = 0x00000004;
    
    /**
     * Type definition for a compound transaction comprised of multiple transactions.
     */
    public static final int COMPOUND_TRANSACTION = 0x00000008;
}

