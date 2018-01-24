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
 * @(#)BrokerAddDialog.java	1.11 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import javax.swing.JOptionPane;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;

/** 
 * This dialog is used to add new brokers to the list of
 * brokers displayed in the administration console.
 */
public class BrokerAddDialog extends BrokerDialog  {
    public static final String DEFAULT_BROKER_HOST 	= "localhost";
    public static final String DEFAULT_PRIMARY_PORT 	= "7676";

    private BrokerListCObj blCObj;

    public BrokerAddDialog(Frame parent, BrokerListCObj blCObj) {
	super(parent, acr.getString(acr.I_ADD_BROKER), (OK | RESET | CANCEL | HELP));
	setHelpId(ConsoleHelpID.ADD_BROKER);
	this.blCObj = blCObj;
    }

    public void doOK() {
	String	brokerName = null;

	brokerName = brokerNameTF.getText();
	brokerName = brokerName.trim();

	if (brokerName.equals(""))  {
            JOptionPane.showOptionDialog(this,
                acr.getString(acr.E_NO_BROKER_NAME),
                acr.getString(acr.I_ADD_BROKER) 
	            + ": " 
	            + acr.getString(acr.I_ERROR_CODE, acr.E_NO_BROKER_NAME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
            return;
        }

	// Check to make sure host and port are non-empty
	if (!isValidString (hostTF.getText()) || 
	    !isValidString (portTF.getText())) {

	    JOptionPane.showOptionDialog(this,
                acr.getString(acr.E_NO_BROKER_HOST_PORT),
                acr.getString(acr.I_ADD_BROKER) + ": " 
		        + acr.getString(acr.I_ERROR_CODE, acr.E_NO_BROKER_HOST_PORT),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}
        BrokerAdminEvent bae = new BrokerAdminEvent(this, BrokerAdminEvent.ADD_BROKER);
	bae.setConnectAttempt(false);
	bae.setBrokerName(brokerName);
	bae.setHost(hostTF.getText());
	bae.setPort(Integer.parseInt(portTF.getText()));
	bae.setUsername(userTF.getText());
	bae.setPassword(String.valueOf(passwdTF.getPassword()));
        bae.setOKAction(true);
        fireAdminEventDispatched(bae);
    }

    public void doReset() { 
	reset();
    } 

    public void doCancel() {
	hide(); 
	reset();
    }

    // not used
    public void doApply() {}
    public void doClear() {}
    public void doClose() {}

    public void show() {
	doReset();
	setEditable(true);
	super.show();
    }

    private void reset() {
	brokerNameTF.setText(getBrokerName(acr.getString(acr.I_BROKER_LABEL)));
	hostTF.setText(DEFAULT_BROKER_HOST);
        portTF.setText(DEFAULT_PRIMARY_PORT);
        userTF.setText(BrokerAdmin.DEFAULT_ADMIN_USERNAME);
        passwdTF.setText("");
    }

    protected String getBrokerName(String baseName)  {

	ConsoleBrokerAdminManager baMgr = blCObj.getBrokerAdminManager();

        if (!baMgr.exist(baseName))  {
            return (baseName);
        }

        for (int i = 1; i < 1000; ++i)  {
            String newStr = baseName + i;
            if (!baMgr.exist(newStr))  {
                return (newStr);
            }
        }

        return ("");
    }
}
