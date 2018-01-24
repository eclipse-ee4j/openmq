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
 * @(#)ObjStorePasswdDialog.java	1.6 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.naming.Context;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;

/** 
 * This dialog is used for object store attributes.
 * It can be used to Add an object store to the list
 * or to modify (update) an existing object store.
 *
 */
public class ObjStorePasswdDialog extends AdminDialog {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    private ObjStore		os;
    private JTextField		principalText;
    private JTextField		credentialsText;
    private Vector 		missingInfo;

    /**
     * Creates a non-modal dialog using the specified frame as parent and string
     * as title. By default, will contain the following buttons:
     * <UL>
     * <LI>OK
     * <LI>CANCEL
     * <LI>HELP
     * </UL>
     *
     * @param parent the Frame from which the dialog is displayed
     * @param title the String to display in the dialog's title bar
     */
    public ObjStorePasswdDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_CONNECT_OBJSTORE), (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.CONNECT_OBJECT_STORE);
    }

    public ObjStorePasswdDialog(Frame parent, int whichButtons) {
	super(parent, acr.getString(acr.I_CONNECT_OBJSTORE), whichButtons);
	setHelpId(ConsoleHelpID.CONNECT_OBJECT_STORE);
    }

    public JPanel createWorkPanel()  {

	JPanel workPanel = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	workPanel.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	LabelledComponent items[] = new LabelledComponent[2];

	principalText = new JTextField(20);
	principalText.addActionListener(this);
	items[0] = new LabelledComponent(Context.SECURITY_PRINCIPAL + ":", 
				         principalText);
	credentialsText = new JPasswordField(20);
	credentialsText.addActionListener(this);
	items[1] = new LabelledComponent(Context.SECURITY_CREDENTIALS + ":", 
				         credentialsText);
	
	LabelValuePanel lvp = new LabelValuePanel(items, 5, 5);

	c.gridx = 0;
	c.gridy = 0;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(lvp, c);
	workPanel.add(lvp);

	return (workPanel);
    }

    public void doOK()  { 
	String principalValue = principalText.getText().trim();

	if (principalValue.equals("")) {
            JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE, Context.SECURITY_PRINCIPAL),
		acr.getString(acr.I_CONNECT_OBJSTORE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            principalText.requestFocus();
            return;
	} 
	
	String credentialsValue = credentialsText.getText().trim();

	if (credentialsValue.equals("")) {
            JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE, Context.SECURITY_CREDENTIALS),
		acr.getString(acr.I_CONNECT_OBJSTORE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            credentialsText.requestFocus();
            return;
	}

        /*
	 * Now add these to the object store.
	 */
	ObjStoreAttrs osa = os.getObjStoreAttrs();

	if (!osa.containsKey(Context.SECURITY_PRINCIPAL)) {
	    this.os.addObjStoreAttr(Context.SECURITY_PRINCIPAL, principalValue);
	}
	if (!osa.containsKey(Context.SECURITY_CREDENTIALS)) {
	    this.os.addObjStoreAttr(Context.SECURITY_CREDENTIALS, credentialsValue);
	}
	
	ObjAdminEvent oae = new ObjAdminEvent(this,
				ObjAdminEvent.UPDATE_CREDENTIALS);
	oae.setObjStore(os);
	oae.setObjStoreAttrs(osa);
	oae.setMissingAuthInfo(missingInfo);
	oae.setOKAction(true);
	fireAdminEventDispatched(oae);
	
    }

    public void doApply()  { }
    public void doReset() { }

    public void doCancel() { hide(); }
    public void doClose() { hide(); }
    public void doClear() { 
	principalText.setText("");
	credentialsText.setText("");
    }

    public void show(ObjStore os, Vector missingInfo) { 

	this.os = os;
	this.missingInfo  = missingInfo;

	doClear();

	ObjStoreAttrs osa = os.getObjStoreAttrs();
	/*
	 * Fill in principal, credentials if we have it
	 */
	if (osa.containsKey(Context.SECURITY_CREDENTIALS)) {
	    credentialsText.setText((String)osa.get(Context.SECURITY_CREDENTIALS));
	} else {
	    credentialsText.requestFocus();
	}

	/*
	 * Fill in this one second in case both are missing
	 * and we want to focus in the principal text field.
	 */
	if (osa.containsKey(Context.SECURITY_PRINCIPAL)) {
	    principalText.setText((String)osa.get(Context.SECURITY_PRINCIPAL));
	} else {
	    principalText.requestFocus();
	}

	setDefaultButton(OK);
	super.show();
    }

    /**********************************************************************
     * ActionListener
     */
    public void actionPerformed(ActionEvent ev) {

        if (ev.getSource() == principalText) {
            credentialsText.requestFocus();
        } else if (ev.getSource() == credentialsText) {
	    doOK();
	} else {
	    super.actionPerformed(ev);
	}

    }

}
