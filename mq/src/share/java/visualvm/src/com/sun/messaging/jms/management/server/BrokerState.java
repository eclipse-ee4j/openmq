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
 * @(#)BrokerState.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on broker states.
 */
public class BrokerState implements java.io.Serializable  {
    /** 
     * Unknown broker state.
     */
    public static final int	     UNKNOWN			= -1;

    /**
     * A broker has started and is operating normally.
     */
    public static final int          OPERATING			= 0;

    /**
     * The broker has started to takeover another broker's message store.
     * This applies to brokers that are part of a HA cluster.
     * 
     */
    public static final int          TAKEOVER_STARTED		= 1;

    /**
     * The broker has completed the takeover another broker's message store.
     * This applies to brokers that are part of a HA cluster.
     */
    public static final int          TAKEOVER_COMPLETE		= 2;

    /**
     * The broker has failed in the attempt to takeover another broker's message store.
     * This applies to brokers that are part of a HA cluster.
     */
    public static final int          TAKEOVER_FAILED		= 3;

    /**
     * The broker has started to quiesce.
     */
    public static final int          QUIESCE_STARTED		= 4;

    /**
     * The broker has finished quiescing.
     */
    public static final int          QUIESCE_COMPLETE		= 5;

    /**
     * The broker is starting to shutdown (either immediately or after a specific grace
     * period) or restart
     */
    public static final int          SHUTDOWN_STARTED		= 6;

    /**
     * The broker is down.
     */
    public static final int          BROKER_DOWN		= 7;

    /*
     * Class cannot be instantiated
     */
    private BrokerState() {
    }

    public static String toString(int state)  {
	switch (state)  {
	case OPERATING:
	    return ("OPERATING");

	case TAKEOVER_STARTED:
	    return ("TAKEOVER_STARTED");

	case TAKEOVER_COMPLETE:
	    return ("TAKEOVER_COMPLETE");

	case TAKEOVER_FAILED:
	    return ("TAKEOVER_FAILED");

	case QUIESCE_STARTED:
	    return ("QUIESCE_STARTED");

	case QUIESCE_COMPLETE:
	    return ("QUIESCE_COMPLETE");

	case SHUTDOWN_STARTED:
	    return ("SHUTDOWN_STARTED");

	case BROKER_DOWN:
	    return ("BROKER_DOWN");

	default:
	    return ("UNKNOWN");
	}
    }
    
}
