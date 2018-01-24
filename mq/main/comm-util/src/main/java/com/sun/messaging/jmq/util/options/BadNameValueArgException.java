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
 * @(#)BadNameValueArgException.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util.options;

/**
 * This exception is for reporting cases where
 * the argument for an option is not in the format
 * <EM>name=value</EM>. 
 */

public class BadNameValueArgException extends OptionException {

    String	nvArg;

    /**
     * Sets the argument containing the erroneous
     * name/value pair.
     *
     * @param arg	The string argument causing the exception.
     */
    public void setArg(String arg)  {
	this.nvArg = arg;
    }

    /**
     * Returns the string argument causing the error.
     *
     * @return The string argument causing the error
     */
    public String getArg()  {
	return (nvArg);
    }

    public String toString()  {
	return(super.toString() + " (" + getArg() + ")");
    }
}
