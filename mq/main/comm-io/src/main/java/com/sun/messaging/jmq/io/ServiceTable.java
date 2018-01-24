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
 * @(#)ServiceTable.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.io;

import java.io.*;
import java.util.*;

/**
 * A table of ServiceEntries.
 */
public class ServiceTable {
    //private static boolean DEBUG = false;

    private String brokerInstanceName = "???";
    private String brokerVersion = "???";

    private Hashtable remoteServices = null;
    private String activeBroker = null;
    private Hashtable  table = null;

    public ServiceTable() {
        remoteServices = new Hashtable();
        table = new Hashtable();
    }

    /**
     * Add a service.
     */
    public void add(ServiceEntry e) {
        table.remove(e.getName());
        table.put(e.getName(), e);
    }

    /**
     * Find a service.
     */
    public ServiceEntry get(String name) {
        return (ServiceEntry) table.get(name);
    }

    /**
     * Get a hashtable containing the service name / ServiceEntry
     * pairs.
     */
    public Hashtable getServices() {
        return table;
    }

    /**
     * Get the address string for a particular service.
     *
     * @param service   Name of service to get port number for
     * @return         address string, or null if the port for
     * service is not known.
     */
    public String getServiceAddress(String service) {
        ServiceEntry se = (ServiceEntry)table.get(service);
        if (se == null) {
            return null;
        } else {
            return se.getAddress();
        }
    }

    /**
     * Get the address string for a particular service.
     *
     * @param type Service type.
     * @param protocol   Service protocol.
     * @return         address string, or null if the port for
     * service is not known.
     */
    public String getServiceAddress(String type, String protocol) {
        String addr = null;
        Enumeration e = table.elements();
        while (e.hasMoreElements()) {
            ServiceEntry se = (ServiceEntry) e.nextElement();
            if (se.getProtocol().equals(protocol) &&
                se.getType().equals(type)) {
                addr = se.getAddress();
                break;
            }
        }
        return addr;
    }

    /**
     * Remove a service.
     */
    public void remove(String name) {
        table.remove(name);
    }

    /**
     * Remove a service. 
     *
     * @param e ServiceEntry to remove.
     */
    public void remove(ServiceEntry e) {
        table.remove(e.getName());
    }

    /**
     * Set the broker instance name
     */
    public void setBrokerInstanceName(String brokerInstanceName) {
        this.brokerInstanceName = brokerInstanceName;
    }

    /**
     * Get the broker instance name
     */
    public String getBrokerInstanceName() {
        return brokerInstanceName;
    }

    /**
     * Set the broker version.
     */
    public void setBrokerVersion(String brokerVersion) {
        this.brokerVersion = brokerVersion;
    }

    /**
     * Get the broker version.
     */
    public String getBrokerVersion() {
        return brokerVersion;
    }

    /**
     * Add a remote service.
     */
    public void addRemoteService(String address) {
        remoteServices.put(address, address);
    }

    /**
     * Remove a remote service.
     */
    public void removeRemoteService(String address) {
        remoteServices.remove(address);
    }

    /**
     * Get the remote service list iterator.
     */
    public Hashtable getRemoteServices() {
        return remoteServices;
    }

    /**
     * Set the active broker address.
     */
    public void setActiveBroker(String address) {
        this.activeBroker = address;
    }

    /**
     * Get the active broker address.
     */
    public String getActiveBroker() {
        return activeBroker;
    }

    public void dumpServiceTable() {
        System.out.println("brokerInstanceName = " + brokerInstanceName);
        System.out.println("brokerVersion = " + brokerVersion);

        System.out.println("active broker = " + activeBroker);

        System.out.println("Remote Services :");
        Enumeration e = remoteServices.keys();
        while (e.hasMoreElements())
            System.out.println("\t" + (String) e.nextElement());

        System.out.println("Local Services :");
        e = table.elements();
        while (e.hasMoreElements())
            System.out.println("\t" +
                ((ServiceEntry) e.nextElement()).toString());
    }
}

/*
 * EOF
 */
