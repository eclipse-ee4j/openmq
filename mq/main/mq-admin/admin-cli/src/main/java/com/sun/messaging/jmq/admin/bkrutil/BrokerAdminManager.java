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
 * @(#)BrokerAdminManager.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.bkrutil;

import java.util.Hashtable;

public class BrokerAdminManager {

    /**
     * The reference to this class itself.
     */
    private static BrokerAdminManager mgr = null;

    /**
     * This contains all the available (bookmarked) instances of BrokerAdmin.
     */
    private static Hashtable admins;

    /**
     * If the BrokerAdminManager was requested more than once, this
     * will simply return the same instance.
     *
     * @return  BrokerAdminManager  the only one instance of this class
     */
    public static synchronized
        BrokerAdminManager getBrokerAdminManager() {

        if (mgr == null) {
            mgr = new BrokerAdminManager();
	    admins = new Hashtable();
	}
        return mgr;
    }

    /**
     * Adds an instance of BrokerAdmin to the list.
     * This will simply overwrite the existing one if there is any.
     */
    public void addBrokerAdmin(BrokerAdmin ba) {
	    admins.put(ba.getKey(), ba);	
    }

    /**
     *
     */
    public void deleteBrokerAdmin(BrokerAdmin ba) {
            admins.remove(ba.getKey());
    }

    /**
     * Returns the list of admin instances.
     */
    public Hashtable getBrokerAdmins() {
	return admins;
    }

    /**
     * Returns true if the key of BrokerAdmin exists in the list.
     * Returns false otherwise.
     */
    public boolean exist(String key) {
	return admins.containsKey(key);
    }
}
