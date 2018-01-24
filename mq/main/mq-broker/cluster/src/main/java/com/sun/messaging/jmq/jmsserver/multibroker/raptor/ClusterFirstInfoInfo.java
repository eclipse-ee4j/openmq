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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

/**
 * A general cluster first info packet after link handshake 
 */

public class ClusterFirstInfoInfo 
{

    private ChangeRecordInfo lastStoredChangeRecord = null;

    private GPacket gp = null;  //out going
    private GPacket pkt = null; //in coming

    private ClusterFirstInfoInfo() {
    }

    private ClusterFirstInfoInfo(GPacket pkt) {
        this.pkt = pkt;
    }

    public static ClusterFirstInfoInfo newInstance() {
        return new ClusterFirstInfoInfo(); 
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterFirstInfoInfo newInstance(GPacket pkt) {
        return new ClusterFirstInfoInfo(pkt);
    }

    public void setLastStoredChangeRecord(ChangeRecordInfo cri) {
        if (gp == null) {
            gp = GPacket.getInstance();
            gp.setType(ProtocolGlobals.G_FIRST_INFO);
        }
        gp.putProp("shareccLastStoredSeq", cri.getSeq());
        gp.putProp("shareccLastStoredUUID", cri.getUUID());
        gp.putProp("shareccLastStoredResetUUID", cri.getResetUUID());
        gp.putProp("shareccLastStoredType", Integer.valueOf(cri.getType()));
    }

    public void setBroadcast(boolean b) {
        assert ( gp != null );
        if (b) { 
            gp.setBit(gp.B_BIT, true); 
        }
    }

    public GPacket getGPacket() { 
        gp.setBit(gp.A_BIT, false);
        return gp;
    }

    public ChangeRecordInfo getLastStoredChangeRecord() {
        assert ( pkt != null );

        if (pkt.getProp("shareccLastStoredSeq") == null) {
            return null;
        }

        ChangeRecordInfo cri = new ChangeRecordInfo();
        cri.setSeq((Long)pkt.getProp("shareccLastStoredSeq"));
        cri.setUUID((String)pkt.getProp("shareccLastStoredUUID"));
        cri.setResetUUID((String)pkt.getProp("shareccLastStoredResetUUID"));
        cri.setType(((Integer)pkt.getProp("shareccLastStoredType")).intValue());
        return cri;
    }

}
