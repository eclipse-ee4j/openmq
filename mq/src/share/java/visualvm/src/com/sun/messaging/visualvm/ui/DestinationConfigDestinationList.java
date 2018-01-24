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
public class DestinationConfigDestinationList extends MultipleMBeanResourceList {
	
    public DestinationConfigDestinationList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
		DestinationAttributes.NAME, // Primary Attribute
		DestinationAttributes.TYPE,
		DestinationAttributes.MAX_TOTAL_MSG_BYTES,
		DestinationAttributes.LIMIT_BEHAVIOR,
    };
    
    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }    
    
    // copied from com.sun.messaging.jmq.jmsserver.management.mbeans.DestinationConfig
    private String completeAttrsList[] = {
    		DestinationAttributes.NAME,
    		DestinationAttributes.TYPE,
    		DestinationAttributes.MAX_TOTAL_MSG_BYTES,
    		DestinationAttributes.LIMIT_BEHAVIOR,
    		DestinationAttributes.CONSUMER_FLOW_LIMIT,
    		DestinationAttributes.LOCAL_ONLY,
    		DestinationAttributes.LOCAL_DELIVERY_PREFERRED,
    		DestinationAttributes.MAX_BYTES_PER_MSG,
    		DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS,
    		DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS,
    		DestinationAttributes.MAX_NUM_MSGS,
    		DestinationAttributes.MAX_NUM_PRODUCERS,
    		DestinationAttributes.USE_DMQ,
    		DestinationAttributes.VALIDATE_XML_SCHEMA_ENABLED,
    		DestinationAttributes.XML_SCHEMA_URI_LIST,
    		DestinationAttributes.RELOAD_XML_SCHEMA_ON_FAILURE,
   };    

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }        
    
	@Override
	public String getPrimaryAttribute() {
		return DestinationAttributes.NAME;
	}
    
	@Override
	protected String getManagerMBeanName() {
		return MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME;
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
		return DataViewComponent.BOTTOM_RIGHT;
	}

}
