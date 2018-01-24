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
 * @(#)PropertiesFile.java	1.22 08/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.VRFileWarning;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;

import java.io.*;
import java.util.*;


/**
 * Keep track of persisted properties by using PHashMap.
 */
class PropertiesFile {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    BrokerConfig config = Globals.getConfig();

    // initial size of backing file
    static final String PROP_FILE_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "property.file.size";

    static final long DEFAULT_PROP_FILE_SIZE = 1024; // 1024k = 1M

    static final String BASENAME = "property"; // basename of properties file

    // all persisted properties
    // maps property name -> property value
    private PHashMap propMap = null;

    private File backingFile = null;

    private LoadException loadException = null;

    // when instantiated, all data are loaded
    PropertiesFile(FileStore p, File topDir, boolean clear) throws BrokerException {
	SizeString filesize = config.getSizeProperty(PROP_FILE_SIZE_PROP,
					DEFAULT_PROP_FILE_SIZE);

	backingFile = new File(topDir, BASENAME);
	try {
	    // safe=false; caller controls data synchronization
	    propMap = new PHashMap(backingFile, filesize.getBytes(),
                false, clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.logStack(logger.ERROR, br.X_LOAD_PROPERTIES_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_PROPERTIES_FAILED), e);
	}

	try {
	    propMap.load(p);
	} catch (IOException e) {
	    logger.logStack(logger.ERROR, br.X_LOAD_PROPERTIES_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_PROPERTIES_FAILED), e);
	} catch (ClassNotFoundException e) {
	    logger.logStack(logger.ERROR, br.X_LOAD_PROPERTIES_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_PROPERTIES_FAILED), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING, br.X_FAILED_TO_LOAD_A_PROPERTY, le);

		// save info in LoadException
		LoadException e = new LoadException(le.getMessage(),
							le.getCause());
		e.setKey(le.getKey());
		e.setValue(le.getValue());
		e.setKeyCause(le.getKeyCause());
		e.setValueCause(le.getValueCause());
		e.setNextException(loadException);
		loadException = e;

		// get the chained exception
		le = le.getNextException();
	    }
	}

	VRFileWarning w = propMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of persisted properties", w);
	}

	if (clear && Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
			"PropertiesFile initialized with clear option");
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "PropertiesFile: loaded " +
				propMap.size() + " properties");
	}
    }

    LoadException getLoadException() {
	return loadException;
    }

    // when instantiated, old data are upgraded
    PropertiesFile(FileStore p, File topDir, File oldTop)
	throws BrokerException {

	File oldFile = new File(oldTop, BASENAME);
	PHashMap olddata = null;

	backingFile = new File(topDir, BASENAME);
	try {
	    // load old data
	    // safe=false; reset=false
	    olddata = new PHashMap(oldFile, false, false, 
                          Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    olddata.load(p);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING,
			br.X_FAILED_TO_LOAD_A_PROPERTY_FROM_OLDSTORE, le);

		// save info in LoadException
		LoadException e = new LoadException(le.getMessage(),
							le.getCause());
		e.setKey(le.getKey());
		e.setValue(le.getValue());
		e.setKeyCause(le.getKeyCause());
		e.setValueCause(le.getValueCause());
		e.setNextException(loadException);
		loadException = e;

		// get the chained exception
		le = le.getNextException();
	    }
	}

	VRFileWarning w = olddata.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
		"possible loss of persisted properties in old store", w);
	}

	try {
	    // pass in safe=false; let caller decide when to sync
	    // safe=false; reset=false
	    propMap = new PHashMap(backingFile, oldFile.length(), false, false, 
                          Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    propMap.load(p);
	} catch (IOException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED, oldFile,
			backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_PROPERTIES_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_PROPERTIES_FAILED,
				oldFile, backingFile), e);
	}

	w = propMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of persisted properties", w);
	}

	// just copy old data to new store
	Iterator itr = olddata.entrySet().iterator();
	while (itr.hasNext()) {
	    Map.Entry entry = (Map.Entry)itr.next();
	    Object key = entry.getKey();
	    Object value = entry.getValue();
	    propMap.put(key, value);
	}
	olddata.close();

	// if upgradeNoBackup, remove oldfile
	if (p.upgradeNoBackup()) {
	    if (!oldFile.delete()) {
		logger.log(logger.ERROR, br.I_DELETE_FILE_FAILED, oldFile);
	    }
	}

    }

    /**
     * Persist the specified property name/value pair.
     * If the property identified by name exists in the store already,
     * it's value will be updated with the new value.
     * If value is null, the property will be removed.
     * The value object needs to be serializable.
     *
     * @param name name of the property
     * @param value value of the property
     * @exception BrokerException if an error occurs while persisting the
     *			timestamp
     */
    void updateProperty(String name, Object value, boolean sync)
	throws BrokerException {

        try {
            boolean updated = false;

            if (value == null) {
                // remove it
                Object old = propMap.remove(name);
                updated = (old != null);
            } else {
                // update it
                propMap.put(name, value);
                updated = true;
            }

            if (updated && sync) {
                sync();
            }
        } catch (RuntimeException e) {

            logger.log(logger.ERROR, br.X_PERSIST_PROPERTY_FAILED, name);
            throw new BrokerException(
                    br.getString(br.X_PERSIST_PROPERTY_FAILED, name), e);
        }
    }

    /**
     * Retrieve the value for the specified property.
     *
     * @param name name of the property whose value is to be retrieved
     * @return the property value; null is returned if the specified
     *		property does not exist in the store
     * @exception BrokerException if an error occurs while retrieving the
     *			data
     * @exception NullPointerException if <code>name</code> is
     *			<code>null</code>
     */
    Object getProperty(String name) throws BrokerException {

        return propMap.get(name);
    }

    /**
     * Return the names of all persisted properties.
     *
     * @return an array of property names; an empty array will be returned
     *		if no property exists in the store.
     */
    String[] getPropertyNames() throws BrokerException {

        Set keys = propMap.keySet();
        return (String[])keys.toArray(new String[keys.size()]);
    }

    Properties getProperties() throws BrokerException {

        Properties props = new Properties();

        Iterator itr = propMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry e = (Map.Entry)itr.next();
            props.put(e.getKey(), e.getValue());
        }

        return props;
    }

    /**
     * Clear all records
     */
    // clear the store; when this method returns, the store has a state
    // that is the same as an empty store
    void clearAll(boolean sync) {

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH, "PropertiesFile.clearAll() called");
	}

        propMap.clear();

        if (sync) {
            try {
                sync();
            } catch (BrokerException e) {
                logger.log(logger.ERROR,
                    "Got exception while synchronizing data to disk", e);
            }
        }
    }

    void close(boolean cleanup) {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
			"PropertiesFile: closing, "+propMap.size()+
			" properties");
	}

	propMap.close();

    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("Properties", String.valueOf(propMap.size()));
	return t;
    }

    // for internal use; not synchronized
    void printInfo(PrintStream out) {
	out.println("\nProperties");
	out.println("----------");
	out.println("backing file: "+ backingFile);

	// print all property name/value pairs
	Set entries = propMap.entrySet();
	Iterator itor = entries.iterator();
	while (itor.hasNext()) {
	    Map.Entry entry = (Map.Entry)itor.next();
	    out.println((String)entry.getKey() + "="
			+ entry.getValue().toString());
	}
    }

    private void sync() throws BrokerException {
	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "PropertiesFile sync()";
			logger.log(Logger.DEBUG,msg);
		}
	    propMap.force();
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize file: " + backingFile, e);
	}
    }
}


