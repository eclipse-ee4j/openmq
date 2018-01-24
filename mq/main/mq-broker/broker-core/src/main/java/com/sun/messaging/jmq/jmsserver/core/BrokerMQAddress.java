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
 * @(#)BrokerMQAddress.java	1.4 06/28/07
 */ 
 
package com.sun.messaging.jmq.jmsserver.core;

import java.net.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.VerifyAddressException;
import com.sun.messaging.jmq.jmsserver.util.LoopbackAddressException;

public class BrokerMQAddress extends MQAddress
{
    static final long serialVersionUID = 9061061210446233838L;

    private transient InetAddress host = null;
    private transient String tostring = null;
    private transient String hostaddressNport = null;

    protected BrokerMQAddress() {} 

    protected void initialize(String addr) 
        throws MalformedURLException {
        super.initialize(addr);
        serviceName = "";
    }

    protected void initialize(String host, int port)
        throws MalformedURLException {
        super.initialize(host, port);
        serviceName = "";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if(obj.getClass() != this.getClass()) {
           return false;
        }
        return toString().equals(((BrokerMQAddress)obj).toString());
    }

    @Override
    public int hashCode() { 
        return toString().hashCode();
    }

    public String toString() {
        if (tostring != null) return tostring;

        if (getIsHTTP()) return super.toString();  

        tostring = getSchemeName() + "://" + getHostAddressNPort() + "/" + getServiceName();
        return tostring;
 
    }

    private void initHostAddressNPort() throws MalformedURLException {
        hostaddressNport = MQAddress.getMQAddress(
            host.getHostAddress(), port).getHostName()+":"+port;
    }

    public String getHostAddressNPort() {
        return hostaddressNport;
    }

    public InetAddress getHost() { 
        return host;
    }


    public void resolveHostName() throws UnknownHostException {
        if (host == null) {
            String h = getHostName();
            if (h == null || h.equals("") || h.equals("localhost")) {
                host = InetAddress.getLocalHost();
            } else {
                host = InetAddress.getByName(h);
            }
        }
    }

    /**
     * Parses the given MQ Message Service Address and creates an
     * MQAddress object.
     */
    public static BrokerMQAddress createAddress(String host, int port)
        throws MalformedURLException, UnknownHostException {
        BrokerMQAddress ret = new BrokerMQAddress();
        ret.initialize(host, port);
        ret.resolveHostName();
        ret.initHostAddressNPort();
        return ret;
    }

    public static BrokerMQAddress createAddress(String addr)
        throws MalformedURLException, UnknownHostException {
        BrokerMQAddress ret = new BrokerMQAddress();
        ret.initialize(addr);
        ret.resolveHostName();
        ret.initHostAddressNPort();
        return ret;
    }

    /**
     *
     * @param nolocalhost if true no return loopback address
     *
     */
    public static InetAddress resolveBindAddress(String listenHost, 
                                                 boolean nolocalhost)
                                                 throws BrokerException,
                                                 UnknownHostException {
        if (listenHost == null) return null;
        if (listenHost.trim().length() == 0) return null;

        InetAddress iaddr = null; 
        if (nolocalhost && listenHost.equals("localhost")) {
            iaddr = InetAddress.getLocalHost();
        } else {
            iaddr = InetAddress.getByName(listenHost);
        }
        if (!nolocalhost) return iaddr;

        checkLoopbackAddress(iaddr, listenHost);
        return iaddr;
    }

    /**
     *
     */
    public static void checkLoopbackAddress(InetAddress iaddr, String hostname)
                                            throws BrokerException,
                                            UnknownHostException {
        if (iaddr == null) return;

        if (iaddr.isLoopbackAddress()) {
            throw new LoopbackAddressException(Globals.getBrokerResources().getString(
            Globals.getBrokerResources().X_LOOPBACKADDRESS, 
            (hostname == null ? "":hostname+"["+iaddr+"]")));
        }
        return;
    }

}
