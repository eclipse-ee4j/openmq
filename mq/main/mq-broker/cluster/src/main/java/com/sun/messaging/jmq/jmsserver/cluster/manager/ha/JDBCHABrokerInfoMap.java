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

package com.sun.messaging.jmq.jmsserver.cluster.manager.ha;

import java.util.Map;
import java.io.Serial;
import java.util.HashMap;
import java.util.Iterator;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterReason;
import com.sun.messaging.jmq.jmsserver.cluster.manager.AutoClusterBrokerMap;

/**
 * we need to verify the list of brokers in the following situations: - a broker is added - a broker is removed (does
 * NOT update database) - the first time a broker is started
 *
 * A subclass of Map which knows how to populate itself from the jdbc store.
 */
public class JDBCHABrokerInfoMap extends HashMap implements AutoClusterBrokerMap {
    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 5277142587633539056L;
    transient HAClusterManagerImpl parent = null;

    /**
     * Create an instance of HAMap.
     *
     * @throws BrokerException if something goes wrong loading the jdbc store.
     */
    public JDBCHABrokerInfoMap(HAClusterManagerImpl manager) throws BrokerException {
        init(manager, null);
    }

    @Override
    public void init(ClusterManager mgr, MQAddress addr) throws BrokerException {
        this.parent = (HAClusterManagerImpl) mgr;

        // OK, load everything in the store that is OPERATING
        Map map = Globals.getStore().getAllBrokerInfos();
        Iterator itr = map.entrySet().iterator();

        while (itr.hasNext()) {
            Map.Entry entry = (Map.Entry) itr.next();
            String key = (String) entry.getKey();
            HABrokerInfo bi = (HABrokerInfo) entry.getValue();
            HAClusteredBroker cb = new HAClusteredBrokerImpl(bi.getId(), bi, parent);
            put(key, cb);
            parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(), null, cb, cb.getBrokerSessionUID(), null);
        }
    }

    /**
     * Method which reloads the contents of this map from the current information in the JDBC store.
     *
     * @throws BrokerException if something goes wrong loading the jdbc store.
     */
    @Override
    public void updateMap() throws BrokerException {
        updateMap(false);
    }

    @Override
    public void updateMap(boolean all) throws BrokerException {
        if (all) {
            updateHAMapForState(null);
        } else {
            updateHAMapForState(BrokerState.OPERATING);
        }
    }

    private void updateHAMapForState(BrokerState state) throws BrokerException {
        // OK, load everything in the store that is the right state
        Map map;
        if (state == null) {
            // Load everything if state is not specified
            map = Globals.getStore().getAllBrokerInfos();
        } else {
            map = Globals.getStore().getAllBrokerInfoByState(state);
        }

        Iterator itr = map.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry entry = (Map.Entry) itr.next();
            String key = (String) entry.getKey();
            HABrokerInfo bi = (HABrokerInfo) entry.getValue();
            HAClusteredBrokerImpl impl = (HAClusteredBrokerImpl) get(key);
            if (impl == null) {
                HAClusteredBroker cb = new HAClusteredBrokerImpl(bi.getId(), bi, parent);
                put(key, cb);
                parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(), null, cb, cb.getBrokerSessionUID(), null);
            } else { // update
                // already exists
                impl.update(bi);
            }
        }

        // Sanity check when do load everything from the DB; remove from
        // memory if any broker has been removed from the broker table
        if (state == null) {
            itr = entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry) itr.next();
                String key = (String) entry.getKey();
                if (!map.containsKey(key)) {
                    itr.remove();
                    HAClusteredBrokerImpl impl = (HAClusteredBrokerImpl) entry.getValue();
                    parent.brokerChanged(ClusterReason.REMOVED, impl.getBrokerName(), impl, null, impl.getBrokerSessionUID(), null);
                }
            }
        }
    }

    /**
     * Retrieves the HAClusteredBroker associated with the passed in broker id. If the id is not found in the hashtable, the
     * database will be checked.
     *
     * @param key the brokerid to lookup
     * @return the HAClusteredBroker object (or null if one can't be found)
     */
    @Override
    public Object get(Object key) {
        return get(key, false);
    }

    /**
     * Retrieves the HAClusteredBroker associated with the passed in broker id. If the id is not found in the hashtable, the
     * database will be checked.
     *
     * @param key the brokerid to lookup
     * @param update update against store
     * @return the HAClusteredBroker object (or null if one can't be found)
     */
    @Override
    public Object get(Object key, boolean update) {
        Object o = super.get(key);
        if (o == null || update) {
            try {
                HABrokerInfo m = Globals.getStore().getBrokerInfo((String) key);
                if (m != null && o == null) {
                    HAClusteredBroker cb = new HAClusteredBrokerImpl((String) key, m, parent);
                    put(key, cb);
                    parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(), null, cb, cb.getBrokerSessionUID(), null);
                    o = cb;
                }
                if (m != null && update) {
                    ((HAClusteredBrokerImpl) o).update(m);
                }
            } catch (BrokerException ex) {
                Globals.getLogger().logStack(Logger.WARNING, "Exception while creating broker entry " + key, ex);
            }
        }
        return o;
    }
}
