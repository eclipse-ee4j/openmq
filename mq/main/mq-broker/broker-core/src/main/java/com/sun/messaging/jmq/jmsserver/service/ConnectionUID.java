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
 * @(#)ConnectionUID.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

//import com.sun.messaging.jmq.util.*;

public class ConnectionUID extends com.sun.messaging.jmq.util.UID {

    static final long serialVersionUID =2047382500516681566L;

    private transient boolean canReconnect = false;

    public boolean getCanReconnect() {
        return canReconnect;
    }
    public void setCanReconnect(boolean reconnect) {
        canReconnect = reconnect;
    } 

    public ConnectionUID() {
        // Allocates a new id
        super();
    }

    public ConnectionUID(long id) {
        // Wraps an existing id
        super(id);
    }


    public String toString() {
        return super.toString();
    }
}
