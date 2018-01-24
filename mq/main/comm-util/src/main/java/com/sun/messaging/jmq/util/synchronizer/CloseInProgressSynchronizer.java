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
 */ 

package com.sun.messaging.jmq.util.synchronizer;

/**
 *
 */
public class CloseInProgressSynchronizer 
{

     private static final long WAIT_INTERVAL = 15*1000L; //15 seconds

     //whether it's closed for operation or not
     private boolean closed = false;
     private Object closedLock = new Object();

     //number of operations in progress
     private int inprogressCount = 0;
     private Object inprogressLock = new Object();

     private Object logger = null;

     public CloseInProgressSynchronizer(Object logger) {
         this.logger = logger;
     }

     public void reset() {

         synchronized(closedLock) {
             closed = false; 
             closedLock.notifyAll();
         }
         synchronized (inprogressLock) {
            inprogressCount = 0;
            inprogressLock.notifyAll();
         }
     }

     /**
      * Set closed to true so that no new operation allowed
      */
     public void setClosedAndWait(CloseInProgressCallback cb, String waitlogmsg) {
         synchronized (closedLock) {
             closed = true;
         }

         if (cb != null) {
             cb.beforeWaitAfterSetClosed();
         }

        synchronized (inprogressLock) {
            inprogressLock.notifyAll();
            if (inprogressCount == 0) {
                return;
            }

            logInfo(waitlogmsg);

            long currtime = System.currentTimeMillis();
            long lastlogtime = currtime;

            while (inprogressCount > 0) {
                try {
                    if ((currtime - lastlogtime) >= WAIT_INTERVAL) {
                        logInfo(waitlogmsg);
                        lastlogtime = currtime;
                    }

                    inprogressLock.wait(WAIT_INTERVAL);

                    if (inprogressCount > 0) {
                        currtime = System.currentTimeMillis();
                    }
                } catch (Exception e) {}
            }
        }
    }

     /**
      * @param timeout in seconds, 0 means no timeout 
      *
      * @throws java.util.concurrent.TimeoutException if wait timed out
      * @throws InterruptedException if wait interrupted
      */
    public void setClosedAndWaitWithTimeout(CloseInProgressCallback cb,
                                            int timeout, String waitlogmsg)
                                            throws InterruptedException, 
                                            java.util.concurrent.TimeoutException {
        if (timeout <= 0) {
            setClosedAndWait(cb, waitlogmsg);
            return;
        }

        synchronized(closedLock) {
            closed = true;
        }

        if (cb != null) {
            cb.beforeWaitAfterSetClosed();
        }

        long maxwait = timeout * 1000L;
        long waittime = maxwait;

        synchronized (inprogressLock) {
            inprogressLock.notifyAll();
            if (inprogressCount == 0) {
                return;
            }

            logInfo(waitlogmsg);

            long currtime = System.currentTimeMillis();
            long precurrtime = currtime;
            long lastlogtime = 0L;
            long totalwaited = 0L;

            while (inprogressCount > 0 && (waittime > 0)) {
                try {
                    if ((currtime - lastlogtime) > WAIT_INTERVAL) {
                        logInfo(waitlogmsg);
                        lastlogtime = currtime;
                    }

                    inprogressLock.wait(waittime);
                    precurrtime = currtime;
                    currtime = System.currentTimeMillis();
                    totalwaited += ((currtime - precurrtime) > 0 ?
                                    (currtime - precurrtime):0);

                    if (inprogressCount > 0) {
                        waittime = maxwait - totalwaited;
                    }
                } catch (InterruptedException e) {
                    throw e;
                }
            }
            if (inprogressCount > 0 ) {
                throw new java.util.concurrent.TimeoutException("timeout");
            }
        }
    }


    /**
     * @throws IllegalAccessException if closed
     */
    public void checkClosedAndSetInProgress()
    throws IllegalAccessException {

        synchronized (closedLock) {
            if (closed) {
                throw new IllegalAccessException("closed");
            } 
            setInProgress(true);
        }
    }

    public void setInProgress(boolean flag) {
        synchronized (inprogressLock) {
            if (flag) {
                inprogressCount++;
            } else {
                inprogressCount--;
            }

            if (inprogressCount == 0) {
               inprogressLock.notifyAll();
            }
        }
    }

    /**
     * @timeout timeout in seconds, 0 means no tmieout
     * @throws IllegalAccessException if closed
     * @throws java.util.concurrent.TimeoutException if timeout 
     * @throws InterruptedException if wait interrupted
     */
    public void checkClosedAndSetInProgressWithWait(int timeout, String waitlogmsg)
    throws IllegalStateException, InterruptedException,
    java.util.concurrent.TimeoutException {

        synchronized (closedLock) {
            if (closed) {
                throw new IllegalStateException("closed");
            } 
            setInProgressWithWait(timeout, waitlogmsg);
        }
    }

    /**
     * @param flag
     * @param timeout
     * @throws IllegalAccessException if closed
     * @throws java.util.concurrent.TimeoutException if timeout 
     * @throws InterruptedException if wait interrupted
     */
    private void setInProgressWithWait(int timeout, String waitlogmsg)
    throws IllegalStateException, InterruptedException,
    java.util.concurrent.TimeoutException {

        synchronized(inprogressLock) {
            if (inprogressCount == 0) {
                inprogressCount++;
                return;
            }

            long maxwait = timeout * 1000L;
            long waittime = ((maxwait <= 0 || maxwait > WAIT_INTERVAL) ?
                              WAIT_INTERVAL : maxwait);

            logInfo(waitlogmsg);
            long currtime = System.currentTimeMillis();
            long precurrtime = currtime;
            long lastlogtime = 0L;

            long totalwaited = 0L;

            while (inprogressCount > 0 && !closed && (waittime > 0)) {

                if ((currtime - lastlogtime) > WAIT_INTERVAL) {
                    logInfo(waitlogmsg);
                    lastlogtime = currtime;
                }

                inprogressLock.wait(waittime);
                precurrtime = currtime;
                currtime = System.currentTimeMillis();
                totalwaited += ((currtime - precurrtime) > 0 ?
                                (currtime - precurrtime):0);
                if (inprogressCount > 0) {
                    waittime = (maxwait <= 0 ? 
                                WAIT_INTERVAL : (maxwait - totalwaited));
                }
                if (waittime > WAIT_INTERVAL) {
                     waittime = WAIT_INTERVAL;
                }
            }
            if (closed) {
                throw new IllegalStateException("closed");
            }
            if (inprogressCount == 0) {
                inprogressCount++;
                return;
            }
            throw new java.util.concurrent.TimeoutException("timeout");
        }
    }

    public boolean isClosed() {
        synchronized (closedLock) {
            return closed;
        }
    }

    private void logInfo(String msg) {
        if (logger instanceof com.sun.messaging.jmq.util.LoggerWrapper) {
            ((com.sun.messaging.jmq.util.LoggerWrapper)logger).logInfo(msg, null);
        } else if (logger instanceof java.util.logging.Logger) {
            ((java.util.logging.Logger)logger).log(
             java.util.logging.Level.INFO, msg);
        } else {
            System.out.println("INFO: "+msg);
        }
    }

}
