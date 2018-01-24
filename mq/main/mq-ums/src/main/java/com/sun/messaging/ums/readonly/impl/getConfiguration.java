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
import com.sun.messaging.ums.dom.util.XMLDataBuilder;
import com.sun.messaging.ums.provider.openmq.ProviderDestinationService;
import com.sun.messaging.ums.readonly.ReadOnlyMessageFactory;
import com.sun.messaging.ums.readonly.ReadOnlyRequestMessage;
import com.sun.messaging.ums.readonly.ReadOnlyResponseMessage;
import com.sun.messaging.ums.readonly.ReadOnlyService;
import com.sun.messaging.ums.service.DestinationService;
import com.sun.messaging.ums.service.UMSServiceException;
import java.util.Properties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author chiaming
 */
public class getConfiguration implements ReadOnlyService {
    
    private Properties initParams = null;
    
    /**
     * initialize with the servlet init params.
     * @param props
     */
    public void init(Properties initParams) {
        this.initParams = initParams;
    }
    
    public ReadOnlyResponseMessage request(ReadOnlyRequestMessage request) {

        try {
        	
        	// authenticate by trying to create a JMX connection to the broker 
            ProviderDestinationService pds = DestinationService.getProviderDestinationService(null);
            String user = request.getMessageProperty(Constants.USER);
            String pass = request.getMessageProperty(Constants.PASSWORD);
            pds.authenticate(user, pass);

            String respMsg = null;

            //create a new instance of ums xml document.
            Document doc = XMLDataBuilder.newUMSDocument();

            //get the root element
            Element root = XMLDataBuilder.getRootElement(doc);

            //create the first child element
            Element baddr = XMLDataBuilder.createUMSElement(doc, "BrokerAddress");

            //set text value to the first child
            XMLDataBuilder.setElementValue(doc, baddr, this.initParams.getProperty(Constants.IMQ_BROKER_ADDRESS, "localhost:7676"));

            //add the first child to the root element
            XMLDataBuilder.addChildElement(root, baddr);
            
            //create the auth child element
            Element auth = XMLDataBuilder.createUMSElement(doc, "JMSAuthenticate");

            //set text value to the auth child
            XMLDataBuilder.setElementValue(doc, auth, this.initParams.getProperty(Constants.JMS_AUTHENTICATE));

            //add to the root element
            XMLDataBuilder.addChildElement(root, auth);
            
            //create the auth child element
            Element cacheTime = XMLDataBuilder.createUMSElement(doc, "CacheDuration");

            //set text value
            XMLDataBuilder.setElementValue(doc, cacheTime, this.initParams.getProperty(Constants.CACHE_DURATION,"420000"));

            //add  to the root element
            XMLDataBuilder.addChildElement(root, cacheTime);
            
            //create the sweep child element
            Element sweepTime = XMLDataBuilder.createUMSElement(doc, "SweepInterval");

            //set text value
            XMLDataBuilder.setElementValue(doc, sweepTime, this.initParams.getProperty(Constants.SWEEP_INTERVAL,"120000"));

            //add  to the root element
            XMLDataBuilder.addChildElement(root, sweepTime);
            
            //create the sweep child element
            Element receiveTimeout = XMLDataBuilder.createUMSElement(doc, "ReceiveTimeout");

            //set text value
            XMLDataBuilder.setElementValue(doc, receiveTimeout, this.initParams.getProperty(Constants.RECEIVE_TIMEOUT,"7000"));

            //add  to the root element
            XMLDataBuilder.addChildElement(root, receiveTimeout);
            
            //create the sweep child element
            Element maxClient = XMLDataBuilder.createUMSElement(doc, "MaxClientsPerConnection");

            //set text value
            XMLDataBuilder.setElementValue(doc, maxClient, this.initParams.getProperty(Constants.MAX_CLIENT_PER_CONNECTION,"100"));

            //add  to the root element
            XMLDataBuilder.addChildElement(root, maxClient);

            //transform xml document to a string
            respMsg = XMLDataBuilder.domToString(doc);

            ReadOnlyResponseMessage response = ReadOnlyMessageFactory.createResponseMessage();

            response.setResponseMessage(respMsg);

            return response;

        } catch (Exception e) {

            UMSServiceException umse = new UMSServiceException(e);

            throw umse;
        }
    }
   

}
