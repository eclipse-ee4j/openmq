/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient.runtime;

import java.util.Properties;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jmq.jmsservice.JMSService;

public interface BrokerInstance {
	
	/**
     *  Parse broker command line and convert the args into a hashtable format.<p>
     *
     *  Additional arguments are:
     *  <UL>
     *      <LI> -varhome: The location of the VAR directory to use</LI>
     *      <LI> -imqhome: The location of the base IMQ directory</LI>
     *  </UL>
     *
     *  @param args The broker arguments in broker command line format.
     *
     *  @return The resulting Properties that represent the command line
     *          parameters passed in.
     *
     *  @throws IllegalArguementException   If args contain any invalid option.
     */
    public Properties parseArgs(String[] args) throws IllegalArgumentException;

	
	/**
	 * Initialize broker with properties specified in the properties.
	 * 
	 * The props parameter is usually obtained from parseArgs() method.
	 * 
	 * This must be called before start/stop/shutdown
	 * 
	 * @param props  the properties required to init broker.  Obtain required info from broker/Linda.
	 * 
	 * @param evlistener used to listen to broker life cycle events.
	 */
	public void init (Properties props, BrokerEventListener evlistener);
	
	/**
	 * start the broker instance.  
	 */
	public void start();
	
	/**
	 * stop the broker instance
	 */
	public void stop();
	
	/**
	 * shutdown the broker instance.
	 */
	public void shutdown();
	
	/**
	 * Get broker init properties
	 * 
	 * @return
	 */
	public Properties getProperties();
	
	/**
	 * Get the broker event listener.
	 * @return
	 */
	public BrokerEventListener getBrokerEventListener();
	
	/**
	 * check if broker is running
	 * @return
	 */
	public boolean isBrokerRunning();
	
	/**
	 * check if broker instance implements direct mode connection.
	 * 
	 * @return
	 */
	public boolean isDirectMode();

	/**
	 * Return a JMSService that can be used to create legacy RADirect
	 * connections to this broker
	 * 
	 * @return
	 */
	public JMSService getJMSService();


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
	public void addEmbeddedBrokerStartupMessage(String embeddedBrokerStartupMessage);
}
