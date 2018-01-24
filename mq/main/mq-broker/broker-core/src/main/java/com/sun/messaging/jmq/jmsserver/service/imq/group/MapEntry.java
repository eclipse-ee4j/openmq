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
 * @(#)MapEntry.java	1.8 06/29/07
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


class MapEntry {
    Logger logger = Globals.getLogger();

        int min = 0;
        List l = null;
        Class selectClass = null;
        int mask = 0;
        int limit = 0;
        GroupService svc = null;

         
        public MapEntry(GroupService svc, int min, int limit, Class selectClass, int mask) {
            this.svc = svc;
            this.min = min;
            this.limit = limit;
            this.selectClass = selectClass;
            this.mask = mask;
            l = new ArrayList();
        }

        public Hashtable getDebugState() {
            Hashtable ht = new Hashtable();
try {
            ht.put("MapEntry", String.valueOf(this.hashCode()));
            ArrayList mylist = null;
            synchronized(this) {
                if (l != null)
                    mylist = new ArrayList(l);
            }
logger.log(Logger.INFO,"... ... ... Dumping MapEntry" + this.hashCode());
            Hashtable thrInfo = new Hashtable();
            for (int i =0; mylist != null && i < mylist.size(); i ++) {
                SelectThread thr = (SelectThread)mylist.get(i);
logger.log(Logger.INFO,"... ... ... ....  thread info " + thr.toString());
                thrInfo.put(String.valueOf(i), thr.getDebugState());
            }
            ht.put("SelectThreads", thrInfo);
            if (mylist != null)
                ht.put("SelectThreadCnt", String.valueOf( mylist.size()));
            if (svc != null)
                ht.put("Service", svc.getName());
            ht.put("min", String.valueOf(min));
            ht.put("limit", String.valueOf(limit));
            ht.put("mask", String.valueOf(mask));
} catch (Exception ex) {
    ht.put("Exception ", ex.toString());
    logger.log(Logger.INFO,"Error dumping ", ex);
}
            return ht;
        }

        public synchronized void destroy(String reason) {
            for (int i =0; i < l.size(); i ++) {
                SelectThread thr = (SelectThread)l.get(i);
                thr.destroy(reason);
            }
            l.clear();
        }


        public  boolean checkRemoveThread(SelectThread thr, boolean force) {

            boolean free = false;

            synchronized (this) {

                int indx = l.indexOf(thr);

                if (force || (thr != null && !thr.isValid()) || (indx == -1 || indx > min)) {
                    l.remove(thr);
                    free = true;
                }
            }
            if (free) {
                GroupRunnable grp= thr.getParent();
                if (grp != null)
                    grp.freeThread();

                else if (thr.isValid())
                    thr.destroy("Unused thread");

                return true;
            } 
            return false;
        }


        public synchronized SelectThread findThread() {
            // find the best in list, cleaning up any old entries
            Iterator itr = l.iterator();
            SelectThread selthr = null;

            int size = 0;
            while (itr.hasNext()) {
                SelectThread thr = (SelectThread)itr.next();
                if (!thr.isValid()) {
                    itr.remove();
                    continue;
                }
                if (limit == GroupService.UNLIMITED || thr.size() < limit) {
                    if (selthr == null || thr.size() < size) {
                        selthr = thr;
                        size = thr.totalSize();
                        size = thr.size();
                    }
                }
            }
            if ((selthr != null && selthr.totalSize() > 0 && l.size() < min) || selthr == null) { // create a new one !!!

                boolean finished = false;
                int attempt = 0;
                int max_attempt=5;

                while (!finished && attempt < max_attempt) {
                    try {
                        Class [] args={Service.class, MapEntry.class};
                        Object [] objs={svc, this};

                        GroupRunnable runner = null;
                        synchronized (svc) {
                            runner = (GroupRunnable)
                                svc.getPool().getAvailRunnable(false);

                            if (runner != null ) { // threads are available
                                Constructor con = selectClass.getConstructor(args);
                                selthr = (SelectThread) con.newInstance(objs);
                                l.add(selthr);
                                runner.assignThread(selthr, mask);
                                finished = true;
                            } else {
                                finished = true;
                                throw new Exception("no threads available from pool " + l.size());
                            }
                        }
                   } catch (Exception ex) {
                      // something internal went wrong, try to recover
                      Globals.getLogger().logStack(Logger.ERROR, 
                          BrokerResources.E_INTERNAL_BROKER_ERROR, 
                         "error creating a selector.", ex);
                      attempt ++;
                   }
                }
            }
            return selthr;

        }

}
