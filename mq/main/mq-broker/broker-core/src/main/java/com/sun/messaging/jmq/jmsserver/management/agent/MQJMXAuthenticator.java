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
 * @(#)MQJMXAuthenticator.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.agent;

import java.net.InetAddress;
import java.rmi.server.RemoteServer;
import javax.security.auth.Subject;
import javax.management.remote.JMXAuthenticator;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.MQAuthenticator;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 * Authenticator for MQ JMX clients
 *
 */
public class MQJMXAuthenticator implements JMXAuthenticator {
    private Logger logger = Globals.getLogger();
    private ConnectorServerInfo csi;
    private BrokerResources rb = Globals.getBrokerResources();

    public MQJMXAuthenticator(ConnectorServerInfo csi) {
	this.csi = csi;
    }

    public Subject authenticate(Object credentials)  {
	if (credentials == null)  {
	    String errStr = rb.getString(rb.W_JMX_CONNECTOR_CREDENTIALS_NEEDED, csi.getName());
            logger.log(Logger.WARNING, errStr);
	    throw new SecurityException(errStr);
	}

	if (!(credentials instanceof String[])) {
	    String errStr = rb.getString(rb.W_JMX_CONNECTOR_CREDENTIALS_WRONG_TYPE, csi.getName());
            logger.log(Logger.WARNING, errStr);
	    throw new SecurityException(errStr);
	}

	String[] up = (String[])credentials;
	String username = up[0], passwd = up[1];
	String clientIP = null;

	MQAuthenticator a = null;
	try {
	    a = new MQAuthenticator("admin", ServiceType.ADMIN);
	} catch(Exception e)  {
	    String errStr = rb.getString(rb.W_JMX_AUTHENTICATOR_INIT_FAILED, e.toString());
	    logger.log(Logger.WARNING, errStr);
	    throw new SecurityException(errStr);
	}

	/*
	 * For RMI based connectors, we can get to the client host IP
	 * This can be used for auth/access control if needed
	 */
	if (csi.getConfiguredJMXServiceURL().getProtocol().equals("rmi"))  {
	    try  {
                clientIP = RemoteServer.getClientHost();

		/*
		 * We need the IP address. The following guarantees that.
		 */
		InetAddress clientHostIA = InetAddress.getByName(clientIP);
		clientIP = clientHostIA.getHostAddress();
	    } catch (Exception e) {
	        String errStr 
		    = rb.getString(rb.W_JMX_FAILED_TO_GET_IP, csi.getName(), e.toString());
                logger.log(Logger.WARNING, errStr);
		/*
		 * XXX: Should a SecurityException be thrown here ?
		 * ie is it necessary for most cases ?
		 */
	        throw new SecurityException(errStr);
	    }

		AccessController ac = a.getAccessController();

		if (ac != null)  {
		    ac.setClientIP(clientIP);
		}
	}

	try {
	    a.authenticate(username, passwd);
	} catch(Exception e)  {
	    String errStr 
	    = rb.getString(rb.W_JMX_CONNECTOR_AUTH_FAILED, csi.getName(), e.toString());
	    logger.log(Logger.WARNING, errStr);
	    throw new SecurityException(errStr);
	}

	return new Subject();
    }
}
