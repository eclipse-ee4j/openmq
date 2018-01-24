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

import com.sun.messaging.jms.management.server.ConsumerInfo;
import com.sun.messaging.jms.management.server.ConsumerOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class ConsumerMonitorConsumerList extends SingleMBeanResourceList {

    public ConsumerMonitorConsumerList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
    	ConsumerInfo.CONSUMER_ID, // Primary attribute
    	ConsumerInfo.DESTINATION_NAME,
    	ConsumerInfo.NUM_MSGS
    };
   
    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }
    
    // copied from com.sun.messaging.jmq.jmsserver.management.util.ConsumerUtil
    final String[] completeAttrsList = {
            ConsumerInfo.ACKNOWLEDGE_MODE,
            ConsumerInfo.ACKNOWLEDGE_MODE_LABEL,
            ConsumerInfo.CLIENT_ID,
            ConsumerInfo.CONNECTION_ID,
            ConsumerInfo.CONSUMER_ID,
            ConsumerInfo.CREATION_TIME,
            ConsumerInfo.DESTINATION_NAME,
            ConsumerInfo.DESTINATION_NAMES,
            ConsumerInfo.DESTINATION_TYPE,
            ConsumerInfo.DURABLE,
            ConsumerInfo.DURABLE_ACTIVE,
            ConsumerInfo.DURABLE_NAME,
            ConsumerInfo.FLOW_PAUSED,
            ConsumerInfo.HOST,
            ConsumerInfo.LAST_ACK_TIME,
            ConsumerInfo.NUM_MSGS,
            ConsumerInfo.NUM_MSGS_PENDING,
            ConsumerInfo.NUM_MSGS_PENDING_ACKS,
            ConsumerInfo.SELECTOR,
            ConsumerInfo.SERVICE_NAME,
            ConsumerInfo.USER,
            ConsumerInfo.WILDCARD,
            ConsumerInfo.NEXT_MESSAGE_ID
    };

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    } 
    
	@Override
	public String getPrimaryAttribute() {
		return ConsumerInfo.CONSUMER_ID;
	}

    @Override
	protected String getManagerMBeanName(){
    	return MQObjectName.CONSUMER_MANAGER_MONITOR_MBEAN_NAME;
    }
    
    @Override
	protected String getGetSubitemInfoOperationName(){
    	return ConsumerOperations.GET_CONSUMER_INFO;
    }
   
    @Override
	protected String getSubitemIdName(){
    	return ConsumerInfo.CONSUMER_ID;
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
