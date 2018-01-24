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
 * @(#)MQTimer.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.util.timer;


import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import com.sun.messaging.jmq.resources.*;
import com.sun.messaging.jmq.util.LoggerWrapper;

public class MQTimer extends java.util.Timer {

    private static boolean DEBUG = false;

    private static SharedResources myrb = SharedResources.getResources();
    private static LoggerWrapper logger = null;

    public static void setLogger(LoggerWrapper l) {
        logger = l;
    }

    /**
     * This object causes the timer's task execution thread to exit
     * gracefully when there are no live references to the Timer object and no
     * tasks in the timer queue.  It is used in preference to a finalizer on
     * Timer as such a finalizer would be susceptible to a subclass's
     * finalizer forgetting to call it.
     */
    private Object mqTimerObject = new Object() {
        protected void finalize() throws Throwable {
            if (DEBUG && logger != null) {
                Exception ex = new RuntimeException("MQTimer.mqtimerObject: finalize");
                ex.fillInStackTrace();
                logger.logInfo("Internal Error: timer canceled ", ex);
            }
        }
    };

    public MQTimer() {
        this(false);
    }

    public MQTimer(boolean isDaemon) {
        super("MQTimer-Thread", isDaemon);
    }

    public void initUncaughtExceptionHandler() {
        TimerTask uehtask = new TimerTask() {
           public void run() {
               Thread thr = Thread.currentThread();
               Thread.UncaughtExceptionHandler ueh = thr.getUncaughtExceptionHandler();
               try {
                   thr.setUncaughtExceptionHandler(new MQTimerUncaughtExceptionHandler(ueh));
               } catch (Exception e) {
                   if (logger != null) {
                       logger.logWarn(myrb.getKString(myrb.W_SET_UNCAUGHT_EX_HANDLER_FAIL,
                                      getClass().getName()), null);
                   }
               }
               cancel();
               
           }
        };
        try {
            schedule(uehtask, new Date());
        } catch (Exception ex) {
            if (logger != null) {
                logger.logWarn(myrb.getKString(myrb.W_SCHEDULE_UNCAUGHT_EX_HANDLER_TASK_FAIL,
                               ex.getMessage()), null);
            }
        }
    } 

    static class MQTimerUncaughtExceptionHandler 
        implements Thread.UncaughtExceptionHandler { 

        Thread.UncaughtExceptionHandler parent = null;

        public MQTimerUncaughtExceptionHandler(Thread.UncaughtExceptionHandler parent) {
            this.parent = parent;
        }
    
        public void uncaughtException(Thread t, Throwable e) { 
            if (logger != null) {
                logger.logSevere(myrb.getKString(myrb.E_UNCAUGHT_EX_IN_THREAD, 
                                e.getMessage(), t.getName()), e);
            }
            parent.uncaughtException(t, e);
        }
    }

    public void cancel() {
        super.cancel();
        if (logger != null && DEBUG) {
            Exception ex = new RuntimeException("MQTimer: cancel");
            ex.fillInStackTrace();
            logger.logInfo("Internal Error: timer canceled ", ex);
        }
    }
}
