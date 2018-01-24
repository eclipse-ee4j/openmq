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
 * @(#)PriorityQueue.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.LinkedList;
import com.sun.messaging.jmq.io.*;

/**
 *
 * <p>Queue structure that handles message priorities. Messages received in a
 * Session are prioritized based on the message priority (0-9).
 * </P>
 */

class PriorityQueue implements MessageQueue {

    /**
     * array size 10 for message priority 0 - 9.
     */
    protected static final int ARRAY_SIZE = 10;

    /**
     * Linked list to hold LinkedList entries.  Each LinkedList entry is to
     * hold pkt/message of a certain priority.
     */
    private LinkedList[] qlist = null;

    /**
     * priority queue total size.
     */
    protected int qsize = 0;

    /**
     * Constructor.  Call init() to init. its data structure.
     */
    public PriorityQueue() {
        init();
    }

    /**
     * Initialize qlist to an array of LinkedList with size of
     * ARRAY_SIZE (10).
     *
     * Init all elements in the qlist to null.
     */
    protected void init() {

        //construct an array of arraylist
        qlist = new LinkedList [ARRAY_SIZE];

        //init each entry to null.
        for ( int i=0; i<ARRAY_SIZE; i++) {
            qlist[i] = null;
        }
    }

    /**
     * Get the queue size.
     */
     public synchronized int size() {
        return qsize;
     }

     /**
      * Check if the queue size is empty.
      * @return true if queue size is 0.  Otherwise, return false.
      */
     public synchronized boolean isEmpty() {
        return (qsize == 0);
     }

    /**
     * Clears all elements from the queue.
     **/
    public synchronized void
    clear () {
        for ( int i=0; i<ARRAY_SIZE; i++) {
            qlist[i] = null;
        }

        qsize = 0;
    }

    /**
     * Enqueues an object in the queue based on the priority of the obj.
     * The priority of nobj is obtained from ReadOnlyPacket or MessageImpl.
     * @param nobj new object to be enqueued.
     *
     * @see getPriority
     */
    public synchronized void
    enqueue(Object nobj) {

        int priority = getPriority(nobj);

        if ( qlist [priority] == null ) {
            qlist [priority] = new LinkedList ();
        }

        qlist [priority].add(nobj);

        qsize ++;

    }
    
    /**
     * Add an object to the front of the queue based on the priority of the obj.
     * The priority of nobj is obtained from ReadOnlyPacket or MessageImpl.
     * @param nobj new object to be added to the front of the queue.
     *
     * @see getPriority
     */
    public synchronized void
    enqueueFirst(Object nobj) {

        int priority = getPriority(nobj);

        if ( qlist [priority] == null ) {
            qlist [priority] = new LinkedList ();
        }

        qlist [priority].addFirst(nobj);

        qsize ++;

    }

    /**
     * get JMS message priority.
     * @param nobj get JMS message priority from this obj.
     * @return message priority.
     */
    protected int getPriority (Object nobj) {

        int priority = 0;

        try {
            if (nobj instanceof ReadOnlyPacket) {
                priority = ( (ReadOnlyPacket) nobj).getPriority();
            }
            else if (nobj instanceof MessageImpl) {
                priority = ( (MessageImpl) nobj).getJMSPriority();
            }
        } catch (Exception jmse) {
            /**
             * This should not happen.  Dump stack trace to output
             * stream if this SHOULD happen.
             */
            Debug.printStackTrace(jmse);
        }

        return priority;
    }

    /**
     * Dequeues an element from the queue.
     * @return dequeued object, or null if empty queue
    */
    public synchronized Object
    dequeue() {
        //var to hold element to be returned.
        Object obj = null;

        //index set to priority 9.
        int index = ARRAY_SIZE - 1;
        //loop from p9 to p0.
        while ( index >= 0 ) {
            //check if there are elements in a specific priority queue.
            if ( (qlist[index] != null) && (qlist[index].isEmpty() == false) ) {
                //get and remove the first element in the list that contains
                //element.
                obj = qlist[index].removeFirst();
                //decrease q size by one
                qsize --;
                //break out while loop.
                index = -1;
            }
            //decrease the priority.
            index --;
        }

        return obj;
    }

    /**
     * Get all elements in the priority queue and return as an array
     * of objects.
     *
     * @return an array of objects in the queue.
     */
    public synchronized Object[] toArray() {

        LinkedList list = new LinkedList();

        //start at p9.
        int index = ARRAY_SIZE - 1;

        while ( index >= 0 ) {

            /**
             * start from priority 9,
             * if the linked list is not null and is not empty,
             * add elements in the list to the return list
             */
            if ( qlist[index] != null && (qlist[index].isEmpty() == false) ) {

                int size = qlist[index].size();

                for ( int i=0; i < size; i++) {
                    list.add ( qlist[index].get(i) );
                }

            }

            index --;
        }

        return list.toArray();
    }

    /**
     * remove obj from the queue.
     * @param obj
     * @return
     */
    public synchronized boolean remove (Object obj) {

        //if obj contains obj and removed, found is set to true.
        boolean found = false;

        //start at p9.
        int index = ARRAY_SIZE -1;

        /**
         * while searching (from p9) and not found.
         */
        while ( index >= 0 && (found == false)) {

            /**
             * start from priority 9,
             * if the array list is not null and is not empty,
             * find and remove the obj from the list.
             */
            if ( qlist[index] != null && (qlist[index].isEmpty() == false) ) {

                if ( qlist[index].contains(obj) ) {
                    found = qlist[index].remove(obj);
                    qsize --;
                }

            }

            index --;
        }

        return found;
    }

}
