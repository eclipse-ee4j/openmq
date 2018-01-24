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
 * @(#)ConsumerHandler.java
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ConsumerAlreadyAddedException;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.Subscription;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.lists.OutOfLimitsException;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.license.*;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.common.handlers.ClientIDHandler;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;


/**
 * Handler class which deals with adding and removing interests from the RouteTable
 */
public class ConsumerHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;

    private static boolean DEBUG_CLUSTER_TXN =
        Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.debug.txn");
    private static boolean DEBUG_CLUSTER_MSG =
        Globals.getConfig().getBooleanProperty(Globals.IMQ + ".cluster.debug.msg");

    private DestinationList DL = Globals.getDestinationList();

    static {
        if (!DEBUG) DEBUG = DEBUG_CLUSTER_TXN || DEBUG_CLUSTER_MSG;
    }

    private FaultInjection fi = null;

    public ConsumerHandler() {
        fi = FaultInjection.getInjection();
    }

    boolean ALWAYS_WAIT_ON_DESTROY = Globals.getConfig().getBooleanProperty(Globals.IMQ + ".waitForConsumerDestroy");


    /**
     * Method to handle Consumer(add or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
        boolean sessionPaused = false;
        boolean conPaused = false;
        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,"Unable to retrieve "+
                " properties from consumer message " + msg, ex);
        }
        if (props == null) {
            props = new Hashtable();
        }

        Long lsessionid = (Long)props.get("JMQSessionID");


        Session session = null;


        String err_reason = null;

        Boolean blockprop = (Boolean)props.get("JMQBlock");

        Consumer newc = null;

        assert blockprop == null || msg.getPacketType() == PacketType.DELETE_CONSUMER
               : msg;

        boolean blockprop_bool = (blockprop != null && blockprop.booleanValue());


        boolean isIndemp = msg.getIndempotent();


        // OK ... set up the reply packet
        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID()); // correlation ID
        Hashtable hash = new Hashtable();
        pkt.setPacketType(msg.getPacketType() + 1);

        int status = Status.OK;
        String warning = BrokerResources.W_ADD_CONSUMER_FAILED;

        ConsumerUID uid = null;
        Integer destType = null;
        Integer oldid = null;
        Subscription sub = null;
        try {
            DL.acquirePartitionLock(true);
            try {

            con.suspend();
            conPaused = true;
            if (msg.getPacketType() == PacketType.ADD_CONSUMER) {
                if (DEBUG) {
                    logger.log(Logger.INFO, "ConsumerHandler: "+
                    "[Received AddConsumer message {0}]", msg.toString());
                }
                pkt.setPacketType(PacketType.ADD_CONSUMER_REPLY);
                if (lsessionid == null) {
                    if (DEBUG) {
                        logger.log(Logger.INFO, "ConsumerHandler: not Raptor consumer packet (no session id)");
                    }
                    // assign session same # as consumer
                    SessionUID sessionID = new SessionUID(
                               con.getConnectionUID().longValue());
                    // single threaded .. we dont have to worry about
                    // someone else creating it
                    session = con.getSession(sessionID);
                    if (session == null) {
                       session = Session.createSession(sessionID, 
                                  con.getConnectionUID(), null, coreLifecycle);
                       con.attachSession(session);
                    }
                } else {
                    SessionUID sessionID = new SessionUID(lsessionid.longValue());
                    session = con.getSession(sessionID);
                    if (session == null) {
                        throw new BrokerException("Internal Error: client set invalid"
                         + " sessionUID " + sessionID + " session does not exist");
                    }
                }

                if (blockprop_bool) { // turn off all processing
                   session.pause("Consumer - Block flag");
                   sessionPaused = true;
                }


                /* XXX-LKS KLUDGE FOR 2.0 compatibility */
                
                // for now, we just pass the consumer ID back on the old
                // packet .. I need to revisit this in the future
                oldid = (Integer)props.get("JMQConsumerID"); // old consumer ID
                if (oldid != null) {
                    hash.put("JMQOldConsumerID", oldid);
                }

                Integer inttype = (Integer )props.get("JMQDestType");
                int type = (inttype == null ? -1 : inttype.intValue());
                if (type == -1) {
                    throw new BrokerException(Globals.getBrokerResources().getString(
                   BrokerResources.X_INTERNAL_EXCEPTION,"Client is not sending DestType, "
                         + "unable to add interest"));
                }

                boolean queue = DestType.isQueue(type) ;

                String destination = (String)props.get("JMQDestination");
                String selector =  (String)props.get("JMQSelector");
                //JMS spec
                if (selector != null && selector.trim().length() == 0) {
                    selector = null;
                }
                boolean mqshare = false; 
                boolean jmsshare = false; //JMS2.0
                boolean nolocal = false;
                Boolean b = (Boolean)props.get("JMQNoLocal");
                if (b != null && b.booleanValue()) {
                    nolocal = true;              
                }
                b  = (Boolean)props.get("JMQShare");
                if (b != null && b.booleanValue()) {
                    mqshare = true;                   
                }
                b = (Boolean)props.get("JMQJMSShare"); //JMS2.0
                if (b != null && b.booleanValue()) {
                    jmsshare = true;                   
                }
                String durablename = (String)props.get("JMQDurableName");
                String subscriptionName = (String)props.get("JMQSharedSubscriptionName"); //JMS2.0

                String clientid = getClientID(props, con);
                Boolean reconnect = (Boolean)props.get("JMQReconnect");
                Integer size = (Integer)props.get("JMQSize");
                if (mqshare && jmsshare) {
                    String emsg = "Client protocol error: both JMQShare and JMQJMSShare set to true";
                    Globals.getLogger().log(Logger.ERROR, emsg); 
                    throw new BrokerException(emsg);
                }
                boolean shared = (mqshare || jmsshare);

                boolean durable = false;
                if (durablename != null) {
                    if (subscriptionName != null) {
                        Object[] args = { Subscription.getDSubLogString(clientid, durablename),
                                          ""+destination, subscriptionName };
                        logger.log(Logger.INFO, br.getKString(
                                   br.I_ADD_CONSUMER_IGNORE_SUBSCRIPTION_NAME, args));
                    }
                    subscriptionName = durablename; 
                    durable = true;
                }

                if (DestType.isTemporary(type)) { 
                    if (durable) {
                        String emsg = br.getKString(br.X_INVALID_DEST_DURA_CONSUMER, 
                                      ""+destination, ""+subscriptionName);
                        logger.log(Logger.ERROR, emsg);
                        throw new BrokerException(emsg, 
                            br.X_INVALID_DEST_DURA_CONSUMER, null,
                            Status.PRECONDITION_FAILED);
                    }
                    if (shared) {
                        String emsg = br.getKString(br.X_INVALID_DEST_SHARE_CONSUMER, 
                                      ""+destination, ""+subscriptionName);
                        logger.log(Logger.ERROR, emsg);
                        throw new BrokerException(emsg, 
                            br.X_INVALID_DEST_SHARE_CONSUMER, null,
                            Status.PRECONDITION_FAILED);
                    }
                }

                ConsumerParameters pm = new ConsumerParameters();
                pm.isqueue = queue;
                pm.destination = destination;
                pm.selector = selector;
                pm.clientid = clientid;
                pm.subscriptionName = subscriptionName;
                pm.durable = durable;
                pm.shared = shared; 
                pm.jmsshare =jmsshare; 
                pm.nolocal = nolocal;

                checkSubscriptionName(pm);
                checkClientID(pm);
                checkNoLocal(pm);

                if (reconnect != null && reconnect.booleanValue()) {
                    Globals.getLogger().log(Logger.ERROR,
                        BrokerResources.E_INTERNAL_BROKER_ERROR,
                        "JMQReconnect not implemented");
                }

                // see if we are a wildcard destination
                DestinationUID dest_uid = null;

                Destination d = null;

                if (DestinationUID.isWildcard(destination)) { // dont create a destination
                    dest_uid = DestinationUID.getUID(destination, DestType.isQueue(type));

                } else {
                    d = null;
                    Destination[] ds = null;
                    while (true ) {
                       ds =  DL.getDestination(con.getPartitionedStore(), destination,
                                     type, true /* autocreate if possible*/,
                                     !con.isAdminConnection());
                       d = ds[0]; //PART
                       if (d == null) {
                           break;
                       }
                       if (d.isAutoCreated())
                           warning = BrokerResources.W_ADD_AUTO_CONSUMER_FAILED;
                       try {
                           d.incrementRefCount();
                       } catch (BrokerException ex) {
                           continue; // was destroyed in process
                       } catch (IllegalStateException ex) {
                            throw new BrokerException(
                                Globals.getBrokerResources().getKString(
                                BrokerResources.X_SHUTTING_DOWN_BROKER),
                                BrokerResources.X_SHUTTING_DOWN_BROKER,
                                ex,
                                Status.ERROR);
                       }
                       break; // we got one
                    }
    
                    if (d == null) {
                        // unable to autocreate destination
                        status  = Status.NOT_FOUND;
                        // XXX error
                        throw new BrokerException(
                            Globals.getBrokerResources().getKString(
                                BrokerResources.X_DESTINATION_NOT_FOUND, destination),
                                BrokerResources.X_DESTINATION_NOT_FOUND,
                                null,
                                Status.NOT_FOUND);
                    }
                    dest_uid = d.getDestinationUID();
                }

                if (jmsshare && mqshare) {
                    Object[] args = { "JMS", 
                                      (!durable ?
                                       Subscription.getNDSubLongLogString(clientid, 
                                          dest_uid, selector, subscriptionName, nolocal):
                                       Subscription.getDSubLogString(clientid, subscriptionName)),
                                      ""+destination, "JMQShare" };
                    logger.log(Logger.INFO, br.getKString(
                               br.I_ADD_SHARE_CONSUMER_IGNORE_CLIENT_FLAG, args));
                    mqshare = false;
                }

                Consumer c = null;
                
                try { 
//LKS
                    Consumer[] retc = _createConsumer( dest_uid,  con,
                         session, selector,  clientid, 
                         subscriptionName, durable, shared, jmsshare, nolocal,
                         (size == null ? -1 : size.intValue()), 
                         msg.getSysMessageID().toString(),  isIndemp, true);

                    c = retc[0];
                    newc = retc[1];
                    sub = (Subscription)retc[2];
                    if (c.getPrefetch() != -1 || size != null) {
                        hash.put("JMQSize", c.getPrefetch());
                    }

                } catch (SelectorFormatException ex) {
                      throw new BrokerException(
                            Globals.getBrokerResources().getKString(
                            BrokerResources.W_SELECTOR_PARSE, ""+selector),
                            BrokerResources.W_SELECTOR_PARSE,
                            ex,
                            Status.BAD_REQUEST);
                } catch (OutOfLimitsException ex) {
                    if (d != null && d.isQueue()) {
                        String args[] = { dest_uid.getName(),
                            String.valueOf(d.getActiveConsumerCount()),
                            String.valueOf(d.getFailoverConsumerCount()) };
                        throw new BrokerException(
                            Globals.getBrokerResources().getKString(
                            BrokerResources.X_S_QUEUE_ATTACH_FAILED, args),
                            BrokerResources.X_S_QUEUE_ATTACH_FAILED,
                            ex,
                            Status.CONFLICT);
                    } else { // durable
                        String args[] = { Subscription.getDSubLogString(clientid, durablename),
                                          dest_uid.getName(),
                                          String.valueOf(ex.getLimit()) };
                        throw new BrokerException(
                            Globals.getBrokerResources().getKString(
                            BrokerResources.X_S_DUR_ATTACH_FAILED, args),
                            BrokerResources.X_S_DUR_ATTACH_FAILED,
                            ex,
                            Status.CONFLICT);
                    }
                } finally {
                    if (d != null) {
                        d.decrementRefCount();
                    }
                }

                // add the consumer to the session
        
                Integer acktype = (Integer)props.get("JMQAckMode");
                if (acktype != null) {
                    c.getConsumerUID().setAckType(acktype.intValue());
                }

                uid = c.getConsumerUID();
                if (props.get("JMQOldConsumerID") != null ) {
                    Object[] args = { uid+(sub == null ? "":"["+sub+"]"),
                                      ""+dest_uid,  props.get("JMQOldConsumerID") }; 
                    logger.log(Logger.INFO, br.getKString(br.I_CREATED_NEW_CONSUMER_FOR_OLD, args));
                }

            } else { // removing Interest
                if (DEBUG) {
                    logger.log(Logger.INFO, "ConsumerHandler: "+
                    "[Received DestroyConsumer message {0}]", msg.toString());
                }

                warning = BrokerResources.W_DESTROY_CONSUMER_FAILED;
                pkt.setPacketType(PacketType.DELETE_CONSUMER_REPLY);

                String durableName = (String)props.get("JMQDurableName");
                String clientID = getClientID(props, con);
                Long cid = (Long)props.get("JMQConsumerID");
                uid = (cid == null ? null :  new ConsumerUID( cid.longValue()));

                if (lsessionid != null) { //  passed on in
                    SessionUID sessionID = new SessionUID(lsessionid.longValue());
                    session = con.getSession(sessionID);
                } else {
                    session = Session.getSession(uid);
                }
                if (session == null && durableName == null && !isIndemp) {
                     if (con.getConnectionState() < Connection.STATE_CLEANED) {
                         logger.log(Logger.ERROR, br.getKString(br.E_UNEXPECTED_EXCEPTION, 
                             br.getKString(br.E_DELETE_CONSUMER_NO_SESSION, 
                             (lsessionid == null ? "":lsessionid), uid+"")+"\n"+
                             com.sun.messaging.jmq.io.PacketUtil.dumpPacket(msg)));
                         Session.dumpAll();
                     }
                }

                // retrieve the LastDelivered property
                Integer bodytype = (Integer)props.get("JMQBodyType");
                int btype = (bodytype == null ? 0 : bodytype.intValue());
                
                SysMessageID lastid = null;
                boolean lastidInTransaction = false;

                if (btype == PacketType.SYSMESSAGEID) {
                    int size = msg.getMessageBodySize();
                    if (size == 0) {
                        logger.log(Logger.INFO,"Warning, bad body in destroy consumer");
                     } else {
                         DataInputStream is = new DataInputStream(
                                msg.getMessageBodyStream());
                         lastid = new SysMessageID();
                         lastid.readID(is);
                         Boolean val = (Boolean)props.get("JMQLastDeliveredIDInTransaction");
                         lastidInTransaction = (val == null ? false : val.booleanValue());
                    
                     }
                }
                if (DEBUG && lastid != null) {
                    logger.log(Logger.INFO, "ConsumerHandler: destroy consumer with lastID ["+
                        lastid+", "+lastidInTransaction+"]"+
                        DL.get(con.getPartitionedStore(), lastid)+" for consumer "+uid);
                }

                Boolean rAll = (Boolean)props.get("JMQRedeliverAll");
                boolean redeliverAll = (rAll == null ? false : rAll.booleanValue());

                if (!sessionPaused && session != null) {
                    sessionPaused = true;
                    session.pause("Consumer removeconsumer");
                }
                destroyConsumer(con, session, uid, durableName, clientID, 
                    lastid, lastidInTransaction, redeliverAll, isIndemp);

            }

            } finally {
            DL.releasePartitionLock(true);
            }

        } catch (BrokerException ex) {

            status = ex.getStatusCode();
            String consumid = null;
            String destination = null;
            try {
                destination = (String) props.get("JMQDestination");
                if (destination == null && msg.getPacketType() 
                     != PacketType.ADD_CONSUMER)
                     destination = "";
                if (oldid != null)
                    consumid = oldid.toString();
                else
                    consumid = "";
            } catch (Exception ex1) {}
            String args[] = {consumid, con.getRemoteConnectionString(), destination};

            err_reason = ex.getMessage();

            if (ex.getStatusCode() == Status.PRECONDITION_FAILED
                  || ex.getStatusCode() == Status.CONFLICT ) {
                logger.log(Logger.WARNING, warning, args, ex);
            } else if (ex.getStatusCode() == Status.BAD_REQUEST) {
                // Probably a bad selector
                logger.log(Logger.WARNING, warning, args, ex);
                if (ex.getCause() != null) {
                    logger.log(Logger.INFO, ex.getCause().toString());
                }
            } else {
                if (isIndemp && msg.getPacketType() == PacketType.DELETE_CONSUMER) {
                    logger.logStack(Logger.DEBUG, "Reprocessing Indempotent message for "
                          + "{0} on destination {2} from {1}",args, ex);
                    status = Status.OK;
                    err_reason = null;
                } else {
                    logger.logStack(Logger.WARNING, warning,args, ex);
                }
            }
        } catch (IOException ex) {
            logger.logStack(Logger.WARNING, "Unable to process "+
                " consumer request " + msg, ex);
            props = new Hashtable();
            err_reason = ex.getMessage();
            assert false;
        } catch (SecurityException ex) {
            status = Status.FORBIDDEN;
            err_reason = ex.getMessage();
            String destination = null;
            String consumid = null;
            try {
                destination = (String) props.get("JMQDestination");
                if (oldid != null)
                    consumid = oldid.toString();
            } catch (Exception ex1) {}
            logger.log(Logger.WARNING, warning, destination, consumid,ex);
        } finally {
            if (conPaused) {
                con.resume();
            }
        }
        hash.put("JMQStatus", Integer.valueOf(status));

        if (err_reason != null)
            hash.put("JMQReason", err_reason);

        if (uid != null) {
            hash.put("JMQConsumerID", Long.valueOf(uid.longValue()));
        }

        if (destType != null)
            hash.put("JMQDestType", destType);

        if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
            hash.put("JMQReqID", msg.getSysMessageID().toString());

        pkt.setProperties(hash);
        con.sendControlMessage(pkt);

        if (sessionPaused)
             session.resume("Consumer - session was paused");

        if (sub != null)
            sub.resume("Consumer - added to sub");
        
        if (newc != null)
            newc.resume("Consumer - new consumer");

        return true;
    }

    private String getClientID(Hashtable props, Connection con) {

        String clientid = (String)con.getClientData(IMQConnection.CLIENT_ID);
        if (clientid == null) {
            clientid = (String)props.get("JMQClientID");
            if (clientid != null) {
                logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Client did not send SET_CLIENTID before adding/removing a consumer, retrieved clientid "+
                 clientid + " from packet properties");
            }
        }
        return clientid;
    } 


    public void destroyConsumer(IMQConnection con, Session session, ConsumerUID uid, 
               String durableName, String clientID, SysMessageID lastid, boolean lastidInTransaction,
               boolean redeliverAll, boolean isIndemp)
               throws BrokerException {

        if (durableName != null) {
            Subscription usub = Subscription.unsubscribe(durableName, clientID);
                    
             if (usub == null) { // already destroyed
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                        BrokerResources.X_UNKNOWN_DURABLE_INTEREST,
                        Subscription.getDSubLogString(clientID, durableName)),
                        Status.NOT_FOUND);
             }
             DestinationUID dest_uid = usub.getDestinationUID();
             Destination[] ds = DL.getDestination(con.getPartitionedStore(), dest_uid);
             Destination d = ds[0];
             if (d != null) {
                 d.removeConsumer(uid, true);
             }
        } else {
            boolean redeliver = false;
            if (con.getClientProtocolVersion() < Connection.RAPTOR_PROTOCOL ) {
                redeliver = true;
            }

            if (session == null && !isIndemp) {
                if (con.getConnectionState() >= Connection.STATE_CLOSED) {
                    throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_CONNECTION_CLOSING, con.getConnectionUID()), Status.NOT_FOUND);
                } else {
                    throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_CONSUMER_SESSION_NOT_FOUND, uid, con.getConnectionUID()), Status.NOT_FOUND);
                }
            }

            if (session != null) { // should only be null w/ indemp
                Consumer c = (Consumer)session.detatchConsumer(uid, lastid,
                                 lastidInTransaction, redeliver, redeliverAll);
                if (DEBUG) {
                logger.log(Logger.INFO, "ConsumerHandler: closed consumer "+c+
                    ", with {lastid="+lastid+", lastidInTransaction="+
                     lastidInTransaction+", redeliver="+redeliver+ ", redeliverAll="+
                     redeliverAll+", isindemp="+isIndemp+"}");
                }
                DestinationUID dest_uid = c.getDestinationUID();
                Destination[] ds = DL.getDestination(con.getPartitionedStore(), dest_uid);
                Destination d = ds[0];
                if (d != null)
                    d.removeConsumer(uid, true);
            }
        }
    }

    public Consumer[] createConsumer(DestinationUID dest_uid, IMQConnection con,
        Session session, String selectorstr, String clientid, 
        String subscriptionName,  boolean durable, 
        boolean shared, boolean jmsshare, boolean nolocal,
        int size, String consumerString, boolean isIndemp, boolean useFlowControl)
        throws BrokerException, SelectorFormatException, IOException {

        ConsumerParameters pm = new ConsumerParameters();
        pm.isqueue = dest_uid.isQueue();
        pm.destination = dest_uid.toString();
        pm.selector = selectorstr;
        pm.clientid = clientid;
        pm.subscriptionName = subscriptionName;
        pm.durable = durable;
        pm.shared = shared; 
        pm.jmsshare = jmsshare; 
        pm.nolocal = nolocal;

        checkSubscriptionName(pm);
        checkClientID(pm);
        checkNoLocal(pm);

        return _createConsumer(dest_uid, con, session, selectorstr, clientid,
            subscriptionName, durable, shared, jmsshare, nolocal,
            size, consumerString, isIndemp, useFlowControl);
    }

    private Consumer[] _createConsumer(DestinationUID dest_uid, IMQConnection con,
        Session session,String selectorstr, String clientid, 
        String subscriptionName,  boolean durable, 
        boolean shared, boolean jmsshare, boolean nolocal,
        int size, String consumerString, boolean isIndemp, boolean useFlowControl)
        throws BrokerException, SelectorFormatException, IOException {

        Consumer c = null;
        Consumer newc = null;
        Subscription sub = null;
        String selector = selectorstr;
        if (selectorstr != null && selectorstr.trim().length() == 0) {
            selector = null;
        }
        // need to deal w/ reconnection AND ackType

        try {

        int prefetch = -1;
        if (isIndemp) { // see if we already created it
            c = Consumer.getConsumer(consumerString);
            if (c != null) 
                prefetch = c.getPrefetch();
        }

        if (c == null) {
            c = new Consumer(dest_uid, selector,
                    nolocal, 
                    con.getConnectionUID());
            c.setCreator(consumerString);
            newc = c;
            newc.pause("Consumer: new consumer");

            // OK, determine if we are a wildcard or not
            Destination[] ds = DL.getDestination(con.getPartitionedStore(), dest_uid);
            Destination d = ds[0]; //PART
            boolean wildcard = dest_uid.isWildcard();

            // NOTE: if d == null, wildcard == true

            int cprefetch = size;
            int dprefetch = (wildcard ? -1 : (!shared
                    ? d.getMaxPrefetch()
                    : d.getSharedConsumerFlowLimit()));
                                       
            prefetch = (dprefetch == -1) ?
                    cprefetch :
                    (cprefetch == -1 ? cprefetch  
                    : (cprefetch > dprefetch?
                    dprefetch : cprefetch));
            c.setPrefetch(prefetch,useFlowControl);

            // actual subscription added to the destination
            if (subscriptionName != null && durable) {
                if (!shared) {
                    logger.log(Logger.INFO,  br.getKString(br.I_CREATE_UNSHARED_DURA,
                        Subscription.getDSubLogString(clientid, subscriptionName), dest_uid));
                } else {
                    logger.log(Logger.INFO,  br.getKString(br.I_CREATE_SHARED_DURA,
                        Subscription.getDSubLogString(clientid, subscriptionName)+
                                     (jmsshare ? "jms":"mq"), dest_uid));
                }
                // durable
                // get the subscription ... this may throw
                // an exception IF we cant 
                sub = Subscription.
                          findCreateDurableSubscription(clientid,
                              subscriptionName, shared, jmsshare, dest_uid,
                              selector, nolocal, true);
                sub.setCreator(consumerString);
                sub.pause("Consumer attaching to durable");

                // add the consumer .. this may throw an
                // exception IF
                sub.attachConsumer(c, con);
                c.localConsumerCreationReady();
   
                Map<PartitionedStore, LinkedHashSet<Destination>> dmap = 
                    DL.findMatchingDestinationMap(null, dest_uid);
                LinkedHashSet dset = null;
                Iterator<LinkedHashSet<Destination>> itr = dmap.values().iterator();
                boolean notify = true;
                while (itr.hasNext()) {
                    dset = itr.next();
                    if (dset == null) {
                        continue;
                    }
                    Iterator<Destination> itr1 = dset.iterator();
                    while (itr1.hasNext()) {
                        Destination dd = itr1.next();
                        if (dd == null) {
                            continue;
                        }
                        Subscription oldsub = null;
                        try { 
                             oldsub = (Subscription)dd.addConsumer(sub, notify, con);
                        } catch (ConsumerAlreadyAddedException e) {
                             logger.log(logger.INFO, e.getMessage());
                        }
                        if (oldsub != null) {
                            oldsub.purge();
                        }
                    }
                    notify = false;
                }
                sub.sendCreateSubscriptionNotification(c);
            } else if ((wildcard || !d.isQueue()) && shared) {
                logger.log(Logger.INFO,  br.getKString(br.I_CREATE_SHARED_NONDURA_SUB,
                           Subscription.getNDSubLongLogString(clientid, dest_uid, 
                           selectorstr, subscriptionName, nolocal), ""+dest_uid));
                sub = Subscription.createAttachNonDurableSub(c, con,
                                   subscriptionName, shared, jmsshare);
                c.localConsumerCreationReady();
                if (sub != null) {
                    sub.pause("Consumer: attaching to nondurable");
                    Map<PartitionedStore, LinkedHashSet<Destination>> dmap = 
                        DL.findMatchingDestinationMap(null, dest_uid);
                    LinkedHashSet dset = null;
                    Iterator<LinkedHashSet<Destination>> itr = dmap.values().iterator();
                    while (itr.hasNext()) {
                        dset = itr.next();
                        if (dset == null) {
                            continue;
                        }
                        Iterator<Destination> itr1 = dset.iterator();
                        while (itr1.hasNext()) {
                            Destination dd = itr1.next();
                            if (dd != null) {
                                dd.addConsumer(sub, true, con);
                            }
                        }
                    }
                }
                c.attachToConnection(con.getConnectionUID());
                if (sub != null)
                    sub.sendCreateSubscriptionNotification(c);
            } else {
                c.localConsumerCreationReady();
            	// non-durable
                List dests = null;
                Destination dd = null;
                Map<PartitionedStore, LinkedHashSet<Destination>> dmap = 
                               DL.findMatchingDestinationMap(null, dest_uid);
                LinkedHashSet dset = null;
                Iterator<LinkedHashSet<Destination>> itr = dmap.values().iterator();
                while (itr.hasNext()) {
                    dset = itr.next();
                    if (dset == null) {
                        continue;
                    }
                    Iterator<Destination> itr1 = dset.iterator();
                    while (itr1.hasNext()) {
                        dd = itr1.next();
                        if (dd != null) {
                            dd.addConsumer(c, true, con);
                        }
                    }
                }
                c.attachToConnection(con.getConnectionUID());
                c.sendCreateConsumerNotification();
            }
        }

        if (fi.FAULT_INJECTION) {
            //e.g. imqcmd debug fault -n consumer.add.1 -o selector="mqDestinationName = 'T:t0.*'" -debug
            HashMap fips = new HashMap();
            fips.put(FaultInjection.DST_NAME_PROP,
                     DestinationUID.getUniqueString(dest_uid.getName(), dest_uid.isQueue()));
            fi.checkFaultAndSleep(FaultInjection.FAULT_CONSUMER_ADD_1, fips);
        }
 
        session.attachConsumer(c);
        if (DEBUG) {
            logger.log(logger.INFO, "Attached consumer "+c+
            "[prefetch="+c.getPrefetch()+", useConsumerFlowControl="+useFlowControl+"] to session "+session);
        }

        Consumer[] retc = new Consumer[3];
        retc[0]=c;
        retc[1]=newc;
        retc[2]=sub;
        return retc;
    } catch (Exception e) {
        Object[] args = { (durable ?
                           Subscription.getDSubLogString(clientid, 
                                                  subscriptionName):
                           (shared ? Subscription.getNDSubLongLogString(clientid, 
                                     dest_uid, selector, subscriptionName, nolocal):"")),
                           con, dest_uid };
        String emsg = Globals.getBrokerResources().getKString(
                          BrokerResources.W_ADD_AUTO_CONSUMER_FAILED, args);
        logger.logStack(logger.ERROR, emsg, e);
        try {
            if (c != null) {
                try {
                    session.detatchConsumer(c.getConsumerUID(), null, false, false, false);
                } catch (Exception e1) {
                    try {
                    c.destroyConsumer((new HashSet()), null, true, false, true);
                    } catch (Exception e2) {}
                };
            }
            Map<PartitionedStore, LinkedHashSet<Destination>> dmap = 
                           DL.findMatchingDestinationMap(null, dest_uid);
            LinkedHashSet dset = null;
            Destination dd = null;
            Iterator<LinkedHashSet<Destination>> itr = dmap.values().iterator();
            while (itr.hasNext()) {
                dset = itr.next();
                if (dset == null) {
                    continue;
                }
                Iterator<Destination> itr1 = dset.iterator();
                while (itr1.hasNext()) {
                    dd = itr1.next();
                    if (dd == null) {
                        continue;
                    }
                    try {
                        if (c != null) {
                            dd.removeConsumer(c.getConsumerUID(), true);
                        }
                        if (sub != null) {
                            dd.removeConsumer(sub.getConsumerUID(), true);
                        }
                    } catch (Exception e1){}
                }
            }
            try {
                if (sub != null && c != null) {
                    sub.releaseConsumer(c.getConsumerUID());
                }
            } catch (Exception e1) {}

            if (subscriptionName != null && durable) {
                if (sub != null && sub.getCreator() != null && 
                    sub.getCreator().equals(consumerString)) {
                    try {
                        Subscription.unsubscribe(subscriptionName, clientid);
                    } catch (Exception e1) { }
                }
            }
        } catch (Exception e3) {}

        if (e instanceof BrokerException) {
            throw (BrokerException)e;
        }
        if (e instanceof IOException) {
            throw (IOException)e;
        }
        if (e instanceof SelectorFormatException) {
            throw (SelectorFormatException)e;
        }
        throw new BrokerException(emsg, e);
    }
    }

    private static class ConsumerParameters {
        boolean isqueue = true;
        String destination = null;
        String selector = null;
        String clientid = null;
        String subscriptionName = null;
        boolean durable = true;
        boolean shared = true;
        boolean jmsshare = true;
        boolean nolocal = true;
    }

    private void checkNoLocal(ConsumerParameters pm)
        throws BrokerException {

        if (!pm.nolocal) {
            return;
        }
        if (pm.isqueue) {
            Globals.getLogger().log(Logger.ERROR, 
                BrokerResources.E_INTERNAL_BROKER_ERROR,
                "NoLocal is not supported on Queue Receivers");
            throw new BrokerException(
                "Unsupported property on queues JMQNoLocal "+
                 "is set to " + pm.nolocal, Status.ERROR);
        }

        /**
         * JMS 2.0 late update has removed noLocal for JMS 2.0 shared subscription
         * Therefore the following should never happen after checkClientID() OK
         */
        if (pm.clientid == null) { //JMS 2.0 
            if (pm.durable) {
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.X_NO_CLIENTID_NOLOCAL_DURA, 
                              ""+pm.subscriptionName);
                Globals.getLogger().log(Logger.ERROR, emsg); 
                throw new BrokerException(emsg, 
                    BrokerResources.X_NO_CLIENTID_NOLOCAL_DURA, null, 
                    Status.PRECONDITION_FAILED);
            }
            if (pm.shared) {
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.X_NO_CLIENTID_NOLOCAL_SHARE, 
               "["+pm.clientid+":"+pm.destination+":"+pm.selector+":"+pm.subscriptionName+"]");
                Globals.getLogger().log(Logger.ERROR, emsg); 
                throw new BrokerException(emsg, 
                    BrokerResources.X_NO_CLIENTID_NOLOCAL_SHARE, 
                    null, Status.PRECONDITION_FAILED);
            }
        }
    }

    private void checkSubscriptionName(ConsumerParameters pm)
        throws BrokerException {
        if (pm.isqueue && pm.subscriptionName != null) {
            String emsg = "Protocol error: Unexpected subscription name for Queue";
            Globals.getLogger().log(Logger.ERROR, emsg);
            throw new BrokerException(emsg);
        }
        if (pm.durable && 
            (pm.subscriptionName == null || pm.subscriptionName.trim().equals(""))) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_INVALID_SUBSCRIPTION_NAME_DURA, 
                    ""+pm.subscriptionName, ""+pm.destination),
                    BrokerResources.X_INVALID_SUBSCRIPTION_NAME_DURA, null,
                    Status.PRECONDITION_FAILED);
             
        }

        if (pm.shared && pm.jmsshare &&
            (pm.subscriptionName == null || pm.subscriptionName.trim().equals(""))) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_INVALID_SUBSCRIPTION_NAME_SHARE, 
                    ""+pm.subscriptionName, ""+pm.destination),
                    BrokerResources.X_INVALID_SUBSCRIPTION_NAME_SHARE, null,
                    Status.PRECONDITION_FAILED);
             
        }
    }

    private void checkClientID(ConsumerParameters pm)
        throws BrokerException {
        if (pm.isqueue) {
            return;
        }
        if (pm.durable && !pm.jmsshare && pm.clientid == null) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_NO_CLIENTID, ""+pm.subscriptionName),
                    BrokerResources.X_NO_CLIENTID, null,
                    Status.PRECONDITION_FAILED);
        }
        if (!pm.durable && pm.shared && 
            !pm.jmsshare && pm.clientid == null) {
            throw new BrokerException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_NON_DURABLE_SHARED_NO_CLIENTID, ""+pm.destination),
                    BrokerResources.X_NON_DURABLE_SHARED_NO_CLIENTID, null,
                    Status.PRECONDITION_FAILED);
        }
        if (pm.durable || pm.shared) {
            if (pm.clientid != null && pm.clientid.trim().length() == 0) {
               throw new BrokerException(
                   Globals.getBrokerResources().getKString(
                       BrokerResources.X_INVALID_CLIENTID, pm.clientid),
                       BrokerResources.X_INVALID_CLIENTID, null,
                       Status.PRECONDITION_FAILED);
            }
        }
    }
}
