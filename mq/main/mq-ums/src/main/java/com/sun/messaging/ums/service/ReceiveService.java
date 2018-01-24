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

package com.sun.messaging.ums.service;

import javax.jms.JMSException;
import javax.xml.soap.SOAPMessage;

public interface ReceiveService {
	
	/**
	 * Receive a SOAPMessage from MQ Service.
	 * 
     * @param message the SOAP message specifies JMS (MQ) destination, domain, 
     * clientID, etc. that the MQ Service used to receive message (from MQ).
     *  
	 * 
	 * @throws JMSException if any internal error occurred to receive a message.
     * 
     * @return SOAPMessage the message received from MQ (JMS).
	 */
	public SOAPMessage receive(SOAPMessage message) throws JMSException;

	public void close() throws JMSException;
}
