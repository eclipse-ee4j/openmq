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
 * @(#)ByteBufferPool.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.nio.ByteBuffer;

import com.sun.messaging.jmq.util.DiagManager;
import com.sun.messaging.jmq.util.DiagManager.Data;
import com.sun.messaging.jmq.util.DiagDictionaryEntry;

/**
 *
 * A pool of ByteBuffers
 *
 * Nio direct ByteBuffers are relatively expensive to allocate and
 * deallocate. To make direct ByteBuffers easier to use we introduce the
 * ByteBuffer pool. this pool should really be only used for direct
 * ByteBuffers. Standard ByteBuffers are better managed by the Java heap.
 *
 * The pool has the following properties:
 *
 * poolCapacity: The maximum number of buffer bytes that the pool will hold.
 *     poolSize: The number of buffer bytes currently in the pool.
 *   bigBufSize: The size a buffer needs to be to be considered "big".
 *     bigRatio: The max number of bytes "big" buffers can occupy in the pool.
 *    useDirect: True to allocate direct buffers, false to allocate normal
 *    blockSize: The allocation block size. This size of all buffers will
 *               be a multiple of blockSize.
 *
 * bigBufSize and bigRatio are used to prevent large buffers from
 * blowing out most of the pool.
 *
 * The pool is implemented as a HashMap of ArrayLists keyed by buffer size.
 * Each ArrayList holds buffers of one size.
 *
 */
public class ByteBufferPool implements DiagManager.Data {

    private static boolean DEBUG = false;

    // Capacity of buffer pool in bytes
    public static final int DEFAULT_CAPACITY = 1024 * 1024;

    // Default block size in bytes.
    public static final int DEFAULT_BLOCKSIZE = 128;

    // Any buffer larger than 64k is considered big
    public static final int DEFAULT_BIGBUFSIZE = 64 * 1024;

    // By default big buffers can take up half of the pool
    public static final float DEFAULT_BIGRATIO = 0.5f;

    // Hash table for holding lists of buffers
    protected HashMap table = null;

    // True if pool is managing direct buffers, else false
    protected boolean useDirect = true;

    // Allocation block size in bytes. All allocations will be a multiple
    // of blockSize. This increases the likelyhood of buffer reuse.
    protected int blockSize = DEFAULT_BLOCKSIZE;

    // Maximum capacity of the pool in bytes
    protected int poolCapacity = DEFAULT_CAPACITY;

    // Any buffers this size or larger are considered "big"
    protected int bigBufSize  = DEFAULT_BIGBUFSIZE;

    // Determines the ammount of space in the pool that can be used
    // by "big" buffers. This prevents large buffers from taking
    // up all the space in the pool
    protected float bigRatio = DEFAULT_BIGRATIO;

    // Current number of bytes in the pool that are used by big buffers
    protected int bigPoolSize  = 0;

    // Current number of bytes in the pool that are used by all buffers
    protected int poolSize = 0;

    // Current number of buffers in the pool
    protected int nBufs = 0;

    // Number of direct bytes allocated. Direct buffers are very expensive
    // to allocate and are not free'd efficiently, so once we reach
    // our limit of direct bytes to allocate we revert to using 
    // heap ByteBuffers.
    protected int directBytesAllocated = 0;
    protected int heapBytesAllocated = 0;
    protected int directBufsAllocated = 0;
    protected int heapBufsAllocated = 0;

    // Diagnostic counters
    protected int   hits = 0;   // # of times we found a buffer in the pool
    protected int misses = 0;   // # of times we had to allocate a new one

    protected int adds = 0;     // # of times we added a buffer to the pool
    protected int drops = 0;    // # of times we failed to add a buffer
    //protected int heapDrops = 0;// # of buffers we dropped because they
                                // were heap buffers
    protected String poolContents = null;
    protected double utilization = 0;

    ArrayList diagDictionary = null;

    /**
     * Create an empty packet pool with a default capacity that
     * manages direct ByteBuffers. Currently the default capacity
     * is 1MB.
     */
    public ByteBufferPool() {
        this(DEFAULT_CAPACITY, true);
    }

    /**
     * Create an empty packet pool.
     *
     * @param   capacity    Capacity in bytes of the pool
     * @param   useDirect   true to manage direct ByteBuffers else false
     */
    public ByteBufferPool(int capacity, boolean useDirect) {
        this.poolCapacity = capacity;
        this.useDirect = useDirect;
        table = new HashMap();
        DiagManager.register(this);
    }

    /**
     * Set the big buffer size. Any buffer that is this size
     * or larger is considered big.
     */
    public void setBigBufSize(int n) {
        this.bigBufSize = n;
    }

    /**
     * Get the big buffer size.
     */
    public int getBigBufSize() {
        return this.bigBufSize;
    }

    /**
     * Set the big ratio for the pool. This ratio determines the
     * number of bytes in the pool that can be occupied by big buffers.
     * For example, if the big ratio is 0.5, then half of the pool
     * can be filled with big buffers. 
     */
    public void setBigRatio(float n) {
        this.bigRatio = n;
    }

    /**
     * Get the big ratio.
     */
    public float getBigRatio() {
        return this.bigRatio;
    }

    /**
     * Set the pool's capacity. 
     */
    public void setCapacity(int n) {
        this.poolCapacity = n;
    }

    /**
     * Get the pool's capacity
     */
    public int getCapacity() {
        return poolCapacity;
    }

    /**
     * Set the pool's capacity. 
     */
    public void setBlockSize(int n) {
        this.blockSize = n;
    }

    /**
     * Get the pool's capacity
     */
    public int getBlockSize() {
        return blockSize;
    }


    /**
     * Get utilization as a percentage. Ideal utilization would
     * be 1.0. poor utilization would be 0.1
     */
    public double getUtilization() {
        return (hits/(double)(hits + misses));
    }

    /**
     * Get a buffer from the pool of at least the specified size.
     * If the pool is empty a newly allocated buffer will be returned.
     * ByteBuffer.limit will be set to bufSize.
     * ByteBuffer.capacity may be larger than bufSize.
     */
    public synchronized ByteBuffer get(int bufSize) {

        int allocateSize = 0;
        ArrayList buffers = null;
        ByteBuffer b = null;

        // The size of the buffer we allocate is a multiple of the blocksize
        allocateSize = (bufSize / blockSize) * blockSize;
        if (bufSize % blockSize > 0) {
            allocateSize += blockSize;
        }

        buffers = (ArrayList)table.get(Integer.valueOf(allocateSize));

        if (buffers == null || buffers.isEmpty()) {
            // No buffer of the correct size in the pool. Allocate one
            misses++;
            if (directBytesAllocated >= poolCapacity) {
                // Once we allocate "poolCapacity" worth of direct bytes
                // allocate heap bytes instead.
                b = ByteBuffer.allocate(bufSize);
                heapBytesAllocated += bufSize;
                heapBufsAllocated += 1;
            } else if (useDirect) {
                b = ByteBuffer.allocateDirect(allocateSize);
                directBytesAllocated += allocateSize;
                directBufsAllocated += 1;
            } else {
                b = ByteBuffer.allocate(allocateSize);
            }
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    " get(): miss: allocating new buffer. Requested " +
                     bufSize + " bytes, allocating " + allocateSize);
            }
        } else {
            b = (ByteBuffer)(buffers.remove(buffers.size() - 1));
            poolSize -= b.capacity();
            if (b.capacity() >= bigBufSize) {
                bigPoolSize -= b.capacity();
            }
            nBufs--;
            hits++;
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    " get(): hit: Requested " +
                     bufSize + " bytes, returning " + b.capacity());
            }
        }

        b.limit(bufSize);
        b.rewind();

        return b;
    }

    /**
     * Return a buffer to the pool. If the pool capacity is exceeded the
     * buffer is not placed in the pool (and presumeably left for 
     * garbage collection).
     *
     * Also, if the ByteBuffer.isDirect is inconsitent with the "useDirect"
     * parameter passed in the pool constructor, the buffer is disgarded.
     */
    public synchronized void put(ByteBuffer b) {
        if (b == null) return;

        // If it's a direct pool, only save direct buffers
        if (useDirect && !b.isDirect()) {
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    " put(): drop: buffer is not direct. " +
                    "Dropping " + b.capacity() +
                    " bytes onto floor ");
            }
            drops++;
            return;
        }

        ArrayList buffers = null;
        boolean bigBuf = false;

        // See if it is a big buffer
        if (b.capacity() >= bigBufSize) {
            bigBuf = true;
        }

        // If it's a big buffer and we've exceeded the space allowed
        // for big buffers, drip it on floor.
        if (bigBuf && bigPoolSize > (poolCapacity * bigRatio)) {
            drops++;
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    " put(): drop: big pool capacity of " +
                    (poolCapacity * bigRatio) +
                    "exceeded. dropping " + b.capacity() +
                    " bytes onto floor ");
            }
        } else if (poolSize < poolCapacity) {
            Integer key = Integer.valueOf(b.capacity());
            // Add buffer to the pool
            buffers = (ArrayList)table.get(key);
            if (buffers == null) {
                buffers = new ArrayList(64);
                table.put(key, buffers);
            }
            buffers.add(b);
            poolSize += b.capacity();
            nBufs++;
	    adds++;
            if (bigBuf) {
                bigPoolSize += b.capacity();
            }
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    "put(): add: putting " + b.capacity() +
                    " bytes back into pool");
            }
        } else {
            // Drop it on floor
            drops++;
            if ( DEBUG ) {
                System.out.println(super.toString() +
                    " put(): drop: dropping " + b.capacity() +
                    " bytes onto floor ");
            }
        }
    }

    /**
     * Empty the pool.
     */
    public synchronized void clear() {
        table.clear();
        poolSize = 0;
        bigPoolSize  = 0;
        nBufs = 0;
    }

    public String toString() {
        return super.toString() + ": capacity=" + poolCapacity +
                 ", size=" + poolSize + ", nBufs=" + nBufs +
                 ", bigBufSize=" + bigBufSize +
                 ", bigPoolSize=" + bigPoolSize + ", bigRatio=" + bigRatio +
                 ", utilization=" + getUtilization() +
                 ", directBytes=" + directBytesAllocated +
                 ", directBuffers=" + directBufsAllocated +
                 ", heapBytes=" + heapBytesAllocated +
                 ", heapBuffers=" + heapBufsAllocated;
    }


    public String toDiagString() {
        return toString() + ", hits=" + hits + ", misses=" + misses +
            ", adds=" + adds + ", drops=" + drops;
    }

    public String poolContents() {
        StringBuffer sb = new StringBuffer();

        Set set = table.entrySet();
        Iterator iter = set.iterator();
        int i = 0;
        int totalBytes = 0;
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            int n = ((Integer)entry.getKey()).intValue();
            ArrayList list = (ArrayList)entry.getValue();
            sb.append(n + ":" + list.size() + " ");
            totalBytes += n * list.size();
        }

        sb.append(" Total Bytes: " + totalBytes);
        return sb.toString();

    }

    public void resetDiagCounters() {
        hits = 0;
        misses = 0;
        adds = 0;
        drops = 0;
    }

    // Methods to support diagnostics
    public synchronized List getDictionary() {
        if (diagDictionary == null) {
            diagDictionary = new ArrayList();

            diagDictionary.add(new DiagDictionaryEntry("poolCapacity", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("poolSize", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("blockSize", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("nBufs", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("bigBufSize", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("bigPoolSize", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("directBytesAllocated", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("directBufsAllocated", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("heapBytesAllocated", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("heapBufsAllocated", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("hits", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("misses", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("adds", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("drops", DiagManager.COUNTER));
            diagDictionary.add(new DiagDictionaryEntry("utilization", DiagManager.VARIABLE));
            diagDictionary.add(new DiagDictionaryEntry("poolContents", DiagManager.VARIABLE));
        }

        return diagDictionary;
    }

    public void update() {
        poolContents = "[" + poolContents() + "]";
        utilization = getUtilization();
    }

    public String getPrefix() {
        return "bbpool";
    }

    public String getTitle() {
        return "ByteBufferPool";
    }

}

