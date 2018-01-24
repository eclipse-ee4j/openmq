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
 * @(#)ObjAdminHandler.java	1.45 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import com.sun.messaging.InvalidPropertyException;
import com.sun.messaging.InvalidPropertyValueException;

import java.io.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ConsoleActionEvent;
import com.sun.messaging.jmq.admin.objstore.AuthenticationException;
import com.sun.messaging.jmq.admin.objstore.NameAlreadyExistsException;
import com.sun.messaging.jmq.admin.objstore.NameNotFoundException;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.objstore.ObjStoreException;

/** 
 * Handles the object administration tasks delegated by the
 * AController class.
 *
 * @see AController
 */
public class ObjAdminHandler implements AdminEventListener  {

    private static String OBJSTORELIST_FILENAME		= "objstorelist.properties";

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    private AdminApp	app;
    private AController	controller;

    private ObjStoreAddDialog objStoreAddDialog = null;
    private ObjStorePropsDialog objStorePropsDialog = null;
    private ObjStorePasswdDialog objStorePasswdDialog = null;
    private ObjStoreDestAddDialog objStoreDestAddDialog = null;
    private ObjStoreDestPropsDialog objStoreDestPropsDialog = null;
    private ObjStoreConFactoryAddDialog objStoreConFactoryAddDialog = null;
    private ObjStoreConFactoryPropsDialog objStoreConFactoryPropsDialog = null;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public ObjAdminHandler(AdminApp app, AController controller) {
	this.app = app;
	this.controller = controller;
    } 

    public void init()  {
        loadObjStoreList();
    }

    /*
     * BEGIN INTERFACE AdminEventListener
     */
    public void adminEventDispatched(AdminEvent e)  {
	int id;
	ConsoleObj selObj;
	
	if (e instanceof DialogEvent)  {
	    handleDialogEvents((DialogEvent)e);
	} else if (e instanceof ObjAdminEvent)  {
	    handleObjAdminEvents((ObjAdminEvent)e);
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
	    if (selObj instanceof ObjStoreListCObj) {
		if (objStoreAddDialog == null) {
                    objStoreAddDialog = new ObjStoreAddDialog(app.getFrame(), 
							(ObjStoreListCObj)selObj);
                    objStoreAddDialog.addAdminEventListener(this);
                    objStoreAddDialog.setLocationRelativeTo(app.getFrame());
	        }
                objStoreAddDialog.show();

	    } else if (selObj instanceof ObjStoreDestListCObj) {
		if (objStoreDestAddDialog == null) {
                    objStoreDestAddDialog = new ObjStoreDestAddDialog(app.getFrame());
                    objStoreDestAddDialog.addAdminEventListener(this);
                    objStoreDestAddDialog.setLocationRelativeTo(app.getFrame());
	        }

	        // This should't happen - menu item should be disabled.
		ObjStore os = ((ObjStoreDestListCObj)selObj).getObjStore();
		if (!os.isOpen()) {
	    	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OBJSTORE_NOT_CONNECTED, os.getID()),
			acr.getString(acr.I_ADD_OBJSTORE_DEST) + ": " +
			    acr.getString(acr.I_ERROR_CODE,
			    	AdminConsoleResources.E_OBJSTORE_NOT_CONNECTED),
			JOptionPane.YES_NO_OPTION,
			JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		}
                objStoreDestAddDialog.show((ObjStoreDestListCObj)selObj);

	    } else if (selObj instanceof ObjStoreConFactoryListCObj) {
		if (objStoreConFactoryAddDialog == null) {
                    objStoreConFactoryAddDialog = new ObjStoreConFactoryAddDialog(app.getFrame());
                    objStoreConFactoryAddDialog.addAdminEventListener(this);
                    objStoreConFactoryAddDialog.setLocationRelativeTo(app.getFrame());
	        }
	        // This should't happen - menu item should be disabled.
		ObjStore os = ((ObjStoreConFactoryListCObj)selObj).getObjStore();
		if (!os.isOpen()) {
	    	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OBJSTORE_NOT_CONNECTED, os.getID()),
			acr.getString(acr.I_ADD_OBJSTORE_CF) + ": " + 
			    acr.getString(acr.I_ERROR_CODE,
			    	AdminConsoleResources.E_OBJSTORE_NOT_CONNECTED),
			JOptionPane.YES_NO_OPTION,
			JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		    return;
		}
                objStoreConFactoryAddDialog.show((ObjStoreConFactoryListCObj)selObj);

            }
	break;

        case DialogEvent.DELETE_DIALOG:
	    if (selObj instanceof ObjStoreCObj) {
		doDeleteObjStore(selObj);
	    } else if (selObj instanceof ObjStoreDestCObj) {
		doDeleteDestination(selObj);
	    } else if (selObj instanceof ObjStoreConFactoryCObj) {
		doDeleteConnFactory(selObj);
            } 
        break;

	case DialogEvent.PROPS_DIALOG:
	    if (selObj instanceof ObjStoreCObj) {
		if (objStorePropsDialog == null) {
		    ObjStoreCObj osCObj = (ObjStoreCObj)selObj;
		    ObjStoreListCObj oslCObj = (ObjStoreListCObj)osCObj.getParent();
                    objStorePropsDialog = new ObjStorePropsDialog(app.getFrame(),
						oslCObj);
                    objStorePropsDialog.addAdminEventListener(this);
                    objStorePropsDialog.setLocationRelativeTo(app.getFrame());
	        }
                objStorePropsDialog.show((ObjStoreCObj)selObj);
	    } else if (selObj instanceof ObjStoreDestCObj) {
		if (objStoreDestPropsDialog == null) {
                    objStoreDestPropsDialog = new ObjStoreDestPropsDialog
						(app.getFrame());
                    objStoreDestPropsDialog.addAdminEventListener(this);
                    objStoreDestPropsDialog.setLocationRelativeTo(app.getFrame());
	        }
	        // This should't happen - menu item should be disabled.
		ObjStore os = ((ObjStoreDestCObj)selObj).getObjStore();
		if (!os.isOpen()) {
	    	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OBJSTORE_NOT_CONNECTED, os.getID()),
			acr.getString(acr.I_OBJSTORE_DEST_PROPS) + ": " +
			    acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_OBJSTORE_NOT_CONNECTED),
			JOptionPane.YES_NO_OPTION,
			JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		    return;
		}
                objStoreDestPropsDialog.show((ObjStoreDestCObj)selObj);
	    } else if (selObj instanceof ObjStoreConFactoryCObj) {
		if (objStoreConFactoryPropsDialog == null) {
                    objStoreConFactoryPropsDialog = 
			new ObjStoreConFactoryPropsDialog(app.getFrame());
                    objStoreConFactoryPropsDialog.addAdminEventListener(this);
                    objStoreConFactoryPropsDialog.setLocationRelativeTo
							(app.getFrame());
	        }
	        // This should't happen - menu item should be disabled.
		ObjStore os = ((ObjStoreConFactoryCObj)selObj).getObjStore();
		if (!os.isOpen()) {
	    	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OBJSTORE_NOT_CONNECTED, os.getID()),
			acr.getString(acr.I_OBJSTORE_CF_PROPS) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_OBJSTORE_NOT_CONNECTED),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		    return;
		}
                objStoreConFactoryPropsDialog.show((ObjStoreConFactoryCObj)selObj);
	    }
	break;

        case DialogEvent.CONNECT_DIALOG:
            if (selObj instanceof ObjStoreCObj) {
		doConnectObjStore(selObj);
	    }
        break;

        case DialogEvent.DISCONNECT_DIALOG:
            if (selObj instanceof ObjStoreCObj) {
		doDisconnectObjStore(selObj);
	    }
        break;

        case DialogEvent.HELP_DIALOG:
	    JOptionPane.showOptionDialog(app.getFrame(), 
		acr.getString(acr.I_NO_HELP),
		acr.getString(acr.I_HELP_TEXT),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, close, close[0]);
	break;

        case DialogEvent.SHUTDOWN_DIALOG:
        case DialogEvent.RESTART_DIALOG:
        case DialogEvent.PAUSE_DIALOG:
        case DialogEvent.RESUME_DIALOG:
	    /*
	     * No shutdown/restart/pause/resume for obj admin
	     */
        break;

        }
    }

    public void handleObjAdminEvents(ObjAdminEvent oae) {
	ConsoleObj      selObj = app.getSelectedObj();
        int	 	type = oae.getType();

        switch (type)  {
	case ObjAdminEvent.ADD_OBJSTORE:
	    doAddObjStore(oae, selObj);
	    break;

	case ObjAdminEvent.UPDATE_OBJSTORE:
	    doUpdateObjStore(oae, selObj);
	    break;

	case ObjAdminEvent.ADD_DESTINATION:
	    doAddDestination(oae, selObj);
	    break;

	case ObjAdminEvent.UPDATE_DESTINATION:
	    doUpdateDestination(oae, selObj);
	    break;

	case ObjAdminEvent.ADD_CONN_FACTORY:
	    doAddConnFactory(oae, selObj);
	    break;

	case ObjAdminEvent.UPDATE_CONN_FACTORY:
	    doUpdateConnFactory(oae, selObj);
	    break;

	case ObjAdminEvent.UPDATE_CREDENTIALS:
	    doUpdateCredentials(oae, selObj);
	    break;

	}

    }


    private void doAddObjStore(ObjAdminEvent oae, ConsoleObj selObj) {

	ObjStoreAttrs osa = oae.getObjStoreAttrs();
	ObjStoreManager osMgr = app.getObjStoreListCObj().getObjStoreManager();
	boolean connect = oae.isConnectAttempt();
	ConsoleObj osCObj = null;

	/*
	 * Create store and connect (if requested).
	 */
	ObjStore os = createStore(osMgr, osa, connect);

	/*
	 * If valid object store, then now try reading in any existing objects. 
	 */
	if (os != null)  {
 	    osCObj = new ObjStoreCObj(os);  
	    if (os.isOpen()) {
	        readObjStore(osCObj, osMgr, os);
	    }
	}    
	/*
	 * If os is still valid, then add it to the tree list.
	 */
	if (osCObj != null) {
	    app.getExplorer().addObjStore(osCObj);
	    app.getInspector().refresh();
	    app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_ADD, 
							 osCObj.toString()));
	}

	/*
	 * Bring down the dialog if successful,
	 * otherwise keep it up.
	 */
	if (oae.isOKAction() && os != null)  {
	    objStoreAddDialog.hide();
	}

	saveObjStoreList();

    }

    private void doUpdateObjStore(ObjAdminEvent oae, ConsoleObj selObj) {

	ObjStoreAttrs osa = oae.getObjStoreAttrs();
	ObjStoreManager osMgr = app.getObjStoreListCObj().getObjStoreManager();
	boolean connect = oae.isConnectAttempt();
	ObjStore prevOs = ((ObjStoreCObj)selObj).getObjStore();
	String newName = oae.getObjStoreID();

	/*
	 * Create/Update a new store and connect (if requested).
	 */
	ObjStore os = updateStore(prevOs, newName, osMgr, osa, connect);

	/*
	 * If valid object store, then now try reading in any existing objects. 
	 */
	if (os == null) {
	    // Don't do anything, just keep the old os.
	    return;
	} else {
	    /*
	     * Read in the objects from the os.
	     */
	    if (os.isOpen()) {
	        readObjStore(selObj, osMgr, os);
	    }
	}  

	/*
	 * If os is still valid, then update it cobj with the new obj store.
	 */
	((ObjStoreCObj)selObj).setObjStore(os);
	/*
	 * We know that 0 is the dest list and 1 is the con factory list.
	 */
	ObjStoreDestListCObj     destListCObj = 
			(ObjStoreDestListCObj)selObj.getChildAt(0);
	ObjStoreConFactoryListCObj cfListCObj = 
			(ObjStoreConFactoryListCObj)selObj.getChildAt(1);
	destListCObj.setObjStore(os);
	cfListCObj.setObjStore(os);

        app.getExplorer().nodeChanged((DefaultMutableTreeNode)selObj);
	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_UPDATE, 
				   	selObj.toString()));
	app.getInspector().selectedObjectUpdated();

	// Update menu items, buttons.
	controller.setActions(selObj);

	/*
	 * If check box to connect is not checked, then
	 * remove all destination/connection factory objects from this
	 * object store node hierarchy
	 */
	if (!connect) {
            clearStore(selObj);
	}

	/*
	 * Bring down the dialog if successful,
	 * otherwise keep it up.
	 */
	if (oae.isOKAction() && os != null)  {
	    objStorePropsDialog.hide();
	}

	saveObjStoreList();
    }

    /*
     * Read in the objects from the obj store and store them
     * in the console object.
     */
    private boolean readObjStore(ConsoleObj osCObj, ObjStoreManager osMgr, ObjStore os) {

	Vector v = null;

	try {
	    v = os.list();
        } catch (Exception e) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		acr.getString(acr.E_OBJSTORE_LIST, os.getID(), e.toString()),
		acr.getString(acr.I_OBJSTORE) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    	AdminConsoleResources.E_OBJSTORE_LIST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    // Close the obj store connection.
	    try {
		os.close();
	    } catch (Exception ex) { }
	    // No Changes
  	    return false;
        }

	/*
	 * We know that 0 is the dest list and 1 is the con factory list.
	 */
	ObjStoreDestListCObj     destListCObj = 
			(ObjStoreDestListCObj)osCObj.getChildAt(0);
	ObjStoreConFactoryListCObj cfListCObj = 
			(ObjStoreConFactoryListCObj)osCObj.getChildAt(1);
	/*
	 * We're re-reading it in so clear out whatever's there.
	 */
	ConsoleObj node;
	for (Enumeration e = destListCObj.children(); e.hasMoreElements(); ) {
	    node = (ConsoleObj)e.nextElement();
	    app.getExplorer().removeFromParent(node);
	    e = destListCObj.children();  // Need to set e again after a remove
	}

	for (Enumeration e = cfListCObj.children(); e.hasMoreElements(); ) {
	    node = (ConsoleObj)e.nextElement();
	    app.getExplorer().removeFromParent(node);
	    e = cfListCObj.children();  // Need to set e again after a remove
	}

	/*
	 * Re-read the objects into the nodes.
	 */
	for (int i = 0; i < v.size(); i++) {
            NameClassPair obj = (NameClassPair)v.get(i);
	    String lookupName = ((NameClassPair)obj).getName();
	    Object object = null;
	    try {
		object = os.retrieve(lookupName);
	    } catch (Exception e) {
	        JOptionPane.showOptionDialog(app.getFrame(), 
		    acr.getString(acr.E_RETRIEVE_OBJECT, lookupName, os.getID()),
		    acr.getString(acr.I_OBJSTORE) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                    	    AdminConsoleResources.E_RETRIEVE_OBJECT),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		continue;
	    }

            if ((com.sun.messaging.Topic.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.Queue.class.getName().
                 equals(obj.getClassName()))) {

 	        ObjStoreDestCObj destCObj = 
			new ObjStoreDestCObj((ObjStoreCObj)osCObj, lookupName, object);

	   	app.getExplorer().addToParent(destListCObj, destCObj);

	    }
            else if ((com.sun.messaging.TopicConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                     (com.sun.messaging.QueueConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                     (com.sun.messaging.ConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                     (com.sun.messaging.XATopicConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                     (com.sun.messaging.XAQueueConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                     (com.sun.messaging.XAConnectionFactory.class.getName().
                      equals(obj.getClassName()))) {

 	        ObjStoreConFactoryCObj cfCObj = 
			new ObjStoreConFactoryCObj((ObjStoreCObj)osCObj, lookupName, 
						    object);
		app.getExplorer().addToParent(cfListCObj, cfCObj);

            }
	}

	return true;
    }

    private void clearStore(ConsoleObj osCObj) {
	/*
	 * We know that 0 is the dest list and 1 is the con factory list.
	 */
	ObjStoreDestListCObj     destListCObj = 
			(ObjStoreDestListCObj)osCObj.getChildAt(0);
	ObjStoreConFactoryListCObj cfListCObj = 
			(ObjStoreConFactoryListCObj)osCObj.getChildAt(1);
	/*
	 * We're re-reading it in so clear out whatever's there.
	 */
	ConsoleObj node;
	for (Enumeration e = destListCObj.children(); e.hasMoreElements(); ) {
	    node = (ConsoleObj)e.nextElement();
	    app.getExplorer().removeFromParent(node);
	    e = destListCObj.children();  // Need to set e again after a remove
	}

	for (Enumeration e = cfListCObj.children(); e.hasMoreElements(); ) {
	    node = (ConsoleObj)e.nextElement();
	    app.getExplorer().removeFromParent(node);
	    e = cfListCObj.children();  // Need to set e again after a remove
	}

    }

    private ObjStore createStore(ObjStoreManager osMgr, ObjStoreAttrs osa,
				 boolean attemptToConnect) {
	ObjStore os = null;

	if (osMgr == null || osa == null)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_PROCESS),
			acr.getString(acr.I_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_OS_PROCESS),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return (null);
	}

	/*
	 * Create ObjStore
	 */
	try  {
	    os = osMgr.createStore(osa);
        } catch (Exception ex) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			ex.toString(),
			acr.getString(acr.I_OBJSTORE),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return null;
	}

	/*
	 * Now try connecting to it (if they want to try).
	 * Then read in the objects from the store.
	 */
	if (attemptToConnect) {

	    try {
	        if (os != null)
	            os.open();
	    } catch (Exception e) {
	        JOptionPane.showOptionDialog(app.getFrame(), 
		    acr.getString(acr.E_INSUFFICIENT_INFO, e.toString()),
		    acr.getString(acr.I_OBJSTORE) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                        	AdminConsoleResources.E_INSUFFICIENT_INFO),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		os = null;
		// Delete it.
		try {
		    osMgr.destroyStore(osa.getID());
		} catch (Exception ex) { }
	    }
	}

	return (os);
    }

    private ObjStore updateStore(ObjStore prevOs, String newName, 
				 ObjStoreManager osMgr, 
				 ObjStoreAttrs osa, boolean attemptToConnect) {
	ObjStore os = null;
	boolean created = false;

	if (osMgr == null || osa == null)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_PROCESS),
			acr.getString(acr.I_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_OS_PROCESS),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return (null);
	}

	/*
	 * Update ObjStore
	 */
	ObjStoreAttrs prevAttrs = prevOs.getObjStoreAttrs();
	os = osMgr.getStore(prevOs.getID());

	if (os == null) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_PROCESS),
			acr.getString(acr.I_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_OS_PROCESS),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	}

	//
	// If we're changing the OS name, then we need to
	// delete old os and create new one.
	// Otherwise, just set new attrs on the os.
	//
	try  {
	    if (newName != null && os != null && !newName.equals(os.getID())) {
	        os = osMgr.createStore(osa);
		created = true;
	    } else if (os != null)  {
	        os.setObjStoreAttrs(osa);
	    }
        } catch (Exception ex) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		ex.toString(),
		acr.getString(acr.I_OBJSTORE_PROPS),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return null;
	}

	/*
	 * Now try connecting to it (if they want to try).
	 * Then read in the objects from the store.
	 */
	if (attemptToConnect) {
	    try {
	        if (os != null) {
	            os.open();
	        }
	    } catch (Exception e) {
	        JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_INSUFFICIENT_INFO, e.toString()),
			acr.getString(acr.I_OBJSTORE_PROPS) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_INSUFFICIENT_INFO),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		// Don't update it, undo everything
		try {
		    if (created) {
	    		osMgr.destroyStore(osa.getID());
		    } else {
			// Reset old attrs back
			os.setObjStoreAttrs(prevAttrs);
		    } 
 		} catch (Exception ex) { }
		    return null;
	    }
	}

	//
	// If successful in updating, now we can delete the old one.
	//
	if (os != null && created) {
	    try {
		if (prevOs.isOpen())
		    prevOs.close();
	    	osMgr.destroyStore(prevOs.getID());

 	        if (!attemptToConnect && os.isOpen())
		    os.close();

	    } catch (Exception ex) { }
	}

	//
	// If the os is open and unchecked connect box, then close it.
	//
 	if (os != null && !attemptToConnect && os.isOpen()) {
	    try {
		os.close();
	    } catch (Exception ex) { }
	}
	return (os);
    }

    private void doConnectObjStore(ConsoleObj selObj) {

	ObjStore os =((ObjStoreCObj)selObj).getObjStore();

	if (os.isOpen()) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_ALREADY_CONNECTED, 
				      selObj.toString()),
			acr.getString(acr.I_CONNECT_OBJSTORE) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_OS_ALREADY_CONNECTED),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}


        /*
         *Retrieve the original ObjStoreAttrs that the user input.
         *This DOES NOT read any jndi property files processed by jndi
         * since this is done PRIOR to creating the initialContext.
         */
        ObjStoreAttrs osa = os.getObjStoreAttrs();
        Vector missingAuthInfo = os.checkAuthentication(osa);
        int missingAuthInfoSize = missingAuthInfo.size();

        boolean carriageReturnNeeded = false;
        if (missingAuthInfo != null && missingAuthInfoSize > 0) {
	    if (objStorePasswdDialog == null && missingAuthInfoSize > 0) {
                objStorePasswdDialog = new ObjStorePasswdDialog(app.getFrame());
                objStorePasswdDialog.addAdminEventListener(this);
                objStorePasswdDialog.setLocationRelativeTo(app.getFrame());
	    }
	    if (missingAuthInfoSize > 0)
                objStorePasswdDialog.show(os, missingAuthInfo);
        } else {
	    boolean success = finishDoConnectObjStore(selObj, os);
	    if (success) {
	  	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_CONNECT, 
			  	           selObj.toString()));
	    }
	}

    }

    private boolean finishDoConnectObjStore(ConsoleObj selObj, ObjStore os) {

	try {
	    os.open();
	} catch (AuthenticationException ae) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_PASSWORD),
			acr.getString(acr.I_CONNECT_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_PASSWORD),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    closeObjStore(os, selObj);
	    return false;
	} catch (Exception e) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_UNABLE_CONNECT, selObj.toString(),
				      e.toString()),
			acr.getString(acr.I_CONNECT_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_OS_UNABLE_CONNECT),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    closeObjStore(os, selObj);
	    return false;
	}

	// Read in the objects.
	ObjStoreManager osMgr = app.getObjStoreListCObj().getObjStoreManager();
	boolean success = readObjStore(selObj, osMgr, os);
	if (success) {
            app.getExplorer().nodeChanged((DefaultMutableTreeNode)selObj);
	    app.getInspector().selectedObjectUpdated();
	    // XXX This causes selection to go away if selected in inspector
	    app.getInspector().refresh();
	}

	// Update menu items, buttons.
	controller.setActions(selObj);

	return true;
    }


    private void doDisconnectObjStore(ConsoleObj selObj) {

	ObjStore os =((ObjStoreCObj)selObj).getObjStore();

	if (!os.isOpen()) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		acr.getString(acr.E_OS_ALREADY_DISCONNECTED,
			      selObj.toString()),
		acr.getString(acr.I_DISCONNECT_OBJSTORE) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    	AdminConsoleResources.E_OS_ALREADY_DISCONNECTED),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}

	try {
	    if (os != null)
	        os.close();
	} catch (Exception e) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OS_UNABLE_DISCONNECT,
				      selObj.toString()),
			acr.getString(acr.I_DISCONNECT_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	AdminConsoleResources.E_OS_UNABLE_DISCONNECT),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}

	/*
	 * Remove all destination/connection factory objects from this
	 * object store node hierarchy
	 */
        clearStore(selObj);

        app.getExplorer().nodeChanged((DefaultMutableTreeNode)selObj);
	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_DISCONNECT, 
				       selObj.toString()));
	// XXX This causes selection to go away if selected in inspector
	app.getInspector().refresh();
        app.getInspector().selectedObjectUpdated();


	// Update menu items, buttons.
	controller.setActions(selObj);
    }

    private void doDeleteObjStore(ConsoleObj selObj) {

    	int result = JOptionPane.showConfirmDialog(app.getFrame(), 
			acr.getString(acr.Q_OBJSTORE_DELETE, selObj.toString()),
			acr.getString(acr.I_DELETE_OBJSTORE),
			JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
	    return;

	/*
	 * Disconnect and delete the object store.
	 */
	ObjStore os = ((ObjStoreCObj)selObj).getObjStore();
	try {
	    os.close();
	} catch (Exception e) { }

	ObjStoreManager osMgr = app.getObjStoreListCObj().getObjStoreManager();
	try {
	    osMgr.destroyStore(os.getID());
	} catch (Exception e) { }

	/*
	 * Remove the node from the tree.
	 */
	app.getExplorer().removeFromParent(selObj);
	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_DELETE, 
				       os.getID()));

	/*
	 * Clear selection if the selection is from the explorer.
	 * Otherwise, refresh the inspector.
	 */
/*
	if (fromExplorer)
	    controller.clearSelection();
	else 
	    app.getInspector().refresh();
*/
	controller.clearSelection();

	saveObjStoreList();
 		
    }

    private void doDeleteDestination(ConsoleObj selObj) {

	ObjStore os = ((ObjStoreDestCObj)selObj).getObjStore();
    	int result = JOptionPane.showConfirmDialog(app.getFrame(), 
			acr.getString(acr.Q_DEST_OBJ_DELETE,
				      selObj.toString(), os.getID()),
			acr.getString(acr.I_DELETE_OBJSTORE_DEST),
			JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
	    return;

	/*
	 * Disconnect and delete the object store.
	 */
	String lookupName = selObj.toString();
	try {
	    os.delete(lookupName);
	} catch (Exception e) { 
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_DELETE_DEST_OBJ, selObj.toString(),
				      e.toString()),
			acr.getString(acr.I_DELETE_OBJSTORE_DEST) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                AdminConsoleResources.E_DELETE_DEST_OBJ),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_DELETE_DEST,
		         		selObj.toString(), os.getID()));
	ObjStoreDestListCObj destList = (ObjStoreDestListCObj)selObj.getParent();
	app.getExplorer().removeFromParent(selObj);
	// Force selection of ObjStoreDestListCObj
	app.getExplorer().select(((ConsoleObj)destList));
	app.getInspector().refresh();

    }

    private void doDeleteConnFactory(ConsoleObj selObj) {

	ObjStore os = ((ObjStoreConFactoryCObj)selObj).getObjStore();
    	int result = JOptionPane.showConfirmDialog(app.getFrame(), 
			acr.getString(acr.Q_CF_OBJ_DELETE,
				      selObj.toString(), os.getID()),
			acr.getString(acr.I_DELETE_OBJSTORE_CF),
			JOptionPane.YES_NO_OPTION);

	if (result == JOptionPane.NO_OPTION)
	    return;

	/*
	 * Disconnect and delete the object store.
	 */
	String lookupName = selObj.toString();
	try {
	    os.delete(lookupName);
	} catch (Exception e) { 
	    JOptionPane.showOptionDialog(app.getFrame(), 
		acr.getString(acr.E_DELETE_CF_OBJ, selObj.toString(),
			      e.toString()),
		acr.getString(acr.I_DELETE_OBJSTORE_CF) + ": " +
                              acr.getString(acr.I_ERROR_CODE,
                              AdminConsoleResources.E_DELETE_CF_OBJ),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_DELETE_CF,
		         		selObj.toString(), os.getID()));
	ObjStoreConFactoryListCObj cfList = (ObjStoreConFactoryListCObj)selObj.getParent();
	app.getExplorer().removeFromParent(selObj);
	// Force selection of ObjStoreDestListCObj
	app.getExplorer().select(((ConsoleObj)cfList));
	app.getInspector().refresh();

    }

    private void doUpdateDestination(ObjAdminEvent oae, ConsoleObj selObj) {

	ObjStore os = oae.getObjStore();
	String lookupName = oae.getLookupName();
	boolean readOnly = oae.isReadOnly();

	Object currentObj = ((ObjStoreDestCObj)selObj).getObject();
	Object updatedObject = updateObject(currentObj, oae.getDestinationType(),
				oae.getObjProperties(), readOnly);
	if (updatedObject == null)
	    return;

	try {
	    os.add(lookupName, updatedObject, true);
        } catch (NameAlreadyExistsException naee) {
            // Should never happen, since we pass true to add

        } catch (Exception e)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		e.toString(),
		acr.getString(acr.I_OBJSTORE_DEST_PROPS),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	/*
	 * Now replace this updated object in the tree.
	 */
	((ObjStoreDestCObj)selObj).setObject(updatedObject);

	//ConsoleObj parent = (ConsoleObj)selObj.getParent();
	//app.getExplorer().removeFromParent(selObj);

 	//ObjStoreDestCObj destCObj = new ObjStoreDestCObj(os, lookupName, updatedObject);
	//app.getExplorer().addToParent(parent, destCObj);

	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_UPDATE_DEST,
		         	       selObj.toString(), os.getID()));
	app.getInspector().selectedObjectUpdated();

	if (oae.isOKAction())  {
	    objStoreDestPropsDialog.hide();
        }
    }

    private Object updateObject(Object object, int type,
                        Properties objProps, boolean readOnly) {

        Object updatedObject = null;
        String titleId = acr.I_OBJSTORE;
        String readOnlyValue = null;

	if (readOnly)
	    readOnlyValue = "true";
	else
	    readOnlyValue = "false";
	    
	try {

            if (type == ObjAdminEvent.QUEUE) {
		titleId = acr.I_OBJSTORE_DEST_PROPS;
                updatedObject = JMSObjFactory.updateQueue(object, objProps,
							  readOnlyValue);
            } else if (type == ObjAdminEvent.TOPIC) {
	  	titleId = acr.I_OBJSTORE_DEST_PROPS;
                updatedObject = JMSObjFactory.updateTopic(object, objProps,
							  readOnlyValue);
            } else if (type == ObjAdminEvent.QCF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateQueueConnectionFactory
                                                 (object, objProps, readOnlyValue);
            } else if (type == ObjAdminEvent.TCF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateTopicConnectionFactory
                                                 (object, objProps, readOnlyValue);
            } else if (type == ObjAdminEvent.CF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateConnectionFactory
                                                 (object, objProps, readOnlyValue);
            } else if (type == ObjAdminEvent.XAQCF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateXAQueueConnectionFactory
                                                 (object, objProps, readOnlyValue);
            } else if (type == ObjAdminEvent.XATCF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateXATopicConnectionFactory
                                                 (object, objProps, readOnlyValue);
            } else if (type == ObjAdminEvent.XACF) {
		titleId = acr.I_OBJSTORE_CF_PROPS;
                updatedObject = JMSObjFactory.updateXAConnectionFactory
                                                 (object, objProps, readOnlyValue);
            }

	} catch (Exception e) {
	    handleExceptions(e, titleId);
	    return null;
	}

        return updatedObject;

    }

    private void doUpdateCredentials(ObjAdminEvent oae, ConsoleObj selObj) {
	ObjStore os = oae.getObjStore();

	boolean success = finishDoConnectObjStore(selObj, os);
	if (success) {
	    app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_CONNECT, 
			  	           selObj.toString()));
	}

	// Remove missing info that we just added.
	ObjStoreAttrs osa = oae.getObjStoreAttrs();
	Vector missingInfo = oae.getMissingAuthInfo();

	for (int i = 0; i < missingInfo.size(); i++) {
	    String key = (String)missingInfo.elementAt(i);
	    osa.remove(key);
	}
	try {
	    os.setObjStoreAttrs(osa);
	} catch (ObjStoreException ose) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		ose.toString(),
		acr.getString(acr.I_OBJSTORE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	}

	// Dismiss window
	
	if (success && oae.isOKAction())  {
	    objStorePasswdDialog.hide();
	}
    }

    private void doUpdateConnFactory(ObjAdminEvent oae, ConsoleObj selObj) {

	ObjStore os = oae.getObjStore();
	String lookupName = oae.getLookupName();
	boolean readOnly = oae.isReadOnly();

	Object currentObj = ((ObjStoreConFactoryCObj)selObj).getObject();
	Object updatedObject = updateObject(currentObj, oae.getFactoryType(),
					    oae.getObjProperties(), readOnly);
	if (updatedObject == null)
	    return;

	try {
	    os.add(lookupName, updatedObject, true);
        } catch (NameAlreadyExistsException naee) {
            // Should never happen, since we pass true to add

        } catch (Exception e)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		e.toString(),
		acr.getString(acr.I_OBJSTORE_CF_PROPS),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	/*
	 * Now replace this updated object in the tree.
	 */
	((ObjStoreConFactoryCObj)selObj).setObject(updatedObject);

	//ConsoleObj parent = (ConsoleObj)selObj.getParent();
	//app.getExplorer().removeFromParent(selObj);

 	//ObjStoreConFactoryCObj conFactoryCObj = new ObjStoreConFactoryCObj (os, lookupName, updatedObject);
	//app.getExplorer().addToParent(parent, conFactoryCObj);

	app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_UPDATE_CF,
		         	       selObj.toString(), os.getID()));
	app.getInspector().selectedObjectUpdated();

	if (oae.isOKAction())  {
	    objStoreConFactoryPropsDialog.hide();
        }
    }

    private void doAddDestination(ObjAdminEvent oae, ConsoleObj selObj) {

	Object object = null;
	ObjStore os = oae.getObjStore();
	int destType = oae.getDestinationType();
	String lookupName = oae.getLookupName();
	Properties objProps = oae.getObjProperties();
	boolean readOnly = oae.isReadOnly();
	boolean overwrite = false;

	if (os.isOpen()) {
	    Object newObj = null;
	    try {

	        if (destType == ObjAdminEvent.TOPIC) {
	            newObj = JMSObjFactory.createTopic(objProps);
	        } else if (destType == ObjAdminEvent.QUEUE) {
	            newObj = JMSObjFactory.createQueue(objProps);
	        }

	    } catch (Exception e) {
	        handleExceptions(e, acr.I_ADD_OBJSTORE_DEST);
		return;
	    }

            /*
             * Set this newly created obj to read-only if specified.
             */
	    if (readOnly)
                JMSObjFactory.doReadOnlyForAdd(newObj, "true");
	    else
                JMSObjFactory.doReadOnlyForAdd(newObj, "false");

	    //
	    // Check if it already exists so we can confirm with user to
	    // overwrite.
	    //
	    try {
		object = os.retrieve(lookupName);
            } catch (NameNotFoundException nnfe) {
		// Make sure that this exception is NOT treated as an error for add
		;
	    } catch (Exception ex) {
	    	JOptionPane.showOptionDialog(app.getFrame(), 
			ex.toString(),
			acr.getString(acr.I_ADD_OBJSTORE_DEST),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		return;
	    }

	    // Object already exists so confirm with user.
	    if (object != null) {
	        int result = JOptionPane.showConfirmDialog(app.getFrame(),
			 acr.getString(acr.Q_LOOKUP_NAME_EXISTS, lookupName),
 			 acr.getString(acr.I_ADD_OBJSTORE_DEST),
			 JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.NO_OPTION)
		    return;
		else
		    overwrite = true;
	    }

	    try {
		os.add(lookupName, newObj, true);
	    } catch (Exception ex) {
	    	JOptionPane.showOptionDialog(app.getFrame(), 
			ex.toString(),
			acr.getString(acr.I_ADD_OBJSTORE_DEST),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		return;
	    }

	    if (os != null)  {
		 /*
		  * If already exists, the delete old tree node before adding.
		  */
		 if (overwrite) {
	   	    removeChild(selObj, lookupName);  
	   	    removeChild((ConsoleObj)selObj.getParent().getChildAt(1), lookupName);  
		 }
		 ObjStoreDestCObj destCObj = 
			new ObjStoreDestCObj((ObjStoreCObj)selObj.getParent(), lookupName, newObj);
	         app.getExplorer().addToParent(selObj, destCObj);
	         app.getInspector().refresh();
	         app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_ADD_DEST,
				   	        lookupName, os.getID()));
		 if (oae.isOKAction())  {
	             objStoreDestAddDialog.hide();
		 }
	    }
	}
    }

    private void doAddConnFactory(ObjAdminEvent oae, ConsoleObj selObj) {

	Object object = null;
	ObjStore os = oae.getObjStore();
	int factoryType = oae.getFactoryType();
	String lookupName = oae.getLookupName();
	Properties objProps = oae.getObjProperties();
        boolean readOnly = oae.isReadOnly();
	boolean overwrite = false;

	if (os.isOpen()) {
	    Object newObj = null;
	    try {

	        if (factoryType == ObjAdminEvent.QCF) {
	            newObj = JMSObjFactory.createQueueConnectionFactory(objProps);
	        } else if (factoryType == ObjAdminEvent.TCF) {
	            newObj = JMSObjFactory.createTopicConnectionFactory(objProps);
	        } else if (factoryType == ObjAdminEvent.CF) {
	            newObj = JMSObjFactory.createConnectionFactory(objProps);
	        } else if (factoryType == ObjAdminEvent.XAQCF) {
	            newObj = JMSObjFactory.createXAQueueConnectionFactory(objProps);
	        } else if (factoryType == ObjAdminEvent.XATCF) {
	            newObj = JMSObjFactory.createXATopicConnectionFactory(objProps);
	        } else if (factoryType == ObjAdminEvent.XACF) {
	            newObj = JMSObjFactory.createXAConnectionFactory(objProps);
	        }

	    } catch (Exception e) {
		handleExceptions(e, acr.I_ADD_OBJSTORE_CF);
		return;
	    }

            /*
             * Set this newly created obj to read-only if specified.
             */
            if (readOnly)
                JMSObjFactory.doReadOnlyForAdd(newObj, "true");
            else
                JMSObjFactory.doReadOnlyForAdd(newObj, "false");

	    //
	    // Check if it already exists so we can confirm with user to
	    // overwrite.
	    //
	    try {
		object = os.retrieve(lookupName);
            } catch (NameNotFoundException nnfe) {
		// Make sure that this exception is NOT treated as an error for add
		;
	    } catch (Exception ex) {
	    	JOptionPane.showOptionDialog(app.getFrame(), 
			ex.toString(),
			acr.getString(acr.I_ADD_OBJSTORE_CF),
			JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		return;
	    }

	    // Object already exists so confirm with user.
	    if (object != null) {
                int result = JOptionPane.showConfirmDialog(app.getFrame(),
                         acr.getString(acr.Q_LOOKUP_NAME_EXISTS, lookupName),
                         acr.getString(acr.I_ADD_OBJSTORE_CF),
                         JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.NO_OPTION)
                    return;
                else
                    overwrite = true;
	    }

	    try {
		os.add(lookupName, newObj, true);
	    } catch (Exception ex) {
	    	JOptionPane.showOptionDialog(app.getFrame(), 
			ex.toString(),
			acr.getString(acr.I_ADD_OBJSTORE_CF),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		return;
	    }

	    if (os != null)  {
		 /*
		  * If already exists, the delete old tree node before adding.
		  */
		 if (overwrite) {
	   	    removeChild(selObj, lookupName);  
	   	    removeChild((ConsoleObj)selObj.getParent().getChildAt(0), lookupName);  
		 }
		 ObjStoreConFactoryCObj conFacCObj = 
			new ObjStoreConFactoryCObj((ObjStoreCObj)selObj.getParent(), lookupName, newObj);
	         app.getExplorer().addToParent(selObj, conFacCObj);
	         app.getInspector().refresh();
	         app.getStatusArea().appendText(acr.getString(acr.S_OBJSTORE_ADD_CF,
				   	        lookupName, os.getID()));
		 if (oae.isOKAction())  {
	             objStoreConFactoryAddDialog.hide();
		 }
	    }
	}
    }

    /*
     * Remove the child node with the given name.
     */
    private void removeChild(ConsoleObj parent, String childName) {

	for (Enumeration e = parent.children(); e.hasMoreElements(); ) {
	    ConsoleObj node = (ConsoleObj)e.nextElement();
	    if ((node.toString()).equals(childName)) {
		app.getExplorer().removeFromParent(node);
		e = parent.children();	
	    }
	}
    }

    /*
     * Load objstore list from property file. See ConsoleObjStoreManager and
     * ObjStoreListProperties for details on the actual file loading.
     */
    private void loadObjStoreList()  {
	ConsoleObjStoreManager 	osMgr;

	osMgr = (ConsoleObjStoreManager)app.getObjStoreListCObj().getObjStoreManager();

	try  {
	    osMgr.setFileName(OBJSTORELIST_FILENAME);
	    osMgr.readObjStoresFromFile();
	} catch (Exception ex)  {
	    String errStr = acr.getString(acr.E_LOAD_OBJSTORE_LIST, ex.getMessage());
	    /*
	     * Show popup to indicate that the loading failed.
	     */
	    JOptionPane.showOptionDialog(app.getFrame(), errStr,
	            acr.getString(acr.I_LOAD_OBJSTORE_LIST) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                AdminConsoleResources.E_LOAD_OBJSTORE_LIST),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    /*
	     * At this point, should we make sure the ConsoleObjStoreManager
	     * contains no obj stores ?
	     */
	}

	Vector v = osMgr.getAllStores();

	for (int i = 0; i < v.size(); i++) {
	    ObjStore os = (ObjStore)v.get(i);
	    ConsoleObj osCObj= new ObjStoreCObj(os);

	    if (os.isOpen()) {
	        readObjStore(osCObj, osMgr, os);
	    } 
	    app.getExplorer().addObjStore(osCObj);
	}

	app.getInspector().refresh();
    }

    /*
     * Save objstore list to property file. See ConsoleObjStoreManager and
     * ObjStoreListProperties for details on the actual file saving.
     */
    private void saveObjStoreList()  {
	ConsoleObjStoreManager 	osMgr;

	osMgr = (ConsoleObjStoreManager)app.getObjStoreListCObj().getObjStoreManager();

	try  {
	    osMgr.setFileName(OBJSTORELIST_FILENAME);
	    osMgr.writeObjStoresToFile();
	} catch (Exception ex)  {
	    String errStr = acr.getString(acr.E_SAVE_OBJSTORE_LIST, ex.getMessage());
	    //String titles[] = {acr.getString(acr.I_DIALOG_CLOSE),
	    //		       acr.getString(acr.I_DIALOG_DO_NOT_SHOW_AGAIN)};

	    /*
	     * Inform the user that the save failed with the option of not
	     * popping up this dialog again in the future.
	     */
	    JOptionPane.showOptionDialog(app.getFrame(), errStr,
	            acr.getString(acr.I_OBJSTORE) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                AdminConsoleResources.E_SAVE_OBJSTORE_LIST),
		    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	}
    }


    public void handleConsoleActionEvents(ConsoleActionEvent cae) {
        int type 		 = cae.getType();

	switch (type)  {
	case ConsoleActionEvent.REFRESH:
	    doRefresh();
	break;
	}
    }

    private void doRefresh() {
	ConsoleObj selObj = app.getSelectedObj();

        if (selObj instanceof ObjStoreCObj)  {
	    doRefreshObjStore(selObj);
	} else if (selObj instanceof ObjStoreDestListCObj)  {
	    doRefreshObjStore(selObj);
	} else if (selObj instanceof ObjStoreDestCObj)  {
	    //doRefreshDestination(selObj);
	    ObjStoreDestCObj destCObj = (ObjStoreDestCObj)selObj;
	    ObjStoreCObj     osCObj = destCObj.getObjStoreCObj();
	    doRefreshObjStore(((ObjStoreCObj)osCObj).getObjStoreDestListCObj());
	} else if (selObj instanceof ObjStoreConFactoryListCObj)  {
	    doRefreshObjStore(selObj);
	} else if (selObj instanceof ObjStoreConFactoryCObj)  {
	    //doRefreshConnFactory(selObj);
	    ObjStoreConFactoryCObj cfCObj = (ObjStoreConFactoryCObj)selObj;
	    ObjStoreCObj           osCObj = cfCObj.getObjStoreCObj();
	    doRefreshObjStore(((ObjStoreCObj)osCObj).getObjStoreConFactoryListCObj());
	}
    }

    /*
    private void doRefreshConnFactory(ConsoleObj selObj) {

	ObjStore os = ((ObjStoreConFactoryCObj)selObj).getObjStore();
	String lookupName = ((ObjStoreConFactoryCObj)selObj).getLookupName();
	Object object = null;

	try {
	    object = os.retrieve(lookupName);
        } catch (Exception e)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		e.toString(),
		acr.getString(acr.I_OBJSTORE_REFRESH_CF),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	 // Now replace this updated object in the tree.
	((ObjStoreConFactoryCObj)selObj).setObject(object);

	app.getStatusArea().appendText(acr.getString(acr.S_OS_CF_REFRESH,
		         	       selObj.toString(), os.getID()));
	app.getInspector().selectedObjectUpdated();

    }
    */

    /*
    private void doRefreshDestination(ConsoleObj selObj) {

	ObjStore os = ((ObjStoreDestCObj)selObj).getObjStore();
	String lookupName = ((ObjStoreDestCObj)selObj).getLookupName();
	Object object = null;

	try {
	    object = os.retrieve(lookupName);
        } catch (Exception e)  {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		e.toString(),
		acr.getString(acr.I_OBJSTORE_REFRESH_DEST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
        }

	 // Now replace this updated object in the tree.
	((ObjStoreDestCObj)selObj).setObject(object);

	app.getStatusArea().appendText(acr.getString(acr.S_OS_DEST_REFRESH,
		         	       selObj.toString(), os.getID()));
	app.getInspector().selectedObjectUpdated();

    }
    */

    private void doRefreshObjStore(ConsoleObj selObj) {

	ObjStore os = null;
  	ConsoleObj cObj = selObj;

	if (selObj instanceof ObjStoreCObj) {
	    os =((ObjStoreCObj)selObj).getObjStore();
	    cObj = selObj;	    
	} else if (selObj instanceof ObjStoreDestListCObj) {
	    os = ((ObjStoreDestListCObj)selObj).getObjStore();
	    cObj = (ConsoleObj)selObj.getParent();	    
	} else if (selObj instanceof ObjStoreConFactoryListCObj) {
	    os = ((ObjStoreConFactoryListCObj)selObj).getObjStore();
	    cObj = (ConsoleObj)selObj.getParent();	    
	}

	if (!os.isOpen()) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_OBJSTORE_NOT_CONNECTED, 
				      selObj.toString()),
			acr.getString(acr.I_OBJSTORE_REFRESH) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_OBJSTORE_NOT_CONNECTED),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}

	boolean success = finishDoConnectObjStore(cObj, os);

	if (success) {
	    // Update menu items, buttons.
	    controller.setActions(selObj);

	    // XXX This causes selection in inspector to go away.
	    app.getInspector().refresh();

	    if (selObj instanceof ObjStoreCObj) {
	        app.getStatusArea().appendText(acr.getString(acr.S_OS_REFRESH,
		         	       os.getID()));
	    } else if (selObj instanceof ObjStoreDestListCObj) {
	        app.getStatusArea().appendText(acr.getString(acr.S_OS_DESTLIST_REFRESH,
		         	       os.getID()));
	    } else if (selObj instanceof ObjStoreConFactoryListCObj) {
	        app.getStatusArea().appendText(acr.getString(acr.S_OS_CFLIST_REFRESH,
		         	       os.getID()));
	    }
	}
    }

    private void handleExceptions(Exception e, String titleId) {

        if (e instanceof InvalidPropertyException) {
	
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_INVALID_PROP_NAME,  
				e.getMessage()),
			acr.getString(titleId) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_INVALID_PROP_NAME),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	} else if (e instanceof InvalidPropertyValueException) {
	    JOptionPane.showOptionDialog(app.getFrame(), 
			acr.getString(acr.E_INVALID_PROP_VALUE,
				e.getMessage()),
			acr.getString(titleId) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                            	AdminConsoleResources.E_INVALID_PROP_VALUE),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	} else {
	    JOptionPane.showOptionDialog(app.getFrame(), 
		e.toString(),
		acr.getString(titleId),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	}

    }

    private void closeObjStore(ObjStore os, ConsoleObj selObj) {

	if (os == null)
	    return;

	try {
	    // Close the obj store
	    // and update the explorer to X icon.
	    // and refresh the inspector.
	    if (os.isOpen()) {
	        os.close();
		if (selObj instanceof ObjStoreCObj) {
		    clearStore(selObj);
        	    app.getExplorer().nodeChanged((DefaultMutableTreeNode)selObj);
	    	    app.getInspector().refresh();
		    // Update menu items, buttons.
	  	    controller.setActions(selObj);
		}	
	    }
	} catch (Exception e) {
	    ;
	}
    }
}
