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
 * @(#)ConsumerInfo.java	1.7 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

import com.sun.messaging.jmq.util.DestType;

/**
 * ConsumerInfo encapsulates information about a JMQ Consumer.
 * It is used to pass this information between the Broker and an
 * administration client.
 *
 * This class has no updateable fields. The admin client should consider
 * it "read-only"
 */
public class ConsumerInfo extends AdminInfo {

    static final long serialVersionUID = -3322006453099714245L;

    /**
     * Broker internal consumer ID
     */
    public byte[]	id;

    /**
     */
    public String uidString = null;

    /**
     * The subscription ID if there is one 
     */
    public String subuidString = null;

    /**
     */
    public String brokerAddressShortString = null;

    /**
     * Destination the consumer is registered on
     */
    public String	destination;

    /**
     * Type of destination. Set at creation only. Should be a combination
     * of bitmasks defined by DestType
     */
    public int		type;


    /**
     * Selector this consumer is using
     */
    public String	selector;

    /**
     * Information about the connection this consumer is on
     */
    public ConnectionInfo	connection;

    /**
     * Constructor for Consumer.
     */

    public ConsumerInfo() {
	reset();
    }

    public void reset() {
	id = null;
        type = 0;
        destination = null;
        selector = null;
	connection = null;
        uidString = null;
        subuidString = null;
        brokerAddressShortString = null;
    }

    /**
     * Return a string representation of the consumer.
     *
     * @return String representation of the consumer.
     */
    public String toString() {

	return "Consumer: destination=" + destination +
            ":" + DestType.toString(type) +" connection=" +
	    (connection == null? "remote consumer" :connection.toString());
    }

}
