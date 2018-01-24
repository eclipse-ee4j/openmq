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
 * @(#)CheckPointListener.java	1.2 06/29/07
 */ 

package com.sun.messaging.jmq.io.txnlog;

/**
 * A broker instance sets itself as a <code>CheckPointListener</code> to the
 * TransactionLogWriter. When the log writer detects that a sync
 * operation is required, it calls the listener's checkpoint()
 * method.
 * <p>                 
 * When the check point listener is called, the broker SHOULD
 * sync its message store ASAP and then call
 * TransactionLogWriter.checkpoint() to instruct the log writer
 * to perform a check point sync operation.
 * <p>
 * In general, the log writer's performance will decrease if the
 * broker fails to call TransactionLogWriter.checkpoint() after
 * the check point listener is notified.
 *
 */
public interface CheckPointListener {
    
    /**
     * Notify broker to perform a check point sync with the 
     * <code>TransactionLogWriter</code>.
     * <p>
     * When received the notification, broker should sync its
     * message store and calls TransactionLogWriter.checkpoint()
     * to inform the TransactionLogWriter that the message store
     * is synced.
     * 
     * @see TransactionLogWriter#setCheckPointListener(CheckPointListener)
     * @see TransactionLogWriter#checkpoint
     */
    public void checkpoint();
}
