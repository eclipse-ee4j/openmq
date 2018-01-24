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
 * @(#)FlowControl.java	1.32 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.*;

import com.sun.messaging.jmq.jmsclient.resources.*;

/**
 * Class to handle imq protocol flow control.
 *
 * Every connection has its own FlowControl object (thread). Why???
 * chiaming: answer to the above question:
 *
 * 1. Simplify the implementation.
 * 2. Reduce interference between connections.  For example, congestion in one
 * connection does not affect the others.
 * 3. Bugs isolation. Should there is a bug in one connection, the other
 * connections are not affected.
 *
 *
 */
public class FlowControl implements Runnable, Traceable {
    // The current protocol handler
    protected ProtocolHandler protocolHandler = null;
    protected ConnectionImpl connection = null;

    // flag to indicate if the connection is closed
    protected boolean isClosed = false;

    // Thread naming
    protected static final String imqFlowControl =
        "imqConnectionFlowControl-";
    protected boolean debug = Debug.debug;

    // Collection of all the FlowControlEntries.
    private Hashtable flowControlTable = null;

    // FlowControlEntries ready for sending resumeFlow.
    private Hashtable readyQueue = null;

    private static boolean FLOWCONTROL_DEBUG =
        Boolean.getBoolean("imq.flowcontrol.debug");

    //This is replaced witn imqPingInterval as debug interval.
    //private static int FLOWCONTROL_DEBUG_INTERVAL =
    //    Integer.getInteger("imq.flowcontrol.debug.interval",
    //        3000).intValue();

    private static String FLOWCONTROL_LOG =
        System.getProperty("imq.flowcontrol.log");

    private static PrintStream fdbg = null;

    //ping interval -- will be initted with connection.getPingInterval().
    private long pingInterval = 30 * 1000;

    private static final Logger connLogger = ConnectionImpl.connectionLogger;

    private static void initFlowControlDebug() {
        if (FLOWCONTROL_DEBUG == false || fdbg != null)
            return;

        if (FLOWCONTROL_LOG == null)
            FLOWCONTROL_LOG = "stderr";

        if (FLOWCONTROL_LOG.equals("stdout"))
            fdbg = System.out;
        else if (FLOWCONTROL_LOG.equals("stderr"))
            fdbg = System.err;
        else {
            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(FLOWCONTROL_LOG, true);
                fdbg = new PrintStream(fos);
            }
            catch (java.io.IOException e) {
                fdbg = System.err;
            }
        }
    }

    /**
     * Create a flow control manager thread for a connection.
     */
    public FlowControl(ConnectionImpl conn) {
        this.connection = conn;

        this.pingInterval = conn.getPingInterval();

        protocolHandler = connection.getProtocolHandler();

        readyQueue = new Hashtable();
        flowControlTable = new Hashtable();

        initFlowControlDebug();

        addConnectionFlowControl(conn);
    }

    private void addConnectionFlowControl(ConnectionImpl connection) {
        FlowControlEntry fce = new ConnectionFlowControlEntry(
            this, connection.getProtocolHandler(),
            connection.protectMode,
            connection.flowControlMsgSize,
            connection.flowControlWaterMark);

        flowControlTable.put(connection, fce);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Added connection flow control entry : " + connection);
        }
    }

    /*
    private void removeConnectionFlowControl(ConnectionImpl connection) {
        flowControlTable.remove(connection);
    }
    */

    /**
     * This method is called by ReadChannel when it receives a message
     * packet with connection flow control bit set.
     */
    public void requestConnectionFlowResume() {
        requestResume(connection);
    }

    public void messageReceived() {
        messageReceived(connection);
    }

    public void messageDelivered() {
        messageDelivered(connection);
    }

    public void addConsumerFlowControl(Consumer consumer) {
        FlowControlEntry fce = new ConsumerFlowControlEntry(
            this, consumer.getConnection().getProtocolHandler(),
            consumer);

        flowControlTable.put(consumer, fce);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Added flow control entry : " + fce);
        }
    }

    public void removeConsumerFlowControl(Consumer consumer) {
        FlowControlEntry fce = (FlowControlEntry)
            flowControlTable.remove(consumer);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Removed flow control entry : " + fce);
        }
    }

    public void requestResume(Object key) {
        if (debug)
            Debug.println("**** In requestResume. key = " + key);

        FlowControlEntry fce = getFlowControlEntry(key);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("PAUSED MESSAGE DELIVERY FOR : " + fce);
        }

        if (connLogger.isLoggable(Level.FINEST)) {
            connLogger.log(Level.FINEST, ClientResources.I_FLOW_CONTROL_PAUSED, fce );
        }

        fce.setResumeRequested(true);
    }

    public void messageReceived(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null)
            fce.messageReceived();
    }

    public void messageDelivered(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null)
            fce.messageDelivered();
    }

    //bug 6271876 -- connection flow control
    public void resetFlowControl(Object key, int count) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null)
            fce.resetFlowControl(count);
    }

    public Hashtable getDebugState(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null)
            return fce.getDebugState();

        return new Hashtable();
    }

    /**
     * Test instrumentation method.
     */
    public Object TEST_GetAttribute(String name, Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        return fce.TEST_GetAttribute(name);
    }

    private FlowControlEntry getFlowControlEntry(Object key) {
        FlowControlEntry fce = (FlowControlEntry) flowControlTable.get(key);

        if (fce == null) {
            // This should never happen.
            if (! (key instanceof Consumer) &&
                ! (key instanceof ConnectionImpl))
                throw new IllegalArgumentException(
                    "getFlowControlEntry: Bad key type. key = " + key);

            throw new java.lang.IllegalStateException(
                "FlowControlEntry not found. key = " + key);
        }

        return fce;
    }

    private FlowControlEntry findFlowControlEntry(Object key) {
        if (key == null)
            return null;

        FlowControlEntry fce = (FlowControlEntry) flowControlTable.get(key);
        return fce;
    }

    /**
     * Start imq flow control thread for this connection.
     */
    public void start() {
        Thread thread = new Thread ( this );
        if (connection.hasDaemonThreads()) {
            thread.setDaemon(true);
        }
        //thread.setName(imqFlowControl + connection.getConnectionID());
        thread.setName(imqFlowControl + connection.getLocalID());
        thread.start();
    }

    /**
     * This method exit only if ReadChannel is closed (connection is
     * closed).
     */
    public void run() {
        long lastDump = 0;
        while (true) {
            FlowControlEntry[] rqCopy = null;

            // Wait for something to happen.
            synchronized (this) {
                while (!isClosed && readyQueue.size() == 0) {

                    try {

                      wait(pingInterval);
                      /**
                       * check if this is a timeout wait.
                       */
                      if (isClosed == false && readyQueue.size() == 0) {
                        /**
                         * send ping if no activities.
                         */
                        if (protocolHandler.getTimeToPing()) {
                            pingBroker();
                        }

                        protocolHandler.setTimeToPing(true);

                      }
                    } catch (InterruptedException e) {
                        if ( debug ) {
                            Debug.printStackTrace(e);
                        }
                    }

                    //if (FLOWCONTROL_DEBUG &&
                    //    FLOWCONTROL_DEBUG_INTERVAL > 0 &&
                    //    ((System.currentTimeMillis() - lastDump) >
                    //    FLOWCONTROL_DEBUG_INTERVAL)) {
                    if (FLOWCONTROL_DEBUG) {

                        if ( (System.currentTimeMillis()-lastDump) > pingInterval) {

                            status_report();

                            lastDump = System.currentTimeMillis();
                        }
                    } //debug

                } //while

                if (isClosed)
                    break; // Returns from the run() method.

                rqCopy = (FlowControlEntry [])
                    readyQueue.values().toArray(
                    new FlowControlEntry[readyQueue.size()]);
            }

            // Send resume flow packets.
            try {
                for (int i = 0; i < rqCopy.length; i++) {
                    rqCopy[i].sendResumeFlow();

                    if (FLOWCONTROL_DEBUG) {
                        fdbg.println("SENDING RESUME_FLOW FOR : " +
                            rqCopy[i]);
                    }

                    if ( connLogger.isLoggable(Level.FINEST) ) {
                        connLogger.log(Level.FINEST,
                                       ClientResources.I_FLOW_CONTROL_RESUME,
                                       rqCopy[i]);
                    }
                }
            }
            catch (Exception e) {
                if (connection.isClosed) {
                    isClosed = true;
                    break; // Returns from the run() method.
                }
                else
                    Debug.printStackTrace(e);
            }
        }
    }

    private void pingBroker() {

        try {
            //bug 6155026 - client sends PING before authenticated.
            //we decided to have broker ignore PING.
            //if ( connection.isClosed == false ) {
            protocolHandler.ping();
            //}
        } catch (JMSException e) {
            if ( debug ) {
                Debug.printStackTrace(e);
            }
        }
    }

    private void status_report() {
        fdbg.println("debug_interval = " + pingInterval);
        fdbg.println("\n-------------------------------- " +
            this + " : " + new java.util.Date());

        Enumeration enum2 = flowControlTable.elements();

        while (enum2.hasMoreElements()) {
            FlowControlEntry fce = (FlowControlEntry) enum2.nextElement();
            fce.status_report(fdbg);
        }
    }

    protected synchronized void addToReadyQueue(FlowControlEntry fce) {
        if (debug)
            Debug.println("In addToReadyQueue : " + fce);
        readyQueue.put(fce, fce);
        notifyAll();
    }

    protected synchronized void removeFromReadyQueue(FlowControlEntry fce) {
        if (debug)
            Debug.println("In addToReadyQueue : " + fce);

        readyQueue.remove(fce);
    }

    /**
     * ReadChannel calls this method when connection is closed.
     */
    public synchronized void close() {
        isClosed = true;
        notifyAll();
    }

    public void dump ( PrintStream ps ) {
    }
}

abstract class FlowControlEntry {
    protected boolean debug = Debug.debug;

    protected FlowControl fc;
    protected ProtocolHandler protocolHandler;

    public FlowControlEntry(FlowControl fc, ProtocolHandler protocolHandler) {
        this.fc = fc;
        this.protocolHandler = protocolHandler;
    }

    public abstract void messageReceived();
    public abstract void messageDelivered();
    public abstract void resetFlowControl(int count);
    public abstract void setResumeRequested(boolean resumeRequested);
    protected abstract void sendResumeFlow() throws Exception;

    protected Hashtable getDebugState() {
        return new Hashtable();
    }

    protected Object TEST_GetAttribute(String name) {
        return null;
    }

    protected abstract void status_report(PrintStream dbg);
}

class ConnectionFlowControlEntry extends FlowControlEntry {
    // Indicates whether flow control watermark checking should be
    // performed on this virtual stream...
    protected boolean enableFlowControlCheck;

    // Undelivered message count.
    protected int inQueueCounter = 0;
    protected int TEST_peakCount = 0;
    protected int TEST_pauseCount = 0;
    protected int TEST_resumeCount = 0;

    // Flow control chunk size.
    protected int flowControlChunkSize;

    // Flow control watermark.
    protected int flowControlWaterMark;

    // This flag indicates whether the broker is waiting for the
    // resume flow response.
    protected boolean resumeRequested = false;

    public ConnectionFlowControlEntry(FlowControl fc,
        ProtocolHandler protocolHandler,
        boolean enableFlowControlCheck,
        int flowControlChunkSize, int flowControlWaterMark) {

        super(fc, protocolHandler);

        this.enableFlowControlCheck = enableFlowControlCheck;
        this.flowControlChunkSize = flowControlChunkSize;
        this.flowControlWaterMark = flowControlWaterMark;
    }

    /**
     * Handle messageReceived event.
     *
     * Increment the inQueueCounter.
     */
    public void messageReceived() {
        synchronized (this) {
            inQueueCounter++;
            if (inQueueCounter > TEST_peakCount)
                TEST_peakCount = inQueueCounter;
        }
    }

    /**
     * Handle messageDelivered event.
     *
     * Decrement the inQueueCounter. If resumeFlow has been requested
     * AND if there is room in the queue, add this entry to the
     * readyQueue.
     */
    public void messageDelivered() {
        synchronized (this) {
            inQueueCounter --;

            if (enableFlowControlCheck) {
                checkAndResumeFlow();
            }
        }
    }

    /**
     * Reset the flow control state counters and send resume flow if
     * necessary.  This is called when session is closed or cinsumer is closed.
     * bug 6271876 -- connection flow control
     */
    public void resetFlowControl(int reduceFlowCount) {

        inQueueCounter = inQueueCounter - reduceFlowCount;

        if ( inQueueCounter < 0 ) {
            inQueueCounter = 0;
        }


        if (enableFlowControlCheck) {
            checkAndResumeFlow();
        }

    }

    /**
     * Handle resume request from the broker.
     */
    public synchronized void setResumeRequested(boolean resumeRequested) {
        if (debug) {
            Debug.println("setResumeRequsted[" + this +"] : " +
                resumeRequested);
        }

        this.resumeRequested = resumeRequested;
        if (resumeRequested) {
            TEST_pauseCount++;
            checkAndResumeFlow();
        }
    }


    protected synchronized void sendResumeFlow() throws Exception {
        setResumeRequested(false);
        protocolHandler.resumeFlow(flowControlChunkSize);
        fc.removeFromReadyQueue(this);

        TEST_resumeCount++;
    }

    private synchronized void checkAndResumeFlow() {
        if (resumeRequested) {
            if ((enableFlowControlCheck == false) ||
                (inQueueCounter < flowControlWaterMark)) {
                fc.addToReadyQueue(this);
            }
        }
    }

    protected Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        ht.put("enableFlowControlCheck",
            String.valueOf(enableFlowControlCheck));
        ht.put("inQueueCounter", String.valueOf(inQueueCounter));
        ht.put("peakCount", String.valueOf(TEST_peakCount));
        ht.put("isFlowPaused", String.valueOf(resumeRequested));
        ht.put("pauseCount", String.valueOf(TEST_pauseCount));
        ht.put("resumeCount", String.valueOf(TEST_resumeCount));

        return ht;
    }

    protected Object TEST_GetAttribute(String name) {
        if (name.equals("FlowControl.Count"))
            return Integer.valueOf(inQueueCounter);
        if (name.equals("FlowControl.PeakCount"))
            return Integer.valueOf(TEST_peakCount);
        if (name.equals("FlowControl.IsFlowPaused"))
            return Boolean.valueOf (resumeRequested);
        if (name.equals("FlowControl.PauseCount"))
            return Integer.valueOf(TEST_pauseCount);

        return null;
    }

    protected void status_report(PrintStream dbg) {
        dbg.println("FlowControlState for Connection : " +
            fc.connection);
        dbg.println("\t# pending messages : " + inQueueCounter);
        dbg.println("\t# resumeRequested : " + resumeRequested);
        dbg.println("\t# flowControlWaterMark : " + flowControlWaterMark);
    }

    public String toString() {
        return "ConnectionFlowControlEntry[" + fc.connection + "]";
    }
}

class ConsumerFlowControlEntry extends FlowControlEntry {

    public static final String CONSUMER_FLOWCONTROL_LOGGER_NAME =
           ConnectionImpl.ROOT_LOGGER_NAME + ".consumer.flowcontrol";
    private static final Logger cfcLogger =
        Logger.getLogger(CONSUMER_FLOWCONTROL_LOGGER_NAME,
                         ClientResources.CLIENT_RESOURCE_BUNDLE_NAME);

    protected Consumer consumer;
    protected int maxMsgCount;
    protected int thresholdCount;

    // This flag indicates whether the broker is waiting for the
    // resume flow response.
    protected boolean resumeRequested = false;

    // Undelivered message count.
    protected int inQueueCounter = 0;
    protected int TEST_peakCount = 0;
    protected int TEST_pauseCount = 0;
    protected int TEST_resumeCount = 0;
    protected int TEST_minResumeCount = Integer.MAX_VALUE;
    protected int TEST_lastResumeCount = -1;
    protected long totalCount = 0;

    private static boolean sendResumeOnRecover = true;
    static {
        if (System.getProperty("imq.resume_on_recover") != null)
            sendResumeOnRecover = Boolean.getBoolean(
                "imq.resume_on_recover");
    }

    public ConsumerFlowControlEntry(FlowControl fc,
        ProtocolHandler protocolHandler, Consumer consumer) {
        super(fc, protocolHandler);
        this.consumer = consumer;

        int prefetchMaxMsgCount = consumer.getPrefetchMaxMsgCount();
        int prefetchThresholdPercent =
            consumer.getPrefetchThresholdPercent();

        maxMsgCount = prefetchMaxMsgCount;

        if (cfcLogger.isLoggable(Level.FINE)) {
            cfcLogger.log(Level.FINE, "ConsumerFlowControl["+consumer+"]maxMsgCount="+maxMsgCount);
        }

        if (prefetchThresholdPercent < 0)
            prefetchThresholdPercent = 0;

        if (prefetchThresholdPercent > 100)
            prefetchThresholdPercent = 100;

        thresholdCount = (int)
            ((float) maxMsgCount * prefetchThresholdPercent / 100.0);

        if (thresholdCount >= maxMsgCount)
            thresholdCount = maxMsgCount - 1;
    }

    public synchronized void messageReceived() {
        inQueueCounter++;
        if (inQueueCounter > TEST_peakCount)
            TEST_peakCount = inQueueCounter;
    }

    public synchronized void messageDelivered() {
        inQueueCounter--;
        checkAndResumeFlow();
    }

    /**
     * Reset the flow control state (counters) and send RESUME_FLOW if
     * necessary.
     *
     * This method is called when the application calls
     * session.recover() and all the undelivered messages are
     * discarded.
     *
     * bug 6271876 -- connection flow control
     */
    public synchronized void resetFlowControl(int count) {
        inQueueCounter = 0;
        if (sendResumeOnRecover)
            checkAndResumeFlow();
    }

    public synchronized void setResumeRequested(boolean resumeRequested) {
        if (debug) {
            Debug.println("setResumeRequsted[" + this +"] : " +
                resumeRequested);
        }

        this.resumeRequested = resumeRequested;
        if (resumeRequested) {
            TEST_pauseCount++;
            checkAndResumeFlow();
        }
    }

    protected synchronized void sendResumeFlow() throws Exception {
        int count = -1;
        if (maxMsgCount > 0)
            count = maxMsgCount - inQueueCounter;

        if (maxMsgCount > 0 && count <= 0) {
            fc.removeFromReadyQueue(this);
            return;
        }
        setResumeRequested(false);
        if (cfcLogger.isLoggable(Level.FINEST)) {
            cfcLogger.log(Level.FINEST, 
                "ConsumerFlowControl["+consumer+"]sendResumeFlow("+
                 count+")total="+(totalCount += count));
        }
        protocolHandler.resumeConsumerFlow(consumer, count);
        fc.removeFromReadyQueue(this);

        if (count < TEST_minResumeCount)
            TEST_minResumeCount = count;

        TEST_lastResumeCount = count;
        TEST_resumeCount++;
    }

    /**
     * Check if broker is waiting for RESUME_FLOW, and send it if
     * there is room for more messages.
     *
     * Caller must take care of synchronization...
     */
    private void checkAndResumeFlow() {
        if (debug)
            Debug.println("In checkAndResumeFlow : " + this +
            "\n\tresumeRequested = " + resumeRequested +
            ", maxMsgCount = " + maxMsgCount +
            ", inQueueCounter = " + inQueueCounter +
            ", thresholdCount = " + thresholdCount);

        if (resumeRequested) {
            if ((maxMsgCount <= 0) || (inQueueCounter <= thresholdCount)) {
                fc.addToReadyQueue(this);
            }
        }
    }

    protected Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        ht.put("maxMsgCount", String.valueOf(maxMsgCount));
        ht.put("thresholdCount", String.valueOf(thresholdCount));
        ht.put("inQueueCounter", String.valueOf(inQueueCounter));
        ht.put("peakCount", String.valueOf(TEST_peakCount));
        ht.put("isFlowPaused", String.valueOf(resumeRequested));
        ht.put("pauseCount", String.valueOf(TEST_pauseCount));
        ht.put("resumeCount", String.valueOf(TEST_resumeCount));
        ht.put("lastResumeCount", String.valueOf(TEST_minResumeCount));
        ht.put("minResumeCount", ((TEST_lastResumeCount == -1) ? "---" :
            Integer.toString(TEST_lastResumeCount)));

        return ht;
    }

    protected Object TEST_GetAttribute(String name) {
        if (name.equals("FlowControl.Count"))
            return Integer.valueOf(inQueueCounter);
        if (name.equals("FlowControl.PeakCount"))
            return Integer.valueOf(TEST_peakCount);
        if (name.equals("FlowControl.IsFlowPaused"))
            return Boolean.valueOf (resumeRequested);
        if (name.equals("FlowControl.PauseCount"))
            return Integer.valueOf(TEST_pauseCount);
        if (name.equals("FlowControl.MinResumeCount"))
            return Integer.valueOf(TEST_minResumeCount);

        return null;
    }

    public String toString() {
        return "ConsumerFlowControlEntry[" + consumer + "]";
    }

    protected void status_report(PrintStream dbg) {
        dbg.println("FlowControlState for : " + this);
        dbg.println("\t# pending messages : " + inQueueCounter);
        dbg.println("\t# resumeRequested : " + resumeRequested);
        dbg.println("\t# threshodCount : " + thresholdCount);
        dbg.println("\t# lastResumeCount : " +
            ((TEST_lastResumeCount == -1) ? "---" : Integer.toString(TEST_lastResumeCount)));
    }
}

