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

package com.sun.messaging;

/**
 * An <code>XAQueueConnectionFactory</code> is used to create XAQueueConnections with a Java Message Service (JMS)
 * Point-to-Point (PTP) provider.
 *
 * @see jakarta.jms.XAQueueConnectionFactory jakarta.jms.XAQueueConnectionFactory
 */
public class XAQueueConnectionFactory extends com.sun.messaging.XAConnectionFactory
        implements jakarta.jms.QueueConnectionFactory, jakarta.jms.XAQueueConnectionFactory {

    private static final long serialVersionUID = -7533973539959581060L;

}
