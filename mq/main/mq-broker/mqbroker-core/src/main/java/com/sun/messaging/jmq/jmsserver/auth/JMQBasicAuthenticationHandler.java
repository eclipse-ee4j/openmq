/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.auth;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.BASE64Decoder;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.UserRepository;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;

/**
 * "basic" authentication handler
 */
public class JMQBasicAuthenticationHandler implements AuthenticationProtocolHandler {

    private AccessControlContext acc = null;
    private Properties authProps;
    private Refreshable cacheData = null;
    private UserRepository repository = null;
    private boolean logout = false;

    @Override
    public String getType() {
        return AccessController.AUTHTYPE_BASIC;
    }

    /**
     * This method is called once before any handleReponse() calls
     *
     * @param sequence packet sequence number
     * @param authProperties authentication properties
     * @param cacheData the cacheData
     *
     * @return initial authentication request data if any
     */
    @Override
    public byte[] init(int sequence, Properties authProperties, Refreshable cacheData) throws LoginException {
        this.authProps = authProperties;
        this.cacheData = cacheData;
        return null;
    }

    /**
     * @param authResponse the authentication response data. This is the AUTHENCATE_RESPONSE packet body.
     * @param sequence packet sequence number
     *
     * @return next request data if any; null if no more request. The request data will be sent as packet body in
     * AUTHENTICATE_REQUEST
     */
    @Override
    public synchronized byte[] handleResponse(byte[] authResponse, int sequence) throws LoginException {
        if (repository == null && logout) {
            throw new LoginException(Globals.getBrokerResources().getKString(BrokerResources.X_CONNECTION_LOGGEDOUT));
        }
        if (repository != null) {
            repository.close();
        }

        Subject subject = null;
        acc = null;

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(authResponse);
            DataInputStream dis = new DataInputStream(bis);

            String username = dis.readUTF();

            BASE64Decoder decoder = new BASE64Decoder();
            String pass = dis.readUTF();
            String password = new String(decoder.decodeBuffer(pass), "UTF8");
            dis.close();

            String rep = authProps.getProperty(AccessController.PROP_AUTHENTICATION_PREFIX + getType() + AccessController.PROP_USER_REPOSITORY_SUFFIX);
            if (rep == null || rep.trim().equals("")) {
                throw new LoginException(Globals.getBrokerResources().getKString(BrokerResources.X_USER_REPOSITORY_NOT_DEFINED, getType()));
            }
            String className = authProps.getProperty(AccessController.PROP_USER_REPOSITORY_PREFIX + rep + ".class");
            if (className == null) {
                throw new LoginException(Globals.getBrokerResources().getKString(BrokerResources.X_USER_REPOSITORY_CLASS_NOT_DEFINED, rep, getType()));
            }
            repository = (UserRepository) Class.forName(className).getDeclaredConstructor().newInstance();
            repository.open(getType(), authProps, cacheData);
            subject = repository.findMatch(username, password, null, getMatchType());
            cacheData = repository.getCacheData();
            if (subject == null) {
                FailedLoginException ex = new FailedLoginException(Globals.getBrokerResources().getKString(BrokerResources.X_FORBIDDEN, username));
                ex.setUser(username);
                throw ex;
            }

            acc = new JMQAccessControlContext(new MQUser(username), subject, authProps);
            return null;

        } catch (ClassNotFoundException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "ClassNotFoundException: " + e.getMessage()));
        } catch (IOException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "IOException: " + e.getMessage()));
        } catch (InstantiationException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "InstantiationException: " + e.getMessage()));
        } catch (IllegalAccessException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "IllegalAccessException: " + e.getMessage()));
        } catch (ClassCastException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "ClassCastException: " + e.getMessage()));
        } catch (NoSuchMethodException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "NoSuchMethodException: " + e.getMessage()));
        } catch (InvocationTargetException e) {
            throw new LoginException(Globals.getBrokerResources().getString(BrokerResources.X_INTERNAL_EXCEPTION, "InvocationTargetException: " + e.getMessage()));
        }
    }

    @Override
    public AccessControlContext getAccessControlContext() {
        return acc;
    }

    @Override
    public Refreshable getCacheData() {
        return cacheData;
    }

    @Override
    public synchronized void logout() throws LoginException {
        logout = true;
        if (repository != null) {
            repository.close();
        }
    }

    public String getMatchType() {
        return getType();
    }
}
