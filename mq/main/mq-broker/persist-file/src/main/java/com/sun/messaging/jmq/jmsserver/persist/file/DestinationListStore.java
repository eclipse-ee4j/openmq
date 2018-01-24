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
 * @(#)DestinationList.java	1.32 08/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.VRFileWarning;

import java.io.*;
import java.util.*;


/**
 * Keep track of all persisted destinations by using PHashMap.
 */
class DestinationListStore {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    protected BrokerConfig config = Globals.getConfig();

    // initial size of backing file
    static final String DEST_FILE_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "destination.file.size";

    static final long DEFAULT_DEST_FILE_SIZE = 1024; // 1024k = 1M

    static final String BASENAME = "destination"; // basename of data file

    // cache all persisted destinations
    // maps destination's unique name(String) -> Destination
    private PHashMap dstMap = null;

    private File backingFile = null;

    private FileStore parent = null;

    private LoadException loadException = null;

    // when instantiated, all data are loaded
    DestinationListStore(FileStore p, File topDir, boolean clear)
	throws BrokerException {

	this.parent = p;

	SizeString filesize = config.getSizeProperty(DEST_FILE_SIZE_PROP,
					DEFAULT_DEST_FILE_SIZE);

	if (clear && Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
			"DestinationList initialized with clear option");
	}

	backingFile = new File(topDir, BASENAME);
	try {
	    // pass in safe=false; caller decide when to sync
	    dstMap = new PHashMap(backingFile, filesize.getBytes(), false, clear, 
                         Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_DESTINATIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_DESTINATIONS_FAILED), e);
	}

	try {
	    dstMap.load(parent);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_LOAD_DESTINATIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_DESTINATIONS_FAILED), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_LOAD_DESTINATIONS_FAILED, e);
	    throw new BrokerException(
			br.getString(br.X_LOAD_DESTINATIONS_FAILED), e);
	} catch (PHashMapLoadException le) {
	    while (le != null) {
		logger.log(Logger.WARNING, br.X_FAILED_TO_LOAD_A_DEST, le);

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

	VRFileWarning w = dstMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of destination data", w); 
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "DestinationList: loaded "+
					dstMap.size()+" destinations");
	}
    }

    LoadException getLoadException() {
	return loadException;
    }

    // will upgrade data from old store to current format
    DestinationListStore(FileStore p, File topDir, File oldTopDir)
	throws BrokerException {

	this.parent = p;

	File oldFile = new File(oldTopDir, BASENAME);
	PHashMap olddata = null;

	backingFile = new File(topDir, BASENAME);
	try {
	    // load old data
	    // safe=false; reset=false
	    olddata = new PHashMap(oldFile, false, false, 
                          Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    olddata.load(parent);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING,
			br.X_FAILED_TO_LOAD_A_DEST_FROM_OLDSTORE, le);

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
			"possible loss of destination data in old store", w); 
	}

	try {
	    // pass in safe=false; caller decide when to sync
	    // safe=false; reset=false
	    dstMap = new PHashMap(backingFile, oldFile.length(), false, false, 
                         Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	}

	try {
	    dstMap.load(parent);
	} catch (IOException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	} catch (ClassNotFoundException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	} catch (PHashMapLoadException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_DESTINATIONS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(
			br.getString(br.X_UPGRADE_DESTINATIONS_FAILED,
				oldFile, backingFile), e);
	}

	w = dstMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of destination data", w); 
	}

	// put data in new store
	// migration done when the old data is read in
	Iterator itr = olddata.values().iterator();
	Destination dst = null;
	while (itr.hasNext()) {
	    dst = (Destination)itr.next();
	    dstMap.put(dst.getDestinationUID().toString(), dst);
	}
	olddata.close();

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "DestinationList: upgraded "+
					dstMap.size()+" destinations");
	}

	// if upgradeNoBackup, remove oldfile
	if (parent.upgradeNoBackup()) {
	    if (!oldFile.delete()) {
		logger.log(logger.ERROR, br.I_DELETE_FILE_FAILED, oldFile);
	    }
	}
    }

    /**
     * Synchronize data associated with the specified destination to disk.
     * If null is specified, data assocated with all persisted destinations
     * will be synchronized.
     */
    void syncDestination(Destination destination) throws BrokerException {

	String dstname = (destination != null ?
			destination.getDestinationUID().toString() : null);

	if (dstname != null) {
	    checkDestination(dstname);
	}

	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "DestinationList sync() "+dstname;
			logger.log(Logger.DEBUG,msg);
		}
	    dstMap.force(dstname);
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize file: " + backingFile, e); 
	}
    }

    /**
     * Store a Destination.
     *
     * @param destination	the destination to be persisted
     * @exception IOException if an error occurs while persisting
     *		the destination
     * @exception BrokerException if the same destination exists
     *			the store already
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    void storeDestination(Destination destination, boolean sync)
	throws IOException, BrokerException {

	String dstname = destination.getDestinationUID().toString();

        try {
            Object oldValue = dstMap.putIfAbsent(dstname, destination);
            if (oldValue != null) {
                logger.log(logger.ERROR, br.E_DESTINATION_EXISTS_IN_STORE,
                    destination.getName());
                throw new BrokerException(br.getString(
                    br.E_DESTINATION_EXISTS_IN_STORE, destination.getName()));
            }

            if (sync) {
                sync();
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_DESTINATION_FAILED,
                            destination.getName());
            throw new BrokerException(
                            br.getString(br.X_PERSIST_DESTINATION_FAILED,
                                    destination.getName()), e);
        }
    }

    /**
     * Update the destination in the persistent store.
     *
     * @param destination	the destination to be updated
     * @exception BrokerException if the destination is not found in the store
     *			or if an error occurs while updating the destination
     */
    void updateDestination(Destination destination, boolean sync)
	throws BrokerException {

	String dstname = destination.getDestinationUID().toString();
	checkDestination(dstname);

        try {
            // update destination
            dstMap.put(dstname, destination);

            if (sync) {
                sync();
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_DESTINATION_FAILED,
                            dstname);
            throw new BrokerException(
                            br.getString(br.X_PERSIST_DESTINATION_FAILED,
                            dstname), e);
        }
    }

    /**
     * Remove the destination from the persistent store.
     * All messages associated with the destination will be removed as well.
     *
     * @param destination	the destination to be removed
     * @exception BrokerException if the destination is not found in the store
     */
    void removeDestination(Destination destination, boolean sync)
	throws BrokerException {

	String dstname = destination.getDestinationUID().toString();
        try {
            Object olddst = dstMap.remove(dstname);

            if (olddst == null) {
                logger.log(logger.ERROR,
                            br.E_DESTINATION_NOT_FOUND_IN_STORE,
                            destination.getName());
                throw new BrokerException(
                            br.getString(
                                    br.E_DESTINATION_NOT_FOUND_IN_STORE,
                                    destination.getName()));
            }

            if (sync) {
                sync();
            }

            // remove all messages associated with this destination
            parent.getMsgStore().releaseMessageDir(
                                    destination.getDestinationUID(), sync);
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_REMOVE_DESTINATION_FAILED,
                            destination.getName(), e);
            throw new BrokerException(
                            br.getString(br.X_REMOVE_DESTINATION_FAILED,
                            destination.getName()), e);
        } catch (IOException e) {
            logger.log(logger.ERROR, br.X_REMOVE_DESTINATION_FAILED,
                            dstname, e);
            throw new BrokerException(
                            br.getString(br.X_REMOVE_DESTINATION_FAILED,
                            dstname), e);
        }
    }

    /**
     * Retrieve the destination from the persistent store.
     *
     * @param did the destination to be retrieved
     * @return a Destination object
     */
    Destination getDestination(DestinationUID did) throws IOException {

        return (Destination)dstMap.get(did.toString());
    }

    /**
     * Retrieve all destinations in the store.
     *
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    Destination[] getAllDestinations() throws IOException {

        return (Destination[])dstMap.values().toArray(new Destination[0]);
    }

    // return the names of all persisted destination
    Collection getDestinations() {

        return dstMap.values(); // Fix me!
    }

    /**
     * Clear all destinations
     */
    void clearAll(boolean sync, boolean clearMessages) {

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
		"DestinationList.clearAll(" + clearMessages + ") called");
	}

        if (clearMessages) {
            Iterator itr = dstMap.values().iterator();
            while (itr.hasNext()) {
                Destination dst = (Destination)itr.next();
                DestinationUID dstuid = dst.getDestinationUID();
                try {
                    parent.getMsgStore().releaseMessageDir(dstuid, sync);
                } catch (IOException e) {
                    // log error and continue
                    logger.log(logger.ERROR, br.X_RELEASE_MSGFILE_FAILED,
                            parent.getMsgStore().getDirName(dstuid),
                            dstuid, e);
                } catch (BrokerException e) {
                    // log error and continue
                    logger.log(logger.ERROR, br.X_RELEASE_MSGFILE_FAILED,
                            parent.getMsgStore().getDirName(dstuid),
                            dstuid, e);
                }
            }
        }
        dstMap.clear();

        if (sync) {
            try {
                sync();
            } catch (BrokerException e) {
                logger.log(logger.ERROR,
                    "Got exception while synchronizing data to disk", e);
            }
        }
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("Destinations", String.valueOf(dstMap.size()));
	return t;
    }

    void close(boolean cleanup) {
	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
			"DestinationList: closing, "+dstMap.size()+
			" persisted destinations");
	}

	dstMap.close();
    }

    // for debugging and diagnostic
    public void dumpDestination() {
	Iterator itr = dstMap.values().iterator();
	int count = 0;
	while (itr.hasNext()) {
	    System.out.println("dst "+(count++)+":"+itr.next());
	}
    }

    // check whether the specified destination exists
    // throw BrokerException if it does not exist
    void checkDestination(String dstname) throws BrokerException {
        if (!dstMap.containsKey(dstname)) {
            logger.log(logger.ERROR,
                    br.E_DESTINATION_NOT_FOUND_IN_STORE, dstname);
            throw new BrokerException(
                    br.getString(br.E_DESTINATION_NOT_FOUND_IN_STORE,
                            dstname));
        }
    }

    private void sync() throws BrokerException {
	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "DestinationList sync() all";
			logger.log(Logger.DEBUG,msg);
		}
	    dstMap.force();
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize file: " + backingFile, e);
	}
    }
}

