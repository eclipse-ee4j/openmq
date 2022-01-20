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

package com.sun.messaging.jmq.jmsclient;

import java.io.PrintStream;
import java.util.*;
import java.util.logging.*;

import com.sun.messaging.jmq.jmsclient.resources.*;

class ConsumerFlowControlEntry extends FlowControlEntry {

    public static final String CONSUMER_FLOWCONTROL_LOGGER_NAME = ConnectionImpl.ROOT_LOGGER_NAME + ".consumer.flowcontrol";
    private static final Logger cfcLogger = Logger.getLogger(CONSUMER_FLOWCONTROL_LOGGER_NAME, ClientResources.CLIENT_RESOURCE_BUNDLE_NAME);

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
        if (System.getProperty("imq.resume_on_recover") != null) {
            sendResumeOnRecover = Boolean.getBoolean("imq.resume_on_recover");
        }
    }

    ConsumerFlowControlEntry(FlowControl fc, ProtocolHandler protocolHandler, Consumer consumer) {
        super(fc, protocolHandler);
        this.consumer = consumer;

        int prefetchMaxMsgCount = consumer.getPrefetchMaxMsgCount();
        int prefetchThresholdPercent = consumer.getPrefetchThresholdPercent();

        maxMsgCount = prefetchMaxMsgCount;

        if (cfcLogger.isLoggable(Level.FINE)) {
            cfcLogger.log(Level.FINE, "ConsumerFlowControl[" + consumer + "]maxMsgCount=" + maxMsgCount);
        }

        if (prefetchThresholdPercent < 0) {
            prefetchThresholdPercent = 0;
        }

        if (prefetchThresholdPercent > 100) {
            prefetchThresholdPercent = 100;
        }

        thresholdCount = (int) ((float) maxMsgCount * prefetchThresholdPercent / 100.0);

        if (thresholdCount >= maxMsgCount) {
            thresholdCount = maxMsgCount - 1;
        }
    }

    @Override
    public synchronized void messageReceived() {
        inQueueCounter++;
        if (inQueueCounter > TEST_peakCount) {
            TEST_peakCount = inQueueCounter;
        }
    }

    @Override
    public synchronized void messageDelivered() {
        inQueueCounter--;
        checkAndResumeFlow();
    }

    /**
     * Reset the flow control state (counters) and send RESUME_FLOW if necessary.
     *
     * This method is called when the application calls session.recover() and all the undelivered messages are discarded.
     *
     * bug 6271876 -- connection flow control
     */
    @Override
    public synchronized void resetFlowControl(int count) {
        inQueueCounter = 0;
        if (sendResumeOnRecover) {
            checkAndResumeFlow();
        }
    }

    @Override
    public synchronized void setResumeRequested(boolean resumeRequested) {
        if (debug) {
            Debug.println("setResumeRequsted[" + this + "] : " + resumeRequested);
        }

        this.resumeRequested = resumeRequested;
        if (resumeRequested) {
            TEST_pauseCount++;
            checkAndResumeFlow();
        }
    }

    @Override
    protected synchronized void sendResumeFlow() throws Exception {
        int count = -1;
        if (maxMsgCount > 0) {
            count = maxMsgCount - inQueueCounter;
        }

        if (maxMsgCount > 0 && count <= 0) {
            fc.removeFromReadyQueue(this);
            return;
        }
        setResumeRequested(false);
        if (cfcLogger.isLoggable(Level.FINEST)) {
            cfcLogger.log(Level.FINEST, "ConsumerFlowControl[" + consumer + "]sendResumeFlow(" + count + ")total=" + (totalCount += count));
        }
        protocolHandler.resumeConsumerFlow(consumer, count);
        fc.removeFromReadyQueue(this);

        if (count < TEST_minResumeCount) {
            TEST_minResumeCount = count;
        }

        TEST_lastResumeCount = count;
        TEST_resumeCount++;
    }

    /**
     * Check if broker is waiting for RESUME_FLOW, and send it if there is room for more messages.
     *
     * Caller must take care of synchronization...
     */
    private void checkAndResumeFlow() {
        if (debug) {
            Debug.println("In checkAndResumeFlow : " + this + "\n\tresumeRequested = " + resumeRequested + ", maxMsgCount = " + maxMsgCount
                    + ", inQueueCounter = " + inQueueCounter + ", thresholdCount = " + thresholdCount);
        }

        if (resumeRequested) {
            if ((maxMsgCount <= 0) || (inQueueCounter <= thresholdCount)) {
                fc.addToReadyQueue(this);
            }
        }
    }

    @Override
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
        ht.put("minResumeCount", ((TEST_lastResumeCount == -1) ? "---" : Integer.toString(TEST_lastResumeCount)));

        return ht;
    }

    @Override
    protected Object TEST_GetAttribute(String name) {
        if (name.equals("FlowControl.Count")) {
            return Integer.valueOf(inQueueCounter);
        }
        if (name.equals("FlowControl.PeakCount")) {
            return Integer.valueOf(TEST_peakCount);
        }
        if (name.equals("FlowControl.IsFlowPaused")) {
            return Boolean.valueOf(resumeRequested);
        }
        if (name.equals("FlowControl.PauseCount")) {
            return Integer.valueOf(TEST_pauseCount);
        }
        if (name.equals("FlowControl.MinResumeCount")) {
            return Integer.valueOf(TEST_minResumeCount);
        }

        return null;
    }

    @Override
    public String toString() {
        return "ConsumerFlowControlEntry[" + consumer + "]";
    }

    @Override
    protected void status_report(PrintStream dbg) {
        dbg.println("FlowControlState for : " + this);
        dbg.println("\t# pending messages : " + inQueueCounter);
        dbg.println("\t# resumeRequested : " + resumeRequested);
        dbg.println("\t# threshodCount : " + thresholdCount);
        dbg.println("\t# lastResumeCount : " + ((TEST_lastResumeCount == -1) ? "---" : Integer.toString(TEST_lastResumeCount)));
    }
}
