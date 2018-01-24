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
 * @(#)ObjStoreManager.java	1.13 06/28/07
 */ 

package com.sun.messaging.jmq.admin.objstore;

import java.util.Enumeration;
import java.util.Vector;

import com.sun.messaging.jmq.admin.objstore.jndi.JNDIStore;

/**
 * This class manages all the instances of the ObjStores that it 
 * creates.
 *
 * This class should be implemented as a singleton, so that we can 
 * guarantee that we do not manage the same ObjStore twice within the 
 * same application.
 */
public class ObjStoreManager {

    // Debug messages on / off.
    private boolean DEBUG = false;

    /**
     * The reference to this class itself.
     */
    private static ObjStoreManager mgr = null;

    /**
     * This flag indicates that this is a JNDI store.
     */
    public static final int JNDI = 0;

    /**
     * This holds all the objstore instances it manages.
     * The key is the unique id for each ObjStore reference.
     */
    protected Vector objStores;

    /**
     * Private constructor for this class.
     * This is called only once.
     */
    protected ObjStoreManager() {

	objStores = new Vector();
    }

    /**
     * If the ObjStoreManager was requested more than once, this
     * will simply return the same instance.
     *
     * @return  ObjStoreManager  the only one instance of this class
     */
    public static synchronized ObjStoreManager getObjStoreManager() {

	if (mgr == null)
	    mgr = new ObjStoreManager();
	return mgr;
    }

    /**
     * Creates an instance of an ObjStore.
     * If the specified type is not supported, this will throw an
     * exception.  If the id already exists, this will throw an
     * exception.
     *
     * @param attrs  	connection attributes needed to open
     *              	the store 
     *
     * @exception ObjStoreTypeNotSupportedException  if type is not supported
     * @exception NameAlreadyExistsException.java    if the name already exists
     * @exception ObjStoreException  		     store cannot be created 
     *						     or an error occurs
     *
     * @see ObjStore
     */
    public ObjStore createStore(ObjStoreAttrs attrs)
        throws ObjStoreException {

	String id = attrs.getID();

	if (DEBUG) System.out.println
	("DEBUG: ObjStoreManager.createStore() getID() ---> " + id);

	if (!idExists(id)) {
            ObjStore os = ObjStoreFactory.createStore(attrs);
            objStores.addElement(os);
	    return os;
	} else {
	    throw new NameAlreadyExistsException();
	}
    }

    /** 
     * Destroys the store and removes it from the management list.
     *
     * @param id  	name of the ObjStore to destroy
     *
     * @exception NameNotFoundException  if the name is not found
     * @exception ObjStoreException  	 if an error occurs
     */
    public void destroyStore(String id) throws ObjStoreException {

	if (idExists(id))
	    removeFromObjStores(id);
	else
	    throw new NameNotFoundException();
    }

    /**
     * Returns the reference to the ObjStore identified by id.
     * If the ObjStore identified by id does not exist, this will
     * return null.
     *
     * @param id  	 id of the ObjStore
     *
     * @return ObjStore  reference to the ObjStore
     *
     * @see ObjStore
     */
    public ObjStore getStore(String id) {

	ObjStore os = null;
	String id2 = null;

	for (int i = 0; i < objStores.size(); i++) {
	    os = (ObjStore)objStores.get(i);
	    id2 = os.getID();
	    if (id.equals(id2)) {
	        return os;
	    }
	}

	return null;
    }

    /**
     * Returns a Hashtable of all open ObjStores.
     *
     * @return Hashtable  id-ObjStore reference pair of currently 
     *                    open ObjStores
     */
    public Vector getOpenStores() {

	return getStores(true);
    }

    /**
     * Returns a Hashtable of all closed ObjStores.
     *
     * @return Hashtable  id-ObjStore reference pair of currently 
     *                    closed ObjStores
     */
    public Vector getClosedStores() {

	return getStores(false);
    }

    /**
     * Returns a Hashtable of either open or closed stores.
     *
     * @param  open       specifies if the returned Hashtable
     *			  contains open / closed ObjStores
     *
     * @return Hashtable  id-ObjStore reference pair of currently
     *                    open / closed ObjStores
     */
    private Vector getStores(boolean open) {

        Vector stores = new Vector();
        ObjStore os = null;

	if (open) {
	    for (int i = 0; i < objStores.size(); i++) {
		os = (ObjStore)objStores.get(i);
	        if (os.isOpen()) {
                    stores.addElement(os);
		}
	    }
        } else {
	    for (int i = 0; i < objStores.size(); i++) {
		os = (ObjStore)objStores.get(i);
	        if (!os.isOpen()) {
                    stores.addElement(os);
		}
	    }
	}

        return stores;
    }

    /**
     * Returns a Hashtable of all ObjStores currently managed by this class.
     *
     * @return Hashtable  id-ObjStore reference pair of all 
     *                    ObjStores currently managed by this class.
     */
    public Vector getAllStores() {

	return objStores;
    }

    /**
     * A ObjStoreFactory creates an ObjStore when requested by the
     * ObjStoreManager.
     */
    private static class ObjStoreFactory {

        /**
         * Creates an instance of an ObjStore.
         * If the specified type is not supported, this will throw an
         * exception.
         *
         * @param id  	name of the ObjStore to create
         *            	suggested id is a URL of the ObjStore
         * @param attrs	connection attributes needed to open
         *             	the store 
         * @param type 	type of ObjStore to create
         *
         * @exception ObjStoreTypeNotSupportedException  if type is not 
         *            supported
         * @exception ObjStoreException  if an error occurs
         *
         * @see ObjStore
         */
        private static ObjStore createStore(ObjStoreAttrs attrs)
            throws ObjStoreException {

	    int type = attrs.getType();

            if (JNDI == type)  {
                return (new JNDIStore(attrs));
            } else {
                throw new ObjStoreTypeNotSupportedException();
            }
        }
    }

    private boolean idExists(String id) {

	for (int i = 0; i < objStores.size(); i++) {
	    String id2 = ((ObjStore)objStores.get(i)).getID();
	    if (id.equals(id2))
		return true;
	}

	return false;
    }

    private void removeFromObjStores(String id) {

	for (int i = 0; i < objStores.size(); i++) {
	    String id2 = ((ObjStore)objStores.get(i)).getID();
	    if (id.equals(id2)) {
		objStores.remove(i);
		return;
	    }
	}

    }

}
