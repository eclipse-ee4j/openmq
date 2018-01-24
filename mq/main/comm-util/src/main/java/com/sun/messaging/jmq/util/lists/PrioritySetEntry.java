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
 * @(#)PrioritySetEntry.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.Comparator;

class PrioritySetEntry extends SetEntry
{
    int priority = 0;
    public PrioritySetEntry(Object o, int priority)
    {
        super(o);
        this.priority = priority;
    }
    public int getPriority() {
        return priority;
    }



    protected Comparator createSortComparator(Comparator comp)
    {
        return new PrioritySetEntryComparator(comp);
    }

    static class PrioritySetEntryComparator implements Comparator {
        Comparator datacmp = null;

        public PrioritySetEntryComparator(Comparator c) {
            datacmp = c;        
        }
        public int compare(Object o1, Object o2) {
            if (o1 instanceof PrioritySetEntry 
               && o2 instanceof PrioritySetEntry) {
                // compare
                if ( ((PrioritySetEntry)o1).priority !=
                      ((PrioritySetEntry)o2).priority)
                    return ((PrioritySetEntry)o1).priority 
                         - ((PrioritySetEntry)o2).priority;
                Object d1 = ((PrioritySetEntry)o1).data;
                Object d2 = ((PrioritySetEntry)o2).data;
                return datacmp.compare(d1, d2);
            } else if (o1 instanceof PrioritySetEntry) {
                Object d1 = ((SetEntry)o1).data;
                return datacmp.compare(d1, o2);
            } else if (o2 instanceof PrioritySetEntry) {
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

}
