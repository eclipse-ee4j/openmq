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

/** A {@code TemporaryQueue} object is a unique {@code Queue} object 
  * created for the duration of a {@code Connection}. It is a 
  * system-defined queue that can be consumed only by the 
  * {@code Connection} that created it.
  *
  *<P>A {@code TemporaryQueue} object can be created at either the 
  * {@code Session} or {@code QueueSession} level. Creating it at the
  * {@code Session} level allows to the {@code TemporaryQueue} to 
  * participate in transactions with objects from the Pub/Sub  domain. 
  * If it is created at the {@code QueueSession}, it will only
  * be able participate in transactions with objects from the PTP domain.
  *
  * @see Session#createTemporaryQueue()
  * @see QueueSession#createTemporaryQueue()
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface TemporaryQueue extends Queue {

    /** Deletes this temporary queue. If there are existing receivers
      * still using it, a {@code JMSException} will be thrown.
      *  
      * @exception JMSException if the JMS provider fails to delete the 
      *                         temporary queue due to some internal error.
      */

    void 
    delete() throws JMSException; 
}
