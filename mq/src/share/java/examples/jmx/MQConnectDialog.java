/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MQConnectDialog extends JDialog 
		implements ActionListener {
    JButton apply, cancel;
    JTextField address, username;
    JPasswordField password;

    private boolean applyHit = false;
    private ActionListener applyListener = null;

    public MQConnectDialog(Frame parent, String title, 
			ActionListener applyListener)  {
	super(parent, title, true);
	this.applyListener = applyListener;
	initContentPane();
	pack();
    }

    public boolean applyDone()  {
	return (applyHit);
    }

    private void initContentPane()  {
	JPanel panel = new JPanel();

	panel.setLayout(new BorderLayout());
	/*
	 * Create 'work' panel
	 */
	JPanel workPanel = createWorkPanel();

	/*
	 * Create button panel
	 */
	JPanel buttonPanel = createButtonPanel();

	panel.add(workPanel, "Center");
	panel.add(buttonPanel, "South");

	getContentPane().add(panel);
    }

    private JPanel createWorkPanel()  {
	JPanel workPanel = new JPanel();
	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	JLabel l;

	workPanel.setLayout(gridbag);

	c.anchor = GridBagConstraints.WEST;
	c.fill = GridBagConstraints.NONE;
	c.insets = new Insets(2, 2, 2, 2);
	c.ipadx = 0;
	c.ipady = 0;
	c.weightx = 1.0;

	c.gridx = 0;
	c.gridy = 0;
	l = new JLabel("Address:");
	gridbag.setConstraints(l,c);
	workPanel.add(l);

	c.gridx = 1;
	c.gridy = 0;
	address = new JTextField(20);
	gridbag.setConstraints(address,c);
	workPanel.add(address);

	c.gridx = 0;
	c.gridy = 1;
	l = new JLabel("Name:");
	gridbag.setConstraints(l,c);
	workPanel.add(l);

	c.gridx = 1;
	c.gridy = 1;
	username = new JTextField(20);
	gridbag.setConstraints(username, c);
	workPanel.add(username);

	c.gridx = 0;
	c.gridy = 2;
	l = new JLabel("Password:");
	gridbag.setConstraints(l,c);
	workPanel.add(l);

	c.gridx = 1;
	c.gridy = 2;
	password = new JPasswordField(20);
	gridbag.setConstraints(password, c);
	workPanel.add(password);

	return (workPanel);
    }

    public void setAddress(String s)  {
	address.setText(s);
    }
    public String getAddress()  {
	return (address.getText());
    }

    public void setUserName(String s)  {
	username.setText(s);
    }
    public String getUserName()  {
	return (username.getText());
    }

    public void setPassword(String s)  {
	password.setText(s);
    }
    public String getPassword()  {
	return (new String(password.getPassword()));
    }

    private JPanel createButtonPanel()  {
	JPanel buttonPanel = new JPanel();

	buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

	apply = new JButton("Apply");
	apply.addActionListener(this);
	if (applyListener != null)  {
	    apply.addActionListener(applyListener);
	}
	buttonPanel.add(apply);

	cancel = new JButton("Cancel");
	cancel.addActionListener(this);
	buttonPanel.add(cancel);

	return (buttonPanel);
    }

    public void actionPerformed(ActionEvent e)  {
	Object src = e.getSource();

	if (src == apply)  {
	    applyHit = true;
	    setVisible(false);
	} else if (src == cancel)  {
	    applyHit = false;
	    setVisible(false);
	}
    }
}
