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

package com.sun.messaging.jmq.httptunnel.tunnel;

import java.util.*;

class HttpTunnelTimerTask extends TimerTask {
    HttpTunnelConnection conn = null;
    int seq = 0;

    HttpTunnelTimerTask(HttpTunnelConnection conn, int seq) {
        this.conn = conn;
        this.seq = seq;
    }

    @Override
    public void run() {
        conn.retransmitPacket(seq, true);
    }
}

