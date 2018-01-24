/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import com.sun.messaging.jmq.io.ReadWritePacket;
import com.sun.messaging.jmq.jmsclient.ConnectionHandler;
import com.sun.messaging.jmq.jmsclient.Debug;

public abstract class SocketConnectionHandler implements ConnectionHandler {
	
    //default buffer size - String for use with system property.
    private static String defaultBufferSize = "2048";
	
    private boolean debug = Debug.debug;
	
    private InputStream is = null;
    private OutputStream os = null;
    
	protected abstract void closeSocket() throws IOException ;    
	
	public boolean isDirectMode(){
		return false;
	}
	
	public ReadWritePacket readPacket () throws IOException {
		ReadWritePacket pkt = new ReadWritePacket();
		pkt.readPacket(is);
		return pkt;
	}
	
	public void writePacket (ReadWritePacket pkt) throws IOException {
		pkt.writePacket(os);
	}
	
	public void configure(Properties configuration) throws IOException {
        //for output stream
        String prop = getProperty(configuration,"imqOutputBuffer", "true");
        if (prop.equals("true")) {
            String bufsize = getProperty(configuration,"imqOutputBufferSize", defaultBufferSize);
            int outSize = Integer.parseInt(bufsize);
            os = new BufferedOutputStream (getOutputStream(), outSize);
            if (debug) {
                Debug.println("buffered output stream, buffer size: " + outSize);
            }

        } else {
            os = getOutputStream();
        }

        //for input stream
        prop = getProperty(configuration,"imqInputBuffer", "true");
        if (prop.equals("true")) {
            String bufsize = getProperty(configuration,"imqInputBufferSize",
                "2048");
            int inSize = Integer.parseInt(bufsize);
            is = new BufferedInputStream (getInputStream(), inSize);

            if (debug) {
                Debug.println("buffered input stream, buffer size: " + inSize);
            }
        } else {
            is = getInputStream();
        }
	}
	
    /**
     * Returns a configuration property.
     * Uses a System property if non-existant and a default if
     * the System property doesn't exist.
     *
     * @param propname The key with which to retreive the property value.
     * @param propdefault The default value to be returned.
     *
     * @return The property value of the property key <code>propname</code>
     *         If the key <code>propname</code> does not exist, then if a System
     *         property named <code>propname</code> exists, return that, otherwise
     *         return the value <code>propdefault</code>.
     */
    private String getProperty(Properties configuration, String propname, String propdefault) {
        String propval = (String)configuration.get(propname);
        if (propval == null) {
            propval = System.getProperty(propname) ;
        }
        return (propval == null ? propdefault : propval);
    }	
	
	public void close() throws IOException {

	    	getInputStream().close();
	    	is.close();
	    	os.close();
	    	closeSocket();
	}


}

