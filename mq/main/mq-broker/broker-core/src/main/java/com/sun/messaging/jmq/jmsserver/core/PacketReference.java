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
 * @(#)PacketReference.java	1.201 08/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.util.*;
import java.lang.ref.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.jmsserver.GlobalProperties;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.memory.MemoryGlobals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;

/**
 * This class contains the references for a packet which is passed
 * around the system.
 *
 * An optimization of this class would allow messages to be 
 * retrieved from the persistent store (if they are persistent)
 *
 */

public class PacketReference implements Sized, Ordered
{

    private static FaultInjection FI = FaultInjection.getInjection();

    private static ConsumerUID queueUID = null;

    /**
     * Controls if we should enable the fix for bug 6196233 and prepend the
     * message ID with "ID:"
     */
    private static boolean PREPEND_ID =
        Globals.getConfig().getBooleanProperty(
            Globals.IMQ + ".fix.JMSMessageID", false);

    private boolean commit2pwait = Globals.getConfig().getBooleanProperty(
                                   Globals.IMQ+".cluster.2pcommitAckWaitReply", false);

    static {
        queueUID = new ConsumerUID(true /* empty */);
        queueUID.setShouldStore(true);
        
    }


    private static boolean DEBUG = false;

    private static boolean DEBUG_CLUSTER_TXN =
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.txn") || DEBUG;

    /**
     * set once store() is called
     */
    private boolean isStored = false;

    private boolean neverStore = false;

    /**
     * ets once store + interests are saved
     */
    private boolean isStoredWithInterest = false;

    private HashMap attachedData = null;

    /**
     * has packet been destroyed
     */
    private boolean destroyed = false;


    /**
     * message id for the packet
     */
    private SysMessageID msgid;


    transient ConnectionUID con_uid;

    /**
     * time the packet reference was created (entered the system)
     */
    private long creationtime;

    /**
     * time the packet reference was last accessed
     */
    private long lastaccesstime;

    /**
     * sequence # (used for sorting queue browsers)
     */
    private int sequence;

    /**
     * if the message is persisted
     */
    private boolean persist;


    /**
     * determines if the message should remain even if its expired
     * (if the lbit count is > 0)
     */
    private Set lbit_set = null;

    private boolean sendMessageDeliveredAck = false;

    /**
     * size of the packet data 
     */
    private long size;

    /**
     * message properties (null if swapped)
     */
    private Hashtable props;

    private HashMap headers;

    /**
     * original packet or SoftReference (null if swapped)
     */
    private Object pktPtr;

    /**
     * priority of the packet
     */
    private int priority = 4;

    /**
     * isQueue setting
     */
    boolean isQueue = false;


    /**
     * flag called when a packet "should" be removed
     * but is is remaining in memory (because the lbit
     * is set on the packet
     */ 
    boolean invalid = false;


    /** 
     * For remote message reference and its replacement ref only 
     */
    boolean overrided = false;
    Object lockObject = new Object();
    boolean canLock = false;
    boolean locked = false;
    boolean overriding = false;
    Thread lockOwner = null;

    private Object destroyRemoteLock = new Object();
    private List<Thread> destroyRemoteReadLocks = 
                          new ArrayList<Thread>();
    private Thread destroyRemoteWriteLockThread = null;

    /**
     * destination uid
     */
    DestinationUID destination = null;

    private ExpirationInfo expireInfo = null;
    private boolean expired = false;


    MessageDeliveryTimeInfo deliveryTimeInfo = null;

    /**
     * timestamp on the packet (for sorting)
     */
    long timestamp = 0;

    /**
     * transaction id
     */
    TransactionUID transactionid;


    /**
     * override redeliver
     */
    transient boolean overrideRedeliver = false;


    transient BrokerAddress addr = null;


    transient Destination d = null;

    transient String clientID = null;


    int interestCnt;
    int deliveredCnt;
    int ackCnt;
    int deadCnt;

    Map ackInfo = null;

    private static final int INITIAL = 0;
    private static final int ROUTED = 1;
    private static final int DELIVERED = 2;
    private static final int CONSUMED = 3;
    private static final int ACKED = 4;
    private static final int DEAD = 5;

    /**
     * Indicates if this message is in delivery or has been delivered to client
     */
    Map inDelivery = new ConcurrentHashMap(); 

    /**
     * true if the message is to be removed and need to be prevented delivering to client
     */
    boolean inRemoval = false; 
    boolean inReplacing = false; 

    ConsumerUID lastDead = null;

    private PartitionedStore pstore = null;

    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
        }
    }
    
    public PartitionedStore getPartitionedStore() {
        return pstore;
    }

    Object order = null; 

    public Object getOrder() {
        return order;
    }
    public void setOrder(Object o) {
        this.order = o;
    }

    static class ConsumerMessagePair
    {
        private ConsumerUID uid;
        private int state = INITIAL;
        private int redeliverCnt = 0;
        private boolean stored;
        private String deadComment = null;
        private Reason deadReason = null;
        private Throwable deadException = null;
        private long timestamp = 0;
        private String deadBroker = null;


        public ConsumerMessagePair(ConsumerUID uid,
                  boolean stored) {
            this.uid = uid;
            this.stored = stored;
        }

        public synchronized int setRedeliver(int n) {
            redeliverCnt = n;
            return redeliverCnt;
        }

        public synchronized int incrementRedeliver() {
            redeliverCnt ++;
            return redeliverCnt;
        }

        public synchronized int incrementRedeliver(int n) {
            redeliverCnt += n;
            return redeliverCnt;
        }

        public synchronized int decrementRedeliver() {
            redeliverCnt --;
            return redeliverCnt;
        }
        
        public synchronized boolean setState(int state) {
            if (this.state == state) {
                return false;
            }
            this.state = state;
            timestamp = System.currentTimeMillis();
            return true;
        }

        public synchronized int getState() {
            return state;
        }

        public synchronized boolean compareAndSetState(int state, int expected) {
            if (this.state != expected) {
                   return false;
            }
            timestamp = System.currentTimeMillis();
            this.state = state;
            return true;
        }

        public synchronized boolean compareState(int expected) {
            return this.state == expected;
        }

        public synchronized boolean compareStateGT(int expected) {
            return this.state > expected;
        }

        public synchronized boolean compareStateLT(int expected) {
            return this.state < expected;
        }

        public synchronized boolean setStateIfLess(int state, int max) {
            if (compareStateLT(max)) {
                return setState(state);
            }
            return false;
        }

        public boolean isStored() {
            return stored;
        }

        public int getRedeliverCount() {
            return redeliverCnt;
        }

        public void setRedeliverCount(int cnt) {
            redeliverCnt = cnt;
        }

        public void setDeadComment(String str) {
             deadComment = str;
        }
        public void setDeadReason(Reason r) {
             deadReason = r;
        }
        public String getDeadComment() {
             return deadComment;
        }
        public Reason getDeadReason() {
            return deadReason;
        }
        public Throwable getDeadException() {
            return deadException;
        }

        public void setDeadException(Throwable thr) {
             deadException = thr;
        }
        public void setDeadBroker(String bkr) {
             deadBroker = bkr;
        }
        public String getDeadBroker() {
            return deadBroker;
        }

        public String toString() {
            return ": CMPair["+ uid.longValue()  + ":" + stateToString(state) 
                    + ":" + stored + "] - " + timestamp;
        }
    }

    public static ConsumerUID getQueueUID() {
        return queueUID;
    }

    public static PacketReference createReferenceWithDestination(
             PartitionedStore ps,
             Packet p, Destination dest, Connection con) 
             throws BrokerException {
         PacketReference ref = createReference(ps, p, dest.getDestinationUID(), con);
         if (dest.isDMQ()) {
             ref.clearDeliveryTimeInfo();
         }
         return ref;
    }

    public static PacketReference createReference(PartitionedStore ps,
             Packet p, DestinationUID duid, Connection con) 
             throws BrokerException {
        PacketReference pr = new PacketReference(ps, p, duid, con);
        if (con != null && 
            (pr.getExpireInfo() != null || 
             pr.getDeliveryTimeInfo() != null)) {
            con.checkClockSkew(pr.getTime(), pr.getTimestamp(), 
                     pr.getExpireTime(), pr.getDeliveryTime());
        }
        return pr;
    }

    public static PacketReference createReference(PartitionedStore ps,
                  Packet p, Connection con) throws BrokerException {
        return createReference(ps, p, (DestinationUID)null, con);
    }

    public static void moveMessage(PartitionedStore storep,
                       PacketReference oldLoc,
                       PacketReference newLoc, Set targets)
                       throws BrokerException, IOException {

        if (targets == null) {
            // get from the original message
            throw new RuntimeException("Internal error: moving message"
                      + " to null targets not supported");
        } 
        if (!oldLoc.isStored && oldLoc.persist) {
            newLoc.store(targets);
            return;
        }

        ConsumerUID[] uids = null;
        int[] states = null;
        ReturnInfo info = calculateConsumerInfo(targets, oldLoc.persist);
        newLoc.ackInfo = info.ackInfo;
        newLoc.ackCnt = info.ackInfo.size();
        if (info.uids == null || info.uids.length == 0) {
            // nothing to store XXXremove old? 
            newLoc.neverStore = true;
            newLoc.isStored = false;
            newLoc.isStoredWithInterest = false;
            return;
        }
        if (oldLoc.isStored && oldLoc.persist && !oldLoc.neverStore) {
            storep.moveMessage(newLoc.getPacket(),
                 oldLoc.getDestinationUID(), 
                 newLoc.getDestinationUID(),
                 info.uids, info.states, Destination.PERSIST_SYNC);
            newLoc.isStored = true;
            newLoc.isStoredWithInterest = true;
            oldLoc.isStored = false;
            oldLoc.isStoredWithInterest = false;
        } else if (oldLoc.persist) {
            storep.storeMessage(newLoc.getDestinationUID(),
                newLoc.getPacket(),
                info.uids, info.states, Destination.PERSIST_SYNC);
            newLoc.isStored = true;
            newLoc.isStoredWithInterest = true;
            newLoc.neverStore = false;
        } else {
            newLoc.isStored = false;
            newLoc.isStoredWithInterest = false;
            newLoc.neverStore = true;
        }
        return;
    }


    static class ReturnInfo
    {
        ConsumerUID uids[];
        int states[];
        int interestCnt;
        Map ackInfo;
    }

// LKS- BUG SOMEWHERE BELOW
/*
 * this method is called when a set of consumers are being routed
*/
    private static ReturnInfo calculateConsumerInfo(Collection consumers, boolean checkStored)
    {
 
        if (consumers.isEmpty())
            return null;

        ReturnInfo ri = new ReturnInfo(); 
        ri.ackInfo = Collections.synchronizedMap(new HashMap());
        ArrayList storedConsumers = (checkStored ? new ArrayList() : null);
        Iterator itr = consumers.iterator();

        int count = 0;
        while (itr.hasNext()) {
           Object o = itr.next();
           ConsumerUID cuid = null;
           if (o instanceof Consumer) {
               cuid = ((Consumer)o).getStoredConsumerUID();
           } else {
               cuid = (ConsumerUID)o;
           }
           ri.interestCnt ++;

           boolean store = false;
           if (storedConsumers != null && cuid.shouldStore()) {
               storedConsumers.add(cuid);
               store = true;
            }

           ConsumerMessagePair cmp = new ConsumerMessagePair(cuid, store);
           ri.ackInfo.put(cuid, cmp);
        }
      
        if (storedConsumers != null) {
            ConsumerUID [] type = new ConsumerUID[0];
            ri.uids = (ConsumerUID[])storedConsumers.toArray(type);
            ri.states = new int[ri.uids.length];
            for (int i=0; i < ri.states.length; i ++) {
                ri.states[i] = PartitionedStore.INTEREST_STATE_ROUTED;
            }
         }
         return ri;
    }


    /**
     * create a new PacketReference object
     */
    private PacketReference(PartitionedStore ps, Packet pkt,
                            DestinationUID duid, Connection con) 
                            throws BrokerException {
        this.pstore = ps;
        this.creationtime = System.currentTimeMillis();
        this.lastaccesstime = creationtime;
        this.msgid = (SysMessageID)pkt.getSysMessageID().clone();
        this.isQueue = pkt.getIsQueue();
        this.persist = pkt.getPersistent();
        this.priority = pkt.getPriority();
        this.sequence = pkt.getSequence();
        this.timestamp = pkt.getTimestamp();
        if (pkt.getRedelivered()) {
            overrideRedeliver = true;
        }
        if (con != null) {
            this.clientID = (String)con.getClientData(IMQConnection.CLIENT_ID);
        }
        setExpireTime(pkt.getExpiration());
        this.size = pkt.getPacketSize();
        String d  = pkt.getDestination();
        this.con_uid = (con == null ? null : con.getConnectionUID());
        if (duid != null) {
            destination = duid;
        } else {
            destination = DestinationUID.getUID(d, isQueue);
        }
        long tid = pkt.getTransactionID();
        synchronized(this) {
            setPacketObject(false, pkt);
        }
        if (tid != 0) {
            transactionid = new TransactionUID(tid);
        } else {
            transactionid = null;
        }
        setDeliveryTime(pkt.getDeliveryTime());
    }

    public void setSequence(int seq) {
        this.sequence = seq;
    }


    public String getClientID() {
        return clientID;
    }


    private ConsumerMessagePair getAck(Object obj) {
        ConsumerUID cuid = null;
        if (obj instanceof ConsumerUID) {
            cuid = (ConsumerUID)obj;
        } else if (obj instanceof Consumer) {
            cuid = ((Consumer)obj).getConsumerUID();
        } else {
            throw new RuntimeException("Bogus ID");
        }
        if (ackInfo == null)  {
            throw new RuntimeException("No AckInfo for message "+ getSysMessageID());
        }
        return (ConsumerMessagePair) ackInfo.get(cuid);
    }

    /**
     * If this method returns, caller is resposible to call
     * postAcnkowledgedRemoval()
     */
    protected boolean removeConsumerForDeliveryDelayed(Consumer c) {
        if (ackInfo == null) {
            return false; 
        }
        //ConsumerUID cuid = c.getConsumerUID();
        ConsumerUID suid = c.getStoredConsumerUID();
        ConsumerMessagePair cmp = (ConsumerMessagePair)ackInfo.get(suid);
        if (cmp == null) {
            return false;
        }
        ackInfo.remove(suid);
        boolean rm = false;
        synchronized(this) {
            interestCnt--;
            rm = (interestCnt == 0); 
        }
        if (!isLocal() && rm) { 
            acquireDestroyRemoteReadLock();
            synchronized(this) {
                rm = (interestCnt == 0); 
            }
            if (!rm) {
                clearDestroyRemoteReadLock();
            }
        }
        return rm;
    }

    protected Collection getAllConsumerUIDForDeliveryDelayed() {
        return ackInfo.keySet();
    }

/*
 *-------------------------------------------------------------------
 * 
 *   ACCESS METHODS
 *
 *--------------------------------------------------------------------
 */

    public void setDestination(Destination d) {
        this.d = d;
    }

    public Destination getDestination() {
         if (d == null) {
             Destination[] ds = Globals.getDestinationList().
                                  getDestination(pstore, destination);
             d = ds[0];
             if (d != null && d.isDMQ()) {
                 clearDeliveryTimeInfo();
             }
         }
         return d;
    }

    public boolean isStored() {
        return isStored;
    }


    public void setBrokerAddress(BrokerAddress a) {
        addr = a;
        if (!isLocal()) {
            clearDeliveryTimeInfo();
        }
    }

    public BrokerAddress getBrokerAddress() {
        return addr;
    }

    public boolean isLocal() {
        return addr == null || addr == Globals.getMyAddress();
    }

    public String toString() {
        return "PacketReference["+msgid+"]";
    }

    Set deliveredMsgAcks = new HashSet();

    public synchronized boolean getMessageDeliveredAck(ConsumerUID uid) {
        return !deliveredMsgAcks.isEmpty() && 
              deliveredMsgAcks.contains(uid);
    }

    public synchronized ConsumerUID[] getConsumersForMsgDelivered()
    {
        return (ConsumerUID [])deliveredMsgAcks.toArray(
                   new ConsumerUID[deliveredMsgAcks.size()]);
    }

    public synchronized void removeMessageDeliveredAck(ConsumerUID uid) {
        deliveredMsgAcks.remove(uid);
    }

    public synchronized void addMessageDeliveredAck(ConsumerUID uid) {
        deliveredMsgAcks.add(uid);
    }

    LinkedHashMap<ConsumerUID, LinkedHashMap<ConsumerUID, ConnectionUID>>
        remoteConsumerUIDs = new LinkedHashMap<ConsumerUID, 
                 LinkedHashMap<ConsumerUID, ConnectionUID>>();

    /**
     * At least destroyRemoteLock reader lock is required to call this method
     */
    private void addRemoteConsumerUID(ConsumerUID suid, 
                                      ConsumerUID cuid, 
                                      ConnectionUID connuid) {
        synchronized(remoteConsumerUIDs) {
            LinkedHashMap<ConsumerUID, ConnectionUID> m = remoteConsumerUIDs.get(suid);
            if (m == null) {
                m = new LinkedHashMap<ConsumerUID, ConnectionUID>();
                remoteConsumerUIDs.put(suid, m);
            }
            m.put(cuid, connuid);
        }
    }

    public void removeRemoteConsumerUID(ConsumerUID suid, ConsumerUID cuid) {
        acquireDestroyRemoteReadLock();
        try {
            synchronized(remoteConsumerUIDs) {
                LinkedHashMap<ConsumerUID, ConnectionUID> m = remoteConsumerUIDs.get(suid);
                if (m != null) {
                    m.remove(cuid);
                    if (m.isEmpty()) {
                        remoteConsumerUIDs.remove(suid);
                    }
                }
            }
        } finally {
            clearDestroyRemoteReadLock();
        }
    }

    /**
     * Caller must ensure isLocal false to call this method
     */
    public boolean isNoAckRemoteConsumers() {
        acquireDestroyRemoteReadLock();
        try {
            Iterator<Map.Entry<ConsumerUID, LinkedHashMap<ConsumerUID, ConnectionUID>>> itr = null;
            Map.Entry<ConsumerUID, LinkedHashMap<ConsumerUID, ConnectionUID>> pair = null;
            LinkedHashMap<ConsumerUID, ConnectionUID> m = null;
            Iterator<ConsumerUID> itr1 = null;
            ConsumerUID cuid = null, suid = null;
            synchronized(remoteConsumerUIDs) {
                itr = remoteConsumerUIDs.entrySet().iterator();
                while (itr.hasNext()) {
                    pair = itr.next();
                    suid = pair.getKey();
                    m = pair.getValue();
                    if (m == null || m.isEmpty()) {
                        continue;
                    }
                    try {
                        if (isAcknowledged(suid)) {
                            continue;
                        }
                    } catch (Exception e) { 
                        continue;
                    }
                    itr1 = m.keySet().iterator();
                    while (itr1.hasNext()) {
                        cuid = itr1.next();
                        if (!cuid.isNoAck()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } finally {
            clearDestroyRemoteReadLock();
        }
    }

    /**
     * At least destroyRemoteLock reader lock is required to call this method
     */
    public boolean isLastRemoteConsumerUID(ConsumerUID suid, ConsumerUID cuid) {
        ConsumerUID lastuid = null;
        synchronized(remoteConsumerUIDs) {
            LinkedHashMap<ConsumerUID, ConnectionUID> m = remoteConsumerUIDs.get(suid);
            if (DEBUG) {
                Globals.getLogger().log(Logger.INFO, 
                "PacketReference.isLastRemoteConsumerUID("+suid+", "+cuid+"): "+
                 m+", ref@"+this.hashCode()+"="+this);
            }
            if (m == null) {
                return true;
            }
            Iterator<ConsumerUID> itr = m.keySet().iterator();
            while (itr.hasNext()) {
                lastuid = itr.next(); 
                if (DEBUG) {
                    Globals.getLogger().log(Logger.INFO, 
                    "PacketReference.isLastRemoteConsumerUID("+suid+", "+cuid+
                    "): lastuid="+lastuid+", ref@"+this.hashCode()+"="+this);
                }
            }
            return (lastuid == null || lastuid.equals(cuid));
        }
    }

    public Map<ConsumerUID, ConnectionUID> getRemoteConsumerUIDs() {
        LinkedHashMap<ConsumerUID, ConnectionUID> allm = 
            new LinkedHashMap<ConsumerUID, ConnectionUID>();
        synchronized(remoteConsumerUIDs) {
            Iterator<LinkedHashMap<ConsumerUID, ConnectionUID>> itr =
                               remoteConsumerUIDs.values().iterator();
            LinkedHashMap<ConsumerUID, ConnectionUID> m = null;
            while (itr.hasNext()) {
                m = itr.next();
                if (m != null && !m.isEmpty()) {
                    allm.putAll(m);
                }
            }
        }
        return allm;
    }

    private void setExpireTime(long time) {
        if (time == 0) {
            expireInfo = null;
        } else {
            expireInfo = new ExpirationInfo(msgid, time);
        }
    }
    public long getExpireTime() {
        if (expireInfo == null) {
            return 0L;
        }
        return expireInfo.getExpireTime();
    }

    public ExpirationInfo getExpireInfo() {
        return expireInfo;
    }

    private void setDeliveryTime(long time) {
        if (time == 0L) {
            deliveryTimeInfo = null;
        } else {
            deliveryTimeInfo = new MessageDeliveryTimeInfo(msgid, time);
        }
    }
    public long getDeliveryTime() {
        if (deliveryTimeInfo == null) {
            return 0L;
        }
        return deliveryTimeInfo.getDeliveryTime();
    }

    public boolean isDeliveryDue() {
        if (deliveryTimeInfo == null) {
            return true;
        }
        return deliveryTimeInfo.isDeliveryDue();
    }

    public MessageDeliveryTimeInfo getDeliveryTimeInfo() {
        getDestination();
        return deliveryTimeInfo;
    }

    public void overrided() {
        overrided = true;
    }
    public boolean isOverrided() {
        return overrided;
    }

    public void overriding() {
        overriding = true;
    }
    public boolean isOverriding() {
        return overriding;
    }

    /**
     * only called by the ref creator before put into MT access
     */
    public void lock() {
        canLock = true;
        locked = true;
        lockOwner = Thread.currentThread(); 
    }

    public void unlock() {
        synchronized(lockObject) {
            locked = false;
            lockOwner = null;
            lockObject.notifyAll();
        }
    }

    public PacketReference checkLock(boolean wait) {
        if (!canLock) return this;
        synchronized(lockObject) {
            if (locked && (lockOwner != null && Thread.currentThread() == lockOwner)) return this;
            while (wait && locked) {
                Globals.getLogger().log(Logger.INFO, "Wait for reference : "+this);
                try {
                lockObject.wait(5000);
                } catch (InterruptedException e) {
                }
            }
            if (locked) return null;
        }
        return this;
    }

    public void setInvalid() {
        invalid = true;
    }

    private Packet getPacketObject() {

        assert (pktPtr ==  null ||
               pktPtr instanceof SoftReference
               || pktPtr instanceof Packet) : pktPtr;

        Object ptr = pktPtr;
        if (ptr == null) return null;
        if (ptr instanceof SoftReference) {
            return (Packet)((SoftReference)pktPtr).get();
        }
        return (Packet)pktPtr;
    }

    private void setPacketObject(boolean soft, Packet p) {

        assert Thread.holdsLock(this);
        if (soft) {
            pktPtr = new SoftReference(p);
        } else {
            pktPtr = p;
        }
    }

    private void makePacketSoftRef() {
        assert Thread.holdsLock(this);
        Object ptr = pktPtr;
        if (ptr != null && ptr instanceof Packet) {
            pktPtr = new SoftReference(ptr);
        }
    }

    public void setNeverStore(boolean s) {
        neverStore = s;
    }

    public boolean getNeverStore() {
        return neverStore;
    }

    public long byteSize() {
        return size;
    }

    public void setStoredWithInterest(boolean withInterest) {
        isStoredWithInterest = withInterest;
    }

    public synchronized void setLoaded() {
        isStored = true;
        makePacketSoftRef();
    }

    public ConnectionUID getProducingConnectionUID()
    {
        return con_uid;
    }

    public int getPriority() {
        return priority;
    }

    public TransactionUID getTransactionID() {
        return transactionid;
    }

    public boolean getIsQueue() {
        return this.isQueue;
    }

    public long getTime() {
        return creationtime;
    }

    public long getSequence() {
        return sequence;
    }

    public synchronized void setLastBit(ConsumerUID id)
        throws IllegalStateException
    {
        if (isInvalid() && isDestroyed()) {
            throw new IllegalStateException(
                   Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION, 
                        "reference has been destroyed"));
        }
        if (lbit_set == null) {
            lbit_set = new HashSet();
        }
        lbit_set.add(id);

    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("AckCount", String.valueOf(ackCnt));
        ht.put("DeadCount", String.valueOf(deadCnt));
        ht.put("ackInfo[#]", String.valueOf( ackInfo.size()));
        ht.put("interestCount", String.valueOf(interestCnt));
        ht.put("inRemoval", Boolean.valueOf(inRemoval));
        ht.put("inReplacing", Boolean.valueOf(inReplacing));
        Vector vt = new Vector();
        List l = null;
        synchronized(this) {
            ht.put("inDeliverySize", String.valueOf(inDelivery.size()));
            l =  new ArrayList(inDelivery.keySet());
        }
        Iterator itr =  l.iterator();
        while (itr.hasNext()) {
            vt.add(itr.next());
        }
        ht.put("InDelivery", vt);

        vt = new Vector();
        ConsumerMessagePair cmp = null;
        synchronized(ackInfo) {
            itr = ackInfo.keySet().iterator();
            while (itr.hasNext()) {
                Object key = itr.next();
                try {
                    cmp = getAck(key);
                } catch (Exception e) {
                    vt.add(key+":"+e.toString());
                    continue;
                }
                vt.add((cmp == null ? key+":null":cmp.toString()));
            }    
        }
        if (!vt.isEmpty())
            ht.put("Acks",vt);
        return ht;
    }
        

    public synchronized boolean isLast(ConsumerUID id) {
        if (lbit_set == null) {
            return false;
        }
        return lbit_set.contains(id);
    }

    public synchronized void removeIsLast(ConsumerUID id) {
        if (lbit_set == null) {
            return;
        }
        lbit_set.remove(id);
        if (lbit_set.isEmpty() && invalid) { // clean up, lbit is gone
            destroy(); // at next expiration check, we will be
                       // removed from the packet store
        }
    }

    public synchronized boolean getLBitSet() {
        return lbit_set != null && !lbit_set.isEmpty();
    }

    public DestinationUID getDestinationUID() {
        return destination;
    }

    public String getDestinationName() {
        return destination.getName();
    }

    public synchronized Packet getPacket()
    {
        Packet pkt = getPacketObject();
        if (pkt != null || destroyed) {
            return pkt;
        }

//        assert persist;

        if (!persist) {
            return null;
        }

        pkt = recoverPacket();

        assert pkt != null;

        setPacketObject(true, pkt);
        return pkt;
    }

    private Packet recoverPacket() 
    {
        // recover from the database
        assert Thread.holdsLock(this);
        assert pktPtr == null || 
            (pktPtr instanceof SoftReference &&
              ((Reference)pktPtr).get() == null);

        try {
            Packet p = pstore.getMessage(destination, msgid);
            assert p != null;
            return p;

        } catch (BrokerException ex) {
            assert false :ex;
            Globals.getLogger().logStack(Logger.ERROR,
                 BrokerResources.E_LOAD_MSG_ERROR,
                 msgid.toString(), ex);
        }
            
        return null;
    }

    public synchronized Hashtable getProperties() 
        throws ClassNotFoundException
    {
        if (destroyed || invalid) {
            return new Hashtable();
        }
        this.lastaccesstime = System.currentTimeMillis();
        Packet pkt = getPacketObject();
        if (props == null && !destroyed) {
            if (pkt == null) {
                pkt = getPacket();
            }
            try {
                props = pkt.getProperties(); 
            } catch (IOException ex) {
                // no properties
                Globals.getLogger().log(Logger.INFO,"Internal Exception: " , ex);
                props = new Hashtable();
            } catch (ClassNotFoundException ex) {
                assert false; // should not happen
                throw ex;
            }
        }
        return props;
     }

    /*
     * @return HashMap of headers or empty HashMap  
     */
    public synchronized HashMap getHeaders() {
        if (headers == null) {
            if (destroyed || invalid) {
                return new HashMap();
            }
            Packet pkt = getPacketObject();
            headers = new HashMap();
            if (pkt == null) {
                // Fix for CR 6891615
                // reload packet if it has been GCd
                pkt = getPacket();
                if (DEBUG) {
                    Globals.getLogger().log(Logger.DEBUG, 
                        "reloaded packet for non-destroyed message "+msgid);
                }
                if (pkt == null) {
                    Globals.getLogger().log(Logger.ERROR,
                        "could not reload packet for non-destroyed message "+msgid);
                        return headers;
                }
            }
            headers.put("JMSPriority", Integer.valueOf(priority));

            /*
             * XXX If the fix for bug 6196233 is enabled, then 
             * prepend the messageid with "ID:".
             */
            headers.put("JMSMessageID",
               (PREPEND_ID ? "ID:" : "") + msgid.toString());
            headers.put("JMSTimestamp", Long.valueOf(timestamp));
            headers.put("JMSDeliveryMode",
                 (pkt.getPersistent() ? "PERSISTENT" :
                     "NON_PERSISTENT"));
            headers.put("JMSCorrelationID", pkt.getCorrelationID());
            headers.put("JMSType", pkt.getMessageType());
        }
        return headers;
    }


    public  SysMessageID getSysMessageID() 
    {
        return msgid;
    }

    public String getSysMessageIDString() {
        // returns the header value
        Map headers = getHeaders();
        return (String)headers.get( "JMSMessageID");

    }

    public  long getCreateTime() 
    {
        return creationtime;
    }

    public  long getLastAccessTime() 
    {
        return lastaccesstime;
    }

    public  long getTimestamp() 
    {
        return timestamp;
    }

    public void setTimestamp(long time)
    {
        timestamp = time;
    }


    public  boolean isPersistent() 
    {
        return persist;
    }

    public  void overridePersistence(boolean persist) 
    {
        this.persist = persist;
    }

    public  long getSize() 
    {
        return size;
    }


    public boolean isDestroyed()
    {
        return destroyed;
    }


    public synchronized boolean isInvalid()
    {
        return invalid;
    }

    /**
     * If the following changes, change caller side accordingly
     *
     * @return false either inReplacing true or inDelivery true;
     *          
     */
    public synchronized boolean checkDeliveryAndSetInRemoval() {
        if (destroyed || invalid ||
            ackInfo == null /*not routed yet*/) {
            inRemoval = true;
            return true;
        }

        if (ackCnt + deadCnt >= interestCnt) {
            inRemoval = true;
            return true;
        }
        if (inReplacing) {
            return false;
        }
        if (inDelivery.size() > 0) {
            return false;
        }
        inRemoval = true;
        return true;
    }

    protected synchronized boolean checkDeliveryAndSetInReplacing() {
	if (destroyed || invalid || inRemoval ||
            isExpired() || inReplacing) {
            return false;
	}

        if (ackCnt + deadCnt >= interestCnt) {
            return false;
	}

        if (inDelivery.size() > 0) {
            return false;
	}
        inReplacing = true;
	return true;
    }

    protected synchronized void clearInReplacing() {
        inReplacing = false;
    }

    public synchronized boolean checkRemovalAndSetInDelivery(ConsumerUID sid) {
        if (destroyed || invalid || inRemoval || isExpired() || inReplacing) {
            return false;
        }
        inDelivery.put(sid, sid);
        return true;
    }

    public synchronized void removeInDelivery(ConsumerUID sid) {
        inDelivery.remove(sid);
    }


    public void overrideExpireTime(long expire) {
        setExpireTime(expire);
    }

    public boolean isExpired() {
        if (expireInfo == null) {
            return false;
        }
        if (expired) {
            return true;
        }
        boolean expiring = expireInfo.isExpired();
        if (expiring) {
           // change to soft reference
           synchronized(this) {
               makePacketSoftRef();
           }
        }
        return expiring;
    }

    protected void clearExpireInfo() {
        expired = true;
        expireInfo = null;
    }

    protected void clearDeliveryTimeInfo() {
        deliveryTimeInfo = null;
    }

    public boolean equals(Object obj) {
        if (msgid == null) {
            return msgid == obj;
        }
        if (obj instanceof PacketReference) {
            return msgid.equals(((PacketReference)obj).msgid);
        }
        return false;
    }

    public int hashCode() {
        return msgid == null ? 0 : msgid.hashCode();
    }
        
    public boolean matches(DestinationUID uid)
    {
        return true;
    }  

/*
 *-------------------------------------------------------------------
 * 
 *   Acknowledgement handling methods
 *
 *--------------------------------------------------------------------
 */


    public static String stateToString(int state) {
        switch (state) {
            case INITIAL:
                return "INITIAL";
            case ROUTED:
                return "ROUTED";
            case DELIVERED:
                return "DELIVERED";
            case CONSUMED:
                return "CONSUMED";
            case ACKED:
                return "ACKED";
            case DEAD:
                return "DEAD";
        }
        return "UNKNOWN";
    }


    /**
     * stores the persistent message (if necessary)
     * (may be called by transactions to store the 
     * message)
     */

    public synchronized void store()  
        throws BrokerException {

        if (!destroyed && persist && !neverStore && !isStored) {
            // persist indicated IF we want to
            // store the message and may be
            // different from the actual
            // state on the message
            assert pktPtr instanceof Packet;
            try {
                pstore.storeMessage(destination,
                    (Packet)getPacket(), Destination.PERSIST_SYNC);
                makePacketSoftRef();
            } catch (IOException ex) {
                throw new BrokerException(ex.toString(), ex);
            } catch (Exception ex) {
                Globals.getLogger().logStack(Logger.ERROR,
                    BrokerResources.W_MESSAGE_STORE_FAILED,
                    msgid.toString(), ex);
                throw new BrokerException(ex.toString(), ex);
            }
            isStored = true;
        }
    }

    public synchronized void setIsStored() {
        isStored = true;
    }

    public void store(Collection consumers) throws BrokerException {

    	if (destroyed || pktPtr == null) {
            return;
        }
        if (isStoredWithInterest) {
            // done already
            return;
        }
        
        boolean botherToStore=!neverStore && persist;

        ReturnInfo info = calculateConsumerInfo(consumers, botherToStore);
        if (ackInfo != null)  {
            ackInfo.putAll(info.ackInfo);
        } else {
            ackInfo = info.ackInfo;
        }
        interestCnt = info.ackInfo.size();
        
        if (!botherToStore) {
            return;
        }
        if (info.uids == null || info.uids.length == 0) {
            // nothing to store
            neverStore = true;
            return;
        }
            
        try {
            if (isStored && !neverStore && persist) {
                pstore.storeInterestStates(
                    destination,
                    msgid, info.uids, info.states, 
                    Destination.PERSIST_SYNC, getPacket());
            } else {
                pstore.storeMessage(destination,
                        (Packet)pktPtr,
                         info.uids, info.states, Destination.PERSIST_SYNC);
                synchronized(this) {
                    makePacketSoftRef();
                }
            }
        } catch (IOException ex) {
                throw new BrokerException(
                    ex.toString(), ex);
        } catch (Exception ex) {
                Globals.getLogger().logStack(Logger.ERROR,
                    BrokerResources.W_MESSAGE_STORE_FAILED,
                    msgid.toString(), ex);
                throw new BrokerException(
                    ex.toString(), ex);
        } 
        isStored = true;
        isStoredWithInterest = true;
        assert interestCnt != 0;
    }

    public ConsumerUID[] getRoutingForStore(Collection consumers)
			throws BrokerException {

		if (destroyed || pktPtr == null) {
			return null;
		}

		boolean botherToStore = !neverStore && persist;
		if (!botherToStore)
			return null;

		if (consumers.isEmpty())
			return null;

		List<ConsumerUID> storedConsumers = null;
		Iterator itr = consumers.iterator();

		while (itr.hasNext()) {
			Object o = itr.next();
			ConsumerUID cuid = null;
			if (o instanceof Consumer) {
				cuid = ((Consumer) o).getStoredConsumerUID();
			} else {
				cuid = (ConsumerUID) o;
			}

			if (cuid.shouldStore()) {
				if (storedConsumers == null) {
					storedConsumers = new ArrayList<ConsumerUID>();
				}
				storedConsumers.add(cuid);
			}
		}
		if (storedConsumers == null)
			return null;

		return storedConsumers.toArray(
                    new ConsumerUID[storedConsumers.size()]);

	}

    /**
     * This method is called for a newly created PacketReference 
     * (before queue to destination destMessages) from cluster router
     */
    public void storeRemoteInterests(Collection<Consumer> interests,
        Map<ConsumerUID, Integer> deliveryCnts) 
        throws BrokerException {
        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO, 
            "storeRemoteInterests("+interests+", "+deliveryCnts+") for ref@"+
             this.hashCode()+"="+this+"["+getDestination()+"]");
        }
        try {
            store(interests);
        } finally {
            Consumer interest = null;
            ConsumerUID suid = null, uid = null;
            Iterator<Consumer> itr = interests.iterator();
            while (itr.hasNext()) {
                interest = itr.next();
                suid = interest.getStoredConsumerUID(); 
                uid = interest.getConsumerUID();
                addRemoteConsumerUID(suid, uid, uid.getConnectionUID());
                Integer dct = deliveryCnts.get(uid); 
                if (dct != null && dct.intValue() > 0) {
                    updateForJMSXDeliveryCount(suid, dct.intValue(), true);
                }
            }
        }
    }
    

    /**
     * This method is called while holding destroyRemoteLock writer lock
     */
    public synchronized void addRemoteInterests(Collection<Consumer> interests) {
        ConsumerMessagePair oldcmp, newcmp; 
        Consumer interest = null;
        ConsumerUID suid = null, uid = null;
        Iterator<Consumer> itr = interests.iterator();
        while (itr.hasNext()) {
            interest = itr.next();
            suid = interest.getStoredConsumerUID(); 
            uid = interest.getConsumerUID();
            if (ackInfo == null) {
                ackInfo = Collections.synchronizedMap(new HashMap());
            }
            oldcmp = (ConsumerMessagePair)ackInfo.get(suid);
            if (oldcmp == null || oldcmp.getState() >= ACKED) {
                    interestCnt++;
            } else {
                overriding();
            }

            newcmp = new ConsumerMessagePair(suid, false);
            newcmp.setState(ROUTED);
            ackInfo.put(suid, newcmp);

            addRemoteConsumerUID(suid, uid, uid.getConnectionUID());
        }
    }

    /**
     * called for messages which have already been
     * loaded from the database
     */
    public void update(ConsumerUID[] uids, int[] states) {
        update(uids, states, false);
    }

    public void update(ConsumerUID[] uids, int[] states, boolean store) {
        assert isStored;
        assert uids != null;
        assert states != null;
        assert uids.length == states.length;
        assert ackInfo == null;
        assert uids.length != 0;


        synchronized (this) {
            interestCnt +=uids.length;
        }
          

        for (int i=0; i < uids.length; i ++) {
           ConsumerUID cuid = uids[i];

           assert uids[i] != null;

           assert states[i] >= PartitionedStore.INTEREST_STATE_ROUTED &&
                  states[i] <= PartitionedStore.INTEREST_STATE_ACKNOWLEDGED;

           if (states[i] == PartitionedStore.INTEREST_STATE_ACKNOWLEDGED) {
               // left over
               continue;
           }

           if (ackInfo == null)
               ackInfo = Collections.synchronizedMap(new HashMap());

           ConsumerMessagePair cmp = new
               ConsumerMessagePair(cuid, true);

           ackInfo.put(cuid, cmp);
           cmp.setState(states[i] == PartitionedStore.INTEREST_STATE_ROUTED
                 ? ROUTED : CONSUMED);

           if (store) {
                try {
                   pstore.storeInterestStates(destination,
                         msgid,
                         uids, states, Destination.PERSIST_SYNC,
                         (Packet)pktPtr);
                } catch (Exception ex) {
                }
            }

        }

        assert interestCnt != 0;
            
    }

    public void debug(String prefix)
    {
        if (prefix == null)
            prefix = "";
        Globals.getLogger().log(Logger.INFO,prefix +"Message " + msgid);
        Globals.getLogger().log(Logger.INFO,prefix + "size " + ackInfo.size());
        Iterator itr = ackInfo.values().iterator();
        while (itr.hasNext()) {
            ConsumerMessagePair ae = (ConsumerMessagePair)itr.next();
            Globals.getLogger().log(Logger.INFO,prefix + "\t " + ae);
        }
    }
    

    /**
     * called when a consumer received
     * the specific message for delivery
     */
    public void routed(ConsumerUID intid) 
        throws BrokerException, IOException
    {

        // NOTE: the caller is responsible for
        // passing the "right" intid - for
        // queues this is the generic ID, for
        // durable subscribers, it is the subscription
        // ID

        if (destroyed || invalid) {
            Globals.getLogger().log(Logger.DEBUG,"route on destroyed ref "
                 + msgid + ":" + intid);
            return; // destroyed
        }


        // nothing to store
        // this just indicates that we've passed
        // the message off to a consumer

        // the state of the message should have
        // already been stored w/ routeTable

        // additional logic may be added in the future

    }

    public int getCompleteCnt() {
        return ackCnt + deadCnt;
    }

    public synchronized boolean isAcknowledged() {
        return ackCnt + deadCnt >= interestCnt;
    }

    public int getDeliverCnt() {
        return deliveredCnt;
    }

    

    public SysMessageID replacePacket(Hashtable props, byte[] bytes)
        throws BrokerException, IOException
    {
        //no messages can be delivered

        if (deliveredCnt > 0)
            throw new BrokerException("Unable to replace already delivered message");
        
        if (ackCnt > 0)
            throw new BrokerException("Unable to replace partially acknowledged message");

        Packet oldp = getPacket();
        Packet newp = new Packet();
        newp.fill(oldp);
        newp.updateSequenceNumber();
        newp.updateTimestamp();
        newp.setMessageBody(bytes);

        headers = null;
        Hashtable oldprops = null;
        try {
            oldprops = getProperties();
            if (oldprops == null)
                oldprops = new Hashtable();
            if (props != null) {
                Globals.getLogger().log(Logger.DEBUG,"Warning although properties "
                    + "have been changed on the message it will "
                    + "not be rerouted");
                oldprops.putAll(props);
            }
            oldprops.put("JMSOrigMessageID",
                (PREPEND_ID ? "ID:" : "") + msgid.toString());
            HashMap headers = getHeaders();
            if (headers != null) {
                Hashtable ht = new Hashtable();
                Iterator itr = headers.entrySet().iterator();
                Map.Entry me = null;
                while (itr.hasNext()) {
                    me = (Map.Entry)itr.next();
                    String key = (String)me.getKey();
                    Object value = me.getValue();
                    if (value != null)
                        ht.put(key, value);
                }
                
            }
            newp.setProperties(oldprops);
        } catch (Exception ex) {
            Globals.getLogger().logStack(Logger.WARNING, 
            Globals.getBrokerResources().getKString(
            BrokerResources.X_REPLACE_PROPS_FOR_REPLACE_MSG,
            this, ex.getMessage()), ex);
        }

        setPacketObject(true /* soft*/, newp);
        if (isStoredWithInterest) {
            int cnt = 0;
            ConsumerUID uids[] = null;
            int states[] = null;
            if (ackInfo != null) {
                synchronized(ackInfo) {
                    // ok count stored entries
                    Iterator itr = ackInfo.values().iterator();
                    while (itr.hasNext()) {
                        ConsumerMessagePair ae = (ConsumerMessagePair)itr.next();
                        if (ae.stored) cnt ++;
                    }
                    uids= new ConsumerUID[cnt];
                    states = new int[cnt];
                    //ok allocate arrays
                    int i =0;
                    itr = ackInfo.values().iterator();
                    while (itr.hasNext()) {
                        ConsumerMessagePair ae = (ConsumerMessagePair)itr.next();
                        if (ae.stored) {
                            uids[i] = ae.uid;
                            states[i] = ae.state;
                        }
                        i ++;
                    }
                }
                pstore.storeMessage(destination,
                    (Packet)newp,
                    uids, states, Destination.PERSIST_SYNC);
            } else {
                pstore.storeMessage(destination,
                    (Packet)newp, Destination.PERSIST_SYNC);
            }
        } else if (isStored) {
            pstore.storeMessage(destination,
                (Packet)newp, Destination.PERSIST_SYNC);
        } else /* not stored */ {
        }
        setPacketObject(persist, newp);
        headers = null;
        props = null;
        SysMessageID id = msgid;
        this.msgid = (SysMessageID)newp.getSysMessageID().clone();
        if (isStored || isStoredWithInterest) {
            Globals.getLogger().log(Logger.DEBUG,"Cleaning up the old replaced message");
            pstore.removeMessage(destination,
                     id,Destination.PERSIST_SYNC );
        }
        return msgid;
        
    }

    /**
     * If this method return true, caller is responsible to
     * call postAcknowledgedRemoval()
     *
     * called just before the message is written
     * to the wire for a consumer
     */
    public boolean delivered(ConsumerUID intid, ConsumerUID storedid,
             boolean sync, boolean store) 
        throws BrokerException, IOException
    {
        // NOTE: the caller is responsible for
        // passing the "right" storedid - for
        // queues this is the generic ID, for
        // durable subscribers, it is the subscription
        // ID
        if (destroyed || invalid) {
            Globals.getLogger().log(Logger.DEBUG,"delivered on destroyed ref "
                 + msgid + ":" + storedid);
            return true; // destroyed
        }

        if (intid.isNoAck()) {
            // immediately ack message when delivered
            return acknowledged(intid, 
                   storedid,
                   sync, store);
        }

        ConsumerMessagePair cmp = getAck(storedid);

        if (cmp == null) {
            // nothing to do
            Globals.getLogger().log(Logger.DEBUG,"Received Unknown delivered:" 
                   +"\n\tStoreUID: " +  storedid 
                   +"\n\tConsumerUID: " + intid 
                   +"\n\tConsumer: " +  Consumer.getConsumer(intid));
            //if (DEBUG)
                debug("\t- ");
            return false;
        }

        // if we are greater than delivered

        if (cmp.compareStateLT(DELIVERED)) {
            synchronized (this) {
                deliveredCnt ++;
             }
        }
        cmp.setStateIfLess(DELIVERED, DELIVERED);        
        if (cmp.isStored() && store) {
                pstore.updateInterestState(
                    destination,
                    msgid, storedid, 
                    PartitionedStore.INTEREST_STATE_DELIVERED, 
                    Destination.PERSIST_SYNC && sync,
                    null, false);
        }

        synchronized (this) {
            // in low memory, free ref explicity
            if (deliveredCnt >= interestCnt &&
                isStored &&
                ((MemoryGlobals.getMEM_FREE_P_ACKED()
                   && persist ) ||
                  (MemoryGlobals.getMEM_FREE_NP_ACKED()
                   && (!persist)))) {
                   unload();
            }
        }

        return false;
    }

    public int getRedeliverCount(ConsumerUID intid) {
        try {
            ConsumerMessagePair cmp = getAck(intid);
            return cmp == null ? 0 : cmp.getRedeliverCount();
        } catch (Exception e) {
             return 0;
        }
    }


    /**
     * called when the client indicates that
     * the message has been consumed (delivered
     * to the specific client side consumer).
     * This method may be triggered by a specific
     * message from the consumer OR when an ack
     * is received in autoack mode
     */
    public void consumed(ConsumerUID intid, boolean sync, boolean delivered) 
        throws BrokerException, IOException {

        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO, 
            "PacketReference.consumed("+intid+", "+sync+", "+delivered+
            ") for ref@"+this.hashCode()+"="+this+"["+getDestination()+"]");
        }

        if (destroyed || invalid) {
            Globals.getLogger().log(Logger.DEBUG,"consumed on destroyed ref "
                 + msgid + ":" + intid);
            return; // destroyed
        }

        if (delivered) {
            if (delivered(intid, intid, sync, true)) {
                clearDestroyRemoteReadLock();
            }
        }

        assert ackInfo != null;

        ConsumerMessagePair cmp = getAck(intid);

        // something went wrong, ignore
        if (cmp == null) {
           // nothing to do
           Globals.getLogger().log(Logger.ERROR,"Internal Error: unknown interest for " 
           + " consumed on " + msgid + intid+", "+getDestination()+", ackInfo="+ackInfo);
           return;
        }

        cmp.incrementRedeliver();
        cmp.setStateIfLess(CONSUMED, CONSUMED);

    }

    public void updateForJMSXDeliveryCount(ConsumerUID suid, int n, boolean set) {
        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO, 
            "PacketReference.updateForJMSXDeliveryCount("+suid+", "+n+", "+set+
            ") for ref@"+this.hashCode()+"="+this+"["+getDestination()+"]");
        }
        if (destroyed || invalid) {
            if (DEBUG) {
            Globals.getLogger().log(Logger.INFO, 
            "updateForJMSXDeliveryCount("+suid+", "+n+", "+set+
            ") on destroyed ref@"+this.hashCode()+"="+this+"["+destination+"]");
            }
            return;
        }

        ConsumerMessagePair cmp = getAck(suid);

        if (cmp == null) {
            // nothing to do
            if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,
            "updateForJMSXDeliveryCount("+suid+", "+n+","+set+"): unknown interest for ref@"+
             this.hashCode()+"="+this+"["+destination+"]ackInfo="+ackInfo);
            }
            return;
        }
        if (cmp.setStateIfLess(DELIVERED, DELIVERED)) {
            if (cmp.isStored() && destination != null) {
                if (DEBUG) {
                Globals.getLogger().log(Logger.INFO,
                "updateForJMSXDeliveryCount("+suid+", "+n+", "+set+") for ref@"+
                 this.hashCode()+"="+this+"["+destination+"]: update delivered in store");
                }
                try {
                    pstore.updateInterestState(
                        destination, msgid, suid, 
                        PartitionedStore.INTEREST_STATE_DELIVERED, 
                        Destination.PERSIST_SYNC && false,
                        null, false);
                } catch (Exception e) {
                    Object[] args = { String.valueOf(n), suid, 
                                      msgid+"["+destination+"]" };
                    Globals.getLogger().logStack(Logger.WARNING, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_STORE_DELIVERED_ON_ADD_DELIVERYCOUNT, args), e);
                }
            }
        }
        if (set) {
            cmp.setRedeliver(n);
        } else {
            cmp.incrementRedeliver(n);
        }
        cmp.setStateIfLess(CONSUMED, CONSUMED);
    }

    public boolean matches(ConsumerUID id) {
        return getAck(id) != null;
    }

    public boolean isAcknowledged(ConsumerUID id) {
        ConsumerMessagePair cmp = getAck(id);
        if (cmp == null) return true;
        return cmp.compareState(ACKED);
    }

    public boolean isDelivered(ConsumerUID id) {
        ConsumerMessagePair cmp = getAck(id);
        if (cmp == null) return true;
        return cmp.compareState(DELIVERED) || cmp.compareState(CONSUMED);
    }

    public boolean removeDelivered(ConsumerUID storedid, boolean decrementCounter)
    {
        if (destroyed || invalid) {
            return true; // destroyed
        }

        ConsumerMessagePair cmp = getAck(storedid);

        // something went wrong, ignore
        if (cmp == null) {
           // nothing to do
           Globals.getLogger().log(Logger.ERROR,"Internal Error: unknown interest for " 
               + " remove consumed on " + msgid
               + storedid);
           return false;
        }

        if (decrementCounter)
            cmp.decrementRedeliver();

        cmp.compareAndSetState(ROUTED, DELIVERED);

        //XXX use destination setting
        return (cmp.getRedeliverCount() >= 20);

    }


    public boolean hasConsumerAcked(ConsumerUID storedid)
    {
        try {
            if (destroyed || invalid) {
                return true; // destroyed
            }    
            ConsumerMessagePair cmp = getAck(storedid);

            
            if ( cmp != null && cmp.getState() != ACKED) {
                return false;
            }
        } catch (Throwable ex) {
            Globals.getLogger().logStack(Logger.ERROR,"Internal Error checking ack" +
                         " on " + msgid + " for " + storedid, ex);
            return false;
        }
        return true;
    }

    /**
     * handle updating acknowledgement of the message
     *
     * @returns if the message should be removed from the persistent list
     */
    public boolean acknowledged(ConsumerUID intid,ConsumerUID storedid,
                   boolean sync, boolean notIgnored) 
        throws BrokerException, IOException
    {
        return acknowledged(intid, storedid, sync, notIgnored,
                            null, null, null, true);
    }

    /**
     * @param ackack whether client requested ackack
     */
    public boolean acknowledged(ConsumerUID intid,ConsumerUID storedid,
                   boolean sync, boolean notIgnored, boolean ackack)
                                throws BrokerException, IOException
    {
        return acknowledged(intid, storedid, sync, notIgnored,
                            null, null, null, ackack);
    }


    /**
     * If this method return true, caller is responsible to 
     * call postAcknowledgedRemoval()  
     *
     * @param ackack whether client requested ackack
     *
     * If the ack is a remote non-transacted ack, waits for remote ack reply only if
     *    sync == true && notIgnored && ackack
     */
    public boolean acknowledged(ConsumerUID intid,ConsumerUID storedid,
                                boolean sync, boolean notIgnored, 
                                TransactionUID tuid, TransactionList translist, 
                                HashMap<TransactionBroker, Object> remoteNotified, boolean ackack) 
                                throws BrokerException, IOException {

        Long txn = (tuid == null ? null : Long.valueOf(tuid.longValue()));
        try {
            if (destroyed || invalid) {
                return true; // destroyed
            }    

            removeInDelivery(storedid);
    
            ConsumerMessagePair cmp = getAck(storedid);
        
            // something went wrong, ignore
            if (cmp == null) {
                // nothing to do
                Globals.getLogger().log(Logger.ERROR,"Internal Error: Received Unknown ack " 
                                        + intid+ " for message "+getSysMessageID());
                Globals.getLogger().log(Logger.ERROR, "AckInfo" + ackInfo.toString());
                boolean rm =  false;
                synchronized (this) {
                    rm = (ackCnt+deadCnt) >= interestCnt;
                }
                if (!isLocal() && rm) {
                    acquireDestroyRemoteReadLock();
                    synchronized(this) {
                        rm = (ackCnt+deadCnt) >= interestCnt;
                    }
                    if (!rm) {
                        clearDestroyRemoteReadLock();
                    }
                }
                return rm;
            }

            // ok ... if setState == false, we were already
            // acked so do nothing
            if (cmp.setState(ACKED)) {
                if (cmp.isStored()) {
                    boolean acked = false;
                    if (Globals.getStore().isJDBCStore()) {
                        acked = pstore.hasMessageBeenAcked(destination, msgid);
                    }
                    if (!acked) {
                        try {
                            //This test may fail to spot that this really is
                            // the last ack because another thread processing an
                            // ack for the same message may not have yet
                            // incremented ackCnt.
                            // However, it should not return a false positive so
                            // should be safe
                            boolean isLastAck = false;
                            synchronized (this) {
                                isLastAck = (ackCnt + 1 + deadCnt) >= interestCnt;
                            }

                            pstore.updateInterestState(destination, msgid,
                                                       storedid,
                                                       PartitionedStore.INTEREST_STATE_ACKNOWLEDGED,
                                                       Destination.PERSIST_SYNC && sync, tuid,
                                                       isLastAck);

                        } catch (Exception ex) {
                            if (ex instanceof BrokerException &&
                                ((BrokerException)ex).getStatusCode() != Status.NOT_ALLOWED &&
                                ((BrokerException)ex).getStatusCode() != Status.NOT_FOUND) {
                                Globals.getLogger().log(Logger.WARNING, 
                                    "Update consumer "+storedid+" state failed for message "+
                                     msgid+": "+ ex.getMessage());
                                throw ex;
                            } else {
                                Globals.getLogger().log(Logger.DEBUGHIGH, 
                                    "Update consumer "+storedid+" state failed for message "+
                                     msgid+": "+ ex.getMessage());
                            }
                        }
                    }
                }
                if (!isLocal()) { // not local
                    if (notIgnored) {
                        if (Globals.getClusterBroadcast().getClusterVersion() <
                            ClusterBroadcast.VERSION_410) {
                            Globals.getClusterBroadcast().
                                acknowledgeMessage(getBrokerAddress(),
                                    getSysMessageID(), intid, 
                                    ClusterBroadcast.MSG_ACKNOWLEDGED, null, false);
                        } else if (txn == null) { 
                            Globals.getClusterBroadcast().
                                acknowledgeMessage(getBrokerAddress(),
                                    getSysMessageID(), intid, 
                                    ClusterBroadcast.MSG_ACKNOWLEDGED, null, (sync && ackack));
                        } else {
                            BrokerAddress raddr = getBrokerAddress();
                            TransactionBroker traddr = new TransactionBroker(raddr);
                            if (remoteNotified.get(traddr) == null) {
                               
                                SysMessageID[] mysysids = { this.getSysMessageID() };
                                ConsumerUID[] myintids = { intid };
                                try {
                                UID txnss = translist.getPartitionedStore().getPartitionID();
                                Globals.getClusterBroadcast().
                                    acknowledgeMessage2P(raddr,
                                                     mysysids, myintids,
                                                     ClusterBroadcast.MSG_ACKNOWLEDGED,
                                                     null, txn, txnss, true, !commit2pwait);
                                remoteNotified.put(traddr, String.valueOf(""));
                                if (commit2pwait) {
                                    translist.completeClusterTransactionBrokerState(tuid,
                                              TransactionState.COMMITTED, raddr, true);
                                }

                                } catch (Exception e) {

                                if (e instanceof BrokerDownException) {
                                    remoteNotified.put(traddr, String.valueOf(""));
                                }
                                if (DEBUG_CLUSTER_TXN) {
                                Globals.getLogger().logStack(Logger.WARNING,
                                    "Notify commit transaction ["+this.getSysMessageID()+
                                    ", "+intid+"]TUID="+txn+" got response: "+e.toString(),  e);
                                } else {
                                Globals.getLogger().log(Logger.WARNING,
                                    "Notify commit transaction ["+this.getSysMessageID()+
                                    ", "+intid+"]TUID="+txn+" got response: "+e.toString(),  e);
                                }

                                } //catch
                                } //if
                            }
                        } else {
                            try {
                                Globals.getClusterBroadcast().
                                    acknowledgeMessage(getBrokerAddress(),
                                                       getSysMessageID(), 
                                                       intid, 
                                                       ClusterBroadcast.MSG_IGNORED, 
                                                       null, false /*no wait ack*/);
                            } catch (BrokerException e) {
                                Globals.getLogger().log(Logger.DEBUG, e.getMessage());
                            }
                        }
                    }
                    
            } else {
                Consumer c = Consumer.getConsumer(intid);
                if (c == null || !c.isValid() || !isLocal()) {
                    // do we want to add a debug level message here
                    // got it while cleaning up
                    // this can only happen on topics and is a
                    // non-fatal error
                    // fixing the timing problem through synchronization
                    // adds too much overhead <- same to !isLocal case
                    boolean rm = false;
                    synchronized (this) {
                        rm = ((ackCnt+deadCnt) >= interestCnt);
                    }
                    if (!isLocal() && rm) {
                        acquireDestroyRemoteReadLock();
                        synchronized(this) {
                            rm = ((ackCnt+deadCnt) >= interestCnt);
                        }
                        if (!rm) {
                            clearDestroyRemoteReadLock();
                        }
                    }
                    return rm;
                } else {
                    Exception e = new Exception("double ack " + cmp);
                    e.fillInStackTrace();
                    Globals.getLogger().logStack(Logger.ERROR, "Received ack twice " +
                         " on " + msgid + " for " + intid + " state is = " + cmp, e );
                    boolean rm  = false;
                    synchronized (this) {
                        rm = ((ackCnt+deadCnt) >= interestCnt);
                    }
                    if (!isLocal() && rm) {
                        acquireDestroyRemoteReadLock();
                        synchronized(this) {
                            rm = ((ackCnt+deadCnt) >= interestCnt);
                        }
                        if (!rm) {
                            clearDestroyRemoteReadLock();
                        }
                    }
                    return rm;
                }
            }

            boolean rm = false;
            synchronized (this) {
                ackCnt ++;
                rm = ((ackCnt+deadCnt) >= interestCnt);
            }
            if (!isLocal() && rm) {
                acquireDestroyRemoteReadLock();
                synchronized (this) {
                    rm = ((ackCnt+deadCnt) >= interestCnt);
                }
                if (!rm) {
                    clearDestroyRemoteReadLock();
                }
            }
            return rm;
        } catch (Throwable thr) {
            if (thr instanceof BrokerDownException) {
                Globals.getLogger().log(Logger.WARNING, Globals.getBrokerResources().getKString(
                                        BrokerResources.W_UNABLE_PROCESS_REMOTE_ACK_BECAUSE,
                                        "["+msgid+"]"+intid+":"+storedid,  thr.getMessage()));
            } else {
                Globals.getLogger().logStack(Logger.ERROR, 
                "Error in processing ack" +" on "+ msgid+ " for " + intid, thr);
            }
            if (thr instanceof BrokerException) throw (BrokerException)thr;
            throw new BrokerException("Unable to process ack", thr);
        }
    }
    
    /**
     * this method is only  called when replaying the transaction log
     */
    public boolean acknowledgedOnReplay(ConsumerUID intid, ConsumerUID storedid) 
    throws BrokerException, IOException {
		try {
			if (destroyed || invalid) {
				return true; // destroyed
			}

			ConsumerMessagePair cmp = getAck(storedid);

			// something went wrong, ignore
			if (cmp == null) {
				// nothing to do
				Globals.getLogger().log(
						Logger.ERROR,
						"Internal Error: Received Unknown ack " + intid
								+ " for message " + getSysMessageID());
				Globals.getLogger().log(Logger.ERROR,
						"AckInfo" + ackInfo.toString());
				synchronized (this) {
					return (ackCnt + deadCnt) >= interestCnt;
				}
			}

			cmp.setState(ACKED);

			ackCnt++;
			return (ackCnt + deadCnt) >= interestCnt;

		} catch (Throwable thr) {
			Globals.getLogger().logStack(
					Logger.ERROR,
					"Error in processing ack" + " on " + msgid + " for "
							+ intid, thr);
			if (thr instanceof BrokerException)
				throw (BrokerException) thr;
			throw new BrokerException("Unable to process ack", thr);
		}

	}


    public void overrideRedeliver() {
        if (!overrideRedeliver) overrideRedeliver = true;
    }

    public boolean getRedeliverFlag(ConsumerUID intid)
    {
        if (destroyed || invalid) {
            Globals.getLogger().log(Logger.DEBUG,"redeliver for destroyed "
                 + msgid + ":" + intid);
            return true; // doesnt matter
        }

        ConsumerMessagePair cmp = getAck(intid);

        // something went wrong, ignore
        if (cmp == null) {
            return false;
        }

        if (overrideRedeliver)
            return true;

        // return true if our state is greater or equal to
        // DELIVERED
        return !cmp.compareStateLT(DELIVERED);
    }


    public boolean getConsumed(ConsumerUID intid) {
        if (destroyed || invalid) {
            Globals.getLogger().log(Logger.DEBUG,"getConsumed for destroyed "
                 + msgid + ":" + intid);
            return true; // doesnt matter
        }

        ConsumerMessagePair cmp = getAck(intid);

        // something went wrong, ignore
        if (cmp == null) {
           // nothing to do
           Globals.getLogger().log(Logger.ERROR,"Internal Error: unknown interest for " 
               + " getConsumed on " + msgid
               + intid);
           return true;
        }

        return cmp.compareState(CONSUMED);
    }



    public synchronized void clear() {
        props = null;
        if (pktPtr instanceof Reference) {
            ((Reference)pktPtr).clear();
            ((Reference)pktPtr).enqueue();
        }
        pktPtr = null;
        msgid = null;
    }

    /**
     * @return false if store msg removal exception
     */
    public boolean remove(boolean onRollback) {
        boolean ret = true;
        if (isStored && !neverStore && persist) {
            try {
                if (FI.FAULT_INJECTION) {
                  if (FI.checkFault(FaultInjection.FAULT_MSG_DESTROY_1_5_EXCEPTION, null)) {
                      FI.unsetFault(FaultInjection.FAULT_MSG_DESTROY_1_5_EXCEPTION);
                      throw new BrokerException("FAULT_INJECTION: "+
                          FaultInjection.FAULT_MSG_DESTROY_1_5_EXCEPTION);
                  }
                  FI.checkFaultAndExit(FaultInjection.FAULT_MSG_DESTROY_1_5_KILL, null, 1, false);
		}

                //
                // removes synchronization when we remove
                // the message (we really dont need it)
                //
                // in the future we may evaulate changing the
                // code be smarter about syncing (if two
                // threads both change the file within a short
                // period of time, we really only need to sync once

                pstore.removeMessage(destination,
                       msgid, 
                      false /*Destination.PERSIST_SYNC*/, onRollback );
                isStored = false;

            } catch (Exception ex) {
                ret = false;
                Object[] args = { msgid.toString(), 
                                  getDestinationUID(), ""+pstore }; 
                Globals.getLogger().logStack(Logger.ERROR,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.E_REMOVE_MSG_FROM_STORE, args), ex);
            }
            isStored = false;
        }
        return ret;
    }

    /**
     * This method must called after 
     *
     * acknowledged()
     * delivered()
     * isDead()
     * markDead()
     *
     * returns true. And if the above calls result removal of the message, this 
     * method must be called after the message removal call
     *
     */
    public final void postAcknowledgedRemoval() {
        if (isLocal()) {
            return;
        }
        clearDestroyRemoteReadLock(); 
    }

    public void acquireDestroyRemoteReadLock() {
        Thread myth = Thread.currentThread(); 
        long totalwaited = 0L;
        long pretime = 0L, curtime = 0L;
        long waitime = 15000L;
        synchronized(destroyRemoteLock) {
            while (destroyRemoteWriteLockThread != null &&
                   myth != destroyRemoteWriteLockThread) {
                curtime = System.currentTimeMillis();
                totalwaited += (pretime == 0L ? 0L:(curtime - pretime));
                if (pretime != 0L) {
                    waitime = 30000L;
                    String[] args = { this.toString(), ""+getDestinationUID(),
                                      "["+destroyRemoteWriteLockThread+
                                      "]("+String.valueOf(totalwaited)+")" };
                    Globals.getLogger().log(Logger.INFO, 
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_WAIT_FOR_REMOTE_REF_READ_LOCK, args));
                }
                pretime = System.currentTimeMillis();
                try {
                    destroyRemoteLock.wait(waitime);
                } catch (Exception e) {
                }
            }
            destroyRemoteReadLocks.add(myth);
        }
    }

    public void clearDestroyRemoteReadLock() {
        Thread myth = Thread.currentThread(); 
        synchronized(destroyRemoteLock) {
            destroyRemoteReadLocks.remove(myth);
            destroyRemoteLock.notifyAll();
        }
    }

    public void acquireDestroyRemoteWriteLock() {
        long totalwaited = 0L;
        long pretime = 0L, curtime = 0L;
        long waitime = 15000L;
        synchronized(destroyRemoteLock) {
            while (!destroyRemoteReadLocks.isEmpty() || 
                   destroyRemoteWriteLockThread != null) {
                curtime = System.currentTimeMillis();
                totalwaited += (pretime == 0L ? 0L:(curtime - pretime));
                if (pretime != 0L) {
                    waitime = 30000L;
                    String[] args = { this.toString(), ""+getDestinationUID(),
                                      "["+destroyRemoteReadLocks+", ["+
                                       destroyRemoteWriteLockThread+
                                      "]]("+String.valueOf(totalwaited)+")" };
                    Globals.getLogger().log(Logger.INFO, 
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_WAIT_FOR_REMOTE_REF_WRITE_LOCK, args));
                }
                pretime = System.currentTimeMillis();
                try {
                    destroyRemoteLock.wait(waitime);
                } catch (Exception e) {
                }
            }
            destroyRemoteWriteLockThread = Thread.currentThread();
        }
    }

    public void clearDestroyRemoteWriteLock() {
        Thread th = Thread.currentThread();
        synchronized(destroyRemoteLock) {
            if (destroyRemoteWriteLockThread == th) {
                destroyRemoteWriteLockThread = null; 
            }
            if (destroyRemoteWriteLockThread == null) {
                destroyRemoteLock.notifyAll();
            }
        }
    }

    /**
     * If this reference is in its Destination.destMessages, this 
     * method must only be called after this reference is removed 
     * from its Destination.destMessages
     */
    public void destroy() {
        assert getLBitSet() == false;
        boolean alreadydestroyed = false;
        synchronized (this) {
            if (destroyed) {
                alreadydestroyed = true;
            }
            destroyed = true;
        }
        synchronized(destroyRemoteLock) {
            destroyRemoteReadLocks.clear();
            destroyRemoteWriteLockThread = null;
            destroyRemoteLock.notifyAll();
        }
        if (alreadydestroyed) {
            return;
        }
        if (deliveryTimeInfo != null) {
            deliveryTimeInfo.cancelTimer();
        }
        if (isStored) {
            try {
                if (Globals.isNewTxnLogEnabled()) {
                    // With txnLog enabled, message will not have been persisted e.g. 
                    // for a topic message with no durable subscribers.
                    // With txnLog, message is only stored if routed ( this is checked with neverStore)
                    if (!neverStore) {
                        pstore.removeMessage(destination, msgid, false);
                    } else {
                        if (Store.getDEBUG()) {
                            Globals.getLogger().log(Logger.DEBUG,
                               "NOT removing msg marked as neverstore");
                        }
                    }
                } else {
                	
                // initial fix for bug 5025164
                // removes synchronization when we remove
                // the message (we really dont need it)
                //
                // in the future we may evaluate changing the
                // code be smarter about syncing (if two
                // threads both change the file within a short
                // period of time, we really only need to sync once
                pstore.removeMessage(destination, msgid, 
                      false /*Destination.PERSIST_SYNC*/ );
                }
                isStored = false;

            } catch (Exception ex) {
                Object[] args = { msgid.toString(), 
                                  getDestinationUID(), ""+pstore }; 
                Globals.getLogger().logStack(Logger.ERROR,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.E_REMOVE_MSG_FROM_STORE, args), ex);
            }
        }
        props = null;
        if (pktPtr instanceof Reference) {
            ((Reference)pktPtr).clear();
            ((Reference)pktPtr).enqueue();
        }
        pktPtr = null;
    }


    void unload() {
        // clears out the reference
        if (pktPtr instanceof SoftReference) {
            ((SoftReference)pktPtr).clear();
        }
    }
    

//------------------------------------------------------------------
//
//          DMQ methods
//------------------------------------------------------------------

    public String getDeadComment() {
        if (lastDead == null)
            return null;
        ConsumerMessagePair cmp = getAck(lastDead);
        return cmp == null ? null: cmp.getDeadComment();
    }
    public int getDeadDeliverCnt() {
        if (lastDead == null)
            return getDeliverCnt();
        ConsumerMessagePair cmp = getAck(lastDead);
        return cmp == null ? getDeliverCnt(): cmp.getRedeliverCount();
    }
    public Reason getDeadReason() {
        if (lastDead == null)
            return null;
        ConsumerMessagePair cmp = getAck(lastDead);
        return cmp == null ? null: cmp.getDeadReason();
    }
    public String getDeadBroker() {
        if (lastDead == null)
            return null;
        ConsumerMessagePair cmp = getAck(lastDead);
        return cmp == null ? null: cmp.getDeadBroker();
    }
    public Throwable getDeadException() {
        if (lastDead == null)
            return null;
        ConsumerMessagePair cmp = getAck(lastDead);
        return cmp == null ? null: cmp.getDeadException();
    }

    /**
     * If this method returns true,  caller is responsible to 
     * call postAcknowledgedRemoval()
     */
    public boolean markDead(ConsumerUID intid, ConsumerUID storedid,
                            String comment, Throwable ex, Reason reason,
                            int redeliverCnt, String broker) 
                            throws BrokerException {
        return markDead(intid, storedid, comment, ex, 
                        reason, redeliverCnt, broker, false);
    }

    /**
     * If this method returns true,  caller is responsible to 
     * call postAcknowledgedRemoval()
     */
    public boolean markDead(ConsumerUID intid, ConsumerUID storedid,
                            String comment, Throwable ex, Reason reason,
                            int redeliverCnt, String broker, boolean remoteDeliveredAck) 
                            throws BrokerException {
        removeInDelivery(storedid);

        ConsumerMessagePair cmp = getAck(storedid);

        if (cmp == null) {
            // nothing to do
            Globals.getLogger().log(Logger.DEBUG,"Received unknown dead message " 
                   + storedid);
            return false;
        }

        if (!isLocal()) { // send remotely and ack
            Hashtable props = new Hashtable();
            if (comment != null)
                props.put(DMQ.UNDELIVERED_COMMENT, comment);
            if (redeliverCnt != -1)
                props.put(Destination.TEMP_CNT, Integer.valueOf(redeliverCnt));
            if (ex != null)
                props.put(DMQ.UNDELIVERED_EXCEPTION, ex);
            if (reason != null)
                props.put("REASON", Integer.valueOf(reason.intValue()));
            if (broker != null)
                props.put(DMQ.DEAD_BROKER, broker);

            if (remoteDeliveredAck) {
                props.put(ClusterBroadcast.MSG_DELIVERED_ACK, Boolean.TRUE);
            }

            Globals.getClusterBroadcast().
                acknowledgeMessage(getBrokerAddress(),
                getSysMessageID(), intid, 
                ClusterBroadcast.MSG_DEAD, props, 
                !(intid.isDupsOK() || intid.isNoAck()));
            cmp.setState(ACKED);
        } else {
            lastDead = storedid;
            cmp.setState(DEAD);
            cmp.setDeadComment(comment);
            cmp.setDeadReason(reason);
            cmp.setDeadException(ex);
            cmp.setDeadBroker(broker);
            if (redeliverCnt > -1)
               cmp.setRedeliverCount(redeliverCnt);
        }
        boolean rm =  false;
        synchronized (this) {
            deadCnt ++;
            rm = (ackCnt + deadCnt >= interestCnt);
        }
        if (!isLocal() && rm) {
            acquireDestroyRemoteReadLock();
            synchronized (this) {
                rm = (ackCnt + deadCnt >= interestCnt);
            }
            if (!rm) {
                clearDestroyRemoteReadLock();
            }
        }
        return rm;
    }

    /**
     * If this method returns true, caller is responsible to
     * call postAcknowledgedRemoval()
     */
    public boolean isDead() {
        boolean rm = false;
        synchronized( this ) {
            rm = (deadCnt > 0 && (ackCnt + deadCnt >= interestCnt));
        } 
        if (!isLocal() && rm) {
            acquireDestroyRemoteReadLock();
            synchronized( this ) {
                rm = (deadCnt > 0 && (ackCnt + deadCnt >= interestCnt));
            }
            if (!rm) {
                clearDestroyRemoteReadLock();
            }
        }
        return rm;
    }
    

    /**
     * TEST CASE FOR VERIFYING REPLACEMEMENT - MUST BE VALIDATED MANUALLY
     **/
    public static void replaceTest() {
    try {
        PartitionedStore ps = Globals.getDestinationList().
                     assignStorePartition(ServiceType.NORMAL, new ConnectionUID(), null);
        DestinationUID duid = new DestinationUID("test", true);
        DestinationUID duid_t = new DestinationUID("test", false);
        ConsumerUID uid1 = new ConsumerUID(1);
        ConsumerUID uid2 = new ConsumerUID(2);
        //ConsumerUID[] uids = {uid1,uid2};

        Consumer consumer1 = new Consumer(duid,null, false,queueUID);
        ArrayList queues = new ArrayList();
        queues.add(consumer1);

        Consumer tconsumer1 = new Consumer(duid_t,null, false,uid1);
        Consumer tconsumer2 = new Consumer(duid_t,null, false,uid2);
        // simple Non-persistent

        ArrayList topics = new ArrayList();
        topics.add(tconsumer1);
        topics.add(tconsumer2);
        try {
        Globals.getDestinationList().createDestination(ps, "test",DestType.DEST_TYPE_QUEUE);
        Globals.getDestinationList().createDestination(ps, "test",DestType.DEST_TYPE_TOPIC);
        } catch (Exception ex) {};
        
        Packet p1 = new Packet();
        Hashtable props = new Hashtable();
        props.put("MyName","MyValue");

        byte[] body1 = {1};
        p1.setProperties(props);
        p1.setMessageBody(body1);
        p1.updateTimestamp();
        p1.updateSequenceNumber();

        System.out.println("----------------------------------");
        System.out.println("Test1: non-persist No Properties");
        System.out.println("----------------------------------");

        System.out.print("Initial Packet " + p1.getSysMessageID());
        // USE CASE 1 basic replace
        //
        // first create original packet
        // then create the packet reference
        // then store it
        // then replace the body
        // then check the contents
        // then clear out the reference and reload
        // then check the contents
        PacketReference ref =  createReference(ps, p1, duid, null);
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get() +"]");
        ref.store(queues);

        byte[] body2 = {2};
        try {
            SysMessageID newid = ref.replacePacket(null, body2);
            System.out.print("New ID " + newid);
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        System.out.println("----------------------------------");
        System.out.println("Test2: persist No Properties");
        System.out.println("----------------------------------");
        p1 = new Packet();
        props = new Hashtable();
        props.put("MyName","MyValue");
        byte[] body3 = {3};
        byte[] body4 = {4};
        p1.setMessageBody(body3);
        p1.setPersistent(true);
        p1.setProperties(props);
        p1.updateTimestamp();
        p1.updateSequenceNumber();

        System.out.print("Initial Packet " + p1.getSysMessageID());
        ref =  createReference(ps, p1, duid, null);
        ref.store(queues);
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        try {
            SysMessageID newid = ref.replacePacket(null, body4);
            System.out.print("New ID " + newid);
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        System.out.println("----------------------------------");
        System.out.println("Test3: Topic and 2 consumerUID");
        System.out.println("----------------------------------");
        p1 = new Packet();
        props = new Hashtable();
        props.put("MyName","MyValue");
        byte[] body5 = {5};
        byte[] body6 = {6};
        p1.setMessageBody(body5);
        p1.setPersistent(true);
        p1.setIsQueue(false);
        p1.setProperties(props);
        p1.updateTimestamp();
        p1.updateSequenceNumber();

        System.out.print("Initial Packet " + p1.getSysMessageID());
        ref =  createReference(ps, p1, duid, null);
        ref.store(topics);
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        try {
            SysMessageID newid = ref.replacePacket(null, body6);
            System.out.print("New ID " + newid);
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");
        System.out.println("----------------------------------");
        System.out.println("Test4: Updating Properties");
        System.out.println("----------------------------------");
        p1 = new Packet();
        props = new Hashtable();
        props.put("MyName","MyValue");
        byte[] body7 = {7};
        byte[] body8 = {8};
        p1.setMessageBody(body7);
        p1.setPersistent(true);
        p1.setIsQueue(false);
        p1.setProperties(props);
        p1.updateTimestamp();
        p1.updateSequenceNumber();

        Hashtable newProps = new Hashtable();
        newProps.put("MyNewName","MyNewValue");
        newProps.put("MyName","***REPLACE***");
        System.out.print("Initial Packet " + p1.getSysMessageID());
        ref =  createReference(ps, p1, duid, null);
        System.out.println(ref.getProperties());
        ref.store(topics);
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        try {
            SysMessageID newid = ref.replacePacket(newProps, body8);
            System.out.print("New ID " + newid);
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");
        System.out.println(ref.getProperties());
        System.out.println("----------------------------------");
        System.out.println("Test5: Message Delivered");
        System.out.println("----------------------------------");
        p1 = new Packet();
        props = new Hashtable();
        props.put("MyName","MyValue");
        byte[] body9 = {9};
        byte[] body10 = {10};
        p1.setMessageBody(body9);
        p1.setPersistent(true);
        p1.setIsQueue(false);
        p1.setProperties(props);
        p1.updateTimestamp();
        p1.updateSequenceNumber();

        newProps = new Hashtable();
        newProps.put("MyNewName","MyNewValue");
        newProps.put("MyName","***REPLACE***");
        ref =  createReference(ps, p1, duid, null);
        ref.store(topics);
        ref.delivered(uid2, uid2, false, false);
        System.out.print("Initial Packet " + p1.getSysMessageID());
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");

        try {
            SysMessageID newid = ref.replacePacket(newProps, body10);
            System.out.print("New ID " + newid);
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        System.out.println("\t[Body="+ref.getPacket().getMessageBodyByteBuffer().get()+"]");
        System.out.println(ref.getProperties());
    } catch (Exception ex) {
        ex.printStackTrace();
    }
    }


    public static void main(String args[]) {
        System.out.println("RUNNING TEST");
        replaceTest();
    }

}

