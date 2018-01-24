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
 * @(#)Protocol.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.protocol;


import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.data.handlers.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.AutoRollbackType;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;

/**
 * 
 * Api used for direct access to the broker. This code is not integrated into 
 * the handler classes because the interaction (e.g. when callbacks happen) 
 * is different for the jms protocol and other protocols.
 */


public interface Protocol
{

    public boolean getDEBUG();

    /**
     * called when a new connection is created.
     * <P>Packet:<B>HELLO,HELLO_REPLY</b></p>
     */

    public void hello();

    /**
     * Authenticate with the passed in username, password
     * <P>Packet:<B>AUTHENTICATE</b></p>
     */
    public void authenticate(String username, String password)
        throws BrokerException;


    /**
     * called when a connection is closed
     * <P>Packet:<B>GOODBYE</b></p>
     */
    public void goodbye();

    /**
     * gets license information.
     * <P>Packet:<B>GET_LICENSE</b></p>
     *
     *@returns a hashtable with license info
     */

    public Hashtable getLicense();

    /**
     * gets information about the broker/cluster.
     * <P>Packet:<B>INFO_REQUEST</b></p>
     *
     *@param cluster if true, return cluster information otherwise
     *               return local broker information.
     *@returns a hashtable with broker/cluster information
     */

    public Hashtable getInfo(boolean cluster);

    /**
     * handles receiving a ping from a client.
     * <P>Packet:<B>PING</b></p>
     */

    public void ping();


    /**
     * handles receiving a flow paused from a client.
     * <P>Packet:<B>FLOW_PAUSED</b></p>
     */
    public void flowPaused(int size);


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
           throws BrokerException, IOException;

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
        throws BrokerException;



    /**
     * creates a producer.
     * <P>Packet:<B>ADD_PRODUCER</b></p>
     *
     *@param d the destination to create the producer on
     *@param con the conectio to use for the producer
     *@param uid a unique string used for finding the producer
     *@param acc check access_control
     *@throws BrokerException if the producer can not be created
     */
    public Producer addProducer(Destination d, IMQConnection con, String uid, boolean acc)
        throws BrokerException;

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
        throws BrokerException;

    /**
     * resumes flow control on a connection.
     * <P>Packet:<B>RESUME_FLOW</b></p>
     *
     *@param bufsize size of the buffer to receive (-1 indicates unlimited)
     *@param con the connection to resume
     */
    public void resumeFlow(IMQConnection con, int bufsize);

    /**
     * resumes flow control on a consumer.
     * <P>Packet:<B>RESUME_FLOW</b></p>
     *
     *@param bufsize size of the buffer to receive (-1 indicates unlimited)
     *@param con the consumer to resume
     */
    public void resumeFlow(Consumer con, int bufsize);

    /**
     * mimics the behavior of DELIVER
     * <P>Packet:<B>DELIVER</b></p>
     *
     *@param cid consumer id to attach to the messages
     *@param ids a list of id's to deliver
     *@return an ArrayList of packets
     */
    public ArrayList deliver(long cid, ArrayList ids)
        throws BrokerException, IOException;
    
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
        throws BrokerException;
    

     /**
      * Creates a session and attaches it to the connection
      * <P>Packet:<B>CREATE_SESSION</b></p>
      *@param ackType acknowledge type to use
      *@param con connection to attach the session to
      */
     public Session createSession(int ackType, IMQConnection con)
        throws BrokerException;

     /**
      * Destroy a session 
      * <P>Packet:<B>DESTROY_SESSION</b></p>
      *@param uid sessionUID to destroy
      *@param con connection to deattach the session from
      */
     public void destroySession(SessionUID uid, IMQConnection con)
        throws BrokerException;

     /**
      *Pause a session
      *<P>Packet:<B>STOP</b></p>
      *@param uid session to pause
      */
     public void pauseSession(SessionUID uid)
        throws BrokerException;

     /**
      *Resume a session
      *<P>Packet:<B>START</b></p>
      *@param uid session to resume
      */
     public void resumeSession(SessionUID uid)
        throws BrokerException;

     /**
      *Pause a connection
      *<P>Packet:<B>STOP</b></p>
      *@param con connection to pause
      */
     public void pauseConnection(IMQConnection con);

     /**
      *Resume a connection
      *<P>Packet:<B>START</b></p>
      *@param con connection to start
      */
     public void resumeConnection(IMQConnection con);

     /**
      * Browse a queue
      * <P>Packet:<b>BROWSE</b></p>
      *@param d destination to browse
      *@param sstr selector string to use (or null if none)
       *@param acc check access_control
      *@return an ordered list of SysMessageIDs
      */
      public ArrayList browseQueue(Destination d, String sstr, IMQConnection con, boolean acc)
          throws BrokerException,SelectorFormatException;


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
       *@param acc check access_control
       *@return a consumer
       */
      public Consumer createConsumer(Destination d, IMQConnection con,
          Session session, String selector, String clientid,
          String subscriptionName, boolean durable, 
          boolean share, boolean jmsshare, boolean nolocal, 
          int size, String creator_uid, boolean acc, boolean useFlowControl)
          throws BrokerException, SelectorFormatException, IOException;

      /**
       * Destroys a durable subscription
       * <P>Packet:<b>DELETE_CONSUMER</b></P>
       *@param durableName durable name associated with the subscription
       *@param clientID clientID associated with the subscription
       */
      public void unsubscribe(String durableName, String clientID, IMQConnection con)
          throws BrokerException;

      /**
       * Closes a consumer
       * <P>Packet:<b>DELETE_CONSUMER</b></P>
       *@param uid ConsumerUID to close.
       *@param session session associated with the consumer.
       *@param con Connection associated with the consumer (used
       *          for retrieving protocol version).
       *@param lastid the last delivered message's SysMessageID
       *@param lastidInTransaction true if the lastid message was delivered in a transaction
       */
      public void destroyConsumer(ConsumerUID uid, Session session,
          SysMessageID lastid, boolean lastidInTransaction, IMQConnection con)
          throws BrokerException;


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
         throws BrokerException;

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
          throws BrokerException;

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
          throws BrokerException;

    /**
     * prepare a transaction.
     * @param id  The TransactionUID to prepare
     */
     public void prepareTransaction(TransactionUID id, IMQConnection con)
          throws BrokerException;


    /**
     * Rollback a transaction
     * @param id  The TransactionUID to rollback
     * @param xid The Xid of the transaction to rollback. Required if transaction
     *            is an XA transaction. Must be null if it is not an XA
     *            transaction.
     * @param redeliver redeliver the messages
     * @param setRedeliver set the redeliver flag on all redelivered messages if true
     * @param con       Connection client rollback packet came in on (or null if internal)
     */
     public void rollbackTransaction(TransactionUID id, JMQXid xid,
          IMQConnection con, boolean redeliver, boolean setRedeliver,
          int maxRollbacks, boolean dmqOnMaxRollbacks)
          throws BrokerException;

     /**
      * Recover a transaction.
      *@param id id to recover or null if all
      */
     public JMQXid[] recoverTransaction(TransactionUID id);

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
        throws BrokerException, IOException;

     /**
      * Verify a transaction is PREPARED
      * @param tuid transaction id to verify
      */
     public Map verifyTransaction(TransactionUID tuid)
         throws BrokerException;

     /**
      * Redeliver messages
      */
     public void redeliver(ConsumerUID ids[], SysMessageID sysids[], 
               IMQConnection con, TransactionUID tid, boolean redeliver)
         throws BrokerException, IOException;


     /**
      * route, store and forward a message
      *
      */
      public void processMessage(IMQConnection con, Packet msg)
          throws BrokerException, SelectorFormatException, IOException;



     /**
      * create a destination
      * Implemented CREATE_DESTINATION
       *@param acc check access_control
      */

     public Destination createDestination(String dest, int destType, IMQConnection con, boolean acc)
         throws BrokerException, IOException;

     /**
      * destroy a destination
      * Implemented DESTROY_DESTINATION
      */
     public void destroyDestination(DestinationUID duid)
         throws BrokerException, IOException;
}
