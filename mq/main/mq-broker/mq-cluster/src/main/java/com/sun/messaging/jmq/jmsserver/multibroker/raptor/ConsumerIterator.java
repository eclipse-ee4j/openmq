/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;

class ConsumerIterator implements Iterator {
    private int count = 0;
    private int count_read = 0;
    private DataInputStream dis = null;
    private BrokerAddress from = null;
    private GPacket gp = null;

    ConsumerIterator(GPacket gp, byte[] payload, int count, BrokerAddress from) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        dis = new DataInputStream(bis);
        this.count = count;
        this.count_read = 0;
        this.from = from;
        this.gp = gp;
    }

    @Override
    public boolean hasNext() {
        if (count_read < 0) {
            throw new IllegalStateException("ConsumerUID");
        }
        return count_read < count;
    }

    /**
     * Caller must catch RuntimeException and getCause
     *
     * @throws RuntimeException
     */
    @Override
    public Object next() {
        try {

            Consumer c = ClusterConsumerInfo.readConsumer(dis);
            Integer prefetch = (Integer) gp.getProp(c.getConsumerUID().longValue() + ":" + Consumer.PREFETCH);
            if (prefetch != null) {
                c.setRemotePrefetch(prefetch.intValue());
            }
            if (from != null) {
                c.getConsumerUID().setBrokerAddress(from);
            }
            count_read++;
            return c;

        } catch (IOException e) {

            Throwable ex = e.getCause();
            if (ex instanceof ConsumerAlreadyAddedException) {
                count_read++;
                throw new RuntimeException(ex);
            }
            count_read = -1;
            throw new RuntimeException(e);

        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not supported");
    }
}
