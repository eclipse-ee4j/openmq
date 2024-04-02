/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import java.util.Properties;

import com.sun.messaging.jmq.io.ReadWritePacket;

/**
 * The connection handler knows how to communicate with the broker uses the specified protocol.
 *
 * The implementation of InputStream and OutputStream should be in the way that no protocol specific values are exposed
 * to the API user.
 */
public interface ConnectionHandler {

    InputStream getInputStream() throws IOException;

    OutputStream getOutputStream() throws IOException;

    int getLocalPort() throws IOException;

    void close() throws IOException;

    String getBrokerHostName();

    /**
     * Get broker address.
     *
     * @return [host,port] for TCP and SSL protocols. URL string for HTTP/HTTPS protocols.
     */
    String getBrokerAddress();

    ReadWritePacket readPacket() throws IOException;

    void writePacket(ReadWritePacket pkt) throws IOException;

    void configure(Properties configuration) throws IOException;

    boolean isDirectMode();

}
