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
 * @(#)JMQAdminKeyAuthenticationHandler.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import java.io.*;
import java.util.*;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.BASE64Decoder;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.jaas.MQAdminGroup;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.UserRepository;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * "jmqadminkey" authentication handler
 */
public final class JMQAdminKeyAuthenticationHandler implements AuthenticationProtocolHandler {

    private static boolean DEBUG = false;
    private static Logger logger = Globals.getLogger();

    private AccessControlContext acc = null;
    private Properties authProps = null;
    private static final String ADMINKEYNAME = "admin";

    public String getType() {
        return AccessController.AUTHTYPE_JMQADMINKEY;
    }

    /**
     * This method is called once before any handleResponse() calls
     *
     * @param sequence packet sequence number 
     * @param authProperties authentication properties
     * @param cacheData the cacheData 
     *
     * @return initial authentication request data if any
     */
    public byte[] init(int sequence, Properties authProperties,
                       Refreshable cacheData) throws LoginException {
       this.authProps = authProperties;
       return null;
    }

    /**
     * @param authResponse the authentication response data.
     *                     This is the AUTHENCATE_RESPONSE packet body.
     * @param sequence packet sequence number
     *
     * @return next request data if any; null if no more request.
     *  The request data will be sent as packet body in AUTHENTICATE_REQUEST
     *                 
     * @exception LoginException 
     */
    public byte[] handleResponse(byte[] authResponse, int sequence) throws LoginException {
        Subject subject = null;
        acc = null;

        if (authProps == null) {
        throw new LoginException(Globals.getBrokerResources().getKString(
                               BrokerResources.X_ILLEGAL_AUTHSTATE, getType()));
        }

        try {
        ByteArrayInputStream bis = new ByteArrayInputStream(authResponse);
        DataInputStream dis = new DataInputStream(bis);

        String username = dis.readUTF();

        BASE64Decoder decoder = new BASE64Decoder();
		String pass = dis.readUTF();
        String password = new String(decoder.decodeBuffer(pass), "UTF8");
        dis.close();

        String adminkey = authProps.getProperty(AccessController.PROP_ADMINKEY);
        if (DEBUG) {
        logger.log(Logger.DEBUG, AccessController.PROP_ADMINKEY+":"+adminkey+":"
                   +" password:"+password+":");
        }
        if (adminkey != null) {
            if (username.equals(ADMINKEYNAME) && password.equals(adminkey)) {
                final String tempUserName = username;
                subject = (Subject) java.security.AccessController.doPrivileged(
                    new PrivilegedAction<Object>() {
                        public Object run(){
                            Subject tempSubject = new Subject();
                            tempSubject.getPrincipals().add(
                                    new MQUser(tempUserName));
                            tempSubject.getPrincipals().add(
                                    new MQAdminGroup(ADMINKEYNAME));
                            return tempSubject;
                        }
                    }
                );
/*
//                subject = new Subject(); 
//                subject.getPrincipals().add(new MQUser(username));
//                subject.getPrincipals().add(new MQAdminGroup(ADMINKEYNAME));
*/
                acc = new JMQAccessControlContext(new MQUser(username), subject, authProps);
                return null;
            }
	    FailedLoginException ex = new FailedLoginException(
			Globals.getBrokerResources().getKString(
                        BrokerResources.X_FORBIDDEN, username));
	    ex.setUser(username);
	    throw ex;
        }
        throw new LoginException(Globals.getBrokerResources().getKString(
                                         BrokerResources.X_ADMINKEY_NOT_EXIST));
        } catch (IOException e) {
            throw new LoginException(Globals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"IOException: "+e.getMessage()));
        }
    }
    
    public AccessControlContext getAccessControlContext() {
        return acc;
    }

    public Refreshable getCacheData() {
        return null;
    }

    public void logout() { 
        authProps = null; 
    }

}
