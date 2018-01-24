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
 * @(#)ProtocolConsumerUIDIterator.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import java.io.*;
import java.nio.*;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;

/**
 * A helper class to read list of ConsumerUIDs in cluster protocols
 */

public class ProtocolConsumerUIDIterator implements Iterator
{
    private int count = 0;
    private int count_read = 0;
    private DataInputStream dis = null;
    private BrokerAddress from = null;

    public ProtocolConsumerUIDIterator(byte[] payload, int count, BrokerAddress from) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        dis = new DataInputStream(bis);
        this.count = count;
        this.count_read = 0;
        this.from = from;
    }

    public ProtocolConsumerUIDIterator(DataInputStream dis, int count) {
        this.dis = dis;
        this.count = count;
        this.count_read = 0;
    }

    public boolean hasNext() {
        if (count_read < 0) throw new IllegalStateException("ConsumerUIDIterator");  
        return count_read < count;
    }

    /**
     * Caller must catch RuntimeException and getCause
     */
    public Object next() throws RuntimeException {
        try {
        ConsumerUID cid =  ClusterConsumerInfo.readConsumerUID(dis);
        if (from != null) cid.setBrokerAddress(from);
        count_read++;
        return cid;
        } catch (IOException e) {
        count_read = -1;
        throw new RuntimeException(e);
        }
    }

    public void remove() {
        throw new UnsupportedOperationException("Not supported");
    }
}

