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
 * @(#)BrokerDestPropsDialog.java	1.34 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Enumeration;
import java.util.Vector;

import java.awt.Insets;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.DurableInfo;
import com.sun.messaging.jmq.util.DestLimitBehavior;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.apps.console.util.BytesField;
import com.sun.messaging.jmq.admin.apps.console.util.IntegerField;
import com.sun.messaging.jmq.admin.apps.console.util.SpecialValueField;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;


/** 
 * This dialog is used to display the properties of a physical destination 
 * on the broker.
 */
public class BrokerDestPropsDialog extends AdminDialog 
	implements ListSelectionListener,
	BrokerConstants {

    private final static int UNLIMITED_VALUE_0 = 0;
    private final static int UNLIMITED_VALUE_NEG1 = -1;  // for active/failover consumers

    private static AdminResources ar = Globals.getAdminResources();
    private static AdminConsoleResources acr = 
			Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    private static String[] columnNames = 
		{ar.getString(ar.I_JMQCMD_SUB_NAME),
    	    	 ar.getString(ar.I_JMQCMD_CLIENT_ID),
    		 ar.getString(ar.I_JMQCMD_DURABLE),
    		 ar.getString(ar.I_JMQCMD_SUB_NUM_MSG),
    		 ar.getString(ar.I_JMQCMD_SUB_STATE)};

    /*
     * The tabbed pane and the individual tabs.
     */
    private JTabbedPane         tabbedPane;
    private JPanel              basicPanel;
    private JPanel              durPanel;

    /*
     * Applicable to Queues and Topics.
     * Basic destination info.
     */
    private JLabel 		destNameValue;
    private JLabel 		destTypeValue;
    private JLabel 		destStateValue;
    /*
     * Applicable to Queues and Topics.
     * Current destination info.
     */
    private JLabel 		curNumProducers;
    private LabelledComponent   curNumProducersLabelC;
    private JLabel 		curNumActive;
    private LabelledComponent   curNumActiveLabelC;
    private JLabel 		curNumFailover;
    private LabelledComponent   curNumFailoverLabelC;
    private JLabel 		curNumMesgsValue;
    private JLabel 		curNumMesgBytesValue;

    /*
     * Applicable to Queues only.
     * Active Consumer Count
     */
    private IntegerField        activeConsumerIF;
    private LabelledComponent	activeConsumerLabelC;
    private SpecialValueField	activeConsumerSF;

    /*
     * Applicable to Queues only.
     * Failover Consumer Count
     */
    private IntegerField        failoverConsumerIF;
    private LabelledComponent	failoverConsumerLabelC;
    private SpecialValueField	failoverConsumerSF;

    /*
     * Applicable to Queues and Topics.
     * Max Producer Count
     */
    private IntegerField        maxProducerIF;
    private LabelledComponent	maxProducerLabelC;
    private SpecialValueField	maxProducerSF;

    /*
     * Applicable to Queues and Topics.
     * Components for supporting Queue Size Limit.
     */
    private BytesField         	mesgSizeLimitBF;
    private LabelledComponent	mesgSizeLimitLabelC;
    private SpecialValueField	mesgSizeLimitSF;

    /*
     * Applicable to Queues and Topics.
     * Components for supporting Queue Message Limit.
     */
    IntegerField        	mesgLimitIF;
    SpecialValueField        	mesgLimitSF;
    private LabelledComponent	mesgLimitLabelC;

    /*
     * Applicable to Queues and Topics.
     * Components for supporting Destination Maximum Size per Message.
     */
    BytesField          	maxSizePerMsgBF;
    SpecialValueField          	maxSizePerMsgSF;

    /*
     * Applicable to Topics only.
     * Durable Subscriptions related components.
     */
    private PropsTableModel     model;
    private JTable              table;
    private JScrollPane         scrollPane;
    private JButton 		deleteButton, purgeButton;

    /*
     * Applicable to Queus and Topics.
     * Limit behavior and use DMQ.
     */
    private JComboBox		limitBehaviorCb;
    private JCheckBox		useDMQCkb;

    /*
     * These should always contain the latest info sent from the broker
     * before show().
     */
    private DestinationInfo destInfo; 
    private Vector durables;

    /*
     * These are used to indicate which durable subscription is currently 
     * selected.
     */
    private int selectedRow = -1; 
    private String selectedDurName = null;
    private String selectedClientID = null;

    private boolean	resetScrollbarPolicy = true;


    public BrokerDestPropsDialog(Frame parent)  {
	super(parent, 
	      acr.getString(acr.I_BROKER_DEST_PROPS),
	      (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.BROKER_DEST_PROPS);
    }

    public void doClose() {
	hide();
	reset();
    }

    // not used
    public void doApply() {}
    public void doReset() {}
    public void doClear() {}

    public void doCancel() { 
	hide(); 
    }

    public void doOK() {
	BrokerAdminEvent bae =
		    new BrokerAdminEvent(this, BrokerAdminEvent.UPDATE_DEST);
	DestinationInfo destInfo = getUpdateDestinationInfo();

	bae.setDestinationInfo(destInfo);

        bae.setOKAction(true);
        fireAdminEventDispatched(bae);
    }

    private DestinationInfo getUpdateDestinationInfo()  {
	int  activeConsumers = -1;
	int  failoverConsumers = -1;
	int  maxProducers = -1;
        long mesgSizeLimitValue = -1;
        int  mesgLimitValue = -1;
        long maxSizePerMsgValue = -1;
	boolean useDMQ;
	int limitBehavior;
	DestinationInfo	updateDestInfo = new DestinationInfo();

	/*
	 * We check if the value set in the GUI differs from the
	 * original value. It is set (and set to the broker) only
	 * if it is different. We do this to prevent sending
	 * across values for properties that did not change. In some
	 * cases, this is harmless. But for some (e.g. for the DMQ),
	 * updates to certain properties is not allowed and the old
	 * behavior will cause an error.
	 */

	if (DestType.isQueue(destInfo.type)) { 
	    /*
	     * Max Active Consumers
	     */
	    if (activeConsumerSF.isSpecialValueSet())  {
	        activeConsumers = UNLIMITED_VALUE_NEG1;
	    } else  {
	        activeConsumers 
			= Integer.parseInt(activeConsumerIF.getText());
	    }

	    if (activeConsumers != destInfo.maxActiveConsumers)  {
		updateDestInfo.setMaxActiveConsumers(activeConsumers);
	    }

	    /*
	     * Max Backup Consumers
	     */
	    if (failoverConsumerSF.isSpecialValueSet())  {
	        failoverConsumers = UNLIMITED_VALUE_NEG1;
	    } else  {
	        failoverConsumers 
			= Integer.parseInt(failoverConsumerIF.getText());
	    }

	    if (failoverConsumers != destInfo.maxFailoverConsumers)  {
		updateDestInfo.setMaxFailoverConsumers(failoverConsumers);
	    }
	}

	/*
	 * Max Producers
	 */
	if (maxProducerSF.isSpecialValueSet())  {
	    maxProducers = UNLIMITED_VALUE_NEG1;
	} else  {
	    maxProducers = Integer.parseInt(maxProducerIF.getText());
	}

	if (maxProducers != destInfo.maxProducers)  {
	    updateDestInfo.setMaxProducers(maxProducers);
	}

	/*
	 * Max Total Message Bytes
	 */
	if (mesgSizeLimitSF.isSpecialValueSet())  {
	    mesgSizeLimitValue = UNLIMITED_VALUE_NEG1;
	} else  {
	    mesgSizeLimitValue = mesgSizeLimitBF.getValue();
	}

	if (mesgSizeLimitValue != destInfo.maxMessageBytes)  {
	    updateDestInfo.setMaxMessageBytes(mesgSizeLimitValue);
	}

	/*
	 * Max Number of Messages
	 */
	if (mesgLimitSF.isSpecialValueSet())  {
	    mesgLimitValue = (int)UNLIMITED_VALUE_NEG1;
	} else  {
	    mesgLimitValue = Integer.parseInt(mesgLimitIF.getText());
	}

	if (mesgLimitValue != destInfo.maxMessages)  {
	    updateDestInfo.setMaxMessages(mesgLimitValue);
	}

	/*
	 * Max Bytes per Messages
	 */
	if (maxSizePerMsgSF.isSpecialValueSet())  {
	    maxSizePerMsgValue = UNLIMITED_VALUE_NEG1;
	} else  {
            maxSizePerMsgValue = maxSizePerMsgBF.getValue();
	}

	if (maxSizePerMsgValue != destInfo.maxMessageSize)  {
	    updateDestInfo.setMaxMessageSize(maxSizePerMsgValue);
	}

	/*
	 * Limit behavior
	 */
	limitBehavior = getLimitBehavValue(
			(String)limitBehaviorCb.getSelectedItem());
	if (limitBehavior != destInfo.destLimitBehavior)  {
	    updateDestInfo.setLimitBehavior(limitBehavior);
	}

	/*
	 * Use DMQ
	 */
	useDMQ = useDMQCkb.isSelected();
	if (useDMQ != destInfo.useDMQ())  {
	    updateDestInfo.setUseDMQ(useDMQ);
	}

	return (updateDestInfo);
    }

    public JPanel createWorkPanel()  {
        JPanel 			workPanel;
        GridBagLayout		workGridbag;
        GridBagConstraints	workConstraints;
	LabelledComponent	tmpLabelC;
	LabelledComponent	lvpItems[];
	LabelValuePanel		lvp;

        workPanel = new JPanel();

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(acr.getString(acr.I_DEST_PROP_BASIC), makeBasicTab());
        tabbedPane.addTab(acr.getString(acr.I_DEST_PROP_SUB), makeDurTab());

        workPanel.add(tabbedPane);

	return (workPanel);
    }

    private JPanel makeBasicTab() {
        JPanel                  basicPanel;
        GridBagLayout           basicGridbag;
        GridBagConstraints      basicConstraints;
        LabelledComponent       tmpLabelC;
        LabelledComponent       lvpItems[];
        LabelValuePanel         lvp;
	int			i = 0;

        basicPanel = new JPanel();
        basicGridbag = new GridBagLayout();
        basicConstraints = new GridBagConstraints();
        basicPanel.setLayout(basicGridbag);

	basicConstraints.gridx = 0;
	basicConstraints.anchor = GridBagConstraints.WEST;
	basicConstraints.fill = GridBagConstraints.NONE;
	basicConstraints.insets = new Insets(10, 0, 10, 0);
	basicConstraints.ipadx = 0;
	basicConstraints.ipady = 0;
	basicConstraints.weightx = 1.0;

	/*
         * Basic destination info: name, type, state
         */
        lvpItems = new LabelledComponent[3];

        destNameValue = new JLabel();
        tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_DEST_NAME),
                                        destNameValue);
        lvpItems[0] = tmpLabelC;

        destTypeValue = new JLabel();
        tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_DEST_TYPE),
                                        destTypeValue);
        lvpItems[1] = tmpLabelC;

        destStateValue = new JLabel();
        tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_DEST_STATE) + ":",
                                        destStateValue);
        lvpItems[2] = tmpLabelC;

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 0;
        lvp = new LabelValuePanel(lvpItems, 4, 3);
        basicGridbag.setConstraints(lvp, basicConstraints);
        basicPanel.add(lvp);

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 1;
        basicConstraints.insets = new Insets(0, 0, 0, 0);
        basicConstraints.fill = GridBagConstraints.HORIZONTAL;
        JSeparator separator = new JSeparator();
        basicGridbag.setConstraints(separator, basicConstraints);
	basicPanel.add(separator);

        /*
         * Reset
         */
        basicConstraints.gridwidth = 1;
	basicConstraints.fill = GridBagConstraints.NONE;

	/*
         * Current number or message size / bytes info
         */
        lvpItems = new LabelledComponent[5];

        curNumMesgsValue = new JLabel();
        tmpLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_DEST_NUM_MSGS),
                         curNumMesgsValue);
        lvpItems[i++] = tmpLabelC;

        curNumMesgBytesValue = new JLabel();
        tmpLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_DEST_TTL_SIZE_MSGS),
                         curNumMesgBytesValue, acr.getString(acr.I_BYTES));
        lvpItems[i++] = tmpLabelC;

	curNumProducers = new JLabel();
        curNumProducersLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_DEST_NUM_PRODUCERS),
                         curNumProducers);
        lvpItems[i++] = curNumProducersLabelC;

	curNumActive = new JLabel();
        curNumActiveLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_CUR_NUM_ACTIVE),
                         curNumActive);
        lvpItems[i++] = curNumActiveLabelC;

	curNumFailover = new JLabel();
        curNumFailoverLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_CUR_NUM_FAILOVER),
                         curNumFailover);
        lvpItems[i++] = curNumFailoverLabelC;

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 2;
        basicConstraints.insets = new Insets(10, 0, 10, 0);
        lvp = new LabelValuePanel(lvpItems, 4, 5);
        basicGridbag.setConstraints(lvp, basicConstraints);
        basicPanel.add(lvp);

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 3;
        basicConstraints.insets = new Insets(0, 0, 0, 0);
        basicConstraints.fill = GridBagConstraints.HORIZONTAL;
        separator = new JSeparator();
        basicGridbag.setConstraints(separator, basicConstraints);
        basicPanel.add(separator);

        /*
         * Reset
         */
        basicConstraints.gridwidth = 1;
	basicConstraints.fill = GridBagConstraints.NONE;
	i = 0;

	lvpItems = new LabelledComponent[6];

	/*
	 * Queue message limit
	 */
        mesgLimitIF = new IntegerField(0, Integer.MAX_VALUE, 11);
        mesgLimitSF = new SpecialValueField(mesgLimitIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	mesgLimitLabelC = new LabelledComponent
				(acr.getString(acr.I_BROKER_MAX_NUM_MSGS),
				 mesgLimitSF, LabelledComponent.NORTH);
	lvpItems[i++] = mesgLimitLabelC;

	/*
	 * Queue size limit
	 */
        mesgSizeLimitBF = new BytesField(0, Long.MAX_VALUE, 11);
        mesgSizeLimitSF = new SpecialValueField(mesgSizeLimitBF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	mesgSizeLimitLabelC = new LabelledComponent
				(acr.getString(acr.I_BROKER_MAX_TTL_SIZE_MSGS),
				 mesgSizeLimitSF, LabelledComponent.NORTH);
	lvpItems[i++] = mesgSizeLimitLabelC;

        /*
         * Destination Maximum Size per Message
         */
        maxSizePerMsgBF = new BytesField(0, Long.MAX_VALUE, 11);
        maxSizePerMsgSF = new SpecialValueField(maxSizePerMsgBF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	tmpLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_MAX_SIZE_PER_MSG),
			 maxSizePerMsgSF, LabelledComponent.NORTH);
	lvpItems[i++] = tmpLabelC;

	/*
	 * Max Producers
	 */
        maxProducerIF = new IntegerField(0, Integer.MAX_VALUE, 11);
        maxProducerSF = new SpecialValueField(maxProducerIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	maxProducerLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_MAX_PRODUCERS),
			 maxProducerSF, LabelledComponent.NORTH);
	lvpItems[i++] = maxProducerLabelC;

	/*
	 * Active Consumers
	 */
        activeConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 11);
        activeConsumerSF = new SpecialValueField(activeConsumerIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	activeConsumerLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_ACTIVE_CONSUMER),
			 activeConsumerSF, LabelledComponent.NORTH);
	lvpItems[i++] = activeConsumerLabelC;

	/*
	 * Failover Consumers
	 */
        failoverConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 11);
        failoverConsumerSF = new SpecialValueField(failoverConsumerIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	failoverConsumerLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_FAILOVER_CONSUMER),
			 failoverConsumerSF, LabelledComponent.NORTH);
	lvpItems[i++] = failoverConsumerLabelC;

        lvp = new LabelValuePanel(lvpItems, 4, 5);

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 4;
        basicConstraints.insets = new Insets(10, 0, 10, 0);
        basicGridbag.setConstraints(lvp, basicConstraints);
        basicPanel.add(lvp);

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 5;
        basicConstraints.insets = new Insets(0, 0, 0, 0);
        basicConstraints.fill = GridBagConstraints.HORIZONTAL;
        separator = new JSeparator();
        basicGridbag.setConstraints(separator, basicConstraints);
        basicPanel.add(separator);

        /*
         * Reset
         */
        basicConstraints.gridwidth = 1;
	basicConstraints.fill = GridBagConstraints.NONE;
	i = 0;

	/*
	 * Limit Behavior, Use Dead Message Queue
	 */
        lvpItems = new LabelledComponent[2];

        limitBehaviorCb = new JComboBox(BKR_LIMIT_BEHAV_VALID_VALUES.toArray(
                                        new String[BKR_LIMIT_BEHAV_VALID_VALUES.size()]));
	tmpLabelC = new LabelledComponent(
			acr.getString(acr.I_BROKER_LIMIT_BEHAVIOR),
			limitBehaviorCb);
        lvpItems[i++] = tmpLabelC;

        useDMQCkb = new JCheckBox();
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_USE_DMQ),
			useDMQCkb);
        lvpItems[i++] = tmpLabelC;

        basicConstraints.gridx = 0;
        basicConstraints.gridy = 6;
        basicConstraints.insets = new Insets(10, 0, 10, 0);
        lvp = new LabelValuePanel(lvpItems, 4, 2);
        basicGridbag.setConstraints(lvp, basicConstraints);
        basicPanel.add(lvp);


	return (basicPanel);
    }

    private JPanel makeDurTab() {
        JPanel                  durPanel;
	JLabel			tmpLabel;
        GridBagLayout           durGridbag;
        GridBagConstraints      durConstraints;

        durPanel = new JPanel();
        durGridbag = new GridBagLayout();
        durConstraints = new GridBagConstraints();
        durPanel.setLayout(durGridbag);

	/*
	 * Calculations to determine the
	 * preferred width for the table.
	 *
	 * We use the length of the longest column header
	 * (as displayed in a JLabel) multiplied by the number 
	 * of columns.
	 */
	int colWidth = 0, tmpWidth = 0, maxWidth = 0;
	for (int i = 0; i < columnNames.length; ++i)  {
	    tmpLabel = new JLabel(columnNames[i]);
	    tmpWidth = tmpLabel.getPreferredSize().width;

	    if (tmpWidth > maxWidth)  {
		maxWidth = tmpWidth;
	    }
	}

	colWidth = maxWidth * columnNames.length;

        model = new PropsTableModel();
        table = new JTable(model);
	//int w2 = table.getColumnModel().getTotalColumnWidth();
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel lsm = table.getSelectionModel();
        lsm.addListSelectionListener(this);

	/*
	 * Set vertical scrollbar policy to VERTICAL_SCROLLBAR_ALWAYS
	 * initially to workaround a layout problem which occurs when
	 * a scrollbar is actually needed.
	 *
	 * The policy is reset back to VERTICAL_SCROLLBAR_AS_NEEDED
	 * in show().
	 */
        scrollPane = new JScrollPane(table, 
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        table.setPreferredScrollableViewportSize(new Dimension(colWidth,
                                                 21 * table.getRowHeight()));

        durConstraints.gridx = 0;
        durConstraints.gridy = 0;
        durGridbag.setConstraints(scrollPane, durConstraints);
        durPanel.add(scrollPane);

	JPanel buttonPanel = new JPanel();
	buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

	deleteButton = new JButton(acr.getString(acr.I_DELETE));
	deleteButton.setEnabled(false);
        deleteButton.addActionListener(this);
	buttonPanel.add(deleteButton);

	purgeButton = new JButton(acr.getString(acr.I_PURGE));
	purgeButton.setEnabled(false);
        purgeButton.addActionListener(this);
        buttonPanel.add(purgeButton);

        durConstraints.gridx = 0;
        durConstraints.gridy = 1;
	durConstraints.anchor = GridBagConstraints.EAST;
        durConstraints.insets = new Insets(0, 5, 0, 0);
        durGridbag.setConstraints(buttonPanel, durConstraints);
        durPanel.add(buttonPanel);

	return (durPanel);
    }

    public void show(DestinationInfo destInfo, Vector durables) {
	int 		value;
	long 		lvalue;
	String 		svalue;
	Dimension	d;
	int		prefWidth, totalWidth;

	this.destInfo = destInfo;
	this.durables = durables;

	reset();

	/*
	 * Enable/disable appropriate components.
	 */
	if (DestType.isQueue(destInfo.type)) { 
	    /*
	     * Durable tab
	     */
	    tabbedPane.setEnabledAt(1, false);

	    /* 
	     * Enable Active/Failover Consumers for Queue
	     */
	    setActiveConsumersEnabled(true);
	    setFailoverConsumersEnabled(true);

	    /*
	     * Enable Cur Number of Actve/Failover Consumers.
	     */
	    curNumFailoverLabelC.setEnabled(true);	
	    curNumFailover.setEnabled(true);	

	    /* 
	     * Populate the data into the Active/Failover fields.
	     */
	    value = destInfo.maxActiveConsumers;
	    if (value != UNLIMITED_VALUE_NEG1)
	        activeConsumerIF.setText(String.valueOf(value));
	    checkUnlimitedNeg1(activeConsumerSF, value);

	    value = destInfo.maxFailoverConsumers;
	    if (value != UNLIMITED_VALUE_NEG1)
	        failoverConsumerIF.setText(String.valueOf(value));
	    checkUnlimitedNeg1(failoverConsumerSF, value);

	    curNumActiveLabelC.setLabelText(acr.getString(acr.I_BROKER_CUR_NUM_ACTIVE));
            curNumActive.setText(String.valueOf(destInfo.naConsumers));

	} else if (DestType.isTopic(destInfo.type)) { 
	    /*
	     * Durable tab
	     */
	    tabbedPane.setEnabledAt(1, true);

	    /* 
	     * Disable Active/Failover Consumers for Topic
	     */
	    setActiveConsumersEnabled(false);
	    setFailoverConsumersEnabled(false);

	    /*
	     * Disable Cur Number of Active/Failover Consumers.
	     */
	    curNumFailoverLabelC.setEnabled(false);	
	    curNumFailover.setEnabled(false);	

	    curNumActiveLabelC.setLabelText(
			acr.getString(acr.I_BROKER_CUR_NUM_CONSUMERS));
            curNumActive.setText(String.valueOf(destInfo.nConsumers));
	}

	/*
	 * Populate fields with more information
	 */

	/*
	 * Basic destination info (dest name, dest type, dest state)
	 */
	destNameValue.setText(destInfo.name);
	destTypeValue.setText
		(BrokerAdminUtil.getDestinationType(destInfo.type));
       // destStateValue.setText(DestState.toString(destInfo.destState));
	    destStateValue.setText
	    (BrokerAdminUtil.getDestinationState(destInfo.destState));
	/*
	 * Current destination info.
	 */ 
        curNumProducers.setText(String.valueOf(destInfo.nProducers));
        curNumFailover.setText(String.valueOf(destInfo.nfConsumers));
        curNumMesgsValue.setText(String.valueOf(destInfo.nMessages));
        curNumMesgBytesValue.setText(String.valueOf(destInfo.nMessageBytes));

	/*
	 * Max info.
	 */
	value = destInfo.maxProducers;
	if (value != UNLIMITED_VALUE_NEG1)
	    maxProducerIF.setText(String.valueOf(value));
	checkUnlimitedNeg1(maxProducerSF, value);

	lvalue = destInfo.maxMessageBytes;
	if (lvalue != UNLIMITED_VALUE_NEG1)
	    mesgSizeLimitBF.setText(String.valueOf(lvalue));
	checkBothUnlimited(mesgSizeLimitSF, lvalue);

	value = destInfo.maxMessages;
	if (value != UNLIMITED_VALUE_NEG1)
	    mesgLimitIF.setText(String.valueOf(value));
	checkBothUnlimited(mesgLimitSF, value);

	/*
	 * Max size per msg - applicable to both queues and topics.
	 */
	lvalue = destInfo.maxMessageSize;
	if (lvalue != UNLIMITED_VALUE_NEG1)
	    maxSizePerMsgBF.setText(String.valueOf(lvalue));
	checkBothUnlimited(maxSizePerMsgSF, lvalue);

	/*
	 * Limit behavior
	 */
	svalue = DestLimitBehavior.getString(destInfo.destLimitBehavior);
	limitBehaviorCb.setSelectedItem(svalue);

	/*
	 * Use DMQ
	 */
	if (destInfo.useDMQ())  {
            useDMQCkb.setSelected(true);
	} else  {
            useDMQCkb.setSelected(false);
	}

	/*
	 * Durable subscriptions info.
	 */
        if (DestType.isTopic(destInfo.type)) {
	    // Columns of the table are intact
            model.fireTableDataChanged();
	    clearSelection();
	}

	/*
	 * Reset the vertical scrollbar policy back to 
	 * VERTICAL_SCROLLBAR_AS_NEEDED.
	 * This is to workaround the bug where if it was initially 
	 * VERTICAL_SCROLLBAR_AS_NEEDED and later a scrollbar 
	 * was* needed. The extra width needed would cause some layout 
	 * problems.
	 *
	 * By forcing the scrollbar to be on always initially, we 
	 * 'claim' the space needed later for when a scrollbar is needed. 
	 * At least it appears to work that way.
	 */
	if (resetScrollbarPolicy)  {
	    scrollPane.setVerticalScrollBarPolicy
		(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
	    resetScrollbarPolicy = false;
	}

	/*
	 * Resize the viewable area of table in the scrollpane.
	 * This is to workaround the case where the collumn headers
	 * are really narrow e.g. one letter names.
	 *
	 * If such names are used, the calculations done in makeDurTab()
	 * produce widths that don't appear to be wide enough. Also,
	 * table.getColumnModel().getTotalColumnWidth() will return
	 * a 'satisfactory' width only after the table/scrollpane has
	 * been fully inserted into the instance hierarchy - not the
	 * case in makeDurTab().
	 */
	d = table.getPreferredScrollableViewportSize();
	prefWidth = d.width;
        totalWidth = table.getColumnModel().getTotalColumnWidth();
	if (prefWidth < totalWidth)  {
	    d.width = totalWidth;
	    table.setPreferredScrollableViewportSize(d);
	}

	super.show();
    }

    public void refresh(Vector durables) {

	this.durables = durables;

        model.fireTableChanged(new TableModelEvent(model)); 
        clearSelection();
    }

    private void setActiveConsumersEnabled(boolean enable) {
        activeConsumerLabelC.setEnabled(enable);
        activeConsumerSF.setSpecialValueSet(enable);
        activeConsumerIF.setEnabled(enable);
    }

    private void setFailoverConsumersEnabled(boolean enable) {
        failoverConsumerLabelC.setEnabled(enable);
        failoverConsumerSF.setSpecialValueSet(enable);
        failoverConsumerIF.setEnabled(enable);
    }
    private void reset() {
        destNameValue.setText("");
        destTypeValue.setText("");
	destStateValue.setText("");

	curNumActive.setText("");
	curNumFailover.setText("");
        curNumMesgsValue.setText("");
        curNumMesgBytesValue.setText("");

        activeConsumerSF.setEnabled(true);
        activeConsumerSF.setSpecialValueSet(true);
        activeConsumerIF.setText("0");

        failoverConsumerSF.setEnabled(true);
        failoverConsumerSF.setSpecialValueSet(true);
        failoverConsumerIF.setText("0");

        mesgSizeLimitSF.setEnabled(true);
        mesgSizeLimitSF.setSpecialValueSet(true);
        mesgSizeLimitBF.setText("0");
        mesgSizeLimitBF.setUnit(BytesField.BYTES);

        mesgLimitSF.setEnabled(true);
        mesgLimitSF.setSpecialValueSet(true);
        mesgLimitIF.setText("0");

        maxSizePerMsgSF.setEnabled(true);
        maxSizePerMsgSF.setSpecialValueSet(true);
        maxSizePerMsgBF.setText("0");
        maxSizePerMsgBF.setUnit(BytesField.BYTES);

        limitBehaviorCb.setSelectedItem(BKR_LIMIT_BEHAV_VALID_VALUES.get(0));
        useDMQCkb.setSelected(true);

	clearSelection();
	deleteButton.setEnabled(false);
	purgeButton.setEnabled(false);

	tabbedPane.setSelectedIndex(0);
    }

    private void clearSelection() {
        if (table != null)
            table.clearSelection();

	/*
	 * Disable delete, purge button.
	 */
	deleteButton.setEnabled(false);
	purgeButton.setEnabled(false);

	/*
	 * Reset selected row.
	 */
	selectedRow = -1;
	selectedDurName = null;
	selectedClientID = null;
    }

    private void doDelete()  {
	if ((selectedRow > -1) && 
	    (selectedDurName != null) && (selectedClientID != null)) {

	    /*
 	     * Dispatch the admin event.
	     */
            BrokerAdminEvent bae = 
		new BrokerAdminEvent(this, BrokerAdminEvent.DELETE_DUR);
       	    bae.setDurableName(selectedDurName);
       	    bae.setClientID(selectedClientID);
            bae.setOKAction(false);
            fireAdminEventDispatched(bae);
  	}
    }

    private void doPurge()  {
	if ((selectedRow > -1) && 
	    (selectedDurName != null) && (selectedClientID != null)) {

	    /*
 	     * Dispatch the admin event.
	     */
            BrokerAdminEvent bae = 
		new BrokerAdminEvent(this, BrokerAdminEvent.PURGE_DUR);
       	    bae.setDurableName(selectedDurName);
       	    bae.setClientID(selectedClientID);
            bae.setOKAction(false);
            fireAdminEventDispatched(bae);
  	}
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
        Object source = e.getSource();

        if (source == deleteButton)  {
	    doDelete();
        } else if (source == purgeButton) {
	    doPurge();
	} else {
            super.actionPerformed(e);
        }
    }
    /*
     * END INTERFACE ActionListener
     */

    /*
    private void checkUnlimited0(SpecialValueField sf, long val)  {
	if (valueIsUnlimited0(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }
    */

    private void checkUnlimitedNeg1(SpecialValueField sf, long val)  {
	if (valueIsUnlimitedNeg1(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }

    /* 
     * Checks  for both 0 and -1 values if unlimited.
     */
    private void checkBothUnlimited(SpecialValueField sf, long val)  {
	if (valueIsUnlimited0(val) || valueIsUnlimitedNeg1(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }

    private boolean valueIsUnlimited0(long val)  {
	if (val == UNLIMITED_VALUE_0)  {
	    return (true);
	}

	return (false);
    }

    private boolean valueIsUnlimitedNeg1(long val)  {
	if (val == UNLIMITED_VALUE_NEG1)  {
	    return (true);
	}

	return (false);
    }


    /*
     * BEGIN INTERFACE ListSelectionListener
     */
    public void valueChanged(ListSelectionEvent e)  {
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        boolean isAdjusting = e.getValueIsAdjusting();

	if (isAdjusting) {
            if (lsm.isSelectionEmpty()) {
                deleteButton.setEnabled(false);
                purgeButton.setEnabled(false);
	    } else {
                selectedRow = lsm.getMinSelectionIndex();
                selectedDurName = (String)model.getValueAt(selectedRow, 0);
                selectedClientID = (String)model.getValueAt(selectedRow, 1);
	    }
	}
        String durable = (String)model.getValueAt(selectedRow, 2);
        if (durable != null && durable.trim().toLowerCase().equals("true")) {
            deleteButton.setEnabled(true);
            purgeButton.setEnabled(true);
        } else {
            deleteButton.setEnabled(false);
            purgeButton.setEnabled(false);
        }
    }
    /*
     * END INTERFACE ListSelectionListener
     */

    class PropsTableModel extends AbstractTableModel {
        /**
         * Returns the number of collumns in table.
         *
         * @return The number of collumns in table.
         */
        public int getColumnCount() {

            return columnNames.length;
        }

        /**
         * Returns the number of rows in table.
         *
         * @return The number of rows in table.
         */
        public int getRowCount() {
            if (durables == null)
                return 0;
            else
                return durables.size();
        }

        /**
         * Returns the collumn name/label for a given column.
         *
         * @return the column name/label for a given column.
         */
        public String getColumnName(int col) {
            return columnNames[col];
        }

        /**
         * Return value at a particular table cell location.
         * Calls the TabledInspector.getValueAtColumn()
         * method.
         */
        public Object getValueAt(int row, int col) {
            if (durables == null)
                return "";

	    int i = 0;
            Enumeration e = durables.elements();
            while (e.hasMoreElements()) {
                DurableInfo durInfo = (DurableInfo)e.nextElement();

                if (col == 0 && i == row)
		    return ((durInfo.name == null) ? "" : durInfo.name);	
                else if (col == 1 && i == row)
		    return ((durInfo.clientID == null) ? "" : durInfo.clientID);	
                else if (col == 2 && i == row)
                    return String.valueOf(durInfo.isDurable);
                else if (col == 3 && i == row)
		    return Integer.toString(durInfo.nMessages);
                else if (col == 4 && i == row) {
                    if (durInfo.isActive)
			return ar.getString(ar.I_ACTIVE);
                    else
			return ar.getString(ar.I_INACTIVE);
		}
		i++;
            }
            return "";
        }

        /**
         * Don't need to implement this method unless your table's
         * data can change.
         */
        public void setValueAt(Object value, int row, int col) {
        }
    }

    private int getLimitBehavValue(String limitBehavStr)  {
	int ret = DestLimitBehavior.UNKNOWN;

	if (limitBehavStr == null)
	    return (ret);

	if (limitBehavStr.equals(LIMIT_BEHAV_FLOW_CONTROL))  {
	    ret = DestLimitBehavior.FLOW_CONTROL;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_RM_OLDEST))  {
	    ret = DestLimitBehavior.REMOVE_OLDEST;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_REJECT_NEWEST))  {
	    ret = DestLimitBehavior.REJECT_NEWEST;
	} else if (limitBehavStr.equals(LIMIT_BEHAV_RM_LOW_PRIORITY))  {
	    ret = DestLimitBehavior.REMOVE_LOW_PRIORITY;
	}
	
	return (ret);
    }

}
