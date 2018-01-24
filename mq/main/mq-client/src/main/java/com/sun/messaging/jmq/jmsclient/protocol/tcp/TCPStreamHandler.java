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
 * @(#)TCPStreamHandler.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.tcp;

import java.io.*;
import javax.jms.JMSException;

import com.sun.messaging.PropertyOwner;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;


 /**
  * This class is the default protocol handler for the iMQ JMS client
  * implementation.  It uses TCP protocol to communicate with the Broker.
  */
public class TCPStreamHandler implements StreamHandler, PropertyOwner {

    /**
     * Null constructor for use by AdministeredObject when used as a PropertyOwner
     */  
    public TCPStreamHandler() {}

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
 
    /**
     * Open socket a new connection.
     *
     * @param connection is the ConnectionImpl object.
     * @return a new instance of TCPConnectionHandler.
     * @exception throws IOException if socket creation failed.
     */
    public ConnectionHandler
    openConnection(Object connection) throws JMSException {
        return new TCPConnectionHandler(connection);
    }

    public ConnectionHandler openConnection(
        MQAddress addr, ConnectionImpl connection) throws JMSException {
        return new TCPConnectionHandler(addr, connection);
    }

}
