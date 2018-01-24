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
 * @(#)ShutdownHandler.java	1.37 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.audit.api.MQAuditSession;
import com.sun.messaging.jmq.jmsserver.management.util.ConnectionUtil;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.admin.ConnectionInfo;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

public class ShutdownHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public ShutdownHandler(AdminDataHandler parent) {
    	super(parent);
    }

    /**
     * Handle the incoming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg, Hashtable cmd_props) {

	if (DEBUG) logger.log(Logger.DEBUG, this.getClass().getName() + ": " + "Shutting down broker: " + cmd_props);

	boolean shouldRestart = true;
    Boolean noFailover;
    Integer time = null;
    
    // extract the properties from the administration message
    Boolean kill = (Boolean)cmd_props.get(MessageType.JMQ_KILL);
    noFailover = (Boolean)cmd_props.get(MessageType.JMQ_NO_FAILOVER);
    boolean failover = (noFailover == null ? true : !(noFailover.booleanValue()));
    time = (Integer)cmd_props.get(MessageType.JMQ_TIME);
    Boolean restart = (Boolean)cmd_props.get(MessageType.JMQ_RESTART);
    
    // is this a restart?
    shouldRestart =  (restart == null ? false : restart.booleanValue());
    if (shouldRestart) {
        failover = false;
    }

	// Prepare reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);
	
	// not allowed to shutdown an in-process broker that has direct connections
    if (Broker.isInProcess() && !shouldRestart && hasDirectConnections()) {
    	//TODO COrrect this message
    	String error = rb.getString(BrokerResources.E_CANNOT_SHUTDOWN_IN_PROCESS);
    	setProperties(reply, MessageType.SHUTDOWN_REPLY, Status.ERROR, error);
    	parent.sendReply(con, cmd_msg, reply);
    	return true;  
    }

	// not allowed to restart an in-process broker
    if (Broker.isInProcess() && shouldRestart) {
    	String error = rb.getString(BrokerResources.E_CANNOT_RESTART_IN_PROCESS);
    	setProperties(reply, MessageType.SHUTDOWN_REPLY, Status.ERROR, error);
    	parent.sendReply(con, cmd_msg, reply);
    	return true;      
    }
    
    // Check if this is a JMQKill message: this is used for testing and triggers an unsafe exit
    // this could be done before the previous check if we wanted 
    if (kill != null && kill.booleanValue()) {
        Broker.getBroker().removeBrokerShutdownHook();
        System.exit(1);
    }
    
    // audit logging for broker restart/shutdown
    Globals.getAuditSession().brokerOperation(
		con.getUserName(), con.remoteHostString(),
		shouldRestart ? MQAuditSession.BROKER_RESTART :	MQAuditSession.BROKER_SHUTDOWN);

    // now begin the shutdown sequence
	try {
        // stop taking new requests
        Globals.getServiceManager().stopNewConnections(ServiceType.NORMAL);

        if (time == null || time.intValue() == 0)
        	Globals.getServiceManager().stopNewConnections(ServiceType.ADMIN);

	} catch (Exception ex)  {
		logger.logStack(Logger.WARNING, BrokerResources.E_INTERNAL_BROKER_ERROR, "unable to shutdown", ex);
    } finally {
    	// send a reply to the client that we received the request
        // currently we return OK even if we had an error during the shutdown
    	setProperties(reply, MessageType.SHUTDOWN_REPLY, Status.OK, null);
	    parent.sendReply(con, cmd_msg, reply);
	    if (con instanceof IMQBasicConnection)  {
	        IMQBasicConnection ipCon = (IMQBasicConnection)con;
            ipCon.flushControl(1000);
	    }

 	    BrokerStateHandler bsh = Globals.getBrokerStateHandler();
 	    if (time == null || time.intValue() == 0) {
        	 // stop new connections
        	 // set the flag to notify everyone we are shutting down
        	 BrokerStateHandler.setShuttingDown(true);
        	 bsh.prepareShutdown(failover, false);
         }

         //if we aren't doing an unsafe exit, give us time to complete any operations
         waitForHandlersToComplete(20);

         // logging
         if (shouldRestart) {
        	 logger.log(Logger.INFO, BrokerResources.I_ADMIN_RESTART_REQUEST);
         } else {
        	 logger.log(Logger.INFO, BrokerResources.I_ADMIN_SHUTDOWN_REQUEST);
         }
         
         // shutdown the broker 
         bsh.initiateShutdown("admin", (time == null ? 0 : time.longValue())* 1000, failover, (shouldRestart ? BrokerStateHandler.getRestartCode() : 0), true);
                   
        }
        return true;
    }
    
    private boolean hasDirectConnections() {
    	// Please keep this consistent with com.sun.messaging.jmq.jmsserver.management.mbeans.BrokerConfig.hasDirectConnections()

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

}
