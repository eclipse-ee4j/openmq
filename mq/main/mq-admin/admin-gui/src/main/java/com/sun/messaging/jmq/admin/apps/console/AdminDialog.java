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
 * @(#)AdminDialog.java	1.18 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.help.DefaultHelpBroker;
import javax.help.HelpBroker;
import javax.help.HelpSet;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.event.EventListenerList;

import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.util.Globals;

/** 
 * This is a basic dialog class for the admin GUI.
 * It's features are:
 * <UL>
 * <LI>Built in OK, APPLY, RESET, CLEAR, CANCEL, CLOSE, HELP buttons.
 * <LI>Ability to select which of the above buttons to be created.
 * <LI>Abstract callback methods for each of the above methods.
 * <LI>Abstract method for creating <EM>work panel</EM>. This is 
 * where the subclass would have code for creating the components
 * in the dialog that do the real work.
 * <LI>Ability to add/remove AdminEvent event listeners.
 * <LI>Ability to fire AdminEvent events.
 * </UL>
 * Note that this class does not call hide() to pop the dialog down.
 * The application code that uses this dialog needs to do that.
 */
public abstract class AdminDialog extends JDialog
			implements ActionListener  {
    
    /**
     * Bit value for OK button.
     */
    public static final int	OK		= 1 << 0;

    /**
     * Bit value for APPLY button.
     */
    public static final int	APPLY		= 1 << 1;

    /**
     * Bit value for CLEAR button.
     */
    public static final int	CLEAR		= 1 << 2;

    /**
     * Bit value for RESET button.
     */
    public static final int	RESET		= 1 << 3;

    /**
     * Bit value for CANCEL button.
     */
    public static final int	CANCEL		= 1 << 4;

    /**
     * Bit value for CLOSE button.
     */
    public static final int	CLOSE		= 1 << 5;

    /**
     * Bit value for HELP button.
     */
    public static final int	HELP		= 1 << 6;

    private EventListenerList	aListeners = new EventListenerList();
    private int			whichButtons = 0;
    protected JPanel 		buttonPanel = null;
    protected JButton		okButton = null,
				cancelButton = null,
				closeButton = null,
				clearButton = null,
				resetButton = null,
				applyButton = null,
				helpButton = null;
    private static boolean helpDisplayed = false;

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

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
    public AdminDialog(Frame parent, String title)  {
	this(parent, title, (OK | CANCEL | HELP));
    }

    /**
     * Creates a non-modal dialog using the specified frame as parent and string
     * as title. Will contain the buttons as specified by the <EM>whichButtons</EM>
     * parameter.
     *
     * @param parent the Frame from which the dialog is displayed
     * @param title the String to display in the dialog's title bar
     * @param whichButtons bit flags OR'd together to determine which
     *		buttons are needed. Valid values here are:
     * <UL>
     * <LI>AdminDialog.OK
     * <LI>AdminDialog.APPLY
     * <LI>AdminDialog.RESET
     * <LI>AdminDialog.CANCEL
     * <LI>AdminDialog.CLOSE
     * <LI>AdminDialog.CLEAR
     * <LI>AdminDialog.HELP
     * </UL>
     */
    public AdminDialog(Frame parent, String title, int whichButtons)  {
	super(parent, title, true);
        this.whichButtons = whichButtons;
	initContentPane(true);
	pack();
    }

    public AdminDialog(Frame parent, String title, int whichButtons,
			boolean border)  {
	super(parent, title, true);
        this.whichButtons = whichButtons;
	initContentPane(border);
	pack();
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

    /**
     * Fire off/dispatch an admin event to all the listeners.
     * @param ae AdminEvent to dispatch to event listeners.
     */
    public void fireAdminEventDispatched(AdminEvent ae)  {
	Object[] l = aListeners.getListenerList();

	for (int i = l.length-2; i>=0; i-=2)  {
	    if (l[i] == AdminEventListener.class)  {
		((AdminEventListener)l[i+1]).adminEventDispatched(ae);
	    }
	}
    }

    /*
     * BEGIN INTERFACE ActionListener
     */
    public void actionPerformed(ActionEvent e)  {
	Object source = e.getSource();

	if (source == okButton)  {
	    doOK();
	} else if (source == applyButton)  {
	    doApply();
	} else if (source == cancelButton)  {
	    doCancel();
	} else if (source == closeButton)  {
	    doClose();
	} else if (source == clearButton)  {
	    doClear();
	} else if (source == resetButton)  {
	    doReset();
	} else if (source == helpButton)  {
	    doHelp();
	}
    }
    /*
     * END INTERFACE ActionListener
     */

    private void initContentPane(boolean border)  {
	JPanel	panel;

	panel = new JPanel();
	panel.setLayout(new BorderLayout());
	if (border) {
	    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	}

	/*
	 * Create 'work' panel
	 */
	JPanel workPanel = createWorkPanel();

	/*
	 * Create button panel
	 */
	buttonPanel = createButtonPanel();

	panel.add(workPanel, "Center");
	panel.add(buttonPanel, "South");

	getContentPane().add(panel);
    }

    /*
     * Create the button panel. Check the 'whichButtons' bitfield
     * to determine which of the OK/APPLY/RESET/CANCEL/CLOSE/CLEAR/HELP
     * buttons to create.
     */
    private JPanel createButtonPanel()  {
	/*
	 * Create button panel
	 */
	JPanel buttonPanel = new JPanel();
	buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

	if (useButton(OK))  {
	    okButton = new JButton(acr.getString(acr.I_DIALOG_OK));
	    okButton.addActionListener(this);
	    buttonPanel.add(okButton);
	}

	if (useButton(APPLY))  {
	    applyButton = new JButton(acr.getString(acr.I_DIALOG_APPLY));
	    applyButton.addActionListener(this);
	    buttonPanel.add(applyButton);
	}

	if (useButton(CLEAR))  {
	    clearButton = new JButton(acr.getString(acr.I_DIALOG_CLEAR));
	    clearButton.addActionListener(this);
	    buttonPanel.add(clearButton);
	}

	if (useButton(RESET))  {
	    resetButton = new JButton(acr.getString(acr.I_DIALOG_RESET));
	    resetButton.addActionListener(this);
	    buttonPanel.add(resetButton);
	}

	if (useButton(CANCEL))  {
	    cancelButton = new JButton(acr.getString(acr.I_DIALOG_CANCEL));
	    cancelButton.addActionListener(this);
	    buttonPanel.add(cancelButton);
	}

	if (useButton(CLOSE))  {
	    closeButton = new JButton(acr.getString(acr.I_DIALOG_CLOSE));
	    closeButton.addActionListener(this);
	    buttonPanel.add(closeButton);
	}

	if (useButton(HELP))  {
	    helpButton = new JButton(acr.getString(acr.I_DIALOG_HELP));
	    helpButton.setEnabled(false);
	    helpButton.addActionListener(this);
	    buttonPanel.add(helpButton);
	}

	return (buttonPanel);
    }

    public void setHelpId(String helpId) {

	if (helpButton == null)
	    return;


	if (ConsoleHelp.helpLoaded())  {
            HelpBroker hb = ConsoleHelp.hb[ConsoleHelp.CONSOLE_HELP];
            HelpSet hs = ConsoleHelp.hs[ConsoleHelp.CONSOLE_HELP];
            helpButton.addActionListener(ConsoleHelp.hl[ConsoleHelp.CONSOLE_HELP]);
            // go to a specific area of overview.
            hb.enableHelp(helpButton, helpId, hs);

	    helpButton.setEnabled(true);
	}

    }

    public void setDefaultButton(int whichButton) {
 	JButton button = null;
 
	if (useButton(OK))  {
	    button = okButton;
	} else if (useButton(APPLY)) {
	    button = applyButton;
	} else if (useButton(CLEAR)) {
	    button = clearButton;
	} else if (useButton(RESET)) {
	    button = resetButton;
	} else if (useButton(CANCEL)) {
	    button = cancelButton;
	} else if (useButton(CLOSE)) {
	    button = closeButton;
	} else if (useButton(HELP)) {
	    button = helpButton;
	}

	if (button != null) {
	   this.getRootPane().setDefaultButton(button);
	}
    }

    public void hide() {
	/* 
   	 * Can only call this only after help has been displayed
	 * at least once.  Otherwise, the call to this on Solaris
	 * will take hang for a few seconds.  STRANGE!!
	 */
	if (helpDisplayed) {
            HelpBroker hb = ConsoleHelp.hb[ConsoleHelp.CONSOLE_HELP];
            ((DefaultHelpBroker)hb).setActivationWindow(null);
            hb.setDisplayed(false);
	}

	super.hide();
    }

    /*
     * Convenience method to check the 'whichButtons' bitfield
     * to see if a button needs to be created.
     */
    private boolean useButton(int buttonFlag)  {
	return ((whichButtons & buttonFlag) == buttonFlag);
    }

    public abstract JPanel createWorkPanel();
    public abstract void doOK();
    public abstract void doApply();
    public abstract void doCancel();
    public abstract void doClose();
    public abstract void doClear();
    public abstract void doReset();
    public void doHelp() {
/*	
        DialogEvent de = new DialogEvent(this);
        de.setDialogType(DialogEvent.HELP_DIALOG);
        //de.setUrl();
        fireAdminEventDispatched(de);
*/
        HelpBroker hb = ConsoleHelp.hb[ConsoleHelp.CONSOLE_HELP];
	((DefaultHelpBroker)hb).setActivationWindow(this);
        hb.setDisplayed(true);
	helpDisplayed = true;

    }
}
