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
 * @(#)ObjStoreConFactoryDialog.java	1.24 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.naming.Context;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.util.IntegerField;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;
import com.sun.messaging.jmq.admin.apps.console.util.LongField;

/** 
 * This dialog is used for object store attributes.
 * It can be used to Add an object store to the list
 * or to modify (update) an existing object store.
 *
 */
public class ObjStoreConFactoryDialog extends AdminDialog {
    
    protected JTabbedPane	 tabbedPane;
    protected JTextField	 lookupText;
    protected JLabel    	 lookupLabel;
    protected JLabel    	 cfLabel;
    protected JCheckBox 	 checkBox;
    //protected JRadioButton       queuecfButton, topiccfButton;
    protected JComboBox	         factoryCombo;
    protected Vector 		 cfProps;
    protected LabelledComponent  extraItems[];

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    private LabelledComponent lookupItem;
    private boolean addExtra;

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
    public ObjStoreConFactoryDialog(Frame parent, String title)  {
	super(parent, title, (OK | CANCEL | HELP));
    }

    public ObjStoreConFactoryDialog(Frame parent, String title, int whichButtons)  {
	super(parent, title, whichButtons, false);
    }

    public JPanel createWorkPanel()  {

	boolean propsDlg = false;
 	if (getTitle().equals(acr.getString(acr.I_OBJSTORE_CF_PROPS))) {
	    propsDlg = true;
	}

	JPanel workPanel = new JPanel();
	JPanel topPanel = makeTopPanel(propsDlg);

	AdministeredObject aobj = new com.sun.messaging.QueueConnectionFactory();

	// Get the groups for this admin obj.
	String groupString = aobj.getPropertyGroups();
	String groups[] = stringToArray(groupString, "|");
	if (groups == null)
	    return workPanel;

        GridBagLayout gridbag = new GridBagLayout();
        workPanel.setLayout(gridbag);
        GridBagConstraints c = new GridBagConstraints();
	
	c.gridx = 0;
	c.gridy = 0;
	c.weightx = 1;
	c.fill = GridBagConstraints.HORIZONTAL;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(topPanel, c);
	workPanel.add(topPanel);

	tabbedPane = new JTabbedPane();
	cfProps = new Vector();

	// Get properties for each group.
	// Then create a separate panel of properties for each group.
	for (int i = 0; i < groups.length; i++) {
	
	    String groupName = aobj.getLabelForGroup(groups[i]);
	    String groupPropsString = aobj.getPropertiesForGroup(groups[i]);
	    String props[] = stringToArray(groupPropsString, "|");

	    JPanel groupPanel = layoutGroupProperties(groups[i], groupName, 
							props, aobj);
	
	    // Add to tabbed pane.
	    if (groupPanel != null) {
		tabbedPane.addTab(groupName, groupPanel);
	    }
	}

	// Add the Tabbed Pane.
	c.gridx = 0;
	c.gridy = 1;
	c.anchor = GridBagConstraints.NORTHWEST;
	//c.fill = GridBagConstraints.HORIZONTAL;
	c.insets = new Insets(0, 0, 0, 0);
	gridbag.setConstraints(tabbedPane, c);
	workPanel.add(tabbedPane);

	// Set max width of lookupname label to width of tabbed pane 
	// in case it's very long.
	if (propsDlg) {
	    int maxWidth = tabbedPane.getPreferredSize().width;
	    Dimension dim = new Dimension(maxWidth - 
				  lookupItem.getLabelWidth() - 40,
				  lookupLabel.getPreferredSize().height);
	    lookupItem.getComponent().setPreferredSize(dim);
	}

	return (workPanel);
    }

    /*
     * Top Panel
     */
    private JPanel makeTopPanel(boolean propsDlg) {

	JPanel topPanel = new JPanel();
	topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        GridBagLayout gridbag = new GridBagLayout();
        topPanel.setLayout(gridbag);
        GridBagConstraints c = new GridBagConstraints();


        JPanel panel1 = new JPanel(new GridLayout(0, 1, -1, -1));
        //JLabel lookUpDescription1 = new JLabel(acr.getString(acr.I_OBJSTORE_JNDI_INFO1));
        //JLabel lookUpDescription2 = new JLabel(acr.getString(acr.I_OBJSTORE_JNDI_INFO2));
        //JLabel lookUpDescription3 = new JLabel(acr.getString(acr.I_OBJSTORE_JNDI_INFO3));
        //panel1.add(lookUpDescription1);
        //panel1.add(lookUpDescription2);
        //panel1.add(lookUpDescription3);


/*
	JPanel panel2 = null;
	if (!propsDlg) {		// Add Conn Factory
	    panel2 = new JPanel();
	    GridBagLayout gridbag2 = new GridBagLayout();
	    panel2.setLayout(gridbag2);

	    GridBagConstraints c2 = new GridBagConstraints();
	    c2.gridx = 0;
	    c2.gridy = 0;
	    c2.ipadx = 0;
	    c2.ipady = -8;
	    c2.anchor = GridBagConstraints.WEST;
	    queuecfButton = new JRadioButton(acr.getString(acr.I_QCF), true);
	    gridbag2.setConstraints(queuecfButton, c2);
	    panel2.add(queuecfButton);

	    c2.gridx = 0;
	    c2.gridy = 1;
	    c2.ipadx = 0;
	    c2.ipady = 8;
	    c2.anchor = GridBagConstraints.WEST;
	    topiccfButton = new JRadioButton(acr.getString(acr.I_TCF));
	    gridbag2.setConstraints(topiccfButton, c2);
	    panel2.add(topiccfButton);
 
	    ButtonGroup group = new ButtonGroup();
	    group.add(queuecfButton);
	    group.add(topiccfButton);
	}
*/

	LabelledComponent items[] = new LabelledComponent[3];
        checkBox = new JCheckBox();
	if (propsDlg) {		// Props Conn Factory
	    lookupLabel = new JLabel(" ");
	    cfLabel = new JLabel(" ");
	    items[0] = new LabelledComponent(acr.getString(acr.I_OBJSTORE_LOOKUP_NAME) +
					     ":", lookupLabel);
	    lookupItem = items[0];
	    items[1] = new LabelledComponent(acr.getString(acr.I_OBJSTORE_FACTORY_TYPE) +
					     ":", cfLabel);
	} else {		// Add Conn Factory
	    lookupText = new JTextField(25);
	
	    String[] factories = {acr.getString(acr.I_CF),
	    			  acr.getString(acr.I_QCF),
	    			  acr.getString(acr.I_TCF),
	    			  acr.getString(acr.I_XACF),
	    			  acr.getString(acr.I_XAQCF),
	    			  acr.getString(acr.I_XATCF)};
	    factoryCombo = new JComboBox(factories);

	    items[0] = new LabelledComponent(acr.getString(acr.I_OBJSTORE_LOOKUP_NAME) +
					     ":", lookupText);
	    items[1] = new LabelledComponent(acr.getString(acr.I_OBJSTORE_FACTORY_TYPE) +
					     ":", factoryCombo, LabelledComponent.NORTH);
	}
        items[2] = new LabelledComponent(acr.getString(acr.I_READONLY) + ":",
                                         checkBox);

	LabelValuePanel lvp = new LabelValuePanel(items, 5, 5);

	c.gridx = 0;
	c.gridy = 0;
	c.anchor = GridBagConstraints.NORTHWEST;
	gridbag.setConstraints(panel1, c);
	topPanel.add(panel1);

	c.gridx = 0;
	c.gridy = 1;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(lvp, c);
	topPanel.add(lvp);

	return topPanel;
    }

    /*
     * Panel for second tab.
    private JPanel makeConnHandlerPanel() {
	JPanel chPanel = new JPanel();
	return chPanel;
    }
    */

    public void doOK()  { }
    public void doApply()  { }
    public void doReset() { }
    public void doCancel() { }
    public void doClose() { }
    public void doClear() { }

    private String [] stringToArray(String from, String separator) {

       if (from == null) {
            return null;
        }
        if (separator == null) {
            separator = " ";
        }
        StringTokenizer toks = new StringTokenizer(from, separator);
        String[] result = new String[toks.countTokens()];
        int i = 0;
        while (toks.hasMoreTokens()) {
            result[i++] = toks.nextToken().trim();
        }
        return result;
    }

   private JPanel layoutGroupProperties(String groupNumber, String groupName, 
					String [] props, AdministeredObject aobj) {
//System.out.println("layoutGroupProps: group # " + groupNumber + " groupName " + groupName + " props.length:" + props.length );
	LabelledComponent items[];

/* XXX
	if (groupNumber.equals("1") || groupNumber.equals("7")) {
	    // XXX I know this is the one with the LIST to add 6 extra props.
	    // XXX Hard coding only 6 extra props (remove BrokerServiceName).
	    items = new LabelledComponent[props.length + 6];   
	} else {
*/
	    items = new LabelledComponent[props.length];
/*
	}
*/

	int k = 0;
	// XXX addExtra means add the 6 extra props.
	addExtra = false; // XXX
	for (int i = 0; i < props.length; i++) {
	    items[k++] = makeLabelledComponent(aobj, props[i]);

	    // XXX temporarily appending the extra list props.
	    if (addExtra) {
		for (int j = 0; j < extraItems.length; j++) {
		    items[k++] = extraItems[j];
		}
	        addExtra = false;
	    }

	}

	LabelValuePanel lvp = new LabelValuePanel(items, 5, 5);
	//
	// Append items to cfProps;
	//
	for (int i = 0; i < items.length; i++) {
	    cfProps.add(items[i]);
	}

	return (lvp);
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
        Object source = e.getSource();

        if (source instanceof JComboBox) {
            doComboBox((JComboBox)source);
        } else
            super.actionPerformed(e);
    }

    /*
     * END INTERFACE ActionListener
     */


    public void doComboBox(JComboBox comboBox) {
	//String name = (String)comboBox.getSelectedItem();
	
/*
	if (name.equals(acr.getString(acr.I_OTHER_ITEM))) {
	    System.out.println("bring up dialog!");
	// XXX fix later
	} else if (name.equals("TCP")) {
	    for (int i = 0; i < extraItems.length; i++) {
		JComponent l = extraItems[i].getLabel();
		JComponent c = extraItems[i].getComponent();
		String propName = (String)extraItems[i].getClientData();
		if (propName.equals("imqBrokerHostName")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerHostPort")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerServiceName")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerServicePort")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqSSLProviderClassname")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqSSLIsHostTrusted")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqConnectionURL")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		}
	    }
	} else if (name.equals("TLS")) {
	    for (int i = 0; i < extraItems.length; i++) {
		JComponent l = extraItems[i].getLabel();
		JComponent c = extraItems[i].getComponent();
		String propName = (String)extraItems[i].getClientData();
		if (propName.equals("imqBrokerHostName")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerHostPort")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerServiceName")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqBrokerServicePort")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqSSLProviderClassname")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqSSLIsHostTrusted")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		} else if (propName.equals("imqConnectionURL")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		}
	    }
	} else if (name.equals("HTTP")) {
	    for (int i = 0; i < extraItems.length; i++) {
		JComponent l = extraItems[i].getLabel();
		JComponent c = extraItems[i].getComponent();
		String propName = (String)extraItems[i].getClientData();
		if (propName.equals("imqBrokerHostName")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqBrokerHostPort")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqBrokerServiceName")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqBrokerServicePort")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqSSLProviderClassname")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqSSLIsHostTrusted")) {
		    l.setEnabled(false);
		    c.setEnabled(false);
		} else if (propName.equals("imqConnectionURL")) {
		    l.setEnabled(true);
		    c.setEnabled(true);
		}
	    }
	}
*/
    }


    protected void setValue(JComponent c, String propType, String value) {

	if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LIST) &&
	    c instanceof JComboBox) {

	    ((JComboBox)c).setSelectedItem(value);

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_STRING) &&
	     	   c instanceof JTextField) {

	    ((JTextField)c).setText(value);

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_INTEGER) &&
	     	   c instanceof JTextField) {

	    ((JTextField)c).setText(value);

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LONG) &&
	    	   c instanceof JTextField) {

	    ((JTextField)c).setText(value);

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_BOOLEAN) &&
	    	   c instanceof JCheckBox) {

	    if (value.equalsIgnoreCase("true"))
	        ((JCheckBox)c).setSelected(true);
	    else
	        ((JCheckBox)c).setSelected(false);

	} else {
	    System.err.println("No setting for " + propType + " " + value);
	}
    }

    protected String getValue(JComponent c, String propType) {

	String value = "";
	boolean b;

	if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LIST) &&
	    c instanceof JComboBox) {

	    value = (String)((JComboBox)c).getSelectedItem();

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_STRING) &&
	     	   c instanceof JTextField) {

	    value = ((JTextField)c).getText();

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_INTEGER) &&
	     	   c instanceof JTextField) {

	    value = ((JTextField)c).getText();

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LONG) &&
	    	   c instanceof JTextField) {

	    value = ((JTextField)c).getText();

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_BOOLEAN) &&
	    	   c instanceof JCheckBox) {

	    if (((JCheckBox)c).isSelected())
	        value = "true";
	    else
	        value = "false";

	} else {
	    System.err.println("No value for " + propType + " " + c);
	}

	return value;
    }

    /*
     * Substitute "..." for "Other..."
     */
    private void changeOtherValues(String[] values) {
	
	for (int i = 0; i < values.length; i++) {
	    if (values[i].equals("...")) {
	        values[i] = acr.getString(acr.I_OTHER_ITEM);
	    }
	}

    }

    /*
     * Temporarily remove the "..." from the combo box
     * since it's not yet implemented.
     */
    private String[] omitOtherValues(String[] values) {
	
        int newLength = values.length;
	for (int i = 0; i < values.length; i++) {
	    if (values[i].equals("...")) {
	        newLength--;
	    }
	}

	String[] newValues = new String[newLength];
	int j = 0;
	for (int i = 0; i < values.length; i++) {
	    if (!values[i].equals("...")) {
		newValues[j++] = values[i];
	    }
 	}

	return newValues;
    }

    private LabelledComponent makeLabelledComponent
		(AdministeredObject aobj, String propName) {

	LabelledComponent lc = null;
	String propType = null;
	String propLabel = null;
	String propDefault = null;

	try {
	    // XXX workaround 
/*
	    if (propName.equals("imqSSLProviderClassname")) {
		aobj.setProperty("imqConnectionType", "TLS");
	    } else if (propName.equals("imqSSLIsHostTrusted")) {
		aobj.setProperty("imqConnectionType", "TLS");
	    } else if (propName.equals("imqConnectionURL")) {
		aobj.setProperty("imqConnectionType", "HTTP");
	    } 
*/

	    propType = aobj.getPropertyType(propName);
	    propLabel = aobj.getPropertyLabel(propName);
	    propDefault = aobj.getProperty(propName);
//System.out.println("   " + propName + " label: " + propLabel + " type is " + propType + " propDefault: " + propDefault);
	} catch (Exception e) {
	    System.out.println("Exception for property: " + propName + e.toString());
	}

	if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LIST)) {

		String listValues = aobj.getPropertyListValues(propName);	
//System.out.println("listValues: " + listValues);
		String comboValues[] = stringToArray(listValues, "|");
	
		// Remove any "..." from menu, not yet implemented
	        comboValues = omitOtherValues(comboValues);

		// subst any "..." values for "Other..."
		changeOtherValues(comboValues);
		
		if (comboValues != null) {
		    lc = new LabelledComponent(propLabel + ":", 
			     		       new JComboBox(comboValues));
		    JComboBox comp = (JComboBox)lc.getComponent();
	 	    comp.addActionListener(this);

		    lc.setClientData(propName);
		}

		extraItems = new LabelledComponent[0];
/*
		// XXX Hard code 6 more props.
		// XXX addExtra means add 6 extra broker props,
	 	//     but omit imqBrokerServiceName.
		addExtra = true;
		String [] listProps = {"imqBrokerHostName",
					 "imqBrokerHostPort",
					 "imqSSLProviderClassname",
					 "imqSSLIsHostTrusted",
					 "imqBrokerServicePort",
					 "imqConnectionURL"};
		extraItems = new LabelledComponent[listProps.length];

		for (int k = 0; k < listProps.length; k++) {
		    extraItems[k] = makeLabelledComponent(aobj, listProps[k]);
	 	}
*/

	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_INTEGER)) {

		if (propDefault == null)
		    lc = new LabelledComponent(propLabel + ":", 
				new IntegerField(Integer.MIN_VALUE, 	
						 Integer.MAX_VALUE, 
						 propDefault, 7));
		else
		    lc = new LabelledComponent(propLabel + ":", 
				new IntegerField(Integer.MIN_VALUE, 	
						 Integer.MAX_VALUE, 7));

		lc.setClientData(propName);
	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_LONG)) {
		if (propDefault == null)
		    lc = new LabelledComponent(propLabel + ":", 
				new LongField(Long.MIN_VALUE, Long.MAX_VALUE,
					      propDefault, 7));
		else
		    lc = new LabelledComponent(propLabel + ":", 
				new LongField(Long.MIN_VALUE, Long.MAX_VALUE, 7));

		lc.setClientData(propName);
	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_BOOLEAN)) {
		
		lc = new LabelledComponent(propLabel + ":", new JCheckBox());
		lc.setClientData(propName);
		
	} else if (propType.equals(AdministeredObject.AO_PROPERTY_TYPE_STRING)) {
		lc = new LabelledComponent(propLabel + ":", 
				new JTextField(15));
		lc.setClientData(propName);
	} else {
		//System.out.println("defaulting to text field for " + propType);
		lc = new LabelledComponent(propLabel + ":", 
				new JTextField(15));
		lc.setClientData(propName);
	}
	   
	return lc;
    }

    /* 
     * XXX Fix later
     * Stuff the default values in the components
     * if they are disabled in case they switch connection
     * types later.
     */ 
    protected void setOtherValues(AdministeredObject tempObj, 
				  boolean setDisabledItemsOnly) {

	String propName;
	String propType = null;
	String propDefault = null;
	String connType = null;
/*

	for (int i = 0; i < extraItems.length; i++) {
	    propName = (String)extraItems[i].getClientData();
	    if (propName.equals("imqBrokerHostName") ||
		propName.equals("imqBrokerHostPort") ||
		propName.equals("imqBrokerServiceName") ||
		propName.equals("imqBrokerServicePort") ||
		propName.equals("imqSSLProviderClassname") ||
		propName.equals("imqSSLIsHostTrusted")) {

		connType = "TLS";
	    } else if (propName.equals("imqConnectionURL")) {
		connType = "HTTP";
	    }

	    if (connType == null)
	        continue;

	    try {
	    	tempObj.setProperty("imqConnectionType", connType);
	        propType = tempObj.getPropertyType(propName);
	        propDefault = tempObj.getProperty(propName);
	    } catch (Exception ex) {
	    	System.err.println("Exception in adminobj.setProperty()");
 	    }

	    JComponent comp = extraItems[i].getComponent();

	    if (setDisabledItemsOnly) {
		if (!comp.isEnabled()) {
	    	    setValue(extraItems[i].getComponent(), propType, propDefault);
		}
	    } else {
	    	setValue(extraItems[i].getComponent(), propType, propDefault);
	    }
	}
*/

    }
}
