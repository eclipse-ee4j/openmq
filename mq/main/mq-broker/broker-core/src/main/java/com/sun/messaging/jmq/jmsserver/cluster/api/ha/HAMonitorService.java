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

package com.sun.messaging.jmq.jmsserver.cluster.api.ha;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import org.jvnet.hk2.annotations.Contract;
import javax.inject.Singleton;

/**
 */
@Contract
@Singleton
public interface HAMonitorService {

     public void init(String brokerID, MQAddress brokerURL, 
         boolean resetTakeoverThenExit) throws Exception;

     /**
      * @return true if in takeover 
      */
     public boolean inTakeover();

     /**
      * @return in seconds 
      */
     public int getMonitorInterval(); 

     /**
      * @return true if d is a destination being taken over 
      */
     public boolean checkTakingoverDestination(Destination d);

     /**
      * @return true if p is a message being taken over
      */
     public boolean checkTakingoverMessage(Packet p);

     /**
      * @return remote broker id running on host:port
      */
     public String getRemoteBrokerIDFromPortMapper(
            String host, int port, String brokerID); 

     /**
      */
     public void takeoverBroker(HAClusteredBroker cb, Object extraInfo1,
                               Object extraInfo2, boolean force)
                               throws BrokerException; 


    /**
     * @return host:port string of the broker that takes over this broker
     *
     * Status code of exception thrown is important
     */
    public String takeoverME(HAClusteredBroker cb,
                           String brokerID, Long syncTimeout)
                           throws BrokerException; 

    public boolean isTakingoverTarget(String brokerID, UID storeSession); 
}
