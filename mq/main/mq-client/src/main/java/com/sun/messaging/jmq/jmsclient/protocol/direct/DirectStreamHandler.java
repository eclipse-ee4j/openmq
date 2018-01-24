/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsclient.protocol.direct;

import javax.jms.JMSException;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.PropertyOwner;
import com.sun.messaging.jmq.jmsclient.ConnectionHandler;
import com.sun.messaging.jmq.jmsclient.ConnectionImpl;
import com.sun.messaging.jmq.jmsclient.MQAddress;
import com.sun.messaging.jmq.jmsclient.StreamHandler;

public class DirectStreamHandler implements StreamHandler, PropertyOwner {
	
	/**
     * Null constructor for use by AdministeredObject when used as a PropertyOwner
     */  
    public DirectStreamHandler() {}

    /**
     * XXX chiaming 10/22/2008: update with direct mode values
     */
    public String[] getPropertyNames() {
        String [] propnames = new String [4];
        propnames[0] = ConnectionConfiguration.imqBrokerHostName;
        propnames[1] = ConnectionConfiguration.imqBrokerHostPort;
        propnames[2] = ConnectionConfiguration.imqBrokerServicePort;
        propnames[3] = ConnectionConfiguration.imqBrokerServiceName;
        return propnames;
    }

    public String getPropertyType(String propname) {
        if (ConnectionConfiguration.imqBrokerHostName.equals(propname) || 
                ConnectionConfiguration.imqBrokerServiceName.equals(propname)) { 
            return AdministeredObject.AO_PROPERTY_TYPE_STRING;
        } else {
            if (ConnectionConfiguration.imqBrokerHostPort.equals(propname) ||
                   ConnectionConfiguration.imqBrokerServicePort.equals(propname)) {
                return AdministeredObject.AO_PROPERTY_TYPE_INTEGER;
            }
        }
        return null;
    }

    public String getPropertyLabel(String propname) {
        if (ConnectionConfiguration.imqBrokerHostName.equals(propname)) {
            return (AdministeredObject.cr.L_JMQBROKER_HOST_NAME);
        } else {
            if (ConnectionConfiguration.imqBrokerHostPort.equals(propname)) {
                return (AdministeredObject.cr.L_JMQBROKER_HOST_PORT);
            } else {
                if (ConnectionConfiguration.imqBrokerServicePort.equals(propname)) {
                    return (AdministeredObject.cr.L_JMQBROKER_SERVICE_PORT);
                } else {
                    if (ConnectionConfiguration.imqBrokerServiceName.equals(propname)) {
                        return (AdministeredObject.cr.L_JMQBROKER_SERVICE_NAME);
                    }
                }
            }
        }
        return null;
    }
 
    public String getPropertyDefault(String propname) {
        if (ConnectionConfiguration.imqBrokerHostName.equals(propname)) {
            return "localhost";
        } else {
            if (ConnectionConfiguration.imqBrokerHostPort.equals(propname)) {
                return ("7676");
            } else {
                if (ConnectionConfiguration.imqBrokerServicePort.equals(propname)) {
                    return ("0");
                } else {
                    if (ConnectionConfiguration.imqBrokerServiceName.equals(propname)) {
                        return ("");
                    }
                }
            }
        }
        return null;
    }
    
	public ConnectionHandler openConnection(Object connection)
			throws JMSException {
		// TODO Auto-generated method stub
		DirectConnectionHandler dsh = new DirectConnectionHandler(connection);
		
		return dsh;
	}

	public ConnectionHandler openConnection(MQAddress addr, ConnectionImpl conn)
			throws JMSException {
		// TODO Auto-generated method stub
		DirectConnectionHandler dsh = new DirectConnectionHandler(conn);
		
		return dsh;
	}

}
