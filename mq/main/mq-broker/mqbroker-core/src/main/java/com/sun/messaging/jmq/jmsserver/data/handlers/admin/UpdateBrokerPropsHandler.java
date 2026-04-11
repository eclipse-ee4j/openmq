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

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Collections;
import java.util.Hashtable;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.config.*;

@SuppressWarnings("JdkObsolete")
public class UpdateBrokerPropsHandler extends AdminCmdHandler {
    private static boolean DEBUG = getDEBUG();

    /**
     * Properties that an authenticated admin client is not allowed to set
     * over the wire. {@code imq.cluster.url} is denied because the broker
     * passes the value to {@link java.net.URL#openStream()} on startup, which
     * an attacker could abuse for arbitrary file read or SSRF (CVE-2026-24457).
     * These properties must be configured locally in {@code config.properties}
     * by an operator with filesystem access to the broker host.
     */
    static final Set<String> REMOTE_UPDATE_DENYLIST;
    static {
        Set<String> denied = new TreeSet<>();
        denied.add("imq.cluster.url");
        REMOTE_UPDATE_DENYLIST = Collections.unmodifiableSet(denied);
    }

    public UpdateBrokerPropsHandler(AdminDataHandler parent) {
        super(parent);
    }

    /**
     * @return the first denied property name found in {@code props}, or
     *     {@code null} if every property is permitted for remote update.
     */
    static String findDeniedProperty(Properties props) {
        if (props == null) {
            return null;
        }
        for (String name : props.stringPropertyNames()) {
            if (REMOTE_UPDATE_DENYLIST.contains(name)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con The Connection the message came in on.
     * @param cmd_msg The administration message
     * @param cmd_props The properties from the administration message
     */
    @Override
    public boolean handle(IMQConnection con, Packet cmd_msg, Hashtable cmd_props) {

        int status = Status.OK;
        String msg = null;

        if (DEBUG) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " + cmd_props);
        }

        HAMonitorService hamonitor = Globals.getHAMonitorService();
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            msg = rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + msg);
        } else {

            // Get properties we are to update from message body
            Properties p = (Properties) getBodyObject(cmd_msg);
            logger.log(Logger.INFO, rb.I_UPDATE_BROKER_PROPS, "[" + p + "]");

            String denied = findDeniedProperty(p);
            if (denied != null) {
                status = Status.BAD_REQUEST;
                msg = "Property '" + denied + "' cannot be modified via the admin protocol; "
                        + "set it locally in config.properties (CVE-2026-24457)";
                logger.log(Logger.WARNING, msg);

                Packet reply = new Packet(con.useDirectBuffers());
                reply.setPacketType(PacketType.OBJECT_MESSAGE);
                setProperties(reply, MessageType.UPDATE_BROKER_PROPS_REPLY, status, msg);
                parent.sendReply(con, cmd_msg, reply);
                return true;
            }

            // Update the broker configuration
            BrokerConfig bcfg = Globals.getConfig();
            try {
                bcfg.updateProperties(p, true);
            } catch (PropertyUpdateException e) {
                status = Status.BAD_REQUEST;
                msg = e.getMessage();
                logger.log(Logger.WARNING, msg);
            } catch (IOException e) {
                status = Status.ERROR;
                msg = e.toString();
                logger.log(Logger.WARNING, msg);
            }

        }

        // Send reply
        Packet reply = new Packet(con.useDirectBuffers());
        reply.setPacketType(PacketType.OBJECT_MESSAGE);

        setProperties(reply, MessageType.UPDATE_BROKER_PROPS_REPLY, status, msg);

        parent.sendReply(con, cmd_msg, reply);
        return true;
    }
}
