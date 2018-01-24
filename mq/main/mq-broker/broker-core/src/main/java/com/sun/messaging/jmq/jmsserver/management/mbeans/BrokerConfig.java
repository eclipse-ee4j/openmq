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
 * @(#)BrokerConfig.java	1.25 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Date;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;
import javax.management.ObjectName;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.ResetMetricsHandler;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;

import com.sun.messaging.jms.management.server.*;

public class BrokerConfig extends MQMBeanReadWrite implements ConfigListener {
    private Properties brokerProps = null;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(BrokerAttributes.BROKER_ID,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_BKR_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.INSTANCE_NAME,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_INSTANCE_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.HOST,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_HOST),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.PORT,
					Integer.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_PORT),
					true,
					true,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.VERSION,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_VERSION),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] shutdownSignature = {
	    new MBeanParameterInfo("noFailover", Boolean.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_SHUTDOWN_PARAM_NO_FAILOVER_DESC)),
	    new MBeanParameterInfo("time", Long.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_SHUTDOWN_PARAM_TIME_DESC))
    		};

    private static MBeanParameterInfo[] takeoverSignature = {
	    new MBeanParameterInfo("brokerID", String.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_TAKEOVER_PARAM_BROKER_ID_DESC))
    		};

    private static MBeanParameterInfo[] getPropertySignature = {
	    new MBeanParameterInfo("propertyName", String.class.getName(), 
		        mbr.getString(mbr.I_BKR_OP_GET_PROPERTY_PARAM_PROP_NAME_DESC))
    		};

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(BrokerOperations.GET_PROPERTY,
		mbr.getString(mbr.I_BKR_OP_GET_PROPERTY_DESC),
		    getPropertySignature, String.class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(BrokerOperations.QUIESCE,
		mbr.getString(mbr.I_BKR_OP_QUIESCE_DESC),
		    null, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.RESET_METRICS,
		mbr.getString(mbr.I_BKR_OP_RESET_METRICS_DESC),
		    null, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.RESTART,
		mbr.getString(mbr.I_BKR_OP_RESTART_DESC),
		    null, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.SHUTDOWN,
		mbr.getString(mbr.I_BKR_OP_SHUTDOWN_DESC),
		    null, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.SHUTDOWN,
		mbr.getString(mbr.I_BKR_OP_SHUTDOWN_DESC),
		    shutdownSignature, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.TAKEOVER,
		mbr.getString(mbr.I_BKR_OP_TAKEOVER_DESC),
		    takeoverSignature, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(BrokerOperations.UNQUIESCE,
		mbr.getString(mbr.I_BKR_OP_UNQUIESCE_DESC),
		    null, Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),
		};


    private static String[] attrChangeTypes = {
		    AttributeChangeNotification.ATTRIBUTE_CHANGE
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    attrChangeTypes,
		    AttributeChangeNotification.class.getName(),
		    mbr.getString(mbr.I_ATTR_CHANGE_NOTIFICATION)
		    )
		};

    public BrokerConfig()  {
	super();

	initProps();

	com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	cfg.addListener("imq.portmapper.port", this);
    }


    public String getBrokerID()  {
        return (Globals.getBrokerID());
    }

    public String getInstanceName()  {
	return (brokerProps.getProperty("imq.instancename"));
    }

    public String getHost()  {
	return (Globals.getBrokerHostName());
    }

    public Integer getPort() throws MBeanException  {
	String s = brokerProps.getProperty("imq.portmapper.port");
	Integer i = null;

	try  {
	    i = new Integer(s);
	} catch (Exception e)  {
	    handleGetterException(BrokerAttributes.PORT, e);
	}

	return (i);
    }

    public void setPort(Integer newPort) throws MBeanException  {
	//Integer oldVal = getPort();
	Properties p = new Properties();
	p.setProperty("imq.portmapper.port", newPort.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(BrokerAttributes.PORT, e);
	}
    }

    public String getVersion()  {
	return (brokerProps.getProperty("imq.product.version"));
    }

    public void quiesce() throws MBeanException  {
	BrokerStateHandler bsh = Globals.getBrokerStateHandler();

	logger.log(Logger.INFO, "Quiesce request received by MBean " + getMBeanName());

	try  {
	    bsh.quiesce();
	} catch (BrokerException e)  {
	    handleOperationException(BrokerOperations.QUIESCE, e);
	}
    }

    public void resetMetrics()  {
	logger.log(Logger.INFO, "Reset metrics request received by MBean " + getMBeanName());
	ResetMetricsHandler.resetAllMetrics();
    }

    public void restart()  {
    	
    	// not allowed to restart an in-process broker
        if (Broker.isInProcess()) {
        	String message = rb.getString(BrokerResources.E_CANNOT_RESTART_IN_PROCESS);
        	
        	logger.log(Logger.WARNING,
        			"BrokerConfig MBean: "+message);
        	return;
        }
    	
        BrokerStateHandler bsh = Globals.getBrokerStateHandler();
        logger.log(Logger.INFO, "Restart request received by MBean " + getMBeanName());

        bsh.initiateShutdown("jmx",
        		0,
        		true,
        		bsh.getRestartCode(),
        		true, Broker.isInProcess(), false);
    }
    
    private boolean hasDirectConnections() {
    	// Please keep this consistent with com.sun.messaging.jmq.jmsserver.data.handlers.admin.ShutdownHandler.hasDirectConnections()
		List connections = ConnectionUtil.getConnectionInfoList(null);
		if (connections.size() == 0) {
			return (false);
		}

		Iterator itr = connections.iterator();
		int i = 0;
		while (itr.hasNext()) {
			ConnectionInfo cxnInfo = (ConnectionInfo) itr.next();
			if (cxnInfo.service.equals("jmsdirect")){
				return true;
			}
		}

		return false;
	}

    public void shutdown() {
    	// not allowed to shutdown an in-process broker that has direct mode connections
        if (Broker.isInProcess() && hasDirectConnections()) {
        	String message = rb.getString(BrokerResources.E_CANNOT_SHUTDOWN_IN_PROCESS);
        	
        	logger.log(Logger.WARNING,
        			"BrokerConfig MBean: "+message);
        	return;
        }
    	
    	shutdown(Boolean.FALSE, Long.valueOf(0));
	}

    public void shutdown(Boolean noFailover, Long time)  {
	BrokerStateHandler bsh = Globals.getBrokerStateHandler();
	boolean failover = (noFailover == null ? true
				: !(noFailover.booleanValue()));

	logger.log(Logger.INFO, "Shutdown request received by MBean " + getMBeanName());

	bsh.initiateShutdown("jmx",
		(time == null ? 0 : time.longValue())* 1000, 
		failover,
		0,
		true, Broker.isInProcess(), false);
    }

    public void takeover(String brokerID) throws MBeanException  {
	BrokerStateHandler bsh = Globals.getBrokerStateHandler();

	logger.log(Logger.INFO, "Request to takeover broker "
		+ brokerID
		+ " received by MBean " + getMBeanName());

	try  {
	    bsh.takeoverBroker(brokerID, null, true);
	} catch (BrokerException e)  {
	    handleOperationException(BrokerOperations.TAKEOVER, e);
	}
    }

    public void unquiesce() throws MBeanException  {
	BrokerStateHandler bsh = Globals.getBrokerStateHandler();

	logger.log(Logger.INFO, "Unquiesce request received by MBean " + getMBeanName());

	try  {
	    bsh.stopQuiesce();
	} catch (BrokerException e)  {
	    handleOperationException(BrokerOperations.UNQUIESCE, e);
	}
    }

    public String getProperty(String propertyName)  {
	return (Globals.getConfig().getProperty(propertyName));
    }

    public String getMBeanName()  {
	return ("BrokerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_BKR_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void validate(String name, String value)
            throws PropertyUpdateException {
    }
            
    public boolean update(String name, String value) {
	Object newVal =  null;
        Object oldVal = null;

	/*
        System.err.println("### cl.update called: "
            + name
            + "="
            + value);
	*/

	if (name.equals("imq.portmapper.port"))  {
	    try  {
	        newVal = Integer.valueOf(value);
	    } catch (NumberFormatException nfe)  {
		logger.log(Logger.WARNING,
		"BrokerConfig MBean: cannot parse internal value of Port: " + nfe);

	        newVal = null;
	    }

	    try  {
	        oldVal = getPort();
	    } catch (MBeanException mbe)  {
		/*
		 * A warning message will be logged by getters on error
		 */

	        oldVal = null;
	    }
            notifyAttrChange(BrokerAttributes.PORT, newVal, oldVal);

	    Agent agent = Globals.getAgent();
	    if (agent != null)  {
	        agent.portMapperPortUpdated((Integer)oldVal, (Integer)newVal);
	    }
	}

        initProps();
        return true;
    }

    public void notifyAttrChange(String attrName, Object newVal, Object oldVal)  {
	sendNotification(
	    new AttributeChangeNotification(this, sequenceNumber++, new Date().getTime(),
	        "Attribute change", attrName, (newVal == null ? "" : newVal.getClass().getName()),
	        oldVal, newVal));
    }

    private void initProps() {
	brokerProps = Globals.getConfig().toProperties();
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());
    }
}
