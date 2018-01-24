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
 * @(#)Heartbeat.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.spi;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 */
public interface Heartbeat {

    /**
     * A name (can be null) for logging purpose
     */
    String
    getName();

    /**
     * A protocol string
     */
    String
    getProtocol();

    /**
     * Initialization
     *
     * @param addr bind IP address
     * @param port bind port 
     * @param cb The HeartbeatCallback
     *
     * @throws IOException if failed to initialize
     */
    void
    init(InetSocketAddress iaddr, HeartbeatCallback cb) throws IOException;

    /**
     * Stop  
     *
     * @throws IOException if failed to start
     */
    void
    stop() throws IOException;


    /**
     *
     * @param key The opaque key associated with this endpoint
     * @param addr The remote IP address
     * @param port The remote port number
     * @param dataLength The expected data length
     *
     * @throws IOException
     */
    void
    addEndpoint(Object key, InetSocketAddress iaddr, int dataLength) throws IOException;

    /**
     * @return true if end point, iaddr, is removed
     */
    boolean 
    removeEndpoint(Object key, InetSocketAddress iaddr) throws IOException;

    /**
     */
    InetSocketAddress 
    getBindEndpoint();

    /**
     *
     * @param interval The inteval (in seconds) between each heartbeat
     */
    void
    setHeartbeatInterval(int interval);

    /**
     *
     * @return The heartbeat interval
     */
    int 
    getHeartbeatInterval();



    /**
     * Timeout when heartbeat message not received for period of threshold*interval
     * from a remote endpoint
     *
     * @param threshold in terms of number of times of heartbeat interval 
     */
    void
    setTimeoutThreshold(int threshold);

    /**
     *
     * @return The heartbeat timeout threshold 
     */
    int 
    getTimeoutThreshold();

}
