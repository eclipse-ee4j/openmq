/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging;

import com.sun.messaging.jmq.DestinationName;
import com.sun.messaging.naming.ReferenceGenerator;
import com.sun.messaging.naming.AdministeredObjectFactory;

/**
 * A <code>Topic</code> represents an identity of a repository of messages used in the JMS Publish/Subscribe messaging
 * domain.
 *
 * @see jakarta.jms.Topic jakarta.jms.Topic
 */
public class Topic extends com.sun.messaging.BasicTopic implements javax.naming.Referenceable {

    private static final long serialVersionUID = -4468834629614174628L;

    /**
     * Constructs an identity of a Publish/Subscribe Topic with the default name
     */
    public Topic() {
    }

    /**
     * Constructs an identity of a Publish/Subscribe Topic with the given name
     *
     * @param name The name of the Topic
     */
    public Topic(String name) throws jakarta.jms.JMSException {
        super(name);
    }

    /**
     * Returns a Reference Object that can be used to reconstruct this object.
     *
     * @return The Reference Object that can be used to reconstruct this object
     *
     */
    @Override
    public javax.naming.Reference getReference() {
        return (ReferenceGenerator.getReference(this, AdministeredObjectFactory.class.getName()));
    }

    /**
     * Sets the name of this Topic. This method performs name validatation This is used by an Application Server via the Sun
     * MQ J2EE Resource Adapter
     *
     * @param name The name of the Topic
     * @throws IllegalArgumentException if name is invalid
     */
    public void setName(String name) {
        if (DestinationName.isSyntaxValid(name)) {
            configuration.put(DestinationConfiguration.imqDestinationName, name);
        } else {
            throw new IllegalArgumentException("MQ:Topic:Invalid Topic Name - " + name);
        }
    }

    /**
     * Sets a description for this Topic. The description can be any String
     *
     * @param description The description for this Topic
     */
    public void setDescription(String description) {
        configuration.put(DestinationConfiguration.imqDestinationDescription, description);
    }

    /**
     * Returns the description for this Topic.
     *
     * @return The description for this Topic
     */
    public String getDescription() {
        try {
            return getProperty(DestinationConfiguration.imqDestinationDescription);
        } catch (jakarta.jms.JMSException jmse) {
            return "";
        }
    }

}
