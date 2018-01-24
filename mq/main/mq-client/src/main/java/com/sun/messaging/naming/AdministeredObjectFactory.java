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
 * @(#)AdministeredObjectFactory.java	1.12 07/02/07
 */ 

package com.sun.messaging.naming;

import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import com.sun.messaging.AdministeredObject;

/**
 * The <code>AdministeredObjectFactory</code> class is the factory class for
 * iMQ Administered Objects that are stored using the Java Naming and Directory
 * Interface (JNDI) API for regeneration using the getInstance() method of this class.
 */ 

public class AdministeredObjectFactory implements javax.naming.spi.ObjectFactory {

    /** Key for version in Reference objects */
    protected static final String REF_VERSION = "version";

    /** Key for read only flag in Reference objects */
    protected static final String REF_READONLY = "readOnly";
 
    /** Current Administered Object version supported */
    protected static final String AO_VERSION_STR = "3.0";

    /** iMQ 3.0 Beta Administered Object version supported */
    protected static final String AO_VERSION_STR_JMQ3B = "2.1";

    /** JMQ 2 Administered Object version supported */
    protected static final String AO_VERSION_STR_JMQ2 = "2.0";

    /** JMQ 1 Administered Object version supported */
    protected static final String AO_VERSION_STR_JMQ1 = "1.1";

    /**
     * Creates an instance of the object represented by a Reference object.
     *
     * @param obj The Reference object.
     *
     * @return an instance of the class named in the Reference object <code>obj</code>.
     * @return null if <code>obj</code> is not an instance of a Reference object.
     *
     * @throws MissingVersionNumberException if either <code>obj</code> references an object
     *         that is not an instance of a <code>com.sun.messaging.AdministeredObject</code> object
     *         or the version number is missing from the Reference object.
     * @throws UnsupportedVersionNumberException if an unsupported version number is present
     *         in the Reference.
     * @throws CorruptedConfigurationPropertiesException if <code>obj</code> does not have the
     *         minimum information neccessary to recreate an instance of a
     *         a valid <code>com.sun.messaging.AdministeredObject</code>.
     */
    public
    Object getObjectInstance (Object obj, Name name, Context ctx, Hashtable env) throws Exception {

        if (obj instanceof Reference) {
            Reference ref = (Reference)obj;
            String version = null;
            boolean readOnly = false;
            
            //Construct the desired AdministeredObject
            Object newobj = Class.forName(ref.getClassName()).newInstance();

            //version number MUST exist and it MUST be this version or a supported version
            RefAddr versionAddr = ref.get(REF_VERSION);

            //Support reading previous object versions here (2.0, 2.1 etc.). Floor is 2.0
            if (versionAddr == null || !(newobj instanceof com.sun.messaging.AdministeredObject)) {
                //if version number does not exist or it is not an AdministeredObject
                throw new MissingVersionNumberException();
            } else {
                version = (String)versionAddr.getContent();
                //Support reading previous object versions here (2.0, 2.1 etc.). Floor is 2.0
                if ( ! (AO_VERSION_STR.equals(version) ||
                        AO_VERSION_STR_JMQ3B.equals(version) ||
                        AO_VERSION_STR_JMQ2.equals(version)) ){
                    //Reference contains a bad version number
                    throw new UnsupportedVersionNumberException(version);
                }
                if (ref.size() < 2) {
                    //Reference is corrupted
                    throw new CorruptedConfigurationPropertiesException();
                }
                RefAddr readOnlyAddr = ref.get(REF_READONLY);
                if ("true".equals((String)readOnlyAddr.getContent())) {
                    //Reference has readOnly set
                    readOnly = true;
                }
                ((AdministeredObject)newobj).storedVersion = version;
            }

            RefAddr refaddr;                                                                               
            String refContent;
            //Skip the version # and r/o flag (start at 2)
            //System.out.println("AOtoString="+ newobj.toString());
            for (int i = 2; i < ref.size(); i++) {
                refaddr = ref.get(i);
                refContent = (String)refaddr.getContent();
                //System.out.println("gOI:ref#="+i+"; refCntnt="+refContent);
                //Guard against null values coming back from JNDI
                //Some service-providers will return `null'; others will return "" (empty string)
                if (refContent == null) {
                    refContent = "";
                }
                //If property fails to set then ignore since we may have looked up a newer object
                try {
                    //XXX RFE:tharakan
                    //Need to add support migrating 2.x properties to 3.x
                    //System.out.println("gOI:settingProp");
                    //System.out.println("gOI:propName="+refaddr.getType());
                    ((AdministeredObject)newobj).setProperty(refaddr.getType(), refContent);
                    //System.out.println("gOI:propName="+refaddr.getType()+" set successfully");
                } catch (Exception bpe) {
                    //Ignore exception
                    //System.out.println("gOI:propName="+refaddr.getType()+" exception; "+bpe.getMessage());
                    //bpe.printStackTrace();
                }
            }
            //Set the readOnly flag
            if (readOnly) {
                ((AdministeredObject)newobj).setReadOnly();
            }
            return newobj;
        }
        return null;
    }
}

