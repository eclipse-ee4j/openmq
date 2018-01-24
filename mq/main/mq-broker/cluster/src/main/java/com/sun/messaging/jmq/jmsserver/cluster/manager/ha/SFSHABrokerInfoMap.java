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
import java.util.HashMap;
import java.util.Iterator;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAClusteredBroker;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterReason;
import com.sun.messaging.jmq.jmsserver.cluster.manager.AutoClusterBrokerMap;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 *  For shared file system store.
 */
public class SFSHABrokerInfoMap extends HashMap implements AutoClusterBrokerMap
{
        transient SFSHAClusterManagerImpl parent = null;

        /**
         * Create an instance of HAMap
         * @throws BrokerException 
         */
        public SFSHABrokerInfoMap(SFSHAClusterManagerImpl manager)
        throws BrokerException {
            init(manager, null);
        }

        public void init(ClusterManager mgr, MQAddress addr) throws BrokerException {
            this.parent = (SFSHAClusterManagerImpl)mgr;

            Map map = Globals.getStore().getAllBrokerInfos();
            Iterator itr = map.entrySet().iterator();

            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry)itr.next();
                String key = (String)entry.getKey();
                HABrokerInfo bi = (HABrokerInfo)entry.getValue();
                HAClusteredBroker cb =  new SFSHAClusteredBrokerImpl(bi.getId(), bi, parent);
                put(key,cb);
                parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(),
                                     null, cb, cb.getBrokerSessionUID(), null);
            }
        }


        /**
         * Method which reloads the contents of this map from the
         * current information in the JDBC store.
         * @throws BrokerException 
         */
        public void updateMap() 
            throws BrokerException
        {
            updateMap(false);
        }

        public void updateMap(boolean all)
            throws BrokerException
        {
            if (all) {
                updateHAMapForState(null);
            } else {
                updateHAMapForState(BrokerState.OPERATING);
            } 
        }

        private void updateHAMapForState(BrokerState state)
            throws BrokerException
        {
            Map map = null;
            if (state == null) {
			    map = Globals.getStore().getAllBrokerInfos();
            } else {
			    map = Globals.getStore().getAllBrokerInfoByState(state);
            }

            Iterator itr = map.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry entry = (Map.Entry)itr.next();
                String key = (String)entry.getKey();
                HABrokerInfo bi = (HABrokerInfo)entry.getValue();
                HAClusteredBrokerImpl impl = (HAClusteredBrokerImpl)get(key);
                if (impl == null) {
                    HAClusteredBroker cb =
                        new SFSHAClusteredBrokerImpl(bi.getId(), bi, parent);
                    put(key,cb);
                    parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(),
                                         null, cb, cb.getBrokerSessionUID(), null);
                } else {
                    impl.update(bi);
                }
            }

            if (state == null) {
                itr = entrySet().iterator();
                while (itr.hasNext()) {
                    Map.Entry entry = (Map.Entry)itr.next();
                    String key = (String)entry.getKey();
                    if (!map.containsKey(key)) {
                        itr.remove();
                        HAClusteredBrokerImpl impl = (HAClusteredBrokerImpl)entry.getValue();
                        parent.brokerChanged(ClusterReason.REMOVED, impl.getBrokerName(),
                                             impl, null, impl.getBrokerSessionUID(), null);
                    }
                }
            }
        }

          
        /**
         * Retrieves the HAClusteredBroker associated with the passed in 
         * broker id. 
         * If the id is not found in the hashtable, the store will be checked.
         * @param key the brokerid to lookup
         * @return the HAClusteredBroker object (or null if one can't be found)
         */
         public Object get(Object key) {
             return get(key, false);
         }

        /**
         * Retrieves the HAClusteredBroker associated with the passed in 
         * broker id. 
         * If the id is not found in the hashtable, the store will be checked.
         * @param key the brokerid to lookup
         * @param update update against store
         * @return the HAClusteredBroker object (or null if one can't be found)
         */
        public Object get(Object key, boolean update) {
            // always check against the backing store
            Object o = super.get(key);
            if (o == null || update) {
                try {
                    HABrokerInfo m= Globals.getStore().getBrokerInfo((String)key);
                    if (m != null && o == null) {
                        HAClusteredBroker cb =  new SFSHAClusteredBrokerImpl((String)key, m, parent);
                        put(key,cb);
                        parent.brokerChanged(ClusterReason.ADDED, cb.getBrokerName(),
                                             null, cb, cb.getBrokerSessionUID(), null);
                        o = cb;
                     }
                     if (m != null && update) {
                         ((HAClusteredBrokerImpl)o).update(m);
                     }
                 } catch (BrokerException ex) {
                     Globals.getLogger().logStack(Logger.WARNING,
                     "Exception while creating broker entry " + key , ex);
                }
            }
            return o;
        }
}

