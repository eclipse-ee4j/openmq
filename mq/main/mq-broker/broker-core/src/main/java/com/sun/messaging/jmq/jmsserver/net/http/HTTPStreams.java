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
 * @(#)HTTPStreams.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.http;

import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;

import java.net.*;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.channels.spi.*;

import java.util.Hashtable;

/**
 * HTTP Input and Output streams.
 */
public class HTTPStreams implements ProtocolStreams
{
    private HttpTunnelSocket socket = null;
    private volatile InputStream is = null;
    private volatile OutputStream os = null;

    private int inputBufferSize = 2048;
    private int outputBufferSize = 2048;

    public HTTPStreams(HttpTunnelSocket soc)
    {
        socket = soc;
    }

    public HTTPStreams(HttpTunnelSocket soc, int inBufSz, int outBufSz)
    {
        socket = soc;
        inputBufferSize = inBufSz;
        outputBufferSize = outBufSz;
    }

    public boolean getBlocking() {
        return true;
    }


    public AbstractSelectableChannel getChannel() {
        return null;
    }


    public InputStream getInputStream() 
        throws IOException
    {
        if (socket == null) 
            throw new
                IOException("Can not get an input stream without a socket");

        if (is == null) {
            synchronized(this) {
                if (is == null) {
                     is = socket.getInputStream();
                     if (inputBufferSize > 0) {
                        is = new BufferedInputStream(is, inputBufferSize);
                     }
                 }
            }
        }
        return is;
    }

    public OutputStream getOutputStream() 
        throws IOException
    {
        if (socket == null) 
           throw new
               IOException("Can not get an output stream without a socket");

        if (os == null) {
            synchronized(this) {
                if (os == null) {
                    os = socket.getOutputStream();
                    if (outputBufferSize > 0) {
                         os = new BufferedOutputStream(os, outputBufferSize);
                    }
                }
            }
        }
        return os;
    }

    public synchronized void close() 
        throws IOException
    {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ex) {}
            is = null;
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException ex) {}
            os = null;
        }
        socket.close();
        socket = null;
    }

    public int getLocalPort() {
        return socket.getConnId();
    }

    public int getRemotePort() {
        return -1;
    }


    public InetAddress getLocalAddress() {
        return null;
    }

    public InetAddress getRemoteAddress() {
        HttpTunnelSocket s = socket;
        if (s == null) return null;
        try {
        return s.getRemoteAddress();
        } catch (Exception e) {
        Globals.getLogger().log(Logger.WARNING, "HttpTunnelSocket - "+e.getMessage());
        return null;
        }
    }


    public String toString() {
        return "HTTP connection to " + socket;
    }

    public String toDebugString() {
        return "HTTP connection to " + socket;
    }

    public int getInputBufferSize() {
        return inputBufferSize;
    }

    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    public Hashtable getDebugState() {
        if (socket != null) {
            return socket.getDebugState();
        }

        return new Hashtable();
    }
}
    
/*
 * EOF
 */
