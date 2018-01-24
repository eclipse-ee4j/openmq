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
 * @(#)ObjStoreCObj.java	1.24 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.ImageIcon;
import javax.swing.tree.MutableTreeNode;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.objstore.ObjStore;

/** 
 * This class is used in the JMQ Administration console
 * to store information related to a particular object store.
 *
 * @see ConsoleObj
 * @see ObjStoreAdminCObj
 *
 */
public class ObjStoreCObj extends ObjStoreAdminCObj  {

    private transient ObjStore			os = null;
    private ObjStoreDestListCObj	objStoreDestList = null;
    private ObjStoreConFactoryListCObj	objStoreConFactoryList = null;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public ObjStoreCObj(ObjStore os) {
	this.os = os;

	objStoreDestList = new ObjStoreDestListCObj(os);
	objStoreConFactoryList = new ObjStoreConFactoryListCObj(os);

	insert(objStoreDestList, 0);
	insert(objStoreConFactoryList, 1);
    } 

    public void setObjStore(ObjStore os)  {
	this.os = os;
    }

    public ObjStore getObjStore()  {
	return (os);
    }

    public String getExplorerLabel()  {
	if (os.getDescription() != null)  {
	    return (os.getDescription());
	} else  {
	    return (os.getID());
        }
    }

    public String getExplorerToolTip()  {
	return (null);
    }

    public ImageIcon getExplorerIcon()  {
	if (os.isOpen())  {
	    return (AGraphics.adminImages[AGraphics.OBJSTORE]);
	} else  {
	    return (AGraphics.adminImages[AGraphics.OBJSTORE_DISCONNECTED]);
	}
    }

    public ObjStoreDestListCObj getObjStoreDestListCObj() {
	return this.objStoreDestList;
    }

    public ObjStoreConFactoryListCObj getObjStoreConFactoryListCObj() {
	return this.objStoreConFactoryList;
    }

    public String getActionLabel(int actionFlag, boolean forMenu)  {
	if (forMenu)  {
	    switch (actionFlag)  {
	    case ActionManager.CONNECT:
	        return (acr.getString(acr.I_MENU_CONNECT_OBJSTORE));

	    case ActionManager.DISCONNECT:
	        return (acr.getString(acr.I_MENU_DISCONNECT_OBJSTORE));

	    case ActionManager.DELETE:
	        return (acr.getString(acr.I_MENU_DELETE));

	    case ActionManager.PROPERTIES:
	        return (acr.getString(acr.I_MENU_PROPERTIES));
	    }
	} else  {
	    switch (actionFlag)  {
	    case ActionManager.CONNECT:
	        return (acr.getString(acr.I_CONNECT_OBJSTORE));

	    case ActionManager.DISCONNECT:
	        return (acr.getString(acr.I_DISCONNECT_OBJSTORE));

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
	    return (AGraphics.adminImages[AGraphics.CONNECT_TO_OBJSTORE]);
	case ActionManager.DISCONNECT:
	    return (AGraphics.adminImages[AGraphics.DISCONNECT_FROM_OBJSTORE]);
	}

	return (null);
    }


    public void insert(MutableTreeNode node, int newIndex)  {
	if ((node instanceof ObjStoreDestListCObj) ||
	    (node instanceof ObjStoreConFactoryListCObj))  {
	    super.insert(node, newIndex);
	} else {
	    /*
	     * No special behaviour yet
	     */
	    super.insert(node, newIndex);
	}
    }


    public int getExplorerPopupMenuItemMask()  {
	return (ActionManager.DELETE | ActionManager.PROPERTIES
		| ActionManager.DISCONNECT | ActionManager.CONNECT);
    }

    public int getActiveActions()  {
	int	mask;

	if (os.isOpen())  {
	    mask =  ActionManager.DELETE  | ActionManager.PROPERTIES
		| ActionManager.DISCONNECT | ActionManager.REFRESH;
	} else  {
	    mask =  ActionManager.DELETE | ActionManager.PROPERTIES
		| ActionManager.CONNECT;
	}
	
	return (mask);
    }



    public String getInspectorPanelClassName()  {
	return (ConsoleUtils.getPackageName(this) + ".ObjStoreInspector");
    }

    public String getInspectorPanelId()  {
	return ("Object Store");
    }

    public String getInspectorPanelHeader()  {
	return (getInspectorPanelId());
    }
}
