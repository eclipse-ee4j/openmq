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
 * @(#)GenerateUIDHandler.java	1.11 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.nio.ByteBuffer;
import java.util.Hashtable;

import com.sun.messaging.jmq.util.UniqueID;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;


/**
 * Handler class which deals with the GenerateUID packet.
 */
public class GenerateUIDHandler extends PacketHandler 
{
    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;

    public GenerateUIDHandler()
    {
        
    }

    /**
     * Method to handle GenerateUID packet.
     * We generate one or more unique ID's and return them in the body
     * of the reply.
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 

         if (DEBUG) {
             logger.log(Logger.DEBUGHIGH, "GenerateUIDHandler: handle() [ Received GenerateUID Packet]");
          }

          Hashtable props = null;
          try {
              props = msg.getProperties();
          } catch (Exception ex) {
              logger.logStack(Logger.WARNING, "GEN-UID Packet.getProperties()", ex);
              props = new Hashtable();
          }

          Integer value = null; 
          int quantity = 1;
          if (props != null) {
              value = (Integer)props.get("JMQQuantity");
              if (value != null) {
                quantity = value.intValue();
              }
          }

	  int status = Status.OK;

          // Each UID is a long (8 bytes);
          int size = quantity * 8;

          ByteBuffer body = ByteBuffer.allocate(size);

          // Get the prefix used by this broker and allocate IDs.
          // We could also do this by creating new jmq.util.UID's, but by
          // doing it this way we avoid the object creation overhead.
          short prefix = UID.getPrefix();
          for (int n = 0; n < quantity; n++) {
            body.putLong(UniqueID.generateID(prefix));
          }

          props = new Hashtable();
          props.put("JMQStatus", Integer.valueOf(status));
          props.put("JMQQuantity", Integer.valueOf(quantity));
          if (((IMQBasicConnection)con).getDumpPacket() ||
              ((IMQBasicConnection)con).getDumpOutPacket())
              props.put("JMQReqID", msg.getSysMessageID().toString());


          // Send reply 
          Packet pkt = new Packet(con.useDirectBuffers());
          pkt.setPacketType(PacketType.GENERATE_UID_REPLY);
          pkt.setConsumerID(msg.getConsumerID());
          pkt.setProperties(props);
          body.rewind();
          pkt.setMessageBody(body);

	  con.sendControlMessage(pkt);

          return true;
    }
}
