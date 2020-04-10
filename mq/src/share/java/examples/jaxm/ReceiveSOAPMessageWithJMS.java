/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.AttachmentPart;

import com.sun.messaging.xml.MessageTransformer;

import com.sun.messaging.ConnectionFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import jakarta.jms.MessageListener;
import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.MessageConsumer;

import java.util.Iterator;

/**
 * This example shows a JMS message listener can use the MessageTransformer
 * utility to convert JMS messages back to SOAP messages.
 */
public class ReceiveSOAPMessageWithJMS implements MessageListener {

    ConnectionFactory        connectionFactory = null;
    Connection               connection = null;
    Session                  session = null;
    Topic                    topic = null;
    MessageConsumer          msgConsumer = null;

    MessageFactory           messageFactory = null;

    /**
     * Default constructor.
     */
    public ReceiveSOAPMessageWithJMS(String topicName) {
        init(topicName);
    }

    /**
     * JMS Connection/Session/Destination/MessageListener set ups.
     */
    public void init(String topicName) {
        try {

            /**
             * construct a default SOAP message factory.
             */
            messageFactory = MessageFactory.newInstance();

            /**
             * JMS set up.
             */
            connectionFactory = new com.sun.messaging.ConnectionFactory();
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            topic = session.createTopic(topicName);
            msgConsumer = session.createConsumer(topic);
            msgConsumer.setMessageListener( this );
            connection.start();

            System.out.println ("ready to receive SOAP messages ...");
        } catch (Exception jmse) {
            jmse.printStackTrace();
        }
    }

    /**
     * JMS Messages are delivered to this method. The body of the message
     * contains SOAP streams.
     *
     * 1.  The message conversion utility converts JMS message to SOAP
     * message type.
     * 2.  Get the attachment parts and print content information to the
     * standard output stream.
     */
    public void onMessage (Message message) {

        try {

            /**
             * convert JMS to SOAP message.
             */
            SOAPMessage soapMessage =
            MessageTransformer.SOAPMessageFromJMSMessage( message, messageFactory );

            /**
             * Print attachment counts.
             */
            System.out.println("message received!  Attachment counts: " + soapMessage.countAttachments());

            /**
             * Get attachment parts of the SOAP message.
             */
            Iterator iterator = soapMessage.getAttachments();
            while ( iterator.hasNext() ) {
                /**
                 * Get next attachment.
                 */
                AttachmentPart ap = (AttachmentPart) iterator.next();
                /**
                 * Get content type.
                 */
                String contentType = ap.getContentType();
                System.out.println("content type: " + contentType);
                /**
                 * Get content Id.
                 */
                String contentId = ap.getContentId();
                System.out.println("content Id: " + contentId);

                /**
                 * Check if this is a Text attachment.
                 */
                if ( contentType.indexOf("text") >=0 ) {
                    /**
                     * Get and print the content if it is a text
                     * attachment.
                     */
                    
                    Object content =  ap.getContent();
                    
                    /**
                     * content could be returned as an Input Stream.
                     */
                    if ( content instanceof InputStream ) {
                        
                        InputStreamReader isr = new InputStreamReader ((InputStream)content);
                        BufferedReader reader = new BufferedReader (isr);
                        
                        System.out.println("*** attachment content: ");
                        
                        String line = null;
                        while ( (line = reader.readLine ()) != null ) {
                            System.out.println (line);
                        }
                        
                    } else {
                        System.out.println("*** attachment content: " + content);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The main method to start the example receiver.
     */
    public static void main (String[] args) {

        String topicName = "TestTopic";

        if (args.length > 0) {
            topicName = args[0];
        }
        try {
            ReceiveSOAPMessageWithJMS rsm = new ReceiveSOAPMessageWithJMS(topicName);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
