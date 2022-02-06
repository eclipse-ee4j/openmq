/*
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

package com.sun.messaging.jmq.jmsclient;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.sun.messaging.jmq.util.JMQXid;

import jakarta.jms.JMSException;

abstract class XAResourceForX {
    /**
     * Possible states of this XAResource
     */
    public static final int CREATED = 0; // after first creation, or after commit() or rollback()
    public static final int STARTED = 1; // after start() called
    public static final int FAILED = 2; // after end(fail) called
    public static final int INCOMPLETE = 3; // after end(suspend) called
    public static final int COMPLETE = 4; // after end (success) called
    public static final int PREPARED = 5; // after prepare() called

    // use this property to turn off xa transaction tracking
    public static final boolean turnOffXATracking = Boolean.getBoolean("imq.ra.turnOffXATracking");

    // set to true by default - track xa transaction state
    public static final boolean XATracking = !turnOffXATracking;

    /*
     * This XAResource depends on the connection being valid across start,end,prepare,commit operations as is the case for
     * the j2ee 1.4 resource adapter connection
     */
    ConnectionImpl epConnection;
    // private Transaction _transaction = null;

    int transactionTimeout = 0; // transactions do not timeout

    int id;

    // transaction ID - remains invalid until set by start
    long transactionID = -1L;

    // JmqXid
    JMQXid jmqXid = null;

    boolean started = false;

    /**
     * State of this XAresource
     */
    int resourceState = CREATED;

    abstract int prepare(Xid foreignXid, boolean onePhase) throws XAException;

    final void removeXid(JMQXid jmqXid) {
        if (isXATracking()) {
            // System.out.println("***** removing xid: " + jmqXid + " ,xatable size: " + xaTable.size());
            xaTableRemove(jmqXid);
            // System.out.println("***** removed xid: " + jmqXid + " ,xatable size: " + xaTable.size());
        }
    }

    abstract void checkCommitStatus(Exception cause, int tstate, JMQXid jmqXid, boolean onePhase) throws JMSException, XAException;

    /**
     * XATracking default is set to true.
     *
     * @return true if is connected to HA broker and XATracking flag is set to true.
     */
    final boolean isXATracking() {
        return (epConnection.isConnectedToHABroker() && XATracking);
    }

    abstract void xaTablePut(JMQXid jmqXid2, Integer xaPrepare);

    abstract void xaTableRemove(JMQXid jmqXid2);

    /**
     * For XA onePhase commit, if RA is connected to HA brokers, we use two phase MQ protocol to commit a transaction.
     *
     * "JMQXAOnePhase" property is set to true for prepare and commit pkts.
     *
     * "TMNOFLAGS" is used in the onePhase commit pkt.
     */
    void HAOnePhaseCommit(Xid foreignXid, JMQXid jmqXid) throws JMSException, XAException {

        int tstate = Transaction.TRANSACTION_ENDED;

        try {
            // prepare xa onephase commit
            this.prepare(foreignXid, true);

            tstate = Transaction.TRANSACTION_PREPARED;

            if (isXATracking()) {
                xaTablePut(jmqXid, XAResourceForRA.XA_PREPARE);
            }

            // param true is to indicate "JMQXAOnePhase" is needed
            // for the commit protocol property.
            epConnection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid, true);
        } catch (Exception jmse) {
            // check onephase commit status
            this.checkCommitStatus(jmse, tstate, jmqXid, true);
        }

        this.removeXid(jmqXid);
    }
}
