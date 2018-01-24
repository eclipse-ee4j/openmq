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

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;

import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.persist.api.ReplicableStore;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.OperationNotAllowedException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

public class MigrateStoreHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();
    public static final String MAX_WAIT_ADMIN_CLIENT_PROP =
        Globals.IMQ+".cluster.migratestore.shutdown.maxWaitAdminClient";
    private static final int DEFAULT_MAX_WAIT_ADMIN_CLIENT = 60; //sec;

    public MigrateStoreHandler(AdminDataHandler parent) {
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
        boolean noop = true;
        int status = Status.OK;
        String errMsg = "";
        if (DEBUG) {
            logger.log(Logger.INFO, this.getClass().getName() + ": " +
                       "Request migrate this broker''s store: " + cmd_props);
        }
        String brokerID = (String)cmd_props.get(MessageType.JMQ_BROKER_ID);
        String partition = (String)cmd_props.get(MessageType.JMQ_MIGRATESTORE_PARTITION);
        if (partition == null) {
            if (brokerID != null) {
                logger.log(Logger.INFO, BrokerResources.I_ADMIN_MIGRATESTORE_TO, brokerID);
            } else {
                logger.log(Logger.INFO, BrokerResources.I_ADMIN_MIGRATESTORE);
            }
        } else {
            logger.log(Logger.INFO, 
            "XXXAdmin request migrate this broker's store partition "+partition+" to broker "+brokerID);
        }

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 

        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.NOT_MODIFIED;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);
            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
        }
        if (status == Status.OK) { 
            if (partition == null && Globals.getHAEnabled()) {
                status = Status.NOT_MODIFIED;
                errMsg =  rb.getKString(rb.E_OPERATION_NOT_SUPPORTED_IN_HA,
                          MessageType.getString(MessageType.MIGRATESTORE_BROKER));
                logger.log(Logger.ERROR, errMsg);
            }
        }
        if (status == Status.OK) { 
            if (Globals.isJMSRAManagedBroker()) {
                status = Status.NOT_MODIFIED;
                errMsg =  "Can not process migration store request because this broker's life cycle is JMSRA managed";
                logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
            }
        }
        UID partitionID = null;
        if (status == Status.OK) { 
            if (partition != null) {
                try {
                    long v = Long.parseLong(partition);
                    partitionID = new UID(v);
                } catch (Exception e) {
                    partitionID = null;
                    status = Status.NOT_MODIFIED;
                    errMsg =  "XXXCan not process migration partition "+partition+" request because unable to parse "+partition+": "+e;
                    logger.log(Logger.ERROR, errMsg);
                }
            }
        }
        if (status == Status.OK) { 
            if (partitionID != null && brokerID == null) {
                status = Status.NOT_MODIFIED;
                errMsg =  "XXXCan not process migration partition "+partitionID+" request because brokerID not specified";
                logger.log(Logger.ERROR, errMsg);
            }
        }
        if (status == Status.OK) {
            if (partitionID != null && 
                !(DL.isPartitionMode() && DL.isPartitionMigratable())) {
                status = Status.NOT_MODIFIED;
                errMsg =  "XXXCan not process migration partition "+partitionID+" request because partition mode not enabled";
                logger.log(Logger.ERROR, errMsg);
            }
        }
        if (status == Status.OK) {
            if (partitionID != null) {
                DestinationList dl = DL.getDestinationList(partitionID);
                if (dl == null) {
                    status = Status.NOT_MODIFIED;
                    errMsg =  "XXXCan not process migration partition "+partitionID+" request because partition "+partitionID+" not found";
                    logger.log(Logger.ERROR, errMsg);
                } else if (dl.getPartitionedStore().isPrimaryPartition()) {
                    status = Status.NOT_MODIFIED;
                    errMsg =  "XXXCan not process migration partition "+partitionID+" request because partition "+partitionID+" is the primary partition";
                    logger.log(Logger.ERROR, errMsg);
                }
                   
            }
        }
        if (status == Status.OK) { 
            if (brokerID == null) {
                try {
                    brokerID = getBrokerID();
                } catch (Throwable t) {
                    status = Status.NOT_MODIFIED;
                    errMsg = "Unable to get a connected broker to takeover this broker's store: "+t.getMessage(); 
                    if ((t instanceof OperationNotAllowedException) &&
                        ((OperationNotAllowedException)t).getOperation().equals(
                        MessageType.getString(MessageType.MIGRATESTORE_BROKER))) {
                        logger.log(logger.ERROR, errMsg);
                    } else {
                        logger.logStack(logger.ERROR, errMsg, t);
                    }
                }
            }
        }
        if (status != Status.OK) { 
            sendReply(con, cmd_msg, brokerID, null, errMsg, status, null);
            return true;
        }
        try {
            BrokerStateHandler.setExclusiveRequestLock(
                               ExclusiveRequest.MIGRATE_STORE);
        } catch (Throwable t) {
            status = Status.PRECONDITION_FAILED;
            if (t instanceof BrokerException) {
                status = ((BrokerException)t).getStatusCode();
            }
            errMsg = MessageType.getString(MessageType.MIGRATESTORE_BROKER)+": "+
                         Status.getString(status)+" - "+t.getMessage();
            logger.log(Logger.ERROR, errMsg);
            status = Status.NOT_MODIFIED;
        }
        try { //unset lock

        if (partitionID != null) {
            migratePartition(con, cmd_msg, partitionID, brokerID); 
            return true;
        }

        Long syncTimeout = null;
        final BrokerStateHandler bsh = Globals.getBrokerStateHandler();
        if (status == Status.OK) {

            try {
                syncTimeout = (Long)cmd_props.get(MessageType.JMQ_MIGRATESTORE_SYNC_TIMEOUT);

                ClusterManager cm =  Globals.getClusterManager();
                BrokerMQAddress self = (BrokerMQAddress)cm.getMQAddress(); 
                BrokerMQAddress master = (cm.getMasterBroker() == null ?
                    null:(BrokerMQAddress)cm.getMasterBroker().getBrokerURL());
                if (self.equals(master)) {
                    throw new BrokerException(
                        rb.getKString(rb.E_CHANGE_MASTER_BROKER_FIRST,
                        MessageType.getString(MessageType.MIGRATESTORE_BROKER)),
                        Status.NOT_ALLOWED);
                }
            } catch (Throwable t) { 
                status = Status.PRECONDITION_FAILED;
                if (t instanceof BrokerException) {
                    status = ((BrokerException)t).getStatusCode();
                }
                errMsg = MessageType.getString(MessageType.MIGRATESTORE_BROKER)+": "+
                       Status.getString(status)+" - "+t.getMessage();
                logger.log(Logger.ERROR, errMsg);
                status = Status.NOT_MODIFIED;
            }
        }

        SysMessageID replyMessageID = null;
        String replyStatusStr = null;

        try { //shutdown if !noop

        String hostport = null;
        if (status == Status.OK) {
            try {
                noop = false;
                hostport = bsh.takeoverME(brokerID, syncTimeout, con);
            } catch (BrokerException ex) {
                status = ex.getStatusCode();
                if (status == Status.BAD_REQUEST ||
                    status == Status.NOT_ALLOWED ||
                    status == Status.NOT_MODIFIED ||
                    status == Status.UNAVAILABLE ||
                    status == Status.PRECONDITION_FAILED) {

                    status = Status.PRECONDITION_FAILED;

                    if (ex instanceof OperationNotAllowedException) {
                        if (((OperationNotAllowedException)ex).getOperation().equals(
                            MessageType.getString(MessageType.MIGRATESTORE_BROKER))) {
                            status = Status.NOT_MODIFIED;
                            noop = true;
                        }
                    }
                    errMsg = Globals.getBrokerResources().getKString(
                             BrokerResources.E_FAIL_MIGRATESTORE_NOT_MIGRATED,
                             ex.getMessage());
                    if (noop) {
                        logger.log(Logger.ERROR, errMsg);
                    } else {
                        logger.logStack(Logger.ERROR, errMsg, ex);
                    }
                } else {
                    status = Status.EXPECTATION_FAILED;
                    errMsg = Globals.getBrokerResources().getKString(
                             BrokerResources.E_FAIL_TAKEOVERME,
                             brokerID, ex.getMessage());
                   logger.logStack(Logger.ERROR, errMsg, ex);
               }
            } 
        }

        if (status == Status.OK) {
            try {
                Globals.getClusterBroadcast().stopClusterIO(false, true, null);
            } catch (Throwable t) {
               logger.logStack(Logger.WARNING, "Failed to stop cluster IO", t);
            }
        }

        List ret = sendReply(con, cmd_msg, brokerID, hostport, errMsg, status, null);
        replyMessageID = (SysMessageID)ret.get(0);
        replyStatusStr = (String)ret.get(1);

        } finally {
        final SysMessageID mid = replyMessageID;
        final String statusStr = replyStatusStr;

        if (!noop) {
            try {
            if (con instanceof IMQBasicConnection)  {
                IMQBasicConnection ipCon = (IMQBasicConnection)con;
                ipCon.flushControl(1000);
            }
            try {
                Globals.getServiceManager().stopNewConnections(ServiceType.NORMAL);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, rb.getKString(rb.W_STOP_SERVICE_FAIL,
                    ServiceType.getServiceTypeString(ServiceType.NORMAL), e.getMessage()), e);
            }
            try {
                Globals.getServiceManager().stopNewConnections(ServiceType.ADMIN);
            } catch (Exception e) {
                logger.logStack(logger.WARNING, rb.getKString(rb.W_STOP_SERVICE_FAIL,
                    ServiceType.getServiceTypeString(ServiceType.ADMIN), e.getMessage()), e);
            }
            BrokerStateHandler.setShuttingDown(true);
            bsh.prepareShutdown(false, true);
            waitForHandlersToComplete(20);
            if (mid == null) {
                logger.log(Logger.INFO, BrokerResources.I_ADMIN_SHUTDOWN_REQUEST);
                bsh.initiateShutdown("admin", 0, false, 0, true);
                return true;
            } 

            final String waitstr = rb.getKString(rb.I_WAIT_ADMIN_RECEIVE_REPLY,
                                   MessageType.getString(MessageType.MIGRATESTORE_BROKER_REPLY)+
                                   "["+statusStr+"]");
            final long totalwait = Globals.getConfig().getIntProperty(MAX_WAIT_ADMIN_CLIENT_PROP,
                                                       DEFAULT_MAX_WAIT_ADMIN_CLIENT)*1000L;
            final IMQConnection conn = con;
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                public void run() {
                    try {

                    long waited = 0L;
                    while (conn.getConnectionState() < Connection.STATE_CLEANED &&
                           waited < totalwait) {
                        logger.log(logger.INFO, waitstr);
                        try {
                            Thread.sleep(500);
                            waited += 500L;
                        } catch (Exception e) {
                            /* ignore */
                        }
                    }
                    logger.log(Logger.INFO, BrokerResources.I_ADMIN_SHUTDOWN_REQUEST);
                    bsh.initiateShutdown("admin-migratestore-shutdown", 0, false, 0, true);

                    } catch (Throwable t) {
                    bsh.initiateShutdown("admin-migratestore-shutdown::["+t.toString()+"]", 0, false, 0, true);
                    }
                }
            });

            } catch (Throwable t) {
            bsh.initiateShutdown("admin-migratestore-shutdown:["+t.toString()+"]", 0, false, 0, true);
            }
        }
        }

        } finally {
            BrokerStateHandler.unsetExclusiveRequestLock(ExclusiveRequest.MIGRATE_STORE);
        }
        return true;
    }

    private List sendReply(IMQConnection con, Packet cmd_msg,
                               String brokerID, String hostport,
                               String errMsg, int status, UID partitionID) {
        if (errMsg != null) {
            errMsg = errMsg+"["+Status.getString(status)+"]";
        }
        Hashtable p = new Hashtable();
        if (brokerID != null) {
            p.put(MessageType.JMQ_BROKER_ID, brokerID);
        }
        if (hostport != null) {
            p.put(MessageType.JMQ_MQ_ADDRESS, hostport);
        }
        Packet reply = new Packet(con.useDirectBuffers());
        reply.setPacketType(PacketType.OBJECT_MESSAGE);
        setProperties(reply, MessageType.MIGRATESTORE_BROKER_REPLY, status, errMsg, p);
        logger.log(logger.INFO, rb.getKString(rb.I_SEND_TO_ADMIN_CLIENT,
                   MessageType.getString(MessageType.MIGRATESTORE_BROKER_REPLY)+
                   (partitionID == null ? "":"["+partitionID+"]")+
                   "["+Status.getString(status)+"]"));
        List ret = new ArrayList();
        ret.add(parent.sendReply(con, cmd_msg, reply));
        ret.add(Status.getString(status));
        return ret;
    }

    private void migratePartition(IMQConnection con, Packet cmd_msg, 
                                  UID partitionID, String brokerID) {
        int status = Status.OK;
        String errMsg = null;
        try {
            DL.movePartition(partitionID, brokerID);
            logger.log(logger.INFO, "XXX The ownership of store partition "+partitionID +" has been changed to broker "+brokerID);
        } catch (Exception e) {
            status = Status.ERROR;
            errMsg = e.getMessage();
            logger.logStack(logger.ERROR, errMsg, e);
            if (e instanceof BrokerException) {
                status = ((BrokerException)e).getStatusCode();
            }
            if (status == Status.BAD_REQUEST ||
                status == Status.NOT_ALLOWED ||
                status == Status.NOT_MODIFIED ||
                status == Status.UNAVAILABLE ||
                status == Status.PRECONDITION_FAILED) {

                status = Status.PRECONDITION_FAILED;
            }
            if (status != Status.PRECONDITION_FAILED) {
                DL.registerPartitionArrivedEvent(partitionID, null);
            }
        }
        if (status == Status.OK) {
            try {
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                    BrokerResources.I_NOTIFY_BROKER_PARTITION_ARRIVAL, brokerID, partitionID));
                Globals.getClusterBroadcast().notifyPartitionArrival(partitionID, brokerID);
            } catch (Throwable e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
                status = Status.MOVED_PERMANENTLY;
                errMsg = e.getMessage();
                DL.registerPartitionArrivalNotificationEvent(partitionID, brokerID);
            }
        }
        sendReply(con, cmd_msg, brokerID, null, errMsg, status, partitionID);
    }

    /**
     */
    private String getBrokerID() throws Exception {
        String brokerID = null;
        ClusterManager cm = Globals.getClusterManager();
        ClusterBroadcast cbc = Globals.getClusterBroadcast();
        if (Globals.getBDBREPEnabled()) {
            String replica = null;
            List<String> replicas = null;
            replicas = ((ReplicableStore)Globals.getStore()).getMyReplicas();
            Iterator<String> itr = replicas.iterator();
            while (itr.hasNext()) {
                replica = itr.next();
                if (cbc.lookupBrokerAddress(replica) != null) {
                    brokerID = replica;
                }
            }
        }
        if (brokerID == null) {
            BrokerMQAddress mqaddr = (BrokerMQAddress)cm.getBrokerNextToMe();
            BrokerAddress addr = cbc.lookupBrokerAddress(mqaddr);
            if (addr != null && addr.getBrokerID() != null) {
                brokerID = addr.getBrokerID();
            }
        }
        if (brokerID == null) {
            BrokerMQAddress mqaddr = null;
            BrokerAddress addr = null;
            Iterator itr1 = cm.getActiveBrokers();
            while (itr1.hasNext()) {
                mqaddr = (BrokerMQAddress)((ClusteredBroker)itr1.next()).getBrokerURL();
                addr = cbc.lookupBrokerAddress(mqaddr);
                if (addr != null && addr.getBrokerID() != null) {
                    brokerID = addr.getBrokerID();
                }
            }
        }
        if (brokerID == null) {
            throw new OperationNotAllowedException(
                "No connected broker found in cluster",
                MessageType.getString(MessageType.MIGRATESTORE_BROKER));
        }
        return brokerID;
    }
}
