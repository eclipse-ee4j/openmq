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
 * @(#)GPacket.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 *
 * A Generic binary packet format. This class encapsulates a simple
 * binary packet format. The packet is made up of a fixed header,
 * a marshalled properties section, and an opaque payload. 
 */
public class GPacket {

    /* The 32 bit flags */
    public static final int A_BIT = 0x00000001;
    public static final int B_BIT = 0x00000002;
    public static final int C_BIT = 0x00000004;
    public static final int D_BIT = 0x00000008;
    public static final int E_BIT = 0x00000010;
    public static final int F_BIT = 0x00000020;
    public static final int G_BIT = 0x00000040;
    public static final int H_BIT = 0x00000080;
    public static final int I_BIT = 0x00000100;
    public static final int J_BIT = 0x00000200;
    public static final int K_BIT = 0x00000400;
    public static final int L_BIT = 0x00000800;
    public static final int M_BIT = 0x00001000;
    public static final int N_BIT = 0x00002000;
    public static final int O_BIT = 0x00004000;
    public static final int P_BIT = 0x00008000;
    public static final int Q_BIT = 0x00010000;
    public static final int R_BIT = 0x00020000;
    public static final int S_BIT = 0x00040000;
    public static final int T_BIT = 0x00080000;
    public static final int U_BIT = 0x00100000;
    public static final int V_BIT = 0x00200000;
    public static final int W_BIT = 0x00400000;
    public static final int X_BIT = 0x00800000;
    public static final int Y_BIT = 0x01000000;
    public static final int Z_BIT = 0x02000000;
    public static final int a_BIT = 0x04000000;
    public static final int b_BIT = 0x08000000;
    public static final int c_BIT = 0x10000000;
    public static final int d_BIT = 0x20000000;
    public static final int e_BIT = 0x40000000;
    public static final int f_BIT = 0x80000000;

    public static final int VERSION350  = 350;
    public static final int VERSION     = VERSION350;
    public static final int MAGIC       = 2147476418;
    public static final int HEADER_SIZE = 36;

    private boolean genTimestamp = true;
    private boolean genSequenceNumber  = true;

    private static long sequenceCounter = 0;

    protected short version       = VERSION;
    protected short type          = 0;
    protected int   size          = 0;
    protected int   propsByteSize = 0;
    protected int   magic         = MAGIC;
    protected long  sequence      = 0;
    protected long  timestamp     = 0;
    protected int   bitFlags      = 0;

    protected ByteBuffer propsBuf = null;
    protected Map      props      = null;
    protected boolean  propsDirty = false;
    protected boolean  versionMismatch = false;

    protected ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_SIZE);
    protected boolean    headerDirty = false;

    protected ByteBuffer payload  = null;

    /**
     * Get an instance of an empty packet
     */
    public static GPacket getInstance() {
        return new GPacket();
    }

    private GPacket() {
    }

    /**
     * Parse the header buffer into instance variables
     */
    private void unmarshallHeader()
        throws StreamCorruptedException, IOException {

        headerBuf.rewind();

        version = headerBuf.getShort();
        type    = headerBuf.getShort();
        size    = headerBuf.getInt();
        magic   = headerBuf.getInt();

        if (version != VERSION350) {
            versionMismatch = true;
        } else {
            versionMismatch = false;
        }

        if (magic != MAGIC) {
            throw new StreamCorruptedException("Bad packet magic number: " +
                magic + ". Expecting: " + MAGIC);
        }

        propsByteSize = headerBuf.getInt();
        timestamp     = headerBuf.getLong();
        sequence      = headerBuf.getLong();
        bitFlags      = headerBuf.getInt();

        headerBuf.rewind();

        headerDirty = false;
    }

    /**
     * Prepare the header buffer for writing by marshalling the
     * instance variables into the header buffer.
     */
    private void marshallHeader() 
        throws IOException {

        if (propsDirty) {
            // Will set headerDirty 
            marshallProperties();
        }

        if (!headerDirty) {
            return;
        }

        size = headerBuf.capacity() + propsByteSize;

        if (payload != null) {
            size += payload.capacity();
        }

        headerBuf.rewind();
        headerBuf.putShort(version);
        headerBuf.putShort(type);
        headerBuf.putInt(size);
        headerBuf.putInt(magic);
        headerBuf.putInt(propsByteSize);
        headerBuf.putLong(timestamp);
        headerBuf.putLong(sequence);
        headerBuf.putInt(bitFlags);

        headerBuf.rewind();

        headerDirty = false;
    }

    /**
     * Parse the properties buffer into a hash table
     */
    private void unmarshallProperties() 
        throws IOException, ClassNotFoundException {

        if (propsByteSize > 0) {
            props = PacketProperties.parseProperties(
                    new JMQByteBufferInputStream(propsBuf));
            propsBuf.rewind();
        } else {
            props = new Hashtable();
        }
        propsDirty = false;
        return;
    }

    /**
     * Prepare the property buffer by marshalling the properties hash
     * table into the property buffer.
     */
    private void marshallProperties()
        throws IOException {

        if (!propsDirty) {
            return;
        }

        // Backing byte array will grow if needed
        JMQByteArrayOutputStream bos =
                            new JMQByteArrayOutputStream(new byte[256]);
        PacketProperties.write(props, bos);

        propsBuf = ByteBuffer.wrap(bos.getBuf(), 0, bos.getCount());
        propsBuf.rewind();
        propsByteSize = bos.getCount();
        propsDirty = false;
        headerDirty = true;
    }

    /**
     * Set the packet's payload. The passed buffer is not copied, it
     * is sliced with ByteBuffer.slice().
     *
     * @param   b   ByteBuffer containing payload. The buffer is not
     *              copied it is sliced. The slice starts at the
     *              buffer's current <code>position</code>. 
     *              <code>b</code> may be <code>null</code> which 
     *              essentially removes any payload that was previously set.
     */
    public synchronized void setPayload(ByteBuffer b) {
        if (b == null) {
            payload = null;
        } else {
            payload = b.slice();
        }
        headerDirty = true;
    }

    /**
     * Return the packet's payload. The returned buffer is not a copy,
     * it is a slice.
     *
     * @return ByteBuffer containing the payload. Note the returned buffer
     *                    is not a copy, it is a slice.
     */
    public synchronized ByteBuffer getPayload() {
        if (payload == null) {
            return null;
        } else {
            return payload.slice();
        }
    }

    /**
     * Set packet version. Default is current version
     *
     * @param   v   Set packet version. Default is the most recent version
     *              which is defined by GPacket.VERSION
     */
    public synchronized void setVersion(short v) {
        version = v;
        headerDirty = true;
    }

    /**
     * Get packet version.
     *
     * @return  Version of this packet.
     */
    public synchronized short getVersion() {
        return version;
    }

    /**
     * Set packet type. This is an application specific value
     *
     * @param   t   The packet type. This is an application defined value.
     *              Default is 0.
     */
    public synchronized void setType(short t) {
        type = t;
        headerDirty = true;
    }

    /**
     * Get packet type
     *
     * @return  Packet type.
     */
    public synchronized short getType() {
        return type;
    }


    /**
     * Set packet timestamp. By default it's generated
     * when the packet is written. If you want to set the timestamp
     * using this method you must turn off timestamp generation
     * using <code>generateTimestamp()</code>, otherwise
     * what you set will be overwritten when the packet is written.
     *
     * @param   t   Timestamp to set.
     *
     * @see generateTimestamp()
     * @see updateTimestamp()
     */
    public synchronized void setTimestamp(long t) {
        timestamp = t;
        headerDirty = true;
    }


    /**
     * Get packet timestamp.
     *
     * @return Packet's timestamp
     */
    public synchronized long getTimestamp() {
        return timestamp;
    }

    /**
     * Set packet sequence number. By default it's generated
     * when the packet is written. If you want to set the sequence number
     * using this method you must turn off sequence number generation
     * using <code>generateSequenceNumber()</code>, otherwise
     * what you set will be overwritten when the packet is written.
     *
     * @param s Sequence number to set on packet.
     *
     * @see generateSequenceNumber()
     * @see updateSequence()
     */
    public synchronized void setSequence(long s) {
        sequence = s;
        headerDirty = true;
    }

    /**
     * Get packet sequence number
     *
     * @return Packet sequence number
     */
    public synchronized long getSequence() {
        return sequence;
    }

    /**
     * Get size of packet in bytes
     *
     * @return Size of fully marshalled packet in bytes.
     */
    public synchronized int getSize() {
        return size;
    }

    /**
     * Get size of marshalled properties in bytes. First
     * call to this may be expensive if the properties need
     * to be marshalled to compute the size.
     *
     * @return Size of marshalled properties in bytes.
     *
     * @throws IOException  If properties could not be marshalled.
     */
    public synchronized int getPropsByteSize() throws
        IOException {
        getProperties();
        if (propsDirty) {
            marshallProperties();
        }
        return propsByteSize;
    }

    /**
     * Get size of payload in bytes
     *
     * @return Size of payload in bytes
     */
    public synchronized int getPayloadSize() {
        if (payload == null) {
            return 0;
        } else {
            return payload.capacity();
        }
    }

    /**
     * Get packets magic number
     *
     * @return Packet's magic number. This should always be GPacket.MAGIC
     */
    public synchronized int getMagic() {
        return magic;
    }

    /**
     * Clear packet properties.
     */
    public synchronized void clearProps() {
        getProperties();
        propsDirty = true;
        props.clear();
    }

    /**
     * Get a packet property
     *
     * @param key   Property key
     * @return      Property value
     */
    public synchronized Object getProp(Object key) {
        getProperties();
        return props.get(key);
    }

    /**
     * Put a packet property
     *
     * @param key       Property key
     * @param value     Property value
     */
    public synchronized void putProp(Object key, Object value) {
        getProperties();
        propsDirty = true;
        props.put(key, value);
    }


    /**
     * Put a map of properties
     *
     * @param t     Map of properties to put
     */
    public synchronized void putAllProps(Map t) {
        getProperties();
        propsDirty = true;
        props.putAll(t);
    }

    /**
     * Remove a property
     *
     * @param   key     Key of property to remove
     */
    public synchronized Object removeProp(Object key) {
        propsDirty = true;
        getProperties();
        propsDirty = true;
        return props.remove(key);
    }

    /**
     * Check if packet has properties
     *
     * return   True if the packet has any properties, else false.
     */
    public synchronized boolean isEmptyProps() {
        if (props == null && propsByteSize == 0) {
            return true;
        } else {
            getProperties();
            return props.isEmpty();
        }
    }

    /**
     * Get the number of properties
     *
     * @return  The number of properties the packet has
     */
    public synchronized int propsSize() {
        if (props == null && propsByteSize == 0) {
            return 0;
        }
        getProperties();
        return props.size();
    }

    /**
     * Returns a Set of keys.
     *
     * @return Set of keys
     *
     * @see java.util.Map.keySet()
     */
    public synchronized Set propsKeySet() {
        getProperties();
        return props.keySet();
    }

    /**
     * Returns a Set of entries (Map.Entry)
     *
     * @return Set of entries (Map.Entry)
     *
     * @see java.util.Map.entrySet()
     */
    public synchronized Set propsEntrySet() {
        getProperties();
        return props.entrySet();
    }

    /**
     * Set a bit flag. There are 32 flags available. 
     *
     * @param   bit     The bit to set. Should be one of GPacket.A_BIT
     *                  through GPacket.Z_Bit, or GPacket.a_BIT through
     *                  GPacket.f_Bit.
     *
     * @param   on      <code>true</code> to turn bit on, else
     *                  <code>false</code>
     */
    public synchronized void setBit (int bit, boolean on) {
        if (on) {
            bitFlags = bitFlags | bit;
        } else {
            bitFlags = bitFlags & ~bit;
        }
        headerDirty = true;
    }

    /**
     * Get a bit flag.
     *
     * @param   bit     The bit to get. Should be one of GPacket.A_BIT
     *                  through GPacket.Z_Bit, or GPacket.a_BIT through
     *                  GPacket.f_Bit.
     * @return  <code>true</code> if bit is set, else <code>false</code>
     */
    public synchronized boolean getBit (int bit) {
        return((bitFlags & bit) == bit);
    }

    /**
     * Disable (and enable) sequence number generation. The GPacket
     * specification defines a "sequence number" field that is defined
     * to be a monotonically increasing sequence number. By default GPacket
     * will automatically increment the sequence number and set it
     * on the packet every time writePacket() is called.
     * The sequence number is a class variable so all packets in a VM
     * share the same sequence.
     *
     * @param    generate    <code>true</code> to have the packet
     *                       automatically generate
     *                       sequence numbers for you, <code>false</code> to
     *                       not. Default is <code>true</code>.
     */
    public void generateSequenceNumber(boolean generate) {
        genSequenceNumber = generate;
    }

    /**
     * Disable (and enable) timestamp generation. The GPacket specification
     * specifies a "timestamp" field that is defined to be the time the
     * packet was sent. By default GPacket will automatically 
     * generate a timestamp and set it on the packet every time
     * writePacket() is called.
     *
     * @param    generate    <code>true</code> to have the packet
     *                       automatically generate a timestamp when the
     *                       packet is written, <code>false</code> to not.
     *                       Default is <code>true</code>.
     */
    public void generateTimestamp(boolean generate) {
	genTimestamp = generate;
    }

    /**
     * Update the timestamp on the packet. The will stamp the packet
     * with the current time. If you do this
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
	synchronized(GPacket.class) {
            sequenceCounter++;
	    setSequence(sequenceCounter);
	}
    }

    /**
     * Read packet from an InputStream. This method reads one packet
     * from the InputStream and sets the state of this object to
     * reflect the packet read.
     *
     * If we read a packet with a bad magic number (ie it looks like
     * bogus data), we give up and throw a StreamCorruptedException. 
     *
     * If we read a packet that does not match our packet version
     * we attempt to read the entire packet and throw an
     * IllegalArgumentException.
     *
     * This method blocks until a full packet can be read.
     *
     * @param is            the InputStream to read the packet from
     *
     * @throws IOException  if there was an error reading
     *
     * @throws EOFException if the connection has been closed
     *
     * @throws StreamCorruptedException if the packet data appears corrupted.
     *                                  If this is thrown then either there
     *                                  is bad data on the wire or GPacket 
     *                                  has a bug and got in the wrong spot
     *                                  in the stream. In either case 
     *                                  subsequent reads will most likely
     *                                  fail.
     *
     * @throws IllegalArgumentException if the packet read has a bad version.
     *                                  In this case the entire packet is
     *                                  read, but can't be parsed. Subusequent
     *                                  reads should still work, but you
     *                                  may continue to encounter bogus packet
     *                                  versions.
     *                                  
     */
    public synchronized void read(InputStream is)
	throws IOException, EOFException, StreamCorruptedException, IllegalArgumentException {

        // ReadFixed buffer
        headerBuf.rewind();
        readFully(is, headerBuf);
        headerBuf.rewind();

        // At this point we know the full header has been read. Parse it.
        unmarshallHeader();

        props = null;
        payload = null;

        if (propsByteSize > 0) {
            // Packet has properties
            if (propsBuf != null && propsBuf.capacity() >= propsByteSize) {
                // Reuse buffer if it is big enough
                propsBuf.clear();
                propsBuf.limit(propsByteSize);
            } else {
                // allocate new
                propsBuf = ByteBuffer.allocate(propsByteSize);
            }
            readFully(is, propsBuf);
            propsBuf.rewind();
        } else {
            propsBuf = null;
        }

        int l = size - propsByteSize - HEADER_SIZE;

        if (l > 0) {
            payload = ByteBuffer.allocate(l);
            readFully(is, payload);
        } else {
            payload = null;
        }

        if (versionMismatch) {
            throw new IllegalArgumentException("Bad packet version number: " +
                version + ". Expecting: " + VERSION350);
        }

	return;
    }

   /**
     * Write the packet to an OutputStream. This method blocks until
     * the packet can be written. This method will also generate packet
     * sequence and timestamps if so configured.
     *
     * @param os                The OutputStream to write the packet to
     *
     * @throws IOException      if there was an error on write
     *
     * @see generateTimestamp()
     * @see generateSequenceNumber()
     *
     */
    public synchronized void write(OutputStream os)
        throws IOException {

        if (genSequenceNumber) {
            updateSequenceNumber();
        }

        if (genTimestamp) {
            updateTimestamp();
        }

        // This wil update the property buffer too
        marshallHeader();

        headerBuf.rewind();
        writeFully(os, headerBuf);
        headerBuf.rewind();

        if (propsByteSize > 0) {
            propsBuf.rewind();
            writeFully(os, propsBuf);
            propsBuf.rewind();
        }

        if (payload != null) {
            payload.rewind();
            writeFully(os, payload);
            payload.rewind();
        }

        os.flush();
    }


    /** 
     * Get all properties from the packet. 
     *
     */
    private Map getProperties() {
        if (props == null) {
            try {
                unmarshallProperties();
            } catch (IOException e) {
                throw new RuntimeException("Can't unmarshall pkt", e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't unmarshall pkt", e);
            }
        }
        return props;
    }

    /**
     * Write a ByteBuffer to an OutputStream. Blocking.
     */
    private static void writeFully(OutputStream os, ByteBuffer buf)
        throws IOException {
        byte[] b = buf.array();
        int    offset = buf.arrayOffset() + buf.position();
        int    length = buf.remaining();
        os.write(b, offset, length);
    }


    /**
     * Read fully into a ByteBuffer from an InputStream. Blocking.
     */
    private static void readFully(InputStream in, ByteBuffer buf)
        throws IOException {

        byte[] b = buf.array();
        int offset = buf.arrayOffset() + buf.position();
        int length = buf.remaining();

        readFully(in, b, offset, length, true);
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
    private static void readFully(InputStream in, byte b[], int off, int len, 
			boolean retry)
	throws IOException, EOFException, InterruptedIOException {

        if (len < 0)
            throw new IndexOutOfBoundsException();

        //System.out.println("readFully(off=" + off + ", len=" + len);
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

		Thread.currentThread().yield();
	    }
            if (count < 0) {
                throw new EOFException("Trying to read " + (len - n) +
                    " bytes. Already read " + n + " bytes.");
            }
            n += count;
        }
    }

    /**
     *
     * Check if two packets are equal.
     *
     * Two packets are considered equal if all the header fields are
     * equal, and the properties are equal. Note that this does NOT
     * compare the payload. If the payloads are of the same size they
     * will be considered equal. If you need to differentiate on payload
     * then set a property that uniquely identifies the payload (for
     * example a checksum of the payload).
     */
    public synchronized boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof GPacket)) {
            return false;
        }

        GPacket obj = (GPacket)o;

        // Need to ensure packet is marshalled so size and propsByteSize
        // are correctly updated.
        try {
            marshallHeader();
            obj.marshallHeader();
        } catch (IOException e) {
            throw new RuntimeException("Can't marshall pkt", e);
        }

        if (
            timestamp       != obj.timestamp ||
            sequence        != obj.sequence ||
            size            != obj.size ||
            type            != obj.type ||
            bitFlags        != obj.bitFlags ||
            propsByteSize   != obj.propsByteSize ||
            version         != obj.version ||
            magic           != obj.magic) {

            return false;
        }

        // OK, the packets look the same. Now compare properties
        if (propsByteSize > 0) {
            // Check if marshalled properties are equal
            if (!propsBuf.equals(obj.propsBuf)) {
                return false;
            }
        }

        // We don't check payload.

        return true;
    }

    public int hashCode() {
        // for now, just compare sysid
        // this really should include property and body comparison
        return (int)(timestamp + sequence + size + type + bitFlags
               + propsByteSize + version + magic);
    }

    public synchronized String toString() {

        getProperties();
        try {
            marshallHeader();
        } catch (IOException e) {
        }

        String s =
        type + ": v=" + version + ",sz=" + size + ",mg=" + magic + 
        ",ts=" + timestamp + ",sq=" + sequence + ",prop_sz=" + propsByteSize +
        ",pay_sz=" + (size - propsByteSize - HEADER_SIZE);


        return s;
    }

    public synchronized String toLongString() {

        getProperties();
        try {
            marshallHeader();
        } catch (IOException e) {
        }

        String s =

        "    Packet: " + type + "\n" +
        "   Version: " + version +       "\t\t        Size: " + size +
            "\tMagic: " + magic+"\n"+
        " Timestamp: " + timestamp +     "\tSequence: " + sequence + 
            "\t Bits: " + bitsToString() +"\n" +
        " Prop Size: " + propsByteSize + ": " +
                            (props == null ? "null" : props.toString()) + "\n" +
        "Payload Size: " + (size - propsByteSize - HEADER_SIZE)
        ;

        return s;
    }

    protected String bitsToString() {

        String s =
            (getBit(A_BIT) ? "A" : "") +
            (getBit(B_BIT) ? "B" : "") +
            (getBit(C_BIT) ? "C" : "") +
            (getBit(D_BIT) ? "D" : "") +
            (getBit(E_BIT) ? "E" : "") +
            (getBit(F_BIT) ? "F" : "") +
            (getBit(G_BIT) ? "G" : "") +
            (getBit(H_BIT) ? "H" : "") +
            (getBit(I_BIT) ? "I" : "") +
            (getBit(J_BIT) ? "J" : "") +
            (getBit(K_BIT) ? "K" : "") +
            (getBit(L_BIT) ? "L" : "") +
            (getBit(M_BIT) ? "M" : "") +
            (getBit(N_BIT) ? "N" : "") +
            (getBit(O_BIT) ? "O" : "") +
            (getBit(P_BIT) ? "P" : "") +
            (getBit(Q_BIT) ? "Q" : "") +
            (getBit(R_BIT) ? "R" : "") +
            (getBit(S_BIT) ? "S" : "") +
            (getBit(T_BIT) ? "T" : "") +
            (getBit(U_BIT) ? "U" : "") +
            (getBit(V_BIT) ? "V" : "") +
            (getBit(W_BIT) ? "W" : "") +
            (getBit(X_BIT) ? "X" : "") +
            (getBit(Y_BIT) ? "Y" : "") +
            (getBit(Z_BIT) ? "Z" : "") +
            (getBit(a_BIT) ? "a" : "") +
            (getBit(b_BIT) ? "b" : "") +
            (getBit(c_BIT) ? "c" : "") +
            (getBit(d_BIT) ? "d" : "") +
            (getBit(e_BIT) ? "e" : "") +
            (getBit(f_BIT) ? "f" : "") 
            ;

        return s;
    }

    public static void main(String args[]) {

        try {

        GPacket outPkt1 = GPacket.getInstance();
        GPacket outPkt2 = GPacket.getInstance();

        outPkt1.putProp("name", "joe");
        outPkt1.putProp("seq", Integer.valueOf(1));
        outPkt1.setType((short)100);
        outPkt1.setBit(A_BIT, true);
        outPkt1.setBit(Z_BIT, true);
        outPkt1.setBit(a_BIT, true);
        outPkt1.setBit(f_BIT, true);

        ByteBuffer payload = ByteBuffer.allocate(128);
        payload.putChar('j');
        payload.putChar('o');
        payload.putChar('e');
        payload.limit(payload.position());
        payload.rewind();
        outPkt1.setPayload(payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        outPkt1.write(out);
        System.out.println("Out:\n" + outPkt1.toLongString());


        outPkt2.putProp("seq", Integer.valueOf(2));
        outPkt2.setType((short)104);
        outPkt2.setBit(f_BIT, true);
        outPkt2.write(out);
        System.out.println("Out:\n" + outPkt2.toLongString());


        GPacket inPkt = GPacket.getInstance();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        inPkt.read(in);
        System.out.println(" In:\n" + inPkt.toLongString());

        if (!outPkt1.equals(inPkt)) {
            throw new Exception("Packet '" + outPkt1 + "' != " +
                                "Packet '" + inPkt + "'");
        }

        inPkt.read(in);
        System.out.println(" In:\n" + inPkt.toLongString());

        if (!outPkt2.equals(inPkt)) {
            throw new Exception("Packet '" + outPkt2 + "' != " +
                                "Packet '" + inPkt + "'");
        }


        } catch (Exception e) {
            System.out.println("FAILED!");
            System.out.println("Exception: " + e);
            e.printStackTrace();

            System.exit(1);
        }
        System.out.println("PASSED");
        System.exit(1);
    }
}
