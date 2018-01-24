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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
//import java.io.PrintWriter;
import java.util.Properties;

import java.util.logging.*;

import javax.xml.soap.Detail;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPBody;

import com.sun.messaging.ums.common.MessageUtil;

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
public abstract class UMSService implements SOAPService {

    /**
     * logger category name.
     */
    public static final String LOGGER_CAT_NAME =
    "com.sun.messaging.ums.MQSOAPService";

    /**
     * SOAP context associated with this SOAP Service.
     */
    protected ServiceContext serviceContext = null;

    /**
     * init properties.
     */
    protected Properties props = null;

    /**
     * request handler chain.
     */
    protected MessageHandlerChain reqChain = new MessageHandlerChain();

    /**
     * resp handler chain.
     */
    protected MessageHandlerChain respChain = new MessageHandlerChain();

    /**
     * logger.
     */
    private Logger logger = Logger.getLogger( LOGGER_CAT_NAME );

    /**
     * request handler registration name prefix.
     * for example, define the following in the web.xml:
     *
     * <p>mq.soap.request.handler.1=com.sun.TestMessageListener1
     * <p>mq.soap.request.handler.2=com.sun.TestMessageListener2
     */
    public static final String REQUEST_HANDLER_PREFIX =
    "mq.soap.request.handler.";

    /**
     * response handler registration name prefix.
     * for example, define the following in the web.xml:
     *
     * <p>mq.soap.response.handler.1=com.sun.TestMessageListener3
     * <p>mq.soap.response.handler.2=com.sun.TestMessageListener4
     */
    public static final String RESPONSE_HANDLER_PREFIX =
    "mq.soap.response.handler.";

    /**
     * init this SOAPService with the specified Properties in the parameter.
     */
    public void init (ServiceContext context) throws SOAPException {
        this.serviceContext = context;

        this.props = context.getInitProperties();

        initHandlerChains();
    }

    /**
     * init req/resp handler chains.
     */
    protected void initHandlerChains () {
        initHandlers (REQUEST_HANDLER_PREFIX, this.reqChain);
        initHandlers (RESPONSE_HANDLER_PREFIX, this.respChain);
    }

    /**
     * Load and register message handlers to the handler chain.
     */
    protected void initHandlers (String prefix, MessageHandlerChain chain) {

        boolean moreHandlers = true;

        try {
            int index = 1;
            while ( moreHandlers ) {

                /**
                 * Get handler property name sequentially.
                 */
                String propName = prefix + index;

                /**
                 * get handler class name.
                 */
                String className = props.getProperty(propName);

                /**
                 * if cannot find the next class for the next sequence,
                 * stop right here.
                 */
                if ( className == null ) {
                    moreHandlers = false;
                } else {

                    /**
                     * load the handler with its class name.
                     */
                    MessageHandler handler =
                    (MessageHandler) Class.forName(className).newInstance();

                    /**
                     * init the handler with current service context.
                     */
                    handler.init( this.getServiceContext() );

                    /**
                     * add the handler to the chain.
                     */
                    chain.addMessageHandler(handler);

                    /**
                     * increase index for the next handler property name.
                     */
                    index ++;
                }
            }
        } catch (Exception e) {
            logger.log( Level.SEVERE, e.getMessage(), e );
        }
    }

    /**
     * Get req handler chain.
     */
    public MessageHandlerChain getReqHandlerChain() {
        return this.reqChain;
    }

    /**
     * Get resp handler chain.
     */
    public MessageHandlerChain getRespHandlerChain() {
        return this.respChain;
    }

    /**
     * SOAP service life cycle - start this soap service.
     * To be overriden by sub class.
     */
    public void start() {
        ;
    }

    /**
     * SOAP service life cycle - stop this soap service.
     * To be overriden by sub class.
     */
    public void stop() {
        ;
    }

    /**
     * SOAP service life cycle - close this soap service.
     * Close all message handlers and release resources.
     */
    public void close() {
        closeHandlers(this.reqChain);
        closeHandlers(this.respChain);
    }

    /**
     * Get the ServiceContext object associated with this SOAP service.
     */
    public ServiceContext getServiceContext() {
        return this.serviceContext;
    }


    /**
     * Get this soap service URI.
     */
    public String getServiceName() {
        return "MQSOAPService";
    }

    /**
     * MQ SOAP Service implements this interafce.
     *
     * <p>The request handler chain is processed in sequence before calling
     * the service() method.
     * <p>The respond handler chain is processed in sequence after calling
     * the service() method.
     */
    public SOAPMessage onMessage (SOAPMessage message) {

        SOAPMessage reply = null;

        logger.fine("MQSOAPService received message.");

        try {

            UMSMessageContext context = new UMSMessageContext();
            context.setRequestMessage( message );

            logger.fine("created msg context: " + context);

            logger.fine("*** processing request chain ...");
            processHandlerChain (this.reqChain, context);

            /**
             * To be over ridden by subclass.
             */
            service (context);

            //context.setResponseMessage(reply);

            logger.fine("*** processing response chain ...");
            processHandlerChain(this.respChain, context);

            reply = context.getResponseMessage();

        } catch (MessageHandlerException mhe) {

            mhe.printStackTrace(System.out);

            logger.log(Level.WARNING, mhe.getMessage(), mhe);

            /**
             * if there is a soap fault message in the exception,
             * use it.  Otherwise, construct one and set error
             * code and string to *Client* and *Client Error*.
             */
            reply = mhe.getSOAPFaultMessage();

            if ( reply == null ) {
                reply = createSOAPFaultMessage(mhe, "Client", "Client Error");
            }

        } catch (Throwable throwe) {
            logger.log(Level.WARNING, throwe.getMessage(), throwe);

            /**
             * For the reset of the exception, assume it is the
             * server unable to process the message.  This implies
             * that soap header processing SHOULD be handled in
             * the Message Handler.  Validation of the SOAP headers
             * SHOULD throw MessageHandlerException if unable to
             * process the message.
             */
            reply = createSOAPFaultMessage (throwe, "Server", "Server Error");
        } finally {
            ;
        }

        return reply;

    }

    /**
     * To be over ridden by sub class.
     */
    public abstract void service (MessageContext context) throws SOAPException;

    /**
     * process message handler chain.  Message handles are called in sequesne.
     */
    protected void
    processHandlerChain (MessageHandlerChain chain, MessageContext context)
    throws SOAPException {

        int size = chain.size();
        for ( int index = 0; index < size; index++) {

            MessageHandler handler =
            (MessageHandler) chain.getMessageHandlerAt(index);

            logger.fine("Calling handler: " + handler.getClass().getName());

            handler.processMessage(context);
        }

    }

    /**
     * close message handlers for the specified handler chain.
     */
    protected void closeHandlers (MessageHandlerChain chain) {

        /**
         * get the size of the chain.
         */
        int size = chain.size();

        /**
         * loop through the chain.
         */
        for ( int index = 0; index < size; index ++ ) {

            try {

                /**
                 * get the next message handler.
                 */
                MessageHandler handler =
                (MessageHandler) chain.getMessageHandlerAt(index);

                /**
                 * close the handler.
                 */
                handler.close();

            } catch (Throwable t) {
                logger.log(Level.WARNING, t.getMessage(), t);
            }
        }
    }

    /**
     * Create a soap fault message and set its error code and
     * error string as specified in the parameter.
     */
    public static SOAPMessage
    createSOAPFaultMessage (Throwable t, String faultCode, String faultString) {

        SOAPMessage soapFault = null;

        try {
            MessageFactory mf = MessageFactory.newInstance();

            soapFault = mf.createMessage();

            SOAPEnvelope env =
            soapFault.getSOAPPart().getEnvelope();

            SOAPBody body = env.getBody();
            SOAPFault faultElement = body.addFault();

            String soapNs = env.getElementName().getPrefix();
            String fcode = soapNs + ":" + faultCode;

            faultElement.setFaultCode( fcode );

            faultElement.setFaultString( faultString );
            
            Detail detail = faultElement.getDetail();
            if ( detail == null ) {
            	detail = faultElement.addDetail();
            }
            
            Name stname = MessageUtil.createJMSName("StackTrace");
            SOAPElement entryEle = detail.addDetailEntry(stname);
                      
            //get stack trace
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream (baos);
            t.printStackTrace(ps);
            
            ps.close();
            
            String trace = baos.toString("utf8");
         
            entryEle.setValue(trace);
            
            soapFault.saveChanges();

        } catch (Exception e) {
           e.printStackTrace();
        }

        return soapFault;
    }
}

