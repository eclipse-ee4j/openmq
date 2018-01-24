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
 * @(#)ObjMgrException.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

/**
 * This exception is thrown when problems are
 * encountered when validating the information
 * that is provided to execute commands. Examples
 * of errors include:
 * <UL>
 * <LI>bad command type
 * <LI>missing mandatory values
 * </UL>
 *
 * <P>
 * The information that is provided by the user is encapsulated
 * in a ObjMgrProperties object. This exception will
 * contain a ObjMgrProperties object to encapsulate
 * the erroneous information.
 **/

public class ObjMgrException extends Exception  {

    public static final int		NO_CMD_SPEC		= 0;
    public static final int		BAD_CMD_SPEC		= 1;
    public static final int		NO_OBJ_TYPE_SPEC	= 2;
    public static final int		INVALID_OBJ_TYPE	= 3;
    public static final int		NO_LOOKUP_NAME_SPEC	= 4;
    public static final int		NO_DEST_NAME_SPEC	= 5;
    public static final int		INVALID_READONLY_VALUE  = 6;

    /**
     * Props object encapsulating the user specified options/commands.
     **/
    private ObjMgrProperties objMgrProps;
    private int type;

    /**
     * Constructs an ObjMgrException
     */ 
    public ObjMgrException() {
        super();
        objMgrProps = null;
    }

    /** 
     * Constructs an ObjMgrException with type
     *
     * @param  type       type of exception 
     **/
    public ObjMgrException(int type) {
        super();
        objMgrProps = null;
	this.type = type;
    }

    /** 
     * Constructs an ObjMgrException with reason
     *
     * @param  reason        a description of the exception
     **/
    public ObjMgrException(String reason) {
        super(reason);
        objMgrProps = null;
    }

    /**
     * Gets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @return the properties object that encapsulates the user 
     *		specified options/commands.
     **/
    public ObjMgrProperties getProperties() {
        return (objMgrProps);
    }

    /**
     * Sets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @param p		the properties object that encapsulates the user 
     *			specified options/commands.
     **/
    public synchronized void setProperties(ObjMgrProperties p) {
        objMgrProps = p;
    }

    /**
     * Gets the type of exception.
     *
     * @return the exception type.
     **/
    public synchronized int getType() {
	return (type);
    }
}
