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

package com.sun.messaging.jmq.util.timer;

public interface TimerEventHandler {
    /**
     * @return the time (millisecs) for next run; 0 if periodic task or wait for wakeup notifications
     */
    long runTask();

    /**
     * returns true if the method actually did something with the error
     */
    void handleOOMError(Throwable e);

    void handleLogInfo(String msg);

    void handleLogWarn(String msg, Throwable e);

    void handleLogError(String msg, Throwable e);

    void handleTimerExit(Throwable e);
}
