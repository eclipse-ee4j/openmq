/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsclient;

import jakarta.jms.*;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.*;

import com.sun.messaging.jmq.jmsclient.resources.*;

/**
 * Class to handle imq protocol flow control.
 *
 * Every connection has its own FlowControl object (thread). Why??? chiaming: answer to the above question:
 *
 * 1. Simplify the implementation. 2. Reduce interference between connections. For example, congestion in one connection
 * does not affect the others. 3. Bugs isolation. Should there is a bug in one connection, the other connections are not
 * affected.
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
    protected static final String imqFlowControl = "imqConnectionFlowControl-";
    protected boolean debug = Debug.debug;

    // Collection of all the FlowControlEntries.
    private Hashtable flowControlTable = null;

    // FlowControlEntries ready for sending resumeFlow.
    private Hashtable readyQueue = null;

    private static boolean FLOWCONTROL_DEBUG = Boolean.getBoolean("imq.flowcontrol.debug");

    // This is replaced witn imqPingInterval as debug interval.
    // private static int FLOWCONTROL_DEBUG_INTERVAL =
    // Integer.getInteger("imq.flowcontrol.debug.interval",
    // 3000).intValue();

    private static String FLOWCONTROL_LOG = System.getProperty("imq.flowcontrol.log");

    private static PrintStream fdbg = null;

    // ping interval -- will be initted with connection.getPingInterval().
    private long pingInterval = 30 * 1000;

    private static final Logger connLogger = ConnectionImpl.connectionLogger;

    private static void initFlowControlDebug() {
        if (FLOWCONTROL_DEBUG == false || fdbg != null) {
            return;
        }

        if (FLOWCONTROL_LOG == null) {
            FLOWCONTROL_LOG = "stderr";
        }

        if (FLOWCONTROL_LOG.equals("stdout")) {
            fdbg = System.out;
        } else if (FLOWCONTROL_LOG.equals("stderr")) {
            fdbg = System.err;
        } else {
            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(FLOWCONTROL_LOG, true);
                fdbg = new PrintStream(fos);
            } catch (java.io.IOException e) {
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
        FlowControlEntry fce = new ConnectionFlowControlEntry(this, connection.getProtocolHandler(), connection.protectMode, connection.flowControlMsgSize,
                connection.flowControlWaterMark);

        flowControlTable.put(connection, fce);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Added connection flow control entry : " + connection);
        }
    }

    /*
     * private void removeConnectionFlowControl(ConnectionImpl connection) { flowControlTable.remove(connection); }
     */

    /**
     * This method is called by ReadChannel when it receives a message packet with connection flow control bit set.
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
        FlowControlEntry fce = new ConsumerFlowControlEntry(this, consumer.getConnection().getProtocolHandler(), consumer);

        flowControlTable.put(consumer, fce);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Added flow control entry : " + fce);
        }
    }

    public void removeConsumerFlowControl(Consumer consumer) {
        FlowControlEntry fce = (FlowControlEntry) flowControlTable.remove(consumer);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("Removed flow control entry : " + fce);
        }
    }

    public void requestResume(Object key) {
        if (debug) {
            Debug.println("**** In requestResume. key = " + key);
        }

        FlowControlEntry fce = getFlowControlEntry(key);

        if (FLOWCONTROL_DEBUG) {
            fdbg.println("PAUSED MESSAGE DELIVERY FOR : " + fce);
        }

        if (connLogger.isLoggable(Level.FINEST)) {
            connLogger.log(Level.FINEST, ClientResources.I_FLOW_CONTROL_PAUSED, fce);
        }

        fce.setResumeRequested(true);
    }

    public void messageReceived(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null) {
            fce.messageReceived();
        }
    }

    public void messageDelivered(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null) {
            fce.messageDelivered();
        }
    }

    // bug 6271876 -- connection flow control
    public void resetFlowControl(Object key, int count) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null) {
            fce.resetFlowControl(count);
        }
    }

    public Hashtable getDebugState(Object key) {
        FlowControlEntry fce = findFlowControlEntry(key);
        if (fce != null) {
            return fce.getDebugState();
        }

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
            if (!(key instanceof Consumer) && !(key instanceof ConnectionImpl)) {
                throw new IllegalArgumentException("getFlowControlEntry: Bad key type. key = " + key);
            }

            throw new java.lang.IllegalStateException("FlowControlEntry not found. key = " + key);
        }

        return fce;
    }

    private FlowControlEntry findFlowControlEntry(Object key) {
        if (key == null) {
            return null;
        }

        FlowControlEntry fce = (FlowControlEntry) flowControlTable.get(key);
        return fce;
    }

    /**
     * Start imq flow control thread for this connection.
     */
    public void start() {
        Thread thread = new Thread(this);
        if (connection.hasDaemonThreads()) {
            thread.setDaemon(true);
        }
        // thread.setName(imqFlowControl + connection.getConnectionID());
        thread.setName(imqFlowControl + connection.getLocalID());
        thread.start();
    }

    /**
     * This method exit only if ReadChannel is closed (connection is closed).
     */
    @Override
    public void run() {
        long lastDump = 0;
        while (true) {
            FlowControlEntry[] rqCopy = null;

            // Wait for something to happen.
            synchronized (this) {
                while (!isClosed && readyQueue.isEmpty()) {

                    try {

                        wait(pingInterval);
                        /**
                         * check if this is a timeout wait.
                         */
                        if (isClosed == false && readyQueue.isEmpty()) {
                            /**
                             * send ping if no activities.
                             */
                            if (protocolHandler.getTimeToPing()) {
                                pingBroker();
                            }

                            protocolHandler.setTimeToPing(true);

                        }
                    } catch (InterruptedException e) {
                        if (debug) {
                            Debug.printStackTrace(e);
                        }
                    }

                    // if (FLOWCONTROL_DEBUG &&
                    // FLOWCONTROL_DEBUG_INTERVAL > 0 &&
                    // ((System.currentTimeMillis() - lastDump) >
                    // FLOWCONTROL_DEBUG_INTERVAL)) {
                    if (FLOWCONTROL_DEBUG) {

                        if ((System.currentTimeMillis() - lastDump) > pingInterval) {

                            status_report();

                            lastDump = System.currentTimeMillis();
                        }
                    } // debug

                } // while

                if (isClosed)
                 {
                    break; // Returns from the run() method.
                }

                rqCopy = (FlowControlEntry[]) readyQueue.values().toArray(new FlowControlEntry[readyQueue.size()]);
            }

            // Send resume flow packets.
            try {
                for (int i = 0; i < rqCopy.length; i++) {
                    rqCopy[i].sendResumeFlow();

                    if (FLOWCONTROL_DEBUG) {
                        fdbg.println("SENDING RESUME_FLOW FOR : " + rqCopy[i]);
                    }

                    if (connLogger.isLoggable(Level.FINEST)) {
                        connLogger.log(Level.FINEST, ClientResources.I_FLOW_CONTROL_RESUME, rqCopy[i]);
                    }
                }
            } catch (Exception e) {
                if (connection.isClosed) {
                    isClosed = true;
                    break; // Returns from the run() method.
                } else {
                    Debug.printStackTrace(e);
                }
            }
        }
    }

    private void pingBroker() {

        try {
            // bug 6155026 - client sends PING before authenticated.
            // we decided to have broker ignore PING.
            // if ( connection.isClosed == false ) {
            protocolHandler.ping();
            // }
        } catch (JMSException e) {
            if (debug) {
                Debug.printStackTrace(e);
            }
        }
    }

    private void status_report() {
        fdbg.println("debug_interval = " + pingInterval);
        fdbg.println("\n-------------------------------- " + this + " : " + new java.util.Date());

        Enumeration enum2 = flowControlTable.elements();

        while (enum2.hasMoreElements()) {
            FlowControlEntry fce = (FlowControlEntry) enum2.nextElement();
            fce.status_report(fdbg);
        }
    }

    protected synchronized void addToReadyQueue(FlowControlEntry fce) {
        if (debug) {
            Debug.println("In addToReadyQueue : " + fce);
        }
        readyQueue.put(fce, fce);
        notifyAll();
    }

    protected synchronized void removeFromReadyQueue(FlowControlEntry fce) {
        if (debug) {
            Debug.println("In addToReadyQueue : " + fce);
        }

        readyQueue.remove(fce);
    }

    /**
     * ReadChannel calls this method when connection is closed.
     */
    public synchronized void close() {
        isClosed = true;
        notifyAll();
    }

    @Override
    public void dump(PrintStream ps) {
    }
}

