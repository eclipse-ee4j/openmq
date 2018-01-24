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
 * @(#)CreateDestinationHandler.java	1.47 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.ConflictException;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.memory.MemoryGlobals;
import com.sun.messaging.jmq.jmsserver.audit.api.MQAuditSession;

public class CreateDestinationHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public CreateDestinationHandler(AdminDataHandler parent) {
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
	DestinationInfo info;

	info = (DestinationInfo)getBodyObject(cmd_msg);

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                "Creating destination: " + cmd_props + ": " + info.toString());
        }

	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

        int status = Status.OK;
        String errMsg = null;

        // Default attributes of the destination
        int type = DestType.DEST_TYPE_QUEUE | DestType.DEST_FLAVOR_SINGLE; 
        int maxMessages = -1;
        SizeString maxMessageBytes = null;
        SizeString maxMessageSize = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else if (MemoryGlobals.getMEM_DISALLOW_CREATE_DEST()) {
            status = Status.ERROR;
            errMsg = rb.W_LOW_MEM_REJECT_DEST;
 
        } else if (info.isModified(DestinationInfo.NAME)) {
            if (info.isModified(DestinationInfo.TYPE)) {
                type = info.type;
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGES)) {
                maxMessages = info.maxMessages;
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGE_BYTES)) {
                maxMessageBytes = new SizeString();
                maxMessageBytes.setBytes(info.maxMessageBytes);
            }
            if (info.isModified(DestinationInfo.MAX_MESSAGE_SIZE)) {
                maxMessageSize = new SizeString();
                maxMessageSize.setBytes(info.maxMessageSize);
            }

        } else {
            status = Status.ERROR;
            errMsg = rb.X_NO_DEST_NAME_SET;
        }
        // 
        //XXX create destination
        if (status == Status.OK) {

            if (DestType.destNameIsInternal(info.name)) {
                status = Status.ERROR;
                errMsg =  rb.getKString( rb.X_CANNOT_CREATE_INTERNAL_DEST, 
                            info.name,
			    DestType.INTERNAL_DEST_PREFIX);
	    } else  {
                if (isValidDestinationName(info.name)) {

                    try {
                        DL.createDestination(null, info.name, type);
                    } catch (Exception ex) {
                        status = Status.ERROR;
                        errMsg =  rb.getKString( rb.X_CREATE_DEST_EXCEPTION, 
                            info.name, getMessageFromException(ex));
                       if (ex instanceof ConflictException)
                           logger.log(Logger.INFO, errMsg, ex);
                       else
                           logger.logStack(Logger.INFO, errMsg, ex);
                    }
                } else {
                    status = Status.ERROR;
                    errMsg =  rb.getKString( rb.X_DEST_NAME_INVALID, 
                            info.name);
                }
	    }
        }

        if (status == Status.OK) {
            try {

                Destination[] ds = DL.getDestination(null, info.name, DestType.isQueue(type));
                Destination d = ds[0];

                d.setCapacity(maxMessages);
                d.setByteCapacity(maxMessageBytes);
                d.setMaxByteSize(maxMessageSize);
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
                if (info.isModified(info.MAX_PRODUCERS)) {
                    int maxp = info.maxProducers;
                    d.setMaxProducers(maxp);
                }
                if (info.isModified(info.MAX_FAILOVER_CONSUMERS)) {
                    int maxcons = info.maxFailoverConsumers;
                    d.setMaxFailoverConsumers(maxcons);
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
              
                // audit logging for create destination
                Globals.getAuditSession().destinationOperation(
                		con.getUserName(), con.remoteHostString(),MQAuditSession.CREATE_DESTINATION,
                		d.isQueue()?MQAuditSession.QUEUE:MQAuditSession.TOPIC,
						d.getDestinationName());
            } catch (Exception ex) {

                // remove the destination
                try {
                    DestinationUID duid = DestinationUID.getUID(
                        info.name, DestType.isQueue(type));
                    DL.removeDestination(null, duid, false, ex.toString());
                } catch (Exception ex1) {
                    // if we cant destroy .. its ok .. ignore the exception
                }

                status = Status.ERROR;
                errMsg = rb.getString( rb.X_UPDATE_DEST_EXCEPTION, 
                            info.name, getMessageFromException(ex));

                logger.logStack(Logger.WARNING, errMsg, ex);

            }
        }

	// Send reply
	setProperties(reply,
	    MessageType.CREATE_DESTINATION_REPLY, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
        return true;
    }



    /**
     * Validates a <code>Destination</code> name.
     *
     * @param name The <code>Destination</code> name.
     *
     * @return <code>true</code> if the name is valid;
     *         <code>false</code> if the name is invalid.
     */
    public static boolean isValidDestinationName(String name) {
        //Invalid if name is null.
        if (name == null) {
            return false;
        }
        //Verify identifier start character and part
        char[] namechars = name.toCharArray();
        if (namechars.length <1) return false;
        if (Character.isJavaIdentifierStart(namechars[0])) {
            for (int i = 1; i<namechars.length; i++) {
                if (!Character.isJavaIdentifierPart(namechars[i]) && namechars[i] != '.') {
                    //Invalid if body characters are not valid using isJavaIdentifierPart().
                    return false;
                }
            }
        } else {
            //Invalid if first character is not valid using isJavaIdentifierStart().
            return false;
        }
        return true;
    }
}
