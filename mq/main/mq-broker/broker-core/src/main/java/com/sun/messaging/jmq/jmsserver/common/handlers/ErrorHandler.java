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
 * @(#)ErrorHandler.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.common.handlers;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.data.PacketHandler;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.service.Connection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;



/**
 * Handler class which deals with handling error messages
 */
public class ErrorHandler extends PacketHandler 
{

    /**
     * Method to handle error messages
     */
    public boolean handle(IMQConnection con, Packet msg) 
        throws BrokerException
    {
         Exception ex = new Exception();
         ex.fillInStackTrace();
         Globals.getLogger().logStack(Logger.ERROR,
             BrokerResources.E_INTERNAL_BROKER_ERROR, 
             "received unexpected error in handler ", ex);
         return true;
    }

}
