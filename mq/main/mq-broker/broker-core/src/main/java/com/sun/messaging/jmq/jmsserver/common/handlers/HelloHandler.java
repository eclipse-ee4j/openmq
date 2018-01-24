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
 * @(#)HelloHandler.java	1.71 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.net.*;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.GoodbyeReason;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.net.*;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.cluster.api.ha.HAMonitorService;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.jmsserver.memory.MemoryManager;
import com.sun.messaging.jmq.jmsserver.license.*;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.plugin.spi.CoreLifecycleSpi;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.GoodbyeReason;




/**
 * Handler class which deals with the Hello message which is created when
 * a client starts talking to the broker.
 * Hello provides a "ping" message and also sets up a connection if the
 * protocol used does not have a unique way of determining a connection
 * (e.g. tcp does not need the HELLO message to set up a connection, since
 * each socket corresponds to a new connection)
 */
public class HelloHandler extends PacketHandler 
{
    private ConnectionManager connectionList;

    private Logger logger = Globals.getLogger();
    private BrokerResources rb = Globals.getBrokerResources();
    private static boolean DEBUG = false;

    private static boolean ALLOW_C_CLIENTS = false;
    private static boolean CAN_RECONNECT = false;

    static {
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            ALLOW_C_CLIENTS = license.getBooleanProperty(
                                license.PROP_ENABLE_C_API, false);
        } catch (BrokerException ex) {
            ALLOW_C_CLIENTS = false;
        }
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            CAN_RECONNECT = license.getBooleanProperty(
                                license.PROP_ENABLE_RECONNECT, false);
        } catch (BrokerException ex) {
            CAN_RECONNECT = false;
        }
        if (Globals.getLogger().getLevel() <= Logger.DEBUG) {
            DEBUG = true;
	}
    }

    public static void DUMP(String title) {
        Globals.getLogger().log(Logger.DEBUG,title);
        Globals.getLogger().log(Logger.DEBUG,"------------------------");
        Globals.getLogger().log(Logger.DEBUG,"Number of connections is " + 
                          Globals.getConnectionManager().getNumConnections(null));
        List l = Globals.getConnectionManager().getConnectionList(null);
        for (int i=0; i < l.size(); i ++ ) {
            Connection c= (Connection)l.get(i);
            Globals.getLogger().log(Logger.DEBUG,"\t" + i + "\t" +
                 c.getConnectionUID() + " :" + c.getRemoteConnectionString());
        }
        Globals.getLogger().log(Logger.DEBUG,"------------------------");
    }



    public HelloHandler(ConnectionManager list)
    {
        connectionList = list;
    }

    /**
     * Method to handle HELLO messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 

         if (DEBUG) {
             logger.log(Logger.DEBUGHIGH, "HelloHandler: handle() [ Received Hello Message]");
          }

          String reason = null;
          Hashtable hello_props = null;


          try {
              hello_props = msg.getProperties();
          } catch (Exception ex) {
              logger.logStack(Logger.WARNING, "HELLO Packet.getProperties()", ex);
              hello_props = new Hashtable();
          }

          boolean alreadyStarted = con.isStarted();
          boolean alreadyAuthenticated = con.isAuthenticated();

          int requestedProtocol = 0;
          int highestProtocol = con.getHighestSupportedProtocol();
          int lowestProtocol = PacketType.VERSION1;

          String expectedClusterID = null;
          UID expectedSessionID = null;
          ConnectionUID oldCID = null;
          Integer bufsize = null;
          boolean badClientType = false;
          String destprov = null;
          if (hello_props != null) {
              Integer level = (Integer)hello_props.get("JMQProtocolLevel");
              String clientv = (String)hello_props.get("JMQVersion");
              if (DEBUG) {
                  logger.log(logger.INFO, "HelloHandler.handle(): Client["+clientv+", "+level+"] "+con);
              }
              if (level == null) {
                  requestedProtocol=PacketType.VERSION1;
              } else {
                  requestedProtocol=level.intValue();
              }
               bufsize = (Integer)hello_props.get("JMQSize");
              if (bufsize == null) { //XXX try old protocol
                  bufsize = (Integer)hello_props.get("JMQRBufferSize");
              }

              // Retrieve HA related properties
              Long longUID = (Long)hello_props.get("JMQStoreSession");
              if (longUID != null) {
                  expectedSessionID = new UID(longUID.longValue());
              }

              expectedClusterID = (String)hello_props.get("JMQClusterID");

              Boolean reconnectable = (Boolean)hello_props.get("JMQReconnectable");
              Boolean haclient = (Boolean)hello_props.get("JMQHAClient");
              if (Globals.getHAEnabled() && haclient != null && haclient.booleanValue()) {
                  reconnectable = haclient;
              }

              String s = (String)hello_props.get("JMQUserAgent");
              if (!ALLOW_C_CLIENTS && s != null &&  s.indexOf("C;") != -1) {
                  badClientType = true;
              }
              if (s != null) {
                con.addClientData(IMQConnection.USER_AGENT, s);
              }

              //currently private property
              destprov = (String)hello_props.get("JMQDestinationProvider");

              longUID = (Long)hello_props.get("JMQConnectionID");

              if (longUID != null) {
                  logger.log(Logger.DEBUG,"Have old connectionUID");
                  oldCID = new ConnectionUID(longUID.longValue());
                  logger.log(Logger.INFO,
                         BrokerResources.I_RECONNECTING, oldCID);
                  logger.log(Logger.DEBUG,"Checking for active connection");

                  Connection oldcon = Globals.getConnectionManager().getConnection(oldCID);
                  DUMP("Before connection Destroy");
                  if (oldcon != null) {
                      logger.log(Logger.DEBUG,"Destroying old connection " + oldCID);
                      oldcon.destroyConnection(true,GoodbyeReason.ADMIN_KILLED_CON, "Destroying old connection with same connectionUID " + oldCID + " - reconnect is happening before connection was reaped");
                  }
/* LKS
                  DUMP();

                  logger.log(Logger.DEBUG,"Updating connection in id list " +
                           "["+oldcid + "," + uid + "]");
                  // old code
                  con.setConnectionUID(oldcid);
                  Globals.getConnectionManager().updateConnectionUID(
                         oldcid, uid);
                  //Globals.getConnectionManager().updateConnectionUID(
                  //       uid, oldcid);
*/
                  DUMP("After Connection Destroy");
              }

              con.getConnectionUID().setCanReconnect(reconnectable == null ? false :
                      reconnectable.booleanValue());

              Long interval = (Long)hello_props.get("JMQInterval");

              // LKS - XXX just override for testing
              long itime = (interval == null ? 
                 ConnectionManager.DEFAULT_RECONNECT_INTERVAL : interval.longValue());
              con.setReconnectInterval(itime);
               
          } else {
              requestedProtocol=PacketType.VERSION1;
          }

          int supportedProtocol = 0;
          if (requestedProtocol > highestProtocol) {
              supportedProtocol = highestProtocol;
          } else if (requestedProtocol < lowestProtocol) {
              supportedProtocol = lowestProtocol;
          }  else {
              supportedProtocol = requestedProtocol;
          }
          con.setClientProtocolVersion(supportedProtocol);

           if (bufsize != null) {
              logger.log(Logger.DEBUG, "Received JMQRBufferSize -" + bufsize);
              con.setFlowCount(bufsize.intValue());
           }


          Packet pkt = new Packet(con.useDirectBuffers());
          pkt.setPacketType(PacketType.HELLO_REPLY);
          pkt.setConsumerID(msg.getConsumerID());
          Hashtable hash = new Hashtable();
          reason = "unavailable";
          int status = Status.UNAVAILABLE;

          // If the connection's remote IP address was not set by the
          // protocol, then use the IP in the message packet.
          if (con.getRemoteIP() == null) {
            con.setRemoteIP(msg.getIP());
          }


          if ((alreadyAuthenticated || alreadyStarted) 
                  && !msg.getIndempotent() ) { // handle ibit
              status = Status.ERROR;
              reason = "Connection reuse not allowed";
              if (alreadyAuthenticated) {
                  logger.log(Logger.WARNING,"Internal Error: " +
                    " received HELLO on already authenticated connection "
                    + con.getRemoteConnectionString() +
                    " " + con.getConnectionUID());
              } else {
                  logger.log(Logger.WARNING,"Internal Error: " +
                    " received HELLO on already started connection "
                    + con.getRemoteConnectionString() +
                    " " + con.getConnectionUID());
              }
          
          } else if (badClientType) {
              logger.log(Logger.ERROR, rb.E_FEATURE_UNAVAILABLE,
                   Globals.getBrokerResources().getString(
                        BrokerResources.M_C_API));
              reason = "C clients not allowed on this version";
	      status = Status.UNAVAILABLE;
          } else if (!CAN_RECONNECT && con.getConnectionUID().getCanReconnect()) {
              logger.log(Logger.ERROR, rb.E_FEATURE_UNAVAILABLE,
                   Globals.getBrokerResources().getString(
                        BrokerResources.M_CLIENT_FAILOVER));
              reason = "Client Failover not allowed on this version";
          } else if (requestedProtocol != supportedProtocol) {
              // Bad protocol level.
              logger.log(Logger.WARNING, rb.W_BAD_PROTO_VERSION,
                Integer.toString(requestedProtocol), 
                Integer.toString(supportedProtocol));

              reason = "bad version";
	      status = Status.BAD_VERSION;
          } else if (con.getConnectionState() != Connection.STATE_UNAVAILABLE) {
              /** 
               * connection may not be able to be created e.g: 
               * licensing, being destroyed (e.g due to timeout)
               */
              if (con.setConnectionState(Connection.STATE_INITIALIZED)) {
                  reason = null;
                  status = Status.OK;
              } else {
                  status = Status.UNAVAILABLE;
              }
          } else {
              status = Status.UNAVAILABLE;
          }
          if (status == Status.OK && destprov != null) {
              if (((IMQService)con.getService()).getServiceType() == ServiceType.ADMIN) {
                  status = Status.BAD_REQUEST;
                  reason = "JMQDestinationProvider not supported on ADMIN service";
                  logger.log(logger.WARNING, reason);
              } else if (!destprov.equals(CoreLifecycleSpi.GFMQ) &&
                         !destprov.equals(CoreLifecycleSpi.CHMP)) {
                  status = Status.UNSUPPORTED_TYPE;
                  reason = "Unsupported JMQDestinationProvider "+destprov;
                  logger.log(logger.WARNING, reason);
              } else if (destprov.equals(CoreLifecycleSpi.CHMP) &&
                         Globals.getCorePlugin(destprov) == null) { 
                  status = Status.UNSUPPORTED_TYPE;
                  reason = destprov+ " not enabled";
                  logger.log(logger.WARNING, reason);
              }
          }
          
          UID brokerSessionID = Globals.getBrokerSessionID();
          if (brokerSessionID!=null){
              hash.put("JMQBrokerSessionID", 
                  Long.valueOf(brokerSessionID.longValue()));
          }
          
          // OK, handle the HA properties HERE

          String clusterID = null;
          UID sessionUID = null;

          ClusterManager cfg = Globals.getClusterManager();
          if (cfg != null) {
              clusterID = cfg.getClusterId();
              sessionUID = cfg.getStoreSessionUID();

              hash.put("JMQHA", Boolean.valueOf(cfg.isHA()));

              if (clusterID != null) {
                  hash.put("JMQClusterID", clusterID);
              }
              if (sessionUID != null && !Globals.getDestinationList().isPartitionMode())  {
                  hash.put("JMQStoreSession", Long.valueOf(sessionUID.longValue()));
              }

              String list = null;
              Iterator itr = null;
              if (((IMQService)con.getService()).getServiceType() != ServiceType.ADMIN) {
                  itr = cfg.getKnownBrokers(false);
              } else {
                  itr = cfg.getKnownBrokers(true);
              }
              Set s = new HashSet();
              // ok get rid of dups
              while (itr.hasNext()) {
                  ClusteredBroker cb = (ClusteredBroker)itr.next();
                  s.add(cb.getBrokerURL().toString());
              }
              // OK .. now convert to a string

              itr = s.iterator();

              while (itr.hasNext()) {
                  if (list == null) {
                      list = itr.next().toString();
                  } else {
                      list += "," + itr.next().toString();
                  }
              }
              if (list != null) {
                  hash.put("JMQBrokerList", list);
              }   
          }

          HAMonitorService hamonitor = Globals.getHAMonitorService(); 
          if (hamonitor != null && hamonitor.inTakeover()) {
              if (((IMQService)con.getService()).getServiceType() != ServiceType.ADMIN) {
                  status = Status.TIMEOUT;
                  if (oldCID != null) {
                      logger.log(logger.INFO,
                          BrokerResources.W_IN_TAKEOVER_RECONNECT_LATER, oldCID);
                  } else {
                      logger.log(logger.INFO,
                          BrokerResources.W_IN_TAKEOVER_RECONNECT_LATER,
                          con.getConnectionUID());
                  }
              }
          }
          
          // first we want to deal with a bad clusterid
          if (clusterID != null && expectedClusterID != null
                 && !clusterID.equals(expectedClusterID)) {
              status = Status.BAD_REQUEST;

          } else if (expectedSessionID != null && sessionUID != null &&
                     expectedSessionID.equals(sessionUID)) {
               // cool we connected to the right broker
               // we already have the right owner
          } else if (expectedSessionID != null) {


              if (cfg == null) { // not running any cluster config
                      logger.log(Logger.WARNING, 
                            BrokerResources.E_INTERNAL_BROKER_ERROR, 
                              "Internal Error: Received session on"
                            + " non-clustered broker");

                      status = Status.NOT_FOUND;

              } else {
                  // OK, if we are here, we need to locate the right
                  // broker for the session
                  //
                  // Here are the steps we need to check:
                  //  1. does this broker support the sessionUID
                  //     if not
                  //  2. can we locate another broker with the sessionUID
                  //

                  ClusteredBroker owner = null;

                  //
                  // OK, see if this was a session UID we took over at some
                  // point in the past
                  Set s = cfg.getSupportedStoreSessionUIDs();
                  if (s.contains(expectedSessionID)) {
                      // yep, we took it over
                      owner = cfg.getLocalBroker();
                  }

                  // OK, we dont have it in our list
                  //
                  // We want to find out who did take it over (although that
                  // information may be lost, there are no guarentees at this
                  // point since there is a limit to what we persistently store
    
                  if (owner == null) { // this broker isnt supprting the session
    
                      // see if the database indicates someone else has it
    
                      String ownerString = cfg.lookupStoreSessionOwner(expectedSessionID);
                      if (ownerString != null) {
                          owner = cfg.getBroker(ownerString);
                      }
                  }
                  try {
    
                      // ok, we didnt find it (that doesnt mean someone didnt
                      // take it over, just that we cant find out who)
                      // makesure we return the right error message
     
                      if (owner != null) {
                          ClusteredBroker creator = null;
                          String creatorString = cfg.getStoreSessionCreator(expectedSessionID); 
                          if (creatorString != null) {
                              creator = cfg.getBroker(creatorString);
                          }
                          int stat = owner.getStatus();
                          if (BrokerStatus.getBrokerInDoubt(stat) ||
                              !BrokerStatus.getBrokerLinkIsUp(stat) ||
                              owner.getState() == BrokerState.FAILOVER_STARTED) {
                              status = Status.TIMEOUT;
                              logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                              BrokerResources.I_RECONNECT_OWNER_INDOUBT, expectedSessionID, owner));
                          } else if (!owner.isLocalBroker()) {
                              status = Status.MOVED_PERMANENTLY;
                              hash.put("JMQStoreOwner", owner.getBrokerURL().toString());
                              logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                     BrokerResources.I_RECONNECT_OWNER_NOTME, expectedSessionID, owner));
                          } else if (creator == null) { //XXX
                              status = Status.NOT_FOUND;
                              logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                         BrokerResources.I_RECONNECT_NOCREATOR, expectedSessionID));
                          } else if (creator.getState() == BrokerState.FAILOVER_STARTED) {
                              status = Status.TIMEOUT;
                              logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                         BrokerResources.I_RECONNECT_INTAKEOVER, expectedSessionID));
                          } else { // local broker owns us - set owner for debugging only
                                   // not required for protocol
                              hash.put("JMQStoreOwner", owner.getBrokerURL().toString());
                          }
                      } else { // didnt find owner
                          status = Status.NOT_FOUND;
                          logger.log(logger.INFO, Globals.getBrokerResources().getKString(
                                  BrokerResources.I_RECONNECT_OWNER_NOTFOUND, expectedSessionID));
                      }
                  } catch (Exception ex) {
                      logger.log(Logger.WARNING, 
                            BrokerResources.W_RECONNECT_ERROR, expectedSessionID.toString(), ex);
                      status = Status.NOT_FOUND;
                  }
             }

          }


          if (!con.isAdminConnection() && Globals.getMemManager() != null) {

              hash.put("JMQSize", Integer.valueOf(Globals.getMemManager().getJMQSize()));
              hash.put("JMQBytes", Long.valueOf(Globals.getMemManager().getJMQBytes()));
              hash.put("JMQMaxMsgBytes", Long.valueOf(Globals.getMemManager().getJMQMaxMsgBytes()));
          }
          hash.put("JMQService", con.getService().getName());
          hash.put("JMQConnectionID", Long.valueOf(con.getConnectionUID().longValue()));
          hash.put("JMQProtocolLevel",
                       Integer.valueOf(supportedProtocol));
          hash.put("JMQVersion",
                       Globals.getVersion().getProductVersion());
          if (((IMQBasicConnection)con).getDumpPacket() ||
                 ((IMQBasicConnection)con).getDumpOutPacket())
              hash.put("JMQReqID", msg.getSysMessageID().toString());

          try {
              // Added licensing description properties
              LicenseBase license = Globals.getCurrentLicense(null);
              hash.put("JMQLicense",
                  license.getProperty(LicenseBase.PROP_LICENSE_TYPE));
              hash.put("JMQLicenseDesc",
                  license.getProperty(LicenseBase.PROP_DESCRIPTION));
          } catch (BrokerException ex) {
              // This should never happen, but go ahead and at least
              // capture exception here
              hash.put("JMQLicenseDesc", ex.toString());
          }
          try {
              sessionUID = con.attachStorePartition(expectedSessionID);
              if (Globals.getDestinationList().isPartitionMode())  {
                  hash.put("JMQStoreSession", Long.valueOf(sessionUID.longValue()));
              }
          } catch (BrokerException e) {
              status = e.getStatusCode();
              reason = e.getMessage();
              if (status == Status.NOT_FOUND) {
                  logger.log(logger.INFO, e.getMessage());
              } else {
                  logger.logStack(logger.ERROR, e.getMessage(), e);
              }
          }
          hash.put("JMQStatus", Integer.valueOf(status));
          if (reason != null) {
              hash.put("JMQReason", reason);
          }
          pkt.setProperties(hash);

	  con.sendControlMessage(pkt);

         // OK .. valid status messages are
         if (status != Status.OK && status != Status.MOVED_PERMANENTLY
              && status != Status.NOT_FOUND && status != Status.TIMEOUT) {
             // destroy the connection !!! (should be ok if destroy twice)
             con.closeConnection(true, GoodbyeReason.CON_FATAL_ERROR,
                  Globals.getBrokerResources().getKString(
                  BrokerResources.M_INIT_FAIL_CLOSE));
             connectionList.removeConnection(con.getConnectionUID(),
                  false,GoodbyeReason.CON_FATAL_ERROR,
                  Globals.getBrokerResources().getKString(
                  BrokerResources.M_INIT_FAIL_CLOSE));
             return true;
         }

          status = Status.UNAVAILABLE;
          String authType = null;
          if (hello_props != null) {
              authType = (String)hello_props.get("JMQAuthType");
          }
          AccessController ac = con.getAccessController();
          pkt = new Packet(con.useDirectBuffers());
          pkt.setPacketType(PacketType.AUTHENTICATE_REQUEST);
          pkt.setConsumerID(msg.getConsumerID());

          hash = new Hashtable();
          hash.put("JMQSequence", Integer.valueOf(msg.getSequence()));
          hash.put("JMQChallenge", Boolean.valueOf(true));

          Properties props = new Properties();
          props.setProperty(Globals.IMQ + ".clientIP", msg.getIPString());
          props.setProperty(Globals.IMQ + ".connectionID", con.getConnectionUID().toString());
          byte[] req = null;
          try {
          AuthCacheData acd = ((IMQService)con.getService()).getAuthCacheData();
          req = ac.getChallenge(msg.getSequence(), props, 
                                acd.getCacheData(), authType);
          hash.put("JMQAuthType", ac.getAuthType());
          if (con.setConnectionState(Connection.STATE_AUTH_REQUESTED)) {
              status = Status.OK;
          }

          } catch (FailedLoginException e) {
          logger.log(Logger.WARNING, e.getMessage(), e);
          status = Status.FORBIDDEN;
          } catch (OutOfMemoryError err) {
              // throw error so that memory is freed and
              // packet is re-processed
              throw err;
          } catch (Throwable w) {
          logger.log(Logger.ERROR, Globals.getBrokerResources().getKString(
              BrokerResources.E_GET_CHALLENGE_FAILED)+" - "+w.getMessage(), w);
          status = Status.FORBIDDEN;
          }
          try {
              if (destprov != null && !destprov.equals(CoreLifecycleSpi.GFMQ)) {
                  CoreLifecycleSpi clc = Globals.getCorePlugin(destprov);
                  ((IMQBasicConnection)con).setPacketRouter(clc.getPacketRouter());
                  con.setCoreLifecycle(clc);
              }
          } catch (Exception e) {
              status = Status.ERROR;
              logger.logStack(logger.ERROR, e.getMessage(), e);
          }
          hash.put("JMQStatus", Integer.valueOf(status));
          if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
              hash.put("JMQReqID", msg.getSysMessageID().toString());

          pkt.setProperties(hash);
          if (req != null) {
              pkt.setMessageBody(req);
          }
          
          con.sendControlMessage(pkt);
          if (DEBUG) {
          logger.log(Logger.DEBUG,  "HelloHandler: handle() [ sent challenge ]"
                                      + ":status="+ Status.getString(status));
          }

          if (status != Status.OK && status != Status.MOVED_PERMANENTLY
              && status != Status.NOT_FOUND && status != Status.TIMEOUT) {
             // destroy the connection !!! (should be ok if destroy twice)
              con.closeConnection(true, GoodbyeReason.CON_FATAL_ERROR, 
                  Globals.getBrokerResources().getKString(
                  BrokerResources.M_INIT_FAIL_CLOSE));
              connectionList.removeConnection(con.getConnectionUID(),
                  false,GoodbyeReason.CON_FATAL_ERROR,
                  Globals.getBrokerResources().getKString(
                  BrokerResources.M_INIT_FAIL_CLOSE));
          }
          return true;

    }

}
