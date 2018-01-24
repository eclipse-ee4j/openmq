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
 * @(#)BrokerServiceListInspector.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminUtil;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.util.ServiceState;
import com.sun.messaging.jmq.util.admin.ServiceInfo;

/** 
 * Inspector panel for the broker service list.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class BrokerServiceListInspector extends TabledInspector  {

    private static AdminResources ar = Globals.getAdminResources();
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Return the array of Strings containing the collumn labels/headers.
     * @return the array of Strings containing the collumn labels/headers.
     */
    public String[] getColumnHeaders()  {
        String[] columnNames = {acr.getString(acr.I_SVC_NAME),
                                acr.getString(acr.I_PORT_NUMBER),
                                acr.getString(acr.I_SVC_STATE)};
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

        BrokerServiceCObj      bSvcCObj;

        if (!(conObj instanceof BrokerServiceCObj))  {
            return null;
        }

        bSvcCObj = (BrokerServiceCObj)conObj;
	ServiceInfo svcInfo = bSvcCObj.getServiceInfo();

        if (col == 0) {
            return (bSvcCObj);
        } else if (col == 1) {
	    String portStr;

	    // The port number is not applicable to this service
	    if (svcInfo.port == -1) {
		portStr = "-";

	    } else if (svcInfo.dynamicPort) {
                // Add more information about the port number:
                // dynamically generated or statically declared
                switch (svcInfo.state) {
                    case ServiceState.UNKNOWN:
                        portStr = ar.getString(ar.I_DYNAMIC);
                        break;
                    default:
                        portStr = Integer.toString(svcInfo.port)
					+ " (" 
					+ ar.getString(ar.I_DYNAMIC) 
					+ ")";
                 }
            } else {
                portStr = Integer.toString(svcInfo.port)
				+ " (" 
				+ ar.getString(ar.I_STATIC) 
				+ ")";
	    }

            return (portStr);
        } else if (col == 2) {
          //  return (ServiceState.getString(svcInfo.state));
        	return BrokerAdminUtil.getServiceState(svcInfo.state);
        }

        return (null);
    }
}
