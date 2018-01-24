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
 * @(#)SysLog.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util.log;

/**
 * A Java interface to syslog(3C)
 */
public class SysLog {

    // libimqutil.so
    public static final String IMQ_NATIVE_LIBRARY = "imqutil";


/*
 * Facility codes. Taken from /usr/include/sys/syslog.h
 * We keep the same values, although the native code will map these
 * to platform specific values just in case these aren't the same
 * over all versions of UNIX.
 */
public static final int	LOG_KERN    = (0<<3); /* kernel messages */
public static final int	LOG_USER    = (1<<3); /* random user-level messages */
public static final int	LOG_MAI     = (2<<3); /* mail system */
public static final int	LOG_DAEMON  = (3<<3); /* system daemons */
public static final int	LOG_AUTH    = (4<<3); /* security/authorization messages */
public static final int	LOG_SYSLOG  = (5<<3); /* messages generated internally by syslogd */
public static final int	LOG_LPR	    = (6<<3); /* line printer subsystem */
public static final int	LOG_NEWS    = (7<<3); /* netnews subsystem */
public static final int	LOG_UUCP    = (8<<3); /* uucp subsystem */
public static final int	LOG_CRON    = (15<<3); /* cron/at subsystem */
	/* other codes through 15 reserved for system use */
public static final int	LOG_LOCAL0  = (16<<3); /* reserved for local use */
public static final int	LOG_LOCAL1  = (17<<3); /* reserved for local use */
public static final int	LOG_LOCAL2  = (18<<3); /* reserved for local use */
public static final int	LOG_LOCAL3  = (19<<3); /* reserved for local use */
public static final int	LOG_LOCAL4  = (20<<3); /* reserved for local use */
public static final int	LOG_LOCAL5  = (21<<3); /* reserved for local use */
public static final int	LOG_LOCAL6  = (22<<3); /* reserved for local use */
public static final int	LOG_LOCAL7  = (23<<3); /* reserved for local use */

public static final int	LOG_NFACILITIES = 24; /* maximum number of facilities */

/*
 *  Priorities (these are ordered). Take from /usr/include/sys/syslog.h
 * Also known as severity levels.
 */
public static final int	LOG_EMERG   = 0; /* system is unusable */
public static final int	LOG_ALERT   = 1; /* action must be taken immediately */
public static final int	LOG_CRIT    = 2; /* critical conditions */
public static final int	LOG_ERR     = 3; /* error conditions */
public static final int	LOG_WARNING = 4; /* warning conditions */
public static final int	LOG_NOTICE  = 5; /* normal but signification condition */
public static final int	LOG_INFO    = 6; /* informational */
public static final int	LOG_DEBUG   = 7; /* debug-level messages */

/*
 *  Option flags for openlog.
 */
public static final int	LOG_PID	    = 0x01; /* log the pid with each message */
public static final int	LOG_CONS    = 0x02; /* log on the console if errors in
                                              sending */
public static final int	LOG_NDELAY  = 0x08; /* don't delay open */
public static final int	LOG_NOWAIT  = 0x10; /* if forking to log on console,
                                              don't wait() */

    static {
        System.loadLibrary(IMQ_NATIVE_LIBRARY);
    }

    /**
     * Mask one priority. Messages of this priority will not be logger
     */
    public static int setLogMask(int priority) {
        return mySetLogMask(1 << (priority));
    }

    /**
     * Mask all priorities through 'priority'. Messages of this priority and
     * below will not be logged.
     */
    public static int setLogMaskUpTo(int priority) {
        return mySetLogMask((1 << ((priority) + 1)) - 1);
    }

    private static native int mySetLogMask(int mask);

    /**
     * Openlog sets process attributes that affect subsequent calls to
     * syslog(). 
     *
     * @param ident String that is prepended to every message.
     * @param logopt Logging options. Values are a bitwise-inclusive OR
     *               of zero or more of LOG_PID, LOG_CONS, LOG_NDELAY,
     *               LOG_NOWAIT. See syslog(3C) for details.
     * @param facility The default facility to be assigned to all messages
     *                 that do not have an explicit facility already 
     *                 assigned. The initial default facility is LOG_USER.
     *                 See the list of facility codes for valid codes.
     *                 
     * 
     */
    public static native void openlog(String ident, int logopt, int facility);

    /**
     * 
     * Send a message to syslog.
     *
     * @param priority Priority of message to log. Values are formed by 
     *                 ORing together a 'severity' level value and an
     *                 optional 'facility' value. If no facility value
     *                 is specified the current default facility value is
     *                 used. See constants for the list of serverity levels
     *                 and facility codes, and the syslog(3C) man page
     *                 for more details.
     */
    public static native void syslog(int priority, String message);

    /**
     * Close any open file descriptors
     */
    public static native void closelog();

}
