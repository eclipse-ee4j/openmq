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
 * @(#)BrokerAddressImpl.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.standalone;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;

/**
 * This class implements the <code>BrokerAddress</code> for
 * a standalone broker.
 */
class BrokerAddressImpl extends BrokerAddress {
    public BrokerAddressImpl() {
    }

    public Object clone() {
        try {
            return super.getObjectClone();
        }
        catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return obj.equals(this);
    }

    public int hashCode() {
        return 0;
    }

    public boolean getHAEnabled() {
        return false;
    }
    public String getBrokerID() {
        return null;
    }
    public UID getBrokerSessionUID() {
        return null;
    }
    public UID getStoreSessionUID() {
        return null;
    }
    public void setStoreSessionUID(UID uid) {
    }
    public String getInstanceName() {
        return null;
    }

    public String toProtocolString() {
        return null;
    }

    public BrokerAddress fromProtocolString(String s) throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName()+".fromProtocolString");
    }

    public void writeBrokerAddress(DataOutputStream dos) {
    }

    public void writeBrokerAddress(OutputStream os) {
    }

    public void readBrokerAddress(DataInputStream dis) {
    }

    public void readBrokerAddress(InputStream is) {
    }
}

/*
 * EOF
 */
