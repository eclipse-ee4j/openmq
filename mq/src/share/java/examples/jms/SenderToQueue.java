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
 * The SenderToQueue class consists only of a main method, which sends 
 * several messages to a queue.
 * <p>
 * Run this program in conjunction with either SynchQueueExample or 
 * AsynchQueueExample.  Specify a queue name on the command line when you run 
 * the program.  By default, the program sends one message.  Specify a number 
 * after the queue name to send that number of messages.
 */
public class SenderToQueue {

    /**
     * Main method.
     *
     * @param args	the queue used by the example and, optionally, the
     *                   number of messages to send
     */
    public static void main(String[] args) {
        String               queueName = null;
        ConnectionFactory    connectionFactory = null;
        Connection           connection = null;
        Session              session = null;
        Queue                queue = null;
        MessageProducer      msgProducer = null;
        TextMessage          message = null;
        final int            NUM_MSGS;
        final String         MSG_TEXT = new String("Here is a message");
        int                  exitResult = 0;
        
    	if ( (args.length < 1) || (args.length > 2) ) {
    	    System.out.println("Usage: java SenderToQueue <queue_name> [<number_of_messages>]");
    	    System.exit(1);
    	} 
        queueName = new String(args[0]);
        System.out.println("Queue name is " + queueName);
        if (args.length == 2){
            NUM_MSGS = (new Integer(args[1])).intValue();
        } else {
            NUM_MSGS = 1;
        }
    	    
        try {
            connectionFactory = 
                SampleUtilities.getConnectionFactory();
            connection = 
                connectionFactory.createConnection();
            session = connection.createSession(false, 
                Session.AUTO_ACKNOWLEDGE);
            queue = SampleUtilities.getQueue(queueName, session);
    	} catch (Exception e) {
            System.out.println("Connection problem: " + e.toString());
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException ee) {} 
            }
    	    System.exit(1);
    	} 

        /*
         * Create producer.
         * Create text message.
         * Send five messages, varying text slightly.
         * Send end-of-messages message.
         * Finally, close connection.
         */
        try {
            msgProducer = session.createProducer(queue);
            message = session.createTextMessage();
            for (int i = 0; i < NUM_MSGS; i++) {
                message.setText(MSG_TEXT + " " + (i + 1));
                System.out.println("Sending message: " + message.getText());
                msgProducer.send(message);
            }

            // Send a non-text control message indicating end of messages.
            msgProducer.send(session.createMessage());
        } catch (JMSException e) {
            System.out.println("Exception occurred: " + e.toString());
            exitResult = 1;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    exitResult = 1;
                }
            }
        }
    	SampleUtilities.exit(exitResult);
    }
}
