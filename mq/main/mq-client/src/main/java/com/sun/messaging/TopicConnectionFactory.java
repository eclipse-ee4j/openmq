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
 * @(#)TopicConnectionFactory.java	1.14 06/28/07
 */ 

package com.sun.messaging;

/**
 * A <code>TopicConnectionFactory</code> is used to create TopicConnections with
 * the Sun MQ Java Message Service (JMS) Publish/Subscribe (Pub/Sub) provider.
 *
 * @see         javax.jms.TopicConnectionFactory javax.jms.TopicConnectionFactory
 */
public class TopicConnectionFactory extends com.sun.messaging.ConnectionFactory implements javax.jms.TopicConnectionFactory {

    /**
     * Constructs a TopicConnectionFactory with the default configuration.
     * 
     */
    public TopicConnectionFactory() {
        super();
    }
 
    /**
     * Constructs a TopicConnectionFactory with the specified configuration.
     * 
     */
    protected TopicConnectionFactory(String defaultsBase) {
        super(defaultsBase);
    }
 
}
