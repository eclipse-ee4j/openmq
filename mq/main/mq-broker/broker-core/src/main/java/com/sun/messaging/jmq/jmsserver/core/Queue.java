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
 * @(#)Queue.java	1.90 11/16/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.util.FeatureUnavailableException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.SelectorFilter;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.ClusterDeliveryPolicy;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;


/**
 * This class represents a queue destination
 */
public class Queue extends Destination
{
    static final long serialVersionUID = 3396316998214097558L;

    private static Object NULL_OBJECT = new Object();

    private static boolean DEBUG = false; 

    private transient NFLPriorityFifoSet pending = null;
    private transient SubSet pendingSubset = null;
    private transient HashSet delivered = null;

    protected transient Map views = null;

    private boolean localDeliveryPreferred = false;
    private int maxActiveCount= 1;
    private int maxFailoverCount=0;
    private int maxSize=(maxActiveCount < 0 || maxFailoverCount < 0) ? -1 
                         :maxActiveCount + maxFailoverCount;

    // I'm using arraylists because I want to know the position ...
    // this may be slow on lots of consumers
    // will need to revisit
    private transient Vector consumerPositions =null;
    private transient Map allConsumers = null;

    private transient int activeConsumerCnt = 0;
    private transient int failoverConsumerCnt =0;
    private transient int localActiveConsumerCnt =0;

    private transient int hwActiveCount = 0;
    private transient int hwFailoverCount =0;

    private transient float activeAverage = (float)0.0;
    private transient float failoverAverage = (float)0.0;

    private transient int activeSampleCnt = 0;
    private transient int failoverSampleCnt = 0;

    public static final String MAX_ACTIVE = "max_active";
    public static final String MAX_FAILOVER = "max_failover";
    public static final String LOCAL_DELIVERY = "local_delivery_preferred";


    public static final int DEFAULT_MAX_ACTIVE_CONSUMERS = -1;

    public static final String MAX_ACTIVE_CNT = Globals.IMQ+
                 ".autocreate.queue.maxNumActiveConsumers";
    private static String MAX_FAILOVER_CNT = Globals.IMQ+
                 ".autocreate.queue.maxNumBackupConsumers";

    private static int defaultMaxActiveCount= Globals.getConfig().
                  getIntProperty(MAX_ACTIVE_CNT, DEFAULT_MAX_ACTIVE_CONSUMERS);

    private static int defaultMaxFailoverCount= Globals.getConfig().
                  getIntProperty(MAX_FAILOVER_CNT, NONE);

    private static Set queueConsumer = null;

    private static int QUEUE_DEFAULT_PREFETCH = Globals.getConfig().
                  getIntProperty(Globals.IMQ+
                 ".autocreate.queue.consumerFlowLimit", 1000);

    private static boolean QUEUE_LDP = Globals.getConfig().
                  getBooleanProperty(Globals.IMQ+
                 ".autocreate.queue.localDeliveryPreferred", false);


    private static int MAX_LICENSED_ACTIVE = -1;
    private static int MAX_LICENSED_BACKUP = -1;

    static {

        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            MAX_LICENSED_ACTIVE = license.getIntProperty(
                                license.PROP_MAX_ACTIVE_CONS, 5);
            MAX_LICENSED_BACKUP = license.getIntProperty(
                                license.PROP_MAX_BACKUP_CONS, 0);
            if (MAX_LICENSED_ACTIVE == Integer.MAX_VALUE)
                MAX_LICENSED_ACTIVE = -1;
            if (MAX_LICENSED_BACKUP == Integer.MAX_VALUE)
                MAX_LICENSED_BACKUP = -1;
        } catch (BrokerException ex) {
            MAX_LICENSED_ACTIVE = 5;
            MAX_LICENSED_BACKUP = 0;
        }
        if (MAX_LICENSED_ACTIVE != -1 && (defaultMaxActiveCount == -1
                   || defaultMaxActiveCount > MAX_LICENSED_ACTIVE)) 
        {
            Globals.getLogger().log(Logger.ERROR,
                 BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
                 Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_PRIMARY_CONSUMERS,
                                String.valueOf(MAX_LICENSED_ACTIVE)));

            com.sun.messaging.jmq.jmsserver.Broker.getBroker().exit(1,
                      Globals.getBrokerResources().getKString(
                             BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
                          Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_PRIMARY_CONSUMERS,
                                String.valueOf(MAX_LICENSED_ACTIVE))),
                      BrokerEvent.Type.FATAL_ERROR);
        }
        if (MAX_LICENSED_BACKUP != -1 && (defaultMaxFailoverCount == -1
                   || defaultMaxFailoverCount > MAX_LICENSED_BACKUP)) 
        {
            Globals.getLogger().log(Logger.ERROR,
                 BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
                 Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_FAILOVER_CONSUMERS,
                                String.valueOf(MAX_LICENSED_BACKUP)));

            com.sun.messaging.jmq.jmsserver.Broker.getBroker().exit(1,
                      Globals.getBrokerResources().getKString(
                             BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
                          Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_FAILOVER_CONSUMERS,
                                String.valueOf(MAX_LICENSED_BACKUP))),
                      BrokerEvent.Type.FATAL_ERROR);
        }

    }

   
    public void unload(boolean refs) {
        super.unload(refs);
        if (refs) { 
            pending.clear();
            delivered.clear();
        }
    } 

    public void sort(Comparator c) {
        // sort pending delivery list
        pending.sort(c);

    }

    public PacketReference peekNext() {
        try {
            if (!loaded)
                load();
        } catch (Exception ex) {
        }
        return (PacketReference) pending.peekNext();
    }


    public int getUnackSize(Set msgset) {
        throw new UnsupportedOperationException(
        "Unsupported operation: getUnackSize(Set)");
    }

    public int getUnackSize() {
        int size = destMessages.size() - pending.size();
        if (size < 0) {
            logger.log(Logger.DEBUG,"Unexpected size for destination "
               + this + " [size,pending]=[" + destMessages.size()
               + "," + pending.size() + "]");
            size = 0;
        }
        return size;
    }

    public static Hashtable getAllDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("maxNumActiveConsumers", String.valueOf(defaultMaxActiveCount));
        ht.put("maxNumBackupConsumers", String.valueOf(defaultMaxFailoverCount));
        ht.put("consumerFlowLimit", String.valueOf(QUEUE_DEFAULT_PREFETCH));
        ht.put("localDeliveryPreferred", String.valueOf(QUEUE_LDP));
        return ht;
    }

    public Hashtable getDebugMessages(boolean full) {
       Hashtable ht = super.getDebugMessages(full);
       Vector p = new Vector();
       Iterator itr = pending.iterator();
       while (itr.hasNext()) {
           PacketReference pr = (PacketReference)itr.next();
           p.add(full ? pr.getPacket().dumpPacketString()
                : pr.getPacket().toString());
       }
       ht.put("PendingList",p);
       return ht;
    }

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        Vector v = null;
        synchronized (consumerPositions) {
            v = new Vector(consumerPositions.size());
            for (int i=0; i < v.size(); i ++) {
                Object o = consumerPositions.get(i);
                v.add((o == NULL_OBJECT ? "none" :
                     String.valueOf(((ConsumerUID)o).longValue())));
            }
        }
        ht.put("ConsumerPositions", v);
        ht.put("pendingCnt",String.valueOf(pending.size()));
        if (DEBUG && DestinationList.DEBUG_LISTS) {
            ht.put("pending", pending.toDebugString());
        }
        ht.put("deliveredCnt",String.valueOf(delivered.size()));
        ht.put("localDeliveryPreferred",String.valueOf(localDeliveryPreferred));
        ht.put("maxActiveCount",String.valueOf(maxActiveCount));
        ht.put("maxFailoverCount",String.valueOf(maxFailoverCount));
        ht.put("maxSize",String.valueOf(maxSize));
        synchronized (allConsumers) {
            v = new Vector(allConsumers.size());
            Iterator itr = allConsumers.values().iterator();
            while (itr.hasNext()) {
                QueueInfo qi = (QueueInfo)itr.next();
                String info = qi.consumer.getConsumerUID().longValue() 
                         + "["+qi.position + "," +
                         ( qi.active ? "active" : "inactive") + "," +
                         ( qi.local ? "local" : "remote") + "," +
                         ( qi.consumingMsgs ? "consuming" : "passive") + "]" ;
                v.add(info);
            }
        }
        ht.put("allConsumers", v);
        ht.put("activeConsumerCnt",String.valueOf(activeConsumerCnt));
        ht.put("failoverConsumerCnt",String.valueOf(failoverConsumerCnt));
        ht.put("localActiveConsumerCnt",String.valueOf(localActiveConsumerCnt));
        return ht;
    }

    private static ConfigListener cl = new ConfigListener()
            {
                public void validate(String name, String value)
                    throws PropertyUpdateException {
            
                    if (name.equals(MAX_ACTIVE_CNT)) {
                          int num = 0;
                          try {
                              num = Integer.parseInt(value);
                          } catch (Exception ex) {
                              throw new PropertyUpdateException(
                                 PropertyUpdateException.InvalidSetting,
                                "bad value "
                                 + value + " expected integer", ex );
                          }
                          if (MAX_LICENSED_ACTIVE != -1 && 
                              (num == -1
                             || num > MAX_LICENSED_ACTIVE)) 
                            
                          {
                              throw new PropertyUpdateException(
                                PropertyUpdateException.OutOfBounds,
                                Globals.getBrokerResources().getKString(
                                 BrokerResources.E_FEATURE_UNAVAILABLE,
                                 Globals.getBrokerResources().getKString(
                                    BrokerResources.M_LIC_PRIMARY_CONSUMERS,
                                    String.valueOf(MAX_LICENSED_ACTIVE))));
                           }

                    } else if (name.equals(MAX_FAILOVER_CNT)) {
                          int num = 0;
                          try {
                              num = Integer.parseInt(value);
                          } catch (Exception ex) {
                              throw new PropertyUpdateException(
                                 PropertyUpdateException.InvalidSetting,
                                "bad value "
                                 + value + " expected integer", ex );
                          }
                          if (MAX_LICENSED_BACKUP != -1 && 
                              (num == -1
                             || num > MAX_LICENSED_BACKUP)) 
                            
                          {
                              throw new PropertyUpdateException(
                                PropertyUpdateException.OutOfBounds,
                                Globals.getBrokerResources().getKString(
                                 BrokerResources.E_FEATURE_UNAVAILABLE,
                                 Globals.getBrokerResources().getKString(
                                    BrokerResources.M_LIC_FAILOVER_CONSUMERS,
                                    String.valueOf(MAX_LICENSED_BACKUP))));
                           }

                    }
                }
            
                public boolean update(String name, String value) {
                    BrokerConfig cfg = Globals.getConfig();
                    if (name.equals(MAX_ACTIVE_CNT)) {
                        defaultMaxActiveCount = 
                          cfg.getIntProperty(MAX_ACTIVE_CNT);
                    } else if (name.equals(MAX_FAILOVER_CNT)) {
                        defaultMaxFailoverCount = 
                          cfg.getIntProperty(MAX_FAILOVER_CNT);
                    }
                    return true;
                }

            };

    public static void init()
    {
        queueConsumer = new HashSet();
        queueConsumer.add(PacketReference.getQueueUID());
        Globals.getConfig().addListener(MAX_ACTIVE_CNT, cl);
        Globals.getConfig().addListener(MAX_FAILOVER_CNT, cl);
    }
    static void clear()
    {
        queueConsumer = null;
        Globals.getConfig().removeListener(MAX_ACTIVE_CNT, cl);
        Globals.getConfig().removeListener(MAX_FAILOVER_CNT, cl);
    }

    // used only as a space holder when deleting corrupted destinations
    protected Queue(DestinationUID uid) {
        super(uid);
    }

    protected Queue(String destination, int type, 
           boolean store, ConnectionUID id, boolean autocreate, DestinationList dl) 
        throws FeatureUnavailableException, BrokerException, IOException
    {
        super(destination,type, store, id, autocreate, dl);
        maxPrefetch = QUEUE_DEFAULT_PREFETCH;
        pending = new NFLPriorityFifoSet(11, false);
        delivered = new HashSet();
        localDeliveryPreferred=QUEUE_LDP;

        // compatibility w/ 3.5
        consumerPositions = new Vector();
        allConsumers = new LinkedHashMap();
        views = new WeakValueHashMap("Views");

	destMessages.addEventListener(this,  EventType.SET_CHANGED, this);
        setDefaultCounts(type);
    }

    public DestMetricsCounters getMetrics() {
        DestMetricsCounters dmc = super.getMetrics();
       
        synchronized (this) {
            // current # of active consumers 
            dmc.setActiveConsumers(activeConsumerCnt);
      
            // current # of failover consumers 
            // only applies to queues
            dmc.setFailoverConsumers(failoverConsumerCnt);
     
            // max # of active consumers
            dmc.setHWActiveConsumers(hwActiveCount);
        
            // max # of failover consumers
            dmc.setHWFailoverConsumers(hwFailoverCount);
        
            // avg active consumer
            dmc.setAvgActiveConsumers((int)activeAverage);
        
            // avg failover consumer
            dmc.setAvgFailoverConsumers((int)failoverAverage);
        }
        return dmc;
    }

    protected void getDestinationProps(Map m) {
        super.getDestinationProps(m);
        m.put(MAX_ACTIVE,Integer.valueOf(maxActiveCount));
        m.put(MAX_FAILOVER, Integer.valueOf(maxFailoverCount));
        m.put(LOCAL_DELIVERY, Boolean.valueOf(localDeliveryPreferred));
    }


    @Override
    public void setDestinationProperties(Map m) 
    throws BrokerException {

        if (m.get(MAX_ACTIVE) != null) {
           try {
               setMaxActiveConsumers(((Integer)m.get(MAX_ACTIVE)).intValue());
           } catch (BrokerException ex) {
               logger.logStack(Logger.WARNING,  "setMaxActiveConsumers()", ex);
           }

        }
        if (m.get(MAX_FAILOVER) != null) {
           try {
               setMaxFailoverConsumers(((Integer)m.get(MAX_FAILOVER)).intValue());
           } catch (BrokerException ex) {
               logger.logStack(Logger.WARNING, "setMaxFailoverConsumers", ex);
           }

        }
        if (m.get(LOCAL_DELIVERY) != null) {
            boolean local = ((Boolean)m.get(LOCAL_DELIVERY)).booleanValue();
            setClusterDeliveryPolicy(local ? 
                 ClusterDeliveryPolicy.LOCAL_PREFERRED :
                 ClusterDeliveryPolicy.DISTRIBUTED);
        }
        super.setDestinationProperties(m);
    }

    /**
     * handled 3.0 compatiblity
     */
    private void setDefaultCounts(int type) 
        throws BrokerException
    {
        int active = 0;
        int failover = 0;
        if (DestType.isSingle(type)) {
            active = 1;
            failover = 0;
        } else if (DestType.isFailover(type)) {
            active = 1;
            failover = UNLIMITED;
        } else if (DestType.isRRobin(type)) {
            active = UNLIMITED;
            failover = 0;
        } else {
            // 3.5 client
            active = defaultMaxActiveCount;
            failover = defaultMaxFailoverCount;
        }
        setMaxActiveConsumers(active);
        setMaxFailoverConsumers(failover);
        maxSize = (maxActiveCount < 0 || maxFailoverCount < 0) ? UNLIMITED
                         :maxActiveCount + maxFailoverCount;
        if (maxSize != UNLIMITED) {
            try {
                setMaxConsumers(maxSize);
            } catch (BrokerException ex) {
                logger.logStack(Logger.WARNING, "setMaxConsumers()", ex);
            }
        }
    }


    /**
     * handles transient data when class is deserialized
     */
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        pending = new NFLPriorityFifoSet(11,false);
        delivered = new HashSet();
        consumerPositions = new Vector();
        allConsumers = new LinkedHashMap();
        views = new WeakValueHashMap("views"); 
	destMessages.addEventListener(this,  EventType.SET_CHANGED, null);

        if (maxActiveCount == 0 && maxFailoverCount == 0) {
            try {
                setDefaultCounts(type);
            } catch (Exception ex) {
                logger.logStack(Logger.WARNING, "setDefaultCounts()", ex);
            }
        }
    }


    public Set routeAndMoveMessage(PacketReference oldRef, 
                  PacketReference newRef)
        throws IOException, BrokerException
    {
        // store w/ the new value
        try {
            newRef.moveMessage(pstore, oldRef, newRef, queueConsumer);
        } catch (BrokerException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BrokerException(
                        ex.toString(), ex);
        } 
        Destination odest = oldRef.getDestination();
        if (odest instanceof Queue) {
            ((Queue)odest).pending.remove(oldRef);
        }
        pending.add(10-newRef.getPriority(), newRef);
        return null; // not an explicit set
    }

    public Set routeNewMessage(PacketReference ref)
    throws BrokerException {
         // store w/ the new value
         try {
             ref.store(queueConsumer);
         } catch (BrokerException ex) {
             throw ex;
         } catch (RuntimeException ex) {
             throw new BrokerException(ex.toString(), ex);
         }
         pending.add(10-ref.getPriority(), ref);
         return null; // not an explicit set
    }

    public void routeNewMessageWithDeliveryDelay(PacketReference ref)
    throws BrokerException {
        try {
            ref.store(queueConsumer);
        } catch (BrokerException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BrokerException(ex.toString(), ex);
        }
    }
    
    public ConsumerUID[] calculateStoredInterests(PacketReference ref)
			throws BrokerException, SelectorFormatException {
		// this will return null if message is not persistent

		ConsumerUID[] storedInterests = null;
		try {
			storedInterests = ref.getRoutingForStore(queueConsumer);
		} catch (RuntimeException ex) {
			throw new BrokerException(ex.toString(), ex);
		}
		return storedInterests;
	}
    
    
    public void unrouteLoadedTransactionAckMessage(PacketReference ref, ConsumerUID consumer)
    throws BrokerException
    {
    	boolean removed =  pending.remove(ref);

    	Globals.getLogger().log(Logger.DEBUG,
    			" removing from pending "+ref + " result="+removed);  
    }

    /* called from transaction code */
    public void forwardOrphanMessage(PacketReference ref,
                  ConsumerUID consumer)
        throws BrokerException
    {
        if (ref.getOrder() == null) {
            pending.add(10-ref.getPriority(), ref);
        } else {
            ArrayList a =  new ArrayList();
            a.add(ref);
            forwardOrphanMessages(a, consumer);
            a.clear();
        }
    }

    /* called from transaction code */
    public void forwardOrphanMessages(Collection refs,
                  ConsumerUID consumer)
        throws BrokerException
    {
        pending.addAllOrdered(refs);
    }

    public void forwardMessage(Set consumers, PacketReference ref) {
        // does nothing
    }

    public void forwardDeliveryDelayedMessage(
                               Set<ConsumerUID> consumers,
                               PacketReference ref) 
                               throws BrokerException {
       pending.add(10-ref.getPriority(), ref);
    }

    protected ConsumerUID[] routeLoadedTransactionMessage(
           PacketReference ref)
        throws BrokerException,SelectorFormatException
    {
         ConsumerUID[] id= {PacketReference.getQueueUID()};
         return id;
     }



    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) 
    {

        // route the message

        
        if (type == EventType.SET_CHANGED)
        {
            assert target == destMessages;
            if (newval == null) {
                // removing message
                Map.Entry me = (Map.Entry)oldval;

                pending.remove(me.getValue());
                delivered.remove(me.getValue());
            }
        }
        super.eventOccured(type,r,target,oldval, newval, userdata);
    }

    public int getClusterDeliveryPolicy() {
        return (localDeliveryPreferred ?
               ClusterDeliveryPolicy.LOCAL_PREFERRED :
               ClusterDeliveryPolicy.DISTRIBUTED);
    }

    public static int getDefaultMaxActiveConsumers() {
        return defaultMaxActiveCount;
    }

    public static int getDefaultMaxFailoverConsumers() {
        return defaultMaxFailoverCount;
    }

    public int getMaxActiveConsumers() {
        return maxActiveCount;
    }
    public int getMaxFailoverConsumers() {
        return maxFailoverCount;
    }

    public int getActiveConsumerCount() {
        return activeConsumerCnt;
    }
    public int getFailoverConsumerCount() {
       return failoverConsumerCnt;
    }

    public Set getActiveConsumers() {

       Set set = new HashSet();
       synchronized (allConsumers) {
           Iterator itr = allConsumers.values().iterator();
           while (itr.hasNext()) {
               QueueInfo info = (QueueInfo)itr.next();
               if (info.active) {
                   set.add(info.consumer);
               }
           }
       }
       return set;
    }

    public Set getFailoverConsumers() {
       Set set = new HashSet();
       synchronized (allConsumers) {
           Iterator itr = allConsumers.values().iterator();
           while (itr.hasNext()) {
               QueueInfo info = (QueueInfo)itr.next();
               if (!info.active) {
                   set.add(info.consumer);
               }
           }
       }
       return set;
    }


    public void setMaxActiveConsumers(int count) 
        throws BrokerException
    {

        if (count == 0) {
            throw new BrokerException("Max Active Consumer count can not be 0");
        }

        if (MAX_LICENSED_ACTIVE != -1 && (count > MAX_LICENSED_ACTIVE ||
              count < 0) )
        {
            throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_FEATURE_UNAVAILABLE,
                            Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_PRIMARY_CONSUMERS,
                                String.valueOf(MAX_LICENSED_ACTIVE)),
                            getName()),
                        BrokerResources.X_FEATURE_UNAVAILABLE,
                        (Throwable) null,
                        Status.ERROR);
        }
      
	Integer oldVal = Integer.valueOf(maxActiveCount);

        maxActiveCount = (count < -1 ? -1:count); 
        maxSize = (maxActiveCount < 0 || maxFailoverCount < 0) ? -1 
                         :maxActiveCount + maxFailoverCount;
        setMaxConsumers(maxSize);
        consumerListChanged();

        notifyAttrUpdated(DestinationInfo.MAX_ACTIVE_CONSUMERS, 
			oldVal, Integer.valueOf(maxActiveCount));

    }
    public void setMaxFailoverConsumers(int count) 
        throws BrokerException
    {
        if (MAX_LICENSED_BACKUP != -1 && (count > MAX_LICENSED_BACKUP
                || count < 0))
        {
            throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_FEATURE_UNAVAILABLE,
                            Globals.getBrokerResources().getKString(
                                BrokerResources.M_LIC_FAILOVER_CONSUMERS,
                                String.valueOf(MAX_LICENSED_BACKUP)),
                            getName()),
                        BrokerResources.X_FEATURE_UNAVAILABLE,
                        (Throwable) null,
                        Status.ERROR);
        }
      
	Integer oldVal = Integer.valueOf(maxFailoverCount);

        maxFailoverCount = count;
        maxSize = (maxActiveCount < 0 || maxFailoverCount < 0) ? -1 
                         :maxActiveCount + maxFailoverCount;
        setMaxConsumers(maxSize);
        consumerListChanged();

        notifyAttrUpdated(DestinationInfo.MAX_FAILOVER_CONSUMERS, 
			oldVal, Integer.valueOf(maxFailoverCount));
    }

    public void setClusterDeliveryPolicy(int policy) 
   {

        boolean oldpolicy = localDeliveryPreferred;
        if (policy == ClusterDeliveryPolicy.LOCAL_PREFERRED) {
            localDeliveryPreferred = true;
        } else {
            assert policy == ClusterDeliveryPolicy.DISTRIBUTED;
            localDeliveryPreferred = false;
        }
        try {
            consumerListChanged();
            notifyAttrUpdated(DestinationInfo.DEST_CDP, 
			Boolean.valueOf(oldpolicy), Boolean.valueOf(localDeliveryPreferred));
        } catch (BrokerException ex) {
            logger.log(Logger.INFO,"XXX - internal error handling delivery policy change ", ex);
        }
    }

    private void setPosition(ConsumerUID cuid, int position) {
        synchronized(consumerPositions) {
            for (int i = consumerPositions.size(); i <= position; i ++) {
                consumerPositions.add(NULL_OBJECT);
            }
            consumerPositions.set(position, cuid);
        }
    }
    private int getPosition(ConsumerUID cuid, boolean local, boolean activeOnly) 
         throws BrokerException
    {
        int position = 0;
        int lastposition = 0;
        int positioncnt = 0;
        boolean gotPosition = false;
        while (!gotPosition) {
            synchronized(consumerPositions) {
                position = consumerPositions.indexOf(NULL_OBJECT,position);
                if (position == -1 ) {
                   if (maxSize == -1 || consumerPositions.size() < maxSize) {
                       consumerPositions.add(NULL_OBJECT);
                       position = consumerPositions.size() -1;
                   }
                } 
                if (position == -1) 
                    break; // no love here

                if (activeOnly && position >= maxActiveCount) {
                    position = -1;
                    break;
                }
            }

            if (local && !getIsLocal()) { // try to lock
                 if (Globals.getClusterBroadcast().
                         getConsumerLock(cuid, 
                         getDestinationUID(), position, maxSize, 
                         cuid.getConnectionUID())) 
                 {
                         // yeah got the lock
                         Object o = consumerPositions.set(position, cuid);
                         if (o != NULL_OBJECT && o != null)
                                   logger.log(Logger.DEBUG,"after lock, object unexpectly changed "+
                                     " position " + o + " at position " + position);

                         gotPosition = true;
                 } else { // lost the lock, try again

                    // OK .. if we cant get a lock its for 1 of 2 reasons
                    //    - someone else got it (but we havent been notified)
                    //    - something else went wrong

                    // try at the same position for a minimum of 5 times

                    if (position == lastposition) {
                        positioncnt ++;
                        if (positioncnt > 10) {
                            logger.log(Logger.DEBUG,
                                       "Could not get position "
                                     + position + " in queue " + this + 
                                     " for consumer " + cuid + 
                                     " trying the next available position");
                            position ++;
                        }
                    } else if (position != lastposition) {
                        positioncnt= 0; // resest
                    }
                    lastposition = position;
     
                    continue;
                 }
                         
            } else {  // no competition, got the lock
                synchronized(consumerPositions) {
                    Object o = consumerPositions.set(position, cuid);
                    if (o != NULL_OBJECT && o != null)
                        logger.log(Logger.DEBUG,"during set: object unexpectly changed "+
                                     " in getPosition for " + o + " at position " + position);

                }
                gotPosition = true;
             }
         }
         return position;
     }
    public Consumer addConsumer(Consumer consumer, boolean local, Connection conn)
        throws BrokerException, SelectorFormatException {
        return addConsumer(consumer, local, conn, true);
    }

    public Consumer addConsumer(Consumer consumer, boolean local, 
        Connection conn, boolean loadIfActive)
        throws BrokerException, SelectorFormatException {

        consumer.setStoredConsumerUID(PacketReference.getQueueUID());
        super.addConsumer(consumer, local, conn, loadIfActive);
        // make sure we dont have anything weird going on
        if (!local && getIsLocal()) { 
            // weird, non-local consumer w/ local destination
            throw new BrokerException("Internal Error " +this
                     + " trying to add remote consumer to local dest",
                     Status.CONFLICT );
        }

        // OK  - get the next position
        if (consumer.lockPosition == -1) {
            consumer.lockPosition = getPosition(consumer.getConsumerUID(), local, false);
        } else {
            setPosition(consumer.getConsumerUID(), 
                          consumer.lockPosition);
        }
 
        // if position is -1, something went wrong
        if (consumer.lockPosition == -1) {
            String args[] = {getName(), 
                    String.valueOf(maxActiveCount),
                    String.valueOf(maxFailoverCount) };

            throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_S_QUEUE_ATTACH_FAILED,
                    args),
                Status.CONFLICT );
                   
       }

       // OK ... now set up our info in the queue list
       QueueInfo qinfo = new QueueInfo();
       qinfo.position = consumer.lockPosition;
       qinfo.consumer = consumer;
       qinfo.local = local;
       qinfo.active = (maxActiveCount == -1 || consumer.lockPosition < maxActiveCount);

       synchronized (allConsumers) {
           allConsumers.put(consumer.getConsumerUID(), qinfo );
       }
       synchronized (this) {
           // update counters
           if (qinfo.local) {
               localActiveConsumerCnt ++;
           }

           if (qinfo.active) {
               updateActive(true); // update counts + 
           } else {
               updateFailover(true); // update counts + 
           }

           boolean hasLocal = localActiveConsumerCnt > 0;
           // determine if we are consuming messages
           if (hasLocal && localDeliveryPreferred  && !local)
           {
               qinfo.consumingMsgs = false;
           } else {
               qinfo.consumingMsgs = qinfo.active;
           }

           //OK .. we now have a position assigned .. set up the
           // consumer to start pulling messages
           makeActive(consumer);

           consumer.setIsActiveConsumer(qinfo.consumingMsgs);

           // NOW ... there could be one case where we need to process
           // the list -> if localDeliverPrefered, we are local and
           // there are more than 1 consumer and the local delivery cnt == 1
           // we need to inactive some consumers in this case

           if (localDeliveryPreferred && local && localActiveConsumerCnt == 1
               && activeConsumerCnt > localActiveConsumerCnt) {
                consumerListChanged();
           }
       }

       notifyConsumerAdded(consumer, conn);

       return consumer;
    }

    public void removeConsumer(ConsumerUID cid, boolean local) 
                throws BrokerException {
        removeConsumer(cid, null, false, local);
    }

    public void removeConsumer(ConsumerUID cid, Map remotePendings, 
                               boolean remoteCleanup, boolean local) 
                               throws BrokerException {

        super.removeConsumer(cid, remotePendings, remoteCleanup, local);
        QueueInfo c = null;
        synchronized(allConsumers) {
            c = (QueueInfo)allConsumers.remove(cid);
        }
        if (c == null) {
            notifyConsumerRemoved();
            return;
        }
        synchronized (this) {
            makeInactive(c.consumer);
        }

        if (c.local && !getIsLocal()) {
            Globals.getClusterBroadcast().unlockConsumer(cid, getDestinationUID(), c.position);
        }
        synchronized (this) {
            Object o = consumerPositions.set( c.position, NULL_OBJECT);
            if (! cid.equals(o))
                 logger.log(Logger.DEBUG,"object changed during remove of " +
                         o + " at position " + c.position);
            if (c.active) {
                if (c.local) {
                   localActiveConsumerCnt--;
                }
                updateActive(false);
            } else {
               updateFailover(false);
            }
        }
        consumerListChanged();

        notifyConsumerRemoved();
    }


    private void makeActive(Consumer consumer) {
            if (consumer.getSelector() == null) {
                if (pendingSubset == null) {
                    pendingSubset = pending.subSet((Filter)null);
                }
                
                consumer.setParentList(pstore, pendingSubset);
            } else {
                SubSet set = null;
                synchronized(views) {
                    set = (SubSet)
                       views.get(consumer.getSelectorStr());
                    if (set == null) {
                        SelectorFilter sf = new SelectorFilter(
                            consumer.getSelectorStr(),
                            consumer.getSelector());
                        set = pending.subSet(sf);

                        views.put(consumer.getSelectorStr(), set);
                    }
                }
                consumer.setParentList(pstore, set);
            }
    }

    private void makeInactive(Consumer s) {
    }


    private void consumerListChanged() 
        throws BrokerException
    {
        if (activeConsumerCnt == maxActiveCount
           && getConsumerCount() == 0) {
            // nothing to do
            return;
        }
        boolean lookRemote = !localDeliveryPreferred || 
               localActiveConsumerCnt == 0;
        QueueInfo target = null;
        synchronized(allConsumers) {
            Iterator itr = allConsumers.values().iterator();
            while (itr.hasNext()) {
                QueueInfo qi = (QueueInfo)itr.next();

                // check to see what the status of the consumer
                // should be
                // cases are:
                //    - we were a backup consumer and should become active
                //    - we were a backup consumer and should STAY backup
                //    - we are an active consumer and
                //          localDeliveryPreferred = false  (no change)
                //    - we are an active remote consumer AND lpd = true
                //       and:
                //          - there are local consumers 
                //          - there are not local consumers
                //  

                if (qi.active)   {   // active consumer local or remote
                    if (qi.position >= maxActiveCount && maxActiveCount != UNLIMITED) {
                       if (failoverConsumerCnt < maxFailoverCount) {
                            // we just became backup
                           qi.consumingMsgs = false;
                           qi.active = false;
                           qi.consumer.setIsActiveConsumer(false);
                           updateActive(false);
                           updateFailover(true);
                       }
                    } else if (qi.local) {
                       // nothing should change
                       assert qi.consumingMsgs == true;
                    } else  { // !qi.local  && < max
                       assert !qi.local;
                       if (lookRemote) {
                           if (!qi.consumingMsgs) {
                               qi.consumingMsgs = true;
                               qi.consumer.setIsActiveConsumer(true);
                           } else {
                               // already correct, nothing to do
                           }
                       } else {
                            if (qi.consumingMsgs) {
                               qi.consumingMsgs = false;
                               qi.consumer.setIsActiveConsumer(false);
                           } else {
                               qi.consumer.setIsActiveConsumer(false);
                               // already correct, nothing to do
                           }
                          
                       }
                    }
                } else { //!qi.active
                    if (qi.position < maxActiveCount) {
                        qi.active = true;
                        qi.consumingMsgs = qi.local || lookRemote;
                        qi.consumer.setIsActiveConsumer(qi.consumingMsgs);
                        updateActive(true);
                        updateFailover(false);
                        logger.log(Logger.INFO, BrokerResources.I_FAILOVER_ACTIVE,
                            String.valueOf(qi.consumer.getConsumerUID().longValue()), 
                            qi.consumer.getDestinationUID().getName()); 
                    } else if (activeConsumerCnt < maxActiveCount) {
                        // move up in the list
                        int pos = getPosition(qi.consumer.getConsumerUID(),
                                   qi.local, true);
                        if (pos != -1) {
                            // moved up the list
                            if (!getIsLocal())
                                Globals.getClusterBroadcast().unlockConsumer(
                                  qi.consumer.getConsumerUID(), 
                                  getDestinationUID(), qi.position);  
                            Object o = consumerPositions.set(qi.position, NULL_OBJECT);
                            if (! qi.consumer.getConsumerUID().equals(o))
                                 logger.log(Logger.DEBUG,"failover update: object unexpected changed "+
                                       " position " + o + " at position " + qi.position + " new pos " + pos);
                            qi.position = pos;
                            qi.consumingMsgs = qi.local || lookRemote;
                            qi.consumer.setIsActiveConsumer(qi.consumingMsgs);
                            qi.active = true;
                            updateActive(true);
                            updateFailover(false);
                            logger.log(Logger.INFO, BrokerResources.I_FAILOVER_ACTIVE,
                                String.valueOf(qi.consumer.getConsumerUID().longValue()), 
                                qi.consumer.getDestinationUID().getName()); 
                            if (qi.consumingMsgs)
                                makeActive(qi.consumer);
                         } else { 
                            // stay failover
                         }
                    } else {
                         // stay failover
                    }
                }    
            } // end iterator
        } // end sync 
     }




    private synchronized void updateActive(boolean increment) {
        if (increment) {
            activeConsumerCnt ++;
        } else {
            activeConsumerCnt --;
        }
        if (activeConsumerCnt > hwActiveCount) {
            hwActiveCount = activeConsumerCnt;
        }
        activeAverage = (((float)activeSampleCnt * activeAverage) 
                   + (float)activeConsumerCnt)/((float)activeSampleCnt + 1.0f);
        activeSampleCnt ++;
    }
    private synchronized void updateFailover(boolean increment) {
        if (increment) {
            failoverConsumerCnt ++;
        } else {
            failoverConsumerCnt --;
        }
        if (failoverConsumerCnt > hwFailoverCount) {
            hwFailoverCount = failoverConsumerCnt;
        }
        failoverAverage = (((float)failoverSampleCnt * failoverAverage) 
                  + (float)failoverConsumerCnt)/((float)failoverSampleCnt + 1.0f);
        failoverSampleCnt ++;
    }

     public int getSharedConsumerFlowLimit() {
        return getMaxPrefetch();
    }

    public void purgeDestination(boolean noerrnotfound) throws BrokerException {
        super.purgeDestination(noerrnotfound);
        pending.clear();
    }
 
 
    public void purgeDestination(Filter criteria) throws BrokerException {
        super.purgeDestination(criteria);
        Set s = pending.getAll(criteria);
        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            Object o = itr.next();
            pending.remove(o);
        }
    }
}

class QueueInfo
{
    int position = 0;
    Consumer consumer = null;
    boolean active = false;
    boolean local = false;
    boolean consumingMsgs = false;

    public String toString() {
        return consumer + ":" + position + ":" + active + ":" + local + ":" + consumingMsgs;
    }
}


 
