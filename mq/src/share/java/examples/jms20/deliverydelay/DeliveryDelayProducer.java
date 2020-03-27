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
import java.util.Calendar;

/**
 * The DeliveryDelayProducer.class sends messages to a topic with
 * delivery delay set on producer
 * <p>
 * Run this program in conjunction with DeliveryDelayConsumer.
 * Specify a topic name on the command line when you run
 * the program.  By default, the program sends one message.  Specify a number
 * after the topic name to send that number of messages.
 */

public class DeliveryDelayProducer {


	private String destName          = null;
        private int noOfMsgs;
        final String MSG_TEXT    = new String("Message");
	static int exitcode = 0;
	Calendar calendar; 

	/**
        * Main method.
        *
        * @param args      the topic used by the example and, optionally, the
        *                   number of messages to send
        */
	public static void main(String args[]) {

	       if ( (args.length < 1) || (args.length > 2) ) {
                  System.out.println("Usage: java DeliveryDelayProducer <topic_name> [<number_of_messages>]");
                  System.exit(1);
        	}

		// Send messages to topic with delivery delay set.
		DeliveryDelayProducer sendMsg = new DeliveryDelayProducer();
		sendMsg.parseArgs(args);
		try {
			// Send messages to topic
			sendMsg.sendmsgs();
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
         * Send messages to the topic destination with delivery delay set on producer
         *
         * @param  none
         * @throws JMSException
         */
	
	public void sendmsgs() throws JMSException {

		ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();

		// JMSContext will be closed automatically with the try-with-resources block
		try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE);) {
		    System.out.println("Created jms context successfully");

		    // Create topic
		    Topic topic = jmsContext.createTopic(destName);
		    System.out.println("Created topic successfully");

		    /*
         	    * Create producer.
		    * Set delivery delay on JMS producer
         	    * Create text message.
         	    * Send messages, varying text slightly.
         	    */
		    for (int i = 0; i < noOfMsgs; i++) {
                        String data = MSG_TEXT + " " + (i + 1);
			int delay = 10000 * (i +1);
		
                        //System.out.println("Sending message: " + data);
			JMSProducer producer = jmsContext.createProducer();

			// Set delivery delay on producer 
                        producer.setDeliveryDelay(delay).send(topic,data);
			System.out.println("Message "+ data + " sent with delayed delivery time "+producer.getDeliveryDelay()+" ms" );
            	    }
		}
	}
}
