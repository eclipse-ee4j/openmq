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

import java.util.Hashtable;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

@SuppressWarnings("JdkObsolete")
public class UnquiesceHandler extends AdminCmdHandler {
    private static boolean DEBUG = getDEBUG();

    public UnquiesceHandler(AdminDataHandler parent) {
        super(parent);
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

        if (DEBUG) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " + "Unquiescing broker: " + cmd_props);
        }

        int status = Status.OK;
        String errMsg = null;

        HAMonitorService hamonitor = Globals.getHAMonitorService();
        if (hamonitor != null && hamonitor.inTakeover()) {
            status = Status.ERROR;
            errMsg = rb.getString(rb.E_CANNOT_PROCEED_TAKEOVER_IN_PROCESS);

            logger.log(Logger.ERROR, this.getClass().getName() + ": " + errMsg);
        } else {
            try {
                BrokerStateHandler bsh = Globals.getBrokerStateHandler();
                bsh.stopQuiesce();
            } catch (Exception ex) {
                errMsg = ex.toString();
                status = Status.ERROR;
            }
        }

        // Send reply
        Packet reply = new Packet(con.useDirectBuffers());
        reply.setPacketType(PacketType.OBJECT_MESSAGE);

        setProperties(reply, MessageType.UNQUIESCE_BROKER_REPLY, status, errMsg);

        parent.sendReply(con, cmd_msg, reply);

        return true;
    }

}
