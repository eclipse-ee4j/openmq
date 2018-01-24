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
 * @(#)AController.java	1.54 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;


import java.awt.Container;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.apps.console.event.SelectionEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ConsoleActionEvent;

/** 
 * The controller component basically listens for events from
 * all the other admin UI components and reacts to them.
 * <P>
 * Unlike the other UI components, the controller does know
 * about all the individual pieces of the admin UI. The controller
 * knows this via the AdminApp object. With it, it can control
 * the entire admin console application.
 * <P>
 * The controller delegates the object administration and broker
 * administration tasks to ObjAdminHandler and BrokerAdminHandler.
 *
 * @see ObjAdminHandler
 * @see BrokerAdminHandler
 */
public class AController implements AdminEventListener  {

    private ObjAdminHandler	objAdminHandler;
    private BrokerAdminHandler	brokerAdminHandler;
    private AboutDialog         aboutDialog = null;

    private AdminApp	app;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public AController(AdminApp app) {
	this.app = app;
	objAdminHandler = new ObjAdminHandler(app, this);
	brokerAdminHandler = new BrokerAdminHandler(app, this);
    } 

    public void init()  {
	// Turn off setScrollToVisbible() just at startup
	app.getExplorer().setScrollToPath(false);
	objAdminHandler.init();
	brokerAdminHandler.init();
	
	// Now expand all the nodes.
	app.getExplorer().expandAll();

	// Now turn on setScrollToVisbible() back.
	app.getExplorer().setScrollToPath(true);
    }



    /*
     * BEGIN INTERFACE AdminEventListener
     */
    public void adminEventDispatched(AdminEvent e)  {
	int id;
	ConsoleObj selObj;
	
	if (e instanceof DialogEvent)  {
	    handleDialogEvents((DialogEvent)e);
	} else if (e instanceof SelectionEvent)  {
	    handleSelectionEvents((SelectionEvent)e);
	} else if (e instanceof ObjAdminEvent)  {
	    handleObjAdminEvents((ObjAdminEvent)e);
        } else if (e instanceof BrokerAdminEvent)  {
            handleBrokerAdminEvents((BrokerAdminEvent)e);
        } else if (e instanceof ConsoleActionEvent)  {
            handleConsoleActionEvents((ConsoleActionEvent)e);
	}
    }
    /*
     * END INTERFACE AdminEventListener
     */

    private void handleDialogEvents(DialogEvent de) {

	ConsoleObj selObj = app.getSelectedObj();
        //int dialogType = de.getDialogType();

        if (selObj instanceof ObjStoreAdminCObj)   {
	    objAdminHandler.handleDialogEvents(de);
	} else if (selObj instanceof BrokerAdminCObj)  {
	    brokerAdminHandler.handleDialogEvents(de);
	}
    }

    private void handleSelectionEvents(SelectionEvent se) {
        ConsoleObj	selObj = se.getSelectedObj();
	Object		source = se.getSource();
        int		type = se.getType();
	boolean 	fromExplorer = true;

	if (source instanceof Container)  {
	    Container c = (Container)source;
	    
	    if (app.getInspector().isAncestorOf(c))  {
		fromExplorer = false;
	    }
	}

        switch (type)  {
        case SelectionEvent.OBJ_SELECTED:
	    app.setSelectedObj(selObj);

	    if (fromExplorer)  {
	        app.getInspector().clearSelection();

		if (selObj.canBeInspected())  {
	            app.getInspector().inspect(selObj);
		}
	    } else  {
	        app.getExplorer().clearSelection();
	    }

	     /*
	      * Activate/deactive actions, menu items, toolbar buttons.
	      */
	    setActions(selObj);

	    /*
	     * Here for debugging, need to remove when ship.
	    app.getStatusArea().appendText(selObj
				+ " ["
				+ selObj.getClass().getName()
				+ "]"
				+ " selected.\n");
	     */
        break;

        case SelectionEvent.CLEAR_SELECTION:
	    clearSelection();
	break;
        }
    }

    private void handleObjAdminEvents(ObjAdminEvent oae) {
	objAdminHandler.handleObjAdminEvents(oae);
    }


    /*
     * Clears any selected object,
     * Clears the inspector to empty.
     * Clears any menu items that don't apply when
     *   nothing is selected.
     */
    public void clearSelection() {
	app.setSelectedObj(null);
	app.getInspector().inspect(null);
	app.getActionManager().setActiveActions(0);
    }

   /*
    * Set buttons, menus based on selObj.
    */
    public void setActions(ConsoleObj selObj) {

	if (selObj == null) 
	    return;

        /*
         * Activate/deactivate actions based on selected object.
	 */
	app.getActionManager().setActiveActions(selObj.getActiveActions());

	/*
	 * Change labels on menu items based on selected object.
	 */
	app.getMenubar().setConsoleObj(selObj);

	/*
	 * Change tooltips on toolbar buttons based on selected object.
	 */
	app.getToolbar().setConsoleObj(selObj);

    }

    private void handleBrokerAdminEvents(BrokerAdminEvent bae) {
	brokerAdminHandler.handleBrokerAdminEvents(bae);
    }

    private void handleConsoleActionEvents(ConsoleActionEvent cae) {
        int type 		 = cae.getType();

	switch (type)  {
	case ConsoleActionEvent.EXIT:
	    doExit();
	break;

	case ConsoleActionEvent.ABOUT:
	    doAbout();
	break;

	case ConsoleActionEvent.EXPAND_ALL:
	    doExpandAll();
	break;

	case ConsoleActionEvent.COLLAPSE_ALL:
	    doCollapseAll();
	break;

	case ConsoleActionEvent.REFRESH:
	    doRefresh(cae);
	break;
	}
    }

    private void doExit()  {
	System.exit(0);
    }

    private void doAbout()  {
	if (aboutDialog == null) {
	    aboutDialog = new AboutDialog(app.getFrame());
	    aboutDialog.addAdminEventListener(this);
	    aboutDialog.setLocationRelativeTo(app.getFrame());
	}
	aboutDialog.show();
    }

    public void doExpandAll() {
	app.getExplorer().expandAll();
    }

    public void doCollapseAll() {
	app.getExplorer().collapseAll();
    }

    public void doRefresh(ConsoleActionEvent cae) {
	ConsoleObj selObj = app.getSelectedObj();

        if (selObj instanceof ObjStoreAdminCObj)  {
	    objAdminHandler.handleConsoleActionEvents(cae);
	} else if (selObj instanceof BrokerAdminCObj)  {
	    brokerAdminHandler.handleConsoleActionEvents(cae);
	}
    }

}
