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
 * @(#)ConsoleObjStoreManager.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Enumeration;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.objstore.ObjStoreException;
import com.sun.messaging.jmq.admin.util.UserPropertiesException;

/**
 * This class manages all the instances of the ObjStores that it 
 * creates.  It also provides the default implementations for the 
 * miscellaneous useful operations for the manager.  The user should 
 * extend this class and overwrite methods if he wishes to provide 
 * different implementations or add more operations.
 */
public class ConsoleObjStoreManager extends ObjStoreManager {


    /**
     * The reference to this class itself.
     */
    private static ConsoleObjStoreManager mgr = null;

    private String fileName = "objstorelist.properties";

    /**
     * Private constructor for this class.
     * This is called only once.
     */
    protected ConsoleObjStoreManager() {
	super();
    }

    /**
     * If the ObjStoreManager was requested more than once, this
     * will simply return the same instance.
     *
     * @return  ConsoleObjStoreManager  the only one instance of this class
     *
     * REVISIT:
     * This allows two instances (ObjStoreManager and ConsoleObjStoreManager)
     * to coexist, which is NO GOOD.  We should only have one instance of
     * it - either ObjStoreManager OR ConsoleObjStoreManager OR a custom 
     * manager.  This needs to be fixed.
     */
    public static synchronized 
	ConsoleObjStoreManager getConsoleObjStoreManager() {

        if (mgr == null)
            mgr = new ConsoleObjStoreManager();
        return mgr;
    }

    /**
     * Reads the files and populates objStores.
     *
     */
    public void readObjStoresFromFile() throws UserPropertiesException, 
					ObjStoreException {

	ObjStoreListProperties	oslProps = readFromFile();

	int count = oslProps.getObjStoreCount();

	for (int i = 0; i < count; ++i)  {
	    ObjStoreAttrs osa = oslProps.getObjStoreAttrs(i);

	    createStore(osa);
	}
    }

    /**
     * Reads the files containing ObjStoreAttrs.
     *
     * @return    Properties object containing list of ObjectStoreAttrs
     *
     */
    private ObjStoreListProperties readFromFile() throws UserPropertiesException {

	ObjStoreListProperties  oslProps = new ObjStoreListProperties();

	oslProps.setFileName(fileName);
	oslProps.load();

        return (oslProps);
    }

    /**
     * Writes ObjStores to files.
     */
    public void writeObjStoresToFile() throws UserPropertiesException  {

        // Must call ObjStoreAttrs.prepareToTerminate() prior to
        // storing

	Enumeration e = objStores.elements();
	ObjStoreListProperties  oslProps = new ObjStoreListProperties();

	while (e.hasMoreElements()) {

	    ObjStore os = (ObjStore)e.nextElement();
	    ObjStoreAttrs osa = os.getObjStoreAttrs();
	    osa.prepareToTerminate();

	    oslProps.addObjStoreAttrs(osa);
	}

	writeToFile(oslProps);
    }

    /**
     * Writes ObjStoreAttrs to files.
     *
     */
    private void writeToFile(ObjStoreListProperties oslProps) 
					throws UserPropertiesException {
        oslProps.setFileName(fileName);
        oslProps.save();
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
}
