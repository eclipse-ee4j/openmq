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

import java.util.Properties;
import com.sun.messaging.jmq.util.options.OptionDesc;
import com.sun.messaging.jmq.util.options.OptionParser;
import com.sun.messaging.jmq.util.options.OptionException;

/**
 * This class is a command line option parser for imqbridgemgr.  
 *
 */
public class BridgeMgrOptionParser extends OptionParser implements BridgeMgrOptions
{

    private static OptionDesc[] bridgeMgrOptions = {

	/******************************************************************************************* 
	 * OptionDesc(String option, int type, String baseProp, String value, String nameValuePair)
	 ******************************************************************************************/

	new OptionDesc(Cmd.LIST, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.LIST),
	new OptionDesc(Cmd.PAUSE, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.PAUSE),
	new OptionDesc(Cmd.RESUME, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.RESUME),
	new OptionDesc(Cmd.START, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.START),
	new OptionDesc(Cmd.STOP, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.STOP),
	new OptionDesc(Cmd.DEBUG, OPTION_VALUE_NEXT_ARG, 
			PropName.CMDARG, null, PropNVForCmd.DEBUG),

	new OptionDesc(Option.BRIDGE_TYPE, OPTION_VALUE_NEXT_ARG, 
            PropName.OPTION_BRIDGE_TYPE, null),
	new OptionDesc(Option.BRIDGE_NAME, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_BRIDGE_NAME, null),
	new OptionDesc(Option.LINK_NAME, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_LINK_NAME, null),

	new OptionDesc(Option.BROKER_HOSTPORT, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_BROKER_HOSTPORT, null),
	new OptionDesc(Option.ADMIN_USERID, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_ADMIN_USERID, null),
	new OptionDesc(Option.ADMIN_PRIVATE_PASSWD, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_ADMIN_PRIVATE_PASSWD, null),
	new OptionDesc(Option.ADMIN_PASSFILE, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_ADMIN_PASSFILE, null),
    new OptionDesc(Option.SYS_PROPS, OPTION_VALUE_SUFFIX_RES,
            PropName.OPTION_SYS_PROPS, null),


	new OptionDesc(Option.FORCE, OPTION_VALUE_HARDCODED, 
			PropName.OPTION_FORCE, PropValue.OPTION_FORCE),
	new OptionDesc(Option.SILENTMODE, OPTION_VALUE_HARDCODED, 
			PropName.OPTION_SILENTMODE, PropValue.OPTION_SILENTMODE),

	new OptionDesc(Option.NOCHECK, OPTION_VALUE_HARDCODED, 
			PropName.OPTION_NOCHECK, PropValue.OPTION_NOCHECK),
	new OptionDesc(Option.DEBUG, OPTION_VALUE_HARDCODED, 
			PropName.OPTION_DEBUG, PropValue.OPTION_DEBUG),
    new OptionDesc(Option.ADMIN_DEBUG, OPTION_VALUE_HARDCODED,
            PropName.OPTION_ADMIN_DEBUG, PropValue.OPTION_ADMIN_DEBUG),
	new OptionDesc(Option.TARGET_NAME, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_TARGET_NAME, null),
    new OptionDesc(Option.TARGET_ATTRS, OPTION_VALUE_NEXT_ARG_RES,
			PropName.OPTION_TARGET_ATTRS, null),

	new OptionDesc(Option.RECV_TIMEOUT, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_RECV_TIMEOUT, null),
	new OptionDesc(Option.NUM_RETRIES, OPTION_VALUE_NEXT_ARG, 
			PropName.OPTION_NUM_RETRIES, null),
	new OptionDesc(Option.SSL, OPTION_VALUE_HARDCODED, 
			PropName.OPTION_SSL, PropValue.OPTION_SSL),

	/*
	 * These are options that are parsed by the startup script. They are
	 * parsed by the option parsing logic, but are not used to create
	 * the options property object.
	 */
	new OptionDesc("-javahome", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-jmqhome", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-jmqvarhome", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-varhome", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-jmqext", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-vmargs", OPTION_VALUE_NEXT_ARG,
		"", "", true),
	new OptionDesc("-verbose", OPTION_VALUE_HARDCODED,
		"", "", true),

    };
    
    
    /**
     * Parses arg list using the specified option description
     * table and returns a BridgeMgrProperties object which corresponds
     * to it.
     */
    public static BridgeMgrProperties parseArgs(String args[]) 
                                     throws OptionException  {

        BridgeMgrProperties props = new BridgeMgrProperties();

	    /*
	     * Invoke main parsing code in superclass
	     */
        parseArgs(args, bridgeMgrOptions, props);

        return (props); 
    }
}
