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
 * @(#)LabelValuePanel.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class LabelValuePanel extends JPanel {

    private int 		vgap = 5;
    private int 		hgap = 5;
    private JPanel 		panel;
    private LabelledComponent 	items[];

    public LabelValuePanel(LabelledComponent items[])  {
        this.items = items;
	
	init();
    }

    public LabelValuePanel(LabelledComponent items[], int hgap, int vgap)  {
        this.items = items;
  	this.hgap = hgap;
  	this.vgap = vgap;
	
	init();
    }


    public LabelledComponent[] getLabelledComponents() {
	return this.items;
    }

    private void init() {

	int numItems = items.length;
	int longest = 0;

	setBorder(BorderFactory.createEmptyBorder(vgap, hgap, vgap, hgap));
	/*
	 * Find the longest label while adding the 
	 * LabelledComponents to the panel.
	 */
	GridBagLayout gridbag = new GridBagLayout();
	setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();

	for (int i = 0; i < numItems; i++) {
	    if (items[i].getLabelWidth() > longest) {
		longest = items[i].getLabelWidth();
	    }
	    c.gridx = 0;
	    c.gridy = i;
	    c.ipadx = hgap;
	    c.ipady = vgap;
	    c.anchor = GridBagConstraints.WEST;
	    c.weightx = 1.0;
	    c.fill = GridBagConstraints.HORIZONTAL;
	    gridbag.setConstraints(items[i], c);
	    add(items[i]);
	}

	/*
	 * Set the label width to the longest label.
	 * so that they are aligned equally.
	 */
	for (int i = 0; i < items.length; i++) {
	    JLabel l = items[i].getLabel();
	    Dimension dim = l.getPreferredSize();
	    dim.width = longest;
	    l.setPreferredSize(dim);
	}

    }
}
