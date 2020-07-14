/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)SimpleNFLHashMap.java	1.16 06/29/07
 */

package com.sun.messaging.jmq.util.lists;

import java.util.*;

public class SimpleNFLHashMap extends HashMap implements EventBroadcaster, Limitable {
    /**
     * 
     */
    private static final long serialVersionUID = 4929344460826666399L;

    EventBroadcastHelper ebh = new EventBroadcastHelper();

    private boolean enforceLimits = true;
    private int highWaterCnt = 0;
    private long highWaterBytes = 0;
    private long largestMessageHighWater = 0;

    private float averageCount = 0.0F;
    private double averageBytes = 0.0D;
    private double messageAverage = 0.0D;
    private long numberSamples = 0;

    protected int maxCapacity = UNLIMITED_CAPACITY;
    protected long maxByteCapacity = UNLIMITED_BYTES;
    protected long bytes = 0;
    protected long maxBytePerObject = UNLIMITED_BYTES;

    Object limitLock = new Object();

    private NLMapEntry oldEntry = new NLMapEntry();
    private NLMapEntry newEntry = new NLMapEntry();

    // resets counters
    public void reset() {
        highWaterCnt = 0;
        highWaterBytes = 0;
        largestMessageHighWater = 0;

        averageCount = 0.0F;
        averageBytes = 0.0D;
        messageAverage = 0.0D;
        numberSamples = 0;
    }

    public SimpleNFLHashMap() {
        super();
    }

    Map comparatorSets = null;
    Map filterMaps = null;

    /**
     * removes all entries that match from this collection and returns a set of the values that were removed.
     *
     * @param c set of object to remove
     * @returns set of values removed
     */
    public Set removeAll(Collection c) {
        return removeAll(c, null);
    }

    /**
     * removes all entries that match from this collection and returns a set of the values that were removed.
     *
     * @param c set of object to remove
     * @param r reason we were removed
     * @returns set of values removed
     */
    public Set removeAll(Collection c, Reason r) {
        Iterator itr = c.iterator();
        Set s = new HashSet();
        while (itr.hasNext()) {
            Object mine = itr.next();
            Object o = remove(mine, r);
            if (o != null) {
                s.add(o);
            }
        }
        return s;
    }

    /**
     * Removes all mappings from this map.
     */
    @Override
    public void clear() {
        Set m = null;
        synchronized (this) {
            m = new HashSet(this.keySet());
        }
        removeAll(m, null);
    }

    /**
     * returns the objects in the values of this map ordered by the comparator. Changes in the original map will reflect in
     * the subset).
     * <P>
     * For example, if you remove an object from the original map it will also be removed from the subset.
     *
     * @param c comparator to use when sorting the objects
     * @returns a set ordered by the comparator
     */
    public Set subSet(Comparator f) {
        Set s = new TreeSet(f);
        synchronized (this) {
            s.addAll(values());
            if (comparatorSets == null) {
                comparatorSets = Collections.synchronizedMap(new WeakValueHashMap("Comparator"));
            }
            comparatorSets.put(f, s);
        }
        return s;

    }

    /**
     * Copies all of the mappings from the specified map to this map These mappings will replace any mappings that this map
     * had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @throws NullPointerException if the specified map is null.
     * @see #putAll(Map, Reason)
     */
    @Override
    public void putAll(Map m) {
        putAll(m, null);
    }

    /**
     * Copies all of the mappings from the specified map to this map These mappings will replace any mappings that this map
     * had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @param reason why this map was added.
     * @throws NullPointerException if the specified map is null.
     */
    public void putAll(Map m, Reason r) {
        Iterator itr = m.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry me = (Map.Entry) itr.next();
            put(me.getKey(), me.getValue(), r);
        }
    }

    /**
     * Associates the specified value with the specified key in this map. If the map previously contained a mapping for this
     * key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the HashMap previously associated <tt>null</tt> with the specified key.
     * @see #put(Object, Object, Reason)
     */
    @Override
    public Object put(Object key, Object value) {
        return this.put(key, value, null);
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map.
     * @param reason why this event occurred (used during notification).
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the map previously associated <tt>null</tt> with the specified key.
     * @see #remove(Object, Reason)
     */
    @Override
    public Object remove(Object key) {
        return this.remove(key, null);
    }

    /**
     * Associates the specified value with the specified key in this map. If the map previously contained a mapping for this
     * key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @param reason why this value was added.
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the HashMap previously associated <tt>null</tt> with the specified key.
     */
    public Object put(Object key, Object value, Reason reason) {
        return this.put(key, value, reason, true);
    }

    /**
     * Associates the specified value with the specified key in this map. If the map previously contained a mapping for this
     * key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @param reason why this value was added.
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the HashMap previously associated <tt>null</tt> with the specified key.
     */
    public Object put(Object key, Object value, Reason reason, boolean overwrite) {
        return put(key, value, reason, overwrite, true);
    }

    public Object put(Object key, Object value, Reason reason, boolean overwrite, boolean checklimit) {

        boolean myenforceLimits = enforceLimits && checklimit;

        if (hasListeners(EventType.SET_CHANGED_REQUEST)) {
            notifyChange(EventType.SET_CHANGED_REQUEST, null, value, reason);
        }

        if (key == null && value == null) {
            throw new NullPointerException("Unable to support null " + "keys or values");
        }

        if (maxByteCapacity != UNLIMITED_BYTES && !(value instanceof Sized)) {
            throw new ClassCastException("Unable to add object not of" + " type Sized when byteCapacity has been set");
        }
        if (maxBytePerObject != UNLIMITED_BYTES && !(value instanceof Sized)) {
            throw new ClassCastException("Unable to add object not of" + " type Sized when maxByteSize has been set");
        }
        long objsize = 0;
        if (value instanceof Sized) {
            objsize = ((Sized) value).byteSize();
        }

        long oldBytes = 0;
        long newBytes = 0;
        int oldSize = 0;
        int newSize = 0;
        boolean wasEmpty = false;
        boolean isEmpty = false;
        boolean wasFull = false;
        boolean isFull = false;
        Object ret = null;

        synchronized (this) {
            oldBytes = bytes;
            oldSize = size();
            newSize = oldSize + 1;
            wasEmpty = oldSize == 0;
            isEmpty = newSize == 0;

            newBytes = bytes + objsize;

            wasFull = isFull();
            isFull = (maxCapacity > 0 && newSize >= maxCapacity) || (maxByteCapacity > 0 && newBytes >= maxByteCapacity);
            if (myenforceLimits && maxBytePerObject != UNLIMITED_BYTES && objsize > maxBytePerObject) {
                throw new OutOfLimitsException(OutOfLimitsException.ITEM_SIZE_EXCEEDED, Long.valueOf(objsize), Long.valueOf(maxBytePerObject));
            }
            if (myenforceLimits && maxCapacity != UNLIMITED_CAPACITY && ((maxCapacity - newSize) < 0)) {
                throw new OutOfLimitsException(OutOfLimitsException.CAPACITY_EXCEEDED, Integer.valueOf(newSize), Integer.valueOf(maxCapacity));
            }

            if (myenforceLimits && maxByteCapacity != UNLIMITED_BYTES && ((maxByteCapacity - newBytes) < 0)) {
                throw new OutOfLimitsException(OutOfLimitsException.BYTE_CAPACITY_EXCEEDED, Long.valueOf(newBytes), Long.valueOf(maxByteCapacity));
            }
            // we're good to go
            bytes = newBytes;

            if (!overwrite && super.get(key) != null) {
                throw new IllegalStateException("Message exist in the store");
            }
            ret = super.put(key, value);

            if (ret != null && ret instanceof Sized) { // replaced
                bytes -= ((Sized) ret).byteSize();
            }
        }

        synchronized (limitLock) {
            if (newSize > highWaterCnt) {
                highWaterCnt = newSize;
            }
            if (objsize > largestMessageHighWater) {
                largestMessageHighWater = objsize;
            }
            if (bytes > highWaterBytes) {
                highWaterBytes = bytes;
            }
            averageCount = ((numberSamples * averageCount + newSize) / (numberSamples + 1.0F));
            averageBytes = (numberSamples * averageBytes + newBytes) / (numberSamples + 1.0D);
            messageAverage = (numberSamples * messageAverage + objsize) / ((double) numberSamples + 1.0F);
            numberSamples++;
        }
        // OK -> deal w/ comparator sets
        if (comparatorSets != null && !comparatorSets.isEmpty()) {
            synchronized (comparatorSets) {
                Iterator itr = comparatorSets.values().iterator();
                while (itr.hasNext()) {
                    Set s = (Set) itr.next();
                    if (s != null) {
                        synchronized (s) {
                            s.add(value);
                        }
                    } else {
                        itr.remove();
                    }
                }
            }

        }
        // OK -> deal w/ filter sets
        if (filterMaps != null && !filterMaps.isEmpty()) {
            synchronized (filterMaps) {
                Iterator itr = filterMaps.values().iterator();
                while (itr.hasNext()) {
                    FilterMap s = (FilterMap) itr.next();
                    if (s != null && (s.getFilter() == null || s.getFilter().matches(value))) {
                        s.put(key, value);
                    } else if (s == null) {
                        itr.remove();
                    }
                }
            }

        }

        // notify listeners
        if (hasListeners(EventType.SIZE_CHANGED) && oldSize != newSize) {
            notifyChange(EventType.SIZE_CHANGED, Integer.valueOf(oldSize), Integer.valueOf(newSize), reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) && oldBytes != newBytes) {
            notifyChange(EventType.BYTES_CHANGED, Long.valueOf(oldBytes), Long.valueOf(newBytes), reason);
        }

        if (hasListeners(EventType.SET_CHANGED)) {
            Object oldv = null;
            newEntry.update(key, value);

            if (ret != null) {
                oldEntry.update(key, ret);
                oldv = oldEntry;
            }

            notifyChange(EventType.SET_CHANGED, oldv, newEntry, reason);
        }

        if (hasListeners(EventType.EMPTY) && wasEmpty != isEmpty) {
            notifyChange(EventType.EMPTY, Boolean.valueOf(wasEmpty), Boolean.valueOf(isEmpty), reason);
        }

        if (hasListeners(EventType.FULL) && wasFull != isFull) {
            notifyChange(EventType.FULL, Boolean.valueOf(wasFull), Boolean.valueOf(isFull), reason);
        }
        return ret;
    }

    /**
     * determines if the map should throw an exeption if the size is exceeded, or just call the FULL event listeners
     *
     * @param b if true, exceptions are thrown when limits are exceeded.
     */
    public void enforceLimits(boolean b) {
        enforceLimits = b;
    }

    /**
     * returns the current value of enforceLimits
     *
     * @returns true if limits are enforces, false otherwise
     */
    public boolean getEnforceLimits() {
        return enforceLimits;
    }

    /**
     * Returns the value to which the specified key is mapped in this identity hash map, or <tt>null</tt> if the map
     * contains no mapping for this key. A return value of <tt>null</tt> does not <i>necessarily</i> indicate that the map
     * contains no mapping for the key; it is also possible that the map explicitly maps the key to <tt>null</tt>. The
     * <tt>containsKey</tt> method may be used to distinguish these two cases.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value to which this map maps the specified key, or <tt>null</tt> if the map contains no mapping for this
     * key.
     * @see #put(Object, Object)
     */
    @Override
    public Object get(Object key) {
        synchronized (this) {
            return super.get(key);
        }
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map.
     * @param reason why this event occurred (used during notification).
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the map previously associated <tt>null</tt> with the specified key.
     */
    public Object remove(Object key, Reason reason) {
        return removeWithValue(key, null, null, reason);
    }

    /**
     * @return errValue if expectedValue not null and if to be removed value not null and != expectedValue
     */
    public Object removeWithValue(Object key, Object expectedValue, Object errValue, Reason reason) {

        Object value = null;

        long oldBytes = 0;
        long newBytes = 0;
        int oldSize = 0;
        int newSize = 0;
        boolean wasEmpty = false;
        boolean isEmpty = false;
        boolean wasFull = false;
        boolean isFull = false;
        long objsize = 0;

        synchronized (this) {
            if (expectedValue != null) {
                value = super.get(key);
                if (value != null && value != expectedValue) {
                    return errValue;
                }
            }
            value = super.remove(key);

            if (value == null) {
                return null;
            }

            if (value instanceof Sized) {
                objsize = ((Sized) value).byteSize();
            }
            oldBytes = bytes;
            oldSize = size() + 1;
            newSize = oldSize - 1;
            wasEmpty = oldSize == 0;
            isEmpty = newSize == 0;
            newBytes = bytes - objsize;
            wasFull = (maxCapacity > 0 && oldSize >= maxCapacity) || (maxByteCapacity > 0 && oldBytes >= maxByteCapacity);
            isFull = (maxCapacity > 0 && newSize >= maxCapacity) || (maxByteCapacity > 0 && newBytes >= maxByteCapacity);
            // we're good to go
            bytes = newBytes;
        }

        if (hasListeners(EventType.SET_CHANGED_REQUEST)) {
            notifyChange(EventType.SET_CHANGED_REQUEST, value, null, reason);
        }

        synchronized (limitLock) {

            averageCount = ((numberSamples * averageCount + newSize) / (numberSamples + 1.0F));
            averageBytes = (numberSamples * averageBytes + newBytes) / (numberSamples + 1.0D);
            messageAverage = (numberSamples * messageAverage + objsize) / ((double) numberSamples + 1.0F);
            numberSamples++;
        }

        // OK -> deal w/ comparator sets
        if (comparatorSets != null && !comparatorSets.isEmpty()) {
            synchronized (comparatorSets) {
                Iterator itr = comparatorSets.values().iterator();
                while (itr.hasNext()) {
                    Set s = (Set) itr.next();
                    if (s != null) {
                        synchronized (s) {
                            s.remove(value);
                        }
                    } else {
                        itr.remove();
                    }
                }
            }
        }
        // OK -> deal w/ filter sets
        if (filterMaps != null && !filterMaps.isEmpty()) {
            synchronized (filterMaps) {
                Iterator itr = filterMaps.values().iterator();
                while (itr.hasNext()) {
                    Set s = (Set) itr.next();
                    if (s != null) {
                        s.remove(key);
                    } else {
                        itr.remove();
                    }
                }
            }
        }

        // notify listeners
        if (hasListeners(EventType.SIZE_CHANGED) && oldSize != newSize) {
            notifyChange(EventType.SIZE_CHANGED, Integer.valueOf(oldSize), Integer.valueOf(newSize), reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) && oldBytes != newBytes) {
            notifyChange(EventType.BYTES_CHANGED, Long.valueOf(oldBytes), Long.valueOf(newBytes), reason);
        }

        if (hasListeners(EventType.SET_CHANGED)) {
            Object oldv = oldEntry;
            oldEntry.update(key, value);
            Map.Entry newv = null;

            notifyChange(EventType.SET_CHANGED, oldv, newv, reason);
        }

        if (hasListeners(EventType.EMPTY) && wasEmpty != isEmpty) {
            notifyChange(EventType.EMPTY, Boolean.valueOf(wasEmpty), Boolean.valueOf(isEmpty), reason);
        }

        if (hasListeners(EventType.FULL) && wasFull != isFull) {
            notifyChange(EventType.FULL, Boolean.valueOf(wasFull), Boolean.valueOf(isFull), reason);
        }
        return value;

    }

    /**
     * returns a new map that contains all objects matching the filter. This new map will not be updated to reflect changes
     * in the original set.
     *
     * @param f filter to use when matching
     * @returns a new map of matching objects
     */
    public Map getAll(Filter f) {
        Map m = new HashMap();
        synchronized (this) {
            Set entrySet = this.entrySet();
            Iterator itr = entrySet.iterator();
            while (itr.hasNext()) {
                Map.Entry e = (Map.Entry) itr.next();
                Object o = e.getValue();
                if (f == null || f.matches(o)) {
                    m.put(e.getKey(), o);
                }
            }
            return m;
        }
    }

    public List getAllKeys() {
        synchronized (this) {
            return new ArrayList(this.keySet());
        }
    }

    /**
     * @param count return first count of keys
     */
    public List getFirstKeys(int count) {
        int cnt = 0;
        List l = new ArrayList(count);
        synchronized (this) {
            Set entrySet = this.entrySet();
            Iterator itr = entrySet.iterator();
            while (cnt < count && itr.hasNext()) {
                Map.Entry e = (Map.Entry) itr.next();
                l.add(e.getKey());
                cnt++;
            }
        }
        return l;
    }

    // ----------------------------------------------------
    // Notification Events
    // ----------------------------------------------------

    /**
     * Request notification when the specific event occurs.
     *
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data sent with any notifications
     * @return an id associated with this notification
     */
    @Override
    public Object addEventListener(EventListener listener, EventType type, Object user_data) {
        return ebh.addEventListener(listener, type, user_data);
    }

    /**
     * Request notification when the specific event occurs AND the reason matched the passed in reason.
     *
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data sent with any notifications
     * @param reason reason which must be associated with the event (or null for all events)
     * @return an id associated with this notification
     */
    @Override
    public Object addEventListener(EventListener listener, EventType type, Reason reason, Object userData) {
        return ebh.addEventListener(listener, type, reason, userData);
    }

    /**
     * remove the listener registered with the passed in id.
     *
     * @return the listener which was removed
     */
    @Override
    public Object removeEventListener(Object id) {
        return ebh.removeEventListener(id);
    }

    protected boolean hasListeners(EventType e) {
        return ebh.hasListeners(e);
    }

    protected void notifyChange(EventType e, Object oldval, Object newval, Reason r) {
        ebh.notifyChange(e, r, this, oldval, newval);
    }

    /**
     * sets the maximum size of an entry allowed to be added to the collection
     *
     * @param bytes maximum number of bytes for an object added to the list or UNLIMITED_BYTES if there is no limit
     */

    @Override
    public void setMaxByteSize(long bytes) {
        if (bytes < UNLIMITED_BYTES) {
            bytes = UNLIMITED_BYTES;
        }
        maxBytePerObject = bytes;
    }

    /**
     * returns the maximum size of an entry allowed to be added to the collection
     *
     * @return maximum number of bytes for an object added to the list or UNLIMITED_BYTES if there is no limit
     */
    @Override
    public long maxByteSize() {
        return maxBytePerObject;
    }

    /**
     * Sets the capacity (size limit).
     *
     * @param cnt the capacity for this set (or UNLIMITED_CAPACITY if unlimited).
     */
    @Override
    public void setCapacity(int cnt) {
        if (cnt < UNLIMITED_CAPACITY) {
            cnt = UNLIMITED_CAPACITY;
        }
        if (!hasListeners(EventType.FULL)) {
            maxCapacity = cnt;
            return;
        }

        boolean wasFull = false;
        boolean isFull = false;

        synchronized (this) {
            wasFull = isFull();
            maxCapacity = cnt;
            isFull = isFull();
        }

        if (wasFull != isFull) {
            notifyChange(EventType.FULL, Boolean.valueOf(wasFull), Boolean.valueOf(isFull), null);
        }

    }

    /**
     * Sets the byte capacity. Once the byte capacity is set, only objects which implement Sized can be added to the class
     *
     * @param size the byte capacity for this set (or UNLIMITED_BYTES if unlimited).
     */
    @Override
    public void setByteCapacity(long size) {
        if (size < UNLIMITED_BYTES) {
            size = UNLIMITED_BYTES;
        }
        if (!hasListeners(EventType.FULL)) {
            maxByteCapacity = size;
            return;
        }

        boolean wasFull = false;
        boolean isFull = false;
        synchronized (this) {
            wasFull = isFull();
            maxByteCapacity = size;
            isFull = isFull();
        }

        if (wasFull != isFull) {
            notifyChange(EventType.FULL, Boolean.valueOf(wasFull), Boolean.valueOf(isFull), null);
        }
    }

    /**
     * Returns the capacity (count limit) or UNLIMITED_CAPACITY if its not set.
     *
     * @return the capacity of the list
     */
    @Override
    public int capacity() {
        return maxCapacity;
    }

    /**
     * Returns the byte capacity (or UNLIMITED_BYTES if its not set).
     *
     * @return the byte capacity for this set.
     */
    @Override
    public long byteCapacity() {
        return maxByteCapacity;
    }

    /**
     * Returns <tt>true</tt> if either the bytes limit or the count limit is set and has been reached or exceeded.
     *
     * @return <tt>true</tt> if the count limit is set and has been reached or exceeded.
     */
    @Override
    public boolean isFull() {
        return (maxCapacity > 0 && size() >= maxCapacity) || (maxByteCapacity > 0 && bytes >= maxByteCapacity);
    }

    /**
     * Returns number of entries remaining in the lists to reach full capacity or UNLIMITED_CAPACITY if the capacity has not
     * been set
     *
     * @return the amount of free space
     */
    @Override
    public int freeSpace() {
        if (maxCapacity == UNLIMITED_CAPACITY) {
            return UNLIMITED_CAPACITY;
        }

        int val = maxCapacity - size();
        if (val < 0) {
            return 0;
        }
        return val;

    }

    /**
     * Returns the number of bytesremaining in the lists to reach full capacity, 0 if the list is greater than the capacity
     * or UNLIMITED_BYTES if the capacity has not been set
     *
     * @return the amount of free space
     */
    @Override
    public long freeBytes() {
        if (maxByteCapacity == UNLIMITED_CAPACITY) {
            return UNLIMITED_CAPACITY;
        }
        synchronized (this) {
            long val = maxByteCapacity - bytes;
            if (val < 0) {
                return 0L;
            }
            return val;
        }
    }

    /**
     * Returns the number of bytes used by all entries in this set which implement Sized. If this set contains more than
     * <tt>Long.MAX_VALUE</tt> elements, returns <tt>Long.MAX_VALUE</tt>.
     *
     * @return the total bytes of data from all objects implementing Sized in this set.
     * @see Sized
     * @see #size
     */
    @Override
    public long byteSize() {
        return bytes;
    }

    /**
     * Returns the number of entries in this collection. If this set contains more than <tt>Long.MAX_VALUE</tt> elements,
     * returns <tt>Long.MAX_VALUE</tt>.
     *
     * @return the total bytes of data from all objects implementing Sized in this set.
     * @see Sized
     * @see #size
     */
    @Override
    public int size() {
        synchronized (this) {
            return super.size();
        }
    }

    /**
     * Maximum number of messages stored in this list at any time since its creation.
     *
     * @return the highest number of messages this set has held since it was created.
     */

    @Override
    public int highWaterCount() {
        return highWaterCnt;
    }

    /**
     * Maximum number of bytes stored in this list at any time since its creation.
     *
     * @return the largest size (in bytes) of the objects in this list since it was created.
     */
    @Override
    public long highWaterBytes() {
        return highWaterBytes;
    }

    /**
     * The largest message (which implements Sized) which has ever been stored in this list.
     *
     * @return the number of bytes of the largest message ever stored on this list.
     */
    @Override
    public long highWaterLargestMessageBytes() {
        return largestMessageHighWater;
    }

    /**
     * Average number of messages stored in this list at any time since its creation.
     *
     * @return the average number of messages this set has held since it was created.
     */
    @Override
    public float averageCount() {
        return averageCount;
    }

    /**
     * Average number of bytes stored in this list at any time since its creation.
     *
     * @return the largest size (in bytes) of the objects in this list since it was created.
     */
    @Override
    public double averageBytes() {
        return averageBytes;
    }

    /**
     * The average message size (which implements Sized) of messages which has been stored in this list.
     *
     * @return the number of bytes of the average message stored on this list.
     */
    @Override
    public double averageMessageBytes() {
        return messageAverage;
    }

    static class NLMapEntry implements Map.Entry {
        Object key = null;
        Object value = null;

        public NLMapEntry() {
            this.key = null;
            this.value = null;
        }

        public void update(Object k, Object v) {
            this.key = k;
            this.value = v;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry me = (Map.Entry) o;
                return (me.getKey() == key || (key != null && key.equals(me.getKey())))
                        && (me.getValue() == value || (value != null && value.equals(me.getValue())));
            }
            return false;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public Object setValue(Object o) {
            throw new UnsupportedOperationException("Can not set values on the entry");
        }

    }

}

class FilterMap extends HashMap {
    /**
     * 
     */
    private static final long serialVersionUID = -5287488524104212543L;
    Filter f = null;

    public FilterMap(Filter f) {
        this.f = f;
    }

    public Filter getFilter() {
        return f;
    }
}
