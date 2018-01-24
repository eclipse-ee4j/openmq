/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;

/** A {@code ServerSessionPool} object is an object implemented by an 
  * application server to provide a pool of {@code ServerSession} objects 
  * for processing the messages of a {@code ConnectionConsumer} (optional).
  *
  * <P>Its only method is {@code getServerSession}. The JMS API does not 
  * architect how the pool is implemented. It could be a static pool of 
  * {@code ServerSession} objects, or it could use a sophisticated 
  * algorithm to dynamically create {@code ServerSession} objects as 
  * needed.
  *
  * <P>If the {@code ServerSessionPool} is out of 
  * {@code ServerSession} objects, the {@code getServerSession} call 
  * may block. If a {@code ConnectionConsumer} is blocked, it cannot 
  * deliver new messages until a {@code ServerSession} is 
  * eventually returned.
  *
  * @see javax.jms.ServerSession
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface ServerSessionPool {

    /** Return a server session from the pool.
      *
      * @return a server session from the pool
      *  
      * @exception JMSException if an application server fails to
      *                         return a {@code ServerSession} out of its
      *                         server session pool.
      */ 

    ServerSession
    getServerSession() throws JMSException;
}
