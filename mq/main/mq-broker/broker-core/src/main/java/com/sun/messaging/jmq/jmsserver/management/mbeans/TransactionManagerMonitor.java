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

/*
 * @(#)TransactionManagerMonitor.java	1.16 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Vector;
import java.util.Enumeration;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;

import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;

import com.sun.messaging.jmq.jmsserver.management.util.TransactionUtil;

public class TransactionManagerMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(TransactionAttributes.NUM_TRANSACTIONS,
					Integer.class.getName(),
					mbr.getString(mbr.I_TXN_MGR_ATTR_NUM_TRANSACTIONS),
					true,
					false,
					false),

	    new MBeanAttributeInfo(TransactionAttributes.NUM_TRANSACTIONS_COMMITTED,
					Long.class.getName(),
					mbr.getString(mbr.I_TXN_MGR_ATTR_NUM_TRANSACTIONS_COMMITTED),
					true,
					false,
					false),

	    new MBeanAttributeInfo(TransactionAttributes.NUM_TRANSACTIONS_ROLLBACK,
					Long.class.getName(),
					mbr.getString(mbr.I_TXN_MGR_ATTR_NUM_TRANSACTIONS_ROLLBACK),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] getTransactionInfoByIDSignature = {
		    new MBeanParameterInfo("transactionID", String.class.getName(), 
			mbr.getString(mbr.I_TXN_MGR_OP_PARAM_TXN_ID))
			    };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(TransactionOperations.GET_TRANSACTION_IDS,
		mbr.getString(mbr.I_TXN_MGR_OP_GET_TRANSACTION_IDS),
		    null , 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(TransactionOperations.GET_TRANSACTION_INFO,
		mbr.getString(mbr.I_TXN_MGR_OP_GET_TRANSACTION_INFO),
		    null , 
		    CompositeData[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(TransactionOperations.GET_TRANSACTION_INFO_BY_ID,
		mbr.getString(mbr.I_TXN_MGR_OP_GET_TRANSACTION_INFO_BY_ID),
		    getTransactionInfoByIDSignature, 
		    CompositeData.class.getName(),
		    MBeanOperationInfo.INFO)
		};
	
    private static String[] txnNotificationTypes = {
		    TransactionNotification.TRANSACTION_COMMIT,
		    TransactionNotification.TRANSACTION_PREPARE,
		    TransactionNotification.TRANSACTION_ROLLBACK
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    txnNotificationTypes,
		    TransactionNotification.class.getName(),
		    mbr.getString(mbr.I_TXN_NOTIFICATIONS)
		    )
		};

    private long numTransactionsCommitted = 0;
    private long numTransactionsRollback = 0;

    public TransactionManagerMonitor()  {
	super();
    }

    public Integer getNumTransactions()  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	Vector transactions = tl.getTransactions(-1);

	return (Integer.valueOf(transactions.size()));
    }

    public Long getNumTransactionsCommitted()  {
	return (Long.valueOf(numTransactionsCommitted));
    }

    public Long getNumTransactionsRollback()  {
	return (Long.valueOf(numTransactionsRollback));
    }

    public void resetMetrics()  {
        numTransactionsCommitted = 0;
        numTransactionsRollback = 0;
    }

    public String[] getTransactionIDs() throws MBeanException  {
	return (TransactionUtil.getTransactionIDs());
    }

    public CompositeData[] getTransactionInfo() throws MBeanException {
	CompositeData cds[] = null;

	try  {
	    cds = TransactionUtil.getTransactionInfo();
	} catch(Exception e)  {
	    handleOperationException(TransactionOperations.GET_TRANSACTION_INFO, e);
	}

	return (cds);
    }

    public CompositeData getTransactionInfoByID(String transactionID) throws MBeanException  {
	CompositeData cd = null;

	try  {
	    cd = TransactionUtil.getTransactionInfo(transactionID);
	} catch(Exception e)  {
	    handleOperationException(TransactionOperations.GET_TRANSACTION_INFO_BY_ID, e);
	}

	return (cd);
    }


    public String getMBeanName()  {
	return ("TransactionManagerMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_TXN_MGR_MON_DESC));
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void notifyTransactionCommit(long id)  {
	TransactionNotification n;
	n = new TransactionNotification(TransactionNotification.TRANSACTION_COMMIT, 
			this, sequenceNumber++);
	n.setTransactionID(Long.toString(id));

	sendNotification(n);

        numTransactionsCommitted++;
    }

    public void notifyTransactionPrepare(long id)  {
	TransactionNotification n;
	n = new TransactionNotification(TransactionNotification.TRANSACTION_PREPARE, 
			this, sequenceNumber++);
	n.setTransactionID(Long.toString(id));

	sendNotification(n);
    }

    public void notifyTransactionRollback(long id)  {
	TransactionNotification n;
	n = new TransactionNotification(TransactionNotification.TRANSACTION_ROLLBACK, 
			this, sequenceNumber++);
	n.setTransactionID(Long.toString(id));

	sendNotification(n);

        numTransactionsRollback++;
    }
}
