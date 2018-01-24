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
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.MissingResourceException;

import javax.management.MBeanServerConnection;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import org.openide.util.NbBundle;

import com.sun.messaging.visualvm.chart.ChartPanel;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;


/**
 * This is the abstract superclass of all JPanels that list the attributes of a single resource
 * 
 * Features of this class:
 * - The panel has a right-mouse menu which offers the following options:
 *      NO "Configure Attributes Displayed" 
 *      Auto reload
 */
@SuppressWarnings("serial")
public abstract class MQAttributeList extends MQList {

    private MQResourceListTableModel tableModel = null;

    public MQAttributeList(DataViewComponent dvc) {
    	super(dvc);
        initTableModel();
        initComponents();
        
        // Additions to handle cell selection
        
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	    dataTable.setCellSelectionEnabled(true);
        
        // configure row selection
        dataTable.setRowSelectionAllowed(true);

		// disable column selection
		dataTable.setColumnSelectionAllowed(false);

		dataTable.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				showPopup(e);
			}

			public void mouseReleased(MouseEvent e) {
				showPopup(e);
			}

			private void showPopup(MouseEvent e) {

				if (e.isPopupTrigger()) {
					if (dataTable.getSelectedRow()>=0 && dataTable.getSelectedColumn()>=0){
						// a cell is selected
						//TODO ideally we should merge this with the tablePopup menu
						cellPopup.show(e.getComponent(), e.getX(), e.getY());
					} else {
						// no cell selected: show the default menu
						tablePopup.show(e.getComponent(), e.getX(), e.getY());
					}
				}
			}
		});        
        
        // configure tooltips for the first (attribute name) column
        TableColumn tc1 = dataTable.getColumnModel().getColumn(0);
        configureTooltips(tc1);
                
        jScrollPane1.addMouseListener(new MouseAdapter() {
        });

// DISABLE MOUSE ACTIONS ON THE TABLE FOR NOW 
//        TableMouseAdapter ta = new TableMouseAdapter(dataTable);
//        ta.addMQUIEventListener(this);
//        dataTable.addMouseListener(ta);
        dataTable.setRowSorter(new TableRowSorter<MQResourceListTableModel>(tableModel));

        tableModel.addTableModelListener(this);
        setAutoReloadValue(tableModel.getInterval());
    }

    /**
     * Configure tooltips for the column that displays attribute names
     * 
     * Note that this doesn't configure the tooltip for the column headers:
     * that is done separately
     * 
     * @param tc1
     */
    private void configureTooltips(TableColumn tc1) {

		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

			public Component getTableCellRendererComponent(JTable table,
					Object color, boolean isSelected, boolean hasFocus,
					int row, int column) {

				Component renderer = super.getTableCellRendererComponent(table,
						color, isSelected, hasFocus, row, column);

				// which attribute name is the mouse over
				String attributeName = null;
				try {
					attributeName = (String) tableModel.getValueAt(row, 0);
				} catch (ClassCastException cce) {
				}

				String tooltip = "";
				if (attributeName != null) {
					tooltip = getDescriptionForAttribute(attributeName);
				}

				this.setToolTipText(tooltip);

				return renderer;
			}

		};

		tc1.setCellRenderer(renderer);
	}

	protected abstract String getDescriptionForAttribute(String attributeName); 

	public abstract void initTableModel();

    public abstract void handleItemQuery(Object obj);

    public MQResourceListTableModel getTableModel() {
        return (tableModel);
    }

    public void setTableModel(MQResourceListTableModel model) {
        tableModel = model;
    }

    public JTableHeader getTableHeader(){
    	
    	return new JTableHeader(dataTable.getColumnModel()) {
            public String getToolTipText(MouseEvent e) {
                String tip = null;
                java.awt.Point p = e.getPoint();
                int index = columnModel.getColumnIndexAtX(p.x);
                String tooltipText="";
                if (index>=0){
                	int realIndex = 
                        columnModel.getColumn(index).getModelIndex();
                	tooltipText = getTooltipForColumn(realIndex);
                }
                return tooltipText;
            }
        };

    }    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tablePopup = new javax.swing.JPopupMenu();
        autoReloadStatus = new javax.swing.JCheckBoxMenuItem();
        cellPopup = new javax.swing.JPopupMenu();
        displayOnChart = new javax.swing.JMenuItem();
        jScrollPane1 = new javax.swing.JScrollPane();
        dataTable = new javax.swing.JTable();
        autoReloadLabel = new javax.swing.JLabel();
        autoReloadValue = new javax.swing.JLabel();

        autoReloadStatus.setSelected(true);
        autoReloadStatus.setText(org.openide.util.NbBundle.getMessage(MQAttributeList.class, "MQAttributeList.autoReloadStatus.text")); // NOI18N
        autoReloadStatus.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                autoReloadStatusItemStateChanged(evt);
            }
        });
        tablePopup.add(autoReloadStatus);

        displayOnChart.setText(org.openide.util.NbBundle.getMessage(MQAttributeList.class, "MQAttributeList.displayOnChart.text")); // NOI18N
        displayOnChart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayOnChartActionPerformed(evt);
            }
        });
        cellPopup.add(displayOnChart);

        setComponentPopupMenu(tablePopup);

        jScrollPane1.setInheritsPopupMenu(true);
        jScrollPane1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                jScrollPane1MousePressed(evt);
            }
        });

        dataTable.setModel(getTableModel());
        dataTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        dataTable.setTableHeader(getTableHeader());
        jScrollPane1.setViewportView(dataTable);

        autoReloadLabel.setText(org.openide.util.NbBundle.getMessage(MQAttributeList.class, "MQAttributeList.autoReloadLabel.text")); // NOI18N

        autoReloadValue.setText(org.openide.util.NbBundle.getMessage(MQAttributeList.class, "MQAttributeList.autoReloadValue.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(99, 99, 99)
                .addComponent(autoReloadLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(autoReloadValue, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(176, 176, 176))
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 430, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(autoReloadLabel)
                    .addComponent(autoReloadValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 293, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

private void jScrollPane1MousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jScrollPane1MousePressed
// TODO add your handling code here:
}//GEN-LAST:event_jScrollPane1MousePressed

private void autoReloadStatusItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_autoReloadStatusItemStateChanged
    int stateChange = evt.getStateChange();

    if (stateChange == ItemEvent.DESELECTED) {
        tableModel.stop();
        setAutoReloadValue(-1);
    } else if (stateChange == ItemEvent.SELECTED) {
        tableModel.start();
        setAutoReloadValue(tableModel.getInterval());
    }
}//GEN-LAST:event_autoReloadStatusItemStateChanged

private void displayOnChartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayOnChartActionPerformed

	MQResourceListTableModel model = (MQResourceListTableModel) dataTable.getModel();
	
	// Handle each row separately
	for (int iSelectedRow = 0; iSelectedRow < dataTable.getSelectedRowCount(); iSelectedRow++){
				
		int thisSelectedRowInModel = dataTable.convertRowIndexToModel(dataTable.getSelectedRows()[iSelectedRow]);

		// Get the attributeName
		int attributeNameCol = dataTable.convertColumnIndexToModel(0);
		String attributeName = (String) model.getValueAt(thisSelectedRowInModel, attributeNameCol);
		
		// Get the attributeValue and check it's a Long
		int attributeValueCol = dataTable.convertColumnIndexToModel(1);
		Object attributeValue = model.getValueAt(thisSelectedRowInModel, attributeValueCol);
 		if (!(attributeValue instanceof Number)){
 			JOptionPane.showMessageDialog(this, "Cannot chart non-numeric attribute "+attributeName+", ignoring it");
 			continue;
 		}
 		
        // Create the charting JPanel "
        ChartPanel chartPanel = new ChartPanel(attributeName);
        
        // add the charting JPanel to the data view component representing the master view  
        // set the name of the charted attribute as the title
        this.getDvc().addDetailsView(new DataViewComponent.DetailsView(attributeName, null, 10, chartPanel, null), this.getCorner());    
        
    	// link the detail view with its charting JPanel
        registerChart(chartPanel);	
 		
	}
		
}//GEN-LAST:event_displayOnChartActionPerformed
    
    public void setAutoReloadValue(int i){
        String val;
        
        if (i == -1)  {
            val = "off";
        } else  {
            val = Integer.toString(i) + " seconds";
        }
        
        autoReloadValue.setText(val);
    }
    
    public void setMBeanServerConnection(MBeanServerConnection mbsc) {
        if (mbsc != null)  {
            tableModel.setMBeanServerConnection(mbsc);
            tableModel.start();
        }
    }
    
    public MBeanServerConnection getMBeanServerConnection()  {
        if (tableModel != null) {
            return (tableModel.getMBeanServerConnection());
        }
        
        return (null);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel autoReloadLabel;
    private javax.swing.JCheckBoxMenuItem autoReloadStatus;
    private javax.swing.JLabel autoReloadValue;
    private javax.swing.JPopupMenu cellPopup;
    private javax.swing.JTable dataTable;
    private javax.swing.JMenuItem displayOnChart;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPopupMenu tablePopup;
    // End of variables declaration//GEN-END:variables

    public void mqUIEventDispatched(MQUIEvent e) {
        if (e.getType() == MQUIEvent.UPDATE_DATA_LIST)  {
            String newAttrList[] = e.getAttrList();
            tableModel.setAttributes(newAttrList);
            tableModel.fireTableStructureChanged();
            initColumnSizes(dataTable);
        } else if (e.getType() ==MQUIEvent.ITEM_SELECTED) {
            Object obj = e.getSelectedObject();
            handleItemQuery(obj);
        }
    }
    
    // we only adjust the column sizes after the table is first populated
    // (and after mqUIEventDispatched() is called, see above) to avoid annoying the user
    boolean doneOnce = false;
    
    public void tableChanged(TableModelEvent e) {
        int type = e.getType();
        if ((type == TableModelEvent.DELETE) || 
                (type == TableModelEvent.INSERT) || 
                (type == TableModelEvent.UPDATE))  {
        }
        
        if (!doneOnce){
        	initColumnSizes(dataTable);
        	doneOnce=true;
        }
    }
    
    /**
     * Return the tooltip for the column headers
     * 
     * Note that this doesn't configure the tooltip for the attribute names:
     * that is done separately
     * 
     * @param columnIndex
     * @return
     */
    public String getTooltipForColumn(int columnIndex){
    	return "Hold the mouse over an attribute name to see its description";
    }
    
    protected ChartUpdater createChartUpdater(ChartPanel chartPanel){
    	return new MQAttributeListChartUpdater(getTableModel(), chartPanel, chartPanel.getChartedAttribute());
    }
    
	/**
	 * Each instance of this class contains the information needed to update a
	 * particular ChartView
	 */
	private class MQAttributeListChartUpdater implements ChartUpdater {
		MQResourceListTableModel resourceListTableModel;
		ChartPanel chartPanel;
		String chartedAttribute;

		/**
		 * Create a ChartUpdater which will handle updates to the chart
		 * chartPanel
		 * 
		 * The specified chart a single lines, which corresponds to
		 * how the value of the attribute
		 * chartedAttribute in that row changes with time.
		 * 
		 * @param resourceListTableModel
		 * 
		 * @param chartPanel
		 * @param chartedAttribute
		 */
		MQAttributeListChartUpdater(MQResourceListTableModel resourceListTableModel, ChartPanel chartPanel, String chartedAttribute) {
			this.resourceListTableModel = resourceListTableModel;
			this.chartPanel = chartPanel;
			this.chartedAttribute = chartedAttribute;
		}

		public void updateCharts() {
			
			// find out which row of the table represents the attribute we are displaying
			Object attributeValue=null;
			for (int rowIndex = 0; rowIndex < resourceListTableModel.getRowCount(); rowIndex++) {
				String thisAttributeName = (String) resourceListTableModel.getValueAt(rowIndex, 0);
				if (thisAttributeName != null && thisAttributeName.equals(chartedAttribute)) {
					attributeValue=resourceListTableModel.getValueAt(rowIndex, 1);
					break;
				}
			}
					
			// convert to a long
			long longValue;
			if (attributeValue == null) {
				longValue = 0L;
			} else if (attributeValue instanceof Long) {
				longValue = ((Long) attributeValue).longValue();
			} else if (attributeValue instanceof Integer) {
				longValue = ((Integer) attributeValue).longValue();
			} else {
				// TODO What if the attribute is not a long?
				throw new RuntimeException("Need to finish this but to support values of " + attributeValue + " of type "	+ attributeValue.getClass());
			}

			// save the value
			long[] values = new long[1]; 
			values[0]=longValue;

			// finally update the chart
			long timestamp = System.currentTimeMillis();
			chartPanel.addValues(timestamp, values);
			chartPanel.updateDetails(values);
		}
	}
}
