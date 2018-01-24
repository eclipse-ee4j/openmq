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
 * @(#)ClientIDHandler.java	1.38 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.cluster.api.ClusterBroadcast;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.license.*;




/**
 * Handler class which deals with adding and removing interests from the RouteTable
 */
public class ClientIDHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;
 
    public ClientIDHandler() {
    }

    /**
     * Method to handle Consumer(add or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {

        // NOTE: setClientID is already Indempotent
        // at this point, this flag is not used
        //boolean isIndemp = msg.getIndempotent();

        // set up data for the return packet
        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID());
        pkt.setPacketType(PacketType.SET_CLIENTID_REPLY);
        Hashtable hash = new Hashtable();
        int status = Status.OK;
        String reason = null;

        Hashtable props = null;
        try {
            props = msg.getProperties();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, "SET-CLIENTID Packet.getProperties()", ex);
            assert false;
        }

        String cclientid = null; //client ID sent from client
        boolean shared = false;
        String namespace = null;

        if (props != null) {
            cclientid = (String)props.get("JMQClientID");
            namespace = (String)props.get("JMQNamespace");
            Boolean shareProp = (Boolean)props.get("JMQShare");

            // we are shared if any of the following is true:
            //    - namespace != null
            //    - JMQShare is true (this was never set by the
            //        app server in the previous release but
            //        may have been used by an internal customer)
            shared = (shareProp == null) ? namespace != null : 
                      shareProp.booleanValue();
        } else {
            assert false;
        }
        logger.log(Logger.DEBUG,"ClientID[" + namespace + ","
                  + cclientid + "," + shared + "] ");

        if (DEBUG)
            logger.log(Logger.DEBUG, "ClientIDHandler: registering clientID "+
                    cclientid);

        try  {
            status = Status.OK;
            reason = null;
            setClientID(con, cclientid, namespace,
                shared);
        } catch (BrokerException ex) {
            status = ex.getStatusCode();
            reason = ex.getMessage();
        }
            

        hash.put("JMQStatus", Integer.valueOf(status));
        if (reason != null)
            hash.put("JMQReason", reason);
        if (((IMQBasicConnection)con).getDumpPacket() || ((IMQBasicConnection)con).getDumpOutPacket())
            hash.put("JMQReqID", msg.getSysMessageID().toString());

        pkt.setProperties(hash);
        con.sendControlMessage(pkt);
        return true;
    }

    /**
	 * method to validate a client ID
     *
     * @param clientid the client ID sent from client
     * @param con the connection
     * @exception if clientid uses JMQ reserved name space "${u:" or null
     *       or in case of ${u} expansion if connection not authenticated
     */
    private String validate(String clientid, Connection con) 
    throws BrokerException {

        String cid = clientid; 
        if (clientid != null) {
            if (clientid.startsWith("${u}")) {
                AccessController ac = con.getAccessController();
                String user = ac.getAuthenticatedName().getName();
                cid = "${u:"+user+"}" +clientid.substring(4);
            }
            else if (clientid.startsWith("${u:")) {
                cid = null;
            } else if (clientid.indexOf("${%%}") != -1) {
                logger.log(Logger.DEBUG,"bad client id ${%%}");
                cid = null;
            }
        }
        if (cid == null || cid.trim().length() == 0) {
            throw new BrokerException(
                   Globals.getBrokerResources().getKString(
                            BrokerResources.X_INVALID_CLIENTID, 
                            (clientid == null) ? "null":clientid));
        }
        if (DEBUG) {
            logger.log(Logger.DEBUG, "ClientIDHandler:validated client ID:"+cid+":");
        }
        return cid;
    }


    public void setClientID(IMQConnection con, String cclientid, 
                            String namespace, boolean shared)
                            throws BrokerException {

        int status = Status.OK;
        String reason = null;

        try {
            // validate and expand the specified clientID
            String clientid = cclientid == null ? null : validate(cclientid, con);

            // retrieve the old client id
            String oldid = (String)con.getClientData(IMQConnection.CLIENT_ID);

            if (DEBUG && oldid != null) {
                logger.log(Logger.DEBUG, "ClientIDHandler: replacing clientID "+ oldid + " with " + clientid);
            }
                         
            if (clientid != null && (oldid == null || !oldid.equals(clientid))) {
                // we are specifying a new client ID (and the previous clientID was unset or different)
                // Nigel: If there was an old clientID, why don't we unlock it? 

                if (namespace != null) {
                    // a namespace was specified
                    // lock the combination of namespace and clientID 

                    String unspace = namespace + "${%%}" + clientid; 
                    int lockstatus = Globals.getClusterBroadcast().
                                         lockClientID(unspace,
                                         con.getConnectionUID(), false);
                    if (lockstatus != ClusterBroadcast.LOCK_SUCCESS) {
                        if (lockstatus == ClusterBroadcast.LOCK_TIMEOUT) {
                            String emsg = Globals.getBrokerResources().getKString(
                                BrokerResources.X_CLUSTER_CLIENTID_LOCK_REQUEST_TIMEOUT,
                                unspace, con.getRemoteConnectionString());
                            logger.log(Logger.INFO, emsg);
                            status = Status.TIMEOUT;
                            throw new BrokerException(emsg, status);
                        } else {
                            // namespace/clientID combination already in use
                            logger.log(Logger.INFO, 
                                BrokerResources.I_CLIENT_ID_IN_USE, 
                                con.getRemoteConnectionString(), unspace);
                            Connection owner = Globals.getConnectionManager().
                                       matchProperty(IMQConnection.CLIENT_ID, unspace);
                            assert owner == null || owner instanceof IMQConnection;
                            if (owner == null) { // remote
                                logger.log(Logger.INFO, 
                                    BrokerResources.I_RMT_CID_OWNER, unspace);
                            } else {
                                logger.log(Logger.INFO, 
                                    BrokerResources.I_LOCAL_CID_OWNER, unspace, 
                                    ((IMQConnection)owner).getRemoteConnectionString());
                            }
                            reason = "conflict w/ clientID";
                            status = Status.CONFLICT;
                            throw new BrokerException(reason, status);
                        }
                    }
                }
                 
                // now lock the clientID itself (whether shared or unshared)
                int lockstatus = Globals.getClusterBroadcast().
                                     lockClientID(clientid, 
                                     con.getConnectionUID(), shared);
                if (lockstatus != ClusterBroadcast.LOCK_SUCCESS) {
                    if (lockstatus == ClusterBroadcast.LOCK_TIMEOUT) {
                        String emsg = Globals.getBrokerResources().getKString(
                            BrokerResources.X_CLUSTER_CLIENTID_LOCK_REQUEST_TIMEOUT,
                            clientid, con.getRemoteConnectionString());
                            logger.log(Logger.INFO, emsg);
                            status = Status.TIMEOUT;
                            throw new BrokerException(emsg, status);
                    } else {
                        logger.log(Logger.INFO, 
                            BrokerResources.I_CLIENT_ID_IN_USE, 
                            con.getRemoteConnectionString(), clientid);
                        Connection owner = Globals.getConnectionManager().
                                   matchProperty(IMQConnection.CLIENT_ID, clientid);
                        assert owner == null || owner instanceof IMQConnection;
                        if (owner == null) { // remote
                            logger.log(Logger.INFO, 
                                BrokerResources.I_RMT_CID_OWNER, clientid);
                        } else {
                            logger.log(Logger.INFO, 
                                BrokerResources.I_LOCAL_CID_OWNER, clientid,
                                ((IMQConnection)owner).getRemoteConnectionString());
                        }
                        reason = "conflict w/ clientID";
                        status = Status.CONFLICT;
                        throw new BrokerException(reason, status);
                    }
                }
            } else if (oldid != null && !oldid.equals(clientid)) {
                /**
                 * we are explicitly clearing an old clientID
                 * unlock the old namespace/clientID combination 
                 * (assume specified namespace is the same as the old one)
                 */
                String oldunspace = namespace + "${%%}" + oldid; 
                logger.log(Logger.DEBUG, "ClientIDHandler: "+
                           "removing old namespace/clientID " + oldunspace);
                Globals.getClusterBroadcast().unlockClientID(oldunspace, con.getConnectionUID());

                // unlock the old clientID
                logger.log(Logger.DEBUG, "ClientIDHandler: "+ "removing old clientID " + oldid);
                Globals.getClusterBroadcast().unlockClientID(oldid, con.getConnectionUID());

                // remove the specified clientID from the connection
                con.removeClientData(IMQConnection.CLIENT_ID);
            }
            // save the specified clientID with the connection
            if (clientid != null && status != Status.CONFLICT) {
            	con.addClientData(IMQConnection.CLIENT_ID, clientid);
            } 
        } catch (BrokerException ex) {
            if (ex.getStatusCode() == Status.CONFLICT) {
                throw ex;
            }
            if (ex.getStatusCode() == Status.TIMEOUT) {
                throw ex;
            }
            logger.log(Logger.WARNING, BrokerResources.W_CLIENT_ID_INVALID,
                       cclientid, con.toString(), ex);
            status = Status.BAD_REQUEST;
            reason = ex.getMessage();
            throw new BrokerException(reason, ex, status);
        } catch (OutOfMemoryError err) {
            // throw so it is handled by higher-level memory handling code
            throw err;
        } catch (Throwable thr) {
            logger.log(Logger.WARNING, "unexpected error processing clientid ", thr);
            reason = thr.getMessage();
            status = Status.ERROR;
            throw new BrokerException(reason, thr, status);
        }
    }
}
