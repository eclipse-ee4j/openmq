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
 * @(#)UserMgr.java	1.26 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.usermgr;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import java.util.ArrayList;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.util.options.OptionException;
import com.sun.messaging.jmq.util.options.UnrecognizedOptionException;
import com.sun.messaging.jmq.util.options.InvalidBasePropNameException;
import com.sun.messaging.jmq.util.options.InvalidHardCodedValueException;
import com.sun.messaging.jmq.util.options.MissingArgException;
import com.sun.messaging.jmq.util.options.BadNameValueArgException;
import com.sun.messaging.jmq.util.FileUtil;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.auth.file.JMQFileUserRepository;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.Broker;

/** 
 * This is a utility that allows administrators to manage the file based
 * user repository in MQ.
 */
public class UserMgr implements UserMgrOptions  {

    private static BrokerResources br = Globals.getBrokerResources();

    /**
     * Constructor
     */
    public UserMgr() {
    } 


    public static void main(String[] args)  {
	int exitcode = 0;
        boolean errorIfNotExists = true;

	if (silentModeOptionSpecified(args))  {
            Output.setSilentMode(true);
	}

        if (createInstanceSpecified(args)) {
            errorIfNotExists = false;
        }

	/*
	 * Check for -h or -H, or that the first argument
	 * is the command type (-a, -d, -l or -q)
	 * if we still want to have that restriction.
	 */
	if (shortHelpOptionSpecified(args))  {
            HelpPrinter hp = new HelpPrinter();
	    hp.printShortHelp();
	    System.exit(0);
	} else if (longHelpOptionSpecified(args))  {
            HelpPrinter hp = new HelpPrinter();
	    hp.printLongHelp();
	    System.exit(0);
	}

	/*
	 * Check for -version.
	 */
	if (versionOptionSpecified(args)) {
	    printBanner();
            printVersion();
	    System.exit(0);
	}
    args = filterSystemProperties(args);

	UserMgrProperties userMgrProps = null;

	/*
	 * Convert String args[] into a UserMgrProperties object.
	 * The UserMgrProperties class is just a Properties
	 * subclass with some convenience methods in it.
	 */
	try  {
	    userMgrProps = UserMgrOptionParser.parseArgs(args);
	} catch (OptionException e)  {
	    handleArgsParsingExceptions(e);
            System.exit(1);
	}

	/*
	 * For each command type used, check that the
	 * information passed in is sufficient.
	 */
	try  {
	    checkInstance(userMgrProps, errorIfNotExists);

	    checkOptions(userMgrProps);

	} catch (UserMgrException ome)  {
	    handleCheckOptionsExceptions(ome);
            System.exit(1);
	}

	/*
	 * Execute the commands specified by the user
	 */

	CmdRunner cmdRunner = new CmdRunner(userMgrProps);
	exitcode = cmdRunner.runCommands();

	System.exit(exitcode);
    }

    /**
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute user commands. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkOptions(UserMgrProperties userMgrProps) 
			throws UserMgrException  {

	/*
	 * For debugging:
        Output.stdErrPrintln("UserMgrProperties dump:");
	userMgrProps.list(System.err);
	Output.stdErrPrintln("-------------\n");
	 */

	/*
	 * For debugging:
	Output.stdErrPrintln("Command: " + userMgrProps.getCommand());
	Output.stdErrPrintln("User Passwd: " + userMgrProps.getPassword());
	Output.stdErrPrintln("User Active State: " + userMgrProps.isActive());
	Output.stdErrPrintln("User Name: " + userMgrProps.getUserName());
	Output.stdErrPrintln("User Role: " + userMgrProps.getRole());
	 */

	String cmd = userMgrProps.getCommand();

	if (cmd == null)  {
	    UserMgrException objMgrEx;
	    objMgrEx = new UserMgrException(UserMgrException.NO_CMD_SPEC);
	    objMgrEx.setProperties(userMgrProps);

	    throw(objMgrEx);
	}

	/*
	 * Determine type of command and invoke the relevant check method
	 * to verify the contents of the UserMgrProperties object.
	 *
	 */
	if (cmd.equals(PROP_VALUE_CMD_ADD))  {
	    checkAdd(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DELETE))  {
	    checkDelete(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_LIST))  {
	    checkList(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UPDATE))  {
	    checkUpdate(userMgrProps);

	/*
	 * Private subcommands - for testing
	 */
	} else if (cmd.equals(PROP_VALUE_CMD_EXISTS))  {
	    checkExists(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_GETGROUP))  {
	    checkGetGroup(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_GETGROUPSIZE))  {
	    checkGetGroupSize(userMgrProps);

	} else if (cmd.equals(PROP_VALUE_CMD_ENCODE))  {
	    checkEncode(userMgrProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DECODE))  {
	    checkDecode(userMgrProps);

	} else  {
	    UserMgrException objMgrEx;
	    objMgrEx = new UserMgrException(UserMgrException.BAD_CMD_SPEC);
	    objMgrEx.setProperties(userMgrProps);

	    throw(objMgrEx);
	}
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the 'add' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkAdd(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        String	username, role, passwd;
	
	promptForUserName(userMgrProps);
	checkUserName(userMgrProps);

	/*
	 * Comment out since it was decided -passfile is not
	 * really useful for imqusermgr
	warnForPassword(userMgrProps);
	*/
	promptForPassword(userMgrProps);
        checkPassword(userMgrProps);

	role = userMgrProps.getRole();

	if (role != null)  {
            checkRole(userMgrProps);
	}

        checkNoActiveInAdd(userMgrProps);

    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the 'delete' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkDelete(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkUserName(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the 'list' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkList(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the 'update' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkUpdate(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkUserName(userMgrProps);
	/*
	 * Comment out since it was decided -passfile is not
	 * really useful for imqusermgr
	warnForPassword(userMgrProps);
	*/
        checkPasswordOrActive(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the '.exists' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkExists(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkUserName(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the '.getgroup' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkGetGroup(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkUserName(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the '.getgroupsize' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkGetGroupSize(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkRole(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the private 'encode' command. 
     * This method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkEncode(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkSrc(userMgrProps);
    }

    /*
     * Check UserMgrProperties object to make sure it contains
     * all the correct info to execute the private 'decode' command. 
     * This method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkDecode(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
        checkSrc(userMgrProps);
    }


    private static String checkPassword(UserMgrProperties userMgrProps)
			throws UserMgrException  {
	UserMgrException ex;
	String passwd = userMgrProps.getPassword(),
		passfile = userMgrProps.getPassfile();

	if ((passwd == null) && (passfile == null))  {
	    ex = new UserMgrException(UserMgrException.PASSWD_NOT_SPEC);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	return (passwd);
    }

    private static void checkPasswordOrActive(UserMgrProperties userMgrProps)
			throws UserMgrException  {
	UserMgrException ex;
	String passwd = userMgrProps.getPassword(),
	        passfile = userMgrProps.getPassfile(),
		activeValue = userMgrProps.getActiveValue();

	if ((passfile == null) && (passwd == null) && (activeValue == null))  {
	    ex = new UserMgrException(UserMgrException.PASSWD_OR_ACTIVE_NOT_SPEC);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	/*
	 * The only other check that needs to be done here is for the active value
	 * specified. If none was specified, don't check.
	 */
	if (activeValue == null)  {
	    return;
	}

	if (activeValue.equalsIgnoreCase("t")
	    || activeValue.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    userMgrProps.setActiveValue(Boolean.TRUE.toString());
	    return;
	} else if (activeValue.equalsIgnoreCase("f")
	    || activeValue.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    userMgrProps.setActiveValue(Boolean.FALSE.toString());
	    return;
	}

	ex = new UserMgrException(UserMgrException.BAD_ACTIVE_VALUE_SPEC);
	ex.setProperties(userMgrProps);
	throw(ex);
    }

    private static void checkNoActiveInAdd(UserMgrProperties userMgrProps)
			throws UserMgrException  {
	String	activeValue = userMgrProps.getActiveValue();
	UserMgrException ex;

	/*
	 * The only other check that needs to be done here is for the active value
	 * specified. If none was specified, don't check.
	 */
	if (activeValue == null)  {
	    return;
	}

	ex = new UserMgrException(UserMgrException.ACTIVE_NOT_VALID_WITH_ADD);
	ex.setProperties(userMgrProps);
	
	throw(ex);
    }

    private static void checkSrc(UserMgrProperties userMgrProps)
			throws UserMgrException  {
	String	srcFile = userMgrProps.getSrc();
	UserMgrException ex;

	/*
	 * The only check that needs to be done here is to make sure that the
	 * src file is specified. If none was specified, throw an exception.
	 */
	if (srcFile == null)  {

	    ex = new UserMgrException(UserMgrException.SRC_FILE_NOT_SPEC);
	    ex.setProperties(userMgrProps);
	
	    throw(ex);
	}
    }


    private static String promptForUserName(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
	String userName = userMgrProps.getUserName();

	if (userName != null)  {
	    return(userName);
	}

	userName = UserMgrUtils.getUserInput(userMgrProps, 
					br.getString(br.I_USERNAME));

	userMgrProps.setUserName(userName);
	return (userName);
    }

    private static void promptForPassword(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
	String password = userMgrProps.getPassword(),
		passfile = userMgrProps.getPassfile();

	if (passfile != null)  {
	    return;
	}

	if (password != null)  {
	    return;
	}

	password = UserMgrUtils.getPasswordInput(userMgrProps, 
					br.getString(br.I_PASSWORD));

	userMgrProps.setPassword(password);
    }

    /*
    private static void warnForPassword(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
	String password = userMgrProps.getPassword();

	if (password == null)  {
	    return;
	}

	Output.stdErrPrintln(br.getString(br.W_PASSWD_OPTION_DEPRECATED, OPTION_PASSWD));
	Output.stdErrPrintln("");
    }
    */

    private static String checkUserName(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
	String userName = userMgrProps.getUserName();

	if (userName == null)  {
	    UserMgrException ex = 
		new UserMgrException(UserMgrException.USERNAME_NOT_SPEC);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	if (userName.length() == 0)  {
	    UserMgrException ex = 
		new UserMgrException(UserMgrException.USERNAME_IS_EMPTY);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	if (!isValidUserName(userName))  {
	    UserMgrException ex = 
		new UserMgrException(UserMgrException.ILLEGAL_USERNAME);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	return (userName);
    }

    /**
     * If -i is not specified, defaults to 'imqbroker'
     * Check to make sure that the instance exists and that
     * the password file exists.
     */
    private static void checkInstance(UserMgrProperties userMgrProps, boolean errorIfNotExists) 
			throws UserMgrException  {

	String instance = userMgrProps.getInstance(),
		cmd = userMgrProps.getCommand();

	/*
	 * Don't need to have instance dir created for encode/decode
	 */
	if ((cmd != null) &&
	    (cmd.equals(PROP_VALUE_CMD_ENCODE) ||
	    cmd.equals(PROP_VALUE_CMD_DECODE)))  {
	    return;
        }

	if (instance == null)  {
	    instance = Globals.DEFAULT_INSTANCE;
	    userMgrProps.setInstance(instance);
	}
	Properties props = new Properties(System.getProperties());
	props.put(Globals.IMQ + ".instancename", instance);
	Globals.init(props, false, false);
    String instancedir = Globals.getInstanceEtcDir();
    String pwdirpath = JMQFileUserRepository.getPasswordDirPath(
                           Globals.getConfig(), true);
	File dirpath = new File(pwdirpath);
	File dir = new File(instancedir);
    boolean createpwdFileIfNotExist = true;
    if (dirpath.equals(dir)) {
        createpwdFileIfNotExist = false;
	    if (!dir.exists()) {
           if (errorIfNotExists) {
	        UserMgrException ex =
		    new UserMgrException(UserMgrException.INSTANCE_NOT_EXISTS);
	        ex.setProperties(userMgrProps);
	        throw(ex);
           } else {
               try {
                   Broker.initializePasswdFile();
               } catch (IOException iex) {
	                UserMgrException ex =
		                new UserMgrException(UserMgrException.CANT_CREATE_INSTANCE, iex);
	                ex.setProperties(userMgrProps);
	                throw(ex);
               }
          }
       }
    }

    File pwfile = JMQFileUserRepository.getPasswordFile(Globals.getConfig(), true);

    if (!pwfile.exists()) {
        if (createpwdFileIfNotExist) {
            try {
                pwfile.createNewFile();
                userMgrProps.setPasswordFile(
                    FileUtil.getCanonicalPath(pwfile.toString()));
            } catch (IOException e) {
	            UserMgrException ex = new UserMgrException(
                    UserMgrException.CANT_CREATE_PWFILE, e); 
	            ex.setProperties(userMgrProps);
	            ex.setPasswordFile(FileUtil.getCanonicalPath(pwfile.toString()));
                throw(ex);
            }
        } else {
            UserMgrException ex =
                new UserMgrException(UserMgrException.PW_FILE_NOT_FOUND);
            ex.setProperties(userMgrProps);
            ex.setPasswordFile(FileUtil.getCanonicalPath(pwfile.toString()));
            throw(ex);
        }
    } else {
	    userMgrProps.setPasswordFile(
			FileUtil.getCanonicalPath(pwfile.toString()));
	}
    }

    private static boolean isValidUserName(String userName)  {
	if (userName == null)  {
	    return (false);
	}

	for (OPTION_USERNAME_INVALID_CHARS oc : OPTION_USERNAME_INVALID_CHARS.values())  {
	    if (userName.indexOf(oc.getChar()) != -1)  {
		return (false);
	    }
	}

	return (true);
    }


    private static String checkRole(UserMgrProperties userMgrProps) 
			throws UserMgrException  {
	UserMgrException ex;
	String role = userMgrProps.getRole();

	if (role == null)  {
	    ex = new UserMgrException(UserMgrException.ROLE_NOT_SPEC);
	    ex.setProperties(userMgrProps);

	    throw(ex);
	}

	for (OPTION_ROLE_VALID_VALUES ov : OPTION_ROLE_VALID_VALUES.values())  {
	    if (role.equals(ov.toString()))  {
		return (role);
	    }
	}

	ex = new UserMgrException(UserMgrException.INVALID_ROLE_SPEC);
	ex.setProperties(userMgrProps);

	throw(ex);
    }

    /**
     * Print banner.
     * XXX REVISIT 07/26/00 nakata: Add build number to M_BANNER
     */
    private static void printBanner() {

	Version version = new Version();
	Output.stdOutPrintln(version.getBanner(false));
    }

    /*
     * REVISIT: Is it possible for any option value to be '-h' ?
     */
    private static boolean shortHelpOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OPTION_SHORT_HELP1) || 
		args[i].equals(OPTION_SHORT_HELP2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean longHelpOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OPTION_LONG_HELP1) ||
	   	args[i].equals(OPTION_LONG_HELP2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean versionOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OPTION_VERSION1) ||
		args[i].equals(OPTION_VERSION2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean silentModeOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OPTION_SILENTMODE)) {
		return (true);
	    }
	}
	return (false);
    }

    private static boolean createInstanceSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OPTION_CREATEMODE)) {
		return (true);
	    }
	}
	return (false);
    }

    private static String[] filterSystemProperties(String args[]) {
    ArrayList newargs = new ArrayList();
    boolean found = false;
	for (int n = 0; n < args.length; ++n)  {
	    if (args[n].startsWith(OPTION_SYSTEM_PROPERTY_PREFIX)) {
            found = true;
            int value_index = 0;
            String prop_name = null, prop_value = "";
            value_index = args[n].indexOf('=');
            if (args[n].length() <= 2) { // -D
                continue;
            } else if (value_index < 0) { // -Dfoo
                prop_name = args[n].substring(2);
            } else if (value_index == args[n].length() - 1) { // -Dfoo=
                prop_name = args[n].substring(2, value_index);
            } else { // -Dfoo=bar
                prop_name = args[n].substring(2, value_index);
                prop_value = args[n].substring(value_index + 1);
		    }
	        System.setProperty(prop_name, prop_value);
            continue;
	    }
        newargs.add(args[n]);
	}
    if (!found) {
        return args;
    }
    return (String[])newargs.toArray(new String[newargs.size()]);
    }

    private static void printVersion() {

	Version version = new Version();
	Output.stdOutPrintln(version.getVersion());
	Output.stdOutPrintln(br.getString(br.I_JAVA_VERSION) +
	    System.getProperty("java.version") + " " +
	    System.getProperty("java.vendor") + " " +
	    System.getProperty("java.home"));
    }

    /*
     * Error handling methods
     */

    private static void handleArgsParsingExceptions(OptionException e) {
	String	option = e.getOption();

	if (e instanceof UnrecognizedOptionException)  {
            Output.stdErrPrintln(
		br.getString(br.E_ERROR), 
		br.getKString(br.E_UNRECOG_OPTION, option));

	} else if (e instanceof InvalidBasePropNameException)  {
            Output.stdErrPrintln(
		br.getString(br.E_INTERNAL_ERROR),
		br.getKString(br.E_INVALID_BASE_PROPNAME, option));

	} else if (e instanceof InvalidHardCodedValueException)  {
            Output.stdErrPrintln(
		br.getString(br.E_INTERNAL_ERROR),
		br.getKString(br.E_INVALID_HARDCODED_VAL, option));

	} else if (e instanceof MissingArgException)  {
	    /*
	     * REVISIT:
	     * We can provide more specific messages here depending on what
	     * the option was e.g. for -t:
	     *  Error: An object type was expected for option -t
	     */
            Output.stdErrPrintln(
		br.getString(br.E_ERROR), 
		br.getKString(br.E_MISSING_ARG, option));

	} else if (e instanceof BadNameValueArgException)  {
	    BadNameValueArgException bnvae = (BadNameValueArgException)e;
	    String	badArg = bnvae.getArg();

            Output.stdErrPrintln(
		br.getString(br.E_ERROR), 
		br.getKString(br.E_BAD_NV_ARG, badArg, option));

	} else  {
            Output.stdErrPrintln(
		br.getString(br.E_ERROR), 
		br.getKString(br.E_OPTION_PARSE_ERROR));
	}
    }

    private static void handleCheckOptionsExceptions(UserMgrException e) {
	UserMgrProperties	userMgrProps = e.getProperties();
	String			cmd = userMgrProps.getCommand(),
				role = userMgrProps.getRole(),
				userName = userMgrProps.getUserName(),
				activeValue = userMgrProps.getActiveValue();
	int			type = e.getType();

	/*
	 * REVISIT: should check userMgrProps != null
	 */

	switch (type)  {
	case UserMgrException.NO_CMD_SPEC:
	    printBanner();
            HelpPrinter hp = new HelpPrinter();
	    hp.printShortHelp();
	break;

	case UserMgrException.BAD_CMD_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_BAD_COMMAND_SPEC, userMgrProps.getCommand()));
	break;

	case UserMgrException.PASSWD_NOT_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_PASSWD_NOT_SPEC, OPTION_PASSFILE, OPTION_PASSWD));
	break;

	case UserMgrException.PASSWD_OR_ACTIVE_NOT_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_PASSWD_OR_ACTIVE_NOT_SPEC));
	break;

	case UserMgrException.BAD_ACTIVE_VALUE_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_BAD_ACTIVE_VALUE_SPEC, activeValue));
	break;

	case UserMgrException.INVALID_ROLE_SPEC:
	    /*
	     * The following prints this error message:
	     *	% jmqusermgr add -u username -p passwd -r fooRole
	     *	Error [A3302]: Bad role value specified for the add command: fooRole
	     *	The valid role values are:
	     *		admin
	     *		user
	     *		guest
	     */

	    /*
	     * Prints:
	     *	Error [A3302]: Bad role value specified for the add command: fooRole
	     */
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_INVALID_ROLE_SPEC1, cmd, role));

	    /*
	     * Prints:
	     *  The valid role values are:
	     */
	    Output.stdErrPrintln(br.getString(br.E_INVALID_ROLE_SPEC2));
	    
	    /*
	     * Prints:
	     *	admin
	     *	user
	     *	guest
	     */
	    for (OPTION_ROLE_VALID_VALUES ov : OPTION_ROLE_VALID_VALUES.values())  {
	        Output.stdErrPrintln("\t" + ov);
	    }
	break;

	case UserMgrException.USERNAME_NOT_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_USERNAME_NOT_SPEC, OPTION_USERNAME));
	break;

	case UserMgrException.ROLE_NOT_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_ROLE_NOT_SPEC, OPTION_ROLE));
	break;

	case UserMgrException.ILLEGAL_USERNAME:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_ILLEGAL_USERNAME, userName));
	break;

	case UserMgrException.PROBLEM_GETTING_INPUT:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_PROBLEM_GETTING_INPUT));
	break;

	case UserMgrException.ACTIVE_NOT_VALID_WITH_ADD:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_ACTIVE_NOT_VALID_WITH_ADD));
	break;

	case UserMgrException.INSTANCE_NOT_EXISTS:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_USERMGR_INSTANCE_NOT_EXIST,
				userMgrProps.getInstance()));
	break;

	case UserMgrException.PW_FILE_NOT_FOUND:
	    Output.stdErrPrintln(
                br.getString(br.E_INTERNAL_ERROR), 
		br.getKString(br.E_PW_FILE_NOT_FOUND, e.getPasswordFile()));
	break;

	case UserMgrException.USERNAME_IS_EMPTY:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_USERNAME_IS_EMPTY));
	break;

	case UserMgrException.SRC_FILE_NOT_SPEC:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
                br.getKString(br.E_ENCODE_DECODE_NO_SRC_PASSFILE, OPTION_SRC));
	break;
	case UserMgrException.CANT_CREATE_INSTANCE:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_UNABLE_TO_CREATE_INSTANCE));
	break;
	case UserMgrException.CANT_CREATE_PWFILE:
	    Output.stdErrPrintln(
                br.getString(br.E_ERROR), br.getKString(
                    br.E_CANT_CREATE_PWFILE, e.getPasswordFile(),
                    e.getCause().toString()));
	break;
	default:
            Output.stdErrPrintln(
                br.getString(br.E_ERROR), 
		br.getKString(br.E_OPTION_VALID_ERROR));
	break;
	}
    }

}
