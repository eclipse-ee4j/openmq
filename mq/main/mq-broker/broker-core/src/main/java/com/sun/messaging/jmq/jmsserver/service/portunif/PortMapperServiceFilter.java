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

package com.sun.messaging.jmq.jmsserver.service.portunif;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.CompletionHandler; 
import org.glassfish.grizzly.memory.MemoryManager;
import com.sun.messaging.jmq.jmsserver.service.PortMapper;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;


public class PortMapperServiceFilter extends BaseFilter {

    private PortMapper pm = null;

    private boolean ssl = false;

    public PortMapperServiceFilter(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
    throws IOException {

        final Logger logger = Globals.getLogger();

        if (ssl) {
            logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                BrokerResources.I_PORTMAPPER_GOT_CONNECTION, "SSL/TLS",
                ctx.getConnection().getPeerAddress())); 
        }

        ByteArrayOutputStream bos =  new ByteArrayOutputStream();
        synchronized(this) {
            if (pm == null) {
                pm = Globals.getPortMapper();
                if (pm == null) {//XXX
                    throw new IOException("Broker portmapper not ready yet");
                }
            }
            pm.getPortMapTable().write(bos);
        }
        byte[] reply = bos.toByteArray();
        bos.close();

        if (PortMapperMessageFilter.DEBUG) {
            logger.log(logger.INFO, 
            "PortMapperServiceFilter.handleRead() write data size "+reply.length+
            " to connection "+ctx.getConnection());
        }

        final MemoryManager mm = ctx.getConnection().
                            getTransport().getMemoryManager();
        final Buffer output = mm.allocate(reply.length);
        output.put(reply);
        output.allowBufferDispose();

        final CloseCompletionHandler cch = new CloseCompletionHandler(ctx);
        ctx.write(output, new CompletionHandler<WriteResult>() {
                          @Override
                          public void cancelled(){
                          	ctx.getConnection().close(cch);
                          }

                          @Override
                          public void failed(Throwable t) {
                       	  	ctx.getConnection().close(cch);
                          }

                          @Override
                          public void completed(WriteResult w) {
                          	ctx.getConnection().close(cch);
                          }

                          @Override
                          public void updated(WriteResult w) {
                          }
		                  } );

        return ctx.getStopAction();
    }

    private static class CloseCompletionHandler implements CompletionHandler<Connection> {
        Logger logger = Globals.getLogger();
        FilterChainContext ctx = null;

        CloseCompletionHandler(final FilterChainContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void cancelled() {
            logger.log(logger.WARNING, "Close ["+ctx+"] connection cancelled");
        }

        @Override
        public void failed(Throwable t) {
            logger.logStack(logger.WARNING, "Close ["+ctx+"] connection failed", t);
        }

        @Override
        public void completed(Connection c) {
            logger.log(logger.DEBUGHIGH, "Close ["+ctx+":"+c+"] connection complete");
        }

        @Override
        public void updated(Connection c) { }
    }
}

