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
 * @(#)ErrHandler.java	1.11 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.util.*;
/**
 * abstract class for classes which deal with handling specific
 * message types
 */
public abstract class ErrHandler  extends PacketHandler
{
    /**
     * method to handle processing the specific packet associated
     * with this PacketHandler
     */
    public abstract void sendError(IMQConnection con, Packet pkt, String emsg, int status);
    public abstract void sendError(IMQConnection con,
                                   boolean sendack, int pktype, long consumerID,
                                   String emsg, int status);
    public abstract void sendError(IMQConnection con, BrokerException ex, Packet pkt);

}
