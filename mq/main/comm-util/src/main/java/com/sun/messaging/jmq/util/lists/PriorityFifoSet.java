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
 * @(#)PriorityFifoSet.java	1.22 08/28/07
 */

package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * This is an Priority Fifo set which implements the SortedSet interface.
 * 
 * Entries have a given priority, which is a minimum value of 0 (maximum priority)
 * to a given maximum (lowest priority).
 */
public class PriorityFifoSet<E> extends FifoSet<E> implements Prioritized<E> {
    SetEntry<E> priorities[] = null;
    protected int defaultPriority = 0;
    int levels = 0;

    /**
     * Creates a PriorityFifoSet with 11 priority levels.
     * The minimum priority will be 0 and max 10.
     */
    public PriorityFifoSet() {
        this(10);
    }

    public int getLevels() {
        return levels;
    }

    @Override
    public void clear() {
        super.clear();
        for (int i = 0; i < levels; i++) {
            priorities[i] = null;
        }
    }

    /**
     * Creates a PriorityFifoSet which ranges in priority level from 0 to the given value.
     * @param levels The maximum priority level
     */
    public PriorityFifoSet(int levels) {
        super();
        this.levels = levels;
        priorities = new SetEntry[levels + 1];
        defaultPriority = levels / 2;
    }

    @Override
    public boolean add(E o) {
        assert lock == null || Thread.holdsLock(lock);
        return add(defaultPriority, o);
    }

    @Override
    public void addAllOrdered(Collection<E> c) {
    }

    @Override
    public void addAllToFront(Collection<E> c, int priority) {
        assert lock == null || Thread.holdsLock(lock);

        if (priorities[priority] == null) {
            // hey .. we just put it in the real place
            Iterator<E> itr = c.iterator();
            while (itr.hasNext()) {
                E o = itr.next();
                add(priority, o);
            }
        } else {
            SetEntry<E> startOfList = null;
            SetEntry<E> endEntry = priorities[priority];
            Iterator<E> itr = c.iterator();
            while (itr.hasNext()) {
                E o = itr.next();
                // make sure its not already there
                // if it is, remove it, we want to
                // replace it
                if (lookup.get(o) != null) {
                    remove(o);
                }
                // add the message @ the right priority
                SetEntry<E> e = createSetEntry(o, priority);
                lookup.put(o, e);
                endEntry.insertEntryBefore(e);
                if (startOfList == null) {
                    startOfList = e;
                }
            }
            priorities[priority] = startOfList;
            if (head == endEntry) {
                head = startOfList;
            }

        }
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(int p) {
        defaultPriority = p;
    }

    protected SetEntry<E> createSetEntry(E o, int p) {
        return new PrioritySetEntry<>(o, p);
    }

    @Override
    public boolean add(int pri, E o) {
        assert lock == null || Thread.holdsLock(lock);
        if (parent != null) {
            if (end != null && pri >= ((PrioritySetEntry) end).getPriority()) {
                throw new IllegalArgumentException("Object added is past end of subset");
            }
            if (start != null && pri <= ((PrioritySetEntry) start).getPriority()) {
                throw new IllegalArgumentException("Object added is past begining of subset");
            }
            return ((PriorityFifoSet<E>) parent).add(pri, o);
        }

        if (pri >= priorities.length) {
            throw new OutOfLimitsException(OutOfLimitsException.PRIORITY_EXCEEDED, pri, priorities.length);
        }

        // make sure its not already there
        // if it is, remove it, we want to
        // replace it
        if (lookup.get(o) != null) {
            remove(o);
        }

        // add the message @ the right priority
        SetEntry<E> e = createSetEntry(o, pri);
        lookup.put(o, e);

        if (head == null) {
            priorities[pri] = e;
            head = tail = e;
            return true;
        }

        // what priority is head at ?

        int hpri = ((PrioritySetEntry) head).getPriority();
        if (hpri > pri) { // add before
            priorities[pri] = e;
            head.insertEntryBefore(e);
            head = e;
            return true;
        }

        // we are not first .. see if we will be last
        if (tail == null) {
            SetEntry<E> fix = head;
            while (fix.getNext() != null) {
                fix = fix.getNext();
            }
            tail = fix;
        }

        int tpri = ((PrioritySetEntry) tail).getPriority();

        if (tpri <= pri) { // were last
            tail.insertEntryAfter(e);
            if (priorities[pri] == null) {
                priorities[pri] = e;
            }
            tail = e;
            return true;
        }

        // not first or last ... just somewhere in the list
        // loop through until I get the priority after me
        SetEntry<E> target = null;
        int i = pri + 1;
        while (i < priorities.length) {
            if (priorities[i] != null) {
                target = priorities[i];
                break;
            }
            i++;
        }
        if (target != null) {
            target.insertEntryBefore(e);
            if (priorities[pri] == null) {
                priorities[pri] = e;
            }
            /*
             * head can't be null if we get here if (head == null) { head = e; }
             */
            return true;
        }
        return false;
    }

    public String toDebugString() {
        StringBuilder str = new StringBuilder();
        str.append("PriorityFifoSet[").append(this.hashCode()).append("]\n\tpriorities:\n");

        for (int i = 0; i < priorities.length; i++) {
            str.append("\t\t").append(i).append("\t").append(priorities[i]).append("\n");
        }
        str.append("\thead=").append(head).append("\n");
        str.append("\ttail=").append(tail).append("\n");
        str.append("\tstart=").append(start).append("\n");
        str.append("\tend=").append(end).append("\n");
        synchronized (lookup) {
            str.append("\tlookup: size=").append(lookup.size()).append(", isEmpty=").append(lookup.isEmpty()).append("\n");
            Iterator<Object> itr = lookup.keySet().iterator();
            while (itr.hasNext()) {
                Object key = itr.next();
                str.append("\t\t[").append(key).append(",").append(lookup.get(key)).append("]\n");
            }
        }
        return str.toString();

    }

    @Override
    protected boolean cleanupEntry(SetEntry<E> e) {
        assert lock == null || Thread.holdsLock(lock);
        PrioritySetEntry<E> pe = (PrioritySetEntry<E>) e;
        int pri = pe.getPriority();
        if (priorities[pri] == pe) {
            PrioritySetEntry<E> nexte = (PrioritySetEntry<E>) pe.getNext();
            if (nexte != null && nexte.getPriority() == pri) {
                priorities[pri] = nexte;
            } else {
                priorities[pri] = null;
            }
        }
        assert pe.getPrevious() != null || pe == head : pe;
        assert pe.getNext() != null || pe == tail : pe;
        // super should take care of head or tail
        return super.cleanupEntry(e);
    }

    @Override
    public boolean remove(Object o) {
        assert lock == null || Thread.holdsLock(lock) : lock + ":" + this;
        return super.remove(o);
    }

    @Override
    public void sort(Comparator<SetEntry<E>> c) {
        super.sort(c);

        for (int i = 0; i < levels; i++) {
            if (priorities[i] != null) {
                priorities[i] = priorities[i].sort(c);
            }
        }
    }

}
