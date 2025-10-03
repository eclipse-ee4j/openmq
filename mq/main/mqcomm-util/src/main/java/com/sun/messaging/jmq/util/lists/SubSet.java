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

import java.util.Set;

/**
 * this class represents a view into an existing set. This subset is different from a copy of a set, because changing
 * one affects the other. e.g.<BR>
 * <UL>
 * <LI>adding an item to the original set is reflected in the subset <i>if </i>the new object should be part of the
 * subset</li>
 * <LI>adding an item to the subset is reflected in the original set</li>
 * </UL>
 */
public interface SubSet<E> extends Set<E>, EventBroadcaster {

    /**
     * Method which allows an object to be added to the class for a specific reason.
     *
     * @see EventBroadcaster
     * @param o object to add
     * @param r reason the object was added
     * @return if the item was added to the list
     */
    boolean add(E o, Reason r);

    /**
     * Method which allows an object to be removed to the class for a specific reason.
     *
     * @see EventBroadcaster
     * @param o object to remove
     * @param r reason the object was removed
     * @return true if the item was removed, false if it didnt exist
     */
    boolean remove(E o, Reason r);

    /**
     * optional method which tells the system it can free up resources. If destroy is not called, the subset will no longer
     * be maintained once the garbage collector frees the reference.
     */
    void destroy();

    /**
     * Used instead of iterator.next(), iterator.remove() to remove the first item from the list. Subsets do not allow
     * iterator.remove() to be called because of the risk on incorrect state or deadlocks.
     */
    E removeNext();

    String toDebugString();

    E peekNext();

}
