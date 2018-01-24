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

package com.sun.messaging.jmq.jmsserver.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit; 
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.SupportUtil;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.util.DestLimitBehavior;
import com.sun.messaging.jmq.util.DestScope;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.InvalidSysMessageIDException;
import com.sun.messaging.jmq.io.InvalidPacketException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.PartitionNotFoundException;
import com.sun.messaging.jmq.jmsserver.util.ConflictException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.util.lists.AddReason;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.handlers.RefCompare;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TxnLoggingStore;
import com.sun.messaging.jmq.jmsserver.persist.api.NoPersistPartitionedStoreImpl;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionListener;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerStatus;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.FaultInjection;

import com.sun.messaging.jmq.jmsserver.management.agent.Agent;

import com.sun.messaging.jmq.util.lists.*;

import java.util.*;
import java.io.*;
import java.lang.*;

/**
 */
public final class DestinationList implements ConnToPartitionStrategyContext
{
    static boolean DEBUG = false;

    static final boolean DEBUG_CLUSTER = Globals.getConfig().getBooleanProperty(
                                           Globals.IMQ + ".cluster.debug.ha") ||
                                           Globals.getConfig().getBooleanProperty(
                                           Globals.IMQ + ".cluster.debug.txn") ||
                                           Globals.getConfig().getBooleanProperty(
                                           Globals.IMQ + ".cluster.debug.msg");

    private static FaultInjection FI = FaultInjection.getInjection();

    static final String DEBUG_LISTS_PROP = Globals.IMQ+".lists.debug";
    static boolean DEBUG_LISTS = 
        Globals.getConfig().getBooleanProperty(DEBUG_LISTS_PROP);

    static final boolean NO_PRODUCER_FLOW =
        Globals.getConfig().getBooleanProperty(
            Globals.IMQ + ".noProducerFlow", false);

    /**
     * default consumer prefetch value 
     */
    static final int DEFAULT_PREFETCH = 1000;

    static final boolean PERSIST_SYNC = 
        Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".persist.file.sync.enabled", false);

    /**
     * default maximum number of producers/destination
     */
    public static final int DEFAULT_MAX_PRODUCERS = 100;

    public static final int UNLIMITED=-1;

    /**
     * default maximum size of a destination
     */
    private static final int DEFAULT_DESTINATION_SIZE=100000;

    private static final int ALL_DESTINATIONS_MASK = 0;
    private static final int TEMP_DESTINATIONS_MASK = DestType.DEST_TEMP;

    private static final String AUTO_QUEUE_STR = Globals.IMQ + ".autocreate.queue";
    private static final String AUTO_TOPIC_STR = Globals.IMQ + ".autocreate.topic";
    private static final String DST_REAP_STR = Globals.IMQ + ".autocreate.reaptime";
    private static final String MSG_REAP_STR = Globals.IMQ + ".message.expiration.interval";

    private static final String CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO_PROP =
        Globals.IMQ + ".cluster.prefetch.checkMsgRateAtCapacityRatio";

    static int CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO = Globals.getConfig().
        getIntProperty(CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO_PROP, 50);

    private static final String CHECK_MSGS_RATE_FOR_ALL_PROP =
        Globals.IMQ + ".cluster.prefetch.checkMsgRateAll";

    static boolean CHECK_MSGS_RATE_FOR_ALL = Globals.getConfig().
        getBooleanProperty(CHECK_MSGS_RATE_FOR_ALL_PROP, false);

    private static final long DEFAULT_TIME = 120;

    private static boolean ALLOW_QUEUE_AUTOCREATE =
        Globals.getConfig().getBooleanProperty(AUTO_QUEUE_STR, true);

    private static boolean ALLOW_TOPIC_AUTOCREATE =
        Globals.getConfig().getBooleanProperty(AUTO_TOPIC_STR, true);

    static long AUTOCREATE_EXPIRE =
        Globals.getConfig().getLongProperty(DST_REAP_STR, DEFAULT_TIME)*1000;

    static long MESSAGE_EXPIRE =
        Globals.getConfig().getLongProperty(MSG_REAP_STR, DEFAULT_TIME)*1000;

    static final boolean CAN_MONITOR_DEST = getCAN_MONITOR_DEST(); 
    static final boolean CAN_USE_LOCAL_DEST = getCAN_USE_LOCAL_DEST();

    private static List<PartitionListener> partitionListeners = 
                                 new ArrayList<PartitionListener>();

    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
        }

        if (DEBUG) {
           Globals.getLogger().log(Logger.INFO, "Syncing message store: " + PERSIST_SYNC);
        }

        if (NO_PRODUCER_FLOW) {
           Globals.getLogger().log(Logger.INFO, "Producer flow control is turned off ");
        }
    }
    private static final boolean getCAN_MONITOR_DEST() {
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            return license.getBooleanProperty(
                       license.PROP_ENABLE_MONITORING, false);
        } catch (BrokerException ex) {
            return false;
        }
    }
    private static final boolean getCAN_USE_LOCAL_DEST() {
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            return license.getBooleanProperty(
                       license.PROP_ENABLE_LOCALDEST, false);
        } catch (BrokerException ex) {
            return false;
        }
    }

    /**
     * maximum size of a batch of messages/producer
     */
    public static final int DEFAULT_MAX_PRODUCER_BATCH = 1000;

    public static final int MAX_PRODUCER_BATCH =
           Globals.getConfig().getIntProperty(
                Globals.IMQ + ".producer.maxBatch",
                DEFAULT_MAX_PRODUCER_BATCH);

    public static final int MAX_PRODUCER_BYTES_BATCH = -1;

    private static final String AUTO_MAX_NUM_MSGS = Globals.IMQ+
                 ".autocreate.destination.maxNumMsgs";
    private static final String AUTO_MAX_TOTAL_BYTES = Globals.IMQ+
                 ".autocreate.destination.maxTotalMsgBytes";
    private static final String AUTO_MAX_BYTES_MSG = Globals.IMQ+
                 ".autocreate.destination.maxBytesPerMsg";
    public static final String AUTO_MAX_NUM_PRODUCERS = Globals.IMQ+
                 ".autocreate.destination.maxNumProducers";
    private static final String AUTO_LOCAL_ONLY = Globals.IMQ+
                 ".autocreate.destination.isLocalOnly";
    private static final String AUTO_LIMIT_BEHAVIOR = Globals.IMQ+
                 ".autocreate.destination.limitBehavior";

    static int defaultMaxMsgCnt = Globals.getConfig().
        getIntProperty(AUTO_MAX_NUM_MSGS, DEFAULT_DESTINATION_SIZE);

    static int defaultProducerCnt = Globals.getConfig().
        getIntProperty(AUTO_MAX_NUM_PRODUCERS, DEFAULT_MAX_PRODUCERS);


    private static final long _defKByteDMQCapacity = 10*1024L;
    private static final long _defKByteCapacity = 10*1024*1024L;
    static SizeString defaultMaxMsgBytes = Globals.getConfig().
           getSizeProperty(AUTO_MAX_TOTAL_BYTES, _defKByteCapacity);

    private static final long _defKBytePerMsg = 10*1024L;
    static SizeString defaultMaxBytesPerMsg = Globals.getConfig().
               getSizeProperty(AUTO_MAX_BYTES_MSG, _defKBytePerMsg);

    static boolean defaultIsLocal = Globals.getConfig().
                     getBooleanProperty(AUTO_LOCAL_ONLY, false);

    static int defaultLimitBehavior = 
        DestLimitBehavior.getStateFromString(Globals.getConfig().
                          getProperty(AUTO_LIMIT_BEHAVIOR, "REJECT_NEWEST"));

    // DEAD MESSAGE QUEUE PROPERTIES

    public static final String USE_DMQ_STR = Globals.IMQ +
                   ".autocreate.destination.useDMQ";

    private static final String TRUNCATE_BODY_STR = Globals.IMQ +
                   ".destination.DMQ.truncateBody";

    private static final String LOG_MSGS_STR = Globals.IMQ +
                   ".destination.logDeadMsgs";


    public static final boolean defaultUseDMQ = 
        Globals.getConfig().getBooleanProperty(USE_DMQ_STR, true);

    private static final boolean defaultTruncateBody = 
        Globals.getConfig().getBooleanProperty(TRUNCATE_BODY_STR, false);

    private static final boolean defaultVerbose = 
        Globals.getConfig().getBooleanProperty(LOG_MSGS_STR, false );

    private static final String DMQ_NAME = "mq.sys.dmq";

    static boolean autocreateUseDMQ = defaultUseDMQ;
    private static boolean storeBodyWithDMQ = !defaultTruncateBody;
    private static boolean verbose = defaultVerbose;

    private static final String SYSTEM_MAX_SIZE 
                 = Globals.IMQ + ".system.max_size";
    private static final  String SYSTEM_MAX_COUNT 
                 = Globals.IMQ + ".system.max_count";
    public static final  String MAX_MESSAGE_SIZE 
                 = Globals.IMQ + ".message.max_size";

    public static final String MIN_CONN_STRATEGY = "MIN_CONN";
    public static final String RR_CONN_STRATEGY = "ROUND_ROBIN";
    public static final String CONN_STRATEGY_PROP =
                  Globals.IMQ+".connection.loadBalanceToPartitionStrategy";
    public static final String CONN_STRATEGY_DEFAULT = MIN_CONN_STRATEGY;
    public static final String MIN_CONN_STRATEGY_CLASS =
           "com.sun.messaging.jmq.jmsserver.core.MinConnToPartitionStrategy";
    public static final String RR_CONN_STRATEGY_CLASS =
           "com.sun.messaging.jmq.jmsserver.core.RRConnToPartitionStrategy";

    private static ConnToPartitionStrategy partitionStrategy = null; 

    /**
     * maximum size of any message (in bytes) 
     */
    private static SizeString individual_max_size = null;

    /**
     * memory max
     */
    private static SizeString max_size = null;

    /**
     * message max 
     */
    private static long message_max_count = 0;

    private static Object totalcntLock = new Object();

    /**
     * current size in bytes of total data
     */
    private static long totalbytes = 0;

    /**
     * current size of total data
     */
    private static int totalcnt = 0;
    private static int totalcntNonPersist = 0;

    private static boolean inited = false;
    private static boolean shutdown = false;

    static final Map<PartitionedStore, DestinationList> destinationListList = 
        Collections.synchronizedMap(new LinkedHashMap<PartitionedStore, DestinationList>());

    private static DestinationList DL = Globals.getDestinationList();

    private static PartitionedStore ADMINP = 
        new NoPersistPartitionedStoreImpl(PartitionedStore.ADMIN_UID);
    private static PartitionedStore REMOTEP = 
        new NoPersistPartitionedStoreImpl(PartitionedStore.REMOTE_UID);

    private ArrayList<ConnectionUID> connections = new ArrayList<ConnectionUID>();

    private Object destinationListLock = new Object();
    private boolean valid = true;
    private boolean destsLoaded = false;

    Queue deadMessageQueue = null;
    
    /**
     * list of messages to destination
     */
    private final Map<SysMessageID, Set<PacketListDMPair>> packetlist = 
        Collections.synchronizedMap(new HashMap<SysMessageID, Set<PacketListDMPair>>());

    final Map destinationList = Collections.synchronizedMap(new HashMap());

    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();

    private PartitionedStore pstore = null;
    private TransactionList translist = null;

    private static boolean partitionModeInited = false;
    private static boolean partitionMode = false;
    private static boolean partitionMigratable = false;
    private String logsuffix = "";

    private static final String PARTITION_LOCK_TIMEOUT_PROP =
                            Globals.IMQ+".partition.lockTimeout";
    private static final long PARTITION_LOCK_TIMEOUT_DEFAULT = 300L; //seconds
    private static long partitionLockTimeout = 
        Globals.getConfig().getLongProperty(PARTITION_LOCK_TIMEOUT_PROP, 
                                            PARTITION_LOCK_TIMEOUT_DEFAULT);
    private static ReentrantReadWriteLock partitionLock = new ReentrantReadWriteLock();
    private static Lock partitionShareLock = partitionLock.readLock();
    private static Lock partitionExclusiveLock = partitionLock.writeLock();

    private static ExecutorService partitionMonitor = 
                           Executors.newSingleThreadExecutor();

    private Object subscriptionLock =  new Object();
    private boolean duraSubscriptionInited = false;
    private boolean nonDuraSharedSubscriptionInited = false;
     

    /** 
     * Only to be called from CoreLifecycle - the singleton DL
     */
    public DestinationList() {
    }

    DestinationList(PartitionedStore partition) {
        pstore = partition;
        if (partitionMode) {
            logsuffix = " ["+pstore+"]";
        }
    }

    public static PartitionedStore getAdminPartition() {
        return ADMINP;
    }

    public static PartitionedStore getRemotePartition() {
        return REMOTEP;
    }

    public PartitionedStore getPartitionedStore() {
        return pstore;
    }

    public static void translistPostProcess(PartitionedStore ps) {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            dl.getTransactionList().postProcess();
            return;
        }
        ArrayList list = null; 
        synchronized(destinationListList) {
            list = new ArrayList(destinationListList.values());
        }
        Iterator<DestinationList> itr = list.iterator();
        while (itr.hasNext()) {
            itr.next().getTransactionList().postProcess();
        }
    }

    public static TransactionList[] getTransactionList(PartitionedStore ps) {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            if (dl == null) {
                return new TransactionList[]{ null };
            }
            return new TransactionList[]{ dl.getTransactionList() };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            TransactionList[] tls = new TransactionList[(sz == 0 ? 1:sz)];
            tls[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                tls[i++] = itr.next().getTransactionList();
            }
            return tls;
        }
    }

    TransactionList getTransactionList() {
        return translist; 
    }

    public void setTransactionList(TransactionList tlist) {
        translist  = tlist;
    }

    public static void storeBodyInDMQ(boolean store) {
        storeBodyWithDMQ = store;
    }
    public static boolean getStoreBodyInDMQ() {
        return storeBodyWithDMQ;
    }
    public static void setVerbose(boolean v) {
        verbose = v;
    }
    public static boolean getVerbose() {
        return verbose;
    }

    public static Queue[] getDMQ(PartitionedStore ps) {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            return new Queue[]{ dl.getDMQ() };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Queue[] qs = new Queue[(sz == 0 ? 1:sz)];
            qs[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                qs[i] = itr.next().getDMQ();
            }
            return qs;
        }
    }

    Queue getDMQ() {
        if (pstore == null) {
            throw new RuntimeException("IllegalStateException: "+
            "DestinationList.getDMQ: no store partition set");
        }
        return deadMessageQueue;
    }

    private synchronized Queue createDMQ() 
    throws BrokerException, IOException {

        if (pstore == null) {
            throw new RuntimeException("IllegalStateException: "+
            "DestinaionList.createDMQ: no store partition set");
        }

        DestinationUID uid = DestinationUID.getUID(DMQ_NAME, true);
        
        Queue dmq = null;
        dmq = (Queue) destinationList.get(uid);
        try {
            if (dmq == null) {
                Globals.getLogger().log(Logger.INFO, BrokerResources.I_DMQ_CREATING_DMQ);
                dmq =(Queue)createDestination(DMQ_NAME, 
                      DestType.DEST_TYPE_QUEUE | DestType.DEST_DMQ,
                      true, false, null, false, false);
                dmq.maxProducerLimit = 0;
                dmq.scope=(Globals.getHAEnabled() ? DestScope.CLUSTER
                           :DestScope.LOCAL);
                dmq.msgSizeLimit= null;
                dmq.setLimitBehavior(DestLimitBehavior.REMOVE_OLDEST);
                dmq.setByteCapacity(new SizeString(_defKByteDMQCapacity));
                dmq.setCapacity(1000);
                dmq.maxPrefetch=1000;
                // deal with remaining properties
                dmq.isDMQ=true;
                dmq.useDMQ=false;
                dmq.update();
            }
        } catch (BrokerException ex) {
            if (ex.getStatusCode() == Status.CONFLICT) {
                // another broker may have created this while we were loading
                Globals.getLogger().logStack(Logger.DEBUG, "Another broker may have created the DMQ, reloading", ex);
                dmq = (Queue)pstore.getDestination(uid);
                if (dmq == null) {
                    ex.overrideStatusCode(Status.ERROR);
                    throw ex;
                } 
                dmq.setDestinationList(this);
            } else {
                throw ex;
            }
        }
        dmq.load(true, null, null);
        return dmq;
    }


    public static boolean removeDeadMessage(PartitionedStore ps,
                  SysMessageID sysid, String comment, 
                  Throwable exception, int deliverCnt,
                  Reason r, String broker) 
                  throws IOException, BrokerException {

        PacketReference ref = get(ps, sysid);
        Destination d = ref.getDestination();
        return d.removeDeadMessage(ref, comment, exception,
                                   deliverCnt, r, broker);
    }

    public static void routeMoveAndForwardMessage(PacketReference oldRef, 
                  PacketReference newRef, Destination target)
                  throws IOException, BrokerException {

        boolean route= target.queueMessage(newRef, false);
        if (route) {
            // we have space ... move the message in a single command
            Set s = target.routeAndMoveMessage(oldRef, newRef);
            if (s != null) {
                target.forwardMessage(s, newRef);
            }
        }
    }

    static int calcProducerBatchCnt(int destSize, int producers) {

        if (destSize == -1) return MAX_PRODUCER_BATCH;

        int p = producers;
        if (p <= 0) {
            p = DEFAULT_MAX_PRODUCERS;
        }

        int val = destSize/p;

        if (val <= 0) val = 1;
            
        if (val > MAX_PRODUCER_BATCH)
            return MAX_PRODUCER_BATCH;

        return val;
    }

    static long calcProducerBatchBytes(long destSize, int producers) {

        if (destSize == -1) return -1;

        int p = producers;
        if (p <= 0) {
            p = DEFAULT_MAX_PRODUCERS;
        }

        long val = destSize/p;

        if (val <= 0) val = 1;
            
        if (MAX_PRODUCER_BYTES_BATCH != -1 && val > MAX_PRODUCER_BYTES_BATCH)
            return MAX_PRODUCER_BYTES_BATCH;

        return val;
    }

    public static void resetAllMetrics(PartitionedStore ps) {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            dl.resetAllMetrics();
            return;
        }
        synchronized(destinationListList) {
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dl.resetAllMetrics();
            }
        }
    }

    private void resetAllMetrics() {
         Iterator itr = getAllDestinations(ALL_DESTINATIONS_MASK);
         while (itr.hasNext()) {
             Destination d = (Destination)itr.next();
             d.resetMetrics();
         }
    }

    public String toString() {
        return (partitionMode ? "["+pstore+"]":super.toString());
    }

    public String toLongString() {
        return "DestinationList["+pstore+"]valid="+valid+",loaded="+destsLoaded;
    }

    public static Hashtable getAllDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "All Destinations");
        ht.put("maxMsgSize", (individual_max_size == null ? "null":
                      individual_max_size.toString()));
        ht.put("maxTotalSize", (max_size == null ? "null" :
                     max_size.toString()));
        ht.put("maxCount", String.valueOf(message_max_count));
        ht.put("totalBytes", String.valueOf(totalbytes));
        ht.put("totalCnt", String.valueOf(totalcnt));
        ht.put("totalCntNonPersist", String.valueOf(totalcntNonPersist));
        ht.put("sync", String.valueOf(PERSIST_SYNC));
        ht.put("noProducerFlow", String.valueOf(NO_PRODUCER_FLOW));
        ht.put("autoCreateTopics", String.valueOf(ALLOW_TOPIC_AUTOCREATE));
        ht.put("autoCreateQueue", String.valueOf(ALLOW_QUEUE_AUTOCREATE));
        ht.put("messageExpiration", String.valueOf(MESSAGE_EXPIRE));
        ht.put("producerBatch", String.valueOf(MAX_PRODUCER_BATCH));
        ht.put("QueueSpecific", Queue.getAllDebugState());

        ArrayList dls = null;
        synchronized(destinationListList) {
            dls = new ArrayList(destinationListList.keySet());
        }
        ht.put("partitionedStoreCnt", String.valueOf(dls.size()));
        Iterator itr = dls.iterator();
        PartitionedStore ps = null;
        DestinationList dl = null;
        while (itr.hasNext()) {
            ps = (PartitionedStore)itr.next();
            dl = destinationListList.get(ps);
            ht.put(ps.toString(), dl.getDebugState());
        }
        return ht;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("partitionedStore", 
               (pstore == null ? "null":pstore.toString()));
        ht.put("msgCnt", (packetlist == null ? "null" :
                   String.valueOf(packetlist.size())));
        Hashtable destInfo = new Hashtable();
        if (destinationList != null) {
            ArrayList dlist = null;
            synchronized (destinationList) {
                dlist = new ArrayList(destinationList.keySet());
            }
            ht.put("destinationCnt", String.valueOf(dlist.size()));
            Iterator itr = dlist.iterator();
            while (itr.hasNext()) {
                DestinationUID duid = (DestinationUID)itr.next();
                Destination d = getDestination(duid);
                if (d == null) {
                    destInfo.put(duid.getLocalizedName(),"Unknown");
                } else {
                    destInfo.put(duid.getLocalizedName(),
                    d.getDebugState());
                }
            }
        } else {
            ht.put("destinationCnt", "null");
        }
        ht.put("destinations", destInfo);
        return ht;
    }

    public static void remoteCheckMessageHomeChange(PacketReference ref, BrokerAddress broker) {
        BrokerAddress addr = ref.getBrokerAddress();
        if (addr != null && addr.equals(broker)) {
            return;
        }

        Set destroyConns = new HashSet();
        Map cc =  ref.getRemoteConsumerUIDs(); 
        Consumer consumer = null;
        ConsumerUID cuid = null;
        Iterator citr = cc.entrySet().iterator();
        Map.Entry me = null;
        while (citr.hasNext()) {
            me = (Map.Entry)citr.next();
            cuid = (ConsumerUID)me.getKey();
            consumer =  Consumer.getConsumer(cuid);
            if (consumer == null) continue;
            if (consumer.tobeRecreated()) {
                destroyConns.add(me.getValue());
            }
        }
        destroyConnections(destroyConns, GoodbyeReason.MSG_HOME_CHANGE,
          GoodbyeReason.toString(GoodbyeReason.MSG_HOME_CHANGE)+"["+addr+":"+broker+"]");
    }

    public static void remoteCheckTakeoverMsgs(
        Map<String, String> msgs, String brokerid, PartitionedStore ps)
        throws BrokerException {

        Set destroyConns = new HashSet();
        Map<String, String> badsysids = null;
        Iterator<Map.Entry<String, String>> itr = msgs.entrySet().iterator();
        Map.Entry<String, String> me = null;
        String sysidstr = null, duidstr = null;
        SysMessageID sysid = null;
        DestinationUID duid = null;
        while (itr.hasNext()) {
            me = itr.next();
            sysidstr = me.getKey();
            duidstr = me.getValue();
            try {
                sysid = SysMessageID.get(sysidstr);
            } catch (InvalidSysMessageIDException e) {
                Globals.getLogger().logStack(Logger.ERROR, e.getMessage(), e);
                if (!Globals.getStore().getStoreType().equals(
                    Store.JDBC_STORE_TYPE)) {
                    throw e;
                }
                duid = new DestinationUID(duidstr);
                Globals.getLogger().log(Logger.WARNING, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_TAKEOVER_MSGID_CORRUPT_TRY_REPAIR,
                    sysidstr, duidstr));
                try {
                    Packet p = null;
                    try {
                        p = ps.getMessage(duid, sysidstr);
                    } catch (BrokerException ee) {
                        Throwable cause = ee.getCause();
                        String[] args = { sysidstr, "[?]", 
                                          duidstr, cause.toString() };
                        String emsg =  Globals.getBrokerResources().getKString(
                            BrokerResources.X_REPAIR_CORRUPTED_MSGID_IN_STORE, args);
                        Globals.getLogger().logStack(Logger.ERROR, emsg, ee);

                        if (cause instanceof InvalidPacketException) {
                            handleInvalidPacket(sysidstr, duidstr, emsg, 
                                         (InvalidPacketException)cause, ps);
                            itr.remove();
                            continue;
                        } else {
                            throw ee;
                        }
                    }
                    sysid = p.getSysMessageID();
                    String realsysidstr = sysid.getUniqueName();
                    String[] args3 = { sysidstr, realsysidstr, duidstr };
                    Globals.getLogger().log(Logger.INFO, 
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_REPAIR_CORRUPTED_MSGID_TO, args3));
                    ps.repairCorruptedSysMessageID(sysid, sysidstr, duidstr, true);
                    if (badsysids == null) {
                        badsysids = new HashMap<String, String>();
                    }
                    badsysids.put(sysidstr, realsysidstr);
                } catch (BrokerException ee) {
                    Globals.getLogger().logStack(Logger.ERROR, e.getMessage(), ee);
                    throw e;
                }
            }
            PacketReference ref = (PacketReference)get(null, sysid);
            if (ref == null) continue;
            Iterator cnitr = ref.getRemoteConsumerUIDs().values().iterator();
            while (cnitr.hasNext()) {
                destroyConns.add(cnitr.next());
            }
        }
        if (badsysids != null) {
            itr = badsysids.entrySet().iterator();   
            String v = null;
            while (itr.hasNext()) {
                me = itr.next(); 
                v = msgs.remove(me.getKey());
                msgs.put(me.getValue(), v);
            }
        }
        destroyConnections(destroyConns, GoodbyeReason.BKR_IN_TAKEOVER,
          GoodbyeReason.toString(GoodbyeReason.BKR_IN_TAKEOVER)+":"+brokerid);
    }

    private static void handleInvalidPacket(
        String sysidstr, String duidstr, String comment,
        InvalidPacketException ipex, PartitionedStore ps)
        throws BrokerException {

        Properties props = new Properties();
        props.put(DMQ.UNDELIVERED_REASON, 
                  RemoveReason.ERROR.toString());
        props.put(DMQ.UNDELIVERED_TIMESTAMP, 
                  Long.valueOf(System.currentTimeMillis()));
        props.put(DMQ.UNDELIVERED_COMMENT, comment);

        String cstr = SupportUtil.getStackTraceString(ipex);
        props.put(DMQ.UNDELIVERED_EXCEPTION, cstr);

        props.put(DMQ.BROKER, Globals.getMyAddress().toString()); 
        props.put(DMQ.DEAD_BROKER, Globals.getMyAddress().toString());

        byte[] data = ipex.getBytes();
        Queue[] dmqs = DL.getDMQ(ps);
        Queue dmq = dmqs[0];

        Packet pkt = new Packet();
        pkt.setPacketType(PacketType.BYTES_MESSAGE);
        pkt.setProperties(props);
        pkt.setDestination(dmq.getDestinationName());
        pkt.setIsQueue(true);
        pkt.setPersistent(true);
        pkt.setIP(Globals.getBrokerInetAddress().getAddress());
        pkt.setPort(Globals.getPortMapper().getPort());
        pkt.updateSequenceNumber();
        pkt.updateTimestamp();
        pkt.generateSequenceNumber(false);
        pkt.generateTimestamp(false);
        pkt.setSendAcknowledge(false);
        pkt.setMessageBody(data);
        PacketReference ref = PacketReference.createReference(
                          dmq.getPartitionedStore(), pkt, null);

        String[] args1 = { sysidstr, duidstr,
                           ref.getSysMessageID().toString()+"["+
                           PacketType.getString(PacketType.BYTES_MESSAGE)+"]" };
        Globals.getLogger().log(Logger.INFO, 
            Globals.getBrokerResources().getKString(
            BrokerResources.I_PLACING_CORRUPTED_MSG_TO_DMQ, args1));

        dmq.queueMessage(ref, false);
        Set s = dmq.routeNewMessage(ref);

        String[] args2 = { sysidstr, duidstr, 
                           ref.getSysMessageID().toString()+"["+
                           PacketType.getString(PacketType.BYTES_MESSAGE)+"]" };
        Globals.getLogger().log(Logger.INFO, 
            Globals.getBrokerResources().getKString(
            BrokerResources.I_PLACED_CORRUPTED_MSG_TO_DMQ, args2));

        dmq.forwardMessage(s, ref);

        Globals.getLogger().log(Logger.INFO, 
            Globals.getBrokerResources().getKString(
            BrokerResources.I_REMOVE_CORRUPTED_MSG_IN_STORE,
            sysidstr, duidstr));
        try {
            ps.removeMessage((new DestinationUID(duidstr)), sysidstr, true); 
        } catch (Exception e) {
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.X_PERSIST_MESSAGE_REMOVE_FAILED, sysidstr);
            Globals.getLogger().logStack(Logger.ERROR, emsg, e);
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(emsg, e);
        }
    }

    private static void destroyConnections(Set destroyConns, int reason, String reasonstr) {

        ConnectionManager cm = Globals.getConnectionManager();
        Iterator cnitr = destroyConns.iterator();
        while (cnitr.hasNext()) {
            IMQBasicConnection conn = (IMQBasicConnection)cm.getConnection(
                                      (ConnectionUID)cnitr.next());
            if (conn == null) continue;
            Globals.getLogger().log(Logger.INFO, 
                "Destroying connection " + conn + " because "+reasonstr);
            if (DEBUG) conn.dump();
            conn.destroyConnection(true, reason, reasonstr);
            conn.waitForRelease(Globals.getConfig().getLongProperty(
                 Globals.IMQ+"."+conn.getService().getName()+".destroy_timeout", 30)*1000);
        }
    }

    public synchronized static void 
        loadTakeoverMsgs(PartitionedStore storep, 
        Map<String, String> msgs, List txns, Map txacks)
        throws BrokerException {

        DestinationList dl = destinationListList.get(storep);

        Map m = new HashMap();
        Logger logger = Globals.getLogger();

        Map ackLookup = new HashMap();

        // ok create a hashtable for looking up txns
        if (txacks != null) {
            Iterator itr = txacks.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry)itr.next();
                TransactionUID tuid = (TransactionUID)entry.getKey();
                List l = (List)entry.getValue();
                Iterator litr = l.iterator();
                while (litr.hasNext()) {
                    TransactionAcknowledgement ta =
                        (TransactionAcknowledgement)litr.next();
                    String key = ta.getSysMessageID() +":" +
                                 ta.getStoredConsumerUID();
                    ackLookup.put(key, tuid);
                }
            }
         }

        // Alright ...
        //    all acks fail once takeover begins
        //    we expect all transactions to rollback
        //    here is the logic:
        //        - load all messages
        //        - remove any messages in open transactions
        //        - requeue all messages
        //        - resort (w/ load comparator)
        //
        //
        // OK, first get msgs and sort by destination
        HashMap openMessages = new HashMap();
        Iterator itr = msgs.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry me = (Map.Entry)itr.next();
            String msgID = (String)me.getKey();
            String dst = (String)me.getValue();
            DestinationUID dUID = new DestinationUID(dst);
            Packet p = null;
            try {
                p = storep.getMessage(dUID, msgID);
            } catch (BrokerException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof InvalidPacketException) {
                    String[] args = { msgID, dst, cause.toString() };
                    String emsg = Globals.getBrokerResources().
                        getKString(BrokerResources.X_MSG_CORRUPTED_IN_STORE, args);
                    logger.logStack(Logger.ERROR, emsg, ex);

                    handleInvalidPacket(msgID, dst, emsg, 
                          (InvalidPacketException)cause, storep);
                    itr.remove();
                    continue;
                }

                // Check if dst even exists!
                if (ex.getStatusCode() == Status.NOT_FOUND) {
                    Destination[] ds = getDestination(storep, dUID);
                    Destination d = ds[0];
                    if (d == null) {
                        String args[] = {
                            msgID, dst, Globals.getBrokerResources().getString(
                                BrokerResources.E_DESTINATION_NOT_FOUND_IN_STORE, dst)};
                        logger.log(Logger.ERROR,
                              BrokerResources.W_CAN_NOT_LOAD_MSG,
                              args, ex);
                    }
                }
                throw ex;
            }

            dUID = DestinationUID.getUID(p.getDestination(), p.getIsQueue());
            PacketReference pr = PacketReference.createReference(storep, p, dUID, null);

            // mark already stored and make packet a SoftReference to
            // prevent running out of memory if dest has lots of msgs
            pr.setLoaded();
            logger.log(Logger.DEBUG,"Loading message " + pr.getSysMessageID() 
                   + " on " + pr.getDestinationUID());
           
            // check transactions
            TransactionUID tid = pr.getTransactionID();
            if (tid != null) {
                // see if in transaction list
                if (txns.contains(tid)) {
                    // open transaction 
                    TransactionState ts = dl.getTransactionList()
                                          .retrieveState(pr.getTransactionID());
                    if (ts != null && 
                        ts.getState() != TransactionState.ROLLEDBACK && 
                        ts.getState() != TransactionState.COMMITTED) {
                        // in transaction ... 
                        logger.log(Logger.DEBUG, "Processing open transacted message " +
                               pr.getSysMessageID() + " on " + tid + 
                               "["+TransactionState.toString(ts.getState())+"]");
                        openMessages.put(pr.getSysMessageID(), tid);
                    }  else if (ts != null && ts.getState() == TransactionState.ROLLEDBACK) {
                        pr.destroy();
                        continue;
                    } else {
                    }
                }
            }
            dl.packetlistAdd(pr.getSysMessageID(), pr.getDestinationUID(), null);

            Set l = null;
            if ((l = (Set)m.get(dUID)) == null) {
                l = new TreeSet(new RefCompare());
                m.put(dUID, l);
            }
            l.add(pr);
        }

        // OK, handle determining how to queue the messages

        Map<PacketReference, MessageDeliveryTimeInfo> deliveryDelays =
                new HashMap<PacketReference, MessageDeliveryTimeInfo>();

        // first add all messages
        Iterator dsts = m.entrySet().iterator();
        while (dsts.hasNext()) {
            Map.Entry entry = (Map.Entry)dsts.next();
            DestinationUID dst = (DestinationUID) entry.getKey();
            Set l = (Set)entry.getValue();
            Destination[] ds = getDestination(storep, dst);
            Destination d = ds[0];

            if (d == null) { // create it 
                try {
                    ds = getDestination(storep, dst.getName(), 
                        (dst.isQueue()? DestType.DEST_TYPE_QUEUE:
                         DestType.DEST_TYPE_TOPIC) , true, true);
                    d = ds[0];
                } catch (IOException ex) {
                     throw new BrokerException(
                         Globals.getBrokerResources().getKString(
                         BrokerResources.X_CANT_LOAD_DEST, d.getName()));
                }
            } else {
                synchronized(d) {
                    if (d.isLoaded()) {
                        // Destination has already been loaded so just called
                        // initialize() to update the size and bytes variables
                        d.initialize();
                    }
                    d.load(l);
                }
            }
            logger.log(Logger.INFO,
                BrokerResources.I_LOADING_DST,
                   d.getName(), String.valueOf(l.size()));

            MessageDeliveryTimeTimer dt = d.deliveryTimeTimer;

            if (dt == null && !d.isDMQ()) {
                if (!d.isValid()) {
                    String emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.W_UNABLE_LOAD_TAKEOVER_MSGS_TO_DESTROYED_DST,
                        d.getDestinationUID());
                    logger.log(Logger.WARNING, emsg);
                    continue;
                }
                String emsg = Globals.getBrokerResources().getKString(
                    BrokerResources.W_UNABLE_LOAD_TAKEOVER_MSGS_NO_DST_DELIVERY_TIMER,
                    d.getDestinationUID()+"["+d.isValid()+"]");
                    logger.log(Logger.WARNING, emsg);
                continue;
            }

            // now we're sorted, process
            Iterator litr = l.iterator();
            try {
                MessageDeliveryTimeInfo di = null;
                while (litr.hasNext()) {
    
                    PacketReference pr = (PacketReference)litr.next();
                    di = pr.getDeliveryTimeInfo();
                    if (di != null) {
                        dt.removeMessage(di);
                    }
                    try {
                        // ok allow overrun
                        boolean el = d.destMessages.getEnforceLimits();

                        d.destMessages.enforceLimits(false);

                        if (DEBUG) {
                        logger.log(logger.INFO, "Put message "+pr+"["+di+"] to destination "+d);
                        }
                        pr.lock();
                        d.acquireQueueRemoteLock();
                        try {
                            d.putMessage(pr, AddReason.LOADED, true);
                        } finally {
                            d.clearQueueRemoteLock();
                        }
                        // turn off overrun
                        d.destMessages.enforceLimits(el);
                    } catch (IllegalStateException ex) {
                        // thats ok, we already exists
                        String args[] = { pr.getSysMessageID().toString(),
                            pr.getDestinationUID().toString(),
                            ex.getMessage() };
                        logger.logStack(Logger.WARNING,
                              BrokerResources.W_CAN_NOT_LOAD_MSG,
                               args, ex);
                        continue;
                    } catch (OutOfLimitsException ex) {
                        String args[] = { pr.getSysMessageID().toString(),
                            pr.getDestinationUID().toString(),
                            ex.getMessage() };
                        logger.logStack(Logger.WARNING,
                              BrokerResources.W_CAN_NOT_LOAD_MSG,
                               args, ex);
                        continue;
                    } finally {
                        if (di != null && !di.isDeliveryDue()) {
                            dt.addMessage(di);
                            deliveryDelays.put(pr, di);
                        }
                   }
              }
              // then resort the destination
              d.sort(new RefCompare());
           } catch (Exception ex) {
           }
        }

        // now route

        dsts = m.entrySet().iterator();
        while (dsts.hasNext()) {
            Map.Entry entry = (Map.Entry)dsts.next();
            DestinationUID dst = (DestinationUID) entry.getKey();
            Set l = (Set)entry.getValue();
            Destination d = dl.getDestination(dst);

            // now we're sorted, process
            Iterator litr = l.iterator();
            try {
                while (litr.hasNext()) {
                    PacketReference pr = (PacketReference)litr.next();

                    if (DEBUG) {
                        logger.log(logger.INFO, 
                        "Process takeover message "+pr+"["+pr.getDeliveryTimeInfo()+"] for destination "+d);
                    }

                    TransactionUID tuid = (TransactionUID)openMessages.get(pr.getSysMessageID());
                    if (tuid != null) {
                        dl.getTransactionList().addMessage(tuid,
                                         pr.getSysMessageID(), true);
                        pr.unlock();
                        continue;
                    }

                    ConsumerUID[] consumers = storep.getConsumerUIDs(dst, pr.getSysMessageID());
    
                    if (consumers == null) {
                        consumers = new ConsumerUID[0];
                    }
                    if (consumers.length == 0 &&
                        storep.hasMessageBeenAcked(dst, pr.getSysMessageID())) {
                        logger.log(Logger.INFO,
                            Globals.getBrokerResources().getString(
                                BrokerResources.W_TAKEOVER_MSG_ALREADY_ACKED,
                                pr.getSysMessageID()));
                        d.unputMessage(pr, RemoveReason.ACKNOWLEDGED);
                        pr.destroy();
                        pr.unlock();
                        continue;
                    }

                    if (consumers.length > 0) {
                        pr.setStoredWithInterest(true);
                    } else {
                        pr.setStoredWithInterest(false);
                    }

                    int states[] = null;
                    if (consumers.length == 0 && 
                        deliveryDelays.get(pr) == null) {
                        // route the message, it depends on the type of
                        // message 
                        try {
                            consumers = d.routeLoadedTransactionMessage(pr);
                        } catch (Exception ex) {
                            logger.logStack(Logger.WARNING,
                                Globals.getBrokerResources().getKString(
                                BrokerResources.W_EXCEPTION_ROUTE_LOADED_MSG,
                                pr.getSysMessageID(), ex.getMessage()), ex);
                        }
                        states = new int[consumers.length];
                        for (int i=0; i < states.length; i ++)  
                            states[i] = PartitionedStore.INTEREST_STATE_ROUTED;
                        try {
                            storep.storeInterestStates(
                                  d.getDestinationUID(),
                                  pr.getSysMessageID(),
                                  consumers, states, true, null);
                            pr.setStoredWithInterest(true);
                        } catch (Exception ex) {
                            // message already routed
                            StringBuffer debuf = new StringBuffer();
                            for (int i = 0; i < consumers.length; i++) {
                                if (i > 0) debuf.append(", ");
                                debuf.append(consumers[i]);
                            }
                            logger.log(logger.WARNING,
                                BrokerResources.W_TAKEOVER_MSG_ALREADY_ROUTED,
                                pr.getSysMessageID(), debuf.toString(), ex);
                        }
                    } else if (consumers.length > 0) {
                        states = new int[consumers.length];
    
                        for (int i = 0; i < consumers.length; i ++) {
                            states[i] = storep.getInterestState(
                                dst, pr.getSysMessageID(), consumers[i]);
                        }
                    }

                    pr.update(consumers, states, false);

                    // OK deal w/ transsactions
                    // LKS - XXX
                    ExpirationInfo ei = pr.getExpireInfo();
                    if (ei != null && d.expireReaper != null) {
                        d.expireReaper.addExpiringMessage(ei);
                    }
                    List<ConsumerUID> consumerList = new ArrayList(
                                          Arrays.asList(consumers));
  
                    // OK ... see if we are in txn
                    Iterator citr = consumerList.iterator();
                    while (citr.hasNext()) {
                        logger.log(Logger.DEBUG," Message " 
                             + pr.getSysMessageID() + " has " 
                             + consumerList.size() + " consumers ");
                        ConsumerUID cuid = (ConsumerUID)citr.next();
                        String key = pr.getSysMessageID() + ":" + cuid;
                        TransactionList tl = dl.getTransactionList();
                        TransactionUID tid = (TransactionUID) ackLookup.get(key);
                        if (DEBUG) {
                        logger.log(logger.INFO, "loadTakeoverMsgs: lookup "+key+" found tid="+tid);
                        }
                        if (tid != null) {
                            boolean remote = false;
                            TransactionState ts = tl.retrieveState(tid);
                            if (ts == null) {
                                ts = tl.getRemoteTransactionState(tid);
                                remote = true;
                            }
                            if (DEBUG) {
                            logger.log(logger.INFO, "tid="+tid+" has state="+
                                       TransactionState.toString(ts.getState()));
                            }
                            if (ts != null && 
                                ts.getState() != TransactionState.ROLLEDBACK &&
                                ts.getState() != TransactionState.COMMITTED) {
                                // in transaction ... 
                                if (DEBUG) {
                                    logger.log(Logger.INFO, 
                                    "loadTakeoverMsgs: Open transaction ack ["+key +"]"+
                                     (remote?"remote":"")+", TUID="+tid);
                                }
                                if (!remote) {
                                    try {
                                    tl.addAcknowledgement(tid, pr.getSysMessageID(),
                                                          cuid, cuid, true, false);
                                    } catch (TransactionAckExistException e) {

                                    //can happen if takeover tid's remote txn after restart
                                    //then txn ack would have already been loaded
                                    logger.log(Logger.INFO, 
                                               Globals.getBrokerResources().getKString(
                                               BrokerResources.I_TAKINGOVER_TXN_ACK_ALREADY_EXIST,
                                                       "["+pr.getSysMessageID()+"]"+cuid+":"+cuid, 
                                                       tid+"["+TransactionState.toString(ts.getState())+"]"));

                                    }
                                    tl.addOrphanAck(tid, pr.getSysMessageID(), cuid);

                                } 
                                citr.remove();
                                logger.log(Logger.INFO,"Processing open ack " +
                                      pr.getSysMessageID() + ":" + cuid + " on " + tid);
                                continue;
                            } else if (ts != null &&
                                       ts.getState() == TransactionState.COMMITTED) {
                                logger.log(Logger.INFO, "Processing committed ack "+
                                    pr.getSysMessageID() + ":"+cuid + " on " +tid);
                                if (pr.acknowledged(cuid, cuid, false, true)) {
                                    d.unputMessage(pr, RemoveReason.ACKNOWLEDGED);
                                    pr.destroy();
                                    continue;
                                }
                                citr.remove();   
                                continue;
                            }
                        }
                    }
                    // route msgs not in transaction 
                    if (DEBUG) {
                        StringBuffer buf = new StringBuffer();
                        ConsumerUID cid = null;
                        for (int j = 0; j <consumerList.size(); j++) {
                            cid = (ConsumerUID)consumerList.get(j);
                            buf.append(cid);
                            buf.append(" ");
                        }
                        if (deliveryDelays.get(pr) == null) {
                        logger.log(Logger.INFO, "non-transacted: Routing Message " 
                             + pr.getSysMessageID() + " to " 
                             + consumerList.size() + " consumers:"+buf.toString());
                        } else {
                        logger.log(Logger.INFO, "non-transacted: deliver time not arrived for message " 
                                   + pr.getSysMessageID());
                        }
                    }
                    pr.unlock();
                    if (deliveryDelays.get(pr) == null) {
                        if (DEBUG) {
                            logger.log(logger.INFO, "Route takeover message "+pr+
                            "["+pr.getDeliveryTimeInfo()+"] for destination "+d+
                            " to consumers "+consumerList);
                        }
                        if (pr.getDeliveryTimeInfo() != null) {
                            d.forwardDeliveryDelayedMessage(
                                  new HashSet<ConsumerUID>(consumerList), pr);
                        } else {
                            d.routeLoadedMessage(pr, consumerList);
                        }
                    } else {
                        MessageDeliveryTimeInfo di = pr.getDeliveryTimeInfo();
                        di.setDeliveryReady();
                    }
                    if (d.destReaper != null) {
                        d.destReaper.cancel();
                        d.destReaper = null;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * @deprecated
     * remove
     */
    public static String getUniqueName(boolean isQueue, String name) {
        return DestinationUID.getUniqueString(name, isQueue);
    }

    /**
     */
    PacketListDMPair packetlistAdd(SysMessageID sysid, 
        DestinationUID duid, PacketReference ref) {
        PacketListDMPair dmp = new PacketListDMPair(duid, ref);
        synchronized (packetlist) {
            Set<PacketListDMPair> s = packetlist.get(sysid);
            if (s == null) {
                s = Collections.synchronizedSet(new LinkedHashSet<PacketListDMPair>());
                packetlist.put(sysid, s);
            }
            
            s.add(dmp);
        }
        return dmp;
    }

    private DestinationUID getPacketListFirst(SysMessageID sysid) {
        Set<PacketListDMPair> s = null;
        synchronized (packetlist) {
            s = packetlist.get(sysid);
            if (s == null) {
                return null;
            }
        }
        synchronized (s) {
            Iterator<PacketListDMPair> itr = s.iterator();
            if (itr.hasNext()) {
                return itr.next().duid;
            }
        }
        return null;
    }

    Object removePacketList(SysMessageID sysid,
        DestinationUID duid, PacketReference ref) {
        synchronized (packetlist) {
            Set<PacketListDMPair> s = packetlist.get(sysid);
            if (s == null) {
                return null;
            }
            PacketListDMPair dmp = new PacketListDMPair(duid, null);
            PacketListDMPair mydmp = null;
            if (s.contains(dmp)) {
                Iterator<PacketListDMPair> itr = s.iterator();
                while (itr.hasNext()) {
                    mydmp = itr.next();
                    if (mydmp.equals(dmp)) {
                        if (ref == null) {
                            itr.remove();
                            break;
                        }
                        if (mydmp.canRemove(ref, this)) {
                            itr.remove();
                            break;
                        }
                    }
                    mydmp = null;
                }
                if (s.isEmpty()) {
                    packetlist.remove(sysid);
                }
            }
            if (mydmp != null) {
                return mydmp.duid;
            }
            return null;
        }
    }

    public static List[] findMatchingIDs(PartitionedStore ps, 
                                         DestinationUID wildcarduid) 
                                         throws PartitionNotFoundException {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            if (dl == null) {
                throw new PartitionNotFoundException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_PARTITION_NOT_FOUND, ps.getPartitionID()));
            }
            return new List[]{ dl.findMatchingIDs(wildcarduid) };
        }
        int i = 0;
        synchronized (destinationListList) {
            int sz = destinationListList.size();
            List[] matchs = new List[(sz == 0 ? 1:sz)];
            matchs[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                matchs[i++] = dl.findMatchingIDs(wildcarduid);
            }
            return matchs;
        }
    }

    public static Map<PartitionedStore, LinkedHashSet<Destination>>
        findMatchingDestinationMap(PartitionedStore ps, DestinationUID wildcarduid) {

        Map<PartitionedStore, LinkedHashSet<Destination>> map = 
            new LinkedHashMap<PartitionedStore, LinkedHashSet<Destination>>();

        DestinationList dl = null;
        LinkedHashSet dset = null;
        List<DestinationUID> duids = null;
        DestinationUID duid = null;
        Destination d = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            dset = new LinkedHashSet();     
            duids = dl.findMatchingIDs(wildcarduid);
            Iterator<DestinationUID> itr = duids.iterator();
            while (itr.hasNext()) {
                duid = itr.next();
                d = dl.getDestination(duid); 
                if (d != null) {
                    dset.add(d); 
                }
            }
            map.put(ps, dset);  
            return map;
        }

        synchronized(destinationListList) {
            for (Map.Entry<PartitionedStore, DestinationList> pair:
                 destinationListList.entrySet()) {
                 dl = pair.getValue();
                 dset = new LinkedHashSet();     
                 duids = dl.findMatchingIDs(wildcarduid);
                 Iterator<DestinationUID> itr = duids.iterator();
                 while (itr.hasNext()) {
                     duid = itr.next();
                     d = dl.getDestination(duid); 
                     dset.add(d); 
                 }
                 map.put(pair.getKey(), dset);
            }
        }  
        return map;
    }

    static List<DestinationUID> findMatchingIDsByDestinationList(
        DestinationList dl, DestinationUID wildcarduid) {
        return dl.findMatchingIDs(wildcarduid);
    }

    private List<DestinationUID> findMatchingIDs(DestinationUID wildcarduid) {
        List l = new ArrayList<DestinationUID>();
        if (!wildcarduid.isWildcard()) {
            l.add(wildcarduid);
            return l;
        }
        synchronized (destinationList) {
            Iterator itr = destinationList.keySet().iterator();
            while (itr.hasNext()) {
                DestinationUID uid = (DestinationUID)itr.next();
                if (DestinationUID.match(uid, wildcarduid)) {
                    l.add(uid);
                }
            }
        }
        return l;
        
    }

    public static void destroyTransactionList(PartitionedStore ps) {
        if (ps != null) {
            DestinationList dl = null;
            dl = destinationListList.get(ps);
            if (dl != null) {
                dl.getTransactionList().destroy();
            }
            return;
        }
        synchronized (destinationListList) {
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                itr.next().getTransactionList().destroy();
            }
        }
    }

    public static void clearDestinations(PartitionedStore ps) {
        if (ps != null) {
            DestinationList dl = null;
            dl = destinationListList.get(ps);
            if (dl != null) {
                dl.clearDestinations();
            }
            return;
        }
        synchronized (destinationListList) {
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                itr.next().clearDestinations();
            }
        }
        Queue.clear();
    }

    private void clearDestinations() {
        destsLoaded = false;
        destinationList.clear();
        packetlist.clear();
        inited = false;

        BrokerConfig cfg = Globals.getConfig();

        cfg.removeListener(SYSTEM_MAX_SIZE, cl);
        cfg.removeListener(SYSTEM_MAX_COUNT, cl);
        cfg.removeListener(MAX_MESSAGE_SIZE, cl);
        cfg.removeListener(AUTO_QUEUE_STR, cl);
        cfg.removeListener(AUTO_TOPIC_STR, cl);
        cfg.removeListener(DST_REAP_STR, cl);
        cfg.removeListener(MSG_REAP_STR, cl);
        cfg.removeListener(AUTO_MAX_NUM_MSGS, cl);
        cfg.removeListener(AUTO_MAX_TOTAL_BYTES, cl);
        cfg.removeListener(AUTO_MAX_BYTES_MSG, cl);
        cfg.removeListener(AUTO_MAX_NUM_PRODUCERS, cl);
        cfg.removeListener(AUTO_LOCAL_ONLY, cl);
        cfg.removeListener(AUTO_LIMIT_BEHAVIOR, cl);
        cfg.removeListener(USE_DMQ_STR, cl);
        cfg.removeListener(TRUNCATE_BODY_STR, cl);
        cfg.removeListener(LOG_MSGS_STR, cl);
        cl = null;
    }

    public static void addDestination(PartitionedStore ps, Destination d, boolean throwRT) {
        if (ps != null) {
            destinationListList.get(ps).addDestination(d, throwRT);
            return;
        }

        DestinationList dl = null;
        synchronized(destinationListList) {
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) { //XXX throwRT check
                dl = itr.next();
                dl.addDestination(d, throwRT);
            }
        }
    }
    
    private void addDestination(Destination d, boolean throwRT) {
        synchronized (destinationList) {
            if (destinationList.get(d.getDestinationUID()) != null) {
                 if (throwRT)
                     throw new RuntimeException("Destination " + d
                        + " is also being" + " created by another broker");
                 return;
            }

            destinationList.put(d.getDestinationUID(), d);
	    Agent agent = Globals.getAgent();
	    if (agent != null)  {
	        agent.registerDestination(d);
	        agent.notifyDestinationCreate(d);
	    }
        }
    }

    boolean setDuraSubscriptionInited() {
        synchronized(subscriptionLock) {
            if (duraSubscriptionInited) {
                return false;
            }
            duraSubscriptionInited = true;
            return true;
        }
    }

    boolean setNonDuraSharedSubscriptionInited() {
        synchronized(subscriptionLock) {
            if (nonDuraSharedSubscriptionInited) {
                return false;
            }
            nonDuraSharedSubscriptionInited = true;
            return true;
        }
    }

    public LinkedHashMap processTransactions(Map inprocessAcks, Map openTrans, Map committingTrans)
        throws BrokerException {

         loadDestinations();
         Subscription.initDuraSubscriptions(this);
         LinkedHashMap prepared = new LinkedHashMap();
         Iterator itr = getAllDestinations(ALL_DESTINATIONS_MASK);
         while (itr.hasNext()) {
             Destination d = (Destination)itr.next();
             boolean loaded = d.loaded;
             if (loaded)
                 d.unload(true);
             LinkedHashMap m = d.load(false, inprocessAcks, openTrans, committingTrans, null, false);
             if (m != null) {
                 prepared.putAll(m);
             }
         }
         return prepared;
    }
    
    public static void loadDestinations(PartitionedStore ps)
    throws BrokerException {
        if (ps == null) {
            throw new BrokerException(
            "IllegalArgument: PartitionedStore null"); 
        }
        DestinationList dl = destinationListList.get(ps);
        dl.loadDestinations();
    }

    private void loadDestinations() throws BrokerException {

        synchronized(destinationListLock) {
            if (destsLoaded) {
                return;
            }
            destsLoaded = true;
        }

        logger.log(Logger.INFO, br.getKString(br.I_RETRIEVE_STORED_DESTINATIONS)+logsuffix);

        // before we do anything else, make sure we dont have any
        // unexpected exceptions
        LoadException load_ex = pstore.getLoadDestinationException();

        if (load_ex != null) {
            // some messages could not be loaded
            LoadException processing = load_ex;
            while (processing != null) {
                String destid = (String)processing.getKey();
                Destination d = (Destination)processing.getValue();
                if (destid == null && d == null) {
                    logger.log(Logger.WARNING, "LoadDestinationException: "+
                               "Both key and value are corrupted");
                    continue;
                }
                if (destid == null) { 
                    // store with valid key
                    try {
                        pstore.storeDestination(d, PERSIST_SYNC);
                    } catch (Exception ex) {
                        logger.log(Logger.WARNING, 
                            BrokerResources.W_DST_RECREATE_FAILED,
                              d.toString(), ex);
                        try {
                            pstore.removeDestination(d, true);
                        } catch (Exception ex1) {
                            logger.logStack(Logger.DEBUG,"Unable to remove dest", ex1);
                        }
                    }
                } else {
                    DestinationUID duid = new DestinationUID(destid);
                    String name = duid.getName();
                    boolean isqueue = duid.isQueue();
                    int type = isqueue ? DestType.DEST_TYPE_QUEUE
                            : DestType.DEST_TYPE_TOPIC;
                    // XXX we may want to parse the names to determine
                    // if this is a temp destination etc
                    try {
                        d = createDestination(name, type);
                        d.store();
                        logger.log(Logger.WARNING, 
                            BrokerResources.W_DST_REGENERATE,
                                    duid.getLocalizedName());
                    } catch (Exception ex) {
                        logger.log(Logger.WARNING, 
                            BrokerResources.W_DST_REGENERATE_ERROR,
                                  duid, ex);
                        try {
                            if (duid.isQueue()) {
                                d = new Queue(duid);
                            } else {
                                d = new Topic(duid);
                            }
                            pstore.removeDestination(d, true);
                        } catch (Exception ex1) {
                            logger.logStack(Logger.DEBUG,
                                "Unable to remove dest", ex1);
                        }
                    }

                } // end if
                processing = processing.getNextException();
            } // end while
        } 

       // retrieve stored destinations
        try {
            Destination dests[] = pstore.getAllDestinations();
            logger.log(Logger.INFO, br.getKString(
                br.I_RETRIEVED_STORED_DESTINATIONS, 
                String.valueOf(dests.length))+logsuffix);

            for (int i =0; i < dests.length; i ++) {
                if ( dests[i] == null) {
                    continue;
                }
                if (DEBUG) {
                    logger.log(Logger.INFO, "Process stored destination "+dests[i].toString());
                }
                dests[i].setDestinationList(this);
                if (!dests[i].isAdmin() && (dests[i].getIsDMQ() || !dests[i].isInternal())) {
                    dests[i].initialize();
                }
                if (dests[i].isAutoCreated() &&
                    dests[i].size == 0 &&  
                    dests[i].bytes == 0) {
                    destinationList.remove(dests[i].getDestinationUID());
                    try {
                        Globals.getLogger().log(logger.INFO,
                            br.getKString(br.I_DESTROYING_DESTINATION,
                            dests[i].getName())+logsuffix);

                        dests[i].destroy(Globals.getBrokerResources().getString
                                (BrokerResources.M_AUTO_REAPED)+logsuffix);

                    } catch (BrokerException ex) {
                        // if HA, another broker may have removed this
                        //
                        if (ex.getStatusCode() == Status.NOT_FOUND) {
                            // someone else removed it already
                            return;
                        }
                        throw ex;
                    }
                } else {
                    addDestination(dests[i], false);
                }
            }

            deadMessageQueue = createDMQ();

            // iterate through and deal with monitors
            Iterator itr = destinationList.values().iterator();
            while (itr.hasNext()) {
                Destination d = (Destination)itr.next();
                try {
                    d.initMonitor();
                } catch (IOException ex) {
                    logger.logStack(logger.INFO,
                          BrokerResources.I_CANT_LOAD_MONITOR,
                          d.toString(),
                          ex);
                    itr.remove();
               }
            }

        } catch (BrokerException ex) {
            logger.logStack(Logger.ERROR, BrokerResources.X_LOAD_DESTINATIONS_FAILED, ex);
            throw ex;
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR, BrokerResources.X_LOAD_DESTINATIONS_FAILED, ex);
            throw new BrokerException(BrokerResources.X_LOAD_DESTINATIONS_FAILED, ex);
        } finally {
        }

    }

    public static Destination[] getLoadedDestination(PartitionedStore ps, DestinationUID uid) {
        DestinationList dl = null;
        if (ps != null) { 
            dl =  destinationListList.get(ps);
            Destination d =  dl.getLoadedDestination(uid);
            return new Destination[]{ d };
        }

        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.getLoadedDestination(uid);
            }
            return dsts;
        } 
    }

    private Destination getLoadedDestination(DestinationUID uid) {
        Destination d = null;
        synchronized(destinationList) {
            d = (Destination)destinationList.get(uid);
        }
        if ((d != null) && !d.isDestInited()) {
            d.initialize();
        }
        return d;
    }

    static Destination getDestinationByDestinationList(
               DestinationList dl, DestinationUID uid) {
        return dl.getDestination(uid);
    }

    public static Destination[] getDestination(PartitionedStore ps, 
                                               DestinationUID uid) {
        Destination[] ret = null;
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            if (dl == null) {
                ret = new Destination[]{ null }; 
            } else {
                Destination d = dl.getDestination(uid);
                ret = new Destination[]{ d };
            }
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.getDestination(uid);
            }
            if (ps == null) {
                return dsts;
            }
        }  
        return ret;
    }

    public static Map<PartitionedStore, Destination> 
        getDestinationMap(PartitionedStore ps, DestinationUID uid) {

        Map<PartitionedStore, Destination> map = 
            new LinkedHashMap<PartitionedStore, Destination>();

        DestinationList dl = null;
        Destination d = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            d = dl.getDestination(uid);
            if (d != null) {
                map.put(ps, d);
            }
            return map;
        }

        synchronized(destinationListList) {
            for (Map.Entry<PartitionedStore, DestinationList> pair:
                destinationListList.entrySet()) {
                dl = pair.getValue();
                d = dl.getDestination(uid);
                if (d != null) {
                    map.put(pair.getKey(), d);
                }
            }
        }  
        return map;
    }

    Destination getDestination(DestinationUID uid) {
        Destination d = null;
        synchronized(destinationList) {
            d = (Destination)destinationList.get(uid);

            if (d == null) {
                try {
                    d = pstore.getDestination(uid);
                    if (d != null) {
                        d.setDestinationList(this);
                        addDestination(d, false);
                    }
                } catch (Exception ex) {
                    // ignore we want to create it
                }
            }
        }
        if ((d != null) && !d.isDestInited()) {
            d.initialize();
        }
        return d;
    }

    public static Destination[] findDestination(PartitionedStore ps, DestinationUID uid) {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            return new Destination[]{ dl.findDestination(uid) };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.findDestination(uid);
            }
            return dsts;
        }  
    }

    Destination findDestination(DestinationUID uid) {
        Destination d = null;
        synchronized(destinationList) {
            d = (Destination)destinationList.get(uid);
        }
        return d;
    }


    // XXX : Destination class public methods should normally accept
    // DestinationUID to identify destinations. (Instead of name,
    // type).
    public static Destination[] getDestination(PartitionedStore ps, String name, boolean isQueue) 
    throws BrokerException, IOException {

        DestinationUID uid = new DestinationUID(name, isQueue);
        return getDestination(ps, uid);
    }

    public static Destination[] findDestination(PartitionedStore ps, String name, boolean isQueue) 
    throws BrokerException, IOException {

        DestinationUID uid = new DestinationUID(name, isQueue);
        return findDestination(ps, uid);
    }


    public static Destination[] getLoadedDestination(PartitionedStore ps, String name, boolean isQueue) 
    throws BrokerException, IOException {

        DestinationUID uid = new DestinationUID(name, isQueue);
        return getLoadedDestination(ps, uid);
    }

    
    static Destination getDestinationByDestinationList(
               DestinationList dl, String name, int type, 
               boolean autocreate, boolean store) 
               throws BrokerException, IOException {
        DestinationUID uid = new DestinationUID(name, DestType.isQueue(type)); 
        return dl.getDestination(uid, type, autocreate, store);
    }

    public static Destination[] getDestination(PartitionedStore ps, String name, int type, 
              boolean autocreate, boolean store) 
              throws BrokerException, IOException {

        DestinationUID uid = new DestinationUID(name, DestType.isQueue(type)); 
        return getDestination(ps, uid, type, autocreate, store);
    }

    /**
     * @param uid the destination uid 
     * @param type the type of destination specified by uid
     */
    public static Destination[] getDestination(PartitionedStore ps, 
                                DestinationUID uid, int type,
                                boolean autocreate, boolean store) 
                                throws BrokerException, IOException {
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps); 
            return new Destination[]{ dl.getDestination(uid, type, autocreate, store) }; 
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.getDestination(uid, type, autocreate, store);  
            }
            return dsts;
        }
    }

    private Destination getDestination(DestinationUID uid, int type,
                              boolean autocreate, boolean store) 
                              throws BrokerException, IOException {

        Destination d = (Destination)destinationList.get(uid);

        if (autocreate && d == null) {
            try {
               d = createDestination(uid.getName(), type, store, autocreate,
                       null, true, CAN_USE_LOCAL_DEST && DestType.isLocal(type));
            } catch (ConflictException ex) {
                //re-get destination             
                d = (Destination)destinationList.get(uid);
            }
        }
        if (d != null && !d.isDestInited()) {
            d.initialize();
        }
        return d;
    }

    public static Destination[] createDestination(PartitionedStore ps, String name, int type) 
    throws BrokerException, IOException {

        Destination[] ret = null;
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            Destination d = dl.createDestination(name, type);
            ret = new Destination[]{ d };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.createDestination(name, type);
            }
            if (ps == null) {
                return dsts;
            }
        }
        return ret;
    }

    private Destination createDestination(String name, int type)
    throws BrokerException, IOException {

	Destination d = createDestination(name, type, true,
		 false, null, true, false);

	if (d != null && !d.isDestInited())
		d.initialize();

	return d;
    }

    public static Destination[] createTempDestination(PartitionedStore ps,
           String name, int type, ConnectionUID uid, boolean store, long time) 
           throws BrokerException, IOException {

        Destination[] ret = null;
        DestinationList dl = null;

        if (ps != null) {
            dl = destinationListList.get(ps);
            if (dl == null) {
                String emsg = "I18NNot found store partition:" +ps+" in "+destinationListList;
                Globals.getLogger().log(Logger.WARNING,  emsg);
                throw new BrokerException(emsg, Status.NOT_FOUND);
            }
            Destination d = dl.createTempDestination(name, type, uid, store, time);
            ret =  new Destination[]{ d };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.createTempDestination(name, type, uid, store, time);
            }
            if (ps == null) {
                return dsts;
            }
        }
        return ret;
    }

    private Destination createTempDestination(String name, 
        int type, ConnectionUID uid, boolean store, long time) 
        throws BrokerException, IOException {

        Destination d = null;
        try {
            d = createDestination(name, type, false, false, uid, true,
                                  CAN_USE_LOCAL_DEST && DestType.isLocal(type));
            d.setReconnectInterval(time);
            d.overridePersistence(store);
            d.store();
        } catch (ConflictException ex) {
            DestinationUID duid = new DestinationUID(name, DestType.isQueue(type));
            d = getDestination(duid, type, false, false);
        }

        return d;
    }

    boolean isValid() {
        return valid;
    }

    public static void shutdown() {
        shutdown = true;
        synchronized(destinationListList) {
            Iterator<DestinationList> itr = 
                destinationListList.values().iterator();
            while (itr.hasNext()) {
                itr.next().valid = false;
            }
        }
        try {
            partitionMonitor.shutdown();
            if (!partitionMonitor.awaitTermination(30, TimeUnit.SECONDS)) {
                Globals.getLogger().log(Logger.INFO, "Force  partition monitor shutdown");
                partitionMonitor.shutdownNow();
                partitionMonitor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            partitionMonitor.shutdownNow();
        }
    }

    public static boolean isShutdown() {
        return shutdown;
    }


    public static Destination[] createDestination(PartitionedStore ps, 
            String name, int type, boolean store, 
            boolean autocreated, Object from) 
            throws BrokerException, IOException {

        ConnectionUID uid = null;
        boolean remote = false;
        if (from instanceof ConnectionUID) {
            uid = (ConnectionUID) from;
        }
        if (from instanceof BrokerAddress) {
            remote = ((BrokerAddress)from).equals(Globals.getMyAddress());
        }

        Destination[] ret = null;
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            Destination d = dl.createDestination(name, type, store, autocreated,
                            uid, !remote, CAN_USE_LOCAL_DEST && DestType.isLocal(type)); 
            ret = new Destination[]{ d };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] =  dl.createDestination(name, type, store, autocreated,
                             uid, !remote, CAN_USE_LOCAL_DEST && DestType.isLocal(type)); 
            }
            if (ps == null) {
                return dsts;
            }
        }
        return ret;
    }


    // XXX : The public createDestination methods can be renamed so
    // that it is easier to find the right variant. (e.g.
    // createTempDestination, createAutoDestination,
    // createClusterDestination etc...

    private Destination createDestination(String name, int type,
            boolean store, boolean autocreated, ConnectionUID uid,
            boolean notify, boolean localOnly) 
            throws BrokerException, IOException {

        DestinationUID duid = new DestinationUID(name, DestType.isQueue(type));
        if (!valid) {
            if (!DL.isPartitionMode()) {
                throw new BrokerException(
                       Globals.getBrokerResources().getKString(
                           BrokerResources.X_SHUTTING_DOWN_BROKER),
                       BrokerResources.X_SHUTTING_DOWN_BROKER,
                       (Throwable) null,
                       Status.ERROR );
            } else {
                throw new BrokerException(br.getKString(
                    br.I_PARTITION_IS_CLOSING, logsuffix));
            }
        }
        if (destinationList.get(duid) != null) {
            throw new ConflictException(
                   Globals.getBrokerResources().getKString(
                        BrokerResources.X_DESTINATION_EXISTS,
                        duid));
        }
        // OK, check the persistent store (required for HA)
        try {
            Destination d = pstore.getDestination(duid);
            if (d != null) {
                d.setDestinationList(this);
                addDestination(d, false);
                return d;
            }
        } catch (Exception ex) {
            // ignore we want to create it
        }

       ClusterBroadcast mbi = Globals.getClusterBroadcast();

       boolean clusterNotify = false;
       Destination d = null;
       try {
            if (DestType.isQueue(type)) {
                d = new Queue(name, type, store, uid, autocreated, this);
            } else {
                d = new Topic(name, type, store, uid, autocreated, this);
            }
            d.setClusterNotifyFlag(notify);

            try {
              synchronized (destinationList) {
                    Destination newd = (Destination)destinationList.get(duid);
                    if (newd != null) { // updating existing
                        String emsg = Globals.getBrokerResources().getKString(
                                      BrokerResources.X_DESTINATION_EXISTS, duid.getLongString());
                        throw new BrokerException(emsg, Status.CONFLICT);
                    }

                    if (!autocreated) {
                        d.setIsLocal(localOnly);
                    }
                    if (store) {
                        d.store();
                    }
                    destinationList.put(duid, d);
               }
            } catch (BrokerException ex) {
                    // may happen with timing of two brokers in an
                    // HA cluster
                    // if this happens, we should try to re-load the 
                    // destination
                    //
                    // so if we get a Broker Exception, throw a new
                    // Conflict message
                    if (ex.getStatusCode() != Status.CONFLICT) {
                        throw new BrokerException(ex.getMessage(), ex, Status.CONFLICT);
                    }
                    throw ex;

            }
            clusterNotify = !d.isAutoCreated() && d.sendClusterUpdate() && notify;

            if (mbi != null && clusterNotify ) { // only null in standalone tonga test
                // prevents two creates at the same time
                // if this is a response to a creation on another broker ..
                // dont worry about locking
                if (!mbi.lockDestination(duid, uid)) {
                     throw new ConflictException("Internal Exception:"
                       + " Destination " + duid + " is in the process"
                       + " of being created");
                }
            }
            if (clusterNotify && mbi != null ) {
                // we dont care about updating other brokers for 
                // autocreated, internal or admin destinations
                // we may or may not update local dests (depends on version
                // of cluster)
                mbi.createDestination(d);
            }
        } finally {
            if (mbi != null && clusterNotify) { // only null in tonga test
                mbi.unlockDestination(duid, uid);
            }
        }

        // NOW ATTACH ANY WILDCARD PRODUCERS OR CONSUMERS
        Iterator itr = Consumer.getWildcardConsumers();
        while (itr.hasNext()) {
            ConsumerUID cuid = (ConsumerUID) itr.next();
            Consumer c = Consumer.getConsumer(cuid);
            if (c == null){
                Globals.getLogger().log(Logger.INFO,"Consumer already destroyed");
                continue;
            }
            DestinationUID wuid = c.getDestinationUID();
            // compare the uids
            if (DestinationUID.match(d.getDestinationUID(), wuid)) {
                try {
                // attach the consumer
                    if (c.getSubscription() != null) {
                         d.addConsumer(c.getSubscription(), false /* XXX- TBD*/);
                    } else {
                        d.addConsumer(c, false);
                    }


                } catch (SelectorFormatException ex) {
                   //LKS TBD
                }
            }
         }

        itr = Producer.getWildcardProducers();
        while (itr.hasNext()) {
            ProducerUID puid = (ProducerUID) itr.next();
            Producer p = (Producer)Producer.getProducer(puid);
            DestinationUID wuid = p.getDestinationUID();
            // compare the uids
            if (DestinationUID.match(d.getDestinationUID(), wuid)) {
                // attach the consumer
                d.addProducer(p);
            }
        }

	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.registerDestination(d);
	    agent.notifyDestinationCreate(d);
	}
        return d;
    }

    public static Destination[] removeDestination(PartitionedStore ps, String name,
                                                  boolean isQueue, String reason)
                                                  throws IOException, BrokerException {
    
        DestinationUID duid = new DestinationUID(name, isQueue);
        return removeDestination(ps, duid, true, reason);
    }

    public static Destination[] removeDestination(PartitionedStore ps, DestinationUID uid,
                                           boolean notify, String reason)
                                           throws IOException, BrokerException {
        Destination[] ret = null;
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps);
            Destination d = dl.removeDestination(uid, true, reason);
            ret = new Destination[]{ d };
        }
        int i = 0;
        synchronized(destinationListList) {
            int sz = destinationListList.size();
            Destination[] dsts = new Destination[(sz == 0 ? 1:sz)];
            dsts[0] = null;
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                dsts[i++] = dl.removeDestination(uid, notify, reason);
            }
            if (ps == null) {
                return dsts;
            }
        }
        return ret;
    }

    Destination removeDestination(DestinationUID uid, boolean notify, String reason)
    throws IOException, BrokerException {

        Destination d = null;
        boolean noerrnotfound = Globals.getHAEnabled() && !notify;
        if (noerrnotfound) {
            // Quick check to see if dst is in memory; doesn't load/initialize
            d = findDestination(uid);
            if (d != null && !d.isTemporary()) {
                // Because temp dst can be deleted in HA, do this check to avoid
                // getting error during load if it has already been deleted
                d = getDestination(uid);
            }
        } else {
            d = getDestination(uid);
        }

        if (d != null) {
            if (d.isDMQ) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.X_DMQ_INVAID_DESTROY));
            } else if (notify && d.sendClusterUpdate() && !d.isTemporary()) {
                Globals.getClusterBroadcast().recordRemoveDestination(d);
            }

            int level = (DestType.isAdmin(d.getType()) ? Logger.DEBUG : Logger.INFO);
            Globals.getLogger().log(level,
                BrokerResources.I_DESTROYING_DESTINATION, d.getName());
        }

        try {
            d =(Destination)destinationList.get(uid);
            DestinationUID.clearUID(uid); // remove from cache
            if (d != null) {
                if (d.producers.size() > 0) {
                    String args[] = { d.getName(),
                       String.valueOf(d.producers.size()), reason};
                    
                    Globals.getLogger().log(Logger.WARNING,
                        BrokerResources.W_DST_ACTIVE_PRODUCERS, args);
                } 
                if (d.consumers.size() > 0) {
                    int csize = d.consumers.size();
                    boolean destroyDurables = false;
                    Set cons = new HashSet(d.consumers.values());
                    Iterator itr = cons.iterator();
                    while (itr.hasNext()) {
                        Consumer c = (Consumer)itr.next();
                        if (c instanceof Subscription && 
                               ((Subscription)c).isDurable()) {
                            destroyDurables = true;
                            Subscription s = (Subscription)c;
                            if (s.isActive()) {
                                csize += s.getActiveSubscriberCnt();
                            }
                            Subscription.unsubscribeOnDestroy(
                                s.getDurableName(),
                                s.getClientID(), notify);
                            csize --;
                         }
                    }
                    if (destroyDurables) {
                        Globals.getLogger().log(Logger.INFO,
                              BrokerResources.I_DST_DURABLE_RM,
                              d.toString(), reason);
                    }
                    if (csize > 0) {
                        String args[] = { d.getName(),
                           String.valueOf(csize),
                           reason};
                        Globals.getLogger().log(Logger.WARNING,
                            BrokerResources.W_DST_ACTIVE_CONSUMERS,
                            args);
                    }
                }
                if (d.size() > 0) {
                    logger.log(Logger.WARNING, br.getKString(br.W_REMOVING_DST_WITH_MSG,
                               String.valueOf(d.size()), d.toString())+logsuffix);
                }
                d.destroy(reason, noerrnotfound);
                if (notify && d.sendClusterUpdate())
                    Globals.getClusterBroadcast().destroyDestination(d);

	        Agent agent = Globals.getAgent();
	        if (agent != null)  {
	            agent.notifyDestinationDestroy(d);
	            agent.unregisterDestination(d);
	        }
            } 
        } finally {
            d =(Destination)destinationList.remove(uid);
        }
        return d ;
    }

    public static boolean removeDestination(PartitionedStore ps, Destination dest, String reason) 
    throws IOException, BrokerException {

        Destination[] dsts = removeDestination(ps, dest.getDestinationUID(), true, reason);
        return (dsts != null && dsts.length == 1);
    }


    /**
     * id  is the ID (if any) to match
     * mast is the DestType to match
     *
     * @param id is either a BrokerAddress or a ConnectionUID
     */
    private Iterator getDestinations(Object id, int mask) {

        List tq = new ArrayList();

        synchronized (destinationList) {
            Collection values = destinationList.values();
            Iterator itr = values.iterator();
            while (itr.hasNext()) {
                Destination dest = (Destination)itr.next();
                if (((dest.getType() & mask) == mask) && 
                    (id == null || id.equals(dest.getConnectionUID()) ||
                     ((id instanceof BrokerAddress) && id == Globals.getMyAddress()
                      && dest.getClusterNotifyFlag() && dest.sendClusterUpdate()))) 

                {
                    tq.add(dest);
                }
            }

        }
        return tq.iterator();
        
    }

    public static Iterator[] getAllDestinations(PartitionedStore ps) {
        if (ps != null) {
            DestinationList dl = destinationListList.get(ps);
            if (dl == null) {
                return new Iterator[]{ null };
            }
            return new Iterator[]{ dl.getAllDestinations(ALL_DESTINATIONS_MASK)};
        }
        ArrayList<DestinationList> dls = null;
        synchronized(destinationListList) {
            dls = new ArrayList(destinationListList.values());
        }
        int sz = dls.size();
        Iterator[] itrs = new Iterator[(sz == 0 ? 1: sz)];
        itrs[0] = null;
        int i = 0;
        Iterator<DestinationList> itr =  dls.iterator();
        while (itr.hasNext()) {
            itrs[i++] = itr.next().getAllDestinations(ALL_DESTINATIONS_MASK);
        }
        return itrs;
    }

    private Iterator getAllDestinations(int mask) {

        return getDestinations(null, mask);
    }

    public static Iterator[] getTempDestinations(PartitionedStore ps, BrokerAddress address) {
        if (ps != null) {
            DestinationList dl = destinationListList.get(ps);
            return new Iterator[]{ dl.getDestinations(address, TEMP_DESTINATIONS_MASK) };
        }
        ArrayList<DestinationList> dls = null;
        synchronized(destinationListList) {
            dls = new ArrayList(destinationListList.values());
        }
        Iterator[] itrs = new Iterator[dls.size()];
        int i = 0;
        Iterator<DestinationList> itr =  dls.iterator();
        while (itr.hasNext()) {
            itrs[i] = itr.next().getDestinations(address, TEMP_DESTINATIONS_MASK);
        }
        return itrs;
    }

    public static Iterator[] getStoredDestinations(PartitionedStore ps) {
        if (ps != null) {
            DestinationList dl = destinationListList.get(ps);
            Iterator itr = dl.getDestinations(null,
                           (ALL_DESTINATIONS_MASK & (~ DestType.DEST_TEMP))); 
            return new Iterator[]{ itr };
        }
        ArrayList<DestinationList> dls = null;
        synchronized(destinationListList) {
            dls = new ArrayList(destinationListList.values());
        }
        Iterator[] itrs = new Iterator[dls.size()];
        int i = 0;
        Iterator<DestinationList> itr =  dls.iterator();
        while (itr.hasNext()) {
            itrs[i] = itr.next().getDestinations(null,
                      (ALL_DESTINATIONS_MASK & (~ DestType.DEST_TEMP)));
        }
        return itrs;
    }


    private static ConfigListener cl = new ConfigListener() {
                public void validate(String name, String value)
                    throws PropertyUpdateException {
            
                }
            
                public boolean update(String name, String value) {
                    BrokerConfig cfg = Globals.getConfig();
                    if (name.equals(SYSTEM_MAX_SIZE)) {
                        setMaxSize(cfg.getSizeProperty(SYSTEM_MAX_SIZE));
                    } else if (name.equals(SYSTEM_MAX_COUNT)) {
                        setMaxMessages(cfg.getIntProperty(SYSTEM_MAX_COUNT));
                    } else if (name.equals(MAX_MESSAGE_SIZE)) {
                        setIndividualMessageMax(
                          cfg.getSizeProperty(MAX_MESSAGE_SIZE));
                    } else if (name.equals(AUTO_QUEUE_STR)) {
                          ALLOW_QUEUE_AUTOCREATE = cfg.getBooleanProperty(
                               AUTO_QUEUE_STR);
                    } else if (name.equals(AUTO_TOPIC_STR)) {
                          ALLOW_TOPIC_AUTOCREATE = cfg.getBooleanProperty(
                               AUTO_TOPIC_STR);
                    } else if (name.equals(DST_REAP_STR)) {
                          AUTOCREATE_EXPIRE = cfg.getLongProperty(
                               DST_REAP_STR) * 1000L;
                    } else if (name.equals(MSG_REAP_STR)) {
                          MESSAGE_EXPIRE = cfg.getLongProperty(
                               MSG_REAP_STR) * 1000L;
                    } else if (name.equals(AUTO_MAX_NUM_MSGS)) {
                          defaultMaxMsgCnt = cfg.getIntProperty(
                               AUTO_MAX_NUM_MSGS);
                    } else if (name.equals(AUTO_MAX_TOTAL_BYTES)) {
                          defaultMaxMsgBytes = cfg.getSizeProperty(
                               AUTO_MAX_TOTAL_BYTES);
                    } else if (name.equals(AUTO_MAX_BYTES_MSG)) {
                          defaultMaxBytesPerMsg = cfg.getSizeProperty(
                               AUTO_MAX_BYTES_MSG);
                    } else if (name.equals(AUTO_MAX_NUM_PRODUCERS)) {
                          defaultProducerCnt = cfg.getIntProperty(
                               AUTO_MAX_NUM_PRODUCERS);
                    } else if (name.equals(AUTO_LOCAL_ONLY)) {
                          defaultIsLocal = cfg.getBooleanProperty(
                               AUTO_LOCAL_ONLY);
                    } else if (name.equals(AUTO_LIMIT_BEHAVIOR)) {
                          defaultLimitBehavior =
                              DestLimitBehavior.getStateFromString(
                                  Globals.getConfig().
                                  getProperty(AUTO_LIMIT_BEHAVIOR));
                    } else if (name.equals(USE_DMQ_STR)) {
                          autocreateUseDMQ = cfg.getBooleanProperty(
                               USE_DMQ_STR);
                    } else if (name.equals(TRUNCATE_BODY_STR)) {
                          storeBodyWithDMQ = !cfg.getBooleanProperty(
                               TRUNCATE_BODY_STR);
                    } else if (name.equals(LOG_MSGS_STR)) {
                          verbose = cfg.getBooleanProperty(
                               LOG_MSGS_STR);
                    } else if (name.equals(DEBUG_LISTS_PROP)) {
                          DEBUG_LISTS = Boolean.valueOf(value);
                    } else if (name.equals(CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO_PROP)) {
                          CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO = cfg.getIntProperty(
                              CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO_PROP);
                    } else if (name.equals(CHECK_MSGS_RATE_FOR_ALL_PROP)) {
                          CHECK_MSGS_RATE_FOR_ALL = cfg.getBooleanProperty(
                              CHECK_MSGS_RATE_FOR_ALL_PROP);
                    }
                    return true;
                }

            };


    public static void doCheckpoint(PartitionedStore ps, boolean sync) {
        if (ps != null) {
            if (ps instanceof TxnLoggingStore) {
                ((TxnLoggingStore)ps).doCheckpoint(sync);
            }
            return;
        }
        ArrayList<PartitionedStore> stores = null;
        synchronized(destinationListList) { 
            stores = new ArrayList(destinationListList.keySet());
        }
        Iterator<PartitionedStore> itr = stores.iterator();
        while (itr.hasNext()) {
            ps = itr.next();
            if (ps instanceof TxnLoggingStore) {
                Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().
                    getKString(BrokerResources.I_CHECKPOINT_BROKER+"["+ps+"]"));
                ((TxnLoggingStore)ps).doCheckpoint(sync);
            }
        }
    }

    public static boolean isPartitionMode() {
        if (!partitionModeInited) {
            throw new IllegalStateException("DestinationList not initialized !");
        }
        return partitionMode;
    }

    public static boolean isPartitionMigratable() {
        if (!partitionModeInited) {
            throw new IllegalStateException("DestinationList not initialized !");
        }
        return partitionMigratable;
    }

    /**
     */
    public static void init() throws BrokerException {

        if (inited) {
            if (shutdown) {
                throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_SHUTTING_DOWN_BROKER));
            }
            return;
        }
        shutdown = false;
        inited = true;

        if (defaultIsLocal && !CAN_USE_LOCAL_DEST) {
            Globals.getLogger().log(Logger.ERROR,
               BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
               Globals.getBrokerResources().getString(
                    BrokerResources.M_LOCAL_DEST)); 
            com.sun.messaging.jmq.jmsserver.Broker.getBroker().exit(
                   1, Globals.getBrokerResources().getKString(
                  BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
               Globals.getBrokerResources().getString(
                    BrokerResources.M_LOCAL_DEST)),
                BrokerEvent.Type.FATAL_ERROR);

        }
        if (canAutoCreate(true))   {
            Globals.getLogger().log(Logger.INFO,
              BrokerResources.I_QUEUE_AUTOCREATE_ENABLED);
        } 
        if (!canAutoCreate(false)) {
            Globals.getLogger().log(Logger.INFO,
              BrokerResources.I_TOPIC_AUTOCREATE_DISABLED);
        } 

        BrokerConfig cfg = Globals.getConfig();

        // OK add listeners for the properties
        cfg.addListener(SYSTEM_MAX_SIZE, cl);
        cfg.addListener(SYSTEM_MAX_COUNT, cl);
        cfg.addListener(MAX_MESSAGE_SIZE, cl);
        cfg.addListener(AUTO_QUEUE_STR, cl);
        cfg.addListener(AUTO_TOPIC_STR, cl);
        cfg.addListener(DST_REAP_STR, cl);
        cfg.addListener(MSG_REAP_STR, cl);
        cfg.addListener(AUTO_MAX_NUM_MSGS, cl);
        cfg.addListener(AUTO_MAX_TOTAL_BYTES, cl);
        cfg.addListener(AUTO_MAX_BYTES_MSG, cl);
        cfg.addListener(AUTO_MAX_NUM_PRODUCERS, cl);
        cfg.addListener(AUTO_LOCAL_ONLY, cl);
        cfg.addListener(AUTO_LIMIT_BEHAVIOR, cl);
        cfg.addListener(USE_DMQ_STR, cl);
        cfg.addListener(TRUNCATE_BODY_STR, cl);
        cfg.addListener(LOG_MSGS_STR, cl);
        cfg.addListener(DEBUG_LISTS_PROP, cl);
        cfg.addListener(CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO_PROP, cl);
        cfg.addListener(CHECK_MSGS_RATE_FOR_ALL_PROP, cl);

        // now configure the system based on the properties
        setMaxSize(cfg.getSizeProperty(SYSTEM_MAX_SIZE));
        setMaxMessages(cfg.getIntProperty(SYSTEM_MAX_COUNT));
        setIndividualMessageMax(cfg.getSizeProperty(MAX_MESSAGE_SIZE));

        Queue.init();

        if (Globals.getStore().getPartitionModeEnabled()) {
            try {
                String typ = cfg.getProperty(CONN_STRATEGY_PROP, CONN_STRATEGY_DEFAULT);
                String cl = null;
                if (typ.equalsIgnoreCase(MIN_CONN_STRATEGY)) {
                    cl = MIN_CONN_STRATEGY_CLASS;
                } else if (typ.equalsIgnoreCase(RR_CONN_STRATEGY)) {
                    cl = RR_CONN_STRATEGY_CLASS;
                } else {
                    Globals.getLogger().log(Logger.WARNING, 
                        "XXXIngore unknown "+typ+" for "+CONN_STRATEGY_PROP); 
                    cl = MIN_CONN_STRATEGY_CLASS;
                }
                if (Globals.isNucleusManagedBroker()) {
                    partitionStrategy = Globals.getHabitat().getService(
                                        ConnToPartitionStrategy.class, cl);
                } else {
                    partitionStrategy = (ConnToPartitionStrategy)
                                          Class.forName(cl).newInstance();
                }
            } catch (Exception e) {
                throw new BrokerException(e.getMessage(), e);
            }
        }

        DestinationList dl = null;
        List<PartitionedStore> partitions = Globals.getStore().getAllStorePartitions();
        if (Globals.getStore().getPartitionModeEnabled()) {
            partitionMode = true;
            if (Globals.getStore().isPartitionMigratable()) {
                partitionMigratable = true;
            }
        } else if (partitions.size() > 1) {
            BrokerException ex = new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Unexpected "+partitions.size()+ 
                    "store partitions when partition mode disabled"));
            Globals.getLogger().logStack(Logger.ERROR, ex.getMessage(), ex);
            throw ex;
        }
        partitionModeInited = true;
        
        PartitionedStore partition = null;
        Iterator<PartitionedStore> itr1 = partitions.iterator();
        while (itr1.hasNext()) {
            partition = itr1.next();
            dl = new DestinationList(partition);
            destinationListList.put(partition, dl);
        }
        
        TransactionList tl = null;
        Iterator<DestinationList> itr2 = destinationListList.values().iterator();
        while (itr2.hasNext()) {
            dl = itr2.next();
            tl = new TransactionList(dl);
            dl.setTransactionList(tl);
        }
        Iterator<DestinationList> itr = destinationListList.values().iterator();
        while (itr.hasNext()) {
            dl = itr.next();
            dl.loadDestinations();
            addPartitionListener((PartitionListener)dl.getTransactionList());
            notifyPartitionAdded(dl.getPartitionedStore(), dl);
        }
    }

    public static void acquirePartitionLock(boolean share) 
        throws BrokerException {
        if (!partitionMode) {
            return;
        }
        Lock lock = partitionShareLock;
        if (!share) {
            lock = partitionExclusiveLock;
        }
        try {
            if (lock.tryLock(partitionLockTimeout, TimeUnit.SECONDS)) {
                return;
            }
            throw new BrokerException(
            "XXXAcquire partition lock timed out", Status.TIMEOUT); 
        } catch (InterruptedException e) {
            throw new BrokerException(
            "XXX Acquire partition lock interrupted", e);
        }
    }

    public static void releasePartitionLock(boolean share) {
        if (!partitionMode) {
            return;
        }
        if (share) {
            partitionShareLock.unlock();
        } else {
            partitionExclusiveLock.unlock();
        } 
    }

    public static void registerPartitionLoadEvent(UID partitionID) {
        partitionMonitor.execute(new PartitionMonitorTask(
            PartitionMonitorTask.PARTITION_RELOAD, partitionID, null));
    }

    public static void registerPartitionArrivedEvent(
        UID partitionID, String sourceBrokerID) {
        partitionMonitor.execute(new PartitionMonitorTask(
            PartitionMonitorTask.PARTITION_ARRIVED, partitionID, sourceBrokerID));
    }

    public static void registerPartitionArrivalNotificationEvent(
                           UID partitionID, String targetBrokerID) {

        partitionMonitor.execute(new PartitionMonitorTask(
            PartitionMonitorTask.PARTITION_ARRIVAL_NOTIFICATION,
            partitionID, targetBrokerID));
    }

    public static void registerPartitionArrivalCheckEvent() {
        partitionMonitor.execute(new PartitionMonitorTask(
            PartitionMonitorTask.PARTITION_ARRIVAL_CHECK, null, null));
    }

    public static void storePartitionArrived(UID partitionID) 
    throws BrokerException {
        List<PartitionedStore> pstores = Globals.getStore().
                                         partitionArrived(partitionID);
        if (partitionID != null) {
            addStorePartition(pstores.get(0), true);
            return;
        } 
        if (pstores.size() > 0) {
            Globals.getLogger().log(Logger.INFO, 
                "Found "+pstores.size()+" arrived store partitions to be loaded");
        }
        Iterator<PartitionedStore> itr = pstores.iterator();
        while (itr.hasNext()) {
            addStorePartition(itr.next(), false);
        }
    }

    public static void addStorePartition(PartitionedStore ps, boolean errdup)
    throws BrokerException {
        acquirePartitionLock(false);
        try {
            synchronized(destinationListList) {
                if (destinationListList.get(ps) != null) {
                    String emsg = "Partition "+ps+" has already been loaded";
                    if (errdup) {
                        throw new BrokerException(emsg);
                    }
                    Globals.getLogger().log(Logger.INFO, emsg);
                    return;
                }
                DestinationList dl = new DestinationList(ps);
                TransactionList tl = new TransactionList(dl);
                dl.setTransactionList(tl); 
                dl.loadDestinations(); 
                Subscription.initDuraSubscriptions(dl);  
                Subscription.initNonDuraSharedSubscriptions(dl);
                Consumer.attachConsumers(dl);
                destinationListList.put(ps, dl);
                tl.postProcess();
                addPartitionListener((PartitionListener)tl);
                notifyPartitionAdded(ps, dl);
            }
        } finally {
            releasePartitionLock(false);
        }
    }

    public static void addPartitionListener(PartitionListener listener) {
        synchronized(partitionListeners) {
            partitionListeners.add(listener);
        }
    }

    public static void removePartitionListener(PartitionListener listener) {
        synchronized(partitionListeners) {
            partitionListeners.remove(listener);
        }
    }

    private static void notifyPartitionRemoved(PartitionedStore ps, 
        DestinationList dl, String targetBroker) {
        synchronized(partitionListeners) {
            Iterator<PartitionListener> itr =  partitionListeners.iterator();
            while (itr.hasNext()) {
                itr.next().partitionRemoved(ps.getPartitionID(), dl, targetBroker);
            }
        }
    }

    private static void notifyPartitionAdded(PartitionedStore ps, DestinationList dl) {
        synchronized(partitionListeners) {
            Iterator<PartitionListener> itr =  partitionListeners.iterator();
            while (itr.hasNext()) {
                itr.next().partitionAdded(ps.getPartitionID(), dl);
            }
        }
    }


    public static int getNumPartitions() {
        synchronized(destinationListList) { 
            return destinationListList.keySet().size();
        }
    }

    public static DestinationList getDestinationList(UID partitionID) {
        if (partitionID == null) {
            return null;
        }
        synchronized(destinationListList) { 
            return destinationListList.get(new NoPersistPartitionedStoreImpl(partitionID));
        }
    }
    public static void movePartition(UID partitionID, String brokerID) 
    throws BrokerException {
        if (!isPartitionMigratable()) {
            throw new BrokerException(Globals.getBrokerResources().
                getKString(BrokerResources.X_PARTITION_NOT_MIGRATABLE),
                Status.PRECONDITION_FAILED);
        }
        PartitionedStore pstore = null;
        synchronized(destinationListList) { 
            pstore = new NoPersistPartitionedStoreImpl(partitionID); 
            DestinationList dl = destinationListList.get(pstore);
            if (dl == null) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_PARTITION_NOT_FOUND, partitionID), 
                Status.PRECONDITION_FAILED);
            }
            if (dl.getPartitionedStore().isPrimaryPartition()) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_MIGRATE_PRIMARY_PARTITION_NOT_ALLOWED, 
                partitionID), Status.NOT_ALLOWED); 
            }
            dl.valid = false;
            notifyPartitionRemoved(pstore, dl, brokerID);
            pstore = dl.getPartitionedStore();

            dl.closeAttachedConnections(GoodbyeReason.MIGRATE_PARTITION, 
                "XXXAdmin request to move partition: "+partitionID);

            destinationListList.remove(pstore);

            Consumer con = null;
            Iterator itr = Consumer.getAllConsumers(true);
            while(itr.hasNext()) {
                con = (Consumer)itr.next();
                con.setParentList(pstore,  null);
            }
            Destination d = null;
            itr = dl.getDestinations(null, ALL_DESTINATIONS_MASK);
            while (itr.hasNext()) {
                d = (Destination)itr.next();
                d.unload(true, true);
            }

            pstore.close();
            Globals.getStore().partitionDeparture(partitionID, brokerID);

            dl.clearDestinations();
        }
    }

    public static PartitionedStore assignStorePartition(
        int serviceType, ConnectionUID connid, UID storeSession)
        throws BrokerException {

        PartitionedStore pstore = null;
        DestinationList dl = null;

        if (partitionMode && serviceType == ServiceType.ADMIN) {
            synchronized(destinationListList) {
                if (destinationListList.size() == 0) {
                    throw new BrokerException(
                    "IllegalStateException: DestinationList not inited !"); 
                }
                dl = destinationListList.get(Globals.getStore().getPrimaryPartition());
                dl.attachConnection(connid);
                return dl.getPartitionedStore();
            }
        }
        if (storeSession != null && 
            Globals.getStore().getPartitionModeEnabled()) {
            PartitionedStore tmpps  = new NoPersistPartitionedStoreImpl(storeSession);
            synchronized(destinationListList) {
                dl = destinationListList.get(tmpps);
                if (dl == null) {
                    throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_CONN_REQUEST_PARTITION_NOT_FOUND,
                        storeSession, connid), Status.NOT_FOUND);
                }
                dl.attachConnection(connid);
                pstore = dl.getPartitionedStore();
            }
            Globals.getLogger().log(Logger.INFO, 
                Globals.getBrokerResources().getKString(
                    BrokerResources.I_ASSIGN_REQUESTED_PARTITION_TO_CONN,
                    pstore, connid));
            return pstore;
        }

        while (!shutdown) {
            ArrayList<ConnToPartitionStrategyContext> dls = null;
            synchronized(destinationListList) {
                dls = new ArrayList<ConnToPartitionStrategyContext>(destinationListList.values());
            }
            if (dls.size() == 0) {
                throw new BrokerException(
                "XXXIllegalState: DestinationList not inited !"); 
            }
            if (dls.size() == 1) {
                dl = (DestinationList)dls.get(0);
                synchronized(destinationListList) {
                    dl.attachConnection(connid);
                }
                pstore = dl.getPartitionedStore();
                if (Globals.getStore().getPartitionModeEnabled()) {
		    Globals.getLogger().log(Logger.INFO, 
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_ASSIGN_CONN_PARTITION,
                        pstore, connid));
                }
                return pstore;
            }
            if (partitionStrategy != null) {
                pstore = partitionStrategy.chooseStorePartition(dls);
            } else {
                pstore = dls.get(0).getPartitionedStore();
            }

            synchronized(destinationListList) { 
                dl = destinationListList.get(pstore);
                if (dl == null) {
                    continue;
                }
                if (!dl.isValid()) {
                    continue;
                }
                dl.attachConnection(connid);
            }
            pstore = dl.getPartitionedStore();
            Globals.getLogger().log(Logger.INFO, 
                Globals.getBrokerResources().getKString(
                BrokerResources.I_ASSIGN_CONN_PARTITION,
                pstore, connid));
            return pstore;
        }
        throw new BrokerException(
            Globals.getBrokerResources().getKString(
                BrokerResources.X_SHUTTING_DOWN_BROKER),
                BrokerResources.X_SHUTTING_DOWN_BROKER);
    }

    public static void unassignStorePartition(ConnectionUID connid, PartitionedStore pstore) {
        if (pstore == null) {
            return;
        }
        synchronized(destinationListList) { 
            DestinationList dl = destinationListList.get(pstore);
            if (dl == null) {
                return;
            }
            dl.detachConnection(connid);
        }
    }

    /**
     * caller must synchronized on destinationListList 
     */
    private void attachConnection(ConnectionUID connid) 
    throws BrokerException {
        if (!valid) {
            throw new BrokerException(
            br.getKString(br.I_PARTITION_IS_CLOSING, logsuffix));
        }
        synchronized(connections) {
            connections.add(connid);
        }
    }

    private void detachConnection(ConnectionUID connid) {
        synchronized(connections) {
            connections.remove(connid);
        }
    }

    private void closeAttachedConnections(int reason, String reasonStr) {

        ConnectionManager cmgr = Globals.getConnectionManager();
        ConnectionUID cuid = null;
        Connection con = null;
        synchronized(connections) {
            logger.log(logger.INFO, 
                "Sending good-bye to all assigned connections("+connections.size()+") for partition "+this); 

            ConnectionUID[] conids = connections.toArray(new ConnectionUID[connections.size()]);
            for (int i = 0; i< conids.length; i++) {
                cuid = conids[i];
                con = cmgr.getConnection(cuid);
                if (con == null) {
                    connections.remove(cuid);
                    continue;
                }
                con.closeConnection(true, reason, reasonStr);
                connections.remove(cuid);
            }
        }
    }

    public int getConnectionCount() {
        synchronized(connections) {
            return connections.size();
        }
    }

    public long getPersistMessageCount() {
        //XXX to be implemented
        return 0L;
    }

    /**
     * sets the maximum size of an individual message
     *
     * @param size the size limit for a message (0 is no message
     *           maximum)
     */
    public static void setIndividualMessageMax(SizeString size) {

        if (size == null)
            size = new SizeString();

        individual_max_size = size;
        long bytesize = size.getBytes();
	//
	// Bug id = 6366551
	// Esc id = 1-13890503
	// 
	// 28/02/2006 - Tom Ross
	// forward port by Isa Hashim July 18 2006
	//
	if (bytesize <= 0){
		bytesize = Long.MAX_VALUE;
	}
	// end of bug change


        // Inform packet code of the largest packet size allowed
        Packet.setMaxPacketSize(bytesize);

        // update memory
        if (Globals.getMemManager() != null)
            Globals.getMemManager().updateMaxMessageSize(bytesize);
        
    }

    /**
     * sets the maximum # of messages (total = swap & memory)
     *
     * @param messages the maximum number of messages (0 is no message
     *           maximum)
     */
    public static void setMaxMessages(long messages) {

        message_max_count = messages;
    }

    public static long getMaxMessages() {

        return message_max_count;
    }

    /**
     * sets the maximum size of all messages (total = swap & memory)
     *
     * @param size the maximum size of messages (0 is no message
     *           size maximum)
     */
    public  static void setMaxSize(SizeString size) {

        if (size == null)
            size = new SizeString();
        max_size = size;
    }

    public static PacketReference get(PartitionedStore ps, SysMessageID id) { 

        return get(ps, id, true);
    }

    public static PacketReference get(PartitionedStore ps, 
                          SysMessageID id, boolean wait) { 
        DestinationList dl = null;
        if (ps != null) {
            dl = destinationListList.get(ps); 
            if (dl == null) {
                Globals.getLogger().log(Logger.WARNING, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_PARTITION_NOT_FOUND, ps));
                return null;
            }
            return dl.get(id, wait);
        }

        PacketReference ref = null;
        synchronized(destinationListList) {
            Iterator<DestinationList> itr = destinationListList.values().iterator();
            while (itr.hasNext()) {
                dl = itr.next();
                DestinationUID uid = (DestinationUID)dl.getPacketListFirst(id);
                if (uid == null) {
                    continue;
                }
                Destination d = (Destination)dl.destinationList.get(uid);
                if (d == null)  {
                    continue;
                }
                ref = (PacketReference)d.destMessages.get(id);
                if (ref == null) {
                    continue;
                }
                break;
            }
        }
        if (ref == null) {
            return ref;
        }
        return ref.checkLock(wait);
    }

    PacketReference get(SysMessageID id, boolean wait) {

        DestinationUID uid = (DestinationUID)getPacketListFirst(id);
        if (uid == null) return null;
        Destination d = (Destination)destinationList.get(uid);
        if (d == null) return null;
        PacketReference ref = (PacketReference)d.destMessages.get(id);
        if (ref == null) return null;
        return ref.checkLock(wait);
    }

    
    public static boolean isLocked(PartitionedStore ps, SysMessageID id) {
        if (ps != null) {
            return destinationListList.get(ps).isLocked(id);
        }
        PacketReference ref = get(null, id, false);
        if (ref == null) {
            return false;
        }
        return (ref.checkLock(false) == null);
    }

    private boolean isLocked(SysMessageID id) { 

        DestinationUID uid = (DestinationUID)getPacketListFirst(id);
        if (uid == null) return false;
        Destination d = (Destination)destinationList.get(uid);
        if (d == null) return false;
        PacketReference ref = (PacketReference)d.destMessages.get(id);
        if (ref == null) return false;
        return (ref.checkLock(false) == null);
    }
    
    /**
     * adds information on the new message to the globals tables.
     * It may or may not change the size (since the size may
     * already have been taken into account
     * @param checkLimits true if message is new, false if it is
     *       just being loaded into memory
     * @param ref the reference to use
     * @return true if the message was added, false it if wasnet
     *         because it had expired
     * @throws BrokerException if a system limit has been exceeded
     */
    static PacketListDMPair addNewMessage(PartitionedStore ps,
        boolean checkLimits, PacketReference ref)
        throws BrokerException {
        if (ps == null) {
            throw new BrokerException("IllegalArgument: addNewMessage(null, "+ref+")"); 
        }
        DestinationList dl = destinationListList.get(ps);
        if (dl == null) {
            throw new BrokerException(Globals.getBrokerResources().
                getKString(BrokerResources.X_PARTITION_NOT_FOUND, ps));
        }
        return dl.addNewMessage(checkLimits, ref);
    }

    /**
     * caller must call PacketListDMPair.nullRef() if remote msg
     * after completes enqueuing the msg
     */ 
    PacketListDMPair addNewMessage(
        boolean checkLimits, PacketReference ref)
        throws BrokerException {

        if (checkLimits) {
            // existing msgs can not fail to add if limits are
            // exceeded, so we only need to do limit checks on 
            // non admin stored messages
            checkSystemLimit(ref); 
        }

        // Add to list
        PacketListDMPair dmp = packetlistAdd(ref.getSysMessageID(),
                                   ref.getDestinationUID(), ref);
        if (ref.isExpired()) {
            dmp.setReturn(false);
        } else {
            dmp.setReturn(true);
        }

        return dmp;
    }

    public static long checkSystemLimit(PacketReference ref)
    throws BrokerException {
    
        long room = -1;

        // check message size
        long indsize = individual_max_size.getBytes();
        if (indsize > 0 && ref.byteSize() > indsize) {
            String limitstr = individual_max_size.toString();

            String msgs[] = { String.valueOf(ref.byteSize()),
                              ref.getSysMessageID().toString(), 
                              MAX_MESSAGE_SIZE,
                              limitstr};
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_IND_MESSAGE_SIZE_EXCEEDED, msgs),
                BrokerResources.X_IND_MESSAGE_SIZE_EXCEEDED,
                (Throwable) null, Status.ENTITY_TOO_LARGE);
        }

        long newsize = 0;
        int newcnt = 0;
        synchronized(totalcntLock) {
            newcnt = totalcnt + 1;
            newsize = totalbytes + ref.byteSize();
        }

        // first check if we have exceeded our maximum message count
        if (message_max_count > 0 && newcnt > message_max_count) {
            String limitstr = (message_max_count <= 0 ?
                               Globals.getBrokerResources().getString(
                                    BrokerResources.M_UNLIMITED):
                                String.valueOf(message_max_count));
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_MAX_MESSAGE_COUNT_EXCEEDED,
                limitstr, ref.getSysMessageID()),
                BrokerResources.X_MAX_MESSAGE_COUNT_EXCEEDED,
                (Throwable) null, Status.RESOURCE_FULL);
        }
        if (message_max_count > 0) {
            room = message_max_count - totalcnt;
            if (room < 0) {
                room  = 0;
            }
        }
        // now check if we have exceeded our maximum message size
        if (max_size.getBytes() > 0 && newsize > max_size.getBytes()) {
            String limitstr = (max_size.getBytes() <= 0 ?
                               Globals.getBrokerResources().getString(
                                   BrokerResources.M_UNLIMITED):
                                   max_size.toString());
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_MAX_MESSAGE_SIZE_EXCEEDED,
                        limitstr, ref.getSysMessageID()),
                        BrokerResources.X_MAX_MESSAGE_SIZE_EXCEEDED,
                        (Throwable) null, Status.RESOURCE_FULL);
        }    
        if (max_size.getBytes() > 0) {
            long cnt = (max_size.getBytes() - totalbytes)/ref.byteSize();
            if (cnt < 0) {
                cnt = 0;
            }
            if (cnt < room) {
                room = cnt;
            }
        }
        return room;
    }

    public static void adjustTotalBytes(long objsize) {
        synchronized(totalcntLock) {
            totalbytes += objsize;
        }
    }

    public static void adjustTotals(long dstsize, long objsize) {
        synchronized(totalcntLock) {
            totalcnt += dstsize;
            totalbytes += objsize;
        }
    }

    public static void incrementTotals(long objsize, boolean nonpersist) {
        synchronized(totalcntLock) {
            totalcnt ++; 
            if (objsize > 0) {
                totalbytes += objsize;
            }
            if (nonpersist) {
                totalcntNonPersist ++;
            }
        }
    }

    public static void decrementTotals(long objsize, boolean nonpersist) {
        synchronized(totalcntLock) {
            totalcnt --;
            if (objsize > 0) {
                totalbytes -= objsize;
            }
            if (nonpersist) {
                totalcntNonPersist --;
            }
        }
    }

    public static int totalCount() {
        synchronized(totalcntLock) {
            assert totalcnt >= 0;
            return totalcnt;
        }
    }

    public static long totalBytes() {
        synchronized(totalcntLock) {
            assert totalbytes >= 0;
            return totalbytes;
        }
    }

    public static int totalCountNonPersist() {
        synchronized(totalcntLock) {
            assert totalcntNonPersist >= 0;
            return totalcntNonPersist;
        }
    }

    public static float totalCountPercent() {
        if (message_max_count <= 0) {
            return (float)0.0;
         }
         synchronized(totalcntLock) {
             return ((float)totalcnt/(float)message_max_count)*100;
        }
    }

    public static boolean canAutoCreate(boolean queue) {
        return (queue ? ALLOW_QUEUE_AUTOCREATE :
            ALLOW_TOPIC_AUTOCREATE);
    }

    public static boolean canAutoCreate(boolean queue, int type) {

	if (DestType.isTemporary(type)){
		return false;
	}

        return (queue ? ALLOW_QUEUE_AUTOCREATE :
            ALLOW_TOPIC_AUTOCREATE);
    }

    private static class PartitionMonitorTask implements Runnable {

        public static final int PARTITION_RELOAD = 0;
        public static final int PARTITION_ARRIVED = 1;
        public static final int PARTITION_ARRIVAL_NOTIFICATION = 2;
        public static final int PARTITION_ARRIVAL_CHECK =  3;

        private int type = PARTITION_ARRIVAL_CHECK;
        private UID partitionID = null;
        private String brokerID = null;

        public PartitionMonitorTask(int type, UID partitionID, String brokerID) {
            this.type = type;
            this.partitionID = partitionID;
            this.brokerID = brokerID;
        }

        public void run() {
            if (shutdown) {
                return;
            }

            Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().
                getKString(BrokerResources.I_EXECUTE_PARTITION_TASK, this.toString()));

            switch (type) {
                case PARTITION_RELOAD:
                case PARTITION_ARRIVED:
                     try {
                         DL.storePartitionArrived(partitionID);
                     } catch (BrokerException e) {
                         if (e.getStatusCode() == Status.PRECONDITION_FAILED ||
                             e.getStatusCode() == Status.NOT_ALLOWED) {
                             Globals.getLogger().log(Logger.ERROR, e.getMessage(), e);
                             return;
                         }
                         Globals.getLogger().logStack(Logger.WARNING, e.getMessage(), e);
                         Globals.getLogger().log(Logger.INFO, "Resubmit partition monitor task "+this);
                         Globals.getTimer(false).schedule(new ResubmitPartitionMonitorTask(this), 30*1000L);
                     }
                     break;
                case PARTITION_ARRIVAL_NOTIFICATION:
                     Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().getKString(
                     BrokerResources.I_NOTIFY_BROKER_PARTITION_ARRIVAL, brokerID, partitionID));
                     try {
                         Globals.getClusterBroadcast().notifyPartitionArrival(partitionID, brokerID);
                     } catch (Exception e) {
                         if (e instanceof BrokerException && 
                             ((BrokerException)e).getStatusCode() == Status.NOT_FOUND) {
                             Globals.getLogger().log(Logger.INFO, e.getMessage());
                         } else {
                             Globals.getLogger().logStack(Logger.WARNING, e.getMessage(), e);
                         }
                         ClusteredBroker cb = Globals.getClusterManager().getBroker(brokerID);
                         if (cb == null) {
                             Globals.getLogger().log(Logger.INFO, "Broker "+brokerID+
                                 " not found, cancel partition monitor event "+this);
                             return;
                         }
                         if (BrokerStatus.getBrokerLinkIsDown(cb.getStatus())) {
                             Globals.getLogger().log(Logger.INFO, "Broker "+brokerID+
                                 " link is down, cancel partition monitor event "+this);
                             return;
                         }
                         Globals.getLogger().log(Logger.INFO, "Resubmit partition monitor task "+this);
                         Globals.getTimer(false).schedule(new ResubmitPartitionMonitorTask(this), 30*1000L);
                     }
                     break;
                case PARTITION_ARRIVAL_CHECK:
                     try {
                         DL.storePartitionArrived(null);
                     } catch (BrokerException e) {
                         Globals.getLogger().log(Logger.WARNING, this+": "+e.getMessage());
                         if (e.getStatusCode() == Status.RETRY) {
                             Globals.getLogger().log(Logger.INFO, "Resubmit partition monitor task "+this);
                             Globals.getTimer(false).schedule(new ResubmitPartitionMonitorTask(this), 30*1000L);
                         }
                     }
                     break;
                default:
            }
        }
        public String toString() {
            StringBuffer buf = new StringBuffer();
            switch(type) {
                case PARTITION_RELOAD: buf.append("PARTITION_RELOAD");
                     break;
                case PARTITION_ARRIVED: buf.append("PARTITION_ARRIVED");
                     break;
                case PARTITION_ARRIVAL_NOTIFICATION: buf.append("PARTITION_ARRIVAL_NOTIFICATION");
                     break;
                case PARTITION_ARRIVAL_CHECK: buf.append("PARTITION_ARRIVAL_CHECK");
                     break;
                default:
            }
            buf.append("[").append((partitionID == null ? "":partitionID));
            buf.append(", ").append((brokerID == null ? "":brokerID)).append("]");
            return  buf.toString();
        }
    }

    private static class ResubmitPartitionMonitorTask extends TimerTask {

        private PartitionMonitorTask task = null;

        public ResubmitPartitionMonitorTask(PartitionMonitorTask task) {
            this.task = task;
        }

        public void run() {
            if (shutdown) {
                cancel();
                return;
            }
            try {
                partitionMonitor.execute(task);
            } catch (Exception e) {
                if (!shutdown) {
                    Globals.getLogger().logStack(Logger.WARNING, 
                        "Can't resubmit partition monitor task "+task, e);
                }
            }
        }
    }
}

