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
 */ 

package com.sun.messaging.bridge.admin.handlers;

import java.util.Hashtable;
import java.util.Properties;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.admin.BridgeServiceManagerImpl;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.admin.resources.BridgeManagerResources;

/**
 * handler for DEBUG message.
 */
public class DebugHandler extends AdminCmdHandler
{
    public DebugHandler(AdminMessageHandler parent, BridgeServiceManagerImpl bsm) {
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
                       throws BridgeException, Exception {
        int msgtype = msg.getIntProperty(AdminMessageType.PropName.MESSAGE_TYPE);
        if (msgtype != AdminMessageType.Type.DEBUG) {
           throw new BridgeException("Unexpected bridge admin message type "+
                                      AdminMessageType.getString(msgtype));
       }

       String debugArg = msg.getStringProperty(AdminMessageType.PropName.CMD_ARG);
       String target = msg.getStringProperty(AdminMessageType.PropName.TARGET);
       if (debugArg == null) {
           throw new BridgeException(_bmr.getKString(_bmr.X_ADMIN_DEBUG_NO_ARG));
       }
       if (!debugArg.trim().equals("fault")) {
           throw new BridgeException(_bmr.getKString(_bmr.X_ADMIN_DEBUG_UNSUPPORTED_ARG, debugArg));
       }
       if (target == null || target.trim().length() == 0) {
           throw new BridgeException(_bmr.getKString(_bmr.X_ADMIN_DEBUG_NO_NAME, debugArg));
       }
       Properties props = (Properties)msg.getObject();

       String faultName = target;
       String faultSelector = (String)props.getProperty("selector");
       FaultInjection fi = FaultInjection.getInjection();
       boolean faultOn = true;

       String enabledStr = props.getProperty("enabled");
       if (enabledStr != null && enabledStr.equalsIgnoreCase("false")) {
           fi.unsetFault(faultName);
       } else {
           fi.setFaultInjection(true);
           try {
               fi.setFault(faultName, faultSelector, props);
           } catch (Exception e) {
               _bc.logError(_bmr.getKString(_bmr.E_ADMIN_SET_FAULT_FAILED, faultName), e);
               throw e;
           }
       }
       parent.sendReply(session, msg, reply, Status.OK, (String)null, bmr);
   }

}
