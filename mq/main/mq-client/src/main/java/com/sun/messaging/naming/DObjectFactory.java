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
 * @(#)DObjectFactory.java	1.6 07/02/07
 */ 

package com.sun.messaging.naming;

import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.Destination;
import com.sun.messaging.Queue;
import com.sun.messaging.Topic;
import com.sun.messaging.DestinationConfiguration;

/**
 * <code>DObjectFactory</code> handles instance creation for
 * <code>com.sun.messaging.Queue</code>
 * and <code>com.sun.messaging.Topic</code> objects from JMQ1.1 created Reference objects.
 * <p>
 * It specifically handles the format conversion from
 * Reference objects created by JMQ1.1 <code>Destination</code> objects
 * to iMQ3.0 <code>Destination</code> objects.
 * <p>
 * @see javax.naming.Reference javax.naming.Reference
 * @see com.sun.messaging.Destination com.sun.messaging.Destination
 * @see com.sun.messaging.AdministeredObject com.sun.messaging.AdministeredObject
 * @since JMQ2.0
 */

/*
 * IMPORTANT:
 * ==========
 * The size of the reference for JMQ1.1 Destination objects is always 2.
 * The format of the reference in JMQ1.1 is always
 * as follows --
 *
 *  [0] = reserved for version
 *  [1] = reserved for destination name
 */

public abstract class DObjectFactory extends AdministeredObjectFactory {

    /** used only by Destination reference objects */
    private static final String REF_DESTNAME = "destName";
 
    /**
     * Creates an instance of the object represented by a Reference object.
     *   
     * @param obj The Reference object.
     *   
     * @return an instance of the class named in the Reference object <code>obj</code>.
     * @return null if <code>obj</code> is not an instance of a Reference object.
     *   
     * @throws MissingVersionNumberException if either <code>obj</code> references an object
     *         that is not an instance of a <code>com.sun.messaging.Queue</code> object
     *         or the version number is missing from the Reference object.
     * @throws UnsupportedVersionNumberException if an unsupported version number is present
     *         in the Reference.
     * @throws CorruptedConfigurationPropertiesException if <code>obj</code> does not have the
     *         minimum information neccessary to recreate an instance of a
     *         a valid <code>com.sun.messaging.AdministeredObject</code>.
     */
    public
    Object getObjectInstance
        (Object obj, Name name, Context ctx, Hashtable env) throws Exception {

        if (obj instanceof Reference) {
            Reference ref = (Reference)obj;
            String refClassName;
            refClassName = ref.getClassName();
            Destination destObj = null;
            if (refClassName.equals(com.sun.messaging.Queue.class.getName())) {
                destObj = new Queue();
            } else {
                if (refClassName.equals(com.sun.messaging.Topic.class.getName())) {
                    destObj = new Topic();
                } else {
                    throw new MissingVersionNumberException();
                }
            }
            //version number MUST exist and it MUST be the same as AO_VERSION_STR_JMQ1
            RefAddr versionAddr = ref.get(REF_VERSION);
            if (versionAddr == null) {
                //version number does not exist
                throw new MissingVersionNumberException();
            } else {
                String version = null;
                if (!AO_VERSION_STR_JMQ1.equals(version = (String)versionAddr.getContent())) {
                    //version number does not match
                    throw new UnsupportedVersionNumberException(version);
                }
                ((AdministeredObject)destObj).storedVersion = version;
            }
            RefAddr destAddr = ref.get(REF_DESTNAME);
            if (destAddr != null) {
                    destObj.setProperty(DestinationConfiguration.imqDestinationName,
                                            (String)destAddr.getContent());
                    return destObj;
            } else {
                    throw new CorruptedConfigurationPropertiesException();
            }
        }
        return null;
    }
}

