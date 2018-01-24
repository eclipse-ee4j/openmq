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
 * @(#)Globals.java	1.15 06/27/07
 */ 

package com.sun.messaging.jmq.admin.util;

import java.io.File;
import java.util.Locale;
import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.resources.AdminConsoleResources;

/**
 * Singleton class which contains any Globals for the
 * system.<P>
 *
 * Other singleton classes which can be considered static
 * once they are retrieved (they do not need to be retrieved
 * from the static method each time they are used) should
 * also be defined here <P>
 */

public class Globals extends CommonGlobals
{
    private static final Object lock = Globals.class;

    private static AdminResources ar = null;
    private static AdminConsoleResources acr = null;

    private static Globals globals = null;


    //------------------------------------------------------------------------
    //--                 static brokerConfig objects                 --
    //------------------------------------------------------------------------
  
    private Globals() { }

    public static Globals getGlobals() {
        if (globals == null) {
            synchronized(lock) {
                if (globals == null)
                    globals = new Globals();
            }
        }
        return globals;
    }


    public static AdminResources getAdminResources() {
	if (ar == null) {
            synchronized(lock) {
	        if (ar == null) {
	            ar = AdminResources.getResources(Locale.getDefault());
		}
	    }
	}
	return ar;
    }

    public static AdminConsoleResources getAdminConsoleResources() {
	if (acr == null) {
            synchronized(lock) {
	        if (acr == null) {
	            acr = AdminConsoleResources.getResources(Locale.getDefault());
		}
	    }
	}
	return acr;
    }

    /*---------------------------------------------
     *          global static variables
     *---------------------------------------------*/

    public static final String IMQ = "imq";

    /**
     * system property name for the non-editable JMQ home location
     */
    public static final String JMQ_HOME_PROPERTY="imq.home";

    /**
     * system property name for the editable JMQ home location
     */
    public static final String JMQ_VAR_HOME_PROPERTY="imq.varhome";

    /**
     * system property name for the /usr/share/lib location
     */
    public static final String JMQ_LIB_HOME_PROPERTY="imq.libhome";

    /**
     * default value for the non-editable JMQ home location (used if
     * the system property is not set)
     */
    public static final String JMQ_HOME_default = ".";

    /**
     * default value for the non-editable JMQ home location (used if
     * the system property is not set)
     */
    public static final String JMQ_VAR_HOME_default = "var";

    /**
     * location the configuration is using for the non-editable home location
     */
    public static final String JMQ_HOME = System.getProperty(JMQ_HOME_PROPERTY,JMQ_HOME_default); 

    /**
     * location the configuration is using for the editable home location
     */
    public static final String JMQ_VAR_HOME = System.getProperty(JMQ_VAR_HOME_PROPERTY,JMQ_HOME + File.separator + JMQ_VAR_HOME_default);

    /**
     * location the configuration is using for the share lib home location
     */
    public static final String JMQ_LIB_HOME = System.getProperty(JMQ_LIB_HOME_PROPERTY,JMQ_HOME + File.separator + "lib") ;


    /**
     * subdirectory under either the editable or non-editable location where the 
     * configuration files are location
     */
    public static final String JMQ_ADMIN_PROP_LOC = "props"+File.separator + "admin"+File.separator;

}

