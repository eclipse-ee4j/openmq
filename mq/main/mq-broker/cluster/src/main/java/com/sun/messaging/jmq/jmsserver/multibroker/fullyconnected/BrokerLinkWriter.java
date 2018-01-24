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
 * @(#)BrokerLinkWriter.java	1.15 07/02/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;
import com.sun.messaging.jmq.io.*;

/**
 * This class implements a dedicated packet writer thread. Each
 * BrokerLink instance has its own BrokerLinkWriter instance..
 */
class BrokerLinkWriter extends Thread {
    private OutputStream os = null;
    private LinkedList q = null;
    private BrokerLink parent;

    private boolean flowControl = false;
    private ArrayList backupQ = null;

    private static final int MAX_BUFFER_SIZE = 8192;
    //private Logger logger = Globals.getLogger();

    private boolean stopThread = false;
    private boolean threadInWaiting = false;
    private boolean shutdownOutput = false;

    private boolean writeActive = false;

    public BrokerLinkWriter(BrokerLink parent) {
        this.parent = parent;
        setName("BrokerLinkWriter:" + parent.getRemoteString());
        setDaemon(true);
    }

    /**
     * Set the output stream and start the writer thread.
     */
    public void startWriterThread(OutputStream os) {
        this.os = new BufferedOutputStream(os, MAX_BUFFER_SIZE);
        q = new LinkedList();

        flowControl = false;
        backupQ = new ArrayList();
        start();
    }

    public void setFlowControl(boolean enabled) {
        synchronized (q) {
            if (stopThread || this.shutdownOutput) return;

            flowControl = enabled;
            if (flowControl == false) {
                if (! backupQ.isEmpty()) {
                    q.addAll(0, backupQ);
                    backupQ.clear();
                    q.notifyAll();
                }
            }
        }
    }

    /**
     * Terminate the writer thread.
     */
    public void shutdown() {
        if (q == null) return;
        synchronized (q) {
            if (shutdownOutput) return;
            stopThread = true;
            q.notifyAll();
        }
    }

    public boolean isOutputShutdown() {
        if (q == null) return false;
        synchronized(q) {
            return shutdownOutput;
        }
    }

    public void sendPacket(Object p, boolean shutdownOutput)
    throws IOException {
        sendPacket(p, shutdownOutput, false);
    }

    /**
     * Adds a packet to the queue and wakes up the writer thread.
     */
    public void sendPacket(Object p, boolean shutdownOutput, boolean urgent)
    throws IOException {
        synchronized (q) {
            if (stopThread || this.shutdownOutput) {
                throw new IOException(
                  "Packet send failed. Unreachable BrokerAddress : " + parent.getRemoteString());
            }
            if (!shutdownOutput) { 
                if (!urgent) {
                    q.add(p);
                } else {
                    q.addFirst(p);
                }
                q.notifyAll();
                return;
            }
            this.shutdownOutput = true;
            while (this.isAlive() && !threadInWaiting) {   
                try {
                q.wait();
                } catch (Exception e) {/* Ignore */}
            }
            try {
                sendPacketDirect(p);
                stopThread = true;
                q.notifyAll();
                parent.closeConn(true, false);
                if (!parent.isOutputShutdown()) {
                    throw new IOException("socket output shutdown check failed");
                }
            } catch (IOException e) {
                this.shutdownOutput = false;
                parent.closeConn(false, true); 
                throw e;
            }
        }
    }

    public void sendPacket(Object p) throws IOException {
        sendPacket(p, false, false);
    }


    private void sendPacketDirect(Object p) throws IOException {
        if (p instanceof GPacket) {
           sendPacketDirect((GPacket)p, true);
        } else {
           sendPacketDirect((Packet)p, true);
        }
    }

    /**
     * Actually writes the packet to the wire.
     */
    private void sendPacketDirect(GPacket gp, boolean doFlush) throws IOException {
        if (os == null) throw new IOException("os null");

        try {
            gp.write(os);
            if (doFlush) os.flush();
            if (gp.getType() != ProtocolGlobals.G_PING) writeActive = true;
        }
        catch (IOException e) {
            os = null;
            throw e;
        }
    }

    /**
     * Actually writes the packet to the wire.
     */
    private void sendPacketDirect(Packet p, boolean doFlush) throws IOException {
        if (os == null) throw new IOException("os null");

        try {
            p.writePacket(os);
            if (doFlush) os.flush();
            if (p.getPacketType() != Packet.PING) writeActive = true;
        }
        catch (IOException e) {
            os = null;
            throw e;
        }
    }

    protected void clearWriteActiveFlag() {
        writeActive = false;
    }

    protected boolean isWriteActive() { 
        return writeActive;
    }

    public void run() {
        ArrayList l = new ArrayList();

        while (true) {
            l.clear();
            synchronized (q) {
                while (q.isEmpty() && stopThread == false) {
                    try {
                        threadInWaiting = true;
                        q.notifyAll();
                        q.wait();
                        threadInWaiting = false;
                    }
                    catch (Exception e) {}
                }

                if (stopThread)
                    return;

                int n = 0;
                boolean bufferFull = false;

                while (! q.isEmpty()) {
                    Object o = q.getFirst();

                    GPacket gp = null;
                    Packet p = null;

                    try {
                        gp = (GPacket) o;
                    }
                    catch (ClassCastException cce) {
                        // Not a GPacket.
                        p = (Packet) o;
                    }

                    if (gp != null) {
                        if (flowControl && gp.getBit(gp.F_BIT)) {
                            backupQ.add(gp);
                        }
                        else {
                            if (n + gp.getSize() > MAX_BUFFER_SIZE) {
                                bufferFull = true;
                                break;
                            }

                            l.add(gp);
                            n += gp.getSize();
                        }
                    }
                    else {
                        if (flowControl && p.getFlag(p.USE_FLOW_CONTROL)) {
                            backupQ.add(p);
                        }
                        else {
                            if (n + p.getPacketSize() > MAX_BUFFER_SIZE) {
                                bufferFull = true;
                                break;
                            }

                            l.add(p);
                            n += p.getPacketSize();
                        }
                    }

                    q.removeFirst();
                }

                // If nothing was written because the first packet
                // was too big...
                if (l.size() == 0 && bufferFull && !q.isEmpty())
                    l.add(q.removeFirst());
            }

            // The following operations do the actual socket I/O,
            // and must be done outside the synchronized block.
            try {
                for (int i = 0; i < l.size(); i++) {
                    try {
                        GPacket gp = (GPacket) l.get(i);
                        sendPacketDirect(gp, false);
                    }
                    catch (ClassCastException cce) {
                        Packet p = (Packet) l.get(i);
                        sendPacketDirect(p, false);
                    }
                }

                if (os != null) os.flush();
            }
            catch (Exception e) {
                os = null;
            }
        }
    }
}

/*
 * EOF
 */
