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
 * @(#)OptionException.java	1.6 06/29/07
 */ 

package com.sun.messaging.jmq.util.options;

/**
 * This exception provides information about problems
 * encountered when processing command line options.
 *
 * <P>It provides following information:
 * <UL>
 *   <LI> A string describing the error - This string is 
 *        the standard Java exception message, and is available via 
 *        getMessage().
 *   <LI>The command line option that is relevant.
 * </UL>
 **/

public class OptionException extends Exception {

    /**
     * Stored command line option
     **/
    private String option;

    /**
     * Constructs an OptionException
     */ 
    public OptionException() {
        super();
        option = null;
    }

    /** 
     * Constructs an OptionException with reason
     *
     * @param  reason        a description of the exception
     **/
    public OptionException(String reason) {
        super(reason);
        option = null;
    }

    /**
     * Gets the command line option that is relevant to the exception.
     *
     * @return the command line option
     **/
    public String getOption() {
        return (option);
    }

    /**
     * Sets the command line option that is relevant to the exception.
     *
     * @param o       the command line option
     **/
    public synchronized void setOption(String o) {
        option = o;
    }
}
