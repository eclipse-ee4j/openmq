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
 * @(#)ObjMgr.java	1.19 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import javax.jms.*;
import javax.naming.*;

import com.sun.messaging.jmq.Version;
import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.admin.objstore.ObjStoreAttrs;
import com.sun.messaging.jmq.admin.objstore.ObjStoreManager;
import com.sun.messaging.jmq.admin.objstore.ObjStore;
import com.sun.messaging.jmq.admin.objstore.NameAlreadyExistsException;

import com.sun.messaging.jmq.util.options.OptionException;
import com.sun.messaging.jmq.util.options.UnrecognizedOptionException;
import com.sun.messaging.jmq.util.options.InvalidBasePropNameException;
import com.sun.messaging.jmq.util.options.InvalidHardCodedValueException;
import com.sun.messaging.jmq.util.options.MissingArgException;
import com.sun.messaging.jmq.util.options.BadNameValueArgException;

import com.sun.messaging.jmq.admin.util.JMSObjFactory;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;

/** 
 * This is a utility that allows administrators to store administration 
 * objects in an object store. The utility has the following features:
 *
 * <OL>
 * <LI>Add an administration object
 * <LI>Delete an administration object
 * <LI>Query and display an administration object
 * <LI>List all administration objects
 * </OL>
 * 
 * The user will execute this utility by running a imqobjmgr script.
 *
 */
public class ObjMgr implements ObjMgrOptions  {

    public final static String		FIRST_VERSION = "2.0";
    public final static String		VERSION = "2.0";

    private final static String		PROP_NAME_VERSION = "version";
    private static AdminResources ar = Globals.getAdminResources();

    /**
     * Constructor
     */
    public ObjMgr() {
    } 


    public static void main(String[] args)  {
	int exitcode = 0;

	if (silentModeOptionSpecified(args))  {
            Globals.setSilentMode(true);
	}

	/*
	 * Check for -h or -H, or that the first argument
	 * is the command type (-a, -d, -l or -q)
	 * if we still want to have that restriction.
	 */
	if (shortHelpOptionSpecified(args))  {
            HelpPrinter hp = new HelpPrinter();
	    hp.printShortHelp(0);
	} else if (longHelpOptionSpecified(args))  {
            HelpPrinter hp = new HelpPrinter();
	    hp.printLongHelp();
	}
	/*
	 * Check for -version.
	 */
	if (versionOptionSpecified(args)) {
	    printBanner();
            printVersion();
	    System.exit(0);
	}

	ObjMgrProperties objMgrProps = null;

	/*
	 * Convert String args[] into a ObjMgrProperties object.
	 * The ObjMgrProperties class is just a Properties
	 * subclass with some convenience methods in it.
	 */
	try  {
	    objMgrProps = ObjMgrOptionParser.parseArgs(args);
	} catch (OptionException e)  {
	    handleArgsParsingExceptions(e);
	}

	String propFileName = objMgrProps.getInputFileName();
	if ((propFileName != null) && !propFileName.equals(""))  {
	    /*
	     * Read in property file.
	     */
	    ObjMgrProperties tmpProps = new ObjMgrProperties();
	    try  {
	        FileInputStream	fis = new FileInputStream(propFileName);

	        tmpProps.load(fis);
		
	    } catch (IOException ioe)  {
		Globals.stdErrPrintln(
                    ar.getString(ar.I_ERROR_MESG),
		    ar.getKString(ar.E_PROB_LOADING_PROP_FILE), false);
                System.err.println(ioe);
                System.exit(1);
	    }
	
	    String v = getFileVersion(tmpProps);

	    /*
	     * If no version property found, assume the file is the current version
	     * and skip otherwise continue to check it.
	     */
	    if (v != null)  {

	        try {
                    checkVersion(propFileName, v, VERSION, FIRST_VERSION);
	        } catch (ObjMgrException ome) {
		    Globals.stdErrPrintln(
                        ar.getString(ar.I_ERROR_MESG), 
		        ome.getMessage(), false);
                    //System.err.println(ome);
                    System.exit(1);
	        }
	    }


	    /*
	     * Override the values with properties that exist on 
	     * the command line.
	     */
	    for (Enumeration e = objMgrProps.propertyNames() ; e.hasMoreElements() ;) {
                String propName = (String)e.nextElement(), propVal;
		/*
		Globals.stdErrPrintln("Override propname: " + propName);
		*/

		if (propName == null)  {
		    continue;
		}

		propVal = objMgrProps.getProperty(propName);
		if (propVal == null)  {
		    continue;
		}

		if (!propName.equals(OBJMGR_INPUTFILE_PROP_NAME))  {
		    tmpProps.put(propName, propVal);
	        }
	    }

	    objMgrProps = tmpProps;

	}

	/*
	 * Remove trailing spaces in prop values.
	 */
	objMgrProps = trimPropValues(objMgrProps);

	/*
	 * For each command type used, check that the
	 * information passed in is sufficient.
	 * e.g. for add (-a), a lookupname and type is
	 * required.
	 */
	try  {
	    checkOptions(objMgrProps);
	} catch (ObjMgrException ome)  {
	    handleCheckOptionsExceptions(ome);
	}

        if (objMgrProps.previewModeSet())  {
            CmdPreviewer cmdPreviewer = new CmdPreviewer(objMgrProps);
            cmdPreviewer.previewCommands();
            System.exit(0);
        }

	/*
	 * Execute the commands specified by the user
	 */

	CmdRunner cmdRunner = new CmdRunner(objMgrProps);
	exitcode = cmdRunner.runCommands();

	System.exit(exitcode);
    }

    /**
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute user commands. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkOptions(ObjMgrProperties objMgrProps) 
			throws ObjMgrException  {

	/*
        Globals.stdErrPrintln("ObjMgrProperties dump:");
	objMgrProps.list(System.err);
	Globals.stdErrPrintln("-------------\n");

	String objType = objMgrProps.getObjType();
	Properties props = objMgrProps.getObjProperties();
	ObjStoreAttrs	osa = objMgrProps.getObjStoreAttrs();

	Globals.stdErrPrintln("Obj Type: " + objType);
	Globals.stdErrPrintln("");
	Globals.stdErrPrintln("objMgrProps.forceModeSet(): " + objMgrProps.forceModeSet());
	Globals.stdErrPrintln("objMgrProps.previewModeSet(): " + objMgrProps.previewModeSet());
	Globals.stdErrPrintln("objMgrProps.getInputFileName(): " + objMgrProps.getInputFileName());

	Globals.stdErrPrintln("ObjProps:");
	props.list(System.err);
	Globals.stdErrPrintln("");

	Globals.stdErrPrintln("ObjStoreAttrs:\n " + osa);
	Globals.stdErrPrintln("");
	*/

	String cmd = objMgrProps.getCommand();

	if (cmd == null)  {
	    ObjMgrException objMgrEx;
	    objMgrEx = new ObjMgrException(ObjMgrException.NO_CMD_SPEC);
	    objMgrEx.setProperties(objMgrProps);

	    throw(objMgrEx);
	}

	/**
 	 * Validate the value to the read only option, if specified
	 */
	if (readOnlyOptionSpecified(objMgrProps)) {
	    checkReadOnly(objMgrProps);
	}

	/*
	 * Determine type of command and invoke the relevant check method
	 * to verify the contents of the ObjMgrProperties object.
	 *
	 */
	if (cmd.equals(OBJMGR_ADD_PROP_VALUE))  {
	    checkAdd(objMgrProps);
	} else if (cmd.equals(OBJMGR_DELETE_PROP_VALUE))  {
	    checkDelete(objMgrProps);
	} else if (cmd.equals(OBJMGR_QUERY_PROP_VALUE))  {
	    checkQuery(objMgrProps);
	} else if (cmd.equals(OBJMGR_LIST_PROP_VALUE))  {
	    checkList(objMgrProps);
	} else if (cmd.equals(OBJMGR_UPDATE_PROP_VALUE))  {
	    checkUpdate(objMgrProps);
	} else  {
	    ObjMgrException objMgrEx;
	    objMgrEx = new ObjMgrException(ObjMgrException.BAD_CMD_SPEC);
	    objMgrEx.setProperties(objMgrProps);

	    throw(objMgrEx);
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the ADD command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkAdd(ObjMgrProperties objMgrProps) 
			throws ObjMgrException  {

	String objType = objMgrProps.getObjType();

	if (objType == null)  {
	    ObjMgrException objMgrEx;

	    objMgrEx = new ObjMgrException(ObjMgrException.NO_OBJ_TYPE_SPEC);
	    objMgrEx.setProperties(objMgrProps);

	    throw(objMgrEx);
	}

	if (Utils.isDestObjType(objType))
	    checkAddDestination(objMgrProps);
	else if (Utils.isFactoryObjType(objType))
	    checkAddFactory(objMgrProps);
	else {
	    ObjMgrException objMgrEx;
	
	    objMgrEx = new ObjMgrException(ObjMgrException.INVALID_OBJ_TYPE);
	    objMgrEx.setProperties(objMgrProps);

	    throw(objMgrEx);
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the ADD command on a destination
     * object (Topic or Queue).  This method may print out errors/warnings
     * and exit with an error code.
     */
    private static void checkAddDestination(ObjMgrProperties objMgrProps) 
			throws ObjMgrException  {
	ObjMgrException	e;
	String lookupName = objMgrProps.getLookupName();
	//String objDestName = objMgrProps.getObjDestName();

	if (lookupName == null) {
	    e = new ObjMgrException(ObjMgrException.NO_LOOKUP_NAME_SPEC);
	    e.setProperties(objMgrProps);
	    throw (e);
/* XXX Not mandatory any more.
	} else if (objDestName == null) {
	    e = new ObjMgrException(ObjMgrException.NO_DEST_NAME_SPEC);
	    e.setProperties(objMgrProps);
	    throw (e);
*/
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the ADD command on a factory
     * object (TopicConnectionFactory or QueueConnectionFactory).  
     * This method may print out errors/warnings and exit with an error code.
     */
    private static void checkAddFactory(ObjMgrProperties objMgrProps) 
			throws ObjMgrException  {

	ObjMgrException e;
	String lookupName = objMgrProps.getLookupName();

	if (lookupName == null) {
            e = new ObjMgrException(ObjMgrException.NO_LOOKUP_NAME_SPEC);
            e.setProperties(objMgrProps);
            throw (e);
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the DELETE command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkDelete(ObjMgrProperties objMgrProps)
			throws ObjMgrException  {

	ObjMgrException e;
	String lookupName = objMgrProps.getLookupName();

        if (lookupName == null) {
            e = new ObjMgrException(ObjMgrException.NO_LOOKUP_NAME_SPEC);
            e.setProperties(objMgrProps);
            throw (e);
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the QUERY command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkQuery(ObjMgrProperties objMgrProps)
			throws ObjMgrException  {

	ObjMgrException e;
        String lookupName = objMgrProps.getLookupName();

        if (lookupName == null) {
            e = new ObjMgrException(ObjMgrException.NO_LOOKUP_NAME_SPEC);
            e.setProperties(objMgrProps);
            throw (e);
        }
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the LIST command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkList(ObjMgrProperties objMgrProps)
			throws ObjMgrException  {

	ObjMgrException e;
	String type = objMgrProps.getObjType();
	
	if (type != null)  {
	    if (!Utils.isValidObjType(type)) {
	        e = new ObjMgrException(ObjMgrException.INVALID_OBJ_TYPE);
		e.setProperties(objMgrProps);	    	
		throw e;
	   }
	}
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * all the correct info to execute the UPDATE command. This
     * method may print out errors/warnings and exit with an error
     * code.
     */
    private static void checkUpdate(ObjMgrProperties objMgrProps)
			throws ObjMgrException  {

        ObjMgrException e;
        String type = objMgrProps.getObjType();

        if (type != null)  {
            if (!Utils.isValidObjType(type)) {
                e = new ObjMgrException(ObjMgrException.INVALID_OBJ_TYPE);
                e.setProperties(objMgrProps);
                throw e;
           }
        }
    }

    /*
     * Check ObjMgrProperties object to make sure it contains
     * a true or false for the readonly command.
     */
    private static void checkReadOnly(ObjMgrProperties objMgrProps)
			throws ObjMgrException  {

	ObjMgrException e;
	String s = objMgrProps.getProperty(OBJMGR_READONLY_PROP_NAME);
	if (s.equalsIgnoreCase("t") || 
	    s.equalsIgnoreCase(Boolean.TRUE.toString())) {
	    s = Boolean.TRUE.toString();
	    objMgrProps.put(OBJMGR_READONLY_PROP_NAME,
			    Boolean.TRUE.toString());
	} else if (s.equalsIgnoreCase("f") ||
		   s.equalsIgnoreCase(Boolean.FALSE.toString())) {
	    s = Boolean.FALSE.toString();
	    objMgrProps.put(OBJMGR_READONLY_PROP_NAME,
			    Boolean.FALSE.toString());
	}

        if (!s.equalsIgnoreCase(Boolean.TRUE.toString()) &&
	    !s.equalsIgnoreCase(Boolean.FALSE.toString())) {
	    e = new ObjMgrException(ObjMgrException.INVALID_READONLY_VALUE);
	    e.setProperties(objMgrProps);	    	
            throw (e);
	}
    }


    /*
    private static void previewCommands(ObjMgrProperties objMgrProps) {
        Globals.stdOutPrintln("Preview mode", true);
    }
    */

    /**
     * Print banner.
     * XXX REVISIT 07/26/00 nakata: Add build number to M_BANNER
     */
    private static void printBanner() {

	Version version = new Version(false);
	Globals.stdOutPrintln(version.getBanner(false));
    }

    /*
     * REVISIT: Is it possible for any option value to be '-h' ?
     */
    private static boolean shortHelpOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OBJMGR_SHORT_HELP1) || 
		args[i].equals(OBJMGR_SHORT_HELP2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean longHelpOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OBJMGR_LONG_HELP1) ||
	   	args[i].equals(OBJMGR_LONG_HELP2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean readOnlyOptionSpecified(ObjMgrProperties objMgrProps) {
	String s = objMgrProps.getProperty(OBJMGR_READONLY_PROP_NAME);
	if (s != null) 
	    return (true);
	else
	    return (false);
    }

    private static boolean versionOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OBJMGR_VERSION1) ||
		args[i].equals(OBJMGR_VERSION2)) {
		return (true);
	    }
	}

	return (false);
    }

    private static boolean silentModeOptionSpecified(String args[]) {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals(OBJMGR_SILENTMODE)) {
		return (true);
	    }
	}

	return (false);
    }

    private static void printVersion() {

	Version version = new Version(false);
	Globals.stdOutPrintln(version.getVersion());
	Globals.stdOutPrintln(ar.getString(ar.I_JAVA_VERSION) +
	    System.getProperty("java.version") + " " +
	    System.getProperty("java.vendor") + " " +
	    System.getProperty("java.home")
	    );
	Globals.stdOutPrintln(ar.getString(ar.I_JAVA_CLASSPATH) +
	    System.getProperty("java.class.path")
	    );
    }

    /*
     * Error handling methods
     */

    private static void handleArgsParsingExceptions(OptionException e) {
	String	option = e.getOption();

	if (e instanceof UnrecognizedOptionException)  {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_UNRECOG_OPTION, option, "imqobjmgr"), true);

	} else if (e instanceof InvalidBasePropNameException)  {
            Globals.stdErrPrintln(
		ar.getString(ar.I_INTERNAL_ERROR_MESG),
		ar.getKString(ar.E_INVALID_BASE_PROPNAME, option), true);

	} else if (e instanceof InvalidHardCodedValueException)  {
            Globals.stdErrPrintln(
		ar.getString(ar.I_INTERNAL_ERROR_MESG),
		ar.getKString(ar.E_INVALID_HARDCODED_VAL, option), true);

	} else if (e instanceof MissingArgException)  {
	    /*
	     * REVISIT:
	     * We can provide more specific messages here depending on what
	     * the option was e.g. for -t:
	     *  Error: An object type was expected for option -t
	     */
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_MISSING_ARG, option, "imqobjmgr"), true);

	} else if (e instanceof BadNameValueArgException)  {
	    BadNameValueArgException bnvae = (BadNameValueArgException)e;
	    String	badArg = bnvae.getArg();

            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_BAD_NV_ARG, badArg, option), true);

	} else  {
            Globals.stdErrPrintln(
		ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_OPTION_PARSE_ERROR), true);
	}
    }

    private static void handleCheckOptionsExceptions(ObjMgrException e) {
	ObjMgrProperties	objMgrProps = e.getProperties();
	int			type = e.getType();

	/*
	 * REVISIT: should check objMgrProps != null
	 */

	switch (type)  {
	case ObjMgrException.NO_CMD_SPEC:
	    printBanner();
            HelpPrinter hp = new HelpPrinter();
	    hp.printShortHelp(1);
	break;

	case ObjMgrException.BAD_CMD_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_BAD_COMMAND_SPEC, objMgrProps.getCommand()), true);
	break;

	case ObjMgrException.NO_OBJ_TYPE_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_OBJ_TYPE_SPEC, ObjMgrOptions.OBJMGR_TYPE), true);
	break;

	case ObjMgrException.INVALID_OBJ_TYPE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_OBJ_TYPE, objMgrProps.getObjType()), true);
	break;

	case ObjMgrException.INVALID_READONLY_VALUE:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_INVALID_READONLY_VALUE, 
			objMgrProps.getProperty(OBJMGR_READONLY_PROP_NAME)), true);
	break;

	case ObjMgrException.NO_LOOKUP_NAME_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_LOOKUP_NAME, ObjMgrOptions.OBJMGR_NAME), true);
	break;

	case ObjMgrException.NO_DEST_NAME_SPEC:
	    Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_NO_DEST_NAME, ObjMgrOptions.OBJMGR_OBJ_ATTRS), true);
	break;

	default:
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG), 
		ar.getKString(ar.E_OPTION_VALID_ERROR), true);
	break;
	}
    }


    private static ObjMgrProperties trimPropValues(ObjMgrProperties props) {

	for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {

	    String propName = (String)e.nextElement();
	    String propValue = props.getProperty(propName);
	    if (propValue != null) {
		propValue = propValue.trim();
		props.put(propName, propValue);
  	    }

	}

	return props;
    }

    private static String getFileVersion(ObjMgrProperties props) {

	String val = props.getProperty(PROP_NAME_VERSION);

	return (val);
    }

    private static void checkVersion(String propFileName, String fileVersionStr, 
				String expectedVersionStr, String firstVersionStr) 
						throws ObjMgrException {
	double	expectedVersion, fileVersion, firstVersion;

	/*
	 * Convert current version string to double.
	 */
	try  {
	    expectedVersion = Double.parseDouble(expectedVersionStr);
	} catch (NumberFormatException nfe)  {
	    ObjMgrException ome;

	    expectedVersion = 0;
	    ome = new ObjMgrException(
		ar.getKString(ar.E_BAD_INPUTFILE_VERSION, expectedVersionStr));
	    throw(ome);
	}

	/*
	 * Convert first version string to double.
	 */
	try  {
	    firstVersion = Double.parseDouble(firstVersionStr);
	} catch (NumberFormatException nfe)  {
	    ObjMgrException ome;

	    firstVersion = 0;
	    ome = new ObjMgrException(
		ar.getKString(ar.E_BAD_INPUTFILE_VERSION, firstVersionStr));
	    throw(ome);
	}

	/*
	 * Convert file version string to double.
	 */
	try  {
	    fileVersion = Double.parseDouble(fileVersionStr);
	} catch (NumberFormatException nfe)  {
	    ObjMgrException ome;
	    Object args[] = new Object [ 4 ];

	    fileVersion = 0;

	    args[0] = propFileName;
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = ar.getKString(ar.E_UNPARSABLE_INPUTFILE_VERSION, args);

	    ome = new ObjMgrException(s);
	    throw(ome);
	}

	/*
	 * File version is less than our first version - error !
	 */
	if (fileVersion < firstVersion)  {
	    ObjMgrException ome;
	    Object args[] = new Object [ 4 ];

	    args[0] = propFileName;
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = ar.getKString(ar.E_NOT_SUP_INPUTFILE_VERSION, args);

	    ome = new ObjMgrException(s);
	    throw (ome);
	}

	/*
	 * File version is greater than our current version - error !
	 */
	if (fileVersion > expectedVersion)  {
	    ObjMgrException ome;
	    Object args[] = new Object [ 4 ];

	    args[0] = propFileName;
	    args[1] = PROP_NAME_VERSION;
	    args[2] = fileVersionStr;
	    args[3] = expectedVersionStr;

            String s = ar.getKString(ar.E_NOT_SUP_INPUTFILE_VERSION, args);

	    ome = new ObjMgrException(s);
	    throw (ome);
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
