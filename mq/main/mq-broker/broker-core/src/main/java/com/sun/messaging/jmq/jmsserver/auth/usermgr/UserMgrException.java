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
 * @(#)UserMgrException.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

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
 * in a UserMgrProperties object. This exception will
 * contain a UserMgrProperties object to encapsulate
 * the erroneous information.
 **/

public class UserMgrException extends Exception  {

    public static final int	NO_CMD_SPEC		= 0;
    public static final int	BAD_CMD_SPEC		= 1;
    public static final int	PASSWD_NOT_SPEC		= 2;
    public static final int	USERNAME_NOT_SPEC	= 4;
    public static final int	ROLE_NOT_SPEC		= 5;
    public static final int	INVALID_ROLE_SPEC	= 6;
    public static final int	PW_FILE_NOT_FOUND	= 7;
    public static final int	PW_FILE_FORMAT_ERROR	= 8;
    public static final int	USER_NOT_EXIST		= 9;
    public static final int	USER_ALREADY_EXIST	= 10;
    public static final int	PASSWD_INCORRECT	= 11;
    public static final int	PW_FILE_WRITE_ERROR	= 12;
    public static final int	PW_FILE_READ_ERROR	= 13;
    public static final int	ONLY_ONE_ANON_USER	= 14;
    public static final int	PASSWD_OR_ACTIVE_NOT_SPEC	= 15;
    public static final int	ILLEGAL_USERNAME	= 16;
    public static final int	BAD_ACTIVE_VALUE_SPEC	= 17;
    public static final int	PROBLEM_GETTING_INPUT	= 18;
    public static final int	ACTIVE_NOT_VALID_WITH_ADD= 19;
    public static final int	PASSWD_ENCRYPT_FAIL	= 20;
    public static final int	INSTANCE_NOT_EXISTS	= 21;
    public static final int	READ_PASSFILE_FAIL	= 22;
    public static final int	USERNAME_IS_EMPTY	= 23;
    public static final int	SRC_FILE_NOT_SPEC	= 24;
    public static final int	CANT_CREATE_INSTANCE	= 25;
    public static final int	CANT_CREATE_PWFILE      = 26;

    /**
     * Props object encapsulating the user specified options/commands.
     **/
    private UserMgrProperties	userMgrProps;
    private String		pwFile,
				userName;
    private Exception		linkedEx;
    private int			type;

    /**
     * Constructs an UserMgrException
     */ 
    public UserMgrException() {
        super();
        userMgrProps = null;
    }

    /** 
     * Constructs an UserMgrException with type
     *
     * @param  type       type of exception 
     **/
    public UserMgrException(int type) {
        super();
        userMgrProps = null;
	this.type = type;
    }

    public UserMgrException(int type, Throwable thr) {
        super(thr);
        userMgrProps = null;
        this.type = type;
    }

    /** 
     * Constructs an UserMgrException with reason
     *
     * @param  reason        a description of the exception
     **/
    public UserMgrException(String reason) {
        super(reason);
        userMgrProps = null;
    }

    public UserMgrException(int type, String reason) {
        super(reason);
        userMgrProps = null;
        this.type = type;
    }

    /**
     * Gets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @return the properties object that encapsulates the user 
     *		specified options/commands.
     **/
    public synchronized UserMgrProperties getProperties() {
        return (userMgrProps);
    }

    /**
     * Sets the properties object that encapsulates the user specified
     * options/commands.
     *
     * @param p		the properties object that encapsulates the user 
     *			specified options/commands.
     **/
    public synchronized void setProperties(UserMgrProperties p) {
        userMgrProps = p;
    }

    /**
     * Gets the type of exception.
     *
     * @return the exception type.
     **/
    public synchronized int getType() {
	return (type);
    }

    public void setLinkedException(Exception ex)  {
	linkedEx = ex;
    }
    public Exception getLinkedException()  {
	return (linkedEx);
    }

    public void setUserName(String name)  {
	userName = name;
    }
    public String getUserName()  {
	return (userName);
    }

    public void setPasswordFile(String fileName)  {
	pwFile = fileName;
    }
    public String getPasswordFile()  {
	return (pwFile);
    }
}
