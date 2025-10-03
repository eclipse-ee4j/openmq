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

package com.sun.messaging.jmq.httptunnel.tunnel.test;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.System.Logger;

import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;
import java.lang.System.Logger.Level;

class Reader extends Thread {

    private static final Logger logger = System.getLogger(Reader.class.getName());

    private HttpTunnelSocket s = null;
    private InputStream is = null;
    private static int VERBOSITY = Integer.getInteger("test.verbosity", 0).intValue();
    private static int MAX = Integer.getInteger("test.max", -1).intValue();
    private static int PULLPERIOD = Integer.getInteger("test.pullperiod", -1).intValue();

    Reader(HttpTunnelSocket s) {
        this.s = s;
        try {
            s.setPullPeriod(PULLPERIOD);
            this.is = s.getInputStream();
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        try {
            ObjectInputStream dis = new FilteringObjectInputStream(is);
            int n = 0;

            while (MAX < 0 || n < MAX) {
                RandomBytes rb = (RandomBytes) dis.readObject();
                boolean valid = rb.isValid();

                int seq = rb.getSequence();

                if (seq != n) {
                    logger.log(Level.ERROR, "#### PACKET OUT OF SEQUENCE ####");
                    return;
                }

                if (VERBOSITY > 0) {
                    logger.log(Level.DEBUG, "#### Received packet #" + seq);
                }

                if (VERBOSITY > 1) {
                    byte[] tmp = rb.getData();
                    int len = tmp.length > 64 ? 64 : tmp.length;

                    StringBuilder sb = new StringBuilder();
                    sb.append("Bytes = " + new String(tmp, 1, len - 1) + "\n");
                    sb.append("Length = " + (tmp.length - 1) + "\n");
                    sb.append("Checksum = " + rb.getChecksum() + "\n");
                    sb.append("Computed checksum = " + RandomBytes.computeChecksum(tmp) + "\n");
                    sb.append("rb.isValid() = " + valid + "\n");
                    logger.log(Level.TRACE, sb.toString());

                }

                if (!valid) {
                    logger.log(Level.ERROR, "#### CHECKSUM ERROR DETECTED ####");
                    return;
                }

                n++;
                if (n % 100 == 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("#### Free memory = " + Runtime.getRuntime().freeMemory() + "\n");
                    sb.append("#### Total memory = " + Runtime.getRuntime().totalMemory() + "\n");
                    logger.log(Level.INFO, sb.toString());
                }
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }

        try {
            s.close();
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
        logger.log(Level.DEBUG, "#### Reader exiting...");
    }
}

