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
 * @(#)GetLicenseHandler.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.license.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;


/**
 * Handler class which deals with the GET_LICENSE message
 * GET_LICENSE requests licensing information so the client can restrict
 * licensed features.
 */
public class GetLicenseHandler extends PacketHandler 
{
    //private ConnectionManager connectionList;

    private Logger logger = Globals.getLogger();
    //private BrokerResources rb = Globals.getBrokerResources();
    private static boolean DEBUG = false;

    //private static boolean ALLOW_C_CLIENTS = false;
    private static boolean CAN_RECONNECT = false;

    static {
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
        } catch (BrokerException ex) {
            
        }
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            CAN_RECONNECT = license.getBooleanProperty(
                                license.PROP_ENABLE_FAILOVER, false);
        } catch (BrokerException ex) {
            CAN_RECONNECT = false;
        }

    }

    public GetLicenseHandler()
    {
    }

    /**
     * Method to handle GET_LICENSE messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 

         if (DEBUG) {
             logger.log(Logger.DEBUGHIGH, "GetLicenseHandler: handle(" + con + ", " + PacketType.getString(msg.getPacketType()) + ")" );
          }

          String reason = "";
	  int    status = Status.OK;

          // Create reply packet
          Packet pkt = new Packet(con.useDirectBuffers());
          pkt.setPacketType(PacketType.GET_LICENSE_REPLY);
          pkt.setConsumerID(msg.getConsumerID());

          Hashtable hash = new Hashtable();
          try {
              // Added licensing description properties
              LicenseBase license = Globals.getCurrentLicense(null);
              hash.put("JMQLicense",
                  license.getProperty(LicenseBase.PROP_LICENSE_TYPE));
              hash.put("JMQLicenseDesc",
                  license.getProperty(LicenseBase.PROP_DESCRIPTION));

              // Copy license properties into packet
              Properties props = license.getProperties();
              Enumeration e = props.propertyNames();
              while (e.hasMoreElements()) {
                  String key = (String)e.nextElement();
                  hash.put(key, props.get(key));
              }
          } catch (BrokerException ex) {
              // This should never happen, but go ahead and at least
              // capture exception here
              reason = ex.toString();
              status = Status.ERROR;
          }

          hash.put("JMQStatus", Integer.valueOf(status));
          if (status != Status.OK) {
              hash.put("JMQReason", reason);
           }

          // Set packet properties
          pkt.setProperties(hash);

          // Send message
	  con.sendControlMessage(pkt);

          return true;
    }

}
