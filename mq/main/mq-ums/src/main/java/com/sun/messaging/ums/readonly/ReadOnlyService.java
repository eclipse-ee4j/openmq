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

package com.sun.messaging.ums.readonly;

import java.util.Properties;

/**
 *
 * @author chiaming
 */
public interface ReadOnlyService {
    
    /**
     * initialize with the servlet init params.
     * @param the servlet init params.
     */
    public void init(Properties initParams);
    
    /**
     * The request message contains message properties and message body.
     * 
     * The message body is the http request message body.
     * 
     * The request message properties contains key/value pair of the http request.  
     * Each key/value pair of the requestProperties is obtained from 
     * the request url query string.
     * 
     * The request message properties contains at least the following none 
     * empty properties. 
     * 
     * 1. "service" property. 
     * 2. "requestURL" property.
     * 
     * The requestURL contains the URL the client used to make the request. 
     * The URL contains a protocol, server name, port number, and server path, 
     * but it does not include query string parameters.
     * 
     * Query string is parsed into key/value pair in the request 
     * message properties.
     * 
     * @param request the request message.
     * @return  The service implementation must construct a proper formatted
     * java string object as the http message body and 
     * set it in the request response message.
     */
    
    public ReadOnlyResponseMessage request (ReadOnlyRequestMessage request);
}
