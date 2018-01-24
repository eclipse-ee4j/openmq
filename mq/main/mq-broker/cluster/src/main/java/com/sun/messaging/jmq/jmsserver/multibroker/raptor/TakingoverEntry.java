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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.io.Serializable;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.UniqueID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAMonitorServiceImpl;
import com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAClusterManagerImpl;
import com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected.BrokerAddressImpl;

public class TakingoverEntry {

    private static boolean DEBUG = false;

    protected String brokerID;
    protected UID storeSession;
    private boolean takeoverComplete = false;
    private Map xids = null;

    private long timeout = 0;
    private static int DEFAULT_TAKEOVER_PENDING_TIMEOUT = 
        2*HAMonitorServiceImpl.MONITOR_TIMEOUT_DEFAULT; //in seconds

    protected static int getTakeoverTimeout() {
         HAMonitorService hams = Globals.getHAMonitorService();
         if (hams == null) {
             return DEFAULT_TAKEOVER_PENDING_TIMEOUT;
         }
         int to =  (2*hams.getMonitorInterval());
         if (to < DEFAULT_TAKEOVER_PENDING_TIMEOUT) { 
            return DEFAULT_TAKEOVER_PENDING_TIMEOUT;
         }
         return to;
    }

    private static class XidEntry {
        String brokerHost = null;
        UID brokerSession = null;
        long expire = 0L;

        public XidEntry(String brokerHost, UID brokerSession, boolean timedout) {
            this.brokerHost = brokerHost;
            this.brokerSession = brokerSession;
            this.expire = 0L;
            if (timedout) {
                this.expire = System.currentTimeMillis();
            }
        }
        public String toString() {
            return "brokerHost="+brokerHost+
                   ", brokerSession="+brokerSession+
                   ", expire="+expire;
        }
    }

    public String toString() {
        return ("brokerID="+brokerID+", storeSession="+storeSession);
    }

    protected String toLongString() {
        StringBuffer sb = new StringBuffer();
        sb.append("brokerID="+brokerID+", storeSession="+storeSession+
                  ", takeoverComplete="+takeoverComplete+
                  ", timeout="+timeout);
        ArrayList al = null; 
        synchronized(xids) {
            al = new ArrayList(xids.keySet());
        }
        sb.append(", xidsSize="+al.size());
        Iterator itr = al.iterator();
        while (itr.hasNext()) {
            Long xid = (Long)itr.next();
            XidEntry xe = (XidEntry)xids.get(xid); 
            sb.append("\nxid - ").append(xid).append(": ").append(xe);
        }
        return "["+sb.toString()+"]";
    }

    private static class ExpireComparator implements Comparator, Serializable {
        public int compare(Object o1, Object o2) {
            XidEntry x1 = (XidEntry)o1;
            XidEntry x2 = (XidEntry)o2;
            return (Long.compare(x1.expire,x2.expire));
        }
        public int hashCode() {
            return super.hashCode();
        }

        public boolean equals(Object o) {
            return super.equals(o);
        }
    }

    private static class SessionComparator implements Comparator, Serializable {
        public int compare(Object o1, Object o2) {
            XidEntry x1 = (XidEntry)o1;
            XidEntry x2 = (XidEntry)o2;
            return Long.compare(x1.brokerSession.getTimestamp(),x2.brokerSession.getTimestamp());
        }
        public int hashCode() {
            return super.hashCode();
        }

        public boolean equals(Object o) {
            return super.equals(o);
        }
    }

    //only used to do lookup
    protected TakingoverEntry(String brokerID, UID storeSession) {
        this(brokerID, storeSession, 0);
    }

    private TakingoverEntry(String brokerID, UID storeSession, int timeout) {
        this.brokerID = brokerID;
        this.storeSession = storeSession;
        this.timeout = timeout * 1000L;
        xids = Collections.synchronizedMap(new LinkedHashMap());
    }

    //caller holding takingoverBrokers lock
    private synchronized boolean addXid(Long xid, String brokerHost, 
                                       UID brokerSession, boolean timedout) {
        if (xid == null) {
            return false;
        }
        XidEntry x = (XidEntry)xids.get(xid);
        if (x != null)  {
            if (timedout) {
                x.expire = System.currentTimeMillis();
            }
            return false;
        }
        XidEntry xe = new XidEntry(brokerHost, brokerSession, timedout);
        xids.put(xid, xe);
        return true;
    }

    protected synchronized boolean isTakeoverTarget(BrokerAddress ba) {
        if (!ba.getBrokerID().equals(brokerID) || 
            !ba.getStoreSessionUID().equals(storeSession)) {
            return false;
        }
        if (takeoverComplete) {
            return true;
        }
        if (xids.size() == 0) {
            return false; 
        }

        long expireTime = 0;
        Collection c = xids.values();
        ArrayList l = new ArrayList(c);
        Collections.sort(l, new ExpireComparator());
        expireTime  = ((XidEntry)l.get(0)).expire;
        if (expireTime != 0) {
            expireTime  = ((XidEntry)l.get(l.size()-1)).expire;
        }
        if (expireTime == 0) {
            return true;
        }
        if (System.currentTimeMillis() <= expireTime) {
            return true;
        }
        if (Globals.getHAMonitorService().isTakingoverTarget(
                    ba.getBrokerID(), ba.getStoreSessionUID())) {
            return true;
        }

        ArrayList sl = new ArrayList();
        XidEntry x = null;
        Iterator itr = l.iterator();
        while (itr.hasNext()) {
            x = (XidEntry)itr.next();
            if (x.brokerHost.equals(
                ((BrokerMQAddress)ba.getMQAddress()).getHost().getHostAddress())) {
                sl.add(x);
            }
        }
        if (sl.size() == 0) {
            return !ifOwnStoreSession(ba);
        }

        Collections.sort(sl, new SessionComparator());
        if (ba.getBrokerSessionUID().getTimestamp() <= 
            ((XidEntry)sl.get(sl.size()-1)).brokerSession.getTimestamp()) {
            return true;
        }
        return !ifOwnStoreSession(ba);
    }

    private boolean ifOwnStoreSession(BrokerAddress ba) {

        try {
            if (!Globals.getSFSHAEnabled()) {
                return Globals.getStore().ifOwnStoreSession(
                       ba.getStoreSessionUID().longValue(), ba.getBrokerID());
            }
            HAClusterManagerImpl cm = (HAClusterManagerImpl)Globals.getClusterManager();
            String owner = cm.lookupStoreSessionOwner(ba.getStoreSessionUID());
            if (owner != null && owner.equals(ba.getBrokerID())) {
                return true;
            }

        } catch (Exception e) {
            Globals.getLogger().log(Logger.WARNING, e.getMessage(), e);
        }

        return false;
    }

    protected synchronized void preTakeoverDone(Long xid) {
         XidEntry x = (XidEntry)xids.get(xid);
         if (x == null) {
             return;
         }
         if (x.expire != 0L) {
             return;
         }
         x.expire = System.currentTimeMillis() + timeout;
    }

    protected synchronized boolean takeoverComplete() {
        boolean ret = takeoverComplete;
        takeoverComplete = true;
        return ret;
    }

    //caller holding takingoverBrokers lock
    protected synchronized boolean takeoverAbort(Long xid) {
        XidEntry x = (XidEntry)xids.remove(xid);
        if (x != null) {
            x.expire = System.currentTimeMillis();
        }
        return (xids.size() == 0); 
    }

    //caller must in synchronized this block
    /*
    private XidEntry getLastNotExpiredXidEntry() {
        if (xids.size() == 0) {
            return null;
        }
        Collection c = xids.values();
        ArrayList l = new ArrayList(c);
        Collections.sort(l, new ExpireComparator());
        long expireTime  = ((XidEntry)l.get(0)).expire;
        XidEntry x  = (XidEntry)l.get(l.size()-1);
        if (expireTime != 0L) {
            expireTime  = x.expire;
        }
        if (expireTime == 0L || System.currentTimeMillis() <= expireTime) {
            return x;
        }
        return null;
    }
    */

    protected synchronized GPacket[] getNotificationGPackets() {
        ArrayList gps =  new ArrayList();
        ClusterTakeoverInfo cti = null;
        if (takeoverComplete) {
            cti = ClusterTakeoverInfo.newInstance(brokerID, storeSession); 
            try {
            gps.add(cti.getGPacket(ProtocolGlobals.G_TAKEOVER_COMPLETE));
            } catch (BrokerException e) {/* Ignore */}
            return (GPacket[])gps.toArray(new GPacket[gps.size()]);
        } 
        Long xid = null;
        XidEntry x = null;
        Map<String, List<XidEntry>> hosts = 
            new LinkedHashMap<String, List<XidEntry>>();
        Iterator itr = xids.keySet().iterator();
        while (itr.hasNext()) {
            xid = (Long)itr.next();
            x = (XidEntry)xids.get(xid);
            boolean timedout = false;
            if (x.expire != 0L && System.currentTimeMillis() >= x.expire) { 
                if (DEBUG) {
                    Globals.getLogger().log(Logger.INFO, 
                    "TakeingoverEntry.getNotificationGPacket(): ignore expired entry: "+x);
                }
                continue;
            }
            List<XidEntry> hostl =  hosts.get(x.brokerHost);
            if (hostl == null) {
                hostl = new ArrayList<XidEntry>();
                hosts.put(x.brokerHost, hostl);
            }
            hostl.add(x);
        }
        Iterator<List<XidEntry>> itr1 = hosts.values().iterator();
        List<XidEntry> hostl = null;
        while (itr1.hasNext()) {
            hostl = itr1.next();
            if (hostl.size() == 0) {
                continue;
            }
            Collections.sort(hostl, new SessionComparator());
            x = hostl.get(hostl.size()-1);
            cti = ClusterTakeoverInfo.newInstance(brokerID, 
                  storeSession, x.brokerHost, x.brokerSession, xid, false, false); 
            try {
                gps.add(cti.getGPacket(ProtocolGlobals.G_TAKEOVER_PENDING));
            } catch (BrokerException e) {/* Ignore */}
        }
        return (GPacket[])gps.toArray(new GPacket[gps.size()]);
    }

    protected synchronized GPacket getNotificationGPacket(BrokerAddress ba) {
        if (!ba.getBrokerID().equals(brokerID) ||
            !ba.getStoreSessionUID().equals(storeSession)) {
            return null;
        }
        ClusterTakeoverInfo cti = null;
        if (takeoverComplete) {
            cti = ClusterTakeoverInfo.newInstance(brokerID, storeSession); 
            try {
            return cti.getGPacket(ProtocolGlobals.G_TAKEOVER_COMPLETE);
            } catch (BrokerException e) {/* Ignore */}
            return null;
        }
        Long xid = null;
        XidEntry x = null;
        ArrayList<XidEntry> entries = new ArrayList<XidEntry>();
        Iterator itr = xids.keySet().iterator();
        while (itr.hasNext()) {
            xid = (Long)itr.next();
            x = (XidEntry)xids.get(xid);
            if (x.brokerHost.equals(
                ((BrokerMQAddress)ba.getMQAddress()).getHost().getHostAddress())) {
                if (x.expire != 0L && System.currentTimeMillis() >= x.expire) {
                    if (DEBUG) {
                    Globals.getLogger().log(Logger.INFO, 
                    "TakeingoverEntry.getNotificationGPacket("+ba+"): ignore expired entry: "+x);
                    }
                    continue;
                }
                entries.add(x);
            }
        }
        if (entries.size() > 0) {
            Collections.sort(entries, new SessionComparator());
            x = entries.get(entries.size()-1);
            if (DEBUG) {
                Globals.getLogger().log(Logger.INFO, 
                "TakeingoverEntry.getNotificationGPacket("+ba+"): select entry "+
                 x.toString()+" from "+entries.size()+" entries");
            }
            cti = ClusterTakeoverInfo.newInstance(brokerID, storeSession,
                               x.brokerHost, x.brokerSession, xid, false);
            try {
                return cti.getGPacket(ProtocolGlobals.G_TAKEOVER_PENDING);
            } catch (BrokerException e) {/* Ignore */}
        }
        return null;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof TakingoverEntry)) {
            return false;
        }
        TakingoverEntry toe = (TakingoverEntry)obj;
        return brokerID.equals(toe.brokerID) && (storeSession.equals(toe.storeSession));
    }

    public int hashCode() {
        return brokerID.hashCode() + (int)(storeSession.longValue() ^ (storeSession.longValue() >>> 32));
    }

    protected static TakingoverEntry addTakingoverEntry(
        Map<TakingoverEntry, TakingoverEntry> takingoverBrokers, 
        ClusterTakeoverInfo cti) {

        boolean exist = false;
        TakingoverEntry toe = new TakingoverEntry(cti.getBrokerID(), cti.getStoreSession(), getTakeoverTimeout());
        synchronized(takingoverBrokers) {
            TakingoverEntry v = (TakingoverEntry)takingoverBrokers.get(toe);
            if (v != null) {
                toe = v;
            } else {
                takingoverBrokers.put(toe, toe);
            }
            if (toe.addXid(cti.getXid(), cti.getBrokerHost(), 
                    cti.getBrokerSession(), cti.isTimedout())) {
                return toe;
            }
        }
        return null;
    }

    protected static void removeTakingoverEntry(
        Map<TakingoverEntry, TakingoverEntry> takingoverBrokers, 
        ClusterTakeoverInfo cti) {

        TakingoverEntry toe = new TakingoverEntry(cti.getBrokerID(), cti.getStoreSession());
        synchronized(takingoverBrokers) {
            TakingoverEntry v = (TakingoverEntry)takingoverBrokers.get(toe);
            if (v != null) {
                if (v.takeoverAbort(cti.getXid())) {
                    takingoverBrokers.remove(toe);
                }
            }
        }
    }

    protected static TakingoverEntry takeoverComplete(
        Map<TakingoverEntry, TakingoverEntry> takingoverBrokers, 
        ClusterTakeoverInfo cti) {

        synchronized(takingoverBrokers) {
            TakingoverEntry toe = (TakingoverEntry)takingoverBrokers.get(
                                   new TakingoverEntry(cti.getBrokerID(), cti.getStoreSession())); 
            if (toe == null) {
                toe = new TakingoverEntry(cti.getBrokerID(), cti.getStoreSession(), getTakeoverTimeout());
                takingoverBrokers.put(toe, toe);
            }
            if (toe.takeoverComplete()) return null;
            return toe;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<TakingoverEntry, TakingoverEntry> map = Collections.synchronizedMap(
            new LinkedHashMap<TakingoverEntry, TakingoverEntry>());
        String broker1 = "broker1", broker2 = "broker2", broker3 = "broker3"; 
        String host1 = "10.133.184.56", host2 = "10.133.184.56", host3 = "10.133.169.141";

        UID ssuid = new UID(); 
        UID buid = new UID(); 
        Long xid1 = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        ClusterTakeoverInfo cti1 = ClusterTakeoverInfo.newInstance(
                            broker2, ssuid, host2, buid, xid1, true);

        Thread.sleep(10);
        buid = new UID(); 
        Long xid2 = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        ClusterTakeoverInfo cti2 = ClusterTakeoverInfo.newInstance(
                            broker2, ssuid, host2, buid, xid2, true);

        Thread.sleep(10);
        buid = new UID(); 
        Long xid3 = Long.valueOf(UniqueID.generateID(UID.getPrefix()));
        ClusterTakeoverInfo cti3 = ClusterTakeoverInfo.newInstance(
                            broker2, ssuid, host2, buid, xid3, true);

        TakingoverEntry toe = TakingoverEntry.addTakingoverEntry(map, cti2);
        toe.preTakeoverDone(xid2);
        System.out.println("Added entry "+toe.toLongString());

        toe = TakingoverEntry.addTakingoverEntry(map, cti3);
        toe.preTakeoverDone(xid3);
        System.out.println("Added entry "+toe.toLongString());

        toe = TakingoverEntry.addTakingoverEntry(map, cti1);
        toe.preTakeoverDone(xid1);
        System.out.println("Added entry "+toe.toLongString());

        toe = new TakingoverEntry(broker2, ssuid);

        //ClusterTakeoverInfo cti = ClusterTakeoverInfo.newInstance(broker2, ssuid);
        //TakingoverEntry.takeoverComplete(map, cti);

        //Thread.sleep(60000);

        toe = map.get(toe);

        System.out.println("getNotificationGPackets() for "+toe.toLongString());
        GPacket[] gps = toe.getNotificationGPackets();
        for (int i = 0; i < gps.length; i++) {
            System.out.println("returned: "+ClusterTakeoverInfo.newInstance(gps[i]).toString());
        }

        BrokerAddress addr = new BrokerAddressImpl("joe-s10-3", broker2, 7677, true, broker2, ssuid, ssuid);
        System.out.println("getNotificationGPacket("+addr+") for "+toe.toLongString());
        GPacket gp = toe.getNotificationGPacket(addr);
        if (gp != null) {
            System.out.println("returned: "+ClusterTakeoverInfo.newInstance(gp).toString());
        } else {
            System.out.println("returned null");
        }
    }
}
