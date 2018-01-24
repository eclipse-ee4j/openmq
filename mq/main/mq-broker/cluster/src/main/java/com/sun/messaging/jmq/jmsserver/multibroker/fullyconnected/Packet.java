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
 * @(#)Packet.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 * This class encapsulates the packet format for standard
 * fully connected broker topology.
 */
class Packet {
    private static final short VERSION = 100;
    private static final int HEADER_SIZE = 16;
    private static final int MAX_PACKET_SIZE = 65536;

    /* packetType */
    public static final int UNICAST = 1;
    public static final int BROADCAST = 2;
    public static final int BROKER_INFO = 3;
    public static final int LINK_INIT = 4;
    public static final int STOP_FLOW = 5;
    public static final int RESUME_FLOW = 6;
    public static final int PING = 7;
    public static final int BROKER_INFO_REPLY = 9;

    /* bitFlags */
    public static final int USE_FLOW_CONTROL = 0x0001;

    private short version = VERSION;
    private short packetType = 0;
    private int packetSize = 0;
    private int destId = 0;
    private int bitFlags = 0;

    private byte[] packetBuffer = null;

    public void readPacket(InputStream is)
        throws IOException, EOFException {

        DataInputStream dis = new DataInputStream(is);

        version = dis.readShort();
        packetType = dis.readShort();
        packetSize = dis.readInt();
        destId = dis.readInt();
        bitFlags = dis.readInt();

        if (packetSize < HEADER_SIZE || packetSize > MAX_PACKET_SIZE) {
            String emsg = Globals.getBrokerResources().getKString(
                BrokerResources.W_CLUSTER_INVALID_PACKET_SIZE_READ,
                String.valueOf(packetSize))+"["+is+"]";
            Globals.getLogger().log(Logger.WARNING,  emsg);
            throw new IOException(emsg);
        }
        try {
            packetBuffer = new byte[packetSize - HEADER_SIZE];
        }
        catch (OutOfMemoryError oom) {
            dis.skip(packetSize - HEADER_SIZE);
            throw oom;
        }
        dis.readFully(packetBuffer);
    }

    public void writePacket(OutputStream os)
        throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeShort(version);
        dos.writeShort(packetType);
        dos.writeInt(packetSize);
        dos.writeInt(destId);
        dos.writeInt(bitFlags);
        dos.flush();
        bos.flush();

        byte[] headerBuffer = bos.toByteArray();

        os.write(headerBuffer, 0, HEADER_SIZE);
        if (packetBuffer != null)
            os.write(packetBuffer, 0, packetSize - HEADER_SIZE);
        os.flush();
    }

    public int getPacketType() {
        return packetType;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getDestId() {
        return destId;
    }

    public byte[] getPacketBody() {
        return packetBuffer;
    }

    public boolean getFlag(int flag) {
        return ((bitFlags & flag) == flag);
    }

    public void setPacketType(int packetType) {
        this.packetType = (short) packetType;
    }

    public void setDestId(int destId) {
        this.destId = destId;
    }

    public void setPacketBody(byte[] data) {
        packetBuffer = data;
        packetSize = HEADER_SIZE;
        if (packetBuffer != null)
            packetSize += packetBuffer.length;
    }

    public void setFlag(int flag, boolean on) {
        if (on)
            bitFlags = bitFlags | flag;
        else
            bitFlags = bitFlags & ~flag;
    }

    public String toString() {
        return "PacketType = " + packetType +
            ", DestId = " + destId + ", DATA :\n" +
            hexdump(packetBuffer, 128);
    }

    public static String hexdump(byte[] buffer, int maxlen) {
        if (buffer == null)
            return "";

        int addr = 0;
        int buflen = buffer.length;
        if (buflen > maxlen)
            buflen = maxlen;

        StringBuffer ret = new StringBuffer(buflen);

        while (buflen > 0) {
            int count = buflen < 16 ? buflen : 16;
            ret.append("\n" + i2hex(addr, 6, "0"));

            StringBuffer tmp = new StringBuffer();

            int i;
            for (i = 0; i < count; i++) {
                int b = (int) buffer[addr + i];

                if (i == 8)
                    ret.append("-");
                else
                    ret.append(" ");
                ret.append(i2hex(b, 2, "0"));
                if (b >= 32 && b < 128)
                    tmp.append(((char) b));
                else
                    tmp.append(".");
            }
            for (; i < 16; i++)
                ret.append("   ");

            ret.append("   " + tmp);

            addr += count;
            buflen -= count;
        }
        return ret.append("\n").toString();
    }

    public static String i2hex(int i, int len, String filler) {
        String str = Integer.toHexString(i);
        if (str.length() == len)
            return str;
        if (str.length() > len)
            return str.substring(str.length() - len);
        while (str.length() < len)
            str = filler + str;
        return str;
    }

    public static String getPacketTypeString(int type) { 
        switch(type) {
            case BROKER_INFO: return "BROKER_INFO";
            case LINK_INIT:   return "LINK_INIT";
            case STOP_FLOW:   return "STOP_FLOW";
            case RESUME_FLOW: return "RESUME_FLOW";
            case PING:        return "PING";
            case BROKER_INFO_REPLY: return "BROKER_INFO_REPLY";
            default: return "UNKNOWN";
        }
    }

}

/*
 * EOF
 */
