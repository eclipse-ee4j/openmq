/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2020, 2022 Contributors to Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility class for printing aligned collumns of text. How/where the text is printed is determined by the abstract
 * methods doPrint(String) and doPrintln(String). The typical case is to create a subclass and make these methods print
 * the string to standard out or error.
 * <P>
 * This class allows you to specify:
 * <UL>
 * <LI>The number of collumns in the output. This will determine the dimension of the string arrays passed to
 * add(Object[]) or addTitle(String[]).
 * <LI>spacing/gap between columns
 * <LI>character to use for title border (null means no border)
 * <LI>column alignment. Only LEFT/CENTER is supported for now.
 * </UL>
 *
 * <P>
 * Example usage:
 *
 * <PRE>
 * MyPrinter mp = new MyPrinter(3, 2, "-");
 * String oneRow[] = new String[3];
 * oneRow[0] = "User Name";
 * oneRow[1] = "Email Address";
 * oneRow[2] = "Phone Number";
 * mp.addTitle(oneRow);
 *
 * oneRow[0] = "Bob";
 * oneRow[1] = "bob@foo.com";
 * oneRow[2] = "123-4567";
 * mp.add(oneRow);
 *
 * oneRow[0] = "John";
 * oneRow[1] = "john@foo.com";
 * oneRow[2] = "456-7890";
 * mp.add(oneRow);
 * mp.print();
 * </PRE>
 *
 * <P>
 * The above would print:
 * <P>
 *
 * <PRE>
 *	--------------------------------------
 *	User Name  Email Address  Phone Number
 *	--------------------------------------
 *	Bob        bob@foo.com    123-4567
 *	John       john@foo.com   456-7890
 * </PRE>
 *
 * <P>
 * This class also supports multi-row titles and having title strings spanning multiple collumns. Example usage:
 *
 * <PRE>
 * TestPrinter tp = new TestPrinter(4, 2, "-");
 * String oneRow[] = new String[4];
 * int[] span = new int[4];
 *
 * span[0] = 2; // spans 2 collumns
 * span[1] = 0; // spans 0 collumns
 * span[2] = 2; // spans 2 collumns
 * span[3] = 0; // spans 0 collumns
 *
 * tp.setTitleAlign(CENTER);
 * oneRow[0] = "Name";
 * oneRow[1] = "";
 * oneRow[2] = "Contact";
 * oneRow[3] = "";
 * tp.addTitle(oneRow, span);
 *
 * oneRow[0] = "First";
 * oneRow[1] = "Last";
 * oneRow[2] = "Email";
 * oneRow[3] = "Phone";
 * tp.addTitle(oneRow);
 *
 * oneRow[0] = "Bob";
 * oneRow[1] = "Jones";
 * oneRow[2] = "bob@foo.com";
 * oneRow[3] = "123-4567";
 * tp.add(oneRow);
 *
 * oneRow[0] = "John";
 * oneRow[1] = "Doe";
 * oneRow[2] = "john@foo.com";
 * oneRow[3] = "456-7890";
 * tp.add(oneRow);
 *
 * tp.println();
 * </PRE>
 *
 * <P>
 * The above would print:
 * <P>
 *
 * <PRE>
 *      ------------------------------------
 *          Name             Contact
 *      First  Last      Email       Phone
 *      ------------------------------------
 *      Bob    Jones  bob@foo.com   123-4567
 *      John   Doe    john@foo.com  456-7890
 * </PRE>
 *
 */
@SuppressWarnings("JdkObsolete")
public abstract class MultiColumnPrinter implements Serializable {

    @Serial
    private static final long serialVersionUID = -8013528725857861640L;
    public static final int LEFT = 0;
    public static final int CENTER = 1;

    /*
     * Sets the default sorting behavior. When set to true, the table entries are sorted unless otherwise specified in a
     * constructor.
     */
    private static final boolean DEFAULT_SORT = true;

    private static final int DEFAULT_NUMCOL = 2;
    private static final int DEFAULT_GAP = 4;

    private int numCol = DEFAULT_NUMCOL;
    private int gap = DEFAULT_GAP;
    private int align = CENTER;
    private int titleAlign = CENTER;
    private String border = null;
    private int indent = 0;

    private Vector<Object[]> table = null;
    private Vector<String[]> titleTable = null;
    private Vector<int[]> titleSpanTable = null;
    private int curLength[];

    private boolean sortNeeded = DEFAULT_SORT;
    private int[] keyCriteria = null;

    public MultiColumnPrinter() {
        this(DEFAULT_NUMCOL, DEFAULT_GAP);
    }

    /**
     * Creates a new MultiColumnPrinter class.
     *
     * @param numCol number of columns
     * @param gap gap between each column
     * @param border character used to frame the titles
     * @param align type of alignment within columns
     * @param sort true if the output is sorted; false otherwise
     *
     * REVISIT: Possibly adding another argument that specifies which ones can be truncated (xxx...)
     */
    public MultiColumnPrinter(int numCol, int gap, String border, int align, boolean sort) {
        table = new Vector<>();
        titleTable = new Vector<>();
        titleSpanTable = new Vector<>();
        curLength = new int[numCol];

        this.numCol = numCol;
        this.gap = gap;
        this.border = border;
        this.align = align;
        this.titleAlign = LEFT;
        this.sortNeeded = sort;
    }

    /**
     * Creates a new sorted MultiColumnPrinter class.
     *
     * @param numCol number of columns
     * @param gap gap between each column
     * @param border character used to frame the titles
     * @param align type of alignment within columns
     */
    public MultiColumnPrinter(int numCol, int gap, String border, int align) {
        this(numCol, gap, border, align, DEFAULT_SORT);
    }

    /**
     * Creates a sorted new MultiColumnPrinter class using LEFT alignment.
     *
     * @param numCol number of columns
     * @param gap gap between each column
     * @param border character used to frame the titles
     */
    public MultiColumnPrinter(int numCol, int gap, String border) {
        this(numCol, gap, border, LEFT);
    }

    /**
     * Creates a sorted new MultiColumnPrinter class using LEFT alignment and with no title border.
     *
     * @param numCol number of columns
     * @param gap gap between each column
     */
    public MultiColumnPrinter(int numCol, int gap) {
        this(numCol, gap, null, LEFT);
    }

    /**
     * These 3 methods can be called right after using of default constructor
     */
    public void setNumCol(int n) {
        numCol = n;
        curLength = new int[numCol];
    }

    public void setGap(int n) {
        gap = n;
    }

    public void setBorder(String b) {
        border = b;
    }

    public void copy(MultiColumnPrinter mcp) {
        numCol = mcp.numCol;
        gap = mcp.gap;
        align = mcp.align;
        titleAlign = mcp.titleAlign;
        border = mcp.border;
        indent = mcp.indent;
        table = mcp.table;
        titleTable = mcp.titleTable;
        titleSpanTable = mcp.titleSpanTable;
        curLength = mcp.curLength;
        sortNeeded = mcp.sortNeeded;
        keyCriteria = mcp.keyCriteria;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    /**
     * Set whether sorting is needed
     *
     * @param sortNeeded If true, table will be sorted. If false, sorting will not be done.
     */
    public void setSortNeeded(boolean sortNeeded) {
        this.sortNeeded = sortNeeded;
    }

    /**
     * Adds to the row of strings to be used as the title for the table.
     *
     * @param row Array of strings to print in one row of title.
     */
    public void addTitle(String[] row) {
        if (row == null) {
            return;
        }

        int[] span = new int[row.length];
        for (int i = 0; i < row.length; i++) {
            span[i] = 1;
        }

        addTitle(row, span);
    }

    /**
     * Adds to the row of strings to be used as the title for the table. Also allows for certain title strings to span
     * multiple collumns The span parameter is an array of integers which indicate how many collumns the corresponding title
     * string will occupy. For a row that is 4 collumns wide, it is possible to have some title strings in a row to 'span'
     * multiple collumns:
     *
     * <P>
     *
     * <PRE>
     * ------------------------------------
     *     Name             Contact
     * First  Last      Email       Phone
     * ------------------------------------
     * Bob    Jones  bob@foo.com   123-4567
     * John   Doe    john@foo.com  456-7890
     * </PRE>
     *
     * In the example above, the title row has a string 'Name' that spans 2 collumns. The string 'Contact' also spans 2
     * collumns. The above is done by passing in to addTitle() an array that contains:
     *
     * <PRE>
     * span[0] = 2; // spans 2 collumns
     * span[1] = 0; // spans 0 collumns, ignore
     * span[2] = 2; // spans 2 collumns
     * span[3] = 0; // spans 0 collumns, ignore
     * </PRE>
     * <P>
     * A span value of 1 is the default. The method addTitle(String[] row) basically does:
     *
     * <PRE>{@code
     * int[] span = new int[row.length];
     * for (int i = 0; i < row.length; i++) {
     *     span[i] = 1;
     * }
     * addTitle(row, span);
     * }</PRE>
     *
     * @param row Array of strings to print in one row of title.
     * @param span Array of integers that reflect the number of columns the corresponding title string will occupy.
     */
    public void addTitle(String[] row, int span[]) {
        // Need to create a new instance of it, otherwise the new values will
        // always overwrite the old values.

        String[] rowInstance = new String[(row.length)];
        System.arraycopy(row, 0, rowInstance, 0, row.length);
        titleTable.addElement(rowInstance);

        titleSpanTable.addElement(span);
    }

    /**
     * Set alignment for title strings
     */
    public void setTitleAlign(int titleAlign) {
        this.titleAlign = titleAlign;
    }

    /**
     * Adds one row of text to output.
     *
     * @param row Array of objects to print in one row.
     */
    public void add(Object[] row) {
        // Need to create a new instance of it, otherwise the new values will
        // always overwrite the old values.
        Object[] rowInstance = new Object[(row.length)];
        System.arraycopy(row, 0, rowInstance, 0, row.length);
        table.addElement(rowInstance);
    }

    /**
     * Clears title strings.
     */
    public void clearTitle() {
        titleTable.clear();
        titleSpanTable.clear();
    }

    /**
     * Clears strings.
     */
    public void clear() {
        table.clear();

        if (curLength != null) {
            for (int i = 0; i < curLength.length; ++i) {
                curLength[i] = 0;
            }
        }
    }

    /**
     * Prints the multi-column table, including the title.
     */
    public void print() {
        print(true);
    }

    /**
     * Prints the multi-column table.
     *
     * @param printTitle Specifies if the title rows should be printed.
     */
    public void print(boolean printTitle) {

        // REVISIT:
        // Make sure you take care of curLength and row being null value cases.

        // Get the longest string for each column and store in curLength[]

        // Scan through title rows
        Enumeration<String[]> e = titleTable.elements();
        Enumeration<int[]> spanEnum = titleSpanTable.elements();
        while (e.hasMoreElements()) {
            String[] row = e.nextElement();
            int[] curSpan = spanEnum.nextElement();

            for (int i = 0; i < numCol; i++) {
                // Fix for 4627901: NullPtrException seen when
                // execute 'jmqcmd list dur'
                // This happens when a field to be listed is null.
                // None of the fields should be null, but if it
                // happens to be so, replace it with "-".
                if (row[i] == null) {
                    row[i] = "-";
                }

                int len = getItemLength(row[i]);

                /*
                 * If a title string spans multiple collumns, then the space it occupies in each collumn is at most len/span (since we
                 * have gap to take into account as well).
                 */
                int span = curSpan[i], rem = 0;
                if (span > 1) {
                    rem = len % span;
                    len = len / span;
                }

                if (curLength[i] < len) {
                    curLength[i] = len;

                    if ((span > 1) && ((i + span) <= numCol)) {
                        for (int j = i + 1; j < (i + span); ++j) {
                            curLength[j] = len;
                        }

                        /*
                         * Add remainder to last collumn in span to avoid round-off errors.
                         */
                        curLength[(i + span) - 1] += rem;
                    }
                }
            }
        }

        // Scan through rest of rows
        Enumeration<Object[]> rows = table.elements();
        while (rows.hasMoreElements()) {
            Object[] row = rows.nextElement();
            for (int i = 0; i < numCol; i++) {
                // Fix for 4627901: NullPtrException seen when
                // execute 'jmqcmd list dur'
                // This happens when a field to be listed is null.
                // None of the fields should be null, but if it
                // happens to be so, replace it with "-".
                if (row[i] == null) {
                    row[i] = "-";
                }

                int itemLen = getItemLength(row[i]);

                if (curLength[i] < itemLen) {
                    curLength[i] = itemLen;
                }
            }
        }

        /*
         * Print title
         */
        if (printTitle) {
            printBorder();
            e = titleTable.elements();
            spanEnum = titleSpanTable.elements();

            /*
             * Print indentation if any
             */
            printSpaces(indent);

            while (e.hasMoreElements()) {
                Object[] row = e.nextElement();
                int[] curSpan = spanEnum.nextElement();

                for (int i = 0; i < numCol; i++) {
                    int availableSpace = 0, span = curSpan[i], itemLen = getItemLength(row[i]);

                    if (span == 0) {
                        continue;
                    }

                    availableSpace = curLength[i];

                    if ((span > 1) && ((i + span) <= numCol)) {
                        for (int j = i + 1; j < (i + span); ++j) {
                            availableSpace += gap;
                            availableSpace += curLength[j];
                        }
                    }

                    if (titleAlign == CENTER) {
                        int space_before, space_after;
                        space_before = (availableSpace - itemLen) / 2;
                        space_after = availableSpace - itemLen - space_before;

                        printSpaces(space_before);
                        doPrint(getItemString(row[i]));
                        printSpaces(space_after);
                        if (i < numCol - 1) {
                            printSpaces(gap);
                        }
                    } else {
                        doPrint(getItemString(row[i]));
                        if (i < numCol - 1) {
                            printSpaces(availableSpace - itemLen + gap);
                        }
                    }

                }
                doPrintln("");
            }
            printBorder();
        }

        if (sortNeeded) {
            printSortedTable();
        } else {
            printUnsortedTable();
        }
    }

    /*
     * Prints the table entries in the sorted order.
     */
    private void printSortedTable() {
        // Sort the table entries
        TreeMap<Object, Object[]> sortedTable = new TreeMap<>();
        Enumeration<Object[]> e = table.elements();
        while (e.hasMoreElements()) {
            Object[] row = e.nextElement();

            // If keyCriteria contains valid info use that
            // to create the key; otherwise, use the default row[0]
            // for the key.
            if (keyCriteria != null && keyCriteria.length > 0) {
                String key = getKey(row);
                if (key != null) {
                    sortedTable.put(key, row);
                } else {
                    sortedTable.put(row[0], row);
                }
            } else {
                sortedTable.put(row[0], row);
            }
        }

        // Iterate through the table entries
        for (Map.Entry<Object, Object[]> entry : sortedTable.entrySet()) {
            printRow(entry.getValue());
        }
    }

    /*
     * Creates the key for the current row based on the criteria specified by setKeyCriteria(). If key cannot be created by
     * the criteria, it simply returns null.
     *
     * Examples: String[] row = {"foo", "bar", "hello");
     *
     * int[] keyCriteria = {0}; key = "foo";
     *
     * int[] keyCriteria = {0, 1}; key = "foobar";
     *
     * int[] keyCriteria = {2, 1}; key = "hellobar";
     *
     * int[] keyCriteria = {4}; key = null;
     */
    private String getKey(Object[] row) {
        StringBuilder key = new StringBuilder();

        for (int i = 0; i < keyCriteria.length; i++) {
            int content = keyCriteria[i];
            try {
                key.append(getItemString(row[content]));
            } catch (ArrayIndexOutOfBoundsException ae) {
                // Happens when keyCriteria[] contains an index that
                // does not exist in 'row'.
                return null;
            }
        }
        return key.toString();
    }

    /*
     * Prints the table entries in the order they were entered.
     */
    private void printUnsortedTable() {
        Enumeration<Object[]> e = table.elements();
        while (e.hasMoreElements()) {
            Object[] row = e.nextElement();

            printRow(row);
        }
    }

    private void printRow(Object[] row) {
        int itemOffset = 0;

        /*
         * Print indentation, if any
         */
        printSpaces(indent);
        itemOffset += indent;

        boolean needLineFeed = true;

        for (int i = 0; i < numCol; i++) {
            int itemLen = getItemLength(row[i]);

            if (align == CENTER) {
                int space1, space2;
                space1 = (curLength[i] - itemLen) / 2;
                space2 = curLength[i] - itemLen - space1;

                printSpaces(space1);
                itemOffset += space1;

                needLineFeed = printObject(row[i], itemOffset);
                itemOffset += itemLen;
                printSpaces(space2);
                itemOffset += space2;

                if (i < numCol - 1) {
                    printSpaces(gap);
                    itemOffset += gap;
                }
            } else {
                needLineFeed = printObject(row[i], itemOffset);
                itemOffset += itemLen;
                if (i < numCol - 1) {
                    printSpaces(curLength[i] - itemLen + gap);
                    itemOffset += (curLength[i] - itemLen + gap);
                }
            }
        }
        if (needLineFeed) {
            doPrintln("");
        }
    }

    /**
     * Prints the multi-column table, with a carriage return.
     */
    public void println() {
        print();
        doPrintln("");
    }

    private void printSpaces(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            sb.append(' ');
        }
        doPrint(sb.toString());
    }

    private void printBorder() {

        if (border == null) {
            return;
        }

        /*
         * Print indentation if any
         */
        printSpaces(indent);

        // For the value in each column
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numCol; i++) {
            for (int j = 0; j < curLength[i]; j++) {
                sb.append(border);
            }
        }
        doPrint(sb.toString());

        // For the gap between each column
        sb.setLength(0);
        for (int i = 0; i < numCol - 1; i++) {
            for (int j = 0; j < gap; j++) {
                sb.append(border);
            }
        }
        doPrintln(sb.toString());
    }

    private String getItemString(Object obj) {
        if (obj instanceof String) {
            return ((String) obj);
        } else {
            String tmp = obj.getClass().getName();
            return (tmp);
        }
    }

    private int getItemLength(Object obj) {
        int len = 0;

        if (obj instanceof String) {
            len = ((String) obj).length();
        } else {
            String tmp = obj.getClass().getName();
            len = tmp.length();
        }

        return len;
    }

    /**
     * @param obj Object to print.
     * @param indent indentation of object to be printed.
     * @return Returns true if a linefeed is needed after printing this object, false otherwise.
     */
    public boolean printObject(Object obj, int indent) {
        if (obj instanceof String) {
            doPrint((String) obj);
        } else {
            String className = obj.getClass().getName();
            doPrintln(className);

            printSpaces(indent);
            doPrint(obj.toString());
        }

        return (true);
    }

    /*
     * Sets the criteria for the key. new int[] {0, 1} means use the first and the second elements of the array.
     */
    public void setKeyCriteria(int[] criteria) {
        this.keyCriteria = criteria;
    }

    /**
     * Method that does the actual printing. Override this method to print to your destination of choice (stdout, stream,
     * etc).
     *
     * @param str String to print.
     */
    public abstract void doPrint(String str);

    /**
     * Method that does the actual printing. Override this method to print to your destination of choice (stdout, stream,
     * etc).
     *
     * This method also prints a newline at the end of the string.
     *
     * @param str String to print.
     */
    public abstract void doPrintln(String str);

}
