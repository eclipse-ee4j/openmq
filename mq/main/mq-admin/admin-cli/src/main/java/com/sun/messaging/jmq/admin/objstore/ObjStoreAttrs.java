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
 * @(#)ObjStoreAttrs.java	1.8 06/28/07
 */ 

package com.sun.messaging.jmq.admin.objstore;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.lang.ClassNotFoundException;

/**
 * This class contains attributes needed to open the ObjStore
 * managed by the ObjStoreManager.
 * 
 * For the ObjStore using JNDI, this object will be directly passed
 * on to create the initialContext.
 */
public class ObjStoreAttrs extends Hashtable {

    // Debug messages on / off.
    private boolean DEBUG = false;

    /**
     * Unique ID for this class.  This will also be used as the unique ID for
     * the corresponding ObjStore.
     */
    private String id = null;

    /**
     * Description for this class.
     */
    private String description = null;

    /**
     * Type of this object store.
     * By default, it is JNDI.
     */
    private int type = ObjStoreManager.JNDI;

    /**
     * The default id and description values given to this class.
     */
    private static final String DEFAULT = "default";

    /**
     * Stores keys of non-stored attributes
     */
    private Vector transientAttrs = new Vector();

    /**
     * Constructor.
     */
    public ObjStoreAttrs() {
	this(DEFAULT, DEFAULT);
    }

    /**
     * Constructor.
     * @param id	unique id for this class.
     */
    public ObjStoreAttrs(String id) {
	this(id, id);
    }

    /**
     * Constructor.
     * @param type	type of this class.
     */
    public ObjStoreAttrs(int type) {
	this(DEFAULT, DEFAULT, type);
    }

    /**
     * Constructor.
     * @param id	unique id for this class.
     * @param type	type of this class.
     */
    public ObjStoreAttrs(String id, int type) {
	this(id, id, type);
    }

    /**
     * Constructor.
     * @param id	unique id for this class.
     * @param desc	description for this class.
     * @param type	type of this class.
     */
    public ObjStoreAttrs(String id, String desc, int type) {
	this(id, desc);
	this.type = type;
    }

    /**
     * Constructor.
     * @param id	unique id for this class.
     * @param desc	description for this class.
     */
    public ObjStoreAttrs(String id, String desc) {
	this.id = id;
	this.description = desc;
    }

    public String getID() {
	return id;
    }

/**
 * Disabling the method so that I don't have to worry about cloing the object
 * when this class is used by multiple ObjStores.
 * 
    public void setID(String id) {
	this.id = id;
    }
*/

    public String getDescription() {
	return description;
    }

/**
 * Disabling the method so that I don't have to worry about cloing the object
 * when this class is used by multiple ObjStores.
 * 
    public void setDescription(String desc) {
	this.description = desc;
    }
*/

    public int getType() {
	return type;
    }

/**
 * Disabling the method so that I don't have to worry about cloing the object
 * when this class is used by multiple ObjStores.
 * 
    public void setType(int type) {
	this.type = type;
    }
*/

    /**
     * Sets the attribute transient.  When the attribute is transient,
     * it will not be saved (serialized) when writeObject() is called.
     */
    public void setAttrTransient(String key) {
	if (!transientAttrs.contains(key))
	    transientAttrs.addElement(key);
    }

    /**
     * Sets the attribute permanent.  When the attribute is permanent,
     * it will be saved (serialized) when writeObject() is called.
     */
    public void setAttrPermanent(String key) {
	if (transientAttrs.contains(key))
	    transientAttrs.removeElement(key);
    }

    /**
     * Prepares this class by removing all the transient attributes.
     * This must be called prior to terminating the objStoreManager.
     */
    public void prepareToTerminate() {
      
	// REVISIT:
	// The best thing to do is to put this code inside of writeObject()
	// so that this cannot be executed independent of writeObject().
	// However, in order to make these two operations an atomic unit
	// one needs to implement Externalizable instead of Serializable, which
	// is prone to more errors.
	// Since the API user will not be able to access this method, it is
	// safe to make them separate and keep the logic and code simple.
	// The only thing is that the ObjStoreManager must call this method
	// prior to terminating so that transient fields will not be stored.
	// If I come up with a better approach, this may be enhanced, but this
	// will do its work just fine as is for now.

	if (DEBUG) 
	System.out.println("DEBUG: transientAttrs -> " + transientAttrs);

	Enumeration e = transientAttrs.elements();

        while (e != null && e.hasMoreElements()) {
            String key = (String)e.nextElement();
	    this.remove(key);	    
	}

	transientAttrs.removeAllElements();
    }
}
