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

package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

import com.sun.messaging.jmq.httptunnel.api.share.HttpTunnelDefaults;

import java.util.Vector;

@SuppressWarnings("JdkObsolete")
class Connection {
    private Vector pullQ = new Vector();
    private int pullPeriod = -1;
    private ServerLink link = null;
    private boolean inUse = false;
    private long lastRequestTime = 0;

    Connection(ServerLink link) {
        this.link = link;
        lastRequestTime = System.currentTimeMillis();
    }

    public Vector getPullQ() {
        return pullQ;
    }

    public int getPullPeriod() {
        return pullPeriod;
    }

    public synchronized void setInUse(boolean inUse) {
        this.inUse = inUse;

        if (inUse == false) {
            this.lastRequestTime = System.currentTimeMillis();
        }
    }

    public synchronized boolean checkConnectionTimeout(long now) {
        if (inUse) {
            return false;
        }

        long timeout = 0;

        if (pullPeriod > 0) {
            timeout = (pullPeriod * 5L);
        } else {
            timeout = HttpTunnelDefaults.DEFAULT_CONNECTION_TIMEOUT_INTERVAL;
        }

        timeout = timeout * 1000;

        return ((now - lastRequestTime) > timeout);
    }

    public ServerLink getServerLink() {
        return link;
    }

    public void setPullPeriod(int pullPeriod) {
        this.pullPeriod = pullPeriod;
    }
}

