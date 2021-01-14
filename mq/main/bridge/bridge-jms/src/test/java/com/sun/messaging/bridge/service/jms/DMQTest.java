/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation.
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
import org.mockito.Mockito;

class DMQTest {
    @Test
    void testLogMessageForNullMessage() {
        Logger logger = Mockito.mock(Logger.class);

        DMQ.logMessage(null, null, null, logger);

        Mockito.verify(logger).log(Level.INFO, "Logging message going to DMQ for null\n"
            + "\tJMS Headers:\n"
            + "\tUnable to get JMSMessageID header from message null for null: null\n"
            + "\tUnable to get JMSDestination header from message null for null: null\n"
            + "\tUnable to get JMSTimestamp header from message null for null: null\n"
            + "\tUnable to get JMSExpiration header from message null for null: null\n"
            + "\tUnable to get JMSDeliveryMode header from message null for null: null\n"
            + "\tUnable to get JMSCorrelationID header from message null for null: null\n"
            + "\tUnable to get JMSPriority header from message null for null: null\n"
            + "\tUnable to get JMSRedelivered header from message null for null: null\n"
            + "\tUnable to get JMSReplyTo header from message null for null: null\n"
            + "\tUnable to get JMSType header from message null for null: null\n"
            + "\n"
            + "\tJMS Properties:\n"
            + "Unable to get PropertyNames from message null for null: null\n\n"
            + "\tMessage.toString:\n"
            + "\ttoString=null");
    }

    @Test
    void testLogMessage() {
        Logger logger = Mockito.mock(Logger.class);
        Message message = Mockito.mock(Message.class);
        String mid = "abcdef123456";
        Link link = Mockito.mock(Link.class);

        DMQ.logMessage(message, mid, link, logger);

        Mockito.verify(logger).log(Level.INFO, "Logging message going to DMQ for Mock for Link, hashCode: " + link.hashCode() + "\n"
            + "\tJMS Headers:\n"
            + "\tJMSMessageID=null\n"
            + "\tJMSDestination=null\n"
            + "\tJMSTimestamp=0\n"
            + "\tJMSExpiration=0\n"
            + "\tJMSDeliveryMode=0\n"
            + "\tJMSCorrelationID=null\n"
            + "\tJMSPriority=0\n"
            + "\tJMSRedelivered=false\n"
            + "\tJMSReplyTo=null\n"
            + "\tJMSType=null\n"
            + "\n"
            + "\tJMS Properties:\n"
            + "\n\n"
            + "\tMessage.toString:\n"
            + "\ttoString=Mock for Message, hashCode: " + message.hashCode());
    }
}
