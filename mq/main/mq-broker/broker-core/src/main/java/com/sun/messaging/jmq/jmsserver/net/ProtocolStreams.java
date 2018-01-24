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
 * @(#)ProtocolStreams.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net;


import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.*;
import java.nio.*;
import java.nio.channels.spi.*;
import java.nio.channels.*;

import java.util.Hashtable;

/**
 * This class handles the input and output streams
 * to a specific connection of a protocol (e.g. with
 * TCP this class will really be a socket and its output
 * streams).
 */
// NOTE this is currently identical to
// com.sun.messaging.jmq.jmsclient.ConnectionHandler
//
public interface ProtocolStreams
{
    public InputStream getInputStream() throws IOException;

    public OutputStream getOutputStream() throws IOException;

    public void close() throws IOException;
    public int getLocalPort();
    public int getRemotePort();
    public InetAddress getLocalAddress();
    public InetAddress getRemoteAddress();

    /**
     *@deprecated
     */
    public int getInputBufferSize();

    /**
     *@deprecated
     */
    public int getOutputBufferSize();
    public String toString();
    public String toDebugString();

    public boolean getBlocking();
    public AbstractSelectableChannel getChannel();

    public Hashtable getDebugState();
}
    

