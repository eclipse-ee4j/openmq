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
 * @(#)Operation.java	1.31 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.io.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;

/**
 */


public interface Operation
{
    public static final int RUNNING = 0;
    public static final int EXITING = 1;
    public static final int DESTROYED = 2;

    public static final int PROCESS_PACKETS_REMAINING = 0;
    public static final int PROCESS_PACKETS_COMPLETE = 1;
    public static final int PROCESS_WRITE_INCOMPLETE = 2;

    public boolean isValid();

    /**
     * a boolean which returns "true" if the operation has
     * been destroyed or is in code in which the thread can be
     * destroyed w/o any problems.
     */
    public boolean canKill();

    public void setCritical(boolean critical);

    public boolean waitUntilDestroyed(long time);

    public void destroy(boolean goodbye, int greason, String reasonstr);

    public void threadAssigned(OperationRunnable runner, int events) 
               throws IllegalAccessException;

    public void notifyRelease(OperationRunnable runner, int events);

    public void attach(NotificationInfo obj);

    public NotificationInfo attachment();
   
    // ---------------------------------------------

    public boolean process(int events, boolean wait) 
        throws IOException;

    public void wakeup();
    public void suspend();
    public void resume();

}

