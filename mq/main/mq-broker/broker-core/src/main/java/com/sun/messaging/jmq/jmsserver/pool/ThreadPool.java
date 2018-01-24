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
 * @(#)ThreadPool.java	1.57 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.pool;

import java.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.util.log.Logger;


/**
 * This class implements a simple thread pool.
 *
 * This thread pool has a minimum and maximum number of threads.
 * At creation, the minimum number of threads are created and
 * "BasicRunnable" objects are assigned to the threads.
 *
 * If there are no available threads (thread count == max), 
 * the system will either wait until a thread is available OR
 * throw an exception.
 *
 * As the load on the system decreases, if the thread count is greater
 * than <B>min</b>, the threads will exit IF they have not been needed after
 * a certain amount of time until the system is back down to <b>min</b>
 * <P>
 *
 * @see Operation
 * @see BasicRunnable
 *
 */
public class ThreadPool
{
// sync scheme:
//      use lock for objects & counts
//      use current -> for current
//      use current -> for availIndx
//      use available -> for available
//      use this -> access to lists/wait->notify
//
// make copies for resume, destroy

    //public Object lock = new Object(); // indexes and counts


    public boolean destroyed = false;

    private boolean in_destroy = false;

    private static boolean DEBUG = false;

    protected Logger logger  = Globals.getLogger();


    RunnableFactory runfac = null;

    /**
     * threads which have exited so their index can be reused
     * threads exit when either they timeout OR a problem occurs
     */
    LinkedHashSet availIndx = null;


    /**
     * list of all current threads/runnables
     */
    ArrayList current = null; // XXX-LKS - may want to use a different list type


    /**
     * list of waiting runnables 
     */
    LinkedList available = null;

    /**
     * current indx for next thread created 
     */
    int nextThreadId = 0;


    /**
     * thread group for this thread pool
     */
    protected ThreadGroup tgroup = null;


    /**
     * minimum number of threads
     */
    protected int min;

    /**
     * maximum number of threads
     */
    protected int max;

    /**
     * name of the thread
     */
    protected String name = null;

    protected int current_count = 0;

    /**
     * flag which indicates if all threads should
     * "suspend" themselves. We dont want to truely
     * suspend the threads because that is considered
     * unsafe 
     */
    protected boolean notActive = true;


    protected int priority = Thread.NORM_PRIORITY;


    public boolean isValid() {
        return !destroyed && !in_destroy;
    }

    public void setPriority(int p) {
        this.priority = p;
    }

    /**
     * retrieve the minimum # of threads 
     */
    public synchronized int getMinimum()
    {
        return min;
    }

    /**
     * retrieve the maximum # of threads
     */
    public synchronized int getMaximum() {
        return max;
    }

    /**
     * set the minumum # of threads
     */
    public synchronized void setMinimum(int num) 
        throws IllegalArgumentException 
    {
        setMinMax(num, max);
    }

    /**
     * set the maximum # of threads
     */
    public synchronized void setMaximum(int num) 
        throws IllegalArgumentException 
    {
        setMinMax(min, num);
    }

    public synchronized Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("min", Integer.valueOf(min));
        ht.put("max", Integer.valueOf(max));
        ht.put("name", name);
        ht.put("current_count", Integer.valueOf(current_count));
        ht.put("nextThreadId", Integer.valueOf(nextThreadId));
        ht.put("notActive", Boolean.valueOf(notActive));
        ht.put("destroyed", Boolean.valueOf(destroyed));
        ht.put("in_destroy", Boolean.valueOf(in_destroy));
        ht.put("priority", Integer.valueOf(priority));
        if (current != null)  {
            ht.put("currentCnt", Integer.valueOf(current.size()) );
            Vector v = new Vector();
            Iterator itr = current.iterator();
            while (itr.hasNext()) {
                BasicRunnable runner = (BasicRunnable)itr.next();
                if (runner == null)
                    v.add("Runner is null");
                else
                    v.add(runner.getDebugState());
            }
            ht.put("current", v);
        } else {
            ht.put("currentCnt", "null" );
        }

        if (availIndx != null) {
            ht.put("availIndxCnt", Integer.valueOf(availIndx.size()) );
            Vector v = new Vector();
            Iterator itr = availIndx.iterator();
            while (itr.hasNext()) {
                Integer index = (Integer)itr.next();
                v.add(index);
            }
            ht.put("availIndx", v);
        } else {
            ht.put("availIndxCnt", "null" );
        }
        if (available != null) {
            ht.put("availableCnt", Integer.valueOf(available.size()) );
            Vector v = new Vector();
            Iterator itr = available.iterator();
            while (itr.hasNext()) {
                BasicRunnable runner = (BasicRunnable)itr.next();
                v.add(runner.getDebugState());
            }
            ht.put("available", v);
        } else {
            ht.put("availableCnt", "null" );
        }
        return ht;
    }

    /**
     * @return int[0] - min; int[1] - max; -1 no change
     */
    public synchronized int[] setMinMax(int newmin, int newmax)
                                  throws IllegalArgumentException {
        int[] rets = new int[2];
        rets[0] = -1;
        rets[1] = -1;

        if (in_destroy) {
            return rets; // nothing to do
        }
        if (newmin == -1) {
            newmin = min;
        }
        if (newmax == -1) {
            newmax = max;
        }

        if (newmin > newmax) {
            String[] args = { name, String.valueOf(newmin),
                              String.valueOf(newmax) };
            String emsg =  Globals.getBrokerResources().getKString(
                BrokerResources.W_THREADPOOL_MIN_GT_MAX_SET_MIN_TO_MAX, args);
            logger.log(logger.WARNING, emsg);
            newmin = newmax;
        }

        // OK first deal w/ destroying any threads over min

        int count = current.size();

        // now set the destroy behavior on existing threads
        // greater than max

        for (int i = newmax; i < max && i < count; i ++)  {
            BasicRunnable runner = (BasicRunnable)current.get(i);
            if (runner == null) continue;
            runner.setThreadBehavior(BasicRunnable.B_DESTROY_THREAD);
        }

        // now turn off timeout behavior on threads above newmin 
        // && below min      
        for (int i = newmin; i > min && i > 0 && i < count; i --)  {
            BasicRunnable runner = (BasicRunnable)current.get(i);
            if (runner == null) continue;
            runner.setThreadBehavior(BasicRunnable.B_STAY_RUNNING);
        }

        // now turn on timeout behavior on threads > newmin      
        for (int i = newmin; i < max && i < count; i ++)  {
            BasicRunnable runner = (BasicRunnable)current.get(i);
            if (runner == null) continue;
            runner.setThreadBehavior(BasicRunnable.B_TIMEOUT_THREAD);
        }

        // finally, redo the list of available indexes
        LinkedHashSet list = new LinkedHashSet();
        for (int i = 0; i < count; i ++)  {
            Object obj = current.get(i);
            if (obj == null) // empty
                list.add(Integer.valueOf(i));
        }
        // set the values
        if (min != newmin) {
            min = newmin;
            rets[0] = min;
        } 
        if (max != newmax) {
            max = newmax;
            rets[1] = max;
        }
        availIndx = list;

        return rets;
    }

    public synchronized void debug() {
        StringBuffer info = new StringBuffer();
        info.append("\n" 
                      + "--------------------------------------------\n"
                      + " DUMPING THREAD POOL " + this + "\n"
                      + "--------------------------------------------\n"
                      + "[min, max] = [" + min + "," + max + "]\n"
                      + "---- threads ----\n"
                      + "#\tAvailable\thash\tRunner\n");
        for (int i = 0; i < max; i ++) {
            BasicRunnable runner = null;
            if (i < current.size()) {
                runner = (BasicRunnable)current.get(i);
            }
            if (runner != null) {
                info.append(i + "\t" + available.contains(runner) + "\t"
                     + Long.toHexString(runner.hashCode())
                     + "\t" +runner  +"\n");
            } else {
                continue;
            }
        }
        info.append("--------------------------------------------\n"
                + "DONE DUMPING THREAD POOL\n"
                + "--------------------------------------------\n\n");
        logger.log(Logger.DEBUG, info.toString());
    }

    /**
     * returns the current number of "threads" in the thread pool
     */
    public synchronized int getThreadNum() {
        return current_count;
    }

    /**
     * returns the # of assigned threads
     */
    public synchronized int getAssignedCnt() {
        return current_count - available.size() - availIndx.size();
    }

    /**
     * Create a thread pool of the passed in name with
     * a minimum and maximum number of threads.
     *
     * @param name the name of the thread pool
     * @param min the minimum number of threads
     * @param max the maximum number of threads
     */
    public ThreadPool(String name, int min, int max,  RunnableFactory runfac) {
        if (DEBUG) {
            String args[] = {name, String.valueOf(min), String.valueOf(max)};
            logger.log(Logger.DEBUG,
                "ThreadPool: Creating Thread Pool({0}) = [ {1}, {2} ]", 
                 args);
        }
	this.min = min;
        this.max  = max;
        tgroup = new MyThreadGroup("ThreadPool(" +name+ ")");
	this.name = name;
        availIndx = new LinkedHashSet();
        current = new ArrayList();
        available = new LinkedList();
        this.runfac = runfac;
        this.current_count = 0;
        this.nextThreadId = 0;

    }

    /**
     * start threads operation. By default, when the thread pool
     * is created,  the notActive flag is true, so the threads
     * are not operating
     */
    public  void start() {
	// for now .. does the same as resume
        resume();
    }

    /**
     * retrieve the state of the notActive flag
     *
     * @return the notActive flag
     */
    public synchronized boolean isSuspended() {
	return notActive;
    }

// suspending doesnt happen immediately .. the current operation
// WILL complete before the pool is suspended
//

    /**
     * suspend threads operation.<P>
     * Threads will <B>not</B> be immediately suspended, instead
     * they will go into a "wait" state as soon as they finish
     * processing the current operation.
     *
     * This method is not synchronized, because we dont really care
     * what the state of the flag is at a given time .. it will always
     * be picked up on the next iteration of the run method.
     */
    public void suspend() {
        if (DEBUG) {
            logger.log(Logger.DEBUG, 
                "ThreadPool:  SUSPENDING ThreadPool({0})", 
                 name);
        }
        if (in_destroy) {
            return; // nothing to do
        }
        notActive = true;

        ArrayList copy = null; 
        synchronized (this) {
            copy = new ArrayList(current);
        }

        for (int i = 0; i < copy.size(); i ++) {
            BasicRunnable runner = (BasicRunnable)copy.get(i);
            if (runner != null) {
                runner.suspend();
            }
        }            
    }



    /**
     * resume threads operation.<P>
     * Threads will notified (which will wake them up) and then
     * the threads will continue operation
     */
    public  void resume() {
        if (DEBUG) {
            logger.log(Logger.DEBUG, 
                "ThreadPool:  RESUMING ThreadPool({0})", name);
        }
        if (in_destroy) {
            return; // nothing to do
        }
        notActive = false;

        ArrayList copy = null; 
        synchronized (this) {
            copy = new ArrayList(current);
        }
        for (int i = 0; i < copy.size(); i ++) {
            BasicRunnable runner = (BasicRunnable)copy.get(i);
            if (runner != null) {
                runner.resume();
            }
        }            
        synchronized (this) {
            notifyAll(); // wake everyone up
        }
    }

    /**
     * suspends or resume threads operation.<P>
     * This just calls the appropriate suspend() or resume()
     * operation.
     * @see #suspend
     * @see #resume
     *
     * @param susp true if the pool should be suspended, false
     *        if the thread pool should be resumed
     */
    public void setSuspended(boolean susp) {
        if (susp)
            suspend();
        else
            resume();
    }

// XXX

    /**
     * get a runnable to use (its assigned when returned)
     */
    public  BasicRunnable getAvailRunnable(boolean wait) {

        try {

        BasicRunnable runner = null;
        String how = null;
        while (!in_destroy && !destroyed) { // wait until something available
            synchronized (this) {
                if (!available.isEmpty()) {
                     how = "availableList";
                    runner = (BasicRunnable)available.removeFirst();
                } else if (!availIndx.isEmpty()) {
                    how = "existing index";
                    Iterator itr = availIndx.iterator();
                    int indx = ((Integer)itr.next()).intValue();
                    itr.remove();
                    runner = createNewThread(indx);
                } else if (nextThreadId < max) {
                    how = "new thread"; 
                    int indx = nextThreadId;
                    nextThreadId++;
                    runner = createNewThread(indx);
                } else {
                    return null; // nothing available
                }
            }
            if (runner == null) {
                logger.log(Logger.ERROR,
                    BrokerResources.E_INTERNAL_BROKER_ERROR, 
                    how);
                continue;
            }
            synchronized(runner) {
                if (runner.available()) {
                    runner.setState(BasicRunnable.RUN_PREASSIGNED);
                } else {  // getting destroyed
                    runner = null;
                }
            }
            if (runner != null) {
                break;
            }
            synchronized (this) {
                if (wait) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        return runner;

        } catch (Exception ex) {
        logger.logStack(logger.WARNING, ex.getMessage(), ex);
        return null;
        }
    }


    /**
     * create a new thread and add it to the thread list
     *
     * @throws ArrayIndexOutOfBoundsException if max threads has been reached
     */
    private synchronized BasicRunnable createNewThread(
            int indx) 
        throws ArrayIndexOutOfBoundsException
    {
        if (indx >= max)
            throw new ArrayIndexOutOfBoundsException( 
                Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION, "Too many threads " + 
                current_count + "," + max));
        BasicRunnable runner = null;
        if (indx < current.size()) { // get current if possible
            runner = (BasicRunnable)current.get(indx);
        }
        if (runner == null) {
            runner = runfac.getRunnable(indx, this);
            if (current.size() <= indx) {
                current.add(indx, runner);
            } else {
                current.set(indx, runner);
            }
        }

        runner.setState(BasicRunnable.RUN_READY);
        Thread thr = new MQThread(tgroup, runner, "Thread-"+name +"["
                +indx + "]");
        thr.setPriority(priority);
        if (indx >= min) {
            runner.setThreadBehavior(BasicRunnable.B_TIMEOUT_THREAD);
        }
        current_count ++;
        thr.start();
        return runner;
    }


    public synchronized void runnableDestroying(int indx) {
        if (indx >= current.size()) {
           logger.log(Logger.ERROR,
               BrokerResources.E_INTERNAL_BROKER_ERROR, 
               " attempting to destroy unknown thread  " + indx);
            debug();
           return;
        }
        BasicRunnable r = (BasicRunnable)current.get(indx);
        current.set(indx, null);
        available.remove(r);
    }

    public synchronized void runnableExit(int indx) {
        if (in_destroy) {
            return; // nothing to do
        }
        current_count --;
        if (indx < min) {
            // recreate
            BasicRunnable runner = createNewThread(indx);
            available.add(runner);
        } else { // put on possibly recreate list
            if (indx > max) { 
                // ignore
            } else { // stick on list
                availIndx.add(Integer.valueOf(indx));
            }
        }
        notifyAll();
    }
    
    public  synchronized void releaseRunnable(BasicRunnable run) {
        if (in_destroy) {
            return; // nothing to do
        }

        if (run == null) {
            logger.log(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR,
                    "null basic runnable " + run);
            return;
        }
        // XXX - add logic for handling min/max
        int indx = run.getId();
        if (indx < min) { // put at front of list

            // XXX - DEBUG
            if (!available.contains(run)) {
                available.addFirst(run);
            }
        } else { // put at back of list
            if (!available.contains(run)) {
                available.addLast(run);
            }
        }
        notifyAll();
    }

    /**
     * cleanly stops threads and destroys
     */
    public void destroy() {
        ArrayList copy = null; 
        synchronized (this) {
           in_destroy = true; // prevents new threads
           copy = new ArrayList(current);
        }
        for (int i = 0; i < copy.size(); i ++) {
            BasicRunnable runner = (BasicRunnable)copy.get(i);
            if (runner != null) {
                runner.destroy();
            }
        }         
    }


    public void waitOnDestroy(long timeout)
    {
        destroy();


        ArrayList copy = null; 
        synchronized (this) {
            copy = new ArrayList(current);
        }


        for (int i = 0; i < copy.size(); i ++) {
            BasicRunnable runner = (BasicRunnable)copy.get(i);
            if (runner == null) {
                continue;
            }
            if (runner.isBusy()) {
                runner.waitOnDestroy(timeout);
            }
            if (!runner.isDestroyed() && runner.isCritical())
                   logger.log(Logger.WARNING, 
                            BrokerResources.W_CANNOT_DESTROY_OPERATION,
                            runner, String.valueOf(timeout));
        }         
        destroyed = true;
        synchronized (this) {
            notifyAll();
        }
    }


    public void handleException(Throwable thr) {
        logger.logStack(Logger.WARNING,
            BrokerResources.E_INTERNAL_BROKER_ERROR, 
            "Unexpected Exception or Error", thr);
    }

    /**
     * this subclass of ThreadGroup handles making sure any exception
     * is correctly handled
     *
     * IF the exception is thread death .. the system will decide whether
     * or not to recreate the thread
     *
     * the exception/Error will always be logged
     *
     */
    public static class MyThreadGroup extends ThreadGroup
    {
        public MyThreadGroup(String name) {
            super(name);
        }
        public void uncaughtException(Thread t, Throwable thr) {
            // notify ThreadPool that the thread is gone
            Globals.handleGlobalError(thr, "Unexpected thread pool error");
        }
    }


}


