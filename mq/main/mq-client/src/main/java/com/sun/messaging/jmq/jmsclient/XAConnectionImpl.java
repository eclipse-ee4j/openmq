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
 * @(#)XAConnectionImpl.java	1.10 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Properties;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;

/** An XAConnection is an active connection to a JMS provider.
  * A client uses an XAConnection to create one or more XASessions
  * for producing and consuming messages.
  *
  * @see javax.jms.XAConnection
  * @see javax.jms.XAConnectionFactory
  */

public class XAConnectionImpl extends UnifiedConnectionImpl implements XAConnection {

    public
    XAConnectionImpl(Properties configuration, String username,
                    String password, String type) throws JMSException {
        super(configuration, username, password, type);
    }

    /**
     * Create an XASession.
     *  
     * @exception JMSException if JMS Connection fails to create an
     *                         XA session due to some internal error.
    public XASession
    createXASession() throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XASessionImpl (this, false, 0);
    }
     */ 

    /**
     * Create an XASession
     *  
     * @param transacted
     * @param acknowledgeMode
     *  
     * @return a newly created XA topic session.
     *  
     * @exception JMSException if JMS Connection fails to create an
     *                         XA session due to some internal error.
     */ 
    public Session
    createSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XASessionImpl(this, transacted, acknowledgeMode);
    }

    public Session
    createSession(boolean transacted,
                  int acknowledgeMode, 
                  JMSRAManagedConnection mc) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XASessionImpl(this, transacted, acknowledgeMode, mc);
    }

    /**
     * Create an XAQueueSession
     *   
     * @param transacted      ignored.
     * @param acknowledgeMode ignored.
     *   
     * @return a newly created XA queue session.
     *   
     * @exception JMSException if JMS Connection fails to create a
     *                         XA queue session due to some internal error.
     */  
    public QueueSession
    createQueueSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XAQueueSessionImpl(this, transacted, acknowledgeMode);
    }

    public QueueSession
    createQueueSession(boolean transacted,
                       int acknowledgeMode,
                       JMSRAManagedConnection mc) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XAQueueSessionImpl(this, transacted, acknowledgeMode, mc);
    }

    /**
     * Create an XATopicSession
     *   
     * @param transacted      ignored.
     * @param acknowledgeMode ignored.
     *   
     * @return a newly created XA topic session.
     *   
     * @exception JMSException if JMS Connection fails to create a
     *                         XA topic session due to some internal error.
     */  
    public TopicSession
    createTopicSession(boolean transacted,
                       int acknowledgeMode) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XATopicSessionImpl(this, transacted, acknowledgeMode);
    }

    public TopicSession
    createTopicSession(boolean transacted,
                       int acknowledgeMode, 
                       JMSRAManagedConnection mc) throws JMSException {

        checkConnectionState();
 
        //disallow to set client ID after this action.
        setClientIDFlag();
 
        return new XATopicSessionImpl(this, transacted, acknowledgeMode, mc);
    }


}
