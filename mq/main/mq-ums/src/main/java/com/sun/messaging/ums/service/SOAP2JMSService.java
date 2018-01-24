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

package com.sun.messaging.ums.service;

import java.util.*;
import javax.jms.*;

import javax.xml.soap.*;

import com.sun.messaging.ums.core.UMSService;
import com.sun.messaging.ums.core.MessageContext;
import com.sun.messaging.ums.core.ServiceContext;
import com.sun.messaging.ums.common.*;
import com.sun.messaging.ums.service.UMSServiceImpl;
//import com.sun.messaging.xml.imq.soap.service.jms.impl.SimpleLogger;
import java.util.logging.Logger;

/**
 * <p>A MQ SOAP Service is a class that implements SOAPService interface and
 * may be used to *register* its service to a MQSOAPServlet as a SOAP
 * service and becomes part of the MQ SOAP Service frame work.
 *
 * <p>A MQ SOAP service consists of the following components:
 *
 * <p>1. Request Handler Chain.  The Request handler can be registered as follows
 * in the web.xml:
 *
 * <p>mq.soap.request.handler.#="MessageHandler class full name"
 *
 * <p>For example,
 *
 * <p>mq.soap.request.handler.1=com.sun.TestMessageListener1
 * <p>mq.soap.request.handler.2=com.sun.TestMessageListener2
 *
 * <p>2. Response Handler Chain.  The Response handler can be registered as
 * follows in the web.xml:
 *
 * <p>mq.soap.response.handler.#=MessageHandler class full name.
 *
 * <p>For example,
 *
 * <p>mq.soap.response.handler.1=com.sun.TestMessageListener1
 * <p>mq.soap.response.handler.2=com.sun.TestMessageListener2
 *
 * <p>3. A service() method to be over ridden by subclass.
 *
 * <p>4. Service lifecycle management methods.  There are four methods defined
 * for life cycle management - init/start/stop/close.  They are used for
 * init/start/stop/close a MQ SOAP Service instance.  Sub class SHOULD
 * implement or over ride the life cycle methods if necessary.
 *
 *
 * @author  chiaming yang
 * 
 * @see     SOAPService
 * @see     MessageHandler
 * @see     MessageHandlerChain
 */
public class SOAP2JMSService extends UMSService {
	
	//setvice context attribute names
	public static final String JMS_CONNECTION = "JMS_CONNECTION";
	public static final String SOAP_FACTORY = "SOAP_FACTORY";
	
	//message context attribute names
	public static final String DESTINATION_NAME = "destination_name";
	public static final String DESTINATION_DOMAIN = "destination_domain";
	
	//private Connection connection = null;
	//private Session session = null;
	
	//private MessageFactory soapFactory = null;
	
	//send service
	//private SendService sendService = null;
	
	//receive service
	//private ReceiveService receiveService = null;
	private static Logger logger = UMSServiceImpl.logger;
    
    /**
     * MQService
     */
    private UMSServiceImpl MQService = null;
    
	/**
     * init this SOAPService with the specified Properties in the parameter.
     */
    public void init (ServiceContext context) throws SOAPException {
        
    	super.init(context);
        
        //should we pass SerViceContext instead?
        MQService = new UMSServiceImpl (this.props);
        
        MQService.init();
        
    }


    /**
     * To be over ridden by sub class.
     */
    public void service(MessageContext context) throws SOAPException {

        try {
            
            SOAPMessage request = context.getRequestMessage();
            
            String provider = MQService.getProvider(request);
           
            SOAPMessage response = null;

            String serviceName = MessageUtil.getServiceName (request);
            
            String destName = MessageUtil.getServiceAttribute(request, Constants.DESTINATION_NAME);
            
            String domain = MessageUtil.getServiceAttribute(request, Constants.DOMAIN);
            if (domain == null) {
                domain = Constants.QUEUE_DOMAIN;
            }
            
            if (Constants.SERVICE_VALUE_LOGIN.equals(serviceName)) {
                
                //login request
                response = MessageUtil.createResponseMessage(request);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_LOGIN_REPLY);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, Constants.SERVICE_STATUS_VALUE_OK);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                
                //authenticate
                String sid = MQService.authenticate(request);
                MessageUtil.setServiceAttribute(response, Constants.CLIENT_ID, sid);
                
                context.setResponseMessage(response);

            } else if (Constants.SERVICE_VALUE_CLOSE.equals(serviceName)) {
                
                MQService.closeClient(request);
                
                response = MessageUtil.createResponseMessage(request);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_CLOSE_REPLY);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, Constants.SERVICE_STATUS_VALUE_OK);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                
                //authenticate
                //String sid = MQService.authenticate(request);
                //MessageUtil.setServiceAttribute(response, Constants.CLIENT_ID, sid);
                
                context.setResponseMessage(response);
                
                
            } else if (Constants.SERVICE_VALUE_SEND_MESSAGE.equals(serviceName)) {
                
                MQService.send(request);
               
                response = MessageUtil.createResponseMessage(request);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_SEND_MESSAGE_REPLY);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, Constants.SERVICE_STATUS_VALUE_OK);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                
                MessageUtil.setServiceAttribute(response, Constants.DESTINATION_NAME, destName);
                
                MessageUtil.setServiceAttribute(response, Constants.DOMAIN, domain);
                
                context.setResponseMessage(response);
                
            } else if (Constants.SERVICE_VALUE_RECEIVE_MESSAGE.equals(serviceName)) {
                
                response = MQService.receive(request);
               
                //System.out.println ("@@@@@@@@@@@@@@@@@@@@@@@@@ received: \n" );
                //response.writeTo(System.out);
                
                String statusCode = null;
                if (response ==null) {
                   
                    response = MessageUtil.createResponseMessage(request);
                    statusCode = Constants.SERVICE_STATUS_VALUE_NO_MESSAGE;
                    
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_RECEIVE_MESSAGE_REPLY);
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, statusCode);
                
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                    
                    MessageUtil.setServiceAttribute(response, Constants.DESTINATION_NAME, destName);
                
                    MessageUtil.setServiceAttribute(response, Constants.DOMAIN, domain);
                    
                    //String destname = MessageUtil.getServiceAttribute(request, Constants.DESTINATION_NAME);
                    //MessageUtil.setServiceAttribute(response, Constants.DESTINATION_NAME, destname);
                    
                } else {
                    
                    response = MessageUtil.createResponseMessage2 (request, response);
                    
                    statusCode = Constants.SERVICE_STATUS_VALUE_OK;
                
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_RECEIVE_MESSAGE_REPLY);
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, statusCode);
                
                    MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                    
                    MessageUtil.setServiceAttribute(response, Constants.DESTINATION_NAME, destName);
                
                    MessageUtil.setServiceAttribute(response, Constants.DOMAIN, domain);
                    
                    //String destname = MessageUtil.getServiceAttribute(request, Constants.DESTINATION_NAME);
                    //MessageUtil.setServiceAttribute(response, Constants.DESTINATION_NAME, destname);
                            
                }
                
                //System.out.println ("\n @@@@@@@@@@@@@@@@@@@@ after service tag: \n");
                //response.writeTo(System.out);
                
                context.setResponseMessage(response);
            } else if (Constants.SERVICE_VALUE_COMMIT.equals(serviceName)) {
                
                if (UMSServiceImpl.debug) {
                    logger.info("***** committing transaction ....");
                }
                
                MQService.commit (request);
               
                response = MessageUtil.createResponseMessage(request);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_COMMIT_REPLY);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, Constants.SERVICE_STATUS_VALUE_OK);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                
                context.setResponseMessage(response);
                
            } else if (Constants.SERVICE_VALUE_ROLLBACK.equals(serviceName)) {
                
                if (UMSServiceImpl.debug) {
                    logger.info("***** rolling back transaction ....");
                }
                
                MQService.rollback (request);
               
                response = MessageUtil.createResponseMessage(request);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_NAME, Constants.SERVICE_VALUE_ROLLBACK_REPLY);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_STATUS_NAME, Constants.SERVICE_STATUS_VALUE_OK);
                
                MessageUtil.setServiceAttribute(response, Constants.SERVICE_PROVIDER_ATTR_NAME, provider);
                
                context.setResponseMessage(response);
            }
            
        } catch (Exception e) {
            SOAPException soape = new SOAPException(e);

            throw soape;
        }

    }
       
    public void close() {
    	try {
            this.MQService.destroy();  	    		
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    public UMSServiceImpl getJMSService() {
        return this.MQService;
    }
    
}

