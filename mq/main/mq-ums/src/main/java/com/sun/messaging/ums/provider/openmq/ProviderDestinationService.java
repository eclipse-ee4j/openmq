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

import com.sun.messaging.ums.common.Constants;
import java.util.Properties;
import javax.jms.JMSException;

import javax.management.*;
import javax.management.remote.*;
import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.ums.resources.UMSResources;
import com.sun.messaging.ums.service.SecuredSid;
import com.sun.messaging.ums.service.UMSServiceException;
import com.sun.messaging.ums.service.UMSServiceImpl;
import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Logger;


/**
 *
 * @author chiaming
 */
public class ProviderDestinationService {
    
    //private com.sun.messaging.ConnectionFactory factory = null;
    
    private Logger logger = UMSServiceImpl.logger;
    
    private String brokerAddress = null;
    
    //private String user = null;
    
    //private String password = null;
    
    private AdminConnectionFactory acf;
    
    private boolean shouldAuthenticate = true;
    
    private boolean base64encoding = false;
    
    /**
     * Called by UMS immediately after constructed.
     * 
     * @param props properties used by the connection factory.
     * @throws javax.jms.JMSException
     */
    
    public void init (Properties props) throws JMSException {
        
        // get connection factory
        acf = new AdminConnectionFactory();

        brokerAddress = props.getProperty(Constants.IMQ_BROKER_ADDRESS);

        if (brokerAddress != null) {
            acf.setProperty(AdminConnectionConfiguration.imqAddress, brokerAddress);
        }
            
        String tmp = props.getProperty(Constants.JMS_AUTHENTICATE, Constants.JMS_AUTHENTICATE_DEFAULT_VALUE);
        
        this.shouldAuthenticate = Boolean.parseBoolean(tmp);
        
        tmp = props.getProperty(Constants.BASIC_AUTH_TYPE, Constants.BASIC_AUTH_TYPE_DEFAULT_VALUE);
        
        this.base64encoding = Boolean.parseBoolean(tmp);
        
        String msg = UMSResources.getResources().getKString(UMSResources.UMS_DEST_SERVICE_INIT, brokerAddress, String.valueOf(shouldAuthenticate));
       
        logger.info(msg);
       
        msg = UMSResources.getResources().getKString(UMSResources.UMS_AUTH_BASE64_ENCODE, base64encoding);
        logger.info(msg);
        
        //logger.info ("broker addr=" + brokerAddress + ", shouldAuth=" + this.shouldAuthenticate + ", base64encode=" + this.base64encoding);
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection();
     * 
     * @return
     * @throws javax.jms.JMSException
     */
    private JMXConnector createConnection() throws JMException {
        return acf.createConnection();
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection(String user, String password);
     * 
     * @param user
     * @param password
     * @return
     * @throws javax.jms.JMSException
     */
    private JMXConnector createConnection(String user, String password) throws JMException, JMSException {
        
        JMXConnector jmxc = null;
        
        if (this.shouldAuthenticate == false) {
            jmxc = acf.createConnection();
        } else {
            
            if (this.base64encoding) {
                
                if (password == null) {
                    throw new UMSServiceException ("Password is required for user=" + user);
                }
                
                password = SecuredSid.decode(password);
            }
            
            jmxc =acf.createConnection(user, password);
        }
        
        return jmxc;
    }
    
    public String listDestinations(String user, String password) {
        
        String destlist = null;
        JMXConnector jmxc = null;
        
        try  {
            
            StringWriter sw = new StringWriter();
            
            if (user == null) {
                jmxc = createConnection();
            } else {
                jmxc = createConnection(user, password);
            }
            
	    /*
	     * Get MBeanServer interface.
	     */
	    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

	    /*
	     * Create object name of destination monitor mgr MBean.
	     */
	    ObjectName objName
		= new ObjectName(MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME);

	    ObjectName destinationObjNames[] = 
                (ObjectName[])mbsc.invoke(objName, DestinationOperations.GET_DESTINATIONS, null, null);

            //System.out.println("Listing destinations:" );
	    
            for (int i = 0; i < destinationObjNames.length; ++i)  {
		
                ObjectName oneDestObjName = destinationObjNames[i];
		
                //System.out.println("\tName: " + 
		//    mbsc.getAttribute(oneDestObjName, DestinationAttributes.NAME));
                
                String destName = (String) mbsc.getAttribute(oneDestObjName, DestinationAttributes.NAME);
                
                sw.write("destination="+ destName +"\n");
                
		//System.out.println("\tType: " + 
		//    mbsc.getAttribute(oneDestObjName, DestinationAttributes.TYPE));
                
                String domain = (String) mbsc.getAttribute(oneDestObjName, DestinationAttributes.TYPE);
                
                if ("q".equals(domain)) {
                    domain = "queue";
                } else if ("t".equals(domain)) {
                    domain = "topic";
                }
                
                sw.write("domain=" + domain + "\n");
                
		//System.out.println("\tState: " + 
		//    mbsc.getAttribute(oneDestObjName, DestinationAttributes.STATE_LABEL));
		
                String state = (String) mbsc.getAttribute(oneDestObjName, DestinationAttributes.STATE_LABEL);
                sw.write("state=" + state + "\n");
                
                //System.out.println("\tNumber of Msgs: " + 
		//    mbsc.getAttribute(oneDestObjName, DestinationAttributes.NUM_MSGS));

                Object numOfMsgs = mbsc.getAttribute(oneDestObjName, DestinationAttributes.NUM_MSGS);
                sw.write("numOfMsgs=" + numOfMsgs + "\n");
                sw.write("\n");
    
		//System.out.println(sw.toString());
	    }
            
            //System.out.println(sw.toString());

            destlist = sw.toString();
            
            //logger.info("*** destination list:" + destlist);
            
	} catch (Exception e)  {
	    throw new UMSServiceException (e);
	} finally {
            
            try {
                
                if (jmxc != null) {
                    jmxc.close();
                }
            
            } catch (Exception e) {
                ;
            }
        }
        
        return destlist;
    }
    
    
    public String queryDestination(String destName, String domain, String user, String password) {
        
        String destinfo = null;
        
        JMXConnector jmxc = null;
        
        try  {
            
            StringWriter sw = new StringWriter();
            
            if (user == null) {
                jmxc = createConnection();
            } else {
                jmxc = createConnection(user, password);
            }
            
	    /*
	     * Get MBeanServer interface.
	     */
	    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

	    /*
	     * Create object name of destination monitor MBean. This MBean can be 
	     * used to access the destination's runtime info.
	     *
	     * For accessing config state of the destination eg MaxNumProducers,
	     * the destination config MBean should be used.
	     */
            String destType = "q";
            if (Constants.TOPIC_DOMAIN.equals(domain)) {
                destType = "t";
            }
            
	    /*
	     * Create object name of destination monitor MBean. This MBean can be 
	     * used to access the destination's runtime info.
	     *
	     * For accessing config state of the destination eg MaxNumProducers,
	     * the destination config MBean should be used.
	     */
	    ObjectName objName
		= MQObjectName.createDestinationMonitor(destType, destName);
            
            sw.write("destination="+ destName +"\n");
            sw.write("domain=" + domain + "\n");
            
            Object numOfMsgs = mbsc.getAttribute(objName, DestinationAttributes.NUM_MSGS);
            sw.write("numOfMsgs=" + numOfMsgs + "\n");
            sw.write("\n");

            destinfo = sw.toString();
            
        } catch (Exception e)  {
	    throw new UMSServiceException (e);
	} finally {
            
            try {
                
                if (jmxc != null) {
                    jmxc.close();
                }
            
            } catch (Exception e) {
                ;
            }
        }
        
        return destinfo;
    }
    
    /**
     * XXX: review
     * @param user
     * @param pass
     * @throws javax.jms.JMSException
     * @throws javax.management.JMException
     * @throws java.io.IOException
     */
    public void authenticate (String user, String pass) throws IOException {
        
        JMXConnector jmxc = null;
        
        try {
        
        if (user == null) {
            this.createConnection();
        } else {
            this.createConnection(user, pass);
        } 
        
        } catch (Exception e) {
            
            throw new RuntimeException (e.getMessage());
        
        } finally {
            
            if (jmxc != null) {
                jmxc.close();
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        
        props.setProperty(Constants.IMQ_BROKER_ADDRESS, "niagra2:7676");
        
        ProviderDestinationService ds = new ProviderDestinationService();
        ds.init(props);
        
        //ds.listDestinations(null, null);
        
       ds.queryDestination("simpleQ", "queue", null, null);
    }
    
    
}
