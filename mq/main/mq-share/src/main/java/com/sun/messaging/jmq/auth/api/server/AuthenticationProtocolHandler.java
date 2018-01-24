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
 * @(#)AuthenticationProtocolHandler.java	1.15 06/28/07
 */ 

package com.sun.messaging.jmq.auth.api.server;

import java.util.Properties;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;

/**
 * This is broker-side AuthenticationProtocolHandler
 */
public interface AuthenticationProtocolHandler {

    /**
     * This method must return the authentication type it implements. 
     */
    public String getType();

    /**
     * This method is called once before any handleResponse() calls for 
     * this authentication process
     *   
     * @param sequence packet sequence number which can be used as a start
     *                 sequence number for this authentication process
     * @param authProperties contains broker auth properties for this authType
     * @param cacheData The cacheData if any (see getCacheData()). 
     *
     * @return initial authentication request data if any
     *         null if no initial authentication request data
     */
    public byte[] init(int sequence,
                       Properties authProperties,
                       Refreshable cacheData) throws LoginException;

    /**
     * This method is called to handle a authentication response
     *
     * @param authResponse the authentication response data.  This is the
     *                     AUTHENTICATE packet body.
     * @param sequence the packet sequence number
     *
     * @return next request data if any; null if no more request 
     *  Request data will be sent as packet body in AUTHENTICATE_REQUEST
     *                 
     * @exception LoginException if error occurs while handle the response
     * @exception com.sun.messaging.jmq.auth.FailedLoginException if invalid user or credential
     */
    public byte[] handleResponse(byte[] authResponse, int sequence)
                                             throws LoginException;

    /**
     * This method will be called when the connection closes or the service
     * type of the connection is denied to the subject.
     */
    public void logout() throws LoginException;

    /**
     * This method is called when handleReponse() successfully completes.
     *
     * @return a AccessControlContext object associated with the authentication subject
     * The object returned is used for access control after successful authentication 
     *
     */
     public AccessControlContext getAccessControlContext();

     /**
      * This method is called after handleReponse() successfully completes.
      * The object retrieved will be stored into the service instance and
      * on next connection authentication, this object will be passed to
      * init() method call.
      *
      * @return A Refreshable object that is to be cached, 
      *         null if not interest to cache anything
      */
     public Refreshable getCacheData();

}
