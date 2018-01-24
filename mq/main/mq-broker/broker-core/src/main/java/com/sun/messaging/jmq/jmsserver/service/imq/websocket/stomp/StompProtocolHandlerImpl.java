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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp;

import java.net.InetAddress;
import java.util.StringTokenizer;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.LoggerWrapper;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompFrameMessageFactory;


/**
 * @author amyk 
 */
public class StompProtocolHandlerImpl extends StompProtocolHandler {

    private static final Logger logger = Globals.getLogger();
    private static final BrokerResources br = Globals.getBrokerResources();

    private static final Version mqversion = new Version();

    private STOMPWebSocket wsocket = null;
    private JMSService jmsservice = null;

    public StompProtocolHandlerImpl(STOMPWebSocket wsocket, JMSService jmss) {
        super((LoggerWrapper)logger);
        this.wsocket = wsocket;
        this.jmsservice = jmss;
        stompConnection =  new StompConnectionImpl(this);
    }

    @Override
    public boolean getDEBUG() {
        return wsocket.getDEBUG();
    }

    public JMSService getJMSService() {
        return jmsservice;
    }

    public InetAddress getRemoteAddress() {
        return wsocket.getRemoteAddress();
    }

    public int getRemotePort() {
        return wsocket.getRemotePort();
    }

    @Override
    public String getSupportedVersions() {
        return StompFrameMessage.STOMP_PROTOCOL_VERSION_12; 
    }

    @Override
    public String getServerName() {
        return mqversion.getProductName();
    }

    @Override
    public String negotiateVersion(String acceptVersions) 
    throws StompProtocolException {
        if (acceptVersions == null) {
            throw new StompProtocolException(
                br.getKString(br.X_STOMP_PROTOCOL_VERSION_NO_SUPPORT, 
                StompFrameMessage.STOMP_PROTOCOL_VERSION_10));
        }
        StringTokenizer st = new StringTokenizer(acceptVersions, ",");
        String ver = null;
        while (st.hasMoreElements()) {
            ver = st.nextToken();
            if (Version.compareVersions(ver,
                StompFrameMessage.STOMP_PROTOCOL_VERSION_12) == 0) {
                return StompFrameMessage.STOMP_PROTOCOL_VERSION_12;
            }
        }
        throw new StompProtocolException(br.getKString(
            br.X_STOMP_PROTOCOL_VERSION_NO_SUPPORT, acceptVersions));
    }


    @Override
    public StompFrameMessageFactory getStompFrameMessageFactory() {
        return StompFrameMessageImpl.getFactory();
    }

    @Override
    public String getTemporaryQueuePrefix() {
        return ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX+
            ClientConstants.TEMPORARY_QUEUE_URI_NAME;
    }

    @Override
    public String getTemporaryTopicPrefix() {
     	return ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX+
            ClientConstants.TEMPORARY_TOPIC_URI_NAME;
    }

    protected String getKStringI_CLOSE_STOMP_CONN(String stompconn) {
        return br.getKString(br.I_STOMP_CLOSE_CONN, stompconn);
    }
    protected String getKStringW_CLOSE_STOMP_CONN_FAILED(String stompconn, String emsg) {
        return br.getKString(br.W_STOMP_CLOSE_CONN_FAILED, stompconn, emsg);
    }
    protected String getKStringE_COMMAND_FAILED(String cmd, String emsg, String stompconn) {
        String[] args = { cmd, emsg, stompconn };
        return br.getKString(br.E_STOMP_COMMAND_FAILED, args);
    }
    protected String getKStringE_UNABLE_SEND_ERROR_MSG(String emsg, String eemsg) {
        return br.getKString(br.E_STOMP_UNABLE_SEND_ERROR_MSG, emsg, eemsg);
    }
    protected String getKStringX_SUBID_ALREADY_EXISTS(String subid) {
        return br.getKString(br.X_STOMP_SUBID_ALREADY_EXISTS, subid);
    }
    protected String getKStringX_UNSUBSCRIBE_WITHOUT_HEADER(
        String destHeader, String subidHeader) {
        return br.getKString(br.X_STOMP_UNSUBSCRIBE_WITHOUT_HEADER,
                             destHeader, subidHeader);
    }
    protected String getKStringX_HEADER_NOT_SPECIFIED_FOR(String header, String cmd) {
        return br.getKString(br.X_STOMP_HEADER_NOT_SPECIFIED_FOR, header, cmd);
    }
    protected String getKStringX_SUBSCRIBER_ID_NOT_FOUND(String subid) {
        return br.getKString(br.X_STOMP_SUBSCRIBER_ID_NOT_FOUND, subid);
    }
    protected String getKStringW_NO_SUBID_TXNACK(String subidHeader,
        String tid, String subidPrefix, String msgid) {
        //not supported
        return br.getKString(br.X_STOMP_HEADER_NOT_SPECIFIED_FOR,
               subidHeader, "ACK[tid="+tid+", "+msgid+"]");
    }
    protected String getKStringW_NO_SUBID_NONTXNACK(
        String subidHeader, String subidPrefix, String msgid) {
        //not supported
        return br.getKString(br.X_STOMP_HEADER_NOT_SPECIFIED_FOR, 
                             subidHeader, "ACK["+msgid+"]");
    }
    protected String getKStringX_INVALID_MESSAGE_PROP_NAME(String name) {
        return br.getKString(br.X_STOMP_INVALID_MESSAGE_PROP_NAME, name);
    }
    @Override
    protected String getKStringX_INVALID_HEADER_VALUE(String header, String value) {
        return br.getKString(br.X_STOMP_INVALID_HEADER_VALUE, value, header);
    }
    @Override
    protected String getKStringI_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR(
        String useHeader, String ignoreHeaders, String cmd) {
        String[] args = { useHeader, ignoreHeaders, cmd };
        return br.getKString(br.I_STOMP_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR, args);
    }

}
