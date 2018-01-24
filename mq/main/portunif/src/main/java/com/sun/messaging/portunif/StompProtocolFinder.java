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
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.portunif.PUContext;
import org.glassfish.grizzly.portunif.ProtocolFinder;
import org.glassfish.grizzly.filterchain.FilterChainContext;


public class StompProtocolFinder implements ProtocolFinder {

    private static boolean DEBUG = false;

    private byte[] cmd1 = null;
    private byte[] cmd2 = null;

    public StompProtocolFinder() {
        this.cmd1 = "STOMP".getBytes(Charset.forName("UTF-8"));
        this.cmd2 = "CONNECT".getBytes(Charset.forName("UTF-8"));
    }

    /**
     */
    @Override
    public Result find(final PUContext puContext, final FilterChainContext ctx) {
        Result res = findInternal(cmd1, puContext, ctx);
        switch (res) {
            case NEED_MORE_DATA:
                  return res;
            case FOUND:
                  return res;
            case NOT_FOUND:
                  return findInternal(cmd2, puContext, ctx);
            default: 
                  return Result.NOT_FOUND;
        }
    }

    public Result findInternal(byte[] cmd, final PUContext puContext, final FilterChainContext ctx) {

        final Buffer input = ctx.getMessage();
        if (DEBUG) {
            System.out.println(this+": input="+input.toStringContent());
        }

        int pos = input.position();
        int len = input.remaining();
        if (len <= cmd.length) {
            return Result.NEED_MORE_DATA;
        }
        for (int i = 0; i < cmd.length; i++) {
            if (cmd[i] != input.get(i)) {
                return Result.NOT_FOUND;
            }
        }
        byte b = input.get(cmd.length);
        if (b != '\n' && b != '\r') {
            return Result.NEED_MORE_DATA;
        }
        input.position(pos);
        if (DEBUG) {
            System.out.println(this+": FOUND input="+input.toStringContent());
        }
        return Result.FOUND;
    }

}

