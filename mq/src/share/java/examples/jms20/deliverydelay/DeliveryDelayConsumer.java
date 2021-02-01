/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.jms.*;
import java.util.concurrent.CountDownLatch;

/**
 * The DeliveryDelayConsumer.class receives messages from
 * a topic and checks the delivery time.
 * <p>
 * Run this program in conjunction with DeliveryDelayProducer
 * Specify a topic name on the command line when you run
 * the program. By default, the program receives one message. Specify a number
 * after the topic name to receive that number of messages.
 */

public class DeliveryDelayConsumer {

	private String destName          = null;
        private int noOfMsgs;
	static int exitcode              = 0;

	 /**
        * Main method.
        *
        * @param args      the topic used by the example and, optionally, the
        *                   number of messages to receive
        */
	public static void main(String args[]) {


		 if ( (args.length < 1) || (args.length > 2) ) {
                  System.out.println("Usage: java DeliveryDelayConsumer <topic_name> [<number_of_messages>]");
                  System.exit(1);
                }

		DeliveryDelayConsumer recvMsg = new DeliveryDelayConsumer();
		recvMsg.parseArgs(args);
		try {
			// Receive messages from topic and check delivery time.
			recvMsg.receivemsgs();
		}catch(JMSException ex) {
		 	ex.printStackTrace();
			exitcode = 1;
		}		
		System.exit(exitcode);
	}

	/**
        * parseArgs method.
        *
        * @param args  the arguments that are passed through main method
        */

	public void parseArgs(String[] args){

                destName = new String(args[0]);
                System.out.println("Topic name is " + destName);
                if (args.length == 2){
                   noOfMsgs = (new Integer(args[1])).intValue();
                } else {
                   noOfMsgs = 1;
                }

        }

	 /**
         * Receive messages from the topic destination and check the delivery time
         *
         * @param  none
         * @throws JMSException
         */
	
	public void receivemsgs() throws JMSException {

		// Create connection factory for the MQ client
		ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();

		// JMSContext will be closed automatically with the try-with-resources block
		try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE);) {
                    System.out.println("Created jms context successfully");
	
		    // Create topic
		    Topic topic = jmsContext.createTopic(destName);
		    System.out.println("Created topic successfully");

		    // Create consumer
		    JMSConsumer consumer = jmsContext.createConsumer(topic);
		    MessageHandler listener = new MessageHandler(noOfMsgs);
		    
		    // Set message listener on consumer
		    consumer.setMessageListener(listener);
		    jmsContext.start();
		    listener.await();
		}

	}

	/**
         * The MessageHandler class implements the MessageListener interface by
         * defining an onMessage method for the DeliveryDelayConsumer class.
         */

	static class MessageHandler implements jakarta.jms.MessageListener {

		private CountDownLatch countDownLatch;
		private int msgsRecv;
		private int totalMsgs;

		MessageHandler(int noOfMsgs) {
		   totalMsgs = noOfMsgs;
		   countDownLatch = new CountDownLatch(1);
		}
		
		 /**
                * Casts the message to a TextMessage and displays its text.
                * The message listener sets its CounDownLatch to all
                * done processing messages.
                *
                * @param message   the incoming message
                */

	        public void onMessage(Message message) {

		   //Receive messages
		   TextMessage  msg = (TextMessage) message;
		   try {
		       if(msg != null) {
			  msgsRecv ++;

			  // Get message body as String
			  String body = msg.getBody(String.class);

			  // Get delivery time of message
			  long deliveryTime = msg.getJMSDeliveryTime();
			  System.out.println("Message "+body+ " received with delivery time "+deliveryTime+ " ms");
		       }
		       if ( msgsRecv == totalMsgs) { 
	                 countDownLatch.countDown();
		       }
		   } catch( JMSException ex) {
                       System.out.println("Exception in onMessage(): " + ex.toString());
			countDownLatch.countDown();
		   }

		}	
		
		public void await() {
                        try {
                                countDownLatch.await();
                        } catch (InterruptedException e) {
                                e.printStackTrace();
                        }
                }

	}

}
