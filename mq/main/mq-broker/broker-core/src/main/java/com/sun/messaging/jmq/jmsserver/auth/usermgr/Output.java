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
 * @(#)Output.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.io.PrintStream;


/** 
 * This class prints the usage/help statements for the jmqobjmgr.
 *
 */
public class Output {

    private static boolean silentMode = false;

    public static void setSilentMode(boolean mode) {
	silentMode = mode;
    }

    public static void stdErrPrintln(String msg) {
        doPrintln(System.err, msg);
    }
    public static void stdErrPrint(String msg) {
        doPrint(System.err, msg);
    }
    public static void stdErrPrintln(String type, String msg) {
        doPrintln(System.err, type + " " + msg);
    }
    public static void stdErrPrint(String type, String msg) {
        doPrint(System.err, type + " " + msg);
    }


    public static void stdOutPrintln(String msg) {
        doPrintln(System.out, msg);
    }
    public static void stdOutPrint(String msg) {
        doPrint(System.out, msg);
    }
    public static void stdOutPrintln(String type, String msg) {
        doPrintln(System.out, type + " " + msg);
    }
    public static void stdOutPrint(String type, String msg) {
        doPrint(System.out, type + " " + msg);
    }


    private static void doPrintln(PrintStream out, String msg) {
	if (silentMode)  {
	    return;
	}

        out.println(msg);
    }

    private static void doPrint(PrintStream out, String msg) {
	if (silentMode)  {
	    return;
	}

        out.print(msg);
    }
}
