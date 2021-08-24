/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.admin.bkrutil;

import jakarta.jms.*;

import com.sun.messaging.jmq.admin.util.CommonGlobals;

class MessageAckThread implements Runnable {
    private Thread ackThread = null;
    private BrokerAdminConn ba;
    private boolean msgReceived = false;
    private long timeout = 30000;
    private boolean debug = false, stopRequested = false;

    MessageAckThread(BrokerAdminConn ba) {
        debug = BrokerAdminConn.getDebug();

        if (debug) {
            CommonGlobals.stdOutPrintln("***** Created MessageAckThread");
        }
        this.ba = ba;
    }

    public synchronized void start() {
        if (ackThread == null) {
            ackThread = new Thread(this, "JMQ Administration MessageAckThread");
            ackThread.start();
            if (debug) {
                CommonGlobals.stdOutPrintln("***** Started MessageAckThread");
            }
        }
    }

    public synchronized void stop() {
        stopRequested = true;
    }

    @Override
    public void run() {
        Message mesg = null;

        ba.setBusy(true);

        while (!msgReceived && ackThread != null && !stopRequested) {
            try {
                mesg = ba.receiver.receive(timeout);

                if (mesg != null) {
                    if (debug) {
                        CommonGlobals.stdOutPrintln("***** MessageAckThread: received reply message !");
                    }
                    msgReceived = true;
                    if (debug) {
                        CommonGlobals.stdOutPrintln("***** MessageAckThread: acknowledging reply message.");
                    }
                    mesg.acknowledge();
                    synchronized (this) {
                        ba.setBusy(false);
                        ba.sendStatusEvent(mesg, null);
                    }
                } else {
                    synchronized (this) {
                        if (stopRequested) {
                            if (debug) {
                                CommonGlobals.stdOutPrintln("***** MessageAckThread: receive() timed out. Not retrying (stop requested).");
                            }
                            stopRequested = false;
                            ackThread = null;
                            return;
                        }
                    }

                    if (debug) {
                        CommonGlobals.stdOutPrintln("***** MessageAckThread: receive() timed out. Retrying...");
                    }
                }
            } catch (Exception e) {
                /*
                 * Caught exception while waiting for reply message. Report error in status event.
                 */
                synchronized (this) {
                    ba.setBusy(false);
                    ba.sendStatusEvent(null, e);
                }
            }
        }

        ba.setBusy(false);
    }
}

