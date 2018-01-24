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
 * @(#)HttpTunnelPush.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel.client;

import com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelPacket;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import java.util.Vector;


/**
 * This class provides a continuous push thread for sending packets
 * from client to server. Pushing a packet can be a time consuming
 * task. A dedicated push thread ensures that the threads generating
 * outbound packets don't waste their cycles in HTTP I/O.
 */
public class HttpTunnelPush extends Thread implements HttpTunnelDefaults {
    private URL pushUrl = null;
    private Vector q = null;
    private boolean stopThread = false;
    private boolean shutdownComplete = false;

    public HttpTunnelPush() {
    }

    /**
     * Set the default push URL and start the push thread.
     */
    public void startPushThread(URL pushUrl) {
        this.pushUrl = pushUrl;
        q = new Vector();

        setName("HttpTunnelPush");
        setDaemon(true);
        start();
    }

    /**
     * Terminate the push thread.
     */
    public void shutdown() {
        synchronized (q) {
            stopThread = true;
            q.notifyAll();
        }

        while (!shutdownComplete) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }

    /**
     * Adds a packet to the push queue and wakes up the push thread.
     * Note that the flow control mechanism automatically limits the
     * maximum queue size.
     */
    public void sendPacket(HttpTunnelPacket p) {
        synchronized (q) {
            if (stopThread) { // Don't accept any more packets.

                return;
            }

            q.addElement(p);
            q.notifyAll();
        }
    }

    /**
     * Sends a packet to a given URL and optionally reads a single
     * HttpTunnelPacket from the HTTP response stream.
     */
    public HttpTunnelPacket sendPacketDirect(URL u, HttpTunnelPacket p,
        boolean getResponse) throws Exception {
        URLConnection uc = u.openConnection();
        uc.setDoInput(true);
        uc.setDoOutput(true);
        uc.setUseCaches(false);

        uc.setRequestProperty("content-type", "application/octet-stream");

        OutputStream os = uc.getOutputStream();
        p.writePacket(os);
        os.close();

        uc.connect();

        int response = HttpURLConnection.HTTP_OK;

        if (uc instanceof HttpURLConnection) {
            response = ((HttpURLConnection) uc).getResponseCode();
        } else {
            uc.getContentType();

            // We don't really need the content type. This just forces
            // the uc to do its job.
        }

        InputStream is = uc.getInputStream();

        if (getResponse == false) {
            is.close();

            return null;
        }

        if (response != HttpURLConnection.HTTP_OK) {
            is.close();
            throw new IOException("Failed to receive response");
        }

        HttpTunnelPacket ret = new HttpTunnelPacket();
        ret.readPacket(is);
        is.close();

        return ret;
    }

    public void run() {
        while (true) {
            HttpTunnelPacket p = null;

            synchronized (q) {
                while (q.isEmpty() && (stopThread == false)) {
                    try {
                        q.wait();
                    } catch (Exception e) {
                    }
                }

                if (stopThread && q.isEmpty()) {
                    break;
                }

                p = (HttpTunnelPacket) q.elementAt(0);
                q.removeElementAt(0);
            }

            try {
                sendPacketDirect(pushUrl, p, false);
            } catch (Exception e) {
            }
        }

        shutdownComplete = true;
    }
}

/*
 * EOF
 */
