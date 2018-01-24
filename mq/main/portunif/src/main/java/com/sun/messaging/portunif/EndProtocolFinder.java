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

package com.sun.messaging.portunif;

import java.nio.charset.Charset;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.portunif.PUContext;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;



public class EndProtocolFinder implements ProtocolFinder {

    PUServiceCallback cb = null;

    public EndProtocolFinder(PUServiceCallback cb) {
        this.cb = cb;
    }
    /**
     */
    @Override
    public Result find(final PUContext puContext, final FilterChainContext ctx) {
        String emsg = "Reject connection "+ctx.getConnection();
        if (cb != null) {
            cb.logWarn(emsg, null);
        } else {
            System.out.println(emsg);
        }
        ctx.getConnection().close();
        return Result.NOT_FOUND;
    }
}

