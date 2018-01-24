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
 * @(#)PropertyOwner.java	1.5 06/28/07
 */ 

package com.sun.messaging;

/**
 * The <code>PropertyOwner</code> interface is implemented by any property that owns
 * other properties. The property type of this property is <code>com.sun.messaging.PropertyOwner</code>.
 * <p>
 * Setting a property Type to <code>com.sun.messaging.PropertyOwner</code> indicates
 * that dependant property information can be obtained from the class via the
 * interface methods described below.
 */

public interface PropertyOwner {
    /**
     * Returns a String array of property names that this <code>PropertyOwner</code>
     * owns.
     *
     * @return The String array of property names that this <code>PropertyOwner</code> owns.
     */
    public String[] getPropertyNames();

    /**
     * Returns the type of a single owned property.
     * 
     * @param propname The name of the owned property.
     *
     * @return The type of the owned property <code>propname</code>.
     *         <code>null</code> if the property <code>propname</code> is invalid.
     */
    public String getPropertyType(String propname);

    /**
     * Returns the label of a single owned property.
     * 
     * @param propname The name of the owned property.
     *
     * @return The label of the owned property <code>propname</code>.
     *         <code>null</code> if the property <code>propname</code> is invalid.
     */
    public String getPropertyLabel(String propname);

    /**  
     * Returns the default value of a single owned property.
     *   
     * @param propname The name of the owned property.
     *   
     * @return The default value of the owned property <code>propname</code>.
     *         <code>null</code> if the property <code>propname</code> is invalid.
     */  
    public String getPropertyDefault(String propname);
}
