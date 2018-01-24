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
 * @(#)AInspector.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Dimension;
import java.awt.CardLayout;
import java.util.Hashtable;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.EventListenerList;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;

/** 
 * The inspector component of the admin GUI displays attributes
 * of a specified console object.
 *
 * <P>
 * There are a variety of objects that can be <EM>inspected</EM>:
 * <UL>
 * <LI>ObjStore List
 * <LI>ObjStore
 * <LI>ObjStore Destination List
 * <LI>ObjStore ConnectionFactory List
 * <LI>Broker List
 * <LI>Broker
 * <LI>Broker Service List
 * <LI>Broker Destination List
 * <LI>Broker Log List
 * </UL>
 *
 * <P>
 * For each of the object types above, a different inspector panel
 * is needed for displaying the object's attributes.
 *
 * <P>
 * This is implemented by having a main panel stacking all the 
 * different InspectorPanels in CardLayout. Each console object
 * that can be inspected will contain information specifying
 * which inspector panel to use to inspect it.
 */
public class AInspector extends JScrollPane  {

    private static String		SPLASH_SCREEN	= "SplashScreen";
    private static String		BLANK		= "Blank";

    private EventListenerList		aListeners = new EventListenerList();
    private InspectorPanel		currentCard = null;
    private CardLayout			cardLayout;
    private JPanel			cardPanel;
    private Hashtable			cardList;

    /**
     * Create/initialize the admin inspector GUI component.
     */
    public AInspector() {
	initGui();
    } 

    /**
     * Add an admin event listener to this admin UI component. 
     * @param l	admin event listener to add.
     */
    public void addAdminEventListener(AdminEventListener l)  {
	aListeners.add(AdminEventListener.class, l);
    }


    /**
     * Remove an admin event listener for this admin UI component. 
     * @param l	admin event listener to remove.
     */
    public void removeAdminEventListener(AdminEventListener l)  {
	aListeners.remove(AdminEventListener.class, l);
    }

    public void inspect(ConsoleObj conObj)  {
	/*
        System.err.println("AInspector: inspecting: " + conObj);
        System.err.println("\tClass: " + conObj.getClass().getName());
	*/

	if (conObj == null)  {
	    cardLayout.show(cardPanel, BLANK);
	    return;
	}

	InspectorPanel	ip = getInspectorPanel(conObj);

	if (ip == null)  {
	    ip = addInspectorPanel(conObj);

	    if (ip == null)  {
	        System.err.println("Cannot inspect object: "
	                + conObj
	                + "\nFailed to create inspector panel");
	        return;
	    }
	}

	currentCard = ip;

	currentCard.inspectConsoleObj(conObj, aListeners);
	showInspectorPanel(conObj);
    }

    public void refresh()  {
	if (currentCard != null)  {
	    currentCard.refresh();
	}
    }

    public void selectedObjectUpdated()  {
	if (currentCard != null)  {
	    currentCard.selectedObjectUpdated();
	}
    }

    public void clearSelection()  {
	if (currentCard != null)  {
	    currentCard.clearSelection();
	}
    }

    private void showInspectorPanel(ConsoleObj conObj)  {
        cardLayout.show(cardPanel, conObj.getInspectorPanelId());
    }

    private InspectorPanel getInspectorPanel(ConsoleObj conObj)  {
	String	panelId = conObj.getInspectorPanelId();
	Object obj = cardList.get(panelId);

	if ((obj != null) && (obj instanceof InspectorPanel))  {
	    return ((InspectorPanel)obj);
	}

	return (null);
    }

    private InspectorPanel addInspectorPanel(ConsoleObj conObj)  {
	String	panelId = conObj.getInspectorPanelId();
	String panelClassName = conObj.getInspectorPanelClassName();
	InspectorPanel ip = null;

	try  {
	    ip = (InspectorPanel)Class.forName(panelClassName).newInstance();
	    /*
	    System.err.println("Class: " + panelClassName + " instantiated !!");
	    */
	} catch (ClassNotFoundException cnfEx)  {
	    System.err.println("ConsoleObj does not name a valid inspector panel classname: "
			+ cnfEx);
	} catch (InstantiationException ie)  {
	    System.err.println("Failed to intantiate inspector panel : "
			+ ie);
	} catch (IllegalAccessException iae)  {
	    System.err.println("Illegal Access Exception while trying to intantiate inspector panel : "
			+ iae);
	}

	if (ip == null)
	    return (null);

	cardPanel.add(ip, panelId);
	cardList.put(panelId, ip);

	return (ip);
    }

    /*
     * Fire off/dispatch an admin event to all the listeners.
     
    private void fireAdminEventDispatched(AdminEvent ae)  {
	Object[] l = aListeners.getListenerList();

	for (int i = l.length-2; i>=0; i-=2)  {
	    if (l[i] == AdminEventListener.class)  {
		((AdminEventListener)l[i+1]).adminEventDispatched(ae);
	    }
	}
    }
    */
   
    private void initGui()  {

	cardPanel = new JPanel();
	cardLayout = new CardLayout();
	cardPanel.setLayout(cardLayout);

        initLayers(cardPanel);

	setViewportView(cardPanel);

        Dimension minimumSize = new Dimension(100, 50);
        setMinimumSize(minimumSize);

    }

    private void initLayers(JPanel parent)  {
	JPanel p = new JPanel(); 
	cardList = new Hashtable();

	p = new SplashScreenInspector(); 
	parent.add(p, SPLASH_SCREEN);
	cardList.put(SPLASH_SCREEN, p);

	p = new BlankInspector(); 
	parent.add(p, BLANK);
	cardList.put(BLANK, p);

	cardLayout.show(parent, SPLASH_SCREEN);
    }
}

