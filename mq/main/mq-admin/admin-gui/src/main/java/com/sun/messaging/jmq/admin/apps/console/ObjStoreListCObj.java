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

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.ImageIcon;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;

/**
 * This class is used in the JMQ Administration console to store information related to the list of object stores.
 *
 * @see ConsoleObj
 * @see ObjStoreAdminCObj
 *
 */
public class ObjStoreListCObj extends ObjStoreAdminCObj {
    private static final long serialVersionUID = 8157671958980785296L;
    private transient ObjStoreManager osMgr = null;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private String label;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public ObjStoreListCObj(ObjStoreManager osMgr) {
        this.osMgr = osMgr;
        label = acr.getString(acr.I_OBJSTORE_LIST);
    }

    public ObjStoreManager getObjStoreManager() {
        return (osMgr);
    }

    @Override
    public String getExplorerLabel() {
        return (label);
    }

    @Override
    public String getExplorerToolTip() {
        return (null);
    }

    @Override
    public ImageIcon getExplorerIcon() {
        return (AGraphics.adminImages[AGraphics.OBJSTORE_LIST]);
    }

    @Override
    public String getActionLabel(int actionFlag, boolean forMenu) {
        if (forMenu) {
            switch (actionFlag) {
            case ActionManager.ADD:
                return (acr.getString(acr.I_MENU_ADD_OBJSTORE));
            }
        } else {
            switch (actionFlag) {
            case ActionManager.ADD:
                return (acr.getString(acr.I_ADD_OBJSTORE));
            }
        }

        return (null);
    }

    @Override
    public int getExplorerPopupMenuItemMask() {
        return (getActiveActions());
    }

    @Override
    public int getActiveActions() {
        return (ActionManager.ADD);
    }

    @Override
    public String getInspectorPanelClassName() {
        return (ConsoleUtils.getPackageName(this) + ".ObjStoreListInspector");
    }

    @Override
    public String getInspectorPanelId() {
        return ("JMQ Object Stores");
    }

    @Override
    public String getInspectorPanelHeader() {
        return (getInspectorPanelId());
    }
}
