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
 * @(#)LockTable.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.*;
/**
 * this is a generic class which allows you to wait for notification
 * that an event has occurred even if the objects are different but
 * equivalent (generate the same hashCode/isequals == true)
 */

public class LockTable
{
    HashMap notifyTable = new HashMap();
    
    /**
     * Request notification when notifiy is called on the same or
     * equivalent instance of an object. This method should be called
     * before any notification could occur.
     * @param object object to wait for notification on
     * @throws IllegalAccessException indicates the system is already waiting
     *                 on that object
     */
    public  void requestNotify(Object object) throws IllegalAccessException {
        synchronized(notifyTable) {
            if (notifyTable.containsKey(object))
                throw new IllegalAccessException("Already waiting for " + object);
            Object lock = new IDLock();
            notifyTable.put(object, lock);
        }   
    }

    /**
     * Cancel notification on an object. Cleans up any
     * allocated resources. This call does NOT wake up
     * any resources waiting for notification.
     * @param object equivalent object to the one passed into
     *               requestNotify
     */
    public void cancelNotify(Object object) {
        synchronized(notifyTable) {
            notifyTable.remove(object);
        }
    }

    // we cant just sync on interest .. it could be a different
    // object w/ the same contents

    /**
     * Wait for notification on an object.
     *
     * @param object equivalent object to the one passed into
     *               requestNotify
     */
    public void wait(Object object) {
        wait(object, 0 /* no timeout */);    
    }


    /**
     * Waits up to a timeout for notification on an object.
     *
     * @param timeout time (in milliseconds) to wait for the
     *                notification
     * @param object equivalent object to the one passed into
     *               requestNotify
     * @returns true if the notification was received, false
     *        if the system timed out.
     */
    public  boolean wait(Object object, long timeout) {
        // OK first get the lock
        IDLock lock = null;
        synchronized (notifyTable) {
            lock = (IDLock)notifyTable.get(object);
        }
        if (lock == null) return true; // done
        synchronized(lock) {
            if (lock.isValid()) {
                try {
                    lock.wait(timeout);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (lock.isValid() ) {
                // wait did not complete
                return false;
            }
            
            synchronized(notifyTable) {
                lock = (IDLock)notifyTable.remove(object);
            }
            return true;

        }
    }

    /**
     * Notify the system that the operation has completed.
     *
     * @param object equivalent object to the one passed into
     *               requestNotify
     */
    public void notify(Object object) {
        IDLock lock = null;
        synchronized (notifyTable) {
            lock = (IDLock)notifyTable.get(object);
        }
        if (lock == null) return;
        synchronized(lock) {
            lock.destroy();
            lock.notifyAll();
        }
    }
}


class IDLock {
    boolean valid = true;
    public IDLock() {
    }
    public void destroy() {
        valid = false;
    }
    public boolean isValid() {
        return valid;
    }
}

