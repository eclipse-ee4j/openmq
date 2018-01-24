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
 * @(#)LabelledComponent.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.util;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * This class implements a panel that contains a 
 * JLabel and a component horizontally separated by a colon. 
 *       Label: Component
 * It can be in conjunction with LabelValuePanel which aligns
 * a set fo LabelledComponents such that the colons are lined up.
 *       Label1: Component1
 *       Label2: Component2
 *   LongLabel3: LongComponent3
 *
 * A secondary label can also be specified. This label is placed after
 * (to the right) of the component:
 *	label1: Component label2
 * For example:
 *	Time:	[textfield] seconds
 *
 * <P>
 * This class contains methods to get the width of the primary label 
 * and the component, but not the secondary label.
 *
 * <P>
 * THis class also allows for the alignment of the label with respect to
 * the component, to be specified.
 *
 */
public class LabelledComponent extends JPanel  {

    public static final int	NORTH = 0;
    public static final int	CENTER = 1;
    public static final int	SOUTH = 2;

    private JLabel	label;
    private JLabel	label2;
    private JComponent  component;
    private JPanel	panel;
    private int		align = CENTER;
    private Object      userData = null;

    public LabelledComponent(String s, JComponent c)  {
	this(s, c, CENTER);
    }

    public LabelledComponent(String s, JComponent c, int align)  {
	this(new JLabel(s, JLabel.RIGHT), c, null, align);
    }

    public LabelledComponent(String s, JComponent c, String s2)  {
	this(s, c, s2, CENTER);
    }

    public LabelledComponent(String s, JComponent c, String s2, int align)  {
	this(new JLabel(s, JLabel.RIGHT), c, new JLabel(s2, JLabel.RIGHT), align);
    }

    /*
     * Don't really need this
    public LabelledComponent(JLabel l, JComponent c)  {
	this(l, c, null);
    }
    */

    public LabelledComponent(JLabel l, JComponent c, JLabel l2, int align)  {
	label = l;
	label2 = l2;
	component = c;
	this.align = align;
	initPanel();
    }

    public JPanel getLabelledComponent()  {
	return panel;
    }

    public JLabel getLabel()  {
	return label;
    }

    public JComponent getComponent()  {
	return component;
    }

    public int getLabelWidth()  {
	return label.getPreferredSize().width;
    }

    public int getComponentWidth()  {
	return component.getPreferredSize().width;
    }

    public void setClientData(Object userData)  {
	this.userData = userData;
    }

    public Object getClientData()  {
	return this.userData;
    }

    public void setLabelText(String s)  {
	if ((label == null) || (s == null))
	    return;
	
	label.setText(s);
    }

    public void setLabelFont(Font f)  {
	if ((label == null) || (f == null))
	    return;
	
	label.setFont(f);
    }

    public void setEnabled(boolean b)  {
        if (label != null)  {
	    label.setEnabled(b);
	}
        if (label2 != null)  {
	    label2.setEnabled(b);
	}
        if (component != null)  {
	    enableComponents(component, b);
	}
    }

    /*
     * Enable/disable all the components within the comp component.
     */
    private void enableComponents(JComponent comp, boolean b) {
        for (int i = 0; i < comp.getComponentCount(); i++)
            comp.getComponent(i).setEnabled(b);
    }

    private void initPanel()  {

	GridBagLayout gridbag = new GridBagLayout();
	setLayout(gridbag);
	GridBagConstraints gbc = new GridBagConstraints();
	int	labelAnchor;

        /*
    	 * Put the label on the left side.
  	 * against the right of the right side of the 2x1 grid.
	 */
	label.setHorizontalAlignment(JLabel.RIGHT);
	gbc.gridx = 0;
	gbc.gridy = 0;

	switch (align)  {
	case NORTH:
	    labelAnchor = GridBagConstraints.NORTHEAST;
	break;

	case CENTER:
	    labelAnchor = GridBagConstraints.CENTER;
	break;

	case SOUTH:
	    labelAnchor = GridBagConstraints.SOUTHEAST;
	break;

	default:
	    labelAnchor = GridBagConstraints.CENTER;
	break;
	}

	gbc.anchor = labelAnchor;

	/*
	if (c instanceof JScrollPane || c instanceof JPanel)
	    gbc.anchor = GridBagConstraints.NORTHEAST;
	*/

	gridbag.setConstraints(label, gbc);

        /*
    	 * Put the value component on the right side.
  	 * against the left side of the 2x1 grid.
	 * Move it over 5 pixels so that there is a space
	 * after the label.
	 */
	gbc.gridx = 1;
	gbc.gridy = 0;
	gbc.anchor = GridBagConstraints.CENTER;
	gbc.insets = new Insets(0, 5, 0, 0);  // value is 5 pixels to the right
	gbc.weightx = 1.0;
	gbc.fill = GridBagConstraints.HORIZONTAL;
	gridbag.setConstraints(component, gbc);

	add(label);
	add(component);

	if (label2 != null)  {
            /*
    	     * Put the right label component on the right of the value.
	     * Move it over 5 pixels so that there is a space
	     * between the value and the right label.
	     */
	    gbc.gridx = 2;
	    gbc.gridy = 0;
	    gbc.insets = new Insets(0, 5, 0, 0);  // value is 5 pixels to the right
	    gbc.anchor = labelAnchor;
	    gbc.weightx = 0;
	    gridbag.setConstraints(label2, gbc);

	    add(label2);
	}

    }
}
