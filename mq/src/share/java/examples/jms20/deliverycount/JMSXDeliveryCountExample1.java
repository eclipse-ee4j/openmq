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
 * The JMSXDeliveryCountExample1.class checks the JMSXDeliverycount on
 * redelivered msg with CLIENT_ACKNOWLDEGE.
 * Specify a queue name on the command line when you run
 * the program.
 *
 */

public class JMSXDeliveryCountExample1 {

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
                JMSXDeliveryCountExample1 deliveryCountExample1 = new JMSXDeliveryCountExample1();
		deliveryCountExample1.parseArgs(args);
                try {
                        deliveryCountExample1.runTest();
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
     	* Receive msg with CLIENT_ACKNOWLEDGE
     	* Close the connection without ack
     	* Receive the redelivered msg again.
	*
	* @param  none
        * @throws JMSException
     	*/
    	protected void runTest() throws JMSException {
	
		ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
	        String uniqueID = Long.toString(System.currentTimeMillis());

        	// send 1 messages to the queue
        	JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
		context.acknowledge();
        	JMSProducer producer = context.createProducer();
        	producer.send(context.createQueue(destName), context.createTextMessage(uniqueID));
		System.out.println("Sent message to the Queue");
        	context.close();

        	// receive 1 message from the queue
		context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
		context.start();
        	JMSConsumer consumer = context.createConsumer(context.createQueue(destName));
        	TextMessage textMessage = (TextMessage) consumer.receive(10000);

		if ( textMessage != null) {
               		System.out.println("Message received..");
		} else {	
               		System.out.println("Message not received..");
               		exitcode=1;
               		return;
        	}

        	String payload = textMessage.getText();
		System.out.println("Message received : "+payload);

		// Check the JMSDeliveryCount for the message
        	int deliveryCount = textMessage.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
        	System.out.println("JMSXDeliveryCount for the received message : "+deliveryCount);
        	context.close();
		System.out.println("Close the context without ack");

        	// receive the message from queue again
		context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
        	context.start();
        	consumer = context.createConsumer(context.createQueue(destName));
        	textMessage = (TextMessage) consumer.receive(10000);
		if ( textMessage != null) {
               		System.out.println("Received the redelivered msg ..");
        	} else {
               		System.out.println("Message not redelivered ..");
               		exitcode=1;
               		return;
        	}
        	payload = textMessage.getText();
        	System.out.println("Message Redelivered : "+payload);

		// Check the JMSXDeliveryCount for the redelivered message
        	deliveryCount = textMessage.getIntProperty(ConnectionMetaDataImpl.JMSXDeliveryCount);
        	System.out.println("JMSXDeliveryCount for the redelivered message : "+deliveryCount);
		context.acknowledge();
		context.close();
    }

}
