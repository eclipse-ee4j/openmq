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
 * @(#)BrokerServiceCObj.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.admin.ServiceInfo;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to a particular broker
 * service.
 *
 * @see ConsoleObj
 * @see BrokerAdminCObj
 *
 */
public class BrokerServiceCObj extends BrokerAdminCObj  {

    private BrokerCObj bCObj;
    private ServiceInfo svcInfo;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    public BrokerServiceCObj(BrokerCObj bCObj, ServiceInfo svcInfo) {
        this.bCObj = bCObj;
        this.svcInfo = svcInfo;
    }

    public BrokerAdmin getBrokerAdmin() {
	return (bCObj.getBrokerAdmin());
    }

    public BrokerCObj getBrokerCObj() {
	return (bCObj);
    }

    public ServiceInfo getServiceInfo() {
	return svcInfo;
    }

    public void setServiceInfo(ServiceInfo svcInfo) {
	this.svcInfo = svcInfo;
    }

    public String getExplorerLabel()  {
	if (svcInfo != null)
	    return svcInfo.name;
	else
	    return (acr.getString(acr.I_BROKER_SVC));
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	return (AGraphics.adminImages[AGraphics.BROKER_SERVICE]);
    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_MENU_PAUSE_SERVICE));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_MENU_RESUME_SERVICE));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_PAUSE_SERVICE));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_RESUME_SERVICE));
	    }
        }

	return (null);
    }

    public int getExplorerPopupMenuItemMask()  {
	return (ActionManager.PROPERTIES | ActionManager.PAUSE | ActionManager.RESUME);
    }


    public int getActiveActions()  {
	int mask;

	// REVISIT: for now, no operation is allowed if we are not connected.
	// This should be taken out, as we should disallow selecting a service
	// when it is not connected.
	if (!getBrokerAdmin().isConnected())
	    mask = 0;

	/*
	 * ActionManager.REFRESH is included here to enable refreshing of the 
	 * entire service list
	 */

	// If this is an admin service, no operation is allowed
	else if (svcInfo.type == ServiceType.ADMIN || 
		 svcInfo.state == ServiceState.UNKNOWN)
	    mask = ActionManager.PROPERTIES | ActionManager.REFRESH;
	else if (svcInfo.state == ServiceState.RUNNING)
	    mask = ActionManager.PROPERTIES | ActionManager.PAUSE | ActionManager.REFRESH;
	else if (svcInfo.state == ServiceState.PAUSED)
	    mask = ActionManager.PROPERTIES | ActionManager.RESUME | ActionManager.REFRESH;
	else
	    mask = ActionManager.PROPERTIES | ActionManager.PAUSE | ActionManager.RESUME | ActionManager.REFRESH;

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
