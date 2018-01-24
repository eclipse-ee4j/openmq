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

package com.sun.messaging.jmq.admin.util;

import java.io.*;
import com.sun.messaging.jmq.Version;

/**
 * abstract class to be extended, contains commonly used globals
 *
 */
public abstract class CommonGlobals
{
    private static Version version = null;

    private static boolean silentMode = false;

  
    public static Version getVersion()  {
	if (version == null)  {
	    version = new Version(false);
	}

	return (version);
    }


    /*---------------------------------------------
     * Global error printing methods.
     *---------------------------------------------*/

    public static void setSilentMode(boolean mode) {
	silentMode = mode;
    }

    public static void stdErrPrintln(String msg) {
        doPrintln(System.err, msg, false);
    }

    public static void stdErrPrintln(String msg, boolean exit) {
        doPrintln(System.err, msg, exit);
    }

    public static void stdErrPrintln(String type, String msg) {
        doPrintln(System.err, type + " " + msg, false);
    }

    public static void stdErrPrintln(String type, String msg, boolean exit) {
        doPrintln(System.err, type + " " + msg, exit);
    }

    public static void stdOutPrintln(String msg) {
        doPrintln(System.out, msg, false);
    }

    public static void stdOutPrintln(String msg, boolean exit) {
        doPrintln(System.out, msg, exit);
    }

    public static void stdOutPrintln(String type, String msg) {
        doPrintln(System.out, type + " " + msg, false);
    }

    public static void stdOutPrintln(String type, String msg, boolean exit) {
        doPrintln(System.out, type + " " + msg, exit);
    }

    public static void stdErrPrint(String msg) {
        doPrint(System.err, msg, false);
    }

    public static void stdErrPrint(String msg, boolean exit) {
        doPrint(System.err, msg, exit);
    }

    public static void stdErrPrint(String type, String msg) {
        doPrint(System.err, type + " " + msg, false);
    }

    public static void stdErrPrint(String type, String msg, boolean exit) {
        doPrint(System.err, type + " " + msg, exit);
    }

    public static void stdOutPrint(String msg) {
        doPrint(System.out, msg, false);
    }

    public static void stdOutPrint(String msg, boolean exit) {
        doPrint(System.out, msg, exit);
    }

    public static void stdOutPrint(String type, String msg) {
        doPrint(System.out, type + " " + msg, false);
    }

    public static void stdOutPrint(String type, String msg, boolean exit) {
        doPrint(System.out, type + " " + msg, exit);
    }

    private static void doPrintln(PrintStream out, String msg, boolean exit) {
	if (silentMode)  {
	    return;
	}

        out.println(msg);
	if (exit)
	    System.exit(1);
    }

    private static void doPrint(PrintStream out, String msg, boolean exit) {
	if (silentMode)  {
	    return;
	}

        out.print(msg);
	if (exit)
	    System.exit(1);
    }
}

