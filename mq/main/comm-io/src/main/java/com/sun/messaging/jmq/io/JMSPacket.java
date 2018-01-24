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
 * @(#)JMSPacket.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.io;

/**
 *  The interface definition for the Type used when exchanging JMS Message data
 *  via the JMSService interface.
 */
public interface JMSPacket {
    
    /**
     *  Return the Sun MQ Packet.<p>
     *  The Packet is the fundamental unit of message data that is persisted to
     *  stable storage.
     * 
     * 
     *  @return The Packet
     */
    public com.sun.messaging.jmq.io.Packet getPacket();
    
}
