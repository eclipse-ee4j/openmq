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
 * @(#)CommonProtocol.java	1.48 07/24/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker;

import java.io.*;
import java.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.multibroker.ClusterGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.RaptorProtocol;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.HeartbeatService;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.persist.api.ChangeRecordInfo;

public class CommonProtocol implements Protocol
{
    private static boolean DEBUG = false;

    protected static final Logger logger = Globals.getLogger();
    protected MessageBusCallback cb = null;
    protected Cluster c = null;
    protected com.sun.messaging.jmq.jmsserver.core.BrokerAddress
        selfAddress = null;

    protected Protocol realProtocol = null;
    protected boolean protocolInitComplete = false;
    protected long startTime = 0;

    private Integer configServerVersion = null;

    public CommonProtocol(MessageBusCallback cb, Cluster c,
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress myaddress)
        throws BrokerException {
        this.cb = cb;
        this.c = c;
        this.selfAddress = myaddress;
        this.startTime =  System.currentTimeMillis();
        initProtocol();
    }

    public void syncChangeRecordOnJoin(BrokerAddress broker,  ChangeRecordInfo cri)
        throws BrokerException {

        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                  BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), 
                  Status.UNAVAILABLE);
        }

        realProtocol.syncChangeRecordOnJoin(broker, cri);
    }

    public ChangeRecordInfo getLastStoredChangeRecord() {
        if (!getProtocolInitComplete()) { //should never happen
            throw new RuntimeException(Globals.getBrokerResources().getKString(
                  BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY));
        }
        return realProtocol.getLastStoredChangeRecord();
    }

    public RaptorProtocol getRealProtocol() {
        return (RaptorProtocol)realProtocol;
    }

    public int getHighestSupportedVersion() {
         return ProtocolGlobals.getCurrentVersion();
    }

    public int getClusterVersion() throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }
        try {
            if (c.getConfigServer() != null && configServerVersion != null) {
                return configServerVersion.intValue();
            }
        } catch (Exception e) {
            logger.logStack(logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "getConfigServer()", e);
        }
        return realProtocol.getClusterVersion();
    }

    private void initProtocol() {
        if (DEBUG) {
            logger.log(logger.DEBUG, "Using RAPTOR cluster protocol.");
        }

        try {
            c.useGPackets(true);
        }
        catch (Exception e) {}

        try {
            realProtocol = new
                com.sun.messaging.jmq.jmsserver.multibroker.raptor.RaptorProtocol(
                cb, c , selfAddress, getBrokerInfo());
        } catch (Exception e) {
            logger.logStack(logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Initializing the cluster protcol", e);
        }
    }

    private void startProtocol() {
        try {
            realProtocol.startClusterIO();
        } catch (Exception e) {
            logger.logStack(logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "start the cluster protcol", e);
        }
    }

    public BrokerInfo getBrokerInfo() {
        BrokerInfo selfInfo = new BrokerInfo();
        selfInfo.setBrokerAddr(selfAddress);
        selfInfo.setStartTime(startTime);
        selfInfo.setStoreDirtyFlag(false);
        selfInfo.setClusterProtocolVersion(Integer.valueOf(ProtocolGlobals.getCurrentVersion()));

        if (Globals.getHAEnabled()) {
            selfInfo.setHeartbeatHostAddress(
                ((HeartbeatService)Globals.getHeartbeatService()).
                 getHeartbeatHostAddress());
            selfInfo.setHeartbeatPort(
                ((HeartbeatService)Globals.getHeartbeatService()).
                 getHeartbeatPort());
            selfInfo.setHeartbeatInterval(
                ((HeartbeatService)Globals.getHeartbeatService()).
                 getHeartbeatInterval());
        }

        return selfInfo;
    }

    public ClusterBrokerInfoReply getBrokerInfoReply(BrokerInfo remote) throws Exception {
        if (c.getConfigServer() != null) {
            ClusterBrokerInfoReply cbi = ClusterBrokerInfoReply.newInstance(
                            getBrokerInfo(), ProtocolGlobals.G_BROKER_INFO_OK);
            return cbi;
        }
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                  BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), 
                  Status.UNAVAILABLE);
        }

        return realProtocol.getBrokerInfoReply(remote);
    }

    public int addBrokerInfo(BrokerInfo brokerInfo) {
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress configServer;
        try {
            configServer = c.getConfigServer();
        }
        catch (Exception e) {
            logger.logStack(logger.WARNING,
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "getConfigServer()", e);
            return ADD_BROKER_INFO_RETRY;
        }
        if (getProtocolInitComplete()) {
            int pv = -1;
            try {
                pv = getClusterVersion();
            } catch (Exception e) {
                logger.log(logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Unable to get cluster protocol version for adding remote broker "+
                 brokerInfo.getBrokerAddr()); 
                return ADD_BROKER_INFO_BAN;
            }
            Integer v = brokerInfo.getClusterProtocolVersion();
            if (v == null || v.intValue() < pv) {
                logger.log(logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
                "Cluster protocol version " + v + " of remote broker " +
                brokerInfo.getBrokerAddr() +
                " is not allowed in the cluster that has cluster protocol version " + pv);
                return ADD_BROKER_INFO_BAN;
            }
            return realProtocol.addBrokerInfo(brokerInfo);
        }

        if (configServer != null &&
            configServer.equals(brokerInfo.getBrokerAddr())) {
            Integer masterv = brokerInfo.getClusterProtocolVersion();
            if (masterv == null || 
                masterv.intValue() < ProtocolGlobals.VERSION_500) {
                logger.log(logger.ERROR, 
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_MASTER_BROKER_VERSION_NO_SUPPORT,
                    (masterv == null ? "null" : masterv.toString()),
                    brokerInfo.getBrokerAddr()));
                return ADD_BROKER_INFO_BAN;
            }

            startProtocol();
            configServerVersion = masterv;

            // From this point on, all the calls will be forwarded to
            // the real protocol implementation...
            setProtocolInitComplete(true);
            return realProtocol.addBrokerInfo(brokerInfo);
        }
        else {
            return ADD_BROKER_INFO_RETRY;
        }
    }

    public void removeBrokerInfo(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress broker, boolean broken) {
        if (getProtocolInitComplete()) {
            realProtocol.removeBrokerInfo(broker, broken);
        }
    }

    public void setMatchProps(Properties matchProps) {
        c.setMatchProps(matchProps);
    }

    public void startClusterIO() {
        if (DEBUG)
            logger.log(Logger.DEBUG,
            "CommonProtocol.startClusterIO()");

        // If this is the master broker then use the new raptor
        // cluster protocol.
        try {
            com.sun.messaging.jmq.jmsserver.core.BrokerAddress configServer
                = c.getConfigServer();
            if (configServer == null ||
                configServer.equals(selfAddress)) {
                startProtocol();
                setProtocolInitComplete(true);
            }
        }
        catch (Exception e) {}

        try {
            c.start();
        }
        catch (Exception e) {
            //should add interface Cluster.getServiceName
            logger.logStack(logger.ERROR, BrokerResources.X_START_SERVICE_EXCEPTION, 
                   "cluster", e.getMessage(), e); 
            Broker.getBroker().exit(1,
                 Globals.getBrokerResources()
                   .getKString(BrokerResources.X_START_SERVICE_EXCEPTION,
                   "cluster", e.getMessage()),
                 BrokerEvent.Type.EXCEPTION);
        }
    }

    public void stopClusterIO(boolean requestTakeover, boolean force,
                              BrokerAddress excludedBroker) {
        if (realProtocol != null)
            realProtocol.stopClusterIO(requestTakeover, force, excludedBroker);
        c.shutdown(force, excludedBroker);
    }

    public void receiveUnicast(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress sender,
        GPacket gp) {
        if (DEBUG)
            logger.log(logger.DEBUG, "receiveUnicast GPacket");
        realProtocol.receiveUnicast(sender, gp);
    }

    public void receiveBroadcast(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress sender,
        GPacket gp) {
        if (DEBUG)
            logger.log(logger.DEBUG, "receiveBroadcast GPacket");
        realProtocol.receiveBroadcast(sender, gp);
    }

    public void receiveUnicast(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress sender,
        int destId, byte []pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "receiveUnicast");
        realProtocol.receiveUnicast(sender, destId, pkt);
    }

    public void receiveBroadcast(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress sender,
        int destId, byte []pkt) {
        if (DEBUG)
            logger.log(logger.DEBUG, "receiveBroadcast");
        realProtocol.receiveBroadcast(sender, destId, pkt);
    }


    public boolean waitForConfigSync() {
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress configServer =
            null;

        try {
            configServer = c.getConfigServer();
        }
        catch (Exception e) {
            return true; // There is config server but it's unreachable.
        }

        if (configServer == null)
            return false; // There is no config server.

        if (configServer.equals(selfAddress))
            return false; // I am the config server.

        if (!getProtocolInitComplete())
            return true;

        return realProtocol.waitForConfigSync();
    }

    public void reloadCluster() {
        waitForProtocolInit();
        realProtocol.reloadCluster();
    }

    public void stopMessageFlow() throws IOException {
        realProtocol.stopMessageFlow();
    }

    public void resumeMessageFlow() throws IOException {
        realProtocol.resumeMessageFlow();
    }

    public void sendMessage(PacketReference pkt, Collection targets,
                 boolean sendMsgDeliveredAck) {
        realProtocol.sendMessage(pkt, targets, sendMsgDeliveredAck);
    }

    public void sendMessageAck(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress msgHome, 
        com.sun.messaging.jmq.io.SysMessageID sysid,
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID cuid,
        int ackType, Map optionalProps, boolean ackack) throws BrokerException {
        realProtocol.sendMessageAck(msgHome, sysid, cuid, ackType, optionalProps, ackack);
    }

    public void sendMessageAck2P(
        com.sun.messaging.jmq.jmsserver.core.BrokerAddress msgHome, 
        com.sun.messaging.jmq.io.SysMessageID[] sysids,
        com.sun.messaging.jmq.jmsserver.core.ConsumerUID[] cuids,
        int ackType, Map optionalProps, Long txnID, UID txnStoreSession,
        boolean ackack, boolean async) throws BrokerException { 

        realProtocol.sendMessageAck2P(msgHome, sysids, cuids, ackType, 
            optionalProps, txnID, txnStoreSession, ackack, async);
    }

    public void sendClusterTransactionInfo(long tid,
                com.sun.messaging.jmq.jmsserver.core.BrokerAddress to) {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendClusterTransactionInfo(tid, to);
    }

    public void sendTransactionInquiry(TransactionUID tid,
                com.sun.messaging.jmq.jmsserver.core.BrokerAddress to) {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendTransactionInquiry(tid, to);
    }

    public void sendPreparedTransactionInquiries(List<TransactionUID> tids,
                com.sun.messaging.jmq.jmsserver.core.BrokerAddress to) {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendPreparedTransactionInquiries(tids, to);
    }

    public int getClusterAckWaitTimeout() {
        if (!getProtocolInitComplete()) {
            return ProtocolGlobals.getAckTimeout();
        }
        return realProtocol.getClusterAckWaitTimeout();
    }

    public com.sun.messaging.jmq.jmsserver.core.BrokerAddress lookupBrokerAddress(String brokerid) {
        if (!getProtocolInitComplete()) {
            logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                   BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY)+": lookup("+brokerid+")");
            return null;
        }
        return realProtocol.lookupBrokerAddress(brokerid);
    }

    public com.sun.messaging.jmq.jmsserver.core.BrokerAddress lookupBrokerAddress(BrokerMQAddress mqaddr) {
        if (!getProtocolInitComplete()) {
            logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                   BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY)+": lookup("+mqaddr+")");
            return null;
        }
        return realProtocol.lookupBrokerAddress(mqaddr);
    }

    public String lookupStoreSessionOwner(UID session) {
        if (!getProtocolInitComplete()) {
            logger.log(logger.WARNING, Globals.getBrokerResources().getKString(
                   BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY)+": lookup("+session+")");
            return null;
        }
        return realProtocol.lookupStoreSessionOwner(session);
    }

    public void clientClosed(ConnectionUID conid, boolean notify) {
        if (!getProtocolInitComplete()) {
            return;
        }

        realProtocol.clientClosed(conid, notify);
    }

    public int lockSharedResource(String resId, Object owner) {
        if (!getProtocolInitComplete()) {
            return ProtocolGlobals.G_LOCK_SUCCESS;
        }
        return realProtocol.lockSharedResource(resId, owner);
    }

    public int lockResource(String resId, long timestamp, Object owner) {
        if (!getProtocolInitComplete()) {
            return ProtocolGlobals.G_LOCK_SUCCESS;
        }

        return realProtocol.lockResource(resId, timestamp, owner);
    }

    public void unlockResource(String resId) {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.unlockResource(resId);
    }

    public void recordUpdateDestination(Destination d)
        throws BrokerException {
        waitForProtocolInit();
        realProtocol.recordUpdateDestination(d);
    }

    public void recordRemoveDestination(Destination d)
        throws BrokerException {
        waitForProtocolInit();
        realProtocol.recordRemoveDestination(d);
    }

    public void sendNewDestination(Destination d)
                  throws BrokerException {
        if (d.isTemporary() && !getProtocolInitComplete()) {
            return;
        }
        waitForProtocolInit();
        realProtocol.sendNewDestination(d);
    }

    public void sendRemovedDestination(Destination d)
                  throws BrokerException {
        if (d.isTemporary() && !getProtocolInitComplete()) {
            return;
        }
        waitForProtocolInit();
        realProtocol.sendRemovedDestination(d);
    }

    public void sendUpdateDestination(Destination d)
                  throws BrokerException {
        if (d.isTemporary() && !getProtocolInitComplete()) {
            return;
        }
        waitForProtocolInit();
        realProtocol.sendUpdateDestination(d);
    }

    public void recordCreateSubscription(Subscription sub)
        throws BrokerException {
        waitForProtocolInit();
        realProtocol.recordCreateSubscription(sub);
    }

    public void recordUnsubscribe(Subscription sub)
        throws BrokerException {
        waitForProtocolInit();
        realProtocol.recordUnsubscribe(sub);
    }

    public void sendNewSubscription(Subscription sub, Consumer cons,
        boolean active) throws BrokerException {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendNewSubscription(sub, cons, active);
    }

    public void sendNewConsumer(Consumer intr, boolean active)
                  throws BrokerException {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendNewConsumer(intr, active);
    }

    public void sendRemovedConsumer(Consumer intr, Map pendingMsgs, boolean cleanup)
                  throws BrokerException {
        if (!getProtocolInitComplete()) {
            return;
        }
        realProtocol.sendRemovedConsumer(intr, pendingMsgs, cleanup);
    }

    public void handleGPacket(MessageBusCallback mbcb, BrokerAddress sender, GPacket pkt) {
        if (!getProtocolInitComplete()) {
            logger.logStack(Logger.ERROR, "No protocol", new Exception("No protocol"));
            return;
        }
        realProtocol.handleGPacket(mbcb, sender, pkt);
    }

	public void preTakeover(String brokerID, UID storeSession, 
           String brokerHost, UID brokerSession) throws BrokerException {
        if (!getProtocolInitComplete()) {
            logger.logStack(Logger.ERROR, "No protocol", new Exception("No protocol"));
            return;
        }
        realProtocol.preTakeover(brokerID, storeSession, brokerHost, brokerSession);
    }
    
	public void postTakeover(String brokerID, UID storeSession, boolean aborted, boolean notify) {
        if (!getProtocolInitComplete()) {
            logger.logStack(Logger.ERROR, "No protocol", new Exception("No protocol"));
            return;
        }
        realProtocol.postTakeover(brokerID, storeSession, aborted, notify);
    }

	public void changeMasterBroker(BrokerMQAddress newmaster,
                                   BrokerMQAddress oldmaster)
                                   throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }

        realProtocol.changeMasterBroker(newmaster, oldmaster);
    }

	public String sendTakeoverME(String brokerID, String uuid)
                                   throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }

        return realProtocol.sendTakeoverME(brokerID, uuid);
    }

    public void sendMigrateStoreRequest(String targetBrokerID, Long syncTimeout,
                                        String uuid, String myBrokerID)
                                        throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }
        realProtocol.sendMigrateStoreRequest(targetBrokerID, syncTimeout,
                                             uuid, myBrokerID);
    }

    public void transferFiles(String[] fileNames, String targetBrokerID,
                              Long syncTimeout, String uuid, String myBrokerID,
                              String module, FileTransferCallback callback)
                              throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }
        realProtocol.transferFiles(fileNames, targetBrokerID, syncTimeout,
                                   uuid, myBrokerID, module, callback);
    }


    public String sendTakeoverMEPrepare(String brokerID, byte[] token,
                                        Long syncTimeout, String uuid)
                                        throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }

        return realProtocol.sendTakeoverMEPrepare(brokerID, token, syncTimeout, uuid);
    }

    public void notifyPartitionArrival(UID partitionID, String brokerID)
    throws BrokerException {
        if (!getProtocolInitComplete()) {
            throw new BrokerException(Globals.getBrokerResources().getKString(
                BrokerResources.X_CLUSTER_PROTOCOL_NOT_READY), Status.UNAVAILABLE);
        }

        realProtocol.notifyPartitionArrival(partitionID, brokerID);
    }

    public Hashtable getDebugState() {
        if (!getProtocolInitComplete()) {
            Hashtable ht = new Hashtable();
            ht.put("protocol", "No protocol yet");
            return ht;
        }
        return realProtocol.getDebugState();
    }

    private Object protocolInitWaitObject = new Object();

    private void waitForProtocolInit() {
        synchronized (protocolInitWaitObject) {
            while (!getProtocolInitComplete()) {
                try {
                    logger.log(Logger.INFO, BrokerResources.I_CLUSTER_WAIT_PROTOCOLINIT);
                    protocolInitWaitObject.wait(60000);
                }
                catch (Exception e) {}
            }
        }
    }

    private boolean getProtocolInitComplete() {
        synchronized (protocolInitWaitObject) {
            return protocolInitComplete;
        }
    }

    private void setProtocolInitComplete(boolean protocolInitComplete) {
        synchronized (protocolInitWaitObject) {
            this.protocolInitComplete = protocolInitComplete;
            protocolInitWaitObject.notifyAll();
        }
    }
}
