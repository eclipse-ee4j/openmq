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
 * @(#)TransactionUtil.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.Vector;
import java.util.Enumeration;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenDataException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.JMQXid;

public class TransactionUtil {
    /*
     * Transaction Info item names for Monitor MBeans
     */
    private static final String[] transactionInfoMonitorItemNames = {
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

    /*
     * Transaction Info item description for Monitor MBeans
     * TBD: use real descriptions
     */
    private static final String[] transactionInfoMonitorItemDesc 
					= transactionInfoMonitorItemNames;

    /*
     * Transaction Info item types for Monitor MBeans
     */
    private static final OpenType[] transactionInfoMonitorItemTypes = {
			    SimpleType.STRING,		// client ID
			    SimpleType.STRING,		// connection string
			    SimpleType.LONG,		// creation time
			    SimpleType.LONG,		// num acks
			    SimpleType.LONG,		// num msgs
			    SimpleType.INTEGER,		// state
			    SimpleType.STRING,		// state label
			    SimpleType.STRING,		// transaction ID
			    SimpleType.STRING,		// user
			    SimpleType.STRING		// xid
                    };

    /*
     * Transaction Info composite type for Monitor MBeans
     */
    private static volatile CompositeType monitorCompType = null;

    public static int toExternalTransactionState(int internalTransactionState)  {
	switch (internalTransactionState)  {
	case TransactionState.CREATED:
	    return (com.sun.messaging.jms.management.server.TransactionState.CREATED);

	case TransactionState.STARTED:
	    return (com.sun.messaging.jms.management.server.TransactionState.STARTED);

	case TransactionState.FAILED:
	    return (com.sun.messaging.jms.management.server.TransactionState.FAILED);

	case TransactionState.INCOMPLETE:
	    return (com.sun.messaging.jms.management.server.TransactionState.INCOMPLETE);

	case TransactionState.COMPLETE:
	    return (com.sun.messaging.jms.management.server.TransactionState.COMPLETE);

	case TransactionState.PREPARED:
	    return (com.sun.messaging.jms.management.server.TransactionState.PREPARED);

	case TransactionState.COMMITTED:
	    return (com.sun.messaging.jms.management.server.TransactionState.COMMITTED);

	case TransactionState.ROLLEDBACK:
	    return (com.sun.messaging.jms.management.server.TransactionState.ROLLEDBACK);

	case TransactionState.TIMED_OUT:
	    return (com.sun.messaging.jms.management.server.TransactionState.TIMED_OUT);

	default:
	    return (-1);

	}
    }

    public static int toInternalTransactionState(int externalTransactionState)  {
	switch (externalTransactionState)  {
	case com.sun.messaging.jms.management.server.TransactionState.CREATED:
	    return (TransactionState.CREATED);

	case com.sun.messaging.jms.management.server.TransactionState.STARTED:
	    return (TransactionState.STARTED);

	case com.sun.messaging.jms.management.server.TransactionState.FAILED:
	    return (TransactionState.FAILED);

	case com.sun.messaging.jms.management.server.TransactionState.INCOMPLETE:
	    return (TransactionState.INCOMPLETE);

	case com.sun.messaging.jms.management.server.TransactionState.COMPLETE:
	    return (TransactionState.COMPLETE);

	case com.sun.messaging.jms.management.server.TransactionState.PREPARED:
	    return (TransactionState.PREPARED);

	case com.sun.messaging.jms.management.server.TransactionState.COMMITTED:
	    return (TransactionState.COMMITTED);

	case com.sun.messaging.jms.management.server.TransactionState.ROLLEDBACK:
	    return (TransactionState.ROLLEDBACK);

	case com.sun.messaging.jms.management.server.TransactionState.TIMED_OUT:
	    return (TransactionState.TIMED_OUT);

	default:
	    return (-1);

	}
    }

    public static String[] getTransactionIDs()  {
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

	    ids[i] = Long.toString(txnID);

	    i++;
	}

	return (ids);
    }

    public static CompositeData[] getTransactionInfo()
				throws BrokerException, OpenDataException  {
	String[] ids = getTransactionIDs();

	if (ids == null)  {
	    return (null);
	}

	CompositeData cds[] = new CompositeData [ ids.length ];

	for (int i = 0; i < ids.length; ++i)  {
	    cds[i] = getTransactionInfo(ids[i]);
	}
	
	return (cds);
    }

    public static CompositeData getTransactionInfo(String transactionID) 
				throws BrokerException, OpenDataException  {
	CompositeData cd = null;
	TransactionUID tid = null;
        BrokerResources	rb = Globals.getBrokerResources();

	if (transactionID == null)  {
	    throw new 
		IllegalArgumentException(rb.getString(rb.X_JMX_NULL_TXN_ID_SPEC));
	}

	long longTid = 0;

	try  {
	    longTid = Long.parseLong(transactionID);
	} catch (NumberFormatException e)  {
	    throw new 
		IllegalArgumentException(rb.getString(rb.X_JMX_INVALID_TXN_ID_SPEC, transactionID));
	}

	tid = new TransactionUID(longTid);

	cd = getTransactionInfo(tid);

	return (cd);
    }

    public static String getClientID(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	TransactionState ts;

	if (tl == null)  {
	    return (null);
	}

	ts = tl.retrieveState(tid);

	if (ts == null)  {
	    return (null);
	}

	return (ts.getClientID());
    }

    public static String getConnectionString(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	TransactionState ts;

	if (tl == null)  {
	    return (null);
	}

	ts = tl.retrieveState(tid);

	if (ts == null)  {
	    return (null);
	}

	return (ts.getConnectionString());
    }

    public static Long getCreationTime(TransactionUID tid)  {
	long currentTime = System.currentTimeMillis();

	return (Long.valueOf(currentTime - tid.age(currentTime)));
    }

    public static Long getNumAcks(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART

	if (tl == null)  {
	    return (null);
	}

	return (Long.valueOf(tl.retrieveNConsumedMessages(tid)));
    }

    public static Long getNumMsgs(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART

	if (tl == null)  {
	    return (null);
	}

	return (Long.valueOf(tl.retrieveNSentMessages(tid)));
    }

    public static Integer getState(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	TransactionState ts;

	if (tl == null)  {
	    return (null);
	}

	ts = tl.retrieveState(tid);

	if (ts == null)  {
	    return (null);
	}

	return (Integer.valueOf(toExternalTransactionState(ts.getState())));
    }

    public static String getStateLabel(TransactionUID tid)  {
	Integer i = getState(tid);

	if (i == null)  {
	    return (null);
	}

	return (com.sun.messaging.jms.management.server.TransactionState.toString(
				i.intValue()));
    }

    public static String getUser(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0]; //PART
	TransactionState ts;

	if (tl == null)  {
	    return (null);
	}

	ts = tl.retrieveState(tid);

	if (ts == null)  {
	    return (null);
	}

	return (ts.getUser());
    }

    public static String getXID(TransactionUID tid)  {
	TransactionList[] tls = Globals.getDestinationList().getTransactionList(null);
        TransactionList tl = tls[0];
	JMQXid xid;

	if (tl == null)  {
	    return (null);
	}

	xid = tl.UIDToXid(tid);

	if (xid == null)  {
	    return (null);
	}

	return (xid.toString());
    }


    private static CompositeData getTransactionInfo(TransactionUID tid) 
						throws OpenDataException  {
	Object[] transactionInfoMonitorItemValues = {
                            getClientID(tid),
                            getConnectionString(tid),
                            getCreationTime(tid),
                            getNumAcks(tid),
                            getNumMsgs(tid),
                            getState(tid),
                            getStateLabel(tid),
	                    Long.toString(tid.longValue()),
                            getUser(tid),
                            getXID(tid)
			};
	CompositeData cd = null;

        if (monitorCompType == null)  {
            monitorCompType = new CompositeType("TransactionMonitorInfo", "TransactionMonitorInfo", 
                        transactionInfoMonitorItemNames, transactionInfoMonitorItemDesc, 
				transactionInfoMonitorItemTypes);
        }

	cd = new CompositeDataSupport(monitorCompType, 
			transactionInfoMonitorItemNames, transactionInfoMonitorItemValues);
	
	return (cd);
    }
}
