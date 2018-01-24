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
 * @(#)BrokerErrorEvent.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.event;

import java.util.EventObject;

/**
 * Events related to reporting any kind of unexpected
 * errors and/or exceptions.
 */
public class BrokerErrorEvent extends AdminEvent {
    /*
     * Error type
     */
    public final static int	UNEXPECTED_SHUTDOWN	= 0;
    public final static int	ALT_SHUTDOWN		= 1;
    public final static int	CONNECTION_ERROR	= 2;

    private String brokerHost = null;
    private String brokerPort = null;
    private String brokerName = null;

    /**
     * Creates an instance of BrokerErrorEvent
     * @param source the object where the event originated
     */
    public BrokerErrorEvent(Object source, int type) {
	super(source, type);
    }

    public String getBrokerHost() {
	return brokerHost;
    }

    public void setBrokerHost(String host) {
	this.brokerHost = host;
    }

    public String getBrokerPort() {
	return brokerPort;
    }

    public void setBrokerPort(String port) {
	this.brokerPort = port;
    }

    public String getBrokerName() {
	return brokerName;
    }

    public void setBrokerName(String name) {
	this.brokerName = name;
    }
}
