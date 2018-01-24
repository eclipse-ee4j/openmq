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
 * @(#)JMSXAQueueSession.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

import javax.jms.*;

/** An JMSXAQueueSession provides a regular QueueSession which can be used to
  * create QueueReceivers, QueueSenders and QueueBrowsers (optional).
  *
  * @see         com.sun.jms.spi.xa.XASession
  * @see         javax.jms.QueueSession
  */

public interface JMSXAQueueSession extends JMSXASession {

    /** Get the queue session associated with this JMSXAQueueSession object.
      *  
      * @return the queue session object.
      *  
      * @exception JMSException if a JMS error occurs.
      */ 
 
    QueueSession
    getQueueSession() throws JMSException;
}
