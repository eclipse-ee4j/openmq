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
 * @(#)OperationRunnable.java	1.26 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.io.*;
import com.sun.messaging.jmq.jmsserver.pool.BasicRunnable;
import com.sun.messaging.jmq.jmsserver.pool.ThreadPool;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;


public class OperationRunnable extends BasicRunnable
{
    public static final int FOREVER = -1;
    public static final int UNTIL_DONE = 0;

    Operation op = null;
    int opcnt = 0;
    int operationCount = 0;
    protected int ioevents = 0;
    protected boolean wait = false;
    Object opUpdateLock = new Object();


    public OperationRunnable(int id, ThreadPool pool, boolean wait) {
        super(id, pool);
        this.wait = wait;
    }

    public void clear() {
        synchronized (opUpdateLock) {
            op = null;
        } 
        release();
    }

    public void suspend() {
        super.suspend();
        if (op != null)
            op.suspend();
    }

    public void resume() {
        super.resume();
        if (op != null)
            op.resume();
    }




    public String toString() {
         return "OpRun[id ="+ id + ", ioevents=" + ioevents 
                    + ", behavior=" +behaviorToString(behavior)
                    + ", op={" + op + "}, state=" 
                    + stateToString(state) + "]";
    }

    public  void assignOperation(Operation newop, 
                                 int ioevents, 
                                 int how_long) 
         throws IllegalAccessException
    {
        synchronized (opUpdateLock) {
            if (op != null) {
                throw new IllegalAccessException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Error trying to assign " + newop 
                    + " to  assigned operation " + this));
            }

            this.op = newop;
            op.threadAssigned(this, ioevents); 
            operationCount = how_long;
            opcnt = 0;
            this.ioevents = ioevents;
        }
        assigned(); // wakes us up
    }

    public void freeOperation() {
        synchronized (opUpdateLock) {

            if (op != null) {
                op.notifyRelease(this, ioevents);
                op = null;
            }
        }
        release();
    }

    public  void destroy(String reason) {
        if (op != null)
            op.destroy(true, GoodbyeReason.OTHER, reason);
        super.destroy();
    }




    protected void process() 
        throws IOException
    {
        Operation myop = null;
        synchronized (opUpdateLock) {
            myop = op;
        }
        
        if (myop == null || state < RUN_ASSIGNED) {
                return;
        }
        if (!myop.isValid()) {
              freeOperation();
              return;
        }
        if (state > RUN_CRITICAL)  {
            if (myop.isValid()) {
                myop.destroy(false, GoodbyeReason.CON_FATAL_ERROR,
                             "invalid operation");
            }
            freeOperation();
            throw new IOException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                   "Exiting"));
        }

        // OK .. determine when to free
        try {
            boolean done = myop.process(ioevents, wait);
            switch (operationCount) {
                case FOREVER:
                    return;
                case UNTIL_DONE:
                {
                    if (done) {
                        freeOperation();
                    }
                    return;
                }
                default:
                {
                    opcnt ++;
                    if (opcnt >= operationCount || done) {
                        freeOperation();
                    }
                    return;
                }
            }
        } catch (IOException ex) {
            // OK .. destroy the operation ... its gone
            if (getDEBUG()) {
                logger.logStack(Logger.DEBUG,
                    "Debug: Connection going away", ex);
            }
            if (ex instanceof EOFException) {
                myop.destroy(false, GoodbyeReason.CLIENT_CLOSED,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.M_CONNECTION_CLOSE));
            } else {
                myop.destroy(false, GoodbyeReason.CON_FATAL_ERROR,
                             ex.toString());
            }
            freeOperation();
        }
    }
    
}


