/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq.group;

import java.util.*;
import java.io.*;
import java.nio.channels.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

@SuppressWarnings("JdkObsolete")
class ReadThread extends SelectThread {
    int selector_cnt = 0;

    ReadThread(Service svc, MapEntry entry) throws IOException {
        super(svc, entry);

        type = "read";
        INITIAL_KEY = SelectionKey.OP_READ; // none
        POSSIBLE_MASK = SelectionKey.OP_READ; // none
    }

    @Override
    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("TYPE", "ReadThread");
        ht.put("selector_cnt", Integer.valueOf(selector_cnt));
        return ht;
    }

    @Override
    protected void wakeup() {
        Selector s = selector;
        if (s != null) {
            s.wakeup();
        }
    }

    @Override
    protected void process() throws IOException {
        Selector s = selector;
        if (s == null) {
            throw new IOException("connection gone");
        }
        int cnt = 0;
        try {
            cnt = s.select(TIMEOUT);
        } catch (java.nio.channels.CancelledKeyException ex) {
            // bug 4944894
            // nio can throw the cancelledKeyException all the
            // way up in some cases, this does not indicate that
            // the selector is closed so the broker should ignore
            // the issue

            return;
        }
        if (cnt > 0) {
            Set keys = s.selectedKeys();
            Iterator keyitr = keys.iterator();
            while (keyitr.hasNext()) {
                SelectionKey key = (SelectionKey) keyitr.next();
                IMQIPConnection con = (IMQIPConnection) key.attachment();
                try {
                    con.readData();
                    // triggers bug 4708106
                    // if (result == Operation.PROCESS_WRITE_INCOMPLETE) {
                    keyitr.remove();
                    // }
                } catch (BrokerException ex) {
                    removeConnection(con, ex.getMessage());
                    keyitr.remove();
                } catch (IOException ex) {
                    String reason = (con.getDestroyReason() == null
                            ? (ex instanceof EOFException ? Globals.getBrokerResources().getKString(BrokerResources.M_CONNECTION_CLOSE) : ex.toString())
                            : con.getDestroyReason());
                    removeConnection(con, reason);
                    keyitr.remove();
                }
            }
        }
    }

}
