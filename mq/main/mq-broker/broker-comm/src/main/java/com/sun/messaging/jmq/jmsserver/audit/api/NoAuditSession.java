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
 */
public class NoAuditSession implements MQAuditSession {

    public boolean isAuditOn() {
        return false;
    }

    public void setInstance(String name, String host, int port) {
    }

    /**
     * Invoked post authentication.
     * @param user	user who is being authenticated
     * @param remoteHost host the user connects from
     * @param success	status of authentication
     */
    public void authentication(String user, String host, boolean success) {
    }

    /**
     * Invoked for the following events:
     *   broker startup
     *   broker shutdown
     *   broker restart
     *   remove instance
     */
    public void brokerOperation(String user, String host, String op) {
    }

    public void connectionAuth(String user, String host, String type,
                               String name, boolean success) {
    }

    public void destinationAuth(String user, String host, String type,
                                String name, String op, boolean success) {
    }

    public void storeOperation(String user, String host, String op) {
    }

    public void destinationOperation(
	String user, String host, String op, String type, String name) {
    }

    public void durableSubscriberOperation(
	String user, String host, String op, String name, String clientID) {
    }
}
