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
 * @(#)CmdRunner.java	1.165 07/12/07
 */ 

package com.sun.messaging.jmq.admin.apps.broker;

import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Date;
import java.text.DateFormat;
import javax.jms.DeliveryMode;
import javax.jms.Message;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.util.MetricCounters;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.Password;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.DestLimitBehavior;
import com.sun.messaging.jmq.util.ClusterDeliveryPolicy;
import com.sun.messaging.jmq.util.DebugPrinter;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.DurableInfo;
import com.sun.messaging.jmq.util.admin.ConsumerInfo;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.jmsclient.GenericPortMapperClient;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerCmdStatusEvent;
import com.sun.messaging.jms.management.server.BrokerClusterInfo;
import com.sun.messaging.jms.management.server.BrokerState;
import com.sun.messaging.jmq.util.FileUtil;


/** 
 * This class contains the logic to execute the user commands
 * specified in the BrokerCmdProperties object. It has one
 * public entry point which is the runCommands() method. It
 * is expected to display to the user if the command execution
 * was successful or not.
 * @see  ObjMgr
 *
 */
public class CmdRunner implements BrokerCmdOptions, BrokerConstants, AdminEventListener {
    /*
     * Int constants for metric types
     * Convenience - to avoid doing String.equals().
     */
    private static final int METRICS_TOTALS				= 0;
    private static final int METRICS_RATES				= 1;
    private static final int METRICS_CONNECTIONS			= 2;
    private static final int METRICS_CONSUMER				= 3;
    private static final int METRICS_DISK				= 4;
    private static final int METRICS_REMOVE				= 5;

    /*
     * List types
     */
    private static final int LIST_ALL					= 0;
    private static final int LIST_TOPIC					= 1;
    private static final int LIST_QUEUE					= 2;

    private int zeroNegOneInt[] = {0, -1};
    private long zeroNegOneLong[] = {0, -1};
    private String zeroNegOneString[] = {"0", "-1"};
    private String negOneString[] = {"-1"};

    private AdminResources ar = Globals.getAdminResources();
    private BrokerCmdProperties brokerCmdProps;
    //private BrokerAdmin admin;

    /**
     * Constructor
     */
    public CmdRunner(BrokerCmdProperties props) {
	this.brokerCmdProps = props;
    } 

    /*
     * Run/execute the user commands specified in the BrokerCmdProperties object.
     */
    public int runCommands() {
	int exitcode = 0;

	/*
	 * If -debug was used, run the debug mode handler
	 * and exit.
	 */
	if (brokerCmdProps.debugModeSet())  {
	    exitcode = runDebug(brokerCmdProps);
	    return (exitcode);
	}

	/*
	 * Determine type of command and invoke the relevant run method
	 * to execute the command.
	 *
	 */
	String cmd = brokerCmdProps.getCommand();
	if (cmd.equals(PROP_VALUE_CMD_LIST))  {
            exitcode = runList(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PAUSE))  {
            exitcode = runPause(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESUME))  {
            exitcode = runResume(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_SHUTDOWN))  {
            exitcode = runShutdown(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESTART))  {
            exitcode = runRestart(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CREATE))  {
            exitcode = runCreate(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DESTROY))  {
            exitcode = runDestroy(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PURGE))  {
            exitcode = runPurge(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UPDATE))  {
            exitcode = runUpdate(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_QUERY))  {
            exitcode = runQuery(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_METRICS))  {
            exitcode = runMetrics(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RELOAD))  {
            exitcode = runReload(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CHANGEMASTER))  {
            exitcode = runChangeMaster(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_COMMIT))  {
            exitcode = runCommit(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_ROLLBACK))  {
            exitcode = runRollback(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_COMPACT))  {
            exitcode = runCompact(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_QUIESCE))  {
            exitcode = runQuiesce(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_TAKEOVER))  {
            exitcode = runTakeover(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_MIGRATESTORE))  {
            exitcode = runMigrateStore(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UNQUIESCE))  {
            exitcode = runUnquiesce(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_RESET))  {
            exitcode = runReset(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_CHECKPOINT))  {
            exitcode = runCheckpoint(brokerCmdProps);            

        /*
         * Private subcommands - to support testing only
         */
	} else if (cmd.equals(PROP_VALUE_CMD_EXISTS))  {
            exitcode = runExists(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_GETATTR))  {
            exitcode = runGetAttr(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_UNGRACEFUL_KILL))  {
            exitcode = runUngracefulKill(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_PURGEALL))  {
            exitcode = runPurgeAll(brokerCmdProps);
	} else if (cmd.equals(PROP_VALUE_CMD_DESTROYALL))  {
            exitcode = runDestroyAll(brokerCmdProps);
	}
	return (exitcode);
    }

    /*
     * BEGIN INTERFACE AdminEventListener
     */
    public void adminEventDispatched(AdminEvent e)  {
	if (e instanceof BrokerCmdStatusEvent)  {
	    BrokerCmdStatusEvent be = (BrokerCmdStatusEvent)e;
	    int type = be.getType();

	    if (type == BrokerCmdStatusEvent.BROKER_BUSY)  {
            CommonCmdRunnerUtil.printBrokerBusyEvent(be);
	    }
	}
    }
    /*
     * END INTERFACE AdminEventListener
     */

    private int runList(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;

        broker = init();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg(),
		destTypeStr = brokerCmdProps.getDestType();
	int destTypeMask = getDestTypeMask(brokerCmdProps);
	boolean listAll = (destTypeStr == null);

	if (CMDARG_DESTINATION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_DST_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
	    if (!force)
	        broker = promptForAuthentication(broker);

	    if (listAll)  {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_DST));
	    } else if (DestType.isQueue(destTypeMask))  {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_QUEUE_DST));
	    } else if (DestType.isTopic(destTypeMask))  {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_TOPIC_DST));
	    }
	    printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetDestinationsMessage(null, -1,
                           brokerCmdProps.showPartitionModeSet(),
                           brokerCmdProps.loadDestinationSet());
		Vector dests = broker.receiveGetDestinationsReplyMessage();

		if (dests != null) {
		    if (listAll) {
                        listDests(brokerCmdProps, dests, LIST_ALL);
		    } else if (DestType.isTopic(destTypeMask))  {
                        listDests(brokerCmdProps, dests, LIST_TOPIC);
		    } else if (DestType.isQueue(destTypeMask))  {
                        listDests(brokerCmdProps, dests, LIST_QUEUE);
		    }
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_DST_SUC));
                } else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_DST_FAIL));
                    return (1);
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_DST_FAIL));
                return (1);
            }

	} else if (CMDARG_SERVICE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SVC_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_SVC));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetServicesMessage(null);
		Vector svcs = broker.receiveGetServicesReplyMessage();

		if (svcs != null) {
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(3, 4, "-");
		    String[] row = new String[3];
		    row[0] = ar.getString(ar.I_JMQCMD_SVC_NAME);
		    row[1] = ar.getString(ar.I_JMQCMD_SVC_PORT);
		    row[2] = ar.getString(ar.I_JMQCMD_SVC_STATE);
		    bcp.addTitle(row);
	
                    Enumeration thisEnum = svcs.elements();
                    while (thisEnum.hasMoreElements()) {
                        ServiceInfo sInfo = (ServiceInfo)thisEnum.nextElement();
                        row[0] = sInfo.name;

			// The port number is not applicable to this service
			if (sInfo.port == -1) {
			    row[1] = "-";

			// Add more information about the port number: 
			// dynamically generated or statically declared
			} else if (sInfo.dynamicPort) {

                            switch (sInfo.state) {
                                case ServiceState.UNKNOWN:
                                    row[1] = ar.getString(ar.I_DYNAMIC); 
                                break;
                                default:
                                    row[1] = Integer.toString(sInfo.port) +
				    	     " (" + ar.getString(ar.I_DYNAMIC) + ")";
                            }
                        } else {
                            row[1] = Integer.toString(sInfo.port) +
			    	     " (" + ar.getString(ar.I_STATIC) + ")";;
			}
                        // row[2] = ServiceState.getString(sInfo.state);
			             row[2] = BrokerAdminUtil.getServiceState(sInfo.state);
			bcp.add(row);
		    }

		    bcp.println();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_SVC_SUC));

                } else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SVC_FAIL));
                    return (1);
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SVC_FAIL));
                return (1);
            }

        } else if (CMDARG_DURABLE.equals(commandArg)) {

            boolean listDstName = false; // display dst if we list all

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getDestName();

            if (destName != null)
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB, destName));
            else
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_ALL_SUB));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_FAIL));
                return (1);
            }

            try  {
                if (destName == null) {
                    listDstName = true;
                } else {
		    isDestTypeTopic(broker, destName);
                }

            } catch (BrokerAdminException bae)  {
                if (BrokerAdminException.INVALID_OPERATION == bae.getType())
                    bae.setBrokerErrorStr
                        (ar.getString(ar.I_ERROR_MESG) +
                         ar.getKString(ar.E_DEST_NOT_TOPIC, destName));

                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_FAIL));
                return (1);
            }
		
	    try {
                broker.sendGetDurablesMessage(destName, null);
                Vector durs = broker.receiveGetDurablesReplyMessage();

                if (durs != null) {
                    BrokerCmdPrinter bcp = null;
                    String[] row = null;
                    if (listDstName) {
                        row = new String[6];
                        bcp= new BrokerCmdPrinter(6, 3, "-");
                    } else {
                        row = new String[5];
                        bcp= new BrokerCmdPrinter(5, 3, "-");
                    }
                    if (brokerCmdProps.detailModeSet()) {
                        bcp.setSortNeeded(false);
                    }
                    int indx = 0;
                    row[indx ++] = ar.getString(ar.I_JMQCMD_SUB_NAME);
                    row[indx ++] = ar.getString(ar.I_JMQCMD_CLIENT_ID);
                    if (listDstName)
                        row[indx ++] = ar.getString(ar.I_JMQCMD_DST_NAME);
                    row[indx ++] = ar.getString(ar.I_JMQCMD_DURABLE);
                    row[indx ++] = ar.getString(ar.I_JMQCMD_SUB_NUM_MSG);
                    row[indx ++] = ar.getString(ar.I_JMQCMD_SUB_STATE);
                    bcp.addTitle(row);

                    Enumeration thisEnum = durs.elements();
                    while (thisEnum.hasMoreElements()) {
                        DurableInfo dInfo = (DurableInfo)thisEnum.nextElement();
                        indx = 0;
                        row[indx++] = (dInfo.name == null) ? "" : dInfo.name;
                        row[indx++] = (dInfo.clientID == null) ? "" : dInfo.clientID;
                        if (listDstName) {
                            row[indx++] = (dInfo.consumer == null) ? "" : dInfo.consumer.destination;
                        }
                        row[indx++] = String.valueOf(dInfo.isDurable);
                        row[indx++] = Integer.toString(dInfo.nMessages);
			if (dInfo.isActive) {
                            if (dInfo.isShared) {
                                row[indx] = ar.getString(ar.I_ACTIVE)+"["+dInfo.activeCount+"]"+
                                               (dInfo.isJMSShared ? "jms":"mq");
                            } else {
                                row[indx] = ar.getString(ar.I_ACTIVE);
                            }
                        } else {
                            if (dInfo.isShared) { 
                                row[indx] = ar.getString(ar.I_INACTIVE)+"[0]"+
                                               (dInfo.isJMSShared ? "jms":"mq");
                            } else {
                                row[indx] = ar.getString(ar.I_INACTIVE); 
                            }
                        }
                        if (brokerCmdProps.detailModeSet()) {
                            row[indx] = row[indx]+", ["+dInfo.uidString+"]";
                        }
                        indx++;
                        bcp.add(row);
                        if (brokerCmdProps.detailModeSet()) {
                            if (dInfo.activeConsumers == null) {
                                continue;
                            }
                            for (Map.Entry<String, ConsumerInfo> pair: dInfo.activeConsumers.entrySet()) {
                                ConsumerInfo cinfo = pair.getValue();
                                String cinfostr = null;
                                if (cinfo.connection != null) {
                                    cinfostr = "["+cinfo.uidString+", "+cinfo.subuidString+", ["+
                                                 cinfo.connection.uuid+", "+cinfo.connection+"], "+
                                                cinfo.brokerAddressShortString+"]";
                                } else {
                                    cinfostr = "["+cinfo.uidString+", "+cinfo.subuidString+", "+
                                                 cinfo.brokerAddressShortString+"]";
                                }
                                int tmpindx  = 0;
                                row[tmpindx++] = " ";
                                row[tmpindx++] = " ";
                                if (listDstName) {
                                    row[tmpindx++] = " ";
                                }
                                row[tmpindx++] = " ";
                                row[tmpindx++] = " ";
                                row[tmpindx++] = cinfostr;
                                bcp.add(row);
                            }
                        }
                    }

        	    // Use durname+clientID as the key when listing.
        	    bcp.setKeyCriteria(new int[] {0, 1});
                    bcp.println();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_SUC));

                } else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_FAIL));
                    return (1);
                }

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_SUB_FAIL));
                return (1);
	    }
        } else if (CMDARG_TRANSACTION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_TXN_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
	    if (!force)
	        broker = promptForAuthentication(broker);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_TXN));
	    printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetTxnsMessage(brokerCmdProps.showPartitionModeSet());
		Vector txns = broker.receiveGetTxnsReplyMessage();

                if ((txns != null) && (txns.size() > 0)) {
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(5, 3, "-");
		    BrokerCmdPrinter bcp_local = new BrokerCmdPrinter(4, 3, "-");
		    BrokerCmdPrinter bcp_remote = new BrokerCmdPrinter(4, 3, "-");
		    String[]	row = new String[5], value;
		    Long	tmpLong;
		    Integer	tmpInt;
		    String	tmpStr, tmpStr2;

		    row[0] = ar.getString(ar.I_JMQCMD_TXN_ID);
		    row[1] = ar.getString(ar.I_JMQCMD_TXN_STATE);
		    row[2] = ar.getString(ar.I_JMQCMD_TXN_USERNAME);
		    row[3] = ar.getString(ar.I_JMQCMD_TXN_NUM_MSGS_ACKS);
		    row[4] = ar.getString(ar.I_JMQCMD_TXN_TIMESTAMP);
		    bcp.addTitle(row);

		    row[0] = ar.getString(ar.I_JMQCMD_TXN_ID);
		    row[1] = ar.getString(ar.I_JMQCMD_TXN_STATE);
		    row[2] = ar.getString(ar.I_JMQCMD_TXN_USERNAME);
		    row[3] = ar.getString(ar.I_JMQCMD_TXN_TIMESTAMP);
		    bcp_local.addTitle(row);

		    row[0] = ar.getString(ar.I_JMQCMD_TXN_ID);
		    row[1] = ar.getString(ar.I_JMQCMD_TXN_STATE);
		    row[2] = "# Acks";
		    row[3] = "Remote broker";
		    bcp_remote.addTitle(row);
	
		    Enumeration thisEnum = txns.elements();
		    while (thisEnum.hasMoreElements()) {
			Hashtable txnInfo = (Hashtable)thisEnum.nextElement();

	                Integer type = (Integer)txnInfo.get("type");

			if (type.intValue() == TXN_LOCAL)  {
	                    tmpStr = (String)txnInfo.get(PROP_NAME_TXN_ID);
	                    row[0] = checkNullAndPrint(tmpStr);

	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_STATE);
	                    row[1] = getTxnStateString(tmpInt);

	                    tmpStr = (String)txnInfo.get(PROP_NAME_TXN_USER);
	                    row[2] = checkNullAndPrint(tmpStr);

	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_NUM_MSGS);
	                    tmpStr = checkNullAndPrint(tmpInt);
	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_NUM_ACKS);
	                    tmpStr2 = checkNullAndPrint(tmpInt);
	                    row[3] = tmpStr + "/" + tmpStr2;

	                    tmpLong = (Long)txnInfo.get(PROP_NAME_TXN_TIMESTAMP);
	                    row[4] = checkNullAndPrintTimestamp(tmpLong);

			    bcp.add(row);
			} else if (type.intValue() == TXN_CLUSTER)  {
	                    tmpStr = (String)txnInfo.get(PROP_NAME_TXN_ID);
	                    row[0] = checkNullAndPrint(tmpStr);

	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_STATE);
	                    row[1] = getTxnStateString(tmpInt);

	                    tmpStr = (String)txnInfo.get(PROP_NAME_TXN_USER);
	                    row[2] = checkNullAndPrint(tmpStr);

	                    tmpLong = (Long)txnInfo.get(PROP_NAME_TXN_TIMESTAMP);
	                    row[3] = checkNullAndPrintTimestamp(tmpLong);

			    bcp_local.add(row);
			} else if (type.intValue() == TXN_REMOTE)  {
	                    tmpStr = (String)txnInfo.get(PROP_NAME_TXN_ID);
	                    row[0] = checkNullAndPrint(tmpStr);

	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_STATE);
	                    row[1] = getTxnStateString(tmpInt);

	                    tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_NUM_ACKS);
	                    tmpStr2 = checkNullAndPrint(tmpInt);
	                    row[2] = tmpStr2;

	                    tmpStr = (String)txnInfo.get("homebroker");
	                    row[3] = checkNullAndPrint(tmpStr);

			    bcp_remote.add(row);
			}
		    }

                    Globals.stdOutPrintln(
			    "Transactions that are owned by this broker");
		    bcp.println();

                    Globals.stdOutPrintln(
			    "   Transactions that involve remote brokers");
		    bcp_local.setIndent(3);
		    bcp_local.println();

                    Globals.stdOutPrintln("Transactions that are owned by a remote broker");
		    bcp_remote.println();

                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_TXN_SUC));

                } else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_TXN_NONE));
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_TXN_FAIL));
                return (1);
            }

        } else if (CMDARG_CONNECTION.equals(commandArg)) {
            String svcName = brokerCmdProps.getService();

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
	    if (!force)
	        broker = promptForAuthentication(broker);

	    if (svcName == null)  {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN));
                printBrokerInfo(broker);
	    } else  {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN_FOR_SVC));
                printServiceInfo(svcName);

                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
                printBrokerInfo(broker);
	    }

            try  {
                connectToBroker(broker);

                broker.sendGetConnectionsMessage(svcName, null);
		Vector cxnList = broker.receiveGetConnectionsReplyMessage();

                if ((cxnList != null) && (cxnList.size() > 0)) {
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(6, 2, "-");
		    String[]	row = new String[6], value;
		    Long	tmpLong;
		    Integer	tmpInt;
		    String	tmpStr;
		    int i;

		    i = 0;
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_CXN_ID);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_USER);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_SERVICE);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_NUM_PRODUCER);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_NUM_CONSUMER);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_HOST);

		    /*
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_CLIENT_ID);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_PORT);
		    row[i++] = ar.getString(ar.I_JMQCMD_CXN_CLIENT_PLATFORM);
		    */
		    bcp.addTitle(row);
	
		    Enumeration thisEnum = cxnList.elements();
		    while (thisEnum.hasMoreElements()) {
			Hashtable cxnInfo = (Hashtable)thisEnum.nextElement();

		        i = 0;

	                tmpLong = (Long)cxnInfo.get(PROP_NAME_CXN_CXN_ID);
	                row[i++] = checkNullAndPrint(tmpLong);

	                tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_USER);
	                row[i++] = checkNullAndPrint(tmpStr);

	                tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_SERVICE);
	                row[i++] = checkNullAndPrint(tmpStr);

	                tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_NUM_PRODUCER);
	                row[i++] = checkNullAndPrint(tmpInt);

	                tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_NUM_CONSUMER);
	                row[i++] = checkNullAndPrint(tmpInt);

	                tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_HOST);
	                row[i++] = checkNullAndPrint(tmpStr);

			/*
	                tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_CLIENT_ID);
	                row[i++] = checkNullAndPrint(tmpStr);

	                tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_PORT);
	                row[i++] = checkNullAndPrint(tmpInt);

	                tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_CLIENT_PLATFORM);
	                row[i++] = checkNullAndPrint(tmpStr);
			*/

			bcp.add(row);
		    }

		    bcp.println();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN_SUC));

                } else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN_NONE));
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_CXN_FAIL));
                return (1);
            }
        } else if (CMDARG_BROKER.equals(commandArg)) {
            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_BKR_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
	    if (!force)
	        broker = promptForAuthentication(broker);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

		/*
		 * Get broker props to find out if broker is in HA cluster and 
		 * broker cluster ID
		 */
                broker.sendGetBrokerPropsMessage();
                Properties bkrProps = broker.receiveGetBrokerPropsReplyMessage();

		BrokerCmdPrinter haBcp = new BrokerCmdPrinter(2, 3, null);
		String[]	haInfoRow = new String[2];

		/*
		 * Check if cluster is HA or not
		 */
		String value = bkrProps.getProperty(PROP_NAME_BKR_CLS_HA);
		boolean isHA = Boolean.valueOf(value).booleanValue();
		value = bkrProps.getProperty(PROP_NAME_BKR_STORE_MIGRATABLE);
		boolean isStoreMigratable = Boolean.valueOf(value).booleanValue();

		/*
		 * Display cluster ID only if HA cluster
		 */
		//if (isHA)  {
		    haInfoRow[0] = ar.getString(ar.I_CLS_CLUSTER_ID);
		    value = bkrProps.getProperty(PROP_NAME_BKR_CLS_CLUSTER_ID, "");
		    haInfoRow[1] = value;
		    haBcp.add(haInfoRow);
		//}

		haInfoRow[0] = ar.getString(ar.I_CLS_IS_HA);
		if (!isHA)  {
	    	    haInfoRow[1] = Boolean.FALSE.toString();
		} else  {
	    	    haInfoRow[1] = Boolean.TRUE.toString();
		}
		haBcp.add(haInfoRow);

		haBcp.println();

		/*
		 * Get state of each broker in cluster
		 */
                broker.sendGetClusterMessage(true);
		Vector bkrList = broker.receiveGetClusterReplyMessage();

                if ((bkrList != null) && (bkrList.size() > 0)) {
		    BrokerCmdPrinter bcp;
		    String[]	row;
		    Long tmpLong;
		    Integer tmpInt;
		    long idle;
		    int	i;

		    if (isHA)  {
		        bcp = new BrokerCmdPrinter(6, 3, "-");
		        row = new String[6];

		        bcp.setSortNeeded(false);
		        bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

		        i = 0;
		        row[i++] = "";
		        row[i++] = "";
		        row[i++] = "";
		        row[i++] = "";
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_TAKEOVER_ID1);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP1);

		        bcp.addTitle(row);
	
		        i = 0;
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_ID);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_ADDRESS);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_STATE);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_NUM_MSGS);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_TAKEOVER_ID2);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP2);

		        bcp.addTitle(row);
	
		        Enumeration thisEnum = bkrList.elements();
		        while (thisEnum.hasMoreElements()) {
			    Hashtable bkrClsInfo 
				= (Hashtable)thisEnum.nextElement();

		            i = 0;

	                    row[i++] = 
			        checkNullAndPrint(
				    bkrClsInfo.get(BrokerClusterInfo.ID));

	                    row[i++] = 
			        checkNullAndPrint(
				    bkrClsInfo.get(BrokerClusterInfo.ADDRESS));

	                    tmpInt = (Integer)bkrClsInfo.get(BrokerClusterInfo.STATE);
			    if (tmpInt != null)  {
	                        row[i++] = BrokerState.toString(tmpInt.intValue());
			    } else  {
	                        row[i++] = "";
			    }

	                    tmpLong = (Long)bkrClsInfo.get(BrokerClusterInfo.NUM_MSGS);
	                    row[i++] = checkNullAndPrint(tmpLong);

	                    row[i++] = checkNullAndPrint(
			                bkrClsInfo.get(
					    BrokerClusterInfo.TAKEOVER_BROKER_ID));

			    tmpLong = (Long)bkrClsInfo.get(
				    BrokerClusterInfo.STATUS_TIMESTAMP);
			    if (tmpLong != null)  {
			        idle = System.currentTimeMillis() - tmpLong.longValue();
	                        row[i++] = CommonCmdRunnerUtil.getTimeString(idle);
			    } else  {
	                        row[i++] = "";
			    }

			    bcp.add(row);
		        }
		    } else if (isStoreMigratable) {
		        bcp = new BrokerCmdPrinter(3, 3, "-");
		        row = new String[3];

		        bcp.setSortNeeded(false);
		        bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

		        i = 0;
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_ID);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_ADDRESS);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_STATE);

		        bcp.addTitle(row);
	
		        Enumeration thisEnum = bkrList.elements();
		        while (thisEnum.hasMoreElements()) {
                    Hashtable bkrClsInfo = (Hashtable)thisEnum.nextElement();
                    i = 0;
			        row[i++] = checkNullAndPrint( bkrClsInfo.get(BrokerClusterInfo.ID));
                    row[i++] = checkNullAndPrint(bkrClsInfo.get(BrokerClusterInfo.ADDRESS));
                    tmpInt = (Integer)bkrClsInfo.get(BrokerClusterInfo.STATE);
                    if (tmpInt != null)  {
                        row[i++] = BrokerState.toString(tmpInt.intValue());
                    } else  {
                        row[i++] = "";
                    }
                    bcp.add(row);
		        }
           } else {
		        bcp = new BrokerCmdPrinter(2, 3, "-");
		        row = new String[2];

		        bcp.setSortNeeded(false);
		        bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

		        i = 0;
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_ADDRESS);
		        row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_STATE);

		        bcp.addTitle(row);
	
		        Enumeration thisEnum = bkrList.elements();
		        while (thisEnum.hasMoreElements()) {
			    Hashtable bkrClsInfo 
				= (Hashtable)thisEnum.nextElement();

		            i = 0;

	                    row[i++] = 
			        checkNullAndPrint(
				    bkrClsInfo.get(BrokerClusterInfo.ADDRESS));

	                    tmpInt = (Integer)bkrClsInfo.get(BrokerClusterInfo.STATE);
			    if (tmpInt != null)  {
	                        row[i++] = BrokerState.toString(tmpInt.intValue());
			    } else  {
	                        row[i++] = "";
			    }

			    bcp.add(row);
		        }
		    }

		    bcp.println();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_BKR_SUC));
                } else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_BKR_NONE));
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_BKR_FAIL));
                return (1);
            }
	} else if (CMDARG_JMX_CONNECTOR.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetJMXConnectorsMessage(null);
		Vector jmxList = broker.receiveGetJMXConnectorsReplyMessage();

		if (jmxList != null) {
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(3, 4, null);
		    String[] row = new String[3];
		    row[0] = ar.getString(ar.I_JMQCMD_JMX_NAME);
		    row[1] = ar.getString(ar.I_JMQCMD_JMX_ACTIVE);
		    row[2] = ar.getString(ar.I_JMQCMD_JMX_URL);
		    bcp.addTitle(row);
	
                    Enumeration thisEnum = jmxList.elements();
                    while (thisEnum.hasMoreElements()) {
                        Hashtable jmxInfo = (Hashtable)thisEnum.nextElement();
			int i = 0;

	                row[i++] = checkNullAndPrint(jmxInfo.get(PROP_NAME_JMX_NAME));
	                row[i++] = checkNullAndPrint(jmxInfo.get(PROP_NAME_JMX_ACTIVE));
	                row[i++] = checkNullAndPrint(jmxInfo.get(PROP_NAME_JMX_URL));

			bcp.add(row);
		    }

		    bcp.println();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX_SUC));

                } else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX_NONE));

                    Globals.stdOutPrintln("");
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX_SUC));
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_JMX_FAIL));
                return (1);
            }

	} else if (CMDARG_MSG.equals(commandArg)) {

            if (broker == null)  {
		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Listing messages failed.");
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            destTypeMask = getDestTypeMask(brokerCmdProps);
	    Long maxNumMsgsRetrieved = brokerCmdProps.getMaxNumMsgsRetrieved(),
			startMsgIndex = brokerCmdProps.getStartMsgIndex();

	    /*
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG));
	    */
            Globals.stdOutPrintln("Listing messages for the destination");
	    printDestinationInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetMessagesMessage(destName, destTypeMask, false, null, 
						startMsgIndex, maxNumMsgsRetrieved);
		Vector msgList = broker.receiveGetMessagesReplyMessage();

		if ((msgList != null) && (msgList.size() != 0)) {
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(4, 3, "-");
		    String[] row = new String[4];

	            bcp.setSortNeeded(false);

		    int i = 0;
		    row[i++] = "Message #";
		    row[i++] = "Message IDs";
		    row[i++] = "Priority";
	            row[i++] = "Body Type";
		    bcp.addTitle(row);
	
		    long start = 0;
		    if (startMsgIndex != null)  {
		        start = startMsgIndex.longValue();
		    }
                    Enumeration thisEnum = msgList.elements();
                    while (thisEnum.hasMoreElements()) {
                        HashMap oneMsg = (HashMap)thisEnum.nextElement();
			i = 0;
			/*
                        String oneID = (String)thisEnum.nextElement();
			*/

	                row[i++] = Long.toString(start++);
	                row[i++] = checkNullAndPrint(oneMsg.get("MessageID"));
	                row[i++] = checkNullAndPrint(oneMsg.get("Priority"));
	                row[i++] = checkNullAndPrintMsgBodyType(
					(Integer)oneMsg.get("MessageBodyType"), false);

			bcp.add(row);
		    }

		    bcp.println();
		    /*
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_SUC));
		    */
                    Globals.stdOutPrintln("Successfully listed messages.");

                } else  {
		    /*
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_NONE));
		    */
                    Globals.stdErrPrintln("There are no messages.");

                    Globals.stdOutPrintln("");
		    /*
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_SUC));
		    */
                    Globals.stdOutPrintln("Successfully listed messages.");
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Listing messages failed.");
                return (1);
            }
	}

        broker.close();

        return (0);
    }


    private void listDests(BrokerCmdProperties brokerCmdProps, 
                           Vector dests, int listType)  {
        BrokerCmdPrinter bcp = setupListDestTitle(listType);
        String[] row = new String[12];
	int i = 0;

        Enumeration thisEnum = dests.elements();

        while (thisEnum.hasMoreElements()) {
            DestinationInfo dInfo = (DestinationInfo)thisEnum.nextElement();
	    int j = 0, numMsgs;
	    long totalMsgSize;
	    float avgMsgSize = 0;
	    String destType;
            if (MessageType.JMQ_ADMIN_DEST.equals(dInfo.name) ||
                MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(dInfo.name))
		continue;

	    if (DestType.isInternal(dInfo.fulltype))
		continue;

            // List temporary destinations only if the "-tmp" flag is
            // specified.  This will also display the admin temporary
            // destination(s), since there is currently no way to
            // differentiate it.
            if (DestType.isTemporary(dInfo.type)) {
                if (brokerCmdProps.showTempDestModeSet()) {
                    destType = BrokerAdminUtil.getDestinationType(dInfo.type) 
                            + " (" 
		            + ar.getString(ar.I_TEMPORARY) 
		            + ")";
		} else  {
		    continue;
		}
	    } else  {
                destType = BrokerAdminUtil.getDestinationType(dInfo.type);
	    }

	    if ((listType == LIST_TOPIC) && !DestType.isTopic(dInfo.type))  {
		continue;
	    }

	    if ((listType == LIST_QUEUE) && !DestType.isQueue(dInfo.type))  {
		continue;
	    }

	    /*
	     * get total msgs, calculate average size
	     */
	    numMsgs = dInfo.nMessages - dInfo.nTxnMessages;
	    totalMsgSize = dInfo.nMessageBytes;
	    if (numMsgs > 0) {
	        avgMsgSize = (float)totalMsgSize/(float)numMsgs;
            }

            row[j++] = dInfo.name;
            row[j++] = destType;
            //row[j++] = DestState.toString(dInfo.destState);
            row[j++] = BrokerAdminUtil.getDestinationState(dInfo.destState);
            
            row[j++] = Integer.toString(dInfo.nProducers);

            if (listType != LIST_QUEUE)  {
	    if (DestType.isTopic(dInfo.type))  {
		/*
		 * For topics, show number of producer wildcards, if any.
		 */
		Hashtable h = dInfo.producerWildcards;
                row[j++] = Integer.toString(getWildcardCount(h));
	    } else  {
		/*
		 * Wildcards not applicable for queues.
		 */
                row[j++] = "-";
	    }
            }

	    /*
	     * Use cases:
	     *  list dst -t t
	     *	  -> show total consumers
	     *	  -> show total wildcard consumers
	     *  list dst -t q
	     *	  -> show active/backup consumers
	     *  list dst
	     *	  -> show total consumers
	     *	  -> show total wildcard consumers for topics
	     *	  -> show "-"  for queues
	     */
	    if (DestType.isTopic(dInfo.type))  {
	        row[j++] = Integer.toString(dInfo.nConsumers);

		/*
		 * For topics, show number of producer wildcards, if any.
		 */
		Hashtable h = dInfo.consumerWildcards;
                row[j++] = Integer.toString(getWildcardCount(h));
	    } else  {
	        if (listType == LIST_QUEUE)  {
	            row[j++] = Integer.toString(dInfo.naConsumers);
	            row[j++] = Integer.toString(dInfo.nfConsumers);
		} else  {
	            row[j++] = Integer.toString(dInfo.naConsumers + dInfo.nfConsumers);
		    /*
		     * Wildcards not applicable for queues.
		     */
                    row[j++] = "-";
		}
	    }

            row[j++] = Integer.toString(numMsgs);
            row[j++] = Integer.toString(dInfo.nRemoteMessages);
            row[j++] = Integer.toString(dInfo.nUnackMessages);
            row[j++] = Integer.toString(dInfo.nInDelayMessages);
            row[j++] = Float.valueOf(avgMsgSize).toString();

            bcp.add(row);
        }

        // Fix for bug 4495379: jmqcmd: when create queue and topic
        // with same name only one is listed
        // Use name+type as the key when listing.
        bcp.setKeyCriteria(new int[] {0, 1});
        bcp.println();
    }

    /*
     * The Hashble contains wildcard consumers (or producers).
     * Each entry is of the form:
     *		<wildcard, count>
     *
     * eg.
     *
     *		<*.sun, 2>
     *		<news.*, 4>
     *
     * This method returns the total # of wildcard consumers or
     * producers. In the example above, the total would be
     *		2 + 4 = 6.
     */
    private int getWildcardCount(Hashtable h)  {
	int count = 0;

	if (h == null)  {
	    return (0);
	}

	Enumeration keys = h.keys();

	while (keys.hasMoreElements())  {
	    String wildcard = (String)keys.nextElement();
	    Integer val = (Integer)h.get(wildcard);
	    count += val.intValue();
	}

	return (count);
    }

    //This method is called from test as well
    public String getTxnStateString(Integer txnState)  {

	if (txnState == null)  {
	    return ("");
	}

	int	tmpInt = txnState.intValue();

	/*
	 * Instead of hardcoding the values 0 - 7 here we should get it
	 * from a interface or class shared by the broker and admin.
	 * The current values are currently in a broker private class:
	 *	com.sun.messaging.jmq.jmsserver.data.TransactionState
	 */
	switch (tmpInt) {
	case 0:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_CREATED));
	case 1:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_STARTED));
	case 2:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_FAILED));
	case 3:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_INCOMPLETE));
	case 4:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_COMPLETE));
	case 5:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_PREPARED));
	case 6:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_COMMITTED));
	case 7:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_ROLLEDBACK));
	default:
	    return(ar.getString(ar.I_JMQCMD_TXN_STATE_UNKNOWN));
	}
    }

    private int runPause(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR));
            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PAUSE_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendPauseMessage(null);
	            broker.receivePauseReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_BKR_NOOP));
                return (1);
            }

	} else if (CMDARG_SERVICE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

	    String svcName = brokerCmdProps.getTargetName();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC));
	    printServiceInfo();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	    printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
            
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_FAIL));
                return (1);
            }

	    try {
	        isAdminService(broker, svcName);

            } catch (BrokerAdminException bae)  {
		if (BrokerAdminException.INVALID_OPERATION == bae.getType())
                    bae.setBrokerErrorStr
                        (ar.getString(ar.I_ERROR_MESG) +
                         ar.getKString(ar.E_CANNOT_PAUSE_SVC, svcName));

                handleBrokerAdminException(bae);
            
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PAUSE_SVC_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendPauseMessage(svcName);
		    broker.receivePauseReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_SUC));

                } catch (BrokerAdminException bae)  {
	    	    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_SVC_NOOP));
                return (1);
            }

	} else if (CMDARG_DESTINATION.equals(commandArg)) {
	    String destName, pauseTypeStr;
	    BrokerCmdPrinter bcp = new BrokerCmdPrinter(2,4);
	    String[] row = new String[2];
	    boolean pauseAll = true;
	    int destTypeMask;

	    destName = brokerCmdProps.getTargetName();
	    destTypeMask = getDestTypeMask(brokerCmdProps);
            pauseTypeStr = brokerCmdProps.getPauseType();

	    if (destName != null)  {
		pauseAll = false;
	    }

            if (broker == null)  {
		if (pauseAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_FAIL));
		}
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

	    if (pauseAll)  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS));
	    } else  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST));
	        printDestinationInfo();
	    }

	    // Only print out the pause type if it was specified
	    if (pauseTypeStr != null) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_USING_ATTR));

	        row[0] = ar.getString(ar.I_JMQCMD_PAUSE_DST_TYPE);
                row[1] = pauseTypeStr;
	        bcp.add(row);
	        bcp.println();
	    }


	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

		if (pauseAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_FAIL));
		}
                return (1);
            }

            if (!force) {
		if (pauseAll)  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PAUSE_DSTS_OK), noShort);
		} else  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PAUSE_DST_OK), noShort);
		}
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
		    int pauseType = getPauseTypeVal(pauseTypeStr);
                    broker.sendPauseMessage(destName, destTypeMask, pauseType);
	            broker.receivePauseReplyMessage();
		    if (pauseAll)  {
                        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_SUC));
		    } else  {
                        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_SUC));
		    }

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

		    if (pauseAll)  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_FAIL));
		    } else  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_FAIL));
		    }
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
		if (pauseAll)  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_NOOP));
		} else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_NOOP));
		}
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
		if (pauseAll)  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DSTS_NOOP));
		} else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PAUSE_DST_NOOP));
		}
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    private int runReset(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);
	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {
	    String resetType;
	    BrokerCmdPrinter bcp = new BrokerCmdPrinter(2,4);
	    String[] row = new String[2];

            resetType = brokerCmdProps.getResetType();

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR));

	    // Only print out the pause type if it was specified
	    if (resetType != null) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_USING_ATTR));

	        row[0] = ar.getString(ar.I_JMQCMD_RESET_BKR_TYPE);
                row[1] = resetType;
	        bcp.add(row);
	        bcp.println();
	    }

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESET_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
		    String resetTypeVal = getResetTypeVal(resetType);
                    broker.sendResetBrokerMessage(resetTypeVal);
	            broker.receiveResetBrokerReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESET_BKR_NOOP));
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    
    private int runCheckpoint(BrokerCmdProperties brokerCmdProps) {

		BrokerAdmin broker;
		String input = null;
		String yes, yesShort, no, noShort;
		//String sessionID;

		yes = ar.getString(ar.Q_RESPONSE_YES);
		yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
		no = ar.getString(ar.Q_RESPONSE_NO);
		noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

		broker = init();

		if (broker == null) {
			Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR_FAIL));
			return (1);
		}

		boolean force = brokerCmdProps.forceModeSet();
		if (!force)
			broker = promptForAuthentication(broker);

		//boolean noFailover = brokerCmdProps.noFailoverSet();
		//int time = brokerCmdProps.getTime();

		Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR));
		printBrokerInfo(broker);

		try {
			connectToBroker(broker);
			//sessionID = getPortMapperSessionID(brokerCmdProps, broker);

		} catch (BrokerAdminException bae) {
			handleBrokerAdminException(bae);

			Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR_FAIL));
			return (1);
		}

		if (!force) {
			input = CommonCmdRunnerUtil.getUserInput(ar
					.getString(ar.Q_CHECKPOINT_BKR_OK), noShort);
			Globals.stdOutPrintln("");
		}

		if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input)
				|| force) {
			try {
				broker.sendCheckpointBrokerMessage();
				broker.receiveCheckpointBrokerReplyMessage();
				 Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR_SUC));


			} catch (BrokerAdminException bae) {
				handleBrokerAdminException(bae);

				Globals.stdErrPrintln(ar
						.getString(ar.I_JMQCMD_CHECKPOINT_BKR_FAIL));
				return (1);
			}

		} else if (noShort.equalsIgnoreCase(input)
				|| no.equalsIgnoreCase(input)) {
			Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR_NOOP));
			return (0);

		} else {
			Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
			Globals.stdOutPrintln("");
			Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHECKPOINT_BKR_NOOP));
			return (1);
		}
		
		broker.close();
		return (0);

	}

    private int runResume(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);


        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
            
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESUME_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendResumeMessage(null);
	            broker.receiveResumeReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_SUC));

                } catch (BrokerAdminException bae)  {
	    	    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_BKR_NOOP));
                return (1);
            }

	} else if (CMDARG_SERVICE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

	    String svcName = brokerCmdProps.getTargetName();
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC));
	    printServiceInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_FAIL));
                return (1);
            }

            try {
                isAdminService(broker, svcName);

            } catch (BrokerAdminException bae)  {
                if (BrokerAdminException.INVALID_OPERATION == bae.getType())
                    bae.setBrokerErrorStr
                        (ar.getString(ar.I_ERROR_MESG) +
                         ar.getKString(ar.E_CANNOT_RESUME_SVC, svcName));

                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESUME_SVC_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendResumeMessage(svcName);
		    broker.receiveResumeReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_SVC_NOOP));
                return (1);
            }
	} else if (CMDARG_DESTINATION.equals(commandArg)) {
	    String destName;
	    int destTypeMask;
	    boolean resumeAll = true;

	    destName = brokerCmdProps.getTargetName();
	    destTypeMask = getDestTypeMask(brokerCmdProps);

	    if (destName != null)  {
		resumeAll = false;
	    }

            if (broker == null)  {
		if (resumeAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_FAIL));
		}
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

	    if (resumeAll)  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS));
	    } else  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST));

	        printDestinationInfo();

                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	    }

            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
            
		if (resumeAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_FAIL));
		}
                return (1);
            }

            if (!force) {
		if (resumeAll)  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESUME_DSTS_OK), noShort);
		} else  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESUME_DST_OK), noShort);
		}
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendResumeMessage(destName, destTypeMask);
	            broker.receiveResumeReplyMessage();
		    if (resumeAll)  {
                        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_SUC));
		    } else  {
                        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_SUC));
		    }

                } catch (BrokerAdminException bae)  {
	    	    handleBrokerAdminException(bae);

		    if (resumeAll)  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_FAIL));
		    } else  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_FAIL));
		    }
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
		if (resumeAll)  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_NOOP));
		} else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_NOOP));
		}
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
		if (resumeAll)  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DSTS_NOOP));
		} else  {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESUME_DST_NOOP));
		}
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    private int runShutdown(BrokerCmdProperties brokerCmdProps) {
	BrokerAdmin 	broker;
	String 		input = null;
	String 		yes, yesShort, no, noShort, sessionID;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);


	broker = init();

        if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_FAIL));
            return (1);
        }

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        boolean noFailover = brokerCmdProps.noFailoverSet();
	int time = brokerCmdProps.getTime();
	
        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);
            sessionID =  getPortMapperSessionID(brokerCmdProps, broker);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_FAIL));
            return (1);
        }

        if (!force) {
            input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_SHUTDOWN_BKR_OK), noShort);
            Globals.stdOutPrintln("");
        }

        if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
            try  {
	        broker.sendShutdownMessage(false, false, noFailover, time);
                broker.receiveShutdownReplyMessage();

                if (waitForShutdown(broker, sessionID, brokerCmdProps))  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_SUC));
		} else  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SENT_SHUTDOWN_BKR_SUC));
		}

	    } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

	        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_FAIL));
	        return (1);
	    }

        } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_NOOP));
            return (0);

        } else {
            Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SHUTDOWN_BKR_NOOP));
            return (1);
        }
	/*
	 * We don't need to call broker.close() since the broker
	 * connection should be gone.
 	 * broker.close();
	 */
	return (0);
    }

    /*
     * Wait for broker to shutdown.
     * Returns true if a wait was performed.
     * Returns false if no waiting was done - return immediately.
     */
    private boolean waitForShutdown(BrokerAdmin broker, String waitSessionID, 
				BrokerCmdProperties brokerCmdProps)  {
	GenericPortMapperClient pmc = null;
	String hostName = broker.getBrokerHost(),
		portString = broker.getBrokerPort(),
		sessionID = null;
	int port, shutdownDelaySecs;
	long sleepTime;

	/*
	 * Don't wait if don't have broker props
	 */
	if (brokerCmdProps == null)  {
	    return (false);
	}

	sleepTime = brokerCmdProps.getShutdownWaitInterval();
	shutdownDelaySecs = brokerCmdProps.getTime();

	/*
	 * Don't wait if delayed shutdown (ie 'imqcmd shutdown bkr' used with -time <non-zero time>)
	 */
	if (shutdownDelaySecs > 0)  {
	    return (false);
	}

	/*
	 * Don't wait if cannot identify the specific broker to wait for.
	 */
	if (waitSessionID == null)  {
	    return (false);
	}

	try  {
	    port = Integer.parseInt(portString);
	} catch(Exception e)  {
	    port = 7676;
	}

	boolean brokerDown = false;
	int count = 0;

	Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_WAITING_FOR_SHUTDOWN, 
				hostName + ":" + portString));
	while (!brokerDown)  {
	    try  {
		Thread.sleep(sleepTime);
	    } catch (Exception e)  {
	    }

	    try  {
	        pmc = new GenericPortMapperClient(hostName, port);
	    } catch(Exception e)  {
	        brokerDown = true;
	    }

	    if (pmc != null)  {
	        sessionID = pmc.getProperty("sessionid", null, "PORTMAPPER", "portmapper");

		if (sessionID == null)  {
		    brokerDown = true;
		} else if (!sessionID.equals(waitSessionID))  {
		    brokerDown = true;
		}
	    }
	}
	
	return (true);
    }

    private String getPortMapperSessionID(BrokerCmdProperties brokerCmdProps, BrokerAdmin broker)  {
	GenericPortMapperClient pmc;
	String hostName = broker.getBrokerHost(),
		portString = broker.getBrokerPort(),
		sessionID = null;
	int port;


	try  {
	    port = Integer.parseInt(portString);
	} catch(Exception e)  {
	    port = 7676;
	}

	try  {
	    pmc = new GenericPortMapperClient(hostName, port);
	    sessionID = pmc.getProperty("sessionid", null, "PORTMAPPER", "portmapper");
	} catch(Exception e)  {
	}

	return (sessionID);
    }

    private int runRestart(BrokerCmdProperties brokerCmdProps) {
	BrokerAdmin 	broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);


	broker = init();

	if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_FAIL));
	    return (1);
	}

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_FAIL));
            return (1);
        }

        if (!force) {
            input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_RESTART_BKR_OK), noShort);
            Globals.stdOutPrintln("");
        }

        if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
            try  {
	        broker.sendShutdownMessage(true);
	        broker.receiveShutdownReplyMessage();
		/*
		 * Shutdown was successful.  Now wait to see if jmqcmd can get
		 * reconnected back to the broker.
		 */
		if (reconnectToBroker(broker))
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_SUC));
		else {
            	    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_FAIL));
	            return (1);
		}
	
	    } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

	        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_FAIL));
	        return (1);
	    }

        } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_NOOP));
            return (0);

        } else {
            Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RESTART_BKR_NOOP));
            return (1);
        }

	broker.close();

	return (0);
    }

    private int runCreate(BrokerCmdProperties brokerCmdProps) {
	BrokerAdmin	broker;
	DestinationInfo	destInfo;
	String		destName;
	int		destTypeMask;
	Properties	destAttrs;

	broker = init();

	if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CREATE_DST_FAIL));
	    return (1);
	}

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

	destName = brokerCmdProps.getTargetName();
	destTypeMask = getDestTypeMask(brokerCmdProps);
	destAttrs = brokerCmdProps.getTargetAttrs();

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CREATE_DST));

	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2,4);
	String[] row = new String[2];

	bcp.setSortNeeded(false);

	row[0] = ar.getString(ar.I_JMQCMD_DST_NAME);
	row[1] = destName;
	bcp.add(row);

	row[0] = ar.getString(ar.I_JMQCMD_DST_TYPE);
        row[1] = BrokerAdminUtil.getDestinationType(destTypeMask);
	bcp.add(row);

	/*
	// Only print out the flavor type if the destination is a queue.
	if (DestType.isQueue(destTypeMask)) {
	    row[0] = ar.getString(ar.I_JMQCMD_DST_FLAVOR);
            row[1] = BrokerAdminUtil.getDestinationFlavor(destTypeMask);
	    bcp.add(row);
	}
	*/

  	// Check for optional destination attributes.
	// Note that the same checking is done twice; once for printing
	// and once for creating the DestinationInfo object.  It can
	// be combined, but this is cleaner.
	String prop = null;
	if ((prop = destAttrs.getProperty
             (PROP_NAME_OPTION_MAX_MESG)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_ALLOW);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_OPTION_MAX_MESG_BYTE)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_BYTES_ALLOW);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_OPTION_MAX_PER_MESG_SIZE)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_BYTES_PER_MSG_ALLOW);
             row[1] = prop;
             bcp.add(row);
	}

	if ((prop = destAttrs.getProperty
             (PROP_NAME_MAX_PRODUCERS)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_PRODUCERS);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_ACTIVE_CONSUMER_COUNT);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_FAILOVER_CONSUMER_COUNT);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_LIMIT_BEHAVIOUR)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_LIMIT_BEHAVIOUR);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_CONSUMER_FLOW_LIMIT)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_CONS_FLOW_LIMIT);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_IS_LOCAL_DEST)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_IS_LOCAL_DEST);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_LOCAL_DELIVERY_PREF)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_LOCAL_DELIVERY_PREF);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_USE_DMQ)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_USE_DMQ);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_VALIDATE_XML_SCHEMA_ENABLED);
             row[1] = prop;
             bcp.add(row);
	}
	if ((prop = destAttrs.getProperty
             (PROP_NAME_XML_SCHEMA_URI_LIST)) != null) {
	     row[0] = ar.getString(ar.I_JMQCMD_DST_XML_SCHEMA_URI_LIST);
             row[1] = prop;
             bcp.add(row);
	}
	bcp.println();

	Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	printBrokerInfo(broker);

	try {
	    SizeString	ss;
	    long	byteValue;

	    destInfo = new DestinationInfo();

	    destInfo.setType(destTypeMask);
	    destInfo.setName(destName);

  	    // Check for optional destination attributes
	    if ((prop = destAttrs.getProperty
		(PROP_NAME_OPTION_MAX_MESG_BYTE)) != null) {
		try  {
		    ss = new SizeString(prop);
		    byteValue = ss.getBytes();
	            destInfo.setMaxMessageBytes(byteValue);
		} catch (NumberFormatException nfe)  {
		    /*
		     * Do nothing. We shouldn't ever get here since
		     * we do input validation prior to all this.
		     */
		}

	    }
	    if ((prop = destAttrs.getProperty
		(PROP_NAME_OPTION_MAX_MESG)) != null) {
	        destInfo.setMaxMessages(Integer.parseInt(prop));
	    }
	    if ((prop = destAttrs.getProperty
		(PROP_NAME_OPTION_MAX_PER_MESG_SIZE)) != null) {
		try  {
		    ss = new SizeString(prop);
		    byteValue = ss.getBytes();
	            destInfo.setMaxMessageSize(byteValue);
		} catch (NumberFormatException nfe)  {
		    /*
		     * Do nothing. We shouldn't ever get here since
		     * we do input validation prior to all this.
		     */
		}
	    }

	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT)) != null) {
	        destInfo.setMaxFailoverConsumers(Integer.parseInt(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT)) != null) {
	        destInfo.setMaxActiveConsumers(Integer.parseInt(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_IS_LOCAL_DEST)) != null) {
	        destInfo.setScope(Boolean.valueOf(prop).booleanValue());
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_LIMIT_BEHAVIOUR)) != null) {
	        destInfo.setLimitBehavior(getLimitBehavValue(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_LOCAL_DELIVERY_PREF)) != null) {
	        destInfo.setClusterDeliveryPolicy(getClusterDeliveryPolicy(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_CONSUMER_FLOW_LIMIT)) != null) {
	        destInfo.setPrefetch(Integer.parseInt(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_MAX_PRODUCERS)) != null) {
	        destInfo.setMaxProducers(Integer.parseInt(prop));
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_USE_DMQ)) != null) {
	        destInfo.setUseDMQ(Boolean.valueOf(prop).booleanValue());
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED)) != null) {
	        destInfo.setValidateXMLSchemaEnabled(Boolean.valueOf(prop).booleanValue());
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_XML_SCHEMA_URI_LIST)) != null) {
	        destInfo.setXMLSchemaUriList(prop);
	    }
	    if ((prop = destAttrs.getProperty
                 (PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE)) != null) {
	        destInfo.setReloadXMLSchemaOnFailure(Boolean.valueOf(prop).booleanValue());
	    }

	    connectToBroker(broker);

	    broker.sendCreateDestinationMessage(destInfo);
	    broker.receiveCreateDestinationReplyMessage();
	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CREATE_DST_SUC));

	} catch (BrokerAdminException bae)  {
	    handleBrokerAdminException(bae);

	    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CREATE_DST_FAIL));
	    return (1);
	}

	broker.close();

	return (0);
    }

    private int runDestroy(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin     broker;
	String 		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        broker = init();

        if (CMDARG_DESTINATION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST));
	    printDestinationInfo();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_FAIL));
                return (1);
            }

    	    if (!force) {
	        input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_DESTROY_DST_OK), noShort);
	        Globals.stdOutPrintln("");
	    }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendDestroyDestinationMessage(destName, destTypeMask);
                    broker.receiveDestroyDestinationReplyMessage();
		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_SUC));

                } catch (BrokerAdminException bae)  {
	            handleBrokerAdminException(bae);

		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_FAIL));
                    return (1);
	        }
	
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DST_NOOP));
                return (1);
            }
	} else if (CMDARG_DURABLE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            String subName = brokerCmdProps.getTargetName();
            String clientID = brokerCmdProps.getClientID();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR));
	    printDurableSubscriptionInfo();           
 
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);
            
            try {
                connectToBroker(broker);
                
            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_FAIL));
                return (1);
            }   

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_DESTROY_DUR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendDestroyDurableMessage(subName, clientID);
                    broker.receiveDestroyDurableReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_SUC));
                    
                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);
                    
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_FAIL));
                    return (1);
                }   
                
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_DUR_NOOP));
                return (1);
            }
	} else if (CMDARG_CONNECTION.equals(commandArg)) {
            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            String cxnIdStr = brokerCmdProps.getTargetName();
            Long cxnId = null;

	    try  {
		cxnId = Long.valueOf(cxnIdStr);
	    } catch (NumberFormatException nfe)  {
	        Globals.stdErrPrintln(ar.getString(ar.E_INVALID_CXN_ID, cxnIdStr));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_FAIL));
                return (1);
	    }


	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN));
	    printConnectionInfo();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_FAIL));
                return (1);
            }

    	    if (!force) {
	        input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_DESTROY_CXN_OK), noShort);
	        Globals.stdOutPrintln("");
	    }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendDestroyConnectionMessage(cxnId);
                    broker.receiveDestroyConnectionReplyMessage();
		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_SUC));

                } catch (BrokerAdminException bae)  {
	            handleBrokerAdminException(bae);

		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_FAIL));
                    return (1);
	        }
	
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_CXN_NOOP));
                return (1);
            }
        } else if (CMDARG_MSG.equals(commandArg)) {
            if (broker == null)  {
		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Destroying message failed.");
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);
	    String msgID = brokerCmdProps.getMsgID();

	    /*
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG));
	    */
            Globals.stdOutPrintln("Destroying message:");
	    printMessageInfo();

            Globals.stdOutPrintln("In the destination");
	    printDestinationInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Destroying message failed.");
                return (1);
            }

    	    if (!force) {
		/*
	        input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_DESTROY_MSG_OK), noShort);
		*/
	        input = CommonCmdRunnerUtil.getUserInput("Are you sure you want to destroy this message? (y/n)[n] ", noShort);
	        Globals.stdOutPrintln("");
	    }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendDestroyMessagesMessage(destName, destTypeMask, msgID);
		    broker.receiveDestroyMessagesReplyMessage();
		    /*
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_SUC));
		    */
                    Globals.stdOutPrintln("Successfully destroyed message.");

                } catch (BrokerAdminException bae)  {
	            handleBrokerAdminException(bae);

		    /*
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_FAIL));
		    */
                    Globals.stdErrPrintln("Destroying message failed.");
                    return (1);
	        }
	
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
		/*
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_NOOP));
		*/
	        Globals.stdOutPrintln("The message was not destroyed.\n");
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
		/*
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_DESTROY_MSG_NOOP));
		*/
	        Globals.stdOutPrintln("The message was not destroyed.\n");
                return (1);
            }
	}

        broker.close();

        return (0);
    }

    private int runPurge(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin     broker;
        String          destName;
        int             destTypeMask;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        broker = init();

    if (CMDARG_DESTINATION.equals(commandArg)) {

        if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_FAIL));
            return (1);
        }

        if (!force)
            broker = promptForAuthentication(broker);

        destName = brokerCmdProps.getTargetName();
        destTypeMask = getDestTypeMask(brokerCmdProps);

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST));
	printDestinationInfo();

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_FAIL));
            return (1);
        }

        if (!force) {
            input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PURGE_DST_OK), noShort);
            Globals.stdOutPrintln("");
        }

        if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
            try  {
                broker.sendPurgeDestinationMessage(destName, destTypeMask);
                broker.receivePurgeDestinationReplyMessage();
		Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_SUC));

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_FAIL));
                return (1);
            }

        } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_NOOP));
            return (0);

        } else {
            Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DST_NOOP));
            return (1);
        }

    } else if (CMDARG_DURABLE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            String subName = brokerCmdProps.getTargetName();
            String clientID = brokerCmdProps.getClientID();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR));
	    printDurableSubscriptionInfo();           
 
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);
            
            try {
                connectToBroker(broker);
                
            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_FAIL));
                return (1);
            }   

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_PURGE_DUR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendPurgeDurableMessage(subName, clientID);
                    broker.receivePurgeDurableReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_SUC));
                    
                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);
                    
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_FAIL));
                    return (1);
                }   
                
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_PURGE_DUR_NOOP));
                return (1);
            }
    }

        broker.close();

        return (0);
    }

    private int runPurgeAll(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin     broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;
	int		ret_code = 0;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        broker = init();

    if (CMDARG_DESTINATION.equals(commandArg)) {

        if (broker == null)  {
            Globals.stdErrPrintln("Purging all the destinations failed");
            return (1);
        }

        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln("Purging all the destinations");

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln("Purging all the destinations failed");
            return (1);
        }

        if (!force) {
            input = 
		CommonCmdRunnerUtil.getUserInput("Are you sure you want to purge all the destinations? (y/n)[n] ", noShort);
            Globals.stdOutPrintln("");
        }

        if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
            try  {
		boolean dstsPurged = false;

		/*
		 * List all destinations
		 */
                broker.sendGetDestinationsMessage(null, -1);
		Vector dests = broker.receiveGetDestinationsReplyMessage();

		if (dests != null) {
                    Enumeration thisEnum = dests.elements();

                    while (thisEnum.hasMoreElements()) {
                        DestinationInfo dInfo = (DestinationInfo)thisEnum.nextElement();
                        String          destName;
                        int             destTypeMask;

			destName = dInfo.name;
			destTypeMask = dInfo.type;

                        if (MessageType.JMQ_ADMIN_DEST.equals(destName)
		            || MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(destName) 
		            || DestType.isInternal(dInfo.fulltype)
                            || DestType.isTemporary(dInfo.type)) {

		            Globals.stdOutPrintln("Skipping destination: " + destName);
			    continue;
			}

			try  {
                            broker.sendPurgeDestinationMessage(destName, destTypeMask);
                            broker.receivePurgeDestinationReplyMessage();

	                    if (DestType.isQueue(destTypeMask)) {
		                Globals.stdOutPrintln("Successfully purged queue " + destName);
			    } else  {
		                Globals.stdOutPrintln("Successfully purged topic " + destName);
			    }
		            dstsPurged = true;
			} catch (BrokerAdminException purgeEx)  {
                            handleBrokerAdminException(purgeEx);
    
	                    if (DestType.isQueue(destTypeMask)) {
		                Globals.stdOutPrintln("Purging failed for queue " + destName);
			    } else  {
		                Globals.stdOutPrintln("Purging failed for topic " + destName);
			    }
			    ret_code = 1;
			}

                    }

		}

		if (!dstsPurged)  {
		    Globals.stdOutPrintln("No destinations purged.");
		}

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln("Purging all the destinations failed");
                return (1);
            }

        } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln("The destinations were not purged.");
            return (0);

        } else {
            Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdOutPrintln("");
	    Globals.stdOutPrintln("The destinations were not purged.");
            return (1);
        }

    }

        broker.close();

        return (ret_code);
    }

    private int runDestroyAll(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin     broker;
	String		input = null;
	String 		yes, yesShort, no, noShort;
	int		ret_code = 0;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        broker = init();

    if (CMDARG_DESTINATION.equals(commandArg)) {

        if (broker == null)  {
            Globals.stdErrPrintln("Destroying all the destinations failed");
            return (1);
        }

        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln("Destroying all the destinations");

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln("Destroying all the destinations failed");
            return (1);
        }

        if (!force) {
            input = 
		CommonCmdRunnerUtil.getUserInput("Are you sure you want to destroy all the destinations? (y/n)[n] ", noShort);
            Globals.stdOutPrintln("");
        }

        if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
            try  {
		boolean dstsDestroyed = false;

		/*
		 * List all destinations
		 */
                broker.sendGetDestinationsMessage(null, -1);
		Vector dests = broker.receiveGetDestinationsReplyMessage();

		if (dests != null) {
                    Enumeration thisEnum = dests.elements();

                    while (thisEnum.hasMoreElements()) {
                        DestinationInfo dInfo = (DestinationInfo)thisEnum.nextElement();
                        String          destName;
                        int             destTypeMask;

			destName = dInfo.name;
			destTypeMask = dInfo.type;

                        if (MessageType.JMQ_ADMIN_DEST.equals(destName)
		            || MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(destName)
		            || DestType.isInternal(dInfo.fulltype)
                            || DestType.isTemporary(dInfo.type) ||
                               DestType.isDMQ(dInfo.type)) {

		            Globals.stdOutPrintln("Skipping destination: " + destName);
			    continue;
			}

			try  {
                            broker.sendDestroyDestinationMessage(destName, destTypeMask);
                            broker.receiveDestroyDestinationReplyMessage();

	                    if (DestType.isQueue(destTypeMask)) {
		                Globals.stdOutPrintln("Successfully destroyed queue "
						+ destName);
			    } else  {
		                Globals.stdOutPrintln("Successfully destroyed topic " 
						+ destName);
			    }
		            dstsDestroyed = true;
			} catch (BrokerAdminException destroyEx)  {
                            handleBrokerAdminException(destroyEx);
    
	                    if (DestType.isQueue(destTypeMask)) {
		                Globals.stdOutPrintln("Destroy failed for queue " + destName);
			    } else  {
		                Globals.stdOutPrintln("Destroy failed for topic " + destName);
			    }
			    ret_code = 1;
			}
                    }

		}

		if (!dstsDestroyed)  {
		    Globals.stdOutPrintln("No destinations destroyed.");
		}

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln("Destroying all the destinations failed");
                return (1);
            }

        } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln("The destinations were not destroyed.");
            return (0);

        } else {
            Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdOutPrintln("");
	    Globals.stdOutPrintln("The destinations were not destroyed.");
            return (1);
        }

    }

        broker.close();

        return (ret_code);
    }

    private int runUpdate(BrokerCmdProperties brokerCmdProps) {
	BrokerAdmin	broker;
	Properties	targetAttrs;
	String		input = null;
	String 		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	broker = init();

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        if (CMDARG_BROKER.equals(commandArg)) {
	    if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR_FAIL));
	        return (1);
	    }

            if (!force)
                broker = promptForAuthentication(broker);

	    targetAttrs = brokerCmdProps.getTargetAttrs();
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR));
            Globals.stdOutPrintln("");
	    printAttrs(targetAttrs);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	    printBrokerInfo(broker);

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_UPDATE_BKR_OK), noShort);
                Globals.stdOutPrintln("");

                if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR_NOOP));
                    return (0);

                } else if (!(yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input))) {
                    Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                    Globals.stdOutPrintln("");
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR_NOOP));
                    return (1);
                }
            }

	    try {
	        connectToBroker(broker);
	
	        broker.sendUpdateBrokerPropsMessage(targetAttrs);
	        broker.receiveUpdateBrokerPropsReplyMessage();
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR_SUC));

	    } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_BKR_FAIL));
	        return (1);
	    }

        } else if (CMDARG_SERVICE.equals(commandArg)) {
	    ServiceInfo	si;
	    String svcName;

	    if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_FAIL));
	        return (1);
	    }

            if (!force)
                broker = promptForAuthentication(broker);

	    targetAttrs = brokerCmdProps.getTargetAttrs();
	    svcName = brokerCmdProps.getTargetName();
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC, svcName));
	    Globals.stdOutPrintln("");
	    printAttrs(targetAttrs);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	    printBrokerInfo(broker);

            si = getServiceInfoFromAttrs(targetAttrs);
            si.setName(svcName);

	    /*
	     * Get the svcPort value.
	     */ 
	    //int svcType = -1;
	    int svcPort = -1;

	    Vector svc = null;
	    try {
                connectToBroker(broker);

                broker.sendGetServicesMessage(svcName);
                svc = broker.receiveGetServicesReplyMessage();

                if ((svc != null) && (svc.size() == 1)) {
                    Enumeration thisEnum = svc.elements();
                    ServiceInfo sInfo = (ServiceInfo)thisEnum.nextElement();
		    //svcType = sInfo.type;
		    svcPort = sInfo.port;
	        }
	    } catch (BrokerAdminException bae) {
                handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_FAIL));
                return (1);
	    }

            if (!force) {
		/*
		 * Rollback the fix for bug 4432483: jmqcmd, jmqadmin: setting 
		 * admin max threads = 0 is allowed & hangs.
		 * Now this check is done by the broker.
                if ((si.isModified(ServiceInfo.MAX_THREADS)) && (si.maxThreads == 0)) {
		    Globals.stdErrPrintln(ar.getString(ar.W_SET_MAX_THREAD_ZERO, svcName));
		}
		*/
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_UPDATE_SVC_OK), noShort);
                Globals.stdOutPrintln("");

                if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_NOOP));
                    return (0);

                } else if (!(yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input))) {
                    Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                    Globals.stdOutPrintln("");
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_NOOP));
                    return (1);
                }
            }

            /*
             * Rollback the fix for bug 4432483: jmqcmd, jmqadmin: setting 
             * admin max threads = 0 is allowed & hangs.
            if ((si.isModified(ServiceInfo.MAX_THREADS)) && (si.maxThreads == 0) && 
		(ServiceType.ADMIN == svcType)) {
                Globals.stdErrPrintln(ar.getString(ar.E_ADMIN_MAX_THREAD));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_FAIL));
		return (1);
	    }
	    */

	    // If the port is -1, it is not used, so disallow the update.
            if ((si.isModified(ServiceInfo.PORT)) && (svcPort == -1)) {
                Globals.stdErrPrintln(ar.getString
		    (ar.E_PORT_NOT_ALLOWED_TO_CHANGE, svcName));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_FAIL));
		return (1);
	    }

	    try {
	        broker.sendUpdateServiceMessage(si);
	        broker.receiveUpdateServiceReplyMessage();
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_SUC));

	    } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_SVC_FAIL));
	        return (1);
	    }

        } else if (CMDARG_DESTINATION.equals(commandArg)) {
	    DestinationInfo	di;
	    String destName;
	    int destTypeMask;

	    if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_DEST_FAIL));
	        return (1);
	    }

            if (!force)
                broker = promptForAuthentication(broker);

	    targetAttrs = brokerCmdProps.getTargetAttrs();
	    destTypeMask = getDestTypeMask(brokerCmdProps);
	    destName = brokerCmdProps.getTargetName();
	    if (DestType.isQueue(destTypeMask)) {
                Globals.stdOutPrintln(ar.getString(
				ar.I_JMQCMD_UPDATE_DEST_Q, destName));
	    } else  {
                Globals.stdOutPrintln(ar.getString(
				ar.I_JMQCMD_UPDATE_DEST_T, destName));
	    }
	    Globals.stdOutPrintln("");
	    printAttrs(targetAttrs);

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
	    printBrokerInfo(broker);

            if (!force) {
		if (updatingDestXMLSchema(targetAttrs))  {
		    Object args[] = new Object [ 3 ];
    
		    args[0] = PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED;
		    args[1] = PROP_NAME_XML_SCHEMA_URI_LIST;
		    args[2] = PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE;
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_UPDATE_DEST_XML_SCHEMA_OK, 
					args), noShort);
		} else  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_UPDATE_DEST_OK), noShort);
		}
                Globals.stdOutPrintln("");

                if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_DEST_NOOP));
                    return (0);

                } else if (!(yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input))) {
                    Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                    Globals.stdOutPrintln("");
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_DEST_NOOP));
                    return (1);
                }
            }

	    try {
	        di = getDestinationInfoFromAttrs(targetAttrs);
	        di.setType(destTypeMask);
	        di.setName(destName);

	        connectToBroker(broker);

	        broker.sendUpdateDestinationMessage(di);
	        broker.receiveUpdateDestinationReplyMessage();
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UPDATE_DEST_SUC));

	    } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UPDATE_DEST_FAIL));
	        return (1);
	    }

	}

	broker.close();

	return (0);
    }

    private void printAttrs(Properties targetAttrs) {
        printAttrs(targetAttrs, false);
    }

    private void printAttrs(Properties targetAttrs, boolean printTitle) {
        CommonCmdRunnerUtil.printAttrs(targetAttrs, printTitle, new BrokerCmdPrinter());
    }

    private ServiceInfo getServiceInfoFromAttrs(Properties svcAttrs) {
	ServiceInfo si = new ServiceInfo();

	for (Enumeration e = svcAttrs.propertyNames();  e.hasMoreElements() ;) {
	    String propName = (String)e.nextElement(),
		   value = svcAttrs.getProperty(propName);
	    int		intValue = 0;
	    boolean	valueOK = true;
	    
	    if (propName.equals(PROP_NAME_SVC_PORT))  {
		try  {
		    intValue = Integer.parseInt(value);
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    si.setPort(intValue);
		}
		continue;
	    }

	    if (propName.equals(PROP_NAME_SVC_MIN_THREADS))  {
		try  {
		    intValue = Integer.parseInt(value);
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    si.setMinThreads(intValue);
		}
		continue;
	    }

	    if (propName.equals(PROP_NAME_SVC_MAX_THREADS))  {
		try  {
		    intValue = Integer.parseInt(value);
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    si.setMaxThreads(intValue);
		}
		continue;
	    }
	}
	
	return (si);
    }

    private boolean updatingDestXMLSchema(Properties dstAttrs) {
	String value;

	if (dstAttrs == null)  {
	    return (false);
	}

	value = dstAttrs.getProperty(PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED);
	if ((value != null) && !value.equals(""))  {
	    return (true);
	}

	value = dstAttrs.getProperty(PROP_NAME_XML_SCHEMA_URI_LIST);
	if ((value != null) && !value.equals(""))  {
	    return (true);
	}
	
	value = dstAttrs.getProperty(PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE);
	if ((value != null) && !value.equals(""))  {
	    return (true);
	}

	return (false);
    }

    private DestinationInfo getDestinationInfoFromAttrs(Properties destAttrs) {
	DestinationInfo di = new DestinationInfo();

	for (Enumeration e = destAttrs.propertyNames();  e.hasMoreElements() ;) {
	    String propName = (String)e.nextElement(),
		   value = destAttrs.getProperty(propName);
	    SizeString	ss;
	    int		intValue = 0;
	    long	longValue = 0;
	    boolean	valueOK = true;
	    
	    /*
	     * maxTotalMsgBytes
	     */
	    if (propName.equals(PROP_NAME_OPTION_MAX_MESG_BYTE))  {
		try  {
		    ss = new SizeString(value);
		    longValue = ss.getBytes();
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    di.setMaxMessageBytes(longValue);
		}
		continue;
	    }

	    /*
	     * maxNumMsgs
	     */
	    if (propName.equals(PROP_NAME_OPTION_MAX_MESG))  {
		try  {
		    intValue = Integer.parseInt(value);
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    di.setMaxMessages(intValue);
		}
		continue;
	    }

	    /*
	     * maxBytesPerMsg
	     */
	    if (propName.equals(PROP_NAME_OPTION_MAX_PER_MESG_SIZE))  {
		try  {
		    ss = new SizeString(value);
		    longValue = ss.getBytes();
		} catch (NumberFormatException nfe)  {
		    valueOK = false;
		}

		if (valueOK)  {
		    di.setMaxMessageSize(longValue);
		}
		continue;
	    }


	    /*
	     * maxFailoverConsumerCount
	     */
	    if (propName.equals(PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT))  {
		try  {
	            di.setMaxFailoverConsumers(Integer.parseInt(value));
		} catch (NumberFormatException nfe)  {
		}
	    }

	    /*
	     * maxNumBackupConsumers
	     */
	    if (propName.equals(PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT))  {
		try  {
	            di.setMaxActiveConsumers(Integer.parseInt(value));
		} catch (NumberFormatException nfe)  {
		}
	    }

	    /*
	     * isLocalDestination
	     */
	    if (propName.equals(PROP_NAME_IS_LOCAL_DEST))  {
	        di.setScope(Boolean.valueOf(value).booleanValue());
	    }

	    /*
	     * limitBehaviour
	     */
	    if (propName.equals(PROP_NAME_LIMIT_BEHAVIOUR))  {
	        di.setLimitBehavior(getLimitBehavValue(value));
	    }

	    /*
	     * localDeliveryPreferred
	     */
	    if (propName.equals(PROP_NAME_LOCAL_DELIVERY_PREF))  {
	        di.setClusterDeliveryPolicy(getClusterDeliveryPolicy(value));
	    }

	    /*
	     * maxPrefetchCount
	     */
	    if (propName.equals(PROP_NAME_CONSUMER_FLOW_LIMIT))  {
		try  {
	            di.setPrefetch(Integer.parseInt(value));
		} catch (NumberFormatException nfe)  {
		}
	    }

	    /*
	     * maxProducerCount
	     */
	    if (propName.equals(PROP_NAME_MAX_PRODUCERS))  {
		try  {
	            di.setMaxProducers(Integer.parseInt(value));
		} catch (NumberFormatException nfe)  {
		}
	    }

	    /*
	     * useDMQ
	     */
	    if (propName.equals(PROP_NAME_USE_DMQ))  {
	        di.setUseDMQ(Boolean.valueOf(value).booleanValue());
	    }

	    if (propName.equals(PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED))  {
	        di.setValidateXMLSchemaEnabled(Boolean.valueOf(value).booleanValue());
	    }

	    if (propName.equals(PROP_NAME_XML_SCHEMA_URI_LIST))  {
	        di.setXMLSchemaUriList(value);
	    }

	    if (propName.equals(PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE))  {
	        di.setReloadXMLSchemaOnFailure(Boolean.valueOf(value).booleanValue());
	    }
	}
	
	return (di);
    }


    private int runQuery(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin     broker;

        broker = init();

        // Check for the target argument.
	// Valid values are dst and svc.
        String commandArg = brokerCmdProps.getCommandArg();

        if (CMDARG_DESTINATION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_DST_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_DST));
	    printDestinationInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetDestinationsMessage(destName, destTypeMask);
                Vector dest = broker.receiveGetDestinationsReplyMessage();

                if ((dest != null) && (dest.size() == 1)) {
		    Enumeration thisEnum = dest.elements();
		    DestinationInfo dInfo = (DestinationInfo)thisEnum.nextElement();
		    BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
		    String[] row = new String[2];

		    bcp.setSortNeeded(false);

		    /*
		     * Basic info - name/type/state etc.
		     */
		    row[0] = ar.getString(ar.I_JMQCMD_DST_NAME);
		    row[1] = dInfo.name;
		    bcp.add(row);
	
		    row[0] = ar.getString(ar.I_JMQCMD_DST_TYPE);
		    row[1] = BrokerAdminUtil.getDestinationType(dInfo.type);
		    // If the destination is temporary, indicate so.
		    if (DestType.isTemporary(dInfo.type))
		        row[1] = row[1] + " (" 
					+ ar.getString(ar.I_TEMPORARY) 
					+ ")";

		    bcp.add(row);

                    row[0] = ar.getString(ar.I_JMQCMD_DST_STATE);
                    //row[1] = DestState.toString(dInfo.destState);
                    row[1] = BrokerAdminUtil.getDestinationState(dInfo.destState);
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_CREATED_ADMIN);
		    if (dInfo.autocreated)  {
                        row[1] = Boolean.FALSE.toString();
		    } else  {
                        row[1] = Boolean.TRUE.toString();
		    }
                    bcp.add(row);

                    row[0] = "";
                    row[1] = "";
                    bcp.add(row);

		    /*
		     * 'Current' numbers
		     */
                    row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_MSG);
                    row[1] = "";
                    bcp.add(row);

		    String indent = "    ";

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_ACTUAL);
                    row[1] = Integer.toString(dInfo.nMessages - dInfo.nTxnMessages);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_REMOTE);
                    row[1] = Integer.toString(dInfo.nRemoteMessages);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_INDELAY);
                    row[1] = Integer.toString(dInfo.nInDelayMessages);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_HELD_IN_TXN);
                    row[1] = Integer.toString(dInfo.nTxnMessages);
                    bcp.add(row);

                    row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_MSG_BYTES);
                    row[1] = "";
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_ACTUAL);
                    row[1] = Long.toString(dInfo.nMessageBytes - dInfo.nTxnMessageBytes);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_REMOTE);
                    row[1] = Long.toString(dInfo.nRemoteMessageBytes);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_INDELAY);
                    row[1] = Long.toString(dInfo.nInDelayMessageBytes);
                    bcp.add(row);

                    row[0] = indent + ar.getString(ar.I_JMQCMD_DST_HELD_IN_TXN);
                    row[1] = Long.toString(dInfo.nTxnMessageBytes);
                    bcp.add(row);

                    row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_PRODUCERS);
                    row[1] = Integer.toString(dInfo.nProducers);
                    bcp.add(row);

		    if (DestType.isQueue(destTypeMask)) {
                        row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_ACTIVE_CONS);
                        row[1] = Integer.toString(dInfo.naConsumers);
                        bcp.add(row);

                        row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_FAILOVER_CONS);
                        row[1] = Integer.toString(dInfo.nfConsumers);
                        bcp.add(row);
		    } else  {
			Hashtable h = dInfo.producerWildcards;

                        row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_NUM_PRODUCERS_WILDCARD);
                        row[1] = Integer.toString(getWildcardCount(h));
                        bcp.add(row);

			/*
			 * The code below will print something like:
			 *
			 *	foo.bar.* (2)
			 *	bar.* (1)
			 */
	                Enumeration keys;
			if (h != null)  {
	                    keys = h.keys();

	                    while (keys.hasMoreElements())  {
	                        String wildcard = (String)keys.nextElement();
		                Integer val = (Integer)h.get(wildcard);
	                        row[0] = indent + wildcard + "  (" + val + ")";
	                        row[1] = "";
	                        bcp.add(row);
	                    }
			}


                        row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_CONS);
                        row[1] = Integer.toString(dInfo.nConsumers);
                        bcp.add(row);

			h = dInfo.consumerWildcards;

                        row[0] = ar.getString(ar.I_JMQCMD_DST_CUR_NUM_CONSUMERS_WILDCARD);
                        row[1] = Integer.toString(getWildcardCount(h));
                        bcp.add(row);

			if (h != null)  {
	                    keys = h.keys();

	                    while (keys.hasMoreElements())  {
	                        String wildcard = (String)keys.nextElement();
		                Integer val = (Integer)h.get(wildcard);
	                        row[0] = indent + wildcard + "  (" + val + ")";
	                        row[1] = "";
	                        bcp.add(row);
	                    }
			}

		    }

                    row[0] = "";
                    row[1] = "";
                    bcp.add(row);

		    /*
		     * 'Current' numbers
		     */
		    row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_ALLOW);
		    row[1] = checkAndPrintUnlimitedInt(dInfo.maxMessages, zeroNegOneInt);
		    bcp.add(row);
		
		    row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_MSG_BYTES_ALLOW);
		    row[1] = checkAndPrintUnlimitedLong(dInfo.maxMessageBytes,
					zeroNegOneLong);
		    bcp.add(row);

		    row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_BYTES_PER_MSG_ALLOW);
		    row[1] = Long.valueOf(dInfo.maxMessageSize).toString();
		    row[1] = checkAndPrintUnlimitedLong(dInfo.maxMessageSize,
					zeroNegOneLong);
		    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_PRODUCERS);
		    row[1] = checkAndPrintUnlimitedInt(dInfo.maxProducers, -1);
                    bcp.add(row);

		    if (DestType.isQueue(destTypeMask)) {
	                row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_ACTIVE_CONSUMER_COUNT);
		        row[1] = checkAndPrintUnlimitedInt(dInfo.maxActiveConsumers, -1);
                        bcp.add(row);

	                row[0] = ar.getString(ar.I_JMQCMD_DST_MAX_FAILOVER_CONSUMER_COUNT);
		        row[1] = checkAndPrintUnlimitedInt(dInfo.maxFailoverConsumers, -1);
                        bcp.add(row);
		    }

                    row[0] = "";
                    row[1] = "";
                    bcp.add(row);

		    /*
		     * Other misc props
		     */
	            row[0] = ar.getString(ar.I_JMQCMD_DST_LIMIT_BEHAVIOUR);
                    row[1] = DestLimitBehavior.getString(dInfo.destLimitBehavior);
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_CONS_FLOW_LIMIT);
		    row[1] = checkAndPrintUnlimitedInt(dInfo.maxPrefetch, -1);
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_IS_LOCAL_DEST);
		    if (dInfo.isDestinationLocal())  {
                        row[1] = Boolean.TRUE.toString();
		    } else  {
                        row[1] = Boolean.FALSE.toString();
		    }
                    bcp.add(row);

		    if (DestType.isQueue(destTypeMask)) {
	                row[0] = ar.getString(ar.I_JMQCMD_DST_LOCAL_DELIVERY_PREF);
		        if (dInfo.destCDP == ClusterDeliveryPolicy.LOCAL_PREFERRED)  {
                            row[1] = Boolean.TRUE.toString();
		        } else  {
                            row[1] = Boolean.FALSE.toString();
		        }
                        bcp.add(row);
		    }

	            row[0] = ar.getString(ar.I_JMQCMD_DST_USE_DMQ);
		    if (dInfo.useDMQ())  {
                        row[1] = Boolean.TRUE.toString();
		    } else  {
                        row[1] = Boolean.FALSE.toString();
		    }
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_VALIDATE_XML_SCHEMA_ENABLED);
		    if (dInfo.validateXMLSchemaEnabled())  {
                        row[1] = Boolean.TRUE.toString();
		    } else  {
                        row[1] = Boolean.FALSE.toString();
		    }
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_XML_SCHEMA_URI_LIST);
                    row[1] = dInfo.XMLSchemaUriList;
                    bcp.add(row);

	            row[0] = ar.getString(ar.I_JMQCMD_DST_RELOAD_XML_SCHEMA_ON_FAILURE);
		    if (dInfo.reloadXMLSchemaOnFailure())  {
                        row[1] = Boolean.TRUE.toString();
		    } else  {
                        row[1] = Boolean.FALSE.toString();
		    }
                    bcp.add(row);

		    bcp.println();

		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_DST_SUC));
                } else  {
		    // Should not get here, since if something went wrong we should get
		    // a BrokerAdminException
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_INCORRECT_DATA_RET));
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_DST_FAIL));
                    return (1);
                }

            } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

	   	Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_DST_FAIL));
                return (1);
            }
        } else if (CMDARG_SERVICE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_SVC_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String svcName = brokerCmdProps.getTargetName();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_SVC));
            printServiceInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetServicesMessage(svcName);
                Vector svc = broker.receiveGetServicesReplyMessage();

            if ((svc != null) && (svc.size() == 1)) {
                    Enumeration thisEnum = svc.elements();
                    ServiceInfo sInfo = (ServiceInfo)thisEnum.nextElement();
                    BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
                    String[] row = new String[2];

		    bcp.setSortNeeded(false);

		    /*
		     * Basic info - name/port/state
		     */
                    row[0] = ar.getString(ar.I_JMQCMD_SVC_NAME);
                    row[1] = sInfo.name;
		    bcp.add(row);

                    row[0] = ar.getString(ar.I_JMQCMD_SVC_STATE);
                    //row[1] = ServiceState.getString(sInfo.state);
                    row[1] = BrokerAdminUtil.getServiceState(sInfo.state);
		    bcp.add(row);

		    // ONLY display port number if it is applicable
		    // It is NOT applicable if it is set to -1
		    if (sInfo.port != -1) {
                        row[0] = ar.getString(ar.I_JMQCMD_SVC_PORT);

                        // Add more information about the port number: 
                        // dynamically generated or statically declared
		        if (sInfo.dynamicPort) {
			    switch (sInfo.state) {
			        case ServiceState.UNKNOWN:
			            row[1] = ar.getString(ar.I_DYNAMIC);
			        break;
			        default:
                                    row[1] = Integer.toString(sInfo.port)
					     + 
 					         " (" 
				             + 
					         ar.getString(ar.I_DYNAMIC) 
					     + 
					         ")";
			    }
		        } else {
                    	    row[1] = Integer.toString(sInfo.port) +
				     " (" + ar.getString(ar.I_STATIC) + ")";
		        }
		        bcp.add(row);
		    }
		
                    row[0] = "";
                    row[1] = "";
		    bcp.add(row);

		    /*
		     * 'Curent' numbers
		     */
                    row[0] = ar.getString(ar.I_JMQCMD_SVC_CUR_THREADS);
                    row[1] = Integer.toString(sInfo.currentThreads);
		    bcp.add(row);
		
                    row[0] = ar.getString(ar.I_JMQCMD_SVC_NUM_CXN);
                    row[1] = Integer.toString(sInfo.nConnections);
		    bcp.add(row);

                    row[0] = "";
                    row[1] = "";
		    bcp.add(row);

		    /*
		     * Min/Max numbers
		     */
                    row[0] = ar.getString(ar.I_JMQCMD_SVC_MIN_THREADS);
                    row[1] = Integer.toString(sInfo.minThreads);
		    bcp.add(row);
		
                    row[0] = ar.getString(ar.I_JMQCMD_SVC_MAX_THREADS);
                    row[1] = Integer.toString(sInfo.maxThreads);
		    bcp.add(row);

		
		    bcp.println();		
		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_SVC_SUC));

                } else  {
                    // Should not get here, since if something went wrong we should get
                    // a BrokerAdminException
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_INCORRECT_DATA_RET));
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_SVC_FAIL));
                    return (1);
                }

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_SVC_FAIL));
                return (1);
            }
        } else if (CMDARG_BROKER.equals(commandArg)) {
            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetBrokerPropsMessage();
                Properties bkrProps = broker.receiveGetBrokerPropsReplyMessage();

                if (bkrProps == null) {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_FAIL));
                    return (1);
		}

		if (brokerCmdProps.adminDebugModeSet())  {
		    printAllBrokerAttrs(bkrProps);
		} else  {
		    printDisplayableBrokerAttrs(bkrProps);
		}

		Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_SUC));

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_FAIL));
                return (1);
            }
        } else if (CMDARG_TRANSACTION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String tidStr = brokerCmdProps.getTargetName();
            Long tid = null;

	    try  {
		tid = Long.valueOf(tidStr);
	    } catch (NumberFormatException nfe)  {
	        Globals.stdErrPrintln(ar.getString(ar.E_INVALID_TXN_ID, tidStr));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN_FAIL));
                return (1);
	    }

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN));
	    printTransactionInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetTxnsMessage(tid, brokerCmdProps.showPartitionModeSet());
                Vector txns = broker.receiveGetTxnsReplyMessage();

                if ((txns != null) && (txns.size() == 1)) {
		    Enumeration thisEnum = txns.elements();
		    Hashtable txnInfo = (Hashtable)thisEnum.nextElement();

		    if (brokerCmdProps.debugModeSet())  {
		        printAllTxnAttrs(txnInfo);
		    } else  {
		        printDisplayableTxnAttrs(txnInfo);
		    }

		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN_SUC));

                } else  {
		    // Should not get here, since if something went wrong we should get
		    // a BrokerAdminException
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_INCORRECT_DATA_RET));
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN_FAIL));
                    return (1);
                }

            } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

	   	Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_TXN_FAIL));
                return (1);
            }
        } else if (CMDARG_CONNECTION.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String cxnIdStr = brokerCmdProps.getTargetName();
            Long cxnId = null;

	    try  {
		cxnId = Long.valueOf(cxnIdStr);
	    } catch (NumberFormatException nfe)  {
	        Globals.stdErrPrintln(ar.getString(ar.E_INVALID_CXN_ID, cxnIdStr));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN_FAIL));
                return (1);
	    }

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN));
	    printConnectionInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetConnectionsMessage(null, cxnId);
		Vector cxnList = broker.receiveGetConnectionsReplyMessage();

                if ((cxnList != null) && (cxnList.size() == 1)) {
		    Enumeration thisEnum = cxnList.elements();
		    Hashtable cxnInfo = (Hashtable)thisEnum.nextElement();

		    if (brokerCmdProps.debugModeSet())  {
		        printAllCxnAttrs(cxnInfo);
		    } else  {
		        printDisplayableCxnAttrs(cxnInfo);
		    }

		    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN_SUC));

                } else  {
		    // Should not get here, since if something went wrong we should get
		    // a BrokerAdminException
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_INCORRECT_DATA_RET));
		    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN_FAIL));
                    return (1);
                }

            } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

	   	Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_CXN_FAIL));
                return (1);
            }
        } else if (CMDARG_MSG.equals(commandArg)) {
            if (broker == null)  {
		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Querying message failed.");
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);
	    String msgID = brokerCmdProps.getMsgID();

	    /*
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_MSG));
	    */
            Globals.stdOutPrintln("Querying message:");
	    printMessageInfo();

            Globals.stdOutPrintln("In the destination");
	    printDestinationInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
                connectToBroker(broker);

                broker.sendGetMessagesMessage(destName, destTypeMask, true, msgID, 
						null, null);
		Vector msgList = broker.receiveGetMessagesReplyMessage();

		if ((msgList != null) && (msgList.size() == 1)) {
		    HashMap oneMsg = (HashMap)msgList.get(0);

                    printDisplayableMsgAttrs(oneMsg);

		    /*
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_MSG_SUC));
		    */
                    Globals.stdOutPrintln("Successfully queried message.");

                } else  {
		    /*
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_LIST_MSG_NONE));
		    */
                    Globals.stdErrPrintln("There are no messages.");

                    Globals.stdOutPrintln("");
		    /*
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUERY_MSG_SUC));
		    */
                    Globals.stdOutPrintln("Successfully queried message.");
                }
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

		/*
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_MSG_FAIL));
		*/
                Globals.stdErrPrintln("Querying message failed.");
                return (1);
            }
	}

	if (broker.isConnected())  {
            broker.close();
	}

        return (0);
    }

    private int runMetrics(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin		broker;
	BrokerCmdPrinter	bcp;
        String			commandArg;
	String			titleRow[];
	long			sleepTime;
	int			metricType,
				metricSamples;

        broker = init();

        commandArg = brokerCmdProps.getCommandArg();

	sleepTime = brokerCmdProps.getMetricInterval();

	metricType = getMetricType(brokerCmdProps);
	metricSamples = brokerCmdProps.getMetricSamples();


        if (CMDARG_SERVICE.equals(commandArg)) {
	    bcp = setupMetricTitle(commandArg, metricType);

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_SVC_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String svcName = brokerCmdProps.getTargetName();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_SVC));
            printServiceInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
		MetricCounters	previousMetrics = null;
		int	rowsPrinted = 0;

                connectToBroker(broker);

		while (true)  {
                    broker.sendGetMetricsMessage(svcName);
                    MetricCounters mc = (MetricCounters)broker.receiveGetMetricsReplyMessage();

                    if (mc == null) {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_SVC_FAIL));
                        return (1);
		    }

		    addOneMetricRow(metricType, bcp, mc, previousMetrics);
    
		    if ((rowsPrinted % 20) == 0)  {
		        bcp.print();
		    } else  {
		        bcp.print(false);
		    }

		    bcp.clear();
		    previousMetrics = mc;
		    rowsPrinted++;

		    if (metricSamples > 0)  {
			if (metricSamples == rowsPrinted)  {
			    break;
			}
		    }

		    try  {
		        Thread.sleep(sleepTime * 1000);
                    } catch (InterruptedException ie)  {
		        Globals.stdErrPrintln(ie.toString());
		    }
		}

                Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_SVC_SUC));

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_SVC_FAIL));
                return (1);
            }
        } else if (CMDARG_BROKER.equals(commandArg)) {
            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_BKR_FAIL));
                return (1);
            }

	    bcp = setupMetricTitle(commandArg, metricType);

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_BKR));
            printBrokerInfo(broker);

            try  {
		MetricCounters	previousMetrics = null;
		int	rowsPrinted = 0;

                connectToBroker(broker);

		while (true)  {
                    broker.sendGetMetricsMessage(null);
                    MetricCounters mc = (MetricCounters)broker.receiveGetMetricsReplyMessage();


                    if (mc == null) {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_BKR_FAIL));
                        return (1);
		    }

		    addOneMetricRow(metricType, bcp, mc, previousMetrics);

		    if ((rowsPrinted % 20) == 0)  {
		        bcp.print();
		    } else  {
		        bcp.print(false);
		    }

		    bcp.clear();
		    previousMetrics = mc;
		    rowsPrinted++;

		    if (metricSamples > 0)  {
			if (metricSamples == rowsPrinted)  {
			    break;
			}
		    }

		    try  {
		        Thread.sleep(sleepTime * 1000);
                    } catch (InterruptedException ie)  {
		        Globals.stdErrPrintln(ie.toString());
		    }

		}

                Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_BKR_SUC));

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_BKR_FAIL));
                return (1);
	    }
	} else if (CMDARG_DESTINATION.equals(commandArg)) {
	    String destName;
	    int destTypeMask;

	    destName = brokerCmdProps.getTargetName();
	    destTypeMask = getDestTypeMask(brokerCmdProps);

	    bcp = setupDestMetricTitle(commandArg, metricType, destTypeMask);
            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_DST_FAIL));
                return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_DST));
	    printDestinationInfo();

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
            printBrokerInfo(broker);

            try  {
		DestMetricsCounters	previousMetrics = null;
		int	rowsPrinted = 0;

                connectToBroker(broker);

		while (true)  {
                    broker.sendGetMetricsMessage(destName, destTypeMask);
                    DestMetricsCounters mc 
			= (DestMetricsCounters)broker.receiveGetMetricsReplyMessage();

                    if (mc == null) {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_DST_FAIL));
                        return (1);
		    }

		    addOneDestMetricRow(metricType, destTypeMask, bcp, mc, 
				previousMetrics);

		    if ((rowsPrinted % 20) == 0)  {
		        bcp.print();
		    } else  {
		        bcp.print(false);
		    }

		    bcp.clear();
		    previousMetrics = mc;
		    rowsPrinted++;

		    if (metricSamples > 0)  {
			if (metricSamples == rowsPrinted)  {
			    break;
			}
		    }

		    try  {
		        Thread.sleep(sleepTime * 1000);
                    } catch (InterruptedException ie)  {
		        Globals.stdErrPrintln(ie.toString());
		    }

		}

                Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_METRICS_DST_SUC));

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_METRICS_DST_FAIL));
                return (1);
	    }

	}

	if (broker.isConnected())  {
            broker.close();
	}


        return (0);
    }

    private BrokerCmdPrinter setupListDestTitle(int listType)  {
        BrokerCmdPrinter bcp = null;

	if (listType != LIST_QUEUE)  {
            bcp = new BrokerCmdPrinter(12, 2, "-");
            String[] row = new String[12];
	    int span[], i = 0;

	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

	    span = new int [ 12 ];

	    span[i++] = 1;
	    span[i++] = 1;
	    span[i++] = 1;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 5;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 0;

	    i = 0;
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NAME_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_TYPE_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_STATE_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NUM_PRODUCER);
	    row[i++] = "";
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NUM_CONSUMER);
	    row[i++] = "";
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS);
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
            bcp.addTitle(row, span);

	    i = 0;
            row[i++] = "";
            row[i++] = "";
            row[i++] = "";
            row[i++] = ar.getString(ar.I_JMQCMD_DST_PRODUCERS_TOTAL);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_WILDCARD);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_CONSUMERS_TOTAL);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_WILDCARD);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_TOTAL_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_REMOTE);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_UNACK_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_INDELAY_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_AVG_SIZE);
            bcp.addTitle(row);
	} else  {
            bcp = new BrokerCmdPrinter(11, 2, "-");
            String[] row = new String[11];
	    int span[], i = 0;

	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

	    span = new int [ 11 ];

	    span[i++] = 1;
	    span[i++] = 1;
	    span[i++] = 1;
	    span[i++] = 1;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 5;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 0;

	    i = 0;
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NAME_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_TYPE_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_STATE_SHORT);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NUM_PRODUCER);
            row[i++] = ar.getString(ar.I_JMQCMD_DST_NUM_CONSUMER);
	    row[i++] = "";
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS);
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
            bcp.addTitle(row, span);

	    i = 0;
            row[i++] = "";
            row[i++] = "";
            row[i++] = "";
            row[i++] = "";
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_CONSUMERS_ACTIVE);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_CONSUMERS_BACKUP);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_TOTAL_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_REMOTE);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_UNACK_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_INDELAY_COUNT);
	    row[i++] = ar.getString(ar.I_JMQCMD_DST_MSGS_AVG_SIZE);
            bcp.addTitle(row);
	}

	return(bcp);
    }

    private BrokerCmdPrinter setupMetricTitle(String commandArg, int metricType)  {
	String			titleRow[];
        BrokerCmdPrinter	bcp = null;

	if (metricType == METRICS_TOTALS)  {
	    int i = 0, span[];

	    span = new int [ 8 ];

	    bcp = new BrokerCmdPrinter(8, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	    titleRow = new String[8];

	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	
	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSG_BYTES);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_PKTS);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_PKT_BYTES);
	    titleRow[i++] = "";
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    bcp.addTitle(titleRow);
	} else if (metricType == METRICS_RATES)  {
	    int i = 0, span[];

	    span = new int [ 8 ];

	    bcp = new BrokerCmdPrinter(8, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	    titleRow = new String[8];

	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	
	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_PER_SEC);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSG_BYTES_PER_SEC);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_PKTS_PER_SEC);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_PKT_BYTES_PER_SEC);
	    titleRow[i++] = "";
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    bcp.addTitle(titleRow);
	} else if (metricType == METRICS_CONNECTIONS) {
	    int i = 0, span[];

	    titleRow = new String[6];
	    span = new int [ 6 ];

	    bcp = new BrokerCmdPrinter(6, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

	    span[i++] = 1;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_CON_NUM_CON1);
	    titleRow[i++] = ar.getString(ar.I_METRICS_JVM_HEAP_BYTES);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_THREADS);
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_CON_NUM_CON2);
	    titleRow[i++] = ar.getString(ar.I_METRICS_TOTAL);
	    titleRow[i++] = ar.getString(ar.I_METRICS_FREE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_ACTIVE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_LOW);
	    titleRow[i++] = ar.getString(ar.I_METRICS_HIGH);
	    bcp.addTitle(titleRow);
	}
	
	return (bcp);
    }


    private BrokerCmdPrinter setupDestMetricTitle(String commandArg, int metricType,
					int destTypeMask)  {
	String			titleRow[];
        BrokerCmdPrinter	bcp = null;

	if (metricType == METRICS_TOTALS)  {
	    bcp = new BrokerCmdPrinter(11, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	    titleRow = new String[11];
	    int i, span[] = new int[ 11 ];

	    i = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 1;

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSG_BYTES);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_COUNT);
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_BYTES);
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_LARGEST1);
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	    titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	    titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_LARGEST2);
	    bcp.addTitle(titleRow);
	} else if (metricType == METRICS_RATES)  {
	    bcp = new BrokerCmdPrinter(11, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	    titleRow = new String[11];
	    int i, span[] = new int[ 11 ];

	    i = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 2;
	    span[i++] = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;
	    span[i++] = 1;

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_PER_SEC);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSG_BYTES_PER_SEC);
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_COUNT);
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_BYTES);
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_LARGEST1);
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_IN);
	    titleRow[i++] = ar.getString(ar.I_METRICS_OUT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	    titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	    titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	    titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	    titleRow[i++] = ar.getString(ar.I_METRICS_DST_MSGS_LARGEST2);
	    bcp.addTitle(titleRow);
	} else if (metricType == METRICS_CONSUMER) {
	    if (DestType.isQueue(destTypeMask)) {
	        bcp = new BrokerCmdPrinter(9, 2, "-", BrokerCmdPrinter.CENTER);
	        bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	        titleRow = new String[9];
	        int i, span[] = new int[ 9 ];

	        i = 0;
	        span[i++] = 3;
	        span[i++] = 0;
	        span[i++] = 0;
	        span[i++] = 3;
	        span[i++] = 0;
	        span[i++] = 0;
	        span[i++] = 3;
	        span[i++] = 0;
	        span[i++] = 0;

	        i = 0;
	        titleRow[i++] = ar.getString(ar.I_METRICS_DST_CON_ACTIVE_CONSUMERS);
	        titleRow[i++] = "";
	        titleRow[i++] = "";
	        titleRow[i++] = ar.getString(ar.I_METRICS_DST_CON_BACKUP_CONSUMERS);
	        titleRow[i++] = "";
	        titleRow[i++] = "";
	        titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_COUNT);
	        titleRow[i++] = "";
	        titleRow[i++] = "";
	        bcp.addTitle(titleRow, span);

	        i = 0;
	        titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	        titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	        titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	        titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	        titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	        titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	        titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	        titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	        titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	        bcp.addTitle(titleRow);
	    } else  {
	        bcp = new BrokerCmdPrinter(6, 2, "-", BrokerCmdPrinter.CENTER);
	        bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	        titleRow = new String[6];
	        int i, span[] = new int[ 6 ];

	        i = 0;
	        span[i++] = 3;
	        span[i++] = 0;
	        span[i++] = 0;
	        span[i++] = 3;
	        span[i++] = 0;
	        span[i++] = 0;

	        i = 0;
	        titleRow[i++] = ar.getString(ar.I_METRICS_DST_CON_CONSUMERS);
	        titleRow[i++] = "";
	        titleRow[i++] = "";
	        titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_COUNT);
	        titleRow[i++] = "";
	        titleRow[i++] = "";
	        bcp.addTitle(titleRow, span);

	        i = 0;
	        titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	        titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	        titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	        titleRow[i++] = ar.getString(ar.I_METRICS_CURRENT);
	        titleRow[i++] = ar.getString(ar.I_METRICS_PEAK);
	        titleRow[i++] = ar.getString(ar.I_METRICS_AVERAGE);
	        bcp.addTitle(titleRow);
	    }
	} else if (metricType == METRICS_DISK) {
	    bcp = new BrokerCmdPrinter(3, 2, "-", BrokerCmdPrinter.CENTER);
	    titleRow = new String[3];

	    titleRow[0] = ar.getString(ar.I_METRICS_DSK_RESERVED);
	    titleRow[1] = ar.getString(ar.I_METRICS_DSK_USED);
	    titleRow[2] = ar.getString(ar.I_METRICS_DSK_UTIL_RATIO);
	    bcp.addTitle(titleRow);
	} else if (metricType == METRICS_REMOVE) {
	    bcp = new BrokerCmdPrinter(3, 2, "-", BrokerCmdPrinter.CENTER);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
	    titleRow = new String[3];
	    int i, span[] = new int[ 3 ];

	    i = 0;
	    span[i++] = 3;
	    span[i++] = 0;
	    span[i++] = 0;

	    i = 0;
	    /*
	    titleRow[i++] = ar.getString(ar.I_METRICS_MSGS_REMOVED);
	    */
	    titleRow[i++] = "Msgs Removed";
	    titleRow[i++] = "";
	    titleRow[i++] = "";
	    bcp.addTitle(titleRow, span);

	    i = 0;
	    /*
	    titleRow[i++] = ar.getString(ar.I_METRICS_EXPIRED);
	    titleRow[i++] = ar.getString(ar.I_METRICS_DISCARDED);
	    titleRow[i++] = ar.getString(ar.I_METRICS_PURGED);
	    */
	    titleRow[i++] = "Expired";
	    titleRow[i++] = "Discarded";
	    titleRow[i++] = "Purged";
	    bcp.addTitle(titleRow);
	}
	
	return (bcp);
    }

    private void addOneMetricRow(int metricType, BrokerCmdPrinter bcp,
		MetricCounters latest, 
		MetricCounters previous)  {
	String	metricRow[];

	if (metricType == METRICS_TOTALS)  {
	    metricRow = new String[8];

	    metricRow[0] = Long.toString(latest.messagesIn);
	    metricRow[1] = Long.toString(latest.messagesOut);
	    metricRow[2] = Long.toString(latest.messageBytesIn);
	    metricRow[3] = Long.toString(latest.messageBytesOut);
	    metricRow[4] = Long.toString(latest.packetsIn);
	    metricRow[5] = Long.toString(latest.packetsOut);
	    metricRow[6] = Long.toString(latest.packetBytesIn);
	    metricRow[7] = Long.toString(latest.packetBytesOut);

	    bcp.add(metricRow);
	} else if (metricType == METRICS_RATES)  {
	    metricRow = new String[8];

	    if (previous == null)  {
	        metricRow[0] = "0";
	        metricRow[1] = "0";
	        metricRow[2] = "0";
	        metricRow[3] = "0";
	        metricRow[4] = "0";
	        metricRow[5] = "0";
	        metricRow[6] = "0";
	        metricRow[7] = "0";
	    } else  {
	        float	secs;

	        secs = (float)(latest.timeStamp - previous.timeStamp)/(float)1000;

                metricRow[0] = CommonCmdRunnerUtil.getRateString(latest.messagesIn, 
					previous.messagesIn, secs);

                metricRow[1] = CommonCmdRunnerUtil.getRateString(latest.messagesOut, 
					previous.messagesOut, secs);

                metricRow[2] = CommonCmdRunnerUtil.getRateString(latest.messageBytesIn, 
					previous.messageBytesIn, secs);

                metricRow[3] = CommonCmdRunnerUtil.getRateString(latest.messageBytesOut, 
					previous.messageBytesOut, secs);

                metricRow[4] = CommonCmdRunnerUtil.getRateString(latest.packetsIn, 
					previous.packetsIn, secs);

                metricRow[5] = CommonCmdRunnerUtil.getRateString(latest.packetsOut, 
					previous.packetsOut, secs);

                metricRow[6] = CommonCmdRunnerUtil.getRateString(latest.packetBytesIn, 
					previous.packetBytesIn, secs);

                metricRow[7] = CommonCmdRunnerUtil.getRateString(latest.packetBytesOut, 
					previous.packetBytesOut, secs);
	    }

            bcp.add(metricRow);
    
        } else if (metricType == METRICS_CONNECTIONS) {
	    metricRow = new String[6];
	    metricRow[0] = Integer.toString(latest.nConnections);
	    metricRow[1] = Long.toString(latest.totalMemory);
	    metricRow[2] = Long.toString(latest.freeMemory);
	    metricRow[3] = Integer.toString(latest.threadsActive);
	    metricRow[4] = Integer.toString(latest.threadsLowWater);
	    metricRow[5] = Integer.toString(latest.threadsHighWater);
	    bcp.add(metricRow);
        }
    }


    private void addOneDestMetricRow(int metricType, int destTypeMask,
		BrokerCmdPrinter bcp,
		DestMetricsCounters latestDest, 
		DestMetricsCounters previousDest)  {
	String	metricRow[];

	if (metricType == METRICS_TOTALS)  {
	    metricRow = new String[11];

	    metricRow[0] = Long.toString(latestDest.getMessagesIn());
	    metricRow[1] = Long.toString(latestDest.getMessagesOut());
	    metricRow[2] = Long.toString(latestDest.getMessageBytesIn());
	    metricRow[3] = Long.toString(latestDest.getMessageBytesOut());

	    metricRow[4] = Integer.toString(latestDest.getCurrentMessages());
	    metricRow[5] = Integer.toString(latestDest.getHighWaterMessages());
	    metricRow[6] = Integer.toString(latestDest.getAverageMessages());

	    metricRow[7] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getCurrentMessageBytes());
	    metricRow[8] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getHighWaterMessageBytes());
	    metricRow[9] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getAverageMessageBytes());
	    metricRow[10] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getHighWaterLargestMsgBytes());

	    bcp.add(metricRow);
	} else if (metricType == METRICS_RATES)  {
	    metricRow = new String[11];

	    if (previousDest == null)  {
	        metricRow[0] = "0";
	        metricRow[1] = "0";
	        metricRow[2] = "0";
	        metricRow[3] = "0";
	    } else  {
	        float	secs;

	        secs = (float)(latestDest.timeStamp - previousDest.timeStamp)/(float)1000;

                metricRow[0] = CommonCmdRunnerUtil.getRateString(latestDest.getMessagesIn(),
					previousDest.getMessagesIn(), secs);

                metricRow[1] = CommonCmdRunnerUtil.getRateString(latestDest.getMessagesOut(),
					previousDest.getMessagesOut(), secs);

                metricRow[2] = CommonCmdRunnerUtil.getRateString(latestDest.getMessageBytesIn(),
					previousDest.getMessageBytesIn(), secs);

                metricRow[3] = CommonCmdRunnerUtil.getRateString(latestDest.getMessageBytesOut(),
					previousDest.getMessageBytesOut(), secs);
	    }

	    metricRow[4] = Integer.toString(latestDest.getCurrentMessages());
	    metricRow[5] = Integer.toString(latestDest.getHighWaterMessages());
	    metricRow[6] = Integer.toString(latestDest.getAverageMessages());

	    metricRow[7] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getCurrentMessageBytes());
	    metricRow[8] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getHighWaterMessageBytes());
	    metricRow[9] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getAverageMessageBytes());
	    metricRow[10] = CommonCmdRunnerUtil.displayInKBytes(latestDest.getHighWaterLargestMsgBytes());

            bcp.add(metricRow);
        } else if (metricType == METRICS_CONSUMER) {
	    if (DestType.isQueue(destTypeMask)) {
	        metricRow = new String[9];

	        metricRow[0] = Integer.toString(latestDest.getActiveConsumers());
	        metricRow[1] = Integer.toString(latestDest.getHWActiveConsumers());
	        metricRow[2] = Integer.toString(latestDest.getAvgActiveConsumers());
	        metricRow[3] = Integer.toString(latestDest.getFailoverConsumers());
	        metricRow[4] = Integer.toString(latestDest.getHWFailoverConsumers());
	        metricRow[5] = Integer.toString(latestDest.getAvgFailoverConsumers());
	        metricRow[6] = Integer.toString(latestDest.getCurrentMessages());
	        metricRow[7] = Integer.toString(latestDest.getHighWaterMessages());
	        metricRow[8] = Integer.toString(latestDest.getAverageMessages());

	        bcp.add(metricRow);
	    } else  {
	        metricRow = new String[6];

	        metricRow[0] = Integer.toString(latestDest.getActiveConsumers());
	        metricRow[1] = Integer.toString(latestDest.getHWActiveConsumers());
	        metricRow[2] = Integer.toString(latestDest.getAvgActiveConsumers());
	        metricRow[3] = Integer.toString(latestDest.getCurrentMessages());
	        metricRow[4] = Integer.toString(latestDest.getHighWaterMessages());
	        metricRow[5] = Integer.toString(latestDest.getAverageMessages());

	        bcp.add(metricRow);
	    }
        } else if (metricType == METRICS_DISK) {
	    metricRow = new String[3];

	    metricRow[0] = Long.toString(latestDest.getDiskReserved());
	    metricRow[1] = Long.toString(latestDest.getDiskUsed());
	    metricRow[2] = Integer.toString(latestDest.getDiskUtilizationRatio());

	    bcp.add(metricRow);
        } else if (metricType == METRICS_REMOVE) {
	    metricRow = new String[3];

	    /*
	    metricRow[0] = Long.toString(latestDest.getMsgsExpired());
	    metricRow[1] = Long.toString(latestDest.getMsgsDiscarded());
	    metricRow[2] = Long.toString(latestDest.getMsgsPurged());
	    */
	    metricRow[0] = "0";
	    metricRow[1] = "0";
	    metricRow[2] = "0";

	    bcp.add(metricRow);
        }

    }

    private int runReload(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin		broker;
	BrokerCmdPrinter	bcp;
        String			commandArg;
	String			titleRow[];
	long			sleepTime;

	broker = init();

	if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RELOAD_CLS_FAIL));
	    return (1);
	}

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RELOAD_CLS));
        printBrokerInfo(broker);

        try {
            connectToBroker(broker);

	    broker.sendReloadClusterMessage();
	    broker.receiveReloadClusterReplyMessage();

	    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_RELOAD_CLS_SUC));

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_RELOAD_CLS_FAIL));
            return (1);
        }

	return (0);
    }

    private int runChangeMaster(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;
        BrokerCmdPrinter bcp;
        String commandArg;
	    String yes = ar.getString(ar.Q_RESPONSE_YES);
	    String yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	    String no = ar.getString(ar.Q_RESPONSE_NO);
	    String noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	    broker = init();

        if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_CLS_FAIL));
            return (1);
        }

        boolean force = brokerCmdProps.forceModeSet();
        if (!force) {
            broker = promptForAuthentication(broker);
        }

        Properties targetAttrs = brokerCmdProps.getTargetAttrs();
        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_CLS));
        Globals.stdOutPrintln("");
        printAttrs(targetAttrs);
        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);

        if (!force) {
            String input = CommonCmdRunnerUtil.getUserInput(
                               ar.getString(ar.Q_CHANGEMASTER_OK), noShort);
            Globals.stdOutPrintln("");

            if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_NOOP));
                return (0);

            } else if (!(yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input))) {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_NOOP));
                return (1);
            }
        }

        try {
            connectToBroker(broker);
            broker.sendClusterChangeMasterMessage(targetAttrs);
            broker.receiveClusterChangeMasterReplyMessage();
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_CLS_SUC));

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_CHANGEMASTER_CLS_FAIL));
            return (1);
        }
        broker.close();
	    return (0);
    }

    private int runCommit(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin		broker;
	BrokerCmdPrinter	bcp;
        String			commandArg;
	String			titleRow[];
	String			tidStr;
	Long			tid = null;
	String 			yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	broker = init();

	if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_FAIL));
	    return (1);
	}

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN));
	printTransactionInfo();

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);


        tidStr = brokerCmdProps.getTargetName();

	try  {
	    tid = Long.valueOf(tidStr);
	} catch (NumberFormatException nfe)  {
	    Globals.stdErrPrintln(ar.getString(ar.E_INVALID_TXN_ID, tidStr));
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_FAIL));
            return (1);
	}

        try {
            connectToBroker(broker);

	    /*
	     *  Prompt user for confirmation.
	     */
            String input = null;
            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_COMMIT_TXN_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
	            broker.sendCommitTxnMessage(tid);
	            broker.receiveCommitTxnReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_SUC));
                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_FAIL));
                    return (1);
                }
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_NOOP));
                return (0);
                    
            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_NOOP));
                return (1);
            }

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMMIT_TXN_FAIL));
            return (1);
        }

	return (0);
    }

    private int runRollback(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin		broker;
	BrokerCmdPrinter	bcp;
        String			commandArg;
	String			titleRow[];
	String			tidStr;
	Long			tid = null;
	String 			yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	broker = init();

	if (broker == null)  {
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_FAIL));
	    return (1);
	}

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN));
	printTransactionInfo();

        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
        printBrokerInfo(broker);

        tidStr = brokerCmdProps.getTargetName();

	try  {
	    tid = Long.valueOf(tidStr);
	} catch (NumberFormatException nfe)  {
	    Globals.stdErrPrintln(ar.getString(ar.E_INVALID_TXN_ID, tidStr));
            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_FAIL));
            return (1);
	}

        try {
	    Hashtable txnInfo = null;

            connectToBroker(broker);

	    /*
	     * Obtain/query transaction info to check it's state.
	     */
            broker.sendGetTxnsMessage(tid, brokerCmdProps.showPartitionModeSet());
            Vector txns = broker.receiveGetTxnsReplyMessage();

            if ((txns != null) && (txns.size() == 1)) {
	        Enumeration thisEnum = txns.elements();
	        txnInfo = (Hashtable)thisEnum.nextElement();

	        if (brokerCmdProps.debugModeSet())  {
	            printAllTxnAttrs(txnInfo);
	        }
            } else  {
                // Should not get here, since if something went wrong we should get
                // a BrokerAdminException
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_INCORRECT_DATA_RET));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_FAIL));
                return (1);
            }

	    /*
	     * Get the transaction's state. 
	     */
	    Integer tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_STATE);
	    String txnState = getTxnStateString(tmpInt);

	    /*
	     *  Prompt user for confirmation. Show transaction's state.
	     */
            String input = null;
            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_ROLLBACK_TXN_OK, txnState), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendRollbackTxnMessage(tid, brokerCmdProps.msgOptionSet());
                    broker.receiveRollbackTxnReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_SUC));
                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_FAIL));
                    return (1);
                }
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_NOOP));
                return (0);
                    
            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_NOOP));
                return (1);
            }
        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);

            Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_ROLLBACK_TXN_FAIL));
            return (1);
        }

	return (0);
    }

    private int runCompact(BrokerCmdProperties brokerCmdProps)  {
        BrokerAdmin     broker;
        String          destName;
        int             destTypeMask;
	String		input = null;
	String 		yes, yesShort, no, noShort;
	boolean		compactAll = true;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        String commandArg = brokerCmdProps.getCommandArg();
        boolean force = brokerCmdProps.forceModeSet();

        broker = init();

        if (CMDARG_DESTINATION.equals(commandArg)) {
            destName = brokerCmdProps.getTargetName();
            destTypeMask = getDestTypeMask(brokerCmdProps);

	    if (destName != null)  {
		compactAll = false;
	    }

            if (broker == null)  {
		if (compactAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_FAIL));
		}
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);


	    if (compactAll)  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS));
                printBrokerInfo(broker);
	    } else  {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST));
	        printDestinationInfo();

                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_SPECIFY_BKR));
                printBrokerInfo(broker);
	    }

            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);

		if (compactAll)  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_FAIL));
		} else  {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_FAIL));
		}
                return (1);
            }

            if (!force) {
		if (compactAll)  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_COMPACT_DSTS_OK), noShort);
		} else  {
                    input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_COMPACT_DST_OK), noShort);
		}
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) 
	       || yes.equalsIgnoreCase(input) 
	       || force) {
                try  {
                    broker.sendCompactDestinationMessage(destName, destTypeMask);
                    broker.receiveCompactDestinationReplyMessage();

		    if (compactAll)  {
		        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_SUC));
		    } else  {
		        Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_SUC));
		    }

                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);

		    if (compactAll)  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_FAIL));
		    } else  {
                        Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_FAIL));
		    }
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
		if (compactAll)  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_NOOP));
		} else  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_NOOP));
		}
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
		if (compactAll)  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DSTS_NOOP));
		} else  {
	            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_COMPACT_DST_NOOP));
		}
                return (1);
            }
        } 

        broker.close();

        return (0);
    }

    private int runQuiesce(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR));
            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_QUIESCE_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendQuiesceMessage();
	            broker.receiveQuiesceReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_QUIESCE_BKR_NOOP));
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    private int runUnquiesce(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR));
            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_FAIL));
                return (1);
            }

            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_UNQUIESCE_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendUnquiesceMessage();
	            broker.receiveUnquiesceReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_UNQUIESCE_BKR_NOOP));
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    private int runTakeover(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin 	broker;
	String		input = null;
	String		yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);
	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

	// Check for the target argument
	String commandArg = brokerCmdProps.getCommandArg();

	if (CMDARG_BROKER.equals(commandArg)) {
            String brokerID = brokerCmdProps.getTargetName();

            if (broker == null)  {
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                return (1);
            }

            if (!force)
                broker = promptForAuthentication(broker);

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR));

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_BKR_PERFORMING_TAKEOVER));

            printBrokerInfo(broker);

	    try {
		connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                return (1);
            }

	    try {
		/*
		 * Get broker props to find out if broker is in HA cluster and 
		 * broker cluster ID
		 */
                broker.sendGetBrokerPropsMessage();
                Properties bkrProps = broker.receiveGetBrokerPropsReplyMessage();

		/*
		 * Check if cluster is HA or not
		 */
		String value1 = bkrProps.getProperty(PROP_NAME_BKR_CLS_HA);
		String value2 = bkrProps.getProperty(PROP_NAME_BKR_STORE_MIGRATABLE);
	        if (!Boolean.valueOf(value1).booleanValue() && !Boolean.valueOf(value2).booleanValue())  {
                    Globals.stdErrPrintln(ar.getString(ar.E_BROKER_NO_TAKEOVER_SUPPORT));
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                    return (1);
		}
            } catch (BrokerAdminException bae)  {
		handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_FAIL));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                return (1);
            }

            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_BKR_STORE_TAKEOVER));

	    BrokerCmdPrinter bcp = new BrokerCmdPrinter(5, 3, "-");
	    String[] row = new String[5];

	    bcp.setSortNeeded(false);
	    bcp.setTitleAlign(BrokerCmdPrinter.CENTER);

	    int i = 0;
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = "";
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP1);

	    bcp.addTitle(row);
	
	    i = 0;
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_ID);
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_ADDRESS);
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_STATE);
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_NUM_MSGS);
	    row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP2);

	    bcp.addTitle(row);

	    /*
	     * Get state of each broker in cluster
	     */
	    Vector bkrList = null;
            try  {
                broker.sendGetClusterMessage(true);
	        bkrList = broker.receiveGetClusterReplyMessage();
            } catch (BrokerAdminException bae)  {
	        handleBrokerAdminException(bae);

                Globals.stdErrPrintln(ar.getString(ar.E_FAILED_TO_OBTAIN_CLUSTER_INFO));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                return (1);
            }

	    String brokerIDFromList = null;
	    boolean found = false;

            Enumeration thisEnum = bkrList.elements();
            while (thisEnum.hasMoreElements()) {
                Hashtable bkrClsInfo = (Hashtable)thisEnum.nextElement();
                Long tmpLong;
                Integer tmpInt;
                long idle;

		brokerIDFromList = (String)bkrClsInfo.get(BrokerClusterInfo.ID);

		if ((brokerIDFromList == null) || 
		    (!brokerIDFromList.equals(brokerID)))  {
		    continue;
		}

                found = true;

                i = 0;

                row[i++] = checkNullAndPrint(brokerIDFromList);

                row[i++] = checkNullAndPrint(
				bkrClsInfo.get(BrokerClusterInfo.ADDRESS));

                tmpInt = (Integer)bkrClsInfo.get(BrokerClusterInfo.STATE);
                if (tmpInt != null)  {
                    row[i++] = BrokerState.toString(tmpInt.intValue());
                } else  {
                    row[i++] = "";
                }

                tmpLong = (Long)bkrClsInfo.get(BrokerClusterInfo.NUM_MSGS);
                row[i++] = checkNullAndPrint(tmpLong);

                tmpLong = (Long)bkrClsInfo.get(
                            BrokerClusterInfo.STATUS_TIMESTAMP);
                if (tmpLong != null)  {
                    idle = System.currentTimeMillis() - tmpLong.longValue();
                    row[i++] = CommonCmdRunnerUtil.getTimeString(idle);
                } else  {
                    row[i++] = "";
                }

                bcp.add(row);

                /*
                 * Only need to display info on the one desired broker
                 */
                break;
            }

	    if (!found)  {
                Globals.stdErrPrintln(ar.getString(ar.E_CANNOT_FIND_BROKERID, brokerID));
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                return (1);
	    }

	    bcp.println();


            if (!force) {
                input = CommonCmdRunnerUtil.getUserInput(ar.getString(ar.Q_TAKEOVER_BKR_OK), noShort);
                Globals.stdOutPrintln("");
            }

            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try  {
                    broker.sendTakeoverMessage(brokerID);
	            broker.receiveTakeoverReplyMessage();
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_SUC));

                } catch (BrokerAdminException bae)  {
		    handleBrokerAdminException(bae);

                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_FAIL));
                    return (1);
                }

            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_NOOP));
                return (0);

            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_TAKEOVER_BKR_NOOP));
                return (1);
            }

	}

        broker.close();

        return (0);
    }

    private int runMigrateStore(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;
        String input = null;
        String yes, yesShort, no, noShort;

        yes = ar.getString(ar.Q_RESPONSE_YES);
        yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
        no = ar.getString(ar.Q_RESPONSE_NO);
        noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);
	
        broker = init();

        boolean force = brokerCmdProps.forceModeSet();

        // Check for the target argument
        String commandArg = brokerCmdProps.getCommandArg();
        Properties targetAttrs = brokerCmdProps.getTargetAttrs();
        String partition = targetAttrs.getProperty(
                               BrokerCmdOptions.PROP_NAME_OPTION_PARTITION);

        if (CMDARG_BROKER.equals(commandArg)) {
            String brokerID = brokerCmdProps.getTargetName();
            if (brokerID == null && partition != null) {
                Globals.stdErrPrintln(ar.getString(ar.E_MIGRATE_PARTITION_NO_TARGET_BROKER, partition));
                return (1);
            }
            if (broker == null)  {
                if (partition == null) { 
                    Globals.stdErrPrintln(ar.getString(
                        ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                } else {
                    Globals.stdErrPrintln(ar.getString(
                        ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                }
                return (1);
            }
            if (!force) {
                broker = promptForAuthentication(broker);
            }
            if (partition == null) {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR));
            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATE_PARTITION, partition));
            }
            printBrokerInfo(broker);
            try {
                connectToBroker(broker);

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                if (partition == null) {
                    Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                } else {
                    Globals.stdErrPrintln(ar.getString(
                        ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                }
                return (1);
            }
            //boolean isHA = false;
            try {
                /*
                 * Get broker props to find out if broker is in HA/BDBREP cluster and 
                 * broker cluster ID
                 */
                broker.sendGetBrokerPropsMessage();
                Properties bkrProps = broker.receiveGetBrokerPropsReplyMessage();
                /*
                 * Check if cluster is HA or not
                 */
                //String value1 = bkrProps.getProperty(PROP_NAME_BKR_CLS_HA);
                String value2 = bkrProps.getProperty(PROP_NAME_BKR_STORE_MIGRATABLE);
                String value3 = bkrProps.getProperty(PROP_NAME_BKR_PARTITION_MIGRATABLE);
                //isHA = Boolean.valueOf(value1).booleanValue();
                if (partition == null && !Boolean.valueOf(value2).booleanValue())  {
                     Globals.stdErrPrintln(ar.getString(
                       ar.E_BROKER_NO_STORE_MIGRATION_SUPPORT));
                     Globals.stdErrPrintln(ar.getString(
                       ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                    return (1);
                }
                if (partition != null && !Boolean.valueOf(value3).booleanValue())  {
                     Globals.stdErrPrintln(ar.getString(ar.E_MIGRATE_PARTITION_NO_SUPPORT));
                     Globals.stdErrPrintln(ar.getString(
                       ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                     return (1);
                }
            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                Globals.stdErrPrintln(ar.getString(ar.I_JMQCMD_QUERY_BKR_FAIL));
                if (partition == null) {
                    Globals.stdErrPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                } else {
                    Globals.stdErrPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                }
                return (1);
            }

            if (brokerID != null) {

            if (partition == null) {
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_TO));
            } else {
            Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATE_PARTITION_TO, partition));
            }
            int numcolums = 4;
            if (partition != null) {
                numcolums = 5; 
            }
            BrokerCmdPrinter bcp = new BrokerCmdPrinter(numcolums, 3, "-");
            String[] row = new String[numcolums];
            bcp.setSortNeeded(false);
            bcp.setTitleAlign(BrokerCmdPrinter.CENTER);
            int i = 0;
            row[i++] = "";
            row[i++] = "";
            row[i++] = "";
            row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP1);
            if (partition != null) {
                row[i++] = "";
            }
            bcp.addTitle(row);

            i = 0;
            row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_ID);
            row[i++] = ar.getString(ar.I_JMQCMD_CLS_ADDRESS);
            row[i++] = ar.getString(ar.I_JMQCMD_CLS_BROKER_STATE);
            row[i++] = ar.getString(ar.I_JMQCMD_CLS_TIME_SINCE_TIMESTAMP2);
            if (partition != null) {
                row[i++] = ar.getString(ar.I_JMQCMD_NUM_PARTITION);
            }
            bcp.addTitle(row);

            /*
             * Get state of each broker in cluster
             */
            Vector bkrList = null;
            try {
                broker.sendGetClusterMessage(true);
                bkrList = broker.receiveGetClusterReplyMessage();
            } catch (BrokerAdminException bae)  {
                 handleBrokerAdminException(bae);
                 Globals.stdErrPrintln(ar.getString(ar.E_FAILED_TO_OBTAIN_CLUSTER_INFO));
                 if (partition == null) {
                     Globals.stdErrPrintln(ar.getString(
                       ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                 } else {
                    Globals.stdErrPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                 }
                 return (1);
            }
            String brokerIDFromList = null;
            boolean found = false;
            Enumeration thisEnum = bkrList.elements();
            while (thisEnum.hasMoreElements()) {
                Hashtable bkrClsInfo = (Hashtable)thisEnum.nextElement();
                Long tmpLong;
                Integer tmpInt;
                long idle;
                brokerIDFromList = (String)bkrClsInfo.get(BrokerClusterInfo.ID);
                if ((brokerIDFromList == null)) { 
                    continue;
                }
                if ((!brokerIDFromList.equals(brokerID)))  {
                    continue;
                }
                found = true;
                i = 0;
                row[i++] = checkNullAndPrint(brokerIDFromList);
                row[i++] = checkNullAndPrint(bkrClsInfo.get(BrokerClusterInfo.ADDRESS));
                tmpInt = (Integer)bkrClsInfo.get(BrokerClusterInfo.STATE);
                if (tmpInt != null)  {
                    row[i++] = BrokerState.toString(tmpInt.intValue());
                } else  {
                    row[i++] = "";
                }
                //tmpLong = (Long)bkrClsInfo.get(BrokerClusterInfo.NUM_MSGS);
                //row[i++] = checkNullAndPrint(tmpLong);
                tmpLong = (Long)bkrClsInfo.get(BrokerClusterInfo.STATUS_TIMESTAMP);
                if (tmpLong != null)  {
                    idle = System.currentTimeMillis() - tmpLong.longValue();
                    row[i++] = CommonCmdRunnerUtil.getTimeString(idle);
                } else  {
                    row[i++] = "";
                }
                if (partition != null) {
                    tmpInt = (Integer)bkrClsInfo.get(MessageType.JMQ_NUM_PARTITIONS); 
                    if (tmpInt != null)  {
                        row[i++] = String.valueOf(tmpInt.intValue());
                    } else  {
                        row[i++] = "";
                    }
                }
                bcp.add(row);
                break;
            }
            if (!found)  {
                Globals.stdErrPrintln(ar.getString(
                  ar.E_CANNOT_FIND_BROKERID, brokerID));
                if (partition == null) {
                    Globals.stdErrPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_NOT_MIGRATED));
                } else {
                    Globals.stdErrPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_NOT_MIGRATED, partition, brokerID));
                }
                return (1);
            }
            bcp.println();
            }

            if (!force) {
                if (partition == null) {
                    input = CommonCmdRunnerUtil.getUserInput(
                              ar.getString(ar.Q_MIGRATESTORE_BKR_OK), noShort);
                } else {
                    input = CommonCmdRunnerUtil.getUserInput(
                      ar.getString(ar.Q_MIGRATE_PARTITION_OK, partition, brokerID), noShort);
                }
                Globals.stdOutPrintln("");
            }
            if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) || force) {
                try {
                    broker.sendMigrateStoreMessage(brokerID, partition);
                    String tobroker = broker.receiveMigrateStoreReplyMessage();
                    if (partition == null) {
                        Globals.stdOutPrintln(ar.getString(
                          ar.I_JMQCMD_MIGRATESTORE_BKR_SUC, tobroker));
                    } else {
                        Globals.stdOutPrintln(ar.getString(
                          ar.I_JMQCMD_MIGRATE_PARTITION_SUC, partition, tobroker));
                    }
                } catch (BrokerAdminException bae)  {
                    handleBrokerAdminException(bae);
                    int status = bae.getReplyStatus();
                    Message msg = bae.getReplyMsg();
                    String bk = null, hp = null;
                    try {
                        bk = msg.getStringProperty(MessageType.JMQ_BROKER_ID);
                        hp = msg.getStringProperty(MessageType.JMQ_MQ_ADDRESS);
                    } catch (Exception e) {}
                    if (bk != null) {
                        bk = bk + (hp == null ? "":"["+hp+"]");
                    } else {
                        bk = "";
                    }
                    String st = null;
                    if (status >= 0) {
                        st = Status.getString(status);
                    }
                    if (st == null) {
                        if (partition == null) {
                            Globals.stdErrPrintln(
                              ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL, bk));
                        } else {
                            Globals.stdErrPrintln(
                              ar.getString(ar.I_JMQCMD_MIGRATE_PARTITION_FAIL, partition, brokerID));
                        }
                    } else { 
                        if (partition == null) {
                            Globals.stdErrPrintln(
                              ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_FAIL_STATUS, bk, st));
                        } else {
                            Object[] args = { partition, brokerID, st };
                            Globals.stdErrPrintln(
                              ar.getString(ar.I_JMQCMD_MIGRATE_PARTITION_FAIL_STATUS, args));
                        }
                    }
                    return (1);
                }
            } else if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
                if (partition == null) {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_NOOP));
                } else {
                    Globals.stdOutPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATE_PARTITION_NOOP, partition, brokerID));
                }
                return (0);
            } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                if (partition == null) {
                    Globals.stdOutPrintln(ar.getString(ar.I_JMQCMD_MIGRATESTORE_BKR_NOOP));
                } else {
                    Globals.stdOutPrintln(ar.getString(
                      ar.I_JMQCMD_MIGRATE_PARTITION_NOOP, partition, brokerID));
                }
                return (1);
            }
	    }

        /** broker will shutdown itself
         * broker.close();
         */
        return (0);
    }


    private int runExists(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;
        int retValue = 1;

        broker = init();

        // Check for the target argument.
        // Valid value is dst only.
        String commandArg = brokerCmdProps.getCommandArg();

        if (CMDARG_DESTINATION.equals(commandArg)) {

            if (broker == null)  {
		Globals.stdOutPrintln("Problems connecting to the broker.");
		return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);

            try {
                connectToBroker(broker);

                broker.sendGetDestinationsMessage(destName, destTypeMask);
                Vector dest = broker.receiveGetDestinationsReplyMessage();

                if ((dest != null) && (dest.size() == 1)) {
		    Globals.stdOutPrintln(Boolean.TRUE.toString());
		    retValue = 0;

                } else {
                    // Should not get here, since if something went wrong we should get
                    // a BrokerAdminException
	 	    Globals.stdErrPrintln("Problems retrieving the destination info.");
		    return (1);
                }

            } catch (BrokerAdminException bae) {
		// com.sun.messaging.jmq.io.Status.java: 404 ==  not found
		if (bae.getReplyStatus() == 404) {
		    Globals.stdOutPrintln(Boolean.FALSE.toString());
		    retValue = 0;
		} else {
		    handleBrokerAdminException(bae);
		    return (1);
		}
 	    }
	}
	return (retValue);
    }

    /**
     * This method is used by tests only
     */
    public List runGetAttrWithReturnResult(BrokerCmdProperties brokerCmdProps) {
        List result = new ArrayList();
        int ret = runGetAttr(brokerCmdProps, result);
        if (ret != 0 || result.size() == 0) {
            return null;
        }
        return result;
    }

    private int runGetAttr(BrokerCmdProperties brokerCmdProps) {
        return runGetAttr(brokerCmdProps, null);
    }

    private int runGetAttr(BrokerCmdProperties brokerCmdProps, List result) {
        BrokerAdmin broker;
        int retValue = 1;

        broker = init();
        try {

        // Check for the target argument.
        // Valid value are dst, svc, and bkr.
        String commandArg = brokerCmdProps.getCommandArg();

        if (CMDARG_DESTINATION.equals(commandArg)) {

            if (broker == null)  {
		Globals.stdOutPrintln("Problems connecting to the broker.");
                retValue = 1;
		return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String destName = brokerCmdProps.getTargetName();
            int destTypeMask = getDestTypeMask(brokerCmdProps);
	    String attrName = brokerCmdProps.getSingleTargetAttr();

            try {
                connectToBroker(broker);

                broker.sendGetDestinationsMessage(destName, destTypeMask);
                Vector dest = broker.receiveGetDestinationsReplyMessage();

                if ((dest != null) && (dest.size() == 1)) {
                    Enumeration thisEnum = dest.elements();
                    DestinationInfo dInfo = (DestinationInfo)thisEnum.nextElement();

		    if (PROP_NAME_OPTION_MAX_MESG_BYTE.equals(attrName)) {
	                Globals.stdOutPrintln(Long.toString(dInfo.maxMessageBytes));
			retValue = 0;

		    } else if (PROP_NAME_OPTION_MAX_MESG.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.maxMessages));
			retValue = 0;

		    } else if (PROP_NAME_OPTION_MAX_PER_MESG_SIZE.equals(attrName)) {
	                Globals.stdOutPrintln(Long.toString(dInfo.maxMessageSize));
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_MESG_BYTE.equals(attrName)) {
	                Globals.stdOutPrintln(Long.toString(dInfo.nMessageBytes));
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_MESG.equals(attrName)) {
                        String val = Integer.toString(dInfo.nMessages);
	                Globals.stdOutPrintln(val);
                        if (result != null) {
                            result.add(val);
                        }
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_UNACK_MESG.equals(attrName)) {
                        String val = Integer.toString(dInfo.nUnackMessages);
	                Globals.stdOutPrintln(val);
                        if (result != null) {
                            result.add(val);
                        }
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_PRODUCERS.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.nProducers));
			retValue = 0;

		    } else if (PROP_NAME_MAX_FAILOVER_CONSUMER_COUNT.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.maxFailoverConsumers));
			retValue = 0;

		    } else if (PROP_NAME_MAX_ACTIVE_CONSUMER_COUNT.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.maxActiveConsumers));
			retValue = 0;

		    } else if (PROP_NAME_IS_LOCAL_DEST.equals(attrName)) {
			if (dInfo.isDestinationLocal())  {
	                    Globals.stdOutPrintln(Boolean.TRUE.toString());
			} else  {
	                    Globals.stdOutPrintln(Boolean.FALSE.toString());
			}
			retValue = 0;

		    } else if (PROP_NAME_LIMIT_BEHAVIOUR.equals(attrName)) {
	                Globals.stdOutPrintln(DestLimitBehavior.getString(dInfo.destLimitBehavior));
			retValue = 0;

		    } else if (PROP_NAME_LOCAL_DELIVERY_PREF.equals(attrName)) {
			int cdp = dInfo.destCDP;

			if (cdp == ClusterDeliveryPolicy.LOCAL_PREFERRED)  {
	                    Globals.stdOutPrintln(Boolean.TRUE.toString());
			} else  {
	                    Globals.stdOutPrintln(Boolean.FALSE.toString());
			}
			retValue = 0;

		    } else if (PROP_NAME_CONSUMER_FLOW_LIMIT.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.maxPrefetch));
			retValue = 0;

		    } else if (PROP_NAME_MAX_PRODUCERS.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.maxProducers));
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_A_CONSUMERS.equals(attrName)) {
                        String val = null;
			if (DestType.isQueue(destTypeMask)) {
                            val = Integer.toString(dInfo.naConsumers);
	                    Globals.stdOutPrintln(val);
			} else  {
                            val = Integer.toString(dInfo.nConsumers);
	                    Globals.stdOutPrintln(val);
			}
                        if (result != null) {
                            result.add(val);
                        }
			retValue = 0;

		    } else if (PROP_NAME_OPTION_CUR_B_CONSUMERS.equals(attrName)) {
	                Globals.stdOutPrintln(Integer.toString(dInfo.nfConsumers));
			retValue = 0;

		    } else if (PROP_NAME_USE_DMQ.equals(attrName)) {
	                Globals.stdOutPrintln(Boolean.toString(dInfo.useDMQ()));
			retValue = 0;

		    } else if (PROP_NAME_VALIDATE_XML_SCHEMA_ENABLED.equals(attrName)) {
	                Globals.stdOutPrintln(Boolean.toString(dInfo.validateXMLSchemaEnabled()));
			retValue = 0;

		    } else if (PROP_NAME_XML_SCHEMA_URI_LIST.equals(attrName)) {
	                Globals.stdOutPrintln(dInfo.XMLSchemaUriList);
			retValue = 0;

		    } else if (PROP_NAME_RELOAD_XML_SCHEMA_ON_FAILURE.equals(attrName)) {
	                Globals.stdOutPrintln(Boolean.toString(dInfo.reloadXMLSchemaOnFailure()));
			retValue = 0;

		    } else {
			// Should not get here since we check for valid attribute
			// names in BrokerCmd.checkGetAttr().
	                Globals.stdErrPrintln(attrName + " is not recognized.");
                        retValue = 1;
			return (1); 
		    }
                } else {
	 	    Globals.stdErrPrintln("Problems retrieving the destination info.");
                    retValue = 1;
		    return (1);
		}

            } catch (BrokerAdminException bae) {
                handleBrokerAdminException(bae);
                retValue = 1;
		return (1);
	    }
	    return (retValue);

        } else if (CMDARG_SERVICE.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdOutPrintln("Problems connecting to the broker.");
		return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String svcName = brokerCmdProps.getTargetName();
            String attrName = brokerCmdProps.getSingleTargetAttr();

            try  {
                connectToBroker(broker);

                broker.sendGetServicesMessage(svcName);
                Vector svc = broker.receiveGetServicesReplyMessage();
                if ((svc != null) && (svc.size() == 1)) {
                    Enumeration thisEnum = svc.elements();
                    ServiceInfo sInfo = (ServiceInfo)thisEnum.nextElement();

                    if (BrokerCmdOptions.PROP_NAME_SVC_PORT.equals(attrName)) {
                        Globals.stdOutPrintln(Integer.toString(sInfo.port));
                        retValue = 0;

                    } else if (BrokerCmdOptions.PROP_NAME_SVC_MIN_THREADS.
			equals(attrName)) {
                        Globals.stdOutPrintln(Integer.toString(sInfo.minThreads));
                        retValue = 0;

                    } else if (BrokerCmdOptions.PROP_NAME_SVC_MAX_THREADS.
                        equals(attrName)) {
                        Globals.stdOutPrintln(Integer.toString(sInfo.maxThreads));
                        retValue = 0;

                    } else {
                        // Should not get here since we check for valid attribute
                        // names in BrokerCmd.checkGetAttr().
                        Globals.stdOutPrintln(attrName + " is not recognized.");
                        retValue = 1;
                        return (1);
		    }
                } else {
	 	    Globals.stdOutPrintln("Problems retrieving the service info.");
                    retValue = 1;
		    return (1);
		}

            } catch (BrokerAdminException bae) {
                 handleBrokerAdminException(bae);
                 retValue = 1;
		 return (1);
	    }
	    return (retValue);

        } else if (CMDARG_BROKER.equals(commandArg)) {

            if (broker == null)  {
                Globals.stdOutPrintln("Problems connecting to the broker.");
                retValue = 1;
		return (1);
            }

            boolean force = brokerCmdProps.forceModeSet();
            if (!force)
                broker = promptForAuthentication(broker);

            String attrName = brokerCmdProps.getSingleTargetAttr();

            try  {
                connectToBroker(broker);

                broker.sendGetBrokerPropsMessage();
                Properties bkrProps = broker.receiveGetBrokerPropsReplyMessage();

                if (bkrProps == null) {
	 	    Globals.stdOutPrintln("Problems retrieving the broker info.");
                    retValue = 1;
		    return (1);
                }

		String value;

                value = bkrProps.getProperty(attrName, "");
                Globals.stdOutPrintln(value);
		retValue = 0;

		/*
                if (PROP_NAME_BKR_PRIMARY_PORT.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_PRIMARY_PORT, "");
                    Globals.stdOutPrintln(value);
		    retValue = 0;

		} else if (PROP_NAME_BKR_AUTOCREATE_TOPIC.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_TOPIC, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

                } else if (PROP_NAME_BKR_AUTOCREATE_QUEUE.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE, "");
                    Globals.stdOutPrintln(value);
		    retValue = 0;

                } else if (PROP_NAME_BKR_MAX_MSG.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

                } else if (PROP_NAME_BKR_MAX_TTL_MSG_BYTES.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_MAX_TTL_MSG_BYTES, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

                } else if (PROP_NAME_BKR_MAX_MSG_BYTES.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG_BYTES, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

                } else if (PROP_NAME_BKR_CUR_MSG.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_CUR_MSG, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

                } else if (PROP_NAME_BKR_CUR_TTL_MSG_BYTES.equals(attrName)) {
                    value = bkrProps.getProperty(PROP_NAME_BKR_CUR_TTL_MSG_BYTES, "");
                    Globals.stdOutPrintln(value);
                    retValue = 0;

		} else {
                    // Should not get here since we check for valid attribute
                    // names in BrokerCmd.checkGetAttr().
                    Globals.stdOutPrintln(attrName + " is not recognized.");
                    retValue = 1;
                    return (1);
		}
		*/

            } catch (BrokerAdminException bae)  {
                handleBrokerAdminException(bae);
                retValue = 1;
		return (1);
            }

        } else if (CMDARG_DURABLE.equals(commandArg)) {

            String destName = brokerCmdProps.getDestName();
            String subName = brokerCmdProps.getTargetName();
            String clientID = brokerCmdProps.getClientID();
	    String attrName = brokerCmdProps.getSingleTargetAttr();
            if (!BrokerCmdOptions.PROP_NAME_OPTION_CUR_A_CONSUMERS.equals(attrName)) {
                return 1;
            }

            if (broker == null)  {
		Globals.stdOutPrintln("Problems connecting to the broker.");
                retValue = 1;
		return (1);
            }
            boolean force = brokerCmdProps.forceModeSet();
            if (!force) {
                broker = promptForAuthentication(broker);
            }

            try {
                connectToBroker(broker);
                if (destName != null) {
                    isDestTypeTopic(broker, destName);
                }

                broker.sendGetDurablesMessage(destName, null);
            	Vector durs = broker.receiveGetDurablesReplyMessage();
                retValue = 1;
                Enumeration thisEnum = durs.elements();
                while (thisEnum.hasMoreElements()) {
                    DurableInfo dinfo = (DurableInfo)thisEnum.nextElement();
                    if (subName != null) {
                        if (dinfo.name.equals(subName)) {
                            String val = null;
                            if (clientID != null && clientID.equals(dinfo.clientID)) {
                                val = Integer.toString(dinfo.activeCount);
                                Globals.stdOutPrintln(val);
                            } else {
                                val = Integer.toString(dinfo.activeCount);
                                Globals.stdOutPrintln(val);
                            }
                            if (result != null) {
                                result.add(val);
                            }
                            return 0;
                        }
                    } else if (destName != null && dinfo.consumer != null &&
                               destName.equals(dinfo.consumer.destination)) {
                        if (clientID != null && clientID.equals(dinfo.clientID)) {
                            Globals.stdOutPrintln(Integer.toString(dinfo.activeCount));
                            return 0;
                        } else {
                            Globals.stdOutPrintln(Integer.toString(dinfo.activeCount));
                            return 0;
                        }
                    }
                }
                Globals.stdErrPrintln("Subscription not found.");
                return 1;
            } catch (BrokerAdminException bae) {
		handleBrokerAdminException(bae);
		return (1);
            }
        } else if (CMDARG_TRANSACTION.equals(commandArg)) {

	    String attrName = brokerCmdProps.getSingleTargetAttr();

            if (broker == null)  {
		Globals.stdOutPrintln("Problems connecting to the broker.");
                retValue = 1;
		return (1);
            }
            boolean force = brokerCmdProps.forceModeSet();
            if (!force) {
                broker = promptForAuthentication(broker);
            }

            try {
                connectToBroker(broker);
                broker.sendGetTxnsMessage(brokerCmdProps.showPartitionModeSet());
                Vector txns = broker.receiveGetTxnsReplyMessage();
                if (txns == null) {
                    throw new BrokerAdminException(BrokerAdminException.REPLY_NOT_RECEIVED);
                }
                if (BrokerCmdOptions.PROP_NAME_OPTION_CUR_TXNS.equals(attrName)) {
                    String val = String.valueOf(txns.size());
                    Globals.stdOutPrintln(val);
                    if (result != null) {
                        result.add(val);
                    }
                    retValue = 0;
                } else if (BrokerCmdOptions.PROP_NAME_OPTION_ALL_TXNS.equals(attrName)) {
                    Globals.stdOutPrintln(txns.toString());
                    if (result != null) {
                        result.addAll(txns);
                    }
                    retValue = 0;
                }
            } catch (BrokerAdminException bae) {
		handleBrokerAdminException(bae);
		return (1);
            }
        }
        return (retValue);

        } finally {
        if (broker != null) {
            if (retValue == 1) { 
                broker.forceClose();
            } else {
                broker.close();
            }
        }
        }
    }

    private int runUngracefulKill(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;

        broker = init();

        if (broker == null)  {
            Globals.stdOutPrintln("Problems connecting to the broker.");
            return (1);
        }

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

        try {
            connectToBroker(broker);
            broker.sendShutdownMessage(false, true);
            Globals.stdOutPrintln("Ungracefully shutdown the broker.");
	    return (0);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);
            return (1);
        }
    }

    private int runDebug(BrokerCmdProperties brokerCmdProps) {
        BrokerAdmin broker;
	BrokerCmdPrinter bcp;
        Hashtable	debugHash = null;
	String		cmd, cmdarg, target;
	String		targetType;
	Properties	optionalProps = null;

        broker = init();

        if (broker == null)  {
            Globals.stdOutPrintln("Problems connecting to the broker.");
            return (1);
        }

        boolean force = brokerCmdProps.forceModeSet();
        if (!force)
            broker = promptForAuthentication(broker);

	cmd = brokerCmdProps.getCommand();
        cmdarg = brokerCmdProps.getCommandArg();
	target = brokerCmdProps.getTargetName();
	/*
	 * The -t option is used to specify target type
	 */
	targetType = brokerCmdProps.getDestType();
	optionalProps = brokerCmdProps.getTargetAttrs();

        Globals.stdOutPrintln("Sending the following DEBUG message:");

	bcp = new BrokerCmdPrinter(2, 4, "-", BrokerCmdPrinter.LEFT, false);
	String[] row = new String[2];
	row[0] = "Header Property Name";
	row[1] = "Value";
	bcp.addTitle(row);
	row[0] = MessageType.JMQ_CMD;
	row[1] = cmd;
	bcp.add(row);
	row[0] = MessageType.JMQ_CMDARG;
	row[1] = cmdarg;
	bcp.add(row);
	if (target != null)  {
	    row[0] = MessageType.JMQ_TARGET;
	    row[1] = target;
	    bcp.add(row);
	}
	if (targetType != null)  {
	    row[0] = MessageType.JMQ_TARGET_TYPE;
	    row[1] = targetType;
	    bcp.add(row);
	}
	bcp.println();

	if ((optionalProps != null) && (optionalProps.size() > 0))  {
            Globals.stdOutPrintln("Optional properties:");
	    printAttrs(optionalProps, true);
	}

	Globals.stdOutPrintln("To the broker specified by:");
	printBrokerInfo(broker);

        try {
            connectToBroker(broker);
            broker.sendDebugMessage(cmd, cmdarg, target, targetType, optionalProps);
            debugHash = broker.receiveDebugReplyMessage();

	    if ((debugHash != null) && (debugHash.size() > 0))  {
	        Globals.stdOutPrintln("Data received back from broker:");
	        CommonCmdRunnerUtil.printDebugHash(debugHash);
	    } else  {
	        Globals.stdOutPrintln("No additional data received back from broker.\n");
	    }

	    Globals.stdOutPrintln("DEBUG message sent successfully.");

	    return (0);

        } catch (BrokerAdminException bae)  {
            handleBrokerAdminException(bae);
            return (1);
        }
    }


    private void printAllBrokerAttrs(Properties bkrProps)  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];

	for (Enumeration e = bkrProps.propertyNames() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();

	    row[0] = curPropName;
	    row[1] = bkrProps.getProperty(curPropName, "");
	    bcp.add(row);
        }
	bcp.println();		
    }

    private void printDisplayableBrokerAttrs(Properties bkrProps)  {
	BrokerCmdPrinter	bcp = new BrokerCmdPrinter(2, 4);
	String[]		row = new String[2];
	String			value;

	bcp.setSortNeeded(false);

	/*
	 * Basic info - version/instance/port
	 */
	row[0] = ar.getString(ar.I_BKR_VERSION_STR);
	value = bkrProps.getProperty(PROP_NAME_BKR_PRODUCT_VERSION, "");
	if (value.equals(""))  {
	    value = ar.getString(ar.I_BKR_VERSION_NOT_AVAILABLE);
	}
	row[1] = value;
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_BKR_INSTANCE_NAME);
	value = bkrProps.getProperty(PROP_NAME_BKR_INSTANCE_NAME, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_BROKER_ID);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_BROKER_ID, "");
	row[1] = value;
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_JMQCMD_PRIMARY_PORT);
	value = bkrProps.getProperty(PROP_NAME_BKR_PRIMARY_PORT, "");
	row[1] = value;
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_BKR_IS_EMBEDDED);
	value = bkrProps.getProperty(PROP_NAME_BKR_IS_EMBEDDED, "");
	row[1] = value;
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CONFIG_DATA_ROOT_DIR);
	value = bkrProps.getProperty(PROP_NAME_BKR_VARHOME, "");
	row[1] = value;
	bcp.add(row);
		
	/*
	row[0] = ar.getString(ar.I_JMQCMD_LICENSE);
	value = bkrProps.getProperty(PROP_NAME_BKR_LICENSE_DESC, "");
	row[1] = value;
	bcp.add(row);
	*/
		
	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * 'Current' numbers
	 */
        row[0] = ar.getString(ar.I_CUR_MSGS_IN_BROKER);
        value = bkrProps.getProperty(PROP_NAME_BKR_CUR_MSG, "");
        row[1] = value;
        bcp.add(row);

        row[0] = ar.getString(ar.I_CUR_BYTES_IN_BROKER);
        value = bkrProps.getProperty(PROP_NAME_BKR_CUR_TTL_MSG_BYTES, "");
        row[1] = value;
        bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * 'Current' numbers for DMQ
	 * Log Dead Msgs
	 */
	row[0] = ar.getString(ar.I_CUR_MSGS_IN_DMQ);
	value = bkrProps.getProperty(PROP_NAME_DMQ_CUR_MSG, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CUR_BYTES_IN_DMQ);
	value = bkrProps.getProperty(PROP_NAME_DMQ_CUR_TTL_MSG_BYTES, "");
	row[1] = value;
	bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	row[0] = ar.getString(ar.I_BKR_LOG_DEAD_MSGS);
	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_DEAD_MSGS, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_BKR_DMQ_TRUNCATE_MSG_BODY);
	value = bkrProps.getProperty(PROP_NAME_BKR_DMQ_TRUNCATE_MSG_BODY, "");
	row[1] = value;
	bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * Max numbers
	 */
	row[0] = ar.getString(ar.I_MAX_MSGS_IN_BROKER);
	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG, "");
	row[1] = checkAndPrintUnlimited(value, zeroNegOneString);
	bcp.add(row);

	row[0] = ar.getString(ar.I_MAX_BYTES_IN_BROKER);
	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_TTL_MSG_BYTES, "");
	row[1] = checkAndPrintUnlimitedBytes(value, zeroNegOneLong);
	bcp.add(row);

	row[0] = ar.getString(ar.I_MAX_MSG_SIZE);
	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG_BYTES, "");
	row[1] = checkAndPrintUnlimitedBytes(value, zeroNegOneLong);
	bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * Autocreate props
	 */
	row[0] = ar.getString(ar.I_AUTO_CREATE_QUEUES);
	value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE, "");
	row[1] = value;
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_AUTO_CREATE_TOPICS);
	value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_TOPIC, "");
	row[1] = value;
	bcp.add(row);

        row[0] = ar.getString(ar.I_AUTOCREATED_QUEUE_MAX_ACTIVE_CONS);
        value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS,
			"");
	row[1] = checkAndPrintUnlimited(value, negOneString);
        bcp.add(row);

        row[0] = ar.getString(ar.I_AUTOCREATED_QUEUE_MAX_FAILOVER_CONS);
        value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS,
			"");
	row[1] = checkAndPrintUnlimited(value, negOneString);
        bcp.add(row);
        
	row[0] = ar.getString(ar.I_BKR_AUTOCREATE_DESTINATION_USE_DMQ);
	value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_DESTINATION_USE_DMQ, "");
        
	row[1] = value;
	bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * Cluster related props
	 */
	row[0] = ar.getString(ar.I_CLS_CLUSTER_ID);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_CLUSTER_ID, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_IS_HA);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_HA);
	row[1] = Boolean.valueOf(value).toString();
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_ACTIVE_BROKERLIST);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_BKRLIST_ACTIVE, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_CONFIGD_BROKERLIST);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_BKRLIST, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_CONFIG_SERVER);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_CFG_SVR, "");
	row[1] = value;
	bcp.add(row);

	row[0] = ar.getString(ar.I_CLS_URL);
	value = bkrProps.getProperty(PROP_NAME_BKR_CLS_URL, "");
	row[1] = value;
	bcp.add(row);

	row[0] = "";
	row[1] = "";
	bcp.add(row);

	/*
	 * Log related props
	 */
	row[0] = ar.getString(ar.I_LOG_LEVEL);
	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_LEVEL, "");
	row[1] = value;
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_LOG_ROLLOVER_INTERVAL);
	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_ROLL_INTERVAL, "");
	row[1] = checkAndPrintUnlimited(value, zeroNegOneString);
	bcp.add(row);

	row[0] = ar.getString(ar.I_LOG_ROLLOVER_SIZE);
	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_ROLL_SIZE, "");
	row[1] = checkAndPrintUnlimitedBytes(value, zeroNegOneLong);
	bcp.add(row);
		
	bcp.println();		
    }

    private void printAllTxnAttrs(Hashtable txnInfo)  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	Object	tmpObj;

	for (Enumeration e = txnInfo.keys() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();

	    row[0] = curPropName;
	    tmpObj = txnInfo.get(curPropName);
	    row[1] = tmpObj.toString();
	    bcp.add(row);
        }
	bcp.println();		
    }

    private void printDisplayableTxnAttrs(Hashtable txnInfo)  {
	BrokerCmdPrinter	bcp = new BrokerCmdPrinter(2, 4);
	String[]		row = new String[2];
	Long			tmpLong;
	Integer			tmpInt;
	String			tmpStr;

        row[0] = ar.getString(ar.I_JMQCMD_TXN_ID);
	tmpStr = (String)txnInfo.get(PROP_NAME_TXN_ID);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_JMQCMD_TXN_STATE);
	tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_STATE);
	row[1] = getTxnStateString(tmpInt);
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_JMQCMD_TXN_NUM_MSGS);
	tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_NUM_MSGS);
	row[1] = checkNullAndPrint(tmpInt);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_TXN_NUM_ACKS);
	tmpInt = (Integer)txnInfo.get(PROP_NAME_TXN_NUM_ACKS);
	row[1] = checkNullAndPrint(tmpInt);
	bcp.add(row);

	row[0] = ar.getString(ar.I_JMQCMD_TXN_CLIENT_ID);
	tmpStr = (String)txnInfo.get(PROP_NAME_TXN_CLIENTID);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_TXN_TIMESTAMP);
	tmpLong = (Long)txnInfo.get(PROP_NAME_TXN_TIMESTAMP);
	row[1] = checkNullAndPrintTimestamp(tmpLong);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_TXN_CONNECTION);
	tmpStr = (String)txnInfo.get(PROP_NAME_TXN_CONNECTION);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_TXN_CONNECTION_ID);
	tmpLong = (Long)txnInfo.get(PROP_NAME_TXN_CONNECTION_ID);
	row[1] = checkNullAndPrint(tmpLong);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_TXN_USERNAME);
	tmpStr = (String)txnInfo.get(PROP_NAME_TXN_USER);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);

	row[0] = ar.getString(ar.I_JMQCMD_TXN_XID);
	tmpStr = (String)txnInfo.get(PROP_NAME_TXN_XID);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	bcp.println();		
    }

    private void printAllCxnAttrs(Hashtable cxnInfo)  {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4);
	String[] row = new String[2];
	Object	tmpObj;

	for (Enumeration e = cxnInfo.keys() ; e.hasMoreElements() ;) {
	    String curPropName = (String)e.nextElement();

	    row[0] = curPropName;
	    tmpObj = cxnInfo.get(curPropName);
	    row[1] = tmpObj.toString();
	    bcp.add(row);
        }
	bcp.println();		
    }

    private void printDisplayableCxnAttrs(Hashtable cxnInfo)  {
	BrokerCmdPrinter	bcp = new BrokerCmdPrinter(2, 4);
	String[]		row = new String[2];
	Long			tmpLong;
	Integer			tmpInt;
	String			tmpStr;

	bcp.setSortNeeded(false);

        row[0] = ar.getString(ar.I_JMQCMD_CXN_CXN_ID);
	tmpLong = (Long)cxnInfo.get(PROP_NAME_CXN_CXN_ID);
	row[1] = checkNullAndPrint(tmpLong);
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_JMQCMD_CXN_USER);
	tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_USER);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CXN_SERVICE);
	tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_SERVICE);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CXN_NUM_PRODUCER);
	tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_NUM_PRODUCER);
	row[1] = checkNullAndPrint(tmpInt);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CXN_NUM_CONSUMER);
	tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_NUM_CONSUMER);
	row[1] = checkNullAndPrint(tmpInt);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CXN_HOST);
	tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_HOST);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
		
	row[0] = ar.getString(ar.I_JMQCMD_CXN_PORT);
	tmpInt = (Integer)cxnInfo.get(PROP_NAME_CXN_PORT);
	row[1] = checkNullAndPrint(tmpInt);
	bcp.add(row);

	row[0] = ar.getString(ar.I_JMQCMD_CXN_CLIENT_ID);
	tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_CLIENT_ID);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);
	
	row[0] = ar.getString(ar.I_JMQCMD_CXN_CLIENT_PLATFORM);
	tmpStr = (String)cxnInfo.get(PROP_NAME_CXN_CLIENT_PLATFORM);
	row[1] = checkNullAndPrint(tmpStr);
	bcp.add(row);

	bcp.println();		
    }

    private void printDisplayableMsgAttrs(HashMap oneMsg)  {
	BrokerCmdPrinter	bcp = new BrokerCmdPrinter(2, 4),
				titleBcp = new BrokerCmdPrinter(1, 0, "-");
	String[]		row = new String[2],
				titleRow = new String[1];
	Integer			tmpInt;

	bcp.setSortNeeded(false);

	/*
	 * Message Header
	 */
	titleRow[0] = "Message Header Information";
	titleBcp.addTitle(titleRow);
	titleBcp.print();

	row[0] = "Message ID";
	row[1] = checkNullAndPrint(oneMsg.get("MessageID"));
	bcp.add(row);

	row[0] = "Correlation ID";
	row[1] = checkNullAndPrint(oneMsg.get("CorrelationID"));
	bcp.add(row);

	row[0] = "Destination Name";
	row[1] = checkNullAndPrint(oneMsg.get("DestinationName"));
	bcp.add(row);

	row[0] = "Destination Type";
	tmpInt = (Integer)oneMsg.get("DestinationType");
	row[1] = BrokerAdminUtil.getDestinationType(tmpInt.intValue());
	bcp.add(row);

	row[0] = "Delivery Mode";
	tmpInt = (Integer)oneMsg.get("DeliveryMode");
	row[1] = checkNullAndPrintDeliveryMode(tmpInt);
	bcp.add(row);

	row[0] = "Priority";
	row[1] = checkNullAndPrint(oneMsg.get("Priority"));
	bcp.add(row);

	row[0] = "Redelivered";
	row[1] = checkNullAndPrint(oneMsg.get("Redelivered"));
	bcp.add(row);

	row[0] = "Timestamp";
	row[1] = checkNullAndPrintTimestamp((Long)oneMsg.get("Timestamp"));
	bcp.add(row);

	row[0] = "Type";
	row[1] = checkNullAndPrint((String)oneMsg.get("Type"));
	bcp.add(row);

	row[0] = "Expiration";
	row[1] = checkNullAndPrintTimestamp((Long)oneMsg.get("Expiration"));
	bcp.add(row);

	row[0] = "ReplyTo Destination Name";
	row[1] = checkNullAndPrint(oneMsg.get("ReplyToDestinationName"));
	bcp.add(row);

	row[0] = "ReplyTo Destination Type";
	tmpInt = (Integer)oneMsg.get("ReplyToDestinationType");
	if (tmpInt != null)  {
	    row[1] = BrokerAdminUtil.getDestinationType(tmpInt.intValue());
	} else  {
	    row[1] = "";
	}
	bcp.add(row);

	bcp.println();		

	/*
	 * Message Properties
	 */
	titleBcp.clear();
	titleBcp.clearTitle();
	titleRow[0] = "Message Properties Information";
	titleBcp.addTitle(titleRow);
	titleBcp.print();

	Hashtable props = (Hashtable)oneMsg.get("MessageProperties");
	if (props != null)  {
	    Enumeration keys = props.keys();
	    bcp.clear();

	    while (keys.hasMoreElements())  {
	        String key = (String)keys.nextElement();
		Object val = (Object)props.get(key);
	        row[0] = key;
	        row[1] = val.toString();
	        bcp.add(row);
	    }

	    bcp.println();		
	} else  {
            Globals.stdOutPrintln("");
	}

	/*
	 * Message body
	 */
	titleBcp.clear();
	titleBcp.clearTitle();
	titleRow[0] = "Message Body Information";
	titleBcp.addTitle(titleRow);
	titleBcp.print();

	bcp.clear();
	row[0] = "Body Type";
	row[1] = checkNullAndPrintMsgBodyType((Integer)oneMsg.get("MessageBodyType"), true);
	bcp.add(row);

	/*
	row[0] = "Body Content";
	row[1] = "";
	bcp.add(row);
	*/

	bcp.println();		
    }


    private int getDestTypeMask(BrokerCmdProperties brokerCmdProps)  {
	Properties	props = brokerCmdProps.getTargetAttrs();
	String		destType = brokerCmdProps.getDestType(),
			flavour;
	int		mask = 0;

	if ((destType == null) || destType.equals(""))  {
	    return (-1);
	}

	if (destType.equals(PROP_VALUE_DEST_TYPE_TOPIC))  {
	    mask = DestType.DEST_TYPE_TOPIC;
	} else if (destType.equals(PROP_VALUE_DEST_TYPE_QUEUE))  {
	    mask = DestType.DEST_TYPE_QUEUE;
	}

	if ((props == null) || props.isEmpty())  {
	    return (mask);
	}

	flavour = props.getProperty(PROP_NAME_QUEUE_FLAVOUR);

	if (flavour == null)  {
	    return (mask);
	}

	if (flavour.equals(PROP_VALUE_QUEUE_FLAVOUR_SINGLE))  {
	    mask |= DestType.DEST_FLAVOR_SINGLE;
	} else if (flavour.equals(PROP_VALUE_QUEUE_FLAVOUR_FAILOVER))  {
	    mask |= DestType.DEST_FLAVOR_FAILOVER;
	} else if (flavour.equals(PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN))  {
	    mask |= DestType.DEST_FLAVOR_RROBIN;
	}

	return (mask);
    }

    private BrokerAdmin init()  {
	BrokerAdmin	broker;

	String 		brokerHostPort = brokerCmdProps.getBrokerHostPort(),
			adminUser = brokerCmdProps.getAdminUserId(),
			adminPasswd;
	//String        brokerHostName = CommonCmdRunnerUtil.getBrokerHost(brokerHostPort);
	int		brokerPort = -1,
			numRetries = brokerCmdProps.getNumRetries(),
			receiveTimeout = brokerCmdProps.getReceiveTimeout();
	boolean		adminKeyUsed = brokerCmdProps.isAdminKeyUsed();
	boolean		useSSL = brokerCmdProps.useSSLTransportSet();

	if (brokerCmdProps.adminDebugModeSet())  {
	    BrokerAdmin.setDebug(true);
	}

	try  {
	    adminPasswd = getPasswordFromFileOrCmdLine(brokerCmdProps);

	    broker = new BrokerAdmin(brokerHostPort,
					adminUser, adminPasswd, 
					(receiveTimeout * 1000), useSSL);

	    if (adminKeyUsed)  {
		broker.setAdminKeyUsed(true);
	    }
	    if (useSSL)  {
		broker.setSSLTransportUsed(true);
	    }
	    if (numRetries > 0)  {
		/*
		 * If the number of retries was specified, set it on the
		 * BrokerAdmin object.
		 */
		broker.setNumRetries(numRetries);
	    }
	} catch (BrokerCmdException bce)  {
	    handleBrokerCmdException(bce);

	    return (null);
	} catch (CommonCmdException cce)  {
	    handleBrokerCmdException(cce);

	    return (null);
	} catch (BrokerAdminException bae)  {
	    handleBrokerAdminException(bae);

	    return (null);
	}

	broker.addAdminEventListener(this);
	return (broker);
    }

    private void connectToBroker(BrokerAdmin broker) throws BrokerAdminException {
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
                                   OPTION_BROKER_HOSTPORT,  
                                   brokerCmdProps.debugModeSet());
    }


    private void handleBrokerCmdException(CommonCmdException bce)  {
        CommonCmdRunnerUtil.printCommonCmdException(bce);
    }

    private void printBrokerInfo(BrokerAdmin broker) {
        CommonCmdRunnerUtil.printBrokerInfo(broker, new BrokerCmdPrinter());
    }

    private void printServiceInfo() {
        printServiceInfo(null);
    }

    private void printServiceInfo(String svcName) {
	BrokerCmdPrinter bcp = new BrokerCmdPrinter(1, 4, "-");
	String[] row = new String[1];

	row[0] = ar.getString(ar.I_JMQCMD_SVC_NAME);
	bcp.addTitle(row);

	/*
	 * If servicename not provided, get value of '-n'.
	 */
	if (svcName == null)  {
	    row[0] = brokerCmdProps.getTargetName();
	} else  {
	    row[0] = svcName;
	}
	bcp.add(row);

	bcp.println();
    }

    private void printMessageInfo() {
        BrokerCmdPrinter bcp = new BrokerCmdPrinter(1, 4, "-");
        String[] row = new String[1];
	/*
        row[0] = ar.getString(ar.I_JMQCMD_MSG_ID);
	*/
        row[0] = "Message ID";
        bcp.addTitle(row);

	row[0] = brokerCmdProps.getMsgID();
        bcp.add(row);

        bcp.println();
    }

    private void printDestinationInfo() {
        BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4, "-");
        String[] row = new String[2];
        row[0] = ar.getString(ar.I_JMQCMD_DST_NAME);
        row[1] = ar.getString(ar.I_JMQCMD_DST_TYPE);
        bcp.addTitle(row);

	row[0] = brokerCmdProps.getTargetName();
	row[1] = BrokerAdminUtil.getDestinationType(getDestTypeMask(brokerCmdProps));
        bcp.add(row);

        bcp.println();
    }

    private void printDurableSubscriptionInfo() {
        BrokerCmdPrinter bcp = new BrokerCmdPrinter(2, 4, "-");
        String[] row = new String[2];
        row[0] = ar.getString(ar.I_JMQCMD_DUR_NAME);
        row[1] = ar.getString(ar.I_JMQCMD_CLIENT_ID);
        bcp.addTitle(row);

        row[0] = brokerCmdProps.getTargetName();
        row[1] = brokerCmdProps.getClientID();
        bcp.add(row);

        bcp.println();
    }

    private void printTransactionInfo() {
        BrokerCmdPrinter bcp = new BrokerCmdPrinter(1, 4, "-");
        String[] row = new String[1];
        row[0] = ar.getString(ar.I_JMQCMD_TXN_ID);
        bcp.addTitle(row);

	row[0] = brokerCmdProps.getTargetName();
        bcp.add(row);

        bcp.println();
    }

    private void printConnectionInfo() {
        BrokerCmdPrinter bcp = new BrokerCmdPrinter(1, 4, "-");
        String[] row = new String[1];
        row[0] = ar.getString(ar.I_JMQCMD_CXN_CXN_ID);
        bcp.addTitle(row);

	row[0] = brokerCmdProps.getTargetName();
        bcp.add(row);

        bcp.println();
    }


    // Check to see if the service is an admin service.
    private void isAdminService(BrokerAdmin broker, String svcName)
    throws BrokerAdminException {

        broker.sendGetServicesMessage(svcName);
        Vector svc = broker.receiveGetServicesReplyMessage();

        if ((svc != null) && (svc.size() == 1)) {
            Enumeration thisEnum = svc.elements();
            ServiceInfo sInfo = (ServiceInfo)thisEnum.nextElement();

            if (sInfo.type == ServiceType.ADMIN)
		throw new BrokerAdminException(BrokerAdminException.INVALID_OPERATION);
        }
    }

    // Check to see if the dest type is topic.
    private void isDestTypeTopic(BrokerAdmin broker, String destName)
    throws BrokerAdminException {

	// Query the destination first to make sure it is topic.
	// First get all the destinations and check each destination's type
	// until we find the one.
	// We have to do this because 'query dst' requires both the name
	// and type of the destination.
	broker.sendGetDestinationsMessage(null, -1);
	Vector dests = broker.receiveGetDestinationsReplyMessage();

	boolean found = false;
	int i = 0;
	while ((!found) && (i < dests.size())) {
	    DestinationInfo dInfo = (DestinationInfo)dests.elementAt(i);
	    if ((destName.equals(dInfo.name)) &&
		(DestType.isTopic(dInfo.type)))
		found = true;
	    i++;
	}

	if (!found) {
	    throw new BrokerAdminException(BrokerAdminException.INVALID_OPERATION);
	}
    }

    /*
     * Not used
    private String checkAndPrintUnlimitedInt(int value)  {
        return (checkAndPrintUnlimitedInt(value, 0));
    }
    */

    private String checkAndPrintUnlimitedInt(int value, int unlimitedValues[])  {
         String ret = null;

	 for (int i = 0; i < unlimitedValues.length; ++i)  {
             if (value == unlimitedValues[i])  {
                 ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
		 break;
             }
	 }

         if (ret == null)  {
             ret = Integer.toString(value);
         }

	 return (ret);
    }

    private String checkAndPrintUnlimitedInt(int value, int unlimitedValue)  {
         String ret;

         if (value == unlimitedValue)  {
             ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
         } else  {
             ret = Integer.toString(value);
         }

	 return (ret);
    }

    /*
     * Not used
    private String checkAndPrintUnlimitedLong(long value)  {
         String ret;

         if (value == 0)  {
             ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
         } else  {
             ret = new Long(value).toString();
         }

	 return (ret);
    }
    */

    private String checkAndPrintUnlimitedLong(long value, long unlimitedValues[])  {
         String ret = null;

	 for (int i = 0; i < unlimitedValues.length; ++i)  {
             if (value == unlimitedValues[i])  {
                 ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
		 break;
             }
	 }

         if (ret == null)  {
             ret = Long.valueOf(value).toString();
         }

	 return (ret);
    }


    private String checkAndPrintUnlimitedBytes(String s, long unlimitedValues[])  {
	 SizeString	ss;
         String ret = null, value = s.trim();

	 try {
	    ss = new SizeString(value);
	 } catch (Exception e)  {
	    /*
	     * Should not get here
	     */
	    return (value);
	 }

	 for (int i = 0; i < unlimitedValues.length; ++i)  {
             if (ss.getBytes() == unlimitedValues[i])  {
                 ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
		 break;
             }
	 }

         if (ret == null)  {
             ret = value;
         }

	 return (ret);
    }

    /*
     * Not used
    private String checkAndPrintUnlimitedBytes(String s)  {
	 SizeString	ss;
         String ret, value = s.trim();

	 try {
	    ss = new SizeString(value);
	 } catch (Exception e)  {
	    return (value);
	 }

         if (ss.getBytes() == 0)  {
             ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
         } else  {
             ret = value;
         }

	 return (ret);
    }
    */

    private String checkAndPrintUnlimited(String s, String unlimitedValues[])  {
         String ret = null, value = s.trim();

	 for (int i = 0; i < unlimitedValues.length; ++i)  {
             if (value.equals(unlimitedValues[i]))  {
                 ret = ar.getString(ar.I_UNLIMITED) + " (-1)";
		 break;
             }
	 }

         if (ret == null)  {
             ret = value;
         }

	 return (ret);
    }

    private String checkNullAndPrint(Object obj)  {
        return CommonCmdRunnerUtil.checkNullAndReturnPrint(obj); 
    }

    private String checkNullAndPrintTimestamp(Long timestamp)  {
        return CommonCmdRunnerUtil.checkNullAndReturnPrintTimestamp(timestamp);
    }

    /*
     * Returns an integer representing the metric type.
     * If the metric type is not specified, the totals
     * metrics is assumed.
     */
    private int getMetricType(BrokerCmdProperties brokerCmdProps)  {
	String	s = brokerCmdProps.getMetricType();
        //String commandArg = brokerCmdProps.getCommandArg();

	if (s == null)  {
	    return (METRICS_TOTALS);
	}

	if (s.equals(PROP_VALUE_METRICS_TOTALS))  {
	    return (METRICS_TOTALS);
	} else if (s.equals(PROP_VALUE_METRICS_RATES))  {
	    return (METRICS_RATES);
	} else if (s.equals(PROP_VALUE_METRICS_CONNECTIONS))  {
	    return (METRICS_CONNECTIONS);
	} else if (s.equals(PROP_VALUE_METRICS_CONSUMER))  {
	    return (METRICS_CONSUMER);
	} else if (s.equals(PROP_VALUE_METRICS_DISK))  {
	    return (METRICS_DISK);
	} else if (s.equals(PROP_VALUE_METRICS_REMOVE))  {
	    return (METRICS_REMOVE);
	}

	return (METRICS_TOTALS);
    }

    /*
     * Prompts for authentication and stores the missing username/password.
     */
    private BrokerAdmin promptForAuthentication(BrokerAdmin broker) {
        return (BrokerAdmin)CommonCmdRunnerUtil.promptForAuthentication(broker);
    }

    private boolean reconnectToBroker(BrokerAdmin broker) {

        boolean connected = false;
        int count = 0;

        while (!connected && (count < broker.getNumRetries())) {
            try {
                broker.connect();
                broker.sendHelloMessage();
                broker.receiveHelloReplyMessage();
		connected = true;

            } catch (BrokerAdminException baex) {
                // try to reconnect based on RECONNECT attributes
                if (baex.getType() == BrokerAdminException.CONNECT_ERROR) {
                    try {
                        Thread.sleep(broker.getTimeout());
                        count++;
                    } catch (InterruptedException ie) {
			connected = false;
                    }
                } else {
		    connected = false;
                }

            } catch (Exception ex) {
	        connected = false;
            }

            if (count >= broker.getNumRetries()) {
		connected = false;
	        Globals.stdErrPrintln(ar.getString(ar.E_JMQCMD_CONNECT_ERROR,
			broker.getBrokerHost(), broker.getBrokerPort()));
                Globals.stdErrPrintln(ar.getString(ar.E_MAX_RECONNECT_REACHED,
		    Long.valueOf(broker.getTimeout()*broker.getNumRetries() / 1000)));
            }
        }
        return connected;
    }

    /*
    private static Properties convertQueueDeliveryPolicy
	(Properties targetAttrs) {

	String deliveryValue = 
	    targetAttrs.getProperty(PROP_NAME_BKR_QUEUE_DELIVERY_POLICY);

        if (PROP_VALUE_QUEUE_FLAVOUR_SINGLE.equals(deliveryValue)) {
	    targetAttrs.setProperty(PROP_NAME_BKR_QUEUE_DELIVERY_POLICY, 
		PROP_NAME_QUEUE_FLAVOUR_SINGLE);

        } else if (PROP_VALUE_QUEUE_FLAVOUR_FAILOVER.equals(deliveryValue)) {
	    targetAttrs.setProperty(PROP_NAME_BKR_QUEUE_DELIVERY_POLICY, 
		PROP_NAME_QUEUE_FLAVOUR_FAILOVER);

        } else if (PROP_VALUE_QUEUE_FLAVOUR_ROUNDROBIN.equals(deliveryValue)) {
	    targetAttrs.setProperty(PROP_NAME_BKR_QUEUE_DELIVERY_POLICY, 
		PROP_NAME_QUEUE_FLAVOUR_ROUNDROBIN);

	} else {
	    // Should not get here, as the value has already been validated
	}

	return targetAttrs;
    }
    */

    /*
    private String getDisplayableQueueDeliveryPolicy(String deliveryValue) {

        if (PROP_NAME_QUEUE_FLAVOUR_SINGLE.equals(deliveryValue)) {
	    return (ar.getString(ar.I_SINGLE));

        } else if (PROP_NAME_QUEUE_FLAVOUR_FAILOVER.equals(deliveryValue)) {
	    return (ar.getString(ar.I_FAILOVER));

        } else if (PROP_NAME_QUEUE_FLAVOUR_ROUNDROBIN.equals(deliveryValue)) {
	    return (ar.getString(ar.I_RROBIN));

        } else {
            // Should not get here, as the value has already been validated
	    return (ar.getString(ar.I_UNKNOWN));
        }
    }
    */

    private int getPauseTypeVal(String destStateStr)  {
	int ret = DestState.UNKNOWN;

	if (destStateStr == null)
	    return (ret);

	if (destStateStr.equals(PROP_VALUE_PAUSETYPE_ALL))  {
	    ret = DestState.PAUSED;
	} else if (destStateStr.equals(PROP_VALUE_PAUSETYPE_PRODUCERS))  {
	    ret = DestState.PRODUCERS_PAUSED;
	} else if (destStateStr.equals(PROP_VALUE_PAUSETYPE_CONSUMERS))  {
	    ret = DestState.CONSUMERS_PAUSED;
	}
	
	return (ret);
    }

    private String getResetTypeVal(String resetType)  {

	if ((resetType == null) || (resetType.equals("")))
	    return (null);

	if (resetType.equals(PROP_VALUE_RESETTYPE_METRICS))  {
	    return (MessageType.JMQ_METRICS);
	}

	/*
	 * If "ALL" was specified, not setting reset type is OK
	 * the admin protocol treats this as "ALL".
	 */
	
	return (null);
    }

    private int getLimitBehavValue(String limitBehavStr)  {
	int ret = DestLimitBehavior.UNKNOWN;

	if (limitBehavStr == null)
	    return (ret);

	if (limitBehavStr.equals(LIMIT_BEHAV_FLOW_CONTROL))  {
	    ret = DestLimitBehavior.FLOW_CONTROL;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_RM_OLDEST))  {
	    ret = DestLimitBehavior.REMOVE_OLDEST;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_REJECT_NEWEST))  {
	    ret = DestLimitBehavior.REJECT_NEWEST;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_RM_LOW_PRIORITY))  {
	    ret = DestLimitBehavior.REMOVE_LOW_PRIORITY;
	}
	
	return (ret);
    }

    private int getClusterDeliveryPolicy(String cdp)  {
	int ret = ClusterDeliveryPolicy.UNKNOWN;

	if (cdp == null)
	    return (ret);

	boolean b = Boolean.valueOf(cdp).booleanValue();

	if (b)  {
	    ret = ClusterDeliveryPolicy.LOCAL_PREFERRED;
	} else  {
	    ret = ClusterDeliveryPolicy.DISTRIBUTED;
	}
	
	return (ret);
    }

    /*
     * Get password from either the passfile or -p option.
     * In some future release, the -p option will go away
     * leaving the passfile the only way to specify the 
     * password (besides prompting the user for it).
     * -p has higher precendence compared to -passfile.
     */
    private String getPasswordFromFileOrCmdLine(BrokerCmdProperties brokerCmdProps) 
		throws CommonCmdException  {
        String passwd = brokerCmdProps.getAdminPasswd(),
	       passfile = brokerCmdProps.getAdminPassfile();
	
	if (passwd != null)  {
	    return (passwd);
	}
    return CommonCmdRunnerUtil.getPasswordFromFile(passfile, PROP_NAME_PASSFILE_PASSWD, brokerCmdProps);

    }

    private String checkNullAndPrintDeliveryMode(Integer deliveryMode)  {
	if (deliveryMode != null)  {
	    String	val;

	    switch (deliveryMode.intValue())  {
	    case DeliveryMode.NON_PERSISTENT:
	        val = "NON_PERSISTENT";
	    break;

	    case DeliveryMode.PERSISTENT:
	        val = "PERSISTENT";
	    break;

	    default:
	        val = "Unknown";
	    }

	    return (val + " (" + deliveryMode.intValue() + ")");
	} else  {
	    return ("");
	}
    }

    private String checkNullAndPrintMsgBodyType(Integer bodyType, boolean includeValue)  {
	if (bodyType != null)  {
	    String label = null;

	    switch (bodyType.intValue())  {
	    case 1:
	        label = "TextMessage";
	    break;

	    case 2:
	        label = "BytesMessage";
	    break;

	    case 3:
	        label = "MapMessage";
	    break;

	    case 4:
	        label = "StreamMessage";
	    break;

	    case 5:
	        label = "ObjectMessage";
	    break;

	    default:
	        label = "Unknown";
	    }

	    if (includeValue)
	        return (label + " (" + bodyType.intValue() + ")");
	    return (label);
	} else  {
	    return ("");
	}
    }
}
