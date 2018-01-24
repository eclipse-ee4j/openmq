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
 *  %W% %G%
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import javax.jms.*;

import java.util.*;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.timer.WakeupableTimer;
import com.sun.messaging.jmq.util.timer.TimerEventHandler;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;
import com.sun.messaging.jms.ra.api.JMSRAXASession;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import java.util.logging.Logger;
import java.util.logging.Level;

/** <P>A JMS Session is a single threaded context for producing and consuming
  * messages. Although it may allocate provider resources outside the Java
  * virtual machine, it is considered a light-weight JMS object.
  *
  * <P>A session serves several purposes:
  *
  * <UL>
  *   <LI>It is a factory for its message producers and consumers.
  *   <LI>It supplies provider-optimized message factories.
  *   <LI>It supports a single series of transactions that combine work
  *       spanning its producers and consumers into atomic units.
  *   <LI>A session defines a serial order for the messages it consumes and
  *       the messages it produces.
  *   <LI>A session retains messages it consumes until they have been
  *       acknowledged.
  *   <LI>A session serializes execution of message listeners registered with
  *       it's message consumers.
  * </UL>
  *
  * <P>A session can create and service multiple message producers and
  * consumers.
  *
  * <P>One typical use is to have a thread block on a synchronous
  * MessageConsumer until a message arrives. The thread may then use one or
  * more of the Session's MessageProducers.
  *
  * <P>If a client desires to have one thread producing messages while others
  * consume them, the client should use a separate Session for its producing
  * thread.
  *
  * <P>Once a connection has been started, any session with a registered
  * message listener(s) is dedicated to the thread of control that delivers
  * messages to it. It is erroneous for client code to use this session
  * or any of its constituent objects from another thread of control. The
  * only exception to this is the use of the session or connection close
  * method.
  *
  * <P>It should be easy for most clients to partition their work naturally
  * into Sessions. This model allows clients to start simply and incrementally
  * add message processing complexity as their need for concurrency grows.
  *
  * <P>The close method is the only session method that can be called
  * while some other session method is being executed in another thread.
  *
  * <P>A session may be optionally specified as transacted. Each transacted
  * session supports a single series of transactions. Each transaction groups
  * a set of message sends and a set of message receives into an atomic unit
  * of work. In effect, transactions organize a session's input message
  * stream and output message stream into series of atomic units. When a
  * transaction commits, its atomic unit of input is acknowledged and its
  * associated atomic unit of output is sent. If a transaction rollback is
  * done, its sent messages are destroyed and the session's input is
  * automatically recovered.
  *
  * <P>The content of a transaction's input and output units is simply those
  * messages that have been produced and consumed within the session's current
  * transaction.
  *
  * <P>A transaction is completed using either its session's
  * <CODE>commit</CODE> or <CODE>rollback</CODE> method. The completion of a
  * session's current transaction automatically begins the next. The result
  * is that a transacted session always has a current transaction within
  * which its work is done.
  *
  * <P>JTS, or some other transaction monitor may be used to combine a
  * session's transaction with transactions on other resources (databases,
  * other JMS sessions, etc.). Since Java distributed transactions are
  * controlled via JTA, use of the session's commit and rollback methods in
  * this context is prohibited.
  *
  * <P>JMS does not require support for JTA; however, it does define how a
  * provider supplies this support.
  *
  * <P>Although it is also possible for a JMS client to handle distributed
  * transactions directly, it is unlikely that many JMS clients will do this.
  * Support for JTA in JMS is targeted at systems vendors who will be
  * integrating JMS into their application server products.
  *
  * @see         javax.jms.QueueSession
  * @see         javax.jms.TopicSession
  * @see         javax.jms.XASession
  */

public class SessionImpl implements JMSRAXASession, Traceable, ContextableSession {

    /** With this acknowledgement mode, the session automatically acknowledges
      * a client's receipt of a message when it has either successfully
      * returned from a call to receive or the message listener it has called
      * to process the message successfully returns.
      */

   //static final int AUTO_ACKNOWLEDGE = 1;

    /** With this acknowledgement mode, the client acknowledges a message by
      * calling a message's acknowledge method. Acknowledging a message
      * acknowledges all messages that the Session has consumed.
      *
      * <P>When client acknowledgment mode is used, a client may build up a
      * large number of unacknowledged messages while attempting to process
      * them. A JMS provider should provide administrators with a way to
      * limit client over-run so that clients are not driven to resource
      * exhaustion and ensuing failure when some resource they are using
      * is temporarily blocked.
      */

    //static final int CLIENT_ACKNOWLEDGE = 2;

    /** This acknowledgement mode instructs the session to lazily acknowledge
      * the delivery of messages. This is likely to result in the delivery of
      * some duplicate messages if JMS fails, it should only be used by
      * consumers that are tolerant of duplicate messages. Its benefit is the
      * reduction of session overhead achieved by minimizing the work the
      * session does to prevent duplicates.
      */

    //static final int DUPS_OK_ACKNOWLEDGE = 3;

    private ServerSessionRunner serverSessionRunner = null;
    private ConnectionConsumerImpl connectionConsumer = null;

    // default values.
    protected ConnectionImpl connection = null;
    protected boolean isTransacted = false;
    protected int acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
    protected SessionReader sessionReader = null;
    // protected SessionTransaction transaction = null;

    protected SessionQueue sessionQueue = null;

    protected ReadChannel readChannel = null;
    //protected FlowControl flowControl = null;

    protected Hashtable consumers = new Hashtable();
    //producers are added here in case we need to recover
    //the connection
    protected Vector producers = new Vector();

    protected WriteChannel writeChannel = null;
    protected Long sessionId = null;
    protected ProtocolHandler protocolHandler = null;
    protected Transaction transaction = null;

    protected boolean failoverOccurred = false;

    protected int dupsOkLimit = 10; //dups ok non ack limit.

    //flag to indicate if we need to check unacked message count
    protected boolean isAckLimited = false;

    //only used if isAckLimited set to true
    protected int ackLimit = 100; //client non acknowledge limit
    //number of messages not yet acked so far.
    protected int ackCounter = 0;

    //queue for unacked messages,
    protected Vector unAckedMessageQueue = new Vector();

    protected boolean isClosed = false;
    protected boolean isStopped = false;

    //is the XASession closed
    //protected boolean isXAClosed = false;
    //is the XASession in rollback/commit
    //protected boolean isXAInRC = false;

    //for flow control
    protected boolean protectMode = false;

    //XXX chiaming REVISIT: init when it is needed.
    protected Hashtable browserConsumers = new Hashtable();

    //for acknowledgement.
    ReadWritePacket ackPkt = new ReadWritePacket();
    ByteArrayOutputStream bos =
         new ByteArrayOutputStream (ProtocolHandler.ACK_MESSAGE_BODY_SIZE);
    DataOutputStream dos = new DataOutputStream(bos);

    ReadWritePacket expirePkt = new ReadWritePacket();
    ByteArrayOutputStream expireBos =
         new ByteArrayOutputStream (ProtocolHandler.ACK_MESSAGE_BODY_SIZE);
    DataOutputStream expireDos = new DataOutputStream(expireBos);

    protected boolean setJMSXConsumerTXID = false;

    protected boolean debug = Debug.debug;

    //session sync object - used for session sync operations.
    //Such as close, commit, recover, rollback, etc.
    private Object syncObject = new Object();
    private boolean inSyncState = false;
    
    // reason why inSyncState is true 
    // we are only interested in knowing whether the other thread is executing close() or something else
    private int inSyncStateOperation = INSYNCSTATE_NOTSET;
    private static final int INSYNCSTATE_NOTSET = 0;
    private static final int INSYNCSTATE_SESSION_CLOSING = 1;
    private static final int INSYNCSTATE_CONSUMER_CLOSING = 2;
    private static final int INSYNCSTATE_OTHER = 3;
    
    //session sync object to replace synchronized (this).
    //application can sync the session object and interfere the client runtime.
    //bug 6302418 - imqReconnectEnabled does not work well on cluster.
    private Object sessionSyncObj = new Object();

    protected boolean xaTxnMode = false;

    //sync object used for endpoint consumers in the RA
    private Object raEndpointSyncObj = new Object();
    
    //this field is never used.
    //private boolean raEndpointSession = false;

    //RA mc object to acquire txn cntxt
    private JMSRAManagedConnection mc = null;

    private boolean isDedicatedToServerSession = false;

    //XXX PROTOCOL 3.5
    //SessionID assigned to this session by the broker.
    private long brokerSessionID = -1;

    private int TEST_ackCount = 0;
    private int TEST_rxCount = 0;

    //if set, dups ok acks when reached unacked limit or session Q is empty.
    protected boolean dupsOkAckOnEmptyQueue = false;
    //if set, dups ok acks only when reached unack limit.
    protected boolean dupsOkAckOnLimit = false;
    //if set, dups ok acks when reached unack limit or max timeout.
    protected boolean dupsOkAckOnTimeout = false;
    //dups ok timeout value -- to set to session reader.
    protected long dupsOkAckTimeout = 0;
    //dups ok timestamp
    protected long dupsOkTimestamp = 0;

    //created to prevent deadlock - bugid 4987018
    protected Object dupsOkSyncObj = new Object();

    //allow session extension flag.  if set to true, MQ extensions are allowed.
    //such as NO_ACK mode.
    protected boolean allowExtensions = false;
    
    //private volatile boolean inResetState = false; 

    //connection logging domain name.
    public static final String SESSION_LOGGER_NAME =
                               ConnectionImpl.ROOT_LOGGER_NAME + ".session";

    protected static final Logger sessionLogger =
        Logger.getLogger(SESSION_LOGGER_NAME,
                         ClientResources.CLIENT_RESOURCE_BUNDLE_NAME);
    
    //flag to indicate that the remote broker where the consumer(s) consumes messages from
    //had failed.  Messages are not able to commit/acknowledge until rollback/recover.
    //see recreateConsumers() for detail information.
    protected volatile boolean remore_broker_failed = false;
    
    //HACC -- rollback only for the current transaction
    //this is set when ServerSessionRunner.run() received
    //exception from listener or ack failed (bug 6667940).
    protected volatile boolean isRollbackOnly = false;
    //assigned when isRollbackOnly happened.
    protected Throwable rollbackCause = null;
    
    //should only be used for JMS local transaction in non-HA mode
    public static final boolean autoStartTxn = Boolean.getBoolean("imq.autoStartTxn");

    public static final boolean noBlockUntilTxnCompletes = Boolean.getBoolean("imq.noBlockUntilTxnCompletes");
    public static final boolean noBlockOnAutoAckNPTopics = Boolean.getBoolean("imq.noBlockOnAutoAckNPTopics");
    private static long waitTimeoutForConsumerCloseDone = (long) 
        Integer.getInteger("imqWaitTimeoutForConsumerCloseDone", 90000).intValue();
    
    private ThreadLocal<Boolean> isMessageListener = new ThreadLocal<Boolean>();
    
    private Object asyncSendLock = new Object();
    private ArrayList<AsyncSendCallback> asyncSends =
                      new ArrayList<AsyncSendCallback>();
    private boolean noAsyncSendCBProcessor = true;
    protected WakeupableTimer asyncSendCBProcessor = null;

    protected static final Exception asyncSendWaitTimeoutEx = 
                          getAsyncSendWaitTimeoutException();

    protected static final Exception noAsyncSendCBProcessorEx = 
             getNoAsyncSendCBProcessorException();

    protected static final Exception connectionBrokenEx = 
                               getConnectionBrokenException();

    private static Exception getAsyncSendWaitTimeoutException() {
        String emsg = AdministeredObject.cr.getKString(
                    ClientResources.X_ASYNC_SEND_COMPLETION_WAIT_TIMEOUT);
        return new JMSException(emsg, 
                   ClientResources.X_ASYNC_SEND_COMPLETION_WAIT_TIMEOUT);
    }

    private static Exception getNoAsyncSendCBProcessorException() {
        String emsg = AdministeredObject.cr.getKString(
                   ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
        return new javax.jms.IllegalStateException(emsg, 
                   ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
    }

    private static Exception getConnectionBrokenException() {
        String emsg = AdministeredObject.cr.getKString(
                          ClientResources.E_CONNECTION_BROKEN);
        return new JMSException(emsg, ClientResources.E_CONNECTION_BROKEN);
    }


    protected SessionImpl() {}

    public SessionImpl(ConnectionImpl connection) throws JMSException {
        this (connection, false, Session.AUTO_ACKNOWLEDGE, false, null);
    }

    /**
     * Constructor for standard JMS sessions.
     * @param connection
     * @param isTransacted
     * @param acknowledgeMode
     * @throws JMSException
     */
    public SessionImpl
    (ConnectionImpl connection, boolean isTransacted, int acknowledgeMode)
    throws JMSException {
        this (connection, isTransacted, acknowledgeMode, false, null);
    }

    public SessionImpl
    (ConnectionImpl connection, boolean isTransacted, 
     int acknowledgeMode, JMSRAManagedConnection mc)
    throws JMSException {
        this (connection, isTransacted, acknowledgeMode, false, mc);
    }

    /**
     * Constructor for MQ extended sessions.  Use this constructor for non
     * transacted MQ extended session -- such as NO_ACKNOWLEDGE mode.
     * @param connection
     * @param acknowledgeMode
     * @throws JMSException
     */
    public SessionImpl
    (ConnectionImpl connection, int acknowledgeMode) throws JMSException {
        this (connection, false, acknowledgeMode, true, null);
    }

    public
    SessionImpl (ConnectionImpl connection, boolean isTransacted, 
    int acknowledgeMode, boolean allowJMSExtension, JMSRAManagedConnection mc)
    throws JMSException {

        try {

            //session's queue data structure. -- 6089070
            sessionQueue = new SessionQueue();
            sessionQueue.validateQueue();

            //set allow extension flag.
            this.allowExtensions = allowJMSExtension;

            this.connection = connection;

            /**
             * check acknowledge mode
             */
            if (isTransacted == false) {
                if (acknowledgeMode == 0) {
                    acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
                }

                checkAckMode(acknowledgeMode);
            }

            this.writeChannel = connection.getWriteChannel();
            this.readChannel = connection.getReadChannel();
            this.protocolHandler = connection.getProtocolHandler();

            this.isTransacted = isTransacted;

            this.acknowledgeMode = acknowledgeMode;

            sessionId = connection.getNextSessionId();

            if (mc != null) {
                this.mc = mc;
            }

            init();

            logLifeCycle(ClientResources.I_SESSION_CREATED);

        } catch (JMSException jmse) {
            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /**
     * Check ack mode.
     */
    private void checkAckMode (int ackMode) throws JMSException {

        if ( ackMode != Session.AUTO_ACKNOWLEDGE &&
             ackMode != Session.CLIENT_ACKNOWLEDGE &&
             ackMode != Session.DUPS_OK_ACKNOWLEDGE ) {

            if ( this.allowExtensions == true ) {
                //if this is MQ extension, NO_ACK mode is allowed.
                if (ackMode == com.sun.messaging.jms.Session.NO_ACKNOWLEDGE) {

                    if (connection.getBrokerProtocolLevel() <= PacketType.VERSION350) {
                        String errorString = AdministeredObject.cr.getKString(
                        ClientResources.X_BROKER_NOT_SUPPORT_NO_ACK_MODE,
                        connection.getBrokerVersion());

                        JMSException jmse = new com.sun.messaging.jms.JMSException(
                        errorString,
                        ClientResources.X_BROKER_NOT_SUPPORT_NO_ACK_MODE);

                        ExceptionHandler.throwJMSException(jmse);
                    }

                    return;
                }
            }

            String ackModeStr = String.valueOf(ackMode);
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_INVALID_ACKNOWLEDGE_MODE, ackModeStr);

            JMSException jmse =
            new JMSException (errorString,
                             ClientResources.X_INVALID_ACKNOWLEDGE_MODE);

            ExceptionHandler.throwJMSException(jmse);
        }

    }

    private void init() throws JMSException {

        serverSessionRunner = new ServerSessionRunner(this, null);

        //XXX PROTOCOL3.5
        if (connection.getBrokerProtocolLevel() >= PacketType.VERSION350) {
            protocolHandler.createSession(this);
        }

        //construct a transaction object to handle transactions
        //handle differently if under an mc
        if (isTransacted) {
            if (mc == null) {
                //setup Transaction delegate for local txns
                transaction = new Transaction(this, true);
            } else {
                transaction = new Transaction(this, false);
                if (mc.xaTransactionStarted()) {
                    transaction.setTransactionID(mc.getTransactionID());
                    xaTxnMode = true;
                }// else {
                //   transaction.startNewLocalTransaction();
                //}
            }
        }

        /**
         * move the following statement to the begining of the constructor.
         * there seems to have a jdk bug that caused the usage of this
         * object's internal data structure.  (6174742, 6089070)
         */
        //sessionQueue = new SessionQueue();

        //if connection is in stop mode, lock the queue
        if ( connection.getIsStopped() ) {
            sessionQueue.setIsLocked( true );
        }

        //construct unAckedPacketQueue if the session is CLIENT_ACKNOWLEDGE
        //mode or DUPS_OK_ACKNOWLEDGE mode or transacted session.
        //XXX:GT TBF
        /*
        if ( acknowledgeMode == Session.CLIENT_ACKNOWLEDGE ||
             acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE ||
             (isTransacted ) ) {
            unAckedMessageQueue = new Vector();
        }
        */

        //put to the readQTable
        connection.addToReadQTable (sessionId, sessionQueue);

        //put to session table
        connection.addSession ( this );

        //set dups ok limit
        this.dupsOkLimit = connection.getDupsOkLimit();

        this.isAckLimited = connection.getIsAckLimited();

        //set client ack limit
        this.ackLimit = connection.getAckLimit();

        // set setJMSXConsumerTXID flag
        setJMSXConsumerTXID = connection.connectionMetaData.setJMSXConsumerTXID &&
                              isTransacted;

        //protect mode
        protectMode = connection.getProtectMode();

        //ConnectionConsumer workaround 4715054
        isDedicatedToServerSession = connection.getIsDedicatedToConnectionConsumer();

        //dups ok init.
        dupsOkInit();

        //session reader blocks on the session queue and dispatch pkt to the
        //receivers.
        sessionReader = new SessionReader (this);

        if (isDedicatedToServerSession) {
            sessionReader.close();
        }
        else {
            //start the thread.  It is blocked on session queue if queue is locked.
            sessionReader.start();
        }
    }

    /**
     * dups ok ack init.
     * default for appp client: dupsOkAckOnTimeout is set to true.
     *                          dupsOkAckTimeout is set to 7000 milli secs.
     * for MDB: dupsOkAckOnEmptyQueue is always set to true.
     */
    protected void dupsOkInit() {

        //set only if not transacted mode && not MDB
        if ( (isTransacted == false) &&
             (acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE) ) {

            if ( isDedicatedToServerSession ) {
                dupsOkAckOnEmptyQueue = true;
            } else {

                //get dups ok ack flag.
                dupsOkAckOnEmptyQueue = connection.dupsOkAckOnEmptyQueue;

                if ( dupsOkAckOnEmptyQueue == false ) {
                    //get timeout value -- default is set to 7000 milli secs
                    dupsOkAckTimeout = connection.dupsOkAckTimeout;

                    if ( dupsOkAckTimeout > 0 ) {
                        dupsOkAckOnTimeout = true;
                    } else {
                        dupsOkAckOnLimit = true;
                        dupsOkAckTimeout = 0;
                    }
                }
            }

        }

        if ( debug ) {
            if ( dupsOkAckOnTimeout || dupsOkAckOnEmptyQueue || dupsOkAckOnLimit ) {
                Debug.println("*** dupsOkAckOnEmptyQueue: " +
                              dupsOkAckOnEmptyQueue);
                Debug.println("*** dupsOkAckOnTimeout: " + dupsOkAckOnTimeout);
                Debug.println("*** dupsOkAckTimeout: " + dupsOkAckTimeout);
                Debug.println("*** dupsOkAckOnLimit: " + dupsOkAckOnLimit);
            } else {
                Debug.println("*** Session ackMode:  " + acknowledgeMode);
            }
        }
    }

    protected void switchOnXATransaction() throws JMSException {
        //Switching from a local transaction to an XA transaction
        //will force an implicit rollback -- i.e. have to commit before switching
        //                                   else work done so far is discarded
        //unless an xaTxn has already been started on this session (and possibly suspended)
        if (xaTxnMode)
            return;
        if (isTransacted) {
            setInSyncState();
            try {
                receiveRollback();
                transaction.rollbackToXA();
            } finally {
                //This will release sync state.
                releaseInSyncState();
            }
        }
        if (transaction == null) {
            //setup Transaction delegate for txns (local = false)
            transaction = new Transaction(this, false);
            //Indicate that Session is transacted
            isTransacted = true;
            setJMSXConsumerTXID = connection.connectionMetaData.setJMSXConsumerTXID;
        }
        //Indicate that we are now in a dist txn
        xaTxnMode = true;
    }

    //Called from XAResource -- prepare() and commit() only
    protected void switchOffXATransaction() {
        //No longer needs to be in a dist txn
        xaTxnMode = false;
        //Done with Transaction delegate!
        isTransacted = false;
        transaction = null;
        //XXX:GT TBF if we need to be able to switch back to a transacted session
    }

    protected /*synchronized*/ void
    addMessageConsumer( MessageConsumerImpl consumer ) throws JMSException {
        //XXX PROTOCOL2.1
        /*if (serverSessionRunner.getMessageListener() != null) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SVRSESSION_MESSAGECONSUMER);
            throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_SVRSESSION_MESSAGECONSUMER);
        }*/

        consumers.put (consumer.interestId, consumer);
    }

    //ConnectionConsumer workaround 4715054
    protected void
    checkBrowserCreation () throws JMSException {
        if (isDedicatedToServerSession) {
            checkConsumerCreation();
        }
    }

    protected void
    checkConsumerCreation () throws JMSException {
        if (isDedicatedToServerSession || serverSessionRunner.getMessageListener() != null) {

            String errorString = AdministeredObject.cr.getKString(ClientResources.X_SVRSESSION_MESSAGECONSUMER);

            JMSException jmse =
            new javax.jms.IllegalStateException(errorString,
            ClientResources.X_SVRSESSION_MESSAGECONSUMER);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    protected /*synchronized*/ void
    removeMessageConsumer (  MessageConsumerImpl consumer ) {
        consumers.remove( consumer.interestId );
    }

    protected MessageConsumerImpl
    getMessageConsumer ( Object key ) {
        return  (MessageConsumerImpl) consumers.get (key);
    }

    protected /*synchronized*/ void
    addBrowserConsumer(BrowserConsumer consumer) {
        consumer.getBrowser().addBrowserConsumer(consumer);
        browserConsumers.put(consumer.interestId, consumer);
    }

    protected /*synchronized*/ void
    removeBrowserConsumer(BrowserConsumer consumer) {
        browserConsumers.remove(consumer.interestId);
        consumer.getBrowser().removeBrowserConsumer(consumer);
    }

    protected BrowserConsumer
    getBrowserConsumer ( Object key ) {
        return  (BrowserConsumer)browserConsumers.get (key);
    }

    /**
     * Add the producer in the vector when it is created.
     */
    protected void
    addMessageProducer(MessageProducerImpl producer) {
        producers.add(producer);
    }

    /**
     * Remove the producer from the table when it is closed.
     */
    protected void
    removeMessageProducer(MessageProducerImpl producer) {
        producers.remove(producer);
    }

    /**
     * check destination existance in broker
     *
     * @param destination the destination to check
     * @param selector the message selector
     * @param browser if called for QueueBrowser create
     *
     * @exception InvalidDestinationException
     * @exception InvalidSelectorException
     * @exception JMSSecurityException
     * @exception JMSException if fails
     */
    protected void
    verifyDestination(Destination destination, String selector, boolean browser)
                                                  throws JMSException {
        protocolHandler.verifyDestination(destination, selector, browser);
    }

    /**
     * get the content (list of SysMessageIDs) in the destination
     *
     * @param destination the destination
     * @param selector the message selector
     *
     * @exception InvalidDestinationException
     * @exception InvalidSelectorException
     * @exception JMSSecurityException
     * @exception JMSException
     */
    /*protected SysMessageID[]
    getMessageIdSet(Destination destination, String selector)
                                               throws JMSException {
        return protocolHandler.browse(destination, selector);
    }*/
    //XXX PROTOCOL2.1
    protected SysMessageID[]
    getMessageIdSet(Consumer consumer) throws JMSException {
        return protocolHandler.browse (consumer);
    }

    /**
     * request deliver all messages listed (SysMessageIDs) in the
     * ByteArrayOutputStream to the browser consumer
     *
     * @param bos the ByteArrayOutputStream constains list of SysMessaegIDs
     * @param consumer the BrowserConsumer who interest the messages
     *
     * @exception JMSException
     */
    protected boolean requestMessages(ByteArrayOutputStream bos,
                          BrowserConsumer consumer) throws JMSException {
        return protocolHandler.deliver(bos, consumer);
    }

    protected ProtocolHandler
    getProtocolHandler() {
        return connection.getProtocolHandler();
    }

    protected Long
    getSessionId() {
        return sessionId;
    }

    protected ConnectionImpl
    getConnection() {
        return connection;
    }

    protected void
    addAsyncSendCallback(AsyncSendCallback cb) throws JMSException {
        if (isClosed) {
            String emsg = AdministeredObject.cr.getKString(
                          ClientResources.X_SESSION_CLOSED);
            JMSException jmse = new javax.jms.IllegalStateException(emsg,
                                ClientResources.X_SESSION_CLOSED);
            ExceptionHandler.throwJMSException(jmse);
        }

        createAsyncSendCBProcessor();

        if (noAsyncSendCBProcessor) {
            String emsg = AdministeredObject.cr.getKString(
                          ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
            JMSException jmse = new javax.jms.IllegalStateException(emsg, 
                          ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
            ExceptionHandler.throwJMSException(jmse);
        }
        synchronized(asyncSendLock) {
            asyncSends.add(cb);
        }
    }

    private void createAsyncSendCBProcessor() {
        synchronized(asyncSendLock) {
            if (asyncSendCBProcessor == null) {
                asyncSendCBProcessor = new WakeupableTimer(
                        "AsyncSendListenerProcessor["+toString()+"]",
                        new AsyncSendTimerEventHandler(),
                        0L, 0L, 
                        AdministeredObject.cr.getKString(
                            ClientResources.I_ASYNC_SEND_LISTENER_PROCESSOR_THREAD_START)+
                            "["+this.toString()+"]",
                        AdministeredObject.cr.getKString(
                            ClientResources.I_ASYNC_SEND_LISTENER_PROCESSOR_THREAD_EXIT)+
                            "["+this.toString()+"]");
                noAsyncSendCBProcessor = false;
            }
        }
    }

    private class AsyncSendTimerEventHandler implements TimerEventHandler {
        public AsyncSendTimerEventHandler() {
        }
        public void handleOOMError(Throwable e) {
            sessionLogger.log(Level.WARNING, 
            "OutOfMemoryError occurred in AsyncSendListenerProcessor thread["+
             SessionImpl.this.toString()+"]", e);
        }
        public void handleLogInfo(String msg) {
            sessionLogger.log(Level.FINE, msg+"["+SessionImpl.this.toString()+"]");
        }
        public void handleLogWarn(String msg, Throwable e) {
            sessionLogger.log(Level.WARNING, msg+"["+SessionImpl.this.toString()+"]", e);
        }
        public void handleLogError(String msg, Throwable e) {
            sessionLogger.log(Level.WARNING, msg+"["+SessionImpl.this.toString()+"]", e);
        }

        public void handleTimerExit(Throwable e) {
            if (isClosed) {
                return;
            }
            synchronized(asyncSendLock) {
                noAsyncSendCBProcessor = true;
            }
            String emsg = AdministeredObject.cr.getKString(
                          ClientResources.E_ASYNC_SEND_CALLBACK_THREAD_EXIT);
            sessionLogger.log(Level.SEVERE, emsg+"["+SessionImpl.this.toString()+"]", e);
        }

        public long runTask() {
            sessionLogger.log(Level.FINEST, "asyncSendCBProcessor start runTask["+SessionImpl.this.toString()+"]");
            long ret = 0L; //wait for next wakeup

            AsyncSendCallback cb = null;
            ArrayList<AsyncSendCallback> calls = new ArrayList<AsyncSendCallback>();
            synchronized(asyncSendLock) {
                if (asyncSends.size() == 0) {
                    sessionLogger.log(Level.FINEST, "asyncSendCBProcessor end runTask["+SessionImpl.this.toString()+"] ret=0L");
                    return 0L;
                }
                Iterator<AsyncSendCallback> itr = asyncSends.iterator();
                while (itr.hasNext()) {
                    cb = itr.next();
                    if (!cb.hasSendReturned()) {
                        break;
                    }
                    if (cb.isCompleted() || cb.isExceptioned()) {
                        calls.add(cb);
                    } else {
                        if (!cb.isOnAckWait()) { 
                            break;
                        }
                        ret = connection.getAsyncSendCompletionWaitTimeout();
                        if (!cb.isTimedout()) {
                            cb.startTimeoutTimer();
                            break;
                        } else {
                            calls.add(cb);
                        }
                    }
                }
            }
            Iterator<AsyncSendCallback> itr = calls.iterator();
            while (itr.hasNext()) {
                cb = itr.next();
                cb.callCompletionListener();
            } 
            calls.clear();
            sessionLogger.log(Level.FINEST, "asyncSendCBProcessor end runTask["+SessionImpl.this.toString()+"] ret="+ret);
            return ret;
        }
    }

    protected void 
    removeAsyncSendCallback(AsyncSendCallback cb) {
        synchronized(asyncSendLock) {
            asyncSends.remove(cb);
            asyncSendLock.notifyAll();
        }
    }

    protected void
    writeJMSMessage (Message message, AsyncSendCallback asynccb)
    throws JMSException {
        //not calling checkSessionState just for performance reason ...
        if (isClosed) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_SESSION_CLOSED);
            JMSException jmse =
            new javax.jms.IllegalStateException(errorString, ClientResources.X_SESSION_CLOSED);

            ExceptionHandler.throwJMSException(jmse);
        }
        if (asynccb != null && xaTxnMode) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_ASYNC_SEND_XA_TXN);
            JMSException jmse = new JMSException(errorString, ClientResources.X_ASYNC_SEND_XA_TXN);

            ExceptionHandler.throwJMSException(jmse);
        }
        if (isTransacted) {
            checkFailOver();
            transaction.send(message, asynccb);
        } else {
            writeChannel.writeJMSMessage(message, asynccb);
        }
    }

    protected void 
    waitAllAsyncSendCompletion(MessageProducerImpl producer)
    throws JMSException {
        checkPermissionForAsyncSend();
        if (connection.isBroken()) {
            synchronized(asyncSendLock) {
                asyncSendLock.notifyAll();
            }
        }

        AsyncSendCallback cb = null;
        long endtime = System.currentTimeMillis() + 
                 connection.getAsyncSendCompletionWaitTimeout(); 
        long remaining = 0L;
        if (producer == null) {
            int asize = 0; 
            synchronized(asyncSendLock) {
                asize = asyncSends.size();
            }
            boolean firstround = true;
            while (asize > 0) {
                remaining = connection.getAsyncSendCompletionWaitTimeout(); 
                synchronized(asyncSendLock) {
                boolean logged = false;
                while (asyncSends.size() > 0 && remaining > 0L &&  
                       (!firstround || !connection.isBroken() ||
                        !noAsyncSendCBProcessor)) {
                    try {
                        if (!logged) {
                            String msg = AdministeredObject.cr.getKString(
                                ClientResources.I_WAIT_ASYNC_SENDS_COMPLETE_SESSION,
                                String.valueOf(remaining), this);
                                sessionLogger.log(Level.INFO, msg);
                            logged = true;
                        }
                        asyncSendLock.wait(remaining);
                    } catch (InterruptedException e) {
                    }
                    remaining = endtime - System.currentTimeMillis();
                } 
                if (connection.isBroken()) { 
                    asyncSendLock.notifyAll();
                }
                if (asyncSends.size() > 0) {
                    Exception ex = (noAsyncSendCBProcessor ? 
                              noAsyncSendCBProcessorEx:asyncSendWaitTimeoutEx);
                    if (connection.isBroken()) {
                        ex = connectionBrokenEx;
                    }
                    ex.fillInStackTrace();
                    Iterator<AsyncSendCallback> itr = asyncSends.iterator();
                    while (itr.hasNext()) {
                        cb = itr.next();   
                        cb.processException(ex);
                        
                    }
                    if (noAsyncSendCBProcessor) {
                         String emsg = AdministeredObject.cr.getKString(
                             ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
                         JMSException jmse = new JMSException(emsg,
                             ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
                         ExceptionHandler.throwJMSException(jmse);
                    }
                }
                firstround = false;
                asize = asyncSends.size();
                } //synchronized
            }
        } else {
            boolean loggered = false;
            boolean found = true;
            boolean firstround = true;
            remaining = connection.getAsyncSendCompletionWaitTimeout();
            while (found) {
                found = false;
                synchronized(asyncSendLock) {
                    Iterator<AsyncSendCallback> itr = asyncSends.iterator();
                    while (itr.hasNext()) {
                        cb = itr.next();   
                        if (cb.producer == producer) {
                            found = true;
                            if (remaining == 0L ||
                                (firstround && 
                                 (connection.isBroken() || 
                                  noAsyncSendCBProcessor))) {
                                Exception ex = (noAsyncSendCBProcessor ? 
                                    noAsyncSendCBProcessorEx:asyncSendWaitTimeoutEx);
                                if (connection.isBroken()) {
                                    ex = connectionBrokenEx;
                                    asyncSendLock.notifyAll();
                                }
                                ex.fillInStackTrace();
                                cb.processException(ex);
                                if (remaining == 0L) {
                                    remaining = connection.getAsyncSendCompletionWaitTimeout();
                                    loggered = false;
                                    break;
                                }
                            } else {
                                if (noAsyncSendCBProcessor) {
                                    String emsg = AdministeredObject.cr.getKString(
                                        ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
                                    JMSException jmse = new JMSException(emsg,
                                        ClientResources.X_NO_ASYNC_SEND_LISTENER_PROCESSOR_THREAD);
                                    ExceptionHandler.throwJMSException(jmse);
                                }
                               
                                try {
                                    if (!loggered) {
                                        String msg = AdministeredObject.cr.getKString(
                                            ClientResources.I_WAIT_ASYNC_SENDS_COMPLETE_PRODUCER,
                                            String.valueOf(remaining), producer);
                                        sessionLogger.log(Level.FINE, msg);
                                        loggered = true;
                                    }
                                    asyncSendLock.wait(remaining);
                                } catch (InterruptedException e) {
                                }
                                remaining = endtime - System.currentTimeMillis();
                                break;
                            }
                        }
                    }
                    firstround = false;
                }
            }
        }
    }

    public void _stopFromRA() throws JMSException {
        synchronized(raEndpointSyncObj) {
        	/**
        	 * EndpointConsumer.stopMessageConsumer() calls this method to stop the
        	 * SessionReader.
        	 * 
        	 * Do not need to wait because the caller must wait for OnMessageRunners to ruturn.
        	 * bug ID 6526078 - MQ thread deadlock during AS shutdown. 
        	 */
            stop(false);
        }
    }

    protected void stop() throws JMSException {
        stop (true);
    }

    /**
     * Called by Connection.stop(), Session.close().
     */
    protected void
    stop(boolean doWait) throws JMSException {

        if ( isStopped || isClosed ) {
            return;
        }

        checkPermission();

        synchronized ( sessionSyncObj ) {
            sessionQueue.stop(doWait);

            serverSessionRunner.serverSessionStop();

            //lock receive queue for each consumer
            //MessageConsumerImpl consumer = null;
            //Enumeration enum = consumers.elements();
            //while (enum.hasMoreElements()) {
            //    consumer = (MessageConsumerImpl) enum.nextElement();
            //    consumer.stop();
            //}

            MessageConsumerImpl[] consumerArray = (MessageConsumerImpl[])
                consumers.values().toArray(new MessageConsumerImpl[0]);

            for (int i = 0; i < consumerArray.length; i++) {

                if ( doWait ) {
                    consumerArray[i].stop();
                } else {
                    consumerArray[i].stopNoWait();
                }
            }

            isStopped = true;
        }
    }

    protected void setConnectionConsumer(ConnectionConsumerImpl cc) {
        connectionConsumer = cc;
    }

    protected void resetServerSessionRunner() {
        resetServerSessionRunner(true);
    }

    /**
     * @param timeout in seconds
     */
    protected void resetServerSessionRunner(boolean resetState) {
        ServerSessionRunner ssr =  serverSessionRunner;
        if (ssr != null) ssr.reset(resetState);
    }

    /**
     * Reset this session.
     * 1. Stop session reader.
     * 2. Clear session queue.
     * 3. Clear Consumer queues.
     * 4. Clear unAckedMessageQueue.
     * 5. Restart session reader.
     *
     * bug ID 6302418 -- there is really no reason to synchronize this method.
     */
    protected /**synchronized**/ void reset() throws JMSException {
    	
        //1. Stop session reader.  Wait does not provide any benefit here.
        try {
            //fix for bug 6578150 - session.close() takes too long
            //we are in recover state, the world is stopped already
            //stop(false);
    		
            // 2. Clear session queue. -- PRIORITYQ
            sessionQueue.clear();
            // 3. Clear Consumer queue.
            MessageConsumerImpl consumer = null;
            Enumeration enum2 = consumers.elements();
            while (enum2.hasMoreElements()) {
                consumer = (MessageConsumerImpl) enum2.nextElement();
                // PRIORITYQ
                consumer.receiveQueue.clear();
            }
            // 4. Clear unacked message queue
            if (unAckedMessageQueue != null) {
                unAckedMessageQueue.removeAllElements();
            }

            // 5.Restart session reader
            //we did not stop
            //start();

            // 6. clean up the consumers table. The objects will be
            // re-constructed
            // in ConnectionRecover. -- bug ID 6311895.
            consumers.clear();
            // 7. reset this flag to false since we will re-create all the
            // sessions/consumers.
            this.remore_broker_failed = false;
            
            //8. HACC -- set reset flag for serversession state.
            if (serverSessionRunner != null) {
                serverSessionRunner.reset();
            }

            closeBrowserConsumers();
            
        } finally {
            sessionQueue.start();
            this.isStopped = false;
        }
    }

    protected void recreateSession() throws JMSException {
        String emsg = AdministeredObject.cr.getKString(
                          AdministeredObject.cr.X_CONNECTION_FAILOVER);
        JMSException ex = new JMSException(emsg, ClientResources.X_CONNECTION_FAILOVER);
        AsyncSendCallback cb = null;
        synchronized(asyncSendLock) {
            Iterator<AsyncSendCallback> itr = asyncSends.iterator();
            while (itr.hasNext()) {
		cb = itr.next();
		cb.processException(ex);
            }
	}

        if (connection.getBrokerProtocolLevel() >= PacketType.VERSION350) {
            protocolHandler.createSession(this);
        }

        if (isTransacted) {

            if ( connection.isConnectedToHABroker ) {
                //resolve the state of transaction.
                //verifyHATransaction();
            } else {
                // Start a new transaction.
                transaction = new Transaction(this, true);
            }
        }

        failoverOccurred = true;
    }

    protected /** synchronized **/ void
    start() throws JMSException {

        synchronized (sessionSyncObj) {
            //unlock session queue
            sessionQueue.start();

            serverSessionRunner.serverSessionRun();

            //unlock receive queue for each consumer
            /*MessageConsumerImpl consumer = null;
                     Enumeration enum = consumers.elements();
                     while (enum.hasMoreElements()) {
                consumer = (MessageConsumerImpl) enum.nextElement();
                consumer.start();
                     }*/

            MessageConsumerImpl[] consumerArray = (MessageConsumerImpl[])
                                                  consumers.values().toArray(new
                MessageConsumerImpl[0]);

            for (int i = 0; i < consumerArray.length; i++) {
                consumerArray[i].start();
            }

            isStopped = false;
        }
    }

    /**
     * Throws IllegalStateException if called from message listener.
     */
    protected void
    checkPermission() throws JMSException {

        if ( Thread.currentThread() ==  sessionReader.sessionThread
             || Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_ILLEGAL_STATE);

            javax.jms.IllegalStateException jmse =
            new javax.jms.IllegalStateException(
                errorString, ClientResources.X_ILLEGAL_STATE);

            ExceptionHandler.throwJMSException(jmse);
        }

    }

    protected void
    checkPermissionForAsyncSend() throws JMSException {
        if (asyncSendCBProcessor != null && 
            asyncSendCBProcessor.isTimerThread(Thread.currentThread())) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_ILLEGAL_STATE);
            javax.jms.IllegalStateException jmse =
            new javax.jms.IllegalStateException(
                errorString, ClientResources.X_ILLEGAL_STATE);
            ExceptionHandler.throwJMSException(jmse);
        }
    }


    /*
     * This method is called from the RA and serializes acknowledgement to
     * avoid any MT violations on the Session which by rule can only be operated
     * on by a single thread 'at a time'
     * Since it has to do things almost exactly like a transacted session but isn't
     * really one, the code from acknowledge() is reproduced here
     * XXX:candidate to be refactored
     */
    public void acknowledgeUndeliverableFromRAEndpoint(
        MessageImpl message,
        XAResourceForRA xar, boolean sendToDMQ) throws JMSException
    {
        synchronized (raEndpointSyncObj) {

            //do this for flow control
            readChannel.flowControl.messageDelivered();

            Consumer consumer = (Consumer) consumers.get(
                Long.valueOf(message.getInterestID()));

            //We know it *does not* have a ConnectionConsumer
            /*
            if (consumer == null) {
                // It may be a ConnectionConsumer...
                consumer = connection.interestTable.getConsumer(
                    new Long(message.getInterestID()));
            }
            */

            readChannel.flowControl.messageDelivered(consumer);
            TEST_rxCount++;

            try {
                setInSyncState();

                //We do not care about rollback since if transacted
                //it is in an XA transaction and the rollback will be via XA

                //We get the transactionID from the XAResource that was
                //passed in
                //System.out.println("\t\tSessionImpl:acknowledgeFromRA-msg="+message.toString());
                if (xar != null && xar.started()) {
                    synchronized (xar) {
                        //System.out.println("\t\tSessionImpl:ackWxar:txID="+xar.getTransactionID()+":bkrSessionId="+brokerSessionID);
                        ackPkt.setTransactionID(xar.getTransactionID() );
                        //System.out.println("\t\tSessionImpl:ackWxar:txID="+xar.getTransactionID()+"...done:bkrSessionId="+brokerSessionID);
                    }
                } else {
                    //System.out.println("\t\tSessionImpl:ackWithoutxar:bkrSessionId="+brokerSessionID);
                    ackPkt.setTransactionID(0L);
                    //System.out.println("\t\tSessionImpl:ackWithoutxar...done:bkrSessionId="+brokerSessionID);
                }
                writeMessageID (message);
                doAcknowledgeUndeliverable(true, sendToDMQ);
                //System.out.println("\t\tSessionImpl:doAcknowledge...done:bkrSessionId="+brokerSessionID);

            } finally {
                releaseInSyncState();
            }
        }
    }


    /*
     * This method is called from the RA and serializes acknowledgement to
     * avoid any MT violations on the Session which by rule can only be operated
     * on by a single thread 'at a time'
     * Since it has to do things almost exactly like a transacted session but isn't
     * really one, the code from acknowledge() is reproduced here
     * XXX:candidate to be refactored
     */
    public void acknowledgeFromRAEndpoint(MessageImpl message, XAResourceForRA xar) throws JMSException {
        synchronized (raEndpointSyncObj) {
        	
        	if (sessionLogger.isLoggable(Level.FINER)) {
        		logMessageDelivered(message);
        	}

            //do this for flow control
            readChannel.flowControl.messageDelivered();

            Consumer consumer = (Consumer) consumers.get(
                Long.valueOf(message.getInterestID()));

            //We know it *does not* have a ConnectionConsumer
            /*
            if (consumer == null) {
                // It may be a ConnectionConsumer...
                consumer = connection.interestTable.getConsumer(
                    new Long(message.getInterestID()));
            }
            */

            readChannel.flowControl.messageDelivered(consumer);
            TEST_rxCount++;

            boolean requiresAckFromBroker = true;
            Hashtable originalProps = null;
            try {
                setInSyncState();

                //We do not care about rollback since if transacted
                //it is in an XA transaction and the rollback will be via XA

                //We get the transactionID from the XAResource that was
                //passed in
                //System.out.println("\t\tSessionImpl:acknowledgeFromRA-msg="+message.toString());
                if (xar != null && xar.started()) {
                    synchronized (xar) {
                        //System.out.println("\t\tSessionImpl:ackWxar:txID="+xar.getTransactionID()+":bkrSessionId="+brokerSessionID);
                        ackPkt.setTransactionID(xar.getTransactionID() );
                        //System.out.println("\t\tSessionImpl:ackWxar:txID="+xar.getTransactionID()+"...done:bkrSessionId="+brokerSessionID);
                    }
                    // performance optimisation
                    // Do not wait for reply from broker when
                    // sending message acknowledgments in a transaction.
                    // Reliability will be ensured by blocking on commit.
                    requiresAckFromBroker = !noBlockUntilTxnCompletes;
                } else {
                    //System.out.println("\t\tSessionImpl:ackWithoutxar:bkrSessionId="+brokerSessionID);
                    ackPkt.setTransactionID(0L);
                    //System.out.println("\t\tSessionImpl:ackWithoutxar...done:bkrSessionId="+brokerSessionID);
                    if (!isTransacted && acknowledgeMode == Session.AUTO_ACKNOWLEDGE) {
                        if ((consumer != null && !consumer.getDurable()) ||
                             noBlockOnAutoAckNPTopics) {
                             /* performance optimisation
                                Normally block when acknowledging messages 
                                to avoid duplicates in the event of provider failure.
                                However, duplicates are not a problem for NP topic sessions, 
                                as messages will not be re-dispatched if client reconnects after failure. */
                                Destination d = message.getJMSDestination();
                            if (!message._getPersistent() && d instanceof Topic) {
                                requiresAckFromBroker = false;
                            }
                        }
                    }
                }
                writeMessageID (message);

                if (message.getClientRetries() > 0) {
                    try {
                        originalProps = ackPkt.getProperties();
                    } catch (Exception e) {}
                    Hashtable props = new Hashtable();
                    props.put(ConnectionMetaDataImpl.JMSXDeliveryCount, Integer.valueOf(message.getClientRetries()));
                    ackPkt.setProperties(props);
                }

                doAcknowledge(requiresAckFromBroker);
                //System.out.println("\t\tSessionImpl:doAcknowledge...done:bkrSessionId="+brokerSessionID);

            } finally {
                if (originalProps != null)
                    ackPkt.setProperties(originalProps);
                releaseInSyncState();
            }
        }
    }

    public void acknowledgeFromRAEndpoint(MessageImpl message) throws JMSException {
        synchronized (raEndpointSyncObj) {
            acknowledge(message);
        }
    }


    public void _appTransactedAck(MessageImpl message) throws JMSException {
        Consumer consumer = (Consumer)consumers.get(
                                Long.valueOf(message.getInterestID()));
        if (consumer == null) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CONSUMER_CLOSED);
            throw new JMSException (errorString, AdministeredObject.cr.X_CONSUMER_CLOSED);
        }
        if (!(consumer instanceof MessageConsumerImpl)) { //should not happen
            throw new JMSException(
            "Operation not supported for consumer type: "+consumer.getClass().getName());
        }
        acknowledge(message);

        ((MessageConsumerImpl)consumer).lastDeliveredID = message.getMessageID();
        
    }

    protected void doPrefetch(Consumer consumer) {
        readChannel.flowControl.messageDelivered();
        readChannel.flowControl.messageDelivered(consumer);
        TEST_rxCount++;
    }

    protected void acknowledge(MessageImpl message) throws JMSException {
        acknowledge(message, true);
    }


    /* This method is called after MessageListener.onMessage() is returned
     * or receive() call returned.
     *
     * This method dispatch the ack action based on the session ack mode.
     */
    protected void acknowledge(MessageImpl message, boolean prefetch) throws JMSException{

        if ( sessionLogger.isLoggable(Level.FINER) ) {

            //String pktType = PacketType.getString( ((MessageImpl) message).getPacket().getPacketType());

            //String param = pktType + "," +
            //               ", ConsumerID=" + message.getInterestID() +
            //               ", " + toString();

        	this.logMessageDelivered(message);

        }

        if (prefetch) {
            //do this for flow control
            readChannel.flowControl.messageDelivered();
        }

        Consumer consumer = (Consumer) consumers.get(
            Long.valueOf(message.getInterestID()));

        if (consumer == null) {
            // It may be a ConnectionConsumer...
            consumer = connection.interestTable.getConsumer(
                Long.valueOf(message.getInterestID()));
        }

        if (prefetch) {
            readChannel.flowControl.messageDelivered(consumer);
            TEST_rxCount++;
        }

        try {
            setInSyncState();

            if (isTransacted) {
                transactedAcknowledge(message);
            }
            else { //non-transacted
                switch (acknowledgeMode) {
                    case Session.AUTO_ACKNOWLEDGE:
                        autoAcknowledge(message, 
                            (consumer == null ? true:consumer.getDurable()));
                        break;
                    case Session.CLIENT_ACKNOWLEDGE:
                        prepareClientAcknowledge(message);
                        break;
                    case Session.DUPS_OK_ACKNOWLEDGE:
                        if ( dupsOkAckOnTimeout ) {
                            syncedDupsOkAcknowledge(message);
                        } else {
                            dupsOkAcknowledge(message);
                        }
                        break;
                    case com.sun.messaging.jms.Session.NO_ACKNOWLEDGE:
                        //NO_ACKNOWLEDGE mode -- do nothing
                        break;
                    default:
                        autoAcknowledge(message, 
                            (consumer == null ? true:consumer.getDurable()));
                        break;
                } //switch
            }
            
        } catch (JMSException jmse) {
        	
        	//we only care about auto/dups-ok mode
        	if ( (isTransacted == false) && 
        			(acknowledgeMode == Session.AUTO_ACKNOWLEDGE || 
        			acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE)) {
        		
        		//check if this is caused by the remote broker failure.
        		if (isRemoteException (jmse)) {
        			//recreate consumers in the session
        			this.recreateConsumers();
        			
        			ExceptionHandler.throwRemoteAcknowledgeException
        			(jmse, ClientResources.X_AUTO_ACK_FAILED_REMOTE);
        			
        			//construct the precise exception
        			//String errorString = AdministeredObject.cr.getKString(
                    //        ClientResources.X_AUTO_ACK_FAILED_REMOTE);
        			
        			//JMSException newjmse = new com.sun.messaging.jms.JMSException(errorString,
                    //        ClientResources.X_AUTO_ACK_FAILED_REMOTE);
        			
        			//set exception link
        			//newjmse.setLinkedException(jmse);
                    //throw the exception
        			//ExceptionHandler.throwJMSException(newjmse);
        		}
        	}
        	
        	//re-throw the old one.
        	throw jmse;
            
        } finally {
            releaseInSyncState();
        }
    }

    protected void acknowledgeExpired(MessageImpl message) throws JMSException {
        acknowledgeExpired(message, true);
    }

    protected void acknowledgeExpired(MessageImpl message, boolean prefetch)
    throws JMSException {
        if ( sessionLogger.isLoggable(Level.FINER) ) {
            this.logMessageExpired(message);
        }
        acknowledgeDeadMessage(message, prefetch, 1 /*expired*/);
    }

    protected void acknowledgeUndeliverable(MessageImpl message) throws JMSException {
        if ( sessionLogger.isLoggable(Level.FINER) ) {
            this.logMessageUndeliverable(message);
        }
        acknowledgeDeadMessage(message, true, 0 /*undeliverable*/);
    }

    protected void acknowledgeDeadMessage(MessageImpl message, 
                                          boolean prefetch, 
                                          int deadReason)
    throws JMSException {

        synchronized(raEndpointSyncObj) {

        if (prefetch) {
            readChannel.flowControl.messageDelivered();
        }

        Consumer consumer = (Consumer)consumers.get(Long.valueOf(message.getInterestID()));

        if (consumer == null) {
            // It may be a ConnectionConsumer...
            consumer = connection.interestTable.getConsumer(Long.valueOf(message.getInterestID()));
        }

        if (prefetch) {
            readChannel.flowControl.messageDelivered(consumer);
            TEST_rxCount++;
        }

        try {
            expireDos.writeLong(message.getInterestID());
            message.getMessageID().writeID(expireDos);
            expireDos.flush();
            expireBos.flush();
            expirePkt.setMessageBody(expireBos.toByteArray());
            ackPkt.setSendAcknowledge(false);
            protocolHandler.acknowledgeUndeliverable(expirePkt, true, deadReason);

        } catch (Exception e) {
            String destName = "";
            try {
                if (consumer != null) {
                    destName = consumer.getDestName();
                } else {
                    destName= message.getJMSDestination().toString();
                }
            } catch (Exception e1) {};
            Object args[] = { message.getMessageID(), destName,
                               message.getInterestID(), e.getMessage() };
            String ecode = (deadReason == 1 ?
                            ClientResources.X_EXPIRE_MSG_TO_DMQ:
                            ClientResources.X_UNDELIVERABLE_MSG_TO_DMQ);
            String estr =  AdministeredObject.cr.getKString(ecode, args);
            JMSException jmse = new JMSException(estr, ecode);
            jmse.setLinkedException(e);
            throw jmse;
        } finally {
            expireBos.reset();
        }

        }//synchronized
    }
    
    private void logMessageExpired(MessageImpl message) {
        logMessage(message, ClientResources.I_EXPIRED_MSG_BEFORE_DELIVER_TO_CONSUMER); 
    }

    private void logMessageUndeliverable(MessageImpl message) {
        logMessage(message, ClientResources.I_UNDELIVERABLE_MSG); 
    }

    private void logMessageDelivered(MessageImpl message) {
        logMessage(message, ClientResources.I_CONSUMER_MESSAGE_DELIVERED);
    }

    private void logMessage(MessageImpl message, String key) {
		try {
			if (sessionLogger.isLoggable(Level.FINER)) {
				String param = makeDebugStringForMessage(message);
				sessionLogger.log(Level.FINER, key, param);
				if (sessionLogger.isLoggable(Level.FINEST)) {
					param = "MQTrace=MessageConsumer" +
							", ConsumerID=" + ((MessageImpl) message).getInterestID() +
							", Message=" + message.toString();
					sessionLogger.log(Level.FINEST, key, param);
				}
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

    private String makeDebugStringForMessage(MessageImpl message) {  
        try {

        com.sun.messaging.Destination mqDest = (com.sun.messaging.Destination)
                                               message.getJMSDestination();
        String domain = (mqDest.isQueue() ? "Queue:" : "Topic:");

        String pktType = PacketType.getString(((MessageImpl)message)
                                              .getPacket().getPacketType());

        return ("MQTrace=MessageConsumer" + 
                       ", ThreadID=" + Thread.currentThread().getId() + 
                       ", ClientID=" + this.connection.getClientID() + 
                       ", ConnectionID=" + this.connection.getConnectionID() + 
                       ", SessionID=" + this.getBrokerSessionID() + 
                       ", ConsumerID=" + ((MessageImpl) message).getInterestID() +
                       ", Destination=" +domain+mqDest.getName() + 
                       ", MessageID=" + message.getJMSMessageID() +
                       ", MessageType=" + pktType);

        } catch (Exception e) {
        e.printStackTrace();
        return "[MessageID="+message.getMessageID()+", ConsumerID="+message.getInterestID()+"]";
        }
    }

    /**
	 * auto acknowledge.
	 * 
	 * @param message
	 *            the message to be acknowledged.
	 */
    protected void autoAcknowledge (MessageImpl message, boolean isdurable) throws JMSException {
        //param true if require broker to ack back.
        writeMessageID (message);
        boolean requireAckFromBroker = true;
        if (!isdurable || noBlockOnAutoAckNPTopics) {
        	// performance optimisation
        	// Normally block when acknowledging messages 
        	// to avoid duplicates in the event of provider failure.
        	// However, duplicates are not a problem for NP topic sessions, 
        	// as messages will not be re-dispatched if client reconnects after failure.
        	//
			Destination d = message.getJMSDestination();
			if (!message._getPersistent() && d instanceof Topic) {
				requireAckFromBroker = false;
			}
		}
     	  
        doAcknowledge(requireAckFromBroker);
    }

    /**
     * For transacted session, each message is acknowledged by calling
     * this method.
     *
     * @param message the message to be acknowledged.
     */
    protected void
    transactedAcknowledge (MessageImpl message) throws JMSException {
    	try {
			// put on ack list in case we have to rollback.
			boolean isAddedToList = prepareTransactedAcknowledge(message);

			// if it is added to the list, we know this message is not
			// acked yet.
			if (isAddedToList) {
				ackPkt.setTransactionID(transaction.getTransactionID());
				writeMessageID(message);

				// performance optimisation
				// Do not wait for reply from broker when 
				// sending message acknowledgments in a transaction.
				// Reliability will be ensured by blocking on commit.
				boolean requiresAckFromBroker = !noBlockUntilTxnCompletes;
				doAcknowledge(requiresAckFromBroker);
			}
		} catch (JMSException jmse) {
			
			//we only set the flag here.  app needs to call Session.rollback() to
			//recreate consumer and flag will be cleared.
			if (jmse instanceof RemoteAcknowledgeException) {
				this.remore_broker_failed = true;
			}
			
			//re-throw exception
			throw jmse;
		}
    }


    /**
	 * Write interestID and messageID to the byte array.
	 * 
	 * @param message
	 *            the message to be acked/redelivered.
	 */
    protected void
    writeMessageID (MessageImpl message) throws JMSException {
        try {
            //XXX PROTOCOL2.1
            dos.writeLong( message.getInterestID() );
            message.getMessageID().writeID(dos);
        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
        }
    }

    protected void
    writeMessageID (UnAckedMessage message) throws JMSException {
        try {
            //XXX PROTOCOL2.1
            dos.writeLong( message.getConsumerID() );
            message.getMessageID().writeID(dos);
        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
        }
    }

    /**
     * Write interestID and messageID to the byte array.
     *
     * @param pkt the pkt to be acked/redelivered.
     */
    protected void
    writeMessageID (ReadOnlyPacket pkt) throws JMSException {
        try {
            //XXX PROTOCOL2.1
            dos.writeLong( pkt.getConsumerID() );
            pkt.getSysMessageID().writeID(dos);
        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
        }
    }

    /**
     * This actually writes ack to the broker
     *
     * @param requireAckFromBroker true if requires broker to ack back.
     */
    protected void
    doAcknowledgeUndeliverable (boolean requireAckFromBroker, boolean sendToDMQ) throws JMSException {

        try {
            dos.flush();
            bos.flush();

            //set message body
            ackPkt.setMessageBody( bos.toByteArray() );

            //set bit if require broker to ack back.
            ackPkt.setSendAcknowledge( requireAckFromBroker );
            //write ack packet
            protocolHandler.acknowledgeUndeliverable (ackPkt, sendToDMQ);

            TEST_ackCount++;

        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_ACK);
        } finally {
            //reset buf count to 0
            bos.reset();
            //reset counter
            ackCounter = 0;
            //reset time stamp
            dupsOkTimestamp = 0;
        }
    }

    /**
     * This actually writes ack to the broker
     *
     * @param requireAckFromBroker true if requires broker to ack back.
     */
    protected void
    doAcknowledge (boolean requireAckFromBroker) throws JMSException {

        try {
            dos.flush();
            bos.flush();

            //set message body
            ackPkt.setMessageBody( bos.toByteArray() );

            //set bit if require broker to ack back.
            ackPkt.setSendAcknowledge( requireAckFromBroker );

            //check failover flag again before acknowledge to broker.
            //bug 6309751 - Unexpected Broker Internal Error during fail over.
            this.checkFailOver();

            protocolHandler.acknowledge(ackPkt);

            TEST_ackCount++;

        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_ACK);
        } finally {
            //reset buf count to 0
            bos.reset();
            //reset counter
            ackCounter = 0;
            //reset time stamp
            dupsOkTimestamp = 0;
        }
    }

    /**
     * DupsOkAcknowledge mode.  Called by Session.acknowledge().
     *
     * @param message the message to be acked.
     */
    protected void dupsOkAcknowledge (MessageImpl message) throws JMSException {

        addMessageToAckList (message);

        /**
         * Acknowledge when reached limit or sessionQueue is empty.
         * This is the minimum check required to ensure messages
         * are acked.  More sophicated ack may be used later.
         */
        //if ( ackCounter == dupsOkLimit || sessionQueue.isEmpty() ) {
        if ( dupsOkShouldAcknowledge() ) {
            dupsOkCommitAcknowledge();
            //dequeue ( unAckedMessageQueue );
            //doAcknowledge(false);
        }
    }

    protected void dupsOkCommitAcknowledge() throws JMSException {

        if ( debug ) {
            Debug.println("***** dups ok committing ack .... size: " + ackCounter);
        }

        dequeueUnAckedMessages();
        doAcknowledge(false);
    }

    //called if session is dupsOkAckOnTimeout mode.
    protected void syncedDupsOkAcknowledge (MessageImpl message) throws JMSException {

        //sync on dupsOkSyncObj to prevent deadlock - bugid 4987018
        synchronized (dupsOkSyncObj) {
            //set timestamp if this is the first unacked msg.
            if ( ackCounter == 0 ) {
                dupsOkTimestamp = System.currentTimeMillis();
            }

            dupsOkAcknowledge (message);
        }
    }

    //called by consumer reader.
    protected void syncedDupsOkCommitAcknowledge() throws JMSException {

        //sync on dupsOkSyncObj - bugid 4987018
        synchronized (dupsOkSyncObj) {
            if ( ackCounter > 0 ) {
                dupsOkCommitAcknowledge();
            }
        }
    }

    protected boolean dupsOkShouldAcknowledge() {

        if ( dupsOkAckOnTimeout ) {
            //time elapsed since first unacked message was received.
            boolean timeToAck =
            (System.currentTimeMillis() - dupsOkTimestamp) >= dupsOkAckTimeout;
            return ((ackCounter == dupsOkLimit) || timeToAck);
        } else if ( dupsOkAckOnEmptyQueue ) {
            return ( ackCounter == dupsOkLimit || sessionQueue.isEmpty() );
        } else { //ack on limit
            return (ackCounter == dupsOkLimit);
        }
    }

    /**
     * called by prepareClientAcknowledge() and prepareTransactedAcknowledge().
     *
     * Do NOT require to synchronize this method because ONLY one thread
     * can call this method at a time.
     */
    protected boolean
    addMessageToAckList (MessageImpl message) throws JMSException {

        boolean isAddedToList = false;

        if ( message != null && message.getIsOnAckList() == false ) {
            message.setIsOnAckList (true);
            //unAckedMessageQueue.addElement(message);
            //use light weight obj instead -- bug 4934856
            UnAckedMessage unacked = new UnAckedMessage (message);
            unAckedMessageQueue.addElement(unacked);

            ackCounter ++;

            isAddedToList = true;
        }

        return isAddedToList;
    }

    /**
     * Remove message from ack list.
     * Called when consumer is closed.  This removes unacked messages
     * on the list if any. -- 4934856
     */
    protected void
    removeMessageFromAckList(UnAckedMessage unacked) {
        //message.setIsOnAckList (false);
        unAckedMessageQueue.removeElement(unacked);
        ackCounter --;
    }

    /**
     * For client ack mode.  Called by Session.acknowledge().
     *
     * @param message the message to be acked.
     */
    protected /*synchronized*/ void
    prepareClientAcknowledge (MessageImpl message) throws JMSException {
        //if ( message.getIsOnAckList() == false ) {
        addMessageToAckList (message);
        if ( isAckLimited ) {
            checkClientAckLimit();
        }
        //}
    }

    /**
     * Method to check client unacknowledged method limit.  Used by client
     * acknowledge mode.
     */
    protected void checkClientAckLimit() throws JMSException {
        if ( ackCounter > ackLimit ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_CLIENT_ACK_LIMIT);
            Debug.println(errorString);
            //throw new JMSException (errorString, ClientResources.X_CLIENT_ACK_LIMIT);
        }
    }

    protected boolean
    prepareTransactedAcknowledge (MessageImpl message) throws JMSException {

        boolean isAddedToList = false;

        isAddedToList = addMessageToAckList (message);

        if ( isAckLimited ) {
            checkTransactedAckLimit();
        }

        return isAddedToList;
    }

     /**
     * Check transacted unack message limit.  Used by transacted session.
     */
    protected void checkTransactedAckLimit() throws JMSException {
        //check unacked limit
        if ( ackCounter > ackLimit ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_COMMIT_LIMIT);
            Debug.println(errorString);
            //throw new JMSException (errorString, ClientResources.X_COMMIT_LIMIT);
        }
    }
    
	/* (non-Javadoc)
	 * @see com.sun.messaging.jmq.jmsclient.ContextableSession#clientAcknowledge()
	 */
	public void clientAcknowledge() throws JMSException {
		
		if (getAcknowledgeMode()!=Session.CLIENT_ACKNOWLEDGE) return;

		if (failoverOccurred) {
			// "Cannot acknowledge messages due to provider connection failover. 
			// "Subsequent acknowledge calls will also fail until the application calls session.recover()."
			String errorString = AdministeredObject.cr.getKString(ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);
			JMSException jmse = new JMSException(errorString, ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);
			ExceptionHandler.throwJMSException(jmse);
		}
		
        checkSessionState();

		if (unAckedMessageQueue.size() > 0) {
			dequeueUnAckedMessages();
			// write the list to the broker.
			doClientAcknowledge();
		}
	}

    /**
     * Called by Message.acknowledge().  This method could be called from two
     * different threads.  One from SessionReader thread, and the other from
     * the user thread.  If called from SessionReader thread, the message is
     * likely to be not on the ack list yet.  We need to check if the current
     * message is on the list.  If not, it is added to the list.
     *
     * Only messages up to the current message should be acknowledged.  There
     * may be messages in the unAckedMessageQueue (messages has been delivered
     * to the client) but the client decides not to acknowledge for whatever
     * reasons.  We should leave those messages in the queue according to the
     * spec.
     */
    protected /*synchronized*/ void
    clientAcknowledge (MessageImpl message) throws JMSException {

		if (failoverOccurred) {
			// "Cannot acknowledge messages due to provider connection failover. 
			// "Subsequent acknowledge calls will also fail until the application calls session.recover()."
			String errorString = AdministeredObject.cr.getKString(ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);
			JMSException jmse = new JMSException(errorString, ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);
			ExceptionHandler.throwJMSException(jmse);
		}

        /**
         * When message consumer is closed, doAcknowledge flag is set
         * to false.  We(George, Amy and Chiaming) decided to throw
         * exception in this case.
         */
        //XXX:tharakan revisit since Session changes should now allow message to be acknowledged
        //XXX:tharakan after the consumer is closed.
        //if ( message.doAcknowledge == false ) {
        //    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //    throw new javax.jms.IllegalStateException (errorString, AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //}
        this.checkClientAckMessage(message);

        checkSessionState();

        if ( isTransacted == false ) {
            //check if on the list, if not, add this message to the list
            prepareClientAcknowledge (message);

            //The following code is to write unacked message IDs to byte array.
            //The unAcked queue is searched sequentially until the message ID
            //in the queue matches the current acking message ID.
            //MessageImpl unAckedMessage = null;
            //boolean found = false;
            //while ( !found ) {
                //make this simple and stupid.  do not disturb the order of
                //messages in the unAcked queue.
                //unAckedMessage = (MessageImpl) unAckedMessageQueue.firstElement();
                //writeMessageID ( unAckedMessage );
                //remove since it has been moved to the out going array.
                //unAckedMessageQueue.removeElementAt(0);

                //if ( message.messageID.equals(unAckedMessage.messageID) ) {
                    //found = true;
                //}
            //}

            /**
             * JMS 1.0.2 (Update b) changed the meaning of Message.acknowledge()
             * From ackowledge all messages consumed upto and including the
             * current one in the session to acknowledge all messages
             * consumed in the session
             */
            if ( unAckedMessageQueue.size() > 0 ) {
                dequeueUnAckedMessages();
                //write the list to the broker.
                //doAcknowledge(true);
                doClientAcknowledge();
            }
        }
    }

    /**
     * Called by Message.acknowledgeThisMessage().
     * This method could be called from two different threads.
     * One from SessionReader thread, and the other from the user thread.
     * If called from SessionReader thread, the message is likely to be not
     * on the ack list yet.  We need to check if the current message is
     * on the list.  If not, it is added to the list.
     *
     * Only this message should be acknowledged. All other message in the
     * unAckedMessageQueue should be left unacknowledged
     */
    protected /*synchronized*/ void
    clientAcknowledgeThisMessage (MessageImpl message) throws JMSException {

        /**
         * When message consumer is closed, doAcknowledge flag is set
         * to false.  We(George, Amy and Chiaming) decided to throw
         * exception in this case.
         */
        //XXX:tharakan revisit since Session changes should now allow message to be acknowledged
        //XXX:tharakan after the consumer is closed.
        //if ( message.doAcknowledge == false ) {
        //    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //    throw new javax.jms.IllegalStateException (errorString, AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //}
        this.checkClientAckMessage(message);

        checkSessionState();

        if ( isTransacted == false ) {
            //check if on the list, if not, add this message to the list
            prepareClientAcknowledge (message);
            //bug 4934856
            UnAckedMessage unAckedMessage = null;
            //kiss - linear search for the messageID
            for (int i = 0; i < unAckedMessageQueue.size(); i++) {
                unAckedMessage = (UnAckedMessage) unAckedMessageQueue.elementAt(i);
                if (message.messageID.equals(unAckedMessage.getMessageID())) {
                    //write the message ID
                    writeMessageID(unAckedMessage);
                    //remove it from the unacked list
                    unAckedMessageQueue.removeElementAt(i);
                    //write the acknowledge list (one message) to the broker
                    //doAcknowledge(true);
                    doClientAcknowledge();
                    return;
                }
            }
        }
    }

    /**
     * Called by Message.acknowledgeUpThroughThisMessage().
     * This method could be called from two different threads.
     * One from SessionReader thread, and the other from the user thread.
     * If called from SessionReader thread, the message is likely to be not
     * on the ack list yet.  We need to check if the current message is
     * on the list.  If not, it is added to the list.
     *
     * Only messages up to the current message should be acknowledged.  There
     * may be messages in the unAckedMessageQueue (messages has been delivered
     * to the client) but the client decides not to acknowledge for whatever
     * reasons.  We should leave those messages in the queue.
     */
    protected /*synchronized*/ void
    clientAcknowledgeUpThroughThisMessage (MessageImpl message) throws JMSException {

        // Messages cannot be acknowledged after connection failover.
        // Reject client acknowledgements until the application calls
        // recover()...
        if (failoverOccurred) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);
            JMSException jmse = new JMSException(errorString,
                ClientResources.X_CLIENT_ACK_FAILOVER_OCCURRED);

            ExceptionHandler.throwJMSException(jmse);
        }

        /**
         * When message consumer is closed, doAcknowledge flag is set
         * to false.  We(George, Amy and Chiaming) decided to throw
         * exception in this case.
         */
        //XXX:tharakan revisit since Session changes should now allow message to be acknowledged
        //XXX:tharakan after the consumer is closed.
        //if ( message.doAcknowledge == false ) {
        //    String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //    throw new javax.jms.IllegalStateException (errorString, AdministeredObject.cr.X_CLIENT_ACKNOWLEDGE);
        //}
        this.checkClientAckMessage(message);

        checkSessionState();

        if ( isTransacted == false ) {
            //check if on the list, if not, add this message to the list
            prepareClientAcknowledge (message);

            //check if the message is in the unacked queue.
            if ( isMessageInUnAckedQueue(message) ) {

                //The following code is to write unacked message IDs to byte array.
                //The unAcked queue is searched sequentially until the message ID
                //in the queue matches the current acking message ID.
                UnAckedMessage unAckedMessage = null;
                boolean found = false;
                while (!found) {
                    //make this simple and stupid.  do not disturb the order of
                    //messages in the unAcked queue. -- bug 4934856
                    unAckedMessage =
                    (UnAckedMessage) unAckedMessageQueue.firstElement();

                    writeMessageID(unAckedMessage);
                    //remove since it has been moved to the out going array.
                    unAckedMessageQueue.removeElementAt(0);

                    if (message.messageID.equals(unAckedMessage.getMessageID())) {
                        found = true;
                    }
                }

                //write the list to the broker.
                //doAcknowledge(true);
                doClientAcknowledge();
            }
        }
    }
    
    private void doClientAcknowledge() throws JMSException {
    	
    	if (this.remore_broker_failed) {
    		
    		ExceptionHandler.throwRemoteAcknowledgeException
    		(null, ClientResources.X_CLIENT_ACK_FAILED_REMOTE);
    		
			//String errorString = AdministeredObject.cr
			//		.getKString(ClientResources.X_CLIENT_ACK_FAILED_REMOTE);

			//JMSException jmse = new com.sun.messaging.jms.JMSException(
			//		errorString, ClientResources.X_CLIENT_ACK_FAILED_REMOTE);

			// throw the exception
			//ExceptionHandler.throwJMSException(jmse);
		}
    	
    	try {
			doAcknowledge(true);
		} catch (JMSException jmse) {

			if (isRemoteException(jmse)) {
				
				//set this flag so that no further client ack is allowed.
				this.remore_broker_failed = true;
				
				//throw remote ack failed exception
				ExceptionHandler.throwRemoteAcknowledgeException
				(jmse, ClientResources.X_CLIENT_ACK_FAILED_REMOTE);

				// rethrow
				// construct the precise exception
				//String errorString = AdministeredObject.cr
				//		.getKString(ClientResources.X_CLIENT_ACK_FAILED_REMOTE);

				//JMSException newjmse = new com.sun.messaging.jms.JMSException(
				//		errorString, ClientResources.X_CLIENT_ACK_FAILED_REMOTE);

				// set exception link
				//newjmse.setLinkedException(jmse);
				// throw the exception
				//ExceptionHandler.throwJMSException(newjmse);
			} else {
				//no-op, re-throw the old exception
				throw jmse;
			}
		}
    }
    
    protected boolean
    isMessageInUnAckedQueue (MessageImpl message) throws JMSException {
        boolean inQueue = false;
        //4934856
        UnAckedMessage unAckedMessage = null;
        int size = unAckedMessageQueue.size();

        for ( int i=0; i< size; i++ ) {
            unAckedMessage = (UnAckedMessage) unAckedMessageQueue.elementAt(i);
            if ( message.messageID.equals(unAckedMessage.getMessageID() ) ) {
                inQueue = true;
                //break the loop
                i = size;
            }
        }

        return inQueue;
    }

    /**
     * Check if session is closed.
     * @throws IllegalStateException if session is closed.
     */
    protected void checkSessionState() throws JMSException {
        if ( isClosed ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_SESSION_CLOSED);

            JMSException jmse =
                new javax.jms.IllegalStateException(errorString,
                                ClientResources.X_SESSION_CLOSED);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    protected void checkFailOver() throws JMSException {

        if ( isTransacted && failoverOccurred ) {
            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_INVALIDATED_FAILOVER);
            JMSException jmse = new javax.jms.JMSException(errorString,
                ClientResources.X_TRANSACTION_INVALIDATED_FAILOVER);

            ExceptionHandler.throwJMSException(jmse);
        }
    }

    /** Create a BytesMessage. A BytesMessage is used to send a message
      * containing a stream of uninterpreted bytes.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public BytesMessage
    createBytesMessage() throws JMSException {
        checkSessionState();
        //param true is to init DataOutputStream for writing.
        return new BytesMessageImpl (true);
    }


    /** Create a MapMessage. A MapMessage is used to send a self-defining
      * set of name-value pairs where names are Strings and values are Java
      * primitive types.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public MapMessage
    createMapMessage() throws JMSException {
        checkSessionState();
        return new MapMessageImpl();
    }


    /** Create a Message. The Message interface is the root interface of
      * all JMS messages. It holds all the standard message header
      * information. It can be sent when a message containing only header
      * information is sufficient.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public Message
    createMessage() throws JMSException {
        checkSessionState();

        Message message = new MessageImpl();
        return message;
    }


    /** Create an ObjectMessage. An ObjectMessage is used to send a message
      * that containing a serializable Java object.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public ObjectMessage
    createObjectMessage() throws JMSException {
        checkSessionState();
        return new ObjectMessageImpl();
    }


    /** Create an initialized ObjectMessage. An ObjectMessage is used
      * to send a message that containing a serializable Java object.
      *
      * @param object the object to use to initialize this message.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public ObjectMessage
    createObjectMessage(Serializable object) throws JMSException {
        checkSessionState();
        ObjectMessage objectMessage = new ObjectMessageImpl();
        objectMessage.setObject (object);

        return objectMessage;
    }


    /** Create a StreamMessage. A StreamMessage is used to send a
      * self-defining stream of Java primitives.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public StreamMessage
    createStreamMessage() throws JMSException {
        checkSessionState();
        //param true is to init ObjectOutputStream for writing.
        return new StreamMessageImpl (true);
    }


    /** Create a TextMessage. A TextMessage is used to send a message
      * containing a String.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public TextMessage
    createTextMessage() throws JMSException {
        checkSessionState();
        return new TextMessageImpl();
    }


    /** Create an initialized TextMessage. A TextMessage is used to send
      * a message containing a String.
      *
      * @param text the string used to initialize this message.
      *
      * @exception JMSException if JMS fails to create this message
      *                         due to some internal error.
      */

    public TextMessage
    createTextMessage(String text) throws JMSException {
        checkSessionState();
        TextMessageImpl message = new TextMessageImpl();
        message.setText( text );
        return message;
    }


    /** Is the session in transacted mode?
      *
      * @return true if in transacted mode
      *
      * @exception JMSException if JMS fails to return the transaction
      *                         mode due to internal error in JMS Provider.
      */

    public boolean
    getTransacted() throws JMSException {
        checkSessionState();
        return isTransacted;
    }

    protected boolean
    getTransactedNoCheck() {
        return isTransacted;
    }

    /** Gets value for how messages are acknowledged.
     *
     * @return one of the following values: <CODE>AUTO_ACKNOWLEDGE</CODE>,
     *       <CODE>CLIENT_ACKNOWLEDGE</CODE>, <CODE>DUPS_OK_ACKNOWLEDGE</CODE>
     *
     * @exception JMSException   if the JMS provider fails to return the
     *                         acknowledge mode due to some internal error.
     *
     * @see Connection#createSession
     * @since 1.1
     */
    public int
    getAcknowledgeMode() throws JMSException {
        checkSessionState();
        if (isTransacted) {
            return 0;
        } else {
            return acknowledgeMode;
        }
    }


    /** Commit all messages done in this transaction and releases any locks
      * currently held.
      *
      * @exception JMSException if JMS implementation fails to commit the
      *                         the transaction due to some internal error.
      * @exception TransactionRolledBackException  if the transaction
      *                         gets rolled back due to some internal error
      *                         during commit.
      * @exception IllegalStateException if method is not called by a
      *                         transacted session.
      */

    public /*synchronized*/ void
    commit() throws JMSException {

        checkSessionState();
        checkPermissionForAsyncSend();

        //XXX:GT TBF
        if (isTransacted == false) {
            String errorString = AdministeredObject.cr.getKString(
                                     ClientResources.X_NON_TRANSACTED);
            JMSException jmse = new javax.jms.IllegalStateException(errorString,
                                               ClientResources.X_NON_TRANSACTED);
            ExceptionHandler.throwJMSException(jmse);
        }

        if (failoverOccurred) {
            rollback();

            String errorString = AdministeredObject.cr.getKString(
                ClientResources.X_TRANSACTION_FAILOVER_OCCURRED);
            JMSException jmse = new TransactionRolledBackException(errorString,
                ClientResources.X_TRANSACTION_FAILOVER_OCCURRED);

            ExceptionHandler.throwJMSException(jmse);
        }
        
        //HACC -- this is due to ack failed or runtime exception in MDB.onMessage.
        //we prevent commit by throwing the exception here (bug 6667940).
        if (isRollbackOnly) {
            if (rollbackCause instanceof Exception) {
                ExceptionHandler.handleException((Exception) rollbackCause, 
                                 ClientResources.X_CAUGHT_EXCEPTION);
            } else {
                Exception e = new Exception (rollbackCause);
                ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
            }
        }

        waitAllAsyncSendCompletion(null);

        JMSException rbrollbackex = null;
        boolean rbrollback = false ;

        // set sync flag
        setInSyncState();

        try {
            // acknowledge the current message if in the reader thread.
            receiveCommit();
            //commit all messages sent and receive
            transaction.commit();
        } catch (JMSException jmse) {
        	
            if (this.isRemoteException(jmse)) {

            //we are rolling back the transaction here.
            doRemoteFailedRollback(jmse);

            } else {

            if (!connection.isConnectedToHABroker()) { //1-phase commit
                if (((JMSException)jmse).getErrorCode().equals(
                     ClientResources.X_SERVER_ERROR)) {
                    Exception e1 = ((JMSException)jmse).getLinkedException();
                    if (e1 != null && (e1 instanceof JMSException) &&
                        (((JMSException)e1).getErrorCode().equals(
                          Status.getString(Status.GONE)) ||
                        ((JMSException)e1).getErrorCode().equals(
                          Status.getString(Status.CONFLICT)))) {
                        sessionLogger.log(Level.WARNING,
                        "Exception on commit transaction "+transaction.getTransactionID()+", will rollback", jmse);
                        rbrollbackex = jmse;
                        rbrollback = true;
                    }
                }
            }
       
            if (!rbrollback) {
                throw jmse;
            }

            }
        	     	
        } finally {
            releaseInSyncState();
        }
        if (rbrollback) {
            JMSException ex = rbrollbackex;
            try {
                rollback();
                ex = new com.sun.messaging.jms.TransactionRolledBackException(
                         rbrollbackex.getMessage(), rbrollbackex.getErrorCode());
            } catch (Exception e) {
                sessionLogger.log(Level.SEVERE,
                "Exception on rollback transaction "+transaction.getTransactionID()+" after commit failure", e);
                ex = rbrollbackex;
            }
            throw ex;
        }
    }
    
    protected void doRemoteFailedRollback(JMSException jmse) throws JMSException {
    		
        	//recreate consumers -- transaction is rolled back
        	this.recreateConsumers();
        	
        	//construct the precise exception
			String errorString = AdministeredObject.cr.getKString(
                    ClientResources.X_COMMIT_FAILED_REMOTE);
			
			JMSException newjmse = 
				new com.sun.messaging.jms.TransactionRolledBackException (errorString,
                    ClientResources.X_COMMIT_FAILED_REMOTE);
			
			//set exception link
			newjmse.setLinkedException(jmse);
            //throw the exception
			ExceptionHandler.throwJMSException(newjmse);
    }


    /** Rollback any messages done in this transaction and releases any locks
      * currently held.
      *
      * @exception JMSException if JMS implementation fails to rollback the
      *                         the transaction due to some internal error.
      * @exception IllegalStateException if method is not called by a
      *                         transacted session.
      *
      */

    public /*synchronized*/ void
    rollback() throws JMSException {

        checkSessionState();
        checkPermissionForAsyncSend();

        if ( isTransacted == false ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_NON_TRANSACTED);
            JMSException jmse =
            new javax.jms.IllegalStateException (errorString,
                                                ClientResources.X_NON_TRANSACTED );
            ExceptionHandler.throwJMSException(jmse);
        }
        
        waitAllAsyncSendCompletion(null);

        //This will make sure that session is not closed/closing.
        setInSyncState();

        try {
        	
        	//check if we have a remote exception
            if (remore_broker_failed) {
            	
            	//delete and re-register consumers -- session is also rolled back.
            	recreateConsumers();
            	//reset the flag to false so that we can proceed
            	remore_broker_failed = false;
            	
            	//we are done in rollback.  messages not committed will be re-delivered.
            	return;
            }
        	
            //request
            //1. all messages in the unAckedMessageQueue to be redelivered.
            //2. all messages in the session queue and receive queues to be
            //   redelivered.
            if ( connection.isConnectedToHABroker ) {
                rollbackHATransaction();
            } else {
                receiveRollback();
                //roll back all messages in the send queue.
                transaction.rollback();
            }

        } finally {
        	
            //HACC -- reset flag (set when received exception from mdb/ack)
            this.isRollbackOnly = false;
            this.rollbackCause = null;
      	
            //This will release sync state.
            failoverOccurred = false;

            releaseInSyncState();
        }
    }

    private void rollbackHATransaction() throws JMSException {

        try {
            receiveRollback();
            transaction.rollback();
        } catch (JMSException jmse) {
            String ecode = jmse.getErrorCode();
            if (ClientResources.X_NET_WRITE_PACKET.equals(ecode)
                || ClientResources.X_NET_ACK.equals(ecode) ) {
                this.rollbackFailed(jmse);
            } else {
                throw jmse;
            }
        }
    }

    private void rollbackFailed(JMSException jmse) throws JMSException {

        if ( connection.imqReconnect == false ) {
            throw jmse;
        }

        //connection.checkAndSetReconnecting();
        yield();

        connection.checkReconnecting(null);

        if ( connection.isCloseCalled || connection.connectionIsBroken) {
            throw jmse;
        }

        //receiveRollback();
        //transaction.rollback();

        //The transaction is rolled back by the broker.  we only have to start
        //a new transaction.

        transaction.startNewLocalTransaction();
    }

    public static void yield() {

        try {
            Thread.yield();
            Thread.sleep(3000);
        } catch (Exception e) {
            ;
        }
    }

    /**
     * Close all consumers.
     * Called from Session.close()
     *
     * @exception JMSException if close a consumer fails
     */
    //must be called from synchronized method
    private void closeConsumers() throws JMSException {
        /*Enumeration e =  consumers.elements();
        MessageConsumerImpl consumer = null;
        while ( e.hasMoreElements() ) {
            consumer = (MessageConsumerImpl) e.nextElement();
            consumer.close();
        }*/

        Collection cons = consumers.values();
        MessageConsumerImpl[] consumerArray = (MessageConsumerImpl[])
            cons.toArray ( new MessageConsumerImpl[cons.size()] );

        for ( int i=0; i< consumerArray.length; i++ ) {
            consumerArray[i].close(true);
        }

        consumers.clear();
    }

    private void closeProducers() throws JMSException {
        MessageProducerImpl[] _producers = (MessageProducerImpl[])
            producers.toArray(new MessageProducerImpl[producers.size()]);

        for (int i = 0; i < _producers.length; i++) {
            _producers[i].close();
        }

        producers.clear();
    }

    // must be called from synchronized method Session.close()
    private void closeBrowserConsumers() throws JMSException {
        //Enumeration e =  browserConsumers.elements();
        //BrowserConsumer consumer = null;
        //while (e.hasMoreElements()) {
        //    consumer = (BrowserConsumer)e.nextElement();
        //    consumer.close();
        //}

        Collection bcons = browserConsumers.values();
        BrowserConsumer[] bcArray = (BrowserConsumer[])
            bcons.toArray( new BrowserConsumer[bcons.size()] );
        for ( int i=0; i< bcArray.length; i++ ) {
            bcArray[i].close();
        }

        browserConsumers.clear();
    }

    /** Since a provider may allocate some resources on behalf of a Session
      * outside the JVM, clients should close them when they are not needed.
      * Relying on garbage collection to eventually reclaim these resources
      * may not be timely enough.
      *
      * <P>There is no need to close the producers and consumers
      * of a closed session.
      *
      * <P> This call will block until a receive or message listener
      * in progress has completed. A blocked message consumer
      * receive call returns null when this session is closed.
      *
      * <P>Closing a transacted session must rollback the in-progress
      * transaction.
      *
      * <P>This method is the only session method that can
      * be concurrently called.
      *
      * <P>Invoking any other session method on a closed session must throw
      * JMSException.IllegalStateException. Closing a closed session must
      * NOT throw an exception.
      *
      * @exception JMSException if JMS implementation fails to close a
      *                         Session due to some internal error.
      */
    public /*synchronized*/ void
    close() throws JMSException {

    	sessionLogger.log(Level.FINEST, "##### closing session.  consumer table size: " + consumers.values().size());
        //messages in the session queue.
        int reduceFlowCount = 0;

        //check if called from listener
        checkPermission();
        checkPermissionForAsyncSend();

        try {
        	
        	//This statement must be above synchronized block to avoid dead-lock
        	//if calling Session.rollback concurrently. bug ID 6390095 and 
        	//6390006
        	prepareToClose(true);
        	
            synchronized ( sessionSyncObj ) {
                try {

                //closing a closed session, just return
                if ( isClosed ) {
                    return;
                }
                //Wait for session to stop (this will block if another thread is in onMessage())
                sessionQueue.stop(true);

                //messages in the session queue
                reduceFlowCount = sessionQueue.size();

                //wait if commit/rollback/recover in process
                //set inSyncState to true
                //prepareToClose();

                if ( isTransacted ) {
                    if (xaTxnMode) {

                        //**If we are in an xaTxn then we ack all received
                        //**messages.
                        //**The real commit happens only when the
                        //**XAResource.commit() is called by the TM

                        //ack all messages received in this session
                        //message has been acked already.
                        receiveCommit();
                        //All messages (recd & sent) [commit and rollback]
                        //will be handled by XAResource
                    } else {
                        if ( connection.isBroken() == false && connection.recoverInProcess == false ) {
                            transaction.releaseBrokerResource();
                        }
                    }
                }

                ////
                //close all consumers
                closeConsumers();
                closeProducers();
                closeBrowserConsumers();
                sessionReader.close();

                serverSessionRunner.serverSessionClose();

                } finally {
                    waitAllAsyncSendCompletion(null);
                    if (asyncSendCBProcessor != null) {
                        asyncSendCBProcessor.cancel();
                    }
                }

                connection.removeSession(this);
                connection.removeFromReadQTable (sessionId);

                //XXX PROTOCOL3.5
                if (connection.getBrokerProtocolLevel() >= PacketType.VERSION350) {
                    if ( connection.isBroken() == false && connection.recoverInProcess == false) {
                        protocolHandler.deleteSession(this);
                    }
                }

                ////
                isClosed = true;
            }
     
        } finally {
        	
        	sessionLogger.log(Level.FINEST, "***** consumer table size: " + consumers.values().size());
        	
        	//unblock session queue
        	if ( isClosed == false ) {
        		this.sessionReader.close();
        	}
        	
        	//unblock consumer queues
        	if ( consumers.values().size() > 0 ) {
        		this.cleanUpConsumers();
        	}

            /**
             * When closing session failed, we still want to mark this
             * session as closed.
             */
            isClosed = true;

            serverSessionRunner.reset();
            if (connectionConsumer != null) {
                connectionConsumer.sessionClosed(this);
            }
            
            releaseInSyncState();
            //bug 6271876 -- connection flow control
            resetConnectionFlowControl (reduceFlowCount);

            if ( sessionLogger.isLoggable(Level.FINE) ) {
                logLifeCycle(ClientResources.I_SESSION_CLOSED);
            }
        }

        if ( debug ) {
            Debug.println ("session closed ...");
            Debug.println (this);
        }
    }
    
    protected void cleanUpConsumers() {
    	
    	sessionLogger.log(Level.FINEST, "Cleaning up consumers in session.  SessionID: " + this.sessionId);
    	
        Collection cons = consumers.values();
    	MessageConsumerImpl[] consumerArray = (MessageConsumerImpl[])
            cons.toArray ( new MessageConsumerImpl[cons.size()] );

    	for ( int i=0; i< consumerArray.length; i++ ) {
    		consumerArray[i].receiveQueue.close();
    		consumerArray[i].isClosed = true;
    	}
    	
    	consumers.clear();
    }

    //bug 6271876 -- connection flow control
    protected void resetConnectionFlowControl (int reduceFlowCount) {

        if ( connection.isCloseCalled ) {
            return;
        }

        if ( connection.protectMode && reduceFlowCount > 0 ) {
            readChannel.flowControl.resetFlowControl(connection, reduceFlowCount);
        }
    }

    /**
     * This method is provided for the MDB adaptor interface to close
     * the session thread.  The call is from session reader thread,
     * and no other consumers/producers in the session.
     */
    public void closeFromRA() {
        synchronized (raEndpointSyncObj) {
            sessionReader.close();
        }
    }

    /*
     * Set to true when this sesion is the one being used by an RA endpoint
     */
    public void _setRAEndpointSession() {
    	//this field is never used.
        //raEndpointSession = true;
    }

    /*
     * start a local transaction
     * called from RA when an mc is enlisted into a local txn rather than an xa txn
     */
    public void _startLocalTransaction()
    throws JMSException
    {
        if (isTransacted) {
            //ensure that transaction is non-null
            if (transaction == null) {
                throw new com.sun.messaging.jms.JMSException("MQRA:S:Can't start local transaction-transacted w/o Transaction Object");
            }
        } else {
            //ensure that transaction is null
            if (transaction != null) {
                throw new com.sun.messaging.jms.JMSException("MQRA:S:Can't start local transaction-already transacted");
            }
            transaction = new Transaction(this, true);
            isTransacted = true;
        }
    }

    /**
     * Called by close/stop methods to check if need to wait for message
     * listener to complete.
     */
    protected boolean needToWait() {
        if ( connection.isBroken() ) {
            return false;
        } else {
            return true;
        }
    }


    /** Stop message delivery in this session, and restart sending messages
      * with the oldest unacknowledged message.
      *
      * <P>All consumers deliver messages in a serial order.
      * Acknowledging a received message automatically acknowledges all
      * messages that have been delivered to the client.
      *
      * <P>Restarting a session causes it to take the following actions:
      *
      * <UL>
      *   <LI>Stop message delivery
      *   <LI>Mark all messages that might have been delivered but not
      *       acknowledged as `redelivered'
      *   <LI>Restart the delivery sequence including all unacknowledged
      *       messages that had been previously delivered.
      *
      *          <P>Redelivered messages do not have to be delivered in
      *             exactly their original delivery order.
      * </UL>
      *
      * @exception JMSException if JMS implementation fails to stop message
      *                         delivery and restart message send due to
      *                         due to some internal error.
      * @exception IllegalStateException if method is called by a
      *                         transacted session.
      */

    public void
    recover() throws JMSException {
        //boolean decremented = false;

        //Throw exception if closed
        checkSessionState();

        //transacted session is not allowed to call this method.
        if ( isTransacted ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_TRANSACTED);
            JMSException jmse =
            new javax.jms.IllegalStateException (errorString,
                                            ClientResources.X_TRANSACTED);

           ExceptionHandler.throwJMSException(jmse);
        }

        //NO_ACKNOWLEDGE mode does now allow to call this method.
        if ( acknowledgeMode == com.sun.messaging.jms.Session.NO_ACKNOWLEDGE ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_NO_ACKNOWLEDGE_RECOVER);
            JMSException jmse = new javax.jms.IllegalStateException (errorString,
                                                   ClientResources.X_NO_ACKNOWLEDGE_RECOVER);

            ExceptionHandler.throwJMSException(jmse);
        }
        
        //check if we have a remote exception
        if (remore_broker_failed) {
        	//delete and re-register consumers
        	recreateConsumers();
        	//reset the flag to false so that we can proceed
        	remore_broker_failed = false;
        	
        	//we are done in recover.  messages not ack successfully will be re-delivered.
        	return;
        }

        //no-op for non client acked session
        //bug ID 4678413 -- We now allow all ack mode to call Session.recover().
        /*if ( acknowledgeMode != Session.CLIENT_ACKNOWLEDGE ) {
            return;
        }*/

        setInSyncState();

        try {
            switch ( acknowledgeMode ) {
                case Session.AUTO_ACKNOWLEDGE:
                    /**
                     * We want to make sure that for these conditions the
                     * message does not want to be acked.
                     */
                    if ( Thread.currentThread() ==  sessionReader.sessionThread ) {
                        sessionReader.currentMessage.doAcknowledge = false;
                    }
                    else if ( Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
                        serverSessionRunner.currentMessage.doAcknowledge = false;
                    }
                    //NOTE: fall through here.
                case Session.CLIENT_ACKNOWLEDGE:
                case Session.DUPS_OK_ACKNOWLEDGE:

                    //include the current message to recover if called from message listener
                    if ( Thread.currentThread() ==  sessionReader.sessionThread ) {
                        prepareClientAcknowledge (sessionReader.currentMessage);
                    }
                    else if ( Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
                        prepareClientAcknowledge (serverSessionRunner.currentMessage);
                    }
                    break;
            }

        } catch (Exception e1) {}

        try {
            //stop message delivery from broker
            stopSession();

            /*
             * Because the caller is the session controlling thread,
             * we do not have to call stop/start methods.  We know that
             * no messages are delivering to the consumers at this moment.
             */
            //GT Have to stop *unless* it is the session thread
            //otherwise the redeliver lists will be wrong
            if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
                (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
                stop();
            }
            //request redeliver of unacked messages - set redelivered flag to true
            //GT reorder the REDELIVER protocol msgs to ensure correct order for redelivery
            //redeliverUnAckedMessages(true);
            redeliverMessagesInQueues(false);
            //request redeliver of messages in different queues but not delivered
            //to the client yet - set redelivered flag to false
            //GT reorder as explained above
            //redeliverMessagesInQueues (false);
            redeliverUnAckedMessages(true);

            failoverOccurred = false;

            //GT Have to start *unless* it is the session thread
            if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
                (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
                start();
            }
        } finally {

            //start message delivery
            releaseInSyncState();
            resumeSession();

        }
    }

    /**
     * Tell the broker to stop sending messages for this session.
     *
     * The 3.0.x brokers don't know about sessions. So in that case we
     * just stop the entire connection.
     */
    protected void stopSession() throws JMSException {
        if (connection.getBrokerProtocolLevel() < PacketType.VERSION350) {
            protocolHandler.incStoppedCount();
            protocolHandler.stop();
        }
        else {
            protocolHandler.stopSession(brokerSessionID);
        }
    }

    /**
     * Tell the broker to resume message delivery for this session.
     *
     * The 3.0.x brokers don't know about sessions. So in that case we
     * just start the connection.
     */
    protected void resumeSession() throws JMSException {
        if (connection.getBrokerProtocolLevel() < PacketType.VERSION350) {
            protocolHandler.decStoppedCount();
            //GT XXX timing hole - fix after FCS

            // SB XXX There is no known test case for this timing
            // hole.  It is theoretically possible that if a thread
            // calls connection.stop while another thread is doing
            // session.recover or rollback, there may be some
            // problems. Since raptor is capable of explicitly
            // stopping a session (during rollback / recover) this is
            // not an issue starting from 3.5 release...

            if (!connection.getIsStopped())
                protocolHandler.start();
        }
        else {
            protocolHandler.resumeSession(brokerSessionID);
        }
    }

    /**
     * Called before commit/rollback/recover ...
     */
    protected void
    setInSyncState () throws JMSException {
        synchronized ( syncObject ) {

            checkSessionState();

            if ( inSyncState ) {
            	
            	// Some other thread is performing critical operation on this session 
            	// which is illegal unless this thread is the MessageListener thread, 
                // and the other thread is calling Session.close() or Consumer.close
            	if (isIsMessageListenerThread() && 
                    (inSyncStateOperation == INSYNCSTATE_SESSION_CLOSING ||
                     inSyncStateOperation == INSYNCSTATE_CONSUMER_CLOSING)) {
            		// this is an onMessage() thread
            		// allow the close to continue in the other thread (it should be blocking until onMessage() returns)
            		// and carry on in this thread
            		return;
            	}

                if (inSyncStateOperation == INSYNCSTATE_CONSUMER_CLOSING) {
                    long totalwaited = 0L;
                    long waittime = waitTimeoutForConsumerCloseDone;
                    while (inSyncState &&
                           inSyncStateOperation == INSYNCSTATE_CONSUMER_CLOSING &&
                           (waittime > 0L || waitTimeoutForConsumerCloseDone == 0L)) {
                        checkSessionState();
                        long starttime = System.currentTimeMillis();
                        try {
                            syncObject.wait(waittime);
                        } catch ( InterruptedException e ) {
                            Debug.printStackTrace(e);
                        }
                        totalwaited += (System.currentTimeMillis() - starttime);
                        waittime = waitTimeoutForConsumerCloseDone - totalwaited;
                        if (waittime < 0L) {
                            waittime = 0L;
                        }
                    }
                    if (!inSyncState) {
                        inSyncState = true;
                        inSyncStateOperation = INSYNCSTATE_OTHER;
                        return;
                    }
                }
            	            	
                String errorString = AdministeredObject.cr.getKString(ClientResources.X_CONFLICT);
                JMSException jmse =
                new javax.jms.IllegalStateException(errorString, ClientResources.X_CONFLICT);

                ExceptionHandler.throwJMSException(jmse);
            }

            inSyncState = true;
            inSyncStateOperation = INSYNCSTATE_OTHER;
        }
    }

    /**
     * Called after commit/rollback/recover
     */
    protected void
    releaseInSyncState() {
        synchronized ( syncObject ) {
            inSyncState = false;
            // unset the reason why we're in inSyncState
            inSyncStateOperation=INSYNCSTATE_NOTSET;
            
            syncObject.notifyAll();
        }
    }

    /**
     * Get inSync status.
     */
    protected boolean getInSyncState() {
        return inSyncState;
    }

    /**
     * This method set the inSyncState flag to true.
     * It waits until commit/rollback/recover returns.
     *
     *<p>Commit/rollback/recover throws JMSException if they
     * are called after close is called.
     */
    protected void prepareToClose(boolean fromSessionClose) {

        synchronized ( syncObject ) {
            //if inSync state, wait until done
            while ( inSyncState ) {
                try {
                    syncObject.wait();
                } catch ( InterruptedException e ) {
                    Debug.printStackTrace(e);
                }
            }

            //set to true so that commit/rollback/recover
            //will throw exception ...
            inSyncState = true;
            
            // record why we've set inSyncState
            if (fromSessionClose) {
                inSyncStateOperation = INSYNCSTATE_SESSION_CLOSING;
            } else {
                inSyncStateOperation = INSYNCSTATE_CONSUMER_CLOSING;
            }
        }
    }

    /**
     * For transacted session, acknowledge all unacked messages.  If
     * Session.commit() is called from MessageListener, the current
     * message is not acknowledged.
     *
     * NOTE: The implementation has been changed. The new implementation
     * only need to acknowledge the current message if commit() is
     * called from the reader thread.
     */
    protected void receiveCommit() throws JMSException {
        //include the current message to recover if called from message listener

        if ( Thread.currentThread() ==  sessionReader.sessionThread ) {
            transactedAcknowledge (sessionReader.currentMessage);
        }
        else if (Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
            transactedAcknowledge (serverSessionRunner.currentMessage);
        }

        //clean up unacked Q
        //bug 6423696 - Session.rollback does not actually ...
        //The fix is to clear the unAckedMessageQueue only after
        //commit has successfully returned.
        //The action to clear the Q is moved to Transaction.java
        //unAckedMessageQueue.clear();

        /*if ( Thread.currentThread() ==  sessionReader.sessionThread ) {
            prepareTransactedAcknowledge (sessionReader.currentMessage);
        }
        else if (Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
            prepareTransactedAcknowledge (serverSessionRunner.currentMessage);
        }
        //only need to send ack if there are messages in the un acked queue.
        if ( unAckedMessageQueue.size() > 0 ) {
            dequeue ( unAckedMessageQueue );
            //set transaction ID
            ackPkt.setTransactionID( transaction.getTransactionID() );
            //do acknowledge, require broker to ack back.
            doAcknowledge(true);
        }*/
    }
    
    /**
     * This method is called after ProtocolHandler.commit() return
     * a OK status.
     *
     */
    protected void clearUnackedMessageQ() {
        this.unAckedMessageQueue.clear();
    }

    /**
     * Called by rollback() in a transacted session. This method handles
     * rollback for consuming messages
     *
     * <p>All messages in the unAckedMessageQueue, receive queues, and session
     *    queue will be redelivered.
     */
    protected void receiveRollback() throws JMSException {
         //XXX REVISIT chiaming: NO transaction ID involved???

        //include the current message to recover if called from message listener
        if ( Thread.currentThread() ==  sessionReader.sessionThread ) {
            prepareTransactedAcknowledge (sessionReader.currentMessage);
        }
        else if (Thread.currentThread() ==  serverSessionRunner.getCurrentThread()) {
            prepareTransactedAcknowledge (serverSessionRunner.currentMessage);
        }

        //stop message delivery from broker
        stopSession();

        //stop this session, all message delivery in the session is stopped.
        //DO NOT need to stop this session because this is the controlling
        //thread calling this method.
        //GT Have to stop *unless* it is the session thread
        //otherwise the redeliver lists will be wrong
        if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
            (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
            stop();
        }

        //put MID from unacked message queue to dos
        //dequeueUnAckedMessages();

        //put MID from all consumer queues and session queue to dos
        //dequeueMessagesInQueues();

        //for bug id 5018703 - we must send two separate pkt with
        //the right redeliver flag.  This is similar to recover().
        this.redeliverMessagesInQueues(false);
        this.redeliverUnAckedMessages(true);

        //redeliver and set redeliver flag to true
        //redeliver (true);

        //start current session again
        //GT Have to start *unless* it is the session thread
        if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
            (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
            start();
        }
        //start message delivery
        resumeSession();
    }


    /**
     * Caller must in setInSyncState block
     *
     * This method is used to rollback a transaction that failed to commit
     * and broker indicates client runtime should rollback it
     */
    protected void rollbackAfterReceiveCommit(JMQXid jmqXid) throws JMSException {

        checkSessionState();

        //stop message delivery from broker
        stopSession();
        boolean stoppedbyme = false;
        try {

            //Have to stop *unless* it is the session thread
            //otherwise the redeliver lists will be wrong
            if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
                (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
                stop();
                stoppedbyme = true;
            }
            this.redeliverMessagesInQueues(false);

            if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
                (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
                start();
                stoppedbyme = false;
            }

            connection.getProtocolHandler().rollback(0L, jmqXid, true);
        } finally {
            try {
                if (stoppedbyme) {
                    start();
                }
            } finally {
                resumeSession();
            }
        }
    }

    /**
     * Called by recover().  Redeliver messages in the unacked message queue.
     *
     * @param redeliverFlag the falg that broker will set to the messages when
     *                      redeliver.
     */
    protected void
    redeliverUnAckedMessages (boolean redeliverFlag) throws JMSException {
        dequeueUnAckedMessages();
        //commit redeliver
        redeliver( redeliverFlag );
    }

    /**
     * Redeliver messages in the consumer's receive queues and session queue
     */
    protected void
    redeliverMessagesInQueues (boolean redeliverFlag) throws JMSException {
        dequeueMessagesInQueues();
        //commit redeliver
        redeliver (redeliverFlag);
    }

    /**
     * Dequeue unacked messages from unAckedMessageQueue and write
     * the message IDs to the data output stream.
     *
     * <p>Reset the ack counter to 0 since the queue is empty.
     */
    //protected void dequeueUnAckedMessages() throws JMSException {
        //dequeue unacked message queue
    //    dequeue (unAckedMessageQueue);
        //reset ack counter
    //    ackCounter = 0;
    //}

    /**
     * Loop through each MessageConsumer and dequeue its receiveQueue
     * in this session.  Message Ids are written to the data output
     * stream.
     *
     * <p>Dequeue the SessionQueue after the above action.
     */
    protected void dequeueMessagesInQueues() throws JMSException {
        MessageConsumerImpl consumer = null;
        Enumeration enum2 = consumers.elements();

        int reduceFlowCount = 0;

        while (enum2.hasMoreElements()) {
            consumer = (MessageConsumerImpl) enum2.nextElement();

            // add messages in the consumer receive queue
            reduceFlowCount = reduceFlowCount + consumer.receiveQueue.size();

            //dequeue receive queue for each consumer in this session
            dequeueReceiveQ (consumer.receiveQueue);

            //XXX PROTOCOL 3.5
            // Reset the flow control counters for all the consumers
            // for this session.
            readChannel.flowControl.resetFlowControl(consumer, 0);


        }

        //add messages in the session queue
        reduceFlowCount = reduceFlowCount + sessionQueue.size();

        //dequeue the currect session's session queue
        dequeueSessionQ (sessionQueue);

        //bug 6271876 -- connection flow control
        resetConnectionFlowControl (reduceFlowCount);

       //XXX REVISIT  "dequeue" from serverSessionMessageQ ?
    }

    /**
     * Loop through each element and write MID to dos.  elements are deleted
     * after used.
     *
     * @param queue the SessionQueue to be dequeued
     */
    protected void dequeueReceiveQ (ReceiveQueue queue) throws JMSException {
        MessageImpl message = null;
        //GT
        //System.out.println(">>>dequeing..ReceiveQ dump");
        while (queue.isEmpty() == false) {
            if ((message = (MessageImpl) queue.dequeue()) != null) {
                writeMessageID ( message );
                //GT
                //message.dump(System.out);
            }
        }
        //GT
        //System.out.println(">>>dequeing..ReceiveQ dump done");
        //System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    protected void dequeueSessionQ (SessionQueue queue) throws JMSException {
        ReadOnlyPacket pkt = null;
        //GT
        //System.out.println(">>>dequeing..SessionQ dump");
        while (queue.isEmpty() == false) {
            if ((pkt = (ReadOnlyPacket) queue.dequeue()) != null) {
                writeMessageID ( pkt );
                //GT
                //pkt.dump(System.out);
            }
        }
        //GT
        //System.out.println(">>>dequeing..SessionQ dump done");
        //System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    /**
     * Loop through each element and write MID to dos.  elements are deleted
     * after used.
     *
     * @param queue the Vector to be dequeued
     */
    private void dequeueUnAckedMessages () throws JMSException {
        //bug 4934856
        UnAckedMessage unAckedMessage = null;
        int size = unAckedMessageQueue.size();

        //GT
        //System.out.println(">>>dequeing..dequeue dump");
        for ( int i=0; i< size; i++ ) {
            unAckedMessage = (UnAckedMessage) unAckedMessageQueue.elementAt(i);
            writeMessageID ( unAckedMessage );
            //GT
            //unAckedMessage.dump(System.out);
        }
        //GT
        //System.out.println(">>>dequeing..dequeue dump done");

        unAckedMessageQueue.removeAllElements();

        this.ackCounter = 0;
    }

/**
    public void _redeliverMessageFromRA(MessageImpl message) throws JMSException {
        synchronized (raEndpointSyncObj) {

            if (message != null) {
                writeMessageID(message);

                ReadWritePacket pkt = new ReadWritePacket();
                try {
                    dos.flush();
                    bos.flush();

                    //set message body
                    pkt.setMessageBody(bos.toByteArray());

                    //for transacted session, set transaction ID to pkt
                    ////if ( isTransacted ) {
                        ////pkt.setTransactionID( transaction.getTransactionID() );
                    ////}

                    //write packet - ensure that this message is marked 'redelivered'
                    protocolHandler.redeliver (pkt, true, isTransacted);
                    //reset buf count to 0
                    bos.reset();

                } catch (IOException e) {
                    ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_REDELIVER);
                }
            }
        }
    }
**/

    /**
     * Redeliver all messages in the current DataOutputStream.
     *
     * @param redeliverFlag the flag for broker to set for redelivered
     *                      messages.
     */
    protected void redeliver (boolean redeliverFlag) throws JMSException {

        //do not request redeliver if received no messages
        if ( bos.size() == 0 ) {
            return;
        }

        ReadWritePacket pkt = new ReadWritePacket();
        try {
            dos.flush();
            bos.flush();
            //set message body
            pkt.setMessageBody( bos.toByteArray() );

            //for transacted session, set transaction ID to pkt
            if ( isTransacted ) {
                pkt.setTransactionID( transaction.getTransactionID() );
            }

            //write packet
            protocolHandler.redeliver (pkt, redeliverFlag, isTransacted);
            //reset buf count to 0
            bos.reset();

        } catch (IOException e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_REDELIVER);
        }
    }

    /** Return the session's distinguished message listener (optional).
      *
      * @return the message listener associated with this session.
      *
      * @exception JMSException if JMS fails to get the message listener
      *                         due to an internal error in JMS Provider.
      *
      * @see javax.jms.Session#setMessageListener
      * @see javax.jms.ServerSessionPool
      * @see javax.jms.ServerSession
      */

    public MessageListener
    getMessageListener() throws JMSException {
        checkSessionState();
        return serverSessionRunner.getMessageListener();
    }


    /** Set the session's distinguished message listener (optional).
      * When it is set no other form of message receipt in the session can
      * be used; however, all forms of sending messages are still supported.
      * This is an expert facility not used by regular JMS clients.
      *
      * @param listener the message listener to associate with this session.
      *
      * @exception JMSException if JMS fails to set the message listener
      *                         due to an internal error in JMS Provider.
      *
      * @see javax.jms.Session#getMessageListener
      * @see javax.jms.ServerSessionPool
      * @see javax.jms.ServerSession
      */

    public /*synchronized*/ void
    setMessageListener(MessageListener listener) throws JMSException {

        checkSessionState();

        if (listener != null && consumers.size() > 0) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_SVRSESSION_MESSAGECONSUMER);
            JMSException jmse =
            new javax.jms.IllegalStateException (errorString, ClientResources.X_SVRSESSION_MESSAGECONSUMER);

            ExceptionHandler.throwJMSException(jmse);
        }
        serverSessionRunner.setMessageListener(listener);
    }

    /**
     * Only intended to be used by Application Servers (optional operation).
     *
     * @see javax.jms.ServerSession
     */
    public void run() {

    	try {
    		serverSessionRunner.run();    
    	} finally {
    		//clean up?
    		;
    	}
    }

    protected void loadMessageToServerSession(MessageImpl message,
                                              ServerSession ss, 
                                              boolean isDMQMessage) {
        serverSessionRunner.loadMessage(message, ss, isDMQMessage);
    }

    protected SessionQueue getSessionQueue() {
        return sessionQueue;
    }

    /**
     * Non-public API.  This method should be called right after ack is
     * returned.
     *
     * @return ack flag for acknowledgement.  If true, ack waits for broker's
     * acknowledgement.  Otherwise, acknowledge returns without waiting for
     * broker's acknowledgement.
     */
    public boolean _getAckSendAcknowledge() {
        return ackPkt.getSendAcknowledge();
    }

    /**
     * Get acknowledge mode
     */
    public int _getAcknowledgeMode() {
        return acknowledgeMode;
    }

    protected boolean _getXaTxnMode() {
        return xaTxnMode;
    }
    protected void _setXaTxnMode(boolean mode) {
        xaTxnMode = mode;
    }

    public long getBrokerSessionID() {
        return brokerSessionID;
    }

    public void setBrokerSessionID(long brokerSessionID) {
        this.brokerSessionID = brokerSessionID;
    }

    public Transaction _getTransaction()
    {
        return transaction;
    }

    public void setFailoverOccurred(boolean v) {
        failoverOccurred = v;
    }

    public void initXATransactionForMC(long transactionID)
    throws JMSException
    {
        if (transaction == null) {
            transaction = new Transaction(this, false);
        }
        transaction.setTransactionID(transactionID);
        xaTxnMode = true;
        isTransacted = true;
    }

    public void finishXATransactionForMC()
    {
        //No longer needs to be in a dist txn
        xaTxnMode = false;
        //Done with Transaction delegate!
        isTransacted = false;
        transaction = null;
    }

    public void dump(PrintStream ps) {

        ps.println ("------ SessionImpl dump ------");
        ps.println ("broker session ID: " + brokerSessionID);
        ps.println ("session ID: " + sessionId);

        //dump session reader status
        if ( sessionReader != null ) {
            sessionReader.dump(ps);
        }
        //dump session queue status
        if ( sessionQueue != null ) {
            sessionQueue.dump (ps);
        }
        //dump unAckedMessageQueue
        if ( unAckedMessageQueue != null ) {
            ps.println ("Number of Unacked messages: " + unAckedMessageQueue.size());
        }

        ps.println ("# of message consumers: " + consumers.size());
        //dump message consumers status
        Enumeration enum2 = consumers.elements();
        while ( enum2.hasMoreElements() ) {
            MessageConsumerImpl consumer = (MessageConsumerImpl) enum2.nextElement();
            consumer.dump (ps);
        }

        serverSessionRunner.dump(ps);
    }

    protected Hashtable getDebugState(boolean verbose) {
        Hashtable ht = new Hashtable();

        ht.put("sessionId", String.valueOf(sessionId));
        ht.put("brokerSessionID", String.valueOf(brokerSessionID));
        ht.put("isTransacted", String.valueOf(isTransacted));
        ht.put("ackMode", String.valueOf(acknowledgeMode));
        ht.put("dupsOkLimit", String.valueOf(dupsOkLimit));
        ht.put("isAckLimited", String.valueOf(isAckLimited));
        ht.put("ackLimit", String.valueOf(ackLimit));
        ht.put("ackCounter", String.valueOf(ackCounter));
        ht.put("xaTxnMode", String.valueOf(xaTxnMode));
        ht.put("rxCount", String.valueOf(TEST_rxCount));
        ht.put("ackCount", String.valueOf(TEST_ackCount));
        ht.put("isStopped", String.valueOf(isStopped));

        ht.put("# Consumers", String.valueOf(consumers.size()));
        int n = 0;
        Enumeration enum2 = consumers.elements();
        while (enum2.hasMoreElements()) {
            MessageConsumerImpl consumer = (MessageConsumerImpl)
                enum2.nextElement();
            ht.put("Consumer[" + n + "]", consumer.getDebugState(verbose));
            n++;
        }

        ht.put("# Producers", String.valueOf(producers.size()));
        MessageProducerImpl[] _producers = (MessageProducerImpl[])
            producers.toArray(new MessageProducerImpl[producers.size()]);
        for (int i = 0; i < _producers.length; i++) {
            ht.put("Producer[" + i + "]",
                _producers[i].getDebugState(verbose));
        }
        ht.put("unAckedMessageQueueSize", unAckedMessageQueue.size());
        if (verbose) {
            ht.put("unAckedMessageQueue", unAckedMessageQueue);
        }
        SessionQueue ssq = sessionQueue;
        if (ssq != null) {
            ht.put("sessionQueue", ssq.getDebugState(verbose));
        }
        ConnectionConsumerImpl cc = connectionConsumer;
        if (cc != null) {
            ht.put("connectionConsumer", cc.getDebugState(verbose));
        }
        ServerSessionRunner ssr = serverSessionRunner;
        if (ssr != null) {
            ht.put("serverSessionRunner", ssr.getDebugState(verbose));
        }

        return ht;
    }

    /**
     * backward compatibility -- 4934856
     * @param message the message used to call client ack method.
     * @throws JMSException
     */
    private void
    checkClientAckMessage (MessageImpl message) throws JMSException {

        if (connection.getBrokerProtocolLevel() <
            com.sun.messaging.jmq.io.PacketType.VERSION350) {

            Long id = Long.valueOf(message.getInterestID());

            if ( consumers.containsKey ( id ) == false ) {
            	// "Cannot acknowledge message for closed consumer"
                String errorString = AdministeredObject.cr.getKString(ClientResources.X_CLIENT_ACKNOWLEDGE);
                JMSException jmse =
                new javax.jms.IllegalStateException (errorString, ClientResources.X_CLIENT_ACKNOWLEDGE);

                ExceptionHandler.throwJMSException(jmse);
            }
        }
    }

     /**
     * When message consumer is closed, we need to remove unacked
     * messages for transacted/clientAck session. -- 4934856
     */
    protected void removeUnAckedMessages(Long interestId) throws JMSException {
        int size = unAckedMessageQueue.size();
        if ( size > 0 ) {  //there are messages in the unacked q.

            Vector removeq = new Vector();
            //interest id for this consumer
            //XXX PROTOCOL2.1
            long consumerID = interestId.longValue();

            //find unacked messages for this consumer and put
            //them in the removeq
            //this is not synchronized because no other thread is
            //modifying the unackq.
            for ( int i=0; i<size; i++ ) {
                UnAckedMessage msg = (UnAckedMessage) (unAckedMessageQueue.elementAt(i));
                if ( msg.getConsumerID() == consumerID ) {
                    //This message can not be used to do acknowledge
                    //for client ack mode.
                    //msg.doAcknowledge = false;
                    removeq.addElement(msg);
                }
            }

            //remove messages in the rollback queue
            for ( int i=0; i<removeq.size(); i++) {
                //System.out.println("********* removing msg from unackq: "+ i);
                if (debug) {
                Debug.println("removing msg from unackq: "+removeq.elementAt(i));
                }
                removeMessageFromAckList( (UnAckedMessage)removeq.elementAt(i) );
            }
        }
    }

    public void logLifeCycle (String key) {

        if ( sessionLogger.isLoggable(Level.FINE) ) {
            sessionLogger.log(Level.FINE, key, this);
        }
    }

    public String toString() {
        return "ConnectionID=" + this.connection.getConnectionID() +
               ", SessionID=" + this.brokerSessionID;
    }



    /**
     * Light weight class to hold Unacked message data.
     * This is used to recover or rollback messages.
     */
    private static class UnAckedMessage {

        private SysMessageID mid = null;
        private long cid = -1;

        private UnAckedMessage (MessageImpl message) {
            this.mid = message.getMessageID();
            this.cid = message.getInterestID();
        }

        public SysMessageID getMessageID() {
            return mid;
        }

        public long getConsumerID() {
            return cid;
        }

        public String toString() {
            return "mid:["+((mid!=null)?mid.toString():"")+"] cid:"+cid;
        }
    }
    
    /**
     * The method is called when client runtime detects that the 
     * remote broker had failed and any of the following conditions
     * is true.
     * 
     * 1. if ((ack_mode == transacted) and (Session.commit())) is called. 
     *     
     *     Broker sets status code (in commit_reply/prepare_reply) to 
     *     inform client runtime that recreate consumer is required.
     *     
     *     1.1 Client runtime calls this method to delete and add consumers 
     *     in the session.
     *     1.2 The transaction is rolled back.  A transaction rolled back
     *     exception is thrown.  A new transaction is created.
     *     
     * 2. if ((ack_mode == transacted) and (Session.rollback())) is called. 
     *     
     *     If the client runtime has a state indicating that the remote broker had failed:
     *     
     *     2.1 Client runtime calls this method to delete and add consumers 
     *     in the session.
     *     2.2 The transaction is rolled back.  A new transaction is created.
     *  
     * 3. if ((ack_mode == client_ack) and (Session.recover())) is called. 
     * 
     *     If the client runtime has a state indicating that the remote broker had failed:
     *     
     *     3.1 Client runtime calls this method to delete and add consumers 
     *     in the session.
     *     3.2 The session is recovered.  Messages ack failed will be redelivered.
     *     
     * 4. If ((ack_mode == auto_ack) || (ack_mode == dups_ok_ack))
     * 
     * 	If the client runtime has a state indicating that the remote broker had failed:
     * 
     *     4.1 Client runtime calls this method to delete and add consumers 
     *     in the session.
     *     4.2 Messages not acknowledged (includes ack failed) are redelivered.
     */
    protected void recreateConsumers() throws JMSException {
        recreateConsumers(false);
    }
    protected void recreateConsumers(boolean fromXAResourceImpl) throws JMSException {
    
    	try {
    		
    		sessionLogger.finest("Re-creating consumers for the session: " + this.sessionId);
    		
    		//the current consumers in the session is saved to recreate after delete them.
    		Object savedConsumers[] = 
    			        (Object[]) consumers.values().toArray();
    		
    		//1. stop message delivery for the session.
    		//this is the active session thread to reconstruct the state of the consumers. 
    		stopSession();
    		
    		//2. stop session reader thread.
    		if ((Thread.currentThread() != sessionReader.sessionThread) &&
				    (Thread.currentThread() != serverSessionRunner.getCurrentThread())) {
				//we must not wait for app thread to be locked.  We simply block (mark a flag in
    			//delivery Qs only) all message delivery.
    			stop(false);
			}
    		
    		// 3. clean up messages in session queue
    		sessionQueue.clear();
    		
            //4. Clear Consumer queue, remove interest
            for (int i=0; i < savedConsumers.length; i++) {
            	//clear consumer queue.
            	((MessageConsumerImpl)savedConsumers[i]).receiveQueue.clear();
            	
            	//remove interest to broker
            	((MessageConsumerImpl)savedConsumers[i]).deregisterInterest();
            	
            	//log
            	((MessageConsumerImpl)savedConsumers[i]).logLifeCycle(ClientResources.I_CONSUMER_CLOSED);
            }
            
            //4. Clear unacked message queue
            if ( unAckedMessageQueue != null ) {
                unAckedMessageQueue.removeAllElements();
            }

            //5. clean up the consumers table in the session
            consumers.clear();
            
            //6. send roll back packet if transacted session
            if (!fromXAResourceImpl && this.isTransacted) {
            	transaction.rollback();
            }
            
            //7. recreate consumers.
            for (int i=0; i < savedConsumers.length; i++) {
            	
            	((MessageConsumerImpl)savedConsumers[i]).registerInterest();
            	
            	((MessageConsumerImpl)savedConsumers[i]).logLifeCycle(ClientResources.I_CONSUMER_CREATED);
            }
            
            //8. resume session
            resumeSession();
            
            //9. restart session thread if stopped
            if ((Thread.currentThread() !=  sessionReader.sessionThread) &&
                    (Thread.currentThread() !=  serverSessionRunner.getCurrentThread())) {
                    start();
            }
    		
    		//10. reset falg
    		remore_broker_failed = false;
    		
    		sessionLogger.finest("Consumers recreated for the session: " + this.sessionId);
    		
    	} catch (JMSException jmse) {
    		sessionLogger.log(Level.SEVERE, jmse.getMessage(), jmse);
    		throw jmse;
    	} finally {
    		//do nothing now.
    	}
    }

    public boolean _appCheckRemoteException(JMSException jmse) {
        if (jmse instanceof RemoteAcknowledgeException) {
            return isRemoteException(jmse);
        }
        Exception e = jmse.getLinkedException();
        if (e == null || !(e instanceof RemoteAcknowledgeException)) {
            return false;
        }
        return isRemoteException((JMSException)e);
    }
    
    /**
     * Check if this is a remote ack/commit failed exception
     * 
     * Re-create consumers in the session if it is caused by remore broker failure.
     * 
     * @param jmse 
     */
    protected boolean isRemoteException (JMSException jmse) {
        boolean isRemoteFailed = false;
        String errcode = jmse.getErrorCode();
    	
        if (ClientResources.X_ACK_FAILED_REMOTE.equals(errcode) ||
            ClientResources.X_AUTO_ACK_FAILED_REMOTE.equals(errcode) ||
            ClientResources.X_CLIENT_ACK_FAILED_REMOTE.equals(errcode) ||
            ClientResources.X_COMMIT_FAILED_REMOTE.equals(errcode)) {
    		
            if (this.matchConsumerIDs((RemoteAcknowledgeException)jmse, consumers, sessionLogger)) {
                isRemoteFailed = true;
            }
        }
        return isRemoteFailed;
    }
    
    /**
     * Match the consumer IDs contained in the specified RemoteAcknowledgeException with
     * the consumer IDs in the session consumers table.
     * 
     * @param rae
     * @return true if the consumers table contains a consumer with cid in the 
     * RemoteAcknowledgeException.JMQRemoteConsumerIDs property.
     */
    protected static boolean matchConsumerIDs (RemoteAcknowledgeException rae, Hashtable cons, Logger logger) {
    	
        Hashtable prop = rae.getProperties();
        if (prop == null) {
            return false;
        }
        String cvalue = (String) prop.get(RemoteAcknowledgeException.JMQRemoteConsumerIDs);
        if (cvalue == null) {
            return false;
        }
		
        StringTokenizer st = new StringTokenizer (cvalue);

        while (st.hasMoreTokens()) {
            String cidstr = st.nextToken();
            Long cid = new Long (cidstr);
            Object obj = cons.get(cid);
            if (obj != null)  {
                logger.finest("SessionImpl.matchConsumerIDs: Consumer ID matches: " + cidstr);
                return true;
            }
        }
        return false;
    }

    public void setIsMessageListenerThread(boolean bool) {
        isMessageListener.set(Boolean.valueOf(bool));
    }

    public boolean isIsMessageListenerThread() {
        Boolean value = isMessageListener.get();
        if (value==null){
            return false;
        } else {
            return value.booleanValue();
        }
    }
    
}
