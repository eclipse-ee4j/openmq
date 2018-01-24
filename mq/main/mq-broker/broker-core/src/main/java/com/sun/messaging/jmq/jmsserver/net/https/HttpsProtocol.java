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
 * @(#)HttpsProtocol.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.https;

import java.net.*;
import java.util.Map;
import java.util.Hashtable;
import java.io.IOException;
import com.sun.messaging.jmq.httptunnel.api.server.*;
import com.sun.messaging.jmq.httptunnel.api.share.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.net.http.HTTPProtocol;
import com.sun.messaging.jmq.jmsserver.resources.*;

public class HttpsProtocol extends HTTPProtocol
{

    static private final String SERVLET_HOST_TRUSTED_PROP = "isHostTrusted";

    // we trust the servlet host by default
    protected boolean isServletHostTrusted = true;

    public HttpsProtocol() {
        driverClass = "com.sun.messaging.jmq.httptunnel.tunnel.server.HttpsTunnelServerDriverImpl";
    }

    @Override
    protected void createDriver() throws IOException {
        String name = InetAddress.getLocalHost().getHostName() + ":" +
            Globals.getConfigName();

        if (servletHost != null || servletPort != -1) {
            String host = servletHost;
            if (host == null)
                host = InetAddress.getLocalHost().getHostAddress();

            int port = servletPort;
            if (port == -1)
                port = HttpTunnelDefaults.DEFAULT_HTTPS_TUNNEL_PORT;

            InetAddress paddr = InetAddress.getLocalHost();
            InetAddress saddr = InetAddress.getByName(host);
            InetAddress laddr = InetAddress.getByName("localhost");

            if (port == Globals.getPortMapper().getPort() &&
                (saddr.equals(paddr) || saddr.equals(laddr))) {
                throw new IOException(Globals.getBrokerResources().getString(
                    BrokerResources.X_HTTP_PORT_CONFLICT));
            }

            try {
                driver = (HttpTunnelServerDriver)
                             Class.forName(driverClass).newInstance();
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
            ((HttpsTunnelServerDriver)driver).init(name, host, 
                         port, isServletHostTrusted, 
                         Globals.getPoodleFixHTTPSEnabled());
            driver.start();
        } else {
            try {
                driver = (HttpTunnelServerDriver)
                             Class.forName(driverClass).newInstance();
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
            ((HttpsTunnelServerDriver)driver).init(name, 
                isServletHostTrusted, Globals.getPoodleFixHTTPSEnabled());
            driver.start();
        }

        driver.setInactiveConnAbortInterval(connectionTimeout);
        driver.setRxBufSize(rxBufSize);
    }

    public String toString() {
        return "https [ " + serversocket + "]";
    }

    @Override
    public Map setParameters(Map params) {

        // check for SERVLET_HOST_TRUSTED_PROP
        String propval = (String)params.get(SERVLET_HOST_TRUSTED_PROP);
        if (propval != null) {
            try {
            boolean value = Boolean.valueOf(propval).booleanValue();
            isServletHostTrusted = value;
            } catch (Exception ex) {}
        }

        return super.setParameters(params);
    }

}

/*
 * EOF
 */
