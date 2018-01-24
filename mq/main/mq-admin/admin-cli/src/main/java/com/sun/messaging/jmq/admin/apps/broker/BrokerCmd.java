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
 * @(#)BrokerCmd.java	1.75 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import javax.jms.*;
import javax.naming.*;

import com.sun.messaging.jmq.util.options.OptionException;
import com.sun.messaging.jmq.util.options.UnrecognizedOptionException;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.resources.AdminResources;

/** 
 * This is an administration utility for the MQ broker.
 *
 */
public class BrokerCmd implements BrokerCmdOptions, BrokerConstants  {

    private static AdminResources ar = Globals.getAdminResources();

    /**
     * Constructor
     */
    public BrokerCmd() {
    } 


    public static void main(String[] args)  {
	int exitcode = 0;

	if (silentModeOptionSpecified(args))  {
            Globals.setSilentMode(true);
	}

	/*
	 * Check for -h or -H, or that the first argument
	 * is the command type (-a, -d, -l or -q)
	 * if we still want to have that restriction.
	 */
	if (shortHelpOptionSpecified(args))  {
            BrokerCmdHelpPrinter hp = new BrokerCmdHelpPrinter();
	    hp.printShortHelp(0);
	} else if (longHelpOptionSpecified(args))  {
            BrokerCmdHelpPrinter hp = new BrokerCmdHelpPrinter();
	    hp.printLongHelp();
	}
	/*
	 * Check for -version.
	 */
	if (versionOptionSpecified(args)) {
	    CommonCmdRunnerUtil.printBanner();
        CommonCmdRunnerUtil.printVersion();
	    System.exit(0);
	}

	BrokerCmdProperties brokerCmdProps = null;

	/*
	 * Convert String args[] into a BrokerCmdProperties object.
	 * The BrokerCmdProperties class is just a Properties
	 * subclass with some convenience methods in it.
	 */
	try  {
	    brokerCmdProps = BrokerCmdOptionParser.parseArgs(args);
	} catch (OptionException e)  {
	    CommonCmdRunnerUtil.handleArgsParsingExceptions(e, OPTION_ADMIN_PASSWD, "imqcmd");
            System.exit(1);
	}

	String propFileName = brokerCmdProps.getInputFileName();
	if ((propFileName != null) && !propFileName.equals(""))  {
	    /*
	     * Read in property file.
	     */
	    BrokerCmdProperties tmpProps = new BrokerCmdProperties();
	    try  {
	        FileInputStream	fis = new FileInputStream(propFileName);

	        tmpProps.load(fis);
	    } catch (Exception e)  {
		Globals.stdErrPrintln(
                    ar.getString(ar.I_ERROR_MESG),
		    ar.getKString(ar.E_PROB_LOADING_PROP_FILE), false);
                Globals.stdErrPrintln(e.toString());
                System.exit(1);
	    }

	    /*
	     * Override the values with properties that exist on 
	     * the command line.
	     */
	    for (Enumeration e = brokerCmdProps.propertyNames() ; e.hasMoreElements() ;) {
                String propName = (String)e.nextElement(), propVal;
		/*
		Globals.stdErrPrintln("Override propname: " + propName);
		*/

		if (propName == null)  {
		    continue;
		}

		propVal = brokerCmdProps.getProperty(propName);
		if (propVal == null)  {
		    continue;
		}

		if (!propName.equals(PROP_NAME_OPTION_INPUTFILE))  {
		    tmpProps.put(propName, propVal);
	        }
	    }

	    brokerCmdProps = tmpProps;
	}

	/*
	 * For each command type used, check that the
	 * information passed in is sufficient.
	 */
	try  {
	    checkOptions(brokerCmdProps);
	} catch (UnrecognizedOptionException oe)  {
	    CommonCmdRunnerUtil.handleArgsParsingExceptions(
                           oe, null, "imqcmd");
            System.exit(1);
	} catch (BrokerCmdException ome)  {
	    handleCheckOptionsExceptions(ome);
            System.exit(1);
	}

	/*
	 * Check if any properties were specified via -Dprop=val
	 * and set them via System.setProperty()
	 */
	Properties sysProps = brokerCmdProps.getSysProps();
	if ((sysProps != null) && (sysProps.size() > 0))  {
	    for (Enumeration e = sysProps.propertyNames() ; e.hasMoreElements() ;)  {
	        String name = (String)e.nextElement(),
			value = sysProps.getProperty(name);

		if (brokerCmdProps.adminDebugModeSet())  {
	            Globals.stdOutPrintln("Setting system property: "
				+ name
				+ "="
				+ value);
		}

		try  {
		    System.setProperty(name, value);
		} catch(Exception ex)  {
	            Globals.stdErrPrintln("Failed to set system property: "
				+ name
				+ "="
				+ value);
	            Globals.stdErrPrintln(ex.toString());
		}
            }
	}

	/*
	 * Execute the commands specified by the user
	 */

	CmdRunner cmdRunner = new CmdRunner(brokerCmdProps);
	exitcode = cmdRunner.runCommands();

	System.exit(exitcode);
    }

    /**
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute user commands. This
     * method may print out errors/warnings and exit with an error
     * code.
     *
     * This method is also called from test
     */
    public static void checkOptions(BrokerCmdProperties brokerCmdProps) 
        throws BrokerCmdException, UnrecognizedOptionException  {

	/*
	 * For debugging:
        Globals.stdErrPrintln("BrokerCmdProperties dump:");
	brokerCmdProps.list(System.err);
	Globals.stdErrPrintln("-------------\n");
	 */

	if (brokerCmdProps.debugModeSet() || 
	    brokerCmdProps.noCheckModeSet())  {
	    /*
	    Globals.stdOutPrintln("Option checking turned off.");
	    */
	    return;
	}

	/*
	 * For debugging:
	Globals.stdErrPrintln("Command: " + brokerCmdProps.getCommand());
	Globals.stdErrPrintln("Command Argument: " + brokerCmdProps.getCommandArg());
	Globals.stdErrPrintln("Destination Type: " + brokerCmdProps.getDestType());
	Globals.stdErrPrintln("Target Name: " + brokerCmdProps.getTargetName());
	Globals.stdErrPrintln("Destination Name: " + brokerCmdProps.getDestName());
	Globals.stdErrPrintln("Service Name: " + brokerCmdProps.getServiceName());
	Globals.stdErrPrintln("Broker Host: " + brokerCmdProps.getBrokerHostName());
	Globals.stdErrPrintln("Broker Port: " + brokerCmdProps.getBrokerPort());
	Globals.stdErrPrintln("Admin User ID: " + brokerCmdProps.getAdminUserId());
	Globals.stdErrPrintln("Admin User Password: " + brokerCmdProps.getAdminPasswd());

	Properties props = brokerCmdProps.getTargetAttrs();
	Globals.stdErrPrintln("Target attributes:");
	props.list(System.err);
	Globals.stdErrPrintln("");
	 */

	String cmd = brokerCmdProps.getCommand();

	if (cmd == null)  {
	    BrokerCmdException objMgrEx;
	    objMgrEx = new BrokerCmdException(BrokerCmdException.NO_CMD_SPEC);
	    objMgrEx.setProperties(brokerCmdProps);

	    throw(objMgrEx);
	}

	/*
	 * Check if -pw was used and warn users that it is
	 * deprecated.
	 */
        checkWarnPassword(brokerCmdProps);

	/*
	 * Determine type of command and invoke the relevant check method
	 * to verify the contents of the BrokerCmdProperties object.
	 *
	 */
	if (cmd.equals(PROP_VALUE_CMD_LIST))  {
	    checkList(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PAUSE))  {
	    checkPause(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESUME))  {
	    checkResume(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_SHUTDOWN))  {
	    checkShutdown(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESTART))  {
	    checkRestart(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CREATE))  {
	    checkCreate(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DESTROY))  {
	    checkDestroy(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DESTROYALL))  {
	    checkDestroyAll(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PURGE))  {
	    checkPurge(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PURGEALL))  {
	    checkPurgeAll(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UPDATE))  {
	    checkUpdate(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_QUERY))  {
	    checkQuery(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_METRICS))  {
	    checkMetrics(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RELOAD))  {
	    checkReload(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CHANGEMASTER))  {
	    checkChangeMaster(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_COMMIT))  {
	    checkCommit(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_ROLLBACK))  {
	    checkRollback(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_COMPACT))  {
	    checkCompact(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_QUIESCE))  {
	    checkQuiesce(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_TAKEOVER))  {
	    checkTakeover(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_MIGRATESTORE))  {
	    checkMigrateStore(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UNQUIESCE))  {
	    checkUnquiesce(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESET))  {
	    checkReset(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CHECKPOINT))  {
	    checkCheckpoint(brokerCmdProps);

        /*
         * Private subcommands - to support testing only
         */
	} else if (cmd.equals(PROP_VALUE_CMD_EXISTS))  {
	    checkExists(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_GETATTR))  {
	    checkGetAttr(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UNGRACEFUL_KILL))  {
	    checkUngracefulKill(brokerCmdProps);

	} else  {
	    BrokerCmdException objMgrEx;
	    objMgrEx = new BrokerCmdException(BrokerCmdException.BAD_CMD_SPEC);
	    objMgrEx.setProperties(brokerCmdProps);

	    throw(objMgrEx);
	}

	/*
	 * Check if receiveTimeout value is valid, if it is specified.
	 */
	String recvTimeoutStr = brokerCmdProps.getProperty(PROP_NAME_OPTION_RECV_TIMEOUT);
	if (recvTimeoutStr != null)  {
	    try  {
	        //int timeout;
		//timeout = checkIntegerValue
                //         (brokerCmdProps, PROP_NAME_OPTION_RECV_TIMEOUT, recvTimeoutStr);
	        checkIntegerValue
	                  (brokerCmdProps, PROP_NAME_OPTION_RECV_TIMEOUT, recvTimeoutStr);
	    } catch (Exception e)  {
	        BrokerCmdException bce;
	        bce = new BrokerCmdException(BrokerCmdException.INVALID_RECV_TIMEOUT_VALUE);
	        bce.setProperties(brokerCmdProps);
	  	bce.setErrorString(recvTimeoutStr);

	        throw(bce);
	    }
	}

	/*
	 * Check if numRetries value is valid, if it is specified.
	 */
	String numRetriesStr = brokerCmdProps.getProperty(PROP_NAME_OPTION_NUM_RETRIES);
	if (numRetriesStr != null)  {
	    try  {
	        //int numRetries;
	        //numRetries = checkIntegerValue
	        //          (brokerCmdProps, PROP_NAME_OPTION_NUM_RETRIES, numRetriesStr);
	        checkIntegerValue
                          (brokerCmdProps, PROP_NAME_OPTION_NUM_RETRIES, numRetriesStr);

	    } catch (Exception e)  {
	        BrokerCmdException bce;
	        bce = new BrokerCmdException(BrokerCmdException.INVALID_NUM_RETRIES_VALUE);
	        bce.setProperties(brokerCmdProps);
	  	bce.setErrorString(numRetriesStr);

	        throw(bce);
	    }
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'list' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkList(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {

	checkCmdArg(brokerCmdProps, 
            CommonCmdRunnerUtil.toStringArray(CMD_LIST_VALID_CMDARGS.values()));

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
	    String s = brokerCmdProps.getDestType();

	    if (s != null)  {
		checkDestType(brokerCmdProps);
	    }
	} else if (cmdArg.equals(CMDARG_MSG))  {
	    checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
    /* LKS -  No destination is now allowed - it lists all durables
	} else if (cmdArg.equals(CMDARG_DURABLE))  {
	    checkDestName(brokerCmdProps);
    */
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'compact' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkCompact(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_COMPACT_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
	    String destType = brokerCmdProps.getDestType(),
	            destName = brokerCmdProps.getTargetName();

	    if (destType != null)  {
		checkDestType(brokerCmdProps);
		checkTargetName(brokerCmdProps);
	    } else if (destName != null)  {
		checkDestType(brokerCmdProps);
	    }
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'quiesce' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkQuiesce(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_QUIESCE_VALID_CMDARGS);
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'unquiesce' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkUnquiesce(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_UNQUIESCE_VALID_CMDARGS);
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'takeover' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkTakeover(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_TAKEOVER_VALID_CMDARGS);

        checkTargetName(brokerCmdProps);
    }

    private static void checkMigrateStore(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_MIGRATESTORE_VALID_CMDARGS);

    }


    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'pause' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkPause(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {

	checkCmdArg(brokerCmdProps, 
            CommonCmdRunnerUtil.toStringArray(CMD_PAUSE_VALID_CMDARGS.values()));

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_SERVICE))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_DESTINATION)) {
	    String destType = brokerCmdProps.getDestType(),
	            destName = brokerCmdProps.getTargetName();

	    if (destType != null)  {
		checkDestType(brokerCmdProps);
		checkTargetName(brokerCmdProps);
	    } else if (destName != null)  {
		checkDestType(brokerCmdProps);
	    }

	    /*
	     * Check pause type
	     */
            checkPauseDstType(brokerCmdProps);
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'resume' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkResume(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_RESUME_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_SERVICE))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_DESTINATION)) {
	    String destType = brokerCmdProps.getDestType(),
	            destName = brokerCmdProps.getTargetName();

	    if (destType != null)  {
		checkDestType(brokerCmdProps);
		checkTargetName(brokerCmdProps);
	    } else if (destName != null)  {
		checkDestType(brokerCmdProps);
	    }
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'reset' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkReset(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_RESET_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_BROKER))  {
            checkResetType(brokerCmdProps);
	}
    }
    
    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'checkpoint' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkCheckpoint(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	
	
	
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'shutdown' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkShutdown(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_SHUTDOWN_VALID_CMDARGS);

	checkTimeValue(brokerCmdProps);


	/*
	 * FIXME: Does shutdown apply to services ?
	String	cmdArg = brokerCmdProps.getCommandArg();
	if (cmdArg.equals(CMDARG_SERVICE))  {
            checkTargetName(brokerCmdProps);
	}
	 */
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'restart' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkRestart(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_RESTART_VALID_CMDARGS);


	/*
	 * FIXME: Does restart apply to services ?
	String	cmdArg = brokerCmdProps.getCommandArg();
	if (cmdArg.equals(CMDARG_SERVICE))  {
            checkTargetName(brokerCmdProps);
	}
	 */
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'create' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkCreate(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_CREATE_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION)) {
	    int		exceptionType = -1;
	    String	destType;
	    String	validAttrs[] = null,
			deprecatedAttrs[] = null;

	    /*
	     * Check destination type (-t)
	     */
            checkDestType(brokerCmdProps);

	    /*
	     * Check target name (-n)
	     */
            checkTargetName(brokerCmdProps);

	    destType = brokerCmdProps.getDestType();
	    if (destType.equals(PROP_VALUE_DEST_TYPE_TOPIC))  {
		validAttrs = CREATE_DST_TOPIC_VALID_ATTRS;
		exceptionType = BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_TOPIC;
	    } else if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE)) {
		validAttrs = CREATE_DST_QUEUE_VALID_ATTRS;
		deprecatedAttrs = CREATE_DST_QUEUE_DEPRECATED_ATTRS;
		exceptionType = BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_QUEUE;
	    }

	    /*
	     * Check for deprecated attribute names (-o)
	     */
	    checkDeprecatedAttrs(brokerCmdProps, deprecatedAttrs);

	    /*
	     * Check for valid attribute names (-o)
	     */
	    checkValidAttrs(brokerCmdProps, validAttrs, exceptionType);

	    // Check attribute values.
	    // Note: These values can be checked against a null value within each
	    // checkXXX method, but it is cleaner to check for them upfront. 
            Properties attrs = brokerCmdProps.getTargetAttrs();
            String value;

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_MESG_BYTE);
 	    if (value != null)
	        checkByteValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_MESG_BYTE, value);

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_PER_MESG_SIZE);
 	    if (value != null)
	        checkByteValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_PER_MESG_SIZE, value);

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_MESG);
 	    if (value != null)
	        checkLongValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_MESG, value);

            value = attrs.getProperty(PROP_NAME_IS_LOCAL_DEST);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
				PROP_NAME_IS_LOCAL_DEST, value);

            value = attrs.getProperty(PROP_NAME_LIMIT_BEHAVIOUR);
 	    if (value != null)
	        checkLimitBehaviourValue(brokerCmdProps, 
				PROP_NAME_LIMIT_BEHAVIOUR, value);

            value = attrs.getProperty(PROP_NAME_CONSUMER_FLOW_LIMIT);
 	    if (value != null)
	        checkIntegerValue(brokerCmdProps, 
				PROP_NAME_CONSUMER_FLOW_LIMIT, value);

            value = attrs.getProperty(PROP_NAME_MAX_PRODUCERS);
 	    if (value != null)
	        checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_PRODUCERS, value);

            value = attrs.getProperty(PROP_NAME_USE_DMQ);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_USE_DMQ, value);

            value = attrs.getProperty(PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED, value);

            value = attrs.getProperty(PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE, value);

	    if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE)) {
                value = attrs.getProperty(PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT);
 	        if (value != null)
	            checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, value);

                value = attrs.getProperty(PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT);
 	        if (value != null)
	            checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, value);

                value = attrs.getProperty(PROP_NAME_LOCAL_DELIVERY_PREF);
 	        if (value != null)
	            checkBooleanValue(brokerCmdProps, 
				PROP_NAME_LOCAL_DELIVERY_PREF, value);
	    }

	    /*
	     * Check for unlimited values.
	     * If an unlimited value of '0' was specified:
	     *  - print warning
	     *  - convert '0' value to '-1'
	     */
	    checkUnlimitedValues(brokerCmdProps, DEST_ATTRS_UNLIMITED_CONV);
	}
    }

    private static void checkValidAttrs(BrokerCmdProperties brokerCmdProps, 
				String validAttrs[], int exceptionType) 
							throws BrokerCmdException { 
        Properties attrs = brokerCmdProps.getTargetAttrs();
        BrokerCmdException ex = null;

	if (attrs == null)  {
	    return;
	}

        for (Enumeration e = attrs.propertyNames() ; e.hasMoreElements() ;) {
	    String oneAttrName = (String)e.nextElement();
	    if (!arrayContainsStr(validAttrs, oneAttrName))  {
        	ex = new BrokerCmdException(exceptionType);
		ex.setProperties(brokerCmdProps);
		ex.setValidAttrs(validAttrs);
		ex.setBadAttr(oneAttrName);
                throw(ex);
	    }
        }
    }

    private static void checkCreateOnlyAttrs(BrokerCmdProperties brokerCmdProps, 
				String createOnlyAttrs[], String validAttrs[], int createOnlyExceptionType) 
							throws BrokerCmdException { 
        Properties attrs = brokerCmdProps.getTargetAttrs();
        BrokerCmdException ex = null;

	if (attrs == null)  {
	    return;
	}

        for (Enumeration e = attrs.propertyNames() ; e.hasMoreElements() ;) {
	    String oneAttrName = (String)e.nextElement();
	    if (arrayContainsStr(createOnlyAttrs, oneAttrName))  {
        	ex = new BrokerCmdException(createOnlyExceptionType);
		ex.setProperties(brokerCmdProps);
		ex.setValidAttrs(validAttrs);
		ex.setBadAttr(oneAttrName);
                throw(ex);
	    }
        }
    }

    private static void checkDeprecatedAttrs(BrokerCmdProperties brokerCmdProps, 
				String deprecatedAttrs[]) 
							throws BrokerCmdException { 
        Properties attrs = brokerCmdProps.getTargetAttrs();
        BrokerCmdException ex = null;

	if ((attrs == null) || (deprecatedAttrs == null))  {
	    return;
	}

        for (Enumeration e = attrs.propertyNames() ; e.hasMoreElements() ;) {
	    String oneAttrName = (String)e.nextElement();
	    if (arrayContainsStr(deprecatedAttrs, oneAttrName))  {
		String value = attrs.getProperty(oneAttrName);
		handleDeprecatedAttr(brokerCmdProps, oneAttrName, value);
	    }
        }
    }

    private static void handleDeprecatedAttr(BrokerCmdProperties brokerCmdProps,
				String deprecatedAttr, String deprecatedValue)
						throws BrokerCmdException { 
        Properties attrs = brokerCmdProps.getTargetAttrs();

	if (deprecatedAttr.equals(PROP_NAME_QUEUE_FLAVOUR))  {
	    String flavor = deprecatedValue,
		   maxFailoverCons = null,
		   maxActiveCons = null;

	    /*
	     * Cases:
	     * 1. QDP specified but with bad/wrong values
	     * 2. QDP specified
	     * 3. QDP specified, active/backup also specified
	     */

	    if (flavor == null)  {
		return;
	    }

	    /*
	     * 1. QDP specified but with bad/wrong values
	     *
	     * This will check if a bad queue delivery policy is
	     * specified, in which case we print an error (which
	     * now includes the fact that the attr is deprecated)
	     * an exit.
	     *
	     * The following method will throw an exception.
	     */
	    checkFlavorType(brokerCmdProps, flavor, 
		BrokerCmdException.DST_QDP_VALUE_INVALID);

	    maxFailoverCons = attrs.getProperty(
				PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT);
	    maxActiveCons = attrs.getProperty(
				PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT);

	    if ((maxFailoverCons == null) && (maxActiveCons == null))  {
	        /*
	         * 2. QDP specified
		 *
		 * maxFailover/maxActive values not specified, so:
		 *  - convert the QDP value.
		 *  - add converted values to props object
		 *  - print warning and the what converted values are used
		 */
		if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_SINGLE))  {
	            maxActiveCons = "1";
	            maxFailoverCons = "0";
		} else if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_FAILOVER))  {
	            maxActiveCons = "1";
	            maxFailoverCons = "-1";
		} else if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN))  {
	            maxActiveCons = "-1";
	            maxFailoverCons = "0";
		}

		/*
		 * Add new maxNumBackupConsumers/maxNumActiveConsumers
		 * attrs to brokerCmdProps.
		 */
		brokerCmdProps.setTargetAttr(
			PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, maxFailoverCons);
		brokerCmdProps.setTargetAttr(
			PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, maxActiveCons);
		
		/*
		 * Print warning about usage of QDP and what values
		 * for maxNumBackupConsumers/maxNumActiveConsumers will
		 * be used instead.
		 */
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_DST_QDP_DEPRECATED));

		Object args2[] = {flavor, maxFailoverCons, maxActiveCons};
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_DST_QDP_DEPRECATED_CONV, args2));
	    } else  {
		/*
	         * 3. QDP specified, active/backup also specified
		 *
		 * Print warning and mention that the QDP value will be
		 * ignored because maxNumBackupConsumers/maxNumActiveConsumers
		 * was specified.
		 */
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_DST_QDP_DEPRECATED));
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_DST_QDP_DEPRECATED_IGNORE, flavor));
	    }

	    /*
	     * Remove QDP property from props object
	     */
	    brokerCmdProps.removeTargetAttr(PROP_NAME_QUEUE_FLAVOUR);
	} else if (deprecatedAttr.equals(
		BrokerConstants.PROP_NAME_BKR_QUEUE_DELIVERY_POLICY))  {
            String flavor = deprecatedValue,
		   maxFailoverCons = null,
		   maxActiveCons = null;

	    /*
	     * Cases:
	     * 1. QDP specified but with bad/wrong values
	     * 2. QDP specified
	     * 3. QDP specified, active/backup also specified
	     */

	    if (flavor == null)  {
		return;
	    }

	    /*
	     * 1. QDP specified but with bad/wrong values
	     *
	     * This will check if a bad queue delivery policy is
	     * specified, in which case we print an error (which
	     * now includes the fact that the attr is deprecated)
	     * an exit.
	     *
	     * The following method will throw an exception.
	     */
	    checkFlavorType(brokerCmdProps, flavor, 
		BrokerCmdException.BKR_QDP_VALUE_INVALID);

	    maxFailoverCons = attrs.getProperty(
		BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS);
	    maxActiveCons = attrs.getProperty(
		BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS);

	    if ((maxFailoverCons == null) && (maxActiveCons == null))  {
	        /*
	         * 2. QDP specified
		 *
		 * max backup/max active not
		 * specified, so:
		 *  - convert the QDP value.
		 *  - add converted values to props object
		 *  - print warning and the what converted values are used
		 */
		if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_SINGLE))  {
	            maxActiveCons = "1";
	            maxFailoverCons = "0";
		} else if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_FAILOVER))  {
	            maxActiveCons = "1";
	            maxFailoverCons = "-1";
		} else if (flavor.equals(PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN))  {
	            maxActiveCons = "-1";
	            maxFailoverCons = "0";
		}

		/*
		 * Add new max backup/max active
		 * attrs to brokerCmdProps.
		 */
		brokerCmdProps.setTargetAttr(
		    BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS,
		    maxFailoverCons);
		brokerCmdProps.setTargetAttr(
		    BrokerConstants.PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
		    maxActiveCons);
		
		/*
		 * Print warning about usage of QDP and what values
		 * for max backup/max active will be used instead.
		 */
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_BKR_QDP_DEPRECATED));
		Object args[] = {flavor, maxFailoverCons, maxActiveCons};
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_BKR_QDP_DEPRECATED_CONV, args));
	    } else  {
		/*
	         * 3. QDP specified, active/backup also specified
		 *
		 * Print warning and mention that the QDP value will be
		 * ignored because max backup/max active
		 * was specified.
		 */
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_BKR_QDP_DEPRECATED));
	        Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_BKR_QDP_DEPRECATED_IGNORE, flavor));
	    }

	    /*
	     * Remove QDP property from props object
	     */
	    brokerCmdProps.removeTargetAttr(
		BrokerConstants.PROP_NAME_BKR_QUEUE_DELIVERY_POLICY);
	}
    }

    private static void checkUnlimitedValues(BrokerCmdProperties brokerCmdProps,
			String unlimitedAttrs[]) 
							throws BrokerCmdException { 
        Properties attrs = brokerCmdProps.getTargetAttrs(),
		    saveAttrs = new Properties();
        BrokerCmdException ex = null;

	if ((attrs == null) || (unlimitedAttrs == null))  {
	    return;
	}

        for (Enumeration e = attrs.propertyNames() ; e.hasMoreElements() ;) {
	    String oneAttrName = (String)e.nextElement();
	    if (arrayContainsStr(unlimitedAttrs, oneAttrName))  {
		String value = attrs.getProperty(oneAttrName);

		/*
		 * 0 was specified as an unlimited value.
		 * We need to:
		 *  - print a friendly 'warning' message saying that '-1'
		 *    is the unlimited value now and that we have converted
		 *    the value to -1
		 *  - convert (ie write back to props object) value from 0 to
		 *    -1
		 */
		if (value.equals("0"))  {
		    saveAttrs.setProperty(oneAttrName, value);
		}
	    }
        }

	if (saveAttrs.size() > 0)  {
	    Globals.stdErrPrintln(
                    ar.getString(ar.I_WARNING_MESG), 
		    ar.getKString(ar.W_ZERO_UNLIMITED_SPECIFIED, "0"));

            for (Enumeration e = saveAttrs.propertyNames() ; e.hasMoreElements() ;) {
	        String oneAttrName = (String)e.nextElement(),
		        value = attrs.getProperty(oneAttrName);

	        Globals.stdErrPrintln("\t" 
				+ oneAttrName
				+ "="
				+ value);
	    }
	    Globals.stdErrPrintln("");

	    Globals.stdErrPrintln(
		    ar.getString(ar.W_NEW_UNLIMITED_VALUE, "-1"));
	    Globals.stdErrPrintln(ar.getString(ar.W_CONVERTED_UNLIMITED_VALUE));

            for (Enumeration e = saveAttrs.propertyNames() ; e.hasMoreElements() ;) {
	        String oneAttrName = (String)e.nextElement();
		//String value = attrs.getProperty(oneAttrName);

	        Globals.stdErrPrintln("\t"
				+ oneAttrName
				+ "=-1");
	        brokerCmdProps.setTargetAttr(oneAttrName, "-1");
	    }
	    Globals.stdErrPrintln("");

	}
    }

    private static void checkValidSingleAttr(BrokerCmdProperties brokerCmdProps,
                                String validAttrs[]) throws BrokerCmdException {
        String attr = brokerCmdProps.getSingleTargetAttr();
        BrokerCmdException ex = null;

        if (attr == null) {
            ex = new BrokerCmdException(BrokerCmdException.SINGLE_TARGET_ATTR_NOT_SPEC);
            ex.setProperties(brokerCmdProps);
            throw(ex);
	}

        if ( (validAttrs != null) && !arrayContainsStr(validAttrs, attr))  {
            ex = new BrokerCmdException(BrokerCmdException.BAD_ATTR_SPEC_GETATTR);
            ex.setProperties(brokerCmdProps);
            ex.setValidAttrs(validAttrs);
            ex.setBadAttr(attr);
            throw(ex);
        }
    }

    private static boolean arrayContainsStr(String strArray[], String str)  {
	if ((strArray == null) || (str == null))  {
	    return (false);
	}

	for (int i = 0; i < strArray.length; ++i) {
	    if (str.equals(strArray[i]))  {
		return (true);
	    }
	}

	return (false);
    }

    private static void checkFlavorType
	    (BrokerCmdProperties brokerCmdProps, String flavor) 
	    throws BrokerCmdException { 
        checkFlavorType(brokerCmdProps, flavor, -1);
    }

    private static void checkFlavorType
	    (BrokerCmdProperties brokerCmdProps, String flavor, 
	    int exceptionType) throws BrokerCmdException { 
        BrokerCmdException ex = null;

	if (exceptionType == -1)  {
	    exceptionType = BrokerCmdException.FLAVOUR_TYPE_INVALID;
	}

        if ((!PROP_VALUE_QUEUE_FLAVOUR_SINGLE.equals(flavor)) &&
            (!PROP_VALUE_QUEUE_FLAVOUR_FAILOVER.equals(flavor)) &&
            (!PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN.equals(flavor))) {

            ex = new BrokerCmdException(exceptionType);
	    ex.setProperties(brokerCmdProps);
            ex.setBadValue(flavor);

            throw(ex);
        }
    }

    private static void checkPauseDstType(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
        BrokerCmdException ex = null;

	String pauseType = brokerCmdProps.getPauseType();
	if (pauseType == null)  {
	    return;
	}

	for (int i = 0; i < PAUSE_DST_TYPE_VALID_VALUES.length; ++i)  {
	    if (pauseType.equals(PAUSE_DST_TYPE_VALID_VALUES[i]))  {
		return;
	    }
	}

        ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_PAUSE_TYPE);
        ex.setProperties(brokerCmdProps);
	ex.setBadValue(pauseType);
	throw(ex);
    }

    private static void checkResetType(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
        BrokerCmdException ex = null;

	String resetType = brokerCmdProps.getResetType();
	if (resetType == null)  {
	    return;
	}

	for (int i = 0; i < RESET_BKR_TYPE_VALID_VALUES.length; ++i)  {
	    if (resetType.equals(RESET_BKR_TYPE_VALID_VALUES[i]))  {
		return;
	    }
	}

        ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_RESET_TYPE);
        ex.setProperties(brokerCmdProps);
	ex.setBadValue(resetType);
	throw(ex);
    }

    private static void checkMetricInterval(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String s = brokerCmdProps.getProperty(PROP_NAME_OPTION_METRIC_INTERVAL);
        BrokerCmdException ex = null;

	if (s == null)  {
	    return;
	}

	try  {
	    long l = Long.parseLong(s);

            if ( l < 0) {
                ex = new BrokerCmdException(
			BrokerCmdException.INVALID_METRIC_INTERVAL);
                ex.setProperties(brokerCmdProps);
		ex.setBadValue(s);
	   	throw(ex);
	    }
	} catch (NumberFormatException nfe)  {
            ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_METRIC_INTERVAL);
            ex.setProperties(brokerCmdProps);
	    ex.setBadValue(s);
	    throw(ex);
	}
    }

    private static void checkMetricSamples(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String s = brokerCmdProps.getProperty(PROP_NAME_OPTION_METRIC_SAMPLES);
        BrokerCmdException ex = null;

	if (s == null)  {
	    return;
	}

	try  {
	    int i = Integer.parseInt(s);

            if ((i < 0) && (i != -1)) {
                ex = new BrokerCmdException(
			BrokerCmdException.INVALID_METRIC_SAMPLES);
                ex.setProperties(brokerCmdProps);
		ex.setBadValue(s);
	   	throw(ex);
	    }
	} catch (NumberFormatException nfe)  {
            ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_METRIC_SAMPLES);
            ex.setProperties(brokerCmdProps);
	    ex.setBadValue(s);
	    throw(ex);
	}
    }

    private static void checkMetricType(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String metricType = brokerCmdProps.getMetricType();
        BrokerCmdException ex = null;

	if (metricType == null)  {
	    return;
	}

	for (int i = 0; i < METRIC_TYPE_VALID_VALUES.length; ++i)  {
	    if (metricType.equals(METRIC_TYPE_VALID_VALUES[i]))  {
		return;
	    }
	}

        ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_METRIC_TYPE);
        ex.setProperties(brokerCmdProps);
	ex.setBadValue(metricType);
	throw(ex);
    }

    private static void checkMetricDstType(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String metricType = brokerCmdProps.getMetricType();
        BrokerCmdException ex = null;

	if (metricType == null)  {
	    return;
	}

	for (int i = 0; i < METRIC_DST_TYPE_VALID_VALUES.length; ++i)  {
	    if (metricType.equals(METRIC_DST_TYPE_VALID_VALUES[i]))  {
		return;
	    }
	}

        ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_METRIC_DST_TYPE);
        ex.setProperties(brokerCmdProps);
	ex.setBadValue(metricType);
	throw(ex);
    }

    private static int checkIntegerValue
	(BrokerCmdProperties brokerCmdProps, String type, String maxValue)
	throws BrokerCmdException {
        BrokerCmdException ex = null;
        int iValue;

        try {
            iValue = Integer.parseInt(maxValue);
            if ( iValue < -1) {
                ex = new BrokerCmdException(BrokerCmdException.INVALID_INTEGER_VALUE);
                ex.setProperties(brokerCmdProps);
	  	ex.setErrorString(type);

	   	throw(ex);
	    }

        } catch (Exception e) {
            ex = new BrokerCmdException(BrokerCmdException.INVALID_INTEGER_VALUE);
            ex.setProperties(brokerCmdProps);
	    ex.setErrorString(type);

	    throw(ex);
	}

	return (iValue);
    }

    private static long checkLongValue
	(BrokerCmdProperties brokerCmdProps, String type, String maxValue)
	throws BrokerCmdException {
        BrokerCmdException ex = null;
        long lValue;

        try {
            lValue = Long.parseLong(maxValue);
            if ( lValue < -1) {
                ex = new BrokerCmdException(BrokerCmdException.INVALID_INTEGER_VALUE);
                ex.setProperties(brokerCmdProps);
	  	ex.setErrorString(type);

	   	throw(ex);
	    }

        } catch (Exception e) {
            ex = new BrokerCmdException(BrokerCmdException.INVALID_INTEGER_VALUE);
            ex.setProperties(brokerCmdProps);
	    ex.setErrorString(type);

	    throw(ex);
	}

	return (lValue);
    }

    private static void checkByteValue
	(BrokerCmdProperties brokerCmdProps, String type, String byteString)
	throws BrokerCmdException {
        BrokerCmdException ex = null;

        try {

	    SizeString	ss = new SizeString(byteString);
	    long	bytesValue;

	    bytesValue = ss.getBytes();

	    if (bytesValue < -1)  {
                ex = new BrokerCmdException(BrokerCmdException.INVALID_BYTE_VALUE);
                ex.setProperties(brokerCmdProps);
	  	ex.setErrorString(type);

	   	throw(ex);
	    }

        } catch (Exception e) {
            ex = new BrokerCmdException(BrokerCmdException.INVALID_BYTE_VALUE);
            ex.setProperties(brokerCmdProps);
	    ex.setErrorString(type);

	    throw(ex);
	}
    }

    private static void checkBooleanValue
	(BrokerCmdProperties brokerCmdProps, String type, String boolValue)
	throws BrokerCmdException {
        BrokerCmdException ex = null;

	if ((boolValue.equalsIgnoreCase("true")) || 
	    (boolValue.equalsIgnoreCase("t")) || 
	    (boolValue.equalsIgnoreCase("false")) || 
	    (boolValue.equalsIgnoreCase("f")) )  {
	    return;
	}

        ex = new BrokerCmdException(BrokerCmdException.INVALID_BOOLEAN_VALUE);
        ex.setProperties(brokerCmdProps);
	ex.setErrorString(type);

	throw(ex);
    }

    private static void checkLogLevelValue
	(BrokerCmdProperties brokerCmdProps, String type, String logLevelValue)
	throws BrokerCmdException {
        BrokerCmdException ex = null;

	for (String value : BKR_LOG_LEVEL_VALID_VALUES)  {
	    if (logLevelValue.equals(value)) {
		return;
	    }
	}

        ex = new BrokerCmdException(BrokerCmdException.INVALID_LOG_LEVEL_VALUE);
        ex.setProperties(brokerCmdProps);
	ex.setErrorString(type);

	throw(ex);
    }

    private static void checkTimeValue(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String s = brokerCmdProps.getProperty(PROP_NAME_OPTION_TIME);
        BrokerCmdException ex = null;

	if (s == null)  {
	    return;
	}

	try  {
	    int i = Integer.parseInt(s);

            if (i < 0)  {
                ex = new BrokerCmdException(
			BrokerCmdException.INVALID_TIME);
                ex.setProperties(brokerCmdProps);
		ex.setBadValue(s);
	   	throw(ex);
	    }
	} catch (NumberFormatException nfe)  {
            ex = new BrokerCmdException(
	            BrokerCmdException.INVALID_TIME);
            ex.setProperties(brokerCmdProps);
	    ex.setBadValue(s);
	    throw(ex);
	}
    }

    private static void checkLimitBehaviourValue
	(BrokerCmdProperties brokerCmdProps, String type, String limitBehaviourValue)
	throws BrokerCmdException {
        BrokerCmdException ex = null;

	for (String value : BKR_LIMIT_BEHAV_VALID_VALUES)  {
	    if (limitBehaviourValue.equals(value))  {
		return;
	    }
	}

        ex = new BrokerCmdException(BrokerCmdException.INVALID_LIMIT_BEHAV_VALUE);
        ex.setProperties(brokerCmdProps);
	ex.setErrorString(type);

	throw(ex);
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'destroy' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkDestroy(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_DESTROY_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
            checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_DURABLE))  {
	    checkTargetName(brokerCmdProps);
	    //checkClientID(brokerCmdProps); JMS 2.0
	} else if (cmdArg.equals(CMDARG_CONNECTION))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_MSG))  {
	    checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
            checkMsgID(brokerCmdProps);
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'purge' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkPurge(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_PURGE_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
            checkTargetName(brokerCmdProps);
	    checkDestType(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_DURABLE))  {
	    checkTargetName(brokerCmdProps);
	    //checkClientID(brokerCmdProps); JMS 2.0
	}
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the '.destroyall' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkDestroyAll(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_DESTROYALL_VALID_CMDARGS);
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the '.purgeall' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkPurgeAll(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_PURGEALL_VALID_CMDARGS);
    }

    /*
     * Check BrokerCmdProperties object to make sure it contains
     * all the correct info to execute the 'update' command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkUpdate(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_UPDATE_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
	    int		exceptionType = -1, createOnlyExceptionType = -1;
	    String	destType;
	    String	validAttrs[] = null,
			createOnlyAttrs[] = null;

	    /*
	     * Check destination type (-t)
	     */
            checkDestType(brokerCmdProps);

	    /*
	     * Check target name (-n).
	     */
            checkTargetName(brokerCmdProps);

	    /*
	     * Make sure some attrs were specified via -o.
	     */
            checkTargetAttrs(brokerCmdProps);

	    destType = brokerCmdProps.getDestType();
	    if (destType.equals(PROP_VALUE_DEST_TYPE_TOPIC))  {
		createOnlyAttrs = CREATE_ONLY_DST_ATTRS;
		validAttrs = UPDATE_DST_TOPIC_VALID_ATTRS;
		exceptionType = BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_TOPIC;
		createOnlyExceptionType = BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_TOPIC;
	    } else if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE)) {
		createOnlyAttrs = CREATE_ONLY_DST_ATTRS;
		validAttrs = UPDATE_DST_QUEUE_VALID_ATTRS;
		exceptionType = BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_QUEUE;
		createOnlyExceptionType = BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_QUEUE;
	    }

	    /*
	     * Check for create only attribute names (-o)
	     */
	    checkCreateOnlyAttrs(brokerCmdProps, createOnlyAttrs, validAttrs, 
							createOnlyExceptionType);

	    /*
	     * Check attribute names passed in via -o.
	     */
	    checkValidAttrs(brokerCmdProps, validAttrs, exceptionType);

	    // Check attribute values.
	    // Note: These values can be checked against a null value within each
	    // checkXXX method, but it is cleaner to check for them upfront. 
            Properties attrs = brokerCmdProps.getTargetAttrs();
            String value;

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_MESG_BYTE);
 	    if (value != null)
	        checkByteValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_MESG_BYTE, value);

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_PER_MESG_SIZE);
 	    if (value != null)
	        checkByteValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_PER_MESG_SIZE, value);

            value = attrs.getProperty(PROP_NAME_OPTION_MAX_MESG);
 	    if (value != null)
	        checkLongValue(brokerCmdProps, 
				PROP_NAME_OPTION_MAX_MESG, value);

            value = attrs.getProperty(PROP_NAME_LIMIT_BEHAVIOUR);
 	    if (value != null)
	        checkLimitBehaviourValue(brokerCmdProps, 
				PROP_NAME_LIMIT_BEHAVIOUR, value);

            value = attrs.getProperty(PROP_NAME_CONSUMER_FLOW_LIMIT);
 	    if (value != null)
	        checkIntegerValue(brokerCmdProps, 
				PROP_NAME_CONSUMER_FLOW_LIMIT, value);

            value = attrs.getProperty(PROP_NAME_MAX_PRODUCERS);
 	    if (value != null)
	        checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_PRODUCERS, value);

            value = attrs.getProperty(PROP_NAME_USE_DMQ);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_USE_DMQ, value);

            value = attrs.getProperty(PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED, value);

            value = attrs.getProperty(PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE);
 	    if (value != null)
	        checkBooleanValue(brokerCmdProps, 
	                        PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE, value);

	    if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE)) {
                value = attrs.getProperty(PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT);
 	        if (value != null)
	            checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT, value);

                value = attrs.getProperty(PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT);
 	        if (value != null)
	            checkIntegerValue(brokerCmdProps, 
				PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT, value);

                value = attrs.getProperty(PROP_NAME_LOCAL_DELIVERY_PREF);
 	        if (value != null)
	            checkBooleanValue(brokerCmdProps, 
				PROP_NAME_LOCAL_DELIVERY_PREF, value);
	    }

	    /*
	     * Check for unlimited values.
	     * If an unlimited value of '0' was specified:
	     *  - print warning
	     *  - convert '0' value to '-1'
	     */
	    checkUnlimitedValues(brokerCmdProps, DEST_ATTRS_UNLIMITED_CONV);
	} else if (cmdArg.equals(CMDARG_BROKER))  {
	    int		exceptionType = -1;
	    String	destType;
	    String	validAttrs[] = null;

	    /*
	     * Make sure some attrs were specified via -o.
	     */
            checkTargetAttrs(brokerCmdProps);

	    /*
	     * Check for deprecated attribute names (-o)
	     */
	    checkDeprecatedAttrs(brokerCmdProps, UPDATE_BKR_DEPRECATED_ATTRS);

	    validAttrs = UPDATE_BKR_VALID_ATTRS;
	    exceptionType = BrokerCmdException.BAD_ATTR_SPEC_UPDATE_BKR;

	    /*
	     * Check attribute names passed in via -o.
	     */
	    checkValidAttrs(brokerCmdProps, validAttrs, exceptionType);

	    /*
	     * Check attribute values - they are:
	     *	Primary Port (int)
	     *	Auto Create Topics (true/false)
	     *	Auto Create Queues (true/false)
	     *  Queue Delivery Policy (s, f, r)
	     *	Log Level (NONE, ERROR, WARNING, INFO, 
	     *			DEBUG, DEBUGMED, DEBUGHIGH)
	     *	Log Rollover Size (kilobytes) (int)
	     *	Log Rollover Interval (seconds) (int)
	     *	Metric Interval (seconds) (int)
	     *	Max Number of Messages in Memory (int)
	     *	Max Total Size of Messages in Memory (kilobytes) (int)
	     *	Max Number of Messages in Memory and Disk (int)
	     *	Max Total Size of Messages in Memory and Disk (kilobytes) (int)
	     *	Max Message Size (kilobytes) (int)
	     *	Log Dead Messages (true/false)
	     */
            Properties attrs = brokerCmdProps.getTargetAttrs();
            String maxValue, autoCreateValue, deliveryPolicyValue, logLevelValue,
			logDeadMsgs, truncateBody, useDMQ;

            maxValue = attrs.getProperty(PROP_NAME_BKR_PRIMARY_PORT);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_BKR_PRIMARY_PORT, maxValue);

            autoCreateValue = attrs.getProperty(PROP_NAME_BKR_AUTOCREATE_TOPIC);
 	    if (autoCreateValue != null)
	        checkBooleanValue
		    (brokerCmdProps, PROP_NAME_BKR_AUTOCREATE_TOPIC, autoCreateValue);

            autoCreateValue = attrs.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE);
 	    if (autoCreateValue != null)
	        checkBooleanValue
		    (brokerCmdProps, PROP_NAME_BKR_AUTOCREATE_QUEUE, autoCreateValue);

            deliveryPolicyValue = 
		attrs.getProperty(PROP_NAME_BKR_QUEUE_DELIVERY_POLICY);
            if (deliveryPolicyValue != null)
    		checkFlavorType(brokerCmdProps, deliveryPolicyValue);

            logLevelValue = attrs.getProperty(PROP_NAME_BKR_LOG_LEVEL);
 	    if (logLevelValue != null)
	        checkLogLevelValue
		    (brokerCmdProps, PROP_NAME_BKR_LOG_LEVEL, logLevelValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_LOG_ROLL_SIZE);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_BKR_LOG_ROLL_SIZE, maxValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_LOG_ROLL_INTERVAL);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_BKR_LOG_ROLL_INTERVAL, maxValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, 
			PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS, maxValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, 
			PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS, maxValue);

	    /*
            maxValue = attrs.getProperty(PROP_NAME_BKR_METRIC_INTERVAL);
 	    if (maxValue != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_BKR_METRIC_INTERVAL, maxValue);
	    */

            maxValue = attrs.getProperty(PROP_NAME_BKR_MAX_MSG);
 	    if (maxValue != null)
	        checkLongValue
		    (brokerCmdProps, PROP_NAME_BKR_MAX_MSG, maxValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_MAX_TTL_MSG_BYTES);
 	    if (maxValue != null)
	        checkByteValue
		    (brokerCmdProps, PROP_NAME_BKR_MAX_TTL_MSG_BYTES, maxValue);

            maxValue = attrs.getProperty(PROP_NAME_BKR_MAX_MSG_BYTES);
 	    if (maxValue != null)
	        checkByteValue
		    (brokerCmdProps, PROP_NAME_BKR_MAX_MSG_BYTES, maxValue);

            logDeadMsgs = attrs.getProperty(PROP_NAME_BKR_LOG_DEAD_MSGS);
 	    if (logDeadMsgs != null)
	        checkBooleanValue
		    (brokerCmdProps, PROP_NAME_BKR_LOG_DEAD_MSGS, logDeadMsgs);

            truncateBody = attrs.getProperty(PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY);
 	    if (truncateBody != null)
	        checkBooleanValue
		    (brokerCmdProps, PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY, truncateBody);
            
            useDMQ = attrs.getProperty(PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ);
 	    if (useDMQ != null)
	        checkBooleanValue
		    (brokerCmdProps, PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ, useDMQ);

	    /*
	     * Check for unlimited values.
	     * If an unlimited value of '0' was specified:
	     *  - print warning
	     *  - convert '0' value to '-1'
	     */
	    checkUnlimitedValues(brokerCmdProps, BKR_ATTRS_UNLIMITED_CONV);
	} else if (cmdArg.equals(CMDARG_SERVICE))  {
	    int		exceptionType = -1;
	    String	validAttrs[] = null;

	    /*
	     * Check target name (-n).
	     */
            checkTargetName(brokerCmdProps);

	    /*
	     * Make sure some attrs were specified via -o.
	     */
            checkTargetAttrs(brokerCmdProps);

	    validAttrs = UPDATE_SVC_VALID_ATTRS;
	    exceptionType = BrokerCmdException.BAD_ATTR_SPEC_UPDATE_SVC;

	    /*
	     * Check attribute names passed in via -o.
	     */
	    checkValidAttrs(brokerCmdProps, validAttrs, exceptionType);

	    /*
	     * Check attribute values - they are:
	     *	Port (int)
	     *	Min Number of Threads (int)
	     *	Max Number of Threads (int)
	     */
            Properties attrs = brokerCmdProps.getTargetAttrs();
            String value;

            value = attrs.getProperty(PROP_NAME_SVC_PORT);
 	    if (value != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_SVC_PORT, value);

            value = attrs.getProperty(PROP_NAME_SVC_MIN_THREADS);
 	    if (value != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_SVC_MIN_THREADS, value);

            value = attrs.getProperty(PROP_NAME_SVC_MAX_THREADS);
 	    if (value != null)
	        checkIntegerValue
		    (brokerCmdProps, PROP_NAME_SVC_MAX_THREADS, value);
	}

    }

    private static void checkQuery(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_QUERY_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_DESTINATION))  {
            checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);

	} else if (cmdArg.equals(CMDARG_SERVICE))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_TRANSACTION))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_CONNECTION))  {
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_MSG))  {
	    checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
	}
    }

    private static void checkMetrics(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_METRICS_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg(),
		samples;

	checkMetricInterval(brokerCmdProps);

        checkMetricSamples(brokerCmdProps);

	if (cmdArg.equals(CMDARG_BROKER))  {
	    checkMetricType(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_SERVICE))  {
	    checkMetricType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
	} else if (cmdArg.equals(CMDARG_DESTINATION))  {
	    checkMetricDstType(brokerCmdProps);
            checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
        }
    }

    private static void checkReload(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_RELOAD_VALID_CMDARGS);
    }

    private static void checkChangeMaster(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_CHANGEMASTER_VALID_CMDARGS);
    int     exceptionType = -1;
    String  destType;
    String  validAttrs[] = null;

    checkTargetAttrs(brokerCmdProps);
    validAttrs = CHANGEMASTER_VALID_ATTRS;
    exceptionType = BrokerCmdException.BAD_ATTR_SPEC_CHANGEMASTER;
    checkValidAttrs(brokerCmdProps, validAttrs, exceptionType);

    }

    private static void checkCommit(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	checkCmdArg(brokerCmdProps, CMD_COMMIT_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_TRANSACTION))  {
            checkTargetName(brokerCmdProps);
	}
    }

    private static void checkRollback(BrokerCmdProperties brokerCmdProps) 
        throws BrokerCmdException, UnrecognizedOptionException {

	checkCmdArg(brokerCmdProps, CMD_ROLLBACK_VALID_CMDARGS);

	String	cmdArg = brokerCmdProps.getCommandArg();

	if (cmdArg.equals(CMDARG_TRANSACTION))  {
            checkTargetName(brokerCmdProps);
        }
        if (brokerCmdProps.msgOptionSet() && 
            !brokerCmdProps.noCheckModeSet()) {
            UnrecognizedOptionException e = new UnrecognizedOptionException();
            e.setOption(OPTION_MSG);
            throw e;
        }
    }

    private static void checkExists(BrokerCmdProperties brokerCmdProps)
                        throws BrokerCmdException  {
        checkCmdArg(brokerCmdProps, CMD_EXISTS_VALID_CMDARGS);

	String cmdArg = brokerCmdProps.getCommandArg();

        if (cmdArg.equals(CMDARG_DESTINATION))  {
            checkDestType(brokerCmdProps);
            checkTargetName(brokerCmdProps);
	}
    }

    private static void checkGetAttr(BrokerCmdProperties brokerCmdProps)
                        throws BrokerCmdException  {
        checkCmdArg(brokerCmdProps, 
            CommonCmdRunnerUtil.toStringArray(CMD_GETATTR_VALID_CMDARGS.values()));

        String cmdArg = brokerCmdProps.getCommandArg();

        if (cmdArg.equals(CMDARG_DESTINATION))  {
            String      destType;
            String      validAttrs[] = null;

            /*
             * Check destination type (-t)
             */
            checkDestType(brokerCmdProps);

            /*
             * Check target name (-n)
             */
            checkTargetName(brokerCmdProps);

            destType = brokerCmdProps.getDestType();
            if (destType.equals(PROP_VALUE_DEST_TYPE_TOPIC))  {
                validAttrs = GETATTR_DST_TOPIC_VALID_ATTRS;
            } else if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE)) {
                validAttrs = GETATTR_DST_QUEUE_VALID_ATTRS;
            }
            /*
             * Check for valid attribute name (-attr)
	     * This is a mandatory attribute and must be specified.
             */
            checkValidSingleAttr(brokerCmdProps, validAttrs);

        } else if (cmdArg.equals(CMDARG_SERVICE))  {
            /*
             * Check target name (-n).
             */
            checkTargetName(brokerCmdProps);

            /*
             * Check for valid attribute name (-attr)
	     * This is a mandatory attribute and must be specified.
             */
            checkValidSingleAttr(brokerCmdProps, GETATTR_SVC_VALID_ATTRS);

        } else if (cmdArg.equals(CMDARG_BROKER))  {
            /*
             * Check for valid attribute name (-attr)
	     * This is a mandatory attribute and must be specified.
             */
            checkValidSingleAttr(brokerCmdProps, null);
        } else if (cmdArg.equals(CMDARG_TRANSACTION))  {
            checkValidSingleAttr(brokerCmdProps, GETATTR_TXN_VALID_ATTRS);
	}
    }

    private static void checkUngracefulKill(BrokerCmdProperties brokerCmdProps)
                        throws BrokerCmdException  {
        checkCmdArg(brokerCmdProps, CMD_UNGRACEFUL_KILL_VALID_CMDARGS);
    }

    /*
    private static void checkDestName(BrokerCmdProperties brokerCmdProps)
			throws BrokerCmdException  {
	BrokerCmdException ex;
	String destName = brokerCmdProps.getDestName();

	if (destName == null)  {
	    ex = new BrokerCmdException(BrokerCmdException.DEST_NAME_NOT_SPEC);
	    ex.setProperties(brokerCmdProps);

	    throw(ex);
	}
    }
    */

    private static void checkCmdArg(BrokerCmdProperties brokerCmdProps,
                                    String validCmdArgs[])
                                    throws BrokerCmdException  {
        BrokerCmdException ex;
        String cmdArg = brokerCmdProps.getCommandArg();

        if (cmdArg == null)  {
            ex = new BrokerCmdException(BrokerCmdException.CMDARG_NOT_SPEC);
            ex.setProperties(brokerCmdProps);

            throw(ex);
        }

        if (validCmdArgs == null)
            return;

        for (int i = 0; i < validCmdArgs.length; ++i)  {
            if (cmdArg.equals(validCmdArgs[i]))  {
                return;
            }
        }

        ex = new BrokerCmdException(BrokerCmdException.BAD_CMDARG_SPEC);
        ex.setProperties(brokerCmdProps);
        ex.setValidCmdArgs(validCmdArgs);
        throw(ex);
    }

    private static void checkDestType(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	BrokerCmdException ex = null;
	String destType = brokerCmdProps.getDestType();

	if (destType == null)  {
  	    ex = new BrokerCmdException(BrokerCmdException.DEST_TYPE_NOT_SPEC);
	    ex.setProperties(brokerCmdProps);

	    throw(ex);

	} else if ((!destType.equals(PROP_VALUE_DEST_TYPE_TOPIC)) && 
	           (!destType.equals(PROP_VALUE_DEST_TYPE_QUEUE))) {
	    ex = new BrokerCmdException(BrokerCmdException.INVALID_DEST_TYPE);
	    ex.setProperties(brokerCmdProps);

	    throw(ex);
	}
    }

    private static void checkTargetName(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	String targetName = brokerCmdProps.getTargetName();

	if (targetName == null)  {
	    BrokerCmdException ex = 
		new BrokerCmdException(BrokerCmdException.TARGET_NAME_NOT_SPEC);
	    ex.setProperties(brokerCmdProps);

	    throw(ex);
	}
    }

    private static void checkTargetAttrs(BrokerCmdProperties brokerCmdProps) 
			throws BrokerCmdException  {
	Properties attrs = brokerCmdProps.getTargetAttrs();

	if ((attrs == null) || (attrs.isEmpty()))  {
	    BrokerCmdException ex = 
		new BrokerCmdException(BrokerCmdException.TARGET_ATTRS_NOT_SPEC);
	    ex.setProperties(brokerCmdProps);

	    throw(ex);
	}
    }

   /*
    private static void checkClientID(BrokerCmdProperties brokerCmdProps)
                        throws BrokerCmdException  {
        BrokerCmdException ex;
        String clientID = brokerCmdProps.getClientID();

        if (clientID == null)  {
            ex = new BrokerCmdException(BrokerCmdException.CLIENT_ID_NOT_SPEC);
            ex.setProperties(brokerCmdProps);

            throw(ex);
        }
    }
    */

    private static void checkMsgID(BrokerCmdProperties brokerCmdProps)
                        throws BrokerCmdException  {
        BrokerCmdException ex;
        String msgID = brokerCmdProps.getMsgID();

        if (msgID == null)  {
            ex = new BrokerCmdException(BrokerCmdException.MSG_ID_NOT_SPEC);
            ex.setProperties(brokerCmdProps);

            throw(ex);
        }
    }

    private static void checkWarnPassword(BrokerCmdProperties brokerCmdProps)  {
        String passwd = brokerCmdProps.getAdminPasswd();

        if (passwd != null)  {
	    Globals.stdErrPrintln(
                ar.getString(ar.I_WARNING_MESG), 
		ar.getKString(ar.W_PASSWD_OPTION_DEPRECATED));
	    Globals.stdErrPrintln("");
        }
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

    /*
     * Error handling methods
     */
    private static void handleCheckOptionsExceptions(BrokerCmdException e) {
	BrokerCmdProperties	brokerCmdProps = (BrokerCmdProperties)e.getProperties();
	String			cmd = brokerCmdProps.getCommand(),
				cmdArg = brokerCmdProps.getCommandArg(),
				badAttr = e.getBadAttr(),
				badValue = e.getBadValue(),
				errorString = e.getErrorString(),
	                        validAttrs[] = e.getValidAttrs();
        //String                  validCmdArgs[] = e.getValidCmdArgs();
	int			type = e.getType();
        String			errorValue;
        Properties		attrs;

	/*
	 * REVISIT: should check brokerCmdProps != null
	 */

	switch (type)  {
	case BrokerCmdException.TARGET_NAME_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_TARGET_NAME_NOT_SPEC, OPTION_TARGET_NAME));
	break;

	case BrokerCmdException.DEST_NAME_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_DEST_NAME_NOT_SPEC, OPTION_DEST_NAME));
	break;

	case BrokerCmdException.TARGET_ATTRS_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_TARGET_ATTRS_NOT_SPEC, OPTION_TARGET_ATTRS));
	break;

	case BrokerCmdException.DEST_TYPE_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_DEST_TYPE_NOT_SPEC, OPTION_DEST_TYPE));
	break;

	case BrokerCmdException.FLAVOUR_TYPE_INVALID:
            //attrs = brokerCmdProps.getTargetAttrs();

	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_FLAVOUR_TYPE_INVALID, badValue));
	break;

	case BrokerCmdException.DST_QDP_VALUE_INVALID:
            //attrs = brokerCmdProps.getTargetAttrs();

	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_FLAVOUR_TYPE_INVALID, badValue));
	    Globals.stdErrPrintln(
                ar.getString(ar.I_WARNING_MESG), 
		ar.getKString(ar.W_DST_QDP_DEPRECATED));
	break;

	case BrokerCmdException.BKR_QDP_VALUE_INVALID:
            //attrs = brokerCmdProps.getTargetAttrs();

	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_FLAVOUR_TYPE_INVALID, badValue));
	    Globals.stdErrPrintln(
                ar.getString(ar.I_WARNING_MESG), 
		ar.getKString(ar.W_BKR_QDP_DEPRECATED));
	break;

	case BrokerCmdException.INVALID_INTEGER_VALUE:
            attrs = brokerCmdProps.getTargetAttrs();
            errorValue = attrs.getProperty(errorString);
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_INTEGER_VALUE, errorValue, errorString));
	break;

	case BrokerCmdException.INVALID_BYTE_VALUE:
            attrs = brokerCmdProps.getTargetAttrs();
            errorValue = attrs.getProperty(errorString);
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_BYTE_VALUE, errorValue, errorString));
	break;

	case BrokerCmdException.INVALID_BOOLEAN_VALUE:
            attrs = brokerCmdProps.getTargetAttrs();
            errorValue = attrs.getProperty(errorString);
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_BOOLEAN_VALUE, errorValue, errorString));
	break;

	case BrokerCmdException.INVALID_LOG_LEVEL_VALUE:
            attrs = brokerCmdProps.getTargetAttrs();
            errorValue = attrs.getProperty(errorString);
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_LOG_LEVEL_VALUE, errorValue, errorString));
	    Globals.stdErrPrintln(ar.getString(ar.I_BROKERCMD_VALID_VALUES,
				    PROP_NAME_BKR_LOG_LEVEL));
	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < BKR_LOG_LEVEL_VALID_VALUES.size(); ++i)  {
	        Globals.stdErrPrint(BKR_LOG_LEVEL_VALID_VALUES.get(i));
                if ((i+1) < BKR_LOG_LEVEL_VALID_VALUES.size())  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.INVALID_LIMIT_BEHAV_VALUE:
            attrs = brokerCmdProps.getTargetAttrs();
            errorValue = attrs.getProperty(errorString);
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_ATTR_VALUE, 
			errorValue, errorString));
	    Globals.stdErrPrintln(ar.getString(ar.I_BROKERCMD_VALID_VALUES,
				    PROP_NAME_LIMIT_BEHAVIOUR));
	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < BKR_LIMIT_BEHAV_VALID_VALUES.size(); ++i)  {
	        Globals.stdErrPrint(BKR_LIMIT_BEHAV_VALID_VALUES.get(i));
                if ((i+1) < BKR_LIMIT_BEHAV_VALID_VALUES.size())  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.INVALID_METRIC_INTERVAL:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_METRIC_INTERVAL, badValue));
	break;

	case BrokerCmdException.INVALID_METRIC_SAMPLES:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_METRIC_SAMPLES, badValue));
	break;

	case BrokerCmdException.INVALID_METRIC_TYPE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_METRIC_TYPE, badValue));
	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < METRIC_TYPE_VALID_VALUES.length; ++i)  {
	        Globals.stdErrPrint(METRIC_TYPE_VALID_VALUES[i]);
                if ((i+1) < METRIC_TYPE_VALID_VALUES.length)  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.INVALID_METRIC_DST_TYPE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_METRIC_DST_TYPE, badValue));
	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < METRIC_DST_TYPE_VALID_VALUES.length; ++i)  {
	        Globals.stdErrPrint(METRIC_DST_TYPE_VALID_VALUES[i]);
                if ((i+1) < METRIC_DST_TYPE_VALID_VALUES.length)  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.INVALID_DEST_TYPE:
	    String invalidDestType = brokerCmdProps.getDestType();
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_DEST_TYPE, invalidDestType));
	break;

	case BrokerCmdException.INVALID_PAUSE_TYPE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_PAUSETYPE_VALUE, badValue));

	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < PAUSE_DST_TYPE_VALID_VALUES.length; ++i)  {
	        Globals.stdErrPrint(PAUSE_DST_TYPE_VALID_VALUES[i]);
                if ((i+1) < PAUSE_DST_TYPE_VALID_VALUES.length)  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.INVALID_RESET_TYPE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_RESETTYPE_VALUE, badValue));

	    Globals.stdErrPrint("\t");
	    for (int i = 0; i < RESET_BKR_TYPE_VALID_VALUES.length; ++i)  {
	        Globals.stdErrPrint(RESET_BKR_TYPE_VALID_VALUES[i]);
                if ((i+1) < RESET_BKR_TYPE_VALID_VALUES.length)  {
                    Globals.stdErrPrint(" ");
                }
	    } 
            Globals.stdErrPrintln("");
	break;

	case BrokerCmdException.CLIENT_ID_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_CLIENT_ID_NOT_SPEC, OPTION_CLIENT_ID));
	break;

	case BrokerCmdException.MSG_ID_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		"The message ID must be specified with the " + OPTION_MSG_ID + "option.");
	break;

	case BrokerCmdException.SINGLE_TARGET_ATTR_NOT_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_SINGLE_TARGET_ATTR_NOT_SPEC, 
		    OPTION_SINGLE_TARGET_ATTR));
	break;

	case BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_QUEUE:
	case BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_TOPIC:
	case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_BKR:
	case BrokerCmdException.BAD_ATTR_SPEC_CHANGEMASTER:
	case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_QUEUE:
	case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_TOPIC:
	case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_SVC:
	case BrokerCmdException.BAD_ATTR_SPEC_GETATTR:
	case BrokerCmdException.BAD_ATTR_SPEC_PAUSE_DST:
	case BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_QUEUE:
	case BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_TOPIC:

	    String	msg1ID, msg2ID;

	    msg1ID = ar.E_BAD_ATTR_SPEC;

	    switch (type)  {
	    case BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_QUEUE:
		msg2ID = ar.E_BAD_ATTR_SPEC_CREATE_QUEUE;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_CREATE_DST_TOPIC:
		msg2ID = ar.E_BAD_ATTR_SPEC_CREATE_TOPIC;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_BKR:
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_BKR;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_QUEUE:
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_QUEUE;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_DST_TOPIC:
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_TOPIC;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_UPDATE_SVC:
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_SVC;
	    break;

	    case BrokerCmdException.BAD_ATTR_SPEC_PAUSE_DST:
		msg2ID = ar.E_BAD_ATTR_SPEC_PAUSE_DST;
	    break;

	    case BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_QUEUE:
	        msg1ID = ar.E_UPDATE_ATTR_SPEC_CREATE_ONLY;
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_QUEUE;
	    break;

	    case BrokerCmdException.UPDATE_DST_ATTR_SPEC_CREATE_ONLY_TOPIC:
	        msg1ID = ar.E_UPDATE_ATTR_SPEC_CREATE_ONLY;
		msg2ID = ar.E_BAD_ATTR_SPEC_UPDATE_TOPIC;
	    break;

	    default:
		msg2ID = ar.E_BAD_ATTR_SPEC2;
	    break;
	    }

	    /*
	     * The following prints this error message:
	     *
	     *	% imqcmd create dst -t t -n t1 -o "foo=bar"
	     *	Error [A3129]: Invalid attribute specified: foo
	     *	The valid attributes for this operation are:
	     *		
	     *		queueDeliveryPolicy 
	     *		maxTotalMsgBytes
	     *		maxBytesPerMsg      
	     *		maxNumMsgs
	     */

	    /*
	     * Prints:
	     *	Error [A3129]: Invalid attribute specified: foo
	     */
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(msg1ID, badAttr));

	    if (validAttrs != null)  {
		/*
		 * Prints:
	         *  The valid attributes for this operation are:
		 *
		 * This string will vary depending on the exception type.
		 *
		 * For example, for creation of queues, it will be:
		 *  The valid attributes for creating a queue are:
		 */
	        Globals.stdErrPrintln(
		    ar.getString(msg2ID));
	    
		/*
		 * Prints the valid attribute list:
	         *	queueDeliveryPolicy 
	         *	maxTotalMsgBytes
	         *	maxBytesPerMsg      
	         *	maxNumMsgs
		 */
		BrokerCmdPrinter bcp = new BrokerCmdPrinter(1, 4, null);
		String[] row = new String[1];
	        for (int i = 0; i < validAttrs.length; ++i)  {
		    /*
	            Globals.stdErrPrintln("\t" + validAttrs[i]);
		    */
		    row[0] = validAttrs[i];
		    bcp.add(row);
	        }
		bcp.println();
	    }
	break;

	default:
    CommonCmdRunnerUtil.handleCommonCheckOptionsExceptions(e, cmd, cmdArg, new BrokerCmdHelpPrinter());
	break;
	}
    }

}
