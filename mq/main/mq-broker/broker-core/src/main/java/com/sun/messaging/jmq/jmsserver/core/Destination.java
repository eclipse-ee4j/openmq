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
 * @(#)Destination.java	1.320 11/26/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;


import java.io.Serializable;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.io.PacketType;

import com.sun.messaging.jmq.util.DestLimitBehavior;
import com.sun.messaging.jmq.util.ClusterDeliveryPolicy;
import com.sun.messaging.jmq.util.DestScope;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ProducerSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.DestinationSpi;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ConflictException;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.util.lists.AddReason;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.jmsserver.util.ConflictException;
import com.sun.messaging.jmq.jmsserver.util.DestinationNotFoundException;
import com.sun.messaging.jmq.jmsserver.util.FeatureUnavailableException;
import com.sun.messaging.jmq.jmsserver.util.TransactionAckExistException;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.handlers.RefCompare;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.DiskFileStore;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.memory.MemoryManager;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;

import com.sun.messaging.jmq.util.lists.*;

import java.util.*;
import java.io.*;
import java.lang.*;

/**
 * This class represents a destination (topic or queue name)
 */
public abstract class Destination implements DestinationSpi, 
Serializable, com.sun.messaging.jmq.util.lists.EventListener
{
    static final long serialVersionUID = 4399175316523022128L;

    protected static boolean DEBUG = false;

    protected static final boolean DEBUG_CLUSTER = DestinationList.DEBUG_CLUSTER;

    private static final FaultInjection FI = FaultInjection.getInjection();

    protected String INITIALIZEBY = "";

    transient protected boolean destvalid = true;
    transient protected boolean startedDestroy =false;

    protected transient Logger logger = Globals.getLogger();
    protected transient BrokerResources br = Globals.getBrokerResources();

    protected transient BrokerMonitor bm = null;

    protected transient boolean stored = false;
    protected transient boolean neverStore = false;
    protected transient SimpleNFLHashMap destMessages = null;
    private transient HashMap destMessagesInRemoving =  null;
    private transient Object _removeMessageLock =  null;
    private boolean dest_inited = false;

    private transient int refCount = 0;

    /**
     * metrics counters
     */
    protected int expiredCnt = 0;
    protected int purgedCnt = 0;
    protected int ackedCnt = 0;
    protected int discardedCnt = 0;
    protected int overflowCnt = 0;
    protected int errorCnt = 0;
    protected int rollbackCnt = 0;

    transient Object sizeLock = new Object();
    /**
     * size of a destination when it is unloaded
     */
    transient int size = 0;
    transient int remoteSize = 0;

    /**
     * bytes of a destination when it is unloaded
     */
    transient long bytes = 0;
    transient long remoteBytes = 0;

    transient boolean loaded = false;
    protected transient SimpleNFLHashMap consumers = new SimpleNFLHashMap();
    protected transient SimpleNFLHashMap producers = new SimpleNFLHashMap();

    transient DestReaperTask destReaper = null;

    protected DestinationUID uid = null;
    protected int type = -1;
    protected transient int state = DestState.UNKNOWN;
    protected int scope = DestScope.CLUSTER;
    protected int limit = DestLimitBehavior.REJECT_NEWEST;
    protected ConnectionUID id = null; // only for temp destinations
    protected SizeString msgSizeLimit = null; 
    protected int countLimit = 0;
    protected SizeString memoryLimit = null;

    protected int maxConsumerLimit = DestinationList.UNLIMITED; 
    protected int maxProducerLimit = DestinationList.defaultProducerCnt;
    protected int maxPrefetch = DestinationList.DEFAULT_PREFETCH;

    protected transient int producerMsgBatchSize = DestinationList.MAX_PRODUCER_BATCH;
    protected transient long producerMsgBatchBytes =  -1;

    private long clientReconnectInterval = 0;

    private transient ReconnectReaperTask reconnectReaper = null;

    private transient ProducerFlow producerFlow = new ProducerFlow();

    private boolean unloadMessagesAtStore = false;

    boolean useDMQ = DestinationList.autocreateUseDMQ;

    boolean isDMQ = false;

    boolean validateXMLSchemaEnabled = false;
    String XMLSchemaUriList = null;
    boolean reloadXMLSchemaOnFailure = false;

    private transient boolean clusterNotifyFlag = false;

    private transient Map<Integer, ChangeRecordInfo> currentChangeRecordInfo = 
        Collections.synchronizedMap(new HashMap<Integer, ChangeRecordInfo>());

    private transient DestinationList DL = null;
    protected transient PartitionedStore pstore = null;
    private transient String logsuffix = "";
    private transient Object queueRemoteLock = new Object();
    private transient Object queueRemoteLockThread = null;

    public static final int UNLIMITED=-1;
    public static final String TEMP_CNT = "JMQ_SUN_JMSQ_TempRedeliverCnt";

    public static final int DEFAULT_RECONNECT_MULTIPLIER = 5;
    public static final int RECONNECT_MULTIPLIER = getRECONNECT_MULTIPLIER();

    private static final int LOAD_COUNT = Globals.getConfig().getIntProperty(
              Globals.IMQ + ".destination.verbose.cnt", 10000);

    private static final boolean EXPIRE_DELIVERED_MSG = 
        Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".destination.expireDeliveredMessages", false);

    private static final boolean PURGE_DELIVERED_MSG = 
        Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".destination.purgeDeliveredMessages", false);

    public static final boolean PERSIST_SYNC =  DestinationList.PERSIST_SYNC; 

    protected static final int NONE=0;

    private static MQTimer timer = Globals.getTimer();

    private static boolean getDEBUG() {
        return (DEBUG || DestinationList.DEBUG);
    }

    private static int getRECONNECT_MULTIPLIER() {
        int v = Globals.getConfig().getIntProperty(
            Globals.IMQ+".reconnect.interval", DEFAULT_RECONNECT_MULTIPLIER);
        if (v < 1) {
            v = DEFAULT_RECONNECT_MULTIPLIER;
        }
        return v;
    }

    public ChangeRecordInfo getCurrentChangeRecordInfo(int type) {
        return currentChangeRecordInfo.get(Integer.valueOf(type));
    }

    public void setCurrentChangeRecordInfo(int type, ChangeRecordInfo cri) {
        currentChangeRecordInfo.put(Integer.valueOf(type), cri);
    }

    public PacketReference peekNext() {
        return null;
    }

    /**
     */
    public void setDestinationList(DestinationList dl) {
        this.DL = dl;
        this.pstore = dl.getPartitionedStore();
        if (DL.isPartitionMode()) {
            logsuffix = " ["+pstore+"]"; 
        }       
    }

    public PartitionedStore getPartitionedStore() {
        return pstore;
    }

    public void setUseDMQ(boolean use) 
        throws BrokerException
    {
        if (use && isDMQ)
            throw new BrokerException(br.getKString(
                            BrokerResources.X_DMQ_USE_DMQ_INVALID));
	Boolean oldVal = Boolean.valueOf(this.useDMQ);
        this.useDMQ = use;

        notifyAttrUpdated(DestinationInfo.USE_DMQ, 
			oldVal, Boolean.valueOf(this.useDMQ));
    }
    public boolean getUseDMQ() {
        return useDMQ;
    }

    // used only as a space holder when deleting corrupted destinations
    protected Destination(DestinationUID uid) {
         this.uid = uid;
    }

    /**
     * this method is called when a message has
     * been completely acked and is dead
      */
    public boolean removeDeadMessage(PacketReference ref)
        throws IOException, BrokerException
    {
        return removeDeadMessage(ref, ref.getDeadComment(),
            ref.getDeadException(), ref.getDeadDeliverCnt(),
            ref.getDeadReason(), ref.getDeadBroker()); 
    }

    public boolean removeDeadMessage(PacketReference ref,
        String comment, Throwable exception, int deliverCnt,
         Reason r, String broker) 
        throws IOException, BrokerException
    {

        if (getDEBUG()) {
            logger.log(Logger.DEBUG,"Calling removeDeadMessage on " + ref
                 + " [" + comment + "," + exception+ "," + 
                 deliverCnt + "," + r + "]");
        }   
        if (ref.isInvalid()) {
            logger.log(Logger.DEBUG, "Internal Error: message is already dead");
            return false;
        }

        Destination d = ref.getDestination();
        if (d == DL.getDMQ()) {
            throw new RuntimeException("Already dead");
        }

        Hashtable m = new Hashtable();

        if (comment != null)
            m.put(DMQ.UNDELIVERED_COMMENT, comment);

        if (deliverCnt != -1)
            m.put(TEMP_CNT, Integer.valueOf(deliverCnt));

        if (exception!= null)
            m.put(DMQ.UNDELIVERED_EXCEPTION, exception);

        if (broker != null)
            m.put(DMQ.DEAD_BROKER, broker);
        else
            m.put(DMQ.DEAD_BROKER, Globals.getMyAddress().toString());

        // remove the old message
        if (r == null)
            r = RemoveReason.ERROR;

        RemoveMessageReturnInfo ret = _removeMessage(ref.getSysMessageID(), r, m,
                                                     null, !ref.isExpired());
        return ret.removed;
    }

    public int seqCnt = 0;

    /**
     * place the message in the DMQ.<P>
     * called from Destination and Consumer.
     */
    private void markDead(PacketReference pr, 
        Reason reason, Hashtable props)
        throws BrokerException {
        
        Packet p = pr.getPacket();
        if (p == null) {
            logger.log(Logger.DEBUG,"Internal Error: null packet for DMQ");
            return;
        }
        Hashtable packetProps = null;
        try {
            packetProps = p.getProperties();
            if (packetProps == null)
                packetProps = new Hashtable();
        } catch (Exception ex) {
            logger.logStack(Logger.DEBUG,"could not get props ", ex);
            packetProps = new Hashtable();
        }

        boolean useVerbose = false;

        Object o = packetProps.get(DMQ.VERBOSE);
        if (o != null) {
            if (o instanceof Boolean) {
                useVerbose = ((Boolean)o).booleanValue();
            } else if (o instanceof String) {
                useVerbose = Boolean.valueOf((String)o).booleanValue();
            } else {
                logger.log(Logger.WARNING,
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                      "Unknown type for verbose " + o.getClass());
                useVerbose=DL.getVerbose();
            }
        } else {
            useVerbose = DL.getVerbose();
        }

        if (isDMQ) {
            if (getDEBUG() || useVerbose) {
                logger.log(Logger.INFO, BrokerResources.I_DMQ_REMOVING_DMQ_MSG,
                     pr.getSysMessageID(),
                     DestinationUID.getUID(p.getDestination(),
                            p.getIsQueue()).toString());
            }
            return;
        }

        // OK deal with various flags
        boolean useDMQforMsg = false;

        o = packetProps.get(DMQ.PRESERVE_UNDELIVERED);
        if (o != null) {
            if (o instanceof Boolean) {
                useDMQforMsg = ((Boolean)o).booleanValue();
            } else if (o instanceof String) {
                useDMQforMsg = Boolean.valueOf((String)o).booleanValue();
            } else {
                logger.log(Logger.WARNING,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Unknown type for preserve undelivered " +
                       o.getClass());
                useDMQforMsg=useDMQ;
            }
        } else {
            useDMQforMsg = useDMQ;
        }

        long receivedTime = pr.getTime();
        long senderTime = pr.getTimestamp();
        long expiredTime = pr.getExpireTime();

        if (!useDMQforMsg) {
            if (getDEBUG() || useVerbose) {
                String args[] = { pr.getSysMessageID().toString(),
                    pr.getDestinationUID().toString(),
                    lookupReasonString(reason, receivedTime,
                       expiredTime, senderTime) };
                logger.log(Logger.INFO, BrokerResources.I_DMQ_REMOVING_MSG, args); 
            }
            if (!pr.isLocal()) {
                boolean waitack = !pr.isNoAckRemoteConsumers();
                Globals.getClusterBroadcast().acknowledgeMessage(
                                              pr.getBrokerAddress(),
                                              pr.getSysMessageID(),
                                              pr.getQueueUID(), 
                                              ClusterBroadcast.MSG_DEAD,
                                              props, waitack);
           }
           return;
        }

        boolean truncateBody = false;
        o = packetProps.get(DMQ.TRUNCATE_BODY);
        if (o != null) {
            if (o instanceof Boolean) {
                truncateBody = ((Boolean)o).booleanValue();
            } else if (o instanceof String) {
                truncateBody = Boolean.valueOf((String)o).booleanValue();
            } else {
                logger.log(Logger.WARNING,
                      BrokerResources.E_INTERNAL_BROKER_ERROR,
                      "Unknown type for preserve undelivered " +
                       o.getClass());
                truncateBody=!DL.getStoreBodyInDMQ();
            }
        } else {
            truncateBody = !DL.getStoreBodyInDMQ();
        }

        if (props == null) {
            props = new Hashtable();
        }
        Integer cnt = (Integer)props.remove(TEMP_CNT);
        if (cnt != null) {
            // set as a header property
            props.put(DMQ.DELIVERY_COUNT, cnt);
        } else { // total deliver cnt ?
        }

        if (pr.isLocal())  {
            props.putAll(packetProps);
        } else {
            // reason for the other side
            props.put("REASON", Integer.valueOf(reason.intValue()));
        }

        if (props.get(DMQ.UNDELIVERED_COMMENT) == null) {
            props.put(DMQ.UNDELIVERED_COMMENT, lookupReasonString(reason,
                  receivedTime, expiredTime, senderTime));
        }

        props.put(DMQ.UNDELIVERED_TIMESTAMP,
             Long.valueOf(System.currentTimeMillis()));

        props.put(DMQ.BODY_TRUNCATED,
             Boolean.valueOf(truncateBody));

        String reasonstr = null;

        if (reason == RemoveReason.EXPIRED || 
            reason == RemoveReason.EXPIRED_BY_CLIENT || 
            reason == RemoveReason.EXPIRED_ON_DELIVERY) {
            props.put(DMQ.UNDELIVERED_REASON,
                  DMQ.REASON_EXPIRED);
        } else if (reason == RemoveReason.REMOVED_LOW_PRIORITY) {
            props.put(DMQ.UNDELIVERED_REASON,
                 DMQ.REASON_LOW_PRIORITY);
        } else if (reason == RemoveReason.REMOVED_OLDEST) {
            props.put(DMQ.UNDELIVERED_REASON,
                 DMQ.REASON_OLDEST);
        } else if (reason == RemoveReason.UNDELIVERABLE) {
            props.put(DMQ.UNDELIVERED_REASON,
                 DMQ.REASON_UNDELIVERABLE);
        } else {
            props.put(DMQ.UNDELIVERED_REASON,
                 DMQ.REASON_ERROR);
        }
        if (pr.getBrokerAddress() != null)
            props.put(DMQ.BROKER, pr.getBrokerAddress().toString());
        else
            props.put(DMQ.BROKER, Globals.getMyAddress().toString());

        String deadbkr = (String)packetProps.get(DMQ.DEAD_BROKER);

        if (deadbkr != null)
            props.put(DMQ.DEAD_BROKER, deadbkr);
        else
            props.put(DMQ.DEAD_BROKER, Globals.getMyAddress().toString());

        if (!pr.isLocal()) {
            boolean waitack = !pr.isNoAckRemoteConsumers();
            Globals.getClusterBroadcast().
                acknowledgeMessage(pr.getBrokerAddress(),
                pr.getSysMessageID(), pr.getQueueUID(), 
                ClusterBroadcast.MSG_DEAD, props, waitack);
            return; // done

        }

        // OK ... now create the packet
        Packet newp = new Packet();

        // first make sure we have the room to put it on the
        // queue ... if we dont, an exception will be thrown
        // from queue Message
        boolean route = false;
        PacketReference ref = null;
        try {
            newp.generateSequenceNumber(false);
            newp.generateTimestamp(false);
            newp.fill(p);
            newp.setProperties(props);

            if (truncateBody) {
                newp.setMessageBody(new byte[0]);
            }
            Queue dmq = DL.getDMQ();
            ref = PacketReference.createReference(pstore,
                   newp, dmq.getDestinationUID(), null);
            ref.overrideExpireTime(0);
            ref.clearExpireInfo();
            ref.clearDeliveryTimeInfo();
            ref.setTimestamp(System.currentTimeMillis());
            synchronized (dmq) {
                ref.setSequence(dmq.seqCnt++);
            }
            DL.routeMoveAndForwardMessage(pr, ref, dmq);
        } catch (Exception ex) {
            // depending on the type, we either ignore or throw out
            if (reason == RemoveReason.UNDELIVERABLE ||
                reason ==  RemoveReason.ERROR) {
                    if (ex instanceof BrokerException) {
                        throw (BrokerException) ex;
                    } 
                    throw new BrokerException( 
                        br.getKString( BrokerResources.X_DMQ_MOVE_INVALID), ex);
            }
            if (getDEBUG() || useVerbose) {
                logger.logStack(Logger.WARNING, BrokerResources.W_DMQ_ADD_FAILURE,
                     pr.getSysMessageID().toString(), ex);
            }

        }
        if (  (getDEBUG() || useVerbose) && useDMQforMsg ) {
             String args[] = { pr.getSysMessageID().toString(),
                      pr.getDestinationUID().toString(),
                      lookupReasonString(reason,
                  receivedTime, expiredTime, senderTime) };
             logger.log(Logger.INFO, BrokerResources.I_DMQ_MOVING_TO_DMQ,
                    args); 
        }

        ref.unload();
        
    }

    /**
     * replaces the body of the message, adds the addProps (if not null)
     * and returns a new SysMessageID
     *
     */
    protected PacketReference _replaceMessage(SysMessageID old, 
              Hashtable addProps, byte[] data)
              throws BrokerException, IOException {

        PacketReference ref = DL.get(old, true);
        if (ref == null) {
            throw new BrokerException(
               br.getKString(br.I_MESSAGE_NOT_FOUND, 
                   old, getDestinationUID()), Status.NOT_FOUND);
        }
        if (!ref.checkDeliveryAndSetInReplacing()) {
            if (ref.isInvalid() || ref.isDestroyed() || 
                ref.isExpired() || ref.isAcknowledged() ||
                ref.inRemoval) {
                throw new BrokerException(
                    br.getKString(br.I_MESSAGE_REMOVED_OR_REMOVING,
                        old, getDestinationUID()), Status.CONFLICT);
            }
            if (ref.inReplacing) {
                throw new BrokerException(
                    br.getKString(br.I_MESSAGE_BEING_REPLACED,
                        old, getDestinationUID()), Status.CONFLICT);
            }
            throw new BrokerException(
                br.getKString(br.I_MESSAGE_IN_DELIVERY_TO_CONSUMER,
                    old, getDestinationUID()), Status.CONFLICT);
        }
        try {

        long oldbsize = ref.byteSize();

        ArrayList subs = new ArrayList();
        Consumer c = null;
        Iterator itr = getConsumers();
        while (itr.hasNext()) {
            c = (Consumer)itr.next();
            if (c instanceof Subscription) {
                if (c.unrouteMessage(ref)) {
                    subs.add(c);
                }
            }
        }

        SysMessageID newid = ref.replacePacket(addProps, data);
        destMessages.remove(old);
        DL.removePacketList(old, ref.getDestinationUID(), ref);
        PacketListDMPair dmp = DL.packetlistAdd(newid, ref.getDestinationUID(), ref);
        destMessages.put(newid, ref);
        dmp.nullRef();
        DL.adjustTotalBytes(ref.byteSize() - oldbsize);

        Subscription sub = null;
        itr = subs.iterator();
        while (itr.hasNext()) {
            sub = (Subscription)itr.next();
            sub.routeMessage(ref, false);
        }

        } finally {
        ref.clearInReplacing();
        }

        return ref;
    }

    public SysMessageID replaceMessage(SysMessageID old, Hashtable addProps,
                          byte[] data)
        throws BrokerException, IOException
    {
        return _replaceMessage(old, addProps, data).getSysMessageID();
    }

    public String replaceMessageString(SysMessageID old, Hashtable addProps,
                          byte[] data)
        throws BrokerException, IOException
    {
        return _replaceMessage(old, addProps, data).getSysMessageIDString();
    }

    public abstract Set routeAndMoveMessage(PacketReference oldRef, 
                  PacketReference newRef)
	throws IOException, BrokerException;
    

    public void setReconnectInterval(long val) 
    {
        clientReconnectInterval = val*RECONNECT_MULTIPLIER;
    }

    public void clientReconnect() {
        synchronized(this) {
            if (reconnectReaper != null) {
                reconnectReaper.cancel();
                reconnectReaper = null;
             }
        }
    }

    private void updateProducerBatch(boolean notifyProducers)
    {
        int oldsize = producerMsgBatchSize;
        long oldbytes = producerMsgBatchBytes;

        notifyProducers = notifyProducers && 
                    limit == DestLimitBehavior.FLOW_CONTROL;
    
        if (limit == DestLimitBehavior.FLOW_CONTROL) {
            producerMsgBatchSize = DL.calcProducerBatchCnt(
                                 destMessages.capacity(),
                                 maxProducerLimit);
            producerMsgBatchBytes = DL.calcProducerBatchBytes(
                                 destMessages.byteCapacity(),
                                 maxProducerLimit);
        } else {
            producerMsgBatchSize = DestinationList.MAX_PRODUCER_BATCH;
            producerMsgBatchBytes =  -1;
        }
        if (notifyProducers && (oldsize != producerMsgBatchSize ||
            oldbytes != producerMsgBatchBytes) ) {
            producerFlow.updateAllProducers(DEST_UPDATE, "update batch");
        }
    }

    class RemoveBehaviorListener implements
             com.sun.messaging.jmq.util.lists.EventListener
        {

            Set orderedSet = null;
            Reason r = null;
            public RemoveBehaviorListener(Set orderedSet, Reason r) {
                this.orderedSet = orderedSet;
                this.r = r;
            }
 
            public void eventOccured(EventType type,  Reason reason,
                Object target, Object orig_value, Object cur_value, 
                Object userdata) 
            {
                assert type == EventType.SET_CHANGED_REQUEST;
                boolean full = destMessages.isFull();
                if (full && cur_value != null) {
                    long tbytes = ((Sized)cur_value).byteSize();
                    while (true) {
                        Iterator itr = null;
                        synchronized (orderedSet) {
                            itr = new LinkedHashSet(orderedSet).iterator();
                        }
                        if (!itr.hasNext()) {
                            break;
                        }
                        Object n = itr.next();
                        if (n == null) {
                            continue;
                        }
                        try {
                            Destination.this.removeMessage(
                               ((PacketReference)n).getSysMessageID(), r);
                        } catch (Exception ex) {
                            logger.logStack(Logger.WARNING, ex.getMessage(), ex);
                            itr.remove();
                            continue;
                        }
                        if ((destMessages.capacity()== -1 || 
                                     destMessages.freeSpace() > 0 ) && 
                            (destMessages.byteCapacity() == -1 ||  
                                     destMessages.freeBytes() > tbytes)) {
                            break;
                        }
                    }
                }
            }
        };

    class FlowListener implements com.sun.messaging.jmq.util.lists.EventListener
        {
            public void eventOccured(EventType type,  Reason reason,
                Object target, Object orig_value, Object cur_value, 
                Object userdata) 
            {

                if (reason instanceof RemoveReason)
                    return;
                assert type == EventType.FULL;
                if (reason != AddReason.LOADED) {
                    assert cur_value instanceof Boolean;
                    boolean shouldStop = destMessages.isFull();
                    if (shouldStop) {
                        logger.log(Logger.DEBUG, "Destination " 
                             + Destination.this + " is full, "
                             + " all producers should be stopped");

                         // for misbehaving producers, we may want to
                         // force a stop
                         producerFlow.updateAllProducers(DEST_PAUSE, 
                             "Destination Full");
                    } else {

                        logger.log(Logger.DEBUG, "Destination " 
                             + Destination.this + " is not full, "
                             + " some producers should be stopped");

                         producerFlow.checkResumeFlow(null, true);

                    }
                }

            }
        };

    transient Object behaviorListener = null;


    protected boolean sendClusterUpdate()
    {
        return !isInternal() && !isAdmin();
    }


    protected void handleLimitBehavior(int limit) {
        if (limit == DestLimitBehavior.FLOW_CONTROL) {
            destMessages.enforceLimits(false);
            FlowListener rl = new FlowListener();
            if (behaviorListener != null) {
                 destMessages.removeEventListener(behaviorListener);
                 behaviorListener = null;
            }
            behaviorListener = destMessages.addEventListener(rl,  EventType.FULL,
                   null);
            producerFlow.updateAllProducers(DEST_BEHAVIOR_CHANGE, "behavior change");
        } else if (limit == DestLimitBehavior.REMOVE_OLDEST) {
            Set s = destMessages.subSet(new OldestComparator());
            RemoveBehaviorListener rl = new RemoveBehaviorListener(s,
                          RemoveReason.REMOVED_OLDEST);
            if (behaviorListener != null) {
                 destMessages.removeEventListener(behaviorListener);
                 behaviorListener = null;
            }
            behaviorListener = destMessages.addEventListener(rl,  EventType.SET_CHANGED_REQUEST,
                   null);
            destMessages.enforceLimits(false);
        } else if (limit == DestLimitBehavior.REJECT_NEWEST) {
            destMessages.enforceLimits(true);
            if (behaviorListener != null) {
                 destMessages.removeEventListener(behaviorListener);
                 behaviorListener = null;
            }
        } else if (limit == DestLimitBehavior.REMOVE_LOW_PRIORITY) {
            destMessages.enforceLimits(false);
            Set s = destMessages.subSet(new LowPriorityComparator());
            RemoveBehaviorListener rl = new RemoveBehaviorListener(s,
                    RemoveReason.REMOVED_LOW_PRIORITY);
            if (behaviorListener != null) {
                 destMessages.removeEventListener(behaviorListener);
                 behaviorListener = null;
            }
            behaviorListener = destMessages.addEventListener(rl,  EventType.SET_CHANGED_REQUEST,
                   null);
        }
    }

    class DestReaperTask extends TimerTask
    {
        DestinationUID uid = null;
        private boolean canceled = false;
        Logger logger = Globals.getLogger();

        public  DestReaperTask(DestinationUID uid) {
            this.uid = uid;
        }
        public synchronized boolean cancel() {
            canceled = true;
            return super.cancel();
        }
        public void run() {
            synchronized(this) {
                if (!canceled) {
                    canceled = true;
                } else {
                    return;
                }
            }

            try {
                Destination d = DL.getDestination(uid);
                if (d == null) {
                    return;
                }

                // Re-verify that the destination can be removed
                synchronized(d) {
                    if (!d.shouldDestroy()) {
                        return;
                    }
                }

                synchronized (DL.destinationList) {
                    if (d.getRefCount() > 0) {
                        return;
                    }
                    int level = (DestType.isAdmin(d.getType()) 
                           ? Logger.DEBUG : Logger.INFO);
                    logger.log(level,
                         BrokerResources.I_AUTO_DESTROY, 
                          uid.getLocalizedName(), String.valueOf(DL.AUTOCREATE_EXPIRE/1000));
                    d.destvalid = false;
                    DL.removeDestination(uid, false,
                        Globals.getBrokerResources().getString(
                        BrokerResources.M_AUTO_REAPED));
                }
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING,
                           BrokerResources.X_REMOVE_DESTINATION_FAILED,
                           uid.getLocalizedName(), ex);
            }
        }
    }

    class ReconnectReaperTask extends TimerTask
    {
        DestinationUID uid = null;
        private boolean canceled = false;
        private long time = 0;

        public  ReconnectReaperTask(DestinationUID uid, long time) {
            this.uid = uid;
            this.time = time;
        }
        public synchronized boolean cancel() {
            canceled = true;
            return super.cancel();
        }
        public void run() {
            synchronized(this) {
                if (canceled) {
                    return;
                }
            }
            Globals.getLogger().log(Logger.DEBUG,
                "Destroying temp destination "+ uid + 
                " inactive for " + (time/1000) + " seconds");

            try {
                DL.removeDestination(uid, false,
                    Globals.getBrokerResources().getString(
                        BrokerResources.M_RECONNECT_TIMEOUT));
            } catch (Exception ex) {
                if (BrokerStateHandler.isShuttingDown()) {
                    Globals.getLogger().log(Logger.INFO,
                        BrokerResources.X_REMOVE_DESTINATION_FAILED,
                        uid.getLocalizedName(), ex);
                } else {
                    Globals.getLogger().logStack(Logger.WARNING,
                        BrokerResources.X_REMOVE_DESTINATION_FAILED,
                        uid.getLocalizedName(), ex);
                }
            }
        }
    }

    transient MsgExpirationReaper expireReaper = new MsgExpirationReaper();

    class MsgExpirationReaper
    {
        SortedSet messages = null;
        TimerTask mytimer = null;

        public  MsgExpirationReaper() {
            messages = new TreeSet(ExpirationInfo.getComparator());
        }

        public synchronized void addExpiringMessage(ExpirationInfo ei) {
            messages.add(ei);
            if (mytimer == null) {
                addTimer();
            }
        }

        public synchronized void removeMessage(ExpirationInfo ei) {
            boolean rem  = messages.remove(ei);
            //assert rem;
            if (rem && messages.isEmpty()) {
                removeTimer();
            }
        }

        public synchronized void destroy() {
            if (mytimer != null) {
                removeTimer();
            }
            messages.clear();            
        }
 
        void addTimer()
        {
            assert Thread.holdsLock(this);
            assert mytimer == null;
            mytimer = new MyExpireTimerTask();
            try {
                timer.schedule(mytimer, DL.MESSAGE_EXPIRE, DL.MESSAGE_EXPIRE);
            } catch (IllegalStateException ex) {
                logger.log(Logger.INFO, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Canceling message expiration on " + this, ex);
            }
        }

        void removeTimer()
        {
            assert Thread.holdsLock(this);
            try {
                if (mytimer != null)
                    mytimer.cancel();
            } catch (IllegalStateException ex) {
                logger.logStack(Logger.DEBUG,"timer canceled ", ex);
            }
            mytimer = null;
        }

        class MyExpireTimerTask extends TimerTask
        {
            public void run() {
                long currentTime = System.currentTimeMillis();
                int removedCount = 0;
                int indeliveryCount = 0;

                LinkedHashSet removed = new LinkedHashSet();
                DestinationUID duid = Destination.this.uid;
                synchronized(MsgExpirationReaper.this) {
                    Iterator itr = messages.iterator();
                    while (itr.hasNext()) {
                        ExpirationInfo ei = (ExpirationInfo)itr.next();
                        if (ei.getExpireTime() > currentTime) {
                            if (FI.FAULT_INJECTION) {
                                if (FI.checkFault(FI.FAULT_MSG_EXPIRE_REAPER_EXPIRE1, null)) {
                                    FI.unsetFault(FI.FAULT_MSG_EXPIRE_REAPER_EXPIRE1);
                                    removed.add(ei);
                                    continue;
                                }
                            }
                            break;
                        }
                        removed.add(ei);
                   }
                }

                // we dont want to do this inside the loop because
                // removeExpiredMessage can generate a callback which
                // can generate a deadlock .. bummer
                Iterator itr = removed.iterator();
                while (itr.hasNext()) {
                    ExpirationInfo ei = (ExpirationInfo)itr.next();
                    try {
                        ei.incrementReapCount();
                        RemoveMessageReturnInfo ret = removeExpiredMessage(duid, ei.id);
                        if (ret.removed) {
                           removeMessage(ei);
                           removedCount++;
                        } else if (ret.indelivery) {
                            indeliveryCount++;
                        } else if (ei.getReapCount() > 1) {
                           removeMessage(ei);
                           removedCount++;
                        }
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, ex.getMessage(), ex);
                    }
                    
                }
                if (removedCount > 0) {
                    logger.log(Logger.INFO, BrokerResources.I_REMOVE_DSTEXP_MSGS,
                               String.valueOf(removedCount), duid.getLocalizedName());
                }
                if (indeliveryCount > 0) {
                    logger.log(Logger.INFO, BrokerResources.I_NUM_MSGS_INDELIVERY_NOT_EXPIRED_FROM_DEST,
                               String.valueOf(indeliveryCount), duid.getLocalizedName());
                }
                removed.clear();
            }
        }
    }


    class DestFilter implements Filter
     {
        public boolean matches(Object o) {
            return uid.equals(((PacketReference)o).getDestinationUID());
        }
        public boolean equals(Object o) {
             return super.equals(o);
        }
        public int hashCode() {
             return super.hashCode();
        }
     }

    protected transient Filter filter = new DestFilter();
    protected transient DestMetricsCounters dmc = new DestMetricsCounters();

    protected transient MessageDeliveryTimeTimer deliveryTimeTimer = null;

    protected synchronized void initialize() {
        try {
          if (stored) {
              synchronized(sizeLock) {
                  int oldsize = size;
                  long oldbytes = bytes;
                  HashMap data = pstore.getMessageStorageInfo(this);
                  size = ((Integer)data.get(DestMetricsCounters.CURRENT_MESSAGES)).intValue();
                  bytes = ((Long)data.get(DestMetricsCounters.CURRENT_MESSAGE_BYTES)).longValue();
                  size += remoteSize;
                  bytes += remoteBytes;
                  int sizediff = size - oldsize;
                  long bytediff = bytes - oldbytes;
                  if (!isAdmin() && (getIsDMQ() || !isInternal())) {
                      DL.adjustTotals(sizediff, bytediff);
                  }
              }
          }
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
            br.getKString(br.E_GET_MSG_METRICS_FROM_STORE_FAIL, this), ex);
        }
        dest_inited = true;
    }

    protected boolean isDestInited() {
        return dest_inited;
    }

    // used during upgrade
    public void initializeOldDestination() {
        overridePersistence(true);
        stored = true;
        dest_inited = false;
        loaded = false;
    }

    public boolean getIsDMQ() {
        return isDMQ;
    }


    /**
     * handles transient data when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException {

        ois.defaultReadObject();
        logger = Globals.getLogger();
        br = Globals.getBrokerResources();
        DL = Globals.getDestinationList();
        currentChangeRecordInfo = Collections.synchronizedMap(
                           new HashMap<Integer, ChangeRecordInfo>());
        producerFlow = new ProducerFlow();
        isDMQ = DestType.isDMQ(type);
        if (!isDMQ) {
            expireReaper = new MsgExpirationReaper();
        }
        dest_inited = false;
        loaded = false;
        destvalid = true;

        sizeLock = new Object();
        size = 0;
        remoteSize = 0;
        bytes = 0L;
        remoteBytes = 0L;

        destMessages = new SimpleNFLHashMap();
        destMessagesInRemoving = new HashMap();
        _removeMessageLock = new Object();
        consumers = new SimpleNFLHashMap();
        producers = new SimpleNFLHashMap();
        if (maxConsumerLimit > DestinationList.UNLIMITED) {
            consumers.setCapacity(maxConsumerLimit);
        }
        if (maxProducerLimit > DestinationList.UNLIMITED) {
            producers.setCapacity(maxProducerLimit);
        }
        filter = new DestFilter() ;
        unloadfilter = new UnloadFilter();
        dmc = new DestMetricsCounters();
        stored = true;
        setMaxPrefetch(maxPrefetch);
        logsuffix = "";
        queueRemoteLock = new Object();
        queueRemoteLockThread = null;

        // when loading a stored destination, we must 
        // set the behavior first OR we will not be notified
        // that the destination is full IF it already exceeds
        // its limits

        handleLimitBehavior(limit);
        if (memoryLimit != null) {
            setByteCapacity(memoryLimit);
        }
        if (countLimit > 0) {
            setCapacity(countLimit);
        }
        if (msgSizeLimit != null) {
            setMaxByteSize(msgSizeLimit);
        }

        if (!isDMQ) {
            deliveryTimeTimer = new MessageDeliveryTimeTimer(this);
        }
        updateProducerBatch(false);
        if (clientReconnectInterval > 0) {
            synchronized(this) {
                if (clientReconnectInterval > 0) {
                    reconnectReaper = new ReconnectReaperTask(
                        getDestinationUID(), clientReconnectInterval);
                    try {
                    timer.schedule(reconnectReaper, clientReconnectInterval);
                    } catch (IllegalStateException ex) {
                        logger.logStack(Logger.INFO,
                          BrokerResources.E_INTERNAL_BROKER_ERROR,
                                "Can not reschedule task, timer has "
                              + "been canceled, the broker is probably "
                              + "shutting down", ex);
                    }
                 }
            }
        }
        logger.log(Logger.DEBUG,"Loading Stored destination " + this + " connectionUID=" + id);
    }

    protected void initMonitor()
        throws IOException
    {
        if (DestType.isInternal(type)) {
            if (!DestType.destNameIsInternalLogging(getDestinationName())) {
                if (!DL.CAN_MONITOR_DEST) {
                    throw new IOException(
                                Globals.getBrokerResources().getKString(
                                BrokerResources.X_FEATURE_UNAVAILABLE,
                                   Globals.getBrokerResources().getKString(
                                          BrokerResources.M_MONITORING), getName()));
                }
                try {
                    bm = new BrokerMonitor(this);
                } catch (IllegalArgumentException ex) {
                    logger.logStack(Logger.INFO, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Unknown Monitor destination " 
                             + getDestinationName(), ex);
                } catch (BrokerException ex) {
                    logger.logStack(Logger.INFO, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "Monitor destination Error  " 
                             + getDestinationName(), ex);
                }
            }
        }
    }

    protected void initVar() {
    }

    protected Destination(String destination, int type, 
        boolean store, ConnectionUID id,
        boolean autocreate, DestinationList dl) 
        throws FeatureUnavailableException, BrokerException, IOException {

        this.uid = new DestinationUID( 
                   destination,DestType.isQueue(type));
        initVar();

        if (this.uid.isWildcard()) {
             throw new RuntimeException("Do not create wildcards");
        }
        this.id = id;
        setDestinationList(dl);
        producers.setCapacity(maxProducerLimit);
        consumers.setCapacity(maxConsumerLimit);
        destMessages = new SimpleNFLHashMap();
        destMessagesInRemoving = new HashMap();
        _removeMessageLock = new Object();
        destMessages.enforceLimits(true);
        if (autocreate) {
            if (!DestType.isAdmin(type)) {
                if (DL.defaultMaxMsgCnt > 0) {
                    setCapacity(DL.defaultMaxMsgCnt);
                }
                setByteCapacity(DL.defaultMaxMsgBytes);
                setMaxByteSize(DL.defaultMaxBytesPerMsg);
                setLimitBehavior(DL.defaultLimitBehavior);
                if (DL.defaultIsLocal) {
                    setScope(DestScope.LOCAL);
                }
            }
            if (!DestType.isAdmin(type) && 
                !DL.canAutoCreate(DestType.isQueue(type),type) && 
                !BrokerMonitor.isInternal(destination)) {

                throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.W_DST_NO_AUTOCREATE,
                         getName()),
                        BrokerResources.W_DST_NO_AUTOCREATE,
                        (Throwable) null,
                        Status.FORBIDDEN);
            } else  {
                int level = (DestType.isAdmin(type) ? Logger.DEBUG : Logger.INFO);
                logger.log(level, BrokerResources.I_AUTO_CREATE, getName());
            }
            this.type = (type | DestType.DEST_AUTO);
        } else {
            int level = (DestType.isAdmin(type) ? Logger.DEBUG : Logger.INFO);
            this.type = type;
            if ((type & DestType.DEST_TEMP) == DestType.DEST_TEMP) {
                logger.log(level, BrokerResources.I_DST_TEMP_CREATE,
                    (id == null ? "<none>" : id.toString()), getName());
            } else {
                logger.log(level, BrokerResources.I_DST_ADMIN_CREATE, getName());
            }
        }
        if ((type & DestType.DEST_DMQ) == 0 && BrokerMonitor.isInternal(destination)) {
            if (DestType.isQueue(type)) {
                throw new BrokerException("Internal Exception: "
                        + "Only topics are supported for monitoring");
            }
            this.type = (type | DestType.DEST_INTERNAL);
            setScope(scope);
            try {
                if (!DestType.destNameIsInternalLogging(getDestinationName())) {
                    if (!DL.CAN_MONITOR_DEST) {
                        throw new BrokerException(
                            br.getKString(
                                BrokerResources.X_FEATURE_UNAVAILABLE,
                                   Globals.getBrokerResources().getKString(
                                          BrokerResources.M_MONITORING), getName()),
                                BrokerResources.X_FEATURE_UNAVAILABLE,
                                (Throwable) null,
                                Status.FORBIDDEN);
                       
                    }
                    bm = new BrokerMonitor(this);
                }
            } catch (IllegalArgumentException ex) {
                throw new BrokerException(
                    br.getKString(BrokerResources.W_UNKNOWN_MONITOR, getName()),
                    BrokerResources.W_UNKNOWN_MONITOR, ex, Status.BAD_REQUEST);
            }
        }
        
        loaded = true;
        if (!store) {
            neverStore = true;
            overridePersistence(false);
        }

        deliveryTimeTimer = new MessageDeliveryTimeTimer(this);

        // NOW ATTACH ANY WILDCARD PRODUCERS OR CONSUMERS
        Iterator itr = Consumer.getWildcardConsumers();
        while (itr.hasNext()) {
            ConsumerUID cuid = (ConsumerUID) itr.next();
            Consumer c = Consumer.getConsumer(cuid);
            if (c == null){
                logger.log(Logger.INFO,"Consumer [" + cuid + "] for destination [" + this.getName() + "] already destroyed.");
                continue;
            }
            DestinationUID wuid = c.getDestinationUID();
            // compare the uids
            if (DestinationUID.match(getDestinationUID(), wuid)) {
                try {
                // attach the consumer
                    if (c.getSubscription() != null) {
                        addConsumer(c.getSubscription(), false /* XXX- TBD*/);
                    } else {
                        // if this destination was just added we may do
                        // this twice but thats OK because we are just
                        // adding to a hashset

                        c.attachToDestination(this, pstore);
                    }


                } catch (SelectorFormatException ex) {
                   //LKS TBD
                }
            }
        }
        handleLimitBehavior(limit);
        updateProducerBatch(false);
        state = DestState.RUNNING;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public DestinationUID getDestinationUID() {
        return uid;
    }

    public void pauseDestination(int type) {
        assert type != DestState.UNKNOWN;
        assert type != DestState.RUNNING;
        assert type <= DestState.PAUSED;
        //assert state == DestState.RUNNING;
	int oldstate = state;
	boolean pauseCon = false, pauseProd = false;
	boolean resumeCon = false, resumeProd = false;

	/*
	 * If requested state matches existing, return right away
	 */
	if (oldstate == type)  {
	    return;
	}

	if (oldstate == DestState.RUNNING)  {
            if (type == DestState.PRODUCERS_PAUSED || 
                type == DestState.PAUSED) {
		/*
		 * Old state = RUNNING, new state = PRODUCERS_PAUSED or PAUSED
		 *  - pause producers
		 */
		pauseProd = true;
	    }
            if (type == DestState.CONSUMERS_PAUSED ||
                type == DestState.PAUSED) {
		/*
		 * Old state = RUNNING, new state = CONSUMERS_PAUSED or PAUSED
		 *  - pause consumers
		 */
		pauseCon = true;
	    }
	}  else if (oldstate == DestState.PAUSED)  {
	    if (type == DestState.CONSUMERS_PAUSED)  {
		/*
		 * Old state = PAUSED, new state = CONSUMERS_PAUSED
		 *  - resume producers
		 */
		resumeProd = true;
	    } else if (type == DestState.PRODUCERS_PAUSED)  {
		/*
		 * Old state = PAUSED, new state = PRODUCERS_PAUSED
		 *  - resume consumers
		 */
		resumeCon = true;
	    }
	} else if (oldstate == DestState.CONSUMERS_PAUSED)  {
	    if (type == DestState.PAUSED)  {
		/*
		 * Old state = CONSUMERS_PAUSED, new state = PAUSED
		 *  - pause producers
		 */
		pauseProd = true;
	    } else if (type == DestState.PRODUCERS_PAUSED)  {
		/*
		 * Old state = CONSUMERS_PAUSED, new state = PRODUCERS_PAUSED
		 *  - resume consumers
		 *  - pause producers
		 */
		resumeCon = true;
		pauseProd = true;
	    }
	} else if (oldstate == DestState.PRODUCERS_PAUSED)  {
	    if (type == DestState.PAUSED)  {
		/*
		 * Old state = PRODUCERS_PAUSED, new state = PAUSED
		 *  - pause consumers
		 */
		pauseCon = true;
	    } else if (type == DestState.CONSUMERS_PAUSED)  {
		/*
		 * Old state = PRODUCERS_PAUSED, new state = CONSUMERS_PAUSED
		 *  - pause consumers
		 *  - resume producers
		 */
		pauseCon = true;
		resumeProd = true;
	    }
	}

        state = type;

	if (resumeProd)  {
             producerFlow.updateAllProducers(DEST_RESUME, "Destination is resumed");
	}
	if (resumeCon)  {
           synchronized(consumers) {
               Iterator itr = consumers.values().iterator();
               while (itr.hasNext()) {
                   Consumer c = (Consumer)itr.next();
                   c.resume("Destination.RESUME");
               }
           }
	}

        if (pauseProd) {
             producerFlow.updateAllProducers(DEST_PAUSE, "Destination is paused");
        }
        if (pauseCon) {
           synchronized(consumers) {
               Iterator itr = consumers.values().iterator();
               while (itr.hasNext()) {
                   Object o = itr.next();
                   Consumer c = (Consumer)o;
                   c.pause("Destination PAUSE");
               }
           }
        }

	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.notifyDestinationPause(this, type);
	}
    }

    public boolean isPaused() {
        return (state > DestState.RUNNING &&
               state <= DestState.PAUSED);
    }

    public void resumeDestination() {
        assert (state > DestState.RUNNING &&
               state <= DestState.PAUSED);
        int oldstate = state;
        state = DestState.RUNNING;
        if (oldstate == DestState.PRODUCERS_PAUSED ||
            oldstate == DestState.PAUSED) {
             producerFlow.updateAllProducers(DEST_RESUME, "Destination is resumed");
        }
        if (oldstate == DestState.CONSUMERS_PAUSED ||
            oldstate == DestState.PAUSED) {
           synchronized(consumers) {
               Iterator itr = consumers.values().iterator();
               while (itr.hasNext()) {
                   Consumer c = (Consumer)itr.next();
                   c.resume("Destination.RESUME");
               }
           }
        }

	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.notifyDestinationResume(this);
	}
        
    }

    /**
     * Compact the message file.
     */
    public void compact() throws BrokerException {
        if (!(pstore instanceof DiskFileStore)) {
            throw new BrokerException("XXXI18N - operation not supported");
        }
	((DiskFileStore)pstore).compactDestination(this);
	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.notifyDestinationCompact(this);
	}
    }

    transient long lastMetricsTime;
    transient long msgsIn = 0L;
    transient long msgsOut = 0L;
    transient long lastMsgsIn = 0L;
    transient long lastMsgsOut = 0L;
    transient long msgBytesIn = 0;
    transient long msgBytesOut = 0;
    transient long lastMsgBytesIn = 0;
    transient long lastMsgBytesOut = 0;
    transient long msgsInInternal = 0;
    transient long msgsOutInternal = 0;
    transient long msgsInOutLastResetTime = 0;

    public void resetMetrics() {
        synchronized(dmc) {
            expiredCnt = 0;
            purgedCnt = 0;
            ackedCnt = 0;
            discardedCnt = 0;
            overflowCnt = 0;
            errorCnt = 0;
            msgsIn = 0L;
            msgsOut = 0L;
            lastMsgsIn = 0L;
            lastMsgsOut = 0L;
            msgBytesIn = 0L;
            msgBytesOut = 0;
            lastMsgBytesIn = 0;
            lastMsgBytesOut = 0;
            destMessages.reset();
            consumers.reset();
        }
    }
    public DestMetricsCounters getMetrics() {
        synchronized(dmc) {

            long currentTime = System.currentTimeMillis();
	    /*
            long timesec = (currentTime - lastMetricsTime)/
                      1000;
	    */
    
	    // time metrics was calculated
	    dmc.timeStamp = currentTime;

            // total messages sent to the destination
            dmc.setMessagesIn(msgsIn);
    
            // total messages sent from the destination
            dmc.setMessagesOut(msgsOut);
    
            // largest size of destination since broker started
            // retrieved from destination
            dmc.setHighWaterMessages((int)destMessages.highWaterCount());
    
            // largest bytes of destination since broker started
            // retrieved from destination
            dmc.setHighWaterMessageBytes(destMessages.highWaterBytes());
    
            // largest message size
            dmc.setHighWaterLargestMsgBytes(
                destMessages.highWaterLargestMessageBytes());
    
            // current # of active consumers 
            dmc.setActiveConsumers(consumers.size());
            dmc.setNumConsumers(consumers.size());
    
            // current # of failover consumers 
            // only applies to queues
            dmc.setFailoverConsumers((int)0);
    
            // max # of active consumers
            dmc.setHWActiveConsumers(consumers.highWaterCount());
            dmc.setHWNumConsumers(consumers.highWaterCount());
    
            // max # of failover consumers
            dmc.setHWFailoverConsumers((int)0);
    
            // avg active consumer
            dmc.setAvgActiveConsumers((int)consumers.averageCount());
            dmc.setAvgNumConsumers((int)consumers.averageCount());
    
            // avg failover consumer
            dmc.setAvgFailoverConsumers((int)(int)0);
    
            // total messages bytes sent to the destination
            dmc.setMessageBytesIn(msgBytesIn);
    
            // total messages bytes sent from the destination
            dmc.setMessageBytesOut(msgBytesOut);
    
            // current size of the destination
            dmc.setCurrentMessages(destMessages.size());
    
            // current size (in bytes) of the destination
            dmc.setCurrentMessageBytes(destMessages.byteSize());
    
            // avg size of the destination
            dmc.setAverageMessages((int)destMessages.averageCount());
    
            // avg size (in bytes) of the destination
            dmc.setAverageMessageBytes((long)destMessages.averageBytes());

	    // get disk usage info
            if (isStored()) {

	        try {
		    if (Globals.getStore().getStoreType().equals(Store.FILE_STORE_TYPE)) {

			HashMap map = ((DiskFileStore)pstore).getStorageInfo(this);
			Object obj = null;
			if ((obj = map.get(dmc.DISK_RESERVED)) != null) {
		            dmc.setDiskReserved(((Long)obj).longValue());
			}
			if ((obj = map.get(dmc.DISK_USED)) != null) {
		            dmc.setDiskUsed(((Long)obj).longValue());
			}
			if ((obj = map.get(dmc.DISK_UTILIZATION_RATIO)) != null)
			{
		            dmc.setUtilizationRatio(((Integer)obj).intValue());
			}
		    }
	        } catch (BrokerException e) {
		    logger.log(Logger.ERROR, e.getMessage(), e);
	        }
            }

            dmc.setExpiredMsgCnt(expiredCnt);
            dmc.setPurgedMsgCnt(purgedCnt);
            dmc.setAckedMsgCnt(ackedCnt);
            dmc.setDiscardedMsgCnt(discardedCnt);
            dmc.setRejectedMsgCnt(overflowCnt + errorCnt);
            dmc.setRollbackMsgCnt(rollbackCnt);

            lastMetricsTime = currentTime;
            lastMsgsIn = msgsIn;
            lastMsgsOut = msgsOut;
            lastMsgBytesIn = msgBytesIn;
            lastMsgBytesOut = msgBytesOut;
    
            return dmc;        

        }
    }

    //return 0, yes; 1 no previous sampling, else no
    public int checkIfMsgsInRateGTOutRate(long[] holder, boolean sampleOnly) {
        if (sampleOnly) {
            synchronized(dmc) {
                holder[0] = msgsInInternal;
                holder[1] = msgsOutInternal;
            }
            holder[2] = System.currentTimeMillis();
            holder[3] = -1;
            holder[4] = -1;
            holder[5] = -1;
            return 1;
        }
        long myins = holder[0];
        long myouts = holder[1];
        long mylastTimeStamp = holder[2];
        long myinr = holder[3];
        long myoutr = holder[4];
        long currtime = System.currentTimeMillis();
        if ((currtime - mylastTimeStamp) < 1000L) {
            if (myinr < 0 || myoutr < 0) {
                return 1; 
            }
            return (myinr > myoutr ? 0:2);
        }
        //long myoutt = holder[5];

        holder[2] = currtime;
        synchronized(dmc) {
            holder[0] = msgsInInternal;
            holder[1] = msgsOutInternal;
        }
        if (msgsInOutLastResetTime >= mylastTimeStamp) {
            return 1;
        }
        long mt = holder[2] - mylastTimeStamp;
        long st = mt/1000L;
        if (st <= 0) {
            return 1;
        }
        long outdiff = holder[1] - myouts; 
        holder[3] = (holder[0] - myins)/st;
        holder[4] = outdiff/st;
        if (outdiff > 0) {
            holder[5] = mt/outdiff;
        }
        if (holder[3] < 0 || holder[4] < 0) {
            return 1;
        }
        return ((holder[3] > holder[4]) ? 0:2);
    }


    protected void decrementDestinationSize(PacketReference ref) {
        long objsize = ref.byteSize();
        boolean local = ref.isLocal();
        boolean persistent = ref.isPersistent();

        synchronized (sizeLock) {
            size --;
            bytes -= objsize;
            if (!local) {
                remoteSize --;
                remoteBytes -= objsize;
            }
            if (!isAdmin() && (getIsDMQ() || !isInternal())) {
                DL.decrementTotals(objsize, !persistent && !getIsDMQ());
            }
        }
    }

    protected void incrementDestinationSize(PacketReference ref) {
        long objsize = ref.byteSize();
        boolean local = ref.isLocal();
        boolean persistent = ref.isPersistent();

        synchronized(sizeLock) {
            size ++;
            bytes += objsize;
            if (!local) {
                remoteSize ++;
                remoteBytes += objsize;
            }
            if (!isAdmin() && (getIsDMQ() || !isInternal())) {
                DL.incrementTotals(objsize, !persistent && !getIsDMQ());
            }
        }
    }

    public int getState() {
        return state;
    }

    protected void setState(int state) {
        this.state = state;
    }

    public void setIsLocal(boolean isLocal) 
        throws BrokerException
    {
        int scopeval = 0;
        if (isLocal) {
            scopeval = DestScope.LOCAL;
        } else {
            scopeval = DestScope.CLUSTER;
        }
        setScope(scopeval);
    }

    public void setScope(int scope)
        throws BrokerException
    {
        if (!DL.CAN_USE_LOCAL_DEST && scope == DestScope.LOCAL) {
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_FEATURE_UNAVAILABLE,
                    Globals.getBrokerResources().getKString(
                        BrokerResources.M_LOCAL_DEST), getName()),
                    BrokerResources.X_FEATURE_UNAVAILABLE,
                    (Throwable) null,
                    Status.FORBIDDEN);
        }
        this.scope = scope;
    }

    public int getScope() {
        return this.scope;
    }

    public boolean getIsLocal() {
        return (scope  == DestScope.LOCAL) ;
    }

    public void setLimitBehavior(int behavior) 
        throws BrokerException
    {
        if (isDMQ && behavior == DestLimitBehavior.FLOW_CONTROL) {
            throw new BrokerException(
                br.getKString(BrokerResources.X_DMQ_INVAID_BEHAVIOR));
        }

	Integer oldVal = Integer.valueOf(this.limit);

        this.limit = behavior;
        handleLimitBehavior(limit);

        notifyAttrUpdated(DestinationInfo.DEST_LIMIT, 
			oldVal, Integer.valueOf(this.limit));
    }

    public int getLimitBehavior() {
        return this.limit;
    }

    public void setClusterDeliveryPolicy(int policy) {
        throw new UnsupportedOperationException(
             " cluster delivery policy not supported for this type of destination ");
    }

    public int getClusterDeliveryPolicy() {
        return ClusterDeliveryPolicy.NA;
    }


    public boolean isStored() {
        return !neverStore || stored;
    }

    public synchronized boolean store() 
        throws BrokerException, IOException
    {
        if (neverStore || stored) return false;
        pstore.storeDestination(this, PERSIST_SYNC);
        stored = true;
        return stored;
    }

    public boolean shouldSync() {
        return PERSIST_SYNC;
    }

    public void update()  
        throws BrokerException, IOException
    {
        update(true);
    }

    public void update(boolean notify) 
        throws BrokerException, IOException
    {
        boolean should_notify =
            !getIsDMQ() && notify
            && sendClusterUpdate() && !isTemporary();

        if (should_notify) {
            Globals.getClusterBroadcast().recordUpdateDestination(this);
        }

        if (!neverStore  && stored)  {
            pstore.updateDestination(this, PERSIST_SYNC);
        }
        updateProducerBatch(true);

        if (should_notify) {
            Globals.getClusterBroadcast().updateDestination(this);
        }
            
    }

    /**
     * used to retrieve properties for sending to
     * remote brokers or for admin support
     */
    public HashMap getDestinationProperties()
    {  
        HashMap m = new HashMap();
        getDestinationProps(m);
        return m;
    }

    private static final String SCOPE_PROPERTY = "scope";
    private static final String MAX_CONSUMERS = "max_consumers";
    private static final String MAX_PRODUCERS = "max_producers";
    private static final String MAX_PREFETCH = "max_prefetch";
    private static final String MAX_MESSAGES = "max_messages";
    private static final String MAX_BYTES = "max_bytes";
    private static final String MAX_MSG_BYTES = "max_msg_bytes";
    private static final String BEHAVIOUR = "behaviour";
    private static final String STATE = "state";
    private static final String NAME = "name";
    private static final String IS_QUEUE = "queue";
    private static final String IS_INTERNAL = "internal";
    private static final String IS_AUTOCREATED = "autocreated";
    private static final String IS_TEMPORARY = "temporary";
    private static final String IS_ADMIN = "admin";
    private static final String IS_LOCAL = "local";
    private static final String REAL_TYPE = "type";
    private static final String USE_DMQ = "useDMQ";
    private static final String VALIDATE_XML_SCHEMA_ENABLED = "validateXMLSchemaEnabled";
    private static final String XML_SCHEMA_URI_LIST = "XMLSchemaUriList";
    private static final String RELOAD_XML_SCHEMA_ON_FAILURE = "reloadXMLSchemaOnFailure";

    protected void getDestinationProps(Map m) {
        m.put(NAME, getDestinationName());
        m.put(IS_QUEUE,Boolean.valueOf(isQueue()));
        m.put(IS_INTERNAL,Boolean.valueOf(isInternal()));
        m.put(IS_AUTOCREATED,Boolean.valueOf(isAutoCreated()));
        m.put(IS_TEMPORARY,Boolean.valueOf(isTemporary()));
        m.put(IS_ADMIN,Boolean.valueOf(isAdmin()));
        m.put(IS_LOCAL,Boolean.valueOf(getIsLocal()));
        m.put(REAL_TYPE,Integer.valueOf(type));
        m.put(SCOPE_PROPERTY,Integer.valueOf(scope));
        m.put(MAX_CONSUMERS,Integer.valueOf(maxConsumerLimit));
        m.put(MAX_PRODUCERS,Integer.valueOf(maxProducerLimit));
        m.put(MAX_PREFETCH,Integer.valueOf(maxPrefetch));
        m.put(MAX_MESSAGES,Integer.valueOf(countLimit));
        m.put(USE_DMQ,Boolean.valueOf(useDMQ));
        if (memoryLimit != null)
            m.put(MAX_BYTES,Long.valueOf(memoryLimit.getBytes()));
        if (msgSizeLimit != null)
            m.put(MAX_MSG_BYTES,Long.valueOf(msgSizeLimit.getBytes()));
        m.put(BEHAVIOUR,Integer.valueOf(limit));
        m.put(STATE,Integer.valueOf(scope));
        m.put(VALIDATE_XML_SCHEMA_ENABLED,Boolean.valueOf(validateXMLSchemaEnabled));
        if (XMLSchemaUriList != null) {
            m.put(XML_SCHEMA_URI_LIST, XMLSchemaUriList);
        }
        m.put(RELOAD_XML_SCHEMA_ON_FAILURE, reloadXMLSchemaOnFailure);
    }

    /**
     * used to update the destination from
     * remote brokers or for admin support
     */
    public void setDestinationProperties(Map m)
        throws BrokerException
    {
        if (getDEBUG())
            logger.log(Logger.DEBUG,"Setting destination properties for "
               + this +" to " + m);
        if (m.get(MAX_CONSUMERS) != null) {
           try {
               setMaxConsumers(((Integer)m.get(MAX_CONSUMERS)).intValue());
           } catch (BrokerException ex) {
               logger.logStack(Logger.WARNING, ex.getMessage(), ex);
           }
        }
        if (m.get(MAX_PRODUCERS) != null) {
           try {
               setMaxProducers(((Integer)m.get(MAX_PRODUCERS)).intValue());
           } catch (BrokerException ex) {
               logger.logStack(Logger.WARNING, ex.getMessage(), ex);
           }
        }
        if (m.get(MAX_PREFETCH) != null) {
            setMaxPrefetch(((Integer)m.get(MAX_PREFETCH)).intValue());
        }
        if (m.get(MAX_MESSAGES) != null) {
            setCapacity(((Integer)m.get(MAX_MESSAGES)).intValue());
        }
        if (m.get(MAX_BYTES) != null) {
            SizeString ss = new SizeString();
            ss.setBytes(((Long)m.get(MAX_BYTES)).longValue());
            setByteCapacity(ss);
        }
        if (m.get(MAX_MSG_BYTES) != null) {
            SizeString ss = new SizeString();
            ss.setBytes(((Long)m.get(MAX_MSG_BYTES)).longValue());
            setMaxByteSize(ss);
        }
        if (m.get(BEHAVIOUR) != null) {
            setLimitBehavior(((Integer)m.get(BEHAVIOUR)).intValue());
        }
        if (m.get(IS_LOCAL) != null) {
            setIsLocal(((Boolean)m.get(IS_LOCAL)).booleanValue());
        }
        if (m.get(USE_DMQ) != null) {
            setUseDMQ(((Boolean)m.get(USE_DMQ)).booleanValue());
        }
        try {
            update(false);
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Unable to update destination " + getName(), ex);
        }

    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "Destination["+uid.toString()+"]");
        getDestinationProps(ht);
        ht.putAll(getMetrics());
        ht.put("Consumers", String.valueOf(consumers.size()));

        Iterator itr = consumers.getAll(null).values().iterator();

        List pfers = null;
        synchronized (destMessages) {
            pfers = new ArrayList(destMessages.values());
        }
        while (itr.hasNext()) {
            Consumer con = (Consumer)itr.next();
            ConsumerUID cuid = con.getConsumerUID();
            ConsumerUID sid = con.getStoredConsumerUID();

            // Format: match[delivered,unacked]
            // OK -> get all messages
            int total = pfers.size();
            int match = 0;
            int delivered = 0;
            int ackno = 0;
            for (int i=0; i < total; i ++) {
                PacketReference ref = (PacketReference)pfers.get(i);
                try {
                    try {
                        if (ref.matches(sid)) {
                            match ++;
                            if (ref.isAcknowledged(sid)) {
                                ackno ++;
                            }
                            if (ref.isDelivered(sid)) {
                                delivered ++;
                            }
                        }
                    } catch (Exception e) {
                        logger.log(logger.INFO, "Destination.getDebugState(): "+this+
                            " for message reference "+ref+" and stored consumer uid "+
                             sid+": "+e.toString());
                    }
                } catch (Exception e) {}
            }
            String ID = match + " of " + total 
              + "[ d=" + delivered
              + ", a=" + ackno + "]";
            
            ht.put("Consumer[" +
                    String.valueOf(cuid.longValue())+ "]" , ID);
        }

        Set s = null;
        synchronized(producers) {
            s = new HashSet(producers.keySet());
        }
        itr = s.iterator();
        Vector v = new Vector();
        while (itr.hasNext()) {
            ProducerUID cuid = (ProducerUID)itr.next();
            v.add(String.valueOf(cuid.longValue()));
        }
        ht.put("Producers", v);
        ht.put("_stored", String.valueOf(stored));
        ht.put("_neverStore", String.valueOf(neverStore));
        ht.put("_destvalid", String.valueOf(destvalid));
        ht.put("_loaded", String.valueOf(loaded));
        ht.put("_state", DestState.toString(state));
        ht.put("producerMsgBatchSize", String.valueOf(producerMsgBatchSize));
        ht.put("producerMsgBatchBytes", String.valueOf(producerMsgBatchBytes));
        if (reconnectReaper != null)
            ht.put("_reconnectReaper",reconnectReaper.toString());
        ht.put("_clientReconnectInterval", String.valueOf(clientReconnectInterval));
        ht.put("TrueType", DestType.toString(type));
        if (id != null)
            ht.put("ConnectionUID", String.valueOf(id.longValue()));
        ht.put("activeProducerCount", String.valueOf(
                producerFlow.activeProducerCnt()));
        ht.put("pausedProducerCount", String.valueOf(
                producerFlow.pausedProducerCnt()));
        ht.put("pausedProducerSet", producerFlow.getDebugPausedProducers());
        ht.put("activeProducerSet", producerFlow.getDebugActiveProducers());
        ht.put("size", Integer.valueOf(size));
        ht.put("bytes", Long.valueOf(bytes));
        ht.put("remoteSize", Long.valueOf(remoteSize));
        ht.put("remoteBytes", Long.valueOf(remoteBytes));
        List sysids = null;
        synchronized (destMessages) {         
            ht.put("destMessagesSize", String.valueOf(destMessages.size()));
            sysids = new ArrayList(destMessages.keySet());
        }
        itr = sysids.iterator();
        v = new Vector();
        PacketReference ref;
        String refs;
        while (itr.hasNext()) {
            SysMessageID sysid = (SysMessageID)itr.next();
            ref = (PacketReference)destMessages.get(sysid);
            refs = "null";
            if (ref != null) {
                refs = "local="+ref.isLocal()+",invalid="+ref.isInvalid()+
                       ",destroyed="+ref.isDestroyed()+",overrided="+ref.isOverrided()+
                       ",overriding="+ref.isOverriding()+",locked="+(ref.checkLock(false)==null); 
            }
            v.add(sysid.toString()+"  ref="+refs);
        }
        ht.put("Messages", v);
        return ht;
    }

    public Hashtable getDebugMessages(boolean full) {
        if (!loaded ) {
            try {
                load();
            } catch (Exception ex) {}
        }

        Vector vt = new Vector();
        try {
            Iterator itr = null;
            synchronized(destMessages) {
            itr = new HashSet(destMessages.values()).iterator();
            }
            while (itr.hasNext()) {
                PacketReference pr = (PacketReference)itr.next();
                Hashtable pht = pr.getDebugState();
                pht.put("ID", pr.getSysMessageID().toString());
                if (full) { 
                    pht.put("PACKET", pr.getPacket().dumpPacketString("        "));
                }
                vt.add(pht);
            }
        } catch (Throwable ex) {
            logger.log(Logger.DEBUG,"Error getting debugMessages ",
                 ex);
        }
        Hashtable ht = new Hashtable();
        ht.put("  ", vt);
        return ht;
        
    }

    public SysMessageID[] getSysMessageIDs() throws BrokerException  {
        return (getSysMessageIDs(null, null));
    }

    public SysMessageID[] getSysMessageIDs(Long startMsgIndex, Long maxMsgsRetrieved) 
					throws BrokerException  {
	SysMessageID ids[] = new SysMessageID[0];
	String errMsg;

        if (!loaded ) {
            load();
        }

	/*
	 * Check/Setup array params
	 */
	long numMsgs = destMessages.size();

	/*
	 * If destination is empty, return empty array.
	 */
	if (numMsgs == 0)  {
	    return (ids);
	}

	if (startMsgIndex == null)  {
	    startMsgIndex = Long.valueOf(0);
	} else if ((startMsgIndex.longValue() < 0) ||
	                (startMsgIndex.longValue() > (numMsgs - 1)))  {
	    errMsg = " Start message index needs to be in between 0 and "
	                + (numMsgs - 1);
            throw new BrokerException(errMsg);
	}

	if (maxMsgsRetrieved == null)  {
	    maxMsgsRetrieved = Long.valueOf(numMsgs - startMsgIndex.longValue());
	} else if (maxMsgsRetrieved.longValue() < 0)  {
            errMsg = " Max number of messages retrieved value needs to be greater than 0.";
            throw new BrokerException(errMsg);
	}

	long maxIndex = startMsgIndex.longValue() + maxMsgsRetrieved.longValue();

        SortedSet s = new TreeSet(new RefCompare());

        try {
            Set msgset = null;
            synchronized(destMessages) {
                msgset = new HashSet(destMessages.values());
            }
            Iterator itr = msgset.iterator();
            while (itr.hasNext()) {
                PacketReference pr = (PacketReference)itr.next();
                s.add(pr);
            }
        } catch (Throwable ex) {
            logger.log(Logger.DEBUG,"Error getting msg IDs ",
                 ex);
        }

	ArrayList idsAl = new ArrayList();

	long i = 0;
        Iterator itr = s.iterator();
        while (itr.hasNext()) {
	    PacketReference pr = (PacketReference)itr.next();

	    if ( (i >= startMsgIndex.longValue()) &&
		 (i < maxIndex) )  {
                SysMessageID id = pr.getSysMessageID();
	        idsAl.add(id);
	    }

	    if (i >= maxIndex)  {
		break;
	    }
	    
	    ++i;
	}

	ids = (SysMessageID[])idsAl.toArray(ids);

	return (ids);
    }


    public String getName() {
        return uid.getLocalizedName();
    }    

    public String getDestinationName() {
        return uid.getName();
    }

    public ConnectionUID getConnectionUID() {
        return id;
    }

    public boolean isAutoCreated() {
        return ((type & DestType.DEST_AUTO) != 0);
    }

    public boolean isTemporary() {

        return ((type & DestType.DEST_TEMP) != 0);
    }

    public boolean isQueue() {
        return ((type & DestType.DEST_TYPE_QUEUE) != 0);
    }

    public int getType() {
        return type;
    }

    public Collection getAllMessages()
        throws UnsupportedOperationException
    {
        return destMessages.values();
    }


    public void purgeDestination() throws BrokerException {
        purgeDestination(false);
        
    }

    public void purgeDestination(boolean noerrnotfound) 
    throws BrokerException {

        if (!loaded) {
            load(noerrnotfound);
        }

        try {

        MemoryManager mm = Globals.getMemManager();
        int maxpurge = destMessages.size();

        long removedCount = 0L;
        long indeliveryCount = 0L;
        boolean do1 = false;
        boolean oomed = false;
        List list = null;
        int count = 0;
        while (maxpurge > 0 && count < maxpurge) {
            do1 = false;
            if (oomed) {
                do1 = true;
                oomed = false;
            } else if (mm !=  null && mm.getCurrentLevel() > 0) { 
                 do1 = true;
            } 
            if (!do1) {
                try {
                    list = new ArrayList(destMessages.getAllKeys());
                    count = maxpurge;
                } catch (OutOfMemoryError oom) {
                    oomed = true;
                    continue;
                }
            } else {
                list = destMessages.getFirstKeys(1);
            }
            if (list.isEmpty()) {
                break;
            }
            count += list.size();
            RemoveMessageReturnInfo ret = null;
            SysMessageID sysid = null;
            Iterator itr = list.iterator();
            while (itr.hasNext()) {
                sysid = (SysMessageID)itr.next();
                ret = _removeMessage(sysid, RemoveReason.PURGED, null, null, true);
                if (ret.removed) {
                    removedCount++;
                } else if (ret.indelivery) {
                    indeliveryCount++;
                }
            } 
        } //while  maxpurge 
        logger.log(logger.INFO, br.getKString(br.I_NUM_MSGS_PURGED_FROM_DEST,
                                removedCount, uid.getLocalizedName()));
        if (indeliveryCount > 0) {
            logger.log(logger.INFO, br.getKString(
                br.I_NUM_MSGS_INDELIVERY_NOT_PURGED_FROM_DEST,
                indeliveryCount, uid.getLocalizedName()));
        }
        Agent agent = Globals.getAgent();
        if (agent != null)  {
            agent.notifyDestinationPurge(this);
        }

        } catch (Exception ex) {
            if (BrokerStateHandler.isShuttingDown()) {
                logger.log(Logger.INFO,
                    BrokerResources.E_PURGE_DST_FAILED, getName(), ex);
            } else {
                logger.logStack(Logger.WARNING,
                    BrokerResources.E_PURGE_DST_FAILED, getName(), ex);
            }

            if (ex instanceof BrokerException) throw (BrokerException)ex;

            throw new BrokerException(br.getKString(
                BrokerResources.E_PURGE_DST_FAILED, getName()), ex);
        }
    }

    public void purgeDestination(Filter criteria) throws BrokerException {
        if (!loaded ) {
            load();
        }

        Map m = destMessages.getAll(criteria);
        Iterator itr = m.keySet().iterator();
        while (itr.hasNext()) {
            try {
                removeMessage((SysMessageID)itr.next(), RemoveReason.PURGED);
            } catch (Exception ex) {
                logger.logStack(Logger.INFO,
                    BrokerResources.E_PURGE_DST_FAILED, getName(), ex);
            }
        }
    }

    public Map getAll(Filter f) {
        if (!loaded ) {
            try {
                load();
            } catch (Exception ex) {}
        }

        return destMessages.getAll(f);
    }

    public void getSizeInfo(DestinationInfo dinfo) {
        if (!loaded) { 
            synchronized(sizeLock) {
                dinfo.nMessages += size;
                dinfo.nMessageBytes += bytes;
            }
            dinfo.nRemoteMessages += 0;
            dinfo.nRemoteMessageBytes += 0L;
            dinfo.nUnackMessages += 0;
            dinfo.nInDelayMessages += 0;
            dinfo.nInDelayMessageBytes += 0L;
            dinfo.nTxnMessages += 0;
            dinfo.nTxnMessageBytes += 0L;
            return;
        }

        Set msgs =  null;
        synchronized(destMessages) {
            msgs = new HashSet(destMessages.values());
            dinfo.nMessages += destMessages.size();
            dinfo.nMessageBytes += destMessages.byteSize();
        }
        if (isQueue()) {
            dinfo.nUnackMessages += getUnackSize();
        } else {
            dinfo.nUnackMessages += getUnackSize(msgs);
        }
        MessageDeliveryTimeTimer dt = deliveryTimeTimer;
        if (dt != null) {
            dt.getSizeInfo(msgs, dinfo);
        } else {
            dinfo.nInDelayMessages += 0;
            dinfo.nInDelayMessageBytes += 0L;
        }
        getRemoteSize(msgs, dinfo);
        txnSize(msgs, dinfo);
    }

    public int size() throws UnsupportedOperationException {
        if (!loaded) {
            return size;
        }
        return destMessages.size();
    }


    public long byteSize() throws UnsupportedOperationException {
        if (!loaded) {
            return bytes;
        }
        return destMessages.byteSize();
    }

    public int getRemoteSize() {
        return getRemoteSize(null, null);
    }

    public int getRemoteSize(Set msgset, DestinationInfo dinfo) {
        int cnt = 0;
        Set msgs =  msgset;
        if (msgs == null) {
            synchronized(destMessages) {
                msgs = new HashSet(destMessages.values());
            }
        }
        Iterator itr = msgs.iterator();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            if (!ref.isLocal()) {
                cnt++;
                if (dinfo != null) {
                    dinfo.nRemoteMessages++;
                    dinfo.nRemoteMessageBytes += ref.getSize();
                }
            }
        }
        return cnt;
    }

    public long getRemoteBytes() {
        return getRemoteBytes(null);
    }
    public long getRemoteBytes(Set msgset) {
        long rbytes = 0;
        Set msgs =  msgset;
        if (msgs == null) {
            synchronized(destMessages) {
                msgs = new HashSet(destMessages.values());
            }
        }
        Iterator itr = msgs.iterator();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            if (!ref.isLocal()) {
                rbytes += ref.getSize();
            }
        }
        return rbytes;
    }


    public int txnSize() {
        return txnSize(null, null);
    }

    public int txnSize(Set msgset, DestinationInfo dinfo) {
        Set msgs = msgset;
        if (msgs == null) {
            synchronized(destMessages) {
                msgs = new HashSet(destMessages.values());
            }
        }
        Iterator itr = msgs.iterator();
        int cnt = 0;
        TransactionList tl = DL.getTransactionList();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            TransactionUID tid = ref.getTransactionID();
            if (tid == null) {
                continue;
            }
            TransactionState ts = tl.retrieveState(tid, true);
            if (ts == null || 
                ts.getState() == TransactionState.COMMITTED) {
                continue;
            }
            cnt ++;
            if (dinfo != null) {
                dinfo.nTxnMessages++;
                dinfo.nTxnMessageBytes += ref.getSize();
            }
        }
        return cnt;
    }

    public long txnByteSize() {
        return txnByteSize(null);
    }

    public long txnByteSize(Set msgset) {
        Set msgs = msgset;
        if (msgs == null) {
            synchronized(destMessages) {
                msgs = new HashSet(destMessages.values());
            }
        }
        Iterator itr = msgs.iterator();
        long sz = 0L;
        TransactionList tl = DL.getTransactionList();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            TransactionUID tid = ref.getTransactionID();
            if (tid == null) {
                continue;
            }
            TransactionState ts = tl.retrieveState(tid, true);
            if (ts == null || 
                ts.getState() == TransactionState.COMMITTED) {
                continue;
            }
            sz += ref.getSize();
        }
        return sz;
    }

    /**
     * @return -1 if unlimited
     */
    public long checkDestinationCapacity(PacketReference ref) {
        long room = -1L;
        int maxc = destMessages.capacity();
        if (maxc > 0L) {
            room = maxc - destMessages.size();
            if (room < 0L) {
                room = 0L;
            }
        }
        if (ref == null) {
            return room;
        }
        long maxb = destMessages.byteCapacity();
        if (maxb > 0L) {
            long cnt = (maxb - destMessages.byteSize())/ref.byteSize();
            if (cnt < 0L) {
                cnt = 0L;
            }
            if (cnt < room) {
                room = cnt;
            }
        }
        return room;
    }

    public float destMessagesSizePercent() {
        int maxc = destMessages.capacity();
        if (maxc <=0 ) {
            return (float)0;
        }
        return ((float)destMessages.size()/(float)maxc)*100;
    }

    public abstract int getUnackSize();
    public abstract int getUnackSize(Set msgset);

     /**
     * Maximum number of messages stored in this
     * list at any time since its creation.
     *
     * @return the highest number of messages this set
     * has held since it was created.
     */
   public long getHighWaterBytes() {
        return destMessages.highWaterBytes();
    }

    /**
     * Maximum number of bytes stored in this
     * list at any time since its creation.
     *
     * @return the largest size (in bytes) of
     *  the objects in this list since it was
     *  created.
     */
    public int getHighWaterCount() {
        return destMessages.highWaterCount();
    }

    /**
     * The largest message 
     * which has ever been stored in this destination.
     *
     * @return the number of bytes of the largest
     *  message ever stored on this destination.
     */
    public long highWaterLargestMessageBytes() {
        return destMessages.highWaterLargestMessageBytes();
    }

    /**
     * Average number of bytes stored in this
     * destination at any time since the broker started.
     *
     * @return the largest size (in bytes) of
     *  the objects in this destination since it was
     *  created.
     */
    public double getAverageBytes() {
        return destMessages.averageBytes();
    }


    /**
     * Average number of messages stored in this
     * list at any time since its creation.
     *
     * @return the average number of messages this set
     * has held since it was created.
     */
    public float getAverageCount() {
        return destMessages.averageCount();
    }

    /**
     * The average message size (which implements Sizeable)
     * of messages which has been stored in this list.
     *
     * @return the number of bytes of the average
     *  message stored on this list.
     */
    public double averageMessageBytes() {
        return destMessages.averageMessageBytes();
    }

    public SizeString getMaxByteSize()
    {
        return msgSizeLimit;
    }

    public int getCapacity()
    {
        return countLimit;
    }

    public SizeString getByteCapacity()
    {
        return memoryLimit;
    }

    public void setMaxByteSize(SizeString limit)
        throws UnsupportedOperationException
    {

        if (getDEBUG()) {
            logger.log(Logger.DEBUG, 
                "attempting to set Message Size Limit to " +
                limit + " for destination " + this);
        }

	Long oldVal;

	if (this.msgSizeLimit == null)  {
	    oldVal = Long.valueOf(Limitable.UNLIMITED_BYTES);
	} else  {
	    oldVal = Long.valueOf(this.msgSizeLimit.getBytes());
	}
	if (oldVal.longValue() == 0)  {
	    oldVal = Long.valueOf(Limitable.UNLIMITED_BYTES);
	}

        this.msgSizeLimit = limit;
        long bytes = 0;
        if (limit == null) {
            bytes = Limitable.UNLIMITED_BYTES;
        } else {
            bytes = limit.getBytes();
        }
        if (bytes == 0) { // backwards compatibiity
            bytes = Limitable.UNLIMITED_BYTES;
        }
        destMessages.setMaxByteSize(bytes);

        notifyAttrUpdated(DestinationInfo.MAX_MESSAGE_SIZE, 
			oldVal, Long.valueOf(bytes));
    }

    public void setCapacity(int limit)
        throws UnsupportedOperationException
    {
        if (getDEBUG()) {
            logger.log(Logger.DEBUG, 
                "attempting to set Message Count Limit to " +
                limit + " for destination " + this);
        }

	Long oldVal = Long.valueOf(this.countLimit);

        if (limit == 0)  { // backwards compatibility
            limit = Limitable.UNLIMITED_CAPACITY;
        }
        this.countLimit = limit;
        destMessages.setCapacity(limit);
        // make sure we update the batch size after a limit change
        updateProducerBatch(false);

        notifyAttrUpdated(DestinationInfo.MAX_MESSAGES, 
				oldVal, Long.valueOf(this.countLimit));
    }

    public void setByteCapacity(SizeString limit)
        throws UnsupportedOperationException
    {
        if (getDEBUG()) {
            logger.log(Logger.INFO, 
                "attempting to set Message Bytes Limit to " +
                limit + " for destination " + this);
        }

	Long oldVal;

	if (this.memoryLimit == null)  {
	    oldVal = Long.valueOf(Limitable.UNLIMITED_BYTES);
	} else  {
	    oldVal = Long.valueOf(this.memoryLimit.getBytes());
	}
	if (oldVal.longValue() == 0)  {
	    oldVal = Long.valueOf(Limitable.UNLIMITED_BYTES);
	}

        this.memoryLimit = limit;
        long bytes = 0;
        if (limit == null) {
            bytes = Limitable.UNLIMITED_BYTES;
        } else {
            bytes = limit.getBytes();
        }
        if (bytes == 0) { // backwards compatibiity
            bytes = Limitable.UNLIMITED_BYTES;
        }
        destMessages.setByteCapacity(bytes);
        updateProducerBatch(false);

        notifyAttrUpdated(DestinationInfo.MAX_MESSAGE_BYTES, 
				oldVal, Long.valueOf(bytes));
    }

    public int getMaxActiveConsumers() {
        return UNLIMITED;
    }

    public int getMaxFailoverConsumers() {
        return NONE;
    }

    public void setMaxProducers(int cnt) 
        throws BrokerException
    {
        if (isDMQ) {
            throw new BrokerException(
                br.getKString(BrokerResources.X_DMQ_INVAID_PRODUCER_CNT));
        }
        if (cnt == 0) {
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_BAD_MAX_PRODUCER_CNT,
                     getName()),
                    BrokerResources.X_BAD_MAX_PRODUCER_CNT,
                    (Throwable) null,
                    Status.ERROR);
        }

	Integer oldVal = Integer.valueOf(maxProducerLimit);

        maxProducerLimit = (cnt < -1 ? -1:cnt);
        producers.setCapacity(maxProducerLimit);

        notifyAttrUpdated(DestinationInfo.MAX_PRODUCERS, 
			oldVal, Integer.valueOf(maxProducerLimit));
    }

    public int getMaxProducers() {
        return maxProducerLimit;
    }

    public int getAllActiveConsumerCount() {
        int cnt = 0;
        synchronized (consumers) {
            if (consumers.size() == 0) return 0;
            Iterator itr = consumers.values().iterator();
            Consumer c = null;
            while (itr.hasNext()) {
                c = (Consumer)itr.next();
                if (c instanceof Subscription) {
                    cnt += ((Subscription)c).getChildConsumers().size();
                } else  {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public int getActiveConsumerCount() {
        return getConsumerCount();
    }

    public Set getActiveConsumers() {
       Set set = new HashSet();
       synchronized (consumers) {
           Iterator itr = consumers.values().iterator();
           while (itr.hasNext()) {
               Consumer con = (Consumer)itr.next();
               set.add(con);
           }
       }
       return set;
    }

    public Set getFailoverConsumers() {
        return new HashSet();
    }

    public int getFailoverConsumerCount() {
        return NONE;
    }

    public void setMaxConsumers(int count) 
        throws BrokerException
    {
        if (count == 0) {
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_BAD_MAX_CONSUMER_CNT,
                    getName()),
                    BrokerResources.X_BAD_MAX_CONSUMER_CNT,
                    (Throwable) null,
                    Status.ERROR);
        }
        maxConsumerLimit = (count < -1 ? -1:count);
        consumers.setCapacity(maxConsumerLimit);
    }


    public void setMaxActiveConsumers(int cnt) 
        throws BrokerException
    {


        throw new UnsupportedOperationException("setting max active consumers not supported on this destination type");
    }

    public void setMaxFailoverConsumers(int cnt) 
        throws BrokerException
    {
        throw new UnsupportedOperationException("setting max failover consumers not supported on this destination type");
    }

    public int hashCode() {
        return uid.hashCode();
    }

    public boolean equals(Object o)
    {   if (o instanceof Destination) {
            if ( uid == ((Destination)o).uid)
                return true;
            return uid.equals(((Destination)o).uid);
        }
        return false;
    }
    

    public boolean queueMessage(PacketReference pkt, boolean trans) 
        throws BrokerException {
        return queueMessage(pkt, trans, true);
    }

    public boolean queueMessage(PacketReference pkt, 
                                boolean trans, 
                                boolean enforcelimit) 
        throws BrokerException {
        if (!DL.isValid()) {
            throw new BrokerException(
               br.getKString(
                    BrokerResources.I_DST_SHUTDOWN_DESTROY, getName()));
        }
        synchronized(dmc) {
            msgsIn +=1;
            msgBytesIn += pkt.byteSize();
            msgsInInternal +=1;
            if (msgsInInternal >= Long.MAX_VALUE) {
                msgsInOutLastResetTime = System.currentTimeMillis(); 
                msgsInInternal = 0;
                msgsOutInternal = 0;
            }
        }
        PacketListDMPair dmp = null;
        try {

        try {
            boolean check = !isAdmin() && !isInternal();
            dmp = DL.addNewMessage((check && enforcelimit), pkt);
            boolean ok = dmp.getReturn();
            if (!ok && !isDMQ) {
               // expired
               // put on dead message queue
               if (!isInternal()) {
                  pkt.setDestination(this);
                  markDead(pkt, RemoveReason.EXPIRED, null);
                  DL.removePacketList(pkt.getSysMessageID(), this.getDestinationUID(), pkt);
               }
               return false;
            }
        } catch (BrokerException ex) {
            pkt.destroy();
            throw ex;
        }
        pkt.setDestination(this);
        try {
            if (!DL.isValid()) {
                pkt.destroy();
                throw new BrokerException(
                   br.getKString(
                        BrokerResources.I_DST_SHUTDOWN_DESTROY, getName()));
            }
            if (overrideP) {
                pkt.overridePersistence(overridePvalue);
            }
            putMessage(pkt, AddReason.QUEUED, false, enforcelimit);
            if (overrideTTL) {
                pkt.overrideExpireTime(System.currentTimeMillis() +
                   overrideTTLvalue);
            }

            ExpirationInfo ei = pkt.getExpireInfo();
            MessageDeliveryTimeInfo di = pkt.getDeliveryTimeInfo();
            if (di != null && di.isDeliveryDue()) {
                di = null;
            }
            MsgExpirationReaper er = null;
            MessageDeliveryTimeTimer dt = null;
            if (ei != null || di != null) {
                er = expireReaper;
                dt = deliveryTimeTimer;
            }
            if (er != null && ei != null) {
                if (!DL.isValid()) {
                    String emsg = br.getKString(br.W_ADD_MSG_DEST_DESTROYED, pkt, ""+uid);
                    RuntimeException ex = new RuntimeException(emsg);
                    logger.log(Logger.WARNING, emsg);
                    removeMessage(pkt.getSysMessageID(), null);
                    throw ex;
                }
                er.addExpiringMessage(ei);
            }
            if (di != null) {
                if (!DL.isValid()) {
                    String emsg = br.getKString(br.W_ADD_MSG_DEST_DESTROYED, pkt, ""+uid);
                    RuntimeException ex = new RuntimeException(emsg);
                    ex.fillInStackTrace();
                    logger.log(Logger.WARNING, emsg);
                    removeMessage(pkt.getSysMessageID(), null);
                    throw ex;
                }
                if (dt == null) {
                    String emsg = br.getKString(br.W_ADD_MSG_NO_DELIVERY_TIMER, 
                                                pkt, uid+"["+isValid()+"]");
                    RuntimeException ex = new RuntimeException(emsg);
                    ex.fillInStackTrace();
                    logger.log(Logger.WARNING, emsg);
                    throw ex;
                }
                dt.addMessage(di);
            }
        } catch (IllegalStateException ex) { // message exists
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_MSG_EXISTS_IN_DEST,
                          pkt.getSysMessageID(),
                          this.toString()),
                    BrokerResources.X_MSG_EXISTS_IN_DEST,
                    (Throwable) ex,
                    Status.NOT_MODIFIED);
        } catch (OutOfLimitsException ex) {
            removeMessage(pkt.getSysMessageID(), RemoveReason.OVERFLOW);
            Object lmt = ex.getLimit();
            boolean unlimited = false; 
            if (lmt == null) {
            } else if (lmt instanceof Integer) {
                unlimited = ((Integer)ex.getLimit()).intValue() <= 0;
            } else if (lmt instanceof Long) {
                unlimited = ((Long)ex.getLimit()).longValue() <= 0;
            }
            String args[] = {pkt.getSysMessageID().toString(), 
                             getName(),
                             (unlimited ?
                                br.getString(BrokerResources.M_UNLIMITED) :
                                ex.getLimit().toString()),
				ex.getValue().toString()};
            String id = BrokerResources.X_INTERNAL_EXCEPTION;
            int status = Status.RESOURCE_FULL;
            switch (ex.getType()) {
                case OutOfLimitsException.CAPACITY_EXCEEDED:
                    id = BrokerResources.X_DEST_MSG_CAPACITY_EXCEEDED;
                    break;
                case OutOfLimitsException.BYTE_CAPACITY_EXCEEDED:
                    id = BrokerResources.X_DEST_MSG_BYTES_EXCEEDED;
                    break;
                case OutOfLimitsException.ITEM_SIZE_EXCEEDED:
                    id = BrokerResources.X_DEST_MSG_SIZE_EXCEEDED;
                    status = Status.ENTITY_TOO_LARGE;
                    break;
                default:
            }
                          
            throw new BrokerException(br.getKString(id, args),
                                      id, (Throwable) ex, status);

        } catch (IllegalArgumentException ex) {
            removeMessage(pkt.getSysMessageID(), RemoveReason.ERROR);
            throw ex;
        }

        } finally {
        if (dmp != null) {
            dmp.nullRef();
        }
        }

        return true;
    }

    public abstract ConsumerUID[] calculateStoredInterests(PacketReference sys)
    throws BrokerException, SelectorFormatException;


    public abstract Set routeNewMessage(PacketReference sys)
    throws BrokerException, SelectorFormatException;

    public abstract void routeNewMessageWithDeliveryDelay(PacketReference ref)
    throws BrokerException, SelectorFormatException;


    /* called from transaction code */
    public abstract void forwardOrphanMessage(PacketReference sys,
                  ConsumerUID consumer)
        throws BrokerException;

    /* called from transaction code */
    public abstract void forwardOrphanMessages(Collection syss,
                  ConsumerUID consumer)
        throws BrokerException;

    public abstract void forwardMessage(Set consumers, PacketReference sys)
         throws BrokerException;

    public abstract void forwardDeliveryDelayedMessage(
                                Set<ConsumerUID> consumers,
                                PacketReference ref)
                                throws BrokerException;

    /**
     * only called when loading a transaction
     * LKS-XXX need to rethink if there is a cleaner way
     * to manage this
     */
    protected abstract ConsumerUID[] routeLoadedTransactionMessage(
           PacketReference ref)
          throws BrokerException, SelectorFormatException;
    
    
    public abstract void unrouteLoadedTransactionAckMessage(PacketReference ref, ConsumerUID consumer)
    throws BrokerException;
    
    public void acquireQueueRemoteLock() throws BrokerException {
        long totalwaited = 0L;
        long pretime = 0L, curtime = 0L;
        long waitime = 15000L;
        synchronized(queueRemoteLock) {
            while (queueRemoteLockThread != null && isValid() &&
                   !DestinationList.isShutdown()) {
                curtime = System.currentTimeMillis();
                totalwaited += (pretime == 0L ? 0L:(curtime - pretime));
                if (pretime != 0L) {
                    waitime = 30000L;
                    String[] args = { ""+getDestinationUID(),
                                      "["+queueRemoteLockThread+
                                      "]("+String.valueOf(totalwaited)+")" };
                    Globals.getLogger().log(Logger.INFO,
                        Globals.getBrokerResources().getKString(
                        BrokerResources.I_WAIT_FOR_QUEUE_REMOTE_MSG_LOCK, args));
                }
                pretime = System.currentTimeMillis();
                try {
                    queueRemoteLock.wait(waitime);
                } catch (Exception e) {
                }
            }
            if (!isValid()) {
                throw new BrokerException(
                br.getKString(br.I_DST_DESTROY, ""+getDestinationUID()));
            }
            if (DestinationList.isShutdown()) {
                throw new BrokerException(br.getKString(br.W_SHUTDOWN_BROKER));
            }
            queueRemoteLockThread = Thread.currentThread();
        }
    }

    public void clearQueueRemoteLock() {
        Thread mythr = Thread.currentThread();
        synchronized(queueRemoteLock) {
            if (queueRemoteLockThread == mythr) {
                queueRemoteLockThread = null;
                queueRemoteLock.notifyAll();
            }
        }
    }

    public void putMessage(PacketReference ref, Reason r)
        throws IndexOutOfBoundsException, 
        IllegalArgumentException, IllegalStateException {

        putMessage(ref, r, false, true);
    }

    public void putMessage(PacketReference ref, Reason r, boolean override)
        throws IndexOutOfBoundsException, 
        IllegalArgumentException, IllegalStateException {

        putMessage(ref, r, override, true);
    }

    public void putMessage(PacketReference ref, Reason r, 
                           boolean override, boolean enforcelimit) 
        throws IndexOutOfBoundsException, 
        IllegalArgumentException, IllegalStateException {

        if (!override) {
             if (!enforcelimit) {
                 destMessages.put(ref.getSysMessageID(), ref, r, override, enforcelimit);
             } else {
                 destMessages.put(ref.getSysMessageID(), ref, r, override);
             }
            _messageAdded(ref, r, false);
            return;
        }
        boolean overrideRemote = false;
        synchronized(destMessages) {
            PacketReference oldref = (PacketReference)destMessages.get(
                                                  ref.getSysMessageID());
            if (oldref != null && oldref != ref && !oldref.isLocal()) {
                oldref.overrided();
                ref.overriding();
                overrideRemote = true;
             }
             if (!enforcelimit) {
                 destMessages.put(ref.getSysMessageID(), ref, r, override, enforcelimit);
             } else {
                 destMessages.put(ref.getSysMessageID(), ref, r, override);
             }
        }
        _messageAdded(ref, r, overrideRemote);
    }

    protected void unputMessage(PacketReference ref, Reason r)
        throws IndexOutOfBoundsException, IllegalArgumentException
    {
        Object o = destMessages.remove(ref.getSysMessageID(), r);
        _messageRemoved(ref, ref.byteSize(), r, (o != null));
    }


    public boolean removeRemoteMessage(SysMessageID id, 
                         Reason r, PacketReference remoteRef, boolean wait) 
                         throws BrokerException {
        RemoveMessageReturnInfo ret = _removeMessage(id, r, null, remoteRef, wait);
        return ret.removed;
    }

    public boolean removeRemoteMessage(SysMessageID id, 
                         Reason r, PacketReference remoteRef) 
                         throws BrokerException {
        RemoveMessageReturnInfo ret = _removeMessage(id, r, null, remoteRef, true);
        return ret.removed;
    }

    public boolean removeMessage(SysMessageID id, Reason r, boolean wait)
        throws BrokerException
    {
        RemoveMessageReturnInfo ret = _removeMessage(id, r, null, null, wait);
        return ret.removed;
    }

    public boolean removeMessage(SysMessageID id, Reason r)
        throws BrokerException {
        RemoveMessageReturnInfo ret = _removeMessage(id, r, null, null, true);
        return ret.removed;
    }

    public RemoveMessageReturnInfo 
    removeMessageWithReturnInfo(SysMessageID id, Reason r)
    throws BrokerException {
        return  _removeMessage(id, r, null, null, true);
    }

    public boolean removeMessage(SysMessageID id, Reason r,
           Hashtable dmqProps)
        throws BrokerException
    {
       RemoveMessageReturnInfo ret = _removeMessage(id, r, dmqProps, null, true);
       return ret.removed;
    }

    public static class RemoveMessageReturnInfo {
        public boolean removed = false;
        public boolean indelivery  = false;
        public boolean inreplacing  = false;
        public boolean storermerror  = false;
    }

    private RemoveMessageReturnInfo _removeMessage(SysMessageID id, Reason r,
            Hashtable dmqProps, PacketReference remoteRef, boolean wait)
            throws BrokerException
    {
        RemoveMessageReturnInfo ret = new RemoveMessageReturnInfo();

        PacketReference ref = null;

        // LKS-XXX revisit if it is really necessary to load the
        // message before removing it 
        if (!loaded ) {
            load();
        }

        // OK .. first deal w/ Lbit
        // specifically .. we cant remove it IF the Lbit
        // is set
        ref = (PacketReference)destMessages.get(id);
        if (ref == null) {
            // message already gone 
            DL.removePacketList(id, getDestinationUID(), null/*ref*/);
            logger.log(Logger.DEBUG, "Reference already gone for " + id);
            return ret;
        }

        if (remoteRef != null && remoteRef != ref) {
            logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG), 
                       "Reference for "+id+" is overrided, not remove");
            remoteRef.setInvalid();
            return ret;
        }

        ExpirationInfo ei = ref.getExpireInfo();

        if (isValid() && (r == RemoveReason.EXPIRED || 
                          r == RemoveReason.PURGED ||
                          r == RemoveReason.REMOVE_ADMIN)) {
            if (!ref.checkDeliveryAndSetInRemoval()) {

                if (r == RemoveReason.EXPIRED && !EXPIRE_DELIVERED_MSG) {
                    logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                        "Message "+ref.getSysMessageID()+" is not "+r+
                        " because it is in delivery to client consumer");
                    if (ei != null) {
                        ei.clearReapCount();
                    }
                    ret.inreplacing = ref.inReplacing;
                    ret.indelivery = !ret.inreplacing;
                    return ret;
                }
                if (r == RemoveReason.PURGED && !PURGE_DELIVERED_MSG) {
                    logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                        "Message "+ref.getSysMessageID()+" is not "+r+
                        " because it is in delivery to client consumer");
                    ret.inreplacing = ref.inReplacing;
                    ret.indelivery = !ret.inreplacing;
                    return ret;
                }
                if (r == RemoveReason.REMOVE_ADMIN) {
                    logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                        "Message "+ref.getSysMessageID()+" can not "+r+
                        " because it is in delivery to client client");
                    ret.inreplacing = ref.inReplacing;
                    ret.indelivery = !ret.inreplacing;
                    return ret;
                }
            }
        }

        synchronized(ref) {
            if (ref.getLBitSet()) {
                ref.setInvalid();
                if (r == RemoveReason.EXPIRED && ei != null) {
                    ei.clearReapCount();
                }
                logger.log(Logger.DEBUG,"LBit set for " + id);
                return ret;
            }
        }

        synchronized(destMessagesInRemoving) {
            if (destMessagesInRemoving.get(id) != null && !wait) {
                logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                           "Reference "+id +" is being removed by another thread ");
                return ret;
            } 
            destMessagesInRemoving.put(id, id);
        }

        try {

            synchronized(_removeMessageLock) {
                if (destMessages.get(id) == null) {
                    logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                           "Reference has already been removed for " + id);
                    return ret;
                }

            // handle DMQ
            // OK we need to move the message TO the DMQ before removing it
            // OK .. if we arent the DMQ and we want to use the DMQ 
            if (!isInternal() &&
                (r == RemoveReason.EXPIRED ||
                 r == RemoveReason.EXPIRED_BY_CLIENT ||
                 r == RemoveReason.EXPIRED_ON_DELIVERY ||
                 r == RemoveReason.REMOVED_LOW_PRIORITY ||
                 r == RemoveReason.REMOVED_OLDEST ||
                 r == RemoveReason.ERROR ||
                 r == RemoveReason.UNDELIVERABLE)) { 
                 markDead(ref, r, dmqProps);
            }

            // OK really remove the message
            ref.setInvalid();
            if (remoteRef == null) {
                ref = (PacketReference)destMessages.remove(id, r);
            } else {
                Object errValue = Boolean.valueOf(false);
                Object o = destMessages.removeWithValue(id, remoteRef, errValue, r);
                if (o == errValue) { /* intended */
                    logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                           "Requeued message found on removing remote reference@"+
                            remoteRef.hashCode()+"="+remoteRef+"[" + id+"]");
                    return ret;
                }
                ref = (PacketReference)o;
            }

            } //synchronized(_removeMessageLock)

            if (ref == null) {
                logger.log(((DEBUG_CLUSTER||getDEBUG()) ? Logger.INFO:Logger.DEBUG),
                           "Reference has already gone for " + id);
                return ret;
            }

            //long l = ref.byteSize();

            // clears out packet, must happen after DMQ
            ret.storermerror = !_messageRemoved(ref, ref.byteSize(), r, true);

            ref.destroy();

            synchronized(dmc) {
                msgsOut += 1;
                msgBytesOut += ref.byteSize();
                msgsOutInternal += 1;
                if (msgsOutInternal >= Long.MAX_VALUE) { 
                    msgsInOutLastResetTime = System.currentTimeMillis();
                    msgsInInternal = 0L;
                    msgsOutInternal = 0L;
                }
            }
            if (ei != null && r != RemoveReason.EXPIRED) {
                MsgExpirationReaper er = expireReaper;
                if (er != null) {
                    er.removeMessage(ei);
                }              
            }
            MessageDeliveryTimeInfo di = ref.getDeliveryTimeInfo();
            if (di != null) {
                MessageDeliveryTimeTimer dt = deliveryTimeTimer;
                if (dt != null) {
                    dt.removeMessage(di);
                }
            }

            ret.removed = true;
            return ret;

        } finally {
           destMessagesInRemoving.remove(id);
        }
    }

    public String lookupReasonString(Reason r, long arrivalTime,
            long expireTime, long senderTime)
    {
         String reason = null;
         if (r == RemoveReason.EXPIRED || 
             r == RemoveReason.EXPIRED_ON_DELIVERY || 
             r == RemoveReason.EXPIRED_BY_CLIENT) {
             String args[] = { getDestinationUID().toString(),
                               (Long.valueOf(expireTime)).toString(),
                               (Long.valueOf(arrivalTime)).toString(),
                               (Long.valueOf(senderTime)).toString() };
             if (r ==  RemoveReason.EXPIRED) {
                 if (arrivalTime != 0 && expireTime != 0 &&
                     expireTime <= arrivalTime) {
                     reason = br.getKString(
                              BrokerResources.M_DMQ_ARRIVED_EXPIRED, args);
                 } else {
                     reason = br.getKString(
                              BrokerResources.M_DMQ_MSG_EXPIRATION, args);
                 }
             } else if (r == RemoveReason.EXPIRED_ON_DELIVERY) {
                 reason = br.getKString(BrokerResources.M_MSG_EXPIRED_ON_DELIVERY, args);
             } else if (r == RemoveReason.EXPIRED_BY_CLIENT) {
                 reason = br.getKString(BrokerResources.M_MSG_EXPIRED_BY_CLIENT, args);
             } else {
                 reason = br.getKString(BrokerResources.M_DMQ_MSG_EXPIRATION, args);
             }
         } else if (r == RemoveReason.REMOVED_LOW_PRIORITY ||
                    r == RemoveReason.REMOVED_OLDEST) {
             String countLimitStr = (countLimit <= 0 ? 
                    Globals.getBrokerResources().getString(
                    BrokerResources.M_UNLIMITED) :
                    String.valueOf(countLimit));
             String sizeLimitStr = (memoryLimit == null ||
                    memoryLimit.getBytes() <= 0 ? 
                    Globals.getBrokerResources().getString(
                    BrokerResources.M_UNLIMITED) :
                    memoryLimit.toString());
             String args[] = { getDestinationUID().toString(),
                     countLimitStr, sizeLimitStr };

             reason =  br.getKString(BrokerResources.M_DMQ_MSG_LIMIT, args);
         } else if (r == RemoveReason.UNDELIVERABLE) {
             reason = br.getKString(
                       BrokerResources.M_DMQ_MSG_UNDELIVERABLE,
                       getDestinationUID().toString());
         } else {
             reason = br.getKString(
                       BrokerResources.M_DMQ_MSG_ERROR,
                       getDestinationUID().toString());
         }

        return reason;
    }


    public void primaryInterestChanged(Consumer interest) {
        // interest has moved from failover to primary
    }

    /*
    private ConnectionUID getConnectionUID(Consumer intr) {
        return intr.getConsumerUID().getConnectionUID();
    }
    */

    public Consumer addConsumer(Consumer interest, boolean local) 
                    throws BrokerException, SelectorFormatException {
        return addConsumer(interest, local, null);
    }

    /**
     * @param conn the client connection 
     */
    public Consumer addConsumer(Consumer interest, boolean local, Connection conn)
        throws BrokerException, SelectorFormatException {
        return addConsumer(interest, local, conn, true);
    }

    protected Consumer addConsumer(Consumer interest, boolean local, 
                                Connection conn, boolean loadIfActive)
        throws BrokerException, SelectorFormatException {

        synchronized(consumers) {
            if (consumers.get(interest.getConsumerUID()) != null) {
                throw new ConsumerAlreadyAddedException(
                    br.getKString(BrokerResources.I_CONSUMER_ALREADY_ADDED,
                    interest.getConsumerUID(), this.toString()));
            }
        }

        if (isInternal() && !BrokerMonitor.isENABLED()) {
            throw new BrokerException(
               br.getKString(
                    BrokerResources.X_MONITORING_DISABLED, getName()));
        }
        interest.attachToDestination(this, pstore);
        interest.addRemoveListener(destMessages);

        if (!loaded && interest.isActive() && loadIfActive) {
            load();
        }
        
        synchronized (consumers) {
            if (maxConsumerLimit != UNLIMITED &&
                maxConsumerLimit <= consumers.size()) {
                throw new BrokerException(
                    br.getKString(
                        BrokerResources.X_CONSUMER_LIMIT_EXCEEDED,
                             getName(), String.valueOf(maxConsumerLimit)),
                        BrokerResources.X_CONSUMER_LIMIT_EXCEEDED,
                        (Throwable) null,
                        Status.CONFLICT);
            }
            consumers.put(interest.getConsumerUID(), interest);
            if (bm != null && consumers.size() == 1) {
                bm.start();
            }
            if (state == DestState.CONSUMERS_PAUSED ||
                state == DestState.PAUSED) {
                interest.pause("Destination PAUSE2");
            }

        } 
        synchronized(this) {
            if (destReaper != null) {
                destReaper.cancel();
                destReaper = null;
            }
            clientReconnect();
        }

        // send a message IF we are on a monitor destination
        if (bm != null)
            bm.updateNewConsumer(interest);

        return null;
    }

    public void removeConsumer(ConsumerUID interest, boolean notify) 
        throws BrokerException {
        removeConsumer(interest, null, false, notify);
    }

    public void removeConsumer(ConsumerUID interest, Map remotePendings,
                               boolean remoteCleanup, boolean notify) 
                               throws BrokerException
    {
        Consumer c = null;
        synchronized(consumers) {
            c = (Consumer)consumers.remove(interest);
            synchronized (this) {
                if (bm != null && consumers.size() == 0) {
                    bm.stop();
                }
                if (shouldDestroy()) {
                    if (destReaper != null) {
                        destReaper.cancel();
                        destReaper = null;
                    }
                    destReaper = new DestReaperTask(uid);
                    try {
                        timer.schedule(destReaper, DL.AUTOCREATE_EXPIRE);
                    } catch (IllegalStateException ex) {
                       logger.log(Logger.DEBUG,"Can not reschedule task, "
                            + "timer has been canceled, the broker " +
                            " is probably shutting down", ex);
                    }
                }
            }
        }
        if (c != null) {
            // remove consumer interest in REMOVE of messages
            c.removeRemoveListener(destMessages);
        }
        MessageDeliveryTimeTimer dt = deliveryTimeTimer;
        if (dt != null) {
            dt.consumerClosed(c);
        }
        if (c != null && sendClusterUpdate() && notify) {
            Globals.getClusterBroadcast().destroyConsumer(c, remotePendings, remoteCleanup);
        }
    }

    protected void notifyConsumerAdded(Consumer c, Connection conn) {
        synchronized(consumers) {
            BrokerAddress ba = c.getConsumerUID().getBrokerAddress();
            if (ba == null || ba == Globals.getMyAddress()) {
                Globals.getConnectionManager().
                    getConsumerInfoNotifyManager().consumerAdded(this, conn);
            } else {
                Globals.getConnectionManager().
                    getConsumerInfoNotifyManager().remoteConsumerAdded(this);
            }
        }
    }

    protected void notifyConsumerRemoved() {
        synchronized(consumers) {
            Globals.getConnectionManager().
                    getConsumerInfoNotifyManager().consumerRemoved(this);
        }
    }

    public boolean addProducer(ProducerSpi producer)   
        throws BrokerException
    {
        if (isInternal()) {
            throw new BrokerException(
               br.getKString(
                    BrokerResources.X_MONITOR_PRODUCER, getName()));
        }
        if (maxProducerLimit !=  UNLIMITED &&
            producers.size() >= maxProducerLimit ) {
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_PRODUCER_LIMIT_EXCEEDED,
                         getName(), String.valueOf(maxProducerLimit)),
                    BrokerResources.X_PRODUCER_LIMIT_EXCEEDED,
                    (Throwable) null,
                    Status.CONFLICT);
        }
        synchronized(this) {
            if (destReaper != null) {
                destReaper.cancel();
                destReaper = null;
            }
        }
        // technically, we can wait until we get a producer, but
        // then we have to deal with ordering
        if (!loaded) {
            load();
        }

        try {
            synchronized (producers) {
                producers.put(producer.getProducerUID(), producer);
            }
        } catch (IndexOutOfBoundsException ex) {
            throw new BrokerException(
                br.getKString(
                    BrokerResources.X_PRODUCER_LIMIT_EXCEEDED,
                         getName(), String.valueOf(maxProducerLimit)),
                    BrokerResources.X_PRODUCER_LIMIT_EXCEEDED,
                    (Throwable) ex,
                    Status.CONFLICT);
        }
        producerFlow.addProducer((Producer)producer);
        boolean active =  producerFlow.checkResumeFlow((Producer)producer, false);
        logger.log(Logger.DEBUGHIGH,"Producer " + producer + " is " + active);

        return active;
    }

    public void removeProducer(ProducerUID producerUID) {
        Producer p = null;
        synchronized (producers) {
            p = (Producer)producers.remove(producerUID);
        }
        if (p == null) return; // nothing to do

        producerFlow.removeProducer(p);
        producerFlow.checkResumeFlow(p, false);

        synchronized (this) {
            if (shouldDestroy()) {
                if (destReaper != null) {
                    destReaper.cancel();
                    destReaper = null;
                }
                destReaper = new DestReaperTask(uid);
                try {
                    timer.schedule(destReaper, DL.AUTOCREATE_EXPIRE);
                } catch (IllegalStateException ex) {
                   logger.log(Logger.DEBUG,"Can not reschedule task, "
                       + "timer has been canceled, "
                       + "the broker is probably shutting down", ex);
                }
            }
        }
    }

    /*
    private void dumpStoredSet(Set s) {
        // DEBUG only - no reason to localize
        logger.log(Logger.INFO,"DEBUG: Dumping order");
        int i =0;
        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            PacketReference n = (PacketReference)itr.next();
            logger.log(Logger.INFO, n.getPriority() +
                 " : " + n.getTime() + " :" + n.getSequence()
                 + "  " + n.getSysMessageID() + " : " + n.getPacket().getTimestamp());
        }
    }
    */
 
    public abstract void sort(Comparator c);

    public synchronized  void load() 
        throws BrokerException
    {
        load(false, null, null);
    }

    public synchronized  void load(boolean noerrnotfound) 
        throws BrokerException
    {
        load(false, null, null, null, null, noerrnotfound);
    }

    public synchronized  void load(Set takeoverMsgs) 
        throws BrokerException
    {
        load(false, null, null, null, takeoverMsgs, false);
    }

    protected synchronized  Map load(boolean neverExpire,
            Map preparedAcks,  Map transactionStates) 
        throws BrokerException
    {
        return load(neverExpire, preparedAcks, transactionStates, null, null, false);
    }

    public synchronized  LinkedHashMap load(boolean neverExpire,
            Map preparedAcks,  Map transactionStates, Map committingTrans, 
            Set takeoverMsgs, boolean noerrnotfound) throws BrokerException {

        if (Globals.getStore().getPartitionModeEnabled() && takeoverMsgs != null) {
            String emsg = br.getKString(br.E_INTERNAL_BROKER_ERROR,
              ": Unexpected call:Destination.load(takeoverMsgs) for partition mode");
            BrokerException ex = new BrokerException(emsg);
            logger.logStack(logger.ERROR, emsg, ex);
            throw ex;
        }
        if (loaded) {
            return null;
        }

        logger.log(Logger.INFO, br.getKString(br.I_LOADING_DESTINATION,
                          toString(),  String.valueOf(size))+logsuffix);

        LinkedHashMap preparedTrans = null;
        boolean enforceLimit = true;
        Set deadMsgs = new HashSet();

        int maxloadcnt = size;
        int curcnt = 0;

        try {
            enforceLimit = destMessages.getEnforceLimits();
            destMessages.enforceLimits(false);

            Enumeration msgs = null;
            try {
                msgs = pstore.messageEnumeration(this);
            } catch (DestinationNotFoundException e) {
                if (noerrnotfound) {
                    logger.log(Logger.INFO, br.getKString(
                           BrokerResources.I_LOAD_DST_NOTFOUND_INSTORE,
                           getName(), e.getMessage()));
                    return null;
                }
                throw e;
            }

            MessageDeliveryTimeTimer dt = deliveryTimeTimer;

            SortedSet s = null;
            try { // no other store access should occur in this block

            HAMonitorService haMonitor = Globals.getHAMonitorService();
            boolean takingoverCheck = (takeoverMsgs == null && 
                                 !Globals.getStore().getPartitionModeEnabled() &&
                                 Globals.getHAEnabled() && haMonitor != null &&
                                 haMonitor.checkTakingoverDestination(this));

            if (dt == null && !isDMQ()) {
                if (!isValid()) {
                    String emsg = br.getKString(
                        br.X_LOAD_MSGS_TO_DESTROYED_DST, 
                                     getDestinationUID());
                    logger.log(logger.WARNING, emsg);
                    throw new BrokerException(emsg);
                }
                String emsg = br.getKString(
                    br.X_LOAD_MSGS_TO_DST_NO_DELIVERY_TIMER, 
                                         getDestinationUID());
                logger.log(logger.WARNING, emsg);
                throw new BrokerException(emsg);
            }

            s = new TreeSet(new RefCompare());
            while (msgs.hasMoreElements()) {
                Packet p = (Packet)msgs.nextElement();
                PacketReference pr =PacketReference.createReference(pstore, p, uid, null);
                if (isDMQ()) {
                    pr.clearDeliveryTimeInfo();
                }
                if (takeoverMsgs != null && takeoverMsgs.contains(pr)) {
                    pr = null;
                    continue;
                }
                if (takingoverCheck && haMonitor.checkTakingoverMessage(p)) {
                    pr = null;
                    continue;
                }
                MessageDeliveryTimeInfo di = pr.getDeliveryTimeInfo();
                if (di != null) {
                    dt.removeMessage(di);
                }
                if (neverExpire) {
                    pr.overrideExpireTime(0);
                }
                // mark already stored and make packet a SoftReference to
                // prevent running out of memory if dest has lots of msgs
                pr.setLoaded(); 
                if (getDEBUG()) {
                    logger.log(Logger.INFO,"Loaded Message " + p +
                         " into destination " + this);
                }
                try {
                    if (!isDMQ && !DL.addNewMessage(false, pr).getReturn()) {
                        // expired
                        deadMsgs.add(pr);
                    }
                } catch (Exception ex) {
                    String args[] = { pr.getSysMessageID().toString(),
                        pr.getDestinationUID().toString(),
                        ex.getMessage() };
                    logger.logStack(Logger.WARNING,
                              BrokerResources.W_CAN_NOT_LOAD_MSG,
                              args, ex);
                    continue;
                }
                s.add(pr);
                DL.packetlistAdd(pr.getSysMessageID(), pr.getDestinationUID(), null);

                curcnt ++;
                if (curcnt > 0 && (curcnt % LOAD_COUNT == 0
                    || (curcnt > LOAD_COUNT && curcnt == size))) {
                    String args[] = { toString(),
                       String.valueOf(curcnt),
                       String.valueOf(maxloadcnt),
                       String.valueOf((curcnt*100)/maxloadcnt) };
                    logger.log(Logger.INFO,
                        BrokerResources.I_LOADING_DEST_IN_PROCESS,
                       args);
               }

            }

            } finally {
            pstore.closeEnumeration(msgs);
            }

            if (FaultInjection.getInjection().FAULT_INJECTION) {
                FaultInjection fi = FaultInjection.getInjection();
                try {
                    fi.checkFaultAndThrowBrokerException(
                        FaultInjection.FAULT_LOAD_DST_1_5, null);
                } catch (BrokerException e) {
                    fi.unsetFault(fi.FAULT_LOAD_DST_1_5);
                    throw e;
                }
            }
               
            // now we're sorted, process
            Iterator itr = s.iterator();
            while (itr.hasNext()) {
    
                PacketReference pr = (PacketReference)itr.next();

                // ok .. see if we need to remove the message
                ConsumerUID[] consumers = pstore.getConsumerUIDs(
                                              getDestinationUID(),
                                              pr.getSysMessageID());

                if (consumers == null) {
                    consumers = new ConsumerUID[0];
                }
                if (getDEBUG()) {
                    logger.log(Logger.INFO, consumers.length+
                        " stored consumers for "+pr+":"+getDestinationUID());
                }

                if (consumers.length == 0 &&
                    pstore.hasMessageBeenAcked(uid,pr.getSysMessageID())) {
                    if (getDEBUG()) {
                    logger.log(Logger.INFO, "Message " +
                    pr.getSysMessageID()+"["+this+"] has been acked, destory..");
                    }
                    decrementDestinationSize(pr);
                    DL.removePacketList(pr.getSysMessageID(), pr.getDestinationUID(), pr);
                    pr.destroy();
                    continue;
                }

                if (consumers.length > 0) {
                    pr.setStoredWithInterest(true);
                } else {
                    pr.setStoredWithInterest(false);
                }

                // first producer side transactions

                MessageDeliveryTimeInfo di = pr.getDeliveryTimeInfo();
                boolean dontRoute = false;
                boolean delayDelivery = false;
                if (di != null && !di.isDeliveryDue()) {
                    dt.addMessage(di);
                    delayDelivery = true;
                    dontRoute = true;
                }
                TransactionUID sendtid = pr.getTransactionID();
                if (sendtid != null) {
                    // if unrouted and not in rollback -> remove
                    Boolean state = (Boolean)
                                    (transactionStates == null ?
                                     null : transactionStates.get(sendtid));

                    // at this point, we should be down to 3 states
                    if (state == null ) { // committed

                        if (consumers.length == 0 && !delayDelivery) {
                            // route the message, it depends on the type of
                            // message 
                            try {
                                consumers = routeLoadedTransactionMessage(pr);
                            } catch (Exception ex) {
                                logger.logStack(Logger.WARNING, 
                                  br.getKString(br.W_EXCEPTION_ROUTE_LOADED_MSG,
                                  pr.getSysMessageID(), ex.getMessage()), ex);
                            }
                            if (consumers.length > 0) {
                                int[] states = new int[consumers.length];
                                for (int i=0; i < states.length; i ++)  
                                    states[i] = PartitionedStore.INTEREST_STATE_ROUTED;
                                try {
                                    pstore.storeInterestStates(
                                          getDestinationUID(),
                                          pr.getSysMessageID(),
                                          consumers, states, true, null);
                                    pr.setStoredWithInterest(true);
                                } catch (Exception ex) {
                                      // ok .. maybe weve already been routed
                                }
                            } else {
                                if (getDEBUG()) {
                                logger.log(Logger.INFO, "Message "+pr.getSysMessageID()+
                                " [TUID="+pr.getTransactionID()+", "+this+"] no interest" +", destroy...");
                                }
                                decrementDestinationSize(pr);
                                DL.removePacketList(pr.getSysMessageID(), pr.getDestinationUID(), pr);
                                pr.destroy();
                                continue;
                            }
                        }
                    } else if (state.equals(Boolean.TRUE)) { // prepared
                     
                        if (preparedTrans == null) {
                            preparedTrans = new LinkedHashMap();
                        }
                        preparedTrans.put(pr.getSysMessageID(), 
                                          pr.getTransactionID());
                        dontRoute = true;
                    } else { // rolledback
                        if (getDEBUG()) {
                        logger.log(Logger.INFO, "Message "+pr.getSysMessageID()+
                        " [TUID="+pr.getTransactionID()+", "+this+"] to be rolled back" +", destroy...");
                        }
                        decrementDestinationSize(pr);
                        DL.removePacketList(pr.getSysMessageID(), pr.getDestinationUID(), pr);
                        pr.destroy();
                        continue;
                    }
                }

                // if the message has a transactionID AND there are 
                // no consumers, we never had time to route it
                //

                if (consumers.length == 0 && !dontRoute) {   
                    if (getDEBUG()) {
                        logger.log(Logger.INFO, 
                        "No consumer and dontRoute: Unrouted packet " + pr+", "+this);
                    }
                    decrementDestinationSize(pr);
                    DL.removePacketList(pr.getSysMessageID(), pr.getDestinationUID(), pr);
                    pr.destroy();
                    continue;
                }
    
                int states[] = new int[consumers.length];
    
                for (int i = 0; i < consumers.length; i ++) {
                    states[i] = pstore.getInterestState(
                        getDestinationUID(),
                        pr.getSysMessageID(), consumers[i]);
                }

                if (consumers.length > 0 ) {
                    pr.update(consumers, states);
                }
                try {
                    putMessage(pr, AddReason.LOADED);
                } catch (IllegalStateException ex) {
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
                }
                ExpirationInfo ei = pr.getExpireInfo();
                if (ei != null && expireReaper != null) {
                    expireReaper.addExpiringMessage(ei);
                }

                List<ConsumerUID> consumerList = Arrays.asList(consumers);

                // now, deal with consumer side transactions
                Map transCidToState = (Map)(preparedAcks == null ? null : 
                        preparedAcks.get(pr.getSysMessageID()));

                if (transCidToState != null) {
                    // ok .. this isnt code focused on performance, but
                    // its rarely called and only once

                    // new a new list that allows itr.remove()
                    consumerList = new ArrayList<ConsumerUID>(consumerList);
                    
                    Iterator citr = consumerList.iterator();
                    while (citr.hasNext()) {
                        ConsumerUID cuid = (ConsumerUID)citr.next();
                        TransactionUID tid = (TransactionUID)transCidToState.get(cuid);
                        Boolean state = Boolean.FALSE;
                        if (tid != null) {
                            state = (Boolean)(transactionStates == null ?
                                        null : transactionStates.get(tid));
                        }
                        // OK for committed transactions, acknowledge
                        if (state == null) {
                            if (getDEBUG()) {
                                logger.log(Logger.INFO, 
                                    "Consumed message has committed state "+
                                     pr.getSysMessageID()+" [TUID="+tid+", "+this+"]"+
                                    ", consumer: "+cuid);
                            }
                            // acknowledge
                            if (pr.acknowledged(cuid, cuid, false, true)) {
                                 if (committingTrans != null && 
                                     committingTrans.get(tid) != null) {
                                     unputMessage(pr, RemoveReason.ACKNOWLEDGED);
                                 }
                                 decrementDestinationSize(pr);
                                 DL.removePacketList(pr.getSysMessageID(), 
                                                     pr.getDestinationUID(), pr);
                                 pr.destroy();
                                 if (getDEBUG()) {
                                     logger.log(Logger.INFO, "Remove committed consumed message "+
                                         pr.getSysMessageID()+" [TUID="+tid+", "+this+"]"+
                                         ", consumer: "+cuid);
                                 }
                             }
                             citr.remove();
                             continue;
                        } else if (state.equals(Boolean.TRUE)) {
                            // for prepared transactions, dont route
                             citr.remove();
                        } else if (state.equals(Boolean.FALSE)) {
                            // for rolled back transactions, do nothing
                            if (getDEBUG()) {
                                logger.log(Logger.INFO, "Redeliver message "+
                                pr.getSysMessageID()+" [TUID="+tid+", "+this+"]" +" to consumer "+cuid);
                            }
                        }
                    }
                    // done processing acks                            
                }
                loaded = true; // dont recurse
                if (!dontRoute) {
                    if (getDEBUG()) {
                        logger.log(Logger.INFO, "Route loaded message "+
                            pr.getSysMessageID()+" ["+this+"]"+
                            " to consumers "+consumerList);
                    }
                    if (di != null) {
                        forwardDeliveryDelayedMessage(
                            new HashSet<ConsumerUID>(consumerList), pr);
                    } else {
                        routeLoadedMessage(pr, consumerList);
                    }
                } else if (delayDelivery) {
                    di.setDeliveryReady();
                }
            }
        } catch (Throwable ex) {
            String emsg = Globals.getBrokerResources().getKString(
                          BrokerResources.W_LOAD_DST_FAIL, getName());
            logger.logStack(Logger.ERROR, emsg, ex);
            loaded = true;
            unload(true);
            throw new BrokerException(emsg, ex);
        }
        destMessages.enforceLimits(enforceLimit);
        loaded = true;
            
        // clean up dead messages
        Iterator deaditr = deadMsgs.iterator();
        while (deaditr.hasNext()) {
            PacketReference pr = (PacketReference)deaditr.next();
            try {
                if (preparedTrans != null)
                    preparedTrans.remove(pr.getSysMessageID());
                removeMessage(pr.getSysMessageID(), RemoveReason.EXPIRED);
            } catch (Exception ex) {
                logger.logStack(Logger.INFO,
                    BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "Processing " + pr + " while loading destination " + this, ex);
            }
        }
        logger.log(Logger.INFO, br.getKString(br.I_LOADING_DEST_COMPLETE,
                   toString(),  String.valueOf(size))+logsuffix);

        return preparedTrans;

    }

    protected void routeLoadedMessage(PacketReference ref,
             List consumerids)
        throws BrokerException, SelectorFormatException
    {
        if (consumerids == null || consumerids.size() == 0) {
            return;
        }
        Iterator itr = consumerids.iterator();

        while (itr.hasNext()) {
            ConsumerUID cuid = (ConsumerUID)itr.next();
            // we dont route messages to queues
            if (cuid == PacketReference.getQueueUID()) {
                Set s = this.routeNewMessage(ref);
                this.forwardMessage(s, ref);
            } else {
                Consumer c = (Consumer)consumers.get(cuid);
                if (c == null) {
                    Set s = this.routeNewMessage(ref);
                    this.forwardMessage(s, ref);
                } else {
                    c.routeMessage(ref, false);
                }
            }
        }
    }

    protected Consumer getConsumer(ConsumerUID uid)
    {
        return (Consumer)consumers.get(uid);
    }


    public void unload(boolean refs) {
        unload(refs, false);
    }

    protected void unload(boolean refs, boolean closePartition) {
        if (getDEBUG()) {
            logger.log(Logger.DEBUG,"Unloading " + this);
        }
        if (!loaded) {
            return;
        }
        bytes = destMessages.byteSize();
        size = destMessages.size();

        // get all the persistent messages
        Map m = destMessages.getAll((closePartition ? null: unloadfilter));
        try {

            // unload them
            if (refs) {
                // remove the refs
                Iterator i = m.values().iterator();
                while (i.hasNext()) {
                    PacketReference ref = (PacketReference)i.next();
                    destMessages.remove(ref.getSysMessageID(),RemoveReason.UNLOADED);
                    ref.clear();
                }
                destMessages = new SimpleNFLHashMap();
                remoteSize = 0;
                remoteBytes = 0;
                loaded = false;
                if (!closePartition) {
                    initialize();
                }
            } else { // clear the ref
                Iterator itr = destMessages.values().iterator();
                while (itr.hasNext()) {
                    PacketReference ref = (PacketReference)itr.next();
                    ref.unload();
                }
            }
            // technically we are still loaded so
        } catch (Throwable thr) {
            logger.logStack(Logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Unloading destination " + this, thr);

            destMessages = new SimpleNFLHashMap();
            remoteSize = 0;
            remoteBytes = 0;
            loaded = false;
            if (!closePartition) {
                initialize();
            }
        }
    }

    static class UnloadFilter implements Filter
    {
        public boolean matches(Object o) {
            assert o instanceof PacketReference;
            return ((PacketReference)o).isPersistent();
        }
        public boolean equals(Object o) {
             return super.equals(o);
        }
        public int hashCode() {
             return super.hashCode();
        }
    };
    transient Filter unloadfilter = new UnloadFilter();


    protected void destroy(String destroyReason)
        throws IOException, BrokerException {
        destroy(destroyReason, false);
    }

    // optional hook for destroying other data
    protected void destroy(String destroyReason, boolean noerrnotfound) 
        throws IOException, BrokerException
    {
        synchronized (DL.destinationList) {
            destvalid = false;
        }
        synchronized(this) {

            if (destReaper != null) {
                destReaper.cancel();
                destReaper = null;
            }
            if (reconnectReaper != null) {
                reconnectReaper.cancel();
                reconnectReaper = null;
            }
            if (expireReaper != null) {
                expireReaper.destroy();
                expireReaper = null;
            }
            if (deliveryTimeTimer != null) {
                deliveryTimeTimer.destroy();
            }
            if (!neverStore || stored) {
                purgeDestination(noerrnotfound);
                try {
                    pstore.removeDestination(this, PERSIST_SYNC);
                } catch (DestinationNotFoundException e) {
                    if (!noerrnotfound) throw e; 
                    logger.log(Logger.INFO, br.getKString(
                               br.I_RM_DST_NOTFOUND_INSTORE, 
                               getName(), e.getMessage()));
                }
                stored = false;
            }
            if (deliveryTimeTimer != null) {
                deliveryTimeTimer = null;
            }
        }
        Globals.getLogger().log(Logger.INFO, 
            BrokerResources.I_DST_DESTROY, getName());
    }

    public void routeCommittedMessageWithDeliveryTime(PacketReference ref) 
    throws BrokerException {
        MessageDeliveryTimeTimer dt = deliveryTimeTimer;
        if (dt != null) {
            dt.routeTransactedMessage(ref);
        }
    }

    public String toString() {
        return uid.getLocalizedName();
    }

    public String getUniqueName() {
        return uid.toString();
    }

    /**
     * Called when a specific event occurs
     * @param type the event that occured
     */

    public void eventOccured(EventType type,  Reason r,
                Object target, Object oldValue, Object newValue, 
                Object userdata) {
    }

    protected void _messageAdded(PacketReference ref, Reason r,
                                 boolean overrideRemote) {
        if (r == AddReason.LOADED) {
            if (ref.isLocal() && overrideRemote) {
                long objsize;
                synchronized(sizeLock) {
                    objsize = ref.byteSize();
                    remoteSize --;
                    remoteBytes -= objsize;
                    size --;
                    bytes -= objsize;
                    DL.decrementTotals(objsize, false);
                }
            }
            return;
        }
        incrementDestinationSize(ref);
    }

    /**
     * @return false if persist store msg removal exception 
     */
    protected boolean _messageRemoved(PacketReference ref, 
        long objsize, Reason r, boolean doCount) {

        if (ref == null) {
            return true; // did nothing
        }  

        DL.removePacketList(ref.getSysMessageID(), getDestinationUID(), ref);
        if (!doCount) {
            return true;
        }

        boolean onRollback = false;
        synchronized (this) {
            if (r == RemoveReason.REMOVED_LOW_PRIORITY ||
                r == RemoveReason.REMOVED_OLDEST ||
                r == RemoveReason.REMOVED_OTHER )
                discardedCnt ++;
            else if (r == RemoveReason.EXPIRED || 
                     r == RemoveReason.EXPIRED_BY_CLIENT || 
                     r == RemoveReason.EXPIRED_ON_DELIVERY)
                expiredCnt ++;
            else if (r == RemoveReason.PURGED)
                purgedCnt ++;
            else if (r == RemoveReason.ROLLBACK)
            {
                rollbackCnt ++;
                onRollback=true;
            }
            else if (r == RemoveReason.ACKNOWLEDGED)
                ackedCnt ++;
            else if (r == RemoveReason.OVERFLOW)
                overflowCnt ++;
            else if (r == RemoveReason.ERROR)
                errorCnt ++;
            decrementDestinationSize(ref);
        }
        boolean ret = ref.remove(onRollback);

        // see if we need to pause/resume any consumers
        producerFlow.checkResumeFlow(null, true);

        return ret;
    }

    /**
     * this method is called to determine if a destination can
     * be removed when all interests are removed.
     * it can be removed if:
     *     - it was autocreated
     *     - no existing messages are stored on the destination
     *     - not being taken over; only in HA mode
     */
    public boolean shouldDestroy() {

        if (Globals.getHAEnabled()) {
            HAMonitorService haMonitor = Globals.getHAMonitorService();
            if (haMonitor != null && haMonitor.checkTakingoverDestination(this)) {
                logger.log(Logger.DEBUG, BrokerResources.X_DESTROY_DEST_EXCEPTION,
                    this.getUniqueName(), "destination is being taken over");
                return false;
            }
        }

        return (size() == 0 && isAutoCreated() && !isTemporary() &&
            producers.isEmpty() && consumers.isEmpty());
    }


    boolean overrideP = false;
    boolean overridePvalue = false;
    boolean overrideTTL = false;
    long overrideTTLvalue = 0;

    public void overridePersistence(boolean persist) {
       neverStore = !persist;
       overrideP =true;
       overridePvalue = persist;
    }
    public void clearOverridePersistence() {
        overrideP = false;
    }
    public void overrideTTL(long ttl) {
        overrideTTL = true;
        overrideTTLvalue = ttl;
    }
    public void clearOverrideTTL() {
        overrideTTL = false;
    }

    public boolean shouldOverridePersistence() {
        return overrideP;
    }
    public boolean getOverridePersistence() {
        return overridePvalue;
    }
    public boolean shouldOverrideTTL() {
        return overrideTTL;
    }
    public long getOverrideTTL() {
        return overrideTTLvalue;
    }

    public boolean isInternal() {
        boolean ret = DestType.isInternal(type);
        return ret;
    }

    public boolean isDMQ() {
        boolean ret = DestType.isDMQ(type);
        return ret;
    }

    public boolean isAdmin() {
        boolean ret = DestType.isAdmin(type);
        return ret;
    }

    public int getConsumerCount() {
        return consumers.size();
    }

    public Iterator getConsumers() {
        List l = null;
        synchronized(consumers) {
            l = new ArrayList(consumers.values());
        }
        return l.iterator();
    }

    public List getAllActiveConsumers() {
        List l = new ArrayList();
        synchronized (consumers) {
            Iterator itr = consumers.values().iterator();
            Consumer c = null;
            while (itr.hasNext()) {
                c = (Consumer)itr.next();
                if (c instanceof Subscription) {
                    l.addAll(((Subscription)c).getChildConsumers());
                } else  {
                    l.add(c);
                }
            }
        }
        return l;
    }

    public Iterator getProducers() {
        List l = null;
        synchronized(producers) {
            l = new ArrayList(producers.values());
        }
        return l.iterator();
    }

    public int getProducerCount() {
        return producers.size();
    }

    public int getMaxPrefetch() {
        return maxPrefetch;
    }
    public void setMaxPrefetch(int prefetch) {
	Long oldVal = Long.valueOf(maxPrefetch);

        maxPrefetch = prefetch;

        notifyAttrUpdated(DestinationInfo.DEST_PREFETCH, 
			oldVal, Long.valueOf(maxPrefetch));
    }
    public void setMaxSharedConsumers(int max) {
        // does nothing for destinations
    }
    public void setSharedFlowLimit(int prefetch) {
        // does nothing for destinations
    }

    public int getMaxNumSharedConsumers() {
        // does nothing for destinations (topic only)
        return -1;
    }
    public int getSharedConsumerFlowLimit() {
        // does nothing for destinations(topic only)
        return 5;
    }

    public long getMsgBytesProducerFlow() {
        // xxx - should we cache this
        if (DL.NO_PRODUCER_FLOW)
            return -1;
        long bytes = 0;
        if (msgSizeLimit == null || msgSizeLimit.getBytes() <= 0) {
            bytes = Limitable.UNLIMITED_BYTES;
        } else {
            bytes = msgSizeLimit.getBytes();
        }
        return bytes;
    }

    public long getBytesProducerFlow() {
        if (DL.NO_PRODUCER_FLOW)
            return -1;
        return producerMsgBatchBytes;
    }
    public int getSizeProducerFlow() {
        if (DL.NO_PRODUCER_FLOW)
            return -1;
        return producerMsgBatchSize;
    }

    public void forceResumeFlow(ProducerSpi p)
    {
        producerFlow.pauseProducer((Producer)p);
        producerFlow.forceResumeFlow((Producer)p);
    }


    public boolean producerFlow(IMQConnection con, Producer producer) {

        producerFlow.pauseProducer(producer);
        boolean retval = producerFlow.checkResumeFlow(producer, true);
        logger.log(Logger.DEBUGHIGH,"producerFlow " + producer + " resumed: "
                   + retval);
        return retval;
    }

    public boolean isValid() {
        return DL.isValid() && destvalid;
    }

    public void incrementRefCount() 
        throws BrokerException
    {
        synchronized(DL.destinationList) {
            if (!DL.isValid()) {
                throw new IllegalStateException("Broker Shutting down");
            }
            if (!isValid()) {
                throw new BrokerException("Destination already destroyed");
            }
            refCount ++;
        }
    }

    public synchronized void decrementRefCount() {
        synchronized(DL.destinationList) {
            refCount --;
        }
    }

    public int getRefCount() {
        synchronized(DL.destinationList) {
            return refCount;
        }
    }

    protected void setClusterNotifyFlag(boolean b) {
        this.clusterNotifyFlag = b;
    }
    protected boolean getClusterNotifyFlag() {
        return clusterNotifyFlag;
    }

    // called after a destination has been changed because of an admin
    // action
    public void updateDestination() 
        throws BrokerException, IOException
    {
        update(true);
    }

    public void debug() {
        logger.log(Logger.INFO,"Dumping state for destination " + this);
        logger.log(Logger.INFO,"Consumer Count " + consumers.size());
        logger.log(Logger.INFO,"Producer Count " + producers.size());
        logger.log(Logger.INFO,"Message count " + destMessages.size());
        logger.log(Logger.INFO," --------- consumers");
        Iterator itr = consumers.values().iterator();
        while (itr.hasNext()) {
            Consumer c = (Consumer)itr.next();
            c.debug("\t");
        }
    }
   
    public  PacketReference getMessage(SysMessageID id)
    {        
    	return (PacketReference)destMessages.get(id);       
    }

     private RemoveMessageReturnInfo 
     removeExpiredMessage(DestinationUID duid, SysMessageID id)
     throws BrokerException {

          RemoveMessageReturnInfo ret = null;

          if (duid == null) {
              throw new RuntimeException("expired messages");
          }
          Destination d = DL.findDestination(duid);
          if (d != null) {
              ret = d._removeMessage(id, RemoveReason.EXPIRED, null, null, true);
              if (ret.removed) {
                 DL.removePacketList(id, d.getDestinationUID(), null);
              }
          }
          if (ret == null) {
              ret = new RemoveMessageReturnInfo();
              ret.removed = false;
              ret.indelivery = false;
          }
          return ret;
     }

    static final int DEST_PAUSE = 0;
    static final int DEST_RESUME = 1;
    static final int DEST_UPDATE = 2;
    static final int DEST_BEHAVIOR_CHANGE = 3;


    class ProducerFlow
    {

        transient Map pausedProducerMap = null;
        transient Map activeProducerMap = null;

        public ProducerFlow() {
            pausedProducerMap = new LinkedHashMap();
            activeProducerMap = new LinkedHashMap();
        }


        public synchronized int pausedProducerCnt() {
            return pausedProducerMap.size();
        }

        public synchronized int activeProducerCnt() {
            return activeProducerMap.size();
        }
        public synchronized Vector getDebugPausedProducers()
        {
            Vector v = new Vector();
            Iterator itr = pausedProducerMap.values().iterator();
            while (itr.hasNext()) {
                try {
                    //Object o = itr.next();
                    ProducerUID pid = 
                        ((Producer)itr.next()).getProducerUID();
                    v.add(String.valueOf(pid.longValue()));
                } catch (Exception ex) {
                    v.add(ex.toString());
                }
            }
            return v;
        }

        public synchronized Vector getDebugActiveProducers()
        {
            Vector v = new Vector();
            Iterator itr = activeProducerMap.values().iterator();
            while (itr.hasNext()) {
                try {
                    //Object o = itr.next();
                    ProducerUID pid = 
                        ((Producer)itr.next()).getProducerUID();
                    v.add(String.valueOf(pid.longValue()));
                } catch (Exception ex) {
                    v.add(ex.toString());
                }
            }
            return v;
        }
    
        private void sendResumeFlow(Producer p, boolean pause,
                String reason)
        {
            int sz = 0;
            long bs = 0L;
            long mbytes = 0;
            if (!pause) {
                sz = producerMsgBatchSize;
                bs = producerMsgBatchBytes;
                mbytes = getMsgBytesProducerFlow();
            }

            p.sendResumeFlow(getDestinationUID(), sz, bs, 
                mbytes, reason, false, DestinationList.MAX_PRODUCER_BATCH);
        }

        public  void updateAllProducers(int why,
                String info)
        {
            if (why == DEST_PAUSE) {
               synchronized (this) {
                   // iterate through the active Producers
                   Iterator itr = activeProducerMap.values().iterator();
                   while (itr.hasNext()) {
                         Producer p = (Producer)itr.next();
                         pausedProducerMap.put(p.getProducerUID(), p);
                         itr.remove();
                         p.pause();
                         sendResumeFlow(p, true,info);
                   }
               }
            } else if (why == DEST_RESUME) {
                checkResumeFlow(null, true, info);
            } else if (why == DEST_UPDATE || why == DEST_BEHAVIOR_CHANGE ) {

               synchronized (this) {
                   // iterate through the active Producers
                   Iterator itr = activeProducerMap.values().iterator();
                   while (itr.hasNext()) {
                       Producer p = (Producer)itr.next();
                       sendResumeFlow(p, false,info);
                   }
                }
            }             
        }

        public synchronized boolean pauseProducer(Producer producer)
        {
            boolean wasActive = false;
            if (activeProducerMap.remove(producer.getProducerUID()) != null) {
                pausedProducerMap.put(producer.getProducerUID(),
                           producer);
                wasActive = true;
            }
            producer.pause();
            return wasActive;
        }

        public boolean isProducerActive(ProducerUID uid) {
             return pausedProducerMap.get(uid) == null;
        }

        /*
        private synchronized void resumeProducer(Producer producer)
        {
            if (pausedProducerMap.remove(producer.getProducerUID()) != null) {
                activeProducerMap.put(producer.getProducerUID(), producer);
            }
            producer.resume();
        }
        */


        public  boolean checkResumeFlow(Producer p, 
                    boolean notify)
        {

             return checkResumeFlow(p, notify, null);
        }

        private  boolean checkResumeFlow(Producer producer, 
                    boolean notify, String info)
        {

            // note: lock order destMessages->this->producer
            // so any calls into destMessages must hold the destMessages
            // lock before this
            //
            synchronized (this) {
                // 1. handle pausing/resuming any producers
                // 2. then handle if the producer passed in (if any)
                // 3. handle pause/resume
                // 4. then notify
                // 
                // first deal with Paused destinations
                // 
                if (state == DestState.PRODUCERS_PAUSED || 
                    state == DestState.PAUSED) {
                    if (activeProducerMap != null) {
                        // pause all
                       Iterator itr = activeProducerMap.values().iterator();
                       while (itr.hasNext()) {
                             Producer p = (Producer)itr.next();
                             pausedProducerMap.put(p.getProducerUID(), p);
                             itr.remove();
                             p.pause();

                             // notify if notify=true or pids dont match
                             boolean shouldNotify = notify || 
                                   (producer != null && 
                                    !p.getProducerUID().equals(
                                      producer.getProducerUID()));
                             if (shouldNotify)
                                 sendResumeFlow(p, true,info);
                       }
                    }
                    return false; // paused
                }
                // now handle the case where we arent using FLOW_CONTROL
                if (limit != DestLimitBehavior.FLOW_CONTROL) {
                    if (pausedProducerMap != null) {
                        // pause all
                       Iterator itr = pausedProducerMap.values().iterator();
                       while (itr.hasNext()) {
                             Producer p = (Producer)itr.next();
                             activeProducerMap.put(p.getProducerUID(), p);
                             itr.remove();
                             p.resume();
                             // notify if notify=true or pids dont match
                             boolean shouldNotify = notify || 
                                   (producer != null && 
                                    !p.getProducerUID().equals(
                                      producer.getProducerUID()));
                             if (shouldNotify)
                                 sendResumeFlow(p, false,info);
                       }
                    }
                    return true; // not paused
                }
            }

            boolean resumedProducer = false;

            // ok, now we are picking the producer to flow control
            synchronized (destMessages) {
                int fs = destMessages.freeSpace();
                long fsb = destMessages.freeBytes();

                synchronized (this) {
     
                    Iterator itr = pausedProducerMap.values().iterator();

                    while (itr.hasNext() &&

                         (fs == Limitable.UNLIMITED_CAPACITY ||
                            (fs > 0  &&
                             fs > (activeProducerMap.size()*
                                 producerMsgBatchSize)))
                       && (fsb == Limitable.UNLIMITED_BYTES ||
                            (fsb > 0 &&
                              fsb > (activeProducerMap.size()*
                              producerMsgBatchBytes))))
                    {
                         // remove the first producer
                         Producer p = (Producer)itr.next();

                         if (!p.isValid()) {
                             continue;
                         }
                         if (getDEBUG())
                             logger.log(logger.DEBUGHIGH,"Resuming producer " 
                                 + p + " The destination has " + fs 
                                 + " more space and " 
                                 + activeProducerMap.size() 
                                 + " active producers ["
                                 + " batch size " 
                                 + producerMsgBatchSize + "  msg " 
                                 + destMessages.size());

                         activeProducerMap.put(p.getProducerUID(), p);
                         itr.remove();
                         p.resume();
                         // notify if notify=true or pids dont match
                         boolean shouldNotify = notify || 
                                   (producer != null && 
                                    !p.getProducerUID().equals(
                                      producer.getProducerUID()));


                         if ( shouldNotify) {
                             if (info == null) {
                                 info = "Producer " 
                                   + p.getProducerUID()
                                   + " has become active";
                             }
 
                             sendResumeFlow(p, false, info);
                        }
                    }
                    if (producer != null) {
                        resumedProducer = activeProducerMap.containsKey(
                              producer.getProducerUID());
                    }
                } // end synchronize
                
            }
            return resumedProducer;
            
        }

        public void forceResumeFlow(Producer p)
        {
           synchronized (destMessages) {
                int fs = destMessages.freeSpace();
                long fsb = destMessages.freeBytes();

                synchronized (this) {
                    if ((fs == Limitable.UNLIMITED_CAPACITY ||
                             fs >= (activeProducerMap.size()*
                                 producerMsgBatchSize))
                       && (fsb == Limitable.UNLIMITED_BYTES ||
                          fsb >= (activeProducerMap.size()*
                          producerMsgBatchBytes)))
                    {
                         activeProducerMap.put(p.getProducerUID(), p);
                         pausedProducerMap.remove(p.getProducerUID());
                         p.resume();
                         sendResumeFlow(p, false, "Producer " 
                                   + p.getProducerUID()
                                   + " has become active");
                    }
                } // end synchronize
                
            }
        }

        public synchronized boolean removeProducer(Producer producer)
        {
             producer.destroy();
             Object oldobj =
                     activeProducerMap.remove(producer.getProducerUID());
             pausedProducerMap.remove(producer.getProducerUID());
             return oldobj != null;
        }

        public synchronized boolean addProducer(Producer producer)
        {
            // always add as paused, then check
            Object o = 
              pausedProducerMap.put(producer.getProducerUID(), producer);
            return o == null;
        }

    }
    public boolean isProducerActive(ProducerUID uid) {
        return producerFlow.isProducerActive(uid);
    }

    /**
     * Method for trigerring JMX notifications whenever a destination
     * attribute is updated.
     *
     * @param attr	Attribute that was modified. Constant from DestinationInfo
     *			class.
     * @param oldVal	Old value
     * @param newVal	New value
     */
    public void notifyAttrUpdated(int attr, Object oldVal, Object newVal)  {
	Agent agent = Globals.getAgent();
	if (agent != null)  {
	    agent.notifyDestinationAttrUpdated(this, attr, oldVal, newVal);
	}
    }
    
    public void setValidateXMLSchemaEnabled(boolean b)  {
	this.validateXMLSchemaEnabled = b;
    }

    public boolean validateXMLSchemaEnabled()  {
	return (this.validateXMLSchemaEnabled);
    }

    public void setXMLSchemaUriList(String s)  {
	this.XMLSchemaUriList = s;
    }

    public String getXMLSchemaUriList()  {
	return (this.XMLSchemaUriList);
    }

    public void setReloadXMLSchemaOnFailure(boolean b)  {
	this.reloadXMLSchemaOnFailure = b;
    }

    public boolean reloadXMLSchemaOnFailure()  {
	return (this.reloadXMLSchemaOnFailure);
    }
}


class LoadComparator implements Comparator, Serializable
{
    public int compare(Object o1, Object o2) {
        if (o1 instanceof PacketReference && o2 instanceof PacketReference) {
                PacketReference ref1 = (PacketReference) o1;
                PacketReference ref2 = (PacketReference) o2;
                // compare priority
                long dif = ref2.getPriority() - ref1.getPriority();

                if (dif == 0)
                    dif = ref1.getTimestamp() - ref2.getTimestamp();

                // then sequence
                if (dif == 0)
                    dif = ref1.getSequence() - ref2.getSequence();
                if (dif < 0) return -1;
                if (dif > 0) return 1;
                return 0;
        } else {
            assert false;
            return o1.hashCode() - o2.hashCode();
        }
    }
    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(Object o) {
        return super.equals(o);
    }
    
}

