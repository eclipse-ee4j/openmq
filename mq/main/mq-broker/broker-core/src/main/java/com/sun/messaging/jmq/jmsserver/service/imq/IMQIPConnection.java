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
 * @(#)IMQIPConnection.java	1.10 11/06/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import java.net.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.io.*;

import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

import java.security.Principal;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;

import com.sun.messaging.jmq.util.admin.ConnectionInfo;

import com.sun.messaging.jmq.util.timer.MQTimer;

import com.sun.messaging.jmq.util.GoodbyeReason;

import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.memory.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.BrokerShutdownRuntimeException;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.util.lists.*;



public class IMQIPConnection extends IMQBasicConnection 
        implements Operation, MemoryCallback
{

    public static final boolean expectPingReply = false;

    /**
     * a value of -1 or 0 turns off feature
     */
    public static final int closeInterval = Globals.getConfig().getIntProperty(Globals.IMQ + 
              ".ping.close.interval", 5);

    public static final boolean enablePingReply = Globals.getConfig().getBooleanProperty(Globals.IMQ + 
              ".ping.reply.enable", true);

    protected int ctrlPktsToConsumer = 0;

    boolean STREAMS = true; 
    boolean BLOCKING = false; 

    protected Set tmpDestinations = Collections.synchronizedSet(new HashSet());

    protected Object timerLock = new Object();

    public int packetVersion = NO_VERSION;

    ConvertPacket convertPkt = null; // for old to new pkts

    public static final int DEFAULT_INTERVAL = 180; // 180sec

   // XXX-CODE TO OVERRIDE BEHAVIOR OF PACKETS

   // to override the type of packet ... 
   //  jmq.packet.[ctrl|read|fill].override = [direct/heap/unset]
   //        direct -> always use direct packets
   //        heap -> always use heap packets
   //        unset -> current behavior
   //  fill is the "waitingForWrite packet"

    private static boolean OVERRIDE_CTRL_PACKET = false;
    private static boolean OVERRIDE_READ_PACKET = false;
    private static boolean OVERRIDE_FILL_PACKET = false;
    private static boolean O_CTRL_USE_DIRECT = false;
    private static boolean O_READ_USE_DIRECT = false;
    private static boolean O_FILL_USE_DIRECT = false;

    static {
        try {
            String ctrlover = Globals.getConfig().getProperty(
            Globals.IMQ + ".packet.ctrl.override");
            if (ctrlover != null && ctrlover.trim().length() > 0) {
                ctrlover = ctrlover.trim();
                if (ctrlover.equalsIgnoreCase("direct")) {
                    OVERRIDE_CTRL_PACKET = true;
                    O_CTRL_USE_DIRECT = true;
                    Globals.getLogger().log(Logger.DEBUG,
                        "DEBUG: Overriding ctrl message "
                        + " packet behavior to DIRECT BUFFERS");
                } else if (ctrlover.equalsIgnoreCase("heap")) {
                    OVERRIDE_CTRL_PACKET = true;
                    O_CTRL_USE_DIRECT = false;
                    Globals.getLogger().log(Logger.DEBUG,
                        "DEBUG: Overriding ctrl message "
                        + " packet behavior to HEAP BUFFERS");
                } else {
                    Globals.getLogger().log(Logger.ERROR, 
                        "DEBUG: Can not determine behavior from "
                        +" imq.packet.ctrl.override = "+ctrlover
                        +"  not one of the valid setting [heap,direct]");
                }
            }
            String readover = Globals.getConfig().getProperty(
                Globals.IMQ + ".packet.read.override");
            if (readover != null && readover.trim().length() > 0) {
                readover = readover.trim();
                if (readover.equalsIgnoreCase("direct")) {
                    OVERRIDE_READ_PACKET = true;
                    O_READ_USE_DIRECT = true;
                    Globals.getLogger().log(Logger.DEBUG,
                        "DEBUG: Overriding read packet"
                        + " behavior to DIRECT BUFFERS");
                } else if (readover.equalsIgnoreCase("heap")) {
                    OVERRIDE_READ_PACKET = true;
                    O_READ_USE_DIRECT = false;
                   Globals.getLogger().log(Logger.DEBUG,
                       "DEBUG: Overriding read packet "
                       + " behavior to HEAP BUFFERS");
                } else {
                    Globals.getLogger().log(Logger.ERROR, 
                        "DEBUG: Can not determine behavior from "
                        + " imq.packet.read.override = "+readover
                        +"  not one of the valid setting [heap,direct]");
                }
            }

            String fillover = Globals.getConfig().getProperty(
            Globals.IMQ + ".packet.fill.override");
            if (fillover != null && fillover.trim().length() > 0) {
                fillover = fillover.trim();
                if (fillover.equalsIgnoreCase("direct")) {
                    OVERRIDE_FILL_PACKET = true;
                    O_FILL_USE_DIRECT = true;
                   Globals.getLogger().log(Logger.DEBUG,
                       "DEBUG: Overriding fill packet "
                       + " behavior to DIRECT BUFFERS");
                } else if (fillover.equalsIgnoreCase("heap")) {
                    OVERRIDE_FILL_PACKET = true;
                    O_FILL_USE_DIRECT = false;
                    Globals.getLogger().log(Logger.DEBUG,
                            "DEBUG: Overriding fill packet "
                            + " behavior to HEAP BUFFERS");
                } else {
                    Globals.getLogger().log(Logger.ERROR, 
                      "DEBUG: Can not determine "
                       + " behavior from jmq.packet.fill.override = "
                       +fillover
                       +"  not one of the valid setting [heap,direct]");
                }
            }
        } catch (Exception ex) {
            Globals.getLogger().logStack(Logger.DEBUG,
                "DEBUG: error setting overrides", ex);
        }
    }

    byte[] empty = {0};

    Object ctrlEL = null;

    /**
     * Ping class
     */
    static class StateWatcher extends TimerTask {
        private int state;
        IMQIPConnection con = null;

        public StateWatcher(int state, IMQIPConnection con) {
            super();
            this.state = state;
            this.con = con;
        }
        public boolean cancel() {
            con = null;
            return super.cancel();
        }

        public void run() {
            con.checkConnection(state);
        }
    }    

    private StateWatcher stateWatcher = null;
    private long interval = DEFAULT_INTERVAL;


    protected ProtocolStreams ps = null;

    protected SocketChannel channel;
    protected InputStream is = null;
    protected OutputStream os = null;

    protected boolean critical = false;

    private boolean flush = false;
    private boolean flushCtrl = false;
    private Object flushLock = new Object();
    private Object flushCtrlLock = new Object();
    private boolean flushCritical = false;
    private boolean lockCritical = false;

    private OperationRunnable read_assigned = null;
    private OperationRunnable write_assigned = null;

    /**
     * constructor
     */


    public IMQIPConnection(Service svc, ProtocolStreams ps, 
             PacketRouter router) 
        throws IOException, BrokerException
    {
        super(svc, router);
        this.ps = ps;

        InetAddress ia = getRemoteAddress();
        if (ia != null) {
            this.setRemoteIP(ia.getAddress());
        }
        if (ps != null) {
            STREAMS = (ps.getChannel() == null);
            BLOCKING = ps.getBlocking();
            channel = (SocketChannel)ps.getChannel();
            is = ps.getInputStream();
            os = ps.getOutputStream();
        }
        accessController = AccessController.getInstance(svc.getName(),
                                                        svc.getServiceType());
        if (ia != null) {
            accessController.setClientIP(ia.getHostAddress());
        }

        // LKS - XXX we want notification, but it doesnt need to have
        // filters -> could created an orderedNLset

        this.control = new NFLPriorityFifoSet();
        ctrlEL = this.control.addEventListener(this,EventType.EMPTY,  null);

        setConnectionState(Connection.STATE_CONNECTED);
        waitingWritePkt = new Packet(OVERRIDE_FILL_PACKET 
                ? O_FILL_USE_DIRECT : !STREAMS);

        if (!isAdminConnection() && Globals.getMemManager() != null)
            Globals.getMemManager().registerMemoryCallback(this);
    }

    protected InetAddress getRemoteAddress() {
        if (ps != null) {
            return ps.getRemoteAddress();
        }
        return null;
    }

// -------------------------------------------------------------------------
//   General connection information and metrics
// -------------------------------------------------------------------------
    public void dumpState() {
	super.dumpState();

        logger.log(Logger.INFO,
                "\tcontrol = " + control.size());
        logger.log(Logger.INFO,
                "\tread_assigned = " + read_assigned);
        logger.log(Logger.INFO,
                "\twrite_assigned = " + write_assigned);
        if (ninfo != null)
            ninfo.dumpState();
    }

    public int getLocalPort() {
        if (ps == null) return 0;
        return ps.getLocalPort();
    }

    /** 
     * The debug state of this object
     */
    public synchronized Hashtable getDebugState() {
        Hashtable ht = super.getDebugState();
        ht.put("pkts[TOTAL](in,out) ", "("+msgsIn + "," + (ctrlPktsToConsumer + 
                     msgsToConsumer) + ")");
        for (int i=0; i < pktsIn.length; i ++) {
            if (pktsIn[i] == 0 && pktsOut[i] == 0)
                continue;
            ht.put("pkts[" + PacketType.getString(i) + "] (in,out)",
                   "("+pktsIn[i] + "," + pktsOut[i] + ")");
        }
        ht.put("ctrlPktsToConsumer", String.valueOf(ctrlPktsToConsumer));
        ht.put("critical", String.valueOf(critical));
        ht.put("controlSize", String.valueOf(control.size()));
        if (control.size() > 0) {
             Vector v = new Vector();
             Iterator itr = control.iterator();
             while (itr.hasNext()) {
                Packet p = (Packet)itr.next();
                v.add(p.toString());
             }
             ht.put("control", v);
        }
        if (ps != null)
            ht.put("transport", ps.getDebugState());
        return ht;
    }


    public Vector getDebugMessages(boolean full) {
        Vector ht = new Vector();
        synchronized(control) {
            Iterator itr = control.iterator();
            while (itr.hasNext()) {
                PacketReference pr = (PacketReference)itr.next();
                ht.add((full ? pr.getPacket().dumpPacketString()
                   : pr.getPacket().toString()));
            }
        }
        return ht;
       
    }

    public ConnectionInfo getConnectionInfo() {
	    coninfo = super.getConnectionInfo();
        coninfo.remPort = getRemotePort();
        return coninfo;
    }

 
    public boolean useDirectBuffers() {
        return (OVERRIDE_CTRL_PACKET ? O_CTRL_USE_DIRECT : !STREAMS);
        // return !STREAMS;
    }

// -------------------------------------------------------------------------
//   Basic Operation implementation
// -------------------------------------------------------------------------

    NotificationInfo ninfo = null;

    public synchronized AbstractSelectableChannel getChannel() {
        if (ps == null) 
            return null;
        return ps.getChannel();
    }

    public boolean canKill() {
        return !critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public boolean waitUntilDestroyed(long time) {
        long targettime = System.currentTimeMillis() + time;
        while (isValid() && System.currentTimeMillis() < targettime) {
            waitForWork(time);
        }
        return isValid();
    }

    Object releaseWaitLock = new Object();

    /**
     * called when the thread is not longer processing the
     * operation
     * this mask used when a thread is assigned or released,
     * which is returned from the notificationInfo object
     * (if any)
     */
    public synchronized void notifyRelease(
               OperationRunnable runner, int events) 
    {
        /* 
         * the thread is giving us UP ...
         */
        int release_events = 0;
        if ((events & SelectionKey.OP_WRITE) != 0 && runner == write_assigned) {
            release_events = release_events | SelectionKey.OP_WRITE;
            write_assigned = null;
        }
        if ((events & SelectionKey.OP_READ) != 0 && runner == read_assigned) {
            release_events = release_events | SelectionKey.OP_READ;
            read_assigned = null;
        }

        if (ninfo != null && release_events != 0) {
            ninfo.released(this, release_events);
        }
        notifyAll();
        return;
    }

    public synchronized void waitForRelease(long timeout) {
        long waitt = 5000;
        while (read_assigned != null || write_assigned != null) { 
            if (timeout <= 0) {
                Globals.getLogger().log(Logger.WARNING, 
                "Timeout in waiting for runnable threads release in "+this);
                return;
            }
            Globals.getLogger().log(Logger.INFO, "Waiting for runnable threads release in "+this);
            if (timeout < waitt) waitt = timeout;
            try {
                wait(waitt); 
            } catch (InterruptedException e) {
                Globals.getLogger().log(Logger.WARNING, 
                "Interrupted in waiting for runnable threads release in "+this);
                return;
            }
            timeout -= waitt;
        }
    }

    public synchronized void clearAssigned() {
        read_assigned = null;
        write_assigned = null;
    }

    public synchronized OperationRunnable getReadRunnable() {
        return read_assigned;
    }

    public synchronized OperationRunnable getWriteRunnable() {
        return write_assigned;
    }

    public synchronized void threadAssigned(
            OperationRunnable runner, int events) 
        throws IllegalAccessException 
    {
        int release_events = 0;
        if ((events & SelectionKey.OP_WRITE) != 0) {
            if (write_assigned != null)  {
                 // we havent released yet ... its a timing thing
                 release_events = release_events | SelectionKey.OP_WRITE;
            }
            write_assigned = runner;
         }
         if ((events & SelectionKey.OP_READ) != 0) {
            if (read_assigned != null)  {
                 // we havent released yet ... its a timing thing
                release_events = release_events | SelectionKey.OP_READ;
            }
            read_assigned = runner;
         }
 
         if (ninfo != null) {
            if (release_events != 0)
                ninfo.released(this, release_events);
            ninfo.assigned(this, events);
         }
    }

    public void attach(NotificationInfo obj) {
        ninfo = obj;
    }

    public NotificationInfo attachment() {
        return ninfo;
    }

    /*
    private String getKeyString(int events) {
        String str = "";
        if ((events & SelectionKey.OP_WRITE) > 0) {
            str += " WRITE ";
        }
        if ((events & SelectionKey.OP_READ) > 0) {
            str += " READ ";
        }
        return str;
    }
    */


    public boolean process(int events, boolean wait) 
        throws IOException
    {
         boolean didSomething = false;
         boolean processedLastIteration = true;

         int readcount = 0;
         int writecount = 0;

         try {
             while (processedLastIteration) {
                 // process all writes
                 processedLastIteration = false;
                 if ((events & SelectionKey.OP_WRITE) != 0) {
                     while (true) {
    
                         if (writeData(wait) != 
                            Operation.PROCESS_PACKETS_REMAINING) 
                         {
                             // wasnt able to write anymore
                             // break out of the loop
                             break;
                         } 
                         processedLastIteration = true;
                         writecount++;
                     }
                 }
                 // process one write
                 if ((events & SelectionKey.OP_READ) != 0) {
                     int returnval = readData();
                     switch (returnval) {
                         case Operation.PROCESS_PACKETS_REMAINING:
                             processedLastIteration = true;
                         case Operation.PROCESS_PACKETS_COMPLETE:
                         case Operation.PROCESS_WRITE_INCOMPLETE:
                         default:
                             break;
                     }
                     readcount++;
                 }
                 didSomething |= processedLastIteration;
              }
         } catch (Throwable t) {
             handleWriteException(t);
         }

         return !didSomething;
    }

    public String getRemoteConnectionString() {
        if (remoteConString != null)
            return remoteConString;

        boolean userset = false;

        String remotePortString = "???";
        String userString = "???";

        if (state >= Connection.STATE_AUTHENTICATED) {
            try {
                Principal principal = getAuthenticatedName();
                if (principal != null) {
                    userString = principal.getName();
                    userset = true;
                }
            } catch (BrokerException e) { 
                if (IMQBasicConnection.DEBUG)
                    logger.log(Logger.DEBUG,"Exception getting authentication name "
                        + conId, e );
                        
            }
        }

        remotePortString = Integer.toString(getRemotePort());

        String retstr = userString + "@" +
            IPAddress.rawIPToString(getRemoteIP(), true, true) + ":" +
            remotePortString;
        if (userset) remoteConString = retstr;
        return retstr;
    }

    protected int getRemotePort() {
        return (ps == null ? 0 : ps.getRemotePort());
    }

    String localsvcstring = null;
    protected String localServiceString() {
        if (localsvcstring != null)
            return localsvcstring;
        String localPortString = "???";
        localPortString = Integer.toString(getLocalPort());
        localsvcstring = service.getName() + ":" + localPortString;
        return localsvcstring;
    }

// -------------------------------------------------------------------------
//   Basic Connection Management
// -------------------------------------------------------------------------

    public synchronized void closeConnection(
            boolean force, int reason, String reasonStr) 
    { 

        if (state >= Connection.STATE_CLOSED)  {
             logger.log(logger.DEBUG,"Requested close of already closed connection:"
                    + this);
             return;
        }
               
        stopConnection();
        if (Globals.getMemManager() != null)
             Globals.getMemManager().removeMemoryCallback(this);
        if (force) { // we are shutting it down, say goodbye
            sayGoodbye(false, reason, reasonStr);
            flushControl(1000);
        }
        
        // CR 6798464: Don't mark connection as closed until we've flushed the queue and sent the GOODBYE
        state = Connection.STATE_CLOSED;
        notifyConnectionClosed();
        
        // clean up everything 
        this.control.removeEventListener(ctrlEL);
        cleanup(reason == GoodbyeReason.SHUTDOWN_BKR);
        // OK - we are done with the flush, we dont need to be
        // notified anymore
        if (ninfo != null)
            ninfo.destroy(reasonStr);

        try {
            closeProtocolStream();
        } catch (IOException ex) {
            // its OK if we cant close the socket ..,
            // aother thread may have already done it
            // ignore
        }

        if (reason == GoodbyeReason.SHUTDOWN_BKR) {
            cleanupConnection(); // OK if we do it twice
        } else {
            cleanup(false);
        }
    }

    protected void closeProtocolStream() throws IOException {
        if (ps != null)  {
            ps.close(); // close socket
        }
        ps = null;
    }

    protected void cleanupControlPackets(boolean shutdown) {
        while (!control.isEmpty()) {
            Packet p = (Packet) control.removeNext();
            if (p == null) continue;
            p.destroy();
        }
    }


    int destroyRecurse = 0;
    /**
     * destroy the connection to the client
     * clearing out messages, etc
     */
    public void destroyConnection(boolean force, int reason, String reasonstr) { 
        int oldstate = 0;
        boolean destroyOK = false;
        try {

            synchronized (this) {
                oldstate = state;
                if (state >= Connection.STATE_DESTROYING)
                    return;
    
                if (state < Connection.STATE_CLOSED) {
                     closeConnection(force, reason, reasonstr);
                }
    
                setConnectionState(Connection.STATE_DESTROYING);
            }
            Globals.getConnectionManager().removeConnection(getConnectionUID(),
                   force, reason, reasonstr);
    
            if (accessController.isAuthenticated()) {
                accessController.logout();
            }

            // The connection is going away. Deposit our metric totals
            // with the metric manager
            MetricManager mm = Globals.getMetricManager();
            if (mm != null) {
                mm.depositTotals(service.getName(), counters);
            }

            // Clear, just in case we are called twice
            counters.reset();

            synchronized (timerLock) {

                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error destroying "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
            }

	    if (this.getDestroyReason()!= null){
		logConnectionInfo(true, this.getDestroyReason());
            } else {
            	logConnectionInfo(true, reasonstr);
            }

            setConnectionState(Connection.STATE_DESTROYED);
            destroyOK = true;
            wakeup();
        } finally {
            if (!destroyOK && reason != GoodbyeReason.SHUTDOWN_BKR 
                    &&  (Globals.getMemManager() == null 
                    || Globals.getMemManager().getCurrentLevel() > 0)) {

                state = oldstate;
                if (destroyRecurse < 2) {
                    destroyRecurse ++;
                    destroyConnection(force, reason, reasonstr);
                }
            } 
                
            // free the lock
            Globals.getClusterBroadcast().connectionClosed(
                getConnectionUID(), isAdminConnection());
        }
    }

    /**
     * sets the connection state 
     * @return false if connection being destroyed
     */
    public boolean setConnectionState(int state) { 
        synchronized (timerLock) {
            this.state = state;
            if (this.state >= Connection.STATE_CLOSED) {
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
                wakeup();
		return false;
            } else if (state == Connection.STATE_CONNECTED) {
                interval = Globals.getConfig().getLongProperty(
                   Globals.IMQ + ".authentication.client.response.timeout",
                   DEFAULT_INTERVAL);
                MQTimer timer = Globals.getTimer(true);
                stateWatcher = new StateWatcher(Connection.STATE_INITIALIZED, this);
                try {
                    timer.schedule(stateWatcher, interval*1000);
                } catch (IllegalStateException ex) {
                    logger.log(Logger.DEBUG,"InternalError: timer canceled ", ex);
                }

            } else if (state == Connection.STATE_INITIALIZED 
                   || state == Connection.STATE_AUTH_REQUESTED
                   || state == Connection.STATE_AUTH_RESPONSED) {
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
                // if next state not from client, return 
                if (state == Connection.STATE_INITIALIZED) {
                    return true;
                }
                if (state == Connection.STATE_AUTH_RESPONSED) {
                    return true;
                }

                MQTimer timer = Globals.getTimer(true);
                stateWatcher = new StateWatcher(
                        Connection.STATE_AUTH_RESPONSED, this);
                try {
                    timer.schedule(stateWatcher, interval*1000);
                } catch (IllegalStateException ex) {
                    logger.log(Logger.DEBUG,"InternalError: timer canceled ", ex);
                }
            } else if (state >= Connection.STATE_AUTHENTICATED 
                    || state == Connection.STATE_UNAVAILABLE) 
            {
                if (stateWatcher != null) {
                    try {
                        stateWatcher.cancel();
                    } catch (IllegalStateException ex) {
                        logger.log(Logger.DEBUG,"Error setting state on "+
                            " connection "  + this + " to state " +
                            state, ex);
                    }
                    stateWatcher = null;
                }
                if (state == Connection.STATE_AUTHENTICATED) {
                    logConnectionInfo(false);
                }
            }
        }
        return true;
    }

    public void logConnectionInfo(boolean closing) {
        this.logConnectionInfo(closing,"Unknown");
    }

    public void logConnectionInfo(boolean closing, String reason) {

        String[] args = {
            getRemoteConnectionString(),
            localServiceString(),
            Integer.toString(Globals.getConnectionManager().size()),
            reason,
            String.valueOf(control.size()),
            Integer.toString(service.size())
        };

        if (!closing) {
            logger.log(Logger.INFO, BrokerResources.I_ACCEPT_CONNECTION, args);
        } else {
            logger.log(Logger.INFO, BrokerResources.I_DROP_CONNECTION, args);
        }
    }


// -------------------------------------------------------------------------
//   Queuing Messages
// -------------------------------------------------------------------------

    private NFLPriorityFifoSet control = null;
    boolean hasCtrl = true;


    /**
     * send a control (reply) message back to the client
     *
     * @param msg message to send back to the client
     */
    public void sendControlMessage(Packet msg) {
        if (!isValid() && msg.getPacketType() != PacketType.GOODBYE ) {
            logger.log(Logger.INFO,"Internal Warning: message " + msg
                  + "queued on destroyed connection " + this);
        }
            
        control.add(msg);
        synchronized (control) {
            hasCtrl = !control.isEmpty();
        }
    }

    protected void sendControlMessage(Packet msg, boolean priority)
    {
        if (IMQBasicConnection.DEBUG) {
            logger.log(Logger.DEBUGHIGH, 
                "IMQIPConnection[ {0} ] queueing Admin packet {1}", 
                this.toString(), msg.toString());
        }
        if (!isValid()) { // we are being destroyed
            return;
        }
        // I am assuming that there isnt any issue
        // with priority
        // if there is, I may need a new list type
        assert priority == false;
        sendControlMessage(msg);
    }


    /** 
     * Flush all control messages on this connection to
     * the client.
     * @param timeout the lenght of time to try and flush the
     *         messages (0 indicates wait forever)
     */
    public void flushControl(long timeout) {

        if (read_assigned == write_assigned && read_assigned != null) {
            localFlushCtrl();
            return;
        }

        synchronized (flushCtrlLock) { 
            if (IMQBasicConnection.DEBUG) {
                logger.log(Logger.DEBUG,
                        "Flushing Control Messages with timeout of " + timeout);
            }
            // dont worry about syncing here -> if we miss the
            // window we should still be woken up w/ the ctrl 
            // notify -> since that happens AFTER a message is
            // removed from the list
            if (ctrlpkt == null && control.isEmpty() && !flushCritical)
                return;
            if (!isValid()) {
                return;
            }
            long time = System.currentTimeMillis();
            flushCtrl = true;
            if (timeout < 0) {
                return;
            }
            while (flushCtrl && isValid()) {
                try {
                    if (timeout != 0) {
                        flushCtrlLock.wait(timeout);
                    } else {
                        flushCtrlLock.wait(1000 /* 1 second */);
                    }
                } catch (InterruptedException ex) {
                   // no reason to do anything w/ it
                }
                if (flushCtrl && timeout > 0 && 
                    System.currentTimeMillis() >= time+timeout)
                    break;
            }
            flushCtrl = false;
            if (IMQBasicConnection.DEBUG) {
                if (flush) {
                    logger.log(Logger.DEBUG,
                        "Control Flush did not complete in timeout of " 
                        + timeout);
                } else {
                    logger.log(Logger.DEBUG,
                            "Contrl Flush completed");
                }
            }
        }
    }

    protected void localFlushCtrl() {
        // OK .. if we are in the SAME thread as write do it inline
        flushCtrl = true;
        try {
            while (writeData(false) != Operation.PROCESS_PACKETS_COMPLETE) {
            }
        } catch (Exception ex) {
            // got exception while flushing, connection is probably gone
            logger.log(Logger.DEBUG,"error in flush " + this , ex);
        }
        flushCtrl = false;

    }

    protected void localFlush() {
        // OK .. if we are in the SAME thread as write do it inline
        flush = true;
        try {
            while (writeData(false) != Operation.PROCESS_PACKETS_COMPLETE) {}
        } catch (Exception ex) {
            // got exception while flushing, connection is probably gone
            logger.log(Logger.DEBUG,"error in flush " + this , ex);
        }
        flush = false;

    }

    /** 
     * Flush all control and JMS messages on this connection to
     * the client.
     * @param timeout the lenght of time to try and flush the
     *         messages (0 indicates wait forever)
     */
    public  void flush(long timeout) {
        if (read_assigned == write_assigned && read_assigned != null) {
            localFlush();
            return;
        }
        if ( !inCtrlWrite && control.isEmpty() 
            && !inJMSWrite && hasBusySessions()
            && !flushCritical && !lockCritical) 
        {
            // nothing to do
            return;
        }
        synchronized (flushLock) { 
            if (IMQBasicConnection.DEBUG) {
                logger.log(Logger.DEBUG,
                        "Flushing Messages with timeout of " + timeout);
            }
            if (!isValid())
                return;
            // dont worry about syncing here -> if we miss the
            // window we should still be woken up w/ the ctrl 
            // notify -> since that happens AFTER a message is
            // removed from the list
            if ( !inCtrlWrite && control.isEmpty() 
                && !inJMSWrite && hasBusySessions()
                && !flushCritical && !lockCritical)
            {
                // nothing to do
                return;
            }
            long time = System.currentTimeMillis();
            flush = true;
            while (flush && isValid()) {
                try {
                    if (timeout != 0) {
                        flushLock.wait(timeout);
                    } else {
                        flushLock.wait(1000 /* 1 second */);
                    }
                } catch (InterruptedException ex) {
                    // valid, no reason to check
                }
                if (flush && timeout > 0 && 
                    System.currentTimeMillis() >= time+timeout)
                    break;
            }
            if (IMQBasicConnection.DEBUG) {
                if (flush) {
                    logger.log(Logger.DEBUG,
                            "Flush did not complete in timeout of " + timeout);
                } else {
                    logger.log(Logger.DEBUG,
                            "Flush completed");
                }
            }
        }
    }




    void dumpConnectionInfo() {
        if (ninfo != null) {
            logger.log(Logger.INFO,"Connection Information [" +this +
              "]" + ninfo.getStateInfo());
        }
    }

    void checkConnection(int state) {

        synchronized(timerLock) {
            try {
                stateWatcher.cancel();
            } catch (IllegalStateException ex) {
                logger.log(Logger.DEBUG,"Error destroying "+
                    " connection "  + this + " to state " +
                    state, ex);
            }
            stateWatcher = null;
        }
        String[] args = {toString(), getConnectionStateString(this.state),
                         getConnectionStateString(state),
                         String.valueOf(interval)};
        synchronized (this) { 
            if (this.state >= state) 
            { 
                return;
            }
            if (this.state >= Connection.STATE_CLOSED 
                || this.state == Connection.STATE_UNAVAILABLE) 
            {
                return;
            }

            logger.log(Logger.WARNING, 
                 Globals.getBrokerResources().getKString(
				   BrokerResources.W_CONNECTION_TIMEOUT, args));
        }

        // FOR DEBUG ... add additional state information
        if (IMQBasicConnection.DEBUG) {
            dumpConnectionInfo();
        }

       // dont bother being nice
        destroyConnection(false, GoodbyeReason.CON_FATAL_ERROR,
            Globals.getBrokerResources().getKString(
            BrokerResources.W_CONNECTION_TIMEOUT, args)); 
    }




// -------------------------------------------------------------------------
//   Receiving Messages
// -------------------------------------------------------------------------

    protected Packet readpkt = null;


    // flag used with assertions to make sure
    // that the thread pool never assigns this
    // thread twice at one time

    private boolean inReadProcess = false;

    // info of the last good packet read on a connection for diagnostic
    private int lastPacketType = 0;
    private int lastPacketSize = 0;


    // new method to handle how we get the packet
    // this is overridden in Embedded more to get it from a queue
    protected boolean readInPacket(Packet p) 
           throws IllegalArgumentException, StreamCorruptedException, BigPacketException, IOException
    {
        boolean OK = true;
            
        if (STREAMS) {
           assert is!= null;
           readpkt.readPacket(is);
        } else {
           assert channel != null;
           OK = readpkt.readPacket(channel, BLOCKING);
         }
         return OK;
    }

    // used for subclasses if we don't want to recreate a packet
    protected Packet clearReadPacket(Packet p) {
        return null;
    }


    public int readData()
         throws IOException, BrokerException /* operation incomplete */
    {
        assert inReadProcess == false;


        try {
            inReadProcess = true;

            if (IMQBasicConnection.DEBUG || DUMP_PACKET || IN_DUMP_PACKET) {
                logger.log(Logger.DEBUG,
                        "Reading from " + getClass() + "{0} ", 
                        this.toString() 
                        + Thread.currentThread());
            }
    
           if (!isValid()) {
                if (IMQBasicConnection.DEBUG) {
                    logger.log(Logger.DEBUG,
                            "Invalid Connection {0} ", 
                            this.toString() + 
                            Thread.currentThread());
                }
               throw new IOException(
                    "Connection has been closed " + this);
           }
    
    
           try {
               if (readpkt == null) { // heck its a new packet
                  readpkt = new Packet(OVERRIDE_READ_PACKET 
                                 ? O_READ_USE_DIRECT 
                                 : !STREAMS);
                  readpkt.generateSequenceNumber(false);
                  readpkt.generateTimestamp(false);
                  if (IMQBasicConnection.DEBUG) {
                      logger.log(Logger.DEBUG,  
                          "IMQIPConnection {0} getting a new read packet {1} ", 
                          this.toString(), readpkt.toString());
                  }
               }
               assert readpkt != null;
           } catch (OutOfMemoryError err) {
               // ack .. got error loading header
               // Dump header to help with debugging (i.e. was it an
               // unusually large packet? Corrupted read?)
               Globals.handleGlobalError(err,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.M_LOW_MEMORY_READALLOC) + ": " +
                    readpkt.headerToString());
           }

           if (isValid()) {
               try {
                   boolean OK = true;
            
                   OK = readInPacket(readpkt);
                   msgsIn ++;
                   if (readpkt.getPacketType() < PacketType.LAST)
                       pktsIn[readpkt.getPacketType()] ++;
    
                   if (!OK) { // we didnt finish reading
                       return Operation.PROCESS_WRITE_INCOMPLETE;

                   } else if (packetVersion == NO_VERSION) {
                       // ok this is the first packet, get the
                       // version of protocol the client is talking
                       packetVersion = readpkt.getVersion();


                       // XXX-LKS need to use protocol version
                       // not just packet version

                       if (packetVersion < CURVERSION)
                           convertPkt = new ConvertPacket(this, 
                                    packetVersion,
                                    CURVERSION);
                   }
                   // convert to new packet type if necessary
                   if (convertPkt != null)
                       convertPkt.handleReadPacket(readpkt); 
    
               } catch (IllegalArgumentException ex) {
                   handleIllegalArgumentExceptionPacket(readpkt, ex);
                   throw ex;
                       
               } catch (OutOfMemoryError ex) {
                   // Dump header to help with debugging (i.e. was it an
                   // unusually large packet? Corrupted read?)
                   Globals.handleGlobalError(ex,
                    Globals.getBrokerResources().getKString(
                    BrokerResources.M_LOW_MEMORY_READALLOC) + ": " +
                    readpkt.headerToString());
    
                   // re-read the packet ... 
                   //  in 99.??? % of the time, we just lost a packet
                   // 
                   // if we fail a second time  or get an unexpected
                   // error ... its fatal for the connection
                    boolean OK = readInPacket(readpkt);
                    if (!OK) { // we didnt finish reading
                        return Operation.PROCESS_WRITE_INCOMPLETE;
                    }
    
                } catch (StreamCorruptedException ex) {
                   String connStr = getRemoteConnectionString();
                    logger.logStack(Logger.WARNING,
                        BrokerResources.W_STREAM_CORRUPTED, connStr, ex);
                    logger.log(Logger.WARNING,
                        "Last good packet received from connection " +
                        connStr + ": type = " + lastPacketType +
                        ", size = " + lastPacketSize);

                    throw ex;
                } catch (BigPacketException e) {
                    // The packet exceeded the maximum packet size. This
                    // Should only occur for JMS message packets since all
                    // control packets are relatively small.
                    // We ignore the packet, log a warning and send a
                    // reply indicating the error.
                    handleBigPacketException(readpkt, e);
                    // Packet is garbage. Null it out so we don't reuse it.
                    readpkt.reset();
                    readpkt = clearReadPacket(readpkt);
                    return Operation.PROCESS_PACKETS_REMAINING;
                }
    
                if (Globals.getConnectionManager().PING_ENABLED) {
                    updateAccessTime(true);
                }
	            if (METRICS_ON) {
                    countInPacket(readpkt);
                }
            }
                      
            if (IMQBasicConnection.DEBUG || DUMP_PACKET || IN_DUMP_PACKET) {
                int flag = (DUMP_PACKET || IN_DUMP_PACKET) ? Logger.INFO
                    : Logger.DEBUGHIGH;

                logger.log(flag, "\n------------------------------"
                    + "\nReceived incoming Packet - Dumping"
                    + "\nConnection: " + this 
                    + "\n------------------------------"
                    + "\n" + readpkt.dumpPacketString(">>>>****") 
                    + "\n------------------------------");
            }

            // Save info of the last good packet read
            lastPacketType = readpkt.getPacketType();
            lastPacketSize = readpkt.getPacketSize();

            try {
                if (MemoryGlobals.getMEM_EXPLICITLY_CHECK() ||
                    (MemoryGlobals.MEM_QUICK_CHECK && readpkt.getPacketSize() >
                     MemoryGlobals.MEM_SIZE_TO_QUICK_CHECK)) {
                    if (Globals.getMemManager() != null)
                        Globals.getMemManager().quickMemoryCheck();
                }
                setCritical(true);
                if (!isValid())  {
                    return Operation.PROCESS_PACKETS_COMPLETE;
                }
                try {
   	                router.handleMessage(this, readpkt);
                    readpkt = clearReadPacket(readpkt);
                } catch (OutOfMemoryError ex) {
                    logger.logStack(Logger.ERROR, 
                        BrokerResources.E_LOW_MEMORY_FAILED, 
                        ex);
                    throw ex;
                }

            } finally { 
                 setCritical(false);
            }
            return Operation.PROCESS_PACKETS_REMAINING;

        } finally {
            // used for thread safety assert
            inReadProcess = false;
        }

    } 


// -------------------------------------------------------------------------
//   Sending Messages
// -------------------------------------------------------------------------


    private Packet ctrlpkt = null;
    private Packet waitingWritePkt = null;


    /**
     * indicates that we were interrupted
     * during a control pkt write
     */
    private boolean inCtrlWrite = false;

    /**
     * indicates that we were interrupted
     * during a JMS pkt write
     */
    private boolean inJMSWrite = false;



    // flag which is used w/ asserts to make
    // sure that there is no competition in
    // the writeData processing
    boolean inWriteProcess = false;

    protected boolean writeOutPacket(Packet p) throws IOException
    {
         boolean donewriting = true;
         if (STREAMS) {
             p.writePacket(os);
         } else {
             donewriting = p.writePacket(channel, BLOCKING);
         }
         return donewriting;
    }

    protected Packet clearWritePacket(Packet p)
    {
         if (p != null)
             p.destroy();
         return null;
    }

    public int writeData(boolean wait)
         throws IOException {

        assert inWriteProcess == false;

        if (hasCtrl) {
            synchronized (control) {
                hasCtrl = !control.isEmpty();
            }
        }
        try { // catch out of memory errors
            inWriteProcess = true;

            if (IMQBasicConnection.DEBUG) {
                logger.log(Logger.DEBUG,  
                    "Writing IMQIPConnection {0} ", this.toString());
            }

            while (wait && !isBusy() && isValid() ) {
                // we are using a blocking thread pool
                // wait for someone to notify us
                waitForWork(0 /* wait forever */);
            }
            if (!isValid()) {
                throw new IOException(
                        Globals.getBrokerResources().getKString(
                        BrokerResources.X_INTERNAL_EXCEPTION,
                        "destroyed Connection"));
            }
            if (!isBusy()) {
                return Operation.PROCESS_PACKETS_COMPLETE;
            }    

            
            flushCritical = true;

            if ((!inCtrlWrite) && (!inJMSWrite)) {
                // we are not completing a packet
                if (!control.isEmpty()) {
                    ctrlpkt = (Packet) control.removeNext();
                    if (ctrlpkt != null) {
                        inCtrlWrite = true;
                    }
                } else {
                    synchronized (control) {
                        hasCtrl = !control.isEmpty();
                    }
                }

            }
            if (ctrlpkt != null)  {
                // ok , we have a new packet
                if (ctrlpkt.getPacketType() > PacketType.MESSAGE) {
                    ctrlpkt.setIP(ipAddress);
                    ctrlpkt.setPort(getLocalPort());
                }
                // first convert it
                if (convertPkt != null)
                    convertPkt.handleWritePacket(ctrlpkt); 
                if (IMQBasicConnection.DEBUG || DUMP_PACKET || OUT_DUMP_PACKET) {
                    dumpControlPacket(ctrlpkt);
                }
                inCtrlWrite = !writeOutPacket(ctrlpkt);
                if (!inCtrlWrite) { // we are done

                   if (ctrlpkt.getPacketType() < PacketType.LAST)
                       pktsOut[ctrlpkt.getPacketType()] ++;

                    ctrlPktsToConsumer ++;
                    if (IMQBasicConnection.DEBUG || DUMP_PACKET || OUT_DUMP_PACKET) {
                        logger.log(Logger.INFO,
                                "Finished writing packet [" + ctrlpkt +"]");
                    }
                    if (Globals.getConnectionManager().PING_ENABLED) {
                        updateAccessTime(false);
                    }
                    if (METRICS_ON) {
                        countOutPacket(ctrlpkt);
                    }

                    assert ctrlpkt != null;

                    ctrlpkt = clearWritePacket(ctrlpkt);;
                }

                // the broker is no longer in a critical state
                flushCritical = false;
                synchronized(stateLock) {
                    checkState();
                }

                // we were interrupted during the write
                if (inCtrlWrite)  {
                     return Operation.PROCESS_WRITE_INCOMPLETE;
                } else if (isBusy()) {
                    return Operation.PROCESS_PACKETS_REMAINING;
                } else {
                    return Operation.PROCESS_PACKETS_COMPLETE;
                }

            }    

            // the broker is no longer in a critical state
            flushCritical = false;

            // OK .. now try normal messages
    
            if (IMQBasicConnection.DEBUG) {
                logger.log(Logger.DEBUGHIGH, 
                    "IMQIPConnection[ {0} ] - processing "
                    + " normal msg queue", this.toString());
            }

           
            // we shouldnt be here if we are paused or waiting for a resume


            if (!inJMSWrite && !hasCtrl) {
                lockCritical = true;
                boolean validJMSPkt = false;

                assert waitingWritePkt != null;
                if (hasCtrl || !runningMsgs || paused || waitingForResumeFlow ||
                    ((validJMSPkt = fillNextPacket(waitingWritePkt)) == false) )
                {
                    synchronized(stateLock) {
                        checkState();
                    }

                    if (isBusy()) {
                        return Operation.PROCESS_PACKETS_REMAINING;
                    } else {
                        return Operation.PROCESS_PACKETS_COMPLETE;
                    }
                }
                if (!validJMSPkt) return Operation.PROCESS_PACKETS_REMAINING;
                inJMSWrite = true;

                // convert to old packet type if necessary
                if (convertPkt != null) {
                    // NOTE : if the c bit is set and this
                    // is an old packet, convert needs to 
                    // restart the resume flow

                    // LKS - XXX
                    convertPkt.handleWritePacket(waitingWritePkt); 
                }


                // check for connection flow control
                sent_count ++;
                boolean aboutToWaitForRF = flowCount != 0 && 
                                   sent_count >= flowCount;    

                if (aboutToWaitForRF) {
                    sent_count = 0;
                    waitingWritePkt.setFlowPaused(aboutToWaitForRF);
                    haltFlow();
                }
                if (IMQBasicConnection.DEBUG || DUMP_PACKET || OUT_DUMP_PACKET) {
                    int flag = (DUMP_PACKET || OUT_DUMP_PACKET) ? Logger.INFO
                            : Logger.DEBUGHIGH;
    
                    logger.log(flag, 
                          "\n------------------------------"
                         +"\nSending JMS Packet -[block = "+BLOCKING 
                         + ",nio = "+!STREAMS+"] " + this + "  Dumping"
                         + "\n" + waitingWritePkt.dumpPacketString("<<<<****")
                         + "\n------------------------------");
                }

            }

    
            if (inJMSWrite) { 
                inJMSWrite = !writeOutPacket(waitingWritePkt);
            }
            lockCritical = false;

            if (inJMSWrite) { // we were interrupted
                return Operation.PROCESS_WRITE_INCOMPLETE;
            }
            msgsToConsumer ++;

            if (waitingWritePkt.getPacketType() < PacketType.LAST)
                   pktsOut[waitingWritePkt.getPacketType()] ++;
   
            if (Globals.getConnectionManager().PING_ENABLED) {
                updateAccessTime(false);
            }
            if (METRICS_ON) {
                countOutPacket(waitingWritePkt);
            }

            if (isBusy()) {
                return Operation.PROCESS_PACKETS_REMAINING;
            } else {
                return Operation.PROCESS_PACKETS_COMPLETE;
            }
        } catch (OutOfMemoryError err) {
            Globals.handleGlobalError(err,
                Globals.getBrokerResources().getKString(
                BrokerResources.M_LOW_MEMORY_WRITE));
        } catch (IOException ex) {
             // connection is gone
             logger.log(logger.DEBUGMED, "closed connection " + this, ex);
             inJMSWrite = false;
             destroyConnection(false, GoodbyeReason.CLIENT_CLOSED, 
                Globals.getBrokerResources().getKString(
                    BrokerResources.M_CONNECTION_CLOSE));
             throw ex; 
        } finally {

             inWriteProcess = false;

             synchronized (flushCtrlLock) { 
                 if (flushCtrl) {
                      if ((ctrlpkt == null && control.isEmpty()) 
                                  || !isValid()) {
                          if (IMQBasicConnection.DEBUG) {
                              logger.log(Logger.DEBUG,
                                  "Done flushing control messages on " 
                                  + this);
                          }
                          flushCtrl = false;
                          flushCtrlLock.notifyAll();
                          if (os != null)
                              os.flush();
                     }
                 }
             }
             if (flush) {
                 synchronized (flushLock) { 
                      if (!isBusy() || !isValid()) {
                          if (IMQBasicConnection.DEBUG) {
                              logger.log(Logger.DEBUG,
                                     "Done flushing control messages on " 
                                      + this);
                          }
                          flush = false;
                          flushLock.notifyAll();
                          os.flush();
                     }
                 }
             }
             if (!isValid()) {
                 synchronized(this) {
                     waitingWritePkt = clearWritePacket(waitingWritePkt);
                 }
             }
        } 

        assert false : " should never happen";

        if (isBusy()) {
            return Operation.PROCESS_PACKETS_REMAINING;
        } else  {
            return Operation.PROCESS_PACKETS_COMPLETE;
        }
    }

    protected void dumpControlPacket(Packet pkt) {
        int loglevel = ((DUMP_PACKET || OUT_DUMP_PACKET) ? 
                        Logger.INFO : Logger.DEBUGHIGH);
        logger.log(loglevel, "\n------------------------------"
                            +"\nSending Control Packet -[block = "+BLOCKING 
                            + ",nio = "+!STREAMS+"]   Dumping"
                            + "\n------------------------------"
                            + "\n" + pkt.dumpPacketString("<<<<****")
                            + "\n------------------------------");
    }

    protected void checkState() {
        assert Thread.holdsLock(stateLock);

        synchronized(control) {
            synchronized(busySessions) {
                boolean is_running = isValid() && (
                  (inCtrlWrite || (!paused && !control.isEmpty()))
                            ||
                  (inJMSWrite || (runningMsgs && !waitingForResumeFlow
                       && !busySessions.isEmpty())));

               if (!isValid() || busy != is_running) {
                    busy = is_running;
                    stateLock.notifyAll();
                    if (ninfo != null) {
                        ninfo.setReadyToWrite(this, busy);
                    } else if (service instanceof NotificationInfo) {
                        ((NotificationInfo)service).setReadyToWrite(this, busy);
                    }
                }
            } 
        } 
                 
    }


    private boolean waitForWork(long time) {
        synchronized (stateLock) {
            if (isValid() && !busy) {
                try {
                    if (time == 0) {
                        stateLock.wait();
                    } else {
                        stateLock.wait(time);
                    }
                } catch (InterruptedException ex) {
                    assert false;
                    logger.logStack(Logger.INFO,"Internal error, "
                      + "got interrupted exception", ex);
                }
            }
            return busy;
        }
    }

    protected void handleWriteException(Throwable ex)
    throws IOException, OutOfMemoryError {

        if (ex instanceof OutOfMemoryError) {
            logger.log(Logger.ERROR,
                BrokerResources.E_FORCE_CON_CLOSE, this, ex);
            int count = 0;
            boolean firstpass = true;
            while (true) {
                try {        
                    logger.log(Logger.ERROR, 
                        Globals.getBrokerResources().getKString(
                        BrokerResources.E_CLOSE_CONN_ON_OOM, this.toString()));
                    closeConnection(firstpass, 
                        GoodbyeReason.CON_FATAL_ERROR, ex.toString());
                    firstpass = false;
                    break;
                } catch (OutOfMemoryError err1) {
                    logger.log(Logger.DEBUG,
                        "Connection could not be cleanly closed,"
                         + " trying again on " + this, ex);
                   count ++;
                   if (count >= 2) {
                       throw err1;
                   }
                }
            }
        } else if (ex instanceof IOException) {
            throw (IOException)ex;
        } else if (ex instanceof BrokerShutdownRuntimeException) {
             logger.log(Logger.INFO, ex.getMessage());
             closeConnection(true, 
                 GoodbyeReason.SHUTDOWN_BKR, ex.toString());
        } else {     
             logger.logStack(Logger.ERROR, "Internal Error: "
                     + "Received unexpected exception processing connection "
                     + " closing connection", ex);
             // something went wrong, close connection
             closeConnection(true, 
                 GoodbyeReason.CON_FATAL_ERROR, ex.toString());
        }
    }

    protected void handleBigPacketException(Packet pkt, BigPacketException e) {
        String params[] = { String.valueOf(pkt.getPacketSize()),
                            pkt.toString(),
                            String.valueOf(pkt.getMaxPacketSize())
                          };
        logger.log(Logger.ERROR,
                   BrokerResources.X_IND_PACKET_SIZE_EXCEEDED, params, e);
        sendReply(pkt, Status.ENTITY_TOO_LARGE);
    }

    protected void handleIllegalArgumentExceptionPacket(
        Packet pkt, IllegalArgumentException e) {

        logger.log(Logger.ERROR, 
        "Bad version packet received: "+e.getMessage()+", reject connection ["+this+"]"); 

        // queue a HELLO_REPLY w/ error
        Packet reply = new Packet(useDirectBuffers());
        reply.setIP(ipAddress);
        reply.setPort(getLocalPort());

        reply.setPacketType(PacketType.HELLO_REPLY);
        Hashtable hash = new Hashtable();
        hash.put("JMQStatus", Integer.valueOf(Status.BAD_VERSION));
        reply.setProperties(hash);
        sendControlMessage(reply);
        flushControl(1000);
        destroyConnection(true, GoodbyeReason.CON_FATAL_ERROR,
            (getDestroyReason() == null ? e.toString():getDestroyReason()));
    }

    /**
     * Send a reply to pkt with the given status.
     */
    private void sendReply(Packet pkt, int status) {
        if (pkt.getSendAcknowledge()) {
            Packet reply = new Packet(useDirectBuffers());
            reply.setPacketType(PacketType.SEND_REPLY);
            reply.setConsumerID(pkt.getConsumerID());
            Hashtable hash = new Hashtable();
            hash.put("JMQStatus", Integer.valueOf(status));
            reply.setProperties(hash);
            sendControlMessage(reply);
        }
    }

// ---------------------------------------
//     Abstract Connection methods
// ---------------------------------------

    public void cleanupMemory(boolean persist) {
        // does nothing right now
    }

    protected void sayGoodbye(int reason, String reasonstr) {
        sayGoodbye(false, reason, reasonstr);
    }
    protected void sayGoodbye(boolean force, int reason, String reasonStr) {
        Packet goodbye_pkt = new Packet(useDirectBuffers());
        goodbye_pkt.setPacketType(PacketType.GOODBYE);
        Hashtable hash = new Hashtable();
        hash.put("JMQExit", Boolean.valueOf(force));
        hash.put("JMQGoodbyeReason", Integer.valueOf(reason));
        hash.put("JMQGoodbyeReasonString", reasonStr);
        goodbye_pkt.setProperties(hash);
        sendControlMessage(goodbye_pkt);
    }

    protected void checkConnection() {
        boolean sendAck = false;
        if (enablePingReply && closeInterval > 0 && getClientProtocolVersion() >= PacketType.VERSION364 ) {
            sendAck = true;
            
            // see if we need to kill the connection
            // get access time
            long access = getLastResponseTime();

            // is delta greater than ping_interval * closeInterval
            long delta = System.currentTimeMillis() - access;
            long interval = ConnectionManager.pingTimeout * (closeInterval+1);

            // if is, kill the connection
            if (delta >= interval) {
                logger.log(Logger.INFO, BrokerResources.W_UNRESPONSIVE_CONNECTION,
                   String.valueOf(this.getConnectionUID().longValue()), 
                   String.valueOf(delta/1000));
                    
                destroyConnection(false,GoodbyeReason.ADMIN_KILLED_CON,
                    "Connection unresponsive");
            }

        }

        Packet flush_pkt = new Packet(useDirectBuffers());
        flush_pkt.setPacketType(PacketType.PING);
        if (sendAck) {
            flush_pkt.setSendAcknowledge(true);
        }
        sendControlMessage(flush_pkt);
    }

    protected void flushConnection(long timeout) {
        flushControl(timeout);
    }

    public void flowPaused(long size) {
        if (Globals.getMemManager() != null)
            Globals.getMemManager().notifyWhenAvailable(this, size);
    }

    public void resumeMemory(int cnt, long memory, long max) {
        sendResume(cnt, memory, max, false);
    }

    public void updateMemory(int cnt, long memory, long max) {
        sendResume(cnt, memory, max, true);
    }

    protected void sendResume(int cnt, long memory, 
          long max, boolean priority) 
    {
        if (packetVersion < Packet.VERSION1)
            return; // older protocol cant handle resume

        Packet resume_pkt = new Packet(useDirectBuffers());
        resume_pkt.setPacketType(PacketType.RESUME_FLOW);
        Hashtable hash = new Hashtable();
        if (Globals.getMemManager() != null) {
            hash.put("JMQSize", Integer.valueOf(Globals.getMemManager().getJMQSize()));
            hash.put("JMQBytes", Long.valueOf(Globals.getMemManager().getJMQBytes()));
            hash.put("JMQMaxMsgBytes", Long.valueOf(
                     Globals.getMemManager().getJMQMaxMsgBytes()));
        }
        resume_pkt.setProperties(hash);

        sendControlMessage(resume_pkt, priority);
    }


    /**
     * called when either the session or the
     * control message is busy 
     */
    public void eventOccured(EventType type,  Reason r,
            Object target, Object oldval, Object newval, 
            Object userdata) 
    {

        // LKS - at this point, we are in a write lock
        // only one person can change the values
        // at a time

        synchronized (stateLock) {
            if (type == EventType.EMPTY) {
    
                // this can only be from the control queue
                assert target == control;
                assert newval instanceof Boolean;
    
            } else if (type == 
                    EventType.BUSY_STATE_CHANGED) {
                assert target instanceof Session;
                assert newval instanceof Boolean;
    
                Session s = (Session) target;
    
                synchronized(busySessions) {
                    synchronized (s.getBusyLock()) {
                        if (s.isBusy()) {
                            busySessions.add(s);
                        }
                    }
                }
                
            }
            checkState();
        }
    }

    private boolean fillNextPacket(Packet p) 
    {
        Session s = null;
        
        synchronized(busySessions) {
           Iterator itr = busySessions.iterator();
           while (itr.hasNext()) {
               s = (Session)itr.next();
               itr.remove();
               if (s == null) 
                   continue;
               synchronized (s.getBusyLock()) {
                   if (s.isBusy()) {
                       busySessions.add(s);
                       break;
                   }
               }
               
           }
        }

        if (s == null) return false;

        return s.fillNextPacket(p) != null;
    }
}



