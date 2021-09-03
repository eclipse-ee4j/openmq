/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
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

/*
 * @(#)SimpleNFLHashMap.java	1.16 06/29/07
 */

package com.sun.messaging.jmq.util.lists;

import java.util.*;

public class SimpleNFLHashMap<K, V> extends HashMap<K, V> implements EventBroadcaster, Limitable {
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

    private final NLMapEntry<K, V> oldEntry = new NLMapEntry<>();
    private final NLMapEntry<K, V> newEntry = new NLMapEntry<>();

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

    Map<Comparator, Set> comparatorSets = null;
    Map filterMaps = null;

    /**
     * removes all entries that match from this collection and returns a set of the values that were removed.
     *
     * @param c set of object to remove
     * @returns set of values removed
     */
    public Set<V> removeAll(Collection<K> c) {
        return removeAll(c, null);
    }

    /**
     * removes all entries that match from this collection and returns a set of the values that were removed.
     *
     * @param c set of object to remove
     * @param r reason we were removed
     * @return set of values removed
     */
    public Set<V> removeAll(Collection<K> c, Reason r) {
        Iterator<K> itr = c.iterator();
        Set<V> s = new HashSet<>();
        while (itr.hasNext()) {
            K mine = itr.next();
            V removed = remove(mine, r);
            if (removed != null) {
                s.add(removed);
            }
        }
        return s;
    }

    /**
     * Removes all mappings from this map.
     */
    @Override
    public void clear() {
        Set<K> m = null;
        synchronized (this) {
            m = new HashSet<>(this.keySet());
        }
        removeAll(m, null);
    }

    /**
     * returns the objects in the values of this map ordered by the comparator. Changes in the original map will reflect in
     * the subset).
     * <P>
     * For example, if you remove an object from the original map it will also be removed from the subset.
     *
     * @param comparator comparator to use when sorting the objects
     * @return a set ordered by the comparator
     */
    public Set<V> subSet(Comparator<V> comparator) {
        Set<V> s = new TreeSet<>(comparator);
        synchronized (this) {
            s.addAll(values());
            if (comparatorSets == null) {
                comparatorSets = Collections.synchronizedMap(new WeakValueHashMap<>("Comparator"));
            }
            comparatorSets.put(comparator, s);
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
    public void putAll(Map<? extends K, ? extends V> m) {
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
    public void putAll(Map<? extends K, ? extends V> m, Reason reason) {
        for (Map.Entry<? extends K, ? extends V> me : m.entrySet()) {
            put(me.getKey(), me.getValue(), reason);
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
    public V put(K key, V value) {
        return this.put(key, value, null);
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or <tt>null</tt> if there was no mapping for key. A
     * <tt>null</tt> return can also indicate that the map previously associated <tt>null</tt> with the specified key.
     * @see #remove(Object, Reason)
     * @throws ClassCastException If the key is not of the type foe the map
     */
    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        return this.remove((K) key, null);
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
    public V put(K key, V value, Reason reason) {
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
    public V put(K key, V value, Reason reason, boolean overwrite) {
        return put(key, value, reason, overwrite, true);
    }

    public V put(K key, V value, Reason reason, boolean overwrite, boolean checklimit) {

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
        V ret = null;

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

            if (ret instanceof Sized) { // replaced
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
                Iterator<Set> itr = comparatorSets.values().iterator();
                while (itr.hasNext()) {
                    Set s = itr.next();
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
            notifyChange(EventType.SIZE_CHANGED, oldSize, newSize, reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) && oldBytes != newBytes) {
            notifyChange(EventType.BYTES_CHANGED, oldBytes, newBytes, reason);
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
            notifyChange(EventType.EMPTY, wasEmpty, isEmpty, reason);
        }

        if (hasListeners(EventType.FULL) && wasFull != isFull) {
            notifyChange(EventType.FULL, wasFull, isFull, reason);
        }
        return ret;
    }

    /**
     * determines if the map should throw an exception if the size is exceeded, or just call the FULL event listeners
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
    public V get(Object key) {
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
    public V remove(K key, Reason reason) {
        return removeWithValue(key, null, null, reason);
    }

    /**
     * Removes a key-value pair if and only if the value matches the expected value.
     * @param key key to remove
     * @param expectedValue value that is expected to be with the key in the map
     * @param errValue Value returned if expectedValue was not in the map
     * @param reason
     * @return errValue if expectedValue not null and if to be removed value not null and != expectedValue
     */
    public V removeWithValue(K key, V expectedValue, V errValue, Reason reason) {

        V value = null;

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
                Iterator<Set> itr = comparatorSets.values().iterator();
                while (itr.hasNext()) {
                    Set s = itr.next();
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
            notifyChange(EventType.SIZE_CHANGED, oldSize, newSize, reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) && oldBytes != newBytes) {
            notifyChange(EventType.BYTES_CHANGED, oldBytes, newBytes, reason);
        }

        if (hasListeners(EventType.SET_CHANGED)) {
            Object oldv = oldEntry;
            oldEntry.update(key, value);
            Map.Entry newv = null;

            notifyChange(EventType.SET_CHANGED, oldv, newv, reason);
        }

        if (hasListeners(EventType.EMPTY) && wasEmpty != isEmpty) {
            notifyChange(EventType.EMPTY, wasEmpty, isEmpty, reason);
        }

        if (hasListeners(EventType.FULL) && wasFull != isFull) {
            notifyChange(EventType.FULL, wasFull, isFull, reason);
        }
        return value;

    }

    /**
     * returns a new map that contains all objects matching the filter. This new map will not be updated to reflect changes
     * in the original set.
     *
     * @param f filter to use when matching
     * @return a new map of matching objects
     * @see #subMap(Filter)
     */
    public Map<K, V> getAll(Filter f) {
        Map<K, V> m = new HashMap<>();
        synchronized (this) {
            for (Map.Entry<K, V> entry : this.entrySet()) {
                V value = entry.getValue();
                if (f == null || f.matches(value)) {
                    m.put(entry.getKey(), value);
                }
            }
            return m;
        }
    }

    public List<K> getAllKeys() {
        synchronized (this) {
            return new ArrayList<>(this.keySet());
        }
    }

    /**
     * @param count return first count of keys
     */
    public List<K> getFirstKeys(int count) {
        int cnt = 0;
        List<K> l = new ArrayList<>(count);
        synchronized (this) {
            Set<Map.Entry<K, V>> entrySet = this.entrySet();
            Iterator<Map.Entry<K, V>> itr = entrySet.iterator();
            while (cnt < count && itr.hasNext()) {
                Map.Entry<K, V> e = itr.next();
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
    public Object addEventListener(EventListener listener, EventType type, Object userData) {
        return ebh.addEventListener(listener, type, userData);
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
            notifyChange(EventType.FULL, wasFull, isFull, null);
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
            notifyChange(EventType.FULL, wasFull, isFull, null);
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

    static class NLMapEntry<K, V> implements Map.Entry<K, V> {
        K key;
        V value;

        public void update(K k, V v) {
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
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public V setValue(V o) {
            throw new UnsupportedOperationException("Can not set values on the entry");
        }

    }
    
    class FilterMap extends HashMap<K, V> {
    /**
     * 
     */
    private static final long serialVersionUID = -5287488524104212543L;
    Filter f = null;

    FilterMap(Filter f) {
        this.f = f;
    }

    public Filter getFilter() {
        return f;
    }
}

}
