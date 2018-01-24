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
 * @(#)Destination.java	1.19 06/28/07
 */ 

package com.sun.messaging;

import com.sun.messaging.jmq.DestinationName;
import com.sun.messaging.jmq.ClientConstants;
import java.util.Properties;
import javax.jms.*;

/**
 * A <code>Destination</code> encapsulates Sun MQ specific configuration information
 * for Sun MQ <code>Destination</code> objects.
 *
 * @see         javax.jms.Destination javax.jms.Destination
 * @see         com.sun.messaging.DestinationConfiguration com.sun.messaging.DestinationConfiguration
 */
public abstract class Destination extends AdministeredObject implements javax.jms.Destination {

    /** The default basename for AdministeredObject initialization */
    private static final String defaultsBase = "Destination";

    /**
     * Constructs an "untitled" Destination.
     */
    public Destination() {
        super(defaultsBase);
    }

    /**
     * Constructs a Destination given the name
     *
     * @param   name The name of the Destination
     * @see     InvalidDestinationException If <code><b>name</b></code> is an invalid destination name
     */
    public Destination (String name) throws InvalidDestinationException {
        super(defaultsBase);
        String errorString;
        if (name == null || "".equals(name)) {
            errorString =
                AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DESTINATION_NAME);
            throw new InvalidDestinationException(errorString,
                AdministeredObject.cr.X_INVALID_DESTINATION_NAME);
        }
        //Allow temporary destinations to have names that normal destinations cannot
        if (isTemporary()) {
            configuration.put(DestinationConfiguration.imqDestinationName, name);
        } else {
            if (DestinationName.isSyntaxValid(name)) {
                configuration.put(DestinationConfiguration.imqDestinationName, name);
            } else {
                errorString =
                    AdministeredObject.cr.getKString(AdministeredObject.cr.X_INVALID_DESTINATION_NAME, name);
                throw new InvalidDestinationException(errorString,
                    AdministeredObject.cr.X_INVALID_DESTINATION_NAME);
            }
        }
    }

    /**
     * Returns the name of this Destination.
     *  
     * @return the Destination name
     */ 
    public String getName() {
        try {
            return super.getProperty(DestinationConfiguration.imqDestinationName);
        } catch (JMSException e) {
            return ("");
        }
    }

    /**
     * Returns the queue name.
     * 
     * @return the queue name
     *
     * @exception JMSException if a queue access error occurs.
     */
    public String getQueueName() throws JMSException {
        return getName();
    }

    /**
     * Returns the topic name.
     *   
     * @return the topic name
     *
     * @exception JMSException if a topic access error occurs.
     */
    public String getTopicName() throws JMSException {
        return getName();
    }
 
    /**
     * Returns a pretty printed version of the provider specific
     * information for this Destination identity object.
     * 
     * @return the pretty printed string.
     */
    public String toString() {
        return ("Oracle GlassFish(tm) Server MQ Destination\ngetName():\t\t" + getName() + super.toString());
    }
 
    /**
     * Returns whether this is a Queueing type of Destination.
     *
     * @return whether this is a Queueing type of Destination.
     */
    public abstract boolean isQueue();

    /**
     * Returns whether this is a Temporary type of Destination.
     *
     * @return whether this is a Temporary type of Destination.
     */
    public abstract boolean isTemporary();

    /** 
     * Sets the minimum <code>Destination</code> configuration defaults 
     * required of a Sun MQ Destination identity object.
     */ 
    public void setDefaultConfiguration() {
        configuration = new Properties();
        configurationTypes = new Properties();
        configurationLabels = new Properties();          

        configuration.put(DestinationConfiguration.imqDestinationName,
                            DestinationConfiguration.IMQ_INITIAL_DESTINATION_NAME);
        configurationTypes.put(DestinationConfiguration.imqDestinationName,
                            AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(DestinationConfiguration.imqDestinationName,
                            AdministeredObject.cr.L_JMQDESINTATION_NAME);

        configuration.put(DestinationConfiguration.imqDestinationDescription,
                            DestinationConfiguration.IMQ_INITIAL_DESTINATION_DESCRIPTION);
        configurationTypes.put(DestinationConfiguration.imqDestinationDescription,
                            AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(DestinationConfiguration.imqDestinationDescription,
                            AdministeredObject.cr.L_JMQDESINTATION_DESC);
    }

    /**
     * Validates a <code>Destination</code> name.
     *
     * @param name The <code>Destination</code> name.
     *
     * @return <code>true</code> if the name is valid;
     *         <code>false</code> if the name is invalid.
     *
     */
    public Boolean validate_imqDestinationName(String name) {
        if (isTemporary()) {
            if ((name != null) && name.startsWith(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX)) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } else {
            return Boolean.valueOf(DestinationName.isSyntaxValid(name));
        }
    }
}
