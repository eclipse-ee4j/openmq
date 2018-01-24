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
 * @(#)SysMessageID.java	1.21 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.*;
import com.sun.messaging.jmq.util.net.IPAddress;
import com.sun.messaging.jmq.util.Bits;

/**
 * This class encapsulates a system message id. This message id uniquely
 * identifies a message in the JMQ system. It consists of four values:
 * the message sequence number, source IP address, source port and timestamp.
 * Note that this is not the JMS MessageID that can be set by the client.
 * <P>
 * The Swift packet format specifies that the IP address be in IPv6 (128 bit)
 * format, so we canonicalize all addresses to IPv6. Initially we will only
 * be dealing with IPv4 addresses since that is all the JDK supports -- those
 * get translated into an "IPv4-mapped IPv6" address.
 *
 */
public class SysMessageID implements Cloneable {

    /**
     * Size of a SysMessageID when externalized.
     */
    public static final int ID_SIZE = 4 + 4 + 8 + IPAddress.IPV6_SIZE;

    public static final String ID_PREFIX             = "ID:";

    protected int  sequence;
    protected int  port;
    protected long timestamp;
    protected IPAddress ip;

    private String msgID = null;
    private boolean dirty = true;
    
    transient protected int hashcodeVal = 0;

    /**
     * Construct an unititialized system message ID. It is assumed
     * the caller will set the fields either explicitly or via
     * readID()
     */
    public SysMessageID() {
	ip = new IPAddress();
	clear();
    }

    public static void main(String args[]) throws Exception {
        SysMessageID trystr = new SysMessageID();

        System.out.println("With MAC Address");
        System.out.println("------------------------");
        // get IPAddress
        java.net.InetAddress inet = null;
        inet = java.net.InetAddress.getLocalHost();
        // set initial sequence, etc
        trystr.setIPAddress(inet.getAddress(), IPAddress.getRandomMac());
        trystr.setPort(123);
        trystr.setTimestamp(123456);
        trystr.setSequence(5);
        System.out.println("\tOriginal: " + trystr);
        SysMessageID newid = get(trystr.toString());
        System.out.println("\tDecoded: " + newid);
        System.out.println("\tHash[o,n]=[" + trystr.hashCode()
                 + "," + newid.hashCode()+"]");
        System.out.println("\tEquals: " +(trystr.equals(newid)) +
                  " , " + (newid.equals(trystr)));
        // set initial sequence, etc
        System.out.println("\n\nWithout MAC Address");
        System.out.println("------------------------");
        trystr = new SysMessageID();
        trystr.setIPAddress(inet.getAddress());
        trystr.setPort(664);
        trystr.setTimestamp(789012);
        trystr.setSequence(2);
        System.out.println("\tOriginal: " + trystr);
        newid = get(trystr.toString());
        System.out.println("\tHash[o,n]=[" + trystr.hashCode()
                 + "," + newid.hashCode()+"]");
        System.out.println("\tEquals: "+ (trystr.equals(newid)) +
                  " , " + (newid.equals(trystr)));
        System.out.println("\tDecoded: " + get(trystr.toString()));
        System.out.println("\n\nIPV6");
        System.out.println("------------------------");
        System.out.println("Testing IPV6 requires changes to OS");
/*
        trystr = new SysMessageID();
        trystr.setIPAddress(inet.getAddress());
        trystr.setPort(772);
        trystr.setTimestamp(948345);
        trystr.setSequence(22);
        System.out.println("\tOriginal: " + trystr);
        newid = get(trystr.toString());
        System.out.println("\tDecoded: " + newid);
        System.out.println("\tHash[o,n]=[" + trystr.hashCode()
                 + "," + newid.hashCode()+"]");
        System.out.println("\tEquals: "+ (trystr.equals(newid)) +
                  " , " + (newid.equals(trystr)));
        System.out.println("\tDecoded: " + get(trystr.toString()));
*/
    }

    public static SysMessageID get(String sid)
    {
        SysMessageID id = new SysMessageID();

	/*
	 * Strip out "ID:" if present
	 */
        if (sid.startsWith(ID_PREFIX))  {
            sid = sid.substring(ID_PREFIX.length());
        }

        // parse string
        String[] ss = sid.split("-");

        if (ss.length != 4) { // something went wrong
            // this is specific case of negative port number (bug 6831547)
            // if the port is negative then ss[2] is empty and the length
            // of ss array is 5 instead of 4
            if (ss.length == 5 && ss[2].length() == 0){
                ss[2] = "-" + ss[3];
                ss[3] = ss[4];
             } else {
                throw new InvalidSysMessageIDException(
                    "Bad SysMessageID ["+sid+"]");
            }
        }
        
        try {
            int sequence = Integer.parseInt(ss[0]);
            int port = Integer.parseInt(ss[2]);
            long ts = Long.parseLong(ss[3]);

            IPAddress ip = IPAddress.readFromString(ss[1]);

            id.setPort(port);
            id.setSequence(sequence);
            id.setTimestamp(ts);
            id.ip = ip;
            return id;
        } catch (NumberFormatException e) { 
            throw new InvalidSysMessageIDException(
                "Bad SysMessageID ["+sid+"]:"+e.getMessage(), e);
        }
    }


    /**
     * Clears the message id
     */
    public void clear() {
	sequence = port = 0;
	timestamp = 0;
	ip.clear();
        dirty = true;
    }

    /**
     * Check if the passed SysMessageID equals this one
     *
     * @param    o    the object to compare
     *
     * @return   true if the objects are equivalent, else false
     */
    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof SysMessageID)) {
            return false;
        }

        SysMessageID id = (SysMessageID)o;

	/*
	 * We try to optimize the comparison so if it fails it fails 
	 * quickly. Most message id comparisions will be done when 
	 * the router handles acknowledgements from a client. In that
	 * case the sequence number is the most likely to determine 
	 * uniqueness
	 */
	return (
	     this.sequence == id.sequence &&
	    this.timestamp == id.timestamp &&
	         this.port == id.port &&
	         ip.equals(id.ip)
            );
    }

    public int hashCode() {
	// This should generate enough uniqueness without messing with
	// the IP address.
    	if(hashcodeVal==0){	
    		int p = (port == 0 ? 1 : port);
    		hashcodeVal = (int)timestamp * p * sequence;
    	}
        return hashcodeVal;
    }


    /**
     * Return a string description. The string will be of the format:
     *
     *    nnn-nnn.nnn.nnn.nnn-nnnn-nnnnnnnnnnnn
     *    ^   ^               ^    ^
     *    seq IP              port timestamp
     *
     * @return    String description of message id
     *
     */
    public String toString() {

        if (msgID == null || dirty) {
            msgID = new StringBuffer(128)
                .append(sequence).append("-")
                .append(ip.toString()).append("-")
                .append(port).append("-")
                .append(timestamp)
                .toString();

            dirty = false;
        }

        return msgID;
    }

    /**
     * Generates a unique string that is suitable for use as a key.
     */
    public String getUniqueName() {
        return toString();
    }

    /**
     * Returns the unique string of msg ID prior to MQ 4.1.
     *
     * Note: should only be used for store migration
     */
    public String getUniqueNameOldFormat() {

        String uniqueName = String.valueOf(sequence) +
                            String.valueOf(timestamp) +
                            String.valueOf(port) +
                            IPAddress.rawIPToString(ip.getAddress(), false);

        return uniqueName;
    }

    public int getSequence() {
	return sequence;
    }

    public int getPort() {
	return port;
    }

    public long getTimestamp() {
	return timestamp;
    }

    /**
     * Get the IP address. 
     *
     * @return   The raw IP address. This will always be an IPv6 (128 bit)
     *           address. It will contain an embedded Mac address if one
     *           was provided to setIPAddress().
     */
    public byte[] getIPAddress() {
	return ip.getAddress();
    }

    public void setSequence(int n) {
        dirty = true;
	sequence = n;
    }

    public void setPort(int n) {
        dirty = true;
	port = n;
    }

    public void setTimestamp(long n) {
        dirty = true;
	timestamp = n;
    }

    /**
     * Set the IP address. 
     *
     * @param    newip    IP address in network byte order. This can be the
     *                    buffer returned by InetAddress.getAddress().
     *                    A null value results in the IP address being cleared.
     *
     */
    public void setIPAddress(byte[] newip)
	throws IllegalArgumentException {

	ip.setAddress(newip);
        dirty = true;
    }

    /**
     * Set the IP address and a 48 bit mac addres.
     *
     * @param    newip    IP address in network byte order. This can be the
     *                    buffer returned by InetAddress.getAddress().
     *                    A null value results in the IP address being cleared.
     *
     * @param   mac       6 byte MAC address (or random psuedo address. You
     *                    can get it from IPAddress.getRandomMac())
     *
     */
    public void setIPAddress(byte[] newip, byte[] mac)
	throws IllegalArgumentException {

	ip.setAddress(newip);

        if (mac != null && ip.getType() != IPAddress.IPV6) {
            // Only set mac if we are not IPV6
	    ip.setMac(mac);
        }
        dirty = true;
    }

    /**
     * Write the ID to the specified DataOutput.
     * The format of the written data will be:
     *
     *   <PRE>
     *    0                   1                   2                   3
     *   |0 1 2 3 4 5 6 7|8 9 0 1 2 3 4 5|6 7 8 9 0 1 2 3|4 5 6 7 8 9 0 1|
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                                                               |
     *   +                        timestamp                              +
     *   |                                                               |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                                                               |
     *   +                                                               +
     *   |                     source IP                                 |
     *   +                                                               +
     *   |                                                               |
     *   +                                                               +
     *   |                                                               |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                      source port                              |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   |                     sequence number                           |
     *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *   </PRE>
     *
     * This format matches the format specified in the ACKNOWLEDGE message
     * of the JMQ protocol spec.
     *
     * We don't use ObjectSerialization to do this because we don't want
     * the class version cruft that would be prefixed to the data.
     *
     * @param    out    DataOutput to write ID to
     */
    public void writeID(DataOutput out)
	throws IOException {

	out.writeLong(timestamp);
	ip.writeAddress(out);
	out.writeInt(port);
	out.writeInt(sequence);

        if (out instanceof DataOutputStream) {
            ((DataOutputStream)out).flush();
        }
    }

    /**
     * Returns the ID in a raw format. The format will be as described
     * in writeID, and the size of the byte[] will be ID_SIZE
     */
    public byte[] getRawID() {
        byte[] buf = new byte[ID_SIZE];

        int i = 0;
        i = Bits.put(buf, i, timestamp);
        i = Bits.put(buf, i, ip.getAddressUnsafe());
        i = Bits.put(buf, i, port);
            Bits.put(buf, i, sequence);

        return buf;
    }

    /**
     * Read the ID from the specified DataInput. The format of
     * the data is assumed to match that generated by writeID.
     *
     * @param    in    DataInput to write ID to
     *
     */
    public void readID(DataInput in)
	throws IOException {

	timestamp = in.readLong();
	            ip.readAddress(in);
	port      = in.readInt();
	sequence  = in.readInt();
    }

    /**
     * Make a deep copy of this object
     */
    public Object clone() {
	try {
	    SysMessageID newID = (SysMessageID)super.clone();
	    newID.ip           = (IPAddress)this.ip.clone();
	    return newID;
	} catch (CloneNotSupportedException e) {
	    // Should never get this, but don't fail silently
	    System.out.println("SysMessageID: Could not clone: " + e);
	    return null;
	}
    }
}
