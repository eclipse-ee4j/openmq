/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2020, 2022 Contributors to Eclipse Foundation
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
 * This is an Priority Fifo set which implements the if (endEntry != null) endEntry = priorities[pri]; SortedSet
 * interface.
 */
@SuppressWarnings("SynchronizeOnNonFinalField")
public class NFLPriorityFifoSet<E> extends PriorityFifoSet<E> implements FilterableSet<E>, EventBroadcaster, Limitable {

    private static boolean DEBUG = false;

    Set<NotifyInfo> gni = new HashSet<>();

    // filter stuff
    Object filterSetLock = new Object();
    Map<Object, FilterSet> filterSets = null;
    Map<Object, ComparatorSet<E>> comparatorSets = null;

    // event stuff
    EventBroadcastHelper ebh = new EventBroadcastHelper();

    // limit stuff
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
    protected boolean orderMaintained = true;

    private long queuePosition = 0;

    public NFLPriorityFifoSet() {
        this(10, false);
    }

    public NFLPriorityFifoSet(int levels) {
        this(levels, false);
    }

    public NFLPriorityFifoSet(int levels, boolean maintainOrder) {
        super(levels);
        orderMaintained = maintainOrder;
        ebh.setOrderMaintained(maintainOrder);
        setLock(this);
        lookup = Collections.synchronizedMap(lookup);
    }

    public void enforceLimits(boolean enforce) {
        this.enforceLimits = enforce;
    }

    public boolean getEnforceLimits() {
        return enforceLimits;
    }

    @Override
    protected boolean cleanupEntry(SetEntry<E> e) {
        synchronized (lock) {
            return super.cleanupEntry(e);
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean ok = false;
        Iterator<? extends E> itr = c.iterator();
        while (itr.hasNext()) {
            ok |= add(itr.next());
        }
        return ok;
    }

    @Override
    public void clear() {
        synchronized (lock) {
            Iterator<E> itr = iterator();
            while (itr.hasNext()) {
                itr.next();
                itr.remove();
            }
            bytes = 0;

            super.clear();
        }
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
        // we are only equal if we are the same object
        return obj == this;
    }

    @Override
    public boolean add(E o) {
        return add(defaultPriority, o, null);
    }

    @Override
    public int size() {
        synchronized (lock) {
            return super.size();
        }
    }

    static class ComparatorSet<C> extends TreeSet<C> implements SubSet<C> {
        private static final long serialVersionUID = -2212313455661614252L;

        transient EventBroadcastHelper ebh = new EventBroadcastHelper();

        Object uid;
        transient NFLPriorityFifoSet<C> parent = null;

        ComparatorSet(Object uid, Comparator<? super C> c, NFLPriorityFifoSet<C> p) {
            super(c);
            this.uid = uid;
            this.parent = p;
        }

        @Override
        public String toDebugString() {
            return "ComparatorSet [" + comparator() + "]" + parent.toDebugString();
        }

        @Override
        public void destroy() {
            parent.destroyComparatorSet(this.uid);
        }

        void addItem(C o) {
            super.add(o);
        }

        void removeItem(C o) {
            super.remove(o);
        }

        @Override
        public boolean add(C o) {
            return add(o, null);
        }

        @Override
        public boolean add(C o, Reason r) {
            boolean ok = super.add(o);
            parent.add(o, r);
            return ok;
        }

        @Override
        public boolean remove(Object o) {
            return remove((C) o, null);
        }

        @Override
        public boolean remove(C o, Reason r) {
            boolean ok = super.remove(o);
            parent.remove(o, r);
            return ok;
        }

        @Override
        public C removeNext() {
            C o = null;

            synchronized (parent.lock) {
                o = first();
            }
            if (o != null) {
                parent.remove(o);
            }
            return o;
        }

        @Override
        public C peekNext() {
            return first();
        }

        public Object getUID() {
            return uid;
        }

        @Override
        public Object addEventListener(EventListener listener, EventType type, Object userData) {
            if (type != EventType.EMPTY) {
                throw new UnsupportedOperationException("Event " + type + " not supported");
            }
            return ebh.addEventListener(listener, type, userData);
        }

        @Override
        public Object addEventListener(EventListener listener, EventType type, Reason r, Object userData) {
            if (type != EventType.EMPTY) {
                throw new UnsupportedOperationException("Event " + type + " not supported");
            }
            return ebh.addEventListener(listener, type, r, userData);
        }

        @Override
        public Object removeEventListener(Object id) {
            return ebh.removeEventListener(id);
        }

        public void notifyEmptyChanged(boolean empty, Reason r) {
            if (ebh.hasListeners(EventType.EMPTY)) {
                ebh.notifyChange(EventType.EMPTY, r, this, (empty ? Boolean.TRUE : Boolean.FALSE), (empty ? Boolean.FALSE : Boolean.TRUE));
            }
        }
    }

    class FilterSet extends AbstractSet<E> implements SubSet<E>, Prioritized<E> {
        EventBroadcastHelper ebh = new EventBroadcastHelper();

        Object uid;
        Filter f = null;
        int currentPriority;

        // NOTE: either currentEntry is set OR
        // nextEntry is set NEVER both
        nSetEntry nextEntry = null;
        nSetEntry currentEntry = null;

        @Override
        public String toString() {
            return "FilterSet[" + f + "]" + super.toString() + "(uid=" + uid + ")";
        }

        public void resetFilterSet(nSetEntry top) {
            synchronized (lock) {
                nextEntry = top;
                currentEntry = null;
            }
        }

        @Override
        public String toDebugString() {
            StringBuilder str = new StringBuilder();
            str.append("FilterSet[").append(f).append("]\n");
            str.append("\tDumping FilterSet\n");
            Iterator itr = iterator();
            while (itr.hasNext()) {
                str.append("\t\t").append(itr.next()).append('\n');
            }
            str.append("\tcurrentPriority ").append(currentPriority).append('\n');
            str.append("\tnextEntry ").append(nextEntry).append('\n');
            str.append("\tcurrentEntry ").append(currentEntry).append('\n');
            str.append('\t').append(ebh);
            str.append("NFLPriorityFifoSet.this.head=").append(NFLPriorityFifoSet.this.head).append('\n');
            str.append("NFLPriorityFifoSet.this.tail=").append(NFLPriorityFifoSet.this.tail).append('\n');
            str.append(NFLPriorityFifoSet.this.toDebugString());
            return str.toString();
        }

        @Override
        public void addAllToFront(Collection<E> c, int pri) {
            NFLPriorityFifoSet.this.addAllToFront(c, pri);
        }

        @Override
        public void addAllOrdered(Collection<E> c) {
            NFLPriorityFifoSet.this.addAllOrdered(c);
        }

        class filterIterator implements Iterator<E> {
            nSetEntry current = null;

            filterIterator() {
                synchronized (lock) {
                    current = currentEntry;
                    if (current == null) {
                        current = nextEntry;
                        findNext();
                    }
                }
            }

            void findNext() {
                synchronized (lock) {
                    while (current != null) {
                        if (current.isValid() && (f == null || f.matches(current.getData()))) {
                            break;
                        }
                        current = (nSetEntry) current.getNext();
                    }
                }
            }

            @Override
            public boolean hasNext() {
                synchronized (lock) {
                    return current != null;
                }
            }

            @Override
            public E next() {
                synchronized (lock) {
                    E n = current.getData();
                    current = (nSetEntry) current.getNext();
                    findNext();
                    return n;
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove is not supported on this iterator");
            }

        }

        FilterSet(Object uid, Filter f) {
            ebh.setOrderMaintained(orderMaintained);
            synchronized (lock) {
                this.uid = uid;
                this.f = f;

                this.nextEntry = (nSetEntry) (NFLPriorityFifoSet.this.start == null ? NFLPriorityFifoSet.this.head : NFLPriorityFifoSet.this.start);
            }

        }

        public Object getUID() {
            return uid;
        }

        private boolean skipToNext() {

            assert Thread.holdsLock(lock);
            synchronized (lock) {
                if (currentEntry != null) {
                    if (!currentEntry.isValid()) {
                        nextEntry = (nSetEntry) currentEntry.getNext();
                        currentEntry = null;
                    } else {
                        return true;
                    }
                }
                if (nextEntry == null) {
                    return false;
                }
                nSetEntry se = nextEntry;

                while (se != null && !se.isValid()) {
                    se = (nSetEntry) se.getNext();
                }

                while (se != null && f != null && !f.matches(se.getData())) {
                    currentPriority = se.getPriority();
                    se = (nSetEntry) se.getNext();
                }

                // OK .. at this point nextEntry is a valid item
                currentEntry = se;
                nextEntry = null;

                if (currentEntry != null) {
                    currentPriority = currentEntry.getPriority();
                }
                return currentEntry != null;
            }

        }

        void removeItem(E o) {
            assert Thread.holdsLock(lock);
            assert o != null;

            synchronized (lock) {
                if (nextEntry != null && nextEntry.getData() == o) {
                    nextEntry = (nSetEntry) nextEntry.getNext();
                }
                if (currentEntry != null && currentEntry.getData() == o) {
                    nextEntry = (nSetEntry) currentEntry.getNext();
                    currentEntry = null;
                }
            }
        }

        void addItem(E o) {
            assert Thread.holdsLock(lock);

            nSetEntry pe = (nSetEntry) lookup.get(o);

            synchronized (lock) {

                if (currentEntry == null && nextEntry == null) {
                    nextEntry = pe;
                    currentPriority = pe.priority;
                } else if (pe.getPriority() == currentPriority
                        && ((currentEntry != null && !currentEntry.isValid()) || (nextEntry != null && !nextEntry.isValid()))) {
                    nextEntry = pe;
                    currentEntry = null;
                } else if (pe.getPriority() < currentPriority) {
                    currentPriority = pe.getPriority();
                    nextEntry = pe;
                    currentEntry = null;
                }
            }
        }

        void addItem(E o, boolean toFront) {
            assert lock == null || Thread.holdsLock(lock);
            nSetEntry pe = (nSetEntry) lookup.get(o);
            if (toFront) {
                synchronized (lock) {
                    nextEntry = pe;
                    currentEntry = null;
                    currentPriority = 0;
                }
            } else {
                addItem(o);
            }

        }

        @Override
        public boolean add(E o) {
            return add(o, null);
        }

        @Override
        public boolean add(int p, E o) {
            return add(p, o, null);
        }

        @Override
        public boolean add(E o, Reason r) {
            if (f != null && !f.matches(o)) {
                throw new IllegalArgumentException("not part of set");
            }
            return NFLPriorityFifoSet.this.add(o, r);
        }

        public boolean add(int p, E o, Reason r) {
            if (f != null && !f.matches(o)) {
                throw new IllegalArgumentException("not part of set");
            }
            return NFLPriorityFifoSet.this.add(p, o, r);
        }

        @Override
        public void clear() {

            // OK .. we only want matching items
            // AND this will also clear parent list
            Set<E> s = new HashSet<>();
            synchronized (lock) {
                Iterator<E> itr = iterator();
                while (itr.hasNext()) {
                    s.add(itr.next());
                }
            }
            removeAll(s);
        }

        @Override
        @SuppressWarnings({"unchecked"}) //Javadoc states that this may throw a ClassCastException
        public boolean remove(Object o) {
            return remove((E) o, null);

        }

        @Override
        public boolean remove(E o, Reason r) {
            if (f != null && !f.matches(o)) {
                return false;
            }
            return NFLPriorityFifoSet.this.remove(o, r);
        }

        @Override
        public boolean contains(Object o) {

            synchronized (lock) {
                nSetEntry pse = (nSetEntry) lookup.get(o);
                if (pse == null) {
                    return false;
                }

                if (f == null) {
                    return true;
                }

                return f.matches(o);
            }
        }

        @Override
        public int size() {
            // this is SLOW (we have to check each item
            synchronized (lock) {
                int cnt = 0;
                Iterator<E> itr = iterator();
                while (itr.hasNext()) {
                    itr.next();
                    cnt++;
                }
                return cnt;
            }
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Set<E> s = new HashSet<>();
            synchronized (lock) {
                Iterator<E> itr = NFLPriorityFifoSet.this.iterator();
                while (itr.hasNext()) {
                    E o = itr.next();
                    if (!c.contains(o)) {
                        s.add(o);
                    }
                }
            }
            return NFLPriorityFifoSet.this.removeAll(s);
        }

        @Override
        public boolean isEmpty() {
            synchronized (lock) {
                boolean state = !skipToNext();
                return state;
            }
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return NFLPriorityFifoSet.this.removeAll(c);
        }

        @Override
        public void destroy() {
            NFLPriorityFifoSet.this.destroyFilterSet(this.uid);
        }

        @Override
        public Iterator<E> iterator() {
            return new filterIterator();
        }

        @Override
        public E removeNext() {

            E currentData = null;
            NotifyInfo ni = null;
            synchronized (lock) {

                if (!skipToNext()) {
                    // removeNext failed
                    return null;
                }
                if (currentEntry == null) {
                    if (DEBUG && f == null && nextEntry == null && lookup.size() != 0) {
                        throw new RuntimeException("Corruption noticed in removeNext " + " lookup.size is not 0 " + lookup);
                    }

                    return null;
                }
                currentData = currentEntry.getData();

                nextEntry = (nSetEntry) currentEntry.getNext();
                currentEntry = null;
                ni = internalRemove(currentData, null, null, hasListeners());

                if (DEBUG && f == null && currentEntry == null && nextEntry == null && !lookup.isEmpty())

                {
                    throw new RuntimeException("Corruption noticed in removeNext " + " lookup.size is not 0 " + lookup);
                }
            }
            // yes .. this is the wrong order .. bummer
            preRemoveNotify(currentData, null);
            if (ni != null) {
                postRemoveNotify(currentData, ni, null);
            }

            return currentData;
        }

        @Override
        public E peekNext() {
            synchronized (lock) {
                if (!skipToNext()) {
                    return null;
                }
                if (currentEntry == null) {
                    return null;
                }
                return currentEntry.getData();
            }
        }

        @Override
        public Object addEventListener(EventListener listener, EventType type, Object userData) {
            if (type != EventType.EMPTY) {
                throw new UnsupportedOperationException("Event " + type + " not supported");
            }
            return ebh.addEventListener(listener, type, userData);
        }

        @Override
        public Object addEventListener(EventListener listener, EventType type, Reason r, Object userData) {
            if (type != EventType.EMPTY) {
                throw new UnsupportedOperationException("Event " + type + " not supported");
            }
            return ebh.addEventListener(listener, type, r, userData);
        }

        @Override
        public Object removeEventListener(Object id) {
            return ebh.removeEventListener(id);
        }

        public void notifyEmptyChanged(boolean empty, Reason r) {
            if (ebh.hasListeners(EventType.EMPTY)) {
                ebh.notifyChange(EventType.EMPTY, r, this, (empty ? Boolean.TRUE : Boolean.FALSE), (empty ? Boolean.FALSE : Boolean.TRUE));
            }
        }
    }

    // XXX - only generate empty notification for now

    @Override
    public void addAllToFront(Collection<E> c, int pri) {
        addAllToFront(c, pri, (Reason) null);
    }

    @Override
    public void addAllOrdered(Collection<E> c) {
        addAllOrdered(c, (Reason) null);
    }

    public void addAllOrdered(Collection<E> c, Reason reason) {

        if (c.isEmpty()) {
            return;
        }

        Set<Object> notify = null;
        boolean notifyTop = false;
        boolean wasEmpty = false;
        wasEmpty = isEmpty();

        Iterator<E> itr = c.iterator();

        // we need this to determine the head

        while (itr.hasNext()) {
            SetEntry<E> ientry = null;
            boolean found = false;
            int pri = 0;
            E o = null;
            synchronized (lock) {
                o = itr.next();
                if (!(o instanceof Ordered)) {
                    throw new RuntimeException("Can not order unordered items");
                }

                Ordered oo = (Ordered) o;

                QueuingOrder orderobj = (QueuingOrder) oo.getOrder();

                pri = orderobj.priority;

                // make sure we dont have a dup entry
                // if it is, remove it so we replace it
                if (lookup.get(o) != null) {
                    remove(o);
                }

                // find the right place and add in front
                ientry = priorities[pri];
                found = false;

                // before we search through everything check the end of the list
                if (tail != null && orderobj.greaterThan((QueuingOrder) ((Ordered) tail.getData()).getOrder())) {
                    // use normal add logic
                    ientry = null;
                }
                if (pri < (levels - 2) && priorities[pri + 1] != null) {
                    SetEntry back = priorities[pri + 1].getPrevious();
                    if (back == null) {
                        ientry = null;
                    } else if (back.getData() == null) {
                        ientry = null;
                    } else if (orderobj.greaterThan((QueuingOrder) ((Ordered) back.getData()).getOrder())) {
                        ientry = null;
                    }
                }
                // ok - we failed so we need to iterate
                while (!found && ientry != null) {
                    Object io = ientry.getData();
                    if (!(io instanceof Ordered)) {
                        throw new RuntimeException("Can not order unordered items");
                    }
                    Ordered so = (Ordered) io;
                    if (orderobj.greaterThan((QueuingOrder) so.getOrder())) {
                        if (ientry.getNext() == null) {
                            break;
                        }
                        ientry = ientry.getNext();
                        continue;
                    }
                    // we found a spot;
                    found = true;
                }

                // if found, insert before, else insert after
                if (ientry != null) {
                    // we are going to have to stick it in the middle
                    SetEntry<E> e = createSetEntry(o, pri);
                    // Object obj = lookup.put(o,e);
                    lookup.put(o, e);
                    if (found) {
                        ientry.insertEntryBefore(e);
                        // e.insertEntryBefore(ientry);

                        if (ientry == priorities[pri]) {
                            // priorities[pri - 1] = e;
                            priorities[pri] = e;
                        }
                        if (ientry == head) {
                            head = e;
                        }
                    } else {
                        ientry.insertEntryAfter(e);
                        if (ientry == tail) {
                            tail = e;
                        }
                    }

                    if (wasEmpty != isEmpty()) {
                        notifyTop = true;
                    }

                    // update any iterators
                    SetEntry<E> startOfList = head;

                    if (startOfList != null && filterSets != null) {
                        for (var s : filterSets.values()) {
                            if (s == null) {
                                continue;
                            }
                            boolean wasFilterEmpty = s.isEmpty();
                            s.addItem(startOfList.getData(), true);
                            // if the filter is empy or the parent list was
                            // we have to notify
                            if (wasFilterEmpty || notifyTop) {
                                if (notify == null) {
                                    notify = new HashSet<>();
                                }
                                notify.add(s.getUID());
                            }
                        }
                        if (comparatorSets != null) {
                            // LKS - XXX
                            // not dealing w/ comparator sets yet
                        }
                    }
                }

            } // end synchronization

            if (ientry == null) { // just use the normal logic
                add(pri, o);
                if (wasEmpty != isEmpty()) {
                    notifyTop = true;
                }
                continue;
            }
        }

        if (notify != null || notifyTop) {

            if (notifyTop && hasListeners(EventType.EMPTY)) {
                notifyChange(EventType.EMPTY, Boolean.TRUE, Boolean.FALSE, reason);
            }

            if (notify != null) {
                Object uid = null;
                SubSet<E> subSet = null;
                Iterator<Object> nitr = notify.iterator();
                while (nitr.hasNext()) {
                    uid = nitr.next();
                    if (uid == null) {
                        continue;
                    }
                    synchronized (filterSetLock) {
                        subSet = filterSets.get(uid);
                    }
                    if (subSet == null) {
                        continue;
                    }
                    if (FilterSet.class.isInstance(subSet)) {
                        ((FilterSet) subSet).notifyEmptyChanged(!(((FilterSet) subSet).isEmpty()), reason);
                    } else { // when supported, add comparatorSetLock similar as filterSetLock
                        assert subSet instanceof ComparatorSet;
                        ((ComparatorSet) subSet).notifyEmptyChanged(!(((ComparatorSet) subSet).isEmpty()), reason);
                    }
                }
            }
        }

    }

    public void addAllToFront(Collection<E> c, int pri, Reason reason) {
        if (c.isEmpty()) {
            return;
        }

        Set<Object> notify = null;
        boolean notifyTop = false;
        boolean wasEmpty = false;
        synchronized (lock) {
            wasEmpty = isEmpty();

            SetEntry<E> startOfList = null;
            if (priorities[pri] == null) {
                // hey .. we just put it in the real place
                Iterator<E> itr = c.iterator();
                while (itr.hasNext()) {
                    E o = itr.next();
                    super.add(pri, o);
                    if (startOfList == null) {
                        startOfList = lookup.get(o);
                    }
                }
            } else {
                SetEntry<E> endEntry = priorities[pri];
                Iterator<E> itr = c.iterator();
                while (itr.hasNext()) {
                    E o = itr.next();

                    // make sure we dont have a dup entry
                    // if it is, remove it so we replace it
                    SetEntry dup = null;
                    if ((dup = lookup.get(o)) != null) {
                        remove(o);
                        if (endEntry == dup) {
                            endEntry = null;
                        }
                        if (dup == startOfList) {
                            startOfList = priorities[pri];
                        }
                    }
                    if (endEntry == null) {
                        super.add(pri, o);
                        if (startOfList == null) {
                            startOfList = lookup.get(o);
                        }
                        continue;
                    }

                    // add the message @ the right priority

                    SetEntry<E> e = createSetEntry(o, pri);
                    // Object obj = lookup.put(o,e);
                    lookup.put(o, e);
                    endEntry.insertEntryBefore(e);

                    if (startOfList == null) {
                        startOfList = e;
                        priorities[pri] = startOfList;
                        if (endEntry == head) {
                            head = startOfList;
                        }
                    }
                }

            }

            if (wasEmpty != isEmpty()) {
                notifyTop = true;
            }

            // update any iterators

            if (filterSets != null) {
                for (var s : filterSets.values()) {
                    if (s == null) {
                        continue;
                    }
                    boolean wasFilterEmpty = s.isEmpty();
                    s.addItem(startOfList.getData(), true);
                    // if the filter is empy or the parent list was
                    // we have to notify
                    if (wasFilterEmpty || notifyTop) {
                        if (notify == null) {
                            notify = new HashSet<>();
                        }
                        notify.add(s.getUID());
                    }
                }
                if (comparatorSets != null) {
                    // LKS - XXX
                    // not dealing w/ comparator sets yet

                }

            }
        }

        if (notify != null || notifyTop) {

            if (notifyTop && hasListeners(EventType.EMPTY)) {
                notifyChange(EventType.EMPTY, Boolean.TRUE, Boolean.FALSE, reason);
            }

            if (notify != null) {
                Object uid = null;
                SubSet subSet = null;
                Iterator<Object> nitr = notify.iterator();
                while (nitr.hasNext()) {
                    uid = nitr.next();
                    if (uid == null) {
                        continue;
                    }
                    synchronized (filterSetLock) {
                        subSet = filterSets.get(uid);
                    }
                    if (subSet == null) {
                        continue;
                    }
                    if (FilterSet.class.isInstance(subSet)) {
                        ((FilterSet) subSet).notifyEmptyChanged(!(((FilterSet) subSet).isEmpty()), reason);
                    } else { // when supported, add comparatorSetLock similar as filterSetLock
                        assert subSet instanceof ComparatorSet;
                        ((ComparatorSet) subSet).notifyEmptyChanged(!(((ComparatorSet) subSet).isEmpty()), reason);
                    }
                }
            }
        }

    }

    @Override
    public boolean add(E o, Reason r) {
        return add(defaultPriority, o, r);
    }

    @Override
    public boolean add(int pri, E o) {
        return add(pri, o, null);
    }

    private void preAdd(Object o, Reason reason) {
        // OK notify of changeRequest
        if (hasListeners(EventType.SET_CHANGED_REQUEST)) {
            notifyChange(EventType.SET_CHANGED_REQUEST, null, o, reason);
        }

        if (o == null) {
            throw new NullPointerException("Unable to support null " + " values");
        }
    }

    NotifyInfo internalAdd(int pri, E o) {
        assert Thread.holdsLock(this);
        NotifyInfo ni = null;
        int oldsize = 0;
        long oldbytes = 0;
        long objsize = 0;
        boolean added = false;
        if (maxByteCapacity != UNLIMITED_BYTES && !(o instanceof Sized)) {
            throw new ClassCastException("Unable to add object not of" + " type Sized when byteCapacity has been set");
        }
        if (maxBytePerObject != UNLIMITED_BYTES && !(o instanceof Sized)) {
            throw new ClassCastException("Unable to add object not of" + " type Sized when maxByteSize has been set");
        }

        if (enforceLimits && maxCapacity != UNLIMITED_CAPACITY && ((maxCapacity - size()) <= 0)) {
            throw new OutOfLimitsException(OutOfLimitsException.CAPACITY_EXCEEDED, size(), maxCapacity);
        }

        if (enforceLimits && maxByteCapacity != UNLIMITED_BYTES && ((maxByteCapacity - bytes) <= 0)) {
            throw new OutOfLimitsException(OutOfLimitsException.BYTE_CAPACITY_EXCEEDED, bytes, maxByteCapacity);
        }

        if (o instanceof Sized) {
            objsize = ((Sized) o).byteSize();
        }

        if (maxBytePerObject != UNLIMITED_BYTES && objsize > maxBytePerObject) {
            throw new OutOfLimitsException(OutOfLimitsException.ITEM_SIZE_EXCEEDED, objsize, maxByteCapacity);
        }

        oldsize = size();
        oldbytes = bytes;

        // OK -- add the actual data

        added = super.add(pri, o);

        // assign a sortable number
        // priority + long value
        //
        if (o instanceof Ordered && ((Ordered) o).getOrder() == null) {
            QueuingOrder orderobj = new QueuingOrder();
            orderobj.priority = pri;
            orderobj.position = queuePosition;
            queuePosition++;
            ((Ordered) o).setOrder(orderobj);
        }

        bytes += objsize;

        averageCount = ((numberSamples * averageCount + size()) / (numberSamples + 1.0F));
        averageBytes = (numberSamples * averageBytes + bytes) / (numberSamples + 1.0D);
        messageAverage = (numberSamples * messageAverage + objsize) / (numberSamples + 1.0D);
        numberSamples++;

        if (added) {
            if (size() > highWaterCnt) {
                highWaterCnt = size();
            }
            if (objsize > largestMessageHighWater) {
                largestMessageHighWater = objsize;
            }
            if (bytes > highWaterBytes) {
                highWaterBytes = bytes;
            }

            if (hasListeners() || (filterSets != null && !filterSets.isEmpty()) || (comparatorSets != null && !comparatorSets.isEmpty())) {
                ni = getNI();
                ni.oldsize = oldsize;
                ni.oldbytes = oldbytes;
                ni.objsize = objsize;
                ni.newbytes = oldbytes + objsize;
                ni.newsize = size();
                ni.curMaxCapacity = maxCapacity;
                ni.curMaxBytesCapacity = maxByteCapacity;

                int cnt = 0;

                synchronized (lock) {

                    if (filterSets != null) {
                        for (var s : filterSets.values()) {
                            if (s == null) {
                                continue;
                            }
                            boolean wasEmpty = s.isEmpty();
                            s.addItem(o);
                            if (wasEmpty != s.isEmpty()) {
                                if (ni.filters[cnt] == null) {
                                    ni.filters[cnt] = new EmptyChanged();
                                }
                                ni.filters[cnt].f = s;
                                ni.filters[cnt].isEmpty = !wasEmpty;
                                cnt++;
                            }
                        }
                    }
                    if (comparatorSets != null) {
                        Iterator<ComparatorSet<E>> itr = comparatorSets.values().iterator();
                        while (itr.hasNext()) {
                            ComparatorSet<E> s = itr.next();
                            if (s == null) {
                                continue;
                            }
                            boolean wasEmpty = s.isEmpty();
                            s.addItem(o);
                            if (wasEmpty != s.isEmpty()) {
                                if (ni.filters[cnt] == null) {
                                    ni.filters[cnt] = new EmptyChanged();
                                }
                                ni.filters[cnt].f = s;
                                ni.filters[cnt].isEmpty = !wasEmpty;
                                cnt++;
                            }
                        }
                    }
                }
                if (cnt < ni.filters.length && ni.filters[cnt] != null) {
                    ni.filters[cnt].f = null;
                }

            }
        }
        return ni;
    }

    private void postAdd(Object o, NotifyInfo ni, Reason reason) {
        // send out any notifications !!!
        if (hasListeners(EventType.SIZE_CHANGED) && ni.oldsize != ni.newsize) {
            notifyChange(EventType.SIZE_CHANGED, ni.oldsize, ni.newsize, reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) && ni.oldbytes != ni.newbytes) {
            notifyChange(EventType.BYTES_CHANGED, ni.oldbytes, ni.newbytes, reason);
        }
        if (hasListeners(EventType.SET_CHANGED)) {
            notifyChange(EventType.SET_CHANGED, null, o, reason);
        }

        if (ni.oldsize == 0 && ni.newsize != 0 && hasListeners(EventType.EMPTY)) {
            notifyChange(EventType.EMPTY, Boolean.TRUE, Boolean.FALSE, reason);
        }

        if (hasListeners(EventType.FULL) && (ni.curMaxBytesCapacity != UNLIMITED_BYTES && ((ni.curMaxBytesCapacity - ni.newbytes) <= 0))
                || (ni.curMaxCapacity != UNLIMITED_BYTES && ((ni.curMaxCapacity - ni.newsize) <= 0))) {
            notifyChange(EventType.FULL, Boolean.FALSE, Boolean.TRUE, reason);
        }
        for (EmptyChanged filter : ni.filters) {
            if (filter == null || filter.f == null) {
                break;
            }
            SubSet subSet = filter.f;
            if (FilterSet.class.isInstance(subSet)) {
                ((FilterSet) subSet).notifyEmptyChanged(filter.isEmpty, reason);
            } else {
                assert subSet instanceof ComparatorSet;
                ((ComparatorSet) subSet).notifyEmptyChanged(filter.isEmpty, reason);
            }
        }
        putNI(ni);
    }

    public boolean add(int pri, E o, Reason reason) {
        NotifyInfo ni = null;
        preAdd(o, reason);
        synchronized (lock) {
            ni = internalAdd(pri, o);
        }
        if (ni != null) {
            postAdd(o, ni, reason);
        }
        return ni != null;
    }

    public boolean removeAll(Collection<E> c, Reason r) {
        boolean removed = false;
        Iterator<E> itr = c.iterator();
        while (itr.hasNext()) {
            removed |= remove(itr.next(), r);
        }
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        return remove((E) o, (Reason) null);
    }

    static class NotifyInfo {
        long oldbytes = 0;
        long newbytes = 0;
        int oldsize = 0;
        int newsize = 0;
        long objsize = 0;
        int curMaxCapacity = 0;
        long curMaxBytesCapacity = 0;
        EmptyChanged filters[] = null;

        NotifyInfo() {
            filters = new EmptyChanged[0];
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("NotifyInfo:");
            for (int i = 0; i < filters.length; i++) {
                str.append('[').append(i).append(']').append(filters[i]);
            }
            return str.toString();
        }
    }

    static class EmptyChanged {
        SubSet f;
        boolean isEmpty = false;

        @Override
        public String toString() {
            return "EmptyChanged:isEmpty=" + isEmpty + "f={" + (f == null ? "null" : f.toString()) + "}";
        }
    }

    NotifyInfo getNI() {
        NotifyInfo ni = null;
        synchronized (lock) {

            if (gni.isEmpty()) {
                ni = new NotifyInfo();
            } else {
                Iterator<NotifyInfo> itr = gni.iterator();
                ni = itr.next();
                itr.remove();
            }
            int size = (filterSets == null ? 0 : filterSets.size());
            if (ni.filters == null || ni.filters.length < size) {
                ni.filters = new EmptyChanged[size];
            }
        }
        return ni;
    }

    void putNI(NotifyInfo ni) {
        if (ni == null) {
            return;
        }
        synchronized (lock) {
            gni.add(ni);
        }
    }

    NotifyInfo internalRemove(E o, Reason r, Iterator pitr, boolean hasListeners) {
        synchronized (lock) {
            assert Thread.holdsLock(lock);
            long objsize = 0;
            int oldsize = size();
            long oldbytes = bytes;
            if (o instanceof Sized) {
                objsize = ((Sized) o).byteSize();
            }
            nSetEntry nse = (nSetEntry) lookup.get(o);
            if (nse == null) { // already removed
                return null;
            }
            boolean removed = true;
            if (pitr != null) {
                pitr.remove();
            } else {
                assert Thread.holdsLock(lock);
                removed = super.remove(o);
            }
            if (!removed) {
                return null;
            }
            if (!hasListeners() && (filterSets == null || filterSets.isEmpty()) && (comparatorSets == null || comparatorSets.isEmpty())) {
                return null;
            }
            NotifyInfo ni = getNI();
            ni.oldbytes = oldbytes;
            ni.oldsize = oldsize;
            ni.objsize = objsize;
            ni.newsize = size();
            ni.newbytes = bytes;
            ni.curMaxCapacity = maxCapacity;
            ni.curMaxBytesCapacity = maxByteCapacity;

            int cnt = 0;
            if (filterSets != null) {
                for (var s : filterSets.values()) {
                    if (s == null) {
                        continue;
                    }
                    boolean empty = s.isEmpty();
                    s.removeItem(o);
                    if (empty != s.isEmpty()) {
                        if (ni.filters[cnt] == null) {
                            ni.filters[cnt] = new EmptyChanged();
                        }
                        ni.filters[cnt].f = s;
                        ni.filters[cnt].isEmpty = empty;
                        cnt++;
                    }
                }
            }
            if (comparatorSets != null) {
                for (ComparatorSet<E> s : comparatorSets.values()) {
                    if (s == null) {
                        continue;
                    }
                    boolean empty = s.isEmpty();
                    s.removeItem(o);
                    if (empty != s.isEmpty()) {
                        if (ni.filters[cnt] == null) {
                            ni.filters[cnt] = new EmptyChanged();
                        }
                        ni.filters[cnt].f = s;
                        ni.filters[cnt].isEmpty = !empty;
                        cnt++;
                    }
                }
            }
            if (cnt < ni.filters.length && ni.filters[cnt] != null) {
                ni.filters[cnt].f = null;
            }
            if (pitr == null) {
                nse.clear();
            }
            return ni;
        }
    }

    @Override
    public boolean remove(E o, Reason r) {
        if (o == null) {
            return false;
        }
        synchronized (lock) {
            if (!contains(o)) {
                return false;
            }
        }
        preRemoveNotify(o, r);
        NotifyInfo ni = internalRemove(o, r, null, hasListeners());
        if (ni != null) {
            postRemoveNotify(o, ni, r);
        }
        synchronized (lock) {
            return !contains(o);
        }
    }

    public E removeNext() {
        return removeNext(null);
    }

    public E removeNext(Reason r) {
        E o = null;
        NotifyInfo ni = null;
        synchronized (lock) {
            o = first();
            if (o == null) {
                return null;
            }
            ni = internalRemove(o, r, null, hasListeners());
        }
        preRemoveNotify(o, r);
        if (ni != null) {
            postRemoveNotify(o, ni, r);
        }
        return o;
    }

    public E peekNext() {
        synchronized (lock) {
            return first();
        }
    }

    class wrapIterator implements Iterator<E> {
        Iterator<E> parentIterator;
        E next = null;

        wrapIterator(Iterator<E> itr) {
            synchronized (lock) {
                parentIterator = itr;
            }
        }

        @Override
        public boolean hasNext() {
            synchronized (lock) {
                return parentIterator.hasNext();
            }
        }

        @Override
        public E next() {
            synchronized (lock) {
                next = parentIterator.next();
                return next;
            }
        }

        @Override
        public void remove() {
            preRemoveNotify(next, null);
            NotifyInfo ni = internalRemove(next, null, parentIterator, hasListeners());
            if (ni != null) {
                postRemoveNotify(next, ni, null);
            }
            next = null;
        }
    }

    @Override
    public Iterator<E> iterator() {
        synchronized (lock) {
            return new wrapIterator(super.iterator());
        }
    }

    @Override
    public SubSet<E> subSet(Filter f) {
        synchronized (lock) {
            Object uid = new Object();
            FilterSet fs = new FilterSet(uid, f);
            if (filterSets == null) {
                filterSets = new WeakValueHashMap<>("FilterSet");
            }
            synchronized (filterSetLock) {
                filterSets.put(uid, fs);
            }
            return fs;
        }
    }

    @Override
    public SubSet<E> subSet(Comparator<E> c) {
        synchronized (lock) {
            Object uid = new Object();
            ComparatorSet<E> cs = new ComparatorSet<>(uid, c, this);
            if (comparatorSets == null) {
                comparatorSets = new WeakValueHashMap<>("ComparatorSet");
            }
            comparatorSets.put(uid, cs);
            return cs;
        }
    }

    @Override
    public Set<E> getAll(Filter f) {
        synchronized (lock) {
            Set<E> s = new LinkedHashSet<>();
            Iterator<E> itr = iterator();
            while (itr.hasNext()) {
                E o = itr.next();
                if (f == null || f.matches(o)) {
                    s.add(o);
                }
            }
            return s;
        }
    }

    private void destroyFilterSet(Object uid) {
        assert filterSets != null;
        synchronized (lock) {
            if (filterSets != null) {
                synchronized (filterSetLock) {
                    filterSets.remove(uid);
                }
            }
        }

    }

    private void destroyComparatorSet(Object uid) {
        assert comparatorSets != null;
        synchronized (lock) {
            if (comparatorSets != null) {
                comparatorSets.remove(uid);
            }
        }
    }

    public void destroy() {
        // clean up for gc
        ebh.clear();
        super.clear();
        synchronized (lock) {
            if (filterSets != null) {
                synchronized (filterSetLock) {
                    filterSets.clear();
                }
            }
            if (comparatorSets != null) {
                comparatorSets.clear();
            }
            gni.clear();
        }
    }

    @Override
    protected SetEntry<E> createSetEntry(E o, int p) {
        return new nSetEntry(o, p);
    }

    long currentID = 1;

    class nSetEntry extends PrioritySetEntry<E> {
        long uid = 0;

        nSetEntry(E o, int p) {
            super(o, p);
            synchronized (lock) {
                uid = currentID++;
            }
        }

        public long getUID() {
            return uid;
        }

        @Override
        public boolean remove() {
            assert Thread.holdsLock(lock);
            // we are always locked (in lock mode)
            // when this is called
            E data = getData();
            assert isValid();
            valid = false;
            boolean ok = super.remove();

            // update bytes
            if (data instanceof Sized) {
                long removedBytes = ((Sized) data).byteSize();
                bytes -= removedBytes;
            }
            // update averages
            averageCount = ((numberSamples * averageCount + size()) / (numberSamples + 1.0F));
            averageBytes = (numberSamples * averageBytes + bytes) / (numberSamples + 1.0D);
            numberSamples++;

            return ok;
        }
    }

    @Override
    public synchronized String toDebugString() {
        StringBuilder str = new StringBuilder();
        str.append("NFLPriorityFifoSet: " + "\n");
        if (filterSets != null) {
            str.append("\tfilterSets: ").append(filterSets.size()).append('\n');
            for (var fs : filterSets.values()) {
                if (fs == null) {
                    continue;
                }
                str.append("\t\tFilterSet ").append(fs.hashCode()).append(" filter[").append(fs.f).append("]\n");
            }
        }
        if (comparatorSets != null) {
            str.append("\tComparatorSets: ").append(comparatorSets.size()).append('\n');
            for (ComparatorSet<E> fs : comparatorSets.values()) {
                if (fs == null) {
                    continue;
                }
                str.append("\t\tComparatorSet ").append(fs.hashCode()).append(" filter[").append(fs.comparator()).append("]\n");
            }
        }
        str.append('\t').append(ebh);
        str.append("\n\nSUBCLASS INFO\n");
        str.append(super.toDebugString());
        return str.toString();
    }

    protected void preRemoveNotify(E o, Reason reason) {
        if (hasListeners(EventType.SET_CHANGED_REQUEST)) {
            notifyChange(EventType.SET_CHANGED_REQUEST, o, null, reason);
        }
    }

    protected void postRemoveNotify(E o, NotifyInfo ni, Reason reason) {
        if (!hasListeners()) {
            return;
        }

        // first notify SIZE changed
        if (ni.oldsize != ni.newsize && hasListeners(EventType.SIZE_CHANGED)) {
            notifyChange(EventType.SIZE_CHANGED, ni.oldsize, ni.newsize, reason);
        }
        if (ni.newbytes != ni.oldbytes && hasListeners(EventType.BYTES_CHANGED)) {
            notifyChange(EventType.BYTES_CHANGED, ni.oldbytes, ni.newbytes, reason);
        }
        if (hasListeners(EventType.SET_CHANGED)) {
            notifyChange(EventType.SET_CHANGED, o, null, reason);
        }

        if (ni.oldsize != 0 && ni.newsize == 0 && hasListeners(EventType.EMPTY)) {
            notifyChange(EventType.EMPTY, Boolean.FALSE, Boolean.TRUE, reason);
        }
        if (hasListeners(EventType.FULL)
                && (ni.curMaxBytesCapacity != UNLIMITED_BYTES && ((ni.curMaxBytesCapacity - ni.oldbytes) <= 0) && ((ni.curMaxBytesCapacity - ni.newbytes) > 0))
                || (ni.curMaxCapacity != UNLIMITED_CAPACITY && ((ni.curMaxCapacity - ni.oldsize) <= 0) && ((ni.curMaxCapacity - ni.newsize) > 0))) {
            // not full
            notifyChange(EventType.FULL, Boolean.TRUE, Boolean.FALSE, reason);
        }
        for (EmptyChanged filter : ni.filters) {
            if (filter == null || filter.f == null) {
                break;
            }
            SubSet s = filter.f;
            if (FilterSet.class.isInstance(s)) {
                ((FilterSet) s).notifyEmptyChanged(filter.isEmpty, reason);
            } else {
                assert s instanceof ComparatorSet;
                ((ComparatorSet) s).notifyEmptyChanged(filter.isEmpty, reason);
            }
        }
        putNI(ni);
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
        synchronized (lock) {
            return highWaterBytes;
        }
    }

    /**
     * The largest message (which implements Sized) which has ever been stored in this list.
     *
     * @return the number of bytes of the largest message ever stored on this list.
     */
    @Override
    public long highWaterLargestMessageBytes() {
        synchronized (lock) {
            return largestMessageHighWater;
        }
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
        synchronized (lock) {
            return averageBytes;
        }
    }

    /**
     * The average message size (which implements Sized) of messages which has been stored in this list.
     *
     * @return the number of bytes of the average message stored on this list.
     */
    @Override
    public double averageMessageBytes() {
        synchronized (lock) {
            return messageAverage;
        }
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
        synchronized (lock) {
            maxBytePerObject = bytes;
        }
    }

    /**
     * returns the maximum size of an entry allowed to be added to the collection
     *
     * @return maximum number of bytes for an object added to the list or UNLIMITED_BYTES if there is no limit
     */
    @Override
    public long maxByteSize() {
        synchronized (lock) {
            return maxBytePerObject;
        }
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
        boolean nowFull = false;
        boolean nowNotFull = false;
        synchronized (lock) {
            nowFull = (!isFull() && cnt != UNLIMITED_CAPACITY && cnt <= size());
            nowNotFull = isFull() && (cnt == UNLIMITED_CAPACITY || cnt > size());

            maxCapacity = cnt;
        }
        if (nowFull) {
            notifyChange(EventType.FULL, Boolean.FALSE, Boolean.TRUE, null);
        } else if (nowNotFull) {
            notifyChange(EventType.FULL, Boolean.TRUE, Boolean.FALSE, null);
        }
    }

    /**
     * Sets the byte capacity. Once the byte capacity is set, only objects which implement Sized can be added to the class
     *
     * @param size the byte capacity for this set (or UNLIMITED_BYTES if unlimited).
     */
    @Override
    public void setByteCapacity(long size) {
        boolean nowFull = false;
        boolean nowNotFull = false;
        if (size < UNLIMITED_BYTES) {
            size = UNLIMITED_BYTES;
        }

        synchronized (lock) {
            nowFull = (!isFull() && size != UNLIMITED_BYTES && size <= byteSize());
            nowNotFull = isFull() && (size == UNLIMITED_CAPACITY || size > byteSize());
            maxByteCapacity = size;
        }

        if (nowFull) {
            notifyChange(EventType.FULL, Boolean.FALSE, Boolean.TRUE, null);
        } else if (nowNotFull) {
            notifyChange(EventType.FULL, Boolean.TRUE, Boolean.FALSE, null);
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
     * Returns the byte capacity or UNLIMITED_CAPACITY if its not set.
     *
     * @return the capacity of the list
     */
    @Override
    public long byteCapacity() {
        synchronized (lock) {
            return maxByteCapacity;
        }
    }

    /**
     * Returns {@code true} if either the bytes limit or the count limit is set and has been reached or exceeded.
     *
     * @return {@code true} if the count limit is set and has been reached or exceeded.
     * @see #freeSpace
     * @see #freeBytes
     */
    @Override
    public boolean isFull() {
        synchronized (lock) {
            return freeSpace() == 0 || freeBytes() == 0;
        }
    }

    /**
     * Returns number of entries remaining in the lists to reach full capacity or UNLIMITED_CAPACITY if the capacity has not
     * been set
     *
     * @return the amount of free space
     */
    @Override
    public int freeSpace() {
        synchronized (lock) {
            if (maxCapacity == UNLIMITED_CAPACITY) {
                return UNLIMITED_CAPACITY;
            }

            int sz = maxCapacity - size();
            if (sz < 0) {
                return 0;
            }
            return sz;
        }
    }

    /**
     * Returns the number of bytesremaining in the lists to reach full capacity, 0 if the list is greater than the capacity
     * or UNLIMITED_BYTES if the capacity has not been set
     *
     * @return the amount of free space
     */
    @Override
    public long freeBytes() {
        synchronized (lock) {
            if (maxByteCapacity == UNLIMITED_BYTES) {
                return UNLIMITED_BYTES;
            }

            long retval = maxByteCapacity - bytes;
            if (retval < 0) {
                return 0;
            }
            return retval;
        }
    }

    /**
     * Returns the number of bytes used by all entries in this set which implement Sized. If this set contains more than
     * {@code Long.MAX_VALUE} elements, returns {@code Long.MAX_VALUE}.
     *
     * @return the total bytes of data from all objects implementing Sized in this set.
     * @see Sized
     * @see #size
     */
    @Override
    public long byteSize() {
        synchronized (lock) {
            return bytes;
        }
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

    protected boolean hasListeners() {
        return ebh.hasListeners();
    }

    protected void notifyChange(EventType e, Object oldval, Object newval, Reason r) {
        if (!hasListeners()) {
            return;
        }
        ebh.notifyChange(e, r, this, oldval, newval);
    }

    @Override
    public void sort(Comparator<SetEntry<E>> c) {
        super.sort(c);
        // reset subsets
        if (filterSets != null) {
            for (var s : filterSets.values()) {
                if (s == null) {
                    continue;
                }
                s.resetFilterSet((nSetEntry) head);
            }
        }
    }

}
