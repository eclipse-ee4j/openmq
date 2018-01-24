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

import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.ums.service.UMSServiceException;
import com.sun.messaging.ums.service.UMSServiceImpl;
import java.util.Properties;
import java.util.logging.Level;

/**
 *
 * @author chiaming
 */
public class DefaultReadOnlyService implements ReadOnlyService {
    
    public static final String REQUEST_URL = "requestURL";
    public static final String SERVICE = "service";
    
    public static final String JMSSERVICE = "JMSService";
    
    //this is the default place to add a new readonly service
    public static final String BASE = "com.sun.messaging.ums.readonly.impl.";
    
    private Properties initParams = null;
    
    /**
     * initialize with the servlet init params.
     * @param props
     */
    public void init(Properties initParams) {
        this.initParams = initParams;
    }
    
    /**
     * A request message contains message properties and message body.
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
     * Query string is parsed into key/value pair in the requestProperties
     * parameter.
     * 
     * A new readonly service can be created in the ./impl package, with the
     * service name as its class name:
     * 
     * com.sun.messaging.ums.readonly.impl.service
     * 
     * For example, 
     * 
     * http://localhost:8080/ums/simple?service=query1&destination=simpleQ&domain=queue
     * 
     * would result in the following object instantiation.
     * 
     * com.sun.messaging.ums.readonly.impl.query1
     * 
     * 
     * @param request
     * @return  The service implementation must construct a proper formatted
     * java string object and return as the request response.
     */
    
    public ReadOnlyResponseMessage request (ReadOnlyRequestMessage request) {
           
        ReadOnlyResponseMessage resp = null;
        
        try {
            //String svr = getSimpleRequestProperty (Constants.SERVICE_NAME, requestProperties);
            String svr = request.getMessageProperty(Constants.SERVICE_NAME);
            String cname = BASE + svr;
            
            String requestURL = request.getMessageProperty(this.REQUEST_URL);
            UMSServiceImpl.logger.info ("Invoking class, name=" + cname + ", requestURL=" + requestURL );
            
            ReadOnlyService ros = (ReadOnlyService) Class.forName(cname).newInstance();
            
            ros.init(initParams);
            
            resp = ros.request(request);
            
        } catch (Exception e) {
            UMSServiceImpl.logger.log(Level.WARNING, e.getMessage(), e);
            throw new UMSServiceException (e);
        }
        
        return resp;
        
    }

}
