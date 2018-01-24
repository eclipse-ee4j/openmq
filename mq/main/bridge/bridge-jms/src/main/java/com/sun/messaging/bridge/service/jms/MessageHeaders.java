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

package com.sun.messaging.bridge.service.jms;

import javax.jms.*;

/**
 * 
 * @author amyk
 *
 */

public class MessageHeaders {

    int deliverymode;
    long expiration;
    String mid = null;
    int priority;
    long timestamp;
    Destination destination = null;

    protected static MessageHeaders getMessageHeaders(Message m) throws JMSException {
        MessageHeaders mhs = new MessageHeaders();

        mhs.deliverymode = m.getJMSDeliveryMode();
        mhs.expiration = m.getJMSExpiration();
        mhs.mid = m.getJMSMessageID();
        mhs.priority = m.getJMSPriority();
        mhs.timestamp = m.getJMSTimestamp();
        mhs.destination = m.getJMSDestination();
        return mhs;
    }

    protected static void resetMessageHeaders(Message m, MessageHeaders mhs) throws JMSException {
        m.setJMSDeliveryMode(mhs.deliverymode);
        m.setJMSExpiration(mhs.expiration);
        m.setJMSMessageID(mhs.mid);
        m.setJMSPriority(mhs.priority);
        m.setJMSTimestamp(mhs.timestamp);
        m.setJMSDestination(mhs.destination);
    }

}
