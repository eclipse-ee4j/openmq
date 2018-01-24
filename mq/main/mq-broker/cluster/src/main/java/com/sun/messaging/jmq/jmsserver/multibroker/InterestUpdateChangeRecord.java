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
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.IOException;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterSubscriptionInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

public class InterestUpdateChangeRecord extends ChangeRecord {
    private String dname;
    private String cid;
    //only for G_NEW_INTEREST
    private Boolean shared = null; //must be Boolean.TRUE/FALSE
    private Boolean jmsshared = null; //must be Boolean.TRUE/FALSE
    private BrokerAddress broker = null;

    public InterestUpdateChangeRecord(GPacket gp) {
        operation = gp.getType();

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(gp);
        dname = csi.getDurableName();
        cid = csi.getClientID();
        shared = csi.getShared();
        jmsshared = csi.getJMSShared();
    }

    public String getSubscriptionKey() {  
        return Subscription.getDSubKey(cid, dname);
    }

    @Override
    public String getUniqueKey() {
        return "dur:"+Subscription.getDSubKey(cid, dname);
    }

    public Boolean getShared() {
        return shared;
    }

    public Boolean getJMSShared() {
        return jmsshared;
    }

    @Override
    public boolean isAddOp() {
        return (operation == ProtocolGlobals.G_NEW_INTEREST);
    }

    @Override
    public void transferFlag(ChangeRecordInfo cri) {
        if (shared != null && shared.booleanValue()) {
            cri.setFlagBit(ChangeRecordInfo.SHARED);
        }
        if (jmsshared != null && jmsshared.booleanValue()) {
            cri.setFlagBit(ChangeRecordInfo.JMSSHARED);
        }
    }

    public String getFlagString() {
        ChangeRecordInfo cri = new ChangeRecordInfo();
        transferFlag(cri);
        return cri.getFlagString(cri.getFlag());
    }

    public void setBroker(BrokerAddress b) {
        broker = b;
    }
    public BrokerAddress getBroker() {
        return broker;
    }
}
