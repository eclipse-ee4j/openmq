/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.io.*;
import com.sun.messaging.jmq.jmsserver.util.*;

class ServiceInfo {
    Service service;
    ServiceFactory handler;

    ServiceInfo(Service service, ServiceFactory handler) {
        this.service = service;
        this.handler = handler;
    }

    public Service getService() {
        return service;
    }

    public ServiceFactory getServiceFactory() {
        return handler;
    }

    public int getState() {
        return service.getState();
    }

    public int getServiceType() {
        return service.getServiceType();
    }

    public void start(boolean pauseAtStart) throws BrokerException {
        handler.updateService(service);
        handler.startMonitoringService(service);
        service.startService(pauseAtStart);
    }

    public void stop(boolean all) throws BrokerException {
        service.stopService(all);
        handler.stopMonitoringService(service);
    }

    public void pause(boolean all) throws BrokerException {

        service.pauseService(all);
    }

    public void stopNewConnections() {

        try {
            service.stopNewConnections();
        } catch (IOException ex) {
        }
    }

    public void startNewConnections() {

        try {
            service.startNewConnections();
        } catch (IOException ex) {
        }
    }

    public void resume() throws BrokerException {

        service.resumeService();
    }

    public void destroy() throws BrokerException {

        stop(true);
        service.destroyService();
    }
}
