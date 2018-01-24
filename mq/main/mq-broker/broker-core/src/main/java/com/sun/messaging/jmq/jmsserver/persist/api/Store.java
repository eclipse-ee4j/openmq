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
 * @(#)Store.java	1.123 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.sun.messaging.bridge.api.DupKeyException;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.api.KeyNotFoundException;
import com.sun.messaging.bridge.api.UpdateOpaqueDataCallback;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.ClusterTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.txnlog.TransactionLogWriter;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;

/**
 * Store provides API for storing and retrieving
 * various kinds of data used by the broker: message,
 * per message interest list, interest objects and destination objects.
 * Classes implementing this interface provide the actual mechanism
 * to persist the data.
 */
@Contract
@Singleton
public abstract class Store implements JMSBridgeStore {

    public static final String STORE_PROP_PREFIX
                        = Globals.IMQ + ".persist.store";
    public static final String CREATE_STORE_PROP
                        = STORE_PROP_PREFIX + "create.all";
    public static final String REMOVE_STORE_PROP
                        = STORE_PROP_PREFIX + "remove.all";
    public static final String RESET_STORE_PROP
			= STORE_PROP_PREFIX + "reset.all";
    public static final String RESET_MESSAGE_PROP
			= STORE_PROP_PREFIX + "reset.messages";
    public static final String RESET_INTEREST_PROP
			= STORE_PROP_PREFIX + "reset.durables";
    public static final String UPGRADE_NOBACKUP_PROP
			= STORE_PROP_PREFIX + "upgrade.nobackup";

    public static final boolean CREATE_STORE_PROP_DEFAULT = false;

    public static final String FILE_STORE_TYPE = "file";
    public static final String JDBC_STORE_TYPE = "jdbc";
    public static final String INMEMORY_STORE_TYPE = "inmemory";
    public static final String COHERENCE_STORE_TYPE = "coherence";
    public static final String BDB_STORE_TYPE = "bdb";

    // control printing debug output by property file
    private static boolean DEBUG = false;
    private static boolean DEBUG_SYNC = 
        Globals.getConfig().getBooleanProperty(
            Globals.IMQ + ".persist.store.debug.sync") || DEBUG;

    public static final Logger logger = Globals.getLogger();
    public static final BrokerResources br = Globals.getBrokerResources();
    public static final BrokerConfig config = Globals.getConfig();

    /**
     * Variables to make sure no Store method is called after the
     * store is closed and that the close operation won't start
     * until all store operations are done.
     */
    // boolean flag indicating whether the store is closed or not
    protected boolean closed = false;
    protected Object closedLock = new Object();	// lock for closed

    // number indicating the number of store operations in progress
    protected int inprogressCount = 0;
    protected Object inprogressLock = new Object(); // lock for inprogressCount

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    public Lock txnLogSharedLock = lock.readLock();
    public Lock txnLogExclusiveLock = lock.writeLock();
    

    
    protected boolean createStore = false;
    protected boolean resetStore = false;
    protected boolean resetMessage = false;
    protected boolean resetInterest = false;
    protected boolean removeStore = false;
    protected boolean upgradeNoBackup = false;

    protected boolean partitionsReady = false;

    /**
     * Default Constructor.
     */
    protected Store() throws BrokerException {

        checkPartitionMode();

        createStore = config.getBooleanProperty(CREATE_STORE_PROP, 
                                                CREATE_STORE_PROP_DEFAULT);
        removeStore = config.getBooleanProperty(REMOVE_STORE_PROP, false);
        resetStore = config.getBooleanProperty(RESET_STORE_PROP, false);
        resetMessage = config.getBooleanProperty(RESET_MESSAGE_PROP, false);
        resetInterest = config.getBooleanProperty(RESET_INTEREST_PROP, false);
        upgradeNoBackup = config.getBooleanProperty(UPGRADE_NOBACKUP_PROP, false);

        if (removeStore) {
	        logger.logToAll(Logger.INFO, BrokerResources.I_REMOVE_PERSISTENT_STORE);
        } else {
            if (resetStore) {
                logger.logToAll(Logger.INFO, BrokerResources.I_RESET_PERSISTENT_STORE);
            } else {
                if (resetMessage) {
                    logger.logToAll(Logger.INFO, BrokerResources.I_RESET_MESSAGE);
                }
                if (resetInterest) {
                    logger.logToAll(Logger.INFO, BrokerResources.I_RESET_INTEREST);
                }
	        }
            if (!resetStore && (resetMessage||resetInterest)) {
                logger.logToAll(Logger.INFO, BrokerResources.I_LOAD_REMAINING_STORE_DATA);
            } else if (!resetStore) {
                logger.logToAll(Logger.INFO, BrokerResources.I_LOAD_PERSISTENT_STORE);
            }
        }
    }

    public void checkPartitionMode() throws BrokerException {
        if (StoreManager.isConfiguredPartitionMode(false)) {
            throw new BrokerException(br.getKString(
            br.X_PARTITION_MODE_NOT_SUPPORTED, getStoreType()));
        }
    }

    /**
     * @return list of store property settings that must be
     *         enforced cluster-wide consistent  
     * @exception BrokerException
     */
    public Map<String, String> getClusterMatchProperties() 
    throws BrokerException {
        return new LinkedHashMap<String, String>();
    }

    /***********************************************************
     * BEGIN Partition Specific Methods 
     ***********************************************************/

    public void partitionsReady() throws BrokerException {
        synchronized(closedLock) {
            partitionsReady = true;
        }
    }

    /**
     * sub class must override this method
     */
    public List<PartitionedStore> getAllStorePartitions() throws BrokerException {
        synchronized(closedLock) {
            if (!partitionsReady) {
                throw new BrokerException("IllegalState: store partitions not ready");
            }
            if (closed) {
                logger.log(Logger.WARNING, BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED);
                throw new BrokerException(
                    br.getString(BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED),
                    Status.NOT_ALLOWED);
            }
        }
        return null;
    }

    public boolean getPartitionModeEnabled() {
        return false;
    }

    public boolean isPartitionMigratable() {
        return false;
    }

    public abstract PartitionedStore getPrimaryPartition()
    throws BrokerException;

    /**
     * The partition to be moved must have been closed 
     *
     * @param partitionID the partition id to be moved
     * @param targetBrokerID the target broker the partition to be moved to 
     * @exception BrokerException if failure, with status code
     *           NOT_MODIFIED if not moved 
     *           ERROR if unknown outcome    
     */
    public void partitionDeparture(UID partitionID, String targetBrokerID)
    throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Notified that a partition has arrived this broker
     * @param id the partition id
     *
     * @exception BrokerException 
     */
    public List<PartitionedStore> partitionArrived(UID id) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public PartitionedStore getStorePartition(UID id)
    throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * @param listener the PartitionListener to be added
     * @exception BrokerException
     */
    public void addPartitionListener(PartitionListener listener)
    throws BrokerException {
    }

    /**
     * @param listener the PartitionListener to be removed 
     * @exception BrokerException
     */
    public void removePartitionListener(PartitionListener listener)
    throws BrokerException {
    }

    /*********
     * PartitionedStore methods that not all store types support
     *
     * They are here to avoid every store type implementation 
     * that doesn't support it, has to do the same 
     ********************/
    public void repairCorruptedSysMessageID(SysMessageID realSysId,
        String badSysIdStr, String duidStr, boolean sync)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }
    public void removeMessage(DestinationUID duid, String sysid, boolean sync)
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /***********************************************************
     * END of Partition Specific Methods 
     ***********************************************************/

    /**
     * @param listener the StoreSessionReaperListener to be added
     * @exception BrokerException
     */
    public void addStoreSessionReaperListener(StoreSessionReaperListener listener)
    throws BrokerException {
    }

    /**
     * @param listener the StoreSessionReaperListener to be removed 
     * @exception BrokerException
     */
    public void removeStoreSessionReaperListener(StoreSessionReaperListener listener)
    throws BrokerException {
    }

    public static boolean getDEBUG() {
        return DEBUG;
    }
    
    public static boolean getDEBUG_SYNC() {
        return DEBUG_SYNC;
    }


    /**
     * Get the store version.
     * @return store version
     */
    public abstract int getStoreVersion();


    public ShareConfigChangeStore getShareConfigChangeStore()
        throws BrokerException {

        if ( !Globals.useSharedConfigRecord() ) {
            RuntimeException ex =new UnsupportedOperationException(
                br.getKString(br.E_INTERNAL_BROKER_ERROR, "Unexpected call"));
            logger.logStack(logger.ERROR, ex.getMessage(), ex);
            throw ex;
        }
        return StoreManager.getShareConfigChangeStore();
    }

    /**
     * Return the number of persisted messages for the given broker (HA support).
     *
     * @param brokerID the broker ID
     * @return the number of persisted messages for the given broker
     * @exception BrokerException if an error occurs while getting the data
     */
    public int getMessageCount(String brokerID) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Store an Consumer which is uniquely identified by it's id.
     *
     * @param interest  the interest to be persisted
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the interest
     * @exception BrokerException if an interest with the same id exists in
     *                  the store already
     * @exception NullPointerException  if <code>interest</code> is
     *                  <code>null</code>
     */
    public abstract void storeInterest(Consumer interest, boolean sync)
        throws IOException, BrokerException;

    /**
     * Remove the interest from the persistent store.
     *
     * @param interest  the interest to be removed from persistent store
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the interest
     * @exception BrokerException if the interest is not found in the store
     */
    public abstract void removeInterest(Consumer interest, boolean sync)
        throws IOException, BrokerException;

    /**
     * Retrieve all interests in the store.
     *
     * @return an array of Interest objects; a zero length array is
     * returned if no interests exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public abstract Consumer[] getAllInterests()
        throws IOException, BrokerException;

    /**
     * Remove all messages associated with the specified destination
     * from the persistent store.
     *
     * @param destination   the destination whose messages are to be removed
     * @param sync          if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the messages
     * @exception BrokerException if the destination is not found in the store
     * @exception NullPointerException  if <code>destination</code> is
     *                  <code>null</code>
     */
    public abstract void removeAllMessages(Destination destination,
        boolean sync) throws IOException, BrokerException;

    /**
     * Return the LoadException for loading consumers; null if there's none.
     */
    public LoadException getLoadConsumerException() {
	return null;
    }

    /**
     * Close the store and releases any system resources associated with
     * it. The store will be cleaned up. All data files trimed to the
     * length of valid data.
     */
    public void close() {
	close(true);
    }

    /**
     * Close the store and releases any system resources associated with
     * it.
     * @param cleanup if this is false, the store will not be cleaned up
     *			when it is closed.  The default behavior is that
     *			the store is cleaned up.
     */
    public abstract void close(boolean cleanup);

    /**
     * Persist the specified property name/value pair.
     * If the property identified by name exists in the store already,
     * it's value will be updated with the new value.
     * If value is null, the property will be removed.
     * The value object needs to be serializable.
     *
     * @param name  the name of the property
     * @param value the value of the property
     * @param sync  if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting the
     *			data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public abstract void updateProperty(String name, Object value, boolean sync)
	throws BrokerException;

    /**
     * Retrieve the value for the specified property.
     *
     * @param name name of the property whose value is to be retrieved
     * @return the property value; null is returned if the specified
     *		property does not exist in the store
     * @exception BrokerException if an error occurs while retrieving the
     *			data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public abstract Object getProperty(String name) throws BrokerException;

    /**
     * Return the names of all persisted properties.
     *
     * @return an array of property names; an empty array will be returned
     *		if no property exists in the store.
     */
    public abstract String[] getPropertyNames() throws BrokerException;

    /**
     * Return all persisted properties.
     *
     * @return a properties object.
     */
    public abstract Properties getAllProperties() throws BrokerException;

    /**
     * Append a new record to the config change record store.
     * The timestamp is also persisted with the recordData.
     * The config change record store is an ordered list (sorted
     * by timestamp).
     *
     * @param timestamp     The time when this record was created.
     * @param recordData    The record data.
     * @param sync	    if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting
     *			the data or if the timestamp is less than 0
     * @exception NullPointerException if <code>recordData</code> is
     *			<code>null</code>
     */
    public abstract void storeConfigChangeRecord(
	long timestamp, byte[] recordData, boolean sync) throws BrokerException;

    /**
     * Get all the config change records since the given timestamp.
     * Retrieves all the entries with recorded timestamp greater than
     * the specified timestamp.
     * 
     * @return a list of ChangeRecordInfo, empty list if no records 
     * @exception BrokerException if an error occurs while getting
     * the data.
     */
    public abstract ArrayList<ChangeRecordInfo> getConfigChangeRecordsSince(long timestamp)
	throws BrokerException;

    /**
     * Return all config records with their corresponding timestamps.
     *
     * @return a list of ChangeRecordInfo
     * @exception BrokerException if an error occurs while getting the data
     */
    public abstract List<ChangeRecordInfo> getAllConfigRecords() throws BrokerException;

    /**
     * Clear all config change records in the store.
     *
     * @param sync  if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while clearing the data
     */
    public abstract void clearAllConfigChangeRecords(boolean sync)
	throws BrokerException;

    /**
     * Clear the store. Remove all persistent data.
     */
    public abstract void clearAll(boolean sync) throws BrokerException;

    /**
     * Get information about the underlying storage for the specified
     * destination.
     * @return A HashMap of name value pair of information
     */
    public abstract HashMap getStorageInfo(Destination destination)
	throws BrokerException;

    /**
     * Return the type of store.
     * @return A String
     */
    public abstract String getStoreType();

    /**
     * Return true if the store is a JDBC Store.
     * @return true if the store is a JDBC Store
     */
    public boolean isJDBCStore() {
        return true;
    }

    public boolean isBDBStore() {
        return false;
    }

    public boolean isHADBStore() {
        return false;
    }

    public void resetConnectionPool() throws BrokerException {
        throw new UnsupportedOperationException(
        "resetConnectionPool: operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public abstract Hashtable getDebugState()
	throws BrokerException;

    // HA cluster support APIs

    /**
     * Get the last heartbeat timestamp for a broker (HA support).
     *
     * @param brokerID the broker ID
     * @return the broker last heartbeat timestamp
     * @throws BrokerException
     */
    public long getBrokerHeartbeat(String brokerID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the last heartbeat timestamps for all the brokers (HA support).
     *
     * @return a HashMap object where the key is the broker ID and the entry
     * value is the broker's heartbeat timestamps
     * @throws BrokerException
     */
    public HashMap getAllBrokerHeartbeats() throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Update the broker heartbeat timestamp to the current time (HA support).
     *
     * @param brokerID the broker ID
     * @return new heartbeat timestamp if successfully updated else null or exception
     * @throws BrokerException
     */
    public Long updateBrokerHeartbeat(String brokerID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Update the broker heartbeat timestamp only if the specified
     * lastHeartbeat match the value store in the DB (HA support).
     *
     * @param brokerID the broker ID
     * @param lastHeartbeat the last heartbeat timestamp
     * @return new heartbeat timestamp if successfully updated else null or exception
     * @throws BrokerException
     */
    public Long updateBrokerHeartbeat(String brokerID,
                                      long lastHeartbeat)
                                      throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Add a broker to the store and set the state to INITIALIZED (HA support).
     *
     * @param brokerID the broker ID
     * @param sessionID the store session ID
     * @param URL the broker's URL
     * @param version the current version of the running broker
     * @param heartbeat heartbeat timestamp
     * @throws BrokerException
     */
    public void addBrokerInfo(String brokerID, String URL,
        BrokerState state, int version, long sessionID, long heartbeat)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }


    /**
     * Add a broker to the store and set the state to INITIALIZED (HA support).
     *
     * @param brokerID the broker ID
     * @param takeOverBkrID the broker ID taken over the store.
     * @param sessionID the store session ID
     * @param URL the broker's URL
     * @param version the current version of the running broker
     * @param heartbeat heartbeat timestamp
     * @throws BrokerException
     */
    public void addBrokerInfo(String brokerID, String takeOverBkrID, String URL,
        BrokerState state, int version, long sessionID, long heartbeat)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Update the broker info for the specified broker ID (HA support).
     *
     * @param brokerID the broker ID
     * @param updateType update Type
     * @param oldValue (depending on updateType)
     * @param newValue (depending on updateType)
     * @return current active store session UID if requested by updateType
     * @throws BrokerException
     */
    public UID updateBrokerInfo( String brokerID, int updateType,
                                  Object oldValue, Object newValue)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Remove inactive store sessions (HA support).
     *
     * Note: Will be called from the store session reaper thread.
     *
     * @throws BrokerException
     */
    public void removeInactiveStoreSession() throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the broker info for all brokers (HA support).
     *
     * @return a HashMap object consisting of HABrokerInfo objects.
     * @throws BrokerException
     */
     public HashMap getAllBrokerInfos()
        throws BrokerException {
        return getAllBrokerInfos(false);
    }

    /**
     * Get the broker info for all brokers (HA support).
     *
     * @param loadSession specify if store sessions should be retrieved
     * @return a HashMap object consisting of HABrokerInfo objects.
     * @throws BrokerException
     */
     public HashMap getAllBrokerInfos(boolean loadSession)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the broker info for the specified broker ID (HA support).
     *
     * @param brokerID the broker ID
     * @return a HABrokerInfo object that encapsulates general information
     * about broker in an HA cluster
     * @throws BrokerException
     */
    public HABrokerInfo getBrokerInfo(String brokerID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get broker info for all brokers in the HA cluster with the specified
     * state (HA support).
     *
     * @param state the broker state
     * @return A HashMap. The key is the broker ID and the value of each entry
     * is a HABrokerInfo object.
     * @throws BrokerException
     */
    public HashMap getAllBrokerInfoByState(BrokerState state)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the broker that owns the specified store session ID (HA support).
     * @param sessionID store session ID
     * @return the broker ID
     * @throws BrokerException
     */
    public String getStoreSessionOwner( long sessionID )
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public boolean ifOwnStoreSession( long sessionID, String brokerID )
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the broker that creates the specified store session ID (HA support).
     * @param sessionID store session ID
     * @return the broker ID
     * @throws BrokerException
     */
    public String getStoreSessionCreator( long sessionID )
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Update the state of a broker only if the current state matches the
     * expected state (HA support).
     *
     * @param brokerID the broker ID
     * @param newState the new state
     * @param expectedState the expected state
     * @return true if the state of the broker has been updated
     * @throws BrokerException
     */
    public boolean updateBrokerState(String brokerID, BrokerState newState,
        BrokerState expectedState, boolean local) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the state of a broker (HA support).
     *
     * @param brokerID the broker ID
     * @return the state of the broker
     * @throws BrokerException
     */
    public BrokerState getBrokerState(String brokerID) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Get the state for all brokers (HA support).
     *
     * @return an array of Object whose 1st element contains an ArrayList of
     *   broker IDs and the 2nd element contains an ArrayList of BrokerState.
     * @throws BrokerException
     */
    public Object[] getAllBrokerStates() throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Try to obtain the takeover lock by updating the target broker entry
     * with the new broker, heartbeat timestamp and state in the broker
     * table. A lock can only be obtained if the target broker is not being
     * takeover by another broker, and the specified lastHeartbeat and
     * expectedState match the value store in the DB. An exception is thrown
     * if we are unable to get the lock. (HA Support)
     *
     * @param brokerID the new broker ID
     * @param targetBrokerID the broker ID of the store being taken over
     * @param lastHeartbeat the last heartbeat timestamp of the broker being takenover
     * @param expectedState the expected state of the broker being takenover
     * @throws TakeoverLockException if the current broker is unable to acquire
     *      the takeover lock
     * @throws BrokerException
     */
    public void getTakeOverLock(String brokerID, String targetBrokerID,
        long lastHeartbeat, BrokerState expectedState,
        long newHeartbeat, BrokerState newState, boolean force,
        TakingoverTracker tracker)
        throws TakeoverLockException, BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Take over a store by updating all relevant info of the target
     * broker with the new broker only if the current broker has already
     * obtained the takeover lock. This include updating the Message Table,
     * and Transaction Table (HA support).
     *
     * Note: This method should only be called after a broker is successfull
     * acquired a takeover lock by calling getTakeOverLock() method.
     *
     * @param brokerID the new broker ID
     * @param targetBrokerID the broker ID of the store being taken over
     * @return takeOverStoreInfo object that contains relevant info of the
     *      broker being taken over.
     * @throws BrokerException
     */
    public TakeoverStoreInfo takeOverBrokerStore(String brokerID,
        String targetBrokerID, TakingoverTracker tracker) 
        throws TakeoverLockException, BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    public TakeoverStoreInfo takeoverBrokerStore(String targetInstanceName,
                             UID targetStoreSession, String targetHostPort,
                             TakingoverTracker tracker) 
                             throws TakeoverLockException, BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + getStoreType() + " store" );
    }

    /**
     * Return the LoadException for loading Properties; null if there's none.
     */
    public LoadException getLoadPropertyException() {
	return null;
    }


    // all close() and close(boolean) implemented by subclasses should
    // call this first to make sure all store operations are done
    // before preceeding to close the store.
    protected void setClosedAndWait() {
	// set closed to true so that no new operation will start
	synchronized (closedLock) {
	    closed = true;
	}

    beforeWaitOnClose();

	// wait until all current store operations are done
	synchronized (inprogressLock) {
	    while (inprogressCount > 0) {
		try {
		    inprogressLock.wait();
		} catch (Exception e) {
		}
	    }
	}
    }

    protected void beforeWaitOnClose() {
    }

    /**
     * This method should be called by all store apis to make sure
     * that the store is not closed before doing the operation.
     * @throws BrokerException
     */
    protected void checkClosedAndSetInProgress() throws BrokerException {
        checkClosedAndSetInProgress(Logger.WARNING); 
    }

    protected void checkClosedAndSetInProgress(int loglevel) throws BrokerException {
	synchronized (closedLock) {
	    if (closed) {
            logger.log(loglevel, BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED);
            throw new BrokerException(
                br.getString(BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED),
                Status.NOT_ALLOWED);
	    } else {
            //increment inprogressCount
            setInProgress(true);
	    }
	}
    }

    /**
     * If the flag is true, the inprogressCount is incremented;
     * if the flag is false, the inprogressCount is decremented;
     * when inprogressCount reaches 0; it calls notify on inprogressLock
     * to wait up anyone waiting on that
     *
     * @param flag
     */
    protected void setInProgress(boolean flag) {
	synchronized (inprogressLock) {
	    if (flag) {
		inprogressCount++;
	    } else {
		inprogressCount--;
	    }

	    if (inprogressCount == 0) {
		inprogressLock.notifyAll();
	    }
	}
    }

    /**
     * @return true if the store has been closed
     */
    public boolean isClosed() {
	synchronized (closedLock) {
	    return closed;
	}
    }

    public boolean upgradeNoBackup() {
        return upgradeNoBackup;
    }

    protected boolean getConfirmation() throws BrokerException {
	try {
	    // get confirmation
	    String yes = br.getString(BrokerResources.M_RESPONSE_YES);
	    String yes_s = br.getString(BrokerResources.M_RESPONSE_YES_SHORT);
	    String no_s = br.getString(BrokerResources.M_RESPONSE_NO_SHORT);

	    String objs[] = { yes_s, no_s };

	    System.out.print(br.getString(
				BrokerResources.M_UPGRADE_NOBACKUP_CONFIRMATION, objs));
	    System.out.flush();

	    String val = (new BufferedReader(new InputStreamReader
				(System.in))).readLine();

	    // if not positive confirmation, just exit!
	    if (!yes_s.equalsIgnoreCase(val) && !yes.equalsIgnoreCase(val)) {

		System.err.println(br.getString(BrokerResources.I_STORE_NOT_UPGRADED));
                Broker.getBroker().exit(1,
                      br.getString(BrokerResources.I_STORE_NOT_UPGRADED),
                      BrokerEvent.Type.FATAL_ERROR);
	    }
	    return true;

	} catch (IOException ex) {
            logger.log(Logger.ERROR, ex.toString());
	    throw new BrokerException(ex.toString(), ex);
	}
    }

    /******************************************************************
     * Extended Store Interface methods, JMSBridgeStore (JDBC only)
     ******************************************************************/

    /**
     * Store a log record
     *
     * @param xid the global XID 
     * @param logRecord the log record data for the xid
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null
     * @exception DupKeyException if already exist
     *            else Exception on error
     */
    public void storeTMLogRecord(String xid, byte[] logRecord,
                                 String name, boolean sync,
                                 java.util.logging.Logger logger_)
                                 throws DupKeyException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Update a log record
     *
     * @param xid the global XID 
     * @param logRecord the new log record data for the xid
     * @param name the jmsbridge name
     * @param callback to obtain updated data
     * @param addIfNotExist
     * @param sync - not used
     * @param logger_ can be null
     * @exception KeyNotFoundException if not found and addIfNotExist false
     *            else Exception on error
     */
    public void updateTMLogRecord(String xid, byte[] logRecord, 
                                  String name,
                                  UpdateOpaqueDataCallback callback,
                                  boolean addIfNotExist,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws DupKeyException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Remove a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param sync - not used
     * @param logger_ can be null
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void removeTMLogRecord(String xid, String name,
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Get a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @return null if not found
     * @exception Exception if error
     */
    public byte[] getTMLogRecord(String xid, String name,
                                 java.util.logging.Logger logger_)
                                 throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");

    }


    /**
     * Get last update time of a log record
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordUpdatedTime(String xid, String name,
                                          java.util.logging.Logger logger_)
                                          throws KeyNotFoundException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Get a log record creation time
     *
     * @param xid the global XID 
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public long getTMLogRecordCreatedTime(String xid, String name,
                                          java.util.logging.Logger logger_)
                                          throws KeyNotFoundException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Get all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @return a list of log records
     * @exception Exception if error
     */
    public List getTMLogRecordsByName(String name,
                                      java.util.logging.Logger logger_)
                                      throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");

    }

    /**
     * Get all log records for a JMS bridge in a broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @return a list of log records
     * @exception Exception if error
     */
    public List getTMLogRecordsByNameByBroker(String name,
                                              String brokerID,
                                              java.util.logging.Logger logger_)
                                              throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");

    }

    /**
     * Get JMS bridge names in all log records owned by a broker
     *
     * @param brokerID
     * @param logger_ can be null
     * @return a list of log records
     * @exception Exception if error
     */
    public List getNamesByBroker(String brokerID,
                                 java.util.logging.Logger logger_)
                                 throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");

    }

    /**
     * Get keys for all log records for a JMS bridge in this broker
     *
     * @param name the jmsbridge name
     * @param logger_ can be null
     * @return a list of keys
     * @exception Exception if error
     */
    public List getTMLogRecordKeysByName(String name,
                                         java.util.logging.Logger logger_)
                                         throws Exception {
        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }


    /**
     * Add a JMS Bridge
     *
     * @param name jmsbridge name
     * @param sync - not used
     * @param logger_ can be null
     * @exception DupKeyException if already exist 
     *            else Exception on error
     */
    public void addJMSBridge(String name, boolean sync,
                             java.util.logging.Logger logger_)
                             throws DupKeyException, Exception { 

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Get JMS bridges owned by this broker
     *
     * @param logger_ can be null
     * @return list of names
     * @exception Exception if error
     */
    public List getJMSBridges(java.util.logging.Logger logger_)
                             throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * Get JMS bridges owned by a broker
     *
     * @param brokerID name
     * @param logger_ can be null
     * @return list of names
     * @exception Exception if error
     */
    public List getJMSBridgesByBroker(String brokerID,
                                      java.util.logging.Logger logger_)
                                      throws Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }


    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return updated time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeUpdatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    /**
     * @param name jmsbridge name
     * @param logger_ can be null;
     * @return created time
     * @throws KeyNotFoundException if not found
     *         else Exception on error
     */
    public long getJMSBridgeCreatedTime(String name,
                                        java.util.logging.Logger logger_)
                                        throws KeyNotFoundException, Exception {

        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

    public void closeJMSBridgeStore() throws Exception {
        throw new UnsupportedOperationException(
        "Operation not supported by the "+getStoreType() + " store");
    }

}

