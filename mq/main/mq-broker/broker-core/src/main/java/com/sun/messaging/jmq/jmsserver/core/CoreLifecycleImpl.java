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

package com.sun.messaging.jmq.jmsserver.core;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.common.handlers.AuthHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.ClientIDHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.ErrorHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.FlowHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.FlowPausedHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.GenerateUIDHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.GetLicenseHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.GoodbyeHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.HelloHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.InfoRequestHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.PingHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.StartStopHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.SessionHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.VerifyDestinationHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.AckHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.ConsumerHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.DataHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.DeliverHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.DestinationHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.ProducerHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.QBrowseHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.RedeliverHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.VerifyTransactionHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.AdminDataHandler;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.PartitionNotFoundException;
import com.sun.messaging.jmq.jmsserver.plugin.spi.SessionOpSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.SubscriptionSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ProducerSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.DestinationSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;

public class CoreLifecycleImpl extends CoreLifecycleSpi { 

    private DestinationList destinationList = null;

    public CoreLifecycleImpl() throws BrokerException {
        super();
        destinationList = new DestinationList();
    }

    public String getType() {
        return GFMQ;
    }

    public void initDestinations() throws BrokerException {
        destinationList.init();
    }

    @Override
    public DestinationList getDestinationList() {
        return destinationList;
    }

    @Override
    public int getMaxProducerBatch() {
        return destinationList.MAX_PRODUCER_BYTES_BATCH;
    }

    public void initSubscriptions() throws BrokerException {
        Subscription.initSubscriptions();
    }

    public void initHandlers(PacketRouter pktrtr, ConnectionManager cmgr,
        PacketRouter admin_pktrtr, AdminDataHandler admin_datahdrl)
        throws BrokerException {

        this.pktr = pktrtr;

        HelloHandler hello = new HelloHandler(cmgr);
        hello.setCoreLifecycle(this);
		 
        GetLicenseHandler getLicense = new GetLicenseHandler();
        getLicense.setCoreLifecycle(this);

        GoodbyeHandler goodbye = new GoodbyeHandler(cmgr);
        goodbye.setCoreLifecycle(this);

        StartStopHandler startstop = new StartStopHandler();
        startstop.setCoreLifecycle(this);

        ConsumerHandler conhdlr = new ConsumerHandler();
        conhdlr.setCoreLifecycle(this);

        ProducerHandler prodhandler = new ProducerHandler();
        prodhandler.setCoreLifecycle(this);

        DestinationHandler desthandler = new DestinationHandler();
        desthandler.setCoreLifecycle(this);

        QBrowseHandler qbrowserhdlr = new QBrowseHandler();
        qbrowserhdlr.setCoreLifecycle(this);

        AuthHandler authenticate = new AuthHandler(cmgr);
        authenticate.setCoreLifecycle(this);

        SessionHandler sessionhdlr = new SessionHandler();
        sessionhdlr.setCoreLifecycle(this);

        PingHandler pinghandler = new PingHandler();
        pinghandler.setCoreLifecycle(this);

        DataHandler datahdrl = new DataHandler();
        datahdrl.setCoreLifecycle(this);

        AckHandler ackhandler = new AckHandler();
        ackhandler.setCoreLifecycle(this);

        RedeliverHandler redeliverhdlr = new RedeliverHandler();
        redeliverhdlr.setCoreLifecycle(this);

        DeliverHandler deliverhdlr = new DeliverHandler();
        deliverhdlr.setCoreLifecycle(this);

        TransactionHandler thandler = new TransactionHandler();
        thandler.setCoreLifecycle(this);

        VerifyDestinationHandler vdhandler = new VerifyDestinationHandler();
        vdhandler.setCoreLifecycle(this);

        ClientIDHandler clienthandler = new ClientIDHandler();
        clienthandler.setCoreLifecycle(this);

        FlowHandler flowhdlr = new FlowHandler();
        flowhdlr.setCoreLifecycle(this);

        FlowPausedHandler fphandler = new FlowPausedHandler();
        fphandler.setCoreLifecycle(this);

        GenerateUIDHandler genUIDhandler = new GenerateUIDHandler();
        genUIDhandler.setCoreLifecycle(this);

        InfoRequestHandler infohandler = new InfoRequestHandler();
        infohandler.setCoreLifecycle(this);

        VerifyTransactionHandler vthandler = new VerifyTransactionHandler();
        vthandler.setCoreLifecycle(this);
       
        pktrtr.addHandler(PacketType.HELLO, hello);
        pktrtr.addHandler(PacketType.AUTHENTICATE, authenticate);
        pktrtr.addHandler(PacketType.GET_LICENSE, getLicense);
        pktrtr.addHandler(PacketType.ADD_CONSUMER, conhdlr);
        pktrtr.addHandler(PacketType.DELETE_CONSUMER, conhdlr);
        pktrtr.addHandler(PacketType.ADD_PRODUCER, prodhandler);
        pktrtr.addHandler(PacketType.START, startstop);
        pktrtr.addHandler(PacketType.STOP, startstop);
        pktrtr.addHandler(PacketType.ACKNOWLEDGE, ackhandler);
        pktrtr.addHandler(PacketType.BROWSE, qbrowserhdlr);
        pktrtr.addHandler(PacketType.GOODBYE, goodbye);
        pktrtr.addHandler(PacketType.REDELIVER, redeliverhdlr);
        pktrtr.addHandler(PacketType.CREATE_DESTINATION, desthandler);
        pktrtr.addHandler(PacketType.DESTROY_DESTINATION, desthandler);
        pktrtr.addHandler(PacketType.VERIFY_DESTINATION, vdhandler);
        pktrtr.addHandler(PacketType.DELIVER, deliverhdlr);
        pktrtr.addHandler(PacketType.START_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.COMMIT_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.ROLLBACK_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.PREPARE_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.END_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.RECOVER_TRANSACTION, thandler);
        pktrtr.addHandler(PacketType.SET_CLIENTID, clienthandler);
        pktrtr.addHandler(PacketType.GENERATE_UID, genUIDhandler);
        pktrtr.addHandler(PacketType.MAP_MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.BYTES_MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.MESSAGE_SET, datahdrl);
        pktrtr.addHandler(PacketType.OBJECT_MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.STREAM_MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.TEXT_MESSAGE, datahdrl);
        pktrtr.addHandler(PacketType.RESUME_FLOW, flowhdlr);
        pktrtr.addHandler(PacketType.FLOW_PAUSED, fphandler);

        pktrtr.addHandler(PacketType.CREATE_SESSION,sessionhdlr);
        pktrtr.addHandler(PacketType.DELETE_PRODUCER,prodhandler);
        pktrtr.addHandler(PacketType.DESTROY_SESSION,sessionhdlr);
        pktrtr.addHandler(PacketType.PING,pinghandler);

        pktrtr.addHandler(PacketType.INFO_REQUEST,infohandler);
        pktrtr.addHandler(PacketType.VERIFY_TRANSACTION,vthandler);

        // Map message handles -> messages. For the admin service this
        // is just like the regular JMS service except we have a specialized
        // data handler
        admin_pktrtr.addHandler(PacketType.HELLO, hello);
        admin_pktrtr.addHandler(PacketType.AUTHENTICATE, authenticate);
        admin_pktrtr.addHandler(PacketType.GET_LICENSE, getLicense);
        admin_pktrtr.addHandler(PacketType.ADD_CONSUMER, conhdlr);
        admin_pktrtr.addHandler(PacketType.DELETE_CONSUMER, conhdlr);
        admin_pktrtr.addHandler(PacketType.ADD_PRODUCER, prodhandler);
        admin_pktrtr.addHandler(PacketType.START, startstop);
        admin_pktrtr.addHandler(PacketType.STOP, startstop);
        admin_pktrtr.addHandler(PacketType.ACKNOWLEDGE, ackhandler);
        admin_pktrtr.addHandler(PacketType.BROWSE, qbrowserhdlr);
        admin_pktrtr.addHandler(PacketType.GOODBYE, goodbye);
        admin_pktrtr.addHandler(PacketType.REDELIVER, redeliverhdlr);
        admin_pktrtr.addHandler(PacketType.CREATE_DESTINATION, desthandler);
        admin_pktrtr.addHandler(PacketType.DESTROY_DESTINATION, desthandler);
        admin_pktrtr.addHandler(PacketType.VERIFY_DESTINATION, vdhandler);
        admin_pktrtr.addHandler(PacketType.DELIVER, deliverhdlr);
        admin_pktrtr.addHandler(PacketType.START_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.COMMIT_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.ROLLBACK_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.PREPARE_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.END_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.RECOVER_TRANSACTION, thandler);
        admin_pktrtr.addHandler(PacketType.SET_CLIENTID, clienthandler);
        admin_pktrtr.addHandler(PacketType.GENERATE_UID, genUIDhandler);

        admin_pktrtr.addHandler(PacketType.MAP_MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.BYTES_MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.MESSAGE_SET, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.OBJECT_MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.STREAM_MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.TEXT_MESSAGE, admin_datahdrl);
        admin_pktrtr.addHandler(PacketType.RESUME_FLOW, flowhdlr);
        admin_pktrtr.addHandler(PacketType.FLOW_PAUSED, fphandler);

        admin_pktrtr.addHandler(PacketType.CREATE_SESSION,sessionhdlr);
        admin_pktrtr.addHandler(PacketType.DELETE_PRODUCER,prodhandler);
        admin_pktrtr.addHandler(PacketType.DESTROY_SESSION,sessionhdlr);
    }

    public void cleanup() {
        destinationList.destroyTransactionList(null);
        Consumer.clearAllConsumers();
        destinationList.clearDestinations(null);
        DestinationUID.clearCache();
        Producer.clearProducers();
        Session.clearSessions();
        Subscription.clearSubscriptions();
    }

    /********************************************
     * SessionOp static methods
     **********************************************/

    public SessionOpSpi newSessionOp(Session ss) {
        return SessionOp.newInstance(ss); 
    }

    /********************************************
     * Producer static methods
     **********************************************/

    public Hashtable getProducerAllDebugState() { 
        return Producer.getAllDebugState();
    }

    public void clearProducers() {
        Producer.clearProducers();
    }

    public Iterator getWildcardProducers() {
        return Producer.getWildcardProducers();
    }

    public int getNumWildcardProducers() {
        return Producer.getNumWildcardProducers();
    }


    public String checkProducer(ProducerUID uid) {
        return Producer.checkProducer(uid);
    }

    public void updateProducerInfo(ProducerUID uid, String str) {
        Producer.updateProducerInfo(uid, str);
    }

    public Iterator getAllProducers() {
        return Producer.getAllProducers();
    }

    public int getNumProducers() {
        return Producer.getNumProducers();
    }

    public ProducerSpi getProducer(ProducerUID uid) {
        return Producer.getProducer(uid); 
    }

    public ProducerSpi destroyProducer(ProducerUID uid, String info) {
        return Producer.destroyProducer(uid, info);
    }

    public ProducerSpi getProducer(String creator) {
        return Producer.getProducer(creator);
    }

    /***********************************************
     * Destination static methods
     ************************************************/

    public DestinationSpi[] getDestination(PartitionedStore ps, DestinationUID duid) {
        return destinationList.getDestination(ps, duid);
    }

    public DestinationSpi[] getDestination(PartitionedStore ps, String name, boolean isQueue)
    throws IOException, BrokerException {
        return destinationList.getDestination(ps,name, isQueue);
    }

    public DestinationSpi[] getDestination(PartitionedStore ps, DestinationUID duid, int type,
                                                  boolean autocreate, boolean store)
                                                  throws IOException, BrokerException {
        return destinationList.getDestination(ps, duid, type, autocreate, store);
    }

    public DestinationSpi[] getDestination(PartitionedStore ps, String name, int type,
                                                boolean autocreate, boolean store)
                                                throws IOException, BrokerException {
         return destinationList.getDestination(ps, name, type, autocreate, store);
    }

    public DestinationSpi[] createTempDestination(PartitionedStore ps, String name,
        int type, ConnectionUID uid, boolean store, long time)
        throws IOException, BrokerException {

        return destinationList.createTempDestination(ps, name, type, uid, store, time);
    }

    public List[] findMatchingIDs(PartitionedStore ps, DestinationUID wildcarduid)
        throws PartitionNotFoundException {
        return  destinationList.findMatchingIDs(ps, wildcarduid);
    }

    public DestinationSpi[] removeDestination(PartitionedStore ps,
        String name, boolean isQueue, String reason)
        throws IOException, BrokerException {
        return destinationList.removeDestination(ps, name, isQueue, reason);
    }

    public DestinationSpi[] removeDestination(PartitionedStore ps, DestinationUID uid,
        boolean notify, String reason) throws IOException, BrokerException {
        return destinationList.removeDestination(ps, uid, notify, reason);
    }

    public boolean canAutoCreate(boolean queue) {
        return destinationList.canAutoCreate(queue);
    }

    /********************************************
     * Consumer static methods
     **********************************************/

    public ConsumerSpi getConsumer(ConsumerUID uid) {
        return Consumer.getConsumer(uid);
    }

    public int calcPrefetch(ConsumerSpi consumer,  int cprefetch) {
        return Consumer.calcPrefetch((Consumer)consumer, cprefetch);
    }

}
