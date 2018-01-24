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
 * @(#)UnifiedConnectionImpl.java	1.11 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Properties;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;

/**
 * A Connection is an active connection to a JMS provider. A
 * client uses a Connection to create one or more Sessions
 * for producing and consuming messages.
 *
 * @see     javax.jms.Connection
 * @see	    javax.jms.QueueConnectionFactory
 */
public class UnifiedConnectionImpl extends ConnectionImpl  {

    public
    UnifiedConnectionImpl(Properties configuration, String username,
                        String password, String type) throws JMSException {
        super(configuration, username, password, type);
    }
}
