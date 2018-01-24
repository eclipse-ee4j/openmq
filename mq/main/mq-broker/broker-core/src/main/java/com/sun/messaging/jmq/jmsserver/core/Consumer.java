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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.io.*;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Set;
import java.util.Iterator;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.jmsserver.util.lists.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.NoPersistPartitionedStoreImpl;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.DMQ;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;

public class Consumer implements ConsumerSpi, EventBroadcaster, 
     Serializable
{
    transient Logger logger = Globals.getLogger();
    static final long serialVersionUID = 3353669107150988952L;

    public static final String PREFETCH = "prefetch";

    private static boolean DEBUG = false;

    private static final FaultInjection FI = FaultInjection.getInjection();

    protected static final  boolean DEBUG_CLUSTER_TXN =
        Globals.getConfig().getBooleanProperty(
                            Globals.IMQ + ".cluster.debug.txn") || DEBUG;

    protected static final boolean DEBUG_CLUSTER_MSG =
        Globals.getConfig().getBooleanProperty(
        Globals.IMQ + ".cluster.debug.msg") || DEBUG_CLUSTER_TXN || DEBUG;

    transient private boolean useConsumerFlowControl = false;
    transient private int msgsToConsumer = 0;

    transient Map<PartitionedStore, LinkedHashSet<Destination>> destinationMap = 
                  Collections.synchronizedMap(
                  new LinkedHashMap<PartitionedStore, LinkedHashSet<Destination>>());

    protected transient BrokerResources br = Globals.getBrokerResources();

    private static boolean C_FLOW_CONTROL_ALLOWED = 
             Globals.getConfig().getBooleanProperty(
             Globals.IMQ + ".destination.flowControlAllowed", true);

    long lastAckTime = 0;

    SessionUID sessionuid = null; 
    DestinationUID dest;
    ConsumerUID uid;
    transient ConsumerUID stored_uid;
    ConnectionUID conuid = null;
    transient boolean valid = true;
    transient boolean active = true;
    transient boolean paused = false;
    transient int pauseCnt = 0;
    transient int pauseFlowCnt =0;
    transient int resumeFlowCnt =0;
    boolean noLocal = false;
    transient boolean busy = false;
    transient Subscription parent = null;
    transient boolean isSpecialRemote = false;

    transient boolean isFailover = false;
    transient int position = 0;
    transient int lockPosition = -1;

    transient EventBroadcastHelper evb = null;

    boolean ackMsgsOnDestroy = true;

    transient int flowCount = 0;
    transient boolean flowPaused = false;

    transient int msgsOut = 0;

    transient int prefetch = -1; //unlimited
    transient int remotePrefetch = -1; 

    transient String creator = null;

    transient boolean requestedRecreation = false;

    /**
     * Optional selector string specified by the client application.
     */
    protected String selstr = null;
    protected transient Selector selector = null;

    transient NFLPriorityFifoSet msgs;
    protected transient Map<PartitionedStore, SubSet> parentListMap = Collections.synchronizedMap(
                               new LinkedHashMap<PartitionedStore, SubSet>());
    protected transient Map plistenerMap = Collections.synchronizedMap(
                               new LinkedHashMap<PartitionedStore, Object>());

    private transient Object mlistener = null;

    private transient boolean localConsumerCreationReady = false;

    private static final int DEFAULT_MSG_MAX_CONSECUTIVE_ROLLBACKS = 0;
    public static final int MSG_MAX_CONSECUTIVE_ROLLBACKS =
	Globals.getConfig().getIntProperty(
	Globals.IMQ + ".transaction.message.maxConsecutiveRollbacks",
                        DEFAULT_MSG_MAX_CONSECUTIVE_ROLLBACKS);

    private transient HashMap messageRollbacks = new HashMap();

    transient EventListener busylistener = null;

     class BusyListener implements EventListener
        {
            public void eventOccured(EventType type,  Reason r,
                    Object target, Object oldval, Object newval, 
                    Object userdata) {

                assert type == EventType.EMPTY;
                assert newval instanceof Boolean;
                checkState(null);
            }
        }

    transient EventListener removeListener = null;


    class RemoveListener implements EventListener
        {
            public void eventOccured(EventType type,  Reason r,
                    Object target, Object oldval, Object newval, 
                    Object userdata) 
            {
                assert type == EventType.SET_CHANGED_REQUEST;
                if (! (r instanceof RemoveReason)) return;
                // OK .. we are only registered to get the
                // following events:
                //   RemoveReason.EXPIRED
                //   RemoveReason.PURGED
                //   RemoveReason.REMOVED_LOW_PRIORITY
                //   RemoveReason.REMOVED_OLDEST
                //   RemoveReason.REMOVED_REJECTED
                //   RemoveReason.REMOVED_OTHER
                assert r != RemoveReason.UNLOADED;
                assert r != RemoveReason.ROLLBACK;
                assert r != RemoveReason.DELIVERED;
                assert r != RemoveReason.ACKNOWLEDGED;
                assert r != RemoveReason.ROLLBACK;
                assert r != RemoveReason.OVERFLOW;
                assert r != RemoveReason.ERROR;
                PacketReference ref = (PacketReference)oldval;
                msgs.remove(ref);
                if (DEBUG || DEBUG_CLUSTER_MSG) {
                    logger.log(logger.INFO, 
                    "Consumer.removeListener: removed msg "+ref+" from msgs in consumer "+Consumer.this);
                }
                if (!ref.isLocal() && ref.getMessageDeliveredAck(uid)) {
                    try {
                         Map props = new HashMap();
                         props.put(Consumer.PREFETCH, Integer.valueOf(1));
                         Globals.getClusterBroadcast().acknowledgeMessage(
                             ref.getBrokerAddress(), ref.getSysMessageID(),
                             uid, ClusterBroadcast.MSG_DELIVERED, props, false);
                    } catch (Exception e) {
                        if (DEBUG) {
                            logger.logStack(Logger.INFO, 
                            "Cannot send DELIVERED ack for message "+ ref+" for consumer "+uid, e);
                        }
                    }
                }
            }
        }

    private transient Object expiredID = null;
    private transient Object purgedID = null;
    private transient Object removedID1 = null;
    private transient Object removedID2 = null;
    private transient Object removedID3 = null;
    private transient Object removedID4 = null;

    public void addRemoveListener(EventBroadcaster l) {
        // add consumer interest in REMOVE of messages
        expiredID = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.EXPIRED, null);
        purgedID = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.PURGED, null);
        removedID1 = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.REMOVED_OLDEST, null);
        removedID2 = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.REMOVED_LOW_PRIORITY, null);
        removedID3 = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.REMOVED_REJECTED, null);
        removedID4 = l.addEventListener(removeListener, EventType.SET_CHANGED_REQUEST,
             RemoveReason.REMOVED_OTHER, null);
    }

    public void removeRemoveListener(EventBroadcaster l) {
        l.removeEventListener(expiredID);
        l.removeEventListener(purgedID);
        l.removeEventListener(removedID1);
        l.removeEventListener(removedID2);
        l.removeEventListener(removedID3);
        l.removeEventListener(removedID4);
    }

    private boolean getParentBusy() {
    	
        // Fix for CR 6875642 New direct mode hangs when creating a durable subscriber
//    	if (parent !=null){
//    		if (parent.isPaused()){
//    			return false;
//    		}
//    	}
    	
        return (parentListMap.size() > 0 && !isParentListEmpty(parentListMap))
                || (parent != null && parent.isBusy());
    }

    private static boolean isParentListEmpty(Map<PartitionedStore, SubSet> map) {
        synchronized(map) {
            Iterator<SubSet> itr = map.values().iterator();
            while (itr.hasNext()) {
                if (!itr.next().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static SubSet getNonEmptyParentList(Map<PartitionedStore, SubSet> map, SubSet lastpl) {
        SubSet pl = null, foundpl = null;
        synchronized(map) {
            Iterator<SubSet> itr = map.values().iterator();
            while (itr.hasNext()) {
                pl = itr.next();
                if (!pl.isEmpty()) {
                    if (lastpl == null) {
                        return pl;
                    }
                    foundpl = pl; 
                    if (!(foundpl == lastpl || foundpl.equals(lastpl))) {
                        return foundpl;
                    }
                }
            }
        }
        return foundpl;
    }

    public void setPrefetch(int count) {
        prefetch = count;
        useConsumerFlowControl = C_FLOW_CONTROL_ALLOWED;
    }
    
    public void setPrefetch(int count, boolean useConsumerFlowControl) {
        prefetch = count;
        this.useConsumerFlowControl = useConsumerFlowControl;
    }

    public void setRemotePrefetch(int count) {
        remotePrefetch = count;
    }

    public int getRemotePrefetch() {
        return remotePrefetch;
    }

    public static int calcPrefetch(Consumer consumer,  int cprefetch) {
        Destination d = consumer.getFirstDestination();
        Subscription sub = consumer.getSubscription();
        int dprefetch =  -1;
        if (d != null ) {
            dprefetch = (sub == null || !sub.getShared()) ?
                         d.getMaxPrefetch() : d.getSharedConsumerFlowLimit();
        }
        int pref = (dprefetch == -1) ?
                        cprefetch :
                            (cprefetch == -1 ? 
                             cprefetch :
                                 (cprefetch > dprefetch ?
                                  dprefetch : cprefetch));
        return pref;
    }

    public long getLastAckTime() {
        return lastAckTime;
    }

    public void setLastAckTime(long time)
    {
        lastAckTime = time;
        if (parent != null) // deal with subscription
            parent.setLastAckTime(time);
    }

    public int getPrefetch()
    {
        return prefetch;
    }

    public int getPrefetchForRemote()
    {
        DestinationList DL = Globals.getDestinationList();
        int fetch = prefetch; 
        if (DL.getMaxMessages() > 0) {
            long room = (DL.getMaxMessages() - DL.totalCount());
            if (room <= 0) {
                room = 1L;
            }
            if (fetch > room) { 
                fetch = (int)room;
            }
        }
        long room = -1L, m;
        Destination d = null;
        Iterator itr =  getDestinations().iterator();
        while (itr.hasNext()) {
            d = (Destination)itr.next();
            m = d.checkDestinationCapacity(null);
            if (m < 0L) {
                continue;
            }
            if (room < 0L || room > m) {
                room = m;
            }
        }
        if (room == 0) {
            room = 1L;
        } 
        if (room > 0L) {
            if (fetch > room) { 
                fetch = (int)room;
            }
        }
        return fetch;
    }

    public void setSubscription(Subscription sub)
    {
        ackMsgsOnDestroy = false;
        parent = sub;
    }

    /**
     * Triggers loading of any associated destiantions
     */
    public void load() {
        Iterator itr = getDestinations().iterator();
        while (itr.hasNext()) {
            Destination d = (Destination)itr.next();
            try {
                d.load();
            } catch (Exception ex) {}
        }
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String id) {
        creator = id;
    }

    public String getClientID() {
        ConnectionUID cuid = getConnectionUID();
        if (cuid == null) return "<unknown>";

        Connection con = (Connection)Globals.getConnectionManager()
                            .getConnection(cuid);
        return (String)con.getClientData(IMQConnection.CLIENT_ID);
        
    }

    public boolean isDurableSubscriber() {
        if (parent != null)
            return parent.isDurable();
        if (this instanceof Subscription)
            return ((Subscription)this).isDurable();
        return false;
    }

    public boolean getIsFlowPaused() {
        return flowPaused;
    }

    public void msgRetrieved() {
        msgsOut ++;
    }

    public int totalMsgsDelivered()
    {
        return msgsOut;
    }

    public int numPendingAcks()
    {
        Session s = Session.getSession(sessionuid);
        if (s == null) return 0;
        return s.getNumPendingAcks(getConsumerUID());
    }

    public Subscription getSubscription() {
        return parent;
    }

    protected static Selector getSelector(String selstr)
        throws SelectorFormatException
    {
        return Selector.compile(selstr);

    }

    public Map<PartitionedStore, SubSet> getParentList() {
        Map m = new LinkedHashMap<PartitionedStore, SubSet>();
        synchronized(parentListMap) {
            m.putAll(parentListMap);
        }
        return m;
    }

    transient    Object destroyLock = new Object(); 

    public void destroyConsumer(Set delivered, 
                                boolean destroyingDest) {
        destroyConsumer(delivered, (Map)null, false, destroyingDest, true);
    }
   
    public void destroyConsumer(Set delivered, 
                                boolean destroyingDest, boolean notify) {
        destroyConsumer(delivered, (Map)null, false, destroyingDest, notify); 
    }

    public void destroyConsumer(Set delivered, Map remotePendings, 
                boolean remoteCleanup, boolean destroyingDest, boolean notify) {

        if (DEBUG) {
            logger.log(logger.INFO, "destroyConsumer("+delivered.size()+
            ", "+remotePendings+", "+remoteCleanup+", "+destroyingDest+", "+notify+")");
        }

        synchronized(destroyLock) {
            if (!valid) {
               // already removed
                return;
            }
            valid = false; // we are going into destroy, so we are invalid
        }


        Subscription sub = parent;

        if (sub != null) {
            sub.pause("Consumer.java: destroy " + this);
        }

        pause("Consumer.java: destroy ");

        // clean up hooks to any parent list
        if (parentListMap.size() > 0 && plistenerMap.size() > 0) {
            Object pll = null; 
            for (Map.Entry<PartitionedStore, SubSet> pair: parentListMap.entrySet()) {
                 pll = plistenerMap.get(pair.getKey());
                 if (pll != null) {
                     pair.getValue().removeEventListener(pll);
                     plistenerMap.remove(pair.getKey());
                 }
            }
        } 

        List<Destination> ds = getDestinations();

        Map<PartitionedStore, SubSet> oldParent = new LinkedHashMap<PartitionedStore, SubSet>();
        synchronized(parentListMap) {
           oldParent.putAll(parentListMap);
           parentListMap.clear();
        }
        if (parent != null) {
            parent.releaseConsumer(uid);
            parent= null;
            if (notify) {
                try {
                    sendDestroyConsumerNotification(remotePendings, remoteCleanup);
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING,
                        "Sending detach notification for "
                        + uid + " from " + parent, ex);
                }
            }
        } else {
            if ((ds == null || ds.isEmpty()) && !destroyingDest ) {
                // destination already gone
                // can happen if the destination is destroyed 
                logger.log(Logger.DEBUG,"Removing consumer from non-existant destination" + dest);
            } else if (!destroyingDest) {
                Iterator itr = ds.iterator();
                while (itr.hasNext()) {
                    Destination d = null;
                    try {
                        d= (Destination)itr.next();
                        d.removeConsumer(uid, remotePendings, remoteCleanup, notify); 
                    } catch (Exception ex) {
                        logger.logStack(Logger.WARNING, "removing consumer "
                              + uid + " from " + d, ex);
                    }
                }
            }
        }
        if (DEBUG)
             logger.log(Logger.DEBUG,"Destroying consumer " + this + "[" +
                  delivered.size() + ":" + msgs.size() + "]" );


        // now clean up and/or requeue messages
        // any consumed messages will stay on the session
        // until we are done

        Set s = new LinkedHashSet(msgs);
        Reason cleanupReason = (ackMsgsOnDestroy ?
               RemoveReason.ACKNOWLEDGED : RemoveReason.UNLOADED);


        delivered.addAll(s);

        DestinationList DL = Globals.getDestinationList();
       // automatically ack any remote  or ack On Destroy messages
        Iterator itr = delivered.iterator();
        while (itr.hasNext()) {
                PacketReference r = (PacketReference)itr.next();
                if (r == null) {
                    continue;
                }
                if (ackMsgsOnDestroy || !r.isLocal()) {
                    itr.remove();
                     try {
                         if (r.acknowledged(getConsumerUID(), 
                                            getStoredConsumerUID(),
                                            !uid.isUnsafeAck(), r.isLocal())) {
                             try {

                             Destination[] dds = DL.getDestination(r.getPartitionedStore(),
                                                   r.getDestinationUID());
                             Destination d = null;
                             for (int i = 0; i < dds.length; i++) {
                                 d = dds[i];
                                 if (d == null) {
                                     continue;
                                 }
                                 if (r.isLocal()) {
                                     d.removeMessage(r.getSysMessageID(),
                                                     RemoveReason.ACKNOWLEDGED);
                                 } else {
                                     d.removeRemoteMessage(r.getSysMessageID(),
                                                   RemoveReason.ACKNOWLEDGED, r);
                                 }
                             }

                             } finally {
                                 r.postAcknowledgedRemoval();
                             }
                         }
                     } catch(Exception ex) {
                         logger.log(Logger.DEBUG,"Broker down Unable to acknowlege"
                            + r.getSysMessageID() + ":" + uid, ex);
                     }
                }
        }
                
      
        msgs.removeAll(s, cleanupReason);

        if (!ackMsgsOnDestroy) {
                Map<PartitionedStore, Set> map = new LinkedHashMap();
                Set set = null;
                PacketReference ref = null;
                PartitionedStore pstore = null;
                itr = delivered.iterator(); 
                while (itr.hasNext()) {
                    ref = (PacketReference)itr.next();
                    if (ref.getDestinationUID().isQueue()) {
                        pstore = ref.getPartitionedStore();
                    } else {
                        pstore = new NoPersistPartitionedStoreImpl(getStoredConsumerUID());
                    }
                    set = map.get(pstore); 
                    if (set == null) {
                        set = new LinkedHashSet();
                        map.put(pstore, set);
                    }
                    set.add(ref);
                }
                SubSet pl = null;
                for (Map.Entry<PartitionedStore, Set> pair: map.entrySet()) {
                    pl = oldParent.get(pair.getKey());
                    if (pl != null) {
                        ((Prioritized)pl).addAllOrdered(pair.getValue());
                    }
                }
                delivered.clear(); // help gc
        } 
  
        destroy();

        if (msgs != null && mlistener != null) {
            msgs.removeEventListener(mlistener);
            mlistener = null;
        }

        if (sub != null) {
            sub.resume("Consumer.java: destroyConsumer " + this);
        }
        selstr = null;
        selector = null;
    }

    /**
     * This method is called from ConsumerHandler.
     */
    public void sendCreateConsumerNotification() throws BrokerException {

        // OK, we may have multiple consumers
        // we just want the first one
       
        Destination d = getFirstDestination();

        if ((dest.isWildcard() || (! d.getIsLocal() && ! d.isInternal() && ! d.isAdmin())) &&
            Globals.getClusterBroadcast() != null) {
            Globals.getClusterBroadcast().createConsumer(this);
        }
    }

    public void sendDestroyConsumerNotification(Map remotePendings, boolean remoteCleanup) 
                throws BrokerException {
        // OK, we may have multiple consumers
        // we just want the first one
       
        Destination d = getFirstDestination();

        if ( dest.isWildcard() || (d != null && ! d.getIsLocal() && ! d.isInternal() && ! d.isAdmin() &&
            Globals.getClusterBroadcast() != null)) {
            Globals.getClusterBroadcast().destroyConsumer(this, remotePendings, remoteCleanup);
        }
    }

    protected void destroy() {
        valid = false;
        pause("Consumer.java: destroy()");
        synchronized (consumers){
            consumers.remove(uid);
        }
        wildcardConsumers.remove(uid);
        selector = null; // easier for weak hashmap
        Reason cleanupReason = RemoveReason.UNLOADED;
        if (ackMsgsOnDestroy) {
            cleanupReason = RemoveReason.ACKNOWLEDGED;
        }
        Set s = new HashSet(msgs);

        try {
            DestinationList DL =  Globals.getDestinationList();
            synchronized (s) {
                Iterator itr = s.iterator();
                while (itr.hasNext()) {
                    PacketReference pr = (PacketReference)itr.next();
                    if (ackMsgsOnDestroy && pr.acknowledged(getConsumerUID(),
                              getStoredConsumerUID(),
                             !uid.isUnsafeAck(), true)) {
                        try {

                        Destination[] ds = DL.getDestination(pr.getPartitionedStore(),
                                              pr.getDestinationUID()); 
                        Destination d = ds[0];
                        if (pr.isLocal()) {
                            d.removeMessage(pr.getSysMessageID(), cleanupReason);
                        } else {
                            d.removeRemoteMessage(pr.getSysMessageID(), cleanupReason, pr);
                        }

                        } finally {
                            pr.postAcknowledgedRemoval();
                        }
                    }
                }
            }
            msgs.removeAll(s, cleanupReason);
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, br.getKString(
            br.W_EXCEPTION_CLEANUP_CONSUMER, this, ex.getMessage()), ex);
        }
    }

    /**
     * the list (if any) that the consumer can pull
     * messages from
     */
    public void setParentList(PartitionedStore pstore, SubSet set) {
        if (DEBUG) {
            logger.log(Logger.INFO, "Consumer.setParentList("+pstore+", "+set+")");
        }
        ackMsgsOnDestroy = false;
        SubSet pl = parentListMap.get(pstore);
        if (pl != null ) {
            Object pll = plistenerMap.get(pstore);
            if (pll != null) {
                pl.removeEventListener(pll);
            }
        }
        plistenerMap.remove(pstore);

        synchronized(parentListMap) {
            if (set != null) {
                parentListMap.put(pstore, set);
                plistenerMap.put(pstore, set.addEventListener(
                                 busylistener, EventType.EMPTY, null));
            }
        }
        checkState(null);
    }


    protected void getMoreMessages(int num) {
        Map<PartitionedStore, SubSet> ss = new LinkedHashMap<PartitionedStore, SubSet>();
        synchronized(parentListMap) {
            ss.putAll(parentListMap);
        }
        if (paused || ss.size() == 0 || isParentListEmpty(ss) || (parent != null &&
             parent.isPaused())) {
                // nothing to do
                return;
        } else {
            // pull
            int count = 0;
            if (isParentListEmpty(ss))  {
                  return;
            }
            SubSet pl = null;
            while (!isFailover && isActive() &&  !isPaused() && isValid() && ss != null 
               && (pl = getNonEmptyParentList(ss, pl)) != null && count < num && (parent == null ||
                  !parent.isPaused())) {

                    PacketReference mm = (PacketReference)pl.removeNext();
                    if (mm == null)  {
                        continue;
                    }
                    msgs.add(11-mm.getPriority(), mm);
                    count ++;
                    busy = true;
            }
        }

    }

    void setIsActiveConsumer(boolean active) {
        isFailover = !active;
        checkState(null);
    }

    public boolean getIsFailoverConsumer() {
        return isFailover;
    }

    public boolean getIsActiveConsumer() {
        return !isFailover;
    }

    /**
     * handles transient data when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        logger = Globals.getLogger();
        remotePendingResumes = new ArrayList();
        lastDestMetrics = new Hashtable();
        localConsumerCreationReady = false;
        parent = null;
        busy = false;
        paused = false;
        flowPaused = false;
        flowCount = 0;
        active = true;
        valid = true;
        destroyLock = new Object();
        destinationMap = Collections.synchronizedMap(
            new LinkedHashMap<PartitionedStore, LinkedHashSet<Destination>>());
        parentListMap = Collections.synchronizedMap(
                      new LinkedHashMap<PartitionedStore, SubSet>());
        plistenerMap = Collections.synchronizedMap(
                     new LinkedHashMap<PartitionedStore, Object>());
        mlistener = null;
        prefetch=-1;
        isFailover = false;
        position = 0;
        isSpecialRemote = false;
        useConsumerFlowControl = false;
        stored_uid = null;
        active = true;
        pauseCnt = 0;
        try {
            selector = getSelector(selstr);
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR,"Internal Error: bad stored selector["
                  + selstr + "], ignoring", ex);
            selector = null;
        }
        initInterest();
    }

    public boolean isBusy() {
        return busy;
    }

    public DestinationUID getDestinationUID() {
        return dest;
    }

    protected Consumer(ConsumerUID uid) {
        // used for removing a consumer during load problems
        // only
        // XXX - revisit changing protocol to use UID so we
        // dont have to do this 
        this.uid = uid;
    }

    public static Consumer newInstance(ConsumerUID uid) {
        return new Consumer(uid);
    }

    public Consumer(DestinationUID d, String selstr, 
            boolean noLocal, ConnectionUID con_uid)
        throws IOException,SelectorFormatException
    {
        dest = d;
        this.noLocal = noLocal;
        uid = new ConsumerUID();
        uid.setConnectionUID(con_uid);
        this.selstr = selstr;
        selector = getSelector(selstr);
        initInterest();
        if (DEBUG) {
            logger.log(Logger.INFO, "Consumer: created new consumer "+
                uid+ " on destination "+d+" with selector "+selstr);
        }
              
    }

    public Consumer(DestinationUID d, String selstr,
            boolean noLocal, ConsumerUID uid)
        throws IOException,SelectorFormatException
    {
        dest = d;
        this.noLocal = noLocal;
        this.uid = uid;
        if (uid == null)
            this.uid = new ConsumerUID();

        this.selstr = selstr;
        selector = getSelector(selstr);
        initInterest();
    }

    public static Consumer newConsumer(DestinationUID destid, String selectorstr,
                                       boolean isnolocal, ConsumerUID consumerid)
                                       throws IOException,SelectorFormatException,
                                       ConsumerAlreadyAddedException {
        synchronized(consumers) {
            if (consumers.get(consumerid) != null) {
                throw new ConsumerAlreadyAddedException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.I_CONSUMER_ALREADY_ADDED,
                    consumerid, destid)); 
            }
            return new Consumer(destid, selectorstr, isnolocal, consumerid);
        }
    }

    public void localConsumerCreationReady() {
        synchronized(consumers) {
            localConsumerCreationReady = true;
        }
    }

    private boolean isLocalConsumerCreationReady() {
        synchronized(consumers) {
            return localConsumerCreationReady;
        }
    }

    public boolean isValid() {
        return valid;
    }

    protected void initInterest()
    {
        removeListener = new RemoveListener();
        busylistener = new BusyListener();
        evb = new EventBroadcastHelper();
        msgs = new NFLPriorityFifoSet(12, false);
        mlistener = msgs.addEventListener(
             busylistener, EventType.EMPTY, null);
        synchronized(consumers){
            consumers.put(uid, this);
        }
        if (dest.isWildcard())
            wildcardConsumers.add(uid);

    }

    protected static void attachConsumers(DestinationList dl)
    throws BrokerException {
        try {

        DestinationList DL = Globals.getDestinationList();
        Consumer c = null;
        DestinationUID did = null;
        synchronized(consumers) {
            Iterator itr = consumers.values().iterator();
            while (itr.hasNext()) {
                c = (Consumer)itr.next();
                if (c instanceof Subscription) {
                    continue;
                }
                did = c.getDestinationUID();
                if (did.isWildcard()) {
                    List<DestinationUID> dids = DL.findMatchingIDsByDestinationList(dl, did);
                    Iterator itr1 = dids.iterator();
                    while (itr1.hasNext()) {
                        DestinationUID match_did = (DestinationUID)itr1.next();
                        Destination d = DL.getDestinationByDestinationList(dl, match_did);
                        d.addConsumer(c, false, null, true);
                        Globals.getLogger().log(Logger.INFO,
                            "XXXI18N attached regular consumer "+c +" to partition "+dl);
                      }

                } else {
                    Destination d = DL.getDestinationByDestinationList(
                                        dl, did.getName(),
                                        (did.isQueue() ? DestType.DEST_TYPE_QUEUE
                                         : DestType.DEST_TYPE_TOPIC) , true, true);
                    d.addConsumer(c, false, null, true);
                    Globals.getLogger().log(Logger.INFO, 
                        "XXXI18N attached regular consumer "+c+" to partition "+dl);
                }

            }
        }
        } catch (Exception e) {
            Globals.getLogger().logStack(Logger.ERROR, e.getMessage(), e);
            if (e instanceof BrokerException) {
                throw (BrokerException)e;
            }
            throw new BrokerException(e.getMessage(), e);
        }
    }

    public PacketReference  peekNext() {
        // ok first see if there is anything on msgs
        PacketReference ref = (PacketReference)msgs.peekNext();
        if (ref == null && parentListMap.size() > 0)
            synchronized(parentListMap) { 
                Iterator<SubSet> itr = parentListMap.values().iterator();
                while (itr.hasNext()) {
                    ref = (PacketReference)itr.next().peekNext();
                    if (ref != null) {
                        break;
                    }
                }
            }
        return ref;
    }

    public void setAckMsgsOnDestroy(boolean ack) {
        ackMsgsOnDestroy = ack;
    }

    public ConsumerUID getConsumerUID() {
        return uid;
    }

    public void setStoredConsumerUID(ConsumerUID uid) {
        stored_uid = uid;
    }
    public ConsumerUID getStoredConsumerUID() {
        if (stored_uid == null) {
            return uid;
        }
        return stored_uid;
    }

    public void messageCommitted(SysMessageID sysid) {
        if (MSG_MAX_CONSECUTIVE_ROLLBACKS <= 0) {
            return;
        }
        synchronized(messageRollbacks) {
            messageRollbacks.clear();
        }
    }

    public boolean addRollbackCnt(SysMessageID sysid, int maxRollbacks) {
        if (maxRollbacks <= 0 && MSG_MAX_CONSECUTIVE_ROLLBACKS <= 0) {
            return true;
        }
        int maxrb = maxRollbacks;
        if (maxRollbacks <= 0) {
            maxrb = MSG_MAX_CONSECUTIVE_ROLLBACKS;
        }
        synchronized(messageRollbacks) {
            Integer count = (Integer)messageRollbacks.get(sysid);
            if (count == null) {
                messageRollbacks.put(sysid, Integer.valueOf(0));
                return true;
            }
            int cnt = count.intValue();
            if (cnt < maxrb) { 
                messageRollbacks.put(sysid, Integer.valueOf(++cnt));
                return true;
            }
            return false;
        }
    }

    public boolean routeMessages(Collection c, boolean toFront) {
        if (toFront) {
            if (!valid)
                return false;
            msgs.addAllToFront(c,0);
            synchronized (destroyLock) {
                msgsToConsumer += c.size();
            }
            checkState(null);
        } else {
            Iterator itr = c.iterator();
            while (itr.hasNext()) {
                routeMessage((PacketReference)itr.next(), false);
            }
        }
        if (!valid) return false;
        return true;
    }
    
    /**
     * called from load transactions, when we need to unroute 
     * a message that has been consumed in a prepared transaction
     */
    public boolean unrouteMessage(PacketReference p)
    {
    	 boolean b = msgs.remove(p);
         if (b) {
             msgsToConsumer --;
         }
         return b;
    }

    /**
     * can be called from cluster dispatch thread for remote messages  
     * on return false, must ensure message not routed
     */
    public boolean routeMessage(PacketReference p, boolean toFront) {
        return routeMessage(p, toFront, false);
    }

    /**
     * @param toFront ignored if ordered true
     */
    public boolean routeMessage(PacketReference p, boolean toFront, boolean ordered) { 
        int position = 0;
        if (!toFront && !ordered) {
            position = 11 - p.getPriority();
        }

        ArrayList a = null;
        if (ordered) {
            a = new ArrayList();
            a.add(p);
        } 

        synchronized(destroyLock) {
            if (!valid) {
                return false;
            }
            if (ordered) {
                msgs.addAllOrdered(a);
            } else {
                msgs.add(position, p);
            }
            msgsToConsumer ++;
        }
        
        checkState(null);

        if (a != null) {
            a.clear();
            a = null;
        }
        return true;
    }

    public int size() {
        return msgs.size();
    }

    // messages in this list may be in:
    //   - in msgs;
    //   - in session list
    public int numInProcessMsgs() {
        int cnt =size();
        cnt += numPendingAcks();
        return cnt;
    }

    public void unloadMessages() {
        msgs.clear();
        synchronized (destroyLock) {
            msgsToConsumer = 0;
        }
    }    

    public void attachToConnection(ConnectionUID uid)
    {
        this.conuid = uid;
        this.uid.setConnectionUID(uid);
    }

    public void attachToSession(SessionUID uid) 
    {
        this.sessionuid = uid;
    }

    public void attachToDestination(Destination d, PartitionedStore pstore) {
        synchronized (destinationMap) {
            LinkedHashSet<Destination> ds = destinationMap.get(pstore);
            if (ds == null) {
                ds = new LinkedHashSet<Destination>();
                destinationMap.put(pstore, ds);
                ds.add(d);
            }
        }
    }

    public Set getUniqueDestinations() {
        LinkedHashSet<Destination> snapshot = new LinkedHashSet<Destination>();
        snapshot.addAll(getDestinations());
        return snapshot;
    }

    protected List<Destination> getDestinations() {
        List<Destination> snapshot = new ArrayList<Destination>();

        synchronized (destinationMap){
            if (destinationMap.size() == 0) {
                DestinationList DL = Globals.getDestinationList();
                if (!dest.isWildcard()) {
                    Map<PartitionedStore, Destination> mp = DL.getDestinationMap(null, dest);
                    LinkedHashSet set = null;
                    for (Map.Entry<PartitionedStore, Destination> pair: mp.entrySet()) {
                        set = destinationMap.get(pair.getKey());
                        if (set == null) {
                            set = new LinkedHashSet<Destination>();
                            destinationMap.put(pair.getKey(), set);
                        }
                        set.add(pair.getValue());
                    }
                } else {
                    Map<PartitionedStore, LinkedHashSet<Destination>> mp = 
                        DL.findMatchingDestinationMap(null, dest);
                    destinationMap.putAll(mp);
                }
            } 
            Iterator<LinkedHashSet<Destination>> itr = destinationMap.values().iterator();
            while (itr.hasNext()) {
                snapshot.addAll(itr.next());
            }
        }
        return snapshot;
    }

    public Destination getFirstDestination()
    {
        Iterator itr = getDestinations().iterator();
        Destination d = (Destination) (itr.hasNext() ? itr.next() : null);
        return d;
    }

    public SessionUID getSessionUID() {
        return sessionuid;
    }
    public ConnectionUID getConnectionUID()
    {
        return conuid;
    }

    public Object getAndFillNextPacket(Packet p) 
    {
        PacketReference ref = null;

        if (flowPaused || paused) {
            checkState(null);
            return null;
        }
        if (!valid) {
            return null;
        }
        if (!paused &&  msgs.isEmpty()) {
            getMoreMessages(prefetch <=0 ? 1000 : prefetch);
        }

        Packet newpkt = null;

        while (valid && !paused && !msgs.isEmpty()) {
            if (FI.FAULT_INJECTION) { 
                HashMap fips = new HashMap();
                fips.put(FaultInjection.DST_NAME_PROP, getDestinationUID().getName());
                if (FI.checkFaultAndSleep(
                    FI.FAULT_CONSUMER_SLEEP_BEFORE_MSGS_REMOVE_NEXT, fips)) {
                    FI.unsetFault(FI.FAULT_CONSUMER_SLEEP_BEFORE_MSGS_REMOVE_NEXT);
                }
            }
            ref= (PacketReference)msgs.removeNext();
            if (ref == null || ref.isOverrided()) {
                int loglevel = (DEBUG_CLUSTER_MSG) ? Logger.INFO:Logger.DEBUG; 
                logger.log(loglevel,  (ref == null) ? 
                "Consumer ["+getConsumerUID()+"] get message null reference":
                "Consumer ["+getConsumerUID()+"] message requened: "+ref);
                continue;
            }
            newpkt = ref.getPacket();
            boolean expired = ref.isExpired();
            if (FI.FAULT_INJECTION) { 
                HashMap fips = new HashMap();
                fips.put(FaultInjection.DST_NAME_PROP,
                             ref.getDestinationUID().getName());
                if (FI.checkFault(FI.FAULT_MSG_EXPIRE_AT_DELIVERY, fips)) {
                    expired = true;
                    FI.unsetFault(FI.FAULT_MSG_EXPIRE_AT_DELIVERY);
                }
            }
            boolean islocal = ref.isLocal();
            boolean remoteDeliveredAck = (!islocal && 
                                          ref.getMessageDeliveredAck(uid));
            if (expired || 
                !ref.checkRemovalAndSetInDelivery(getStoredConsumerUID())) {  
                if (!expired) {
                    expired = ref.isExpired();
                }
                try {
                    if (expired) {

                    String cmt = br.getKString(br.I_RM_EXPIRED_MSG_BEFORE_DELIVER_TO_CONSUMER,
                                 ref.getSysMessageID(), "["+uid+", "+uid.getAckMode()+"]"+dest);
                    String cmtr = br.getKString(br.I_RM_EXPIRED_REMOTE_MSG_BEFORE_DELIVER_TO_CONSUMER,
                                  ref.getSysMessageID(), "["+uid+", "+uid.getAckMode()+"]"+dest);

                    if (ref.markDead(uid, getStoredConsumerUID(), 
                                     (islocal ? cmt:cmtr), null,
                                     RemoveReason.EXPIRED_ON_DELIVERY,
                                     -1, null, remoteDeliveredAck)) {
                        try {

                        boolean removed = false;
                        DestinationList DL = Globals.getDestinationList();
                        Destination[] ds = DL.getDestination(ref.getPartitionedStore(),
                                               ref.getDestinationUID());
                        Destination d = ds[0];
                        if (d != null) {
                            if (ref.isDead()) {
                                removed = d.removeDeadMessage(ref);
                            } else {
                                removed = d.removeMessage(ref.getSysMessageID(),
                                              RemoveReason.REMOVED_OTHER, !expired);
                            }
                            if (removed && expired && DL.getVerbose()) {
                                logger.log(logger.INFO, (islocal ? cmt:cmtr));
                            }
                        }

                        } finally {
                            ref.postAcknowledgedRemoval();
                        }
                    }

                    } else {
                    if (DEBUG) {
                        logger.log(Logger.INFO, 
                        "Message "+ref+(newpkt == null ? "[null]":"")+
                        " in removal,  not deliver to consumer "+this);
                    }
                    if (remoteDeliveredAck) {
                        try {
                            Map props = new HashMap();
                            props.put(Consumer.PREFETCH, Integer.valueOf(1));
                            Globals.getClusterBroadcast().acknowledgeMessage(
                                ref.getBrokerAddress(), ref.getSysMessageID(),
                                uid, ClusterBroadcast.MSG_DELIVERED, props, false);
                        } catch (Exception e) {
                            if (DEBUG) {
                            logger.logStack(Logger.INFO, "Cannot send DELIVERED ack for message "+
                                       ref+" for consumer "+uid, e);
                            }
                        }
                    }
                    }

                } catch (Exception ex) {
                    if (newpkt != null && DEBUG) {
                        logger.logStack(Logger.INFO, 
                        "Unable to cleanup expired message "+ref+" for consumer "+this, ex);
                    }
                }
                ref = null;
                newpkt = null;
                continue;
            }

            break;
        }
        if (!valid) {
            if (DEBUG_CLUSTER_MSG) {
                logger.log(Logger.INFO, "getAndFillNextPacket(): consumer "+this+
                                        " closed, discard ref "+ref);
            }
            return null;
        }
        if (ref == null) {
            checkState(null);
            return null;
        }

        newpkt = ref.getPacket();
        if (newpkt == null) {
            assert false;
            return null;
        }

        if (p != null) {
            try {
                p.fill(newpkt);
            } catch (IOException ex) {
                logger.logStack(Logger.INFO,"Internal Exception processing packet ", ex);
                return null;
            }
            p.setConsumerID(uid.longValue());
            ConsumerUID suid = getStoredConsumerUID();
            boolean rflag = ref.getRedeliverFlag(suid);
            p.setRedelivered(rflag);
            int rcnt = ref.getRedeliverCount(suid)+1;
            if (rflag) {
                if (rcnt < 2) {
                    rcnt = 2;
                }
            }
            if (DEBUG) {
                logger.log(Logger.INFO, 
                "Consumer.getAndFillNextPacket: Set DeliveryCount to "+rcnt+" for message "+ref+" for consumer "+this);
            }
            p.setDeliveryCount(rcnt);
            if (ref.isLast(uid)) {
                ref.removeIsLast(uid);
                p.setIsLast(true);
            }
            msgRetrieved();
            if (parent != null) {
                // hey, we pulled one from the durable too
                parent.msgRetrieved();
            }
        } else {
            newpkt.setRedelivered(ref.getRedeliverFlag(getStoredConsumerUID()));
        }

        if (useConsumerFlowControl) {
           if (prefetch != -1) {
              flowCount ++;
           }
           if (!flowPaused && ref.getMessageDeliveredAck(uid)) {
               BrokerAddress addr = ref.getBrokerAddress();
               if (addr != null) { // do we have a remove
                   synchronized(remotePendingResumes) {
                       remotePendingResumes.add (ref);
                   }
               } else {
               }
               if (p != null) {
                   p.setConsumerFlow(true);
                }
            } 
            if (prefetch > 0 && flowCount >= prefetch ) {
                if (p != null) {
                    p.setConsumerFlow(true);
                }
                ref.addMessageDeliveredAck(uid);
                BrokerAddress addr = ref.getBrokerAddress();
                if (addr != null) { // do we have a remove
                    synchronized(remotePendingResumes) {
                       remotePendingResumes.add (ref);
                    }
                }
                pauseFlowCnt ++;
                flowPaused = true;
            }
        } else if (ref.getMessageDeliveredAck(uid)) {
            HashMap props = null;
            ConnectionUID cuid = getConnectionUID();
            if (cuid != null) {
                IMQConnection con = (IMQConnection)Globals.getConnectionManager().
                                                         getConnection(cuid);
                if (con != null) {
                    props = new HashMap();
                    props.put(Consumer.PREFETCH, 
                              Integer.valueOf(con.getFlowCount()));
                }
            }
            try {
                Globals.getClusterBroadcast().acknowledgeMessage(
                    ref.getBrokerAddress(), ref.getSysMessageID(),
                    uid, ClusterBroadcast.MSG_DELIVERED, props, false);
            } catch (BrokerException ex) {
                logger.log(Logger.DEBUG,"Can not send DELIVERED ack "
                     + " received ", ex);
            }
            ref.removeMessageDeliveredAck(uid);
       }
       return ref;

    }



    public void purgeConsumer() 
         throws BrokerException
    {
        Reason cleanupReason = RemoveReason.ACKNOWLEDGED;
        Set set = new HashSet(msgs);
        if (set.isEmpty()) return;
        msgs.removeAll(set, cleanupReason);
        Iterator itr = set.iterator();
        while (itr.hasNext()) {
            try {
                PacketReference pr = (PacketReference)itr.next();
                if (pr.acknowledged(getConsumerUID(),
                       getStoredConsumerUID(),
                       !uid.isUnsafeAck(), true)) {
                     try {

                     DestinationList DL = Globals.getDestinationList();
                     Destination[] ds = DL.getDestination(pr.getPartitionedStore(),
                                                          pr.getDestinationUID());
                     Destination d = ds[0];
                     d.removeMessage(pr.getSysMessageID(), cleanupReason);

                     } finally {
                         pr.postAcknowledgedRemoval();
                     }
                }
            } catch (IOException ex) {
                logger.logStack(Logger.WARNING, "Purging consumer " 
                        + this , ex);
            }
        }
                
    }

    public boolean isWildcard() {
        return dest.isWildcard();
    }

    public void purgeConsumer(Filter f) 
         throws BrokerException
    {
        Reason cleanupReason = RemoveReason.ACKNOWLEDGED;
        Set set = msgs.getAll(f);
        msgs.removeAll(set, cleanupReason);
        Iterator itr = set.iterator();
        while (itr.hasNext()) {
            try {
                PacketReference pr = (PacketReference)itr.next();
                if (pr.acknowledged(getConsumerUID(),
                      getStoredConsumerUID(),
                     !uid.isUnsafeAck(), true)) {
                     try {

                     DestinationList DL = Globals.getDestinationList();
                     Destination[] ds = DL.getDestination(pr.getPartitionedStore(),
                                          pr.getDestinationUID());
                     Destination d = ds[0];
                     d.removeMessage(pr.getSysMessageID(), cleanupReason);

                     } finally {
                         pr.postAcknowledgedRemoval();
                     }
                }
            } catch (IOException ex) {
                logger.logStack(Logger.WARNING, "Problem purging consumer " 
                        + this, ex);
            }
        }
                
    }

    public void activate() {
        active = true;
        checkState(null);
    }

    public void deactive() {
        active = false;
        checkState(null);
    }

    public void pause(String reason) {
        synchronized(msgs) {
            paused = true;
            pauseCnt ++;
            if (DEBUG)
                logger.log(logger.DEBUG,"Pausing consumer " + this 
                      + "[" + pauseCnt + "] " + reason);
        }
        checkState(null);
    }

    public void resume(String reason) {
        synchronized(msgs) {
            pauseCnt --;
            if (pauseCnt <= 0) {
                paused = false;
            }
            if (DEBUG)
               logger.log(logger.DEBUG,"Pausing consumer " + this 
                     + "[" + pauseCnt + "] " + reason);
        }
        checkState(null);
    }

    public void setFalconRemote(boolean notlocal)
    {
        isSpecialRemote = notlocal;
    }

    public boolean isFalconRemote() {
        return isSpecialRemote;
    }

    //not used 
    HashSet remotePendingDelivered = new HashSet();

    transient private ArrayList remotePendingResumes = new ArrayList();
    transient private Hashtable lastDestMetrics = new Hashtable();

    //return 0, yes; 1 no previous sampling, else no
    public int checkIfMsgsInRateGTOutRate(Destination d) {

        Subscription sub = getSubscription();
        if (!DestinationList.CHECK_MSGS_RATE_FOR_ALL && 
            !(sub != null && sub.getShared() && !sub.isDurable())) {
            return 2;
        }
        if (DestinationList.CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO < 0) {
            return 2;
        }
        float pc =Math.max(Globals.getDestinationList().totalCountPercent(),
                         d.destMessagesSizePercent());
        if (pc < (float)DestinationList.CHECK_MSGS_RATE_AT_DEST_CAPACITY_RATIO) {
            return 2;
        }
        synchronized(lastDestMetrics) {
            DestinationUID did = d.getDestinationUID(); 
            long[] holder = (long[])lastDestMetrics.get(did);
            if (holder == null) {
                holder = new long[6];
                d.checkIfMsgsInRateGTOutRate(holder, true);
                lastDestMetrics.put(did, holder);
                return 1;
            }
            return d.checkIfMsgsInRateGTOutRate(holder, false);
        }
    }

    public long getMsgOutTimeMillis(Destination d) {
        synchronized(lastDestMetrics) {
            DestinationUID did = d.getDestinationUID(); 
            long[] holder = (long[])lastDestMetrics.get(did);
            if (holder == null) {
                holder = new long[6];
                d.checkIfMsgsInRateGTOutRate(holder, true);
                lastDestMetrics.put(did, holder);
                return -1;
            }
            d.checkIfMsgsInRateGTOutRate(holder, false);
            return holder[5];
        }
    }

    /**
     * resume flow not changing flow control
     * from remote broker
     */
    public void resumeFlow() {

        // deal w/ remote flow control 
        synchronized (remotePendingResumes) {
            if (!remotePendingResumes.isEmpty()) {
               Iterator itr = remotePendingResumes.iterator();
               while (itr.hasNext()) {
                   PacketReference ref = (PacketReference)itr.next();
                   //DELIVERED translated to resume flow on remote client
                   try {
                        Globals.getClusterBroadcast().acknowledgeMessage(
                            ref.getBrokerAddress(), ref.getSysMessageID(),
                            uid, ClusterBroadcast.MSG_DELIVERED, null, false);
                   } catch (BrokerException ex) {
                        logger.log(Logger.DEBUG,"Can not send DELIVERED ack "
                             + " received ", ex);
                   }
                   itr.remove();
               }
            }
        }
        if (flowPaused) { 
            resumeFlowCnt ++;
            flowCount = 0;
            flowPaused = false;
            checkState(null); 
        }
    }

    private void resumeRemoteFlow(int id) {
        synchronized (remotePendingResumes) {
            if (!remotePendingResumes.isEmpty()) {
               boolean emptyp = (parentListMap.size() == 0 || isParentListEmpty(parentListMap)); 
               int remoteSize = remotePendingResumes.size();

               //estimate remote prefetch
               int prefetchVal = id; 
               if (id > 0) { 
                   if (prefetchVal != 1) {
                       prefetchVal = id/(remoteSize+(emptyp ? 0:1));
                       if (prefetchVal == 0) { 
                           prefetchVal = 1;
                       }
                   }
               }
               int myprefetch = prefetchVal;
               int totalPrefetched = 0;
               BrokerAddress addr = null;
               ArrayList addrs = new ArrayList();
               Destination[] ds = null;
               Destination d = null;
               PacketReference ref = null;
               HashMap props = null;
               long systemrm, destrm, room;
               DestinationList DL = Globals.getDestinationList();
               Iterator itr = remotePendingResumes.iterator();
               while (itr.hasNext()) {
                   ref = (PacketReference)itr.next();
                   addr = ref.getBrokerAddress();
                   if (addrs.contains(addr)) {
                       itr.remove();
                       continue;
                   }
                   myprefetch = prefetchVal;
                   destrm = room = -1L; 
                   props = new HashMap();
                   ds = DL.findDestination(ref.getPartitionedStore(),
                                           ref.getDestinationUID());
                   d = ds[0];
                   try { 
                       systemrm = DL.checkSystemLimit(ref);
                   } catch (Throwable t) {
                       systemrm = 0;
                   }
                   if (d != null) {
                       destrm = d.checkDestinationCapacity(ref);
                   }
                   if (systemrm >= 0 && destrm >= 0) {
                       room = Math.min(systemrm, destrm);
                   } else if(systemrm >= 0) {
                       room = systemrm;
                   } else {
                       room = destrm;
                   }
                   if (room == 0) {
                       room = 1;
                   }
                   myprefetch = prefetchVal;
                   if (room > 0) {
                       myprefetch = (int)(room - totalPrefetched);
                       if (myprefetch <= 0) {
                           myprefetch = 1;
                       } else {
                           int ret = checkIfMsgsInRateGTOutRate(d);
                           if (ret == 0) {
                               myprefetch = 1;
                           } else if (ret == 1) {
                               myprefetch = 1;
                           }
                       }
                       if (prefetchVal > 0) {
                           myprefetch =  Math.min(prefetchVal, myprefetch);
                           if (myprefetch <= 0) { 
                               myprefetch = 1;
                           }
                       }
                   }
                   //DELIVERED translated to resume flow on remote client
                   try {
                        props.put(Consumer.PREFETCH, Integer.valueOf(myprefetch));
                        Globals.getClusterBroadcast().acknowledgeMessage(
                            addr, ref.getSysMessageID(),
                            uid, ClusterBroadcast.MSG_DELIVERED, props, false);
                        if (addr != null) {
                            addrs.add(addr);
                        }
                        totalPrefetched += myprefetch;
                        itr.remove();
                   } catch (BrokerException ex) {
                        logger.log(Logger.DEBUG,"Can not send DELIVERED ack "
                             + " received ", ex);
                   }
               }
            }
        }
    }

    public void resumeFlow(int id) {
        resumeRemoteFlow(id);
        setPrefetch(id);
        if (flowPaused) { 
            resumeFlowCnt ++;
            flowCount = 0;
            flowPaused = false;
            checkState(null); 
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getSelectorStr() {
        return selstr;
    }
    public Selector getSelector() {
        return selector;
    }

    public boolean getNoLocal() {
        return noLocal;
    }

     /**
     * Request notification when the specific event occurs.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *          publish the event type passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Object userData)
        throws UnsupportedOperationException {

        if (type != EventType.BUSY_STATE_CHANGED ) {
            throw new UnsupportedOperationException("Only " +
                "Busy State Changed notifications supported on this class");
        }
        return evb.addEventListener(listener,type, userData);
    }

    /**
     * Request notification when the specific event occurs AND
     * the reason matched the passed in reason.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @param reason reason which must be associated with the
     *               event (or null for all events)
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *         support the event type or reason passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Reason reason,
                        Object userData)
        throws UnsupportedOperationException
    {
        if (type != EventType.BUSY_STATE_CHANGED ) {
            throw new UnsupportedOperationException("Only " +
                "Busy State Changed notifications supported on this class");
        }
        return evb.addEventListener(listener,type, reason, userData);
    }

    /**
     * remove the listener registered with the passed in
     * id.
     * @return the listener callback which was removed
     */
    public Object removeEventListener(Object id) {
        return evb.removeEventListener(id);
    }
  
    private void notifyChange(EventType type,  Reason r, 
               Object target,
               Object oldval, Object newval) 
    {
        evb.notifyChange(type,r, target, oldval, newval);
    }

    public void dump(String prefix) {
        if (prefix == null)
            prefix = "";
        logger.log(Logger.INFO,prefix + "Consumer: " + uid + " [paused, active,"
            + "flowPaused, parentBusy, hasMessages, parentSize ] = [" 
            + paused + "," + active + "," + flowPaused + "," +
            getParentBusy() + "," + (msgs == null || !msgs.isEmpty()) + ","  
            + parentListMap + "]");
        logger.log(Logger.INFO,prefix +"Busy state [" + uid + "] is " + busy);
        if (msgs == null)
            logger.log(Logger.INFO, "msgs is null");
        else
            logger.log(Logger.INFO, msgs.toDebugString());
    }

    public static Hashtable getAllDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("FlowControlAllowed", String.valueOf(C_FLOW_CONTROL_ALLOWED));
        ht.put("ConsumerCnt", String.valueOf(consumers.size()));
        Iterator itr = getAllConsumers();
        while (itr.hasNext()) {
           Consumer c = (Consumer)itr.next();
           ht.put("Consumer["+c.getConsumerUID().longValue()+"]",
                c.getDebugState());
        }
        return ht;
    }
    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("ConsumerUID", String.valueOf(uid.longValue()));
        ht.put("Broker", (uid.getBrokerAddress() == null ? "NONE" 
                   : uid.getBrokerAddress().toString()));
        ht.put("msgsToConsumer", String.valueOf(msgsToConsumer));
        ht.put("StoredConsumerUID", String.valueOf(getStoredConsumerUID().longValue()));
        ht.put("ConnectionUID", (conuid == null ? "none" : String.valueOf(conuid.longValue())));
        ht.put("type", "CONSUMER");
        ht.put("valid", String.valueOf(valid));
        ht.put("paused", String.valueOf(paused));
        ht.put("pauseCnt", String.valueOf(pauseCnt));
        ht.put("noLocal", String.valueOf(noLocal));
        ht.put("destinationUID", dest.toString());
        ht.put("busy", String.valueOf(busy));
        if (parent != null)
            ht.put("Subscription", String.valueOf(parent.
                     getConsumerUID().longValue()));
        ht.put("isSpecialRemote", String.valueOf(isSpecialRemote));
        ht.put("ackMsgsOnDestroy", String.valueOf(ackMsgsOnDestroy));
        ht.put("position", String.valueOf(position));
        ht.put("active", String.valueOf(active));
        ht.put("flowCount", String.valueOf(flowCount));
        ht.put("flowPaused", String.valueOf(flowPaused));
        ht.put("pauseFlowCnt", String.valueOf(pauseFlowCnt));
        ht.put("resumeFlowCnt", String.valueOf(resumeFlowCnt));
        ht.put("useConsumerFlowControl", String.valueOf(useConsumerFlowControl));
        ht.put("selstr", (selstr == null ? "none" : selstr));
        ht.put("parentListMap", parentListMap.toString());
        ht.put("plistenerMap", plistenerMap.toString());
        ht.put("isParentListEmpty", Boolean.valueOf(isParentListEmpty(parentListMap)));
        if (DestinationList.DEBUG_LISTS) {
            Hashtable h = new Hashtable();
            synchronized(parentListMap) {
                for (Map.Entry<PartitionedStore, SubSet> pair: parentListMap.entrySet()) {
                    h.put(pair.getKey().toString(), pair.getValue().toDebugString());
                } 
            }
            ht.put("ParentListMap(detail)", h);
        }
        ht.put("prefetch", String.valueOf(prefetch));
        ht.put("remotePrefetch", String.valueOf(remotePrefetch));
        ht.put("parentBusy", String.valueOf(getParentBusy()));
        ht.put("hasMessages", String.valueOf(!msgs.isEmpty()));
        ht.put("msgsSize", String.valueOf(msgs.size()));
        if (DestinationList.DEBUG_LISTS) {
            ht.put("msgs", msgs.toDebugString());
        }
        ht.put("isFailover", String.valueOf(isFailover));
        ht.put("localConsumerCreationReady", String.valueOf(localConsumerCreationReady));
        Vector v1 = new Vector();
        ArrayList vals = null;
        synchronized(remotePendingResumes) {
            vals = new ArrayList(remotePendingResumes);
        }
        Iterator itr = vals.iterator();
        while (itr.hasNext()) {
            PacketReference ref = (PacketReference)itr.next();
            String val = ref.getBrokerAddress()+": "+ref;
            v1.add(val);
        }
        ht.put("remotePendingResumes", v1);
        Vector v2 = new Vector();
        synchronized(lastDestMetrics) {
            itr = lastDestMetrics.keySet().iterator();
            while (itr.hasNext()) {
                DestinationUID did = (DestinationUID)itr.next();
                long[] holder = (long[])lastDestMetrics.get(did);
                String val = did+": "+"ins=" +holder[0]+"|outs="+holder[1]+"|time="+holder[2]+
                             "|inr="+holder[3]+"|outr="+holder[4]+"|outt="+holder[5];
                v2.add(val);
            }
        }
        ht.put("lastDestMetrics", v2);
        return ht;
    }

    public Vector getDebugMessages(boolean full) {
        Vector ht = new Vector();
        synchronized(msgs) {
            Iterator itr = msgs.iterator();
            while (itr.hasNext()) {
                PacketReference pr = (PacketReference)itr.next();
                ht.add( (full ? pr.getPacket().dumpPacketString() :
                     pr.getPacket().toString()));
            }
        }
        return ht;
       
    }

    private void checkState(Reason r) {
        // XXX - LKS look into adding an assert that a lock
        // must be held when this is called
        boolean isbusy = false;
        boolean notify = false;
        synchronized (msgs) {
            isbusy = !paused && active && !flowPaused &&
                ( (getParentBusy() && !isFailover) || !msgs.isEmpty());
            notify = isbusy != busy;
            busy = isbusy;
        }
        if (notify) {
            notifyChange(EventType.BUSY_STATE_CHANGED,
                r, this, Boolean.valueOf(!isbusy), Boolean.valueOf(isbusy));
        }
    }


    /**
     * Return a concises string representation of the object.
     * 
     * @return    a string representation of the object.
     */
    public String toString() {
        String str = "Consumer - "+ dest  + ":"+ 
            "["+getConsumerUID()+", "+getStoredConsumerUID()+"]";
        return str;
    }

    public void debug(String prefix) {
        if (prefix == null)
            prefix = "";
        logger.log(Logger.INFO,prefix + toString() );
        String follow = prefix + "\t";
        logger.log(Logger.INFO, follow + "Selector = " + selector);
        logger.log(Logger.INFO, follow + "msgs = " + msgs.size());
        logger.log(Logger.INFO, follow + "parentListMap = " + parentListMap);
        logger.log(Logger.INFO, follow + "parent = " + parent);
        logger.log(Logger.INFO, follow + "valid = " + valid);
        logger.log(Logger.INFO, follow + "active = " + active);
        logger.log(Logger.INFO, follow + "paused = " + paused);
        logger.log(Logger.INFO, follow + "pauseCnt = " + pauseCnt);
        logger.log(Logger.INFO, follow + "noLocal = " + noLocal);
        logger.log(Logger.INFO, follow + "busy = " + busy);
        logger.log(Logger.INFO, follow + "flowPaused = " + flowPaused);
        logger.log(Logger.INFO, follow + "prefetch = " + prefetch);
        logger.log(Logger.INFO,follow + msgs.toDebugString());
    }


    public void setCapacity(int size) {
        msgs.setCapacity(size);
    }
    public void setByteCapacity(long size) {
        msgs.setByteCapacity(size);
    }
    public int capacity() {
        return msgs.capacity();
    }
    public long byteCapacity()
    {
        return msgs.byteCapacity();
    }


    private static Map consumers = Collections.synchronizedMap(new HashMap());

    protected static final Set wildcardConsumers = Collections.synchronizedSet(new HashSet());

    public static void clearAllConsumers()
    {
        consumers.clear();
        wildcardConsumers.clear();
    }

    public static Iterator getAllConsumers() {
        return getAllConsumers(true);
    }
    public static Iterator getAllConsumers(boolean all) {
        synchronized (consumers) {
            HashSet cs = new HashSet(consumers.values());
            if (all) {
                return cs.iterator();
            }
            Consumer co = null;
            Iterator itr = cs.iterator();
            while (itr.hasNext()) {
                co = (Consumer)itr.next();
                if (co instanceof Subscription) {
                    continue;
                }
                if (!co.isLocalConsumerCreationReady()) {
                    itr.remove();
                }
            }
            return cs.iterator();
        }
    }

    public static Iterator getWildcardConsumers() {
        synchronized (consumers) {
            return (new HashSet(wildcardConsumers)).iterator();
        }
    }

    public static int getNumConsumers() {
        return (consumers.size());
    }

    public static int getNumWildcardConsumers() {
        return wildcardConsumers.size();
    }

    public static Consumer getConsumer(ConsumerUID uid)
    {
        synchronized(consumers) {
            Consumer c = (Consumer)consumers.get(uid);
            return c;
        }
    }

    public static Consumer getConsumer(String creator)
    {
        if (creator == null) return null;

        synchronized(consumers) {
            Iterator itr = consumers.values().iterator();
            while (itr.hasNext()) {
                Consumer c = (Consumer)itr.next();
                if (creator.equals(c.getCreator()))
                    return c;
            }
        }
        return null;
    }

     public void setLockPosition(int position) {
         lockPosition = position;
     }
     public int getLockPosition() {
         return lockPosition;
     }

     public void recreationRequested() {
         requestedRecreation = true;
     }
     protected boolean tobeRecreated() {
         return requestedRecreation;
     }
}

