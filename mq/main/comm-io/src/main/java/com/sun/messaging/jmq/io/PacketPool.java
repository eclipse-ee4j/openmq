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
 * @(#)PacketPool.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.ArrayList;

/**
 *
 * A pool of Packets.
 *
 * The latest Packet code makes use of nio direct ByteBuffers. Direct
 * buffers are faster than byte[]s, but are more expensive to allocate.
 * To compensate for this we introduce a packet pool so that packets
 * can be reaused.
 *
 */
public class PacketPool {

    // List of packets. This will grow as needed.
    ArrayList pool = null;

    // Initial size of list of packets.
    static final int INITIALSIZE = 128;

    // Max size of buffer pool in # of packets
    int capacity = INITIALSIZE;
    int size = 0;

    // Diagnostic counters
    int   hits = 0;
    int misses = 0;

    int drops = 0;
    int adds = 0;

    boolean resetPacket = false;
    boolean dontTimestampPacket= false;



    /**
     *Create an empty packet pool with a default capacity (128)
     */
    public PacketPool() {
        pool = new ArrayList(INITIALSIZE);
    }

    /**
     * Create an empty packet pool with a capacity
     */
    public PacketPool(int capacity) {
        this.capacity = capacity;
        pool = new ArrayList(INITIALSIZE);
    }

    /**
     * Create an empty packet pool with a capacity
     */
    public PacketPool(int capacity, boolean resetPacket, boolean dontTiemstamp) {
        this.capacity = capacity;
        pool = new ArrayList(INITIALSIZE);
        this.dontTimestampPacket = dontTiemstamp;
        this.resetPacket = resetPacket;
    }

    /**
     * Set the pool's capacity
     */
    public void setCapacity(int n) {
        this.capacity = n;
    }

    /**
     * Get the pool's capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Get a packet from the pool. If the pool is empty a newly allocated
     * packet is returned.
     */
    public synchronized Packet get() {
        if (size > 0) {
            size--;
	    hits++;
            return (Packet)(pool.remove(pool.size() - 1));
        } else {
            misses++;
            Packet p =  new Packet();
            if (dontTimestampPacket) {
                p.generateSequenceNumber(false);
                p.generateTimestamp(false);
            }
            return p;
        }
    }

    /**
     * Return a packet to the pool. If the pool capacity is exceeded the
     * packet is not placed in the pool (and presumeably left for 
     * garbage collection).
     */
    public void put(Packet p) {
        if (p == null) return;

        if (resetPacket)
            p.reset();
   
        synchronized(this) {
        
            if (size < capacity) {
                // Clear packet and add it to the pool
                size++;
                pool.add(p);
	        adds++;
            } else {
                // Drop it on floor
                drops++;
            }
        }
    }

    /**
     * Empty the pool.
     */
    public synchronized void clear() {
        // We reallocate the ArrayList so it shrinks back to initial size
        pool = null;
        size = 0;
        pool = new ArrayList(INITIALSIZE);
    }

    public String toString() {
        return super.toString() + ": capacity=" + capacity +
                 ", size=" + size;
    }

    public String toDiagString() {
        return toString() + ", hits=" + hits + ", misses=" + misses +
            ", adds=" + adds + ", drops=" + drops;
    }
}
