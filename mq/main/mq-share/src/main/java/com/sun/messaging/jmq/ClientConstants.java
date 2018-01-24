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
 * @(#)ClientConstants.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq;

/**
 * <code>ClientConstants</code> encapsulates JMQ specific
 * constant definitions and static strings used by the client that
 * need to be shared with the admin.
 */
public class ClientConstants {

    /* No public constructor needed */
    private ClientConstants(){}

    /** The connection type indicator for NORMAL connections */
    public static final String CONNECTIONTYPE_NORMAL = "NORMAL";
 
    /** The connection type indicator for ADMIN connections */
    public static final String CONNECTIONTYPE_ADMIN = "ADMIN";
 
    /** The connection type indicator for ADMINKEY connections */
    public static final String CONNECTIONTYPE_ADMINKEY = "ADMINKEY";
 
    /** The URI prefix for a temporary destination name */
    public static final String TEMPORARY_DESTINATION_URI_PREFIX = "temporary_destination://";

    /** The URI component for a temporary queue */
    public static final String TEMPORARY_QUEUE_URI_NAME = "queue/";

    /** The URI component for a temporary topic */
    public static final String TEMPORARY_TOPIC_URI_NAME = "topic/";

    /** The Destination Type value for an Unknown Destination */
    public static final int DESTINATION_TYPE_UNKNOWN = 0;

    /** The Destination Type value for a Queue Destination */
    public static final int DESTINATION_TYPE_QUEUE = 1;

    /** The Destination Type value for a Topic Destination */
    public static final int DESTINATION_TYPE_TOPIC = 2;

}
