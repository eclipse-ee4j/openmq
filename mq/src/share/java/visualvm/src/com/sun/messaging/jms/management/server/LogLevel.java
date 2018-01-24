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
 * @(#)LogLevel.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on log levels
 */
public class LogLevel {
    /** 
     * Unknown log level
     */
    public static final String UNKNOWN = "UNKNOWN";

    /** 
     * Log level that will allow messages indicating problems that could cause system failure
     * to be logged.
     */
    public static final String ERROR = "ERROR";

    /** 
     * Log level that will allow alerts that should be heeded (but will not cause system failure)
     * to be logged.
     */
    public static final String WARNING = "WARNING";

    /** 
     * Log level that will allow reporting of metrics and other informational messages
     * to be logged.
     */
    public static final String INFO = "INFO";

    /** 
     * Turn off logging.
     */
    public static final String NONE = "NONE";

    /*
     * Class cannot be instantiated
     */
    private LogLevel() {
    }
}
