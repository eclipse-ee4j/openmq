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
import java.util.Map;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager; 
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.CompletionHandler;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.StompFrameMessage;
import com.sun.messaging.bridge.api.StompProtocolHandler;
import com.sun.messaging.bridge.api.StompOutputHandler;
import com.sun.messaging.bridge.service.stomp.resources.StompBridgeResources;

/**
 *
 * @author amyk
 */
public class StompMessageDispatchFilter extends BaseFilter implements StompOutputHandler {
     
     protected static final String STOMP_PROTOCOL_HANDLER_ATTR = "stomp-protocol-handler";
     private Logger _logger = null;

     private BridgeContext _bc = null;
     private Properties _jmsprop = null;
     private StompBridgeResources _sbr = null;

     public StompMessageDispatchFilter(StompServer server) {
         _logger = server.getLogger();
         _bc = server.getBridgeContext();
         _jmsprop = server.getJMSConfig();
         _sbr = server.getStompBridgeResources();
     }
    
     @Override
     public NextAction handleRead(final FilterChainContext ctx) throws IOException {
         BridgeContext bc = null;
         synchronized(this) {
             if (_bc == null || _jmsprop == null ||
                 _logger == null || _sbr == null) {
                 if (_logger != null) {
                     _logger.log(Level.WARNING, "Stomp service not ready yet");
                 }
                 throw new IOException("Stomp service not ready yet");
             }
             bc = _bc;
         }
         final Connection conn = ctx.getConnection();

        StompProtocolHandlerImpl sph = null;
        try {

        final StompFrameMessage msg = ctx.getMessage();
        sph = (StompProtocolHandlerImpl)ctx.getAttributes().
              getAttribute(StompMessageFilter.STOMP_PROTOCOL_HANDLER);

        switch (msg.getCommand()) { 
            case CONNECT:
            case STOMP:
                sph.onCONNECT(msg, this, ctx);
                break; 
            case SEND:
                sph.onSEND(msg, this, ctx);
                break; 
            case SUBSCRIBE:
                StompOutputHandler soh = new AsyncStompOutputHandler(ctx, sph, bc);

                sph.onSUBSCRIBE(msg, this, soh, ctx);

                return ctx.getSuspendingStopAction(); 

            case UNSUBSCRIBE:
                sph.onUNSUBSCRIBE(msg, this, ctx);
                break; 
            case BEGIN:
                sph.onBEGIN(msg, this, ctx);
                break; 
            case COMMIT:
                sph.onCOMMIT(msg, this, ctx);
                break; 
            case ABORT:
                sph.onABORT(msg, this, ctx);
                break; 
            case ACK:
                sph.onACK(msg, this, ctx);
                break; 
            case DISCONNECT:
                sph.onDISCONNECT(msg, this, ctx);
                break; 
            case ERROR:
                sendToClient(msg, sph, ctx);
                break; 
            default: 
                throw new IOException(
                ((StompFrameMessageImpl)msg).getKStringX_UNKNOWN_STOMP_CMD(
                     msg.getCommand().toString()));
        }
 
        } catch (Throwable t) {
            _logger.log(Level.SEVERE,  t.getMessage(), t);
            try {

            StompFrameMessage err = sph.toStompErrorMessage(
                "StompProtocolFilter", t, (t instanceof IOException));
            sendToClient(err, sph, ctx);

            } catch (Exception e) {
            _logger.log(Level.SEVERE, _sbr.getKString(_sbr.E_UNABLE_SEND_ERROR_MSG, t.toString(), e.toString()), e);
            }
        }
        return ctx.getInvokeAction();
    }

    public void sendToClient(StompFrameMessage msg) throws Exception {
        throw new UnsupportedOperationException("sendToclient(msg)");
    }
    
    public void sendToClient(final StompFrameMessage msg, 
                             StompProtocolHandler sph, 
                             final Object context) throws Exception {
        FilterChainContext ctx = (FilterChainContext)context;
        boolean closechannel = false;
        try {
            if (msg.getCommand() == StompFrameMessage.Command.ERROR) {
                if (msg.isFatalERROR()) {
                    closechannel = true;
                }
            }
            ctx.write(msg, true);

        } catch (Exception e) {
            if (e instanceof java.nio.channels.ClosedChannelException ||
                e.getCause() instanceof java.nio.channels.ClosedChannelException) { 
                _logger.log(Level.WARNING, _sbr.getKString(
                  _sbr.W_EXCEPTION_ON_SEND_MSG, msg.toString(), e.toString()));
                if (sph != null) {
                    sph.close(false);
                }
            }
            throw e;
        } finally {
            if (closechannel) {
                GrizzlyFuture f = ctx.getConnection().close();              
                try {
                    f.get();
                } catch (Exception ee) {
                    _logger.log(Level.WARNING, ee.getMessage(), ee);
                }
            }
        }
    }
    
}
