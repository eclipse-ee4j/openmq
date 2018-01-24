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
 * @(#)IMQDirectService.java	1.57 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.io.*;
import java.util.*;
import java.security.AccessControlException;
import javax.transaction.xa.Xid;

import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.UniqueID;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.data.handlers.AckHandler;
import com.sun.messaging.jmq.jmsserver.data.protocol.Protocol;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;

public class IMQDirectService extends IMQService
{

    /**
     * the list of connections which this service knows about
     * XXX make this accessible from IMQService ??
     */
    private ConnectionManager connectionList = Globals.getConnectionManager();

    private AuthCacheData authCacheData = new AuthCacheData();
    private JMSServiceImpl jmsservice = null;
    
    public IMQDirectService(String name, int type, int min, int max, boolean acc) {
        super(name, type);
        jmsservice = new JMSServiceImpl(this, Globals.getProtocol(), acc);
    }

    public JMSService getJMSService() {
        return jmsservice;    
    }

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        ht.put("JMSService", jmsservice.getDebugState());
        return ht;
    }

    // XXX - this implemenetation assumes that we have 1 distinct
    // listen thread per/service
    // revisit  (may not always be true)

    @Override
    public synchronized  void startService(boolean startPaused) {
        if (isServiceRunning()) {
            /*
             * this error should never happen in normal operation
             * if its does, we will need to add an error message 
             * to the resource bundle
             */
            logger.log(Logger.DEBUG, 
                       BrokerResources.E_INTERNAL_BROKER_ERROR, 
                       "unable to start service, already started.");
            return;
        }
        setState(ServiceState.STARTED);
        {
            String args[] = { getName(), 
                              "",
                              String.valueOf(getMinThreadpool()),
                              String.valueOf(getMaxThreadpool()) };
            logger.log(Logger.INFO, BrokerResources.I_SERVICE_START, args);
            try {
            logger.log(Logger.INFO, BrokerResources.I_SERVICE_USER_REPOSITORY,
                       AccessController.getInstance(
                       getName(), getServiceType()).getUserRepository(),
                       getName());
            } catch (BrokerException e) {
            logger.log(Logger.WARNING, 
                       BrokerResources.W_SERVICE_USER_REPOSITORY,
                       getName(), e.getMessage());
            }
        }
     
        if (startPaused) {
            setServiceRunning(false);
            setState(ServiceState.PAUSED);
	    /*
	     * CHECK: This used to be if'd by WORKAROUND_HTTP
	     */
                try {
                    Globals.getPortMapper().updateServicePort(name, 0);
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING, 
                          BrokerResources.E_INTERNAL_BROKER_ERROR, 
                          "starting paused service " + this, ex);
                }
        } else {
            setServiceRunning(true);
            setState(ServiceState.RUNNING);
        }
        notifyAll();
    }

    @Override
    public void stopService(boolean all) { 
        synchronized (this) {

            if (isShuttingDown()) {
                // we have already been called
                return;
            }

            String strings[] = { getName(), "" };
            if (all) {
                logger.log(Logger.INFO, 
                           BrokerResources.I_SERVICE_STOP, 
                           strings);
            } else if (!isShuttingDown()) {
                logger.log(Logger.INFO, 
                           BrokerResources.I_SERVICE_SHUTTINGDOWN, 
                           strings);
            } 
           
	    setShuttingDown(true);
        }

        if (this.getServiceType() == ServiceType.NORMAL) {
            List cons = connectionList.getConnectionList(this);
            Connection con = null;
            for (int i = cons.size()-1; i >= 0; i--) {
                con = (Connection)cons.get(i);
                con.stopConnection();
            }
        }

        synchronized (this) {
            setState(ServiceState.SHUTTINGDOWN);
            this.notifyAll();
        }

        if (!all) {
            return;
        }

        // set operation state to EXITING
        if (this.getServiceType() == ServiceType.NORMAL) {
            List cons = connectionList.getConnectionList(this);
            Connection con = null;
            for (int i = cons.size()-1; i >= 0; i--) {
                con = (Connection)cons.get(i);
                con.destroyConnection(true, GoodbyeReason.SHUTDOWN_BKR, 
                    Globals.getBrokerResources().getKString(
                        BrokerResources.M_SERVICE_SHUTDOWN));
            }
        }

        synchronized (this) {
            setState(ServiceState.STOPPED);
            this.notifyAll();
        }

        if (DEBUG) {
            logger.log(Logger.DEBUG, 
                "Destroying Service {0}", getName());
        }
    }

    @Override
    public void pauseService(boolean all) {

        if (!isServiceRunning()) {
            logger.log(Logger.DEBUG, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    "unable to pause service " 
                    + name + ", already paused.");
            return;
        }  

        String strings[] = { getName(), "" };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_PAUSE, strings);

        try {
            stopNewConnections();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, 
                      BrokerResources.E_INTERNAL_BROKER_ERROR, 
                      "pausing service " + this, ex);
        }
        setState(ServiceState.PAUSED);

        setServiceRunning(false);
    }

    @Override
    public void resumeService() {
        if (isServiceRunning()) {
             logger.log(Logger.DEBUG, 
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    "unable to resume service " 
                    + name + ", already running.");
           return;
        }
        String strings[] = { getName(), "" };
        logger.log(Logger.DEBUG, BrokerResources.I_SERVICE_RESUME, strings);
        try {
            startNewConnections();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, 
                      BrokerResources.E_INTERNAL_BROKER_ERROR, 
                      "pausing service " + this, ex);
        }
        setServiceRunning(true);

        synchronized (this) {
            setState(ServiceState.RUNNING);
            this.notifyAll();
        }
        
    }

    @Override
    public void updateService(int port, int min, int max) 
        throws IOException, PropertyUpdateException, BrokerException
    {
        Globals.getPortMapper().updateServicePort(name, 0);
        Globals.getPortMapper().removeService(name);
        Globals.getPortMapper().addService(name, "none", 
        	Globals.getConfig().getProperty(
        	IMQDirectServiceFactory.SERVICE_PREFIX + 
        	name + ".servicetype"),
        	0, getServiceProperties());
    }

    @Override
    public AuthCacheData getAuthCacheData() {
        return authCacheData;     
    }  

    @Override
    public void removeConnection(ConnectionUID uid, int reason, String str) {
        jmsservice.removeConnection(uid, reason, str);
    }

    @Override
    public boolean isDirect() {
        return (true);
    }
}
