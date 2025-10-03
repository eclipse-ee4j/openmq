/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;

import java.io.*;

public class ConsumerUID extends com.sun.messaging.jmq.util.UID implements Externalizable {

    static final long serialVersionUID = 471544583389431969L;

    protected int ackType = Session.NONE;

    protected ConnectionUID conuid = null;
    protected BrokerAddress brokeraddr = Globals.getMyAddress();

    protected boolean shouldStore = false;

    public ConsumerUID() {
        // Allocates a new id
    }

    public ConsumerUID(long id) {
        // Wraps an existing id
        super(id);
    }

    public ConsumerUID(boolean empty) {
        super(0);
        if (!empty) {
            initializeID();
        }
    }

    public boolean shouldStore() {
        return shouldStore;
    }

    public void setShouldStore(boolean store) {
        shouldStore = store;
    }

    public boolean isEmpty() {
        return id == 0;
    }

    public void initializeID() {
        if (id == 0) {
            id = UniqueID.generateID(getPrefix());
        }
    }

    public void clear() {
        id = 0;
        conuid = null;
        brokeraddr = null;
        ackType = Session.NONE;
    }

    public void updateUID(ConsumerUID uid) {
        id = uid.id;
    }

    public void copy(ConsumerUID uid) {
        id = uid.id;
        conuid = uid.conuid;
        brokeraddr = uid.brokeraddr;
        ackType = uid.ackType;
    }

    public boolean isAutoAck() {
        return (ackType == Session.AUTO_ACKNOWLEDGE);
    }

    public int getAckType() {
        return ackType;
    }

    public String getAckMode() {
        switch (ackType) {
        case Session.AUTO_ACKNOWLEDGE:
            return "AUTO_ACKNOWLEDGE";
        case Session.DUPS_OK_ACKNOWLEDGE:
            return "DUPS_OK_ACKNOWLEDGE";
        case Session.CLIENT_ACKNOWLEDGE:
            return "CLIENT_ACKNOWLEDGE";
        case Session.NO_ACK_ACKNOWLEDGE:
            return "NO_ACK_ACKNOWLEDGE";
        default:
            return "NONE";
        }
    }

    public boolean isDupsOK() {
        return (ackType == Session.DUPS_OK_ACKNOWLEDGE);
    }

    public boolean isNoAck() {
        return (ackType == Session.NO_ACK_ACKNOWLEDGE);
    }

    public boolean isUnsafeAck() {
        return isDupsOK() || isNoAck();
    }

    public void setAckType(int mode) {
        this.ackType = mode;
    }

    public void setConnectionUID(ConnectionUID cid) {
        this.conuid = cid;
    }

    public ConnectionUID getConnectionUID() {
        return conuid;
    }

    public void setBrokerAddress(BrokerAddress bkraddr) {
        this.brokeraddr = bkraddr;
    }

    public BrokerAddress getBrokerAddress() {
        if (brokeraddr == null) {
            brokeraddr = Globals.getMyAddress();
        }
        return this.brokeraddr;
    }

    public boolean isLocal() {
        return brokeraddr == null || this.brokeraddr == Globals.getMyAddress();
    }

    @Override
    public String toString() {
        return "[consumer:" + super.toString() + ", type=" + getAckMode() + "]";
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        id = in.readLong();

        ackType = Session.NONE;
        conuid = null;
        brokeraddr = Globals.getMyAddress();
        shouldStore = false;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(id);
    }

    private void readObject(java.io.ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        ackType = Session.NONE;
        conuid = null;
        brokeraddr = Globals.getMyAddress();
        shouldStore = false;
    }

}
