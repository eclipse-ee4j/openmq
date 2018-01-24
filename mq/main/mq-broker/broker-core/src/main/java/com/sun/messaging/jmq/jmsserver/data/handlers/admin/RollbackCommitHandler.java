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
 * @(#)RollbackCommitHandler.java	1.13 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;
import java.util.List;
import java.util.Iterator;
import javax.transaction.xa.XAResource;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.RollbackReason;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;

public class RollbackCommitHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public RollbackCommitHandler(AdminDataHandler parent) {
	super(parent);
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
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                            "Rollback/Commit transaction " + cmd_props);
        }

        // Get the admin request message type
        int requestType =
            ((Integer)cmd_props.get(MessageType.JMQ_MESSAGE_TYPE)).intValue();
        int status = Status.OK;
	String errMsg = null;
        TransactionUID tid = null;
        TransactionState ts = null;
        TransactionHandler thandler = null;

        // Get the packet handler that handles transaction packets
        if (parent.adminPktRtr != null) {
            thandler = (TransactionHandler)
            parent.adminPktRtr.getHandler(PacketType.ROLLBACK_TRANSACTION);
        }

	Long id = (Long)cmd_props.get(MessageType.JMQ_TRANSACTION_ID);

        //only applies to rollback request
	Boolean v = (Boolean)cmd_props.get(MessageType.JMQ_PROCESS_ACTIVE_CONSUMERS);
        boolean processActiveConsumers = (v == null ? false: v.booleanValue());

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	}

        if (id != null) {
            tid = new TransactionUID(id.longValue());
        } else {
            status = Status.BAD_REQUEST;
        }

        if (status == Status.OK) {
            TransactionList[] tls = DL.getTransactionList(null);
            TransactionList tl = null;
            for (int i = 0; i < tls.length; i++) {
                tl = tls[i];
                ts = tl.retrieveState(tid);
                if (ts == null) {
                    continue;
                }
                break;
            }
            if (ts == null) {
                // Specified transaction did not exist
                status = Status.NOT_FOUND;
	        errMsg = rb.getString(rb.E_NO_SUCH_TRANSACTION, tid);
            } else if (requestType == MessageType.COMMIT_TRANSACTION &&
                        ts.getState() != TransactionState.PREPARED) { 
                status = Status.PRECONDITION_FAILED;
	        errMsg = rb.getString(rb.E_TRANSACTION_NOT_PREPARED, tid);
            } else if (requestType == MessageType.ROLLBACK_TRANSACTION &&
                        (ts.getState() < TransactionState.STARTED || 
                         ts.getState() > TransactionState.PREPARED)) { 
                status = Status.PRECONDITION_FAILED;
	        errMsg = rb.getString(rb.E_INVALID_TXN_STATE_FOR_ROLLBACK, tid);
            } else {
                JMQXid xid = tl.UIDToXid(tid);

                if (xid == null && 
                    (!(Globals.getHAEnabled() && 
                       ts.getState() == TransactionState.PREPARED))) {
		    /*
		     * Need to pick the right error message:
		     * If (action is ROLLBACK and state is one of {STARTED, FAILED, 
		     *			INCOMPLETE, COMPLETE})
		     *  "Rollback of non-XA transaction 123456789 in non-PREPARED state 
		     *   is not supported."
		     * else
                     *  "Could not find Xid for 123456789"
		     */
                    if (requestType == MessageType.ROLLBACK_TRANSACTION &&
                        (ts.getState() >= TransactionState.STARTED && 
                         ts.getState() < TransactionState.PREPARED)) {
                        errMsg = rb.getString(rb.E_INTERNAL_BROKER_ERROR,
                            "Rollback of non-XA transaction " 
				+ tid
				+ " in non-PREPARED state is not supported.") ;
		            } else {
                        errMsg = rb.getString(rb.E_INTERNAL_BROKER_ERROR,
                            "Could not find Xid for " + tid) ;
		            }

                    status = Status.ERROR;
                } else if (thandler == null) {
                    errMsg = rb.getString(rb.E_INTERNAL_BROKER_ERROR,
                        "Could not locate TransactionHandler") ;
                    status = Status.ERROR;
                } else {
                    if (requestType == MessageType.ROLLBACK_TRANSACTION) {
	                if ( DEBUG ) {
                            logger.log(Logger.DEBUG, 
                                "Rolling back " + tid + " in state " + ts);
                        }
                        try {
                            if (processActiveConsumers) {
                                logger.log(logger.INFO, rb.getKString(
                                    rb.I_ADMIN_REDELIVER_MSGS_ON_TXN_ROLLBACK, tid));
                                try {
                                    thandler.redeliverUnacked(tl, tid, 
                                        true, true, true, -1, false);
                                } catch (Exception e) {
                                    logger.logStack(logger.WARNING, 
                                        rb.getKString(
                                        rb.X_ADMIN_REDELIVER_MSG_ON_TXN_ROLLBACK,
                                        tid, e.getMessage()), e);
                                }
                            }
                            thandler.doRollback(tl, tid, xid,
                                                null, ts, null, con, RollbackReason.ADMIN);
                        } catch (BrokerException e) {
                            status = Status.ERROR;
                            errMsg = e.getMessage();
                        }
                    } else if (requestType == MessageType.COMMIT_TRANSACTION) {
	                if ( DEBUG ) {
                            logger.log(Logger.DEBUG, 
                                "Committing " + tid + " in state " + ts);
                        }
                        try {
                            thandler.doCommit(tl, tid, xid,
                                Integer.valueOf(XAResource.TMNOFLAGS), ts, null,
                            false, con, null);
                        } catch (BrokerException e) {
                            status = Status.ERROR;
                            errMsg = e.getMessage();
                        }
                    } else {
                       // Should never happen.
                       return super.handle(con, cmd_msg, cmd_props);
                    }
                }
            }
        }

        sendReply(con, cmd_msg, requestType + 1, status, errMsg);

        return true;
    }

    private void sendReply(IMQConnection con, Packet cmd_msg,
        int replyType, int status, String errMsg) {

        // Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, replyType, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);

    }

}
