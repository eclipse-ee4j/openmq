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
 * @(#)GetServicesHandler.java	1.29 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.io.IOException;
import java.io.*;
import java.util.Vector;
import java.util.List;
import java.util.Iterator;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.admin.ServiceInfo;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.MetricManager;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;

public class GetServicesHandler extends AdminCmdHandler
{

    private static boolean DEBUG = getDEBUG();

    private static final String SERVICE_PREFIX = Globals.IMQ + ".";
    private static BrokerConfig props = Globals.getConfig();

    public GetServicesHandler(AdminDataHandler parent) {
	super(parent);
    }

    public static ServiceInfo getServiceInfo(String name) {

        ServiceManager sm = Globals.getServiceManager();
        ConnectionManager cm = Globals.getConnectionManager();
	MetricManager mm = Globals.getMetricManager();

        /* XXX REVISIT dipol 10/17/00 we should probably put this logic
         * into the ServiceManager so knowledge of property names
         * is encapsulated there.
         */
        String proto = props.getProperty(SERVICE_PREFIX +
            name + ".protocoltype");

        // Fill in admin service info object
	ServiceInfo si = new com.sun.messaging.jmq.util.admin.ServiceInfo();
	si.name = name;
	si.protocol = proto;

        // strange kludge here ...
        // if protocol is tcp or tls, it defaults to 0
        int default_value=-1;
	if (si.protocol != null)  {
            if (si.protocol.equals("tcp") || si.protocol.equals("tls"))
                default_value = 0;
	}

        si.port = props.getIntProperty(SERVICE_PREFIX +
            name + "." + proto + ".port", default_value);

        if (si.port == 0) {
            si.dynamicPort = true;
        } else {
            si.dynamicPort = false;
        }

        si.minThreads = props.getIntProperty(SERVICE_PREFIX + name + ".min_threads");
        si.maxThreads = props.getIntProperty(SERVICE_PREFIX + name + ".max_threads");

        si.type = sm.getServiceType(name);

        Service service = sm.getService(name);
        
        if (service != null) {
            si.nConnections = cm.getNumConnections(service);
            si.state = service.getState();

            if (service instanceof IMQService) {
                IMQService ss = (IMQService)service;
                si.currentThreads = ss.getActiveThreadpool();
                si.minThreads = ss.getMinThreadpool();
                si.maxThreads = ss.getMaxThreadpool();
		// If we were configured to use dynamic ports, get the
		// port number that is acutally being used
		if (si.port == 0 && ss.getProtocol() != null) {
		    si.port = ss.getProtocol().getLocalPort();
		}
            }
	    if (mm != null) {
	        si.metrics = mm.getMetricCounters(name);
	    } else {
		si.metrics = null;
            }
        } else {
            // Service is not intitialized
            si.state = ServiceState.UNKNOWN;
        }

        return si;
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
                            "Getting services " + cmd_props);
        }

        int status = Status.OK;
	String errMsg = null;

	String service = (String)cmd_props.get(MessageType.JMQ_SERVICE_NAME);

	ServiceManager sm = Globals.getServiceManager();

        // Get the list of service names from the ServiceManager
	List serviceNames = sm.getAllServiceNames();

        // Iterate through services
	Vector v = new Vector();
	Iterator iter = serviceNames.iterator(); 
	while (iter.hasNext()) {
            String name = (String)iter.next();

            if (service == null) {
	        ServiceInfo si = getServiceInfo(name);
	        v.add(si);
            } else if (service.equals(name)) {
	        ServiceInfo si = getServiceInfo(name);
	        v.add(si);
                break;
            }
	}

        if (service != null && v.size() == 0) {
            // Specified service did not exist
            status = Status.NOT_FOUND;
	    errMsg = rb.getString(rb.X_NO_SUCH_SERVICE, service);
        }

        // Write reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

	setProperties(reply, MessageType.GET_SERVICES_REPLY, status, errMsg);

	setBodyObject(reply, v);

	parent.sendReply(con, cmd_msg, reply);
    return true;
    }
}
