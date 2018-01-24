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

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.glassfish.grizzly.GrizzlyFuture; 
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 *
 * @author amyk
 */
public class AsyncStompOutputHandler implements StompOutputHandler {

    private  Logger _logger = null;

    private FilterChainContext _context = null;
    private StompProtocolHandler _sph = null;
    private BridgeContext _bc = null;
    private StompBridgeResources _sbr = null;
     
    public AsyncStompOutputHandler(FilterChainContext ctx,
                                   StompProtocolHandlerImpl sph,
                                   BridgeContext bc) { 
        _context = ctx;
        _sbr = sph.getStompBridgeResources();
        _logger = sph.getLogger();
        _sph = sph;
        _bc = bc;
    }

    public void sendToClient(StompFrameMessage msg, 
                             StompProtocolHandler sph,
                             Object ctx) throws Exception {

        throw new UnsupportedOperationException("sendToClient(msg, ctx, sph)");
    }

    public void sendToClient(final StompFrameMessage msg) throws Exception {
        boolean closechannel = false;
        if (msg.getCommand() == StompFrameMessage.Command.ERROR) {
            if (msg.isFatalERROR()) {
                closechannel = true;
            }
        }
        try {
            _context.write(msg, true);
        } catch (Exception e) {
            if (e instanceof java.nio.channels.ClosedChannelException ||
                e.getCause() instanceof java.nio.channels.ClosedChannelException) {
                _logger.log(Level.WARNING, StompServer.getStompBridgeResources().getKString(
                  StompServer.getStompBridgeResources().W_SEND_MSG_TO_CLIENT_FAILED, msg.toString(), e.toString()));
                _sph.close(true);
                throw e;
            }
        } finally {
            if (closechannel) {
                GrizzlyFuture f = _context.getConnection().close();
                try {
                    f.get();
                } catch (Exception ee) {
                    _logger.log(Level.WARNING, ee.getMessage(), ee);
                }
            }
        }
    }
}
