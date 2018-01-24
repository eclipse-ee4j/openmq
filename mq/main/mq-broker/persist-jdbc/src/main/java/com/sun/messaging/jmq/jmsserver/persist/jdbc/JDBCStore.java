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
 * @(#)JDBCStore.java	1.163 07/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc;

import java.util.concurrent.locks.ReentrantLock;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessageAck;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.*;
import com.sun.messaging.jmq.jmsserver.persist.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.TakingoverTracker;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.comm.BaseDAO;
import java.io.*;
import java.sql.*;
import java.util.*;
import com.sun.messaging.bridge.api.DupKeyException;
import com.sun.messaging.bridge.api.KeyNotFoundException;
import com.sun.messaging.bridge.api.UpdateOpaqueDataCallback;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

/**
 * JDBCStore provides JDBC based persistence.
 * <br>
 * Note that some methods are NOT synchronized.
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.persist.jdbc.JDBCStore")
@Singleton
public class JDBCStore extends Store implements DBConstants, PartitionedStore {

    private static boolean DEBUG = getDEBUG();

    public static final String LOCK_STORE_PROP =
        DBManager.JDBC_PROP_PREFIX + ".lockstore.enabled";

    public static final String ENABLE_STORED_PROC_PROP =
        DBManager.JDBC_PROP_PREFIX + ".enableStoredProc";
    public static final boolean ENABLE_STORED_PROC = 
        Globals.getConfig().getBooleanProperty(ENABLE_STORED_PROC_PROP, false);
    public static final int STORED_PROC_VERSION = 500;

    private static final String MSG_ENUM_USE_CURSOR_PROP =
        DBManager.JDBC_PROP_PREFIX + ".msgEnumUseResultSetCursor";

    // current version of store
    public static final int OLD_STORE_VERSION_350 = 350;
    public static final int OLD_STORE_VERSION_370 = 370;
    public static final int OLD_STORE_VERSION_400 = 400;
    public static final int STORE_VERSION = 410;


    // database connection
    DBManager dbmgr;
    DAOFactory daoFactory;

    private HashMap pendingDeleteDsts = new HashMap(5);

    private HashMap takeoverLockMap = new HashMap();

    private StoreSessionReaperTask sessionReaper = null;
    private boolean msgEnumUseCursor = true;
    private List<Enumeration> dataEnums = Collections.synchronizedList(
                                          new ArrayList<Enumeration>()); 

    private UID partitionid = PartitionedStore.DEFAULT_UID;

    private static final String PARTITION_MODE_PROP = 
                                StoreManager.PARTITION_MODE_PROP;

    private static final boolean PARTITION_MODE_DEFAULT= false;

    private static final String PARTITION_MIGRATABLE_PROP = 
            DBManager.JDBC_PROP_PREFIX+".partitionMigratable";
    private static final boolean PARTITION_MIGRATABLE_DEFAULT = false;

    private static final String INIT_NUM_PARTITION_PROP = 
            DBManager.JDBC_PROP_PREFIX+".initialNumPartitions";
    private static final int INIT_NUM_PARTITION_DEFAULT = 1;
    private int initialNumPartitions = INIT_NUM_PARTITION_DEFAULT;

    private static final String PARTITION_STORE_CLASS_PROP = 
            DBManager.JDBC_PROP_PREFIX+".storePartitionClass";
    private static final String PARTITION_STORE_CLASS_DEFAULT = 
            "com.sun.messaging.jmq.jmsserver.persist.partition.jdbc.JDBCPartitionedStoreImpl";

    private boolean partitionMode = PARTITION_MODE_DEFAULT; 
    private boolean partitionMigratable = PARTITION_MIGRATABLE_DEFAULT; 
    private Class partitionClass = null;
    private static final String partitionClassStr = Globals.getConfig().
            getProperty(PARTITION_STORE_CLASS_PROP,
            PARTITION_STORE_CLASS_DEFAULT);

    private LinkedHashMap<UID, PartitionedStore> partitionStores = 
                         new LinkedHashMap<UID, PartitionedStore>();

    private List<PartitionListener> partitionListeners = 
                          new ArrayList<PartitionListener>();
    private List<StoreSessionReaperListener> sessionReaperListeners = 
                          new ArrayList<StoreSessionReaperListener>();

    private ReentrantLock partitionLock = new ReentrantLock();

    private FaultInjection FI = null;

    /**
     * When instantiated, the object configures itself by reading the
     * properties specified in BrokerConfig.
     */
    public JDBCStore() throws BrokerException {

        FI = FaultInjection.getInjection();

        partitionMode = StoreManager.isConfiguredPartitionMode(
                                         PARTITION_MODE_DEFAULT);

        if (partitionMode && !Globals.getHAEnabled()) {
            partitionMode = false;
            logger.log(logger.WARNING, br.getKString(
                br.W_IGNORE_PROP_SETTING, PARTITION_MODE_PROP+"="+true));
        }
        if (partitionMode) {
            try {
                partitionClass = Class.forName(partitionClassStr);
            } catch (Exception e) {
                throw new BrokerException(e.getMessage(), e);
            }
            int val = config.getIntProperty(
                      INIT_NUM_PARTITION_PROP, INIT_NUM_PARTITION_DEFAULT); 
            if (val > 0) { 
               initialNumPartitions = val; 
            }

            partitionMigratable = config.getBooleanProperty(
                PARTITION_MIGRATABLE_PROP, PARTITION_MIGRATABLE_DEFAULT); 

            String reskey = br.I_STORE_USE_PARTITION_MODE;
            if (partitionMigratable) {
                reskey = br.I_STORE_USE_MIGRATABLE_PARTITION_MODE;
            }
            String str = "";
            if (initialNumPartitions > 1) {
                str = "["+INIT_NUM_PARTITION_PROP+"="+initialNumPartitions+"]";
            }
            logger.log(logger.INFO, br.getKString(reskey, getStoreType())+str); 
        }

        dbmgr = DBManager.getDBManager();
        daoFactory = dbmgr.getDAOFactory();

	// print out info messages
        String url = dbmgr.getOpenDBURL();
        if (url == null) {
            url = "not specified";
        }

        String user = dbmgr.getUser();
        if (user == null) {
            user = "not specified";
        }

        String msgArgs[] = { String.valueOf(STORE_VERSION), dbmgr.getBrokerID(), url, user };
        logger.logToAll(Logger.INFO,
            br.getString(BrokerResources.I_JDBC_STORE_INFO, msgArgs));

        if (createStore) {
            logger.log(Logger.INFO, BrokerResources.I_STORE_AUTOCREATE_ENABLED);
        } else {
            logger.log(Logger.INFO, BrokerResources.I_STORE_AUTOCREATE_DISABLED);
        }

        msgEnumUseCursor = config.getBooleanProperty( MSG_ENUM_USE_CURSOR_PROP, !dbmgr.isHADB() );

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection( true );

            // this will create, remove, reset, or upgrade old store
            if ( !checkStore( conn ) ) {
                // false=dont unlock; since tables are dropped already
                closeDB(false);
                return;
            }

            if ( Globals.getHAEnabled() ) {
                try {
                    // Schedule inactive store session reaper for every 24 hrs.
                    long period = 86400000; // 24 hours
                    long delay = 60000 + (long)(Math.random() * 240000); // 1 - 5 mins
                    sessionReaper = new StoreSessionReaperTask(this);
                    Globals.getTimer().schedule(sessionReaper, delay, period);
                    logger.log(Logger.INFO, br.getKString(
                               br.I_STORE_SESSION_REAPER_SCHEDULED)+
                               "[delay="+delay+", period="+period+"]");
                } catch (IllegalStateException e) {
                    logger.logStack(Logger.WARNING, 
                        br.getKString(br.W_CANNOT_SCHEDULE_STORE_SESSION_REAPER), e);
                }
            } else {
                // Lock the tables so that no other processes will access them
                if ( config.getBooleanProperty( LOCK_STORE_PROP, true ) ) {
                    dbmgr.lockTables( conn, true );
                }
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex );
        }

        dbmgr.setStoreInited(true);

        if (DEBUG) {
            logger.log(Logger.INFO, "JDBCStore instantiated.");
        }
    }

    @Override
    public void checkPartitionMode() throws BrokerException {
    }

    /**
     * Get the JDBC store version.
     * @return JDBC store version
     */
    public final int getStoreVersion() {
        return STORE_VERSION;
    }

    /**
     * @return list of property settings that must be enforced cluster-wide consistent  
     */
    @Override
    public Map<String, String> getClusterMatchProperties() 
    throws BrokerException {

        Map<String, String> map = new LinkedHashMap<String, String>();

        if (partitionMode) {
            map.put(PARTITION_MODE_PROP, "true");
            if (partitionMigratable) {
                map.put(PARTITION_MIGRATABLE_PROP, "true");
            }
        }
        if (Globals.getHAEnabled()) {
            map.put(StoreManager.STORE_TYPE_PROP, getStoreType());
        }
        return map;
    }

    /**
     * Store a message, which is uniquely identified by it's SysMessageID,
     * and it's list of interests and their states.
     *
     * @param message	the message to be persisted
     * @param iids	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     */
    public void storeMessage(DestinationUID dst, Packet message,
	ConsumerUID[] iids, int[] states, boolean sync)
	throws BrokerException {

        if (partitionMode) {
            String emsg = br.getKString(br.E_INTERNAL_BROKER_ERROR, 
                "JDBCStore.storeMessage(,,,,,): Unexpected call in partition-mode");
            BrokerException ex = new BrokerException(emsg);
            logger.logStack(logger.ERROR, emsg, ex);
            throw ex;
        }

        if (iids.length == 0 || iids.length != states.length) {
            throw new BrokerException(br.getKString(
                BrokerResources.E_BAD_INTEREST_LIST));
        }

        storeMessage(dst, message, iids, states, getStoreSession(), true);
    }

    /**
     * Store a message which is uniquely identified by it's system message id.
     *
     * @param message	the readonly packet to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     */
    public void storeMessage(DestinationUID dst, Packet message, boolean sync)
	throws BrokerException {

        if (partitionMode) {
            String emsg = br.getKString(br.E_INTERNAL_BROKER_ERROR,
                "JDBCStore.storeMessage(,,,): Unexpected call in partition-mode");
            BrokerException ex = new BrokerException(emsg);
            logger.logStack(logger.ERROR, emsg, ex);
            throw ex;
        }

        storeMessage(dst, message, null, null, getStoreSession(), true);
    }

    protected void storeMessage(DestinationUID dst, Packet message,
	ConsumerUID[] iids, int[] states, long storeSessionID,
        boolean checkMsgExist) throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            storeMessageInternal(dst, message, iids, states,
                                 storeSessionID, checkMsgExist);
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public void storeMessageInternal(DestinationUID dst, Packet message,
	ConsumerUID[] iids, int[] states, long storeSessionID,
        boolean checkMsgExist) throws BrokerException {

        if (message == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
            if (iids == null) {
	        logger.log(Logger.INFO,
                    "JDBCStore.storeMessageInternal("+dst+", "+
                    message.getSysMessageID().getUniqueName()+
                    ", "+Arrays.toString(iids)+", "+Arrays.toString(states)+
                    ", "+storeSessionID+", "+checkMsgExist+")");
            } else {
                StringBuffer buf = new StringBuffer();
                for (int i = 0; i < iids.length; i++) {
                    buf.append("[consumer="+iids[i]+", state="+states[i]+"], ");
                }
                logger.log(Logger.INFO,
                "JDBCStore.storeMessageInternal("+dst+", "+
                 message.getSysMessageID().getUniqueName()+
                ", "+buf.toString()+storeSessionID+", "+checkMsgExist+")");
           }
	}

        boolean replaycheck = false;
        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getMessageDAO().insert(null, dst, message, iids,
                           states, storeSessionID, message.getTimestamp(),
                           checkMsgExist, replaycheck);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
            replaycheck = retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Move the message from one destination to another.
     * The message will be stored in the target destination with the
     * passed in consumers and their corresponding states.
     * After the message is persisted successfully, the message in the
     * original destination will be removed.
     *
     * @param message	message to be moved
     * @param from	destination the message is currently in 
     * @param to	destination to move the message to
     * @param iids	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk.
     * @exception IOException if an error occurs while moving the message
     * @exception BrokerException if the message is not found in source
     *		destination
     * @exception NullPointerException	if <code>message</code>, 
     *			<code>from</code>, <code>to</code>,
     *			<code>iids</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public void moveMessage(Packet message, DestinationUID from,
	DestinationUID to, ConsumerUID[] iids, int[] states, boolean sync)
	throws BrokerException {

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
            moveMessageInternal(message, from, to, iids, states, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    public void moveMessageInternal(Packet message, DestinationUID from,
	DestinationUID to, ConsumerUID[] iids, int[] states, boolean sync)
	throws BrokerException {

        if (message == null || from == null || to == null) {
            throw new NullPointerException();
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.moveMessageInternal("+message.getSysMessageID().getUniqueName()+
                ", "+from+", "+to+", "+Arrays.toString(iids)+", "+Arrays.toString(states)+", "+sync+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getMessageDAO().moveMessage(
                    null, message, from, to, iids, states);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * This repairs the corrupted system message id 
     */
    @Override
    public void repairCorruptedSysMessageID(SysMessageID realSysId, 
        String badSysIdStr, String duidStr, boolean sync) 
	throws BrokerException {

	// make sure store is not closed then increment in progress count
	super.checkClosedAndSetInProgress();

	try {
            repairCorruptedSysMessageIDInternal(realSysId, badSysIdStr, duidStr, sync);
	} finally {
	    // decrement in progress count
	    super.setInProgress(false);
	}
    }

    public void repairCorruptedSysMessageIDInternal(
        SysMessageID realSysId, String badSysIdStr, 
        String duidStr, boolean sync)
	throws BrokerException {

        if (realSysId == null || badSysIdStr == null || duidStr == null) {
            throw new NullPointerException();
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.repairCorruptedSysMessageIDInternal("+
                realSysId.getUniqueName()+", "+badSysIdStr+", "+duidStr+", "+sync+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getMessageDAO().repairCorruptedSysMessageID(
                    null,realSysId, badSysIdStr, duidStr);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }


    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not found in the store
     */
    public void removeMessage(DestinationUID dID, SysMessageID mID, boolean sync, boolean onRollback)
	throws BrokerException {

        String id =  mID.getUniqueName();

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            removeMessageInternal(dID, id, sync, onRollback);
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public void removeMessage(DestinationUID dID, String id, boolean sync)
	throws BrokerException {

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            removeMessageInternal(dID, id, sync, false);
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public void removeMessageInternal(
        DestinationUID dID, String id, boolean sync, boolean onRollback)
        throws BrokerException {
        if (id == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.removeMessageInternal("+ 
                       dID+", "+id+", "+sync+", "+onRollback);
	}

        boolean replaycheck = false;
        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getMessageDAO().delete(null, dID, id, replaycheck);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                replaycheck = retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Remove all messages associated with the specified destination
     * from the persistent store.
     *
     * @param dst	the destination whose messages are to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the messages
     * @exception BrokerException if the destination is not found in the store
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void removeAllMessages(Destination dst, boolean sync)
	throws IOException, BrokerException {

        if (dst == null) {
            throw new NullPointerException();
        }

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.removeAllMessages("+dst.getUniqueName()+")");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getMessageDAO().deleteByDestinationBySession(
                        null, dst.getDestinationUID(), null);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Return an enumeration of all persisted messages for the given
     * destination.
     * Use the Enumeration methods on the returned object to fetch
     * and load each message sequentially.
     *
     * <p>
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @return an enumeration of all persisted messages, an empty
     *		enumeration will be returned if no messages exist for the
     *		destionation
     * @exception BrokerException if an error occurs while getting the data
     */
    public Enumeration messageEnumeration(Destination dst)
	throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            return messageEnumerationInternal(dst, null);
        } catch (RuntimeException e) {
            setInProgress(false);
            throw e;
        } catch (BrokerException e) {
            setInProgress(false);
            throw e;

        } finally {
            if (!msgEnumUseCursor) {
                setInProgress(false);
            }
        }
    }

    public boolean getMessageEnumUseCursor() {
        return msgEnumUseCursor;
    }

    public Enumeration messageEnumerationInternal(Destination dst, Long storeSession)
	throws BrokerException {

        if (dst == null) {
            throw new NullPointerException();
        }
	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.messageEnumerationInternal("+dst.getUniqueName()+", "+storeSession+")");
	}
        String brokerID = dbmgr.getBrokerID();

        if (!msgEnumUseCursor) {

            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getMessageDAO().messageEnumeration(
                        dst, brokerID, storeSession);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } else {

            Enumeration en = null;
            try {

            Util.RetryStrategy retry = null;
            do {
                try {
                    en = daoFactory.getMessageDAO().
                             messageEnumerationCursor(dst, brokerID, storeSession);
                    dataEnums.add(en);
                    return en;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );

            } catch (Throwable e) {
                if (en != null) {
                    dataEnums.remove(en);
                    ((MessageEnumeration)en).close();
                }
                if (e instanceof BrokerException) {
                    throw (BrokerException)e;
                }
                throw new BrokerException(e.toString(), e);
            }
        } 
    }

    public void closeEnumeration(Enumeration en) {
        if (!(en instanceof MessageEnumeration)) {
            return;
        }
        try {
            closeEnumerationInternal(en);
        } finally { 
            setInProgress(false);
        }
    }

    public void closeEnumerationInternal(Enumeration en) {
        dataEnums.remove(en);
        ((MessageEnumeration)en).close();
    }

    /**
     * Return the number of persisted messages for the given broker.
     *
     * @return the number of persisted messages for the given broker
     * @exception BrokerException if an error occurs while getting the data
     */
    public int getMessageCount(String brokerID) throws BrokerException {

        if (brokerID == null) {
            throw new NullPointerException();
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.getMessageCount() called for broker: " + brokerID);
	}
        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getMessageDAO().getMessageCount(null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    /**
     * Return the number of persisted messages and total number of bytes for
     * the given destination.
     *
     * @param dst the destination whose messages are to be counted
     * @return A HashMap of name value pair of information
     * @throws BrokerException if an error occurs while getting the data
     */
    public HashMap getMessageStorageInfo(Destination dst)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        super.checkClosedAndSetInProgress();

        try {
            return getMessageStorageInfoInternal(dst, null);
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public HashMap getMessageStorageInfoInternal(Destination dst, Long storeSession)
        throws BrokerException {
        if (dst == null) {
            throw new NullPointerException();
        }
        if (Store.getDEBUG()) {
            logger.log(Logger.INFO,
                "JDBCStore.getMessageStorageInfoInternal("+dst.getUniqueName()+", "+storeSession+")");
        }
        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getMessageDAO().getMessageStorageInfo(null, dst, storeSession);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Return the message with the specified message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dID, SysMessageID mID)
	throws BrokerException {

        if (mID == null) {
            throw new NullPointerException();
        }

        return getMessage(dID, mID.toString());
    }

    /**
     * Return the message with the specified message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dID, String mID)
	throws BrokerException {

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            return getMessageInternal(dID, mID);
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public Packet getMessageInternal(DestinationUID dID, String mID)
	throws BrokerException {
        if (mID == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.getMessageInternal("+dID+", "+mID+")");
	}
        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getMessageDAO().getMessage(null, dID, mID);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Store the given list of interests and their states with the
     * specified message.  The message should not have an interest
     * list associated with it yet.
     *
     * @param dID	the destination the message is associated with
     * @param mID	system message id of the message that the interest
     *			is associated with
     * @param iids	an array of interest ids whose states are to be
     *			stored
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not in the store;
     *				if there's an interest list associated with
     *				the message already; or if an error occurs
     *				while persisting the data
     */
    public void storeInterestStates(DestinationUID dID,
	SysMessageID mID, ConsumerUID[] iids, int[] states, boolean sync, Packet msg)
	throws BrokerException {


	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            storeInterestStatesInternal(dID, mID, iids, states, sync, msg);
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public void storeInterestStatesInternal(DestinationUID dID,
	SysMessageID mID, ConsumerUID[] iids, int[] states, boolean sync, Packet msg)
	throws BrokerException {

        if (mID == null || iids == null || states == null) {
            throw new NullPointerException();
        }
        if (iids.length == 0 || iids.length != states.length) {
            throw new BrokerException(br.getKString(BrokerResources.E_BAD_INTEREST_LIST));
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeInterestStatesInternal("+dID+", "+
                mID.getUniqueName()+", "+Arrays.toString(iids)+", "+Arrays.toString(states)+")");
	}

        boolean replaycheck = false;
        Util.RetryStrategy retry = null;
        do {
            try {
                 daoFactory.getConsumerStateDAO().insert(
                     null, dID.toString(), mID, iids, states, true, replaycheck);
                 return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                replaycheck = retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Update the state of the interest associated with the specified
     * message.  If the message does not have an interest list associated
     * with it, the interest list will be created. If the interest is not
     * in the interest list, it will be added to the interest list.
     *
     * @param dID	the destination the message is associated with
     * @param mID	system message id of the message that the interest
     *			is associated with
     * @param iID	id of the interest whose state is to be updated
     * @param state	state of the interest
     * @param sync	if true, will synchronize data to disk
     * @param txid  current transaction id. Unused by jdbc store
     * @param isLastAck Unused by jdbc store
     * @exception BrokerException if the message is not in the store
     */
    public void updateInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID, int state, boolean sync, TransactionUID txid, boolean isLastAck)
    	throws BrokerException {

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            updateInterestStateInternal(dID, mID, iID, state, sync, txid, isLastAck);
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public void updateInterestStateInternal(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID, int state, boolean sync, TransactionUID txid, boolean isLastAck)
    	throws BrokerException {

        if (mID == null || iID == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.updateInterestStateInternal("+dID+", "+ mID.getUniqueName()+
                ", "+iID.toString()+", "+state+", "+sync+", "+txid+", "+isLastAck+")");
	}
        boolean replaycheck = false;
        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getConsumerStateDAO().updateState(null, dID, mID, iID, state, replaycheck);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                replaycheck = retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Update the state of the interest associated with the specified
     * message.  If the message does not have an interest list associated
     * with it, the interest list will be created. If the interest is not
     * in the interest list, it will be added to the interest list.
     *
     * @param dID	the destination the message is associated with
     * @param mID	system message id of the message that the interest
     *			is associated with
     * @param iID	id of the interest whose state is to be updated
     * @param newState	state of the interest
     * @param expectedState the expected state
     * @exception BrokerException if the message is not in the store
     */
    public void updateInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID, int newState, int expectedState)
	throws BrokerException {

        if (mID == null || iID == null) {
            throw new NullPointerException();
        }

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.updateInterestState() called with message: " +
		mID.getUniqueName() + ", consumer: " + iID.toString() +
                ", state=" + newState + ", expected: " + expectedState);
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getConsumerStateDAO().updateState(
                        null, dID, mID, iID, newState, expectedState);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Get the state of the interest associated with the specified message.
     *
     * @param dID	the destination the message is associated with
     * @param mID	system message id of the message that the interest
     *			is associated with
     * @param iID	id of the interest whose state is to be returned
     * @return state of the interest
     * @exception BrokerException if the specified interest is not
     *		associated with the message; or if the message is not in the
     *		store
     */
    public int getInterestState(
	DestinationUID dID, SysMessageID mID, ConsumerUID iID)
    	throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            return getInterestStateInternal(dID, mID, iID);
        } finally {
            setInProgress(false);
        }
    }

    public int getInterestStateInternal(
	DestinationUID dID, SysMessageID mID, ConsumerUID iID)
    	throws BrokerException {

        if (mID == null || dID == null || iID == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.getInterestStateInternal("+dID+", "+mID.getUniqueName()+", "+iID+")");
	}

        Connection conn = null;
        Exception myex = null;
	try {
            conn = dbmgr.getConnection(true);

            Util.RetryStrategy retry = null;
            do {
                try {
                    // check existence of message
                    daoFactory.getMessageDAO().checkMessage(
                        conn, dID.toString(), mID.getUniqueName());
                    return daoFactory.getConsumerStateDAO().getState(conn, mID, iID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
	} finally {
            Util.close(null, null, conn, myex);
	}
    }

    public HashMap getInterestStates(DestinationUID dID, SysMessageID mID)
    	throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            return getInterestStatesInternal(dID, mID);
        } finally {
            setInProgress(false);
        }
    }

    public HashMap getInterestStatesInternal(DestinationUID dID, SysMessageID mID)
    	throws BrokerException {

        if (mID == null || dID == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.getInterestStatesInternal("+dID+", "+mID.getUniqueName()+")");
	}

        Connection conn = null;
        Exception myex = null;
	try {
            conn = dbmgr.getConnection(true);

            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getConsumerStateDAO().getStates(conn, mID);
                } catch ( Exception e ) {
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
	} finally {
            Util.close(null, null, conn, myex);
	}
    }

    /**
     * Retrieve all interest IDs associated with the specified message.
     * Note that the state of the interests returned is either
     * INTEREST_STATE_ROUTED or INTEREST_STATE_DELIVERED, i.e., interest
     * whose state is INTEREST_STATE_ACKNOWLEDGED will not be returned in the
     * array.
     *
     * @param dID	the destination the message is associated with
     * @param mID	system message id of the message whose interests
     *			are to be returned
     * @return an array of ConsumerUID objects associated with the message; a
     *		zero length array will be returned if no interest is
     *		associated with the message
     * @exception BrokerException if the message is not in the store
     */
    public ConsumerUID[] getConsumerUIDs(DestinationUID dID, SysMessageID mID)
	throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            return getConsumerUIDsInternal(dID, mID);
        } finally {
            setInProgress(false);
        }
    }

    public ConsumerUID[] getConsumerUIDsInternal(DestinationUID dID, SysMessageID mID)
	throws BrokerException {

        if (mID == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
            "JDBCStore.getConsumerUIDsInternal(dID="+dID.getLongString()+", mID="+mID.getUniqueName()+")");
	}

        Connection conn = null;
        Exception myex = null;
	try {
            conn = dbmgr.getConnection(true);

            Util.RetryStrategy retry = null;
            do {
                try {
                    // check existence of message
                    daoFactory.getMessageDAO().checkMessage(
                        conn, dID.toString(), mID.getUniqueName());
                    List cuids = daoFactory.getConsumerStateDAO().getConsumerUIDs(conn, mID);
                    return (ConsumerUID[])cuids.toArray( new ConsumerUID[cuids.size()] );
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
	} finally {
            Util.close( null, null, conn, myex );
	}
    }

    /**
     * Store an Interest which is uniquely identified by it's id.
     *
     * @param interest the interest to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the interest
     * @exception BrokerException if an interest with the same id exists in
     *			the store already
     */
    public void storeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

        if (interest == null) {
            throw new NullPointerException();
        }

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeInterest("+
                 interest.getConsumerUID().toString()+", "+sync+")");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getConsumerDAO().insert(null, interest,
                        System.currentTimeMillis());
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Remove the interest from the persistent store.
     *
     * @param interest	the interest to be removed from persistent store
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the interest
     * @exception BrokerException if the interest is not found in the store
     */
    public void removeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

        if (interest == null) {
            throw new NullPointerException();
        }

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.removeInterest() called with interest: " +
		interest.getConsumerUID().toString());
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getConsumerDAO().delete(null, interest);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Retrieve all interests in the store.
     * Will return as many interests as we can read.
     * Any interests that are retrieved unsuccessfully will be logged as a
     * warning.
     *
     * @return an array of Interest objects; a zero length array is
     * returned if no interests exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Consumer[] getAllInterests() throws IOException, BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.getAllInterests() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    List cuids = daoFactory.getConsumerDAO().getAllConsumers(null);
                    return (Consumer[])cuids.toArray( new Consumer[cuids.size()] );
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Store a Destination.
     *
     * @param destination	the destination to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the same destination exists
     *			the store already
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void storeDestination(Destination destination, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            storeDestinationInternal(destination, getStoreSession());
        } finally {
            setInProgress(false);
        }
    }

    protected void storeDestination(Destination destination, long storeSession)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            storeDestinationInternal(destination, storeSession);
        } finally {
            setInProgress(false);
        }
    }

    public void storeDestinationInternal(Destination destination, long storeSessionID)
        throws BrokerException {

        if (destination == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeDestinationInternal("+destination.getUniqueName()+", "+storeSessionID+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getDestinationDAO().insert(null, destination,
                    storeSessionID, 0, System.currentTimeMillis());
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Update the specified destination.
     *
     * @param destination	the destination to be updated
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     *				or if an error occurs while updating the
     *				destination
     */
    public void updateDestination(Destination destination, boolean sync)
	throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            updateDestinationInternal(destination);
	} finally {
	    setInProgress(false);
	}
        
    }

    public void updateDestinationInternal(Destination destination)
	throws BrokerException {

        if (destination == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.updateDestinationInternal("+destination.getUniqueName()+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getDestinationDAO().update(null, destination);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Remove the destination from the persistent store.
     *
     * @param destination	the destination to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     */
    public void removeDestination(Destination destination, boolean sync)
	throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            removeDestinationInternal(destination, null);
	} finally {
	    setInProgress(false);
	}
    }

    public void removeDestinationInternal(Destination destination, Long storeSessionID)
	throws BrokerException {

        if (destination == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
            "JDBCStore.removeDestinationInternal("+destination.getUniqueName()+", "+storeSessionID+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                boolean isDeleted =
                    daoFactory.getDestinationDAO().delete(null, destination, storeSessionID);
                if (destination.isAutoCreated() && Globals.getHAEnabled() && 
                    storeSessionID == null) {
                    DestinationUID dstID = destination.getDestinationUID();
                    synchronized(pendingDeleteDsts) {
                        if (isDeleted) {
                            pendingDeleteDsts.remove(dstID);
                        } else {
                            pendingDeleteDsts.put(dstID,
                                Integer.valueOf(destination.getType()));
                        }
                    }
                }
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Reap auto-created destinations.
     *
     * Only applicable for broker running in HA mode and will should be invoked
     * when a broker is removed from the cluster.
     *
     * @exception BrokerException
     */
    public void reapAutoCreatedDestinations()
	throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.reapAutoCreatedDestinations() called" );
	}

        if (!Globals.getHAEnabled()) {
            return; // No-op
        }

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    synchronized (pendingDeleteDsts) {
                        if (!pendingDeleteDsts.isEmpty()) {
                            // Iterate over the auto-created dst pending removal
                            // list and see if any can be removed from the DB
                            DestinationDAO dao = daoFactory.getDestinationDAO();
                            Iterator itr = pendingDeleteDsts.entrySet().iterator();
                            while (itr.hasNext()) {
                                Map.Entry e = (Map.Entry)itr.next();
                                DestinationUID dst = (DestinationUID)e.getKey();
                                int type = ((Integer)e.getValue()).intValue();
                                if (dao.delete(null, dst, type)) {
                                    if (DEBUG) {
                                        logger.log(Logger.INFO,
                                        "Auto-created destination " + dst +
                                        " has been removed from HA Store");
                                    }

                                    // Remove from pending list if it is reaped
                                    itr.remove();
                                }
                            }
                        }
                    }
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Retrieve a destination in the store.
     *
     * @param id the destination ID
     * @return a Destination object
     * @throws BrokerException if no destination exist in the store
     */
    public Destination getDestination(DestinationUID id) throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            return getDestinationInternal(id);
	} finally {
	    setInProgress(false);
	}
    }

    public Destination getDestinationInternal(DestinationUID id) throws BrokerException {

        if (id == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.getDestinationInternal("+id+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getDestinationDAO().getDestination(null, id.toString());
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Retrieve all destinations in the store.
     *
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Destination[] getAllDestinations() throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            return getAllDestinationsInternal(dbmgr.getBrokerID(), null);
	} finally {
	    setInProgress(false);
	}
    }

    /**
     * Retrieve all destinations in the store for the specified broker ID.
     *
     * @param brokerID the broker ID
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    protected Destination[] getAllDestinations(String brokerID)
	throws BrokerException {

	checkClosedAndSetInProgress();
        try {
            return getAllDestinationsInternal(brokerID, null);
	} finally {
	    setInProgress(false);
	}
    }

    public Destination[] getAllDestinationsInternal(Long storeSessionID)
	throws BrokerException {
        return getAllDestinationsInternal(dbmgr.getBrokerID(), storeSessionID);
    }

    /**
     * currently storeSessionID, added for partitioned store, is ignored  
     */
    public Destination[] getAllDestinationsInternal(String brokerID, Long storeSessionID)
	throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO, 
            "JDBCStore.getAllDestinationsInternal("+brokerID+", "+storeSessionID+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                List dests = daoFactory.getDestinationDAO().
                             getAllDestinations(null, brokerID);
                return (Destination[])dests.toArray(
                           new Destination[dests.size()]);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Store a transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param ts	the transaction state to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the same transaction id exists
     *			the store already
     * @exception NullPointerException	if <code>id</code> is
     *			<code>null</code>
     */
    public void storeTransaction(TransactionUID id, TransactionState ts,
        boolean sync) throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            storeTransactionInternal(id, ts, sync, getStoreSession());
	} finally {
	    setInProgress(false);
	}
    }

    public void storeTransactionInternal(TransactionUID id, TransactionState ts,
        boolean sync, long storeSession) throws BrokerException {

        if (id == null || ts == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeTransactionInternal("+
                 id+", "+ts+", "+sync+", "+storeSession+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getTransactionDAO().insert(
                           null, id, ts, storeSession);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Remove the transaction from the persistent store.
     *
     * @param id	the id of the transaction to be removed
     * @param removeAcks if true, will remove all associated acknowledgements
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction is not found
     *			in the store
     */
    public void removeTransaction(TransactionUID id, boolean removeAcks,
        boolean sync) throws BrokerException {

	checkClosedAndSetInProgress();
        try { 
            removeTransactionInternal(id, removeAcks, sync);
        } finally {
            setInProgress(false);
        }
    }

    public void removeTransactionInternal(TransactionUID id, boolean removeAcks,
        boolean sync) throws BrokerException {

        if (id == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.removeTransactionInternal("+id+", "+removeAcks+", "+sync+")");
	}

        Connection conn = null;
        Exception myex = null;
	try {
            conn = dbmgr.getConnection(false);

            Util.RetryStrategy retry = null;
            do {
                try {
                    // First, remove the acks
                    if (removeAcks) {
                        daoFactory.getConsumerStateDAO().clearTransaction(conn, id);
                    }

                    // Now, remove the txn
                    daoFactory.getTransactionDAO().delete(conn, id);

                    conn.commit();

                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    try {
                        retry.assertShouldRetry( e, conn );
                    } catch (RetrySQLRecoverableException ee) {
                        try {
                            Util.close(null, null, conn, ee);
                            conn = dbmgr.getConnection(false);
                        } catch (Exception eee) { 
                            logger.logStack(Logger.WARNING, eee.getMessage(), eee);
                            conn = null;
                            if (e instanceof BrokerException) {
                                throw (BrokerException)e; 
                            }
                            throw new BrokerException(e.getMessage(), e);
                        }
                    }
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
	} finally {
            Util.close(null, null, conn, myex);
	}
    }

    /**
     * Update the state of a transaction
     *
     * @param id	the transaction id to be updated
     * @param ts	the new transaction state
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     * @exception NullPointerException	if <code>id</code> is
     *			<code>null</code>
     */
    public void updateTransactionState(TransactionUID id, TransactionState ts,
        boolean sync) throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            updateTransactionStateInternal(id, ts, sync);
	} finally {
	    setInProgress(false);
	}
    }

    public void updateTransactionStateInternal(TransactionUID id, TransactionState ts,
        boolean sync) throws BrokerException {

        if (id == null || ts == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
            logger.log(Logger.INFO, 
                "JDBCStore.updateTransactionStateInternal("+id+", "+ts+", "+sync+")");
	}

        boolean replaycheck = false;
        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getTransactionDAO().updateTransactionState(null, id, ts, replaycheck);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                replaycheck = retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public void updateTransactionStateWithWork(TransactionUID id, 
        TransactionState ts, TransactionWork txnwork, boolean sync)
        throws BrokerException {
        if (partitionMode) {
            String emsg = br.getKString(br.E_INTERNAL_BROKER_ERROR,
                "JDBCStore.updateTransactionStateWithWork(): Unexpected call in partition-mode");
            BrokerException ex = new BrokerException(emsg);
            logger.logStack(logger.ERROR, emsg, ex);
            throw ex;
        }

        checkClosedAndSetInProgress();
        try {
            updateTransactionStateWithWorkInternal(id, ts, txnwork, getStoreSession(), sync);
        } finally {
            setInProgress(false);
        }
    }

    public void updateTransactionStateWithWorkInternal(TransactionUID tid, 
        TransactionState ts, TransactionWork txnwork, long storeSessionID, boolean sync)
        throws BrokerException {

        if (tid == null || ts == null || txnwork == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO,
            "JDBCStore.updateTransactionStateInternal("+tid+", "+ts+", "+", "+txnwork+", "+sync+")");
        }

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(false);
            Util.RetryStrategy retry = null;
            do {
                boolean inside = false;
                try {
                    Iterator<TransactionWorkMessage> itr1 = txnwork.getSentMessages().iterator();
                    while (itr1.hasNext()) {
                        TransactionWorkMessage txnmsg = itr1.next();
                        Packet m = txnmsg.getMessage();
                        if (m == null) {
                            continue;
                        }
                        inside = true;
                        daoFactory.getMessageDAO().insert(conn, txnmsg.getDestUID(), 
                            m, null, null, storeSessionID, m.getTimestamp(), true, false);
                        inside = false;
                    }
                    if (FI.FAULT_INJECTION) {
                        try {
                            FI.checkFaultAndThrowBrokerException(
                                FaultInjection.FAULT_TXN_PERSIST_WORK_1_5, null);
                        } catch (BrokerException e) {
                            FI.unsetFault(FI.FAULT_TXN_PERSIST_WORK_1_5);
                            throw e;
                        }
                    }

                    List<TransactionWorkMessageAck> txnacks = txnwork.getMessageAcknowledgments();
                    if (txnacks != null) {
                        Iterator<TransactionWorkMessageAck> itr2 = txnacks.iterator();
                        while (itr2.hasNext()) {
                            TransactionWorkMessageAck txnack = itr2.next();
                            TransactionAcknowledgement ta = txnack.getTransactionAcknowledgement();
                            if (ta != null) {
                                inside = true;
                                daoFactory.getConsumerStateDAO().updateTransaction(conn,
                                    ta.getSysMessageID(), ta.getStoredConsumerUID(), tid);
                                inside = false;
                            }
                        }
                    }
                    inside = true;
                    daoFactory.getTransactionDAO().updateTransactionState(conn, tid, ts, false);
                    inside = false;
                    conn.commit();
                    return;
                } catch ( Exception e ) {
                    if (!inside) {
                        try {
                            conn.rollback();
                        } catch ( SQLException rbe ) {
                            logger.logStack(Logger.WARNING, 
                            BrokerResources.X_DB_ROLLBACK_FAILED, rbe);
                        }
                    }

                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    try {
                        retry.assertShouldRetry( e, conn );
                    } catch (RetrySQLRecoverableException ee) {
                        try {
                            Util.close(null, null, conn, ee);
                            conn = dbmgr.getConnection(false);
                        } catch (Exception eee) {
                            logger.logStack(Logger.WARNING, eee.getMessage(), eee);
                            conn = null;
                            if (e instanceof BrokerException) {
                                throw (BrokerException)e;
                            }
                            throw new BrokerException(e.getMessage(), e);
                        }
                    }
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close(null, null, conn, myex);
        }

    }

    /**
     * Retrieve all transaction ids in the store with their state
     *
     * @return a HashMap. The key is a TransactionUID.
     * The value is a TransactionState.
     * @exception IOException if an error occurs while getting the data
     */
    public HashMap getAllTransactionStates()
	throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            return getAllTransactionStatesInternal(null);
	} finally {
	    setInProgress(false);
	}
    }

    public HashMap getAllTransactionStatesInternal(Long storeSession)
	throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.getAllTransactionStatesInternal("+storeSession+")");
	}
        String brokerID = dbmgr.getBrokerID();

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionStatesByBroker(
                    null, brokerID, storeSession);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public HashMap getAllRemoteTransactionStates()
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getAllRemoteTransactionStatesInternal(null);
        } finally {
            setInProgress(false);
        }
        
    }

    public HashMap getAllRemoteTransactionStatesInternal(Long storeSession)
        throws BrokerException {

        if (DEBUG) {
            logger.log(Logger.INFO, 
                "JDBCStore.getAllRemoteTransactionStatesInternal("+storeSession+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getRemoteTransactionStatesByBroker(
                    null, dbmgr.getBrokerID(), storeSession);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Store the acknowledgement for the specified transaction.
     *
     * @param tid	the transaction id with which the acknowledgment is to
     *			be stored
     * @param ack	the acknowledgement to be stored
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction id is not found in the
     *				store, if the acknowledgement already
     *				exists, or if it failed to persist the data
     */
    public void storeTransactionAck(TransactionUID tid,
	TransactionAcknowledgement ack, boolean sync) throws BrokerException {
	checkClosedAndSetInProgress();
	try {
            storeTransactionAckInternal(tid, ack, sync);
	} finally {
	    setInProgress(false);
	}
    }

    public void storeTransactionAckInternal(TransactionUID tid,
	TransactionAcknowledgement ack, boolean sync) throws BrokerException {

        if (tid == null || ack == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeTransactionAckInternal("+tid+", "+ack+", "+sync+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getConsumerStateDAO().updateTransaction(null,
                    ack.getSysMessageID(), ack.getStoredConsumerUID(), tid);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Remove all acknowledgements associated with the specified
     * transaction from the persistent store.
     *
     * From 4.0 onward, acknowledgement is implemented as a TRANSACTION_ID
     * column on the message and consumer state table so to remove the acks
     * is the same as clear txnID from the column.
     *
     * @param tid	the transaction id whose acknowledgements are
     *			to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if error occurs while removing the
     *			acknowledgements
     */
    public void removeTransactionAck(TransactionUID tid, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            removeTransactionAckInternal(tid, sync);
        } finally {
            setInProgress(false);
        }
    }

    public void removeTransactionAckInternal(TransactionUID tid, boolean sync)
	throws BrokerException {

        if (tid == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO,
                "JDBCStore.removeTransactionAckInternal("+tid+", "+sync+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getConsumerStateDAO().clearTransaction(null, tid);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Retrieve all acknowledgement list in the persistence store together
     * with their associated transaction id. The data is returned in the
     * form a HashMap. Each entry in the HashMap has the transaction id as
     * the key and an array of the associated TransactionAcknowledgement
     * objects as the value.
     * @return a HashMap object containing all acknowledgement lists in the
     *		persistence store
     */
    public HashMap getAllTransactionAcks() throws BrokerException {
	checkClosedAndSetInProgress();
	try {
            return getAllTransactionAcksInternal(null);
	} finally {
	    setInProgress(false);
	}
    }

    public HashMap getAllTransactionAcksInternal(Long storeSession)
        throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.getAllTransactionAcksInternal("+storeSession+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getConsumerStateDAO().getAllTransactionAcks(null);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    /**
     * Retrieve all acknowledgements for the specified transaction.
     *
     * @param id	id of the transaction whose acknowledgements
     *			are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionAcknowledgement[] getTransactionAcks(
	TransactionUID id) throws BrokerException {

	checkClosedAndSetInProgress();
	try {
            return getTransactionAcksInternal(id);
	} finally {
	    setInProgress(false);
	}
    }

    public TransactionAcknowledgement[] getTransactionAcksInternal(TransactionUID id)
        throws BrokerException {

        if (id == null) {
            throw new NullPointerException();
        }
	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.getTransactionAcksInternal("+id+")");
	}

        Util.RetryStrategy retry = null;
        do {
            try {
                List acks = daoFactory.getConsumerStateDAO().
                            getTransactionAcks(null, id);
                return (TransactionAcknowledgement[])acks.toArray(
                           new TransactionAcknowledgement[acks.size()]);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public void storeTransaction(TransactionUID tid, TransactionInfo txnInfo,
        boolean sync) throws BrokerException {

        storeTransaction(tid, txnInfo, getStoreSession());
    }

    protected void storeTransaction(TransactionUID tid, TransactionInfo txnInfo,
        long storeSessionID) throws BrokerException {

        if (tid == null || txnInfo == null) {
            throw new NullPointerException();
        }

        int type = TransactionInfo.TXN_NOFLAG;
        BrokerAddress txnHomeBroker = null;
        TransactionBroker[] txnBrokers = null;
        if (txnInfo.isCluster()) {
            type = TransactionInfo.TXN_CLUSTER;
            txnBrokers = txnInfo.getTransactionBrokers();
        } else if (txnInfo.isRemote()) {
            type = TransactionInfo.TXN_REMOTE;
            txnHomeBroker = txnInfo.getTransactionHomeBroker();
        } else if (txnInfo.isLocal()) {
            type = TransactionInfo.TXN_LOCAL;
        } else {
            String errorMsg = "Illegal transaction type: " + txnInfo.getType();
            logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                errorMsg );
            throw new BrokerException(
                br.getKString( BrokerResources.E_INTERNAL_BROKER_ERROR, errorMsg ) );
        }

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getTransactionDAO().insert(null, tid,
                        txnInfo.getTransactionState(), txnHomeBroker,
                        txnBrokers, type, storeSessionID);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public void storeClusterTransaction(TransactionUID tid, TransactionState ts,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            storeClusterTransactionInternal(tid, ts, txnBrokers, sync, getStoreSession());
        } finally {
            setInProgress(false);
        }
        
    }

    public void storeClusterTransactionInternal(TransactionUID tid, TransactionState ts,
        TransactionBroker[] txnBrokers, boolean sync, long storeSession)
        throws BrokerException {

        if (tid == null || ts == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO, "JDBCStore.storeClusterTransactionInternal("+
                tid+", "+ts+", "+Arrays.toString(txnBrokers)+", "+sync+", "+storeSession+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getTransactionDAO().insert(null, tid, ts, null,
                           txnBrokers, TransactionInfo.TXN_CLUSTER, storeSession);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public void updateClusterTransaction(TransactionUID tid,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            updateClusterTransactionInternal(tid, txnBrokers, sync);
        } finally {
            setInProgress(false);
        }
    }

    public void updateClusterTransactionInternal(TransactionUID tid,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        if (tid == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO,
                "JDBCStore.updateClusterTransactionInternal("+
                 tid+", "+Arrays.toString(txnBrokers)+", "+sync+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getTransactionDAO().updateTransactionBrokers(null, tid, txnBrokers);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public void updateClusterTransactionBrokerState(TransactionUID tid,
        int expectedTxnState, TransactionBroker txnBroker, boolean sync)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            updateClusterTransactionBrokerStateInternal(tid, expectedTxnState, txnBroker, sync);
        } finally {
            setInProgress(false);
        }
         
    }
    public void updateClusterTransactionBrokerStateInternal(TransactionUID tid,
        int expectedTxnState, TransactionBroker txnBroker, boolean sync)
        throws BrokerException {

        if (tid == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO,
                "JDBCStore.updateClusterTransactionBrokerStateInternal("+
                 tid+", "+expectedTxnState+", "+txnBroker+", "+sync+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                daoFactory.getTransactionDAO().updateTransactionBrokerState(
                    null, tid, expectedTxnState, txnBroker);
                return;
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public void updateRemoteTransaction(TransactionUID tid,
        TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            updateRemoteTransactionInternal(tid, txnAcks, txnHomeBroker, sync);
        } finally {
            setInProgress(false);
        }
    }

    public void updateRemoteTransactionInternal(TransactionUID tid,
        TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException {

        if (tid == null) {
            throw new NullPointerException();
        }
        if (DEBUG) {
            logger.log(Logger.INFO,
                "JDBCStore.updateRemoteTransactionInternal("+
                 tid+", "+Arrays.toString(txnAcks)+", "+txnHomeBroker+", "+sync+")");
        }
        if (!Globals.getHAEnabled()) {
            throw new UnsupportedOperationException(
                "Operation not supported by the " + getStoreType() +
                " store in non-HA mode" );
        }

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(false);
            Util.RetryStrategy retry = null;
            do {
                try {
                    //store the acks if any
                    if (txnAcks != null && txnAcks.length > 0) {
                        for (int i = 0, len = txnAcks.length; i < len; i++) {
                            TransactionAcknowledgement ack = txnAcks[i];
                            if (ack.shouldStore()) {
                            daoFactory.getConsumerStateDAO().updateTransaction(
                                conn, ack.getSysMessageID(),
                                ack.getStoredConsumerUID(),
                                tid);
                            }
                        }
                    }

                    // In HA mode, the txn is owned by another broker
                    // so we'll only update the txn home broker
                    daoFactory.getTransactionDAO().updateTransactionHomeBroker(
                        conn, tid, txnHomeBroker);
                    conn.commit();
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close(null, null, conn, myex);
        }
    }

    public void storeRemoteTransaction(TransactionUID tid, TransactionState ts,
        TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
        boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            storeRemoteTransactionInternal(tid, ts, txnAcks, 
                txnHomeBroker, sync, getStoreSession());
        } finally {
            setInProgress(false);
        }
    }

    public void storeRemoteTransactionInternal(TransactionUID tid, TransactionState ts,
        TransactionAcknowledgement[] txnAcks, BrokerAddress txnHomeBroker,
        boolean sync, long storeSession) throws BrokerException {

        if (tid == null) {
            throw new NullPointerException();
        }
        if (Globals.getHAEnabled()) {
            throw new UnsupportedOperationException(
                "Operation not supported by the "+getStoreType()+" store in HA mode" );
        }

        if (DEBUG) {
            logger.log(Logger.INFO,
                "JDBCStore.storeRemoteTransactionInternal("+
                 tid+", "+ts+", "+Arrays.toString(txnAcks)+
                ", "+txnHomeBroker+", "+sync+")");
        }

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection(false);

            Util.RetryStrategy retry = null;
            do {
                try {
                    // First, store the txn
                    daoFactory.getTransactionDAO().insert(
                        conn, tid, ts, txnHomeBroker, null,
                        TransactionInfo.TXN_REMOTE, storeSession);

                    // Now, store the acks if any
                    if (txnAcks != null && txnAcks.length > 0) {
                        for (int i = 0, len = txnAcks.length; i < len; i++) {
                            TransactionAcknowledgement ack = txnAcks[i];
                            if (ack.shouldStore()) {
                            daoFactory.getConsumerStateDAO().updateTransaction(
                                conn, ack.getSysMessageID(),
                                ack.getStoredConsumerUID(),
                                tid);
                            }
                        }
                    }

                    conn.commit();

                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close(null, null, conn, myex);
        }
    }

    /**
     * Persist the specified property name/value pair.
     * If the property identified by name exists in the store already,
     * it's value will be updated with the new value.
     * If value is null, the property will be removed.
     * The value object needs to be serializable.
     *
     * @param name name of the property
     * @param value value of the property
     * @param sync if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting the data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    public void updateProperty(String name, Object value, boolean sync)
	throws BrokerException {

        if (name == null) {
            throw new NullPointerException();
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.updateProperty() called with name: " + name);
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getPropertyDAO().update(null, name, value);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

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
    public Object getProperty(String name) throws BrokerException {

        if (name == null) {
            throw new NullPointerException();
        }

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.getProperty() called with name: " + name);
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getPropertyDAO().getProperty( null, name);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Return the names of all persisted properties.
     *
     * @return an array of property names; an empty array will be returned
     *		if no property exists in the store.
     */
    public String[] getPropertyNames() throws BrokerException {

	if (Store.getDEBUG()) {
	    logger.log(Logger.INFO, "JDBCStore.getPropertyNames() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    List pnames = daoFactory.getPropertyDAO().getPropertyNames(null);
                    return (String[])pnames.toArray( new String[pnames.size()] );
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Return all persisted properties.
     *
     * @return a properties object.
     */
    public Properties getAllProperties() throws BrokerException {

        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, "JDBCStore.getAllProperties() called");
        }

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getPropertyDAO().getProperties(null);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    /**
     * Append a new record to the config change record store.
     * The timestamp is also persisted with the recordData.
     * The config change record store is an ordered list (sorted
     * by timestamp).
     *
     * @param createdTime The time when this record was created.
     * @param recordData The record data.
     * @param sync if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while persisting
     *			the data or if the timestamp is less than 0
     * @exception NullPointerException if <code>recordData</code> is
     *			<code>null</code>
     */
    public void storeConfigChangeRecord(long createdTime, byte[] recordData,
        boolean sync) throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.storeConfigChangeRecord() called");
	}

        if (createdTime <= 0) {
            String ts = String.valueOf(createdTime);
            logger.log(Logger.ERROR, BrokerResources.E_INVALID_TIMESTAMP, ts);
            throw new BrokerException(
                br.getKString(BrokerResources.E_INVALID_TIMESTAMP, ts));
        }

        if (recordData == null) {
            throw new NullPointerException();
        }

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getConfigRecordDAO().insert(null, recordData, createdTime);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Get all the config change records since the given timestamp.
     * Retrieves all the entries with recorded timestamp greater than
     * the specified timestamp.
     * 
     * @return a list of ChangeRecordInfo, empty list if no record
     */
    public ArrayList<ChangeRecordInfo> getConfigChangeRecordsSince(long timeStamp)
	throws BrokerException {

	if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.getConfigChangeRecordsSince() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return (ArrayList)daoFactory.getConfigRecordDAO().getRecordsSince(
                        null, timeStamp);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Return all config records with their corresponding timestamps.
     *
     * @return a list of ChangeRecordInfo
     * @exception BrokerException if an error occurs while getting the data
     */
    public List<ChangeRecordInfo> getAllConfigRecords() throws BrokerException {

        if (Store.getDEBUG()) {
	    logger.log(Logger.INFO,
                "JDBCStore.getAllConfigRecords() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getConfigRecordDAO().getAllRecords(null);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    /**
     * Clear all config change records in the store.
     *
     * @param sync if true, will synchronize data to disk
     * @exception BrokerException if an error occurs while clearing the data
     */
    public void clearAllConfigChangeRecords(boolean sync)
	throws BrokerException {

        if (DEBUG) {
	    logger.log(Logger.INFO,
                "JDBCStore.clearAllConfigChangeRecords() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

	try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getConfigRecordDAO().deleteAll(null);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
	} finally {
	    // decrement in progress count
	    setInProgress(false);
	}
    }

    public void clearAll(boolean sync) throws BrokerException {

        if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.clearAll() called");
	}

	// make sure store is not closed then increment in progress count
	checkClosedAndSetInProgress();

        Connection conn = null;
        Exception myex = null;
	try {
            conn = dbmgr.getConnection(false);

            Util.RetryStrategy retry = null;
            do {
                try {
                    if (Globals.getHAEnabled()) {
                        // In HA mode, only reset txns, dsts, states, and msgs
                        // in the specified order
                        daoFactory.getTransactionDAO().deleteAll(conn);
                        daoFactory.getDestinationDAO().deleteAll(conn);
                        daoFactory.getConsumerStateDAO().deleteAll(conn);
                        daoFactory.getMessageDAO().deleteAll(conn);
                        daoFactory.getTMLogRecordDAOJMSBG().deleteAll(conn);
                        daoFactory.getJMSBGDAO().deleteAll(conn);
                    } else {
                        List daos = daoFactory.getAllDAOs();
                        Iterator itr = daos.iterator();
                        while (itr.hasNext()) {
                            BaseDAO dao = (BaseDAO)itr.next();
                            if ( !(dao instanceof VersionDAO ||
                                   dao instanceof BrokerDAO ||
                                   dao instanceof StoreSessionDAO) ) {
                                dao.deleteAll(conn);
                            }
                        }
                    }
                    conn.commit();
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } catch ( Exception e ) {
            myex = e;
            throw new BrokerException(
                br.getKString( BrokerResources.X_CLEAR_ALL_FAILED ), e );
	} finally {
            try {
                Util.close(null, null, conn, myex);
            } finally {
                // decrement in progress count
                setInProgress(false);
            }
	}
    }

    public void close(boolean cleanup) {

	// make sure all operations are done before we proceed to close
	setClosedAndWait();
        
        if (partitionMode) {
            PartitionedStore pstore = null;
            synchronized(partitionStores) {
                Iterator<PartitionedStore> itr = partitionStores.values().iterator();
                while (itr.hasNext()) {
                    pstore = itr.next();
                    pstore.close();
                }
            } 
        }

	// true = unlock the tables
	closeDB(true);

       dbmgr.setStoreInited(false);

	if (DEBUG) {
	    logger.log(Logger.INFO, "JDBCStore.close("+ cleanup +") done.");
	}
    }

    protected void beforeWaitOnClose() {

        Iterator<Enumeration> itr = null;
        synchronized(dataEnums) {
            itr = dataEnums.iterator();
            Enumeration en = null;
            while (itr.hasNext()) {
                en = itr.next();
                if (en instanceof MessageEnumeration) {
                    ((MessageEnumeration)en).cancel();
                }
            }
        }
        if (dbmgr != null) {
            dbmgr.setIsClosing();
        }
 
    }

    // HA operations

    public long getBrokerHeartbeat(String brokerID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getHeartbeat(null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public HashMap getAllBrokerHeartbeats() throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getAllHeartbeats(null);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public Long updateBrokerHeartbeat(String brokerID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().updateHeartbeat(null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        // Override default so total retry time is 30 secs
                        retry = new Util.RetryStrategy(dbmgr,
                            DBManager.TRANSACTION_RETRY_DELAY_DEFAULT, 4, true);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public Long updateBrokerHeartbeat(String brokerID, long lastHeartbeat)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().updateHeartbeat(null,
                        brokerID, lastHeartbeat);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        // Override default so total retry time is 30 secs
                        retry = new Util.RetryStrategy(dbmgr,
                            DBManager.TRANSACTION_RETRY_DELAY_DEFAULT, 4, false, true);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public void addBrokerInfo(String brokerID, String URL, BrokerState state,
        int version, long sessionID, long heartbeat) throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        List<UID> additionalSessions = null;
        if (partitionMode) {
            additionalSessions = new ArrayList<UID>();
            for (int i = 0; i < initialNumPartitions-1; i++) {
                additionalSessions.add(new UID());
            }
        }

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getBrokerDAO().insert(null, brokerID, null, URL,
                        version, state.intValue(), Long.valueOf(sessionID), 
                        heartbeat, additionalSessions);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }


    public void addBrokerInfo(HABrokerInfo bkrInfo, boolean sync) throws BrokerException {
        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getBrokerDAO().insert(null, bkrInfo.getId(),
                        bkrInfo.getTakeoverBrokerID(), bkrInfo.getUrl(),
                        bkrInfo.getVersion(), bkrInfo.getState(),
                        null, bkrInfo.getHeartbeat(), null);

                    List sessions = bkrInfo.getAllSessions();
                    if (sessions != null && !sessions.isEmpty()) {
                        StoreSessionDAO dao = daoFactory.getStoreSessionDAO();
                        Iterator itr = sessions.iterator();
                        while (itr.hasNext()) {
                            HABrokerInfo.StoreSession ses =
                                (HABrokerInfo.StoreSession)itr.next();
                            dao.insert(null, ses.getBrokerID(), ses.getID(),
                                ses.getIsCurrent(), ses.getCreatedBy(),
                                ses.getCreatedTS());
                        }
                    }
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }


    public UID updateBrokerInfo( String brokerID, int updateType,
                                 Object oldValue, Object newValue )
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().update(null, brokerID, 
                                                     updateType, oldValue, newValue);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public HABrokerInfo getBrokerInfo(String brokerID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getBrokerInfo(null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public HashMap getAllBrokerInfos(boolean loadSession)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getAllBrokerInfos(null, loadSession);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public HashMap getAllBrokerInfoByState(BrokerState state)
        throws BrokerException {

        if (state == null) {
            throw new NullPointerException();
        }

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getAllBrokerInfosByState(null, state);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public String getStoreSessionOwner( long sessionID )
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getStoreSessionDAO().getStoreSessionOwner(null, sessionID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public boolean ifOwnStoreSession( long sessionID, String brokerID )
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getStoreSessionDAO().ifOwnStoreSession(null, sessionID, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public String getStoreSessionCreator( long sessionID )
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getStoreSessionDAO().getStoreSessionCreator(null, sessionID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public boolean updateBrokerState(String brokerID, BrokerState newState,
        BrokerState expectedState, boolean local) throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().updateState( null, brokerID,
                        newState, expectedState, local );
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        // Override default so total retry time is about 1 min
                        retry = new Util.RetryStrategy(dbmgr,
                            DBManager.TRANSACTION_RETRY_DELAY_DEFAULT, 5, false, true);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public BrokerState getBrokerState(String brokerID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getState(null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy(dbmgr,
                            DBManager.TRANSACTION_RETRY_DELAY_DEFAULT, 5, true);
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public Object[] getAllBrokerStates() throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getBrokerDAO().getAllStates(null);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public void getTakeOverLock(String brokerID, String targetBrokerID,
        long lastHeartbeat, BrokerState expectedState,
        long newHeartbeat, BrokerState newState, boolean force,
        TakingoverTracker tracker)
        throws TakeoverLockException, BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        tracker.setStage_BEFORE_GET_LOCK();

        Connection conn = null;
        Exception myex = null;
        try {
            synchronized ( takeoverLockMap ) {
                // Verify if a lock has been acquired for the target broker
                TakeoverStoreInfo takeoverInfo =
                    (TakeoverStoreInfo)takeoverLockMap.get( targetBrokerID );
                if (takeoverInfo != null) {
                    logger.logToAll( Logger.WARNING,
                        BrokerResources.W_UNABLE_TO_ACQUIRE_TAKEOVER_LOCK, targetBrokerID );
                    return;
                }

                conn = dbmgr.getConnection( true );

                Util.RetryStrategy retry = null;
                do {
                    try {
                        // Try to obtain the lock by updating the target broker
                        // entry in the broker table; an exception is thrown if we
                        // are unable to get the lock.
                        HABrokerInfo savedInfo =
                            daoFactory.getBrokerDAO().takeover(
                                conn, brokerID, targetBrokerID, lastHeartbeat,
                                expectedState, newHeartbeat, newState );
                        savedInfo.setTakeoverTimestamp(newHeartbeat);

                        tracker.setStage_AFTER_GET_LOCK();

                        long timestamp = System.currentTimeMillis();

                        logger.logToAll( Logger.INFO,
                            BrokerResources.I_TAKEOVER_LOCK_ACQUIRED,
                            targetBrokerID, String.valueOf(timestamp) );

                        takeoverInfo = new TakeoverStoreInfo(
                            targetBrokerID, savedInfo, timestamp );

                        // Save the broker's state
                        takeoverLockMap.put( targetBrokerID, takeoverInfo );

                        // Now, get the all the msgs and corresponding dst IDs
                        // that we'll be taking over to track and ensure that
                        // they can't be accessed while we're taking over!
                        Map msgMap =
                            daoFactory.getMessageDAO().getMsgIDsAndDstIDsByBroker(
                                conn, targetBrokerID );

                        tracker.setMessageMap( msgMap );

                        return;
                    } catch ( Exception e ) {
                        // Exception will be log & re-throw if operation cannot be retry
                        if ( retry == null ) {
                            retry = new Util.RetryStrategy();
                        }
                        retry.assertShouldRetry( e );
                    }
                } while ( true );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            try {
                Util.close( null, null, conn, myex );
            } finally  {
                // decrement in progress count
                setInProgress(false);
            }
        }
    }

    public TakeoverStoreInfo takeOverBrokerStore(String brokerID,
        String targetBrokerID, TakingoverTracker tracker) 
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        tracker.setStage_BEFORE_TAKE_STORE();

        partitionLock.lock();

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection( false );

            BrokerDAO brokerDAO = daoFactory.getBrokerDAO();
            HABrokerInfo bkrInfo = null;

            Util.RetryStrategy retry = null;
            do {    // JDBC Retry loop
                try {
                    bkrInfo = brokerDAO.getBrokerInfo( conn, targetBrokerID );
                    break;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );

            if ( bkrInfo == null ) {
                String errorMsg = br.getKString(
                    BrokerResources.E_BROKERINFO_NOT_FOUND_IN_STORE, targetBrokerID );
                logger.log( Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                    errorMsg );
                throw new BrokerException(
                    br.getKString( BrokerResources.E_INTERNAL_BROKER_ERROR, errorMsg ) );
            }

            // Verify a takeover lock has been acquired for the target broker.
            TakeoverStoreInfo takeoverInfo = null;
            synchronized ( takeoverLockMap ) {
                takeoverInfo = (TakeoverStoreInfo)takeoverLockMap.get( targetBrokerID );
            }

            if ( takeoverInfo == null ||
                 !brokerID.equals( bkrInfo.getTakeoverBrokerID() ) ) {
                // Cannot takeover a store without 1st obtaining the lock
                logger.log( Logger.ERROR, BrokerResources.E_TAKEOVER_WITHOUT_LOCK,
                    targetBrokerID );
                throw new BrokerException(
                    br.getKString( BrokerResources.E_TAKEOVER_WITHOUT_LOCK, targetBrokerID ) );
            }
            tracker.setStoreSession(bkrInfo.getSessionID());

            // Start takeover process...

            try {
                retry = null;
                do {    // JDBC Retry loop
                    try {
                        // Get local destinations of target broker
                        DestinationDAO dstDAO = daoFactory.getDestinationDAO();
                        List dstList = dstDAO.getLocalDestinationsByBroker( conn, targetBrokerID );
                        takeoverInfo.setDestinationList( dstList );
                        String args[] = { String.valueOf(dstList.size()),
                            targetBrokerID, dstList.toString() };
                        logger.log( Logger.INFO, br.getString(
                            BrokerResources.I_TAKINGOVER_LOCAL_DSTS, args) );

                        // Get messages of target broker
                        MessageDAO msgDAO = daoFactory.getMessageDAO();
                        Map<String, String> msgMap = msgDAO.
                            getMsgIDsAndDstIDsByBroker( conn, targetBrokerID );
                        takeoverInfo.setMessageMap( msgMap );
                        logger.log( Logger.INFO, br.getString(
                            BrokerResources.I_TAKINGOVER_MSGS,
                            msgMap.size(), targetBrokerID) );

                        // Get transactions of target broker
                        TransactionDAO txnDAO = daoFactory.getTransactionDAO();
                        List txnList = txnDAO.getTransactionsByBroker( conn, targetBrokerID );
                        takeoverInfo.setTransactionList( txnList );
                        logger.log( Logger.INFO, br.getString(
                            BrokerResources.I_TAKINGOVER_TXNS,
                            txnList.size(), targetBrokerID) );

                        // Get remote transactions of target broker
                        List remoteTxnList = txnDAO.getRemoteTransactionsByBroker( conn, targetBrokerID );
                        takeoverInfo.setRemoteTransactionList( remoteTxnList );
                        logger.log( Logger.INFO, br.getString(
                            BrokerResources.I_TAKINGOVER_REMOTE_TXNS,
                            remoteTxnList.size(), targetBrokerID) );

                        tracker.setStage_BEFORE_DB_SWITCH_OWNER();

                        // Takeover all store sessions of target broker
                        StoreSessionDAO sesDAO = daoFactory.getStoreSessionDAO();
                        List<Long> sesList = sesDAO.takeover( conn, brokerID, targetBrokerID );
                        takeoverInfo.setTakeoverStoreSessionList(sesList);
                        String args2[] = { String.valueOf(sesList.size()),
                            targetBrokerID, sesList.toString() };
                        logger.log( Logger.INFO, br.getString(
                            BrokerResources.I_TAKINGOVER_STORE_SESSIONS, args2) );

                        if (!brokerDAO.updateState( conn, targetBrokerID, 
                                                    BrokerState.FAILOVER_COMPLETE,
                                                    BrokerState.FAILOVER_STARTED, false )) {
                            try {
                                conn.rollback();
                            } catch (SQLException rbe) {
                                logger.logStack( Logger.ERROR, BrokerResources.X_DB_ROLLBACK_FAILED, rbe );
                            }
                            throw new BrokerException(
                            "Unable to update state to "+ BrokerState.FAILOVER_COMPLETE+
                            " for broker "+targetBrokerID );
                        }

                        conn.commit();

                        tracker.setStage_AFTER_DB_SWITCH_OWNER();

                        // Removed saved state from cache
                        synchronized( takeoverLockMap ) {
                            takeoverLockMap.remove( targetBrokerID );
                        }

                        tracker.setStage_AFTER_TAKE_STORE();
                        if (partitionMode) {
                            addPartitionStores(sesList);
                        }

                        return takeoverInfo;
                    } catch ( Exception e ) {
                        // Exception will be log & re-throw if operation cannot be retry
                        if ( retry == null ) {
                            retry = new Util.RetryStrategy();
                        }
                        retry.assertShouldRetry( e );
                    }
                } while ( true );
            } catch ( Throwable thr ) {
                // We need to remove the takeover lock on the broker table
                // if we're unable to takeover the store due to an error.
                // We do not need to do a transaction rollback here because
                // the DAO layer should done this already.

                logger.logToAll( Logger.INFO,
                    BrokerResources.I_REMOVING_TAKEOVER_LOCK, targetBrokerID );

                HABrokerInfo savedInfo = takeoverInfo.getSavedBrokerInfo();
                try {
                    retry = null;
                    do {    // JDBC Retry loop
                        try {
                            // Restore original heartbeat
                            brokerDAO.update( conn, targetBrokerID,
                                              HABrokerInfo.RESTORE_HEARTBEAT_ON_TAKEOVER_FAIL,
                                              brokerID,
                                              savedInfo );

                            // Removed the lock, i.e. restore entry values
                            brokerDAO.update( conn, targetBrokerID, HABrokerInfo.RESTORE_ON_TAKEOVER_FAIL,
                                              brokerID, savedInfo );

                            logger.log(logger.INFO, br.getKString(br.I_BROKER_STATE_RESTORED_TAKEOVER_FAIL,
                                targetBrokerID, BrokerState.getState(savedInfo.getState()).toString()+
                                                            "[StoreSession:"+savedInfo.getSessionID()+"]"));

                            conn.commit();

                            synchronized( takeoverLockMap ) {
                                takeoverLockMap.remove( targetBrokerID );
                            }

                            break;  // // JDBC Retry loop
                        } catch ( Exception e ) {
                            // Exception will be log & re-throw if operation cannot be retry
                            if ( retry == null ) {
                                retry = new Util.RetryStrategy();
                            }
                            retry.assertShouldRetry( e );
                        }
                    } while ( true );
                } catch ( Exception e ) {
                    logger.logStack( Logger.ERROR,
                        BrokerResources.E_UNABLE_TO_REMOVE_TAKEOVER_LOCK,
                        targetBrokerID, e );
                }

                throw new BrokerException(
                    br.getKString( BrokerResources.E_UNABLE_TO_TAKEOVER_BROKER,
                    targetBrokerID), thr );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            try {
                Util.close( null, null, conn, myex );
            } finally {
                partitionLock.unlock();
                // decrement in progress count
                setInProgress(false);
            }
        }
    }

    public void updateTransactionAccessedTime(TransactionUID txnID,
        long accessedTime) throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getTransactionDAO().updateAccessedTime(null,
                        txnID, accessedTime);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public TransactionState getTransactionState(TransactionUID txnID)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getTransactionStateInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    public TransactionState getTransactionStateInternal(TransactionUID txnID)
        throws BrokerException {

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionState(null, txnID);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public BrokerAddress getRemoteTransactionHomeBroker(TransactionUID tid)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getRemoteTransactionHomeBrokerInternal(tid);
        } finally {
            setInProgress(false);
        }
        
    }

    public BrokerAddress getRemoteTransactionHomeBrokerInternal(TransactionUID tid)
        throws BrokerException {

        if (DEBUG) {
            logger.log(logger.INFO, "JDBCStore.getRemoteTransactionHomeBrokerInternal("+tid+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionHomeBroker(null, tid);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public TransactionBroker[] getClusterTransactionBrokers(TransactionUID tid)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getClusterTransactionBrokersInternal(tid);
        } finally {
            setInProgress(false);
        }
    }

    public TransactionBroker[] getClusterTransactionBrokersInternal(TransactionUID tid)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO, "JDBCStore.getClusterTransactionBrokersInternal("+tid+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionBrokers(null, tid);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public long getTransactionAccessedTime(TransactionUID txnID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTransactionDAO().getAccessedTime( null, txnID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public TransactionInfo getTransactionInfo(TransactionUID txnID)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getTransactionInfoInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    public TransactionInfo getTransactionInfoInternal(TransactionUID txnID)
        throws BrokerException {
        if (DEBUG) {
            logger.log(logger.INFO,  "JDBCStore.getTransactionInfoInternal("+txnID+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionInfo( null, txnID);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    protected Collection getTransactions(String brokerID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTransactionDAO().getTransactionsByBroker(
                        null, brokerID);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public int[] getTransactionUsageInfo(TransactionUID txnID)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getTransactionUsageInfoInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    public int[] getTransactionUsageInfoInternal(TransactionUID txnID)
        throws BrokerException {

        if (DEBUG) {
            logger.log(logger.INFO, "JDBCStore.getTransactionUsageInfoInternal("+txnID+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getTransactionDAO().getTransactionUsageInfo(null, txnID);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public long getDestinationConnectedTime(Destination destination)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return getDestinationConnectedTimeInternal(destination);
        } finally {
            setInProgress(false);
        }
    }

    public long getDestinationConnectedTimeInternal(Destination destination)
        throws BrokerException {

        if (DEBUG) {
            logger.log(Logger.INFO, 
            "JDBCStore.getDestinationConnectedTimeInternal("+destination.getUniqueName()+")");
        }

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getDestinationDAO().getDestinationConnectedTime(
                    null, destination.getUniqueName());
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    public boolean hasMessageBeenAcked(DestinationUID dst, SysMessageID mID)
        throws BrokerException {

        // make sure store is not closed then increment in progress count
        checkClosedAndSetInProgress();

        try {
            return hasMessageBeenAckedInternal(dst, mID);
        } finally {
            // decrement in progress count
            setInProgress(false);
        }
    }

    public boolean hasMessageBeenAckedInternal(DestinationUID dst, SysMessageID mID)
        throws BrokerException {

        Util.RetryStrategy retry = null;
        do {
            try {
                return daoFactory.getMessageDAO().hasMessageBeenAcked(null, dst, mID);
            } catch ( Exception e ) {
                if ( retry == null ) {
                    retry = new Util.RetryStrategy();
                }
                retry.assertShouldRetry( e );
            }
        } while ( true );
    }

    // unlock indicates whether we need to unlock the tables before we close
    private void closeDB(boolean unlock) {

        Connection conn = null;
        Exception myex = null;
        try {
            if (unlock) {
                conn = dbmgr.getConnection( true);

                // unlock the tables
                if ( !Globals.getHAEnabled() &&
                    config.getBooleanProperty( LOCK_STORE_PROP, true ) ) {
                    dbmgr.lockTables( conn, false );
                }
            }
        } catch (Exception e) {
            myex = e;
            logger.log(Logger.WARNING, BrokerResources.X_CLOSE_DATABASE_FAILED, e);
        } finally {
            try {
                Util.close( null, null, conn, myex );
            } catch ( Exception e ) {}
        }
        dbmgr.close();
    }

    public String getStoreType() {
	return JDBC_STORE_TYPE;
    }

    /**
     * The following methods does not apply to jdbc store.
     * Data synchronization methods are implemented as no-ops
     * after the necessary checks. Others will throw BrokerException.
     */
    public HashMap getStorageInfo(Destination destination)
	throws BrokerException {
	throw new BrokerException(br.getKString(BrokerResources.E_NOT_JDBC_STORE_OPERATION));
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public Hashtable getDebugState() throws BrokerException {

        String url = dbmgr.getOpenDBURL();
	String bid = "(" + dbmgr.getBrokerID() + ")";

	Hashtable t = new Hashtable();
        t.put("JDBC-based store", url + bid);
        t.put("Store version", String.valueOf(STORE_VERSION));

        Connection conn = null;
        Exception myex = null;
        try {
            conn = dbmgr.getConnection( true );

            Iterator itr = daoFactory.getAllDAOs().iterator();
            while ( itr.hasNext() ) {
                // Get debug info for each DAO
                t.putAll( ((BaseDAO)itr.next()).getDebugInfo( conn ) );
            }
        } catch (BrokerException e) {
            myex = e;
            throw e;
        } finally {
            Util.close( null, null, conn, myex );
        }
        t.put(dbmgr.toString(), dbmgr.getDebugState());

        return t;
    }

    public boolean isHADBStore() {
        if (dbmgr != null) {
            return dbmgr.isHADB();
        }
        return false;
    }

    public void resetConnectionPool() throws BrokerException {
        if (dbmgr != null) {
            dbmgr.resetConnectionPool();
        }
        throw new BrokerException("JDBCStore not inited !");
    }

    public void compactDestination(Destination destination)
	throws BrokerException {
	throw new BrokerException(br.getKString(BrokerResources.E_NOT_JDBC_STORE_OPERATION));
    }

    /**
     * 1. if new store exists (store of current version exists)
     *      print reminder message if old version still exists
     *      check to see if store need to be remove or reset
     * 2. if new store NOT exists
     *	    check if old store exists
     * 3. if old store exists
     *      check to see if store need to be upgrade
     * 4. if old store NOT exists
     *      check if store need to be create
     */
    private boolean checkStore( Connection conn ) throws BrokerException {

        boolean status = true;  // Set to false for caller to close DB

        // Check old store
        int oldStoreVersion = -1;
        if ( checkOldStoreVersion( conn,
            dbmgr.getTableName( VersionDAO.TABLE + SCHEMA_VERSION_40 ),
            VersionDAO.STORE_VERSION_COLUMN, OLD_STORE_VERSION_400 ) ) {
            oldStoreVersion = OLD_STORE_VERSION_400;
        } else if ( checkOldStoreVersion( conn, VERSION_TBL_37 + dbmgr.getBrokerID(),
            TVERSION_CVERSION, OLD_STORE_VERSION_370 ) ) {
            oldStoreVersion = OLD_STORE_VERSION_370;
        } else if ( checkOldStoreVersion( conn, VERSION_TBL_35 + dbmgr.getBrokerID(),
            TVERSION_CVERSION, OLD_STORE_VERSION_350 ) ) {
            oldStoreVersion = OLD_STORE_VERSION_350;
        }

        // Get the store version
        boolean foundNewStore = false;
        int storeVersion = 0;
        try {
            storeVersion = daoFactory.getVersionDAO().getStoreVersion( conn );
        } catch ( BrokerException e ) {
            // Assume new store doesn't exist
            logger.log(Logger.WARNING, e.getMessage(), e.getCause() );
        }

        // Verify the version match
        if ( storeVersion > 0 ) {
            foundNewStore = ( storeVersion == STORE_VERSION );
            if ( foundNewStore ) {
                // Make sure tables exist
                DBTool.updateStoreVersion410IfNecessary( conn, dbmgr );
                if ( dbmgr.checkStoreExists(conn) == -1 ) {
                    logger.log(Logger.ERROR, BrokerResources.E_BAD_STORE_MISSING_TABLES);
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_BAD_STORE_MISSING_TABLES));
                }
            } else {
                // Store doesn't have the version that we are expecting!
                String found = String.valueOf(storeVersion);
                String expected = String.valueOf(STORE_VERSION);
                logger.log(Logger.ERROR, BrokerResources.E_BAD_STORE_VERSION,
                    found, expected);
                throw new BrokerException(br.getKString(
                    BrokerResources.E_BAD_STORE_VERSION,
                    found, expected));
            }

            if ( ( oldStoreVersion > 0 ) && !removeStore ) {
                // old store exists & removeStore is not true so
                // log reminder message to remove old tables, i.e. 3.5
                logger.logToAll(Logger.INFO,
                    BrokerResources.I_REMOVE_OLDTABLES_REMINDER);
            }
        }

        // Process any cmd line options

        if ( foundNewStore ) {
            if ( removeStore ) {
                try {
                    // just drop all tables
                    DBTool.dropTables( conn, false, dbmgr );
                } catch (SQLException e) {
                    throw new BrokerException(
                        br.getKString(BrokerResources.E_REMOVE_JDBC_STORE_FAILED,
                        dbmgr.getOpenDBURL()), e);
                }
                status = false; // Signal calling method to close DB
            } else if ( resetStore ) {
                clearAll(true);
            }
        } else {
            boolean createNew = false;
            if ( createStore ) {
                // Are we supposed to automatically create the store?
                createNew = true;
            }

            boolean dropOld = false;
            String dropMsg = null;  // Reason for dropping the old store
            if ( oldStoreVersion > 0 ) {
                if ( removeStore ) {
                    dropOld = true;
                    dropMsg = BrokerResources.I_REMOVE_OLD_DATABASE_TABLES;
                } else if ( resetStore ) {
                    createNew = true;
                    dropOld = true;
                    dropMsg = BrokerResources.I_RESET_OLD_DATABASE_TABLES;
                } else {
                    // log message to do upgrade
                    logger.logToAll(Logger.INFO, BrokerResources.I_UPGRADE_STORE_MSG,
			Integer.valueOf( oldStoreVersion ) );

                    if (upgradeNoBackup && !Broker.getBroker().force) {
                        // will throw BrokerException if the user backs out
                        getConfirmation();
                    }

                    // Upgrade the store to the new version
                    if (!Globals.getHAEnabled()) {
                        new UpgradeStore(this, oldStoreVersion).upgradeStore(conn);
                        return status;
                    }
                }
            }

            if ( !createNew ) {
                // Error - user must create the store manually!
                logger.log(Logger.ERROR, BrokerResources.E_NO_DATABASE_TABLES);
                throw new BrokerException(
                    br.getKString(BrokerResources.E_NO_DATABASE_TABLES));
            }

            // Now, do required actions

            if ( dropOld ) {
                logger.logToAll(Logger.INFO, dropMsg);

                try {
                    // just drop all old tables
                    DBTool.dropTables(conn, dbmgr.getTableNames( oldStoreVersion ),
                                      false, false, dbmgr );
                } catch (Exception e) {
                    logger.logToAll(Logger.ERROR, BrokerResources.E_REMOVE_OLD_TABLES_FAILED, e);
                    throw new BrokerException(
                        br.getKString(BrokerResources.E_REMOVE_OLD_TABLES_FAILED), e);
                }
            }

            if ( createNew ) {
                logger.logToAll(Logger.INFO, BrokerResources.I_WILL_CREATE_NEW_STORE);

                try {
                    // create the tables
                    DBTool.createTables( conn );
                } catch (Exception e) {
                    String url = dbmgr.getCreateDBURL();
                    if ( url == null || url.length() == 0 ) {
                        url = dbmgr.getOpenDBURL();
                    }
                    String msg = br.getKString(
                        BrokerResources.E_CREATE_DATABASE_TABLE_FAILED, url);
                    logger.logToAll(Logger.ERROR, msg+": "+e);
                    logger.logStack(Logger.ERROR, msg, e);
                    throw new BrokerException(msg, e);
                }
            }
        }

        return status;
    }

    /**
     * Get the current store session ID.
     */
    private long getStoreSession() throws BrokerException {
        if (Globals.getHAEnabled()) {
            return Globals.getStoreSession().longValue();
        } else {
            StoreSessionDAO sessionDAO = daoFactory.getStoreSessionDAO();
            return sessionDAO.getStoreSession( null, dbmgr.getBrokerID() );
        }
    }

    /**
     * Return true if table exists and stored version match what's expected
     * Return false if table does not exist
     */
    public boolean checkOldStoreVersion( Connection conn, String vTable,
        String vColumn, int version ) throws BrokerException {

        try {
            String selectSQL = "SELECT " + vColumn + " FROM " + vTable;

            Statement stmt = null;
            ResultSet rs = null;
            Exception myex = null;
            try {
                stmt = conn.createStatement();
                rs = dbmgr.executeQueryStatement( stmt, selectSQL );
                if ( rs.next() ) {
                    int storeVersion = rs.getInt( 1 );
                    if ( storeVersion == version ) {
                        return true;
                    }

                    // Old store doesn't have the version we are expecting
                    String found = String.valueOf(storeVersion);
                    String expected = String.valueOf(version);
                    logger.log(Logger.ERROR, BrokerResources.E_BAD_OLDSTORE_VERSION,
                        found, expected);
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_BAD_OLDSTORE_VERSION,
                        found, expected));
                } else {
                    // Old store doesn't have any data
                    logger.log(Logger.ERROR,
                        BrokerResources.E_BAD_OLDSTORE_NO_VERSIONDATA, vTable);
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_BAD_OLDSTORE_NO_VERSIONDATA, vTable));
                }
            } catch ( SQLException e ) {
                myex = e;
                // assume that the table does not exist
                if (DEBUG) {
                    logger.log( Logger.INFO, "Assume old store does not exist because : " +
                                e.getMessage() );
                }
            } finally {
                Util.close( rs, stmt, null, myex );
            }
        } catch ( Exception e ) {
            logger.log(Logger.ERROR, BrokerResources.X_STORE_VERSION_CHECK_FAILED, e);
            throw new BrokerException(
                br.getKString(BrokerResources.X_STORE_VERSION_CHECK_FAILED), e);
        }

        return false;
    }

    boolean resetMessage() {
	return resetMessage;
    }

    boolean resetInterest() {
	return resetInterest;
    }

    static class StoreSessionReaperTask extends TimerTask
    {
        private boolean canceled = false;
        Logger logger = Globals.getLogger();
        JDBCStore store = null;

        public StoreSessionReaperTask(JDBCStore store) {
            this.store = store;
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

            try {
                if (!store.partitionMode) { 
                    StoreSessionDAO sesDAO = store.daoFactory.getStoreSessionDAO();
                    List<Long> reaped = sesDAO.deleteInactiveStoreSession(null);
                    if (reaped.size() == 0) {
                        store.notifyPartitionRemoved(null, null);
                    } else {
                        Iterator<Long> itr = reaped.iterator();
                        while (itr.hasNext()) {
                            store.notifyPartitionRemoved(new UID(itr.next()), null);
                        }
                    }
                }
                List<StoreSessionReaperListener> moretasks = null;
                synchronized(store.sessionReaperListeners) {
                    moretasks = new ArrayList<StoreSessionReaperListener>(
                                    store.sessionReaperListeners);
                }
                Iterator<StoreSessionReaperListener> itr = moretasks.iterator();
                while (itr.hasNext()) {
                    itr.next().runStoreSessionTask();
                }
            } catch (Exception e) {
                logger.logStack( Logger.ERROR,
                    BrokerResources.E_INACTIVE_SESSION_REMOVAL_FAILED, e );
            }
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

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (logRecord == null) throw new IllegalArgumentException("null logRecord");
        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.storeTMLogRecord("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.storeTMLogRecord("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getTMLogRecordDAOJMSBG().insert(null,
                                      xid, logRecord, name, logger_);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
 
    }

    /**
     * Update a log record
     *
     * @param xid the global XID 
     * @param logRecord the new log record data for the xid
     * @param name the jmsbridge name
     * @param addIfNotExist
     * @param sync - not used
     * @param callback to obtain updated data 
     * @param logger_ can be null  
     * @exception KeyNotFoundException if not found and addIfNotExist false
     *            else Exception on erorr
     */
    public void updateTMLogRecord(String xid, byte[] logRecord, 
                                  UpdateOpaqueDataCallback callback,
                                  String name, boolean addIfNotExist, 
                                  boolean sync,
                                  java.util.logging.Logger logger_)
                                  throws Exception {

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (logRecord == null) throw new IllegalArgumentException("null logRecord");
        if (name == null) throw new IllegalArgumentException("null name");
        if (callback == null) throw new IllegalArgumentException("null callback");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.updateTMLogRecord("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
             "JDBCStore.updateTMLogRecord("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getTMLogRecordDAOJMSBG().updateLogRecord(null,
                                                        xid, logRecord, name,
                                                        callback, addIfNotExist,
                                                        logger_);
                    return;
                } catch (Exception e) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Remove a log record
     *
     * @param xid the global XID 
     * @param name the jmsbride name
     * @param sync - not used 
     * @param logger_ can be null  
     * @exception KeyNotFoundException if not found 
     *            else Exception on error
     */
    public void removeTMLogRecord(String xid, String name, 
                                  boolean sync, 
                                  java.util.logging.Logger logger_)
                                  throws KeyNotFoundException, Exception {

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.removeTMLogRecord("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.removeTMLogRecord("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getTMLogRecordDAOJMSBG().delete(null,
                                                        xid, name, logger_);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
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

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getTMLogRecord("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.getTMLogRecord("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTMLogRecordDAOJMSBG().getLogRecord(null,
                                                               xid, name, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
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
    public long getTMLogRecordUpdatedTime(String xid,  String name, 
                                          java.util.logging.Logger logger_) 
                                          throws KeyNotFoundException, Exception {

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getTMLogRecordUpdatedTime("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.getTMLogRecordUpdatedTime("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTMLogRecordDAOJMSBG().getUpdatedTime(
                                                     null, xid, name, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
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

        if (xid == null) throw new IllegalArgumentException("null xid");
        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getTMLogRecordCreatedTime("+xid+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.getTMLogRecordcreatedTime("+ xid+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTMLogRecordDAOJMSBG().getCreatedTime(
                                                     null, xid, name, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Get all log records for a TM name
     *
     * @param name the jmsbridge name
     * @param logger_ can be null  
     * @return a list of log records
     * @exception Exception if error
     */
    public List getTMLogRecordsByName(String name,
                                      java.util.logging.Logger logger_)
                                      throws Exception {
        return getLogRecordsByNameByBroker(name, dbmgr.getBrokerID(), logger_);
    }

    /**
     * Get all log records for a JMS bridge in a broker 
     *
     * @param name the jmsbridge name
     * @param logger_ can be null  
     * @return a list of log records
     * @exception Exception if error
     */
    public List getLogRecordsByNameByBroker(String name,
                                            String brokerID,
                                            java.util.logging.Logger logger_)
                                            throws Exception {

        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getTMLogRecordsByNameByBroker("+name+", "+brokerID+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.getTMLogRecordsByNameByBroker("+name+", "+brokerID+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTMLogRecordDAOJMSBG().getLogRecordsByNameByBroker(
                                                               null, name, brokerID, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }

    }

    /**
     * Get JMS bridge names in all log records owned by a brokerID
     *
     * @param brokerID 
     * @param logger_ can be null  
     * @return a list of log records
     * @exception Exception if error
     */
    public List getNamesByBroker(String brokerID, 
                                 java.util.logging.Logger logger_)
                                 throws Exception {

        if (brokerID == null) throw new IllegalArgumentException("null brokerID");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getTMNamesByBroker("+brokerID+")");
        Util.logExt(logger_, java.util.logging.Level.FINE, 
                    "JDBCStore.getTMNamesByBroker("+brokerID+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getTMLogRecordDAOJMSBG().getNamesByBroker(
                                                               null, brokerID, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }

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

        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.addJMSBridge("+name+")");
        Util.logExt(logger_, java.util.logging.Level.FINE,
                    "JDBCStore.addJMSBridge("+name+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getJMSBGDAO().insert(null, name, logger_);
                    return;
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Get JMS bridges owned by this broker 
     *
     * @param logger_ can be null
     * @return a list of names
     * @exception Exception if error
     */
    public List getJMSBridges(java.util.logging.Logger logger_)
                             throws Exception {
        return getJMSBridgesByBroker(dbmgr.getBrokerID(), logger_);
    }

    /**
     * Get JMS bridges owned by a broker
     *
     * @param brokerID 
     * @param logger_ can be null
     * @return a list of names
     * @exception Exception if error
     */
    public List getJMSBridgesByBroker(String brokerID,
                                      java.util.logging.Logger logger_)
                                      throws Exception {
 
        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getJMSBridgesByBroker("+brokerID+")");
        Util.logExt(logger_, java.util.logging.Level.FINE,
                             "JDBCStore.getJMSBridges("+brokerID+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getJMSBGDAO().getNamesByBroker(null, brokerID, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
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

        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getJMSBridgeUpdatedTime("+name+")");
        Util.logExt(logger_, java.util.logging.Level.FINE,
                             "JDBCStore.getJMSBridgeUpdatedTime("+name+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getJMSBGDAO().getUpdatedTime(null, name, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
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

        if (name == null) throw new IllegalArgumentException("null name");

        if (DEBUG) {
        logger.log(Logger.INFO, "JDBCStore.getJMSbridgeCreatedTime("+name+")");
        Util.logExt(logger_, java.util.logging.Level.FINE,
                    "JDBCStore.getJMSBridgeCreatedTime("+name+")", null);
        }

        checkClosedAndSetInProgress();

        try {
            Util.RetryStrategy retry = null;
            do {
                try {
                    return daoFactory.getJMSBGDAO().getCreatedTime(null, name, logger_);
                } catch ( Exception e ) {
                    // Exception will be log & re-throw if operation cannot be retry
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
    }

    public void closeJMSBridgeStore() throws Exception {
        //ignore
    }


    /************************************************
     *	Additional PartitionedStore Methods
     ************************************************/
  
    /**
     * Return the LoadException for loading destinations; null if there's
     * none.
     */
    public LoadException getLoadDestinationException() {
        return null;
    }

    /**
     * Return the LoadException for loading transactions; null if there's none.
     */
    public LoadException getLoadTransactionException() {
        return null;
    }

    /**
     * Return the LoadException for loading transaction acknowledgements;
     * null if there's none.
     */
    public LoadException getLoadTransactionAckException() {
	return null;
    }


   /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID       the destination the message is associated with
     * @param mID       the system message id of the message to be removed
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException  if <code>dID</code> is
     *                  <code>null</code>
     */
    public void removeMessage(DestinationUID dID,
                              SysMessageID mID, boolean sync)
                              throws IOException, BrokerException {
         removeMessage(dID,mID,sync, false);
    }

    /********************************************
     * Partitioned Store Specific Methods
     **********************************************/

    public void init(Store store, UID id, boolean isPrimrary)
    throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by JDBCStore class"); 
    }

    @Override
    public boolean getPartitionModeEnabled() {
        return partitionMode;
    }

    @Override
    public boolean isPartitionMigratable() {
        return partitionMigratable;
    }

    public String toString() {
        return "["+getStoreType()+"]";
    }

    public int hashCode() {
        return partitionid.hashCode();
    }

    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof PartitionedStore)) {
            return false;
        }
        return getPartitionID().equals(
            ((PartitionedStore)anObject).getPartitionID());

    }

    public UID getPartitionID() {
        return partitionid;
    }

    public boolean isPrimaryPartition() {
        return true;
    }

    @Override
    public void partitionsReady() throws BrokerException {
        synchronized(closedLock) {
            partitionsReady = true;
	}
        initPartitionStores();
    }

    @Override
    public List<PartitionedStore> getAllStorePartitions() throws BrokerException {
        synchronized(partitionStores) {
            return new ArrayList<PartitionedStore>(partitionStores.values());
        }
    }

    @Override
    public PartitionedStore getStorePartition(UID id) throws BrokerException {
        PartitionedStore ps = null;

        checkClosedAndSetInProgress();
        try {
            synchronized(partitionStores) {
                ps = partitionStores.get(id); 
            } 
            if (ps == null) {
                throw new BrokerException(
                br.getKString(br.X_STORE_PARTITION_NOT_FOUND, id),
                Status.NOT_FOUND);
            }
        } finally {
            setInProgress(false);
        }
        return ps;
    }

    public void partitionDeparture(UID partitionID, String targetBrokerID)
    throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            if (!partitionMode) {
                throw new BrokerException("Broker store not partition mode",
                    Status.PRECONDITION_FAILED);
            }
            if (!partitionMigratable) {
                throw new BrokerException("Broker store partition not migratable", 
                    Status.PRECONDITION_FAILED);
            }
            synchronized(closedLock) {
                if (!partitionsReady) {
                    throw new BrokerException("Store partitions init not ready yet", Status.PRECONDITION_FAILED);
                }
            }
            synchronized(partitionStores) {
                PartitionedStore pstore = partitionStores.remove(partitionID);
                if (pstore == null) {
                    String emsg = "Departure partition "+partitionID+" does not exist in this broker";
                    throw new BrokerException(emsg, Status.NOT_FOUND);
                }
                notifyPartitionRemoved(partitionID, targetBrokerID); //XXX
                if (!pstore.isClosed()) {
                    throw new BrokerException("Departure partition "+partitionID+ " not closed");
                }
            }
            Util.RetryStrategy retry = null;
            do {
                try {
                    daoFactory.getStoreSessionDAO().moveStoreSession(
                        null, partitionID.longValue(), targetBrokerID);
                    return;
                } catch ( Exception e ) {
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            setInProgress(false);
        }
    }

    /**
     * @partitionID the UID for the arrived partition 
     *              or null if check partition arrival 
     * @return a list contains the PartitionedStore for the partitionID
     *         or a list of arrived PartitionedStore during check 
     */
    public List<PartitionedStore> partitionArrived(UID partitionID)
    throws BrokerException {

        PartitionedStore pstore = null;

        checkClosedAndSetInProgress();
        boolean locked = false;
        try {
            synchronized(closedLock) {
                if (!partitionsReady) {
                    throw new BrokerException(
                    "Store partitions init not ready yet", Status.PRECONDITION_FAILED);
                }
            }
            if (!partitionMode) {
                throw new BrokerException("Broker store not partition mode", Status.NOT_ALLOWED);
            }
            if (partitionID != null) {
                synchronized(partitionStores) {
                    pstore = partitionStores.get(partitionID);
                    if (pstore != null) {
                        logger.log(logger.INFO, 
                        "Arrived partition "+partitionID+" has already been loaded in store");
                        List<PartitionedStore> p = new ArrayList<PartitionedStore>();
                        p.add(pstore);
                        return p;
                    }
                }
            }
            Util.RetryStrategy retry = null;
            do {
                try {
                    if (partitionID != null) {

                        String brokerID = daoFactory.getStoreSessionDAO().
                            getStoreSessionOwner(null, partitionID.longValue());
                        if (brokerID == null || !brokerID.equals(dbmgr.getBrokerID())) {
                            String emsg = br.getKString(
                                 br.X_NOT_OWN_ARRIVED_STORE_PARTITION, partitionID);
                            throw new BrokerException(emsg, Status.NOT_ALLOWED);
                        }
                        List<Long> l = new ArrayList<Long>(); 
                        l.add(Long.valueOf(partitionID.longValue()));
                        return addPartitionStores(l);

                    } else {

                        if (!partitionLock.tryLock()) {
                            throw new BrokerException(
                            "Broker takeover in process, retry operation later", Status.RETRY);
                        }
                        locked = true;

                        //This method is executed by 1 dedicated thread - partition monitor

                        List<Long> sessions = dbmgr.getDAOFactory().getStoreSessionDAO().
                                              getStoreSessionsByBroker( null, dbmgr.getBrokerID() );
                        List<Long> l = new ArrayList<Long>(); 
                        synchronized(partitionStores) {
                            Iterator<Long> itr = sessions.iterator();
                            while (itr.hasNext()) {
                                Long s = itr.next();
                                if (partitionStores.get(new UID(s)) == null) {
                                    l.add(s);
                                }
                            }
                        }
                        return addPartitionStores(l);
                    }
                } catch ( Exception e ) {
                    if ( retry == null ) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );
        } finally {
            if (locked) {
                partitionLock.unlock();
            }
            setInProgress(false);
        }
    }


    private List<PartitionedStore> addPartitionStores(List<Long> storeSessions)
    throws BrokerException {

        List<PartitionedStore> loaded = new ArrayList<PartitionedStore>();
        UID uid = null;
        PartitionedStore ps = null;
        try {
            synchronized(partitionStores) {
                Iterator<Long> itr = storeSessions.iterator();
                while (itr.hasNext()) {
                    uid = new UID(itr.next().longValue());
                    if (partitionStores.get(uid) != null) {
                        throw new BrokerException(
                        br.getKString(br.X_ADD_STORE_PARTITION_ALREAD_EXIST, uid));
                    }
                    logger.log(logger.INFO,  br.getKString(br.I_CREATE_STORE_PARTITION, uid)); 
                    if (Globals.isNucleusManagedBroker()) {
                        ps = Globals.getHabitat().
                             getService(PartitionedStore.class, partitionClassStr);
                    } else {
                        ps = (PartitionedStore)partitionClass.newInstance();
                    }
                    ps.init(this, uid, (uid.longValue() == getStoreSession()));
                    partitionStores.put(uid, ps);
                    loaded.add(ps);
                    notifyPartitionAdded(uid);
                }
            }
            return loaded;
        } catch (Exception e) {
            throw new BrokerException(e.getMessage(), e);
        }
    }

    private void initPartitionStores() throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            if (!partitionMode) {
                partitionStores.put(getPartitionID(), this);
                return;
            }  
            synchronized(closedLock) {
                if (!partitionsReady) {
                    throw new BrokerException("IllegalState: store partitions not ready");
                }
            }
            List<Long> sessions =  new ArrayList<Long>(); 
            sessions.add(Long.valueOf(getStoreSession()));
            addPartitionStores(sessions);

            Util.RetryStrategy retry = null;
            do {
                try {
                    sessions = dbmgr.getDAOFactory().getStoreSessionDAO().
                                   getStoreSessionsByBroker( null, dbmgr.getBrokerID() );
                    logger.log(logger.INFO,  br.getKString(
                               br.I_OWN_STORE_SESSIONS_CREATE_PARTITIONS, 
                               Integer.valueOf(sessions.size()))); 
                    sessions.remove(Long.valueOf(getStoreSession()));
                    addPartitionStores(sessions);
                    return;
                } catch ( Exception e ) {
                    if (retry == null) {
                        retry = new Util.RetryStrategy();
                    }
                    retry.assertShouldRetry( e );
                }
            } while ( true );

        } finally {
            setInProgress(false);
        }
    }

    public PartitionedStore getPrimaryPartition() throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            if (!partitionMode) {
                return this;
            }
            if (partitionStores.size() == 0) {
                throw new BrokerException("IllegalStateException: partition store not ready");
            }
            return partitionStores.get(new UID(getStoreSession()));

        } finally {
            setInProgress(false);
        }
    }

    protected void notifyPartitionRemoved(UID partitionID, String removedToBroker) {
        synchronized(partitionStores) {
            Iterator<PartitionListener> itr =  partitionListeners.iterator();
            while (itr.hasNext()) {
                itr.next().partitionRemoved(partitionID, this, removedToBroker);
            }
        }
    }

    protected void notifyPartitionAdded(UID partitionID) {
        synchronized(partitionStores) {
            Iterator<PartitionListener> itr =  partitionListeners.iterator();
            while (itr.hasNext()) {
                itr.next().partitionAdded(partitionID, this);
            }
        }
    }
    /**
     * @param listener the PartitionListener to be added
     * @exception BrokerException
     */
    @Override
    public void addPartitionListener(PartitionListener listener) 
    throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            synchronized(partitionStores) {
                partitionListeners.add(listener);
            }

        } finally {
            setInProgress(false);
        }
    }

    /**
     * @param listener the PartitionListener to be removed 
     * @exception BrokerException
     */
    @Override
    public void removePartitionListener(PartitionListener listener) 
    throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            synchronized(partitionStores) {
                partitionListeners.remove(listener);
            }

        } finally {
            setInProgress(false);
        }
    }

    /**
     * @exception BrokerException
     */
    @Override
    public void addStoreSessionReaperListener(StoreSessionReaperListener listener)
    throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            synchronized(sessionReaperListeners) {
                sessionReaperListeners.add(listener);
            }

        } finally {
            setInProgress(false);
        }
    }

    /**
     * @exception BrokerException
     */
    @Override
    public void removeStoreSessionReaperListener(StoreSessionReaperListener listener)
    throws BrokerException {
        checkClosedAndSetInProgress();
        try {
            synchronized(sessionReaperListeners) {
                sessionReaperListeners.remove(listener);
            }

        } finally {
            setInProgress(false);
        }
    }

}
