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
 * @(#)AdminMQAddress.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import com.sun.messaging.jmq.io.MQAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

public class AdminMQAddress extends MQAddress  {
    private static final String	DEFAULT_SERVICE_NAME		= "admin";

    protected AdminMQAddress() {} 

    public String getDefaultServiceName()  {
        return (DEFAULT_SERVICE_NAME);
    }

    /**
     * Parses the given MQ Message Service Address and creates an
     * MQAddress object.
     */
    public static AdminMQAddress createAddress(String addr)
        throws MalformedURLException, UnknownHostException {
        AdminMQAddress ret = new AdminMQAddress();
        ret.initialize(addr);
        return ret;
    }


}
