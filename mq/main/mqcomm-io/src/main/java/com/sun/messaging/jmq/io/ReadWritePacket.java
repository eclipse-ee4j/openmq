/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.io;

import java.util.Hashtable;
import java.io.*;

/**
 * This class is an ecapsulation of a JMQ packet.
 */
public class ReadWritePacket extends ReadOnlyPacket {

    /**
     * Read packet from an InputStream. This method reads one packet from the InputStream and sets the state of this object
     * to reflect the packet read.
     *
     * @param is the InputStream to read the packet from
     */
    @Override
    public synchronized void readPacket(InputStream is) throws IOException, EOFException {

        // Read packet into internal buffers
        super.readPacket(is);
    }

    /**
     * Write the packet to the specified OutputStream
     */
    @Override
    public synchronized void writePacket(OutputStream os) throws IOException {
        super.writePacket(os);
    }

    /**
     * Update the timestamp on the packet. If you do this you should call generateTimestamp(false) before writing the
     * packet, otherwise the timestamp will be overwritten when writePacket() is called.
     */
    @Override
    public synchronized void updateTimestamp() {
        super.updateTimestamp();
    }

    /**
     * Update the sequence number on the packet. If you do this you should call generateSequenceNumber(false) before writing
     * the packet, otherwise the sequence number will be overwritten when writePacket() is called.
     */
    @Override
    public synchronized void updateSequenceNumber() {
        super.updateSequenceNumber();
    }

    /**
     * Set the packet type.
     *
     * @param pType The type of packet
     */
    @Override
    public synchronized void setPacketType(int pType) {
        super.setPacketType(pType);
    }

    @Override
    public synchronized void setTimestamp(long t) {
        super.setTimestamp(t);
    }

    @Override
    public synchronized void setExpiration(long e) {
        super.setExpiration(e);
    }

    @Override
    public synchronized void setPort(int p) {
        super.setPort(p);
    }

    @Override
    public synchronized void setIP(byte[] ip) {
        super.setIP(ip);
    }

    @Override
    public synchronized void setIP(byte[] ip, byte[] mac) {
        super.setIP(ip, mac);
    }

    @Override
    public synchronized void setSequence(int n) {
        super.setSequence(n);
    }

    // Version should be VERSION1, VERSION2 or VERSION3. Default is VERSION3
    @Override
    public synchronized void setVersion(int n) {
        super.setVersion(n);
    }

    @Override
    public synchronized void setTransactionID(long n) {
        super.setTransactionID(n);
    }

    @Override
    public synchronized void setEncryption(int e) {
        super.setEncryption(e);
    }

    @Override
    public synchronized void setPriority(int p) {
        super.setPriority(p);
    }

    @Override
    public synchronized void setFlag(int flag, boolean on) {
        super.setFlag(flag, on);
    }

    @Override
    public synchronized void setProducerID(long l) {
        super.setProducerID(l);
    }

    @Override
    public synchronized void setDestination(String d) {
        super.setDestination(d);
    }

    @Override
    public synchronized void setDestinationClass(String d) {
        super.setDestinationClass(d);
    }

    @Override
    public synchronized void setMessageID(String id) {
        super.setMessageID(id);
    }

    @Override
    public synchronized void setCorrelationID(String id) {
        super.setCorrelationID(id);
    }

    @Override
    public synchronized void setReplyTo(String r) {
        super.setReplyTo(r);
    }

    @Override
    public synchronized void setReplyToClass(String r) {
        super.setReplyToClass(r);
    }

    @Override
    public synchronized void setMessageType(String t) {
        super.setMessageType(t);
    }

    /**
     * Set the message properties. WARNING! The Hashtable is NOT copied.
     *
     */
    @Override
    public synchronized void setProperties(Hashtable props) {
        super.setProperties(props);
    }

    /**
     * Set the message body. WARNING! The byte array is NOT copied.
     *
     * @param body The message body.
     */
    @Override
    public synchronized void setMessageBody(byte[] body) {
        super.setMessageBody(body);
    }

    /**
     * Set the message body. Specify offset and length of where to take data from buffer. WARNING! The byte array is NOT
     * copied.
     *
     * @param body The message body.
     */
    @Override
    public synchronized void setMessageBody(byte[] body, int off, int len) {
        super.setMessageBody(body, off, len);
    }

    /**
     * Get the length of the message body
     *
     * @return Legnth of the message body in bytes
     */
    public synchronized int getMessageBodyLength() {
        return getMessageBodySize();
    }

    @Override
    public synchronized int getMessageBodySize() {
        return super.getMessageBodySize();
    }

    /**
     * Get the offset into the message body buffer where the message body data starts
     *
     * @return Byte offset into buffer returned by getMessageBody where message body data starts.
     *
     */
    public synchronized int getMessageBodyOffset() {
        return 0;
    }

    /**
     * Return the message body. WARNING! This returns a reference to the message body, not a copy. Also, if the body was set
     * using setMessageBody(buf, off, len) then you will get back the buffer that was passed to setMessageBody(). Therefore
     * you may need to use getMessageBodyOffset() and getMessageBodyLength() to determine the true location of the message
     * body in the buffer.
     *
     * @return A byte array containing the message body. null if no message body.
     */
    public synchronized byte[] getMessageBody() {
        return super.getMessageBodyByteArray();
    }

    /**
     * Make a deep copy of this packet
     */
    @Override
    public Object clone() {
        try {
            ReadWritePacket rp = new ReadWritePacket();
            rp.fill(this, true);
            return rp;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Reset state of packet to initial values
     */
    @Override
    public synchronized void reset() {
        super.reset();
    }
}
