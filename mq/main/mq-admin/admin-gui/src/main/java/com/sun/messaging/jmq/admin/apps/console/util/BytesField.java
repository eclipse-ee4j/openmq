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
 * @(#)BytesField.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.util;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import javax.swing.JComboBox;

import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/**
 * This class implements a field used to enter some amount of
 * bytes. It supports the selection of the byte unit:
 *
 * <UL>
 * <LI>MegaBytes
 * <LI>KiloBytes
 * <LI>Bytes
 * </UL>
 * The default unit displayed is KILOBYTES.
 * <P>
 *
 * The getValue() method returns the value entered in <STRONG>BYTES</STRONG>.
 */
public class BytesField extends JPanel {
    public final static int		BYTES		= 0;
    public final static int		KILOBYTES	= 1;
    public final static int		MEGABYTES	= 2;

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    private LongField	lf;
    private JComboBox	unitCB;

    public BytesField(long min, long max, String text) {
	this(min, max, text, 0);
    }

    public BytesField(long min, long max, int columns) {
	this(min, max, null, columns);
    }

    public BytesField(long min, long max, 
			String text, int columns) {
	initGui(min, max, text, columns);
	setUnit(KILOBYTES);
    }

    public void addActionListener(ActionListener l)  {
	lf.addActionListener(l);
    }

    public void setText(String s)  {
	lf.setText(s);
    }

    public String getText()  {
	return (lf.getText());
    }

    public void setEnabled(boolean b)  {
	lf.setEnabled(b);
	unitCB.setEnabled(b);
    }

    /**
     * Set the byte unit.
     *
     * @param unit	byte unit. Can be one of MEGABYTES,
     *			KILOBYTES, or BYTES
     */
    public void setUnit(int unit)  {
	if ((unit < 0) || (unit > MEGABYTES))  {
	    return;
	}

	unitCB.setSelectedIndex(unit);
    }

    /*
     * Returns the unit of the value i.e. BYTES, KILOBYTES, or
     * MEGABYTES.
     *
     * @return	The unit of the value.
     */
    public int getUnit()  {
	int selIndex = unitCB.getSelectedIndex();

	return (selIndex);
    }


    /**
     * Sets the bytes string using the format
     * recognized by the SizeString class.
     */
    public void setSizeString(String strVal)  {
	SizeString	ss;
	String		tmp = strVal.trim();
	long		val;
	int		unit;
	char		c;

	try  {
	    ss = new SizeString(tmp);
	} catch (Exception e)  {
	    /*
	     * Should not get here
	     */
	    return;
	}

	c = tmp.charAt(tmp.length() -1);

	if (Character.isLetter(c)) {
	    switch (c)  {
	    case 'm':
	    case 'M':
		val = ss.getMBytes();
	        unit = BytesField.MEGABYTES;
	    break;

	    case 'k':
	    case 'K':
		val = ss.getKBytes();
	        unit = BytesField.KILOBYTES;
	    break;

	    case 'b':
	    case 'B':
		val = ss.getBytes();
	        unit = BytesField.BYTES;
	    break;

	    default:
		val = 0;
	        unit = BytesField.BYTES;
	    break;
	    }
	} else  {
	    val = ss.getBytes();
	    unit = BytesField.BYTES;
	}

	setText(Long.toString(val));
	setUnit(unit);
    }

    /**
     * Returns the bytes string in the format recognized by
     * the SizeString class.
     */
    public String getSizeString()  {
	String	strValue = getText();
	int	unit = getUnit();
	String	unitStr;

	switch (unit)  {
	case BytesField.BYTES:
	    unitStr = "b";
	break;

	case BytesField.KILOBYTES:
	    unitStr = "k";
	break;

	case BytesField.MEGABYTES:
	    unitStr = "m";
	break;

	default:
	    unitStr = "b";
	break;
	}

	return (strValue + unitStr);
    }


    /**
     * Return the value in bytes.
     *
     * @return	The value entered in bytes.
     */
    public long getValue()  {
	String  s;
	int	selIndex;
	long	tmpLong;

	s = lf.getText();

	try  {
	    tmpLong = Long.parseLong(s);
	} catch (Exception e)  {
	    return (-1);
	}

	selIndex = unitCB.getSelectedIndex();

	switch (selIndex)  {
	case MEGABYTES:
	    return (tmpLong * 1048576);

	case KILOBYTES:
	    return (tmpLong * 1024);

	case BYTES:
	    return (tmpLong);

	default:
	    return (-1);
	}
    }

    private void initGui(long min, long max, String text, int collumns)  {
        String[] units;

	lf = new LongField(min, max, text, collumns);

	units = new String[ 3 ];
	units[MEGABYTES] = acr.getString(acr.I_MEGABYTES);
	units[KILOBYTES] = acr.getString(acr.I_KILOBYTES);
	units[BYTES] = acr.getString(acr.I_BYTES);

	unitCB = new JComboBox(units);
	setLayout(new BorderLayout());

	add(lf, "Center");
	add(unitCB, "East");
    }

}

