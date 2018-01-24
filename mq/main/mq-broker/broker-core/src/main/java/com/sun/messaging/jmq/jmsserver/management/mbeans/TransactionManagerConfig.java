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
 * @(#)TransactionManagerConfig.java	1.15 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Enumeration;
import java.util.Vector;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;

import javax.transaction.xa.XAResource;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.RollbackReason;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.util.JMQXid;

import com.sun.messaging.jms.management.server.*;

public class TransactionManagerConfig extends MQMBeanReadWrite  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(TransactionAttributes.NUM_TRANSACTIONS,
					Integer.class.getName(),
					mbr.getString(mbr.I_TXN_MGR_ATTR_NUM_TRANSACTIONS),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] txnIdParam = {
		    new MBeanParameterInfo("transactionID", String.class.getName(),
					mbr.getString(mbr.I_TXN_MGR_OP_PARAM_TXN_ID))
			    };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(TransactionOperations.COMMIT,
		mbr.getString(mbr.I_TXN_MGR_OP_COMMIT),
		    txnIdParam, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(TransactionOperations.GET_TRANSACTION_IDS,
		mbr.getString(mbr.I_TXN_MGR_OP_GET_TRANSACTION_IDS),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(TransactionOperations.ROLLBACK,
		mbr.getString(mbr.I_TXN_MGR_OP_ROLLBACK),
		    txnIdParam, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),
		};

    public TransactionManagerConfig()  {
	super();
    }

    public Integer getNumTransactions()  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	Vector transactions = tl.getTransactions(-1);

	return (Integer.valueOf(transactions.size()));
    }

    public void commit(String transactionID) throws MBeanException  {
	doRollbackCommit(transactionID, false);
    }

    public String[] getTransactionIDs() throws MBeanException  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	Vector transactions = tl.getTransactions(-1);
	String ids[];

	if ((transactions == null) || (transactions.size() == 0))  {
	    return (null);
	}

	ids = new String [ transactions.size() ];

	Enumeration e = transactions.elements();

	int i = 0;
	while (e.hasMoreElements()) {
	    TransactionUID tid = (TransactionUID)e.nextElement();
	    long		txnID = tid.longValue();
	    String id;

	    try  {
	        id = Long.toString(txnID);

	        ids[i] = id;
	    } catch (Exception ex)  {
		handleOperationException(TransactionOperations.GET_TRANSACTION_IDS, ex);
	    }

	    i++;
	}

	return (ids);
    }

    public void rollback(String transactionID) throws MBeanException  {
	doRollbackCommit(transactionID, true);
    }

    public void doRollbackCommit(String transactionID, boolean rollback) 
				throws MBeanException  {
	try  {
	    long longTid = 0;

	    if (transactionID == null)  {
	        throw new Exception("Null transaction ID");
	    }

	    try  {
		longTid = Long.parseLong(transactionID);
	    } catch (Exception e)  {
	        throw new Exception("Invalid transaction ID: " + transactionID);
	    }

	    TransactionUID tid = new TransactionUID(longTid);
	    TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
            TransactionList tl = null;
	    TransactionState ts = null;
            for (int i = 0; i < tls.length; i++) {
                 tl = tls[i]; 
	         if (tl == null)  {
                     continue;
	         }
	         ts = tl.retrieveState(tid);
	         if (ts == null)  {
                     continue;
                 }
                 break;
            }

	    if (ts == null)  {
	        throw new Exception(rb.getString(rb.E_NO_SUCH_TRANSACTION, tid));
	    }

	    if (ts.getState() != TransactionState.PREPARED)  {
	        throw new Exception(rb.getString(rb.E_TRANSACTION_NOT_PREPARED, tid));
	    }

	    JMQXid xid = tl.UIDToXid(tid);

	    if (xid == null) {
	        throw new Exception(rb.getString(rb.E_INTERNAL_BROKER_ERROR, 
				"Could not find Xid for " + tid));
	    }

	    PacketRouter pr = Globals.getPacketRouter(0);

	    if (pr == null)  {
	        throw new Exception(rb.getString(rb.E_INTERNAL_BROKER_ERROR,
					"Could not locate Packet Router"));
	    }

	    TransactionHandler thandler = (TransactionHandler)
	    			pr.getHandler(PacketType.ROLLBACK_TRANSACTION);

	    if (thandler == null)  {
	        throw new Exception(rb.getString(rb.E_INTERNAL_BROKER_ERROR,
					"Could not locate Transaction Handler"));
	    }

	    if (rollback)  {
	        thandler.doRollback(tl, tid, xid, null,
                                    ts, null, null, RollbackReason.ADMIN);
	    } else  {
		thandler.doCommit(tl, tid, xid, 
                                  Integer.valueOf(XAResource.TMNOFLAGS), 
                                  ts, null, false, null, null);
	    }
	} catch(Exception e)  {
	    String opName;
	    if (rollback)  {
		opName = TransactionOperations.ROLLBACK;
	    } else  {
		opName = TransactionOperations.COMMIT;
	    }

	    handleOperationException(opName, e);
	}
    }


    public String getMBeanName()  {
	return ("TransactionManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_TXN_MGR_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (null);
    }
}
