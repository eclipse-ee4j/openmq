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
 * @(#)HelpPrinter.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/** 
 * This class prints the usage/help statements for the jmqobjmgr.
 *
 */
public class HelpPrinter {

    private static BrokerResources br = Globals.getBrokerResources();

    /**
     * Constructor
     */
    public HelpPrinter() {
    } 

    /**
     * Prints usage, subcommands, options then exits.
     */
    public void printShortHelp() {
	printUsage();
	printSubcommands();
	printOptions();
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
    }

    private void printUsage() {
	Output.stdOutPrintln(br.getString(br.I_USERMGR_HELP_USAGE));
    }

    private void printSubcommands() {
	/*
	Output.stdOutPrintln(br.getString(br.I_USERMGR_HELP_SUBCOMMANDS));
	*/
    }

    private void printOptions() {
	Output.stdOutPrintln(br.getString(br.I_USERMGR_HELP_OPTIONS));
    }

    private void printExamples() {
	/*
        Output.stdOutPrintln(br.getString(br.I_USERMGR_HELP_EXAMPLES1));
	*/
    }
}
