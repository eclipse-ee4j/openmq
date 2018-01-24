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

package com.sun.messaging.jmq.util.timer;

public class WakeupableTimer implements Runnable 
{
    private static boolean DEBUG = false;

    private String name = null;
    private long mynexttime = 0L;
    private long nexttime = 0L;
    private long repeatinterval = 0;
    private Thread thr = null;
    private boolean valid = true;
    private boolean wakeup = false;
    private String startString = null;
    private String exitString = null;
    private TimerEventHandler handler = null;


    /**
     * @param delaytime initial delay in millisecs
     * @param repeatInterval wait time in milliseconds to repeat the task;
     *        if 0, wait for wakeup notifications (see also TimerEventHandler.runTask)
     */
    public WakeupableTimer(String name, TimerEventHandler handler, 
                           long delaytime, long repeatInterval,
                           String startString, String exitString) {
        this.name = name;
        this.mynexttime = delaytime + System.currentTimeMillis();
        this.repeatinterval = repeatInterval;
        this.startString = startString;
        this.exitString = exitString;
        this.handler = handler;

        thr = new Thread(this, name);
        thr.start();
    }

    public boolean isTimerThread(Thread t) {
        if (thr == null) {
            return false;
        }
        return (t == thr);
    }

    public synchronized void wakeup() {
        wakeup = true;
        notifyAll();
    }

    /**
     * @param time next time to run task
     */
    public synchronized void wakeup(long time) {
        nexttime = time;
        wakeup = true;
        notifyAll();
    }

    public void cancel() {
        valid = false;
        wakeup();
        thr.interrupt();
    }

    public void run() {
        try {

        handler.handleLogInfo(startString);

        long time = System.currentTimeMillis();
        long waittime = mynexttime - time;
        if (waittime < 0L) {
            waittime = 0L;
        }
        boolean nowaitOn0 = true;
        while (valid) {
            try {

            synchronized(this) {
                while (valid && !wakeup &&
                       !(waittime == 0L && nowaitOn0)) {
                    if (DEBUG) {
                        handler.handleLogInfo(name+" run(): before wait("+waittime+"), valid="+
                            valid+ ", wakeup="+wakeup+", nowaitOn0="+nowaitOn0);
                    }
                    if (nowaitOn0) {
                        nowaitOn0 = false;
                    }
                    try {
                        this.wait(waittime);
                    } catch (InterruptedException ex) {
                    }
                    if (valid && !wakeup && waittime != 0L) {
                        time = System.currentTimeMillis();
                        waittime = mynexttime - time;
                        if (waittime <= 0L) {
                            waittime = 0L;
                            if (repeatinterval > 0L) {
                                waittime = repeatinterval;                              
                            } 
                            break;
                        }
                    }
                }
                if (!valid) {
                    break;
                }
                wakeup = false;

            } //synchronized

            if (DEBUG) {
                handler.handleLogInfo(name+" runTask "+handler.getClass().getName());
            }

            boolean asrequested = false; 
            mynexttime = handler.runTask();
            if (mynexttime > 0L) {
                nowaitOn0 = true;
                asrequested = true;
            }
            if (DEBUG) {
                handler.handleLogInfo(name+" completed run "+
                    handler.getClass().getName()+" with return "+mynexttime);
            }
            time = System.currentTimeMillis();
            if (mynexttime == 0L) {
                mynexttime = time + repeatinterval;
            }
            synchronized(this) {
                if (DEBUG) {
                    handler.handleLogInfo(name+" run() after runTask(), nexttime="+
                                 nexttime+", mynexttime="+mynexttime+", time="+time);
                }
                if (nexttime > 0L && nexttime < mynexttime) {
                    mynexttime = nexttime;
                    nowaitOn0 = true;
                    asrequested = true;
                }
                nexttime = 0L;
            }
            waittime = mynexttime - time;
            if (waittime < 0L) {
                waittime = 0L;
            }
            if (waittime == 0L && !asrequested) {
                nowaitOn0 = false;
            }

            } catch (Throwable e) {
            handler.handleLogWarn(e.getMessage(), e);
            if (e instanceof OutOfMemoryError) {
                handler.handleOOMError(e);
            }
            }
        } //while

        handler.handleLogInfo(exitString);

        } catch (Throwable t) {
        handler.handleLogError(exitString, t);
        handler.handleTimerExit(t);
        }
    }
}
