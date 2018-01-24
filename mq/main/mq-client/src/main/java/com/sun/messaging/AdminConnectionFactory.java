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
 * @(#)AdminConnectionFactory.java	1.13 06/28/07
 */ 

package com.sun.messaging;

import java.util.Properties;
import java.util.HashMap;
import java.net.MalformedURLException;
import javax.management.JMException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.management.JMXMQAddress;
import com.sun.messaging.jmq.jmsclient.GenericPortMapperClient;

/**
 * An <code>AdminConnectionFactory</code> is used by management clients
 * to create JMX connections to the  Message Queue broker. After establishing
 * a connection successfully, a handle to a JMX Connector can be obtained
 * which can then be used for management or monitoring operations.
 * <P>
 * The sample code below obtains a JMX Connector that communicates with the
 * default RMI based connector on the broker that is running on the default
 * host and port (localhost and port 7676).
 * The administrator username and password used here is the default 
 * <CODE>admin</CODE> and <CODE>admin</CODE>.
 * <P>
 * <PRE>
 *     import javax.management.*;
 *     import javax.management.remote.*;
 *     import com.sun.messaging.AdminConnectionFactory;
 *     ...
 *     AdminConnectionFactory acf;
 *
 *     acf = new AdminConnectionFactory();
 *     System.out.println("JMXServiceURL used: " + acf.getJMXServiceURL().toString());
 * 
 *     JMXConnector jmxc = acf.createConnection();
 *
 *     // Proceed to manage/monitor the broker using the JMX Connector
 *     // obtained above.
 *     ...
 * </PRE>
 * <P>
 * The sample code below obtains a JMX Connector that communicates with the
 * default RMI connector on the broker that is running on the host 
 * <CODE>myhost</CODE> on port 7979.
 * The administrator username and password used here is <CODE>admin1</CODE> 
 * and <CODE>adminpasswd</CODE>.
 * <P>
 * <PRE>
 *     import javax.management.*;
 *     import javax.management.remote.*;
 *     import com.sun.messaging.AdminConnectionFactory;
 *     import com.sun.messaging.AdminConnectionConfiguration;
 *     ...
 *     AdminConnectionFactory acf;
 *
 *     acf = new AdminConnectionFactory();
 *     acf.setProperty(AdminConnectionConfiguration.imqAddress,
 *			"myhost:7979");
 *     System.out.println("JMXServiceURL used: " + acf.getJMXServiceURL().toString());
 * 
 *     JMXConnector jmxc = acf.createConnection("admin1", "adminpasswd");
 *
 *     // Proceed to manage/monitor the broker using the JMX Connector
 *     // obtained above.
 *     ...
 * </PRE>
 * <P>
 * The sample code below obtains a JMX Connector that communicates with the
 * RMI connector named ssljmxrmi on the broker that is running on the localhost
 * and on port 7676.
 * This is the JMX connector that is configured to use SSL.
 * The administrator username and password used here is the default 
 * <CODE>admin</CODE> and <CODE>admin</CODE>.
 * <P>
 * <PRE>
 *     import javax.management.*;
 *     import javax.management.remote.*;
 *     import com.sun.messaging.AdminConnectionFactory;
 *     import com.sun.messaging.AdminConnectionConfiguration;
 *     ...
 *     AdminConnectionFactory acf;
 *
 *     acf = new AdminConnectionFactory();
 *     acf.setProperty(AdminConnectionConfiguration.imqAddress,
 *			"localhost:7676/ssljmxrmi");
 *     System.out.println("JMXServiceURL used: " + acf.getJMXServiceURL().toString());
 * 
 *     JMXConnector jmxc = acf.createConnection();
 *
 *     // Proceed to manage/monitor the broker using the JMX Connector
 *     // obtained above.
 *     ...
 * </PRE>
 *
 * @see         com.sun.messaging.AdminConnectionConfiguration com.sun.messaging.AdminConnectionConfiguration
 */
public class AdminConnectionFactory extends com.sun.messaging.AdministeredObject {

    /** The default basename for AdministeredObject initialization */
    private static final String defaultsBase = "AdminConnectionFactory";

    /** The default Username and Password for Sun MQ client authentication */
    private static final String DEFAULT_IMQ_ADMIN_USERNAME_PASSWORD = "admin";

    /** The default Username Label */
    private static final String DEFAULT_IMQ_ADMIN_USERNAME_LABEL 
				= "Default Administrator Username";

    /** The default Password Label */
    private static final String DEFAULT_IMQ_ADMIN_PASSWORD_LABEL
				= "Default Administrator Password";

    /**
     * Constructs a AdminConnectionFactory with the default configuration.
     * 
     */
    public AdminConnectionFactory() {
        super(defaultsBase);
    }
 
    /**
     * Constructs a AdminConnectionFactory with the specified configuration.
     * 
     */
    protected AdminConnectionFactory(String defaultsBase) {
        super(defaultsBase);
    }
 
    /**
     * Creates a Connection with the default user identity. The default user identity
     * is defined by the <code>AdminConnectionFactory</code> properties
     * <code><b>imqDefaultAdminUsername</b></code> and <code><b>imqDefaultAdminPassword</b></code>
     * 
     * @return a newly created Connection.
     * 
     * @exception JMException if a JMS error occurs.
     * @see AdminConnectionConfiguration#imqDefaultAdminUsername
     * @see AdminConnectionConfiguration#imqDefaultAdminPassword
     */  
    public JMXConnector createConnection() throws JMException {
	String u = null, p = null;
	try  {
            u = getCurrentConfiguration().getProperty(
			AdminConnectionConfiguration.imqDefaultAdminUsername);
            p = getCurrentConfiguration().getProperty(
			AdminConnectionConfiguration.imqDefaultAdminPassword);
	} catch (Exception e)  {
	}

        return createConnection(u, p);
    }

    /**
     * Creates a Connection with a specified user identity.
     * 
     * @param username the caller's user name
     * @param password the caller's password
     * 
     * @return a newly created connection.
     * 
     * @exception JMException if a JMX error occurs.
     */  
    public JMXConnector createConnection(String username, String password) 
		throws JMException {
	JMXConnector jmxc = null;
	JMXServiceURL url = null;

	url = getJMXServiceURL();
	/*
	System.err.println("url: " + url);
	*/

	try  {
	    HashMap env = new HashMap();
	    String[] credentials = new String[] { username, password };
	    env.put(JMXConnector.CREDENTIALS, credentials);

	    jmxc = JMXConnectorFactory.connect(url, env);
	} catch (Exception e)  {
	    JMException jme 
		= new JMException("Caught exception when creating JMXConnector");
	    jme.initCause(e);

	    throw (jme);
	}

        return (jmxc);
    }

    /**
     * Returns a pretty printed version of the provider specific
     * information for this ConnectionFactory object.
     *
     * @return the pretty printed string.
     */
    public String toString() {
        return ("Oracle GlassFish(tm) Server MQ AdminConnectionFactory" + super.toString());
    }

    /**
     * Returns the relevant JMXServiceURL that is advertised by the 
     * portmapper. This url will be used in connection attempts.
     *
     * @return The relevant JMXServiceURL that is advertised by the 
     * portmapper.
     */
    public JMXServiceURL getJMXServiceURL() throws JMException {
	GenericPortMapperClient pmc;
	JMXMQAddress mqAddr;
	JMXServiceURL url;
	String addr = null, urlString, host, connectorName;
	int port;

	try  {
	    addr = getCurrentConfiguration().getProperty(
			AdminConnectionConfiguration.imqAddress);

	    mqAddr = JMXMQAddress.createAddress(addr);
	    host = mqAddr.getHostName();
	    port = mqAddr.getPort();
	    connectorName = mqAddr.getServiceName();

	    /*
	    System.out.println("ACF: address used: " + addr);
	    System.out.println("\thost: " + host);
	    System.out.println("\tport: " + port);
	    System.out.println("\tconnector: " + connectorName);
	    */

	} catch (Exception e)  {
	    JMException jme 
		= new JMException("Caught exception when parsing address: "
					+ addr);
	    jme.initCause(e);

	    throw (jme);
	}

	/*
	System.out.println("host: " + host);
	System.out.println("port: " + port);
	System.out.println("connectorName: " + connectorName);
	*/

	try  {
	    pmc = new GenericPortMapperClient(host, port);

	    /*
	     * Should add code to check/compare version of client runtime and
	     * broker here.
	     */

	} catch (Exception e)  {
	    JMException jme 
		= new JMException("Caught exception when contacing portmapper.");
	    jme.initCause(e);

	    throw (jme);
	}

	urlString = pmc.getProperty("url", null, "JMX", connectorName);

	if (urlString == null)  {
	    JMException jme = new JMException("No JMXServiceURL was found for connector "
			+ connectorName + ".\n"
			+ "Address used: "
			+ addr);

	    throw (jme);
	}

	try  {
	    url = new JMXServiceURL(urlString);
	} catch (MalformedURLException mfe)  {
	    JMException jme 
		= new JMException("Caught exception when creating JMXServiceURL.");
	    jme.initCause(mfe);

	    throw (jme);
	}
	
	return (url);
    }

    /**
     * Sets the minimum <code>AdminConnectionFactory</code> configuration defaults
     * required to connect to the MQ Administration Service.
     */  
    public void setDefaultConfiguration() {
        configuration = new Properties();
        configurationTypes = new Properties();
        configurationLabels = new Properties();

        configuration.put(AdminConnectionConfiguration.imqDefaultAdminUsername,
                                DEFAULT_IMQ_ADMIN_USERNAME_PASSWORD);
        configurationTypes.put(AdminConnectionConfiguration.imqDefaultAdminUsername,
                                AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(AdminConnectionConfiguration.imqDefaultAdminUsername,
                                DEFAULT_IMQ_ADMIN_USERNAME_LABEL);
 
        configuration.put(AdminConnectionConfiguration.imqDefaultAdminPassword,
                                DEFAULT_IMQ_ADMIN_USERNAME_PASSWORD);
        configurationTypes.put(AdminConnectionConfiguration.imqDefaultAdminPassword,
                                AO_PROPERTY_TYPE_STRING);
        configurationLabels.put(AdminConnectionConfiguration.imqDefaultAdminPassword,
                                DEFAULT_IMQ_ADMIN_PASSWORD_LABEL);
    }

}
