/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.common.handlers;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.util.GoodbyeReason;

import java.util.*;

@SuppressWarnings("JdkObsolete")
final class GoodbyeTask extends TimerTask {
    static Logger logger = Globals.getLogger();
    static GoodbyeTask runner = null;

    LinkedList nextSet = new LinkedList();
    LinkedList reasonSet = new LinkedList();

    static long timeout = 0;

    boolean invalid = false;

    private static synchronized GoodbyeTask get() {
        if (runner == null) {
            runner = new GoodbyeTask();
        }
        return runner;
    }

    public static void initialize(long ttl) {
        timeout = ttl;
    }

    private GoodbyeTask() {
        if (timeout <= 0)
         {
            timeout = 300 * 1000; // 5 minutes
        }
        try {
            Globals.getTimer(true).schedule(this, timeout, timeout);
        } catch (IllegalStateException ex) {
            logger.logStack(Logger.DEBUG, "Timer canceled ", ex);
            invalid = true;
        }
    }

    private synchronized void _addCon(ConnectionUID conuid, String reason) {
        if (invalid) {
            return;
        }
        reasonSet.add(reason);
        nextSet.add(conuid);
    }

    public static void addConnection(ConnectionUID conuid, String reason) {
        synchronized (GoodbyeTask.class) {
            // GoodbyeTask task = runner.get();
            try {
                runner.get()._addCon(conuid, reason);
            } catch (IllegalStateException ex) {
                logger.logStack(Logger.DEBUG, "Timer canceled ", ex);
            }
        }
    }

    @Override
    public void run() {
        LinkedList list = null;
        LinkedList reasonlist = null;
        synchronized (GoodbyeTask.class) {
            synchronized (this) {
                if (nextSet.isEmpty()) {
                    runner.cancel();
                    runner = null;
                } else {
                    list = nextSet;
                    reasonlist = reasonSet;
                    nextSet = new LinkedList();
                    reasonSet = new LinkedList();
                }
            }
        }
        if (list == null) {
            return;
        }
        Iterator itr = list.iterator();
        while (itr.hasNext()) {
            ConnectionUID uid = (ConnectionUID) itr.next();
            IMQConnection con = (IMQConnection) Globals.getConnectionManager().getConnection(uid);
            String reason = null;
            try {
                reason = (String) reasonlist.removeFirst();
            } catch (Exception e) {
                logger.log(Logger.DEBUG, "Can't get reason string for destroying connection " + uid);
            }
            if (reason == null) {
                reason = "REASON NOTFOUND";
            }
            if (con != null && con.isValid()) {
                try {
                    con.destroyConnection(false, GoodbyeReason.CLIENT_CLOSED, reason);
                } catch (Exception ex) {
                    logger.logStack(Logger.DEBUG, "error destroying connection " + con, ex);
                }
            }
            itr.remove();
        }
    }

}
