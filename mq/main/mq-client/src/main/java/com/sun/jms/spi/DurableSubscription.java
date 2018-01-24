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
 * @(#)DurableSubscription.java	1.4 06/27/07
 */ 

package com.sun.jms.spi;
import javax.jms.*;

/**
 * A DurableSubscription is a descriptor of a provider-specific
 * durable subscription used for administration purposes.
 *
 * @see com.sun.jms.spi.JMSAdmin#getDurableSubscriptions()
 * @see com.sun.jms.spi.JMSAdmin#getDurableSubscriptions( Topic )
 * @see com.sun.jms.spi.JMSAdmin#createDurableSubscription( String, TopicConnectionFactory, Topic, String, java.util.Map )
 * @see com.sun.jms.spi.JMSAdmin#deleteDurableSubscription( String, TopicConnectionFactory, Topic )
 * @see com.sun.jms.spi.JMSAdmin#deleteDurableSubscription( DurableSubscription ) 
 */
public interface DurableSubscription {


    /**
     * Accessor for the client ID associated with this durable subscription.
     *
     * @return the subscription's client ID
     * @exception JMSException thrown if there are any internal errors
     * @see javax.jms.Connection#getClientID()
     */
    public String getClientID() throws JMSException;


    /**
     * Accessor for the name of the durable subscription.  This value should
     * be identical to the name that would be supplied to the
     * javax.jms.TopicSession.createDurableSubscription() API - it should
     * not be the internal provider-specific name assigned to the subscription.
     *
     * @return the logical name of the durable subscription
     * @exception JMSException thrown if there are any internal errors
     * @see javax.jms.TopicSession#createDurableSubscriber( Topic, String, String, boolean )
     */
    public String getSubscriptionName() throws JMSException;


    /**
     * Accessor for the topic that the subscription is consuming from.
     *
     * @return the topic that the subscription is consuming from
     * @exception JMSException thrown if there are any internal errors
     */
    public Topic getTopic() throws JMSException;


    /**
     * This method returns a TopicConnectionFactory which a client could
     * use to create a subscriber for this durable subscription.
     *
     * @return the associated TopicConnectionFactory or null if one wasn't specified 
     * during creation
     * @exception JMSException thrown if there are any internal errors
     */
    public TopicConnectionFactory getConnectionFactory() throws JMSException;


    /**
     * Accessor for the message selector
     *
     * @return the message selector
     * @exception JMSException thrown if there are any internal errors
     */
    public String getMessageSelector() throws JMSException;


}

