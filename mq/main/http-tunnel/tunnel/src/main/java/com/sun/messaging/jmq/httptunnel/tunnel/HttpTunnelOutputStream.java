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
 * @(#)HttpTunnelOutputStream.java	1.3 06/28/07
 */ 

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.io.*;
import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

/**
 * Provides an output stream for writing data to an HTTP tunnel
 * connection.
 */
public class HttpTunnelOutputStream extends OutputStream
    implements HttpTunnelDefaults {
    private HttpTunnelConnection conn = null;

    /**
     * Creates an OutputStream for a HttpTunnelSocket.
     */
    protected HttpTunnelOutputStream(HttpTunnelConnection conn) {
        this.conn = conn;
    }

    /**
     * Writes the specified byte to this output stream. The general 
     * contract for <code>write</code> is that one byte is written 
     * to the output stream. The byte to be written is the eight 
     * low-order bits of the argument <code>b</code>. The 24 
     * high-order bits of <code>b</code> are ignored.
     * <p>
     *
     * @param      b   the <code>byte</code>.
     * @exception  IOException  if an I/O error occurs. In particular, 
     *             an <code>IOException</code> may be thrown if the 
     *             output stream has been closed.
     */
    public synchronized void write(int b) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = (byte) b;
        conn.writeData(buf);
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array 
     * starting at offset <code>off</code> to this output stream. 
     * The general contract for <code>write(b, off, len)</code> is that 
     * some of the bytes in the array <code>b</code> are written to the 
     * output stream in order; element <code>b[off]</code> is the first 
     * byte written and <code>b[off+len-1]</code> is the last byte written 
     * by this operation.
     * <p>
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs. In particular, 
     *             an <code>IOException</code> is thrown if the output 
     *             stream is closed.
     */
    public synchronized void write(byte b[], int off, int len)
        throws IOException {
        byte[] buf;

        while (len > 0) {
            if (len > MAX_PACKETSIZE)
                buf = new byte[MAX_PACKETSIZE];
            else
                buf = new byte[len];

            System.arraycopy(b, off, buf, 0, buf.length);
            conn.writeData(buf);

            off += buf.length;
            len -= buf.length;
        }
    }

    /**
     * Closes this output stream and releases any system resources associated
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
