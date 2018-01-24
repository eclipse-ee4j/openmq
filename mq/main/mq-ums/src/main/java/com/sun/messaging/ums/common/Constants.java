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

package com.sun.messaging.ums.common;

public class Constants {

    //public static final String JMS_NS_PREFIX = "jms";
    //public static final String JMS_NS_URI = "https://mq.java.net/MQService";
    
    public static final String JMS_NS_PREFIX = "ums";
    public static final String JMS_NS_URI = "https://mq.java.net/ums";
    
    public static final String MESSAGE_HEADER = "MessageHeader";
   
    //JMS destination domain attribute name.
    public static final String DOMAIN = "domain";
    //JMS destination domain attribute value -- topic.
    public static final String TOPIC_DOMAIN = "topic";
    //JMS destination domain attribute value -- queue.
    public static final String QUEUE_DOMAIN = "queue";
    //message context attributes
    //used by soap services
    //public static final String SERVICE_TYPE = "SERVICE_TYPE";
    //Content-Type header name
    //public static final String Content_Type = "Content-Type";
    //message header id attribute name
    public static final String ID = "id";
    //message header version attribute name
    public static final String VERSION = "version";

    //Message Header Local name
    public static final String FROM = "From";
    public static final String TO = "To";
    public static final String MESSAGE_ID = "MessageID";
    public static final String TIMESTAMP = "Timestamp";
    public static final String REF_TO_MESSAGE_ID = "RefToMessageID";
    
    //Service element name
    public static final String SERVICE = "Service";
    //service values used by MQ JMS SOAP Service
    //public static final String CREATE_CONSUMER_REPLY_SERVICE = "CREATE_CONSUMER_REPLY";
    //public static final String CLOSE_CONSUMER_REPLY_SERVICE = "CLOSE_CONSUMER_REPLY";
    //public static final String SEND_REPLY_SERVICE = "SEND_MESSAGE_REPLY";
    //From element
    public static final String FROM_DEFAULT_VALUE = "MQSOAPService";
    //To element default value
    public static final String TO_DEAFULT_VALUE = "MQSOAPService";
    //property set if MQ carries SOAP message graph in the BytesMessage.
    //This is a boolean property.
    public static final String JMS_SUN_SOAP_MESSAGE_TYPE = "JMS_SUN_SOAP_MESSAGE_TYPE";
    //prefix for mime types.
    public static final String JMS_SUN_PROPERTY_PREFIX = "JMS_SUN_";
    //Service Element, serviceName attribute name
    public static final String SERVICE_NAME = "service";
    //Service serviceName attribute value
    public static final String SERVICE_VALUE_SEND_MESSAGE = "send";
    //Service serviceName attribute value
    public static final String SERVICE_VALUE_SEND_MESSAGE_REPLY = "send_reply";
    //Service serviceName attribute value
    public static final String SERVICE_VALUE_RECEIVE_MESSAGE = "receive";
    //Service serviceName attribute value
    public static final String SERVICE_VALUE_RECEIVE_MESSAGE_REPLY = "receive_reply";
    
    //Service login attribute
    public static final String SERVICE_VALUE_LOGIN = "login";
    public static final String SERVICE_VALUE_LOGIN_REPLY = "login_reply";
    
    public static final String SERVICE_VALUE_CLOSE = "close";
    public static final String SERVICE_VALUE_CLOSE_REPLY = "close_reply";
    
    public static final String SERVICE_VALUE_COMMIT = "commit";
    public static final String SERVICE_VALUE_COMMIT_REPLY = "commit_reply";
    
    public static final String SERVICE_VALUE_ROLLBACK = "rollback";
    public static final String SERVICE_VALUE_ROLLBACK_REPLY = "rollback_reply";
    
    public static final String SERVICE_VALUE_LIST_DEST = "listDestinations";
    public static final String SERVICE_VALUE_LIST_DEST_REPLY = "listDestinations_reply";
    
    public static final String SERVICE_VALUE_QUERY_DEST = "query";
    public static final String SERVICE_VALUE_QUERY_DEST_REPLY = "query_reply";
    
    
    //Service service destination attribute name
    public static final String DESTINATION_NAME = "destination";
    ////Service clientID attribute name
    public static final String CLIENT_ID = "sid";
    
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    
    public static final String TRANSACTED = "transacted";
    
    ////Service timeout attribute name -- used by receive
    public static final String RECEIVE_TIMEOUT = "timeout";
    //Service element, service status code attribute name.
    public static final String SERVICE_STATUS_NAME = "status";
    
    //Service element, provider attribute name - default attribute value=openmq
    public static final String SERVICE_PROVIDER_ATTR_NAME = "mom";
    
    //Service element, service status code attribute value - OK.
    public static final String SERVICE_STATUS_VALUE_OK = "200";
    //Service element, service status code attribute value - no message received.
    public static final String SERVICE_STATUS_VALUE_NO_MESSAGE = "404";
    
    //HTTP/GET respond message separator parameter name
    public static final String HTTP_GET_RESPONSE_SEPARATOR = "separator";
    
    //HTTP/GET respond message default separator
    public static final String HTTP_GET_RESPONSE_SEPARATOR_DEFAULT_VALUE = "&";
    
    //HTTP/GET SEND text parameter name
    public static final String HTTP_GET_SEND_TEXT = "text";
    
    //default messaging provider
    public static final String DEFAULT_PROVIDER = "openmq";
    
    /**
	 * broker address config. parameter name.
	 * default localhost.
	 */
	public static final String IMQ_BROKER_ADDRESS = "imqAddressList";
	
	
	/**
	 * UMS receive timeout - default 30 secs.
	 */
	public static final String IMQ_RECEIVE_TIMEOUT = "ums.receive.timeout";
	
        //default receive timeout - 7 seconds.
        public static final String IMQ_RECEIVE_TIMEOUT_DEFAULT_VALUE = "7000";
        
	/**
	 * JMS cache duration.  JMS resources are closed if not used
	 * for the defined duration (milli secs).
	 * 
	 * default is 7 minutes.
	 */
	public static final String CACHE_DURATION = "ums.cache.duration";
	
	/**
	 * Daemon wake up intervals to sweep the cache.
	 * 
	 * default 30 seconds.
	 */
	public static final String SWEEP_INTERVAL = "ums.cache.sweep.interval";

	/**
	 * Max client per cached connection
	 * Deafult 300.
	 */
	public static final String MAX_CLIENT_PER_CONNECTION = "ums.cache.connection.max.clients";
        
        /**
         * user name for UMS to authenticate to JMS server
         */
        public static final String IMQ_USER_NAME = "ums.user.name";
        
        /**
         * password for UMS to authenticate to JMS server
         */
        public static final String IMQ_USER_PASSWORD = "ums.password";
           
        /**
         * should use MQ to authenticate, default is true
         */
        public static final String JMS_AUTHENTICATE = "ums.service.authenticate";
        
        /**
         * should authenticate to jms server default value
         */
        public static final String JMS_AUTHENTICATE_DEFAULT_VALUE = "true";
        
        /**
         * authenticate type - use base64 encoding? default is set to false 
         */
        public static final String BASIC_AUTH_TYPE = "ums.service.authenticate.basic";
        
        /**
         * authenticate with UMS with base64 encoding for passowrd?
         */
        public static final String BASIC_AUTH_TYPE_DEFAULT_VALUE = "false";
}
