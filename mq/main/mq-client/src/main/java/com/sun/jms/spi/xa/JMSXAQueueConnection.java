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
 * @(#)JMSXAQueueConnection.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

import javax.jms.*;

/** JMSXAQueueConnection creates JMSXAQueueSession and provides the QueueConnection associated
  * with this JMSXAQueueConnection.
  *
  * @see         com.sun.jms.spi.xa.XAConnection
  */

public interface JMSXAQueueConnection 
	extends JMSXAConnection {

    /** Create an XAQueueSession.
      *  
      * @exception JMSException if JMS Connection fails to create a
      *                         XA queue session due to some internal error.
      */ 

    JMSXAQueueSession
    createXAQueueSession(boolean transacted, int acknowledgeMode) throws JMSException;

    /** get an QueueConnection associated with this XAQueueConnection object.
      *  
      * @return a QueueConnection.
      */ 
    QueueConnection getQueueConnection();
}
