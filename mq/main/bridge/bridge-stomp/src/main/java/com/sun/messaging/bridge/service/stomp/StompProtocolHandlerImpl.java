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

package com.sun.messaging.bridge.service.stomp;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.Destination;
import javax.jms.JMSException;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.util.LoggerWrapper;
import com.sun.messaging.bridge.api.StompSession;
import com.sun.messaging.bridge.api.StompDestination;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.MessageTransformer;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompProtocolException;
import com.sun.messaging.bridge.api.StompFrameMessageFactory;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 * @author amyk 
 */
public class StompProtocolHandlerImpl extends StompProtocolHandler {

    private static final Version mqversion = new Version();

    private StompBridgeResources sbr = null;
    private StompServer server = null;

    public StompProtocolHandlerImpl(StompServer server) {
        super(new LoggerWrapperImpl(server.getLogger()));
        this.server = server;
        sbr = server.getStompBridgeResources();
        stompConnection = new StompConnectionImpl(this);
    }

    BridgeContext getBridgeContext() {
        return server.getBridgeContext();
    }

    Properties getJMSConfig() {
        return server.getJMSConfig();
    }

    Logger getLogger() {
        return server.getLogger();
    }

    MessageTransformer getMessageTransformer() {
        return server.getMessageTransformer();
    }

    StompBridgeResources getStompBridgeResources() {
        return server.getStompBridgeResources();
    }

    @Override
    public String getSupportedVersions() {
        return StompFrameMessage.STOMP_PROTOCOL_VERSION_10+","+
               StompFrameMessage.STOMP_PROTOCOL_VERSION_12; 
    }

    @Override
    public String getServerName() {
        return mqversion.getProductName();
    }

    @Override
    public String negotiateVersion(String acceptVersions) 
    throws StompProtocolException {
        if (acceptVersions == null) {
            return StompFrameMessage.STOMP_PROTOCOL_VERSION_10; 
        }
        StringTokenizer st = new StringTokenizer(acceptVersions, ",");
        String ver = null;
        while (st.hasMoreElements()) {
            ver = st.nextToken();
            if (Version.compareVersions(ver, 
                StompFrameMessage.STOMP_PROTOCOL_VERSION_12) == 0) {
                return StompFrameMessage.STOMP_PROTOCOL_VERSION_12; 
            }
            if (Version.compareVersions(ver, 
                StompFrameMessage.STOMP_PROTOCOL_VERSION_10) == 0) {
                return StompFrameMessage.STOMP_PROTOCOL_VERSION_10; 
            }
        }
        throw new StompProtocolException(sbr.getKString(
            sbr.X_PROTOCOL_VERSION_NO_SUPPORT, acceptVersions));
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

    @Override
    protected String getKStringI_CLOSE_STOMP_CONN(String stompconn) {
        return sbr.getKString(sbr.I_CLOSE_STOMP_CONN, stompconn);
    }
    @Override
    protected String getKStringW_CLOSE_STOMP_CONN_FAILED(String stompconn, String emsg) {
        return sbr.getKString(sbr.W_CLOSE_STOMP_CONN_FAILED, stompconn, emsg);
    }
    @Override
    protected String getKStringE_COMMAND_FAILED(String cmd, String emsg, String stompconn) {
        String[] eparam = { cmd, emsg, stompconn };
        return sbr.getKString(sbr.E_COMMAND_FAILED, eparam);
    }
    @Override
    protected String getKStringE_UNABLE_SEND_ERROR_MSG(String emsg, String eemsg) {
        return sbr.getKString(sbr.E_UNABLE_SEND_ERROR_MSG, emsg, eemsg);
    }
    @Override
    protected String getKStringX_SUBID_ALREADY_EXISTS(String subid) {
        return sbr.getKString(sbr.X_SUBID_ALREADY_EXISTS, subid);
    }
    @Override
    protected String getKStringX_UNSUBSCRIBE_WITHOUT_HEADER(String destHeader, String subidHeader) {
        return sbr.getKString(sbr.X_UNSUBSCRIBE_WITHOUT_HEADER, destHeader, subidHeader);
    }
    @Override
    protected String getKStringX_HEADER_NOT_SPECIFIED_FOR(String header, String cmd) {
        return sbr.getKString(sbr.X_HEADER_NOT_SPECIFIED_FOR, header, cmd);
    }
    @Override
    protected String getKStringX_SUBSCRIBER_ID_NOT_FOUND(String subid) {
        return sbr.getKString(sbr.X_SUBSCRIBER_ID_NOT_FOUND, subid);
    }
    @Override
    protected String getKStringW_NO_SUBID_TXNACK(String subidHeader, String tid, String subidPrefix, String msgid) {
        String[] eparam = { subidHeader, tid, subidPrefix, msgid };
        return sbr.getKString(sbr.W_NO_SUBID_TXNACK, eparam);
    }
    @Override
    protected String getKStringW_NO_SUBID_NONTXNACK(String subidHeader, String subidPrefix, String msgid) {
        String[] eparam = { subidHeader, subidPrefix, msgid };
        return sbr.getKString(sbr.W_NO_SUBID_NONTXNACK, eparam);
    }
    @Override
    protected String getKStringX_INVALID_MESSAGE_PROP_NAME(String name) {
        return sbr.getKString(sbr.X_INVALID_MESSAGE_PROP_NAME, name);
    }
    @Override
    protected String getKStringX_INVALID_HEADER_VALUE(String header, String value) {
        return sbr.getKString(sbr.X_INVALID_HEADER_VALUE, header, value);
    }
    @Override
    protected String getKStringI_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR(
        String useHeader, String ignoreHeaders, String cmd) {
        String[] args = { useHeader, ignoreHeaders, cmd };
        return sbr.getKString(sbr.I_USE_HEADER_IGNORE_OBSOLETE_HEADER_FOR, args);
    }

}
