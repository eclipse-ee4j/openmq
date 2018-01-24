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
 * @(#)ObjStoreDestPropsDialog.java	1.17 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import java.util.Enumeration;
import java.util.Properties;
import javax.swing.JOptionPane;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.apps.console.event.ObjAdminEvent;
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
public class ObjStoreDestPropsDialog extends ObjStoreDestDialog  {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static String close[] = {acr.getString(acr.I_DIALOG_CLOSE)};
    private static String okcancel[] = {"OK", "Cancel"};
    private ObjStoreDestCObj osDestCObj;

    public ObjStoreDestPropsDialog(Frame parent)  {
	super(parent, acr.getString(acr.I_OBJSTORE_DEST_PROPS), (OK | CANCEL | HELP));
	setHelpId(ConsoleHelpID.DEST_OBJECT_PROPS);
    }

    public void doOK()  {
        Object object = osDestCObj.getObject();

	// Check to see if the retrieved object's version is
	// compatible with the current product version.
	// No need to check for invalid/missing version number, as
	// an exception must have been already thrown if that 
	// was the case.
        if (object instanceof AdministeredObject) {
	    AdministeredObject adminObj = (AdministeredObject)object;
	    String curVersion = adminObj.getVERSION();
	    String objVersion = adminObj.getStoredVersion();

            if (!adminObj.isStoredVersionCompatible()) {
		int response = JOptionPane.showOptionDialog(this, 
                    acr.getString(acr.W_INCOMPATIBLE_OBJ, objVersion, curVersion),
                    acr.getString(acr.I_OBJSTORE_DEST_PROPS)
                        + ": "
                        + acr.getString(acr.I_WARNING_CODE, acr.W_INCOMPATIBLE_OBJ),
            	    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
		    null, okcancel, okcancel[1]);

	        if (response == JOptionPane.NO_OPTION)
		    return;
	    }
	}

	/*
	 * Lookup Name
	 */
	String lookupName = lookupLabel.getText();
	lookupName = lookupName.trim();

	if (lookupName == null || lookupName.equals("")) {
	    JOptionPane.showOptionDialog(this, 
		acr.getString(acr.E_NO_LOOKUP_NAME),
		acr.getString(acr.I_OBJSTORE_DEST_PROPS) + ": " +
                    acr.getString(acr.I_ERROR_CODE,
                    		  AdminConsoleResources.E_NO_LOOKUP_NAME),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE, null, close, close[0]);
	    return;
	}
	
	/*
	 * Destination Type
	 */
	int type = ObjAdminEvent.QUEUE;
	AdministeredObject tempObj = null;

	if (destLabel.getText().equals(acr.getString(acr.I_QUEUE))) {
	    type = ObjAdminEvent.QUEUE;
	    tempObj = (AdministeredObject)new com.sun.messaging.Queue();
	}
	else if (destLabel.getText().equals(acr.getString(acr.I_TOPIC))) {
	    type = ObjAdminEvent.TOPIC;
	    tempObj = (AdministeredObject)new com.sun.messaging.Topic();
	}

	/*
	 * Object Properties (dest name, ...);
	 */
	int i = 0;
	Properties props = tempObj.getConfiguration();
	for (Enumeration e = tempObj.enumeratePropertyNames(); 
				e.hasMoreElements(); i++) {
	    String propName = (String)e.nextElement();
	    String value = textItems[i].getText();
	    value = value.trim();

            // If blank, then use default set in Administered Object
            // so no need to set to "".
            if (!(value.trim()).equals("")) {
                props.put(propName, value);
            }
	}

	
	ObjAdminEvent oae = new ObjAdminEvent(this, ObjAdminEvent.UPDATE_DESTINATION);
	ObjStore os = osDestCObj.getObjStore();

	/*
	 * Set values in the event.
	 */
	oae.setLookupName(lookupName);
	oae.setObjStore(os);  
	oae.setDestinationType(type);
	oae.setObjProperties(props);
        if (checkBox.isSelected())
            oae.setReadOnly(true);
        else
            oae.setReadOnly(false);
	oae.setOKAction(true);
	fireAdminEventDispatched(oae);
    }

    public void doApply()  { }
    public void doReset() { }
    public void doCancel() { hide(); }
    public void doClose() { hide(); }
    public void doClear() { }

    public void show()  { }
    public void show(ObjStoreDestCObj osDestCObj)  {

	this.osDestCObj = osDestCObj;
	//ObjStore os = osDestCObj.getObjStore();
	//
	// Set fields to current destination values.
	//
	lookupLabel.setText(osDestCObj.getLookupName());

	Object object = osDestCObj.getObject();
	if (object instanceof com.sun.messaging.Queue)
	    destLabel.setText(acr.getString(acr.I_QUEUE));
	else
	    destLabel.setText(acr.getString(acr.I_TOPIC));

	//
	// Create a temp object and set its default values in the
	// text fields.
	//
	AdministeredObject adminObj = (AdministeredObject)object;
	Properties props = adminObj.getConfiguration();
	int i = 0;
	for (Enumeration e = adminObj.enumeratePropertyNames(); 
					e.hasMoreElements(); i++) {
	    String propName = (String)e.nextElement();
	    try {
	        textItems[i].setText(adminObj.getProperty(propName));
	    } catch (Exception ex) {
	        textItems[i].setText("");
	    }
	}
	//
	// Set Read-only field.
	//
	if (adminObj.isReadOnly())
	    checkBox.setSelected(true);
	else
	    checkBox.setSelected(false);
	
	/*
	 * Set focus to first text item.
	 */
	if (props.size() > 0)
	    textItems[0].requestFocus();

	super.show();
    }

}
