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
 * @(#)Queue.java	1.11 06/28/07
 */ 

package com.sun.messaging;

import com.sun.messaging.jmq.DestinationName;
import com.sun.messaging.naming.ReferenceGenerator;
import com.sun.messaging.naming.AdministeredObjectFactory;

/**
 * A <code>Queue</code> represents an identity of a repository of messages
 * used in the JMS Point-To-Point messaging domain.
 *
 * @see         javax.jms.Queue javax.jms.Queue
 */
public class Queue extends com.sun.messaging.BasicQueue implements javax.naming.Referenceable {

    /**
     * Constructs an identity of a Point-To-Point Queue with the default name
     */
    public Queue () {
	super();
    }

    /**
     * Constructs an identity of a Point-To-Point Queue with the given name
     *
     * @param   name The name of the Queue
     */
    public Queue (String name) throws javax.jms.JMSException {
	super(name);
    }

    /**
     * Returns a Reference Object that can be used to reconstruct this object.
     *
     * @return  The Reference Object that can be used to reconstruct this object
     *
     */
    public javax.naming.Reference getReference() {
        return (ReferenceGenerator.getReference(this, AdministeredObjectFactory.class.getName()));
    }

    /**
     * Sets the name of the Queue. This method performs name validatation
     * This is used by an Application Server via the Sun MQ J2EE Resource Adapter
     *
     * @param   name The name of the Queue
     * @throws  IllegalArgumentException if name is invalid
     */
    public void setName (String name) {
        if (DestinationName.isSyntaxValid(name)) {
            configuration.put(DestinationConfiguration.imqDestinationName, name);
        } else {
            throw new IllegalArgumentException("MQ:Queue:Invalid Queue Name - " + name);
        }
    }

    /**
     * Sets a description for this Queue. The description can be any String
     *
     * @param   description The description for this Queue
     */
    public void setDescription (String description) {
        configuration.put(DestinationConfiguration.imqDestinationDescription, description);
    }

    /**
     * Returns the description for this Queue.
     *   
     * @return The description for this Queue
     */  
    public String
    getDescription()
    {
        try { 
            return getProperty(DestinationConfiguration.imqDestinationDescription);
        } catch (javax.jms.JMSException jmse) {
            return "";
        } 
    }

}
