/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.ra;

import jakarta.jms.*;

import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;

/**
 * DirectTopicSession ensures correct JMS semantics for JMS APIs that are valid at jakarta.jms.Session but invalid at
 * jakarta.jms.TopicSession
 */
public class DirectTopicSession extends DirectSession {

    /** Creates a new instance of DirectTopicSession */
    public DirectTopicSession(DirectConnection dc, JMSService jmsservice, long sessionId, SessionAckMode ackMode) throws JMSException {
        super(dc, jmsservice, sessionId, ackMode);
    }

    /**
     * Create a QueueBrowser to peek at the messages on the specified queue
     */
    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        String methodName = "createBrowser(Queue)";
        String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a TopicSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new jakarta.jms.IllegalStateException(isIllegalMsg);
    }

    /**
     * Create a QueueBrowser to peek at the messages on the specified queue using a message selector
     */
    @Override
    public QueueBrowser createBrowser(Queue queue, String selector) throws JMSException {
        String methodName = "createBrowser(Queue, selector)";
        String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a TopicSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new jakarta.jms.IllegalStateException(isIllegalMsg);
    }

    /**
     * Create a TemporaryQueue identity object
     */
    @Override
    public jakarta.jms.TemporaryQueue createTemporaryQueue() throws JMSException {
        String methodName = "createTemporaryQueue()";
        String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a TopicSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new jakarta.jms.IllegalStateException(isIllegalMsg);
    }

    /**
     * Create a Queue identity object with the specified topic name
     *
     * @param queueName The name of the Queue Destination
     *
     * @throws InvalidDestinationException If the queueName contains illegal syntax.
     */
    @Override
    public Queue createQueue(String queueName) throws JMSException {
        String methodName = "createQueue()";
        String isIllegalMsg = _lgrMID_EXC + methodName + ":Invalid for a TopicSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new jakarta.jms.IllegalStateException(isIllegalMsg);
    }
}
