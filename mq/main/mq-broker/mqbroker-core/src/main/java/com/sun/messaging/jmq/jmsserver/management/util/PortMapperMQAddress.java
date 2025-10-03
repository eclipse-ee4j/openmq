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

package com.sun.messaging.jmq.jmsserver.management.util;

import com.sun.messaging.jmq.io.MQAddress;
import java.io.Serial;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

public class PortMapperMQAddress extends MQAddress {
    @Serial
    private static final long serialVersionUID = 9217668026006232968L;

    protected PortMapperMQAddress() {
    }

    @Override
    public String getDefaultServiceName() {
        return ("");
    }

    /**
     * Parses the given MQ Message Service Address and creates an MQAddress object.
     */
    public static PortMapperMQAddress createAddress(String addr) throws MalformedURLException, UnknownHostException {
        PortMapperMQAddress ret = new PortMapperMQAddress();
        ret.initialize(addr);
        return ret;
    }

}
