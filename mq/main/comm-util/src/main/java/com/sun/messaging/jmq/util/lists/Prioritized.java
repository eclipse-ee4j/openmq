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
 * @(#)Prioritized.java	1.7 08/28/07
 */


package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * interface used by lists which use priorities to allow
 * a single test to verify any collection using priority
 * ordering
 */

public interface Prioritized
{
    /**
     * add a single item at the passed in priority.
     * Item will be added behind all other objects
     * which have the same priority
     * @param priority priority to add object at (0 is
     *        the highest priority, followed by 1, etc)
     * @param o object to add
     * @returns true if the item could be added
     */
    public boolean add(int priority, Object o);

    /**
     * add the set of items infront of all items of
     * the same priority.
     * @param priority priority to add object at (0 is
     *        the highest priority, followed by 1, etc)
     * @param c collection of objects to add
     */
    public void addAllToFront(Collection c, int pri);


    public void addAllOrdered(Collection c);

}
