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
 * @(#)ClusterManager.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api;

import java.util.Set;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionListener;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;

// for javadocs
import com.sun.messaging.jmq.jmsserver.Globals;


/**
 * This interface represents the status of the cluster.
 * The cluster configuration may be obtained from either the
 * database (for HA) or configuration properties.
 *<p>
 * The purpose of this class is to abstract how clusters
 * are configured and managed in different cluster types.
 */

@Contract
@Singleton
public interface ClusterManager extends PartitionListener
{

   /**
     * The property name used to set the transport (tcp,ssl)
     * used by the cluster service.
     */
    public static final String TRANSPORT_PROPERTY =
        Globals.IMQ + ".cluster.transport";

    /**
     * property name used to set host that the
     * cluster service binds to.
     */
    public static final String HOST_PROPERTY =
        Globals.IMQ + ".cluster.hostname";

    /**
     * The property name used to set the port
     * used by the cluster service.
     */
     public static final String PORT_PROPERTY =
        Globals.IMQ + ".cluster.port";

    /**
     * The property name used to specify the URL to load cluster
     * configuration used by the cluster service.
     */
     public static final String CLUSTERURL_PROPERTY =
        Globals.IMQ + ".cluster.url";


    /**
     * The property name used to set the name of the master
     * broker used by the cluster service.
     */
    public static final String CONFIG_SERVER =
        Globals.IMQ + ".cluster.masterbroker";

    public static final String CLUSTER_PING_INTERVAL_PROP =
        Globals.IMQ + ".cluster.ping.interval";

    public static final int CLUSTER_PING_INTERVAL_DEFAULT = 60; //secs

    /**
     * This is the property name used to set the list of brokers
     * in a cluster. This property is only the list of
     * brokers defined on the command line and does NOT
     * include any brokers passed in with -cluster.
     */
    public static final String AUTOCONNECT_PROPERTY =
        Globals.AUTOCONNECT_CLUSTER_PROPERTY;

   /**
    * Initializes the broker 
    */

   public String initialize(MQAddress address) 
           throws BrokerException;

   /**
    * @return in seconds
    */
   public int getClusterPingInterval();

   /**
    * retrieves the cluster id associated with this cluster.
    * @return the cluster id (or null if there isnt an id associated with
    *            this cluster)
    */
   public String getClusterId();

   /**
    * sets the address for the portmapper
    * @param address MQAddress to the portmapper
    * @throws Exception if something goes wrong
    *         when the address is changed
    */
   public void setMQAddress(MQAddress address) throws Exception;

   /**
    * retrieves the host/port of the local broker
    * @return the MQAddress to the portmapper
    */
   public MQAddress getMQAddress();
      
   /**
    * sets a listener for notification when the state changes.
    * <p>
    * this api is used by the Monitor Service to determine when
    * a broker should be monitored because it may be down.
    *
    * @see  ClusterListener
    * @param listener the listener to add
    */
   public void addEventListener(ClusterListener listener);

   /**
    * removes a listener for notification when the state changes.
    * <p>
    * this api is used by the Monitor Service to determine when
    * a broker should be monitored because it may be down.
    *
    *
    * @return true if the item existed and was removed.
    * @see  ClusterListener
    * @param listener the listener to remove
    */
   public boolean removeEventListener(ClusterListener listener);
   
   /**
    * returns the ClusteredBroker which represents
    * this broker.
    * @return the local broker
    * @see ClusterManager#getBroker(String)
    */
   public ClusteredBroker getLocalBroker();
   
   /**
    * returns the list of all known brokers in the
    * cluster (the union of the active and configured 
    * brokers)
    * @return count of known brokers in the cluster. 
    */
   public int getKnownBrokerCount();
 
   /**
    * returns the number of brokers configured in the
    * cluster
    * @return count of all configured brokers in the cluster. 
    */
   public int getConfigBrokerCount();

   /**
    * returns the number of active brokers in the
    * cluster
    * @return count of all active brokers in the cluster. 
    */
   public int getActiveBrokerCount();
        
   /**
    * Returns an iterator of ClusteredBroker objects for
    * all known brokers in the cluster.  This is a copy
    * of the current list.
    *
    * @param refresh refresh current list then return it
    *
    * @return iterator of ClusteredBrokers
    */
   public Iterator getKnownBrokers(boolean refresh);

   /**
    * returns an iterator of ClusteredBroker objects for
    * all active brokers in the cluster. This is a copy of
    * the current list and is accurate at the time getActiveBrokers was
    * called.
    * @return iterator of ClusteredBrokers
    */
   public Iterator getActiveBrokers();

   /**
    * returns an iterator of ClusteredBroker objects for
    * all configured brokers in the cluster. This is a copy of
    * the current list and is accurate at the time getBrokers was
    * called.
    * @return iterator of ClusteredBrokers
    */
   public Iterator getConfigBrokers();
         
   /**
    * returns a specific ClusteredBroker object by name.
    * @param brokerid the id associated with the broker
    * @return the broker associated with brokerid or null
    *         if the broker is not found
    */
   public ClusteredBroker getBroker(String brokerid);
         
   /**
    * method used in a dynamic cluster, it updates the
    * system when a new broker is added.
    *
    * @param URL the MQAddress of the new broker
    * @param brokerSession UID associated with this broker (if known)
    * @param instName the instance name of this broker
    * @param userData optional user data
    * @throws NoSuchElementException if the broker can not
    *              be added to the cluster (for example if
    *              the cluster is running in HA mode and
    *              the URL is not in the shared database)
    * @throws BrokerException if the database can not be accessed
    * @return the uid associated with the new broker
    */
   public String activateBroker(MQAddress URL, UID brokerSession, 
                                String instName, Object userData)
                                throws NoSuchElementException,
                                       BrokerException;

   /**
    * method used in a all clusters, it updates the
    * system when a new broker is added.
    *
    * @param brokerid the id of the broker (if known)
    * @param brokerSession UID associated with this broker (if known)
    * @param instName the instance name of this broker
    * @param userData optional user data
    * @throws NoSuchElementException if the broker can not
    *              be added to the cluster (for example if
    *              the cluster is running in HA mode and
    *              the brokerid is not in the shared database)
    * @throws BrokerException if the database can not be accessed
    * @return the uid associated with the new broker
    */
   public String activateBroker(String brokerid, UID brokerSession, 
                                String instName, Object userData)
                                throws NoSuchElementException,
                                       BrokerException;

   /**
    * method used in a dynamic cluster, it updates the
    * system when a broker is removed.
    *
    * @param URL the MQAddress associated with the broker
    * @param userData optional user data
    * @throws NoSuchElementException if the broker can not
    *              be found in the cluster.
    */
   public void deactivateBroker(MQAddress URL, Object userData)
       throws NoSuchElementException;        


   /**
    * method used in a dynamic cluster, it updates the
    * system when a broker is removed.
    *
    * @param brokerid the id associated with the broker
    * @param userData optional user data
    * @throws NoSuchElementException if the broker can not
    *              be found in the cluster.
    */
   public void deactivateBroker(String brokerid, Object userData)
       throws NoSuchElementException;        


    /**
    * finds the brokerid associated with the given address.
    *
    * @param broker the MQAddress of the new broker
    * @return the uid associated with the broker or null if the broker does not exist
    */  
   public String lookupBrokerID(MQAddress broker);
   
   /**
    * finds the brokerid associated with the given store session.
    *
    * @param session is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */  
   public String lookupStoreSessionOwner(UID session);


   /**
    * Retrieve the broker that creates the specified store session ID.
    * @param sessionID store session ID
    * @return the broker ID
    */
   public String getStoreSessionCreator(UID session); 

   /**
    * finds the brokerid associated with the given broker session.
    *
    * @param session is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */  
   public String lookupBrokerSessionUID(UID session);

   /**
    * the master broker in the cluster (if any).
    *
    * @return the master broker (or null if none)
    * @see ClusterManagerImpl#getBroker(String)
    */
    public ClusteredBroker getMasterBroker();


   /**
    * the transport (as a string) used by
    * the cluster of brokers.
    *
    * @return the transport (tcp, ssl)
    */
   public String getTransport();

   /**
    * Returns the port configured for the cluster service.
    *
    * @return the port to use (or 0 if dynamic)
    */
   public int getClusterPort();

   /**
    * Returns the host that the cluster service should bind to.
    *
    * @return the host to use (or null if bind to all)
    */
   public String getClusterHost();

   /**
    * is the cluster "highly available".
    *
    * @return true if the cluster is HA
    * @see Globals#getHAEnabled()
    */
   public boolean isHA();


   /**
    * Gets the UID associated with the store session.
    *
    * @return the store session uid (if any)
    */
   public UID getStoreSessionUID();

   /**
    * Gets the UID associated with the broker session.
    *
    * @return the broker session uid (if any)
    */
   public UID getBrokerSessionUID();


   /**
    * Returns a list of supported session UID's for this
    * broker (not including its own sessionUID).<p>
    * This list may not include all sessionUID's that have
    * been supported by this running broker (ids may age
    * out over time).
    * 
    * @return the set of sessionUIDs
    */
   public Set getSupportedStoreSessionUIDs();

   /**
    * Reload the cluster properties from config 
    *
    */
   public void reloadConfig() throws BrokerException;

   /**
    */
   public MQAddress getBrokerNextToMe();


   /**
    * @return a set of MQAddress from host:port,host:port,..
    */
   public LinkedHashSet parseBrokerList(String values) 
       throws MalformedURLException, UnknownHostException; 

   /**
    * used by replicated BDB 
    */
   public ClusteredBroker getBrokerByNodeName(String nodeName)
   throws BrokerException;

}
