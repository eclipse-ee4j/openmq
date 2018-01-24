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
 * @(#)AdminApp.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.Frame;

import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;

/**
 * This class defines the interface to the admin GUI application.
 * It represents the central point from which the major GUI 
 * pieces of the admin GUI application and other information
 * can be obtained.
 * <P>
 * A class implementing this interface can be used to run or
 * control the admin application. For the admin console
 * application, this controller can do things like:
 *
 * <UL>
 * <LI>show some status text in the status pane
 * <LI>show some object as being selected in the select pane
 * <LI>tell the canvas to select or deselect something
 * <LI>disable/enable some buttons in the control panel
 * </UL>
 *
 * Currently, the admin console is a main application. This is done
 * via the AdminConsole class which extends AdminApp.
 * <P>
 * Later, if we decide to create an applet version of the console,
 * we would create an applet class also extend AdminApp.
 */
public interface AdminApp  {

    /**
     * Returns the application frame.
     *
     * @return The application frame.
     */
    public Frame		getFrame();

    /**
     * Returns the menubar.
     *
     * @return The menubar.
     */
    public AMenuBar		getMenubar();

    /**
     * Returns the toolbar.
     *
     * @return The toolbar.
     */
    public AToolBar		getToolbar();

    /**
     * Returns the explorer pane. This is the pane that
     * contains the tree.
     *
     * @return The explorer pane.
     */
    public AExplorer		getExplorer();

    /**
     * Returns the inspector pane. This is the pane
     * that shows the attributes of what is currently
     * selected.
     *
     * @return The inspector pane.
     */
    public AInspector		getInspector();

    /**
     * Returns the status area pane.
     *
     * @return The status area pane.
     */
    public AStatusArea		getStatusArea();

    /**
     * Returns the action manager.
     *
     * @return The action manager.
     */
    public ActionManager	getActionManager();

    /**
     * Returns the top level object store list object.
     *
     * @return The top level object store list object.
     */
    public ObjStoreListCObj	getObjStoreListCObj();

    /**
     * Returns the top level broker list object.
     *
     * @return The top level broker list object.
     */
    public BrokerListCObj	getBrokerListCObj();


    /**
     * Sets the selected object in the application.
     */
    public void			setSelectedObj(ConsoleObj obj);

    /**
     * Returns the selected object.
     *
     * @return The selected object.
     */
    public ConsoleObj		getSelectedObj();

    /**
     * Sets the selected objects in the application.
     * <P>
     * Currently, the application only supports single selection
     * so this is not impemented.
     */
    public void			setSelectedObjs(ConsoleObj obj[]);

    /**
     * Returns the selected objects.
     * <P>
     * Currently, the application only supports single selection
     * so this is not impemented.
     *
     * @return the selected objects.
     */
    public ConsoleObj[]		getSelectedObjs();
}
