/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jms.ra;

import jakarta.jms.*;
import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;

import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsclient.ConnectionMetaDataAdapter;

public abstract class ConnectionMetaData extends ConnectionMetaDataAdapter {

    /**
     * Holds the configuration properties of this JMS Connection
     */
    protected Properties connectionProps;

    /**
     * Holds the JMSX property names that are supported by this JMS Connection as required by the JMS Specification. The
     * names are returned by the {@code getJMSXPropertyNames} method.
     *
     * {@link jakarta.jms.ConnectionMetaData#getJMSXPropertyNames}
     */
    private Vector<String> supportedProperties = new Vector<>(7);

    /** Creates a new instance of ConnectionMetaData */
    public ConnectionMetaData(Properties connectionProps) {
        this.connectionProps = connectionProps;

        // The first two properties are supported by default and set by apps
        // if needed
        supportedProperties.addElement(JMSService.JMSXProperties.JMSXGroupID.toString());
        supportedProperties.addElement(JMSService.JMSXProperties.JMSXGroupSeq.toString());

        // The subsequent properties are supported *only* if the connection
        // is configured to support them
        if (hasJMSXAppID()) {
            supportedProperties.addElement(JMSService.JMSXProperties.JMSXAppID.toString());
        }
        if (hasJMSXUserID()) {
            supportedProperties.addElement(JMSService.JMSXProperties.JMSXUserID.toString());
        }
        if (hasJMSXProducerTXID()) {
            supportedProperties.addElement(JMSService.JMSXProperties.JMSXProducerTXID.toString());
        }
        if (hasJMSXConsumerTXID()) {
            supportedProperties.addElement(JMSService.JMSXProperties.JMSXConsumerTXID.toString());
        }
        if (hasJMSXRcvTimestamp()) {
            supportedProperties.addElement(JMSService.JMSXProperties.JMSXRcvTimestamp.toString());
        }
        supportedProperties.addElement(JMSService.JMSXProperties.JMSXDeliveryCount.toString());
    }

    protected abstract boolean hasJMSXAppID();

    protected abstract boolean hasJMSXUserID();

    protected abstract boolean hasJMSXProducerTXID();

    protected abstract boolean hasJMSXConsumerTXID();

    protected abstract boolean hasJMSXRcvTimestamp();

    /////////////////////////////////////////////////////////////////////////
    // Methods implementing jakarta.jms.ConnectionMetaData
    /////////////////////////////////////////////////////////////////////////
    /**
     * Returns the JMSX properties that this JMS Connection supports
     *
     * @return The supported JMSX properties as an Enumeration
     */
    @Override
    public Enumeration getJMSXPropertyNames() throws JMSException {
        return supportedProperties.elements();
    }

    /////////////////////////////////////////////////////////////////////////
    // End methods implementing jakarta.jms.ConnectionMetaData
    /////////////////////////////////////////////////////////////////////////

    public Properties getConnectionProperties() {
        return this.connectionProps;
    }
}
