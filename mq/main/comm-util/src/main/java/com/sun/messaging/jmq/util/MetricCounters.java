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
 * @(#)MetricCounters.java	1.11 06/27/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.Serializable;

/**
 * Class for performing packet counting
 */

public class MetricCounters implements Cloneable, Serializable
{
    // We use two locks because counters are typically incremented by
    // seperate input and output threads.
    transient private Object inLock;
    transient private Object outLock;

    // Number of JMS messages in and out
    public long    messagesIn = 0;
    public long    messagesOut = 0;

    // Number of JMS message bytes in and out
    public long    messageBytesIn = 0;
    public long    messageBytesOut = 0;

    // Number of packets (control and JMS) in and out
    public long    packetsIn = 0;
    public long    packetsOut = 0;

    // Number of packet bytes (control and JMS) in and out
    public long    packetBytesIn = 0;
    public long    packetBytesOut = 0;

    // JVM memory usage
    public long     totalMemory = 0;
    public long     freeMemory = 0;

    // Thread metrics
    public int      threadsActive = 0;
    public int      threadsHighWater = 0;
    public int      threadsLowWater = 0;

    // Timestamp of when counters were last updated. May be 0 if whomever
    // is doing the counting does not want to incur the cost of generating
    // the timestamp
    public long    timeStamp = 0;

    // Number of connections this data represents
    public int     nConnections = 1;

    public MetricCounters() {
        inLock = new Object();
        outLock = new Object();
        reset();
    }

    /**
     * Reset counters to 0
     */
    public void reset() {

        synchronized (inLock) {
            messagesIn = messageBytesIn = 0;
            packetsIn =  packetBytesIn  = 0;
        }

        synchronized (outLock) {
            messagesOut =  messageBytesOut = 0;
            packetsOut =  packetBytesOut = 0;
        }
    }

    /**
     * Updated input counters
     */
    public synchronized void updateIn(long messagesIn, long messageBytesIn,
                               long packetsIn,  long packetBytesIn) {

        synchronized (inLock) {
            this.messagesIn += messagesIn;
            this.messageBytesIn += messageBytesIn;
            this.packetsIn += packetsIn;
            this.packetBytesIn += packetBytesIn;
        }
    }

    /**
     * Update output counters
     */
    public synchronized void updateOut(long messagesOut, long messageBytesOut,
                                long packetsOut,  long packetBytesOut) {

        synchronized (outLock) {
            this.messagesOut += messagesOut;
            this.messageBytesOut += messageBytesOut;
            this.packetsOut += packetsOut;
            this.packetBytesOut += packetBytesOut;
        }
    }

    /**
     * Update counters using values from another MetricCounters
     */
    public synchronized void update(MetricCounters counter) {

        synchronized (inLock) {
            this.messagesIn += counter.messagesIn;
            this.messageBytesIn += counter.messageBytesIn;
            this.packetsIn += counter.packetsIn;
            this.packetBytesIn += counter.packetBytesIn;
        }

        synchronized (outLock) {
            this.messagesOut += counter.messagesOut;
            this.messageBytesOut += counter.messageBytesOut;
            this.packetsOut += counter.packetsOut;
            this.packetBytesOut += counter.packetBytesOut;

            this.threadsActive    = counter.threadsActive;
            this.threadsHighWater = counter.threadsHighWater;
            this.threadsLowWater  = counter.threadsLowWater;
        }
    }

    public String toString() {
        synchronized (outLock) {
            synchronized (inLock) {
                return 
        " In: " + messagesIn + " messages(" + messageBytesIn + " bytes)\t" +
                  packetsIn  + " packets(" + packetBytesIn  + " bytes)\n" +
        "Out: " + messagesOut + " messages(" + messageBytesOut + " bytes)\t" +
                  packetsOut  + " packets(" + packetBytesOut  + " bytes)\n";
            }
        }
    }

    public Object clone() {

	// Bug id 6359793
	// 9 Oct 2006
	// Tom Ross

	// old line
	//MetricCounters counter = new MetricCounters();
	//
	// new lines

	MetricCounters counter = null;

	try {
		counter = (MetricCounters) super.clone();
	} catch ( CloneNotSupportedException e ){
		System.out.println("Class MetricCounters could not be cloned.");
		return null;
	}
	// do deep clone
	counter.inLock = new Object();
	counter.outLock = new Object();

	// do just shallow clone
        synchronized (inLock) {
            counter.messagesIn = this.messagesIn;
            counter.messageBytesIn = this.messageBytesIn;
            counter.packetsIn = this.packetsIn;
            counter.packetBytesIn = this.packetBytesIn;
        }

        synchronized (outLock) {
            counter.messagesOut = this.messagesOut;
            counter.messageBytesOut = this.messageBytesOut;
            counter.packetsOut = this.packetsOut;
            counter.packetBytesOut = this.packetBytesOut;
        }

	return counter;
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException
    {
        s.defaultReadObject();

        // Instantiate transient locks
        inLock = new Object();
        outLock = new Object();
    }
}
