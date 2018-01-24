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
 * @(#)FilterableSet.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.*;

/**
 * An interface for sets which allow subsets of objects
 * to be returned that match a certain critera specified
 * by a filter or returns a set in the order specified by
 * the comparator
 *
 * @see Filter
 * @see java.util.Comparator
 */
public interface FilterableSet extends Set
{
    /**
     * returns a new set that contains all
     * objects matching the filter.
     * This new set will not be updated to reflect
     * changes in the original set.
     * @param f filter to use when matching
     * @returns a new set of matching objects
     * @see #subSet(Filter)
     */
    public Set getAll(Filter f);

    /**
     * returns a subset that contains all
     * objects matching the filter.
     * Changes made to this  new set will be reflected
     * in the original set (and changes in the original
     * set will reflect in the subset).<P> For example,
     * if you remove an object from the original set it
     * will also be removed from the subset.
     * @param f filter to use when matching
     * @returns a subset of matching objects
     * @see #getAll(Filter)
     */
    public SubSet subSet(Filter f);

    /**
     * returns the objects in this set ordered
     * by the comparator.
     * Changes made to this  new set will be reflected
     * in the original set (and changes in the original
     * set will reflect in the subset).<P> For example,
     * if you remove an object from the original set it
     * will also be removed from the subset.
     * @param c comparator to use when sorting the objects
     * @returns a set ordered by the comparator
     */
    public SubSet subSet(Comparator c);

    /**
     * Method which allows an object to be added to the
     * class for a specific reason.
     * @see EventBroadcaster
     * @param o object to add
     * @param r reason the object was added
     * @returns if the item was added to the list
     */
    public boolean add(Object o, Reason r);

    /**
     * Method which allows an object to be removed to the
     * class for a specific reason.
     * @see EventBroadcaster
     * @param o object to remove
     * @param r reason the object was removed
     * @returns true if the item was removed, false if
     *          it didnt exist
     */
    public boolean remove(Object o, Reason r);
}
