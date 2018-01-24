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
 * @(#)SplashScreenInspector.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JLabel;

/** 
 * Splash screen for JMQ Administration console.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class SplashScreenInspector extends InspectorPanel  {
    
    public JPanel createWorkPanel()  {
	JPanel panel = new JPanel();
	JLabel l = new JLabel(AGraphics.adminImages[AGraphics.SPLASH_SCREEN], JLabel.CENTER);

	panel.setLayout(new BorderLayout());
	panel.add(l, "Center");
	
	return (panel);
    }

    public void inspectorInit() {}
    public void clearSelection() {}
    public void selectedObjectUpdated() {}
}
