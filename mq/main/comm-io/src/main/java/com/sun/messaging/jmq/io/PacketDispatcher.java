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

package com.sun.messaging.jmq.io;

import javax.jms.JMSException;

/**
 * This interface encapsulates the client-side processing of incoming packets from the broker
 * 
 * This behaviour had been encapsulated in this interface because in certain direct mode client implementations 
 * it needs to be invoked from the broker rather than from the client, and it is cleaner to pass this interface over 
 * to the broker rather than its client-side implementation
 *
 */
public interface PacketDispatcher {
	
	/**
	 * Process a packet that has been received by the client.
	 * 
	 * If this is a message, dispatch it to the appropriate session(s)
	 * 
	 * @param pkt
	 * @throws JMSException
	 */
	  public void dispatch(ReadWritePacket pkt) throws JMSException;

}

