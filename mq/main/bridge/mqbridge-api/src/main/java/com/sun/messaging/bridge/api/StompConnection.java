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

package com.sun.messaging.bridge.api;

import com.sun.messaging.bridge.api.StompProtocolHandler.StompAckMode;

/**
 * @author amyk
 */
public interface StompConnection {

    /**
     * @return the connection id
     */
    String connect(String login, String passcode, String clientid) throws Exception;

    void disconnect(boolean closeCheck) throws Exception;

    void sendMessage(StompFrameMessage message, String tid) throws Exception;

    StompSubscriber createSubscriber(String subid, String stompdest, StompAckMode ackMode, String selector, String duraname, boolean nolocal, String tid,
            StompOutputHandler out) throws Exception;

    void ack(String id, String tid, String subid, String msgid, boolean nack) throws Exception;

    // for STOMP protocol 1.0 only
    void ack10(String subidPrefix, String msgid, String tid) throws Exception;

    /**
     * @return subid if duraname not null
     */
    String closeSubscriber(String subid, String duraname) throws Exception;

    void beginTransactedSession(String tid) throws Exception;

    void commitTransactedSession(String tid) throws Exception;

    void abortTransactedSession(String tid) throws Exception;
}
