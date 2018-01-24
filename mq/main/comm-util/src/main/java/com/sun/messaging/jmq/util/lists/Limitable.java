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
 * @(#)Limitable.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

/**
 * Interface for lists which can have limited
 * capacities
 * @see NFLHashMap
 * @see AbstractNFLSet
 * @see Sized
 */
public interface Limitable
{
    public static final int UNLIMITED_CAPACITY = -1;
    public static final long UNLIMITED_BYTES = -1;

    /** 
     * sets the maximum size of an entry allowed
     * to be added to the collection
     * @param bytes maximum number of bytes for
     *        an object added to the list or
     *        UNLIMITED_BYTES if there is no limit
     */   
    public void setMaxByteSize(long bytes);
 
    /** 
     * returns the maximum size of an entry allowed
     * to be added to the collection
     * @return maximum number of bytes for an object
     *        added to the list  or
     *        UNLIMITED_BYTES if there is no limit
     */   
    public long maxByteSize();
 
    /**
     * Sets the capacity (size limit).
     *
     * @param cnt the capacity for this set (or
     *         UNLIMITED_CAPACITY if unlimited).
     */
    public void setCapacity(int cnt);

    /**
     * Sets the byte capacity. Once the byte capacity
     * is set, only objects which implement Sized
     * can be added to the class
     *
     * @param size the byte capacity for this set (or
     *         UNLIMITED_BYTES if unlimited).
     */
    public void setByteCapacity(long size);


    /**
     * Returns the capacity (count limit) or UNLIMITED_CAPACITY
     * if its not set.
     *
     * @return the capacity of the list
     */
    public int capacity();


    /**
     * Returns the byte capacity (or UNLIMITED_BYTES if its not set).
     *
     * @return the byte capacity for this set.
     */
    public long byteCapacity();



    /**
     * Returns <tt>true</tt> if either the bytes limit
     *         or the count limit is set and
     *         has been reached or exceeded.
     *
     * @return <tt>true</tt> if the count limit is set and
     *         has been reached or exceeded.
     */
    public boolean isFull();


    /**
     * Returns number of entries remaining in the
     *         lists to reach full capacity or
     *         UNLIMITED_CAPACITY if the capacity
     *         has not been set
     *
     * @return the amount of free space
     */
    public int freeSpace();

    /**
     * Returns the number of bytesremaining in the
     *         lists to reach full capacity, 0
     *         if the list is greater than the 
     *         capacity  or UNLIMITED_BYTES if 
     *         the capacity has not been set
     *
     * @return the amount of free space
     */
    public long freeBytes();


    /**
     * Returns the number of bytes used by all entries in this set which implement
     * Sized.  If this
     * set contains more than <tt>Long.MAX_VALUE</tt> elements, returns
     * <tt>Long.MAX_VALUE</tt>.
     *
     * @return the total bytes of data from all objects implementing
     *         Sized in this set.
     * @see Sized
     * @see #size
     */
    public long byteSize();
    
    /**
     * Returns the number of entries in this collection.  If this
     * set contains more than <tt>Long.MAX_VALUE</tt> elements, returns
     * <tt>Long.MAX_VALUE</tt>.
     *
     * @return the total bytes of data from all objects implementing
     *         Sized in this set.
     * @see Sized
     * @see #size
     */
    public int size();

    /**
     * Maximum number of messages stored in this
     * list at any time since its creation.
     *
     * @return the highest number of messages this set
     * has held since it was created.
     */
    public int highWaterCount();

    /**
     * Maximum number of bytes stored in this
     * list at any time since its creation.
     *
     * @return the largest size (in bytes) of
     *  the objects in this list since it was
     *  created.
     */
    public long highWaterBytes();

    /**
     * The largest message (which implements Sized)
     * which has ever been stored in this list.
     *
     * @return the number of bytes of the largest
     *  message ever stored on this list.
     */
    public long highWaterLargestMessageBytes();

    /**
     * Average number of messages stored in this
     * list at any time since its creation.
     *
     * @return the average number of messages this set
     * has held since it was created.
     */
    public float averageCount();

    /**
     * Average number of bytes stored in this
     * list at any time since its creation.
     *
     * @return the largest size (in bytes) of
     *  the objects in this list since it was
     *  created.
     */
    public double averageBytes();

    /**
     * The average message size (which implements Sized)
     * of messages which has been stored in this list.
     *
     * @return the number of bytes of the average
     *  message stored on this list.
     */
    public double averageMessageBytes();

}
