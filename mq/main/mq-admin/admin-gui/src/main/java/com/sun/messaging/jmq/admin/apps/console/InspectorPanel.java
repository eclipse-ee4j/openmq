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
 * @(#)InspectorPanel.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.event.EventListenerList;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;

/** 
 * This InspectorPanel class is the superclass of all inspector panels used
 * to inspect objects in the JMQ Administration GUI.
 *
 * <P>
 * The collection of classes that subclass InspectorPanel are used in the 
 * AInspector class to display the attributes of a specified ConsoleObj 
 * object. 
 *
 * <P>
 * Each ConsoleObj object contains information (i.e. classname) that specifies 
 * which InspectorPanel to use to <EM>inspect</EM> it within the AInspector 
 * class. The AInspector class is a container for all the InspectorPanels used 
 * in the console application.
 *
 * <P>
 * The InspectorPanel class provides the following features:
 * <UL>
 * <LI>allow subclass to create their GUI content via
 * the <STRONG>createWorkPanel()</STRONG> method.
 * <LI>provide method for accessing currently inspected object via
 * <STRONG>getConsoleObj()</STRONG> method.
 * <LI>provide hooks for initializing the panel for the specific
 * ConsoleObj that is being inspected, via the <STRONG>inspectorInit()</STRONG>
 * method.
 * <LI>provide method for firing off events, via the
 * <STRONG>fireAdminEventDispatched</STRONG> method.
 * <LI>provide hooks for deselecting objects in the InspectorPanel GUI,
 * via the <STRONG>clearSelection</STRONG> method. This method will be invoked
 * by the containing AInspector class whenever objects displayed in the
 * InspectorPanel class needs to be deselected.
 * </UL>
 *
 * When a ConsoleObj object is inspected (in the AInspector class), it's
 * InspectorPanel is instantiated (if not already done), and the
 * inspector panel's inspectConsoleObj() method is invoked, passing in
 * the object to inspect as well as an event listeners list which
 * is used when events need to be dispatched.
 *
 * @see AInspector
 * @see ConsoleObj
 */
public abstract class InspectorPanel extends JPanel  {
    
    private EventListenerList	aListeners = null;
    private ConsoleObj		conObj = null;

    /**
     * Instantiate the InspectorPanel. Create the GUI
     * content.
     */
    public InspectorPanel()  {
	initContent();
    }

    /**
     * Fire off/dispatch an admin event to all the listeners.
     *
     * @param ae	The AdminEvent to dispatch/deliver.
     */
    public void fireAdminEventDispatched(AdminEvent ae)  {
	if (aListeners == null)  {
	    return;
	}

	Object[] l = aListeners.getListenerList();

	for (int i = l.length-2; i>=0; i-=2)  {
	    if (l[i] == AdminEventListener.class)  {
		((AdminEventListener)l[i+1]).adminEventDispatched(ae);
	    }
	}
    }

    /**
     * Inspect a ConsoleObj object.<BR>
     *
     * <P>
     * This is the entry point from AInspector. The console object
     * is passed in as well as the event listener list which is used
     * by InspectorPanel whenever an event needs to be dispatched.
     * <P>
     * The InspectorPanel is configured/initialized for the inspected
     * object when this method is called. For example, when a
     * BrokerListCObj object is inspected, it's inspector panel is
     * populated with the list of Brokers that it contains.
     *
     * @param conObj	Object that needs to be inspected.
     * @param l		Event listener for dispatching events.
     */
    public void inspectConsoleObj(ConsoleObj conObj, EventListenerList l)  {
	setConsoleObj(conObj);
	setAdminEventListener(l);
	inspectorInit();
    }


    /**
     * Get currently inspected ConsoleObj object.
     *
     * @return Currently inspected ConsoleObj object.
     */
    public ConsoleObj getConsoleObj()  {
	return (conObj);
    }

    /**
     * Reinitialize inspector panel for the currently inspected object.
     */
    public void refresh()  {
	if (conObj == null)  {
	    return;
	}

	inspectorInit();
    }

    /*
     * Initializes the inspector panel, creates the GUI by invoking
     * createWorkPanel().
     */
    private void initContent()  {
	setLayout(new BorderLayout());

	/*
	 * Create 'work' panel
	 */
	JPanel workPanel = createWorkPanel();

	if (workPanel != null)  {
	    add(workPanel, "Center");
	}
    }

    /*
     * Set currently inspected object.
     * This method is private because there is no need for
     * a public interface for doing this. inspectConsoleObj()
     * should be the only interface for inspecting an object.
     */
    private void setConsoleObj(ConsoleObj conObj)  {
	this.conObj = conObj;
    }

    /*
     * Set event listeners. No need to have the add/remove listeners
     * since inspector panels are used within AInspector. Each inspector
     * panel will basically (for the duration of time that it is
     * visible) inherit the AInspector's event listeners.
     */
    private void setAdminEventListener(EventListenerList l)  {
	aListeners = l;
    }

    /**
     * Creates the InspectorPanel GUI.
     *
     * <P>
     * @return the panel that contains the GUI.
     */
    public abstract JPanel createWorkPanel();

    /**
     * Initializes the InspectorPanel for the currently inspected
     * object.
     */
    public abstract void inspectorInit();

    /**
     * Clears the selection in the InspectorPanel.
     */
    public abstract void clearSelection();

    /**
     * Indicate to inspector panel that the data for the currently
     * selected object has been updated.
     */
    public abstract void selectedObjectUpdated();

}
