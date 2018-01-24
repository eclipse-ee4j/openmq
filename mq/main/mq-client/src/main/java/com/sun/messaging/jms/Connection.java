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
 * @(#)Connection.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms;

import javax.jms.JMSException;
import javax.jms.Session;

import com.sun.messaging.Destination;
import com.sun.messaging.jms.notification.EventListener;

/**
 * This interafce provides the following API for the MQ applications:
 * <p>
 * 1. Provide API to create a MQ NO_ACKNOWLEDGE session.
 * <p>
 * 2. Provide API to set the connection event listener.
 * <p>
 * 3. Provide API to query broker adress and HA state.
 */
public interface Connection extends javax.jms.Connection {

    /** Creates a <CODE>Session</CODE> object.
      *
      * @param acknowledgeMode indicates whether the consumer or the
      * client will acknowledge any messages it receives;
      * Legal values are <code>Session.AUTO_ACKNOWLEDGE</code>,
      * <code>Session.CLIENT_ACKNOWLEDGE</code>,
      * <code>Session.DUPS_OK_ACKNOWLEDGE</code>, and
      * <code>com.sun.messaging.jms.Session.NO_ACKNOWLEDGE</code>
      *
      * @return a newly created  session
      *
      * @exception JMSException if the <CODE>Connection</CODE> object fails
      *                         to create a session due to some internal error or
      *                         lack of support for the specific transaction
      *                         and acknowledgement mode.
      *
      * @see Session#AUTO_ACKNOWLEDGE
      * @see Session#CLIENT_ACKNOWLEDGE
      * @see Session#DUPS_OK_ACKNOWLEDGE
      * @see com.sun.messaging.jms.Session#NO_ACKNOWLEDGE
      */

    public Session
    createSession(int acknowledgeMode) throws JMSException;

    /**
     * Set MQ connection event listener to the current connection.
     *
     * @param listener EventListener
     * @throws JMSException
     */
    public void
    setEventListener (EventListener listener) throws JMSException;

    /**
     * Set consumer event listener on a destination to the current connection. 
     *
     * @param dest the destination on which consumer event is interested 
     * @param listener EventListener
     * @throws JMSException
     * @since 4.5 
     */
    public void
    setConsumerEventListener (Destination dest,
                              EventListener listener)
                              throws JMSException;

    /**
     * Remove a MQ consumer event listener from the current connection.
     *
     * @param dest the destination on which addConsumerEventListener() was called previously 
     * @param listener EventListener
     * @throws JMSException
     * @since 4.5 
     */
    public void
    removeConsumerEventListener (Destination dest) throws JMSException;

    /**
     * Get the broker's address that the connection is connected (related) to.
     *
     * @return the broker's address that the connection is connected (related)
     *         to.
     */
    public String getBrokerAddress();

    /**
     * Get the current connection state.
     *
     * @return true if the connection is connected to a HA broker.
     *         false if not connected to a HA broker.
     */
    public boolean isConnectedToHABroker();

}
