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
 * @(#)Destination.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.jmsservice;

import java.io.Serializable;

/**
 *
 */
public class Destination implements Serializable {

    /**
     *  Enum values that specify the Type of the Destination
     *
     *  @see javax.jms.Destination javax.jms.Destination
     */
    public static enum Type {
        /**
         *  The Destination is a Queue destination as defined by the JMS
         *  Specification
         *
         *  @see javax.jms.Queue javax.jms.Queue
         */
        QUEUE,

        /**
         *  The Destination is a Topic destination as defined by the JMS
         *  Specification
         *
         *  @see javax.jms.Topic javax.jms.Topic
         */
        TOPIC
    }

    /**
     *  Enum values that specify the Life of the Destination
     */
    public static enum Life {
        /**
         *  The Destination is a Standard Destination as defined by the JMS
         *  Specification
         *
         *  @see javax.jms.Queue javax.jms.Queue
         *  @see javax.jms.Topic javax.jms.Topic
         */
        STANDARD,

        /**
         *  The Destination is a TemporaryDestination as defined by the JMS
         *  Specification
         *
         *  @see javax.jms.TemporaryQueue javax.jms.TemporaryQueue
         *  @see javax.jms.TemporaryTopic javax.jms.TemporaryTopic
         */
        TEMPORARY
    }

    /**
     *  Enum values that specify how the physical Destination was created
     */
    public static enum CreationType {
        /**
         *  The Destination is automatically created
         */
        AUTO,

        /**
         *  The Destination is administratively created
         */
        ADMIN
    }  

    /** Enum value that specify the temporary destination name prefix */
    public static enum TemporaryType {
        queue,
        topic
    }

    /** Definition of TemporaryQueue and TemporaryTopic name prefixes */
    public static final String TEMPORARY_DESTINATION_PREFIX =
            "temporary_destination://";
    public static final String TEMPORARY_QUEUE_NAME_PREFIX = "queue/";
    public static final String TEMPORARY_TOPIC_NAME_PREFIX = "topic/";

    /** The name of the Destination */
    private String name;

    /** The Type of the Destination */
    private Type type;

    /** The Life of this Destination */
    private Life life;

    /** The CreationType of this Destination */
    private CreationType creationType;
    
    /** Creates a new instance of a Destination */
    public Destination (String name, Type type, Life life) {
        this.name = name;
        this.type = type;
        this.life = life;
        this.creationType = CreationType.AUTO;
    }

    /**
     *  returns the Name of the Destination
     *
     *  @return The name of the Destination
     */
    public String getName() {
        return name;
    }

    /**
     *  returns the DestinationType of this Destination
     *
     *  @return The DestinationType
     */
    public Type getType() {
        return this.type;
    }

    /**
     *  returns the DestinationLifeSpan of this Destination
     *
     *  @return The DestinationLifeSpan
     */
    public Life getLife() {
        return this.life;
    }

    /**
     *  Set the creationType for this Destination
     *
     *  @param creationType The JMSService.DestinationCreation value
     */
    public void setCreationType(CreationType creationType) {
        this.creationType = creationType;
    }

    /**
     *  Return the creationType for this Destination
     *
     *  @return The creationType
     */
    public CreationType getCreationType() {
        return this.creationType;
    }
}
