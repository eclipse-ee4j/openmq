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
 * @(#)ObjStoreException.java	1.3 06/27/07
 */ 

package com.sun.messaging.jmq.admin.objstore;

/**
 * <P>This is the root class of all ObjStore exceptions.
 *
 * <P>It provides following information:
 * <UL>
 *   <LI> A string describing the error - This string is 
 *        the standard Java exception message, and is available via 
 *        getMessage().
 *   <LI> A reference to another exception - Often a ObjStore exception will 
 *        be the result of a lower level problem. If appropriate, this 
 *        lower level exception can be linked to the ObjStore exception.
 * </UL>
 **/

public class ObjStoreException extends Exception {

    /**
     * Exception reference
     **/
    private Exception linkedException;

    /**
     * Constructs an ObjStoreException
     */ 
    public ObjStoreException() {
        super();
        linkedException = null;
    }

    /** 
     * Constructs an ObjStoreException with reason
     *
     * @param  reason        a description of the exception
     **/
    public ObjStoreException(String reason) {
        super(reason);
        linkedException = null;
    }

    /**
     * Gets the exception linked to this one
     *
     * @return the linked Exception, null if none
     **/
    public Exception getLinkedException() {
        return (linkedException);
    }

    /**
     * Adds a linked Exception
     *
     * @param ex       the linked Exception
     **/
    public synchronized void setLinkedException(Exception ex) {
        linkedException = ex;
    }
}
