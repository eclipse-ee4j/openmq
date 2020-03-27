/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/**
 * The HelloWorldMessage class consists only of a main method, which sends 
 * a message to a queue and then receives the message from the queue.
 * <p>
 * This example is used in the "Quick Start Tutorial" of the Oracle GlassFish(tm) 
 * Server Message Queue Developer's Guide to illustrate a very simple JMS 
 * client.
 * The line comments associate the lines of code with the steps in the tutorial.
 */

//Step 1:
//Import the JMS API classes.
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.Session;
import jakarta.jms.MessageProducer;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
//Import the classes to use JNDI.
import javax.naming.*;
import java.util.*;

public class HelloWorldMessage {

    /**
     * Main method.
     *
     * @param args	not used
     *
     */
    public static void main(String[] args) {

        try {

            ConnectionFactory myConnFactory;
            Queue myQueue;

            /*
             * The following code uses the JNDI File System Service Provider
             * to lookup() Administered Objects that were stored in the
             * Administration Console Tutorial in the Administrator's Guide
             *
             * The following code (in this comment block replaces the
             * statements in Steps 2 and 5 of this example.
             *
             ****
                String MYCF_LOOKUP_NAME = "MyConnectionFactory";
                String MYQUEUE_LOOKUP_NAME = "MyQueue";

                Hashtable env;
                Context ctx = null;

                env = new Hashtable();

                // Store the environment variable that tell JNDI which initial context
                // to use and where to find the provider.

                // For use with the File System JNDI Service Provider
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.fscontext.RefFSContextFactory");
                // On Unix, use file:///tmp instead of file:///C:/Temp
                env.put(Context.PROVIDER_URL, "file:///C:/Temp");
 
                // Create the initial context.
                ctx = new InitialContext(env);

                // Lookup my connection factory from the admin object store.
                // The name used here here must match the lookup name
                // used when the admin object was stored.
                myConnFactory = (jakarta.jms.ConnectionFactory) ctx.lookup(MYCF_LOOKUP_NAME);
      
                // Lookup my queue from the admin object store.
                // The name I search for here must match the lookup name used when
                // the admin object was stored.
                myQueue = (jakarta.jms.Queue)ctx.lookup(MYQUEUE_LOOKUP_NAME);
            ****
            *
            */

            //Step 2:
            //Instantiate a Oracle GlassFish(tm) Server Message Queue ConnectionFactory 
	    //administered object.
            //This statement can be eliminated if the JNDI code above is used.
            myConnFactory = new com.sun.messaging.ConnectionFactory();


            //Step 3:
            //Create a connection to the Oracle GlassFish(tm) Server Message Queue Message 
	    //Service.
            Connection myConn = myConnFactory.createConnection();


            //Step 4:
            //Create a session within the connection.
            Session mySess = myConn.createSession(false, Session.AUTO_ACKNOWLEDGE);


            //Step 5:
            //Instantiate a Oracle GlassFish(tm) Server Message Queue Destination 
	    //administered object.
            //This statement can be eliminated if the JNDI code above is used.
            myQueue = new com.sun.messaging.Queue("world");


            //Step 6:
            //Create a message producer.
            MessageProducer myMsgProducer = mySess.createProducer(myQueue);


            //Step 7:
            //Create and send a message to the queue.
            TextMessage myTextMsg = mySess.createTextMessage();
            myTextMsg.setText("Hello World");
            System.out.println("Sending Message: " + myTextMsg.getText());
            myMsgProducer.send(myTextMsg);


            //Step 8:
            //Create a message consumer.
            MessageConsumer myMsgConsumer = mySess.createConsumer(myQueue);


            //Step 9:
            //Start the Connection created in step 3.
            myConn.start();


            //Step 10:
            //Receive a message from the queue.
            Message msg = myMsgConsumer.receive();


            //Step 11:
            //Retreive the contents of the message.
            if (msg instanceof TextMessage) {
                TextMessage txtMsg = (TextMessage) msg;
                System.out.println("Read Message: " + txtMsg.getText());
            }

     
            //Step 12:
            //Close the session and connection resources.
            mySess.close();
            myConn.close();

        } catch (Exception jmse) {
            System.out.println("Exception occurred : " + jmse.toString());
            jmse.printStackTrace();
        }
    }
}
