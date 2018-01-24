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
 * @(#)CFObjectFactory.java	1.6 07/02/07
 */ 

package com.sun.messaging.naming;

import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionFactory;
import com.sun.messaging.QueueConnectionFactory;
import com.sun.messaging.TopicConnectionFactory;
import com.sun.messaging.ConnectionConfiguration;

/**
 * <code>CFObjectFactory</code> handles instance creation for
 * <code>com.sun.messaging.QueueConnectionFactory</code>
 * and <code>com.sun.messaging.TopicConnectionFactory</code> objects from JMQ1.1 created Reference objects.
 * <p>
 * It specifically handles the format conversion from
 * Reference objects created by JMQ1.1 <code>ConnectionFactory</code> objects
 * to iMQ3.0 <code>ConnectionFactory</code> objects.
 * <p>
 * @see javax.naming.Reference javax.naming.Reference
 * @see com.sun.messaging.ConnectionFactory com.sun.messaging.ConnectionFactory
 * @see com.sun.messaging.AdministeredObject com.sun.messaging.AdministeredObject
 * @since JMQ2.0
 */
 
/* 
 * IMPORTANT:
 * ==========
 * The size of the reference for JMQ1.1 ConnectionFactory objects is always 11.
 * The format of the reference in JMQ1.1 is always
 * as follows --
 *
 *  [0] = reserved for version
 *  [1] = reserved for securityPort
 *  [2] = reserved for JMSXUserID
 *  [3] = reserved for JMSXAppID
 *  [4] = reserved for JMSXProducerTXID
 *  [5] = reserved for JMSXConsumerTXID
 *  [6] = reserved for JMSXRcvTimestamp
 *  [7] = reserved for parm
 *  [8] = reserved for host
 *  [9] = reserved for subnet
 * [10] = reserved for ackTimeout
 *
 */

public abstract class CFObjectFactory extends AdministeredObjectFactory {

    /** used only by ConnectionFactory reference objects */
    private static final String REF_SECURITYPORT = "securityPort";
    private static final String REF_JMSXUSERID = "JMSXUserID";
    private static final String REF_JMSXAPPID = "JMSXAppID";
    private static final String REF_JMSXPRODUCERTXID = "JMSXProducerTXID";
    private static final String REF_JMSXCONSUMERTXID = "JMSXConsumerTXID";
    private static final String REF_JMSXRCVTIMESTAMP = "JMSXRcvTimestamp";
    private static final String REF_PARM = "parm";
    private static final String REF_HOST = "host";
    private static final String REF_SUBNET = "subnet";
    private static final String REF_ACKTIMEOUT = "ackTimeout";
    /** the content of the parm, if the configuration object exists */
    private static final String REF_PARM_CONTENT = "--";
 
    /** JMSXxxx properties */
    private static final String JMSXUSERID = "JMSXUserID";
    private static final String JMSXAPPID = "JMSXAppID";
    private static final String JMSXPRODUCERTXID = "JMSXProducerTXID";
    private static final String JMSXCONSUMERTXID = "JMSXConsumerTXID";
    private static final String JMSXRCVTIMESTAMP = "JMSXRcvTimestamp";
 
    /**
     * generic default value: if value is not specified in the reference
     * object, its value defaults to this value
     */
    private static final String DEFAULT = "default";
 
    /** the prefix to the attributes of the ConnectionFactyory objects */
    private static final String PREF_HOST = "-s";
    private static final String PREF_SUBNET = "-n";
    private static final String PREF_ACKTIMEOUT = "-t";
 
    /** default values for attributes */
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_SUBNET = 0;
    private static final int DEFAULT_SECURITYPORT = 22000;
    private static final int DEFAULT_ACKTIMEOUT = 30000;

    /**
     * Creates an instance of the object represented by a Reference object.
     *   
     * @param obj The Reference object.
     *   
     * @return an instance of the class named in the Reference object <code>obj</code>.
     * @return null if <code>obj</code> is not an instance of a Reference object.
     *   
     * @throws MissingVersionNumberException if either <code>obj</code> references an object
     *         that is not an instance of a <code>com.sun.messaging.Queue</code> object
     *         or the version number is missing from the Reference object.
     * @throws UnsupportedVersionNumberException if an unsupported version number is present
     *         in the Reference.
     * @throws CorruptedConfigurationPropertiesException if <code>obj</code> does not have the
     *         minimum information neccessary to recreate an instance of a
     *         a valid <code>com.sun.messaging.AdministeredObject</code>.
     */  
    public
    Object getObjectInstance
        (Object obj, Name name, Context ctx, Hashtable env) throws Exception {

        if (obj instanceof Reference) {
            String parm = null;
            String host = null;
            String subnet = null;
            String ackTimeout = null;
            Reference ref = (Reference)obj;
            String refClassName = ref.getClassName();
            ConnectionFactory cf;
            if (refClassName.equals(com.sun.messaging.QueueConnectionFactory.class.getName())) {
                cf = new QueueConnectionFactory();
            } else {
                if (refClassName.equals(com.sun.messaging.TopicConnectionFactory.class.getName())) {
                    cf = new TopicConnectionFactory();
                } else {
                    throw new MissingVersionNumberException();
                }
            }

            //version number MUST exist and it MUST be the same as AO_VERSION_STR_JMQ1
            RefAddr versionAddr = ref.get(REF_VERSION);
            if (versionAddr == null) {
                //version number does not exist
                throw new MissingVersionNumberException();
            } else {
                //version number does not match
                String version = null;
                if (!AO_VERSION_STR_JMQ1.equals (version = (String)versionAddr.getContent())) {
                    throw new UnsupportedVersionNumberException(version);
                }
                ((AdministeredObject)cf).storedVersion = version;
            }   
            String securityPort = DEFAULT;

            // retreive the security port value from the Reference
            RefAddr securityPortAddr = ref.get(REF_SECURITYPORT);
            if (securityPortAddr != null) {
                securityPort = (String)securityPortAddr.getContent();
            } else {
                //securityPort is missing - corrupted?
                throw new CorruptedConfigurationPropertiesException();
            }
            /*
            try {
                parm = (String)
                    (((RefAddr)ref.get(REF_PARM)).getContent());
                host = (String)
                    (((RefAddr)ref.get(REF_HOST)).getContent());
                subnet = (String)
                    (((RefAddr)ref.get(REF_SUBNET)).getContent());
                ackTimeout = (String)
                    (((RefAddr)ref.get(REF_ACKTIMEOUT)).getContent());
            } catch (NullPointerException e) {
                // this should NOT happen under normal operations
                // this will happen when the object was modified outside
                // of its intended use
                throw new CorruptedConfigurationPropertiesException();
            }
            */
            recreateConfigurationObject(cf, ref);
            setJMSXProperties(cf, ref);
            return cf;
	}
	return null;
    }

    /**
     * Recreates the configuration object (host and subnet) from the Reference.
     *
     */
    private void recreateConfigurationObject(ConnectionFactory cf, Reference ref)
        throws Exception {

        String parm = null;
        String host = null;
        String subnet = null;
        String ackTimeout = null;

        try {
            parm = (String)(((RefAddr)
                ref.get(REF_PARM)).getContent());
            host = (String)(((RefAddr)
                ref.get(REF_HOST)).getContent());
            subnet = (String)(((RefAddr)
                ref.get(REF_SUBNET)).getContent());
            ackTimeout = (String)(((RefAddr)
                ref.get(REF_ACKTIMEOUT)).getContent());

        } catch (NullPointerException e) {
            // this should NOT happen under normal operations
            // this will happen when the object was modified outside
            // of its intended use
            throw new CorruptedConfigurationPropertiesException();
        }

        if (!REF_PARM_CONTENT.equals(parm)) {
            return;
        }

        int configSize = 1;  // one for the parm

        if (!DEFAULT.equals(host)) configSize++;
        if (!DEFAULT.equals(subnet)) configSize++;
        if (!DEFAULT.equals(ackTimeout)) configSize++;

        boolean hostSet = false;
        boolean subnetSet = false;
        boolean ackTimeoutSet = false;

        for (int i = 1; i < configSize; i++) {
            if (!DEFAULT.equals(host) && (hostSet == false)) {
                String hostString = host.substring(host.indexOf("-s", 0)+2, host.length()).trim();
                cf.setProperty(ConnectionConfiguration.imqBrokerHostName, hostString);
                hostSet = true;
            } else if (!DEFAULT.equals(subnet) && (subnetSet == false)) {
                subnetSet = true;
            } else if (!DEFAULT.equals(ackTimeout) && (ackTimeoutSet == false)) {
                String atoString = ackTimeout.substring(
                                       ackTimeout.indexOf("-t", 0)+2, ackTimeout.length()).trim();
                cf.setProperty(ConnectionConfiguration.imqAckTimeout, atoString);
                ackTimeoutSet = true;
            }
        }
    }

    /**
     * Retrieves the value for each JMSX propertry from the Reference.
     * Enables the JMSX property in ConnectionFactory, if it is true.
     * 
     */
    private void setJMSXProperties(ConnectionFactory cf, Reference ref) throws Exception {
        RefAddr addr = null;
        if (((addr = (ref.get(REF_JMSXUSERID))) != null) &&
            ("true".equals((String)addr.getContent())))
            cf.setProperty(ConnectionConfiguration.imqSetJMSXUserID, "true");
        if (((addr = (ref.get(REF_JMSXAPPID))) != null) &&
            ("true".equals((String)addr.getContent())))
            cf.setProperty(ConnectionConfiguration.imqSetJMSXAppID, "true");
        if (((addr = (ref.get(REF_JMSXPRODUCERTXID)))
            != null) && ("true".equals((String)addr.getContent())))
            cf.setProperty(ConnectionConfiguration.imqSetJMSXProducerTXID, "true");
        if (((addr = (ref.get(REF_JMSXCONSUMERTXID)))
            != null) && ("true".equals((String)addr.getContent())))
            cf.setProperty(ConnectionConfiguration.imqSetJMSXConsumerTXID, "true");
        if (((addr = (ref.get(REF_JMSXRCVTIMESTAMP)))
            != null) && ("true".equals((String)addr.getContent())))
            cf.setProperty(ConnectionConfiguration.imqSetJMSXRcvTimestamp, "true");
    }
}

