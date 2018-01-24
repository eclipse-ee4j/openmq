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

package com.sun.messaging.jms.ra;

import javax.jms.*;
import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;

import com.sun.messaging.jmq.jmsservice.JMSService;

/**
 *
 */
public abstract class ConnectionMetaData implements javax.jms.ConnectionMetaData {

    /**
     *  Holds the configuration properties of this JMS Connection
     */
    protected Properties connectionProps;

    /**
     *  Holds the JMSX property names that are supported by this JMS Connection
     *  as required by the JMS Specification. The names are returned by the
     *  {@code getJMSXPropertyNames} method.
     *
     *  {@link javax.jms.ConnectionMetaData#getJMSXPropertyNames}
     */
    private Vector <String> supportedProperties = new Vector <String> (7);
    
    /** Creates a new instance of ConnectionMetaData */
    public ConnectionMetaData(Properties connectionProps) {
        this.connectionProps = connectionProps;

        //The first two properties are supported by default and set by apps
        //if needed
        supportedProperties.addElement(
                JMSService.JMSXProperties.JMSXGroupID.toString());
        supportedProperties.addElement(
                JMSService.JMSXProperties.JMSXGroupSeq.toString());

        //The subsequent properties are supported *only* if the connection
        //is configured to support them
        if (hasJMSXAppID())
            supportedProperties.addElement(
                    JMSService.JMSXProperties.JMSXAppID.toString());
        if (hasJMSXUserID())
            supportedProperties.addElement(
                    JMSService.JMSXProperties.JMSXUserID.toString());
        if (hasJMSXProducerTXID())
            supportedProperties.addElement(
                    JMSService.JMSXProperties.JMSXProducerTXID.toString());
        if (hasJMSXConsumerTXID())
            supportedProperties.addElement(
                    JMSService.JMSXProperties.JMSXConsumerTXID.toString());
        if (hasJMSXRcvTimestamp())
            supportedProperties.addElement(
                    JMSService.JMSXProperties.JMSXRcvTimestamp.toString());
        supportedProperties.addElement(
            JMSService.JMSXProperties.JMSXDeliveryCount.toString());
    }

    protected abstract boolean hasJMSXAppID();
    protected abstract boolean hasJMSXUserID();
    protected abstract boolean hasJMSXProducerTXID();
    protected abstract boolean hasJMSXConsumerTXID();
    protected abstract boolean hasJMSXRcvTimestamp();

    /////////////////////////////////////////////////////////////////////////
    //  Methods implementing javax.jms.ConnectionMetaData
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Returns the major version number of the JMS API that this JMS Connection
     *  implements.
     *
     *  @return The major version number of the JMS API that this JMS Connection
     *          implements.
     */
    public int getJMSMajorVersion() throws JMSException {
        return 2;
        //JMSMajorVersion; -> Version.getJMSMajorVersion();
    }

     /**
     *  Returns the minor version number of the JMS API that this JMS Connection
     *  implements.
     *
     *  @return The minor version number of the JMS API that this JMS Connection
     *          implements
     */
    public int getJMSMinorVersion() throws JMSException {
        return 0;
        //JMSMinorVersion; -> Version.getJMSMinorVersion();
    }

    /**
     *  Returns the JMS Provider Name for this JMS Connection
     *
     *  @return The JMS Provider Name for this JMS Connection
     */
    public String getJMSProviderName() throws JMSException {
        return "Oracle GlassFish(tm) Server Message Queue";
        //JMSProviderName; -> Version.getProductName();
    }

    /**
     *  Returns the JMS API Version for this JMS Connection
     *
     *  @return The JMS API Version for this JMS Connection
     */
    public String getJMSVersion() throws JMSException {
        return "2.0";
        //JMSVersion; -> Version.getTargetJMSVersion();
    }

    /**
     *  Returns the JMSX properties that this JMS Connection supports
     *
     *  @return The supported JMSX properties as an Enumeration
     */
    public Enumeration getJMSXPropertyNames() throws JMSException {
        return supportedProperties.elements();
    }

    /**
     *  Returns the JMS Provider's major version number for this JMS Connection
     *
     *  @return The JMS Provider's major version number for this JMS Connection
     */
    public int getProviderMajorVersion() throws JMSException {
        return 5;
        //ProviderMajorVersion; -> Version.getMajorVersion();
    }

    /**
     *  Returns the JMS Provider's minor version number for this JMS Connection
     *
     *  @return The JMS Provider's minor version number for this JMS Connection
     */
    public int getProviderMinorVersion() throws JMSException {
        return 0;
        //ProviderMinorVersion; -> Version.getMinorVersion();
    }

    /**
     *  Returns the JMS API Version for this JMS Connection
     *
     *  @return The JMS API Version for this JMS Connection
     */
    public String getProviderVersion() throws JMSException {
        return "5.0";
        //ProviderVersion; -> Version.getProviderVersion();
    }
    /////////////////////////////////////////////////////////////////////////
    // End methods implementing javax.jms.ConnectionMetaData
    /////////////////////////////////////////////////////////////////////////

    /**
     *
     */
    public Properties getConnectionProperties() {
        return this.connectionProps;
    }
}
