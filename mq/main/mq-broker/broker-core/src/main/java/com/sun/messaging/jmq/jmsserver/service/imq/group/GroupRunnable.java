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
 * @(#)GroupRunnable.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.group;

import java.io.*;
import java.util.Hashtable;
import com.sun.messaging.jmq.jmsserver.pool.BasicRunnable;
import com.sun.messaging.jmq.jmsserver.pool.ThreadPool;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;


public class GroupRunnable extends BasicRunnable
{

    SelectThread selthr = null;
    protected int ioevents = 0;
    Object threadUpdateLock = new Object();

    boolean paused = false;


    public GroupRunnable(int id, ThreadPool pool) {
        super(id, pool);
    }

    public Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        if (selthr == null) {
            ht.put("selthr", "empty");
        } else {
            ht.put("selthr", selthr.getDebugState());
        }
        return ht;
    }

    public  void assignThread(SelectThread selthr, int events) 
        throws IOException
    {
        synchronized (threadUpdateLock) {
            if (this.selthr != null) {
                throw new IOException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Error trying to assign " + selthr + 
                     " to  group runnable " + this));
            }
            this.selthr = selthr;
            selthr.assign(this);
            this.ioevents = events;
            assigned(); // wakes us up
        }
    }


    public String toString() {
         return "GroupRun[id ="+ id + ", ioevents=" + ioevents 
                    + ", behavior=" +behaviorToString(behavior)
                    + ", selthr={" + selthr + "}, state=" 
                    + stateToString(state) + "]";
    }

    public void freeThread() {
        synchronized (threadUpdateLock) {
            if (selthr != null) {
                selthr.free(this);
                selthr = null;
                ioevents = 0;
                release();
            }
        }
    }

    public void suspend() {
        super.suspend();
        paused = true;
    }

    public void resume() {
        super.resume();
        synchronized (this) {
            paused = false;
            notifyAll();
        }
    }



    protected void process() 
        throws IOException
    {
        boolean OK = false;

        synchronized (this) {
            while (paused) {
                try {
                    wait();
                } catch (Exception ex) {
                }
            }
        }

        // OK .. determine when to free
        Throwable err = null;
        try { // how to handle ???
            if (selthr != null)
                selthr.processThread();
            OK = true;
        } catch (NullPointerException ex) {
            // if we are shutting the thread down .. there are times
            // when selector may be set to null after the valid check
            // we really dont want to have to synchronized each access
            // SO ... if we get a null pointer exception .. just ignore
            // it and exit the thread ... its what we want to do anyway
            if (selthr != null && selthr.isValid())
                logger.logStack(Logger.WARNING,
                        BrokerResources.E_INTERNAL_BROKER_ERROR, 
                        selthr.getSelector().toString(), ex);
            err = ex;
        } catch (IOException ex) {
            // ignore, its OK
            OK = true;
            err = ex;
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
                    BrokerResources.E_INTERNAL_BROKER_ERROR, ex);
            err = ex;
        } finally {
            if (!OK) {
                if (err != null)
                    logger.logStack(Logger.WARNING,"got an unexpected error " + err + " freeing thread " + this, err);
                freeThread();
            }
        }
    }
    
}


