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
 * @(#)FifoSet.java	1.22 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.*;


/**
 * This is a First In-First Out set which implements the
 * SortedSet interface.
 */

public class FifoSet extends AbstractSet implements SortedSet
{

    // linked list entries for the list
    protected SetEntry head = null;
    protected SetEntry tail = null;
    protected Map lookup = null;

    // interfaces used for first/last subLists
    protected FifoSet parent = null;
    protected SetEntry start = null;
    protected SetEntry end = null;

    protected Object lock = null;

    protected void setLock(Object lock) {
        this.lock = lock;
    }

    public String toString() {
        return this.getClass().toString() /*+ " : " + this.hashCode()*/;
    }

    public boolean isEmpty() {
        return lookup.isEmpty();
    }

    public int size() {
        //assert lock == null || Thread.holdsLock(lock) : "lock is " + lock.toString();
        if (parent == null) {
            return lookup.size();
        }
        if (start == null && end == null) {
            return 0;
        }
        int cnt = 0;
        Iterator itr = iterator();
        while (itr.hasNext())
        {
            itr.next();
            cnt ++;
         }
         return cnt;
    }

    public boolean contains(Object o) {
        assert lock == null || Thread.holdsLock(lock);
        SetEntry se = (SetEntry)lookup.get(o);
        boolean has = se != null && se.isValid();
        return has;
    }

    public FifoSet() {
        lookup = new HashMap();
        this.head = null;
        this.tail = null;

        this.start = null;
        this.end = null;

        parent = null;
        lookup = new HashMap();
    }

    private FifoSet(FifoSet parent, SetEntry start, SetEntry end)
    {
        this.parent = parent;
        this.lookup = parent.lookup;
        this.start = start;
        this.end = end;
    }

    class SetIterator implements Iterator {
        SetEntry last_entry = null;  // exclusive
        SetEntry first_entry = null; // inclusive
        boolean initialPass = true;
        SetEntry current = null;

        public SetIterator(SetEntry initialEntry, SetEntry finalEntry) {
            assert lock == null || Thread.holdsLock(lock) : " lock is " + lock
               + ":" + Thread.holdsLock(lock);
            this.first_entry = initialEntry;
            this.last_entry = finalEntry;
            this.current = null;
            initialPass = true;
        }
        public boolean hasNext() {
            assert lock == null || Thread.holdsLock(lock);
            if (current == null && !initialPass) {
                return false;
            }
            SetEntry nextEntry = null;
            if (initialPass) {
                nextEntry = first_entry;
            } else {
                nextEntry = current.getNext();
            }
            while (nextEntry != null && !nextEntry.isValid() &&
                nextEntry != last_entry ) {
                nextEntry = nextEntry.getNext();
            }
            return nextEntry != null && nextEntry != last_entry
                   && nextEntry.isValid();
        }

        public Object next() {
            assert lock == null || Thread.holdsLock(lock);
            if (current == null && initialPass) {
                current = first_entry;
                initialPass = false;
            } else {
                current = current.getNext();
            }

            if (current == null) {
                throw new NoSuchElementException("set empty");
            }
            while (current != null && !current.isValid() &&
                current != last_entry ) {
                current = current.getNext();
            }
            if (current == null || !current.isValid() 
                   || current == last_entry) 
            {
                throw new NoSuchElementException("set empty");
            }
            return current.getData();
        }
        public void remove() {
            if (current != null) {
                cleanupEntry(current);
            }
             
        }
    }


    protected boolean cleanupEntry(SetEntry e) {

        assert lookup.size() != 0 || (head == null && tail == null);
        assert (head != null && tail != null) ||
               (head == null && tail == null) :
                 "values: " + head + "," + tail ;

        if (e == null)
            return false;
        boolean retval =  e.isValid();
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
        assert (head != null && tail != null) ||
               (head == null && tail == null) : 
                 "values: " +
                 "\n\thead: " +  head + 
                 "\n\ttail: " + tail +
                 "\n\toldhead: " + oldhead +
                 "\n\toldtail: " + oldtail +
                  "\n\tentry removed:" + e + "\n\n" ;

        return retval;
    }
    
    public Iterator iterator() {
        assert lock == null || Thread.holdsLock(lock) : " lock is " + lock
               + ":" + Thread.holdsLock(lock);
        SetEntry beginEntry = (parent != null ?
                  (start == null ? parent.head : start)
               :  head);
        return new SetIterator(beginEntry, end);
    }


    /**
     * Returns the comparator associated with this sorted set, or
     * <tt>null</tt> if it uses its elements' natural ordering.
     *
     * @return the comparator associated with this sorted set, or
     * 	       <tt>null</tt> if it uses its elements' natural ordering.
     */
    public Comparator comparator() {
        return null;
    }

    /**
     * Returns a view of the portion of this sorted set whose elements range
     * from <tt>fromElement</tt>, inclusive, to <tt>toElement</tt>, exclusive.
     * @throws ClassCastException if <tt>fromElement</tt> and
     *         <tt>toElement</tt> cannot be compared to one another using this
     *         set's comparator (or, if the set has no comparator, using
     *         natural ordering).  Implementations may, but are not required
     *	       to, throw this exception if <tt>fromElement</tt> or
     *         <tt>toElement</tt> cannot be compared to elements currently in
     *         the set.
     * @throws IllegalArgumentException if <tt>fromElement</tt> is greater than
     *         <tt>toElement</tt>; or if this set is itself a subSet, headSet,
     *         or tailSet, and <tt>fromElement</tt> or <tt>toElement</tt> are
     *         not within the specified range of the subSet, headSet, or
     *         tailSet.
     * @throws NullPointerException if <tt>fromElement</tt> or
     *	       <tt>toElement</tt> is <tt>null</tt> and this sorted set does
     *	       not tolerate <tt>null</tt> elements.
     */
    public SortedSet subSet(Object fromElement, Object toElement) {
        assert lock == null || Thread.holdsLock(lock);
        if (parent != null) {
           boolean foundFrom = fromElement == null;
           boolean foundTo = toElement == null;
           Iterator itr = iterator();
           while (itr.hasNext()) {
               Object o = itr.next();
               if (!foundFrom && 
                  (o == fromElement || o.equals(fromElement))) {
                  foundFrom = true;
               }
               if (!foundTo && 
                  (o == toElement || o.equals(toElement))) {
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
        SetEntry st = null;
        if (fromElement != null) {
            st = (SetEntry)lookup.get(fromElement);
        }
        SetEntry end = null;
        if (toElement != null) {
            end = (SetEntry)lookup.get(toElement);
        }
        return new FifoSet(this, st, end);
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are
     * strictly less than <tt>toElement</tt>.  The returned sorted set is
     * backed by this sorted set, so changes in the returned sorted set are
     * reflected in this sorted set, and vice-versa.  The returned sorted set
     * supports all optional set operations.<p>
     *
     * The sorted set returned by this method will throw an
     * <tt>IllegalArgumentException</tt> if the user attempts to insert a
     * element outside the specified range.<p>
     *
     * Note: this method always returns a view that does not contain its
     * (high) endpoint.  If you need a view that does contain this endpoint,
     * and the element type allows for calculation of the successor a given
     * value, merely request a headSet bounded by
     * <tt>successor(highEndpoint)</tt>.  For example, suppose that <tt>s</tt>
     * is a sorted set of strings.  The following idiom obtains a view
     * containing all of the strings in <tt>s</tt> that are less than or equal
     * to <tt>high</tt>:
     * 	    <pre>    SortedSet head = s.headSet(high+"\0");</pre>
     *
     * @param toElement high endpoint (exclusive) of the headSet.
     * @return a view of the specified initial range of this sorted set.
     * @throws ClassCastException if <tt>toElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>toElement</tt> does not implement <tt>Comparable</tt>).
     *         Implementations may, but are not required to, throw this
     *	       exception if <tt>toElement</tt> cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if <tt>toElement</tt> is <tt>null</tt> and
     *	       this sorted set does not tolerate <tt>null</tt> elements.
     * @throws IllegalArgumentException if this set is itself a subSet,
     *         headSet, or tailSet, and <tt>toElement</tt> is not within the
     *         specified range of the subSet, headSet, or tailSet.
     */
    public SortedSet headSet(Object toElement) {
        return subSet(null, toElement);
    }

    /**
     * Returns a view of the portion of this sorted set whose elements are
     * greater than or equal to <tt>fromElement</tt>.  The returned sorted set
     * is backed by this sorted set, so changes in the returned sorted set are
     * reflected in this sorted set, and vice-versa.  The returned sorted set
     * supports all optional set operations.<p>
     *
     * The sorted set returned by this method will throw an
     * <tt>IllegalArgumentException</tt> if the user attempts to insert a
     * element outside the specified range.<p>
     *
     * Note: this method always returns a view that contains its (low)
     * endpoint.  If you need a view that does not contain this endpoint, and
     * the element type allows for calculation of the successor a given value,
     * merely request a tailSet bounded by <tt>successor(lowEndpoint)</tt>.
     * For example, suppose that <tt>s</tt> is a sorted set of strings.  The
     * following idiom obtains a view containing all of the strings in
     * <tt>s</tt> that are strictly greater than <tt>low</tt>:
     * 
     * 	    <pre>    SortedSet tail = s.tailSet(low+"\0");</pre>
     *
     * @param fromElement low endpoint (inclusive) of the tailSet.
     * @return a view of the specified final range of this sorted set.
     * @throws ClassCastException if <tt>fromElement</tt> is not compatible
     *         with this set's comparator (or, if the set has no comparator,
     *         if <tt>fromElement</tt> does not implement <tt>Comparable</tt>).
     *         Implementations may, but are not required to, throw this
     *	       exception if <tt>fromElement</tt> cannot be compared to elements
     *         currently in the set.
     * @throws NullPointerException if <tt>fromElement</tt> is <tt>null</tt>
     *	       and this sorted set does not tolerate <tt>null</tt> elements.
     * @throws IllegalArgumentException if this set is itself a subSet,
     *         headSet, or tailSet, and <tt>fromElement</tt> is not within the
     *         specified range of the subSet, headSet, or tailSet.
     */
    public SortedSet tailSet(Object fromElement) {
        return subSet(fromElement, null);
    }

    public void clear() {
        assert lock == null || Thread.holdsLock(lock);

        Iterator itr = iterator();
        while (itr.hasNext()) {
            //Object o = itr.next();
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
     * @throws    NoSuchElementException sorted set is empty.
     */
    public Object first() {
        assert lock == null || Thread.holdsLock(lock);
        SetEntry beginEntry = (parent != null ?
                  (start == null ? parent.head : start)
               :  head);

        Object o = (beginEntry == null ? null : beginEntry.getData());
        // LKS - XXX workaround for corruption until I find a cure
        if ((beginEntry != null && lookup.get(o) == null) ||
            (beginEntry == null && !lookup.isEmpty())) {
            assert false : "List corrupted: " +
               "\n\t beginEntry: " + (beginEntry == null ? "null":beginEntry.toString()) +
               "\n\t parent : " + parent +
               "\n\t head " + head +
               "\n\t start " + start +
               "\n\t lookup " + lookup.toString() ;
 
/* LKS
            SetEntry se = beginEntry.getNext();
            if (head == beginEntry) head = se;
            else if (start == beginEntry) start = se;
            else if (parent != null && parent.head == beginEntry) parent.head = se;
            return first();
*/
        }
        return o;
    }

    /**
     * Returns the last (highest) element currently in this sorted set.
     *
     * @return the last (highest) element currently in this sorted set.
     * @throws    NoSuchElementException sorted set is empty.
     */
    public Object last() {
        assert lock == null || Thread.holdsLock(lock);
        if (parent != null) {
            if (end != null) return end.getPrevious().getData();
            return parent.last();
        }
        return (tail == null ? null : tail.getData());
    }

    public boolean add(Object o) {
        assert lock == null || Thread.holdsLock(lock);

        if (parent != null) {
            if (end != null) {
               throw new IllegalArgumentException(
                   "Object added is past end of subset");
            }
            return parent.add(o);
        }

        // replace previous entries
        if (lookup.get(o) != null) {
            remove(o);
         }
            
        SetEntry e = new SetEntry(o);
        lookup.put(o,e);
        if (tail != null) {
            tail.insertEntryAfter(e);
        }
        tail = e;
        if (head == null) {
            head = tail;
        }
        return true;
    }


    public boolean remove(Object o) {
        assert lock == null || Thread.holdsLock(lock) : lock + " : " + this;
        if (parent != null) {
            // make sure it is in the subset
            Iterator itr = iterator();
            while (itr.hasNext()) {
                if (itr.next() == o) {
                    itr.remove();
                    return true;
                }
            }
            return false;
        }
        SetEntry e = (SetEntry)lookup.get(o);
        if (e == null) {
            return false; // not in list
        }
        return cleanupEntry(e);
    }

    public void sort(Comparator c) {
        if (head == null) return;
        head = head.sort(c);
        if (start != null)
            start = head;
        // find tail
        SetEntry e = head;
        while (e.next != null)
            e = e.next;
        tail = e;
        if (end != null)
            end = tail;
    }

}

