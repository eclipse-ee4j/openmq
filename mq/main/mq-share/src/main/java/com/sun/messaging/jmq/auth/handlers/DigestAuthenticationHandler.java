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
 * @(#)DigestAuthenticationHandler.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.auth.handlers;

import java.io.*;
import java.util.Hashtable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.client.*;
import com.sun.messaging.jmq.util.MD5;

/**
 * MQ 'digest' authentication request handler
 */

public class DigestAuthenticationHandler implements AuthenticationProtocolHandler {

    private String username = null;
    private String password = null;

    public String getType() {
        return "digest";
    }

    /**
     * This method is called right before start a authentication process
     * Currently for JMQ2.0, username/password always have values (if not
     * passed in createConnection() call, they are assigned default values).
     */
    public void init(String username, String password,
                     Hashtable authProperties) throws LoginException {
        this.username = username;
        this.password = password;
    }


    public byte[] handleRequest(byte[] authRequest, int sequence) 
                                throws LoginException {
        if (username == null || password == null) {
            throw new LoginException("null");
        }
        try {

        byte[] response;
        byte[] nonce = authRequest;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
		dos.writeUTF(username);
        String userpwd = MD5.getHashString(username + ":" + password);
        try {
        String credential = MD5.getHashString(userpwd + ":" + new String(nonce, "UTF8"));
        dos.writeUTF(credential);
        } catch (UnsupportedEncodingException e) {
        throw new IOException(e.getMessage());
        }
        dos.flush();
        response = bos.toByteArray();
        dos.close();
        return response;

        } catch (IOException e) {
        throw new LoginException("IOException: "+e.getMessage());
        }
    }
}
