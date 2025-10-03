/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import jakarta.jms.*;

/**
 * TemporaryQueue for DIRECT Mode
 */
public class TemporaryQueue extends TemporaryDestination implements jakarta.jms.TemporaryQueue {

    /** Creates a new instance of TemporaryQueue */
    protected TemporaryQueue(DirectConnection dc) throws JMSException {
        super(dc, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE, com.sun.messaging.jmq.jmsservice.Destination.TemporaryType.queue);
    }

    /** Creates a new instance of TemporaryQueue with a pre-assigned name */
    protected TemporaryQueue(String name) throws JMSException {
        super(name, com.sun.messaging.jmq.jmsservice.Destination.Type.QUEUE);
    }

    @Override
    public boolean isQueue() {
        return true;
    }
}
