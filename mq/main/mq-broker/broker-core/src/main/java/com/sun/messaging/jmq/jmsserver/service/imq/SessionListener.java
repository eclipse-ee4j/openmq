/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.util.*;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.handlers.AckHandler;
import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsservice.*;

/**
 * Thread that watches a session for events
 */
class SessionListener implements com.sun.messaging.jmq.util.lists.EventListener, Runnable {
    /**
     * IMQDirectService
     */

    JMSServiceImpl parent;

    /**
     * listener is alive
     */
    boolean valid = false;
    boolean destroyed = false;

    /**
     * session is stopped ?
     */
    boolean stopped = true;

    // async process thread in stopped position
    boolean islocked = true;

    /**
     * session associated with this listener
     */
    Session session = null;

    /**
     * is this a sync or async consumer
     */
    boolean sync = true;

    /**
     * was a Session event listener registered ?
     */
    boolean sessionEvListenerRegistered = false;

    /**
     * has a thread been started
     */
    boolean started = false;

    /**
     * maps consumerUID to Consumer object NOTE: this is NOT the core.Consumer object
     */
    HashMap consumers = new HashMap();

    /**
     * lock used for notification
     */
    Object sessionLock = new Object();

    /**
     * ListenerObject used for the eventListener on Session
     */
    Object sessionEL = null;

    /**
     * create a session listener
     */
    SessionListener(JMSServiceImpl svc, Session s) {
        this.parent = svc;
        this.session = s;
        parent.addListener(s.getSessionUID(), this);
    }

    /**
     * Synchronous method to retrieve a packet
     */
    public Packet getNextConsumerPacket(ConsumerUID cuid) {
        return getNextConsumerPacket(cuid, 0);
    }

    /**
     * Synchronous method to retrieve a packet
     */
    public Packet getNextConsumerPacket(ConsumerUID cuid, long timeout) {

        // we are either async or sync
        // if this is called from something that registered a message
        // listener, something went wrong. Throw an exception
        if (!sync) {
            throw new RuntimeException("Cannot invoke SessionListener.getNextConsumerPacket() when in asynchronous receiving mode");
        }

        // see if we are busy
        // if we aren't wait

        com.sun.messaging.jmq.jmsserver.core.Consumer c = com.sun.messaging.jmq.jmsserver.core.Consumer
                .getConsumer(cuid);

        sync = true;

        /*
         * If timeout is < 0, this is for receiveNoWait(). If the consumer is not busy() return null right away.
         */
        if (timeout < 0) {
            if (stopped || !c.isBusy()) {
                return (null);
            }
        }

        // add an event listener to wake us up when the consumer is busy
        Object lock = c.addEventListener(this, EventType.BUSY_STATE_CHANGED, null);
        try {

            while (!c.isBusy() || stopped) {
                try {
                    // wait until the consumer is not busy
                    synchronized (c.getConsumerUID()) {
                        synchronized (sessionLock) {
                            if (stopped) {
                                if (timeout > 0) {
                                    long locktime = System.currentTimeMillis();
                                    try {
                                        sessionLock.wait(timeout);
                                    } catch (Exception ex) {
                                    }
                                    // adjust the timeout by the wait time
                                    long now = System.currentTimeMillis();
                                    timeout -= now - locktime;
                                    if (timeout <= 0) {
                                        return null;
                                    }
                                } else if (timeout == 0) {
                                    try {
                                        sessionLock.wait(timeout);
                                    } catch (Exception ex) {
                                    }
                                }

                                // Is this needed ?
                                if (stopped) {
                                    return null;
                                }

                            }
                        }

                        // we cant check isBusy in the lock because
                        // we can deadlock
                        // instead look in the cuidNotify table
                        if (cuidNotify.remove(c.getConsumerUID())) {
                            continue;
                        }
                        /*
                         * Just in case between the (timeout < 0) check above and the while loop, the consumer became not busy, we want to
                         * return if it is a noWait case.
                         */
                        if (timeout < 0) {
                            c.removeEventListener(lock);
                            return (null);
                        }
                        c.getConsumerUID().wait(timeout);
                    }
                    if (stopped) {
                        return null;
                    }

                    /*
                     * wait() returned but this could be due to a timeout and not from notify()
                     *
                     * We check the busy state and also if a non zero timeout was specified. If it is indeed a timeout, return null.
                     */
                    if (!c.isBusy() && (timeout > 0)) {

                        // remove the event listener since we arent sure if we will care
                        c.removeEventListener(lock);
                        return (null);
                    }
                } catch (Exception ex) {
                }
                // remove the event listener since we arent sure if we will care
                // c.removeEventListener(lock);
            } // while

        } finally {
            c.removeEventListener(lock);
        }
        // if (stopped) return null;
        synchronized (c.getConsumerUID()) {
            cuidNotify.remove(c.getConsumerUID());
        }

        // now get the packet
        Packet p = new Packet();
        if (session.fillNextPacket(p, cuid)) {
            return p;
        }

        // this should only happen if something went strange like we
        // have two threads processing @ the same time
        // but just try it again
        return getNextConsumerPacket(cuid, timeout); // recurse

    }

    /**
     * Set this up as an AsyncListener
     */
    public void setAsyncListener(com.sun.messaging.jmq.jmsserver.core.Consumer brokerC, Consumer target) {
        ConsumerUID cuid;

        /*
         * Target passed in may be null This is to indicate that the consumer has unregistered itself as an async consumer - may
         * go into sync mode.
         *
         */
        if (target == null) {
            // XXX Need to stop delivery of msgs - TBD
            //

            // we are no longer asynchronous
            sync = true;

            // Remove Consumer from table
            //
            cuid = brokerC.getConsumerUID();
            consumers.remove(cuid);

            return;
        }

        // we arent synchronous
        sync = false;

        // put the Consumer into a table so we can retrieve it when
        // a message is received
        cuid = brokerC.getConsumerUID();
        consumers.put(cuid, target);

        // set up a listener to wake up when the Session is Busy
        if (!sessionEvListenerRegistered) {
            sessionEL = session.addEventListener(this, EventType.BUSY_STATE_CHANGED, null);
            sessionEvListenerRegistered = true;
        }

        // ok - start the parsing thread
        if (!started) {
            Thread thr = new Thread(this, "Session" + session.getSessionUID());
            thr.start();
            started = true;
        }
    }

    /**
     * Thread run method
     */
    @Override
    public void run() {
        process();
    }

    // set when a notification is about to occur
    boolean sessionLockNotify = false;

    // set when a consumer notificationis about to occur
    HashSet cuidNotify = new HashSet();

    /**
     * method which handles delivering messages
     */
    public void process() {
        if (sync) {
            // we shouldn't be here
            // there doesnt need to be an external thread
            throw new RuntimeException("Cannot invoke SessionListener.process() when in synchronous receiving mode");
        }
        synchronized (sessionLock) {
            if (destroyed) {
                valid = false;
                sessionLock.notifyAll();
                return;
            }
            valid = true;
        }
        while (valid) {
            // if we are not busy, wait for the eventListener to wake us up
            while (valid && (!session.isBusy() || stopped)) {
                // get the lock
                synchronized (sessionLock) {
                    islocked = true;
                    sessionLock.notifyAll();
                    // we cant check isBusy in the loop so
                    // instead check the sessionLockNotify flag
                    if (sessionLockNotify || !valid || stopped) {
                        sessionLockNotify = false;
                        continue;
                    }
                    try {
                        sessionLock.wait();
                    } catch (Exception ex) {
                        Globals.getLogger().log(Logger.DEBUGHIGH, "Exception in sessionlock wait", ex);
                    }
                }
            }
            if (!valid) {
                continue;
            }
            synchronized (sessionLock) {
                // we dont care if we are about to notify
                sessionLockNotify = false;
                islocked = false;
            }
            if (session.isBusy() && !stopped && valid) {
                // cool, we have something to do
                Packet p = new Packet();

                // retrieve the next packet and its ConsumerUID
                ConsumerUID uid = session.fillNextPacket(p);

                if (uid == null) {
                    // weird, something went wrong, try again
                    continue;
                }

                // Get the consumer object
                Consumer con = (Consumer) consumers.get(uid);
                try {
                    // call the deliver method
                    JMSAck ack = con.deliver(p);
                    if (ack != null) {
                        long transactionId = ack.getTransactionId(), consumerId = ack.getConsumerId();
                        SysMessageID sysMsgId = ack.getSysMessageID();
                        TransactionUID txnUID = null;
                        ConsumerUID conUID = null;

                        if (transactionId != 0) {
                            txnUID = new TransactionUID(transactionId);
                        }

                        if (consumerId != 0) {
                            conUID = new ConsumerUID(consumerId);
                        }

                        IMQConnection cxn = parent.checkConnectionId(ack.getConnectionId(), "Listener Thread");

                        SysMessageID ids[] = new SysMessageID[1];
                        // ids[0] = sysMsgId;
                        // ids[0] = ((Packet)p).getSysMessageID();
                        ids[0] = sysMsgId;
                        ConsumerUID cids[] = new ConsumerUID[1];
                        cids[0] = conUID;
                        Globals.getProtocol().acknowledge(cxn, txnUID, false, AckHandler.ACKNOWLEDGE_REQUEST, null, null, 0, ids, cids);
                    }
                } catch (Exception ex) {
                    if (ex instanceof ConsumerClosedNoDeliveryException) {
                        if (parent.getDEBUG()) {
                            Globals.getLogger().logStack(Logger.INFO,
                                    "DirectConsumer " + con + " is closed, message " + p.getSysMessageID() + " can not be deliverd", ex);
                        }
                    } else {
                        // I have no idea what the exception might mean so just
                        // log it and go on
                        Globals.getLogger().logStack(Logger.ERROR, Globals.getBrokerResources().getKString(BrokerResources.X_CANNOT_DELIVER_MESSAGE_TO_CONSUMER,
                                p.getSysMessageID(), uid + " DirectConsumer[" + con + "]"), ex);
                    }
                }
            }

        }
    }

    /**
     * This method is called when a thread puts a message on a consumer or session. Its used to wake up the waiting thread
     * which might be in the process() [asynchronous] method or in the getNextConsumerPacket()[synchronous].
     */
    @Override
    public void eventOccured(EventType type, Reason r, Object target, Object oldval, Object newval, Object userdata) {

        if (type != EventType.BUSY_STATE_CHANGED) {
            return; // should never occur
        }

        // deal with consumers (the synchronous case)
        if (target instanceof com.sun.messaging.jmq.jmsserver.core.Consumer) {
            // notify on the ConsumerUID
            com.sun.messaging.jmq.jmsserver.core.Consumer cc = (com.sun.messaging.jmq.jmsserver.core.Consumer) target;
            ConsumerUID cuid = cc.getConsumerUID();
            if (cc.isBusy()) {
                synchronized (cuid) {
                    // we cant check isBusy in here - we can deadlock
                    // so instead look in the cuidNotify hashtable

                    cuidNotify.add(cuid);
                    // ok, we want to wake up the thread currently in
                    // getNextConsumerPacket.
                    // it is waiting on the ConsumerUID
                    // so do a notify on ConsumerUID
                    cuid.notifyAll();
                }
            }
            return;
        }

        // deal with sessions (the async case)
        // Session s = (Session)target;

        // this wakes up the process thread which is blocked on SessionLock
        synchronized (sessionLock) {
            sessionLockNotify = true;
            sessionLock.notifyAll();
        }
    }

    /*
     * Start this session - set stopped flag to false and wake up waiting thread
     */
    public void startSession() {
        // wake up the thread (if waiting())
        synchronized (sessionLock) {
            stopped = false;
            sessionLock.notifyAll();
        }
    }

    private static final long MAX_WAIT_FOR_SESSION_STOP = 90000; // 90sec

    /*
     * Stop this session - set stopped flag to true
     */
    public void stopSession(boolean dowait) {
        long totalwaited = 0L;
        synchronized (sessionLock) {
            stopped = true;
            sessionLock.notifyAll();
            if (!dowait) {
                return;
            }
            while (valid && !islocked && totalwaited < MAX_WAIT_FOR_SESSION_STOP) {
                String[] args = { "DirectSession[" + session + "]" };
                Globals.getLogger().log(Logger.INFO, Globals.getBrokerResources().getKTString(BrokerResources.I_WAIT_FOR_SESSION_STOP, args));
                long starttime = System.currentTimeMillis();
                try {
                    sessionLock.wait(1500L);
                } catch (Exception e) {
                }
                totalwaited += (System.currentTimeMillis() - starttime);
            }
            if (valid && !islocked) {
                String[] args = { "DirectSession[" + session + "]" };
                Globals.getLogger().log(Logger.WARNING, Globals.getBrokerResources().getKTString(BrokerResources.W_WAIT_FOR_SESSION_STOP_TIMED_OUT, args));
            }
        }
    }

    public void destroy() {

        // set the shutdown flag
        // wake up the thread (if waiting())
        synchronized (sessionLock) {
            valid = false;
            destroyed = true;
            sessionLock.notifyAll();
        }

        // remove any even listener
        session.removeEventListener(sessionEL);

        parent.removeListener(session.getSessionUID());

        // clean up the consumers table
        consumers = null;
    }

}

