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
 * @(#)GroupServiceFactory.java	1.10 06/29/07
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
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsserver.license.LicenseBase;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.resources.*;


public class GroupServiceFactory extends IMQIPServiceFactory
{
    private static boolean SHARED_ALLOWED = false;

    static {
        try {
            LicenseBase license = Globals.getCurrentLicense(null);
            SHARED_ALLOWED =license.getBooleanProperty(
                       license.PROP_ENABLE_SHAREDPOOL, false);

        } catch (BrokerException ex) {
            SHARED_ALLOWED = false;
        }
    }

    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname1 = "shared_old";
        String myname2 = "group_old";
        if (!myname1.equals(handlerName) && !myname2.equals(handlerName)) {
            throw new IllegalAccessException(
            "Unexpected service Handler name "+handlerName+", expected "+myname1);
        }
    }

    public Service createService(String instancename, int type) 
        throws BrokerException
    {
        // see if we need to override properties
        if (!SHARED_ALLOWED) {

            Globals.getLogger().log(Logger.ERROR,
               BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
               Globals.getBrokerResources().getString(
                    BrokerResources.M_SHARED_THREAD_POOL)); 
            Broker.getBroker().exit(1,
               Globals.getBrokerResources().getKString(
                   BrokerResources.E_FATAL_FEATURE_UNAVAILABLE,
                   Globals.getBrokerResources().getString(
                        BrokerResources.M_SHARED_THREAD_POOL)),
               BrokerEvent.Type.FATAL_ERROR);
        }

        if (!Globals.getConfig().getBooleanProperty(Globals.IMQ +
                "." + instancename + ".override")) {
            Globals.getConfig().put(Globals.IMQ +
                "." + instancename + ".tcp.blocking", "false");
            Globals.getConfig().put(Globals.IMQ +
                "." + instancename + ".tcp.useChannels", "true");
        } else {
            Globals.getLogger().log(Logger.DEBUG,"Overriding shared properties for instance " + instancename);
       }
       return super.createService(instancename, type);

    }



    protected IMQService createService(String instancename, 
           Protocol proto, PacketRouter router, int type, 
           int min, int max)
        throws IOException
    {
        proto.configureBlocking(false);
        return new GroupService(instancename, proto, 
              type, router,  min, max);
    }

    

}
