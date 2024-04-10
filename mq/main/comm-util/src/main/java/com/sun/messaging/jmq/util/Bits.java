/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Bits. Some convenience methods for manipulating bits/bytes in byte arrays. Byte order is always assumed to be
 * BIGENDIAN (network byte order).
 */
public class Bits {

    /*
     * Returns the requested byte from the primitive. The bytes are numbered from least significant to most significant.
     */
    public static byte short1(short x) {
        return (byte) (x >> 8);
    }

    public static byte short0(short x) {
        return (byte) (x >> 0);
    }

    public static byte int3(int x) {
        return (byte) (x >> 24);
    }

    public static byte int2(int x) {
        return (byte) (x >> 16);
    }

    public static byte int1(int x) {
        return (byte) (x >> 8);
    }

    public static byte int0(int x) {
        return (byte) (x >> 0);
    }

    public static byte long7(long x) {
        return (byte) (x >> 56);
    }

    public static byte long6(long x) {
        return (byte) (x >> 48);
    }

    public static byte long5(long x) {
        return (byte) (x >> 40);
    }

    public static byte long4(long x) {
        return (byte) (x >> 32);
    }

    public static byte long3(long x) {
        return (byte) (x >> 24);
    }

    public static byte long2(long x) {
        return (byte) (x >> 16);
    }

    public static byte long1(long x) {
        return (byte) (x >> 8);
    }

    public static byte long0(long x) {
        return (byte) (x >> 0);
    }

    /* Make a short from 2 bytes */
    public static short makeShort(byte b1, byte b0) {
        return (short) ((b1 << 8) | (b0 & 0xff));
    }

    /* Make an int from 4 bytes */
    public static int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return ((((b3 & 0xff) << 24) | ((b2 & 0xff) << 16) | ((b1 & 0xff) << 8) | ((b0 & 0xff) << 0)));
    }

    /* make a long from 8 bytes */
    private static long makeLong(byte b7, byte b6, byte b5, byte b4, byte b3, byte b2, byte b1, byte b0) {
        return ((((long) b7 & 0xff) << 56) | (((long) b6 & 0xff) << 48) | (((long) b5 & 0xff) << 40) | (((long) b4 & 0xff) << 32) | (((long) b3 & 0xff) << 24)
                | (((long) b2 & 0xff) << 16) | (((long) b1 & 0xff) << 8) | (((long) b0 & 0xff) << 0));
    }

    /**
     * Put a short into a byte array. The short will occupy two bytes.
     *
     * @param dst Array to put value into.
     * @param index The index to copy the msb of the short into
     * @param value The value to copy into the array
     *
     * @return The index of the position immediately after the last bye of 'value'
     */
    public static int put(byte[] dst, int index, short value) {

        dst[index++] = short1(value);
        dst[index++] = short0(value);

        return index;
    }

    /**
     * Put an int into a byte array. The int will occupy four bytes.
     *
     * @param dst Array to put int into.
     * @param index The index to copy the msb of the int into
     * @param value The value to copy into the array
     *
     * @return The index of the position immediately after the last bye of 'value'
     */
    public static int put(byte[] dst, int index, int value) {

        dst[index++] = int3(value);
        dst[index++] = int2(value);
        dst[index++] = int1(value);
        dst[index++] = int0(value);

        return index;
    }

    /**
     * Put a long into a byte array. The long will occupy 8 bytes.
     *
     * @param dst Array to put long into.
     * @param index The index to copy the msb of the long into
     * @param value The value to copy into the array
     *
     * @return The index of the position immediately after the last bye of 'value'
     */
    public static int put(byte[] dst, int index, long value) {

        dst[index++] = long7(value);
        dst[index++] = long6(value);
        dst[index++] = long5(value);
        dst[index++] = long4(value);
        dst[index++] = long3(value);
        dst[index++] = long2(value);
        dst[index++] = long1(value);
        dst[index++] = long0(value);

        return index;
    }

    /**
     * Put the contents of one byte[] into another. This is just a convenience routine on System.arrayCopy
     *
     * @param dst Array to put value into.
     * @param index The index to copy the first element into
     * @param value The array to copy into dst
     *
     * @return The index of the position immediately after the last bye of 'value'
     *
     */
    public static int put(byte[] dst, int index, byte[] value) {
        System.arraycopy(value, 0, dst, index, value.length);
        return index + value.length;
    }

    /**
     * Get a short from a byte array starting at an index. The byte array is assumed to hold bytes in network byte order
     * (BIGENDIAN). It is up to the caller to advance the index by the size of a short.
     *
     * @param buf Buffer to get short from
     * @return A short created from the next two bytes in 'buf'.
     */
    public static short getShort(byte[] buf, int index) {
        return makeShort(buf[index + 0], buf[index + 1]);
    }

    /**
     * Get an int from a byte array starting at an index. The byte array is assumed to hold bytes in network byte order
     * (BIGENDIAN). It is up to the caller to advance the index by the size of an int.
     *
     * @param buf Buffer to get int from
     * @return A int created from the next four bytes in 'buf'.
     */
    public static int getInt(byte[] buf, int index) {
        return makeInt(buf[index + 0], buf[index + 1], buf[index + 2], buf[index + 3]);
    }

    /**
     * Get a long from a byte array starting at an index. The byte array is assumed to hold bytes in network byte order
     * (BIGENDIAN). It is up to the caller to advance the index by the size of a long.
     *
     * @param buf Buffer to get long from
     * @return A long created from the next eight bytes in 'buf'.
     */
    public static long getLong(byte[] buf, int index) {
        return makeLong(buf[index + 0], buf[index + 1], buf[index + 2], buf[index + 3], buf[index + 4], buf[index + 5], buf[index + 6], buf[index + 7]);
    }

    /**
     * Get a buffer of random bits. We don't use java.util.Random because it is not random enough. We don't use
     * java.security.SecureRandom() because it is expensive. Instead we concatenate a couple random seeds into a buffer, run
     * MD5 on the buffer, then use 6 bytes from the MD5 buffer. This was inspired by an algorithm given in:
     *
     * http://www-old.ics.uci.edu/pub/ietf/webdav/uuid-guid/draft-leach-uuids-guids-01.txt If that URL is bad just search
     * the web for "draft-leach-uuids-guids" and you'll find it.
     *
     * @param nBytes Number of random bytes needed
     * @return A byte array of random bytes
     */
    public static byte[] randomBits(int nBytes) {

        // Get a few "random" longs
        long[] seeds = new long[4];
        seeds[0] = System.currentTimeMillis();
        seeds[1] = (new Object()).hashCode();
        seeds[2] = Runtime.getRuntime().freeMemory();
        seeds[3] = Runtime.getRuntime().totalMemory();

        // Concatenate longs into a buffer
        byte[] seedbuf = new byte[seeds.length * 8];
        int i = 0;
        for (int n = 0; n < seeds.length; n++) {
            i = Bits.put(seedbuf, i, seeds[n]);
        }

        // Run MD5 on the seed buffer.
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            return null;
        }

        // Pull bytes from digest.
        byte[] digest = md.digest(seedbuf);
        byte[] output = new byte[nBytes];
        int n;
        for (n = 0; n < output.length && n < digest.length; n++) { //NOPMD
            output[n] = digest[n];
        }

        // If we need more bytes just use Random
        if (n < output.length) {
            Random r = new Random();
            for (; n < output.length; n++) {
                output[n] = (byte) (r.nextInt(Byte.MAX_VALUE));
            }
        }
        return output;
    }

    /**
     * Takes a buffer of bytes and returns a hex string representation of that buffer where every two bytes is represented
     * as a hex number. The buffer must be an even number of bytes.
     */
    public static String toHexString(byte[] buf, boolean useDelim) {

        // Buffer must be an even length
        if (buf.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        int value;
        StringBuilder sb = new StringBuilder(3 * buf.length);

        /*
         * Assume buf is in network byte order (most significant byte is buf[0]). Convert two byte pairs to a short, then
         * display as a hex string.
         */
        int n = 0;
        while (n < buf.length) {
            value = buf[n + 1] & 0xFF; // Lower byte
            value |= (buf[n] << 8) & 0xFF00; // Upper byte
            sb.append(Integer.toHexString(value));
            n += 2;
            if (n < buf.length && useDelim) {
                sb.append(':');
            }
        }
        return sb.toString();
    }
}
