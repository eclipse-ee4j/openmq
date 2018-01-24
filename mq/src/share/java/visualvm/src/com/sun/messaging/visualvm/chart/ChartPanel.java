/*
 * Copyright (c) 2010, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.visualvm.chart;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import com.sun.tools.visualvm.charts.ChartFactory;
import com.sun.tools.visualvm.charts.SimpleXYChartDescriptor;
import com.sun.tools.visualvm.charts.SimpleXYChartSupport;

@SuppressWarnings("serial")
public class ChartPanel extends JPanel {


    private SimpleXYChartSupport chartSupport;
    
    private String chartedAttribute;
    private String rowIndentifierAttribute;
    private String[] rowIdentifierValues;
    
    /**
     * Create a chart to show the value of a single attribute for one or more items in a MQResourceList
     * @param chartedAttribute
     * @param rowIndentifierAttribute
     * @param rowIdentifierValues
     */
    public ChartPanel(String chartedAttribute,
			String rowIndentifierAttribute, String[] rowIdentifierValues) {
    	super();

		this.chartedAttribute = chartedAttribute;
		this.rowIndentifierAttribute = rowIndentifierAttribute;
		this.rowIdentifierValues = rowIdentifierValues;

        initModels();
        initComponents();
	}
    

    /**
     * Create a chart to show the value of a single attribute for the item represented by a MQAttributeList
     * @param attributeName
     */
    public ChartPanel(String attributeName) {
		super();
		
		this.chartedAttribute = attributeName;
		this.rowIdentifierValues = new String[1];
		rowIdentifierValues[0]="";

        initModels();
        initComponents();
		
	}


    /**
     * Make no-arg constructor private so it is not used
     */
    @SuppressWarnings("unused")
	private ChartPanel() {}



	private void initModels() {
        // Create a descriptor for chart showing decimal values
        // 100: initial y maximum before first values arrive
        // true: any item can be temporarily hidden by the user
        // 30: number of visible values, kind of viewport width
    	//TODO Should be able to configure valuesBuffer
        SimpleXYChartDescriptor chartDescriptor =
                SimpleXYChartDescriptor.decimal(100, true, 1000);

        // Add two items displayed as filled area with a line border
        ////chartDescriptor.addLineFillItems("Item 1", "Item 2");
        // Add two items displayed as lines
        chartDescriptor.addLineItems(rowIdentifierValues);
        
        // Create textual details area above the chart
        String[] detailsItems = new String[rowIdentifierValues.length];
        for (int i = 0; i < rowIdentifierValues.length; i++) {
			detailsItems[i]=rowIdentifierValues[i]+" "+chartedAttribute;
		}
        
        chartDescriptor.setDetailsItems(detailsItems);

        // Create SimpleXYChartSupport instance from the descriptor to access the chart
        chartSupport = ChartFactory.createSimpleXYChart(chartDescriptor);
    }

    private void initComponents() {
        // Makes this JPanel transparent to better fit VisualVM UI (white background)
        setOpaque(false);
        
        setLayout(new BorderLayout());
        
        // add the chart to this JPanel
        add(chartSupport.getChart(), BorderLayout.CENTER);

        // Update the initial values in details area before first values arrive
        String[] initialDetails = new String[rowIdentifierValues.length];
        for (int i = 0; i < rowIdentifierValues.length; i++) {
        	initialDetails[i]="waiting...";
		}
        chartSupport.updateDetails(initialDetails);
    }

	public void updateDetails(long[] values) {
		String[] details = new String[rowIdentifierValues.length];
        for (int i = 0; i < rowIdentifierValues.length; i++) {
        	details[i]=chartSupport.formatDecimal(values[i]);
		}
		chartSupport.updateDetails(details);
	}


	public void addValues(long timestamp, long[] values) {
		chartSupport.addValues(timestamp, values);
	}


	public String getChartedAttribute() {
		return chartedAttribute;
	}


	public String getRowIndentifierAttribute() {
		return rowIndentifierAttribute;
	}


	public String[] getRowIdentifierValues() {
		return rowIdentifierValues;
	}



}
