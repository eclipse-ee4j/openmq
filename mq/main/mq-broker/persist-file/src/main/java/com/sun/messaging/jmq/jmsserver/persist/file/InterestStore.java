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
 * @(#)InterestStore.java	1.35 08/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.file;

import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.io.disk.PHashMap;
import com.sun.messaging.jmq.io.disk.PHashMapLoadException;
import com.sun.messaging.jmq.io.disk.VRFileWarning;

import java.io.*;
import java.util.*;

/**
 * Keep track of all persisted Interest objects
 */
class InterestStore {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();
    BrokerConfig config = Globals.getConfig();

    // initial size of backing file
    static final String INTEREST_FILE_SIZE_PROP
		= FileStore.FILE_PROP_PREFIX + "interest.file.size";

    static final long DEFAULT_INTEREST_FILE_SIZE = 1024; // 1024k = 1M

    static final String BASENAME = "interest"; // data file

    private File backingFile = null;

    // cache of all stored interests; maps interest id -> interest
    private PHashMap interestMap = null;

    private LoadException loadException = null;

    /**
     * When instantiated, all interests are loaded.
     */
    InterestStore(FileStore p, File topDir, boolean clear) throws BrokerException {

	SizeString filesize = config.getSizeProperty(INTEREST_FILE_SIZE_PROP,
					DEFAULT_INTEREST_FILE_SIZE);

	backingFile = new File(topDir, BASENAME);
	try {
	    // safe=false; data synchronization is controlled by caller
	    interestMap = new PHashMap(backingFile, filesize.getBytes(),
                false, clear, Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    throw new BrokerException(br.getString(
					br.X_LOAD_INTERESTS_FAILED), e);
	}

	try {
	    interestMap.load(p);
	} catch (IOException e) {
	    throw new BrokerException(br.getString(
					br.X_LOAD_INTERESTS_FAILED), e);
	} catch (ClassNotFoundException e) {
	    throw new BrokerException(br.getString(
					br.X_LOAD_INTERESTS_FAILED), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING, br.X_FAILED_TO_LOAD_A_CONSUMER, le);

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

	VRFileWarning w = interestMap.getWarning();
	if (w != null) {
		logger.log(logger.WARNING,
			"possible loss of consumer data", w);
	}

	if (clear && Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH,
			"InterestStore initialized with clear option");
	}

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "LOADED " + (interestMap.size()) +
				" INTERESTS");
	}
    }

    /**
     * When instantiated, upgrade data from old store
     * if clear is true, just remove all data in old store
     */
    InterestStore(FileStore p, File topDir, File oldDir, boolean clear)
	throws BrokerException {

	SizeString filesize = config.getSizeProperty(INTEREST_FILE_SIZE_PROP,
					DEFAULT_INTEREST_FILE_SIZE);

	File oldFile = new File(oldDir, BASENAME);
	PHashMap olddata = null;

	backingFile = new File(topDir, BASENAME);
	try {
	    // safe=false; reset=clear
	    olddata = new PHashMap(oldFile, false, clear, 
                          Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	}

	try {
	    olddata.load(p);
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	} catch (ClassNotFoundException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	} catch (PHashMapLoadException le) {
	
	    while (le != null) {
		logger.log(Logger.WARNING,
			br.X_FAILED_TO_LOAD_A_CONSUMER_FROM_OLDSTORE, le);

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
			"possible loss of consumer data in old store", w);
	}

	try {
	    // safe=false; reset=false
	    interestMap = new PHashMap(backingFile, oldFile.length(), false, false,
                              Globals.isMinimumWritesFileStore(), Broker.isInProcess());
	} catch (IOException e) {
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	}

	try {
	    interestMap.load(p);
	} catch (IOException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	} catch (ClassNotFoundException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	} catch (PHashMapLoadException e) {
	    // should not happen so throw exception
	    logger.log(logger.ERROR, br.X_UPGRADE_INTERESTS_FAILED,
			oldFile, backingFile, e);
	    throw new BrokerException(br.getString(
				br.X_UPGRADE_INTERESTS_FAILED, oldFile,
					backingFile), e);
	}

	w = interestMap.getWarning();
	if (w != null) {
	    logger.log(logger.WARNING,
			"possible loss of consumer data", w);
	}

	Iterator itr = olddata.entrySet().iterator();
	while (itr.hasNext()) {
	    Map.Entry entry = (Map.Entry)itr.next();
	    Object key = entry.getKey();
	    Object value = entry.getValue();
	    interestMap.put(key, value);
	}
	olddata.close();

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUG, "UPGRADED " + (interestMap.size()) +
				" INTERESTS");
	}

	// if upgradeNoBackup, remove oldfile
	if (p.upgradeNoBackup()) {
	    if (!oldFile.delete()) {
		logger.log(logger.ERROR, br.I_DELETE_FILE_FAILED, oldFile);
	    }
	}
    }

    LoadException getLoadException() {
	return loadException;
    }

    private void sync() throws BrokerException {
	try {
		if(Store.getDEBUG_SYNC())
		{
			String msg = "InterestStore sync()";
			logger.log(Logger.DEBUG,msg);
		}
	    interestMap.force();
	} catch (IOException e) {
	    throw new BrokerException(
		"Failed to synchronize file: " + backingFile, e);
	}
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */  
    Hashtable getDebugState() {
	Hashtable t = new Hashtable();
	t.put("Consumers", String.valueOf(interestMap.size()));
	return t;
    }

    /**
     * Print out usage info in the interest directory.
     */
    public void printInfo(PrintStream out) {
	out.println("\nInterests");
	out.println("---------");
	out.println("backing file: " + backingFile);
	out.println("number of interests:   " + interestMap.size());
    }

    /**
     * Store an interest which is uniquely identified by it's interest id.
     *
     * @param interest	interest to be persisted
     * @exception IOException if an error occurs while persisting the interest
     * @exception BrokerException if an interest with the same id exists
     *			in the store already
     */
    void storeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

	ConsumerUID id = interest.getConsumerUID();

        try {
            Object oldValue = interestMap.putIfAbsent(id, interest);
            if (oldValue != null) {
                logger.log(logger.ERROR, br.E_INTEREST_EXISTS_IN_STORE, id,
                    interest.getDestinationUID().getLongString());
                throw new BrokerException(
                    br.getString(br.E_INTEREST_EXISTS_IN_STORE, id,
                    interest.getDestinationUID().getLongString()));
            }

            if (sync) {
                sync();
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_PERSIST_INTEREST_FAILED, id, e);
            throw e;
        }
    }

    /**
     * Remove the interest from the persistent store.
     *
     * @param interest	the interest to be removed
     * @exception IOException if an error occurs while removing the interest
     * @exception BrokerException if the interest is not found in the store
     */
    void removeInterest(Consumer interest, boolean sync)
	throws IOException, BrokerException {

	Object oldinterest = null;
        ConsumerUID id = interest.getConsumerUID();

        try {
            oldinterest = interestMap.remove(id);

            if (oldinterest == null) {
                logger.log(logger.ERROR,
                            br.E_INTEREST_NOT_FOUND_IN_STORE, id,
            interest.getDestinationUID().getLongString());
                throw new BrokerException(
                            br.getString(br.E_INTEREST_NOT_FOUND_IN_STORE,
                            id, interest.getDestinationUID().getLongString()));
            }

            if (sync) {
                sync();
            }
        } catch (RuntimeException e) {
            logger.log(logger.ERROR, br.X_REMOVE_INTEREST_FAILED, id);
            throw new BrokerException(
                    br.getString(br.X_REMOVE_INTEREST_FAILED, id), e);
        }
    }

    /**
     * Retrieve all interests in the store.
     * Will return as many interests as we can read.
     * Any interests that are retrieved unsuccessfully will be logged as a
     * warning.
     *
     * @return an array of Interest objects; a zero length array is
     * returned if no interests exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    Consumer[] getAllInterests() throws IOException {

        return (Consumer[])interestMap.values().toArray(new Consumer[0]);
    }

    // clear the store; when this method returns, the store has a state
    // that is the same as an empty store (same as when InterestStore
    // is instantiated with the clear argument set to true
    void clearAll(boolean sync) {

	if (Store.getDEBUG()) {
	    logger.log(logger.DEBUGHIGH, "InterestStore.clearAll() called");
	}

        interestMap.clear();

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
			"InterestStore: closing, "+interestMap.size()+
			" persisted interests");
	}

	interestMap.close();
    }

}

