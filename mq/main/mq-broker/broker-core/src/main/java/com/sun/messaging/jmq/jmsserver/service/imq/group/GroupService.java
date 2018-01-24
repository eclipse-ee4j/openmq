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
 * @(#)GroupService.java	1.19 06/29/07
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

public class GroupService extends IMQIPService
{
    static boolean DEBUG = false;

    Logger getLogger() {
        return logger;
    }


    public GroupService(String name, Protocol protocol,
        int type, PacketRouter router, int min, int max) {
        super(name, protocol, type, router, min, max);
        logger.log(Logger.DEBUG, "Running Group Service");

        serviceReadSelectors.initializeService(this, 
                    ((min/2)+ (min%2)), limit, 
                    readSelectorClass, SelectionKey.OP_READ);
        serviceWriteSelectors.initializeService(this, 
                    (min/2), limit, 
                    writeSelectorClass, SelectionKey.OP_WRITE);

    }

    public Hashtable getDebugState()
    {
        Hashtable ht = super.getDebugState();
        ht.put("readState", getDebugState(true));
        ht.put("writeState", getDebugState(false));
        return ht;
    }

    
    public void acceptConnection(IMQIPConnection con)
        throws IOException, BrokerException
    {
        if (DEBUG) {
            logger.log(Logger.DEBUG, "Adding new Connection {0} ",
                con.toString());
        }
      
        addConnection(this, con);

    }


    static MapList serviceReadSelectors = new MapList();
    static MapList serviceWriteSelectors = new MapList();

    static Class readSelectorClass = null;
    static Class writeSelectorClass = null;


    private static final String pkgname = "com.sun.messaging.jmq.jmsserver."
                                   + "service.imq.group.";
    static {
        try {
            readSelectorClass = Class.forName(pkgname +"ReadThread");
        } catch (Exception ex) {
                Globals.getLogger().logStack(Logger.ERROR, 
                     BrokerResources.E_INTERNAL_BROKER_ERROR, 
                     "unable to create class for handling READ selectors.", ex);
        }
        try {
            writeSelectorClass = Class.forName(pkgname +"WriteThread");
        } catch (Exception ex) {
                Globals.getLogger().logStack(Logger.ERROR, 
                     BrokerResources.E_INTERNAL_BROKER_ERROR, 
                     "unable to create class for handling WRITE selectors.", 
                      ex);
        }
    }



    public static final int UNLIMITED = -1;
    private static final int limit = Globals.getConfig().getIntProperty(
        Globals.IMQ + ".shared.connectionMonitor_limit", 64);


    public static void addConnection(GroupService svc, IMQIPConnection conn) 
        throws IOException
    {
        
        synchronized (GroupService.class) {
            SelectThread readthr = serviceReadSelectors.findThread(svc);
            SelectThread writethr = serviceWriteSelectors.findThread(svc);

            if (readthr == null || writethr == null) {
                  throw new IOException(Globals.getBrokerResources().getKString(
                                BrokerResources.E_INTERNAL_BROKER_ERROR, 
                                " No threads allocated for " 
                                + (readthr == null 
                                    ? (writethr == null ? "both" : "read") 
                                    : "write") 
                                + " selector thread on service " 
                                + svc + " closing connection " + conn));
            }
            GroupNotificationInfo ninfo = new GroupNotificationInfo();
            ninfo.targetThreads(readthr, writethr);
            conn.attach(ninfo);
            readthr.addNewConnection(conn);
            writethr.addNewConnection(conn);
        }

    }


    public static void destroyService(Service svc) {
        synchronized (GroupService.class) {
            serviceReadSelectors.destroy(svc);
            serviceWriteSelectors.destroy(svc);
        }

    }

    public static void dump(PrintStream str) {
        //synchronized (GroupService.class) {
        //}
    }

    public RunnableFactory getRunnableFactory() {
        return new GroupRunnableFactory();
    }

    ThreadPool getPool() {
        return pool;
    }

    public Hashtable getDebugState(boolean read) {
        if (read ) {
            if (serviceReadSelectors == null) {
                Hashtable ht = new Hashtable();
                ht.put("serviceReadSelectors","null");
                return ht; 
            }
            return serviceReadSelectors.getDebugState(this);
        } else {
            if (serviceWriteSelectors == null) {
                Hashtable ht = new Hashtable();
                ht.put("serviceWriteSelectors","null");
                return ht; 
            }
            return serviceWriteSelectors.getDebugState(this);
        }
    }

}



