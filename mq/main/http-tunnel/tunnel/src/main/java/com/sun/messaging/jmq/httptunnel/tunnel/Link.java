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
 * @(#)Link.java	1.11 09/11/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class provides a common mechanism for establishing / maintaining
 * and consuming packets from a TCP connection.
 */
public abstract class Link extends Thread {
    private static boolean DEBUG = Boolean.getBoolean("httptunnel.debug");
    private boolean connected = false;
    protected boolean done = false;
    protected InputStream is = null;
    protected OutputStream os = null;
    private Logger logger = Logger.getLogger("Http Tunneling");

    /**
     * Establish the connection. This method blocks and keeps trying
     * until a connection is established.
     */
    protected abstract void createLink();

    /**
     * Consume a packet received over this connection.
     */
    protected abstract void receivePacket(HttpTunnelPacket p);

    /**
     * Handle connection error.
     */
    protected abstract void handleLinkDown();

    /**
     * Send a packet over this connection.
     */
    public synchronized void sendPacket(HttpTunnelPacket p) {
        if (DEBUG) {
            log("Sending packet : " + p);
        }

        try {
            p.writePacket(os);
        } catch (Exception e) {
            if (DEBUG) {
                log(e);
            }

            linkDown();
        }
    }

    protected void linkDown() {
        try {
            is.close();
            os.close();
            handleLinkDown();
        } catch (Exception e) {
        }

        connected = false;
    }

    public void shutdown() {
        done = true;
    }

    public void run() {
        while (!done) {
            try {
                if (connected == false) {
                    createLink();
                    connected = true;
                }

                HttpTunnelPacket p = new HttpTunnelPacket();
                p.readPacket(is);
                receivePacket(p);
            } catch (IllegalStateException e) {
                if (DEBUG) {
                    log(e);
                }

                done = true;
            } catch (Exception e) {
                if (DEBUG) {
                    log(e);
                }

                linkDown();
            }
        }
    }

    protected void log(String msg) {
        logger.log(Level.INFO, msg);
    }

    protected void log(Exception ex) {
    	logger.log(Level.INFO, "Http Tunneling", ex);
    }

    protected void log(Level v, String msg, Exception ex) {
    	logger.log(v, msg, ex);
    }
}

/*
 * EOF
 */
