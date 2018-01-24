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
package com.sun.messaging.jmq.jmsserver.cluster.api;

import java.util.*;
import java.net.UnknownHostException;
import java.net.MalformedURLException;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

/**
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.cluster.api.NoClusterManager")
@Singleton
public class NoClusterManager implements ClusterManager
{
    private static boolean DEBUG = false;

    private Logger logger = Globals.getLogger();
    //private BrokerResources br = Globals.getBrokerResources();

    private boolean initialized = false;

    /**
     * the local broker.
     */
    private ClusteredBroker localcb = null;

    /**
     * The id of the cluster.
     */
    private String clusterid = Globals.getClusterID();

    /**
     */
    public NoClusterManager() {
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
    * Changes the host/port of the local broker. 
    * 
    * @param address MQAddress to the portmapper
    * @throws BrokerException if something goes wrong
    *         when the address is changed
    */
   public void setMQAddress(MQAddress address) throws Exception {
       if (!initialized) {
           initialize(address); 
           return;
       } 
       ClusteredBroker cb = getLocalBroker();
       cb.setBrokerURL(address);
   }

   public int getClusterPingInterval() {
       return CLUSTER_PING_INTERVAL_DEFAULT;
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
    public void addEventListener(ClusterListener listener) {
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
    public boolean removeEventListener(ClusterListener listener) {
        return true;
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
   public ClusteredBroker getLocalBroker() {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       return localcb;
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
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       return 1;
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
   public int getConfigBrokerCount() {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       return 1;
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
   public int getActiveBrokerCount() {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       return 1;
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
   public Iterator getKnownBrokers(boolean refresh) {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }

       List l = new ArrayList();
       l.add(localcb);
       return l.iterator();
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
   public Iterator getConfigBrokers() {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       List l = new ArrayList();
       l.add(localcb);
       return l.iterator();
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
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       List l = new ArrayList();
       l.add(localcb);
       return l.iterator();
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
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       if (localcb.getBrokerName().equals(brokerid)) {
           return localcb;
       }
       return null;
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
                     throws NoSuchElementException, BrokerException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".activateBroker("+URL+" ...)");
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
                                BrokerException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".activateBroker("+brokerid+" ...)");
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
       throws NoSuchElementException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".deactivateBroker("+URL+" ...)");
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
       throws NoSuchElementException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".deactivateBroker("+brokerid+" ..)");
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
   public String lookupBrokerID(MQAddress broker) {
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       MQAddress addr = localcb.getBrokerURL();
       if (addr.equals(broker)) {
           return localcb.getBrokerName();
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
       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       UID buid = localcb.getBrokerSessionUID();
       if (buid.equals(uid)) {
           return localcb.getBrokerName();
       }
       return null;
   }

   /**
    * @return true if allow configured master broker
    */

   protected boolean allowMasterBroker() {
       return false;
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
   public ClusteredBroker getMasterBroker() {
       return null;
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

   public String getTransport() {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".getTransport()");
   }


   /**
    * Returns the port configured for the cluster service.
    * @return the port (or 0 if a dynamic port should be used)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public int getClusterPort() {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".getClusterPort()");
   }

   /**
    * Returns the host that the cluster service should bind to .
    * @return the hostname (or null if the service should bind to all)
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */
   public String getClusterHost() {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".getClusterHost()");
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
       return false;
   }


   /**
    * Reload cluster properties from config 
    *
    */
   public void reloadConfig() throws BrokerException {
       throw new UnsupportedOperationException(
       "Unexpected call: "+getClass().getName()+".reloadCluster()");
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
        throws BrokerException {

        localcb = new NoClusteredBroker(address, new UID());
        localcb.setStatus(BrokerStatus.ACTIVATE_BROKER, null);

        if (DEBUG) {
            logger.log(Logger.INFO, "ClusterManager: " + toString());
        }

        initialized = true;

        return localcb.getBrokerName();
    }

    /**
     * Returns a user-readable string representing this class.
     * @return the user-readable represeation.
     */
    public String toString() {
        return "NoClusterManager: [local=" + localcb+"]";
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
    public synchronized UID getBrokerSessionUID() {
        return getLocalBroker().getBrokerSessionUID();
    }


    /**
     */
    protected void addSupportedStoreSessionUID(UID uid) {
        throw new UnsupportedOperationException(
        "Unexpected call: "+getClass().getName()+".addSupportedStoreSessionUID()");
    }

    /**
    */
    public Set getSupportedStoreSessionUIDs() {
        return new HashSet();
    }

    public MQAddress getBrokerNextToMe() {
        throw new UnsupportedOperationException(
        "Unexpected call: "+getClass().getName()+".getBrokerNextToMe()");
    }

    public LinkedHashSet parseBrokerList(String values)
       throws MalformedURLException, UnknownHostException {
        throw new UnsupportedOperationException(
        "Unexpected call: "+getClass().getName()+".parseBrokerList()");
    }

    public ClusteredBroker getBrokerByNodeName(String nodeName)
    throws BrokerException {
        throw new UnsupportedOperationException(
        "Unexpected call: "+getClass().getName()+".getbrokerByNodeName()");
    }
    
    /**
     * @param partitionID the partition id
     */
    public void partitionAdded(UID partitionID, Object source) {
    }

    /**
     * @param partitionID the partition id
     */
    public void partitionRemoved(UID partitionID, Object source, Object destinedTo) {
    }

}

