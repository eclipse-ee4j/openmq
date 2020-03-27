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
 * The MessageHeadersProducer.class sends messages to a queue with
 * message header properties set on producer
 * <p>
 * Run this program in conjunction with MessageHeadersConsumer.
 * Specify a queue name on the command line when you run
 * the program.
 */

public class MessageHeadersProducer {

	private String destName          = null;
	static int exitcode = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example
        */
        public static void main(String args[]) {

		if ( args.length < 1 ) {
                  System.out.println("Usage: java MessageHeadersProducer <queue_name> ");
                  System.exit(1);
                }

		// Send messages to queue with message header properties set.
                MessageHeadersProducer msgHeadersProducer = new MessageHeadersProducer();
		msgHeadersProducer.parseArgs(args);

                try {
                        msgHeadersProducer.runTest();
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


	/**
	 * Test JMSProducer method send(Destination destination, Message message),
         * with a TextMessage ensuring that you can set the four supported message
         * headers
	 * 
	 * @param  none
	 * @throws JMSException
	 */
	private void runTest() throws JMSException {

		String uniqueID = Long.toString(System.currentTimeMillis());

		byte[] jmsCorrelationIDAsBytes = { 77, 121, 67, 111, 114, 114, 101, 108, 97, 116, 105, 111, 110, 73, 68 };
                String jmsCorrelationIDAsString = "MyCorrelationID";
                String jmsType = "MyJMSType";
                String jmsReplyTo = "SomeOtherQueue";

		// send a message
		{
			ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			JMSContext context = connectionFactory.createContext();
			JMSProducer producer = context.createProducer();
			System.out.println("Set message headers on producer");
			// setJMSReplyTo
			System.out.println("setJMSReplyTo on producer");
			producer.setJMSReplyTo(context.createQueue(jmsReplyTo));
			// setJMSType
			System.out.println("setJMSType on producer");
                        producer.setJMSType(jmsType);
			// setJMSCorrelationID 
			System.out.println("setJMSCorrelationID on producer");
			producer.setJMSCorrelationID(jmsCorrelationIDAsString);
			// setJMSCorrelationIDAsBytes
			System.out.println("setJMSCorrelationIDAsBytes on producer");
                        producer.setJMSCorrelationIDAsBytes(jmsCorrelationIDAsBytes);

			// send message
			producer.send(context.createQueue(destName),context.createTextMessage(uniqueID));
			System.out.println("Message "+uniqueID+" sent successfully");
			context.close();
		}


	}
			

}
