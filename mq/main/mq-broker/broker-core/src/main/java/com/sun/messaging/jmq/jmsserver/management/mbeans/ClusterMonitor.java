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
 * @(#)ClusterMonitor.java	1.18 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Hashtable;
import java.net.MalformedURLException;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenDataException;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetClusterHandler;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.management.util.ClusterUtil;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;

public class ClusterMonitor extends MQMBeanReadOnly  {
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ClusterAttributes.CONFIG_FILE_URL,
					String.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_CONFIG_FILE_URL_DESC),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ClusterAttributes.CLUSTER_ID,
					String.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_CLUSTER_ID_DESC),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ClusterAttributes.HIGHLY_AVAILABLE,
					Boolean.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_HIGHLY_AVAILABLE_DESC),
					true,
					false,
					true),

	    new MBeanAttributeInfo(ClusterAttributes.USE_SHARED_DATABASE_FOR_CONFIG_RECORD,
					Boolean.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_USE_SHARED_DATABASE_FOR_CONFIG_RECORD_DESC),
					true,
					false,
					true),

	    new MBeanAttributeInfo(ClusterAttributes.LOCAL_BROKER_INFO,
					CompositeData.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_LOCAL_BROKER_INFO_DESC),
					true,
					false,
					false),

	    new MBeanAttributeInfo(ClusterAttributes.MASTER_BROKER_INFO,
					CompositeData.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_MASTER_BROKER_INFO_DESC),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] getBrokerInfoByAddrSignature = {
        new MBeanParameterInfo("BrokerAddress", String.class.getName(), 
				mbr.getString(mbr.I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_PARAM_ADDR_DESC))
				     };

    private static MBeanParameterInfo[] getBrokerInfoByIdSignature = {
        new MBeanParameterInfo("BrokerID", String.class.getName(), 
				mbr.getString(mbr.I_CLS_OP_GET_BROKER_INFO_BY_ID_PARAM_ID_DESC))
				     };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_ADDRESSES,
		mbr.getString(mbr.I_CLS_MON_OP_GET_BROKER_ADDRESSES_DESC),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_IDS,
		mbr.getString(mbr.I_CLS_MON_OP_GET_BROKER_IDS_DESC),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_INFO,
		mbr.getString(mbr.I_CLS_MON_OP_GET_BROKER_INFO_DESC),
		    null, 
		    CompositeData[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_INFO_BY_ADDRESS,
		mbr.getString(mbr.I_CLS_OP_GET_BROKER_INFO_BY_ADDRESS_DESC),
		    getBrokerInfoByAddrSignature, 
		    CompositeData.class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_INFO_BY_ID,
		mbr.getString(mbr.I_CLS_OP_GET_BROKER_INFO_BY_ID_DESC),
		    getBrokerInfoByIdSignature, 
		    CompositeData.class.getName(),
		    MBeanOperationInfo.INFO),
		    		    
		};

    private static String[] clsNotificationTypes = {
		    ClusterNotification.CLUSTER_BROKER_DOWN,
		    ClusterNotification.CLUSTER_BROKER_JOIN
		};

    private static String[] brokerNotificationTypes = {
		    BrokerNotification.BROKER_TAKEOVER_COMPLETE,
		    BrokerNotification.BROKER_TAKEOVER_FAIL,
		    BrokerNotification.BROKER_TAKEOVER_START
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    brokerNotificationTypes,
		    BrokerNotification.class.getName(),
		    mbr.getString(mbr.I_BKR_NOTIFICATIONS)
		    ),

	    new MBeanNotificationInfo(
		    clsNotificationTypes,
		    ClusterNotification.class.getName(),
		    mbr.getString(mbr.I_CLS_NOTIFICATIONS)
		    )
		};
	
    /*
     * Broker Cluster Info item names
     */
    private static final String[] brokerInfoItemNames = {
                            BrokerClusterInfo.ADDRESS,
                            BrokerClusterInfo.ID,
			    BrokerClusterInfo.STATE,
			    BrokerClusterInfo.STATE_LABEL,
			    BrokerClusterInfo.NUM_MSGS,
			    BrokerClusterInfo.TAKEOVER_BROKER_ID,
			    BrokerClusterInfo.STATUS_TIMESTAMP
                    };

    /*
     * Broker Cluster Info item descriptions
     * TBD: use real descriptions
     */
    private static final String[] brokerInfoItemDesc = brokerInfoItemNames;

    /*
     * Broker Cluster Info item types
     */
    private static final OpenType[] itemTypes = {
			    SimpleType.STRING,		// address
			    SimpleType.STRING,		// id
			    SimpleType.INTEGER,		// state
			    SimpleType.STRING,		// state label
			    SimpleType.LONG,		// num msgs
			    SimpleType.STRING,		// takeover broker id
			    SimpleType.LONG		// timestamp
                    };


    /*
     * Broker Cluster Info composite type.
     */
    private CompositeType compType = null;


    public ClusterMonitor()  {
	super();
    }

    public String getConfigFileURL()  {
	Properties brokerProps = Globals.getConfig().toProperties();

	return (brokerProps.getProperty(Globals.IMQ + ".cluster.url"));
    }

    public String getClusterID()  {
        return (Globals.getClusterID());
    }

    public Boolean isHighlyAvailable()  {
	return(getHighlyAvailable());
    }

    public Boolean getHighlyAvailable()  {
        return (Boolean.valueOf(Globals.getHAEnabled()));
    }

    public Boolean isUseSharedDatabaseForConfigRecord() {
    return(getUseSharedDatabaseForConfigRecord());
    }

    public Boolean getUseSharedDatabaseForConfigRecord() {
        return (Boolean.valueOf(Globals.useSharedConfigRecord()));
    }

    public CompositeData getLocalBrokerInfo() throws MBeanException  {
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
            handleGetterException(ClusterAttributes.LOCAL_BROKER_INFO, e);
        }

        if ((id == null) || (id.equals("")))  {
            return (null);
        }

        try  {
            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = getCompositeData(cb);
        } catch (Exception e)  {
            handleGetterException(ClusterAttributes.LOCAL_BROKER_INFO, e);
        }

        return(cd);
    }


    public String getMasterBroker()  {
	Properties brokerProps = Globals.getConfig().toProperties();

	return (brokerProps.getProperty(Globals.IMQ + ".cluster.masterbroker"));
    }

    public CompositeData getMasterBrokerInfo() throws MBeanException  {
	if (Globals.getHAEnabled())  {
	    return (null);
	}
    if (Globals.useSharedConfigRecord()) {
	    return (null);
    }

	String mbAddr = getMasterBroker();
        ClusterManager cm = Globals.getClusterManager();
        CompositeData cd = null;

        if (cm == null)  {
            return (null);
        }

        String id = null;

        try  {
            id = cm.lookupBrokerID(BrokerMQAddress.createAddress(mbAddr));
        } catch (Exception e)  {
            handleGetterException(ClusterAttributes.MASTER_BROKER_INFO, e);
        }

        if ((id == null) || (id.equals("")))  {
            return (null);
        }

        try  {
            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = getCompositeData(cb);
        } catch (Exception e)  {
            handleGetterException(ClusterAttributes.MASTER_BROKER_INFO, e);
        }

        return(cd);
    }


    public String[] getBrokerAddresses()  {
        return(getBrokerIDsOrAddresses(false));
    }

    public String[] getBrokerIDs()  {
	if (!Globals.getHAEnabled())  {
	    return (null);
	}

        return(getBrokerIDsOrAddresses(true));
    }

    private String[] getBrokerIDsOrAddresses(boolean getID) {
	ClusterManager cm = Globals.getClusterManager();
	ArrayList al = new ArrayList();
	String	list[] = null;

	if (cm == null)  {
	    return (null);
	}

	Iterator itr = cm.getKnownBrokers(true);

	if (itr == null)  {
	    return (null);
	}

	while (itr.hasNext()) {
	    ClusteredBroker cb = (ClusteredBroker)itr.next();
	    Hashtable bkrInfo = GetClusterHandler.getBrokerClusterInfo(cb, logger);

	    if (bkrInfo == null)  {
		logger.log(logger.WARNING, 
		    "MBean: "
		    + getMBeanName()
		    + "Problem encountered while constructing list of broker IDs or addresses, continuing...");
		continue;
	    }

	    String idOrAddress;

	    if (getID)  {
	        idOrAddress = (String)bkrInfo.get(BrokerClusterInfo.ID);
	    } else  {
	        idOrAddress = (String)bkrInfo.get(BrokerClusterInfo.ADDRESS);
	    }

	    if (idOrAddress == null)  {
		logger.log(logger.WARNING, 
		    "MBean: "
		    + getMBeanName()
		    + "Problem encountered while constructing list of broker IDs or addresses, continuing...");
		continue;
	    }

	    al.add(idOrAddress);
	}

	list = new String [ al.size() ];
	list = (String[])al.toArray(list);

        return (list);
    }

    public CompositeData[] getBrokerInfo() throws MBeanException  {
	ClusterManager cm = Globals.getClusterManager();
	CompositeData cds[] = null;
	ArrayList al = new ArrayList();

	if (cm == null)  {
	    return (null);
	}

	Iterator itr = cm.getKnownBrokers(true);

	if (itr == null)  {
	    return (null);
	}

	while (itr.hasNext()) {
	    ClusteredBroker cb = (ClusteredBroker)itr.next();

	    try  {
	        CompositeData cd = getCompositeData(cb);
	        al.add(cd);
	    } catch (Exception e)  {
	        handleOperationException(ClusterOperations.GET_BROKER_INFO, e);
	    }
	}

	cds = new CompositeData [ al.size() ];
	cds = (CompositeData[])al.toArray(cds);

        return (cds);
    }

    public CompositeData getBrokerInfoByAddress(String address) throws MBeanException  {
        ClusterManager cm = Globals.getClusterManager();
        CompositeData cd = null;

        if (cm == null)  {
            return (null);
        }

        String id = null;

        try  {
            id = cm.lookupBrokerID(BrokerMQAddress.createAddress(address));
        } catch (Exception e)  {
            handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ADDRESS, e);
        }

        if ((id == null) || (id.equals("")))  {
            return (null);
        }

        try  {
            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = getCompositeData(cb);
        } catch (Exception e)  {
            handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ADDRESS, e);
        }

        return(cd);
    }


    public CompositeData getBrokerInfoByID(String id) throws MBeanException  {
        ClusterManager cm = Globals.getClusterManager();
        CompositeData cd = null;

	if (!Globals.getHAEnabled())  {
	    return (null);
	}

        try  {
            if (cm == null)  {
                return (null);
            }

            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = getCompositeData(cb);
        } catch (Exception e)  {
            handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ID, e);
        }

        return(cd);
    }
    
    private CompositeData getCompositeData(ClusteredBroker cb) 
					throws OpenDataException  {
	CompositeData cds = null;

	Hashtable bkrInfo = GetClusterHandler.getBrokerClusterInfo(cb, logger);

	String id = null;

	if (Globals.getHAEnabled())  {
	    id = (String)bkrInfo.get(BrokerClusterInfo.ID);
	}

	Object[] brokerInfoItemValues = {
			    bkrInfo.get(BrokerClusterInfo.ADDRESS),
			    id,
			    bkrInfo.get(BrokerClusterInfo.STATE),
			    com.sun.messaging.jms.management.server.BrokerState.
				toString(((Integer)bkrInfo.get(BrokerClusterInfo.STATE)).intValue()),
			    bkrInfo.get(BrokerClusterInfo.NUM_MSGS),
			    bkrInfo.get(BrokerClusterInfo.TAKEOVER_BROKER_ID),
			    bkrInfo.get(BrokerClusterInfo.STATUS_TIMESTAMP)
			};

        if (compType == null)  {
            compType = new CompositeType("BrokerClusterInfo", "BrokerClusterInfo", 
            brokerInfoItemNames, brokerInfoItemDesc, itemTypes);
        }

	cds = new CompositeDataSupport(compType, brokerInfoItemNames, brokerInfoItemValues);
	
	return (cds);
    }

    public String getMBeanName()  {
	return ("ClusterMonitor");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CLS_MON_DESC));
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

    public void notifyTakeoverStart(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_START, 
						this, sequenceNumber++);
	n.setFailedBrokerID(brokerID);

	try  {
	    cd = getLocalBrokerInfo();

	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	} catch (MBeanException e)  {
            logger.log(logger.WARNING, 
                "MBean: "
                + getMBeanName()
                + "Problem encountered while sending notification " 
				+ BrokerNotification.BROKER_TAKEOVER_START);
	}

	sendNotification(n);
    }

    public void notifyTakeoverComplete(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_COMPLETE, 
						this, sequenceNumber++);
	n.setFailedBrokerID(brokerID);

	try  {
	    cd = getLocalBrokerInfo();

	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	} catch (MBeanException e)  {
            logger.log(logger.WARNING, 
                "MBean: "
                + getMBeanName()
                + "Problem encountered while sending notification " 
				+ BrokerNotification.BROKER_TAKEOVER_COMPLETE);
	}

	sendNotification(n);
    }

    public void notifyTakeoverFail(String brokerID)  {
	BrokerNotification n;
	CompositeData cd;

	n = new BrokerNotification(BrokerNotification.BROKER_TAKEOVER_FAIL, 
						this, sequenceNumber++);
	n.setFailedBrokerID(brokerID);

	try  {
	    cd = getLocalBrokerInfo();

	    /*
	     * This notification will be sent only by brokers in a HA Cluster
	     */
	    n.setBrokerAddress((String)cd.get(BrokerClusterInfo.ADDRESS));
	    n.setBrokerID((String)cd.get(BrokerClusterInfo.ID));
	} catch (MBeanException e)  {
            logger.log(logger.WARNING, 
                "MBean: "
                + getMBeanName()
                + "Problem encountered while sending notification " 
				+ BrokerNotification.BROKER_TAKEOVER_FAIL);
	}

	sendNotification(n);
    }

    public void notifyClusterBrokerDown(String brokerID)  {
	ClusterNotification n;
	n = new ClusterNotification(ClusterNotification.CLUSTER_BROKER_DOWN, 
			this, sequenceNumber++);

	n.setBrokerAddress(ClusterUtil.getShortBrokerAddress(brokerID));
	n.setHighlyAvailable(Globals.getHAEnabled());

	if (n.isHighlyAvailable())  {
	    n.setClusterID(Globals.getClusterID());
	    n.setBrokerID(brokerID);
	} else  {
	    n.setMasterBroker(ClusterUtil.isMasterBroker(n.getBrokerAddress()));
	}

	sendNotification(n);
    }

    public void notifyClusterBrokerJoin(String brokerID)  {
	ClusterNotification n;
	n = new ClusterNotification(ClusterNotification.CLUSTER_BROKER_JOIN, 
			this, sequenceNumber++);

	n.setBrokerAddress(ClusterUtil.getShortBrokerAddress(brokerID));
	n.setHighlyAvailable(Globals.getHAEnabled());

	if (n.isHighlyAvailable())  {
	    n.setClusterID(Globals.getClusterID());
	    n.setBrokerID(brokerID);
	} else  {
	    n.setMasterBroker(ClusterUtil.isMasterBroker(n.getBrokerAddress()));
	}

	sendNotification(n);
    }
}
