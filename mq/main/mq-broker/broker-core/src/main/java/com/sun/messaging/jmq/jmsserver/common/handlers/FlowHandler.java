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
 * @(#)FlowHandler.java	1.20 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import java.util.*;
import java.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.net.*;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionManager;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.auth.AuthCacheData;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;

import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;

/**
 * handles receiving Flow packet
 */
public class FlowHandler extends PacketHandler 
{

    private Logger logger = Globals.getLogger();
    private static boolean DEBUG = false;


    /**
     * Method to handle flow messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException 
    { 

         if (DEBUG) {
             logger.log(Logger.DEBUGHIGH, "FlowHandler: handle() [ Received Flow  Message]");
          }

          assert msg.getPacketType() == PacketType.RESUME_FLOW;


          Hashtable props = null;
          try {
              props = msg.getProperties();
          } catch (Exception ex) {
              logger.logStack(Logger.WARNING, "RESUME-FLOW Packet.getProperties()", ex);
              props = new Hashtable();
          }

          Integer bufsize = null; 

          ConsumerSpi consumer = null;
          if (props != null) {
              bufsize = (Integer)props.get("JMQSize");
              if (bufsize == null) { // try old protocol
                  bufsize = (Integer)props.get("JMQRBufferSize");
              }

              Long cuid = (Long)props.get("JMQConsumerID");
              if (cuid != null) {
                  ConsumerUID tmpuid = new ConsumerUID(cuid.longValue());
                  consumer = coreLifecycle.getConsumer(tmpuid);
              }
          }

          if (DEBUG)
              logger.log(Logger.DEBUG, "Setting JMQRBufferSize -" + bufsize);

          if (consumer != null) {
              // consumer flow control
              int size = (bufsize == null ? -1 : bufsize.intValue());
		      consumerFlow(consumer, size);
          } else {
              // connection flow control
              int size = (bufsize == null ? -1 : bufsize.intValue());
              connectionFlow(con, size);
          }
          return true;
 
    }

    public void consumerFlow(ConsumerSpi consumer, int cprefetch)
    {
          try {
              int prefetch = coreLifecycle.calcPrefetch(consumer, cprefetch);
              consumer.resumeFlow(prefetch);
          } catch (Exception ex) {
              // only happens if client passs bad cprefetch
              // which is < current size .. this is a protocol
              // error 
              logger.logStack(Logger.ERROR,
                  Globals.getBrokerResources().getString(
                      BrokerResources.X_INTERNAL_EXCEPTION,
                       "protocol error, bad rbuf size"), ex);
              consumer.resumeFlow(-1);
          }
    }

    public void connectionFlow(IMQConnection con,int  size)
    {
        con.resumeFlow(size);
    }
}
