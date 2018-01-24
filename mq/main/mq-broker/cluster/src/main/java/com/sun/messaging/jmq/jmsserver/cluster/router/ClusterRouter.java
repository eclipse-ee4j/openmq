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
 * @(#)ClusterRouter.java	1.16 07/23/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.router;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.io.*;


public interface ClusterRouter
{
    public void forwardMessage(PacketReference ref, Collection consumers);

    /* REPLACE THE NEXT SEVERAL PROTOCOL MESSAGES WITH handleCtrlMsg
     */

    public void addConsumer(Consumer c) 
       throws BrokerException, IOException, SelectorFormatException;

    public void removeConsumer(com.sun.messaging.jmq.jmsserver.core.ConsumerUID c,
        Map<TransactionUID, LinkedHashMap<SysMessageID, Integer>> pendingMsgs, boolean cleanup)
        throws BrokerException, IOException;

    public void removeConsumers(ConnectionUID uid)
       throws BrokerException, IOException;

    public void brokerDown(com.sun.messaging.jmq.jmsserver.core.BrokerAddress ba)
       throws BrokerException, IOException;

    public void shutdown();

    /*
     * END REPACEMENT
     */

    public void handleJMSMsg(Packet p, Map<ConsumerUID, Integer> consumers,
                             BrokerAddress sender,
                             boolean sendMsgRedeliver)
                             throws BrokerException;

    public void handleAck(int ackType, SysMessageID sysid, ConsumerUID cuid, 
                          Map optionalProps) throws BrokerException;

    public void handleAck2P(int ackType, SysMessageID[] sysids, ConsumerUID[] cuids, 
                            Map optionalProps, Long txnID, 
                            com.sun.messaging.jmq.jmsserver.core.BrokerAddress txnHomeBroker)
                            throws BrokerException;

    public void handleCtrlMsg(int type, HashMap props)
                              throws BrokerException;

   public Hashtable getDebugState();
}
