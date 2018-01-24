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
 * @(#)ObjStore.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.admin.objstore;

import java.util.Vector;

import javax.naming.directory.Attributes;


/**
 * This class represents a general object store for the JMS administered
 * object management.
 *
 * Issues to be resolved:
 * 1.  Do we want to make sure that the object type to add / delete is
 *     of type Destination / ConnectionFactory?  If we do, then we
 *     should throw a specific type of an exception... or should we
 *     care?
 */
public interface ObjStore {

    /**
     * Opens the store.
     * Ideally, this is when the validation of ObjStoreAttrs should take 
     * place.  However, JNDI does not specify when to validate; it is 
     * either at the creation of initialContext or at the time of an 
     * operation execution.
     *
     * @exception ObjStoreException    	    if an error occurs
     */
    public void open() throws ObjStoreException;

    /**
     * Closes the store.
     *
     * @exception ObjStoreException 	     if an error occurs
     */
    public void close() throws ObjStoreException;

    /**
     * Adds a JMS administered object defined by obj to the
     * store.  The NameAlreadyExistsException is thrown only when overwrite is 
     * false.
     *
     * @param lookupName  binding name used to identify the object
     * @param Object  	  JMS administered object to store
     * @param overwrite   flag indicating whether to overwrite the existing
     *  		  object or not
     *
     * @exception NameAlreadyExistsException  if lookupName already exists
     * @exception ObjStoreException  	      object type is invalid or error 
     *					      occurs
     */
    public void add(String lookupName, Object obj, boolean overwrite) 
	throws ObjStoreException;

    /**
     * Adds a JMS administered object defined by obj to the
     * store.  The NameAlreadyExistsException is thrown only when overwrite is 
     * false.
     *
     * @param lookupName  binding name used to identify the object
     * @param Object      JMS administered object to store
     * @param bindAttrs   binding attributes used to set the attributes of
     *                    the object to be bound
     * @param overwrite   flag indicating whether to overwrite the existing
     *                    object or not
     *
     * @exception NameAlreadyExistsException  if lookupName already exists
     * @exception ObjStoreException           object type is invalid or error 
     *                                        occurs
     */
    public void add(String lookupName, Object obj, 
		    Attributes bindAttrs, boolean overwrite)
        throws ObjStoreException;

    /**
     * Deletes a JMS administered object from the store.
     *
     * @param lookupName  name of the object to delete 
     *
     * @exception NameNotFoundException  if lookupName does not exist
     *            			 in the store
     * @exception ObjStoreException      if an error occurs
     */
    public void delete(String lookupName) throws ObjStoreException;

    /**
     * Retrieves the instance of the administered object.
     * If the object with 'lookupName' is not found, this will
     * return null.
     *
     * @param lookupName  name of the object to retrieve 
     *
     * @return Object     a retrieved administered object reference
     *
     * @exception ObjStoreException  if an error occurs
     */
    public Object retrieve(String lookupName) throws ObjStoreException; 

    /**
     * Returns a Vector of all the JMS administered 
     * objects.
     *
     * @return Vector  vector of objects
     *
     * @exception ObjStoreException  if an error occurs
     *
     * REVISIT: 
     * NOTE: Currently, the returned vector of objects is store-dependent.
     *       In the case of JNDI, it will return a vector of 
     *       javax.naming.NameDClassPair.
     */
    public Vector list() throws ObjStoreException;

    /**
     * Returns a Vector of all the JMS administered objects of type 
     * defined by 'type'.
     *
     * @param type  	types of JMS administered objects to list
     *
     * @return Vector   vector of objects
     *
     * @exception ObjStoreException  if an error occurs
     *
     * REVISIT: We need to define what types are valid by creating constants.
     *          So far this method is not implemented by anybody.
     */
    public Vector list(int[] type) throws ObjStoreException;

    /**
     * Returns true if the store is open.  Returns false otherwise.
     *
     * @return  true or false
     */
    public boolean isOpen();

    /**
     * Returns the unique id associated with this store.
     *
     * @return  String  unique ID associated with this store
     */
    public String getID();


    public String getDescription();

    /**
     * Sets the ObjStore attributes.
     *
     * @param attrs  new ObjStoreAttrs 
     *
     * @exception ObjStoreException  if an error occurs
     */
    public void setObjStoreAttrs(ObjStoreAttrs newAttrs) 
	throws ObjStoreException;

    /**
     * Returns the ObjStoreAtts.
     *
     * @return ObjStoreAttrs  ObjStoreAttrs associated with this class.
     */
    public ObjStoreAttrs getObjStoreAttrs();

    /**
     * Checks for user's authentication info.
     *
     * @param osa  ObjStoreAttrs that the user has initially specified
     *
     * @return Vector  names of missing security attributes.  If none,
     *                 the size of the Vector is 0.
     */
    public Vector checkAuthentication(ObjStoreAttrs osa);

    /**
     * Adds a pair of name-value attribute to the ObjStoreAttrs.
     *
     * @param name  name of the attribute
     * @param value  value of the attribute specified by name
     */ 
    public void addObjStoreAttr(String name, String value);

    // to be determined
    public void search();
}
