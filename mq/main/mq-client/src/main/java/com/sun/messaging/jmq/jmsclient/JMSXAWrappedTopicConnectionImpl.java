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

package com.sun.messaging.jmq.jmsclient;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

import jakarta.jms.*;
import java.util.Vector;

import com.sun.jms.spi.xa.*;
import java.lang.System.Logger;

/**
 * An XAConnection is an active connection to a JMS provider. A client uses an XAConnection to create one or more
 * XASessions for producing and consuming messages.
 *
 * @see jakarta.jms.XAConnectionFactory
 * @see jakarta.jms.XAConnection
 */

public class JMSXAWrappedTopicConnectionImpl implements JMSXATopicConnection {

    private static final boolean debug = JMSXAWrappedConnectionFactoryImpl.debug;
    private static Logger logger = System.getLogger(JMSXAWrappedTopicConnectionImpl.class.getName());
    private Connection wrapped_connection;

    private JMSXAWrappedConnectionFactoryImpl wcf_ = null;
    private String username_ = null;
    private String password_ = null;

    private Vector sessions_ = new Vector();
    private boolean markClosed_ = false;
    private boolean closed_ = false;

    public JMSXAWrappedTopicConnectionImpl(TopicConnection tconn, JMSXAWrappedConnectionFactoryImpl wcf, String username, String password) {
        wrapped_connection = tconn;
        this.wcf_ = wcf;
        this.username_ = username;
        this.password_ = password;
    }

    /**
     * Create an XATopicSession
     *
     * @param transacted ignored.
     * @param acknowledgeMode ignored.
     *
     * @return a newly created XA topic session.
     *
     * @exception JMSException if JMS Connection fails to create a XA topic session due to some internal error.
     */
    @Override
    public JMSXATopicSession createXATopicSession(boolean transacted, int acknowledgeMode) throws JMSException {
        synchronized (sessions_) {

            if (closed_) {
                throw new jakarta.jms.IllegalStateException("JMSXWrapped Connection has been closed");
            }

            if (markClosed_) {
                throw new jakarta.jms.IllegalStateException("JMSXAWrapped Connection is closed");
            }

            JMSXATopicSession s = (new JMSXAWrappedTopicSessionImpl((TopicConnection) wrapped_connection, transacted, acknowledgeMode, this));

            if (((JMSXAWrappedTopicSessionImpl) s).delaySessionClose()) {
                sessions_.add(s);
            }

            return s;
        }
    }

    /**
     * get a TopicConnection associated with this XAConnection object.
     *
     * @return a TopicConnection.
     */
    @Override
    public TopicConnection getTopicConnection() {
        return (TopicConnection) wrapped_connection;
    }

    @Override
    public void close() throws JMSException {
        dlog("closing " + wrapped_connection + " " + wrapped_connection.getClass().getName());
        synchronized (sessions_) {
            if (sessions_.isEmpty()) {
                closed_ = true;
            } else {
                markClosed_ = true;
            }
        }
        if (closed_) {
            hardClose();
        }
    }

    private void hardClose() throws JMSException {
        dlog("hard closing " + wrapped_connection + " " + wrapped_connection.getClass().getName());
        wrapped_connection.close();
        closed_ = true;
        dlog("hard closed " + wrapped_connection + " " + wrapped_connection.getClass().getName());
    }

    protected void removeSession(JMSXAWrappedTopicSessionImpl s) {
        synchronized (sessions_) {
            sessions_.remove(s);
            if (sessions_.isEmpty() && markClosed_) {
                dlog("All sessions closed, hard close connection " + wrapped_connection + " " + wrapped_connection.getClass().getName());
                closed_ = true;
            }
        }
        if (closed_) {
            try {
                hardClose();
            } catch (JMSException e) {
                logWarning(e);
            }
        }
    }

    protected JMSXAConnectionFactory getJMSXAWrappedConnectionFactory() {
        return wcf_;
    }

    protected String getUsername() {
        return username_;
    }

    protected String getPassword() {
        return password_;
    }

    private static void dlog(String msg) {
        if (debug) {
            logger.log(DEBUG, msg);
        }
    }

    private static void logWarning(Exception e) {
        logger.log(WARNING, e.getMessage(), e);
    }

}
