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
 * @(#)JMSXAConnectionFactory.java	1.5 06/28/07
 */ 

package com.sun.jms.spi.xa;

/** Some application servers provide support for grouping JTS capable 
  * resource use into a distributed transaction (optional). To include JMS transactions
  * in a JTS transaction, an application server requires a JTS aware JMS 
  * provider. A JMS provider exposes its JTS support using a JMS 
  * JMSXAConnectionFactory which an application server uses to create XASessions.
  *
  * <P>JMSXAConnectionFactory's are JMS administered objects just like 
  * ConnectionFactory's. It is expected that application servers will find 
  * them using JNDI.
  *
  * @see         com.sun.jms.spi.xa.JMSXAQueueConnectionFactory
  * @see         com.sun.jms.spi.xa.JMSXATopicConnectionFactory
  */

public interface JMSXAConnectionFactory {
}
