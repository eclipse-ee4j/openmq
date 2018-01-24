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
 * @(#)BrokerDestCObj.java	1.15 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.DestState;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to a particular broker
 * destination.
 *
 * @see ConsoleObj
 * @see BrokerAdminCObj
 *
 */
public class BrokerDestCObj extends BrokerAdminCObj  {

    private BrokerCObj bCObj;
    private DestinationInfo destInfo = null;
    private Vector durables = null;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /*
     * It is okay not to get the latest durable subscriptions in the constructor, 
     * since we always retrieve the latest durable subscriptions right before we 
     * display them (because of their dynamic nature.)
     */
    public BrokerDestCObj(BrokerCObj bCObj, DestinationInfo destInfo) {
        this.bCObj = bCObj;
        this.destInfo = destInfo;
    }

    public BrokerDestCObj(BrokerCObj bCObj, DestinationInfo destInfo, Vector durables) {
        this.bCObj = bCObj;
        this.destInfo = destInfo;
        this.durables = durables;
    }

    public BrokerAdmin getBrokerAdmin() {
	return (bCObj.getBrokerAdmin());
    }

    public BrokerCObj getBrokerCObj() {
	return (bCObj);
    }

    public DestinationInfo getDestinationInfo() {
	return (destInfo);
    }

    public void setDestinationInfo(DestinationInfo destInfo) {
	this.destInfo = destInfo;
    }

    public Vector getDurables() {
	return (durables);
    }

    public void setDurables(Vector durables) {
	this.durables = durables;
    }

    public String getExplorerLabel()  {
        if (destInfo != null)
            return destInfo.name;
        else
	    return (acr.getString(acr.I_BROKER_DEST));
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	return (null);
    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_MENU_PAUSE_DEST));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_MENU_RESUME_DEST));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_PAUSE_DEST));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_RESUME_DEST));
	    }
	}

	return (null);
    }

    public int getExplorerPopupMenuItemMask()  {
	return (ActionManager.DELETE | ActionManager.PROPERTIES 
		| ActionManager.PURGE | ActionManager.PAUSE | ActionManager.RESUME);
    }


    public int getActiveActions()  {
	BrokerAdmin ba = getBrokerAdmin();
	int mask;

        // REVISIT: for now, no operation is allowed if we are not connected.
        // This should be taken out, as we should disallow selecting a dest
        // when it is not connected.
	if (!ba.isConnected())
	    mask = 0;
	else
	    if (destInfo.destState == DestState.RUNNING)  {
	        mask = ActionManager.DELETE | ActionManager.PROPERTIES
		     | ActionManager.PURGE | ActionManager.REFRESH 
		     | ActionManager.PAUSE;
	    } else if ((destInfo.destState == DestState.CONSUMERS_PAUSED)
		    || (destInfo.destState == DestState.PRODUCERS_PAUSED)
		    || (destInfo.destState == DestState.PAUSED))  {
	        mask = ActionManager.DELETE | ActionManager.PROPERTIES
		     | ActionManager.PURGE | ActionManager.REFRESH 
		     | ActionManager.RESUME;
	    } else {
	        mask = ActionManager.DELETE | ActionManager.PROPERTIES
		     | ActionManager.PURGE | ActionManager.REFRESH;
	    }

	return (mask);
    }



    public String getInspectorPanelClassName()  {
	return (null);
    }

    public String getInspectorPanelId()  {
	return (null);
    }

    public String getInspectorPanelHeader()  {
	return (null);
    }
}
