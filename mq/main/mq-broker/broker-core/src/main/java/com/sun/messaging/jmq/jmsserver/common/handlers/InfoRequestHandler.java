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
 * @(#)InfoRequestHandler.java	1.11 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.ConsumerInfoNotifyManager;



/**
 * Handler class which deals with the INFO_REQUEST message 
 * sent by a client after failover.
 */
public class InfoRequestHandler extends PacketHandler 
{

    private Logger logger = Globals.getLogger();
    //private BrokerResources rb = Globals.getBrokerResources();

    public InfoRequestHandler()
    {
    }


    public static final int REQUEST_STATUS_INFO = 1;
    public static final int REQUEST_CLUSTER_INFO = 2;
    public static final int REQUEST_CONSUMER_INFO = 3;

    /**
     * Method to handle INFO_REQUEST messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 
          Hashtable pktprops = null;

          try {
              pktprops = msg.getProperties();
          } catch (Exception ex) {
              logger.logStack(Logger.WARNING, "INFO-REQUEST Packet.getProperties()", ex);
              pktprops = new Hashtable();

          }

          Integer level = (Integer)pktprops.get("JMQRequestType");

          if (level == null) {
              logger.log(logger.INFO,"No JMQRequestType set ");
              level = Integer.valueOf(-1); //pick and invalid value
          }

          if (level.intValue() == REQUEST_CONSUMER_INFO) {
              String destName = (String)pktprops.get("JMQDestination");
              int destType = ((Integer)pktprops.get("JMQDestType")).intValue();
              Boolean offb = (Boolean)pktprops.get("JMQRequestOff");
              boolean off = (offb == null ? false:offb.booleanValue());
              DestinationUID duid = DestinationUID.getUID(destName, destType);
              if (off) {
                  con.removeConsumerInfoRequest(duid);
              } else {
                  con.addConsumerInfoRequest(duid);
              }
              ConsumerInfoNotifyManager cm = Globals.getConnectionManager().
                                             getConsumerInfoNotifyManager();
              cm.consumerInfoRequested(con, duid, destType);
              return true;
          }

          sendInfoPacket(level.intValue(), con, msg.getConsumerID());
          return true;

    }


    // used by other method to get the INFO packet

    public static void sendInfoPacket(int requestType, IMQConnection con, long consumerUID) {
          Logger logger = Globals.getLogger();
          ClusterManager cfg =  Globals.getClusterManager();
          String reason = null;
          int status = Status.OK;

          Packet pkt = new Packet(con.useDirectBuffers());
          pkt.setPacketType(PacketType.INFO);
          pkt.setConsumerID(consumerUID);
          Hashtable replyprops = new Hashtable();        

          Hashtable bodyProperties = null;

          if (cfg == null) {
              status = Status.ERROR;
              reason = "Internal Error: NOT VALID CLUSTER ";
              logger.log(Logger.INFO,reason);
          } else if (requestType == REQUEST_STATUS_INFO) {
               ClusteredBroker cb = cfg.getLocalBroker();
               // OK, set properties
              bodyProperties = getInfo(cb);
              try {
                  if (cb.getState() == BrokerState.SHUTDOWN_STARTED) {
                      long timeout = Globals.getBrokerStateHandler()
                              .getShutdownRemaining(); 
                      bodyProperties.put("ShutdownMS", Long.valueOf(timeout));
                  }
              } catch (BrokerException ex) {
                  logger.logStack(Logger.WARNING,  "INFO-REQUEST", ex);
              }
          } else if (requestType == REQUEST_CLUSTER_INFO) {
              bodyProperties = getAllInfo();
          } else {
              status = Status.BAD_REQUEST;
              reason = "Internal Error: Bad JMQRequestType set " + requestType;
              logger.log(Logger.INFO,reason);
          }

//XXX - TBD
// should we write out the contents in a less intensive way
// (e.g. use the same format used for properties)
// or just serialize Hashtable (which is whay we are doing now)
//

          String list = null;
          if (cfg != null) {
              Iterator itr = cfg.getKnownBrokers(true);
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
                  replyprops.put("JMQBrokerList", list);
              }   
          }

          if (bodyProperties != null) {
              try {
                  ByteArrayOutputStream bos = new ByteArrayOutputStream();
                  ObjectOutputStream oos = new ObjectOutputStream(bos);
                  oos.writeObject(bodyProperties);
                  oos.flush();
                  bos.flush();
                  pkt.setMessageBody(bos.toByteArray());
                  bos.close();
              } catch (Exception ex) {
                  logger.logStack(Logger.WARNING, "INFO-REQUEST", ex);
              }
          }
          // OK deal with properties, etc
          replyprops.put("JMQRequestType", Integer.valueOf(requestType));
          replyprops.put("JMQStatus", Integer.valueOf(status));
          if (reason != null) {
              replyprops.put("JMQReason", reason);
          }
          pkt.setProperties(replyprops);
	      con.sendControlMessage(pkt);
    }


    static Hashtable getAllInfo() {

        ClusterManager cfg =  Globals.getClusterManager();
        if (cfg == null) return null;
        Iterator itr = cfg.getKnownBrokers(true);
        Hashtable ht = new Hashtable();
        String nameList = "";

        while (itr.hasNext()) {
            ClusteredBroker bc = (ClusteredBroker)itr.next();
            if (nameList.length() == 0) {
                nameList = bc.getBrokerName();
            } else {
                nameList += ","+bc.getBrokerName();
            }
            ht.put(bc.getBrokerName(), getInfo(bc));
        }
        ht.put("BrokerIDs", nameList);
        return ht;
    }

    static Hashtable getInfo(ClusteredBroker bc) 
    {

        BrokerState state = null;
        Logger logger = Globals.getLogger();
        //ClusterManager cfg =  Globals.getClusterManager();

        try {
            state = bc.getState();
        } catch (BrokerException ex) {
            logger.logStack(Logger.WARNING, "INFO-REQUEST", ex);
            state = BrokerState.INITIALIZING;
        }

        Hashtable ht = new Hashtable();
        ht.put("BrokerID", bc.getBrokerName());
        ht.put("brokerURL", bc.getBrokerURL().toString());
        ht.put("State", Integer.valueOf( mapStateToInt(state) ));
        ht.put("StateString", state.toString());        
        ht.put("isLocal", Boolean.valueOf( bc.isLocalBroker()));
        return ht;
    }

    static final int INITIALIZING = 1;
    static final int RUNNING = 2;
    static final int QUIESCE_STARTED = 3;
    static final int QUIESCE_COMPLETE = 4;
    static final int FAILOVER_STARTED = 5;
    static final int FAILOVER_COMPLETE = 6;
    static final int SHUTDOWN_STARTED = 7;
    static final int SHUTDOWN_COMPLETE = 8;

    static int mapStateToInt(BrokerState state)
    {
        if (state == BrokerState.INITIALIZING) {
            return INITIALIZING;
        } else if (state == BrokerState.OPERATING) {
            return RUNNING;
        } else if (state == BrokerState.QUIESCE_STARTED) {
            return QUIESCE_STARTED;
        } else if (state == BrokerState.QUIESCE_COMPLETED) {
            return QUIESCE_COMPLETE;
        } else if (state == BrokerState.SHUTDOWN_STARTED) {
            return SHUTDOWN_STARTED;
        } else if (state == BrokerState.SHUTDOWN_FAILOVER) {
            return SHUTDOWN_COMPLETE;
        } else if (state == BrokerState.SHUTDOWN_COMPLETE) {
            return SHUTDOWN_COMPLETE;
        } else if (state == BrokerState.FAILOVER_PENDING) {
            return FAILOVER_STARTED;
        } else if (state == BrokerState.FAILOVER_STARTED) {
            return FAILOVER_STARTED;
        } else if (state == BrokerState.FAILOVER_COMPLETE) {
            return FAILOVER_COMPLETE;
        } else if (state == BrokerState.FAILOVER_FAILED) {
            return FAILOVER_COMPLETE;
        }
        return 0;
    }



}

