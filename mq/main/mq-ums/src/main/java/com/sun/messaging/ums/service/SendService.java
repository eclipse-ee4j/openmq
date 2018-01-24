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

import com.sun.messaging.ums.simple.SimpleMessage;
import javax.jms.JMSException;
import javax.xml.soap.SOAPMessage;

public interface SendService {
	
	/**
	 * Send a SOAP message to MQ.
	 * 
	 * 
	 * @param message the soap message to be sent to MQ.
	 * 
	 * @throws JMSException if any internal error occurred to send the message.
	 */
	public void send (SOAPMessage message) throws JMSException;
        
        public void commit (SOAPMessage message) throws JMSException;
        
        public void rollback (SOAPMessage message) throws JMSException;

        public void commit (SimpleMessage message) throws JMSException;
        
        public void rollback (SimpleMessage message) throws JMSException;
	
        
        /**
	 * Close the send service resources.
	 * 
	 * @throws JMSException
	 */
	public void close() throws JMSException;
}
