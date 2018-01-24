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
 * @(#)DestinationConfiguration.java	1.10 06/28/07
 */ 

package com.sun.messaging;

/**
 * The <code>DestinationConfiguration</code> class contains Sun MQ specific
 * destination identity configuration property names and values.
 * 
 * @see         com.sun.messaging.Destination com.sun.messaging.Destination
 */
public class DestinationConfiguration {

    /* No public constructor needed */ 
    private DestinationConfiguration() {} 

    /**
     * The property name that holds the name assigned to the
     * <code>Destination</code> object.
     */
    public static final String imqDestinationName = "imqDestinationName";

    /**
     * @deprecated
     * @see com.sun.messaging.DestinationConfiguration#imqDestinationName
     */
    public static final String JMQDestinationName = imqDestinationName;

    /**
     * The property name that holds a description given to the
     * <code>Destination</code> object.
     */
    public static final String imqDestinationDescription = "imqDestinationDescription";

    /**
     * @deprecated
     * @see com.sun.messaging.DestinationConfiguration#imqDestinationDescription
     */
    public static final String JMQDestinationDescription = imqDestinationDescription;

    /**
     * The default name initially given to a <code>Destination</code> object
     * upon instantiation. Typically, either the administrator or programmer will
     * assign a name for this <code>Destination</code> object.
     *
     * @see com.sun.messaging.Destination com.sun.messaging.Destination
     */
    public static final String IMQ_INITIAL_DESTINATION_NAME = "Untitled_Destination_Object";

    /**
     * The default description initially given to a <code>Destination</code> object
     * upon instantiation. Typically, either the administrator or programmer will
     * set a meaningful description for this <code>Destination</code> object.
     *
     * @see com.sun.messaging.Destination com.sun.messaging.Destination
     */
    public static final String IMQ_INITIAL_DESTINATION_DESCRIPTION = "A Description for the Destination Object";
}
