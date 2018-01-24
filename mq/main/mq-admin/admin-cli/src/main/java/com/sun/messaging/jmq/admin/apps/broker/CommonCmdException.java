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
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import java.util.Properties;

/**
 * This class is sub classed by imqcmd (BrokerCmdException) 
 * and imqbridgemgr (BridgeCmdException) 
 *
 * Please place individual admin tool specific references in its subclass 
 *
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
 * in a BrokerCmdProperties object. This exception will
 * contain a BrokerCmdProperties object to encapsulate
 * the erroneous information.
 **/

public class CommonCmdException extends Exception  {

    /**************************************************************************
     * use integer >= 10000, each subclass should use different integer ranges
     **************************************************************************/
    public static final int		NO_CMD_SPEC		           = 10000;
    public static final int		BAD_CMD_SPEC		       = 10001;
    public static final int		CMDARG_NOT_SPEC		       = 10002;
    public static final int		BAD_CMDARG_SPEC		       = 10003;
    public static final int		INVALID_RECV_TIMEOUT_VALUE = 10004;
    public static final int		INVALID_NUM_RETRIES_VALUE  = 10005;
    public static final int		READ_PASSFILE_FAIL		   = 10006;
    public static final int		INVALID_TIME			   = 10007;
     public static final int     INVALID_INTEGER_VALUE     = 10008;


    /**
     * Props object encapsulating the user specified options/commands.
     **/
    private Properties cmdProps;
    private String validCmdArgs[];
    private String validAttrs[];
    private String badAttr;
    private String badValue;
    private int type;
    // this is a convenient storage place for a single value
    private String errorString = null;
    private Exception           linkedEx;

    /**
     * Constructs an BrokerCmdException
     */ 
    public CommonCmdException() {
        super();
        cmdProps = null;
    }

    /** 
     * Constructs an BrokerCmdException with type
     *
     * @param  type       type of exception 
     **/
    public CommonCmdException(int type) {
        super();
        cmdProps = null;
	this.type = type;
    }

    /** 
     * Constructs an BrokerCmdException with reason
     *
     * @param  reason        a description of the exception
     **/
    public CommonCmdException(String reason) {
        super(reason);
        cmdProps = null;
    }

    /**
     * Gets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @return the properties object that encapsulates the user 
     *		specified options/commands.
     **/
    public Properties getProperties() {
        return (cmdProps);
    }

    /**
     * Sets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @param p		the properties object that encapsulates the user 
     *			specified options/commands.
     **/
    public synchronized void setProperties(Properties p) {
        cmdProps = p;
    }

    /**
     * Gets the type of exception.
     *
     * @return the exception type.
     **/
    public synchronized int getType() {
	return (type);
    }

    /**
     * Sets the valid cmd args - to be used for exceptions of type
     * BAD_CMDARG_SPEC when printing out the error message.
     *
     * @param validCmdArgs	the array of Strings that contains
     *				the valid command arguments for
     *				the command specified in the
     *				BrokerCmdProperties object.
     **/
    public void setValidCmdArgs(String validCmdArgs[]) {
	this.validCmdArgs = validCmdArgs;
    }

    /**
     * Gets the valid cmd args - to be used for exceptions of type
     * BAD_CMDARG_SPEC when printing out the error message.
     *
     * @return	The array of Strings that contains
     *		the valid command arguments for
     *		the command specified in the
     *		BrokerCmdProperties object.
     **/
    public String[] getValidCmdArgs() {
	return (validCmdArgs);
    }

    /**
     * Sets the valid attrs - to be used for exceptions of type
     * BAD_ATTR_SPEC_* when printing out the error message.
     *
     * @param validAttrs	the array of Strings that contains
     *				the valid attributes for
     *				the command specified in the
     *				BrokerCmdProperties object.
     **/
    public void setValidAttrs(String validAttrs[]) {
	this.validAttrs = validAttrs;
    }

    /**
     * Gets the valid attrs - to be used for exceptions of type
     * BAD_ATTR_SPEC_* when printing out the error message.
     *
     * @return	The array of Strings that contains
     *		the valid attributes for
     *		the command specified in the
     *		BrokerCmdProperties object.
     **/
    public String[] getValidAttrs() {
	return (validAttrs);
    }

    /**
     * Sets the bad attr that caused the BAD_ATTR_SPEC_* exception.
     * This will be used when printing out the error message.
     *
     * @param badAttr		the bad attribute causing the exception.
     **/
    public void setBadAttr(String badAttr)  {
	this.badAttr = badAttr;
    }

    /**
     * Gets the bad attr that caused the BAD_ATTR_SPEC_* exception.
     * This will be used when printing out the error message.
     *
     * @return	The bad attribute causing the exception.
     **/
    public String getBadAttr()  {
	return (badAttr);
    }

    /**
     * Sets the bad value that caused the exception.
     * This will be used when printing out the error message.
     *
     * @param badAttr		the bad value causing the exception.
     **/
    public void setBadValue(String badValue)  {
	this.badValue = badValue;
    }

    /**
     * Returns the bad value that caused the exception.
     * This will be used when printing out the error message.
     *
     * @return	The bad value causing the exception.
     **/
    public String getBadValue()  {
	return (badValue);
    }

    /**
     * Sets an error string.  This can be used to print a more explicit error message.
     **/
    public void setErrorString(String value) {
	this.errorString = value;
    }

    /**
     * Gets an error string.
     *
     * @return errorString
     **/
    public String getErrorString() {
	return (errorString);
    }

    /**
     * Set linked exception.
     *
     * @param Exception relevant to this one.
     **/
    public void setLinkedException(Exception ex)  {
        linkedEx = ex;
    }

    /**
     * Get linked exception.
     *
     * @return Exception relevant to this one.
     **/
    public Exception getLinkedException()  {
        return (linkedEx);
    }
}
