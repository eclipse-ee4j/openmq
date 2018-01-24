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

import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.messaging.jms.management.server.ProducerInfo;
import com.sun.messaging.jms.management.server.ProducerOperations;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class ProducerMonitorProducerList extends SingleMBeanResourceList {

    public ProducerMonitorProducerList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
    	ProducerInfo.PRODUCER_ID, // Primary attribute
    	ProducerInfo.DESTINATION_NAME,
    	ProducerInfo.NUM_MSGS
    };

    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }
    
    // copied from com.sun.messaging.jmq.jmsserver.management.util.ProducerUtil
    final String[] completeAttrsList = {
            ProducerInfo.CONNECTION_ID,
            ProducerInfo.CREATION_TIME,
            ProducerInfo.DESTINATION_NAME,
            ProducerInfo.DESTINATION_NAMES,
            ProducerInfo.DESTINATION_TYPE,
            ProducerInfo.FLOW_PAUSED,
            ProducerInfo.HOST,
            ProducerInfo.NUM_MSGS,
            ProducerInfo.PRODUCER_ID,
            ProducerInfo.SERVICE_NAME,
            ProducerInfo.USER,
            ProducerInfo.WILDCARD
    };

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }    
    
	@Override
	public String getPrimaryAttribute() {
		return ProducerInfo.PRODUCER_ID;
	}

    @Override
	protected String getManagerMBeanName(){
    	return MQObjectName.PRODUCER_MANAGER_MONITOR_MBEAN_NAME;
    }
    
    @Override
	protected String getGetSubitemInfoOperationName(){
    	return ProducerOperations.GET_PRODUCER_INFO;
    }
   
    @Override
	protected String getSubitemIdName(){
    	return ProducerInfo.PRODUCER_ID;
    }

    @Override
    public void handleItemQuery(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public int getCorner() {
		return DataViewComponent.BOTTOM_LEFT;
	}

}
