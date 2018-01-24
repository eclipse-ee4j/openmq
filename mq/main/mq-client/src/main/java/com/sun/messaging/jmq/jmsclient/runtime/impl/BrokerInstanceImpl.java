/*
 * Copyright (c) 2014, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.jms.JMSException;

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

public class BrokerInstanceImpl implements DirectBrokerInstance {

	private static final String BROKER_PROCESS =
        "com.sun.messaging.jmq.jmsserver.BrokerProcess";

	private static final String DIRECT_BROKER_PROCESS2 =
        "com.sun.messaging.jmq.jmsserver.DualThreadDBP";
	
	private static final String DIRECT_BROKER_PROCESS =
        "com.sun.messaging.jmq.jmsserver.DirectBrokerProcess";
	
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
	public volatile static boolean isTwoThread = true;
	
	/**
	 * If this is true then if twoThread mode is set as well then 
	 * replies will be sent synchronously using a ThreadLocal
	 * 
	 * Has no meaning unless isTwoThread is true
	 */
	public volatile static boolean isTwoThreadSyncReplies = true;
	
	static {
       	// NOTE that if you change the default values for these properties, update the logging in addStartupMessages() as well

		//disable direct mode
		boolean tmp = Boolean.getBoolean("imq.embed.broker.direct.disabled");	
		if (tmp) {
			isDirect = false;
		}
		
		// decide whether to use two-thread mode
        isTwoThread = Boolean.valueOf(System.getProperty("imq.embed.broker.direct.twothread","true"));
        
       	isTwoThreadSyncReplies = Boolean.valueOf(System.getProperty("imq.embed.broker.direct.twothread.syncreplies","true"));       
        Globals.setAPIDirectTwoThreadSyncReplies(isTwoThreadSyncReplies);
       	
	}
		
	/**
	 * Constructor not for general use
	 */
	protected BrokerInstanceImpl () {
	}
	
	public static BrokerInstanceImpl getInstance() {
		return soleInstance;
	}
	
	public synchronized static BrokerInstanceImpl createInstance() throws IllegalAccessException{
		if (soleInstance == null) {
			soleInstance = new BrokerInstanceImpl();
		} else if (soleInstance.isShutdown()) {
			soleInstance = new BrokerInstanceImpl();
		} else {
			throw new IllegalAccessException ("Cannot create broker instance.  A broker instance is already created.");
		}
		return soleInstance;
	}
	
	public BrokerEventListener getBrokerEventListener() {
		// TODO Auto-generated method stub
		return this.evlistener;
	}

	public Properties getProperties() {
		// TODO Auto-generated method stub
		return this.props;
	}

	public synchronized void init(Properties props, BrokerEventListener evlistener) {
		// TODO Auto-generated method stub
		this.props = props;
		this.evlistener = evlistener;
		
		if (this.running) {
			throw new java.lang.IllegalStateException ("Cannot initialize while broker is in running state.");
		}
		
		try {
			
			this.getBroker();
			
			//bkr.init(true, props, evlistener);
		} catch (Exception e) {
			throw new RuntimeException (e);
		}
		
		// Supply messages to the newly-created broker which will be written to the broker log when it starts
		addStartupMessages();
		
	}

	public Properties parseArgs(String[] args) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		
		//System.out.println ("*** parsing args ...");
		
		this.getBroker();
		
		return bkr.parseArgs(args);
	}

	public void shutdown() {

		this.bkr.stop(true);
		
		this.isShutdown = true;
	}
	
	public boolean isShutdown() {
		return this.isShutdown || (bkr != null && bkr.isShutdown());
	}
	
	public void setShutdown(boolean isShutdown){
		this.isShutdown=isShutdown;
	}

	public synchronized void start() {
	
		if (this.running) {
			return;
		}
		RuntimeException failStartEx = 
			new RuntimeException("Broker failed to start");
		try {		
			if (bkr.start(true, props, evlistener, false, failStartEx) != 0) {
				throw failStartEx;
			}
			this.running = true;
		
		} catch (Exception e) {
			if (e == failStartEx) {
				throw (RuntimeException)failStartEx;
			}
			throw new RuntimeException (e);
		}
	}

	public synchronized void stop() {
		// TODO Auto-generated method stub
		
		if (this.running == false) {
			return;
		}
		
		bkr.stop(false);
		
		this.running = false;
	}
	
	public synchronized boolean isBrokerRunning() {
		return this.running;
	}
	
	/**
	 * XXX chiaming 10/27/2008 returns true if this is a direct connection
	 */
	public boolean isDirectMode() {
		return isDirect;
	}
	
	public DirectBrokerConnection createDirectConnection() throws JMSException {
		DirectBrokerConnection dbc = null;
		
		if (isDirect) {
			 dbc = ((JMSDirectBroker)bkr).getConnection();	
		}
		
		return dbc;
	}
	
	private synchronized void getBroker() {
		
		try {
			
			if (this.bkr == null) {
				
				if (isDirect) {
                    if (isTwoThread) {
					    bkr = (JMSBroker) Class.forName(DIRECT_BROKER_PROCESS2).newInstance();
                    } else {
					    bkr = (JMSBroker) Class.forName(DIRECT_BROKER_PROCESS).newInstance();
                    }
				} else {
					bkr = (JMSBroker) Class.forName(BROKER_PROCESS).newInstance();
				}
				

			}
			
		} catch (Exception e) {
			throw new RuntimeException (e);
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
		if (!isDirect){
			bkr.addEmbeddedBrokerStartupMessage("Embedded broker: non-default value used for imq.embed.broker.direct.disabled="+isDirect);				
		}
		if (!isTwoThread){
			bkr.addEmbeddedBrokerStartupMessage("Embedded broker: non-default value used for imq.embed.broker.direct.twothread="+isTwoThread);				
		}
		if (!isTwoThreadSyncReplies){
			bkr.addEmbeddedBrokerStartupMessage("Embedded broker: non-default value used for imq.embed.broker.direct.twothread.syncreplies="+isTwoThreadSyncReplies);				
		}
	}
	
	/**
	 * Return a JMSService that can be used to create legacy RADirect connections to this broker
	 * @return
	 */
	public JMSService getJMSService() {
		
		// This string is also hardcoded in
		// com.sun.messaging.jmq.jmsserver.management.mbeans.BrokerConfig.hasDirectConnections() and
		// com.sun.messaging.jmq.jmsserver.data.handlers.admin.ShutdownHandler.hasDirectConnections()
	    String DEFAULT_DIRECTMODE_SERVICE_NAME = "jmsdirect";
				
		ServiceManager sm = Globals.getServiceManager();
		JMSService jmsService = getJMSService(DEFAULT_DIRECTMODE_SERVICE_NAME);

		if (jmsService != null)  {
		    return (jmsService);
		}

		/* 
		 * If "jmsdirect" is not available, loop through all services
		 */
		List serviceNames = sm.getAllServiceNames();
		Iterator iter = serviceNames.iterator();

		while (iter.hasNext())  {
		    jmsService = getJMSService((String)iter.next());

		    if (jmsService != null)  {
		        return (jmsService);
		    }
		}

		return (null);
	    
	}
	
    private JMSService getJMSService(String serviceName) throws IllegalStateException {
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
	 * Specify a message that will be written to the broker logfile  
	 * when the broker starts as an INFO message. This is typically used to log the
	 * broker properties configured on an embedded broker, and so is logged immediately
	 * after its arguments are logged. However this method can be used for other
	 * messages which need to be logged by an embedded broker when it starts.
	 * 
	 * This can be called multiple times to specify
	 * multiple messages, each of which will be logged on a separate line.
	 * 
	 * @param embeddedBrokerStartupMessage
	 */
	public void addEmbeddedBrokerStartupMessage(String embeddedBrokerStartupMessage){
		bkr.addEmbeddedBrokerStartupMessage(embeddedBrokerStartupMessage);
	}

}
