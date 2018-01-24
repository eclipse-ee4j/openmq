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
 * @(#)SessionHandler.java	1.19 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;



public class SessionHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;
  

    public SessionHandler() {
    }


    public Session createSession(int stype, String creator, IMQConnection con,
            boolean isIndemp)
        throws BrokerException
    {
        Session session = null;
        if (isIndemp) {
            session = Session.getSession(creator);
        }
        if (session == null) {
            session = Session.createSession(con.getConnectionUID(),
                                            creator, coreLifecycle);
            session.setAckType(stype);
            con.attachSession(session);
        }
        return session;
     }

     public void closeSession(SessionUID sessionID, IMQConnection con, boolean isIndemp)
        throws BrokerException
     {
         if (!isIndemp || Session.getSession(sessionID) != null) {
             assert con.getSession(sessionID) != null;

             Session.closeSession(sessionID);
             con.closeSession(sessionID);
         }
     }


    /**
     * Method to handle Session(add or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
        int status = Status.OK;
        String reason = null;
        Hashtable hash = new Hashtable(); // return props
        boolean isIndemp = msg.getIndempotent();

        try {
            Hashtable props = msg.getProperties();

            Session session = null;
            if (msg.getPacketType() == PacketType.CREATE_SESSION) {
               Integer  ack = (props == null ? null : 
                    (Integer)props.get("JMQAckMode"));
               // if we dont know, treat like client ack
               int stype = (ack == null ? Session.NONE
                      : ack.intValue());

               session = createSession(stype, msg.getSysMessageID().toString(),
                    con, isIndemp);
               hash.put("JMQSessionID", Long.valueOf(
                    session.getSessionUID().longValue()));

            } else {
                assert msg.getPacketType() == PacketType.DESTROY_SESSION;
                Long lsessionid = (Long)props.get("JMQSessionID");
                if (lsessionid == null) {
                    throw new BrokerException(
                        Globals.getBrokerResources().getString(
                             BrokerResources.X_INTERNAL_EXCEPTION,
                             "protocol error, no session"));
                }
                SessionUID sessionID = new SessionUID(
                       lsessionid.longValue());

                closeSession(sessionID, con, isIndemp);

            }
        } catch (Exception ex) {
            boolean log = false;
            reason = ex.getMessage();
            if (ex instanceof BrokerException) {
                status = ((BrokerException)ex).getStatusCode();
                log = false;
            } else {
                status = Status.ERROR;
                log = true;
            }

            if (log) {
                logger.logStack(Logger.INFO,
                   Globals.getBrokerResources().getString(
                      BrokerResources.X_INTERNAL_EXCEPTION,
                        " session "),ex);
            } else {
                logger.log(Logger.INFO, ex.getMessage());
            }
        }
            
        hash.put("JMQStatus", Integer.valueOf(status));
        if (reason != null)
            hash.put("JMQReason", reason);

        if (msg.getSendAcknowledge()) {
            Packet pkt = new Packet(con.useDirectBuffers());
            pkt.setConsumerID(msg.getConsumerID()); // correlation ID
            pkt.setPacketType(msg.getPacketType()+1);
            pkt.setProperties(hash);
            con.sendControlMessage(pkt);
        }

        return true;


    }


}
