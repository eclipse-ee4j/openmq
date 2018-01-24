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

package com.sun.messaging.jmq.jmsserver.cluster.manager.ha;

import java.util.*;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.persist.api.MigratableStoreUtil;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.persist.api.HABrokerInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.multibroker.BrokerInfo;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;

// XXX FOR TEST CLASS
import java.io.*;


/**
 * This class extends ClusterManagerImpl and is used to obtain and
 * distribute cluster information in an HA cluster.
 */

@Service(name = "com.sun.messaging.jmq.jmsserver.cluster.manager.ha.SFSHAClusterManagerImpl")
@Singleton
public class SFSHAClusterManagerImpl extends HAClusterManagerImpl 
{

    /**
     * The brokerid associated with the local broker.
     * The local broker is running in the current vm.
     */

    //private String localBrokerId = null;

   /**
    */
   public SFSHAClusterManagerImpl() throws BrokerException
   {
       super();
   }

   public String initialize(MQAddress address)
   throws BrokerException {

       Globals.getStore().updateBrokerInfo(Globals.getBrokerID(),
           HABrokerInfo.UPDATE_URL, null, address.toString());

       String bid = super.initialize(address);
       UID ss = Globals.getStoreSession();
       ((SFSHAClusteredBrokerImpl)getLocalBroker()).setStoreSessionUID(ss);
       return bid;
   }

   protected void checkStore() throws BrokerException {
       if (!StoreManager.isConfiguredBDBSharedFS()) {
           throw new BrokerException(
                 Globals.getBrokerResources().getKString(
                 BrokerResources.E_HA_CLUSTER_INVALID_STORE_CONFIG));
       }
   }

   protected HAClusteredBroker newHAClusteredBroker(String brokerid,
             MQAddress url, int version, BrokerState state, UID session)
                        throws BrokerException {
       return new SFSHAClusteredBrokerImpl(brokerid, url,
                      version, state, session, this);
   }

   protected ClusteredBroker updateBrokerOnActivation(ClusteredBroker broker,
                                                      Object userData) {
       ((SFSHAClusteredBrokerImpl)broker).setStoreSessionUID(
           ((BrokerInfo)userData).getBrokerAddr().getStoreSessionUID());
       ((SFSHAClusteredBrokerImpl)broker).setRemoteBrokerStateOnActivation();
       return broker;
   }

   protected ClusteredBroker updateBrokerOnDeactivation(ClusteredBroker broker,
                                                        Object userData) {
       ((SFSHAClusteredBrokerImpl)broker).setRemoteBrokerStateOnDeactivation();
       return broker;
   }

   /**
    * finds the brokerid associated with the given session.
    *
    * @param uid is the session uid to search for
    * @return the uid associated with the session or null we cant find it.
    */
   public String lookupStoreSessionOwner(UID uid) {

       try {
           if (Globals.getMyAddress().getStoreSessionUID().equals(uid)) {
               return Globals.getBrokerID();
           }
           if (Globals.getStore().ifOwnStoreSession(uid.longValue(), (String)null)) {
               return Globals.getBrokerID(); 
           }
           return Globals.getClusterBroadcast().lookupStoreSessionOwner(uid);
       } catch (Exception ex) {
           logger.logStack(logger.WARNING, ex.getMessage(), ex);
       }
       return null;
   }

   protected Map newHABrokerInfoMap() throws BrokerException {
       return new SFSHABrokerInfoMap(this);
   }

   public ClusteredBroker getBrokerByNodeName(String nodeName) 
   throws BrokerException {

       if (!initialized) {
           throw new RuntimeException("Cluster not initialized");
       }
       HAClusteredBrokerImpl cb = null;
       synchronized (allBrokers) {
           Iterator itr = allBrokers.values().iterator();
           while (itr.hasNext()) {

               cb = (HAClusteredBrokerImpl)itr.next();
               String instn = cb.getInstanceName();
               UID ss = cb.getStoreSessionUID();
               if (instn != null && ss != null) {
                   if (MigratableStoreUtil.makeEffectiveBrokerID(instn, ss).equals(nodeName)) {
                       return cb;
                   }
               }
           }
       }
       return null;
   }

}
