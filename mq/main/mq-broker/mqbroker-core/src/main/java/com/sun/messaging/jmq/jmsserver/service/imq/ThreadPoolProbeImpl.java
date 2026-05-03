/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.grizzly.threadpool.ThreadPoolInfo;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;

import com.sun.messaging.jmq.util.log.Logger;

public final class ThreadPoolProbeImpl implements ThreadPoolProbe {
    private final boolean DEBUG;
    private final Logger logger;
    private final String pname;
    private final AtomicInteger counter;

    public ThreadPoolProbeImpl(boolean DEBUG, Logger logger, String pname, AtomicInteger counter) {
        this.DEBUG = DEBUG;
        this.logger = logger;
        this.pname = pname;
        this.counter = counter;
    }

    @Override
    public void onThreadPoolStartEvent(ThreadPoolInfo threadPool) {
        if (DEBUG) {
            logger.log(logger.INFO, "ThreadPool[" + pname + "] started, " + threadPool);
        }
    }

    @Override
    public void onThreadPoolStopEvent(ThreadPoolInfo threadPool) {
        if (DEBUG) {
            logger.log(logger.INFO, "ThreadPool[" + pname + "] stopped");
        }
    }

    @Override
    public void onThreadAllocateEvent(ThreadPoolInfo threadPool, Thread thread) {
        int cnt = counter.getAndIncrement();
        if (DEBUG) {
            logger.log(logger.INFO, "ThreadPool[" + pname + "] thread allocated[" + (++cnt) + "]");
        }
    }

    @Override
    public void onThreadReleaseEvent(ThreadPoolInfo threadPool, Thread thread) {
        int cnt = counter.getAndDecrement();
        if (DEBUG) {
            logger.log(logger.INFO, "ThreadPool[" + pname + "] thread released[" + (--cnt) + "]");
        }
    }

    @Override
    public void onMaxNumberOfThreadsEvent(ThreadPoolInfo threadPool, int maxNumberOfThreads) {
        if (DEBUG) {
            logger.log(logger.INFO, "ThreadPool[" + pname + "] threads max " + maxNumberOfThreads + " reached");
        }
    }

    @Override
    public void onTaskQueueEvent(ThreadPoolInfo threadPool, Runnable task) {
        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "ThreadPool[" + pname + "] task queue event:" + task);
        }
    }

    @Override
    public void onTaskDequeueEvent(ThreadPoolInfo threadPool, Runnable task) {
        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "ThreadPool[" + pname + "] task dequeue event:" + task);
        }
    }

    @Override
    public void onTaskCompleteEvent(ThreadPoolInfo threadPool, Runnable task) {
        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "ThreadPool[" + pname + "] task complete event:" + task);
        }
    }

    @Override
    public void onTaskQueueOverflowEvent(ThreadPoolInfo threadPool) {
        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "ThreadPool[" + pname + "] task queue overflow event");
        }
    }

    @Override
    public void onTaskCancelEvent(ThreadPoolInfo threadPool, Runnable task) {
        if (DEBUG) {
            logger.log(logger.DEBUGHIGH, "ThreadPool[" + pname + "] task canceled event");
        }
    }
}