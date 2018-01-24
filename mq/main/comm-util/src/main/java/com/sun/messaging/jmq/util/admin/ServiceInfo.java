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
 * @(#)ServiceInfo.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

import com.sun.messaging.jmq.util.MetricCounters;

/**
 * ServiceInfo encapsulates information about a JMQ Broker service. It
 * is used to pass this information between the Broker and an
 * administration client.
 */
public class ServiceInfo extends AdminInfo {

    // Values that are only set by broker
    public String	name;
    public String	protocol;
    public int		type;
    public int		state;
    public int		nConnections;
    public int		currentThreads;
    public boolean      dynamicPort = false;
    public MetricCounters metrics;

    // Values that can be updated by client
    public int		port;
    public int		minThreads;
    public int		maxThreads;

    public static final int PORT            = 0x00000001;
    public static final int MIN_THREADS     = 0x00000002;
    public static final int MAX_THREADS     = 0x00000004;

    private int         updateMask = 0;

    /**
     * Constructor for ServiceInfo.
     *
     */
    public ServiceInfo() {
	reset();
    }

    public void reset() {
	name = null;
	protocol = null;
        type = 0;
	state = 0;
	port = 0;
	nConnections = 0;
	minThreads = 0;
	maxThreads = 0;
	currentThreads = 0;
        //metrics = null;

        resetMask();
    }

    /**
     * Return a string representation of the service. 
     *
     * @return String representation of the service.
     */
    public String toString() {

	return "{" + name + ":" +
		" port=" + port +
		" #connections=" + nConnections +
		" threads=" + currentThreads + "[" +
			minThreads + "," + maxThreads + "]" +
		" state=" + state + "}";
    }

    /**
     * Set the port the service is listening for connections on.
     *
     * @param port	Service's port number. 0 to have the broker
     *                  use a dynamic port.
     */
    public void setPort(int port) {
	this.port = port;
        setModified(PORT);
    }

    /**
     * Set the low water mark for the service's thread pool.
     *
     * @param n	Low water mark for service's thread pool
     */
    public void setMinThreads(int n) {
	this.minThreads = n;
        setModified(MIN_THREADS);
    }

    /**
     * Set the high water mark for the service's thread pool.
     *
     * @param n	High water mark for service's thread pool
     */
    public void setMaxThreads(int n) {
	this.maxThreads = n;
        setModified(MAX_THREADS);
    }

    /**
     * XXX dipol need to remove. Just here during transition period
     *    so admin won't break.
     *
     * Set the high water mark for the service's thread pool.
     *
     * @param n	High water mark for service's thread pool
     */
    public void setName(String name) {
	this.name = name;
    }

}
