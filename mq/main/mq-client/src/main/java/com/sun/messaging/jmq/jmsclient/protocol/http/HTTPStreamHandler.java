/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * @(#)HTTPStreamHandler.java	1.12 06/27/07
 */

package com.sun.messaging.jmq.jmsclient.protocol.http;

import java.util.Arrays;
import jakarta.jms.*;

import com.sun.messaging.PropertyOwner;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;
import com.sun.messaging.jmq.jmsclient.protocol.ssl.SSLUtil;

/**
 * This class is the HTTP protocol handler for the iMQ JMS client implementation.
 */
public class HTTPStreamHandler implements StreamHandler, PropertyOwner {

    /**
     * POODLE fix see http://www.oracle.com/technetwork/java/javase/documentation/cve-2014-3566-2342133.html
     */
    static {
        String[] protocols = SSLUtil.getKnownSSLEnabledProtocols();
        final String orig = Arrays.toString(protocols);
        StringBuilder buf = new StringBuilder();
        int cnt = 0;
        for (String s : protocols) {
            if (s.equals("SSLv3") || s.equals("SSLv2Hello")) {
                continue;
            }
            if (cnt > 0) {
                buf.append(',');
            }
            buf.append(s);
            cnt++;
        }
        final String sysval = buf.toString();
        final String sysprop = "https.protocols";
        java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<>() {
            @Override
            public Object run() {
                if (System.getProperty(sysprop) == null) {
                    System.out.println(orig + ", System.setProperty: " + sysprop + "=" + sysval);
                    System.setProperty(sysprop, sysval);
                }
                return null;
            }
        });
    }

    @Override
    public String[] getPropertyNames() {
        String[] propnames = new String[1];
        propnames[0] = ConnectionConfiguration.imqConnectionURL;
        return propnames;
    }

    @Override
    public String getPropertyType(String propname) {
        if (ConnectionConfiguration.imqConnectionURL.equals(propname)) {
            return AdministeredObject.AO_PROPERTY_TYPE_STRING;
        }
        return null;
    }

    @Override
    public String getPropertyLabel(String propname) {
        if (ConnectionConfiguration.imqConnectionURL.equals(propname)) {
            return (AdministeredObject.cr.L_JMQHTTP_URL);
        }
        return null;
    }

    @Override
    public String getPropertyDefault(String propname) {
        if (ConnectionConfiguration.imqConnectionURL.equals(propname)) {
            return "http://localhost/imq/tunnel";
        }
        return null;
    }

    /**
     * Open socket a new connection.
     *
     * @param connection is the ConnectionImpl object.
     * @return a new instance of ConnectionHandler.
     * @exception throws IOException if socket creation failed.
     */
    @Override
    public ConnectionHandler openConnection(Object connection) throws JMSException {
        return new HTTPConnectionHandler(connection);
    }

    @Override
    public ConnectionHandler openConnection(MQAddress addr, ConnectionImpl connection) throws JMSException {
        return new HTTPConnectionHandler(addr, connection);
    }

}

