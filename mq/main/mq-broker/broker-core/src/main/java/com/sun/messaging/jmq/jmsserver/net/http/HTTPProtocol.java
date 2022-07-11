/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
 * @(#)HTTPProtocol.java	1.32 06/29/07
 */

package com.sun.messaging.jmq.jmsserver.net.http;

import java.util.Map;
import java.net.*;
import java.io.IOException;
import java.nio.channels.spi.AbstractSelectableChannel;
import com.sun.messaging.jmq.httptunnel.api.server.*;
import com.sun.messaging.jmq.httptunnel.api.share.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.resources.*;

public class HTTPProtocol implements Protocol {
    // protected boolean nodelay = true;
    protected static final int defaultPullPeriod = -1;
    protected static final int defaultConnectionTimeout = 300;

    protected String servletHost = null;
    protected int servletPort = -1;
    protected int pullPeriod = defaultPullPeriod;
    protected int connectionTimeout = defaultConnectionTimeout;

    protected int rxBufSize = Globals.getConfig().getIntProperty(Globals.IMQ + ".httptunnel.rxBufSize", 0);

    protected ProtocolCallback cb = null;
    protected Object callback_data = null;

    protected int inputBufferSize = 2048;
    protected int outputBufferSize = 2048;

    protected HttpTunnelServerDriver driver = null;
    protected volatile HttpTunnelServerSocket serversocket = null;
    protected String driverClass = null;
    protected String serverSocketClass = null;

    public HTTPProtocol() {
        driverClass = "com.sun.messaging.jmq.httptunnel.tunnel.server.HttpTunnelServerDriverImpl";
        serverSocketClass = "com.sun.messaging.jmq.httptunnel.tunnel.server.HttpTunnelServerSocketImpl";
    }

    @Override
    public void registerProtocolCallback(ProtocolCallback cb, Object callback_data) {
        this.cb = cb;
        this.callback_data = callback_data;
    }

    protected void notifyProtocolCallback() {
        if (cb != null) {
            cb.socketUpdated(callback_data, getLocalPort(), null);
        }
    }

    @Override
    public String getHostName() {
        return null;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public AbstractSelectableChannel getChannel() throws IOException {
        return null;
    }

    /** @throws UnsupportedOperationException */
    @Override
    public void configureBlocking(boolean blocking) throws IOException {
        throw new UnsupportedOperationException("HttpProtocol is not a channel, can not change blocking state");
    }

    protected void createDriver() throws IOException {
        String name = InetAddress.getLocalHost().getHostName() + ":" + Globals.getConfigName();

        if (servletHost != null || servletPort != -1) {
            String host = servletHost;
            if (host == null) {
                host = InetAddress.getLocalHost().getHostAddress();
            }

            int port = servletPort;
            if (port == -1) {
                port = HttpTunnelDefaults.DEFAULT_HTTP_TUNNEL_PORT;
            }

            InetAddress paddr = InetAddress.getLocalHost();
            InetAddress saddr = InetAddress.getByName(host);
            InetAddress laddr = InetAddress.getByName("localhost");

            if (port == Globals.getPortMapper().getPort() && (saddr.equals(paddr) || saddr.equals(laddr))) {
                throw new IOException(Globals.getBrokerResources().getString(BrokerResources.X_HTTP_PORT_CONFLICT));
            }

            try {
                driver = (HttpTunnelServerDriver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
            driver.init(name, host, port);
            driver.start();
        } else {
            try {
                driver = (HttpTunnelServerDriver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
            driver.init(name);
            driver.start();
        }

        driver.setInactiveConnAbortInterval(connectionTimeout);
        driver.setRxBufSize(rxBufSize);
    }

    protected HttpTunnelServerSocket createSocket() throws IOException {
        if (driver == null) {
            createDriver();
        }

        HttpTunnelServerSocket sock = null;
        try {
            sock = (HttpTunnelServerSocket) Class.forName(serverSocketClass).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
        sock.init(driver);
        return sock;
    }

    private HTTPStreams createConnection(HttpTunnelSocket socket) {
        return new HTTPStreams(socket, inputBufferSize, outputBufferSize);
    }

    @Override
    public ProtocolStreams accept() throws IOException {
        if (serversocket == null) {
            throw new IOException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "Unable to accept on un-opened protocol"));
        }

        HttpTunnelSocket s = serversocket.accept();
        s.setPullPeriod(pullPeriod);
        s.setConnectionTimeout(connectionTimeout);

        HTTPStreams streams = createConnection(s);
        return streams;
    }

    /** @throws IllegalStateException */
    @Override
    public void open() throws IOException {
        if (serversocket != null) {
            throw new IOException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "can not open already opened protocol"));
        }

        if (serversocket == null) {
            synchronized (this) {
                if (serversocket == null) {
                    serversocket = createSocket();
                }
            }
        }

        notifyProtocolCallback(); // ok-> socket is creates, callback
    }

    @Override
    public boolean isOpen() {
        return serversocket != null;
    }

    /** @throws IllegalStateException */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (serversocket != null) {
                serversocket.close();
                serversocket = null;
            } else {
                throw new IOException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "can not close un-opened protocol"));
            }
        }
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    /** @throws IllegalArgumentException */
    @Override
    public void checkParameters(Map params) {
    }

    @Override
    public Map setParameters(Map params) {
        String newServletHost = getStringValue("servletHost", params, null);
        int newServletPort = getIntValue("servletPort", params, -1);

        pullPeriod = getIntValue("pullPeriod", params, pullPeriod);
        connectionTimeout = getIntValue("connectionTimeout", params, connectionTimeout);

        if ((servletHost != null && !servletHost.equalsIgnoreCase(newServletHost)) || servletPort != newServletPort) {
            /*
             * Because of a bug in HttpTunnelServerSocket in JMQ 2.0 we cannot close and reopen the listening socket.
             *
             * Uncomment this code when the HttpTunnelServerSocket bug is fixed.
             *
             * if (active) { try { close(); } catch (Exception ex) { } }
             */

            servletHost = newServletHost;
            servletPort = newServletPort;

            /*
             * if (active) { try { open(); } catch (Exception ex) { } }
             */
        }
        return null;
    }

    private int getIntValue(String propname, Map params, int defval) {
        String propvalstr = (String) params.get(propname);
        if (propvalstr == null) {
            return defval;
        }
        try {
            int val = Integer.parseInt(propvalstr);
            return val;
        } catch (Exception ex) {
            return defval;
        }
    }

    private String getStringValue(String propname, Map params, String defval) {
        String propvalstr = (String) params.get(propname);
        if (propvalstr == null) {
            return defval;
        }
        return propvalstr;
    }

    @Override
    public String toString() {
        return "http [ " + serversocket + "]";
    }

    @Override
    public void setNoDelay(boolean set) {
        // nodelay = set;

        // LKS - XXX - 10/24/00
        // currently the no delay flag has no affect
        // we may want it to affect the tcp connection between the
        // broker and servlet in the future
    }

    @Override
    public void setTimeout(int val) {
        // LKS - XXX - 10/24/00
        // currently the no delay flag has no affect
        // we may want it to affect the tcp connection between the
        // broker and servlet in the future
    }

    @Override
    public void setInputBufferSize(int val) {
        inputBufferSize = val;
    }

    @Override
    public void setOutputBufferSize(int val) {
        outputBufferSize = val;
    }

    @Override
    public int getInputBufferSize() {
        return inputBufferSize;
    }

    @Override
    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    @Override
    public boolean getBlocking() {
        return true;
    }
}

