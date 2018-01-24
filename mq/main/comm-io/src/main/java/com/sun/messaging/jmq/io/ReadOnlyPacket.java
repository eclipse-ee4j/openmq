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
 * @(#)ReadOnlyPacket.java	1.42 07/17/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.Hashtable;
import java.io.*;

import com.sun.messaging.jmq.util.net.IPAddress;

/**
 * This class is a mostly immutable encapsulation of a JMQ packet.
 * It is only "mostly" immutable because it can be modified by calling
 * the readPacket() method which modifies the state of the object to reflect
 * the packet just read.
 *
 * WARNING! This class emphasizes performance over safety. In particular
 * the readPacket() method is NOT synchronized. You must only call
 * readPacket() in a single threaded environment (or do the synchronization
 * yourself). Once the object is initialized via a readPacket() you can safely
 * use the accessors and the writePacket() methods in a multi-threaded
 * envornment.
 */

 /*
  * 10/27/08 - Changed ReadOnlyPacket to use Packet. We can make this change now
  * because the client has updated to a current enough JDK to use nio.
  *
  * The names (ReadOnlyPacket, ReadWritePacket) are maintained to prevent 
  * code modification on the client.
  */
public class ReadOnlyPacket extends Packet implements Cloneable {

    public ReadOnlyPacket() {
	super();
    }

    /**
     * this method MUST be called before a packet is created
     */
    public static void setDefaultVersion(short version) {
        defaultVersion = version;
    }

    /**
     * Read packet from an InputStream. This method reads one packet
     * from the InputStream and sets the state of this object to
     * reflect the packet read. This method is not synchronized
     * and should only be called by non-concurrent code.
     *
     * If we read a packet with a bad magic number (ie it looks like
     * bogus data), we give up and throw a StreamCorruptedException. 
     *
     * If we read a packet that does not match our packet version
     * we attempt to swallow the packet and then throw an
     * IllegalArgumentException.
     *
     * If this method throws an OutOfMemoryError, the caller can try
     * to free memory and call retryReadPacket().
     *
     * @param is        the InputStream to read the packet from
     */
    public void readPacket(InputStream is)
	throws IOException, EOFException, StreamCorruptedException, IllegalArgumentException {
         super.readPacket(is);
	return;
    }

    public boolean getFlag(int flag) {
        return super.getFlag(flag);
    }

    public void setIndempotent(boolean set)
    {
    }

    /**
     * Write the packet to an OutputStream
     *
     * @param os                The OutputStream to write the packet to
     *
     */
    public void writePacket(OutputStream os)
	throws IOException {
        super.writePacket(os);
    }

    /**
     * Check if this packet matches the specified system message id.
     *
     * @return    "true" if the packet matches the specified id, else "false"
     */
    public boolean isEqual(SysMessageID id) {
            return sysMessageID.equals(id);
    }

    /*
     * Accessors for packet fields
     */

    @Override
    public int getVersion() {
	return super.getVersion();
    }

    public int getMagic() {
	return super.getMagic();
    }

    public int getPacketType() {
	return super.getPacketType();
    }

    public int getPacketSize() {
	return super.getPacketSize();
    }

    public long getTimestamp() {
	return super.getTimestamp();
    }

    public long getExpiration() {
	return super.getExpiration();
    }

    public int getPort() {
	return super.getPort();
    }

    public String getIPString() {
	return super.getIPString();
    }

    public byte[] getIP() {
	return super.getIP();
    }

    public int getSequence() {
	return super.getSequence();
    }

    public int getPropertyOffset() {
	return super.getPropertyOffset();
    }

    public int getPropertySize() {
        return super.getPropertySize();
    }

    public int getEncryption() {
	return super.getEncryption();
    }

    public int getPriority() {
	return super.getPriority();
    }

    public long getTransactionID() {
	return super.getTransactionID();
    }

    public long getProducerID() {
	return super.getProducerID();
    }

    public long getConsumerID() {
	return super.getConsumerID();
    }

    // backwards compatibility
    public int getInterestID() {
	return (int)super.getConsumerID();
    }

    public boolean getPersistent() {
	return super.getPersistent();
    }

    public boolean getRedelivered() {
	return super.getRedelivered();
    }

    public boolean getIsQueue() {
	return super.getIsQueue();
    }

    public boolean getSelectorsProcessed() {
	return super.getSelectorsProcessed();
    }

    public boolean getSendAcknowledge() {
	return super.getSendAcknowledge();
    }

    public boolean getIsLast() {
	return super.getIsLast();
    }

    public boolean getFlowPaused() {
	return super.getFlowPaused();
    }

    public boolean getIsTransacted() {
	return super.getIsTransacted();
    }

    public boolean getConsumerFlow() {
	return super.getConsumerFlow();
    }

    public boolean getIndempotent() {
	return super.getIndempotent();
    }

    public String getDestination() {
	return super.getDestination();
    }

    public String getDestinationClass() {
	return super.getDestinationClass();
    }

    /**
     * Get the MessageID for the packet. If the client has set a MessageID
     * then that is what is returned. Otherwise the system message ID is 
     * returned (see getSysMessageID())
     *
     * @return     The packet's MessageID
     */
    public String getMessageID() {
        return super.getMessageID();
    }

    public String getCorrelationID() {
	return super.getCorrelationID();
    }

    public String getReplyTo() {
	return super.getReplyTo();
    }

    public String getReplyToClass() {
	return super.getReplyToClass();
    }

    public String getMessageType() {
	return super.getMessageType();
    }

    /**
     * Get the system message ID. Note that this is not the JMS MessageID
     * set by the client. Rather this is a system-wide unique message ID
     * generated from the timestamp, sequence number, port number and
     * IP address of the packet.
     * <P>
     * WARNING! This returns a references to the Packet's SysMessageID
     * not a copy.
     * 
     * @return     The packet's system MessageID
     *
     */
    public SysMessageID getSysMessageID() {

	return super.getSysMessageID();
    }

    /**
     * Return the size of the message body in bytes
     *
     * @return    Size of message body in bytes
     */
    public int getMessageBodySize() {
	return super.getMessageBodySize();
    }


    /**
     * Return an InputStream that contains the contents of the
     * message body.
     *
     * @return    An InputStream from which the message body can
     *            be read from. Or null if no message body.
     */
    public InputStream getMessageBodyStream() {
        return super.getMessageBodyStream();
    }


    /**
     * Return the property hashtable for this packet.
     *
     * WARNING! This method emphasizes performance over safety. The
     * HashTable object returned is a reference to the HashTable object
     * in the object -- it is NOT a copy. Modifying the contents of
     * the HashTable will have non-deterministic results so don't do it!
     */
    public Hashtable getProperties()
	throws IOException, ClassNotFoundException {
        return super.getProperties();
    }

    public Object cloneShallow() {
        try {
            ReadOnlyPacket rp = new ReadOnlyPacket();
            rp.fill(this);
            return rp;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Make a deep copy of this packet. This will be slow.
     */
    public Object clone() {
        try {
            ReadOnlyPacket rp = new ReadOnlyPacket();
            rp.fill(this, true);
            return rp;
        } catch (IOException ex) {
            return null;
        }
    }


    /**
     * Return a unique string that identifies the packet
     */
    public String toString() {
	return super.toString();
    }

    /**
     * Return a string containing the contents of the packet in a human
     * readable form.
     */
    public String toVerboseString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        this.dump(new PrintStream(bos));

        return bos.toString();
    }

    /**
     * Dump the contents of the packet in human readable form to
     * the specified OutputStream.
     *
     * @param    os    OutputStream to write packet contents to
     */
    public void dump(PrintStream os) {
        super.dump(os);
    }
}
