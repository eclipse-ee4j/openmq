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
 * @(#)MQAddressList.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.Random;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.net.MalformedURLException;

/**
 * This class represents broker address URL.
 */
public class MQAddressList extends ArrayList {

    protected MQAddressList() {}

    protected com.sun.messaging.jmq.io.MQAddress createMQAddress(String s)  
         throws java.net.MalformedURLException
   {
            return MQAddress.getMQAddress(s);
    }

    public static MQAddressList createAddressList(String addrs)
        throws MalformedURLException {
        MQAddressList alist = new MQAddressList();
        StringTokenizer st = new StringTokenizer(addrs, " ,");
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            MQAddress a;

            alist.add(alist.createMQAddress(s));
        }

        return alist;
    }

    public String toString() {
        StringBuffer strbuf = new StringBuffer();
        strbuf.append("");
        for (int i = 0; i < size(); i++) {
            strbuf.append("addr[" + i + "] :\t" + get(i) + "\n");
        }

        return strbuf.toString();
    }

}

/*
 * EOF
 */
