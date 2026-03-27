/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.httptunnel.api.share;

/**
 * Protocol constants, packet types, default values etc.
 */
public final class HttpTunnelDefaultPorts {
    private HttpTunnelDefaultPorts() {
        throw new UnsupportedOperationException();
    }

    /**
     * Default listening port for the TCP connection between the servlet and the <code>HttpTunnelServerDriver</code>.
     */
    public static final int DEFAULT_HTTP_TUNNEL_PORT = 7675;
    public static final int DEFAULT_HTTPS_TUNNEL_PORT = 7674;
}

