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
 * @(#)Filter.java	1.4 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;


/**
 * 
 * A algorithm which can be used to determine if an object
 * matches a specific critera. <P>
 * 
 * Used to determine the set of "items" which match a "view"
 *
 */


public interface Filter
{
   /**
    * determines if an object matches the filters criteria
    * @param o object to compare against filter
    * @returns true if the object matches, false otherwise
    */
    public boolean matches(Object o);

   /**
    * determines if an object is the same as the filter
    * @param o object to compare against filter
    * @returns true if the object matches, false otherwise
    * @see Object#equals
    */
    public boolean equals(Object o);

   /**
    * This hashcode of this object
    * @returns value of the hashcode
    * @see Object#hashCode
    */
    public int hashCode();
}
