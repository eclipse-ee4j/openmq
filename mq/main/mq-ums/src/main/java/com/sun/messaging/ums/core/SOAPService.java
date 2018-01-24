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

package com.sun.messaging.ums.core;

//import javax.xml.messaging.ReqRespListener;

import javax.xml.soap.SOAPException;

/**
 * MQ SOAP Service interface.  This is part of the MQ SOAP Service frame work
 * that is used to implement and provide a new SOAP service in the frame work.
 *
 * <p>A SOAP service consists of the following components:
 *
 * <p>1. Request Handler Chain.  The Request handler can be registered as follows
 * in the web.xml:
 *
 * <p>mq.soap.request.handler.#=MessageHandler class full name.
 *
 * <p>For example,
 *
 * <p>mq.soap.request.handler.1=com.sun.TestMessageListener1
 * <p>mq.soap.request.handler.2=com.sun.TestMessageListener2
 *
 * <P>2. Response Handler Chain.  The Response handler can be registered as
 * follows in the web.xml:
 *
 * <p>mq.soap.response.handler.#=MessageHandler class full name.
 *
 * <p>For example,
 *
 * <P>mq.soap.response.handler.1=com.sun.TestMessageListener1
 * <p>mq.soap.response.handler.2=com.sun.TestMessageListener2
 *
 * <p>3. ReqRespListener onMessage() implementation.
 *
 * <p>4. Service lifecycle management methods.  There are four methods defined
 * for life cycle management - init/start/stop/close.  They are used for
 * init/start/stop/close a MQ SOAP Service instance.  Sub class SHOULD
 * implement or over ride the life cycle methods if necessary.
 *
 *
 * <p>MQSOAPService is a class that provides base implementation of SOApService
 * interface.  A new SOAP service is recommended to sub class MQSOAPService
 * and over ride appropriate methods as needed.  Please see MQSOAPService
 * Javadoc for details.
 *
 * @author  chiaming yang
 * @see     MQSOAPService
 * @see     MessageHandler
 * @see     MessageHandlerChain
 */
public interface SOAPService {

    /**
     * init this SOAPService with the specified Properties in the parameter.
     * This method is called when the service is loaded in the the frame
     * work.
     */
    public void init (ServiceContext context) throws SOAPException;

    /**
     * Get req handler chain in this service.
     */
    public MessageHandlerChain getReqHandlerChain();

    /**
     * Get resp handler chain in this service.
     */
    public MessageHandlerChain getRespHandlerChain();

    /**
     * SOAP service life cycle - start this soap service.
     */
    public void start();

    /**
     * SOAP service life cycle - stop this soap service.
     */
    public void stop();

    /**
     * SOAP service life cycle - close this soap service.
     */
    public void close();

    /**
     * Get the ServiceContext object associated with this SOAP service.
     */
    public ServiceContext getServiceContext();

    /**
     * Get this soap service URI.
     */
    public String getServiceName();
    
    public void service (MessageContext context) throws SOAPException;

}

