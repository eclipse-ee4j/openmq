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

/*
 * @(#)MessageConvert.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import java.util.Enumeration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.sun.messaging.AdministeredObject;

/**
 * This class is to convert other vendor's message to JMQ message
 * type.
 */

public class MessageConvert {

    protected static final MessageConvert messageConvert = new MessageConvert();

    public static synchronized MessageConvert getInstance() {
        return messageConvert;
    }

    public MessageConvert() {
    }

    /**
     * This method converts foreign message to JMQ message type.
     *
     * @param foreignMessage other vendor's JMS Message implementation.
     *
     * @return Message JMQ message implementation of JMS Message.
     */
    protected Message
    convertJMSMessage (Message foreignMessage) throws JMSException {
        Message message = null;

        //1. Convert message body
        if ( foreignMessage instanceof TextMessage ) {
            message = convertTextMessage ( (TextMessage) foreignMessage );
        } else if ( foreignMessage instanceof MapMessage ) {
            message = convertMapMessage ( (MapMessage) foreignMessage );
        } else if ( foreignMessage instanceof BytesMessage ) {
            message = convertBytesMessage ( (BytesMessage) foreignMessage );
        } else if ( foreignMessage instanceof ObjectMessage ) {
            message = convertObjectMessage ( (ObjectMessage) foreignMessage );
        } else if ( foreignMessage instanceof StreamMessage ) {
            message = convertStreamMessage ( (StreamMessage) foreignMessage );
        } else {
            message = new MessageImpl();
        }

        //2. Convert JMS message header
        convertJMSHeader (message, foreignMessage);

        //3. Convert JMS message properties
        convertJMSProperties (message, foreignMessage);

        return message;
    }

    /**
     * Convert JMS message headers from foreign message to JMQ message.
     */
    protected void
    convertJMSHeader (Message message, Message foreignMessage) throws JMSException {

        message.setJMSCorrelationID( foreignMessage.getJMSCorrelationID() );

        //message.setJMSDeliveryMode( foreignMessage.getJMSDeliveryMode() );
        //This is provider dependent
        //message.setJMSDestination( foreignMessage.getJMSDestination() );
        //message.setJMSExpiration( foreignMessage.getJMSExpiration() );
        //message.setJMSMessageID( foreignMessage.getJMSMessageID() );
        //message.setJMSPriority( foreignMessage.getJMSPriority() );
        //message.setJMSRedelivered( foreignMessage.getJMSRedelivered() );
        //This is provider dependent
        //message.setJMSReplyTo( foreignMessage.getJMSReplyTo() );
        //message.setJMSTimestamp( foreignMessage.getJMSTimestamp() );
        message.setJMSType( message.getJMSType() );

    }

    /**
     * reset foreign message headers after it is sent by iMQ.
     */
    protected void
    resetForeignMessageHeader (Message message, Message foreignMessage) throws JMSException {
        foreignMessage.setJMSDeliveryMode( message.getJMSDeliveryMode() );
        foreignMessage.setJMSExpiration( message.getJMSExpiration() );
        Method m = null;
        try {
            Class c = foreignMessage.getClass();
            m = c.getMethod("getJMSDeliveryTime", (Class[])null);
            if (Modifier.isAbstract(m.getModifiers())) {
                m = null;
            }
        } catch (NoSuchMethodException e) {
            m = null;
        }
        if (m != null && message.getJMSDeliveryTime() != 0L) {
            foreignMessage.setJMSDeliveryTime( message.getJMSDeliveryTime() );
        }
        foreignMessage.setJMSMessageID( message.getJMSMessageID() );
        foreignMessage.setJMSPriority( message.getJMSPriority() );
        foreignMessage.setJMSTimestamp( message.getJMSTimestamp() );
        foreignMessage.setJMSDestination( message.getJMSDestination() );
        //System.out.println ("*****MID: " + message.getJMSMessageID() );
    }

    /**
     * Convert JMS message properties from foreign message to JMQ message.
     */
    protected void
    convertJMSProperties (Message message, Message foreignMessage) throws JMSException {
        Enumeration keys = foreignMessage.getPropertyNames();
        String key = null;
        Object value = null;

        while ( keys.hasMoreElements() ) {
            key = (String) keys.nextElement();
            value = foreignMessage.getObjectProperty(key);
            message.setObjectProperty( key, value);
        }
    }

    /**
     * Convert foreign text message into JMQ TextMessage implementation.
     *
     * @param foreignMessage other vendors implementation of TextMessage.
     *
     * @return Message JMQ message implementation.
     *
     * @exception JMSException if message conversion failed.
     */
    protected Message
    convertTextMessage ( TextMessage foreignMessage ) throws JMSException {

        TextMessageImpl message = new TextMessageImpl();
        message.setText( foreignMessage.getText() );

        return message;
    }

    /**
     * Convert foreign MapMessage into JMQ MapMessage implementation.
     *
     * @param foreignMessage other vendors implementation of MapMessage.
     *
     * @return Message JMQ message implementation.
     *
     * @exception JMSException if message conversion failed.
     */
    protected Message
    convertMapMessage ( MapMessage foreignMessage ) throws JMSException {
        MapMessageImpl message = new MapMessageImpl();
        String key = null;

        Enumeration keys = foreignMessage.getMapNames();
        while ( keys.hasMoreElements() ) {
            key = (String) keys.nextElement();
            message.setObject( key, foreignMessage.getObject(key) );
        }

        return message;
    }

    /**
     * Convert foreign BytesMessage into JMQ BytesMessage implementation.
     *
     * @param foreignMessage other vendors implementation of BytesMessage.
     *
     * @return Message JMQ message implementation.
     *
     * @exception JMSException if message conversion failed.
     */
    protected Message
    convertBytesMessage ( BytesMessage foreignMessage ) throws JMSException {
        BytesMessageImpl message = new BytesMessageImpl(true);
        byte b = 0;

        foreignMessage.reset();
        try {
            while ( true ) {
                b = foreignMessage.readByte();
                message.writeByte(b);
            }
        } catch ( MessageEOFException e) {
            //ok if we catch this
        } catch ( Exception e ) {
            //String error = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ERROR_FOREIGN_CONVERSION) + "\n" + e.toString();
            //throw new JMSException (error);

            ExceptionHandler.handleException(e, AdministeredObject.cr.X_ERROR_FOREIGN_CONVERSION);
        }

        return message;
    }

    /**
     * Convert foreign ObjectMessage into JMQ ObjectMessage implementation.
     *
     * @param foreignMessage other vendors implementation of ObjectMessage.
     *
     * @return Message JMQ message implementation.
     *
     * @exception JMSException if message conversion failed.
     */
    protected Message
    convertObjectMessage ( ObjectMessage foreignMessage ) throws JMSException {
        ObjectMessageImpl message = new ObjectMessageImpl();

        message.setObject( foreignMessage.getObject() );

        return message;
    }

    /**
     * Convert foreign StreamMessage into JMQ StreamMessage implementation.
     *
     * @param foreignMessage other vendors implementation of StreamMessage.
     *
     * @return Message JMQ message implementation.
     *
     * @exception JMSException if message conversion failed.
     */
    protected Message
    convertStreamMessage ( StreamMessage foreignMessage ) throws JMSException {
        StreamMessageImpl message = new StreamMessageImpl(true);
        Object obj = null;
        foreignMessage.reset();
        try {
            while ( true ) {
                obj = foreignMessage.readObject();
                message.writeObject( obj );
            }
        } catch ( MessageEOFException e) {
            //ok if we catch this
        } catch ( Exception e ) {
            //String error = AdministeredObject.cr.getKString(AdministeredObject.cr.X_ERROR_FOREIGN_CONVERSION) + "\n" + e.toString();
            //throw new JMSException (error);
            ExceptionHandler.handleException(e, AdministeredObject.cr.X_ERROR_FOREIGN_CONVERSION);
        }

        return message;
    }

}

