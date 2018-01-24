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
 * @(#)ClusterManagerImpl.java	1.36 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.manager;

import java.util.*;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.CacheHashMap;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

/**
 * This class represents the non-ha implementation of ClusterManager.
 * Configuration information is not retrieved from the store.<p>
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterManagerImpl")
@Singleton
public class ClusterManagerImpl implements ClusterManager, ConfigListener
{

    private static final String DEBUG_ALL_PROP = Globals.IMQ + ".cluster.debug.all";
    private static final boolean debug_CLUSTER_ALL = Globals.getConfig().getBooleanProperty(DEBUG_ALL_PROP);
    private static boolean DEBUG_CLUSTER_ALL = debug_CLUSTER_ALL;

    private static final String DEBUG_LOCK_PROP = Globals.IMQ + ".cluster.debug.lock";
    private static final boolean debug_CLUSTER_LOCK = Globals.getConfig().getBooleanProperty(DEBUG_LOCK_PROP);
    private static boolean DEBUG_CLUSTER_LOCK = debug_CLUSTER_LOCK;

    private static final String DEBUG_TXN_PROP = Globals.IMQ + ".cluster.debug.txn";
    private static final boolean debug_CLUSTER_TXN = Globals.getConfig().getBooleanProperty(DEBUG_TXN_PROP);
    private static boolean DEBUG_CLUSTER_TXN = debug_CLUSTER_TXN;

    private static final String DEBUG_TAKEOVER_PROP = Globals.IMQ + ".cluster.debug.takeover";
    private static final boolean debug_CLUSTER_TAKEOVER = Globals.getConfig().getBooleanProperty(DEBUG_TAKEOVER_PROP);
    private static boolean DEBUG_CLUSTER_TAKEOVER = debug_CLUSTER_TAKEOVER;

    private static final String DEBUG_MSG_PROP = Globals.IMQ + ".cluster.debug.msg";
    private static final boolean debug_CLUSTER_MSG = Globals.getConfig().getBooleanProperty(DEBUG_MSG_PROP);
    private static boolean DEBUG_CLUSTER_MSG = debug_CLUSTER_MSG;


    private static final String DEBUG_CONN_PROP = Globals.IMQ + ".cluster.debug.conn";
    private static final boolean debug_CLUSTER_CONN = Globals.getConfig().getBooleanProperty(DEBUG_CONN_PROP);
    private static boolean DEBUG_CLUSTER_CONN = debug_CLUSTER_CONN;

    private static final String DEBUG_PING_PROP = Globals.IMQ + ".cluster.debug.ping";
    private static final boolean debug_CLUSTER_PING = Globals.getConfig().getBooleanProperty(DEBUG_PING_PROP);
    private static boolean DEBUG_CLUSTER_PING = debug_CLUSTER_PING;

    private static final String DEBUG_PKT_PROP = Globals.IMQ + ".cluster.debug.packet";
    private static final boolean debug_CLUSTER_PACKET = Globals.getConfig().getBooleanProperty(DEBUG_PKT_PROP);
    private static boolean DEBUG_CLUSTER_PACKET = debug_CLUSTER_PACKET;

    /*******************************************************
     * accessor methods for dynamic cluster debug flags
     *******************************************************/
    public static boolean isDEBUG_CLUSTER_ALL() {
        return DEBUG_CLUSTER_ALL;
    }
    public static boolean isDEBUG_CLUSTER_LOCK() {
        return DEBUG_CLUSTER_LOCK;
    }
    public static boolean isDEBUG_CLUSTER_TXN() {
        return DEBUG_CLUSTER_TXN;
    }
    public static boolean isDEBUG_CLUSTER_TAKEOVER() {
        return DEBUG_CLUSTER_TAKEOVER;
    }
    public static boolean isDEBUG_CLUSTER_MSG() {
        return DEBUG_CLUSTER_MSG;
    }
    public static boolean isDEBUG_CLUSTER_CONN() {
        return DEBUG_CLUSTER_CONN;
    }
    public static boolean isDEBUG_CLUSTER_PING() {
        return DEBUG_CLUSTER_PING;
    }
    public static boolean isDEBUG_CLUSTER_PACKET() {
        return DEBUG_CLUSTER_PACKET;
    }


   /**
    * Turns on/off debugging.
    */
   private static boolean DEBUG = false;

   /**
    * The basic configuration (name, value pairs) for the
    * broker.
    */
   protected BrokerConfig config = Globals.getConfig();

   /**
    * The class used for logging. 
    */
   protected Logger logger = Globals.getLogger();

   /**
    * The set of listeners waiting state or configuration
    * changed information. 
    * @see ClusterListener
    */
   protected Set listeners = null;

   /**
    * The current transport type for the cluster. 
    */
   protected String transport = null;

   /**
    * The current hostname used for the cluster service.  
    * A value of null indicates bind to all hosts.
    */
   protected String clusterhost = null;

   /**
    * The current port used for the cluster service.
    * A value of 0 indicates that the value should be obtained 
    * dynamically.
    */
   protected int clusterport = 0;

   /**
    * This is a private initialization flag.
    */
   protected boolean initialized = false;

   /**
    * This is the brokerid for the local broker.
    */
   protected String localBroker = null;

   /**
    * The brokerid for the master broker,
    * null indicates no master broker.
    */
   protected String masterBroker = null;

   /**
    * The list of all brokers in the cluster.
    * The list contains ClusteredBroker objects.
    * @see ClusteredBroker
    */
   protected Map allBrokers = null;


    protected static final String MANUAL_AUTOCONNECT_PROPERTY =
        Globals.MANUAL_AUTOCONNECT_CLUSTER_PROPERTY;


    /**
     * Private property name used to set the number of entries
     * in the session list.
     */
    protected static final String MAX_OLD_SESSIONS_PROP =
        Globals.IMQ + ".cluster.maxTakeoverSessions";
    protected static final String MAX_RETAITION_TIME_PROP =
        Globals.IMQ + ".cluster.maxTakeoverSessionRetaintionTime";
    protected static final long MAX_RETAITION_TIME_DEFAULT = 1800; //secs

    protected int maxTakeoverSessions = Globals.getConfig().getIntProperty(
                       MAX_OLD_SESSIONS_PROP, 10);
    protected long maxTakeoverRetaintionTime = getMAX_RETAITION_TIME(); //millisecs
    /**
     */
    protected LinkedHashMap<UID, Long> supportedSessionMap = 
                                 new LinkedHashMap<UID, Long>();

    /**
     * The id of the cluster.
     */
    private String clusterid = Globals.getClusterID();


    private BrokerResources br = Globals.getBrokerResources();

    private MQAddress brokerNextToMe = null;
    private Object brokerNextToMeLock = new Object();
    private int clusterPingInterval = CLUSTER_PING_INTERVAL_DEFAULT;

    private static long getMAX_RETAITION_TIME() { 
        long t = Globals.getConfig().getLongProperty(MAX_RETAITION_TIME_PROP,
                                     MAX_RETAITION_TIME_DEFAULT);
        if (t < MAX_RETAITION_TIME_DEFAULT) {
            t = MAX_RETAITION_TIME_DEFAULT;
        }
        return t*1000L;
    }

    /**
     * Creates a ClusterManagerImpl. 
     */
    public ClusterManagerImpl()
    {
       listeners = new LinkedHashSet();  
    } 

    public int getClusterPingInterval() {
        return clusterPingInterval;
    }

   /**
    * Retrieves the cluster id associated with this cluster. 
    *
    * @return the id or null if this is not an HA cluster
    */
   public String getClusterId() {
       return clusterid;
   }



   /**
    * Internal method to return the map which contains
    * all brokers. This method may be overridden by
    * sub-classes.
    */
   protected Map initAllBrokers(MQAddress myaddr)
        throws BrokerException
   {

       return new HashMap();
   }


   /**
    * Changes the host/port of the local broker. 
    * 
    * @param address MQAddress to the portmapper
    * @throws BrokerException if something goes wrong
    *         when the address is changed
    */
   public void setMQAddress(MQAddress address)
       throws Exception 
   {
       if (!initialized) {
           initialize(address); // sets up cluster state
       } else {
           mqAddressChanged(address);
       }
   }

   /**
    * Retrieves the host/port of the local broker.
    * 
    * @return the MQAddress to the portmapper
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public MQAddress getMQAddress() {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return getLocalBroker().getBrokerURL();
   }

      
   /**
    * Sets a listener for notification when the state/status
    * or configuration of the cluster changes. 
    * 
    * <p>
    * This api is used by the Monitor Service to determine when
    * a broker should be monitored because it may be down.
    *
    * @see  ClusterListener
    * @param listener the listener to add
    */
   public void addEventListener(ClusterListener listener)
   {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

   /**
    * Removes a listener for notification when the state changes.
    * 
    * <p>
    * This api is used by the Monitor Service to determine when
    * a broker should be monitored because it may be down.
    *
    * @return true if the item existed and was removed.
    * @see  ClusterListener
    * @param listener the listener to remove
    */
   public boolean removeEventListener(ClusterListener listener)
   {
        synchronized (listeners) {
            return listeners.remove(listener);
        }
   }
   
   /**
    * Retrieves the ClusteredBroker which represents
    * this broker.
    *
    * @return the local broker
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    * @see ClusterManagerImpl#getBroker(String)
    */
   public ClusteredBroker getLocalBroker()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");
       return getBroker(localBroker);
   }
   
   /**
    * Returns the current number of brokers in the
    * cluster. In a non-ha cluster, this includes all
    * brokers which have a BrokerLink to the local broker and
    * the local broker.
    * @return count of all brokers in the cluster. 
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public int getKnownBrokerCount()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       synchronized (allBrokers) {
           return allBrokers.size();
       }
   }

   /**
    * Returns the current number of brokers in the
    * configuration propperties. In a non-ha cluster, this includes all
    * brokers listed by -cluster or the cluster property.
    * @return count of configured brokers in the cluster. 
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public int getConfigBrokerCount()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       int cnt = 0;
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {
               ClusteredBroker cb = (ClusteredBroker)itr.next();
               if (cb.isConfigBroker())
                   cnt ++;
           }
       }
       return cnt;
   }

   /**
    * Returns the current number of brokers in the
    * cluster. In a non-ha cluster, this includes all
    * brokers which have an active BrokerLink to the local broker and
    * the local broker.
    * @return count of all brokers in the cluster. 
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public int getActiveBrokerCount()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       int cnt = 0;
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {
               ClusteredBroker cb = (ClusteredBroker)itr.next();
               if (BrokerStatus.getBrokerLinkIsUp(cb.getStatus()))
                   cnt ++;
           }
       }
       return cnt;
   }
         
   /**
    * Returns an iterator of ClusteredBroker objects for
    * all brokers in the cluster. This is a copy of
    * the current list. 
    * 
    * @param refresh if true refresh current list then return it
    * @return iterator of ClusteredBrokers
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public Iterator getKnownBrokers(boolean refresh)
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       HashSet brokers = null;
       synchronized (allBrokers) {
           brokers = new HashSet(allBrokers.values());
       }
       return brokers.iterator();
   }

         

   static class ConfigInterator implements Iterator
   {
       Object nextObj = null;
       Iterator parent = null;

       public ConfigInterator(Iterator parentItr)
       {
           parent = parentItr;
       }

       public boolean hasNext() {
           if (nextObj != null) {
               return true;
           }
           if (!parent.hasNext()) {
                return false;
           }
           while (nextObj == null) {
               if (!parent.hasNext()) break;
               nextObj = parent.next();
               ClusteredBroker cb = (ClusteredBroker)nextObj;
               if (!cb.isConfigBroker()) {
                   parent.remove();
                   nextObj = null; // not valid
               }
           }
           return nextObj != null;
       }
       public Object next() {
           // ok, skip to the right location
           if (!hasNext())
               throw new NoSuchElementException("no more");
           Object ret = nextObj;
           nextObj = null;
           return ret;           
       }

       public void remove() {
            parent.remove();
       }
    }

   /**
    * Returns an iterator of ClusteredBroker objects for
    * all brokers in the cluster. This is a copy of
    * the current list and is accurate at the time getBrokers was
    * called.
    * 
    * @return iterator of ClusteredBrokers
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public Iterator getConfigBrokers()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       HashSet brokers = null;
       synchronized (allBrokers) {
           brokers = new HashSet(allBrokers.values());
           return new ConfigInterator(brokers.iterator());
       }

   }

   static class ActiveInterator implements Iterator

   {
       Object nextObj = null;
       Iterator parent = null;

       public ActiveInterator(Iterator parentItr)
       {
           parent = parentItr;
       }

       public boolean hasNext() {
           if (nextObj != null) {
               return true;
           }
           if (!parent.hasNext()) {
                return false;
           }
           while (nextObj == null) {
               if (!parent.hasNext()) break;
               nextObj = parent.next();
               ClusteredBroker cb = (ClusteredBroker)nextObj;
               if (BrokerStatus.getBrokerLinkIsDown(cb.getStatus())) {
                   parent.remove();
                   nextObj = null; // not valid
               }
           }
           return nextObj != null;
       }
       public Object next() {
           // ok, skip to the right location
           if (!hasNext())
               throw new NoSuchElementException("no more");
           Object ret = nextObj;
           nextObj = null;
           return ret;           
       }

       public void remove() {
            parent.remove();
       }
    }


   /**
    * Returns an iterator of ClusteredBroker objects for
    * all active brokers in the cluster. This is a copy of
    * the current list and is accurate at the time getBrokers was
    * called.
    * 
    * @return iterator of ClusteredBrokers
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public Iterator getActiveBrokers()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       HashSet brokers = null;
       synchronized (allBrokers) {
           brokers = new HashSet(allBrokers.values());
           return new ActiveInterator(brokers.iterator());
       }
   }
         
   /**
    * Returns a specific ClusteredBroker object by name.
    * 
    * @param brokerid the id associated with the broker
    * @return the broker associated with brokerid or null
    *         if the broker is not found
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public ClusteredBroker getBroker(String brokerid)
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       synchronized (allBrokers) {
           return (ClusteredBroker)allBrokers.get(brokerid);
       }
   }
         
         
   /**
    * Method used in a dynamic cluster, it updates the
    * system when a new broker is added.
    *
    * @param URL the MQAddress of the new broker
    * @param uid the brokerSessionUID associated with this broker (if known)
    * @param instName the instance name of the broker to be activated
    * @param userData optional data associated with the status change
    * @throws NoSuchElementException if the broker can not
    *              be added to the cluster (for example if
    *              the cluster is running in HA mode and
    *              the URL is not in the shared database)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    * @return the uid associated with the new broker
    */
   public String activateBroker(MQAddress URL, UID uid, 
                                String instName, Object userData)
                                throws NoSuchElementException,
                                       BrokerException
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       // does it exist yet ?
       String brokerid = lookupBrokerID(URL);
       if (brokerid == null) {
           brokerid = addBroker(URL, false, false, uid);
       }
       
       return activateBroker(brokerid, uid, instName, userData );
    }


   /**
    * method used in a all clusters, it updates the
    * system when a new broker is added.
    *
    * @param brokerid the id of the broker (if known)
    * @param uid the broker sessionUID
    * @param instName the broker instance name 
    * @param userData optional data associated with the status change
    * @throws NoSuchElementException if the broker can not
    *              be added to the cluster (for example if
    *              the cluster is running in HA mode and
    *              the brokerid is not in the shared database)
    * @throws BrokerException if the database can not be accessed
    * @return the uid associated with the new broker
    */
   public String activateBroker(String brokerid, UID uid, 
                                String instName, Object userData)
                                throws NoSuchElementException, 
                                BrokerException
   {
       ClusteredBroker cb = getBroker(brokerid);
       if (cb == null) {
           throw new BrokerException("Unknown broker " + brokerid);
       }
       cb.setInstanceName(instName);
       if (uid != null)
           cb.setBrokerSessionUID(uid);
       cb = updateBrokerOnActivation(cb, userData);
       cb.setStatus(BrokerStatus.ACTIVATE_BROKER, userData);
       return brokerid;

   }

    /**
     * protected method used to update a newly activated
     * broker if necessary
     */
    protected ClusteredBroker updateBrokerOnActivation(ClusteredBroker broker,
                                                       Object userData) {
        return broker;
    }

    protected ClusteredBroker updateBrokerOnDeactivation(ClusteredBroker broker,
                                                         Object userData) {
        broker.setInstanceName(null);
        return broker;
    }
   
   protected String addBroker(MQAddress URL, boolean isLocal, boolean config, 
                    UID sid)
             throws NoSuchElementException, BrokerException
   {
         ClusteredBroker cb = newClusteredBroker(URL, isLocal, sid);
         ((ClusteredBrokerImpl)cb).setConfigBroker(config);
         synchronized (allBrokers) {
             allBrokers.put(cb.getBrokerName(), cb);
         }
         brokerChanged(ClusterReason.ADDED, 
                cb.getBrokerName(), null, cb, sid, null);

         return cb.getBrokerName();

   }

   /**
    * only be called internally from cluster manager framework
    */
   public ClusteredBroker newClusteredBroker(MQAddress URL, 
                                   boolean isLocal, UID sid)
                                   throws BrokerException { 
       return new ClusteredBrokerImpl(this, URL, isLocal, sid);
   }

   protected void removeFromAllBrokers(String brokerName) {
       synchronized(allBrokers) {
           allBrokers.remove(brokerName);
       }
   }

   /**
    * method used in a dynamic cluster, it updates the
    * system when a broker is removed.
    *
    * @param URL the MQAddress associated with the broker
    * @param userData optional data associated with the status change
    * @throws NoSuchElementException if the broker can not
    *              be found in the cluster.
    */
   public void deactivateBroker(MQAddress URL, Object userData)
       throws NoSuchElementException
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       // does it exist yet ?
       String brokerid = lookupBrokerID(URL);
       if (brokerid == null) 
           throw new NoSuchElementException("Unknown URL " + URL);
       deactivateBroker(brokerid, userData);
    }       

   /**
    * Method used in a dynamic cluster, it updates the
    * system when a broker is removed.
    *
    * @param brokerid the id associated with the broker
    * @param userData optional data associated with the status change
    * @throws NoSuchElementException if the broker can not
    *              be found in the cluster.
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public void deactivateBroker(String brokerid, Object userData)
       throws NoSuchElementException
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       ClusteredBroker cb = null;

       boolean removed = false;

       synchronized (allBrokers) {
           cb = (ClusteredBroker)allBrokers.get(brokerid);
           if (cb == null) throw new NoSuchElementException("Unknown Broker" + brokerid);
           if (!cb.isConfigBroker()) { // remove it if its dynamic
               allBrokers.remove(brokerid);
               removed = true;
           }
           updateBrokerOnDeactivation(cb, null);
       }
       // OK, set the broker link down
       cb.setStatus(BrokerStatus.setBrokerLinkIsDown(
                          cb.getStatus()), userData);
       if (removed)
           brokerChanged(ClusterReason.REMOVED, cb.getBrokerName(),
                        cb, null, cb.getBrokerSessionUID(),  null);

   }     


   /**
    * Finds the brokerid associated with the given host/port.
    *
    * @param broker the MQAddress of the new broker
    * @return the id associated with the broker or null if the broker does not exist
    * @throws RuntimeException if the cluster has not be initialized
    *              (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */  
   public String lookupBrokerID(MQAddress broker)
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       // the safe thing to do is to iterate
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {
            
               ClusteredBroker cb = (ClusteredBroker)itr.next();
               MQAddress addr = cb.getBrokerURL();
               if (addr.equals(broker))
                   return cb.getBrokerName();
           }
       }
       return null;
   }

   /**
    * finds the brokerid associated with the given session.
    *
    * @param uid is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */
   public String lookupStoreSessionOwner(UID uid) {
       return null;
   }

   public String getStoreSessionCreator(UID uid) {
       return null;
   }

   /**
    * finds the brokerid associated with the given session.
    *
    * @param uid is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */
   public String lookupBrokerSessionUID(UID uid) {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       // the safe thing to do is to iterate
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {
            
               ClusteredBroker cb = (ClusteredBroker)itr.next();
               UID buid = cb.getBrokerSessionUID();
               if (buid.equals(uid))
                   return cb.getBrokerName();
           }
       }
       return null;
   }

   /**
    * @return true if allow configured master broker
    */

   protected boolean allowMasterBroker() {
       return true;
   }

   /**
    * The master broker in the cluster (if any).
    *
    * @return the master broker (or null if none)
    * @see ClusterManagerImpl#getBroker(String)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public ClusteredBroker getMasterBroker()
   {
       if (masterBroker == null) return null;

       return getBroker(masterBroker);
   }


   /**
    * The transport (as a string) used by
    * the cluster of brokers.
    *
    * @return the transport (tcp, ssl)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */

   public String getTransport()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return transport;
   }


   /**
    * Returns the port configured for the cluster service.
    * @return the port (or 0 if a dynamic port should be used)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public int getClusterPort()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return clusterport;
   }

   /**
    * Returns the host that the cluster service should bind to .
    * @return the hostname (or null if the service should bind to all)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public String getClusterHost()
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return clusterhost;
   }

   /**
    * Is the cluster "highly available" ?
    *
    * @return true if the cluster is HA
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    * @see Globals#getHAEnabled()
    */
   public boolean isHA() {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return false;
   }


   /**
    * Reload cluster properties from config 
    *
    */
   public void reloadConfig() throws BrokerException {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       String[] props = { CLUSTERURL_PROPERTY, 
                          AUTOCONNECT_PROPERTY }; //XXX
       config.reloadProps(Globals.getConfigName(), props, false); 
   }

   protected void setupListeners() {

       config.addListener(TRANSPORT_PROPERTY, this);
       config.addListener(HOST_PROPERTY, this);
       config.addListener(PORT_PROPERTY, this);
       config.addListener(AUTOCONNECT_PROPERTY, this);
       config.addListener(CONFIG_SERVER, this);
       config.addListener(CLUSTER_PING_INTERVAL_PROP, this);
   }
   /**
    * Initializes the cluster (loading all configuration). This
    * methods is called the first time setMQAddress is called
    * after the broker is created.
    *
    * @param address the address of the local broker
    * @throws BrokerException if the cluster can not be initialized
    * @see ClusterManagerImpl#setMQAddress
    */
   public String initialize(MQAddress address)  
        throws BrokerException
   {
        initialized = true;

        allBrokers = initAllBrokers(address);

        setupListeners();

        config.addListener(DEBUG_ALL_PROP, this);
        config.addListener(DEBUG_LOCK_PROP, this);
        config.addListener(DEBUG_TXN_PROP, this);
        config.addListener(DEBUG_TAKEOVER_PROP, this);
        config.addListener(DEBUG_MSG_PROP, this);
        config.addListener(DEBUG_CONN_PROP, this);
        config.addListener(DEBUG_PING_PROP, this);
        config.addListener(DEBUG_PKT_PROP, this);

        // handle parsing transport
        transport = config.getProperty(TRANSPORT_PROPERTY);
        if (transport == null) {
            transport = "tcp";
        }
        clusterhost = config.getProperty(HOST_PROPERTY);
        //if not set, try imq.hostname
        if (clusterhost == null ) {
            clusterhost = Globals.getHostname();
            if (clusterhost != null && clusterhost.equals(Globals.HOSTNAME_ALL)) {
                clusterhost = null; 
            }
        }
        clusterport = config.getIntProperty(PORT_PROPERTY, 0);
        clusterPingInterval = config.getIntProperty(CLUSTER_PING_INTERVAL_PROP,
                                     CLUSTER_PING_INTERVAL_DEFAULT);
        if (clusterPingInterval <= 0) {
            clusterPingInterval = CLUSTER_PING_INTERVAL_DEFAULT;
        }

        LinkedHashSet s = null;
        try {
            s = parseBrokerList();
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR, Globals.getBrokerResources().getKString(
                BrokerResources.X_BAD_ADDRESS_BROKER_LIST, ex.toString()), ex);
            throw new BrokerException(ex.getMessage(), ex);
        }
        
        localBroker =  addBroker(address, true, s.remove(address), new UID() );
        getLocalBroker().setStatus(BrokerStatus.ACTIVATE_BROKER, null);
        setBrokerNextToMe(s);

        // handle broker list

        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            MQAddress addr = (MQAddress)itr.next();
            try {
                // ok, are we the local broker ?
                ClusteredBroker lcb = getLocalBroker();

                if (addr.equals(getMQAddress())) {
                    if (lcb instanceof ClusteredBrokerImpl)
                         ((ClusteredBrokerImpl)lcb)
                           .setConfigBroker(true);
                } else {
                    addBroker(addr, false, true, null);
                }
            } catch (NoSuchElementException ex) {
                logger.logStack(Logger.ERROR, ex.getMessage()+": "+
                    "bad address in the broker list ", ex);
            }
        }

        // handle master broker
        String mbroker = config.getProperty(CONFIG_SERVER);
        if (!allowMasterBroker()) {
            if (DEBUG || logger.getLevel() <= Logger.DEBUG) {
            logger.log(Logger.INFO, "This broker does not allow "+CONFIG_SERVER+
                       " to be configured."+ (mbroker == null? "":" Ignore "+ 
                       CONFIG_SERVER+"="+mbroker)); 
            }
            mbroker = null;
        } else if (Globals.useSharedConfigRecord()) {
            if (mbroker == null) {
                logger.log(logger.INFO, br.getKString(br.I_USE_SHARECC_STORE));
            } else {
                logger.log(logger.WARNING, br.getKString(
                br.I_USE_SHARECC_STORE_IGNORE_MB, CONFIG_SERVER+"="+mbroker));
            }
            mbroker = null;
        }

        if (mbroker != null) {
            // ok, see if we exist
            MQAddress addr = null;
            try {
                addr = BrokerMQAddress.createAddress(mbroker);
            } catch (Exception ex) {
                logger.logStack(Logger.ERROR,
                     BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "bad address while parsing "
                        + "the broker list ", ex);
            }

            masterBroker = lookupBrokerID(addr);
            if (masterBroker == null) { // wasnt in list, add it
                logger.log(Logger.WARNING,
                      BrokerResources.W_MB_UNSET,
                      addr.toString());
                masterBroker = addBroker(addr, false, true, null);
            }
            masterBroker = lookupBrokerID(addr);
        }

        if (DEBUG) {
            logger.log(Logger.DEBUG,"Cluster is:" + toString());
        }

        return localBroker;

   }

   /**
    * Method which determines the list of brokers in the cluster.
    * For non-ha clusters, this is determined by the configuration
    * properties for this broker.
    * @return a Set containing all MQAddress objects associated with
    *         the broker cluster (except the local broker)
    * @throws MalformedURLException if something is wrong with the
    *         properties and an MQAddress can not be created.
    */
   protected LinkedHashSet parseBrokerList()
       throws MalformedURLException, UnknownHostException
   {
       // OK, handles the broker list removing

        /*
         * "imq.cluster.brokerlist" is usually kept in the
         * cluster configuration file. Administrators can use this
         * list to setup a 'permanent' set of brokers that will
         * join this cluster.
         */
        String propfileSetting = config.getProperty(AUTOCONNECT_PROPERTY);
        String cmdlineSetting = config.getProperty(MANUAL_AUTOCONNECT_PROPERTY);

        String values = null;
        if (propfileSetting == null && cmdlineSetting == null) {
            return new LinkedHashSet();
        }
        if (propfileSetting == null) {
            values = cmdlineSetting;
        } else if (cmdlineSetting == null) {
            values = propfileSetting;
        } else {
            values = cmdlineSetting + "," + propfileSetting;
        }
        return parseBrokerList(values);
    }

    public LinkedHashSet parseBrokerList(String values) 
        throws MalformedURLException, UnknownHostException {
        // we want to pull out dups .. so we use a hashmap
        // with host:port as a key

        HashMap tmpMap = new LinkedHashMap();

        // OK, parse properties
        StringTokenizer st = new StringTokenizer(values, ",");
        // Parse the given broker address list.
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            MQAddress address = BrokerMQAddress.createAddress(s);
            tmpMap.put(address.toString(),
                    address);
        }
        // OK, we can now return the list of MQAddresses

        return new LinkedHashSet(tmpMap.values());
   }

   private void setBrokerNextToMe(LinkedHashSet list) {
       synchronized(brokerNextToMeLock) {
           Iterator itr = list.iterator();
           MQAddress addr = null, next = null;
           boolean foundlocal = false; 
           int i = 0;
           while (itr.hasNext()) {
               addr = (MQAddress)itr.next();
               if (i == 0) {
                   if (!addr.equals(getMQAddress())) {
                       next = addr;
                   }
               }
               if (foundlocal) {
                   next = addr;
                   break;
               }
               if (addr.equals(getMQAddress())) {
                   foundlocal = true;
               } 
           }
           brokerNextToMe = next;
       }
   }

   public MQAddress getBrokerNextToMe() {
       synchronized(brokerNextToMeLock) {
           return brokerNextToMe;
       }
   }

   /**
    * Returns a user-readable string representing this class.
    * @return the user-readable represeation.
    */
   public String toString() {
       StringBuffer str = new StringBuffer();
       str.append("ClusterManager: [local=" + localBroker
                   + ", master = " + masterBroker + "]\n");
       synchronized(allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) str.append("\t"+itr.next() + "\n");
       }
       return str.toString();
   }

    /**
     * Gets the UID associated with the local broker
     *
     * @return null (this cluster type does not support session)
     */
    public synchronized UID getStoreSessionUID()
    {
        return null;
    }

    /**
     * Gets the UID associated with the local broker
     *
     * @return null (this cluster type does not support session)
     */
    public synchronized UID getBrokerSessionUID()
    {
        return getLocalBroker().getBrokerSessionUID();
    }


   /**
    * Adds an old UID to the list of supported sessions
    * for this broker.
    *
    * @param uid the broker's store session UID that has been taken over
    */
   protected void addSupportedStoreSessionUID(UID uid) {
       synchronized(supportedSessionMap) {
           supportedSessionMap.put(uid, 
               Long.valueOf(System.currentTimeMillis()));
       }
   }

   /**
    * Returns a list of supported session UID's for this
    * broker (not including its own sessionUID).<p>
    * This list may not include all sessionUID's that have
    * been supported by this running broker (ids may age
    * out over time).
    * 
    *
    * @return the set of sessionUIDs
    */
   public Set getSupportedStoreSessionUIDs() {
       Set s =  null;
       synchronized(supportedSessionMap) {
           s = new HashSet(supportedSessionMap.keySet());
       }
       if (getStoreSessionUID() != null) {
           s.add(getStoreSessionUID());
       }
       return s;
   }



    /**
     * Handles reparsing the broker list if it changes.
     *
     * @throws BrokerException if something goes wrong during
     *  parsing
     */
    protected void brokerListChanged()
        throws BrokerException
    {
        // OK .. get the new broker list 
        LinkedHashSet s = null;
        try {
            s = parseBrokerList();
            setBrokerNextToMe(s);
            if (DEBUG) {
            logger.log(Logger.INFO, "ClusterManagerImpl.parseBrokerList:"+s);
            }
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR,
                  BrokerResources.E_INTERNAL_BROKER_ERROR,
                  "bad address in brokerListChanged ",
                       ex);
            s = new LinkedHashSet();
        }

        Iterator itr = s.iterator();
        while (itr.hasNext()) {
            MQAddress addr = (MQAddress)itr.next();
            if (lookupBrokerID(addr) == null) {
                addBroker(addr,false, true, null);
            }
        }

        // OK, we need to clean up the allBroker's list

        List oldBrokers = new ArrayList();
        synchronized (allBrokers) {
            itr = allBrokers.values().iterator();
            while (itr.hasNext()) {
                ClusteredBroker cb = (ClusteredBroker)itr.next();
                ((ClusteredBrokerImpl)cb)
                           .setConfigBroker(true);
                MQAddress addr = cb.getBrokerURL();
                if (s.contains(addr)) {
                    s.remove(addr);
                    continue; 
                } else if (!cb.isLocalBroker()) {
                    oldBrokers.add(cb);
                    itr.remove();
                }
           }
        }
        // send out remove notifications
        itr = oldBrokers.iterator();
        while (itr.hasNext()) {
            ClusteredBroker cb = (ClusteredBroker)itr.next();
            brokerChanged(ClusterReason.REMOVED, cb.getBrokerName(),
                        cb, null, cb.getBrokerSessionUID(),  null);
            itr.remove();
        }
        
        // now add any remaining brokers
        itr = s.iterator();
        while (itr.hasNext()) {
               addBroker((MQAddress)itr.next(), false, true, null);
        }
    }
   
    /**
     * Handles changing the name of the master broker.
     *
     * @param mbroker the brokerid associated with the
     *                master broker
     * @throws BrokerException if something goes wrong
     */
    protected void masterBrokerChanged(String mbroker)
        throws BrokerException
    {
        // handle master broker
        
        ClusteredBroker oldMaster = getMasterBroker();
        masterBroker = null;
        if (mbroker != null) {
            // ok, see if we exist
            MQAddress addr = null;
            try {
                addr = BrokerMQAddress.createAddress(mbroker);

            } catch (Exception ex) {
                logger.log(Logger.ERROR,
                    BrokerResources.W_BAD_MB,
                    mbroker, ex);
            }

            masterBroker = lookupBrokerID(addr);
            if (masterBroker == null) { // wasnt in list, add it
                masterBroker = addBroker(addr, false, true, null);
            }
        }
        ClusteredBroker newMaster = getMasterBroker();
        brokerChanged(ClusterReason.MASTER_BROKER_CHANGED,
                       null, oldMaster, newMaster, null,  null);

    }

    /**
     * Method called when the MQAddress is changed on the system.
     * @param address the new address of the local brokers portmapper
     */
    protected void mqAddressChanged(MQAddress address) throws Exception
    {
        ClusteredBroker cb = getLocalBroker();
        MQAddress oldAddress = cb.getBrokerURL();
        cb.setBrokerURL(address);
        brokerChanged(ClusterReason.ADDRESS_CHANGED, 
                cb.getBrokerName(), oldAddress, address,null,  null);
    }

    /**
     * Validates an updated property.
     * @see ConfigListener
     * @param name the name of the property to be changed
     * @param value the new value of the property
     * @throws PropertyUpdateException if the value is
     *          invalid (e.g. format is wrong, property
     *          can not be changed)
     */
    public void validate(String name, String value)
        throws PropertyUpdateException
    {
        if (name.equals(TRANSPORT_PROPERTY)) {
            // XXX - is there a valid value
            throw new PropertyUpdateException(
                br.getString(br.X_BAD_PROPERTY, name));
        } else if (name.equals(HOST_PROPERTY)) {
            // nothing to validate
            throw new PropertyUpdateException(
                br.getString(br.X_BAD_PROPERTY, name));
        } else if (name.equals(PORT_PROPERTY)) {
            // validate its an int
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new PropertyUpdateException(
                     PropertyUpdateException.InvalidSetting,
                     PORT_PROPERTY + " should be set to an int"
                     + " not " + value );
            }
        } else if (name.equals(CLUSTER_PING_INTERVAL_PROP)) {
            try {
                int v = Integer.parseInt(value);
                if (v <= 0) {
                    throw new NumberFormatException(""+value);
                }
            } catch (NumberFormatException ex) {
                throw new PropertyUpdateException(
                     PropertyUpdateException.InvalidSetting,
                     CLUSTER_PING_INTERVAL_PROP + " should be set to a positive int"
                     + " not " + value );
            }
        } else if (name.equals(AUTOCONNECT_PROPERTY)) {
            // XXX - is there a valid value
        } else if (name.equals(CONFIG_SERVER)) {
            try {
                BrokerMQAddress.createAddress(value);
            } catch (Exception e) {
                throw new PropertyUpdateException(
                br.getString(br.X_BAD_PROPERTY, value)+": "+e.getMessage());
            }
        }
    }

    /**
     * Updates a new configuration property.
     * @see ConfigListener
     * @param name the name of the property to be changed
     * @param value the new value of the property
     * @return true if the property took affect immediately,
     *         false if the broker needs to be restarted.
     */
    public boolean update(String name, String value)
    {
        if (name.equals(TRANSPORT_PROPERTY)) {
            transport = value;
            if (transport == null || transport.length() == 0) {
                transport = "tcp";
            }
            clusterPropertyChanged(name, value);
        } else if (name.equals(HOST_PROPERTY)) {
            clusterPropertyChanged(name, value);
        } else if (name.equals(PORT_PROPERTY)) {
            clusterPropertyChanged(name, value);
        } else if (name.equals(CLUSTER_PING_INTERVAL_PROP)) {
            clusterPropertyChanged(name, value);
        } else if (name.equals(AUTOCONNECT_PROPERTY)) {
            if (DEBUG) {
                logger.log(logger.INFO, "ClusterManagerImpl.update("+name+"="+value+")");
            }
            try {
                brokerListChanged();
            } catch (Exception ex) {
                 logger.log(Logger.INFO,"INTERNAL ERROR", ex);
            }
        } else if (name.equals(CONFIG_SERVER)) {
            try {
                masterBrokerChanged(value);
            } catch (Exception ex) {
                 logger.log(Logger.INFO,"INTERNAL ERROR", ex);
            }
        } else if (name.equals(DEBUG_ALL_PROP)) {
            DEBUG_CLUSTER_ALL = Boolean.valueOf(value);
            DEBUG_CLUSTER_LOCK = Boolean.valueOf(value);
            DEBUG_CLUSTER_TXN = Boolean.valueOf(value);
            DEBUG_CLUSTER_TAKEOVER = Boolean.valueOf(value);
            DEBUG_CLUSTER_MSG = Boolean.valueOf(value);
            DEBUG_CLUSTER_CONN = Boolean.valueOf(value);
            DEBUG_CLUSTER_PING = Boolean.valueOf(value);
            DEBUG_CLUSTER_PACKET = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_LOCK_PROP)) {
            DEBUG_CLUSTER_LOCK = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_TXN_PROP)) {
            DEBUG_CLUSTER_TXN = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_TAKEOVER_PROP)) {
            DEBUG_CLUSTER_TAKEOVER = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_MSG_PROP)) {
            DEBUG_CLUSTER_MSG = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_CONN_PROP)) {
            DEBUG_CLUSTER_CONN = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_PING_PROP)) {
            DEBUG_CLUSTER_PING = Boolean.valueOf(value);
        } else if (name.equals(DEBUG_PKT_PROP)) {
            DEBUG_CLUSTER_PACKET = Boolean.valueOf(value);
        }

        return true;
    }



   /**
    * Called to notify ClusterListeners when the cluster service
    * configuration. Configuration changes include:
    * <UL><LI>cluster service port</LI>
    *     <LI>cluster service hostname</LI>
    *     <LI>cluster service transport</LI>
    * </UL>
    * @param name the name of the changed property
    * @param value the new value of the changed property
    * @see ClusterListener
    */
   public void clusterPropertyChanged(String name, String value)
   {
       synchronized (listeners) {
           if ( listeners.size() == 0 )
               return;
           Iterator itr = listeners.iterator(); 
           while (itr.hasNext()) {
               ClusterListener listen = (ClusterListener)
                        itr.next();
               listen.clusterPropertyChanged(name, value);
           }
       }
   }

   /**
    * only to be called internally from cluster manager framework
    *
    * Notify ClusterListeners when state of the cluster is changed.
    * Reasons for changes include:
    *  <UL><LI>A broker has been added to the cluster</LI>
    *      <LI>A broker has been removed from the cluster</LI>
    *      <LI>the master broker has changed</LI>
    *      <LI>the portmapper address has changed</LI>
    *      <LI>the protocol version of a broker has changed
    *          (this should only happen when a broker reconnects to
    *          the cluster)</LI>
    *      <LI>the dynamic status of the broker has changed</LI>
    *      <LI>the state of the broker has changed</LI>
    * </UL>
    * <P>
    * The data passed to the listener is determined by the
    * reason this method is being called:
    * <TABLE border=1>
    *   <TR><TH>Reason</TH><TH>brokerid</TH><TH>oldvalue</TH><TH>newvalue</TH></TR>
    *   <TR><TD>ADDED</TD><TD>added broker</TD><TD>null</TD><TD>ClusteredBroker added</TD></TR>
    *   <TR><TD>REMOVED</TD><TD>removed broker</TD><TD>ClusteredBroker removed</TD><TD>null</TD></TR>
    *   <TR><TD>STATUS_CHANGED</TD><TD>changed broker</TD><TD>Integer (old status)</TD>
    *                               <TD>Integer (new status)</TD></TR>
    *   <TR><TD>STATE_CHANGED</TD><TD>changed broker</TD><TD>BrokerState (old state)</TD>
    *                               <TD>BrokerState (new state)</TD></TR>
    *   <TR><TD>VERSION_CHANGED</TD><TD>changed broker</TD><TD>Integer (old version)</TD>
    *                               <TD>Integer (new version)</TD></TR>
    *   <TR><TD>ADDRESS_CHANGED</TD><TD>changed broker</TD><TD>MQAddress (old address)</TD>
    *                               <TD>MQAddress (new address)</TD></TR>
    *   <TR><TD>MASTER_BROKER_CHANGED</TD><TD>null</TD><TD>old master (ClusteredBroker)</TD>
    *                    <TD>new master (ClusteredBroker)</TD>
    *                    <TD>the master broker  has changed</TD></TR>
    * </TABLE>
    *
    * @param reason why this listener is being called
    * @param brokerid broker affected (if applicable)
    * @param oldvalue old value if applicable
    * @param newvalue new value if applicable
    * @param optional user data (if applicable)
    * @see ClusterListener
    */

   public void brokerChanged(ClusterReason reason, 
                 String brokerid, Object oldvalue,
                 Object newvalue, UID suid,
                 Object userData)
   {
       synchronized (listeners) {
           if (listeners.size() == 0)
                return;
       }

       BrokerChangedEntry bce = new BrokerChangedEntry(reason,
               brokerid, oldvalue, newvalue, suid,  userData); 

       // OK, if we are processing, queue up next entry
       //
       synchronized(brokerChangedEntryList) {
           brokerChangedEntryList.add(bce);
           if (brokerChangedProcessing == true) 
               return; // let other guy handle it
           brokerChangedProcessing = true;
       }

       try {
           BrokerChangedEntry process = null;

           while (true) {

               ClusterListener[] alisteners = null;
               synchronized (listeners) {
                   synchronized (brokerChangedEntryList) {    
                       if (listeners.size() == 0 || brokerChangedEntryList.isEmpty()) {
                           // nothing to do
                           brokerChangedProcessing = false;
                           break;
                       }    
                       process = (BrokerChangedEntry)brokerChangedEntryList.removeFirst();
                   } 
                   alisteners = (ClusterListener[])listeners.toArray(
                                new ClusterListener[listeners.size()]);
               }

               for (int i = 0; i < alisteners.length; i++) {

                       ClusterListener listen = (ClusterListener)alisteners[i];
                       synchronized(listeners) {
                           if (!listeners.contains(listen)) continue;
                       }

                       if (process.reason == ClusterReason.ADDED) {
                           listen.brokerAdded((ClusteredBroker)process.newValue,
                                  process.brokerSession );

                       } else if (process.reason == ClusterReason.REMOVED) {
                           listen.brokerRemoved((ClusteredBroker)process.oldValue,
                                  process.brokerSession);

                       } else if (process.reason == ClusterReason.STATUS_CHANGED) {
                           listen.brokerStatusChanged(process.brokerid,
                                  ((Integer)process.oldValue).intValue(),
                                  ((Integer)process.newValue).intValue(),
                                  process.brokerSession,
                                  process.userData);

                       } else if (process.reason == ClusterReason.STATE_CHANGED) {
                           listen.brokerStateChanged(process.brokerid,
                                  (BrokerState)process.oldValue,
                                  (BrokerState)process.newValue);
                       } else if (process.reason == ClusterReason.VERSION_CHANGED) {
                           listen.brokerVersionChanged(process.brokerid,
                                  ((Integer)process.oldValue).intValue(),
                                  ((Integer)process.newValue).intValue());
                       } else if (process.reason == ClusterReason.ADDRESS_CHANGED) {
                           listen.brokerURLChanged(process.brokerid,
                                  (MQAddress)process.oldValue,
                                  (MQAddress)process.newValue);

                       } else if (process.reason == ClusterReason.MASTER_BROKER_CHANGED) {
                           listen.masterBrokerChanged((ClusteredBroker)process.oldValue,
                                         (ClusteredBroker)process.newValue);
                       }
               }

           }
        } finally {
            synchronized (brokerChangedEntryList) {    
                brokerChangedProcessing = false;
            }
        }
       
   }

   /**
    * flag used to determine if we are in the middle of
    * listener processing when a call occurs (to make sure
    * notifications are processed in order).
     */
   private boolean brokerChangedProcessing = false;

   /**
    * list used to make sure listeners are processed in order
    */
   LinkedList brokerChangedEntryList = new LinkedList();

   /**
    * container class used when listeners are processed in order.
    */
   static private class BrokerChangedEntry
   {
       ClusterReason reason = null;
       String brokerid = null;
       Object oldValue = null;
       Object newValue = null;
       Object userData = null;
       UID brokerSession = null;

       public BrokerChangedEntry(ClusterReason reason, String brokerid,
                Object oldValue, Object newValue, UID bs,
                Object userData) {
           this.reason = reason;
           this.brokerid = brokerid;
           this.oldValue = oldValue;
           this.newValue = newValue;
           this.userData = userData;
           this.brokerSession = bs;
       }
   }


   // NOTE: for clustered brokers, the id is really
   // the same as the host:port

   int brokerindx = 0;


   public ClusteredBroker getBrokerByNodeName(String nodeName)
   throws BrokerException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".getbrokerByNodeName()");
   }
   
   /**
    * @param partitionID the partition id
    */
   public void partitionAdded(UID partitionID, Object source) {
       synchronized(supportedSessionMap) {
           if (source instanceof DestinationList) {
               supportedSessionMap.put(partitionID, 
                   Long.valueOf(System.currentTimeMillis()));
           }
       }
       
   }

   /**
    * @param partitionID the partition id
    */
   public void partitionRemoved(UID partitionID, Object source, Object destinedTo) {
       synchronized(supportedSessionMap) {
           if (Globals.getDestinationList().isPartitionMode()) {
               if (partitionID != null) {
                   supportedSessionMap.remove(partitionID);
               }
               return;
           } 
           if (partitionID != null) {
               Long v = supportedSessionMap.get(partitionID);
               if (v != null) {
                   supportedSessionMap.put(partitionID, Long.valueOf(0L));
               }
           }
           if (supportedSessionMap.size() > maxTakeoverSessions) {
               Iterator<Map.Entry<UID, Long>> itr =  
                        supportedSessionMap.entrySet().iterator();
               long currtime = System.currentTimeMillis();
               while (itr.hasNext()) {
                   Long v = itr.next().getValue();
                   if (v.longValue() == 0L || 
                       (currtime - v.longValue()) > maxTakeoverRetaintionTime) {
                       itr.remove();
                       break;
                   }
               }
           }
       }
   }

}

