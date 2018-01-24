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
 * @(#)TransactionBroker.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.*;
import java.io.*;

/**
 * A transaction participant broker
 */

public class TransactionBroker implements Externalizable, Cloneable
{
    static final long serialVersionUID = 4331266333483540901L;

    transient private static Logger logger = Globals.getLogger();

    static final int PENDING  = 0;
    static final int COMPLETE = 1;

    BrokerAddress broker = null;
    int state  = PENDING;

    // default construct for uninitialized object
    public TransactionBroker() {
    }

    /**
     */
    public TransactionBroker(BrokerAddress broker) {
        this.broker = broker;
        state = PENDING;
    }

    public TransactionBroker(BrokerAddress broker, boolean completed) {
        this(broker);
        if (completed) state = COMPLETE;
    }

    public BrokerAddress getBrokerAddress() {
        return broker;
    }

    public boolean isCompleted() {
        return state == COMPLETE;
    }

    public void setCompleted(boolean value) {
        state = (value ? COMPLETE : PENDING);
    }

    public boolean copyState(TransactionBroker b) throws BrokerException {
        if (state == b.state) return false;
        if (state == PENDING) {
            state = b.state;
            return true;
        }
        throw new BrokerException(
        "Can't update transaction broker state from "+toString(state)+ " to "+toString(b.state));
    }

    public int hashCode() {
        return broker.hashCode();
    }

    // just compare the hashcode
    public boolean equals(Object o) {
        if (!(o instanceof TransactionBroker)) {
            return false;
        }
        TransactionBroker other = (TransactionBroker)o;
        BrokerAddress thiscurrb = this.getCurrentBrokerAddress();
        BrokerAddress othercurrb = other.getCurrentBrokerAddress();
        boolean sameaddr = ((this.broker).equals(other.broker) ||
                            (thiscurrb != null && 
                             thiscurrb.equals(othercurrb)));
        if (!Globals.getDestinationList().isPartitionMode()) {
            return sameaddr;
        }
        return sameaddr && 
               (this.broker.getStoreSessionUID()).equals(
                other.broker.getStoreSessionUID());
    }

    public BrokerAddress getCurrentBrokerAddress() {
        if (!Globals.getHAEnabled()) {
            return getBrokerAddress();
        }
        String brokerid = null;
        UID ss = broker.getStoreSessionUID();
        if (ss == null) {
            return null;
        }
        brokerid = Globals.getClusterManager().lookupStoreSessionOwner(ss);
        if (brokerid == null) {
            return null;
        }
        if (brokerid.equals(Globals.getMyAddress().getBrokerID())) {
            return Globals.getMyAddress();
        }
        return Globals.getClusterBroadcast().lookupBrokerAddress(brokerid);
    }

    public boolean isSame(UID ssid) {
        if (!Globals.getHAEnabled()) {
            return false;
        }
        UID ss = broker.getStoreSessionUID();
        if (ss.equals(ssid)) {
            return true;
        }
        return false;
    }

    public String toString() {
        return "[" + broker.toString() + "]"+
                ((state == COMPLETE) ? "":toString(state));
    }

    private static String toString(int s) {
        if (s == PENDING) {
            return "PENDING";
        }
        if (s == COMPLETE) {
            return "COMPLETE"; 
        }
        return "UNKNOWN";
    }

    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

        state = in.readInt();
        broker = (BrokerAddress)in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(state);
        out.writeObject(broker);
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error ("This should never happen!");
        }
    }

}
