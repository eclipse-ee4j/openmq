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
 * @(#)SequentialQueue.java	1.6 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Vector;

/**
 * <p>
 * Queue structure to store messages in a serial order.
 * Messages received in a Session are stored in its received order.
 * </P>
 */

public class SequentialQueue implements MessageQueue {
    //queue structure.
    private Vector queue = null;

    /**
     * default constructor.
     */
    public SequentialQueue() {
        queue = new Vector ();
    }

    /**
     * constructor with init queue size.
     * @param size the init size for the queue size.
     */
    public SequentialQueue(int size) {
        queue = new Vector (size);
    }

    /**
     * constructor with init queue size and increment number.
     * @param size the init size for the queue size.
     * @param increment number to increase queue size the when reached
     *                  the init size.
     */
    public SequentialQueue(int size, int increment) {
        queue = new Vector (size, increment);
    }

    /**
     * get queue size
     */
     public int size() {
        return queue.size();
     }

     /**
      * check if the queue size is empty.
      * @return true if the queue size is empty.
      */
     public boolean isEmpty() {
        return queue.isEmpty();
     }

    /**
     * Clears all elements from the queue
     **/
    public void clear () {
        queue.clear();
    }

    /**
     * Enqueues an object in the queue.
     * @param nobj new object to be enqueued
     */
    public void enqueue(Object nobj) {
        queue.addElement(nobj);
    }

    /**
     * Dequeues an element from the queue.
     * @return dequeued object, or null if empty queue
    */
    public Object dequeue() {
        //var to hold element to be returned.
        Object obj = null;

        /**
         * not synced since we have only one thread in the session that
         * access this queue.
         *
         * Note: added sync for general purpose.
         */
        synchronized (queue) {
            if (queue.isEmpty() == false) {
                obj = queue.remove(0);
            }
        }

        return obj;
    }

    /**
     * Get all elements in the queue and return as an array
     * of objects.
     *
     * @return an array of objects in the queue.
     */
    public Object[] toArray() {
        return queue.toArray();
    }

    /**
     * remove obj from the queue.
     * @param obj obj to be removed from the queue.
     * @return true if object is in the queue and removed.  Otherwise,
     *         return false.
     */
    public boolean remove (Object obj) {
        return queue.remove(obj);
    }

    /**
     * Adds the specified object to the front of the queue.
     * @param nobj new object to be added to the front of the queue
     */
	@Override
	public void enqueueFirst(Object nobj) {
		// This method was added for PriorityQueue, not for SequentialQueue
		// so has not been implemented   
		throw new RuntimeException("This method is not yet implemented");
	}

}
