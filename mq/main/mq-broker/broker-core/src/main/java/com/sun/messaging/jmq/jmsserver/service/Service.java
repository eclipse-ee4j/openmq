/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;
import java.io.*;

/**
 * This interface abstracts the basic methods for sending and receiving data from a client
 * <P>
 *
 * A service will generally have some sort of socket it uses to talk to the outside world, and a threading scheme to
 * handle reading in messages from clients and sending messages back out to clients
 *
 * Each service will also implement its own Connection interface.
 */

public interface Service {

    String getName();

    Hashtable getDebugState();

    int getState();

    int getServiceType();

    List getProducers();

    List getConsumers();

    int size();

    /**
     * start the service running
     */
    void startService(boolean startPaused);

    /**
     * stop and destroy the service
     *
     * @param all if false, disallow new connections only
     */
    void stopService(boolean all);

    /**
     * stop allowing new connections
     */
    void stopNewConnections() throws IOException;

    /**
     * allowing new connections
     */
    void startNewConnections() throws IOException;

    /**
     * pause the service
     *
     * @param all if true, connections as well as the service should be paused
     */
    void pauseService(boolean all);

    /**
     * resume a paused service
     */
    void resumeService();

    /**
     * destroy a stopped service
     */
    void destroyService();

    /**
     * cleans up a connection of the service
     */
    void removeConnection(ConnectionUID con, int reason, String str);

    /**
     * add a service restriction
     */
    void addServiceRestriction(ServiceRestriction sr);

    /**
     * remove a service restriction
     */
    void removeServiceRestriction(ServiceRestriction sr);

    /**
     * get all service restrictions
     */
    ServiceRestriction[] getServiceRestrictions();

    void addServiceRestrictionListener(ServiceRestrictionListener l);

    void removeServiceRestrictionListener(ServiceRestrictionListener l);
}
