/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import jakarta.jms.JMSException;
import jakarta.jms.MessageNotWriteableException;

import com.sun.messaging.jmq.io.JMSPacket;
import com.sun.messaging.jmq.io.PacketType;

public class DirectTextPacket extends DirectPacket implements jakarta.jms.TextMessage {

    /**
     * The String hat holds the JMS TextMessage body
     */
    private String text = null;

    /**
     * Logging
     */
    private static final String _className = "com.sun.messaging.jms.ra.DirectTextPacket";

    /**
     * Create a new instance of DirectTextPacket.
     * <p>
     * 
     * Used by createTextMessage API
     * 
     */
    public DirectTextPacket(DirectSession ds, String txt) throws JMSException {
        super(ds);
        if (_logFINE) {
            Object params[] = new Object[3];
            params[0] = ds;
            params[2] = txt;
            _loggerOC.entering(_className, "constructor()", params);
        }
        this.text = txt;
    }

    public DirectTextPacket(JMSPacket jmsPacket, long consumerId, DirectSession ds) throws JMSException {
        super(jmsPacket, consumerId, ds);
        this._getMessageBodyFromPacket();
    }

    /////////////////////////////////////////////////////////////////////////
    // methods that implement jakarta.jms.TextMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     * Clear out the message body .
     */
    @Override
    public void clearBody() throws JMSException {
        super.clearBody();
        this.text = null;
    }

    /**
     * Get the string containing this message's data. The default value is null.
     * 
     * @return The <CODE>String</CODE> containing the message's data
     * 
     * @throws JMSException if the JMS provider fails to get the text due to some internal error.
     */
    @Override
    public String getText() throws JMSException {
        if (_logFINE) {
            String methodName = "getText()";
            _loggerJM.fine(_lgrMID_INF + /* "messageId="+messageId+":"+ */
                    methodName + ":"/* +this.text */);
        }
        return text;
    }

    /**
     * Set the string containing this message's data.
     * 
     * @param string the <CODE>String</CODE> containing the message's data
     * 
     * @throws JMSException if the JMS provider fails to set the text due to some internal error.
     * @throws MessageNotWriteableException if the message is in read-only mode.
     */
    @Override
    public void setText(String string) throws JMSException {
        String methodName = "setText()";
        if (_logFINE) {
            _loggerJM.fine(_lgrMID_INF + /* "messageId="+messageId+":"+ */
                    methodName + ":" + string);
        }
        this.checkForReadOnlyMessageBody(methodName);
        this.text = string;
    }

    /////////////////////////////////////////////////////////////////////////
    // end jakarta.jms.TextMessage
    /////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////
    // MQ methods DirectTextPacket / jakarta.jms.TextMessage
    /////////////////////////////////////////////////////////////////////////
    /**
     * Set the JMS default values on this JMS TextMessage
     */
    @Override
    protected void _setDefaultValues() throws JMSException {
        super._setDefaultValues();
        this.pkt.setPacketType(PacketType.TEXT_MESSAGE);
    }

    /**
     * Set the JMS Message body into the packet
     */
    @Override
    protected void _setBodyToPacket() throws JMSException {
        if (this.text != null) {
            try {
                super._setMessageBodyOfPacket(text.getBytes(UTF8));
            } catch (Exception ex) {
                String errMsg = _lgrMID_EXC + ":ERROR setting TextMessage body=" + this.text + ":Exception=" + ex.getMessage();
                _loggerJM.severe(errMsg);
                JMSException jmse = new jakarta.jms.JMSException(errMsg);
                jmse.initCause(ex);
                throw jmse;
            }
        }
    }

    /**
     * Get the JMS Message body from the packet on a receeived message
     */
    @Override
    protected void _getMessageBodyFromPacket() throws JMSException {
        try {
            byte[] btext = this._getMessageBodyByteArray();
            if (btext != null) {
                this.text = new String(btext, UTF8);
            }
        } catch (Exception e) {
            String errMsg = _lgrMID_EXC + ":Exception getting body for receieved TextMessage" + e.getMessage();
            _loggerJM.severe(errMsg);
            JMSException jmse = new jakarta.jms.JMSException(errMsg);
            jmse.initCause(e);
            throw jmse;
        }
    }
}
