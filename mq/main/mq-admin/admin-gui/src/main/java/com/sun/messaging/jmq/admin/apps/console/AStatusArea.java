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
 * @(#)AStatusArea.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JPanel;

/**
 * Admin Status Panel 
 */
public class AStatusArea extends JPanel {

    private JTextArea statusTextArea;

    /**
     * Create status bar for admin console application.
     */
    public AStatusArea() {
	super(true);
	setLayout(new BorderLayout());
	statusTextArea = new JTextArea(4, 60);
	statusTextArea.setLineWrap(true);
	statusTextArea.setEditable(false);
	JScrollPane statusTextPane = new JScrollPane(statusTextArea);
	add(statusTextPane,  BorderLayout.CENTER);
    }
    
    /**
     * Append status text to the text area.
     *
     * @param statusText the status text
     */
    public void appendText(String statusText) {
	statusTextArea.append(statusText);
	statusTextArea.setCaretPosition(statusTextArea.getText().length());
    }

    /*
     * Clears the text shown in the Status Area.
     */
    public void clearText()  {
	statusTextArea.setText("");
    }
}
