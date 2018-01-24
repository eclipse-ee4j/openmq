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

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Hashtable;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.apps.broker.CommonCmdRunnerUtil;
import com.sun.messaging.jmq.admin.apps.broker.CommonCmdException;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.bridge.api.BridgeCmdSharedReplyData;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.bridge.admin.bridgemgr.resources.BridgeAdminResources;



/** 
 * This class contains the logic to execute the user commands
 * specified in the BridgeMgrProperties object. It has one
 * public entry point which is the runCommands() method. It
 * is expected to display to the user if the command execution
 * was successful or not.
 * @see  ObjMgr
 *
 */
public class CmdRunner implements BridgeMgrOptions, AdminEventListener {
    private BridgeAdminResources ar = Globals.getBridgeAdminResources();
    private BridgeMgrProperties bridgeMgrProps;

    /**
     * Constructor
     */
    public CmdRunner(BridgeMgrProperties props) {
	this.bridgeMgrProps = props;
    } 

    /*
     * Run/execute the user commands specified in the BridgeMgrProperties object.
     */
    public int runCommand() {
	int exitcode = 0;

	/*
	 * Determine type of command and invoke the relevant run method
	 * to execute the command.
	 *
	 */
	String cmd = bridgeMgrProps.getCommand();
	if (cmd.equals(Cmd.LIST))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.PAUSE))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.RESUME))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.START))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (cmd.equals(Cmd.STOP))  {
            exitcode = runCommand(bridgeMgrProps);
	} else if (bridgeMgrProps.debugModeSet() && cmd.equals(Cmd.DEBUG))  {
            exitcode = runCommand(bridgeMgrProps);
	}
	return (exitcode);
    }

    private int runCommand(BridgeMgrProperties bridgeMgrProps) {
        BridgeAdmin 	broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        broker = init();

        boolean force = bridgeMgrProps.forceModeSet();

	// Check for the target argument
	String cmd = bridgeMgrProps.getCommand();
	String commandArg = bridgeMgrProps.getCommandArg();
    String bn = bridgeMgrProps.getBridgeName();
    String bt = bridgeMgrProps.getBridgeType();
    String ln = bridgeMgrProps.getLinkName();
	boolean debugMode = bridgeMgrProps.debugModeSet();

    if (debugMode && cmd.equals(Cmd.DEBUG)) {
        if (broker == null)  {
            Globals.stdErrPrintln("Problem connecting to the broker");
            return (1);
        }
        if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);
        String target = bridgeMgrProps.getTargetName();
        Properties optionalProps = bridgeMgrProps.getTargetAttrs();

        Globals.stdOutPrintln("Sending the following DEBUG message:"); 
        if (target != null) {
            BridgeMgrPrinter bmp = new BridgeMgrPrinter(2, 4, null, BridgeMgrPrinter.LEFT, false);
            String[] row = new String[2];
            row[0] = commandArg;
            row[1] = target;
            bmp.add(row);
            bmp.println();
        } else {
            BridgeMgrPrinter bmp = new BridgeMgrPrinter(1, 4,  null, BridgeMgrPrinter.LEFT, false);
            String[] row = new String[1];
            row[0] = commandArg;
            bmp.add(row);
            bmp.println();
        }
        if ((optionalProps != null) && (optionalProps.size() > 0))  {
            Globals.stdOutPrintln("Optional properties:");
            CommonCmdRunnerUtil.printAttrs(optionalProps, true, new BridgeMgrPrinter());
        }

        Globals.stdOutPrintln("To the broker specified by:");
        printBrokerInfo(broker);
        try {
             connectToBroker(broker);
             broker.sendDebugMessage(commandArg, target, optionalProps);
             Hashtable debugHash = broker.receiveDebugReplyMessage();
             if ((debugHash != null) && (debugHash.size() > 0))  {
                 Globals.stdOutPrintln("Data received back from broker:");
                 CommonCmdRunnerUtil.printDebugHash(debugHash);
             } else  {
                 Globals.stdOutPrintln("No additional data received back from broker.\n");
            }
            Globals.stdOutPrintln("DEBUG message sent successfully.");
        } catch (BrokerAdminException bae)  {
             handleBrokerAdminException(bae);
             return (1);
        }
	} else if (CmdArg.BRIDGE.equals(commandArg)) {

        if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmd(cmd)));
            return (1);
        }

        if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);

        boolean single = false;
        boolean startRet = true;

	    if ((bn == null) || (bn.trim().equals("")))  {
	        if ((bt == null) || (bt.trim().equals("")))  {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_ALL_BRIDGES_CMD_ON_BKR, getLocalizedCmd(cmd)));
		    } else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_ALL_TYPE_BRIDGES_CMD, getLocalizedCmd(cmd)));
                    printBridgeInfo(false);

                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
		    }

	    } else  {
            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD, getLocalizedCmd(cmd) ));
            printBridgeInfo();
            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
            single = true;
	    }

        printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                if (single) {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmd(cmd)));
                } else {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_FAIL, getLocalizedCmd(cmd)));
                }
                return (1);
            }

            if (cmd.equals(Cmd.LIST)) {
                force = true;
            }
            if (!force) {
                input = getUserInput(ar.getString(ar.Q_BRIDGE_CMD_OK, getLocalizedCmd(cmd)), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                if (cmd.equals(Cmd.LIST)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.LIST, "LIST", 
                                              BridgeMgrStatusEvent.Type.LIST, 
                                              AdminMessageType.Type.LIST_REPLY, "LIST_REPLY", debugMode);
		            ArrayList<BridgeCmdSharedReplyData> data = broker.receiveListReplyMessage();
                    Iterator<BridgeCmdSharedReplyData> itr = data.iterator();
                    BridgeMgrPrinter bcp = null;
                    BridgeCmdSharedReplyData reply = null;
                    while (itr.hasNext()) {
                        reply = itr.next();
                        bcp = new BridgeMgrPrinter();
                        bcp.copy(reply);
                        bcp.println();
                        Globals.stdOutPrintln("");
                    }
                } else if (cmd.equals(Cmd.START)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.START, "START", 
                                              BridgeMgrStatusEvent.Type.START, 
                                              AdminMessageType.Type.START_REPLY, "START_REPLY");
		            startRet = broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.START_REPLY, "START_REPLY");
                } else if (cmd.equals(Cmd.STOP)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.STOP, "STOP", 
                                              BridgeMgrStatusEvent.Type.STOP, 
                                              AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
                } else if (cmd.equals(Cmd.RESUME)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.RESUME, "RESUME", 
                                              BridgeMgrStatusEvent.Type.RESUME, 
                                              AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
                } else if (cmd.equals(Cmd.PAUSE)) {
                    broker.sendCommandMessage(cmd, bn, bt, null,
                                              AdminMessageType.Type.PAUSE, "PAUSE", 
                                              BridgeMgrStatusEvent.Type.PAUSE, 
                                              AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
                } else {
                    return 1;
                }

                if (single) {
                    if (cmd.equals(Cmd.START) && !startRet) {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_ASYNC_STARTED));
                    } else {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_SUC, getLocalizedCmd(cmd)));
                    }
                } else {
                    if (cmd.equals(Cmd.START) && !startRet) {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_ASYNC_STARTED));
                    } else {
                        Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_SUC, getLocalizedCmd(cmd)));
                    }
                }

                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);
                    if (single) {
                    Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_FAIL, getLocalizedCmd(cmd)));
                    } else {
                    Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_FAIL, getLocalizedCmd(cmd)));
                    }
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                if (single) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_NOOP, getLocalizedCmd(cmd)));
                } else {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_NOOP, getLocalizedCmd(cmd)));
                }
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                if (single) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGE_CMD_NOOP, getLocalizedCmd(cmd)));
                } else {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_BRIDGES_CMD_NOOP, getLocalizedCmd(cmd)));
                }
                return (1);
            }

	} else if (CmdArg.LINK.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmd(cmd)));
                return (1);
            }

            if (!force) broker = (BridgeAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD, getLocalizedCmd(cmd)));
            printLinkInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmd(cmd)));
                return (1);
            }

            if (cmd.equals(Cmd.LIST)) {
                force = true;
            }
            if (!force) {
                input = getUserInput(ar.getString(ar.Q_LINK_CMD_OK, getLocalizedCmd(cmd)), noShort);
                Globals.stdOutPrintln("");
            }

            boolean startRet = true;

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                if (cmd.equals(Cmd.LIST)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.LIST, "LIST", 
                                              BridgeMgrStatusEvent.Type.LIST, 
                                              AdminMessageType.Type.LIST_REPLY, "LIST_REPLY", debugMode);
		            ArrayList<BridgeCmdSharedReplyData> data = broker.receiveListReplyMessage();
                    Iterator<BridgeCmdSharedReplyData> itr = data.iterator();
                    BridgeMgrPrinter bcp = null;
                    BridgeCmdSharedReplyData reply = null;
                    while (itr.hasNext()) {
                        reply = itr.next();
                        bcp = new BridgeMgrPrinter();
                        bcp.copy(reply);
                        bcp.println();
                    }
                } else if (cmd.equals(Cmd.START)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.START, "START", 
                                              BridgeMgrStatusEvent.Type.START, 
                                              AdminMessageType.Type.START_REPLY, "START_REPLY");
		            startRet = broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.START_REPLY, "START_REPLY");
                } else if (cmd.equals(Cmd.STOP)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.STOP, "STOP", 
                                              BridgeMgrStatusEvent.Type.STOP, 
                                              AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.STOP_REPLY, "STOP_REPLY");
                } else if (cmd.equals(Cmd.RESUME)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.RESUME, "RESUME", 
                                              BridgeMgrStatusEvent.Type.RESUME, 
                                              AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.RESUME_REPLY, "RESUME_REPLY");
                } else if (cmd.equals(Cmd.PAUSE)) {
                    broker.sendCommandMessage(cmd, bn, bt, ln,
                                              AdminMessageType.Type.PAUSE, "PAUSE", 
                                              BridgeMgrStatusEvent.Type.PAUSE, 
                                              AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
		            broker.receiveCommandReplyMessage(cmd, AdminMessageType.Type.PAUSE_REPLY, "PAUSE_REPLY");
                } else {
                    return 1;
                }

                if (cmd.equals(Cmd.START) && !startRet) {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_ASYNC_STARTED));
                } else {
                    Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_SUC, getLocalizedCmd(cmd)));
                }

                } catch (BrokerAdminException bae)  {
		        handleBrokerAdminException(bae);
                Globals.stdErrPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_FAIL, getLocalizedCmd(cmd)));
                return (1);
                }
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_NOOP, getLocalizedCmd(cmd)));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_BGMGR_LINK_CMD_NOOP, getLocalizedCmd(cmd)));
                return (1);
            }
	    }

        broker.close();

        return (0);
    }

    private BridgeAdmin init() {
	BridgeAdmin	broker;

	String  brokerHostPort = bridgeMgrProps.getBrokerHostPort(),
			adminUser = bridgeMgrProps.getAdminUserId(),
			adminPasswd;
	int		brokerPort = -1,
			numRetries = bridgeMgrProps.getNumRetries(),
			receiveTimeout = bridgeMgrProps.getReceiveTimeout();
	boolean		useSSL = bridgeMgrProps.useSSLTransportSet();

	if (bridgeMgrProps.adminDebugModeSet())  {
	    BridgeAdmin.setDebug(true);
	}

	try  {
	    adminPasswd = getPasswordFromFileOrCmdLine(bridgeMgrProps);

	    broker = new BridgeAdmin(brokerHostPort,
					adminUser, adminPasswd, 
					(receiveTimeout * 1000), useSSL);

	    if (useSSL)  {
		broker.setSSLTransportUsed(true);
	    }
	    if (numRetries > 0)  {
		/*
		 * If the number of retries was specified, set it on the
		 * BridgeAdmin object.
		 */
		broker.setNumRetries(numRetries);
	    }
	} catch (BridgeMgrException bce)  {
	    handleBridgeMgrException(bce);

	    return (null);
	} catch (CommonCmdException cce)  {
	    handleBridgeMgrException(cce);

	    return (null);
	} catch (BrokerAdminException bae)  {
	    handleBrokerAdminException(bae);

	    return (null);
	}

    broker.setCheckShutdownReply(false);
	broker.addAdminEventListener(this);

	return (broker);
    }

    private void connectToBroker(BridgeAdmin broker) throws BrokerAdminException {
        broker.connect();
        broker.sendHelloMessage();
        broker.receiveHelloReplyMessage();
    }

    /*
     * Prints out the appropriate error message using 
     * Globals.stdErrPrintln()
     */
    private void handleBrokerAdminException(BrokerAdminException bae)  {
        CommonCmdRunnerUtil.printBrokerAdminException(bae,
                                   Option.BROKER_HOSTPORT,
                                   bridgeMgrProps.debugModeSet());
    }

    private void handleBridgeMgrException(CommonCmdException bce)  {
        CommonCmdRunnerUtil.printCommonCmdException(bce);
    }

    /**
     * Return user input. Return <defaultResponse> if no response ("") was
     * given. Return null if an error occurred.
     */
    private String getUserInput(String question, String defaultResponse)  {
        return CommonCmdRunnerUtil.getUserInput(question, defaultResponse); 
    }    

    private void printBrokerInfo(BridgeAdmin broker) {
        CommonCmdRunnerUtil.printBrokerInfo(broker, new BridgeMgrPrinter());
    }

    private void printBridgeInfo() {
        printBridgeInfo(true);
    }

    private void printBridgeInfo(boolean printName) {
	BridgeMgrPrinter bcp = new BridgeMgrPrinter(1, 4, "-");
	String[] row = new String[1];
	String value, title;

	if (printName)  {
	    title = ar.getString(ar.I_BGMGR_BRIDGE_NAME);
	    value = bridgeMgrProps.getBridgeName();
	} else  {
	    title = ar.getString(ar.I_BGMGR_BRIDGE_TYPE);
	    value = bridgeMgrProps.getBridgeType();
	}

	row[0] = title;
	bcp.addTitle(row);

	row[0] = value;
	bcp.add(row);

	bcp.println();
    }

    private void printLinkInfo() {
	BridgeMgrPrinter bcp = new BridgeMgrPrinter(2, 4, "-");
	String[] row = new String[2];
	String ln = bridgeMgrProps.getLinkName(),
	        bn = bridgeMgrProps.getBridgeName();

	row[0] = ar.getString(ar.I_BGMGR_BRIDGE_NAME);
	row[1] = ar.getString(ar.I_BGMGR_LINK_NAME);
	bcp.addTitle(row);

	row[0] = bn;
	row[1] = ln;
	bcp.add(row);

	bcp.println();
    }

    /*
     * Get password from either the passfile or -p option.
     * In some future release, the -p option will go away
     * leaving the passfile the only way to specify the 
     * password (besides prompting the user for it).
     * -p has higher precendence compared to -passfile.
     */
    private String getPasswordFromFileOrCmdLine(BridgeMgrProperties bridgeMgrProps) 
		throws CommonCmdException  {
        String passwd = bridgeMgrProps.getAdminPasswd(),
	       passfile = bridgeMgrProps.getAdminPassfile();
	
	if (passwd != null)  {
	    return (passwd);
	}
	return CommonCmdRunnerUtil.getPasswordFromFile(passfile, PropName.PASSFILE_PASSWD, bridgeMgrProps);

    }


    public void adminEventDispatched(AdminEvent e)  {
    if (e instanceof BridgeMgrStatusEvent)  {
        BridgeMgrStatusEvent be = (BridgeMgrStatusEvent)e;
        int type = be.getType();

        if (type == BridgeMgrStatusEvent.BROKER_BUSY)  {
            CommonCmdRunnerUtil.printBrokerBusyEvent(be);
        }
    }
    }

    private String getLocalizedCmd(String cmd) {
	if (cmd.equals(Cmd.LIST))  {
           return ar.getString(ar.I_BGMGR_CMD_list);
	} else if (cmd.equals(Cmd.PAUSE))  {
           return ar.getString(ar.I_BGMGR_CMD_pause);
	} else if (cmd.equals(Cmd.RESUME))  {
           return ar.getString(ar.I_BGMGR_CMD_resume);
	} else if (cmd.equals(Cmd.START))  {
           return ar.getString(ar.I_BGMGR_CMD_start);
	} else if (cmd.equals(Cmd.STOP))  {
           return ar.getString(ar.I_BGMGR_CMD_stop);
	}
	return (cmd);
    }

}
