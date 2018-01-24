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
 * @(#)BrokerPasswdDialog.java	1.6 06/27/07
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
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;

/** 
 * This dialog is used for broker authentication.
 */
public class BrokerPasswdDialog extends AdminDialog {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    private JTextField		username;
    private JTextField		password;
    private BrokerAdmin 	ba;

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
    public BrokerPasswdDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_CONNECT_BROKER), (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.CONNECT_BROKER);
    }

    public BrokerPasswdDialog(Frame parent, int whichButtons) {
	super(parent, acr.getString(acr.I_CONNECT_BROKER), whichButtons);
	setHelpId(ConsoleHelpID.CONNECT_BROKER);
    }

    public JPanel createWorkPanel()  {

	JPanel workPanel = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	workPanel.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	LabelledComponent items[] = new LabelledComponent[2];

	username = new JTextField(20);
	username.addActionListener(this);
	items[0] = new LabelledComponent(acr.getString(acr.I_BROKER_USERNAME), username);
	password = new JPasswordField(20);
	password.addActionListener(this);
	items[1] = new LabelledComponent(acr.getString(acr.I_BROKER_PASSWD), password);
	
	LabelValuePanel lvp = new LabelValuePanel(items, 5, 5);

	c.gridx = 0;
	c.gridy = 0;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(lvp, c);
	workPanel.add(lvp);

	return (workPanel);
    }

    public void doOK() {

	/*
	 * Note:
	 * Not forcing the username and password to be mandatory,
	 * since the plugin authentication can require anything.
	 */	 
	String usernameValue = username.getText().trim();

        /*
	if (usernameValue.equals("")) {
            JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE, "username"),
		acr.getString(acr.I_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            username.requestFocus();
            return;
	} 
	*/
	
	String passwordValue = password.getText().trim();
 
	/*
	if (passwordValue.equals("")) {
            JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE, "password"),
		acr.getString(acr.I_BROKER),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            password.requestFocus();
            return;
	}
	*/

	BrokerAdminEvent bae = 
		new BrokerAdminEvent(this, BrokerAdminEvent.UPDATE_LOGIN);
	bae.setUsername(usernameValue);
	bae.setPassword(passwordValue);
	bae.setOKAction(true);
	fireAdminEventDispatched(bae);

        username.requestFocus();
        if ((usernameValue.length() != 0) && (passwordValue.length() == 0))
            password.requestFocus();
    }

    public void doApply() { }
    public void doReset() { }

    public void doCancel() { hide(); }

    public void doClose() { hide(); }

    public void doClear() { 
	username.setText("");
	password.setText("");
    }

    public void show(BrokerAdmin ba) {
	
	this.ba = ba;

        doClear();
	String usernameValue = ba.getUserName();
	String passwordValue = ba.getPassword();

        username.requestFocus();

        /*
	 * Missing both.
	 */
	if ((usernameValue.length() == 0) && (passwordValue.length() == 0)) {

 	/* 
         * Missing username only.
         */
	} else if (usernameValue.length() == 0) {
	    password.setText(passwordValue);
 	/* 
         * Missing password only.
         */
	} else {
	    username.setText(usernameValue);
            password.requestFocus();
	}

        setDefaultButton(OK);
        super.show();
    }

    /**********************************************************************
     * ActionListener
     */
    public void actionPerformed(ActionEvent ev) {
        if (ev.getSource() == username) {
            password.requestFocus();
        } else if (ev.getSource() == password) {
            doOK();
        } else {
	    super.actionPerformed(ev);
	}

    }
}
