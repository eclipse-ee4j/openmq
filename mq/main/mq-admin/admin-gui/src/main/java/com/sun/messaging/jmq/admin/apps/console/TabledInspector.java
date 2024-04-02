/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import java.util.Enumeration;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import com.sun.messaging.jmq.admin.apps.console.event.SelectionEvent;

/**
 * Inspector panel containing a JTable used to display the children of the inspected ConsoleObj object.
 *
 * <P>
 * This class can be used as the super class of any InspectorPanel that requires the following:
 * <UL>
 * <LI>lists children in a JTable
 * <LI>dispatches a OBJ_SELECTED event when an entry in the JTable is selected.
 * <LI>collumn labels are customizable
 * <LI>cell content displayed is obtained from the ConsoleObj (or subclass of it) class.
 * </UL>
 *
 * <P>
 * 2 abstract methods need to be implemented by subclasses:
 * <UL>
 * <LI>public abstract String[] getColumnHeaders()<BR>
 * This method returns an array containing the strings used for the collumn headers/labels.
 * <LI>public abstract Object getValueAtCollumn(ConsoleObj conObj, int col)<BR>
 * This method returns the Object at a particular cell collumn for a given ConsoleObj object. Each row in the JTable
 * represents one ConsoleObj. This method returns the object/value for the ConsoleObj, for a particular collumn.
 *
 * </UL>
 *
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public abstract class TabledInspector extends InspectorPanel implements ListSelectionListener {
    private static final long serialVersionUID = 1955105658708757041L;
    private CObjTableModel model;
    private JTable table;

    /**
     * Creates/Returns the panel that contains the InspectorPanel GUI.
     *
     * @return the panel that contains the InspectorPanel GUI.
     */
    @Override
    public JPanel createWorkPanel() {
        JPanel listPanel = new JPanel();
        JScrollPane scrollPane;

        model = new CObjTableModel();
        table = new JTable(model);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel lsm = table.getSelectionModel();
        lsm.addListSelectionListener(this);
        scrollPane = new JScrollPane(table);

        listPanel.setLayout(new BorderLayout());
        listPanel.add(scrollPane, BorderLayout.CENTER);

        return (listPanel);
    }

    /**
     * Clears the selection in the InspectorPanel.
     */
    @Override
    public void clearSelection() {
        if (table != null) {
            table.clearSelection();
        }
    }

    /**
     * Initializes the InspectorPanel for the currently inspected object.
     */
    @Override
    public void inspectorInit() {
        model.fireTableChanged(new TableModelEvent(model));
        clearSelection();
    }

    /**
     * This method is called when the selected object (row) has been updated. The appropriate TableModelEvent is dispatched.
     */
    @Override
    public void selectedObjectUpdated() {
        if (table == null) {
            return;
        }

        int row = table.getSelectedRow();

        if (row < 0) {
            return;
        }

        model.fireTableChanged(new TableModelEvent(model, row));
    }

    /*
     * BEGIN INTERFACE ListSelectionListener
     */
    @Override
    public void valueChanged(ListSelectionEvent e) {
        ListSelectionModel lsm = (ListSelectionModel) e.getSource();
        boolean isAdjusting = e.getValueIsAdjusting();
        // int firstIndex = e.getFirstIndex();
        // int lastIndex = e.getLastIndex();

        if (isAdjusting == false) {
            /*
             * Query model for selected index and get the object at the index.
             */
            if (lsm.isSelectionEmpty()) {//NOPMD
                /*
                 * System.out.println("nothing selected");
                 */
            } else {
                int selectedRow = lsm.getMinSelectionIndex();
                /*
                 * System.err.println("selected row : " + selectedRow);
                 */

                Object o = model.getValueAt(selectedRow, 0);

                /*
                 * System.err.println("Selected obj: " + o); System.err.println("\tobj type:" + o.getClass().getName());
                 */

                /*
                 * Dispatch a selection event.
                 */
                SelectionEvent se = new SelectionEvent(this, SelectionEvent.OBJ_SELECTED);
                se.setSelectedObj((ConsoleObj) o);

                fireAdminEventDispatched(se);
            }
        }
    }
    /*
     * END INTERFACE ListSelectionListener
     */

    /*
     * TableModel class for TabledInspector. Note: This model assumes the objects at collumn = 0 are the ConsoleObj objects.
     */
    class CObjTableModel extends AbstractTableModel {
        /**
         * 
         */
        private static final long serialVersionUID = -3527022764578800789L;

        /**
         * Returns the number of collumns in table.
         *
         * @return The number of collumns in table.
         */
        @Override
        public int getColumnCount() {
            String[] columnNames = getColumnHeaders();

            if (columnNames == null) {
                return (-1);
            }

            return columnNames.length;
        }

        /**
         * Returns the number of rows in table.
         *
         * @return The number of rows in table.
         */
        @Override
        public int getRowCount() {
            ConsoleObj conObj = getConsoleObj();
            int rowcount = 0;

            if (conObj != null) {
                for (Enumeration e = conObj.children(); e.hasMoreElements(); e.nextElement()) {
                    rowcount++;
                }
            }
            return rowcount;
        }

        /**
         * Returns the collumn name/label for a given collumn.
         *
         * @return the collumn name/label for a given collumn.
         */
        @Override
        public String getColumnName(int col) {
            String[] columnNames = getColumnHeaders();

            if (columnNames == null) {
                return (null);
            }

            if (col >= columnNames.length) {
                return (null);
            }

            return columnNames[col];
        }

        /**
         * Return value at a particular table cell location. Calls the TabledInspector.getValueAtCollumn() method.
         */
        @Override
        public Object getValueAt(int row, int col) {
            ConsoleObj conObj = getConsoleObj(), childNode;

            if (conObj == null) {
                return (null);
            }

            int rowcount = 0;
            for (Enumeration e = conObj.children(); e.hasMoreElements();) {
                childNode = (ConsoleObj) e.nextElement();
                if (rowcount == row) {
                    if (col == 0) {
                        return (childNode);
                    }
                    return (getValueAtCollumn(childNode, col));
                }

                rowcount++;

            }
            return null;
        }

        /**
         * Don't need to implement this method unless your table's editable.
         */
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        /**
         * Don't need to implement this method unless your table's data can change.
         */
        @Override
        public void setValueAt(Object value, int row, int col) {
        }

    }

    /**
     * Return the array of Strings containing the collumn labels/headers.
     *
     * @return the array of Strings containing the collumn labels/headers.
     */
    public abstract String[] getColumnHeaders();

    /**
     * Returns the Object at a particular cell collumn for a given ConsoleObj object. Each row in the JTable represents one
     * ConsoleObj. This method returns the object/value for the ConsoleObj, for a particular collumn.
     *
     * @return the Object at a particular cell collumn for a given ConsoleObj object.
     */
    public abstract Object getValueAtCollumn(ConsoleObj conObj, int col);
}
