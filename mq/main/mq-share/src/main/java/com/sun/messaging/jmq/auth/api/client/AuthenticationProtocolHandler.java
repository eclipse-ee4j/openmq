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
 * @(#)AuthenticationProtocolHandler.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.auth.api.client;

import java.util.Hashtable;
import javax.security.auth.login.LoginException;

/**
 * AuthenticationProtocolHandler provides the client-side API for
 * application implementor to plug-in own authentication request handler
 */
public interface AuthenticationProtocolHandler {

    /**
     * This method must return the authentication type it implements.  
     */
    public String getType();

    /**
	 * This method is called right before start a authentication process
     *
     * @param username the user name passed from createConnection()
     * @param password the password passed from createConnection()
     * @param authProperties not defined yet 
     *
     * Currently for JMQ2.0, username/password always have values (if not
     * passed in createConnection() call, they are assigned default values).
     *
     */
    public void init(String username, String password,
                     Hashtable authProperties) throws LoginException; 

    /**
     * This method is called to handle a authentication request.
     *
     * @param authRequest the authentication request data.  This is the
     *                    packet body of AUTHENTICATE_REQUEST packet.
     * @param sequence this is the sequence number field in the 
     *                 AUTHENTICATE_REQUEST packet 
     *                 (canbe used for correlation in multiple requests case)
     * @return the response data.  This will be sent as packet body in 
     *                 the AUTHENTICATE packet. 
     *                 
     * @exception LoginException any error while handle the request
     *
     */
    public byte[] handleRequest(byte[] authRequest, int sequence) 
                                            throws LoginException; 
}
