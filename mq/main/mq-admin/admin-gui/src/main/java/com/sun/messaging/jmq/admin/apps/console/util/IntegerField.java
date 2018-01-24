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
 * @(#)IntegerField.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.util;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

public class IntegerField extends JTextField {
    
    //*****************************************************************
    // Constructors
    
    public IntegerField(long min, long max, String text) {
	this(min, max, text, 0);
    }

    public IntegerField(long min, long max, int columns) {
	this(min, max, null, columns);
    }

    public IntegerField(long min, long max, 
			String text, int columns) {
	super(new IntegerDocument(min, max), text, columns);
    }
}

//*************************************************************************
// IntegerDocument class

class IntegerDocument extends PlainDocument {
    long min;
    long max;

    //*********************************************************************
    // Constructors
    
    public IntegerDocument(long min, long max) {
	this.min = min;
	this.max = max;
    }
    
    //*********************************************************************
    // Validation routines

    public void insertString(int offset, String str, AttributeSet a)
	 throws BadLocationException 
    {
	// Validate each char in str checking if in '0' .. '9'.
	// If the min value is < 0, then allow a '-' only in the
	// first position.

	for (int i=0; i<str.length(); i++) {
	    int keyCode = (int)str.charAt(i);
	    if (keyCode < KeyEvent.VK_0 || keyCode > KeyEvent.VK_9) {
		// keyCode 45 is the '-' char.
		if (!(min < 0 && offset == 0 && keyCode == 45)) {
		    Toolkit.getDefaultToolkit().beep();
		    return;
		}
	    }
	}
	
	// Validate the entire string in text field making
	// sure it's within range.

	String sval = getText(0, getLength());
	sval = sval.substring(0, offset) + str +
	    sval.substring(offset, sval.length());
	// Max digits for a number to fit in a type long.
	// And also make sure two '-'s weren't entered.
	if (sval.length() > 18 || sval.startsWith("--")) {
	    Toolkit.getDefaultToolkit().beep();
	    return;
	} else if (!sval.equals("-") && sval.length() > 0) {
	    // Evaluate only if it's not a single '-' char.
	    long ival = Long.parseLong(sval);
	    if (ival < min || ival > max) {
		Toolkit.getDefaultToolkit().beep();
		return;
	    }
	}
	
	// Accept the input.
	super.insertString(offset, str, a);
    }
}







