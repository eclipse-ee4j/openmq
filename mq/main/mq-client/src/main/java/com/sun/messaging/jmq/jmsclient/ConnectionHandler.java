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
 * @(#)ConnectionHandler.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import java.util.Properties;

import com.sun.messaging.jmq.io.ReadWritePacket;

/**
 *  The connection handler knows how to communicate with the broker uses the
 *  specified protocol.
 *
 *  The implementation of InputStream and OutputStream should be in the way
 *  that no protocol specific values are exposed to the API user.
 */
public interface ConnectionHandler {

    public InputStream
    getInputStream() throws IOException;

    public OutputStream
    getOutputStream() throws IOException;

    public int
    getLocalPort() throws IOException;

    public void
    close() throws IOException;

    public String getBrokerHostName();

    /**
     * Get broker address.
     *
     * @return [host,port] for TCP and SSL protocols.
     *         URL string for HTTP/HTTPS protocols.
     */
    public String getBrokerAddress();
    
    public ReadWritePacket readPacket() throws IOException;
    public void writePacket (ReadWritePacket pkt) throws IOException;
    
    public void configure(Properties configuration) throws IOException;
    
    public boolean isDirectMode();
	
}
