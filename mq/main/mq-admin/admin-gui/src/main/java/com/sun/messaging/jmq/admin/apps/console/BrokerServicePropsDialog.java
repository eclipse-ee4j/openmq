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
 * @(#)BrokerServicePropsDialog.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Dimension;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JSeparator;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;

import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.ServiceType;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.apps.console.util.IntegerField;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;

/** 
 * Implementation of the Service Properties Dialog
 */
public class BrokerServicePropsDialog extends AdminDialog {
    
    private static AdminResources ar = Globals.getAdminResources();
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};
    private BrokerServiceCObj	svcCObj;
    private JLabel 		svcName;
    private JRadioButton	dynamicPortButton;
    private JRadioButton	staticPortButton;
    private JLabel		dynamicPortLabel;
    private IntegerField	staticPortIF;
    private JLabel 	 	svcState;
    private IntegerField 	minThreads, maxThreads;
    private JLabel 		numConnections, allocatedThreads;
    private JPanel		lvp;
    private LabelledComponent   items[];

    private ServiceInfo		svcInfo;

    private LabelledComponent	svcPortComponent;


    public BrokerServicePropsDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_BROKER_SVC_PROPS), (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.SERVICE_PROPS);
    }

    public JPanel createWorkPanel() {

        JPanel                  workPanel;
        GridBagLayout           workGridbag;
        GridBagConstraints      workConstraints;
        LabelledComponent       tmpLabelC;
        LabelledComponent       lvpItems[];
        LabelValuePanel         lvp;
        JSeparator              sep;

        workPanel = new JPanel();
        workGridbag = new GridBagLayout();
        workPanel.setLayout(workGridbag);
        workConstraints = new GridBagConstraints();

	/*
         * Initialize.
         */
        workConstraints.gridx = 0;
        workConstraints.gridy = 0;
        workConstraints.anchor = GridBagConstraints.WEST;
        workConstraints.fill = GridBagConstraints.NONE;
        workConstraints.insets = new Insets(5, 0, 5, 0);
        workConstraints.ipadx = 0;
        workConstraints.ipady = 0;
        workConstraints.weightx = 1.0;

        lvpItems = new LabelledComponent[3];

        svcName = new JLabel();
        tmpLabelC = new LabelledComponent(
		ar.getString(ar.I_JMQCMD_SVC_NAME) + ":",
		svcName);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
	lvpItems[0] = tmpLabelC;

	// for radio buttons for dynamic / static port number
	JPanel servicePanel = new JPanel();
	GridBagLayout serviceGridbag = new GridBagLayout();
	servicePanel.setLayout(serviceGridbag);
	GridBagConstraints serviceConstraints = new GridBagConstraints();

	/*
         * Initialize.
         */
        serviceConstraints.anchor = GridBagConstraints.WEST;
	serviceConstraints.insets = new Insets(0, 0, 0, 0);

	serviceConstraints.gridx = 0;
	serviceConstraints.gridy = 0;
	dynamicPortButton = new JRadioButton();
	dynamicPortButton.addActionListener(this);
	serviceGridbag.setConstraints(dynamicPortButton, serviceConstraints);
	servicePanel.add(dynamicPortButton);

	serviceConstraints.gridx = 0;
	serviceConstraints.gridy = 1;
	staticPortButton = new JRadioButton();
	staticPortButton.addActionListener(this);
	serviceGridbag.setConstraints(staticPortButton, serviceConstraints);
	servicePanel.add(staticPortButton);

	ButtonGroup servicePortGroup = new ButtonGroup();
	servicePortGroup.add(dynamicPortButton);
	servicePortGroup.add(staticPortButton);

	serviceConstraints.gridx = 1;
	serviceConstraints.gridy = 0;
	JLabel dynamicLabel = new JLabel(acr.getString(acr.I_DYNAMIC_CAP) + ":");
	serviceGridbag.setConstraints(dynamicLabel, serviceConstraints);
	servicePanel.add(dynamicLabel);

	serviceConstraints.gridx = 1;
	serviceConstraints.gridy = 1;
	JLabel staticLabel = new JLabel(acr.getString(acr.I_STATIC_CAP) + ":");
	serviceGridbag.setConstraints(staticLabel, serviceConstraints);
	servicePanel.add(staticLabel);

	serviceConstraints.gridx = 2;
	serviceConstraints.gridy = 0;
	serviceConstraints.insets = new Insets(0, 5, 0, 0);
	dynamicPortLabel = new JLabel();
	serviceGridbag.setConstraints(dynamicPortLabel, serviceConstraints);
	servicePanel.add(dynamicPortLabel);

	serviceConstraints.gridx = 2;
	serviceConstraints.gridy = 1;
	serviceConstraints.insets = new Insets(0, 5, 0, 0);
	staticPortIF = new IntegerField(0, Integer.MAX_VALUE, 15);
	staticPortIF.setEnabled(false);
	serviceGridbag.setConstraints(staticPortIF, serviceConstraints);
	servicePanel.add(staticPortIF);

        tmpLabelC = new LabelledComponent(
                ar.getString(ar.I_JMQCMD_SVC_PORT)+":",
                servicePanel, LabelledComponent.NORTH);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
        lvpItems[1] = tmpLabelC;

	// Set this so that we can enable/disable this component
	svcPortComponent = lvpItems[1];

        svcState = new JLabel();
        tmpLabelC = new LabelledComponent(
		ar.getString(ar.I_JMQCMD_SVC_STATE)+":",
		svcState);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
	lvpItems[2] = tmpLabelC;

        lvp = new LabelValuePanel(lvpItems, 4, 0);
        workGridbag.setConstraints(lvp, workConstraints);
        workPanel.add(lvp);

        workConstraints.gridy = 1;

        sep = new JSeparator();
        workConstraints.fill = GridBagConstraints.HORIZONTAL;
        workGridbag.setConstraints(sep, workConstraints);
        workPanel.add(sep);
        /*
         * Reset
         */
        workConstraints.fill = GridBagConstraints.NONE;
        workConstraints.gridy = 2;

        lvpItems = new LabelledComponent[2];

        allocatedThreads = new JLabel();
        tmpLabelC = new LabelledComponent(
		ar.getString(ar.I_JMQCMD_SVC_CUR_THREADS) + ":",
		allocatedThreads);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
	lvpItems[0] = tmpLabelC;

        numConnections = new JLabel();
        tmpLabelC = new LabelledComponent(
		ar.getString(ar.I_JMQCMD_SVC_NUM_CXN) + ":",
		numConnections);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
	lvpItems[1] = tmpLabelC;

        lvp = new LabelValuePanel(lvpItems, 4, 0);
        workGridbag.setConstraints(lvp, workConstraints);
        workPanel.add(lvp);

        workConstraints.gridy = 3;

        sep = new JSeparator();
        workConstraints.fill = GridBagConstraints.HORIZONTAL;
        workGridbag.setConstraints(sep, workConstraints);
        workPanel.add(sep);
        /*
         * Reset
         */
        workConstraints.fill = GridBagConstraints.NONE;
        workConstraints.gridy = 4;

        lvpItems = new LabelledComponent[2];

        minThreads = new IntegerField(0, Integer.MAX_VALUE, 15);
	minThreads.addActionListener(this);
        tmpLabelC = new LabelledComponent(
                ar.getString(ar.I_JMQCMD_SVC_MIN_THREADS) + ":",
                minThreads);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
        workPanel.add(tmpLabelC);
	lvpItems[0] = tmpLabelC;

	maxThreads = new IntegerField(0, Integer.MAX_VALUE, 15);
	maxThreads.addActionListener(this);
        tmpLabelC = new LabelledComponent(
                ar.getString(ar.I_JMQCMD_SVC_MAX_THREADS) + ":",
                maxThreads);
        workGridbag.setConstraints(tmpLabelC, workConstraints);
	lvpItems[1] = tmpLabelC;

        lvp = new LabelValuePanel(lvpItems, 4, 0);
        workGridbag.setConstraints(lvp, workConstraints);
        workPanel.add(lvp);

	return workPanel;
    }

    public void doOK() { 
	int portValue = -1;
	int minThreadsValue = -1;
	int maxThreadsValue = -1;

	if (staticPortButton.isSelected()) {
	    String portText = staticPortIF.getText();

	    if (!"".equals(portText)) {
		portValue = Integer.parseInt(portText);
	    } else {
                JOptionPane.showOptionDialog(this,
                    acr.getString(acr.E_NO_STATIC_PORT),
                    acr.getString(acr.I_BROKER_SVC_PROPS) + ": "
                        + acr.getString(acr.I_ERROR_CODE, acr.E_NO_STATIC_PORT),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                return;
	    }

	} else if (dynamicPortButton.isSelected()) {
	    // New protocol.
	    // If the port number is not used for this service it
	    // is set to -1.  If the port number was -1 do not change it.
	    if (svcInfo.port == -1)
		portValue = -1;		
	    // Set it to 0 for dynamic otherwise
	    else
	        portValue = 0;
	}

	minThreadsValue = Integer.parseInt(minThreads.getText());
	maxThreadsValue = Integer.parseInt(maxThreads.getText());

        /*
	 * Rollback the following fix.  The check is now done
	 * by the broker.	 
	 *
         * Fix for bug 4432483: jmqcmd, jmqadmin: setting admin max 
         * threads = 0 is allowed & hangs
         *
         * Display a warning if service type != admin.
         * Disallow the operation if service type == admin.
	if (maxThreadsValue <= 0) {
	    if (ServiceType.ADMIN == svcInfo.type) {
                JOptionPane.showOptionDialog(this,
                    acr.getString(acr.E_ADMIN_MAX_THREAD),
                    acr.getString(acr.I_BROKER_SVC_PROPS) + ": "
                        + acr.getString(acr.I_ERROR_CODE, acr.E_ADMIN_MAX_THREAD),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
                return;

	    } else {
        	int result = JOptionPane.showConfirmDialog(this,
      		    acr.getString(acr.Q_SET_MAX_THREAD_ZERO, svcInfo.name),
                    acr.getString(acr.I_BROKER_SVC_PROPS),
               	    JOptionPane.YES_NO_OPTION);

        	if (result == JOptionPane.NO_OPTION)
            	    return;
	    }
	}
	*/

        BrokerAdminEvent bae = 
	    new BrokerAdminEvent(this, BrokerAdminEvent.UPDATE_SVC);

	bae.setPort(portValue);
	bae.setMinThreads(minThreadsValue);
	bae.setMaxThreads(maxThreadsValue);

        bae.setOKAction(true);
        fireAdminEventDispatched(bae);
    }

    public void doApply()  { }
    public void doReset() { }
    public void doCancel() { hide(); }

    public void doClose() { 
	hide(); 
	doClear();
    }

    public void doClear() { 
	svcName.setText(" ");
	dynamicPortButton.setSelected(true);
	staticPortButton.setSelected(false);
	dynamicPortLabel.setText(" ");
	staticPortIF.setText("");
	svcState.setText(" ");
	minThreads.setText("0");
	maxThreads.setText("0");
	allocatedThreads.setText("0");
	numConnections.setText("0");
	svcPortComponent.setEnabled(true);
    }

    public void show(ServiceInfo svcInfo)  {

	this.svcInfo = svcInfo;

	if (svcInfo == null) {
	    doClear();
	    return;
	}

	svcName.setText(svcInfo.name);

	// The port number is not applicable to this service
	if (svcInfo.port == -1)
	    enableServicePort(false);
	else {
	    enableServicePort(true);

	    if (svcInfo.dynamicPort) {
	        dynamicPortButton.setSelected(true);
	        staticPortIF.setEnabled(false);
	        staticPortIF.setText("");

                switch (svcInfo.state) {
                    case ServiceState.UNKNOWN:
		        dynamicPortLabel.setText(" ");
                        break;
                    default:
		        dynamicPortLabel.setText(Integer.toString(svcInfo.port));
                }
            } else {
	        staticPortButton.setSelected(true);
	        staticPortIF.setEnabled(true);
	        staticPortIF.setText(Integer.toString(svcInfo.port));
	        dynamicPortLabel.setText(" ");
	    }
	}
	//svcState.setText(ServiceState.getString(svcInfo.state));
	svcState.setText(BrokerAdminUtil.getServiceState(svcInfo.state));
	maxThreads.setText(Integer.toString(svcInfo.minThreads));
	allocatedThreads.setText(Integer.toString(svcInfo.currentThreads));
	numConnections.setText(Integer.toString(svcInfo.nConnections));
	super.show();
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
        Object source = e.getSource();

        if (source == dynamicPortButton)  {
            doDynamicPortButton();

        } else if (source == staticPortButton)  {
            doStaticPortButton();

        } else  {
            super.actionPerformed(e);
        }
    }
    /*
     * END INTERFACE ActionListener
     */

    private void doDynamicPortButton() {
	staticPortIF.setEnabled(false);
    }

    private void doStaticPortButton() {
	staticPortIF.setEnabled(true);
    }

    private void enableServicePort(boolean b) {
	svcPortComponent.setEnabled(b);
	if (!b) {
            dynamicPortButton.setSelected(true);
            staticPortButton.setSelected(false);
            dynamicPortLabel.setText(" ");
            staticPortIF.setText("");
	}
    }

    /*
     * Not used.
    private void makeReadOnly() {
        svcName.setEditable(false);
        svcPort.setEditable(false);
        svcState.setEditable(false);
        minThreads.setEditable(false);
        maxThreads.setEditable(false);
        allocatedThreads.setEditable(false);
        numConnections.setEditable(false);
    }
     */
}
