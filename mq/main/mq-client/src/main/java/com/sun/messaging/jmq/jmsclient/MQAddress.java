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
 * @(#)MQAddress.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * This class represents broker address URL.
 */
public class MQAddress extends com.sun.messaging.jmq.io.MQAddress {

    protected static final HashMap handlers = new HashMap();

    private static final String TCP_HANDLER =
        "com.sun.messaging.jmq.jmsclient.protocol.tcp.TCPStreamHandler";

    private static final String SSL_HANDLER =
        "com.sun.messaging.jmq.jmsclient.protocol.ssl.SSLStreamHandler";

    private static final String HTTP_HANDLER =
            "com.sun.messaging.jmq.jmsclient.protocol.http.HTTPStreamHandler";
    
    private static final String DIRECT_HANDLER =
        "com.sun.messaging.jmq.jmsclient.protocol.direct.DirectStreamHandler";

    private static final String WEBSOCKET_HANDLER =
        "com.sun.messaging.jmq.jmsclient.protocol.websocket.WebSocketStreamHandler";

    static {
        handlers.put("jms", TCP_HANDLER);
        handlers.put("ssljms", SSL_HANDLER);
        handlers.put("httpjms", HTTP_HANDLER);
        handlers.put("httpsjms", HTTP_HANDLER);
        handlers.put("admin", TCP_HANDLER);
        handlers.put("ssladmin", SSL_HANDLER);
        handlers.put("httpadmin", HTTP_HANDLER);
        handlers.put("httpsadmin", HTTP_HANDLER);
        handlers.put("direct", DIRECT_HANDLER);
        handlers.put(DEFAULT_WS_SERVICE, WEBSOCKET_HANDLER);
        handlers.put(DEFAULT_WSS_SERVICE, WEBSOCKET_HANDLER);
    }


    protected MQAddress() {
         super();
    }

    /**
     * Parses the given MQ Message Service Address and creates an
     * MQAddress object.
     */
    public static MQAddress 
           createMQAddress(String addr)
        throws MalformedURLException {
        MQAddress ret = new MQAddress();
        ret.initialize(addr);
        return ret;
    }




    public String getHandlerClass() {
        if (isHTTP) {
            return HTTP_HANDLER;
        }
        if (isWebSocket) {
            return WEBSOCKET_HANDLER;
        }
        if (schemeName.equalsIgnoreCase("mqtcp"))
            return TCP_HANDLER;
        if (schemeName.equalsIgnoreCase("mqssl"))
            return SSL_HANDLER;
        if (schemeName.equalsIgnoreCase("direct"))
            return DIRECT_HANDLER;

        String ret = (String) handlers.get(serviceName);
        // assert (ret != null);
        
        if (Debug.debug) {
            ConnectionImpl.getConnectionLogger().info("Handler class: " + ret);
        }
        
        return ret;
    }


    public static void main(String args[]) throws Exception {
        MQAddress addr = createMQAddress(args[0]);
        System.out.println("schemeName = " + addr.getSchemeName());
        if (addr.getIsHTTP())
            System.out.println("URL = " + addr.getURL());
        else {
            System.out.println("host = " + addr.getHostName());
            System.out.println("port = " + addr.getPort());
        }
        System.out.println("serviceName = " + addr.getServiceName());
        System.out.println("handlerClass = " + addr.getHandlerClass());
        System.out.println("isFinal = " + addr.isServicePortFinal());
        System.out.println("properties = " + addr.props);
    }
}

/*
 * EOF
 */
