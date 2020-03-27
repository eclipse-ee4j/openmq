/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.jms.*;

/**
 * The TextListener class implements the MessageListener interface by 
 * defining an onMessage method that displays the contents of a TextMessage.
 * <p>
 * This class acts as the listener for the AsynchQueueReceiver class.
 */
public class TextListener implements MessageListener {
    final SampleUtilities.DoneLatch  monitor = 
        new SampleUtilities.DoneLatch();

    /**
     * Casts the message to a TextMessage and displays its text.
     * A non-text message is interpreted as the end of the message 
     * stream, and the message listener sets its monitor state to all 
     * done processing messages.
     *
     * @param message	the incoming message
     */
    public void onMessage(Message message) {
        if (message instanceof TextMessage) {
            TextMessage  msg = (TextMessage) message;
            
            try {
                System.out.println("Reading message: " + msg.getText());
            } catch (JMSException e) {
                System.out.println("Exception in onMessage(): " + e.toString());
            }
        } else {
            monitor.allDone();
        }
    }
}
