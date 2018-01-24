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
 * @(#)AToolBar.java	1.12 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.ImageIcon;


/** 
 * This class is the toolbar class used in the admin console
 * application.
 * <P>
 *
 * All the items created here are done via Actions and
 * they are controlled by the ActionManager class.
 *
 */
public class AToolBar extends JToolBar  {

    private ConsoleObj		conObj = null;
    private ActionManager	actionMgr;
    private boolean		displayIcons,
				displayText,
				displayToolTip;

    JButton		addButton,
			deleteButton,
			propsButton,
			shutdownButton,
			restartButton,
			connectButton,
			disconnectButton,
			queryButton,
			pauseButton,
			resumeButton,
			refreshButton;

    /**
     * Creates a toolbar for the admin console application.
     * <P>
     * The toolbar created will:
     * <UL>
     * <LI>Display icons
     * <LI>Not display text labels
     * <LI>Display tooltip text
     * </UL>
     *
     * @param actionMgr		The ActionManager.
     */
    public AToolBar(ActionManager actionMgr) {
	this(actionMgr, true, false, true);
    } 

    /**
     * Creates a toolbar for the admin console application.
     *
     * @param actionMgr		The ActionManager.
     * @param displayIcons	Flag to indicate whether the
     *				toolbar buttons should display icons
     *				for the action if available.
     * @param displayText	Flag to indicate whether the
     *				toolbar buttons should display their 
     *				text labels for the action if available.
     * @param displayToolTip	Flag to indicate whether the
     *				toolbar buttons should display their 
     *				tooltip text for the action if available.
     */
    public AToolBar(ActionManager actionMgr,
			boolean displayIcons,
			boolean displayText,
			boolean displayToolTip) {
	super();

	this.actionMgr = actionMgr;
	this.displayIcons = displayIcons;
	this.displayText = displayText;
	this.displayToolTip = displayToolTip;

	initGui();
    } 

    /**
     * Set the <EM>current</EM> console object.
     * The purpose for doing this is to determine
     * what icons/tooltips to display for the buttons.
     *
     * @param conObj	Console object.
     */
    public void setConsoleObj(ConsoleObj conObj)  {
	this.conObj = conObj;
	setLabels();
	setIcons();
    }


    private void initGui()  {
	setFloatable(false);

	/*
	putClientProperty("JToolBar.isRollover", Boolean.TRUE);
	*/

	addButton = addOneAction(actionMgr.getAction(ActionManager.ADD));
	deleteButton = addOneAction(actionMgr.getAction(ActionManager.DELETE));
	propsButton = addOneAction(actionMgr.getAction(ActionManager.PROPERTIES));

	addSeparator();
	connectButton = addOneAction(actionMgr.getAction(ActionManager.CONNECT));
	disconnectButton = addOneAction(actionMgr.getAction(ActionManager.DISCONNECT));

	addSeparator();
	queryButton = addOneAction(actionMgr.getAction(ActionManager.QUERY_BROKER));
	pauseButton = addOneAction(actionMgr.getAction(ActionManager.PAUSE));
	resumeButton = addOneAction(actionMgr.getAction(ActionManager.RESUME));
	restartButton = addOneAction(actionMgr.getAction(ActionManager.RESTART));
	shutdownButton = addOneAction(actionMgr.getAction(ActionManager.SHUTDOWN));

	addSeparator();
	refreshButton = addOneAction(actionMgr.getAction(ActionManager.REFRESH));
    }

    private JButton addOneAction(Action a)  {
	JButton jb;

	jb = add(a);

	if (!displayIcons)  {
	    jb.setIcon(null);
	}
	if (!displayText)  {
	    jb.setText("");
	}
	if (displayToolTip)  {
	    jb.setToolTipText((String)a.getValue(a.NAME));
	}

	return (jb);
    }

    /*
     * Set tooltips on buttons based on current console object.
     */
    private void setLabels()  {
	if (conObj == null)  {
	    return;
	}

	checkAndSetLabel(ActionManager.ADD, addButton);
	checkAndSetLabel(ActionManager.PAUSE, pauseButton);
	checkAndSetLabel(ActionManager.RESUME, resumeButton);
	checkAndSetLabel(ActionManager.CONNECT, connectButton);
	checkAndSetLabel(ActionManager.DISCONNECT, disconnectButton);
    }

    private void checkAndSetLabel(int actionFlag, JButton button)  {
	String label;

	label = conObj.getActionLabel(actionFlag, false);
	if (label != null)  {
	    if (displayToolTip)  {
	        button.setToolTipText(label);
	    }

	    if (displayText)  {
	        button.setText(label);
	    }
	}
    }

    /*
     * Set icons on buttons based on current console object.
     */
    private void setIcons()  {
	if (conObj == null)  {
	    return;
	}

	checkAndSetIcon(ActionManager.CONNECT, connectButton);
	checkAndSetIcon(ActionManager.DISCONNECT, disconnectButton);
    }

    private void checkAndSetIcon(int actionFlag, JButton button)  {
	ImageIcon icon;

	icon = conObj.getActionIcon(actionFlag);
	if (icon != null)  {
	    /*
	     * 'Clear' out disabled icon.
	     * If this is not done, the disabled icon will remain what it was
	     * prior to the setIcon() below.
	     *
	     * This may be related to bug:
	     * 4117779 - JLabel.setIcon() has no effect on default disabled 
	     *			icon of a JLabel
	     */
	    button.setDisabledIcon(null);

	    button.setIcon(icon);
	}
    }

}
