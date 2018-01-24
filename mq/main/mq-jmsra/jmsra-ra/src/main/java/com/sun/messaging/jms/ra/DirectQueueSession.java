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

package com.sun.messaging.jms.ra;

import javax.jms.*;

import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSService.SessionAckMode;

/**
 *  DirectQueueSession ensures correct JMS semantics for JMS APIs that are valid
 *  at javax.jms.Session but invalid at javax.jms.QueueSession
 */
public class DirectQueueSession
extends DirectSession {
    
    /** Creates a new instance of DirectQueueSession */
    public DirectQueueSession(DirectConnection dc,
            JMSService jmsservice, long sessionId, SessionAckMode ackMode)
    throws JMSException {
        super(dc, jmsservice, sessionId, ackMode);
    }

    public TopicSubscriber createDurableSubscriber(Topic topic,
            String name)
    throws JMSException {
        String methodName =
                "createDurableSubscriber(Topic, name)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
    }

    public TopicSubscriber createDurableSubscriber(Topic topic,
            String name, String selector, boolean noLocal)
    throws JMSException {
        String methodName =
                "createDurableSubscriber(Topic, name, selector, noLocal)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
    }

    @Override
	public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        String methodName =
                "createDurableConsumer(Topic topic, String name)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        String methodName =
                "createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        String methodName =
                "createSharedConsumer(Topic topic, String sharedSubscriptionName)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        String methodName =
                "createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        String methodName =
                "createSharedDurableConsumer(Topic topic, String name)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	@Override
	public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        String methodName =
                "createSharedDurableConsumer(Topic topic, String name, String messageSelector)";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
	}

	/**
     *  Create a TemporaryTopic identity object
     */
    public javax.jms.TemporaryTopic createTemporaryTopic()
    throws JMSException {
        String methodName = "createTemporaryTopic()";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
    }

    /**
     *  Create a Topic identity object with the specified topic name
     *
     *  @param topicName The name of the Topic Destination
     *
     *  @throws InvalidDestinationException If the topicName contains illegal
     *          syntax.
     */
    public Topic createTopic(String topicName)
    throws JMSException {
        String methodName = "createTopic()";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
    }

    /**
     *  Unsubscribe the durable subscription specified by name
     */
    public void unsubscribe(String name)
    throws JMSException {
        String methodName = "unsubscribe()";
        String isIllegalMsg = _lgrMID_EXC + methodName +
                    ":Invalid for a QueueSession:sessionId=" + sessionId;
        _loggerJS.warning(isIllegalMsg);
        throw new javax.jms.IllegalStateException(isIllegalMsg);
    }

}
