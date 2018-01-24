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
 * @(#)SubSet.java	1.8 08/28/07
 */

package com.sun.messaging.jmq.util.lists;


import java.util.Set;

/**
 * this class represents a view into an existing set.
 * This subset is different from a copy of a set, because
 * changing one affects the other. e.g.<BR>
 * <UL>
 *   <LI>adding an item to the original set is reflected
 *       in the subset <i>if </i>the new object should be
 *       part of the subset </li>
 *   <LI>adding an item to the subset is reflected
 *       in the original set </li>
 * </UL>
 */
public interface SubSet extends Set, EventBroadcaster
{

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

    /**
     * optional method which tells the
     * system it can free up resources.
     * If destroy is not called, the
     * subset will no longer be maintained
     * once the garbage collector frees
     * the reference.
     */
    public void destroy();

    /**
     * Used instead of iterator.next(), 
     * iterator.remove() to remove the first 
     * item from the list.
     * Subsets do not allow iterator.remove() to
     * be called because of the risk on 
     * incorrect state or deadlocks.
     */
    public Object removeNext();

    public String toDebugString();

    public Object peekNext();


}
