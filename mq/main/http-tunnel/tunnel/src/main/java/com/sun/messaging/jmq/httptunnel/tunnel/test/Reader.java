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
 * @(#)Reader.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel.test;

import java.io.InputStream;
import java.io.ObjectInputStream;

import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

class Reader extends Thread {
    private HttpTunnelSocket s = null;
    private InputStream is = null;
    private static int VERBOSITY =
        Integer.getInteger("test.verbosity", 0).intValue();
    private static int MAX =
        Integer.getInteger("test.max", -1).intValue();
    private static int PULLPERIOD =
        Integer.getInteger("test.pullperiod", -1).intValue();

    public Reader(HttpTunnelSocket s) {
        this.s = s;
        try {
            s.setPullPeriod(PULLPERIOD);
            this.is = s.getInputStream();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            ObjectInputStream dis = new FilteringObjectInputStream(is); 
            int n = 0;

            while (MAX < 0 || n < MAX) {
                RandomBytes rb = (RandomBytes) dis.readObject();
                boolean valid = rb.isValid();

                int seq = rb.getSequence();

                if (seq != n) {
                    System.out.println(
                        "#### PACKET OUT OF SEQUENCE ####");
                    return;
                }

                if (VERBOSITY > 0) {
                    System.out.println("#### Received packet #" + seq);
                }

                if (VERBOSITY > 1) {
                    byte[] tmp = rb.getData();
                    int len = tmp.length > 64 ? 64 : tmp.length;

                    System.out.println("Bytes = " +
                        new String(tmp, 1, len - 1));
                    System.out.println("Length = " + (tmp.length - 1));
                    System.out.println("Checksum = " + rb.getChecksum());
                    System.out.println("Computed checksum = " +
                        RandomBytes.computeChecksum(tmp));
                    System.out.println("rb.isValid() = " + valid);
                    System.out.println();
                }

                if (! valid) {
                    System.out.println(
                        "#### CHECKSUM ERROR DETECTED ####");
                    return;
                }

                n++;
                if (n % 100 == 0) {
                    System.out.println("#### Free memory = " +
                        Runtime.getRuntime().freeMemory());
                    System.out.println("#### Total memory = " +
                        Runtime.getRuntime().totalMemory());
                    System.out.println();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            s.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("#### Reader exiting...");
    }
}

/*
 * EOF
 */
