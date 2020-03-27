/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.sql.*;
import java.util.*;
import jakarta.jms.*;

/**
 * The MessageHeadersTopic class demonstrates the use of message header fields.
 * <p>
 * The program contains a HeaderProducer class, a HeaderConsumer class, a
 * display_headers() method that is called by both classes, a main method, and 
 * a method that runs the consumer and producer threads.
 * <p>
 * The producing class sends three messages, and the consuming class
 * receives them.  The program displays the message headers just before the 
 * send call and just after the receive so that you can see which ones are
 * set by the send method.
 * <p>
 * Specify a topic name on the command line when you run the program.  The 
 * program also uses a queue named "controlQueue", which should be created  
 * before you run the program.
 */
public class MessageHeadersTopic {
    final String  CONTROL_QUEUE = "controlQueue";
    String        topicName = null;
    int           exitResult = 0;

    /**
     * The HeaderProducer class sends three messages, setting the JMSType  
     * message header field, one of three header fields that are not set by 
     * the send method.  (The others, JMSCorrelationID and JMSReplyTo, are 
     * demonstrated in the RequestReplyQueue example.)  It also sets a 
     * client property, "messageNumber". 
     *
     * The displayHeaders method is called just before the send method.
     */
    public class HeaderProducer extends Thread {

        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory       connectionFactory = null;
            jakarta.jms.Connection    connection = null;
            Session                 session = null;
            Topic                   topic = null;
            MessageProducer         msgProducer = null;
            TextMessage             message = null;
            final String            MSG_TEXT = new String("Read My Headers");

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
            
            try {
                /*
                 * Synchronize with consumer.  Wait for message indicating 
                 * that consumer is ready to receive messages.
                 */
                try {
                    SampleUtilities.receiveSynchronizeMessages("PRODUCER THREAD: ",
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

                // Create producer.
                msgProducer = session.createProducer(topic);
                
                // First message: no-argument form of send method
                message = session.createTextMessage();
                message.setJMSType("Simple");
                System.out.println("PRODUCER THREAD: Setting JMSType to " 
                    + message.getJMSType());
                message.setIntProperty("messageNumber", 1);
                System.out.println("PRODUCER THREAD: Setting client property messageNumber to " 
                    + message.getIntProperty("messageNumber"));
                message.setText(MSG_TEXT);
                System.out.println("PRODUCER THREAD: Setting message text to: " 
                    + message.getText());
                System.out.println("PRODUCER THREAD: Headers before message is sent:");
                displayHeaders(message, "PRODUCER THREAD: ");
                msgProducer.send(message);

                /* 
                 * Second message: 3-argument form of send method;
                 * explicit setting of delivery mode, priority, and
                 * expiration
                 */
                message = session.createTextMessage();
                message.setJMSType("Less Simple");
                System.out.println("\nPRODUCER THREAD: Setting JMSType to " 
                    + message.getJMSType());
                message.setIntProperty("messageNumber", 2);
                System.out.println("PRODUCER THREAD: Setting client property messageNumber to " 
                    + message.getIntProperty("messageNumber"));
                message.setText(MSG_TEXT + " Again");
                System.out.println("PRODUCER THREAD: Setting message text to: " 
                    + message.getText());
                displayHeaders(message, "PRODUCER THREAD: ");
                msgProducer.send(message, DeliveryMode.NON_PERSISTENT,
                    3, 10000);
                
                /* 
                 * Third message: no-argument form of send method,
                 * MessageID and Timestamp disabled
                 */
                message = session.createTextMessage();
                message.setJMSType("Disable Test");
                System.out.println("\nPRODUCER THREAD: Setting JMSType to " 
                    + message.getJMSType());
                message.setIntProperty("messageNumber", 3);
                System.out.println("PRODUCER THREAD: Setting client property messageNumber to " 
                    + message.getIntProperty("messageNumber"));
                message.setText(MSG_TEXT
                    + " with MessageID and Timestamp disabled");
                System.out.println("PRODUCER THREAD: Setting message text to: " 
                    + message.getText());
                msgProducer.setDisableMessageID(true);
                msgProducer.setDisableMessageTimestamp(true);
                System.out.println("PRODUCER THREAD: Disabling Message ID and Timestamp");
                displayHeaders(message, "PRODUCER THREAD: ");
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
     * The HeaderConsumer class receives the three messages and calls the
     * displayHeaders method to show how the send method changed the
     * header values.
     * <p>
     * The first message, in which no fields were set explicitly by the send 
     * method, shows the default values of these fields.
     * <p>
     * The second message shows the values set explicitly by the send method.
     * <p>
     * The third message shows whether disabling the MessageID and Timestamp 
     * has any effect in the current JMS implementation.
     */
    public class HeaderConsumer extends Thread {
 
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory       connectionFactory = null;
            jakarta.jms.Connection    connection = null;
            Session                 session = null;
            Topic                   topic = null;
            MessageConsumer         msgConsumer = null;
            final boolean           NOLOCAL = true;
            TextMessage             message = null;

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
             * Create consumer and start message delivery.
             * Send synchronize message to producer.
             * Receive the three messages.
             * Call the displayHeaders method to display the message headers.
             */
            try {
                msgConsumer = 
                    session.createConsumer(topic, null, NOLOCAL);
                connection.start();

                // Let producer know that consumer is ready.
                try {
                    SampleUtilities.sendSynchronizeMessage("CONSUMER THREAD: ",
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

                for (int i = 0; i < 3; i++) {
                    message = (TextMessage) msgConsumer.receive();
                    System.out.println("\nCONSUMER THREAD: Message received: " 
                        + message.getText());
                    System.out.println("CONSUMER THREAD: Headers after message is received:");
                    displayHeaders(message, "CONSUMER THREAD: ");
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
        }
    }
    
    /**
     * Displays all message headers.  Each display is in a try/catch block in
     * case the header is not set before the message is sent.
     *
     * @param message	the message whose headers are to be displayed
     * @param prefix	the prefix (producer or consumer) to be displayed
     */
    public void displayHeaders (Message message, String prefix) {
        Destination  dest = null;      
        int          delMode = 0;
        long         expiration = 0;
        Time         expTime = null;
        int          priority = 0;
        String       msgID = null;
        long         timestamp = 0;
        Time         timestampTime = null;
        String       correlID = null;
        Destination  replyTo = null;
        boolean      redelivered = false;
        String       type = null;
        String       propertyName = null;
        
        try {
            System.out.println(prefix + "Headers set by send method: ");
            
            // Display the destination (topic, in this case).
            try {
                dest = message.getJMSDestination();
                System.out.println(prefix + " JMSDestination: " + dest);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the delivery mode.
            try {
                delMode = message.getJMSDeliveryMode();
                System.out.print(prefix);
                if (delMode == DeliveryMode.NON_PERSISTENT) {
                    System.out.println(" JMSDeliveryMode: non-persistent");
                } else if (delMode == DeliveryMode.PERSISTENT) {
                    System.out.println(" JMSDeliveryMode: persistent");
                } else {
                    System.out.println(" JMSDeliveryMode: neither persistent nor non-persistent; error");
                }
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            /*
             * Display the expiration time.  If value is 0 (the default),
             * the message never expires.  Otherwise, cast the value
             * to a Time object for display.
             */
            try {
                expiration = message.getJMSExpiration();
                System.out.print(prefix);
                if (expiration != 0) {
                    expTime = new Time(expiration);
                    System.out.println(" JMSExpiration: " + expTime);
                } else {
                    System.out.println(" JMSExpiration: " + expiration);
                }
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the priority.
            try {
                priority = message.getJMSPriority();
                System.out.println(prefix + " JMSPriority: " + priority);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the message ID.
            try {
                msgID = message.getJMSMessageID();
                System.out.println(prefix + " JMSMessageID: " + msgID);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            /*
             * Display the timestamp.
             * If value is not 0, cast it to a Time object for display.
             */
            try {
                timestamp = message.getJMSTimestamp();
                System.out.print(prefix);
                if (timestamp != 0) {
                    timestampTime = new Time(timestamp);
                    System.out.println(" JMSTimestamp: " + timestampTime);
                } else {
                    System.out.println(" JMSTimestamp: " + timestamp);
                }
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the correlation ID.
            try {
                correlID = message.getJMSCorrelationID();
                System.out.println(prefix + " JMSCorrelationID: " + correlID);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
           // Display the ReplyTo destination.
           try {
                replyTo = message.getJMSReplyTo();
                System.out.println(prefix + " JMSReplyTo: " + replyTo);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the Redelivered value (usually false).
            System.out.println(prefix + "Header set by JMS provider:");
            try {
                redelivered = message.getJMSRedelivered();
                System.out.println(prefix + " JMSRedelivered: " + redelivered);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display the JMSType.
            System.out.println(prefix + "Headers set by client program:");
            try {
                type = message.getJMSType();
                System.out.println(prefix + " JMSType: " + type);
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
            
            // Display any client properties.
            try {
                for (Enumeration e = message.getPropertyNames(); e.hasMoreElements() ;) {
                    propertyName = new String((String) e.nextElement());
                    System.out.println(prefix + " Client property " 
                        + propertyName + ": " 
                        + message.getObjectProperty(propertyName)); 
                }
            } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
                exitResult = 1;
            }
        } catch (Exception e) {
                System.out.println(prefix + "Exception occurred: " 
                    + e.toString());
            exitResult = 1;
        }
    }

    /**
     * Instantiates the consumer and producer classes and starts their
     * threads.
     * Calls the join method to wait for the threads to die.
     * <p>
     * It is essential to start the consumer before starting the producer.
     * In the send/subscribe model, a consumer can ordinarily receive only 
     * messages sent while it is active.
     */
    public void run_threads() {
        HeaderConsumer   headerConsumer = new HeaderConsumer();
        HeaderProducer   headerProducer = new HeaderProducer();

        headerConsumer.start();
        headerProducer.start();
        try {
            headerConsumer.join();
            headerProducer.join();
        } catch (InterruptedException e) {}
    }
    
    /**
     * Reads the topic name from the command line, then calls the
     * run_threads method to execute the program threads.
     *
     * @param args	the topic used by the example
     */
    public static void main(String[] args) {
        MessageHeadersTopic  mht = new MessageHeadersTopic();

        if (args.length != 1) {
    	    System.out.println("Usage: java MessageHeadersTopic <topic_name>");
    	    System.exit(1);
    	}
        mht.topicName = new String(args[0]);
        System.out.println("Topic name is " + mht.topicName);

    	mht.run_threads();
    	SampleUtilities.exit(mht.exitResult); 
    }
}
