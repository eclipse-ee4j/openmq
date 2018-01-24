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
 * @(#)ObjMgrPrinter.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.util.MultiColumnPrinter;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;

public class ObjMgrPrinter extends MultiColumnPrinter {

    private static AdminResources ar = Globals.getAdminResources();

    public ObjMgrPrinter(int numCol, int gap, String border, int align, boolean sort) {
	super(numCol, gap, border, align, sort);
    }

    public ObjMgrPrinter(int numCol, int gap, String border, int align) {
	super(numCol, gap, border, align);
    }

    public ObjMgrPrinter(int numCol, int gap, String border) {
	super(numCol, gap, border);
    }

    public ObjMgrPrinter(int numCol, int gap) {
	super(numCol, gap);
    }

    public ObjMgrPrinter(Hashtable h, int numCol, int gap) {
	super(numCol, gap);

	String[] row = new String[2];

        for (Enumeration e = h.keys();  e.hasMoreElements();) {
            String propName = (String)e.nextElement(),
                        propValue = (String)h.get(propName);

	    row[0] = propName;
	    row[1] = propValue;
            add(row);
        }
    }

    public void printJMSObject(Object obj) {

        if (obj instanceof com.sun.messaging.Topic) {
            Globals.stdOutPrintln(ar.getString(ar.I_TOPIC_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.Queue) {
            Globals.stdOutPrintln(ar.getString(ar.I_QUEUE_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.XATopicConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_XATOPIC_CF_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.XAQueueConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_XAQUEUE_CF_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.XAConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_XA_CF_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.TopicConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_TOPIC_CF_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.QueueConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_QUEUE_CF_ATTRS_HDR));
        }
        else if (obj instanceof com.sun.messaging.ConnectionFactory) {
            Globals.stdOutPrintln(ar.getString(ar.I_CF_ATTRS_HDR));
	}

        if (obj instanceof AdministeredObject)
            printObjPropertiesFromObj((AdministeredObject)obj);
    }

    /**
     * Prints the properties of the administered object in a nice
     * formatted 2 collumn table. The property names/values, as well
     * as property name labels are obtained from the passed object.
     */
    public void printObjPropertiesFromObj(AdministeredObject obj) {
        /*
         * Set the specified properties on the new object.
         */
        Properties props = obj.getConfiguration();
        for (Enumeration e = obj.enumeratePropertyNames(); e.hasMoreElements();) {

            String propName = (String)e.nextElement();

	    /*
	     * If an exception is caught while checking if a property is hidden
	     * the property will be displayed.
	     */
	    try  {
	        if (obj.isPropertyHidden(propName))  {
		    continue;
	        }
	    } catch(Exception ex)  {
	    }

            String value = props.getProperty(propName);
            String propLabel = "";

	    /*
	     * If an exception is caught while getting the property label,
	     * "" will be used instead as the label.
	     */
            try  {
                propLabel = obj.getPropertyLabel(propName);
            } catch (Exception ex)  {
            }

            String printLabel = propName + " [" + propLabel + "]";
            String printValue = value;

	    String[] row = new String[2];
            row[0] = printLabel;
            row[1] = printValue;
            add(row);
        }
	print();
    }

    /**
     * Prints the property names and values in a nice formatted 2
     * collumn table. The property names will also contain a
     * description/label if found by querying the passed administered
     * object.
     *
     * In this method, the administered object serves only as a way
     * to get the property name label. The property values will come
     * from the properties object. 
     *
     * This is used mostly for printing the printing the properties
     * modified by the 'update' operation.
     */
    public void printObjPropertiesFromProp(Properties p,
                                        AdministeredObject obj)  {

        for (Enumeration e = p.propertyNames();  e.hasMoreElements();)  {
            String propName = (String)e.nextElement(),
                value = p.getProperty(propName),
                propLabel, printLabel;

            try  {
                propLabel = obj.getPropertyLabel(propName);
            } catch (Exception ex)  {
                propLabel = "";
            }

            printLabel = propName + " [" + propLabel + "]";

            String[] row = new String[2];
            row[0] = printLabel;
            row[1] = value;
            add(row);
        }
	print();
    }

    public static void printReadOnly(String value)  {
        if (value != null)
            Globals.stdOutPrintln(ar.getString(ar.I_READONLY, value));
        else
            Globals.stdOutPrintln(ar.getString(ar.I_READONLY,
                                  Boolean.FALSE.toString()));
    }

    public static void printReadOnly(boolean value)  {
        if (value)
            Globals.stdOutPrintln(ar.getString(ar.I_READONLY,
                                  Boolean.TRUE.toString()));
        else
            Globals.stdOutPrintln(ar.getString(ar.I_READONLY,
                                  Boolean.FALSE.toString()));
    }

    public void doPrint(String str) {
        Globals.stdOutPrint(str);
    }

    public void doPrintln(String str) {
        Globals.stdOutPrintln(str);
    }
}
