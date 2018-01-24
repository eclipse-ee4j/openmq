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
 * @(#)JNDIStore.java	1.28 06/27/07
 */ 

package com.sun.messaging.jmq.admin.objstore.jndi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import com.sun.messaging.jmq.admin.objstore.*;
import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.admin.resources.AdminResources;


public class JNDIStore implements ObjStore {

    private ObjStoreAttrs attrs = null;
    private boolean open = false;
    private DirContext dirCtx;

    private AdminResources ar = Globals.getAdminResources();

    private static final String SUN_LDAP_IC = "com.sun.jndi.ldap.LdapCtxFactory";


    public JNDIStore(ObjStoreAttrs attrs) {
	this.attrs = attrs;
    }

    public void open() throws ObjStoreException {
	try {
    	    if (attrs != null)
	        dirCtx = new InitialDirContext(attrs); 
	    else
	        dirCtx = new InitialDirContext();
	} catch (Exception e) {
	    handleException(e);
	}

	open = true;
    }

    public void close() throws ObjStoreException {
	try {
	    dirCtx.close();
	} catch (Exception e) {
	    handleException(e);
	}

	open = false;
    }

    public void add(String lookupName, Object obj, boolean overwrite) 
	throws ObjStoreException {

	Attributes bindAttrs = null;

        try {
	    Hashtable storedEnv = dirCtx.getEnvironment();
	    String initialContextValue = null;

	    if (storedEnv.containsKey(Context.INITIAL_CONTEXT_FACTORY)) {
        	initialContextValue = 
		    (String)storedEnv.get(Context.INITIAL_CONTEXT_FACTORY);

            	// add "cn" attribute to attrs if initial.context is 
		// com.sun.jndi.ldap.LdapCtxFactory
		if ((SUN_LDAP_IC.equals(initialContextValue)) && 
		    (lookupName.startsWith("cn="))) {
		    bindAttrs = new BasicAttributes();
	            bindAttrs.put("cn", lookupName.substring(3));
		}
	    }
        } catch (Exception e) {
	    handleException(e);
        }

	try {
	    if (bindAttrs != null)
	        dirCtx.bind(lookupName, obj, bindAttrs);
	    else
	        dirCtx.bind(lookupName, obj);

	} catch (javax.naming.NameAlreadyBoundException nabe) {
	    if (overwrite) {
	        try {
		    if (bindAttrs != null)
	                dirCtx.rebind(lookupName, obj, bindAttrs);
		    else
	                dirCtx.rebind(lookupName, obj);
		} catch (Exception e) {
		    handleException(e);
		}
	    } else {
	        NameAlreadyExistsException naee = new NameAlreadyExistsException(
			ar.getString(ar.X_JNDI_NAME_ALREADY_BOUND));
		naee.setLinkedException(nabe);
		throw naee;
	    }
	} catch (Exception e) {
	    handleException(e);
	}
    }

    public void add(String lookupName, Object obj, 
		    Attributes bindAttrs, boolean overwrite)
        throws ObjStoreException {

        try {
	    Hashtable storedEnv = dirCtx.getEnvironment();
    	    String initialContextValue = null;

    	    if (storedEnv.containsKey(Context.INITIAL_CONTEXT_FACTORY)) {
        	initialContextValue = 
		    (String)storedEnv.get(Context.INITIAL_CONTEXT_FACTORY);

            	// add "cn" attribute to attrs if initial.context is 
		// com.sun.jndi.ldap.LdapCtxFactory
		if ((SUN_LDAP_IC.equals(initialContextValue)) && 
	    	    (lookupName.startsWith("cn="))) {
		    bindAttrs.put("cn", lookupName.substring(3));
		}
	    }
    	} catch (Exception e) {
	    handleException(e);
        }

        try {
            dirCtx.bind(lookupName, obj, bindAttrs);

        } catch (javax.naming.NameAlreadyBoundException nabe) {
            if (overwrite) {
                try {
                    dirCtx.rebind(lookupName, obj, bindAttrs);
                } catch (Exception e) {
                    handleException(e);
                }
            } else {
                NameAlreadyExistsException naee = new NameAlreadyExistsException(
			ar.getString(ar.X_JNDI_NAME_ALREADY_BOUND));
                naee.setLinkedException(nabe);
                throw naee;
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void delete(String lookupName) throws ObjStoreException {
        try {
	    dirCtx.unbind(lookupName);
	} catch (Exception e) {
	    handleException(e);
	}
    }

    public Object retrieve(String lookupName) throws ObjStoreException {

	Object obj = null;
	try {
	    obj = dirCtx.lookup(lookupName);

	} catch (Exception e) {
	    handleException(e);
	}

	return obj;
    }

    public Vector list() throws ObjStoreException {
	NamingEnumeration nameEnum;
	Vector vec = new Vector();
	try {
	    nameEnum = dirCtx.list("");

	    while (nameEnum.hasMore()) {
	        NameClassPair obj = (NameClassPair)nameEnum.next();
		vec.add(obj);
	    }
	} catch (Exception e) {
	    handleException(e);
	}

	return vec;
    }

    /* Note:
     * This would require us to search based on the javaclassname attribute.
     * However, we do not really want to hardcode values like 
     * com.sun.messaging.QueueConnectionFactory as the value to search for
     * a couple of reasons:
     *
     * 1. We cannot support other vendors' admin objects.  Suppose that the
     *    user creates admin objects from JMQ and some other vendor and stores
     *    both in the same location in the ldap.  Hardcoding the value like
     *    this will only allow him to retrieve JMQ-specific admin objects.
     *
     * 2.  We cannot allow users to extend our admin objects.  Since
     *     we do not currently support cosNaming, we may allow users to
     *     create their own admin object class, extending our admin object
     *     interface.  We will not be able to retrieve such objects.
     *   
     * Current implementation is doing the filtering at a local level,
     * hardcoding values as the one above.  We would like to come up with
     * a different schema so that there will be no hardcoding done.  Until
     * things are hashed out, we will postpone this implementation.
     */
    public Vector list(int[] type) throws ObjStoreException {
	return null;
    }

    public boolean isOpen() {
	return open;
    }

    public String getID() {
	return attrs.getID();
    }

    public String getDescription() {
	return attrs.getDescription();
    }

    public void setObjStoreAttrs(ObjStoreAttrs newAttrs) 
	throws ObjStoreException {
	this.attrs = newAttrs;
    }

    public ObjStoreAttrs getObjStoreAttrs() {
	return this.attrs;
    }

    private void handleException(Exception e) throws ObjStoreException {

	if (e instanceof javax.naming.AuthenticationException) {
	    AuthenticationException ae = new AuthenticationException(
			ar.getString(ar.X_JNDI_AUTH_ERROR));
	    ae.setLinkedException(e);
	    throw ae;
	} else if (e instanceof javax.naming.AuthenticationNotSupportedException) {
	    AuthenticationNotSupportedException anse = new
		 AuthenticationNotSupportedException(
			ar.getString(ar.X_JNDI_AUTH_TYPE_NOT_SUPPORTED));
	    anse.setLinkedException(e);
	    throw anse;
	} else if (e instanceof javax.naming.NoPermissionException) {
	    NoPermissionException pe = new NoPermissionException(
			ar.getString(ar.X_JNDI_NO_PERMISSION));
	    pe.setLinkedException(e);
	    throw pe;
	} else if (e instanceof javax.naming.CommunicationException) {
	    CommunicationException ce = new CommunicationException(
			ar.getString(ar.X_JNDI_CANNOT_COMMUNICATE));
	    ce.setLinkedException(e);
	    throw ce;
	} else if (e instanceof javax.naming.NoInitialContextException) {
	    InitializationException ie = new InitializationException(
			ar.getString(ar.X_JNDI_CANNOT_CREATE_INIT_CTX));
	    ie.setLinkedException(e);
	    throw ie;
	} else if (e instanceof javax.naming.directory.SchemaViolationException) {
	    SchemaViolationException sve = new SchemaViolationException(
			ar.getString(ar.X_JNDI_SCHEMA_VIOLATION));
	    sve.setLinkedException(e);
	    throw sve;
	} else if (e instanceof javax.naming.NameNotFoundException) {
	    NameNotFoundException nnfe = new NameNotFoundException(
			ar.getString(ar.X_JNDI_NAME_NOT_EXIST));
	    nnfe.setLinkedException(e);
	    throw nnfe;
	} else if (e instanceof javax.naming.NameAlreadyBoundException) {
	    NameAlreadyExistsException naee = new NameAlreadyExistsException(
			ar.getString(ar.X_JNDI_NAME_ALREADY_EXISTS));
	    naee.setLinkedException(e);
	    throw naee;
        } else if (e instanceof javax.naming.NotContextException) {
            NotContextException nce = new NotContextException(
			ar.getString(ar.X_JNDI_NOT_CONTEXT));
            nce.setLinkedException(e);
            throw nce;
	} else if (e instanceof javax.naming.directory.InvalidAttributesException) {
	    InvalidAttributesException iae = new InvalidAttributesException(
			ar.getString(ar.X_JNDI_INVALID_ATTRS));
	    iae.setLinkedException(e);
	    throw iae;
	// REVISIT:
	// this is to take care of new NamingExceptions thrown by 
	// com.sun.messaging.naming package
	// once it becomes more solid we may create a subexception for each
	} else if (e instanceof javax.naming.NamingException) {
	    GeneralNamingException gne = new GeneralNamingException(
			ar.getString(ar.X_JNDI_GENERAL_NAMING_EXCEPTION));
	    gne.setLinkedException(e);
	    throw gne;
	} else {
	    ObjStoreException ose = new ObjStoreException();
	    ose.setLinkedException(e);
	    throw ose;
	}
    }

    /**
     * Checks for user's authentication info.
     *
     * It first checks for the authentication type.
     * Depending on the authentication type, it checks for appropriate
     * java.naming.security attributes and returns a Vector containing
     * the names of missing attributes.
     *
     * @param osa  original ObjStoreAttrs that the user has specified
     *
     * @return Vector  Vector of missing attributes
     */
    public Vector checkAuthentication(ObjStoreAttrs osa) {

	Vector missingAuthInfo = new Vector();
	String authType = null;

        if (osa.containsKey(Context.SECURITY_AUTHENTICATION)) {
	    authType = (String)osa.get(Context.SECURITY_AUTHENTICATION);

	    // For "simple" authentication type, we need the following security
	    // attributes being set
	    if ("simple".equals(authType)) {
	        if (!osa.containsKey(Context.SECURITY_PRINCIPAL))
		    missingAuthInfo.addElement(Context.SECURITY_PRINCIPAL);

		if (!osa.containsKey(Context.SECURITY_CREDENTIALS))
		    missingAuthInfo.addElement(Context.SECURITY_CREDENTIALS);
	    }
        }
	return missingAuthInfo;
    }

    public void addObjStoreAttr(String name, String value) {
	attrs.put(name, value);
    }

    // to be determined
    public void search() {}

    // local methods
    /**
     * A useful debugging method that prints out the content of the object.
     * 
     * @param obj  obj to dump
     */
    public void dump(Object obj) {
	// you should have called retrieve() to get the Object before calling
	// this method
    }

    // TEMPORARY: this will be taken out once checkAuthentication() is replaced
    /*
    private String getUserInput(String question)  {

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            Globals.stdOutPrint(question);
            return in.readLine();

        } catch (IOException ex) {
            Globals.stdErrPrintln(
                ar.getString(ar.I_ERROR_MESG),
                ar.getKString(ar.E_PROB_GETTING_USR_INPUT));
            return null;
        }
    }
    */
}
/*
 * EOF
 */
