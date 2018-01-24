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

import java.util.Enumeration;

/** A client uses a {@code QueueBrowser} object to look at messages on a 
  * queue without removing them.
  *
  * <P>The {@code getEnumeration} method returns a 
  * {@code java.util.Enumeration} that is used to scan 
  * the queue's messages. It may be an enumeration of the entire content of a 
  * queue, or it may contain only the messages matching a message selector.
  *
  * <P>Messages may be arriving and expiring while the scan is done. The JMS API
  * does 
  * not require the content of an enumeration to be a static snapshot of queue 
  * content. Whether these changes are visible or not depends on the JMS 
  * provider.
  * <p>
  * A message must not be returned by a {@code QueueBrowser} before its delivery time has been reached.
  *
  *<P>A {@code QueueBrowser} can be created from either a 
  * {@code Session} or a {@code  QueueSession}. 
  * 
  * @see         javax.jms.Session#createBrowser
  * @see         javax.jms.QueueSession#createBrowser
  * @see         javax.jms.QueueReceiver
  *
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface QueueBrowser extends AutoCloseable {

    /** Gets the queue associated with this queue browser.
      * 
      * @return the queue
      *  
      * @exception JMSException if the JMS provider fails to get the
      *                         queue associated with this browser
      *                         due to some internal error.
      */

    Queue 
    getQueue() throws JMSException;


    /** Gets this queue browser's message selector expression.
      *  
      * @return this queue browser's message selector, or null if no
      *         message selector exists for the message consumer (that is, if 
      *         the message selector was not set or was set to null or the 
      *         empty string)
      *
      * @exception JMSException if the JMS provider fails to get the
      *                         message selector for this browser
      *                         due to some internal error.
      */

    String
    getMessageSelector() throws JMSException;


    /** Gets an enumeration for browsing the current queue messages in the
      * order they would be received.
      *
      * @return an enumeration for browsing the messages
      *  
      * @exception JMSException if the JMS provider fails to get the
      *                         enumeration for this browser
      *                         due to some internal error.
      */

    Enumeration 
    getEnumeration() throws JMSException;


    /** Closes the {@code QueueBrowser}.
      *
      * <P>Since a provider may allocate some resources on behalf of a 
      * QueueBrowser outside the Java virtual machine, clients should close them
      * when they 
      * are not needed. Relying on garbage collection to eventually reclaim 
      * these resources may not be timely enough.
      *
      * @exception JMSException if the JMS provider fails to close this
      *                         browser due to some internal error.
      */

    void 
    close() throws JMSException;
}
