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

package com.sun.messaging.jmq.jmsserver.cluster.manager;

import java.util.Map;
import java.util.HashMap;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;

/**
 */
@Contract
@Singleton
public interface AutoClusterBrokerMap 
{
      public void init(ClusterManager mgr, MQAddress addr) throws BrokerException;

        /**
         * Method which reloads the contents of this map from
         * current auto-clustering source.
         *
         * @throws BrokerException 
         */
        public void updateMap() throws BrokerException;

        public void updateMap(boolean all) throws BrokerException;

        public Object get(Object key);

        /**
         * Retrieves a ClusteredBroker associated with the passed in 
         * broker id. 
         * If the id is not found in the hashtable, the auto-clustering
         * source will be checked.
         *
         * @param key the brokerid to lookup
         * @param update update against store
         * @return the ClusteredBroker object (or null if one can't be found)
         */
        public Object get(Object key, boolean update); 
}

