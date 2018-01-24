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

package com.sun.messaging.jmq.jmsserver;

import java.util.*;
import java.util.concurrent.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQEmbeddedService;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsservice.DirectBrokerConnection;
import com.sun.messaging.jmq.jmsservice.JMSDirectBroker;
import com.sun.messaging.jmq.io.*; //test program only

/**
 * DirectBrokerProcess implementation. It wraps a singleton class
 * (only one broker can be running in any process).<P>
 *
 * <u>Example</u><P>
 * <code><PRE>
 *      DirectBrokerProcess bp = BrokerProcess.getBrokerProcess(BrokerProcess.DIRECT_BROKER);
 *      try {
 *      
 *          Properties ht = BrokerProcess.convertArgs(args);
 *          int exitcode = bp.start();
 *          if (exitcode != 0) { // failure to start
 *              System.out.println("Broker exited with " + exitcode);
 *          }
 *
 *      } catch (IllegalArgumentException ex) {
 *          System.err.println("Bad Argument " + ex.getMessage());
 *          System.out.println(BrokerProcess.usage());
 *      }
 * </PRE></code>
 */
public class DirectBrokerProcess extends BrokerProcess implements JMSDirectBroker
{
    String name = "mqdirect";
    public DirectBrokerProcess() {
        super();
    }


    public int start(boolean inProcess, 
        Properties properties, BrokerEventListener bel, 
        boolean initOnly,  Throwable failStartThrowable)
        throws OutOfMemoryError, IllegalStateException, IllegalArgumentException {

        if (properties == null) {
             properties = new Properties();
        }
        properties.put(Globals.IMQ + ".service.runtimeAdd", name);
        return super.start(inProcess, properties, bel, initOnly, failStartThrowable);
    }

    /**
     * Returns true when the broker is ready to start processing messages
     */
    public boolean directServiceIsUp() {
        IMQService service = (IMQService)Globals.getServiceManager().getService(name);
        if (service == null) return false;
        return service.getState() == ServiceState.RUNNING;
    }

    public DirectBrokerConnection getConnection() {
        IMQEmbeddedService service = (IMQEmbeddedService)Globals.getServiceManager().getService(name);
        try {
            return service.createConnection();
        } catch (Exception ex) {
            Globals.getLogger().logStack(Logger.WARNING, "L10N-XXX: Unable to create connection", ex);
        }
        return null;
    }

}


