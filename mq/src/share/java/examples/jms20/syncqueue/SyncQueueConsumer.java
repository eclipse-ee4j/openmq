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
import java.io.Serializable;

/**
 * The SyncQueueConsumer.class receives messages from
 * a queue using synchronous message delivery.
 * <p>
 * Run this program in conjunction with SendObjectMsgsToQueue.
 * Specify a queue name on the command line when you run
 * the program. By default, the program receives one message. Specify a number
 * after the queue name to receive that number of messages.
 */

public class SyncQueueConsumer {

	private String destName          = null;
        private int noOfMsgs;
	static int exitcode		 = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example and, optionally, the
        *                   number of messages to receive
        */
	public static void main(String args[]) {

		 if ( (args.length < 1) || (args.length > 2) ) {
                  System.out.println("Usage: java SyncQueueConsumer <queue_name> [<number_of_messages>]");
                  System.exit(1);
                }

		SyncQueueConsumer recvMsg = new SyncQueueConsumer();
		recvMsg.parseArgs(args);
		try {
			// Receive messages from queue
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
                System.out.println("Queue name is " + destName);
                if (args.length == 2){
                   noOfMsgs = (new Integer(args[1])).intValue();
                } else {
                   noOfMsgs = 1;
                }

        }

	 /**
         * Receive object messages synchronously from the queue destination
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
	
		    // Create queue 
		    Queue queue = jmsContext.createQueue(destName);
		    System.out.println("Created queue successfully");

		    // Create consumer
		    JMSConsumer consumer = jmsContext.createConsumer(queue);
		    jmsContext.start();

		    // Receive msgs
		    for (int i = 0; i < noOfMsgs; i++) {
			
		       //Receive messages using receiveBody(Class<T> c, long timeout)
	
		       MyObject payload = consumer.receiveBody(MyObject.class, 1000);

		       if( payload != null ) {
			  System.out.println("Message Received : "+payload.getValue());
		       } else {
			  System.out.println("Message not received");
			  exitcode=1;
			  break;
		       }

		   }	
		
	      }

	}

}
