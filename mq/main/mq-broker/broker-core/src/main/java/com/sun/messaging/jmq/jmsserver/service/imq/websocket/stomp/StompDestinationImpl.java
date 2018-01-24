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

package com.sun.messaging.jmq.jmsserver.service.imq.websocket.stomp;

import com.sun.messaging.jmq.jmsservice.Destination;
import com.sun.messaging.bridge.api.StompDestination;


/**
 * @author amyk 
 */
public class StompDestinationImpl implements StompDestination  {

    private Destination dest = null;
    private String stompdest = null;

    public StompDestinationImpl(Destination dest) {
        this.dest = dest;
    }

    @Override
    public boolean isQueue() {
        return (dest.getType() == Destination.Type.QUEUE);
    }

    @Override
    public boolean isTemporary() {
        return (dest.getLife() == Destination.Life.TEMPORARY);
    }

    @Override
    public String getName() throws Exception {
        return dest.getName();
    }

    protected Destination getDestination() {
        return dest;
    }

    protected void setStompDestinationString(String stompdest) {
        this.stompdest = stompdest;
    }

    protected String getStompDestinationString() {
        return stompdest;
    }
}
