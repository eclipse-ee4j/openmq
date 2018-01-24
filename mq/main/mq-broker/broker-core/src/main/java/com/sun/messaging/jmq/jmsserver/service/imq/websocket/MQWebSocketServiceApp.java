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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.net.URL;
import java.net.InetAddress;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.Constants;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusteredBroker;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp.STOMPWebSocket;
import com.sun.messaging.jmq.jmsserver.service.imq.websocket.json.JSONWebSocket;

/**
 * @author amyk
 */
public class MQWebSocketServiceApp extends WebSocketApplication {

    private static final String SUBPROTOCOL_V12STOMP = "v12.stomp";

    protected static final String JMS = "mqjms";
    protected static final String STOMP = "mqstomp";
    protected static final String JSONSTOMP = "mqjsonstomp";
    private static final String JMS_REQUEST_PATH = "/"+JMS; 
    private static final String STOMP_REQUEST_PATH = "/"+STOMP; 
    private static final String JSON_REQUEST_PATH = "/"+JSONSTOMP; 

    private Logger logger = Globals.getLogger();
    private BrokerResources br = Globals.getBrokerResources();

    private Map<MQWebSocket, WebSocketMQIPConnection> wsMQConnMap = 
        Collections.synchronizedMap(new HashMap<MQWebSocket, WebSocketMQIPConnection>());

    private WebSocketIPService service = null;

    private Object java8checkLock = new Object();
    private Class base64Class = null; 
    private boolean java8checked = false;

    public MQWebSocketServiceApp(WebSocketIPService svc) {
        this.service = svc;
    }

    public IMQService getMQService() {
        return service;
    }

    public Class getBase64Class() {
        synchronized(java8checkLock) {
            return base64Class;
        }
    }

    private boolean checkOrigin(HttpRequestPacket request)
    throws Exception {

        String origin = request.getHeader(Constants.ORIGIN_HEADER);
        List<URL> origins = service.getAllowedOrigins(); 
        URL myurl = service.getMyURL();

        if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, getClass().getSimpleName()+
                ".checkOrigin("+request+"): origin="+origin+
                ", myurl="+myurl+", allowedOrigins="+origins);
        }
        if (origin == null || origins == null) {
            return true;
        }
        URL ou = null;
        try {
            ou= new URL(origin);
        } catch (Exception e) {
            logger.log(logger.ERROR, br.getKString(
                br.X_WEBSOCKET_INVALID_CLIENT_ORIGIN, origin));
            return false;
        }
        if (!ou.getProtocol().equals(myurl.getProtocol())) { 
            return false;
        }
        if (ou.getHost().equals(myurl.getHost())) {
            return true;
        }
        InetAddress oia = InetAddress.getByName(ou.getHost()); 
        if (oia.equals(InetAddress.getByName(myurl.getHost()))) {
            return true;
        }

        ClusterManager cm = Globals.getClusterManager();
        Iterator itr = cm.getConfigBrokers();
        ClusteredBroker cb = null;
        BrokerMQAddress addr = null;
        String h;
        while (itr.hasNext()) {
            cb = (ClusteredBroker)itr.next();
            addr = (BrokerMQAddress)cb.getBrokerURL();
            h = addr.getHost().getCanonicalHostName();
            if (MQWebSocket.getDEBUG()) {
                logger.log(logger.INFO,
                    getClass().getSimpleName()+".checkOrigin("+request+
                    "), origin="+ou+", check configured cluster broker "+ addr+"["+h+"]");
            }
            if (ou.getHost().equals(h)) {
                return true;
            }
            if (oia.equals(addr.getHost())) {
                return true;
            }
        }

        URL url;
        Iterator<URL> itr1 = origins.iterator();
        while (itr1.hasNext()) {
            url = itr1.next();
            if (!ou.getProtocol().equals(url.getProtocol())) {
                continue;
            }
            if (ou.getHost().equals(url.getHost())) {
                return true;
            }
            if (oia.equals(InetAddress.getByName(url.getHost()))) {
                return true;
            }
        }
        logger.log(logger.ERROR, br.getKString(
            br.X_WEBSOCKET_ORIGIN_NOT_ALLOWED, origin, origins));

        return false;
    }


    protected static boolean isSupportedSubService(String subserv) {
        if (subserv.equals(JMS) || 
            subserv.equals(STOMP) || 
            subserv.equals(JSONSTOMP)) {
            return true;
        }
        return false;
    }

    @Override
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        if (subProtocol.contains(SUBPROTOCOL_V12STOMP)) {
            List<String> l = new ArrayList<String>();
            l.add(SUBPROTOCOL_V12STOMP);
            return l;
        } else {
            return super.getSupportedProtocols(subProtocol);
        }
    }

    private boolean isJMSRequest(HttpRequestPacket request) {
        return ("/"+service.getName()+JMS_REQUEST_PATH).equals(request.getRequestURI());
    }

    private boolean isSTOMPRequest(HttpRequestPacket request) {
        return ("/"+service.getName()+STOMP_REQUEST_PATH).equals(request.getRequestURI());
    }

    private boolean isJSONRequest(HttpRequestPacket request) {
        return ("/"+service.getName()+JSON_REQUEST_PATH).equals(request.getRequestURI());
    }

    @Override
    public WebSocket createSocket(ProtocolHandler handler,
                                  HttpRequestPacket request,
                                  WebSocketListener... listeners) {
        if (isJMSRequest(request)) {
            return new JMSWebSocket(this, handler, request, listeners);
        }
        if (isJSONRequest(request)) {
            synchronized(java8checkLock) {            
            if (!java8checked) {
                try {
                    base64Class =  Class.forName("java.util.Base64");
                } catch (ClassNotFoundException e) {
                    base64Class = null;
                } catch (Exception e) {
                    base64Class = null;
                    logger.logStack(logger.WARNING, e.getMessage(), e);
                }
                java8checked = true;
            }
            }
            return new JSONWebSocket(this, handler, request, listeners);
        }
        if (isSTOMPRequest(request)) {
            return new STOMPWebSocket(this, handler, request, listeners);
        } 
        throw new UnsupportedOperationException(
            "MQWebSocketServiceApp.createSocket("+request.getRequestURI()+")");
    }

    @Override
    public void onConnect(WebSocket wsocket) {
        super.onConnect(wsocket);
        try {
            if (wsocket instanceof JMSWebSocket) {
                WebSocketMQIPConnection conn = service.createConnection((MQWebSocket)wsocket);
                wsMQConnMap.put((MQWebSocket)wsocket, conn);
                Globals.getConnectionManager().addConnection(conn);
                if (MQWebSocket.getDEBUG()) {
                    logger.log(Logger.INFO,
                    "MQWebSocketServiceApplication.onConnect("+wsocket+"): "+conn);
                }
            } else {
                if (MQWebSocket.getDEBUG()) {
                    logger.log(Logger.INFO,
                    "MQWebSocketServiceApplication.onConnect("+wsocket+")");
                }
            }
        } catch (Exception e) {
            logger.logStack(logger.ERROR, e.getMessage(), e);
        }
    }

    @Override
    public boolean isApplicationRequest(HttpRequestPacket request) {
        String origin = request.getHeader(Constants.ORIGIN_HEADER);
        String uri = request.getRequestURI();
        logger.log(Logger.INFO, br.getKString(
                   br.I_WEBSOCKET_CONN_REQUEST_ON_SERVICE, request+
                   "[uri="+uri+", origin="+origin+"]", service.getName()));

        try {
            if (!checkOrigin(request)) {
                return false;
            }
        } catch (Exception e) {
           logger.log(Logger.ERROR, e.getMessage(), e);
           return false;
        }

        boolean jms = false, stomp = false, json = false;
        if ((jms = isJMSRequest(request)) || 
            (stomp = isSTOMPRequest(request)) || 
            (json = isJSONRequest(request))) {
            if (MQWebSocket.getDEBUG()) {
                logger.log(Logger.INFO, "isApplicationRequest("+request+
                "): found match[jms="+jms+", stomp="+stomp+", json="+json+"]");
            }
            if ((jms && !service.isSubServiceEnabled(JMS)) ||
                (stomp && !service.isSubServiceEnabled(STOMP)) ||
                (json && !service.isSubServiceEnabled(JSONSTOMP))) {
                String emsg = br.getKString(br.X_SERVICETYPE_NO_SUPPORT,
                              request.getRequestURI(), service.getName());
                logger.log(logger.WARNING, emsg);
                return false;
            }
            if (isSTOMPRequest(request) || isJSONRequest(request)) {
                if (service.getServiceType() != ServiceType.NORMAL) { 
                    String emsg = br.getKString(br.X_SERVICETYPE_NO_SUPPORT,
                        ServiceType.getServiceTypeString(service.getServiceType()),
                        service.getName()+"["+request.getRequestURI()+"]");
                    logger.log(logger.WARNING, emsg);
                    return false;
                }
            }
            return true;
        }
        String emsg = br.getKString(br.W_UNKNOWN_REQUEST_ON_SERVICE,
                          request.getRequestURI(), service.getName());
        logger.log(logger.WARNING, emsg);
        return false;
    }

    @Override
    public void onMessage(WebSocket wsocket, String text) {
        if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onMessage("+wsocket+", text="+text+")");
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
        try {
            if (!(wsocket instanceof JSONWebSocket) &&
                !(wsocket instanceof STOMPWebSocket)) {
                logger.log(Logger.ERROR, br.getKString(
                    br.W_UNKNOWN_REQUEST_ON_SERVICE,
                    "WebSocket.onMessage("+wsocket+", String)", service.getName()));
                wsocket.close(WebSocket.INVALID_DATA);
                return;
            }
            ((MQWebSocket)wsocket).processData(text);
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void onMessage(WebSocket wsocket, byte[] data) {
        if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onMessage("+wsocket+", bytes.len="+data.length+")");
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
 
        try {
            if (wsocket instanceof JSONWebSocket) { 
                logger.log(Logger.ERROR, br.getKString(
                    br.W_UNKNOWN_REQUEST_ON_SERVICE,
                    "WebSocket.onMessage("+wsocket+", byte[])", service.getName()));
                wsocket.close(WebSocket.INVALID_DATA);
                return;
            }
            ((MQWebSocket)wsocket).processData(data);
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void onClose(WebSocket wsocket, DataFrame frame) {
        super.onClose(wsocket, frame);
	WebSocketMQIPConnection conn = wsMQConnMap.get(wsocket);
	if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO,
            "MQWebSocketServiceApp.onClose(): "+conn+"["+wsocket+"]");
	}
        if (conn != null) {
            if (conn.getConnectionState() < WebSocketMQIPConnection.STATE_CLOSED) {
		try {
                    conn.destroyConnection(true, GoodbyeReason.CLIENT_CLOSED,
                                           br.getKString(br.M_CONNECTION_CLOSE));
                } catch (Exception e) {
                    if (MQWebSocket.getDEBUG()) {
                        logger.log(Logger.WARNING, e.getMessage(), e);
                    }
                }
            }
            wsMQConnMap.remove(wsocket); 
	}
        logger.log(Logger.INFO, br.getKString(br.I_ClOSED_WEBSOCKET, 
            "@"+wsocket.hashCode()+"["+wsocket+"]["+frame+"]"));
    }

    public void onPing(WebSocket wsocket, byte[] bytes) {
	if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onPing("+wsocket+", bytes.len="+bytes.length);
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
    }

    public void onPong(WebSocket wsocket, byte[] bytes) {
	if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onPong("+wsocket+", bytes.len="+bytes.length);
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
    }

    public void onFragment(WebSocket wsocket, String fragment, boolean last) {
	if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onFragment("+wsocket+", text="+fragment+", last="+last);
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
        try {
            ((MQWebSocket)wsocket).processData(fragment);
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
        }
    }

    public void onFragment(WebSocket wsocket, byte[] fragment, boolean last) {
        if (MQWebSocket.getDEBUG()) {
            logger.log(Logger.INFO, 
            "MQWebSocketServiceApp.onFragment("+wsocket+", bytes.len="+fragment.length+", last="+last);
        }
        if (!(wsocket instanceof MQWebSocket)) {
            String emsg = "Unexpected class: "+wsocket.getClass().getName();
            logger.logStack(Logger.ERROR, emsg, (new Exception(emsg)));
            wsocket.close(WebSocket.INVALID_DATA);
            return;
        }
        try {
            ((MQWebSocket)wsocket).processData(fragment);
        } catch (Exception e) {
            logger.logStack(Logger.ERROR, e.getMessage(), e);
        }
    }

    protected WebSocketMQIPConnection getMQIPConnection(MQWebSocket wsocket) {
        return wsMQConnMap.get(wsocket);
    }
}
