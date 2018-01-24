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
 * @(#)ObjStoreDialog.java	1.31 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.util.Enumeration;
import java.util.Properties;

import javax.naming.Context;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;

/** 
 * This dialog is used for object store attributes.
 * It can be used to Add an object store to the list
 * or to modify (update) an existing object store.
 *
 */
public class ObjStoreDialog extends AdminDialog 
		implements ListSelectionListener {
    
    protected JTextField	osText;
    protected LabelValuePanel   lvp;
    protected ObjStoreManager	osMgr = null;
    protected Properties 	jndiProps = new Properties();
    protected JComboBox		comboBox;
    protected JCheckBox		checkBox;
    protected PropsTableModel 	model;
    protected JButton		addButton, delButton, chgButton;
    protected boolean		dirty = false;
    protected JTextField	valueText;
    protected JTable 		table;
    protected JTextArea 	ta;

    private boolean	        editable;
    private JLabel		urlLabel;
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};

    /**
     * Creates a non-modal dialog using the specified frame as parent and string
     * as title. By default, will contain the following buttons:
     * <UL>
     * <LI>OK
     * <LI>CANCEL
     * <LI>CLOSE
     * <LI>HELP
     * </UL>
     *
     * @param parent the Frame from which the dialog is displayed
     * @param title the String to display in the dialog's title bar
     */
    public ObjStoreDialog(Frame parent, String title, ObjStoreListCObj oslCObj)  {
	super(parent, title, (OK | CANCEL | HELP));
	if (oslCObj != null)
	    osMgr = oslCObj.getObjStoreManager();
    }

    public ObjStoreDialog(Frame parent, String title, int whichButtons,
				ObjStoreListCObj oslCObj)  {
	super(parent, title, whichButtons);
	if (oslCObj != null)
	    osMgr = oslCObj.getObjStoreManager();
    }

    public JPanel createWorkPanel()  {
	String[] jndiPropNames = {Context.INITIAL_CONTEXT_FACTORY,
			      Context.OBJECT_FACTORIES,
			      Context.STATE_FACTORIES,
			      Context.URL_PKG_PREFIXES,
			      Context.PROVIDER_URL,
			      Context.DNS_URL,
			      Context.AUTHORITATIVE,
			      Context.BATCHSIZE,
			      Context.REFERRAL,
			      Context.SECURITY_PROTOCOL,
			      Context.SECURITY_AUTHENTICATION,
			      Context.SECURITY_PRINCIPAL,
			      Context.SECURITY_CREDENTIALS,
			      Context.LANGUAGE,
			      Context.APPLET};

        boolean    propsDlg = false;
        if (getTitle().equals(acr.getString(acr.I_OBJSTORE_PROPS))) {
            propsDlg = true;
        }

	JPanel workPanel = new JPanel();
	workPanel.setLayout(new BorderLayout());

	JPanel panel1 = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	panel1.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();

	JPanel osPanel = new JPanel();
	GridBagLayout gb = new GridBagLayout();
	osPanel.setLayout(gb);
	GridBagConstraints c1 = new GridBagConstraints();

	JLabel storeLabel = new JLabel(acr.getString(acr.I_OBJSTORE_NAME) + ":");
	c1.gridx = 0;
	c1.gridy = 0;
	c1.anchor = GridBagConstraints.NORTHEAST;
	gridbag.setConstraints(storeLabel, c1);
	osPanel.add(storeLabel);
	
	osText = new JTextField(25);
	DocumentListener docListener = new ObjStoreDocumentListener();
	osText.getDocument().addDocumentListener(docListener);
	c1.gridx = 1;
	c1.gridy = 0;
	c1.anchor = GridBagConstraints.WEST;
	c1.insets = new Insets(0, 5, 0, 0);
	gb.setConstraints(osText, c1);
	osPanel.add(osText);

	c.gridx = 0;
	c.gridy = 0;
	c1.anchor = GridBagConstraints.WEST;
	panel1.add(osPanel);

	if (propsDlg)
	    checkBox = new JCheckBox(acr.getString(acr.I_CONNECT_AFTER_UPDATES), true);
	else
	    checkBox = new JCheckBox(acr.getString(acr.I_CONNECT_UPON_ADDING), true);
	checkBox.addActionListener(this);
	c.gridx = 0;
	c.gridy = 1;
	c.gridwidth = 3;
	c.insets = new Insets(0, 0, 0, 0);  // reset
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(checkBox, c);
/*
	panel1.add(checkBox);
*/
	
	JSeparator separator = new JSeparator();
	c.gridx = 0;
	c.gridy = 2;
	c.anchor = GridBagConstraints.CENTER;
	c.fill = GridBagConstraints.HORIZONTAL;
	c.insets = new Insets(5, 0, 5, 0);
	gridbag.setConstraints(separator, c);
	panel1.add(separator);

	JLabel jndiLabel = new JLabel(acr.getString(acr.I_OBJSTORE_JNDI_PROPS));
	c.gridx = 0;
	c.gridy = 3;
	c.gridwidth = 3;
	c.anchor = GridBagConstraints.WEST; // reset
	c.insets = new Insets(0, 0, 0, 0);  // reset
	c.fill = GridBagConstraints.NONE; // reset c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(jndiLabel, c);
	panel1.add(jndiLabel);

	LabelledComponent items[] = new LabelledComponent[3];
	comboBox = new JComboBox(jndiPropNames);
	comboBox.addActionListener(this);
	items[0] = new LabelledComponent(acr.getString(acr.I_NAME) + ":", comboBox);
	valueText = new JTextField(40);
	valueText.getDocument().addDocumentListener(docListener);
	items[1] = new LabelledComponent(acr.getString(acr.I_VALUE) + ":", valueText);

	model = new PropsTableModel();
	table = new JTable(model);
	table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	ListSelectionModel lsm = table.getSelectionModel();
	lsm.addListSelectionListener(this);
	JScrollPane scrollPane = new JScrollPane(table);
	scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	
	items[2] = new LabelledComponent(" ", scrollPane);

	lvp = new LabelValuePanel(items, 5, 5);
	c.gridx = 0;
	c.gridy = 4;
	c.gridwidth = 2;
	c.anchor = GridBagConstraints.CENTER;
	gridbag.setConstraints(lvp, c);
	panel1.add(lvp);

	//
	// Make width of list the same as width of text field and 
	// 8 rows in height.
	//
	int width = valueText.getPreferredSize().width;
	//Dimension dim = scrollPane.getPreferredSize();
	//scrollPane.setPreferredSize(new Dimension(width, dim.height));
	table.getRowHeight();
	table.setPreferredScrollableViewportSize(new Dimension(width, 
						 8 * table.getRowHeight()));
	

	JPanel buttonPanel = new JPanel();
	addButton = new JButton(acr.getString(acr.I_DIALOG_ADD));
	addButton.addActionListener(this);
 	delButton = new JButton(acr.getString(acr.I_DIALOG_DELETE));
	delButton.addActionListener(this);
	delButton.setEnabled(false);
	chgButton = new JButton(acr.getString(acr.I_DIALOG_CHANGE));
	chgButton.addActionListener(this);
	chgButton.setEnabled(false);
	buttonPanel.setLayout(new GridLayout(3, 1, 5, 5));
	buttonPanel.add(addButton);
	buttonPanel.add(delButton);
	buttonPanel.add(chgButton);
	c.gridx = 2;
	c.gridy = 4;
	c.gridwidth = 1;
	c.anchor = GridBagConstraints.NORTHWEST;
	c.insets = new Insets(items[0].getPreferredSize().height +
			      items[1].getPreferredSize().height + 20, 0, 0, 0);
	gridbag.setConstraints(buttonPanel, c);
	panel1.add(buttonPanel);

	String s = acr.getString(acr.W_SAVE_AS_CLEAR_TEXT);
	ta = new JTextArea(s);
	ta.setLineWrap(true);
	ta.setWrapStyleWord(true);
	Color bgColor = panel1.getBackground();
	ta.setBackground(bgColor);
	Color fgColor = jndiLabel.getForeground();
	ta.setForeground(fgColor);
	ta.setFont(jndiLabel.getFont());

	// Find longer length to set length of text area:
	// Either 1) ObjectStoreLabel: ___     OR
	//	  2) Width of LabelValuePanel
	int width1 = storeLabel.getPreferredSize().width + 5 +
			osText.getPreferredSize().width;
	int width2 = lvp.getPreferredSize().width;
	if (width1 >= width2)
	    ta.setSize(width1, 1);
	else
	    ta.setSize(width2, 1);
	ta.setEditable(false);
	Dimension textSize = ta.getPreferredSize();
	ta.setSize(textSize);
	
	c.gridx = 0;
	c.gridy = 5;
	c.gridwidth = 3;
	c.anchor = GridBagConstraints.WEST;
	c.insets = new Insets(5, 0, 5, 0);
	gridbag.setConstraints(ta, c);
	panel1.add(ta);

	workPanel.add(panel1, BorderLayout.NORTH);
	
	return (workPanel);
    }

    public void doOK() { }
    public void doApply()  { }
    public void doReset() { }
    public void doCancel() { }
    public void doClose() { }
    public void doClear() {
	// Clear out all fields.
        jndiProps.clear();
	osText.setText("");
	checkBox.setSelected(true);
	comboBox.setSelectedIndex(0);
	valueText.setText("");

	delButton.setEnabled(false);
	chgButton.setEnabled(false);

	model.fireTableChanged(new TableModelEvent(model));

        osText.requestFocus();
	
	dirty = false;
	
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
	Object source = e.getSource();

	if (source == addButton)  {
	    doAdd();
	} else if (source == delButton) {
	    doDelete();
	} else if (source == chgButton) {
	    doChange();
	} else if (source == comboBox) {
	    doComboBox();
	    dirty = true;
	} else if (source == checkBox) {
	    dirty = true;
	} else
	    super.actionPerformed(e);
    }
    /*
     * END INTERFACE ActionListener
     */

    /*
     * BEGIN INTERFACE ListSelectionListener
     */
    public void valueChanged(ListSelectionEvent e)  {
	ListSelectionModel lsm = (ListSelectionModel)e.getSource();
	boolean	isAdjusting = e.getValueIsAdjusting();
	//int	firstIndex = e.getFirstIndex();
	//int	lastIndex = e.getLastIndex();

	if (isAdjusting == false) {
	    /*
	     * Query model for selected index and get the object at the
	     * index.
	     */
	    if (lsm.isSelectionEmpty()) {
		delButton.setEnabled(false);
		chgButton.setEnabled(false);
	    } else {
		//
		// Selected something in list so
		// change the combo box of properties and display its
		// value.
		//
	        int selectedRow = lsm.getMinSelectionIndex();

		String selectedItem = (String)model.getValueAt(selectedRow, 0);
		comboBox.setSelectedItem(selectedItem);
		valueText.setText(jndiProps.getProperty(selectedItem));

	 	/*
		 * Change state of buttons only if this is an
		 * editable dialog by checking a text field.
		 * If not editable, then just leave buttons as is.
		 */
	 	if (isEditable()) {
		    delButton.setEnabled(true);
		    chgButton.setEnabled(true);
		}

		/*
		 * Dispatch a selection event.
	        SelectionEvent se = new SelectionEvent(this, SelectionEvent.OBJ_SELECTED);
	        se.setSelectedObj((ConsoleObj)o);

	        fireAdminEventDispatched(se);
		 */
	    }
	}
    }
    /*
     * END INTERFACE ListSelectionListener
     */


    public void doAdd() {

	//
	// Make sure a value was entered.
	//
	String value = (valueText.getText()).trim();
	if (value == null || value.equals("")) {
	    JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE, (String)comboBox.getSelectedItem()),
		acr.getString(acr.I_OBJSTORE) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    		  AdminConsoleResources.E_NO_PROP_VALUE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    valueText.requestFocus();
	    return;
	}

	//
	// Check if the value has already been set.
	//
	String checkValue = jndiProps.getProperty((String)comboBox.getSelectedItem());
	if (checkValue != null) {
	    JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_PROP_VALUE_EXISTS, 
				(String)comboBox.getSelectedItem()),
		acr.getString(acr.I_OBJSTORE) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    		  AdminConsoleResources.E_PROP_VALUE_EXISTS),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}
	
	/*
	 * Verify the value (if possible) if we're 
	 * adding/changing the provider url.
	 */
	if (((String)comboBox.getSelectedItem()).equals(Context.PROVIDER_URL)) {
	    verifyProviderURL(value);
	}

	//
	// Add value to jndiProps
	//
	jndiProps.setProperty((String)comboBox.getSelectedItem(), value);

	//
	// Select the item in the list that was just added.
	//
	int index = 0;
	
	for (Enumeration e = jndiProps.propertyNames(); e.hasMoreElements();) {
		String propName = (String)e.nextElement();
		if (propName.equals((String)comboBox.getSelectedItem()))
		    break;
		index++;
	}
	model.fireTableRowsInserted(index, index);
	table.setRowSelectionInterval(index, index);

    }

    public void doDelete() {

	//
	// Send table event that a row was deleted.
	//
	int index = 0;
	
	for (Enumeration e = jndiProps.propertyNames(); e.hasMoreElements();) {
		String propName = (String)e.nextElement();
		if (propName.equals((String)comboBox.getSelectedItem()))
		    break;
		index++;
	}
	jndiProps.remove((String)comboBox.getSelectedItem());
	model.fireTableRowsDeleted(index, index);

	// Nothing is selected now so
	// disable these buttons and clear
	// out value field.
	addButton.setEnabled(true);
	delButton.setEnabled(false);
	chgButton.setEnabled(false);
	valueText.setText("");
    }

    public void doChange() {

	//
	// Make sure a value was entered.
	//
	String value = (valueText.getText()).trim();
	if (value == null || value.equals("")) {
	    JOptionPane.showOptionDialog(this,
		acr.getString(acr.E_NO_PROP_VALUE,
		    (String)comboBox.getSelectedItem()),
		acr.getString(acr.I_OBJSTORE) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    		  AdminConsoleResources.E_NO_PROP_VALUE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    valueText.requestFocus();
	    return;
	}

	/*
	 * Verify the value (if possible) if we're 
	 * adding/changing the provider url.
	 */
	if (((String)comboBox.getSelectedItem()).equals(Context.PROVIDER_URL)) {
	    verifyProviderURL(value);
	}

	//
	// Send table event that a row was updated.
	//
	int index = 0;
	
	for (Enumeration e = jndiProps.propertyNames(); e.hasMoreElements();) {
		String propName = (String)e.nextElement();
		if (propName.equals((String)comboBox.getSelectedItem()))
		    break;
		index++;
	}
	jndiProps.setProperty((String)comboBox.getSelectedItem(), value);
	model.fireTableRowsUpdated(index, index);

    }

/*
    public void doUrlButton() {
	osText.setEnabled(false);
	urlLabel.setEnabled(true);
    }

    public void doOsTextButton() {
	osText.setEnabled(true);
	urlLabel.setEnabled(false);
    }
*/

    public void doComboBox() {
	String name = (String)comboBox.getSelectedItem();
	
	if (jndiProps == null) 
	    valueText.setText("");

	if (name != null) {	
            String value = jndiProps.getProperty(name);
	    /* 
  	     * Stuff the value in the text area.
	     */
	    valueText.setText(jndiProps.getProperty(name));
	    if (value != null) {
		//
		// Select the item in the list that was selected in
		// the combox box, if it exists.
		//
		int index = 0;
		for (Enumeration e = jndiProps.propertyNames(); e.hasMoreElements();) {
		    String propName = (String)e.nextElement();
		    if (propName.equals(name)) 
		        break;
		    index++;
		}
		table.setRowSelectionInterval(index, index);

		// Set the appropriate buttons, if editable
		if (isEditable()) {
		    addButton.setEnabled(false);
		    delButton.setEnabled(true);
		    chgButton.setEnabled(true);
		}
	    } else {
		// Set the appropriate buttons, if editable
		if (isEditable()) {
		    addButton.setEnabled(true);
		    delButton.setEnabled(false);
		    chgButton.setEnabled(false);
		}
	    }
	// Empty property list, nothing selected so only Add applies.
	} else if (name == null) {	
	    if (isEditable()) {
		addButton.setEnabled(true);
		delButton.setEnabled(false);
		chgButton.setEnabled(false);
	    }
	}
    }

    protected int checkMandatoryProps() {

	//
	// Make sure a current set of mandatory attributes were
	// set.
	//
	String[] mandatoryNames = {Context.INITIAL_CONTEXT_FACTORY,
				   Context.PROVIDER_URL};

	for (int i = 0; i < mandatoryNames.length; i++) {
	    String propName = jndiProps.getProperty(mandatoryNames[i]);
	    if (propName == null) {
	        JOptionPane.showOptionDialog(this,
		    acr.getString(acr.E_NO_JNDI_PROPERTY_VALUE, mandatoryNames[i]),
		    acr.getString(acr.I_ADD_OBJSTORE) + ": " +
                        acr.getString(acr.I_ERROR_CODE,
                                      AdminConsoleResources.E_NO_JNDI_PROPERTY_VALUE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		// set the combo box to this property name and set cursor at the textfield.
		comboBox.setSelectedItem(mandatoryNames[i]);
		valueText.requestFocus();	
	        return (0);

	    } 
            else if (mandatoryNames[i].equals(Context.INITIAL_CONTEXT_FACTORY)) {
		// Validate the value of initial context provided.
		try {
		    Object obj = (Object)Class.forName(propName);
		} catch (ClassNotFoundException e) {
	            JOptionPane.showOptionDialog(this,
		        acr.getString(acr.E_CANNOT_INSTANTIATE, propName,
			    mandatoryNames[i]),
		        acr.getString(acr.I_ADD_OBJSTORE) + ": " +
                            acr.getString(acr.I_ERROR_CODE,
                                      AdminConsoleResources.E_CANNOT_INSTANTIATE),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		    // Set the combo box this this property name and 
		    // set cursor at the textfield.
		    comboBox.setSelectedItem(mandatoryNames[i]);
		    valueText.requestFocus();	
		    return 0;
		}

	    }
	}
	return 1;
    }

    protected String getDefaultStoreName(String baseName)  {
        if (osMgr.getStore(baseName) == null)  {
            return (baseName);
        }

        for (int i = 1; i < 1000; ++i)  {
            String newStr = baseName + i;
            if (osMgr.getStore(newStr) == null)  {
                return (newStr);
            }
        }

        return ("");
    }

    protected boolean isEditable() {
	return editable;
    }

    protected void setEditable(boolean editable)  {

	this.editable = editable;

	if (editable) {
	    osText.setEditable(true);
	    valueText.setEditable(true);
	    addButton.setEnabled(true);
	    delButton.setEnabled(true);
	    chgButton.setEnabled(true);
	    ta.setText(acr.getString(acr.W_SAVE_AS_CLEAR_TEXT));
	} else {
	    osText.setEditable(false);
	    valueText.setEditable(false);
	    addButton.setEnabled(false);
	    delButton.setEnabled(false);
	    chgButton.setEnabled(false);
	    ta.setText(acr.getString(acr.W_OS_NOT_EDITABLE_TEXT));
	}
    }

    /*
     * Verify this provider URL value exists if we
     * think they are specifying a file URL.
     * If the file url does not exist, just print a warning.
     */
    private void verifyProviderURL(String value) {

	boolean fileURL = false;
	File    file = null;

	// Check if this if a file url by checking the initialContext type.
	String facInit = jndiProps.getProperty(Context.INITIAL_CONTEXT_FACTORY);
	if (facInit != null && !facInit.equals("")) {
	    // If InitialContextFactory = RefFSContextFactory, 
	    // then this is probably a file url.
	    if (facInit.equals("com.sun.jndi.fscontext.RefFSContextFactory")) {
	       fileURL = true;
	    }
	}

	// If we still don't know if this is a file URL, check if
	// the provider url value starts with "file:".
	if (!fileURL && value.startsWith("file:")) {
	    fileURL = true;
	}

	// OK, we think they are specifying a file URL,
	// so let's see if this file URL exists.
	// If not, then print a warning.
	if (fileURL) {
	    try {
	    URI uri = new URI(value);
	    file = new File(uri);
	    } catch (Exception e) {
	        try {
	            file = new File(value);  // in case no "file:" specified.
		} catch (Exception ex) {
		    file = null;  // do nothing - we tried.
	        }
	    }	
	    if (file != null && !file.isDirectory()) {
		JOptionPane.showOptionDialog(this, 
                    acr.getString(acr.W_PROVIDER_URL, Context.PROVIDER_URL, value),
                    acr.getString(acr.I_OBJSTORE) + ": "
                        + acr.getString(acr.I_WARNING_CODE, acr.W_PROVIDER_URL),
            	    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
		    null, close, close[0]);
	    }
	}

    }

    /*
     * TableModel class for Property Table.
     * Note: This model assumes the objects at column = 0
     * are the ConsoleObj objects.
     */

    private static String [] columnNames = {acr.getString(acr.I_NAME), 
				 	    acr.getString(acr.I_VALUE)};

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
	    if (jndiProps == null)
		return 0;
	    else
	        return jndiProps.size();
        }

	/**
	 * Returns the collumn name/label for a given collumn.
	 *
	 * @return the collumn name/label for a given collumn.
	 */
        public String getColumnName(int col) {
            return columnNames[col];
        }

	/**
	 * Return value at a particular table cell location.
	 * Calls the TabledInspector.getValueAtCollumn()
	 * method.
	 */
        public Object getValueAt(int row, int col) {

	    if (jndiProps == null)
		return "";

	    int i = 0;
	    for (Enumeration e = jndiProps.propertyNames(); 
					e.hasMoreElements();) {
		String propName = (String)e.nextElement();
		if (col == 0 && i == row)
		    return propName;
		else if (col == 1 && i == row)
		    return jndiProps.getProperty(propName);
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

    class ObjStoreDocumentListener implements DocumentListener  {
        public void insertUpdate(DocumentEvent e) { dirty = true; }
        public void removeUpdate(DocumentEvent e) { dirty = true; }
        public void changedUpdate(DocumentEvent e) { dirty = true; }
    }

}
