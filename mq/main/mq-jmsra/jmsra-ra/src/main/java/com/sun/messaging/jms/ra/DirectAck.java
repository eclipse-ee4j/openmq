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

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsservice.JMSAck;
import com.sun.messaging.jmq.jmsservice.JMSService.MessageAckType;

/**
 *
 */
public class DirectAck implements JMSAck {

    /** The connectionId of the JMSAck */
    private long connectionId;

    /** The sessionId of the JMSAck */
    private long sessionId;

    /** The consumerId of the JMSAck */
    private long consumerId;

    /** The Sun MQ SysMessageID of the JMSAck */
    private SysMessageID sysMessageID;

    /** The transactionId of the JMSAck */
    private long transactionId;

    /** The messageAckType of the JMSAck */
    private MessageAckType messageAckType;

    /** Creates a new instance of DirectAck */
    public DirectAck(long connectionId, long sessionId, long consumerId,
            SysMessageID sysMessageID, long transactionId,
            MessageAckType messageAckType) {
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.consumerId = consumerId;
        this.sysMessageID = sysMessageID;
        this.messageAckType = messageAckType;
    }

    /**
     *  Return the connectionId of this JMSAck
     *
     *  @return The connectionId
     */
    public long getConnectionId(){
        return this.connectionId;
    }

    /**
     *  Return the consumerId of this JMSAck
     *
     *  @return The consumerId
     */
    public long getConsumerId(){
        return this.consumerId;
    }

    /**
     *  Return the messageAckType of this JMSAck
     *
     *  @return The messageAckType
     */
    public MessageAckType getMessageAckType(){
        return this.messageAckType;
    }

    /**
     *  Return the sessionId of this JMSAck
     *
     *  @return The sessionId
     */
    public long getSessionId(){
        return this.sessionId;
    }

    /**
     *  Return the sysMessageID of this JMSAck
     *
     *  @return The sysMessageID
     */
    public SysMessageID getSysMessageID(){
        return this.sysMessageID;
    }

    /**
     *  Return the transactionId of this JMSAck
     *
     *  @return The transactionId
     */
    public long getTransactionId(){
        return this.transactionId;
    }
}
