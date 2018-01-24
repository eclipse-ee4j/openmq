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


/** A {@code Queue} object encapsulates a provider-specific queue name. 
  * It is the way a client specifies the identity of a queue to JMS API methods.
  * For those methods that use a {@code Destination} as a parameter, a 
  * {@code Queue} object used as an argument. For example, a queue can
  * be used  to create a {@code MessageConsumer} and a 
  * {@code MessageProducer}  by calling:
  *<UL>
  *<LI> {@code Session.CreateConsumer(Destination destination)}
  *<LI> {@code Session.CreateProducer(Destination destination)}
  *
  *</UL>
  *
  * <P>The actual length of time messages are held by a queue and the 
  * consequences of resource overflow are not defined by the JMS API.
  *
  * @see Session#createConsumer(Destination)
  * @see Session#createProducer(Destination)
  * @see Session#createQueue(String)
  * @see QueueSession#createQueue(String)
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */
 
public interface Queue extends Destination { 

    /** Gets the name of this queue.
      *  
      * <P>Clients that depend upon the name are not portable.
      *  
      * @return the queue name
      *  
      * @exception JMSException if the JMS provider implementation of 
      *                         {@code Queue} fails to return the queue
      *                         name due to some internal
      *                         error.
      */ 
 
    String
    getQueueName() throws JMSException;  


    /** Returns a string representation of this object.
      *
      * @return the provider-specific identity values for this queue
      */
 
    String
    toString();
}
