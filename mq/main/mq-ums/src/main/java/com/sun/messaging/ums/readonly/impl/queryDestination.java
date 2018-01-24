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

package com.sun.messaging.ums.readonly.impl;

import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.ums.provider.openmq.ProviderDestinationService;
//import com.sun.messaging.ums.readonly.DefaultReadOnlyService;
import com.sun.messaging.ums.readonly.ReadOnlyMessageFactory;
import com.sun.messaging.ums.readonly.ReadOnlyRequestMessage;
import com.sun.messaging.ums.readonly.ReadOnlyResponseMessage;
import com.sun.messaging.ums.readonly.ReadOnlyService;
import com.sun.messaging.ums.service.DestinationService;
import com.sun.messaging.ums.service.UMSServiceImpl;
//import java.util.Map;
import java.util.Properties;

/**
 *
 * @author chiaming
 */
public class queryDestination implements ReadOnlyService {
    
    private Properties initParams = null;
    
    /**
     * initialize with the servlet init params.
     * @param props
     */
    public void init(Properties initParams) {
        this.initParams = initParams;
    }
    
    /**
     * The requestProperties contains key/value pair of the request.  
     * Each key/value pair of the requestProperties is obtained from 
     * the request url query string.
     * 
     * The requestProperties parameter contains at least the following none 
     * empty properties. 
     * 
     * "service" and its corresponding value. 
     * "requestURL" 
     * 
     * The requestURL contains the URL the client used to make the request. 
     * The URL contains a protocol, server name, port number, and server path, 
     * but it does not include query string parameters.
     * 
     * Query string is parsed into key/value pair in the requestProperties
     * parameter.
     * 
     * @param props
     * @return  The service implementation must construct a proper formatted
     * java string object and return as the request response.
     */
    public ReadOnlyResponseMessage request (ReadOnlyRequestMessage request) {
         ProviderDestinationService pds = DestinationService.getProviderDestinationService(null);
            
        String destname = request.getMessageProperty (Constants.DESTINATION_NAME);
            
        String domain = request.getMessageProperty (Constants.DOMAIN);
            
        String user = request.getMessageProperty (Constants.USER);
        
        String pass = request.getMessageProperty (Constants.PASSWORD);
        
        String respMsg = pds.queryDestination (destname, domain, user, pass);
        
        //System.out.println ("***** query1 request., domain=" + domain + ", dest="+destname+", user=" + user);
        ReadOnlyResponseMessage rorm = ReadOnlyMessageFactory.createResponseMessage();
        
        rorm.setResponseMessage (respMsg);
        
        UMSServiceImpl.logger.info ("queryDestination invoked ..., domain=" + domain + ", dest="+destname+", user=" + user);
        
        return rorm;
     }

}
