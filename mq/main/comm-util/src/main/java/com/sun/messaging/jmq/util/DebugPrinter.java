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
 * @(#)DebugPrinter.java	1.9 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;

/**
 * Class used to print debug information received from the MQ broker.
 * It subclasses MultiColumnPrinter and extends the printObject()
 * to handle the case where a Hashtable is the value to be printed.
 *
 * Output is sent to stdout but can also be redirected to a file.
 */
public class DebugPrinter extends MultiColumnPrinter  {
    private Hashtable		hashtable = null;
    private Vector		vector = null;
    private String		filename = null;
    private File		f;
    private FileOutputStream	fos;
    private PrintStream		ps = System.out;
    private StringBuffer buffer = new StringBuffer(1024);
    private static String nl = System.getProperty("line.separator");

    /**
     *
     */
    public DebugPrinter(int gap) {
    this((Hashtable)null, gap);
    }

    public DebugPrinter(Hashtable hashtable, int gap) {
    this(hashtable, gap, null);
    }

    public DebugPrinter(Vector v, int gap) {
    this(v, gap, null);
    }

    public DebugPrinter(Vector v, int gap, String filename)
    {
    super(2, gap, "-");
    this.filename = filename;
    this.vector = v;

    initOutput();
    setupTitle();
    setupData();
    }

    public DebugPrinter(Hashtable hashtable, int gap, String filename) {
    super(2, gap, "-");
    this.filename = filename;
    this.hashtable = hashtable;

    initOutput();
    setupTitle();
    setupData();
    }

    public void setHashtable(Hashtable h)  {
    this.hashtable = h;
    clear();
    setupData();
    }

    public void setFile(String filename)  {
    this.filename = filename;

    closeOutput();
    initOutput();
    }

    public void doPrint(String str)  {
        if (ps == System.out) {
           buffer.append(str);
           return;
        }
        ps.print(str);
    }

    public void doPrintln(String str)  {
        if (ps == System.out) {
           buffer.append(str);
           buffer.append(nl);
           ps.print(buffer.toString());
           buffer.setLength(0);
           return;
        }
        ps.println(str);
    }

    /**
     * Print object.
     *
     * @param obj Object to print.
     * @param indent indentation of object to be printed.
     *		This is used if the output spans multiple lines.
     *		This parameter specifies the number of spaces required
     *		to align the lines below the first.
     * @return Returns true if a linefeed is needed after
     *		printing this object, false otherwise.
     */
    public boolean printObject(Object obj, int indent)  {
    if (obj instanceof String)  {
        doPrint((String)obj);
        return(true);
    }  else if (obj instanceof Hashtable)  {
        Hashtable hashObj = (Hashtable)obj;
        DebugPrinter dbp;
        String tmp = obj.getClass().getName();
        doPrintln((String)tmp);

        dbp = new DebugPrinter(hashObj, 4, filename);
        dbp.setIndent(indent);
        dbp.print();
        dbp.close();

        return(false);
    } else if (obj instanceof Vector) {
        Vector vobj = (Vector)obj;
            if (vobj.isEmpty()) {
            String tmp = "Empty    (" + obj.getClass().getName() + ")";
            doPrint(tmp);
            return(true);
            }
        DebugPrinter dbp;

        String tmp = obj.getClass().getName();
        doPrintln((String)tmp);

        dbp = new DebugPrinter(vobj, 4, filename);
        dbp.setIndent(indent);
        dbp.print();
        dbp.close();

        return(false);

    } else if (obj instanceof Boolean || obj instanceof Integer
              || obj instanceof Long ) {
        String tmp = obj.toString() + "    (" + obj.getClass().getName() + ")";
        doPrint(tmp);
        return(true);
    } else  {
        String tmp = obj.getClass().getName();
        doPrint((String)tmp);
        doPrintln("");

        printSpaces(indent);
        tmp = obj.toString();
        doPrint(tmp);

        return(true);
    }
    }

    /*
     * Convenience method for printing spaces
     */
    private void printSpaces(int count)  {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < count; ++i)  {
            sb.append(" ");
        }
        doPrint(sb.toString());
    }

    /*
     * Create output title.
     */
    private void setupTitle()  {
    String[] titleRow = new String[2];

        if (vector != null) {
            titleRow[0] = "index";
            titleRow[1] = "Value";
        addTitle(titleRow);
            return;
        }
    titleRow[0] = "Property Name";
    titleRow[1] = "Property Value";
    addTitle(titleRow);
    }

    /*
     * Populate rows of printer with contents of hashtable
     */
    private void setupData()  {
    Object[] row;

    if (hashtable == null)  {

            if (vector == null)
            return;

            setSortNeeded(false);
        row = new Object[2];
            int i =0;
        for (Enumeration e = vector.elements() ; e.hasMoreElements() ;) {
            Object obj = e.nextElement();
                i++;
            row[0] = String.valueOf(i);
            row[1] = obj;
            add(row);
            }
            return;

    }

        setSortNeeded(true);
    row = new Object[2];
    for (Enumeration e = hashtable.keys() ; e.hasMoreElements() ;) {
        String curKey = (String)e.nextElement();
        Object obj;

        row[0] = curKey;
        obj = hashtable.get(curKey);
        row[1] = obj;
        add(row);
        }
    }

    /*
     * Set output print stream depending on whether a filename is set
     * or not.
     */
    private void initOutput()  {
    if (filename != null)  {
        try  {
            f = new File(filename);
            fos = new FileOutputStream(f, true);
            ps = new PrintStream(fos);
        } catch (Exception e)  {
            System.err.println("Exception caught when setting output to file: "
                + filename
                + ": "
                + e.toString());
            System.err.println("Reverting to stdout");
        }
    }
    }

    /*
     * Close output streams.
     */
    private void closeOutput()  {
    if (filename != null)  {
        try  {
        if ((ps != null) && (ps != System.out))  {
                ps.close();
                ps = null;
        }
        if (fos != null)  {
                fos.close();
                fos = null;
        }
            f = null;
        } catch (Exception e)  {
            System.err.println("Exception caught when closing print streams to: "
                + filename
                + ": "
                + e.toString());
        }
    }
    }

    /**
     * Close the debug printer.  If this associates with a file, the file
     * output stream is closed.
     */
    public void close() {
        if (ps == System.out) {
          if (buffer.length()>0) {
             ps.print(buffer.toString());
             buffer.setLength(0);
          }
        }
        closeOutput();
    }
}

