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
 * @(#)DurableInfo.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

import java.util.Map;

/**
 * DurableInfo encapsulates information about a JMQ Durable subscription.
 * It is used to pass this information between the Broker and an
 * administration client.
 *
 * This class has no updateable fields. The admin client should consider
 * it "read-only"
 */
public class DurableInfo implements java.io.Serializable {

    static final long serialVersionUID = 2435222814345146809L;

    public String	name;
    public String	clientID;
    public int		nMessages;
    public boolean      isActive;
    public boolean isDurable = false;
    public boolean isShared = false;
    public boolean isJMSShared = false;
    public int activeCount = 0;
    public String uidString = null;
    public Map<String, ConsumerInfo> activeConsumers = null;

    public ConsumerInfo consumer;

    /**
     * Constructor for Durable.
     *
     */
    public DurableInfo() {
	reset();
    }

    public void reset() {
	name = null;
	clientID = null;
	nMessages = 0;
        isActive = false;
	consumer = null;
        isDurable = false;
        isShared = false;
        isJMSShared = false;
        uidString = null;
        activeConsumers = null;
    }

    /**
     * Return a string representation of the durable subscription. 
     *
     * @return String representation of durable subscription.
     */
    public String toString() {
	return clientID + "/" + name + ": " + consumer;
    }
}
