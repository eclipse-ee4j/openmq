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

package com.sun.messaging.jmq.jmsserver.cluster.manager;

import java.util.*;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;


/**
 * This class extends ClusterManagerImpl and is used to obtain and
 * distribute cluster information in an HA cluster.
 */

@Service(name = "com.sun.messaging.jmq.jmsserver.cluster.manager.BasicAutoClusterManagerImpl")
@Singleton
public class BasicAutoClusterManagerImpl extends ClusterManagerImpl 
{

    /**
     */
    public BasicAutoClusterManagerImpl() throws BrokerException {
       super();
    }

    public String initialize(MQAddress address) throws BrokerException {
        if (Globals.getClusterID() == null) {
            throw new BrokerException("imq.cluster.clusterid must set");
        }
        String r = super.initialize(address);
        if (!(allBrokers instanceof AutoClusterBrokerMap)) {
            throw new BrokerException(
            "Cluster configuration inconsistent: unexpected class "+allBrokers.getClass());
        }
        return r;
    }

    protected void setupListeners() {
        config.addListener(TRANSPORT_PROPERTY, this);
        config.addListener(HOST_PROPERTY, this);
        config.addListener(PORT_PROPERTY, this);
    }

    /**
     * to be called internally in cluster manager framework
     */
    public ClusteredBroker newClusteredBroker(MQAddress url,
                                   boolean isLocal, UID sid)
                                   throws BrokerException {

       ClusteredBroker cb = super.newClusteredBroker(url, isLocal, sid);
       ((ClusteredBrokerImpl)cb).setConfigBroker(true);
       return cb;
    }

    /**
     * Reload the cluster properties from config 
     *
     */
    public void reloadConfig() throws BrokerException {
        if (!initialized)
            throw new RuntimeException("Cluster not initialized");

        String[] props = { CLUSTERURL_PROPERTY };
        config.reloadProps(Globals.getConfigName(), props, false);
    }

    /**
     */
    protected String addBroker(MQAddress url, 
        boolean isLocal, boolean isConfig, UID uid)
        throws NoSuchElementException, BrokerException {

        if (!initialized) {
            throw new RuntimeException("Cluster not initialized");
        }

        String name = null;
        ClusteredBroker cb = null;
        if (isLocal) {
            synchronized(allBrokers) {
                name  = lookupBrokerID(url);
                if (name == null) {
                    cb = newClusteredBroker(url, isLocal, uid);
                    name = cb.getBrokerName();
                } else {
                    cb = getBroker(name);
                }
                synchronized(allBrokers) {
                    allBrokers.put(name, cb);
                }
            }
        } else {
            name = lookupBrokerID(url);
            if (name != null) {
                cb = getBroker(name);
            }
            if (name == null || cb == null) {
                throw new NoSuchElementException("Unknown broker "+url);
            }
        }
        if (uid != null) {
            cb.setBrokerSessionUID(uid);
        }
        if (isLocal) { 
           cb.setStatus(BrokerStatus.ACTIVATE_BROKER, null);
        }
        brokerChanged(ClusterReason.ADDED,
                      cb.getBrokerName(), null, cb, uid, null); 
        return name;
    }

    protected Map initAllBrokers(MQAddress myaddr) throws BrokerException {

        String cstr = Globals.getConfig().getProperty
                      (Globals.AUTOCLUSTER_BROKERMAP_CLASS_PROP);
        if (cstr == null) {
            return super.initAllBrokers(myaddr);
        }
        try {
            if (Globals.isNucleusManagedBroker()) {
                AutoClusterBrokerMap map = Globals.getHabitat().
                           getService(AutoClusterBrokerMap.class, cstr);
                if (map == null) {
                    throw new BrokerException("Class "+cstr+" not found");
                }
                map.init(this, myaddr);
                return (Map)map;
            } else {
                Class c = Class.forName(cstr);
                Class[] paramTypes = { ClusterManagerImpl.class, MQAddress.class };
                Constructor cons = c.getConstructor(paramTypes);
                Object[] paramArgs = { this, myaddr }; 
                return (Map)cons.newInstance(paramArgs);
            }
        } catch (Exception e) {
             throw new BrokerException(e.getMessage(), e);
        }
    }

    protected LinkedHashSet parseBrokerList()
    throws MalformedURLException {

        String val = config.getProperty(AUTOCONNECT_PROPERTY);
        if (val != null) {
             logger.log(Logger.INFO,
                 BrokerResources.W_IGNORE_PROP_SETTING,
                 AUTOCONNECT_PROPERTY+"="+val);
        }

        val = config.getProperty(Globals.IMQ
                         + ".cluster.brokerlist.manual");
        if (val != null) {
             logger.log(Logger.INFO,
                 BrokerResources.W_IGNORE_PROP_SETTING,
                 Globals.IMQ + ".cluster.brokerlist.manual"+"="+val);
        }
        LinkedHashSet brokers = new LinkedHashSet();
        synchronized(allBrokers) {
            Iterator itr = allBrokers.values().iterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                ClusteredBroker b = (ClusteredBroker)o;
                if (!b.isLocalBroker()) {
                    brokers.add(b.getBrokerURL());
                }
            }
        }
        return brokers;
   }

    public String lookupBrokerID(MQAddress address) {

        if (!initialized) {
            throw new RuntimeException("Cluster manager is not initialized");
        }
        try {
             synchronized(allBrokers) {
                 ((AutoClusterBrokerMap)allBrokers).updateMap();
             }
        } catch (BrokerException e) {
             logger.logStack(logger.WARNING, e.getMessage(), e);
        }
        return super.lookupBrokerID(address);
    }

    public Iterator getConfigBrokers() {
        return getKnownBrokers(true);
    }

    public int getConfigBrokerCount() {
        return super.getKnownBrokerCount();
    }

    public Iterator getKnownBrokers(boolean refresh) {

        if (!initialized) {
            throw new RuntimeException("Cluster manager is not initialized");
        }

        if (refresh) {
            try {
                synchronized(allBrokers) {
                    ((AutoClusterBrokerMap)allBrokers).updateMap(true);
                }
            } catch (BrokerException e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            }
        }
        return super.getKnownBrokers(refresh);
    }

    public ClusteredBroker getBroker(String brokerid) {

        ClusteredBroker cb = super.getBroker(brokerid);
        if (cb != null) {
            return cb;
        }
        try {
             synchronized(allBrokers) {
                 ((AutoClusterBrokerMap)allBrokers).updateMap(true);
             }
        } catch (BrokerException e) {
             logger.logStack(logger.WARNING, e.getMessage(), e);
        }
        return super.getBroker(brokerid);
    }
}
