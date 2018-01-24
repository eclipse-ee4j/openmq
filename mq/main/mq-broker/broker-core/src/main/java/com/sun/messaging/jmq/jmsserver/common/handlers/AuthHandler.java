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
 * @(#)AuthHandler.java	1.45 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.security.AccessControlException;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.jmsserver.management.agent.Agent;

/**
 * Handler class which deals with handling authentication messages
 */
public class AuthHandler extends PacketHandler 
{

    //private ConnectionManager connectionList;

    private Logger logger = Globals.getLogger();

    public AuthHandler(ConnectionManager list)
    { 
        //connectionList = list;
    }

    /**
     * Method to handle Authentication messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
        byte[] resp = null;

        ByteBuffer bbuf = msg.getMessageBodyByteBuffer();
        int size = bbuf.remaining();
        resp = new byte[size];
        bbuf.get(resp);

        String reason = null;

        AccessController ac = con.getAccessController();
        boolean isIndemp = msg.getIndempotent();

        byte[] req = null;
        int status = Status.ERROR;
        String username = null;
        if (con.isAuthenticated()) {
            if (!isIndemp) { // already authenticated
                reason = "already authenticated";
                logger.log(Logger.WARNING,"Received unexpected authentication "
                     + con.getRemoteConnectionString() + ":" +  
                       con.getConnectionUID());
                status = Status.ERROR;
            } else {
                status = Status.OK;
            }
            resp = null;
        } else if (!con.setConnectionState(Connection.STATE_AUTH_RESPONSED)) {
            reason = "bad connection state";
            status = Status.UNAVAILABLE;
            resp = null;
        }
        if (resp != null) {
            try {
            req = ac.handleResponse(resp, msg.getSequence());
            status = Status.OK;

            // audit logging for successful authentication
            Globals.getAuditSession().authentication(con.getUserName(),	con.remoteHostString(),	true);

            if (req == null) {
                IMQService s = (IMQService)con.getService();
                String stype = ServiceType.getServiceTypeString(s.getServiceType());
                try {
                    AuthCacheData acd = s.getAuthCacheData();
                    acd.setCacheData(ac.getCacheData());
                    ac.checkConnectionPermission(s.getName(), stype);

                    // audit logging for connection authorization
                    Globals.getAuditSession().connectionAuth(con.getUserName(),	con.remoteHostString(),	stype, s.getName(), true);

                } catch (AccessControlException e) {
                    reason = "Forbidden";
                    status = Status.FORBIDDEN;
                    ac.logout();
                    logger.log(Logger.WARNING, 
                    		Globals.getBrokerResources().getKString(
                    				BrokerResources.W_SERVICE_ACCESS_DENIED,
                    				s.getName(), stype)+ " - " + e.getMessage(), e);

                    // audit logging for authentication failure
                    Globals.getAuditSession().connectionAuth(con.getUserName(), con.remoteHostString(),	stype, s.getName(), false);

                    username = con.getUserName();
                }
            }

            } catch (FailedLoginException e) {
                //IMQService s = (IMQService)con.getService();

                Globals.getAuditSession().authentication(e.getUser(),con.remoteHostString(), false);

                username = e.getUser();

                status = Status.INVALID_LOGIN;
                reason = e.getMessage();
                logger.log(Logger.WARNING, BrokerResources.W_LOGIN_FAILED, e);
            } catch (OutOfMemoryError err) {
                // if we get an out of memory error, throw it
                // up so that memory is freed and the message is
                // re-processed
                throw err;
            } catch (Throwable w) {
            status = Status.FORBIDDEN;
            reason = w.getMessage();
            logger.log(Logger.ERROR, w.getMessage(), w);
            }
        }

        // XXX - for now simple returns granted authenticate reply
        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID());
        Hashtable hash = new Hashtable();

        if (reason != null)
            hash.put("JMQReason", reason);

        if (resp == null) {
            pkt.setPacketType(PacketType.AUTHENTICATE_REPLY);
            hash.put("JMQStatus", Integer.valueOf(status));
            pkt.setProperties(hash);

        } else {

          if (req != null) {
              if (!con.setConnectionState(Connection.STATE_AUTH_REQUESTED)) {
                  status = Status.UNAVAILABLE; 
                  req = null;
              }
          }

          if (req == null) {

            if (status == Status.OK) {
                if (!con.setConnectionState(Connection.STATE_AUTHENTICATED)) {
                status = Status.UNAVAILABLE;               
                }
            }
            pkt.setPacketType(PacketType.AUTHENTICATE_REPLY);
            hash.put("JMQStatus", Integer.valueOf(status));
            if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
                hash.put("JMQReqID", msg.getSysMessageID().toString());
            pkt.setProperties(hash);

          } else {

            pkt.setPacketType(PacketType.AUTHENTICATE_REQUEST);
            hash.put("JMQAuthType", ac.getAuthType());
            hash.put("JMQChallenge", Boolean.valueOf(false));
            if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
                hash.put("JMQReqID", msg.getSysMessageID().toString());
            pkt.setProperties(hash);
            pkt.setMessageBody(req);
          }

        }


        con.sendControlMessage(pkt);

        if (status != Status.OK) {
            IMQService s = (IMQService)con.getService();
	    Agent agent = Globals.getAgent();
	    if (agent != null)  {
	        agent.notifyConnectionReject(s.getName(), 
	                username,
	                con.remoteHostString());
	    }

            con.closeConnection(true,
                   GoodbyeReason.CON_FATAL_ERROR,
                  Globals.getBrokerResources().getKString(
                  BrokerResources.M_AUTH_FAIL_CLOSE));
        } else  {
            Agent agent = Globals.getAgent();
            if (agent != null)  {
                agent.registerConnection(con.getConnectionUID().longValue());
                agent.notifyConnectionOpen(con.getConnectionUID().longValue());
            }
	}

        return true;
    }

}
