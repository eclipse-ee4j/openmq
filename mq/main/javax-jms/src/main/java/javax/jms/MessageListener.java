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


/** A {@code MessageListener} object is used to receive asynchronously 
  * delivered messages.
  * <p>
  * Each session must ensure that it passes messages serially to the
  * listener. This means that a listener assigned to one or more consumers
  * of the same session can assume that the {@code onMessage} method 
  * is not called with the next message until the session has completed the 
  * last call.
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public interface MessageListener {

    /** Passes a message to the listener.
      *
      * @param message the message passed to the listener
      */

    void 
    onMessage(Message message);
}
