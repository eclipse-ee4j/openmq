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

/*
 * @(#)Protocol.java	1.15 06/29/07
 */

package com.sun.messaging.jmq.jmsserver.net;

import java.util.Map;
import java.io.IOException;
import java.nio.channels.spi.*;

/**
 * This interface class handles a specific type of protocol (e.g. tcp)
 */

public interface Protocol {
    void registerProtocolCallback(ProtocolCallback cb, Object data);

    /**
     * The canPause() method is a temporary workaround for bugid 4435336 for jmq2.0 fcs. The TCP and TLS (SSL) transports
     * always return "true". The HTTPProtocol class always returns false.
     */
    boolean canPause();

    ProtocolStreams accept() throws IOException;

    AbstractSelectableChannel getChannel() throws IOException;

    /** @throws UnsupportedOperationException */
    void configureBlocking(boolean blocking) throws IOException;

    /** @throws IllegalStateException */
    void open() throws IOException;

    /** @throws IllegalStateException */
    void close() throws IOException;

    boolean isOpen();

    /** @throws IllegalArgumentException */
    void checkParameters(Map params);

    /**
     * @return old params if param change cause rebind
     */
    Map setParameters(Map params) throws IOException;

    int getLocalPort();

    String getHostName();

    /**
     * method to set the TCP no delay flag on all sockets created if applicable
     */
    void setNoDelay(boolean val);

    /**
     * method to set the socket timeout (if any) 0 indicates no timeout
     */
    void setTimeout(int time);

    /**
     * method to set the input buffer size for a connection
     */
    void setInputBufferSize(int size);

    /**
     * method to set the output buffer size for a connection
     */
    void setOutputBufferSize(int size);

    /**
     * method to get the input buffer size for a connection
     */
    int getInputBufferSize();

    /**
     * method to get the output buffer size for a connection
     */
    int getOutputBufferSize();

    boolean getBlocking();
}
