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

package com.sun.messaging.bridge.admin.bridgemgr;


import com.sun.messaging.jmq.admin.apps.broker.CommonHelpPrinter;
import com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources;

/** 
 * This class prints the usage/help statements for the imqbridgemgr
 *
 */
public class BridgeMgrHelpPrinter implements CommonHelpPrinter, BridgeMgrOptions  {

    private BridgeAdminResources bar = Globals.getBridgeAdminResources();

    /**
     * Constructor
     */
    public BridgeMgrHelpPrinter() {
    } 

    /**
     * Prints usage, subcommands, options then exits.
     */
    public void printShortHelp(int exitStatus) {
	printUsage();
	printSubcommands();
	printOptions();
	System.exit(exitStatus);
    }

    /**
     * Prints everything in short help plus
     * attributes, examples then exits.
     */
    public void printLongHelp() {
	printUsage();
	printSubcommands();
	printOptions();

	printExamples();
	System.exit(0);
    }

    private void printUsage() {
	Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_USAGE));
    }

    private void printSubcommands() {
	Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_SUBCOMMANDS));
    }

    private void printOptions() {
	Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_OPTIONS));
    }

    private void printExamples() {
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES1));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES2));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES3));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES4));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES5));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES6));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES7));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES8));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES9));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES10));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES11));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES12));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES13));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES14));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES15));
        Globals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES16));
    }
}
