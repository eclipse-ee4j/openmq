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
 */ 
 
package com.sun.messaging.jmq.jmsserver.auth.jaas;

import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;

/**
 */
public interface SubjectHelper 
{

    /**
     * This method is called before each makeSubject() call
     *
     * @param loginModuleName the name of the JAAS LoginModule
     * @param props properties configured for this SubjectHelper
     * @param cacheData the cached data from previous call getCacheData(), null on first call
     *
     * @throws Exception if any failure
     */
    public void init(String loginModuleName, Properties props,
                     Refreshable cacheData)
                     throws Exception;

    /**
     *  Make a Subject object representing the user to be authenticated.
     *
     *  This Subject object will be passed to the JAAS LoginContext for
     *  authentication by the LoginModule 
     *
     * @param username the user name to be authenticatd
     * @param password the password of the user to be authenticated
     * @return a the subject to be authenticated
     *
     * @throws Exception if any failure
     */
    public Subject makeSubject(String username, String password) throws Exception;

    /**
     * This method is called after each makeSubject() call
     * 
     * @return data that need to be cached
     */
    public Refreshable getCacheData(); 
}
