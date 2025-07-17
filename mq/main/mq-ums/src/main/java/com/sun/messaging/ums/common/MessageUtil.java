/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.messaging.ums.common;

import java.util.Iterator;
import java.util.Random;
import jakarta.xml.soap.Name;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPHeaderElement;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;

public class MessageUtil {

    private static MessageFactory messageFactory = null;
    private static SOAPFactory soapFactory = null;
    private static Object syncObj = new Object();

    static {

        try {
            soapFactory = SOAPFactory.newInstance();

            // messageFactory = MessageFactory.newInstance();
            // messageFactory = MessageFactory.newInstance(SOAPConstants.DEFAULT_SOAP_PROTOCOL);
            messageFactory = MessageFactory.newInstance(SOAPConstants.DEFAULT_SOAP_PROTOCOL);

            Random random = new Random();
            random.setSeed(System.currentTimeMillis());
        } catch (Exception se) {
            se.printStackTrace();
        }
    }

    private static SOAPHeaderElement getMessageHeaderElement(SOAPMessage message) throws SOAPException {

        SOAPHeader soapHeader = message.getSOAPHeader();

        SOAPHeaderElement she = (SOAPHeaderElement) getJMSChildElement(soapHeader, Constants.MESSAGE_HEADER);

        return she;
    }

    public static SOAPHeaderElement removeMessageHeaderElement(SOAPMessage message) throws SOAPException {

        SOAPHeader soapHeader = message.getSOAPHeader();

        SOAPHeaderElement she = (SOAPHeaderElement) getJMSChildElement(soapHeader, Constants.MESSAGE_HEADER);

        she.detachNode();

        message.saveChanges();

        return she;
    }

    private static SOAPElement getJMSChildElement(SOAPElement soapElement, String localName) throws SOAPException {

        SOAPElement se = null;

        Name name = createJMSName(localName);

        Iterator it = soapElement.getChildElements(name);
        if (it.hasNext()) {
            se = (SOAPElement) it.next();
        }

        return se;
    }

    public static Name createJMSName(String localName) throws SOAPException {

        Name name = soapFactory.createName(localName, Constants.JMS_NS_PREFIX, Constants.JMS_NS_URI);

        return name;
    }

    private static SOAPHeaderElement addMessageHeader(SOAPMessage soapm) throws SOAPException {

        SOAPHeaderElement she = getMessageHeaderElement(soapm);

        if (she == null) {
            SOAPHeader sh = soapm.getSOAPHeader();
            she = addJMSNsSOAPHeaderElement(sh, Constants.MESSAGE_HEADER);
            addMessageHeaderChildElements(she);
            soapm.saveChanges();
        }

        return she;
    }

    private static SOAPMessage newMessageInstance() throws SOAPException {

        SOAPMessage soapm = null;

        /**
         * sync create new instance to make sure to work in ALL SAAJ impl.
         */
        synchronized (syncObj) {
            soapm = messageFactory.createMessage();
        }

        SOAPHeader sh = soapm.getSOAPHeader();

        SOAPHeaderElement she = addJMSNsSOAPHeaderElement(sh, Constants.MESSAGE_HEADER);

        addMessageHeaderChildElements(she);

        soapm.saveChanges();

        return soapm;
    }

    private static SOAPHeaderElement addJMSNsSOAPHeaderElement(SOAPHeader soapHeader, String localName) throws SOAPException {

        Name mh = createJMSName(localName);

        SOAPHeaderElement headerElement = soapHeader.addHeaderElement(mh);

        addHeaderAttributes(headerElement);

        return headerElement;
    }

    private static void addHeaderAttributes(SOAPHeaderElement she) throws SOAPException {

        Name id = createJMSName(Constants.ID);
        she.addAttribute(id, "1.0");

        Name version = createJMSName(Constants.VERSION);
        she.addAttribute(version, "1.1");

        // she.setMustUnderstand(true);
    }

    private static void addMessageHeaderChildElements(SOAPHeaderElement messageHeader) throws SOAPException {

        // SOAPElement se = null;

        // se = addJMSChildElement(messageHeader, Constants.FROM);
        // se.setValue(Constants.FROM_DEFAULT_VALUE);

        // se = addJMSChildElement(messageHeader, Constants.TO);
        // se.setValue(Constants.TO_DEAFULT_VALUE);

        // se = addJMSChildElement(messageHeader, Constants.MESSAGE_ID);
        // String mid = getNewMessageID();
        // se.setValue(mid);

        // se = addJMSChildElement(messageHeader, Constants.TIMESTAMP);
        // String ts = getTimestamp(mid);
        // se.setValue(ts);

        // addJMSChildElement(messageHeader, Constants.REF_TO_MESSAGE_ID);

        addJMSChildElement(messageHeader, Constants.SERVICE);
    }

    private static SOAPElement addJMSChildElement(SOAPElement element, String localName) throws SOAPException {
        Name name = createJMSName(localName);
        SOAPElement se = element.addChildElement(name);
        return se;
    }

    /**
     * Create ack message from the specified soap message.
     *
     * @param soapm the message to be acknowledged.
     * @return the created acknowledge message.
     */
    public static SOAPMessage createResponseMessage(SOAPMessage soapm) throws SOAPException {

        // create message
        SOAPMessage ackm = newMessageInstance();

        ackm = createResponseMessage2(soapm, ackm);

        return ackm;
    }

    /**
     * Create ack message from the specified soap message.
     *
     * @param req the message to be acknowledged.
     * @return the created acknowledge message.
     */
    public static SOAPMessage createResponseMessage2(SOAPMessage req, SOAPMessage resp) throws SOAPException {

        // create message
        // SOAPMessage ackm = newMessageInstance();

        checkJMSMessageHeader(resp);

        resp.saveChanges();

        return resp;
    }

    private static void checkJMSMessageHeader(SOAPMessage sm) throws SOAPException {
        SOAPHeaderElement msgHeader = getMessageHeaderElement(sm);

        if (msgHeader != null) {
            msgHeader.detachNode();
        }

        addMessageHeader(sm);

    }

    public static void setServiceAttribute(SOAPMessage soapm, String localName, String value) throws SOAPException {

        SOAPHeaderElement mh = getMessageHeaderElement(soapm);

        SOAPElement serviceElement = getJMSChildElement(mh, Constants.SERVICE);

        Name n = createJMSName(localName);

        serviceElement.addAttribute(n, value);

        soapm.saveChanges();
    }

    public static String getServiceAttribute(SOAPMessage soapm, String localName) throws SOAPException {

        SOAPHeaderElement mh = getMessageHeaderElement(soapm);

        SOAPElement serviceElement = getJMSChildElement(mh, Constants.SERVICE);

        if (serviceElement == null) {
            throw new SOAPException("Message does not contain a Service SOAP Header Element.");
        }

        Name n = createJMSName(localName);

        String value = serviceElement.getAttributeValue(n);

        return value;
    }

    public static String getServiceDestinationName(SOAPMessage soapm) throws SOAPException {

        String destName = getServiceAttribute(soapm, Constants.DESTINATION_NAME);

        return destName;
    }

    public static long getServiceReceiveTimeout(SOAPMessage soapm) throws SOAPException {

        // in milli secconds
        long timeout = 30000;

        String str = getServiceAttribute(soapm, Constants.RECEIVE_TIMEOUT);

        if (str != null) {

            try {
                timeout = Integer.parseInt(str);
            } catch (Exception e) {
                throw new SOAPException(e);
            }
        }

        return timeout;
    }

    public static boolean isServiceTopicDomain(SOAPMessage soapm) throws SOAPException {

        boolean isTopic = false;

        String domain = getServiceAttribute(soapm, Constants.DOMAIN);

        if (Constants.TOPIC_DOMAIN.equals(domain)) {
            isTopic = true;
        } else if (Constants.QUEUE_DOMAIN.equals(domain)) {
            isTopic = false;
        } else {
            throw new SOAPException("SOAP message does not contain domain attribute.");
        }

        return isTopic;

    }

    public static String getServiceName(SOAPMessage soapm) throws SOAPException {
        String sname = getServiceAttribute(soapm, Constants.SERVICE_NAME);

        return sname;
    }

    public static String getServiceClientId(SOAPMessage soapm) throws SOAPException {

        String clientId = getServiceAttribute(soapm, Constants.CLIENT_ID);

        return clientId;
    }

    /*
     * private static String getTimestamp(String mid) { int index = mid.lastIndexOf('-') + 1;
     *
     * String ts = mid.substring(index);
     *
     * return ts; }
     */
}
