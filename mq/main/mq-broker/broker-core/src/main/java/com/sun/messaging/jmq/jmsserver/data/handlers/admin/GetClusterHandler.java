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
 * @(#)GetClusterHandler.java	1.16 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.core.BrokerMQAddress;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.admin.ConsumerInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.persist.api.StoreManager;
import com.sun.messaging.jms.management.server.BrokerClusterInfo;

public class GetClusterHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public GetClusterHandler(AdminDataHandler parent) {
    super(parent);
    }


    public static int convertState(BrokerState state)
    {
        if (state == BrokerState.INITIALIZING) {
            return com.sun.messaging.jms.management.server.BrokerState.INITIALIZING;
        } else if (state == BrokerState.OPERATING) {
            return com.sun.messaging.jms.management.server.BrokerState.OPERATING;
        } else if (state == BrokerState.QUIESCE_STARTED) {
            return com.sun.messaging.jms.management.server.BrokerState.QUIESCE_STARTED;
        } else if (state == BrokerState.QUIESCE_COMPLETED) {
            return com.sun.messaging.jms.management.server.BrokerState.QUIESCE_COMPLETE;
        } else if (state == BrokerState.SHUTDOWN_STARTED) {
            return com.sun.messaging.jms.management.server.BrokerState.SHUTDOWN_STARTED;
        } else if (state == BrokerState.SHUTDOWN_COMPLETE) {
            return com.sun.messaging.jms.management.server.BrokerState.BROKER_DOWN;
        } else if (state == BrokerState.SHUTDOWN_FAILOVER) {
            return com.sun.messaging.jms.management.server.BrokerState.BROKER_DOWN;
        } else if (state == BrokerState.FAILOVER_PENDING) {
            return com.sun.messaging.jms.management.server.BrokerState.BROKER_DOWN;
        } else if (state == BrokerState.FAILOVER_STARTED) {
            return com.sun.messaging.jms.management.server.BrokerState.TAKEOVER_STARTED;
        } else if (state == BrokerState.FAILOVER_COMPLETE) {
            return com.sun.messaging.jms.management.server.BrokerState.TAKEOVER_COMPLETE;
        } else if (state == BrokerState.FAILOVER_FAILED) {
            return com.sun.messaging.jms.management.server.BrokerState.TAKEOVER_FAILED;
        } else if (state == BrokerState.FAILOVER_PROCESSED) {
            return com.sun.messaging.jms.management.server.BrokerState.TAKEOVER_COMPLETE;
        }
        return com.sun.messaging.jms.management.server.BrokerState.BROKER_DOWN;
    }

    public static Hashtable getBrokerClusterInfo(ClusteredBroker cb, Logger logger)  {
	Hashtable h = new Hashtable();

        if (Globals.getBDBREPEnabled()) {
            h.put(StoreManager.BDB_REPLICATION_ENABLED_PROP, "true");
        }
        if (Globals.isBDBStore()) {
            h.put(Globals.IMQ+".storemigratable", "true");
        }

        String brkid;
	if (cb.isBrokerIDGenerated())  {
        if (Globals.isBDBStore()) {
            try {
            brkid = cb.getNodeName();
            } catch (Exception e) {
            brkid = "";
            }
        } else {
            brkid = "";
        }
	} else  {
            brkid = cb.getBrokerName();
	}
        h.put(BrokerClusterInfo.ID, brkid);

    //String instName = cb.getInstanceName();
    //uncomment when ready to change
    //h.put(BrokerClusterInfo.INSTNAME, (instName == null ? "", instName));


        BrokerMQAddress bm = (BrokerMQAddress)cb.getBrokerURL();
        String address = bm.getHostAddressNPort();
        h.put(BrokerClusterInfo.ADDRESS, address);

	/*
	 * Substitute "Version" with BrokerClusterInfo.VERSION if/when
	 * that constant is added. It's not there now because we don't
	 * want to expose version just yet - because cb.getVersion()
	 * currently returns protocol version, not broker version.
	 * A new method needs to be added to ClusteredBroker that returns the 
	 * broker version.
	 */
        h.put("Version", String.valueOf(cb.getVersion()));

        if (cb instanceof HAClusteredBroker) {
            try {
                HAClusteredBroker hcb = (HAClusteredBroker)cb;
                h.put(BrokerClusterInfo.STATUS_TIMESTAMP, Long.valueOf(hcb.getHeartbeat()));
                String tk = hcb.getTakeoverBroker();
                if (tk != null) {
                    h.put(BrokerClusterInfo.TAKEOVER_BROKER_ID, tk);
                }
                int cnt = Globals.getStore().getMessageCount(brkid);
                h.put(BrokerClusterInfo.NUM_MSGS, Long.valueOf(cnt)); 
            } catch (Exception ex) {
            }
        }
        if (Globals.getDestinationList().isPartitionMode() &&
            Globals.getDestinationList().isPartitionMigratable()) {
            h.put(Globals.IMQ+".partitionmigratable", "true");
            if (cb.isLocalBroker()) {
                h.put(MessageType.JMQ_NUM_PARTITIONS, 
                    Integer.valueOf(Globals.getDestinationList().
                                    getNumPartitions()));
            }
        }
        try {
            h.put(BrokerClusterInfo.STATE, Integer.valueOf(convertState(cb.getState())));
        } catch (Exception ex) {
	    if (logger != null)  {
                logger.logStack(Logger.WARNING, ex.getMessage(), ex);
	    }
            h.put(BrokerClusterInfo.STATE,
		Integer.valueOf(com.sun.messaging.jms.management.server.BrokerState.BROKER_DOWN));
        }

	return (h);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con    The Connection the message came in on.
     * @param cmd_msg    The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
                       Hashtable cmd_props) {

        if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                            "Getting cluster info/state: " + cmd_props);
        }


        Vector v = new Vector();
        int status = Status.OK;
        String errMsg = null;

       /*
	* Populate vector 'v' with Hashtable objects; each containing broker
	* info.
	*/
	//Properties brokerProps = Globals.getConfig().toProperties();

	/*
	 * See com.sun.messaging.jms.management.BrokerClusterInfo class on how
	 * to setup the Hashtable with data to initialize the BrokerClusterInfo
	 * object.
	 */


    ClusterManager cm = Globals.getClusterManager();


    Iterator itr = cm.getKnownBrokers(true);

    while (itr.hasNext()) {
        ClusteredBroker cb = (ClusteredBroker)itr.next();
        Hashtable h = getBrokerClusterInfo(cb, logger);
        v.add(h);
    }

       // Send reply
       Packet reply = new Packet(con.useDirectBuffers());
       reply.setPacketType(PacketType.OBJECT_MESSAGE);
   
       setProperties(reply, MessageType.GET_CLUSTER_REPLY,
           status, errMsg);
   
       setBodyObject(reply, v);
       parent.sendReply(con, cmd_msg, reply);

       return true;
   }
}
