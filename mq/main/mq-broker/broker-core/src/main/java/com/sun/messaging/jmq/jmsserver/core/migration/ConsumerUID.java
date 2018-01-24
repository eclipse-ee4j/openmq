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
 * @(#)ConsumerUID.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core.migration;

import com.sun.messaging.jmq.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.Session;

/**
 * Object format prior to 370 filestore, use for migration purpose only.
 * @see com.sun.messaging.jmq.jmsserver.core.ConsumerUID
 */
public class ConsumerUID extends com.sun.messaging.jmq.util.UID {

    static final long serialVersionUID = 5231476734057401743L;

    protected transient int ackType=Session.NONE;

    protected transient ConnectionUID conuid = null;
    protected transient BrokerAddress brokeraddr = Globals.getMyAddress();

    protected transient boolean shouldStore = false;

    public ConsumerUID() {
        // Allocates a new id
        super();
    }

    public ConsumerUID(long id) {
        // Wraps an existing id
        super(id);
    }

    public ConsumerUID(boolean empty) {
         super(0);
         if (!empty)
             initializeID();
    }

    /**
     * @deprecated since 3.5
     * for compatibility
     */
    public ConsumerUID(int oldnum) {
         super(oldnum);
    }

    public boolean shouldStore() {
        return shouldStore;
    }

    public void setShouldStore(boolean store) 
    {
        shouldStore = store;
    }

    public boolean isEmpty() {
        return id == 0;
    }

    public void initializeID() {
        if (id == 0)
            id = UniqueID.generateID(getPrefix());
    }

    public void clear() {
        id =0;
        conuid = null;
        brokeraddr = null;
        ackType =Session.NONE;
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

    public String getAckMode(int mode) {
        switch(ackType) {
            case Session.AUTO_ACKNOWLEDGE:
                return "AUTO_ACKNOWLEDGE";
            case Session.DUPS_OK_ACKNOWLEDGE:
                return "DUPS_OK_ACKNOWLEDGE";
            case Session.CLIENT_ACKNOWLEDGE:
                return "CLIENT_ACKNOWLEDGE";
            case Session.NO_ACK_ACKNOWLEDGE :
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
        if (brokeraddr == null)
            brokeraddr = Globals.getMyAddress();
        return this.brokeraddr;
    }

    public String toString() {
        return "[consumer:" + super.toString() + ", type="
                 + getAckMode(ackType) +"]";
    }

    public Object readResolve() throws ObjectStreamException {
        // Replace w/ the new object
        Object obj = new com.sun.messaging.jmq.jmsserver.core.ConsumerUID(id);
        return obj;
    }
}
