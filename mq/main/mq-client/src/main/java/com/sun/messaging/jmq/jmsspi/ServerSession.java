/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

/*
 * @(#)ServerSession.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.jmsspi;

/**
 * A ConnectionConsumer implemented by a JMS provider uses a ServerSession
 * to process one or more messages that have arrived. It does this by getting
 * a ServerSession from the ConnectionConsumer's ServerSessionPool; getting
 * the ServerSession's JMS session; loading it with the messages; and then
 * starting the ServerSession.

 * ServerSession is an abstraction of the association between a JMS Session,
 * a thread and a message-bean instance. Each ServerSession given to a
 * ConnectionConsumer represents a single thread of message-delivery.
 *
 * This interface extends javax.jms.ServerSession 
 *
 */

public interface ServerSession extends javax.jms.ServerSession {

    /**
     * Hook to enable container processing just prior to msg delivery.
     * For example, allow a transaction to be started just prior to msg
     * being delivered to the Session MessageListener.
     *
     * To be called by the thread that invokes Session's 
     * MessageListener.onMessage()
     *
     * This call must be paired with the call afterMessageDelivery
     *
     * @param msg Message that is about to be delivered.
     */
    void beforeMessageDelivery(javax.jms.Message msg);

    /**
     * Hook to enable container processing after msg delivery.
     *
     * To be called by the thread that invokes Session's 
     * MessageListener.onMessage()
     *
     * @param msg Message that was delivered.
     */
    void afterMessageDelivery(javax.jms.Message msg);

    /**
     * Indicate that this ServerSession is invalidate and should not 
     * be used again. 
     *
     * To be called by ConnectionConsumer when finds the ServerSession
     * invalid (e.g. unable to getSession or its Session is invalid).
     *
     * This method must not be called after ServerSession.start() has
     * been successfully returned.
     */
    void destroy();
}

