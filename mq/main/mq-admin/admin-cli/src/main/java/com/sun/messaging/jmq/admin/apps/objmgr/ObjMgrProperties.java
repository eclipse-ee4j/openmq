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
 * @(#)ObjMgrProperties.java	1.7 06/28/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.util.Properties;
import java.util.Enumeration;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;


/**
 * This class encapsulates the information that the user
 * has provided to perform any JMS Object Administration
 * task. It contains properties that describe:
 * <UL>
 * <LI>the type of command
 * <LI>the object lookup name
 * <LI>attributes of the object to create
 * <LI>attributes of the object store to act upon
 * </UL>
 *
 * This class has a number of convenience methods to extract
 * the information above. Currently, each of these methods
 * has a get() and a get(commandIndex) version. The version
 * that takes a commandIndex is currently not supported.
 * It is for handling the case where multiple commands are
 * stored in one ObjMgrProperties object.
 *
 * <P>
 * The String values returned by the get() methods can be
 * checked against the String constants defined in
 * ObjMgrOptions.
 *
 * @see		ObjMgrOptions
 */
public class ObjMgrProperties extends Properties
			implements ObjMgrOptions  {
    
    public ObjMgrProperties()  {
	super();
    }

    /**
     * Returns the command string. e.g. <EM>add</EM>.
     *
     * @return	The command string
     */
    public String getCommand()  {
	return (getCommand(-1));
    }
    /**
     * Returns the command string. e.g. <EM>add</EM>.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	The command string
     */
    public String getCommand(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(OBJMGR_CMD_PROP_NAME));
	}

	return (null);
    }

    /**
     * Returns the number of commands.
     *
     * @return	The number of commands.
     */
    public int getCommandCount()  {
	return (1);
    }

    /**
     * Returns the object lookup name.
     *
     * @return	The object lookup name.
     */
    public String getLookupName()  {
	return (getLookupName(-1));
    }
    /**
     * Returns the object lookup name.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	The object lookup name.
     */
    public String getLookupName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(OBJMGR_NAME_PROP_NAME));
	}

	return (null);
    }

    /**
     * Returns the object destination name.
     * This applies only if the object type was
     * a destination (queue or topic).
     *
     * @return	The object destination name.
     */
    public String getObjDestName()  {
	return (getObjDestName(-1));
    }
    /**
     * Returns the object destination name.
     * This applies only if the object type was
     * a destination (queue or topic).
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	The object destination name.
     */
    public String getObjDestName(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(OBJMGR_OBJ_ATTRS_PROP_NAME + "." + "name"));
	}

	return (null);
    }

    /**
     * Returns the object type.
     *
     * @return	The object type.
     */
    public String getObjType()  {
	return (getObjType(-1));
    }
    /**
     * Returns the object type.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	The object type.
     */
    public String getObjType(int commandIndex)  {
	if (commandIndex == -1)  {
	    return (getProperty(OBJMGR_TYPE_PROP_NAME));
	}

	return (null);
    }

    /**
     * Returns a Properties object containing the properties
     * that are relevant for creating a JMS object. These
     * properties are also normalized e.g. the preceding
     * <EM>obj.attrs.</EM> is stripped.
     *
     * @return	A Properties object containing properties
     *		needed for creation of a JMS object.
     */
    public Properties getObjProperties()  {
	return (getObjProperties(-1));
    }
    /**
     * Returns a Properties object containing the properties
     * that are relevant for creating a JMS object. These
     * properties are also normalized e.g. the preceding
     * <EM>obj.attrs.</EM> is stripped.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	A Properties object containing properties
     *		needed for creation of a JMS object.
     */
    public Properties getObjProperties(int commandIndex)  {
	Properties	props = new Properties();
	String		objAttrsStr = OBJMGR_OBJ_ATTRS_PROP_NAME + ".";
	int		objAttrsStrLen = objAttrsStr.length();

	if (commandIndex == -1)  {
            for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
		String propName = (String)e.nextElement();

		if (propName.startsWith(objAttrsStr))  {
		    String newPropName, value;

		    newPropName = propName.substring(objAttrsStrLen);
		    value = getProperty(propName);

		    props.put(newPropName, value);
		}
            }
	    
	    return (props);
	}

	return (null);
    }

    /**
     * Returns a ObjStoreAttrs object containing the attributes
     * that are relevant for creating an object store. These
     * attributes are also normalized e.g. the preceding
     * <EM>objstore.attrs.</EM> is stripped.
     *
     * @return	A ObjStoreAttrs object containing attributes
     *		needed for creation of an object store.
     */
    public ObjStoreAttrs getObjStoreAttrs()  {
        return (getObjStoreAttrs(-1));
    }
    /**
     * Returns a ObjStoreAttrs object containing the attributes
     * that are relevant for creating an object store. These
     * attributes are also normalized e.g. the preceding
     * <EM>objstore.attrs.</EM> is stripped.
     *
     * @param	commandIndex	Index for specifyng which
     *		command (for the case where multiple commands
     *		exist in the same ObjMgrProperties object).
     *				
     * @return	A ObjStoreAttrs object containing attributes
     *		needed for creation of an object store.
     */
    public ObjStoreAttrs getObjStoreAttrs(int commandIndex)  {
	ObjStoreAttrs	osa = new ObjStoreAttrs();
	String		objstoreAttrsStr = OBJMGR_OBJSTORE_ATTRS_PROP_NAME + ".";
	int		objstoreAttrsStrLen = objstoreAttrsStr.length();

	if (commandIndex == -1)  {
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

	return (null);
    }

    /**
     * Returns whether force mode was specified by the user.
     * Force mode is when no user interaction will be needed.
     * i.e. if storing an object, and an object with the same
     * lookup name already exists, no overwrite confirmation
     * will be asked, the object is overwritten.
     *
     * @return	true if force mode is set, false if force mode
     *		was not set.
     */
    public boolean forceModeSet()  {
	String s = getProperty(OBJMGR_FORCE_PROP_NAME);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns whether read-only flag was specified by the user.
     * Read0only mode is the setting that the object should
     * be created or updated as.
     *
     * @return	true if read-only is set, false if read-only
     *		was not set.
     */
    public String readOnlyValue()  {
	String s = getProperty(OBJMGR_READONLY_PROP_NAME);

	return s;

    }

    /**
     * Returns whether preview mode was specified by the user.
     * Preview mode is when the action specified is not really
     * executed, but a 'preview' of what would be done is
     * shown.
     *
     * @return	true if preview mode is set, false if preview mode
     *		was not set.
     */
    public boolean previewModeSet()  {
	String s = getProperty(OBJMGR_PREVIEW_PROP_NAME);

	if (s == null)  {
	    return (false);
	}

	if (s.equalsIgnoreCase(Boolean.TRUE.toString()))  {
	    return (true);
	} else if (s.equalsIgnoreCase(Boolean.FALSE.toString()))  {
	    return (false);
	}

	return (false);
    }

    /**
     * Returns the input filename specified by the user.
     *
     * @return	Input filename specified by the user
     */
    public String getInputFileName()  {
	return(getProperty(OBJMGR_INPUTFILE_PROP_NAME));
    }

    /**
     * Returns an Attributes object containing the attributes
     * that are relevant when binding an object. These
     * attributes are also normalized e.g. the preceding
     * <EM>objstore.binding.attrs.</EM> is stripped.
     *
     * @return  An Attributes object containing attributes
     *          needed for binding an object.
     */
    public Attributes getBindAttrs()  {
        return (getBindAttrs(-1));
    }
    /**
     * Returns an Attributes object containing the attributes
     * that are relevant when binding an object. These
     * attributes are also normalized e.g. the preceding
     * <EM>objstore.binding.attrs.</EM> is stripped.
     *
     * @param   commandIndex    Index for specifyng which
     *          command (for the case where multiple commands
     *          exist in the same ObjMgrProperties object).
     *                          
     * @return  An Attributes object containing attributes
     *          needed for binding an object.
     */
    public Attributes getBindAttrs(int commandIndex)  {
        Attributes bindAttrs = new BasicAttributes();
        String     objstoreBindAttrsStr = OBJMGR_OBJSTORE_BIND_ATTRS_PROP_NAME + ".";
        int        objstoreBindAttrsStrLen = objstoreBindAttrsStr.length();

        if (commandIndex == -1)  {
            for (Enumeration e = propertyNames();  e.hasMoreElements() ;) {
                String propName = (String)e.nextElement();

                if (propName.startsWith(objstoreBindAttrsStr))  {
                    String newPropName, value;

                    newPropName = propName.substring(objstoreBindAttrsStrLen);
                    value = getProperty(propName);

                    bindAttrs.put(newPropName, value);
                }
            }

            return (bindAttrs);
        }

        return (null);
    }
}

