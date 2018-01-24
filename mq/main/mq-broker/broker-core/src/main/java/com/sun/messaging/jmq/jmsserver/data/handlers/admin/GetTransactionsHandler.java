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
 * @(#)GetTransactionsHandler.java	1.15 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;
import java.util.List;
import java.util.Enumeration;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.Globals;

public class GetTransactionsHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    /*
     * Transaction types
     */
    private static int LOCAL   = 0;
    private static int CLUSTER    = 1;
    private static int REMOTE   = 2;
    private static int UNKNOWN  = -1;

    public GetTransactionsHandler(AdminDataHandler parent) {
	super(parent);
    }

    public static Hashtable getTransactionInfo(TransactionList tl,
                                               TransactionUID id,
					       int type) {
        return getTransactionInfo(tl, id, type, false);
    }

    private static Hashtable getTransactionInfo(TransactionList tl,
                                                TransactionUID id,
                                                int type, boolean showpartition) {
                                               
	Logger logger = Globals.getLogger();
        TransactionState ts = tl.retrieveState(id);

	if (type == LOCAL) {
            ts = tl.retrieveState(id, true);
	} else if (type == CLUSTER)  {
            ts = tl.retrieveState(id, true);
	} else if (type == REMOTE)  {
            ts = tl.getRemoteTransactionState(id);
	}

        if (ts == null) return null;

        JMQXid xid = tl.UIDToXid(id);

        Hashtable table = new Hashtable();

	table.put("type", Integer.valueOf(type));

        if (xid != null) {
            table.put("xid", xid.toString());
        }

        PartitionedStore pstore = tl.getPartitionedStore();
        table.put("txnid", Long.valueOf(id.longValue())+
                  (showpartition ? "["+pstore.getPartitionID()+
                   (pstore.isPrimaryPartition() ? "*]":"]"):""));
        if (ts.getUser() != null)
            table.put("user", ts.getUser());

        if (ts.getClientID() != null) {
            table.put("clientid", ts.getClientID());
        }
        table.put("timestamp",
                Long.valueOf(System.currentTimeMillis() - id.age()));
        table.put("connection", ts.getConnectionString());

        table.put("nmsgs", Integer.valueOf(tl.retrieveNSentMessages(id)));
        if (type != REMOTE) {
            table.put("nacks", Integer.valueOf(tl.retrieveNConsumedMessages(id)));
        } else {
            table.put("nacks", Integer.valueOf(tl.retrieveNRemoteConsumedMessages(id)));
        }
        table.put("state", Integer.valueOf(ts.getState()));

	ConnectionUID cuid = ts.getConnectionUID();
	if (cuid != null)  {
	    table.put("connectionid", Long.valueOf(cuid.longValue()));
	}

	TransactionBroker homeBroker = tl.getRemoteTransactionHomeBroker(id);
	String homeBrokerStr = "";
	if (homeBroker != null)  {
	    homeBrokerStr = homeBroker.getBrokerAddress().toString();
	}
        table.put("homebroker", homeBrokerStr);

	    TransactionBroker brokers[] = null;

        if (type != REMOTE) { 
            try {
                brokers = tl.getClusterTransactionBrokers(id);
            } catch (BrokerException be)  {
                logger.log(Logger.WARNING, 
                "Exception caught while obtaining list of brokers in transaction", be);
            }
        }

	    StringBuffer allBrokers = new StringBuffer();
            StringBuffer pendingBrokers = new StringBuffer();

	    if (brokers != null)  {
		for  (int i = 0; i < brokers.length; ++i)  {
	            TransactionBroker oneBroker = brokers[i];
		    
		    BrokerAddress addr = oneBroker.getBrokerAddress();

		    if (allBrokers.length() != 0)  {
		        allBrokers.append(", ");
		    }
		    allBrokers.append(addr.toString());

		    if (oneBroker.isCompleted())  {
			continue;
		    }

		    if (pendingBrokers.length() != 0)  {
		        pendingBrokers.append(", ");
		    }
		    pendingBrokers.append(addr.toString());
		}
	    }

	    table.put("allbrokers", allBrokers.toString());
	    table.put("pendingbrokers", pendingBrokers.toString());

        return table;
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

	if ( DEBUG ) {
            logger.log(Logger.INFO, this.getClass().getName() + ": " +
                            "Getting transactions " + cmd_props);
        }

        int status = Status.OK;
	String errMsg = null;
        TransactionUID tid = null;

	Long id = (Long)cmd_props.get(MessageType.JMQ_TRANSACTION_ID);
        Boolean val = (Boolean)cmd_props.get(MessageType.JMQ_SHOW_PARTITION);
        boolean showpartition = (val == null ? false:val.booleanValue());

        if (id != null) {
            tid = new TransactionUID(id.longValue());
        }

	Vector v = new Vector();

        try {
        TransactionList tl = null;
        Hashtable table = null;

        if (tid != null) {
            Object[] oo = TransactionList.getTransListAndState(tid, null, true, false);
            if (oo == null) {
                oo = TransactionList.getTransListAndRemoteState(tid);
            }
            if (oo == null) {
                throw new BrokerException(
                rb.getKString(rb.X_TXN_NOT_FOUND, tid), Status.NOT_FOUND);
            }
            tl = (TransactionList)oo[0];

            // Get info for one transaction
	    int type = UNKNOWN;

	    if (tl.isRemoteTransaction(tid))  {
		type = REMOTE;
	    } else if (tl.isClusterTransaction(tid))  {
		type = CLUSTER;
	    } else if (tl.isLocalTransaction(tid))  {
		type = LOCAL;
	    }

            table = getTransactionInfo(tl, tid, type, showpartition);
            if (table != null) {
                v.add(table);
            }
        } else {
            TransactionList[] tls = DL.getTransactionList(null);
            for (int i = 0; i < tls.length; i++) {
                tl = tls[i];

                // Get info for all transactions (-1 means get transactions
                // no matter what the state).

                TransactionUID ttid = null;  
                Vector transactions = tl.getTransactions(-1);
                if (transactions != null) {
                    Enumeration e = transactions.elements();
                    while (e.hasMoreElements()) {
                        ttid = (TransactionUID)e.nextElement();
                        table = getTransactionInfo(tl, ttid, LOCAL, showpartition);
                        if (table != null) {
                            v.add(table);
                        }
                    }
                }

                transactions = tl.getClusterTransactions(-1);
                if (transactions != null) {
                    Enumeration e = transactions.elements();
                    while (e.hasMoreElements()) {
                        ttid = (TransactionUID)e.nextElement();
                        table = getTransactionInfo(tl, ttid, CLUSTER, showpartition);
                        if (table != null) {
                            v.add(table);
                        }
                    }
                }

                transactions = tl.getRemoteTransactions(-1);
                if (transactions != null) {
                    Enumeration e = transactions.elements();
                    while (e.hasMoreElements()) {
                        ttid = (TransactionUID)e.nextElement();
                        table = getTransactionInfo(tl, ttid, REMOTE, showpartition);
                        if (table != null) {
                            v.add(table);
                        }
                    }
                }
            }
        }

        } catch (Exception e) {
        errMsg = e.getMessage();
        status = Status.ERROR;
        if (e instanceof BrokerException) {
            status = ((BrokerException)e).getStatusCode();
        }
        }

        if (tid != null && v.size() == 0) {
            // Specified transaction did not exist
            status = Status.NOT_FOUND;
	    errMsg = rb.getString(rb.E_NO_SUCH_TRANSACTION, tid);
        }

        // Write reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.GET_TRANSACTIONS_REPLY, status, errMsg);
        // Add JMQQuantity property
        try {
            reply.getProperties().put(MessageType.JMQ_QUANTITY,
                            Integer.valueOf(v.size()));
        } catch (IOException e) {
	    // Programming error. No need to I18N
	    logger.logStack(Logger.WARNING, rb.E_INTERNAL_BROKER_ERROR,
                "Admin: GetTransactions: Could not extract properties from pkt",
                e);
        } catch (ClassNotFoundException e) {
	    // Programming error. No need to I18N
	    logger.logStack(Logger.WARNING, rb.E_INTERNAL_BROKER_ERROR,
                "Admin: GetTransactions: Could not extract properties from pkt",
                e);
        }

        // Always set the body even if vector is empty
	setBodyObject(reply, v);

	parent.sendReply(con, cmd_msg, reply);
        return true;
    }
}
