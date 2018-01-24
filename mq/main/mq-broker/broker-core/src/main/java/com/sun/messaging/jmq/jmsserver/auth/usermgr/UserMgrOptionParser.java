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
 * @(#)UserMgrOptionParser.java	1.11 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.util.Properties;
import javax.naming.*;
import com.sun.messaging.jmq.util.options.OptionDesc;
import com.sun.messaging.jmq.util.options.OptionParser;
import com.sun.messaging.jmq.util.options.OptionException;

/**
 * This class is a command line option parser that is
 * specific to jmqcmd.
 *
 * The options that are valid for jmqcmd are defined in
 * the options table. This class also defines a
 * parseArgs() method, which is different from
 *	OptionParser.parseArgs()
 * because it:
 * <UL>
 * <LI>returns a ObjMgrProperties object
 * <LI>only takes the String args[] as parameter
 * </UL>
 *
 * @see		com.sun.messaging.jmq.admin.util.OptionType
 * @see		com.sun.messaging.jmq.admin.util.OptionDesc
 * @see		com.sun.messaging.jmq.admin.util.OptionParser
 */
public class UserMgrOptionParser extends OptionParser
			implements UserMgrOptions  {

    /**
     * Options for the jmqobjmgr utility
     */
    static OptionDesc userMgrOptions[] = {
	/* 
	 *
	 * OptionDesc(String option, int type, String baseProp, String value)
	 */
	new OptionDesc(CMD_ADD, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_ADD),
	new OptionDesc(CMD_DELETE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_DELETE),
	new OptionDesc(CMD_LIST, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_LIST),
	new OptionDesc(CMD_UPDATE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_UPDATE),

	new OptionDesc(CMD_EXISTS, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_EXISTS),
	new OptionDesc(CMD_GETGROUP, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_GETGROUP),
	new OptionDesc(CMD_GETGROUPSIZE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_GETGROUPSIZE),

	new OptionDesc(CMD_ENCODE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_ENCODE),
	new OptionDesc(CMD_DECODE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_CMD, PROP_VALUE_CMD_DECODE),

	new OptionDesc(OPTION_ACTIVE, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_ACTIVE, null),
	new OptionDesc(OPTION_PASSWD, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_PASSWD, null),
	new OptionDesc(OPTION_ROLE, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_ROLE, null),
	new OptionDesc(OPTION_USERNAME, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_USERNAME, null),
	new OptionDesc(OPTION_INSTANCE, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_INSTANCE, null),
	new OptionDesc(OPTION_PASSFILE, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_PASSFILE, null),

	new OptionDesc(OPTION_FORCE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_OPTION_FORCE, PROP_VALUE_OPTION_FORCE),
	new OptionDesc(OPTION_SILENTMODE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_OPTION_SILENTMODE, PROP_VALUE_OPTION_SILENTMODE),
	new OptionDesc(OPTION_CREATEMODE, OPTION_VALUE_HARDCODED, 
			PROP_NAME_OPTION_CREATEMODE, PROP_VALUE_OPTION_CREATEMODE),

	new OptionDesc(OPTION_SRC, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_SRC, null),
	new OptionDesc(OPTION_TARGET, OPTION_VALUE_NEXT_ARG, 
			PROP_NAME_OPTION_TARGET, null),

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
	new OptionDesc("-verbose", OPTION_VALUE_HARDCODED,
		"", "", true),
	new OptionDesc("-jmqext", OPTION_VALUE_NEXT_ARG,
		"", "", true)
	};
    
    
    /**
     * Parses arg list using the specified option description
     * table and returns a ObjMgrProperties object which corresponds
     * to it.
     */
    public static UserMgrProperties parseArgs(String args[]) 
		throws OptionException  {
	UserMgrProperties userMgrProps = new UserMgrProperties();

	/*
	 * Invoke main parsing code in superclass
	 */
        parseArgs(args, userMgrOptions, userMgrProps); 

        return (userMgrProps); 
    }
}
