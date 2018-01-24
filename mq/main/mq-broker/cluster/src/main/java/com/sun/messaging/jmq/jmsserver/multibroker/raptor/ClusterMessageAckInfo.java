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
 * @(#)ClusterMessageAckInfo.java	1.20 11/13/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.*;
import java.util.*;
import java.nio.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.AckEntryNotFoundException;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

/**
 * An instance of this class is intended to be used one direction only
 */

public class ClusterMessageAckInfo 
{
    protected Logger logger = Globals.getLogger();

    private SysMessageID[] sysids = null;
    private ConsumerUID[] intids =  null; 
    private int ackType;
    private Long ackackXid = null;
    private Map optionalProps = null;
    private Long transactionID = null;
    private UID txnStoreSession = null;
    private BrokerAddress msgHome;
    private Cluster c = null;

    private GPacket pkt = null;
    private DataInputStream dis = null;
    private static FaultInjection fi = FaultInjection.getInjection(); 
    private boolean twophase = false;
    private boolean ackackAsync = false;

    private ClusterMessageAckInfo(SysMessageID[] sysids, ConsumerUID[] intids,
                                  int ackType, Long ackackXid, boolean async, 
                                  Map optionalProps, Long transactionID, UID txnStoreSession,
                                  BrokerAddress msgHome, 
                                  Cluster c, boolean twophase) {
        this.sysids = sysids;
        this.intids = intids;
        this.ackType = ackType;
        this.ackackXid = ackackXid;
        this.optionalProps = optionalProps;
        this.transactionID = transactionID;
        this.txnStoreSession = txnStoreSession;
        this.msgHome =  msgHome;
        this.c = c;
        this.twophase = twophase;
        this.ackackAsync = async;
    }

    private ClusterMessageAckInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    private ClusterMessageAckInfo(GPacket pkt) {
        this.pkt = pkt;
        this.c = null;
    }

    public static ClusterMessageAckInfo newInstance(SysMessageID[] sysids,
                             ConsumerUID[] cuids, int ackType, Long ackackXid, boolean async,
                             Map optionalProps, Long transactionID, UID txnStoreSession,
                             BrokerAddress msgHome, Cluster c, boolean twophase) {
        return new ClusterMessageAckInfo(sysids, cuids, ackType, ackackXid, async,
                                         optionalProps, transactionID, txnStoreSession,
                                         msgHome, c, twophase);
    }

    /**
     * GPacket to Destination 
     *
     * @param pkt The GPacket to be unmarsheled
     */
    public static ClusterMessageAckInfo newInstance(GPacket pkt, Cluster c) {
        return new ClusterMessageAckInfo(pkt, c);
    }

    public GPacket getGPacket() throws IOException { 
        if (twophase && transactionID == null) {
            throw new IOException(Globals.getBrokerResources().getKString(
                      BrokerResources.E_INTERNAL_BROKER_ERROR, 
                      "transactionID required for two-phase ack"));
        } 

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_MESSAGE_ACK);
        gp.putProp("T", Integer.valueOf(ackType));
        c.marshalBrokerAddress(c.getSelfAddress(), gp);
        if (msgHome.getBrokerSessionUID() != null) {
        gp.putProp("messageBrokerSession", Long.valueOf(msgHome.getBrokerSessionUID().longValue()));
        }
        if (msgHome.getStoreSessionUID() != null) {
        gp.putProp("messageStoreSession", Long.valueOf(msgHome.getStoreSessionUID().longValue()));
        }

        if (optionalProps != null) {
            Object pn = null;
            Iterator itr = optionalProps.keySet().iterator();
            while (itr.hasNext()) {
                pn =  itr.next();
                gp.putProp(pn, optionalProps.get(pn));
            }
        }

        if (transactionID != null) {
            gp.putProp("transactionID", transactionID);
            if (txnStoreSession != null) {
                gp.putProp("transactionStoreSession", 
                    Long.valueOf(txnStoreSession.longValue()));
            }
        }
        if (ackackXid != null) {
            gp.setBit(gp.A_BIT, true);
            gp.putProp("X", ackackXid);
            if (ackackAsync) {
                gp.putProp("ackackAsync", Boolean.valueOf(true));
            }
        }

        if (ackType == ClusterGlobals.MB_MSG_TXN_ROLLEDBACK) {
            gp.putProp("C", Integer.valueOf(0));
            return gp;
        }
        int cnt = sysids.length;
        gp.putProp("C", Integer.valueOf(cnt));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        for (int i = 0; i < cnt; i++) {
            sysids[i].writeID(dos);
            ClusterConsumerInfo.writeConsumerUID(intids[i], dos);
        }

        dos.flush();
        bos.flush();

        byte[] buf = bos.toByteArray();
        gp.setPayload(ByteBuffer.wrap(buf));

        return gp;
    }

    public int getAckType() {
        assert ( pkt != null );
        return ((Integer) pkt.getProp("T")).intValue();
    }

    public Map getOptionalProps() {
        assert ( pkt != null );
        Set keys = pkt.propsKeySet();
        if (keys == null || keys.size() == 0) return null;
        Map m = new HashMap();
        Object key = null;
        Iterator itr = keys.iterator();
        while (itr.hasNext()) {
            key = itr.next();   
            m.put(key, pkt.getProp(key));
        }
        return m;
    }
    
    public UID getMessageStoreSessionUID() {
        assert ( pkt != null );
        Long ssid = (Long)pkt.getProp("messageStoreSession"); 
        if (ssid == null) {
            return null;
        }
        return new UID(ssid.longValue());
    }

    public UID getMessageBrokerSessionUID() {
        assert ( pkt != null );
        Long bsid = (Long)pkt.getProp("messageBrokerSession"); 
        if (bsid == null) return null;
        return new UID(bsid.longValue());
    }

    public Long getTransactionID() {
        assert ( pkt != null );
        return  (Long)pkt.getProp("transactionID");
    }

    public UID getTransactionStoreSessionUID() {
        assert ( pkt != null );
        Long ssid = (Long)pkt.getProp("transactionStoreSession"); 
        if (ssid == null) {
            return null;
        }
        return new UID(ssid.longValue());
    }


    public Integer getCount() {
        assert ( pkt != null );
        return  (Integer)pkt.getProp("C");
    }


    /**
     * must called in the following order: 
     * 
     * initPayloadRead()
     * readPayloadSysMesssageID()
     * readPayloadConsumerUID()
     *
     */
    public void initPayloadRead() {
        assert ( pkt != null );

        byte[] buf = pkt.getPayload().array();
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        dis = new DataInputStream(bis);
    }

    public SysMessageID readPayloadSysMessageID() throws IOException {
        assert ( dis != null );

        SysMessageID sysid = new SysMessageID();
        sysid.readID(dis);
        return sysid;
    }

    public ConsumerUID readPayloadConsumerUID() throws Exception { 
        assert ( dis != null );
        ConsumerUID intid =  ClusterConsumerInfo.readConsumerUID(dis);
        if (c != null) {
            BrokerAddress ba = c.unmarshalBrokerAddress(pkt);
            if (ba != null) intid.setBrokerAddress(ba);
        }
        return intid;
    }

    public boolean needReply() {
        assert ( pkt != null );
        return pkt.getBit(pkt.A_BIT);
    }

    public GPacket getReplyGPacket(int status, String reason, ArrayList[] aes) {
        assert ( pkt != null );

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_MESSAGE_ACK_REPLY);
        gp.putProp("X", (Long)pkt.getProp("X"));
        gp.putProp("T", Integer.valueOf(getAckType()));
        if (pkt.getProp("C") != null) {
            gp.putProp("C", pkt.getProp("C"));
        }
        if (pkt.getProp("messageBrokerSession") != null) {
            gp.putProp("messageBrokerSession", pkt.getProp("messageBrokerSession"));
        }
        if (pkt.getProp("messageStoreSession") != null) {
            gp.putProp("messageStoreSession", pkt.getProp("messageStoreSession"));
        }
        if (pkt.getProp("transactionID") != null) {
            gp.putProp("transactionID", pkt.getProp("transactionID"));
        }
        if (pkt.getProp("ackackAsync") != null) {
            gp.putProp("ackackAsync", pkt.getProp("ackackAsync"));
        }
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) gp.putProp("reason", reason);

        if (aes == null) {
            if (pkt.getPayload() != null) {
                gp.setPayload(ByteBuffer.wrap(pkt.getPayload().array()));
            }
            return gp;
        }

        gp.putProp("notfound", Integer.valueOf(aes[0].size()));
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            for (int i = 0; i < aes[0].size(); i++) {
                ((SysMessageID)aes[0].get(i)).writeID(dos);
                ClusterConsumerInfo.writeConsumerUID((
                  com.sun.messaging.jmq.jmsserver.core.ConsumerUID)aes[1].get(i), dos);
            }
            dos.flush();
            bos.flush();
            byte[] buf = bos.toByteArray();
            gp.setPayload(ByteBuffer.wrap(buf));
        } catch (Exception e) {
            Globals.getLogger().logStack(Globals.getLogger().WARNING, e.getMessage(), e);
        }

        return gp;
    }

    public static AckEntryNotFoundException getAckEntryNotFoundException(GPacket ackack) {
        Integer notfound =  (Integer)ackack.getProp("notfound");
        if (notfound == null) return null;

        int cnt = notfound.intValue();

        //Long tid = (Long)ackack.getProp("transactionID");
        String reason = (String)ackack.getProp("reason");
        AckEntryNotFoundException aee = new AckEntryNotFoundException(reason);

        byte[] buf = ackack.getPayload().array();
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bis);
        SysMessageID sysid = null;
        ConsumerUID intid = null;
        try {
            for (int i = 0; i < cnt; i++) {
                sysid = new SysMessageID();
                sysid.readID(dis);
                intid =  ClusterConsumerInfo.readConsumerUID(dis);
                aee.addAckEntry(sysid, intid);
            }
        } catch (Exception e) {
            Globals.getLogger().logStack(Globals.getLogger().WARNING, e.getMessage(), e);
        }

        return aee;
    }


    /** 
     * To be used by ack sender
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("\n\tAckType = ").append(ClusterGlobals.getAckTypeString(ackType));

        if (msgHome.getBrokerSessionUID() != null) {
        buf.append("\n\tMessageBrokerSession = ").append(msgHome.getBrokerSessionUID().longValue());
        }
        if (msgHome.getHAEnabled()) {
            buf.append("\n\tMessageStoreSession = ").append(msgHome.getStoreSessionUID().longValue());
        }

        if (transactionID != null) {
           buf.append("\n\tTransactionID = ").append(transactionID);
        }

        if (ackackXid != null) {
           buf.append("\n\tAckAck = ").append("true");
		   buf.append("\n\tXID = ").append(ackackXid);
        }

        buf.append("\n\tMessage Home = ").append(msgHome);

        if (sysids != null) {
            buf.append("\n\tC=").append(Integer.valueOf(sysids.length));
            for (int i = 0; i < sysids.length; i++) {
                buf.append("\n\t\tSysMessageID = ").append(sysids[i]);
                buf.append("\n\t\tConsumerUID = ").append(intids[i]);
            }
        }
        if (optionalProps != null) {
            buf.append("\n\tOptional Props = ").append(""+optionalProps);
        }
        buf.append("\n");
        return buf.toString();
    }

     /**
     * To be used by ack receiver
     */
    public String toString(SysMessageID[] sysids, ConsumerUID[] cuids) {
        assert ( pkt !=  null );

        StringBuffer buf = new StringBuffer();
        buf.append("\n\tAckType = ").append(ClusterGlobals.getAckTypeString(getAckType()));

        if (getMessageBrokerSessionUID() != null) {
        buf.append("\n\tMessageBrokerSession = ").append(getMessageBrokerSessionUID().longValue());
        }
        if (getMessageStoreSessionUID() != null) {
        buf.append("\n\tMessageStoreSession = ").append(getMessageStoreSessionUID().longValue());
        }

        if (getTransactionID() != null) {
        buf.append("\n\tTransactionID = ").append(getTransactionID());
        }
        if (pkt.getProp("X") != null) {
            buf.append("\n\tXID = ").append(pkt.getProp("X"));
        }
        if (getCount() != null) {
            buf.append("\n\tCount = ").append(getCount());
        }

        if (sysids != null) {
            buf.append("\n\tC=").append(sysids.length);
            for (int i = 0; i < sysids.length; i++) {
                buf.append("\n\t\tSysMessageID = ").append(sysids[i]);
                buf.append("\n\t\tConsumerUID = ").append(cuids[i]);
                buf.append("\n");
            }
        }
        buf.append("\n");

        return buf.toString();
    }

    public static Long getAckAckXid(GPacket ackack) {
        return (Long)ackack.getProp("X");
    }

    public static Integer getAckAckType(GPacket ackack) {
        return (Integer)ackack.getProp("T");
    }

    public static boolean isAckAckAsync(GPacket ackack) {
        Boolean b = (Boolean)ackack.getProp("ackackAsync");
        if (b == null) return false;
        return b.booleanValue();
     
    }

    public static Long getAckAckTransactionID(GPacket ackack) {
        return (Long)ackack.getProp("transactionID");
    }

    public static int getAckAckStatus(GPacket ackack) {
        return ((Integer)ackack.getProp("S")).intValue();
    }

    public static String getAckAckStatusReason(GPacket ackack) {
        return (String)ackack.getProp("reason");
    }

    public static UID getAckAckStoreSessionUID(GPacket ackack) {
        Long v = (Long)ackack.getProp("messageBrokerSession");
        if (v == null) {
            return null;
        }
        return new UID(v.longValue());
    }

    /**
     * To be used for ackack pkt 
     */
    public static String toString(GPacket ackack) {
        int acktyp = -1;
        if (getAckAckType(ackack) != null) {
        acktyp = getAckAckType(ackack).intValue();
        }

        StringBuffer buf = new StringBuffer();
        buf.append("\n\tackStatus = ").append(Status.getString(getAckAckStatus(ackack)));

        if (ackack.getProp("reason") != null) {
        buf.append("\n\tReason = ").append(getAckAckStatusReason(ackack));
        }

        buf.append("\n\tAckType = ").append(ClusterGlobals.getAckTypeString(acktyp));

        if (ackack.getProp("messageBrokerSession") != null) {
            buf.append("\n\tMessageBrokerSession = ").append(ackack.getProp("messageBrokerSession"));
        }
        if (ackack.getProp("messageStoreSession") != null) {
            buf.append("\n\tMessageStoreSession = ").append(ackack.getProp("messageStoreSession"));
        }

        if (ackack.getProp("transactionID") != null) {
            buf.append("\n\tTransactionID = ").append(ackack.getProp("transactionID"));
        }

        if (ackack.getProp("notfound") != null) {
            buf.append("\n\tnotfound = ").append(((Integer)ackack.getProp("notfound")));
        }

        if (ackack.getPayload() != null) {
            Integer notfound = (Integer)ackack.getProp("notfound");
            ClusterMessageAckInfo cai = new ClusterMessageAckInfo(ackack);
            try {
                int cnt = (cai.getCount() == null) ? 1: cai.getCount().intValue();
                if (notfound != null) cnt = notfound.intValue();
                cai.initPayloadRead();
                for (int i = 0; i < cnt; i++) {
                    buf.append("\n\t\tSysMessageID = ").append(cai.readPayloadSysMessageID());
                    buf.append("\n\t\tConsumerUID = ").append(cai.readPayloadConsumerUID().longValue());
                    buf.append("\n");
                }

            } catch (Exception e) {
                Globals.getLogger().logStack(Logger.WARNING, e.getMessage(), e);
            }
        }
        return buf.toString();
    }

    public static void CHECKFAULT(HashMap ackCounts, 
                                  int ackType, Long txnID,
                                  String fprefix, String fstage) {
        int ackCount = 0;
        HashMap fips = new HashMap();
        Integer ak = Integer.valueOf(ackType);
        synchronized(ackCounts) {
            Integer v = (Integer)ackCounts.get(ak);
            if (v != null) ackCount = v.intValue();
            if (fstage.equals(FaultInjection.STAGE_1)) {
                ackCounts.put(ak, Integer.valueOf(++ackCount));
            }
        }
        fips.put(FaultInjection.MSG_ACKCOUNT_PROP, Integer.valueOf(ackCount));
        String faultstr =  null;
        switch (ackType) {
             case ClusterGlobals.MB_MSG_CONSUMED:
                 faultstr =  ((txnID == null) ? "":
                             FaultInjection.MSG_REMOTE_ACK_TXNCOMMIT);
                 break;
             case ClusterGlobals.MB_MSG_TXN_PREPARE:
                 faultstr = FaultInjection.MSG_REMOTE_ACK_TXNPREPARE;
                 break;
             case ClusterGlobals.MB_MSG_TXN_ROLLEDBACK:
                 faultstr = FaultInjection.MSG_REMOTE_ACK_TXNROLLBACK;
        }

        fi.checkFaultAndExit(fprefix+faultstr+fstage, fips, 2, false);
    }
}
