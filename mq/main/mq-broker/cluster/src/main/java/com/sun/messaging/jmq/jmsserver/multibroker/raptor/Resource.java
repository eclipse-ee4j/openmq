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

package com.sun.messaging.jmq.jmsserver.multibroker.raptor;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.core.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.multibroker.Cluster;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.handlers.*;

/**
 * Represents a resource to be locked. E.g. durable name, client ID, role of primary queue receiver.
 */
class Resource {
    private final Logger logger = Globals.getLogger();
    private String resId = null;
    private Object owner = null;
    private long timestamp;
    private long xid;
    private int lockState;
    private boolean shared;

    private int status;
    private HashMap<BrokerAddress, Object> recipients;
    private Cluster c = null;

    Resource(String resId, Cluster c) {
        this.resId = resId;
        this.c = c;
        timestamp = 0;
        xid = 0;

        recipients = new HashMap<>();
    }

    @Override
    public String toString() {
        return "[" + resId + ", owner=" + owner + ", " + getLockStateString(lockState) + ", timestamp=" + timestamp + ", shared=" + shared + ", xid=" + getXid()
                + "]";
    }

    public static String getLockStateString(int s) {
        if (s == ProtocolGlobals.G_RESOURCE_LOCKED) {
            return "RESOURCE_LOCKED";
        } else if (s == ProtocolGlobals.G_RESOURCE_LOCKING) {
            return "RESOURCE_LOCKING";
        } else {
            return "UNKNOWN";
        }
    }

    public String getResId() {
        return resId;
    }

    public Object getOwner() {
        return owner;
    }

    public void setOwner(Object owner) {
        this.owner = owner;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public synchronized long getXid() {
        return xid;
    }

    public int getLockState() {
        return lockState;
    }

    public void setLockState(int lockState) {
        this.lockState = lockState;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean getShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String showRecipients(PingHandler pingHandler, Cluster c) {
        ClusterManager cm = Globals.getClusterManager();
        StringBuilder ret = new StringBuilder();
        
        String brokerid;
        Iterator itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress baddr = (BrokerAddress) itr.next();
            pingHandler.enablePingLogging(baddr);
            c.enablePingLogging(baddr);
            if (Globals.getHAEnabled()) {
                brokerid = baddr.getBrokerID();
            } else {
                brokerid = cm.lookupBrokerID(baddr.getMQAddress());
            }
            if (brokerid != null) {
                ret.append("\n\t" + baddr.toString() + "[" + cm.getBroker(brokerid) + "]");
            } else {
                ret.append("\n\t" + baddr.toString());
            }
        }

        return ret.toString();
    }

    /**
     * Election protocol preparation. Remember the list of brokers that need to vote on this lock request.
     */
    public synchronized void prepareLockRequest(BrokerAddress[] brokerList, long xid) {
        recipients.clear();
        for (int i = 0; i < brokerList.length; i++) {
            recipients.put(brokerList[i], null);
        }
        this.xid = xid;
        status = ProtocolGlobals.G_LOCK_SUCCESS;
    }

    public synchronized void updateRecipients(Map<BrokerAddress, Object> map) {
        ArrayList<BrokerAddress> updates = null;
        Object o;
        BrokerAddress baddr;
        Iterator<BrokerAddress> itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            baddr = itr.next();
            o = map.get(baddr);
            if (o == null) {
                itr.remove();
            } else {
                if (updates == null) {
                    updates = new ArrayList<>(map.size());
                }
                updates.add(baddr);
            }
        }
        if (updates != null) {
            itr = updates.iterator();
            while (itr.hasNext()) {
                baddr = itr.next();
                recipients.put(baddr, map.get(baddr));
            }
        }
        notifyAll();
    }

    // call with synchronization
    private boolean needRestartLockRequest() {
        Iterator<Map.Entry<BrokerAddress, Object>> itr = recipients.entrySet().iterator();
        Map.Entry<BrokerAddress, Object> pair = null;
        while (itr.hasNext()) {
            pair = itr.next();
            if (c.isLinkModified(pair.getKey(), pair.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnreachable(int timeout) {
        boolean hasUnreachable = false;
        Iterator itr = recipients.keySet().iterator();
        while (itr.hasNext()) {
            BrokerAddress baddr = (BrokerAddress) itr.next();
            try {
                if (!c.isReachable(baddr, timeout)) {
                    logger.log(Logger.INFO, Globals.getBrokerResources().getKString(BrokerResources.I_CLUSTER_CLOSE_UNREACHABLE, baddr));
                    c.closeLink(baddr, true);
                    hasUnreachable = true;
                }
            } catch (IOException e) {
                logger.log(Logger.WARNING, Globals.getBrokerResources().getKString(BrokerResources.W_CLUSTER_CANNOT_CHECK_REACHABILITY, baddr, e.getMessage()));
            }
        }
        return hasUnreachable;
    }

    /**
     * Wait for the conclusion of election protocol.
     */
    public synchronized int waitForStatusChange(int timeout, boolean failOntimeout) {
        long waittime = timeout * 1000L;
        long endtime = System.currentTimeMillis() + waittime;

        boolean checkReachable = true;
        while (status == ProtocolGlobals.G_LOCK_SUCCESS && recipients.size() > 0) {
            try {
                wait(waittime);
            } catch (Exception e) {
            }

            if (status != ProtocolGlobals.G_LOCK_SUCCESS || recipients.size() == 0) {
                break;
            }
            long curtime = System.currentTimeMillis();
            if (curtime >= endtime) {
                if (!failOntimeout && checkReachable) {
                    if (hasUnreachable(timeout)) {
                        waittime = timeout * 1000L;
                        endtime = System.currentTimeMillis() + waittime;
                        checkReachable = false;
                        continue;
                    }
                }
                if (needRestartLockRequest()) {
                    return ProtocolGlobals.G_LOCK_TRY_AGAIN;
                }
                return ProtocolGlobals.G_LOCK_TIMEOUT;
            }

            waittime = endtime - curtime;
        }

        return status;
    }

    /**
     * Process an election protocol 'vote' from a broker.
     */
    public synchronized void consumeResponse(long xid, BrokerAddress sender, int response) {
        if (xid != this.xid) {
            return;
        }

        if (status != ProtocolGlobals.G_LOCK_SUCCESS) {
            return;
        }

        switch (response) {
        case ProtocolGlobals.G_LOCK_SUCCESS:
            recipients.remove(sender);
            break;

        case ProtocolGlobals.G_LOCK_FAILURE:
        case ProtocolGlobals.G_LOCK_BACKOFF:
            status = response;
            break;
        }

        if (status != ProtocolGlobals.G_LOCK_SUCCESS || recipients.size() == 0) {
            notifyAll();
        }
    }

    /**
     * Forfeit an attempt to lock a resource.
     */
    public synchronized void impliedFailure() {
        status = ProtocolGlobals.G_LOCK_FAILURE;
        notifyAll();
    }

    public synchronized void brokerAdded(BrokerAddress broker) {
        status = ProtocolGlobals.G_LOCK_TRY_AGAIN;
        notifyAll();
    }

    public synchronized void brokerRemoved(BrokerAddress broker) {
        if (status != ProtocolGlobals.G_LOCK_SUCCESS) {
            return;
        }

        recipients.remove(broker);
        if (recipients.size() == 0) {
            notifyAll();
        }
    }
}

