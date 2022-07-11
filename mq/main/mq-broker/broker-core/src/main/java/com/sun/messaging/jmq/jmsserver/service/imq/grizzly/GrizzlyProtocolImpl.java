/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.nio.channels.spi.AbstractSelectableChannel;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.net.ProtocolStreams;
import com.sun.messaging.jmq.jmsserver.net.ProtocolCallback;
import com.sun.messaging.jmq.jmsserver.net.tcp.TcpProtocol;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;

public class GrizzlyProtocolImpl implements Protocol {

    protected static final int defaultReadTimeout = TcpProtocol.defaultReadTimeout;
    protected static final int defaultLingerTimeout = TcpProtocol.defaultLingerTimeout;
    protected static final int defaultBacklog = TcpProtocol.defaultBacklog;

    private static final int defaultPort = TcpProtocol.defaultPort;

    protected boolean requireClientAuth = false;

    protected GrizzlyService service = null;
    protected String proto = null;
    protected String modelName = null;
    protected int readTimeout = defaultReadTimeout;
    protected int lingerTimeout = defaultLingerTimeout;
    protected int backlog = defaultBacklog;
    protected boolean tcpNoDelay = true;

    protected int inputBufferSize = 0;
    protected int outputBufferSize = 0;

    protected int port = defaultPort;
    protected String hostname = null; // all hosts

    protected int minThreads = 4;
    protected int maxThreads = 10;

    public GrizzlyProtocolImpl(GrizzlyService s, String proto) {
        this.service = s;
        this.proto = proto;
    }

    public String getType() {
        return proto;
    }

    @Override
    public void setNoDelay(boolean v) {
        tcpNoDelay = v;
    }

    public boolean getNoDelay() {
        return tcpNoDelay;
    }

    public int getLingerTimeout() {
        return lingerTimeout;
    }

    @Override
    public void setTimeout(int time) {
        readTimeout = time;
    }

    public int getTimeout() {
        return readTimeout;
    }

    public int getBacklog() {
        return backlog;
    }

    @Override
    public void setInputBufferSize(int size) {
        inputBufferSize = size;
    }

    @Override
    public void setOutputBufferSize(int size) {
        outputBufferSize = size;
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
        throw new UnsupportedOperationException("Unsupported call: " + getClass().getName() + ".getBlocking");
    }

    public boolean getRequireClientAuth() {
        return requireClientAuth;
    }

    @Override
    public void registerProtocolCallback(ProtocolCallback cb, Object callback_data) {
        throw new RuntimeException("Unsupported call: " + getClass().getName() + ".registerProtocolCallback()");
    }

    protected void notifyProtocolCallback() {
        throw new RuntimeException("Unsupported call: " + getClass().getName() + ".notifyProtocolCallback()");
    }

    @Override
    public boolean canPause() {
        throw new RuntimeException("Unsupported call: " + getClass().getName() + ".canPause()");
    }

    /** @throws UnsupportedOperationException */
    @Override
    public void configureBlocking(boolean blocking) throws IOException {
        throw new UnsupportedOperationException("Unsupported call: " + getClass().getName() + ".configureBlocking");
    }

    @Override
    public AbstractSelectableChannel getChannel() throws IOException {
        return null;
    }

    @Override
    public ProtocolStreams accept() throws IOException {
        throw new UnsupportedOperationException("GrizzlyProtocolImpl:accept");
    }

    /** @throws IllegalStateException */
    @Override
    public void open() throws IOException {
    }

    @Override
    public boolean isOpen() {
        return service.isOpen();
    }

    /** @throws IllegalStateException */
    @Override
    public void close() throws IOException {
    }

    /** @throws IllegalArgumentException */
    @Override
    public void checkParameters(Map params) {
        TcpProtocol.checkTcpParameters(params);
    }

    /**
     * @return old params if param change cause rebind
     */
    @Override
    public Map setParameters(Map params) throws IOException {
        if (params.get("serviceFactoryHandlerName") != null) {
            this.modelName = (String) params.get("serviceFactoryHandlerName");
        }

        HashMap oldparams = null;

        int newport = TcpProtocol.getIntValue("port", params, port);
        readTimeout = TcpProtocol.getIntValue("readtimeout", params, readTimeout);
        lingerTimeout = TcpProtocol.getIntValue("solinger", params, lingerTimeout);
        int newbacklog = TcpProtocol.getIntValue("backlog", params, backlog);
        String newhostname = (String) params.get("hostname");
        if (newhostname == null) {
            newhostname = Globals.getHostname();
        }
        if (newhostname == null || newhostname.trim().length() == 0) {
            newhostname = Globals.HOSTNAME_ALL;
        }

        boolean newhost = hostname == null
                || (hostname != null && !newhostname.equals(hostname));

        if (newport != port || newbacklog != backlog || newhost) {
            oldparams = new HashMap();
            if (newport != -1) {
                oldparams.put("port", String.valueOf(port));
                port = newport;
            }
            if (newbacklog != -1) {
                oldparams.put("backlog", String.valueOf(backlog));
                backlog = newbacklog;
            }
            if (newhost) {
                oldparams.put("hostname", (hostname == null ? "" : hostname));
                hostname = newhostname;
            }
        }
        if (isSSLProtocol()) {
            requireClientAuth = TcpProtocol.getBooleanValue("requireClientAuth", params, requireClientAuth);
        }

        return oldparams;
    }

    protected boolean isSSLProtocol() {
        if (proto.equals("tls")) {
            return true;
        }
        return false;
    }

    @Override
    public int getLocalPort() {
        return service.getLocalPort();
    }

    @Override
    public String getHostName() {
        if (hostname == null || hostname.equals("") || hostname.equals(Globals.HOSTNAME_ALL)) {
            return null;
        }
        return hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * @return int[0] min; int[1] max; -1 no change
     *
     * @throws IllegalArgumentException
     */
    public int[] setMinMaxThreads(int min, int max, String svcname) {
        int[] rets = new int[2];
        rets[0] = rets[1] = -1;
        int tmpmin = min;
        int tmpmax = max;
        if (tmpmin <= -1) {
            tmpmin = minThreads;
        } else {
            tmpmin = (int) (((float) min) / 2);
        }
        if (tmpmax <= -1) {
            tmpmax = maxThreads;
        } else {
            tmpmax = (int) (((float) max) / 2);
        }

        if (tmpmax == 0) {
            throw new IllegalArgumentException(
                    Globals.getBrokerResources().getKString(BrokerResources.X_MAX_THREAD_ILLEGAL_VALUE, svcname, String.valueOf(max)));
        }
        if (tmpmin > tmpmax) {
            String[] args = { service.getName(), String.valueOf(tmpmin), String.valueOf(tmpmax) };
            String emsg = Globals.getBrokerResources().getKString(BrokerResources.W_THREADPOOL_MIN_GT_MAX_SET_MIN_TO_MAX, args);
            Globals.getLogger().log(Logger.WARNING, emsg);
            tmpmin = tmpmax;
        }
        if (tmpmin != minThreads) {
            minThreads = tmpmin;
            rets[0] = minThreads;
        }
        if (tmpmax != maxThreads) {
            maxThreads = tmpmax;
            rets[1] = maxThreads;
        }
        return rets;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    @Override
    public String toString() {
        return getType() + "(host = " + (hostname == null ? Globals.HOSTNAME_ALL : hostname) + ", port=" + port + ", mode=" + modelName + ")";
    }
}
