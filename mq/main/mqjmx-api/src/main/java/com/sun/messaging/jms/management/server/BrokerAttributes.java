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
 * @(#)BrokerAttributes.java	1.9 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on broker attributes.
 */
public class BrokerAttributes {
    /** 
     * Broker ID
     */
    public static final String		BROKER_ID = "BrokerID";

    /** 
     * Broker version.
     */
    public static final String		VERSION = "Version";

    /** 
     * Broker instance name.
     */
    public static final String		INSTANCE_NAME = "InstanceName";

    /** 
     * Broker memory level
     */
    public static final String		RESOURCE_STATE = "ResourceState";

    /** 
     * Broker port.
     */
    public static final String		PORT = "Port";

    /** 
     * Broker host.
     */
    public static final String		HOST = "Host";

    /** 
     * Whether broker is embedded (running in process) or not.
     */
    public static final String		EMBEDDED = "Embedded";

    /*
     * Class cannot be instantiated
     */
    private BrokerAttributes() {
    }
    
}
