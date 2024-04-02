/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.multibroker.standalone;

import java.io.*;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;

/**
 * This class implements the <code>BrokerAddress</code> for a standalone broker.
 */
class BrokerAddressImpl extends BrokerAddress {
    private static final long serialVersionUID = 6738727667850878073L;

    @Override
    public Object clone() {
        try {
            return super.getObjectClone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return obj.equals(this);
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean getHAEnabled() {
        return false;
    }

    @Override
    public String getBrokerID() {
        return null;
    }

    @Override
    public UID getBrokerSessionUID() {
        return null;
    }

    @Override
    public UID getStoreSessionUID() {
        return null;
    }

    @Override
    public void setStoreSessionUID(UID uid) {
    }

    @Override
    public String getInstanceName() {
        return null;
    }

    @Override
    public String toProtocolString() {
        return null;
    }

    @Override
    public BrokerAddress fromProtocolString(String s) throws Exception {
        throw new UnsupportedOperationException(this.getClass().getName() + ".fromProtocolString");
    }

    @Override
    public void writeBrokerAddress(DataOutputStream dos) {
    }

    @Override
    public void writeBrokerAddress(OutputStream os) {
    }

    @Override
    public void readBrokerAddress(DataInputStream dis) {
    }

    @Override
    public void readBrokerAddress(InputStream is) {
    }
}

