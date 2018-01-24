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
 * @(#)BrokerListCObj.java	1.20 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.tree.DefaultMutableTreeNode;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to the list of brokers.
 *
 * @see ConsoleObj
 * @see BrokerAdminCObj
 *
 */
public class BrokerListCObj extends BrokerAdminCObj  {

    private transient ConsoleBrokerAdminManager baMgr = null;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    private String label;

    public BrokerListCObj(ConsoleBrokerAdminManager baMgr)  {
        this.baMgr = baMgr;
	label = acr.getString(acr.I_BROKER_LIST); 
    }

    public ConsoleBrokerAdminManager getBrokerAdminManager() {
        return (baMgr);
    }

    public String getExplorerLabel()  {
	return (label);
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	return (AGraphics.adminImages[AGraphics.BROKER_LIST]);
    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.ADD:
	        return (acr.getString(acr.I_MENU_ADD_BROKER));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.ADD:
	        return (acr.getString(acr.I_ADD_BROKER));
	    }
	}

	return (null);
    }

    public int getExplorerPopupMenuItemMask()  {
	return (getActiveActions());
    }


    public int getActiveActions()  {
	return (ActionManager.ADD);
    }


    public String getInspectorPanelClassName()  {
	return (ConsoleUtils.getPackageName(this) + ".BrokerListInspector");
    }

    public String getInspectorPanelId()  {
	return ("Broker List");
    }

    public String getInspectorPanelHeader()  {
	return (getInspectorPanelId());
    }
}
