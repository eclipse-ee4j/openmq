/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.util.Iterator;

import java.util.Random;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;

public class MessageUtil {

    private static MessageFactory messageFactory = null;
    private static SOAPFactory soapFactory = null;
    private static String HOST_ADDRESS = null;
    private static Object syncObj = new Object();
    
    private static long sequence = 0;
    
    private static long MSG_RANDOM = 0;
    
    private static String MSG_ID_PREFIX = null;

    static {

        try {
            soapFactory = SOAPFactory.newInstance();

            //messageFactory = MessageFactory.newInstance();
            //messageFactory = MessageFactory.newInstance(SOAPConstants.DEFAULT_SOAP_PROTOCOL);
            messageFactory = MessageFactory.newInstance(SOAPConstants.DEFAULT_SOAP_PROTOCOL);
            HOST_ADDRESS = InetAddress.getLocalHost().toString();
            
            sequence = 0;
            
            Random random = new Random();
            random.setSeed(System.currentTimeMillis());
            MSG_RANDOM = random.nextLong();
            
            MSG_ID_PREFIX = HOST_ADDRESS + "-" + MSG_RANDOM + "-";
            
        } catch (Exception se) {
            se.printStackTrace();
        }
    }

    public static SOAPHeaderElement getMessageHeaderElement(SOAPMessage message) throws SOAPException {

        SOAPHeader soapHeader = message.getSOAPHeader();

        SOAPHeaderElement she = (SOAPHeaderElement) getJMSChildElement(soapHeader, Constants.MESSAGE_HEADER);

        return she;
    }
    
    public static SOAPHeaderElement removeMessageHeaderElement(SOAPMessage message) throws SOAPException {

        SOAPHeader soapHeader = message.getSOAPHeader();

        SOAPHeaderElement she = (SOAPHeaderElement) getJMSChildElement (soapHeader, Constants.MESSAGE_HEADER);

        she.detachNode();
        
        message.saveChanges();
        
        return she;
    }

    public static Iterator getJMSProperties(SOAPMessage m) throws SOAPException {

        SOAPHeader sheader = m.getSOAPHeader();

        Name propEleName = createJMSName(InternalConstants.JMS_PROPERTY);

        Iterator it = sheader.getChildElements(propEleName);

        if (it.hasNext()) {
            SOAPHeaderElement she = (SOAPHeaderElement) it.next();
            return she.getChildElements();
        }

        //iterator with no elements.
        return it;

    }

    public static boolean isStringProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.STRING_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static boolean isBooleanProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.BOOLEAN_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static boolean isLongProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.LONG_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static boolean isShortProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.SHORT_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static boolean isByteProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.BYTE_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static boolean isIntProperty(SOAPElement se) throws SOAPException {

        if (se.getLocalName().equals(InternalConstants.INT_PROPERTY)) {
            return true;
        }

        return false;
    }

    public static String getPropertyName(SOAPElement se) throws SOAPException {

        Name attrName = createJMSName(InternalConstants.PNAME);
        String propName = se.getAttributeValue(attrName);

        return propName;
    }

    public static Object getPropertyValue(SOAPElement se) throws SOAPException {

        Name pvalueAttrName = createJMSName(InternalConstants.PVALUE);
        String pValue = se.getAttributeValue(pvalueAttrName);

        Object pvObj = null;

        if (isStringProperty(se)) {
            pvObj = pValue;
        } else if (isLongProperty(se)) {
            pvObj = new Long(pValue);
        } else if (isIntProperty(se)) {
            pvObj = new Integer(pValue);
        } else if (isShortProperty(se)) {
            pvObj = new Short(pValue);
        } else if (isBooleanProperty(se)) {
            pvObj = Boolean.valueOf(pValue);
        } else if (isByteProperty(se)) {
            pvObj = new Byte(pValue);
        }

        return pvObj;

    }

    public static SOAPHeaderElement getSOAPHeaderElement(SOAPMessage message, String headerName,
            String nameSpace) throws SOAPException {

        Iterator it = message.getSOAPHeader().examineAllHeaderElements();

        while (it.hasNext()) {

            SOAPHeaderElement she = (SOAPHeaderElement) it.next();
            String uri = she.getNamespaceURI();
            String localName = she.getLocalName();

            if (headerName.equals(localName) && nameSpace.equals(uri)) {
                //found message header.
                return she;
            }

        }

        return null;
    }

    public static SOAPBodyElement getJMSBodyElement(SOAPMessage message, String localName) throws SOAPException {

        Name name = createJMSName(localName);
        Iterator it = message.getSOAPBody().getChildElements(name);

        if (it.hasNext()) {
            return (SOAPBodyElement) it.next();
        }

        return null;
    }

    public static String getJMSChildElementValue(SOAPElement soapElement, String localName) throws SOAPException {

        String value = null;

        SOAPElement se = getJMSChildElement(soapElement, localName);

        if (se != null) {
            value = se.getValue();
        }

        return value;
    }

    public static SOAPElement getJMSChildElement(SOAPElement soapElement, String localName) throws SOAPException {

        SOAPElement se = null;

        Name name = createJMSName(localName);

        Iterator it = soapElement.getChildElements(name);
        if (it.hasNext()) {
            se = (SOAPElement) it.next();
        }

        return se;
    }

    public static boolean isJMSPropertyExists(SOAPHeader sheader) throws SOAPException {

        Name pname = createJMSName(InternalConstants.JMS_PROPERTY);

        Iterator it = sheader.getChildElements(pname);

        if (it.hasNext()) {
            return true;
        }

        return false;
    }

    public static SOAPHeaderElement getJMSPropertyElement(SOAPHeader sheader) throws SOAPException {

        SOAPHeaderElement she = (SOAPHeaderElement) getJMSChildElement(sheader, InternalConstants.JMS_PROPERTY);

        return she;

    }

    public static SOAPHeaderElement addJMSPropertyElement(SOAPHeader sheader) throws SOAPException {

        SOAPHeaderElement she = null;

        //Name pname = createJMSName (Constants.JMS_PROPERTY);

        //System.out.println("*** JMS property exists: " + pname.getQualifiedName());
        she = getJMSPropertyElement(sheader);

        if (she == null) {
            she = addJMSNsSOAPHeaderElement(sheader, InternalConstants.JMS_PROPERTY);
        }

        return she;
    }

    public static SOAPElement setObjectProperty(SOAPMessage soapm, String pname, Object pvalue)
            throws SOAPException {

        SOAPHeader sheader = soapm.getSOAPHeader();

        SOAPHeaderElement jmsPropertyRoot =
                getJMSPropertyElement(sheader);

        if (jmsPropertyRoot == null) {
            jmsPropertyRoot = addJMSPropertyElement(sheader);
        }

        SOAPElement propEle = setJMSProperty(jmsPropertyRoot, pname, pvalue);

        return propEle;
    }

    public static SOAPElement setJMSProperty(SOAPHeaderElement jmsPropertyRoot, String pname, Object pvalue)
            throws SOAPException {

        //prop element to be added to JMSProperty element
        String eleName = null;

        if (pvalue instanceof String) {
            eleName = InternalConstants.STRING_PROPERTY;
        // System.out.println ("Message util setting String prop ... " +
        // pvalue );
        } else if (pvalue instanceof Integer) {
            eleName = InternalConstants.INT_PROPERTY;
        // System.out.println ("Message util setting Int prop ... " + pvalue
        // );
        } else if (pvalue instanceof Long) {
            eleName = InternalConstants.LONG_PROPERTY;
        // System.out.println ("Message util setting Long prop ... " +
        // pvalue );
        } else if (pvalue instanceof Boolean) {
            eleName = InternalConstants.BOOLEAN_PROPERTY;
        // System.out.println ("Message util setting Boolean prop... " +
        // pvalue );
        } else if (pvalue instanceof Short) {
            eleName = InternalConstants.SHORT_PROPERTY;
        // System.out.println ("Message util setting SHORT prop... " +
        // pvalue );
        } else if (pvalue instanceof Byte) {
            eleName = InternalConstants.BYTE_PROPERTY;
        // System.out.println ("Message util setting Byte prop ... " +
        // pvalue );
        } else {
            throw new SOAPException("Invalid property value." + pvalue);
        }

        // SOAPElement propEle = jmsPropertyRoot.addChildElement(eleName);
        SOAPElement propEle =
                MessageUtil.addJMSChildElement(jmsPropertyRoot, eleName);

        // pname attr
        Name propNameAttr = createJMSName(InternalConstants.PNAME);

        // pvalue attr
        Name propValueAttr = createJMSName(InternalConstants.PVALUE);

        // add pname attr
        propEle.addAttribute(propNameAttr, pname);
        // add pvalue attr.
        propEle.addAttribute(propValueAttr, pvalue.toString());

        // System.out.println ("Message util setting prop attributes for "
        // + eleName + ", " + pname + "=" +pvalue);

        return propEle;
    }

    public static Name createJMSName(String localName) throws SOAPException {

        Name name =
                soapFactory.createName(localName,
                Constants.JMS_NS_PREFIX, Constants.JMS_NS_URI);

        return name;
    }

    public static SOAPHeaderElement addMessageHeader(SOAPMessage soapm) throws SOAPException {

        SOAPHeaderElement she = getMessageHeaderElement(soapm);

        if (she == null) {
            SOAPHeader sh = soapm.getSOAPHeader();
            she = addJMSNsSOAPHeaderElement(sh, Constants.MESSAGE_HEADER);
            addMessageHeaderChildElements(she);
            soapm.saveChanges();
        }

        return she;
    }

    public static SOAPMessage newMessageInstance() throws SOAPException {

        SOAPMessage soapm = null;

        /**
         * sync create new instance to make sure to work in ALL SAAJ impl.
         */
        synchronized (syncObj) {
            soapm = messageFactory.createMessage();
        }

        SOAPHeader sh = soapm.getSOAPHeader();

        SOAPHeaderElement she =
                addJMSNsSOAPHeaderElement(sh, Constants.MESSAGE_HEADER);

        addMessageHeaderChildElements(she);

        soapm.saveChanges();

        return soapm;
    }

    public static SOAPHeaderElement addJMSNsSOAPHeaderElement(SOAPHeader soapHeader, String localName)
            throws SOAPException {

        SOAPHeaderElement headerElement = null;

        Name mh = createJMSName(localName);

        headerElement = soapHeader.addHeaderElement(mh);

        addHeaderAttributes(headerElement);

        return headerElement;
    }

    public static void addHeaderAttributes(SOAPHeaderElement she)
            throws SOAPException {

        Name id = createJMSName(Constants.ID);
        she.addAttribute(id, "1.0");

        Name version = createJMSName(Constants.VERSION);
        she.addAttribute(version, "1.1");

    //she.setMustUnderstand(true);
    }

    public static void addMessageHeaderChildElements(SOAPHeaderElement messageHeader)
            throws SOAPException {
        
        SOAPElement se = null;
        
        //se = addJMSChildElement(messageHeader, Constants.FROM);
        //se.setValue(Constants.FROM_DEFAULT_VALUE);

        //se = addJMSChildElement(messageHeader, Constants.TO);
        //se.setValue(Constants.TO_DEAFULT_VALUE);
        
        //se = addJMSChildElement(messageHeader, Constants.MESSAGE_ID);
        //String mid = getNewMessageID();
        //se.setValue(mid);

        //se = addJMSChildElement(messageHeader, Constants.TIMESTAMP);
        //String ts = getTimestamp(mid);
        //se.setValue(ts);

        //addJMSChildElement(messageHeader, Constants.REF_TO_MESSAGE_ID);

        addJMSChildElement(messageHeader, Constants.SERVICE);
    }

    public static SOAPElement addJMSChildElement(SOAPElement element, String localName) throws SOAPException {
        Name name = createJMSName(localName);
        SOAPElement se = element.addChildElement(name);
        return se;
    }

    /**
     * Create ack message from the specified soap message.
     * @param soapm the message to be acknowledged.
     * @return the created acknowledge message.
     * @throws SOAPException
     */
    public static SOAPMessage createAcknowledgeMessage(SOAPMessage soapm) throws SOAPException {

        //create message
        SOAPMessage ackm = newMessageInstance();

        //add acknowledge element
        SOAPHeaderElement ackele =
                addJMSNsSOAPHeaderElement(ackm.getSOAPHeader(), InternalConstants.ACKNOWLEDGMENT);

        //message to be acked
        SOAPHeaderElement messageHeader = getMessageHeaderElement(soapm);
        String mid = getJMSChildElementValue(messageHeader, Constants.MESSAGE_ID);

        SOAPHeaderElement ackMsgHdr = getMessageHeaderElement(ackm);
        SOAPElement se = getJMSChildElement(ackMsgHdr, Constants.REF_TO_MESSAGE_ID);
        se.setValue(mid);

        //add RefToMessageID element
        SOAPElement refEle = addJMSChildElement(ackele, Constants.REF_TO_MESSAGE_ID);
        refEle.setValue(mid);


        ackm.saveChanges();

        return ackm;
    }
    
    /**
     * Create ack message from the specified soap message.
     * @param soapm the message to be acknowledged.
     * @return the created acknowledge message.
     * @throws SOAPException
     */
    public static SOAPMessage createResponseMessage (SOAPMessage soapm) throws SOAPException {

        //create message
        SOAPMessage ackm = newMessageInstance();

       ackm = createResponseMessage2 (soapm, ackm);

        return ackm;
    }
    
    
    /**
     * Create ack message from the specified soap message.
     * @param soapm the message to be acknowledged.
     * @return the created acknowledge message.
     * @throws SOAPException
     */
    public static SOAPMessage createResponseMessage2 (SOAPMessage req, SOAPMessage resp) throws SOAPException {

        //create message
        //SOAPMessage ackm = newMessageInstance();
        
        checkJMSMessageHeader (resp);
        
        resp.saveChanges();

        return resp;
    }

    public static SOAPMessage createResponseMessage2_save (SOAPMessage req, SOAPMessage resp) throws SOAPException {

        //create message
        //SOAPMessage ackm = newMessageInstance();
        
        checkJMSMessageHeader (resp);
        
        //set ref to mid
        SOAPHeaderElement messageHeader = getMessageHeaderElement(req);
        String mid = getJMSChildElementValue(messageHeader, Constants.MESSAGE_ID);

        SOAPHeaderElement ackMsgHdr = getMessageHeaderElement(resp);
        SOAPElement se = getJMSChildElement(ackMsgHdr, Constants.REF_TO_MESSAGE_ID);
        se.setValue(mid);
        
        //get <from> value from req msg 
        String from = getJMSChildElementValue(messageHeader, Constants.FROM);
        
        //get <to> element from resp msg
        SOAPElement toEle = getJMSChildElement (ackMsgHdr, Constants.TO);
        toEle.setValue(from);
        
        resp.saveChanges();

        return resp;
    }
    
    public static void checkJMSMessageHeader (SOAPMessage sm) throws SOAPException {
        SOAPHeaderElement msgHeader = getMessageHeaderElement (sm);
        
        if (msgHeader != null) {
            msgHeader.detachNode();
        }
        
        addMessageHeader(sm);
        
    }

    
    /**
     * XXX chiaming 06/19/2008- OLD -- to be removed
     * @param soapm
     * @param value
     * @throws javax.xml.soap.SOAPException
     */
    public static void setService(SOAPMessage soapm, String value) throws SOAPException {

        SOAPHeaderElement mh = MessageUtil.getMessageHeaderElement(soapm);

        SOAPElement service = MessageUtil.addJMSChildElement(mh, Constants.SERVICE);

        service.setValue(value);

        soapm.saveChanges();
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
    
    public static long getServiceReceiveTimeout (SOAPMessage soapm) throws SOAPException {
        
        //in milli secconds
        long timeout = 30000;
        
        String str = getServiceAttribute(soapm, Constants.RECEIVE_TIMEOUT);
        
        if (str != null) {
            
            try {
                timeout = Integer.parseInt(str);
            } catch (Exception e) {
                throw new SOAPException (e);
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
        int stype = -1;

        String sname = getServiceAttribute(soapm, Constants.SERVICE_NAME);

        return sname;
    }

    public static String getServiceClientId(SOAPMessage soapm) throws SOAPException {

        String clientId = getServiceAttribute(soapm, Constants.CLIENT_ID);

        return clientId;
    }

    public static String getNewMessageID() {
       
        return MSG_ID_PREFIX + System.currentTimeMillis() + getNextSequence();
        
    }
    
    public static long getNextSequence() {
        
        synchronized (syncObj) {
            
            if (sequence == Long.MAX_VALUE) {
                sequence = 0;
            }
            
            sequence ++;
        }
        
        return sequence;
        
    }

    /*
    private static String getTimestamp(String mid) {
        int index = mid.lastIndexOf('-') + 1;

        String ts = mid.substring(index);

        return ts;
    }
    */
}
