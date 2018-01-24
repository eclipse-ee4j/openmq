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
 * @(#)TopicConnectionImpl.java	1.22 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Properties;

/**
 * A TopicConnection is an active connection to a JMS Pub/Sub provider.
 * A client uses a TopicConnection to create one or more TopicSessions
 * for producing and consuming messages.
 *
 * @see javax.jms.Connection
 * @see javax.jms.TopicConnectionFactory
 */
public class TopicConnectionImpl extends UnifiedConnectionImpl implements com.sun.messaging.jms.TopicConnection {

    public
    TopicConnectionImpl(Properties configuration, String username,
                        String password, String type) throws JMSException {
        super(configuration, username, password, type);
        setIsTopicConnection(true);
    }

    public TopicSession
    createTopicSession(int acknowledgeMode) throws JMSException {
        checkConnectionState();

        TopicSessionImpl ts = new TopicSessionImpl (this, acknowledgeMode);

        //disallow to set client ID after this action.
        setClientIDFlag();

        return ( ts );
    }

}
