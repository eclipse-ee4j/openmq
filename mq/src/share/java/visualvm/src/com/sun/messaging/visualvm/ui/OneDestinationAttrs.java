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

import java.io.IOException;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.openide.util.Exceptions;

import com.sun.messaging.visualvm.chart.ChartPanel;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 *
 * @author isa
 */
@SuppressWarnings("serial")
public class OneDestinationAttrs extends MQResourceList {

    private ObjectName destinationObjectName = null;
    private static String completeAttrsList[] = {
        "AvgNumActiveConsumers",
        "AvgNumBackupConsumers",
        "AvgNumConsumers",
        "AvgNumMsgs",
        "AvgTotalMsgBytes",
        "ConnectionID",
        "CreatedByAdmin",
        "DiskReserved",
        "DiskUsed",
        "DiskUtilizationRatio",
        "MsgBytesIn",
        "MsgBytesOut",
        "Name",
        "NumActiveConsumers",
        "NumBackupConsumers",
        "NumConsumers",
        "NumMsgs",
        "NumMsgsHeldInTransaction",
        "NumMsgsIn",
        "NumMsgsOut",
        "NumMsgsPendingAcks",
        "NumProducers",
        "NumWildcards",
        "NumWildcardConsumers",
        "NumWildcardProducers",
        "PeakMsgBytes",
        "PeakNumActiveConsumers",
        "PeakNumBackupConsumers",
        "PeakNumConsumers",
        "PeakNumMsgs",
        "PeakTotalMsgBytes",
        "State",
        "StateLabel",
        "Temporary",
        "TotalMsgBytes",
        "TotalMsgBytesHeldInTransaction",
        "Type",
        "NumMsgsRemote",
        "TotalMsgBytesRemote"
    };
    private static String initialDisplayedAttrsList[] = {
        "Name", // Primary attribute
        "Type",
        "NumMsgs"
    };

    /** Creates new form MQResourceList */
    public OneDestinationAttrs(DataViewComponent dvc) {
        super(dvc);
    }

    public ObjectName getDestinationObjectName() {
        return destinationObjectName;
    }

    public void setDestinationObjectName(ObjectName destinationObjectName) {
        this.destinationObjectName = destinationObjectName;
    }

    public void initTableModel() {
        if (getTableModel() == null) {
            MQResourceListTableModel tm = new MQResourceListTableModel() {

                @Override
                public List loadData() {
                    List list = null;
                    AttributeList attrList = null;

                    if ((getMBeanServerConnection() == null) || (getDestinationObjectName() == null)) {
                        return null;
                    }

                    try {
                        attrList = getMBeanServerConnection().getAttributes(getDestinationObjectName(), getAttributes());
                        list = attrList;
                    } catch (InstanceNotFoundException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (ReflectionException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }

                    return list;
                }

                @Override
                public Object getDataValueAt(List l, int row, int col) {
                    String value = null;

                    if ((getMBeanServerConnection() == null) || (getDestinationObjectName() == null)) {
                        return null;
                    }

                    AttributeList attrList = (AttributeList) l;
                    Attribute attr = (Attribute) attrList.get(row);

                    if (col == 0) {
                        value = attr.getName();
                    } else if (col == 1) {
                        Object obj = attr.getValue();
                        if (obj != null) {
                            value = obj.toString();
                        }
                    }
                    return value;
                }

				@Override
				public void updateCharts() {
					updateRegisteredCharts();
				}
            };
            tm.setAttributes(getinitialDisplayedAttrsList());
            tm.setAttrsInColumn(false);
            setTableModel(tm);
        }
    }

    public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }
    
	@Override
	public String getPrimaryAttribute() {
		return "Name";
	}

    public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }

    @Override
    public void handleItemQuery(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	String getTooltipForColumn(int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getCorner() {
		throw new UnsupportedOperationException();
	}
	
    /**
	 * Register for updates to the specified chart
     * @param chartPanel
     */
	public void registerChart(ChartPanel chartPanel) {
		throw new UnsupportedOperationException("Not yet implemented");
	}


}
