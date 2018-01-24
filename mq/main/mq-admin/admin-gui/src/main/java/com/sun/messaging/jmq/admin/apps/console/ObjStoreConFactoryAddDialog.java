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
 * @(#)ObjStoreConFactoryAddDialog.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import java.util.Properties;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.objstore.ObjStore;

/** 
 * The inspector component of the admin GUI displays attributes
 * of the currently selected item. It does not know or care about
 * what is currently selected. It is basically told what to display
 * i.e. what object's attributes to display.
 * <P>
 * There are a variety of objects that can be <EM>inspected</EM>:
 * <UL>
 * <LI>Collection of object stores
 * <LI>Individual object stores
 * <LI>Collection of brokers
 * <LI>Individual brokers
 * <LI>Collection of services
 * <LI>Individual services
 * <LI>Collection of Topics
 * <LI>Individual Topics
 * <LI>Collection of Queues
 * <LI>Individual Queues
 * <LI>
 * </UL>
 *
 * For each of the object types above, a different inspector panel
 * is potentially needed for displaying the object's attributes.
 * This will be implemented by having a main panel stacking all the 
 * different property panels in CardLayout. For each object that
 * needs to be inspected, the object needs to be passed in to the
 * inspector as well as it's type.
 */
public class ObjStoreConFactoryAddDialog extends ObjStoreConFactoryDialog  {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};
    private ObjStoreConFactoryListCObj osCObj;

    public ObjStoreConFactoryAddDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_ADD_OBJSTORE_CF), (OK | RESET | CANCEL | HELP));
	setHelpId(ConsoleHelpID.ADD_CF_OBJECT);
    }

    public void doOK()  {

	/*
	 * Lookup Name
	 */
	String lookupName = lookupText.getText();
	lookupName = lookupName.trim();

	if (lookupName == null || lookupName.equals("")) {
	    JOptionPane.showOptionDialog(this, 
		acr.getString(acr.E_NO_LOOKUP_NAME),
		acr.getString(acr.I_ADD_OBJSTORE_CF) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    		  AdminConsoleResources.E_NO_LOOKUP_NAME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}
	

	/*
	 * Factory Type
	 */
	int type = ObjAdminEvent.CF;
	AdministeredObject tempObj = null;

	String factory = (String)factoryCombo.getSelectedItem();
	if (factory.equals(acr.getString(acr.I_QCF))) {
	    type = ObjAdminEvent.QCF;
	    tempObj = (AdministeredObject)new com.sun.messaging.QueueConnectionFactory();
	} else if (factory.equals(acr.getString(acr.I_TCF))) {
	    type = ObjAdminEvent.TCF;
	    tempObj = (AdministeredObject)new com.sun.messaging.TopicConnectionFactory();
	} else if (factory.equals(acr.getString(acr.I_CF))) {
	    type = ObjAdminEvent.CF;
	    tempObj = (AdministeredObject)new com.sun.messaging.ConnectionFactory();
	} else if (factory.equals(acr.getString(acr.I_XAQCF))) {
	    type = ObjAdminEvent.XAQCF;
	    tempObj = (AdministeredObject)new com.sun.messaging.XAQueueConnectionFactory();
	} else if (factory.equals(acr.getString(acr.I_XATCF))) {
	    type = ObjAdminEvent.XATCF;
	    tempObj = (AdministeredObject)new com.sun.messaging.XATopicConnectionFactory();
	} else if (factory.equals(acr.getString(acr.I_XACF))) {
	    type = ObjAdminEvent.XACF;
	    tempObj = (AdministeredObject)new com.sun.messaging.XAConnectionFactory();
	}

	/*
	 * Conn Factory Object Properties.
	 * Go through each of the cfProps, get the userdata which is
	 * the property name, get the value, set it in props.
	 */
	Properties props = tempObj.getConfiguration();
	String propName, propValue, propType = null, propLabel = null;

	for (int i = 0; i < cfProps.size(); i++) {
	    LabelledComponent cfItem = (LabelledComponent)cfProps.elementAt(i);
            propName = (String)cfItem.getClientData();
            if (propName == null) 
		continue;

	    if (!(cfItem.getComponent().isEnabled())) {
		props.remove(propName);
		continue;
	    }

	    // XXX Asssumes ConnectionType is set before it's
	    // associated properties or exception will occur.
	    try {
                propType = tempObj.getPropertyType(propName);
                propLabel = tempObj.getPropertyLabel(propName);
	    } catch (javax.jms.JMSException jmsex) {
	    	JOptionPane.showOptionDialog(this, 
		    jmsex.toString(),
		    acr.getString(acr.I_ADD_OBJSTORE_CF),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		return;
	    }

	    if (propType == null) 
		continue;

	    propValue = getValue(cfItem.getComponent(), propType).trim();

            // If blank, then use default set in Administered Object
            // so no need to set to "".
	    if (propValue.equals(""))
	 	continue;

 	    try {
	        // Calling setProperty() will verify if this value is valid.
		tempObj.setProperty(propName, propValue);
		props.put(propName, propValue);
	    } catch (javax.jms.JMSException jmsex) {
		if (jmsex instanceof com.sun.messaging.InvalidPropertyValueException) {
	    	    JOptionPane.showOptionDialog(this, 
			acr.getString(acr.E_INVALID_VALUE, propLabel),
			acr.getString(acr.I_ADD_OBJSTORE_CF) + ": " +
                                acr.getString(acr.I_ERROR_CODE,
                                	      AdminConsoleResources.E_INVALID_VALUE),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		} else { 
	    	    JOptionPane.showOptionDialog(this, 
			jmsex.toString(),
			acr.getString(acr.I_ADD_OBJSTORE_CF),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, close, close[0]);
		}
		return;
	    }
	}

	ObjAdminEvent oae = new ObjAdminEvent(this, ObjAdminEvent.ADD_CONN_FACTORY);
	ObjStore os = osCObj.getObjStore();

	/*
	 * Set values in the event.
	 */
	oae.setLookupName(lookupName);
	oae.setObjStore(os);  
	oae.setFactoryType(type);
	oae.setObjProperties(props);
        if (checkBox.isSelected())
            oae.setReadOnly(true);
        else
            oae.setReadOnly(false);
	oae.setOKAction(true);
	fireAdminEventDispatched(oae);
    }

    public void doApply()  { }
    public void doReset() { 

	// Reset the look up name and factory type.
	lookupText.setText("");
	factoryCombo.setSelectedItem(acr.getString(acr.I_CF));
	checkBox.setSelected(false);

	//
	// Go through each of the items, get the userData which is
	// the property name, then get the default value from a
	// temporary object.
	//
	AdministeredObject tempObj = new com.sun.messaging.QueueConnectionFactory();
	//Properties props = tempObj.getConfiguration();
	String propName = null, propType = null;
	String defaultValue = null;

	for (int i = 0; i < cfProps.size(); i++) {

	    LabelledComponent cfItem = (LabelledComponent)cfProps.elementAt(i);
	    JComponent comp = cfItem.getComponent();

	    propName = (String)cfItem.getClientData();
	    if (propName != null) {
		try {
		    // Get value only if enabled, otherwise
		    // gets, InvalidPropertyException.
		    if (comp.isEnabled()) {
	                propType = tempObj.getPropertyType(propName);
		        defaultValue = tempObj.getProperty(propName);
	                setValue(comp, propType, defaultValue);

		        // XXX This assumes the ConnectionType setting
		        // comes before the getting of the connection handler
		        // properties. Also, hard codes JMQConnectionType
		        // setting.
		        if (comp instanceof JComboBox) {
			    doComboBox((JComboBox)comp);
		        }
		    }
		    
		} catch (Exception e) {
		    System.err.println(e.toString() + ": " + propName);
		}
	    }
	}

	// XXX Bad
	AdministeredObject tempObj2 = new com.sun.messaging.QueueConnectionFactory();
	setOtherValues(tempObj2, false);

	lookupText.requestFocus();
	
    }

    public void doCancel() { hide(); }
    public void doClose() { hide(); }
    public void doClear() { }

    public void show() { }

    public void show(ObjStoreConFactoryListCObj osCObj)  {
	this.osCObj = osCObj;
	doReset();

	// Go back to first tab.
	tabbedPane.setSelectedIndex(0);

	super.show();
    }

}
