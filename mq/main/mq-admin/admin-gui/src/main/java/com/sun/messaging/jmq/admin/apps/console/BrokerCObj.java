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
 * @(#)BrokerCObj.java	1.27 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Vector;
import java.util.Properties;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to a particular broker.
 *
 * @see ConsoleObj
 * @see BrokerAdminCObj
 *
 */
public class BrokerCObj extends BrokerAdminCObj  {
    private BrokerServiceListCObj	bSvcListCObj;
    private BrokerDestListCObj		bDestListCObj;
    private BrokerLogListCObj		bLogListCObj;

    private transient BrokerAdmin ba;
    private Properties bkrProps;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    public BrokerCObj(BrokerAdmin ba) {
        this.ba = ba;

        bSvcListCObj = new BrokerServiceListCObj(this);
        bDestListCObj = new BrokerDestListCObj(this);
	/*
        bLogListCObj = new BrokerLogListCObj();
	*/

        insert(bSvcListCObj, 0);
        insert(bDestListCObj, 1);
	/*
	 * Logs not managed yet in console
        insert(bLogListCObj, 2);
	*/
    }

    public BrokerAdmin getBrokerAdmin() {
	return (ba);
    }

    public Properties getBrokerProps() {
	return bkrProps;
    }

    public BrokerDestListCObj getBrokerDestListCObj() {
	return bDestListCObj;
    }

    public BrokerServiceListCObj getBrokerServiceListCObj() {
	return bSvcListCObj;
    }

    public void setBrokerProps(Properties bkrProps) {
	this.bkrProps = bkrProps;
    }


    public String getExplorerLabel()  {
	if (ba != null)
	    return (ba.getKey());
	else
	    return (acr.getString(acr.getString(acr.I_BROKER)));
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	if (ba.isConnected()) {
	    return (AGraphics.adminImages[AGraphics.BROKER]);
	} else  {
	    return (AGraphics.adminImages[AGraphics.BROKER_DISCONNECTED]);
	}

    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.CONNECT:
	        return (acr.getString(acr.I_MENU_CONNECT_BROKER));

	    case ActionManager.DISCONNECT:
	        return (acr.getString(acr.I_MENU_DISCONNECT_BROKER));

	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_MENU_PAUSE_BROKER));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_MENU_RESUME_BROKER));

	    case ActionManager.SHUTDOWN:
	        return (acr.getString(acr.I_MENU_SHUTDOWN_BROKER));

	    case ActionManager.RESTART:
	        return (acr.getString(acr.I_MENU_RESTART_BROKER));

	    case ActionManager.QUERY_BROKER:
	        return (acr.getString(acr.I_MENU_QUERY_BROKER));

	    case ActionManager.DELETE:
	        return (acr.getString(acr.I_MENU_DELETE));

	    case ActionManager.PROPERTIES:
	        return (acr.getString(acr.I_MENU_PROPERTIES));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.CONNECT:
	        return (acr.getString(acr.I_CONNECT_BROKER));

	    case ActionManager.DISCONNECT:
	        return (acr.getString(acr.I_DISCONNECT_BROKER));

	    case ActionManager.PAUSE:
	        return (acr.getString(acr.I_PAUSE_BROKER));

	    case ActionManager.RESUME:
	        return (acr.getString(acr.I_RESUME_BROKER));

	    case ActionManager.SHUTDOWN:
	        return (acr.getString(acr.I_SHUTDOWN_BROKER));

	    case ActionManager.RESTART:
	        return (acr.getString(acr.I_RESTART_BROKER));

	    case ActionManager.QUERY_BROKER:
	        return (acr.getString(acr.I_QUERY_BROKER));

	    case ActionManager.DELETE:
	        return (acr.getString(acr.I_DELETE));

	    case ActionManager.PROPERTIES:
	        return (acr.getString(acr.I_PROPERTIES));
	    }
	}

	return (null);
    }

    public ImageIcon getActionIcon(int actionFlag)  {
	switch (actionFlag)  {
	case ActionManager.CONNECT:
	    return (AGraphics.adminImages[AGraphics.CONNECT_TO_BROKER]);
	case ActionManager.DISCONNECT:
	    return (AGraphics.adminImages[AGraphics.DISCONNECT_FROM_BROKER]);
	}

	return (null);
    }

    public int getExplorerPopupMenuItemMask()  {
        return (ActionManager.DELETE | ActionManager.PROPERTIES
                | ActionManager.CONNECT | ActionManager.DISCONNECT
                | ActionManager.PAUSE | ActionManager.RESUME
                | ActionManager.SHUTDOWN | ActionManager.RESTART
		| ActionManager.QUERY_BROKER);
    }


    public int getActiveActions()  {
	int mask = 0;
	
	if (ba.isConnected()) {
	    if (isPausable(ba)) {
	      mask |= ActionManager.PAUSE;
	    }
	    
	    if (isResumable(ba)) {
	      mask |= ActionManager.RESUME;
	    }

	    mask |= ActionManager.DELETE
                 | ActionManager.SHUTDOWN | ActionManager.RESTART
		 | ActionManager.DISCONNECT | ActionManager.REFRESH
		 | ActionManager.QUERY_BROKER | ActionManager.PROPERTIES;
	} else {
            mask = ActionManager.DELETE | ActionManager.CONNECT
		| ActionManager.PROPERTIES;
	}
	return (mask);
    }

    public String getInspectorPanelClassName()  {
	return (ConsoleUtils.getPackageName(this) + ".BrokerInspector");
    }

    public String getInspectorPanelId()  {
	return ("Broker");
    }

    public String getInspectorPanelHeader()  {
	return (getInspectorPanelId());
    }

    private boolean isPausable(BrokerAdmin ba) {
	boolean answer = false;

	/*
	 * Consider a broker "pausable" if at least
	 * one service (that is not an ADMIN service) 
	 * is RUNNING.
	 */
	for (java.util.Enumeration e = bSvcListCObj.children(); 
		e.hasMoreElements();) {
	    ConsoleObj node = (ConsoleObj)e.nextElement();
	    if (node instanceof BrokerServiceCObj) {
		ServiceInfo svcInfo = ((BrokerServiceCObj)node).getServiceInfo();
		if (svcInfo != null &&
		    svcInfo.type != ServiceType.ADMIN &&
		    svcInfo.state == ServiceState.RUNNING) {
		    answer = true;
		    break;
		}
	    }
	}

	return answer;
    }

    private boolean isResumable(BrokerAdmin ba) {
	boolean answer = false;

	/*
	 * Consider a broker "resumable" if at least
	 * one service (that is not an ADMIN service) 
	 * is PAUSED.
	 */
	for (java.util.Enumeration e = bSvcListCObj.children(); 
		e.hasMoreElements();) {
	    ConsoleObj node = (ConsoleObj)e.nextElement();
	    if (node instanceof BrokerServiceCObj) {
		ServiceInfo svcInfo = ((BrokerServiceCObj)node).getServiceInfo();
		if (svcInfo != null &&
		    svcInfo.type != ServiceType.ADMIN &&
		    svcInfo.state == ServiceState.PAUSED) {
		    answer = true;
		    break;
		}
	    }
	}
	return answer;
    }
}
