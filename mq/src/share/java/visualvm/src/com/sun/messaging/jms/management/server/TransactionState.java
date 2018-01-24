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
 * @(#)TransactionState.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing constants for transaction states.
 */
public class TransactionState {
    /** 
     * Unknown transaction state.
     */
    public static final int UNKNOWN = -1;

    /** 
     * Transaction was created.
     */
    public static final int CREATED    = 0;

    /** 
     * Transaction was started.
     */
    public static final int STARTED    = 1;

    /** 
     * Transaction failed.
     */
    public static final int FAILED     = 2;

    /** 
     * Transaction incomplete.
     */
    public static final int INCOMPLETE = 3;

    /** 
     * Transaction completed.
     */
    public static final int COMPLETE   = 4;

    /** 
     * Transaction is in prepared state. 
     * It can now be commited or rolled back.
     */
    public static final int PREPARED   = 5;

    /** 
     * Transaction was committed.
     */
    public static final int COMMITTED  = 6;

    /** 
     * Transaction was rolled back.
     */
    public static final int ROLLEDBACK = 7;

    /** 
     * Transaction timed out.
     */
    public static final int TIMED_OUT = 8;


    /*
     * Class cannot be instantiated
     */
    private TransactionState() {
    }
    
    /**
     * Returns a string representation of the specified transaction state.
     *
     * @param state Transaction state.
     * @return String representation of the specified transaction state.
     */
    public static String toString(int state)  {
        switch (state) {
            case CREATED:
                return "CREATED";

            case STARTED:
                return "STARTED";

            case FAILED:
                return "FAILED";

            case INCOMPLETE:
                return "INCOMPLETE";

            case COMPLETE:
                return "COMPLETE";

            case PREPARED:
                return "PREPARED";

            case COMMITTED:
                return "COMMITTED";

            case ROLLEDBACK:
                return "ROLLEDBACK";

            case TIMED_OUT:
                return "TIMED_OUT";

	    default:
                return "UNKNOWN";
        }
    }
}
