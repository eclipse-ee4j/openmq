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
 * @(#)ReadQTable.java	1.7 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Iterator;

class ReadQTable {

    Hashtable table = new Hashtable();

    // add new sessionQ
    protected void
    put (Object sessionId, Object sessionQ) {
        table.put(sessionId, sessionQ);
    }

    //remove sessionQ
    protected void
    remove (Object sessionId) {
        table.remove (sessionId);
    }

    protected SessionQueue
    get ( Object key ) {
        return (SessionQueue) table.get(key);
    }

    protected Enumeration
    elements() {
        return table.elements();
    }

    /**
     * Close all session queues in this read queue table.
     */
    protected void closeAll() {
        try {
            synchronized (table) {
                Iterator it = table.values().iterator();
                SessionQueue sq = null;

                while ( it.hasNext() ) {
                    sq = (SessionQueue) it.next();
                    sq.close();
                }
            }
        } catch (Exception e) {
            Debug.printStackTrace(e);
        }
    }

    /**
     * notify each queue in this table with a null pkt.
     */
    protected void notifyAllQueues() {
        try {
            synchronized (table) {
                Iterator it = table.values().iterator();
                SessionQueue sq = null;

                while ( it.hasNext() ) {
                    sq = (SessionQueue) it.next();
                    sq.enqueueNotify(null);
                }
            }
        } catch (Exception e) {
            Debug.printStackTrace(e);
        }
    }

}
