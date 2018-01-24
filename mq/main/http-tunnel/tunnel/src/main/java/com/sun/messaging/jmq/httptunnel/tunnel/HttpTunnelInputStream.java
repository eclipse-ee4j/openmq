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
 * @(#)HttpTunnelInputStream.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.io.*;

/**
 * Provides an input stream for reading data from an HTTP tunnel
 * connection.
 */
public class HttpTunnelInputStream extends InputStream {
    private HttpTunnelConnection conn = null;
    private byte[] singlebyte = new byte[1];

    /**
     * Creates an InputStream for a HttpTunnelSocket.
     */
    protected HttpTunnelInputStream(HttpTunnelConnection conn) {
        this.conn = conn;
    }

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized int read() throws IOException {
        int n = conn.readData(singlebyte);
        if (n == 0)
            return -1;

        return (((int) singlebyte[0]) & 0xff);
    }

    /**
     * Reads up to <code>len</code> bytes of data from the input stream into
     * an array of bytes.  An attempt is made to read as many as
     * <code>len</code> bytes, but a smaller number may be read, possibly
     * zero. The number of bytes actually read is returned as an integer.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array <code>b</code>
     *                   at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized int read(byte b[], int off, int len)
        throws IOException {
        int n = conn.readData(b, off, len);
        if (n == 0)
            return -1;

        return n;
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from this input
     * stream. The <code>skip</code> method may, for a variety of reasons, end
     * up skipping over some smaller number of bytes, possibly <code>0</code>.
     * This may result from any of a number of conditions; reaching end of file
     * before <code>n</code> bytes have been skipped is only one possibility.
     * The actual number of bytes skipped is returned.  If <code>n</code> is
     * negative, no bytes are skipped.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized long skip(long n) throws IOException {
        int skipped = 0;
        int ret;

        while (skipped < n) {
            try {
                ret = conn.readData(null, 0, (int) n - skipped);
            }
            catch (IOException e) {
                if (skipped == 0)
                    throw e;
                else
                    break;
            }
            if (ret == 0)
                break;

            skipped += ret;
        }

        return skipped;
    }

    /**
     * Returns the number of bytes that can be read (or skipped over) from
     * this input stream without blocking by the next caller of a method for
     * this input stream.  The next caller might be the same thread or or
     * another thread.
     *
     * @return     the number of bytes that can be read from this input stream
     *             without blocking.
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized int available() throws IOException {
        return conn.available();
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized void close() throws IOException {
    }
}

/*
 * EOF
 */
