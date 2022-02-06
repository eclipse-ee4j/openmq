/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.ums.service;

import com.sun.messaging.ums.factory.UMSConnectionFactory;
import com.sun.messaging.ums.common.Constants;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;

/**
 *
 * @author chiaming
 */
public class Authenticator {

    private Logger logger = UMSServiceImpl.logger;

    private UMSConnectionFactory connFactory = null;

    private boolean shouldAuthenticate = true;

    private boolean base64encoding = false;

    /**
     * contains authenticated user/password
     */
    // private transient Hashtable <String, String> authTable =
    // new Hashtable <String, String>();

    /**
     * contains a list of current valid clients (uuids)
     */
    // private transient Vector <String> clients = new Vector<String>();

    private SecuredSid securedSid = null;

    /**
     * The constructor is used to validate with the specified JMS provider.
     */
    public Authenticator(UMSConnectionFactory umsConnectionFactory, Properties props) throws JMSException {
        this.connFactory = umsConnectionFactory;

        String tmp = props.getProperty(Constants.JMS_AUTHENTICATE, Constants.JMS_AUTHENTICATE_DEFAULT_VALUE);

        this.shouldAuthenticate = Boolean.parseBoolean(tmp);

        tmp = props.getProperty(Constants.BASIC_AUTH_TYPE, Constants.BASIC_AUTH_TYPE_DEFAULT_VALUE);
        this.base64encoding = Boolean.parseBoolean(tmp);

        this.securedSid = new SecuredSid();
    }

    /**
     * Authenticate the provided user/password.
     *
     * It is intentional to NOT have password cache in the implementation.
     *
     * Applications MUST use the returned sid after authenticated.
     *
     * Each invokation of this generates a new sid -- as a new client has been created.
     */
    public String authenticate(String user, String password) throws JMSException {

        if (UMSServiceImpl.debug) {
            logger.info("auth user., user=" + user);
        }

        if (this.shouldAuthenticate) {

            /**
             * first check if user/pass are valid string
             */
            // if ((user == null) || (password == null) || user.isEmpty() || password.isEmpty()) {
            // throw new JMSException("User or password cannot be null or empty");
            // }

            // check with server - let JMS server check if user/pass is valid
            authenticateWithJMSServer(user, password);
        }

        if (UMSServiceImpl.debug) {
            logger.info("getting sid");
        }

        // put info to cache
        String sid = nextSid();

        if (UMSServiceImpl.debug) {
            logger.info("got., sid=" + sid);
        }

        // client is added to client table (in client pool).
        // this.clients.add(sid);

        if (UMSServiceImpl.debug) {
            logger.info("Generated sid for user., user=" + user + ",sid=" + sid);
        }

        return sid;
    }

    /**
     * This is called each time a service request (send/receive) is received.
     *
     */
    public void authenticateSid(String sid) throws JMSException {

        this.securedSid.verifySid(sid);

        // if (clients.contains(sid) == false) {
        // throw new JMSException("sid is not authenticated. Use login to get a new sid, expired/invalid sid=" + sid);
        // }
    }

    /**
     * Check with JMS server if the provided user/password is valid.
     *
     */
    private void authenticateWithJMSServer(String user, String password) throws JMSException {

        if (UMSServiceImpl.debug) {
            logger.info("Authenticating user, user = " + user);
        }

        Connection conn = null;

        try {

            if (user == null) {
                // user default user/password
                conn = this.connFactory.createConnection();
            } else {

                if (this.base64encoding) {
                    password = this.securedSid.decode(password);
                }

                conn = this.connFactory.createConnection(user, password);
            }

            if (UMSServiceImpl.debug) {
                logger.info("User authenticated, user = " + user);
            }

        } finally {

            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ex2) {
                    logger.log(Level.WARNING, ex2.getMessage(), ex2);
                }
            } else {

                if (UMSServiceImpl.debug) {
                    logger.info("Authentication failed, user = " + user);
                }

            }
        }

    }

    public String nextSid() throws JMSException {
        // return UUID.randomUUID().toString();

        return this.securedSid.nextSid();
    }

}
