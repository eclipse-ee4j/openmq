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
 * @(#)Writer.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel.test;

import java.io.*;
import java.util.Random;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelSocket;

class Writer extends Thread {
    private HttpTunnelSocket s = null;
    private OutputStream os = null;

    private static int SLEEP =
        Integer.getInteger("test.sleep", 0).intValue();
    private static int DATASIZE =
        Integer.getInteger("test.datasize", 32768).intValue();
    private static int VERBOSITY =
        Integer.getInteger("test.verbosity", 0).intValue();
    private static int MAX =
        Integer.getInteger("test.max", -1).intValue();
    private static int PULLPERIOD =
        Integer.getInteger("test.pullperiod", -1).intValue();

    public Writer(HttpTunnelSocket s) {
        this.s = s;
        try {
            s.setPullPeriod(PULLPERIOD);
            this.os = s.getOutputStream();;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os, 8192);
            ObjectOutputStream dos = new ObjectOutputStream(bos);

            int n = 0;
            Random r = new Random();
            while (MAX < 0 || n < MAX) {
                RandomBytes rb = new RandomBytes(DATASIZE);
                rb.setSequence(n);

                dos.writeObject(rb);
                dos.flush();
                dos.reset();
                if (SLEEP > 0)
    //                Thread.sleep(r.nextInt(SLEEP) * 1000);
                    Thread.sleep((int)(r.nextFloat() * 1000));

                n++;
                if (VERBOSITY > 0)
                    System.out.println("#### Sent packet #" + n);
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
        System.out.println("#### Writer exiting...");
    }
}

/*
 * EOF
 */
