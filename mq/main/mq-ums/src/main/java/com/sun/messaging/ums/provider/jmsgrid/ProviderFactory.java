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

package com.sun.messaging.ums.provider.jmsgrid;

    //import com.sun.messaging.xml.imq.soap.common.Constants;
//import com.sun.messaging.ums.openmq.*;
import com.sun.messaging.ums.factory.UMSConnectionFactory;
import java.lang.reflect.Constructor;
import java.util.Properties;
import javax.jms.Connection;
//import javax.jms.ConnectionFactory;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

/**
 *
 * @author chiaming
 */
public class ProviderFactory implements UMSConnectionFactory {
    
    private ConnectionFactory factory = null;
    private Properties props = null;
    
    /**
     * Called by UMS immediately after constructed.
     * 
     * @param props properties used by the connection factory.
     * @throws javax.jms.JMSException
     */
    
    public void init(Properties props) throws JMSException {
        this.props = props;

        try {
            String hostname = props.getProperty("grid.host", "localhost");
            String portstr = props.getProperty("grid.port", "50607");

            Properties factProps = new Properties();

            factProps.setProperty("driverName", "SpiritWave");

            String url = "tcp://" + hostname + ":" + portstr;
            factProps.setProperty("messageChannels", url);

            //factory = new com.spirit.wave.jms.WaveConnectionFactory (factProps);
            
            String cname = "com.spirit.wave.jms.WaveConnectionFactory";
            Class cf_class = Class.forName(cname);
            Class[] conargs = {Properties.class};

            Object[] convalues = {factProps};

            Constructor con = cf_class.getConstructor(conargs);
            
            factory = (ConnectionFactory) con.newInstance(convalues);
            
        } catch (Exception e) {
            
            JMSException jmse = new JMSException(e.getMessage());
            jmse.setLinkedException(e);

            throw jmse;
        }

    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection();
     * 
     * @return
     * @throws javax.jms.JMSException
     */
    public Connection createConnection() throws JMSException {
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
