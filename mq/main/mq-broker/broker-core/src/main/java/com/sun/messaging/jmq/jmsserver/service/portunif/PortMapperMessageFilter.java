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

import java.util.Properties;
import java.io.IOException;
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.portunif.PUFilter;
import org.glassfish.grizzly.portunif.PUProtocol;
import com.sun.messaging.portunif.PUService;
import com.sun.messaging.portunif.PortMapperProtocolFinder;
import com.sun.messaging.portunif.PUServiceCallback;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.PortMapper;


public class PortMapperMessageFilter extends BaseFilter {
   
    protected static boolean DEBUG = false;

    /**
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
    throws IOException {

        final Buffer input = ctx.getMessage();

        String data = input.toStringContent(Charset.forName("UTF-8"));
        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,
            "PortMapperMessageFilter.handleRead called with data="+data+
            " from connection "+ctx.getConnection());
        }
        input.tryDispose();

        return ctx.getInvokeAction();
    }

    /**
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
    throws IOException {

        final Buffer output = ctx.getMessage();
        output.flip();

        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO,
            "PortMapperMessageFilter.handleWrite called with data size "+output.remaining()+
            " for connection "+ctx.getConnection());
        }
        ctx.setMessage(output);

        return ctx.getInvokeAction();
    }

    public static PUProtocol configurePortMapperProtocol(
        PUService pu, 
        PUServiceCallback cb) throws IOException {

        final FilterChain pmProtocolFilterChain =
                pu.getPUFilterChainBuilder()
                    .add(new PortMapperMessageFilter())
                    .add(new PortMapperServiceFilter(false))
                    .build();
        return new PUProtocol(new PortMapperProtocolFinder(cb, false),
                              pmProtocolFilterChain);
    }

    public static PUProtocol configurePortMapperSSLProtocol(
        PUService pu, 
        PUServiceCallback cb, 
        Properties sslprops, boolean clientAuthRequired)
        throws IOException {

        if (!pu.initializeSSL(sslprops, clientAuthRequired, cb, 
                 Globals.getPoodleFixEnabled(),
                 Globals.getKnownSSLEnabledProtocols("PortMapper"))) {
            throw new IOException(
            "Unexpected: Someone initialized SSL PUService before PortMapper service");
        }

        final FilterChain pmSSLProtocolFilterChain =
                pu.getSSLPUFilterChainBuilder()
                    .add(new PortMapperMessageFilter())
                    .add(new PortMapperServiceFilter(true))
                    .build();
        return new PUProtocol(new PortMapperProtocolFinder(cb, true),
                              pmSSLProtocolFilterChain);
    }
}

