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
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.websocket;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.nio.channels.spi.AbstractSelectableChannel;
import org.glassfish.grizzly.PortRange;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.net.ProtocolStreams;
import com.sun.messaging.jmq.jmsserver.net.ProtocolCallback;
import com.sun.messaging.jmq.jmsserver.net.tcp.TcpProtocol;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.imq.grizzly.GrizzlyProtocolImpl;

/**
 * @author amyk
 */
public class WebSocketProtocolImpl extends GrizzlyProtocolImpl
{

    private static final int DEFAULT_WS_PORT = 7670; 
    private static final int DEFAULT_WSS_PORT = 7671;

    public WebSocketProtocolImpl(WebSocketIPService s, String proto) {
        super(s, proto);
        if (proto.equals("ws")) {
            port = DEFAULT_WS_PORT;
        } else {
            port = DEFAULT_WSS_PORT;
        }
    }

    @Override
    protected boolean isSSLProtocol() {
        if (proto.equals("wss")) { 
            return true;
        }
        return false;
    }

    public PortRange getPortRange() {
        if (port <= 0) {
            return new PortRange(1, 65535);
        }
        return new PortRange(port);
    }

}
