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

import com.sun.messaging.jms.management.server.BrokerClusterInfo;
import com.sun.messaging.jms.management.server.ClusterOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class ClusterMonitorBrokerList extends SingleMBeanResourceList {

    public ClusterMonitorBrokerList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
        BrokerClusterInfo.ID,
        BrokerClusterInfo.ADDRESS, // Primary attribute
        BrokerClusterInfo.STATE,
        BrokerClusterInfo.STATE_LABEL,
        BrokerClusterInfo.NUM_MSGS,
        BrokerClusterInfo.TAKEOVER_BROKER_ID,
        BrokerClusterInfo.STATUS_TIMESTAMP
    };
   
    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }
    
    final String[] completeAttrsList = {
            BrokerClusterInfo.ID,
            BrokerClusterInfo.ADDRESS,
            BrokerClusterInfo.STATE,
            BrokerClusterInfo.STATE_LABEL,
            BrokerClusterInfo.NUM_MSGS,
            BrokerClusterInfo.TAKEOVER_BROKER_ID,
            BrokerClusterInfo.STATUS_TIMESTAMP
    };

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }      
    
	@Override
	public String getPrimaryAttribute() {
		return BrokerClusterInfo.ADDRESS;
	}

    @Override
	protected String getManagerMBeanName(){
    	return MQObjectName.CLUSTER_MONITOR_MBEAN_NAME;
    }
    
    @Override
	protected String getGetSubitemInfoOperationName(){
    	return ClusterOperations.GET_BROKER_INFO;
    }
   
    @Override
	protected String getSubitemIdName(){
    	return BrokerClusterInfo.ADDRESS;
    }
    
    @Override
    public void handleItemQuery(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public int getCorner() {
		return DataViewComponent.BOTTOM_RIGHT;
	}


    
}
