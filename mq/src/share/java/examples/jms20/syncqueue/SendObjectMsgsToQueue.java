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
 * The SendObjectMsgsToQueue.class sends several object messages to a queue.
 * <p>
 * Run this program in conjunction with SyncQueueConsumer.
 * Specify a queue name on the command line when you run
 * the program.  By default, the program sends one message.  Specify a number
 * after the queue name to send that number of messages.
 */

public class SendObjectMsgsToQueue{


	private String destName          = null;
        private int noOfMsgs;
        final String MSG_TEXT    = new String("Here is a message");
	static int exitcode      = 0;

	/**
        * Main method.
        *
        * @param args      the queue used by the example and, optionally, the
        *                   number of messages to send
        */
	public static void main(String args[]) {

	       if ( (args.length < 1) || (args.length > 2) ) {
                  System.out.println("Usage: java SendObjectMsgsToQueue <queue_name> [<number_of_messages>]");
                  System.exit(1);
        	}

		SendObjectMsgsToQueue sendMsg = new SendObjectMsgsToQueue();
		sendMsg.parseArgs(args);
		try {
			// Send messages to queue
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
        	System.out.println("Queue name is " + destName);
        	if (args.length == 2){
            	   noOfMsgs = (new Integer(args[1])).intValue();
        	} else {
            	   noOfMsgs = 1;
        	}

	}

	 /**
         * Send object messages to the queue destination
         *
         * @param  none
         * @throws JMSException
         */
	
	public void sendmsgs() throws JMSException {

		ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();

		// JMSContext will be closed automatically with the try-with-resources block
		try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE);) {
		    System.out.println("Created jms context successfully");

		    // Create queue 
		    Queue queue = jmsContext.createQueue(destName);
		    System.out.println("Created queue successfully");

		    /*
         	    * Create producer.
         	    * Create object message.
         	    * Send Object messages.
         	    */
		    for (int i = 0; i < noOfMsgs; i++) {
			
                        MyObject data = new MyObject(i + 1);
                        jmsContext.createProducer().send(queue,data);
		        System.out.println("Message sent : " + data.getValue());
            	    }
		}
	}

}
