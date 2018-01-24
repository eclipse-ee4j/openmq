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

package com.sun.messaging.bridge.api;

import java.util.Enumeration;

/**
 * This interface encapsulates all information needed to convert  
 * between a StompFrameMessage and a provider message object 
 *
 * @author amyk 
 */
public interface StompMessage  {

    /**************************
     * to StompFrameMessage
     ************************************/
    public String getSubscriptionID() throws Exception; 
    public String getDestination() throws Exception; 
    public String getReplyTo() throws Exception;
    public String getJMSMessageID() throws Exception;
    public String getJMSCorrelationID() throws Exception;
    public String getJMSExpiration() throws Exception;
    public String getJMSRedelivered() throws Exception;
    public String getJMSPriority() throws Exception;
    public String getJMSTimestamp() throws Exception; 
    public String getJMSType() throws Exception;
    public Enumeration getPropertyNames() throws Exception;
    public String getProperty(String name) throws Exception;
    public boolean isTextMessage() throws Exception; 
    public boolean isBytesMessage() throws Exception; 
    //to be called only if isTextMessage() return true
    public String getText() throws Exception;
    //to be called only if isBytesMessage() return true
    public byte[] getBytes() throws Exception;

    /**************************
     * from StompFrameMessage
     ************************************/
    //either setText or setBytes (not both) to be called first
    public void setText(StompFrameMessage message) throws Exception;
    public void setBytes(StompFrameMessage message) throws Exception;
    public void setDestination(String stompdest) throws Exception;
    public void setPersistent(String v) throws Exception;
    public void setReplyTo(String replyto) throws Exception;
    public void setJMSCorrelationID(String v) throws Exception;
    public void setJMSExpiration(String v) throws Exception;
    public void setJMSPriority(String v) throws Exception;
    public void setJMSType(String v) throws Exception;
    public void setProperty(String name, String value) throws Exception;

}
