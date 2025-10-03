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

import java.io.Serial;
import javax.swing.ImageIcon;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/**
 * This class is used in the JMQ Administration console to store information related to the broker destination list.
 *
 * @see ConsoleObj
 * @see BrokerAdminCObj
 *
 */
public class BrokerDestListCObj extends BrokerAdminCObj {

    @Serial
    private static final long serialVersionUID = 7072055242203219452L;
    private BrokerCObj bCObj;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    public BrokerDestListCObj(BrokerCObj bCObj) {
        this.bCObj = bCObj;
    }

    public BrokerCObj getBrokerCObj() {
        return (bCObj);
    }

    public BrokerAdmin getBrokerAdmin() {
        return (bCObj.getBrokerAdmin());
    }

    @Override
    public String getExplorerLabel() {
        return (acr.getString(acr.I_BROKER_DEST_LIST));
    }

    @Override
    public String getExplorerToolTip() {
        return (null);
    }

    @Override
    public ImageIcon getExplorerIcon() {
        return (AGraphics.adminImages[AGraphics.BROKER_DEST_LIST]);
    }

    @Override
    public String getActionLabel(int actionFlag, boolean forMenu) {
        if (forMenu) {
            switch (actionFlag) {
            case ActionManager.ADD:
                return (acr.getString(acr.I_MENU_ADD_BROKER_DEST));

            case ActionManager.PAUSE:
                return (acr.getString(acr.I_MENU_PAUSE_ALL_DESTS));

            case ActionManager.RESUME:
                return (acr.getString(acr.I_MENU_RESUME_ALL_DESTS));
            }
        } else {
            switch (actionFlag) {
            case ActionManager.ADD:
                return (acr.getString(acr.I_ADD_BROKER_DEST));

            case ActionManager.PAUSE:
                return (acr.getString(acr.I_PAUSE_ALL_DESTS));

            case ActionManager.RESUME:
                return (acr.getString(acr.I_RESUME_ALL_DESTS));
            }
        }

        return (null);
    }

    @Override
    public int getExplorerPopupMenuItemMask() {
        return (ActionManager.ADD | ActionManager.PAUSE | ActionManager.RESUME);
    }

    @Override
    public int getActiveActions() {
        BrokerAdmin ba = getBrokerAdmin();
        int mask;
        if (ba.isConnected()) {
            mask = ActionManager.ADD | ActionManager.REFRESH | ActionManager.PAUSE | ActionManager.RESUME;
        } else {
            mask = 0;
        }

        return (mask);
    }

    @Override
    public String getInspectorPanelClassName() {
        return (ConsoleUtils.getPackageName(this) + ".BrokerDestListInspector");
    }

    @Override
    public String getInspectorPanelId() {
        return ("Broker Destinations");
    }

    @Override
    public String getInspectorPanelHeader() {
        return (getInspectorPanelId());
    }
}
