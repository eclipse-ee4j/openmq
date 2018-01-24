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
 * @(#)CmdRunner.java	1.29 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.io.*;
import java.util.Properties;
import java.util.Vector;

import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.NameClassPair;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.InvalidPropertyException;
import com.sun.messaging.InvalidPropertyValueException;
import com.sun.messaging.ReadOnlyPropertyException;
import com.sun.messaging.naming.MissingVersionNumberException;
import com.sun.messaging.naming.UnsupportedVersionNumberException;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.objstore.ObjStoreException;
import com.sun.messaging.jmq.admin.objstore.NameNotFoundException;
import com.sun.messaging.jmq.admin.objstore.NameAlreadyExistsException;
import com.sun.messaging.jmq.admin.objstore.InitializationException;
import com.sun.messaging.jmq.admin.objstore.AuthenticationException;
import com.sun.messaging.jmq.admin.objstore.AuthenticationNotSupportedException;
import com.sun.messaging.jmq.admin.objstore.GeneralNamingException;
import com.sun.messaging.jmq.admin.objstore.NoPermissionException;
import com.sun.messaging.jmq.admin.objstore.CommunicationException;
import com.sun.messaging.jmq.admin.objstore.SchemaViolationException;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.util.Password;

/** 
 * This class contains the logic to execute the user commands
 * specified in the ObjMgrProperties object. It has one
 * public entry point which is the runCommands() method. It
 * is expected to display to the user if the command execution
 * was successful or not.
 * @see  ObjMgr
 *
 */
public class CmdRunner implements ObjMgrOptions  {

    private static AdminResources ar = Globals.getAdminResources();
    private ObjMgrProperties objMgrProps;

    /**
     * Constructor
     */
    public CmdRunner(ObjMgrProperties props) {
	this.objMgrProps = props;
    } 

    /*
     * Run/execute the user commands specified in the ObjMgrProperties object.
     */
    public int runCommands() {
	int exitcode = 0;

	ObjStore os = null;

	/*
	 * Open/Initialize the object store
        os = openStore(objMgrProps);
	if (os == null)
	    return (1);
	 */

	/*
	 * Determine type of command and invoke the relevant check method
	 * to verify the contents of the ObjMgrProperties object.
	 *
	 */
	String cmd = objMgrProps.getCommand();
	if (cmd.equals(OBJMGR_ADD_PROP_VALUE))  {
            exitcode = runAddCommand(objMgrProps);
	} else if (cmd.equals(OBJMGR_DELETE_PROP_VALUE))  {
            exitcode = runDeleteCommand(objMgrProps);
	} else if (cmd.equals(OBJMGR_QUERY_PROP_VALUE))  {
            exitcode = runQueryCommand(objMgrProps);
	} else if (cmd.equals(OBJMGR_LIST_PROP_VALUE))  {
            runListCommand(objMgrProps);
	} else if (cmd.equals(OBJMGR_UPDATE_PROP_VALUE))  {
            exitcode = runUpdateCommand(objMgrProps);
	}

	return (exitcode);
    }

    /*
     * 02/05/2001
     * Creates a store.
     */
    private ObjStore createStore(ObjStoreAttrs osa) {
        ObjStore os = null;

        if (osa == null)  {
            return (null);
        }

        /*
         * Create ObjStore
         */
        try  {
            ObjStoreManager osmgr =
                ObjStoreManager.getObjStoreManager();
            os = osmgr.createStore(osa);

        } catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_CANNOT_LOC_TREE));
            os = null;

        } catch (ObjStoreException e) {
            handleRunCommandExceptions(e);
            os = null;
        }
        return (os);
    }

    /*
     * 02/05/2001
     * Opens an existing store.
     */
    private void openStore(ObjStore os) throws ObjStoreException {
        os.open();
    }

    /*
     * Not used
     *
     * Tries to open a connection to the object store
     * based on the properties/attributes specified.
     * If an error occurred, no exception is thrown,
     * null is returned, and a error msg is printed to stderr.
    private ObjStore openStore(ObjMgrProperties objMgrProps) {
	ObjStore os = null;

	// Get ObjStoreAttrs
	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();

	return (openStore(osa));
    }
     */

    /*
     * Tries to open a connection to the object store
     * based on the properties/attributes specified.
     * If an error occurred, no exception is thrown,
     * null is returned, and a error msg is printed to stderr.
     */
    private ObjStore openStore(ObjStoreAttrs osa) {
	ObjStore os = null;

	if (osa == null)  {
	    return (null);
	}

	/*
	 * Create ObjStore
	 */
	try  {
	    ObjStoreManager osmgr = 
		ObjStoreManager.getObjStoreManager();
	    os = osmgr.createStore(osa);
	    os.open();

        } catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_CANNOT_LOC_TREE));
	    os = null;
	} catch (ObjStoreException e) {
	    handleRunCommandExceptions(e);
	    os = null;
	}

	return (os);
    }


    private void printAddCmdDescription(Object newObj,
				String type,
				String lookupName,
				ObjStoreAttrs osa,
				String readOnlyValue) {
	Globals.stdOutPrintln(ar.getString(ar.I_ADD_CMD_DESC_INTRO,
				Utils.getObjTypeString(type)));
	Globals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(2, 4);
        omp.printObjPropertiesFromObj((AdministeredObject)newObj);
	Globals.stdOutPrintln("");

	Globals.stdOutPrintln(ar.getString(ar.I_ADD_CMD_DESC_LOOKUP));
	Globals.stdOutPrintln("");
	Globals.stdOutPrintln(lookupName);
	Globals.stdOutPrintln("");

	ObjMgrPrinter.printReadOnly(readOnlyValue);
	Globals.stdOutPrintln("");

	Globals.stdOutPrintln(ar.getString(ar.I_ADD_CMD_DESC_STORE));
	Globals.stdOutPrintln("");

        ObjMgrPrinter omp2 = new ObjMgrPrinter(osa, 2, 4);
        omp2.print();
	Globals.stdOutPrintln("");
    }

    private int runAddCommand(ObjMgrProperties objMgrProps) {
        ObjStore os;
	int exitcode = 0;

	String input = null;
	Object object = null;

	String yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	/*
	 * Get object type, props object, and lookup name
	 */
	String type = objMgrProps.getObjType();
	Properties objProps = objMgrProps.getObjProperties();
	String lookupName = objMgrProps.getLookupName();

	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
	/*
	 * Check for any mandatory bind attrs and display warning,
	 * if necessary.
	 */
	checkObjStoreAttrs(osa);

        Attributes bindAttrs = objMgrProps.getBindAttrs();
	
	/*
	 * Check if -f (force) was specified on cmd line.
	 */
	boolean force = objMgrProps.forceModeSet();

	/*
	 * Create JMS Object with the specified properties.
	 */
	Object newObj = null;
	try {
 
            if (type.equals(OBJMGR_TYPE_QUEUE)) {
	        newObj = JMSObjFactory.createQueue(objProps);
            } else if (type.equals(OBJMGR_TYPE_TOPIC)) {
	        newObj = JMSObjFactory.createTopic(objProps);
            } else if (type.equals(OBJMGR_TYPE_QCF)) {
	        newObj = JMSObjFactory.createQueueConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_TCF)) {
	        newObj = JMSObjFactory.createTopicConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_CF)) {
	        newObj = JMSObjFactory.createConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_XTCF)) {
	        newObj = JMSObjFactory.createXATopicConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_XQCF)) {
	        newObj = JMSObjFactory.createXAQueueConnectionFactory(objProps);
            } else if (type.equals(OBJMGR_TYPE_XCF)) {
	        newObj = JMSObjFactory.createXAConnectionFactory(objProps);
            }

        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
            return(1);
        } 

	/*
	 * Set this newly created obj to read-only if specified.
	 */
	JMSObjFactory.doReadOnlyForAdd(newObj, objMgrProps.readOnlyValue());

        printAddCmdDescription(newObj, type, lookupName, osa,
				objMgrProps.readOnlyValue());

        os = createStore(osa);
        if (os == null)  {
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
            return(1);
        }

        /*
         * Prompt for missing authentication info BEFORE opening the store.
         */
        if (!force) {
            // Update ObjStoreAttrs in ths ObjStore if some security
            // info was missing.
            os = promptForAuthentication(os);
        }

        /*
         * Open/Initialize the object store
         */
        try {
            openStore(os);

        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
            return(1);
        }

        /*
         * If no -f option, check if the object already exists
         * so we can confirm with user to overwrite.
         */
	if (!force) {
	    try  {
	        object = os.retrieve(lookupName);

            } catch (NameNotFoundException nnfe) {
		// Make sure that this exception is NOT treated as an error for add
		;

	    } catch (Exception e) {
		handleRunCommandExceptions(e, lookupName);
	        Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
		return(1);
	    }

	    // Object already exists so confirm with user.
	    if (object != null) {
	        Globals.stdOutPrintln(
		    ar.getCString(ar.I_WARNING_MESG),
		    ar.getString(ar.W_OBJ_ALREADY_EXISTS, lookupName));
		     
	        Globals.stdOutPrintln(
		    ar.getCString(ar.I_WARNING_MESG),
		    ar.getString(ar.W_ADD_OBJ_BE_OVERWRITTEN));

	        input = getUserInput(ar.getString(ar.Q_OVERWRITE_OK), noShort);
	    }
	}

	if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(
		ar.getString(ar.I_OBJ_NOT_ADDED));

	/*
	 * Case where user typed 'y' or 'yes' to overwrite OR	
	 * case where object doesn't exist yet so no confirmation needed OR
	 * case where user used -f.
	 */
	} else if ((yesShort.equalsIgnoreCase(input) || 
			yes.equalsIgnoreCase(input)) ||
		   (object == null) || 
		   (force)) {

	    /*
	     * Update the object in object store.
	     */
	    try  {
                if (bindAttrs.size() > 0)
                    os.add(lookupName, newObj, bindAttrs, true);
                else
                    os.add(lookupName, newObj, true);

	    } catch (NameAlreadyExistsException naee) {
		; // Should never happen, since we pass true to add

	    } catch (NameNotFoundException nnfe) {
                Globals.stdErrPrintln(
                    ar.getString(ar.I_ERROR_MESG),
                    ar.getKString(ar.E_CANNOT_LOC_TREE));
	        exitcode = 1;

	    } catch (Exception e)  {
		handleRunCommandExceptions(e, lookupName);
	        exitcode = 1;
	    }

	    if (exitcode == 0)  {
	        Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADDED));
	    } else  {
	        Globals.stdOutPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
	    }
	} else {
	    Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_ADDED));
	    exitcode = 1;
	}

	return (exitcode);
    }

    private void printDeleteCmdDescription(String lookupName,
				ObjStoreAttrs osa) {
	Globals.stdOutPrintln(ar.getString(ar.I_DELETE_CMD_DESC_INTRO));
	Globals.stdOutPrintln("");
	Globals.stdOutPrintln(lookupName);
	Globals.stdOutPrintln("");

	Globals.stdOutPrintln(ar.getString(ar.I_DELETE_CMD_DESC_STORE));
	Globals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
	Globals.stdOutPrintln("");
    }

    private int runDeleteCommand(ObjMgrProperties objMgrProps) {

	int exitcode = 0;

	/*
	 * Get lookup Name.
	 */
	String lookupName = objMgrProps.getLookupName();
	Object object = null;
	String input = null;
        ObjStore os;
	String yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
	/*
	 * Check for any mandatory bind attrs and display warning,
	 * if necessary.
	 */
	checkObjStoreAttrs(osa);

        printDeleteCmdDescription(lookupName, osa);

        os = createStore(osa);
        if (os == null)  {
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
            return(1);
        }

        /*
         * Prompt for missing authentication info BEFORE opening the store.
         */
        if (!force) {
            // Update ObjStoreAttrs in ths ObjStore if some security
            // info was missing.
            os = promptForAuthentication(os);
        }

        /*
         * Open/Initialize the object store.
         */
        try {
            openStore(os);

        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
            return(1);
        }

        /*
	 * Retrieve the object by its lookup name.
         */
	try  {
	    object = os.retrieve(lookupName);

	} catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getKString(ar.E_CANNOT_LOC_OBJ, lookupName));

	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
	    return (1);
	} catch (Exception e)  {
	    handleRunCommandExceptions(e, lookupName);

	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
	    return (1);
	}
	
	/*
	 * Delete the object if it exists.
	 */
	if (object != null) {

	    if (!force) {
	        input = getUserInput(ar.getString(ar.Q_DELETE_OK), noShort);
	    }

	    if (yesShort.equalsIgnoreCase(input) || yes.equalsIgnoreCase(input) 
		|| force) {
	        try  {
	            os.delete(lookupName);

	        } catch (Exception e)  {
		    handleRunCommandExceptions(e, lookupName);
		    exitcode = 1;
	        }

		if (exitcode == 0)  {
		    Globals.stdOutPrintln("");
	            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETED));
		} else  {
		    Globals.stdOutPrintln("");
	            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
		}

            } else if (noShort.equalsIgnoreCase(input) || 
		       no.equalsIgnoreCase(input)) {
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_DELETED));

	    } else {
                Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_DELETED));
		exitcode = 1;
	    }
	} else {  
	// object == null
	// Should not happen, since if the object cannot be retrieved, 
	// it should throw NameNotFoundException
            Globals.stdErrPrintln(ar.getKString(ar.E_CANNOT_LOC_OBJ), lookupName);
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_DELETE_FAILED));
	    exitcode = 1;
	}

	return (exitcode);
    }

    private void printQueryCmdDescription(String lookupName,
				ObjStoreAttrs osa) {
	Globals.stdOutPrintln(ar.getString(ar.I_QUERY_CMD_DESC_INTRO));
	Globals.stdOutPrintln("");
	Globals.stdOutPrintln(lookupName);
	Globals.stdOutPrintln("");

	Globals.stdOutPrintln(ar.getString(ar.I_QUERY_CMD_DESC_STORE));
	Globals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
	Globals.stdOutPrintln("");
    }

    private int runQueryCommand(ObjMgrProperties objMgrProps) {
	int exitcode = 0;

	/*
	 * Get Properties object containing obj attrs
	 */
	String lookupName = objMgrProps.getLookupName();
	Object object = null;
        ObjStore os;

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
	/*
	 * Check for any mandatory bind attrs and display warning,
	 * if necessary.
	 */
	checkObjStoreAttrs(osa);

        printQueryCmdDescription(lookupName, osa);

        os = createStore(osa);
        if (os == null)  {
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_QUERY_FAILED));
            return(1);
        }

        /*
         * Prompt for missing authentication info BEFORE opening the store.
         */
        if (!force) {
            // Update ObjStoreAttrs in ths ObjStore if some security
            // info was missing.
            os = promptForAuthentication(os);
        }

        /*
         * Open/Initialize the object store.
         */
        try {
            openStore(os);

        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_QUERY_FAILED));
            return(1);
        }

        /*
	 * Retrieve the object by its lookup name.
         */
	try  {
	    object = os.retrieve(lookupName);

	} catch (NameNotFoundException nnfe)  {
	    Globals.stdErrPrintln(
	  	ar.getKString(ar.E_CANNOT_LOC_OBJ, lookupName));
	    Globals.stdOutPrintln("");
	    Globals.stdErrPrintln(ar.getString(ar.I_OBJ_QUERY_FAILED));
	    return (1);

	} catch (Exception e)  {
	    handleRunCommandExceptions(e, lookupName);
	    Globals.stdOutPrintln("");
	    Globals.stdErrPrintln(ar.getString(ar.I_OBJ_QUERY_FAILED));
	    return (1);
	}

	if (object != null) {
	    /*
	     * Print the object.
	     */
       	    ObjMgrPrinter omp = new ObjMgrPrinter(2, 4);
	    omp.printJMSObject(object);
	    Globals.stdOutPrintln("");

	    ObjMgrPrinter.printReadOnly(((AdministeredObject)object).isReadOnly());
	} else {  
	// object == null
	// Should not happen, since if the object cannot be retrieved, 
	// it should throw NameNotFoundException
	    Globals.stdErrPrintln(
	  	ar.getKString(ar.E_CANNOT_LOC_OBJ, lookupName));
	    
	    exitcode = 1;
	}

	if (exitcode == 0)  {
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_QUERIED));
	} else  {
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_QUERY_FAILED));
	}

	return (exitcode);
    }

    private void printListCmdDescription(String type,
				ObjStoreAttrs osa) {
        String typeString = Utils.getObjTypeString(type);

	if (typeString == null)  {
	    Globals.stdOutPrintln(ar.getString(ar.I_LIST_CMD_DESC_INTRO));
	} else  {
	    Globals.stdOutPrintln(ar.getString(ar.I_LIST_CMD_DESC_INTRO_TYPE,
			typeString));
	}

	Globals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
	Globals.stdOutPrintln("");
    }

    private int runListCommand(ObjMgrProperties objMgrProps) {

	int exitcode = 0;

	/*
	 * Check if a type was specified otherwise
 	 * just list all jms objs.
	 */
        ObjStore os;
	String type = objMgrProps.getObjType();
	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();

	/*
	 * Check for any mandatory bind attrs and display warning,
	 * if necessary.
	 */
	checkObjStoreAttrs(osa);

        /*
         * Check if -f (force) was specified on cmd line.
         */
        boolean force = objMgrProps.forceModeSet();

        printListCmdDescription(type, osa);

        os = createStore(osa);
        if (os == null)  {
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_LIST_FAILED));
            return(1);
        }

        /*
         * Prompt for missing authentication info BEFORE opening the store.
         */
        if (!force) {
            // Update ObjStoreAttrs in ths ObjStore if some security
            // info was missing.
            os = promptForAuthentication(os);
        }

        /*
         * Open/Initialize the object store.
         */
        try {
            openStore(os);

        } catch (Exception e) {
            handleRunCommandExceptions(e);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_LIST_FAILED));
            return(1);
        }

	if (type != null) {
	    exitcode = listByType(os, type);
	}
	else {
	    exitcode = listAll(os);
	}

	if (exitcode == 0)  {
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_LISTED));
	} else  {
	    Globals.stdOutPrintln("");
	    Globals.stdErrPrintln(ar.getString(ar.I_OBJ_LIST_FAILED));
	}

	return (exitcode);
    }

    private void printUpdateCmdDescription(String type,
				String lookupName,
				Properties objProps,
				ObjStoreAttrs osa,
				String readOnlyValue) {
        String typeString = null;

	/*
	 * Commented out for now to avoid creation of the tempObj
	 * for now. If a bad object type was specified for update,
	 * the creation code would fail. The type checking code
	 * is later on in runUpdateCommand().
	 *
        typeString = Utils.getObjTypeString(type);
	*/

	if (typeString != null)  {
	    /*
	     * Create JMS Object with the specified properties.
	     * We don't really need to print all the object's
	     * attributes since we only want to show the attributes
	     * that will be updated. These attributes are stored in
	     * 'objProps'. The JMS Object is created here so we
	     * can get a hold of the attribute/property labels.
	     */
	    Object tempObj = null;
 
 	    try {
                if (type.equals(OBJMGR_TYPE_QUEUE)) {
	            tempObj = JMSObjFactory.createQueue(objProps);
                } else if (type.equals(OBJMGR_TYPE_TOPIC)) {
	            tempObj = JMSObjFactory.createTopic(objProps);
                } else if (type.equals(OBJMGR_TYPE_QCF)) {
	            tempObj = JMSObjFactory.createQueueConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_TCF)) {
	            tempObj = JMSObjFactory.createTopicConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_CF)) {
	            tempObj = JMSObjFactory.createConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_XTCF)) {
	            tempObj = JMSObjFactory.createXATopicConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_XQCF)) {
	            tempObj = JMSObjFactory.createXAQueueConnectionFactory(objProps);
                } else if (type.equals(OBJMGR_TYPE_XCF)) {
	            tempObj = JMSObjFactory.createXAConnectionFactory(objProps);
                }

 	    } catch (Exception e) {
                handleRunCommandExceptions(e, lookupName);
                Globals.stdOutPrintln("");
                Globals.stdOutPrintln(ar.getString(ar.I_OBJ_ADD_FAILED));
                return;
  	    }

	    Globals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_INTRO_TYPE,
			typeString));

	    Globals.stdOutPrintln(lookupName);
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_LOOKUP));
	    Globals.stdOutPrintln("");

            ObjMgrPrinter omp = new ObjMgrPrinter(objProps, 2, 4);
            omp.print();

	} else  {
	    Globals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_INTRO));
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(lookupName);
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_LOOKUP));
	    Globals.stdOutPrintln("");

            ObjMgrPrinter omp = new ObjMgrPrinter(objProps, 2, 4);
            omp.print();
	}
	Globals.stdOutPrintln("");

	ObjMgrPrinter.printReadOnly(readOnlyValue);
	Globals.stdOutPrintln("");

	Globals.stdOutPrintln(ar.getString(ar.I_UPDATE_CMD_DESC_STORE));
	Globals.stdOutPrintln("");

        ObjMgrPrinter omp = new ObjMgrPrinter(osa, 2, 4);
        omp.print();
	Globals.stdOutPrintln("");
    }

    private int runUpdateCommand(ObjMgrProperties objMgrProps) {
	int exitcode = 0;
	String input = null;
	Object object = null;
	String type = null;
        ObjStore os;
	String yes, yesShort, no, noShort;

	yes = ar.getString(ar.Q_RESPONSE_YES);
	yesShort = ar.getString(ar.Q_RESPONSE_YES_SHORT);
	no = ar.getString(ar.Q_RESPONSE_NO);
	noShort = ar.getString(ar.Q_RESPONSE_NO_SHORT);

	/*
	 * Get the lookup name
	 */
	String lookupName = objMgrProps.getLookupName();

	/*
	 * Check if -f (force) was specified on cmd line.
	 */
	boolean force = objMgrProps.forceModeSet();

	ObjStoreAttrs osa = objMgrProps.getObjStoreAttrs();
	/*
	 * Check for any mandatory bind attrs and display warning,
	 * if necessary.
	 */
	checkObjStoreAttrs(osa);

        Properties objProps = objMgrProps.getObjProperties();
	type = objMgrProps.getObjType();

        Attributes bindAttrs = objMgrProps.getBindAttrs();

        printUpdateCmdDescription(type, lookupName, objProps, osa,
				objMgrProps.readOnlyValue());

        os = createStore(osa);
        if (os == null)  {
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
            return(1);
        }

        /*
         * Prompt for missing authentication info BEFORE opening the store.
         */
        if (!force) {
            // Update ObjStoreAttrs in ths ObjStore if some security
            // info was missing.
            os = promptForAuthentication(os);
        }

        /*
         * Open/Initialize the object store.
         */
        try {
            openStore(os);

        } catch (Exception e) {
            handleRunCommandExceptions(e, lookupName);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
            return(1);
        }

        /*
	 * Updates only work if the object already exists
	 * so check if one already exists.
	 */
	try  {
	    object = os.retrieve(lookupName);

        } catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getKString(ar.E_CANNOT_LOC_OBJ, lookupName));
	    Globals.stdOutPrintln("");
            Globals.stdErrPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
	    return (1);

	} catch (Exception e)  {
	    handleRunCommandExceptions(e, lookupName);
	    Globals.stdOutPrintln("");
            Globals.stdErrPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
	    return (1);
	}

	/*
	 * Check here if the type being updated and the
	 * type specified match.
	 */
	if (object != null) {
	    type = checkObjectType(object, type);
	    if (type == null)  {
	        Globals.stdOutPrintln("");
                Globals.stdErrPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
		return (1);
	    }
	} else {
        // object == null
        // Should not happen, since if the object cannot be retrieved, 
        // it should throw NameNotFoundException
            Globals.stdErrPrintln(
                ar.getKString(ar.E_CANNOT_LOC_OBJ, lookupName));
	    Globals.stdOutPrintln("");
            Globals.stdErrPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
	    return (1);
        }

	if (!force) {
            // object not null
	    // Object already exists so confirm with user.
            //runQueryCommand(os, objMgrProps);

		// Check to see if the retrieved object's version is
		// compatible with the current product version.
		// No need to check for invalid/missing version number, as
		// an exception must have been already thrown if that 
		// was the case.
		if (object instanceof AdministeredObject) {
		    AdministeredObject adminObj = (AdministeredObject)object;
		    String curVersion = adminObj.getVERSION();
		    String objVersion = adminObj.getStoredVersion();

		    if (!adminObj.isStoredVersionCompatible()) {
			Globals.stdErrPrintln(ar.getString(ar.W_INCOMPATIBLE_OBJ, objVersion, curVersion));
		    }	
		}
	        input = getUserInput(ar.getString(ar.Q_UPDATE_OK), noShort);
	}

	if (noShort.equalsIgnoreCase(input) || no.equalsIgnoreCase(input)) {
	    Globals.stdOutPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_UPDATED));

	/*
	 * Case where user typed 'y' or 'yes' to overwrite OR	
	 * case where object doesn't exist yet so no confirmation needed OR
	 * case where user used -f.
	 */
	} else if (((yesShort.equalsIgnoreCase(input) || 
		     yes.equalsIgnoreCase(input)) ||
		   (force)) && 
		   (object != null)) {

	    /*
	     * Update the object with the new properties.
	     */
	    Object updatedObject = updateObject(object, type, objMgrProps);
	    if (updatedObject == null)
	        return 1;

	    /*
	     * Add the object to object store.
	     */
	    try  {
                if (bindAttrs.size() > 0)
                    os.add(lookupName, updatedObject, bindAttrs, true);
                else
                    os.add(lookupName, updatedObject, true);

	    } catch (NameAlreadyExistsException naee) {
		// Should never happen, since we pass true to add
		exitcode = 1;

	    } catch (Exception e)  {
		handleRunCommandExceptions(e, lookupName);
		exitcode = 1;
	    }

	    if (exitcode == 0)  {
		if (!force)
                    Globals.stdErrPrintln("");
	        Globals.stdOutPrintln(ar.getString(ar.I_OBJ_UPDATED));
	    } else  {
                Globals.stdErrPrintln("");
                Globals.stdErrPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
	    }
	} else {
	    Globals.stdOutPrintln(ar.getString(ar.I_UNRECOGNIZED_RES, input));
            Globals.stdErrPrintln("");
	    Globals.stdOutPrintln(ar.getString(ar.I_OBJ_NOT_UPDATED));

	    exitcode = 1;
	}
	
	return (exitcode);
    }

    private String checkObjectType(Object object, String type) {

	//
	// No type specified, so set the type to the same one
	// that we're updating.
	//
	if (type == null) {
	    if (object instanceof com.sun.messaging.Topic)
		type = OBJMGR_TYPE_TOPIC;
	    else if (object instanceof com.sun.messaging.Queue)
		type = OBJMGR_TYPE_QUEUE;
	    else if (object instanceof com.sun.messaging.XAQueueConnectionFactory)
		type = OBJMGR_TYPE_XQCF;
	    else if (object instanceof com.sun.messaging.XATopicConnectionFactory)
		type = OBJMGR_TYPE_XCF;
	    else if (object instanceof com.sun.messaging.XAConnectionFactory)
		type = OBJMGR_TYPE_XTCF;
	    else if (object instanceof com.sun.messaging.TopicConnectionFactory)
		type = OBJMGR_TYPE_TCF;
	    else if (object instanceof com.sun.messaging.QueueConnectionFactory)
		type = OBJMGR_TYPE_QCF;
	    else if (object instanceof com.sun.messaging.ConnectionFactory)
		type = OBJMGR_TYPE_CF;
	}
	//
	// Verify that the specified type is the same type as the 
	// object we're updating.
	//
        else if (!(((object instanceof com.sun.messaging.Topic) &&
               OBJMGR_TYPE_TOPIC.equals(type)) ||
            ((object instanceof com.sun.messaging.Queue) &&
               OBJMGR_TYPE_QUEUE.equals(type)) ||
            ((object instanceof com.sun.messaging.XATopicConnectionFactory) &&
               OBJMGR_TYPE_XTCF.equals(type)) ||
            ((object instanceof com.sun.messaging.XAQueueConnectionFactory) &&
               OBJMGR_TYPE_XQCF.equals(type)) ||
            ((object instanceof com.sun.messaging.XAConnectionFactory) &&
               OBJMGR_TYPE_XCF.equals(type)) ||
            ((object instanceof com.sun.messaging.QueueConnectionFactory) &&
               OBJMGR_TYPE_QCF.equals(type)) ||
            ((object instanceof com.sun.messaging.TopicConnectionFactory) &&
               OBJMGR_TYPE_TCF.equals(type)) ||
            ((object instanceof com.sun.messaging.ConnectionFactory) &&
               OBJMGR_TYPE_CF.equals(type)))) {

            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_OBJ_TYPES_NOT_SAME));

	    // Type mismatch.  Returns null.
	    return null;
        }
        return type;
    }

    /*
     * Called from runUpdateCommand() after we confirmed
     * the object can be updated.
     * Returns an object updated with the new properties.
     */ 
    private Object updateObject(Object object, String type,
			ObjMgrProperties objMgrProps) {

        Properties objProps = objMgrProps.getObjProperties();
	Object updatedObject = null;
	String readOnlyValue = objMgrProps.readOnlyValue();

	try {
            if (type.equals(OBJMGR_TYPE_QUEUE)) {
	        updatedObject = JMSObjFactory.updateQueue(object, objProps,
						      readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_TOPIC)) {
	        updatedObject = JMSObjFactory.updateTopic(object, objProps,
						      readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_QCF)) {
	        updatedObject = JMSObjFactory.updateQueueConnectionFactory
					(object, objProps, readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_TCF)) {
	        updatedObject = JMSObjFactory.updateTopicConnectionFactory
					(object, objProps, readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_CF)) {
	        updatedObject = JMSObjFactory.updateConnectionFactory
					(object, objProps, readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_XQCF)) {
	        updatedObject = JMSObjFactory.updateXAQueueConnectionFactory
					(object, objProps, readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_XTCF)) {
	        updatedObject = JMSObjFactory.updateXATopicConnectionFactory
					(object, objProps, readOnlyValue);
            } else if (type.equals(OBJMGR_TYPE_XCF)) {
	        updatedObject = JMSObjFactory.updateXAConnectionFactory
					(object, objProps, readOnlyValue);
            }

	} catch (Exception e) {
            handleRunCommandExceptions(e);
            Globals.stdOutPrintln("");
            Globals.stdOutPrintln(ar.getString(ar.I_OBJ_UPDATE_FAILED));
            return null;
	}

	return updatedObject;

    }

    /**
     * List JMS administration objects of particular type.
     */
    private int listByType(ObjStore os, String type) {
        Vector v = null;

        try {
            v = os.list();

        } catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_CANNOT_LOC_TREE));
	    return (1);

        } catch (Exception e) {
	    handleRunCommandExceptions(e);
	    return (1);
        }

	ObjMgrPrinter omp = new ObjMgrPrinter(2, 4);
	String[] row = new String[2];

	row[0] = ar.getString(ar.I_JNDI_LOOKUPNAME);
	row[1] = ar.getString(ar.I_OBJ_CLASS_NAME);
	omp.addTitle(row);

	for (int i = 0; i < v.size(); i++) {
	    NameClassPair obj = (NameClassPair)v.get(i);
            if ((type.equals(OBJMGR_TYPE_TOPIC) &&
                 com.sun.messaging.Topic.class.getName().
			equals(obj.getClassName())) ||
                (type.equals(OBJMGR_TYPE_QUEUE) &&
                 com.sun.messaging.Queue.class.getName().
		  equals(obj.getClassName())) 
		||
                (type.equals(OBJMGR_TYPE_TCF) &&
                 com.sun.messaging.TopicConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                (type.equals(OBJMGR_TYPE_QCF) && 
                 com.sun.messaging.QueueConnectionFactory.class.getName().
                      equals(obj.getClassName()))  ||
                (type.equals(OBJMGR_TYPE_CF) && 
                 com.sun.messaging.ConnectionFactory.class.getName().
                      equals(obj.getClassName()))  ||
                (type.equals(OBJMGR_TYPE_XTCF) &&
                 com.sun.messaging.XATopicConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                (type.equals(OBJMGR_TYPE_XQCF) && 
                 com.sun.messaging.XAQueueConnectionFactory.class.getName().
                      equals(obj.getClassName())) ||
                (type.equals(OBJMGR_TYPE_XCF) && 
                 com.sun.messaging.XAConnectionFactory.class.getName().
                      equals(obj.getClassName())))
		{

		row[0] = ((NameClassPair)obj).getName();
		row[1] = ((NameClassPair)obj).getClassName();
		omp.add(row);
            }
        }
	omp.print();
	
	return (0);
    }

    /**
     * List JMS administration objects.
     */
    private int listAll(ObjStore os) {

        Vector v = null;

        try {
            v = os.list();

	} catch (NameNotFoundException nnfe) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_CANNOT_LOC_TREE));
	    return (1);

        } catch (Exception e) {
	    handleRunCommandExceptions(e);
	    return (1);
        }

        ObjMgrPrinter omp = new ObjMgrPrinter(2, 4);
	String[] row = new String[2];

	row[0] = ar.getString(ar.I_JNDI_LOOKUPNAME);
	row[1] = ar.getString(ar.I_OBJ_CLASS_NAME);
	omp.addTitle(row);

	for (int i = 0; i < v.size(); i++) {
            NameClassPair obj = (NameClassPair)v.get(i);
            if ((com.sun.messaging.Topic.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.Queue.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.TopicConnectionFactory.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.QueueConnectionFactory.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.ConnectionFactory.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.XATopicConnectionFactory.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.XAQueueConnectionFactory.class.getName().
                 equals(obj.getClassName())) ||
                (com.sun.messaging.XAConnectionFactory.class.getName().
                 equals(obj.getClassName())))
	    {
		row[0] = ((NameClassPair)obj).getName();
		row[1] = ((NameClassPair)obj).getClassName();
		omp.add(row);
            }
        }
	omp.print();

	return (0);
    }

    /**
     * Return user input. Return null if an error occurred.
     */
    private String getUserInput(String question)  {
	return (getUserInput(question, null));
    }

    /**
     * Return user input. Return <defaultResponse> if no response ("") was
     * givem. Return null if an error occurred.
     */
    private String getUserInput(String question, String defaultResponse)  {

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	    Globals.stdOutPrint(question);
	    String s = in.readLine();

            if (s.equals("") && (defaultResponse != null))  {
                s = defaultResponse;
            }

            return(s);
        } catch (IOException ex) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG),
		ar.getKString(ar.E_PROB_GETTING_USR_INPUT));
            return null;
        }
    }    

    /**
     * Return user input without echoing, if possible.  
     */
    private String getPassword(String question)  {

	Password pw = new Password();
    if (pw.echoPassword()) {
        Globals.stdOutPrintln(ar.getString(ar.W_ECHO_PASSWORD));
    }
	Globals.stdOutPrint(question);
	return pw.getPassword();
    }    

    /*
     * Handles exceptions that may be thrown when operations are performed.
     */
    private void handleRunCommandExceptions(Exception e) {
	handleRunCommandExceptions(e, null);
    }

    /*
     * Handles exceptions that may be thrown when operations are performed.
     */
    private void handleRunCommandExceptions(Exception e, String lookupName) {

	if (e instanceof InitializationException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_OBJ_CREATOR));
/*
	This case should be taken care of by each command, so there is 
	no need to cath it here
        } else if (e instanceof NameNotFoundException) {

	This should only happen when adding, but we do the check in
	runAddCommand() so this should never happen
        } else if (e instanceof NameAlreadyExistsException) {
*/
        } else if (e instanceof AuthenticationException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_UN_OR_PASSWD));

        } else if (e instanceof AuthenticationNotSupportedException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NONSUPPORTED_AUTH_TYPE));

        } else if (e instanceof NoPermissionException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_PERMISSION));

        } else if (e instanceof CommunicationException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_COMMUNICATION));

        } else if (e instanceof InvalidPropertyException) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_INVALID_PROPNAME, e.getMessage()));
            Globals.stdErrPrintln("");
	    Globals.stdErrPrintln(ar.getString(ar.I_VALID_PROPNAMES));
	
        } else if (e instanceof InvalidPropertyValueException) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_INVALID_PROP_VALUE, e.getMessage()));

        } else if (e instanceof ReadOnlyPropertyException) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_CANT_MOD_READONLY));
	
        } else if (e instanceof SchemaViolationException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_SYNTAX));
/*
	Can't think of any situations.  Might be needed when
	we support attribute-based search
        } else if (e instanceof InvalidAttributesException) {
*/
	// REVISIT:
	// these exceptions are thrown by com.sun.messaging.naming package
        // once it becomes more solid we may create a subexception for each
        } else if (e instanceof GeneralNamingException) {
	    Exception newe = (((GeneralNamingException)e)).getLinkedException();

	    if (newe instanceof UnsupportedVersionNumberException) {
                Globals.stdErrPrintln(
		    ar.getString(ar.I_ERROR_MESG), 
		    ar.getKString(ar.E_UNSUPP_VER_NUMBER, 
			 	((UnsupportedVersionNumberException)newe).getExplanation(), lookupName));

            } else if (newe instanceof MissingVersionNumberException) {
                Globals.stdErrPrintln(
		    ar.getString(ar.I_ERROR_MESG), 
		    ar.getKString(ar.E_MISSING_VER_NUMBER, lookupName));
	    } else  {
                Globals.stdErrPrintln(newe.toString());
	    }

        } else if (e instanceof ObjStoreException) {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_GEN_OP_FAILED));
	    Globals.stdErrPrintln(e.toString());
	    Globals.stdErrPrintln(((ObjStoreException)e).getLinkedException().toString());

        } else {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.X_GENERAL_EXCEPTION));
	    Globals.stdErrPrintln(e.toString());
	}
    }

    /*
     * Handles initiliization-related exceptions.
     * Currently, there are only two types of exceptions
     * that could possibly be thrown:
     * 1.  InitializationException
     * 2.  NameNotFoundException
     *
     * Sun's implementation of ldap sp does not check for
     * its validity until when the operation is performed, so
     * there really isn't any that we can catch at this point.
    
    private void handleInitializationExceptions(Exception e) {
	handleRunCommandExceptions(e);
    }
    */

    private void checkObjStoreAttrs(ObjStoreAttrs osa) {

	String[] mandatoryAttrs = {Context.INITIAL_CONTEXT_FACTORY,
                                   Context.PROVIDER_URL};
	for (int i = 0; i < mandatoryAttrs.length; i++) {
	    String propName = (String)(osa.get(mandatoryAttrs[i]));
            if (propName == null) {
	        Globals.stdErrPrintln(ar.getString(ar.W_JNDI_PROPERTY_WARNING, mandatoryAttrs[i]));
	    }
	}
	System.out.println("");
    }

    /*
     * Prompts for authentication and stores the missing security info.
     * Individual store knows what to ask, so all this has to do is
     * to go through the Vector of missing security attributes and
     * and ask for the missing values, given the name of the attribute.
     */
    private ObjStore promptForAuthentication(ObjStore os) {
        /*
         *Retrieve the original ObjStoreAttrs that the user input.
         *This DOES NOT read any jndi property files processed by jndi
         * since this is done PRIOR to creating the initialContext.
         */
        ObjStoreAttrs osa = os.getObjStoreAttrs();
        Vector missingAuthInfo = os.checkAuthentication(osa);
        int missingAuthInfoSize = missingAuthInfo.size();

        boolean carriageReturnNeeded = false;
        if (missingAuthInfo != null) {
            for (int i = 0; i < missingAuthInfoSize; i++) {
                carriageReturnNeeded = true;
                String name = (String)missingAuthInfo.elementAt(i);
		String value = null;
		// If prompting for "credentials", use the one that doesn't echo.
		if (name.equals(Context.SECURITY_CREDENTIALS)) {
                    value = getPassword(ar.getString(ar.Q_ENTER_VALUE, name));
		} else {
                    value = getUserInput(ar.getString(ar.Q_ENTER_VALUE, name));
		}
                os.addObjStoreAttr(name, value);
            }
            if (carriageReturnNeeded)
                Globals.stdOutPrintln("");
        }
        return os;
    }
}
