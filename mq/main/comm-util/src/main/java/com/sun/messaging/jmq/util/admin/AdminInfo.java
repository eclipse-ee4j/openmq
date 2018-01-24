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
 * @(#)AdminInfo.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util.admin;

/**
 * Abstract base class for all the admin *Info classes. Basically
 * just provides the implementation for maintaining updateMask.
 */
public class AdminInfo implements java.io.Serializable {

    static final long serialVersionUID = 6731577042303829252L;

    /**
     * A bit mask that subclasses use to indicate if a particular field
     * in the object has been updated or not.
     */
    private int updateMask = 0;

    /*
     * Constructor
     */
    public AdminInfo() {
	reset();
    }

    /*
     * Reset all fields to null values
     */
    public void reset() {
        resetMask();
    }

    /**
     * Clear updateMask so object thinks no fields have been modified
     */
    public void resetMask() {
        updateMask = 0;
    }

    /**
     * Check if a field has been modified
     */
    public boolean isModified(int fieldBit) {
        return ((updateMask & fieldBit) == fieldBit);
    }

    /**
     * Indicate that a field has been modified
     */
    public void setModified(int fieldBit) {
        updateMask = updateMask | fieldBit;
    }

    /**
     * Indicate that a field has NOT been modified
     */
    public void clearModified(int fieldBit) {
        updateMask = updateMask & ~fieldBit;
    }
}
