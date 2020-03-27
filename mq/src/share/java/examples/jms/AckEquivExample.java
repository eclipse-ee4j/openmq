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
 * The AckEquivExample class shows how the following two scenarios both ensure
 * that a message will not be acknowledged until processing of it is complete:
 * <ul>
 * <li> Using an asynchronous consumer (message listener) in an 
 * AUTO_ACKNOWLEDGE session
 * <li> Using a synchronous consumer in a CLIENT_ACKNOWLEDGE session
 * </ul>
 * <p>
 * With a message listener, the automatic acknowledgment happens when the
 * onMessage method returns -- that is, after message processing has finished.
 * <p>
 * With a synchronous receive, the client acknowledges the message after
 * processing is complete.  (If you use AUTO_ACKNOWLEDGE with a synchronous
 * receive, the acknowledgement happens immediately after the receive call; if
 * any subsequent processing steps fail, the message cannot be redelivered.)
 * <p>
 * The program contains a SynchProducer class, a SynchConsumer class, an 
 * AsynchSubscriber class with a TextListener class, a MultiplePublisher class,
 * a main method, and a method that runs the other classes' threads.
 * <p>
 * Specify a queue name and a topic name on the command line when you run the 
 * program.  The program also uses a queue named "controlQueue", which should be
 * created before you run the program.
 */
public class AckEquivExample {
    final String  CONTROL_QUEUE = "controlQueue";
    String        queueName = null;
    String        topicName = null;
    int           exitResult = 0;

    /**
     * The SynchProducer class creates a session in CLIENT_ACKNOWLEDGE mode and
     * sends a message.
     */
    public class SynchProducer extends Thread {
        
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Queue                queue = null;
            MessageProducer      msgProducer = null;
            final String         MSG_TEXT = 
                                    new String("Here is a client-acknowledge message");
            TextMessage          message = null;

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                session = connection.createSession(false, 
                    Session.CLIENT_ACKNOWLEDGE);
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
             * Create client-acknowledge producer.
             * Create and send message.
             */
            try {
                System.out.println("PRODUCER: Created client-acknowledge session");
                msgProducer = session.createProducer(queue);
                message = session.createTextMessage();
                message.setText(MSG_TEXT);
                System.out.println("PRODUCER: Sending message: " 
                    + message.getText());
                msgProducer.send(message);
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
        }
    }
    
    /**
     * The SynchConsumer class creates a session in CLIENT_ACKNOWLEDGE mode and
     * receives the message sent by the SynchProducer class.
     */
    public class SynchConsumer extends Thread {

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

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                session = connection.createSession(false, 
                    Session.CLIENT_ACKNOWLEDGE);
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
             * Create client-acknowledge consumer.
             * Receive message and process it.
             * Acknowledge message.
             */
             try {
                System.out.println("CONSUMER: Created client-acknowledge session");
                msgConsumer = session.createConsumer(queue);
                connection.start();
                message = (TextMessage) msgConsumer.receive();
                System.out.println("CONSUMER: Processing message: " 
                    + message.getText());
                System.out.println("CONSUMER: Now I'll acknowledge the message");
                message.acknowledge();
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
        }  	    
    }
    
    /**
     * The AsynchSubscriber class creates a session in AUTO_ACKNOWLEDGE mode
     * and fetches several messages from a topic asynchronously, using a 
     * message listener, TextListener.
     * <p>
     * Each message is acknowledged after the onMessage method completes.
     */
    public class AsynchSubscriber extends Thread {

        /**
         * The TextListener class implements the MessageListener interface by 
         * defining an onMessage method for the AsynchSubscriber class.
         */
        private class TextListener implements MessageListener {
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
                        System.out.println("CONSUMER: Processing message: " 
                                           + msg.getText());
                    } catch (JMSException e) {
                        System.out.println("Exception in onMessage(): " 
                                           + e.toString());
                    } 
                } else {
                    monitor.allDone();
                }
            }
        }

        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Topic                topic = null;
            TopicSubscriber      topicSubscriber = null;
            TextListener         topicListener = null;

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                connection.setClientID("AckEquivExample");
                session = connection.createSession(false, 
                    Session.AUTO_ACKNOWLEDGE);
                System.out.println("CONSUMER: Created auto-acknowledge session");
                topic = SampleUtilities.getTopic(topicName, session);
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
             * Create auto-acknowledge subscriber.
             * Register message listener (TextListener).
             * Start message delivery.
             * Send synchronize message to publisher, then wait till all
             *   messages have arrived.
             * Listener displays the messages obtained.
             */
            try {
                topicSubscriber = session.createDurableSubscriber(topic,
                     "AckEquivExampleSubscription");
                topicListener = new TextListener();
                topicSubscriber.setMessageListener(topicListener);
                connection.start();

                // Let publisher know that subscriber is ready.
                try {
                    SampleUtilities.sendSynchronizeMessage("CONSUMER: ", 
                                                            CONTROL_QUEUE);
                } catch (Exception e) {
                    System.out.println("Queue probably missing: " + e.toString());
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (JMSException ee) {}
                    }
    	            System.exit(1);
    	        }

                /*
                 * Asynchronously process messages.
                 * Block until publisher issues a control message indicating
                 * end of publish stream.
                 */
                topicListener.monitor.waitTillDone();
                topicSubscriber.close();
                session.unsubscribe("AckEquivExampleSubscription");
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
        }	    
    }

    /**
     * The MultiplePublisher class creates a session in AUTO_ACKNOWLEDGE mode
     * and publishes three messages to a topic. 
     */
    public class MultiplePublisher extends Thread {
        
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Topic                topic = null;
            MessageProducer      topicPublisher = null;
            TextMessage          message = null;
            final int            NUMMSGS = 3;
            final String         MSG_TEXT = 
                                     new String("Here is an auto-acknowledge message");

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                session = connection.createSession(false, 
                    Session.AUTO_ACKNOWLEDGE);
                System.out.println("PRODUCER: Created auto-acknowledge session");
                topic = SampleUtilities.getTopic(topicName, session);
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
             * After synchronizing with subscriber, create publisher.
             * Send 3 messages, varying text slightly.
             * Send end-of-messages message.
             */
            try {
                /*
                 * Synchronize with subscriber.  Wait for message indicating 
                 * that subscriber is ready to receive messages.
                 */
                try {
                    SampleUtilities.receiveSynchronizeMessages("PRODUCER: ", 
                                                              CONTROL_QUEUE, 1);
                } catch (Exception e) {
                    System.out.println("Queue probably missing: " + e.toString());
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (JMSException ee) {}
                    }
    	            System.exit(1);
    	        }

                topicPublisher = session.createProducer(topic);
                message = session.createTextMessage();
                for (int i = 0; i < NUMMSGS; i++) {
                    message.setText(MSG_TEXT + " " + (i + 1));
                    System.out.println("PRODUCER: Publishing message: " 
                        + message.getText());
                    topicPublisher.send(message);
                }
                
                // Send a non-text control message indicating end of messages. 
                topicPublisher.send(session.createMessage());
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
        }
    }
    
    /**
     * Instantiates the producer, consumer, subscriber, and publisher classes and 
     * starts their threads.
     * Calls the join method to wait for the threads to die.
     */
    public void run_threads() {
        SynchProducer        synchProducer = new SynchProducer();
        SynchConsumer        synchConsumer = new SynchConsumer();
        AsynchSubscriber     asynchSubscriber = new AsynchSubscriber();
        MultiplePublisher    multiplePublisher = new MultiplePublisher();
        
        synchProducer.start();
        synchConsumer.start();
        try {
            synchProducer.join();
            synchConsumer.join();
        } catch (InterruptedException e) {}

        asynchSubscriber.start();
        multiplePublisher.start();
        try {
            asynchSubscriber.join();
            multiplePublisher.join();
        } catch (InterruptedException e) {}
    }

    /**
     * Reads the queue and topic names from the command line, then calls the
     * run_threads method to execute the program threads.
     *
     * @param args	the topic used by the example
     */
    public static void main(String[] args) {
        AckEquivExample  aee = new AckEquivExample();
        
        if (args.length != 2) {
    	    System.out.println("Usage: java AckEquivExample <queue_name> <topic_name>");
    	    System.exit(1);
    	}
        aee.queueName = new String(args[0]);
        aee.topicName = new String(args[1]);
        System.out.println("Queue name is " + aee.queueName);
        System.out.println("Topic name is " + aee.topicName);

    	aee.run_threads();
    	SampleUtilities.exit(aee.exitResult);
    }
}
