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
 * %W% %G%
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.selector.Selector;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.jmsserver.plugin.spi.DestinationSpi;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.DestType;

import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;


import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;


/**
 * Handler class which deals with starting/stoping the delivery of 
 * messages to a specific connection
 */
public class VerifyDestinationHandler extends PacketHandler 
{

    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;
  

    public VerifyDestinationHandler() {
    }

    /**
     * Method to handle Destination (create or delete) messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {

        int status = Status.OK;
        String reason = null;

        assert msg.getPacketType() == PacketType.VERIFY_DESTINATION;

        Packet pkt = new Packet(con.useDirectBuffers());
        pkt.setConsumerID(msg.getConsumerID());

        pkt.setPacketType(PacketType.VERIFY_DESTINATION_REPLY);

        Hashtable hash = new Hashtable();

        Hashtable props = null;
        String selectorstr = null;
        Integer destType;
        int type = 0;
        try {
            props = msg.getProperties();
            String destination = (String )props.get("JMQDestination");
            Integer inttype = (Integer)props.get("JMQDestType");

            // destination & type required
            assert destination != null;

            type = (inttype == null) ? 0 : inttype.intValue();
            selectorstr = (String )props.get("JMQSelector");

            if (selectorstr != null) {
                Selector selector = Selector.compile(selectorstr);
                selector = null;
            }

            boolean notFound = false;

            DestinationSpi d = null;
            if (DestinationUID.isWildcard(destination)) {
                // retrieve the list of destinations
                pkt.setWildcard(true);
                // see if there are any destinations that match
                DestinationUID duid = DestinationUID.getUID(destination, type);
                List[] ll = coreLifecycle.findMatchingIDs(con.getPartitionedStore(), duid);
                List l = ll[0];
                if (l.isEmpty()) {
                    notFound = true;
                }

            } else {
                DestinationSpi[] ds =  coreLifecycle.getDestination(con.getPartitionedStore(),
                                                       destination, DestType.isQueue(type));
                d = ds[0];
                notFound = ( d == null);
            }

            if (notFound) {
                // not found
                status = Status.NOT_FOUND;
                reason = "destination not found";
                hash.put("JMQCanCreate", Boolean.valueOf(
                        coreLifecycle.canAutoCreate(DestType.isQueue(type))));
            } else {
                if (d != null)
                    hash.put("JMQDestType", Integer.valueOf(d.getType()));
            }
            
        } catch (SelectorFormatException ex) {
            reason = ex.getMessage();
            status = Status.BAD_REQUEST;
            logger.log(Logger.WARNING,BrokerResources.W_SELECTOR_PARSE, 
                 selectorstr, ex);
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Unable to verify destination - no properties", ex);
            reason = ex.getMessage();
            status = Status.ERROR;
        } catch (ClassNotFoundException ex) {
            logger.logStack(Logger.ERROR, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                "Unable to verify destination -bad class", ex);
            reason = ex.getMessage();
            status = Status.ERROR;
        } catch (BrokerException ex) {
            reason = ex.getMessage();
            status = ex.getStatusCode();
            logger.logStack(Logger.DEBUG, 
                BrokerResources.E_INTERNAL_BROKER_ERROR, 
               "Unable to verify destination ", ex);

        } catch (SecurityException ex) {
            reason = ex.getMessage();
            status = Status.FORBIDDEN;
            logger.log(Logger.WARNING,ex.toString(), ex);
        }
    

        hash.put("JMQStatus", Integer.valueOf(status));
        if (reason != null)
            hash.put("JMQReason", reason);
        if (((IMQBasicConnection)con).getDumpPacket() ||
                ((IMQBasicConnection)con).getDumpOutPacket()) 
            hash.put("JMQReqID", msg.getSysMessageID().toString());


        pkt.setProperties(hash);
        con.sendControlMessage(pkt);
        return true;
    }

}
