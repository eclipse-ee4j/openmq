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

package com.sun.messaging.ums.core;

import com.sun.messaging.ums.simple.SimpleMessage;
import com.sun.messaging.ums.simple.SimpleMessageFactory;
import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.ums.provider.openmq.ProviderDestinationService;
import com.sun.messaging.ums.resources.UMSResources;
import com.sun.messaging.ums.readonly.DefaultReadOnlyService;
import com.sun.messaging.ums.readonly.ReadOnlyMessageFactory;
import com.sun.messaging.ums.readonly.ReadOnlyRequestMessage;
import com.sun.messaging.ums.readonly.ReadOnlyResponseMessage;
import com.sun.messaging.ums.service.DestinationService;
import com.sun.messaging.ums.service.BrokerInfoService;
import com.sun.messaging.ums.service.SOAP2JMSService;
import com.sun.messaging.ums.service.UMSServiceImpl;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//import java.io.PrintWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Enumeration;

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;

//import javax.xml.messaging.ReqRespListener;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.SOAPConstants;

/**
 * 
 * The entry class for UMS services.
 * 
 * The configurations are defined in web.xml for thi servlet.
 * 
 * <servlet>
 *   <servlet-name>UMS</servlet-name>
 *   <servlet-class>
 *       com.sun.messaging.ums.core.UMSServlet
 *   </servlet-class>
 *	
 *   <!-- set openmq broker address -->
 *   <!--
 *	 <init-param>
 *     	<param-name>imqAddressList</param-name>
 *     	<param-value>localhost</param-value>
 *	 </init-param>
 *   -->
 *   
 *   
 *    <!-- provider registeration -->
 *    <!-- mom.provider.num-->
 *    
 *    <!--
 *    <init-param>
 *     	<param-name>mom.provider.1</param-name>
 *     	<param-value>jmsgrid</param-value>
 *	 </init-param>
 *    -->
 *    
 *    <!--
 *    <init-param>
 *     	<param-name>mom.provider.0</param-name>
 *     	<param-value>openmq</param-value>
 *	 </init-param>
 *    -->
 *    
 *    <!-- provider factory class registration, not used in first release -->
 *    <!--
 *    <init-param>
 *     	<param-name>mom.jmsgrid.providerFactory</param-name>
 *     	<param-value>com.sun.messaging.ums.jmsgrid.ProviderFactory</param-value>
 *	 </init-param>
 *    
 *    <init-param>
 *     	<param-name>mom.openmq.providerFactory</param-name>
 *     	<param-value>com.sun.messaging.ums.openmq.ProviderFactory</param-value>
 *	 </init-param>
 *    --> 
 * 
 *    <!-- jms grid daemon host configuration, not used in first release
 *    <init-param>
 *     	<param-name>grid.host</param-name>
 *     	<param-value>niagra2</param-value>
 *	 </init-param>
 *    -->
 *    
 *    <!-- authenticate with JMS server -->
 *    <!-- applications must provide user/pass for JMS server if set to true -->
 *    <init-param>
 *     	<param-name>ums.service.authenticate</param-name>
 *     	<param-value>false</param-value>
 *	 </init-param>
 *    
 *    <!-- applications must encode password with base64 encoding if set to true -->
 *    <init-param>
 *     	<param-name>ums.service.authenticate.basic</param-name>
 *     	<param-value>false</param-value>
 *	 </init-param>
 *    
 *    <!-- user name for UMS to authenticate with JMS Server -->
 *    <init-param>
 *     	<param-name>ums.user.name</param-name>
 *     	<param-value>guest</param-value>
 *	 </init-param>
 *    
 *    <!-- password for ums to authenticate with JMS server -->
 *    <init-param>
 *     	<param-name>ums.password</param-name>
 *     	<param-value>guest</param-value>
 *	 </init-param>
 *    
 * </servlet>
 * 
 * <!-- simple messaging service url -->
 *  <servlet-mapping>
 *   <servlet-name>UMS</servlet-name>
 *   <url-pattern>/simple</url-pattern>
 *  </servlet-mapping>
 * 
 * <!-- xml messaging service url -->
 * <servlet-mapping>
 *   <servlet-name>UMS</servlet-name>
 *   <url-pattern>/xml</url-pattern>
 * </servlet-mapping>
 * 
 * @author chiaming
 */
public class UMSServlet extends HttpServlet {

    /**
     * message factory to be used to construct soap message.
     */
    protected MessageFactory mfactory = null;

    private static Logger logger = UMSServiceImpl.logger;

    protected Properties props = null;

    /**
     * <p>define class name in the web.xml as follows:
     * <p> <init-param>
     * <p>  <param-name>mq.soap.service</param-name>
     * <p>  <param-value>MQSOAPService-CLASS-FULL-NAME</param-value>
     * <p> </init-param>
     *
     */
    //public static final String MQ_SERVICE_PARAM_NAME = "mq.soap.service";
    
    /**
     * default MQ SOAP service class name.
     */
    //public static final String MQ_SERVICE_DEFAULT_CLASSNAME = 
    //        "com.sun.messaging.ums.jms.SOAP2JMSService";

    /**
     * MQ SERVICE class name.
     */
    //protected String mqServiceClassName = null;

    /**
     * MQ SERVICE instance.
     */
    protected UMSService mqService = null;

    /**
     * Service Context instance.
     */
    protected ServiceContext serviceContext = null;
    
    /**
     * MQ JMS Service
     */
    protected UMSServiceImpl JMSService = null;
    
    /**
     * receive timeout for HTTP/GET JMS service.
     */
    private static long receiveTimeout = 7000;
    
    /**
     * topic/queue domain
     */
    private static final String TOPIC_DOMAIN = "topic";
    
    private static final String QUEUE_DOMAIN = "queue";
    
    /**
     * service field string used in HTTP/GET response message
     */
    private static final String SERVICE = "service=";
    
    /**
     * message field string used in HTTP/GET response message
     */
    private static final String MESSAGE = "text=";
    
    /**
     * destination field string used in HTTP/GET response message
     */
    private static final String DESTINATION = "destination=";
    
    /**
     * domain field string used in HTTP/GET response message
     */
    private static final String DOMAIN = "domain=";
    
    /**
     * UUID/SID/CLIENTID used in HTTP/GET response message
     */
    private static final String UUID = "sid=";
    
    /**
     * Content-Length
     */
    private static final String CONTENT_LENGTH = "Content-Length";
    
    /**
     * content-type
     */
    private static final String CONTENT_TYPE = "Content-Type";
    
    /**
     * 
     */
    private static final String TEXT_XML = "text/xml";
     
    /**
     * authenticate
     */
    protected boolean shouldAuthenticate = false;
    
    /**
     * admin debug service.
     * this is sent from HTTP/GET with service=admin
     * and debug=true/false.
     */
    private static final String ADMIN_DEBUG = "debug";
    
    private static final String SIMPLE = "simple";
    
    /**
     * simple messaging response message header fields
     */
    private static final String UMS_SERVICE = "ums.service";
    private static final String UMS_MOM = "ums.mom";
    private static final String UMS_DESTINATION = "ums.destination";
    private static final String UMS_DOMAIN = "ums.domain";
    private static final String UMS_STATUS = "ums.status";
    
    /**
     * UTF-8
     */
    private static final String UTF8 = "UTF-8";
    
    /**
     * empty string
     */
    private static final String EMPTY_STRING = "";
    
    /**
     * utf-8 content type
     */
    private static final String PLAIN_TEXT_CONTENT_TYPE = "text/plain;charset=UTF-8";
    
    private static final String HTML_CONTENT_TYPE = "text/html;charset=UTF-8";
    
    private static final String XML_CONTENT_TYPE = "text/xml;charset=UTF-8";
    
    /**
     * read only service -- this is to be used to dispatch readonly request.
     */
    private DefaultReadOnlyService readOnlyService = null;
    
    /**
     * init message factory and soap intermediate object.
     */
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);

        /**
         * init logger.
         */
        initLogger();

        /**
         * init config params:
         * 1. get mq service class name.
         * 2. construct init properties object from servlet config object.
         */
        initParams (servletConfig);

        /**
         * init/construct message factory.
         */
        initMessageFactory();

        /**
         * construct/init/start mq service.
         */
        initService ();
        
    }

    /**
     * Init logger
     */
    protected void initLogger() {
        
        //logger = Logger.getLogger(UMSService.LOGGER_CAT_NAME);

        String msg = UMSResources.getResources().getKString(UMSResources.UMS_LOGGER_INIT, logger.getName());
        logger.info(msg);
        
        //logger.fine("**** servlet logger init: " + logger.getName() );
    }

    /**
     * 1. Get mq service class name.
     * 2. Convert servlet init parameters into Properties object.
     */
    protected void initParams (ServletConfig servletConfig) {

        /**
         * get MQ Service Class Name.
         */
        //mqServiceClassName =
        //servletConfig.getInitParameter(MQ_SERVICE_PARAM_NAME);
        
        //if (mqServiceClassName == null) {
        //    mqServiceClassName = MQ_SERVICE_DEFAULT_CLASSNAME;
        //}

        props = new Properties();
        Enumeration enum2 = servletConfig.getInitParameterNames();

        while ( enum2.hasMoreElements() ) {
            String pname = (String) enum2.nextElement();
            String pvalue = (String) servletConfig.getInitParameter(pname);

            props.put(pname, pvalue);
        }
        
        String msg = UMSResources.getResources().getKString(UMSResources.UMS_CONFIG_INIT);
        logger.info(msg);

    }

    /**
     * init message factory -- may be over ridden by sub class.
     */
    protected void
    initMessageFactory () throws ServletException {
        try {
            mfactory = MessageFactory.newInstance(SOAPConstants.DEFAULT_SOAP_PROTOCOL);
        } catch (Exception e) {
            throw new ServletException (e.getMessage(), e);
        }
    }

    /**
     * instantiate/init/start MQ SOAP Service.
     */
    protected void
    initService () throws ServletException {

        try {

            /**
             * construct mq service.
             */
            //mqService =
            //(UMSService) Class.forName(mqServiceClassName).newInstance();
            
            mqService = new SOAP2JMSService();
            
            /**
             * construct service context.
             */
            initServiceContext();

            /**
             * init mq service with service context.
             */
            this.mqService.init( serviceContext );

            /**
             * start mq service.
             */
            this.mqService.start();
            
            /**
             * JMSService for doGet
             */
            //if (mqService instanceof SOAP2JMSService) {
            this.JMSService = ((SOAP2JMSService) mqService).getJMSService();
            //}
            
            //make the JMSService available to ReadOnlyServices
            props.put(DefaultReadOnlyService.JMSSERVICE, this.JMSService);
            
            /**
             * receive timeout - default 7 seconds
             */
            String tmp = props.getProperty (Constants.IMQ_RECEIVE_TIMEOUT, Constants.IMQ_RECEIVE_TIMEOUT_DEFAULT_VALUE);
            if (tmp != null) {
                receiveTimeout = Long.parseLong(tmp);
            }
            
            String msg = UMSResources.getResources().getKString(UMSResources.UMS_DEFAULT_RECEIVE_TIMEOUT, receiveTimeout);
            logger.info(msg);
            //logger.info("default receive timeout=" + receiveTimeout + " milli seconds.");
            
            /**
             * authenticate
             */
            tmp = props.getProperty(Constants.JMS_AUTHENTICATE, "false");
            this.shouldAuthenticate = Boolean.valueOf(tmp).booleanValue();
            
            /**
             * destination service
             * 
             */
            DestinationService.init(props);
            
            /**
             * broker info service
             * 
             */
            BrokerInfoService.init(props);
            
            /**
             * init readonly service
             */
            readOnlyService = new DefaultReadOnlyService();
            readOnlyService.init(props);
            
            msg = UMSResources.getResources().getKString(UMSResources.UMS_SERVICE_STARTED);
            logger.info(msg);
            
        } catch (Exception e) {
            throw new ServletException (e.getMessage(), e);
        }
    }

    /**
     * init service context.  Sub class may over ride this method and
     * provide a customized service object.
     */
    protected void initServiceContext() throws ServletException {
        serviceContext = new UMSServiceContext (props);
    }
    
    public void
    doGet(HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
        //this.doSimpleMessaging(req, resp);
        this.doReadOnlyService(req, resp);
    }
    
    /**
     * The features are not yet ready for public use yet.
     * XXX
     * @param req
     * @param resp
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    private void doReadOnlyService (HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
        
        int status = resp.SC_BAD_REQUEST;
        
        String service = req.getParameter(Constants.SERVICE_NAME);
        //String user = req.getParameter(Constants.USER);
        //String pass = req.getParameter(Constants.PASSWORD);
        
        String respMsg = null;
        
        if (UMSServiceImpl.getDebug()) {
            
            String remoteHost = req.getRemoteHost();
            int remotePort = req.getRemotePort();
            
            String requestURL = req.getRequestURL().toString();
            logger.info("requestURL=" + requestURL + ", client=" + remoteHost + ":" + remotePort + ", request service=" + service);
        }
        
        Map map = this.getHeadersAsMap(req);
        
        //use string[] to be consistent
        String[] ru = new String[1];
        ru[0] = req.getRequestURL().toString();
        map.put(DefaultReadOnlyService.REQUEST_URL, ru);
        
        try {
            
	        //respMsg = readOnlyService.request(map);
	        ReadOnlyRequestMessage rom = ReadOnlyMessageFactory.createRequestMessage(map, req.getInputStream());
	        ReadOnlyResponseMessage respm = readOnlyService.request(rom);
	        
	        respMsg = respm.getResponseMessage();
	        
	        //get status code from respm
	        status = respm.getStatusCode();
	        
	        resp.setStatus(status);
	
	        if (isHTML (respMsg)) {
	            resp.setHeader(CONTENT_TYPE, HTML_CONTENT_TYPE);
	        } else if (isXML (respMsg)) {
	            resp.setHeader(CONTENT_TYPE, XML_CONTENT_TYPE);
	        } else {
	            resp.setHeader(CONTENT_TYPE, PLAIN_TEXT_CONTENT_TYPE);
	        }
	        
	        byte[] data = null;
	
	        if (respMsg != null) {
	            data = respMsg.getBytes(UTF8);
	        } else {
	            data = EMPTY_STRING.getBytes(UTF8);
	        }
	
	        DataOutputStream dos = new DataOutputStream(resp.getOutputStream());
	        
	        resp.setContentLength(data.length);
	
	        dos.write(data, 0, data.length);
	
	        dos.flush();
	        dos.close();
        
        } catch (Exception e) {

            logger.log(Level.WARNING, e.getMessage(), e);

            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setHeader(CONTENT_TYPE, PLAIN_TEXT_CONTENT_TYPE);
           
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            PrintStream ps = new PrintStream (baos);
            ps.println(e);

            // don't print a stack trace: too much information
            //e.printStackTrace(ps);
            
            byte[] data = baos.toString().getBytes(UTF8);
            
            DataOutputStream dos = new DataOutputStream(resp.getOutputStream());

            resp.setContentLength(data.length);

            dos.write(data, 0, data.length);

            dos.flush();
            dos.close();
            resp.flushBuffer();
        
        }
    }
    
    public void
    doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
        
        String uri = req.getServletPath();
        
        if (UMSServiceImpl.debug) {
            
            String remoteHost = req.getRemoteHost();
            int remotePort = req.getRemotePort();
            
            String requestURL = req.getRequestURL().toString();
            logger.info("requestURL=" + requestURL + ", client=" + remoteHost + ":" + remotePort);
        }
        
        if ( uri != null && uri.indexOf(SIMPLE) > 0 ) {
            doSimpleMessaging (req, resp);
            //doTest(req, resp);    
        } else {
            doXmlMessaging (req, resp);
        }
        
    }
    
    /**
     * Send/Receive a simple text message.  Default domain is queue.  To specify 
     * domain, add domain=queue/topic in the query string.
     * 
     * Example:
     * 
     * http://host:port/xmlprotocol/service?clientId=chiaming&service=receive&destination=testQ
     * http://host:port/xmlprotocol/service?clientId=chiaming&service=send&destination=testQ&text=myMessage 
     * 
     * @param req
     * @param resp
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    public void doSimpleMessaging(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String respMsg = null;

        int status = resp.SC_BAD_REQUEST;

        boolean isSend = false;
        boolean isReceive = false;
        boolean isAdmin = false;
        boolean isLogin = false;
        boolean isClose = false;
        boolean isCommit = false;
        boolean isRollback = false;

        boolean isValidRequest = true;

        //String separator = ",";

        Map map = this.getHeadersAsMap(req);

        //String text = this.readHttpBody(req);
        InputStream in = req.getInputStream();

        SimpleMessage msg = SimpleMessageFactory.createMessage(map, in);

        in.close();

        //String text = msg.getText();

        String destName = msg.getMessageProperty(Constants.DESTINATION_NAME);
        boolean isTopic = msg.isTopicDomain();

        String clientId = msg.getMessageProperty(Constants.CLIENT_ID);

        if (UMSServiceImpl.debug) {
            logger.info("Simple messaging sid=" + clientId);
        }

        if (msg.isSendService()) {
            isSend = true;
        } else if (msg.isReceiveService()) {
            isReceive = true;
        } else if (msg.isLoginService()) {
            isLogin = true;
        } else if (msg.isCloseService()) {
            isClose = true;
        } else if (msg.isAdminService()) {
            isAdmin = true;
        } else if (msg.isCommitService()) {
            isCommit = true;
        } else if (msg.isRollbackService()) {
            isRollback = true;
        } else {
            isValidRequest = false;
        }

        try {

            //logger.info("request servlet path=" + req.getServletPath());

            if (isValidRequest == false) {

                status = resp.SC_BAD_REQUEST;
                //throw exception   
                respMsg = "Invalid query string., see http://host:port/<context>/";

            } else {

                if (isSend) {

                    //String text = req.getParameter(Constants.HTTP_GET_SEND_TEXT);

                    String text = msg.getText();

                    if (UMSServiceImpl.debug) {
                        logger.info("Simple messaging, sending text=" + text);
                    }

                    this.JMSService.sendText(clientId, isTopic, destName, text, map);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_SEND_MESSAGE_REPLY);
                    resp.setHeader(UMS_DESTINATION, destName);
                    resp.setHeader(UMS_DOMAIN, getDomain(isTopic));

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));
                    resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);

                } else if (isReceive) {

                    long timeout = this.getServiceTimeout(msg);

                    //receive message
                    String text = this.JMSService.receiveText(clientId, destName, isTopic, timeout, map);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_RECEIVE_MESSAGE_REPLY);
                    resp.setHeader(UMS_DESTINATION, destName);
                    resp.setHeader(UMS_DOMAIN, getDomain(isTopic));

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));

                    respMsg = text;

                    if (respMsg == null) {
                        //respMsg = "null";
                        resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_NO_MESSAGE);
                    } else {
                        resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);
                    }

                } else if (isLogin) {

                    String sid = this.JMSService.authenticate(map);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_LOGIN_REPLY);

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));
                    resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);

                    respMsg = sid;
                } else if (isClose) {

                    String sid = this.JMSService.closeClient2(map);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_CLOSE_REPLY);

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));
                    resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);

                } else if (isCommit) {

                    this.JMSService.commit(msg);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_COMMIT_REPLY);

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));
                    resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);
                } else if (isRollback) {

                    this.JMSService.rollback(msg);

                    resp.setHeader(UMS_SERVICE, Constants.SERVICE_VALUE_ROLLBACK_REPLY);

                    resp.setHeader(UMS_MOM, JMSService.getProvider(map));

                    resp.setHeader(UMS_STATUS, Constants.SERVICE_STATUS_VALUE_OK);

                } else if (isAdmin) {

                    String user = req.getParameter(Constants.USER);
                    String pass = req.getParameter(Constants.PASSWORD);
                    ProviderDestinationService pds = DestinationService.getProviderDestinationService(null);

                    pds.authenticate(user, pass);

                    String flag = req.getParameter(ADMIN_DEBUG);

                    boolean debug = Boolean.valueOf(flag).booleanValue();

                    UMSServiceImpl.debug = debug;

                    respMsg = SERVICE + "admin, " + ADMIN_DEBUG + "=" + debug;
                }

                status = resp.SC_OK;
            }

            resp.setStatus(status);

            //resp.setHeader("Content-Type", "text/plain;charset=UTF-8");
            resp.setHeader(CONTENT_TYPE, PLAIN_TEXT_CONTENT_TYPE);
            //resp.setCharacterEncoding("UTF-8");

            byte[] data = null;

            if (respMsg != null) {
                data = respMsg.getBytes(UTF8);
            } else {
                //data = "".getBytes(UTF8);
                data = EMPTY_STRING.getBytes(UTF8);
            }

            DataOutputStream dos = new DataOutputStream(resp.getOutputStream());


            resp.setContentLength(data.length);

            dos.write(data, 0, data.length);

            //dos.writeUTF(respMsg);

            dos.flush();
            dos.close();

        } catch (Exception e) {

            logger.log(Level.WARNING, e.getMessage(), e);

            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setHeader(CONTENT_TYPE, PLAIN_TEXT_CONTENT_TYPE);
           
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            PrintStream ps = new PrintStream (baos);
            e.printStackTrace(ps);
            byte[] data = baos.toString().getBytes(UTF8);
            
            DataOutputStream dos = new DataOutputStream(resp.getOutputStream());

            resp.setContentLength(data.length);

            dos.write(data, 0, data.length);

            dos.flush();
            dos.close();
            resp.flushBuffer();
        }

    }

    /**
    * Http request services.  HTTP requests are transformed into
    * SOAP messages and dispated to the appropriate services.
    */
   public void
   doXmlMessaging (HttpServletRequest req, HttpServletResponse resp)
   throws ServletException, IOException {

       SOAPMessage msg = null;

       try {
           // Get all the headers from the HTTP request.
           MimeHeaders headers = getHeaders(req);

           // Get the body of the HTTP request.
           InputStream is = req.getInputStream();

           // Now internalize the contents of a HTTP request and
           // create a SOAPMessage
           msg = mfactory.createMessage(headers, is);

           SOAPMessage reply = null;

           /**
            * Dispatch message to mq soap service.
            */
           reply = onMessage (msg);
           
           //System.out.println ("@@@@@@ in doPost() \n");
           //reply.writeTo(System.out);
                   

           if ( reply != null ) {

               // Need to saveChanges 'cos we're going to use the
               // MimeHeaders to set HTTP response information. These
               // MimeHeaders are generated as part of the save.

               if (reply.saveRequired()) {
                   reply.saveChanges();
               }

               if ( isSOAPFault (reply) ) {
                   resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
               } else {
                   resp.setStatus(HttpServletResponse.SC_OK);
               }

               //remove content-length header field
               reply.getMimeHeaders().removeHeader(CONTENT_LENGTH);
               
               putHeaders (reply.getMimeHeaders(), resp);
               
               String[] sa = reply.getMimeHeaders().getHeader(CONTENT_TYPE);
               if ( sa == null || sa.length == 0 ) {
            	   //XXX: 
            	   //int acount = reply.countAttachments();
            	   resp.setHeader (CONTENT_TYPE, TEXT_XML);
               }
               
               // Write out the message on the response stream.
               OutputStream os = resp.getOutputStream();
               reply.writeTo(os);

               os.flush();

           } else {
               resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
           }
       } catch (Throwable ex) {
           /**
            * Log exception.
            */
           logger.log(Level.WARNING, ex.getMessage(), ex);

           resp.setStatus (HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
       }
   }

   /**
     * Returns a <code>MimeHeaders</code> object that contains the headers
     * in the given <code>HttpServletRequest</code> object.
     *
     * @param req the <code>HttpServletRequest</code> object that a
     *        messaging provider sent to the servlet
     * @return a new <code>MimeHeaders</code> object containing the headers
     *         in the message sent to the servlet
     */
    protected static MimeHeaders getHeaders(HttpServletRequest req) {
        Enumeration enum2 = req.getHeaderNames();
        MimeHeaders headers = new MimeHeaders();

        while (enum2.hasMoreElements()) {
            String headerName = (String)enum2.nextElement();
            String headerValue = req.getHeader(headerName);

            StringTokenizer values = new StringTokenizer(headerValue, ",");
            while (values.hasMoreTokens())
                headers.addHeader(headerName, values.nextToken().trim());
        }

        return headers;
    }
    
    
    private void parseQueryString (Map map, String querystr) {
                
        String[] values = querystr.split("&");
        
        for (int i=0; i<values.length; i++) {
              
            //String[] keyValuePair = values[i].split("=");
            
            //String key = keyValuePair[0];
            
            //value as array -- to be consisitent with the rest of http headers
            //in the map.
            //String[] value = new String[1];
            //value[0] = keyValuePair[1];
            
            int index = values[i].indexOf('=');
            String key = values[i].substring(0, index);
            
            String[] value = new String[1];
            value[0] = values[i].substring(index+1);
            
            map.put(key, value);
            
            if (UMSServiceImpl.debug) {
                logger.info ("Adding query string param, " + key+"="+key + ", value[0]=" + value[0]);
            }
        }
                
    }
    
    protected Map getHeadersAsMap (HttpServletRequest req) {
        
        Hashtable<String, String[]> headers = new Hashtable <String, String[]> ();
        
        String querystr = req.getQueryString();
        
        if (querystr != null) {
            parseQueryString (headers, querystr);
        }
        
        Enumeration enum2 = req.getHeaderNames();
        

        while (enum2.hasMoreElements()) {
            String headerName = (String)enum2.nextElement();
            String headerValue = req.getHeader(headerName);

            ArrayList <String> alist = new ArrayList<String>();
            
            StringTokenizer values = new StringTokenizer(headerValue, ",");
            while (values.hasMoreTokens()) {
                //headers.addHeader(headerName, values.nextToken().trim());
                String hvtrim = values.nextToken().trim();
                alist.add(hvtrim);
                
                if (UMSServiceImpl.debug) {
                    logger.info ("Adding http header to map, " + headerName +"=" + hvtrim);
                }
            }
            
            String[] sarray = new String [alist.size()];
            
            alist.toArray(sarray);
            
            headers.put (headerName, sarray);
            
            //logger.info ("**** header in map, header=" + headerName + ", value=" + sarray);
        }

        return headers;
    }
    
     protected Map getRequestQueryAsMap_deprecated (HttpServletRequest req) {
         
         Hashtable <String, String> map = new Hashtable <String, String>();
         
         String qstr = req.getQueryString();
         
         StringTokenizer tokens = new StringTokenizer (qstr, " ");
         
         while (tokens.hasMoreTokens()) {
             
             //String pair = tokens.nextToken();
             tokens.nextToken();
            
         }
         
         return map;
     }
    
    protected String readHttpBody(HttpServletRequest req)
            throws ServletException, IOException {

        String text = null;

        InputStream in = req.getInputStream();
        DataInputStream din = new DataInputStream(in);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] bytes = new byte[1024];

        boolean more = true;
        int len = 0;

        while (more) {

            len = din.read(bytes);
            
            if (UMSServiceImpl.debug) {
                logger.info("Simple messaging, reading message body ... len=" + len);
            }
            
            if (len > 0) {
                baos.write(bytes, 0, len);
            } else if (len < 0) {
                more = false;
            }
        }

        byte[] body = baos.toByteArray();

        String enc = req.getCharacterEncoding();
        if (enc == null) {
            enc = UTF8;
        }
        
        baos.close();
        din.close();

        text = new String(body, enc);
        return text;
    }

    /**
     * Sets the given <code>HttpServletResponse</code> object with the
     * headers in the given <code>MimeHeaders</code> object.
     *
     * @param headers the <code>MimeHeaders</code> object containing the
     *        the headers in the message sent to the servlet
     * @param res the <code>HttpServletResponse</code> object to which the
     *        headers are to be written
     * @see #getHeaders
     */
    protected static void
    putHeaders(MimeHeaders headers, HttpServletResponse res) {
         
        Iterator it = headers.getAllHeaders();
        
        while (it.hasNext()) {
            MimeHeader header = (MimeHeader)it.next();

            String[] values = headers.getHeader(header.getName());
            
            if (values.length == 1) {
                
                res.setHeader(header.getName(), header.getValue());
            } else {
                StringBuffer concat = new StringBuffer();
                int i = 0;

                while (i < values.length) {
                    if (i != 0) {
                        concat.append(',');
                    }
                    concat.append(values[i++]);
                }
                
                res.setHeader(header.getName(), concat.toString());
                  
            }
        }
    }

    /**
     * check if this is a soap fault message.
     */
    protected boolean isSOAPFault (SOAPMessage message) throws SOAPException {
        SOAPBody body = message.getSOAPPart().getEnvelope().getBody();
        return body.hasFault();
    }

    /**
     * Message delivered from doPost().  Subclass can override this method
     * or define mq.soap.service property in web.xml file.
     */
    public SOAPMessage onMessage ( SOAPMessage message ) {
        SOAPMessage reply = null;

        try {
            reply = mqService.onMessage(message);
        } catch (Throwable e) {
            //this should not happen.  the exception is handled in onMessage()
            //and doPost().
            log (e.getMessage(), e);
        }

        return reply;
    }

    /**
     * destroy this servlet.
     */
    public void destroy() {
        mqService.close();
    }
    
    /**
     * Send/Receive a simple text message.  Default domain is queue.  To specify 
     * domain, add domain=queue/topic in the query string.
     * 
     * Example:
     * 
     * http://host:port/xmlprotocol/service?clientId=chiaming&service=receive&destination=testQ
     * http://host:port/xmlprotocol/service?clientId=chiaming&service=send&destination=testQ&text=myMessage 
     * 
     * @param req
     * @param resp
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    
    /**
    public void
    doGetOld(HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
        
        String respMsg = null;
        
        int status = resp.SC_BAD_REQUEST;
        
        boolean isSend = false;
        boolean isReceive = false;
        boolean isAdmin = false;
        boolean isLogin = false;
        boolean isClose = false;
        
        boolean isValidRequest = true;
        
        boolean isTopic = false;
        
        String separator = ",";
        
        //check the query string
        String clientId = req.getParameter(Constants.CLIENT_ID);
        String domain = req.getParameter(Constants.DOMAIN);
        if (Constants.TOPIC_DOMAIN.equals(domain)) {
            isTopic = true;
        }
        
        String destName = req.getParameter(Constants.DESTINATION_NAME);
        
        String service = req.getParameter(Constants.SERVICE_NAME);
        
        if (Constants.SERVICE_VALUE_SEND_MESSAGE.equals(service)) {
            isSend = true;
        } else if (Constants.SERVICE_VALUE_RECEIVE_MESSAGE.equals(service)) {
            isReceive = true;
        } else if (Constants.SERVICE_VALUE_LOGIN.equals(service)) {
            isLogin = true;
        } else if (Constants.SERVICE_VALUE_CLOSE.equals(service)) {
            isClose = true;
        } else if ("admin".equals(service)) {
            isAdmin = true;
        } else {
            isValidRequest = false;
        }
       
        try {
            
            //logger.info("request servlet path=" + req.getServletPath());
        
        if (isValidRequest == false) {
            
           status = resp.SC_BAD_REQUEST; 
          //throw exception   
           respMsg = "Invalid query string., Usage: http://hostport/service?clientId=ID&destination=destName";
              
        } else {
            
            Map map = req.getParameterMap();
            
            separator = req.getParameter(Constants.HTTP_GET_RESPONSE_SEPARATOR);
            if (separator ==null) {
                separator = Constants.HTTP_GET_RESPONSE_SEPARATOR_DEFAULT_VALUE;
            }
        
            if (isSend) {
                
                String text = req.getParameter(Constants.HTTP_GET_SEND_TEXT);
                
                this.JMSService.sendText(clientId, isTopic, destName, text, map);
                
                respMsg = SERVICE + service + separator + DESTINATION + 
                        destName + separator + DOMAIN + getDomain (isTopic) + separator + MESSAGE + text;
                
            } else if (isReceive) {
                
                long timeout = this.getServiceTimeout(req);
                
                //receive message
                String text = this.JMSService.receiveText(clientId, destName, isTopic, timeout, map);
                
                respMsg = SERVICE + service + separator + DESTINATION + destName + 
                        separator + DOMAIN + getDomain (isTopic) + separator + MESSAGE + text;
                
            } else if (isLogin) {
                
                String uuid = this.JMSService.authenticate(map);
                
                respMsg = SERVICE + service + separator + UUID + uuid;
            } else if (isClose) {
                
                String sid = this.JMSService.closeClient2 (map);
                
                respMsg = SERVICE + service + separator + UUID + sid;
                
            } else if (isAdmin) {
                
                String flag = req.getParameter(ADMIN_DEBUG);
                
                boolean debug = Boolean.valueOf(flag).booleanValue();
                
                MQServiceImpl.debug = debug;
            
                respMsg = SERVICE + service + separator + ADMIN_DEBUG + "=" + debug;
            }
            
            status = resp.SC_OK;
        }
        
        } catch (Exception e) {
            
            throw new ServletException (e);
                  
        }
        
        resp.setStatus(status);
        
        resp.setHeader ("Content-Type", "text/plain");

        OutputStream os = resp.getOutputStream();
        
        PrintWriter pw = new PrintWriter (os);
           
        pw.println(respMsg);
        
        pw.close();
    }
    **/
    
    protected static long getServiceTimeout (SimpleMessage msg) {
        
        long timeout = receiveTimeout;
        
        try {
            
            String str = msg.getMessageProperty (Constants.RECEIVE_TIMEOUT);
            
            if (str != null) {
                
                timeout = Long.parseLong(str);
            
                if (timeout <=0 || timeout > 60000) {
                    timeout = receiveTimeout;
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        
        return timeout;
    }
    
    private String getDomain (boolean isTopic) {
        if (isTopic) {
            return TOPIC_DOMAIN;
            
        } else {
            return QUEUE_DOMAIN;
        }
    }
    
    protected static boolean isHTML (String msg) {
        boolean flag = false;
        
        if (msg == null || msg.length()==0) {
            return false;
        }
        
        String prefix = msg.substring(0, 5);
        if ("<html".equalsIgnoreCase(prefix) == true) {
            flag = true;
        }
        
        return flag;
    }
    
    protected static boolean isXML (String msg) {
        boolean flag = false;
        
        if (msg == null || msg.length()==0) {
            return false;
        }
        
        String prefix = msg.substring(0, 5);
        if ("<?xml".equalsIgnoreCase(prefix) == true) {
            flag = true;
        }
        
        return flag;
    }

}
