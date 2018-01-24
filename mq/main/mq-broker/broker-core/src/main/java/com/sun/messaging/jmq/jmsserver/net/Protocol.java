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
 * @(#)Protocol.java	1.15 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net;

import java.util.Map;
import java.io.IOException;
import java.nio.*;
import java.nio.channels.spi.*;
import java.nio.channels.*;
import java.net.*;

/**
 * This interface class handles a specific type of protocol (e.g. tcp)
 */

public interface Protocol 
{
    public void registerProtocolCallback(ProtocolCallback cb, Object data);

    /**
     * The canPause() method is a temporary workaround for bugid
     * 4435336 for jmq2.0 fcs. The TCP and TLS (SSL) transports
     * always return "true". The HTTPProtocol class always returns
     * false.
     */
    public boolean canPause();

    public ProtocolStreams accept()  
        throws IOException;

    public AbstractSelectableChannel getChannel()
        throws IOException;

    public void configureBlocking(boolean blocking)
        throws UnsupportedOperationException,IOException;

    public void open() 
        throws IOException, IllegalStateException;

    public void close() 
        throws IOException, IllegalStateException;

    public boolean isOpen();

    public void checkParameters(Map params)
        throws IllegalArgumentException;

    /**
     * @return old params if param change cause rebind
     */
    public Map setParameters(Map params) throws IOException;

    public int getLocalPort();

    public String getHostName();

    /**
     * method to set the TCP no delay flag on all
     * sockets created if applicable
     */
    public void setNoDelay(boolean val);

    /**
     * method to set the socket timeout (if any)
     * 0 indicates no timeout
     */
    public void setTimeout(int time);

    /**
     * method to set the input buffer size for a connection
     */
    public void setInputBufferSize(int size);

    /**
     * method to set the output buffer size for a connection
     */
    public void setOutputBufferSize(int size);

    /**
     * method to get the input buffer size for a connection
     */
    public int getInputBufferSize();

    /**
     * method to get the output buffer size for a connection
     */
    public int getOutputBufferSize();

    public boolean getBlocking();
}
 

