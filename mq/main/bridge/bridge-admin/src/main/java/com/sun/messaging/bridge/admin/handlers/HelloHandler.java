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

package com.sun.messaging.bridge.admin.handlers;

import javax.jms.Session;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.admin.BridgeServiceManagerImpl;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.bridge.admin.resources.BridgeManagerResources;

public class HelloHandler extends AdminCmdHandler
{

    public HelloHandler(AdminMessageHandler parent, BridgeServiceManagerImpl bsm) {
        super(parent, bsm);
    }

    /**
     * When called, parent has set reply message type property
     *
     * throw exception if let parent handle sendReply 
     */
    public void handle(Session session, 
                       ObjectMessage msg, ObjectMessage reply,
                       BridgeManagerResources bmr)
                       throws BridgeException,JMSException, Exception {

        int msgtype = msg.getIntProperty(AdminMessageType.PropName.MESSAGE_TYPE);
        if (msgtype != AdminMessageType.Type.HELLO) {
           throw new BridgeException("Unexpected bridge admin message type "+
                                      AdminMessageType.getString(msgtype));
       }
       throw new BridgeException("Internal Error: unexpected call "+ AdminMessageType.getString(msgtype));

       //parent.sendReply(session, msg, reply, Status.OK, (String)null, bmr);
    }

}
