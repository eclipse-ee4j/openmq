/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging;

/**
 * A <code>QueueConnectionFactory</code> is used to create QueueConnections with the OpenMQ Java Message Service (JMS)
 * Point-to-Point (PTP) provider.
 *
 * @see jakarta.jms.QueueConnectionFactory jakarta.jms.QueueConnectionFactory
 */
public class QueueConnectionFactory extends com.sun.messaging.ConnectionFactory implements jakarta.jms.QueueConnectionFactory {

    private static final long serialVersionUID = -7857914973118338833L;

    /**
     * Constructs a QueueConnectionFactory with the default configuration.
     *
     */
    public QueueConnectionFactory() {
    }

    /**
     * Constructs a QueueConnectionFactory with the specified configuration.
     *
     */
    protected QueueConnectionFactory(String defaultsBase) {
        super(defaultsBase);
    }

}
