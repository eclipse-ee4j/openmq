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
 * @(#)BrokerDestListInspector.java	1.13 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.DestState;
import com.sun.messaging.jmq.util.admin.DestinationInfo;


/** 
 * Inspector panel for the broker destination list.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class BrokerDestListInspector extends TabledInspector  {

    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();
    private static AdminResources ar = Globals.getAdminResources();

    /**
     * Return the array of Strings containing the collumn labels/headers.
     * @return the array of Strings containing the collumn labels/headers.
     */
    public String[] getColumnHeaders()  {
        String[] columnNames = {acr.getString(acr.I_OBJSTORE_DEST_NAME),
                                acr.getString(acr.I_OBJSTORE_DEST_TYPE),
				acr.getString(acr.I_BROKER_DEST_STATE)};
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

        BrokerDestCObj      bDestCObj;

        if (!(conObj instanceof BrokerDestCObj))  {
            return null;
        }

        bDestCObj = (BrokerDestCObj)conObj;
        DestinationInfo destInfo = bDestCObj.getDestinationInfo();

	/*
    	 * Do not list internal destinations.
	 */
	if (DestType.isInternal(destInfo.fulltype)) {
	    return null;
	}

        if (col == 0) {
            return (bDestCObj);
        } else if (col == 1) {
	    return (BrokerAdminUtil.getDestinationType(destInfo.type));
        } else if (col == 2) {
            //return (DestState.toString(destInfo.destState));
            return BrokerAdminUtil.getDestinationState(destInfo.destState);
	}
        return (null);
    }
}
