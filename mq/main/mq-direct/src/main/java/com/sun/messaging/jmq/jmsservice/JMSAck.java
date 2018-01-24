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
 * @(#)JMSAck.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;
import com.sun.messaging.jmq.io.SysMessageID;

/**
 *  JMSAck holds all the information required to acknowledge a message
 */
public interface JMSAck {

    /**
     *  Get the connectionId for this message acknowledgement
     *
     *  @return The connectionId
     */
    public long getConnectionId();

    /**
     *  Get the sessionId for this message acknowledgement
     *
     *  @return The sessionId
     */
    public long getSessionId();

    /**
     *  Get the consumerId for this message acknowledgement
     *
     *  @return The consumerId
     */
    public long getConsumerId();

    /**
     *  Get the SysMessageID for this message acknowledgement
     *
     *  @return The SysMessageID
     */
    public SysMessageID getSysMessageID();

    /**
     *  Get the transactionId for this message acknowledgement
     *
     *  @return The transactionId
     */
    public long getTransactionId();

    /**
     *  Get the MessageAckType for this message acknowledgement
     *
     *  @return The MessageAckType
     */
    public MessageAckType getMessageAckType();

}
