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
 * @(#)MissingVersionNumberException.java	1.4 07/02/07
 */ 

package com.sun.messaging.naming;

/** 
 * A <code>MissingVersionNumberException</code> is thrown when the Reference object used by the
 * <code>getInstance()</code> method of <code>AdministeredObjectFactory</code> either
 * does not name a class that extends from <code>com.sun.messaging.AdministeredObject</code>
 * or does not contain a Version number.
 *  
 * @see com.sun.messaging.naming.AdministeredObjectFactory com.sun.messaging.naming.AdministeredObjectFactory
 */

public class MissingVersionNumberException extends javax.naming.NamingException {

}

