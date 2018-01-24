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
 * @(#)ObjStoreDestListCObj.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.ImageIcon;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.objstore.ObjStore;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to the list of destination
 * objects in an object store.
 *
 * @see ConsoleObj
 * @see ObjStoreAdminCObj
 *
 */
public class ObjStoreDestListCObj extends ObjStoreAdminCObj  {
    private transient ObjStore	os = null;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public ObjStoreDestListCObj(ObjStore os) {
	this.os = os;
    } 

    public void setObjStore(ObjStore os)  {
	this.os = os;
    }

    public ObjStore getObjStore()  {
	return (os);
    }

    public String getExplorerLabel()  {
	return (acr.getString(acr.I_OBJSTORE_DEST_LIST));
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	return (AGraphics.adminImages[AGraphics.OBJSTORE_DEST_LIST]);
    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.ADD:
	        return (acr.getString(acr.I_MENU_ADD_OBJSTORE_DEST));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.ADD:
	        return (acr.getString(acr.I_ADD_OBJSTORE_DEST));
	    }
	}

	return (null);
    }

    public int getExplorerPopupMenuItemMask()  {
	return (ActionManager.ADD);
    }

    public int getActiveActions()  {
	int mask;

	if (os.isOpen())  {
	    mask = ActionManager.ADD | ActionManager.REFRESH;
	} else  {
	    mask = 0;
	}
	
	return (mask);
    }




    public String getInspectorPanelClassName()  {
	return (ConsoleUtils.getPackageName(this) + ".ObjStoreDestListInspector");
    }

    public String getInspectorPanelId()  {
	return ("Destinations");
    }

    public String getInspectorPanelHeader()  {
	return (getInspectorPanelId());
    }
}
