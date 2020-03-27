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
 * The AsynchQueueExample class consists only of a main method, which fetches 
 * one or more messages from a queue using asynchronous message delivery.
 * <p>
 * Compile TextListener.java before you run this program.
 * <p>
 * Run this program in conjunction with SenderToQueue.  Specify a queue name
 * on the command line when you run the program.
 */
public class AsynchQueueExample {

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
        TextListener         textListener = null;
        int                  exitResult = 0;
        
    	if (args.length != 1) {
    	    System.out.println("Usage: java AsynchQueueExample <queue_name>");
    	    System.exit(1);
    	}   	
        queueName = new String(args[0]);
        System.out.println("Queue name is " + queueName);
    	    
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
         * Create consumer.
         * Register message listener (TextListener).
         * Start message delivery; listener displays the message obtained.
         * Block until producer issues a control message indicating
         * end of publish stream.
         */
        try {
            msgConsumer = session.createConsumer(queue);
            textListener = new TextListener();
            msgConsumer.setMessageListener(textListener);
            connection.start();
            
            textListener.monitor.waitTillDone();
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
