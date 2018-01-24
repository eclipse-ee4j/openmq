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

package com.sun.messaging.jmq.util.service;

import java.nio.channels.SocketChannel;

/**
* <p>If an external proxy port mapper is being used to listen for connections to the port mapper, 
* then after accepting the connection it should forward the new client socket this class for handling</p>
*/
public interface PortMapperClientHandler {
	
    /**
     * <p>Process a newly-connected PortMapper client and then close the socket.</p>
     * 
     * <p>This method takes a <tt>SocketChannel</tt> and is intended to be called by an external proxy 
     * which has accepted the connection for us and created the new socket</tt>
     * 
     * @param clientSocketChannel the newly-connected PortMapper client
     */
	void handleRequest(SocketChannel clientSocketChannel) ;

}


 
