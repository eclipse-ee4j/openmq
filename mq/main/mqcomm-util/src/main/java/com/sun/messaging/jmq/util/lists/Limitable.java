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

package com.sun.messaging.jmq.util.lists;

/**
 * Interface for lists which can have limited capacities
 *
 * @see SimpleNFLHashMap
 * @see Sized
 */
public interface Limitable {
    int UNLIMITED_CAPACITY = -1;
    long UNLIMITED_BYTES = -1;

    /**
     * sets the maximum size of an entry allowed to be added to the collection
     *
     * @param bytes maximum number of bytes for an object added to the list or UNLIMITED_BYTES if there is no limit
     */
    void setMaxByteSize(long bytes);

    /**
     * returns the maximum size of an entry allowed to be added to the collection
     *
     * @return maximum number of bytes for an object added to the list or UNLIMITED_BYTES if there is no limit
     */
    long maxByteSize();

    /**
     * Sets the capacity (size limit).
     *
     * @param cnt the capacity for this set (or UNLIMITED_CAPACITY if unlimited).
     */
    void setCapacity(int cnt);

    /**
     * Sets the byte capacity. Once the byte capacity is set, only objects which implement Sized can be added to the class
     *
     * @param size the byte capacity for this set (or UNLIMITED_BYTES if unlimited).
     */
    void setByteCapacity(long size);

    /**
     * Returns the capacity (count limit) or UNLIMITED_CAPACITY if its not set.
     *
     * @return the capacity of the list
     */
    int capacity();

    /**
     * Returns the byte capacity (or UNLIMITED_BYTES if its not set).
     *
     * @return the byte capacity for this set.
     */
    long byteCapacity();

    /**
     * Returns {@code true} if either the bytes limit or the count limit is set and has been reached or exceeded.
     *
     * @return {@code true} if the count limit is set and has been reached or exceeded.
     */
    boolean isFull();

    /**
     * Returns number of entries remaining in the lists to reach full capacity or UNLIMITED_CAPACITY if the capacity has not
     * been set
     *
     * @return the amount of free space
     */
    int freeSpace();

    /**
     * Returns the number of bytesremaining in the lists to reach full capacity, 0 if the list is greater than the capacity
     * or UNLIMITED_BYTES if the capacity has not been set
     *
     * @return the amount of free space
     */
    long freeBytes();

    /**
     * Returns the number of bytes used by all entries in this set which implement Sized. If this set contains more than
     * {@code Long.MAX_VALUE} elements, returns {@code Long.MAX_VALUE}.
     *
     * @return the total bytes of data from all objects implementing Sized in this set.
     * @see Sized
     * @see #size
     */
    long byteSize();

    /**
     * Returns the number of entries in this collection. If this set contains more than {@code Long.MAX_VALUE} elements,
     * returns {@code Long.MAX_VALUE}.
     *
     * @return the total bytes of data from all objects implementing Sized in this set.
     * @see Sized
     * @see #size
     */
    int size();

    /**
     * Maximum number of messages stored in this list at any time since its creation.
     *
     * @return the highest number of messages this set has held since it was created.
     */
    int highWaterCount();

    /**
     * Maximum number of bytes stored in this list at any time since its creation.
     *
     * @return the largest size (in bytes) of the objects in this list since it was created.
     */
    long highWaterBytes();

    /**
     * The largest message (which implements Sized) which has ever been stored in this list.
     *
     * @return the number of bytes of the largest message ever stored on this list.
     */
    long highWaterLargestMessageBytes();

    /**
     * Average number of messages stored in this list at any time since its creation.
     *
     * @return the average number of messages this set has held since it was created.
     */
    float averageCount();

    /**
     * Average number of bytes stored in this list at any time since its creation.
     *
     * @return the largest size (in bytes) of the objects in this list since it was created.
     */
    double averageBytes();

    /**
     * The average message size (which implements Sized) of messages which has been stored in this list.
     *
     * @return the number of bytes of the average message stored on this list.
     */
    double averageMessageBytes();

}
