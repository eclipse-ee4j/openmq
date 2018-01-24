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

package com.sun.messaging.bridge.service.jms;

import javax.jms.Connection;
/**
 *
 * @author amyk
 */
public abstract class PooledConnection {

    protected Connection _conn = null;
    private boolean _valid = true;
    private long _idletime = -1;

    public PooledConnection(Connection conn) {
        _conn = conn;
    }

    public boolean isValid() {
        return _valid;
    }

    public void invalid() {
        _valid = false;
    }

    public void idleStart() {
        _idletime = System.currentTimeMillis();
    }

    public void idleEnd() { 
        _idletime = 0;
    }        

    public long getIdleStartTime() {
        return _idletime;
    }

}
