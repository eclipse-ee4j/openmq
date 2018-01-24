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
 * @(#)ConsoleBrokerAdminManager.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Enumeration;
import java.util.Vector;
import com.sun.messaging.jmq.admin.util.UserPropertiesException;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdmin;
import com.sun.messaging.jmq.admin.bkrutil.BrokerAdminException;


public class ConsoleBrokerAdminManager {
    private String fileName = "brokerlist.properties";

    /**
     * The collection of BrokerAdmin objects.
     */
    private Vector admins = new Vector();

    public ConsoleBrokerAdminManager()  {
    }

    /**
     * Adds an instance of BrokerAdmin to the list.
     * This will simply overwrite the existing one if there is any.
     * Should make sure dups are not added by calling exist() before doing
     * this.
     */
    public void addBrokerAdmin(BrokerAdmin ba) {
	admins.addElement(ba);	
    }

    /**
     *
     */
    public void deleteBrokerAdmin(BrokerAdmin ba) {

	String baKey = ba.getKey();

	for (int i = 0; i < admins.size(); i++) {
	    BrokerAdmin ba2 = (BrokerAdmin)admins.get(i);
	    String ba2Key = ba2.getKey();
	    if (baKey.equals(ba2Key)) {
		admins.remove(i);
	        return;
	    }
	}
    }

    /**
     * Reads the files and populates the manager with
     * BrokerAdmin objects.
     *
     */
    public void readBrokerAdminsFromFile() throws UserPropertiesException, 
					BrokerAdminException {

	BrokerListProperties	blProps = readFromFile();

	int count = blProps.getBrokerCount();

	for (int i = 0; i < count; ++i)  {
	    BrokerAdmin ba = blProps.getBrokerAdmin(i);

	    addBrokerAdmin(ba);
	}
    }


    /**
     * Writes broker list to files.
     */
    public void writeBrokerAdminsToFile() throws UserPropertiesException  {

	BrokerListProperties  blProps = new BrokerListProperties();

	for (int i = 0; i < admins.size(); i++) {
	    BrokerAdmin ba = (BrokerAdmin)admins.get(i);
	    blProps.addBrokerAdmin(ba);
	}


	writeToFile(blProps);
    }

    /**
     * Returns the list of admin instances.
     */
    public Vector getBrokerAdmins() {
	return admins;
    }

    /**
     * Returns true if the key of BrokerAdmin exists in the list.
     * Returns false otherwise.
     */
    public boolean exist(String key) {
	for (int i = 0; i < admins.size(); i++) {
	    BrokerAdmin ba = (BrokerAdmin)admins.get(i);
	    String baKey = ba.getKey();
	    if (key.equals(baKey)) {
		return true;
	    }
	}

	return false;
    }

    /**
     * Sets the name of the file where the objstore list is saved
     * when writeObjStoresToFile() is called. This is also the file
     * that is read from when readObjStoresFromFile() is called.
     *
     * @param     fileName	The fileName where the object stores
     *				are read from and written to.
     */
    public void setFileName(String fileName)  {
        this.fileName = fileName;
    }


    /**
     * Reads the files containing the list of Brokers.
     *
     * @return    Properties object containing list of Brokers
     *
     */
    private BrokerListProperties readFromFile() throws UserPropertiesException {

	BrokerListProperties  blProps = new BrokerListProperties();

	blProps.setFileName(fileName);
	blProps.load();

        return (blProps);
    }

    /**
     * Writes ObjStoreAttrs to files.
     *
     */
    private void writeToFile(BrokerListProperties blProps) 
					throws UserPropertiesException {
        blProps.setFileName(fileName);
        blProps.save();
    }


}
