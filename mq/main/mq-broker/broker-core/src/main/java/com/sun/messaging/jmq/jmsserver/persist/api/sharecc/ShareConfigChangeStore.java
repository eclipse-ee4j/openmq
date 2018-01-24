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

package com.sun.messaging.jmq.jmsserver.persist.api.sharecc;

import java.util.List;
import java.util.Properties;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;

/**
 * This class contains static methods to obtain a singleton instance
 * for storing and retrieving change records of durable subscriptions
 * and administratively created destinations needed by a conventional
 * cluster that uses a shared store
 */

@Contract
@Singleton
public abstract class ShareConfigChangeStore {

    private static boolean DEBUG = false;

    public static final String CLUSTER_SHARECC_PROP_PREFIX = Globals.IMQ + ".cluster.sharecc";
    public static final String STORE_TYPE_PROP = Globals.IMQ + ".cluster.sharecc.persist";
    public static final String DEFAULT_STORE_TYPE = Store.JDBC_STORE_TYPE;

    public static final String CREATE_STORE_PROP = STORE_TYPE_PROP+"Create";
    public static final boolean CREATE_STORE_PROP_DEFAULT = Store.CREATE_STORE_PROP_DEFAULT;

    private static final String CLASS_PROP_SUFFIX = ".class";


    static private final String DEFAULT_JDBCSTORE_CLASS =
	"com.sun.messaging.jmq.jmsserver.persist.jdbc.sharecc.JDBCShareConfigChangeStore";

    // Singleton Store instance
    static private ShareConfigChangeStore store = null;

    public static boolean getDEBUG() {
        return DEBUG;
    }

    /**
     * Return a singleton instance of a Store object.
     */
    public static synchronized ShareConfigChangeStore 
    getStore() throws BrokerException {

        if (store != null) {
            return store;
        }

        if (BrokerStateHandler.isShuttingDown()) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_SHUTTING_DOWN_BROKER),
                    BrokerResources.X_SHUTTING_DOWN_BROKER);
        }

        BrokerConfig config = Globals.getConfig();

        String type = config.getProperty(STORE_TYPE_PROP, DEFAULT_STORE_TYPE);
        if (!type.equalsIgnoreCase(DEFAULT_STORE_TYPE)) {
            throw new BrokerException(
            "Not supported"+STORE_TYPE_PROP+"="+type);
        }

        String classprop = STORE_TYPE_PROP + "."+type + CLASS_PROP_SUFFIX;
        String classname = config.getProperty(classprop);
	    if (classname == null || classname.equals("")) {
            classname = DEFAULT_JDBCSTORE_CLASS;
        } else if (!classname.equals(DEFAULT_JDBCSTORE_CLASS)) {
            throw new BrokerException(
            "Not supported "+classprop+"="+classname);
        }

        try {

            if (Globals.isNucleusManagedBroker()) {
                store = Globals.getHabitat().
                            getService(ShareConfigChangeStore.class, classname);
                if (store == null) {
                    throw new BrokerException("Class "+classname+" not found");
                }
            } else {
                store = (ShareConfigChangeStore)Class.forName(
                         classname).newInstance();
           }
        } catch (Exception e) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
            BrokerResources.E_FAIL_OPEN_SHARECC_STORE, e.getMessage()), e);
        }

        return store;
    }

    /**
     * Release the singleton instance 
     * <p>
     * The next time <code>getStore()</code> is called, a new instance will
     * be instantiated.
     * @param	cleanup	if true, the store will be cleaned up, i.e.
     *			redundant data removed.
     */
    public static synchronized void 
    releaseStore(boolean cleanup) {

	    if (store != null) {
            store.close();
	    }
	    store = null;
    }

    /**
     * @param rec the record to be inserted
     * @param sync true sync to disk
     * @param return the inserted record
     */
    public ChangeRecordInfo storeChangeRecord(ChangeRecordInfo rec, boolean sync)
                                              throws BrokerException {
        throw new UnsupportedOperationException("Unsupported operation");

    }

    
    /**
     * @param rec the reset record to be inserted
     * @param canExist throw exception if the reset record already exists
     * @param sync true sync to disk
     */
    public void storeResetRecord(ChangeRecordInfo rec, boolean canExist, boolean sync)
                                 throws BrokerException {
        throw new UnsupportedOperationException("Unsupported operation");

    }

    /**
     * @param seq get records whose sequence number > seq
     */
    public List<ChangeRecordInfo> getChangeRecordsSince(Long seq, String resetUUID, boolean canReset)
    throws BrokerException {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public List<ChangeRecordInfo> getAllChangeRecords() throws BrokerException {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public void clearAllChangeRecords(boolean sync) throws BrokerException {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public abstract Properties getStoreShareProperties();

    public abstract String getVendorPropertySetting();

    public abstract void close();

}
