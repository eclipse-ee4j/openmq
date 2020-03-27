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
 * The MessagePropertiesConsumer.class receives message from a queue
 * and check the message properties.
 * <p>
 * Run this program in conjunction with MessagePropertiesProducer.
 * Specify a queue name on the command line when you run
 * the program.
 */

public class MessagePropertiesConsumer {

	private String destName          = null;
	static int exitcode = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example
        */
        public static void main(String args[]) {

		if ( args.length < 1 ) {
                  System.out.println("Usage: java MessagePropertiesConsumer <queue_name> ");
                  System.exit(1);
                }

                MessagePropertiesConsumer msgPropertiesConsumer = new MessagePropertiesConsumer();
		msgPropertiesConsumer.parseArgs(args);

                try {
			//receive messages
                        msgPropertiesConsumer.runTest();
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
	 * Receive TextMessage and check the message properties that are set on message
	 * 
	 * @param  none
	 * @throws JMSException
	 */
	private void runTest() throws JMSException {

		String uniqueID = Long.toString(System.currentTimeMillis());
		String queueName = destName + uniqueID;

		// receive a message
		{
			ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			JMSContext context = connectionFactory.createContext();
			JMSConsumer consumer = context.createConsumer(context.createQueue(destName));
			TextMessage textMessage = (TextMessage) consumer.receive(1000);
                        String payload = textMessage.getText();

			if ( textMessage != null) {
                                System.out.println("Message Received : "+textMessage.getText());
                        } else {
                                System.out.println("Message not Received..");
                                exitcode=1;
                                return;
                        }

                       	// check message properties
                       	//

			// boolean 
			System.out.println( "booleanProp on Message through getObjectProperty :" + textMessage.getObjectProperty("booleanProp"));
			System.out.println( "booleanProp on Message through getBooleanProperty :" + textMessage.getBooleanProperty("booleanProp"));

			// byte
			System.out.println( "byteProp on Message through getObjectProperty :" + textMessage.getObjectProperty("byteProp"));
			System.out.println( "byteProp on Message through getBytesProperty :" + textMessage.getByteProperty("byteProp"));

			// short
			System.out.println( "shortProp on Message through getObjectProperty :" + textMessage.getObjectProperty("shortProp"));
                       	System.out.println( "shortProp on Message through getShortProperty :" + textMessage.getShortProperty("shortProp"));

			// int
                       	System.out.println( "intProp on Message through getObjectProperty :" + textMessage.getObjectProperty("intProp"));
                       	System.out.println( "intProp on Message through getIntProperty :" + textMessage.getIntProperty("intProp"));

			// long
                       	System.out.println( "longProp on Message through getObjectProperty :" + textMessage.getObjectProperty("longProp"));
                       	System.out.println( "longProp on Message through getLongProperty :" + textMessage.getLongProperty("longProp"));

			// float
                       	System.out.println( "floatProp on Message through getObjectProperty :" + textMessage.getObjectProperty("floatProp"));
                       	System.out.println( "floatProp on Message through getFloatProperty :" + textMessage.getFloatProperty("floatProp"));

			// double
			System.out.println( "doubleProp on Message through getObjectProperty :" +textMessage.getObjectProperty("doubleProp"));
                       	System.out.println( "doubleProp on Message through getDoubleProperty :" +textMessage.getDoubleProperty("doubleProp"));

			// String
			System.out.println( "stringProp on Message through getObjectProperty :" +textMessage.getObjectProperty("stringProp"));
                       	System.out.println( "stringProp on Message through getStringProperty :" +textMessage.getStringProperty("stringProp"));

			context.close();
		}


	}
			

}
