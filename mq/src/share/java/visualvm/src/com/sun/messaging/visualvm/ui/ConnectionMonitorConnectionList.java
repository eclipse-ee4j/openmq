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

import com.sun.messaging.jms.management.server.ConnectionAttributes;
import com.sun.messaging.jms.management.server.ConnectionOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

@SuppressWarnings("serial")
public class ConnectionMonitorConnectionList extends MultipleMBeanResourceList {

    public ConnectionMonitorConnectionList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
    	ConnectionAttributes.CONNECTION_ID, // Primary Attribute
    	ConnectionAttributes.SERVICE_NAME,
	    ConnectionAttributes.CLIENT_ID,
	    ConnectionAttributes.HOST,
	    ConnectionAttributes.PORT,
    	ConnectionAttributes.USER,
	    ConnectionAttributes.NUM_CONSUMERS,
    	ConnectionAttributes.NUM_PRODUCERS
    };
    
    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }
    
    // copied from com.sun.messaging.jmq.jmsserver.management.mbeans.ConnectionMonitor
    private String completeAttrsList[] = {
	    ConnectionAttributes.CLIENT_ID,
	    ConnectionAttributes.CLIENT_PLATFORM,
	    ConnectionAttributes.CONNECTION_ID,
	    ConnectionAttributes.CREATION_TIME,
	    ConnectionAttributes.HOST,
	    ConnectionAttributes.NUM_CONSUMERS,
	    ConnectionAttributes.NUM_PRODUCERS,
	    ConnectionAttributes.PORT,
	    ConnectionAttributes.SERVICE_NAME,
	    ConnectionAttributes.USER
   };    

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }    
    
	@Override
	public String getPrimaryAttribute() {
		return "ConnectionID";
	}
    
	@Override
	protected String getManagerMBeanName() {
		return MQObjectName.CONNECTION_MANAGER_MONITOR_MBEAN_NAME;
	}

	@Override
	protected String getGetSubMbeanOperationName(){
		return ConnectionOperations.GET_CONNECTIONS;
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
