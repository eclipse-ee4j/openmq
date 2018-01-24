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
 * @(#)StreamHandlerFactory.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

/**
 * StreamHandler factory provides a standard way to get a JMQ StreamHandler.
 */
public class StreamHandlerFactory {
     private static final String defaultProtocol =
               "com.sun.messaging.jmq.jmsclient.protocol.tcp.TCPStreamHandler";

    /**
     * Called by ProtocolHandler to get the specified StreamHandler.
     * @return StreamHandler.  If className is null, the default
     * StreamHandler - TCPStreamHandler is returned.
     */
    public static StreamHandler
    getStreamHandler (String protocolName) throws ClassNotFoundException,
                                IllegalAccessException, InstantiationException {

        String className = null;
        if ( protocolName == null ) {
            className = defaultProtocol;
        } else {
            className = protocolName;
        }

        return (StreamHandler) Class.forName (className).newInstance();
    }

}

