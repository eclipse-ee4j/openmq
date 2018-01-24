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

package com.sun.messaging.bridge.service.jms;

import javax.jms.*;

/**
 *
 * @author amyk
 */
public class PooledConnectionImpl extends PooledConnection implements Connection {
    
    public PooledConnectionImpl(Connection conn) {
        super(conn);
    }

    public Session
    createSession(boolean transacted,
                  int acknowledgeMode) throws JMSException {
    return _conn.createSession(transacted, acknowledgeMode);
    }
    
	@Override
	public Session createSession(int sessionMode) throws JMSException {
		return _conn.createSession(sessionMode);
	}

	@Override
	public Session createSession() throws JMSException {
		return _conn.createSession();
	}

    public String
    getClientID() throws JMSException {
    return _conn.getClientID();
    }

    public void
    setClientID(String clientID) throws JMSException {
    _conn.setClientID(clientID);
    }

    public ConnectionMetaData
    getMetaData() throws JMSException {
    return _conn.getMetaData();
    }

    public ExceptionListener 
    getExceptionListener() throws JMSException {
    return _conn.getExceptionListener();
    }

    public void 
    setExceptionListener(ExceptionListener listener) throws JMSException {
    _conn.setExceptionListener(listener);
    }

    public void
    start() throws JMSException {
    _conn.start();
    }

    public void
    stop() throws JMSException {
    _conn.stop();
    }

    public void 
    close() throws JMSException {
    _conn.close();
    }
    
    public ConnectionConsumer
    createConnectionConsumer(Destination destination,
                             String messageSelector,
                             ServerSessionPool sessionPool,
			     int maxMessages)
			     throws JMSException {
    return _conn.createConnectionConsumer(destination, messageSelector,
                                          sessionPool, maxMessages);
    }

    public ConnectionConsumer
    createDurableConnectionConsumer(Topic topic,
                                    String subscriptionName,
                                    String messageSelector,
                                    ServerSessionPool sessionPool,
                                    int maxMessages)
                                    throws JMSException {
    return _conn.createDurableConnectionConsumer(topic, subscriptionName, 
                                messageSelector, sessionPool, maxMessages);
    }
    
	@Override
	public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		return _conn.createSharedConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
	}

	@Override
	public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		return _conn.createSharedDurableConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
	}

    public String toString() {
        return _conn.toString();
    }

}
