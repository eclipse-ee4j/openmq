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
 * @(#)ObjMgrOptionParser.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.util.Properties;
import javax.naming.*;
import com.sun.messaging.jmq.util.options.OptionDesc;
import com.sun.messaging.jmq.util.options.OptionParser;
import com.sun.messaging.jmq.util.options.OptionException;

/**
 * This class is a command line option parser that is
 * specific to jmqobjmgr.
 *
 * The options that are valid for jmqobjmgr are defined in
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
public class ObjMgrOptionParser extends OptionParser
			implements ObjMgrOptions  {

    /**
     * Options for the jmqobjmgr utility
     */
    static OptionDesc objMgrOptions[] = {
	/* 
	 *
	 * OptionDesc(String option, int type, String baseProp, String value)
	 */
	new OptionDesc(OBJMGR_ADD, OPTION_VALUE_HARDCODED, 
			OBJMGR_CMD_PROP_NAME, OBJMGR_ADD_PROP_VALUE),
	new OptionDesc(OBJMGR_DELETE, OPTION_VALUE_HARDCODED,
			OBJMGR_CMD_PROP_NAME, OBJMGR_DELETE_PROP_VALUE),
	new OptionDesc(OBJMGR_QUERY, OPTION_VALUE_HARDCODED,
			OBJMGR_CMD_PROP_NAME, OBJMGR_QUERY_PROP_VALUE),
	new OptionDesc(OBJMGR_LIST, OPTION_VALUE_HARDCODED,
			OBJMGR_CMD_PROP_NAME, OBJMGR_LIST_PROP_VALUE),
	new OptionDesc(OBJMGR_UPDATE, OPTION_VALUE_HARDCODED,
			OBJMGR_CMD_PROP_NAME, OBJMGR_UPDATE_PROP_VALUE),

	new OptionDesc(OBJMGR_TYPE, OPTION_VALUE_NEXT_ARG,
			OBJMGR_TYPE_PROP_NAME, OBJMGR_TYPE_PROP_VALUE),
	new OptionDesc(OBJMGR_NAME, OPTION_VALUE_NEXT_ARG,
			OBJMGR_NAME_PROP_NAME, OBJMGR_NAME_PROP_VALUE),
	new OptionDesc(OBJMGR_OBJ_ATTRS, OPTION_VALUE_NEXT_ARG_RES,
			OBJMGR_OBJ_ATTRS_PROP_NAME, OBJMGR_OBJ_ATTRS_PROP_VALUE),
	new OptionDesc(OBJMGR_OBJSTORE_ATTRS, OPTION_VALUE_NEXT_ARG_RES,
			OBJMGR_OBJSTORE_ATTRS_PROP_NAME, OBJMGR_OBJSTORE_ATTRS_PROP_VALUE),
	new OptionDesc(OBJMGR_READONLY, OPTION_VALUE_NEXT_ARG,
			OBJMGR_READONLY_PROP_NAME, OBJMGR_READONLY_PROP_VALUE),
	new OptionDesc(OBJMGR_FORCE, OPTION_VALUE_HARDCODED,
			OBJMGR_FORCE_PROP_NAME, OBJMGR_FORCE_PROP_VALUE),
	new OptionDesc(OBJMGR_PREVIEW, OPTION_VALUE_HARDCODED,
		OBJMGR_PREVIEW_PROP_NAME, OBJMGR_PREVIEW_PROP_VALUE),
	new OptionDesc(OBJMGR_INPUTFILE, OPTION_VALUE_NEXT_ARG,
		OBJMGR_INPUTFILE_PROP_NAME, OBJMGR_INPUTFILE_PROP_VALUE),
	new OptionDesc(OBJMGR_SILENTMODE, OPTION_VALUE_HARDCODED,
		OBJMGR_SILENTMODE_PROP_NAME, OBJMGR_SILENTMODE_PROP_VALUE),
        new OptionDesc(OBJMGR_OBJSTORE_BIND_ATTRS, OPTION_VALUE_NEXT_ARG_RES,
                        OBJMGR_OBJSTORE_BIND_ATTRS_PROP_NAME, OBJMGR_OBJSTORE_BIND_ATTRS_PROP_VALUE),

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
    public static ObjMgrProperties parseArgs(String args[]) 
		throws OptionException  {
	ObjMgrProperties objMgrProps = new ObjMgrProperties();

	/*
	 * Invoke main parsing code in superclass
	 */
        parseArgs(args, objMgrOptions, objMgrProps); 

        return (objMgrProps); 
    }
}


