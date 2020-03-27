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
 * The RequestReplyQueue class illustrates a simple implementation of a
 * request/reply message exchange.  It uses the QueueRequestor class provided
 * by JMS.  Providers and clients can create more sophisticated versions of
 * this facility.
 * <p>
 * The program contains a Request class, a Reply class, a main method, and 
 * a method that runs the sender and receiver threads.
 */
public class RequestReplyQueue {
    String  queueName = null;
    int     exitResult = 0;

    /**
     * The Request class represents the request half of the message exchange.  
     */
    public class Request extends Thread {
        
        /**
         * Runs the thread.
         */
        public void run() {
            QueueConnectionFactory    queueConnectionFactory = null;
            QueueConnection           queueConnection = null;
            QueueSession              queueSession = null;
            Queue                     queue = null;
            QueueRequestor            queueRequestor = null;
            TextMessage               message = null;
            final String              MSG_TEXT = new String("Here is a request");
            TextMessage               reply = null;
            String                    replyID = null;

            try {
                queueConnectionFactory = 
                    SampleUtilities.getQueueConnectionFactory();
                queueConnection = 
                    queueConnectionFactory.createQueueConnection();
                queueSession = queueConnection.createQueueSession(false, 
                    Session.AUTO_ACKNOWLEDGE);
                queue = SampleUtilities.getQueue(queueName, (Session) queueSession);
            } catch (Exception e) {
                System.out.println("Connection problem: " + e.toString());
                if (queueConnection != null) {
                    try {
                        queueConnection.close();
                    } catch (JMSException ee) {}
                }
                System.exit(1);
            } 

            /*  
             * Create a QueueRequestor.
             * Create a text message and set its text.
             * Start delivery of incoming messages.
             * Send the text message as the argument to the request method, 
             * which returns the reply message.  The request method also
             * creates a temporary queue and places it in the JMSReplyTo
             * message header field.
             * Extract and display the reply message.
             * Read the JMSCorrelationID of the reply message and confirm that 
             * it matches the JMSMessageID of the message that was sent.
             * Finally, close the connection.
             */
            try {
                queueRequestor = new QueueRequestor(queueSession, queue);
                message = queueSession.createTextMessage();
                message.setText(MSG_TEXT);
                System.out.println("REQUEST: Sending message: " 
                    + message.getText());
                queueConnection.start();
                reply = (TextMessage) queueRequestor.request(message);
                System.out.println("REQUEST: Reply received: " 
                    + reply.getText());
                replyID = new String(reply.getJMSCorrelationID());
                if (replyID.equals(message.getJMSMessageID())) {
                    System.out.println("REQUEST: OK: Reply matches sent message");
                } else {
                    System.out.println("REQUEST: ERROR: Reply does not match sent message");
                }
            } catch (JMSException e) {
                System.out.println("Exception occurred: " + e.toString());
                exitResult = 1;
            } catch (Exception ee) {
                System.out.println("Unexpected exception: " + ee.toString());
                ee.printStackTrace();
                exitResult = 1;
            } finally {
                if (queueConnection != null) {
                    try {
                        queueConnection.close();
                    } catch (JMSException e) {
                        exitResult = 1;
                    }
                }
            }
        }
    }

    /**
     * The Reply class represents the reply half of the message exchange.
     */
    public class Reply extends Thread {

        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Queue                queue = null;
            MessageConsumer      msgConsumer = null;
            TextMessage          message = null; 
            Queue                tempQueue = null;
            MessageProducer      replyProducer = null;
            TextMessage          reply = null; 
            final String         REPLY_TEXT = new String("Here is a reply");

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
             * Create a MessageConsumer.
             * Start delivery of incoming messages.
             * Call receive, which blocks until it obtains a message.
             * Display the message obtained.
             * Extract the temporary reply queue from the JMSReplyTo field of 
             * the message header.
             * Use the temporary queue to create a sender for the reply message.
             * Create the reply message, setting the JMSCorrelationID to the 
             * value of the incoming message's JMSMessageID.
             * Send the reply message.
             * Finally, close the connection. 
             */    
            try {
                msgConsumer = session.createConsumer(queue);
                connection.start();
                message = (TextMessage) msgConsumer.receive();
                System.out.println("REPLY: Message received: " 
                    + message.getText());
                tempQueue = (Queue) message.getJMSReplyTo();
                replyProducer = session.createProducer(tempQueue);
                reply = session.createTextMessage();
                reply.setText(REPLY_TEXT);
                reply.setJMSCorrelationID(message.getJMSMessageID());
                System.out.println("REPLY: Sending reply: " + reply.getText());
                replyProducer.send(reply);
            } catch (JMSException e) {
                System.out.println("Exception occurred: " + e.toString());
                exitResult = 1;
            } catch (Exception ee) {
                System.out.println("Unexpected exception: " + ee.toString());
                ee.printStackTrace();
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
        }
    }
        
    /**
     * Instantiates the Request and Reply classes and starts their
     * threads.
     * Calls the join method to wait for the threads to die.
     */
    public void run_threads() {
        Request  request = new Request();
        Reply    reply = new Reply();

        request.start();
        reply.start();
        try {
            request.join();
            reply.join();
        } catch (InterruptedException e) {}
    }

    /**
     * Reads the queue name from the command line, then calls the
     * run_threads method to execute the program threads.
     *
     * @param args	the queue used by the example
     */
    public static void main(String[] args) {
        RequestReplyQueue  rrq = new RequestReplyQueue();
        
        if (args.length != 1) {
    	    System.out.println("Usage: java RequestReplyQueue <queue_name>");
    	    System.exit(1);
    	}
    	
        rrq.queueName = new String(args[0]);
        System.out.println("Queue name is " + rrq.queueName);

    	rrq.run_threads();
    	SampleUtilities.exit(rrq.exitResult);
    }
}
