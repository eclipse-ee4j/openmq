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
 * @(#)ConfigStore.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.config;

import java.util.*;
import java.net.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.util.*;


/**
 * this interface handles how personal and cluster
 * properties are stored and retrieved.
 *
 * A ConfigStore class should have only have a default
 * constructor.
 */

public interface ConfigStore
{

    /**
     * loads the instance properties
     *
     * @param currentprops already loaded properties (including
     *             system, default and install properties)
     * @param instancename the name used by the broker, passed in at startup
     *
     * @return a properties object with the correct instance properties
     *
     * @throws BrokerException if a fatal error occurs loading the 
     *         config store
     */

    public Properties loadStoredProps(Properties currentprops,
                   String instancename) 
           throws BrokerException;

    /**
     * loads the cluster properties
     *
     * @param currentprops already loaded properties (including
     *             system, default and install properties)
     * @param parameters properties passed in on the command line
     * @param instanceprops properties returned from the 
     *             loadStoredProps method
     *
     * @return a properties object with the correct cluster properties
     *            (or null if there aren't any)
     *
     * @throws BrokerException if a fatal error occurs loading the 
     *         config store
     */

    public Properties loadClusterProps(Properties currentprops,
                     Properties parameters,
                     Properties instanceprops) 
           throws BrokerException;

    /**
     * stores the modified properties
     *
     * @param props the list of properties to store
     *
     * @throws IOException if the property can not be stored
     */

    public void storeProperties(Properties props)
              throws IOException;

    /**
     * Reload the specified properties from the store.
     *
     * @param instancename the name used by the broker, passed in at startup
     *
     * @param propnames Array containing names of the properties
     * to be reloaded.
     *
     * @throws BrokerException if a fatal error occurs loading the 
     *         config store
     */
    public Properties reloadProps(String instancename, String[] propnames)
        throws BrokerException;

    /**
     * Clear out any local property file
     *
     * @param instancename the name used by the broker, passed in at startup
     *
     */
    public void clearProps(String instancename);
}
    
