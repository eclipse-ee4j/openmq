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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.util.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.PartitionNotFoundException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ProducerSpi;
import com.sun.messaging.jmq.util.log.Logger;

/**
 *
 */

//XXX - it would be nice to add metrics info
// unfortunately we dont know what producer a message
// comes from at this time
public class Producer extends ProducerSpi {
    
    transient Set destinations = null;
    private transient DestinationList DL = Globals.getDestinationList();
    private transient PartitionedStore pstore = null;

    /**
     */
    private Producer(ConnectionUID cuid, DestinationUID duid,
                     String id, PartitionedStore ps) {
        super(cuid, duid, id);
        this.pstore = ps;
    }
   
    public static Producer createProducer(DestinationUID duid,
              ConnectionUID cuid, String id, PartitionedStore ps) 
    {
        Producer producer = new Producer(cuid, duid, id, ps);
        Object old = allProducers.put(producer.getProducerUID(), producer);
        if (duid.isWildcard()) {
            wildcardProducers.add(producer.getProducerUID());
        }
        assert old == null : old;

        return producer;
    }


    public void destroyProducer() {
        if (getDestinationUID().isWildcard()) {
            wildcardProducers.remove(getProducerUID());
            // remove from each destination
            List[] dss = null;
            try {
                dss = DL.findMatchingIDs(pstore, getDestinationUID());
            } catch (PartitionNotFoundException e) {
                if (DEBUG) {
                logger.log(logger.INFO, 
                "Producer.destroyProducer on "+getDestinationUID()+": "+e.getMessage());
                }
                dss = new List[]{ new ArrayList<DestinationUID>() };
            }
            List duids = dss[0];
            Iterator itr = duids.iterator();
            while (itr.hasNext()) {
                DestinationUID duid = (DestinationUID)itr.next();
                Destination[] dd = DL.getDestination(pstore, duid);
                Destination d = dd[0];
                if (d != null) {
                   d.removeProducer(uid);
                }
            }
        } else {
            Destination[] dd = DL.getDestination(pstore, getDestinationUID());
            Destination d = dd[0];
            if (d != null) {
                d.removeProducer(uid);
            }
        }
        destroy();
    }

    public synchronized void destroy() {
        super.destroy();
        lastResumeFlowSizes.clear();
    }

    public boolean isWildcard() {
        return destination_uid.isWildcard();
    }

    public Set getDestinations() {
        if (this.destinations == null) {
            destinations = new HashSet();
            if (!destination_uid.isWildcard()) {
                destinations.add(destination_uid);
            } else {
                List[] ll = null;
                try {
                    ll = DL.findMatchingIDs(pstore, destination_uid);
                } catch (PartitionNotFoundException e) {
                    if (DEBUG) {
                    logger.log(logger.INFO, 
                    "Producer.getDestinations() on "+getDestinationUID()+": "+e.getMessage());
                    }
                    ll = new List[]{ new ArrayList<DestinationUID>() };
                }
                List l = ll[0];
                Iterator itr = l.iterator();
                while (itr.hasNext()) {
                    DestinationUID duid = (DestinationUID)itr.next();
                    destinations.add(duid);
                }
                    
            }
        }
        return destinations;
    }
}
