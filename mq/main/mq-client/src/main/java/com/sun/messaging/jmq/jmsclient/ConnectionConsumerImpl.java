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
 * @(#)ConnectionConsumerImpl.java	1.19 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.jms.*;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;

/** For application servers, Connections provide a special facility for
  * creating a ConnectionConsumer. The messages it is to consume is
  * specified by a Destination and a Property Selector. In addition, a
  * ConnectionConsumer must be given a ServerSessionPool to use for
  * processing its messages.
  *
  * <P>Normally, when traffic is light, a ConnectionConsumer gets a
  * ServerSession from its pool; loads it with a single message; and,
  * starts it. As traffic picks up, messages can back up. If this happens,
  * a ConnectionConsumer can load each ServerSession with more than one
  * message. This reduces the thread context switches and minimizes resource
  * use at the expense of some serialization of a message processing.
  *
  * @see javax.jms.QueueConnection#createConnectionConsumer
  * @see javax.jms.TopicConnection#createConnectionConsumer
  * @see javax.jms.TopicConnection#createDurableConnectionConsumer
  */
public class ConnectionConsumerImpl extends Consumer
                implements ConnectionConsumer, Traceable {

    protected ServerSessionPool serverSessionPool;
    protected int maxMessages;

    private SessionQueue readQueue = null;
    private ConnectionConsumerReader reader = null;
    private Long readQueueId = null;

    private ServerSession serverSession = null;
    private SessionImpl session = null;

    private Object closeLock = new Object();
    private boolean failoverInProgress = false;

    private Object recreationLock = new Object();
    private boolean recreationInProgress1 = false; //close wait this
    private boolean recreationInProgress2 = false; //failover wait this

    private Long interestIdToBeRecreated = null;
    private List seenSessions = Collections.synchronizedList(new ArrayList());

    public ConnectionConsumerImpl(ConnectionImpl connection, Destination d,
           String messageSelector, ServerSessionPool sessionPool,
           int maxMessages, String subscriptionName, boolean durable, boolean share)
           throws JMSException {

        super(connection, d, messageSelector, false);
        if (durable) {
            if (!share && connection.clientID == null) {
                String errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_CLIENT_ID, "\"\"");
                throw new JMSException (errorString, AdministeredObject.cr.X_INVALID_CLIENT_ID);
            }

            setDurable(true);
            setDurableName (subscriptionName);
        }
        if (share) {
            setShared(true);
            if (!durable) {
                setSharedSubscriptionName(subscriptionName);
            }
        }
        if (durable || share) {
            if (connection.clientID != null) {
                if (connection.getProtocolHandler().isClientIDsent() == false) {
                    connection.getProtocolHandler().setClientID(connection.clientID);
                }
            }
        }
        
        this.serverSessionPool = sessionPool;
        this.maxMessages = maxMessages;
        init();
    }

    public void init() throws JMSException {
        readQueue = new SessionQueue();

        if (connection.getIsStopped()) {
            readQueue.setIsLocked(true);
        }

        readQueueId = connection.getNextSessionId();
        connection.addToReadQTable(readQueueId, readQueue);

        reader = new ConnectionConsumerReader(this);
        reader.start();
        addInterest();
    }

    /**
     * add this consumer's interest
     *
     * @exception JMSException if fails to register the interest to broker
     */
    private void addInterest() throws JMSException {
        connection.addConnectionConsumer(this);
        registerInterest();
    }

    /**
     * remove this consumer's interest
     *
     * @param destroy if true deregister interest from broker
     *                if false do not deregister from broker (for durable)
     *
     * @exception JMSException if fails to deregister from broker
     */
    private void removeInterest() throws JMSException {
        connection.removeConnectionConsumer(this);
        deregisterInterest();
    }

    /**
     * get the reader's Id for this consumer
     *
     * @return the reader Id
     */
    protected Long getReadQueueId() {
        return readQueueId;
    }

    /**
     * get the read queue for this connection consumer
     *
     * @return the associated read queue
     */
    protected SessionQueue getReadQueue() {
        return readQueue;
    }

    protected boolean canRecreate() {
        if (destination instanceof Queue) return true;
        if (getDurableName() != null) return true;
        return false;
    }

    protected void notifyRecreation(RemoteAcknowledgeException rex) {
        Long cid = getInterestId();
        Hashtable ht = new Hashtable();
        ht.put(cid, this);
        if (SessionImpl.matchConsumerIDs(rex, ht, connection.connectionLogger)) {
            synchronized(recreationLock) {
                if (interestIdToBeRecreated == null ||
                    !interestIdToBeRecreated.equals(cid)) {
                    if (!getInterestId().equals(cid)) return;
                    interestIdToBeRecreated = cid;
                    connection.connectionLogger.log(Level.FINE, "Notified ConnectionConsumer["+cid+"] to be recreated");
                    if (readQueue.isEmpty()) {
                        readQueue.enqueueNotify(null);
                    }
                }
            }
        }
    }

    /*
     * @return true if recreated
     */
    private boolean recreateIfNecessary() throws JMSException {
        if (!canRecreate()) return false;

        Long recid = null;
        boolean recreated = false;

        synchronized(closeLock) {
            checkState();
            if (failoverInProgress) {
                return false;
            }
            synchronized(recreationLock) {
                recid = interestIdToBeRecreated;
                setRecreationInProgress1(true);
            }
        }
        Logger logger = connection.connectionLogger;

        boolean sessionstopped = false;
        try {

            Long cid = getInterestId();

            if (recid != null && recid.equals(cid)) {

                logger.log(Level.INFO, 
                         "Recreate ConnectionConsumer["+cid+"] ...");


                ProtocolHandler ph = connection.getProtocolHandler();
                try {
                    ph.stopSession(connection.getConnectionID().longValue());
                    logger.log(Level.FINE, 
                           "Stopped ConnectionConsumer["+cid+"]'s session "+cid);
                    sessionstopped = true;
                } catch (Throwable t) {
                    String emsg = "Exception in stopping ConnectionConsumer["+cid+"]'s session";
                    logger.log(Level.SEVERE, emsg, t);
                    if (t instanceof JMSException) throw (JMSException)t;
                    JMSException jmse = new JMSException(emsg+": "+t.getMessage());
                    jmse.initCause(t);
                    throw jmse;
                }

                synchronized(closeLock) {
                    checkState();
                    if (failoverInProgress) {
                        return false;
                    }
                    synchronized(recreationLock) {
                        setRecreationInProgress2(true);
                    }
                }
                

                logger.log(Level.INFO,
                       "Reset ConnectionConsumer["+cid+"]'s ServerSessions "+seenSessions.size());

                SessionImpl[] sss = null;
                synchronized(seenSessions) {
                    sss = (SessionImpl[])seenSessions.toArray(
                                  new SessionImpl[seenSessions.size()]);
                }
                SessionImpl ss = null;
                for (int i = 0; i < sss.length; i++) {
                    ss = (SessionImpl)sss[i];
                    logger.log(Level.FINE,
                           "Reseting ConnectionConsumer["+cid+"]'s ServerSession's session "+ss);

                    ss.resetServerSessionRunner(false);

                    logger.log(Level.FINE,
                           "Reseted ConnectionConsumer["+cid+"]'s ServerSession's session "+ss);
                }

                int xarcnt = 0;
                long waittime = 0;
                while ((xarcnt=XAResourceMap.hasXAResourceForCC(this)) > 0 &&
                       !failoverInProgress && !isClosed) {

                    if (waittime%15000 == 0) {   
                        waittime = 0;
                        logger.log(Level.INFO, 
                        "Waiting for all active XAResources "+xarcnt+" before recreate ConnectionConsumer["+cid+"] ...");
                    }
                    synchronized(closeLock) {

                    try {
                        closeLock.wait(1000);
                        waittime += 1000;
                    } catch (InterruptedException e) {}
                    }
                }

                try {
                    deregisterInterest();
                } catch (Throwable t) {
                    Level loglevel = Level.SEVERE;
                    if (connection.getRecoverInProcess()) { 
                        loglevel = Level.WARNING;
                    } 
                    logger.log(loglevel, 
                           "Exception on deregister interest to recreate ConnectionConsumer["+cid+"]");
                    return false;
                }

                readQueue.clear();
                do {
                    try {
                        registerInterest();
                        logger.log(Level.INFO, 
                               "Recreated ConnectionConsumer["+cid+"]: "+getInterestId());
                        recreated = true;
                        break;
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, 
                               "Exception on register interest to recreate ConnectionConsumer["+cid+"], retry ...", t);
                        try {
                        deregisterInterest();
                        } catch (Throwable t1) {};
                        readQueue.clear();

                        synchronized(closeLock) {

                        try {
                            closeLock.wait(5000);
                        } catch (InterruptedException e) {};
                        }
                    }
                } while (!isClosed && !failoverInProgress);

                synchronized(closeLock) {
                    checkState();
                    if (failoverInProgress) {
                        throw new JMSException("Connection recovery in progress");
                    }
                }
                setRecreationInProgress2(false);
            }
            synchronized(recreationLock) {
                if (interestIdToBeRecreated == null) return recreated;

                if (interestIdToBeRecreated.equals(recid)) {
                    interestIdToBeRecreated = null;
                }
            }
            return recreated;

        } finally {
            setRecreationInProgress1(false);
            setRecreationInProgress2(false);
            if (sessionstopped) {
                ProtocolHandler ph = connection.getProtocolHandler();

                logger.log(Level.INFO, 
                       "Start ConnectionConsumer["+getInterestId()+"]'s session "+connection.getConnectionID());
                do {

                try {
                    ph.resumeSession(connection.getConnectionID().longValue());

                    logger.log(Level.INFO,
                           "Started ConnectionConsumer["+getInterestId()+"]'s session "+connection.getConnectionID());
                    break;
                } catch (Throwable t) {
                    String emsg = "Exception on start ConnectionConsumer["+getInterestId()+"]'s session, retry ...";
                    logger.log(Level.SEVERE, emsg, t);

                    synchronized(closeLock) {

                    try {
                    closeLock.wait(5000);
                    } catch (InterruptedException e) {};
                    }
                }

                } while (!isClosed);
            }
        }
    }

    private void setRecreationInProgress1(boolean b) {
        synchronized(recreationLock) {
            recreationInProgress1 = b;
            if (!b) {
                recreationLock.notifyAll();
            }
        }
    }

    private void setRecreationInProgress2(boolean b) {
        synchronized(recreationLock) {
            recreationInProgress2 = b;
            if (!b) {
                recreationLock.notifyAll();
            }
        }
    }

    protected void unregisteredXAResource() {
        synchronized(closeLock) {
            closeLock.notifyAll();
        }
    }

    protected void sessionClosed(SessionImpl ss) {
        seenSessions.remove(ss);
    }

    protected void onNullMessage() throws JMSException {
        if (isClosed) return;
        recreateIfNecessary();
    }

    /**
     * Load a message to the current ServerSession, if no current
     * ServerSession, a ServerSession will be retrieved from ServerSessionPool.
     *  This method is called by the ConnectionConsumerReader.
     *
     * @param message the message to be consumed
     *
     * @exception JMSException if fails to obtain a valid session from app server
     */
    protected void onMessage(MessageImpl message) throws JMSException {

        if (recreateIfNecessary()) return;

        if (session == null) {
            serverSession = serverSessionPool.getServerSession(); //may block

            try {

            session = (SessionImpl)serverSession.getSession();
            if (session.getConnection() != connection) {
                String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SVRSESSION_INVALID);
                throw new JMSException(errorString, AdministeredObject.cr.X_SVRSESSION_INVALID);
            }
            if (session.getMessageListener() == null) {
                String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_SVRSESSION_INVALID);
                throw new javax.jms.IllegalStateException(errorString, AdministeredObject.cr.X_SVRSESSION_INVALID);
            }

            } catch (JMSException e) {
            if (session != null && serverSession instanceof com.sun.messaging.jmq.jmsspi.ServerSession)
                ((com.sun.messaging.jmq.jmsspi.ServerSession)serverSession).destroy();
            session = null;
            serverSession = null;
            throw e;
            }
        }
        message.setSession(session);
        session.loadMessageToServerSession(message, serverSession, isDMQConsumer);
        if ((session instanceof XASessionImpl)) {
            XAResourceImpl xar = (XAResourceImpl)((XASessionImpl)session).getXAResource();
            xar.setConnectionConsumer(this);
            session.setConnectionConsumer(this);
            if (!seenSessions.contains(session)) {
                seenSessions.add(session);
            }
        }
    }

    /**
     * Start the current ServerSession.  This method is called from
     * the ConnectionConsumerReader when it has loaded maxMessages
     * to the current ServerSession.
     *
     * @exception JMSException if ServerSession.start() fails
     */
    protected void startServerSession() throws JMSException {
        if (serverSession != null) {
            serverSession.start();
            serverSession = null;
            session = null;
        }
    }

    protected int getMaxMessages() {
        return maxMessages;
    }

    /** Get the server session pool associated with this connection consumer.
      *
      * @return the server session pool used by this connection consumer.
      *
      * @exception JMSException if a JMS error occurs.
      */
    public ServerSessionPool getServerSessionPool() throws JMSException {
        return serverSessionPool;
    }

    /** Since a provider may allocate some resources on behalf of a
      * ConnectionConsumer outside the JVM, clients should close them when
      * they are not needed. Relying on garbage collection to eventually
      * reclaim these resources may not be timely enough.
      *
      * @exception JMSException if a JMS error occurs.
      */
    public void close() throws JMSException {
        synchronized(closeLock) {
           isClosed = true;
           closeLock.notifyAll();
        }
        reader.close();

        long waittime = 0;
        synchronized(recreationLock) {
            while (recreationInProgress1) { 
                if (waittime%15000 == 0) {
                    waittime = 0;
                    connection.connectionLogger.log(Level.INFO, 
                    "Waiting for ConnectionConsumer["+getInterestId()+"] reader thread completion ...");
                }
                try {
                recreationLock.wait(5000);
                waittime += 5000;
                } catch (Exception e) {} 
            }
        }
        removeInterest();
        connection.removeFromReadQTable(readQueueId);
    }

    public void setFailoverInprogress(boolean b) {
        synchronized(closeLock) {
           failoverInProgress = b;
           closeLock.notifyAll();
           if (!b) {
               return;
           }
        }

        long waittime = 0;
        synchronized(recreationLock) {
            while (recreationInProgress2) {
                if (waittime%15000 == 0) {
                    waittime = 0;
                    connection.connectionLogger.log(Level.INFO,
                    "Waiting for reader thread completes recreation of ConnectionConsumer["+getInterestId()+"] ...");
                }
                try {
                recreationLock.wait(5000);
                waittime += 5000;
                } catch (Exception e) {}
            }
        }

        readQueue.clear();
    }


    //should be called after connection stopped all sessions
    protected void stop() {
        readQueue.stop(false);
    }

    protected void start() {
        readQueue.start();
    }

    public void dump (PrintStream ps) {

        ps.println ("------ ConnectionConsumerImpl dump ------");

        ps.println ("Interest ID: " + getInterestId());
        ps.println ("is registered: " + getIsRegistered());
        //ps.println ("isTopic: " + getIsTopic());
        ps.println ("is durable: " + getDurable());

        if ( durable ) {
            ps.println ("durableName: " + getDurableName());
        }

        ps.println ("destination: " + getDestination());
        ps.println ("selector: " + messageSelector);
        ps.println("maxMessages: " + maxMessages);

    }

    protected java.util.Hashtable getDebugState(boolean verbose) {
        java.util.Hashtable ht = super.getDebugState(verbose);

        ht.put("maxMessages", String.valueOf(maxMessages));
        ht.put("recreationInProgress1", Boolean.valueOf(recreationInProgress1)); 
        ht.put("recreationInProgress2", Boolean.valueOf(recreationInProgress2)); 
        ht.put("failoverInProgress", Boolean.valueOf(failoverInProgress)); 
        Long id = interestIdToBeRecreated;
        ht.put("interestIdToBeRecreated", 
            (id == null ? "null": String.valueOf(id.longValue()))); 

        int sssize =  seenSessions.size();
        ht.put("#seenSessions", String.valueOf(sssize));

        int cnt = XAResourceMap.hasXAResourceForCC(this, false);
        ht.put("#xaresourcesInFlight", String.valueOf(cnt));
        
        return ht;
    }
}
