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
 * @(#)BrokerAdminHandler.java	1.73 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;


import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.jms.JMSSecurityException;

import com.sun.messaging.AdministeredObject;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.MessageType;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerErrorEvent;
import com.sun.messaging.jmq.admin.event.BrokerCmdStatusEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ConsoleActionEvent;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminConn;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;

/** 
 * Handles the broker administration tasks delegated by the
 * AController class.
 *
 * @see AController
 */
public class BrokerAdminHandler implements AdminEventListener  {

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static AdminResources ar = Globals.getAdminResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};
    private static String BROKERLIST_FILENAME		= "brokerlist.properties";

    /*
     * Reconnect attributes.
     * Currently not being used.
     */
    public static final boolean JMQ_RECONNECT = false;
    public static final int     JMQ_RECONNECT_RETRIES = BrokerAdmin.RECONNECT_RETRIES;
    public static final long    JMQ_RECONNECT_DELAY = BrokerAdmin.RECONNECT_DELAY;

    private AdminApp	app;
    private AController	controller;

    private BrokerAddDialog brokerAddDialog = null;
    private BrokerDestAddDialog brokerDestAddDialog = null;
    private BrokerDestPropsDialog brokerDestPropsDialog = null;
    private BrokerServicePropsDialog brokerSvcPropsDialog = null;
    private BrokerPropsDialog brokerBkrPropsDialog = null;
    private BrokerQueryDialog brokerBkrQueryDialog = null;
    private BrokerPasswdDialog brokerPasswdDialog = null;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public BrokerAdminHandler(AdminApp app, AController controller) {
	this.app = app;
	this.controller = controller;
    } 

    public void init()  {
        loadBrokerList();
    }

    /*
     * BEGIN INTERFACE AdminEventListener
     */
    public void adminEventDispatched(AdminEvent e)  {
	int id;
	ConsoleObj selObj;
	
	if (e instanceof DialogEvent)  {
	    handleDialogEvents((DialogEvent)e);
        } else if (e instanceof BrokerAdminEvent)  {
            handleBrokerAdminEvents((BrokerAdminEvent)e);
	} else if (e instanceof BrokerErrorEvent)  {
            handleBrokerErrorEvents((BrokerErrorEvent)e);
	} else if (e instanceof BrokerCmdStatusEvent)  {
            handleBrokerCmdStatusEvent((BrokerCmdStatusEvent)e);
	}
    }
    /*
     * END INTERFACE AdminEventListener
     */

    public void handleDialogEvents(DialogEvent de) {
	ConsoleObj selObj = app.getSelectedObj();
        int dialogType = de.getDialogType();

        switch (dialogType)  {
        case DialogEvent.ADD_DIALOG:
	    if (selObj instanceof BrokerListCObj) {
		if (brokerAddDialog == null) {
                    brokerAddDialog = new BrokerAddDialog(app.getFrame(),
							  (BrokerListCObj)selObj);
                    brokerAddDialog.addAdminEventListener(this);
                    brokerAddDialog.setLocationRelativeTo(app.getFrame());
	        }
                brokerAddDialog.show();

            } else if (selObj instanceof BrokerDestListCObj) {
                if (brokerDestAddDialog == null) {
                    brokerDestAddDialog =
                        new BrokerDestAddDialog(app.getFrame());
                    brokerDestAddDialog.addAdminEventListener(this);
                    brokerDestAddDialog.setLocationRelativeTo(app.getFrame());
                }
                brokerDestAddDialog.show();
            }
	break;

        case DialogEvent.DELETE_DIALOG:
            if (selObj instanceof BrokerCObj)
		doDeleteBroker((BrokerCObj)selObj);
	    else if (selObj instanceof BrokerDestCObj)
		doDeleteDestination((BrokerDestCObj)selObj);
        break;

        case DialogEvent.PURGE_DIALOG:
            if (selObj instanceof BrokerDestCObj)
		doPurgeDestination((BrokerDestCObj)selObj);
	break;

	case DialogEvent.PROPS_DIALOG:
            if (selObj instanceof BrokerDestCObj) {
                BrokerDestCObj bDestCObj;
                if (brokerDestPropsDialog == null) {
                    brokerDestPropsDialog = new BrokerDestPropsDialog(app.getFrame());
                    brokerDestPropsDialog.addAdminEventListener(this);
                    brokerDestPropsDialog.setLocationRelativeTo(app.getFrame());
                }
		bDestCObj = (BrokerDestCObj)selObj;
                if (refreshBrokerDestCObj(bDestCObj)) {
		    app.getInspector().selectedObjectUpdated();
                    brokerDestPropsDialog.show
			(bDestCObj.getDestinationInfo(), bDestCObj.getDurables());
		}

	    } else if (selObj instanceof BrokerServiceCObj) {
                BrokerServiceCObj bSvcCObj;
                if (brokerSvcPropsDialog == null) {
                    brokerSvcPropsDialog = new BrokerServicePropsDialog(app.getFrame());
                    brokerSvcPropsDialog.addAdminEventListener(this);
                    brokerSvcPropsDialog.setLocationRelativeTo(app.getFrame());
                }
                bSvcCObj = (BrokerServiceCObj)selObj;
                if (refreshBrokerServiceCObj(bSvcCObj)) {
                    app.getInspector().selectedObjectUpdated();
                    brokerSvcPropsDialog.show(bSvcCObj.getServiceInfo());
		}

	    } else if (selObj instanceof BrokerCObj) {
                if (brokerBkrPropsDialog == null) {
                    brokerBkrPropsDialog = new BrokerPropsDialog(app.getFrame());
                    brokerBkrPropsDialog.addAdminEventListener(this);
                    brokerBkrPropsDialog.setLocationRelativeTo(app.getFrame());
                }
		BrokerCObj bCObj = (BrokerCObj)selObj;
                brokerBkrPropsDialog.setBrokerCObj(bCObj);
                brokerBkrPropsDialog.show();
	    }
	break;

        case DialogEvent.CONNECT_DIALOG:
            if (selObj instanceof BrokerCObj)
		doConnectToBroker((BrokerCObj)selObj);
        break;

        case DialogEvent.DISCONNECT_DIALOG:
            if (selObj instanceof BrokerCObj)
		doDisconnectFromBroker((BrokerCObj)selObj);
        break;

        case DialogEvent.SHUTDOWN_DIALOG:
            if (selObj instanceof BrokerCObj) 
		doShutdownBroker((BrokerCObj)selObj);
        break;

        case DialogEvent.RESTART_DIALOG:
	    if (selObj instanceof BrokerCObj) 
		doRestartBroker((BrokerCObj)selObj);
        break;

        case DialogEvent.PAUSE_DIALOG:
            if (selObj instanceof BrokerServiceCObj)
		doPauseService((BrokerServiceCObj)selObj);
	    else if (selObj instanceof BrokerCObj)
		doPauseBroker((BrokerCObj)selObj);
	    else if (selObj instanceof BrokerDestCObj)
		doPauseDest((BrokerDestCObj)selObj);
	    else if (selObj instanceof BrokerDestListCObj)
		doPauseAllDests((BrokerDestListCObj)selObj);
        break;

        case DialogEvent.RESUME_DIALOG:
            if (selObj instanceof BrokerServiceCObj) 
		doResumeService((BrokerServiceCObj)selObj);
	    else if (selObj instanceof BrokerCObj)
		doResumeBroker((BrokerCObj)selObj);
	    else if (selObj instanceof BrokerDestCObj)
		doResumeDest((BrokerDestCObj)selObj);
	    else if (selObj instanceof BrokerDestListCObj)
		doResumeAllDests((BrokerDestListCObj)selObj);
        break;

        case DialogEvent.HELP_DIALOG:
	    JOptionPane.showOptionDialog(app.getFrame(), 
		acr.getString(acr.I_NO_HELP),
		acr.getString(acr.I_HELP_TEXT),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, close, close[0]);
	break;
        }
    }

    public void handleBrokerAdminEvents(BrokerAdminEvent bae) {
        int type 		 = bae.getType();
        ConsoleBrokerAdminManager baMgr = app.getBrokerListCObj().getBrokerAdminManager();
        ConsoleObj selObj 	 = app.getSelectedObj();
	BrokerAdmin ba;
	BrokerCObj bCObj;

        switch (type)  {
        case BrokerAdminEvent.ADD_BROKER:
            if (selObj instanceof BrokerListCObj) {
                try {
                    ba = new BrokerAdmin(bae.getHost(),
                                         bae.getPort(),
                                         bae.getUsername(),
                                         bae.getPassword(),
                                         -1,
                                         JMQ_RECONNECT,
                                         JMQ_RECONNECT_RETRIES,
                                         JMQ_RECONNECT_DELAY
                                         );

		    ba.setKey(bae.getBrokerName());

                } catch (BrokerAdminException baex) {
                    JOptionPane.showOptionDialog(app.getFrame(),
                        acr.getString(acr.E_BROKER_ADD_BROKER, bae.getBrokerName()) +
                           printBrokerAdminExceptionDetails(baex),
                        acr.getString(acr.I_ADD_BROKER) + ": " +
                           acr.getString(acr.I_ERROR_CODE,
                              AdminConsoleResources.E_BROKER_ADD_BROKER),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                    return;

                } catch (Exception e) {
                    JOptionPane.showOptionDialog(app.getFrame(),
                        acr.getString(acr.E_BROKER_ADD_BROKER, bae.getBrokerName()) +
                           e.toString(),
                        acr.getString(acr.I_ADD_BROKER) + ": " +
                           acr.getString(acr.I_ERROR_CODE,
                              AdminConsoleResources.E_BROKER_ADD_BROKER),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                    return;
                }

                if (baMgr.exist(ba.getKey())) {
                    JOptionPane.showOptionDialog(app.getFrame(),
			acr.getString(acr.E_BROKER_EXISTS, ba.getKey()),
                        acr.getString(acr.I_ADD_BROKER) + ": " +
			   acr.getString(acr.I_ERROR_CODE,
			      AdminConsoleResources.E_BROKER_EXISTS),	
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                    return;
                }

		bCObj = new BrokerCObj(ba);

		/*
		 * bae.isConnectAttempt() is always false, since we no longer
		 * allow users to 'connect after add'.
                if (bae.isConnectAttempt()) {
		    if (connectToBroker(ba)) {
                         baMgr.addBrokerAdmin(ba);
                         app.getExplorer().addBroker(bCObj);
			 if (!refreshBrokerCObj(bCObj)) return;
	  	         if (!populateBrokerServices(bCObj)) return;
			 if (!populateBrokerDestinations(bCObj)) return;
                         app.getInspector().refresh();
			 saveBrokerList();
		    } else
			return;
		} else {
 		 */
                baMgr.addBrokerAdmin(ba);
                app.getExplorer().addBroker(bCObj);
                app.getInspector().refresh();
		saveBrokerList();
		/*
		}
		 */

                if (bae.isOKAction())
                    brokerAddDialog.hide();
            }

        break;

	case BrokerAdminEvent.ADD_DEST:
            if (selObj instanceof BrokerDestListCObj) {
		BrokerDestCObj bDestCObj = null;
                bCObj = ((BrokerDestListCObj)selObj).getBrokerCObj();
		if ((bDestCObj = addDestination(bCObj, bae)) == null)
		    return;
		
		// Update the table entry
                app.getExplorer().addToParent(selObj, bDestCObj);
                app.getInspector().refresh();

                if (bae.isOKAction())
                    brokerDestAddDialog.hide();
	    }
	break;

	case BrokerAdminEvent.UPDATE_LOGIN:
	    doUpdateLogin(bae, selObj);
	break;

	case BrokerAdminEvent.UPDATE_BROKER:
	    doUpdateBroker(bae, selObj);
	break;

	case BrokerAdminEvent.DELETE_DUR:
	    doDeleteDurable(bae, selObj);
	break;

	case BrokerAdminEvent.PURGE_DUR:
	    doPurgeDurable(bae, selObj);
	break;

	case BrokerAdminEvent.UPDATE_SVC:
	    doUpdateService(bae, selObj);
	break;

	case BrokerAdminEvent.UPDATE_DEST:
	    doUpdateDestination(bae, selObj);
	break;

	case BrokerAdminEvent.QUERY_BROKER:
	    doQueryBroker(bae, selObj);
	break;

	case BrokerAdminEvent.UPDATE_BROKER_ENTRY:
	    doUpdateBrokerEntry(bae, selObj);
	break;
        }
    }

    private void handleBrokerErrorEvents(BrokerErrorEvent bee) {
        int type                 = bee.getType();
	BrokerListCObj blCObj 	 = app.getBrokerListCObj();
	String brokerHost 	 = bee.getBrokerHost();
	String brokerPort 	 = bee.getBrokerPort();
	String brokerName 	 = bee.getBrokerName();
	String[] args 		 = {brokerHost, brokerPort, brokerName};

        switch (type) {
        /*
	 * Handle cases where:
         *  - Broker initiated connection close.
         *  - Broker unexpectedly shutdown.
	 * the same way. See bug:
	 * 4431955 - jmqadmin:show different msg when ctrl-C broker 
	 *		depending on JDK used to run bkr
	 * Until we figure out a way to distinguish between all the
	 * possible ways of broker shutdown (graceful and non-graceful)
	 * we'll just report this in the generic "connection has been lost"
	 * fashion.
         */
        case BrokerErrorEvent.ALT_SHUTDOWN:
        case BrokerErrorEvent.UNEXPECTED_SHUTDOWN:
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_ERR_SHUTDOWN, args),
                acr.getString(acr.I_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_ERR_SHUTDOWN),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        break;
        /*
         * Other misc. connection problems.
         */
        case BrokerErrorEvent.CONNECTION_ERROR:
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_CONN_ERROR, args),
                acr.getString(acr.I_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_CONN_ERROR),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        break;
	}

        /*
	 * Get the reference to each instance of BrokerCObj.
	 * Update Gui for those affected broker label(s).
	 */	
        for (Enumeration e = blCObj.children(); e.hasMoreElements();) {
            ConsoleObj node = (ConsoleObj)e.nextElement();
            if (node instanceof BrokerCObj) {
		BrokerCObj bCObj = (BrokerCObj)node;
		
	        if ((brokerName != null) && 
		    (brokerName.equals(bCObj.getExplorerLabel()))) {
		    bCObj.setBrokerProps(null);
		    clearBroker(bCObj);
                    app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);

		    // Update menu items, buttons.
                    controller.setActions(bCObj);
	        }
            }
        }
        app.getInspector().refresh();
    }

    private void handleBrokerCmdStatusEvent(BrokerCmdStatusEvent cse)  {
        int		type                 = cse.getType(),
			msgType;
	BrokerAdmin	ba = cse.getBrokerAdmin();
	boolean		success = cse.getSuccess();
	Exception	ex = cse.getLinkedException();
	String		dstName = cse.getDestinationName(),
			svcName = cse.getServiceName(),
			clientID = cse.getClientID(),
			durName = cse.getDurableName(),
			bkrName = ba.getKey(), 
			title, msg;
	DestinationInfo dstInfo = cse.getDestinationInfo();
	ServiceInfo svcInfo = cse.getServiceInfo();
	Object refreshObj = ba.getAssociatedObj();

	if (type == BrokerCmdStatusEvent.BROKER_BUSY)  {
	    int numRetriesAttempted = cse.getNumRetriesAttempted(),
	    maxNumRetries = cse.getMaxNumRetries();
	    long retryTimeount = cse.getRetryTimeount();
	    Object args[] = new Object [ 3 ];

	    args[0] = Integer.toString(numRetriesAttempted);
	    args[1] = Integer.toString(maxNumRetries);
	    args[2] = Long.toString(retryTimeount);
	    /*
	     * This string is of the form:
	     *  Broker not responding, retrying [1 of 5 attempts, timeout=20 seconds]
	     */
	    String s = ar.getString(ar.I_JMQCMD_BROKER_BUSY, args);
            app.getStatusArea().appendText(s + "\n");
	    return;
	}


        /*
         * Do not bring up the result dialog for
         * 1.  QUERY_SVC
         * 2.  QUERY_DST
         * 3.  LIST_DUR (for querying destination)
         * 4.  QUERY_BKR
         */
        if ((type == BrokerCmdStatusEvent.QUERY_SVC) ||
            (type == BrokerCmdStatusEvent.QUERY_DST) ||
            (type == BrokerCmdStatusEvent.LIST_DUR)  ||
            (type == BrokerCmdStatusEvent.QUERY_BKR)) {
	    /*
	     * We need to refresh the console if 'connect' is successful
	     * but 'query broker' is not upon connecting to a broker.
	     */
	    if (refreshObj instanceof BrokerCObj) {
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)refreshObj);
		controller.setActions((BrokerCObj)refreshObj);
	    }
            return;
        }

	/*
	 * Type of dialog depends on success/failure
	 */
	if (success)  {
            msgType = JOptionPane.INFORMATION_MESSAGE;
	} else  {
   	    msgType = JOptionPane.ERROR_MESSAGE;
	}

	/*
	 * Dialog title reads:
	 * 	Status received from broker
	 */
	title = acr.getString(acr.I_STATUS_RECV);

	switch (type)  {
	case BrokerCmdStatusEvent.HELLO:
	    if (success)  {
	        msg = acr.getString(acr.S_BROKER_CONNECT, bkrName);

		if (refreshObj instanceof BrokerCObj) {
                    BrokerCObj bCObj = (BrokerCObj)refreshObj;
		    /*
		     * Connection is considered created when the hello protocol
		     * handshake takes place successfully.
		     */
		    ba.setIsConnected(true);
                    if (refreshBrokerServiceListCObj(bCObj.getBrokerServiceListCObj())) {
                        if (refreshBrokerDestListCObj(bCObj.getBrokerDestListCObj())) {
                	    app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                	    app.getInspector().refresh();
            		    controller.setActions(bCObj);
			}
		    }
		}
	    } else  {
	        msg = acr.getString(acr.E_CONNECT_BROKER, bkrName);
	    }
	break;

	case BrokerCmdStatusEvent.CREATE_DST:
	    if (success)  {
	        msg = acr.getString(acr.S_BROKER_DEST_ADD, dstInfo.name, bkrName);

		if (refreshObj instanceof BrokerCObj) {
		    BrokerCObj bCObj = (BrokerCObj)refreshObj;
		    BrokerDestListCObj bDestlCObj = bCObj.getBrokerDestListCObj();
		    BrokerDestCObj bDestCObj = new BrokerDestCObj(bCObj, dstInfo);
                    app.getExplorer().addToParent(bDestlCObj, bDestCObj);
                    app.getInspector().refresh();
		}
	    } else  {
	        msg = acr.getString(acr.E_ADD_DEST_BROKER, dstInfo.name, bkrName);
	    }
	break;

	case BrokerCmdStatusEvent.DESTROY_DST:
	    if (success)  {
	        msg = acr.getString(acr.S_BROKER_DEST_DELETE, dstName, bkrName);

                if (refreshObj instanceof BrokerDestCObj) {
        	    app.getExplorer().removeFromParent((BrokerDestCObj)refreshObj);
        	    app.getInspector().refresh();
                }
	    } else  {
	        msg = acr.getString(acr.E_BROKER_DEST_DELETE, dstName, bkrName);
	    }
	break;

        case BrokerCmdStatusEvent.UPDATE_DST:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_UPDATE_DEST, dstInfo.name);

            } else  {
                msg = acr.getString(acr.E_UPDATE_DEST, dstInfo.name);
            }
        break;

	case BrokerCmdStatusEvent.PURGE_DST:
	    if (success)  {
	        msg = acr.getString(acr.S_BROKER_DEST_PURGE, dstName, bkrName);
	    } else  {
	        msg = acr.getString(acr.E_BROKER_DEST_PURGE, dstName, bkrName);
	    }
	break;

	case BrokerCmdStatusEvent.DESTROY_DUR:
	    if (success)  {
	        msg = acr.getString(acr.S_BROKER_DESTROY_DUR, 
                      BrokerAdminUtil.getDSubLogString(clientID, durName));
	        if (refreshObj instanceof BrokerDestCObj) {
	    	    BrokerDestCObj bDestCObj = (BrokerDestCObj)refreshObj;
                    refreshBrokerDestCObj(bDestCObj, BrokerAdminEvent.DELETE_DUR);
                    Vector durables = bDestCObj.getDurables();
		    if (durables != null)
                        brokerDestPropsDialog.refresh(durables);
		}
	    } else  {
	        msg = acr.getString(acr.E_BROKER_DESTROY_DUR, 
                      BrokerAdminUtil.getDSubLogString(clientID, durName));
	    }
	break;

        case BrokerCmdStatusEvent.LIST_DST:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_REFRESH_DESTLIST, bkrName);

                Object obj = cse.getReturnedObject();
                BrokerDestListCObj bDestlCObj;

                if (obj instanceof Vector) {
                    Vector dests = (Vector)obj;

		    if (refreshObj instanceof BrokerDestListCObj) {
                        bDestlCObj = (BrokerDestListCObj)refreshObj;

		    } else if (refreshObj instanceof BrokerCObj) {
			BrokerCObj bCObj = (BrokerCObj)refreshObj;
                        bDestlCObj = bCObj.getBrokerDestListCObj();

		    } else
			return;
	
                    refreshBrokerDestList(dests, bDestlCObj);
                    app.getInspector().refresh();
		}
            } else  {
                msg = acr.getString(acr.E_REFRESH_DESTLIST);
            }
        break;

        case BrokerCmdStatusEvent.PAUSE_SVC:
            if (success)  {
                msg = acr.getString(acr.S_SERVICE_PAUSE, svcName, bkrName);

                if (refreshObj instanceof BrokerServiceCObj) {
		    BrokerServiceCObj bSvcCObj = (BrokerServiceCObj)refreshObj;
                    if (refreshBrokerServiceCObj(bSvcCObj)) {
                        app.getInspector().refresh();
                        controller.setActions(bSvcCObj);
		    }
                }

            } else  {
                msg = acr.getString(acr.E_SERVICE_PAUSE, svcName);
            }
        break;

        case BrokerCmdStatusEvent.RESUME_SVC:
            if (success)  {
                msg = acr.getString(acr.S_SERVICE_RESUME, svcName, bkrName);

                if (refreshObj instanceof BrokerServiceCObj) {
		    BrokerServiceCObj bSvcCObj = (BrokerServiceCObj)refreshObj;
                    if (refreshBrokerServiceCObj(bSvcCObj)) {
                        app.getInspector().refresh();
                        controller.setActions(bSvcCObj);
		    }
                }

            } else  {
                msg = acr.getString(acr.E_SERVICE_RESUME, svcName);
            }
        break;

        case BrokerCmdStatusEvent.UPDATE_SVC:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_UPDATE_SVC, svcInfo.name);

                if (refreshObj instanceof BrokerServiceCObj) {
                    if (refreshBrokerServiceCObj((BrokerServiceCObj)refreshObj))
                        app.getInspector().refresh();
                }

            } else  {
                msg = acr.getString(acr.E_UPDATE_SERVICE, svcInfo.name);
            }
        break;

        case BrokerCmdStatusEvent.LIST_SVC:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_REFRESH_SVCLIST, bkrName);

                Object obj = cse.getReturnedObject();
                BrokerServiceListCObj bSvclCObj;

                if (obj instanceof Vector) {
                    Vector svcs = (Vector)obj;

                    if (refreshObj instanceof BrokerServiceListCObj) {
                        bSvclCObj = (BrokerServiceListCObj)refreshObj;

                    } else if (refreshObj instanceof BrokerCObj) {
                        BrokerCObj bCObj = (BrokerCObj)refreshObj;
                        bSvclCObj = bCObj.getBrokerServiceListCObj();

                    } else
                        return;

                    refreshBrokerServiceList(svcs, bSvclCObj);
                    app.getInspector().refresh();
                }
            } else  {
                msg = acr.getString(acr.E_REFRESH_SVCLIST);
            }
        break;

        case BrokerCmdStatusEvent.PAUSE_BKR:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_PAUSE, bkrName);

                if (refreshObj instanceof BrokerCObj) {
                    BrokerCObj bCObj = (BrokerCObj)refreshObj;
                    if (refreshBrokerServiceListCObj(bCObj.getBrokerServiceListCObj())) {
                        app.getInspector().refresh();
                        controller.setActions(bCObj);
		    }
                }
            } else  {
                msg = acr.getString(acr.E_BROKER_PAUSE, bkrName);
            }
        break;

        case BrokerCmdStatusEvent.RESUME_BKR:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_RESUME, bkrName);

                if (refreshObj instanceof BrokerCObj) {
                    BrokerCObj bCObj = (BrokerCObj)refreshObj;
                    if (refreshBrokerServiceListCObj(bCObj.getBrokerServiceListCObj())) {
                        app.getInspector().refresh();
                        controller.setActions(bCObj);
		    }
                }
            } else  {
                msg = acr.getString(acr.E_BROKER_RESUME, bkrName);
            }
        break;

        case BrokerCmdStatusEvent.UPDATE_BKR:
            if (success)  {
                msg = acr.getString(acr.S_BROKER_UPDATE, bkrName);

            } else  {
                msg = acr.getString(acr.E_UPDATE_BROKER, bkrName);
            }
        break;

	default:
	    msg = acr.getString(acr.I_UNKNOWN_STATUS, bkrName);
	break;
	}

	if (!success) {
	    if (ex instanceof BrokerAdminException)  {
                msg = msg + "\n"
			+ printBrokerAdminExceptionDetails((BrokerAdminException)ex);
	    } else  {
	            msg = msg + "\n" + ex.toString();
	    }
	}

        JOptionPane.showOptionDialog(app.getFrame(),
                msg,
                title,
                JOptionPane.YES_NO_OPTION,
                msgType, null, close, close[0]);
    }

    private void doConnectToBroker(BrokerCObj bCObj) {
        BrokerAdmin ba = bCObj.getBrokerAdmin();
	/*
	 * This value should be true only when restart is requested.
	 */
        ba.setReconnect(false);
        /*
	 * Broker may take more time to complete the task than the specified 
	 * timeout value.
	 * The associated object is used when refreshing the console in 
	 * such cases.  We only set the associated object when BrokerAdmin is
	 * not busy; otherwise, we could be pointing to the wrong ConsoleObj.
	 */
	if (!ba.isBusy())
            ba.setAssociatedObj(bCObj);

	boolean authenticationNeeded = false;

	if (ba.getUserName().length() == 0) {
	    authenticationNeeded = true;
	}

	if (ba.getPassword().length() == 0) {
	    authenticationNeeded = true;
	}

	if (authenticationNeeded) {
            if (brokerPasswdDialog == null) {
                brokerPasswdDialog = new BrokerPasswdDialog(app.getFrame());
                brokerPasswdDialog.addAdminEventListener(this);
                brokerPasswdDialog.setLocationRelativeTo(app.getFrame());
            }
            brokerPasswdDialog.show(ba);

	} else { // no authentication needed; everything is already provided
	    if (connectToBroker(ba)) {
                if (!refreshBrokerCObj(bCObj)) return;
                if (!populateBrokerServices(bCObj)) return;
                if (!populateBrokerDestinations(bCObj)) return;
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
            	app.getInspector().refresh();
                // Update menu items, buttons.
                controller.setActions(bCObj);

            } else
                return;
	}
    }

    private boolean connectToBroker(BrokerAdmin ba) {
	return connectToBroker(ba, null, null, false);
    }

    private boolean connectToBroker(BrokerAdmin ba, String tempUsername, 
			  	    String tempPassword, boolean useTempValues) {
	try {
	    if (useTempValues) {
                ba.connect(tempUsername, tempPassword);
	    } else {
                ba.connect();
	    }

            /*
	     * Add a listener to unexpected error reporting only
	     * after a successful connect.
	     * Set the initiator to false.  This flag should only
	     * be set to true when it is ready to preform a shutdown.
	     */
            ba.addAdminEventListener(this);
	    ba.setInitiator(false);

            ba.sendHelloMessage();
            ba.receiveHelloReplyMessage();

	    /*
	     * This method is used in two ways:
	     * 1. Normal connect.
	     * 2. Restart with password prompting.
	     *
	     * In the latter case, we need to print a "restart successful"
	     * message, not a "connect successful" message.
	     *
	     * Restart without password prompting will not go here.
	     */
	    if (ba.isReconnect()) {
                app.getStatusArea().appendText
		    (acr.getString(acr.S_BROKER_RESTART, ba.getKey()));
	    } else {
                app.getStatusArea().appendText
		    (acr.getString(acr.S_BROKER_CONNECT, ba.getKey()));
	    }
	} catch (BrokerAdminException baex) {
	    /*
	     * Check to see if this is an invalid username / password.
	     */
	    if (BrokerAdminException.INVALID_LOGIN == baex.getType()) {
                JOptionPane.showOptionDialog(app.getFrame(),
                    acr.getString(acr.E_INVALID_LOGIN),
                    acr.getString(acr.I_CONNECT_BROKER) + ": " +
                       acr.getString(acr.I_ERROR_CODE,
                          AdminConsoleResources.E_INVALID_LOGIN),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    /*
	     * Check to see if this is some sort of authentication problem.
	     */
	    } else if (BrokerAdminException.SECURITY_PROB == baex.getType()) {
                JOptionPane.showOptionDialog(app.getFrame(),
                    acr.getString(acr.E_LOGIN_FORBIDDEN),
                    acr.getString(acr.I_CONNECT_BROKER) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                           AdminConsoleResources.E_LOGIN_FORBIDDEN),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    /*
	     * Display a generic error message otherwise.
	     */
	    } else {
		Object args[] = new Object [ 4 ];

		args[0] = ba.getKey();
		args[1] = ba.getBrokerHost();
		args[2] = ba.getBrokerPort();
		args[3] = printBrokerAdminExceptionDetails(baex);
                JOptionPane.showOptionDialog(app.getFrame(),
                    acr.getString(acr.E_BROKER_CONNECT, args),
                    acr.getString(acr.I_CONNECT_BROKER) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                           AdminConsoleResources.E_BROKER_CONNECT),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		/*
		 * This is not a security problem so dismiss the password dialog.
		 * If we do not do this then the user will have to manually
		 * dismiss the password dialog by clicking CANCEL.
		 */
		if (brokerPasswdDialog != null)
		    brokerPasswdDialog.hide();
	    }
	    return false;
                        
	} catch (Exception baex) {
	    JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_CONNECT_BROKER, ba.getKey()) +
		   baex.toString(),
                acr.getString(acr.I_CONNECT_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_CONNECT_BROKER),
		JOptionPane.YES_NO_OPTION,
		JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
	}
	return true;
    }

    private void doUpdateLogin(BrokerAdminEvent bae, ConsoleObj selObj) {

	if (selObj instanceof BrokerCObj) {

	    BrokerCObj bCObj = (BrokerCObj)selObj;
	    BrokerAdmin ba = bCObj.getBrokerAdmin();

	    String tempUsername = bae.getUsername();
	    String tempPasswd = bae.getPassword();

            if (connectToBroker(ba, tempUsername, tempPasswd, true)) {
                if (!refreshBrokerCObj(bCObj)) return;
                if (!populateBrokerServices(bCObj)) return;
                if (!populateBrokerDestinations(bCObj)) return;
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                app.getInspector().refresh();

	        // Close the login window
	        brokerPasswdDialog.hide();

            } else
                return;

            // Update menu items, buttons.
            controller.setActions(bCObj);
	}
    }

    /*
     * Update a running broker's properties.
     */
    private void doUpdateBroker(BrokerAdminEvent bae, ConsoleObj selObj) {
	Properties bkrProps	= bae.getBrokerProps();
	BrokerCObj bCObj;
        BrokerAdmin ba;

	if (!(selObj instanceof BrokerCObj))  {
	    /*
	     * REMINDER: Need to flag this error ?
	     */
	    return;
	}

	bCObj = (BrokerCObj)selObj;
        ba = bCObj.getBrokerAdmin();
        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
        if (!ba.isBusy())
            ba.setAssociatedObj(bCObj);

        try {
            ba.sendUpdateBrokerPropsMessage(bkrProps);
            ba.receiveUpdateBrokerPropsReplyMessage();

            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_UPDATE, ba.getKey()));

	    if (bae.isOKAction())  {
		brokerBkrQueryDialog.hide();
	    }

        } catch (BrokerAdminException bae2) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_UPDATE_BROKER, ba.getKey()) +
                   printBrokerAdminExceptionDetails(bae2),
                acr.getString(acr.I_QUERY_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_UPDATE_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        }
    }

    private void doDeleteDurable(BrokerAdminEvent bae, ConsoleObj selObj) {
        BrokerDestCObj 	bDestCObj;
        BrokerAdmin 	ba;
	String 		durableName = null;
	String 		clientID = null;

        if (!(selObj instanceof BrokerDestCObj)) {
            /*
             * REMINDER: Need to flag this error ?
             */
            return;
        }

        bDestCObj = (BrokerDestCObj)selObj;
        ba = bDestCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bDestCObj);

	durableName = bae.getDurableName();
	clientID = bae.getClientID();

	int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_DELETE_DUR, durableName, ""+clientID),
                acr.getString(acr.I_DELETE_DURABLE),
                JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
            return;

        try {
            ba.sendDestroyDurableMessage(durableName, clientID);
            ba.receiveDestroyDurableReplyMessage();

            app.getStatusArea().appendText
                (acr.getString(acr.S_BROKER_DESTROY_DUR, 
                 BrokerAdminUtil.getDSubLogString(clientID, durableName)));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_BROKER_DESTROY_DUR, 
                   BrokerAdminUtil.getDSubLogString(clientID, durableName)) +
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_DELETE_DURABLE) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_DESTROY_DUR),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }
	/*
	 * Refresh the durable subscription table.
	 */
	refreshBrokerDestCObj(bDestCObj, bae.getType());
	Vector durables = bDestCObj.getDurables();
	if (durables != null)
            brokerDestPropsDialog.refresh(durables);
    }

    private void doPurgeDurable(BrokerAdminEvent bae, ConsoleObj selObj) {
        BrokerDestCObj 	bDestCObj;
        BrokerAdmin 	ba;
	String 		durableName = null;
	String 		clientID = null;

        if (!(selObj instanceof BrokerDestCObj)) {
            /*
             * REMINDER: Need to flag this error ?
             */
            return;
        }

        bDestCObj = (BrokerDestCObj)selObj;
        ba = bDestCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bDestCObj);

	durableName = bae.getDurableName();
	clientID = bae.getClientID();

	int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_PURGE_DUR, durableName, ""+clientID),
                acr.getString(acr.I_PURGE_DURABLE),
                JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
            return;

        try {
            ba.sendPurgeDurableMessage(durableName, clientID);
            ba.receivePurgeDurableReplyMessage();

            app.getStatusArea().appendText
                (acr.getString(acr.S_BROKER_PURGE_DUR, 
                 BrokerAdminUtil.getDSubLogString(clientID, durableName)));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_BROKER_PURGE_DUR, 
                   BrokerAdminUtil.getDSubLogString(clientID, durableName))+
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_DELETE_DURABLE) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_PURGE_DUR),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	/*
	 * Refresh the durable subscription table.
	 */
	refreshBrokerDestCObj(bDestCObj, bae.getType());
	Vector durables = bDestCObj.getDurables();
	if (durables != null)
            brokerDestPropsDialog.refresh(durables);
    }

    private void doUpdateService(BrokerAdminEvent bae, ConsoleObj selObj) {
        BrokerServiceCObj  	bSvcCObj;
        BrokerAdmin    		ba;
        String	         	serviceName = null;
        int          		portValue = -1;
        int          		minThreadsValue = -1;
        int          		maxThreadsValue = -1;

        if (!(selObj instanceof BrokerServiceCObj)) {
            /*
             * REMINDER: Need to flag this error ?
             */
            return;
        }

        bSvcCObj = (BrokerServiceCObj)selObj;
        ba = bSvcCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bSvcCObj);

	serviceName = bSvcCObj.getServiceInfo().name;
        portValue = bae.getPort();
        minThreadsValue = bae.getMinThreads();
        maxThreadsValue = bae.getMaxThreads();

	ServiceInfo svcInfo = new ServiceInfo();
	svcInfo.setName(serviceName);
	svcInfo.setPort(portValue);
	svcInfo.setMinThreads(minThreadsValue);
	svcInfo.setMaxThreads(maxThreadsValue);

        try {
            ba.sendUpdateServiceMessage(svcInfo);
            ba.receiveUpdateServiceReplyMessage();

            app.getStatusArea().appendText
                (acr.getString(acr.S_BROKER_UPDATE_SVC, serviceName));

            if (bae.isOKAction())  {
                brokerSvcPropsDialog.hide();
	    }

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_UPDATE_SERVICE, serviceName) +
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_BROKER_SVC_PROPS) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_UPDATE_SERVICE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	if (refreshBrokerServiceCObj(bSvcCObj)) {
            app.getInspector().selectedObjectUpdated();

            // Update menu items, buttons.
            controller.setActions(bSvcCObj);
	}
    }

    private void doUpdateDestination(BrokerAdminEvent bae, ConsoleObj selObj) {
        BrokerDestCObj       	bDestCObj;
        BrokerAdmin             ba;
        String                  destName = null;
        int                  	destTypeMask = -1;

        if (!(selObj instanceof BrokerDestCObj)) {
            /*
             * REMINDER: Need to flag this error ?
             */
            return;
        }

        bDestCObj = (BrokerDestCObj)selObj;
        ba = bDestCObj.getBrokerAdmin();
        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
        if (!ba.isBusy())
            ba.setAssociatedObj(bDestCObj);

        destName = bDestCObj.getDestinationInfo().name;
        destTypeMask = bDestCObj.getDestinationInfo().type;

        DestinationInfo destInfo = bae.getDestinationInfo();
        destInfo.setName(destName);
        destInfo.setType(destTypeMask);

        try {
            ba.sendUpdateDestinationMessage(destInfo);
            ba.receiveUpdateDestinationReplyMessage();

            app.getStatusArea().appendText
                (acr.getString(acr.S_BROKER_UPDATE_DEST, destName));

            if (bae.isOKAction())  {
                brokerDestPropsDialog.hide();
            }

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_UPDATE_DEST, destName) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_BROKER_DEST_PROPS) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_UPDATE_DEST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

        if (refreshBrokerDestCObj(bDestCObj, bae.getType())) {
            app.getInspector().selectedObjectUpdated();

            // Update menu items, buttons.
            controller.setActions(bDestCObj);
        }
    }

    private void doQueryBroker(BrokerAdminEvent bae, ConsoleObj selObj) {
	if (!(selObj instanceof BrokerCObj))  {
	    return;
	}

        if (brokerBkrQueryDialog == null) {
            brokerBkrQueryDialog = new BrokerQueryDialog(app.getFrame());
            brokerBkrQueryDialog.addAdminEventListener(this);
            brokerBkrQueryDialog.setLocationRelativeTo(app.getFrame());
        }

	BrokerCObj bCObj = (BrokerCObj)selObj;
	if (refreshBrokerCObj(bCObj))
            brokerBkrQueryDialog.show(bCObj);
    }

    /*
     * Update brokerlist entry in admin console.
     * This is *not* updating a running broker's properties.
     */
    private void doUpdateBrokerEntry(BrokerAdminEvent bae, ConsoleObj selObj) {
        ConsoleBrokerAdminManager baMgr = 
			app.getBrokerListCObj().getBrokerAdminManager();
	BrokerCObj	bCObj;
	BrokerAdmin	ba;
	String		oldPasswd, oldUserName, oldHost, oldPort, oldName,
			newName = bae.getBrokerName();

	if (!(selObj instanceof BrokerCObj))  {
	    return;
	}

	bCObj = (BrokerCObj)selObj;
	ba = bCObj.getBrokerAdmin();

	/*
	 * Get old/current values.
	 */
	oldName = ba.getKey();
	oldHost = ba.getBrokerHost();
	oldPort = ba.getBrokerPort();
	oldUserName = ba.getUserName();
	oldPasswd = ba.getPassword();

	if (!oldName.equals(newName))  {
            if (baMgr.exist(newName)) {
                JOptionPane.showOptionDialog(app.getFrame(),
			acr.getString(acr.E_BROKER_EXISTS, newName),
                        acr.getString(acr.I_BROKER_PROPS) + ": " +
			   acr.getString(acr.I_ERROR_CODE,
			      AdminConsoleResources.E_BROKER_EXISTS),	
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                return;
            }

	}

	try  {
	    ba.setKey(bae.getBrokerName());
	    ba.setBrokerHost(bae.getHost());
	    ba.setBrokerPort(Integer.toString(bae.getPort()));
	    ba.setUserName(bae.getUsername());
	    ba.setPassword(bae.getPassword());
	} catch(BrokerAdminException baex)  {
	    int		type = baex.getType();
	    String	badVal = baex.getBadValue(),
			errorStrId = acr.E_INVALID_PORT;

	    /*
	     * Revert to old values
	     */
	    try  {
	        ba.setKey(oldName);
	        ba.setBrokerHost(oldHost);
	        ba.setBrokerPort(oldPort);
	        ba.setUserName(oldUserName);
	        ba.setPassword(oldPasswd);
	    } catch (Exception e)  {
		/*
		 * Don't check for any errors since these were
		 * the original values
		 */
	    }

	    switch (type)  {
	    case BrokerAdminException.BAD_HOSTNAME_SPECIFIED:
		errorStrId = acr.E_INVALID_HOSTNAME;
	    break;

	    case BrokerAdminException.BAD_PORT_SPECIFIED:
		errorStrId = acr.E_INVALID_PORT;
	    break;
	    }

	    JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(errorStrId, badVal),
		acr.getString(acr.I_BROKER_PROPS) + ": " +
	    	    acr.getString(acr.I_ERROR_CODE, errorStrId),
		JOptionPane.YES_NO_OPTION,
		JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    
	    return;
	}

        app.getExplorer().nodeChanged(selObj);
        app.getStatusArea().appendText
                (acr.getString(acr.S_BROKER_ENTRY_UPDATE, selObj.toString()));
	app.getInspector().selectedObjectUpdated();

	saveBrokerList();

        if (bae.isOKAction())
            brokerBkrPropsDialog.hide();

    }

    private boolean reconnectToBroker(BrokerCObj bCObj) 
	throws BrokerAdminException {

	BrokerAdmin ba = bCObj.getBrokerAdmin();

	boolean connected = false;
	int count = 0;

	while (!connected && (count < BrokerAdmin.RECONNECT_RETRIES)) {
            try {
		count++;
                ba.connect();
                ba.sendHelloMessage();
                ba.receiveHelloReplyMessage();
		connected = true;

                /*
                 * Add a listener to unexpected error reporting only
                 * after a successful connect.
                 * Set the initiator to false.  This flag should only
                 * be set to true when it is ready to preform a shutdown.
                 */
                ba.addAdminEventListener(this);
                ba.setInitiator(false);

            } catch (BrokerAdminException baex) {
		// try to reconnect based on RECONNECT attributes
		if (baex.getType() == BrokerAdminException.CONNECT_ERROR) {
		    try {
		        Thread.sleep(BrokerAdmin.RECONNECT_DELAY);
		    } catch (InterruptedException ie) {
                	JOptionPane.showOptionDialog(app.getFrame(),
                            acr.getString(acr.E_RECONNECT_BROKER, ba.getKey()) +
                    	       ie.toString(),
                            acr.getString(acr.I_BROKER) + ": " +
                               acr.getString(acr.I_ERROR_CODE,
                                  AdminConsoleResources.E_RECONNECT_BROKER),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                	// Update the node
                	app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                	// Update menu items, buttons.
                	controller.setActions(bCObj);
		    }
		} else {
               	    JOptionPane.showOptionDialog(app.getFrame(),
                        acr.getString(acr.E_RECONNECT_BROKER, ba.getKey()) +
                           baex.toString(),
                        acr.getString(acr.I_BROKER) + ": " +
                           acr.getString(acr.I_ERROR_CODE,
                              AdminConsoleResources.E_RECONNECT_BROKER),
                    	JOptionPane.YES_NO_OPTION,
                    	JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                    // Update the node
                    app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                    // Update menu items, buttons.
                    controller.setActions(bCObj);
		}
		
            } catch (Exception ex) {
                JOptionPane.showOptionDialog(app.getFrame(),
                    acr.getString(acr.E_RECONNECT_BROKER, ba.getKey()) +
                       ex.toString(),
                    acr.getString(acr.I_BROKER) + ": " +
                       acr.getString(acr.I_ERROR_CODE,
                          AdminConsoleResources.E_RECONNECT_BROKER),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                // Update the node
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                // Update menu items, buttons.
                controller.setActions(bCObj);
	    }

	    if (count >= BrokerAdmin.RECONNECT_RETRIES) {
                JOptionPane.showOptionDialog(app.getFrame(),
		    acr.getString(acr.E_RECONNECT),
                    acr.getString(acr.I_CONNECT_BROKER) + ": " +
			acr.getString(acr.I_ERROR_CODE,
			  AdminConsoleResources.E_RECONNECT),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                // Update the node
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                // Update menu items, buttons.
                controller.setActions(bCObj);

	    }
        }
	return connected;
    }

    private void doDisconnectFromBroker(BrokerCObj bCObj) {
	BrokerAdmin ba = bCObj.getBrokerAdmin();

	if (ba == null)  {
	    /*
	     * Should not get here.
	     */
	    return;
	}

	/*
	 * If BrokerAdmin is busy, disallow disconnect.
	 */
	if (ba.isBusy())  {
	    Object	args[] = new Object [ 3 ];
	    String	busyMsg;


	    args[0] = ba.getKey();
	    args[1] = ba.getBrokerHost();
	    args[2] = ba.getBrokerPort();
	    busyMsg = acr.getString(acr.I_BUSY_WAIT_FOR_REPLY, args);

            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_DISCONNECT_BROKER_NOT_POSSIBLE)
		    + busyMsg,
                acr.getString(acr.I_DISCONNECT_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);

	    return;
	}

        // Remove a listener to unexpected error reporting
        ba.removeAdminEventListener(this);

        /*
         * This value should be true only when restart is requested.
         */
	ba.setReconnect(false);

        if ((ba != null) && (disconnectFromBroker(ba))) {
	    bCObj.setBrokerProps(null);
	    /*
	     * Remove destinations and services from this broker's node
	     * hierarchy.
	     */
            clearBroker(bCObj);
            app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
            app.getInspector().refresh();
        } else
            return;

        // Update menu items, buttons.
        controller.setActions(bCObj);
    }

    // REVISIT: BrokerAdmin may need to be changed to propagage the exceptions.
    private boolean disconnectFromBroker(BrokerAdmin ba) {
        ba.close();
        app.getStatusArea().appendText
	    (acr.getString(acr.S_BROKER_DISCONNECT, ba.getKey()));
        return true;
    }

    private boolean populateBrokerServices(BrokerCObj bCObj) {
        BrokerServiceListCObj bSvclCObj = null;

	bSvclCObj = bCObj.getBrokerServiceListCObj();
	return refreshBrokerServiceListCObj(bSvclCObj);
    }

    private boolean refreshBrokerServiceListCObj(BrokerServiceListCObj bSvclCObj) {
        BrokerCObj bCObj;
        BrokerAdmin ba;
        Vector svcs = null;

        bCObj = bSvclCObj.getBrokerCObj();
        ba = bCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bSvclCObj);

        try {
            ba.sendGetServicesMessage(null);
            svcs = ba.receiveGetServicesReplyMessage();

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_REFRESH_SVCLIST) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_REFRESH_SVCLIST) + ": " +
                    acr.getString(acr.I_ERROR_CODE, 
		    AdminConsoleResources.E_REFRESH_SVCLIST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	refreshBrokerServiceList(svcs, bSvclCObj);
	return true;
    }

    private void refreshBrokerServiceList(Vector svcs, BrokerServiceListCObj bSvclCObj) {
        BrokerCObj bCObj = bSvclCObj.getBrokerCObj();

        // If there are any services, remove them all from the list, only
        // if the new services is NOT null.
        if (svcs != null) {
            if (bSvclCObj != null)
                bSvclCObj.removeAllChildren();

            BrokerServiceCObj bSvcCObj;
            int i = 0;

            Enumeration e = svcs.elements();
            while (e.hasMoreElements()) {
                ServiceInfo sInfo = (ServiceInfo)e.nextElement();
                bSvcCObj = new BrokerServiceCObj(bCObj, sInfo);
                bSvclCObj.insert(bSvcCObj, i++);
            }
        }
    }

    private boolean refreshBrokerServiceCObj(BrokerServiceCObj bSvcCObj) {
        ServiceInfo oldSvcInfo = bSvcCObj.getServiceInfo();
        BrokerAdmin ba = bSvcCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bSvcCObj);

        Vector svc = null;
        try {
            ba.sendGetServicesMessage(oldSvcInfo.name);
            /*
             * False because users do not need to know whether
             * or not the operation had succeeded after timeout.
             */
            svc = ba.receiveGetServicesReplyMessage(false);

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_RETRIEVE_SVC, oldSvcInfo.name) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_BROKER_SVC_PROPS) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    AdminConsoleResources.E_RETRIEVE_SVC),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }

        if ((svc != null) && (svc.size() == 1)) {
            Enumeration e = svc.elements();
            ServiceInfo sInfo = (ServiceInfo)e.nextElement();
            bSvcCObj.setServiceInfo(sInfo);
	    return true;
        }
	return false;
    }

    private boolean populateBrokerDestinations(BrokerCObj bCObj) {
        BrokerDestListCObj bDestlCObj = null;

	bDestlCObj = bCObj.getBrokerDestListCObj();
	return refreshBrokerDestListCObj(bDestlCObj);
    }

    private boolean refreshBrokerDestListCObj(BrokerDestListCObj bDestlCObj) {
        //BrokerCObj bCObj;
        BrokerAdmin ba;
        Vector dests = null;

	//bCObj = bDestlCObj.getBrokerCObj();
	ba = bDestlCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bDestlCObj);

        try {
            ba.sendGetDestinationsMessage(null, -1);
            dests = ba.receiveGetDestinationsReplyMessage();

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_REFRESH_DESTLIST) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_REFRESH_DESTLIST) + ": " +
                    acr.getString(acr.I_ERROR_CODE, 
                    AdminConsoleResources.E_REFRESH_DESTLIST),
                JOptionPane.YES_NO_OPTION, 
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	refreshBrokerDestList(dests, bDestlCObj);
	return true;
    }

    private void refreshBrokerDestList(Vector dests, BrokerDestListCObj bDestlCObj) {
        BrokerCObj bCObj = bDestlCObj.getBrokerCObj();

        // If there are any destinations, remove them all from the list, only
	// if the new destinations is NOT null.
	if (dests != null) {
	    if (bDestlCObj != null)
                bDestlCObj.removeAllChildren();

            BrokerDestCObj bDestCObj;
            int i = 0;

            Enumeration e = dests.elements();
            while (e.hasMoreElements()) {
                DestinationInfo dInfo = (DestinationInfo)e.nextElement();
                if ((!DestType.isTemporary(dInfo.type)) &&
                    (!MessageType.JMQ_ADMIN_DEST.equals(dInfo.name)) &&
                    (!MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(dInfo.name)) &&
                    (!DestType.isInternal(dInfo.fulltype))) {
                    bDestCObj = new BrokerDestCObj(bCObj, dInfo);
                    bDestlCObj.insert(bDestCObj, i++);
                }
            }
        }
    }


    /*
     * This is needed because eventType from other Event types may conflict
     * with BrokerAdminEvent type.
     */
    private boolean refreshBrokerDestCObj(BrokerDestCObj bDestCObj) {
	return refreshBrokerDestCObj(bDestCObj, -1);
    }

    private boolean refreshBrokerDestCObj(BrokerDestCObj bDestCObj, int eventType) {
	DestinationInfo	oldDestInfo = bDestCObj.getDestinationInfo();
	BrokerAdmin ba = bDestCObj.getBrokerAdmin();

        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bDestCObj);

        Vector dests = null;
        Vector durables = null;

	boolean succeed = false;
        try {
            ba.sendGetDurablesMessage(oldDestInfo.name, null);
            /*
             * False because users do not need to know whether
             * or not the operation had succeeded after timeout.
             */
            durables = ba.receiveGetDurablesReplyMessage(false);

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_RETRIEVE_DUR, oldDestInfo.name) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_BROKER_DEST_PROPS) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    AdminConsoleResources.E_RETRIEVE_DUR),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            return false;
        }

        if (durables.size() >= 0) {
            bDestCObj.setDurables(durables);
            succeed = true;
        }

        try {
            ba.sendGetDestinationsMessage(oldDestInfo.name, oldDestInfo.type);
            /*
             * False because users do not need to know whether
             * or not the operation had succeeded after timeout.
             */
            dests = ba.receiveGetDestinationsReplyMessage(false);

        } catch (BrokerAdminException baex) {
	    /* 
	     *Do not pop up an error message, as another error message will be popped up.
	     */	
	    if (eventType == BrokerAdminEvent.DELETE_DUR || 
		eventType == BrokerAdminEvent.UPDATE_DEST) {
		return false;
	    }

            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_RETRIEVE_DEST, oldDestInfo.name) +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_BROKER_DEST_PROPS) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    AdminConsoleResources.E_RETRIEVE_DEST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);

            return false;
        }

       	if ((dests != null) && (dests.size() == 1)) {
	    Enumeration e = dests.elements();
	    DestinationInfo dInfo = (DestinationInfo)e.nextElement();
	    if ((!DestType.isTemporary(dInfo.type)) &&
	        (!DestType.isInternal(dInfo.fulltype)) &&
	                (!MessageType.JMQ_BRIDGE_ADMIN_DEST.equals(dInfo.name)) &&
	                (!MessageType.JMQ_ADMIN_DEST.equals(dInfo.name))) {
		bDestCObj.setDestinationInfo(dInfo);
	    }
	    succeed = true;
	}
	return succeed;
    }

    /*
     * Remove destinations and services from this broker's node
     * hierarchy.
     */
    private void clearBroker(BrokerCObj bCObj) {
        BrokerDestListCObj bDestlCObj = null;
	BrokerServiceListCObj bSvclCObj = null;

        // Get the reference to the instance of BrokerDestListCObj
        for (Enumeration e = bCObj.children(); e.hasMoreElements();) {
            ConsoleObj node = (ConsoleObj)e.nextElement();
            if (node instanceof BrokerDestListCObj)  {
                bDestlCObj = (BrokerDestListCObj)node;
	    }  else if (node instanceof BrokerServiceListCObj)  {
                bSvclCObj = (BrokerServiceListCObj)node;
	    }	
	}	

	if (bDestlCObj != null)  {
	    bDestlCObj.removeAllChildren();
	}
	if (bSvclCObj != null)  {
	    bSvclCObj.removeAllChildren();
	}
    }

    private void doDeleteBroker(BrokerCObj bCObj) {

        ConsoleBrokerAdminManager baMgr = app.getBrokerListCObj().getBrokerAdminManager();
        BrokerAdmin ba = bCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_DELETE, ba.getKey()),
                acr.getString(acr.I_DELETE_BROKER),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            baMgr.deleteBrokerAdmin(ba);
            saveBrokerList();
            ba.close();

            app.getExplorer().removeFromParent(bCObj);
            controller.clearSelection();
        }
    }

    private void doPauseService(BrokerServiceCObj bSvcCObj) {
	BrokerAdmin ba = bSvcCObj.getBrokerAdmin();
        ServiceInfo svcInfo = bSvcCObj.getServiceInfo();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_SERVICE_PAUSE, svcInfo.name, ba.getKey()),
                acr.getString(acr.I_PAUSE_SERVICE),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bSvcCObj);

            if (pauseService(ba, svcInfo.name)) {
                svcInfo = queryServiceInfo(ba, svcInfo.name);
		if (svcInfo != null) {
                    bSvcCObj.setServiceInfo(svcInfo);
                    app.getInspector().selectedObjectUpdated();
		}

                // Update menu items, buttons.
                controller.setActions(bSvcCObj);
	    }
        }
    }

    private boolean pauseService(BrokerAdmin ba, String svcName) {
        try {
            ba.sendPauseMessage(svcName);
            ba.receivePauseReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_SERVICE_PAUSE, svcName, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_SERVICE_PAUSE, svcName) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_PAUSE_BROKER) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_SERVICE_PAUSE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private void doResumeService(BrokerServiceCObj bSvcCObj) {

	BrokerAdmin ba = bSvcCObj.getBrokerAdmin();
        ServiceInfo svcInfo = bSvcCObj.getServiceInfo();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_SERVICE_RESUME, svcInfo.name, ba.getKey()),
                acr.getString(acr.I_RESUME_SERVICE),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bSvcCObj);

            if (resumeService(ba, svcInfo.name)) {
                svcInfo = queryServiceInfo(ba, svcInfo.name);
		if (svcInfo != null) {
                    bSvcCObj.setServiceInfo(svcInfo);
                    app.getInspector().selectedObjectUpdated();
		}
                // Update menu items, buttons.
                controller.setActions(bSvcCObj);
	    }
        }
    }

    private boolean resumeService(BrokerAdmin ba, String svcName) {
        try {
            ba.sendResumeMessage(svcName);
            ba.receiveResumeReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_SERVICE_RESUME, svcName, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_SERVICE_RESUME, svcName) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_RESUME_SERVICE) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_SERVICE_RESUME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private void doPauseDest(BrokerDestCObj bDestCObj) {
	BrokerAdmin ba = bDestCObj.getBrokerAdmin();
        DestinationInfo destInfo = bDestCObj.getDestinationInfo();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_DEST_PAUSE, destInfo.name, ba.getKey()),
                acr.getString(acr.I_PAUSE_DEST),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	/*
	 * REVISIT:
	 * At this point we can ask the user what pauseType they want:
	 *	ALL
	 *	PRODUCERS
	 *	CONSUMERS
	 * For now, we assume 'ALL' (== DestState.PAUSED).
	 */

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bDestCObj);

            if (pauseDest(ba, destInfo.name, destInfo.type, DestState.PAUSED)) {
	        destInfo = queryDestinationInfo(ba, destInfo.name, destInfo.type);
		if (destInfo != null) {
                    bDestCObj.setDestinationInfo(destInfo);
                    app.getInspector().selectedObjectUpdated();
		}

                // Update menu items, buttons.
                controller.setActions(bDestCObj);
	    }
        }
    }

    private void doPauseAllDests(BrokerDestListCObj bDestListCObj) {
	BrokerAdmin ba = bDestListCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_DEST_PAUSE_ALL, ba.getKey()),
                acr.getString(acr.I_PAUSE_ALL_DESTS),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bDestListCObj);

            if (pauseAllDests(ba, DestType.DEST_TYPE_QUEUE, DestState.PAUSED)) {

		if (!populateBrokerDestinations((BrokerCObj)bDestListCObj.getParent())) return;

                // Update menu items, buttons.
                controller.setActions(bDestListCObj);
                app.getInspector().refresh();
	    }
        }
    }

    private boolean pauseDest(BrokerAdmin ba, String destName, 
					int destTypeMask, int pauseType) {
        try {
            ba.sendPauseMessage(destName, destTypeMask, pauseType);
            ba.receivePauseReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_DEST_PAUSE, destName, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_DEST_PAUSE, destName) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_PAUSE_DEST) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_DEST_PAUSE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private boolean pauseAllDests(BrokerAdmin ba, 
				int destTypeMask, int pauseType) {
        try {
            ba.sendPauseMessage(null, destTypeMask, pauseType);
            ba.receivePauseReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_DEST_ALL_PAUSE, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
		acr.getString(acr.E_DEST_ALL_PAUSE) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_PAUSE_ALL_DESTS) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_DEST_ALL_PAUSE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }


    private void doPauseBroker(BrokerCObj bCObj) {

	BrokerAdmin ba = bCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_PAUSE, ba.getKey()),
                acr.getString(acr.I_PAUSE_BROKER),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bCObj);

            if (pauseBroker(ba)) {
                Enumeration e = bCObj.children();
                BrokerServiceListCObj bSvclCObj = null;
                while (e.hasMoreElements()) {
                    ConsoleObj cObj = (ConsoleObj)e.nextElement();
                    if (cObj instanceof BrokerServiceListCObj)
                        bSvclCObj = (BrokerServiceListCObj)cObj;
                }

                Vector svcs = queryAllServiceInfo(ba);
                if (svcs != null) {
		    updateAllServiceInfo(bSvclCObj, svcs);
            	    // Update menu items, buttons.
                    controller.setActions(bCObj);
                    app.getInspector().refresh();
		}
	    }
        }
    }

    private boolean pauseBroker(BrokerAdmin ba) {
        try {
            ba.sendPauseMessage(null);
            ba.receivePauseReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_PAUSE, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_PAUSE, ba.getKey()) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_PAUSE_BROKER) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
			AdminConsoleResources.E_BROKER_PAUSE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private void doResumeBroker(BrokerCObj bCObj) {

	BrokerAdmin ba = bCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_RESUME, ba.getKey()),
                acr.getString(acr.I_RESUME_BROKER),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bCObj);

            if (resumeBroker(ba)) {
                Enumeration e = bCObj.children();
                BrokerServiceListCObj bSvclCObj = null;
                while (e.hasMoreElements()) {
                    ConsoleObj cObj = (ConsoleObj)e.nextElement();
                    if (cObj instanceof BrokerServiceListCObj)
                        bSvclCObj = (BrokerServiceListCObj)cObj;
                }

                Vector svcs = queryAllServiceInfo(ba);
		if (svcs != null) {
                    updateAllServiceInfo(bSvclCObj, svcs);
            	    // Update menu items, buttons.
                    controller.setActions(bCObj);
                    app.getInspector().refresh();
		}
	    }
        }
    }

    private boolean resumeBroker(BrokerAdmin ba) {
        try {
            ba.sendResumeMessage(null);
            ba.receiveResumeReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_RESUME, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_RESUME, ba.getKey()) + ".\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_RESUME_BROKER) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		        AdminConsoleResources.E_BROKER_RESUME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private void doResumeAllDests(BrokerDestListCObj bDestListCObj) {
	BrokerAdmin ba = bDestListCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_DEST_RESUME_ALL, ba.getKey()),
                acr.getString(acr.I_RESUME_ALL_DESTS),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bDestListCObj);

            if (resumeAllDests(ba, DestType.DEST_TYPE_QUEUE)) {

		if (!populateBrokerDestinations((BrokerCObj)bDestListCObj.getParent())) return;

                // Update menu items, buttons.
                controller.setActions(bDestListCObj);
                app.getInspector().refresh();
	    }
        }
    }

    private void doResumeDest(BrokerDestCObj bDestCObj) {
	BrokerAdmin ba = bDestCObj.getBrokerAdmin();
        DestinationInfo destInfo = bDestCObj.getDestinationInfo();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_DEST_RESUME, destInfo.name, ba.getKey()),
                acr.getString(acr.I_RESUME_DEST),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
            /*
             * Broker may take more time to complete the task than the specified 
             * timeout value.
             * This value is used when refreshing the console in such cases.
             */
	    if (!ba.isBusy())
                ba.setAssociatedObj(bDestCObj);

            if (resumeDest(ba, destInfo.name, destInfo.type)) {
	        destInfo = queryDestinationInfo(ba, destInfo.name, destInfo.type);
		if (destInfo != null) {
                    bDestCObj.setDestinationInfo(destInfo);
                    app.getInspector().selectedObjectUpdated();
		}

                // Update menu items, buttons.
                controller.setActions(bDestCObj);
	    }
        }
    }


    private boolean resumeAllDests(BrokerAdmin ba,
					int destTypeMask) {
        try {
            ba.sendResumeMessage(null, destTypeMask);
            ba.receiveResumeReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_DEST_ALL_RESUME, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_DEST_ALL_RESUME) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_RESUME_ALL_DESTS) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_DEST_ALL_RESUME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private boolean resumeDest(BrokerAdmin ba, String destName, 
					int destTypeMask) {
        try {
            ba.sendResumeMessage(destName, destTypeMask);
            ba.receiveResumeReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_DEST_RESUME, destName, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_DEST_RESUME, destName) + "\n" +
                    printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_RESUME_DEST) + ": " +
		    acr.getString(acr.I_ERROR_CODE,
		    AdminConsoleResources.E_DEST_RESUME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private boolean refreshBrokerCObj(BrokerCObj bCObj) {
	BrokerAdmin ba = bCObj.getBrokerAdmin();
        Properties bkrProps = queryBrokerProps(ba);

        if (bkrProps != null)  {
            bCObj.setBrokerProps(bkrProps);
	    return true;
        }
	return false;
    }

    private Properties queryBrokerProps(BrokerAdmin ba) {
	Properties bkrProps = null;

        try {
            ba.sendGetBrokerPropsMessage();
	    /*
	     * False because users do not need to know whether
	     * or not the operation had succeeded after timeout.
	     */
	    bkrProps = ba.receiveGetBrokerPropsReplyMessage(false);

        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_QUERY, ba.getKey()) +
                    printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_BROKER) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_BROKER_QUERY),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        }
	return bkrProps;
    }

    private ServiceInfo queryServiceInfo(BrokerAdmin ba, String svcName) {
	ServiceInfo svcInfo = null;

        try {
            ba.sendGetServicesMessage(svcName);
            /*
             * False because users do not need to know whether
             * or not the operation had succeeded after timeout.
             */
            Vector svc = ba.receiveGetServicesReplyMessage(false);
            if ((svc != null) && (svc.size() == 1)) {
                Enumeration e = svc.elements();
                svcInfo = (ServiceInfo)e.nextElement();
            }
        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_SERVICE_QUERY, svcName) +
                    printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_BROKER) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_SERVICE_QUERY),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        }
	return svcInfo;
    }

    private Vector queryAllServiceInfo(BrokerAdmin ba) {
        Vector svcs = null;

        try {
            ba.sendGetServicesMessage(null);
            svcs = ba.receiveGetServicesReplyMessage();

        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_ALL_SERVICES_QUERY) +
                    printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_BROKER) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_ALL_SERVICES_QUERY),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        }
        return svcs;
    }

    private void updateAllServiceInfo(BrokerServiceListCObj bSvclCObj, Vector svcs) {
        if (bSvclCObj == null || svcs == null)
            return;

        Enumeration e = bSvclCObj.children();
        while (e.hasMoreElements()) {
            BrokerServiceCObj bSvcCObj = (BrokerServiceCObj)e.nextElement();
            for (int i = 0; i < svcs.size(); i++) {
                ServiceInfo svc = (ServiceInfo)svcs.elementAt(i);
                if ((bSvcCObj.getServiceInfo().name).equals(svc.name))
                    bSvcCObj.setServiceInfo((ServiceInfo)svcs.elementAt(i));
            }
        }
    }

    private DestinationInfo queryDestinationInfo(BrokerAdmin ba, String name, int type) {
        DestinationInfo destInfo = null;

        try {
            ba.sendGetDestinationsMessage(name, type);
            /*
             * False because users do not need to know whether
             * or not the operation had succeeded after timeout.
             */
            Vector dest = ba.receiveGetDestinationsReplyMessage(false);
            if ((dest != null) && (dest.size() == 1)) {
                Enumeration e = dest.elements();
                destInfo = (DestinationInfo)e.nextElement();
            }

        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_DEST_QUERY, name) +
                    printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_BROKER) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_DEST_QUERY),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
        }
        return destInfo;
    }

    private void doShutdownBroker(BrokerCObj bCObj) {

	BrokerAdmin ba = bCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_SHUTDOWN, ba.getKey()),
                acr.getString(acr.I_SHUTDOWN_BROKER),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
	    ba.setInitiator(true);

            /*
             * This value should be true only when restart is requested.
             */
            ba.setReconnect(false);

            if (shutdownBroker(ba)) {
                clearBroker(bCObj);
                app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                app.getInspector().refresh();

                // Update menu items, buttons.
                controller.setActions(bCObj);
	    }
        }
    }

    private boolean shutdownBroker(BrokerAdmin ba) {
        try {
            ba.sendShutdownMessage(false);
            ba.receiveShutdownReplyMessage();

            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_SHUTDOWN, ba.getKey()));

        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_SHUTDOWN_BROKER, ba.getKey()) +
                   printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_SHUTDOWN_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_SHUTDOWN_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return false;
        }
	return true;
    }

    private void doRestartBroker(BrokerCObj bCObj) {

	BrokerAdmin ba = bCObj.getBrokerAdmin();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_RESTART, ba.getKey()),
                acr.getString(acr.I_RESTART_BROKER),
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.NO_OPTION)
            return;

	if (ba != null) {
	    ba.setInitiator(true);
            /*
             * This value should be true only when restart is requested.
             */
	    ba.setReconnect(true);
            restartBroker(ba, bCObj);
        }
    }

    private boolean restartBroker(BrokerAdmin ba, BrokerCObj bCObj) {
        try {
            ba.sendShutdownMessage(true);
            ba.receiveShutdownReplyMessage();
	    clearBroker(bCObj);

            boolean authenticationNeeded = false;

            if (ba.getUserName().length() == 0) {
                authenticationNeeded = true;
            }

            if (ba.getPassword().length() == 0) {
                authenticationNeeded = true;
            }

            if (authenticationNeeded) {
                if (brokerPasswdDialog == null) {
                    brokerPasswdDialog = new BrokerPasswdDialog(app.getFrame());
                    brokerPasswdDialog.addAdminEventListener(this);
                    brokerPasswdDialog.setLocationRelativeTo(app.getFrame());
                }
                brokerPasswdDialog.show(ba);

            } else { // no authentication needed; everything is already provided
                if (reconnectToBroker(bCObj)) {
                    if (!refreshBrokerCObj(bCObj)) return false;
                    if (!populateBrokerServices(bCObj)) return false;
                    if (!populateBrokerDestinations(bCObj)) return false;
                    app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
                    app.getInspector().refresh();
                    app.getStatusArea().appendText
		        (acr.getString(acr.S_BROKER_RESTART, ba.getKey()));

                } else
                    return false;

                // Update menu items, buttons.
                controller.setActions(bCObj);
	    }

        } catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_RESTART_BROKER, ba.getKey()) +
                   printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_RESTART_BROKER) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_RESTART_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    // Update the node
            app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
            // Update menu items, buttons.
            controller.setActions(bCObj);

	    return false;
        }
	return true;
    }

    private BrokerDestCObj addDestination(BrokerCObj bCObj, BrokerAdminEvent bae) {

	BrokerDestCObj bDestCObj;
	DestinationInfo destInfo = createDestination(bae);
	BrokerAdmin ba = bCObj.getBrokerAdmin();
        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bCObj);

	String destName = destInfo.name;
	try {
	    ba.sendCreateDestinationMessage(destInfo);
	    ba.receiveCreateDestinationReplyMessage();
	    bDestCObj = new BrokerDestCObj(bCObj, destInfo);
            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_DEST_ADD, destInfo.name, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_ADD_DEST_BROKER, destName, ba.getKey()) +
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_ADD_BROKER_DEST) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_ADD_DEST_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            return null;
        }
	return bDestCObj;
    }

    private DestinationInfo createDestination(BrokerAdminEvent bae) {
	DestinationInfo destInfo = new DestinationInfo();

        destInfo.setName(bae.getDestinationName());
        destInfo.setType(bae.getDestinationTypeMask());

	if (DestType.isQueue(bae.getDestinationTypeMask())) {
            destInfo.setMaxActiveConsumers(bae.getActiveConsumers());
            destInfo.setMaxFailoverConsumers(bae.getFailoverConsumers());
	}

        destInfo.setMaxProducers(bae.getMaxProducers());
	destInfo.setMaxMessageBytes(bae.getMaxMesgBytes());
	destInfo.setMaxMessages(bae.getMaxMesg());
	destInfo.setMaxMessageSize(bae.getMaxPerMesgSize());
	
	return destInfo;
    }

    private void doDeleteDestination(BrokerDestCObj bDestCObj) {

	BrokerAdmin ba = bDestCObj.getBrokerAdmin();
        /*
         * Broker may take more time to complete the task than the specified 
         * timeout value.
         * This value is used when refreshing the console in such cases.
         */
	if (!ba.isBusy())
            ba.setAssociatedObj(bDestCObj);

	DestinationInfo destInfo = bDestCObj.getDestinationInfo();

	int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_DELETE_DEST, destInfo.name, ba.getKey()),
                acr.getString(acr.I_DELETE_BROKER_DEST),
                JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
            return;

        if (!deleteDestination(ba, destInfo.name, destInfo.type))
            return;

	BrokerDestListCObj destList = (BrokerDestListCObj)bDestCObj.getParent();
        app.getExplorer().removeFromParent(bDestCObj);
	// Force selection of BrokerDestListCObj
	app.getExplorer().select(((ConsoleObj)destList));
        app.getInspector().refresh();
    }

    private boolean deleteDestination(BrokerAdmin ba, String name, int type) {
        try {
	    ba.sendDestroyDestinationMessage(name, type);
            ba.receiveDestroyDestinationReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_DEST_DELETE, name, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_DEST_DELETE, name, ba.getKey()) +
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_DELETE_BROKER_DEST) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_DEST_DELETE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            return false;
        }
        return true;
    }

    private void doPurgeDestination(BrokerDestCObj bDestCObj) {

        BrokerAdmin ba = bDestCObj.getBrokerAdmin();
        DestinationInfo destInfo = bDestCObj.getDestinationInfo();

        int result = JOptionPane.showConfirmDialog(app.getFrame(),
                acr.getString(acr.Q_BROKER_PURGE_DEST, destInfo.name, ba.getKey()),
                acr.getString(acr.I_PURGE_MESSAGES),
                JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
	    return;

	if (!purgeDestination(ba, destInfo.name, destInfo.type))
	    return;
                    
	destInfo = queryDestinationInfo(ba, destInfo.name, destInfo.type);
	if (destInfo != null) {
	    bDestCObj.setDestinationInfo(destInfo);
	    app.getInspector().selectedObjectUpdated();
	}
    }

    private boolean purgeDestination(BrokerAdmin ba, String name, int type) {
        try {
            ba.sendPurgeDestinationMessage(name, type);
            ba.receivePurgeDestinationReplyMessage();
            app.getStatusArea().appendText
		(acr.getString(acr.S_BROKER_DEST_PURGE, name, ba.getKey()));

        } catch (BrokerAdminException baex) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_DEST_PURGE, name, ba.getKey()) +
                   printBrokerAdminExceptionDetails(baex),
                acr.getString(acr.I_PURGE_MESSAGES) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_DEST_PURGE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            return false;
        }
        return true;
    }

    private String printBrokerAdminExceptionDetails(BrokerAdminException bae)  {
	String basicMsg, detailMsg, fullMsg = null;

	basicMsg = getBrokerAdminExceptionBasicMsg(bae);
	detailMsg = getBrokerAdminExceptionMsgDetail(bae);

	if ((basicMsg == null) && (detailMsg == null))  {
	    return (acr.getString(acr.E_UNKNOWN_ERROR));
	}

	if (basicMsg != null) {
	    fullMsg = basicMsg;
	}

	if (detailMsg != null)  {
	    if (fullMsg == null)  {
		fullMsg = detailMsg;
	    } else {
		fullMsg = fullMsg + "\n" + detailMsg;
	    }
	}

	return (fullMsg);
    }

    private String getBrokerAdminExceptionMsgDetail(BrokerAdminException bae)  {
        Exception       e = bae.getLinkedException();
        String          s = bae.getBrokerErrorStr();

        String errorMessage = null;

        if (s != null)  {
            errorMessage = s;
        }

        if (e != null)  {
            if (errorMessage != null)
                errorMessage = errorMessage + e.getMessage();
            else
                errorMessage = e.getMessage();
        }

	return (errorMessage);
    }


    private String getBrokerAdminExceptionBasicMsg(BrokerAdminException bae)  {
	int		type = bae.getType();
	BrokerAdminConn ba = bae.getBrokerAdminConn();
	String		ret, brokerName, host, port;

	switch (type)  {
	case BrokerAdminException.MSG_SEND_ERROR:
	    ret = ar.getString(ar.E_JMQCMD_MSG_SEND_ERROR);
	break;

	case BrokerAdminException.MSG_REPLY_ERROR:
	    ret = ar.getString(ar.E_JMQCMD_MSG_REPLY_ERROR);
	break;

	case BrokerAdminException.CLOSE_ERROR:
	    ret = ar.getString(ar.E_JMQCMD_CLOSE_ERROR);
	break;

	case BrokerAdminException.PROB_GETTING_MSG_TYPE:
	    ret = ar.getString(ar.E_JMQCMD_PROB_GETTING_MSG_TYPE);
	break;

	case BrokerAdminException.PROB_GETTING_STATUS:
	    ret = ar.getString(ar.E_JMQCMD_PROB_GETTING_STATUS);
	break;

	case BrokerAdminException.REPLY_NOT_RECEIVED:
	    ret = acr.getString(acr.E_REPLY_NOT_RECEIVED);
	break;

	case BrokerAdminException.INVALID_OPERATION:
	    ret = ar.getString(ar.E_JMQCMD_INVALID_OPERATION);
	break;

	case BrokerAdminException.IGNORE_REPLY_IF_RCVD:
	    ret = acr.getString(acr.E_NO_REPLY_GIVEUP);
	break;

	case BrokerAdminException.BUSY_WAIT_FOR_REPLY:
	    Object	args[] = new Object [ 3 ];

	    brokerName = ba.getKey();
	    host = ba.getBrokerHost();
	    port = ba.getBrokerPort();

	    args[0] = brokerName;
	    args[1] = host;
	    args[2] = port;
	    ret = acr.getString(acr.I_BUSY_WAIT_FOR_REPLY, args);
	break;

	default:
	    ret = null;
	break;
	}

	return (ret);
    }

    /*
     * Load broker list from property file. See ConsoleBrokerAdminManager and
     * BrokerListProperties for details on the actual file loading.
     */
    private void loadBrokerList()  {
        ConsoleBrokerAdminManager baMgr = app.getBrokerListCObj().getBrokerAdminManager();

	try  {
	    baMgr.setFileName(BROKERLIST_FILENAME);
	    baMgr.readBrokerAdminsFromFile();
	} catch (Exception ex)  {
	    String errStr = acr.getString(acr.E_LOAD_BKR_LIST) + ex.getMessage();

	    /*
	     * Show popup to indicate that the loading failed.
	     */
	    JOptionPane.showOptionDialog(app.getFrame(), 
		    errStr,
                    acr.getString(acr.I_LOAD_BKR_LIST) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                            AdminConsoleResources.E_LOAD_BKR_LIST),
		    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    /*
	     * At this point, should we make sure the ConsoleBrokerAdminManager
	     * contains no obj stores ?
	     */
	}

	Vector v = baMgr.getBrokerAdmins();

	for (int i = 0; i < v.size(); i++) {
	    BrokerAdmin ba = (BrokerAdmin)v.get(i);
	    ConsoleObj brokerCObj= new BrokerCObj(ba);
	    /*
	     * Add code to populate broker node here if connected
	     */

	    app.getExplorer().addBroker(brokerCObj);
	}

	app.getInspector().refresh();
    }


    /*
     * Save broker list to property file. See ConsoleBrokerAdminManager and
     * BrokerListProperties for details on the actual file saving.
     */
    private void saveBrokerList()  {
        ConsoleBrokerAdminManager baMgr = app.getBrokerListCObj().getBrokerAdminManager();

	try  {
	    baMgr.setFileName(BROKERLIST_FILENAME);
	    baMgr.writeBrokerAdminsToFile();
	} catch (Exception ex)  {
	    String errStr = acr.getString(acr.E_SAVE_BKR_LIST) + ex.getMessage();
	    //String titles[] = {acr.getString(acr.I_DIALOG_CLOSE),
	    //		       acr.getString(acr.I_DIALOG_DO_NOT_SHOW_AGAIN)};

	    /*
	     * Inform the user that the save failed.
	     */
	    
	    //int response = JOptionPane.showOptionDialog(app.getFrame(),
            //    errStr,
            //    acr.getString(acr.I_BROKER) + ": " +
            //        acr.getString(acr.I_ERROR_CODE,
            //            AdminConsoleResources.E_SAVE_BKR_LIST),
            //    JOptionPane.YES_NO_OPTION,
            //    JOptionPane.ERROR_MESSAGE, null, close, close[0]);

	    JOptionPane.showOptionDialog(app.getFrame(), 
		errStr,
                acr.getString(acr.I_BROKER) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                        AdminConsoleResources.E_SAVE_BKR_LIST),
		JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	   
	}
    }

    public void handleConsoleActionEvents(ConsoleActionEvent cae) {
        int type 		 = cae.getType();

	switch (type)  {
	case ConsoleActionEvent.REFRESH:
	    doRefresh();
            app.getInspector().refresh();
	break;
	}
    }

    private void doRefresh() {
	ConsoleObj selObj = app.getSelectedObj();

	if (selObj instanceof BrokerCObj)  {
	    BrokerCObj bCObj = (BrokerCObj)selObj;

	    /*
	     * Not necessary since the broker should always be running
	     * or else exception must have been caught and have been taken care of.
	     * if (!isLatestBrokerAvailable(bCObj)) return;
	     */

            /*
	     * Refresh BrokerServiceListCObj and BrokerDestListCObj for
	     * this BrokerCObj.
	     *
	     * Refresh the destination list only if the service list refresh
	     * was successful.
	     */
            if (refreshBrokerServiceListCObj(bCObj.getBrokerServiceListCObj())) {
		if (refreshBrokerDestListCObj(bCObj.getBrokerDestListCObj()))
                    app.getStatusArea().appendText(acr.getString(acr.S_BROKER_REFRESH,
                        bCObj.toString()));
	    }

	} else if (selObj instanceof BrokerServiceListCObj)  {
            BrokerServiceListCObj  bSvclCObj = (BrokerServiceListCObj)selObj;
            BrokerCObj         	   bCObj = bSvclCObj.getBrokerCObj();

            /*
             * Not necessary since the broker should always be running
             * or else exception must have been caught and have been taken care of.
             * if (!isLatestBrokerAvailable(bCObj)) return;
             */
            if (refreshBrokerServiceListCObj(bSvclCObj))
                app.getStatusArea().appendText(acr.getString(acr.S_BROKER_REFRESH_SVCLIST,
                        bCObj.toString()));

	} else if (selObj instanceof BrokerServiceCObj)  {
            BrokerServiceCObj  	   bSvcCObj = (BrokerServiceCObj)selObj;
            BrokerCObj         	   bCObj = bSvcCObj.getBrokerCObj();
	    BrokerServiceListCObj  bSvclCObj = bCObj.getBrokerServiceListCObj();

            /*
             * Not necessary since the broker should always be running
             * or else exception must have been caught and have been taken care of.
             * if (!isLatestBrokerAvailable(bCObj)) return;
             */
            if (refreshBrokerServiceListCObj(bSvclCObj)) 
                app.getStatusArea().appendText(acr.getString(acr.S_BROKER_REFRESH_SVCLIST,
                        bCObj.toString()));

	} else if (selObj instanceof BrokerDestListCObj)  {
            BrokerDestListCObj	bDestlCObj = (BrokerDestListCObj)selObj;
	    BrokerCObj		bCObj = bDestlCObj.getBrokerCObj();

            /*
             * Not necessary since the broker should always be running
             * or else exception must have been caught and have been taken care of.
             * if (!isLatestBrokerAvailable(bCObj)) return;
             */
            if (refreshBrokerDestListCObj(bDestlCObj))
                app.getStatusArea().appendText(
		    acr.getString(acr.S_BROKER_REFRESH_DESTLIST, bCObj.toString()));

	} else if (selObj instanceof BrokerDestCObj)  {
            BrokerDestCObj	bDestCObj = (BrokerDestCObj)selObj;
	    BrokerCObj		bCObj = bDestCObj.getBrokerCObj();
            BrokerDestListCObj	bDestlCObj = bCObj.getBrokerDestListCObj();

            /*
             * Not necessary since the broker should always be running
             * or else exception must have been caught and have been taken care of.
             * if (!isLatestBrokerAvailable(bCObj)) return;
             */
            if (refreshBrokerDestListCObj(bDestlCObj))
                app.getStatusArea().appendText(acr.getString(acr.S_BROKER_REFRESH_DESTLIST,
			bCObj.toString()));
        }
    }

   /*
    private boolean isLatestBrokerAvailable(BrokerCObj bCObj) {
        BrokerAdmin ba = bCObj.getBrokerAdmin();
        
         // Check to see if BrokerAdmin can connect to the broker by
         // sending a hello message.  It acts like a ping (verified w/ Joe.)
        
        try {
            ba.sendHelloMessage();
            ba.receiveHelloReplyMessage();
	    return true;

	} catch (BrokerAdminException bae) {
            JOptionPane.showOptionDialog(app.getFrame(),
                acr.getString(acr.E_BROKER_NOT_CONNECTED, ba.getKey()) +
		   printBrokerAdminExceptionDetails(bae),
                acr.getString(acr.I_BROKER_REFRESH) + ": " +
                   acr.getString(acr.I_ERROR_CODE,
                      AdminConsoleResources.E_BROKER_NOT_CONNECTED),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);

	    ba.setIsConnected(false);
            bCObj.setBrokerProps(null);
         
             // Remove destinations and services from this broker's node
             // hierarchy.
             // 
            clearBroker(bCObj);
            app.getExplorer().nodeChanged((DefaultMutableTreeNode)bCObj);
            app.getInspector().refresh();

            // Update menu items, buttons.
            controller.setActions(bCObj);
	}
	return false;
    }
    */
}
