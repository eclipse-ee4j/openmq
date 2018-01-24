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
 * @(#)Rlimit.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util;


/**
 * Native class that provides an interface to Unix getrlimit(2).
 */
public class Rlimit {

    private static final String IMQ_NATIVE_LIBRARY = "imqutil";

    public final static int RLIMIT_CPU      = 0;    // cpu time in milliseconds
    public final static int RLIMIT_FSIZE    = 1;    // maximum file size
    public final static int RLIMIT_DATA     = 2;    // data size
    public final static int RLIMIT_STACK    = 3;    // stack size
    public final static int RLIMIT_CORE     = 4;    // core file size
    public final static int RLIMIT_NOFILE   = 5;    // file descriptors
    public final static int RLIMIT_VMEM     = 6;    // maximum mapped memory
    public final static int RLIMIT_AS       = RLIMIT_VMEM;
    public final static int RLIMIT_NLIMITS  = 7;    // number of resource limits

    public final static long RLIM_INFINITY  = -3;

    private static boolean loadFailed = true;

    static {
        try {
            System.loadLibrary(IMQ_NATIVE_LIBRARY);
            loadFailed = false;
        } catch (Throwable ex) {
            loadFailed = true;
        }
    }

    private static native Limits nativeGetRlimit(int resource);

    /**
     * Get Unix system resource limits.
     *
     * @param resource Resource to get limits for. Must be one of RLIMIT_*
     * constants.
     *
     * @return The soft and hard limits for the resource
     */
    public static Limits get(int resource) throws
        UnsupportedOperationException, IllegalArgumentException {

        if (loadFailed) {
            throw new UnsupportedOperationException();
        }

        if (resource < RLIMIT_CPU || resource == RLIMIT_NLIMITS) {
            throw new IllegalArgumentException(String.valueOf(resource));
        }

        Limits l = null;

        try {
            l = nativeGetRlimit(resource);
        } catch (Throwable e) {
            throw new UnsupportedOperationException(e.toString());
        }

        return l;
    }

    /*
     * Limits for a resource.
     */
    public static class Limits {
        /* soft limit */
        public long current;

        /* hard limit */
        public long maximum;
    }
}

