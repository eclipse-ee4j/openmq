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
 * @(#)ConnectorServerInfo.java	1.10 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.agent;

import java.util.List;
import java.util.Set;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Collection;
import java.util.Vector;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.*;
import javax.management.remote.*;
import javax.management.remote.rmi.RMIConnectorServer;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.io.MQAddress;

import com.sun.messaging.jmq.management.MQRMIClientSocketFactory;

/**
 * Class that represents one JMX connector server.
 *
 */
public class ConnectorServerInfo  {
    private static String	CONNECTOR_PROPBASE
				    		= Globals.IMQ + ".jmx.connector.";
    private static String	PROTOCOL_SUFFIX	= ".protocol";
    private static String	PORT_SUFFIX	= ".port";
    private static String	URLPATH_SUFFIX	= ".urlpath";
    private static String	URL_SUFFIX	= ".url";
    private static String	SSL_SUFFIX	= ".useSSL";
    private static String	BROKER_HOST_TRUSTED_SUFFIX 
						= ".brokerHostTrusted";
    private static String	BACKLOG_SUFFIX	= ".backlog";

    Agent		agent;
    JMXConnectorServer	connectorServer = null;
    JMXServiceURL	configuredURL = null;
    String		name = null;
    boolean		configuredActive = false,
			stopped = false;
    NotificationListener	listener;
    private BrokerResources			rb = Globals.getBrokerResources();

    public ConnectorServerInfo(Agent agent, String name, boolean configuredActive, 
				NotificationListener listener) 
					throws BrokerException  {
	this.name = name;
	this.configuredActive = configuredActive;
	this.listener = listener;
	this.agent = agent;

        initURL();
    }

    /*
    public ConnectorServerInfo(String name, 
			JMXConnectorServer connectorServer, 
				JMXServiceURL configuredURL)  {
	this.name = name;
	this.connectorServer = connectorServer;
	this.configuredURL = configuredURL;
    }
    */

    public JMXServiceURL getConfiguredJMXServiceURL()  {
        return (configuredURL);
    }

    public JMXServiceURL getJMXServiceURL()  {
	if (isActive())  {
	    return (connectorServer.getAddress());
	} else  {
	    return (configuredURL);
	}
    }

    public String getName()  {
	return (name);
    }

    public boolean isActive()  {
	if (!configuredActive)  {
	    return (false);
	}

	if (connectorServer == null)  {
	    return (false);
	}

	return (connectorServer.isActive());
    }

    public void start() throws IOException, BrokerException {
	if (!configuredActive)  {
	    throw new BrokerException(rb.getString(rb.W_JMX_CANNOT_START_INACTIVE_CONNECTOR, 
					name));
	}

	initConnectorServer();

        connectorServer.start();

	/*
	System.err.println(">>>>Starting connector at URL: " + getJMXServiceURL());
	System.err.println(">>>>\tURL path: " + getJMXServiceURL().getURLPath());
	System.err.println(">>>>Connector Server: " + connectorServer);
	*/
    }

    public void stop() throws IOException {
        if (!isActive())  {
	    return;
	}

        connectorServer.stop();
	stopped = true;

	/*
	 * Javadoc for JMXConnectorServer says that "A connector server 
	 * once stopped cannot be restarted."
	 */
        connectorServer = null;
	configuredURL = null;
    }

    public boolean isStopped()  {
	return (stopped);
    }

    public int getPort()  {
	return (getJMXServiceURL().getPort());
    }

    public String getProtocol()  {
	return (getJMXServiceURL().getProtocol());
    }

    public JMXConnectorServer getConnectorServer()  {
	return (connectorServer);
    }

    /*
     * Initialize the JMXServiceURL associated with this connector.
     * This class was initialized with just the connector 'name'
     * which has nothing to do with JMX, but merely a reference
     * into the broker configuration properties.
     *
     * There are various properties that can be used to configure
     * or construct the URL - the properties map more or less
     * to the constructor of the JMXServiceURL class:
     *
     *  public JMXServiceURL(String protocol, String host, int port, String urlPath)
     *
     * So, for example, the default JMX connector is named "jmxrmi"
     * and the properties that can be used to configure it's URL are:
     *
     *	imq.jmx.connector.<connector server name>.protocol
     *	imq.jmx.connector.<connector server name>.port
     *	imq.jmx.connector.<connector server name>.urlpath
     *	imq.jmx.connector.<connector server name>.url
     *
     * Out of the above, only the following is made public:
     *
     *	imq.jmx.connector.<connector server name>.urlpath
     *
     * because the only protocol supported in MQ 4.0 is rmi, and the port
     * is pretty much ignored for RMI connectors. No real reason url is 
     * not made public other than the fact that we want to simplify things
     * and it is not really needed.
     *
     * If urlpath is not specified via the above property, a default exists
     * and can be constructed with Agent.getDefaultJMXUrlPathBase().
     *
     * One point worth noting is that this fixed or JNDI form of the url
     * is only used when a RMI registry (or some form of JNDI repository)
     * is used to store the RMI stub for the connector.
     *
     * Example entry in configuration properties:
     *
     *	imq.jmx.connector.jmxrmi.urlpath=/foo/bar/connector1
     */
    private void initURL() throws BrokerException  {
	String		protocol = null,
			urlpath = null,
			jmxurl = null;
	int		port = 0;
	JMXServiceURL	url;
        BrokerConfig	config = Globals.getConfig();
        String          jmxHostname = Globals.getJMXHostname();

	if (configuredURL != null)  {
	    return;
	}

	jmxurl = config.getProperty(CONNECTOR_PROPBASE
			+ name + URL_SUFFIX);

	if (jmxurl == null)  {
	    protocol = config.getProperty(CONNECTOR_PROPBASE 
					+  name + PROTOCOL_SUFFIX, "rmi");
	    /*
	     * Get port value, defaulting to 0 if none was specified.
	     *
	     * The javadoc for JMXServiceURL says that getPort() will return
	     * 0 if none was specified.
	     */
	    port = config.getIntProperty(CONNECTOR_PROPBASE 
					+  name + PORT_SUFFIX, 0);
	    urlpath = config.getProperty(CONNECTOR_PROPBASE 
					+  name + URLPATH_SUFFIX);

	    if (urlpath == null)  {
		/*
		 * If imq.jmx.rmiregistry.start or imq.jmx.rmiregistry.use is set 
		 * to true, the default urlpath is:
	         *  /jndi/rmi://<brokerhost>:<rmiport>/<brokerhost>/<brokerport>/<connector name>
		 */
		if (agent.startRmiRegistry() || agent.useRmiRegistry())  {
		    urlpath = agent.getDefaultJMXUrlPathBase() + name;
		}
	    }
	}

	try  {
	    if (jmxurl != null)  {
	        url = new JMXServiceURL(jmxurl);
	    } else  {
                if (jmxHostname != null && 
                    !jmxHostname.equals(Globals.HOSTNAME_ALL)) {
	           url = new JMXServiceURL(protocol, jmxHostname, 
                                           port, urlpath);
                } else {
	           url = new JMXServiceURL(protocol, null, port, urlpath);
                }
	    }
	} catch (MalformedURLException mfe)  {
	    throw new BrokerException(rb.getString(rb.W_JMX_CREATE_URL_FOR_CONNECTOR_FAILED, 
					name), mfe);
	}

	configuredURL = url;
    }

    /*
     * Initialize the connector server. This method assumes the URL has been
     * initialized.
     *
     * The following properties can be used to further configure the
     * connector server:
     *	imq.jmx.connector.<connector server name>.useSSL
     *	imq.jmx.connector.<connector server name>.brokerHostTrusted
     *	imq.jmx.connector.<connector server name>.backlog
     * 
     */
    private void initConnectorServer() throws BrokerException  {
	JMXConnectorServer	cs;
        BrokerConfig		config = Globals.getConfig();
	MBeanServer		mbs = agent.getMBeanServer();
	HashMap			env = new HashMap();
	boolean			useSSL, brokerHostTrusted;
	String			jmxHostname = Globals.getJMXHostname();
	int			backlog;

	if (connectorServer != null)  {
	    return;
	}

	if (mbs == null)  {
	    throw new BrokerException(rb.getString(rb.X_JMX_CANT_CREATE_CONNECTOR_SVR, name));
	}

        useSSL = config.getBooleanProperty(CONNECTOR_PROPBASE
					+  name 
					+ SSL_SUFFIX, false);

	/*
	 * Query property that specifies if the broker is to be trusted.
	 * If this property is not set, by default the broker is *not*
	 * trusted.
	 */
        brokerHostTrusted = config.getBooleanProperty(CONNECTOR_PROPBASE
					+  name 
					+ BROKER_HOST_TRUSTED_SUFFIX, false);

	backlog = config.getIntProperty(CONNECTOR_PROPBASE 
					+  name 
					+ BACKLOG_SUFFIX, 0);

	try  {
	    /*
	     * We only support the RMI protocol in MQ 4.0
	     */
	    if (configuredURL.getProtocol().equals("rmi"))  {
		boolean useAuth = true;
		if (useAuth)  {
		    env.put(JMXConnectorServer.AUTHENTICATOR, new MQJMXAuthenticator(this));
		}

		/*
		 * Use MQ specific server socket factory only if:
		 *  - SSL is enabled
		 *  - need to specify a specific host/IP on a multihome system
		 */
	        if ((useSSL) || 
		     (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL))) {
                    MQRMIServerSocketFactory ssf
				= new MQRMIServerSocketFactory(jmxHostname, backlog, useSSL);
                    env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE,
					ssf);
		}

		/*
		 * Use MQ specific client socket factory only if:
		 *  - need to specify a specific host/IP on a multihome system
		 */
	        if (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL)) {
                    MQRMIClientSocketFactory csf
				= new MQRMIClientSocketFactory(jmxHostname,
						brokerHostTrusted, useSSL);
                    env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,
					csf);
                    if ((Globals.isJMSRAManagedBroker() || 
                         Globals.isNucleusManagedBroker()) &&
                        agent.useRmiRegistry()) {
                        env.put("com.sun.jndi.rmi.factory.socket", csf);
                    }
		} else  {
		    /*
		     * Use MQ specific client socket factory only if:
		     * - SSL is enabled and brokerHostTrusted is true
		     * If brokerHostTrusted is false, use the JDK provided
		     * javax.rmi.ssl.SslRMIClientSocketFactory
		     */
		    if (useSSL)  {
			if (brokerHostTrusted)  {
                            MQRMIClientSocketFactory csf
				= new MQRMIClientSocketFactory(jmxHostname,
						brokerHostTrusted, useSSL);
                            env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,
					csf);
			} else  {
                            javax.rmi.ssl.SslRMIClientSocketFactory sslcf = 
                                new javax.rmi.ssl.SslRMIClientSocketFactory();
                            env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, sslcf);
                            if ((Globals.isJMSRAManagedBroker() || 
                                 Globals.isNucleusManagedBroker()) &&
                                agent.useRmiRegistry()) {
                                env.put("com.sun.jndi.rmi.factory.socket", sslcf);
                            }
			}
		    }
		}
	    }

            cs = JMXConnectorServerFactory.newJMXConnectorServer(configuredURL, env, mbs);
	} catch (Exception e)  {
	    throw new BrokerException(rb.getString(rb.W_JMX_ERROR_CREATING_CONNECTOR, 
					name), e);
	}

	cs.addNotificationListener(listener, null, this);

	connectorServer = cs;
    }
}

