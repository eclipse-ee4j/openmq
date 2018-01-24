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
 * @(#)AboutDialog.java	1.17 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.io.File;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

import com.sun.messaging.jmq.admin.apps.console.util.LabelledComponent;
import com.sun.messaging.jmq.admin.apps.console.util.LabelValuePanel;

/** 
 * This dialog is used for object store attributes.
 * It can be used to Add an object store to the list
 * or to modify (update) an existing object store.
 *
 */
public class AboutDialog extends AdminDialog {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Creates a non-modal dialog using the specified frame as parent and string
     * as title. By default, will contain the following buttons:
     * <UL>
     * <LI>OK
     * <LI>CANCEL
     * <LI>HELP
     * </UL>
     *
     * @param parent the Frame from which the dialog is displayed
     * @param title the String to display in the dialog's title bar
     */
    public AboutDialog(Frame parent)  {
	super(parent, "", CLOSE);
	setTitle(acr.getString(acr.I_ABOUT));
    }

    public JPanel createWorkPanel()  {

	JPanel leftPanel, rightPanel;

	JPanel workPanel = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	workPanel.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	
	leftPanel = makeLeftPanel();
	c.gridx = 0;
	c.gridy = 0;
	c.anchor = GridBagConstraints.NORTHWEST;
	gridbag.setConstraints(leftPanel, c);
	workPanel.add(leftPanel);

	rightPanel = makeRightPanel();
	c.gridx = 1;
	c.gridy = 0;
	c.anchor = GridBagConstraints.NORTHWEST;
	c.ipadx = 20;
	gridbag.setConstraints(rightPanel, c);
	workPanel.add(rightPanel);

	return (workPanel);
    }

    private JPanel makeLeftPanel() {

	JPanel leftPanel = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	leftPanel.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	
 	JLabel label1 = new JLabel(AGraphics.adminImages[AGraphics.ABOUT_BOX]);
	c.gridx = 0;
	c.gridy = 0;
	gridbag.setConstraints(label1, c);
	leftPanel.add(label1);
	
	return leftPanel;
    }

    private JPanel makeRightPanel() {
	Version			version;
	JPanel			rightPanel;
	GridBagLayout		gridbag;
	GridBagConstraints	c;
	JLabel			label;
	JSeparator		sep;
	LabelValuePanel		lvp;
	LabelledComponent	lvpItems[];
	LabelledComponent	tmpLabelC;
	JTextArea		ta;
	JScrollPane	 	sp;
	int			gridy = 0,
				width;

	version = new Version(false);
	rightPanel = new JPanel();
	gridbag = new GridBagLayout();
	rightPanel.setLayout(gridbag);
	c = new GridBagConstraints();

	label = new JLabel(version.getProductName() + " " + version.getReleaseQID());
	c.gridx = 0;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	label = new JLabel(acr.getString(acr.I_VERSION, version.getBuildVersion()));
	c.gridx = 0;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	label = new JLabel(acr.getString(acr.I_COMPILE, version.getBuildDate()));
	c.gridx = 0;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	sep = new JSeparator();
	c.gridx = 0;
	c.gridy = gridy++;
	c.fill = GridBagConstraints.HORIZONTAL;
	c.insets = new Insets(5, 0, 5, 0);
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(sep, c);
	rightPanel.add(sep);

	/*
	 * Reset
	 */
	c.fill = GridBagConstraints.NONE;
	c.insets = new Insets(0, 0, 0, 0);

	label = new JLabel(acr.getString(acr.I_VERSION_INFO, 
				version.getVersionPackageName()));
	c.gridx = 0;
	gridy++;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	/*
	 * Reset
	 */
	c.insets = new Insets(0, 0, 0, 0);

	String[] patchids = version.getPatchIds();
	if (patchids == null || patchids.length < 1)
	    lvpItems = new LabelledComponent[3];
	else
	    lvpItems = new LabelledComponent[4];

	tmpLabelC = new LabelledComponent(acr.getString(acr.I_IMPLEMENTATION),
				new JLabel(version.getImplementationVersion()));
	lvpItems[0] = tmpLabelC;

	tmpLabelC = new LabelledComponent(acr.getString(acr.I_PROTOCOL_VERSION),
				new JLabel(version.getProtocolVersion()));
	lvpItems[1] = tmpLabelC;

	tmpLabelC = new LabelledComponent(acr.getString(acr.I_TARGET_JMS_VERSION),
				new JLabel(version.getTargetJMSVersion()));
	lvpItems[2] = tmpLabelC;

	// Append a PatchID Item and list of patchids only if they exist.
	// The Patchids are listed in a panel on the right.
	if (patchids != null && patchids.length >= 1) {
	    JPanel patchPanel = new JPanel();
	    GridBagLayout gb = new GridBagLayout();
	    patchPanel.setLayout(gb);

	    GridBagConstraints c2 = new GridBagConstraints();
	    for (int p = 0; p < patchids.length; p++) {
                c2.gridx = 0;
                c2.gridy = p;
                c2.ipadx = 0;
                c2.ipady = 0;
                c2.anchor = GridBagConstraints.WEST;
            	JLabel patchLabel = new JLabel(patchids[p]);
                gb.setConstraints(patchLabel, c2);
                patchPanel.add(patchLabel);
	    }
 
	    tmpLabelC = new LabelledComponent(acr.getString(acr.I_PATCHES), patchPanel,
					      LabelledComponent.NORTH);
	    lvpItems[3] = tmpLabelC;
	}
						
	lvp = new LabelValuePanel(lvpItems, 4, 0);
	c.gridx = 0;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(lvp, c);
	rightPanel.add(lvp);

	label = new JLabel(acr.getString(acr.I_JAVA_VERSION)
		     		+ System.getProperty("java.version") + " " 
				+ System.getProperty("java.vendor") + " " 
				+ System.getProperty("java.home"));
	c.gridx = 0;
	c.gridy = gridy++;
	c.insets = new Insets(5, 0, 0, 0);
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	String	classpathStr = System.getProperty("java.class.path");
	classpathStr = classpathStr.replace(File.pathSeparatorChar, '\n');
	ta = new JTextArea(classpathStr);
	ta.setEditable(false);
	ta.setLineWrap(true);
	ta.setWrapStyleWord(true);
	Color bgColor = rightPanel.getBackground();
	ta.setBackground(bgColor);
	Color fgColor = label.getForeground();
	ta.setForeground(fgColor);
	ta.setFont(label.getFont());
	width = label.getPreferredSize().width;
	ta.setSize(width, 1);
	tmpLabelC = new LabelledComponent(acr.getString(acr.I_JAVA_CLASSPATH),
				ta, LabelledComponent.NORTH);
	c.gridx = 0;
	c.gridy = gridy++;
	c.insets = new Insets(0, 0, 0, 0);
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(tmpLabelC, c);
	rightPanel.add(tmpLabelC);

	sep = new JSeparator();
	c.gridx = 0;
	c.gridy = gridy++;
	c.fill = GridBagConstraints.HORIZONTAL;
	c.insets = new Insets(5, 0, 5, 0);
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(sep, c);
	rightPanel.add(sep);

	/*
	 * Reset
	 */
	c.fill = GridBagConstraints.NONE;
	c.insets = new Insets(0, 0, 0, 0);

	ta = new JTextArea(version.getCopyright(Version.SHORT_COPYRIGHT), 8, 0);
	ta.setEditable(false);
	ta.setLineWrap(true);
	ta.setWrapStyleWord(true);
	bgColor = rightPanel.getBackground();
	ta.setBackground(bgColor);
	fgColor = label.getForeground();
	ta.setForeground(fgColor);
	ta.setFont(label.getFont());
	//width = label.getPreferredSize().width;
	width = rightPanel.getPreferredSize().width;
	ta.setSize(width, 1);

	sp = new JScrollPane(ta);
	c.gridx = 0;
	c.gridy = gridy++;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(sp, c);
	rightPanel.add(sp);

	label = new JLabel(acr.getString(acr.I_RSA_CREDIT));
	c.gridx = 0;
	c.gridy = gridy++;
	c.ipady = 10;
	c.anchor = GridBagConstraints.WEST;
	gridbag.setConstraints(label, c);
	rightPanel.add(label);

	return rightPanel;
    }

    public void show()  { 
	setDefaultButton(CLOSE);
	super.show();
    }

    public void doOK()  { }
    public void doApply()  { }
    public void doReset() { }
    public void doCancel() { }
    public void doClose() { hide(); }
    public void doClear() { }

}
