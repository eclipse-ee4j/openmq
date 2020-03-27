/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Properties;

import jakarta.jms.ConnectionConsumer;
import jakarta.jms.JMSException;
import jakarta.jms.QueueSession;
import jakarta.jms.ServerSessionPool;
import jakarta.jms.Topic;

import com.sun.messaging.AdministeredObject;

/**
 * A QueueConnection is an active connection to a JMS PTP provider. A client uses a QueueConnection to create one or
 * more QueueSessions for producing and consuming messages.
 *
 * @see jakarta.jms.Connection
 * @see jakarta.jms.QueueConnectionFactory
 */

public class QueueConnectionImpl extends UnifiedConnectionImpl implements com.sun.messaging.jms.QueueConnection {

    public QueueConnectionImpl(Properties configuration, String username, String password, String type) throws JMSException {
        super(configuration, username, password, type);

        // bug 6172663
        setIsTopicConnection(false);
        super.setIsQueueConnection(true);
    }

    @Override
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException {

        return super.createQueueSession(transacted, acknowledgeMode);
    }

    @Override
    public QueueSession createQueueSession(int acknowledgeMode) throws JMSException {

        checkConnectionState();

        // disallow to set client ID after this action.
        setClientIDFlag();

        return new QueueSessionImpl(this, acknowledgeMode);
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
            int maxMessages) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createDurableConnectionConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
            int maxMessages) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedConnectionConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
            int maxMessages) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedDurableConnectionConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

}
