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
 * @(#)BrokerQueryDialog.java	1.27 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Properties;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.JTabbedPane;
import javax.swing.JRadioButton;

import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.bkrutil.BrokerConstants;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.apps.console.util.SpecialValueField;
import com.sun.messaging.jmq.admin.apps.console.util.IntegerField;
import com.sun.messaging.jmq.admin.apps.console.util.TimeField;
import com.sun.messaging.jmq.admin.apps.console.util.BytesField;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;

/** 
 * This dialog is used to display / update a broker's properties.
 *
 * NOTE: For Beta, the broker properties will not be updatable.
 * Because of this:
 *	- all the fields in this dialog will not be editable.
 *	- there will only be a CLOSE and HELP button.
 */
public class BrokerQueryDialog extends AdminDialog 
			       implements BrokerConstants
			        {

    private final static String	UNLIMITED_VALUE_0 = "0";
    // Unlimited value for Active/Failover Consumers
    private final static String	UNLIMITED_VALUE_NEG1 = "-1"; 
    private final static String	OFF_VALUE = "0";

    private static AdminResources ar = Globals.getAdminResources();
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    private Properties		savedBkrProps;

    private JTabbedPane		tabbedPane;

    private JLabel		versionLbl;
    private JLabel		instanceNameLbl;
    private IntegerField	portTF;

    private JCheckBox		autoCreateTopicCkb;
    private JCheckBox		autoCreateQueueCkb;

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


    private JComboBox		logLevelCb;
    private BytesField		logRolloverSizeBF;
    private SpecialValueField	logRolloverSizeSF;
    private TimeField		logRolloverIntervalTF;
    private SpecialValueField	logRolloverIntervalSF;

    /*
    private TimeField		metricIntervalTF;
    private SpecialValueField	metricIntervalSF;
    */

    private IntegerField	maxNumMsgsInMemDskTF;
    private SpecialValueField	maxNumMsgsInMemDskSF;

    private BytesField		maxTtlSizeMsgsInMemDskBF;
    private SpecialValueField	maxTtlSizeMsgsInMemDskSF;

    private BytesField		maxMsgSizeBF;
    private SpecialValueField	maxMsgSizeSF;

    public BrokerQueryDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_QUERY_BROKER), (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.QUERY_BROKER);
    }

    public void doCancel() { hide();}

    public void doOK() {
	Properties bkrProps = new Properties();
	String	tmpStr;

	/*
	 * Check if values have actually changed before setting them in
	 * bkrProps.
	 *
	 * This is to workround the problem where updating the broker
	 * will fail if you're updating the port number to the
	 * same port number it is already running on.
	 *
	 * I figure since I'm checking the port number, might as well
	 * check everything else.
	 */

	/*
	 * Primary Port
	 */
	tmpStr = portTF.getText();
        setIfNotModified(bkrProps, PROP_NAME_BKR_PRIMARY_PORT, tmpStr);

	/*
	 * Auto Create Topics
	 */
	if (autoCreateTopicCkb.isSelected())  {
	    tmpStr = "true";
	} else  {
	    tmpStr = "false";
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_AUTOCREATE_TOPIC, tmpStr);

	/*
	 * Auto Create Queues
	 */
	if (autoCreateQueueCkb.isSelected())  {
	    tmpStr = "true";
	} else  {
	    tmpStr = "false";
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_AUTOCREATE_QUEUE, tmpStr);

	/* 
	 * Auto Created Active Consumer Count
	 */
	if (activeConsumerSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = activeConsumerIF.getText();
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS, tmpStr);

	/* 
	 * Auto Created Failover Consumer Count
	 */
	if (failoverConsumerSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = failoverConsumerIF.getText();
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS, tmpStr);

	/*
	 * Log Level
	 */
	tmpStr = (String)logLevelCb.getSelectedItem();
        setIfNotModified(bkrProps, PROP_NAME_BKR_LOG_LEVEL, tmpStr);

	/*
	 * Log Rollover Size
	 */
	if (logRolloverSizeSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = Long.toString(logRolloverSizeBF.getValue());
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_LOG_ROLL_SIZE, tmpStr);

	/*
	 * Log Rollover Interval
	 */
	if (logRolloverIntervalSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = Long.toString(logRolloverIntervalTF.getValue() / 1000);
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_LOG_ROLL_INTERVAL, tmpStr);

/*
	 * Metric Interval
	if (metricIntervalSF.isSpecialValueSet())  {
	    tmpStr = OFF_VALUE;
	} else  {
	    tmpStr = Long.toString(metricIntervalTF.getValue() / 1000);
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_METRIC_INTERVAL, tmpStr);
 */

	/*
	 * Max Number of Messages in Memory and Disk
	 */
	if (maxNumMsgsInMemDskSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = maxNumMsgsInMemDskTF.getText();
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_MAX_MSG, tmpStr);

	/*
	 * Max Total Size of Messages in Memory and Disk
	 */
	if (maxTtlSizeMsgsInMemDskSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = maxTtlSizeMsgsInMemDskBF.getSizeString();
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_MAX_TTL_MSG_BYTES, tmpStr);

	/*
	 * Max Message Size
	 */
	if (maxMsgSizeSF.isSpecialValueSet())  {
	    tmpStr = UNLIMITED_VALUE_NEG1;
	} else  {
	    tmpStr = maxMsgSizeBF.getSizeString();
	}
        setIfNotModified(bkrProps, PROP_NAME_BKR_MAX_MSG_BYTES, tmpStr);

	BrokerAdminEvent	bae;
	bae = new BrokerAdminEvent(this, BrokerAdminEvent.UPDATE_BROKER);
	bae.setBrokerProps(bkrProps);
	bae.setOKAction(true);

	fireAdminEventDispatched(bae);
    }

    private void setIfNotModified(Properties newProps, String propName, String value)  {
	String oldValue;

	oldValue = savedBkrProps.getProperty(propName, "");

	/*
	System.err.println("old value: >"
			+ oldValue
			+ "<"
			+ ", new value: >" + value + "<");
	*/

	if (!oldValue.equals(value))  {
	    newProps.setProperty(propName, value);
	}
    }

    // not used
    public void doClose() {}
    public void doApply() {}
    public void doReset() {}
    public void doClear() {}

    public JPanel createWorkPanel()  {
        JPanel 			workPanel, tab;

        workPanel = new JPanel();

	tabbedPane = new JTabbedPane();

	tab = makeBasicTab();
	tabbedPane.addTab(acr.getString(acr.I_BROKER_TAB_BASIC), tab);

	tab = makeLogTab();
	tabbedPane.addTab(acr.getString(acr.I_BROKER_TAB_LOGS), tab);

	tab = makeMsgTab();
	tabbedPane.addTab(acr.getString(acr.I_BROKER_TAB_MSG_CAPACITY), tab);

	workPanel.add(tabbedPane);

	return (workPanel);
    }

    public JPanel makeBasicTab()  {
        JPanel 			workPanel;
	GridBagLayout 		workGridbag;
	GridBagConstraints 	workConstraints;
	LabelledComponent	tmpLabelC;
	LabelledComponent	lvpItems[];
	LabelValuePanel		lvp;

        workPanel = new JPanel();
	workGridbag = new GridBagLayout();
	workPanel.setLayout(workGridbag);
	workConstraints = new GridBagConstraints();

	workConstraints.gridx = 0;
	workConstraints.anchor = GridBagConstraints.WEST;
	workConstraints.fill = GridBagConstraints.NONE;
	workConstraints.insets = new Insets(5, 0, 5, 0);
	workConstraints.ipadx = 0;
	workConstraints.ipady = 0;
	workConstraints.weightx = 1.0;

	/*
	 * Removed metric interval
	lvpItems = new LabelledComponent[6];
	*/
	lvpItems = new LabelledComponent[7];

	versionLbl = new JLabel();
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_VERSION_STR), 
			versionLbl);
	lvpItems[0] = tmpLabelC;

	instanceNameLbl = new JLabel();
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_INSTANCE_NAME), 
			instanceNameLbl);
	lvpItems[1] = tmpLabelC;

	portTF = new IntegerField(0, Integer.MAX_VALUE, 10);
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_PORT), portTF);
	lvpItems[2] = tmpLabelC;

	autoCreateTopicCkb = new JCheckBox();
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_ACREATE_TOPICS), 
				autoCreateTopicCkb);
	lvpItems[3] = tmpLabelC;

	autoCreateQueueCkb = new JCheckBox();
        autoCreateQueueCkb.addActionListener(this);
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_BROKER_ACREATE_QUEUES), 
				autoCreateQueueCkb);
	lvpItems[4] = tmpLabelC;

	/*
	 * Active Consumers
	 */
        activeConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 10);
        activeConsumerSF = new SpecialValueField(activeConsumerIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	activeConsumerLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_AUTOCREATED_ACTIVE_CONSUMER),
			 activeConsumerSF, LabelledComponent.NORTH);
	lvpItems[5] = activeConsumerLabelC;

	/*
	 * Failover Consumers
	 */
        failoverConsumerIF = new IntegerField(0, Integer.MAX_VALUE, 10);
        failoverConsumerSF = new SpecialValueField(failoverConsumerIF,
				acr.getString(acr.I_BROKER_UNLIMITED));
	failoverConsumerLabelC = new LabelledComponent
			(acr.getString(acr.I_BROKER_AUTOCREATED_FAILOVER_CONSUMER),
			 failoverConsumerSF, LabelledComponent.NORTH);
	lvpItems[6] = failoverConsumerLabelC;


	/*
	metricIntervalTF = new TimeField(Integer.MAX_VALUE, "0", 10);
	metricIntervalSF = new SpecialValueField(metricIntervalTF,
			acr.getString(acr.I_BROKER_OFF));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_METRIC_INTERVAL), 
		metricIntervalSF, LabelledComponent.NORTH);
	lvpItems[4] = tmpLabelC;
	*/

	lvp = new LabelValuePanel(lvpItems, 4, 5);
	workGridbag.setConstraints(lvp, workConstraints);
	workPanel.add(lvp);

	return (workPanel);
    }

    public JPanel makeLogTab()  {
        JPanel 			workPanel;
	GridBagLayout 		workGridbag;
	GridBagConstraints 	workConstraints;
	LabelledComponent	tmpLabelC;
	LabelledComponent	lvpItems[];
	LabelValuePanel		lvp;

        workPanel = new JPanel();
	workGridbag = new GridBagLayout();
	workPanel.setLayout(workGridbag);
	workConstraints = new GridBagConstraints();

	workConstraints.gridx = 0;
	workConstraints.anchor = GridBagConstraints.WEST;
	workConstraints.fill = GridBagConstraints.NONE;
	workConstraints.insets = new Insets(5, 0, 5, 0);
	workConstraints.ipadx = 0;
	workConstraints.ipady = 0;
	workConstraints.weightx = 1.0;

	lvpItems = new LabelledComponent[3];

	logLevelCb = new JComboBox(BKR_LOG_LEVEL_VALID_VALUES.toArray(
                                   new String[BKR_LOG_LEVEL_VALID_VALUES.size()]));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_LOG_LEVEL), 
		logLevelCb);
	lvpItems[0] = tmpLabelC;

        logRolloverSizeBF = new BytesField(0, Integer.MAX_VALUE, "0", 10);
	logRolloverSizeSF = new SpecialValueField(logRolloverSizeBF, 
			acr.getString(acr.I_BROKER_UNLIMITED));
        tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_LOG_ROLLOVER_SIZE),
                logRolloverSizeSF, LabelledComponent.NORTH);
	lvpItems[1] = tmpLabelC;

	logRolloverIntervalTF = new TimeField(Integer.MAX_VALUE, "0", 10);
	logRolloverIntervalSF = new SpecialValueField(logRolloverIntervalTF,
			acr.getString(acr.I_BROKER_UNLIMITED));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_LOG_ROLLOVER_INTERVAL),
		logRolloverIntervalSF, LabelledComponent.NORTH);
	lvpItems[2] = tmpLabelC;

	lvp = new LabelValuePanel(lvpItems, 4, 5);
	workGridbag.setConstraints(lvp, workConstraints);
	workPanel.add(lvp);

	return (workPanel);
    }

    public JPanel makeMsgTab()  {
        JPanel 			workPanel;
	GridBagLayout 		workGridbag;
	GridBagConstraints 	workConstraints;
	LabelledComponent	tmpLabelC;
	LabelledComponent	lvpItems[];
	LabelValuePanel		lvp;

        workPanel = new JPanel();
	workGridbag = new GridBagLayout();
	workPanel.setLayout(workGridbag);
	workConstraints = new GridBagConstraints();

	workConstraints.gridx = 0;
	workConstraints.anchor = GridBagConstraints.WEST;
	workConstraints.fill = GridBagConstraints.NONE;
	workConstraints.insets = new Insets(5, 0, 5, 0);
	workConstraints.ipadx = 0;
	workConstraints.ipady = 0;
	workConstraints.weightx = 1.0;

	lvpItems = new LabelledComponent[3];

	maxNumMsgsInMemDskTF = new IntegerField(0, Integer.MAX_VALUE, 10);
	maxNumMsgsInMemDskSF = new SpecialValueField(maxNumMsgsInMemDskTF,
			acr.getString(acr.I_BROKER_UNLIMITED));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_MAX_MSGS_IN_MEM_DSK), 
		maxNumMsgsInMemDskSF, LabelledComponent.NORTH);
	lvpItems[0] = tmpLabelC;

	maxTtlSizeMsgsInMemDskBF = new BytesField(0, Integer.MAX_VALUE, "0", 10);
	maxTtlSizeMsgsInMemDskSF = new SpecialValueField(maxTtlSizeMsgsInMemDskBF,
			acr.getString(acr.I_BROKER_UNLIMITED));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_MAX_TTL_SIZE_MSGS_IN_MEM_DSK),
		maxTtlSizeMsgsInMemDskSF, LabelledComponent.NORTH);
	lvpItems[1] = tmpLabelC;

	maxMsgSizeBF = new BytesField(0, Integer.MAX_VALUE, "0", 10);
	maxMsgSizeSF = new SpecialValueField(maxMsgSizeBF,
			acr.getString(acr.I_BROKER_UNLIMITED));
	tmpLabelC = new LabelledComponent(
		acr.getString(acr.I_BROKER_MAX_MSG_SIZE), 
		maxMsgSizeSF, LabelledComponent.NORTH);
	lvpItems[2] = tmpLabelC;

	lvp = new LabelValuePanel(lvpItems, 4, 5);
	workGridbag.setConstraints(lvp, workConstraints);
	workPanel.add(lvp);

	return (workPanel);
    }


    /*

    private void makeReadOnly()  {
        portTF.setEditable(false);
	
        //logRolloverSizeBF.setEditable(false);
        //logRolloverIntervalTF.setEditable(false);

        //metricIntervalTF.setEditable(false);

        //maxNumMsgsInMemDskTF.setEditable(false);
        //maxTtlSizeMsgsInMemDskBF.setEditable(false);
        //maxMsgSizeBF.setEditable(false);
	
    }

    */

    public void show(BrokerCObj bkrCObj) {
	reset();

	setBrokerProps(bkrCObj.getBrokerProps());

	tabbedPane.setSelectedIndex(0);

	pack();
	super.show();
    }

    private void setBrokerProps(Properties bkrProps)  {
	String		value;

	savedBkrProps = bkrProps;

	if (bkrProps == null)  {
	    return;
	}

	value = bkrProps.getProperty(PROP_NAME_BKR_PRODUCT_VERSION, "");
	if (value.equals(""))  {
	    value = acr.getString(acr.I_BROKER_VERSION_NOT_AVAIL);
	}
        versionLbl.setText(value);

	value = bkrProps.getProperty(PROP_NAME_BKR_INSTANCE_NAME, "");
        instanceNameLbl.setText(value);

	value = bkrProps.getProperty(PROP_NAME_BKR_PRIMARY_PORT, "");
        portTF.setText(value);

	value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_TOPIC, "");
	if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("t"))  {
            autoCreateTopicCkb.setSelected(true);
	} else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("f"))  {
            autoCreateTopicCkb.setSelected(false);
	}

        value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE, "");
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("t"))  {
            autoCreateQueueCkb.setSelected(true);
            showQueueDeliveryPolicy();
        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("f"))  {
            autoCreateQueueCkb.setSelected(false);
            hideQueueDeliveryPolicy();
        }

        value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_ACTIVE_CONS, "");
	activeConsumerIF.setText("0");  // Reset Integer field
	if (!value.equals(UNLIMITED_VALUE_NEG1))
	    activeConsumerIF.setText(value);
	checkUnlimitedNeg1(activeConsumerSF, value);

        value = bkrProps.getProperty(PROP_NAME_BKR_AUTOCREATE_QUEUE_MAX_BACKUP_CONS, "");
	failoverConsumerIF.setText("0");  // Reset Integer field
	if (!value.equals(UNLIMITED_VALUE_NEG1))
	    failoverConsumerIF.setText(value);
	checkUnlimitedNeg1(failoverConsumerSF, value);

	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_LEVEL, "");
        logLevelCb.setSelectedItem(value);

	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_ROLL_SIZE, "");
	if (!value.equals(UNLIMITED_VALUE_NEG1) && !value.equals(UNLIMITED_VALUE_0))
	    logRolloverSizeBF.setSizeString(value);
	checkBothUnlimited(logRolloverSizeSF, value);

	value = bkrProps.getProperty(PROP_NAME_BKR_LOG_ROLL_INTERVAL, "");
	if (!value.equals(UNLIMITED_VALUE_NEG1) && !value.equals(UNLIMITED_VALUE_0))
            logRolloverIntervalTF.setText(value);
	checkBothUnlimited(logRolloverIntervalSF, value);

	/*
	value = bkrProps.getProperty(PROP_NAME_BKR_METRIC_INTERVAL, "");
        metricIntervalTF.setText(value);
	checkOff(metricIntervalSF, value);
	*/

	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG, "");
	if (!value.equals(UNLIMITED_VALUE_NEG1) && !value.equals(UNLIMITED_VALUE_0)) {
            maxNumMsgsInMemDskTF.setText(value);
	}
	checkBothUnlimited(maxNumMsgsInMemDskSF, value);

	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_TTL_MSG_BYTES, "");
	if (!value.equals(UNLIMITED_VALUE_NEG1) && !value.equals(UNLIMITED_VALUE_0)) {
	    maxTtlSizeMsgsInMemDskBF.setSizeString(value);
	}
	checkBothUnlimited(maxTtlSizeMsgsInMemDskSF, value);

	value = bkrProps.getProperty(PROP_NAME_BKR_MAX_MSG_BYTES, "");
	if (!value.equals(UNLIMITED_VALUE_NEG1) && !value.equals(UNLIMITED_VALUE_0)) {
	    maxMsgSizeBF.setSizeString(value);
	}
	checkBothUnlimited(maxMsgSizeSF, value);
    }

    /**
    private void checkUnlimited0(SpecialValueField sf, String val)  {
	if (valueIsUnlimited0(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }
    */

    private boolean valueIsUnlimited0(String val)  {
	SizeString ss;

	try  {
	    ss = new SizeString(val);
	} catch (Exception e)  {
	    /*
	     * Should not get here
	     */
	    return (false);
	}

	if (ss.getBytes() == 0)  {
	    return (true);
	}

	return (false);
    }

    private void checkUnlimitedNeg1(SpecialValueField sf, String val)  {
	if (valueIsUnlimitedNeg1(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }

    private boolean valueIsUnlimitedNeg1(String val)  {

	if (val.equals(UNLIMITED_VALUE_NEG1)) 
	    return true;
	else
	    return false;
    }

    private void checkBothUnlimited(SpecialValueField sf, String val)  {
	if (valueIsUnlimited0(val) || valueIsUnlimitedNeg1(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }

    /*
    private void checkOff(SpecialValueField sf, String val)  {
	if (valueIsOff(val))  {
            sf.setSpecialValueSet(true);
	} else  {
            sf.setSpecialValueSet(false);
	}
    }
    */

    /*
    private boolean valueIsOff(String val)  {
	return (val.equals(OFF_VALUE));
    }
    */

    private void reset()  {
        instanceNameLbl.setText("");
        portTF.setText("");

        autoCreateTopicCkb.setSelected(true);
        autoCreateQueueCkb.setSelected(true);

        logLevelCb.setSelectedItem("INFO");
        logRolloverSizeBF.setText("0");
        logRolloverSizeBF.setUnit(BytesField.BYTES);
        logRolloverIntervalTF.setText("");
	logRolloverIntervalTF.setUnit(TimeField.SECONDS);

	/*
        metricIntervalTF.setText("0");
	metricIntervalTF.setUnit(TimeField.SECONDS);
	*/

        maxNumMsgsInMemDskTF.setText("");
        maxTtlSizeMsgsInMemDskBF.setText("0");
        maxTtlSizeMsgsInMemDskBF.setUnit(BytesField.BYTES);
        maxMsgSizeBF.setText("0");
        maxMsgSizeBF.setUnit(BytesField.BYTES);
    }

    private void showQueueDeliveryPolicy() {

	activeConsumerLabelC.setEnabled(true);
	activeConsumerIF.setEnabled(true);
	activeConsumerSF.setEnabled(true);

	failoverConsumerLabelC.setEnabled(true);
	failoverConsumerIF.setEnabled(true);
	failoverConsumerSF.setEnabled(true);
    }

    private void hideQueueDeliveryPolicy() {

	activeConsumerLabelC.setEnabled(false);
	activeConsumerIF.setEnabled(false);
	activeConsumerSF.setEnabled(false);

	failoverConsumerLabelC.setEnabled(false);
	failoverConsumerIF.setEnabled(false);
	failoverConsumerSF.setEnabled(false);
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
        Object source = e.getSource();

        if (source == autoCreateQueueCkb) {
	    if (autoCreateQueueCkb.isSelected()) {
                showQueueDeliveryPolicy();
	    } else {
                hideQueueDeliveryPolicy();
	    }	
        } else {
            super.actionPerformed(e);
        }
    }
    /*
     * END INTERFACE ActionListener
     */
}
