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

package com.sun.messaging.jmq.httptunnel.tunnel.servlet;

class ConnKey {
    private String serverName;
    private int connId = -1;

    ConnKey(String serverName, int connId) {
        this.serverName = serverName;
        this.connId = connId;
    }

    /** @throws NumberFormatException */
    ConnKey(String serverName, String connIdStr) {
        this.serverName = serverName;
        this.connId = Integer.parseInt(connIdStr);
    }

    public String getServerName() {
        return serverName;
    }

    public int getConnId() {
        return connId;
    }

    @Override
    public int hashCode() {
        return (serverName.hashCode() + connId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        ConnKey key = (ConnKey) obj;

        return ((key.getServerName().equals(this.serverName)) && (key.getConnId() == this.connId));
    }
}

