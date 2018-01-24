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
import com.sun.messaging.jms.management.server.TransactionInfo;
import com.sun.messaging.jms.management.server.TransactionOperations;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;


@SuppressWarnings("serial")
public class TransactionMonitorTransactionList extends SingleMBeanResourceList {

    public TransactionMonitorTransactionList(DataViewComponent dvc) {
		super(dvc);
	}

	private static String initialDisplayedAttrsList[] = {
        TransactionInfo.TRANSACTION_ID,
        TransactionInfo.STATE_LABEL,
        TransactionInfo.USER,
        TransactionInfo.NUM_ACKS,
        TransactionInfo.CREATION_TIME
    };

    @Override
	public String[] getinitialDisplayedAttrsList() {
        return initialDisplayedAttrsList;
    }
    
    // copied from com.sun.messaging.jmq.jmsserver.management.util.TransactionUtil
    private static final String[] completeAttrsList = {
            TransactionInfo.CLIENT_ID,
            TransactionInfo.CONNECTION_STRING,
            TransactionInfo.CREATION_TIME,
            TransactionInfo.NUM_ACKS,
            TransactionInfo.NUM_MSGS,
            TransactionInfo.STATE,
            TransactionInfo.STATE_LABEL,
            TransactionInfo.TRANSACTION_ID,
            TransactionInfo.USER,
            TransactionInfo.XID
    };   

    @Override
	public String[] getCompleteAttrsList() {
        return completeAttrsList;
    }
    
	@Override
	public String getPrimaryAttribute() {
		return TransactionInfo.TRANSACTION_ID;
	}

    @Override
	protected String getManagerMBeanName(){
    	return MQObjectName.TRANSACTION_MANAGER_MONITOR_MBEAN_NAME;
    }
    
    @Override
	protected String getGetSubitemInfoOperationName(){
    	return TransactionOperations.GET_TRANSACTION_INFO;
    }
   
    @Override
	protected String getSubitemIdName(){
    	return TransactionInfo.TRANSACTION_ID;
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
