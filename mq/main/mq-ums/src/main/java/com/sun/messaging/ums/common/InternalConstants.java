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

/**
 *
 * @author chiaming
 */
public class InternalConstants {
    
    public static final String JMS_HEADER = "JMSHeader";
    public static final String JMS_PROPERTY = "JMSProperty";
    //JMS Message body element
    public static final String SEND_MESSAGE = "SendMessage";
    public static final String CREATE_CONSUMER = "CreateConsumer";
    public static final String CLOSE_CONSUMER = "CloseConsumer";
    //JMS destination element name
    public static final String JMS_DESTINATION = "JMSDestination";
    //JMS time to live element name
    public static final String JMS_TIME_TO_LIVE = "JMSTimeToLive";
    //JMS JMSDeliveryMode element name
    public static final String JMS_DELIVERY_MODE = "JMSDeliveryMode";
    //JMSSOAPMessage element name
    public static final String JMS_SOAP_MESSAGE = "JMSSOAPMessage";
    //Endpoint element name
    public static final String EndPOINT = "Endpoint";
    //durable element name
    public static final String DURABLE_SUBSCRIBER = "DurableSubscriber";
    //selector element name
    public static final String SELECTOR = "Selector";
    //JMS property element names
    public static final String STRING_PROPERTY = "StringProperty";
    public static final String INT_PROPERTY = "IntProperty";
    public static final String BOOLEAN_PROPERTY = "BooleanProperty";
    public static final String SHORT_PROPERTY = "ShortProperty";
    public static final String LONG_PROPERTY = "LongProperty";
    public static final String BYTE_PROPERTY = "ByteProperty";
    //property attr "pvalue" local name
    public static final String PVALUE = "pvalue";
    //property attr "pname" local name
    public static final String PNAME = "pname";
    
    //Acknowledge element name
    public static final String ACKNOWLEDGMENT = "Acknowledgment";
}
