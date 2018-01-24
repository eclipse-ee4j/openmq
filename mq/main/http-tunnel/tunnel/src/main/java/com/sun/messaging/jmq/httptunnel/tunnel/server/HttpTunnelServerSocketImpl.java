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

package com.sun.messaging.jmq.httptunnel.tunnel.server;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.httptunnel.tunnel.*;
import com.sun.messaging.jmq.httptunnel.api.server.*;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;

/**
 * This class implements server sockets for HTTP tunnel protocol.
 * A server socket waits for connection requests from the clients.
 */
public class HttpTunnelServerSocketImpl implements HttpTunnelServerSocket {
    private Vector listenQ = null;
    private boolean closed;
    private HttpTunnelServerDriver wire = null;

    public HttpTunnelServerSocketImpl() {
    }

    /**
     * Creates a server socket.
     */
    public void init(HttpTunnelServerDriver wire) throws IOException {
        listenQ = wire.getListenQ();
        closed = false;
        this.wire = wire;

        wire.listen(true);
    }

    /**
     * Listens for a connection to be made to this socket and accepts
     * it. The method blocks until a connection is made. 
     */
    public HttpTunnelSocket accept() throws IOException {
        synchronized (listenQ) {
            while (listenQ.isEmpty()) {

                if (closed)
                    break;

                try {
                    listenQ.wait(5000);
                }
                catch (Exception e) {}
            }

            if (closed) {
                if (! listenQ.isEmpty())
                    listenQ.notifyAll(); // Wakeup the next thread
                throw new IOException("Socket closed");
            }

            HttpTunnelConnection conn =
                (HttpTunnelConnection) listenQ.elementAt(0);
            listenQ.removeElementAt(0);
            return new HttpTunnelSocketImpl(conn);
        }
    }

    /**
     * Closes this server socket.
     */
    public void close() throws IOException {
        wire.listen(false);

        synchronized (listenQ) {
            closed = true;
            listenQ.notifyAll();
        }
    }
}

/*
 * EOF
 */
