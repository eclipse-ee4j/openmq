/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * This is a First In-First Out set which implements the SortedSet interface.
 * @since 1.22
 */
public class FifoSet<E> extends AbstractSet<E> implements SortedSet<E> {

    // linked list entries for the list
    protected SetEntry<E> head = null;
    protected SetEntry<E> tail = null;
    protected Map<Object, SetEntry<E>> lookup = null;

    // interfaces used for first/last subLists
    protected FifoSet<E> parent = null;
    protected SetEntry<E> start = null;
    protected SetEntry<E> end = null;

    protected Object lock = null;

    protected void setLock(Object lock) {
        this.lock = lock;
    }

    @Override
    public String toString() {
        return this.getClass().toString() /* + " : " + this.hashCode() */;
    }

    @Override
    public boolean isEmpty() {
        return lookup.isEmpty();
    }

    @Override
    public int size() {
        // assert lock == null || Thread.holdsLock(lock) : "lock is " + lock.toString();
        if (parent == null) {
            return lookup.size();
        }
        if (start == null && end == null) {
            return 0;
        }
        int cnt = 0;
        Iterator<E> itr = iterator();
        while (itr.hasNext()) {
            itr.next();
            cnt++;
        }
        return cnt;
    }

    @Override
    public boolean contains(Object o) {
        assert lock == null || Thread.holdsLock(lock);
        
        SetEntry<E> se = lookup.get(o);
        boolean has = se != null && se.isValid();
        return has;
    }

    public FifoSet() {
        lookup = new HashMap<>();
        this.head = null;
        this.tail = null;

        this.start = null;
        this.end = null;

        parent = null;
    }

    private FifoSet(FifoSet<E> parent, SetEntry<E> start, SetEntry<E> end) {
        this.parent = parent;
        this.lookup = parent.lookup;
        this.start = start;
        this.end = end;
    }

    class SetIterator implements Iterator<E> {
        SetEntry<E> last_entry = null; // exclusive
        SetEntry<E> first_entry = null; // inclusive
        boolean initialPass = true;
        SetEntry<E> current = null;

        SetIterator(SetEntry<E> initialEntry, SetEntry<E> finalEntry) {
            assert lock == null || Thread.holdsLock(lock) : " lock is " + lock + ":" + Thread.holdsLock(lock);
            this.first_entry = initialEntry;
            this.last_entry = finalEntry;
            this.current = null;
            initialPass = true;
        }

        @Override
        public boolean hasNext() {
            assert lock == null || Thread.holdsLock(lock);
            if (current == null && !initialPass) {
                return false;
            }
            SetEntry<E> nextEntry = null;
            if (initialPass) {
                nextEntry = first_entry;
            } else {
                nextEntry = current.getNext();
            }
            while (nextEntry != null && !nextEntry.isValid() && nextEntry != last_entry) {
                nextEntry = nextEntry.getNext();
            }
            return nextEntry != null && nextEntry != last_entry && nextEntry.isValid();
        }

        @Override
        public E next() {
            assert lock == null || Thread.holdsLock(lock);
            if (current == null && initialPass) {
                current = first_entry;
                initialPass = false;
            } else if (current != null) {
                current = current.getNext();
            }

            if (current == null) {
                throw new NoSuchElementException("set empty");
            }
            while (current != null && !current.isValid() && current != last_entry) {
                current = current.getNext();
            }
            if (current == null || !current.isValid() || current == last_entry) {
                throw new NoSuchElementException("set empty");
            }
            return current.getData();
        }

        @Override
        public void remove() {
            if (current != null) {
                cleanupEntry(current);
            }

        }
    }

    protected boolean cleanupEntry(SetEntry<E> e) {

        assert !lookup.isEmpty() || (head == null && tail == null);
        assert (head != null && tail != null) || (head == null && tail == null) : "values: " + head + "," + tail;

        if (e == null) {
            return false;
        }
        boolean retval = e.isValid();
        lookup.remove(e.getData());
        e.remove();

        SetEntry oldhead = head;
        SetEntry oldtail = tail;

        if (head == e) {
            head = e.getNext();
        }
        if (tail == e) {
            tail = e.getPrevious();
        }
        assert (head != null && tail != null) || (head == null && tail == null) : "values: " + "\n\thead: " + head + "\n\ttail: " + tail + "\n\toldhead: "
                + oldhead + "\n\toldtail: " + oldtail + "\n\tentry removed:" + e + "\n\n";

        return retval;
    }

    @Override
    public Iterator<E> iterator() {
        assert lock == null || Thread.holdsLock(lock) : " lock is " + lock + ":" + Thread.holdsLock(lock);
        SetEntry<E> beginEntry = (parent != null ? (start == null ? parent.head : start) : head);
        return new SetIterator(beginEntry, end);
    }

    /**
     * Returns the comparator associated with this sorted set, or {@code null} if it uses its elements' natural ordering.
     *
     * @return the comparator associated with this sorted set, or {@code null} if it uses its elements' natural ordering.
     */
    @Override
    public Comparator<E> comparator() {
        return null;
    }

    /**
     * Returns a view of the portion of this sorted set whose elements range from {@code fromElement}, inclusive, to
     * {@code toElement}, exclusive.
     *
     * @throws ClassCastException if {@code fromElement} and {@code toElement} cannot be compared to one another using
     * this set's comparator (or, if the set has no comparator, using natural ordering). Implementations may, but are not
     * required to, throw this exception if {@code fromElement} or {@code toElement} cannot be compared to elements
     * currently in the set.
     * @throws IllegalArgumentException if {@code fromElement} is greater than {@code toElement}; or if this set is itself
     * a subSet, headSet, or tailSet, and {@code fromElement} or {@code toElement} are not within the specified range of
     * the subSet, headSet, or tailSet.
     * @throws NullPointerException if {@code fromElement} or {@code toElement} is {@code null} and this sorted set does
     * not tolerate {@code null} elements.
     */
    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        assert lock == null || Thread.holdsLock(lock);
        if (parent != null) {
            boolean foundFrom = fromElement == null;
            boolean foundTo = toElement == null;
            Iterator<E> itr = iterator();
            while (itr.hasNext()) {
                E o = itr.next();
                if (!foundFrom && (o == fromElement || o.equals(fromElement))) {
                    foundFrom = true;
                }
                if (!foundTo && (o == toElement || o.equals(toElement))) {
                    foundTo = true;
                }
                if (foundTo && foundFrom) {
                    break;
                }
            }
            if (!foundFrom || !foundTo) {
                throw new IllegalArgumentException("Elements are not in subset");
            }

        }
        SetEntry<E> st = null;
        if (fromElement != null) {
            st = lookup.get(fromElement);
        }
        SetEntry<E> end = null;
        if (toElement != null) {
            end = lookup.get(toElement);
        }
        return new FifoSet<>(this, st, end);
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are strictly less than {@code toElement}. The
     * returned sorted set is backed by this sorted set, so changes in the returned sorted set are reflected in this sorted
     * set, and vice-versa. The returned sorted set supports all optional set operations.
     * <p>
     *
     * The sorted set returned by this method will throw an {@code IllegalArgumentException} if the user attempts to insert
     * a element outside the specified range.
     * <p>
     *
     * Note: this method always returns a view that does not contain its (high) endpoint. If you need a view that does
     * contain this endpoint, and the element type allows for calculation of the successor a given value, merely request a
     * headSet bounded by {@code successor(highEndpoint)}. For example, suppose that {@code s} is a sorted set of strings.
     * The following idiom obtains a view containing all of the strings in {@code s} that are less than or equal to
     * {@code high}:
     *
     * <pre>
     * SortedSet head = s.headSet(high + "\0");
     * </pre>
     *
     * @param toElement high endpoint (exclusive) of the headSet.
     * @return a view of the specified initial range of this sorted set.
     * @throws ClassCastException if {@code toElement} is not compatible with this set's comparator (or, if the set has no
     * comparator, if {@code toElement} does not implement {@code Comparable}). Implementations may, but are not required
     * to, throw this exception if {@code toElement} cannot be compared to elements currently in the set.
     * @throws NullPointerException if {@code toElement} is {@code null} and this sorted set does not tolerate
     * {@code null} elements.
     * @throws IllegalArgumentException if this set is itself a subSet, headSet, or tailSet, and {@code toElement} is not
     * within the specified range of the subSet, headSet, or tailSet.
     */
    @Override
    public SortedSet<E> headSet(E toElement) {
        return subSet(null, toElement);
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are greater than or equal to {@code fromElement}.
     * The returned sorted set is backed by this sorted set, so changes in the returned sorted set are reflected in this
     * sorted set, and vice-versa. The returned sorted set supports all optional set operations.
     * <p>
     *
     * The sorted set returned by this method will throw an {@code IllegalArgumentException} if the user attempts to insert
     * a element outside the specified range.
     * <p>
     *
     * Note: this method always returns a view that contains its (low) endpoint. If you need a view that does not contain
     * this endpoint, and the element type allows for calculation of the successor a given value, merely request a tailSet
     * bounded by {@code successor(lowEndpoint)}. For example, suppose that {@code s} is a sorted set of strings. The
     * following idiom obtains a view containing all of the strings in {@code s} that are strictly greater than
     * {@code low}:
     *
     * <pre>
     * SortedSet tail = s.tailSet(low + "\0");
     * </pre>
     *
     * @param fromElement low endpoint (inclusive) of the tailSet.
     * @return a view of the specified final range of this sorted set.
     * @throws ClassCastException if {@code fromElement} is not compatible with this set's comparator (or, if the set has
     * no comparator, if {@code fromElement} does not implement {@code Comparable}). Implementations may, but are not
     * required to, throw this exception if {@code fromElement} cannot be compared to elements currently in the set.
     * @throws NullPointerException if {@code fromElement} is {@code null} and this sorted set does not tolerate
     * {@code null} elements.
     * @throws IllegalArgumentException if this set is itself a subSet, headSet, or tailSet, and {@code fromElement} is not
     * within the specified range of the subSet, headSet, or tailSet.
     */
    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return subSet(fromElement, null);
    }

    @Override
    public void clear() {
        assert lock == null || Thread.holdsLock(lock);

        Iterator<E> itr = iterator();
        while (itr.hasNext()) {
            // Object o = itr.next();
            itr.next();
            itr.remove();
        }
        if (parent == null) {
            super.clear();
            lookup.clear();
        }
        head = null;
        tail = null;
        start = null;
        end = null;
    }

    /**
     * Returns the first (lowest) element currently in this sorted set.
     *
     * @return the first (lowest) element currently in this sorted set.
     * @throws NoSuchElementException sorted set is empty.
     */
    @Override
    public E first() {
        assert lock == null || Thread.holdsLock(lock);
        SetEntry<E> beginEntry = (parent != null ? (start == null ? parent.head : start) : head);

        E o = (beginEntry == null ? null : beginEntry.getData());
        // LKS - XXX workaround for corruption until I find a cure
        if ((beginEntry != null && lookup.get(o) == null) || (beginEntry == null && !lookup.isEmpty())) {
            assert false : "List corrupted: " + "\n\t beginEntry: " + (beginEntry == null ? "null" : beginEntry.toString()) + "\n\t parent : " + parent
                    + "\n\t head " + head + "\n\t start " + start + "\n\t lookup " + lookup;

            /*
             * LKS SetEntry se = beginEntry.getNext(); if (head == beginEntry) head = se; else if (start == beginEntry) start = se;
             * else if (parent != null && parent.head == beginEntry) parent.head = se; return first();
             */
        }
        return o;
    }

    /**
     * Returns the last (highest) element currently in this sorted set.
     *
     * @return the last (highest) element currently in this sorted set.
     * @throws NoSuchElementException sorted set is empty.
     */
    @Override
    public E last() {
        assert lock == null || Thread.holdsLock(lock);
        if (parent != null) {
            if (end != null) {
                return end.getPrevious().getData();
            }
            return parent.last();
        }
        return (tail == null ? null : tail.getData());
    }

    @Override
    public boolean add(E o) {
        assert lock == null || Thread.holdsLock(lock);

        if (parent != null) {
            if (end != null) {
                throw new IllegalArgumentException("Object added is past end of subset");
            }
            return parent.add(o);
        }

        // replace previous entries
        if (lookup.get(o) != null) {
            remove(o);
        }

        SetEntry<E> e = new SetEntry<>(o);
        lookup.put(o, e);
        if (tail != null) {
            tail.insertEntryAfter(e);
        }
        tail = e;
        if (head == null) {
            head = tail;
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        assert lock == null || Thread.holdsLock(lock) : lock + " : " + this;
        if (parent != null) {
            // make sure it is in the subset
            Iterator<E> itr = iterator();
            while (itr.hasNext()) {
                if (itr.next() == o) {
                    itr.remove();
                    return true;
                }
            }
            return false;
        }
        SetEntry<E> e = lookup.get(o);
        if (e == null) {
            return false; // not in list
        }
        return cleanupEntry(e);
    }

    public void sort(Comparator<SetEntry<E>> c) {
        if (head == null) {
            return;
        }
        head = head.sort(c);
        if (start != null) {
            start = head;
        }
        // find tail
        SetEntry<E> e = head;
        while (e.next != null) {
            e = e.next;
        }
        tail = e;
        if (end != null) {
            end = tail;
        }
    }

}
