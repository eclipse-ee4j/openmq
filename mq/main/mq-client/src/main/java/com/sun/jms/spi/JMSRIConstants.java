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
 * @(#)JMSRIConstants.java	1.4 06/27/07
 */ 

package com.sun.jms.spi;
import javax.jms.*;
import java.util.Map;

public interface JMSRIConstants {
    final static int QUEUE = 0;
    final static int TOPIC = 1;
    
    /**
     * Return both QUEUE and TOPIC destinations.
     * @see JMSAdmin#getDestinations(int)
     */
    final static int ALL   = 2; 

    // property identifiers for creating ConnectionFactory.

    /**
     * ConnectionFactory property representing jms service host.
     * This property is optional and defaults to accessing the
     * jms service running on the localhost.
     * @see JMSAdmin#createConnectionFactory(int, Map)
     * @see JMSAdmin#createXAConnectionFactory(int, Map)
     */
    final static String CF_URL       = "url";
 
    /**
     * ConnectionFactory property representing transport to use to 
     * connect from JMS client to JMS Service. <p>
     * Defaults to TRANSPORT_RMIIIOP.
     * @see JMSAdmin#createConnectionFactory(int, Map)
     * @see JMSAdmin#createXAConnectionFactory(int, Map)
     * @see #TRANSPORT_RMIIIOP
     * @see #TRANSPORT_JRMP
     */
    final static String CF_TRANSPORT = "transport";

    /**
     * ConnectionFactory propery representing 
     * ClientID to assign to a connection when created
     * from connection factory created with this property
     * set. It is optional
     * to set this value. JMS will generate a default one.
     * ClientID's are only used in scoping durable subscription's
     * namespaces as of JMS 1.0.2.
     * @see JMSAdmin#createConnectionFactory(int, Map)
     * @see JMSAdmin#createXAConnectionFactory(int, Map)
     */
    final static String CF_CLIENT_ID = "clientId";

    /**
     * List of properties for creating a ConnectionFactory.
     * @see JMSAdmin#createConnectionFactory(int, Map)
     * @see JMSAdmin#createXAConnectionFactory(int, Map)
     */
    final static String[] CF_PROPERTIES = { CF_URL, CF_TRANSPORT, CF_CLIENT_ID };

    /**
     * Values for CF_TRANSPORT.
     * @see #CF_TRANSPORT
     */
    final static String TRANSPORT_RMIIIOP = "rmiiiop";
    final static String TRANSPORT_RMIJRMP = "rmijrmp";


    // Map identifiers for Destination creation.

    /**
     * Boolean Value. If true, create overwrites an exisiting destination with same name.
     * If false, throw a JMSException if destination already exists.
     * Also used for durable subscriptions.
     * Defaults to false if not provided in properties.
     * 
     * @see JMSAdmin#createProviderDestination(String, int, Map)
     * @see JMSAdmin#createDurableSubscription(String, TopicConnectionFactory, Topic, String, Map)
     */
    final static String OVERWRITE = "overwrite";

    /**
     * Boolean Value. If true, create a temporary destination and ignore
     * destinationName provided. Defaults to false if not mentioned in 
     * properties.
     *
     * @see JMSAdmin#createProviderDestination(String, int, Map)
     */
    final static String DESTINATION_IS_TEMPORARY = "isTemporary";


    //EXCEPTION CODES
    /**
     * Error code returned by createServiceDestination() when overwriting an
     * existing destination is not allowed.
     */
    final static String DESTINATION_ALREADY_EXISTS = "destinationAlreadyExists";
}


