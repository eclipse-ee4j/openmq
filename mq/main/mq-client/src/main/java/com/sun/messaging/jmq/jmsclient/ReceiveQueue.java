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
 * @(#)ReceiveQueue.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Vector;
import java.io.PrintStream;

/**
 * This Class is used by MessageConsumerImpl and ProtocolHandler(for ack use).
 */

class ReceiveQueue extends SessionQueue {

    private boolean receiveInProcess = false;

    public ReceiveQueue() {
        super();
    }

    public ReceiveQueue (boolean useSequential, int size) {
        super (useSequential, size);
    }

    protected synchronized Object dequeueWait() {
        return dequeueWait (0);
    }

    /**
    * receive with time out.
    */
    protected synchronized Object dequeueWait ( long timeout ) {

        long waitTime = timeout;
        boolean expired = false;

        while ( isEmpty() || isLocked ) {

            if ( isClosed || expired ) {
                return null;
            }

            try {
                if ( timeout == 0 ) {
                    wait (0);
                } else {

                    long st = System.currentTimeMillis();

                    wait(waitTime);

                    if ( isEmpty() || isLocked ) {
                        long elapsed = System.currentTimeMillis() - st;
                        waitTime = waitTime - elapsed;

                        if ( waitTime <= 0 ) {
                            expired = true;
                        }
                    }
                }
            } catch (InterruptedException e) {
            }
        }

        //if still in lock mode or Connection.close() is called
        //don't even check if there is anything in the queue.

        if ( isClosed ) {
            return null;
        }

        //Set this flag so that Connection.stop() will be blocked.
        //This flag is set only when receive() was called and is going to
        //obtain the next available message.
        //NOTE: If used as ack temp queue, this flag has no meaning.
        receiveInProcess = true;

        return dequeue();
    }

    /**
     * This method is called before receive() is returned.  This will wake up
     * the thread from connection.stop(), if any, wait on
     * waitUntilReceiveDone().
     */
    protected synchronized void setReceiveInProcess(boolean state) {
        receiveInProcess = state;
        notifyAll();
    }

    /*
     * This method is called by Session.stop().  This method will block receive()
     * until Connection.start() is called or timeout.
     */
    protected synchronized void stop() {
        isLocked = true;
        waitUntilReceiveIsDone();

        if (debug) {
            Debug.println("receive queue 'stop' called ...");
        }
    }

    /**
     * when a failover occurred, we don't want to wait since the ack may be
     * blocked at the connection.  we simply set the flag and return.
     */
    protected synchronized void stopNoWait() {
        isLocked = true;
    }

    protected synchronized void start() {

        if ( isEmpty() == false ) {
            setIsLocked (false);
        } else {
            isLocked = false;
        }

        if (debug) {
            Debug.println("receive queue 'start' called ...");
        }
    }

    //when Connection.stop is called, each session call method to ensure no
    //messages will be delivered until Connection.start() is called.
    //
    //This method is not returned until SessionReader is locked and blocked.
    protected synchronized void waitUntilReceiveIsDone() {

        try {
            while ( isLocked && receiveInProcess == true ) {
                wait ();
            }
        } catch (InterruptedException e)  {
            ;
        }

    }

    public void dump (PrintStream ps) {
        ps.println ("------ ReceiveQueue dump ------");

        ps.println("isLocked: " + isLocked);
        ps.println("receiveInProcess: " + receiveInProcess );
        ps.println ("isClosed: " + isClosed);

        if ( size() > 0 ) {
            ps.println ("^^^^^^ receive queue super class dump ^^^^^^");
            super.dump(ps);
            ps.println ("^^^^^^ end receive queue super class dump ^^^^^^");
        }
    }

}
