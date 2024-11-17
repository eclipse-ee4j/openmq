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

@SuppressWarnings("JdkObsolete")
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

    ConnectionFlowControlEntry(FlowControl fc, ProtocolHandler protocolHandler, boolean enableFlowControlCheck, int flowControlChunkSize,
            int flowControlWaterMark) {

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
    @Override
    public void messageReceived() {
        synchronized (this) {
            inQueueCounter++;
            if (inQueueCounter > TEST_peakCount) {
                TEST_peakCount = inQueueCounter;
            }
        }
    }

    /**
     * Handle messageDelivered event.
     *
     * Decrement the inQueueCounter. If resumeFlow has been requested AND if there is room in the queue, add this entry to
     * the readyQueue.
     */
    @Override
    public void messageDelivered() {
        synchronized (this) {
            inQueueCounter--;

            if (enableFlowControlCheck) {
                checkAndResumeFlow();
            }
        }
    }

    /**
     * Reset the flow control state counters and send resume flow if necessary. This is called when session is closed or
     * cinsumer is closed. bug 6271876 -- connection flow control
     */
    @Override
    public void resetFlowControl(int reduceFlowCount) {

        inQueueCounter = inQueueCounter - reduceFlowCount;

        if (inQueueCounter < 0) {
            inQueueCounter = 0;
        }

        if (enableFlowControlCheck) {
            checkAndResumeFlow();
        }

    }

    /**
     * Handle resume request from the broker.
     */
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
        setResumeRequested(false);
        protocolHandler.resumeFlow(flowControlChunkSize);
        fc.removeFromReadyQueue(this);

        TEST_resumeCount++;
    }

    private synchronized void checkAndResumeFlow() {
        if (resumeRequested) {
            if ((enableFlowControlCheck == false) || (inQueueCounter < flowControlWaterMark)) {
                fc.addToReadyQueue(this);
            }
        }
    }

    @Override
    protected Hashtable getDebugState() {
        Hashtable ht = new Hashtable();

        ht.put("enableFlowControlCheck", String.valueOf(enableFlowControlCheck));
        ht.put("inQueueCounter", String.valueOf(inQueueCounter));
        ht.put("peakCount", String.valueOf(TEST_peakCount));
        ht.put("isFlowPaused", String.valueOf(resumeRequested));
        ht.put("pauseCount", String.valueOf(TEST_pauseCount));
        ht.put("resumeCount", String.valueOf(TEST_resumeCount));

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

        return null;
    }

    @Override
    protected void status_report(PrintStream dbg) {
        dbg.println("FlowControlState for Connection : " + fc.connection);
        dbg.println("\t# pending messages : " + inQueueCounter);
        dbg.println("\t# resumeRequested : " + resumeRequested);
        dbg.println("\t# flowControlWaterMark : " + flowControlWaterMark);
    }

    @Override
    public String toString() {
        return "ConnectionFlowControlEntry[" + fc.connection + "]";
    }
}

