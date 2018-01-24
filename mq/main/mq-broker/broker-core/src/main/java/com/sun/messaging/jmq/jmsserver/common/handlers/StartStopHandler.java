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
 * @(#)StartStopHandler.java	1.34 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

/**
 * Handler class which deals with starting/stoping the delivery of 
 * messages to a specific connection
 */
public class StartStopHandler extends PacketHandler 
{

    Hashtable hash = new Hashtable();

    public StartStopHandler() {
        hash.put("JMQStatus", Integer.valueOf(Status.OK));

    }

    /**
     * Method to handle Start and Stop messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            throw new RuntimeException("Can not load props", ex);
        }
        Long lsid = (props == null ? null :
             (Long)props.get("JMQSessionID"));

        SessionUID suid = (lsid == null ? null :
              new SessionUID(lsid.longValue()));

        int status = Status.OK;
        String reason = null;

        switch (msg.getPacketType()) {
            case PacketType.START:
                if (suid != null) {
                    boolean bad = false;
                    Session s= Session.getSession(suid);
                    if (s != null && 
                        !s.getConnectionUID().equals(con.getConnectionUID())) {
                        bad = true;
                    }
                    // OK .. the client should never be sending us
                    // a bad session ID, but in reconnect it sometimes
                    // does
                    // handle it gracefully if the client does the 
                    // wrong thing
                    if (s == null) {
                       status = Status.ERROR;
                       String[] args = { ""+suid, "START-SESSION", con.toString() };
                       reason = Globals.getBrokerResources().getKString(
                                BrokerResources.W_RECEIVED_UNKNOWN_SESSIONID, args);
                       logger.log(Logger.WARNING, reason);
                    } else if (bad) {
                       status = Status.ERROR;
                       String[] args = { ""+suid, "START-SESSION", con.toString(), 
                                         s.getConnectionUID().toString() };
                       reason = Globals.getBrokerResources().getKString(
                                BrokerResources.W_RECEIVED_BAD_SESSIONID, args);
                       logger.log(Logger.WARNING, reason);
                    } else {
                        s.resume("START_STOP");
                    }
                } else {
                    con.startConnection();
                }
                break;
            case PacketType.STOP:
                if (suid != null) {
                    boolean bad = false;
                    Session s= Session.getSession(suid);
                    if (s != null && 
                        !s.getConnectionUID().equals(con.getConnectionUID())) {
                        bad = true;
                    }
                    // OK .. the client should never be sending us
                    // a bad session ID, but in reconnect it sometimes
                    // does
                    // handle it gracefully if the client does the 
                    // wrong thing
                    if (s == null) {
                       status = Status.ERROR;
                       String[] args = { ""+suid, "STOP-SESSION", con.toString() };
                       reason = Globals.getBrokerResources().getKString(
                                BrokerResources.W_RECEIVED_UNKNOWN_SESSIONID, args);
                       logger.log(Logger.WARNING, reason);
                    } else if (bad) {
                       status = Status.ERROR;
                       String[] args = { ""+suid, "STOP-SESSION", con.toString(), 
                                         s.getConnectionUID().toString() };
                       reason = Globals.getBrokerResources().getKString(
                                BrokerResources.W_RECEIVED_BAD_SESSIONID, args);
                       logger.log(Logger.WARNING, reason);
                    } else {
                         s.pause("START_STOP");
                    }
                } else {
                    con.stopConnection();
                }
                Packet pkt = new Packet(con.useDirectBuffers());
                pkt.setPacketType(PacketType.STOP_REPLY);
                pkt.setConsumerID(msg.getConsumerID());
                if (((IMQBasicConnection)con).getDumpPacket() ||
                      ((IMQBasicConnection)con).getDumpOutPacket())
                    hash.put("JMQReqID", msg.getSysMessageID().toString());

                hash.put("JMQStatus", Integer.valueOf(status));
                if (reason != null)
                    hash.put("JMQReason", reason);

                pkt.setProperties(hash);
                con.sendControlMessage(pkt);
                break;
        }
        return true;
    }

}
