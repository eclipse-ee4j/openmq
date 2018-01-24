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
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Set;
import java.util.Hashtable;
import java.util.Properties;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;

public class ChangeClusterMasterBrokerHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public ChangeClusterMasterBrokerHandler(AdminDataHandler parent) {
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

        int status = Status.OK;
        String emsg = null;

        if (DEBUG) {
            logger.log(Logger.INFO, this.getClass().getName()+": "+cmd_props);
        }

        boolean notificationOnly = false, fromJMSRA = false;
        String oldmb = (String)cmd_props.get(MessageType.JMQ_CLUSTER_OLD_MASTER_BROKER);
        String newmb = (String)cmd_props.get(MessageType.JMQ_CLUSTER_NEW_MASTER_BROKER);
        Object val = cmd_props.get(MessageType.JMQ_JMSRA_MANAGED_BROKER);
        if (val != null && Boolean.valueOf(val.toString()).booleanValue()) {
            fromJMSRA = true;
        }
        val = cmd_props.get(MessageType.JMQ_JMSRA_NOTIFICATION_ONLY);
        if (val != null && Boolean.valueOf(val.toString()).booleanValue()
            && fromJMSRA && Globals.isJMSRAManagedBroker()) {
            notificationOnly = true;
        }
        logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                   BrokerResources.I_ADMIN_RECEIVED_CMD, 
                   MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER)+
                   "["+MessageType.JMQ_CLUSTER_NEW_MASTER_BROKER+"="+newmb+", "+
                       MessageType.JMQ_CLUSTER_OLD_MASTER_BROKER+"="+oldmb+"]"+
                    (fromJMSRA ? "JMSRA":"")+
                    (notificationOnly ? "("+MessageType.JMQ_JMSRA_NOTIFICATION_ONLY+")":"")));

        if (Globals.getHAEnabled()) {
            status = Status.PRECONDITION_FAILED;
            emsg =  rb.getKString(rb.E_OP_NOT_APPLY_TO_HA_BROKER, 
                   MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER));
            logger.log(Logger.ERROR, emsg);
            sendReply(status, emsg, con, cmd_msg);
            return true;
        }
        if (Globals.useSharedConfigRecord()) {
            status = Status.PRECONDITION_FAILED;
            emsg =  rb.getKString(rb.E_OP_NOT_APPLY_NO_MASTER_BROKER_MODE, 
                   MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER));
            logger.log(Logger.ERROR, emsg);
            sendReply(status, emsg, con, cmd_msg);
            return true;
        }
        try {
            BrokerStateHandler.setExclusiveRequestLock(
                ExclusiveRequest.CHANGE_MASTER_BROKER);
        } catch (Throwable t) {
            status = Status.PRECONDITION_FAILED;
            if (t instanceof BrokerException) {
                status = ((BrokerException)t).getStatusCode();
            }
            emsg = MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER)+": "+
                       Status.getString(status)+" - "+t.getMessage();
            logger.log(Logger.ERROR, emsg);
            status = Status.PRECONDITION_FAILED;
            sendReply(status, emsg, con, cmd_msg);
            return true;
        }
        try {
            if (!Globals.dynamicChangeMasterBrokerEnabled()) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_NO_SUPPORT_DYNAMIC_CHANGE_MASTER_BROKER),
                    Status.NOT_ALLOWED);
            }
            if (newmb == null) {
                throw new IllegalArgumentException("null "+
                    MessageType.JMQ_CLUSTER_NEW_MASTER_BROKER);
            }

            if (!fromJMSRA && Globals.isJMSRAManagedBroker()) {
                throw new IllegalAccessException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.X_ADMIN_CHANGE_MASTER_NOT_FROM_JMSRA));
            }

            ClusterManager cm = Globals.getClusterManager();
            BrokerMQAddress self = (BrokerMQAddress)cm.getMQAddress();
            BrokerMQAddress master = (cm.getMasterBroker() == null ? 
                                      null:(BrokerMQAddress)cm.getMasterBroker().getBrokerURL());
            BrokerMQAddress newmba = BrokerMQAddress.createAddress(newmb); 
            BrokerMQAddress oldmba = null;
            if (oldmb != null) {
                oldmba = BrokerMQAddress.createAddress(oldmb); 
            }
            if (notificationOnly) {
                if (master == null) {
                    emsg = "IllegalStateException for notification "+ 
                            MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER)+
                            ": No master broker";
                    logger.log(logger.ERROR, emsg);
                    sendReply(Status.ERROR, emsg, con, cmd_msg);
                    Broker.getBroker().exit(1, emsg, BrokerEvent.Type.ERROR);
                    throw new IllegalStateException(emsg);
                }
                if (newmba.equals(self)) {
                    if (!master.equals(self)) {
                        emsg = "IllegalStateException for notification "+ 
                            MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER)+
                            ": This broker, which has master broker "+master+
                            ", is not the master broker as expected";
                        logger.log(logger.ERROR, emsg);
                        sendReply(Status.ERROR, emsg, con, cmd_msg);
                        Broker.getBroker().exit(1, emsg, BrokerEvent.Type.ERROR);
                        return true;
                    }
                }
                if (oldmba != null && oldmba.equals(self)) {
                    if (!master.equals(newmba)) {
                        emsg = "IllegalStateException for notification "+ 
                            MessageType.getString(MessageType.CHANGE_CLUSTER_MASTER_BROKER)+
                            ": This broker, which is the old master broker "+oldmba+
                            ", does not have "+newmba+" as the master broker as expected";
                        logger.log(logger.ERROR, emsg);
                        sendReply(Status.ERROR, emsg, con, cmd_msg);
                        Broker.getBroker().exit(1, emsg, BrokerEvent.Type.ERROR);
                        return true;
                    }
                }
                sendReply(Status.OK, null, con, cmd_msg);
                return true;
            }
            if (master == null) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_CLUSTER_NO_MASTER_BROKER_REJECT_CHANGE_MASTER),
                    Status.PRECONDITION_FAILED);
            }
            if (newmba.equals(master)) {
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_CLUSTER_CHANGE_MASTER_BROKER_SAME, newmba));
                sendReply(Status.OK, null, con, cmd_msg);
                return true;
            }
            if (oldmba == null) {
                oldmba = master;
            }
            if (!oldmba.equals(master)) {
                throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_CLUSTER_CHANGE_MASTER_BROKER_MISMATCH,
                    oldmba.toString(), master), Status.PRECONDITION_FAILED);
            }
            if (!self.equals(master)) {
                if (!Globals.isJMSRAManagedBroker()) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_CLUSTER_THIS_BROKER_NOT_MASTER_BROKER_REJECT_CHANGE_MASTER,
                        master.toString()), Status.NOT_ALLOWED);
                 } 
                 sendReply(Status.OK, null, con, cmd_msg);
                 return true;
            }
            Globals.getClusterBroadcast().changeMasterBroker(newmba, oldmba);
            sendReply(Status.OK, null, con, cmd_msg);
            return true;
        } catch (Exception e) {
            status = Status.ERROR;
            emsg = e.getMessage();
            if (e instanceof BrokerException) {
                status = ((BrokerException)e).getStatusCode();
                emsg = emsg+"["+Status.getString(status)+"]";
            }
            logger.logStack(Logger.ERROR, emsg, e);
            sendReply(status, emsg, con, cmd_msg);
            return true; 
        } finally {
            BrokerStateHandler.unsetExclusiveRequestLock(
                ExclusiveRequest.CHANGE_MASTER_BROKER);
        }
    }

    private void sendReply(int status, String emsg, IMQConnection con, Packet cmd_msg) {
	    Packet reply = new Packet(con.useDirectBuffers());
	    reply.setPacketType(PacketType.OBJECT_MESSAGE);

	    setProperties(reply, MessageType.CHANGE_CLUSTER_MASTER_BROKER_REPLY, status, emsg);
        parent.sendReply(con, cmd_msg, reply);
    }
}
