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
 * @(#)TcpStreams.java	1.19 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.tcp;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;

/**
 * This class handles the input and output streams
 * to a specific connection of a protocol (e.g. with
 * TCP this class will really be a socket and its output
 * streams).
 */

public class TcpStreams implements ProtocolStreams
{
    protected Socket socket = null;
    private volatile InputStream is = null;
    private volatile OutputStream os = null;
    protected boolean blocking = true;


    private int inputBufferSize = 0;
    private int outputBufferSize = 0;

    public TcpStreams(Socket soc)
        throws IOException
    {
        // Default to no buffering
        this(soc, true, 0, 0);
    }
    public boolean getBlocking() {
        return blocking;
    }


    public AbstractSelectableChannel getChannel() {
        if (socket == null) return null;
        return socket.getChannel();
    }

    public TcpStreams(Socket soc, boolean blocking, int inBufSz, int outBufSz)
        throws IOException
    {
        this.blocking = blocking;
        socket = soc;
        if (getChannel() != null)
            getChannel().configureBlocking(blocking);
             
        inputBufferSize = inBufSz;
        outputBufferSize = outBufSz;
    }

    public InputStream getInputStream() 
        throws IOException
    {
        if (socket == null) 
            throw new IOException( Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,"Can not get an input stream without a socket"));
         if (is == null) {
             synchronized(this) {
                 if (is == null) {
                     if (socket == null) return null;
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
            throw new IOException( Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,"Can not get an output stream without a socket"));
         if (os == null) {
            synchronized(this) {
                if (os == null) {
                    if (socket == null) return null;
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
        if (getChannel() != null)  {
            getChannel().close();
        }
        socket.close();
        socket = null;
  
    }

    public int getLocalPort() {
        if (socket == null) return 0;
        return socket.getLocalPort();
    }

    public int getRemotePort() {
        if (socket == null) return 0;
        return socket.getPort();
    }

    public InetAddress getLocalAddress() {
        if (socket == null) return null;
        return socket.getLocalAddress();
    }

    public InetAddress getRemoteAddress() {
        if (socket == null) return null;
        return socket.getInetAddress();
    }

    public int getInputBufferSize() {
        return inputBufferSize;
    }

    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    public String toString() {
        return "tcp connection to " + socket ;
    }
    public String toDebugString() {
        return toString() + socket + " inBufsz=" + inputBufferSize +
					       ",outBufSz=" + outputBufferSize;
    }

    public java.util.Hashtable getDebugState() {
        return new java.util.Hashtable();
    }
}
    

