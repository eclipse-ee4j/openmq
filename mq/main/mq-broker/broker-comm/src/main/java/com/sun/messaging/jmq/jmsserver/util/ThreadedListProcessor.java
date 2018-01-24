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
 * @(#)ThreadedListProcessor.java	1.10 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util;

import java.lang.Thread;
import java.util.List;
import java.util.ArrayList;
import com.sun.messaging.jmq.util.MQThread;


/**
 * this is a simple class which provides for a class which allows
 * you to automatically thread off tasks if they become too time
 * intensive ..
 * new tasks will be added in order to the task list until all
 * tasks have been processes, then the thread will exit
 */

public abstract class ThreadedListProcessor implements Runnable
{
    private Thread thr = null;
    private List list = null;
    private String name = null;

    protected static final long DEFAULT_TIME=10000;

    public ThreadedListProcessor() {
        this(null);
    }

    public ThreadedListProcessor(String name) {
        if (name == null) {
            this.name = this.toString();
        } else {
            this.name = name;
        }
   }

    /**
     * lifetime is the length of time the thread will live
     * if a new object is not added to the process list
     */
    protected long getLifeTime() {
        return DEFAULT_TIME;
    }

    abstract protected boolean startThreading(ThreadedTask e);
    abstract protected void process(ThreadedTask q);


    protected synchronized final void add(ThreadedTask q) {
        if (thr == null && startThreading(q)) {
            if (list == null)
                list = new ArrayList();
            thr = new MQThread(this, name);
            thr.start();
        }
        if (thr != null) {
            list.add(q);
            notifyAll();
        } else {
            process(q);
        }
    }

    public synchronized void clear() {
        if (list != null)
            list.clear();
         thr = null;
    }


    public void run() {
        long time = getLifeTime();
        while (true) {
            ThreadedTask entry = null;
            synchronized (this) {

                if (thr == null) {
                   // we are done, exit the thread
                   break;
                }
                if (list.size() == 0) {
                    try {
                        wait(time);
                    } catch (InterruptedException ex) {
                    }
                }
                if (list.size() <= 0) {
                    // OK .. the thread has been inactive for a while,
                    // let it do
                    thr = null;
                    break;
                }
                entry = (ThreadedTask)list.remove(0);
                if (entry != null) {
                     process(entry);
                     Thread.currentThread().yield();
                }
           }
            
        }
    }

}
