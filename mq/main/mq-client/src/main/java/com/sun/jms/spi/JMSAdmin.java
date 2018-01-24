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
 * @(#)JMSAdmin.java	1.5 06/28/07
 */ 

package com.sun.jms.spi;
import javax.jms.*;
import java.util.Set;

/**
 * Interface definition to provide administrative support of the JMS Server. 
 */

public interface JMSAdmin extends JMSRIConstants {

    
    /**
     * Create a physical Destination within the JMS Provider using the provided
     * properties to define provider specific attributes.
     * Destination is not automatically bound into JNDI namespace.
     * 
     * @param destinationName
     * @param destinationType QUEUE or TOPIC
     * @param properties creation properties.
     * @return Identifier for newly created Destination.
     * @exception JMSException thrown if Queue could not be created.
     */
    Destination createProviderDestination(String destinationName, 
                                          int destinationType, 
					  java.util.Map properties)
        throws JMSException;
    
    /**
     * Get all Destinations.
     * @param destinationType   QUEUE or TOPIC or ALL
     * @return Set of Destinations of destination type.
     * @exception JMSException thrown if set could not be obtained.
     */
    Set getDestinations(int destinationType)
        throws JMSException;

    /**
     * Delete a physical destination within the JMS Provider.
     * @param destinationName
     * @exception JMSException thrown if Queue could not be deleted.
     */
    void deleteProviderDestination(String destinationName)
        throws JMSException;

    /**
     * Get all messages contained within a specified Queue.
     * @param queue
     * @param messageSelector
     * @return QueueBrowser for queue
     * @exception JMSException thrown if browser could not be obtained.
     */
    QueueBrowser createQueueBrowser(Queue queue, String messageSelector)
        throws JMSException;

    /**
     * --- ConnectionFactory
     */

    /**
     * Create a ConnectionFactory.
     * ConnectionFactory is not automatically bound into JNDI namespace.
     *
     * @param connectionType        Either QUEUE or TOPIC. 
     * @param connectionProperties Connection specific properties.
     * @return New created ConnectionFactory.
     * @exception JMSException thrown if connectionFactory could not be created.
     */
    ConnectionFactory createConnectionFactory(int connectionType,
                                              java.util.Map properties)
        throws JMSException;

    /**
     * Create a XAConnectionFactory. 
     * ConnectionFactory is not automatically bound into JNDI namespace.
     *
     * @param connectionType        Either QUEUE or TOPIC.
     * @param connectionProperties Connection specific properties.
     * @return New created XAConnectionFactory. (Object being returned is
     * not in javax.jms standard interface. It is a JMS RI specific interface
     * of XAConnectionFactory. Considering deprecation of 
     * javax.jms.XAConnectionFactory.)
     * @exception JMSException thrown if connectionFactory could not be created.
     */
    Object createXAConnectionFactory(int connectionType,
	      			     java.util.Map properties)
        throws JMSException;

    /**
     * Validate selector string to be used with a JMS Message Consumer.
     * 
     * @param selector   Selector string to validate
     * @exception InvalidSelectorException if the selector is invalid.
     */
    void validateJMSSelector(String selector) throws JMSException;


    /**
     * List all durable subscriptions 
     *
     * @return a set of DurableSubscription objects
     * @exception JMSException thrown if there was an internal provider failure
     *
     * @see com.sun.jms.spi.DurableSubscription
     */
    Set getDurableSubscriptions()
	throws JMSException;
    

    /**
     * List all durable subscriptions consuming from the specified Topic.
     *
     * @param topic the topic to look for durable subscriptions on
     * @return a set of DurableSubscription objects
     * @exception InvalidDestinationException thrown if the specified topic was invalid
     * @exception JMSException thrown if there was an internal provider failure
     *
     * @see com.sun.jms.spi.DurableSubscription
     */
    Set getDurableSubscriptions(Topic topic)
	throws InvalidDestinationException, JMSException;


    /**
     * List all durable subscriptions associated with the specified TopicConnectionFactory
     *
     * @param connFactory the TopicConnectionFactory
     * @return a set of DurableSubscription objects
     * @exception JMSException thrown if there was an internal provider failure
     *
     * @see com.sun.jms.spi.DurableSubscription
     */
    Set getDurableSubscriptions(TopicConnectionFactory connFactory)
	throws JMSException;


    /**
     * Create a durable subscription with the specified parameters in the
     * JMS Provider on the given topic.  Calling this SPI will cause the JMS
     * Provider to seutp a durable subscription on the given topic and then
     * return a descriptor object that can then be used in future administration
     * operations.
     *
     * @param subscriptionName is the logical name of the subscription exactly as it would 
     * be supplied to TopicSession.createDurableSubscriber( Topic, String, String, boolean )
     * @param connFactory is the TopicConnectionFactory that will uniquely identify
     * this subscription when paired with the subscriptionName.  If this parameter is null
     * or does not specify a clientId, it is assumed that the clientId will be specified
     * in the property map.
     * @param topic is the Topic from which this subscription will consume
     * @param messageSelector an optional message selector
     * @param properties optional provider-specific durable subscription properties
     * @return a DurableSubscription descriptor object
     * @exception InvalidDestinationException thrown if the specified topic was invalid
     * @exception InvalidSelectorException thrown if the specified selector was invalid
     * @exception JMSException thrown if there was an internal provider failure or there
     * was an insufficient amount of information specified to uniquely identify the
     * subscription
     * @see javax.jms.TopicSession#createDurableSubscriber( Topic, String, String, boolean )
     */
    DurableSubscription createDurableSubscription(String subscriptionName, 
						  TopicConnectionFactory connFactory,
						  Topic topic,
						  String messageSelector,
						  java.util.Map properties)
	throws InvalidDestinationException, InvalidSelectorException, JMSException;


    /**
     * Delete the specified durable subscription.
     *
     * @param subscriptionName is the logical name of the subscription exactly as it would 
     * be supplied to TopicSession.createDurableSubscriber( Topic, String, String, boolean )
     * @param connFactory is the TopicConnectionFactory that will uniquely identify
     * this subscription when paired with the subscriptionName.  
     * @param topic is the Topic from which the subscriber is consuming from
     * @exception InvalidDestinationException thrown if the specified topic was invalid     
     * @exception JMSException if there are any errors encountered during the delete
     */
    void deleteDurableSubscription(String subscriptionName,
				   TopicConnectionFactory connFactory,
				   Topic topic)
	throws InvalidDestinationException, JMSException;


    /**
     * Delete the specified durable subscription.
     *
     * @param subscription is the DurableSubscription descriptor that identifies
     * the durable subscription that should be deleted.
     * @exception JMSException if there are any errors encountered during the delete
     */
    void deleteDurableSubscription(DurableSubscription subscription) 
	throws javax.jms.JMSException;
}


