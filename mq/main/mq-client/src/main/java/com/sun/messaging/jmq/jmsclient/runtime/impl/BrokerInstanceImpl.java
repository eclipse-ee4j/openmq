/*
 * Copyright (c) 2014, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2025 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsclient.runtime.impl;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import jakarta.jms.JMSException;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQDirectService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;
import com.sun.messaging.jmq.jmsservice.JMSBroker;
import com.sun.messaging.jmq.jmsservice.JMSDirectBroker;
import com.sun.messaging.jmq.jmsservice.JMSService;

@SuppressWarnings("JdkObsolete")
public class BrokerInstanceImpl implements DirectBrokerInstance {

    private static final String BROKER_PROCESS = "com.sun.messaging.jmq.jmsserver.BrokerProcess";

    private static final String DIRECT_BROKER_PROCESS2 = "com.sun.messaging.jmq.jmsserver.DualThreadDBP";

    private static final String DIRECT_BROKER_PROCESS = "com.sun.messaging.jmq.jmsserver.DirectBrokerProcess";

    private BrokerEventListener evlistener = null;
    private Properties props = null;

    private JMSBroker bkr = null;

    private boolean running = false;

    private boolean isShutdown = false;

    private static BrokerInstanceImpl soleInstance;

    /**
     * default is direct enabled.
     */
    private static boolean isDirect = true;

    /**
     * If this is true, the producer thread is used by the broker.
     *
     * "imqAckOnAcknowledge" and "imqAckOnProduce" are turned off.
     *
     */
    public static volatile boolean isTwoThread = true;

    /**
     * If this is true then if twoThread mode is set as well then replies will be sent synchronously using a ThreadLocal
     *
     * Has no meaning unless isTwoThread is true
     */
    public static volatile boolean isTwoThreadSyncReplies = true;

    static {
        // NOTE that if you change the default values for these properties, update the logging in addStartupMessages() as well

        // disable direct mode
        boolean tmp = Boolean.getBoolean("imq.embed.broker.direct.disabled");
        if (tmp) {
            isDirect = false;
        }

        // decide whether to use two-thread mode
        isTwoThread = Boolean.valueOf(System.getProperty("imq.embed.broker.direct.twothread", "true"));

        isTwoThreadSyncReplies = Boolean.valueOf(System.getProperty("imq.embed.broker.direct.twothread.syncreplies", "true"));
        Globals.setAPIDirectTwoThreadSyncReplies(isTwoThreadSyncReplies);

    }

    /**
     * Constructor not for general use
     */
    protected BrokerInstanceImpl() {
    }

    public static BrokerInstanceImpl getInstance() {
        return soleInstance;
    }

    public static synchronized BrokerInstanceImpl createInstance() throws IllegalAccessException {
        if (soleInstance == null) {
            soleInstance = new BrokerInstanceImpl();
        } else if (soleInstance.isShutdown()) {
            soleInstance = new BrokerInstanceImpl();
        } else {
            throw new IllegalAccessException("Cannot create broker instance.  A broker instance is already created.");
        }
        return soleInstance;
    }

    @Override
    public BrokerEventListener getBrokerEventListener() {
        return this.evlistener;
    }

    @Override
    public Properties getProperties() {
        return this.props;
    }

    @Override
    public synchronized void init(Properties props, BrokerEventListener evlistener) {
        if (this.running) {
            throw new java.lang.IllegalStateException("Cannot initialize while broker is in running state.");
        }

        this.props = props;
        this.evlistener = evlistener;

        try {

            this.getBroker();

            // bkr.init(true, props, evlistener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Supply messages to the newly-created broker which will be written to the broker log when it starts
        addStartupMessages();

    }

    /** @throws IllegalArgumentException */
    @Override
    public Properties parseArgs(String[] args) {
        // System.out.println ("*** parsing args ...");

        this.getBroker();

        return bkr.parseArgs(args);
    }

    @Override
    public void shutdown() {

        this.bkr.stop(true);

        this.isShutdown = true;
    }

    public boolean isShutdown() {
        return this.isShutdown || (bkr != null && bkr.isShutdown());
    }

    public void setShutdown(boolean isShutdown) {
        this.isShutdown = isShutdown;
    }

    @Override
    public synchronized void start() {

        if (this.running) {
            return;
        }
        RuntimeException failStartEx = new RuntimeException("Broker failed to start");
        try {
            if (bkr.start(true, props, evlistener, false, failStartEx) != 0) {
                throw failStartEx;
            }
            this.running = true;

        } catch (Exception e) {
            if (e == failStartEx) {
                throw failStartEx;
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void stop() {
        if (this.running == false) {
            return;
        }

        bkr.stop(false);

        this.running = false;
    }

    @Override
    public synchronized boolean isBrokerRunning() {
        return this.running;
    }

    /**
     * XXX chiaming 10/27/2008 returns true if this is a direct connection
     */
    @Override
    public boolean isDirectMode() {
        return isDirect;
    }

    @Override
    public DirectBrokerConnection createDirectConnection() throws JMSException {
        DirectBrokerConnection dbc = null;

        if (isDirect) {
            dbc = ((JMSDirectBroker) bkr).getConnection();
        }

        return dbc;
    }

    private synchronized void getBroker() {

        try {

            if (this.bkr == null) {

                if (isDirect) {
                    if (isTwoThread) {
                        bkr = (JMSBroker) Class.forName(DIRECT_BROKER_PROCESS2).getDeclaredConstructor().newInstance();
                    } else {
                        bkr = (JMSBroker) Class.forName(DIRECT_BROKER_PROCESS).getDeclaredConstructor().newInstance();
                    }
                } else {
                    bkr = (JMSBroker) Class.forName(BROKER_PROCESS).getDeclaredConstructor().newInstance();
                }

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Supply messages to the newly-created broker which will be written to the broker log when it starts
     */
    private void addStartupMessages() {

//		if (props!=null){
//			// log all properties except for passwords
//			boolean first = true;
//			String stringToLog = "";
//			for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
//				String thisPropertyName = (String) e.nextElement();
//				// skip BrokerArgs as this is logged by the broker itself
//				if (!thisPropertyName.equals("BrokerArgs")){
//					String thisPropertyValue = "";
//					if (thisPropertyName.endsWith("password")) {
//						// don't log the password!
//						thisPropertyValue = "*****";
//					} else {
//						thisPropertyValue = props.getProperty(thisPropertyName);
//					}
//					if (first) {
//						first = false;
//					} else {
//						stringToLog += ", ";
//					}
//
//					stringToLog += thisPropertyName + "=" + thisPropertyValue;
//				}
//			}
//			if (!stringToLog.equals("")){
//				// tell the broker to log this message when it starts
//				bkr.addEmbeddedBrokerStartupMessage("Embedded broker properties: " + stringToLog);
//			}
//		}

        // if non-default values of these properties are used, log this
        if (!isDirect) {
            bkr.addEmbeddedBrokerStartupMessage("Embedded broker: non-default value used for imq.embed.broker.direct.disabled=" + isDirect);
        }
        if (!isTwoThread) {
            bkr.addEmbeddedBrokerStartupMessage("Embedded broker: non-default value used for imq.embed.broker.direct.twothread=" + isTwoThread);
        }
        if (!isTwoThreadSyncReplies) {
            bkr.addEmbeddedBrokerStartupMessage(
                    "Embedded broker: non-default value used for imq.embed.broker.direct.twothread.syncreplies=" + isTwoThreadSyncReplies);
        }
    }

    /**
     * Return a JMSService that can be used to create legacy RADirect connections to this broker
     */
    @Override
    public JMSService getJMSService() {

        // This string is also hardcoded in
        // com.sun.messaging.jmq.jmsserver.management.mbeans.BrokerConfig.hasDirectConnections() and
        // com.sun.messaging.jmq.jmsserver.data.handlers.admin.ShutdownHandler.hasDirectConnections()
        String DEFAULT_DIRECTMODE_SERVICE_NAME = "jmsdirect";

        JMSService jmsService = getJMSService(DEFAULT_DIRECTMODE_SERVICE_NAME);

        if (jmsService != null) {
            return (jmsService);
        }

        /*
         * If "jmsdirect" is not available, loop through all services
         */
        List serviceNames = ServiceManager.getAllServiceNames();
        Iterator iter = serviceNames.iterator();

        while (iter.hasNext()) {
            jmsService = getJMSService((String) iter.next());

            if (jmsService != null) {
                return (jmsService);
            }
        }

        return (null);

    }

    /** @throws IllegalStateException */
    private JMSService getJMSService(String serviceName) {
        ServiceManager sm = Globals.getServiceManager();
        Service svc;
        IMQService imqSvc;
        IMQDirectService imqDirectSvc;

        if (sm == null) {
            return (null);
        }

        svc = sm.getService(serviceName);

        if (svc == null) {
            return (null);
        }

        if (!(svc instanceof IMQService)) {
            return (null);
        }

        imqSvc = (IMQService) svc;

        if (!imqSvc.isDirect()) {
            return (null);
        }

        if (!(imqSvc instanceof IMQDirectService)) {
            return (null);
        }

        imqDirectSvc = (IMQDirectService) imqSvc;

        return imqDirectSvc.getJMSService();
    }

    /**
     * Specify a message that will be written to the broker logfile when the broker starts as an INFO message. This is
     * typically used to log the broker properties configured on an embedded broker, and so is logged immediately after its
     * arguments are logged. However this method can be used for other messages which need to be logged by an embedded
     * broker when it starts.
     *
     * This can be called multiple times to specify multiple messages, each of which will be logged on a separate line.
     */
    @Override
    public void addEmbeddedBrokerStartupMessage(String embeddedBrokerStartupMessage) {
        bkr.addEmbeddedBrokerStartupMessage(embeddedBrokerStartupMessage);
    }

}
