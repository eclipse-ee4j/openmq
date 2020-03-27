/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.util.*;
import jakarta.jms.*;

/**
 * The TopicSelectors class demonstrates the use of multiple 
 * subscribers and message selectors.
 * <p>
 * The program contains a Publisher class, a Subscriber class with a listener 
 * class, a main method, and a method that runs the subscriber and publisher
 * threads.
 * <p>
 * The Publisher class publishes 30 messages of 6 different types, randomly
 * selected, then publishes a "Finished" message.  The program creates four
 * instances of the Subscriber class, one for each of three types and one that 
 * listens for the "Finished" message.  Each subscriber instance uses a 
 * different message selector to fetch messages of only one type.  The publisher
 * displays the messages it sends, and the listener displays the messages that
 * the subscribers receive.  Because all the objects run in threads, the
 * displays are interspersed when the program runs.
 * <p>
 * Specify a topic name on the command line when you run the program.  The 
 * program also uses a queue named "controlQueue", which should be created  
 * before you run the program.
 */
public class TopicSelectors {
    final String         CONTROL_QUEUE = "controlQueue";
    String               topicName = null;
    static final String  MESSAGE_TYPES[] = 
                             {"Nation/World", "Metro/Region", "Business",
                              "Sports", "Living/Arts", "Opinion",
                               // always last type
                              "Finished"
                             };
    static final String  END_OF_MESSAGE_STREAM_TYPE = 
                             MESSAGE_TYPES[MESSAGE_TYPES.length-1];
    int                  exitResult = 0;


    /**
     * The Publisher class publishes a number of messages.  For each, it
     * randomly chooses a message type.  It creates a message and sets its
     * text to a message that indicates the message type.
     * It also sets the client property NewsType, which the Subscriber
     * objects use as the message selector.
     * After a pause to allow the subscribers to get all the messages, the
     * publisher sends a final message with a NewsType of "Finished", which 
     * signals the end of the messages.
     */
    public class Publisher extends Thread {
        final int  NUM_SUBSCRIBERS;
        final int  ARRSIZE = 6;
        
        public Publisher(int numSubscribers) {
            NUM_SUBSCRIBERS = numSubscribers;
        }

        /**
         * Chooses a message type by using the random number generator
         * found in java.util.
         *
         * @return	the String representing the message type
         */
        public String chooseType() {
           int     whichMsg;
           Random  rgen = new Random();
           
           whichMsg = rgen.nextInt(ARRSIZE);
           return MESSAGE_TYPES[whichMsg];
        }
        
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Topic                topic = null;
            MessageProducer      msgProducer = null;
            TextMessage          message = null;
            int                  numMsgs = ARRSIZE * 5;
            String               messageType = null;

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
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
            
            /*
             * After synchronizing with subscriber, create publisher.
             * Create and send news messages.
             * Send end-of-messages message.
             */
            try {
                /*
                 * Synchronize with subscribers.  Wait for messages indicating 
                 * that all subscribers are ready to receive messages.
                 */
                try {
                    SampleUtilities.receiveSynchronizeMessages("PUBLISHER THREAD: ",
                                                               CONTROL_QUEUE, 
                                                               NUM_SUBSCRIBERS);
                } catch (Exception e) {
                    System.out.println("Queue probably missing: " + e.toString());
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (JMSException ee) {}
                    }
    	            System.exit(1);
    	        }

                msgProducer = session.createProducer(topic);
                message = session.createTextMessage();
                for (int i = 0; i < numMsgs; i++) {
                    messageType = chooseType();
                    message.setStringProperty("NewsType", messageType);
                    message.setText("Item " + i + ": " + messageType);
                    System.out.println("PUBLISHER THREAD: Setting message text to: " 
                        + message.getText());
                    msgProducer.send(message);
                }
                
                message.setStringProperty("NewsType", END_OF_MESSAGE_STREAM_TYPE);
                message.setText("That's all the news for today.");
                System.out.println("PUBLISHER THREAD: Setting message text to: " 
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
     * Each instance of the Subscriber class creates a subscriber that uses
     * a message selector that is based on the string passed to its 
     * constructor.
     * It registers its message listener, then starts listening
     * for messages.  It does not exit until the message listener sets the 
     * variable done to true, which happens when the listener gets the last
     * message.
     */
    public class Subscriber extends Thread {
        String                  whatKind;
        int                     subscriberNumber;

        /**
         * The MultipleListener class implements the MessageListener interface  
         * by defining an onMessage method for the Subscriber class.
         */
        private class MultipleListener implements MessageListener {
            final SampleUtilities.DoneLatch  monitor =
                new SampleUtilities.DoneLatch();

            /**
             * Displays the message text.
             * If the value of the NewsType property is "Finished", the message 
             * listener sets its monitor state to all done processing messages.
             *
             * @param inMessage	the incoming message
             */
            public void onMessage(Message inMessage) {
                TextMessage  msg = (TextMessage) inMessage;
                String       newsType;

                try {
                    System.out.println("SUBSCRIBER " + subscriberNumber 
                                       + " THREAD: Message received: " 
                                       + msg.getText());
                    newsType = msg.getStringProperty("NewsType");
                    if (newsType.equals(TopicSelectors.END_OF_MESSAGE_STREAM_TYPE)) {
                        System.out.println("SUBSCRIBER " + subscriberNumber 
                             + " THREAD: Received finished-publishing message");
                        monitor.allDone();
                    }
                } catch(JMSException e) {
                    System.out.println("Exception in onMessage(): " 
                                       + e.toString());
                }
            }
        }
        
        /**
         * Constructor.  Sets whatKind to indicate the type of
         * message this Subscriber object will listen for; sets
         * subscriberNumber based on Subscriber array index.
         *
         * @param str	a String from the MESSAGE_TYPES array
         * @param num	the index of the Subscriber array
         */
        public Subscriber(String str, int num) {
            whatKind = str;
            subscriberNumber = num + 1;
        }
 
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
            Connection           connection = null;
            Session              session = null;
            Topic                topic = null;
            String               selector = null;
            MessageConsumer      msgConsumer = null;
            MultipleListener     multipleListener = new MultipleListener();

            try {
                connectionFactory = 
                    SampleUtilities.getConnectionFactory();
                connection = 
                    connectionFactory.createConnection();
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
            
            /*
             * Create subscriber with message selector.
             * Start message delivery.
             * Send synchronize message to publisher, then wait till all
             * messages have arrived.
             * Listener displays the messages obtained.
             */
            try {
                selector = new String("NewsType = '" + whatKind + "'" + 
                                      " OR NewsType = '" + END_OF_MESSAGE_STREAM_TYPE + "'");
                System.out.println("SUBSCRIBER " + subscriberNumber 
                                    + " THREAD: selector is \"" + selector + "\"");
                msgConsumer = 
                    session.createConsumer(topic, selector, false);
                msgConsumer.setMessageListener(multipleListener);
                connection.start();
                
                // Let publisher know that subscriber is ready.
                try {
                    SampleUtilities.sendSynchronizeMessage("SUBSCRIBER " 
                                                            + subscriberNumber 
                                                            + " THREAD: ", 
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
                 * Asynchronously process appropriate news messages.
                 * Block until publisher issues a finished message.
                 */
                multipleListener.monitor.waitTillDone();
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
     * Creates an array of Subscriber objects, one for each of three message  
     * types including the Finished type, and starts their threads.
     * Creates a Publisher object and starts its thread.
     * Calls the join method to wait for the threads to die.
     */
    public void run_threads() {
        final       int NUM_SUBSCRIBERS = 3;
        Subscriber  subscriberArray[] = new Subscriber[NUM_SUBSCRIBERS];
        Publisher   publisher = new Publisher(NUM_SUBSCRIBERS);

        subscriberArray[0] = new Subscriber(MESSAGE_TYPES[2], 0);
        subscriberArray[0].start();        
        subscriberArray[1] = new Subscriber(MESSAGE_TYPES[3], 1);
        subscriberArray[1].start();
        subscriberArray[2] = new Subscriber(MESSAGE_TYPES[4], 2);
        subscriberArray[2].start();    
        publisher.start();

        
        for (int i = 0; i < subscriberArray.length; i++) {
            try {
                subscriberArray[i].join();
            } catch (InterruptedException e) {}
        }
        
        try {
            publisher.join();
        } catch (InterruptedException e) {}
    }
    
    /**
     * Reads the topic name from the command line, then calls the
     * run_threads method to execute the program threads.
     *
     * @param args	the topic used by the example
     */
    public static void main(String[] args) {
        TopicSelectors  ts = new TopicSelectors();

        if (args.length != 1) {
    	    System.out.println("Usage: java TopicSelectors <topic_name>");
    	    System.exit(1);
    	}
    	
        ts.topicName = new String(args[0]);
        System.out.println("Topic name is " + ts.topicName);

    	ts.run_threads();
    	SampleUtilities.exit(ts.exitResult);
    }
}
