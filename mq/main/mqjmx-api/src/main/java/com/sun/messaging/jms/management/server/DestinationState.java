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
 * @(#)DestinationState.java	1.4 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on destination states.
 */
public class DestinationState {
    /** 
     * Unknown destination state.
     */
    public static final int UNKNOWN = -1;

    /** 
     * Destination is active.
     */
    public static final int RUNNING = 0;

    /** 
     * Message delivery to consumers is paused.
     */
    public static final int CONSUMERS_PAUSED = 1;

    /** 
     * Message delivery from producers is paused.
     */
    public static final int PRODUCERS_PAUSED = 2;

    /** 
     * Message delivery from producers and to consumers
     * is paused.
     */
    public static final int PAUSED = 3;

    /*
     * Class cannot be instantiated
     */
    private DestinationState() {
    }
    
    /**
     * Returns a string representation of the specified destination state.
     *
     * @param state Destination state.
     * @return String representation of the specified destination state.
     */
    public static String toString(int state)  {

	switch (state)  {
	case RUNNING:
	    return("Running");

	case CONSUMERS_PAUSED:
	    return("Consumers Paused");

	case PRODUCERS_PAUSED:
	    return("Producers Paused");

	case PAUSED:
	    return("Paused");

	default:
	    return("unknown");
	}
    }
}
