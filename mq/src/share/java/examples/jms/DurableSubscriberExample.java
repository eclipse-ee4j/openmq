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
 * The DurableSubscriberExample class demonstrates that a durable subscription
 * is active even when the subscriber is not active.
 * <p>
 * The program contains a DurableSubscriber class, a MultiplePublisher class, 
 * a main method, and a method that instantiates the classes and calls their
 * methods in sequence.
 * <p>
 * The program begins like any publish/subscribe program: the subscriber starts,
 * the publisher publishes some messages, and the subscriber receives them.
 * <p>
 * At this point the subscriber closes itself.  The publisher then publishes 
 * some messages while the subscriber is not active.  The subscriber then 
 * restarts and receives the messages.
 * <p>
 * Specify a topic name on the command line when you run the program.
 */
public class DurableSubscriberExample {
    String      topicName = null;
    int         exitResult = 0;
    static int  startindex = 0;

    /**
     * The DurableSubscriber class contains a constructor, a startSubscriber 
     * method, a closeSubscriber method, and a finish method.
     * <p>
     * The class fetches messages asynchronously, using a message listener, 
     * TextListener.
     */
    public class DurableSubscriber {
        Connection         connection = null;
        Session            session = null;
        Topic              topic = null;
        TopicSubscriber    topicSubscriber = null;
        TextListener       topicListener = null;

        /**
         * The TextListener class implements the MessageListener interface by 
         * defining an onMessage method for the DurableSubscriber class.
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
                        System.out.println("SUBSCRIBER: Reading message: " 
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
         * Constructor: looks up a connection factory and topic and creates a 
         * connection and session.
         */
        public DurableSubscriber() {
            ConnectionFactory  connectionFactory = null;

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                connection.setClientID("DurableSubscriberExample");
                session = connection.createSession(false, 
                    Session.AUTO_ACKNOWLEDGE);
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
        }

        /**
         * Stops connection, then creates durable subscriber, registers message 
         * listener (TextListener), and starts message delivery; listener
         * displays the messages obtained.
         */
        public void startSubscriber() {
            try {
                System.out.println("Starting subscriber");
                connection.stop();
                topicSubscriber = session.createDurableSubscriber(topic,
                    "MakeItLast");
                topicListener = new TextListener();
                topicSubscriber.setMessageListener(topicListener);
                connection.start();
            } catch (JMSException e) {
                System.out.println("Exception occurred: " + e.toString());
                exitResult = 1;
            }
        }
        
        /**
         * Blocks until publisher issues a control message indicating
         * end of publish stream, then closes subscriber.
         */
        public void closeSubscriber() {
            try {
                topicListener.monitor.waitTillDone();
                System.out.println("Closing subscriber");
                topicSubscriber.close();
            } catch (JMSException e) {
                System.out.println("Exception occurred: " + e.toString());
                exitResult = 1;
            }
        }
        
        /**
         * Closes the connection.
         */
        public void finish() {
            if (connection != null) {
                try {
                    session.unsubscribe("MakeItLast");
                    connection.close();
                } catch (JMSException e) {
                    exitResult = 1;
                }
            }
        }
    }

    /**
     * The MultiplePublisher class publishes several messages to a topic. It
     * contains a constructor, a publishMessages method, and a finish method.
     */
    public class MultiplePublisher {
        Connection        connection = null;
        Session           session = null;
        Topic             topic = null;
        MessageProducer   topicPublisher = null;

        /**
         * Constructor: looks up a connection factory and topic and creates a 
         * connection and session.  Also creates the producer.
         */
        public MultiplePublisher() {
            ConnectionFactory  connectionFactory = null;

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
                session = connection.createSession(false, 
                    Session.AUTO_ACKNOWLEDGE);
                topic = SampleUtilities.getTopic(topicName, session);
                topicPublisher = session.createProducer(topic);
            } catch (Exception e) {
                System.out.println("Connection problem: " + e.toString());
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException ee) {}
                }
    	        System.exit(1);
            } 
        }
        
        /**
         * Creates text message.
         * Sends some messages, varying text slightly.
         * Messages must be persistent.
         */
        public void publishMessages() {
            TextMessage   message = null;
            int           i;
            final int     NUMMSGS = 3;
            final String  MSG_TEXT = new String("Here is a message");

            try {
                message = session.createTextMessage();
                for (i = startindex; i < startindex + NUMMSGS; i++) {
                    message.setText(MSG_TEXT + " " + (i + 1));
                    System.out.println("PUBLISHER: Publishing message: " 
                        + message.getText());
                    topicPublisher.send(message);
                }

                // Send a non-text control message indicating end of messages.
                topicPublisher.send(session.createMessage());
                startindex = i;
            } catch (JMSException e) {
                System.out.println("Exception occurred: " + e.toString());
                exitResult = 1;
            }
        }
        
        /**
         * Closes the connection.
         */
        public void finish() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    exitResult = 1;
                }
            }
        }
    }
    
    /**
     * Instantiates the subscriber and publisher classes.
     *
     * Starts the subscriber; the publisher publishes some messages.
     *
     * Closes the subscriber; while it is closed, the publisher publishes
     * some more messages.
     *
     * Restarts the subscriber and fetches the messages.
     *
     * Finally, closes the connections.    
     */
    public void run_program() {
        DurableSubscriber  durableSubscriber = new DurableSubscriber();
        MultiplePublisher  multiplePublisher = new MultiplePublisher();

        durableSubscriber.startSubscriber();
        multiplePublisher.publishMessages();
        durableSubscriber.closeSubscriber();
        multiplePublisher.publishMessages();
        durableSubscriber.startSubscriber();
        durableSubscriber.closeSubscriber();
        multiplePublisher.finish();
        durableSubscriber.finish();
    }

    /**
     * Reads the topic name from the command line, then calls the
     * run_program method.
     *
     * @param args	the topic used by the example
     */
    public static void main(String[] args) {
        DurableSubscriberExample  dse = new DurableSubscriberExample();
        
        if (args.length != 1) {
    	    System.out.println("Usage: java DurableSubscriberExample <topic_name>");
    	    System.exit(1);
    	}
        dse.topicName = new String(args[0]);
        System.out.println("Topic name is " + dse.topicName);

    	dse.run_program();
    	SampleUtilities.exit(dse.exitResult);
    }
}
