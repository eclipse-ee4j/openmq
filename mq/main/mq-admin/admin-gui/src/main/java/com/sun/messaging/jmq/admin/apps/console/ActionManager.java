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
 * @(#)ActionManager.java	1.33 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Hashtable;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.event.EventListenerList;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.BrokerAdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.DialogEvent;
import com.sun.messaging.jmq.admin.apps.console.event.ConsoleActionEvent;


/**
 * This class manages the set of actions used by the admin console.
 * A task is made an 'action' because it can be trigerred from multiple
 * controls e.g. from the toolbar or from a menu. It helps to centralize
 * control of the task in an action so that things like enabling/disabling
 * is made easy.
 */
public class ActionManager  {
    /*
     * Bit flags to identify a particular action.
     * Bit flags are needed because in some cases we need
     * to specify more than one action to enable. These flags
     * are also conveniently used as action identifiers e.g.
     * in getAction().
     */
    public static final int	ADD		= 1 << 0;
    public static final int	DELETE		= 1 << 1;
    public static final int	PREFERENCES	= 1 << 2;
    public static final int	EXIT		= 1 << 3;
    public static final int	ABOUT		= 1 << 4;
    public static final int	PROPERTIES	= 1 << 5;
    public static final int	SHUTDOWN	= 1 << 6;
    public static final int	RESTART		= 1 << 7;
    public static final int	PAUSE		= 1 << 8;
    public static final int	RESUME		= 1 << 9;
    public static final int	CONNECT		= 1 << 10;
    public static final int	DISCONNECT	= 1 << 11;
    public static final int	EXPAND_ALL	= 1 << 12;
    public static final int	COLLAPSE_ALL	= 1 << 13;
    public static final int	REFRESH		= 1 << 14;
    public static final int	PURGE		= 1 << 15;
    public static final int	QUERY_BROKER	= 1 << 16;

    private Hashtable	actionTable = new Hashtable(20);

    /* 
     * Bit mask reflecting which actions are currently active.
     */
    private int		currentlyActive = 0;

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private EventListenerList	aListeners = new EventListenerList();

    public ActionManager()  {
	currentlyActive = initActions(this);
    }

    /** Returns the relevant action object corresponding to the
     * flag passed.
     *
     */
    public Action getAction(int actionFlag)  {
	Action a;
	
	a = (Action)(actionTable.get(Integer.valueOf(actionFlag)));

	return (a);
    }


    /**
     * Set the enabled state of a particular action.
     */
    public void setEnabled(int actionFlag, boolean b)  {
	Action a = getAction(actionFlag);

	if (a == null)  {
	    return;
	}

	if (b)  {
	    if (isActive(currentlyActive, actionFlag))  {
		return;
	    }
	    a.setEnabled(b);

	    /*
	     * Set the flag's bit in the mask.
	     */
	    currentlyActive |= actionFlag;
	} else  {
	    if (!isActive(currentlyActive, actionFlag))  {
		return;
	    }
	    a.setEnabled(b);

	    /*
	     * Unset the flag's bit in the mask.
	     */
	    currentlyActive &= ~actionFlag;
	}
    }


    /**
     * Enable the actions specfied in the mask and disable the
     * ones that are not. This is to bring the current set
     * of actions to be in the state exactly specified by
     * the mask parameter.
     */
    public void setActiveActions(int mask)  {
	if (mask == currentlyActive)  {
	    return;
	}

	/*
	 * Make sure that the actions specified in the mask are enabled.
	 * Make sure that the actions not specified in the mask are disabled.
	 *
	 * Some actions are not specified below because they are to always
	 * remain active.
	 */
	matchActions(mask, ADD);
	matchActions(mask, DELETE);
	matchActions(mask, PROPERTIES);
	matchActions(mask, SHUTDOWN);
	matchActions(mask, RESTART);
	matchActions(mask, PAUSE);
	matchActions(mask, RESUME);
	matchActions(mask, CONNECT);
	matchActions(mask, DISCONNECT);
	matchActions(mask, REFRESH);
	matchActions(mask, PURGE);
	matchActions(mask, QUERY_BROKER);
    }

    public char getCharMnemonic(int actionFlag)  {
        if (actionFlag == ADD)  {
	    return (acr.getChar(acr.I_ADD_MNEMONIC));
	} else if (actionFlag == DELETE)  {
	    return (acr.getChar(acr.I_DELETE_MNEMONIC));
	/*
	} else if (actionFlag == PREFERENCES)  {
	    return (acr.getChar(acr.I_PREFERENCES_MNEMONIC));
	*/
	} else if (actionFlag == EXIT)  {
	    return (acr.getChar(acr.I_EXIT_MNEMONIC));
	} else if (actionFlag == ABOUT)  {
	    return (acr.getChar(acr.I_ABOUT_MNEMONIC));
	} else if (actionFlag == PROPERTIES)  {
	    return (acr.getChar(acr.I_PROPERTIES_MNEMONIC));
	} else if (actionFlag == SHUTDOWN)  {
	    return (acr.getChar(acr.I_SHUTDOWN_MNEMONIC));
	} else if (actionFlag == RESTART)  {
	    return (acr.getChar(acr.I_RESTART_MNEMONIC));
	} else if (actionFlag == PAUSE)  {
	    return (acr.getChar(acr.I_PAUSE_MNEMONIC));
	} else if (actionFlag == RESUME)  {
	    return (acr.getChar(acr.I_RESUME_MNEMONIC));
	} else if (actionFlag == CONNECT)  {
	    return (acr.getChar(acr.I_CONNECT_MNEMONIC));
	} else if (actionFlag == DISCONNECT)  {
	    return (acr.getChar(acr.I_DISCONNECT_MNEMONIC));
	} else if (actionFlag == EXPAND_ALL)  {
	    return (acr.getChar(acr.I_EXPAND_ALL_MNEMONIC));
	} else if (actionFlag == COLLAPSE_ALL)  {
	    return (acr.getChar(acr.I_COLLAPSE_ALL_MNEMONIC));
	} else if (actionFlag == REFRESH)  {
	    return (acr.getChar(acr.I_REFRESH_MNEMONIC));
	} else if (actionFlag == PURGE)  {
	    return (acr.getChar(acr.I_PURGE_MNEMONIC));
	} else if (actionFlag == QUERY_BROKER)  {
	    return (acr.getChar(acr.I_QUERY_BROKER_MNEMONIC));
	}

	return ((char)0);
    }

    /*
     * Checks the action flag in the specified mask and
     * makes sure the corresponding action managed by this
     * class matches it's enabled state.
     */
    private void matchActions(int mask, int actionFlag)  {
	if (isActive(mask, actionFlag))  {
	    setEnabled(actionFlag, true);
	} else  {
	    setEnabled(actionFlag, false);
	}
    }

    /*
     * Returns the enabled state of a particular action in
     * the specified mask.
     */
    private boolean isActive(int mask, int actionFlag)  {
	return ((mask & actionFlag) == actionFlag);
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

    /*
     * Fire off/dispatch an admin event to all the listeners.
     */
    private static void fireAdminEventDispatched(AdminEvent ae, EventListenerList ell)  {
	Object[] l = ell.getListenerList();

	for (int i = l.length-2; i>=0; i-=2)  {
	    if (l[i] == AdminEventListener.class)  {
		((AdminEventListener)l[i+1]).adminEventDispatched(ae);
	    }
	}
    }

    /*
     * Creates all actions
     */
    private static int initActions(ActionManager manager)  {
	Action tmpAction;
        int activeFlags = 0;
        Hashtable table = manager.actionTable;
        final EventListenerList ell = manager.aListeners;

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_ADD),
	        AGraphics.adminImages[AGraphics.ADD])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
			de.setDialogType(DialogEvent.ADD_DIALOG);
			fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(ADD, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_DELETE),
	        AGraphics.adminImages[AGraphics.DELETE])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.DELETE_DIALOG);
			fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(DELETE, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_PREFERENCES),
	        AGraphics.adminImages[AGraphics.PREFERENCES])  {
	            public void actionPerformed(ActionEvent e) {
	                System.err.println("Preferences");
	            }
	        };
        activeFlags |= addAction(PREFERENCES, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_EXIT))  {
	            public void actionPerformed(ActionEvent e) {
            		ConsoleActionEvent cae = new 
				ConsoleActionEvent(this, ConsoleActionEvent.EXIT);
            		fireAdminEventDispatched(cae, ell);
	            }
	        };
        activeFlags |= addAction(EXIT, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_ABOUT)) {
	            public void actionPerformed(ActionEvent e) {
            		ConsoleActionEvent cae = new 
				ConsoleActionEvent(this, ConsoleActionEvent.ABOUT);
            		fireAdminEventDispatched(cae, ell);
	            }
	        };
        activeFlags |= addAction(ABOUT, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_PROPERTIES),
		    AGraphics.adminImages[AGraphics.PROPERTIES])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.PROPS_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(PROPERTIES, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_SHUTDOWN_BROKER),
			AGraphics.adminImages[AGraphics.SHUTDOWN])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.SHUTDOWN_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(SHUTDOWN, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_RESTART_BROKER),
			AGraphics.adminImages[AGraphics.RESTART])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.RESTART_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(RESTART, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_PAUSE),
			AGraphics.adminImages[AGraphics.PAUSE])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.PAUSE_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(PAUSE, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_RESUME),
			AGraphics.adminImages[AGraphics.RESUME])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.RESUME_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(RESUME, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_CONNECT), 
			AGraphics.adminImages[AGraphics.CONNECT_TO_OBJSTORE])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.CONNECT_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(CONNECT, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_DISCONNECT), 
			AGraphics.adminImages[AGraphics.DISCONNECT_FROM_OBJSTORE])  {
	            public void actionPerformed(ActionEvent e) {
            		DialogEvent de = new DialogEvent(this);
            		de.setDialogType(DialogEvent.DISCONNECT_DIALOG);
            		fireAdminEventDispatched(de, ell);
	            }
	        };
        activeFlags |= addAction(DISCONNECT, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_EXPAND_ALL),
			AGraphics.adminImages[AGraphics.EXPAND_ALL])  {
	            public void actionPerformed(ActionEvent e) {
            		ConsoleActionEvent cae = new 
				ConsoleActionEvent(this, ConsoleActionEvent.EXPAND_ALL);
            		fireAdminEventDispatched(cae, ell);
	            }
	        };
        activeFlags |= addAction(EXPAND_ALL, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_COLLAPSE_ALL),
			AGraphics.adminImages[AGraphics.COLLAPSE_ALL])  {
	            public void actionPerformed(ActionEvent e) {
            		ConsoleActionEvent cae = new 
				ConsoleActionEvent(this, ConsoleActionEvent.COLLAPSE_ALL);
            		fireAdminEventDispatched(cae, ell);
	            }
	        };
        activeFlags |= addAction(COLLAPSE_ALL, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_REFRESH),
		AGraphics.adminImages[AGraphics.REFRESH])  {
	            public void actionPerformed(ActionEvent e) {
            		ConsoleActionEvent cae = new 
				ConsoleActionEvent(this, ConsoleActionEvent.REFRESH);
            		fireAdminEventDispatched(cae, ell);
	            }
	        };
        activeFlags |= addAction(REFRESH, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_PURGE_BROKER_DEST),
		AGraphics.adminImages[AGraphics.PURGE])  {
                    public void actionPerformed(ActionEvent e) {
                        DialogEvent de = new DialogEvent(this);
                        de.setDialogType(DialogEvent.PURGE_DIALOG);
                        fireAdminEventDispatched(de, ell);
                    }
	        };
        activeFlags |= addAction(PURGE, tmpAction, table);

	tmpAction =
	    new AbstractAction(acr.getString(acr.I_QUERY_BROKER),
		AGraphics.adminImages[AGraphics.QUERY_BROKER])  {
                    public void actionPerformed(ActionEvent e) {
            		BrokerAdminEvent bae = new 
				BrokerAdminEvent(this, BrokerAdminEvent.QUERY_BROKER);
            		fireAdminEventDispatched(bae, ell);
                    }
	        };
        activeFlags |= addAction(QUERY_BROKER, tmpAction, table);

        return activeFlags;
    }

    private static int addAction(int actionFlag, Action a, Hashtable table)  {
	table.put(Integer.valueOf(actionFlag), a);
	return actionFlag;
    }

}
