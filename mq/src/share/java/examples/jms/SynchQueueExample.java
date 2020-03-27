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
 * The SynchQueueExample class consists only of a main method, which fetches 
 * one or more messages from a queue using synchronous message delivery.  Run 
 * this program in conjunction with SenderToQueue.  Specify a queue name on the
 * command line when you run the program.
 * <p>
 * The program calls methods in the SampleUtilities class.
 */
public class SynchQueueExample {

    /**
     * Main method.
     *
     * @param args	the queue used by the example
     */
    public static void main(String[] args) {
        String               queueName = null;
        ConnectionFactory    connectionFactory = null;
        Connection           connection = null;
        Session              session = null;
        Queue                queue = null;
        MessageConsumer      msgConsumer = null;
        TextMessage          message = null;
        int                  exitResult = 0;
                
    	/*
    	 * Read queue name from command line and display it.
    	 */
    	if (args.length != 1) {
    	    System.out.println("Usage: java SynchQueueExample <queue_name>");
    	    System.exit(1);
    	}
    	queueName = new String(args[0]);
    	System.out.println("Queue name is " + queueName);
    	    
        /*
         * Obtain connection factory.
         * Create connection.
         * Create session from connection; false means session is not
         * transacted.
         * Obtain queue name.
         */
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
         * Create consumer, then start message delivery.
	 * Receive all text messages from queue until
	 * a non-text message is received indicating end of
	 * message stream.
         * Close connection and exit.
         */
        try {
            msgConsumer = session.createConsumer(queue);
            connection.start();
	    while (true) {
		Message m = msgConsumer.receive();
		if (m instanceof TextMessage) {
		    message = (TextMessage) m;
		    System.out.println("Reading message: " + message.getText());
		} else {
                    // Non-text control message indicates end of messages.
 		    break;
		}
	    }
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
