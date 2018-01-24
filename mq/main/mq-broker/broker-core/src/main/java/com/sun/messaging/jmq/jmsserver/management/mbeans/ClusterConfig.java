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
 * @(#)ClusterConfig.java	1.17 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Date;
import java.util.Iterator;
import java.util.ArrayList;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetClusterHandler;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.management.util.ClusterUtil;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jms.management.server.*;

public class ClusterConfig extends MQMBeanReadWrite
					implements ConfigListener  {
    private Properties brokerProps = null;
    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(ClusterAttributes.CONFIG_FILE_URL,
					String.class.getName(),
					mbr.getString(mbr.I_CLS_ATTR_CONFIG_FILE_URL_DESC),
					true,
					true,
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
    
    private static MBeanParameterInfo[] changeMasterBrokerSignature = {
	    new MBeanParameterInfo("oldMasterBroker", String.class.getName(), 
		                        mbr.getString(mbr.I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_OLDMASTERBROKER_DESC)),
	    new MBeanParameterInfo("newMasterBroker", String.class.getName(), 
		                        mbr.getString(mbr.I_CLS_OP_CHANGE_MASTER_BROKER_PARAM_NEWMASTERBROKER_DESC))
		        };    

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_ADDRESSES,
		mbr.getString(mbr.I_CLS_CFG_OP_GET_BROKER_ADDRESSES_DESC),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_IDS,
		mbr.getString(mbr.I_CLS_CFG_OP_GET_BROKER_IDS_DESC),
		    null, 
		    String[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(ClusterOperations.GET_BROKER_INFO,
		mbr.getString(mbr.I_CLS_CFG_OP_GET_BROKER_INFO_DESC),
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
		    
		new MBeanOperationInfo(ClusterOperations.CHANGE_MASTER_BROKER,
		mbr.getString(mbr.I_CLS_OP_CHANGE_MASTER_BROKER),
			changeMasterBrokerSignature, 
			CompositeData.class.getName(),
		    MBeanOperationInfo.ACTION),		    

	    new MBeanOperationInfo(ClusterOperations.RELOAD,
		mbr.getString(mbr.I_CLS_CFG_OP_RELOAD_DESC),
		    null, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION)
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

    public ClusterConfig()  {
	super();
	initProps();

	com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	cfg.addListener("imq.cluster.url", this);
    }

    public void setConfigFileURL(String s) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.cluster.url", s);

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(ClusterAttributes.CONFIG_FILE_URL, e);
	}
    }
    public String getConfigFileURL()  {
	return (brokerProps.getProperty(Globals.IMQ + ".cluster.url"));
    }

    public String getClusterID()  {
        return (Globals.getClusterID());
    }

    public Boolean isUseSharedDatabaseForConfigRecord() {
	return(getUseSharedDatabaseForConfigRecord());
    }

    public Boolean getUseSharedDatabaseForConfigRecord() {
        return (Boolean.valueOf(Globals.useSharedConfigRecord()));
    }

    public Boolean isHighlyAvailable()  {
	return(getHighlyAvailable());
    }

    public Boolean getHighlyAvailable()  {
        return (Boolean.valueOf(Globals.getHAEnabled()));
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

            cd = ClusterUtil.getConfigCompositeData(cb);
        } catch (Exception e)  {
            handleGetterException(ClusterAttributes.LOCAL_BROKER_INFO, e);
        }

        return(cd);
    }

    public String getMasterBroker()  {
	brokerProps = Globals.getConfig().toProperties();

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

            cd = ClusterUtil.getConfigCompositeData(cb);
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

    private String[] getBrokerIDsOrAddresses(boolean getID)  {
	ClusterManager cm = Globals.getClusterManager();
	ArrayList al = new ArrayList();
	String	list[] = null;

	if (cm == null)  {
	    return (null);
	}

	Iterator itr = cm.getConfigBrokers();

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

	Iterator itr = cm.getConfigBrokers();

	if (itr == null)  {
	    return (null);
	}

	while (itr.hasNext()) {
	    ClusteredBroker cb = (ClusteredBroker)itr.next();

	    try  {
	        CompositeData cd = ClusterUtil.getConfigCompositeData(cb);
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

            cd = ClusterUtil.getConfigCompositeData(cb);
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

	/*
	 * FIXME: Check if id is in *configured* id List 
	 */

        try  {
            if (cm == null)  {
                return (null);
            }

            ClusteredBroker cb = cm.getBroker(id);

	    if (cb == null)  {
		return (null);
	    }

            cd = ClusterUtil.getConfigCompositeData(cb);
        } catch (Exception e)  {
            handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ID, e);
        }

        return(cd);
    }
    
    public CompositeData changeMasterBroker(String oldMasterBroker, String newMasterBroker) throws MBeanException {
		CompositeData result = null;

		String[] itemNames = { ChangeMasterBrokerResultInfo.SUCCESS, ChangeMasterBrokerResultInfo.STATUS_CODE, ChangeMasterBrokerResultInfo.DETAIL_MESSAGE };
		String[] itemDescriptions = { ChangeMasterBrokerResultInfo.SUCCESS, ChangeMasterBrokerResultInfo.STATUS_CODE, ChangeMasterBrokerResultInfo.DETAIL_MESSAGE };
		OpenType[] itemTypes = { 
				SimpleType.BOOLEAN, // success flag
				SimpleType.INTEGER, // status code
				SimpleType.STRING, // detail message
		};

		BrokerMQAddress oldmba = null;
		BrokerMQAddress newmba = null;

		try {
			oldmba = BrokerMQAddress.createAddress(oldMasterBroker);
			newmba = BrokerMQAddress.createAddress(newMasterBroker);
		} catch (Exception e) {
			handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ID, e);
			// does not return
		}

		Boolean success = null;
		Integer statusCode=null;
		String errorCode=null;
		try {
			Globals.getClusterBroadcast().changeMasterBroker(newmba, oldmba);
			success = Boolean.TRUE;
			statusCode = 0;
			errorCode="";
		} catch (BrokerException e) {
			success = Boolean.FALSE;
			statusCode = e.getStatusCode();
			errorCode = e.getMessage()+"["+Status.getString(statusCode)+"]";
		}
		Object[] itemValues = { success, statusCode, errorCode };

		try {
			CompositeType changeMasterBrokerCompositeType = new CompositeType("ChangeMasterBrokerResult",
					"ChangeMasterBrokerResult", itemNames, itemDescriptions, itemTypes);
			result = new CompositeDataSupport(changeMasterBrokerCompositeType, itemNames, itemValues);

		} catch (Exception e) {
			handleOperationException(ClusterOperations.GET_BROKER_INFO_BY_ID, e);
			// does not return
		}
		return result;
	}

    public void reload() throws MBeanException  {
	try  {
	    Globals.getClusterBroadcast().reloadCluster();
	} catch (Exception e)  {
	    handleOperationException(ClusterOperations.RELOAD, e);
	}
    }

    public String getMBeanName()  {
	return ("ClusterConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_CLS_CFG_DESC));
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
	Object newVal, oldVal;

	/*
        System.err.println("### cl.update called: "
            + name
            + "="
            + value);
	*/

	if (name.equals("imq.cluster.url"))  {
	    newVal = value;
	    oldVal = getConfigFileURL();
            notifyAttrChange(ClusterAttributes.CONFIG_FILE_URL, 
				newVal, oldVal);
	}

        initProps();
        return true;
    }

    public void notifyAttrChange(String attrName, Object newVal, Object oldVal)  {
	sendNotification(
	    new AttributeChangeNotification(this, sequenceNumber++, new Date().getTime(),
	        "Attribute change", attrName, newVal.getClass().getName(),
	        oldVal, newVal));
    }

    private void initProps() {
	brokerProps = Globals.getConfig().toProperties();
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());
    }
}
