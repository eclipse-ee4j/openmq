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

package com.sun.messaging.jmq.util;

public class MQThreadGroup extends ThreadGroup
{
    private LoggerWrapper logger = null;
    private String uncaughtExMsg = null;

    public MQThreadGroup(String name, LoggerWrapper logger, String threadUncaughtExWarnMsg) {
        super(name);
        this.logger = logger;
        this.uncaughtExMsg = threadUncaughtExWarnMsg;
    }

    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        if (logger == null) {//XXX
            System.out.println("WARNING: Uncaught exception in thread ["+t+"]:"+e);
        } else {
            logger.logWarn(uncaughtExMsg+" ["+t+"]", e);
        }
    }

}

