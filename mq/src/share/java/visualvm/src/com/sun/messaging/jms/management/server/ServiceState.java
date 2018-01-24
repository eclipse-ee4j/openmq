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
 * @(#)ServiceState.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on service states.
 */
public class ServiceState {
    /** 
     * Unknown service state.
     */
    public static final int UNKNOWN = -1;

    /**
     * Service is up and running.
     */
    public static final int RUNNING = 0;

    /**
     * Service is paused.
     */
    public static final int PAUSED = 1;

    /**
     * Service is quiesced.
     */
    public static final int QUIESCED = 2;


    /*
     * Class cannot be instantiated
     */
    private ServiceState() {
    }
    
    /**
     * Returns a string representation of the specified service state.
     *
     * @param state Service state.
     * @return String representation of the specified service state.
     */
    public static String toString(int state)  {
        switch (state) {
            case RUNNING:
                return "RUNNING";

            case PAUSED:
                return "PAUSED";

            case QUIESCED:
                return "QUIESCED";

	    default:
                return "UNKNOWN";
        }
    }
}
