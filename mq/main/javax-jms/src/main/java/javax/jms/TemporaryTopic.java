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

/** A {@code TemporaryTopic} object is a unique {@code Topic} object 
  * created for the duration of a {@code Connection}. It is a 
  * system-defined topic that can be consumed only by the 
  * {@code Connection} that created it.
  *
  *<P>A {@code TemporaryTopic} object can be created either at the 
  * {@code Session} or {@code TopicSession} level. Creating it at the
  * {@code Session} level allows the {@code TemporaryTopic} to participate
  * in the same transaction with objects from the PTP domain. 
  * If a {@code TemporaryTopic} is  created at the 
  * {@code TopicSession}, it will only
  * be able participate in transactions with objects from the Pub/Sub domain.
  *
  * @see Session#createTemporaryTopic()
  * @see TopicSession#createTemporaryTopic()
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface TemporaryTopic extends Topic {

    /** Deletes this temporary topic. If there are existing subscribers
      * still using it, a {@code JMSException} will be thrown.
      *  
      * @exception JMSException if the JMS provider fails to delete the
      *                         temporary topic due to some internal error.
      */

    void 
    delete() throws JMSException; 
}
