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
 * @(#)MetricData.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.Serializable;
import com.sun.messaging.jmq.util.MetricCounters;

/**
 * This class represents metric performance data that is derived
 * from MetricCounters
 */

public class MetricData implements Serializable
{

    public MetricCounters   totals = null;
    public MetricCounters   rates  = null;

    public long     totalMemory;
    public long     freeMemory;

    public long     timestamp;

    public int      nConnections;

    public MetricData() {
        totals = new MetricCounters();
        rates  = new MetricCounters();
        reset();
    }

    /**
     * Reset counters to 0
     */
    public synchronized void reset() {

        totals.reset();
        rates.reset();

        timestamp = 0;
        totalMemory = 0;
        freeMemory = 0;
        nConnections = 0;
    }

    public synchronized void setTotals(MetricCounters counters) {
        totals.reset();
        totals.update(counters);
    }

    public synchronized void setRates(MetricCounters counters) {
        rates.reset();
        rates.update(counters);
    }

    public String toString() {
        String s =

        "Connections: " + nConnections + "    JVM Heap: " +
            totalMemory + " bytes (" + freeMemory + " free)" +
	" Threads: " + totals.threadsActive + " (" + totals.threadsLowWater + "-" + totals.threadsHighWater + ")" + "\n" +
        "      In: " +
        totals.messagesIn + " msgs (" + totals.messageBytesIn +  " bytes)  " +
         totals.packetsIn +  " pkts (" + totals.packetBytesIn  + " bytes)\n" +
        "     Out: " +
        totals.messagesOut + " msgs (" + totals.messageBytesOut +  " bytes)  " +
         totals.packetsOut +  " pkts (" + totals.packetBytesOut  + " bytes)\n" +
        " Rate In: " +
          rates.messagesIn + " msgs/sec (" + rates.messageBytesIn + " bytes/sec)  " +
           rates.packetsIn +  " pkts/sec (" + rates.packetBytesIn + " bytes/sec)\n" +
        "Rate Out: " +
         rates.messagesOut + " msgs/sec (" + rates.messageBytesOut + " bytes/sec)  " +
          rates.packetsOut +  " pkts/sec (" + rates.packetBytesOut + " bytes/sec)";

        return s;
    }
}
