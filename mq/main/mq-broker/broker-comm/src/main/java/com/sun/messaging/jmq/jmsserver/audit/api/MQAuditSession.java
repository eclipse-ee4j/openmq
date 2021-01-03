/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

    String QUEUE = "queue";
    String TOPIC = "topic";

    // actions
    String BROKER_STARTUP = "broker startup";
    String BROKER_SHUTDOWN = "broker shutdown";
    String BROKER_RESTART = "broker restart";
    String AUTHENTICATION = "authentication";
    String AUTHORIZATION = "authorization";
    String REMOVE_INSTANCE = "remove instance";
    String RESET_STORE = "reset store";
    String CREATE_DESTINATION = "create destination";
    String PURGE_DESTINATION = "purge destination";
    String DESTROY_DESTINATION = "destroy destination";
    String DESTROY_DURABLE = "destroy durable subscriber";

    // destination operations
    String CREATE = "create";
    String PRODUCE = "produce";
    String CONSUME = "consume";
    String BROWSE = "browse";

    boolean isAuditOn();

    void setInstance(String name, String host, int port);

    /**
     * Invoked post authentication.
     *
     * @param user user who is being authenticated
     * @param remoteHost host the user connects from
     * @param success status of authentication
     */
    void authentication(String user, String host, boolean success);

    /**
     * Invoked for the following events: broker startup broker shutdown broker restart remove instance
     */
    void brokerOperation(String user, String host, String op);

    void connectionAuth(String user, String host, String type, String name, boolean success);

    void destinationAuth(String user, String host, String type, String name, String op, boolean success);

    void storeOperation(String user, String host, String op);

    void destinationOperation(String user, String host, String op, String type, String name);

    void durableSubscriberOperation(String user, String host, String op, String name, String clientID);
}
