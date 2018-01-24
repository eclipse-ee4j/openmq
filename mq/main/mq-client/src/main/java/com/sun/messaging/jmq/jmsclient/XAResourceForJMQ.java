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

package com.sun.messaging.jmq.jmsclient;

import javax.jms.JMSException;

/**
 * This interface defines certain method common to the various XAResource implementations supported by MQ
 */
public interface XAResourceForJMQ {
	
    /**
     * Return whether this XAResourceForJMQ and the specified XAResourceForJMQ
     * represent the same resource manager instance.
     * 
     * This is determined by checking whether the two resources 
     * have the same brokerSessionID
     *
     * @param xaResource XAResourceForJMQ  
     * @return true if same RM instance, otherwise false.
     */  
    public boolean isSameJMQRM(XAResourceForJMQ xaResource);

    /**
     * Return the brokerSessionID of this object's connection
     * @return
     */
	public long getBrokerSessionID();

	/**
	 * Return whether this XAResource is in the COMPLETE state.
	 * This state is reached when end(TMSUCCESS) is called
	 * 
	 * @return
	 */
	public boolean isComplete();

	/**
	 * Clean up the state of this object following a commit or rollback,
	 * ready for it to be used again if necessary
	 * @throws JMSException 
	 */ 
	public void clearTransactionInfo() throws JMSException;
	
}
