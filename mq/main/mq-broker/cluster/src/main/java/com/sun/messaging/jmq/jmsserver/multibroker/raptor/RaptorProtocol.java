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
 * @(#)RaptorProtocol.java
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import java.io.*;
import java.nio.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStore;
import com.sun.messaging.jmq.jmsserver.persist.api.ReplicableStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionListener;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreSessionReaperListener;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterManagerImpl;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAClusterManagerImpl;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.ExclusiveRequest;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.ServiceRestriction;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAMonitorServiceImpl;
import com.sun.messaging.jmq.jmsserver.multibroker.MessageBusCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterBrokerInfoReply;
import com.sun.messaging.jmq.jmsserver.multibroker.HandshakeInProgressException;
import com.sun.messaging.jmq.jmsserver.multibroker.Protocol;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.cluster.api.FileTransferCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected.ClusterImpl;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecord;
import com.sun.messaging.jmq.jmsserver.multibroker.ChangeRecordCallback;
import com.sun.messaging.jmq.jmsserver.multibroker.DestinationUpdateChangeRecord;
import com.sun.messaging.jmq.jmsserver.multibroker.InterestUpdateChangeRecord;
import com.sun.messaging.jmq.jmsserver.multibroker.CallbackDispatcher;
import com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.HeartbeatService;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

public class RaptorProtocol implements Protocol, PartitionListener, StoreSessionReaperListener
{
    protected static final Logger logger = Globals.getLogger();
    protected static final BrokerResources br = Globals.getBrokerResources();

    private static boolean DEBUG = false;

    protected static final boolean DEBUG_CLUSTER_ALL = 
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.all") || DEBUG;

    protected static final boolean DEBUG_CLUSTER_CONN =
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.conn") || DEBUG_CLUSTER_ALL;

    protected static final boolean DEBUG_CLUSTER_TXN =
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.txn") || DEBUG_CLUSTER_ALL;

    protected static final boolean DEBUG_CLUSTER_MSG =
        Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".cluster.debug.msg") || DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_ALL;

    protected static final boolean DEBUG_CLUSTER_TAKEOVER =
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.takeover") || DEBUG_CLUSTER_ALL;

    protected MessageBusCallback cb = null;
    protected Cluster c = null;
    protected BrokerAddress selfAddress = null;
    protected BrokerInfo selfInfo = null;
    protected CallbackDispatcher cbDispatcher = null;

    private static final int version = ProtocolGlobals.getCurrentVersion();

    private boolean shutdown = false;
    protected Map brokerList = null;
    private Map<TakingoverEntry, TakingoverEntry> takingoverBrokers = null;

    protected HashMap resTable = null;
    protected Random random = null;

    //protected long startTime = 0;

    protected boolean configSyncComplete = false;
    private boolean storeDirtyFlag = false;

    private Map eventLogWaiters = null;

    protected Object cfgSrvWaitObject = null;
    protected int cfgSrvRequestCount = 0;
    protected boolean cfgSrvRequestErr = false;

    protected Store store = null;

    protected GPacketHandler[] handlers;
    protected GPacketHandler unknownPacketHandler;

    protected boolean flowStopped = false;

    private int takeoverPendingReplyTimeout = 60; //XXX seconds
    private Map myPretakeovers = null; 
    private ThreadGroup takeoverCleanupTG = new MQThreadGroup("TakeoverCleanup", logger,
                                          br.getKString(br.W_UNCAUGHT_EXCEPTION_IN_THREAD));
    private FaultInjection fi = null;
    private HashMap ackCounts = new HashMap(); //for msg remote ack fi 

    private ReplyTracker ackackTracker = null;
    private ReplyTracker broadcastAnyOKReplyTracker = null;
    private ReplyTracker takeoverPendingReplyTracker = null;
    private ReplyTracker newMasterBrokerReplyTracker = null;
    private ReplyTracker takeoverMEReplyTracker = null;

    protected int changeMasterBrokerWaitTimeout = Globals.getConfig().getIntProperty(
                   Globals.IMQ + ".cluster.changeMasterBrokerWaitTimeout",
                   ProtocolGlobals.DEFAULT_WAIT_REPLY_TIMEOUT);

    private Object masterBrokerBlockedLock = new Object();
    private Object configOpLock = new Object();
    private int configOpInProgressCount = 0;
    private boolean masterBrokerBlocked = false;

    private Object newMasterBrokerLock = new Object();
    private String newMasterBrokerPreparedUUID = null; 
    private BrokerAddress newMasterBrokerPreparedSender = null; 
    private DestinationList DL = Globals.getDestinationList();
    private Map<String, ChangeRecord> inDSubToBrokerMap = 
        Collections.synchronizedMap(new HashMap<String, ChangeRecord>());

    public RaptorProtocol(MessageBusCallback cb, Cluster c, 
        BrokerAddress myaddress, BrokerInfo myinfo) throws BrokerException
    {
        if (DEBUG_CLUSTER_ALL) {
            DEBUG = DEBUG_CLUSTER_ALL;
        }
        if (DEBUG) {
            logger.log(logger.INFO, "Initializing RaptorProtocol");
        }
        this.cb = cb;
        this.c = c;
        this.selfAddress = myaddress;
        this.selfInfo = myinfo;
        this.cbDispatcher = new CallbackDispatcher(cb);
        store = Globals.getStore();
        resTable = new HashMap();
        random = new Random();
        brokerList = Collections.synchronizedMap(new LinkedHashMap());
        takingoverBrokers = Collections.synchronizedMap(
            new LinkedHashMap<TakingoverEntry, TakingoverEntry>());
        myPretakeovers = Collections.synchronizedMap(new LinkedHashMap());
        cfgSrvWaitObject = new Object();
        eventLogWaiters = Collections.synchronizedMap(new LinkedHashMap());

        initHandlers();

        String backupFileName = Globals.getConfig().getProperty(
            Globals.IMQ + ".cluster.masterbroker.backup");
        String restoreFileName = Globals.getConfig().getProperty(
            Globals.IMQ + ".cluster.masterbroker.restore");

        if (backupFileName != null) {
            configServerBackup(backupFileName);
        }

        if (restoreFileName != null) {
            configServerRestore(restoreFileName);
        }

        ackackTracker = new ReplyTracker();
        newMasterBrokerReplyTracker = new ReplyTracker();
        takeoverMEReplyTracker = new ReplyTracker();
        takeoverPendingReplyTracker = new ReplyTracker();
        broadcastAnyOKReplyTracker = new ReplyTracker();

        fi = FaultInjection.getInjection();
    }

    private void addHandler(int id, GPacketHandler h) {
        if (id > ProtocolGlobals.G_MAX_PACKET_TYPE) {
            throw new ArrayIndexOutOfBoundsException(
                "Bad ProtocolHandler");
        }

        handlers[id] = h;
    }

    private void initHandlers() {
        if (DEBUG) {
            logger.log(logger.INFO, "Initializing RaptorProtocol handlers");
        }

        handlers = new GPacketHandler[ProtocolGlobals.G_MAX_PACKET_TYPE + 1];

        GPacketHandler h;

        h = new MessageDataHandler(this);
        addHandler(ProtocolGlobals.G_MESSAGE_DATA, h);
        addHandler(ProtocolGlobals.G_MESSAGE_DATA_REPLY, h);

        h = new MessageAckHandler(this);
        addHandler(ProtocolGlobals.G_MESSAGE_ACK, h);
        addHandler(ProtocolGlobals.G_MESSAGE_ACK_REPLY, h);

        h = new NewInterestHandler(this);
        addHandler(ProtocolGlobals.G_NEW_INTEREST, h);
        addHandler(ProtocolGlobals.G_NEW_INTEREST_REPLY, h);
        addHandler(ProtocolGlobals.G_DURABLE_ATTACH, h);
        addHandler(ProtocolGlobals.G_DURABLE_ATTACH_REPLY, h);

        h = new RemDurableHandler(this);
        addHandler(ProtocolGlobals.G_REM_DURABLE_INTEREST, h);
        addHandler(ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY, h);

        h = new InterestUpdateHandler(this);
        addHandler(ProtocolGlobals.G_INTEREST_UPDATE, h);
        addHandler(ProtocolGlobals.G_INTEREST_UPDATE_REPLY, h);

        h = new LockHandler(this);
        addHandler(ProtocolGlobals.G_LOCK, h);
        addHandler(ProtocolGlobals.G_LOCK_REPLY, h);

        h = new DestinationUpdateHandler(this);
        addHandler(ProtocolGlobals.G_UPDATE_DESTINATION, h);
        addHandler(ProtocolGlobals.G_UPDATE_DESTINATION_REPLY, h);
        addHandler(ProtocolGlobals.G_REM_DESTINATION, h);
        addHandler(ProtocolGlobals.G_REM_DESTINATION_REPLY, h);

        h = new ConfigChangeEventHandler(this);
        addHandler(ProtocolGlobals.G_CONFIG_CHANGE_EVENT, h);
        addHandler(ProtocolGlobals.G_CONFIG_CHANGE_EVENT_REPLY, h);

        h = new GetConfigChangesHandler(this);
        addHandler(ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST, h);
        addHandler(ProtocolGlobals.G_GET_CONFIG_CHANGES_REPLY, h);

        h = new ClientClosedHandler(this);
        addHandler(ProtocolGlobals.G_CLIENT_CLOSED, h);
        addHandler(ProtocolGlobals.G_CLIENT_CLOSED_REPLY, h);

        h = new ClusterFlowControlHandler(this);
        addHandler(ProtocolGlobals.G_STOP_MESSAGE_FLOW, h);
        addHandler(ProtocolGlobals.G_STOP_MESSAGE_FLOW_REPLY, h);
        addHandler(ProtocolGlobals.G_RESUME_MESSAGE_FLOW, h);
        addHandler(ProtocolGlobals.G_RESUME_MESSAGE_FLOW_REPLY, h);

        h = new ReloadClusterHandler(this);
        addHandler(ProtocolGlobals.G_RELOAD_CLUSTER, h);
        addHandler(ProtocolGlobals.G_RELOAD_CLUSTER_REPLY, h);

        h = new GetInterestUpdateHandler(this);
        addHandler(ProtocolGlobals.G_GET_INTEREST_UPDATE, h);
        addHandler(ProtocolGlobals.G_GET_INTEREST_UPDATE_REPLY, h);

        h = new PingHandler(this);
        addHandler(ProtocolGlobals.G_PING, h);
        addHandler(ProtocolGlobals.G_PING_REPLY, h);

        h = new GoodbyeHandler(this);
        addHandler(ProtocolGlobals.G_GOODBYE, h);
        addHandler(ProtocolGlobals.G_GOODBYE_REPLY, h);

        h = new TakeoverCompleteHandler(this);
        addHandler(ProtocolGlobals.G_TAKEOVER_COMPLETE, h);

        h = new TakeoverPendingHandler(this);
        addHandler(ProtocolGlobals.G_TAKEOVER_PENDING, h);
        addHandler(ProtocolGlobals.G_TAKEOVER_PENDING_REPLY, h);

        h = new TakeoverAbortHandler(this);
        addHandler(ProtocolGlobals.G_TAKEOVER_ABORT, h);

        h = new TransactionInquiryHandler(this);
        addHandler(ProtocolGlobals.G_TRANSACTION_INQUIRY, h);

        h = new TransactionInfoHandler(this);
        addHandler(ProtocolGlobals.G_TRANSACTION_INFO, h);

        h = new FirstInfoHandler(this);
        addHandler(ProtocolGlobals.G_FIRST_INFO, h);

        h = new NewMasterBrokerHandler(this);
        addHandler(ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE, h);
        addHandler(ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY, h);
        addHandler(ProtocolGlobals.G_NEW_MASTER_BROKER, h);
        addHandler(ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY, h);

        h = new ReplicationGroupInfoHandler(this);
        addHandler(ProtocolGlobals.G_REPLICATION_GROUP_INFO, h);

        h = new TakeoverMEHandler(this);
        addHandler(ProtocolGlobals.G_TAKEOVER_ME_PREPARE, h);
        addHandler(ProtocolGlobals.G_TAKEOVER_ME_PREPARE_REPLY, h);
        addHandler(ProtocolGlobals.G_TAKEOVER_ME, h);
        addHandler(ProtocolGlobals.G_TAKEOVER_ME_REPLY, h);
        addHandler(ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK, h);

        h = new TransferFileRequestHandler(this);
        addHandler(ProtocolGlobals.G_TRANSFER_FILE_REQUEST, h);
        addHandler(ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY, h);

        h = new InfoHandler(this);
        addHandler(ProtocolGlobals.G_INFO_REQUEST, h);
        addHandler(ProtocolGlobals.G_INFO, h);

        h = new NotifyPartitionArrivalHandler(this);
        addHandler(ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL, h);
        addHandler(ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY, h);

        unknownPacketHandler = new UnknownPacketHandler(this);
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ArrayList l = null;
        synchronized(brokerList) {
            l = new ArrayList(brokerList.keySet());
        }
        ht.put("brokerListCount", l.size());
        Iterator itr = l.iterator();
        while (itr.hasNext()) {
            BrokerAddress ba = (BrokerAddress)itr.next();
            BrokerInfoEx bie = (BrokerInfoEx)brokerList.get(ba);
            ht.put("[brokerList]"+ba.toString(), bie.toString());
        }
        if (cbDispatcher != null) {
            ht.put("callbackDispatcher", cbDispatcher.getDebugState());
        }
        if (Globals.getHAEnabled()) {
            synchronized(takingoverBrokers) {
                l = new ArrayList<TakingoverEntry>(takingoverBrokers.keySet());
            }
            ht.put("takingoverBrokersCount", l.size());
            Iterator<TakingoverEntry> itr1 = l.iterator();
            while (itr1.hasNext()) {
                TakingoverEntry toe = itr1.next();
                ht.put(toe.toString(), toe.toLongString());
            }
        }
        return ht;
    }


    public int getHighestSupportedVersion() {
         return ProtocolGlobals.getCurrentVersion();
    }

    /**
     * Get the message bus (cluster) protocol version used by this
     * broker.
     */
    public int getClusterVersion() {
        return version;
    }

    /**
     * Receive a unicast packet (raptor protocol).
     */
    public void receiveUnicast(BrokerAddress sender, GPacket pkt) {
        if (DEBUG) {
            logger.log(logger.DEBUGMED,
                "RaptorProtocol.receiveUnicast(GPacket) from : " +
                sender + " Packet :\n" + pkt.toLongString());
        }
        int pktType = pkt.getType();

        if (pktType > ProtocolGlobals.G_MAX_PACKET_TYPE) {
            unknownPacketHandler.handle(sender, pkt);
            return;
        }

        switch (pktType) { 
            case ProtocolGlobals.G_MESSAGE_ACK_REPLY: 
                 cbDispatcher.processMessageAckReply(sender, pkt, this);
                 return;
            case ProtocolGlobals.G_NEW_INTEREST_REPLY:
            case ProtocolGlobals.G_INTEREST_UPDATE_REPLY:
            case ProtocolGlobals.G_DURABLE_ATTACH_REPLY:
            case ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY:
            case ProtocolGlobals.G_MESSAGE_DATA:
            case ProtocolGlobals.G_CLIENT_CLOSED_REPLY:
                 cbDispatcher.processMessageData(sender, pkt, this);
                 return;

            case ProtocolGlobals.G_NEW_INTEREST:
            case ProtocolGlobals.G_DURABLE_ATTACH:
                 if (((NewInterestHandler)handlers[pktType]).
                               ignoreNewInterest(null, pkt)) {
                     return;
                 }

            case ProtocolGlobals.G_UPDATE_DESTINATION:
            case ProtocolGlobals.G_REM_DESTINATION:
            case ProtocolGlobals.G_UPDATE_DESTINATION_REPLY:
            case ProtocolGlobals.G_INTEREST_UPDATE:
            case ProtocolGlobals.G_REM_DURABLE_INTEREST:
            case ProtocolGlobals.G_MESSAGE_ACK: 
            case ProtocolGlobals.G_MESSAGE_DATA_REPLY:
            case ProtocolGlobals.G_CLIENT_CLOSED:
            case ProtocolGlobals.G_TRANSACTION_INQUIRY:
            case ProtocolGlobals.G_TRANSACTION_INFO:
            case ProtocolGlobals.G_INFO:
            case ProtocolGlobals.G_INFO_REQUEST:
            cbDispatcher.processGPacket(sender, pkt, this);
            return;
        }
  

        handlers[pktType].handle(sender, pkt);
    }

    /**
     * Receive a broadcast packet (raptor protocol).
     */
    public void receiveBroadcast(BrokerAddress sender, GPacket pkt) {
        if (DEBUG) {
            logger.log(logger.DEBUGMED,
                "RaptorProtocol.receiveBroadcast(GPacket) from : " +
                sender + " Packet :\n" + pkt.toLongString());
        }
        int pktType = pkt.getType();

        if (pktType > ProtocolGlobals.G_MAX_PACKET_TYPE) {
            unknownPacketHandler.handle(sender, pkt);
            return;
        }

        switch (pktType) { 
            case ProtocolGlobals.G_NEW_INTEREST:
            case ProtocolGlobals.G_DURABLE_ATTACH:
                 if (((NewInterestHandler)handlers[pktType]).
                               ignoreNewInterest(null, pkt)) {
                     return;
                 }
            case ProtocolGlobals.G_UPDATE_DESTINATION:
            case ProtocolGlobals.G_REM_DESTINATION:
            case ProtocolGlobals.G_UPDATE_DESTINATION_REPLY:
            case ProtocolGlobals.G_NEW_INTEREST_REPLY:
            case ProtocolGlobals.G_DURABLE_ATTACH_REPLY:
            case ProtocolGlobals.G_INTEREST_UPDATE:
            case ProtocolGlobals.G_INTEREST_UPDATE_REPLY:
            case ProtocolGlobals.G_REM_DURABLE_INTEREST:
            case ProtocolGlobals.G_REM_DURABLE_INTEREST_REPLY:
            case ProtocolGlobals.G_MESSAGE_ACK: 
            case ProtocolGlobals.G_MESSAGE_ACK_REPLY: 
            case ProtocolGlobals.G_MESSAGE_DATA:
            case ProtocolGlobals.G_MESSAGE_DATA_REPLY:
            case ProtocolGlobals.G_CLIENT_CLOSED:
            case ProtocolGlobals.G_CLIENT_CLOSED_REPLY:
            case ProtocolGlobals.G_TRANSACTION_INQUIRY:
            case ProtocolGlobals.G_TRANSACTION_INFO:
            case ProtocolGlobals.G_INFO:
            case ProtocolGlobals.G_INFO_REQUEST:
            cbDispatcher.processGPacket(sender, pkt, this);
            return;
        }

        handlers[pktType].handle(sender, pkt);
    }

    public void handleGPacket(MessageBusCallback mbcb, BrokerAddress sender, GPacket pkt) {
        handlers[pkt.getType()].handle(mbcb, sender, pkt);
    }

    /**
     * Obsolete.
     */
    public void receiveUnicast(BrokerAddress sender,
        int destId, byte []pkt) {
        // This should never happen.
        logger.log(Logger.WARNING, "Protocol Mismatch. sender = " + sender);
        Thread.dumpStack();
    }

    /**
     * Obsolete.
     */
    public void receiveBroadcast(BrokerAddress sender,
        int destId, byte []pkt) {
        // This should never happen.
        logger.log(Logger.WARNING, "Protocol Mismatch. sender = " + sender);
        Thread.dumpStack();
    }

    /**
     * Obsolete.
     */
    public void receiveBroadcast(BrokerAddress sender,
        int destId, byte []pkt, boolean configSyncResponse) {
        // This should never happen.
        logger.log(Logger.WARNING, "Protocol Mismatch. sender = " + sender);
        Thread.dumpStack();
    }


    public void syncChangeRecordOnJoin(BrokerAddress broker,  ChangeRecordInfo cri)
        throws BrokerException {
        cb.syncChangeRecordOnJoin(broker, cri);
    }

    public ChangeRecordInfo getLastStoredChangeRecord() {
        return cb.getLastStoredChangeRecord();
    }

    /**
     * Construct a BrokerInfo object that describes this broker.
     * This object is exchanged during initial handshake between
     * brokers.
     * @return BrokerInfo object describing the current state of the broker.
     */
    public BrokerInfo getBrokerInfo() {
        logger.logStack(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                        "Unexpected call", new Exception());
        return null;
    }

    public ClusterBrokerInfoReply getBrokerInfoReply(BrokerInfo remoteInfo) throws Exception {
        checkUIDPrefixClash(remoteInfo);

        int status = ProtocolGlobals.G_BROKER_INFO_OK;
        if (isTakeoverTarget(remoteInfo.getBrokerAddr())) {
            status = ProtocolGlobals.G_BROKER_INFO_TAKINGOVER;
        }
        ClusterBrokerInfoReply cbi = ClusterBrokerInfoReply.newInstance(selfInfo, status);
        return cbi;
    }

    private void checkUIDPrefixClash(BrokerInfo info) throws BrokerException {
        if (c.getConfigServer() == null &&
            (UniqueID.getPrefix(info.getBrokerAddr().getBrokerSessionUID().longValue()) ==
             UniqueID.getPrefix(selfInfo.getBrokerAddr().getBrokerSessionUID().longValue()))) {
            final BrokerAddress remote = info.getBrokerAddr();
            if (selfInfo.getStartTime() > info.getStartTime() ||
                (selfInfo.getStartTime() == info.getStartTime() &&
                 ((BrokerMQAddress)selfInfo.getBrokerAddr().getMQAddress()).toString().compareTo( 
                                        ((BrokerMQAddress)remote.getMQAddress()).toString()) > 0)) {
                String msg = br.getKString(BrokerResources.E_CLUSTER_UID_PREFIX_CLASH_RESTART, remote);
                BrokerException ex = new BrokerException(msg); 
                logger.logStack(logger.ERROR, msg, ex);
                Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(), msg,
                                   BrokerEvent.Type.RESTART, null, false, true, false);
            }
            
        }
    }


    /**
     * Set the matchProps for the cluster.
     */
    public void setMatchProps(Properties matchProps) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.setMatchProps :\n" + matchProps);
        }
        c.setMatchProps(matchProps);
    }

    /**
     * Start the I/O operations on the cluster, now that the upper
     * layers are ready to receive notifications.
     */
    public void startClusterIO() {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.startClusterIO");
        }
        // If this broker is the config server, make sure it's own
        // persistent store is in sync with the config server
        // change record.
        try {
            BrokerAddress configServer = c.getConfigServer();
            if (configServer == null) { // No config server...
                configSyncComplete = true;

                if (DEBUG) {
                    logger.log(logger.INFO,
                        "No master broker. configSyncComplete=true");
                }
            }
            else if (configServer.equals(selfAddress)) {
                // I am the config server...
                initConfigServer();

                long timestamp = getLastRefreshTime();
                sendConfigChangesRequest(selfAddress, timestamp);

                // At this point the persistent store is in sync..
                //
                // Obviously, this is not very efficient - we can
                // avoid all the marshalling / unmarshalling since
                // the target is selfAddress.
            }
        }
        catch (Exception e) {
            // There is a master broker, but we don't yet know who.
            // Ignore this exception...
        }
        Globals.getDestinationList().
            addPartitionListener((PartitionListener)this);
        try {
            store.addPartitionListener((PartitionListener)this);
            if (Globals.getHAEnabled()) {
                store.addStoreSessionReaperListener(this);
            }
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(logger.WARNING, "Unable to add store listener: "+e.getMessage(), e);
            } else {
            logger.log(logger.WARNING, "Unable to add store listener: "+e.getMessage(), e);
            }
        }
    }

    /*********************************************
     * implement PartitionListener interface
     *********************************************/

    /**
     * @param partitionID the partition id
     */
    public void partitionAdded(UID partitionID, Object source) {
        if (!(source instanceof DestinationList)) {
            return;
        }
        ClusterInfoInfo cii = ClusterInfoInfo.newInstance();
        cii.partitionAdded(partitionID);
        GPacket gp = cii.getGPacket();

        BrokerAddress[] addrs = getBrokerList(null, Globals.getMyAddress());
        for (int i = 0; i < addrs.length; i++) {
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,
                ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                "["+cii+"]", addrs[i]));
            try {
                c.unicast(addrs[i], gp);
            } catch (Exception e) {
                logger.log(logger.WARNING, 
                    br.getKString(BrokerResources.W_CLUSTER_UNICAST_FAILED,
                    ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), addrs[i])+
                    ": "+e.getMessage(), e);

            }
        }
    }

    /**
     * @param partitionID the partition id
     */
    public void partitionRemoved(UID partitionID, Object source, Object destinedTo) {
        if (destinedTo != null) {
            return; 
        }
        if (!(source instanceof Store)) {
            return;
        }
        List<TakingoverEntry> entries = null;
        synchronized(takingoverBrokers) {
            entries = new ArrayList<TakingoverEntry>(takingoverBrokers.keySet());
        } 
        Iterator<TakingoverEntry> itr = entries.iterator();
        TakingoverEntry toe = null;
        while (itr.hasNext()) {
            toe =  itr.next();
            if (!toe.storeSession.equals(partitionID)) {
                continue;
            }
            takingoverBrokers.remove(toe);
            logger.log(logger.INFO, br.getKString(
                br.I_REMOVE_CACHED_TAKEOVER_NOTIFICATION_ENTRY, toe.toLongString()));
        }
    }

    public void runStoreSessionTask() { 
        List<TakingoverEntry> entries = null;
        synchronized(takingoverBrokers) {
            entries = new ArrayList<TakingoverEntry>(takingoverBrokers.keySet());
        } 
        Iterator<TakingoverEntry> itr = entries.iterator();
        TakingoverEntry toe = null;
        while (itr.hasNext()) {
            toe =  itr.next();
            try {
                if (store.getStoreSessionOwner(toe.storeSession.longValue()) == null) {
                    takingoverBrokers.remove(toe);
                    logger.log(logger.INFO, br.getKString(
                        br.I_REMOVE_CACHED_TAKEOVER_NOTIFICATION_ENTRY, toe.toLongString())+
                        ", ("+takingoverBrokers.size()+")");
                }
            } catch (Exception e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            }
        }
    }

    private static class BrokerInfoEx {
        private int GOODBYE_SENT           = 0x00000001;
        private int GOODBYE_RECEIVED       = 0x00000002;
        private int GOODBYE_REPLY_SENT     = 0x00000004;
        private int GOODBYE_REPLY_RECEIVED = 0x00000008;

        private int finStatus = 0;

        private BrokerInfo info = null;
        private ClusterGoodbyeInfo cgi = null;

        private boolean deactivated = false; 

        public BrokerInfoEx(BrokerInfo info) {
            this.info = info;
        }

        public synchronized boolean deactivated() {
            return deactivated;
        }
        public synchronized void deactivate() {
            deactivated = true;
            notifyAll();
        }
        public synchronized void setGoodbyeInfo(ClusterGoodbyeInfo cgi) {
            this.cgi = cgi;
        }
        public synchronized ClusterGoodbyeInfo getGoodbyeInfo() {
            return cgi;
        }
        public BrokerInfo getBrokerInfo() {
            return info;
        }

        public void setBrokerInfo(BrokerInfo info) {
            this.info = info;
        }

        public synchronized boolean goodbyeDone() {
            return sentGoodbyeReply() && gotGoodbyeReply();
        }

        public synchronized void goodbyeSent() { 
            finStatus = finStatus | GOODBYE_SENT;
        }

        public synchronized void goodbyeReceived() { 
            finStatus = finStatus | GOODBYE_RECEIVED;
        }
        public synchronized void goodbyeReplySent() { 
            finStatus = finStatus | GOODBYE_REPLY_SENT;
        }
        public synchronized void goodbyeReplyReceived() { 
            finStatus = finStatus | GOODBYE_REPLY_RECEIVED;
        }
        public synchronized boolean gotGoodbye() {
            return cgi != null &&
                  ((finStatus & GOODBYE_RECEIVED) == GOODBYE_RECEIVED); 
        }
        public synchronized boolean sentGoodbye() {
            return (finStatus & GOODBYE_SENT) == GOODBYE_SENT;
        }
        public synchronized boolean gotGoodbyeReply() {
            return (finStatus & GOODBYE_REPLY_RECEIVED) == GOODBYE_REPLY_RECEIVED;
        }
        public synchronized boolean sentGoodbyeReply() {
            return (finStatus & GOODBYE_REPLY_SENT) == GOODBYE_REPLY_SENT;
        }
    }

    public void stopClusterIO(boolean requestTakeover, boolean force,
                               BrokerAddress excludedBroker) {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.shutdown");
        }

        if (excludedBroker == null) {
            synchronized(brokerList) {
                shutdown = true;
            }
        

            synchronized(configOpLock) {
                configOpLock.notifyAll();
            }
        }

        cbDispatcher.shutdown();

        try {
            if (Globals.getClusterBroadcast().getClusterVersion() <
                ClusterBroadcast.VERSION_400) {
                return;
            }
        } catch (Exception e) {
            logger.log(logger.DEBUG, 
            "Unable to get cluster version on stop cluster IO:"+e.getMessage());
            return;
        }

        ClusterGoodbyeInfo cgi = ClusterGoodbyeInfo.newInstance(requestTakeover, c);

        BrokerInfoEx be;
        BrokerAddress ba;
        GPacket gp;
        Object[] args;
        synchronized(brokerList) {
            Iterator itr = brokerList.values().iterator();
            while(itr.hasNext()) {
                be = (BrokerInfoEx)itr.next();
                ba = be.getBrokerInfo().getBrokerAddr();
                if (excludedBroker != null && ba.equals(excludedBroker)) {
                    continue;
                }
                gp = cgi.getGPacket();
                try {
                    logger.log(logger.INFO, br.getKString(
                               BrokerResources.I_CLUSTER_UNICAST,
                                   ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                                   "["+cgi+"]", ba.toString()));
                    c.unicast(ba, gp);
                    be.goodbyeSent();
                } catch (IOException e) {
                    logger.log(logger.WARNING, br.getKString(BrokerResources.W_CLUSTER_UNICAST_FAILED,
                        ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), ba)+": "+e.getMessage(), e); 
                }
            }
        }
    }

    public void goodbyeReceived(BrokerAddress sender, ClusterGoodbyeInfo cgi) {
        Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                      ProtocolGlobals.G_GOODBYE),
                                      "["+cgi+"]", sender };
        logger.log(logger.INFO, br.getKString(BrokerResources.I_CLUSTER_RECEIVE, args));

        synchronized(brokerList) {
            BrokerInfoEx be = (BrokerInfoEx)brokerList.get(sender);
            if (be != null) {
                be.setGoodbyeInfo(cgi);
                be.goodbyeReceived();
            }
        }
    }
    public void goodbyeReplySent(BrokerAddress sender) {
        boolean done = false;
        synchronized(brokerList) {
            BrokerInfoEx be = (BrokerInfoEx)brokerList.get(sender);
            if (be != null) {
                be.goodbyeReplySent();
                if (DEBUG) {
                logger.log(logger.INFO, "Sent GOODBYE_REPLY to "+sender);
                }
            }
        }
    }
    public void goodbyeReplyReceived(BrokerAddress sender) {
        boolean done = false;
        synchronized(brokerList) {
            BrokerInfoEx be = (BrokerInfoEx)brokerList.get(sender);
            if (be != null) {
                be.goodbyeReplyReceived();
                if (DEBUG) {
                logger.log(logger.INFO, "Received GOODBYE_REPLY from "+sender);
                }
            }
        }
    }
    public void sendGoodbye(BrokerAddress remote) {
        try {
            synchronized(brokerList) {
                BrokerInfoEx be = (BrokerInfoEx)brokerList.get(remote);
                if (be != null)  {
                    if (be.sentGoodbye()) return;
                    be.goodbyeSent();
                }
            }
            c.unicast(remote, ClusterGoodbyeInfo.newInstance(c).getGPacket());
            if (DEBUG) {
            logger.log(logger.INFO, "Sent GOODBYE to "+remote);
            }
        } catch (Exception e) {
            logger.logStack(logger.WARNING, "Unable to send GOODBYE to "+remote, e);
        }
    }

    private BrokerAddress[] getBrokerList() { 
        return getBrokerList(null, null);
    }

    public BrokerAddress lookupBrokerAddress(String brokerid) {
        if (!Globals.getHAEnabled() && !Globals.isBDBStore()) {
            return null;
        }

        synchronized(brokerList) {
            Iterator itr = brokerList.keySet().iterator();
            BrokerAddress ba = null;
            while (itr.hasNext()) {
                ba = (BrokerAddress)itr.next();
                if (ba.getBrokerID().equals(brokerid)) {
                    return ba;
                }
            }
            return null;
        }
    }

    public BrokerAddress lookupBrokerAddress(BrokerMQAddress url) {

        synchronized(brokerList) {
            Iterator itr = brokerList.keySet().iterator();
            BrokerAddress ba = null;
            while (itr.hasNext()) {
                ba = (BrokerAddress)itr.next();
                if (ba.getMQAddress().equals(url)) {
                    return ba;
                }
            }
            return null;
        }
    }

    public String lookupStoreSessionOwner(UID uid) {
        if (!Globals.isBDBStore()) {
            logger.log(logger.ERROR, "Internal Error: unexpected call:\n",
                       SupportUtil.getStackTrace("lookupStoreSessionOwner"));
        }
        synchronized(brokerList) {
            Iterator itr = brokerList.keySet().iterator();
            BrokerAddress ba = null;
            while (itr.hasNext()) {
                ba = (BrokerAddress)itr.next();
                if (ba.getStoreSessionUID().equals(uid)) {
                    return ba.getBrokerID();
                }
            }
        }
        BrokerAddress[] addrs = getBrokerList(null, Globals.getMyAddress());
        Long xid = broadcastAnyOKReplyTracker.addWaiter(
                   new BroadcastAnyOKReplyWaiter(addrs, ProtocolGlobals.G_INFO));

        ClusterInfoRequestInfo cir = ClusterInfoRequestInfo.newInstance(xid);
        try {
            cir.storeSessionOwnerRequest(uid.longValue());
        } catch (Exception e) {
            logger.log(logger.ERROR, e.toString());
            return null;
        }

        GPacket gp = cir.getGPacket();
        for (int i = 0; i < addrs.length; i++) {
            try {
                c.unicast(addrs[i], gp);
            } catch (Exception e) {
                logger.log(logger.WARNING, br.getKString(BrokerResources.W_CLUSTER_UNICAST_FAILED,
                    ProtocolGlobals.getPacketTypeDisplayString(
                    gp.getType()), addrs[i])+": "+e.getMessage(), e);
            }
        }
        ReplyStatus reply = null;
        try {
            reply = broadcastAnyOKReplyTracker.waitForReply(xid, 
                        ProtocolGlobals.getWaitInfoReplyTimeout());
            if (reply != null && reply.getStatus() == Status.OK) {
                gp = reply.getReply();
                return ClusterInfoInfo.newInstance(gp).getStoreSessionOwner();
            }
        } catch (BrokerException e) {
            String cause = e.getMessage();
            if (e.getStatusCode() == Status.GONE) {
                cause = br.getKString(br.X_CLUSTER_BROKER_LINK_DOWN, Arrays.toString(addrs));
            } else if (e.getStatusCode() == Status.TIMEOUT) {
                String[] args = new String[] {
                         String.valueOf(ProtocolGlobals.getWaitInfoReplyTimeout()),
                         ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_INFO),
                          Arrays.toString(addrs) };
                cause = br.getKString(br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args);
            } 
            Object[] args = { ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_INFO),
                              Arrays.toString(addrs), cause };
            logger.log(Logger.WARNING, br.getKString(br.E_CLUSTER_ABORT_WAIT_REPLY, args));
        }
        return null;
    }

    public void receiveLookupStoreSessionOwnerRequest(BrokerAddress sender, ClusterInfoRequestInfo cir) {
        String owner = null;
        int status = Status.NOT_FOUND;
        String reason = null;
        if (!Globals.isBDBStore()) {
            logger.log(logger.WARNING, "Unexpected protocol "+ 
                       ProtocolGlobals.getPacketTypeDisplayString(
                       ProtocolGlobals.G_INFO_REQUEST)+cir.toString()+" from "+sender);
            status = Status.UNSUPPORTED_TYPE;
        }
        try {
            long sid = cir.getStoreSession();
            if (Globals.getStore().ifOwnStoreSession(sid, null)) {
                status = Status.OK;
                owner = Globals.getBrokerID();
            }
        } catch (Exception e) {
            status = Status.ERROR;
            reason = e.getMessage();
            if (e instanceof BrokerException) {
                status = ((BrokerException)e).getStatusCode();
            }
            logger.logStack(logger.WARNING, e.getMessage(), e);
        }
        ClusterInfoInfo cii = cir.getReply(status, reason, owner);
        try {
            c.unicast(sender, cii.getGPacket());
        } catch (IOException e) {
            logger.logStack(Logger.WARNING, br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_INFO)+cii, sender), e);
        }
    }

    public void receiveStoreSessionOwnerInfo(
        BrokerAddress sender, ClusterInfoInfo cii, GPacket pkt) {

        Long xid = cii.getXid();
        if (!broadcastAnyOKReplyTracker.notifyReply(xid, sender, pkt)) {
            Object[] args = new Object[]{
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_INFO), "["+cii.toString()+"]", sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    public void receivePartitionAddedInfo(
        BrokerAddress sender, ClusterInfoInfo cii, GPacket pkt) {
        UID pid = cii.getPartition();
        sendTransactionInquiries(sender, pid);
    }

    private BrokerAddress[] getBrokerList(BrokerAddress minus, BrokerAddress add) { 
        BrokerAddress[] blist = null;
        synchronized (brokerList) {
            int size = brokerList.size();
            if (minus != null && brokerList.get(minus) != null) {
                size--;
            }
            if (add != null && brokerList.get(add) == null) {
                size++;
            }
            blist = new BrokerAddress[size];
            int i = 0;
            if (add != null) {
                blist[i++] = add;
            }
            Collection values = brokerList.values();
            Iterator itr = values.iterator();
            BrokerAddress ba = null;
            while (itr.hasNext()) {
                BrokerInfoEx binfo = (BrokerInfoEx) itr.next();
                ba = binfo.getBrokerInfo().getBrokerAddr(); 
                if (minus == null || !ba.equals(minus)) {
                    if (!ba.equals(add)) {
                        blist[i++] = ba;
                    }
                }
            }
        }
        return blist;
    }

    public boolean isTakeoverTarget(BrokerAddress ba) {
        if (!Globals.getHAEnabled()) return false;
        TakingoverEntry toe = takingoverBrokers.get(
                              new TakingoverEntry(ba.getBrokerID(), ba.getStoreSessionUID()));
        if (toe == null) {
            return false;
        }
        return toe.isTakeoverTarget(ba);
    }

    public void preTakeover(String brokerID, UID storeSession, 
        String brokerHost, UID brokerSession) throws BrokerException { 
        logger.log(logger.INFO, br.getKString(BrokerResources.I_CLUSTER_PRETAKEOVER, 
                                "[brokerID="+brokerID+", storeSession="+storeSession+"]"));

        Long xid = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance(
                  brokerID, storeSession, brokerHost, brokerSession, xid, true);
        TakingoverEntry toe = TakingoverEntry.addTakingoverEntry(takingoverBrokers, cti);
        myPretakeovers.put(toe, xid);
        takeoverCleanup(toe, false);
        takeoverPendingConvergecast(null, cti);
        toe.preTakeoverDone(xid);
    }

    public void takeoverPendingConvergecast(BrokerAddress sender, ClusterTakeoverInfo cti) {
        if (!cti.isFromTaker()) return;
        BrokerAddress[] brokers = null;
        synchronized(brokerList) {
            brokers = getBrokerList(sender, null);
            if (brokers.length == 0) {
                sendTakeoverPendingReply(sender, cti, Status.OK, null);
                return;
            }
            takeoverPendingReplyTracker.addWaiter(cti.getXid(), new TakeoverPendingReplyWaiter(brokers)); 
        }
        try { 

        try {
            for (int i = 0; i < brokers.length; i++) { 
            c.unicast(brokers[i], cti.getGPacket(ProtocolGlobals.G_TAKEOVER_PENDING));
            }
        } catch (Exception e) { 
            logger.log(Logger.WARNING, e.getMessage());
        }
        ReplyStatus reply = null;
        int status = Status.OK; 
        String reason = null;
        try {
            reply = takeoverPendingReplyTracker.waitForReply(cti.getXid(), takeoverPendingReplyTimeout);
            if (reply != null && reply.getStatus() != Status.OK) {
                status = reply.getStatus();
                reason = reply.getReason();
                Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                                  ProtocolGlobals.G_TAKEOVER_PENDING_REPLY),
                                              cti.toString(),Status.getString(status), reason };
                logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_RECEIVE_STATUS, args));
            }
        } catch (BrokerException e) {
            status = e.getStatusCode();
            reason = e.getMessage();
            Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                              ProtocolGlobals.G_TAKEOVER_PENDING_REPLY), 
                                          cti.toString(), Status.getString(status), reason };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_WAIT_REPLY_FAILED, args));
        }
        sendTakeoverPendingReply(sender, cti, status, reason);

        } finally {
        takeoverPendingReplyTracker.removeWaiter(cti.getXid());  
        }
    }

    private void sendTakeoverPendingReply(BrokerAddress sender, ClusterTakeoverInfo cti,
                                          int status, String reason) {
        if (sender != null && cti.needReply()) {
            GPacket reply = cti.getReplyGPacket(
                           ProtocolGlobals.G_TAKEOVER_PENDING_REPLY, status, reason);
            try {
                c.unicast(sender, reply);
            } catch (IOException e) {
               logger.logStack(Logger.WARNING, 
               "Unable send to " + sender+ " TAKEOVER_PENDING reply "+ ClusterTakeoverInfo.toString(reply), e);
            }
        }
    }

    public void takeoverCleanup(TakingoverEntry toe, boolean complete) { 
        if (toe.isTakeoverTarget(selfAddress)) {
            String msg = br.getKString(BrokerResources.E_CLUSTER_TAKINGOVER_RESTART);
            logger.logStack(logger.ERROR, msg, new BrokerException(msg));
            Broker.getBroker().exit(Globals.getBrokerStateHandler().getRestartCode(), msg,
                                    BrokerEvent.Type.RESTART, null, true, true, true);
            return;
        }
        BrokerAddress ba = null;
        BrokerInfoEx[] binfos = null;
        synchronized(brokerList) {
            binfos = (BrokerInfoEx[])brokerList.values().
                toArray(new BrokerInfoEx[brokerList.size()]);
        }
        for (int i = 0; i < binfos.length; i++) {
            ba = binfos[i].getBrokerInfo().getBrokerAddr();
            if (brokerList.get(ba) == null) { 
                continue;             
            }
            if (!toe.isTakeoverTarget(ba)) {
                continue;
            }
            String logmsg1 = null;
            if (complete) {
                Object[] args = { ba };
                logmsg1 = br.getKTString(br.W_FORCE_CLOSE_BROKER_LINK_TAKEOVER, args);
            } else {
                Object[] args = { ba };
                logmsg1 = br.getKTString(br.W_FORCE_CLOSE_BROKER_LINK_BEING_TAKEOVER, args);
            }
            logger.log(logger.WARNING, logmsg1); 
            c.closeLink(ba, true); 
            long totalwaited = 0L;
            while (brokerList.get(ba) == binfos[i] && !shutdown) {
                synchronized (binfos[i]) {
                    try {
                        if (binfos[i].deactivated()) {
                            break;
                        }
                        binfos[i].wait(15000L);
                        totalwaited += 15000L;
                        if (totalwaited >= c.getLinkInitWaitTime()) {
                            logger.log(logger.WARNING, logmsg1); 
                            c.closeLink(ba, true); 
                            break; 
                        }
                        Object[] args = { ba+", ("+totalwaited+" ms of "+c.getLinkInitWaitTime()+")" };
                        logger.log(logger.WARNING, br.getKTString(
                                   br.I_WAITING_FOR_BROKER_LINK_DEACTIVATED, args)); 
                    } catch (InterruptedException e) {}
                }
            }
        }
    }

    public void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify) {
        if (aborted) {
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_PRETAKEOVER_ABORT,
                       "[brokerID="+brokerID+", storeSession="+storeSession+"]"));
            Long xid = (Long)myPretakeovers.get(new TakingoverEntry(brokerID, storeSession));
            if (xid == null) {
            logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                       BrokerResources.W_TAKEOVER_ENTRY_NOT_FOUND, 
                       "[brokerID="+brokerID+", storeSession="+storeSession+"]"));
            return;
            }
            ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance( 
                                      brokerID, storeSession, null, null, xid, true);
            GPacket gp = null;
            try {
                gp = cti.getGPacket(ProtocolGlobals.G_TAKEOVER_ABORT);
                c.broadcast(gp);
            } catch (Exception e) {
                if (gp == null) {
                logger.logStack(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, e);
                } else {
                logger.logStack(Logger.WARNING, 
                "Unable to broadcast TAKEOVER_ABORT "+ ClusterTakeoverInfo.newInstance(gp), e);
                }
            }

            receivedTakeoverAbort(null, cti);
            return;
        }
        try {
             ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance(brokerID, storeSession); 
             if (!notify) {
                 TakingoverEntry.takeoverComplete(takingoverBrokers, cti);
                 return;
             }
             logger.log(logger.INFO, br.getKString(br.I_CLUSTER_BROADCAST_TAKEOVER_COMPLETE,
                        "[brokerID="+brokerID+", storeSession="+storeSession+"]"));
             receivedTakeoverComplete(null, cti);
             c.broadcast(cti.getGPacket(ProtocolGlobals.G_TAKEOVER_COMPLETE));
        } catch (Exception e) {
            logger.logStack(logger.WARNING, "Broadcast TAKEOVER_COMPLETE got exception", e);
        }
    }

    public void receivedTakeoverComplete(BrokerAddress sender, ClusterTakeoverInfo cti) {
        if (sender != null) {
            Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                          ProtocolGlobals.G_TAKEOVER_COMPLETE),
                                          "["+cti+"]", sender };
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE_NOTIFICATION, args));
        }
        TakingoverEntry toe = TakingoverEntry.takeoverComplete(takingoverBrokers, cti);
        if (toe == null) {
            return;
        }
        Thread t = new TakeoverCleanupThread(takeoverCleanupTG, this, sender, cti, toe,
                                             ProtocolGlobals.G_TAKEOVER_COMPLETE);
        t.start();
    }

    public void receivedTakeoverPending(BrokerAddress sender, ClusterTakeoverInfo cti) {
        TakingoverEntry toe = TakingoverEntry.addTakingoverEntry(takingoverBrokers, cti);
        if (toe != null || DEBUG_CLUSTER_ALL || DEBUG_CLUSTER_TAKEOVER) {
            Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                          ProtocolGlobals.G_TAKEOVER_PENDING),
                                          "["+cti+"]", sender };
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE_NOTIFICATION, args));
        }
        boolean doconverge = true;
        if (toe == null || (getBrokerList(sender, null)).length == 0) {
            sendTakeoverPendingReply(sender, cti, Status.OK, null);
            if (toe == null) {
                return;
            }
            doconverge = false;
        }
        Thread t = new TakeoverCleanupThread(takeoverCleanupTG, this, sender, 
                       cti, toe, ProtocolGlobals.G_TAKEOVER_PENDING, doconverge);
        t.start();
    }

    public void receivedTakeoverPendingReply(BrokerAddress sender, GPacket reply) {
        if (DEBUG_CLUSTER_ALL || DEBUG_CLUSTER_TAKEOVER) {
            Object[] args = new Object[]{ 
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_TAKEOVER_PENDING_REPLY),
                            "["+ClusterTakeoverInfo.toString(reply)+"]", sender };
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));
        }
        Long xid = ClusterTakeoverInfo.getReplyXid(reply);
        if (xid == null) {;
            logger.log(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
            "Received takeover reply without correlation ID from "+sender+" : "
            +ClusterTakeoverInfo.toString(reply));
            return;
        }

        if (!takeoverPendingReplyTracker.notifyReply(xid, sender, reply)) {
            Object[] args = new Object[]{
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_TAKEOVER_PENDING_REPLY),
                            "["+ClusterTakeoverInfo.toString(reply)+"]", sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    public void receivedTakeoverAbort(BrokerAddress sender, ClusterTakeoverInfo cti) {
        if (sender != null) {
            Object[] args = new Object[]{ ProtocolGlobals.getPacketTypeDisplayString(
                                          ProtocolGlobals.G_TAKEOVER_ABORT),
                                          "["+cti+"]", sender };
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE_NOTIFICATION, args));
        }
        TakingoverEntry.removeTakingoverEntry(takingoverBrokers, cti);
        takeoverPendingReplyTracker.abortWaiter(cti.getXid());
    }

    private void forwardTakeoverBrokers(BrokerAddress ba, boolean all) {
        if (!Globals.getHAEnabled()) return;
        if (all) {
            Set<TakingoverEntry> toes = null;
            synchronized(takingoverBrokers) {
                toes = new HashSet(takingoverBrokers.keySet());
            }
            if (toes.size() == 0) {
                return;
            }
            logger.log(logger.INFO, br.getKString(
                       br.I_CLS_PROCESS_CACHED_TAKEOVERS_FORWARD,
                       String.valueOf(toes.size()), ba));
            GPacket[] gps = null;
            TakingoverEntry toe = null; 
            Iterator<TakingoverEntry> itr = toes.iterator();
            while (itr.hasNext()) {
                toe = itr.next();
                gps = toe.getNotificationGPackets();
                for (int i = 0; i < gps.length; i++) {
                    if (i == 0) {
                        Object[] args = { String.valueOf(gps.length),
                            ProtocolGlobals.getPacketTypeDisplayString(gps[i].getType()), ba };
                        logger.log(logger.INFO, br.getKString(
                                   br.I_CLS_FORWARD_CACHED_TAKEOVERS, args));
                    }
                    try {
                        c.unicast(ba, gps[i]);
                    } catch (IOException e) {/* Ignore */}
                }
            }
            return; 
        }
        TakingoverEntry toe = (TakingoverEntry)takingoverBrokers.get(
                 new TakingoverEntry(ba.getBrokerID(), ba.getStoreSessionUID()));
        if (toe == null) return;
        GPacket gp = toe.getNotificationGPacket(ba);
        if (gp == null) return; 
        try {
            c.unicastAndClose(ba, gp);
        } catch (IOException e) {/* Ignore */}
    }


    private void initConfigServer() {
        logger.log(Logger.FORCE, br.I_MBUS_I_AM_MASTER);

	    boolean masteripChanged = false;
	    BrokerAddress lastConfigServer = getLastConfigServer();
	    BrokerMQAddress nowMQAddr = (BrokerMQAddress)selfAddress.getMQAddress();
	    if (lastConfigServer != null) {
            BrokerMQAddress preMQAddr = (BrokerMQAddress)lastConfigServer.getMQAddress();
            if (!selfAddress.equals(lastConfigServer) &&
		        (nowMQAddr.getHost().getCanonicalHostName().equals(
                           preMQAddr.getHost().getCanonicalHostName()) && 
		         selfAddress.getInstanceName().equals(lastConfigServer.getInstanceName())))  {
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_MASTER_BROKER_IP_CHANGED,
                                                      lastConfigServer, selfAddress));
                masteripChanged = true;
            }
	    }

        // The first change record must be ProtocolGlobals.G_RESET_PERSISTENCE
        try {
            List<ChangeRecordInfo> records = store.getAllConfigRecords();
            byte[] resetEvent = prepareResetPersistenceRecord();

            // XXX There should be a persistence store method to
            // get the size directly. Even though getAllConfigRecords
            // does a shallow clone, this is still quite ugly..

            if (records.size() == 0) {
                logger.log(Logger.INFO, br.I_MBUS_MASTER_INIT);
                store.storeConfigChangeRecord(System.currentTimeMillis(), resetEvent, false);

            } else if (masteripChanged) { //address bug 6293053

               ArrayList recordList = ChangeRecord.compressRecords(records);

               store.clearAllConfigChangeRecords(false);
               long startime = System.currentTimeMillis();
               store.storeConfigChangeRecord(startime, resetEvent, false);

               ChangeRecord cr = null;
               for (int i = 0; i < recordList.size(); i++) {
                    cr = (ChangeRecord)recordList.get(i);
                    if (!cr.isDiscard()) {
                        store.storeConfigChangeRecord(
                          (Globals.isBDBStore() ? startime++:System.currentTimeMillis()),
                          cr.getBytes(), false);
                    }
               }

            }
	    } catch (Exception e) {
            logger.logStack(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR,
                            "Master broker initialization failed.", e);
	    }
    }

    /**
     * Handle jmq administration command to reload and update
     * the cluster configuration.
     */
    public void reloadCluster() {
        logger.log(Logger.INFO, br.I_MBUS_RELOAD_CLS);

        // Broadcast to other brokers.
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_RELOAD_CLUSTER);

        try {
            c.broadcast(gp);
        }
        catch (IOException e) { /* Ignore */ }

        // Notify self.
        c.reloadCluster();
    }

    /**
     * Stop the cluster message inflow.
     */
    public void stopMessageFlow() {
        if (DEBUG) {
            logger.log(Logger.DEBUG,"RaptorProtocol.stopMessageFlow()");
        }

        flowStopped = true;
        sendFlowControlUpdate(null);
    }

    /**
     * Resume the cluster message inflow.
     */
    public void resumeMessageFlow() {
        if (DEBUG) {
            logger.log(Logger.DEBUG, "RaptorProtocol.stopMessageFlow()");
        }

        flowStopped = false;
        sendFlowControlUpdate(null);
    }

    private void sendFlowControlUpdate(BrokerAddress baddr) {
        GPacket gp = GPacket.getInstance();
        if (flowStopped)
            gp.setType(ProtocolGlobals.G_STOP_MESSAGE_FLOW);
        else
            gp.setType(ProtocolGlobals.G_RESUME_MESSAGE_FLOW);

        try {
            if (baddr == null)
                c.broadcast(gp);
            else
                c.unicast(baddr, gp);
        }
        catch (IOException e) { /* Ignore */ }
    }

    /**
     *
     */
    public boolean waitForConfigSync() {
        BrokerAddress configServer = null;
        try {
            configServer = c.getConfigServer();
        }
        catch (Exception e) {
            return true; // There is config server but it's unreachable.
        }

        if (configServer == null)
            return false; // There is no config server.

        if (configServer.equals(selfAddress))
            return false; // I am the config server.

        return (! configSyncComplete); // Waiting for sync complete..
    }

    private void masterBrokerUnBlock() {
        synchronized (masterBrokerBlockedLock) {
            masterBrokerBlocked = false;
        }
    }

    private void masterBrokerBlockWait(int timeout, String opstr)
    throws BrokerException {
        synchronized (masterBrokerBlockedLock) {
            if (masterBrokerBlocked) {
                throw new BrokerException(br.getKString(
                br.X_MASTER_BROKER_OP_IN_PROGRESS, opstr));
            }
            masterBrokerBlocked  = true;
        }

        long defaultInterval = 60000L;
        long waittime = timeout*1000L;
        long endtime = System.currentTimeMillis() + waittime;
        if (waittime > defaultInterval) {
            waittime = defaultInterval; 
        }

        synchronized (configOpLock) {
            while (configOpInProgressCount > 0 && !shutdown) {

            try {
                logger.log(Logger.INFO, br.getKString(
                           br.I_CLUSTER_WAIT_CONFIG_CHANGE_OP_COMPLETE));

                configOpLock.wait(waittime);

            } catch (InterruptedException e) {
                throw new BrokerException(e.toString());
            }
            long curtime = System.currentTimeMillis();
            if (curtime >= endtime)  {
                if (configOpInProgressCount > 0) {
                    throw new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_CONFIG_CHANGE_OP_COMPLETE_TIMEOUT,
                        String.valueOf(timeout)));

                }
            }
            waittime = endtime - curtime;
            if (waittime > defaultInterval) {
                waittime = defaultInterval;
            }

            }
        }
        if (shutdown) {
            throw new BrokerException(br.getKString(br.I_CLUSTER_SHUTDOWN)); 
        }
    }

    private void setConfigOpInProgressIfNotBlocked() throws BrokerException {
        synchronized (masterBrokerBlockedLock) {
            if (masterBrokerBlocked) {
                String emsg = br.getKString(br.X_MASTER_BROKER_OP_IN_PROGRESS, "");
                logger.log(Logger.ERROR, emsg);
                throw new BrokerException(emsg, Status.RETRY);
            } else {
               setConfigOpInProgress(true);
           }
        }
    }

    private void setConfigOpInProgress(boolean flag) {
        synchronized (configOpLock) {
            if (flag) {
                configOpInProgressCount++;
            } else {
                configOpInProgressCount--;
            }
            if (configOpInProgressCount == 0) {
                configOpLock.notifyAll();
            }
        }
    }

    /**
     * Please see comments in method 
     * com.sun.messaging.jmq.jmsserver.core.ClusterBroadcast.changeMasterBroker
     * before make any changes 
     */
    public void changeMasterBroker(BrokerMQAddress newmaster,
                                   BrokerMQAddress oldmaster)
                                   throws BrokerException {
        if (newmaster == null) {
            String emsg = "null new master broker on change master broker request";
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.BAD_REQUEST);
        }

        if (Globals.getHAEnabled()) {
            String emsg =  br.getKString(br.E_OP_NOT_APPLY_TO_HA_BROKER,
                               br.getString(br.M_CHANGE_MASTER_BROKER));
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }
        if (Globals.useSharedConfigRecord()) {
            String emsg =  br.getKString(br.E_OP_NOT_APPLY_NO_MASTER_BROKER_MODE,
                               br.getString(br.M_CHANGE_MASTER_BROKER));
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }
        if (!Globals.dynamicChangeMasterBrokerEnabled()) {
            String emsg = br.getKString(br.X_NO_SUPPORT_DYNAMIC_CHANGE_MASTER_BROKER);
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.NOT_ALLOWED);
        }

        if (Globals.isMasterBrokerSpecified() && !Globals.isJMSRAManagedBroker()) {
            String emsg = br.getKString(
                   br.X_CLUSTER_NO_SUPPORT_CHANGE_MASTER_BROKER_CMDLINE,
                   ClusterManager.CONFIG_SERVER);
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.NOT_ALLOWED);
        }

        ClusterManager cm = Globals.getClusterManager();
        BrokerMQAddress master = (cm.getMasterBroker() == null ?
            null:(BrokerMQAddress)cm.getMasterBroker().getBrokerURL());
        if (master == null) {
            String emsg = br.getKString(br.X_CLUSTER_NO_MASTER_BROKER_REJECT_CHANGE_MASTER);
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        if (oldmaster != null && !oldmaster.equals(master)) {
            String emsg = br.getKString(
                   br.X_CLUSTER_CHANGE_MASTER_BROKER_MISMATCH, oldmaster, master);
            logger.log(Logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        if (newmaster.equals(master)) {
            logger.log(logger.INFO, br.getKString(
                br.I_CLUSTER_CHANGE_MASTER_BROKER_SAME, newmaster));
            return;
        }

        Exception exp = null;
        BrokerAddress masterb = null;
        try {
            masterb = c.getConfigServer();
        } catch (Exception e) {
            exp = e;
        }
        if (masterb == null || exp != null) {
            String expmsg = ((exp instanceof BrokerException) ?
                             (" - "+exp.getMessage()):""); 
            String emsg = br.getKString(br.X_CLUSTER_NO_MASTER_BROKER_REJECT_CHANGE_MASTER)+expmsg;
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg, exp, Status.UNAVAILABLE);
        }

        if (!masterb.equals(selfAddress)) {
            String emsg = br.getKString(
                   br.X_CLUSTER_THIS_BROKER_NOT_MASTER_BROKER_REJECT_CHANGE_MASTER,
                   masterb.toString());
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        if (!configSyncComplete) {
            String emsg = br.getKString(
                   br.X_CLUSTER_MASTER_BROKER_NOT_READY_REJECT_CHANGE_MASTER,
                   masterb.toString());
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        BrokerAddress newmasterb = lookupBrokerAddress(newmaster);
        BrokerInfoEx newmasterbinfo = (BrokerInfoEx)brokerList.get(newmasterb);
        if (newmasterb == null || newmasterbinfo == null) {
            String emsg = br.getKString(
                   br.X_CLUSTER_BROKER_NOT_CONNECTED_REJECT_CHANGE_MASTER,
                   newmaster.toString());
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        Integer ver = newmasterbinfo.getBrokerInfo().getClusterProtocolVersion(); 
        if (ver == null || ver.intValue() < version) {
            String[] args = new String[] { (ver == null ? "null":String.valueOf(ver)), 
                                           newmasterb.toString(), String.valueOf(version),
                                           selfAddress.toString() };
            String emsg = br.getKString(
                   br.X_CLUSTER_CHANGE_MASTER_BROKER_VERSION_MISMATCH, args);
            logger.log(logger.ERROR, emsg);
            throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
        }

        try {
            masterBrokerBlockWait(changeMasterBrokerWaitTimeout, "[CHANGE_MASTER]");
        } catch (Exception e) {
            String emsg = br.getKString(br.E_CHANGE_MASTER_BROKER_FAIL, e.getMessage());
            logger.logStack(logger.ERROR, emsg, e);
            if (e instanceof BrokerException) {
                ((BrokerException)e).overrideStatusCode(Status.PRECONDITION_FAILED);
                throw (BrokerException)e;
            }
            throw new BrokerException(emsg, e, Status.PRECONDITION_FAILED);
        }
        String uuid = null;
        try {
            try {
                uuid = sendNewMasterBrokerPrepareAndWaitReply(newmasterb);
            } catch (Exception e) {
                String emsg = br.getKString(br.E_CHANGE_MASTER_BROKER_FAIL, e.getMessage());
                logger.logStack(logger.ERROR, emsg, e);
                if (e instanceof BrokerException) {
                    ((BrokerException)e).overrideStatusCode(Status.PRECONDITION_FAILED);
                    throw (BrokerException)e;
                }
                throw new BrokerException(emsg, e, Status.PRECONDITION_FAILED);
            }

            c.changeMasterBroker(newmasterb, selfAddress);

            try { 
                sendNewMasterBroker(uuid, newmasterb, masterb, newmasterb, true);
            } catch (Exception e) {
                String emsg = br.getKString(br.E_CHANGE_MASTER_BROKER_FAIL, e.getMessage());
                logger.logStack(logger.ERROR, emsg, e);
                if (e instanceof BrokerException) {
                    ((BrokerException)e).overrideStatusCode(Status.ERROR);
                    throw (BrokerException)e;
                }
                throw new BrokerException(emsg, e, Status.ERROR);
            }
            try {
                storeLastRefreshTime(-1L);
                storeLastConfigServer(newmasterb);
                broadcastNewMasterBroker(uuid, newmasterb, masterb);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            }
        } finally {
            masterBrokerUnBlock();
        }
    }

    private void sendMyReplicationGroupInfo(BrokerAddress to) {
        if (store.isClosed()) {
            return;
        }
        MQAddress backup = Globals.getClusterManager().getBrokerNextToMe();
        if (backup == null || !backup.equals(to.getMQAddress())) {
            return;
        }
        try {
            ClusterReplicationGroupInfo rgi = ClusterReplicationGroupInfo.
                 newInstance(((ReplicableStore)store).getMyReplicationGroupName(),
                             ((MigratableStore)store).getMyEffectiveBrokerID(),
                             ((ReplicableStore)store).getMyReplicationHostPort(), c);
            GPacket gp = rgi.getGPacket();
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,  
                       ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                       "["+rgi+"]", to));
                 c.unicast(to, gp);
        } catch (Exception e) {
            if (e instanceof BrokerException &&
                ((BrokerException)e).getStatusCode() ==  Status.NOT_ALLOWED) {
                logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                    ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_REPLICATION_GROUP_INFO), to)+":"+e.getMessage());
            } else {
                logger.logStack(logger.WARNING, br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                    ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_REPLICATION_GROUP_INFO), to), e);
            }
        }
    }

    public void receivedReplicationGroupInfo(GPacket pkt, BrokerAddress from)
    throws Exception { 
        ClusterReplicationGroupInfo rgi = ClusterReplicationGroupInfo.newInstance(pkt, c);
        logger.log(logger.INFO, "Received replication group info:"+rgi+" from "+from);
        BrokerAddress owner = rgi.getOwnerAddress();
        if (!Globals.getBDBREPEnabled() || !owner.equals(from) || 
             !rgi.getClusterId().equals(Globals.getClusterID())) {

            logger.log(logger.ERROR, "Received unexpected packet "+
                ProtocolGlobals.getPacketTypeDisplayString(
                ProtocolGlobals.G_REPLICATION_GROUP_INFO)+"["+rgi+"], from "+from);
            return;
        }

        ((ReplicableStore)store).joinReplicationGroup(rgi.getGroupName(), rgi.getNodeName(), 
                                   rgi.getMasterHostPort(),
                                   (byte[])null, (Long)null,
                                   false, null, rgi.getOwnerAddress(), rgi);
    }

    public void transferFiles(String[] fileNames, String targetBrokerID,
                              Long syncTimeout, String uuid, String myBrokerID,
                              String module, FileTransferCallback callback)
                              throws BrokerException {

        BrokerAddress addr = lookupBrokerAddress(targetBrokerID);
        if (addr == null) {
            throw new BrokerException(br.getKString(br.X_CLUSTER_BROKER_NOT_ONLINE, targetBrokerID));
        }
        if (module.equals(FileTransferCallback.STORE)) {
            BrokerAddress master = c.getConfigServer();
           if (master != null && selfAddress.equals(master)) {
               throw new BrokerException(
                   br.getKString(br.E_CHANGE_MASTER_BROKER_FIRST,
                   MessageType.getString(MessageType.MIGRATESTORE_BROKER)),
                   Status.NOT_ALLOWED);
           }
        }
        c.transferFiles(fileNames, addr, syncTimeout, uuid,
                        myBrokerID, module, callback);
    }

    public void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout,
                                        String uuid, String myBrokerID)
                                        throws BrokerException {

        BrokerAddress addr = lookupBrokerAddress(targetBrokerID);
        if (addr == null) {
            throw new BrokerException(br.getKString(br.X_CLUSTER_BROKER_NOT_ONLINE, targetBrokerID));
        }
        BrokerAddress master = c.getConfigServer();
        if (master != null && selfAddress.equals(master)) {
            throw new BrokerException(
                br.getKString(br.E_CHANGE_MASTER_BROKER_FIRST,
                MessageType.getString(MessageType.MIGRATESTORE_BROKER)),
               Status.NOT_ALLOWED);
        }
        Long xid =  takeoverMEReplyTracker.addWaiter(
                        new UnicastReplyWaiter(addr,
                            ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY));
        try {
            ClusterTransferFileRequestInfo tfr = ClusterTransferFileRequestInfo.
                                                 newInstance(myBrokerID, uuid, xid);
            try {
                GPacket gp = tfr.getGPacket();
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,  
                           ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                           "["+tfr+"]", addr));
                c.unicast(addr, gp);
            } catch (Exception e) { 
                String emsg = br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                              ProtocolGlobals.getPacketTypeDisplayString(
                              ProtocolGlobals.G_TRANSFER_FILE_REQUEST)+tfr, addr);
                logger.log(logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

            int timeout = (int)Math.min(syncTimeout.longValue(),
                                        ProtocolGlobals.getWaitReplyTimeout()); 
            ReplyStatus reply = null;
            try {
                reply = takeoverMEReplyTracker.waitForReply(xid, timeout);
            } catch (BrokerException e) {
                BrokerException e1 = e;
                if (e.getStatusCode() == Status.GONE) {
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_BROKER_LINK_DOWN, addr.toString()), Status.GONE);
                } else if (e.getStatusCode() == Status.TIMEOUT) {
                    String[]  args = new String[]{ 
                        String.valueOf(timeout),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY),
                        addr.toString() };
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
                }
                throw e1;
            }
            if (reply.getStatus() != Status.OK) {
                String[] args = new String[]{ reply.getReason(),
                                    ProtocolGlobals.getPacketTypeDisplayString(
                                    ProtocolGlobals.G_TRANSFER_FILE_REQUEST),
                                    addr.toString() };
                String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
                throw new BrokerException(emsg, reply.getStatus());
            }
        } finally {
            takeoverMEReplyTracker.removeWaiter(xid);
        }
    }

    public void receivedTransferFileRequest(BrokerAddress sender, GPacket pkt)
    throws Exception {
        ClusterTransferFileRequestInfo tfr = ClusterTransferFileRequestInfo.newInstance(pkt);
        String[] args = new String[] { 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                        tfr.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        c.receivedFileTransferRequest(sender, tfr.getUUID());

    }

    public void receivedTransferFileRequestReply(BrokerAddress sender, GPacket pkt) {

        ClusterTransferFileRequestInfo tfr = ClusterTransferFileRequestInfo.newInstance(pkt);
        
        String[] args = new String[] { 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                  tfr.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        Long xid = ClusterTransferFileRequestInfo.getReplyPacketXid(pkt);

        if (!takeoverMEReplyTracker.notifyReply(xid, sender, pkt)) {
            args = new String[]{
                       ProtocolGlobals.getPacketTypeDisplayString(
                       ProtocolGlobals.G_TRANSFER_FILE_REQUEST_REPLY),
                       xid.toString(), sender.toString() };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    public String sendTakeoverMEPrepare(String brokerID,
                   byte[] commitToken, Long syncTimeout, String uuid)
                   throws BrokerException {

        BrokerAddress addr = lookupBrokerAddress(brokerID);
        if (addr == null) {
            throw new BrokerException("Broker not connected "+brokerID);
        }
        BrokerAddress master = c.getConfigServer();
        if (master != null && selfAddress.equals(master)) {
            throw new BrokerException(
                br.getKString(br.E_CHANGE_MASTER_BROKER_FIRST,
                MessageType.getString(MessageType.MIGRATESTORE_BROKER)),
               Status.NOT_ALLOWED);
        }
        Long xid =  takeoverMEReplyTracker.addWaiter(
                        new UnicastReplyWaiter(addr,
                            ProtocolGlobals.G_TAKEOVER_ME_PREPARE_REPLY));
        try {
            ClusterTakeoverMEPrepareInfo tme = ClusterTakeoverMEPrepareInfo.
                                newInstance(((ReplicableStore)store).getMyReplicationGroupName(),
                                ((MigratableStore)store).getMyEffectiveBrokerID(),
                                ((ReplicableStore)store).getMyReplicationHostPort(), 
                                commitToken, syncTimeout, brokerID, uuid, xid, c);
            try {
                GPacket gp = tme.getGPacket();
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,  
                           ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                           "["+tme+"]", addr));
                c.unicast(addr, gp);
            } catch (Exception e) { 
                String emsg = br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                              ProtocolGlobals.getPacketTypeDisplayString(
                              ProtocolGlobals.G_TAKEOVER_ME_PREPARE)+tme, addr);
                logger.log(logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

            int timeout = (int)syncTimeout.longValue()+
                          ProtocolGlobals.getWaitReplyTimeout(); 
            ReplyStatus reply = null;
            try {
                reply = takeoverMEReplyTracker.waitForReply(xid, timeout);
            } catch (BrokerException e) {
                BrokerException e1 = e;
                if (e.getStatusCode() == Status.GONE) {
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_BROKER_LINK_DOWN, addr.toString()), Status.GONE);
                } else if (e.getStatusCode() == Status.TIMEOUT) {
                    String[]  args = new String[]{ 
                        String.valueOf(timeout),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_TAKEOVER_ME_PREPARE_REPLY),
                        addr.toString() };
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
                }
                throw e1;
            }
            if (reply.getStatus() != Status.OK) {
                String[] args = new String[]{ reply.getReason(),
                                    ProtocolGlobals.getPacketTypeDisplayString(
                                    ProtocolGlobals.G_TAKEOVER_ME_PREPARE),
                                    addr.toString() };
                String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
                throw new BrokerException(emsg, reply.getStatus());
            }
            return tme.getReplyReplicaHostPort(reply.getReply());

        } finally {
            takeoverMEReplyTracker.removeWaiter(xid);
        }
    }

    public void receivedTakeoverMEPrepare(BrokerAddress sender,
                GPacket pkt, ClusterTakeoverMEPrepareInfo tme)
                throws Exception {

        BrokerStateHandler.setExclusiveRequestLock(ExclusiveRequest.MIGRATE_STORE);
        try {
        String[] args = new String[]{ 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                  tme.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        ((ReplicableStore)store).joinReplicationGroup(tme.getGroupName(), tme.getNodeName(),
                                   tme.getMasterHostPort(), 
                                   tme.getCommitToken(), tme.getSyncTimeout(),
                                   true, tme.getUUID(), tme.getOwnerAddress(), tme);
        } finally {
        BrokerStateHandler.unsetExclusiveRequestLock(ExclusiveRequest.MIGRATE_STORE);
        }
    }

    public void receivedTakeoverMEPrepareReply(BrokerAddress sender, GPacket pkt) {
        Long xid = ClusterTakeoverMEPrepareInfo.getReplyPacketXid(pkt);

        Object[] args = new Object[] {
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
            ClusterTakeoverMEPrepareInfo.getReplyPacketXid(pkt)+"", sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        if (!takeoverMEReplyTracker.notifyReply(xid, sender, pkt)) {
            args = new Object[]{
                       ProtocolGlobals.getPacketTypeDisplayString(
                       ProtocolGlobals.G_TAKEOVER_ME_PREPARE_REPLY),
                       xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    public String sendTakeoverME(String brokerID, String uuid)
    throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.sendTakeoverME, to " + brokerID); 
        }
        BrokerAddress addr = lookupBrokerAddress(brokerID);
        if (addr == null) {
            throw new BrokerException("Broker not connected "+brokerID);
        }

        Long xid =  takeoverMEReplyTracker.addWaiter(
                        new UnicastReplyWaiter(addr,
                            ProtocolGlobals.G_TAKEOVER_ME_REPLY));
        try {

            ClusterTakeoverMEInfo tme = null;
            if (Globals.getBDBREPEnabled()) { 
                tme = ClusterTakeoverMEInfo.newInstance(
                                ((ReplicableStore)store).getMyReplicationGroupName(),
                                ((MigratableStore)store).getMyEffectiveBrokerID(),
                                ((ReplicableStore)store).getMyReplicationHostPort(),
                                brokerID, uuid, xid, c);
            } else {
                tme = ClusterTakeoverMEInfo.newInstance(
                                ((MigratableStore)store).getMyEffectiveBrokerID(),
                                brokerID, uuid, xid, c);
            }
            try {
                GPacket gp = tme.getGPacket();
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,
                           ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                           "["+tme+"]", addr.toString()));
                c.unicast(addr, gp);
            } catch (Exception e) { 
                String emsg = br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                                 ProtocolGlobals.getPacketTypeDisplayString(
                                 ProtocolGlobals.G_TAKEOVER_ME)+tme, addr);
                logger.log(logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

            ReplyStatus reply = null;
            try {
                reply = takeoverMEReplyTracker.waitForReply(xid,
                           ProtocolGlobals.getWaitReplyTimeout());
            } catch (BrokerException e) {
                BrokerException e1 = e;
                if (e.getStatusCode() == Status.GONE) {
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_BROKER_LINK_DOWN, addr.toString()), Status.GONE);
                } else if (e.getStatusCode() == Status.TIMEOUT) {
                    String[]  args = new String[]{
                        String.valueOf(ProtocolGlobals.getWaitReplyTimeout()),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_TAKEOVER_ME_REPLY),
                        addr.toString() };
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
                }
                throw e1;
            }
            if (reply.getStatus() != Status.OK) {
                String[] args = new String[]{ reply.getReason(),
                                    ProtocolGlobals.getPacketTypeDisplayString(
                                    ProtocolGlobals.G_TAKEOVER_ME),
                                    addr.toString() };
                String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
                throw new BrokerException(emsg, reply.getStatus());
            }

        } finally {
            takeoverMEReplyTracker.removeWaiter(xid);
        }
        return addr.getMQAddress().getHostAddressNPort();
    }

    public void receivedTakeoverME(BrokerAddress sender,
                GPacket pkt, ClusterTakeoverMEInfo tme)
                throws Exception {

        String[] args = new String[] { 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                  tme.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        tme.setParent(this);
        ((MigratableStore)store).initTakeoverBrokerStore(tme.getGroupName(), tme.getNodeName(),
            tme.getMasterHostPort(), tme.getUUID(), tme.getOwnerAddress(), tme);
    }

    protected void sendTakeoverMEReply(ClusterTakeoverMEInfo tme, int status,
                                       String reason, BrokerAddress to) {

        GPacket reply = tme.getReplyGPacket(status, reason);
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,
                   ProtocolGlobals.getPacketTypeDisplayString(reply.getType())+
                   "["+tme.getReplyToString(reply)+"]", to.toString()));


        Long xid = (Long)reply.getProp("X");
        takeoverMEReplyTracker.addWaiter(xid, new UnicastReplyWaiter(to,
                               ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK));
        try {
            c.unicastUrgent(to, reply);
        } catch (Exception e) {
            String[] args = new String[] {
                ProtocolGlobals.getPacketTypeDisplayString(
                    ProtocolGlobals.G_TAKEOVER_ME_REPLY),
                    to.toString(), this.toString() };
            logger.logStack(logger.ERROR, br.getKString(
                br.E_CLUSTER_SEND_PACKET_FAILED, args), e);
            return;
        }

        int timeout = ProtocolGlobals.getWaitReplyTimeout();
        ReplyStatus replyack = null;
        try {
            replyack = takeoverMEReplyTracker.waitForReply(xid, timeout);
        } catch (BrokerException e) {
            String emsg = e.getMessage();
            if (e.getStatusCode() == Status.GONE) {
                emsg = br.getKString(br.X_CLUSTER_BROKER_LINK_DOWN, to.toString());
            } else if (e.getStatusCode() == Status.TIMEOUT) {
                String[]  args = new String[]{
                        String.valueOf(timeout),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK),
                        to.toString() };
                emsg = br.getKString(br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args);
           }
           String[] args = { ProtocolGlobals.getPacketTypeDisplayString(
                             ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK),
                             to.toString(), emsg };
           logger.log(logger.WARNING,
               br.getKString(br.E_CLUSTER_ABORT_WAIT_REPLY, args));
           return;
        }
        if (replyack.getStatus() != Status.OK) {
            String[] args = new String[]{ replyack.getReason(),
                                ProtocolGlobals.getPacketTypeDisplayString(
                                ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK),
                                to.toString() };
             String emsg = br.getKString(
                 br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
             logger.log(logger.WARNING, emsg);
        }
    }

    public void receivedTakeoverMEReply(BrokerAddress sender, GPacket pkt) {
        Long xid = ClusterTakeoverMEInfo.getReplyPacketXid(pkt);

        Object[] args = new Object[] {
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
            xid+"", sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        if (!takeoverMEReplyTracker.notifyReply(xid, sender, pkt)) {
            args = new Object[]{
                       ProtocolGlobals.getPacketTypeDisplayString(
                       ProtocolGlobals.G_TAKEOVER_ME_REPLY),
                       xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
        GPacket replyack = ClusterTakeoverMEInfo.getReplyAckGPacket(pkt);

        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,
            ProtocolGlobals.getPacketTypeDisplayString(replyack.getType())+
            "["+ClusterTakeoverMEInfo.getReplyAckToString(replyack)+"]", sender.toString()));
        try {
            c.unicast(sender, replyack);
        } catch (Exception e) {
            String emsg = br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                             ProtocolGlobals.getPacketTypeDisplayString(
                             ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK), sender);
            logger.log(logger.WARNING, emsg+": "+e.getMessage());
        }
    }

    public void receivedTakeoverMEReplyAck(BrokerAddress sender, GPacket pkt) {
        Long xid = ClusterTakeoverMEInfo.getReplyPacketXid(pkt);
        Object[] args = new Object[] {
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
            xid+"", sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        if (!takeoverMEReplyTracker.notifyReply(xid, sender, pkt)) {
            args = new Object[] {
                       ProtocolGlobals.getPacketTypeDisplayString(
                       ProtocolGlobals.G_TAKEOVER_ME_REPLY_ACK),
                       xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    private String sendNewMasterBrokerPrepareAndWaitReply(BrokerAddress newmaster)
    throws Exception {

        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendNewMasterBrokerPrepare, to = " + newmaster); 
        }

        String uuid = null;

        ArrayList<ChangeRecordInfo> records = store.getConfigChangeRecordsSince(-1);
        //long now = System.currentTimeMillis();
        ArrayList<ChangeRecord> recordList = ChangeRecord.compressRecords(records);

        records.clear();
        records =  new ArrayList<ChangeRecordInfo>();
        records.add(ChangeRecord.makeResetRecord(false));

        ChangeRecord cr = null;
        ChangeRecordInfo cri = null;
        for (int i = 0; i < recordList.size(); i++) {
            cr = recordList.get(i);
            if (!cr.isDiscard()) {
                cri = new ChangeRecordInfo();
                cri.setRecord(cr.getBytes());
                records.add(cri);
            }
        }

        Long xid =  newMasterBrokerReplyTracker.addWaiter(
                        new UnicastReplyWaiter(newmaster,
                            ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY));
        try {
            ClusterNewMasterBrokerPrepareInfo nmpi = ClusterNewMasterBrokerPrepareInfo.
                                                newInstance(newmaster, records, xid, c);
            GPacket gp = nmpi.getGPacket();
            logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,  
                       ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                       "["+nmpi+"]", newmaster.toString()));
            uuid = nmpi.getUUID();
            c.unicast(newmaster, gp);

            ReplyStatus reply = null;
            try {
                reply = newMasterBrokerReplyTracker.waitForReply(
                            xid, changeMasterBrokerWaitTimeout); 
            } catch (BrokerException e) {
                BrokerException e1 = e;
                if (e.getStatusCode() == Status.GONE) {
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_BROKER_LINK_DOWN, newmaster.toString()), Status.GONE);
                } else if (e.getStatusCode() == Status.TIMEOUT) {
                    String[]  args = new String[]{ 
                        String.valueOf(changeMasterBrokerWaitTimeout),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY),
                        newmaster.toString() };
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
                }
                throw e1;
            }
         
            if (reply.getStatus() != Status.OK) {
                String[] args = new String[]{ reply.getReason(),
                                    ProtocolGlobals.getPacketTypeDisplayString(
                                    ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE),
                                    newmaster.toString() };
                String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
                throw new BrokerException(emsg, reply.getStatus());
            }

        } finally {
            newMasterBrokerReplyTracker.removeWaiter(xid); 
        }
        return uuid;
    }

    public void receivedNewMasterBrokerPrepare(BrokerAddress sender,
                                               GPacket pkt)
                                               throws Exception {

        if (Globals.isMasterBrokerSpecified() && !Globals.isJMSRAManagedBroker()) {
            throw new BrokerException(br.getKString(
                br.X_CLUSTER_NO_SUPPORT_CHANGE_MASTER_BROKER_CMDLINE,
                ClusterManager.CONFIG_SERVER));
        }

        BrokerStateHandler.setExclusiveRequestLock(ExclusiveRequest.CHANGE_MASTER_BROKER);
        try {
        synchronized(newMasterBrokerLock) {

        ClusterNewMasterBrokerPrepareInfo nmpi = 
            ClusterNewMasterBrokerPrepareInfo.newInstance(pkt, c);
        String[] args = new String[]{ 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                  nmpi.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        BrokerAddress configServer = c.getConfigServer();
        if (configServer == null) {
            throw new Exception("Unexpected: there is no master broker configured");
        }
        if (!configServer.equals(sender)) {
           args = new String[] { 
               configServer.toString(), sender.toString(),
               ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()), 
                                        };
           String emsg = br.getKString(br.X_CLUSTER_NOT_CURRENT_MASTER_BROKER_REJECT, args); 
           logger.log(logger.WARNING, emsg);
           throw new BrokerException(emsg);
        }
        if (!configSyncComplete) { 
            args = new String[] { 
               configServer.toString(),
               ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()), 
               sender.toString() };
           String emsg = br.getKString(br.X_CLUSTER_NOT_SYNC_WITH_MASTER_BROKER_REJECT, args); 
           logger.log(logger.WARNING, emsg);
           throw new BrokerException(emsg);
        }

        byte[] buf = nmpi.getRecords();
        if (buf == null) {
            throw new BrokerException("Unexpected: received no payload for "+nmpi);
        }
        store.clearAllConfigChangeRecords(false);
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        InputStream dis = new DataInputStream(bis);
        int count = nmpi.getRecordCount();
        long startime = System.currentTimeMillis();
        try {
            for (int i = 0; i < count; i++) {
                GPacket gp = GPacket.getInstance();
                gp.read(dis);
                if (i == 0 &&
                    gp.getType() != ProtocolGlobals.G_RESET_PERSISTENCE) {
                    throw new BrokerException(br.getKString(
                        br.X_CLUSTER_NEW_MASTER_PREPARE_FIRST_RECORD_NOT_RESET,
                        ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                        sender.toString()));
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                gp.write(bos);
                bos.flush();
                byte[] record = bos.toByteArray();
                bos.close();
                store.storeConfigChangeRecord(startime++, record, true);
            }
        } catch (Exception e) {
            try {
            store.clearAllConfigChangeRecords(true);
            } catch (Exception e1) {
            logger.log(logger.WARNING, br.getKString(
            br.X_CLUSTER_FAIL_CLEANUP_INCOMPLETE_CONFIG_RECORDS_AFTER_PROCESS_FAILURE, nmpi.toString()));
            }
            throw e;
        }

        newMasterBrokerPreparedUUID = nmpi.getUUID();
        newMasterBrokerPreparedSender = sender;
        }

        } finally {
        BrokerStateHandler.unsetExclusiveRequestLock(ExclusiveRequest.CHANGE_MASTER_BROKER);
        }
    }

    public void receivedNewMasterBrokerPrepareReply(BrokerAddress sender, GPacket pkt) {
        Long xid = ClusterNewMasterBrokerPrepareInfo.getReplyPacketXid(pkt);

        if (!newMasterBrokerReplyTracker.notifyReply(xid, sender, pkt)) {
            Object[] args = new Object[]{
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_NEW_MASTER_BROKER_PREPARE_REPLY),
                            xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    public void receivedNewMasterBrokerReply(BrokerAddress sender, GPacket pkt) {
        Long xid = ClusterNewMasterBrokerInfo.getReplyPacketXid(pkt);

        if (!newMasterBrokerReplyTracker.notifyReply(xid, sender, pkt)) {
            Object[] args = new Object[]{
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY),
                            xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }

    private Long sendNewMasterBroker(String uuid, BrokerAddress newmaster,
                                     BrokerAddress oldmaster, BrokerAddress to,
                                     boolean waitReply)
                                     throws Exception {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendNewMasterBroker, to = " + to); 
        }

        Long xid =  newMasterBrokerReplyTracker.addWaiter(
                        new UnicastReplyWaiter(to,
                            ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY));
        boolean noremove = false;
        try {

            ClusterNewMasterBrokerInfo nmi = ClusterNewMasterBrokerInfo.
                            newInstance(newmaster, oldmaster, uuid, xid, c);
            GPacket gp = nmi.getGPacket();
            c.unicast(to, gp);
            if (!waitReply) {
                noremove = true; 
                return xid;
            }
            waitNewMasterBrokerReply(xid, newmaster, to);
            return null;

        } finally {
            if (!noremove) {
                newMasterBrokerReplyTracker.removeWaiter(xid); 
            }
        }
    }

    public void receivedNewMasterBroker(BrokerAddress sender, GPacket pkt)
    throws Exception { 

        ClusterNewMasterBrokerInfo nmi = ClusterNewMasterBrokerInfo.newInstance(pkt, c);

        BrokerAddress newmaster = nmi.getNewMasterBroker();
        //BrokerAddress oldmaster = nmi.getOldMasterBroker();

        String[] args = new String[] {
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                    nmi.toString(), sender.toString() };
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        if (Globals.isMasterBrokerSpecified() && !Globals.isJMSRAManagedBroker()) {
            throw new BrokerException(br.getKString(
                br.X_CLUSTER_NO_CHANGE_MASTER_BROKER_CMDLINE,
                ClusterManager.CONFIG_SERVER));
        }

        synchronized(newMasterBrokerLock) {

            BrokerAddress configServer = c.getConfigServer();
            if (!configServer.getMQAddress().equals(sender.getMQAddress()) &&
                !newmaster.getMQAddress().equals(sender.getMQAddress())) {
                args = new String[] {
                     ProtocolGlobals.getPacketTypeDisplayString(pkt.getType())+nmi,
                     sender.toString(), configServer.toString(), newmaster.toString() };
                String emsg = br.getKString(br.X_CLUSTER_RECEIVED_NEW_MASTER_FROM_NON_MASTER, args);
                logger.log(logger.WARNING, emsg);
                throw new BrokerException(emsg);
            }
            if (configServer.getMQAddress().equals(newmaster.getMQAddress())) {
                logger.log(logger.INFO, br.getKString(
                    br.I_CLUSTER_CHANGE_MASTER_BROKER_SAME, newmaster));
                return;
            }
            if (newmaster.equals(selfAddress)) {
                if (newMasterBrokerPreparedUUID == null) {
                    String emsg = br.getKString(
                        br.X_CLUSTER_NEW_MASTER_BROKER_NO_PREPARE,
                            sender.toString()+nmi);
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
                }
                if (!newMasterBrokerPreparedUUID.equals(nmi.getUUID())) {
                    String emsg = br.getKString(
                        br.X_CLUSTER_NEW_MASTER_BROKER_NOT_PREPARED_ONE,
                            sender.toString()+nmi, 
                            newMasterBrokerPreparedUUID+"["+newMasterBrokerPreparedSender+"]");
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
                }
            } 
            if (!configSyncComplete) {
                if (newmaster.equals(selfAddress)) {
                    args = new String[] { configServer.toString(),
                           ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                           sender.toString() };
                    String emsg = br.getKString(
                        br.X_CLUSTER_NOT_SYNC_WITH_MASTER_BROKER_REJECT, args);
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
                } else {
                    String emsg = br.getKString(br.X_CLUSTER_NO_SYNC_WITH_MASTER_BROKER,
                                                configServer.toString());
                    logger.log(logger.INFO, emsg);
                    logger.log(logger.WARNING, br.getKString(
                               br.W_CLUSTER_FORCE_CLOSE_LINK, configServer.toString(), emsg));
                    c.closeLink(configServer, true);
                }
            }
            c.changeMasterBroker(newmaster, configServer);
            storeLastRefreshTime(-1L);
        }
    }

    private void waitNewMasterBrokerReply(Long xid, BrokerAddress newmaster, BrokerAddress from)
    throws BrokerException {

        ReplyStatus reply = null;
        try {
             reply = newMasterBrokerReplyTracker.waitForReply(xid,
                             ProtocolGlobals.getWaitReplyTimeout()); 
        } catch (BrokerException e) {
             BrokerException e1 = e;
             if (e.getStatusCode() == Status.GONE) {
                 e1 = new BrokerException(br.getKString(
                     br.X_CLUSTER_BROKER_LINK_DOWN, newmaster.toString()), Status.GONE);
             } else if (e.getStatusCode() == Status.TIMEOUT) {
                 String[]  args = new String[]{ String.valueOf(changeMasterBrokerWaitTimeout),
                                                ProtocolGlobals.getPacketTypeDisplayString(
                                                ProtocolGlobals.G_NEW_MASTER_BROKER_REPLY),
                                                from.toString() };
                  e1 = new BrokerException(br.getKString(
                      br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
             } 
             throw e1;
        }
         
        if (reply.getStatus() != Status.OK) {
            String[] args = new String[]{ reply.getReason(),
                                          ProtocolGlobals.getPacketTypeDisplayString(
                                          ProtocolGlobals.G_NEW_MASTER_BROKER),
                                          from.toString() };
            String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
            throw new BrokerException(emsg, reply.getStatus());
        }
    }

    private void broadcastNewMasterBroker(String uuid, BrokerAddress newmaster,
                                          BrokerAddress oldmaster) {

         BrokerAddress[] bas = getBrokerList(newmaster, null); 

         ArrayList<Long> xids = new ArrayList<Long>();
         try {
             Long xid = null;
             for (int i = 0; i < bas.length; i++) {
                 logger.log(logger.INFO, br.getKString(
                     br.I_CLUSTER_ANNOUNCE_NEW_MASTER_BROKER, 
                     bas[i].toString(), newmaster.toString()));

                 try {
                     xid = sendNewMasterBroker(uuid, newmaster, oldmaster, bas[i], false);
                 } catch (Exception e) {
                     logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                         ProtocolGlobals.getPacketTypeDisplayString(
                         ProtocolGlobals.G_NEW_MASTER_BROKER), bas[i].toString()));
                 }
                 xids.add(xid);
             }

             Iterator itr = xids.iterator();
             while (itr.hasNext()) {
                 xid = (Long)itr.next();
                 try {
                     waitNewMasterBrokerReply(xid, newmaster,
                         ((UnicastReplyWaiter)
                         newMasterBrokerReplyTracker.getWaiter(xid)).getToBroker());
                 } catch (Exception e) {
                     logger.logStack(logger.WARNING, e.getMessage(), e);
                 }
             }

         } finally {
 
             Long xid = null;
             Iterator itr = xids.iterator();
             while (itr.hasNext()) {
                 xid = (Long)itr.next();
                 newMasterBrokerReplyTracker.removeWaiter(xid); 
             }
        }
    }
    public void notifyPartitionArrival(UID partitionID, String brokerID)
    throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, 
            "RaptorProtocol.notifyPartitionArrival("+partitionID+", "+brokerID+")"); 
        }
        BrokerAddress addr = lookupBrokerAddress(brokerID);
        if (addr == null) {
            throw new BrokerException(br.getKString(
            br.X_CLUSTER_BROKER_NOT_ONLINE, brokerID), Status.NOT_FOUND);
        }
        Long xid =  takeoverMEReplyTracker.addWaiter(
                        new UnicastReplyWaiter(addr,
                            ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY));
        try {

            ClusterNotifyPartitionArrivalInfo npa = 
                         ClusterNotifyPartitionArrivalInfo.
                             newInstance(partitionID, brokerID, xid, c);
            try {
                GPacket gp = npa.getGPacket();
                logger.log(logger.INFO, br.getKString(br.I_CLUSTER_UNICAST,
                           ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+
                           "["+npa+"]", addr.toString()));
                c.unicast(addr, gp);
            } catch (Exception e) { 
                String emsg = br.getKString(br.W_CLUSTER_UNICAST_FAILED,
                                 ProtocolGlobals.getPacketTypeDisplayString(
                                 ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL)+npa, addr);
                logger.log(logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }

            ReplyStatus reply = null;
            try {
                reply = takeoverMEReplyTracker.waitForReply(xid,
                           ProtocolGlobals.getWaitReplyTimeout());
            } catch (BrokerException e) {
                BrokerException e1 = e;
                if (e.getStatusCode() == Status.GONE) {
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_BROKER_LINK_DOWN, addr.toString()), Status.GONE);
                } else if (e.getStatusCode() == Status.TIMEOUT) {
                    String[]  args = new String[]{
                        String.valueOf(ProtocolGlobals.getWaitReplyTimeout()),
                        ProtocolGlobals.getPacketTypeDisplayString(
                        ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY),
                        addr.toString() };
                    e1 = new BrokerException(br.getKString(
                        br.X_CLUSTER_WAIT_REPLY_TIMEOUT, args), Status.TIMEOUT);
                }
                throw e1;
            }
            if (reply.getStatus() != Status.OK) {
                String[] args = new String[]{ reply.getReason(),
                                    ProtocolGlobals.getPacketTypeDisplayString(
                                    ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL),
                                    addr.toString() };
                String emsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
                throw new BrokerException(emsg, reply.getStatus());
            }

        } finally {
            takeoverMEReplyTracker.removeWaiter(xid);
        }
    }

    public void receivedNotifyPartitionArrival(BrokerAddress sender,
                GPacket pkt, ClusterNotifyPartitionArrivalInfo npa)
                throws Exception {

        String[] args = new String[] { 
            ProtocolGlobals.getPacketTypeDisplayString(pkt.getType()),
                                  npa.toString(), sender.toString() }; 
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVE, args));

        //XXXcheck brokerId match

        Globals.getDestinationList().registerPartitionArrivedEvent(
                          npa.getPartitionID(), sender.getBrokerID());
    }

    public void receivedNotifyPartitionArrivalReply(BrokerAddress sender,
                                    GPacket pkt,
                                    ClusterNotifyPartitionArrivalInfo npa) {
        Long xid = npa.getReplyPacketXid(pkt);

        if (!takeoverMEReplyTracker.notifyReply(xid, sender, pkt)) {
            Object[] args = new Object[]{
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_NOTIFY_PARTITION_ARRIVAL_REPLY),
                            xid.toString(), sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
    }


    /**
     * Deliver the message.
     *
     * Constructs and sends ProtocolGlobals.G_MESSAGE_DATA packets.
     */
    public void sendMessage(PacketReference pkt, Collection<Consumer> targets,
                            boolean sendMsgDeliveredAck) {
        HashMap<BrokerAddress, ArrayList[]> m = 
            new HashMap<BrokerAddress, ArrayList[]>();
        if (DEBUG) {
            logger.log(Logger.DEBUGMED,
                "MessageBus: sending message {0} to {1} targets.",
                pkt.getSysMessageID(),
                Integer.toString(targets.size()));
        }

        StringBuffer debugString = new StringBuffer("\n");
        Boolean redeliverFlag = false;
        Iterator<Consumer> itr = targets.iterator();
        while (itr.hasNext()) {
            // TBD: Revisit - Send the  ProtocolGlobals.G_MSG_SENT ack
            // instead of calling Interest.messageSent() ???
            Consumer target = itr.next();
            ConsumerUID intid = target.getConsumerUID();
            ConsumerUID storedid = target.getStoredConsumerUID();
            boolean rflag = pkt.getRedeliverFlag(storedid);
            int dct = pkt.getRedeliverCount(storedid);
            try {
                pkt.delivered(intid, storedid, intid.isUnsafeAck(), true);
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR,
                "saving redeliver flag for " + pkt.getSysMessageID() + " to " + intid, ex);
            }
            if (rflag) {
                redeliverFlag = true;
                if (dct < 1) { 
                    dct = 1;
                }
            }
            BrokerAddress baddr =
                target.getConsumerUID().getBrokerAddress();

            ArrayList[] v = m.get(baddr);
            if (v == null) {
                v = new ArrayList[2];
                v[0] = new ArrayList();
                v[1] = new ArrayList();
                m.put(baddr, v);
            }
            v[0].add(target);
            v[1].add(Integer.valueOf(dct));
            debugString.append("\t").append(target.toString()).append("#"+dct).append("\n");
        }

        if (DEBUG) {
            logger.log(Logger.DEBUGHIGH,
                "MessageBus: Local Targets = {0}", debugString);
        }

        // Now deliver message to other target brokers. The target
        // list may contain multiple entries with same BrokerAddress,
        // Combine all such entries and send the packet only once.

        Iterator<Map.Entry<BrokerAddress, ArrayList[]>> brokers = 
                                          m.entrySet().iterator();
        Map.Entry<BrokerAddress, ArrayList[]> entry = null;
        while (brokers.hasNext()) {
            entry = brokers.next();
            BrokerAddress b = entry.getKey();
            ArrayList[] v = entry.getValue();
            ClusterMessageInfo cmi = ClusterMessageInfo.newInstance(
                pkt, v[0], v[1], redeliverFlag, sendMsgDeliveredAck, c);
            try {
                synchronized(brokerList) {
                    BrokerInfoEx be = (BrokerInfoEx)brokerList.get(b);
                    if (be == null ) {
                        throw new BrokerException(
                        "NOTFOUND: Could not deliver message "+cmi.toString()+ " to "+ b); 
                    }
                    if (be.sentGoodbye()) {
                        throw new BrokerException(
                           "GOODBYE: Could not deliver message "+cmi.toString()+ " to "+ b); 
                    }
                }
                c.unicast(b, cmi.getGPacket(), true);
                if (DEBUG) {
                    logger.log(Logger.DEBUGHIGH,
                        "MessageBus: Broker {0} Targets = {1}", b, debugString + cmi.toString());
                }
            } catch (Exception e) {
                // This exception means that there is no way to
                // deliver this message to the target. If there was
                // an alternate path, the cluster implementation would
                // have automatically used it...
                HashMap map = new HashMap();
                map.put(ClusterBroadcast.MSG_NOT_SENT_TO_REMOTE, "true");
                for (int i = 0; i < v[0].size(); i++) {
                    ConsumerUID intid = ((Consumer)v[0].get(i)).getConsumerUID();
                    try {
                    cb.processRemoteAck(pkt.getSysMessageID(), intid,
                                        ClusterGlobals.MB_MSG_IGNORED, map);
                    } catch (BrokerException ex) {//XXX
                    logger.log(logger.WARNING, ex.getMessage(), ex);  
                    }
                }
                if (DEBUG) {
                    logger.log(Logger.DEBUGHIGH,
                         "RaptorProtocol: Could not deliver message to broker {0}", b);
                }
            }
        }
    }

    public void sendMessageAck(BrokerAddress msgHome, SysMessageID sysid, 
                               ConsumerUID cuid, int ackType, 
                               Map optionalProps, boolean ackack) 
                               throws BrokerException {
        SysMessageID[] sysids = {sysid};
        ConsumerUID[] cuids = {cuid};
        if (ackack) {
            sendMessageAcks(msgHome, sysids, cuids, ackType, 
                optionalProps, null, null, ackack, false, false);
            return;
        }

        try {
            sendMessageAcks(msgHome, sysids, cuids, ackType, 
                optionalProps, null, null, ackack, false, false);
        } catch (BrokerException e) {
            Object[] args = new Object[] { 
                     ClusterGlobals.getAckTypeString(ackType)+
                         (optionalProps == null ? "":"["+optionalProps+"]"),
                     msgHome, "["+sysid+", "+cuid+"]" };
            String emsg = br.getKString(br.E_CLUSTER_SEND_PACKET_FAILED, args);
            boolean logstack = true;
            if ((e instanceof BrokerDownException) ||
                e.getStatusCode() == Status.GONE ||
                e.getStatusCode() == Status.TIMEOUT) {
                logstack = false;
            }
            boolean dolog = true;
            if (ackType == ClusterGlobals.MB_MSG_IGNORED && 
                optionalProps == null) {
                dolog = (DEBUG_CLUSTER_MSG ? true:false);
            }
            if (dolog) {
                if (logstack) {
                    logger.logStack(logger.WARNING, emsg, e);
                } else {
                    logger.log(logger.WARNING, emsg+": "+e.getMessage());
                }
            }
        }
    }

    /**
     * Acknowledge a message to the message home broker. We will have to
     * somehow keep track of message home BrokerAddress for every
     * message coming from message bus. This may be done either using
     * the Packet trailer or by maintaining a SysMessageID to
     * message home (BrokerAddress) mapping.
     *
     * Constructs and sends ProtocolGlobals.G_MESSAGE_ACK packets.
     */
    public void sendMessageAck2P(BrokerAddress msgHome, SysMessageID[] sysids, 
                                 ConsumerUID[] cuids, int ackType,
                                 Map optionalProps, Long txnID, UID txnStoreSession,
                                 boolean ackack, boolean async)
                                 throws BrokerException {

        sendMessageAcks(msgHome, sysids, cuids, ackType, optionalProps, 
                        txnID, txnStoreSession, ackack, async, true);
    }

    private void sendMessageAcks(BrokerAddress msgHome, SysMessageID[] sysids, 
                                 ConsumerUID[] cuids, int ackType,
                                 Map optionalProps, Long txnID, UID txnStoreSession,
                                 boolean ackack, boolean async, boolean twophase)
                                 throws BrokerException {

        if (fi.FAULT_INJECTION) {
            if (ackType == ClusterGlobals.MB_MSG_IGNORED &&
                optionalProps != null) {
                synchronized(fi) {
                    if (fi.checkFault(fi.FAULT_MSG_REMOTE_ACK_P_ACKIGNORE_1_EXCEPTION, null)) {
                        fi.unsetFault(fi.FAULT_MSG_REMOTE_ACK_P_ACKIGNORE_1_EXCEPTION);
                        throw new BrokerDownException("FAULT: unreachable message home broker"+msgHome);
                    }
                }
            } else if (ackType == ClusterGlobals.MB_MSG_TXN_ROLLEDBACK) {
                synchronized(fi) {
                    if (fi.checkFault(fi.FAULT_MSG_REMOTE_ACK_P_TXNROLLBACK_1_EXCEPTION, null)) {
                        fi.unsetFault(fi.FAULT_MSG_REMOTE_ACK_P_TXNROLLBACK_1_EXCEPTION);
                        throw new BrokerException("FAULT: unreachable message home broker "+msgHome);
                    }
                }
            }
            ClusterMessageAckInfo.CHECKFAULT(ackCounts, ackType, txnID, 
                 FaultInjection.MSG_REMOTE_ACK_P, FaultInjection.STAGE_1);
        }

        Long xid = null;
        if (ackack) {
           xid = ackackTracker.addWaiter(new MessageAckReplyWaiter(msgHome)); 
        }
        ClusterMessageAckInfo cai =  ClusterMessageAckInfo.newInstance(
                                         sysids, cuids, ackType, xid, async, optionalProps, 
                                         txnID, txnStoreSession, msgHome, c, twophase);
        if (DEBUG || DEBUG_CLUSTER_TXN) {
        logger.log(Logger.INFO, "MessageBus: Sending message ack: " + cai.toString());
        }

        try {
            synchronized(brokerList) {
                BrokerInfoEx be = (BrokerInfoEx)brokerList.get(msgHome); //XXX 1-1 addr - brokerInfo
                if (be == null && msgHome !=  Globals.getMyAddress()) {
                    BrokerException e = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_HOME_UNREACHABLE, cai.toString(), msgHome), Status.GONE);
                    e.setRemote(true);
                    e.setRemoteBrokerAddress(msgHome);
                    throw e;
                }
                if (be != null && (be.sentGoodbye() || be.gotGoodbye())) {
                    BrokerException e = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_GOODBYED_HOME, cai.toString(), msgHome), Status.GONE);
                    e.setRemote(true);
                    e.setRemoteBrokerAddress(msgHome);
                    throw e;
                }
                if (isTakeoverTarget(msgHome)) {
                    BrokerException e = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_HOME_BEING_TAKEOVER, cai.toString(), msgHome), Status.GONE); 
                    e.setRemote(true);
                    e.setRemoteBrokerAddress(msgHome);
                    throw e;
                }
                c.unicast(msgHome, cai.getGPacket());
                if (fi.FAULT_INJECTION) {
                ClusterMessageAckInfo.CHECKFAULT(ackCounts, ackType, txnID, 
                FaultInjection.MSG_REMOTE_ACK_P, FaultInjection.STAGE_2);
                }
            }
            if (ackack && !async) {
                ReplyStatus reply = null;
                try {
                    reply = ackackTracker.waitForReply(xid, ProtocolGlobals.getAckTimeout());
                } catch (BrokerException e) {
                    if (e.getStatusCode() == Status.GONE) {
                        BrokerException e1 = new BrokerDownException(br.getKString(
                        br.X_CLUSTER_MSG_ACK_FAILED_HOME_GONE, cai.toString(), msgHome), Status.GONE);
                        e1.setRemote(true);
                        e1.setRemoteBrokerAddress(msgHome);
                        throw e1;
                    }
                    if (e.getStatusCode() == Status.TIMEOUT) {
                        BrokerException e1 = new BrokerDownException(br.getKString(
                        br.X_CLUSTER_MSG_ACK_FAILED_HOME_NORESPONSE, cai.toString(), msgHome), Status.GONE);
                        e1.setRemote(true);
                        e1.setRemoteBrokerAddress(msgHome);
                        throw e1;
                    }
                    throw e;
                }
                if (reply.getStatus() != Status.OK) {
                    logger.log(logger.WARNING, br.getKString(
                           br.W_CLUSTER_MSG_ACK_FAILED_FROM_HOME, 
                           msgHome, ClusterMessageAckInfo.toString(reply.getReply())));
                    AckEntryNotFoundException aee = ClusterMessageAckInfo.getAckEntryNotFoundException(
                                                                                       reply.getReply());
                    if (aee != null) throw aee;
                    throw new BrokerException(reply.getReason(), reply.getStatus());
                }
                if (fi.FAULT_INJECTION) {
                    if (ackType == ClusterGlobals.MB_MSG_TXN_PREPARE) {
                        synchronized(fi) {
                            if (fi.checkFault(fi.FAULT_MSG_REMOTE_ACK_P_TXNPREPARE_3_1, null)) {
                                fi.unsetFault(fi.FAULT_MSG_REMOTE_ACK_P_TXNPREPARE_3_1);
                                BrokerException e1 = new BrokerDownException(br.getKString(
                                                         br.X_CLUSTER_MSG_ACK_FAILED_HOME_NORESPONSE,
                                                         cai.toString(), msgHome), Status.GONE);
                                e1.setRemote(true);
                                e1.setRemoteBrokerAddress(msgHome);
                                throw e1;
                            }
                        }
                    }
                    ClusterMessageAckInfo.CHECKFAULT(ackCounts, ackType, txnID, 
                    FaultInjection.MSG_REMOTE_ACK_P, FaultInjection.STAGE_3);
                }
            }
        }
        catch (Exception e) {
            if (e instanceof BrokerDownException) throw (BrokerException)e;
            synchronized(brokerList) {
                BrokerInfoEx be = (BrokerInfoEx)brokerList.get(msgHome); 
                if (be == null && msgHome != Globals.getMyAddress()) {
                    BrokerException e1 = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_HOME_UNREACHABLE, cai.toString(), msgHome), Status.GONE);
                    e1.setRemote(true);
                    e1.setRemoteBrokerAddress(msgHome);
                    throw e1;
                }
                if (be != null && (be.sentGoodbye() || be.gotGoodbye())) {
                    BrokerException e1 = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_GOODBYED_HOME, cai.toString(), msgHome), Status.GONE);
                    e1.setRemote(true);
                    e1.setRemoteBrokerAddress(msgHome);
                    throw e1;
                }
                if (isTakeoverTarget(msgHome)) {
                    BrokerException e1 = new BrokerDownException(br.getKString(
                    br.X_CLUSTER_MSG_ACK_HOME_BEING_TAKEOVER, cai.toString(), msgHome), Status.GONE); 
                    e1.setRemote(true);
                    e1.setRemoteBrokerAddress(msgHome);
                    throw e1;
                }
            }
            if (e instanceof  BrokerException) throw (BrokerException)e;
            throw new BrokerException(e.getMessage(), e, Status.ERROR);
        } finally {
            if (ackack) ackackTracker.removeWaiter(xid);
        }
    }

    public void receivedMessageAckReply(BrokerAddress sender, GPacket ackack) {
        Long xid = ClusterMessageAckInfo.getAckAckXid(ackack);
        if (xid == null) {
            try {
            if (Globals.getClusterBroadcast().getClusterVersion() <  
                ClusterBroadcast.VERSION_410) {
                logger.log(logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Received message ack reply without correlation ID from "+sender+" : "
                +ClusterMessageAckInfo.toString(ackack));
            } else {
                logger.log(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Received message ack reply without correlation ID from "+sender+" : "
                +ClusterMessageAckInfo.toString(ackack));
            }
            } catch (Exception e) {
            logger.log(logger.WARNING, 
            "XXXI18n-Unable to get cluster version to process message ack reply"+
            ClusterMessageAckInfo.toString(ackack)+": "+e.getMessage()+" from "+sender);
            }
            return;
        }

        if (!ackackTracker.notifyReply(xid, sender, ackack)) {
            Object[] args = new Object[]{ 
                            ProtocolGlobals.getPacketTypeDisplayString(
                            ProtocolGlobals.G_MESSAGE_ACK_REPLY),
                            "["+ClusterMessageAckInfo.toString(ackack)+"]", sender };
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_UNABLE_NOTIFY_REPLY, args));
        }
      
    }

    public static byte[] prepareResetPersistenceRecord() {
    
        if (DEBUG) {
            Globals.getLogger().log(Logger.DEBUG,
                "RaptorProtocol.prepareResetPersistenceRecord");
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_RESET_PERSISTENCE);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            gp.write(bos);
            bos.flush();

            return bos.toByteArray();
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * This method is used when a new interest is created and we need
     * to send a broadcast.
     */
    private void sendNewInterestUpdate(Consumer interest) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendNewInterestUpdate : " +
                interest.toString());
        }
        ClusterConsumerInfo cci =  ClusterConsumerInfo.newInstance(interest, c);
        try {
            c.broadcast(cci.getGPacket(ProtocolGlobals.G_NEW_INTEREST));
        }
        catch (IOException e) { /* Ignore */ }
    }

    /**
     * This method is used when a new broker-to-broker connection is
     * established. All the current interests are forwarded.
     */
    private void sendNewInterestUpdate(BrokerAddress to, Collection consumers) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendNewInterestUpdate to " +
                to);
        }

        ClusterConsumerInfo cci =  ClusterConsumerInfo.newInstance(consumers, c);
        try {
            c.unicast(to, cci.getGPacket(ProtocolGlobals.G_NEW_INTEREST));
        }
        catch (IOException e) { /* Ignore */ }
    }

    /**
     * This method is used when a new interest is created and we need
     * to send a broadcast.
     */
    private void sendAttachDurable(BrokerAddress to, Subscription sub, Consumer interest) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendAttachDurable("+to+", "+
                 Subscription.getDSubLogString(sub.getClientID(), sub.getDurableName())+
                ") ,"+interest);
        }

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub, interest, c);
        try {
            if (to == null) { 
            c.broadcast(csi.getGPacket(ProtocolGlobals.G_DURABLE_ATTACH));
            } else {
            c.unicast(to, csi.getGPacket(ProtocolGlobals.G_DURABLE_ATTACH));
            }
        }
        catch (IOException e) { /* Ignore */ }
    }


    /**
     * This method is used when a durable interest is unsubscribed and
     * we need to send a broadcast.
     */
    private void sendRemDurableUpdate(Consumer interest) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendRemDurableUpdate : " + interest);
        }

        Subscription sub  = null;
        if (interest instanceof Subscription) {
            sub = (Subscription)interest;
        }
        else {
            sub = interest.getSubscription();
        }
        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
        try {
            c.broadcast(csi.getGPacket(ProtocolGlobals.G_REM_DURABLE_INTEREST, false));
        }
        catch (IOException e) { /* Ignore */ }
    }


    /**
     * Send an interest update broadcast for an existing interest.
     *
     * This method is called in following cases -
     * 1. When an interest is activated. (type = G_NEW_PRIMARY_INTEREST)
     * 2. When an interest is removed. (type = G_REM_INTEREST)
     * 3. When an interest is detached. (type = G_DURABLE_DETACH)
     */
    private void sendInterestUpdate(Consumer interest, int type) {
        sendInterestUpdate(interest, type, null, false);
    }

    private void sendInterestUpdate(Consumer interest, int type, Map pendingMsgs, boolean cleanup) {
        ClusterConsumerInfo cci = ClusterConsumerInfo.newInstance(interest, pendingMsgs, cleanup, c);
        BrokerAddress[] bas = getBrokerList();
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.sendInterestUpdate. consumer="+
                       interest+", cleanup="+cleanup+ ", subtype="+
                       ProtocolGlobals.getInterestUpdateSubTypeString(type)+
                       ", pendingmsgs="+pendingMsgs+ " to brokers "+Arrays.toString(bas));
        }
        for (int i = 0; i < bas.length; i++) {
            try {
                c.unicast(bas[i], cci.getGPacket(ProtocolGlobals.G_INTEREST_UPDATE, type, bas[i]));
            } catch (IOException e) {
                logger.logStack(logger.WARNING, br.getKString(BrokerResources.W_CLUSTER_UNICAST_FAILED,
                       ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_INTEREST_UPDATE)+
                       "["+ProtocolGlobals.getInterestUpdateSubTypeString(type)+"]", bas[i]), e);
            }
        }
    }

    /**
     * This method is used when a new broker-to-broker connection is
     * established. All the current active (primary) interests are
     * forwarded.
     */
    private void sendInterestUpdate(BrokerAddress to, Collection consumers,
        int type) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendInterestUpdate. to = " + to);
        }
        ClusterConsumerInfo cci = ClusterConsumerInfo.newInstance(consumers, c);
        try {
            c.unicast(to, cci.getGPacket(ProtocolGlobals.G_INTEREST_UPDATE, type));
        }
        catch (IOException e) { /* Ignore */ }
    }

    /**
     * Broadcast the client closed notification to all the
     */
    private void sendClientClosedUpdate(ConnectionUID conid) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendClientClosedUpdate. conid = " + conid);
        }
        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_CLIENT_CLOSED);
        gp.putProp("I", Long.valueOf(conid.longValue()));

        try {
            c.broadcast(gp);
        }
        catch (IOException e) { /* Ignore */ }
    }

    /**
     * This method should be called when a client connection gets
     * closed. Following things need to happen when a client goes
     * away -
     * <OL>
     * <LI> All the resource locks owned by the client should be
     * released. </LI>
     * <LI> All the interests created by the client should be either
     * removed or detached in case of durables. </LI>
     * <LI> A notification should be sent to all the brokers so that
     * they can clean up any temporary destinations owned by the
     * client. </LI>
     * </OL>
     * @param conid The ConnectionUID representing the client.
     * @param notify If <code> true, </code> all the brokers in
     * the cluster (including 'this' broker) are notified.
     */
    public void clientClosed(ConnectionUID conid, boolean notify) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO, "RaptorProtocol.clientClosed("+conid+")");
        }

        unlockOwnedResources(conid);

        // Tell interest manager to cleanup all the interests.
        if (notify) {
            sendClientClosedUpdate(conid); // Notify other brokers.
            // Notify self..
            cbDispatcher.clientDown(conid);
        }
    }

    private void unlockOwnedResources(Object owner) { 
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,  
                "unlockOwnedResources(owner="+owner+")["+owner.getClass().getName()+"]");
        }
        ArrayList tmpList = new ArrayList();
        synchronized(resTable) {
            Collection entries = resTable.keySet();
            Iterator itr = entries.iterator();
            while (itr.hasNext()) {
                String resId = (String) itr.next();
                Resource res = (Resource) resTable.get(resId);
                Object resowner = res.getOwner();
                if (resowner == null) {
                    continue;
                }
                if (((resowner.getClass() == owner.getClass()) && 
                     resowner.equals(owner)) ||
                    ((resowner instanceof List) && ((List)resowner).contains(owner))) {
                    tmpList.add(resId);
                }
            }
        }
        for (int i = 0; i < tmpList.size(); i++) {
            String resId = (String) tmpList.get(i);
            unlockResource(resId);
        }
    }

    /**
     * Obtain a cluster-wide "shared" lock on a resource.
     * Unlike the normal "exclusive" locks, the shared locks allow
     * more than one clients to access the same resource. This method
     * ensures that the resource cannot be locked as shared and
     * exclusive at the same time!
     *
     * @param resID Resource name. The caller must ensure that
     * there are no name space conflicts between different
     * types of resources. This can be achieved by simply using
     * resource names like -"durable:foo", "queue:foo",
     * "clientid:foo"...
     * @param owner The object representing the owner of the resource
     * @return  ProtocolGlobals.G_LOCK_SUCCESS if the resource was
     *          locked successfully.
     *          ProtocolGlobals.G_LOCK_FAILURE if the resource could
     *          not be locked.
     *          ProtocolGlobals.G_LOCK_TIMEOUT
     */
    public int lockSharedResource(String resId, Object owner) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,
                "RaptorProtocol.lockSharedResource("+resId+ ", owner="+owner+")");
        }
        return lockResource(resId, 0, owner, true);
    }

    /**
     * Obtain a cluster-wide lock on a resource. This method is
     * used to ensure mutual exclusion for durable subscriptions,
     * queue receivers, client IDs etc.
     *
     * @param resID Resource name. The caller must ensure that
     * there are no name space conflicts between different
     * types of resources. This can be achieved by simply using
     * resource names like -"durable:foo", "queue:foo",
     * "clientid:foo"...
     * @param owner The object representing the owner of the resource
     * @return  ProtocolGlobals.G_LOCK_SUCCESS if the resource was locked successfully.
     *          ProtocolGlobals.G_LOCK_FAILURE if the resource could not be locked.
     *          ProtocolGlobals.G_LOCK_TIMEOUT
     */
    public int lockResource(String resId, Object owner) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,
                "RaptorProtocol.lockResource("+resId+", owner="+owner+")");
        }
        return lockResource(resId, 0, owner);
    }

    /**
     * Obtain a cluster-wide lock on a resource. This method is
     * used to ensure mutual exclusion for durable subscriptions,
     * queue receivers, client IDs etc.
     *
     * @param resID Resource name. The caller must ensure that
     * there are no name space conflicts between different
     * types of resources. This can be achieved by simply using
     * resource names like -"durable:foo", "queue:foo",
     * "clientid:foo"...
     *
     * @param timestamp The creation time for the resource.
     * In case of a lock contention the older resource automatically
     * wins.
     *
     * @param owner The object representing the owner of the resource
     *
     * @return  ProtocolGlobals.G_LOCK_SUCCESS if the resource was locked successfully.
     *          ProtocolGlobals.G_LOCK_FAILURE if the resource could not be locked.
     *          ProtocolGlobals.G_LOCK_TIMEOUT if the resource could not be locked
     *              because of timed out in waiting for lock request response(s)
     */
    public int lockResource(String resId, long timestamp, Object owner) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,
                "RaptorProtocol.lockResource("+resId+ ", timestamp="+
                 timestamp + ", owner=" + owner+")");
        }
        return lockResource(resId, timestamp, owner, false);
    }

    private int lockResource(String resId, long timestamp,
        Object owner, boolean shared) {
        boolean failOntimeout = resId.startsWith(ClusterBroadcast.DESTINATION_EXCLUSIVE_LOCK_PREFIX);  //XXXpending: interface change 
        int b = 1;

        int i = 0;
        while (true) {
            int ret = tryLockResource(resId, timestamp, owner, shared, failOntimeout);
            if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
                logger.log(logger.INFO, "Lock resource "+resId+" returned: "+
                    ProtocolGlobals.getLockStatusString(ret));
            }
            if (ret == ProtocolGlobals.G_LOCK_SUCCESS ||
                ret == ProtocolGlobals.G_LOCK_TIMEOUT ||
                ret == ProtocolGlobals.G_LOCK_FAILURE) {
                return ret;
            }

            i++;
            if (i == ProtocolGlobals.G_LOCK_MAX_ATTEMPTS)
                break;

            // ret ==  ProtocolGlobals.G_LOCK_BACKOFF - Try to lock again after
            // binary exponential backoff.
            b = b * 2;
            int sleep = random.nextInt(b);
            if (sleep == 0) {
                sleep =  1;
            }
            try {
                Thread.sleep(sleep * 1000L);
            }
            catch (Exception e) {
                /* ignore */
            }
        }

        logger.log(Logger.WARNING, br.W_MBUS_LOCK_ABORTED, resId);

        return  ProtocolGlobals.G_LOCK_FAILURE;
    }

    /**
     * Attempts to lock a resource using election protocol.
     * @return G_LOCK_SUCCESS, G_LOCK_FAILURE, 
     *         G_LOCK_BACKOFF, G_LOCK_TIMEOUT
     */
    private int tryLockResource(String resId, long timestamp,
        Object owner, boolean shared, boolean failOntimeout) {
        if (DEBUG || DEBUG_CLUSTER_ALL ||
            ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO, "RaptorProtocol.tryLockResource("+
                resId+", timestamp="+timestamp+", owner="+owner+
                ", shared="+shared+", failOnTimeout="+failOntimeout+")");
        }
        
        if (fi.FAULT_INJECTION) {
            if (resId.startsWith(ClusterBroadcast.CLIENTID_EXCLUSIVE_LOCK_PREFIX)) {
                HashMap fp = new HashMap();
                String tmp = resId.replace(ClusterBroadcast.CLIENTID_EXCLUSIVE_LOCK_PREFIX, "");
                fp.put(FaultInjection.CLUSTER_LOCK_RESOURCE_ID_PROP, tmp);
                logger.log(logger.INFO, "RaptorProtocol.tryLockResource("+resId+"), checkFault for "+fp);
                synchronized(fi) {
                    if (fi.checkFault(fi.FAULT_CLUSTER_LOCK_TIMEOUT, fp)) {
                        fi.unsetFault(fi.FAULT_CLUSTER_LOCK_TIMEOUT);
                        return ProtocolGlobals.G_LOCK_TIMEOUT; 
                    }
                }
            }
        }

        Resource res;

        synchronized(resTable) {
            res = (Resource) resTable.get(resId);
            if (res != null) {
                if (shared && res.getShared())
                    return ProtocolGlobals.G_LOCK_SUCCESS;
                else
                    return ProtocolGlobals.G_LOCK_FAILURE;
            }

            res = new Resource(resId, c);
            res.setShared(shared);
            res.setOwner(owner);
            if (timestamp != 0)
                res.setTimestamp(timestamp);
            resTable.put(resId, res);
        }

        res.setLockState(ProtocolGlobals.G_RESOURCE_LOCKING);

        while (true) {
            if (DEBUG || DEBUG_CLUSTER_ALL ||
                ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
                logger.log(Logger.INFO,
                    "MessageBus: Trying to lock resource {0}", resId);
            }

            int status = 0;
            try {
                sendLockRequest(res);
            } catch (HandshakeInProgressException e) {
                synchronized (resTable) {
                    resTable.remove(resId);
                }
                return ProtocolGlobals.G_LOCK_BACKOFF;
            }
            status = res.waitForStatusChange(
                ProtocolGlobals.getLockTimeout(), failOntimeout);

            if (DEBUG || DEBUG_CLUSTER_ALL ||
                ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
                logger.log(Logger.INFO,
                    "MessageBus: Lock attempt status = {0} for resource {1}",
                    Integer.toString(status),
                    resId);
            }

            switch (status) {
            case  ProtocolGlobals.G_LOCK_SUCCESS:
                res.setLockState(ProtocolGlobals.G_RESOURCE_LOCKED);
                return status;

            case ProtocolGlobals.G_LOCK_BACKOFF:
            case ProtocolGlobals.G_LOCK_FAILURE:
                synchronized (resTable) {
                    resTable.remove(resId);
                }
                return status;

            case  ProtocolGlobals.G_LOCK_TIMEOUT:
                String shows = res.showRecipients(
                    (PingHandler)handlers[ProtocolGlobals.G_PING], c);
                String[] args = { res.getResId(), shows };
                logger.log(Logger.WARNING, br.getKTString(br.W_MBUS_LOCK_TIMEOUT, args));
                synchronized (resTable) {
                    resTable.remove(resId);
                }
                return status;

            case  ProtocolGlobals.G_LOCK_TRY_AGAIN:
                if (DEBUG || DEBUG_CLUSTER_ALL || 
                    ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
                    logger.log(Logger.INFO,
                    "Active brokerlist changed. Restarting Lock election for " + res.getResId());
                }
                break;
            }
        }
    }

    /**
     * Unlocks a resource.
     */
    public void unlockResource(String resId) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(Logger.INFO, "RaptorProtocol.unlockResource("+resId+")");
        }
        synchronized (resTable) {
            Resource res = (Resource) resTable.remove(resId);
            if (res != null)
                res.impliedFailure();
        }
    }

    /**
     * Constructs and sends (broadcast) an ProtocolGlobals.G_LOCK_REQUEST
     * packet.
     */
    private void sendLockRequest(Resource res) 
        throws HandshakeInProgressException {

        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendLockRequest("+res+")");
        }

        c.waitClusterInit();

        long xid = System.currentTimeMillis();
        BrokerAddress[] recipients = getBrokerList();

        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            StringBuffer debugstr = new StringBuffer();
            for (int i = 0; i < recipients.length; i++) {
                debugstr.append("\n\t" + recipients[i]);
            }
            logger.log(Logger.INFO,
                "Sending resource lock request for : " + res.getResId() +
                "\nExpecting responses from : " + debugstr.toString());
        }

        res.prepareLockRequest(recipients, xid);

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_LOCK);

        gp.putProp("I", res.getResId());
        gp.putProp("TS", Long.valueOf(res.getTimestamp()));
        gp.putProp("X" , Long.valueOf(xid));
        gp.putProp("SH", Boolean.valueOf(res.getShared()));

        try {
            Map m = c.broadcastUrgent(gp);
            res.updateRecipients(m); 
        } catch (Exception e) {
            logger.log(Logger.WARNING, br.getKString(br.W_CLUSTER_BROADCAST_FAIL, 
                ProtocolGlobals.getPacketTypeDisplayString(gp.getType())+"["+res.getResId()+"]",
                e.getMessage()), e);
            if (e instanceof HandshakeInProgressException) {
                throw (HandshakeInProgressException)e;
            }
        }
    }

    /**
     * Respond to a lock request sent by another broker.
     */
    public void receiveLockRequest(BrokerAddress sender,
        String resId, long timestamp, long xid, boolean shared) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO, "Received lock request("+resId+ 
            ", timestamp="+timestamp+", xid="+xid+", shared="+shared+") from "+sender);
        }

        int response;
        Resource res = null;
        
        synchronized (resTable) {
            res = (Resource) resTable.get(resId);
        }

        if (res == null || (shared && res.getShared()))
            response = ProtocolGlobals.G_LOCK_SUCCESS;
        else if (res.getLockState() == ProtocolGlobals.G_RESOURCE_LOCKED)
            response = ProtocolGlobals.G_LOCK_FAILURE;
        else {
            if (timestamp < res.getTimestamp()) {
                res.impliedFailure();
                response = ProtocolGlobals.G_LOCK_SUCCESS;
            }
            else if (timestamp > res.getTimestamp())
                response = ProtocolGlobals.G_LOCK_FAILURE;
            else
                response = ProtocolGlobals.G_LOCK_BACKOFF;
        }

        sendLockResponse(sender, resId, xid, response);
    }

    /**
     * Construct and send a ProtocolGlobals.G_LOCK_RESPONSE packet.
     */
    private void sendLockResponse(BrokerAddress to, String resId,
        long xid, int response) {
        if (DEBUG || DEBUG_CLUSTER_ALL || 
            ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO,
                "sendLockResponse("+resId+", xid="+xid+", response="+response+") to "+to);
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_LOCK_REPLY);

        gp.putProp("I", resId);
        gp.putProp("X" , Long.valueOf(xid));
        gp.putProp("S", Integer.valueOf(response));

        try {
            c.unicastUrgent(to, gp);
        }
        catch (Exception e) { 
            logger.log(logger.WARNING, br.getString(
              BrokerResources.W_CLUSTER_SEND_LOCK_RESPONSE_EXCEPTION, resId, to.toString()), e);
        }
    }

    /**
     * Process an ProtocolGlobals.G_LOCK_RESPONSE packet.
     */
    public void receiveLockResponse(BrokerAddress sender,
        String resId, long xid, int response) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(logger.INFO, "Received lock response("+resId+", xid="+xid+
            ", response="+ProtocolGlobals.lockResponseStrings[response]+") from "+sender);
        }

        Resource res = null;
        synchronized (resTable) {
            res = (Resource) resTable.get(resId);
        }

        if (res == null)
            return;

        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            if (res.getXid() == xid) {
                logger.log(logger.INFO, "Found waiting xid for received lock response("+
                    resId+", xid="+xid+", response="+
                    ProtocolGlobals.lockResponseStrings[response]+") from "+sender);
            }
        }
        res.consumeResponse(xid, sender, response);
    }


    public void recordUpdateDestination(Destination d)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.recordUpdateDestination: "+d);
        }

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
        int ret = recordConfigChangeEvent(cdi.getGPacket(ProtocolGlobals.G_UPDATE_DESTINATION, true));
        if (ret != ProtocolGlobals.G_EVENT_LOG_SUCCESS) {
            throw new BrokerException(
            br.getKString(BrokerResources.E_CLUSTER_RECORD_CONFIG_CHANGE_EVENT_FAILED,
            "UPDATE_DESTINATION["+d.getDestinationName()+"]"));
        }
    }

    public void recordRemoveDestination(Destination d)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.recordRemoveDestination");
        }

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
        int ret = recordConfigChangeEvent(cdi.getGPacket(ProtocolGlobals.G_REM_DESTINATION, true));
        if (ret != ProtocolGlobals.G_EVENT_LOG_SUCCESS) {
            throw new BrokerException(
            br.getKString(BrokerResources.E_CLUSTER_RECORD_CONFIG_CHANGE_EVENT_FAILED,
            "REM_DESTINATION["+d.getDestinationName()+"]"), ret);
        }
    }

    private static class EventLogWaiter {
        private int status = ProtocolGlobals.G_EVENT_LOG_FAILURE;
        private String reason = null;
        public EventLogWaiter(int s) {
            this.status = s;
        }
        public synchronized int getStatus() {
            return status;
        }
        public synchronized String getReason() {
            return reason;
        }
        public synchronized void setStatus(int s) {
            status = s;
        }
        public synchronized void setReason(String r) {
            reason = r; 
        }
    }

    /**
     * Log a cluster configuration event.
     */
    private int recordConfigChangeEvent(GPacket event)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.recordConfigChangeEvent");
        }

        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "Event = " + event.toLongString());
        }

        // XXX Optimize the no master broker case : Avoid preparing
        // the 'event' GPacket and coming all the way here just to
        // return back without doing anything...
        BrokerAddress configserv = c.getConfigServer();
        if (configserv == null) {
            return ProtocolGlobals.G_EVENT_LOG_SUCCESS;
        }

        Long xid = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        EventLogWaiter waiter = new EventLogWaiter(ProtocolGlobals.G_EVENT_LOG_WAITING);
        eventLogWaiters.put(xid, waiter); 
        try {

        synchronized (waiter) {
            sendConfigChangeEvent(xid, event);

            while (waiter.getStatus() == ProtocolGlobals.G_EVENT_LOG_WAITING) {
                try {
                    logger.log(Logger.INFO, br.getKString(
                           BrokerResources.I_CLUSTER_WAIT_RECORD_CONFIG_CHANGE_EVENT_REPLY, 
                           "["+Thread.currentThread().toString()+"]"+xid, configserv.toString()));
                    waiter.wait(60000);
                }
                catch (Exception e) {}
            }
            if (waiter.getStatus() == ProtocolGlobals.G_EVENT_LOG_SUCCESS) {
                storeDirtyFlag = true;
            }

            return waiter.getStatus();
        }

        } catch (BrokerException e) {
        eventLogWaiters.remove(xid);
        if (DEBUG) {
        logger.logStack(logger.WARNING,  e.getMessage()+": "+event.toLongString(), e);
        }
        throw e;
        }
    }

    /**
     * Send configuration change event to the config server.
     */
    private void sendConfigChangeEvent(Long eventLogXid,
        GPacket event) throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendConfigChangeEvent. xid = " +
                eventLogXid);
        }

        BrokerAddress configServer = c.getConfigServer();

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_CONFIG_CHANGE_EVENT);
        gp.putProp("X", eventLogXid);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            event.write(bos);
            bos.flush();
        }
        catch (Exception e) {}

        byte[] buf = bos.toByteArray();
        gp.setPayload(ByteBuffer.wrap(buf));
        try {
            // Send the event to the config server.
            c.unicast(configServer, gp);
        }
        catch (Exception e) {
            throw new BrokerException(
                br.getKString(br.X_CFG_SERVER_UNREACHABLE),
                br.X_CFG_SERVER_UNREACHABLE, (Throwable) null,
                Status.FORBIDDEN);
        }
    }

    public void receiveConfigChangeEvent(BrokerAddress sender,
        Long xidProp, byte[] eventData) {
        if (DEBUG) {
            logger.log(logger.INFO, 
            "RaptorProtocol.receiveConfigChangeEvent(xid="+xidProp+") from "+sender);
        }

        int status = ProtocolGlobals.G_EVENT_LOG_SUCCESS;
        String reason = null;
        boolean nologstack = false;
        try {

        setConfigOpInProgressIfNotBlocked();
        try {

            BrokerAddress configServer = c.getConfigServer();
            if (configServer == null) {
                throw new BrokerException("Unexpected: there is no master broker");
            }
            if (!configServer.equals(selfAddress)) {
                String[] args = new String[] { ProtocolGlobals.getPacketTypeDisplayString(
                                               ProtocolGlobals.G_CONFIG_CHANGE_EVENT),
                                               sender.toString(), configServer.toString() };
                throw new BrokerException(br.getKString(
                    br.X_CLUSTER_UNABLE_PROCESS_NOT_MASTER_BROKER, args),
                    Status.PRECONDITION_FAILED);
            }

            ChangeRecord cr = ChangeRecord.makeChangeRecord(eventData);

            if (Globals.nowaitForMasterBroker()) {
                if (cr.getOperation() == ProtocolGlobals.G_REM_DESTINATION) { 
                    if (DestType.isQueue(((DestinationUpdateChangeRecord)cr).getType())) {
                        HashSet  bmas = new HashSet(); 
                        synchronized(brokerList) {
                            Iterator itr = brokerList.keySet().iterator(); 
                            while (itr.hasNext()) {
                                BrokerMQAddress bma = ((BrokerAddress)itr.next()).getMQAddress();
                                bmas.add(bma);
                            }
                        }
                        Iterator itr = Globals.getClusterManager().getConfigBrokers();
                        ClusteredBroker cb = null;
                        while (itr.hasNext()) {
                            cb = (ClusteredBroker)itr.next();
                            if (!bmas.contains(cb.getBrokerURL()) && !cb.isLocalBroker()) {
                                String[] args = { ((DestinationUpdateChangeRecord)cr).getName(),
                                                  sender.toString(),
                                        ServiceRestriction.NO_SYNC_WITH_MASTERBROKER.toString(),
                                                  cb.getBrokerURL().toString() };
                                nologstack = true; 
                                throw new BrokerException(
                                br.getKString(BrokerResources.X_SERVICE_RESTRICTION_DELETE_QUEUE, args));
                            }
                        }
                    }
                }
            }

            if (cr.getOperation() == ProtocolGlobals.G_REM_DURABLE_INTEREST) {
                    inDSubToBrokerMap.remove(cr.getUniqueKey());
            } else if (cr.getOperation() == ProtocolGlobals.G_NEW_INTEREST) {
                Subscription sub = Subscription.findDurableSubscription(
                    ((InterestUpdateChangeRecord)cr).getSubscriptionKey());
                if (sub != null) {
                    inDSubToBrokerMap.remove(cr.getUniqueKey());
                    String emsg = br.getKString(br.I_RECORD_DURA_SUB_EXIST_ALREADY, 
                        "["+cr.getUniqueKey()+"]"+sub.getDSubLongLogString(), sender);
                    logger.log(logger.INFO, emsg);
                    if (sub.getShared() != ((InterestUpdateChangeRecord)cr).getShared() || 
                        sub.getJMSShared() != ((InterestUpdateChangeRecord)cr).getJMSShared()) {
                        throw new BrokerException(emsg);
                    }
                } else {
                    synchronized(inDSubToBrokerMap) {
                        InterestUpdateChangeRecord existcr = (InterestUpdateChangeRecord)
                                                   inDSubToBrokerMap.get(cr.getUniqueKey());
                        if (existcr != null) {
                            Object[] args = { "["+cr.getUniqueKey()+"]", existcr.getBroker(), sender };
                            String emsg = br.getKString(br.I_RECORD_DURA_SUB_CONCURRENT, args)+
                                              "["+existcr.getFlagString()+"]";
                            logger.log(logger.INFO, emsg);
                            if (((InterestUpdateChangeRecord)cr).getShared() != existcr.getShared() ||
                                ((InterestUpdateChangeRecord)cr).getJMSShared() != existcr.getJMSShared()) {
                                throw new BrokerException(emsg);
                            }
                        } else {
                            ((InterestUpdateChangeRecord)cr).setBroker(sender);
                            inDSubToBrokerMap.put(cr.getUniqueKey(), cr);
                        }
                    }
                }
            }
            try {
                store.storeConfigChangeRecord(System.currentTimeMillis(), eventData, false);
            } catch (Exception e) {
                inDSubToBrokerMap.remove(cr.getUniqueKey());
                throw e;
            }
        } finally {
            setConfigOpInProgress(false);
        }

        } catch (Exception e) {
            reason = e.getMessage();
            status = ProtocolGlobals.G_EVENT_LOG_FAILURE;
            if (e instanceof BrokerException) {
                status = ((BrokerException)e).getStatusCode();
            }
            if (nologstack) {
                logger.log(logger.ERROR, e.getMessage(), e); 
            } else {
                logger.logStack(logger.ERROR, e.getMessage(), e); 
            }
        }

        sendConfigChangeEventAck(sender, xidProp, status, reason);
    }

    private void sendConfigChangeEventAck(BrokerAddress to, Long xidProp,
                                          int status, String reason) {
        if (DEBUG) {
            logger.log(logger.INFO,
            "RaptorProtocol.sendConfigChangeEventAck("+to+", "+xidProp+", "+status+", "+reason+")");
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_CONFIG_CHANGE_EVENT_REPLY);

        gp.putProp("X", xidProp);
        gp.putProp("S", Integer.valueOf(status));
        if (reason != null) {
            gp.putProp("reason", reason);
        }
        try {
            c.unicast(to, gp);
        }
        catch (Exception e) { 
            /* Ignore */ 
            logger.log(Logger.DEBUGHIGH,"Unable to unicast message ", e);
        }
    }

    public void receiveConfigChangeEventReply(BrokerAddress sender,
        Long xid, int status, String reason) {
        if (DEBUG) {
            logger.log(logger.INFO, 
            "RaptorProtocol.receiveConfigChangeEventAck("+sender+", "+xid+", "+status+", "+reason+")");
        }

        EventLogWaiter waiter = (EventLogWaiter)eventLogWaiters.remove(xid);
        if (waiter == null) {
            String args[] = {String.valueOf(xid.longValue()), String.valueOf(status)};
            logger.log(logger.WARNING, BrokerResources.W_CONFIG_CHANGEEVENT_NOTFOUND, args);
            return;
        }
        synchronized (waiter) {
            waiter.setStatus(status);
            waiter.setReason(reason);
            waiter.notifyAll();
        }
    }

    private void sendConfigChangesRequest(BrokerAddress to, long timestamp) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendConfigChangesRequest. timestamp = " +
                timestamp+", to "+to);
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST);

        gp.putProp("TS", Long.valueOf(timestamp));

        synchronized (cfgSrvWaitObject) {
            try {
                c.unicast(to, gp);
                cfgSrvRequestCount++;
                cfgSrvRequestErr = false;
            }
            catch (Exception e) {
                cfgSrvRequestCount = 0;
                cfgSrvRequestErr = true;
                cfgSrvWaitObject.notifyAll();
            }
        }
    }

    public void receiveConfigChangesRequest(BrokerAddress sender, long timestamp) {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.receiveConfigChangesRequest. from = " +
                sender+", timestamp="+timestamp);
        }

        String emsg = null;
        ArrayList<ChangeRecordInfo> records = null;

        try {
            setConfigOpInProgressIfNotBlocked();
            try {
                BrokerAddress configServer = c.getConfigServer();
                if (configServer == null) {
                    throw new BrokerException("Unexpected: there is no master broker");
                }
                if (!configServer.equals(selfAddress)) {
                    String[] args = new String[] { ProtocolGlobals.getPacketTypeDisplayString(
                                                   ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST),
                                                   sender.toString(), configServer.toString() };
                    throw new BrokerException(br.getKString(
                        br.X_CLUSTER_UNABLE_PROCESS_NOT_MASTER_BROKER, args));
                }
                records = store.getConfigChangeRecordsSince(timestamp);

            } finally {
                setConfigOpInProgress(false);
            }

        } catch (Exception e) {
            emsg = e.getMessage();
            String[] args = new String[] { ProtocolGlobals.getPacketTypeDisplayString(
                                           ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST),
                                           sender.toString(), e.getMessage() };

            logger.logStack(logger.ERROR, br.getKString(
                            br.E_CLUSTER_PROCESS_PACKET_FROM_BROKER_FAIL, args), e);
        }

        long now = System.currentTimeMillis();
        sendConfigChangesResponse(sender, now, records, emsg);

    }

    private void sendConfigChangesResponse(BrokerAddress to, long currentTime,
                                           ArrayList<ChangeRecordInfo> records,
                                           String emsg) {

        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.sendConfigChangesResponse. to = " + to);
        }

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_GET_CONFIG_CHANGES_REPLY);
        gp.putProp("TS", Long.valueOf(currentTime));

        if (records == null)  {
            gp.putProp("S", Integer.valueOf(Status.ERROR));
            if (emsg != null) {
                gp.putProp("reason", emsg);
            }
        } else {
            gp.putProp("S", Integer.valueOf(ProtocolGlobals.G_SUCCESS));
            gp.putProp("C", Integer.valueOf(records.size()));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            try {
                for (int i = 0; i < records.size(); i++) {
                     byte[] rec = records.get(i).getRecord();
                     bos.write(rec, 0, rec.length);
                }

                bos.flush();

            } catch (Exception e) { /* Ignore */ }

            byte[] buf = bos.toByteArray();
            gp.setPayload(ByteBuffer.wrap(buf));
        }

        try {
            c.unicast(to, gp);
        }
        catch (Exception e) { /* Ignore */ 
            logger.log(Logger.DEBUGHIGH,"Exception in unicast ", e);
        }
    }

    public void receiveConfigChangesReply(BrokerAddress sender,
        long refreshTime, int count, byte[] buf, String emsg) {
                            
        logger.log(logger.INFO, br.getKString(br.I_CLUSTER_RECEIVED_CHANGE_RECORDS_FROM,
                                "("+count+", "+refreshTime+")", sender.toString()));
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.receiveConfigChangesResponse from "+
                                     sender+", record count="+count);
        }

        if (emsg != null) {
            String[] args = new String[] { emsg, 
                                  ProtocolGlobals.getPacketTypeDisplayString(
                                  ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST),
                                  sender.toString() };
            String logmsg = br.getKString(br.E_CLUSTER_RECEIVED_ERROR_REPLY_FROM_BROKER, args);
            logger.log(logger.ERROR, logmsg);
            logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_FORCE_CLOSE_LINK, sender, logmsg));
            c.closeLink(sender, true);
        }

        synchronized(newMasterBrokerLock) {
            BrokerAddress configServer = null;
            try {
                configServer = c.getConfigServer(); 
            } catch (Exception e) {
                logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_FORCE_CLOSE_LINK,
                    sender, br.getString(br.M_MASTER_BROKER_NOT_CONNECTED)+
                    "["+e.getMessage()+"]"));
                c.closeLink(sender, true);
            }
            if (!configServer.getMQAddress().equals(sender.getMQAddress())) {
                logger.log(logger.WARNING, br.getKString(br.W_CLUSTER_FORCE_CLOSE_LINK,
                    sender, br.getString(br.M_MASTER_BROKER_CHANGED)+"["+sender+", "+configServer+"]"));
                c.closeLink(sender, true);
            }

        //
        // First, check if we are talking to the same config server
        // as last time. Usually this is true, except in following
        // scenarios -
        //
        // 1. The administrator has changed the config server for
        // this cluster, typically using backup and restore. In this
        // case the new config server will ensure that all the brokers
        // will get the ProtocolGlobals.G_RESET_PERSISTENCE command,
        // so there is no problem.
        //
        // 2. When a broker is moved from one cluster to another.
        // In this case, we cannot allow the broker to join in
        // with the old destinations / durable state information...
        //

        boolean resetRequired = false;
        BrokerAddress lastConfigServer = getLastConfigServer();
        if (lastConfigServer != null &&
            ! lastConfigServer.equals(sender)) {
            resetRequired = true;

            if (count == 0) {
                logger.log(Logger.ERROR, br.E_MBUS_CLUSTER_JOIN_ERROR);

                // Since all the client connections were blocked,
                // it should be safe to terminate the VM here.
                Broker.getBroker().exit(1,
                   br.getString(br.E_MBUS_CLUSTER_JOIN_ERROR),
                   BrokerEvent.Type.FATAL_ERROR);
            }
        }

        DataInputStream dis = null;

        if (buf != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(buf);
            dis = new DataInputStream(bis);
        }

        try {
            boolean resetFlag = false;
            ArrayList l = null;

            for (int i = 0; i < count; i++) {
                GPacket gp = GPacket.getInstance();
                gp.read(dis);

                if (i == 0) {
                    if (gp.getType() == ProtocolGlobals.G_RESET_PERSISTENCE)
                    {
                        resetFlag = true;
                        l = new ArrayList();
                    }

                    if (resetRequired && resetFlag == false) {
                        logger.log(Logger.ERROR,
                            br.E_MBUS_CLUSTER_JOIN_ERROR);

                        // Since all the client connections were blocked,
                        // it should be safe to terminate the VM here.
                        Broker.getBroker().exit(1,
                           br.getString(br.E_MBUS_CLUSTER_JOIN_ERROR),
                           BrokerEvent.Type.FATAL_ERROR);
                    }
                }

                if (resetFlag) {
                    l.add(gp);
                }
                else {
                    receiveBroadcast(sender, gp);
                }
            }

            if (resetFlag)
                applyPersistentStateChanges(sender, l);


            if (configSyncComplete == false) {
                configSyncComplete = true;
                // So far, this broker was not capable of accepting
                // any interest updates (because it's destination list
                // was potentially stale) and hence receiveInterestUpdate
                // was just throwing them away. However note that such
                // interest updates can only originate from the master
                // broker itself, because before this point we were not
                // even talking to any other brokers...
                //
                // So - This now is the time to ask the master broker
                // to send it's full interest udpate again!!!
                //
                sendGetInterestUpdate(sender);

                cbDispatcher.configSyncComplete(); // Do this only once.
                logger.log(Logger.FORCE, br.I_MBUS_SYNC_COMPLETE);
            }

            synchronized (cfgSrvWaitObject) {
                cfgSrvRequestCount--;
                if (cfgSrvRequestCount == 0) {
                    cfgSrvWaitObject.notifyAll();
                }
            }

            storeLastRefreshTime(refreshTime -
                ProtocolGlobals.G_EVENT_LOG_CLOCK_SKEW_TOLERANCE);
            storeLastConfigServer(sender);

        }
        catch (Exception e) {
            logger.logStack(Logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Error while applying the config changes...",
                    e);
            return;
        }

        }
    }

    /**
     * Wait for config server's response to the 
     * ProtocolGlobals.G_GET_CONFIG_CHANGES_REQUEST.
     *
     * @return true if response received and consumed successfully,
     * false if config server failure.
     */
    private boolean waitConfigChangesResponse() {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.waitConfigChangesResponse.");
        }

        synchronized (cfgSrvWaitObject) {
            while (cfgSrvRequestCount > 0) {
                try {
                    cfgSrvWaitObject.wait();
                }
                catch (Exception e) {}
            }

            if (DEBUG) {
                logger.log(logger.INFO,
                    "RaptorProtocol.waitConfigChangesResponse returning :"
                    + (!cfgSrvRequestErr));
            }

            return (! cfgSrvRequestErr);
        }
    }

    public void applyPersistentStateChanges(BrokerAddress sender,
        ArrayList list) throws Exception {
        if (DEBUG) {
            logger.log(logger.INFO,
                "RaptorProtocol.applyPersistentStateChanges.");
        }

        HashMap oldInts = new HashMap();
        HashMap oldDests = new HashMap();

        Set ints = Subscription.getAllSubscriptions(null);
        Iterator itr = ints.iterator();
        while (itr.hasNext()) {
            Subscription sub = (Subscription)itr.next();
            com.sun.messaging.jmq.jmsserver.core.ConsumerUID intid =
                sub.getConsumerUID();
            String key = Subscription.getDSubKey(sub.getClientID(), sub.getDurableName());
            oldInts.put(key, intid);
        }

        Iterator[] itrs = DL.getAllDestinations(null); 
        itr = itrs[0];
        while (itr.hasNext()) {
            Destination d = (Destination) itr.next();
            if (d.isAutoCreated() || d.isInternal() || d.isTemporary() || d.isDMQ())
                continue;

            oldDests.put(d.getDestinationUID(), d);
        }

        for (int i = 0; i < list.size(); i++) {
            GPacket gp = (GPacket) list.get(i);
            if (gp.getType() == ProtocolGlobals.G_RESET_PERSISTENCE) {
                if (DEBUG) {
                    logger.log(logger.INFO,
                        "applyPersistentStateChanges : RESET_PERSISTENCE");
                }
                continue;
            }

            if (gp.getType() == ProtocolGlobals.G_NEW_INTEREST ||
                gp.getType() == ProtocolGlobals.G_REM_DURABLE_INTEREST) {
                handlers[gp.getType()].handle(cb, sender, gp);
                ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(gp);
                String key = Subscription.getDSubKey(csi.getClientID(), csi.getDurableName());
                oldInts.remove(key);

                if (DEBUG) {
                    if (gp.getType() == ProtocolGlobals.G_NEW_INTEREST) {
                        logger.log(logger.INFO, "Added subscription : " + key);
                    }
                    else {
                        logger.log(logger.INFO, "Remove subscription : " + key);
                    }
                }

            }
            else if (gp.getType() == ProtocolGlobals.G_UPDATE_DESTINATION ||
                gp.getType() == ProtocolGlobals.G_REM_DESTINATION) {
                ClusterDestInfo cdi = ClusterDestInfo.newInstance(gp);
                String dname = cdi.getDestName();
                int dtype = cdi.getDestType();

                Object key = DestinationUID.getUID(dname, DestType.isQueue(dtype));

                Destination old = (Destination) oldDests.get(key);

                if (gp.getType() == ProtocolGlobals.G_UPDATE_DESTINATION) {
                    if (old != null && old.getType() != dtype) {
                        // The new destination has same name and
                        // domain BUT the type does not match. e.g -
                        // old : (name = "foo", domain = Q, type=single)
                        // new : (name = "foo", domain = Q, type=failover)
                        //
                        // Hence, remove the old destination...
                        //
                        // XXX Not sure if this can ever happen with
                        // MQ3.5 ...

                        DestinationUID duid = DestinationUID.getUID(
                                                  old.getDestinationName(),
                                                  DestType.isQueue(old.getType()));
                        cb.notifyDestroyDestination(duid);
                    }
                }

                handlers[gp.getType()].handle(cb, sender, gp);
                oldDests.remove(key);

                if (DEBUG) {
                    if (gp.getType() == ProtocolGlobals.G_UPDATE_DESTINATION) {
                        logger.log(logger.INFO, "Updated destination : " + key);
                    }
                    else {
                        logger.log(logger.INFO, "Removed destination : " + key);
                    }
                }
            }
        }

        // Now walk through the "remaining" entries in oldInts and
        // oldDests. These objects should be removed because master
        // broker does not know about them!
        for (itr = oldInts.values().iterator(); itr.hasNext();) {
            ConsumerUID intid = (ConsumerUID) itr.next();
            Consumer c = Consumer.getConsumer(intid);
            if (c != null) {
                cb.interestRemoved(c, null, false);
                if (c instanceof Subscription) {
                    cb.unsubscribe((Subscription)c);
                }
                if (DEBUG) {
                    logger.log(logger.INFO, "Removed stale subscription : " + c);
                }
            }
        }

        for (itr = oldDests.keySet().iterator(); itr.hasNext();) {
            DestinationUID duid = (DestinationUID) itr.next();
            cb.notifyDestroyDestination(duid);

            if (DEBUG) {
                logger.log(logger.INFO, "Removed stale destination : " + duid);
            }
        }
    }


    public void sendNewDestination(Destination d) 
        throws BrokerException 
    {
        if (DEBUG)
            logger.log(Logger.DEBUG,"Sending New Destination " + d);

        sendUpdateDestination(null, d);
    }

    public void sendUpdateDestination(Destination d)
        throws BrokerException 
    {
        sendUpdateDestination(null, d);
    }

    private void sendUpdateDestination(BrokerAddress to, Destination d)
        throws BrokerException 
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG,"Sending Update Destination " +d+(to == null ? "":" to "+to));
        }

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
        try {
            if (to == null) {
                c.broadcast(cdi.getGPacket(ProtocolGlobals.G_UPDATE_DESTINATION, false));
            } else {
                c.unicast(to, cdi.getGPacket(ProtocolGlobals.G_UPDATE_DESTINATION, false));
            }
        }
        catch (IOException e) { /* Ignore */ }
    }

    public void sendRemovedDestination(Destination d)
        throws BrokerException 
    {
        if (DEBUG)
            logger.log(Logger.DEBUG,"Sending New Destination " + d);

        ClusterDestInfo cdi = ClusterDestInfo.newInstance(d); 
        try {
            c.broadcast(cdi.getGPacket(ProtocolGlobals.G_REM_DESTINATION, false));
        }
        catch (IOException e) { /* Ignore */ }

    }

    public void recordCreateSubscription(Subscription sub)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.recordCreateSubscription");
        }

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
        int ret = recordConfigChangeEvent(csi.getGPacket(ProtocolGlobals.G_NEW_INTEREST, true));
        if (ret != ProtocolGlobals.G_EVENT_LOG_SUCCESS) {
            throw new BrokerException(br.getKString(
                br.E_CLUSTER_RECORD_CONFIG_CHANGE_EVENT_FAILED,
                "NEW_INTEREST"+Subscription.getDSubLogString(
                    sub.getClientID(), sub.getDurableName())), ret);
        }
    }

    public void recordUnsubscribe(Subscription sub)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "RaptorProtocol.recordUnsubscribe");
        }

        ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
        int ret = recordConfigChangeEvent(csi.getGPacket(ProtocolGlobals.G_REM_DURABLE_INTEREST, true));
        if (ret != ProtocolGlobals.G_EVENT_LOG_SUCCESS) {
            throw new BrokerException(br.getKString(
                br.E_CLUSTER_RECORD_CONFIG_CHANGE_EVENT_FAILED,
                "REMOVE_DURABLE"+Subscription.getDSubLogString(
                    sub.getClientID(), sub.getDurableName())));
        }
    }

    public void sendNewSubscription(Subscription sub, Consumer cons,
        boolean active) throws BrokerException {
        if (DEBUG)
            logger.log(Logger.INFO, "RaptorProtocol.sendNewSubscription");

        sendNewInterestUpdate(sub);
        sendAttachDurable(null, sub, cons);

        if (active) {

            sendInterestUpdate(cons, ProtocolGlobals.G_NEW_PRIMARY_INTEREST);
        }
    }

    public void sendNewConsumer(Consumer c, boolean active)
        throws BrokerException 
    {
        if (DEBUG)
            logger.log(Logger.DEBUG, "RaptorProtocol.sendNewConsumer");

        sendNewInterestUpdate(c);

        if (active) {
            sendInterestUpdate(c, ProtocolGlobals.G_NEW_PRIMARY_INTEREST);
        }
    }

    public void sendRemovedConsumer(Consumer c, Map pendingMsgs, boolean cleanup) 
        throws BrokerException 
    {
        if (DEBUG) {
            logger.log(Logger.INFO, "RaptorProtocol.sendRemovedConsumer("+
                       c+", pending="+pendingMsgs+", cleanup="+cleanup+")");
        }
        unlockOwnedResources(c.getConsumerUID());

        if (c instanceof Subscription) {
            sendRemDurableUpdate(c);
        } else if (c.getSubscription() != null) { // detatching
            sendInterestUpdate(c, ProtocolGlobals.G_DURABLE_DETACH, pendingMsgs, cleanup);
        } else {
            sendInterestUpdate(c, ProtocolGlobals.G_REM_INTEREST, pendingMsgs, cleanup);
        }
    }


    private void sendGetInterestUpdate(BrokerAddress broker) {
        if (DEBUG)
            logger.log(Logger.DEBUG, "RaptorProtocol.sendGetInterestUpdate");

        GPacket gp = GPacket.getInstance();
        gp.setType(ProtocolGlobals.G_GET_INTEREST_UPDATE);

        try {
            c.unicast(broker, gp);
        }
        catch (IOException e) { /* Ignore */ }
    }

    public void receiveGetInterestUpdate(BrokerAddress sender) {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "MessageBus: Received request for a full interest " +
                "update from {0}.", sender);
        }

        forwardLocalInterests(sender);
    }


    /**
     * Add a new broker to the list of known brokers in this cluster.
     */
    public synchronized int addBrokerInfo(BrokerInfo brokerInfo) {
        if (DEBUG) {
            logger.log(Logger.INFO, "RaptorProtocol.addBrokerInfo("+brokerInfo+")");
        }

        if (brokerInfo.getClusterProtocolVersion() == null) {
            /* This is a 3.5 cluster (i.e. either there is no
             * master broker, or the master broker is 3.5. Hence
             * don't let old brokers in...
             */
            logger.log(Logger.WARNING, br.W_MBUS_REJECT_OLD, brokerInfo.getBrokerAddr());
            return ADD_BROKER_INFO_BAN;
        }
        if (brokerInfo.getClusterProtocolVersion().intValue() == ProtocolGlobals.VERSION_400) {
            logger.log(Logger.WARNING, "XXXI18N - Reject no cluster support broker version 400:"+
                       brokerInfo.getBrokerAddr());
            return ADD_BROKER_INFO_BAN;
        }
        if (brokerInfo.getClusterProtocolVersion().intValue() >= ProtocolGlobals.VERSION_400) {
            try {
            checkUIDPrefixClash(brokerInfo);
            } catch (Exception e) {
            return ADD_BROKER_INFO_BAN;
            }
        }
        if (brokerInfo.getClusterProtocolVersion().intValue() < ProtocolGlobals.VERSION_400 &&
            Globals.getHAEnabled()) {
            logger.log(Logger.WARNING, br.getKString(
                   br.W_CLUSTER_REJECT_LESS_400VERSION, brokerInfo.getBrokerAddr()));
            return ADD_BROKER_INFO_BAN;
        }
        if (brokerInfo.getBrokerAddr().getHAEnabled() != Globals.getHAEnabled()) {
            logger.log(Logger.WARNING, "HA mode not match with remote broker "
                                       + brokerInfo.getBrokerAddr()); 
            return ADD_BROKER_INFO_BAN;
        }
        if (Globals.getBDBREPEnabled()) {
            BrokerAddress r =  brokerInfo.getBrokerAddr();
            if (r.getInstanceName().equals(Globals.getConfigName())) {
                logger.log(Logger.WARNING, br.getKString(
                    br.E_DUPLICATE_INSTNAME_WITH_THIS_BROKER,
                    r.getInstanceName(), r));
                return ADD_BROKER_INFO_BAN;
            }
        }

        BrokerAddress configServer = null;
        try {
            configServer = c.getConfigServer();
        } catch (Exception e) {
            logger.log(Logger.WARNING, 
                       "Master broker not resolved yet, ask remote broker "+
                        brokerInfo.getBrokerAddr()+" retry later");
            return ADD_BROKER_INFO_RETRY;
        }

        synchronized (brokerList) {
            if (shutdown) {
                return ADD_BROKER_INFO_BAN;
            }
            if (isTakeoverTarget(brokerInfo.getBrokerAddr())) {
                logger.log(logger.WARNING, br.getKString(
                       br.W_CLUSTER_REJECT_TAKINGOVER_TARGET, brokerInfo));
                forwardTakeoverBrokers(brokerInfo.getBrokerAddr(), false);
                return ADD_BROKER_INFO_RETRY;
            }

            Object old = brokerList.get(brokerInfo.getBrokerAddr());
            if (old != null) { 
                logger.log(logger.WARNING,  br.getKString(
                       br.W_CLUSTER_REJECT_EXISTING_SAME, brokerInfo));
                return ADD_BROKER_INFO_RETRY; //XXX 
            }
            BrokerInfoEx brokerInfoEx = new BrokerInfoEx(brokerInfo);
            brokerList.put(brokerInfo.getBrokerAddr(), brokerInfoEx);
            if (DEBUG) {
                logger.log(logger.INFO, 
                    "RaptorProtocol.addBrokerInfo(): added BrokerInfoEx@"+
                     brokerInfoEx.hashCode()+" for broker "+brokerInfo.getBrokerAddr());
            }
        }

        /* not used anymore 
        try {
            DL.remoteCheckBrokerDown(brokerInfo.getBrokerAddr(), true);
        } catch (Exception e) {
            logger.logStack(Logger.WARNING, 
            "Destination remote check failed before establish connection with broker "+brokerInfo.getBrokerAddr(), e); 
        }
        */

        if (configSyncComplete == false) {
            try {
                if (configServer != null &&
                    configServer.equals(brokerInfo.getBrokerAddr())) {
                    // This is the config server! Initiate config sync.

                    long timestamp = -1L;
                    timestamp = getLastRefreshTime();
                    BrokerAddress lastConfigServer = getLastConfigServer();
                    if (lastConfigServer == null || 
                        !lastConfigServer.equals(configServer)) {
                        timestamp = -1L;
                    }

                    sendConfigChangesRequest(configServer, timestamp);

                    if (DEBUG_CLUSTER_ALL || DEBUG_CLUSTER_CONN) {
                        logger.log(Logger.INFO,
                            br.I_MBUS_SYNC_INIT);
                    }
                }
                else {
                    if (DEBUG) {
                        logger.log(Logger.DEBUG,
                            "Config sync not complete. " +
                            "Rejecting addBrokerInfo : " +
                            brokerInfo);
                    }

                    // This is not the config server. Cannot
                    // accept connections from this broker at this
                    // stage...
                    brokerList.remove(brokerInfo.getBrokerAddr());
                    return ADD_BROKER_INFO_RETRY;
                }
            }
            catch (Exception e) {}
        }

            if (DEBUG) {
                logger.log(Logger.DEBUG,
                    "MessageBus: New Broker : {0}",
                    brokerInfo);
            }

            // If the new broker is not the config server AND
            // it has added some destinations / durables recently,
            // make sure that this broker knows about it.
            // Why? Consider the following sequence of events -
            //
            // 1. A syncs with the config server.
            // 2. B adds a destination and sends a broadcast.
            // 3. Five seconds later A connects with B...
            // At this point A must check with the config server again
            // to see if anything has changed in last five seconds...

            try {
                if (brokerInfo.getStoreDirtyFlag() &&
                    configServer != null &&
                    ! configServer.equals(brokerInfo.getBrokerAddr())) {

                    long timestamp = -1;
                    timestamp = getLastRefreshTime();
                    sendConfigChangesRequest(configServer, timestamp);

                    // The broker I/O thread blocks here...
                    if (waitConfigChangesResponse() == false) {
                        brokerList.remove(brokerInfo.getBrokerAddr());
                        return ADD_BROKER_INFO_BAN;
                    }
                }
            }
            catch (Exception e) {}

            try {

            if (brokerInfo.getBrokerAddr().getHAEnabled()) { 
                Globals.getClusterManager().activateBroker(
                       brokerInfo.getBrokerAddr().getBrokerID(),
                       brokerInfo.getBrokerAddr().getBrokerSessionUID(), 
                       brokerInfo.getBrokerAddr().getInstanceName(), brokerInfo);
            } else {
                Globals.getClusterManager().activateBroker(
                       brokerInfo.getBrokerAddr().getMQAddress(), 
                       brokerInfo.getBrokerAddr().getBrokerSessionUID(),
                       brokerInfo.getBrokerAddr().getInstanceName(), brokerInfo);
            }
            } catch (Exception e) {
               logger.logStack(Logger.ERROR, br.getKString(
                      br.W_CLUSTER_ACTIVATE_BROKER_FAILED, brokerInfo, e.getMessage()), e);
               brokerList.remove(brokerInfo.getBrokerAddr());
               return ADD_BROKER_INFO_RETRY;
            }

            logger.log(Logger.INFO, br.getKString(br.I_CLUSTER_ACTIVATED_BROKER, brokerInfo));

            if (Globals.getHAEnabled()) {
                forwardTakeoverBrokers(brokerInfo.getBrokerAddr(), true); 
            }

            if (flowStopped) {
                sendFlowControlUpdate(brokerInfo.getBrokerAddr());
            }

            forwardLocalInterests(brokerInfo.getBrokerAddr());
            sendTransactionInquiries(brokerInfo.getBrokerAddr(), null);
            restartElections(brokerInfo.getBrokerAddr());
            if (Globals.getBDBREPEnabled()) {
                sendMyReplicationGroupInfo(brokerInfo.getBrokerAddr());
            }
            logger.log(Logger.FORCE, br.I_MBUS_ADD_BROKER, 
                       brokerInfo.getBrokerAddr().toString()+
                       (brokerInfo.getRealRemoteString() == null ? "":
                        "["+brokerInfo.getRealRemoteString()+"]"));

        return ADD_BROKER_INFO_OK;

    }

    private void forwardLocalInterests(BrokerAddress broker) {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "RaptorProtocol.forwardLocalInterests to : " + broker);
        }

        // BugID : 4451545
        // Advertize my (local) temporary destinations.
        // Note - temporary destinations are not recorded by
        // the master broker.

        Iterator[] itrs = DL.getTempDestinations(null, selfAddress);
        Iterator itr = itrs[0]; //PART
        while (itr.hasNext()) {
            Destination d = (Destination) itr.next();
            try {
            sendUpdateDestination(broker, d);
            } catch (BrokerException e) {
            logger.logStack(logger.INFO, e.getMessage(), e);
            }
        }

        // Advertize my local interests only to the new guy.
        Set localActiveInterests = new HashSet();
        Set primaryInterests = new HashSet();
        Set attachedSubs = new HashSet();
        Set subscriptions = new HashSet();

        itr = Consumer.getAllConsumers(false);
        while (itr.hasNext()) {
            Consumer c = (Consumer) itr.next();
            Destination d = c.getFirstDestination();

            // fix for 6229572
            if (d != null && d.getIsLocal()) {
                // dont forward local consumers
                continue;
            }
            if (!c.isValid()) {
                logger.log(Logger.INFO, br.getKString(
                       br.I_CLUSTER_SKIP_FORWARDING_CLOSED_CONSUMER, c.toString()));
                continue;
            }
            if (! (c instanceof Subscription)) { // active consumer
                ConsumerUID uid = c.getConsumerUID();
                if (selfAddress == uid.getBrokerAddress()) { // local
                    if (c.getSubscription() != null) {
                        // either durable or shared non-durable interest
                        // bug 6214036
                        Subscription s = c.getSubscription();
                        attachedSubs.add(c);

                        // forward subscription info for non-durables
                        // see bug 6208621
                        if (!c.getSubscription().isDurable())
                          subscriptions.add(s);

                        // if we are durable but shared, we also need
                        // to forward the information to correctly set
                        // the shared property
                        // it doesnt hurt in the non-shared case so
                        // we do it anyway
                        else
                          subscriptions.add(s);

                    } else {
                        localActiveInterests.add(c);
                    }
 
                }
                if (c.getIsActiveConsumer()) { // indicates active queue
                    if (d != null && d.getMaxActiveConsumers() == 1) {
                        // primary SQR or primary on failover
                        primaryInterests.add(c);
                    }
                }
                   
            }
        }

        // deal with either durables or shared non-durable
        // subscriptions
        if (!attachedSubs.isEmpty()) {
            // send over subscriptions for non-durable
            // we dont worry about durable subscriptions
            // because they "should" have been handled by
            // the masterbroker or are already there
            if (DEBUG) {
            logger.log(logger.INFO, 
            "forward local subscriptions "+subscriptions.size()+" to "+broker);
            }
            sendNewInterestUpdate(broker, subscriptions);
            // send over attaching consumers
            itr = attachedSubs.iterator();
            while (itr.hasNext()) {
                Consumer cons = (Consumer)itr.next();
                Subscription sub = cons.getSubscription();
                if (DEBUG) {
                logger.log(logger.INFO, 
                "forward local attached consumer  "+cons+" to "+broker);
                }
                sendAttachDurable(broker, sub, cons);
            }
        }
        // now send over all other consumers
        if (!localActiveInterests.isEmpty()) {
            if (DEBUG) {
            logger.log(logger.INFO, 
            "forward local activeInterest "+localActiveInterests.size()+" to "+broker);
            }
            sendNewInterestUpdate(broker, localActiveInterests);
        }
        // finally send over notification that we have a new primary
        // for SQR queues
        if (!primaryInterests.isEmpty()) {
            sendInterestUpdate(broker, primaryInterests,  
                    ProtocolGlobals.G_NEW_PRIMARY_INTEREST);
        }
    }

    private void restartElections(BrokerAddress broker) {
        if (DEBUG || ClusterManagerImpl.isDEBUG_CLUSTER_LOCK()) {
            logger.log(Logger.INFO,
                "RaptorProtocol.restartElections("+broker+")");
        }

        // The new broker should participate in all the ongoing
        // lockResource() elections...
        synchronized(resTable) {
            Collection entries = resTable.keySet();
            Iterator itr = entries.iterator();
            while (itr.hasNext()) {
                String resId = (String) itr.next();
                Resource res = (Resource) resTable.get(resId);
                res.brokerAdded(broker);
            }
        }
    }

    public int getClusterAckWaitTimeout() {
        return ProtocolGlobals.getAckTimeout();
    }

    /**
     * Caller must ensure broker is the transaction home broker
     */
    public void sendTransactionInquiry(TransactionUID tid, BrokerAddress broker) {
        TransactionBroker tb = new TransactionBroker(broker);
        BrokerAddress to = tb.getCurrentBrokerAddress();
        ClusterTxnInquiryInfo cii = ClusterTxnInquiryInfo.newInstance(
                              Long.valueOf(tid.longValue()), to, null);
        if (DEBUG_CLUSTER_TXN) {
            logger.log(Logger.INFO, "Sending transaction inquiry: "+cii+" to "+to+"["+broker+"]");
        }
        try {
             c.unicast(to, cii.getGPacket());
        } catch (Exception e) {
             logger.log(Logger.WARNING, 
                 "Sending transaction inquiry " + cii+" to " +to+"["+broker+"] failed");
        }
    }

    private void sendTransactionInquiries(
        BrokerAddress broker, UID partitionID) {
        sendPreparedTransactionInquiries(null, broker, partitionID);
    }

    public void sendPreparedTransactionInquiries(
        List<TransactionUID> tidsonly, BrokerAddress broker) {
        sendPreparedTransactionInquiries(tidsonly, broker, null);
    }
     
    private void sendPreparedTransactionInquiries(
        List<TransactionUID> tidsonly, BrokerAddress broker,
        UID partitionID) {

        TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = null;
        ArrayList tids = null;
        TransactionUID tid = null;
        ClusterTxnInquiryInfo cii = null;
        TransactionBroker txnhome = null;
        UID txnhomess = null;
        for (int i = 0; i < tls.length; i++) {
            tl = tls[i];
            if (tl == null) {
                continue;
            }
            tids = tl.getPreparedRemoteTransactions(null);
            Iterator itr = tids.iterator();
            while ( itr.hasNext()) {
                tid = (TransactionUID)itr.next();
                if (tidsonly != null && !tidsonly.contains(tid)) {
                    continue;
                }
                txnhome = tl.getRemoteTransactionHomeBroker(tid); 
                if (broker == null && txnhome == null) {
                    continue;
                }
                txnhomess = null;
                if (txnhome != null) {
                    txnhomess = txnhome.getBrokerAddress().getStoreSessionUID();
                }
                if (partitionID == null || txnhomess == null || 
                    txnhomess.equals(partitionID)) {
                    cii = ClusterTxnInquiryInfo.newInstance(Long.valueOf(tid.longValue()),
                          ((txnhome == null) ? null:txnhome.getCurrentBrokerAddress()), null);
                    if (DEBUG_CLUSTER_TXN) {
                        logger.log(Logger.INFO, "Sending transaction inquiry: " + cii + " to "+broker);
                    }
                    BrokerAddress tobroker = (broker == null ? 
                                              txnhome.getCurrentBrokerAddress():broker);
                    try {
                        c.unicast(tobroker, cii.getGPacket());
                    } catch (Exception e) {
                        logger.log(Logger.WARNING, 
                        "Sending transaction inquiry " + cii+" to " +tobroker +" failed");
                    }
                }
            }
        }
    }

    public void receivedTransactionInquiry(GPacket pkt, BrokerAddress from) {
        ClusterTxnInquiryInfo cii = ClusterTxnInquiryInfo.newInstance(pkt);

        if (DEBUG_CLUSTER_TXN) {
        logger.log(logger.INFO, "Received transaction inquiry "+cii.toString()+ " from "+from);
        }

        TransactionUID tid = new TransactionUID(cii.getTransactionID().longValue());
        BrokerAddress txnHomeBroker = cii.getTransactionHome();
        TransactionBroker thb = null;
        if (txnHomeBroker != null) {
            thb = new TransactionBroker(txnHomeBroker);
        }

        Object[] oo = TransactionList.getTransListAndState(tid, null, true, false);
        TransactionState ts = null; 
        if (oo != null) {
            //tl = (TransactionList)oo[0];
            ts = (TransactionState)oo[1];
        }
        if (ts == null && DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "Transaction "+ tid+ " not found in local transactions");
        }
        if (ts != null) {
            BrokerAddress currba = (thb == null ? null:thb.getCurrentBrokerAddress());
            if (currba != null && !currba.equals(Globals.getMyAddress())) {
                logger.log(logger.INFO,
                "Transaction "+tid+" home broker current address "+
                 currba+", old address "+txnHomeBroker+ " inquired from "+ from);
            }
            sendClusterTransactionInfo(tid, from, cii.getXid());
            return;
        }
        if (thb != null) { 
            BrokerAddress currba = thb.getCurrentBrokerAddress();
            if (currba != null && Globals.getMyAddress().equals(currba)) {
                sendClusterTransactionInfo(tid, from, cii.getXid());
                return;
            }
        }

        sendRemoteTransactionInfo(tid, from, cii.getXid(), false);
    }

    private void sendRemoteTransactionInfo(TransactionUID tid, BrokerAddress to,
                                           Long xid, boolean ownerWaitingFor) {

        List<Object[]> list = TransactionList.getTransListsAndRemoteTranStates(tid);
        TransactionList tl = null;
        TransactionState ts = null;
        Object[] oo = null;
        if (list != null) {
            oo = list.get(0);
            tl = (TransactionList)oo[0];
            ts = (TransactionState)oo[1];
        }
        if (ts == null && !ownerWaitingFor) { 
            if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, "Remote transaction "+ tid+ " not found");
            }
            return;
        }

        UID tlss = null;
        boolean partitionmode = Globals.getDestinationList().isPartitionMode(); 
        int loop = (list == null ? 1:list.size());
        for (int i = 0; i < loop; i++) {

        if (list != null) {
            oo = list.get(i);
            tl = (TransactionList)oo[0];
            ts = (TransactionState)oo[1];
        }

        if (ts != null && ts.getState() !=  TransactionState.ROLLEDBACK && 
                          ts.getState() != TransactionState.COMMITTED) {
            continue;
        }

        tlss = null;
        if (tl != null && partitionmode) {
            tlss = tl.getPartitionedStore().getPartitionID();
        }

        int s  = (ts == null ? TransactionState.NULL:ts.getState());
        TransactionBroker txnhome = null;
        if (s ==  TransactionState.ROLLEDBACK || s ==  TransactionState.COMMITTED) {
            if (tl != null) {
                txnhome = tl.getRemoteTransactionHomeBroker(tid);
            }
        }
        ClusterTxnInfoInfo ii = ClusterTxnInfoInfo.newInstance(
                           Long.valueOf(tid.longValue()), 
                           s, null, null, 
                           (txnhome == null ? null:txnhome.getBrokerAddress()), 
                           false, tlss, c, xid);
        if (DEBUG_CLUSTER_TXN) {
            logger.log(logger.INFO, 
            Globals.getBrokerResources().getKString(
                BrokerResources.I_SEND_REMOTE_TXN_INFO, to, ii.toString()));
        }
        try {
            c.unicast(to, ii.getGPacket());
        } catch (Exception e) {
            String[] args = { ii.toString(), to.toString(), e.getMessage() }; 
            logger.log(Logger.WARNING, Globals.getBrokerResources().getString(
                              BrokerResources.W_SEND_REMOTE_TXN_INFO_FAIL, args));
        }

        } //for
    }

    public void sendClusterTransactionInfo(long tid, BrokerAddress to) {
        sendClusterTransactionInfo(new TransactionUID(tid), to, null);
    }

    //Caller must ensure this is the transaction home broker
    private void sendClusterTransactionInfo(TransactionUID tid, BrokerAddress to, Long xid) {
        TransactionList tl = null;
        TransactionState ts = null; 
        Object[] oo = TransactionList.getTransListAndState(tid, null, true, false);
        if (oo != null) {
            tl = (TransactionList)oo[0];
            ts = (TransactionState)oo[1];
        }
        BrokerAddress[] parties = null;
        BrokerAddress[] waitfor = null;
        TransactionBroker[] brokers = null;
        if (ts != null) {
            try {
                brokers = tl.getClusterTransactionBrokers(tid);
            } catch (Exception e) {
                logger.log(logger.WARNING, 
                "Can't retrieve cluster transaction brokers:"+ e.getMessage());
            }
            if (brokers == null) {
                logger.log(logger.WARNING, 
                "No cluster transaction brokers information for TID="+ tid);
            } else {
                parties = new BrokerAddress[brokers.length];
                ArrayList waits = new ArrayList();
                for (int i = 0; i < brokers.length; i++) {
                    parties[i] = brokers[i].getBrokerAddress();
                    if (!brokers[i].isCompleted()) {
                        waits.add(brokers[i].getBrokerAddress());
                    }
                }
                if (waits.size() > 0) {
                    waitfor = (BrokerAddress[])waits.toArray(
                                  new BrokerAddress[waits.size()]);
                }
            }
        }

        UID tranpid = null;
        if (tl != null && Globals.getDestinationList().isPartitionMode()) {
            tranpid = tl.getPartitionedStore().getPartitionID();
        }
        ClusterTxnInfoInfo ii = ClusterTxnInfoInfo.newInstance(Long.valueOf(tid.longValue()), 
                                        (ts == null ? TransactionState.NULL: ts.getState()), 
                                                   parties, waitfor, Globals.getMyAddress(),
                                                   true, tranpid, c, xid);
        if (DEBUG_CLUSTER_TXN) {
        logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                   BrokerResources.I_SEND_CLUSTER_TXN_INFO, to.toString(), ii.toString()));
        }

        try {
            c.unicast(to, ii.getGPacket());
        } catch (Exception e) {
            String[] args = { ii.toString(), to.toString(), e.getMessage() }; 
            logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                              BrokerResources.W_SEND_CLUSTER_TXN_INFO_FAIL, args));
        }
    }


    public void receivedTransactionInfo(GPacket pkt, 
        BrokerAddress sender, MessageBusCallback cb) {

        ClusterTxnInfoInfo ii = ClusterTxnInfoInfo.newInstance(pkt, c); 
        BrokerAddress from = sender;
        UID msgss = ii.getMessageStoreSessionUID();
        if (msgss != null) {
            from = (BrokerAddress)sender.clone();
            from.setStoreSessionUID(msgss);
        }

        if (DEBUG_CLUSTER_TXN) {
        logger.log(logger.INFO, Globals.getBrokerResources().getString(
                   BrokerResources.I_RECEIVED_TXN_INFO, from.toString(), ii.toString()));
        }
        Long tid = ii.getTransactionID(); 
        TransactionUID tuid = new TransactionUID(tid.longValue());
        int s = ii.getTransactionState();

        TransactionList tl = null;
        TransactionState mystate = null;
        Object[] oo = TransactionList.getTransListAndState(tuid, null, true, false);
        if (oo != null) {
            tl = (TransactionList)oo[0];
            mystate = (TransactionState)oo[1];
        }
        if (!ii.isOwner() || (ii.isOwner() && from.equals(selfAddress))) {
            if (mystate != null && mystate.getState() == TransactionState.COMMITTED) {
                if (tl.getClusterTransactionBroker(tuid, from) != null) {
                    if (s == TransactionState.COMMITTED || 
                        (!ii.isOwner() && s == TransactionState.NULL)) {
                        if (DEBUG_CLUSTER_TXN) {
                        logger.log(logger.INFO, 
                        "Update broker "+from+ " for committed cluster transaction "+tuid);   
                        }
                        try {
                            tl.completeClusterTransactionBrokerState(tuid,
                                       TransactionState.COMMITTED, from, true); 
                            if (!ii.isOwner() && s != TransactionState.NULL) {
                                sendClusterTransactionInfo(tuid, from, null);
                            }

                        } catch (Exception e) {
                            logger.logStack(logger.WARNING, 
                            "Unable to update transaction broker state for "+from+", TUID="+tuid, e);
                            if (!ii.isOwner()) {
                                return;
                            }
                        }
                    }
                } else {
                    if (DEBUG_CLUSTER_TXN) {
                    logger.log(logger.INFO, 
                    "Broker "+from+" is not a transaction broker for TUID="+tuid);
                    }
                }
                if (!ii.isOwner()) {
                    return;
                }
            }
        }

        if (s == TransactionState.NULL && !ii.isOwner()) {
            return;
        }

        List<Object[]> list = TransactionList.getTransListsAndRemoteTranStates(tuid); 
        if (list == null && !ii.isOwner()) {
            return;
        }
        if (list == null && ii.isOwner()) {
            try {
                if (ii.getWaitfor() != null &&
                    ii.isWaitedfor((selfAddress))) {

                    sendRemoteTransactionInfo(tuid, from, null, true);
                    return;
                }
            } catch (Exception e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
                return;
            }
            //for rollback pending unprepared 
            if (s != TransactionState.NULL) {
                return;
            }
        }
   
        int type = -1;
        switch (s) {
            case TransactionState.ROLLEDBACK:
            case TransactionState.FAILED:
            type = ClusterGlobals.MB_MSG_TXN_ROLLEDBACK;
            break;
            case TransactionState.NULL:
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                   BrokerResources.I_REMOTE_TXN_PRESUMED_ROLLBACK, tuid, from));   
            type = ClusterGlobals.MB_MSG_TXN_ROLLEDBACK;
            break;
               
            case TransactionState.COMMITTED:
            type = ClusterGlobals.MB_MSG_CONSUMED;
            break;
            default: return;
        }
        try {
            cb.processRemoteAck2P(null, null, type, null, tid, from);
            if (s == TransactionState.COMMITTED && ii.isOwner()) {
                BrokerAddress[] brokers = ii.getBrokers();
                List waitfor = ii.getWaitfor();
                if (brokers == null && waitfor == null) return;
                if (waitfor != null) {
                    sendRemoteTransactionInfo(tuid, from, null, true);
                }
            }
        } catch (Exception e) {
            if (DEBUG_CLUSTER_TXN) {
            logger.logStack(logger.WARNING, e.getMessage(), e);
            } else {
            logger.log(logger.WARNING, e.getMessage());
            }
        }
    }


    /**
     * Remove a broker since it is no longer attached to this cluster.
     */
    public synchronized void removeBrokerInfo(BrokerAddress broker, boolean broken) {
        if (DEBUG) {
            logger.log(Logger.DEBUG,
                "RaptorProtocol.removeBrokerInfo. broker : " + broker);
        }

        BrokerInfoEx brokerInfoEx = null;

        brokerInfoEx = (BrokerInfoEx) brokerList.remove(broker);

        if (DEBUG) {
            logger.log(Logger.DEBUG, "Broker down " +broker);
        }

        /* If this was the configuration server, fail any pending
         * attempts to record configuration chage */
        try {
            if (c.getConfigServer().equals(broker)) {
                Long xid = null;
                EventLogWaiter waiter = null;
                Set s = eventLogWaiters.keySet();
                synchronized (eventLogWaiters) {
                    Iterator itr = s.iterator();
                    while (itr.hasNext()) {
                        xid = (Long)itr.next();
                        waiter = (EventLogWaiter)eventLogWaiters.get(xid);
                        itr.remove();
                        synchronized(waiter) {
                            if (waiter.getStatus() == ProtocolGlobals.G_EVENT_LOG_WAITING) {
                                waiter.setStatus(ProtocolGlobals.G_EVENT_LOG_FAILURE);
                            }
                            waiter.notifyAll();
                        }
                    }
                }

                synchronized (cfgSrvWaitObject) {
                    if (cfgSrvRequestCount > 0) {
                        cfgSrvRequestCount = 0;
                        cfgSrvRequestErr = true;
                        cfgSrvWaitObject.notifyAll();
                    }
                }
            }
        }
        catch (Exception e) {} // Catches NullPointerException too.

        logger.log(Logger.FORCE, br.I_MBUS_DEL_BROKER, broker.toString());

        cbDispatcher.brokerDown(broker);

        /*
        try {
            DL.remoteCheckBrokerDown(broker, false);
        } catch (Exception e) {
            logger.logStack(Logger.WARNING, "Destination remote check failed for down broker "+broker, e); 
        }
        */

        boolean goodbyed = false;
        if (brokerInfoEx != null) {
            goodbyed = brokerInfoEx.goodbyeDone();
        }
        ackackTracker.removeBroker(broker, goodbyed, shutdown);
        takeoverPendingReplyTracker.removeBroker(broker, goodbyed, shutdown);
        newMasterBrokerReplyTracker.removeBroker(broker, goodbyed, shutdown); 
        takeoverMEReplyTracker.removeBroker(broker, goodbyed, shutdown);
        broadcastAnyOKReplyTracker.removeBroker(broker, goodbyed, shutdown);

        // Since the broker has gone down, don't wait for its
        // election responses.
        synchronized(resTable) {
            Collection entries = resTable.keySet();
            Iterator itr = entries.iterator();
            while (itr.hasNext()) {
                String resId = (String) itr.next();
                Resource res = (Resource) resTable.get(resId);
                res.brokerRemoved(broker);
            }
        }

        if (brokerInfoEx == null) return;
        BrokerInfo brokerInfo = brokerInfoEx.getBrokerInfo();

        try {

        if (brokerInfoEx.getBrokerInfo().getBrokerAddr().getHAEnabled()) {
           Globals.getClusterManager().deactivateBroker(
                   brokerInfo.getBrokerAddr().getBrokerID(),
                   brokerInfo.getBrokerAddr().getBrokerSessionUID());
        } else {
           Globals.getClusterManager().deactivateBroker(
                   brokerInfo.getBrokerAddr().getMQAddress(), 
                   brokerInfo.getBrokerAddr().getBrokerSessionUID());
        }
        logger.log(Logger.INFO, br.getKString(br.I_CLUSTER_DEACTIVATED_BROKER, brokerInfo));
        if (Globals.getSFSHAEnabled()) {
            ClusterManager cm = Globals.getClusterManager();
            HAClusteredBroker cb = (HAClusteredBroker)cm.getBroker(
                                    brokerInfo.getBrokerAddr().getBrokerID());
            ClusterGoodbyeInfo cgi = brokerInfoEx.getGoodbyeInfo();
            if (!goodbyed || (cgi != null && cgi.getRequestTakeover())) {
                cb.setBrokerInDoubt(true, brokerInfo.getBrokerAddr().getBrokerSessionUID());
            } else {
                ((HeartbeatService)Globals.getHeartbeatService()).
                 removeHeartbeatEndpoint(cb, brokerInfo.getBrokerAddr().
                                             getBrokerSessionUID());
            }
        }

        } catch (NoSuchElementException e) { /* Ignore */
        if (DEBUG) {
        logger.logStack(Logger.INFO, "Unable to deactivate "+ brokerInfo, e);
        }
        } catch (Exception e) {
        logger.logStack(Logger.WARNING, br.getKString(
               br.W_CLUSTER_DEACTIVATE_BROKER_FAILED, brokerInfo, e.getMessage()), e);
        }

        brokerInfoEx.deactivate();

    }


    /**
     * Backup the config server data.
     */
    private void configServerBackup(String fileName) {
        if (DEBUG) {
            logger.log(Logger.INFO, "ConfigServerBackup. fileName : " + fileName);
        }

        BrokerAddress configServer = null;
        try {
            configServer = c.getConfigServer();
        }
        catch (Exception e) {
            logger.log(Logger.WARNING, br.W_MBUS_CANCEL_BACKUP1);
            return;
        }

        if (configServer == null ||
            !configServer.equals(selfAddress)) {
            logger.log(Logger.WARNING, br.W_MBUS_CANCEL_BACKUP1);
            return;
        }

        try {
            masterBrokerBlockWait(ProtocolGlobals.getWaitReplyTimeout(), "[BACKUP]");
            try {
                List<ChangeRecordInfo> records = store.getAllConfigRecords();
                ChangeRecord.backupRecords(records, fileName, false);
            } finally {
                masterBrokerUnBlock();
            }

        } catch (Exception e) {
            logger.logStack(Logger.WARNING, br.W_MBUS_BACKUP_ERROR, e);
        }

        if (DEBUG) {
            logger.log(Logger.INFO, "ConfigServerBackup complete.");
        }
    }

    /**
     * Restore the config server database.
     */
    private void configServerRestore(String fileName) {
        if (DEBUG) {
            logger.log(Logger.INFO,
                "RaptorProtocol.configServerRestore. fileName = " + fileName);
        }

        try {
            // Make sure that the file does exist.
            File f = new File(fileName);
            if (! f.exists()) {
                logger.log(Logger.WARNING, br.W_MBUS_CANCEL_RESTORE1, fileName);
                return;
            }

            masterBrokerBlockWait(ProtocolGlobals.getWaitReplyTimeout(), "[RESTORE]");
            try {
                List<ChangeRecordInfo> records = ChangeRecord.prepareRestoreRecords(fileName);
                store.clearAllConfigChangeRecords(false);

                Iterator itr = records.iterator();
                ChangeRecordInfo cri = null;
                long startime = System.currentTimeMillis();
                while (itr.hasNext()) {
                    cri = (ChangeRecordInfo)itr.next();
                    store.storeConfigChangeRecord(
                        (Globals.isBDBStore() ? startime++:System.currentTimeMillis()),
                     cri.getRecord(), false);
                }
                logger.log(Logger.INFO, br.I_CLUSTER_MB_RESTORE_SUCCESS, fileName);
            } finally {
                masterBrokerUnBlock();
            }
        }
        catch (Exception e) {
            logger.logStack(Logger.WARNING, br.W_MBUS_RESTORE_ERROR, e);
            return;
        }

        if (DEBUG) {
            logger.log(Logger.INFO, "RaptorProtocol.configServerRestore complete.");
        }
    }

    private void storeLastConfigServer(BrokerAddress baddr)
        throws BrokerException {
        store.updateProperty(ClusterGlobals.STORE_PROPERTY_LASTCONFIGSERVER, baddr, false);
    }

    private BrokerAddress getLastConfigServer() {
        BrokerAddress baddr = null;

        try {
            baddr = (BrokerAddress)
                store.getProperty(ClusterGlobals.STORE_PROPERTY_LASTCONFIGSERVER);
        }
        catch (Exception e) {}

        return baddr;
    }

    private void storeLastRefreshTime(long timestamp)
        throws BrokerException {
        Long t = Long.valueOf(timestamp);
        store.updateProperty(ClusterGlobals.STORE_PROPERTY_LASTREFRESHTIME, t, false);
    }

    private long getLastRefreshTime() {
        Long t = null;

        try {
            t = (Long) store.getProperty(ClusterGlobals.STORE_PROPERTY_LASTREFRESHTIME);
        }
        catch (Exception e) {}

        if (t == null) {
            return -1;
        }

        return t.longValue();
    }


    public boolean getConfigSyncComplete() {
        return configSyncComplete;
    }

    private static byte[] getEventData(GPacket event) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            event.write(bos);
            bos.flush();
        }
        catch (Exception e) {}

        return bos.toByteArray();
    }

    public static byte[] generateAddDurableRecord(Subscription sub) {
        try {
            ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
            return getEventData(csi.getGPacket(ProtocolGlobals.G_NEW_INTEREST, true));
        }
        catch (Exception e) {
            Globals.getLogger().logStack(Logger.INFO,
                "Internal Error: generateAddDurableRecord failed.",
                e);
            return null;
        }
    }

    public static byte[] generateRemDurableRecord(Subscription sub) {
        try {
            ClusterSubscriptionInfo csi = ClusterSubscriptionInfo.newInstance(sub);
            return getEventData(csi.getGPacket(ProtocolGlobals.G_REM_DURABLE_INTEREST, true));
        }
        catch (Exception e) {
            Globals.getLogger().logStack(Logger.WARNING,
                "generateRemDurableRecord failed.", e);
            return null;
        }
    }

    public static byte[] generateAddDestinationRecord(Destination d) {
        try {
            ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
            GPacket gp = cdi.getGPacket(ProtocolGlobals.G_UPDATE_DESTINATION, true);

            return getEventData(gp);
        }
        catch (Exception e) {
            Globals.getLogger().logStack(Logger.WARNING,
                "generateRemDestinationRecord failed.", e);
            return null;
        }
    }

    public static byte[] generateRemDestinationRecord(Destination d) {
        try {
		    ClusterDestInfo cdi = ClusterDestInfo.newInstance(d);
            GPacket gp = cdi.getGPacket(ProtocolGlobals.G_REM_DESTINATION, true);
            return getEventData(gp);
        }
        catch (Exception e) {
            Globals.getLogger().logStack(Logger.INFO,
                "generateRemDestinationRecord failed.", e);
            return null;
        }
    }
}

/**
 * Represents a resource to be locked. E.g. durable name, client ID,
 * role of primary queue receiver.
 */
class Resource {
    private final Logger logger = Globals.getLogger();
    private String resId = null;
    private Object owner = null;
    private long timestamp;
    private long xid;
    private int lockState;
    private boolean shared;

    private int status;
    private HashMap<BrokerAddress, Object> recipients;
    private Cluster c = null;

    public Resource(String resId, Cluster c) {
        this.resId = resId;
        this.c = c;
        timestamp = 0;
        xid = 0;

        recipients = new HashMap<BrokerAddress, Object>();
    }

    public String toString() {
        return "["+resId+", owner="+owner+", "+
            getLockStateString(lockState)+", timestamp="+timestamp+
            ", shared="+shared+", xid="+getXid()+"]";
    }

    public static String getLockStateString(int s) {
        if (s == ProtocolGlobals.G_RESOURCE_LOCKED) {
            return "RESOURCE_LOCKED";
        } else if (s == ProtocolGlobals.G_RESOURCE_LOCKING) {
            return "RESOURCE_LOCKING";
        } else {
            return "UNKNOWN";
        }
    }

    public String getResId() {
        return resId;
    }

    public Object getOwner() {
        return owner;
    }

    public void setOwner(Object owner) {
        this.owner = owner;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public synchronized long getXid() {
        return xid;
    }

    public int getLockState() {
        return lockState;
    }

    public void setLockState(int lockState) {
        this.lockState = lockState;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean getShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String showRecipients(PingHandler pingHandler, Cluster c) {
        ClusterManager cm = Globals.getClusterManager();
        StringBuffer ret = new StringBuffer();;
        String brokerid;
        Iterator itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress baddr = (BrokerAddress) itr.next();
            pingHandler.enablePingLogging(baddr);
            c.enablePingLogging(baddr);
            if (Globals.getHAEnabled()) {
                brokerid = baddr.getBrokerID();   
            } else {
                brokerid = cm.lookupBrokerID(baddr.getMQAddress());
            }
            if (brokerid != null) {
               ret.append("\n\t" + baddr.toString()+"["+cm.getBroker(brokerid)+"]");
            } else {
               ret.append("\n\t" + baddr.toString());
            }
        }

        return ret.toString();
    }

    /**
     * Election protocol preparation. Remember the list of brokers
     * that need to vote on this lock request.
     */
    public synchronized void prepareLockRequest(
        BrokerAddress[] brokerList, long xid) {
        recipients.clear();
        for (int i = 0; i < brokerList.length; i++)
            recipients.put( brokerList[i], null);
        this.xid = xid;
        status = ProtocolGlobals.G_LOCK_SUCCESS;
    }

    public synchronized void updateRecipients(Map<BrokerAddress, Object> map) {
        ArrayList<BrokerAddress> updates = null;
        Object o;
        BrokerAddress baddr;
        Iterator<BrokerAddress> itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            baddr = itr.next();
            o = map.get(baddr);
            if (o == null) {
                itr.remove();
            } else {
                if (updates == null) {
                    updates = new ArrayList<BrokerAddress>(map.size());
                }
                updates.add(baddr);
            }
        }
        if (updates != null) {
            itr = updates.iterator();
            while (itr.hasNext()) {
                baddr = itr.next();
                recipients.put(baddr, map.get(baddr));
            }
        }
        notifyAll();
    }

    //call with synchronization
    private boolean needRestartLockRequest() {
        Iterator<Map.Entry<BrokerAddress, Object>> itr = 
            recipients.entrySet().iterator();
        Map.Entry<BrokerAddress, Object> pair = null;
        while (itr.hasNext()) {
            pair = itr.next();
            if (c.isLinkModified(pair.getKey(), pair.getValue())) {
                return true;
            }
        }
        return false;
    }
  

    private boolean hasUnreachable(int timeout) {
        boolean hasUnreachable = false;
        Iterator itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress baddr = (BrokerAddress) itr.next();
            try {
            if (!c.isReachable(baddr, timeout)) {
                logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                              BrokerResources.I_CLUSTER_CLOSE_UNREACHABLE, baddr));
                c.closeLink(baddr, true);
                hasUnreachable = true;
            }
            } catch (IOException e) {
            logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
            BrokerResources.W_CLUSTER_CANNOT_CHECK_REACHABILITY, baddr, e.getMessage()));
            }
        }
        return hasUnreachable;
    }


    /**
     * Wait for the conclusion of election protocol.
     */
    public synchronized int waitForStatusChange(int timeout, boolean failOntimeout) {
        long waittime = timeout * 1000L;
        long endtime = System.currentTimeMillis() + waittime;

        boolean checkReachable = true;
        while (status == ProtocolGlobals.G_LOCK_SUCCESS &&
               recipients.size() > 0) {
            try {
                wait(waittime);
            } catch (Exception e) {}

            if (status != ProtocolGlobals.G_LOCK_SUCCESS ||
                recipients.size() == 0) {
                break;
            }
            long curtime = System.currentTimeMillis();
            if (curtime >= endtime) {
                if (!failOntimeout && checkReachable) {
                    if (hasUnreachable(timeout)) {
                        waittime = timeout * 1000L;
                        endtime = System.currentTimeMillis() + waittime;
                        checkReachable = false;
                        continue;
                    }
                }
                if (needRestartLockRequest()) {
                    return ProtocolGlobals.G_LOCK_TRY_AGAIN;
                }
                return ProtocolGlobals.G_LOCK_TIMEOUT;
            }

            waittime = endtime - curtime;            
        }

        return status;
    }

    /**
     * Process an election protocol 'vote' from a broker.
     */
    public synchronized void consumeResponse(long xid,
        BrokerAddress sender, int response) {
        if (xid != this.xid)
            return;

        if (status != ProtocolGlobals.G_LOCK_SUCCESS)
            return;

        switch (response) {
        case ProtocolGlobals.G_LOCK_SUCCESS:
            recipients.remove(sender);
            break;

        case ProtocolGlobals.G_LOCK_FAILURE:
        case ProtocolGlobals.G_LOCK_BACKOFF:
            status = response;
            break;
        }

        if (status != ProtocolGlobals.G_LOCK_SUCCESS ||
            recipients.size() == 0)
            notifyAll();
    }

    /**
     * Forfeit an attempt to lock a resource.
     */
    public synchronized void impliedFailure() {
        status = ProtocolGlobals.G_LOCK_FAILURE;
        notifyAll();
    }

    public synchronized void brokerAdded(BrokerAddress broker) {
        status = ProtocolGlobals.G_LOCK_TRY_AGAIN;
        notifyAll();
    }

    public synchronized void brokerRemoved(BrokerAddress broker) {
        if (status != ProtocolGlobals.G_LOCK_SUCCESS)
            return;

        recipients.remove(broker);
        if (recipients.size() == 0)
            notifyAll();
    }
}


class ReplyStatus {

    private GPacket reply = null;

    public ReplyStatus(GPacket gp) {
        reply = gp;
    }

    public int getStatus() {
        return ((Integer)reply.getProp("S")).intValue();
    }
    public String getReason() {
        String r = (String)reply.getProp("reason");
        return (r == null ? "":r);
    }
    public GPacket getReply() {
        return reply;
    }
}


class ReplyTracker {

    private Map waiters = null;

    public ReplyTracker() {
        waiters = Collections.synchronizedMap(new LinkedHashMap());
    }

    /**
     * @return The xid 
     */
    public Long addWaiter(ReplyWaiter waiter) {
        Long xid = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        waiters.put(xid, waiter);
        return xid;
    }

    public void addWaiter(Long xid, ReplyWaiter waiter) {
        waiters.put(xid, waiter);
    }

    protected ReplyWaiter getWaiter(Long xid) {
        return (ReplyWaiter)waiters.get(xid);
    }

    public void removeWaiter(Long xid) {
        waiters.remove(xid);
    }

    /**
     * @param xid The reply correlation ID 
     * @param timeout in seconds
     *
     * @return The reply status or null if unable to get reply status 
     */
    public ReplyStatus waitForReply(Long xid, int timeout) throws BrokerException {
        ReplyWaiter waiter = (ReplyWaiter)waiters.get(xid);
        assert ( waiter != null );
        try {
            return waiter.waitForReply(timeout);
        } finally {
            waiters.remove(xid);
        }
    }

    /**
     * @return The same ReplyStatus object or null if not found
     */
    public boolean notifyReply(Long xid, BrokerAddress from, GPacket reply) {
        ReplyWaiter waiter = (ReplyWaiter)waiters.get(xid);
        if (waiter == null) return false; 
        waiter.notifyReply(from, reply);
        return true;
    }

    public void abortWaiter(Long xid) {
        ReplyWaiter waiter = (ReplyWaiter)waiters.get(xid);
        if (waiter == null) return; 
        waiter.abort();
    }

    public void addBroker(BrokerAddress remote) {
        //not implemented
    }

    public void removeBroker(BrokerAddress remote, boolean goodbyed, boolean shutdown) { 
         Long xid = null;
         ReplyWaiter waiter = null;
         Set s = waiters.keySet();
         synchronized (waiters) {
             Iterator itr = s.iterator();
             while (itr.hasNext()) {
                 xid = (Long)itr.next();
                 waiter = (ReplyWaiter)waiters.get(xid);
                 waiter.removeParticipant(remote, goodbyed, shutdown);
             }
         }
    }
}

abstract class ReplyWaiter {

    protected Logger logger = Globals.getLogger();

    //not overlap with jmq.io.Status
    protected static final int WAITING = 0;

    protected int waitStatus = WAITING; 
    protected HashMap participants = new HashMap();
    protected HashMap replies = new HashMap(); 
    private short protocol;

    private static final long DEFAULT_WAIT_INTERVAL = 60000; //60sec  

    /**
     * @param participant Wait reply from
     * @param protocol Cluster protocol to wait for
     */
    public ReplyWaiter(BrokerAddress participant, short protocol) {
        this(new BrokerAddress[]{participant}, protocol); 
    }

    public ReplyWaiter(BrokerAddress[] brokerList, short protocol) {
        this.waitStatus = WAITING;
        for (int i = 0; i < brokerList.length; i++) {
            this.participants.put(brokerList[i], "");
        }
        this.protocol = protocol;
    }

    public synchronized int getWaitStatus() {
        return waitStatus;
    }

    public synchronized void setWaitStatus(int s) {
        waitStatus = s;
        notifyAll();
    }

    private synchronized String currentParticipants() {
        StringBuffer cp = new StringBuffer("");
        Iterator itr = participants.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress addr = (BrokerAddress)itr.next();
            cp.append("\n\t" + addr.toString());
        }
        return cp.toString();
    }

    /**
     * 
     * @return ReplyStatus if not aborted and Status is OK 
     *                     else null if aborted 
     * @throws BrokerException if Status is not OK 
     *
     */
    public synchronized ReplyStatus waitForReply(int timeout) throws BrokerException {
        long endtime = System.currentTimeMillis() + timeout*1000L;

        long waittime = timeout*1000L;
        if (waittime > DEFAULT_WAIT_INTERVAL) waittime = DEFAULT_WAIT_INTERVAL;
        int loglevel = Logger.DEBUGHIGH;

        int i = 0;
        while (waitStatus == WAITING) {
            try {
                Object[] args = { Integer.valueOf(i++),
                                  ProtocolGlobals.getPacketTypeDisplayString(protocol),
                                  currentParticipants() };
                logger.log(loglevel, Globals.getBrokerResources().getKTString(
                           BrokerResources.I_CLUSTER_WAITING_REPLY, args));
                wait(waittime);
            } catch (Exception e) {}

            long curtime = System.currentTimeMillis();
            if (curtime >= endtime)  {
                if (waitStatus == WAITING) {
                    waitStatus = Status.TIMEOUT;
                }
                continue;
            }

            waittime = endtime - curtime;
            if (waittime > DEFAULT_WAIT_INTERVAL) waittime = DEFAULT_WAIT_INTERVAL;
            loglevel = Logger.INFO;
        }

        if (waitStatus == Status.OK) {
            return getReply();
        }

        throw new BrokerException(Status.getString(waitStatus), waitStatus); 
    }

    public synchronized void abort() {
	    if (waitStatus != WAITING) return;

        waitStatus = Status.OK;
        notifyAll();
    }

    public synchronized void notifyReply(BrokerAddress from, GPacket reply) {
        if (participants.remove(from) != null) {
            replies.put(from, new ReplyStatus(reply)); 
            onReply(from, reply);
        }
    }

    public synchronized void addParticipant(BrokerAddress remote) {
        onAddParticipant(remote);
    }

    public synchronized void removeParticipant(BrokerAddress remote, 
                                               boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) return;

        onRemoveParticipant(remote, goodbyed, shutdown);
    }

    /** 
     * a. set waitStatus and notify if necessary
     * b. check participants.size() == 0 notify if necessary
     */
    public abstract void onReply(BrokerAddress remote, GPacket reply);

    public abstract void onAddParticipant(BrokerAddress remote); 

    /**
     * a. decide whether to remove from participants             
     * b. set waitStatus and notify if necessary
     */
    public abstract void onRemoveParticipant(BrokerAddress remote, 
                                             boolean goodbyed, boolean shutdown);

    public abstract ReplyStatus getReply();

}

class MessageAckReplyWaiter extends ReplyWaiter {

    private BrokerAddress home;

    public MessageAckReplyWaiter(BrokerAddress home) {
        super(home, ProtocolGlobals.G_MESSAGE_ACK_REPLY);
        this.home = home;
    }

    public void onReply(BrokerAddress remote, GPacket reply) {
        waitStatus = Status.OK;
        notifyAll();
    }

    public void onAddParticipant(BrokerAddress remote) {
        //do nothing
    }

    public void onRemoveParticipant(BrokerAddress remote, 
                                    boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) {
            return;
        }
        participants.remove(remote);

        if (!remote.equals(home) && !shutdown) {
            return;
        }
        if (goodbyed) {
            waitStatus = Status.GONE;
        } else {
            waitStatus = Status.TIMEOUT;
        }
        notifyAll();
    }

    public ReplyStatus getReply() {
        return (ReplyStatus)replies.get(home);
    }
}

class UnicastReplyWaiter extends ReplyWaiter {

    private BrokerAddress toBroker;

    public UnicastReplyWaiter(BrokerAddress to, short replyType) {
        super(to, replyType);
        this.toBroker = to;
    }

    public void onReply(BrokerAddress remote, GPacket reply) {
        waitStatus = Status.OK;
        notifyAll();
    }

    public void onAddParticipant(BrokerAddress remote) {
        //do nothing
    }

    public void onRemoveParticipant(BrokerAddress remote,
                                    boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) {
            return;
        }
        participants.remove(remote);
        if (!remote.equals(toBroker) && !shutdown) {
            return;
        }
        if (goodbyed) {
            waitStatus = Status.GONE;
        } else {
            waitStatus = Status.TIMEOUT;
        }
        notifyAll();
    }

    public ReplyStatus getReply() {
        return (ReplyStatus)replies.get(toBroker);
    }

    public BrokerAddress getToBroker() {
        return toBroker;
    }
}

class BroadcastAnyOKReplyWaiter extends ReplyWaiter {

    private BrokerAddress okBroker = null;

    public BroadcastAnyOKReplyWaiter(BrokerAddress[] tos, short replyType) {
        super(tos, replyType);
    }

    public void onReply(BrokerAddress remote, GPacket reply) {
        if (((Integer)reply.getProp("S")).intValue() == Status.OK) {
            this.okBroker = remote;
            waitStatus = Status.OK;
            notifyAll();
            return;
        }
        if (participants.size() == 0) {
            notifyAll();
        }
    }

    public void onAddParticipant(BrokerAddress remote) {
        //do nothing
    }

    public void onRemoveParticipant(BrokerAddress remote,
                                    boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) {
            return;
        }
        participants.remove(remote);

        if (shutdown || participants.size() == 0) {
            waitStatus = Status.GONE;
            notifyAll();
            return;
        }
    }

    public ReplyStatus getReply() {
        if (okBroker == null) {
            return null;
        }
        return (ReplyStatus)replies.get(okBroker);
    }
}

class TakeoverPendingReplyWaiter extends ReplyWaiter {

    public TakeoverPendingReplyWaiter(BrokerAddress[] brokerList) {
        super(brokerList, ProtocolGlobals.G_TAKEOVER_PENDING_REPLY);
    }

    public void onReply(BrokerAddress remote, GPacket reply) {
        if (participants.size() == 0) { 
            waitStatus = Status.OK;
            notifyAll();
            return;
        }
    }

    public void onAddParticipant(BrokerAddress remote) {
        //do nothing
    }

    public void onRemoveParticipant(BrokerAddress remote, 
                                    boolean goodbyed, boolean shutdown) {
        if (waitStatus != WAITING) return; 

        if (shutdown) {
            waitStatus = Status.GONE;
            notifyAll();
            return;
        }
        if (goodbyed) {
            participants.remove(remote);
            if (participants.size() == 0) {
                waitStatus = Status.OK;
            }
            notifyAll();
            return;
        }
    }

    public ReplyStatus getReply() {
        return  null;
    }
}

class TakeoverCleanupThread extends Thread {

    private Logger logger = Globals.getLogger();
    private RaptorProtocol p = null;
    private BrokerAddress sender = null;
    private ClusterTakeoverInfo cti = null;
    private TakingoverEntry toe = null;
    private short protocol;
    private boolean doconverge = true;

    public TakeoverCleanupThread(ThreadGroup tg, 
                                 RaptorProtocol p, 
                                 BrokerAddress sender, 
                                 ClusterTakeoverInfo cti, 
                                 TakingoverEntry toe, short protocol) {
        this(tg, p, sender, cti, toe, protocol, false);
    }

    public TakeoverCleanupThread(ThreadGroup tg, 
                                 RaptorProtocol p, 
                                 BrokerAddress sender, 
                                 ClusterTakeoverInfo cti, 
                                 TakingoverEntry toe, short protocol, boolean doconverge) {
        super(tg, "TakeoverCleanup");
        if (Thread.MAX_PRIORITY - 1 > 0) setPriority(Thread.MAX_PRIORITY-1);
        setDaemon(true);
	    this.p = p;
        this.sender = sender;
        this.cti = cti;
        this.toe = toe;
        this.protocol = protocol;
        this.doconverge = doconverge;
    }

    public void run() {
        logger.log(Logger.DEBUG, "Processing "+ ProtocolGlobals.getPacketTypeString(protocol));
        p.takeoverCleanup(toe, protocol == ProtocolGlobals.G_TAKEOVER_COMPLETE);

        if (protocol == ProtocolGlobals.G_TAKEOVER_COMPLETE) {
            return;
        }
        if (doconverge) {
            p.takeoverPendingConvergecast(sender, cti);
        }
        toe.preTakeoverDone(cti.getXid());
        logger.log(Logger.DEBUG, "Done processing "+ ProtocolGlobals.getPacketTypeString(protocol));
    }
}

