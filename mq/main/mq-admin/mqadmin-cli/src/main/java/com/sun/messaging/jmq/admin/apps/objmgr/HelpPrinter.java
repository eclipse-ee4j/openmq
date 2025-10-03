/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to Eclipse Foundation
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

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.util.Enumeration;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;

/**
 * This class prints the usage/help statements for the jmqobjmgr.
 *
 */
public class HelpPrinter {

    private AdminResources ar = Globals.getAdminResources();

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
     * Prints everything in short help plus attributes, examples then exits.
     */
    public void printLongHelp() {
        printUsage();
        printSubcommands();
        printOptions();

        printAttributes();
        printExamples();
        System.exit(0);
    }

    private void printUsage() {
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_USAGE));
    }

    private void printSubcommands() {
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_SUBCOMMANDS));
    }

    private void printOptions() {
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_OPTIONS));
    }

    private void printAttributes() {

        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_ATTRIBUTES1));

        // Create a Destination administered object to get it's properties
        AdministeredObject obj = new com.sun.messaging.Topic();

        ObjMgrPrinter omp = new ObjMgrPrinter(2, 6);
        String[] row = new String[2];

        for (Enumeration e = obj.enumeratePropertyNames(); e.hasMoreElements();) {
            String propName = (String) e.nextElement();
            try {
                row[0] = "    " + propName;
                row[1] = obj.getPropertyLabel(propName);
                omp.add(row);
            } catch (Exception ex) {
            }
        }
        omp.print();

        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_ATTRIBUTES2));
        // Create a ConnFactory administered object to get it's properties
        obj = new com.sun.messaging.TopicConnectionFactory();

        ObjMgrPrinter omp2 = new ObjMgrPrinter(2, 6);

        for (Enumeration e = obj.enumeratePropertyNames(); e.hasMoreElements();) {
            String propName = (String) e.nextElement();
            try {
                row[0] = "    " + propName;
                row[1] = obj.getPropertyLabel(propName);
                omp2.add(row);
            } catch (Exception ex) {
            }
        }
        omp2.print();

    }

    private void printExamples() {
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES1));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES2));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES3));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES4));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES5));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES6));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES7));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES8));
        Globals.stdOutPrintln(ar.getString(ar.I_OBJMGR_HELP_EXAMPLES9));
    }

}
