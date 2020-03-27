/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.util.*;
import jakarta.jms.*;

/**
 * The SharedNonDuraConsumerExample class demonstrates 
 * the use of multiple non dura subscribers
 * sharing messages that are published.
 * <p>
 * The program contains a Subscriber class with a listener 
 * class, a main method, and a method that runs the subscriber
 * threads.
 * <p>
 * The program creates two 
 * instances of the Subscriber class 
 * The listener displays the messages that
 * the shared subscribers receive.  Because all the objects run in threads, the
 * displays are interspersed when the program runs.
 * <p>
 * Specify a topic name & no of msgs on the command line when you run the program.
 */
public class SharedNonDuraConsumerExample {

    static int                  exitcode     = 0;
    private String destName                  = null;
    private int noOfMsgs;
    static int msgsReceived                  = 0;
    static boolean doneSignal		     = false;

     /**
     * Reads the topic name from the command line, then calls the
     * run_threads method to execute the program threads.
     *
     * @param args      the topic used by the example
     */
    public static void main(String[] args) {

	if ( (args.length < 1) || (args.length > 2) ) {
                  System.out.println("Usage: java SharedNonDuraConsumerExample <topic_name> [<number_of_messages>]");
                  System.exit(1);
        }

        SharedNonDuraConsumerExample receiveMsg = new SharedNonDuraConsumerExample();
        receiveMsg.parseArgs(args);
        try {
                // Receive messages from topic
                receiveMsg.run_threads();
        }catch(Exception ex) {
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
     * Each instance of the Subscriber class creates a subscriber.
     * It registers its message listener, then starts listening
     * for messages.  It does not exit till the both subscribers get messages.
    */
    public class Subscriber extends Thread {

        int     subscriberNumber;

        /**
         * The MultipleListener class implements the MessageListener interface  
         * by defining an onMessage method for the Subscriber class.
        */
        private class MultipleListener implements MessageListener {

	    MultipleListener() {
            }

            /**
             * Displays the message text
	     * for each Subscriber.
             * @param inMessage	the incoming message
             */
            public void onMessage(Message inMessage) {
                TextMessage  msg = (TextMessage) inMessage;
                try {
                    System.out.println("SUBSCRIBER " + subscriberNumber 
                                       + " : Message received: " 
                                       + msg.getText());
		    msgsReceived++;
		    if ( msgsReceived == noOfMsgs) 
		    {
			System.out.println("Received all messages");
                        doneSignal = true;
	
                    }
                } catch(JMSException e) {
                    System.out.println("Exception in onMessage(): " 
                                       + e.toString());
                }
            }
	
        }
        
        /**
         * Constructor.
         * subscriberNumber based on Subscriber array index.
         *
         * @param num	the index of the Subscriber array
         */
        public Subscriber(int num) {
            subscriberNumber = num + 1;
        }
 
        /**
         * Runs the thread.
         */
        public void run() {
            ConnectionFactory    connectionFactory = null;
	    JMSContext  	 context = null;          
            Topic                topic = null;
            String               selector = null;
            JMSConsumer          msgConsumer = null;
            MultipleListener     multipleListener = new MultipleListener();
            connectionFactory = 
                    new com.sun.messaging.ConnectionFactory();
            try  {
	    	context = 
                    connectionFactory.createContext();
             	topic = context.createTopic(destName);
		
            	/*
             	* Create subscriber with shared subscription name.
             	* Start message delivery.
             	* Wait till all messages have arrived.
             	* Listener displays the messages obtained.
             	*/
             	msgConsumer = 
                    context.createSharedConsumer(topic,"subid");
             	msgConsumer.setMessageListener(multipleListener);
             	context.start();
                
             	/*
              	* Asynchronously process all messages.
              	* Block until all subscribers receive msgs.
              	*/
		
             	while(!doneSignal) {	
			Thread.sleep(1000);
	     	}
		
           } catch (Exception e) {
          	System.out.println("Exception occurred: " + e.toString());
               	exitcode = 1;
	   } finally {
		if( context != null) {
			context.close();
		}
	   }
        }

     }
    
    /**
     * Creates an array of Subscriber objects and starts their threads.
     * Calls the join method to wait for the threads to die.
     */
    public void run_threads() {
        final       int NUM_SUBSCRIBERS = 2;
        Subscriber  subscriberArray[] = new Subscriber[NUM_SUBSCRIBERS];

        subscriberArray[0] = new Subscriber(0);
        subscriberArray[0].start();        
        subscriberArray[1] = new Subscriber(1);
        subscriberArray[1].start();

        for (int i = 0; i < subscriberArray.length; i++) {
            try {
                subscriberArray[i].join();
            } catch (InterruptedException e) {}
        }
        
    }
}
