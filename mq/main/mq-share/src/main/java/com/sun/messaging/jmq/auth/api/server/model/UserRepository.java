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
 * @(#)UserRepository.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.auth.api.server.model;

import java.io.*;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.server.*;

/**
 * Interface for plug-in different user repository for authentication.
 * A class implements this interface for a particular user repository
 * canbe used in AuthenticationProtocolHandler.handleResponse() method
 * to authenticate user agaist the particular user repository.
 */

public interface UserRepository {

    /**
     * @return the type of this user repsitory
     */
    public String getType();


    /**
     * This method is called from AuthenticationProtocolHandler to
     * open the user repository before findMatch call
     *
     * @param authType the authentication type
     * @param authProperties auth properties in broker configuration
     * @param cacheData from last getCacheData() call
     *
     * @exception LoginException
     */
    public void open(String authType, 
                     Properties authProperties,
                     Refreshable cacheData) throws LoginException; 

    /**
     * Find the user in the repository and compare the credential with
     * the user's  credential in database 
     *
     * @param user the user name 
     * @param credential its type is a contract between the caller and implementor <BR>
     *                   for "basic" it is the plain password String <BR>
     *                   for "digest" it is MD5 digest user:password (bye[] type) <BR>
     * @param extra additional information 
     * @param matchType must be one of the supported match-types specified by
     *                  the UserRepository implementation class or null if not
     *                  required. The matchType is to tell what type of the credential
     *                  is passed.
     *
     * @return The authenticated subject or null if no match found <BR>
     * <P>
     * @exception LoginException
     */
    public Subject findMatch(String user,
                             Object credential,
                             Object extra, String matchType)
                             throws LoginException;
    
    /**
     * This method is called after findMatch() is successful
     * 
     * The cacheData will be passed to open() call next time on
     * a connection authentication
     *
     * @return A refreshed Refreshable object that need to be cached or
     *         null if no cache data or the cache data is not refreshed
     *         in the last open/findMatch call
     */
    public Refreshable getCacheData();

    /**
     * This method is called after findMatch returns
     *
     * @exception LoginException
     */
    public void close() throws LoginException;

}
