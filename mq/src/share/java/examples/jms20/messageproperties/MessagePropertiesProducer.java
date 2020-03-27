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
 * The MessagePropertiesProducer.class sends messages to a queue with
 * various types of message properties set on producer
 * <p>
 * Run this program in conjunction with MessagePropertiesConsumer.
 * Specify a queue name on the command line when you run
 * the program.
 */

public class MessagePropertiesProducer {

	private String destName          = null;
	static int exitcode = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example
        */
        public static void main(String args[]) {

		if ( args.length < 1 ) {
                  System.out.println("Usage: java MessagePropertiesProducer <queue_name> ");
                  System.exit(1);
                }

		// Send messages to queue with message properties set.
                MessagePropertiesProducer msgPropertiesProducer = new MessagePropertiesProducer();
		msgPropertiesProducer.parseArgs(args);

                try {
                        msgPropertiesProducer.runTest();
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
	 * JMSProducer method send(Destination destination, Message message),
	 * with a TextMessage ensuring that you can set message properties
	 * 
	 * @param  none
	 * @throws JMSException
	 */
	private void runTest() throws JMSException {

		String uniqueID = Long.toString(System.currentTimeMillis());

		boolean booleanVal = true;
		byte byteVal = 7;
		short shortVal = 123;
		int intVal = 1357924680;
		long longVal = 84838481357924680L;
		float floatVal = 3.1415926535f;
		double doubleVal = 2.71828182846d;
		String stringVal = "Hello";

		// send a message
		{
			ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			JMSContext context = connectionFactory.createContext();
			JMSProducer producer = context.createProducer();
			System.out.println("Set properties on producer");
			// set properties

			// boolean 
			System.out.println("Set boolean property on producer");
			producer.setProperty("booleanProp", booleanVal);
			System.out.println( "booleanProp on producer through getObjectProperty :" + producer.getObjectProperty("booleanProp"));
			System.out.println( "booleanProp on producer through getBooleanProperty :" + producer.getBooleanProperty("booleanProp"));

			// byte
			System.out.println("Set byte property on producer");
			producer.setProperty("byteProp", byteVal);
			System.out.println( "byteProp on producer through getObjectProperty :" +producer.getObjectProperty("byteProp"));
			System.out.println( "byteProp on producer through getBytesProperty :" +producer.getByteProperty("byteProp"));

			// short
			System.out.println("Set short property on producer");
			producer.setProperty("shortProp", shortVal);
			System.out.println( "shortProp on producer through getObjectProperty :" +producer.getObjectProperty("shortProp"));
                        System.out.println( "shortProp on producer through getShortProperty :" +producer.getShortProperty("shortProp"));

			// int
			System.out.println("Set int property on producer");
			producer.setProperty("intProp", intVal);
                        System.out.println( "intProp on producer through getObjectProperty :" +producer.getObjectProperty("intProp"));
                        System.out.println( "intProp on producer through getIntProperty :" +producer.getIntProperty("intProp"));

			// long
			System.out.println("Set long property on producer");
                        producer.setProperty("longProp", longVal);
                        System.out.println( "longProp on producer through getObjectProperty :" +producer.getObjectProperty("longProp"));
                        System.out.println( "longProp on producer through getLongProperty :" +producer.getLongProperty("longProp"));

			// float
			System.out.println("Set long property on producer");
			producer.setProperty("floatProp", floatVal);
                        System.out.println( "floatProp on producer through getObjectProperty :" +producer.getObjectProperty("floatProp"));
                        System.out.println( "floatProp on producer through getFloatProperty :" +producer.getFloatProperty("floatProp"));

			// double
			System.out.println("Set double property on producer");
			producer.setProperty("doubleProp", doubleVal);
			System.out.println( "doubleProp on producer through getObjectProperty :" +producer.getObjectProperty("doubleProp"));
                        System.out.println( "doubleProp on producer through getDoubleProperty :" +producer.getDoubleProperty("doubleProp"));

			// String
			System.out.println("Set String property on producer");
			producer.setProperty("stringProp", stringVal);
			System.out.println( "stringProp on producer through getObjectProperty :" +producer.getObjectProperty("stringProp"));
                        System.out.println( "stringProp on producer through getStringProperty :" +producer.getStringProperty("stringProp"));

			// now send message
			producer.send(context.createQueue(destName),context.createTextMessage(uniqueID));
			System.out.println("Message "+uniqueID+" sent successfully");
			context.close();
		}


	}
			

}
