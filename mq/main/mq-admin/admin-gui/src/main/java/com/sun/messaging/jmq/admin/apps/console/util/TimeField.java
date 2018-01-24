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
 * @(#)TimeField.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console.util;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/**
 * This class implements a field used to enter some amount of
 * time. It supports the selection of the time unit:
 *
 * <UL>
 * <LI>Milliseconds
 * <LI>Seconds
 * <LI>Minutes
 * <LI>Hours
 * <LI>Days
 * </UL>
 * The default unit displayed is Seconds.
 * <P>
 *
 * The getValue() method returns the value entered in <STRONG>Milliseconds</STRONG>.
 * (because this class gives you the option of displaying the Milliseconds unit 
 * or not).
 */
public class TimeField extends JPanel {
    /*
     * Unit types. These are not indices
     */
    public final static int	MILLISECONDS	= 0;
    public final static int	SECONDS		= 1;
    public final static int	MINUTES		= 2;
    public final static int	HOURS		= 3;
    public final static int	DAYS		= 4;

    /*
     * Indices that indicate what string is displayed in the unit
     * combobox
     */
    private int		msecPos = 0,
			secPos = 1,
			minPos = 2,
			hrPos = 3,
			dayPos = 4;

    private boolean	showMillis = false;

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    private IntegerField	intF;
    private JComboBox	unitCB;

    public TimeField(long max, String text) {
	this(max, text, 0);
    }

    public TimeField(long max, int columns) {
	this(max, null, columns);
    }

    public TimeField(long max, String text, int columns) {
	this(max, text, columns, false);
    }

    public TimeField(long max, String text, int columns, boolean showMillis) {
        this.showMillis = showMillis;
	initGui(max, text, columns);
	setUnit(SECONDS);
    }

    public void addActionListener(ActionListener l)  {
	intF.addActionListener(l);
    }

    public void setText(String s)  {
	intF.setText(s);
    }

    public String getText()  {
	return (intF.getText());
    }

    public void setEnabled(boolean b)  {
	intF.setEnabled(b);
	unitCB.setEnabled(b);
    }

    /**
     * Set the byte unit.
     *
     * @param unit	byte unit. Can be a number from
     *			0 to 4 (inclusive) depending if Milliseconds
     *			is shown or not.
     */
    public void setUnit(int unit)  {
	int	index = secPos;

	switch (unit)  {
	case MILLISECONDS:
	    index = msecPos;
	break;

	case SECONDS:
	    index = secPos;
	break;

	case MINUTES:
	    index = minPos;
	break;

	case HOURS:
	    index = hrPos;
	break;

	case DAYS:
	    index = dayPos;
	break;
	}

	if ((index < 0) || (index > dayPos))  {
	    return;
	}

	unitCB.setSelectedIndex(index);
    }

    /**
     * Return the value in milliseconds.
     *
     * @return	The value entered in milliseconds.
     */
    public long getValue()  {
	String  s;
	int	selIndex;
	long	tmpLong;

	s = intF.getText();

	try  {
	    tmpLong = Long.parseLong(s);
	} catch (Exception e)  {
	    return (-1);
	}

	selIndex = unitCB.getSelectedIndex();

	if (showMillis)  {
	    if (selIndex == msecPos)  {
	        return (tmpLong);
	    } else if (selIndex == secPos)  {
	        return (tmpLong * 1000);
	    } else if (selIndex == minPos)  {
	        return (tmpLong * 1000 * 60);
	    } else if (selIndex == hrPos)  {
	        return (tmpLong * 1000 * 60 * 60);
	    } else if (selIndex == dayPos)  {
	        return (tmpLong * 1000 * 60 * 60 * 24);
	    }
	} else  {
	    if (selIndex == secPos)  {
	        return (tmpLong * 1000);
	    } else if (selIndex == minPos)  {
	        return (tmpLong * 1000 * 60);
	    } else if (selIndex == hrPos)  {
	        return (tmpLong * 1000 * 60 * 60);
	    } else if (selIndex == dayPos)  {
	        return (tmpLong * 1000 * 60 * 60 * 24);
	    }
	}

	return (-1);
    }

    private void initGui(long max, String text, int collumns)  {
        String[] units;

	intF = new IntegerField(0, max, text, collumns);

	if (showMillis)  {
    	    msecPos = 0;
	    secPos = 1;
	    minPos = 2;
	    hrPos = 3;
	    dayPos = 4;

	    units = new String[ 5 ];

	    units[msecPos] = acr.getString(acr.I_MILLISECONDS);

	} else  {
	    secPos = 0;
	    minPos = 1;
	    hrPos = 2;
	    dayPos = 3;

	    units = new String[ 4 ];
	}

	units[secPos] = acr.getString(acr.I_SECONDS);
	units[minPos] = acr.getString(acr.I_MINUTES);
	units[hrPos] = acr.getString(acr.I_HOURS);
	units[dayPos] = acr.getString(acr.I_DAYS);

	unitCB = new JComboBox(units);
	setLayout(new BorderLayout());

	add(intF, "Center");
	add(unitCB, "East");
    }

}

