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
 * @(#)BrokerAdminUtil.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.ServiceState;

/**
 * Class containing useful methods for broker administration.
 */
public class BrokerAdminUtil {

    private static AdminResources ar = Globals.getAdminResources();

    public static String getDestinationType(int mask) {
        if (DestType.isTopic(mask))
            return ar.getString(ar.I_TOPIC);
        else if (DestType.isQueue(mask))
            return ar.getString(ar.I_QUEUE);
        else
            return ar.getString(ar.I_UNKNOWN);
    }

    public static String getDestinationFlavor(int mask) {
        if (DestType.isTopic(mask))
            return "-";
        else if (DestType.isSingle(mask))
            return ar.getString(ar.I_SINGLE);
        else if (DestType.isRRobin(mask))
            return ar.getString(ar.I_RROBIN);
        else if (DestType.isFailover(mask))
            return ar.getString(ar.I_FAILOVER);
        else if (DestType.isQueue(mask))
            return ar.getString(ar.I_SINGLE);  // This is the default
        else
            return ar.getString(ar.I_UNKNOWN);
    }
    
    public static String getDestinationState(int destState) {
    	switch (destState) {
    	
        case DestState.RUNNING:
            return ar.getString(AdminResources.I_DEST_STATE_RUNNING);

        case DestState.CONSUMERS_PAUSED:            
            return ar.getString(AdminResources.I_DEST_STATE_CONSUMERS_PAUSED);

        case DestState.PRODUCERS_PAUSED:
            return ar.getString(AdminResources.I_DEST_STATE_PRODUCERS_PAUSED);

        case DestState.PAUSED:
            return ar.getString(AdminResources.I_DEST_STATE_PAUSED);

    }
    return "UNKNOWN";
    }
    
    public static String getServiceState(int serviceState) {

		switch (serviceState) {
		case ServiceState.UNINITIALIZED:
			return ar.getString(AdminResources.I_SERVICE_STATE_UNINITIALIZED);

		case ServiceState.INITIALIZED:
			return ar.getString(AdminResources.I_SERVICE_STATE_INITIALIZED);

		case ServiceState.STARTED:
			return ar.getString(AdminResources.I_SERVICE_STATE_STARTED);

		case ServiceState.RUNNING:
			return ar.getString(AdminResources.I_SERVICE_STATE_RUNNING);

		case ServiceState.PAUSED:
			return ar.getString(AdminResources.I_SERVICE_STATE_PAUSED);

		case ServiceState.SHUTTINGDOWN:
			return ar.getString(AdminResources.I_SERVICE_STATE_SHUTTINGDOWN);

		case ServiceState.STOPPED:
			return ar.getString(AdminResources.I_SERVICE_STATE_STOPPED);

		case ServiceState.DESTROYED:
			return ar.getString(AdminResources.I_SERVICE_STATE_DESTROYED);

		case ServiceState.QUIESCED:
			return ar.getString(AdminResources.I_SERVICE_STATE_QUIESCED);

		}
		return ar.getString(AdminResources.I_SERVICE_STATE_UNKNOWN);

	}
        	
   

    public static String getActiveConsumers(int mask, int value) {
        if (DestType.isTopic(mask))
            return "-";
        else {
	    if (value == -1) 
		return ar.getString(ar.I_UNLIMITED);
	    else
	    	return Integer.toString(value);
	}
    }

    public static String getFailoverConsumers(int mask, int value) {
        if (DestType.isTopic(mask))
            return "-";
        else {
	    if (value == -1) 
		return ar.getString(ar.I_UNLIMITED);
	    else
	    	return Integer.toString(value);
	}
    }

    /**
     * see com.sun.messaging.jmq.jmsserver.core.Subscription.getDSubLogString
     */
    public static String getDSubLogString(String clientID, String duraName) {
        return "["+(clientID == null ? "":clientID)+":"+duraName+"]";
    }
}
