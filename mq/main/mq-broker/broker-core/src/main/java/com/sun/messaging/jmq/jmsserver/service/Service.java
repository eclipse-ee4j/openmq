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
 * @(#)Service.java	1.28 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;
import java.io.*;

/**
 * This interface abstracts the basic methods for sending
 * and receiving data from a client<P>
 *
 * A service will generally have some sort of socket it uses
 * to talk to the outside world, and a threading scheme to handle
 * reading in messages from clients and sending messages back out 
 * to clients
 *
 * Each service will also implement its own Connection interface.
 */

public interface Service
{

    public String getName();

    public Hashtable getDebugState();

    public int getState();

    public int getServiceType();

    public List getProducers();
    public List getConsumers();

    public int size();

    /**
     * start the service running
     */
    public void startService(boolean startPaused);

    /**
     * stop and destroy the service
     * @param all if false, disallow new connections only
     */
    public void stopService(boolean all);

    /**
     * stop allowing new connections
     */
    public void stopNewConnections() 
            throws IOException;

    /**
     * allowing new connections
     */
    public void startNewConnections() 
            throws IOException;

    /**
     * pause the service
     * @param all if true, connections as well as the service
     *            should be paused
     */
    public void pauseService(boolean all);

    /**
     * resume a paused service
     */
    public void resumeService();


    /**
     * destroy a stopped service
     */
    public void destroyService();

    /**
     * cleans up a connection of the service
     */
     public void removeConnection(ConnectionUID con, int reason, String str);


    /**
     * add a service restriction
     */
    public void addServiceRestriction(ServiceRestriction sr);

    /**
     * remove a service restriction
     */
    public void removeServiceRestriction(ServiceRestriction sr);

    /**
     * get all service restrictions
     */
    public ServiceRestriction[] getServiceRestrictions();

    public void addServiceRestrictionListener(ServiceRestrictionListener l);

    public void removeServiceRestrictionListener(ServiceRestrictionListener l);
}
