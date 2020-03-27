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

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.QueueSession;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;

import com.sun.messaging.AdministeredObject;

/**
 * A QueueSession provides methods for creating QueueReceiver's, QueueSender's, QueueBrowser's and TemporaryQueues.
 *
 * <P>
 * If there are messages that have been received but not acknowledged when a QueueSession terminates, these messages
 * will be retained and redelivered when a consumer next accesses the queue.
 *
 * @see jakarta.jms.Session
 * @see jakarta.jms.QueueConnection#createQueueSession(boolean, int)
 * @see jakarta.jms.XAQueueSession#getQueueSession()
 */

public class QueueSessionImpl extends UnifiedSessionImpl implements QueueSession {

    public QueueSessionImpl(ConnectionImpl connection, boolean transacted, int ackMode) throws JMSException {
        super(connection, transacted, ackMode);
    }

    public QueueSessionImpl(ConnectionImpl connection, int ackMode) throws JMSException {
        super(connection, ackMode);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createTopic");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createTemporaryTopic");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createDurableSubscriber");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {

        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createDurableSubscriber");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createDurableConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createDurableConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedDurableConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "createSharedDurableConsumer");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN, "unsubscribe");
        throw new jakarta.jms.IllegalStateException(errorString, AdministeredObject.cr.X_ILLEGAL_METHOD_FOR_DOMAIN);
    }

}
