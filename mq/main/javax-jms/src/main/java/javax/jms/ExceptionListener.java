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


/** If a JMS provider detects a serious problem with a {@code Connection}
  * object, it informs the {@code Connection} object's 
  * {@code ExceptionListener}, if one has been registered. 
  * It does this by calling the listener's {@code onException} method, 
  * passing it a {@code JMSException} argument describing the problem.
  *
  * <P>An exception listener allows a client to be notified of a problem 
  * asynchronously. Some connections only consume messages, so they would have no
  * other way to learn that their connection has failed.
  *
  * <P>A JMS provider should attempt to resolve connection problems 
  * itself before it notifies the client of them.
  *
  * @see javax.jms.Connection#setExceptionListener(ExceptionListener)
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  *
  */

public interface ExceptionListener {

    /** Notifies user of a JMS exception.
      *
      * @param exception the JMS exception
      */

    void 
    onException(JMSException exception);
}
