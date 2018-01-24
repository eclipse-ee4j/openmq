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
 * @(#)ClusterUtil.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import java.util.Hashtable;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenDataException;

import com.sun.messaging.jms.management.server.BrokerClusterInfo;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetClusterHandler;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;

public class ClusterUtil  {
    /*
     * Broker Cluster Info item names for Config MBeans
     */
    private static final String[] configBrokerInfoItemNames = {
                            BrokerClusterInfo.ADDRESS,
                            BrokerClusterInfo.ID
                    };

    /*
     * Broker Cluster Info item descriptions for Config MBeans
     * TBD: use real descriptions
     */
    private static final String[] configBrokerInfoItemDesc = configBrokerInfoItemNames;

    /*
     * Broker Cluster Info item types for Config MBeans
     */
    private static final OpenType[] configItemTypes = {
			    SimpleType.STRING,		// address
			    SimpleType.STRING		// id
                    };

    /*
     * Broker Cluster Info composite type for Config MBeans
     */
    private static volatile CompositeType configCompType = null;

    public static String getBrokerAddress(String brokerID)  {
        ClusterManager cmgr;
        ClusteredBroker bkr;
	MQAddress	addr;

	if (brokerID == null)  {
	    return (null);
	}

        cmgr = Globals.getClusterManager();
	if (cmgr == null)  {
	    return (null);
	}

        bkr = cmgr.getBroker(brokerID);
	if (bkr == null)  {
	    return (null);
	}

        addr = bkr.getBrokerURL();
	if (addr == null) {
	    return (null);
	}

        return (addr.toString());
    }

    public static String getShortBrokerAddress(String brokerID)  {
	BrokerMQAddress ba = null;
	String longAddr = getBrokerAddress(brokerID);
	Logger logger = Globals.getLogger();

	if (longAddr == null)  {
	    return (null);
	}

	try  {
	    ba = BrokerMQAddress.createAddress(longAddr);
	} catch (Exception e)  {
            BrokerResources	rb = Globals.getBrokerResources();

            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_FAILED_TO_OBTAIN_BKR_ADDRESS_FROM_ID, brokerID),
		e);

	    return (null);
	}

	if (ba == null)  {
	    return (null);
	}

        return (ba.getHost().getHostName() + ":" + ba.getPort());
    }

    public static boolean isMasterBroker(String brokerAddress)  {
	ClusterManager cm = Globals.getClusterManager();
	ClusteredBroker cb = null, master;
	String id = null;
	boolean isMaster = false;

	if (cm == null)  {
	    return (false);
	}

	try  {
            id = cm.lookupBrokerID(BrokerMQAddress.createAddress(brokerAddress));
        } catch (Exception e)  {
	    return (false);
        }

	if ((id == null) || (id.equals("")))  {
            return (false);
        }

	try  {
	    cb = cm.getBroker(id);

	    if (cb == null)  {
                return (false);
	    }
	} catch(Exception e)  {
            return (false);
	}

	master = cm.getMasterBroker();

	if (master == null)  {
	    return (false);
	}

	return (master.equals(cb));
    }

    public static CompositeData getConfigCompositeData(ClusteredBroker cb) 
					throws OpenDataException  {
	Logger logger = Globals.getLogger();
	CompositeData cds = null;

	Hashtable bkrInfo = GetClusterHandler.getBrokerClusterInfo(cb, logger);

	String id = null;

	if (Globals.getHAEnabled())  {
	    id = (String)bkrInfo.get(BrokerClusterInfo.ID);
	}

	Object[] brokerInfoItemValues = {
			    bkrInfo.get(BrokerClusterInfo.ADDRESS),
			    id
			};

        if (configCompType == null)  {
            configCompType = new CompositeType("BrokerClusterInfoConfig", "BrokerClusterInfoConfig", 
                        configBrokerInfoItemNames, configBrokerInfoItemDesc, configItemTypes);
        }

	cds = new CompositeDataSupport(configCompType, 
			configBrokerInfoItemNames, brokerInfoItemValues);
	
	return (cds);
    }
}
