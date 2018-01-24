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

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.util.lists.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Hashtable;
import java.util.Vector;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.lang.ref.*;
import java.io.*;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.lists.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.SessionOpSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.DestinationSpi;

public class Session implements EventBroadcaster, EventListener
{
    static boolean DEBUG = false;
    protected static final boolean DEBUG_CLUSTER_MSG =
                   Globals.getConfig().getBooleanProperty(
                        Globals.IMQ + ".cluster.debug.msg") || DEBUG;

    /**
     * types of consumers
     */
    public static final int AUTO_ACKNOWLEDGE = 1;
    public static final int CLIENT_ACKNOWLEDGE = 2;
    public static final int DUPS_OK_ACKNOWLEDGE = 3;
    public static final int NO_ACK_ACKNOWLEDGE= 32768;
    // NONE may be transacted or an error
    public static final int NONE = 0;

    protected Logger logger = Globals.getLogger();

    private int ackType = 0; // XXX -should really use this not consumer

    private boolean isTransacted = false;
    private boolean isXATransacted = false;
    private TransactionUID currentTransactionID = null;

    SessionUID uid;

    // single session lock
    Object sessionLock = new Object();

    EventBroadcastHelper evb = new EventBroadcastHelper();

    Map consumers = null;
    Map listeners = null;
    Set busyConsumers = null;

    boolean paused = false;
    int pausecnt = 0;
    boolean valid = false;
    private boolean busy = false;

    ConnectionUID parentCuid = null;

    transient String creator = null;
    transient CoreLifecycleSpi coreLifecycle = null;
    transient SessionOpSpi ssop = null;


    private static boolean NOACK_ENABLED = false;
    static {
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
        }

        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            NOACK_ENABLED = license.getBooleanProperty(
                                license.PROP_ENABLE_NO_ACK, false);
        } catch (BrokerException ex) {
            NOACK_ENABLED = false;
        }

    }

    public static boolean isValidAckType(int type) {
        switch (type) {
            case Session.NONE: // transacted
            case Session.AUTO_ACKNOWLEDGE:
            case Session.CLIENT_ACKNOWLEDGE:
            case Session.DUPS_OK_ACKNOWLEDGE:
            case Session.NO_ACK_ACKNOWLEDGE:
                return true;
            default:
                return false;
        }
    }

    public SessionOpSpi getSessionOp() {
        return ssop;
    }

    public ConnectionUID getConnectionUID() {
        return parentCuid;
    }

    public boolean isValid() {
        return valid;
    }

    public static Hashtable getAllDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "All Sessions");
        Hashtable all = new Hashtable();
        synchronized(allSessions) {
            ht.put("allSessionCnt", String.valueOf(allSessions.size()));
            Iterator itr = allSessions.values().iterator();
            while (itr.hasNext()) {
                Session s = (Session)itr.next();
                all.put(String.valueOf(s.getSessionUID().longValue()),
                       s.getDebugState());
            }
        }
        ht.put("allSessions", all);
        all = new Hashtable();
        synchronized(consumerToSession) {
            ht.put("consumerToSessionCnt", String.valueOf(consumerToSession.size()));
            Iterator itr = consumerToSession.keySet().iterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                all.put(o.toString(),
                       consumerToSession.get(o).toString());
            }
        }
        ht.put("consumerToSession", all);
        return ht;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TABLE", "Session[" + uid.longValue() + "]");
        ht.put("uid", String.valueOf(uid.longValue()));
        ht.put("connection", String.valueOf(parentCuid.longValue()));
        ht.put("paused", String.valueOf(paused));
        ht.put("pausecnt", String.valueOf(pausecnt));
        ht.put("valid", String.valueOf(valid));
        ht.put("busy", String.valueOf(busy));

        ht.put("SessionOp", ssop.getDebugState());

        ht.put("consumerCnt", String.valueOf(consumers.size()));
        Vector v = new Vector();
        synchronized (consumers) {
            Iterator itr = consumers.keySet().iterator();
            while (itr.hasNext()) {
                ConsumerUID cuid = (ConsumerUID)itr.next();
                v.add(String.valueOf(cuid.longValue()));
            }
        }
        ht.put("consumers", v);
        ht.put("busyConsumerCnt", String.valueOf(busyConsumers.size()));
        v = new Vector();
        synchronized (busyConsumers) {
            Iterator itr = busyConsumers.iterator();
            while (itr.hasNext()) {
                ConsumerUID cuid = (ConsumerUID)itr.next();
                v.add(String.valueOf(cuid.longValue()));
            }
        }
        ht.put("busyConsumers", v);
        return ht;
    }

    public Vector getDebugMessages(boolean full) {
        return ssop.getDebugMessages(full);
    }


    // used for JMX
    public int getNumPendingAcks(ConsumerUID uid)
    {
         return getPendingAcks(uid).size();
    }
    

    public List getPendingAcks(ConsumerUID uid)
    {
        return ssop.getPendingAcks(uid);
    }

    public void setAckType(int type) 
        throws BrokerException
    {
        if (!Session.isValidAckType(type))

            throw new BrokerException(
                        "Internal Error: Invalid Ack Type :" + type,
                        Status.BAD_REQUEST);

        if (type == Session.NO_ACK_ACKNOWLEDGE && !NOACK_ENABLED) {
            throw new BrokerException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.E_FEATURE_UNAVAILABLE,
                            Globals.getBrokerResources().getKString(
                                BrokerResources.M_NO_ACK_FEATURE)),
                        BrokerResources.E_FEATURE_UNAVAILABLE,
                        (Throwable) null,
                        Status.NOT_ALLOWED);
        }
        ssop.checkAckType(type);
        ackType = type;
    }

    public int getConsumerCnt() {
        if (consumers == null) return 0;
        return consumers.size();
    }

    public Iterator getConsumers() {
        if (consumers == null) {
            return (new ArrayList()).iterator();
        }
        synchronized(consumers) {
            return (new ArrayList(consumers.values())).iterator();
        }
    }

    public List getConsumerUIDs() {
        if (consumers == null) {
            return new ArrayList();
        }
        synchronized(consumers) {
            return new ArrayList(consumers.keySet());
        }
    }

    public boolean isAutoAck(ConsumerUID uid) {
        if (isUnknown()) {
            return uid.isAutoAck();
        }
        return ackType == Session.AUTO_ACKNOWLEDGE;
    }

    public boolean isUnknown() {
        return ackType == Session.NONE;
    }

    public boolean isClientAck(ConsumerUID uid) {
        if (isUnknown()) {
            return !uid.isAutoAck() && !uid.isDupsOK();
        }
        return ackType == Session.CLIENT_ACKNOWLEDGE;
    }

    public boolean isDupsOK(ConsumerUID uid) {
        if (isUnknown()) {
            return uid.isDupsOK();
        }
        return ackType == Session.DUPS_OK_ACKNOWLEDGE;
    }

    public boolean isUnsafeAck(ConsumerUID uid) {
        return isDupsOK(uid) || isNoAck(uid);
    }

    public boolean isNoAck(ConsumerUID uid) {
        if (isUnknown()) {
            return uid.isNoAck();
        }
        return ackType == Session.NO_ACK_ACKNOWLEDGE;
    }

    public boolean isTransacted() {
        return isTransacted;
    }

    public boolean isXATransacted() {
        return isXATransacted;
    }

    public TransactionUID getCurrentTransactionID() {
        return currentTransactionID;
    }

    public ConsumerUID getStoredIDForDetatchedConsumer(ConsumerUID cuid) {
        return ((SessionOp)ssop).getStoredIDForDetatchedConsumer(cuid);
    }

    public void debug(String prefix) {
        if (prefix == null)
            prefix = "";
        logger.log(Logger.INFO,prefix + "Session " + uid);
        logger.log(Logger.INFO,"Paused " + paused);
        logger.log(Logger.INFO,"pausecnt " + pausecnt);
        logger.log(Logger.INFO,"busy " + busy);
        logger.log(Logger.INFO,"ConsumerCnt " + consumers.size());
        logger.log(Logger.INFO,"BusyConsumerCnt " + consumers.size());
        Iterator itr = consumers.values().iterator();
        while (itr.hasNext()) {
            ConsumerSpi c = (ConsumerSpi)itr.next();
            c.debug("\t");
        }

    }

    private Session(ConnectionUID uid, String sysid, CoreLifecycleSpi clc) {
        this(new SessionUID(), uid, sysid, clc);
    }

    private Session(SessionUID uid, ConnectionUID cuid, String sysid, CoreLifecycleSpi clc) {
        this.uid = uid;
        parentCuid = cuid;
        consumers = Collections.synchronizedMap(new HashMap());
        listeners = Collections.synchronizedMap(new HashMap());
        busyConsumers = Collections.synchronizedSet(new LinkedHashSet());
        valid = true;
        creator = sysid;

        coreLifecycle = clc;
        ssop = coreLifecycle.newSessionOp(this);

        DEBUG = (DEBUG || logger.getLevel() <= Logger.DEBUG || DEBUG_CLUSTER_MSG);
        logger.log(Logger.DEBUG,"Created new session " + uid
              + " on connection " + cuid);
    }

    public void dump(String prefix) {
        if (prefix == null)
            prefix = "";

        logger.log(Logger.INFO,prefix + " Session " + uid);
        logger.log(Logger.INFO, prefix + "---------------------------");
        logger.log(Logger.INFO, prefix + "busyConsumers (size) " + busyConsumers.size());
        logger.log(Logger.INFO, prefix + "busyConsumers (list) " + busyConsumers);
        logger.log(Logger.INFO, prefix + "consumers (size) " + consumers.size());
        logger.log(Logger.INFO, prefix + "consumers (list) " + consumers);
        logger.log(Logger.INFO, prefix + "---------------------------");
        Iterator itr = consumers.values().iterator();
        while (itr.hasNext()) {
            ((ConsumerSpi)itr.next()).dump(prefix + "\t");
        }
    }

    public SessionUID getSessionUID() {
        return uid;
    }

    public void pause(String reason) {
        synchronized(sessionLock) {
            paused = true;
            pausecnt ++;
            if (DEBUG)
                logger.log(Logger.INFO,"Session: Pausing " + this 
                    + "[" + pausecnt + "]" + reason);
        }
        checkState(null);
    }

    public void resume(String reason) {
        synchronized(sessionLock) {
            pausecnt --;
            if (pausecnt <= 0)
                paused = false;
            assert pausecnt >= 0: "Bad pause " + this;
            if (DEBUG)
                logger.log(Logger.INFO,"Session: Resuming " + this 
                     + "[" + pausecnt + "]" + reason);
        }
        checkState(null);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean hasWork() {
        return (busyConsumers.size() > 0);
    }


    public boolean fillNextPacket (Packet p, ConsumerUID cid) {
        if (paused) {
            return false;
        }
        ConsumerSpi consumer = (ConsumerSpi)consumers.get(cid);
        Object ref = null;
        synchronized (sessionLock) {
            ref = consumer.getAndFillNextPacket(p);
            if (ref == null) {
                return false;
            }
            return ssop.onMessageDelivery(consumer, ref);
        }
    }

    public ConsumerUID fillNextPacket(Packet p) {
        if (paused) {
            return null;
        }
        
        ConsumerUID cid = null;
        ConsumerSpi consumer = null;
        while (!paused) {          
            // get a consumer
            synchronized (busyConsumers) {
               if (busyConsumers.isEmpty()) {
                   break;
               }
               Iterator itr = busyConsumers.iterator();
               cid = (ConsumerUID)itr.next();
               consumer = (ConsumerSpi)consumers.get(cid);
               itr.remove();
            }

            assert p != null;

            if (consumer == null) return null;

            Object ref = null;
            synchronized (sessionLock) {
                if (paused)  {
                    synchronized (busyConsumers) {
                        if (consumer.isBusy())
                            busyConsumers.add(cid);
                    }
                    return null;
                }

                ref = consumer.getAndFillNextPacket(p);
                synchronized (busyConsumers) {
                    if (consumer.isBusy())
                        busyConsumers.add(cid);
                }
                if (ref == null) {
                    continue;
                }
                if (!ssop.onMessageDelivery(consumer, ref)) {
                    continue;
                }
            }

            checkState(null);
            return (ref != null && cid != null ? cid : null);

        }
        checkState(null);
        return null;
    }

    public Object getBusyLock() {
        return busyConsumers;
    }

    public boolean isBusy() {
        synchronized (busyConsumers) {
            return busy;
        }
    }

    public String toString() {
        return "Session [" + uid + "]";

    }

    public synchronized void attachConsumer(ConsumerSpi c) throws BrokerException {
        logger.log(Logger.DEBUG,"Attaching Consumer " + c.getConsumerUID()
           + " to Session " + uid);

        if (!valid) {
            throw new BrokerException(Globals.getBrokerResources().
                getKString(BrokerResources.X_SESSION_CLOSED, this.toString()));
        }
        c.attachToSession(getSessionUID());
        ConsumerUID cuid = c.getConsumerUID();
        cuid.setAckType(ackType);
        c.getStoredConsumerUID().setAckType(ackType);
        consumers.put(cuid, c);

        //DestinationSpi d = c.getFirstDestination();
        listeners.put(cuid, c.addEventListener(this, 
             EventType.BUSY_STATE_CHANGED, null));
        if (c.isBusy()) {
            busyConsumers.add(cuid);
        }
        synchronized(consumerToSession) {
            consumerToSession.put(c.getConsumerUID(), getSessionUID());
        }

        checkState(null);
    }

    /**
     * clean indicated that it was made by a 3.5 consumer calling
     * close
     * @param id last SysMessageID seen (null indicates all have been seen)
     * @param redeliverAll  ignore id and redeliver all
     * @param redeliverPendingConsume - redeliver pending messages
     */
    public ConsumerSpi detatchConsumer(ConsumerUID c, SysMessageID id, 
        boolean idInTransaction, boolean redeliverPendingConsume, boolean redeliverAll)
        throws BrokerException {

        pause("Consumer.java: detatch consumer " + c);
        ConsumerSpi con = (ConsumerSpi)consumers.remove(c);
        if (con == null) {
            resume("Consumer.java: bad removal " + c);
            throw new BrokerException("Detatching consumer " + c 
                 + " not currently attached "
                  +  "to " + this );
        }
        con.pause("Consumer.java: detatch consumer " + c
             + " DEAD"); // we dont want to ever remove messages
        detatchConsumer(con, id, idInTransaction, redeliverPendingConsume, redeliverAll);
        resume("Consumer.java: detatch consumer " + c);
        return con;
    }

    /**
     * @param id last SysMessageID seen (null indicates all have been seen)
     * @param redeliverAll  ignore id and redeliver all
     * @param redeliverPendingConsume - redeliver pending messages
     */
    private void detatchConsumer(ConsumerSpi con, SysMessageID id,
        boolean idInTransaction, boolean redeliverPendingConsume, boolean redeliverAll)
    {
        if (DEBUG) {
        logger.log(Logger.INFO,"Detaching Consumer "+con.getConsumerUID()+
           " on connection "+ con.getConnectionUID()+ 
           " from Session " + uid + " last id was " + id);
        }
        con.pause("Consumer.java: Detatch consumer 1 " + con   );
        pause("Consumer.java: Detatch consumer A " + con);
        ConsumerUID c = con.getConsumerUID();
        //ConsumerUID sid = con.getStoredConsumerUID();
        Object listener= listeners.remove(c);
        assert listener != null;
        con.removeEventListener(listener);
        con.attachToSession(null);
        busyConsumers.remove(c);
        consumers.remove(c);
        checkState(null);

        Connection conn = Globals.getConnectionManager().getConnection(getConnectionUID());

        if (ssop.detachConsumer(con, id, idInTransaction, 
                redeliverPendingConsume, redeliverAll, conn)) {
            synchronized(consumerToSession) {
                consumerToSession.remove(c);
            }
        }

        resume("Consumer.java: resuming after detatch " + con);

    }

    public Object ackInTransaction(ConsumerUID cuid, SysMessageID id, 
        TransactionUID tuid, boolean isXA, int deliverCnt)
        throws BrokerException {

        //workaround client REDELIVER protocol for XA transaction
        if (!isTransacted) {
            isTransacted = true;
        }
        if (isXA && !isXATransacted) {
            isXATransacted = true;
        }
        currentTransactionID = tuid;

        return ssop.ackInTransaction(cuid, id, tuid, deliverCnt);
    }


    private void close() {
        synchronized(this) {
            if (!valid) return;
            valid = false;
        }

        if (DEBUG) {
            logger.log(Logger.INFO, "Close Session " + uid);
        }
        
        Connection conn = Globals.getConnectionManager().getConnection(getConnectionUID());
        boolean old = false;
        if (conn != null && conn.getClientProtocolVersion() < Connection.RAPTOR_PROTOCOL) {
            old =true;
        }

        Iterator itr = null;
        synchronized (this) {
            itr = new HashSet(consumers.values()).iterator();
        }
        while (itr.hasNext()) {
            ConsumerSpi c =(ConsumerSpi)itr.next();
            itr.remove();
            detatchConsumer(c, null, false, old, false);
        }

        ssop.close(conn);

          // Clear up old session to consumer match
        synchronized(consumerToSession) {
            Iterator citr = consumerToSession.values().iterator();
            while (citr.hasNext()) {
                SessionUID suid = (SessionUID)citr.next();
                if (suid.equals(uid)) {
                    citr.remove();
                }
            }
        }
        allSessions.remove(uid);
    }

    /**
     * handles an undeliverable message. This means:
     * <UL>
     *   <LI>removing it from the pending ack list</LI>
     *   <LI>Sending it back to the destination to route </LI>
     * </UL>
     * If the message can not be routed, returns the packet reference
     * (to clean up)
     */
    public Object handleUndeliverable(ConsumerUID cuid, SysMessageID id, 
                                      int deliverCnt, boolean deliverCntUpdateOnly)
                                      throws BrokerException {

        ConsumerSpi c = coreLifecycle.getConsumer(cuid);
        return ssop.handleUndeliverable(c, id, deliverCnt, deliverCntUpdateOnly);
    }

    /**
     * handles an undeliverable message. This means:
     * <UL>
     *   <LI>removing it from the pending ack list</LI>
     *   <LI>Sending it back to the destination to route </LI>
     * </UL>
     * If the message can not be routed, returns the packet reference
     * (to clean up)
     */
    public Object handleDead(ConsumerUID cuid,
           SysMessageID id, RemoveReason deadReason, Throwable thr, 
           String comment, int deliverCnt)
           throws BrokerException {

        if (DEBUG) {
        logger.log(logger.INFO, "handleDead["+id+", "+cuid+"]"+deadReason);
        }
        ConsumerSpi c = coreLifecycle.getConsumer(cuid);

        return ssop.handleDead(c, id, deadReason, thr, comment, deliverCnt);

    }

    /**
     * Caller must call postAckMessage() immediately after this call
     *
     * @param ackack whether client requested ackack
     */
    public Object ackMessage(ConsumerUID cuid, SysMessageID id, boolean ackack)
    throws BrokerException {
        return ackMessage(cuid, id, null, null, null, ackack);
    }

    /**
     * Caller must call postAckMessage() immediately after this call
     */
    public Object ackMessage(ConsumerUID cuid, SysMessageID id,
        TransactionUID tuid, TransactionList translist, 
        HashMap<TransactionBroker, Object> remoteNotified, boolean ackack) 
        throws BrokerException {
        return ssop.ackMessage(cuid, id, tuid, translist, remoteNotified, ackack);
    }

    public void postAckMessage(ConsumerUID cuid, SysMessageID id, boolean ackack)
    throws BrokerException {
        ssop.postAckMessage(cuid, id, ackack);
        if (isValid()) {
            if (consumers.get(cuid) == null) {
                if (!ssop.hasDeliveredMessages(cuid)) {
                    synchronized(consumerToSession) {
                        consumerToSession.remove(cuid); 
                    }
                }
            }
        }
    }

    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) {

        ConsumerUID cuid = ((ConsumerSpi)target).getConsumerUID();
        if (type == EventType.BUSY_STATE_CHANGED) {

            synchronized(busyConsumers) {
                ConsumerSpi c = (ConsumerSpi)consumers.get(cuid);
                if ( c != null && c.isBusy()) {
                    // busy
                    busyConsumers.add(cuid);
                }
            }
            checkState(null); // cant hold a lock, we need to prevent a
                              // deadlock

        } else  {
            assert false : " event is not valid ";
        }
            
    }

     /**
     * Request notification when the specific event occurs.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *          publish the event type passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Object userData)
        throws UnsupportedOperationException {

        if (type != EventType.BUSY_STATE_CHANGED ) {
            throw new UnsupportedOperationException("Only " +
                "Busy and Not Busy types supported on this class");
        }
        return evb.addEventListener(listener,type, userData);
    }

    /**
     * Request notification when the specific event occurs AND
     * the reason matched the passed in reason.
     * @param listener object to notify when the event occurs
     * @param type event which must occur for notification
     * @param userData optional data queued with the notification
     * @param reason reason which must be associated with the
     *               event (or null for all events)
     * @return an id associated with this notification
     * @throws UnsupportedOperationException if the broadcaster does not
     *         support the event type or reason passed in
     */
    public Object addEventListener(EventListener listener, 
                        EventType type, Reason reason,
                        Object userData)
        throws UnsupportedOperationException
    {
        if (type != EventType.BUSY_STATE_CHANGED ) {
            throw new UnsupportedOperationException("Only " +
                "Busy and Not Busy types supported on this class");
        }
        return evb.addEventListener(listener,type, reason, userData);
    }

    /**
     * remove the listener registered with the passed in
     * id.
     * @return the listener callback which was removed
     */
    public Object removeEventListener(Object id) {
        return evb.removeEventListener(id);
    }
  
    private void checkState(Reason r) {

        boolean notify = false;
        boolean isBusy = false;
        synchronized(busyConsumers) {
            isBusy = !paused && (busyConsumers.size() > 0);
            if (isBusy != busy) {
                busy = isBusy;
                notify = true;
            }
        }
        if (notify) {
            notifyChange(EventType.BUSY_STATE_CHANGED,
                r, this, Boolean.valueOf(!isBusy), Boolean.valueOf(isBusy));
        }
       
    }

    private void notifyChange(EventType type,  Reason r, 
               Object target,
               Object oldval, Object newval) 
    {
        evb.notifyChange(type,r, target, oldval, newval);
    }

    public synchronized ConsumerSpi getConsumerOnSession(ConsumerUID uid)
    {
        return (ConsumerSpi)consumers.get(uid);
    }


// ----------------------------------------------------------------------------
// Static Methods
// ----------------------------------------------------------------------------

    private static Map consumerToSession = new HashMap();
    static Map allSessions = new HashMap();


    public static void clearSessions()
    {
        consumerToSession.clear();
        allSessions.clear();
    }

    public static Session getSession(ConsumerUID uid)
    {
        SessionUID suid = null;
        synchronized(consumerToSession) {
            suid = (SessionUID)consumerToSession.get(uid);
        }
        if (suid == null) return null;
        return getSession(suid);
    }


    // used for internal errors only
    public static void dumpAll() {
        synchronized(allSessions) {
            Globals.getLogger().log(Logger.INFO,"Dumping active sessions");
            Iterator itr = allSessions.keySet().iterator();
            while (itr.hasNext()) {
                Object k = itr.next();
                Object v = allSessions.get(k);
                Globals.getLogger().log(Logger.INFO,"\t"+k+ " : " + v);
            }
        }
    }


    public static Session createSession(ConnectionUID uid, String id, CoreLifecycleSpi clc)
    {
        Session s = new Session(uid, id, clc);
        synchronized(allSessions) {
            allSessions.put(s.getSessionUID(), s);
        }
        return s;
    }


    // for default session only
    public static Session createSession(SessionUID uid, ConnectionUID cuid,
                 String id, CoreLifecycleSpi clc)
    {
        Session s = new Session(uid, cuid, id, clc);
        synchronized(allSessions) {
            allSessions.put(s.getSessionUID(), s);
        }
        return s;
    }


    /**
     * @param clean closeSession was called
     */
    public static void closeSession(SessionUID uid) {
        Session s = null;
        synchronized(allSessions) {
            s = (Session)allSessions.remove(uid);
        }
        if (s == null) {
            // already was closed
            return;
        }
        s.close();
    }

    public static Session getSession(SessionUID uid)
    {
        synchronized(allSessions) {
            return (Session)allSessions.get(uid);
        }
    }

    public static Session getSession(String creator)
    {
        if (creator == null) return null;

        synchronized(allSessions) {
            Iterator itr = allSessions.values().iterator();
            while (itr.hasNext()) {
                Session c = (Session)itr.next();
                if (creator.equals(c.creator))
                    return c;
            }
        }
        return null;
    }

}

