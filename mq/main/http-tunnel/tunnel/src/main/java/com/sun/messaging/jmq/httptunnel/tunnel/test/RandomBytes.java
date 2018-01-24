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
 * @(#)RandomBytes.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel.test;

import java.io.*;
import java.util.Random;

class RandomBytes implements Serializable {
    private byte[] data = null;
    private int sequence = 0;

    public static byte computeChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += (data[i] & 0xff);
            if (sum > 255)
                sum = (sum & 0xff) + 1;
        }
        return (byte) ~((sum & 0xff));
    }

    public RandomBytes(int maxlen) {
        Random r = new Random();

        //int len = r.nextInt(maxlen) + 1;
        int len = (int)(r.nextFloat()*maxlen) + 1;
        data = new byte[len];

        data[0] = 0;
        for (int i = 1; i < len; i++) {
            //data[i] = (byte) (32 + r.nextInt(96));
            data[i] = (byte) (32 + (int)(r.nextFloat()*96));
        }
        data[0] = computeChecksum(data);
    }

    public RandomBytes(String str) {
        byte[] in = str.getBytes();
        data = new byte[in.length + 1];
        data[0] = 0;
        System.arraycopy(in, 0, data, 1, in.length);
        data[0] = computeChecksum(data);
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public int getSequence() {
        return sequence;
    }

    public byte getChecksum() {
        return data[0];
    }

    public byte[] getData() {
        return data;
    }

    public boolean isValid() {
        return (computeChecksum(data) == 0);
    }

    public static void main(String args[]) {
        int maxlen = 64;
        if (args.length > 0) {
            try {
                maxlen = Integer.parseInt(args[0]);
            }
            catch (Exception e) {
                maxlen = -1;
            }
        }
        RandomBytes rb;
        if (maxlen < 0)
            rb = new RandomBytes(args[0]);
        else
            rb = new RandomBytes(maxlen);

        byte[] tmp = rb.getData();
        int len = tmp.length;
        System.out.println("Bytes = " + new String(tmp, 1, len - 1));
        System.out.println("Length = " + (len - 1));
        System.out.println("Checksum = " + rb.getChecksum());
        System.out.println("Computed checksum = " +
            computeChecksum(tmp));
        System.out.println("rb.isValid() = " + rb.isValid());
    }
}

/*
 * EOF
 */
