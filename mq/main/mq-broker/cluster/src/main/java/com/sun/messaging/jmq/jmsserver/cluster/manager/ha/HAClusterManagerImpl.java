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
 * @(#)HAClusterManagerImpl.java	1.77 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.manager.ha;

import java.util.*;
import java.net.MalformedURLException;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.persist.api.TakeoverStoreInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.manager.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

// XXX FOR TEST CLASS
import java.io.*;


/**
 * This class extends ClusterManagerImpl and is used to obtain and
 * distribute cluster information in an HA cluster.
 */

@Service(name = "com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAClusterManagerImpl")
@Singleton
public class HAClusterManagerImpl extends ClusterManagerImpl 
{

// testing only property
    //private static boolean IGNORE_HADB_ERRORS = true;

    /**
     * The brokerid associated with the local broker.
     * The local broker is running in the current vm.
     */
    protected String localBrokerId = null;

    /**
     * The version of the cluster protocol.
     */
    protected int VERSION = ClusterBroadcast.VERSION_500;

    protected UID localSessionUID = null;

   /**
    * Creates an instance of HAClusterManagerImpl.
    * @throws BrokerException if the cluster information could not be loaded
    *      because of a configuration issue.
    */
   public HAClusterManagerImpl() 
       throws BrokerException
   {
       super();
   }

   /**
    * Returns if the cluster is "highly available".
    *
    * @return true if the cluster is HA
    * @throws RuntimeException if called before the cluster has
    *         been initialized by calling ClusterManager.setMQAddress
    * @see ClusterManager#setMQAddress
    */
   public boolean isHA() {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       return true;
   }

   /**
    * Reload the cluster properties from config 
    *
    */
   public void reloadConfig() throws BrokerException {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       String[] props = { CLUSTERURL_PROPERTY };
       config.reloadProps(Globals.getConfigName(), props, false);
   }


   /**
    * Retrieves the Map used to store all objects. In the HA broker,
    * the map automatically checks the database if a broker can not
    * be found in memory.
    * @return the map used to store the brokers.
    * @throws BrokerException if something goes wrong loading brokers
    *             from the database
    */
   protected Map initAllBrokers(MQAddress myaddr) 
        throws BrokerException
   {
       return newHABrokerInfoMap();
   }


   /**
    * Method which initializes the broker cluster. (Called by
    * ClusterManager.setMQAddress()).
    *
    * @param address the address for the portmapper
    *
    * @see ClusterManager#setMQAddress
    * @throws BrokerException if something goes wrong during intialzation 
    */
   public String initialize(MQAddress address) 
        throws BrokerException
   {
        logger.log(Logger.DEBUG, "initializingCluster at " + address);

        localBrokerId = Globals.getBrokerID();

        if (localBrokerId == null) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.E_BAD_BROKER_ID, "null"));
        }

        // make sure master broker is not set
        String mbroker = config.getProperty(CONFIG_SERVER);

        if (mbroker != null) {
            logger.log(Logger.WARNING, 
                   Globals.getBrokerResources().getKString(
                   BrokerResources.W_HA_MASTER_BROKER_NOT_ALLOWED, 
                   CONFIG_SERVER+"="+mbroker));
        }

        super.initialize(address);

        return localBrokerId;
   }

   protected void checkStore() throws BrokerException {
       if (!StoreManager.isConfiguredJDBCStore()) {
           throw new BrokerException(
                 Globals.getBrokerResources().getKString(
                 BrokerResources.E_HA_CLUSTER_INVALID_STORE_CONFIG));
       }
   }

   /**
    * @return true if allow configured master broker
    */
   protected boolean allowMasterBroker() {
       return false; 
   }


   /**
    * Method used to retrieve the list of brokers. In HA, this
    * method displays warnings if the old cluster properties
    * have been set and then loads the brokers from the database.
    *
    * @return a set of MQAddress objects which contains all known
    *         brokers except the local broker
    * @throws MalformedURLException if the address of a broker
    *         stored in the database is invalid.
    */
   protected LinkedHashSet parseBrokerList()
       throws MalformedURLException
   {
       // ignore properties, we get the list from the 
       // database
   
        String propfileSetting = config.getProperty(AUTOCONNECT_PROPERTY);
        String cmdlineSetting = config.getProperty(Globals.IMQ 
                                + ".cluster.brokerlist.manual");

        if (propfileSetting != null) {
             logger.log(Logger.INFO,
                 BrokerResources.I_HA_IGNORE_PROP,
                 AUTOCONNECT_PROPERTY);
        }
        if (cmdlineSetting != null) {
             logger.log(Logger.INFO,
                 BrokerResources.I_HA_IGNORE_PROP,
                 Globals.IMQ + ".cluster.brokerlist.manual");
        }
        LinkedHashSet brokers = new LinkedHashSet();
        synchronized(allBrokers) {
            Iterator itr = allBrokers.values().iterator();
            while (itr.hasNext()) {
                Object obj = itr.next();
                HAClusteredBroker hab = (HAClusteredBroker)obj;
                if (!hab.isLocalBroker())
                    brokers.add(hab.getBrokerURL());
            }
        }
        return brokers;
   }

   /**
    * Method used in ClusterManagerImpl (both in initialization and
    * in normal operation) to add a broker.
    * <p>
    *<b>NOTE:</b> broker created is an HAClusteredBroker.<p>
    * @param URL the MQAddress of the new broker
    * @param isLocal indicates if this is the current broker in this
    *                vm.
    * @throws NoSuchElementException if the broker listed is
    *              not available in the shared store (since this
    *              indicates a misconfiguration).
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    * @return the uid associated with the new broker
    */
   protected String addBroker(MQAddress URL, boolean isLocal, boolean isConfig,
             UID brokerUID)
       throws NoSuchElementException, BrokerException
   {
       // NOTE we are always a config broker in the HA case, ignore this argument

       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       String brokerid = null;

       ClusteredBroker cb = null;
       if (isLocal) { // get the broker id
           brokerid = localBrokerId;

           // see if the broker exists, if not create one
           cb = getBroker(brokerid);

           // NOTE: in HA, the Monitor class will have to validate the
           // URL and update if necessary

           if (cb == null) {
               cb = newHAClusteredBroker(brokerid, URL, VERSION,
                             BrokerState.INITIALIZING, brokerUID);
               ((HAClusteredBrokerImpl)cb).setIsLocal(true);
               cb.setInstanceName(Globals.getConfigName());
           } else {
               ((HAClusteredBrokerImpl)cb).setIsLocal(true);
               cb.setInstanceName(Globals.getConfigName());
           }
           synchronized(allBrokers) {
               allBrokers.put(brokerid, cb);
           }
       } else { // lookup id
           brokerid = lookupBrokerID(URL);
           if (brokerid != null) {
               cb = getBroker(brokerid);
           }
       }
       if (brokerUID != null) {
           cb.setBrokerSessionUID(brokerUID);
       }

       // OK, if we are here we need to create a new one
       if (brokerid == null ) {
           throw new NoSuchElementException(
              Globals.getBrokerResources().getKString(
              BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, URL));
       }
       
       if (isLocal) { // OK, we know the local broker is up
                      // for all others, activate must be called
           cb.setStatus(BrokerStatus.ACTIVATE_BROKER, null);
       } else {
           updateBroker(cb);
       }
       brokerChanged(ClusterReason.ADDED, cb.getBrokerName(),
                     null, cb, cb.getBrokerSessionUID(), null);

       return brokerid;

   }

   protected HAClusteredBroker newHAClusteredBroker(String brokerid,
             MQAddress url, int version, BrokerState state, UID session)
             throws BrokerException {

       return new HAClusteredBrokerImpl(brokerid, url, 
                        version, state, session, this);
   }

    protected ClusteredBroker updateBrokerOnActivation(
              ClusteredBroker broker, Object userData) {
        return updateBroker(broker);
    }

    private ClusteredBroker updateBroker(ClusteredBroker broker)  
    {
         // force an update
         synchronized(allBrokers) {
             return (HAClusteredBroker)((AutoClusterBrokerMap)allBrokers).get(broker.getBrokerName(), true);
         }
    }

   /**
    * Method used in a dynamic cluster, it updates the
    * system when a broker is removed.
    *
    * @param brokerid the id associated with the broker
    * @param userData optional user data
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

       ClusteredBroker cb = getBroker(brokerid);

       if (cb == null) throw new NoSuchElementException(
             "Unknown brokerid " + brokerid);

       cb.setInstanceName(null);

           // OK, set the broker link down
       synchronized (this) {
             cb.setStatus(BrokerStatus.setBrokerLinkIsDown(
                              cb.getStatus()), userData);
       }

   }     


   /**
    * finds the brokerid associated with the given session.
    *
    * @param uid is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */
   public String lookupStoreSessionOwner(UID uid) {

       try {
           // for HA, check the database if necessary
           return Globals.getStore().getStoreSessionOwner(uid.longValue());
       } catch (Exception ex) {
           logger.logStack(logger.WARNING, ex.getMessage(), ex);
       }

       return null;
   }

   
   /**
    * Retrieve the broker that creates the specified store session ID.
    * @param uid store session ID
    * @return the broker ID
    */
   public String getStoreSessionCreator(UID uid)
   {
       try {
           return Globals.getStore().getStoreSessionCreator(uid.longValue());
       } catch (Exception ex) {
           logger.logStack(logger.INFO, ex.getMessage(), ex);
       }
       return null;
   }


   /**
    * Finds the brokerid associated with the given host/port.
    *
    * @param address the MQAddress of the new broker
    * @return the id associated with the broker or null if the broker does not exist
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    */  

   public String lookupBrokerID(MQAddress address)
   {
        // for HA, check the database if necessary
        try {
            synchronized(allBrokers) {
                ((AutoClusterBrokerMap)allBrokers).updateMap();
            }
        } catch (BrokerException e) {
            logger.logStack(logger.WARNING, e.getMessage(), e);
        }
        return super.lookupBrokerID(address);
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
        return super.getKnownBrokerCount();
   }


   /**
    * Returns an iterator of <i>HAClusteredBroker</i> objects for
    * all other brokers in the cluster. (this is a copy of
    * the current list)
    *
    * @param refresh if true refresh current list then return it
    * @return iterator of <i>HAClusteredBroker</i>
    * @throws RuntimeException if called before the cluster has
    *         been initialized by calling ClusterManager.setMQAddress
    * @see ClusterManager#setMQAddress
    */
   public Iterator getKnownBrokers(boolean refresh)
   {
       if (!initialized)
           throw new RuntimeException("Cluster not initialized");

       HashSet brokers = null;
       if (refresh) {
           try {
               synchronized(allBrokers) {
                   ((AutoClusterBrokerMap)allBrokers).updateMap(true);
               }
           } catch (BrokerException ex) {
               logger.logStack(logger.WARNING, ex.getMessage(), ex);
           }
       }

       synchronized (allBrokers) {
           brokers = new HashSet(allBrokers.values());
       }
       return brokers.iterator();
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
        return getKnownBrokers(true);
    }

    protected void addSupportedStoreSessionUID(UID uid) {
        super.addSupportedStoreSessionUID(uid);
    }

    /**
     * Called when the master broker is set or changed
     *
     * @param mbroker the brokerid associated with the
     *                master broker
     * @throws UnsupportedOperationException if called since
     *              a master broker is not allowed with an HA
     *              cluster.
     */

    protected void masterBrokerChanged(String mbroker)
    {
        // no master broker allowed !!!
        throw new UnsupportedOperationException(
             "Can not use/set/ change masterbroker");
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
        // no master broker allowed !!!
        if (name.equals(CONFIG_SERVER)) {
            throw new PropertyUpdateException(
                  Globals.getBrokerResources().getKString(
                  BrokerResources.X_HA_MASTER_BROKER_UNSUPPORTED));
        }
        super.validate(name, value);
    }

//------------------------------------------------------------
// apis added for javadoc documentation ONLY
//------------------------------------------------------------
   /**
    * Retrieves the <i>HAClusteredBroker</i> which represents
    * this broker.
    *
    * @return the local broker
    * @throws RuntimeException if the cluster has not be initialized
    *          (which occurs the first time the MQAddress is set)
    * @see ClusterManagerImpl#setMQAddress
    * @see HAClusterManagerImpl#getBroker(String)
    */
   public ClusteredBroker getLocalBroker()
   {
       return super.getLocalBroker();
   }

   protected String getLocalBrokerName() {
       return localBroker;
   }

   /**
    * Gets the session UID associated with the local broker
    *
    * @return the broker session uid (if known)
    */
   public UID getStoreSessionUID()
   {
       if (localSessionUID == null) {
           localSessionUID = ((HAClusteredBroker)getLocalBroker()).getStoreSessionUID();
       }
       return localSessionUID;
   }

   /**
    * Returns a specific <i>HAClusteredBroker</i> object by name.
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
        ClusteredBroker cb = super.getBroker(brokerid);
        if (cb != null) 
            return cb;

        // for HA, check the database if necessary
        try {
            synchronized(allBrokers) {
                ((AutoClusterBrokerMap)allBrokers).updateMap(true);
            }
        } catch (BrokerException ex) {
            logger.logStack(logger.WARNING, ex.getMessage(), ex);
        }
        return super.getBroker(brokerid);
    }

    protected Map newHABrokerInfoMap() throws BrokerException {
        return new JDBCHABrokerInfoMap(this);
    }
}
