/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.DataFrame;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;


/**
 * @author amyk
 */
public abstract class MQWebSocket extends DefaultWebSocket {

    protected static boolean DEBUG = false;

    protected Logger logger = Globals.getLogger();
    protected BrokerResources br = Globals.getBrokerResources();

    protected MemoryManager memManager = null;
    protected Object closeLock = new Object();
    protected boolean closed = false;
    protected MQWebSocketServiceApp websocketApp = null;
    private ProtocolHandler protocolHandler = null;
    private String requestURL = null;

    public static boolean getDEBUG() {
        return DEBUG;
    }

    public MQWebSocket(MQWebSocketServiceApp app, 
                       ProtocolHandler protocolHandler,
                       HttpRequestPacket request,
                       WebSocketListener... listeners) {
        super(protocolHandler, request, listeners);
        this.requestURL = request.getRequestURI();
        this.websocketApp = app;
        this.protocolHandler = protocolHandler;
        memManager = protocolHandler.getConnection().getTransport().getMemoryManager();
    }

    public InetAddress getRemoteAddress() {
        return ((InetSocketAddress)protocolHandler.getConnection().
                   getPeerAddress()).getAddress();
    }

    public int getRemotePort() {
        return ((InetSocketAddress)protocolHandler.getConnection().
                   getPeerAddress()).getPort();
    }

    protected int getLocalPort() {
        return ((InetSocketAddress)protocolHandler.getConnection().
                   getLocalAddress()).getPort();
    }

    protected abstract void writePacket(Packet pkt) throws IOException; 
    protected abstract void processData(String text) throws Exception;
    protected abstract void processData(byte[] data) throws Exception; 

    @Override
    public void onClose(final DataFrame frame) {
        super.onClose(frame);
        synchronized(closeLock) {
             if (closed) {
                 return;
             }
             closed = true;
        }
        logger.log(Logger.INFO, br.getKString(
            br.I_CLIENT_CLOSE_WEBSOCKET, 
            getLogString()+(frame == null ? "":"["+frame+"]")));
    }

    public boolean isClosed() {
        return (closed || !isConnected());
    }

    protected String getLogString() {
        return "["+requestURL+"@"+this.hashCode()+"]";
    }
}
