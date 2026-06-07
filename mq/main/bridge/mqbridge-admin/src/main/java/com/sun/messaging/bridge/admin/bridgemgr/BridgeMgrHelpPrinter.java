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

package com.sun.messaging.bridge.admin.bridgemgr;

import com.sun.messaging.jmq.admin.apps.broker.CommonHelpPrinter;
import com.sun.messaging.jmq.admin.util.CommonGlobals;
import com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources;

/**
 * This class prints the usage/help statements for the imqbridgemgr
 *
 */
public class BridgeMgrHelpPrinter implements CommonHelpPrinter, BridgeMgrOptions {

    private BridgeAdminResources bar = Globals.getBridgeAdminResources();

    /**
     * Prints usage, subcommands, options then exits.
     */
    @Override
    public void printShortHelp(int exitStatus) {
        printUsage();
        printSubcommands();
        printOptions();
        System.exit(exitStatus);
    }

    /**
     * Prints everything in short help plus attributes, examples then exits.
     */
    public void printLongHelp() {
        printUsage();
        printSubcommands();
        printOptions();

        printExamples();
        System.exit(0);
    }

    private void printUsage() {
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_USAGE));
    }

    private void printSubcommands() {
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_SUBCOMMANDS));
    }

    private void printOptions() {
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_OPTIONS));
    }

    private void printExamples() {
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES1));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES2));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES3));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES4));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES5));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES6));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES7));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES8));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES9));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES10));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES11));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES12));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES13));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES14));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES15));
        CommonGlobals.stdOutPrintln(bar.getString(bar.I_BGMGR_HELP_EXAMPLES16));
    }
}
