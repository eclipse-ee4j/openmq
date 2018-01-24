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
 * @(#)NFLPriorityFifoSet.java	1.46 11/04/07
 */

package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * This is an Priority Fifo set which implements the
                    if (endEntry != null) endEntry =  priorities[pri];
 * SortedSet interface.
 */

public class NFLPriorityFifoSet extends PriorityFifoSet 
    implements FilterableSet,  EventBroadcaster, Limitable
{

    private static boolean DEBUG = false;

    Set gni = new HashSet();

    // filter stuff
    Object filterSetLock = new Object(); 
    Map filterSets = null;
    Map comparatorSets = null;

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

    protected boolean cleanupEntry(SetEntry e) {
        synchronized(lock) {
            return super.cleanupEntry(e);
        }
    }

    public boolean addAll(Collection c) {
        boolean ok = false;
        Iterator itr = c.iterator();
        while (itr.hasNext()) {
            ok |= add(itr.next());
        }
        return ok;
    }

    public void clear() {
        synchronized(lock) {
            Iterator itr = iterator();
            while (itr.hasNext()) {
                itr.next();
                itr.remove();
            }
            bytes = 0;

            super.clear();
        }
    }


    public boolean equals(Object obj)
    {
        // we are only equal if we are the same object
        if (obj == this) return true;
        return false;
    }

    public int hashCode() {
        return super.hashCode();
    }

          
    
    public boolean add(Object o) {
        return add(defaultPriority, o, null);
    }

    public int size() {
        synchronized(lock) {
            return super.size();
        }
    }


    static class ComparatorSet extends TreeSet implements SubSet
    {
        transient EventBroadcastHelper ebh = new EventBroadcastHelper();

        Object uid;
        transient NFLPriorityFifoSet parent = null;

        public ComparatorSet(Object uid, Comparator c, NFLPriorityFifoSet p) {
            super(c);
            this.uid = uid;
            this.parent = p;
        }
        public String toDebugString() {
            return "ComparatorSet [" +
                  comparator() + "]" 
                + parent.toDebugString();
        }

        public void destroy() {
            parent.destroyComparatorSet(this.uid);
        }

        void addItem(Object o) {
            super.add(o);
        }

        void removeItem(Object o) {
            super.remove(o);
        }

        public boolean add(Object o) {
            return add(o, null);
        }

        public boolean add(Object o, Reason r) {
            boolean ok = super.add(o);
            parent.add(o, r);
            return ok;
        }

        public boolean remove(Object o) {
            return remove(o, null);
        }
        public boolean remove(Object o, Reason r) {
            boolean ok = super.remove(o);
            parent.remove(o, r);
            return ok;
        }

         public Object removeNext() {
             boolean ok = false;
             Object o = null;

             synchronized(parent.lock) { 
                 o = first();
             }
             if (o != null) {
                 parent.remove(o);
             }
             return o;
         }

         public Object peekNext() {
             return first();
         }

         public Object getUID() {
             return uid;
         }

         public Object addEventListener(EventListener listener,
                        EventType type, Object userData)
             throws UnsupportedOperationException 
         {
             if (type != EventType.EMPTY) {
                 throw new UnsupportedOperationException(
                   "Event " + type + " not supported");
             }
             return ebh.addEventListener(listener, type, userData);
         }
         public Object addEventListener(EventListener listener,
                         EventType type, Reason r, Object userData)
             throws UnsupportedOperationException 
         {
             if (type != EventType.EMPTY) {
                 throw new UnsupportedOperationException(
                   "Event " + type + " not supported");
             }
             return ebh.addEventListener(listener, type, r, userData);
        }
 
        public Object removeEventListener(Object id)
        {
            return ebh.removeEventListener(id);
        }

        
        public void notifyEmptyChanged(boolean empty, Reason r)
        {
           if (ebh.hasListeners(EventType.EMPTY))
                ebh.notifyChange(EventType.EMPTY, r, this,
                    (empty ? Boolean.TRUE : Boolean.FALSE),
                    (empty ? Boolean.FALSE : Boolean.TRUE));
        }
    }



    class FilterSet extends AbstractSet implements SubSet, Prioritized
    {
        EventBroadcastHelper ebh = new EventBroadcastHelper();

        Object uid;
        Filter f = null;
        int currentPriority;

        // NOTE: either currentEntry is set OR
        //       nextEntry is set NEVER both
        nSetEntry nextEntry = null;
        nSetEntry currentEntry = null;

        public String toString() {
            return "FilterSet["+f+"]" + super.toString()+"(uid="+uid+")";
        }

        public void resetFilterSet(nSetEntry top)
        {
            synchronized (lock) {
                nextEntry = top;
                currentEntry = null;
            }
        }

        public String toDebugString() {
            StringBuffer str = new StringBuffer();
            str.append("FilterSet[" + f + "]\n");
            str.append("\tDumping FilterSet\n");
            Iterator itr = iterator();
            while(itr.hasNext()) {
                str.append("\t\t"+itr.next() + "\n");
            }
            str.append("\tcurrentPriority " + currentPriority + "\n");
            str.append("\tnextEntry " + nextEntry + "\n");
            str.append("\tcurrentEntry " + currentEntry + "\n");
            str.append("\t"+ebh.toString());
            str.append("NFLPriorityFifoSet.this.head="+NFLPriorityFifoSet.this.head+"\n");
            str.append("NFLPriorityFifoSet.this.tail="+NFLPriorityFifoSet.this.tail+"\n");
            str.append(NFLPriorityFifoSet.this.toDebugString());
            return str.toString();
        }

        public void addAllToFront(Collection c, int pri) {
            NFLPriorityFifoSet.this.addAllToFront(c, pri);
        }

        public void addAllOrdered(Collection c) {
            NFLPriorityFifoSet.this.addAllOrdered(c);
        }
        
        class filterIterator implements Iterator 
        {
            nSetEntry current = null;

            public filterIterator() {
                synchronized(lock) {
                current = currentEntry;
                    if (current == null) {
                        current = nextEntry;
                        findNext();
                    }
                }
            }

            void findNext() {
                synchronized(lock) {
                    while (current != null) {
                        if (current.isValid() &&
                            (f == null || f.matches(current.getData()))){
                            break;
                        }
                        current = (nSetEntry)current.getNext();
                    }
                }
            }

            public boolean hasNext() {
                synchronized(lock) {
                    return current != null;
                }
            }

            public Object next() {
                synchronized(lock) {
                    Object n = current.getData();
                    current = (nSetEntry)current.getNext();
                    findNext();
                    return n;
                }
            }

            public void remove() {
                throw new UnsupportedOperationException(
                    "remove is not supported on this iterator");
            }

        }
        public FilterSet(Object uid, Filter f) {
            ebh.setOrderMaintained(orderMaintained);
            synchronized (lock) {
                this.uid = uid;
                this.f = f;

                this.nextEntry = (nSetEntry)(NFLPriorityFifoSet.this.start == null
                    ? NFLPriorityFifoSet.this.head
                      : NFLPriorityFifoSet.this.start);
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
                        nextEntry = (nSetEntry)currentEntry.getNext();
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
                    se = (nSetEntry)se.getNext();
                }
    
                while (se != null && f != null &&
                      !f.matches(se.getData())) {
                      currentPriority = se.getPriority();
                      se = (nSetEntry)se.getNext();
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

        void removeItem(Object o)
        {
            assert Thread.holdsLock(lock);
            assert o != null;

            synchronized (lock) {
                if (nextEntry != null && nextEntry.getData() == o) {
                   nextEntry = (nSetEntry)nextEntry.getNext();
                }
                if (currentEntry != null && currentEntry.getData() == o) {
                    nextEntry = (nSetEntry)currentEntry.getNext();
                    currentEntry = null;
                }
            }
        }

        void addItem(Object o) {
            assert Thread.holdsLock(lock);

            nSetEntry pe = (nSetEntry)lookup.get(o);

            synchronized (lock) {

                if (currentEntry == null && nextEntry == null) {
                    nextEntry = pe;
                    currentPriority = pe.priority;
                } else if (pe.getPriority() == currentPriority
                    && ((currentEntry != null && !currentEntry.isValid()) ||
                       (nextEntry != null && !nextEntry.isValid()))) {
                    nextEntry = pe;
                    currentEntry = null;
                } else if (pe.getPriority() < currentPriority) {
                    currentPriority = pe.getPriority();
                    nextEntry = pe;
                    currentEntry = null;
                }
            }
        }
        void addItem(Object o, boolean toFront) {
            assert lock == null || Thread.holdsLock(lock);
            nSetEntry pe = (nSetEntry)lookup.get(o);
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

        public boolean add(Object o) 
        {
            return add(o, null);
        }

        public boolean add(int p, Object o) {
            return add(p, o, null);
        }

        public boolean add(Object o, Reason r) 
        {
            if (f != null && !f.matches(o)) {
                throw new IllegalArgumentException("not part of set");
            }
            return NFLPriorityFifoSet.this.add(o, r);
        }

        public boolean add(int p, Object o, Reason r) 
        {
            if (f != null && !f.matches(o)) {
                throw new IllegalArgumentException("not part of set");
            }
            return NFLPriorityFifoSet.this.add(p, o, r);
        }

        public void clear() {

            // OK .. we only want matching items
            // AND this will also clear parent list
            Set s = new HashSet();
            synchronized(lock) {
                Iterator itr = iterator();
                while (itr.hasNext()) {
                    s.add(itr.next());
                }
            }
            removeAll(s);
        }


        public boolean remove(Object o) {
            return remove(o, null);

        }
        public boolean remove(Object o, Reason r) {
            if (f != null && !f.matches(o)) {
                return false;
            }
            return NFLPriorityFifoSet.this.remove(o, r);
        }

        public boolean contains(Object o) {

            synchronized(lock) {
                nSetEntry pse = (nSetEntry)
                    lookup.get(o);
                if (pse == null) return false;

                if (f == null) return true;

                return f.matches(o);
            }
        }

        public int size() {
            // this is SLOW (we have to check each item
            synchronized(lock) {
                int cnt = 0;
                Iterator itr = iterator();
                while (itr.hasNext()) {
                    itr.next();
                    cnt ++;
                }
                return cnt;
            }
        }

        public boolean retainAll(Collection c) {
            Set s = new HashSet();
            synchronized(lock) {
                Iterator itr = NFLPriorityFifoSet.this.iterator();
                while (itr.hasNext()) {
                    Object o = itr.next();
                    if (!c.contains(o)) {
                        s.add(o);
                    }
                 }
            }
            return NFLPriorityFifoSet.this.removeAll(s);
        }

        public boolean isEmpty() {
            synchronized(lock) {
                boolean state = !skipToNext();
                return state;
            }
        }

        public boolean removeAll(Collection c) {
            return NFLPriorityFifoSet.this.removeAll(c);
        }

        public void destroy() {
            NFLPriorityFifoSet.this.destroyFilterSet(this.uid);
        }

        public Iterator iterator() {
            return new filterIterator();
        }

        public Object removeNext() {


            Object o =  null;
            NotifyInfo ni = null;
            synchronized(lock) {

                if (!skipToNext()) {
                    // removeNext failed
                    return null;
                }
                if (currentEntry == null) {
                    if (DEBUG && f == null && nextEntry == null && lookup.size() != 0) {
                         throw new RuntimeException("Corruption noticed in removeNext "
                              + " lookup.size is not 0 " + lookup);
                    }

                    return null;
                }
                o = currentEntry.getData();

                nextEntry = (nSetEntry)currentEntry.getNext();
                currentEntry = null;
                ni = internalRemove(o,null, null, hasListeners());

                if (DEBUG && f == null && currentEntry == null && nextEntry == null 
                    && lookup.size() != 0)

                {
                     throw new RuntimeException("Corruption noticed in removeNext "
                          + " lookup.size is not 0 " + lookup);
                }
            }
            // yes .. this is the wrong order .. bummer
            preRemoveNotify(o, null);
            if (ni != null) {
                postRemoveNotify(o,ni, null);
            }
            

            return o;
        }

        public Object peekNext() {
            synchronized(lock) {
                if (!skipToNext()) {
                    return null;
                }
                if (currentEntry == null) {
                    return null;
                }
                return currentEntry.getData();
           }
        }
         public Object addEventListener(EventListener listener,
                        EventType type, Object userData)
             throws UnsupportedOperationException 
         {
             if (type != EventType.EMPTY) {
                 throw new UnsupportedOperationException(
                   "Event " + type + " not supported");
             }
             return ebh.addEventListener(listener, type, userData);
         }
         public Object addEventListener(EventListener listener,
                         EventType type, Reason r, Object userData)
             throws UnsupportedOperationException 
         {
             if (type != EventType.EMPTY) {
                 throw new UnsupportedOperationException(
                   "Event " + type + " not supported");
             }
             return ebh.addEventListener(listener, type, r, userData);
        }
 
        public Object removeEventListener(Object id)
        {
            return ebh.removeEventListener(id);
        }

        
        public void notifyEmptyChanged(boolean empty, Reason r)
        {
           if (ebh.hasListeners(EventType.EMPTY)) {
                ebh.notifyChange(EventType.EMPTY, r, this,
                    (empty ? Boolean.TRUE : Boolean.FALSE),
                    (empty ? Boolean.FALSE : Boolean.TRUE));
           }
        }
    }

    // XXX - only generate empty notification for now

    public void addAllToFront(Collection c, int pri) {
        addAllToFront(c, pri, null);
    }

    public void addAllOrdered(Collection c) {
        addAllOrdered(c, null);
    }

    public void addAllOrdered(Collection c, Reason reason) {

        if (c.isEmpty()) 
            return;

        Set notify = null;
        boolean notifyTop = false;
        boolean wasEmpty = false;
        wasEmpty = isEmpty();

        Iterator itr = c.iterator();

        // we need this to determine the head

        while (itr.hasNext()) {
            SetEntry ientry = null;
            boolean found = false;
            int pri = 0;
            Object o = null;
            synchronized(lock) {
                o = itr.next();
                if (! (o instanceof Ordered))
                    throw new RuntimeException("Can not order unordered items");

                Ordered oo = (Ordered) o;


                QueuingOrder orderobj = (QueuingOrder)oo.getOrder();

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
                if (tail != null && orderobj.greaterThan((QueuingOrder)
                                ((Ordered)tail.getData()).getOrder())) {
                    // use normal add logic
                    ientry = null;
                }
                if (pri < (levels -2) && priorities[pri+1] != null) {
                    SetEntry back = priorities[pri+1].getPrevious();
                    if (back == null) {
                        ientry = null;
                    } else if (back.getData() == null) {
                        ientry = null;
                    } else if (orderobj.greaterThan((QueuingOrder)
                               ((Ordered)back.getData()).getOrder())) {
                        ientry = null;
                    }
                }
                // ok - we failed so we need to iterate
                while (!found && ientry != null) {
                   Object io = ientry.getData();
                   if (! (io instanceof Ordered)) {
                      throw new RuntimeException("Can not order unordered items");
                   }
                   Ordered so = (Ordered)io;
                   if (orderobj.greaterThan((QueuingOrder)so.getOrder())) {
                      if (ientry.getNext() == null) {
                          break;
                      }
                      ientry = ientry.getNext();
                      continue;
                   }
                   // we found a spot;
                   found = true;
                }

                //if found, insert before, else insert after
                if (ientry != null) {
                    //we are going to have to stick it in the middle
                    SetEntry e = createSetEntry(o, pri);
                    //Object obj = lookup.put(o,e);
                    lookup.put(o,e);
                    if (found) {
                        ientry.insertEntryBefore(e);
                        //e.insertEntryBefore(ientry);

                        if (ientry == priorities[pri]) {
                            //priorities[pri - 1] = e;
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

                    if (wasEmpty != isEmpty())  {
                        notifyTop = true;
                    }

                    // update any iterators             
                    SetEntry startOfList = head;
                      
                    if (startOfList != null && filterSets != null) {
                        Iterator fitr = filterSets.values().iterator();
                        while (fitr.hasNext()) {
                            FilterSet s = (FilterSet)fitr.next();
                            if (s == null) {
                                continue;
                            }
                            boolean wasFilterEmpty = s.isEmpty();
                            s.addItem(startOfList.getData(), true);
                            // if the filter is empy or the parent list was
                            // we have to notify
                            if (wasFilterEmpty || notifyTop) {
                                if (notify == null) {
                                    notify = new HashSet();
                                }
                                notify.add(s.getUID());
                            }
                        }
                        if (comparatorSets != null) {
                           //LKS - XXX 
                           // not dealing w/ comparator sets yet
                        }                   
                    }
                }

            } // end synchronization

            if (ientry == null) { // just use the normal logic
                 add(pri, o);
                 if (wasEmpty != isEmpty())  {
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
                SubSet s = null;
                Iterator nitr = notify.iterator();
                while (nitr.hasNext()) {
                    uid = nitr.next();
                    if (uid == null) continue;
                    synchronized(filterSetLock) {
                        s = (SubSet)filterSets.get(uid);
                    }
                    if (s == null) continue;
                    if (s instanceof FilterSet) {
                        ((FilterSet)s).notifyEmptyChanged(!(((FilterSet)s).isEmpty()), reason);
                    } else { //when supported, add comparatorSetLock similar as filterSetLock 
                        assert s instanceof ComparatorSet;
                        ((ComparatorSet)s).notifyEmptyChanged(!(((ComparatorSet)s).isEmpty()),reason);
                    }
                }
            }
        }
 
    }

    public boolean isEmpty() {
        return super.isEmpty();
    }


    public void addAllToFront(Collection c, int pri, Reason reason)
    {
        if (c.isEmpty()) 
            return;

        Set notify = null;
        boolean notifyTop = false;
        boolean wasEmpty = false;
        synchronized(lock) {
            wasEmpty = isEmpty();

            SetEntry startOfList = null;
            if (priorities[pri] == null) {
                // hey .. we just put it in the real place
                Iterator itr = c.iterator();
                while (itr.hasNext()) {
                    Object o = itr.next();
                    super.add(pri, o); 
                    if (startOfList == null) {
                        startOfList = (SetEntry)lookup.get(o);
                    }
                }
            } else {
                SetEntry endEntry = priorities[pri];
                Iterator itr = c.iterator();
                while (itr.hasNext()) {
                    Object o = itr.next();


                    // make sure we dont have a dup entry
                    // if it is, remove it so we replace it
                    SetEntry dup = null;
                    if ((dup = (SetEntry)lookup.get(o)) != null) {
                        remove(o);
                        if (endEntry == dup) endEntry =  null;
                        if (dup == startOfList) {
                            startOfList = priorities[pri];
                        }
                    }
                    if (endEntry == null) {
                        super.add(pri, o); 
                        if (startOfList == null) startOfList = (SetEntry)lookup.get(o);
                        continue;
                    }

                    // add the message @ the right priority

                    SetEntry e = createSetEntry(o, pri);
                    //Object obj = lookup.put(o,e);
                    lookup.put(o,e);
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

            if (wasEmpty != isEmpty())  {
                notifyTop = true;
            }

            // update any iterators             
                      
           if (filterSets != null) {
               Iterator fitr = filterSets.values().iterator();
               while (fitr.hasNext()) {
                        FilterSet s = (FilterSet)fitr.next();
                        if (s == null) continue;
                        boolean wasFilterEmpty = s.isEmpty();
                        s.addItem(startOfList.getData(), true);
                        //if the filter is empy or the parent list was
                        // we have to notify
                        if (wasFilterEmpty || notifyTop) {
                            if (notify == null)
                                notify = new HashSet();
                            notify.add(s.getUID());
                        }
                }
                if (comparatorSets != null) {
                    //LKS - XXX 
                    // not dealing w/ comparator sets yet
                    
                }                   
 
            }
        }

        if (notify != null || notifyTop) {

            
            if (notifyTop && hasListeners(EventType.EMPTY))
                notifyChange(EventType.EMPTY, Boolean.TRUE,
                    Boolean.FALSE, reason);

            if (notify != null) {
                Object uid = null;
                SubSet s = null;
                Iterator nitr = notify.iterator();
                while (nitr.hasNext()) {
                    uid = nitr.next();
                    if (uid == null) continue;
                    synchronized(filterSetLock) {
                        s = (SubSet)filterSets.get(uid);
                    }
                    if (s == null) continue;
                    if (s instanceof FilterSet) {
                        ((FilterSet)s).notifyEmptyChanged(!(((FilterSet)s).isEmpty()), reason);
                    } else { //when supported, add comparatorSetLock similar as filterSetLock
                        assert s instanceof ComparatorSet;
                        ((ComparatorSet)s).notifyEmptyChanged(!(((ComparatorSet)s).isEmpty()),reason);
                    }
                }
            }
        }

    }

    public boolean add(Object o, Reason r) {
        return add(defaultPriority, o, r);
    }

    public boolean add(int pri, Object o) {
        return add(pri, o, null);
    }

    private void preAdd(Object o, Reason reason) {
        // OK notify of changeRequest
        if (hasListeners(EventType.SET_CHANGED_REQUEST))
            notifyChange(EventType.SET_CHANGED_REQUEST, null,
                    o, reason);

        if (o == null ) {
            throw new NullPointerException("Unable to support null "
                      + " values");
        }
    }

    NotifyInfo internalAdd(int pri, Object o) {
        assert Thread.holdsLock(this);
        NotifyInfo ni = null;
        int oldsize =0;
        long oldbytes = 0;
        long objsize = 0;
        boolean added = false;
            if (maxByteCapacity != UNLIMITED_BYTES &&
                  !(o instanceof Sized)) 
            {
                throw new ClassCastException(
                   "Unable to add object not of"
                   + " type Sized when byteCapacity has been set");
            }
            if (maxBytePerObject != UNLIMITED_BYTES &&
                  !(o instanceof Sized)) 
            {
                throw new ClassCastException(
                   "Unable to add object not of"
                   + " type Sized when maxByteSize has been set");
            }

            if (enforceLimits && maxCapacity != UNLIMITED_CAPACITY &&
                ((maxCapacity -size()) <= 0)) {
                throw new OutOfLimitsException(
                      OutOfLimitsException.CAPACITY_EXCEEDED,
                      Integer.valueOf(size()),
                      Integer.valueOf(maxCapacity));
            }
    
            if (enforceLimits && maxByteCapacity != UNLIMITED_BYTES &&
                ((maxByteCapacity -bytes) <= 0)) {
                throw new OutOfLimitsException(
                      OutOfLimitsException.BYTE_CAPACITY_EXCEEDED,
                      Long.valueOf(bytes),
                      Long.valueOf(maxByteCapacity));
            }
    
            if (o instanceof Sized) {
                objsize = ((Sized)o).byteSize();
            }
    
            if (maxBytePerObject != UNLIMITED_BYTES && 
                objsize > maxBytePerObject) {
                throw new OutOfLimitsException(
                      OutOfLimitsException.ITEM_SIZE_EXCEEDED,
                      Long.valueOf(objsize),
                      Long.valueOf(maxByteCapacity));
            }
    
            oldsize = size();
            oldbytes = bytes;

            // OK -- add the actual data

            added = super.add(pri, o);

            // assign a sortable number
            // priority + long value
            // 
            if (o instanceof Ordered && ((Ordered)o).getOrder() == null) {
                QueuingOrder orderobj = new QueuingOrder();
                orderobj.priority = pri;
                orderobj.position = queuePosition;
                queuePosition ++;
                ((Ordered)o).setOrder(orderobj);
            }

            bytes +=objsize;

            averageCount = (((float)numberSamples*averageCount 
                      + (float)size())/((float)numberSamples+1.0F)); 
            averageBytes = ((double)numberSamples*averageBytes 
                      + (double)bytes)/((double)numberSamples+1.0D); 
            messageAverage = ((double)numberSamples*messageAverage 
                      + (double)objsize)/((double)numberSamples+1.0D); 
            numberSamples ++;

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

                if (hasListeners() || (filterSets != null &&
                    !filterSets.isEmpty()) ||
                    (comparatorSets != null && !comparatorSets.isEmpty()) ) {
                    ni = getNI();
                    ni.oldsize = oldsize;
                    ni.oldbytes = oldbytes;
                    ni.objsize = objsize;
                    ni.newbytes = oldbytes + objsize;
                    ni.newsize = size();
                    ni.curMaxCapacity = maxCapacity;
                    ni.curMaxBytesCapacity = maxByteCapacity;

                    int cnt = 0;

                    synchronized(lock) {
    
                        if (filterSets != null) {
                            Iterator itr = filterSets.values().iterator();
                            while (itr.hasNext()) {
                                FilterSet s = (FilterSet)itr.next();
                                if (s == null) continue;
                                boolean wasEmpty = s.isEmpty();
                                s.addItem(o);
                                if (wasEmpty != s.isEmpty() ) {
                                    if (ni.filters[cnt] == null) {
                                        ni.filters[cnt] = new EmptyChanged();
                                    }
                                    ni.filters[cnt].f = s;
                                    ni.filters[cnt].isEmpty = !wasEmpty;
                                    cnt ++;
                                }
                            }
                        }
                        if (comparatorSets != null) {
                            Iterator itr = comparatorSets.values().iterator();
                            while (itr.hasNext()) {
                                ComparatorSet s = (ComparatorSet)itr.next();
                                if (s==null) continue;
                                boolean wasEmpty = s.isEmpty();
                                s.addItem(o);
                                if (wasEmpty != s.isEmpty() ) {
                                    if (ni.filters[cnt] == null) {
                                        ni.filters[cnt] = new EmptyChanged();
                                    }
                                    ni.filters[cnt].f = s;
                                    ni.filters[cnt].isEmpty = !wasEmpty;
                                    cnt ++;
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
        if (hasListeners(EventType.SIZE_CHANGED) &&
                 ni.oldsize != ni.newsize) {
            notifyChange(EventType.SIZE_CHANGED, 
                    Integer.valueOf(ni.oldsize),
                    Integer.valueOf(ni.newsize),
                    reason);
        }
        if (hasListeners(EventType.BYTES_CHANGED) &&
                ni.oldbytes != ni.newbytes) {
            notifyChange(EventType.BYTES_CHANGED, 
                Long.valueOf(ni.oldbytes),
                Long.valueOf(ni.newbytes),
                reason);
        }
        if (hasListeners(EventType.SET_CHANGED)) {
            notifyChange(EventType.SET_CHANGED, null,
                   o, reason);
        }
 

        if  (ni.oldsize == 0 && ni.newsize != 0 && 
                 hasListeners(EventType.EMPTY)) {
            notifyChange(EventType.EMPTY, Boolean.TRUE,
                    Boolean.FALSE, reason);
        }

        int curMaxCapacity = 0;
        long curMaxBytesCapacity = 0;
        if ( hasListeners(EventType.FULL) &&
             (ni.curMaxBytesCapacity != UNLIMITED_BYTES &&
              ((ni.curMaxBytesCapacity -ni.newbytes) <= 0)) 
             || (ni.curMaxCapacity != UNLIMITED_BYTES &&
            ((ni.curMaxCapacity -ni.newsize) <= 0)))
        {
            notifyChange(EventType.FULL, Boolean.FALSE,
                    Boolean.TRUE, reason);
        }
        for (int i=0; i < ni.filters.length; i ++) {
            if (ni.filters[i] == null || ni.filters[i].f  == null) break;
            SubSet s = ni.filters[i].f;
            if (s instanceof FilterSet) {
                ((FilterSet)s).notifyEmptyChanged(ni.filters[i].isEmpty, reason);
            } else {
                assert s instanceof ComparatorSet;
                ((ComparatorSet)s).notifyEmptyChanged(ni.filters[i].isEmpty,reason);
            }
        }
        putNI(ni);
    }


    public boolean add(int pri, Object o, Reason reason) 
    {
        NotifyInfo ni = null;
        preAdd(o, reason);
        synchronized(lock) {
            ni = internalAdd(pri, o);
        }
        if (ni != null) {
            postAdd(o, ni, reason);
        }
        return ni != null;
    }

    public boolean removeAll(Collection c, Reason r) {
        boolean removed = false;
        Iterator itr = c.iterator();
        while (itr.hasNext()) {
            removed |= remove(itr.next(), r);
        }
        return removed;
    }

    public boolean remove(Object o) {
        return remove(o, (Reason)null);
    }

    static class NotifyInfo
    {
        long oldbytes = 0;
        long newbytes = 0;
        int oldsize =0;
        int newsize = 0;
        long objsize = 0;
        int curMaxCapacity = 0;
        long curMaxBytesCapacity = 0;
        EmptyChanged filters[] = null;

        public NotifyInfo() {
            filters = new EmptyChanged[0];
        }

        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append("NotifyInfo:");
            for (int i = 0; i < filters.length; i++) {
               str.append("["+i+"]"+filters[i]);
            }
            return str.toString();
        }
    }        

    static class EmptyChanged
    {
        SubSet f;
        boolean isEmpty = false;

        public String toString() {
            return "EmptyChanged:isEmpty="+isEmpty+"f={"+(f == null ? "null":f.toString())+"}";
        }
    }

    NotifyInfo getNI() {
        NotifyInfo ni = null;
        synchronized(lock) {
            
            if (gni.isEmpty()) {
                ni= new NotifyInfo();
            } else {
                Iterator itr = gni.iterator();
                ni = (NotifyInfo)itr.next();
                itr.remove();
            }
            int size = (filterSets == null ? 0 : filterSets.size());
            if (ni.filters == null || ni.filters.length < size ) {
                ni.filters = new EmptyChanged[size];
            }
        }
        return ni;
    }

    void putNI(NotifyInfo ni) {
        if (ni == null) return;
        synchronized (lock) {
            gni.add(ni);
        }
    }

    NotifyInfo internalRemove(Object o, Reason r, Iterator pitr, boolean hasListeners) {
        synchronized(lock) {
            assert Thread.holdsLock(lock);
            long objsize = 0;
            int oldsize = size();
            long oldbytes = bytes;
            if (o instanceof Sized) {
                objsize = ((Sized)o).byteSize();
            }
            nSetEntry nse = (nSetEntry)lookup.get(o);
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
            if (!removed)  {
                return null;
            }
            if (!hasListeners() && (filterSets == null ||
                 filterSets.isEmpty()) &&
                 (comparatorSets == null || comparatorSets.isEmpty()))  {
                return null;
            }
            NotifyInfo ni = getNI();
            ni.oldbytes = oldbytes;
            ni.oldsize = oldsize;
            ni.objsize = objsize;
            ni.newsize =size();
            ni.newbytes = bytes;
            ni.curMaxCapacity = maxCapacity;
            ni.curMaxBytesCapacity = maxByteCapacity;

            int cnt =0;
            if (filterSets != null) {
                Iterator itr = filterSets.values().iterator();
                while (itr.hasNext()) {
                    FilterSet s = (FilterSet)itr.next();
                    if (s == null) continue;
                    boolean empty = s.isEmpty();
                    s.removeItem(o);
                    if (empty != s.isEmpty()) {
                        if (ni.filters[cnt] == null) {
                            ni.filters[cnt] = new EmptyChanged();
                        }
                        ni.filters[cnt].f = s;
                        ni.filters[cnt].isEmpty = empty;
                        cnt ++;
                    }
                }
            }
            if (comparatorSets != null) {
                Iterator itr = comparatorSets.values().iterator();
                while (itr.hasNext()) {
                    ComparatorSet s = (ComparatorSet)itr.next();
                    if (s==null) continue;
                    boolean empty = s.isEmpty();
                    s.removeItem(o);
                    if (empty != s.isEmpty()) {
                        if (ni.filters[cnt] == null) {
                            ni.filters[cnt] = new EmptyChanged();
                        }
                        ni.filters[cnt].f = s;
                        ni.filters[cnt].isEmpty = !empty;
                        cnt ++;
                    }
                }
            }
            if (cnt < ni.filters.length && ni.filters[cnt] != null) {
                ni.filters[cnt].f = null;
            }
            if (pitr == null)
                nse.clear();
            return ni;
        }
    }
     

    public boolean remove(Object o, Reason r) {
        if (o == null) {
            return false;
        }
        synchronized (lock) {
            if (!contains(o))
                return false;
        }
        preRemoveNotify(o, r);
        NotifyInfo ni = internalRemove(o,r, null, hasListeners());
        if (ni != null) {
            postRemoveNotify(o,ni, r);
        }
        synchronized (lock) {
            return !contains(o);
        }
    }

    public Object removeNext() {
        return removeNext(null);
    }

    public Object removeNext(Reason r) {
        Object o = null;
        NotifyInfo ni = null;
        synchronized(lock) {
            o = first();
            if (o == null) {
                return null;
            }
            ni = internalRemove(o,r, null, hasListeners());
        }
        preRemoveNotify(o, r);
        if (ni != null) {
            postRemoveNotify(o,ni, r);
        }
        return o;
    }

    public Object peekNext() {
        synchronized(lock) {
            return first();
        }
    }

    class wrapIterator implements Iterator
    {
        Iterator parentIterator;
        Object next = null;

        public wrapIterator(Iterator itr) {
            synchronized(lock) {
                parentIterator = itr;
            }
        }
        public boolean hasNext() {
            synchronized(lock) {
                return parentIterator.hasNext();
            }
        }
        public Object next() {
            synchronized(lock) {
                next = parentIterator.next();
                return next;
            }
        }
        public void remove() {
            preRemoveNotify(next, null);
            NotifyInfo ni = internalRemove(next,null, parentIterator, hasListeners());
            if (ni != null) {
                postRemoveNotify(next,ni, null);
            }
            next = null;
        }
    }

    public Iterator iterator() {
        synchronized (lock) {
            return new wrapIterator(super.iterator());
        }
    }



    public SubSet subSet(Filter f) {
        synchronized(lock) {
            Object uid = new Object();
            FilterSet fs = new FilterSet(uid, f);
            if (filterSets == null) {
                filterSets = new WeakValueHashMap("FilterSet");
            }
            synchronized(filterSetLock) {
                filterSets.put(uid, fs);
            }
            return fs;
        }
    }

    public SubSet subSet(Comparator c) {
        synchronized(lock) {
            Object uid = new Object();
            ComparatorSet cs = new ComparatorSet(uid, c, this);
            if (comparatorSets == null) {
                comparatorSets = new WeakValueHashMap("ComparatorSet");
            }
            comparatorSets.put(uid, cs);
            return cs;
        }
    }

    public Set getAll(Filter f) {
        synchronized(lock) {
            Set s = new LinkedHashSet();
            Iterator itr = iterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                if (f == null || f.matches(o)) {
                    s.add(o);
                }
            }
            return s;
        }
    }


    private void destroyFilterSet(Object uid)
    {
        assert filterSets != null;
        synchronized(lock) {
            if (filterSets != null) {
                synchronized(filterSetLock) {
                    filterSets.remove(uid);
                }
            }
        }

    }

    private void destroyComparatorSet(Object uid)
    {
        assert comparatorSets != null;
        synchronized(lock) {
            if (comparatorSets != null) {
                comparatorSets.remove(uid);
            }
        }
    }


    public void destroy() {
        // clean up for gc
        ebh.clear();
        super.clear();
        synchronized(lock) {
            if (filterSets != null) {
                synchronized(filterSetLock) {
                    filterSets.clear();
                }
            }
            if (comparatorSets != null) {
                comparatorSets.clear();
            }
            gni.clear();
        }
    }

    protected SetEntry createSetEntry(Object o, int p) {
        return new nSetEntry(o,p);
    }

    long currentID = 1;

    class nSetEntry extends PrioritySetEntry
    {
        long uid = 0;

        public nSetEntry(Object o, int p) {
            super(o,p);
            synchronized(lock) {
                uid = currentID ++;
            }
        }
        public long getUID() {
            return uid;
        }

        public boolean remove() {
            assert Thread.holdsLock(lock);
            // we are always locked (in lock mode)
            // when this is called
            Object o = getData();
            assert isValid();
            valid = false;
            boolean ok = super.remove();

            // update bytes 
            if (o instanceof Sized) {
                long removedBytes = ((Sized)o).byteSize();
                bytes -= removedBytes;
            }
            // update averages
            averageCount = (((float)numberSamples*averageCount 
                      + (float)size())/((float)numberSamples+1.0F)); 
            averageBytes = ((double)numberSamples*averageBytes 
                      + (double)bytes)/((double)numberSamples+1.0D); 
            numberSamples ++;

            return ok;
        }
    }
        


    public synchronized String toDebugString() {
        StringBuffer str = new StringBuffer();
        str.append("NFLPriorityFifoSet: " + "\n");
        if (filterSets != null) {
            str.append("\tfilterSets: " + filterSets.size() + "\n");
            Iterator fitr = filterSets.values().iterator();
            while (fitr.hasNext()) {
                FilterSet fs = (FilterSet)fitr.next();
                if (fs == null) continue;
                str.append("\t\tFilterSet " + fs.hashCode() + " filter["
                           + fs.f + "]\n");
            }
        }
        if (comparatorSets != null) {
            str.append("\tComparatorSets: " + comparatorSets.size() + "\n");
            Iterator fitr = comparatorSets.values().iterator();
            while (fitr.hasNext()) {
                ComparatorSet fs = (ComparatorSet)fitr.next();
                if (fs==null) continue;
                str.append("\t\tComparatorSet " + fs.hashCode() + " filter["
                         + fs.comparator() + "]\n");
            }
        }
        str.append("\t"+ebh.toString());
        str.append("\n\nSUBCLASS INFO\n");
        str.append(super.toDebugString());
        return str.toString();
    }

    protected void preRemoveNotify(Object o, Reason reason) {
        if (hasListeners(EventType.SET_CHANGED_REQUEST))
            notifyChange(EventType.SET_CHANGED_REQUEST, o,
                       null, reason);
    }

    protected void postRemoveNotify(Object o, NotifyInfo ni, Reason reason) 
    {
        if (!hasListeners()) {
            return;
        }

        // first  notify SIZE changed
        if (ni.oldsize != ni.newsize &&
               hasListeners(EventType.SIZE_CHANGED)) {
            notifyChange(EventType.SIZE_CHANGED, 
                Integer.valueOf(ni.oldsize),
                Integer.valueOf(ni.newsize),
                reason);
        }
        if (ni.newbytes != ni.oldbytes &&
               hasListeners(EventType.BYTES_CHANGED)) {
            notifyChange(EventType.BYTES_CHANGED, 
                Long.valueOf(ni.oldbytes),
                Long.valueOf(ni.newbytes),
                reason);
        }
        if (hasListeners(EventType.SET_CHANGED))
            notifyChange(EventType.SET_CHANGED, o,
                   null, reason);

        if (ni.oldsize != 0 && ni.newsize == 0 &&
              hasListeners(EventType.EMPTY)) {
            notifyChange(EventType.EMPTY, Boolean.FALSE,
                    Boolean.TRUE, reason);
        }
        if (hasListeners(EventType.FULL) &&
            (ni.curMaxBytesCapacity != UNLIMITED_BYTES 
              && ((ni.curMaxBytesCapacity - ni.oldbytes) <= 0)
              && ((ni.curMaxBytesCapacity - ni.newbytes) > 0)) 
           || (ni.curMaxCapacity != UNLIMITED_CAPACITY 
              && ((ni.curMaxCapacity -ni.oldsize) <= 0)
              && ((ni.curMaxCapacity -ni.newsize) > 0)))
        {
              // not full
            notifyChange(EventType.FULL, Boolean.TRUE,
                    Boolean.FALSE, reason);
        }
        for (int i=0; i < ni.filters.length; i ++) {
            if (ni.filters[i] == null || ni.filters[i].f  == null) break;
            SubSet s = ni.filters[i].f;
            if (s instanceof FilterSet) {
                ((FilterSet)s).notifyEmptyChanged(ni.filters[i].isEmpty,reason);
            } else {
                assert s instanceof ComparatorSet;
                ((ComparatorSet)s).notifyEmptyChanged(
                       ni.filters[i].isEmpty,reason);
            }
        }
        putNI(ni);
    }

    /**
     * Maximum number of messages stored in this
     * list at any time since its creation.
     *
     * @return the highest number of messages this set
     * has held since it was created.
     */
    public int highWaterCount() {
        return highWaterCnt;
    }

    /**
     * Maximum number of bytes stored in this
     * list at any time since its creation.
     *
     * @return the largest size (in bytes) of
     *  the objects in this list since it was
     *  created.
     */
    public long highWaterBytes() {
        synchronized(lock) {
            return highWaterBytes;
        }
    }

    /**
     * The largest message (which implements Sized)
     * which has ever been stored in this list.
     *
     * @return the number of bytes of the largest
     *  message ever stored on this list.
     */
    public long highWaterLargestMessageBytes() {
        synchronized(lock) {
            return largestMessageHighWater;
        }
    }

    /**
     * Average number of messages stored in this
     * list at any time since its creation.
     *
     * @return the average number of messages this set
     * has held since it was created.
     */
    public float averageCount() {
        return averageCount;
    }

    /**
     * Average number of bytes stored in this
     * list at any time since its creation.
     *
     * @return the largest size (in bytes) of
     *  the objects in this list since it was
     *  created.
     */
    public double averageBytes() {
        synchronized(lock) {
            return averageBytes;
        }
    }

    /**
     * The average message size (which implements Sized)
     * of messages which has been stored in this list.
     *
     * @return the number of bytes of the average
     *  message stored on this list.
     */
    public double averageMessageBytes() {
        synchronized(lock) {
            return messageAverage;
        }
    }



    /** 
     * sets the maximum size of an entry allowed
     * to be added to the collection
     * @param bytes maximum number of bytes for
     *        an object added to the list or
     *        UNLIMITED_BYTES if there is no limit
     */   
    public void setMaxByteSize(long bytes) {
        if (bytes < UNLIMITED_BYTES) {
            bytes = UNLIMITED_BYTES;
        }
        synchronized(lock) {
            maxBytePerObject = bytes;
        }
    }
 
    /** 
     * returns the maximum size of an entry allowed
     * to be added to the collection
     * @return maximum number of bytes for an object
     *        added to the list  or
     *        UNLIMITED_BYTES if there is no limit
     */   
    public long maxByteSize() {
        synchronized(lock) {
            return maxBytePerObject;
        }
    }
 

    /**
     * Sets the capacity (size limit).
     *
     * @param cnt the capacity for this set (or
     *         UNLIMITED_CAPACITY if unlimited).
     */
    public void setCapacity(int cnt) {
        if (cnt < UNLIMITED_CAPACITY) {
            cnt = UNLIMITED_CAPACITY;
        }
        boolean nowFull = false;
        boolean nowNotFull = false;
        synchronized(lock) {
            nowFull = (!isFull() 
                   && cnt != UNLIMITED_CAPACITY 
                   && cnt <= size());
            nowNotFull = isFull() &&
                 (cnt == UNLIMITED_CAPACITY ||
                  cnt > size());

            maxCapacity = cnt;
        }
        if (nowFull) {
            notifyChange(EventType.FULL, Boolean.FALSE,
                Boolean.TRUE, null);
        } else if (nowNotFull) {
            notifyChange(EventType.FULL, Boolean.TRUE,
                Boolean.FALSE, null);
        }
    }

    /**
     * Sets the byte capacity. Once the byte capacity
     * is set, only objects which implement Sized
     * can be added to the class
     *
     * @param size the byte capacity for this set (or
     *         UNLIMITED_BYTES if unlimited).
     */
    public void setByteCapacity(long size) {
        boolean nowFull = false;
        boolean nowNotFull = false;
        if (size < UNLIMITED_BYTES) {
            size = UNLIMITED_BYTES;
        } 

        synchronized(lock) {
            nowFull = (!isFull() 
               && size != UNLIMITED_BYTES 
               && size <= byteSize());
            nowNotFull = isFull() &&
                 (size == UNLIMITED_CAPACITY ||
                  size > byteSize());
            maxByteCapacity = size;
        }
 
        if (nowFull) {
            notifyChange(EventType.FULL, Boolean.FALSE,
                    Boolean.TRUE, null);
        } else if (nowNotFull) {
            notifyChange(EventType.FULL, Boolean.TRUE,
                    Boolean.FALSE, null);
        }

    }

    /**
     * Returns the capacity (count limit) or UNLIMITED_CAPACITY
     * if its not set.
     *
     * @return the capacity of the list
     */
    public int capacity() {
        return maxCapacity;
    }

    /**
     * Returns the byte capacity or UNLIMITED_CAPACITY
     * if its not set.
     *
     * @return the capacity of the list
     */
    public long byteCapacity() {
        synchronized(lock) {
            return maxByteCapacity;
        }
    }



    /**
     * Returns <tt>true</tt> if either the bytes limit
     *         or the count limit is set and
     *         has been reached or exceeded.
     *
     * @return <tt>true</tt> if the count limit is set and
     *         has been reached or exceeded.
     * @see #freeSpace
     * @see #freeBytes
     */
    public boolean isFull() {
        synchronized(lock) {
            return freeSpace()==0 || freeBytes() == 0;
        }
    }


    /**
     * Returns number of entries remaining in the
     *         lists to reach full capacity or
     *         UNLIMITED_CAPACITY if the capacity
     *         has not been set
     *
     * @return the amount of free space
     */
    public int freeSpace() {
        synchronized(lock) {
            if (maxCapacity == UNLIMITED_CAPACITY)
                return UNLIMITED_CAPACITY;
                
            int sz = maxCapacity - size();
            if (sz < 0) {
                return 0;
            }
            return sz;
        }
    }

    /**
     * Returns the number of bytesremaining in the
     *         lists to reach full capacity, 0
     *         if the list is greater than the 
     *         capacity  or UNLIMITED_BYTES if 
     *         the capacity has not been set
     *
     * @return the amount of free space
     */
    public long freeBytes() {
        synchronized(lock) {
            if (maxByteCapacity == UNLIMITED_BYTES)
                return UNLIMITED_BYTES;
               
            long retval = maxByteCapacity - bytes;
            if (retval < 0) {
                return 0;
            }
            return retval;
        }
    }

    /**
     * Returns the number of bytes used by all entries in this 
     * set which implement Sized.  If this
     * set contains more than <tt>Long.MAX_VALUE</tt> elements, returns
     * <tt>Long.MAX_VALUE</tt>.
     *
     * @return the total bytes of data from all objects implementing
     *         Sized in this set.
     * @see Sized
     * @see #size
     */
    public long byteSize() {
        synchronized(lock) {
            return bytes;
        }
    }

   // ----------------------------------------------------
   //   Notification Events
   // ----------------------------------------------------

    /**
     * Request notification when the specific event occurs.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data sent with any notifications
     * @return an id associated with this notification
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Object user_data) 
    {
        return ebh.addEventListener(listener, type, 
                       user_data);
    }

    /**
     * Request notification when the specific event occurs AND
     * the reason matched the passed in reason.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data sent with any notifications
     * @param reason reason which must be associated with the
     *               event (or null for all events)
     * @return an id associated with this notification
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Reason reason,
                        Object userData)
    {
        return ebh.addEventListener(listener, type, 
                       reason, userData);
    }

    /**
     * remove the listener registered with the passed in
     * id.
     * @return the listener which was removed
     */
    public Object removeEventListener(Object id)
    {
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


    public void sort(Comparator c) {
        super.sort(c);
        // reset subsets
        if (filterSets != null) {
            Iterator fitr = filterSets.values().iterator();
            while (fitr.hasNext()) {
                FilterSet s = (FilterSet)fitr.next();
                if (s == null) continue;
                s.resetFilterSet((nSetEntry)head);
            }
        }
    }

}

