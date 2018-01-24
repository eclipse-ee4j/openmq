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
 * @(#)BrokerListInspector.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * Inspector panel for the list of brokers.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class BrokerListInspector extends TabledInspector {

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Return the array of Strings containing the collumn labels/headers.
     * @return the array of Strings containing the collumn labels/headers.
     */
    public String[] getColumnHeaders()  {
        String[] columnNames = {acr.getString(acr.I_BROKER_NAME2),
			      acr.getString(acr.I_BROKER_HOST2),
			      acr.getString(acr.I_PRIMARY_PORT),
                              acr.getString(acr.I_CONN_STATUS)};
	return (columnNames);
    }

    /**
     * Returns the Object at a particular cell collumn for a given
     * ConsoleObj object. Each row in the JTable represents one ConsoleObj.
     * This method returns the object/value for the ConsoleObj, for a particular 
     * collumn.
     *
     * @return the Object at a particular cell collumn for a given
     * ConsoleObj object.
     */
    public Object getValueAtCollumn(ConsoleObj conObj, int col)  {

	BrokerCObj	bCObj;

	if (!(conObj instanceof BrokerCObj))  {
	    return null;
	}

	bCObj = (BrokerCObj)conObj;

	if (col == 0) {
	    return (bCObj);
	} else if (col == 1) {
	    return (bCObj.getBrokerAdmin().getBrokerHost());
	} else if (col == 2) {
	    return (bCObj.getBrokerAdmin().getBrokerPort());
	} else if (col == 3) {
	    if (bCObj.getBrokerAdmin().isConnected())
	        return acr.getString(acr.I_CONNECTED);
	    else
	        return acr.getString(acr.I_DISCONNECTED);
	}

	return (null);
    }
}
