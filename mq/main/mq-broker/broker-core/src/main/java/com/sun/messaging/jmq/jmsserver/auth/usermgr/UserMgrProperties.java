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
 * @(#)UserMgrProperties.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.util.Properties;
import java.util.Enumeration;

/**
 * This class encapsulates the information that the user
 * has provided to perform any JMQ Broker Administration
 * task. It contains properties that describe:
 * <UL>
 * <LI>the type of command
 * <LI>the command argument
 * <LI>the destination type
 * <LI>the target name
 * <LI>the target attributes
 * <LI>etc..
 * </UL>
 *
 * This class has a number of convenience methods to extract
 * the information above. Currently, each of these methods
 * has a get() and a get(commandIndex) version. The version
 * that takes a commandIndex is currently not supported.
 * It is for handling the case where multiple commands are
 * stored in one UserMgrProperties object.
 *
 * @see		BrokerCmdOptions
 */
public class UserMgrProperties extends Properties
			implements UserMgrOptions  {
    
    public UserMgrProperties()  {
	super();
    }

    /**
     * Returns the command string. e.g. <EM>list</EM>.
     *
     * @return	The command string
     */
    public String getCommand()  {
	return (getCommand(-1));
    }
    /**
     * Returns the command string. e.g. <EM>list</EM>.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The command string
     */
    public String getCommand(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_CMD));
	}

	return (null);
    }

    /**
     * Returns the number of commands.
     *
     * @return	The number of commands.
     */
    public int getCommandCount()  {
	return (1);
    }

    /**
     * Returns the old/current user password.
     *
     * @return	The old/current user password.
     */
    public Boolean isActive()  {
	return (isActive(-1));
    }
    /**
     * Returns the old/current user password.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The old/current user password.
     */
    public Boolean isActive(int commandIndex)  {
	if (commandIndex == -1)  {

	    String s = getActiveValue();

	    if (s == null)  {
	        return ((Boolean)null);
	    }

	    if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	        return (Boolean.TRUE);
	    } else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	        return (Boolean.FALSE);
	    }

	    return (Boolean.FALSE);
	}

	return (Boolean.FALSE);
    }

    public String getActiveValue()  {
        String s = getProperty(PROP_NAME_OPTION_ACTIVE);

	return (s);
    }
    public void setActiveValue(String s)  {
        setProperty(PROP_NAME_OPTION_ACTIVE, s);
    }


    /**
     * Returns the user password.
     *
     * @return	The user password.
     */
    public String getPassword()  {
	return (getPassword(-1));
    }
    /**
     * Returns the user password.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The user password.
     */
    public String getPassword(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_PASSWD));
	}

	return (null);
    }

    /**
     * Sets the user password.
     *
     * @param	The user password.
     */
    public void setPassword(String password)  {
        setProperty(PROP_NAME_OPTION_PASSWD, password);
    }



    /**
     * Returns the user role
     *
     * @return	The user role.
     */
    public String getRole()  {
	return (getRole(-1));
    }
    /**
     * Returns the user role.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The user role.
     */
    public String getRole(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_ROLE));
	}

	return (null);
    }

    /**
     * Returns the user name.
     *
     * @return	The user name.
     */
    public String getUserName()  {
	return (getUserName(-1));
    }
    /**
     * Returns the user name.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The user name.
     */
    public String getUserName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_USERNAME));
	}

	return (null);
    }

    /**
     * Sets the user name.
     *
     * @param	The user name.
     */
    public void setUserName(String username)  {
        setProperty(PROP_NAME_OPTION_USERNAME, username);
    }

    /**
     * Returns the instance name.
     *
     * @return	The instance name.
     */
    public String getInstance()  {
	return (getInstance(-1));
    }

    /**
     * Returns the instance name.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same UserMgrProperties object).
     *				
     * @return	The instance name.
     */
    public String getInstance(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(PROP_NAME_OPTION_INSTANCE));
	}

	return (null);
    }

    /**
     * Sets the instance name.
     *
     * @param	The instance name.
     */
    public void setInstance(String instance)  {
        setProperty(PROP_NAME_OPTION_INSTANCE, instance);
    }

    /**
     * Returns the path name of the password file
     *
     * @return	The path name of the password file.
     */
    public String getPasswordFile() {
	return getProperty(PROP_NAME_PASSWORD_FILE);
    }

    /**
     * Sets the path name of the password file
     *
     * @param	The path name of the password file.
     */
    public void setPasswordFile(String pwfile)  {
        setProperty(PROP_NAME_PASSWORD_FILE, pwfile);
    }

    /**
     * Returns the path name of the passfile
     *
     * @return	The path name of the passfile.
     */
    public String getPassfile() {
	return getProperty(PROP_NAME_OPTION_PASSFILE);
    }

    /**
     * Sets the path name of the passfile
     *
     * @param	The path name of the passfile.
     */
    public void setPassfile(String passfile)  {
        setProperty(PROP_NAME_OPTION_PASSFILE, passfile);
    }


    /**
     * Returns whether force mode was specified by the user.
     * Force mode is when no user interaction will be needed.
     * i.e. if storing an object, and an object with the same
     * lookup name already exists, no overwrite confirmation
     * will be asked, the object is overwritten.
     *
     * @return	true if force mode is set, false if force mode
     *		was not set.
     */
    public boolean forceModeSet()  {
	String s = getProperty(PROP_NAME_OPTION_FORCE);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns the path name of the src file (for encode/decode)
     *
     * @return	The path name of the src file (for encode/decode).
     */
    public String getSrc() {
	return getProperty(PROP_NAME_OPTION_SRC);
    }

    /**
     * Returns the path name of the target file (for encode/decode)
     *
     * @return	The path name of the target file (for encode/decode).
     */
    public String getTarget() {
	return getProperty(PROP_NAME_OPTION_TARGET);
    }


}

