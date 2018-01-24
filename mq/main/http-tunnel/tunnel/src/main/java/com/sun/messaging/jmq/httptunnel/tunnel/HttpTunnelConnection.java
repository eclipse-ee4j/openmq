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
 * @(#)HttpTunnelConnection.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.timer.*;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

/**
 * This class implements the state engine for a HTTP tunnel
 * connection. It uses the unreliable packet delivery mechanism
 * provided by the appropriate implementation of
 * {@link com.sun.messaging.jmq.httptunnel.tunnel.HttpTunnelDriver}.
 * <p>
 * It also provides methods to read bytes from and write bytes to
 * the connection stream.
 */
public class HttpTunnelConnection implements HttpTunnelDefaults {
    private HttpTunnelDriver wire;
    private int connId;

    private int nextRecvSeq;
    private int rxWindowMax;
    private HttpTunnelPacket[] recvQ;
    private Object recvQLock;
    private int readOffset;
    private boolean sendWindowUpdate;
    private boolean connCloseReceived;
    private boolean connAbortReceived;
    private int rxConnCloseSeq;

    private int lastAckSeq;
    private int nextSendSeq;
    private int txWindowMax;
    private int dupAckCount;
    private Vector sendQ;
    private Object sendQLock;
    private boolean txDataDisabled;
    private boolean connCloseSent;
    private int txConnCloseSeq;

    private Hashtable rexmitTable;
    private long RTO;
    private long measuredRTO;

    private int pullPeriod;
    private int connectionTimeout;

    private int nRetransmit;
    private int nFastRetransmit;

    private static MQTimer timer = new MQTimer(true);

    private static long CLOSE_WAIT_TIMEOUT =
        Long.getLong("imq.httptunnel.close_wait", 60000).longValue();

    private String remoteip = null;

    /**
     * Create and initialize a new connection object.
     */
    public HttpTunnelConnection(int connId, HttpTunnelDriver wire) {
        this.wire = wire;
        this.connId = connId;

        nextRecvSeq = 0;
        rxWindowMax = DEFAULT_WINDOW_SIZE;
        recvQ = new HttpTunnelPacket[DEFAULT_WINDOW_SIZE];
        recvQLock = new Object();

        readOffset = 0;
        sendWindowUpdate = false;

        connCloseReceived = false;
        connAbortReceived = false;
        rxConnCloseSeq = 0;

        lastAckSeq = -1; // = (remote initial nextRecvSeq) - 1
        nextSendSeq = 0;
        txWindowMax = 16; // Start with a reasonable default value.
        dupAckCount = 0;
        sendQ = new Vector();
        sendQLock = new Object();
        txDataDisabled = false;
        connCloseSent = false;
        txConnCloseSeq = 0;

        rexmitTable = new Hashtable();
        RTO = INITIAL_RETRANSMIT_PERIOD;
        measuredRTO = INITIAL_RETRANSMIT_PERIOD;

        pullPeriod = -1;
        connectionTimeout = -1;

        nRetransmit = 0;
        nFastRetransmit = 0;

        TimerTask dummyTask = new TimerTask() {
            public void run() {}
        };

        try {
            timer.schedule(dummyTask, 1000);
            dummyTask.cancel();
        }
        catch (IllegalStateException ise) {
            timer = new MQTimer(true);
        }
        // DebugStats ds = new DebugStats(this);
    }

    public void setRemoteAddr(String ip) {
        remoteip = ip;
    }

    public String getRemoteAddr() {
        return remoteip;
    }

    /**
     * Check if a given sequence number fits within a given range.
     */
    private boolean checkRange(int first, int last, int n) {
        if (first < last)
            return (n >= first && n <= last);
        else if (first > last)
            return (n >= first || n <= last);
        else
            return (first == n);
    }

    /**
     * Receive a data/acknowledgement packet from the driver.
     */
    public void receivePacket(HttpTunnelPacket p, boolean moreData) {
        int packetType = p.getPacketType();

        if (packetType == DATA_PACKET)
            receiveData(p, moreData);
        else
            receiveAck(p);
        return;
    }

    /**
     * Handles a data packet from the network.
     * @param p Incoming packet
     * @param moreData <code>true</code> if driver has more data
     * packets that need to be processed. If so, this method defers
     * sending an acknowledgement, if possible.
     */
    private void receiveData(HttpTunnelPacket p, boolean moreData) {
        int seq = p.getSequence();
        boolean ackNow = true;

        synchronized (recvQLock) {
            // BugID 4758336 : Check for CONN_CLOSE_PACKET even if
            // recvQ is null.
            if (p.getPacketType() == CONN_CLOSE_PACKET) {
                connCloseReceived = true;
                rxConnCloseSeq = seq;
            }

            if (recvQ == null) {
                // BugID 4758336 : Drop data packets. Connection is
                // being closed. The only packet we need to worry
                // about at this point is the CONN_CLOSE_PACKET...
                if (connCloseReceived)
                    flushAndClose();

                return;
            }

            if (checkRange(nextRecvSeq, nextRecvSeq + rxWindowMax - 1, seq))
            {
                // If the close packet was generated due to ungraceful
                // termination on the other side, do not accept any
                // packets beyond the close - because there is nobody
                // at the other end to do retransmissions anymore...
                if (connCloseReceived && p.getPacketType() == DATA_PACKET) {
                    if (checkRange(
                        rxConnCloseSeq, rxConnCloseSeq + rxWindowMax - 1,
                        seq))
                        return;
                }

                // Store the packet.
                int first = nextRecvSeq;
                if (recvQ[0] != null)
                    first = recvQ[0].getSequence();
                recvQ[seq - first] = p;

                if (seq == nextRecvSeq) {
                    int n;
                    for (n = 0; n < recvQ.length; n++) {
                        if (recvQ[n] == null)
                            break;
                    }
                    nextRecvSeq = first + n;
                    rxWindowMax = recvQ.length - n;

                    recvQLock.notifyAll();

                    // If driver says it has more data packets,
                    // don't ack this packet right away...
                    ackNow = ! moreData;
                }
            }

            if (ackNow)
                sendAck();
        }
    }

    /**
     * Send an acknowledgement packet.
     * Besides acknowledging a data packet sequence number, an
     * acknowledgement packet also conveys receivers window size.
     */
    private void sendAck() {
        if (connAbortReceived)
            return;

        int seq = (nextRecvSeq - 1);
        HttpTunnelPacket ack = new HttpTunnelPacket();

        ack.setPacketType(ACK);
        ack.setPacketBody(null);
        ack.setConnId(connId);
        ack.setSequence(seq);
        ack.setWinsize(rxWindowMax);
        ack.setChecksum(0);
        wire.sendPacket(ack);

        if (connCloseReceived && rxConnCloseSeq == seq) {
            // We ack the CONN_CLOSE_PACKET only once.
            wire.shutdown(connId);
        }

        if (connCloseReceived == false && rxWindowMax == 0) {
            // This ack tells the sender to stop sending packets
            // since the receiver is flooded. The receiver must send
            // 'window update' ack whenever it is ready to receive
            // more packets. If the sender somehow misses this
            // 'window update' it will rely on the 'window update
            // probe' mechanism and eventually learn that it can
            // resume transmission.
            sendWindowUpdate = true;
        } else {
            sendWindowUpdate = false;
        }
    }

    /**
     * Schedule a packet for retransmission.
     * This method always uses the current value of <code>RTO</code>
     * as retransmission timeout.
     */
    private void startRetransmitTimer(int seq) {
        HttpTunnelTimerTask task = new HttpTunnelTimerTask(this, seq);
        synchronized (rexmitTable) {
            rexmitTable.put(Integer.toString(seq), task);
            timer.schedule(task, RTO);
        }
    }

    /**
     * Cancel a scheduled packet retransmission. This method is
     * called when a packet is acknowledged.
     */
    private void stopRetransmitTimer(int seq) {
        HttpTunnelTimerTask task = null;
        synchronized (rexmitTable) {
            task = (HttpTunnelTimerTask)
                rexmitTable.remove(Integer.toString(seq));
            if (task != null)
                task.cancel();
        }
    }

    /**
     * Cancels all the scheduled packet retransmissions. This method is
     * called during connection close sequence.
     */
    private void stopRetransmitTimers() {
        synchronized (rexmitTable) {
            for (Enumeration e = rexmitTable.elements();
                e.hasMoreElements(); /* */) {
                ((HttpTunnelTimerTask)e.nextElement()).cancel();
            }
            rexmitTable.clear();
        }
    }

    //Random r = new Random();
    //int total = 0;
    //int drop = 0;

    static final int ERRORRATE = -1;
    /**
     * Artificially induces packet transmission errors. This method is
     * used ONLY FOR TESTING.
     */
    /*
    private boolean dropPacket(ExtHttpTunnelPacket p) {
        total++;
        if ((int)(r.nextFloat()*100) < ERRORRATE) {
            drop++;
            if (drop % 100 == 0)
                System.out.println(
                    "Packet drop rate : " + (drop * 100 / total) + " %");
            return true;
        }
        return false;
    }
    */

    /**
     * Send a packet reliably over this connection.
     * This method allocates a sequence number for this packet and
     * lines up the packet for reliable delivery.
     */
    private void sendData(ExtHttpTunnelPacket p) throws IOException {
        synchronized (sendQLock) {
            if (txDataDisabled) {
                throw new IOException("Connection closed.");
            }
            if (sendQ == null) {
                // BugID 4758336 : If this connection has already been
                // closed by the peer, and if this method is called
                // for sending CONN_CLOSE_PACKET from this end, do not
                // generate an IOException.
                if (p.getPacketType() == CONN_CLOSE_PACKET) {
                    return;
                }

                if (connCloseReceived)
                    throw new IOException("Broken pipe.");
                else
                    throw new IOException("Connection closed.");
            }

            int seq = nextSendSeq++;
            p.setSequence(seq);

            long waitStart = -1;

            while (txWindowMax == 0 || checkRange(lastAckSeq + 1,
                lastAckSeq + txWindowMax, seq) == false) {

                if (waitStart == -1)
                    waitStart = System.currentTimeMillis();

                try {
                    // Wait for window update
                    sendQLock.wait(MAX_RETRANSMIT_PERIOD);
                }
                catch (Exception e) {}

                if (sendQ == null) {
                    throw new IOException("Broken pipe.");
                }

                // If txWindowMax is 0, keep sending the current
                // packet periodically as a 'window update probe'.
                if (txWindowMax == 0 &&
                    (System.currentTimeMillis() - waitStart >=
                        MAX_RETRANSMIT_PERIOD)) {
                    // Set the dirty flag so that we don't updateRTO
                    // based on ACK for the probe packet.
                    p.setDirtyFlag(true);
                    wire.sendPacket(p);
                    waitStart = -1;
                }
            }
            sendQ.addElement(p);

            if (p.getPacketType() == CONN_CLOSE_PACKET) {
                txDataDisabled = true;

                connCloseSent = true;
                txConnCloseSeq = seq;
            }
        }

        p.setTxTime(System.currentTimeMillis());
        startRetransmitTimer(p.getSequence());

        // if (! dropPacket(p))
            wire.sendPacket(p);
    }

    /**
     * Retransmit a packet.
     * @param seq Sequence number to be retransmitted.
     * @param fromTimer <code>true</code> if the retransmission is
     * due to timer expiry (as against the 'fast retransmission' mechanism.)
     */
    public void retransmitPacket(int seq, boolean fromTimer) {
        ExtHttpTunnelPacket p = null;
        boolean doSend = false;

        synchronized (sendQLock) {
            if (sendQ == null)
                return;

            if (sendQ.size() == 0)
                return;

            int first = ((HttpTunnelPacket) sendQ.elementAt(0)).getSequence();
            int last = first + sendQ.size() - 1;
            if (checkRange(first, last, seq) == false)
                return;

            p = (ExtHttpTunnelPacket) sendQ.elementAt(seq - first);
            p.setDirtyFlag(true);

            if (first == seq)
                doSend = true;
        }

        if (fromTimer)
            startRetransmitTimer(p.getSequence());

        if (doSend) {
            int count = p.getRetransmitCount();
            p.setRetransmitCount(count + 1);

            wire.sendPacket(p);

            if (fromTimer && p.getRetransmitCount() > 1) {
                RTO <<= 1;
                if (RTO > MAX_RETRANSMIT_PERIOD)
                    RTO = MAX_RETRANSMIT_PERIOD;
            }

            if (fromTimer)
                nRetransmit++;
            else
                nFastRetransmit++;
        }
    }

    /**
     * Handles an acknowledgement packet from the network.
     * If the acknowledged sequence number is valid - <p>
     * 1. Removes the buffered packets and stops retransmission timers. <p>
     * 2. Calculates the roundtrip delay and updates retransmission
     * timeout. <p>
     * 3. Sets the txWindowMax to receiver's advertized window size.
     * (flow control). <p>
     * 4. Retransmits data packet using the 'fast retransmit' mechanism
     */
    private void receiveAck(HttpTunnelPacket p) {
        int seq = p.getSequence();
        synchronized (sendQLock) {
            if (sendQ == null) {
                return;
            }

            if (connCloseSent && txConnCloseSeq == seq) {
                txShutdown();
                wire.shutdown(connId);
                return;
            }

            if (sendQ.size() > 0) {
                int first = ((HttpTunnelPacket)
                    sendQ.elementAt(0)).getSequence();
                int last = first + sendQ.size() - 1;
                
                if (checkRange(first, last, seq) == true) {
                    while (true) {
                        ExtHttpTunnelPacket tmp =
                            (ExtHttpTunnelPacket) sendQ.elementAt(0);
                        sendQ.removeElementAt(0);
                        stopRetransmitTimer(tmp.getSequence());
                        if (tmp.getSequence() == seq) {
                            if (!tmp.getDirtyFlag())
                                updateRTO(tmp);
                            break;
                        }
                    }
                    dupAckCount = 0;
                    RTO = measuredRTO;
                } else if (seq == (first - 1)) {
                    dupAckCount++;
                    if (dupAckCount == FAST_RETRANSMIT_ACK_COUNT) {
                        retransmitPacket(first, false);
                    }
                }
            }

            lastAckSeq = seq;
            txWindowMax = p.getWinsize();
            sendQLock.notifyAll();
        }
    }

    /**
     * Update the round trip delay using the latest round
     * trip time measurement. This method uses the simple approach
     * described in original TCP RFC 793. Current TCP implementations
     * use much more efficient approach for RTO calculation, but
     * for now this should be adequate.
     */
    private void updateRTO(ExtHttpTunnelPacket p) {
        measuredRTO >>= 1;
        long RTT = System.currentTimeMillis() - p.getTxTime();

        // ALPHA = 7/8 = 0.875; BETA = 2
        long SRTT = ((measuredRTO << 3) - measuredRTO + RTT) >>> 3;

        measuredRTO = SRTT << 1;
        if (measuredRTO < MIN_RETRANSMIT_PERIOD)
            measuredRTO = MIN_RETRANSMIT_PERIOD;
    }

    /**
     * Consume the data received on this connection.
     */
    public int readData(byte[] buffer) throws IOException {
        return readData(buffer, 0, buffer.length);
    }

    /**
     * Throws away the packets from the receive queue that have
     * been consumed.
     */
    private void discardPackets(int n) {
        System.arraycopy(recvQ, n, recvQ, 0, recvQ.length - n);
        for (int i = recvQ.length - n; i < recvQ.length; i++)
            recvQ[i] = null;
        rxWindowMax += n;
    }

    /**
     * Consume the data received on this connection.
     */
    public int readData(byte[] buffer, int off, int maxlen)
        throws IOException {
        int copied = 0;
        boolean endOfStream = false;
        boolean windowMoved = false;

        synchronized (recvQLock) {
            while (true) {
                if (recvQ == null) {
                    if (connCloseReceived)
                        throw new IOException("Connection reset by peer.");
                    else
                        throw new IOException("Connection closed.");
                }
                if (recvQ[0] != null) {
                    int packetType = recvQ[0].getPacketType();
                    if (packetType == DATA_PACKET ||
                        packetType == CONN_CLOSE_PACKET)
                    break;

                    discardPackets(1); // e.g. CONN_OPTION_PACKET
                    windowMoved = true;
                    continue;
                }

                try {
                    recvQLock.wait();
                }
                catch (Exception e) {}
            }

            int n = 0;
            while (n < recvQ.length && recvQ[n] != null && copied < maxlen) {
                HttpTunnelPacket p = recvQ[n];

                if (p.getPacketType() == CONN_CLOSE_PACKET) {
                    endOfStream = true;
                    n++;
                    continue;
                }

                if (p.getPacketType() == CONN_OPTION_PACKET) {
                    n++;
                    continue;
                }

                int len = p.getPacketDataSize() - readOffset;

                if (len > maxlen - copied)
                    len = maxlen - copied;

                if (buffer != null) {
                    System.arraycopy(p.getPacketBody(), readOffset,
                        buffer, off, len);
                }

                readOffset += len;
                off += len;
                copied += len;

                if (readOffset == p.getPacketDataSize()) {
                    readOffset = 0;
                    n++;
                }
            }

            if (endOfStream) {
                rxShutdown();
            } else {
                if (n > 0) {
                    discardPackets(n);
                    windowMoved = true;
                }

                // Receive window has moved.
                if (windowMoved && sendWindowUpdate) {
                    // Send window update acknowledgement.
                    sendAck();
                }
            }
        }
        return copied;
    }

    /**
     * Return the number of bytes available for reading.
     */
    public int available() throws IOException {
        synchronized (recvQLock) {
            int ret = 0;

            if (recvQ == null || recvQ[0] == null)
                return 0;

            if (recvQ[0].getPacketType() == DATA_PACKET)
                ret += (recvQ[0].getPacketDataSize() - readOffset);

            int n = 1;
            while (n < recvQ.length && recvQ[n] != null) {
                if (recvQ[n].getPacketType() == DATA_PACKET)
                    ret += recvQ[n].getPacketDataSize();
                n++;
            }

            return ret;
        }
    }

    /**
     * Send application data.
     */
    public void writeData(byte[] data) throws IOException {
        if (data == null || data.length == 0)
            return;

        ExtHttpTunnelPacket p = new ExtHttpTunnelPacket();
        p.setPacketType(DATA_PACKET);
        p.setPacketBody(data);
        p.setConnId(connId);
        p.setWinsize(0);
        p.setChecksum(0);

        sendData(p);
    }

    /**
     * Ignore the current connection state and close the connection.
     */
    private void flushAndClose() {
        nextRecvSeq = rxConnCloseSeq + 1;
        sendAck();
    }

    /**
     * Initiate the connection close protocol.
     */
    public void closeConn() throws IOException {
        boolean sendClosePkt = true;
        synchronized (recvQLock) {
            if (connCloseReceived) {
                flushAndClose();
                sendClosePkt = false;
            }
        }

        synchronized (sendQLock) {
            rxShutdown();

            if (sendClosePkt) {
                // Send the CONN_CLOSE_PACKET to the other end.
                ExtHttpTunnelPacket p = new ExtHttpTunnelPacket();
                p.setPacketType(CONN_CLOSE_PACKET);
                p.setPacketBody(null);
                p.setConnId(connId);
                p.setWinsize(0);
                p.setChecksum(0);
                sendData(p);
            }

            long waitStart = System.currentTimeMillis();

            // Block until everything is acknowledged.
            // One exception - The last message in the sendQ is always
            // CONN_CLOSE_PACKET. We don't need to wait for it to be
            // acknowledged...
            while (sendQ != null && sendQ.size() > 1) {
                try {
                    // Wait for window update
                    sendQLock.wait(CLOSE_WAIT_TIMEOUT);
                }
                catch (InterruptedException e) {}

                if (sendQ != null)
                    sendQLock.notifyAll(); // This notification was not for me.

                if ((System.currentTimeMillis() - waitStart) >
                    CLOSE_WAIT_TIMEOUT) {
                    break; // Can't wait forever...
                }
            }
        }
    }

    public int getConnId() {
        return connId;
    }

    public int getPullPeriod() {
        return pullPeriod;
    }

    public void setPullPeriod(int pullPeriod) throws IOException {
        if (this.pullPeriod == pullPeriod)
            return;

        this.pullPeriod = pullPeriod;

        ExtHttpTunnelPacket p = new ExtHttpTunnelPacket();
        p.setPacketType(CONN_OPTION_PACKET);
        p.setConnId(connId);
        p.setWinsize(0);
        p.setChecksum(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            dos.writeInt(CONOPT_PULL_PERIOD);
            dos.writeInt(pullPeriod);
            dos.flush();
            bos.flush();
        }
        catch (Exception e) {}

        byte[] buf = bos.toByteArray();
        p.setPacketBody(buf);

        sendData(p);
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout)
        throws IOException {
        if (this.connectionTimeout == connectionTimeout)
            return;

        this.connectionTimeout = connectionTimeout;

        ExtHttpTunnelPacket p = new ExtHttpTunnelPacket();
        p.setPacketType(CONN_OPTION_PACKET);
        p.setConnId(connId);
        p.setWinsize(0);
        p.setChecksum(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            dos.writeInt(CONOPT_CONNECTION_TIMEOUT);
            dos.writeInt(connectionTimeout);
            dos.flush();
            bos.flush();
        }
        catch (Exception e) {}

        byte[] buf = bos.toByteArray();
        p.setPacketBody(buf);

        sendData(p);
    }

    /**
     * Handles connection parameter negotiations.
     */
    public void handleConnOption(HttpTunnelPacket p) {
        byte[] buf = p.getPacketBody();
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bis);
        
        try {
            int optname = dis.readInt();
            switch (optname) {
            case CONOPT_PULL_PERIOD:
                pullPeriod = dis.readInt();
                break;
            case CONOPT_CONNECTION_TIMEOUT:
                connectionTimeout = dis.readInt();
                break;
            }
        }
        catch (Exception e) {}
        receiveData(p, false); // Treat it like a normal data packet.
    }

    /**
     * Handles connection close request sent by remote end.
     */
    public void handleClose(HttpTunnelPacket p) {
        txShutdown();
        receiveData(p, false); // Treat it like a normal data packet.
    }

    /**
     * Handle connection abort notification.
     */
    public void handleAbort(HttpTunnelPacket p) {
        synchronized (recvQLock) {
            if (connAbortReceived)
                return;
            connAbortReceived = true;

            // 'Translate' the abort notification into a CONN_CLOSE_PACKET
            // Some people may think this is a hack. I personally have
            // no comments on that issue.
            p.setPacketType(CONN_CLOSE_PACKET);
            p.setSequence(nextRecvSeq);
            handleClose(p);

            // Don't expect any more packets...
            wire.shutdown(connId);
        }
    }

    /**
     * Shutdown the packet receiver.
     */
    private void rxShutdown() {
        synchronized (recvQLock) {
            recvQ = null;
            recvQLock.notifyAll();
        }
    }

    /**
     * Shutdown the packet transmitter.
     */
    private void txShutdown() {
        synchronized (sendQLock) {
            stopRetransmitTimers();
            sendQ = null;
            sendQLock.notifyAll();
        }
    }

    public Vector getStats() {
        if (sendQ == null && recvQ == null)
            return null;

        Vector s = new Vector();

        s.addElement("connId = " + connId);
        s.addElement("RX.nextRecvSeq = " + nextRecvSeq);
        s.addElement("RX.rxWindowMax = " + rxWindowMax);
        s.addElement("RX.rxConnCloseSeq = " + rxConnCloseSeq);
        s.addElement("sendWindowUpdate = " + sendWindowUpdate);
        s.addElement("connCloseReceived = " + connCloseReceived);
        s.addElement("connAbortReceived = " + connAbortReceived);

        s.addElement("TX.lastAckSeq = " + lastAckSeq);
        s.addElement("TX.nextSendSeq = " + nextSendSeq);
        s.addElement("TX.txWindowMax = " + txWindowMax);
        s.addElement("TX.dupAckCount = " + dupAckCount);
        s.addElement("TX.txConnCloseSeq = " + txConnCloseSeq);
        s.addElement("txDataDisabled = " + txDataDisabled);
        s.addElement("connCloseSent = " + connCloseSent);

        s.addElement("RTO = " + RTO);
        s.addElement("measuredRTO = " + measuredRTO);
        s.addElement("TX.nRetransmit = " + nRetransmit);
        s.addElement("TX.nFastRetransmit = " + nFastRetransmit);

        return s;
    }

    public Hashtable getDebugState() {
        Hashtable ht = wire.getDebugState();

        ht.put("connId", String.valueOf(connId));
        ht.put("RX.nextRecvSeq", String.valueOf(nextRecvSeq));
        ht.put("RX.rxWindowMax", String.valueOf(rxWindowMax));
        ht.put("RX.rxConnCloseSeq", String.valueOf(rxConnCloseSeq));
        ht.put("sendWindowUpdate", String.valueOf(sendWindowUpdate));
        ht.put("connCloseReceived", String.valueOf(connCloseReceived));
        ht.put("connAbortReceived", String.valueOf(connAbortReceived));

        ht.put("TX.lastAckSeq", String.valueOf(lastAckSeq));
        ht.put("TX.nextSendSeq", String.valueOf(nextSendSeq));
        ht.put("TX.txWindowMax", String.valueOf(txWindowMax));
        ht.put("TX.dupAckCount", String.valueOf(dupAckCount));
        ht.put("TX.txConnCloseSeq", String.valueOf(txConnCloseSeq));
        ht.put("txDataDisabled", String.valueOf(txDataDisabled));
        ht.put("connCloseSent", String.valueOf(connCloseSent));

        ht.put("RTO", String.valueOf(RTO));
        ht.put("measuredRTO", String.valueOf(measuredRTO));
        ht.put("TX.nRetransmit", String.valueOf(nRetransmit));
        ht.put("TX.nFastRetransmit", String.valueOf(nFastRetransmit));

        return ht;
    }
}

class HttpTunnelTimerTask extends TimerTask {
    HttpTunnelConnection conn = null;
    int seq = 0;

    public HttpTunnelTimerTask(HttpTunnelConnection conn, int seq) {
        this.conn = conn;
        this.seq = seq;
    }

    public void run() {
        conn.retransmitPacket(seq, true);
    }
}

class ExtHttpTunnelPacket extends HttpTunnelPacket {
    private long txTime = 0;
    private int retransmitCount = 0;
    private boolean dirtyFlag = false;

    public void setTxTime(long txTime) {
        this.txTime = txTime;
    }

    public long getTxTime() {
        return txTime;
    }

    public void setRetransmitCount(int retransmitCount) {
        this.retransmitCount = retransmitCount;
    }

    public int getRetransmitCount() {
        return retransmitCount;
    }

    public void setDirtyFlag(boolean dirtyFlag) {
        this.dirtyFlag = dirtyFlag;
    }

    public boolean getDirtyFlag() {
        return dirtyFlag;
    }
}

class DebugStats extends Thread {
    HttpTunnelConnection conn;

    public DebugStats(HttpTunnelConnection conn) {
        this.conn = conn;
        setDaemon(true);
        start();
    }

    public void run() {
        while (true) {
            Vector s = conn.getStats();
            System.out.println("-----------------------------------------");

            if (s == null) {
                System.out.println("CONNECTION CLOSED : " + conn.getConnId());
            } else {
                for (int i = 0; i < s.size(); i++) {
                    System.out.println((String) s.elementAt(i));
                }
            }
            System.out.println("-----------------------------------------");

            if (s == null)
                break;

            try {
                Thread.sleep(10000);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

/*
 * EOF
 */
