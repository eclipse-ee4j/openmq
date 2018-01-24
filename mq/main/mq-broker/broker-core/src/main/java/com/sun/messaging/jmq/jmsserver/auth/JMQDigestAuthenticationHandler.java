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
 * @(#)JMQDigestAuthenticationHandler.java	1.21 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import java.io.*;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.MD5;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.UserRepository;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;

/**
 * "digest" authentication handler
 */
public class JMQDigestAuthenticationHandler implements AuthenticationProtocolHandler {

    private String nonce = null;
    private AccessControlContext acc = null;
    private Properties authProps;
    private Refreshable cacheData = null;
    //private boolean cacheDataMaystaled;

    public String getType() {
        return AccessController.AUTHTYPE_DIGEST;
    }

    /**
     * This method is called once before any handleReponse() calls
     *   
     * @param sequence packet sequence number
     * @param authProps authentication properties
     * @param cacheData the cacheData 
     *   
     * @return initial authentication request data if any
     */  
    public byte[] init(int sequence, Properties authProperties,
                       Refreshable cacheData) throws LoginException {
        this.authProps = authProperties;
        String clientip = authProps.getProperty(Globals.IMQ+".clientIP");
        long timestamp = System.currentTimeMillis();
        String seed = authProps.getProperty(Globals.IMQ+".connectionID");
        nonce = MD5.getHashString(clientip+":"+timestamp+seed);
        try {
        return nonce.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
        throw new LoginException(e.getMessage());
        }
    }

    /**
     * @param authResponse the authentication response data.
     *                     This is the AUTHENCATE packet body.
     * @param sequence packet sequence number
     *
     * @return next request data if any; null if no more request.
     *  The request data will be sent as packet body in AUTHENTICATE_REQUEST
     *                 
     * @exception LoginException 
     */
    public byte[] handleResponse(byte[] authResponse, int sequence) throws LoginException {
        Subject subject = null;

        ByteArrayInputStream bis = new ByteArrayInputStream(authResponse);
        DataInputStream dis = new DataInputStream(bis);
        try {
        String username = dis.readUTF();
        String credential = dis.readUTF();
        dis.close();

        String rep = authProps.getProperty(
            AccessController.PROP_AUTHENTICATION_PREFIX+ getType()+
                      AccessController.PROP_USER_REPOSITORY_SUFFIX);
        if (rep == null || rep.trim().equals("")) {
            throw new LoginException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_USER_REPOSITORY_NOT_DEFINED, getType()));
        }
        String cn = authProps.getProperty(
                    AccessController.PROP_USER_REPOSITORY_PREFIX + rep +".class");
        if (cn == null) {
            throw new LoginException(
            Globals.getBrokerResources().getKString(
            BrokerResources.X_USER_REPOSITORY_CLASS_NOT_DEFINED, rep, getType()));
        }
        UserRepository repository =  (UserRepository)Class.forName(cn).newInstance();
        repository.open(getType(), authProps, cacheData);
        subject = repository.findMatch(username, credential, nonce, getMatchType());
        cacheData = repository.getCacheData();
        repository.close();
        if (subject == null) {
            FailedLoginException ex = new FailedLoginException(
                Globals.getBrokerResources().getKString(
                    BrokerResources.X_FORBIDDEN, username));
	    ex.setUser(username);
	    throw ex;
        }

        acc = new JMQAccessControlContext(new MQUser(username), subject, authProps);
        return null;

        } catch (ClassNotFoundException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"ClassNotFoundException: "+e.getMessage()));
        } catch (IOException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"IOException: "+e.getMessage()));
        } catch (InstantiationException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"InstantiationException: "+e.getMessage()));
        } catch (IllegalAccessException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"IllegalAccessException: "+e.getMessage()));
        } catch (ClassCastException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"cLassCastException: "+e.getMessage()));
        }

    }
    
    public void logout() { }

    public AccessControlContext getAccessControlContext() {
        return acc;
    }

    public Refreshable getCacheData() {
        return cacheData;
    }

    public String getMatchType() {
        return getType();
    }
}
