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
 * @(#)BrokerExitCode.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util;

/**
 * This class defines the exit codes the broker uses to indicate
 * exit error condition.
 *
 * Note that 128+signal is used by the JVM (e.g. 129 (SIGHUP),
 * 130 (SIGINT), etc). And the restart logic of the broker checks for
 * 129 (SIGHUP), 130 (SIGINT), 143 (SIGTERM) and 255.
 * When defining new exit codes, avoid overlapping with those.
 * 
 */
public class BrokerExitCode {

    public static final int NORMAL = 0;
    public static final int ERROR = 1;

    // exit codes used to indicate error conditions for
    // the -remove instance option
    public static final int INSTANCE_NOT_EXISTS			= 10;
    public static final int INSTANCE_BEING_USED			= 11;
    public static final int NO_PERMISSION_ON_INSTANCE		= 12;
    public static final int PROBLEM_REMOVING_PERSISTENT_STORE	= 13;
    public static final int IOEXCEPTION				= 14;

    // not to be instantiated
    private BrokerExitCode() {}
}

