/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import jakarta.xml.messaging.JAXMServlet;
import jakarta.xml.messaging.ReqRespListener;

import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPPart;

import com.sun.messaging.xml.MessageTransformer;

import com.sun.messaging.TopicConnectionFactory;

import jakarta.jms.MessageListener;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicSession;
import jakarta.jms.Session;
import jakarta.jms.Message;
import jakarta.jms.Topic;
import jakarta.jms.JMSException;
import jakarta.jms.TopicPublisher;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;

/**
 * This example shows how to use the MessageTransformer utility to convert SOAP
 * message to JMS message.  When SOAP messages are received, they are
 * delivered to the ReqRespListener's onMessage() method.  The onMessage()
 * implementation uses the utility to convert SOAP to JMS message, then
 * publishes the message to the JMS Topic.
 * <p>
 * The onMessage() method adds <MessageStatus> element with value "published"
 * to the SOAPBody and returns the SOAP message to the caller.
 */
@WebServlet("/SOAPtoJMSServlet")
public class SOAPtoJMSServlet extends JAXMServlet implements ReqRespListener {

    TopicConnectionFactory tcf = null;
    TopicConnection tc = null;
    TopicSession session = null;
    Topic topic = null;

    TopicPublisher publisher = null;

    /**
     * The init method set up JMS Connection/Session/Publisher.
     */
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            tcf = new com.sun.messaging.TopicConnectionFactory();

            tc = tcf.createTopicConnection();
            session = tc.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            String topicName = config.getInitParameter("TopicName");
            if ( topicName == null ) {
                topicName = "TestTopic";
            }

            topic = session.createTopic(topicName);
            publisher = session.createPublisher(topic);

        } catch (Exception jmse) {
            throw new ServletException (jmse);
        }
    }

    /**
     * Clean up JMS connection.
     */
    public void destroy() {
        try {
            if ( tc != null ) {
                tc.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * SOAP Messages are delivered to this method and then published to the
     * JMS topic destination.
     */
    public SOAPMessage onMessage (SOAPMessage soapMessage) {

        try {
            Message message =
            MessageTransformer.SOAPMessageIntoJMSMessage(soapMessage, session);

            publisher.publish( message );

        } catch (Exception e) {
            e.printStackTrace();
        }

        SOAPMessage resp = generateResponseMessage(soapMessage);

        return resp;
    }

    /**
     * Add a MessageStatus element with the value of "published" to
     * the soapMessage.
     */
    public SOAPMessage generateResponseMessage(SOAPMessage soapMessage) {

        try {
            SOAPPart soapPart = soapMessage.getSOAPPart();
            SOAPEnvelope envelope = soapPart.getEnvelope();
            SOAPBody soapBody = envelope.getBody();

            soapBody.addChildElement("MessageStatus").addTextNode("published");
            soapMessage.saveChanges();
        } catch (SOAPException soape) {
            soape.printStackTrace();
        }

        return soapMessage;
    }

}
