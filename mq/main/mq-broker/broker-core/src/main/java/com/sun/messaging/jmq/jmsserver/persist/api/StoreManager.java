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
 * @(#)StoreManager.java	1.25 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 * This class contains static methods to obtain a singleton Store instance
 * for storing and retrieving data needed by the broker.
 */

public class StoreManager {

    static private final String PERSIST_PROP_PREFIX = Globals.IMQ + ".persist.";
    static private final String CLASS_PROP = ".class";
    public static final String STORE_TYPE_PROP =
                     Globals.IMQ + ".persist.store";

    static private final String TXNLOG_ENABLED_PROP =
                    Globals.IMQ + ".persist.file.txnLog.enabled";

    static public final String NEW_TXNLOG_ENABLED_PROP =
                    Globals.IMQ + ".persist.file.newTxnLog.enabled";

    static public final boolean NEW_TXNLOG_ENABLED_PROP_DEFAULT = true;

    static private final String COHERENCE_SERVER_ENABLED_PROP =
                    Globals.IMQ + ".persist.coherenceServer.enabled";
    static private final boolean COHERENCE_SERVER_ENABLED_DEFAULT = false;

    static public final String BDB_REPLICATION_PROP_PREFIX = 
                    Globals.IMQ + ".persist.bdb.replication.";
    static public final String BDB_REPLICATION_ENABLED_PROP = 
                    BDB_REPLICATION_PROP_PREFIX+"enabled";
    static public final String BDB_ACTIVE_REPLICA_PROP = 
                    BDB_REPLICATION_PROP_PREFIX+"activeReplica";

    static public final String BDB_SHARED_FS_PROP = 
                    Globals.IMQ + ".persist.bdb.sharedfs";
    static public final boolean BDB_SHARED_FS_DEFAULT = false;

    static public final boolean BDB_ACTIVE_REPLICA_DEFAULT = true;

    static public final boolean BDB_REPLICATION_ENABLED_DEFAULT = false;

    
    static private final String DEFAULT_STORE_TYPE = Store.FILE_STORE_TYPE;

    static private final String DEFAULT_FILESTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.file.FileStore";

    static private final String DEFAULT_JDBCSTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.jdbc.JDBCStore";

    static private final String DEFAULT_INMEMORYSTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.inmemory.InMemoryStore";

    static private final String DEFAULT_COHERENCESTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.coherence.CoherenceStore";

    static private final String DEFAULT_BDBSTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.bdb.BDBStore";

    public static final String PARTITION_MODE_PROP =
           PERSIST_PROP_PREFIX+"partitionMode.enabled";

    static private Boolean isConfiguredFileStore = null;
    static private Boolean isConfiguredJDBCStore = null;
    static private Boolean isConfiguredBDBStore = null;
    static private Boolean isConfiguredCoherenceStore = null;
    static private Boolean txnLogEnabled = null;
    static private Boolean newTxnLogEnabled = null;

    // Singleton Store instance
    static private Store store = null;

    static private ShareConfigChangeStore shareccStore = null;

    /**
     * Return a singleton instance of a Store object.
     * <p>
     * The type of store to use is defined in the property:<br>
     * jmq.persist.store=<type>
     * <p>
     * The class to use for the specified type is defined in:<br>
     * jmq.persist.<type>.class=<classname>
     * <p>
     * If the type property is not defined, the default file based
     * store will be instantiated and returned.
     * If 'jdbc' type is defined, and no class is defined, the default
     * jdbc based store will be instantiated and returned.
     * <p>
     * If the type property is defined but we fail to instantiate the
     * correspoinding class, a BrokerException will be thrown
     * @return	a Store
     * @exception BrokerException	if it fails to instantiate a Store
     *					instance
     */
    public static synchronized Store getStore() throws BrokerException {
        Logger logger = Globals.getLogger();
        BrokerResources br = Globals.getBrokerResources();

        if (store == null) {
            // Can't open the store if we are shutting down
            if (BrokerStateHandler.isShuttingDown()) {
                throw new BrokerException(
                   Globals.getBrokerResources().getKString(
                       BrokerResources.X_SHUTTING_DOWN_BROKER),
                   BrokerResources.X_SHUTTING_DOWN_BROKER);
            }

	    BrokerConfig config = Globals.getConfig();

	    String type = config.getProperty(STORE_TYPE_PROP,
					DEFAULT_STORE_TYPE);
	    if (Store.getDEBUG()) {
		logger.log(logger.DEBUG, STORE_TYPE_PROP + "=" + type);
	    }

	    String classname = config.getProperty(
                        PERSIST_PROP_PREFIX + type + CLASS_PROP);
	    
        isConfiguredFileStore = Boolean.valueOf(false);
        isConfiguredJDBCStore = Boolean.valueOf(false);
        isConfiguredBDBStore = Boolean.valueOf(false);
	    if (classname == null || classname.equals("")) {
            if (type.equals(Store.FILE_STORE_TYPE)) {
                classname = DEFAULT_FILESTORE_CLASS;
                isConfiguredFileStore = Boolean.valueOf(true);
            } else if (type.equals(Store.JDBC_STORE_TYPE)) {
                classname = DEFAULT_JDBCSTORE_CLASS;
                isConfiguredJDBCStore = Boolean.valueOf(true);
            } else if (type.equals(Store.INMEMORY_STORE_TYPE)) {
                classname = DEFAULT_INMEMORYSTORE_CLASS;
            } else if (type.equals(Store.COHERENCE_STORE_TYPE)) {
                classname = DEFAULT_COHERENCESTORE_CLASS;
                isConfiguredCoherenceStore = Boolean.valueOf(true);
            } else if (type.equals(Store.BDB_STORE_TYPE)) {
                classname = DEFAULT_BDBSTORE_CLASS;
                isConfiguredBDBStore = Boolean.valueOf(true);
            } else {
                classname = null;
            }
        }
	    if (classname == null) {
		throw new BrokerException(
                    br.getString(br.E_BAD_STORE_TYPE, type));
	    } else {
		if (Store.getDEBUG()) {
		    logger.log(logger.DEBUG,
			PERSIST_PROP_PREFIX + type + CLASS_PROP + "=" + classname);
		}
	    }

	    try {
                boolean reload = false;
                do {

                    if (Globals.isNucleusManagedBroker()) {
                       store = Globals.getHabitat().
                                   getService(Store.class, classname);
                    } else {
                        store = (Store)Class.forName(classname).newInstance();
                    }

                    //store.init();
                    txnLogEnabled = Boolean.valueOf(config.getBooleanProperty(TXNLOG_ENABLED_PROP, false));
                    newTxnLogEnabled = Boolean.valueOf(config.getBooleanProperty(
                                       NEW_TXNLOG_ENABLED_PROP, NEW_TXNLOG_ENABLED_PROP_DEFAULT));
                    if (!isConfiguredFileStore.booleanValue()) {
                        if (txnLogEnabled.booleanValue()) {
                            logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                            BrokerResources.W_IGNORE_PROP_SETTING, TXNLOG_ENABLED_PROP+"=true"));
                        }                        
                        if (newTxnLogEnabled.booleanValue()) {
                            logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(
                            BrokerResources.W_IGNORE_PROP_SETTING, NEW_TXNLOG_ENABLED_PROP+"=true"));
                        }
                        break;
                    } 
                    Globals.isMinimumPersistLevel2(); //logging

                    if(Globals.isNewTxnLogEnabled())
                    {
                    
                    	// txn logger is now read during store.init
                    	return store;
                    }
                    
                    // Initialize transaction logging class if enabled.
                    // If return status is set to true, the store need to be
                    // reload because the store has been reconstructed from
                    // the txn log files; probably due to system crash or the
                    // broker didn't shutdown cleanly.
                    if (reload) {
                        if (((TxnLoggingStore)store).initTxnLogger()) {
                            // Shouldn't happens
                            throw new BrokerException(br.getString(
                                BrokerResources.E_RECONSTRUCT_STORE_FAILED));
                        }
                        break;
                    } else {
                        // 1st time through the loop
                        reload = ((TxnLoggingStore)store).initTxnLogger();
                        if (reload) {
                            store.close();
                            store = null;
                        }
                    }
                } while (reload);
	    } catch (Exception e) {
                if (e instanceof BrokerException) {
                    throw (BrokerException)e;
                } else {
                    throw new BrokerException(
                        br.getString(br.E_OPEN_STORE_FAILED), e);
                }
	    }
    }

	return store;
    }

    public static boolean isConfiguredFileStore() {
        Boolean isfs = isConfiguredFileStore;
        if (isfs != null) return isfs.booleanValue();

        String type = Globals.getConfig().getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        return ((type.equals(Store.FILE_STORE_TYPE)));
    }

    public static boolean isConfiguredJDBCStore() {
        Boolean isjdbc = isConfiguredJDBCStore;
        if (isjdbc != null) return isjdbc.booleanValue();

        String type = Globals.getConfig().getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        return ((type.equals(Store.JDBC_STORE_TYPE)));
    }

    public static boolean isConfiguredBDBStore() {
        Boolean isbdb = isConfiguredBDBStore;
        if (isbdb != null) return isbdb.booleanValue();

        String type = Globals.getConfig().getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        return ((type.equals(Store.BDB_STORE_TYPE)));
    }

    public static boolean isConfiguredCoherenceStore() {
        Boolean isch = isConfiguredCoherenceStore;
        if (isch != null) return isch.booleanValue();

        String type = Globals.getConfig().getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        return ((type.equals(Store.COHERENCE_STORE_TYPE)));
    }

    public static boolean bdbREPEnabled() {
        return isConfiguredBDBStore() && Globals.getConfig().getBooleanProperty(
            BDB_REPLICATION_ENABLED_PROP, BDB_REPLICATION_ENABLED_DEFAULT);
    }

    public static boolean isConfiguredBDBSharedFS() {
        return isConfiguredBDBStore() && Globals.getConfig().getBooleanProperty(
            BDB_SHARED_FS_PROP, BDB_SHARED_FS_DEFAULT);
    }

    public static boolean isConfiguredCoherenceServer() {
        return isConfiguredCoherenceStore() || 
               Globals.getConfig().getBooleanProperty(
               COHERENCE_SERVER_ENABLED_PROP, COHERENCE_SERVER_ENABLED_DEFAULT);
    }

    public static boolean isConfiguredPartitionMode(boolean deft) {
        return Globals.getConfig().getBooleanProperty(
                           PARTITION_MODE_PROP, deft);
    }

    public static boolean txnLogEnabled() {
        if (!isConfiguredFileStore()) return false;

        Boolean tloge = txnLogEnabled;
        if (tloge != null) return tloge.booleanValue();

        return Globals.getConfig().getBooleanProperty(TXNLOG_ENABLED_PROP, false);
    }
    
    public static boolean newTxnLogEnabled() {
        if (!isConfiguredFileStore()) return false;

        Boolean ntloge = newTxnLogEnabled;
        if (ntloge != null) return ntloge.booleanValue();

        return Globals.getConfig().getBooleanProperty(
            NEW_TXNLOG_ENABLED_PROP, NEW_TXNLOG_ENABLED_PROP_DEFAULT);
    }

    public static synchronized ShareConfigChangeStore
    getShareConfigChangeStore() throws BrokerException {

        if (BrokerStateHandler.isShuttingDown()) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_SHUTTING_DOWN_BROKER),
                    BrokerResources.X_SHUTTING_DOWN_BROKER);
        }
        if (shareccStore == null) {
            shareccStore = ShareConfigChangeStore.getStore();
        }
        return shareccStore;
    }

    /**
     * Release the singleton instance of a Store object.
     * The store will be closed and any resources used by it released.
     * <p>
     * The next time <code>getStore()</code> is called, a new instance will
     * be instantiaed.
     * @param	cleanup	if true, the store will be cleaned up, i.e.
     *			redundant data removed.
     */
    public static synchronized void releaseStore(boolean cleanup) {

        if (store != null) {
            // this check is for tonga test so that the store
            // is not closed twice
            if (!store.isClosed()) {
                store.close(cleanup);
            }
            store = null;
        }

        if (shareccStore != null) {
            shareccStore.close();
            shareccStore = null;
        }
        isConfiguredFileStore = null; 
        txnLogEnabled = null;
        newTxnLogEnabled = null;
    }

    public static Store getStoreForTonga(String jdbcLockStoreProp, 
                                         String dbpoolNumConnProp)
                                         throws BrokerException {

        // For tonga test, get a store without a lock otherwise we'll
        // an error because the store is already locked by the broker!
        BrokerConfig config = Globals.getConfig();
        String type = config.getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        if (type.equals(Store.JDBC_STORE_TYPE)) {
            config.put(jdbcLockStoreProp, "false");
            // Limit the # of conn in the pool to 2
            config.put(dbpoolNumConnProp, "2");
        }
        return getStore();
    }
}
