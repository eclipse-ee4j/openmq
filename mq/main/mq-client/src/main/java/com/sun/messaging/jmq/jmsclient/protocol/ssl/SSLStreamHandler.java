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
 * @(#)SSLStreamHandler.java	1.16 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient.protocol.ssl;

import java.io.*;
import javax.jms.*;

import com.sun.messaging.PropertyOwner;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.*;

 /**
  * This class is the SSL protocol handler for the iMQ JMS client
  * implementation.  It uses SSL protocol to communicate with the Broker.
  */
public class SSLStreamHandler implements StreamHandler, PropertyOwner {

    /**
     * Null constructor for use by AdministeredObject when used as a PropertyOwner
     */  
    public SSLStreamHandler() {}

    public String[] getPropertyNames() {
        String [] propnames = new String [6];
        propnames[0] = ConnectionConfiguration.imqBrokerHostName;
        propnames[1] = ConnectionConfiguration.imqBrokerHostPort;
        propnames[2] = ConnectionConfiguration.imqBrokerServicePort;
        propnames[3] = ConnectionConfiguration.imqBrokerServiceName;
        propnames[4] = ConnectionConfiguration.imqSSLProviderClassname;
        propnames[5] = ConnectionConfiguration.imqSSLIsHostTrusted;
        return propnames;
    }

    public String getPropertyType(String propname) {
        if (ConnectionConfiguration.imqBrokerHostName.equals(propname) ||
            ConnectionConfiguration.imqBrokerServiceName.equals(propname) || 
            ConnectionConfiguration.imqSSLProviderClassname.equals(propname)) {
            return AdministeredObject.AO_PROPERTY_TYPE_STRING;
        } else {
            if (ConnectionConfiguration.imqBrokerHostPort.equals(propname)) {
                return AdministeredObject.AO_PROPERTY_TYPE_INTEGER;
            } else {
                if (ConnectionConfiguration.imqSSLIsHostTrusted.equals(propname)) {
                    return AdministeredObject.AO_PROPERTY_TYPE_BOOLEAN;
                }
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
                if (ConnectionConfiguration.imqSSLProviderClassname.equals(propname)) {
                    return (AdministeredObject.cr.L_JMQSSL_PROVIDER_CLASSNAME);
                } else {
                    if (ConnectionConfiguration.imqSSLIsHostTrusted.equals(propname)) {
                        return (AdministeredObject.cr.L_JMQSSL_IS_HOST_TRUSTED);
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
                if (ConnectionConfiguration.imqSSLProviderClassname.equals(propname)) {
                    return "com.sun.net.ssl.internal.ssl.Provider";
                } else {
                    if (ConnectionConfiguration.imqSSLIsHostTrusted.equals(propname)) {
                        return "false"; //TCR#3 default to false
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
            }
        }
        return null;
    }    

    /**
     * Open socket a new connection.
     *
     * @param connection is the ConnectionImpl object.
     * @return a new instance of SSLConnectionHandler.
     * @exception throws IOException if socket creation failed.
     */
    public ConnectionHandler
    openConnection(Object connection) throws JMSException {
        return new SSLConnectionHandler(connection);
    }

    public ConnectionHandler openConnection(
        MQAddress addr, ConnectionImpl connection) throws JMSException {
        return new SSLConnectionHandler(addr, connection);
    }
}
