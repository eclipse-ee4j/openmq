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
 * @(#)PacketHandler.java	1.45 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import java.io.IOException;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.security.Principal;
import java.security.AccessControlException;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;
import com.sun.messaging.jmq.jmsserver.plugin.spi.DestinationSpi;
import com.sun.messaging.jmq.jmsserver.GlobalProperties;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;

/**
 * super classes which deal with handling specific
 * message types
 */
public abstract class PacketHandler 
{
    private static boolean DEBUG = false;

    public static boolean getDEBUG() {
        return DEBUG;
    }

    protected final Logger logger = Globals.getLogger();
    protected final BrokerResources br = Globals.getBrokerResources();

    protected CoreLifecycleSpi coreLifecycle = null;
    protected DestinationList DL = Globals.getDestinationList();

    public void setCoreLifecycle(CoreLifecycleSpi clc) {
        coreLifecycle = clc;
    }

    public CoreLifecycleSpi getCoreLifecycle() {
        return coreLifecycle; 
    }

    /**
     * method to handle processing the specific packet associated
     * with this PacketHandler
     * @returns true if the packet can be freed
     */
    public abstract boolean handle(IMQConnection con, Packet msg) throws
        BrokerException;

    public void handleForbidden(IMQConnection con, Packet msg,
                                int replyType) throws BrokerException {
        Packet reply = new Packet(con.useDirectBuffers());
        if (DEBUG) {
        logger.log(Logger.DEBUG, "handle forbidden: sending "+PacketType.getString(replyType));
        }
        reply.setPacketType(replyType);
        reply.setConsumerID(msg.getConsumerID());
        Hashtable hash = new Hashtable();
        hash.put("JMQStatus", Integer.valueOf(Status.FORBIDDEN));
        reply.setProperties(hash);
        con.sendControlMessage(reply);
    }
   
    /**
     * entry point for destination access control check
     */
    public void checkPermission(Packet msg, IMQConnection con)
                            throws AccessControlException, IOException,
                            ClassNotFoundException, BrokerException {

        int id = msg.getPacketType();
        String op = PacketType.mapOperation(id);
        if (op == null) {
            return;
        }
        Hashtable prop = msg.getProperties();

        String destination = (String)prop.get("JMQDestination");
        //all non-null op should have destination
        if (destination == null) { 
            throw new BrokerException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,
               "checkPermission() no JMQDestination"));
        }

        Integer dtype = (Integer)prop.get("JMQDestType");
        if (dtype == null) {
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.X_INTERNAL_EXCEPTION,
                "checkPermission() no JMQDestType"));
        }
        int destTypeInt = dtype.intValue();

        checkPermission(id, op, destination, destTypeInt, con);

    }

    public void checkPermission(int id, String op, String destination, int destTypeInt, 
                                       IMQConnection con)
                            throws AccessControlException,
                                   BrokerException
    {
        //Temporary destination should return null
        String destTypeStr = DestType.queueOrTopic(destTypeInt);
        if (destTypeStr == null) { 
            return;
        }

        Service service  = con.getService();
        int serviceType = service.getServiceType();

        if (!checkIsNonAdminDest(con, service, serviceType, destination)) {
            return;
        }

        String acdestination = destination;

        //if autocreate false, return to normal path
        if (id == PacketType.CREATE_DESTINATION) {
            if (!checkForAutoCreate(destination, destTypeInt)) { 
                return;
            }
            DestinationUID duid = DestinationUID.getUID(destination, DestType.isQueue(destTypeInt));
            DestinationSpi[] ds = coreLifecycle.getDestination(con.getPartitionedStore(), duid);
            DestinationSpi d = ds[0];
            if (d != null && !d.isAutoCreated()) {
                return;
            }
            acdestination = null;
        } 
        checkPermission(con, service, serviceType,
                        op, acdestination, destTypeStr, destination);
        
        // audit logging for destination authorization
        Globals.getAuditSession().destinationAuth(con.getUserName(), con.remoteHostString(),destTypeStr, acdestination, op, true);
   }

    /**
     * @return true if need access control check on create
     *         false if no need access control check
     */
    private static boolean checkForAutoCreate(String destination, int destType) { 
        if (DestType.isQueue(destType)) {
            if (!GlobalProperties.getGlobalProperties().AUTOCREATE_QUEUE) {
                return false;
            }
        }
        else if (DestType.isTopic(destType)) {
            if (!GlobalProperties.getGlobalProperties().AUTOCREATE_TOPIC) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true destination is not JMQ_ADMIN_DEST
     *         false ADMIN service access JMQ_ADMIN_DEST
     * @exception non ADMIN service access JMQ_ADMIN_DEST
     * @exception restricted ADMIN service access non JMQ_ADMIN_DEST
     */
    private static boolean checkIsNonAdminDest(IMQConnection con,
                    Service service, int serviceType, String destination)
                            throws AccessControlException, BrokerException {

        if (!destination.equals(MessageType.JMQ_ADMIN_DEST)) {
            if (serviceType == ServiceType.ADMIN 
                && con.getAccessController().isRestrictedAdmin()) {
            String emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.X_RESTRICTED_ADMIN_NON_JMQADMINDEST,
                        destination, service.getName());
            Globals.getLogger().log(Logger.WARNING, emsg);
            throw new AccessControlException(emsg);
            }
            if (!destination.equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)) {
                 return true;
            }
        }
        /*
         * Protect JMQ_ADMIN_DEST to ADMIN service only
         * ADMIN service (when get here the connection has been
         * authenticated and service type connection access control
         * has been applied) should automatically to be allowed to
         * access JMQ_ADMIN_DEST
         */
        if (serviceType == ServiceType.ADMIN) {
            return false;
        }
        String name = "";
        Principal pp = con.getAccessController().getAuthenticatedName();
        if (pp!= null) {
            name = pp.getName();
        }
        String[] args = {name, service.getName(),
                         ServiceType.getServiceTypeString(serviceType)};
        String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_FORBIDDEN_JMQ_ADMIN_DEST, args);
        Globals.getLogger().log(Logger.WARNING, emsg);
        throw new AccessControlException(emsg);
    }

    /**
     * delegate to AccessController.checkDestinationPermission
     *
     * @param con connection
     * @param service
     * @param serviceType
     * @param op operation 
     * @param destination null if op = create otherwise = dest
     * @param destTypeStr 
     * @param dest the destination as JMQDestination property
     */
    private static void checkPermission(IMQConnection con, 
                                 Service service, 
                                 int serviceType,
                                 String op, 
                                 String destination,
                                 String destType,
                                 String dest) 
                                 throws AccessControlException {
        try {

        con.getAccessController().checkDestinationPermission(
                      service.getName(),
                      ServiceType.getServiceTypeString(serviceType),
                      op, destination, destType);

        } catch (AccessControlException e) {

        if (destination !=  null) {
        String[] args = {op, destType, destination};
        String emsg = Globals.getBrokerResources().getKString(
                         BrokerResources.W_DESTINATION_ACCESS_DENIED, args);
        Globals.getLogger().log(Logger.WARNING, emsg + " - " + e.getMessage(), e);
        }
        else { //AC_DESTCREATE
        String[] args = {op, destType, dest};
        String emsg = Globals.getBrokerResources().getKString(
                         BrokerResources.W_DESTINATION_CREATE_DENIED, args);
        Globals.getLogger().log(Logger.WARNING, emsg + " - " + e.getMessage(), e);
        }

        throw e;
        }
    }

    public void checkServiceRestriction(Packet msg, IMQConnection con,
                                        ErrHandler defhandler)
                                        throws BrokerException,
                                        IOException, ClassNotFoundException {

        Service service = con.getService();
        if (service.getServiceType() != ServiceType.NORMAL) {
            return;
        }
        int id = msg.getPacketType();
        if (id != PacketType.CREATE_DESTINATION && 
            id != PacketType.ADD_CONSUMER && id != PacketType.ADD_PRODUCER) {
            return;
        }
        ServiceRestriction[] srs = service.getServiceRestrictions();
        if (srs == null) return;
        ServiceRestriction sr = null;
        for (int i = 0; i < srs.length; i++) {
            sr = srs[i];  
            if (sr == ServiceRestriction.NO_SYNC_WITH_MASTERBROKER) {
                Hashtable prop = msg.getProperties();
                String dest = (String)prop.get("JMQDestination");
                int dtype = ((Integer)prop.get("JMQDestType")).intValue();
                if (id == PacketType.CREATE_DESTINATION) {
                    if (!checkForAutoCreate(dest, dtype)) return;
                    DestinationUID duid = DestinationUID.getUID(dest, DestType.isQueue(dtype));
                    DestinationSpi[] ds = coreLifecycle.getDestination(con.getPartitionedStore(), duid);
                    DestinationSpi d = ds[0];
                    if (d != null) return;
                    if (DestType.isQueue(dtype) && DestType.isTemporary(dtype)) return;
                    String[] args = {Thread.currentThread().getName(), 
                                     dest, service.toString(), sr.toString(true)};
                    String emsg = Globals.getBrokerResources().getKString(
                                  BrokerResources.X_SERVICE_RESTRICTION_AUTO_CREATE_DEST, args);
                    logger.log(logger.WARNING, emsg);
                    waitForMasterBrokerSync(con, msg, emsg, emsg, defhandler);
                    return;
                } else if (DestType.isTopic(dtype)) {
                    if (id == PacketType.ADD_PRODUCER) {
                        String[] args = {Thread.currentThread().getName(),
                                         dest, service.toString(), sr.toString(true)};
                        String emsg = Globals.getBrokerResources().getKString(
                                      BrokerResources.X_SERVICE_RESTRICTION_TOPIC_PRODUCER, args);
                        logger.log(logger.WARNING, emsg);
                        waitForMasterBrokerSync(con, msg, emsg, emsg, defhandler);
                        return;
                       
                    } else {
                        String[] args = {Thread.currentThread().getName(), 
                                         dest, service.toString(), sr.toString(true)};
                        String emsg =  Globals.getBrokerResources().getKString(
                                       BrokerResources.X_SERVICE_RESTRICTION_TOPIC_CONSUMER, args);
                        logger.log(logger.WARNING, emsg);
                        waitForMasterBrokerSync(con, msg, emsg, emsg, defhandler);
                        return;
                    }
                }

            } else {
                throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Unknown service restriction "+sr+" on service "+service));
            }
        }
    }

    private boolean waitForMasterBrokerSync(IMQConnection con, Packet pkt,
                                            String retrymsg, String errmsg,
                                            ErrHandler defhandler)
                                            throws BrokerException {

        if (con.getClientProtocolVersion() < Connection.MQ450_PROTOCOL) { 
            throw new ServiceRestrictionException(errmsg, Status.UNAVAILABLE);
        }

        if (!MasterBrokerWaiter.addRequest(pkt, con, retrymsg, errmsg, defhandler)) {
            throw new ServiceRestrictionException(errmsg, Status.UNAVAILABLE);
        }
        
        throw new ServiceRestrictionWaitException(
              Globals.getBrokerResources().getKString(
                  BrokerResources.I_WAIT_FOR_SYNC_WITH_MASTERBROKER,
                  Thread.currentThread().getName(), "["+MasterBrokerWaiter.maxwait/1000+"]"),
                  Status.UNAVAILABLE);
   }

}


class MasterBrokerWaiter extends Thread 
implements ServiceRestrictionListener, ConnectionClosedListener
{
    static Logger logger = Globals.getLogger();

    static final long waitinterval = 15*1000L;
    static final int DEFAULT_MAXWAIT = 90; //seconds

    static long maxwait  = Globals.getConfig().getIntProperty(
           Globals.IMQ+".cluster.nowaitForMasterBrokerTimeoutInSeconds",
                            DEFAULT_MAXWAIT)*1000L;

    static MasterBrokerWaiter waiter = null;
    static ErrHandler defaultHandler = null;

    Object lock = new Object();
    ArrayList<Request> requests = new ArrayList<Request>();
    boolean notified = false;

    public void serviceRestrictionChanged(Service s) {
        synchronized(lock) {
            notified = true;
            lock.notifyAll();
        }
    }

    public void connectionClosed(Connection con) {
        if (PacketHandler.getDEBUG()) {
            logger.log(logger.INFO, "MasterBrokerWaiter.connectionClosed(): "+con);
        }
        synchronized(lock) {
            notified = true;
            lock.notifyAll();
        }
    }

    public void waitForNotify(long timeout, boolean log) throws InterruptedException {
        synchronized(lock) {
           if (!notified && !requests.isEmpty()) {
               if (log) {
                   logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                              BrokerResources.I_WAIT_FOR_SYNC_WITH_MASTERBROKER,
                              Thread.currentThread().getName(),
                              ""+(timeout/1000)+"["+maxwait/1000+"]"));
               }
               lock.wait(timeout);
           }
           notified = false;
        }
    }

    static class Request {
        PacketInfo pi = null;
        IMQConnection con = null;
        Service service = null;
        long totalwaited = 0L;
        String errmsg = "";
        String retrymsg = "";
        TimeoutTimerTask timertask = null;
        boolean timedout = false;
    }

    static class PacketInfo {
        boolean sendack;
        int pktype;
        long consumerID;
    }


    /**
     * @return true if the request is added for waiting 
     */
    public static boolean addRequest(Packet pkt, IMQConnection con,
                                     String retrymsg, String errmsg,
                                     ErrHandler defh) {
        if (maxwait == 0) {
            return false;
        }

        synchronized(MasterBrokerWaiter.class) {
            if (defaultHandler == null) {
                defaultHandler = defh;
            }
            boolean started = true;
            if (waiter  == null) { 
                waiter = new MasterBrokerWaiter();
                waiter.setDaemon(true);
                waiter.setName("MQ-mbwaiter");
                started = false;
            }
            waiter.addRequest(pkt, con, retrymsg, errmsg);
            if (!started) {
                waiter.start();
            }
            return true;
        }
    }

    public void requestTimedout(Request rq) {
        synchronized(lock) {
            rq.timedout = true;
            notified = true;
            lock.notifyAll();
        }
    }

    static class TimeoutTimerTask extends TimerTask { 
        MasterBrokerWaiter waiter = null;
        Request rq = null;
        public TimeoutTimerTask(MasterBrokerWaiter waiter, Request rq) {
            this.waiter = waiter;
            this.rq = rq;
        }
        public void run() {
            waiter.requestTimedout(rq);
        }
    }

    public void addRequest(Packet pkt, IMQConnection con,
                           String retrymsg, String errmsg) {
        Request rq = new Request();
        PacketInfo pi = new PacketInfo();
        pi.sendack = pkt.getSendAcknowledge();
        pi.pktype = pkt.getPacketType();
        pi.consumerID = pkt.getConsumerID();
        rq.pi = pi;
        rq.con = con;
        rq.service = con.getService();
        rq.retrymsg = retrymsg;
        rq.errmsg = errmsg;
        rq.service.addServiceRestrictionListener(this);
        rq.con.addConnectionClosedListener(this);

        synchronized(lock) {
            requests.add(rq);
            lock.notifyAll();
        }
        if (maxwait > 0) {
           rq.timertask = new TimeoutTimerTask(this, rq);
           Globals.getTimer().schedule(rq.timertask, maxwait);
        }
    }

    public void removeRequest(Request rq) {
        synchronized(lock) {
            requests.remove(rq);
        }
        rq.service.removeServiceRestrictionListener(this);
        rq.con.removeConnectionClosedListener(this);
        if (rq.timertask != null) {
            rq.timertask.cancel();
        }
    }

    public void sendRetry(ArrayList rqs) {
        Iterator itr = rqs.iterator();
        Request rq = null;
        while (itr.hasNext()) {
            rq = (Request)itr.next();
            defaultHandler.sendError(rq.con, 
                rq.pi.sendack, rq.pi.pktype, rq.pi.consumerID,
                rq.retrymsg, Status.RETRY);
        }
    }

    public void sendError(ArrayList rqs) {
        Iterator itr = rqs.iterator();
        Request rq = null;
        while (itr.hasNext()) {
            rq = (Request)itr.next();
            defaultHandler.sendError(rq.con,
                rq.pi.sendack, rq.pi.pktype, rq.pi.consumerID,
                rq.errmsg, Status.UNAVAILABLE);
        }
    }

    public void run() {

        long logtime = 0L;
        long currtime = System.currentTimeMillis();
        long timewaited = 0L;

        while (true) {
            synchronized(MasterBrokerWaiter.class) {
                synchronized(lock) { 
                    if (requests.isEmpty()) {
                        waiter = null; 
                        logger.log(logger.INFO, 
                            Globals.getBrokerResources().getKString(
                            BrokerResources.I_MASTER_BROKER_WAITER_THREAD_EXITS, 
                            "["+Thread.currentThread().getName()+"]"));
                       return;
                    }
                }
            }
            ArrayList retrys = new ArrayList();
            ArrayList errors = new ArrayList();
            Request[] rqs = null;
            synchronized(MasterBrokerWaiter.class) {
                synchronized(lock) {
                    rqs = requests.toArray(new Request[requests.size()]);
                }
            }
            if (rqs != null) {
                for (int i = 0; i < rqs.length; i++) {
                    if (rqs[i].con.getConnectionState() >= Connection.STATE_CLOSED) {
                        removeRequest(rqs[i]);
                    }
                    if (maxwait < 0) {
                        rqs[i].totalwaited = 0L;
                    } else if (rqs[i].totalwaited >= maxwait || rqs[i].timedout) {
                        errors.add(rqs[i]);
                        removeRequest(rqs[i]);
                    }
                    ServiceRestriction[] srs = rqs[i].service.getServiceRestrictions();
                    if (srs == null) { 
                        retrys.add(rqs[i]); 
                        removeRequest(rqs[i]);
                    } else {
                        boolean found = false;
                        for (int j = 0; j < srs.length; j++) {
                            if (srs[j] == ServiceRestriction.NO_SYNC_WITH_MASTERBROKER) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            retrys.add(rqs[i]);
                            removeRequest(rqs[i]);
                        }
                    }
                    rqs[i].con.updateAccessTime(true);
                }
            }
            sendRetry(retrys);
            sendError(errors);
            boolean log = false;
            if ((currtime - logtime) > waitinterval) {
                log = true;
                logtime =  currtime;
            }
            try {
                synchronized(lock) {
                    rqs = requests.toArray(new Request[requests.size()]);
                    waitForNotify(waitinterval, log);
                }
                long precurrtime = currtime;
                currtime = System.currentTimeMillis();
                timewaited = ((currtime - precurrtime) > 0 ?
                              (currtime - precurrtime):0);
                for (int i = 0; i < rqs.length; i++) {
                    rqs[i].totalwaited += timewaited;
                }

            } catch (InterruptedException ex) {
                 logger.log(Logger.INFO, Globals.getBrokerResources().getKString(
                     BrokerResources.I_WAIT_FOR_SYNC_WITH_MASTERBROKER_INTERRUPTED,
                     Thread.currentThread().getName()));

                 ArrayList all = new ArrayList(); 
                 synchronized(MasterBrokerWaiter.class) {
                     synchronized(lock) {
                         rqs = requests.toArray(new Request[requests.size()]);
                         for (int i = 0; i < rqs.length; i++) {
                             removeRequest(rqs[i]);
                             all.add(rqs[i]);
                         }
                     }
                     waiter = null;
                 }
                 sendError(all);
                 logger.log(logger.INFO, 
                     Globals.getBrokerResources().getKString(
                     BrokerResources.I_MASTER_BROKER_WAITER_THREAD_EXITS, 
                     "["+Thread.currentThread().getName()+"]"));
                 return;
            }
        } 
    }

}
