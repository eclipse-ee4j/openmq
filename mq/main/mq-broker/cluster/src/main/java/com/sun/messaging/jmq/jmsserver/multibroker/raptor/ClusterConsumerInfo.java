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
 * @(#)ClusterConsumerInfo.java	1.11 07/23/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import java.io.*;
import java.nio.*;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;

/**
 * An instance of this class is intended to be used one direction only 
 * either Consumers -> GPacket or GPacket -> Consumers (see assertions)
 */

public class ClusterConsumerInfo
{
    private Logger logger = Globals.getLogger();
	private static final long ConsumerVersionUID = 99353142765567461L;

    private static final String PROP_PREFIX_PENDING_TID = "PENDING-TID:"; //4.5.2.2, 4.4u2p8
    private static final String PROP_PREFIX_PENDING_TID_MID_DCT = "PENDING_TID-MID-DCT:"; //5.0
    private static final String PROP_PENDING_MESSAGES = "pendingMessages"; // <= 4.5
    private static final String MID_DCT_SEPARATOR = "#";

    private Cluster c;
    private Collection consumers = null;
    private Map pendingMsgs = null;
    private boolean cleanup = false;
    private GPacket pkt = null;

    private ClusterConsumerInfo(Collection consumers, Cluster c) {
        this.consumers = consumers;
        this.c = c;
    }

    private ClusterConsumerInfo(Consumer consumer, Map pendingMsgs, boolean cleanup, Cluster c) {
        Set s = new HashSet();
        s.add(consumer);
        this.consumers = s;
        this.c = c;
        this.pendingMsgs = pendingMsgs;
        this.cleanup = cleanup;
    }
    
    private  ClusterConsumerInfo(GPacket pkt, Cluster c) {
        this.pkt = pkt;
        this.c = c;
    }

    public static ClusterConsumerInfo newInstance(Collection consumers, Cluster c) {
        return new ClusterConsumerInfo(consumers, c);
    }

    public static ClusterConsumerInfo newInstance(Consumer consumer, Cluster c) {
        return new ClusterConsumerInfo(consumer, null, false, c);
    }

    public static ClusterConsumerInfo newInstance(Consumer consumer, Map pendingMsgs,
                                                  boolean cleanup, Cluster c) {
        return new ClusterConsumerInfo(consumer, pendingMsgs, cleanup, c);
    }

    public static ClusterConsumerInfo newInstance(GPacket pkt, Cluster c) { 
        return new ClusterConsumerInfo(pkt, c);
    }

    public GPacket getGPacket(short protocol) {
        return getGPacket(protocol, -1, null); 
    }

    public GPacket getGPacket(short protocol, int subtype) {
        return getGPacket(protocol, subtype, null);
    }

    public GPacket getGPacket(short protocol, int subtype,  BrokerAddress broker) {
        assert ( consumers != null );
        assert ( protocol == ProtocolGlobals.G_NEW_INTEREST ||
                 protocol == ProtocolGlobals.G_INTEREST_UPDATE );

        if (protocol == ProtocolGlobals.G_INTEREST_UPDATE) {
        assert ( subtype == ProtocolGlobals.G_NEW_PRIMARY_INTEREST || // not effectively used ?
                 subtype == ProtocolGlobals.G_REM_INTEREST ||
                 subtype == ProtocolGlobals.G_DURABLE_DETACH );
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(protocol);
        gp.putProp("C", Integer.valueOf(consumers.size()));
        if (broker != null && pendingMsgs != null && pendingMsgs.size() > 0) {
            TransactionUID tid = null;
            LinkedHashMap mm = null;
            SysMessageID sysid = null;
            StringBuffer sb = null;
            StringBuffer sb45 = null;
            StringBuffer oldsb = null;
            Map m = (Map)pendingMsgs.get(broker);
            if (m != null) {
                oldsb = new StringBuffer();
                Map.Entry pair = null;
                Iterator itr0 = m.entrySet().iterator();
                while (itr0.hasNext()) {
                    pair = (Map.Entry)itr0.next();
                    tid = (TransactionUID)pair.getKey();
                    mm = (LinkedHashMap)pair.getValue();
                    sb = new StringBuffer();
                    sb45 = new StringBuffer();
                    Map.Entry entry = null;
                    Iterator itr = mm.entrySet().iterator();
                    while (itr.hasNext()) {
                        entry = (Map.Entry)itr.next();
                        sysid = (SysMessageID)entry.getKey();
                        Integer deliverCnt = (Integer)entry.getValue();
                        sb.append(sysid).append(MID_DCT_SEPARATOR+
                            (deliverCnt == null ? 0:deliverCnt)).append(" ");
                        sb45.append(sysid).append(" ");
                        oldsb.append(sysid).append(" ");
                    }
                    if (sb.length() > 0) {
                        gp.putProp(PROP_PREFIX_PENDING_TID_MID_DCT+
                           (tid == null ? "":tid), String.valueOf(sb.toString()));
                        gp.putProp(PROP_PREFIX_PENDING_TID+
                           (tid == null ? "":tid), String.valueOf(sb45.toString()));
                    }
                }
                //To be removed when clustering the old protocol (< 500) broker no longer supported
                gp.putProp(PROP_PENDING_MESSAGES, oldsb.toString());
            }
        }
        if (cleanup) {
            gp.putProp("cleanup", Boolean.valueOf(true));
        }
        if (c != null) c.marshalBrokerAddress(c.getSelfAddress(), gp);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        switch (protocol) {
            case ProtocolGlobals.G_NEW_INTEREST:

            try {
                ClusterManager cm = Globals.getClusterManager();
                int csize = 1;
                if (cm != null) {
                    csize = cm.getConfigBrokerCount();
                    if (csize <= 0) {
                        csize = 1;
                    }
                }
                int i = 0;
                Iterator itr = consumers.iterator();
                while (itr.hasNext()) {
                    i++;
                    Consumer c = (Consumer) itr.next();
                    int prefetch = c.getPrefetchForRemote()/csize;
                    if (prefetch <= 0) {
                        prefetch = 1;
                    }
                    gp.putProp(String.valueOf(c.getConsumerUID().longValue())+":"
                                       +Consumer.PREFETCH, Integer.valueOf(prefetch));
                    writeConsumer(c, dos);
                    if (!(c instanceof Subscription)) {
                        continue;
                    }
                    ChangeRecordInfo cri = 
                        ((Subscription)c).getCurrentChangeRecordInfo(
                                          ProtocolGlobals.G_NEW_INTEREST);
                    if (cri == null) {
                        continue;
                    }
                    gp.putProp("shareccSeq"+i, cri.getSeq());
                    gp.putProp("shareccUUID"+i, cri.getUUID());
                    gp.putProp("shareccResetUUID"+i, cri.getResetUUID());
                }
                dos.flush();
                bos.flush();
            }
            catch (IOException e) { /* Ignore */ }

            gp.setPayload(ByteBuffer.wrap(bos.toByteArray()));
            break;

            case ProtocolGlobals.G_INTEREST_UPDATE:

            gp.putProp("T", Integer.valueOf(subtype));
            try {
                Iterator itr = consumers.iterator();
                while (itr.hasNext()) {
                    Consumer c = (Consumer)itr.next();
                    writeConsumerUID(c.getConsumerUID(), dos);
                }
                dos.flush();
                bos.flush();
            }
            catch (IOException e) { /* Ignore */ }

            gp.setPayload(ByteBuffer.wrap(bos.toByteArray()));
            break;
        }

        return gp;
    }

    public int getConsumerCount() {
        assert ( pkt !=  null ); 
		return ((Integer)pkt.getProp("C")).intValue();
    }

    public ChangeRecordInfo getShareccInfo(int i) {
        if (pkt.getProp("shareccSeq"+i) == null) {
            return null;
        }
        ChangeRecordInfo cri =  new ChangeRecordInfo();
        cri.setSeq((Long)pkt.getProp("shareccSeq"+i));
        cri.setUUID((String)pkt.getProp("shareccUUID"+i));
        cri.setResetUUID((String)pkt.getProp("shareccResetUUID"+i));
        cri.setType(pkt.getType());
        return cri;
    }

    public int getSubtype() {
        assert ( pkt != null );

        short type = pkt.getType();
        assert ( type == ProtocolGlobals.G_INTEREST_UPDATE );

        return ((Integer)pkt.getProp("T")).intValue();
    }

    public Iterator getConsumers() throws Exception {
        assert ( pkt !=  null ); 

        short type = pkt.getType();
        assert ( type == ProtocolGlobals.G_NEW_INTEREST );

        return new ConsumerIterator(pkt, pkt.getPayload().array(), getConsumerCount(), 
                                    c.unmarshalBrokerAddress(pkt));
    }

    public Iterator getConsumerUIDs() throws Exception {
        assert ( pkt !=  null ); 

        short type = pkt.getType();
        assert ( type == ProtocolGlobals.G_INTEREST_UPDATE );

        return new ProtocolConsumerUIDIterator(pkt.getPayload().array(), getConsumerCount(),
                                               c.unmarshalBrokerAddress(pkt));
    }

    /**
     * @return null if empty
     */
    public Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> getPendingMessages() {
        assert ( pkt !=  null ); 

        LinkedHashMap<TransactionUID, LinkedHashMap<SysMessageID, Integer>> m = 
             new LinkedHashMap<TransactionUID, LinkedHashMap<SysMessageID, Integer>>();
        String key = null, val = null;
        String tidstr = null;
        TransactionUID tid = null;
        LinkedHashMap<SysMessageID, Integer> sysiddcnts = null;
        Iterator itr = pkt.propsKeySet().iterator();
        while (itr.hasNext()) {
            key = (String)itr.next();
            tid = null;
            if (key.startsWith(PROP_PREFIX_PENDING_TID_MID_DCT)) {
                tidstr = key.substring(PROP_PREFIX_PENDING_TID_MID_DCT.length());
                if (tidstr.length() > 0) {
                    tid = new TransactionUID(Long.parseLong(tidstr));
                } 
                val = (String)pkt.getProp(key);
                if (val == null || val.length() == 0) { 
                    continue;
                }
                sysiddcnts = parsePendingMsgs(val);
                if (m.get(tid) != null) {
                    throw new RuntimeException("Unexpected "+
                        PROP_PREFIX_PENDING_TID_MID_DCT+
                        " content: duplicated entries("+m.get(tid)+
                        ", "+sysiddcnts+") for "+tid+", "+m);
                }
                m.put(tid, sysiddcnts);
            } 
        }
        if (m.size() > 0) {
            return m;
        }
        //check 4.5 patch protocol
        key = null;
        val = null;
        tidstr = null;
        tid = null;
        sysiddcnts = null;
        itr = pkt.propsKeySet().iterator();
        while (itr.hasNext()) {
            key = (String)itr.next();
            tid = null;
            if (key.startsWith(PROP_PREFIX_PENDING_TID)) {
                tidstr = key.substring(PROP_PREFIX_PENDING_TID.length());
                if (tidstr.length() > 0) {
                    tid = new TransactionUID(Long.parseLong(tidstr));
                } 
                val = (String)pkt.getProp(key);
                if (val == null || val.length() == 0) { 
                    continue;
                }
                sysiddcnts = parsePendingMsgs(val);
                if (m.get(tid) != null) {
                    throw new RuntimeException("Unexpected "+
                        PROP_PREFIX_PENDING_TID+
                        " content: duplicated entries("+
                        m.get(tid)+", "+sysiddcnts+") for "+tid+", "+m) ;
                }
                m.put(tid, sysiddcnts);
            }
        }
        if (m.size() > 0) {
            return m;
        }
        //check 4.5 protocol
        val = (String)pkt.getProp(PROP_PENDING_MESSAGES);
        if (val == null || val.length() == 0) {
            return null;
        }
        sysiddcnts = parsePendingMsgs(val); 
        m.put(null, sysiddcnts);
        return m;
    }
        
    private LinkedHashMap<SysMessageID, Integer> parsePendingMsgs(String val) {
        LinkedHashMap<SysMessageID, Integer> pms = 
            new LinkedHashMap<SysMessageID, Integer>();
        StringTokenizer st = new StringTokenizer(val, " ", false);
        while (st.hasMoreTokens()) {
           String s = (String)st.nextToken();
           if (s != null && !s.trim().equals("")) {
               Integer deliverCnt = Integer.valueOf(0);
               int ind = s.lastIndexOf(MID_DCT_SEPARATOR);
               if (ind != -1 && s.length() > (ind+1)) {
                   try {
                       deliverCnt = Integer.valueOf(s.substring(ind+1));
                   } catch (Exception e) {
                       deliverCnt = Integer.valueOf(0);
                       logger.log(Logger.WARNING, e.toString()+" - "+s);
                   }
               } 
               if (ind == -1) {
                   pms.put(SysMessageID.get(s), deliverCnt);
               } else {
                   pms.put(SysMessageID.get(s.substring(0, ind)), deliverCnt);
               }
           }
        }
        return pms;
    }

    public boolean isCleanup() {
        assert ( pkt !=  null ); 
        Boolean b = (Boolean)pkt.getProp("cleanup");
        if (b != null) {
            return b.booleanValue();
        }
        return false;
    }

    public boolean isConfigSyncResponse() {
        assert ( pkt != null );

        boolean b = false;
        if (pkt.getProp("M") != null) {
            b = ((Boolean) pkt.getProp("M")).booleanValue();
        }
        return b;
    }

    public boolean needReply() {
        assert ( pkt != null );
        return pkt.getBit(pkt.A_BIT);
    }


    public static void writeConsumer(Consumer consumer, DataOutputStream dos)
                       throws IOException
    {
        String destName = consumer.getDestinationUID().getName();
        ConsumerUID id = consumer.getConsumerUID();
        String durableName = null;
        String clientID = null;
        String selstr = consumer.getSelectorStr();
        boolean noLocalDelivery = consumer.getNoLocal();
        boolean isQueue = consumer.getDestinationUID().isQueue();
        boolean isReady = true;
        boolean setMaxCnt = false;
        int position = consumer.getLockPosition();;
        int maxcnt = 1;
        boolean jmsshare = false;
        String ndsubname = null;
 
        if (consumer instanceof Subscription ) {
            Subscription s = (Subscription)consumer;
            maxcnt = s.getMaxNumActiveConsumers();
            setMaxCnt = true;
            jmsshare = s.getJMSShared();
            durableName = s.getDurableName();
            if (jmsshare && durableName == null) {
                ndsubname = s.getNDSubscriptionName();
            }
            clientID = s.getClientID();
            if (! s.isActive()) {
                isReady = false;
            }
        }
        dos.writeLong(ConsumerVersionUID); // version
        dos.writeUTF(destName);
        dos.writeBoolean(id != null);
        if (id != null) {
            writeConsumerUID(id, dos);
        }
        dos.writeBoolean(clientID != null);
        if (clientID != null) {
            dos.writeUTF(clientID);
        }
        dos.writeBoolean(durableName != null);
        if (durableName != null) {
            dos.writeUTF(durableName);
        }
        dos.writeBoolean(selstr != null);
        if (selstr != null) {
            dos.writeUTF(selstr);
        }
        dos.writeBoolean(isQueue);
        dos.writeBoolean(noLocalDelivery);
        dos.writeBoolean(isReady);
        dos.writeBoolean(setMaxCnt);
        if (setMaxCnt)
            dos.writeInt(maxcnt);
        dos.writeInt(position);
        dos.writeBoolean(jmsshare);
        dos.writeBoolean(ndsubname != null);
        if (ndsubname != null) {
            dos.writeUTF(ndsubname);
        }
    }

    public static Consumer readConsumer(DataInputStream dis) throws IOException
    {
        Logger logger = Globals.getLogger();
        ConsumerUID id = null;
        String destName = null;
        String clientID = null;
        String durableName = null;
        String selstr = null;
        boolean isQueue;
        boolean noLocalDelivery;
        //boolean consumerReady;
        int sharedcnt;
        int position;

        long ver = dis.readLong(); // version
        if (ver != ConsumerVersionUID) {
            throw new IOException("Wrong Consumer Version " + ver + " expected " + ConsumerVersionUID);
        }
        destName = dis.readUTF();
        boolean hasId = dis.readBoolean();
        if (hasId) {
            id = readConsumerUID(dis);
        }
        boolean hasClientID = dis.readBoolean();
        if (hasClientID) {
            clientID = dis.readUTF();
        }
        boolean hasDurableName = dis.readBoolean();
        if (hasDurableName) {
            durableName = dis.readUTF();
        }

        boolean hasSelector = dis.readBoolean();
        if (hasSelector) {
            selstr = dis.readUTF();
        }

        isQueue = dis.readBoolean();
        noLocalDelivery = dis.readBoolean();
        //consumerReady = dis.readBoolean();
        dis.readBoolean();

        boolean sharedSet = false;
        sharedcnt = 1;
        try {
            sharedSet = dis.readBoolean();
            if (sharedSet == true) {
                sharedcnt = dis.readInt();
            } 
        } catch (Exception ex) {
            // do nothing prevents failures with old brokers
        }

        position = -1;
        try {
            position = dis.readInt();
        } catch (Exception ex) {
            // do nothing prevents failures with old brokers
        }

        //5.0
        boolean jmsshare = false;
        String ndsubname = null; 
        try {
            jmsshare = dis.readBoolean(); 
            boolean hasndsubname = dis.readBoolean();
            if (hasndsubname) {
                ndsubname = dis.readUTF();
            }
        } catch (Exception ex) {
            // do nothing prevents failures with old brokers
        }

        try {
            DestinationUID dest = DestinationUID.getUID(destName, isQueue);
            if (durableName != null) {
                Subscription sub = Subscription.findCreateDurableSubscription(
                                       clientID, durableName, (sharedcnt != 1),
                                       jmsshare, dest, selstr, noLocalDelivery, 
                                       false,  id, Integer.valueOf(sharedcnt));
                return sub;
            } else {
                if (sharedSet) { /* non-durable subscriber */
                    Subscription sub = Subscription.findCreateNonDurableSubscription(
                                         clientID, selstr, ndsubname, (sharedcnt != 1),
                                         jmsshare, dest, noLocalDelivery, id, 
                                         Integer.valueOf(sharedcnt)); 
                    return sub;
                } else {
                    Consumer c = Consumer.newConsumer(dest, selstr, noLocalDelivery, id);
                    c.setLockPosition(position);
                    return c;
                }
            }
         } catch (SelectorFormatException ex) {
             logger.logStack(Logger.WARNING, "Got bad selector["+selstr + "] " , ex);
             IOException ioe = new IOException(ex.getMessage());
             ioe.initCause(ex);
             throw ioe;
         } catch (BrokerException ex) {
             if (ex.getStatusCode() == Status.CONFLICT ||
                 ex instanceof ConsumerAlreadyAddedException) {
                 logger.log(Logger.WARNING, ex.getMessage());
             } else {
                 logger.logStack(Logger.WARNING, ex.getMessage(), ex);
             }
             IOException ioe = new IOException(ex.getMessage());
             ioe.initCause(ex);
             throw ioe;
         }
    }


    public static void writeConsumerUID(ConsumerUID uid, DataOutputStream dos)
                                                            throws IOException
    {
        dos.writeLong(uid.longValue()); // UID write
        dos.writeLong((uid.getConnectionUID() == null ? 0 :
                                   uid.getConnectionUID().longValue()));
        BrokerAddress brokeraddr= uid.getBrokerAddress();
        if (brokeraddr == null) brokeraddr = Globals.getMyAddress();

        if (brokeraddr == null) {
            // XXX Revisit and cleanup : This method may be called
            // before cluster initialization only during persistent
            // store upgrade. i.e. from -
            // FalconProtocol.upgradeConfigChangeRecord()
            // At that time, Globals.getMyAddress() returns null.
            // Hence this kludge...
            try {
            brokeraddr =
            new com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected.BrokerAddressImpl();
            }
            catch (Exception e) {}
        }

        brokeraddr.writeBrokerAddress(dos); // UID write
    }


    public static ConsumerUID readConsumerUID(DataInputStream dis)
          throws IOException
    {
        long id = dis.readLong(); // UID write
        ConnectionUID conuid = new ConnectionUID(dis.readLong());
        BrokerAddress tempaddr = Globals.getMyAddress();
        BrokerAddress brokeraddr = (BrokerAddress)tempaddr.clone();
        brokeraddr.readBrokerAddress(dis); // UID write
        ConsumerUID cuid = new ConsumerUID(id);
        cuid.setConnectionUID(conuid);
        cuid.setBrokerAddress(brokeraddr);
        return cuid;
    }

    public static GPacket getReplyGPacket(short protocol, int status) {
        GPacket gp = GPacket.getInstance();
        gp.setType(protocol);
        gp.putProp("S", Integer.valueOf(status));
        return gp;
    }

}

class ConsumerIterator implements Iterator
{
    private int count = 0;
    private int count_read = 0;
    private DataInputStream dis = null;
    private BrokerAddress from = null;
    private GPacket gp = null;

    public ConsumerIterator(GPacket gp, byte[] payload, int count, BrokerAddress from) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        dis = new DataInputStream(bis);
        this.count = count;
        this.count_read = 0;
        this.from = from;
        this.gp = gp;
    }

    public boolean hasNext() { 
        if (count_read < 0) throw new IllegalStateException("ConsumerUID");  
        return count_read < count;
    }

    /**
     * Caller must catch RuntimeException and getCause
     */
    public Object next() throws RuntimeException {
        try {

        Consumer c =  ClusterConsumerInfo.readConsumer(dis);
        Integer prefetch = (Integer)gp.getProp(String.valueOf(
                                               c.getConsumerUID().longValue())+
                                                          ":"+Consumer.PREFETCH);
        if (prefetch != null) {
            c.setRemotePrefetch(prefetch.intValue());
        }
        if (from != null) {
            c.getConsumerUID().setBrokerAddress(from);
        }
        count_read++;
        return c;

        } catch (IOException e) {

        Throwable ex = e.getCause();
        if (ex instanceof ConsumerAlreadyAddedException) {
            count_read++;
            throw new RuntimeException(ex);
        }
        count_read = -1;
        throw new RuntimeException(e);

        }
    }

    public void remove() {
        throw new UnsupportedOperationException("Not supported");
    }
}

