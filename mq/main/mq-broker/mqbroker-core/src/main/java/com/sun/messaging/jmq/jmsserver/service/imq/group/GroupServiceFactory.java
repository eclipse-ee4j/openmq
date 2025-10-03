/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service.imq.group;

import java.io.IOException;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;

public class GroupServiceFactory extends IMQIPServiceFactory {
    @Override
    public void checkFactoryHandlerName(String handlerName) throws IllegalAccessException {
        String myname1 = "shared_old";
        String myname2 = "group_old";
        if (!myname1.equals(handlerName) && !myname2.equals(handlerName)) {
            throw new IllegalAccessException("Unexpected service Handler name " + handlerName + ", expected " + myname1);
        }
    }

    @Override
    public Service createService(String instancename, int type) throws BrokerException {
        // see if we need to override properties
        if (!Globals.getConfig().getBooleanProperty(Globals.IMQ + "." + instancename + ".override")) {
            Globals.getConfig().put(Globals.IMQ + "." + instancename + ".tcp.blocking", "false");
            Globals.getConfig().put(Globals.IMQ + "." + instancename + ".tcp.useChannels", "true");
        } else {
            Globals.getLogger().log(Logger.DEBUG, "Overriding shared properties for instance " + instancename);
        }
        return super.createService(instancename, type);

    }

    @Override
    protected IMQService createService(String instancename, Protocol proto, PacketRouter router, int type, int min, int max) throws IOException {
        proto.configureBlocking(false);
        return new GroupService(instancename, proto, type, router, min, max);
    }

}
