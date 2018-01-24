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
 * @(#)HeartbeatCallback.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.spi;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 */
public interface HeartbeatCallback {

    /**
     * The implementation of this method could check the validity
     * of the received data and throw IOException to indicate the
     * data should be discarded - that is it should not be counted
     * in calculating timeout, e.g.
     *
     * 1. The received data could come from a different store session
     *    of a broker instance running on the endpoint
     * 2. The received data could be a UDP broadcast
     *
     * @param sender The sender where the data received from
     * @param data The data received from the remote endpoint
     *
     * @throws IOException if the data should be discarded
     */
    void
    heartbeatReceived(InetSocketAddress sender, byte[] data) throws IOException;


    /**
     * This method should be called before each send to the endpoint
     *
     * @param key The opaque key associated with this endpoint 
     * @param endpoint The endpoint to send heartbeat to
     *
     * @return array of bytes for sending to the endpoint
     *
     * @throws IOException
     */
    byte[]
    getBytesToSend(Object key, InetSocketAddress endpoint) throws IOException;


    /**
     * Timed out in receiving data from the remote endpoint 
     *
     * @param key The opaque key associated with this endpoint 
     * @param endpoint The endpoint
     * @param reason The IOException if any associated with the timeout or null
     *               
     */
    void
    heartbeatTimeout(Object key, InetSocketAddress endpoint, IOException reason);

    /**
     * Heartbeat send io exception occurred
     *
     * @param key The opaque key associated with this endpoint 
     * @param endpoint The endpoint
     * @param reason The IOException if any associated with the timeout or null
     *               
     */
    void
    heartbeatIOException(Object key, InetSocketAddress endpoint, IOException reason);

}
