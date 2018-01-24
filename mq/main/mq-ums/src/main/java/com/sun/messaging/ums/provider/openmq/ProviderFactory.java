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

package com.sun.messaging.ums.provider.openmq;

    //import com.sun.messaging.xml.imq.soap.common.Constants;
import com.sun.messaging.ums.factory.UMSConnectionFactory;
import com.sun.messaging.ums.common.Constants;
import java.util.Properties;
import javax.jms.Connection;
//import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

/**
 *
 * @author chiaming
 */
public class ProviderFactory implements UMSConnectionFactory {
    
    private com.sun.messaging.ConnectionFactory factory = null;
    
    private String brokerAddress = null;
    
    //private String user = null;
    
    //private String password = null;
    
    /**
     * Called by UMS immediately after constructed.
     * 
     * @param props properties used by the connection factory.
     * @throws javax.jms.JMSException
     */
    
    public void init (Properties props) throws JMSException {
        // get connection factory
        factory = new com.sun.messaging.ConnectionFactory();

        brokerAddress = props.getProperty(Constants.IMQ_BROKER_ADDRESS);

        if (brokerAddress != null) {
            factory.setProperty(Constants.IMQ_BROKER_ADDRESS, brokerAddress);
        }
        
        factory.setProperty(com.sun.messaging.ConnectionConfiguration.imqReconnectEnabled, "true");
        factory.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressListIterations, "-1");
        
        String connectionType = props.getProperty(com.sun.messaging.ConnectionConfiguration.imqConnectionType);
        if ("TLS".equals(connectionType)) {
            factory.setProperty(com.sun.messaging.ConnectionConfiguration.imqConnectionType, "TLS");
            factory.setProperty(com.sun.messaging.ConnectionConfiguration.imqSSLIsHostTrusted, "true");
        }
        
        //user name to authenticate
        //this.user = props.getProperty(Constants.IMQ_USER_NAME, "guest");
        
        //this.password = props.getProperty(Constants.IMQ_USER_PASSWORD, "guest");
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection();
     * 
     * @return
     * @throws javax.jms.JMSException
     */
    public Connection createConnection() throws JMSException {
        //return factory.createConnection(user, password);
        return factory.createConnection();
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection(String user, String password);
     * 
     * @param user
     * @param password
     * @return
     * @throws javax.jms.JMSException
     */
    public Connection createConnection(String user, String password) throws JMSException {
        return factory.createConnection(user, password);
    }

}
