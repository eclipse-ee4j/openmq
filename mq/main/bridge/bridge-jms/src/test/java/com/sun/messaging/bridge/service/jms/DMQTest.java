/*
 * Copyright (c) 2020, 2025 Contributors to the Eclipse Foundation.
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

import jakarta.jms.Message;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DMQTest {
    @Mock
    private Logger logger;

    @Mock
    private Message message;

    @Mock
    private Link link;

    @Test
    void testLogMessageForNullMessage() {
        DMQ.logMessage(null, null, null, logger);

        Mockito.verify(logger).log(Level.INFO, """
            Logging message going to DMQ for null
            	JMS Headers:
            	Unable to get JMSMessageID header from message null for null: null
            	Unable to get JMSDestination header from message null for null: null
            	Unable to get JMSTimestamp header from message null for null: null
            	Unable to get JMSExpiration header from message null for null: null
            	Unable to get JMSDeliveryMode header from message null for null: null
            	Unable to get JMSCorrelationID header from message null for null: null
            	Unable to get JMSPriority header from message null for null: null
            	Unable to get JMSRedelivered header from message null for null: null
            	Unable to get JMSReplyTo header from message null for null: null
            	Unable to get JMSType header from message null for null: null
            
            	JMS Properties:
            Unable to get PropertyNames from message null for null: null
            
            	Message.toString:
            	toString=null""");
    }

    @Test
    void testLogMessage() {
        String mid = "abcdef123456";

        DMQ.logMessage(message, mid, link, logger);

        Mockito.verify(logger).log(Level.INFO, """
            Logging message going to DMQ for link
            	JMS Headers:
            	JMSMessageID=null
            	JMSDestination=null
            	JMSTimestamp=0
            	JMSExpiration=0
            	JMSDeliveryMode=0
            	JMSCorrelationID=null
            	JMSPriority=0
            	JMSRedelivered=false
            	JMSReplyTo=null
            	JMSType=null
            
            	JMS Properties:
            
            
            	Message.toString:
            	toString=message""");
    }
}
