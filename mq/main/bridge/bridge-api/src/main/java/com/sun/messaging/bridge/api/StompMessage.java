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

import java.util.Enumeration;

/**
 * This interface encapsulates all information needed to convert between a StompFrameMessage and a provider message
 * object
 *
 * @author amyk
 */
public interface StompMessage {

    /**************************
     * to StompFrameMessage
     ************************************/
    String getSubscriptionID() throws Exception;

    String getDestination() throws Exception;

    String getReplyTo() throws Exception;

    String getJMSMessageID() throws Exception;

    String getJMSCorrelationID() throws Exception;

    String getJMSExpiration() throws Exception;

    String getJMSRedelivered() throws Exception;

    String getJMSPriority() throws Exception;

    String getJMSTimestamp() throws Exception;

    String getJMSType() throws Exception;

    Enumeration getPropertyNames() throws Exception;

    String getProperty(String name) throws Exception;

    boolean isTextMessage() throws Exception;

    boolean isBytesMessage() throws Exception;

    // to be called only if isTextMessage() return true
    String getText() throws Exception;

    // to be called only if isBytesMessage() return true
    byte[] getBytes() throws Exception;

    /**************************
     * from StompFrameMessage
     ************************************/
    // either setText or setBytes (not both) to be called first
    void setText(StompFrameMessage message) throws Exception;

    void setBytes(StompFrameMessage message) throws Exception;

    void setDestination(String stompdest) throws Exception;

    void setPersistent(String v) throws Exception;

    void setReplyTo(String replyto) throws Exception;

    void setJMSCorrelationID(String v) throws Exception;

    void setJMSExpiration(String v) throws Exception;

    void setJMSPriority(String v) throws Exception;

    void setJMSType(String v) throws Exception;

    void setProperty(String name, String value) throws Exception;

}
