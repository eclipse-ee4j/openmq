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
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Set;
import java.util.Hashtable;
import java.util.Properties;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterManager;
import com.sun.messaging.jmq.io.MQAddress;

public class UpdateClusterBrokerListHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public UpdateClusterBrokerListHandler(AdminDataHandler parent) {
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
        String msg = null;

        if (DEBUG) {
            logger.log(Logger.INFO, this.getClass().getName()+": "+cmd_props);
        }

        if (Globals.getHAEnabled()) {
            status = Status.ERROR;
            msg =  rb.getKString(rb.E_OP_NOT_APPLY_TO_HA_BROKER, 
                   MessageType.getString(MessageType.UPDATE_CLUSTER_BROKERLIST));
            logger.log(Logger.ERROR, msg);

        } else if (!Globals.isJMSRAManagedBroker()) {
            status = Status.ERROR;
            msg =  rb.getKString(rb.E_BROKER_NOT_JMSRA_MANAGED_IGNORE_OP, 
                   MessageType.getString(MessageType.UPDATE_CLUSTER_BROKERLIST));
            logger.log(Logger.ERROR, msg);
            msg = "BAD REQUEST";

        } else  {
             try {
                 ClusterManager cm = Globals.getClusterManager();
                 MQAddress self = cm.getMQAddress();
                 String brokerlist = (String)cmd_props.get(MessageType.JMQ_CLUSTER_BROKERLIST);
                 Set brokers = cm.parseBrokerList(brokerlist);
                 MQAddress master = (cm.getMasterBroker() == null ? 
                                     null:cm.getMasterBroker().getBrokerURL());
                 logger.log(logger.INFO, rb.getKString(rb.I_UPDATE_BROKERLIST, 
                     self+(master == null ?"]":"("+cm.CONFIG_SERVER+"="+master+")"),
                     "["+brokerlist+"]"));
                 if (master != null && !brokers.contains(master)) {
                     msg = rb.getKString(rb.X_REMOVE_MASTERBROKER_NOT_ALLOWED,
                               master.toString(), brokers.toString()+"["+brokerlist+"]");
                     throw new BrokerException(msg);
                 }
                 if (!brokers.contains(self)) {
                     brokerlist = "";
                 }
                 Properties prop = new Properties(); 
                 prop.put(cm.AUTOCONNECT_PROPERTY, brokerlist);
                 BrokerConfig bcfg = Globals.getConfig();
                 bcfg.updateProperties(prop, true);
             } catch (PropertyUpdateException e) {
                 status = Status.BAD_REQUEST;
                 msg = e.getMessage();
                 logger.log(Logger.WARNING, msg);
             } catch (Exception e) {
                 status = Status.ERROR;
                 msg = e.toString();
                 logger.log(Logger.WARNING, msg);
             }
         }

         // Send reply
	     Packet reply = new Packet(con.useDirectBuffers());
	     reply.setPacketType(PacketType.OBJECT_MESSAGE);

	     setProperties(reply, MessageType.UPDATE_CLUSTER_BROKERLIST_REPLY, status, msg);
         parent.sendReply(con, cmd_msg, reply);

         return true;
    }
}
