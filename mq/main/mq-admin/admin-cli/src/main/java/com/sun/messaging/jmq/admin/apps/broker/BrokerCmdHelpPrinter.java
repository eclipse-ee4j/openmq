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
 * @(#)HelpPrinter.java	1.28 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import java.util.Enumeration;
import java.util.Properties;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.admin.resources.AdminResources;

/** 
 * This class prints the usage/help statements for the jmqobjmgr.
 *
 */
public class BrokerCmdHelpPrinter implements CommonHelpPrinter, BrokerCmdOptions, BrokerConstants {

    private AdminResources ar = Globals.getAdminResources();

    /**
     * Constructor
     */
    public BrokerCmdHelpPrinter() {
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

	printAttributes();
	printExamples();
	System.exit(0);
    }

    private void printUsage() {
	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_USAGE));
    }

    private void printSubcommands() {
	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_SUBCOMMANDS));
    }

    private void printOptions() {
	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_OPTIONS));
    }

    private void printAttributes() {
	/*
	Object qAttrs[] = {PROP_NAME_QUEUE_FLAVOUR,
			PROP_NAME_OPTION_MAX_MESG_BYTE,
			PROP_NAME_OPTION_MAX_PER_MESG_SIZE,
			PROP_NAME_OPTION_MAX_MESG};
	*/

	String tAttrs;

	tAttrs = PROP_NAME_OPTION_MAX_PER_MESG_SIZE;

	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_ATTRIBUTES1));

	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_ATTRIBUTES2));
	printQueueAttrs();
	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_ATTRIBUTES3, tAttrs));
	printTopicAttrs();

        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_DEST_UNLIMITED));

	for (int i = 0; i < DEST_ATTRS_UNLIMITED.length; ++i)  {
            Globals.stdOutPrintln("    " + DEST_ATTRS_UNLIMITED[i]);
	}
        Globals.stdOutPrintln("");

        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_VALID_VALUES, 
			PROP_NAME_LIMIT_BEHAVIOUR));

        Globals.stdOutPrint("\t");
	for (int i =  0; i < BKR_LIMIT_BEHAV_VALID_VALUES.size(); ++i)  {
            Globals.stdOutPrint(BKR_LIMIT_BEHAV_VALID_VALUES.get(i));
	    
	    if ((i+1) < BKR_LIMIT_BEHAV_VALID_VALUES.size()) {
                Globals.stdOutPrint(" ");
	    }
	}
        Globals.stdOutPrintln("\n");

	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_ATTRIBUTES4));
	printBrokerAttrs();
	Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_ATTRIBUTES5));
	printServiceAttrs();
    }

    private void printExamples() {
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES1));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES2));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES3));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES4));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES5));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES6));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES7));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES8));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES9));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES10));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES11));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES12));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES13));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES14));
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_EXAMPLES15));
    }

    private void printBrokerAttrs()  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	String indent = "    ";

	row[0] = indent + PROP_NAME_BKR_PRIMARY_PORT;
	row[1] = ar.getString(ar.I_JMQCMD_PRIMARY_PORT);
	bcp.add(row);
		
	row[0] = indent + PROP_NAME_BKR_AUTOCREATE_TOPIC;
	row[1] = ar.getString(ar.I_AUTO_CREATE_TOPICS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_AUTOCREATE_QUEUE;
	row[1] = ar.getString(ar.I_AUTO_CREATE_QUEUES);
	bcp.add(row);
		
	row[0] = indent + PROP_NAME_BKR_LOG_LEVEL;
	row[1] = ar.getString(ar.I_LOG_LEVEL);
	bcp.add(row);
		
	row[0] = indent + PROP_NAME_BKR_LOG_ROLL_SIZE;
	row[1] = ar.getString(ar.I_LOG_ROLLOVER_SIZE);
	bcp.add(row);
		
	row[0] = indent + PROP_NAME_BKR_LOG_ROLL_INTERVAL;
	row[1] = ar.getString(ar.I_LOG_ROLLOVER_INTERVAL);
	bcp.add(row);

	/*
	row[0] = indent + PROP_NAME_BKR_METRIC_INTERVAL;
	row[1] = ar.getString(ar.I_METRIC_INTERVAL);
	bcp.add(row);
	*/
		
	row[0] = indent + PROP_NAME_BKR_MAX_MSG;
	row[1] = ar.getString(ar.I_MAX_MSGS_IN_BROKER);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_MAX_TTL_MSG_BYTES;
	row[1] = ar.getString(ar.I_MAX_BYTES_IN_BROKER);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_MAX_MSG_BYTES;
	row[1] = ar.getString(ar.I_MAX_MSG_SIZE);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_CLS_URL;
	row[1] = ar.getString(ar.I_CLS_URL);
	bcp.add(row);

	/*
	row[0] = indent + PROP_NAME_BKR_QUEUE_DELIVERY_POLICY;
	row[1] = ar.getString(ar.I_AUTOCREATED_QUEUE_DELIVERY_POLICY);
	bcp.add(row);
	*/

	row[0] = indent + PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS;
	row[1] = ar.getString(ar.I_AUTOCREATED_QUEUE_MAX_ACTIVE_CONS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS;
	row[1] = ar.getString(ar.I_AUTOCREATED_QUEUE_MAX_FAILOVER_CONS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_LOG_DEAD_MSGS;
	row[1] = ar.getString(ar.I_BKR_LOG_DEAD_MSGS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY;
	row[1] = ar.getString(ar.I_BKR_DMQ_TRUNCATE_MSG_BODY);
	bcp.add(row);
        
	row[0] = indent + PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ;
	row[1] = ar.getString(ar.I_BKR_AUTOCREATE_DESTINATION_USE_DMQ);
	bcp.add(row);
		
	bcp.print();		

        Globals.stdOutPrintln("");
        Globals.stdOutPrint(indent);
        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_VALID_VALUES, 
			PROP_NAME_BKR_LOG_LEVEL));

        Globals.stdOutPrint("\t");
	for (int i = 0; i < BKR_LOG_LEVEL_VALID_VALUES.size(); ++i)  {
            Globals.stdOutPrint(BKR_LOG_LEVEL_VALID_VALUES.get(i));
	    
	    if ((i+1) < BKR_LOG_LEVEL_VALID_VALUES.size())  {
                Globals.stdOutPrint(" ");
	    }
	}
        Globals.stdOutPrintln("\n");

        Globals.stdOutPrintln(ar.getString(ar.I_BROKERCMD_HELP_BKR_UNLIMITED));

	for (int i = 0; i < BKR_ATTRS_UNLIMITED.length; ++i)  {
            Globals.stdOutPrintln("    " + BKR_ATTRS_UNLIMITED[i]);
	}
        Globals.stdOutPrintln("");

    }

    private void printQueueAttrs()  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	String indent = "    ";

	row[0] = indent + PROP_NAME_OPTION_MAX_MESG;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_OPTION_MAX_MESG_BYTE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_BYTES_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_OPTION_MAX_PER_MESG_SIZE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_BYTES_PER_MSG_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_FAILOVER_CONSUMER_COUNT);
	bcp.add(row);

	row[0] = indent + PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_ACTIVE_CONSUMER_COUNT);
	bcp.add(row);

	row[0] = indent + PROP_NAME_IS_LOCAL_DEST
			+ " " 
			+ ar.getString(ar.I_BROKERCMD_HELP_ATTR_CREATE_ONLY);
	row[1] = ar.getString(ar.I_JMQCMD_DST_IS_LOCAL_DEST);
	bcp.add(row);

	row[0] = indent + PROP_NAME_LIMIT_BEHAVIOUR;
	row[1] = ar.getString(ar.I_JMQCMD_DST_LIMIT_BEHAVIOUR);
	bcp.add(row);

	row[0] = indent + PROP_NAME_LOCAL_DELIVERY_PREF;
	row[1] = ar.getString(ar.I_JMQCMD_DST_LOCAL_DELIVERY_PREF);
	bcp.add(row);

	row[0] = indent + PROP_NAME_CONSUMER_FLOW_LIMIT;
	row[1] = ar.getString(ar.I_JMQCMD_DST_CONS_FLOW_LIMIT);
	bcp.add(row);

	row[0] = indent + PROP_NAME_MAX_PRODUCERS;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_PRODUCERS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_USE_DMQ;
	row[1] = ar.getString(ar.I_JMQCMD_DST_USE_DMQ);
	bcp.add(row);

	row[0] = indent + PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED;
	row[1] = ar.getString(ar.I_JMQCMD_DST_VALIDATE_XML_SCHEMA_ENABLED);
	bcp.add(row);

	row[0] = indent + PROP_NAME_XML_SCHEMA_URI_LIST;
	row[1] = ar.getString(ar.I_JMQCMD_DST_XML_SCHEMA_URI_LIST);
	bcp.add(row);

	row[0] = indent + PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_RELOAD_XML_SCHEMA_ON_FAILURE);
	bcp.add(row);

	bcp.print();		

        Globals.stdOutPrintln("");
    }

    private void printTopicAttrs()  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	String indent = "    ";

	row[0] = indent + PROP_NAME_OPTION_MAX_MESG;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_OPTION_MAX_MESG_BYTE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_BYTES_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_OPTION_MAX_PER_MESG_SIZE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_BYTES_PER_MSG_ALLOW);
	bcp.add(row);

	row[0] = indent + PROP_NAME_IS_LOCAL_DEST
			+ " " 
			+ ar.getString(ar.I_BROKERCMD_HELP_ATTR_CREATE_ONLY);
	row[1] = ar.getString(ar.I_JMQCMD_DST_IS_LOCAL_DEST);
	bcp.add(row);

	row[0] = indent + PROP_NAME_LIMIT_BEHAVIOUR;
	row[1] = ar.getString(ar.I_JMQCMD_DST_LIMIT_BEHAVIOUR);
	bcp.add(row);

	row[0] = indent + PROP_NAME_CONSUMER_FLOW_LIMIT;
	row[1] = ar.getString(ar.I_JMQCMD_DST_CONS_FLOW_LIMIT);
	bcp.add(row);

	row[0] = indent + PROP_NAME_MAX_PRODUCERS;
	row[1] = ar.getString(ar.I_JMQCMD_DST_MAX_PRODUCERS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_USE_DMQ;
	row[1] = ar.getString(ar.I_JMQCMD_DST_USE_DMQ);
	bcp.add(row);

	row[0] = indent + PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED;
	row[1] = ar.getString(ar.I_JMQCMD_DST_VALIDATE_XML_SCHEMA_ENABLED);
	bcp.add(row);

	row[0] = indent + PROP_NAME_XML_SCHEMA_URI_LIST;
	row[1] = ar.getString(ar.I_JMQCMD_DST_XML_SCHEMA_URI_LIST);
	bcp.add(row);

	row[0] = indent + PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE;
	row[1] = ar.getString(ar.I_JMQCMD_DST_RELOAD_XML_SCHEMA_ON_FAILURE);
	bcp.add(row);

	bcp.print();		

        Globals.stdOutPrintln("");
    }

    private void printServiceAttrs()  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	String indent = "    ";

	row[0] = indent + PROP_NAME_SVC_PORT;
	row[1] = ar.getString(ar.I_JMQCMD_SVC_PORT);
	bcp.add(row);
		
	row[0] = indent + PROP_NAME_SVC_MIN_THREADS;
	row[1] = ar.getString(ar.I_JMQCMD_SVC_MIN_THREADS);
	bcp.add(row);

	row[0] = indent + PROP_NAME_SVC_MAX_THREADS;
	row[1] = ar.getString(ar.I_JMQCMD_SVC_MAX_THREADS);
	bcp.add(row);
		
	bcp.println();		
    }


}
