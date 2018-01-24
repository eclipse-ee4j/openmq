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
 * @(#)BrokerAddress.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import java.io.*;
import java.net.*;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.UID;

/**
 * This class encapsulates the broker address / identifier. The
 * implementation is specific to the broker topology.
 */
public abstract class BrokerAddress 
       implements Cloneable, Serializable {

    static final long serialVersionUID = -8900410708742494160L;

    BrokerMQAddress address = null;


    public BrokerAddress() {
    }

    public BrokerMQAddress getMQAddress() {
        return address;
    }

    public void initialize(String host, int port)
        throws MalformedURLException, UnknownHostException
    {
        address = BrokerMQAddress.createAddress(host, port);
    }

    public void initialize(BrokerMQAddress ba)
        throws MalformedURLException
    {
        address = ba; 
    }
        

    public int getClusterVersion() {
        return -1;
    }

    public abstract boolean getHAEnabled();
    public abstract String getBrokerID();
    public abstract UID getBrokerSessionUID();
    public abstract UID getStoreSessionUID();
    public abstract void setStoreSessionUID(UID uid);
    public abstract String getInstanceName();

    /**
     * Must be provided by topology specific implementation.
     */
    public abstract Object clone();

    /**
     * Makes a shallow copy of the BrokerAddress object using
     * Object.clone().
     */
    protected Object getObjectClone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Must be provided by topology specific implementation.
     */
    public abstract boolean equals(Object obj);

    /**
     * Must be provided by topology specific implementation.
     */
    public abstract int hashCode();

    /**
     * Get Object.hashCode().
     */
    protected int getObjectHashCode() {
        return super.hashCode();
    }

    /**
     * Get the string representation with the syntax used
     * in the configuration file.
     */
    public String toConfigString() {
        return toString();
    }

    /**
     * Get the string representation with syntax used in cluster protocol
     */
    public abstract String toProtocolString();

    public abstract BrokerAddress fromProtocolString(String s) throws Exception;


    /**
     * Writes the broker address to a given <code> DataOutputStream </code>.
     */
    public abstract void writeBrokerAddress(DataOutputStream dos)
        throws IOException;

    /**
     * Writes the broker address to a given <code> OutputStream </code>.
     */
    public void writeBrokerAddress(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        writeBrokerAddress(dos);
    }

    /**
     * Reads the broker address from a given <code> DataInputStream </code>
     */
    public abstract void readBrokerAddress(DataInputStream dis)
        throws IOException;

    /**
     * Reads the broker address from a given <code> InputStream </code>
     */
    public void readBrokerAddress(InputStream is)
        throws IOException {
        DataInputStream dis = new DataInputStream(is);
        readBrokerAddress(dis);
    }

}

/*
 * EOF
 */
