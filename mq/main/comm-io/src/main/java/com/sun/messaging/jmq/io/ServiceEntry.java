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
 * @(#)ServiceEntry.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.StringTokenizer;

/**
 * Encapsulates information about a service. For use with the cluster
 * discovery protocol.
 */
public class ServiceEntry {
    private String address = null;
    private String protocol = null;;
    private String type = null;;
    private String name = null;

    public final static String SPACE = " ";

    public ServiceEntry() {
    }

    /**
     * Set the transport address for this service.
     *
     * Service address syntax examples :
     * <pre>
     *     jms@host:port
     *     ssljms@host:port
     *     httpjms@http://www.foo.com/jmqservlet?ServerName=jpgserv
     * </pre>
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Get the service address.
     */
    public String getAddress() {
        return this.address;
    }

    /**
     * Set the protocol.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Get the protocol.
     */
    public String getProtocol() {
        return this.protocol;
    }

    /**
     * Set the service type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the service type.
     */
    public String getType() {
        return this.type;
    }

    /**
     * Set the service name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the service name.
     */
    public String getName() {
        return this.name;
    }

    public String toString() {
        return name + SPACE + protocol + SPACE +
            type + SPACE + address;
    }
}

/*
 * EOF
 */
