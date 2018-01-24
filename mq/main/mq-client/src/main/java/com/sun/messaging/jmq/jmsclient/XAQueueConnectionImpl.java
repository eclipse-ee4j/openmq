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
 * @(#)XAQueueConnectionImpl.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Properties;

import com.sun.jms.spi.xa.*;

/** An XAQueueConnection is an active connection to a JMS Pub/Sub provider.
  * A client uses an XAQueueConnection to create one or more XAQueueSessions
  * for producing and consuming messages.
  *
  * @see javax.jms.XAConnection
  * @see javax.jms.XAQueueConnectionFactory
  */

public class XAQueueConnectionImpl extends QueueConnectionImpl implements XAQueueConnection, JMSXAQueueConnection {

    public
    XAQueueConnectionImpl(Properties configuration, String username,
                        String password, String type) throws JMSException {
        super(configuration, username, password, type);
    }

    /**
     * Create an XAQueueSession.
     *  
     * @exception JMSException if JMS Connection fails to create a
     *                         XA topic session due to some internal error.
     */ 
    public XAQueueSession
    createXAQueueSession() throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XAQueueSessionImpl (this, false, 0);
    }

    /**
     * Create an XAQueueSession
     *  
     * @param transacted      ignored.
     * @param acknowledgeMode ignored.
     *  
     * @return a newly created XA topic session.
     *  
     * @exception JMSException if JMS Connection fails to create a
     *                         XA topic session due to some internal error.
     */ 
    public QueueSession
    createQueueSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {
        return new XAQueueSessionImpl(this, transacted, acknowledgeMode);
    }

    /**
     * Create an XAQueueSession
     *  
     * @param transacted      ignored.
     * @param acknowledgeMode ignored.
     *  
     * @return a newly created XA topic session.
     *  
     * @exception JMSException if JMS Connection fails to create a
     *                         XA topic session due to some internal error.
     */ 
    public JMSXAQueueSession
    createXAQueueSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {
        return new JMSXAQueueSessionImpl(this, transacted, acknowledgeMode);
    }

    /**
     * Return the QueueConnection
     *
     * @return the QueueConnection
     */
    public QueueConnection getQueueConnection() {
        return (QueueConnection) (this);
    }
}

