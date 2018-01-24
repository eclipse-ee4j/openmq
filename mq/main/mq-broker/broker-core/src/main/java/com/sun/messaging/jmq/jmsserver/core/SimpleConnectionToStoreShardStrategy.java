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

package com.sun.messaging.jmq.jmsserver.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

/**
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.core.SimpleConnectionToStoreShardStrategy") 
@PerLookup
public class SimpleConnectionToStoreShardStrategy implements ConnectionToStoreShardStrategy
{
    public PartitionedStore chooseStorePartition(Collection<ShardStrategyContext> pscs)
    throws BrokerException {

        ShardStrategyContext psc = Collections.min(pscs, 

                 new Comparator<ShardStrategyContext>() {
                 public int compare(ShardStrategyContext o1, ShardStrategyContext o2) {
                        if (o1.getConnectionCount() < o2.getConnectionCount()) {
                            return -1;
                        }
                        if (o1.getConnectionCount() > o2.getConnectionCount()) {
                            return 1;
                        }
                        return 0;
                    }

		});
        return psc.getPartitionedStore();
    }
}
