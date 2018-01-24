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

package com.sun.messaging.jmq.jmsserver.plugin.spi;

import java.util.List;
import java.util.Hashtable;
import java.util.Iterator;
import java.io.IOException;

import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.PartitionNotFoundException;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.AdminDataHandler;

public abstract class CoreLifecycleSpi { 

    public static final String GFMQ = "GLASSFISH_MQ"; 
    public static final String CHMP = "COHERENCE_MESSAGE_PATTERN";

    protected PacketRouter pktr = null; 

    public CoreLifecycleSpi() {}

    public abstract String getType();

    public PacketRouter getPacketRouter() {
        return pktr;
    }

    public abstract void initDestinations() throws BrokerException;

    public abstract void initSubscriptions() throws BrokerException;

    public abstract void initHandlers(PacketRouter prt, ConnectionManager cm,
        PacketRouter adminprt, AdminDataHandler adminh)
        throws BrokerException;

    public abstract void cleanup(); 

    public DestinationList getDestinationList() {
        return null;
    }

    public int getMaxProducerBatch() {
        return 0;
    }

    /********************************************
     * SessionOp static method
     **********************************************/

    public abstract SessionOpSpi newSessionOp(Session ss); 

    /********************************************
     * Producer static methods
     **********************************************/

    public abstract Hashtable getProducerAllDebugState(); 

    public abstract void clearProducers();

    public abstract Iterator getWildcardProducers();

    public abstract int getNumWildcardProducers();

    public abstract String checkProducer(ProducerUID uid);

    public abstract void updateProducerInfo(ProducerUID uid, String str);

    public abstract Iterator getAllProducers();

    public abstract int getNumProducers();

    public abstract ProducerSpi getProducer(ProducerUID uid);

    public abstract ProducerSpi destroyProducer(ProducerUID uid, String info);

    public abstract ProducerSpi getProducer(String creator);

    /***********************************************
     * Destination static methods
     ************************************************/

    public abstract DestinationSpi[] getDestination(PartitionedStore ps, DestinationUID uid);

    public abstract DestinationSpi[] getDestination(PartitionedStore ps, String name, boolean isQueue)
    throws IOException, BrokerException; 

    public abstract DestinationSpi[] getDestination(PartitionedStore ps, DestinationUID duid, int type,
                                                  boolean autocreate, boolean store)
                                                  throws IOException, BrokerException;

    public abstract DestinationSpi[] getDestination(PartitionedStore ps, String name, int type,
                                                boolean autocreate, boolean store)
                                                throws IOException, BrokerException ;
    public abstract DestinationSpi[] createTempDestination(PartitionedStore ps, String name,
        int type, ConnectionUID uid, boolean store, long time)
        throws IOException, BrokerException;

    public abstract List[] findMatchingIDs(PartitionedStore ps, DestinationUID wildcarduid) 
    throws PartitionNotFoundException;
    
    public abstract DestinationSpi[] removeDestination(PartitionedStore ps,
        String name, boolean isQueue, String reason)
        throws IOException, BrokerException;

    public abstract DestinationSpi[] removeDestination(PartitionedStore ps, DestinationUID uid,
        boolean notify, String reason)
        throws IOException, BrokerException; 

    public abstract boolean canAutoCreate(boolean queue);

    /********************************************
     * Consumer static methods
     **********************************************/

    public abstract ConsumerSpi getConsumer(ConsumerUID uid);

    public abstract int calcPrefetch(ConsumerSpi consumer,  int cprefetch);
}
