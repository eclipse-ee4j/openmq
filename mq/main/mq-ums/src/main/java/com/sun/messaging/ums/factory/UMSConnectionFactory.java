/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.ums.factory;

import java.util.Properties;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;

/**
 * Each JMS provider implements this interface so that UMS can construct vendor specific connection factory in a generic
 * way.
 *
 * This is basically a class that knows how to construct a vendor specific connection factory. The init method
 * implementation is vendor specific.
 *
 * The implementation of this class must contain a no-arg constructor.
 *
 * The init() will be called immediately (by UMS) after the class is constructed.
 *
 * @author chiaming
 */
public interface UMSConnectionFactory {

    /**
     * Called by UMS immediately after constructed.
     *
     * @param props properties used by the connection factory.
     */

    void init(Properties props) throws JMSException;

    /**
     * Same as JMS ConnectionFactory.createConnection();
     */
    Connection createConnection() throws JMSException;

    /**
     * Same as ConnectionFactory.createConnection(String user, String password);
     */
    Connection createConnection(String user, String password) throws JMSException;

}
