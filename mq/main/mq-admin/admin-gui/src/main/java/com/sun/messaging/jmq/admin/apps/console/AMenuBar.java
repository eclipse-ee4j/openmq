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
 * @(#)AMenuBar.java	1.24 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.help.HelpBroker;
import javax.help.HelpSet;

import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.Box;
import javax.swing.KeyStroke;
import javax.swing.ImageIcon;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * This class is the menubar class used in the admin console
 * application.
 * <P>
 *
 * All the menu items created here are done via Actions and
 * they are controlled by the ActionManager class. This class
 * has (or will have) various methods to manipulate the
 * appearance of various menu items e.g. label.
 *
 */
public class AMenuBar extends JMenuBar  {

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static final int cmdKey = 
			Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    private ActionManager	actionMgr = null;
    private ConsoleObj		conObj = null;
    private boolean		displayIcons;

    private JMenu consoleMenu;
    private JMenuItem showLogItem;
    private JMenuItem showCommandsItem;
    private JMenuItem showHelpItem;
    private JMenuItem prefsItem;
    private JMenuItem exitItem;

    private JMenu editMenu;
    private JMenuItem deleteItem;

    private JMenu actionsMenu;
    private JMenuItem addItem;
    private JMenuItem purgeItem;
    private JMenuItem connectItem;
    private JMenuItem disconnectItem;
    private JMenuItem shutdownBrokerItem;
    private JMenuItem restartBrokerItem;
    private JMenuItem queryBrokerItem;
    private JMenuItem pauseItem;
    private JMenuItem resumeItem;
    private JMenuItem propsItem;

    private JMenu viewMenu;
    private JMenuItem expandItem;
    private JMenuItem collapseItem;
    private JMenuItem refreshItem;

    private JMenu helpMenu;
    private JMenuItem overviewItem;
    private JMenuItem aboutItem;


    /**
     * Creates a menubar for the admin console application.
     *
     * @param actionMgr		The ActionManager.
     */
    public AMenuBar(ActionManager actionMgr) {
	this(actionMgr, true);
    } 

    /**
     * Creates a menubar for the admin console application.
     *
     * @param actionMgr		The ActionManager.
     * @param displayIcons	Flag to indicate whether the
     *				menu items should display icons
     *				for the action if available.
     */
    public AMenuBar(ActionManager actionMgr, boolean displayIcons) {
	super();
	this.actionMgr = actionMgr;
	this.displayIcons = displayIcons;

	initGui();
    } 

    /**
     * Set the <EM>current</EM> console object.
     * The purpose for doing this is to determine
     * what labels/icons to display for the menu items.
     *
     * @param conObj	Console object.
     */
    public void setConsoleObj(ConsoleObj conObj)  {
	this.conObj = conObj;
	setLabels();
	setIcons();
    }

    /**
     * Creates the main MenuBar
     */
    private void initGui()  {
	/*
	 * Console Menu
	 */
	consoleMenu = (JMenu)this.add(new JMenu(acr.getString(acr.I_MENU_CONSOLE)));
	consoleMenu.setMnemonic(acr.getChar(acr.I_CONSOLE_MNEMONIC));

	/*
	prefsItem = addMenuItem(consoleMenu, ActionManager.PREFERENCES);
	prefsItem.setMnemonic(acr.getChar(acr.I_PREFERENCES_MNEMONIC));

	consoleMenu.add(new JSeparator());
	*/

	exitItem = addMenuItem(consoleMenu, ActionManager.EXIT);
	exitItem.setText(acr.getString(acr.I_MENU_EXIT));
	exitItem.setMnemonic(acr.getChar(acr.I_EXIT_MNEMONIC));
        exitItem.setAccelerator(KeyStroke.getKeyStroke
				(acr.getChar(acr.I_QUIT_ACCELERATOR), cmdKey));

	/*
	 * Edit menu
	 */
	editMenu = (JMenu) this.add(new JMenu(acr.getString(acr.I_MENU_EDIT)));
	editMenu.setMnemonic(acr.getChar(acr.I_EDIT_MNEMONIC));

	deleteItem = addMenuItem(editMenu, ActionManager.DELETE);
	deleteItem.setText(acr.getString(acr.I_MENU_DELETE));
	deleteItem.setMnemonic(acr.getChar(acr.I_DELETE_MNEMONIC));

	/*
	 * Actions menu
	 */
	actionsMenu = (JMenu)this.add(new JMenu(acr.getString(acr.I_MENU_ACTIONS)));
	actionsMenu.setMnemonic(acr.getChar(acr.I_ACTIONS_MNEMONIC));	

	addItem = addMenuItem(actionsMenu, ActionManager.ADD);
	addItem.setText(acr.getString(acr.I_MENU_ADD));
        addItem.setMnemonic(acr.getChar(acr.I_ADD_MNEMONIC));
        addItem.setAccelerator(KeyStroke.getKeyStroke
				(acr.getChar(acr.I_ADD_ACCELERATOR), cmdKey));


	purgeItem = addMenuItem(actionsMenu, ActionManager.PURGE);
	purgeItem.setText(acr.getString(acr.I_MENU_PURGE_BROKER_DEST));
        purgeItem.setMnemonic(acr.getChar(acr.I_PURGE_MNEMONIC));


	actionsMenu.add(new JSeparator());

	connectItem = addMenuItem(actionsMenu, ActionManager.CONNECT);
	connectItem.setText(acr.getString(acr.I_MENU_CONNECT));
	connectItem.setMnemonic(acr.getChar(acr.I_CONNECT_MNEMONIC));

	disconnectItem = addMenuItem(actionsMenu, ActionManager.DISCONNECT);
	disconnectItem.setText(acr.getString(acr.I_MENU_DISCONNECT));
	disconnectItem.setMnemonic(acr.getChar(acr.I_DISCONNECT_MNEMONIC));

	actionsMenu.add(new JSeparator());

	queryBrokerItem = addMenuItem(actionsMenu, ActionManager.QUERY_BROKER);
	queryBrokerItem.setText(acr.getString(acr.I_MENU_QUERY_BROKER));
	queryBrokerItem.setMnemonic(acr.getChar(acr.I_QUERY_BROKER_MNEMONIC));

	pauseItem = addMenuItem(actionsMenu, ActionManager.PAUSE);
	pauseItem.setText(acr.getString(acr.I_MENU_PAUSE));
	pauseItem.setMnemonic(acr.getChar(acr.I_PAUSE_MNEMONIC));

	resumeItem = addMenuItem(actionsMenu, ActionManager.RESUME);
	resumeItem.setText(acr.getString(acr.I_MENU_RESUME));
	resumeItem.setMnemonic(acr.getChar(acr.I_RESUME_MNEMONIC));

	restartBrokerItem = addMenuItem(actionsMenu, ActionManager.RESTART);
	restartBrokerItem.setText(acr.getString(acr.I_MENU_RESTART_BROKER));
	restartBrokerItem.setMnemonic(acr.getChar(acr.I_RESTART_MNEMONIC));

	shutdownBrokerItem = addMenuItem(actionsMenu, ActionManager.SHUTDOWN);
	shutdownBrokerItem.setText(acr.getString(acr.I_MENU_SHUTDOWN_BROKER));
	shutdownBrokerItem.setMnemonic(acr.getChar(acr.I_SHUTDOWN_MNEMONIC));

	actionsMenu.add(new JSeparator());

	propsItem = addMenuItem(actionsMenu, ActionManager.PROPERTIES);
	propsItem.setText(acr.getString(acr.I_MENU_PROPERTIES));
	propsItem.setMnemonic(acr.getChar(acr.I_PROPERTIES_MNEMONIC));


	/*
	 * View menu
	 */
	viewMenu = (JMenu) this.add(new JMenu(acr.getString(acr.I_MENU_VIEW)));
	viewMenu.setMnemonic(acr.getChar(acr.I_VIEW_MNEMONIC));

	expandItem = addMenuItem(viewMenu, ActionManager.EXPAND_ALL);
	expandItem.setText(acr.getString(acr.I_MENU_EXPAND_ALL));
	expandItem.setMnemonic(acr.getChar(acr.I_EXPAND_ALL_MNEMONIC));

	collapseItem = addMenuItem(viewMenu, ActionManager.COLLAPSE_ALL);
	collapseItem.setText(acr.getString(acr.I_MENU_COLLAPSE_ALL));
	collapseItem.setMnemonic(acr.getChar(acr.I_COLLAPSE_ALL_MNEMONIC));

	viewMenu.add(new JSeparator());

	refreshItem = addMenuItem(viewMenu, ActionManager.REFRESH);
	refreshItem.setText(acr.getString(acr.I_MENU_REFRESH));
	refreshItem.setMnemonic(acr.getChar(acr.I_REFRESH_MNEMONIC));

	/*
	 * Workaround for bug:
	 * 4087846 - JMenuBar.setHelpMenu() => "not yet implemented"
	 */
	this.add(Box.createGlue());

	/*
	 * Help menu
	 */
	helpMenu = (JMenu) this.add(new JMenu(acr.getString(acr.I_MENU_HELP)));
	helpMenu.setMnemonic(acr.getChar(acr.I_HELP_MNEMONIC));	

	overviewItem = new JMenuItem(acr.getString(acr.I_OVERVIEW));
	overviewItem.setText(acr.getString(acr.I_MENU_OVERVIEW));
	helpMenu.add(overviewItem);
	if (ConsoleHelp.helpLoaded())  {
	    HelpBroker hb = ConsoleHelp.hb[ConsoleHelp.CONSOLE_HELP]; 
	    HelpSet hs = ConsoleHelp.hs[ConsoleHelp.CONSOLE_HELP]; 
	    overviewItem.addActionListener(ConsoleHelp.hl[ConsoleHelp.CONSOLE_HELP]);
	    // go to a specific area of overview.
	    hb.enableHelp(overviewItem, ConsoleHelpID.INTRO, hs);
	} else {
	    overviewItem.setEnabled(false);
	}

	helpMenu.add(new JSeparator());

	aboutItem = addMenuItem(helpMenu, ActionManager.ABOUT);
	aboutItem.setText(acr.getString(acr.I_MENU_ABOUT));
	aboutItem.setMnemonic(acr.getChar(acr.I_ABOUT_MNEMONIC));

    }

    private JMenuItem addMenuItem(JMenu menu, int actionId)  {
	JMenuItem item;

	item = menu.add(actionMgr.getAction(actionId));
	if (!displayIcons)  {
	    item.setIcon(null);
	}
	
	return (item);
    }

    /*
     * Set labels on menu items based on current console object.
     */
    private void setLabels()  {
	if (conObj == null)  {
	    return;
	}

	checkAndSetLabel(ActionManager.ADD, addItem);
	checkAndSetLabel(ActionManager.CONNECT, connectItem);
	checkAndSetLabel(ActionManager.DISCONNECT, disconnectItem);
	checkAndSetLabel(ActionManager.PAUSE, pauseItem);
	checkAndSetLabel(ActionManager.RESUME, resumeItem);
    }

    private void checkAndSetLabel(int actionFlag, JMenuItem item)  {
	String label;

	label = conObj.getActionLabel(actionFlag, true);
	if (label != null)  {
	    item.setText(label);
	}
    }

    /*
     * Set icons on menu items based on current console object.
     */
    private void setIcons()  {
	if (conObj == null)  {
	    return;
	}

	checkAndSetIcon(ActionManager.CONNECT, connectItem);
	checkAndSetIcon(ActionManager.DISCONNECT, disconnectItem);
    }

    private void checkAndSetIcon(int actionFlag, JMenuItem item)  {
	ImageIcon icon;

	icon = conObj.getActionIcon(actionFlag);
	if (icon != null)  {
	    item.setIcon(icon);
	}
    }
}
