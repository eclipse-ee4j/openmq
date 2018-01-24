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
 * @(#)TLSStreams.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.net.tls;

import java.io.IOException;

import javax.net.ssl.*;

import com.sun.messaging.jmq.jmsserver.net.tcp.TcpStreams;

/**
 * This class handles the input and output streams to a specific connection 
 * of a protocol (e.g. with TLS/SSL this class will really be a socket and 
 * its output streams).
 *
 * <B><U>NOTE:</B></U> This class may be removed later.
 */

public class TLSStreams extends TcpStreams  {

    public TLSStreams(SSLSocket soc) 
        throws IOException
    {
        super(soc);
    }
    public TLSStreams(SSLSocket soc, int inBufSz, int outBufSz) 
        throws IOException
    {
        super(soc, true/* must be blocking */, inBufSz, outBufSz);
    }

    public String toString() {
	if (socket != null) {
	    return "SSL/TLS connection to " + socket;
	}
	else {
	    return "SSL/TLS connection to NULL";
	} 
    }

    /**
     * fix for 4809079: broker hangs at shutdown when ssl is used
     * Work around for jdk bug 4814140 (commited for tiger 1.5)
     * - Call socket.close() instead of inputStream.close() to
     *   prevent com.sun.net.ssl.internal.ssl.AppInputStream's
     *   close() and read() locking each other causing the broker
     *   to hang.
     */
    public synchronized void close() 
        throws IOException
    {
	if (socket != null) {
            socket.close();
            socket = null;
	}
    }

}
