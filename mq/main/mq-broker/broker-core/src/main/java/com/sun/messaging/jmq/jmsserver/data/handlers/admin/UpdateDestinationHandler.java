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
 * @(#)UpdateDestinationHandler.java	1.25 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.jmsserver.core.Destination;

public class UpdateDestinationHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public UpdateDestinationHandler(AdminDataHandler parent) {
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

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                cmd_props);
        }

	//String destination = (String)cmd_props.get(MessageType.JMQ_DESTINATION);

	DestinationInfo info = (DestinationInfo)getBodyObject(cmd_msg);

    int status = Status.OK;
    String errMsg = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {

    try {
        Destination[] ds = DL.getDestination(null, info.name, DestType.isQueue(info.type));
        Destination d = ds[0]; //PART
	if (d == null)  {
	   errMsg= rb.getString( rb.X_DESTINATION_NOT_FOUND,
					  info.name);
	   status = Status.NOT_FOUND;
	} else  {
            if (info.isModified(info.MAX_MESSAGES)) {
                int maxMessages = info.maxMessages;
                d.setCapacity(maxMessages);
            }
            if (info.isModified(info.MAX_MESSAGE_SIZE)) {
                SizeString maxSize = new SizeString();
                maxSize.setBytes(info.maxMessageSize);
                d.setMaxByteSize(maxSize);
            }
            if (info.isModified(info.MAX_MESSAGE_BYTES)) {
                SizeString maxBytes = new SizeString();
                maxBytes.setBytes(info.maxMessageBytes);
                d.setByteCapacity(maxBytes);
            }
            if (info.isModified(info.DEST_SCOPE)) {
                int scope = info.destScope;
                d.setScope(scope);
            
            }
            if (info.isModified(info.DEST_LIMIT)) {
                int destlimit = info.destLimitBehavior;
                d.setLimitBehavior(destlimit);
            }
            if (info.isModified(info.DEST_PREFETCH)) {
                int prefetch = info.maxPrefetch;
                d.setMaxPrefetch(prefetch);
            }
            if (info.isModified(info.DEST_CDP)) {
                int clusterdeliverypolicy = info.destCDP;
                d.setClusterDeliveryPolicy(clusterdeliverypolicy);
            }
            if (info.isModified(info.MAX_ACTIVE_CONSUMERS)) {
                int maxcons = info.maxActiveConsumers;
                d.setMaxActiveConsumers(maxcons);
            }
            if (info.isModified(info.MAX_FAILOVER_CONSUMERS)) { //PART
                int maxcons = info.maxFailoverConsumers;
                d.setMaxFailoverConsumers(maxcons);
            }
            if (info.isModified(info.MAX_PRODUCERS)) {
                int maxproducers = info.maxProducers;
                d.setMaxProducers(maxproducers);
            }
            if (info.isModified(info.MAX_SHARED_CONSUMERS)) {
                int maxsharedcons = info.maxNumSharedConsumers;
                d.setMaxSharedConsumers(maxsharedcons);
            }
            if (info.isModified(info.SHARE_FLOW_LIMIT)) {
                int sflowlimit = info.sharedConsumerFlowLimit;
                d.setSharedFlowLimit(sflowlimit);
            }
            if (info.isModified(info.USE_DMQ)) {
                boolean dmq = info.useDMQ;
                d.setUseDMQ(dmq);
            }
            if (info.isModified(info.VALIDATE_XML_SCHEMA_ENABLED)) {
                d.setValidateXMLSchemaEnabled(info.validateXMLSchemaEnabled);
            }
            if (info.isModified(info.XML_SCHEMA_URI_LIST)) {
                d.setXMLSchemaUriList(info.XMLSchemaUriList);
            }
            if (info.isModified(info.RELOAD_XML_SCHEMA_ON_FAILURE)) {
                d.setReloadXMLSchemaOnFailure(info.reloadXMLSchemaOnFailure);
            }
            d.update();
	}
        
    } catch (Exception ex) {
        errMsg = getMessageFromException(ex);
        status = Status.ERROR;
    }
        }


	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply,
	    MessageType.UPDATE_DESTINATION_REPLY, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
