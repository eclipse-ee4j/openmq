/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import javax.xml.messaging.URLEndpoint;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPPart;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPConnection;

/**
 * This example demonstrates a hello world example for using JAXM API.
 */
public class SendSOAPMessage {

    /**
     * send a simple soap message with JAXM API.
     */
    public void sendMessage (String url) {

        try {
            /**
             * Construct a default SOAP message factory.
             */
            MessageFactory mf = MessageFactory.newInstance();
            /**
             * Create a SOAP message object.
             */
            SOAPMessage soapMessage = mf.createMessage();
            /**
             * Get SOAP part.
             */
            SOAPPart soapPart = soapMessage.getSOAPPart();
            /**
             * Get SOAP envelope.
             */
            SOAPEnvelope soapEnvelope = soapPart.getEnvelope();

            /**
             * Get SOAP body.
             */
            SOAPBody soapBody = soapEnvelope.getBody();

            /**
             * Add child element with the specified name.
             */
            SOAPElement element = soapBody.addChildElement("HelloWorld");

            /**
             * Add text message
             */
            element.addTextNode("Welcome to SunOne Web Services!");

            soapMessage.saveChanges();

            /**
             * Construct a default SOAP connection factory.
             */
            SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();

            /**
             * Get SOAP connection.
             */
            SOAPConnection soapConnection = connectionFactory.createConnection();

            /**
             * Construct endpoint object.
             */
            URLEndpoint endpoint = new URLEndpoint (url);

            /**
             * Send SOAP message.
             */
            SOAPMessage resp = soapConnection.call(soapMessage, endpoint);

            /**
             * Print response to the std output.
             */
            resp.writeTo( System.out );

            /**
             * close the connection
             */
            soapConnection.close();

        } catch (java.io.IOException ioe) {
            ioe.printStackTrace();
        } catch (SOAPException soape) {
            soape.printStackTrace();
        }
    }

    public static void main (String args[]) {

        String url = "http://localhost:8080/imqSOAPexamples/SOAPEchoServlet";

        if (args.length > 0) {
            url = args[0];
        } else {
            System.out.println("Usage: " +
                "\tjava SendSOAPMessage <SOAP servlet url>\n" +
                "e.g.\n\tjava SendSOAPMessage http://localhost:8080/imqSOAPexamples/SOAPEchoServlet"
                );
            System.exit(1);
        }

        SendSOAPMessage ssm = new SendSOAPMessage();
        ssm.sendMessage(url);
    }

}
