/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.awt.Frame;
import java.io.Serial;
import java.util.Enumeration;
import javax.swing.JOptionPane;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;

/**
 * The inspector component of the admin GUI displays attributes of the currently selected item. It does not know or care
 * about what is currently selected. It is basically told what to display i.e. what object's attributes to display.
 * <P>
 * There are a variety of objects that can be <EM>inspected</EM>:
 * <UL>
 * <LI>Collection of object stores
 * <LI>Individual object stores
 * <LI>Collection of brokers
 * <LI>Individual brokers
 * <LI>Collection of services
 * <LI>Individual services
 * <LI>Collection of Topics
 * <LI>Individual Topics
 * <LI>Collection of Queues
 * <LI>Individual Queues
 * <LI>
 * </UL>
 *
 * For each of the object types above, a different inspector panel is potentially needed for displaying the object's
 * attributes. This will be implemented by having a main panel stacking all the different property panels in CardLayout.
 * For each object that needs to be inspected, the object needs to be passed in to the inspector as well as it's type.
 */
public class ObjStoreAddDialog extends ObjStoreDialog {

    @Serial
    private static final long serialVersionUID = -7709739086684528622L;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = { acr.getString(acr.I_DIALOG_CLOSE) };

    public ObjStoreAddDialog(Frame parent, ObjStoreListCObj oslCObj) {
        super(parent, acr.getString(acr.I_ADD_OBJSTORE), (OK | CLEAR | CANCEL | HELP), oslCObj);
        setHelpId(ConsoleHelpID.ADD_OBJECT_STORE);
    }

    @Override
    public void doOK() {

        // if (osTextButton.isSelected()) {
        String osName = osText.getText();
        osName = osName.trim();
        //
        // Make sure store name is not empty.
        //
        if (osName.equals("")) {
            JOptionPane.showOptionDialog(this, acr.getString(acr.E_NO_OBJSTORE_NAME),
                    acr.getString(acr.I_ADD_OBJSTORE) + ": " + acr.getString(acr.I_ERROR_CODE, AdminConsoleResources.E_NO_OBJSTORE_NAME),
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            osText.requestFocus();
            return;
        }
        /*
         * } else if (urlButton.isSelected()) { // Make sure a provider.url property was set. osName =
         * jndiProps.getProperty(Context.PROVIDER_URL); if (osName == null || osName.equals("")) {
         * JOptionPane.showOptionDialog(this, acr.getString(acr.E_NO_PROVIDER_URL, Context.PROVIDER_URL),
         * acr.getString(acr.I_ADD_OBJSTORE) + ": " + acr.getString(acr.I_ERROR_CODE, AdminConsoleResources.E_NO_PROVIDER_URL),
         * JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, close, close[0]);
         * comboBox.setSelectedItem(Context.PROVIDER_URL); valueText.requestFocus(); return; } }
         */

        ObjAdminEvent oae;
        ObjStoreAttrs osa = constructAttrs(osName);

        if (osa == null) {
            return;
        }

        oae = new ObjAdminEvent(this, ObjAdminEvent.ADD_OBJSTORE);
        oae.setObjStoreAttrs(osa);
        // oae.setConnectAttempt(checkBox.isSelected());
        oae.setConnectAttempt(false);
        oae.setOKAction(true);
        fireAdminEventDispatched(oae);
    }

    @Override
    public void doReset() {
    }

    @Override
    public void doCancel() {
        setVisible(false);
    }

    @Override
    public void doClose() {
        setVisible(false);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            doClear();
            osText.setText(getDefaultStoreName(acr.getString(acr.I_OBJSTORE_LABEL)));
            setEditable(true);
            osText.selectAll();
            doComboBox();
        }
        super.setVisible(visible);
    }

    private ObjStoreAttrs constructAttrs(String osName) {

        //
        // Check if this store name already exists.
        //
        if (osMgr != null && osMgr.getStore(osName) != null) {
            JOptionPane.showOptionDialog(this, acr.getString(acr.E_OBJSTORE_NAME_IN_USE, osName),
                    acr.getString(acr.I_ADD_OBJSTORE) + ": " + acr.getString(acr.I_ERROR_CODE, AdminConsoleResources.E_OBJSTORE_NAME_IN_USE),
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            // if (osTextButton.isSelected()) {
            osText.requestFocus();
            osText.selectAll();
            // }
            return (null);
        }

        ObjStoreAttrs osa = new ObjStoreAttrs(osName, osName);

        if (jndiProps == null) {
            return (osa);
        }

        // Check for any properties that MUST be set.
        if (checkMandatoryProps() == 0) {
            return null;
        }

        for (Enumeration e = jndiProps.propertyNames(); e.hasMoreElements();) {
            String propName = (String) e.nextElement();
            osa.put(propName, jndiProps.getProperty(propName));
        }

        return (osa);
    }

}
