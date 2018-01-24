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
 * @(#)BrokerPropsDialog.java	1.7 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import javax.swing.JOptionPane;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;

/** 
 * This dialog is used for viewing/changing the connection
 * properties of a broker. 
 * <P>
 * Note: This dialog is not used to query a broker's attributes
 * (as in "imqcmd query bkr").
 * <P>
 */
public class BrokerPropsDialog extends BrokerDialog  {
    private BrokerCObj bCObj;
    private BrokerAdmin ba;

    public BrokerPropsDialog(Frame parent) {
	super(parent, 
		acr.getString(acr.I_BROKER_PROPS), 
		(OK | CANCEL | CLOSE | HELP));
	setHelpId(ConsoleHelpID.BROKER_PROPS);
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

        BrokerAdminEvent bae = new BrokerAdminEvent(this, 
				BrokerAdminEvent.UPDATE_BROKER_ENTRY);
	bae.setConnectAttempt(false);
	bae.setBrokerName(brokerName);
	bae.setHost(hostTF.getText());
	bae.setPort(Integer.parseInt(portTF.getText()));
	bae.setUsername(userTF.getText());
	bae.setPassword(String.valueOf(passwdTF.getPassword()));
        bae.setOKAction(true);
        fireAdminEventDispatched(bae);
    }

    public void doCancel() {
	hide(); 
	clearFields();
    }

    // not used
    public void doReset() {}
    public void doApply() {}
    public void doClear() {}
    public void doClose() {
	hide();
	clearFields();
    }

    public void show() {
	if (ba.isConnected()) {
	    setEditable(false);
        } else {
	    setEditable(true);
        }
	super.show();
    }

    public void setBrokerCObj(BrokerCObj bCObj)  {
	String tmp;


	if (bCObj == null)  {
	    clearFields();

	    return;
	}

	ba = bCObj.getBrokerAdmin();

	tmp = ba.getKey();
	brokerNameTF.setText(tmp);

	tmp = ba.getBrokerHost();
	hostTF.setText(tmp);

	tmp = ba.getBrokerPort();
        portTF.setText(tmp);

	tmp = ba.getUserName();
        userTF.setText(tmp);

	tmp = ba.getPassword();
        passwdTF.setText(tmp);

	this.bCObj = bCObj;
    }

    protected void setEditable(boolean editable) {
        if (editable) {
            okButton.setVisible(true);
            closeButton.setVisible(false);
            cancelButton.setVisible(true);
            buttonPanel.doLayout();

        } else {
            okButton.setVisible(false);
            closeButton.setVisible(true);
            cancelButton.setVisible(false);
            buttonPanel.doLayout();
        }

	super.setEditable(editable);

    }
}
