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

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.ObjectMessage;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.api.BridgeBaseContext;
import com.sun.messaging.bridge.admin.BridgeServiceManagerImpl;
import com.sun.messaging.bridge.admin.resources.BridgeManagerResources;
import com.sun.messaging.jmq.io.Status;

public class AdminCmdHandler
{

    protected AdminMessageHandler parent = null;

    protected BridgeServiceManagerImpl _bsm = null;
    protected BridgeBaseContext _bc = null;
    protected BridgeManagerResources _bmr = null;

    public AdminCmdHandler(AdminMessageHandler parent, BridgeServiceManagerImpl bsm) {

        this.parent = parent;
        _bsm = bsm;
        _bc = bsm.getBridgeBaseContext();
        _bmr = bsm.getBridgeManagerResources();
    }

    /**
     * When called, parent has set reply message type property
     *
     * throw exception if let parent handle sendReply 
     */
    public void handle(Session session, 
                       ObjectMessage cmdmsg, ObjectMessage reply, BridgeManagerResources bmr)
                       throws BridgeException, JMSException, Exception {

        parent.sendReply(session, cmdmsg, reply, Status.NOT_IMPLEMENTED, 
                         Status.getString(Status.NOT_IMPLEMENTED), bmr);
    }

    public static String getMessageFromThrowable(Throwable t) {
	    String m = t.getMessage();

        Throwable cause = null;
        if (t instanceof JMSException) {
            cause = ((JMSException)t).getLinkedException();
        } else {
            cause = t.getCause();
        }

        if (cause == null)  return m;

        String cm = cause.getMessage();
        if (cm == null) return m;

        return  (m + "\n" + cm);
    }

}
