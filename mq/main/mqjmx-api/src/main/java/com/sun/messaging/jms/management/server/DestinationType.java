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
 * @(#)DestinationType.java	1.5 07/02/07
 */ 

package com.sun.messaging.jms.management.server;

/**
 * Class containing information on destination types.
 */
public class DestinationType {
    /** 
     * Queue destination type
     */
    public static final String QUEUE	= "q";

    /** 
     * Topic destination type
     */
    public static final String TOPIC	= "t";

    /*
     * Class cannot be instantiated
     */
    private DestinationType() {
    }
    
    /**
     * Returns a human readable representation of the specified destination type.
     *
     * @param type Destination type
     * @return String representation of the specified destination type
     */
    public static String toStringLabel(String type)  {

	if (type.equals(QUEUE))  {
	    return("queue");
	} else if (type.equals(TOPIC))  {
	    return("topic");
	} else  {
	    return("unknown");
	}
    }
}
