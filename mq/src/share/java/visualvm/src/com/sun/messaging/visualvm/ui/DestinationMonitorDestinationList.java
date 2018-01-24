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

import java.awt.Frame;

import javax.swing.SwingUtilities;

import com.sun.messaging.jms.management.server.DestinationAttributes;
import com.sun.messaging.jms.management.server.DestinationOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class DestinationMonitorDestinationList extends MultipleMBeanResourceList {
	
    public DestinationMonitorDestinationList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
    	DestinationAttributes.NAME, // Primary attribute
    	DestinationAttributes.TYPE,
    	DestinationAttributes.NUM_MSGS
    };
    
    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }    
    
    // copied from com.sun.messaging.jmq.jmsserver.management.mbeans.DestinationMonitor
    private String completeAttrsList[] = {
    	    DestinationAttributes.AVG_NUM_ACTIVE_CONSUMERS,
    	    DestinationAttributes.AVG_NUM_BACKUP_CONSUMERS,
    	    DestinationAttributes.AVG_NUM_CONSUMERS,
    	    DestinationAttributes.AVG_NUM_MSGS,
    	    DestinationAttributes.AVG_TOTAL_MSG_BYTES,
    	    DestinationAttributes.CONNECTION_ID,
    	    DestinationAttributes.CREATED_BY_ADMIN,
    	    DestinationAttributes.DISK_RESERVED,
    	    DestinationAttributes.DISK_USED,
    	    DestinationAttributes.DISK_UTILIZATION_RATIO,
    	    DestinationAttributes.MSG_BYTES_IN,
    	    DestinationAttributes.MSG_BYTES_OUT,        
    	    DestinationAttributes.NAME,
    	    DestinationAttributes.NUM_ACTIVE_CONSUMERS,
    	    DestinationAttributes.NUM_BACKUP_CONSUMERS,
    	    DestinationAttributes.NUM_CONSUMERS,
    	    DestinationAttributes.NUM_WILDCARDS,
    	    DestinationAttributes.NUM_WILDCARD_CONSUMERS,
    	    DestinationAttributes.NUM_WILDCARD_PRODUCERS,
    	    DestinationAttributes.NUM_MSGS,
    	    DestinationAttributes.NUM_MSGS_REMOTE,
    	    DestinationAttributes.NUM_MSGS_HELD_IN_TRANSACTION,
    	    DestinationAttributes.NUM_MSGS_IN,
    	    DestinationAttributes.NUM_MSGS_OUT,
    	    DestinationAttributes.NUM_MSGS_PENDING_ACKS,
    	    DestinationAttributes.NUM_PRODUCERS,
    	    DestinationAttributes.PEAK_MSG_BYTES,
    	    DestinationAttributes.PEAK_NUM_ACTIVE_CONSUMERS,
    	    DestinationAttributes.PEAK_NUM_BACKUP_CONSUMERS,
    	    DestinationAttributes.PEAK_NUM_CONSUMERS,
    	    DestinationAttributes.PEAK_NUM_MSGS,
    	    DestinationAttributes.PEAK_TOTAL_MSG_BYTES,
    	    DestinationAttributes.NEXT_MESSAGE_ID,
    	    DestinationAttributes.STATE,
    	    DestinationAttributes.STATE_LABEL,
    	    DestinationAttributes.TEMPORARY,
    	    DestinationAttributes.TOTAL_MSG_BYTES,
    	    DestinationAttributes.TOTAL_MSG_BYTES_REMOTE,
    	    DestinationAttributes.TOTAL_MSG_BYTES_HELD_IN_TRANSACTION,
    	    DestinationAttributes.TYPE
   };    

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }        
    
	@Override
	public String getPrimaryAttribute() {
		return "Name";
	}
    
	@Override
	protected String getManagerMBeanName() {
		return MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME;
	}

	@Override
	protected String getGetSubMbeanOperationName(){
		return DestinationOperations.GET_DESTINATIONS;
	}

    @Override
    public void handleItemQuery(Object obj) {
        QueryDestinationDialog qdd = new QueryDestinationDialog((Frame) SwingUtilities.getWindowAncestor(this), false);
        
        
        RowData rowData = (RowData)obj;
        qdd.setDestinationObjectName(rowData.getObjectName());
        qdd.setMBeanServerConnection(getMBeanServerConnection());
        qdd.setLocationRelativeTo((Frame) SwingUtilities.getWindowAncestor(this));
        
        qdd.setVisible(true);
    }

	@Override
	public int getCorner() {
		return DataViewComponent.BOTTOM_LEFT;
	}
}
