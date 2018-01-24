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
 * @(#)JMSBroker.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import java.util.Properties;

/**
 *
 */
public interface JMSBroker {
    
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
    public Properties parseArgs(String[] args)
    throws IllegalArgumentException;

    /**
     *  Start the broker. Only one broker can be running in a single JVM.
     *  The call returns as soon as the broker successfully starts.
     *
     *  @param  inProcess   indicates that the broker is running inprocess
     *                      and the shutdown hook and memory management
     *                      code should not be used.
     *
     *  @param  properties  the configuration properties for the broker.
     *
     *  @param  el          An optional class to notify when a broker has
     *                      completed starting or has been shutdown.
     *  @param initOnly      Only initiaize the broker var directory, do
     *                      not actually start the broker
     *  @return The exit code returned by the broker. This is the same value
     *          returned as would be if the broker was running as a standalone
     *          process.<br>
     *          {@code 0} - if it was started successfully<br>.
     *          {@code non-zero} - otherwise
     *
     *  @throws OutOfMemoryError    If the broker can not allocate enough
     *                              memory to continue running.
     *  @throws IllegalStateException   If the broker is already running.  
     *  @throws IllegalArgumentException    If an invalid value for a property
     *                                      was passed in {@code properties}.
     */
    public int start(boolean inProcess, 
        Properties properties, BrokerEventListener el, 
        boolean initOnly, Throwable failStartThrowable)
        throws OutOfMemoryError, IllegalStateException, IllegalArgumentException;
    
    /**
     *  Stop the broker. Only one broker can be running in a single JVM
     *
     *  @param  cleanup {@code false} indicates that the broker does not have
     *                  to clean up; free resources etc. since the it is about
     *                  to exit.<br>
     *                  {@code true} indicates that the broker does have to
     *                  clean up/free resources etc.
     *
     *  @throws IllegalStateException if the broker is already stopped.  
     */
    public void stop(boolean cleanup)
    throws IllegalStateException;

    /**
     * @return true if the broker is shutdown
     */
    public boolean isShutdown();
    
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
