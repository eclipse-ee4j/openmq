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
 * The MessageHeadersConsumer.class receives message from a queue
 * and check the message headers.
 * <p>
 * Run this program in conjunction with MessageHeadersProducer.
 * Specify a queue name on the command line when you run
 * the program.
 */

public class MessageHeadersConsumer {

	private String destName          = null;
	static int exitcode = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example
        */
        public static void main(String args[]) {

		if ( args.length < 1 ) {
                  System.out.println("Usage: java MessageHeadersConsumer <queue_name> ");
                  System.exit(1);
                }

		// receive messages
                MessageHeadersConsumer msgHeadersConsumer = new MessageHeadersConsumer();
		msgHeadersConsumer.parseArgs(args);

                try {
                        msgHeadersConsumer.runTest();
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
	 * Receive TextMessage and check the message headers that are set on message
	 * 
	 * @param  none
	 * @throws JMSException
	 */
	private void runTest() throws JMSException {

		// receive a message
		{
			ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			JMSContext context = connectionFactory.createContext();
			JMSConsumer consumer = context.createConsumer(context.createQueue(destName));
			TextMessage textMessage = (TextMessage) consumer.receive(1000);
		
			if ( textMessage != null) {
				System.out.println("Message Received : "+textMessage.getText());
			} else {
				System.out.println("Message not Received..");
                        	exitcode=1;
                        	return;
                	}
                       	// check message header
                       	//

			// check JMSType message header
			System.out.println("getJMSType : "+textMessage.getJMSType());
			// check JMSReplyTo mesage header
			System.out.println("getJMSReplyTo : "+textMessage.getJMSReplyTo());
			// check getJMSCorrelationIDAsBytes message header
			byte[] jmsCorrelationIdAsBytesRead = textMessage.getJMSCorrelationIDAsBytes();
			System.out.println("getJMSCorrelationIdAsBytes length : "+ jmsCorrelationIdAsBytesRead.length);
                       	for (int i = 0; i < jmsCorrelationIdAsBytesRead.length; i++) {
                        	System.out.println("getJMSCorrelationIdAsBytes[" + i + "] :" + jmsCorrelationIdAsBytesRead[i]);
                       	}
		
			// check getCorrelationID
			String jmsCorrelationIdAsStringRead = textMessage.getJMSCorrelationID();
                        System.out.println("getJMSCorrelationID : "+jmsCorrelationIdAsStringRead);

			context.close();
		}


	}
			

}
