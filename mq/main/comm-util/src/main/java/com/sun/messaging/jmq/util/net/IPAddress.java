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
 * @(#)IPAddress.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.util.net;

import java.io.*;
import java.util.Arrays;
import java.util.regex.*;
import java.net.InetAddress;

import com.sun.messaging.jmq.util.Bits;

/**
 * This class encapsulates an IP address which may be either IPv4 (32 bit) or
 * IPv6 (128 bit). The JDK does not yet support IPv6 addresses, but
 * the Swift packet format uses 128 bit addresses (in preparation for
 * IPv6) and therefore we need this class. Internally everything is kept
 * as an IPv6 address. If an IPv4 address is set (which will be the typical
 * case until IPv6 is adopted), then it is converted to an IPv4-mapped IPv6
 * address. See RFC 2373 for more info on IPv6.
 *
 * Additionally we have added a third format called IPv4+MAC. In iMQ IP
 * addresses are simply used as a unique identifier for clients. Since
 * an IPv4 address is not suitably unique (for example when NAT is used)
 * we add the ability to embedded a 40 bit MAC address (or psuedo MAC
 * address) along with an IPv4 address in the 128 bits an IPv6 address
 * gives us. The format is:
 *
 * +--------+--------+--------+--------+
 * |11111111|00000000|00000000|00000000|
 * +--------+--------+--------+--------+
 * |       48 bit MAC address  . . .   
 * +--------+--------+--------+--------+
 *       . . .       |11111111|11111111|
 * +--------+--------+--------+--------+
 * |      32 bit IPv4 address          |
 * +--------+--------+--------+--------+
 *
 * The leading 11111111 is used in IPv6 to signify an IPv6 multicast address.
 * Since a client will never be assigned a multicast address we can use
 * this prefix to indicate an IPv4+MAC address.
 */
public class IPAddress implements Cloneable, Serializable {

    /**
     * Size of an IPv4 address in bytes
     */
    public static final int IPV4_SIZE = 4;

    /**
     * Size of an IPv6 address in bytes
     */
    public static final int IPV6_SIZE = 16;

    // Address types
    public static final int UNKNOWN = 0;
    public static final int IPV4 = 1;       // IPv4-mapped IPv6
    public static final int IPV6 = 2;       // IPv6 unicast
    public static final int IPV4MAC = 3;    // iMQ IPV4 + MAC

    // Format of address. One of above types
    protected int type = UNKNOWN;

    // Prefix used to create an IPv4-mapped IPv6 address
    static private byte[] prefix =
	{ 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
	  (byte)0xFF, (byte)0xFF };

    // Prefix used to identify the iMQ IPv4+MAC format
    static private byte[] IPv4MacPrefix =
	{ (byte)0xFF, 0x0, 0x0, 0x0 };

    // A null IPv6 address
    static private byte[] nullAddr =
	{ 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
	  (byte)0xFF, (byte)0xFF,
          0x0, 0x0, 0x0, 0x0 };

    // 128 bit buffer to hold IP address. We always store address as IPv6
    protected byte ip[] = new byte[IPV6_SIZE];


    /**
     * Construct an unititialized IP address
     */
    public IPAddress() {
	clear();
    }

    /**
     * Clear the IP address.
     */
    public void clear() {
	System.arraycopy(nullAddr, 0, ip, 0, nullAddr.length);
        type = IPV4;
    }

    /**
     * Get the type of address we are encapsulating
     */
    public int getType() {
        if (type == UNKNOWN) {
            if (isIPv4Mapped(ip)) {
                type = IPV4;
            } else if (isIPv4Mac(ip)) {
                type = IPV4MAC;
            } else {
                type = IPV6;
            }
        }
        return type;
    }

    /**
     * Make a deep copy of the IP address
     */
    public Object clone() {
	IPAddress newIP = null;
	try {
	    newIP    = (IPAddress)super.clone();
	    newIP.ip = (byte[])(this.ip.clone());
	} catch (CloneNotSupportedException e) {
	    // this should never happen since we are Cloneable and so
	    // are arrays. We print a message just in case.
            System.out.println("IPAddress: Could not clone: " + e);
	}
	return newIP;
    }

    public boolean equals(Object obj) {
	if (obj instanceof IPAddress) {
	    return isEqual(ip, ((IPAddress)obj).ip);
        } else {
	    return false;
        }
    }

    public int hashCode() {
        if (ip == null) return 0;
        return Arrays.hashCode(ip);
    }

    /**
     * Check if the IP address is 0
     *
     * @return   true if IP address is 0
     *           false if IP address is not 0
     */
    public static boolean isNull(byte[] addr) {

	if (addr == null || isEqual(addr, nullAddr))
	    return true;
        else
	    return false;
    }

    /**
     * Check if two raw IP addresses are equal. Handles
     * either or both buffers being null. If they are both
     * null they are considered equal.
     *
     * @param    ip1    Raw IP address to compare
     * @param    ip2    Raw IP address to compare
     */
    private static boolean isEqual(byte[] ip1, byte[] ip2) {
	if (ip1 == ip2) {
	    // Same buffer
	    return true;
        } else if (ip1 == null || ip2 == null) {
	    // One null buffer
	    return false;
	} else if (ip1.length == ip2.length) {
	    // Different buffers, same length, check content.
	    // We start at the end since that is most likely to be different
	    for (int n = ip1.length - 1; n >= 0; n--) {
		if (ip1[n] != ip2[n]) {
		    return false;
		}
            }

	    // Content matches
	    return true;

	} else {
	    // Different lengths
	    return false;
        }
    }

    /**
     * Get the IP address
     *
     * @return   The raw IP address. This will always be an IPv6
     *           (128 bit) address. It is a copy
     */
    public byte[] getAddress() {
	return (byte[])ip.clone();
    }

    /**
     * Get the IP address
     *
     * @return   The raw IP address. This will always be an IPv6
     *           (128 bit) address. It is not a copy. Caller should
     *           treat the buffer as read-only.
     */
    public byte[] getAddressUnsafe() {
	return ip;
    }

    /**
     * Get the IP address as an IPv4 address. 
     *
     * @return   The raw IP address if the current address is an IPv4
     *           address (or an IPv4-mapped IPv6) address.
     *
     * @throws   IllegalArgumentException if the current address is
     *           not an IPv4 address.
     */
    public byte[] getIPv4Address()
	throws IllegalArgumentException {

	if (getType() != IPV4 && getType() != IPV4MAC) {
	    throw new IllegalArgumentException("Address is not an IPv4 or IPv4-mapped IPv6 address");
	}

	byte[] buf = new byte[4];
	// Copy lower four bytes into return buffer
	System.arraycopy(ip, prefix.length, buf, 0, buf.length);

	return buf;
    }

    /**
     * Set a 48 bit MAC address into an iPv4-mapped IPv6 address.
     */
     public void setMac(byte[] mac) {
	if (getType() != IPV4 && getType() != IPV4MAC) {
	    throw new IllegalArgumentException("Address is not an IPv4 or IPv4-mapped IPv6 address");
	}

        if (mac.length != 6) {
            throw new IllegalArgumentException("Invalid mac length: " + mac.length);
        }

        // Set initial 8 bits to 1 to indicate IPv4+Mac address
        ip[0] = (byte)0xFF;

        // Copy Mac address into bytes 4-9
        for (int n = 0; n < mac.length; n++) {
            ip[n + 4] = mac[n];
        }

        type = IPV4MAC;
     }

    /**
     * Set a 48 bit MAC address into an iPv4-mapped IPv6 address.
     * This must be called AFTER an IPv4 address is set.
     */
     public byte[] getMac() {
	if (getType() != IPV4MAC) {
	    throw new IllegalArgumentException();
	}

        byte[] mac = new byte[6];

        // Copy Mac address into bytes 4-9
        for (int n = 0; n < mac.length; n++) {
            mac[n] = ip[n + 4];
        }
        return mac;
     }

    /**
     * Get the IP address as an IPv4 integer address. 
     *
     * @return   The integer IP address if the current address is an IPv4
     *           address or an IPv4-mapped IPv6 address.
     *
     * @throws   IllegalArgumentException if the current address is
     *           not an IPv4 address.
     */
    public int getIPv4AddressAsInt()
	throws IllegalArgumentException {

	if (getType() != IPV4 && getType() != IPV4MAC) {
	    throw new IllegalArgumentException("Address is not an IPv4 or IPv4-mapped IPv6 address");
	}

	int address;

	// Generate address from last 4 bytes of address buffer
	address  =   ip[15]        & 0xFF;
        address |= ((ip[14] <<  8) & 0xFF00);
        address |= ((ip[13] << 16) & 0xFF0000);
        address |= ((ip[12] << 24) & 0xFF000000);

	return address;
    }

    /**
     * Set the IP address. 
     *
     * @param    newip    IP address in network byte order. This can be
     *                    either a 32 bit IPv4 address or a 128 bit IPv6
     *                    address. Typically you'd use the buffer
     *                    returned by InetAddress.getAddress().
     *                    If newip is null the IP address will be cleared.
     *
     */
    public void setAddress(byte[] newip)
	throws IllegalArgumentException {

        type = UNKNOWN;
	if (newip == null) {
	    // Clear IP
	    clear();
	} else  if (newip.length == IPV4_SIZE) {
	    // IPv4 address. Map to IPv6
	    System.arraycopy(prefix, 0, ip, 0,             prefix.length);
	    System.arraycopy( newip, 0, ip, prefix.length, newip.length);
            type = IPV4;
	} else if (newip.length == IPV6_SIZE) {
	    // IPv6 address. Use it.
	    System.arraycopy(newip, 0, ip, 0, newip.length);
            type = IPV6;
	} else {
	    throw new IllegalArgumentException("Bad IP address length: " +
		newip.length + ". Should be " + IPV4_SIZE + " or " +
		IPV6_SIZE + " bytes");
	}
    }


    /**
     * Write the raw IP address to the specified DataOutput.
     * The address will be written as a 128 bit IPv6
     * or IPv4-mapped IPv6 address
     *
     * @param    out    DataOutput to write address to
     */
    public void writeAddress(DataOutput out)
	throws IOException {
	out.write(ip);
    }

    /**
     * Read the raw IP address from the specified DataInput. The
     * IP address is assumed to a 128 bit IPv6 address or IPv4-mapped
     * IPv6 address (i.e. we are going to read 128 bits)
     *
     * @param    in    DataInput to read address from
     */
    public void readAddress(DataInput in)
	throws IOException {
        type = UNKNOWN;
        in.readFully(ip);
    }

    /**
     * 
     * Check if the passed address is an IPv4-mapped IPv6 address.
     * <BR>
     * IPv6 defines an address that holds an embedded IPv4 address.
     * This is called an "IPv4-mapped IPv6 address". It is specified
     * by a prefix of 80 0 bits and 16 1 bits. The last 32 bits
     * are the IPv4 address. This method checks if the passed raw
     * IP address is an IPv4-mapped address.
     *
     * @param  addr    Raw IPv6 address (128 bits)
     *
     * @return true    If addr is an IPv4-mapped IPv6 address
     * 
     */
    public static boolean isIPv4Mapped(byte[] addr) {
        if (addr.length == IPV6_SIZE) {
	    // Check if prefixed by IPv4-mapped prefix
	    for (int n = 0; n < prefix.length; n++) {
		if (addr[n] != prefix[n]) {
		    return false;
                }
	    }
	    return true;
	} else {
	    return false;
        }
    }

    /**
     * 
     * Check if the passed address is an iMQ IPv4+MAC address.
     * <BR>
     * iMQ defines an alternate format used to make the IPv4 address
     * more unique. We basically bake a 40 bit MAC address (or psuedo
     * MAC address) into the upper portion of the 128 bits. The format
     * is 8 1 bits followed by 24 0 bits followed by the 48 bit MAC address
     * followed by 16 1 bits followed by the 32 bit IPv4 address.
     *
     * @param  addr    Raw IPv6 address (128 bits)
     *
     * @return true    If addr is an iMQ IPv4+MAC address
     * 
     */
    public static boolean isIPv4Mac(byte[] addr) {
        if (addr.length == IPV6_SIZE) {
	    // Check if prefixed by IPv4-mapped prefix
	    for (int n = 0; n < IPv4MacPrefix.length; n++) {
		if (addr[n] != IPv4MacPrefix[n]) {
		    return false;
                }
	    }
	    return true;
	} else {
	    return false;
        }
    }

    public String toString() {
	return rawIPToString(ip, true, false);
    }

    /**
     * Converts a raw IP address to a readable String.
     */
    public static String rawIPToString(byte[] addr) {
	return rawIPToString(addr, true, false);
    }

    /**
     * Converts a raw IP address to a readable String.
     */
    public static String rawIPToString(byte[] addr, boolean useDelim) {
	return rawIPToString(addr, useDelim, false);
    }


    /**
     * parses a rawIPToString back into an IP
     * string MUST have been written with a delim.
     */
    public static IPAddress readFromString(String str)
    {
/*
     *             "%d.%d.%d.%d".<br>
     *             If IPv6 address then it will be in colon seperated Hex:
     *             "%x:%x:%x:%x:%x:%x:%x:%x"<br>
     *             If iMQ IPv4+MAC it will be the IPv4 address followed
     *             by the mac address
     *             "%d.%d.%d.%d(%x:%x:%x:%x:%x:%x)"
*/
        // OK - I should really do this with a regex
        int indx = str.indexOf("(");
        String macAddr = null;
        byte addr[] = null;
        if (indx != -1) {
            String ips=str.substring(0,indx);
            macAddr=str.substring(indx+1, str.length() -1);
            String ip4[]=ips.split("\\.");
            addr = new byte[4];

            for (int i=0; i < 4; i++) {
                addr[i] = Integer.valueOf(ip4[i]).byteValue();
            }
        } else {
            indx = str.indexOf(":");
            if (indx != -1) {
                String ip6[]=str.split(":");
                addr = new byte[16];
                int val = 0;
                for (int i=0, j=0; i < 8; i++) {
                    val = Integer.parseInt(ip6[i], 16);
                    addr[j++] = (byte)((val>>8) & 0xFF);
                    addr[j++] = (byte)(val & 0xFF);
                }
            } else {
                // IPv4
                String ip4[]=str.split("\\.");

                addr = new byte[4];

                for (int i=0; i < 4; i++)
                    addr[i] = Integer.valueOf(ip4[i]).byteValue();
            }
        }

        IPAddress ip = new IPAddress();
        ip.setAddress(addr);

        if (macAddr != null) {
            String macbyte[]=macAddr.split("\\:");
            byte[] mac = new byte[macbyte.length];
            for (int i=0; i < macbyte.length; i++)
                mac[i] = Integer.valueOf(macbyte[i].toUpperCase(),16).byteValue();
            ip.setMac(mac);
        }
        return ip;
    }

    /**
     * Converts a raw IP address to a String.
     *
     * @param      addr    Raw IP address in network byte order.
     *                     May be either a 4 byte IPv4 address or a
     *                     16 byte IPv6 address.
     * @param      useDelim By default the returned string will
     *                      use delimeters (. or :) to make the address
     *                      more readable. If "useDelim" is false then
     *                      the delimiters are not used which makes for
     *                      a more compact string.
     * @param      hideMac  True to NOT include the Mac portion of
     *                      an IPV4+MAC address in the returned string.
     *                      Otherwise the MAc address is included.
     *
     * @return     The string representation of the address. If IPv4
     *             address or IPv4-mapped IPv6 address then it will
     *             be in dotted-decimal format:
     *             "%d.%d.%d.%d".<br>
     *             If IPv6 address then it will be in colon seperated Hex:
     *             "%x:%x:%x:%x:%x:%x:%x:%x"<br>
     *             If iMQ IPv4+MAC it will be the IPv4 address followed
     *             by the mac address
     *             "%d.%d.%d.%d(%x:%x:%x:%x:%x:%x)"
     *	<br>	  
     *             Will return null if addr buf is not 4 or 16 bytes.
     */
    public static String rawIPToString(byte[] addr, boolean useDelim,
                                        boolean hideMac) {

	String DOT = ".";
	String COLON = ":";
	String DASH = "-";
	String OPENP = "(";
	String CLOSEP = ")";

	if (!useDelim) {
	    DOT = "";
	    COLON = "";
	    DASH = "";
            OPENP = "";
            CLOSEP = "";
        }

	/* We mask with 0xFF to mask the extension of the sign bit that
	 * would occur when the byte is promoted to an int before conversion
	 * to a string
	 */
	if (addr == null) {
	    return "0.0.0.0";
        } else if (addr.length == IPV4_SIZE) {
	    // IPv4 address. Use dotted decimal
            return ((addr[0] & 0xFF)) + DOT +
		   ((addr[1] & 0xFF)) + DOT +
		   ((addr[2] & 0xFF)) + DOT +
		   ((addr[3] & 0xFF));
	} else if (isIPv4Mapped(addr) || (hideMac && isIPv4Mac(addr))) {
	    // IPv4-mapped IPv6 address. Use dotted decimal on lower 4 bytes
            return ((addr[12] & 0xFF)) + DOT +
		   ((addr[13] & 0xFF)) + DOT +
		   ((addr[14] & 0xFF)) + DOT +
		   ((addr[15] & 0xFF));
        } else if (isIPv4Mac(addr)) {
            // Colon hex for the Mac address, dotted decimal for IPv4
            return ((addr[12] & 0xFF)) + DOT +
		   ((addr[13] & 0xFF)) + DOT +
		   ((addr[14] & 0xFF)) + DOT +
		   ((addr[15] & 0xFF)) + OPENP +
                   Integer.toHexString((addr[4] & 0xFF)) + COLON +
                   Integer.toHexString((addr[5] & 0xFF)) + COLON +
                   Integer.toHexString((addr[6] & 0xFF)) + COLON +
                   Integer.toHexString((addr[7] & 0xFF)) + COLON +
                   Integer.toHexString((addr[8] & 0xFF)) + COLON +
                   Integer.toHexString((addr[9] & 0xFF)) + CLOSEP;
	} else if (addr.length == IPV6_SIZE) {
	    // IPv6 address. Use colon seperated hex
            return Bits.toHexString(addr, useDelim);
	} else {
	    return null;
        }
    }

    /**
     * Generate a 40 bit psuedo MAC node address for the node we are
     * running on. The JDK provides no way to get the hardware MAC address
     * for the system we are running on (see JDK RFE 
     * so we generate a random one.
     *
     */
    public static byte[] getRandomMac() {

        byte[] nodebuf = Bits.randomBits(6);

        // Make sure the upper most bit is on. This is the multicast
        // bit on MAC addresses and ensures no clash with true hardware
        // addresses.
        nodebuf[0] = (byte)(nodebuf[0] | (byte)0x80);

        return nodebuf;
    }

    public static void main (String args[]) {
        System.out.println(Bits.toHexString(getRandomMac(), true));
        System.out.println(Bits.toHexString(getRandomMac(), true));

        InetAddress inet = null;
        
        try {
            inet = InetAddress.getLocalHost();
        } catch (Exception e) {
        }
        IPAddress ip = new IPAddress();
        ip.setAddress(inet.getAddress());
        System.out.println("IPv4: " + ip);

        ip.setMac(getRandomMac());

        System.out.println("IPv4Mac: " + ip);
    }
}
