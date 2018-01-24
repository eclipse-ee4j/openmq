/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.audit.api;

/**
 * The AuditSession interface defines a generic interface for
 * generating audit records for important management events.
 * Underlying audit session implementation objects exporting this
 * interface to perform specific types of auditing.
 * <p>
 * An audit session is created by calling a get method in the
 * audit service factory, a static factory class which
 * creates platform specific implementation class instances of
 * the audit session based upon installation time configuration
 * parameters.
 *
 */

import org.jvnet.hk2.annotations.Contract;
import org.glassfish.hk2.api.PerLookup;

/**
 */
@Contract
@PerLookup
public interface MQAuditSession {

    public static final String QUEUE = "queue";
    public static final String TOPIC = "topic";

    // actions
    public static final String BROKER_STARTUP = "broker startup";
    public static final String BROKER_SHUTDOWN = "broker shutdown";
    public static final String BROKER_RESTART = "broker restart";
    public static final String AUTHENTICATION = "authentication";
    public static final String AUTHORIZATION = "authorization";
    public static final String REMOVE_INSTANCE = "remove instance";
    public static final String RESET_STORE = "reset store";
    public static final String CREATE_DESTINATION = "create destination";
    public static final String PURGE_DESTINATION = "purge destination";
    public static final String DESTROY_DESTINATION = "destroy destination";
    public static final String DESTROY_DURABLE = "destroy durable subscriber";

    // destination operations
    public static final String CREATE = "create";
    public static final String PRODUCE = "produce";
    public static final String CONSUME = "consume";
    public static final String BROWSE = "browse";

    public boolean isAuditOn(); 

    public void setInstance(String name, String host, int port); 

    /**
     * Invoked post authentication.
     * @param user	user who is being authenticated
     * @param remoteHost host the user connects from
     * @param success	status of authentication
     */
    public void authentication(String user, String host, boolean success);

    /**
     * Invoked for the following events:
     *   broker startup
     *   broker shutdown
     *   broker restart
     *   remove instance
     */
    public void brokerOperation(String user, String host, String op);

    public void connectionAuth(String user, String host, String type,
                               String name, boolean success);

    public void destinationAuth(String user, String host, String type,
                                String name, String op, boolean success);

    public void storeOperation(String user, String host, String op);

    public void destinationOperation(
	String user, String host, String op, String type, String name);

    public void durableSubscriberOperation(
	String user, String host, String op, String name, String clientID);
}
