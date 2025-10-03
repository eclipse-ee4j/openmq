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

package com.sun.messaging.bridge.service.jms;

import jakarta.jms.*;

/**
 *
 * @author amyk
 */
public class PooledXAConnectionImpl extends PooledConnectionImpl implements XAConnection {

    public PooledXAConnectionImpl(XAConnection conn) {
        super(conn);
    }

    @Override
    public XASession createXASession() throws JMSException {
        return ((XAConnection) _conn).createXASession();
    }

}
