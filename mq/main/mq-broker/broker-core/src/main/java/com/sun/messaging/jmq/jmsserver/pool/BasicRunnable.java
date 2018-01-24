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
 * @(#)BasicRunnable.java	1.41 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.pool;

import java.util.TimerTask;
import java.lang.Runnable;
import java.util.Hashtable;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.timer.*;

/**
 * Basic "runnable" which is used by the thread pool.
 * <P>
 * This class provides basic support for timimg out unused
 * threads.
 *
 * @see ThreadPool
 */
public abstract class BasicRunnable implements Runnable
{

    private static boolean DEBUG = false;

    public static boolean getDEBUG() {
        return DEBUG;
    }

//------------------------------------------------
// behavior
//------------------------------------------------

    // behavior types which determine HOW the runnable should
    // react when it no longer has an operation to
    // use

    /**
     * wait for another operation
     */
    public static final int B_STAY_RUNNING = 0;

    /**
     * wait until either another operation has been
     * received OR if a timeout occurs -> destroy
     */
    public static final int B_TIMEOUT_THREAD = 1;

    /**
     * destroy (we are over the max)
     */
    public static final int B_DESTROY_THREAD = 2;

    /**
     * current thread behavior
     */
    protected int behavior = B_STAY_RUNNING;

    /* STATES */
//---------------------------------------------------
// current state of the Runnable
//---------------------------------------------------
    public static final int RUN_STARTING = 0;
    public static final int RUN_READY = 1;
    public static final int RUN_PREASSIGNED = 2;
    public static final int RUN_ASSIGNED = 3;
    public static final int RUN_SUSPENDED = 4;
    public static final int RUN_CRITICAL = 5;
    public static final int RUN_DESTROYING = 6;
    public static final int RUN_DESTROYED = 7;

    protected int state = RUN_STARTING;

    boolean suspended = false;

    /**
     * timeout to wait until destroying thread
     */
    public static final long DEFAULT_TIMEOUT =  Globals.getConfig().getIntProperty(
	Globals.IMQ + ".thread.expiration.timeout", 120);

    private long timeout = DEFAULT_TIMEOUT;
    private ThreadExpiration expr = null;


    protected Logger logger  = Globals.getLogger();

    /**
     * pointer to the ThreadPool parent 
     */
    protected ThreadPool tctrl;

    /**
     * handlers "unique" id 
     */
    protected int id;



    /**
     * Create a Handler thread with a unique id and a pointer
     * to the thread pool parent
     */
    public BasicRunnable(int id, ThreadPool tctrl) {
        this(id, tctrl, B_STAY_RUNNING);
    }

    public BasicRunnable(int id, ThreadPool tctrl, int behavior) {
        if (DEBUG) {
            logger.log(Logger.DEBUG, 
                "BasicRunnable: created BasicRunnable {0}:{1}",
                String.valueOf(id), tctrl.toString());
        }
        this.tctrl = tctrl;
        this.id = id;
        setThreadBehavior(behavior);
    }

    public String toString() {
        String str = "BasicRunnable[" + id + " , " + behaviorToString(behavior)
           + " ," + stateToString(state) + "]";
        return str;
    }  

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("state", stateToString(state));
        ht.put("behavior", behaviorToString(behavior));
        ht.put("suspended", Boolean.valueOf(suspended));
        ht.put("id", Integer.valueOf(id));
        return ht;
    }


    protected String stateToString(int state) {
        switch (state) {
            case RUN_STARTING:
                return "RUN_STARTING";
            case RUN_READY :
            {
                if (suspended) {
                    return "RUN_READY(suspended)";
                } else {
                    return "RUN_READY ";
                }
            }
            case RUN_PREASSIGNED :
            {
                if (suspended) {
                    return "RUN_PREASSIGNED(suspended)";
                } else {
                    return "RUN_PREASSIGNED ";
                }
            }
            case RUN_ASSIGNED :
            {
                if (suspended) {
                    return "RUN_ASSIGNED(suspended)";
                } else {
                    return "RUN_ASSIGNED ";
                }
            }
            case RUN_CRITICAL :
            {
                if (suspended) {
                    return "RUN_CRITICAL(suspended)";
                } else {
                    return "RUN_CRITICAL ";
                }
            }
            case RUN_DESTROYING :
                return "RUN_DESTROYING ";
            case RUN_DESTROYED:
                return "RUN_DESTROYED";
            default:
                return "RUN_UNKNOWN(" + state + ")";
        }
    }
    protected static String behaviorToString(int behavior) {
        switch (behavior) {
            case B_STAY_RUNNING:
                return "B_STAY_RUNNING";
            case B_TIMEOUT_THREAD:
                return "B_TIMEOUT_THREAD";
            case B_DESTROY_THREAD:
                return "B_DESTROY_THREAD";
            default:
                return "B_UNKNOWN("+behavior + ")";
        }
    }


    public synchronized boolean available() {
        return state == RUN_READY;
    }

    public boolean hasBeenDestroyed()
    {
        return (state == RUN_DESTROYED);
    }

    public synchronized boolean waitOnDestroy(long time) {
        if (state >= RUN_DESTROYED) {
            return true; // HAS BEEN DESTROYED
        } else if (state < RUN_DESTROYING) {
            destroy();
        }
        while (state == RUN_CRITICAL)  { // we can shutdown if we arent critical
            try {
                wait();
            } catch (InterruptedException ex) {
                break;
            }
        }
        return hasBeenDestroyed();
    }

    public void suspend() {
        suspended = true;
    }

    public void resume() {
        suspended = false;
    }

    public synchronized void setState(int newstate) {
        if (state == newstate) {
            // nothing to do, return
            return;
        }
        if (state >= RUN_DESTROYED) { // ignore
            return;
        }
        if (state >= RUN_DESTROYING && newstate < RUN_DESTROYING) {
            return;
        }
        if (behavior == B_TIMEOUT_THREAD) {
            if (newstate == RUN_READY) {
                // start timing
                startTimeout();
            } else {
                cancelTimeout();
            }
        }
        state = newstate;
        if (state >= RUN_DESTROYING || state == RUN_ASSIGNED) {
            this.notifyAll();
        }
    }


    public void setThreadBehavior(int newbehavior) {
        if (behavior == newbehavior) {
            // nothing to do, return
            return;
        }
        int oldbehavior = behavior;
        behavior = newbehavior;
        if (oldbehavior == B_TIMEOUT_THREAD) {
            // ok .. we were a timeout thread, 
            cancelTimeout();
        } else if (newbehavior == B_TIMEOUT_THREAD && state == RUN_READY) {
            startTimeout();
        } else if (newbehavior == B_DESTROY_THREAD) {
            destroy();
        }
    }


    public synchronized void startTimeout() {
        if (behavior != B_TIMEOUT_THREAD) {
            throw new ArrayIndexOutOfBoundsException( 
                Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION, 
                "trying to timeout a non-TIMEOUT thread"));
        }
        if (expr != null) {
            cancelTimeout();
        }
        MQTimer timer = Globals.getTimer();
        expr = new ThreadExpiration(this);
        long time = timeout * 1000; // convert to miliseconds
        try {
            timer.scheduleAtFixedRate(expr, time, time); 
        } catch (IllegalStateException ex) {
            // shutting down, nothing to do 
            logger.log(Logger.INFO,"Timer shutting down ", ex);
        }
    }

    // 0 is no timeout
    public synchronized void setTimeout(long timeout)
    {
        this.timeout = timeout;
    }


    static class ThreadExpiration extends TimerTask
    {
        BasicRunnable hr = null;

        public ThreadExpiration(BasicRunnable hr) {
            super();
            this.hr = hr;
        }
        public void run() 
        {
             hr.checkExpiration();
        }

    }

    private synchronized void cancelTimeout() {
        if (expr == null) {
            return;
        }
        try {
            expr.cancel();
        } catch (IllegalStateException ex) {
            // shutting down, nothing to do
            logger.log(Logger.INFO,"Timer shutting down ", ex);
        }
        expr = null;
    }

    public void checkExpiration() {
        synchronized (this) {
            if (state < RUN_PREASSIGNED) {
                // lose the thread
                cancelTimeout();
                destroy();
            } else if (state > RUN_CRITICAL) {
                cancelTimeout();
            }
        }
    }


    public void destroy() {
        tctrl.runnableDestroying(id);
        synchronized (this) {
            if (state >= RUN_DESTROYING) {
                return;
            }
             
            setState(RUN_DESTROYING);
        }
    }

    public void release() {
        synchronized(this) {
            setState(RUN_READY);
        }
        tctrl.releaseRunnable(this);
    }

    protected synchronized void assigned() {
        setState(RUN_ASSIGNED);
        this.notifyAll();
    }

    protected void waitUntilAssigned() {
        synchronized (this) {
            while (state < RUN_ASSIGNED) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    protected void waitUntilDestroyed(long timeout) {
        synchronized (this) {
            if (isCritical()) { // dont need to wait if we arent doing anything
                                // critical
                try {
                    wait(timeout); // XXX-LKS use only destroy lock ???
                } catch (InterruptedException ex) {
                }
            }
        }
    }
    protected boolean isDestroyed() {
        return state == RUN_DESTROYED;
    }

    protected boolean isBusy() {
        return !(state < RUN_PREASSIGNED || state > RUN_CRITICAL);
    }

    protected boolean isCritical() {
        return state == RUN_CRITICAL;
    }

    protected void setCritical(boolean critical) {
        setState(critical?RUN_CRITICAL : RUN_ASSIGNED);
        if (!critical) {
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    protected abstract void process() throws Exception;

    /**
     * This is the method which is called by the thread to process
     * Operations <P>
     *
     *          
     */ 
    public void run() {

        int wt = 0;

        synchronized (this) {
        if (state < RUN_READY)
            setState(RUN_READY);
        }


	while (state < RUN_DESTROYING)  { // check
            if (suspended) {
                if (DEBUG) {
                    logger.log(Logger.DEBUG, "BasicRunnable: "+
                        "suspending Thread [ {0} ]",
                        String.valueOf(id));
                }
                synchronized (this) {
                    if (suspended && tctrl.isSuspended()) {
                        try {
                            this.wait(); // wait until someone cares
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }

            // see if the thread should exit .. and if so .. exit
            if (behavior >= B_DESTROY_THREAD || state >= RUN_DESTROYING) {
                break;
            }

            // see if we are assigned
            if (state < RUN_ASSIGNED) {
                synchronized (this) {
                    if (state < RUN_ASSIGNED)  {
                        try {
                            this.wait();
                        } catch (InterruptedException ex) {
                        } 
                    }
                }
            }
            try {
                process();
            } catch (Exception ex) {
                if (state < RUN_DESTROYING) {
                    tctrl.handleException(ex);
                } else {
                    logger.log(Logger.DEBUG,"Exiting", ex);
                }
                break;
            }
        }

        if (state < RUN_DESTROYING)
            destroy();
        synchronized (this) {
            state = RUN_DESTROYED;
            tctrl.runnableExit(id);
            notifyAll();
        }

    }

    public int getId() {
        return id;
    }

    public int hashCode() {
        return id;
    }

    public boolean equals(Object o) {
        if (! (o instanceof BasicRunnable))
            return false;

        return ((BasicRunnable)o).id == this.id;
    }

}


