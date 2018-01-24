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
 * @(#)ObjStoreListProperties.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.util.Enumeration;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.UserProperties;
import com.sun.messaging.jmq.admin.util.UserPropertiesException;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/**
 * This class encapsulates the information needed to save
 * and restore the list of <EM>favourite</EM> object stores in the
 * JMQ Administration Console.
 * 
 * <P>
 * It represents the property object containing the list of
 * object store attributes. The format of the property file
 * is:
 * <PRE>
 * objstore.count=5
 * objstore0.id.<STRONG>objstore id</STRONG>
 * objstore0.desc.<STRONG>objstore description</STRONG>
 * objstore0.attrs.<STRONG>attrName=value</STRONG>
 * objstore0.attrs.<STRONG>attrName=value</STRONG>
 * objstore1.id.<STRONG>objstore id</STRONG>
 * objstore1.desc.<STRONG>objstore description</STRONG>
 * objstore1.attrs.<STRONG>attrName=value</STRONG>
 * objstore1.attrs.<STRONG>attrName=value</STRONG>
 * ...
 * objstore4.id.<STRONG>objstore id</STRONG>
 * objstore4.desc.<STRONG>objstore description</STRONG>
 * objstore4.attrs.<STRONG>attrName=value</STRONG>
 * objstore4.attrs.<STRONG>attrName=value</STRONG>
 * </PRE>
 */
public class ObjStoreListProperties extends UserProperties  {
    
    public final static String		FIRST_VERSION = "2.0";
    public final static String		VERSION = "2.0";

    private final static String		PROP_NAME_VERSION = "version";
    private final static String		PROP_NAME_OBJSTORE_BASENAME = "objstore";
    private final static String		PROP_NAME_OBJSTORE_COUNT 
				= PROP_NAME_OBJSTORE_BASENAME + ".count";
    private final static String		PROP_NAME_OBJSTORE_ID_PREFIX = "id";
    private final static String		PROP_NAME_OBJSTORE_DESC_PREFIX = "desc";
    private final static String		PROP_NAME_OBJSTORE_ATTR_PREFIX = "attrs";
    private static AdminConsoleResources acr = Globals.getAdminConsoleResources();

    /**
     * Instantiate a ObjStoreListProperties object.
     */
    public ObjStoreListProperties()  {
	super();
	setProperty(PROP_NAME_VERSION, VERSION);
    }

    public String getVersion()  {
	String val =  getProperty(PROP_NAME_VERSION);

	return (val);
    }

    /**
     * Returns the number of object stores.
     *
     * @return	The number of object stores.
     */
    public int getObjStoreCount()  {
	String val =  getProperty(PROP_NAME_OBJSTORE_COUNT);
	int	ret;

	if ((val == null) || val.equals(""))  {
	    return (0);
	}

	try  {
	    ret = Integer.parseInt(val);
	} catch (Exception e)  {
	    ret = 0;
	}

	return (ret);
    }

    /**
     * Returns an ObjStoreAttrs array containing the ObjStoreAttrs
     * objects that can be used to create object stores.
     * <P>
     * The attributes in the ObjStoreAttrs object are normalized e.g. 
     * the preceding
     * <EM>objstore1.attrs.</EM> is stripped.
     *
     * @return	Returns an ObjStoreAttrs array containing the 
     *		ObjStoreAttrs objects that can be used to create 
     *		object stores.
     */
    public ObjStoreAttrs[] getObjStoreAttrs()  {
	int count = getObjStoreCount();


	if (count <= 0)  {
	    return (null);
	}

	ObjStoreAttrs[] objStoreArray = new ObjStoreAttrs [ count ];

	for (int i = 0; i < count; ++i)  {
	    objStoreArray[i] = getObjStoreAttrs(i);
	}

        return (objStoreArray);
    }

    /**
     * Returns a ObjStoreAttrs object containing the attributes
     * that are relevant for creating an object store. These
     * attributes are normalized e.g. the preceding
     * <EM>objstore.attrs.</EM> is stripped.
     *
     * @param	index	Index for specifying the
     *		object store in question.
     *				
     * @return	A ObjStoreAttrs object containing attributes
     *		needed for creation of an object store.
     */
    public ObjStoreAttrs getObjStoreAttrs(int index)  {
	//ObjStoreAttrs	osa = new ObjStoreAttrs();
	ObjStoreAttrs	osa;
	String		basePropName, objstoreAttrsStr, idPropName, descPropName;
	String		id, desc;
	int		objstoreAttrsStrLen;

	basePropName = PROP_NAME_OBJSTORE_BASENAME
				+ Integer.toString(index)
				+ ".";

	idPropName = basePropName
			+ PROP_NAME_OBJSTORE_ID_PREFIX;
	id = getProperty(idPropName, "");

	descPropName = basePropName
			+ PROP_NAME_OBJSTORE_DESC_PREFIX;
	desc = getProperty(descPropName, "");

	osa = new ObjStoreAttrs(id, desc);

	/*
	 * Construct string:
	 *	objstore1.attrs.
	 */
	objstoreAttrsStr = PROP_NAME_OBJSTORE_BASENAME
				+ Integer.toString(index)
				+ "."
				+ PROP_NAME_OBJSTORE_ATTR_PREFIX
				+ ".";

	objstoreAttrsStrLen = objstoreAttrsStr.length();

        for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
	    String propName = (String)e.nextElement();

	    if (propName.startsWith(objstoreAttrsStr))  {
	        String newPropName, value;
                newPropName = propName.substring(objstoreAttrsStrLen);
                value = getProperty(propName);
    
                osa.put(newPropName, value);
            }
        }
	    
	return (osa);
    }

    public void addObjStoreAttrs(ObjStoreAttrs osa)  {
	String	basePropName, objstoreAttrsStr, idPropName, descPropName;
	String	id, desc;
	int	index = getObjStoreCount();

	/*
	System.err.println("Setting properties:");
	*/

	basePropName = PROP_NAME_OBJSTORE_BASENAME
				+ Integer.toString(index)
				+ ".";

	idPropName = basePropName
			+ PROP_NAME_OBJSTORE_ID_PREFIX;
	id = osa.getID();
	setProperty(idPropName, id);
	/*
	System.err.println("\t"
			+ idPropName
			+ "="
			+ id);
	*/

	descPropName = basePropName
			+ PROP_NAME_OBJSTORE_DESC_PREFIX;
	desc = osa.getDescription();
	setProperty(descPropName, desc);
	/*
	System.err.println("\t"
			+ descPropName
			+ "="
			+ desc);
	*/


	objstoreAttrsStr = basePropName
				+ PROP_NAME_OBJSTORE_ATTR_PREFIX
				+ ".";

	for (Enumeration e = osa.keys();  e.hasMoreElements() ;) {
	    String propName = (String)e.nextElement(), 
				newPropName, newValue;
	    
            newValue = (String)osa.get(propName);
	    newPropName = objstoreAttrsStr
				+ propName;
	    
	    setProperty(newPropName, newValue);
	    /*
	    System.err.println("\t" 
			+ newPropName
			+ "="
			+ newValue);
	    */
	}

	index++;
	setProperty(PROP_NAME_OBJSTORE_COUNT, Integer.toString(index));
	/*
	System.err.println("\t" 
			+ PROP_NAME_OBJSTORE_COUNT
			+ "="
			+ index);
	*/
    }

    public void load() throws UserPropertiesException, SecurityException  {
	super.load();

	String v = getVersion();

	/*
	 * If no version property found, assume the file is the current version
	 * (i.e. OK)
	 */
	if (v == null)  {
	    return;
	}

        checkVersion(v, VERSION, FIRST_VERSION);
    }

    private void checkVersion(String fileVersionStr, String expectedVersionStr,
				String firstVersionStr) throws UserPropertiesException  {
	double	expectedVersion, fileVersion, firstVersion;

	/*
	 * Convert current version string to double.
	 */
	try  {
	    expectedVersion = Double.parseDouble(expectedVersionStr);
	} catch (NumberFormatException nfe)  {
	    UserPropertiesException upe;

	    expectedVersion = 0;
	    upe = new UserPropertiesException(
		acr.getString(acr.E_BAD_INT_OBJSTORE_LIST_VER, expectedVersionStr));
	    throw(upe);
	}

	/*
	 * Convert first version string to double.
	 */
	try  {
	    firstVersion = Double.parseDouble(firstVersionStr);
	} catch (NumberFormatException nfe)  {
	    UserPropertiesException upe;

	    firstVersion = 0;
	    upe = new UserPropertiesException(
		acr.getString(acr.E_BAD_INT_OBJSTORE_LIST_VER, firstVersionStr));
	    throw(upe);
	}

	/*
	 * Convert file version string to double.
	 */
	try  {
	    fileVersion = Double.parseDouble(fileVersionStr);
	} catch (NumberFormatException nfe)  {
	    UserPropertiesException upe;
	    Object args[] = new Object [ 4 ];

	    fileVersion = 0;

	    args[0] = getAbsoluteFileName();
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = acr.getString(acr.E_BAD_FILE_OBJSTORE_LIST_VER, args);

	    upe = new UserPropertiesException(s);
	    throw(upe);
	}

	/*
	 * File version is less than our first version - error !
	 */
	if (fileVersion < firstVersion)  {
	    UserPropertiesException upe;
	    Object args[] = new Object [ 4 ];

	    args[0] = getAbsoluteFileName();
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = acr.getString(acr.E_BAD_FILE_OBJSTORE_LIST_VER, args);

	    upe = new UserPropertiesException(s);
	    throw (upe);
	}

	/*
	 * File version is greater than our current version - error !
	 */
	if (fileVersion > expectedVersion)  {
	    UserPropertiesException upe;
	    Object args[] = new Object [ 4 ];

	    args[0] = getAbsoluteFileName();
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = acr.getString(acr.E_BAD_FILE_OBJSTORE_LIST_VER, args);

	    upe = new UserPropertiesException(s);
	    throw (upe);
	}

	/*
	 * Add checks for 
	 *	firstVersion < fileVersion < expectedVersion
	 * here.
	 * Currently we don't have any - since this is our first official
	 * version.
	 */
    }
}
