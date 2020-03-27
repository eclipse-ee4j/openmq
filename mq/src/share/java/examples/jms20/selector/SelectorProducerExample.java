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
 * The SelectorProducerExample class demonstrates the use of multiple 
 * subscribers and message selectors.
 * <p>
 * The SelectorProducerExample class publishes 30 messages of 6 different types, randomly
 * selected.
 * <p>
 * Specify a topic name on the command line when you run the program.
 */
public class SelectorProducerExample {

    static final String  MESSAGE_TYPES[] = 
                             {"Nation/World", "Metro/Region", "Business",
                              "Sports", "Living/Arts", "Opinion",
                               // always last type
                              "Finished"
                             };
    static final String  END_OF_MESSAGE_STREAM_TYPE =
                             MESSAGE_TYPES[MESSAGE_TYPES.length-1];

    static int                  exitcode = 0;
    private String destName              = null;
    final int  ARRSIZE                   = 6;


    /**
    * Main method.
    *
    * @param args      the topic used by the example
    */
    public static void main(String args[]) {


               if ( args.length < 1 ) {
                  System.out.println("Usage: java SelectorProducerExample <topic_name>");
                  System.exit(1);
                }

                SelectorProducerExample sendMsg = new SelectorProducerExample();
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
        }

        /**
         * Chooses a message type by using the random number generator
         * found in java.util.
         *
         * @return	the String representing the message type
         */
        public String chooseType() {
           int     whichMsg;
           Random  rgen = new Random();
           
           whichMsg = rgen.nextInt(ARRSIZE);
           return MESSAGE_TYPES[whichMsg];
        }
        
	/**
         * Send messages to the topic destination
         *
         * @param  none
         * @throws JMSException
         */
        public void sendmsgs() throws JMSException {
            ConnectionFactory    connectionFactory = null;
            Topic                topic = null;
            JMSProducer          msgProducer = null;
            TextMessage          message = null;
            int                  numMsgs = ARRSIZE * 5;
            String               messageType = null;

            connectionFactory = 
                    new com.sun.messaging.ConnectionFactory();
	    // JMSContext will be closed automatically with the try-with-resources block
            try ( JMSContext context = 
                    connectionFactory.createContext();) {
            topic = context.createTopic(destName);
            
            /*
             * Create producer.
             * Create and send news messages.
             */
             msgProducer = context.createProducer();
             message = context.createTextMessage();
             for (int i = 0; i < numMsgs; i++) {
                 messageType = chooseType();
                 message.setStringProperty("NewsType", messageType);
                 message.setText("Item " + i + ": " + messageType);
                 System.out.println("PUBLISHER THREAD: Setting message text to: " 
                        + message.getText());
                 msgProducer.send(topic,message);
              }
	      message.setStringProperty("NewsType", END_OF_MESSAGE_STREAM_TYPE);
              message.setText("That's all the news for today.");
              System.out.println("PUBLISHER THREAD: Setting message text to: "
                   + message.getText());
              msgProducer.send(topic,message);
          }
	}	
}
