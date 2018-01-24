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

/** For application servers, {@code Connection} objects provide a special 
  * facility 
  * for creating a {@code ConnectionConsumer} (optional). The messages it 
  * is to consume are 
  * specified by a {@code Destination} and a message selector. In addition,
  * a {@code ConnectionConsumer} must be given a 
  * {@code ServerSessionPool} to use for 
  * processing its messages.
  *
  * <P>Normally, when traffic is light, a {@code ConnectionConsumer} gets a
  * {@code ServerSession} from its pool, loads it with a single message, and
  * starts it. As traffic picks up, messages can back up. If this happens, 
  * a {@code ConnectionConsumer} can load each {@code ServerSession}
  * with more than one 
  * message. This reduces the thread context switches and minimizes resource 
  * use at the expense of some serialization of message processing.
  *
  * @see javax.jms.Connection#createConnectionConsumer
  * @see javax.jms.Connection#createDurableConnectionConsumer
  * @see javax.jms.QueueConnection#createConnectionConsumer
  * @see javax.jms.TopicConnection#createConnectionConsumer
  * @see javax.jms.TopicConnection#createDurableConnectionConsumer
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface ConnectionConsumer {

    /** Gets the server session pool associated with this connection consumer.
      *  
      * @return the server session pool used by this connection consumer
      *  
      * @exception JMSException if the JMS provider fails to get the server 
      *                         session pool associated with this consumer due
      *                         to some internal error.
      */

    ServerSessionPool 
    getServerSessionPool() throws JMSException; 

 
    /** Closes the connection consumer.
      *
      * <P>Since a provider may allocate some resources on behalf of a 
      * connection consumer outside the Java virtual machine, clients should 
      * close these resources when
      * they are not needed. Relying on garbage collection to eventually 
      * reclaim these resources may not be timely enough.
      *  
      * @exception JMSException if the JMS provider fails to release resources 
      *                         on behalf of the connection consumer or fails
      *                         to close the connection consumer.
      */

    void 
    close() throws JMSException; 
}
