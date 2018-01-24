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

import javax.jms.Connection;
import javax.jms.JMSException;

import javax.transaction.xa.XAResource;

/**
 *  The base class for JMS ConnectionFactory implementation classes to make
 *  the interface to ManagedConnectionFactory consistent.<p>
 *  The ManagedConnection will get an object of this type from the
 *  ManagedConnectionFactory, with which the ManagedConnection will create
 *  the actual connection.
 */
public abstract class ConnectionCreator {
    
    /** Creates a new instance of ConnectionCreator */
    protected ConnectionCreator() {
    }

    protected abstract Connection _createConnection(String un, String pw)
    throws JMSException;
    
    protected abstract Connection _createQueueConnection(String un, String pw)
    throws JMSException;
    
    protected abstract Connection _createTopicConnection(String un, String pw)
    throws JMSException;

    protected abstract XAResource _createXAResource(ManagedConnection mc,
            Object connection)
    throws JMSException;

}
