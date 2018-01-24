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

/** A {@code ServerSession} object is an application server object that 
  * is used by a server to associate a thread with a JMS session (optional).
  *
  * <P>A {@code ServerSession} implements two methods:
  *
  * <UL>
  *   <LI>{@code getSession} - returns the {@code ServerSession}'s 
  *       JMS session.
  *   <LI>{@code start} - starts the execution of the 
  *       {@code ServerSession} 
  *       thread and results in the execution of the JMS session's 
  *       {@code run} method.
  * </UL>
  *
  * <P>A {@code ConnectionConsumer} implemented by a JMS provider uses a 
  * {@code ServerSession} to process one or more messages that have 
  * arrived. It does this by getting a {@code ServerSession} from the 
  * {@code ConnectionConsumer}'s {@code ServerSessionPool}; getting 
  * the {@code ServerSession}'s JMS session; loading it with the messages; 
  * and then starting the {@code ServerSession}.
  *
  * <P>In most cases the {@code ServerSession} will register some object 
  * it provides as the {@code ServerSession}'s thread run object. The 
  * {@code ServerSession}'s {@code start} method will call the 
  * thread's {@code start} method, which will start the new thread, and 
  * from it, call the {@code run} method of the 
  * {@code ServerSession}'s run object. This object will do some 
  * housekeeping and then call the {@code Session}'s {@code run} 
  * method. When {@code run} returns, the {@code ServerSession}'s run 
  * object can return the {@code ServerSession} to the 
  * {@code ServerSessionPool}, and the cycle starts again.
  *
  * <P>Note that the JMS API does not architect how the 
  * {@code ConnectionConsumer} loads the {@code Session} with 
  * messages. Since both the {@code ConnectionConsumer} and 
  * {@code Session} are implemented by the same JMS provider, they can 
  * accomplish the load using a private mechanism.
  *
  * @see         javax.jms.ServerSessionPool
  * @see         javax.jms.ConnectionConsumer
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface ServerSession {

    /** Return the {@code ServerSession}'s {@code Session}. This must 
      * be a {@code Session} created by the same {@code Connection} 
      * that will be dispatching messages to it. The provider will assign one or
      * more messages to the {@code Session} 
      * and then call {@code start} on the {@code ServerSession}.
      *
      * @return the server session's session
      *  
      * @exception JMSException if the JMS provider fails to get the associated
      *                         session for this {@code ServerSession} due
      *                         to some internal error.
      **/

    Session
    getSession() throws JMSException;


    /** Cause the {@code Session}'s {@code run} method to be called 
      * to process messages that were just assigned to it.
      *  
      * @exception JMSException if the JMS provider fails to start the server
      *                         session to process messages due to some internal
      *                         error.
      */

    void 
    start() throws JMSException; 
}
