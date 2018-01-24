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
 * @(#)MapList.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.group;

import java.util.*;
import java.lang.reflect.*;
import java.io.*;
import java.nio.channels.spi.*;
import java.nio.channels.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.pool.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.net.Protocol;
import com.sun.messaging.jmq.jmsserver.net.ProtocolStreams;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;


class MapList {
    HashMap map = new HashMap();

    public void initializeService(GroupService svc, int min, int limit, Class selectClass, int mask)
    {
        MapEntry entry = new MapEntry(svc, min, limit,selectClass, mask);
        synchronized (this) {
            map.put(svc.getName(), entry);
        }
    }

    public Hashtable getDebugState(GroupService svc) 
    {
        MapEntry entry = null;
        synchronized (this) {
            entry = (MapEntry)map.get(svc.getName());
        }
        if (entry == null) {
            Hashtable ht = new Hashtable();
            ht.put("Service " + svc, "null");
            return ht;
        }
       
        return entry.getDebugState();
    }

    public void destroy(Service svc) {
        MapEntry entry = null;
        synchronized (this) {
            entry = (MapEntry)map.get(svc.getName());
            if (entry != null) {
                map.remove(svc.getName());
             }
        }
        if (entry != null)
             entry.destroy(
               Globals.getBrokerResources().getKString(
                   BrokerResources.M_SERVICE_SHUTDOWN));
    }

    public SelectThread findThread(GroupService svc) {
        MapEntry entry = null;
        synchronized (this) {
            entry = (MapEntry)map.get(svc.getName());
        }
        if (entry == null) {
             throw new RuntimeException("service does not have thread pool");
        }
        return entry.findThread();
   }
   public synchronized boolean checkRemoveThread(GroupService svc, SelectThread thr, boolean force) 
   {
        MapEntry entry = null;
        synchronized (this) {
           entry = (MapEntry)map.get(svc.getName());
        }
        if (entry == null) {
             throw new RuntimeException("service does not have thread pool");
        }
        return false; 
        // for now, dont remove threads
        //return entry.checkRemoveThread(thr, force);
   }


}
