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
import com.sun.messaging.jmq.jmsclient.ConnectionMetaDataImpl;

/**
 * The JMSXDeliveryCountExample2.class checks the JMSXDeliverycount on
 * redelivered transacted message.
 * Specify a queue name on the command line when you run
 * the program.
 *
 */

public class JMSXDeliveryCountExample2 {

        private String destName          = null;
        static int exitcode = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example
        */
        public static void main(String args[]) {

		if ( args.length < 1 ) {
                  System.out.println("Usage: java JMSXDeliveryCountExample1 <queue_name>");
                  System.exit(1);
                }
                JMSXDeliveryCountExample2 deliveryCountExample2 = new JMSXDeliveryCountExample2();
		deliveryCountExample2.parseArgs(args);
                try {
                        deliveryCountExample2.runTest();
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
                System.out.println("Queue name is " + destName);
        }


    	/*
     	* Receive msg with in txn
     	* Rollback txn but don't close the connection
     	* Receive the redelivered msg again.
     	*
     	* @param  none
     	* @throws JMSException
     	*/
    	protected void runTest() throws JMSException {
		ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
        	String uniqueID = Long.toString(System.currentTimeMillis());

        	// send 1 messages to the queue
        	JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
        	JMSProducer producer = context.createProducer();
        	producer.send(context.createQueue(destName), context.createTextMessage(uniqueID));
		System.out.println("Sent message to queue ");
		context.commit();
		context.close();

        	// receive 1 message from the queue and rollback
	
		context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
		context.start();
        	JMSConsumer consumer = context.createConsumer(context.createQueue(destName));
        	TextMessage textMessage = (TextMessage) consumer.receive(10000);
		if ( textMessage != null) {
			System.out.println("Message Received..");
		} else {
			System.out.println("Message not Received..");
			exitcode=1;
			return;
		}
	     
        	String payload = textMessage.getText();
	
        	System.out.println("Message received : "+payload);

		// Check the JMSDeliveryCount for the message
        	int deliveryCount = textMessage.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
        	System.out.println("JMSXDeliveryCount for the received message : " + deliveryCount);
        	context.rollback();
		System.out.println("Rollback the transaction");

		System.out.println("Try to receive the Message again");
        	textMessage = (TextMessage) consumer.receive(10000);
        	payload = textMessage.getText();
        	System.out.println("Message redelivered : " + payload);

		// Check the JMSXDeliveryCount for the redelivered message
        	deliveryCount = textMessage.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
        	System.out.println("JMSXDeliveryCount for the redelivered message : " +deliveryCount);

		// Commit the transaction
        	context.commit();

        	context.close();
    	}

}
