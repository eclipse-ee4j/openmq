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
 * @(#)RemoteConsumer.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.cluster.api;

/**
 * Class which handled 3.0 remove cluster topic
 * consumers (3.5 clusters will be smarter in
 * later releases
 */


import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.util.selector.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.core.*;

public class RemoteConsumer extends Consumer
{
    transient Set consumers = new HashSet();

    private static boolean DEBUG = false;

    private static Logger logger = Globals.getLogger();

    public RemoteConsumer(DestinationUID duid) 
        throws IOException, SelectorFormatException
    {
        super(duid, null, false, (ConnectionUID)null);
    }

    public int getConsumerCount() {
        synchronized(consumers) {
            return consumers.size();
        }
    }

    public void addConsumer(Consumer c) 
    {
        synchronized(consumers) {
            consumers.add(c);
        }
    }

    public void removeConsumer(Consumer c)
    {
        synchronized(consumers) {
            consumers.remove(c);
        }
    }

    public boolean match(PacketReference msg, Set s)
         throws BrokerException, SelectorFormatException
    {
        boolean match = false;
        Map props = null;
        Map headers = null;
      
        synchronized(consumers) {
            Iterator itr = consumers.iterator();
            Consumer c = (Consumer) itr.next();
            if (c.getSelector() == null) {
                match = true;
                s.add(c);
             } else  {
                 Selector selector = c.getSelector();
        
                 if (props == null && selector.usesProperties()) {
                     try {
                         props = msg.getProperties();
                     } catch (ClassNotFoundException ex) {
                         logger.logStack(Logger.ERROR,"INTERNAL ERROR", ex);
                         props = new HashMap();
                     }
                 }
                 if (headers == null && selector.usesFields()) {
                     headers = msg.getHeaders();
                 }
                 if (selector.match(props, headers)) {
                     match = true;
                     s.add(c);
                 }
           
            }
            return match;
        }
    }

    

    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        ois.defaultReadObject();
        consumers = new HashSet();
    }
} 
