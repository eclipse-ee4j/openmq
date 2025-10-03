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

import java.io.*;

public interface Operation {
    int RUNNING = 0;
    int EXITING = 1;
    int DESTROYED = 2;

    int PROCESS_PACKETS_REMAINING = 0;
    int PROCESS_PACKETS_COMPLETE = 1;
    int PROCESS_WRITE_INCOMPLETE = 2;

    boolean isValid();

    /**
     * a boolean which returns "true" if the operation has been destroyed or is in code in which the thread can be destroyed
     * w/o any problems.
     */
    boolean canKill();

    void setCritical(boolean critical);

    boolean waitUntilDestroyed(long time);

    void destroy(boolean goodbye, int greason, String reasonstr);

    void threadAssigned(OperationRunnable runner, int events) throws IllegalAccessException;

    void notifyRelease(OperationRunnable runner, int events);

    void attach(NotificationInfo obj);

    NotificationInfo attachment();

    // ---------------------------------------------

    boolean process(int events, boolean wait) throws IOException;

    void wakeup();

    void suspend();

    void resume();

}
