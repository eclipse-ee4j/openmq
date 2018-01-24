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
 * @(#)UpdateServiceHandler.java	1.14 07/12/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;

public class UpdateServiceHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public UpdateServiceHandler(AdminDataHandler parent) {
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
				       Hashtable cmd_props) 
    {

        if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                cmd_props);
        }

	ServiceInfo info = (ServiceInfo)getBodyObject(cmd_msg);
        int status = Status.OK;
        String errMsg = null;

	ServiceManager sm = Globals.getServiceManager();
        Service svc = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService(); 
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg =  rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
	} else  {
        if (info.name == null || ((svc= sm.getService(info.name)) == null)) {
            status = Status.ERROR;
            errMsg = rb.getString( rb.X_NO_SUCH_SERVICE, 
                (info.name == null ? "<null>" : info.name));
        }
        }

        // OK .. set the service information
        if (status == Status.OK) {
            if (! (svc instanceof IMQService)) {
                status = Status.ERROR;
                errMsg = "Internal Error: can updated non-standard Service";
            } else {
            // XXX - really we want to do this through properties, I need
            // to repair this by fcs
                try {
                    IMQService stsvc = (IMQService)svc;
                    int port = -1;
                    int min = -1;
                    int max = -1;
                    if (info.isModified(info.PORT)) {
                        port = info.port;
                    }
                    if (info.isModified(info.MIN_THREADS)) {
                        min = info.minThreads;
                    } 
                    if (info.isModified(info.MAX_THREADS)) {
                        max = info.maxThreads;
                    }
                    if (port != -1 || min !=-1 || max != -1) {
                        stsvc.updateService(port, min, max);
                    } else {
                        status = Status.ERROR;
                        errMsg = rb.getString( rb.X_NO_SERVICE_PROPS_SET, 
                            info.name);
                    }
                } catch (Exception ex) {
                    logger.logStack(Logger.WARNING, rb.W_ERROR_UPDATING_SERVICE,
			svc.getName(), ex);
                    status = Status.ERROR;
		    errMsg = getMessageFromException(ex);
                }
            }
        }


	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply,
	    MessageType.UPDATE_SERVICE_REPLY, status, errMsg);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
