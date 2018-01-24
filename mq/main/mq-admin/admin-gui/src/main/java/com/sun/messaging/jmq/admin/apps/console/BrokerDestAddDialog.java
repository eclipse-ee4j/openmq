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
 * @(#)BrokerDestAddDialog.java	1.23 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Insets;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JOptionPane;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.apps.console.util.IntegerField;
import com.sun.messaging.jmq.admin.apps.console.util.BytesField;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * This dialog is used to add a physical destination to the broker.
 */
public class BrokerDestAddDialog extends AdminDialog {

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};
    private final static int UNLIMITED_VALUE_NEG1 = -1;  // for active/failover consumers

    JPanel destPropertyPanel;
    JRadioButton queueRB;
    JRadioButton topicRB;

    JTextField nameTF;

    /*
     * Max Active Consumer Count
     */
    JLabel 		activeConsumerLbl;
    JRadioButton	activeConsumerLimitedRB,
    			activeConsumerUnlimitedRB;
    JLabel 		activeConsumerUnlimitedLbl;
    IntegerField	activeConsumerIF;

    /*
     * Max Failover Consumer Count
     */
    JLabel 		failoverConsumerLbl;
    JRadioButton	failoverConsumerLimitedRB,
    			failoverConsumerUnlimitedRB;
    JLabel 		failoverConsumerUnlimitedLbl;
    IntegerField	failoverConsumerIF;

    /*
     * Max Producer Count
     */
    JLabel 		maxProducerLbl;
    JRadioButton	maxProducerLimitedRB,
    			maxProducerUnlimitedRB;
    JLabel 		maxProducerUnlimitedLbl;
    IntegerField	maxProducerIF;

    /*
     * Queue Size Limit
     */
    JLabel 		QSizeLimit;
    JRadioButton 	queueSizeLimitUnlimitedRB,
			queueSizeLimitLimitedRB;
    JLabel 		queueSizeLimitUnlimitedLbl;
    BytesField		queueSizeLimitBF;

    /*
     * Queue Message Limit
     */
    JLabel 		QMessageLimit;
    JRadioButton 	queueMessageLimitUnlimitedRB,
			queueMessageLimitLimitedRB;
    JLabel		queueMessageLimitUnlimitedLbl;
    IntegerField	queueMessageLimitTF;

    /*
     * Dest Maximum Size per Message
     */
    JRadioButton	destMaxSizePerMsgUnlimitedRB,
    			destMaxSizePerMsgLimitedRB;
    JLabel		destMaxSizePerMsgUnlimitedLbl;
    BytesField		destMaxSizePerMsgBF;

    public BrokerDestAddDialog(Frame parent)  {
	super(parent, 
	      acr.getString(acr.I_ADD_BROKER_DEST),
	      (OK | RESET | CANCEL | HELP));
	setHelpId(ConsoleHelpID.ADD_BROKER_DEST);
    }

    public void show()  {
	reset();
	super.show();
    }

    public void doOK() {
	String destName = nameTF.getText();
	destName = destName.trim();
	int intValue;
	long longValue;

        BrokerAdminEvent bae = new BrokerAdminEvent(this, BrokerAdminEvent.ADD_DEST);

	// Destination name is a must.
	if (!isValidString(destName)) {
	    JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_BROKER_DEST_NAME),
		acr.getString(acr.I_ADD_BROKER_DEST),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	} else
            bae.setDestinationName(destName);

	/*
	 *  QUEUE-specifics.
	 */
	if (queueRB.isSelected()) {
	    bae.setDestinationTypeMask(DestType.DEST_TYPE_QUEUE);

	    if (activeConsumerUnlimitedRB.isSelected()) {
		bae.setActiveConsumers(UNLIMITED_VALUE_NEG1);   
	    } else {
		intValue = Integer.parseInt(activeConsumerIF.getText());
		bae.setActiveConsumers(intValue);
	    }

	    if (failoverConsumerUnlimitedRB.isSelected()) {
		bae.setFailoverConsumers(UNLIMITED_VALUE_NEG1);   
	    } else {
		intValue = Integer.parseInt(failoverConsumerIF.getText());
		bae.setFailoverConsumers(intValue);
	    }
	
        /*
         *  TOPIC-specifics.
         */
	} else if (topicRB.isSelected()) {
	     bae.setDestinationTypeMask(DestType.DEST_TYPE_TOPIC);
	}

	/*
	 *  From here on applies to both queues and topics.
	 */

	if (maxProducerUnlimitedRB.isSelected()) {
	    bae.setMaxProducers(UNLIMITED_VALUE_NEG1);   
	} else {
	    intValue = Integer.parseInt(maxProducerIF.getText());
	    bae.setMaxProducers(intValue);
	}
	
	// Set default value unlimited anyway in case if the broker
	// decides to change its default value...
	if (queueSizeLimitUnlimitedRB.isSelected())
	    bae.setMaxMesgBytes(UNLIMITED_VALUE_NEG1);
	else {
	    longValue = queueSizeLimitBF.getValue();
            bae.setMaxMesgBytes(longValue);
	}

	if (queueMessageLimitUnlimitedRB.isSelected())
	    bae.setMaxMesg(UNLIMITED_VALUE_NEG1);
	else {
	    String s = queueMessageLimitTF.getText();
            try {
                intValue = Integer.parseInt(s);
            } catch (NumberFormatException nfe) {
	        /*
		 * Should never happen since queueMessageLimitTF
		 * is an IntegerField.
		 */
                intValue = -1;
	    }

            if (intValue != -1)  {
                bae.setMaxMesg(intValue);
	    }
        }
	
        if (destMaxSizePerMsgUnlimitedRB.isSelected())
	    bae.setMaxPerMesgSize(UNLIMITED_VALUE_NEG1);
	else {
	    longValue = destMaxSizePerMsgBF.getValue();
            bae.setMaxPerMesgSize(longValue);
        }

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

    public JPanel createWorkPanel()  {
        JPanel 			workPanel;
        GridBagLayout		workGridbag;
        GridBagConstraints	workConstraints;

        workPanel = new JPanel();
        workGridbag = new GridBagLayout();
        workConstraints = new GridBagConstraints();
        workPanel.setLayout(workGridbag);

        workConstraints.anchor = GridBagConstraints.WEST;

        LabelledComponent items[] = new LabelledComponent[3];

	/*
	 * Destination Name
	 */
	nameTF = new JTextField("", 20);
        items[0] = new LabelledComponent(
	    acr.getString(acr.I_BROKER_DEST_NAME), nameTF);

	/*
	 * Destination Type
	 */
	JPanel destTypePanel = createDestTypePanel();
        items[1] = new LabelledComponent(
	    acr.getString(acr.I_BROKER_DEST_TYPE), destTypePanel, 
	    LabelledComponent.NORTH);

	/*
	 * Destination Properties
	 */
	JPanel destPropsPanel = createDestPropsPanel();
        items[2] = new LabelledComponent("", destPropsPanel);

        LabelValuePanel lvp = new LabelValuePanel(items, 5, 5);
        workConstraints.gridx = 0;
        workConstraints.gridy = 4;
        workConstraints.gridwidth = 2;
        workConstraints.anchor = GridBagConstraints.CENTER;
        workGridbag.setConstraints(lvp, workConstraints);
        workPanel.add(lvp);
	
	return (workPanel);
    }

    // currently not used
    /*
    private void disableComponents(JPanel comp) {
	for (int i = 0; i < comp.getComponentCount(); i++)
	    comp.getComponent(i).setEnabled(false);
    }
    */

    // currently not used
    /*
    private void enableComponents(JPanel comp) {
	for (int i = 0; i < comp.getComponentCount(); i++)
	    comp.getComponent(i).setEnabled(true);
    }
    */

    private JPanel createDestTypePanel() {
	JPanel			destPanel;
        GridBagLayout		destGridbag;
        GridBagConstraints	destConstraints;
	Insets			indentInsets;

        indentInsets = new Insets(0, 5, 0, 0);

	destPanel = new JPanel();
	destGridbag = new GridBagLayout();
	destConstraints = new GridBagConstraints();
	destPanel.setLayout(destGridbag);

	/*
	 * Common constraint values
	 */
	destConstraints.anchor = GridBagConstraints.WEST;

        destConstraints.gridx = 0;
        destConstraints.gridy = 0;
        destConstraints.insets = indentInsets;
        queueRB = new JRadioButton(acr.getString(acr.I_QUEUE), true);
        queueRB.addActionListener(this);
        destGridbag.setConstraints(queueRB, destConstraints);
        destPanel.add(queueRB);

        destConstraints.gridx = 0;
        destConstraints.gridy = 1;
        topicRB = new JRadioButton(acr.getString(acr.I_TOPIC), false);
        topicRB.addActionListener(this);
        destGridbag.setConstraints(topicRB, destConstraints);
        destPanel.add(topicRB);

        ButtonGroup destTypeGroup = new ButtonGroup();
        destTypeGroup.add(queueRB);
        destTypeGroup.add(topicRB);

	return destPanel;
    }

    private JPanel createDestPropsPanel() {
	JPanel			destPanel;
        GridBagLayout		destGridbag;
        GridBagConstraints	destConstraints;
        JLabel			tmpLabel;
	Insets			zeroInsets, indentInsets, newSectionInsets;
	int			i = 0;

	destPanel = new JPanel();
	destGridbag = new GridBagLayout();
	destConstraints = new GridBagConstraints();
	destPanel.setLayout(destGridbag);

	zeroInsets = new Insets(0, 0, 0, 0);
	indentInsets = new Insets(0, 5, 0, 0);
	newSectionInsets = new Insets(5, 0, 0, 0);

	/*
	 * Common constraint values
	 */
	destConstraints.anchor = GridBagConstraints.WEST;

	/*
	 * BEGIN Queue Message Limit
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = newSectionInsets;
	/*
	JLabel QMessageLimit = new JLabel("Queue Message Limit:");
	*/
	QMessageLimit = new JLabel(acr.getString(acr.I_BROKER_MAX_NUM_MSGS));
	destGridbag.setConstraints(QMessageLimit, destConstraints);
	destPanel.add(QMessageLimit);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        queueMessageLimitUnlimitedRB = new JRadioButton();
        queueMessageLimitUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(queueMessageLimitUnlimitedRB, destConstraints);
	destPanel.add(queueMessageLimitUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = zeroInsets;
	queueMessageLimitUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(queueMessageLimitUnlimitedLbl, destConstraints);
	destPanel.add(queueMessageLimitUnlimitedLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
        queueMessageLimitLimitedRB = new JRadioButton();
	destConstraints.insets = indentInsets;
        queueMessageLimitLimitedRB.addActionListener(this);
	destGridbag.setConstraints(queueMessageLimitLimitedRB, destConstraints);
	destPanel.add(queueMessageLimitLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i;
	destConstraints.insets = zeroInsets;
	queueMessageLimitTF = new IntegerField(0, Integer.MAX_VALUE, 10);
	destGridbag.setConstraints(queueMessageLimitTF, destConstraints);
	destPanel.add(queueMessageLimitTF);

	destConstraints.gridx = 2;
	destConstraints.gridy = i++;
	destConstraints.insets = new Insets(0, 4, 0, 0);

        ButtonGroup qMessageLimitGroup = new ButtonGroup();
        qMessageLimitGroup.add(queueMessageLimitUnlimitedRB);
        qMessageLimitGroup.add(queueMessageLimitLimitedRB);
	/*
	 * END Queue Message Limit
	 */

	/*
	 * BEGIN Queue Size Limit
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = newSectionInsets;
	/*
        JLabel QSizeLimit = new JLabel("Queue Size Limit:");
	*/
        QSizeLimit = new JLabel(acr.getString(acr.I_BROKER_MAX_TTL_SIZE_MSGS));
	destGridbag.setConstraints(QSizeLimit, destConstraints);
	destPanel.add(QSizeLimit);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        queueSizeLimitUnlimitedRB = new JRadioButton();
        queueSizeLimitUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(queueSizeLimitUnlimitedRB, destConstraints);
	destPanel.add(queueSizeLimitUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	queueSizeLimitUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(queueSizeLimitUnlimitedLbl, destConstraints);
	destPanel.add(queueSizeLimitUnlimitedLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        queueSizeLimitLimitedRB = new JRadioButton();
        queueSizeLimitLimitedRB.addActionListener(this);
	destGridbag.setConstraints(queueSizeLimitLimitedRB, destConstraints);
	destPanel.add(queueSizeLimitLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = zeroInsets;
	queueSizeLimitBF = new BytesField(0, Integer.MAX_VALUE, 10);
	queueSizeLimitBF.addActionListener(this);
	destGridbag.setConstraints(queueSizeLimitBF, destConstraints);
	destPanel.add(queueSizeLimitBF);

        ButtonGroup qSizeLimitGroup = new ButtonGroup();
        qSizeLimitGroup.add(queueSizeLimitUnlimitedRB);
        qSizeLimitGroup.add(queueSizeLimitLimitedRB);
	/*
	 * END Queue Size Limit
	 */

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	/*
	 * BEGIN Maximum Size per Message
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = newSectionInsets;
	/*
        JLabel maxSizePerMsg = new JLabel("Maximum Size per Message:");
	*/
        JLabel maxSizePerMsg = new JLabel(acr.getString(acr.I_BROKER_MAX_SIZE_PER_MSG));
	destGridbag.setConstraints(maxSizePerMsg, destConstraints);
	destPanel.add(maxSizePerMsg);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        destMaxSizePerMsgUnlimitedRB = new JRadioButton();
        destMaxSizePerMsgUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(destMaxSizePerMsgUnlimitedRB, destConstraints);
	destPanel.add(destMaxSizePerMsgUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destMaxSizePerMsgUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(destMaxSizePerMsgUnlimitedLbl, destConstraints);
	destPanel.add(destMaxSizePerMsgUnlimitedLbl);

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        destMaxSizePerMsgLimitedRB = new JRadioButton();
        destMaxSizePerMsgLimitedRB.addActionListener(this);
	destGridbag.setConstraints(destMaxSizePerMsgLimitedRB, destConstraints);
	destPanel.add(destMaxSizePerMsgLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destMaxSizePerMsgBF = new BytesField(0, Integer.MAX_VALUE, 10);
	destMaxSizePerMsgBF.addActionListener(this);
	destGridbag.setConstraints(destMaxSizePerMsgBF, destConstraints);
	destPanel.add(destMaxSizePerMsgBF);

        ButtonGroup maxMesgSizeGroup = new ButtonGroup();
        maxMesgSizeGroup.add(destMaxSizePerMsgUnlimitedRB);
        maxMesgSizeGroup.add(destMaxSizePerMsgLimitedRB);
	/*
	 * END Maximum Size per Message
	 */

	/*
	 * BEGIN Max Producer Count
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = newSectionInsets;
        maxProducerLbl = new JLabel(acr.getString(acr.I_BROKER_MAX_PRODUCERS));
	destGridbag.setConstraints(maxProducerLbl, destConstraints);
	destPanel.add(maxProducerLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        maxProducerUnlimitedRB = new JRadioButton();
        maxProducerUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(maxProducerUnlimitedRB, destConstraints);
	destPanel.add(maxProducerUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	maxProducerUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(maxProducerUnlimitedLbl, destConstraints);
	destPanel.add(maxProducerUnlimitedLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        maxProducerLimitedRB = new JRadioButton();
        maxProducerLimitedRB.addActionListener(this);
	destGridbag.setConstraints(maxProducerLimitedRB, destConstraints);
	destPanel.add(maxProducerLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = zeroInsets;
	maxProducerIF = new IntegerField(0, Integer.MAX_VALUE, 10);
	maxProducerIF.addActionListener(this);
	destGridbag.setConstraints(maxProducerIF, destConstraints);
	destPanel.add(maxProducerIF);

        ButtonGroup maxProducerGroup = new ButtonGroup();
        maxProducerGroup.add(maxProducerUnlimitedRB);
        maxProducerGroup.add(maxProducerLimitedRB);

	/*
	 * END Max Producer Count
	 */

	/*
	 * BEGIN Active Consumer Count
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
        activeConsumerLbl = new JLabel(acr.getString(acr.I_BROKER_ACTIVE_CONSUMER));
	destGridbag.setConstraints(activeConsumerLbl, destConstraints);
	destPanel.add(activeConsumerLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        activeConsumerUnlimitedRB = new JRadioButton();
        activeConsumerUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(activeConsumerUnlimitedRB, destConstraints);
	destPanel.add(activeConsumerUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	activeConsumerUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(activeConsumerUnlimitedLbl, destConstraints);
	destPanel.add(activeConsumerUnlimitedLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        activeConsumerLimitedRB = new JRadioButton();
        activeConsumerLimitedRB.addActionListener(this);
	destGridbag.setConstraints(activeConsumerLimitedRB, destConstraints);
	destPanel.add(activeConsumerLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = zeroInsets;
	activeConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 10);
	activeConsumerIF.addActionListener(this);
	destGridbag.setConstraints(activeConsumerIF, destConstraints);
	destPanel.add(activeConsumerIF);

        ButtonGroup activeConsumerGroup = new ButtonGroup();
        activeConsumerGroup.add(activeConsumerUnlimitedRB);
        activeConsumerGroup.add(activeConsumerLimitedRB);

	/*
	 * END Active Consumer Count
	 */

	/*
	 * BEGIN Failover Consumer Count
	 */
	destConstraints.gridx = 0;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = newSectionInsets;
        failoverConsumerLbl = new JLabel(acr.getString(acr.I_BROKER_FAILOVER_CONSUMER));
	destGridbag.setConstraints(failoverConsumerLbl, destConstraints);
	destPanel.add(failoverConsumerLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        failoverConsumerUnlimitedRB = new JRadioButton();
        failoverConsumerUnlimitedRB.addActionListener(this);
	destGridbag.setConstraints(failoverConsumerUnlimitedRB, destConstraints);
	destPanel.add(failoverConsumerUnlimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.insets = zeroInsets;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	failoverConsumerUnlimitedLbl = new JLabel(acr.getString(acr.I_BROKER_UNLIMITED));
	destGridbag.setConstraints(failoverConsumerUnlimitedLbl, destConstraints);
	destPanel.add(failoverConsumerUnlimitedLbl);

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	destConstraints.gridx = 0;
	destConstraints.gridy = i;
	destConstraints.insets = indentInsets;
        failoverConsumerLimitedRB = new JRadioButton();
        failoverConsumerLimitedRB.addActionListener(this);
	destGridbag.setConstraints(failoverConsumerLimitedRB, destConstraints);
	destPanel.add(failoverConsumerLimitedRB);

	destConstraints.gridx = 1;
	destConstraints.gridy = i++;
	destConstraints.gridwidth = GridBagConstraints.REMAINDER;
	destConstraints.insets = zeroInsets;
	failoverConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 10);
	failoverConsumerIF.addActionListener(this);
	destGridbag.setConstraints(failoverConsumerIF, destConstraints);
	destPanel.add(failoverConsumerIF);

        ButtonGroup failoverConsumerGroup = new ButtonGroup();
        failoverConsumerGroup.add(failoverConsumerUnlimitedRB);
        failoverConsumerGroup.add(failoverConsumerLimitedRB);

	/*
	 * END Failover Consumer Count
	 */

	/*
	 * Reset
	 */
	destConstraints.gridwidth = 1;

	return destPanel;
    }

    private void reset() {
	nameTF.setText("");

	queueRB.setSelected(true);

	activeConsumerLimitedRB.setSelected(true);
	activeConsumerIF.setText("1");
	doActiveConsumerLimited();

	failoverConsumerLimitedRB.setSelected(true);
	failoverConsumerIF.setText("0");
	doFailoverConsumerLimited();

	maxProducerLimitedRB.setSelected(true);
	maxProducerIF.setText("100");
	doMaxProducerLimited();

	queueSizeLimitUnlimitedRB.setSelected(true);
	queueSizeLimitBF.setText("0");
	queueSizeLimitBF.setUnit(BytesField.BYTES);
	doQueueSizeLimitUnlimited();

	queueMessageLimitUnlimitedRB.setSelected(true);
	queueMessageLimitTF.setText("0");
	doQueueMessageLimitUnlimited();

	destMaxSizePerMsgUnlimitedRB.setSelected(true);
	destMaxSizePerMsgBF.setText("0");
	destMaxSizePerMsgBF.setUnit(BytesField.BYTES);
	doDestMaxSizePerMsgUnlimited();

	showQueueProperties();
    }

    private boolean isValidString(String s) {
        if ((s == null) || ("".equals(s)))
            return false;
        else
            return true;
    }

    private void doActiveConsumerUnlimited()  {
	activeConsumerUnlimitedLbl.setEnabled(true);
        activeConsumerIF.setEnabled(false);
    }
    private void doActiveConsumerLimited()  {
	activeConsumerUnlimitedLbl.setEnabled(false);
        activeConsumerIF.setEnabled(true);
    }

    private void doFailoverConsumerUnlimited()  {
	failoverConsumerUnlimitedLbl.setEnabled(true);
        failoverConsumerIF.setEnabled(false);
    }
    private void doFailoverConsumerLimited()  {
	failoverConsumerUnlimitedLbl.setEnabled(false);
        failoverConsumerIF.setEnabled(true);
    }

    private void doMaxProducerUnlimited()  {
	maxProducerUnlimitedLbl.setEnabled(true);
        maxProducerIF.setEnabled(false);
    }
    private void doMaxProducerLimited()  {
	maxProducerUnlimitedLbl.setEnabled(false);
        maxProducerIF.setEnabled(true);
    }

    private void doQueueSizeLimitUnlimited()  {
	queueSizeLimitUnlimitedLbl.setEnabled(true);
        queueSizeLimitBF.setEnabled(false);
    }
    private void doQueueSizeLimitLimited()  {
	queueSizeLimitUnlimitedLbl.setEnabled(false);
        queueSizeLimitBF.setEnabled(true);
    }

    private void doQueueMessageLimitUnlimited()  {
        queueMessageLimitUnlimitedLbl.setEnabled(true);
        queueMessageLimitTF.setEnabled(false);
    }
    private void doQueueMessageLimitLimited()  {
        queueMessageLimitUnlimitedLbl.setEnabled(false);
        queueMessageLimitTF.setEnabled(true);
    }

    private void doDestMaxSizePerMsgUnlimited()  {
        destMaxSizePerMsgUnlimitedLbl.setEnabled(true);
        destMaxSizePerMsgBF.setEnabled(false);
    }
    private void doDestMaxSizePerMsgLimited()  {
        destMaxSizePerMsgUnlimitedLbl.setEnabled(false);
        destMaxSizePerMsgBF.setEnabled(true);
    }

    private void showQueueProperties() {
	/* 
	 * Enable Active Consumer
	 */
	activeConsumerLbl.setEnabled(true);
	activeConsumerUnlimitedRB.setEnabled(true);
	activeConsumerUnlimitedLbl.setEnabled(true);
	activeConsumerLimitedRB.setEnabled(true);
	
	if (activeConsumerUnlimitedRB.isSelected())
	    doActiveConsumerUnlimited();
	else if (activeConsumerLimitedRB.isSelected())
	    doActiveConsumerLimited();

	/* 
	 * Enable Failover Consumer
	 */
	failoverConsumerLbl.setEnabled(true);
	failoverConsumerUnlimitedRB.setEnabled(true);
	failoverConsumerUnlimitedLbl.setEnabled(true);
	failoverConsumerLimitedRB.setEnabled(true);
	
	if (failoverConsumerUnlimitedRB.isSelected())
	    doFailoverConsumerUnlimited();
	else if (failoverConsumerLimitedRB.isSelected())
	    doFailoverConsumerLimited();

        /*
         * Enable Queue Size Limit
         */
        QSizeLimit.setEnabled(true);
        queueSizeLimitUnlimitedRB.setEnabled(true);
        queueSizeLimitLimitedRB.setEnabled(true);

	if (queueSizeLimitUnlimitedRB.isSelected())
	    doQueueSizeLimitUnlimited();
	else if (queueSizeLimitLimitedRB.isSelected())
	    doQueueSizeLimitLimited();

        /*
         * Enable Queue Message Limit
         */
        QMessageLimit.setEnabled(true);
        queueMessageLimitUnlimitedRB.setEnabled(true);
        queueMessageLimitLimitedRB.setEnabled(true);

	if (queueMessageLimitUnlimitedRB.isSelected())
	    doQueueMessageLimitUnlimited();
	else if (queueMessageLimitLimitedRB.isSelected())
	    doQueueMessageLimitLimited();
    }

    private void showTopicProperties() {
        /*
	 * Disable Active Consumer Count
	 */
	activeConsumerLbl.setEnabled(false);
	activeConsumerUnlimitedRB.setEnabled(false);
	activeConsumerLimitedRB.setEnabled(false);
	activeConsumerUnlimitedLbl.setEnabled(false);
	activeConsumerIF.setEnabled(false);

        /*
	 * Disable Failover Consumer Count
	 */
	failoverConsumerLbl.setEnabled(false);
	failoverConsumerUnlimitedRB.setEnabled(false);
	failoverConsumerLimitedRB.setEnabled(false);
	failoverConsumerUnlimitedLbl.setEnabled(false);
	failoverConsumerIF.setEnabled(false);
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
        Object source = e.getSource();

	if (source == topicRB)  {
	    showTopicProperties();
	} else if (source == queueRB)  {
	    showQueueProperties();
	} else if (source == activeConsumerUnlimitedRB)  {
            doActiveConsumerUnlimited();
	} else if (source == activeConsumerLimitedRB)  {
            doActiveConsumerLimited();
	} else if (source == failoverConsumerUnlimitedRB)  {
            doFailoverConsumerUnlimited();
	} else if (source == failoverConsumerLimitedRB)  {
            doFailoverConsumerLimited();
	} else if (source == maxProducerUnlimitedRB)  {
            doMaxProducerUnlimited();
	} else if (source == maxProducerLimitedRB)  {
            doMaxProducerLimited();
	} else if (source == queueSizeLimitUnlimitedRB)  {
            doQueueSizeLimitUnlimited();
	} else if (source == queueSizeLimitLimitedRB)  {
            doQueueSizeLimitLimited();

	} else if (source == queueMessageLimitUnlimitedRB)  {
            doQueueMessageLimitUnlimited();
	} else if (source == queueMessageLimitLimitedRB)  {
            doQueueMessageLimitLimited();

	} else if (source == destMaxSizePerMsgUnlimitedRB)  {
	    doDestMaxSizePerMsgUnlimited();
	} else if (source == destMaxSizePerMsgLimitedRB)  {
	    doDestMaxSizePerMsgLimited();

	} else  {
	    super.actionPerformed(e);
	}
    }
    /*
     * END INTERFACE ActionListener
     */
 }
