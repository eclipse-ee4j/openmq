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

/** The delivery modes supported by the JMS API are {@code PERSISTENT} and
  * {@code NON_PERSISTENT}.
  *
  * <P>A client marks a message as persistent if it feels that the 
  * application will have problems if the message is lost in transit.
  * A client marks a message as non-persistent if an occasional
  * lost message is tolerable. Clients use delivery mode to tell a
  * JMS provider how to balance message transport reliability with throughput.
  *
  * <P>Delivery mode covers only the transport of the message to its 
  * destination. Retention of a message at the destination until
  * its receipt is acknowledged is not guaranteed by a {@code PERSISTENT} 
  * delivery mode. Clients should assume that message retention 
  * policies are set administratively. Message retention policy
  * governs the reliability of message delivery from destination
  * to message consumer. For example, if a client's message storage 
  * space is exhausted, some messages may be dropped in accordance with 
  * a site-specific message retention policy.
  *
  * <P>A message is guaranteed to be delivered once and only once
  * by a JMS provider if the delivery mode of the message is 
  * {@code PERSISTENT} 
  * and if the destination has a sufficient message retention policy.
  *
  * @version JMS 2.0
  * @since JMS 1.0
  */

public interface DeliveryMode {

    /** This is the lowest-overhead delivery mode because it does not require 
      * that the message be logged to stable storage. The level of JMS provider
      * failure that causes a {@code NON_PERSISTENT} message to be lost is 
      * not defined.
      *
      * <P>A JMS provider must deliver a {@code NON_PERSISTENT} message 
      * with an 
      * at-most-once guarantee. This means that it may lose the message, but it 
      * must not deliver it twice.
      */

    static final int NON_PERSISTENT = 1;

    /** This delivery mode instructs the JMS provider to log the message to stable 
      * storage as part of the client's send operation. Only a hard media 
      * failure should cause a {@code PERSISTENT} message to be lost.
      */

    static final int PERSISTENT = 2;
}
