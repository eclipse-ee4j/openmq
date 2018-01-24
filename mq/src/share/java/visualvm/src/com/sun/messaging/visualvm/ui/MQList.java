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

package com.sun.messaging.visualvm.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import com.sun.messaging.visualvm.chart.ChartPanel;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 * This is the abstract superclass of all JPanels used by the MQ plugin that show tables of things,
 * and which are used in a detail view
 */
@SuppressWarnings("serial")
public abstract class MQList extends javax.swing.JPanel implements MQUIEventListener, TableModelListener {
	
	/**
	 * The DataViewComponent within which this component is being displayed
	 * This is needed to allow this class to create charts in that DataViewComponent
	 */
	private DataViewComponent dvc;
	
	/**
	 * A List of ChartUpdater object, one for each ChartView dependent on this
	 * resource list
	 */
	List<ChartUpdater> chartUpdaters = new ArrayList<ChartUpdater>();
    	
	// prevent subclasses calling default constructor
	@SuppressWarnings("unused")
	private MQList(){		
	}
	
	MQList(DataViewComponent dvc){
		this.dvc=dvc;
	}

	/**
	 * Utility method:
	 * Set the column widths of the supplied table to appropriate values given the size of the heading and the data
	 * 
	 * Based on the example referred to from 
	 * http://java.sun.com/docs/books/tutorial/uiswing/components/table.html
	 * @param table
	 */
    protected void initColumnSizes(JTable table) {

		TableModel tableModel = table.getModel();
		TableCellRenderer defaultRenderer = table.getTableHeader().getDefaultRenderer();
		
		// this technique seems to under-estimate the width needed, so need to add a fudge factor
		int fudgeFactor=5;
		
		int colCount = table.getColumnModel().getColumnCount();
		int rowCount = table.getRowCount();
		
		for (int iCol = 0; iCol < colCount; iCol++) {
			TableColumn column = table.getColumnModel().getColumn(iCol);

			// calculate width of column heading
			Component headerComp = defaultRenderer.getTableCellRendererComponent(null, column.getHeaderValue(), false, false, 0, 0);
			int headerWidth = headerComp.getPreferredSize().width;

			// calculate width of widest value in this column
			int maxWidth = 0;

			for (int iRow = 0; iRow < rowCount; iRow++) {
				Object value = tableModel.getValueAt(iRow, iCol);
				Component cellComp = table.getDefaultRenderer(
						tableModel.getColumnClass(iCol)).getTableCellRendererComponent(table, value, false,false, 0, iCol);
				int thisCellWidth = cellComp.getPreferredSize().width;

				if (thisCellWidth > maxWidth) {
					maxWidth = thisCellWidth;
				}
			}

			column.setPreferredWidth(Math.max(headerWidth, maxWidth)+fudgeFactor);
		}
	}

	public DataViewComponent getDvc() {
		return dvc;
	}
	

	
	/**
	 * Return the corner of the master view where this panel should be displayed
	 * 
	 * Possible values are DataViewComponent.TOP_LEFT, DataViewComponent.TOP_RIGHT, 
	 * DataViewComponent.BOTTOM_LEFT, DataViewComponent.BOTTOM_RIGHT
	 * 
	 * @return
	 */
	public abstract int getCorner();
	
    /**
	 * Register for updates to the specified chart
     * @param chartPanel
     */
	public void registerChart(ChartPanel chartPanel) {
		chartUpdaters.add(createChartUpdater(chartPanel));
	}
	
	protected void updateRegisteredCharts() {
		for (ChartUpdater thisChartUpdater : chartUpdaters) {
			thisChartUpdater.updateCharts();
		}
	}

	protected abstract ChartUpdater createChartUpdater(ChartPanel chartPanel);
}
