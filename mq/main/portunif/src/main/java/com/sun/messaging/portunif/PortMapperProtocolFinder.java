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



public class PortMapperProtocolFinder implements ProtocolFinder {

    private static boolean DEBUG = false;
    public static final int PORTMAPPER_VERSION_MAX_LEN = 128;

    private PUServiceCallback callback = null;
    private boolean ssl = false;

    public PortMapperProtocolFinder(PUServiceCallback callback, boolean ssl) {
        this.callback = callback;
        this.ssl = ssl;
    }

    /**
     */
    @Override
    public Result find(final PUContext puContext, final FilterChainContext ctx) {

        final Buffer input = ctx.getMessage();

        int len = input.remaining();
        if (len <= 0) {
            return Result.NEED_MORE_DATA;
        }
        String data = input.toStringContent(Charset.forName("UTF-8"), 0,
                                Math.min(PORTMAPPER_VERSION_MAX_LEN, len));
        int ind1 = data.indexOf("\r");
        int ind2 = data.indexOf("\n");
        if (DEBUG) {
            String logmsg = this+": input="+input.toStringContent()+
                                ", newline index: "+ind1+", "+ind2;
            if (callback != null) {
                callback.logInfo(logmsg);
            } else {
                System.out.println(logmsg);
            }
        }

        if (ind1 == 0 || ind2 == 0) {
            return Result.NOT_FOUND;
        }
        int indmax = Math.max(ind1, ind2);
        if (indmax < 0) {
            if (len >= PORTMAPPER_VERSION_MAX_LEN) {
                return Result.NOT_FOUND;
            }
            return Result.NEED_MORE_DATA;
        }
        
        int indmin = Math.min(ind1, ind2);
        if (!data.substring(0, (indmin < 0 ? indmax:indmin)).matches("\\d+")) {
            if (DEBUG) {
                String logmsg = this+": data not all digits before newline:["+
                                data.substring(0, (indmin < 0 ? indmax:indmin))+"]";
                if (callback !=  null) {
                    callback.logInfo(logmsg);
                } else {
                    System.out.println(logmsg);
                }
            }
            return Result.NOT_FOUND;
        }
        if (DEBUG) {
            String logmsg = this+": FOUND input="+input.toStringContent();
            if (callback !=  null) {
                callback.logInfo(logmsg);
            } else {
                System.out.println(logmsg);
            }
        }
        if (callback == null) {
            return Result.FOUND;
        }
        Connection c = ctx.getConnection();
        if (c instanceof TCPNIOConnection) {
            SocketAddress sa = ((TCPNIOConnection)c).getPeerAddress();
            if (sa instanceof InetSocketAddress) {
                if (callback.allowConnection((InetSocketAddress)sa, ssl)) {
                    return Result.FOUND;
                }
            }
        }
        return Result.NOT_FOUND;
    }
}

