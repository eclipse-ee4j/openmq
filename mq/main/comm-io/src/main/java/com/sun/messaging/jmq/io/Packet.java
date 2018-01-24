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
 * @(#)Packet.java	1.57 11/26/07
 */ 

package com.sun.messaging.jmq.io;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.util.JMQXid;

/**
 *
 * Encapsulation of an iMQ packet. A packet consists of three parts:
 *
 *  1) A fixed sized header (size HEADER_SIZE bytes)
 *  2) A variable sized header (for holding strings, and optional packet fields)
 *  3) A serialized java.util.Hashtable (for holding message properties)
 *  4) the packet body (a bag of bytes)
 *
 * This class directly manages #1, uses PacketVariableHeader to manage #2,
 * and uses PacketPayload to manage #3 and #4.
 *
 * This class also employs the use of a buffer pool to manager reuse of
 * direct ByteBuffers. Direct ByteBuffers are an order of magnitude more
 * expensive to allocate than normal ByteBuffers, so keeping a pool of them
 * around is a big win (which is not typically the case for memory
 * allocated off of the Java heap).
 *
 */
public class Packet implements JMSPacket {

    private static boolean DEBUG = false;

    protected boolean destroyed = false;

    // Packet magic number. NEVER change this
    public static final int   MAGIC = 469754818;
    // Packet version numbers
    public static final short VERSION1 = 103;
    public static final short VERSION2 = 200;
    public static final short VERSION3 = 301;
    public static final short CURRENT_VERSION = VERSION3;

    static short defaultVersion = CURRENT_VERSION;

    // Number of bytes in fixed header portion of packet
    public static final int  HEADER_SIZE = 72;

    public static final int  DEFAULT_POOL_SIZE = 1024 * 1024;
    public static final int  DEFAULT_POOL_BLOCKSIZE = 128;

    // Global sequence number (optionally) tagged onto packets
    private static int sequenceNumber = 0;

    // A global pool of ByteBuffers. This is used to reuse direct
    // ByteBuffers which are expensive to allocate and deallocate
    // Note: the pool is only used if the packet is using direct ByteBuffers.
    protected static final ByteBufferPool bbPool;

    // Maximum size a packet can be, and the lower and upper bounds
    // this value can be. We put a relatively high lower bound
    // so that control packets are not impacted. 
    private static long     SIZE_LOWER_BOUND = 512  * 1024;
    protected static final long     SIZE_UPPER_BOUND = Integer.MAX_VALUE;
    private static long maxPacketSize = SIZE_UPPER_BOUND;

    // true to automatically generate timestamps
    protected   boolean genTimestamp      = true;

    // true to automatically generate sequence numbers
    protected   boolean genSequenceNumber = true;

    // Buffer to hold fixed portion of header
    protected ByteBuffer fixedBuf = null;

    // Dirty flag. This is true when the fixedBuf is out-of-date
    // with respect to the state of this object.
    protected boolean bufferDirty = false;

    // True to use direct ByteBuffers, else false.
    protected boolean useDirect = false;

    // Buffers used for reading packet off of wire. We keep these around just
    // in case we want to reuse them for subsequent reads if the packet
    // is reused
    protected ByteBuffer varBuf = null;
    protected ByteBuffer propBuf = null;
    protected ByteBuffer bodyBuf = null;

    // Array used to hold buffers during reads and writes. 
    protected ByteBuffer[] writeBufs = new ByteBuffer[4];
    protected ByteBuffer[] readBufs  = new ByteBuffer[4];

    // XXX 1/24/2002 dipol: Needed to work around nio bug (imq 4627557)
    protected int[] readBufsLimits  = new int[4];
    protected int nBufs = 0;

    // A list of all buffers allocated by this packet. We track this
    // so we can easily return them to the pool when the packet is destroyed
    protected ArrayList allocatedBuffers = new ArrayList(8);

    // Values in fixed header
    protected short	version        = defaultVersion;
    protected int	magic          = MAGIC;
    protected short	packetType     = 0;
    protected int	packetSize     = 0;
    protected long	expiration     = 0;
    protected int	propertyOffset = 0;
    protected int	propertySize   = 0;
    protected byte	encryption     = 0;
    protected long	transactionID  = 0;
    protected byte	priority       = 5;
    protected int	bitFlags       = 0;
    protected long	consumerID     = 0;

    // Holds sequence, IPaddr, port and timestamp
    protected SysMessageID sysMessageID = new SysMessageID();

    // Classes that handle the variable portion of the packet and the payload
    protected PacketVariableHeader packetVariableHeader = null;
    protected PacketPayload packetPayload = null;

    // Used to track state of in-progress non-blocking I/O
    protected boolean readInProgress = false;
    protected boolean writeInProgress = false;
    protected boolean versionMismatch = false;
    protected int     headerBytesRead = 0;
    protected int     ropBytesRead = 0;
    protected int     bytesWritten = 0;

    static {
        // XXX - the buffer pool should be tunable through properties
        bbPool = new ByteBufferPool(DEFAULT_POOL_SIZE, true);
        bbPool.setBlockSize(HEADER_SIZE);
    }

    // Maximum size packet we should read. This let's the broker
    // protect against denial of service attacks if an overly
    // large packet is sent.

    /**
     * Set the max packet size that should be read. If a packet
     * is larger than this the packet read methods will skip the 
     * bytes and throw an exception.
     *
     * There is a lower bound on the smallest value you can set
     * the max packet size to. So the actual value set may be
     * different than the value passed. This method returns the
     * actual value set.
     */
    public synchronized static long setMaxPacketSize(long n) {
        if (n < SIZE_LOWER_BOUND) {
            maxPacketSize = SIZE_LOWER_BOUND;
	} else {
            maxPacketSize = n;
        }
        return maxPacketSize;
    }

    public synchronized static long getMaxPacketSize() {
        return maxPacketSize;
    }

    public static void setSizeLowerBound(long n) {
        SIZE_LOWER_BOUND = n;
    } 

    public static long getSizeLowerBound() {
        return SIZE_LOWER_BOUND;
    } 

    public synchronized static ByteBufferPool getBufferPool() {
        return bbPool;
    }

    /**
     * Constructs an empty packet that will use direct buffers
     */
    public Packet() {
        this(false);
    }

    /**
     * Construct a packet indicating whether or not to use direct
     * ByteBuffers. 
     *
     * If you plan on reading and writing packets using the methods that
     * take a channel as a parameter then you should specify useDirect=true.
     *
     * If you plan on reading and writing packets using the methods that
     * take an IO Stream as a parameter then you should specify 
     * useDirect = false.
     *
     * Default is to use direct buffers.
     *
     */
    public Packet(boolean useDirect) {
        this.useDirect = useDirect;
	this.reset();
    }

    /**
     * Fill this packet with the contents of sourcePacket. The fixed and
     * variable headers are copied. The payload (properties and body) are
     * shared. This method turns off timestamp and sequence number
     * generation since we assume you want the copied header contents to
     * remain unchanged.
     */
    public synchronized void fill(Packet sourcePacket) throws IOException {
        fill(sourcePacket, false);
    }
    public synchronized void fill(Packet sourcePacket, boolean deep) throws IOException {
	this.reset();

        synchronized (sourcePacket) {

        sourcePacket.updateBuffers();

        ByteBuffer buf = null;

        // Copy fixed header from source packet
        buf = sourcePacket.getHeaderBytes();
        buf.rewind();
        fixedBuf.rewind();
        fixedBuf.put(buf);
        buf.rewind();
        fixedBuf.rewind();

        // Copy variable header from source packet. Note this IS a copy.
        try {
            buf = sourcePacket.getPacketVariableHeader().getBytes();
        } catch (IOException e) {
            // Should never happen
            System.out.println("Could not get variable header" + e);
            return;
        }

        if (buf != null) {
            ByteBuffer newBuf = packetVariableHeader.getBytes();
            if (newBuf == null || newBuf.capacity() < buf.limit()) {
                // Need a new buffer
                newBuf = allocateBuffer(buf.limit());
            } else {
                // Can reuse existing buffer
                newBuf.limit(buf.limit());
                newBuf.rewind();
            }
            newBuf.put(buf);
            newBuf.rewind();
            buf.rewind();
            packetVariableHeader.setBytes(newBuf);
        }

        // Get payload from source packet. Note this is NOT a copy 
	PacketPayload sourcePayload = sourcePacket.getPacketPayload();
	if (sourcePayload != null) {
            // If not deep: set our payload buffers to be those of the source packet.
            // The packets will share payload content, but each must have
            // their own position, mark and limit.
            ByteBuffer b = sourcePayload.getPropertiesBytes(version);
            if (b != null) {
                if (deep) { // copy everything
                    ByteBuffer newb = allocateBuffer(b.limit());
                    newb.put(b);
                    newb.rewind();
                    packetPayload.setPropertiesBytes(
                        newb, version);

                } else { // shallow - use the same buffer
                    packetPayload.setPropertiesBytes(
                        (ByteBuffer)b.duplicate().rewind(), version);
                }
            }
            b = sourcePayload.getBodyBytes();
            if (b != null) {
                if (deep) { // copy everything
                    ByteBuffer newb = allocateBuffer(b.limit());
                    newb.put(b);
                    newb.rewind();
                    packetPayload.setBody(newb);
                } else {
                    packetPayload.setBody((ByteBuffer)b.duplicate().rewind());
                }
                b.rewind();
            }
	}

        } // end synchronized

        // Populate packet with the new info
        parseFixedBuffer(fixedBuf);
        // Forces transaction ID to be populated if VERSION2
        getTransactionID();

        // Since a filled packet clones the header of another packet we
        // turn off sequence and timestamp generation.
        generateSequenceNumber(false);
        generateTimestamp(false);

        return;
    }

    /**
     * Parse the fixed packet header into instance variables.
     */
    protected void parseFixedBuffer(ByteBuffer buf)
	throws StreamCorruptedException, IOException {

        buf.rewind();
	magic   = buf.getInt();

	if (magic != MAGIC) {
	    throw new StreamCorruptedException("Bad packet magic number: " +
			magic + ". Expecting: " + MAGIC);
	}
	   version = buf.getShort();
	packetType = buf.getShort();
	packetSize = buf.getInt();

        // Up to this point the packet header stays the same between 
        // versions. After this point it may be different depending
        // on the version. If the version is something we don't understand
        // then the following will generate garbage values, but we will
        // catch the version error later.


        // In VERSION1 the transactionID is a 32 bit value in the header.
        // In VERSION2 It became a 64 bit value in the variable portion
        // of the packet.
	if (version == VERSION1) {
	    transactionID  = buf.getInt();
        }

	expiration = buf.getLong();

	// Reads timestamp, source IP address, source port and sequence number
        // Hack to get a data input stream for SysMessageID.
        try (InputStream is = new JMQByteBufferInputStream(buf)) {
             try (DataInputStream dis = new DataInputStream(is)) { 
                  sysMessageID.readID(dis);
             }
        }

	propertyOffset = buf.getInt();
	propertySize   = buf.getInt();

	priority   = buf.get();
	encryption = buf.get();

	bitFlags   = (int)(buf.getShort());

        // In VERSION2 the consumerID is a long
        if (version == VERSION1) {
	    consumerID = buf.getInt();
            // There was a bug in the 2.0 packet code that left cruft in the
            // uppper 8 bits of the bitFlags. The upper 8 bits was never 
            // used in 2.0 so this problem was not noticed until later.
            // See bug 4948563 for more info.
            // Zero out the upper 8 bits to clear the garbage.
            bitFlags = bitFlags & 0x00FF;
	} else {
	    consumerID = buf.getLong();
        }

        buf.rewind();
	return;
    }

    public boolean getFlag(int flag) {
	return((bitFlags & flag) == flag);
    }

    public int getVersion() {
	return version;
    }

    public int getMagic() {
	return magic;
    }

    public int getPacketType() {
	return packetType;
    }
    
    /**
     * Return whether this is a reply packet
     * 
     * By convention, a reply packet has a packetType 
     * which is odd and is >=9
     * 
     * In addition, a AUTHENTICATE_REQUEST is considered a reply
     * despite having an odd packetType  
     * 
     * @return whether this is a reply packet
     */
    public boolean isReply(){
    	return (((packetType>=9) && (packetType % 2 != 0)) || 
                (packetType==PacketType.AUTHENTICATE_REQUEST));
    }

    public synchronized int getPacketSize() {
        // Make sure value is correct. If packet is not dirty the update is
        // a no-op
        try {
            updateBuffers();
        } catch (IOException e) {};
	return packetSize;
    }

    public synchronized long getTransactionID() {
        // In VERSION1 the transactionID was in the fixed header. It moved
        // to the variable header in VERSION2.
        if (version >= VERSION2) {
            transactionID =
                packetVariableHeader.getLongField(PacketString.TRANSACTIONID);
        }

        return transactionID;
    }

    public synchronized long getProducerID() {
        return packetVariableHeader.getLongField(PacketString.PRODUCERID);
    }

    public synchronized long getDeliveryTime() {
        return packetVariableHeader.getLongField(PacketString.DELIVERY_TIME);
    }

    public synchronized int getDeliveryCount() {
        return packetVariableHeader.getIntField(PacketString.DELIVERY_COUNT);
    }

    public synchronized long getTimestamp() {
	return sysMessageID.timestamp;
    }

    public synchronized long getExpiration() {
	return expiration;
    }

    public int getPort() {
	return sysMessageID.port;
    }

    public synchronized String getIPString() {
	return sysMessageID.toString();
    }

    public synchronized byte[] getIP() {
	return sysMessageID.getIPAddress();
    }

    public int getSequence() {
	return sysMessageID.sequence;
    }

    public synchronized int getPropertyOffset() {
        try {
            updateBuffers();
        } catch (IOException e) {};
	return propertyOffset;
    }

    public synchronized int getPropertySize() {
        try {
            updateBuffers();
        } catch (IOException e) {};
        return propertySize;
    }

    public int getEncryption() {
	return encryption;
    }

    public int getPriority() {
	return priority;
    }

    public synchronized long getConsumerID() {
	return consumerID;
    }

    public boolean getPersistent() {
	return getFlag(PacketFlag.P_FLAG);
    }

    public boolean getRedelivered() {
	return getFlag(PacketFlag.R_FLAG);
    }

    public boolean getIsQueue() {
	return getFlag(PacketFlag.Q_FLAG);
    }

    public boolean getSelectorsProcessed() {
	return getFlag(PacketFlag.S_FLAG);
    }

    public boolean getSendAcknowledge() {
	return getFlag(PacketFlag.A_FLAG);
    }

    public boolean getIsLast() {
	return getFlag(PacketFlag.L_FLAG);
    }

    public boolean getFlowPaused() {
	return getFlag(PacketFlag.F_FLAG);
    }

    public boolean getIsTransacted() {
	return getFlag(PacketFlag.T_FLAG);
    }

    public boolean getConsumerFlow() {
	return getFlag(PacketFlag.C_FLAG);
    }

    public boolean getIndempotent() {
	return getFlag(PacketFlag.I_FLAG);
    }

    public boolean getWildcard() {
	return getFlag(PacketFlag.W_FLAG);
    }

    /**
     * Get the MessageID for the packet. If the client has set a MessageID
     * then that is what is returned. Otherwise the system message ID is 
     * returned (see getSysMessageID())
     *
     * @return     The packet's MessageID
     */
    public synchronized String getMessageID() {
	String messageID =
            packetVariableHeader.getStringField(PacketString.MESSAGEID);

	if (messageID == null) {
	    return sysMessageID.toString();
        } else {
	    return messageID;
	}
    }

    public synchronized void setMessageID(String messageID) {
         // sets the MESSAGEID string
         packetVariableHeader.setStringField(PacketString.MESSAGEID, messageID);
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
    public synchronized SysMessageID getSysMessageID() {

	return sysMessageID;
    }


    public synchronized String getDestination() {
        return packetVariableHeader.getStringField(PacketString.DESTINATION);
    }

    public synchronized String getDestinationClass() {
        return packetVariableHeader.getStringField(PacketString.DESTINATION_CLASS);
    }

    public synchronized String getCorrelationID() {
        return packetVariableHeader.getStringField(PacketString.CORRELATIONID);
    }

    public synchronized String getReplyTo() {
        return packetVariableHeader.getStringField(PacketString.REPLYTO);
    }

    public synchronized String getReplyToClass() {
        return packetVariableHeader.getStringField(PacketString.REPLYTO_CLASS);
    }

    public synchronized String getMessageType() {
        return packetVariableHeader.getStringField(PacketString.TYPE);
    }

    /**
     * Return the property hashtable for this packet.
     *
     * WARNING! This method emphasizes performance over safety. The
     * HashTable object returned is a reference to the HashTable object
     * in the object -- it is NOT a copy. Modifying the contents of
     * the HashTable will have non-deterministic results so don't do it!
     */
    public synchronized Hashtable getProperties()
	throws IOException, ClassNotFoundException {

        return packetPayload.getProperties();
    }


    /**
     * Return the size of the message body in bytes
     *
     * @return    Size of message body in bytes
     */
    public synchronized int getMessageBodySize() {
	return packetPayload.getBodySize();
    }

    /**
     * Return the message body as a ByteBuffer.
     *
     */
    public synchronized ByteBuffer getMessageBodyByteBuffer() {
        return packetPayload.getBodyBytes();
    }

    /**
     * Return the message body as a byte array
     */
    public synchronized byte[] getMessageBodyByteArray() {
        ByteBuffer bb = packetPayload.getBodyBytes();
        if (bb != null && bb.hasArray()) {
            return bb.array();
        }
        return null;
    }

    /**
     * Clear the message body of this packate
     */
    public synchronized void clearMessageBody() {
        if (packetPayload != null) {
            packetPayload.setBody(null);
        }
    }

    /**
     * Return an InputStream that contains the contents of the
     * message body.
     *
     * @return    An InputStream from which the message body can
     *            be read from. Or null if no message body.
     */
    public synchronized InputStream getMessageBodyStream() {
        return packetPayload.getBodyStream();
    }


    /**
     * Reset packet to initial values. This does not free buffers just
     * in case we can reused them. 
     */
    public synchronized void reset() {
	version        = VERSION3;
	magic          = MAGIC;
        packetType     = 0;
        packetSize     = 0;
        expiration     = 0;
        propertyOffset = 0;
        propertySize   = 0;
        encryption     = 0;
        priority       = 5;
        bitFlags       = 0;
        consumerID     = 0;
        transactionID  = 0;

        readInProgress = false;
        headerBytesRead = 0;
        ropBytesRead = 0;

        bufferDirty = false;

	sysMessageID.clear();

        if (fixedBuf != null) {
            fixedBuf.clear();
        } else {
            fixedBuf = allocateBuffer(HEADER_SIZE);
        }

        if (varBuf != null) {
            varBuf.clear();
        }

        if (propBuf != null) {
            propBuf.clear();
        }

        if (bodyBuf != null) {
            bodyBuf.clear();
        }

        if (packetVariableHeader != null) {
            packetVariableHeader.reset();
        } else {
            packetVariableHeader = new PacketVariableHeader();
        }

        if (packetPayload != null) {
            packetPayload.reset();
        } else {
            packetPayload = new PacketPayload();
        }
        bigPacketEx = null;
    }

    /**
     * Update all buffers to reflect the current state of the object.
     */
    public void updateBuffers()
        throws IOException {

        if (!bufferDirty) {
            return;
        }

        // Forces variable header and payload to update their buffers.
        // Then we can compute sizes correctly
        ByteBuffer varBytes;
        if (version == VERSION1) {
            // The 2.0 packet code has a bug where it requires something
            // in the variable portion of the header, even if it is just
            // the Null list terminator. getBytes2() addresses this.
            varBytes = packetVariableHeader.getBytes2();
        } else {
            varBytes = packetVariableHeader.getBytes();
        }
        ByteBuffer propBytes = packetPayload.getPropertiesBytes(version);
        ByteBuffer bodyBytes = packetPayload.getBodyBytes();

        propertySize = ((propBytes == null) ? 0 : propBytes.limit());
        int varSize =  ((varBytes == null)  ? 0 : varBytes.limit());
        int bodySize = ((bodyBytes == null) ? 0 : bodyBytes.limit());

        propertyOffset = HEADER_SIZE + varSize;
        packetSize = propertyOffset + propertySize + bodySize;

        fixedBuf.rewind();

        // Write data into fixed header
	fixedBuf.putInt(MAGIC);

	fixedBuf.putShort(version);
	fixedBuf.putShort(packetType);
	fixedBuf.putInt(packetSize);

        // In VERSION1 transactionID was a 32bit value in the header
        if (version == VERSION1) {
	    fixedBuf.putInt((int)transactionID);
        }

	fixedBuf.putLong(expiration);

	// Writes timestamp, source IP addr, source port, and sequence number
        // Hack to get a data output stream for SysMessageID.
        try (OutputStream os = new JMQByteBufferOutputStream(fixedBuf)) {
            try (DataOutputStream dos = new DataOutputStream(os)) {
                 sysMessageID.writeID(dos);
            }
        }

	fixedBuf.putInt(propertyOffset);
	fixedBuf.putInt(propertySize);
	fixedBuf.put(priority);
	fixedBuf.put(encryption);

	fixedBuf.putShort((short)bitFlags);

        // In VERSION1 consumerID is 32bits. In VERSION2 it is 64bits
        if (version == VERSION1) {
	    fixedBuf.putInt((int)consumerID);
        } else {
	    fixedBuf.putLong(consumerID);
        }
        bufferDirty = false;

        fixedBuf.rewind();
    }

    /** 
     * Set the packet type.
     *
     * @param    pType    The type of packet
     */
    public synchronized void setPacketType(int pType) {
	packetType = (short)pType;
	bufferDirty = true;
    }

    public synchronized void setTimestamp(long t) {
	sysMessageID.setTimestamp(t);
	bufferDirty = true;
    }

    public synchronized void setExpiration(long e) {
	expiration = e;
	bufferDirty = true;
    }

    public synchronized void setPort(int p) {
	sysMessageID.port = p;
	bufferDirty = true;
    }

    public synchronized void setIP(byte[] ip) {
	sysMessageID.setIPAddress(ip);
	bufferDirty = true;
    }

    public synchronized void setIP(byte[] ip, byte[] mac) {
	sysMessageID.setIPAddress(ip, mac);
	bufferDirty = true;
    }

    public synchronized void setSequence(int n) {
	sysMessageID.setSequence(n);
	bufferDirty = true;
    }

    // Version should be VERSION1, VERSION2 or VERSION3. Default is VERSION3
    public synchronized void setVersion(int n) {
	if (version != (short)n) {
	    version = (short)n;

            // We are going from VERSION1 to VERSION2 packet. We explicitly
            // set the transactionID to make sure it gets set in the
            // variable portion of the packet.
            if (version >= VERSION2) {
                setTransactionID(transactionID);
            }

	    bufferDirty = true;
	}
    }

    public synchronized void setTransactionID(long n) {

	transactionID = n;
	bufferDirty = true;

        // For version2 packets value is in the varible header. We save
        // it in the fixed header as well -- just in case somebody changes
        // the version later.
        if (version >= VERSION2) {
            packetVariableHeader.setLongField(PacketString.TRANSACTIONID, n);
        }
    }

    public synchronized void setProducerID(long n) {
        packetVariableHeader.setLongField(PacketString.PRODUCERID, n);
	bufferDirty = true;
    }

    public synchronized void setDeliveryCount(int n) {
        packetVariableHeader.setIntField(PacketString.DELIVERY_COUNT, n);
	bufferDirty = true;
    }

    public synchronized void setDeliveryTime(long n) {
        packetVariableHeader.setLongField(PacketString.DELIVERY_TIME, n);
	bufferDirty = true;
    }

    public synchronized void setEncryption(int e) {
	encryption = (byte)e;
	bufferDirty = true;
    }

    public synchronized void setPriority(int p) {
	priority = (byte)p;
	bufferDirty = true;
    }

    public synchronized void setConsumerID(long n) {
	consumerID = n;
	bufferDirty = true;
    }

    public void setPersistent(boolean b) {
	setFlag(PacketFlag.P_FLAG, b);
    }

    public void setRedelivered(boolean b) {
	setFlag(PacketFlag.R_FLAG, b);
    }

    public void setIsQueue(boolean b) {
	setFlag(PacketFlag.Q_FLAG, b);
    }

    public void setSelectorsProcessed(boolean b) {
	setFlag(PacketFlag.S_FLAG, b);
    }

    public void setSendAcknowledge(boolean b) {
	setFlag(PacketFlag.A_FLAG, b);
    }

    public void setIsLast(boolean b) {
	setFlag(PacketFlag.L_FLAG, b);
    }

    public void setFlowPaused(boolean b) {
	setFlag(PacketFlag.F_FLAG, b);
    }

    public void setIsTransacted(boolean b) {
	setFlag(PacketFlag.T_FLAG, b);
    }

    public void setConsumerFlow(boolean b) {
	setFlag(PacketFlag.C_FLAG, b);
    }
    public void setIndempotent(boolean b) {
	setFlag(PacketFlag.I_FLAG, b);
    }

    public void setWildcard(boolean b) {
	setFlag(PacketFlag.W_FLAG, b);
    }

    public synchronized void setFlag(int flag, boolean on) {
	if (on) {
	    bitFlags = bitFlags | flag;
	} else {
	    bitFlags = bitFlags & ~flag;
	}
	bufferDirty = true;
    }

    public synchronized void setDestination(String s) {
        packetVariableHeader.setStringField(PacketString.DESTINATION, s);
	bufferDirty = true;
    }

    public synchronized void setDestinationClass(String s) {
        packetVariableHeader.setStringField(PacketString.DESTINATION_CLASS, s);
	bufferDirty = true;
    }

    public synchronized void setCorrelationID(String s) {
        packetVariableHeader.setStringField(PacketString.CORRELATIONID, s);
	bufferDirty = true;
    }

    public synchronized void setReplyTo(String s) {
        packetVariableHeader.setStringField(PacketString.REPLYTO, s);
	bufferDirty = true;
    }

    public synchronized void setReplyToClass(String s) {
        packetVariableHeader.setStringField(PacketString.REPLYTO_CLASS, s);
	bufferDirty = true;
    }

    public synchronized void setMessageType(String s) {
        packetVariableHeader.setStringField(PacketString.TYPE, s);
	bufferDirty = true;
    }

    /**
     * Set the message properties.
     * WARNING! The Hashtable is NOT copied.
     *
     * @param    props    The message properties.
     */
    public synchronized void setProperties(Hashtable props) {
        packetPayload.setProperties(props);
	bufferDirty = true;
    }

    /**
     * Set the message body.
     * 'body' is sliced to derive the message body so be careful
     * what the buffers position is!
     * Note: If you allocate a direct ByteBuffer and pass it here you
     * will get better performance than passing a byte[] 
     *
     * @param    body    The message body.
     */
    public synchronized void setMessageBody(ByteBuffer body) {
        packetPayload.setBody(body.slice());
	bufferDirty = true;
    }

    /**
     * Set the message body.
     * WARNING! The byte array is NOT copied.
     *
     * @param    body    The message body.
     */
    public synchronized void setMessageBody(byte[] body) {
        
    	//ByteBuffer buf = ByteBuffer.wrap(body);
        
    	ByteBuffer buf = null;
    	//check if body is null
    	if (body != null) {
    		buf = ByteBuffer.wrap(body);
    	}
    	
    	packetPayload.setBody(buf);
    	bufferDirty = true;
    }

    /**
     * Set the message body. Specify offset and length of where to take
     * data from buffer.
     * WARNING! The byte array is NOT copied.
     *
     * @param    body    The message body.
     * @param    off     Offset into body that data starts
     * @param    len     Size of message body
     */
    public synchronized void setMessageBody(byte[] body, int off, int len) {
        ByteBuffer buf = ByteBuffer.wrap(body, off, len);
        packetPayload.setBody(buf);
	bufferDirty = true;
    }

    protected ByteBuffer getHeaderBytes() {
        return fixedBuf;
    }

    protected PacketPayload getPacketPayload() {
        return packetPayload;
    }

    protected PacketVariableHeader getPacketVariableHeader() {
        return packetVariableHeader;
    }

    /**
     * Disable (and enable) sequence number generation. The JMQ packet 
     * specification defines a "sequence number" field that is defined
     * to be an increasing sequence number. By default Packet
     * will automatically increment the sequence number and set it
     * on the packet every time writePacket() is called.
     * The sequence number is a class variable so all packets in a VM
     * share the same sequence.
     *
     * @param    generate    true to have the packet automatically generate
     *                       sequence numbers for you, false to not. Default
     *                       is "true".
     */
    public void generateSequenceNumber(boolean generate) {
        genSequenceNumber = generate;
    }

    /**
     * Disable (and enable) timestamp generation. The JMQ packet specification
     * specifies a "timestamp" field that is defined to be the time the
     * packet was sent. By default ReadWritePacket will automatically 
     * generate a timestamp and set it on the packet every time
     * writePacket() is called.
     *
     * @param    generate    true to have the packet automatically generate
     *                       a timestamp on each packet sent, false to not.
     *                       Default is "true".
     */
    public void generateTimestamp(boolean generate) {
	genTimestamp = generate;
    }

    /**
     * Update the timestamp on the packet. If you do this
     * you should call generateTimestamp(false) before writing the
     * packet, otherwise the timestamp will be overwritten when
     * writePacket() is called.
     */
    public synchronized void updateTimestamp() {
        setTimestamp(System.currentTimeMillis());
    }

    /**
     * Update the sequence number on the packet. If you do this
     * you should call generateSequenceNumber(false) before writing the
     * packet, otherwise the sequence number will be overwritten when
     * writePacket() is called.
     */
    public synchronized void updateSequenceNumber() {
	synchronized(Packet.class) {
            sequenceNumber++;
	    setSequence(sequenceNumber);
	}
    }

    /**
     * Read a packet from a ReadableByteChannel. This method may be
     * used for blocking and non-blocking I/O. If you are using blocking
     * I/O then you should specify "true" for 'block'. If you are using
     * non-blocking then specify "false".
     *
     * Returns "true" if a complete packet has been read, else "false".
     * If this method returns false then you may call it again (as many
     * times as necessary) to try and complete the read. If 'block'
     * is true, then the method will block until a complete packet is read.
     *
     * If the packet being read is larger than the limit set by
     * setMaxPacketSize() then the packet bytes are skipped over
     * and this method will throw a BigPacketException. At that
     * point the packet is partially constructed, and only the header
     * fields in the fixed portion of the packet are valid. 
     */
    public synchronized boolean readPacket(ScatteringByteChannel channel,
                                           boolean block) 
        throws IOException {

        if (writeInProgress) {
            // Should never happen
            throw new IOException("Can't read packet. Write in progress.");
        }
        if (destroyed) {
            throw new IOException("Packet has been destroyed");
        }

        if (!readInProgress) {
            // No read in progress. Initialize.
            reset();
            headerBytesRead = 0;
            ropBytesRead = 0;
            readInProgress = true;
            versionMismatch = false;
        }

        // If we haven't completed reading the fixed header, try to do so
        if (headerBytesRead < HEADER_SIZE) {
            // readFixedHeader returns false if it hasn't completed the read
            if (!readFixedHeader(channel, block)) {
                return false;
            }
        }

        // At this point we know the full header has been read. Parse it.
        fixedBuf.rewind();
        parseFixedBuffer(fixedBuf);

        if (packetSize > maxPacketSize) {
            // This packet is too large. Skip it.
            skipBytes(channel, packetSize - HEADER_SIZE);
            throw new BigPacketException("Packet size (" + packetSize +
                ") is greater than the maximum allowed packet size ("
                + maxPacketSize + "). Disgarding packet." );
        }

        if (ropBytesRead == 0) {
            // Initialize read buffers
            initializeReadBufs();
        }

        // Try read of rest-of-packet. Will return FALSE if entire packet
        // was not read into read buffers.
        if (!readRestOfPacket(channel, block)) {
            return false;
        }

        // Yipeee! The entire packet has been read. Initialize
        packetVariableHeader.setBytes(varBuf);
        packetPayload.setPropertiesBytes(propBuf, version);
        packetPayload.setBody(bodyBuf);

        readInProgress = false;

        if (versionMismatch) {
            throw new IllegalArgumentException("Bad packet version number: " +
                version + ". Expecting: " + VERSION1 + " or " + VERSION2
                + " or " + VERSION3);
        }

        return true;
    }

    private transient BigPacketException bigPacketEx = null;
   
    public boolean hasBigPacketException() {
        return (bigPacketEx != null);
    }

    public BigPacketException getBigPacketException() {
        return bigPacketEx;
    }

    public synchronized boolean readPacket(ByteBuffer inputBuffer) 
        throws IOException {

        if (bigPacketEx != null) {
            int skipRemaining = bigPacketEx.getSkipBytesRemaining();
            while (skipRemaining > 0 && inputBuffer.hasRemaining()) {
                inputBuffer.get(); 
                skipRemaining--;
            }
            if (skipRemaining <= 0) {
                return true;
            }
            bigPacketEx.setSkipBytesRemaining(skipRemaining);
            return false;
        }

        if (writeInProgress) {
            // Should never happen
            throw new IOException("Can't read packet. Write in progress.");
        }
        if (destroyed) {
            throw new IOException("Packet has been destroyed");
        }

        if (!readInProgress) {
            // No read in progress. Initialize.
            reset();
            headerBytesRead = 0;
            ropBytesRead = 0;
            readInProgress = true;
            versionMismatch = false;
        }

        // If we haven't completed reading the fixed header, try to do so
        if (headerBytesRead < HEADER_SIZE) {
            // readFixedHeader returns false if it hasn't completed the read
            if (!readFixedHeader(inputBuffer)) {
                return false;
            }
        }

        // At this point we know the full header has been read. Parse it.
        fixedBuf.rewind();
        parseFixedBuffer(fixedBuf);

        if (packetSize > maxPacketSize) {
            // This packet is too large. Skip it.
            int skip = packetSize - HEADER_SIZE;
            int n = 0;
            while (inputBuffer.hasRemaining() && n < skip) {
                inputBuffer.get();
                n++;
            }
            bigPacketEx =  new BigPacketException("Packet size (" + packetSize +
                ") is greater than the maximum allowed packet size ("
                + maxPacketSize + "). Disgarding packet." );
            bigPacketEx.setSkipBytesRemaining(skip-n);

            throw bigPacketEx;
        }

        if (ropBytesRead == 0) {
            // Initialize read buffers
            initializeReadBufs();
        }

        // Try read of rest-of-packet. Will return FALSE if entire packet
        // was not read into read buffers.
        if (!readRestOfPacket(inputBuffer)) {
            return false;
        }

        // Yipeee! The entire packet has been read. Initialize
        packetVariableHeader.setBytes(varBuf);
        packetPayload.setPropertiesBytes(propBuf, version);
        packetPayload.setBody(bodyBuf);

        readInProgress = false;

        if (versionMismatch) {
            throw new IllegalArgumentException("Bad packet version number: " +
                version + ". Expecting: " + VERSION1 + " or " + VERSION2
                + " or " + VERSION3);
        }

        return true;
    }

    private long skipBytes(InputStream is, long n) throws IOException {

        long total = 0;
        int  zeroReads = 0;
        long r = 0;
        do {
            if (zeroReads > 1000) {
                // A kludge. If we are on a socket it might be
                // non-blocking. We don't want to spin tightly so
                // every 1000 zero byte reads we sleep for a bit.
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {}
                zeroReads = 0;
            }
            r = is.skip(n - total);

            if (r < 0) {
                throw new EOFException();
            } else if (r == 0) {
                // No bytes skipped. Try again. Keep track of how often this
                // happens
                zeroReads++;
            }
            total += r;
        } while (total < n);

        return total;

    }

    /**
     * Skip over and discard n bytes of data from this channel.
     * The skip method may, for a variety of reasons, end up skipping over
     * some smaller number of bytes, possibly 0. This may result from any 
     * of a number of conditions; reaching end of file before n bytes have
     * been skipped is only one possibility. The actual number of bytes
     * skipped is returned. If n is negative, no bytes are skipped.
     *
     * This simulates a blocking read.
     */
    private long skipBytes(ReadableByteChannel ch, long n) throws IOException {

        int CHUNK_SIZE = 1024;
        ByteBuffer buf = ByteBuffer.allocate(CHUNK_SIZE); 
        SelectableChannel sch = null;
        Selector selector = null;

        if (ch instanceof SelectableChannel) {
            sch = (SelectableChannel)ch;
        }

        if (sch != null && !sch.isBlocking()) {
            // If this is a non-blocking channel then register a selector
            // so we can block until data is ready.
            selector = Selector.open();
            sch.register(selector, SelectionKey.OP_READ);
        }

        long total = 0;
        int  zeroReads = 0;
        long r = 0;
        do {
            if (zeroReads > 1000) {
                // A kludge. Since we are on a channel it is likely 
                // non-blocking. We don't want to spin tightly so
                // every 1000 zero byte reads we sleep for a bit.
                // The select should make this unecessary.
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {}
                zeroReads = 0;
            }
            buf.rewind();
            r = ch.read(buf);

            if (r < 0) {
                throw new EOFException();
            } else if (r == 0) {
                zeroReads++;
                // No data ready to be read, block until there is some
                if (selector != null) {
                    selector.select(1000);
                }
            } else {
                total += r;
            }
        } while (total < n);

        return total;
    }

    /**
     * Read the fixed header into fixedBuf. Returns true if the entire
     * fixed header has been read. Returns false if it is a partial read.
     */
    private boolean readFixedHeader(ScatteringByteChannel channel,
                                    boolean block) throws IOException {

        // Keep reading while there is data to be read, or
        // we are supposed to block and the full header hasn't been
        // read yet.
        int n = 0;
        do {
            n = channel.read(fixedBuf);
            if (n < 0) {
                throw new EOFException();
            }
            headerBytesRead += n;
        } while ((n > 0 || block) && headerBytesRead < HEADER_SIZE);

        if (headerBytesRead < HEADER_SIZE) {
            return false;
        }

        return true;
    }

    private boolean readFixedHeader(ByteBuffer inputBuffer) throws IOException {

        while (inputBuffer.hasRemaining() && 
               headerBytesRead < HEADER_SIZE) {
            fixedBuf.put(inputBuffer.get());
            headerBytesRead ++;
        } 

        if (headerBytesRead < HEADER_SIZE) {
            return false;
        }

        return true;
    }


    /**
     * Initialize the readBufs to be the proper size. This must be
     * called after the fixed header has been read and parsed.
     * Returns the number of buffers allocated
     */
    protected void initializeReadBufs() {

        if (version != VERSION1 && version != VERSION2 && version != VERSION3) {
            // This is a packet version we don't understand. Set values
            // so we swallow rest of packet as the body
            propertyOffset = HEADER_SIZE;
            propertySize = 0;
            versionMismatch = true;
        }

        // Now that we know the sizes we can allocate buffers to read
        // the rest of the packet.
        int size = 0;
        nBufs = 0;

        // Variable header buffer
        size = propertyOffset - HEADER_SIZE;
        if (size != 0) {
            if (varBuf == null || varBuf.capacity() < size) {
                varBuf = allocateBuffer(size);
            } else {
                varBuf.clear();
                varBuf.limit(size);
            }
            readBufs[nBufs++] = varBuf;
        }

        // Properties buffer
        size = propertySize;
        if (size > 0) {
            if (propBuf == null || propBuf.capacity() < size) {
                propBuf = allocateBuffer(size);
            } else {
                propBuf.clear();
                propBuf.limit(size);
            }
            readBufs[nBufs++] = propBuf;
        }
                
        // Body Buffer
        size = packetSize - propertyOffset - propertySize;
        if (size > 0) {
            if (bodyBuf == null || bodyBuf.capacity() < size) {
                bodyBuf = allocateBuffer(size);
            } else {
                bodyBuf.clear();
                bodyBuf.limit(size);
            }
            readBufs[nBufs++] = bodyBuf;
        }

        // XXX 1/24/2002 dipol: Needed to work around nio bug (imq 4627557)
        for (int i = 0; i < readBufs.length; i++) {
            if (readBufs[i] != null) {
                readBufsLimits[i] = readBufs[i].limit();
            }
        }
    }

    /**
     * Read the the rest of the packet into the read buffers.
     * Returns true if the rest of the packet has been read.
     * Returns false if it was only partially read.
     */
    private boolean readRestOfPacket(ScatteringByteChannel channel,
                                    boolean block) throws IOException {

        // Use scattering read to read data into the three buffers
        // Keep reading while there is data to be read, or
        // we are supposed to block and the full packet hasn't been
        // read yet.
        long n = 0;
        do {
            // XXX 1/24/2002 dipol: Needed to work around nio memory leak bug
            //n = channel.read(readBufs, 0, nBufs);
            n = myChannelRead(channel, readBufs, 0, nBufs);

            if (n < 0) {
                throw new EOFException();
            }
            ropBytesRead += n;

            // XXX 1/24/2002 dipol: Needed to work around nio bug (imq 4627557)
            for (int i = 0; i < readBufs.length; i++) {
                if (readBufs[i] != null &&
                    readBufs[i].limit() != readBufsLimits[i]) {
                    readBufs[i].limit(readBufsLimits[i]);
                }
            }
            // XXX End work-around

        } while ((n > 0 || block) &&
                  ((ropBytesRead + headerBytesRead) < packetSize));

        if (headerBytesRead + ropBytesRead != packetSize) {
            return false;
        } else {
            return true;
        }
    }

    private boolean readRestOfPacket(ByteBuffer inputBuffer) throws IOException {

        int i = 0;
        while (inputBuffer.hasRemaining() && i < nBufs && 
               ((ropBytesRead + headerBytesRead) < packetSize)) {
            if (readBufs[i].position() < readBufs[i].limit()) { 
                readBufs[i].put(inputBuffer.get());
                ropBytesRead++;
            } else {
                i++; 
            }
        }

        if ((headerBytesRead + ropBytesRead) != packetSize) {
            return false;
        } else {
            return true;
        }
    }


    /**
     * Initialize the buffers used for writing (writeBufs[]). Returns the
     * number of valid buffers in writeBufs 
     */
    private int initializeWriteBufs()
        throws IOException {

        nBufs = 0;

        // We may have written fixedBuf before.
        fixedBuf.rewind();
        writeBufs[nBufs] = fixedBuf;
        nBufs++;

        if (version == VERSION1) {
            // The 2.0 packet code has a bug where it requires something
            // in the variable portion of the header, even if it is just
            // the Null list terminator. getBytes2() addresses this.
            writeBufs[nBufs] = packetVariableHeader.getBytes2();
        } else {
            writeBufs[nBufs] = packetVariableHeader.getBytes();
        }
        if (writeBufs[nBufs] != null) {
            nBufs++;
        }

        writeBufs[nBufs] = packetPayload.getPropertiesBytes(version);
        if (writeBufs[nBufs] != null) {
            nBufs++;
        }

        writeBufs[nBufs] = packetPayload.getBodyBytes();
        if (writeBufs[nBufs] != null) {
            nBufs++;
        }

        return nBufs;
    }

    /**
     * Write a packet to a WritableByteChannel. This method may be
     * used for blocking and non-blocking I/O. If you are using blocking
     * I/O then you should specify "true" for 'block'. If you are using
     * non-blocking then specify "false".
     *
     * Returns "true" if a complete packet has been written, else "false".
     * If this method returns false then you may call it again (as many
     * times as necessary) to try and complete the write. If 'block'
     * is true, then the method will block until a complete packet is written.
     */
    public synchronized boolean writePacket(GatheringByteChannel channel,
                                           boolean block)
        throws IOException {

        if (readInProgress) {
            // Should never happen
            throw new IOException("Can't write packet. Read in progress.");
        }

        if (!writeInProgress) {
            if (genSequenceNumber) {
                updateSequenceNumber();
            }

            if (genTimestamp) {
                updateTimestamp();
            }
            updateBuffers();

            // Prepare write buffers for writing.
            initializeWriteBufs();

            writeInProgress = true;
            bytesWritten = 0;
        }

        long n = 0;
        do {
            // XXX 1/24/2002 dipol: Needed to work around nio memory leak bug
            //n = channel.write(writeBufs, 0, nBufs);
            n = myChannelWrite(channel, writeBufs, 0, nBufs);

            bytesWritten += n;
        } while ((n > 0 || block) && (bytesWritten < packetSize));

        if (bytesWritten != packetSize) {
            return false;
        }

        writeInProgress = false;

        return true;
    }

    public synchronized boolean writePacket(
        ByteBufferOutput endpoint, boolean outputByteBuffer)
        throws IOException {

        if (readInProgress) {
            // Should never happen
            throw new IOException("Can't write packet. Read in progress.");
        }

        if (!writeInProgress) {
            if (genSequenceNumber) {
                updateSequenceNumber();
            }

            if (genTimestamp) {
                updateTimestamp();
            }
            updateBuffers();

            // Prepare write buffers for writing.
            initializeWriteBufs();

            writeInProgress = true;
            bytesWritten = 0;
        }

        int length = 0;
        for (int i = 0; i < nBufs; i++) {
            length += writeBufs[i].remaining();
        }
        byte[] b = new byte[length]; //XXopt

        int len = 0;
        int offset = 0 ;
        for (int i = 0; i < nBufs; i++) {
            len = writeBufs[i].remaining();
            writeBufs[i].get(b, offset, len);
            offset += len;
        }
        if (outputByteBuffer) {
            ByteBuffer buf = ByteBuffer.wrap(b);
            endpoint.writeByteBuffer(buf);
        } else {
            endpoint.writeBytes(b);
        } 

        writeInProgress = false;

        return true;
    }


    /**
     * Return a unique string that identifies the packet
     */
    public synchronized String toString() {
	return
	    PacketType.getString(packetType) + ":" +
	    sysMessageID.toString();
    }

    /**
     * Return a string containing the contents of the packet in a human
     * readable form.
     *
     * @param prefix String to prefix string with. By default "********"
     */
    public synchronized String dumpPacketString(String prefix) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

	this.dump(new PrintStream(bos), prefix);

	return bos.toString();
    }

    public String dumpPacketString() {
        return dumpPacketString(null);
    }

    public void dump(PrintStream os) {
        this.dump(os, null);
    }

    /**
     * Return a string representation of the data in the fixed header
     * portion of the packet.
     */
    public synchronized String headerToString() {

        return (

	"  Magic/Version: " + magic + "/" + version + "\tSize: " + packetSize +
		"\t Type: " + PacketType.getString(packetType) + "\n" +

	"     Expiration: " + expiration +
        ((version == VERSION1) ? ("   TransactionID: " + transactionID) : "\t\t") +
	"       Timestamp: " + sysMessageID.timestamp +
	"\n" +

	"      Source IP: " + sysMessageID.ip.toString() +
	      "  Port: " + sysMessageID.port +
		"\tSequence: " + sysMessageID.sequence + "\n" +
	
	"Property Offset: " + propertyOffset +
		"\t\t\tProperty Size: " + propertySize + "\n" +
	
	"     Encryption: " + encryption + "\tPriority: " +
		priority + "\n" +

	
	"          Flags: " + PacketFlag.getString(bitFlags) +
		"\t\t\t   consumerID: " +
						consumerID);
    }


    /**
     * Dump the contents of the packet in human readable form to
     * the specified OutputStream.
     *
     * @param    os    OutputStream to write packet contents to
     * @param    prefix String prefix to print before the packet.
     *                  If null "********" is used.
     */
    public synchronized void dump(PrintStream os, String prefix) {

        if (prefix == null) prefix = "********";

        try {
            updateBuffers();
        } catch (Exception e) {
            os.println("Warning!: Could not update buffers: " + e);
        }

	os.println(prefix + " Packet: " + this.toString() + "\n" +
                   headerToString());

        if (version >= VERSION2) {
	    os.println("   TransactionID: " + getTransactionID());
        }
        if (getProducerID() != 0L) {
	    os.println("      ProducerID: " + getProducerID());
        }
        if (getDeliveryTime() != 0L) {
	    os.println("      DeliveryTime: " + getDeliveryTime());
        }
        if (getDeliveryCount() != 0) {
	    os.println("      DeliveryCount: " + getDeliveryCount());
        }
	if (getDestination() != null)
	    os.println("     Destination: " + getDestination());
	if (getDestinationClass() != null)
	    os.println("DestinationClass: " + getDestinationClass());
	if (getMessageID() != null)
	    os.println("       MessageID: " + getMessageID());
	if (getCorrelationID() != null)
	    os.println("   CorrelationID: " + getCorrelationID());
	if (getReplyTo() != null)
	    os.println("         ReplyTo: " + getReplyTo());
	if (getReplyToClass() != null)
	    os.println("    ReplyToClass: " + getReplyToClass());
	if (getMessageType() != null)
	    os.println("     MessageType: " + getMessageType());

        os.flush();

	Hashtable props = null;
	try {
	    props = getProperties();
	} catch (Exception e) {
	    os.println("Exception getting properties: " + e.getMessage());
	}
	if (props == null) {
	    os.println("      Properties: null");
        } else {
	    os.println("      Properties: " + props);
	}

        PacketUtil.dumpBody(os, packetType,
                getMessageBodyStream(), getMessageBodySize(), props);

        os.println("Internal Buffers (useDirect=" + useDirect + "):");
        os.println("Fixed Header Buffer:" + fixedBuf.toString());

/*
        os.print("    Variable Header Buffer:" );
        String s = null;
        try {
            s = packetVariableHeader.getBytes().toString();
        } catch (Exception e) {
            s = "Could not get buffer: " + e;
        }
        os.println(s);
        os.println("    Properties Buffer: " + 
            packetPayload.getPropertiesBytes().toString());
        os.println("    Body Buffer: " + 
            packetPayload.getBodyBytes().toString());
*/
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
     * If the packet being read is larger than the limit set by
     * setMaxPacketSize() then the packet bytes are skipped over
     * and this method will throw a BigPacketException. At that
     * point the packet is partially constructed, and only the header
     * fields in the fixed portion of the packet are valid. 
     *
     * @param is        the InputStream to read the packet from
     */
    public synchronized void readPacket(InputStream is)
	throws IOException, EOFException, StreamCorruptedException, IllegalArgumentException {

        if (writeInProgress) {
            // Should never happen
            throw new IOException("Can't read packet. Write in progress.");
        }

        reset();

        int bytesrd = 0;
        try {

        // ReadFixed buffer
        bytesrd = readFully(is, fixedBuf, true);

        // At this point we know the full header has been read. Parse it.
        fixedBuf.rewind();
        parseFixedBuffer(fixedBuf);

        if (packetSize > maxPacketSize) {
            // This packet is too large. Skip it.
            skipBytes(is, packetSize - HEADER_SIZE);
            throw new BigPacketException("Packet size (" + packetSize +
                ") is greater than the maximum allowed packet size ("
                + maxPacketSize + "). Disgarding packet." );
        }

        // Initialize read buffers
        initializeReadBufs();

        // Read rest of packet
        for (int i = 0; i < nBufs; i++) {
            bytesrd += readFully(is, readBufs[i], true);
        }

        } catch (PacketReadEOFException e) {
        e.setBytesRead(bytesrd);
        e.setPacketSize(packetSize);
        throw e;
        }

        // Yipeee! The entire packet has been read. Initialize
        packetVariableHeader.setBytes(varBuf);
        packetPayload.setPropertiesBytes(propBuf, version);
        packetPayload.setBody(bodyBuf);

        if (versionMismatch) {
            throw new IllegalArgumentException("Bad packet version number: " +
                version + ". Expecting: " + VERSION1 + " or " + VERSION2
                 + " or " + VERSION3);
        }

	return;
    }

    /**
     * A version of readFully that reads from an InputStream and puts
     * the data into a ByteBuffer.
     */
    private int readFully(InputStream in, ByteBuffer b, boolean retry) 
	throws IOException, EOFException, InterruptedIOException {

        byte[] abuf = null;
        int offset = 0;
        int length = 0;
        boolean allocate = false;

        // If it is a direct buffer we won't be able to get backing array.
        // It is a bit faster to explicitly check this case rather than
        // rely on catching the UnsupportedOperationException.
        if (!b.hasArray()) {
            allocate = true;
        }

        if (!allocate) {
            // Get ByteBuffers backing byte array
            try  {
                abuf  = b.array();
                offset = b.arrayOffset() + b.position();
                length = b.remaining();
            } catch (UnsupportedOperationException e) {
                allocate = true;
            }
        }

        if (allocate) {
            // Bummer. Allocate a byte[]
            abuf = new byte[b.remaining()];
            allocate = true;
            offset = 0;
            length = abuf.length;
        }
        readFully(in, abuf, offset, length, true);
        b.rewind();

        // If we had to allocate the byte[] then copy data into ByteBuffer.
        // We don't wrap because we don't want to change they type of backing
        // buffer used by ByteBuffer.
        if (allocate) {
            b.put(abuf);
        }
        return length;
    }

    /**
     * Our own version of readFully(). This is identical to DataInputStream's
     * except that we handle InterruptedIOException by doing a yield, and
     * continuing trying to read. This is to handle the case where the
     * socket has an SO_TIMEOUT set.
     *
     * If retry is false we abandon the read if it times out and we haven't
     * read anything. If it is true we continue to retry the read.
     */
    private void readFully(InputStream in, byte b[], int off, int len, 
			boolean retry)
	throws IOException, EOFException, InterruptedIOException {

        if (len < 0)
            throw new IndexOutOfBoundsException();

        int n = 0;
	int count;
        while (n < len) {
	    count = 0;
            try {
                count = in.read(b, off + n, len - n);
            } catch (InterruptedIOException e) {
                // if we really have read nothing .. throw an ex
                if (!retry && n == 0 && count == 0 && e.bytesTransferred == 0) {
                    throw new InterruptedIOException("no data available");
		}

		count = e.bytesTransferred;
                if (count < 0) { //should not happen
                    throw new PacketReadEOFException(
                    "Trying to read "+(len - n)+" bytes, interrupted. Already read "+n+" bytes.");
                 
                }

		Thread.yield();
	    }
            if (count < 0) {
                throw new PacketReadEOFException(
                "Trying to read "+(len - n)+" bytes. Already read "+n+" bytes.");
            }
            n += count;
        }
    }

    /**
     * Write the packet to an OutputStream. Blocking.
     *
     * @param os                The OutputStream to write the packet to
     *
     */
    public synchronized void writePacket(OutputStream os)
        throws IOException {

        if (genSequenceNumber) {
            updateSequenceNumber();
        }

        if (genTimestamp) {
            updateTimestamp();
        }

        // Update buffers and write them
        updateBuffers();
        initializeWriteBufs();

        byte[] b = null;
        int offset = 0;
        int length = 0;
        boolean allocate = false;

        for (int i = 0; i < nBufs; i++) {

            // Faster than relying on catching UnsupportedException
            if (writeBufs[i].isDirect()) {
                allocate = true;
            }

            if (!allocate) {
                try {
                    b = writeBufs[i].array();
                    offset = writeBufs[i].arrayOffset();
                    length = writeBufs[i].remaining();
                } catch (UnsupportedOperationException e) {
                    // Bummer. Allocate a byte[] and copy data to it
                    allocate = true;
                }
            }

            if (allocate) {
                b = new byte[writeBufs[i].remaining()];
                writeBufs[i].get(b);
                offset = 0;
                length = b.length;
            }
            os.write(b, offset, length);
        }

        os.flush();
    }

    /**
     * Retrieves the packet as an array of bytes.
     */
    public synchronized byte[] getBytes()
        throws IOException {

        if (genSequenceNumber) {
            updateSequenceNumber();
        }

        if (genTimestamp) {
            updateTimestamp();
        }

        // Update buffers and write them
        updateBuffers();
        initializeWriteBufs();

        byte[] b = new byte[packetSize];
        int offset = 0;
        int length = 0;

        for (int i = 0; i < nBufs; i++) {
            length = writeBufs[i].remaining();
            writeBufs[i].get(b, offset, length);
            offset += length;
        }

        return b;
    }

    /**
     * Prepare this Packet for the sendMessage method of
     * the jmq.jmsservice.JMSService interface.
     */  
    public synchronized void prepareToSend() {

        //Update the global sequence number and
        //generate the timestamp on this packet.
        updateSequenceNumber();
        updateTimestamp();
        //Ensure MESSAGEID in StringField is cleared
        if (this.packetVariableHeader.getStringField(PacketString.MESSAGEID) !=null){
            this.packetVariableHeader.setStringField(PacketString.MESSAGEID, null);
        }
    }    

    /**
     * Allocates a ByteBuffer of the specified capacity.
     */
    protected ByteBuffer allocateBuffer(int capacity) {
        ByteBuffer b = null;
        if (useDirect) {
            // The pool is only used for direct ByteBuffers
            if (bbPool == null) {
                b = ByteBuffer.allocateDirect(capacity);
            } else {
                b = bbPool.get(capacity);
            }
            // Track allocated direct ByteBuffers
            allocatedBuffers.add(b);
        } else {
            b = ByteBuffer.allocate(capacity);
        }
        return b;
    }

    /**
     * Destroy a packet and put all direct buffers back into the buffer pool.
     *
     * WARNING!
     * Once destroy is called the packet may not be used again.
     * Once destroy is called all buffers ever returned by the packet
     * are invalid!
     */
    public synchronized void destroy() {
        destroyed = true;

        ByteBuffer b = null;

        this.reset();

        Iterator iterator = allocatedBuffers.iterator();
        while (iterator.hasNext()) {
            b = (ByteBuffer)iterator.next();
            if (b.isDirect() && bbPool != null) {
                // put() ignores null and knows to disgard non-direct buffers
                bbPool.put(b);
            }
            iterator.remove();
        }

        // Null all buffers to ensure they are not accessed again
        for (int i = 0; i < writeBufs.length; i++) {
            writeBufs[i] = null;
        }

        for (int i = 0; i < readBufs.length; i++) {
            readBufs[i] = null;
        }

        varBuf = null;
        propBuf = null;
        bodyBuf = null;
        fixedBuf = null;
        packetVariableHeader = null;
        packetPayload = null;
    }

    public static void dumpBufs(ByteBuffer bufs[]) {

        for (int i = 0; i < bufs.length; i++) {
            System.out.println("bufs[" + i + "]=" + bufs[i]);
        }

    }

    /**
     * myChannelRead and myChannelWrite are used to work-around an
     * nio bug. Nio scattering/gathering IO methods leak a ton of
     * memory, so we kludge our own versions.
     *
     * See iMQ bug 4629634 for more info
     */

    public static long myChannelWrite(
        GatheringByteChannel channel,
        ByteBuffer[] bufs, int offset, int length)
        throws IOException {

        int n = 0;
        int r = 0;
        int i = offset;
        while (i < length) {
            int remains = bufs[i].remaining();
            if (remains != 0) {
                r = channel.write(bufs[i]);
                if (r < 0) {
                    return r;
                }
                if (r == 0) {
                    return n;
                }
                n += r;
                if (r == remains) {
                    // We've written all there is to write. Go to next buffer
                    i++;
                } else {
                    // More left in the buffer. Try same bufer again
                    ;
                }
            } else {
                // Buffer has nothing left. Go to next buffer
                i++;
            }
        }
       
        return n;
    }

    public static long myChannelRead(
        ScatteringByteChannel channel,
        ByteBuffer[] bufs, int offset, int length)
        throws IOException {

        int n = 0;
        int r = 0;
        int i = offset;
        while (i < length) {
            int remains = bufs[i].remaining();
            if (remains != 0) {
                r = channel.read(bufs[i]);
                if (r < 0) {
                    return r;
                }
                if (r == 0) {
                    return n;
                }
                n += r;
                if (r == remains) {
                    // We've read all there is to read. Go to next buffer
                    i++;
                } else {
                    // More space in the buffer. Try same buffer again
                    ;
                }
            } else {
                // Buffer has no more space. Go to next buffer
                i++;
            }
        }
       
        return n;
    }


    public javax.jms.Message getMessage() {
         return null;
    }

    public com.sun.messaging.jmq.io.Packet getPacket() {
         return this;
    }
}
