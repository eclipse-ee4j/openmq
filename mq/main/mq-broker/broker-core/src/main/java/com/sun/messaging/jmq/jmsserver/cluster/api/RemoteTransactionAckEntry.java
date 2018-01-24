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
 * @(#)RemoteTransactionAckEntry.java	1.2 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api;

/**
 */

import java.util.Hashtable;
import java.util.Vector;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;

public class RemoteTransactionAckEntry
{
    TransactionAcknowledgement[] acks = null;
    boolean processed =  false;
    boolean recovery = false;
    boolean localremote = false;

    public RemoteTransactionAckEntry(TransactionAcknowledgement[] acks,
                                     boolean localremote) {
        this(acks, false, localremote, false);
    }

    public RemoteTransactionAckEntry(TransactionAcknowledgement[] acks,
                                     boolean recovery, boolean done) {
        this(acks, recovery, false, done);
    }

    private RemoteTransactionAckEntry(TransactionAcknowledgement[] acks,
        boolean recovery, boolean localremote, boolean done) {

        this.acks = acks;
        this.recovery = recovery;
        this.localremote = localremote;
        this.processed = done;
    }

    public synchronized boolean processed() {
        if (processed) return true;
        processed = true;
        return false;
    }

    public synchronized boolean isProcessed() { 
        return processed;
    }

    public synchronized boolean isLocalRemote() { 
        return localremote;
    }

    public TransactionAcknowledgement[] getAcks() {
        return acks;
    }

    public boolean isRecovery() {
        return recovery;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable(); 
        ht.put("processed", String.valueOf(processed));
        ht.put("recovery", String.valueOf(recovery));
        ht.put("localremote", String.valueOf(localremote));
        if (acks != null) {
            Vector v = new Vector();
            for (int i = 0;  i < acks.length; i++) {
                v.add(acks[i].toString());
            }
            ht.put("txnacks", v);
        }
        return ht;
    }
} 
