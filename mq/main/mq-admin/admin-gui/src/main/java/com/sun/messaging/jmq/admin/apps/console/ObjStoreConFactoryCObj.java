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

import com.sun.messaging.jmq.admin.objstore.ObjStore;

/**
 * This class is used in the JMQ Administration console to store information related to a particular connection factory
 * object in an object store.
 *
 * @see ConsoleObj
 * @see ObjStoreAdminCObj
 *
 */
public class ObjStoreConFactoryCObj extends ObjStoreAdminCObj {

    private static final long serialVersionUID = -6070347195680456565L;
    private ObjStoreCObj osCObj = null;
    private transient ObjStore os = null;
    private String lookupName = null;
    private Object object;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public ObjStoreConFactoryCObj(ObjStoreCObj osCObj, String lookupName, Object object) {
        this.osCObj = osCObj;
        this.os = osCObj.getObjStore();
        this.lookupName = lookupName;
        this.object = object;
    }

    public ObjStore getObjStore() {
        return this.os;
    }

    @Override
    public String getExplorerLabel() {
        return this.lookupName;
    }

    public void setLookupName(String lookupName) {
        this.lookupName = lookupName;
    }

    public String getLookupName() {
        return this.lookupName;
    }

    public Object getObject() {
        return this.object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    @Override
    public String getExplorerToolTip() {
        return (null);
    }

    @Override
    public ImageIcon getExplorerIcon() {
        return (null);
    }

    public ObjStoreCObj getObjStoreCObj() {
        return this.osCObj;
    }

    @Override
    public int getExplorerPopupMenuItemMask() {
        return (getActiveActions());
    }

    @Override
    public int getActiveActions() {
        return (ActionManager.DELETE | ActionManager.PROPERTIES | ActionManager.REFRESH);
    }

    @Override
    public String getInspectorPanelClassName() {
        return (ConsoleUtils.getPackageName(this) + ".ObjStoreConFactoryListInspector");
    }

    @Override
    public String getInspectorPanelId() {
        return (null);
    }

    @Override
    public String getInspectorPanelHeader() {
        return (null);
    }
}
