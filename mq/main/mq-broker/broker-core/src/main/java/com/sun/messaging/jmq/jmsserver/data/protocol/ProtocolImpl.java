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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.data.protocol;


import java.io.IOException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Arrays;

import javax.transaction.xa.XAResource;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Producer;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.core.MessageDeliveryTimeInfo;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.RollbackReason;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.handlers.AckHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.ClientIDHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.FlowHandler;
import com.sun.messaging.jmq.jmsserver.common.handlers.SessionHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.ConsumerHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.DataHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.ProducerHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.QBrowseHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.RedeliverHandler;
import com.sun.messaging.jmq.jmsserver.data.handlers.TransactionHandler;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.MaxConsecutiveRollbackException;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;



/**
 * 
 * Api used for direct access to the broker. This code is not integrated into 
 * the handler classes because the interaction (e.g. when callbacks happen) 
 * is different for the jms protocol and other protocols.
 */


public class ProtocolImpl implements Protocol
{

    private static boolean DEBUG = false; 

    PacketRouter pr = null; 
    private DestinationList DL = Globals.getDestinationList();

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();

    public ProtocolImpl(PacketRouter router)
    {
        pr = router;
    }

    public boolean getDEBUG() {
        return DEBUG;
    }

    /**
     * called when a new connection is created.
     * <P>Packet:<B>HELLO,HELLO_REPLY</b></p>
     */

    public void hello() {
        // does nothing
    }

    /**
     * Authenticate with the passed in username, password
     * <P>Packet:<B>AUTHENTICATE</b></p>
     */
    public void authenticate(String username, String password)
        throws BrokerException
    {
        /*
         * TBD - currently does nothing
         */
    }


    /**
     * called when a connection is closed
     * <P>Packet:<B>GOODBYE</b></p>
     */
    public void goodbye() {
        // does nothing
    }

    /**
     * gets license information.
     * <P>Packet:<B>GET_LICENSE</b></p>
     *
     *@returns a hashtable with license info
     */

    public Hashtable getLicense() {
        // does nothing
       return new Hashtable();
    }

    /**
     * gets information about the broker/cluster.
     * <P>Packet:<B>INFO_REQUEST</b></p>
     *
     *@param cluster if true, return cluster information otherwise
     *               return local broker information.
     *@returns a hashtable with broker/cluster information
     */

    public Hashtable getInfo(boolean cluster) {
        // currently does nothing
        return new Hashtable();
    }

    /**
     * handles receiving a ping from a client.
     * <P>Packet:<B>PING</b></p>
     */

    public void ping() {
        // does nothing
    }


    /**
     * handles receiving a flow paused from a client.
     * <P>Packet:<B>FLOW_PAUSED</b></p>
     */
    public void flowPaused(int size) {
       throw new UnsupportedOperationException("flow paused is not supported by the client");
    }


    /**
     * Processes acknowledgements.
     * <P>Packet:<B>ACKNOWLEDGE</b></p>
     *
     *@param tid transaction id (or null if no transaction) associated
     *           with the acknowledgement.
     *@param validate  should the acks just be validated (normally false)
     *@param ackType acknowledge type of the request. One of <UL>
     *               <LI>ACKNOWLEDGE == 0 </LI><LI>UNDELIVERABLE==1</LI>
     *               <LI>DEAD</LI></UL>
     *@param exception exception associated with a dead message (should be null
     *                 if ackType != DEAD)
     *@param deadComment the explaination why a message was marked dead (should be null
     *               if ackType != DEAD)
     *@param deliverCnt number of times a dead message was delivered (should be 0
     *               if ackType != DEAD)
     *@param ids  list of message ids to process
     *@param cids list of consumerIDs associated with a message, should directly 
     *            correspond to the same index in ids
     */
    public void acknowledge(IMQConnection con, TransactionUID tid, 
                boolean validate,
                int ackType, Throwable exception, String deadComment,
                int deliverCnt, SysMessageID ids[], ConsumerUID cids[])
           throws BrokerException, IOException
    {
        if (DEBUG) {
        logger.log(Logger.INFO, 
        "ProtocolImpl.ACKNOWLEDGE:TID="+tid+", ackType="+ackType+", ids="+Arrays.toString(ids)+
        ", cids="+Arrays.toString(cids)+", validate="+validate+", deadComment="+deadComment+
        ", deliverCnt="+deliverCnt+", exception="+exception);
        }

        TransactionList[] tls = DL.getTransactionList(con.getPartitionedStore());
        TransactionList tl = tls[0];

        AckHandler handler = (AckHandler)
                      pr.getHandler(PacketType.ACKNOWLEDGE);

        ArrayList<PacketReference> cleanList = new ArrayList<PacketReference>();
        try {

        if (validate) {
            if (ackType == handler.DEAD_REQUEST ||
                ackType == handler.UNDELIVERABLE_REQUEST) {
                throw new BrokerException("Can not use JMQValidate with"
                        + " an ackType of " + ackType,
                        null,
                        Status.BAD_REQUEST);
            } else if (tid == null) {
                throw new BrokerException("Can not use JMQValidate with"
                        + " no tid",
                        null,
                        Status.BAD_REQUEST);
            } else if (!handler.validateMessages(tl, tid, ids, cids)) {
                throw new BrokerException("Acknowledgement could not be found",
                        null,
                        Status.NOT_FOUND);
            }
        } else if (ackType == handler.DEAD_REQUEST) {
           handler.handleDeadMsgs(con, ids, cids,
                        handler.DEAD_REASON_UNDELIVERABLE,
                        exception, deadComment, deliverCnt, cleanList);
        } else if (ackType == handler.UNDELIVERABLE_REQUEST) {
            handler.handleUndeliverableMsgs(con, ids, cids, cleanList, deliverCnt, false);
        
        } else if (tid != null) {
            handler.handleTransaction(tl, con, tid, ids, cids, deliverCnt);
        } else {
            handler.handleAcks(con, ids, cids, true, cleanList); //XXX ackack flag
        }

        } finally {
            handler.cleanUp(cleanList);
        }
    }

    /**
     *sets/checks the clientID.
     * <P>Packet:<B>SET_CLIENTID</b></p>
     *
     *@param con the connection to set the clientID on
     *@param clientID the clientID to set
     *@param namespace to concatonate to clientID if shared
     *                 (generally set to null)
     *@param share true if the clientID is shared
     *@throws BrokerException if the clientId can not be set
     */

    public void setClientID(IMQConnection con, 
                String clientID, 
                String namespace, 
                boolean share)
        throws BrokerException
    {
        ClientIDHandler handler = (ClientIDHandler)
                      pr.getHandler(PacketType.SET_CLIENTID);
        handler.setClientID(con, clientID, namespace, share);
    }



    /**
     * creates a producer.
     * <P>Packet:<B>ADD_PRODUCER</b></p>
     *
     *@param d the destination to create the producer on
     *@param con the conectio to use for the producer
     *@param uid a unique string used for finding the producer
     *@throws BrokerException if the producer can not be created
     */
    public Producer addProducer(Destination d, IMQConnection con, String uid, boolean acc)
        throws BrokerException
    {
        if (DEBUG) {
        logger.log(Logger.INFO,
        "ProtocolImpl.addProducer(d="+d+", uid="+uid+", acc="+acc+
        ") on conn=@"+con.hashCode()+ "["+con.getConnectionUID()+", "+con+"]");
        } 

        if (acc) {
            checkAccessPermission(PacketType.ADD_PRODUCER, d, con);
        }
        ProducerHandler handler = (ProducerHandler)
                      pr.getHandler(PacketType.ADD_PRODUCER);
        return handler.addProducer(d.getDestinationUID(), con, uid, false);
    }

    /**
     * Destroys a producer.
     * <P>Packet:<B>REMOVE_PRODUCER</b></p>
     *
     *@param d the destination to create the producer on
     *@param con the conectio to use for the producer
     *@param uid a unique string used for finding the producer
     *@throws BrokerException if the producer can not be created
     */
    public void removeProducer(ProducerUID uid, IMQConnection con,
              String suid)
        throws BrokerException
    {
        ProducerHandler handler = (ProducerHandler)
                      pr.getHandler(PacketType.ADD_PRODUCER);
        handler.removeProducer(uid, false, con, suid);
    }

    public void resumeFlow(IMQConnection con, int bufsize)
    {
        FlowHandler handler = (FlowHandler)
                      pr.getHandler(PacketType.RESUME_FLOW);
        handler.connectionFlow(con, bufsize);
    }

    /**
     * resumes flow control on a connection.
     * <P>Packet:<B>RESUME_FLOW</b></p>
     *
     *@param bufsize size of the buffer to receive (-1 indicates unlimited)
     *@param con the consumer to resume
     */
    public void resumeFlow(Consumer con, int bufsize)
    {
        FlowHandler handler = (FlowHandler)
                      pr.getHandler(PacketType.RESUME_FLOW);
        handler.consumerFlow(con, bufsize);
    }

    /**
     * mimics the behavior of DELIVER
     * <P>Packet:<B>DELIVER</b></p>
     *
     *@param cid consumer id to attach to the messages
     *@param ids a list of id's to deliver
     *@return an ArrayList of packets
     */
    public ArrayList deliver(long cid, ArrayList ids)
        throws BrokerException, IOException
    {
        ArrayList returnlist = new ArrayList();
        Iterator itr = ids.iterator();
        while (itr.hasNext()) {
            SysMessageID id = (SysMessageID)itr.next();
            PacketReference ref = DL.get(null, id);
            if (ref == null) continue;
            Packet realp = ref.getPacket();
            if (ref.isInvalid()) continue;
            Packet p = new Packet(false /* use direct buffers */);
            p.fill(realp);
            p.setConsumerID(cid);
            returnlist.add(p);
        }
        return returnlist;
    }
    
    /**
     * mimics the behavior of REDELIVER. It retrieves the messages
     * in a similar way but does not retrieve
     * <P>Packet:<B>REDELIVER</b></p>
     *The consumer should stop sessions before requeueing the
     *messages.
     *
     *@param cids consumer id to attach to the messages
     *@param ids a list of id's to deliver
     *@return an ArrayList of packets
     */
    public ArrayList redeliver(TransactionUID tid, ConsumerUID cids[],
            SysMessageID[] ids, boolean redeliver)
        throws BrokerException
    {
        // does nothing currently
        //XXX - TBD
        return new ArrayList();
    }

    

     /**
      * Creates a session and attaches it to the connection
      * <P>Packet:<B>CREATE_SESSION</b></p>
      *@param ackType acknowledge type to use
      *@param con connection to attach the session to
      */
     public Session createSession(int ackType, IMQConnection con)
        throws BrokerException
     {

        Object obj = new Object();
    
        SessionHandler handler = (SessionHandler)
                      pr.getHandler(PacketType.CREATE_SESSION);
    
        return handler.createSession(ackType, obj.toString(), con, false);
     }

     /**
      * Destroy a session 
      * <P>Packet:<B>DESTROY_SESSION</b></p>
      *@param uid sessionUID to destroy
      *@param con connection to deattach the session from
      */
     public void destroySession(SessionUID uid, IMQConnection con)
        throws BrokerException
     {
        SessionHandler handler = (SessionHandler)
                      pr.getHandler(PacketType.CREATE_SESSION);
    
        handler.closeSession(uid, con, false);
     }

     /**
      *Pause a session
      *<P>Packet:<B>STOP</b></p>
      *@param uid session to pause
      */
     public void pauseSession(SessionUID uid)
        throws BrokerException
     {
         Session ses = Session.getSession(uid);
         if (ses == null)
             throw new BrokerException("No session for " + uid);
         ses.pause("PROTOCOL");
     }

     /**
      *Resume a session
      *<P>Packet:<B>START</b></p>
      *@param uid session to resume
      */
     public void resumeSession(SessionUID uid)
        throws BrokerException
     {
         Session ses = Session.getSession(uid);
         if (ses == null)
             throw new BrokerException("No session for " + uid);
         ses.resume("PROTOCOL");
     }

     /**
      *Pause a connection
      *<P>Packet:<B>STOP</b></p>
      *@param con connection to pause
      */
     public void pauseConnection(IMQConnection con)
     {
         con.stopConnection();
     }

     /**
      *Resume a connection
      *<P>Packet:<B>START</b></p>
      *@param con connection to start
      */
     public void resumeConnection(IMQConnection con)
     {
         con.startConnection();
     }

     /**
      * Browse a queue
      * <P>Packet:<b>BROWSE</b></p>
      *@param d destination to browse
      *@param sstr selector string to use (or null if none)
      *@return an ordered list of SysMessageIDs
      */
      public ArrayList browseQueue(Destination d, String sstr, IMQConnection con, boolean acc)
          throws BrokerException,SelectorFormatException
      {
          if (acc) 
             checkAccessPermission(PacketType.BROWSE, d, con);
          QBrowseHandler handler = (QBrowseHandler)
                      pr.getHandler(PacketType.BROWSE);
          return handler.getQBrowseList(d, sstr);
      }


      /**
       * Create a consumer
       * <P>Packet:<b>ADD_CONSUMER</b></p>
       *@param d Destination to create the consumer on
       *@param con Connection associated with the consumer
       *@param session session associated with the consumer
       *@param selector selector string (or null if none)
       *@param clientid clientid or null if none
       *@param subscriptionName if dest is Topic and 
       *       if either durable true or share true, the subscription name
       *@param durable if dest is Topic, if true, this is a durable subscription
       *@param share if dest is Topic, if true, this is a shared subscription
       *@param jmsshare if dest is Topic, 
       *       if true and share true, this is a JMS 2.0 Shared Subscription
       *       if false and share true, this is a MQ Shared Subscription
       *@param nolocal is NoLocal turned on (topics only)
       *@param size prefetch size (or -1 if none)
       *@param creator_uid a unique id to use as the creator for this consumer
       *                   which is used for indempotence (usually sysmessageid)
       *@return a consumer
       */
      public Consumer createConsumer(Destination d, IMQConnection con,
          Session session, String selector, String clientid,
          String subscriptionName, boolean durable, boolean share, 
          boolean jmsshare, boolean nolocal, 
          int size, String creator_uid, boolean acc, boolean useFlowControl)
          throws BrokerException, SelectorFormatException, IOException {

          if (DEBUG) {
          logger.log(Logger.INFO,
          "ProtocolImpl.createProducer(d="+d+", selector="+selector+
          ", clientid="+clientid+", subName="+subscriptionName+
          ", durable="+durable+", share="+share+", jmsshare="+jmsshare+
          ", nolocal="+nolocal+", size="+size+", creator_uid="+creator_uid+
          ", acc="+acc+", useFlowControl="+useFlowControl+
          ") on session="+session+"@"+session.hashCode()+", conn=@"+
           con.hashCode()+ "["+con.getConnectionUID()+", "+con+"]");
          }

          if (acc) {
              checkAccessPermission(PacketType.ADD_CONSUMER, d, con);
          }
          ConsumerHandler handler = (ConsumerHandler)
                      pr.getHandler(PacketType.ADD_CONSUMER);
          String selectorstr = selector;
          if (selectorstr != null && selectorstr.trim().length() == 0) {
              selectorstr = null;
          }
          if (d.isTemporary()) {
              if (durable) {
                  String emsg = br.getKString(br.X_INVALID_DEST_DURA_CONSUMER,
                                ""+d.getDestinationUID(), ""+subscriptionName);
                  logger.log(Logger.ERROR, emsg);
                  throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
              }
              if (share) {
                  String emsg = br.getKString(br.X_INVALID_DEST_SHARE_CONSUMER,
                                ""+d.getDestinationUID(), ""+subscriptionName);
                  logger.log(Logger.ERROR, emsg);
                  throw new BrokerException(emsg, Status.PRECONDITION_FAILED);
              }
          }
          Consumer[] c = handler.createConsumer(d.getDestinationUID(), con,
                       session, selectorstr, clientid, subscriptionName, durable,
                       share, jmsshare, nolocal, size, creator_uid, false, useFlowControl);
          if (c[2] != null)
              c[2].resume("Resuming from protocol");
          if (c[1] != null)
              c[1].resume("Resuming from protocol");
          return c[0];
      }
        

      /**
       * Destroys a durable subscription
       * <P>Packet:<b>DELETE_CONSUMER</b></P>
       *@param durableName durable name associated with the subscription
       *@param clientID clientID associated with the subscription
       */
      public void unsubscribe(String durableName, String clientID, IMQConnection con)
          throws BrokerException
      {
          ConsumerHandler handler = (ConsumerHandler)
                      pr.getHandler(PacketType.ADD_CONSUMER);
          handler.destroyConsumer(con, null, null,
              durableName, clientID, null, false, false, false);
      }

      /**
       * Closes a consumer
       * <P>Packet:<b>DELETE_CONSUMER</b></P>
       *@param uid ConsumerUID to close.
       *@param session session associated with the consumer.
       *@param lastid last message id seen by application
       *@param con Connection associated with the consumer (used
       *          for retrieving protocol version).
       */
      public void destroyConsumer(ConsumerUID uid, Session session,
          SysMessageID lastid, boolean lastidInTransaction, IMQConnection con)
          throws BrokerException {
          ConsumerHandler handler = (ConsumerHandler)
                      pr.getHandler(PacketType.ADD_CONSUMER);
          handler.destroyConsumer(con, session, uid,
              null, null, lastid, lastidInTransaction, (lastid == null), false);
      }


    /**
     * End a transaction.
     * @param id  The TransactionUID to end
     * @param xid The Xid of the transaction to end. Required if transaction
     *            is an XA transaction. Must be null if it is not an XA
     *            transaction.
     * @param xaFlags  xaFlags passed on END operation. Used only if
     *                 an XA transaction.
     */
     public void endTransaction(TransactionUID id, JMQXid xid,
              Integer xaFlags, IMQConnection con)
              throws BrokerException {

          if (DEBUG) {
          logger.log(Logger.INFO,
          "ProtocolImpl.END TRANSACTION:TID="+id+", XID="+xid+", xaFlags="+
           TransactionState.xaFlagToString(xaFlags)+
          ", conn=@"+con.hashCode()+"["+con.getConnectionUID()+", "+con+"]");
          }
          
          TransactionHandler handler = (TransactionHandler)
                      pr.getHandler(PacketType.START_TRANSACTION);

          Object[] oo = TransactionList.getTransListAndState(id, con, false, false);
          if (oo == null) {
              throw new BrokerException(
              "Unknown transaction "+id+(xid == null ? "":" XID="+xid), Status.NOT_FOUND);
          }
          TransactionList tl = (TransactionList)oo[0];
          TransactionState ts = (TransactionState)oo[1];

          handler.doEnd(tl, PacketType.END_TRANSACTION, xid,
                  xaFlags, ts, id);

     }

    /**
     * Start a transaction.
     * @param xid The Xid of the transaction to start. Required if transaction
     *            is an XA transaction. Must be null if it is not an XA
     *            transaction.
     * @param xaFlags  xaFlags passed on START operation. Used only if
     *                 an XA transaction.
     * @param con       Connection client start packet came in on (or null if internal)
     * @param type  how rollback should be handled (e.g. only not prepared)
     * @param lifetime how long the transaction should live (0 == forever)
     * @return The TransactionUID started
     */
     public TransactionUID startTransaction(JMQXid xid,
              Integer xaFlags, AutoRollbackType type, long lifetime, IMQConnection con)
          throws BrokerException
     {
          if (DEBUG) {
          logger.log(Logger.INFO,
          "ProtocolImpl.START TRANSACTION:XID="+xid+", xaFlags="+
           TransactionState.xaFlagToString(xaFlags)+
          ", type="+type+", lifetime="+lifetime+", conn=@"+con.hashCode()+
          "["+con.getConnectionUID()+", "+con+"]");
          }
          
          List conlist = con.getTransactionListThreadSafe();

          TransactionHandler handler = (TransactionHandler)
                      pr.getHandler(PacketType.START_TRANSACTION);

          // allocated a TID
          TransactionUID id = null;
          TransactionList tl = null;
          if (xaFlags == null || 
              TransactionState.isFlagSet(XAResource.TMNOFLAGS, xaFlags)) {
              id = new TransactionUID();
              TransactionList[] tls = DL.getTransactionList(con.getPartitionedStore());
              tl = tls[0];
              if (tl == null) {
                  throw new BrokerException(
                  "No transaction List for connection "+con+" to start new transaction "+
                  id+(xid == null ? "":" XID="+xid));
              }
          } else if (xid != null) {
              Object[] oo = TransactionList.mapXidToTid(xid, con);
              if (oo == null) {
                  throw new BrokerException("Unknown XID " + xid, Status.NOT_FOUND);
              } else {
                  tl = (TransactionList)oo[0];
                  id = (TransactionUID)oo[1];
              }
          } else { // XID is null, something is wrong
              throw new BrokerException("Invalid xid");
          }
          if (tl == null) {
              Object[] oo = TransactionList.getTransListAndState(id, con, false, false);
              if (oo != null) {
                  tl = (TransactionList)oo[0];
              }
          }
          if (tl == null) {
              throw new BrokerException(
              "No Transaction List found for connection "+con+" to start transaction "+
              id+(xid == null ? "":" XID="+xid));
          }
          
          Object o = new Object();
          handler.doStart(tl, id, conlist, con, 
                  type, xid, xid!= null, lifetime, 0,
                        xaFlags, PacketType.START_TRANSACTION, 
                        false, o.toString());
          if (DEBUG) {
          logger.log(Logger.INFO, 
          "ProtocolImpl.STARTED TRANSACTION:TID="+id+", XID="+xid+", type="+type+", con="+con);
          }

          return id;

     }

    /**
     * Commit a transaction.
     * @param id  The TransactionUID to commit
     * @param xid The Xid of the transaction to commit. Required if transaction
     *            is an XA transaction. Must be null if it is not an XA
     *            transaction.
     * @param xaFlags  xaFlags passed on COMMIT operation. Used only if
     *                 an XA transaction.
     * @param con       Connection client commit packet came in on (or null if internal)
     */
     public void commitTransaction(TransactionUID id, JMQXid xid,
              Integer xaFlags, IMQConnection con)
          throws BrokerException
     {
          if (DEBUG) {
          logger.log(Logger.INFO,
          "ProtocolImpl.COMMIT TRANSACTION:TID="+id+", XID="+xid+", xaFlags="+
           TransactionState.xaFlagToString(xaFlags)+
          ", conn=@"+con.hashCode()+"["+con.getConnectionUID()+", "+con+"]");
          }
          
          List conlist = con.getTransactionListThreadSafe();

          TransactionHandler handler = (TransactionHandler)
                      pr.getHandler(PacketType.START_TRANSACTION);

          TransactionList tl = null;
          if (0L == id.longValue()) {
              if (xid == null) {
                  throw new BrokerException("Unexpected TransactionUID  " + id);
              }
              Object[] oo = TransactionList.mapXidToTid(xid, con);
              if (oo == null) {
                  tl = null;
                  id = null;
              } else {
                  tl = (TransactionList)oo[0];
                  id = (TransactionUID)oo[1];
              }
              if (id == null) {
                  throw new BrokerException("Unknown XID " + xid, Status.NOT_FOUND);
              }
          }
          TransactionState ts = null;
          if (tl == null) {
              Object[] oo = TransactionList.getTransListAndState(id, con, false, false);
              if (oo != null) {
                  tl = (TransactionList)oo[0];
                  ts = (TransactionState)oo[1];
              }
          }
          if (tl == null) {
              throw new BrokerException(
              "Unknown transaction "+id+(xid == null ? "":" XID="+xid), Status.NOT_FOUND);
          }
          if (ts == null) {
              ts = tl.retrieveState(id);
              if (ts == null) {
                  throw new BrokerException(
                  "Unknown transaction "+id+(xid == null ? "":" XID="+xid), Status.NOT_FOUND);
              }
          }

          if (xid != null) {
              if (ts.getXid() == null || !xid.equals(ts.getXid())) {
                  throw new BrokerException(
                  "Transaction XID mismatch "+xid+", expected "+ts.getXid()+" for transaction "+id);
              }
          }

          handler.doCommit(tl, id, xid, xaFlags, 
                           ts, conlist, false,con, null);
     }

    /**
     * prepare a transaction.
     * @param id  The TransactionUID to prepare
     */
     public void prepareTransaction(TransactionUID id, IMQConnection con)
          throws BrokerException
     {
          if (DEBUG) {
          logger.log(Logger.INFO,
          "ProtocolImpl.PREPARE TRANSACTION:TID="+id+
          ", conn=@"+con.hashCode()+"["+con.getConnectionUID()+", "+con+"]");
          }

          TransactionHandler handler = (TransactionHandler)
                      pr.getHandler(PacketType.START_TRANSACTION);

          Object[] oo = TransactionList.getTransListAndState(id, con, false, false);
          if (oo == null) {
              throw new BrokerException(
              "Unknown transaction id "+id, Status.NOT_FOUND);
          }
          TransactionList tl = (TransactionList)oo[0];
          TransactionState ts = (TransactionState)oo[1];

          handler.doPrepare(tl, id, null/*xaFlags*/, 
                            ts, PacketType.PREPARE_TRANSACTION, con);
     }


    /**
     * Rollback a transaction
     * @param id  The TransactionUID to rollback
     * @param xid The Xid of the transaction to rollback. Required if transaction
     *            is an XA transaction. Must be null if it is not an XA
     *            transaction.
     * @param redeliver should messages be redelivered
     * @param setRedeliver if the messages are redelivered, should the redeliver 
     *                     flag be set on all messages or not
     * @param con       Connection client rollback packet came in on (or null if internal)
     */
     public void rollbackTransaction(TransactionUID id, JMQXid xid,
         IMQConnection con, boolean redeliver, boolean setRedeliver, 
         int maxRollbacks, boolean dmqOnMaxRollbacks)
         throws BrokerException {

         if (maxRollbacks <= 0) {
             dmqOnMaxRollbacks = !(Consumer.MSG_MAX_CONSECUTIVE_ROLLBACKS <= 0);
         }

          if (DEBUG) {
          logger.log(Logger.INFO, 
          "ProtocolImpl.ROLLBACK TRANSACTION:TID="+id+", XID="+xid+
          ", conn=@"+con.hashCode()+"["+con.getConnectionUID()+", "+con+
          "], redeliver="+redeliver+", setRedeliver="+setRedeliver);
          }

          List conlist = con.getTransactionListThreadSafe();

          TransactionHandler handler = (TransactionHandler)
                      pr.getHandler(PacketType.START_TRANSACTION);

          TransactionList tl = null;
          if (0L == id.longValue()) {
              if (xid == null) {
                  throw new BrokerException("Unexpected TransactionUID  " + id);
              }
              Object[] oo = TransactionList.mapXidToTid(xid, con);
              if (oo == null) {
                  tl = null;
                  id = null;
              } else {
                  tl = (TransactionList)oo[0];
                  id = (TransactionUID)oo[1];
              }
              if (id == null) {
                  throw new BrokerException("Unknown XID " + xid, Status.NOT_FOUND);
              }
          }
          TransactionState ts = null;
          if (tl == null) {
              Object[] oo = TransactionList.getTransListAndState(id, con, false, false);
              if (oo != null) {
                  tl = (TransactionList)oo[0];
                  ts = (TransactionState)oo[1];
              }
          }
          if (tl == null) {
              throw new BrokerException(
              "Unknown transaction "+id+(xid == null ? "":" XID="+xid), Status.NOT_FOUND);
          }
          if (ts == null) {
              ts = tl.retrieveState(id);
              if (ts == null) {
                  throw new BrokerException(
                  "Unknown transaction "+id+(xid == null ? "":" XID="+xid), Status.NOT_FOUND);
              }
          }
          if (xid != null) {
              if (ts.getXid() == null || !xid.equals(ts.getXid())) {
                  throw new BrokerException(
                  "Transaction XID mismatch "+xid+", expected "+ts.getXid()+" for transaction "+id);
              }
          }

          handler.preRollback(tl, id, xid, null/*xaFlags*/, ts);

          BrokerException bex = null;
          if (redeliver) {
              try {
                  handler.redeliverUnacked(tl, id, true, setRedeliver, 
                      false, maxRollbacks, dmqOnMaxRollbacks);
              } catch (MaxConsecutiveRollbackException e) {
                  bex = e;
                  if (!dmqOnMaxRollbacks) {
                      throw bex;
                  }
              }
          }

          try {
              handler.doRollback(tl, id, xid, null/*xaFlags*/, ts,
                                 conlist, con,  RollbackReason.APPLICATION);
          } catch (BrokerException e) {
              if (bex != null) {
                  throw bex;
              }
              throw e;
          }
          if (bex != null) {
              throw bex;
          }
     }


     /**
      * Recover a transaction.
      *@param id id to recover or null if all
      */
     public JMQXid[] recoverTransaction(TransactionUID id)
     {
         if (DEBUG) {
         logger.log(Logger.INFO, "ProtocolImpl.RECOVER TRANSACTION:TID="+id);
         }

         //TransactionHandler handler = (TransactionHandler)
         //            pr.getHandler(PacketType.START_TRANSACTION);

         TransactionList[] tls = DL.getTransactionList(null);
         TransactionList tl = null;
         TransactionState ts = null;
         Map<TransactionList, Vector> map = new LinkedHashMap<TransactionList, Vector>();
         Vector v = null;
         for (int i = 0; i < tls.length; i++) {
             tl = tls[i]; 
             if (id == null) {
                 v = tl.getTransactions(TransactionState.PREPARED);
                 map.put(tl, v);
             } else {
                 ts = tl.retrieveState(id);
                 if (ts == null) {
                     continue;
                 }
                 if (ts.getState() == TransactionState.PREPARED) {
                     v = new Vector();
                     v.add(id);
                     map.put(tl, v);
                     break;
                 }
             }
         }
         ArrayList xids = new ArrayList();
         for (Map.Entry<TransactionList, Vector> pair: map.entrySet()) {
             tl = pair.getKey();
             v = pair.getValue();
             
             Iterator itr = v.iterator();
             int i = 0;
             while (itr.hasNext()) {
                 TransactionUID tuid = (TransactionUID)itr.next();
                 TransactionState _ts = tl.retrieveState(tuid);
                 if (_ts == null) {
                     // Should never happen
                     continue;
                 }
                 JMQXid _xid = _ts.getXid();
                 xids.add(_xid);
             }
         }
         return (JMQXid[])xids.toArray(new JMQXid[xids.size()]);
     }

    /**
     * Verify a destination exists.
     * @param destination destination name
     * @param type DestType of the destination
     * @param selectorstr selector string to verify or null if none
     * @see com.sun.messaging.jmq.util.DestType
     * @return a hashmap which contains the following
     *  entries:<UL><LI>JMQStatus</LI><LI>JMQCanCreate</LI><LI>DestType</LI></UL>
     */
     public HashMap verifyDestination(String destination,
               int type, String selectorstr /* may be null */)
        throws BrokerException, IOException
     {

         HashMap returnmap = new HashMap();
         try {
             if (selectorstr != null) {
                 Selector.compile(selectorstr);
             }
         } catch (SelectorFormatException ex) {
              returnmap.put("JMQStatus", Integer.valueOf(Status.BAD_REQUEST));
              return returnmap;
         }
         
         Destination[] ds =  DL.getDestination(null, destination, 
                                 DestType.isQueue(type));
         Destination d = ds[0];

         if (d == null) {
             returnmap.put("JMQCanCreate", Boolean.valueOf(
                   DL.canAutoCreate(DestType.isQueue(type))));
             returnmap.put("JMQStatus", Integer.valueOf(Status.NOT_FOUND));
         } else {
             returnmap.put("JMQDestType", Integer.valueOf(d.getType()));
             returnmap.put("JMQStatus", Integer.valueOf(Status.OK));
         }
         return returnmap;
     }

     /**
      * Verify a transaction is PREPARED
      * @param tuid transaction id to verify
      */
     public Map verifyTransaction(TransactionUID tuid)
     throws BrokerException {

         //TransactionHandler handler = (TransactionHandler)
         //            pr.getHandler(PacketType.START_TRANSACTION);
         Object[] oo = TransactionList.getTransListAndState(tuid, null, true, false);
         if (oo == null) {
             return null; 
         }
         TransactionList translist = (TransactionList)oo[0];
         TransactionState ts = (TransactionState)oo[1];
         int realstate = ts.getState();

         if (realstate != TransactionState.PREPARED) {
             return null; // GONE
         }
         return translist.getTransactionMap(tuid, true);
     }


     /**
      * Redeliver messages
      */
     public void redeliver(ConsumerUID ids[], SysMessageID sysids[], 
               IMQConnection con, TransactionUID tid, boolean redeliver)
         throws BrokerException, IOException
     {
         RedeliverHandler handler = (RedeliverHandler)
                      pr.getHandler(PacketType.REDELIVER);
         handler.redeliver(ids, sysids, con, tid, redeliver);
     }

     /**
      * route, store and forward a message
      */
      public void processMessage(IMQConnection con, Packet msg)
          throws BrokerException, SelectorFormatException, IOException
      {
          DataHandler handler = (DataHandler)
                      pr.getHandler(PacketType.MESSAGE);

          Destination d = null;
          PacketReference ref = null;
          Set s = null;
          boolean route = false;
          boolean isadmin = con.isAdminConnection();
          List<MessageDeliveryTimeInfo> deliveryDelayReadyList =
                       new ArrayList<MessageDeliveryTimeInfo>();
          try {
               Destination[] ds = DL.getDestination(con.getPartitionedStore(), 
                                      msg.getDestination(), msg.getIsQueue());
               d = ds[0];
               if (d == null) {
                   throw new BrokerException("Unknown Destination:" + msg.getDestination());
               }
    
               Producer pausedProducer = handler.checkFlow(msg, con);
               boolean transacted = (msg.getTransactionID() != 0);
               if (DEBUG) {
               logger.log(Logger.INFO, 
               "ProtocolImpl.PROCESS MESSAGE["+msg+"]TID="+msg.getTransactionID()+
               " to destination "+d+", on conn=@"+con.hashCode()+
               "["+con.getConnectionUID()+", "+con+"]");
               }
    
                // OK generate a ref. This checks message size and
                // will be needed for later operations
                ref = handler.createReference(msg, d.getDestinationUID(), con, isadmin);
    
                // dont bother calling route if there are no messages
                //
                // to improve performance, we route and later forward
                route = handler.queueMessage(d, ref, transacted);
    
                s = handler.routeMessage(con.getPartitionedStore(), transacted, 
                                         ref, route, d, deliveryDelayReadyList);
    
                // handle producer flow control
                handler.pauseProducer(d, pausedProducer, con);
    
         } catch (BrokerException ex) {
            int status = ex.getStatusCode();
            if (status == Status.ERROR && ref!= null && d != null)
                handler.cleanupOnError(d, ref);
    
            // rethrow
            throw ex;
         }
    
         if (route && s != null) {
             try {
                  handler.forwardMessage(d, ref, s);
             } catch (Exception e) {
                  Object[] emsg = { ref, d.getDestinationUID(), s };
                  logger.logStack(Logger.WARNING,
                      Globals.getBrokerResources().getKString(
                      BrokerResources.X_ROUTE_PRODUCED_MSG_FAIL, emsg), e);
             }
         }
         if (deliveryDelayReadyList.size() > 0) {
             MessageDeliveryTimeInfo di = null;
             Iterator<MessageDeliveryTimeInfo> itr =
                      deliveryDelayReadyList.iterator();
             while (itr.hasNext()) {
                 di = itr.next();
                 di.setDeliveryReady();
             }
         }
      }


     /**
      * create a destination
      * Implemented CREATE_DESTINATION
      * @param dname name of the destination
      * @param dtype type of the destination as a bit flag from DestType
      */

    public Destination createDestination(String dname, int dtype, IMQConnection con, boolean acc)
        throws BrokerException, IOException
    {
        if (acc)
            checkAccessPermission(PacketType.CREATE_DESTINATION, dname, dtype, con);
        if (DestType.isTemporary(dtype)) {
            boolean storeTemps = con.getConnectionUID().
                            getCanReconnect();
            long reconnectTime = con.getReconnectInterval();
            Destination[] ds = DL.createTempDestination(con.getPartitionedStore(),
                         dname, dtype, con.getConnectionUID(), 
                         storeTemps, reconnectTime);
            Destination d = ds[0];
            if (con.getConnectionUID().equals(d.getConnectionUID())) {
                        con.attachTempDestination(d.getDestinationUID());
            }
            return d;

        }
        Destination[] ds = DL.getDestination(con.getPartitionedStore(), dname, 
                               dtype, true, !con.isAdminConnection());
        return ds[0];
    }

    /**
     * destroy a destination
     * Implemented DESTROY_DESTINATION
     */
    public void destroyDestination(DestinationUID duid)
         throws BrokerException , IOException {

        DL.removeDestination(null, duid, true, "request from protocol");
    }

    

    void checkAccessPermission(int pktType, Destination d, IMQConnection con)
                    throws AccessControlException,
                           BrokerException
    {

        checkAccessPermission(pktType, d.getDestinationName(), d.getType(), con);

    } 
    void checkAccessPermission(int pktType, String dname, int dtype, IMQConnection con)
                    throws BrokerException
    {
        String op = PacketType.mapOperation(pktType);
        if (op == null) return;
        PacketHandler handler = pr.getHandler(pktType);
        try {
            handler.checkPermission(pktType, op, dname, dtype, con);
        } catch (AccessControlException e) {
            throw new BrokerException(e.getMessage(), e, Status.FORBIDDEN);
        }
        
    }
           
}
