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
 * @(#)BrokerProcess.java	1.16 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver;

import java.util.*;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQDirectService;
import com.sun.messaging.jmq.jmsservice.JMSBroker;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;

/**
 * Wrapper used to start the broker. It wraps a singleton class
 * (only one broker can be running in any process).<P>
 *
 * <u>Example</u><P>
 * <code><PRE>
 *      BrokerProcess bp = BrokerProcess.getBrokerProcess();
 *      try {
 *      
 *          Properties ht = bp.convertArgs(args);
 *          int exitcode = bp.start(true, ht, null);
 *          System.out.println("Broker exited with " + exitcode);
 *
 *      } catch (IllegalArgumentException ex) {
 *          System.err.println("Bad Argument " + ex.getMessage());
 *          System.out.println(BrokerProcess.usage());
 *      }
 * </PRE></code>
 */
public class BrokerProcess implements JMSBroker
{
    private Broker broker = null;

    /**
     * Constructor
     */
    public BrokerProcess() {
        broker = Broker.getBroker();
    }

    /**
     * Change command line args into a hashtable format
     *<P>
     * Additional arguments are:
     *    <UL>
     *       <LI> -varhome</LI>
     *       <LI> -imqhome</LI>
     *    </UL>
     *
     * @param args arguments in broker format
     */
    private Properties convertArgs(String[] args)
    throws IllegalArgumentException {

        Properties props = new Properties();

        // first look for var home and the like
        for (int i =0; i < args.length; i ++) {
            String arg = args[i];
            if (arg.equals("-varhome")) {
                props.setProperty("imq.varhome",
                        args[i+1]);
                i ++;
            } else if (arg.equals("-imqhome")) {
                props.setProperty("imq.home",
                        args[i+1]);
                i ++;
            } else if (arg.equals("-libhome")) {
                props.setProperty("imq.libhome",
                        args[i+1]);
                i ++;
            }
        }
        Globals.pathinit(props);
        return broker.convertArgs(args);
    }

    public Properties parseArgs(String[] args)
        throws IllegalArgumentException
    {
        return (convertArgs(args));
    }

    /**
     * Checks the state of the Broker
     *
     * @return the state of the broker
     */
    public boolean isRunning() {
        return true;
    }

    /**
     * Start the broker (only one broker can be running in a given
     * vm).<p>This call returns as soon as the broker sucessfully starts.
     * @param inProcess - indicates that the broker is running inprocess
     *                    and the shutdown hook and memory management
     *                    code should not be used.
     * @param properties - configuration setttings for the broker
     *
     * @param bn - optional class to notify when a broker has completed
     *             starting or has been shutdown.
     *
     * @return the exit code what would be returned by the broker if it
     *       was running as a standalone process. (or 0 if it sucessfully
     *       started).
     *
     * @throws OutOfMemoryError - if the broker can not allocate enough 
     *          memory to continue running
     * @throws IllegalStateException - the broker is already running.  
     * @throws IllegalArgumentException - an invalid value for a property
     *                was passed on the command line
     */
    public int start(boolean inProcess, Properties properties, 
        BrokerEventListener bel, boolean initOnly, Throwable failStartThrowable)
        throws OutOfMemoryError, IllegalStateException, IllegalArgumentException {

        return broker.start(inProcess, properties, bel, initOnly, failStartThrowable);
    }

    /**
     * Stop the broker (only one broker can be running in a given
     * vm).<p>
     * @param cleanup - if false, the code does not need to worry about freeing
     *                  unused resources. (broker is about to exit)
     * @throws IllegalStateException - the broker is already stopped.  
     */
    public void stop(boolean cleanup)
        throws IllegalStateException
    {
        broker.destroyBroker(cleanup);
        broker = null;
    }

    public boolean isShutdown() {
        return (broker == null || broker.broker == null);
    }
    
	/**
	 * Specify a message that will be written to the broker logfile  
	 * when the broker starts as an INFO message. This is typically used to log the
	 * broker properties configured on an embedded broker, and so is logged immediately
	 * after its arguments are logged. 
	 * 
	 * This can be called multiple times to specify
	 * multiple messages, each of which will be logged on a separate line.
	 * 
	 * @param embeddedBrokerStartupMessage
	 */    
	public void addEmbeddedBrokerStartupMessage(String message) {
		broker.addEmbeddedBrokerStartupMessage(message);
	}

}


