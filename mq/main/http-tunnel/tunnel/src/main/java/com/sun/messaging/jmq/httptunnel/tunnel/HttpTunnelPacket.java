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
 * @(#)HttpTunnelPacket.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;


/**
 * This class encapsulates the HTTP tunnel packet.
 */
public class HttpTunnelPacket implements HttpTunnelDefaults {
    protected static final short VERSION = 100;
    protected static final int HEADER_SIZE = 24;
    protected short version = VERSION;
    protected short packetType = 0;
    protected int packetSize = 0;
    protected int connId = 0;
    protected int sequence = 0;
    protected short winsize = 0;
    protected short reserved = 0;
    protected int checksum = 0;
    protected byte[] headerBuffer = new byte[HEADER_SIZE];
    protected byte[] packetBuffer = null;
    protected boolean dirty = false;

    private void parseHeader(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);

        version = dis.readShort();

        if (version > VERSION) {
            throw new IllegalStateException("Bad response format. " +
                "Check the tunnel servlet URL.");
        }

        packetType = dis.readShort();
        packetSize = dis.readInt();
        connId = dis.readInt();
        sequence = dis.readInt();
        winsize = dis.readShort();
        reserved = dis.readShort();
        checksum = dis.readInt();
    }

    private void updateBuffers() throws IOException {
        if (!dirty) {
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        DataOutputStream dos = new DataOutputStream(bos);

        dos.writeShort(version);
        dos.writeShort(packetType);
        dos.writeInt(packetSize);
        dos.writeInt(connId);
        dos.writeInt(sequence);
        dos.writeShort(winsize);
        dos.writeShort(reserved);
        dos.writeInt(checksum);

        dos.flush();
        bos.flush();

        headerBuffer = bos.toByteArray();

        dirty = false;
    }

    /**
     * Read a packet from the given InputStream.
     */
    public void readPacket(InputStream is) throws IOException, EOFException {
        DataInputStream dis = new DataInputStream(is);
        dis.readFully(headerBuffer);

        parseHeader(new ByteArrayInputStream(headerBuffer));

        packetBuffer = new byte[packetSize - HEADER_SIZE];
        dis.readFully(packetBuffer);
    }

    /**
     * Write a packet to the given OutputStream.
     */
    public void writePacket(OutputStream os) throws IOException {
        updateBuffers();

        os.write(headerBuffer, 0, HEADER_SIZE);

        if (packetBuffer != null) {
            os.write(packetBuffer, 0, packetSize - HEADER_SIZE);
        }

        os.flush();
    }

    public int getPacketType() {
        return packetType;
    }

    public byte[] getPacketBody() {
        return packetBuffer;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getPacketDataSize() {
        return packetSize - HEADER_SIZE;
    }

    public int getConnId() {
        return connId;
    }

    public int getSequence() {
        return sequence;
    }

    public int getWinsize() {
        return winsize;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setPacketType(int packetType) {
        this.packetType = (short) packetType;
        dirty = true;
    }

    public void setPacketBody(byte[] data) {
        packetBuffer = data;
        packetSize = HEADER_SIZE;

        if (packetBuffer != null) {
            packetSize += packetBuffer.length;
        }

        dirty = true;
    }

    public void setConnId(int connId) {
        this.connId = connId;
        dirty = true;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
        dirty = true;
    }

    public void setWinsize(int winsize) {
        this.winsize = (short) winsize;
        dirty = true;
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
        dirty = true;
    }

    public String toString() {
        String ret = " HttpTunnelPacket [ Version = " + version + "," +
            " packetType = " + getPacketTypeStr() + "," + " packetSize = " +
            packetSize + "," + " connId = " + connId + "," + " sequence = " +
            sequence + "," + " winsize = " + winsize + "," + " reserved = " +
            reserved + "," + " checksum = " + checksum + "]";

        if (packetBuffer != null) {
            ret = ret + " [ DATA = \"" + new String(packetBuffer) + "\"]";
        }

        return ret;
    }

    private String getPacketTypeStr() {
        switch (packetType) {
        case CONN_ABORT_PACKET:
            return "CONN_ABORT_PACKET";

        case CONN_CLOSE_PACKET:
            return "CONN_CLOSE_PACKET";

        case CONN_INIT_ACK:
            return "CONN_INIT_ACK";

        case CONN_INIT_PACKET:
            return "CONN_INIT_PACKET";

        case CONN_OPTION_PACKET:
            return "CONN_OPTION_PACKET";

        case CONN_REJECTED:
            return "CONN_REJECTED";

        case CONN_SHUTDOWN:
            return "CONN_SHUTDOWN";

        case DATA_PACKET:
            return "DATA_PACKET";

        case ACK:
            return "ACK";

        case LINK_INIT_PACKET:
            return "LINK_INIT_PACKET";

        case LISTEN_STATE_PACKET:
            return "LISTEN_STATE_PACKET";

        default:
            return String.valueOf(packetType);
        }
    }
}

/*
 * EOF
 */
