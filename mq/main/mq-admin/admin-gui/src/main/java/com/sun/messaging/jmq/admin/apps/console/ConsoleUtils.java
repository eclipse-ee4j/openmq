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
 * @(#)ConsoleUtils.java	1.4 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

/** 
 * This class contains miscellaneous utilities used by the admin GUI
 * application.
 */
public class ConsoleUtils   {

    /*
     * Get the package name of the passed object.
     * @return The package name of the passed object.
     */
    public static String getPackageName(Object obj)  {
	String	thisClassName, thisPkgName = null;
	int	lastDotIndex;

	try  {
	    thisClassName = obj.getClass().getName();
	    lastDotIndex = thisClassName.lastIndexOf('.');
	    thisPkgName = thisClassName.substring(0, lastDotIndex);
	} catch (Exception e)  {
	}

	return (thisPkgName);
    }

}
