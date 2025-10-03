/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.UUID;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;

/**
 */
public class ClusterNewMasterBrokerPrepareInfo {

    private List<ChangeRecordInfo> records = null;
    private Long xid = null;
    private String uuid = null;

    private BrokerAddress newmaster = null;
    Cluster c = null;

    private GPacket pkt = null;

    private ClusterNewMasterBrokerPrepareInfo(BrokerAddress newmaster, List<ChangeRecordInfo> records, Long xid, Cluster c) {
        this.records = records;
        this.xid = xid;
        this.c = c;
        this.newmaster = newmaster;
        this.uuid = UUID.randomUUID().toString();
    }

    private ClusterNewMasterBrokerPrepareInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterNewMasterBrokerPrepareInfo newInstance(BrokerAddress newmaster, List<ChangeRecordInfo> records, Long xid, Cluster c) {
        return new ClusterNewMasterBrokerPrepareInfo(newmaster, records, xid, c);
    }

    /**
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterNewMasterBrokerPrepareInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterNewMasterBrokerPrepareInfo(pkt, c);
    }

    public GPacket getGPacket() throws Exception {

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE);
        gp.putProp("TS", Long.valueOf(System.currentTimeMillis()));
        gp.putProp("C", Integer.valueOf(records.size()));
        gp.putProp("X", xid);
        gp.putProp("UUID", uuid);
        c.marshalBrokerAddress(newmaster, gp);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < records.size(); i++) {
            byte[] rec = records.get(i).getRecord();
            bos.write(rec, 0, rec.length);
        }
        bos.flush();
        byte[] buf = bos.toByteArray();
        gp.setPayload(ByteBuffer.wrap(buf));
        gp.setBit(gp.A_BIT, true);

        return gp;
    }

    public String getUUID() {
        if (pkt != null) {
            return (String) pkt.getProp("UUID");
        }
        return uuid;
    }

    public BrokerAddress getNewMasterBroker() throws Exception {
        assert (pkt != null);
        newmaster = c.unmarshalBrokerAddress(pkt);
        return newmaster;
    }

    public int getRecordCount() {
        assert (pkt != null);
        return ((Integer) pkt.getProp("C")).intValue();
    }

    public Long getXid() {
        assert (pkt != null);
        return (Long) pkt.getProp("X");
    }

    public Long getTimestamp() {
        assert (pkt != null);
        return (Long) pkt.getProp("TS");
    }

    public byte[] getRecords() {
        assert (pkt != null);
        byte[] buf = null;
        if (pkt.getPayload() != null) {
            buf = pkt.getPayload().array();
        }
        return buf;
    }

    public GPacket getReplyGPacket(int status, String reason) {
        assert (pkt != null);
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY);
        gp.putProp("X", pkt.getProp("X"));
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        return gp;
    }

    @Override
    public String toString() {
        if (pkt == null) {
            return "[newMasterBroker=" + newmaster + ", records=" + records.size() + ", xid=" + xid + ", uuid=" + uuid + "]";
        }
        return (newmaster == null ? "" : "[newMasterBroker=" + newmaster) + ", records=" + getRecordCount() + ", xid=" + getXid() + ", ts=" + getTimestamp()
                + ", uuid=" + getUUID() + "]";
    }

    public static Long getReplyPacketXid(GPacket gp) {
        return (Long) gp.getProp("X");
    }
}
