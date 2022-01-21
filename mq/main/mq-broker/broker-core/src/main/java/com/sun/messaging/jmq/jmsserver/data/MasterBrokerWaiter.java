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

package com.sun.messaging.jmq.jmsserver.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TimerTask;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.Globals;

class MasterBrokerWaiter extends Thread implements ServiceRestrictionListener, ConnectionClosedListener {
    static Logger logger = Globals.getLogger();

    static final long waitinterval = 15 * 1000L;
    static final int DEFAULT_MAXWAIT = 90; // seconds

    static long maxwait = Globals.getConfig().getIntProperty(Globals.IMQ + ".cluster.nowaitForMasterBrokerTimeoutInSeconds", DEFAULT_MAXWAIT) * 1000L;

    static MasterBrokerWaiter waiter = null;
    static ErrHandler defaultHandler = null;

    Object lock = new Object();
    ArrayList<Request> requests = new ArrayList<>();
    boolean notified = false;

    @Override
    public void serviceRestrictionChanged(Service s) {
        synchronized (lock) {
            notified = true;
            lock.notifyAll();
        }
    }

    @Override
    public void connectionClosed(Connection con) {
        if (PacketHandler.getDEBUG()) {
            logger.log(logger.INFO, "MasterBrokerWaiter.connectionClosed(): " + con);
        }
        synchronized (lock) {
            notified = true;
            lock.notifyAll();
        }
    }

    public void waitForNotify(long timeout, boolean log) throws InterruptedException {
        synchronized (lock) {
            if (!notified && !requests.isEmpty()) {
                if (log) {
                    logger.log(Logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_WAIT_FOR_SYNC_WITH_MASTERBROKER,
                            Thread.currentThread().getName(), "" + (timeout / 1000) + "[" + maxwait / 1000 + "]"));
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
    public static boolean addRequest(Packet pkt, IMQConnection con, String retrymsg, String errmsg, ErrHandler defh) {
        if (maxwait == 0) {
            return false;
        }

        synchronized (MasterBrokerWaiter.class) {
            if (defaultHandler == null) {
                defaultHandler = defh;
            }
            boolean started = true;
            if (waiter == null) {
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
        synchronized (lock) {
            rq.timedout = true;
            notified = true;
            lock.notifyAll();
        }
    }

    static class TimeoutTimerTask extends TimerTask {
        MasterBrokerWaiter waiter = null;
        Request rq = null;

        TimeoutTimerTask(MasterBrokerWaiter waiter, Request rq) {
            this.waiter = waiter;
            this.rq = rq;
        }

        @Override
        public void run() {
            waiter.requestTimedout(rq);
        }
    }

    public void addRequest(Packet pkt, IMQConnection con, String retrymsg, String errmsg) {
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

        synchronized (lock) {
            requests.add(rq);
            lock.notifyAll();
        }
        if (maxwait > 0) {
            rq.timertask = new TimeoutTimerTask(this, rq);
            Globals.getTimer().schedule(rq.timertask, maxwait);
        }
    }

    public void removeRequest(Request rq) {
        synchronized (lock) {
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
            rq = (Request) itr.next();
            defaultHandler.sendError(rq.con, rq.pi.sendack, rq.pi.pktype, rq.pi.consumerID, rq.retrymsg, Status.RETRY);
        }
    }

    public void sendError(ArrayList rqs) {
        Iterator itr = rqs.iterator();
        Request rq = null;
        while (itr.hasNext()) {
            rq = (Request) itr.next();
            defaultHandler.sendError(rq.con, rq.pi.sendack, rq.pi.pktype, rq.pi.consumerID, rq.errmsg, Status.UNAVAILABLE);
        }
    }

    @Override
    public void run() {

        long logtime = 0L;
        long currtime = System.currentTimeMillis();
        long timewaited = 0L;

        while (true) {
            synchronized (MasterBrokerWaiter.class) {
                synchronized (lock) {
                    if (requests.isEmpty()) {
                        waiter = null;
                        logger.log(logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_MASTER_BROKER_WAITER_THREAD_EXITS,
                                "[" + Thread.currentThread().getName() + "]"));
                        return;
                    }
                }
            }
            ArrayList retrys = new ArrayList();
            ArrayList errors = new ArrayList();
            Request[] rqs = null;
            synchronized (MasterBrokerWaiter.class) {
                synchronized (lock) {
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
                logtime = currtime;
            }
            try {
                synchronized (lock) {
                    rqs = requests.toArray(new Request[requests.size()]);
                    waitForNotify(waitinterval, log);
                }
                long precurrtime = currtime;
                currtime = System.currentTimeMillis();
                timewaited = ((currtime - precurrtime) > 0 ? (currtime - precurrtime) : 0);
                for (int i = 0; i < rqs.length; i++) {
                    rqs[i].totalwaited += timewaited;
                }

            } catch (InterruptedException ex) {
                logger.log(Logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_WAIT_FOR_SYNC_WITH_MASTERBROKER_INTERRUPTED,
                        Thread.currentThread().getName()));

                ArrayList all = new ArrayList();
                synchronized (MasterBrokerWaiter.class) {
                    synchronized (lock) {
                        rqs = requests.toArray(new Request[requests.size()]);
                        for (int i = 0; i < rqs.length; i++) {
                            removeRequest(rqs[i]);
                            all.add(rqs[i]);
                        }
                    }
                    waiter = null;
                }
                sendError(all);
                logger.log(logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_MASTER_BROKER_WAITER_THREAD_EXITS,
                        "[" + Thread.currentThread().getName() + "]"));
                return;
            }
        }
    }

}
