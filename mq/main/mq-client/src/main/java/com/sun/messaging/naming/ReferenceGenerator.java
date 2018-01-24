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
 * @(#)ReferenceGenerator.java	1.7 07/02/07
 */ 

package com.sun.messaging.naming;

import com.sun.messaging.AdministeredObject;
import java.util.Properties;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import javax.naming.StringRefAddr;
import javax.naming.Reference;

/**
 * A ReferenceGenerator generates a Reference object given an Administered object and
 * the Object Factory Class Name.
 */
public class ReferenceGenerator {

    /** The index in the Reference object of the Version Number */
    public final static int REF_INDEX_VERSION = 0;

    /** The index in the Reference object of the read only state */
    public final static int REF_INDEX_RO_STATE = 1;

    /** The index in the Reference object of the configuration properties */
    public final static int REF_INDEX_PROPERTIES = 2;

    /**
     * Returns the reference to this object.
     *
     * @param ao The AdministeredObject for which the Reference object is to be generated.
     * @param objectfactoryclassname The classname of the ObjectFactory class for ao.
     *
     * @return  The Reference object that can be used to reconstruct this object
     */
    public static Reference getReference(AdministeredObject ao, String objectfactoryclassname) {
    
        //Create a Reference without any addresses
        Reference ref = new Reference(ao.getClass().getName(),
               objectfactoryclassname, null);

        //Set the version number
        ref.add(REF_INDEX_VERSION, new StringRefAddr
            (AdministeredObjectFactory.REF_VERSION, ao.getVERSION()));

        //Set the readOnly state
        ref.add(REF_INDEX_RO_STATE, new StringRefAddr
            (AdministeredObjectFactory.REF_READONLY, String.valueOf(ao.isReadOnly())));

        //Set the configuration
        String sb;
        Properties aoprops = ao.getConfiguration();
        Enumeration ep = aoprops.propertyNames();
        for (int i = REF_INDEX_PROPERTIES; ep.hasMoreElements(); i++) {
            try {
                sb = (String)ep.nextElement();
                ref.add(i, new StringRefAddr(sb, (String)aoprops.get(sb)));
            } catch (NoSuchElementException e) {
                break;
            }
        }
        return ref;
    }
}
