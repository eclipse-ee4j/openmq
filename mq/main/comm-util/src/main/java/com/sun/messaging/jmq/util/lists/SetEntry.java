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
 * @(#)SetEntry.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * Entry used in the ordered list. package private
 */

class SetEntry
{
    public static boolean DEBUG = false;

    SetEntry next = null;
    SetEntry previous = null;
    boolean valid = true;
    Object data = null;

    static int ctr = 0;
    int debugid = 0;
    
    /**
     * takes a linked list which starts with the first
     * SetEntry and sorts it
     */
    public SetEntry sort(Comparator comp)
    {
        if (this.next == null) return this;

        // OK, for now we are doing this the slow/easy way
        // the assumption is that this is an infrequent operation

        // stick everything in an array list
        ArrayList al = new ArrayList();
        SetEntry entry = this;
        al.add(this);
        while (entry.next != null) {
            al.add(entry.next);
            entry = entry.next;
        }
        // sort
        Collections.sort(al, comp);
        // now fill in the next entries
        SetEntry back = null;
        for (int i = 0; i < al.size(); i ++) {
            SetEntry fwd = (i < (al.size() -1)) ?
                            (SetEntry)al.get(i+1) : null;
            SetEntry cur =  (SetEntry)al.get(i);
            cur.previous = back;
            cur.next = fwd;
            back = cur;
        }
        return (SetEntry)al.get(0);
    }

    protected Comparator createSortComparator(Comparator comp)
    {
        return new SetEntryComparator(comp);
    }

        
    static class SetEntryComparator implements Comparator {
        Comparator datacmp = null;

        public SetEntryComparator(Comparator c) {
            datacmp = c;        
        }
        public int compare(Object o1, Object o2) {
            if (o1 instanceof SetEntry && o2 instanceof SetEntry) {
                // compare
                Object d1 = ((SetEntry)o1).data;
                Object d2 = ((SetEntry)o2).data;
                return datacmp.compare(d1, d2);
            } else if (o1 instanceof SetEntry) {
                Object d1 = ((SetEntry)o1).data;
                return datacmp.compare(d1, o2);
            } else if (o2 instanceof SetEntry) {
                Object d2 = ((SetEntry)o2).data;
                return datacmp.compare(o1, d2);
            } else if (o1 == null && o2 == null) {
                return 0;
            } else if (o1 == null) {
                return 1;
            } else if (o2 == null) {
                return -1;
            } else {
                return o1.hashCode() - o2.hashCode();
            }
        }
        public boolean equals(Object o1) {
            return super.equals(o1);
        }
    }

    public SetEntry(Object data) {
        if (DEBUG) {
            debugid = ctr ++; 
        } else {
            debugid = hashCode();
        }
        this.data = data;
    }

    public String toString() {
        return "SetEntry(" + debugid 
            +")[ before(" +
            (previous == null ? null : String.valueOf(previous.debugid)) 
            + ") after(" +
            (next == null ? null : String.valueOf(next.debugid)) 
            +") ] " +data+"]valid="+isValid();
    }
       

    public SetEntry getNext() {
        return next;
    }

    public SetEntry getPrevious() {
        return previous;
    }

    public Object getData() {
        return data;
    }

    public boolean isFirst() {
        return previous == null;
    }

    public boolean isLast() {
        return next == null;
    }

    public boolean isValid() {
        return valid;
    }

    // speed up gc
    public void clear() {
        previous = null;
        next = null;
        data = null;
    }

    public boolean remove() {
        valid = false;
        data = null;
        if (previous != null) {
            previous.next = next;
        }
        if (next != null) {
            next.previous = previous;
        }
        if (next == null || previous == null)
            return true; // first or last
        assert previous.next == next
              && next.previous == previous;
        return false;
    }

    // returns true if last
    public boolean insertEntryAfter(SetEntry newEntry) {
        newEntry.previous = this;
        newEntry.next = this.next;
        this.next = newEntry;
        if (newEntry.next != null)
            newEntry.next.previous = newEntry;

        assert newEntry.previous == this && this.next == newEntry;
        return newEntry.next == null;
    }

    public boolean insertEntryBefore(SetEntry newEntry) {
        if (this.previous != null)
            this.previous.next = newEntry;
        newEntry.next = this;
        newEntry.previous = this.previous;
        this.previous = newEntry;
        assert newEntry.next == this && this.previous == newEntry;
        return newEntry.previous == null;
    }

}
 
/*
 * EOF
 */
