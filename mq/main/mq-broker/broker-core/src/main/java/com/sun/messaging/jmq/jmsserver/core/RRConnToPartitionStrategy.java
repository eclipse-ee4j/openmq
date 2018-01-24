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

import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.Comparator;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

/**
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.core.RRConnToPartitionStrategy")
@PerLookup
public class RRConnToPartitionStrategy implements ConnToPartitionStrategy
{
    private long lastid = 0L;

    public PartitionedStore chooseStorePartition(List<ConnToPartitionStrategyContext> pscs)
    throws BrokerException {

        if (pscs == null || pscs.size() == 0) {
            return null;
        }
        Collections.sort(pscs, 

                 new Comparator<ConnToPartitionStrategyContext>() {
                 public int compare(ConnToPartitionStrategyContext o1,
                                    ConnToPartitionStrategyContext o2) {

                        long v1 = o1.getPartitionedStore().getPartitionID().longValue();
                        long v2 = o2.getPartitionedStore().getPartitionID().longValue();

                        if (v1 < v2) {
                            return -1;
                        }
                        if (v1 > v2) {
                            return 1;
                        }
                        return 0;
                    }

		 });

        PartitionedStore ps = null;
        synchronized(this) {
            if (lastid == 0L) {
                ps = pscs.get(0).getPartitionedStore(); 
                lastid = ps.getPartitionID().longValue();
                return ps;
            }
            Iterator<ConnToPartitionStrategyContext> itr = pscs.iterator();
            while (itr.hasNext()) {
                ps = itr.next().getPartitionedStore();
                long id = ps.getPartitionID().longValue();
                if (id > lastid) { 
                    lastid = id;
                    return ps;
                }
            }
            ps = pscs.get(0).getPartitionedStore(); 
            lastid = ps.getPartitionID().longValue();
            return ps;
        }
    }
}
