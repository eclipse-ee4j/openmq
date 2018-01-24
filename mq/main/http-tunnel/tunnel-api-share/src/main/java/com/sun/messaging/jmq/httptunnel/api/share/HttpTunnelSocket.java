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
 */ 

package com.sun.messaging.jmq.httptunnel.api.share;

import java.util.Hashtable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

public interface HttpTunnelSocket {

    public void init(String serverAddr) throws IOException; 

    public InputStream getInputStream() throws IOException; 

    public OutputStream getOutputStream() throws IOException;

    public void close() throws IOException; 

    public int getConnId();

    public InetAddress getRemoteAddress() throws UnknownHostException, SecurityException;

    public int getPullPeriod();

    public void setPullPeriod(int pullPeriod) throws IOException;

    public int getConnectionTimeout();

    public void setConnectionTimeout(int connectionTimeout) throws IOException; 

    public Hashtable getDebugState(); 
}
