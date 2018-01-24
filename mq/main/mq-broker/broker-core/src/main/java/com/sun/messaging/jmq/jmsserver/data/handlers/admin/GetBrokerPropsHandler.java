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
 * @(#)GetBrokerPropsHandler.java	1.24 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.Iterator;
import java.util.Properties;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.Queue;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.license.*;
import com.sun.messaging.jmq.jmsserver.persist.api.sharecc.ShareConfigChangeStore;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class GetBrokerPropsHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public GetBrokerPropsHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

        int status = Status.OK;
        String emsg = null;

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                cmd_props);
        }

	/* We need to create a copy of the broker configuration because
	 * the protocol requires we send a serialized java.util.Properties
	 * object. If we just serialize (or clone the serialize) BrokerConfig
	 * it will end up being a serialized BrokerConfig object, not
	 * a serialized Properties object (even if we cast). So we do
	 * this rather expensive operation.
	 */
	Properties brokerProps = Globals.getConfig().toProperties();


	/* Add the version properties */
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());


	try  {
	    addLicenseInfo(brokerProps);
	} catch (Exception ex)  {
	    logger.log(Logger.WARNING, rb.X_CANT_GET_LICENSE_EXCEPTION, ex);
	}

        brokerProps.put(Globals.IMQ + ".system.current_count",
                        String.valueOf(DL.totalCount()));
        brokerProps.put(Globals.IMQ + ".system.current_size",
                         String.valueOf(DL.totalBytes()));

        Queue[] qs = DL.getDMQ(null);
        Queue dmq = qs[0]; //PART
        brokerProps.put(Globals.IMQ + ".dmq.current_count",
                        String.valueOf(dmq.size()));
        brokerProps.put(Globals.IMQ + ".dmq.current_size",
                         String.valueOf(dmq.byteSize()));

        String val = brokerProps.getProperty(DL.USE_DMQ_STR);
        if (val == null || val.trim().equals("")) {
            brokerProps.put(DL.USE_DMQ_STR, String.valueOf(DL.defaultUseDMQ));
        }


        if (Globals.getBrokerID() != null) {
            brokerProps.put(Globals.IMQ + ".brokerid", Globals.getBrokerID());
        } else if (Globals.isBDBStore() && !Globals.getSFSHAEnabled()) {
            ClusteredBroker cb = null;
            try {
                cb = Globals.getClusterManager().getLocalBroker();
                brokerProps.put(Globals.IMQ + ".brokerid", cb.getNodeName());
            } catch (Exception e) {
                logger.logStack(logger.WARNING, e.getMessage(), e);
            }
        }
        if (Globals.getClusterID() != null) {
            brokerProps.put(Globals.IMQ + ".cluster.clusterid", Globals.getClusterID());
        }
        if (Globals.getBDBREPEnabled()) {
            brokerProps.put(StoreManager.BDB_REPLICATION_ENABLED_PROP, "true");
        }
        if (Globals.isBDBStore()) {
            brokerProps.put(Globals.IMQ+".storemigratable", "true");
        }
        if (DL.isPartitionMode() && DL.isPartitionMigratable()) {
            brokerProps.put(Globals.IMQ+".partitionmigratable", "true");
        }

        brokerProps.put(Globals.IMQ + ".embedded", Boolean.toString(Broker.isInProcess()));

        if (Globals.getHAEnabled() && !Globals.getSFSHAEnabled()) {
            brokerProps.put(ClusterManager.CONFIG_SERVER, "");

        } else if (Globals.useSharedConfigRecord()) {
            String shareccVendor = null;
            try {
                shareccVendor = Globals.getStore().getShareConfigChangeStore().
                                    getVendorPropertySetting();
            } catch (BrokerException e) {
	            logger.logStack(Logger.WARNING, e.getMessage(), e);
            }
            brokerProps.put(ClusterManager.CONFIG_SERVER, "["+Globals.NO_MASTERBROKER_PROP+"="+
                        brokerProps.get(Globals.NO_MASTERBROKER_PROP)+", "+shareccVendor+"]");

        } 

        /**
         * OK, use the cluster object to get active and normal brokers
         */
        ClusterManager cfg = Globals.getClusterManager();

            // calculate url
            String list = null;
            Iterator itr = cfg.getConfigBrokers();

            // OK we want to remove any duplicates
            Set s = new HashSet();
            while (itr.hasNext()) {
                ClusteredBroker cb = (ClusteredBroker)itr.next();
                s.add(cb.getBrokerURL().toString());
            }
            itr = s.iterator();
            while (itr.hasNext()) {
                if (list == null) {
                    list = itr.next().toString();
                } else {
                    list += "," + itr.next().toString();
                }
            }
            if (list == null) list = "";
            brokerProps.put("imq.cluster.brokerlist", list);

            list = null;
            s = new HashSet();
            itr = cfg.getActiveBrokers();
            while (itr.hasNext()) {
                ClusteredBroker cb = (ClusteredBroker)itr.next();
                s.add(cb.getBrokerURL().toString());
            }
            itr = s.iterator();
            while (itr.hasNext()) {
                if (list == null) {
                    list = itr.next().toString();
                } else {
                    list += "," + itr.next().toString();
                }
            }
            if (list == null) list = "";
            brokerProps.put("imq.cluster.brokerlist.active", list);

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.GET_BROKER_PROPS_REPLY,
		status, emsg);

	setBodyObject(reply, brokerProps);
	parent.sendReply(con, cmd_msg, reply);
        return true;
    }

    private void addLicenseInfo(Properties brokerProps) throws BrokerException  {
        LicenseBase license = null;

	license = Globals.getCurrentLicense(null);

	brokerProps.put("imq.license.description",
			license.getProperty(license.PROP_DESCRIPTION));
    }
}
