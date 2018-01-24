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
 * @(#)JMSXATopicSession.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

import javax.jms.*;

/** An JMSXATopicSession provides a regular TopicSession which can be used to
  * create TopicSubscribers and TopicPublishers (optional).
  *
  * @see         com.sun.jms.spi.xa.JMSXASession
  * @see         javax.jms.TopicSession
  */

public interface JMSXATopicSession extends JMSXASession {

    /** Get the topic session associated with this JMSXATopicSession object.
      *   
      * @return the topic session object. 
      *   
      * @exception JMSException if a JMS error occurs.
      */  
  
    TopicSession 
    getTopicSession() throws JMSException;
}
