/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.jmsclient;

import java.util.Random;
import java.util.StringTokenizer;
import java.io.Serial;
import java.net.MalformedURLException;

/**
 * This class represents broker address URL.
 */
public class MQAddressList extends com.sun.messaging.jmq.io.MQAddressList {
    @Serial
    private static final long serialVersionUID = -9204049230207538102L;
    public static final int PRIORITY = 1;
    public static final int RANDOM = 2;

    private int behavior;

    @Override
    protected com.sun.messaging.jmq.io.MQAddress createMQAddress(String s) throws java.net.MalformedURLException {
        return com.sun.messaging.jmq.jmsclient.MQAddress.createMQAddress(s);
    }

    public static MQAddressList createMQAddressList(String addrs) throws MalformedURLException {
        MQAddressList alist = new MQAddressList();
        StringTokenizer st = new StringTokenizer(addrs, " ,");
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            alist.add(alist.createMQAddress(s));
        }

        return alist;
    }

    public int getBehavior() {
        return behavior;
    }

    public void setBehavior(int behavior) {
        this.behavior = behavior;

        if (behavior == RANDOM) {
            // Randomize the sequence.
            Random r = new Random();
            int max = size();

            for (int i = 0; i < max; i++) {
                int pos = i + r.nextInt(max - i);

                Object o = get(i);
                set(i, get(pos));
                set(pos, o);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            ret.append("addr[").append(i).append("] :\t").append(get(i)).append('\n');
        }

        return ret.toString();
    }

    public static void main(String[] args) throws Exception {
        MQAddressList list = createMQAddressList(args[0]);
        if (System.getProperty("test.random") != null) {
            list.setBehavior(RANDOM);
        }
        System.out.println(list);
    }
}

