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
 * @(#)ObjStoreConFactoryListInspector.java	1.12 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/** 
 * Inspector panel for the object store connection factory list.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class ObjStoreConFactoryListInspector extends TabledInspector {
    
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Return the array of Strings containing the collumn labels/headers.
     * @return the array of Strings containing the collumn labels/headers.
     */
    public String[] getColumnHeaders()  {
        String[] columnNames = {acr.getString(acr.I_OBJSTORE_LOOKUP_NAME),
                                acr.getString(acr.I_OBJSTORE_FACTORY_TYPE)};
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
	if (col == 0) {
	    return (conObj);
	} else if (col == 1) {
	    Object object = ((ObjStoreConFactoryCObj)conObj).getObject();
	    if (object instanceof com.sun.messaging.XATopicConnectionFactory) {
		return acr.getString(acr.I_XATCF);
	    } else if (object instanceof com.sun.messaging.XAQueueConnectionFactory) {
		return acr.getString(acr.I_XAQCF);
	    } else if (object instanceof com.sun.messaging.XAConnectionFactory) {
		return acr.getString(acr.I_XACF);
	    } else if (object instanceof com.sun.messaging.TopicConnectionFactory) {
		return acr.getString(acr.I_TCF);
	    } else if (object instanceof com.sun.messaging.QueueConnectionFactory) {
		return acr.getString(acr.I_QCF);
	    } else if (object instanceof com.sun.messaging.ConnectionFactory) {
		return acr.getString(acr.I_CF);
	    }
	}

	return (null);
    }
}
