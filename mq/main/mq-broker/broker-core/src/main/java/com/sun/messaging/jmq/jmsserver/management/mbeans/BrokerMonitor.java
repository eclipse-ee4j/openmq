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
 * @(#)BrokerMonitor.java	1.24 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Iterator;
import java.util.Properties;
import java.lang.management.MemoryUsage;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.management.util.ClusterUtil;
import com.sun.messaging.jmq.jmsserver.management.util.MQAddressUtil;

public class BrokerMonitor extends MQMBeanReadOnly implements ConfigListener {
    private Properties brokerProps = null;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(BrokerAttributes.BROKER_ID,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_BKR_ID),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.EMBEDDED,
					Boolean.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_EMBEDDED),
					true,
					false,
					true),

	    new MBeanAttributeInfo(BrokerAttributes.INSTANCE_NAME,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_INSTANCE_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.RESOURCE_STATE,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_RESOURCE_STATE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.PORT,
					Integer.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_PORT),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.HOST,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_HOST),
					true,
					false,
					false),

	    new MBeanAttributeInfo(BrokerAttributes.VERSION,
					String.class.getName(),
					mbr.getString(mbr.I_BKR_ATTR_VERSION),
					true,
					false,
					false)
			};

    private static String[] brokerNotificationTypes = {
		    BrokerNotification.BROKER_RESOURCE_STATE_CHANGE,
		    BrokerNotification.BROKER_QUIESCE_COMPLETE,
		    BrokerNotification.BROKER_QUIESCE_START,
		    BrokerNotification.BROKER_SHUTDOWN_START,
		    BrokerNotification.BROKER_TAKEOVER_COMPLETE,
		    BrokerNotification.BROKER_TAKEOVER_FAIL,
		    BrokerNotification.BROKER_TAKEOVER_START
		};

    private static String[] clusterNotificationTypes = {
		    ClusterNotification.CLUSTER_BROKER_JOIN
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    brokerNotificationTypes,
		    BrokerNotification.class.getName(),
		    mbr.getString(mbr.I_BKR_NOTIFICATIONS)
		    ),

	    new MBeanNotificationInfo(
		    clusterNotificationTypes,
		    ClusterNotification.class.getName(),
		    mbr.getString(mbr.I_CLS_NOTIFICATIONS)
		    )
		};


    public BrokerMonitor()  {
	super();
	initProps();

	BrokerConfig cfg = Globals.getConfig();
	cfg.addListener("imq.instancename", this);
	cfg.addListener("imq.portmapper.port", this);
	cfg.addListener("imq.product.version", this);
	cfg.addListener("imq.system.max_count", this);
    }

    public String getBrokerID()  {
        return (Globals.getBrokerID());
    }

    public Boolean getEmbedded() {
	return (Boolean.valueOf(Broker.isInProcess()));
    }

    public Boolean isEmbedded() {
	return (getEmbedded());
    }

    public String getInstanceName()  {
	return (brokerProps.getProperty("imq.instancename"));
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

    public String getHost()  {
	return (Globals.getBrokerHostName());
    }

    public String getResourceState()  {
	return (Globals.getMemManager().getCurrentLevelName());
    }

    public MQAddress getMQAddress()  {
	MQAddress addr = null;

	try  {
	    addr = MQAddressUtil.getPortMapperMQAddress(getPort());
	} catch (Exception e)  {
	}

	return (addr);
    }


    public String getVersion()  {
	return (brokerProps.getProperty("imq.product.version"));
    }

    public String getMBeanName()  {
	return("BrokerMonitor");
    }

    public String getMBeanDescription()  {
	return(mbr.getString(mbr.I_BKR_MON_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (null);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void validate(String name, String value)
            throws PropertyUpdateException {
    }
            
    public boolean update(String name, String value) {
	/*
        System.err.println("### cl.update called: "
            + name
            + "="
            + value);
	*/
        initProps();
        return true;
    }

    public void notifyResourceStateChange(String oldResourceState, String newResourceState, MemoryUsage heapMemoryUsage)  {
	BrokerNotification n = new BrokerNotification(BrokerNotification.BROKER_RESOURCE_STATE_CHANGE, 
						this, sequenceNumber++);
	n.setOldResourceState(oldResourceState);
	n.setNewResourceState(newResourceState);
	n.setHeapMemoryUsage(heapMemoryUsage);
	sendNotification(n);
    }

    public void notifyQuiesceStart()  {
	sendNotification(
	    new BrokerNotification(BrokerNotification.BROKER_QUIESCE_START, this, sequenceNumber++));
    }

    public void notifyQuiesceComplete()  {
	sendNotification(
	    new BrokerNotification(BrokerNotification.BROKER_QUIESCE_COMPLETE, this, sequenceNumber++));
    }

    public void notifyShutdownStart()  {
	sendNotification(
	    new BrokerNotification(BrokerNotification.BROKER_SHUTDOWN_START, this, sequenceNumber++));
    }

    public void notifyTakeoverStart(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_START, 
						this, sequenceNumber++);
	n.setFailedBrokerID(brokerID);

	cd = getLocalBrokerInfo();

	if (cd != null)  {
	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	}

	sendNotification(n);
    }

    public void notifyTakeoverComplete(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_COMPLETE, 
						this, sequenceNumber++);

	n.setFailedBrokerID(brokerID);

	cd = getLocalBrokerInfo();

	if (cd != null)  {
	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	}

	sendNotification(n);
    }

    public void notifyTakeoverFail(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_FAIL, 
						this, sequenceNumber++);

	n.setFailedBrokerID(brokerID);

	cd = getLocalBrokerInfo();

	if (cd != null)  {
	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	}

	sendNotification(n);
    }

    public void notifyClusterBrokerJoin(String brokerID)  {
	ClusterNotification n;
	n = new ClusterNotification(ClusterNotification.CLUSTER_BROKER_JOIN, 
			this, sequenceNumber++);

	n.setBrokerID(brokerID);
	n.setBrokerAddress(ClusterUtil.getBrokerAddress(brokerID));

	n.setClusterID(Globals.getClusterID());
	n.setHighlyAvailable(Globals.getHAEnabled());

	boolean isMaster = false;
	if (n.isHighlyAvailable())  {
	    isMaster = false;
	} else  {
	    /*
	     * FIXME: Need to determine if broker is master broker or not
	     */
	    isMaster = false;
	}
	n.setMasterBroker(isMaster);

	sendNotification(n);
    }

    private CompositeData getLocalBrokerInfo()  {
        ClusterManager cm = Globals.getClusterManager();
        CompositeData cd = null;

        if (cm == null)  {
            return (null);
        }

	MQAddress address = cm.getMQAddress();

        String id = null;

        try  {
            id = cm.lookupBrokerID(BrokerMQAddress.createAddress(address.toString()));
        } catch (Exception e)  {
            return (null);
        }

        if ((id == null) || (id.equals("")))  {
            return (null);
        }

        try  {
            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = ClusterUtil.getConfigCompositeData(cb);
        } catch (Exception e)  {
            return (null);
        }

        return(cd);
    }

    private void initProps() {
	brokerProps = Globals.getConfig().toProperties();
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());
    }
}
