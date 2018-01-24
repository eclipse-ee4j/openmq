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

import com.sun.messaging.ums.common.Constants;
import com.sun.messaging.xml.MessageTransformer;
import com.sun.messaging.ums.common.MessageUtil;
import com.sun.messaging.ums.simple.SimpleMessage;
import java.util.Map;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import javax.jms.Session;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

public class SendServiceImpl implements SendService {

    //private Lock lock = null;
    private Properties props = null;
    private ClientPool cache = null;
    //cache sweeper
    private CacheSweeper sweeper = null;
    private Logger logger = UMSServiceImpl.logger;
    
    private static final String SERVICE_NAME = "SEND_SERVICE";
    
    private String myName = null;
    
    private String provider = null;

    public SendServiceImpl(String provider, ClientPool cache, CacheSweeper sweeper, Properties p) throws JMSException {
        
        this.provider = provider;
        
        this.sweeper = sweeper;
        
        this.myName = provider + "_" + SERVICE_NAME;
        
        this.props = p;

        //lock = new Lock();

        //cache = new JMSCache(MY_NAME, props, lock, logger);
        //cache = new ClientPool(provider, SERVICE_NAME, props, lock);
       
        //add my cache to the sweeper
        //sweeper.addClientPool(cache);
        this.cache = cache;
    }
    
    //public String authenticate (String user, String password) throws JMSException {
    //    return cache.authenticate(user, password);
    //}
    
    //public void authenticateUUID (String clientId) throws JMSException {
    //    cache.authenticateUUID (clientId);
    //}
       
    public void send(SOAPMessage sm) throws JMSException {

        Client client = null;
        Message message = null;
        
        String user = null;
        String pass = null;

        try {
            
            /**
            String clientId = MessageUtil.getServiceClientId(sm);

            if (clientId == null) {
                user = MessageUtil.getServiceAttribute(sm, Constants.USER);
                pass = MessageUtil.getServiceAttribute(sm, Constants.PASSWORD);
            }
            
            client = cache.getClient(clientId, user, pass);
            **/
            
            client = cache.getClient(sm);
            
            //Object syncObj = lock.getLock(client.getId());
            Object syncObj = client.getLock();
            
            if (UMSServiceImpl.debug) {
                logger.info("*** SendServiceImpl sending message: " + sm);
            }
            
            synchronized (syncObj) {

                //client = cache.getClient(clientId);

                Session session = client.getSession();

                String destName = MessageUtil.getServiceDestinationName(sm);
                boolean isTopic = MessageUtil.isServiceTopicDomain(sm);

                Destination dest = cache.getJMSDestination(destName, isTopic);
                
                //XXX remove message header element
                MessageUtil.removeMessageHeaderElement(sm);
                
                if (UMSServiceImpl.debug) {
                    logger.info("*** SendServiceImpl sending message: " + sm);
                }
                //sm.writeTo(System.out);
                
                message = MessageTransformer.SOAPMessageIntoJMSMessage(sm, session);

                MessageProducer producer = client.getProducer();

                producer.send(dest, message);
            }

            if (UMSServiceImpl.debug) {
                logger.info ("*** SendServiceImpl sent message: " + message);
            }
            
        } catch (Exception ex) {

            if (ex instanceof JMSException) {
                throw (JMSException) ex;
            } else {
                JMSException jmse = new JMSException(ex.getMessage());
                jmse.setLinkedException(ex);
                throw jmse;
            }

        } finally {
            cache.returnClient(client);
        }
    }

    /**
     * Send a Text message to MQ.
     * 
     * @param clientId
     * @param isTopic
     * @param destName
     * @param text
     * @throws javax.jms.JMSException
     */
    public void sendText(String sid, boolean isTopic, String destName, String text, Map map) throws JMSException {

        Client client = null;
        Message message = null;

        String user = null;
        String pass = null;

        try {

            client = cache.getClient(sid, map); 
            
            //Object syncObj = lock.getLock(client.getId());
            Object syncObj = client.getLock();
            
            if (UMSServiceImpl.debug) {
                logger.info("*** SendServiceImpl sending simple message: sid = " + sid + ", text=" + text);
            }
            
            synchronized (syncObj) {

                //client = cache.getClient(clientId);

                Session session = client.getSession();

                Destination dest = cache.getJMSDestination(destName, isTopic);

                message = session.createTextMessage(text);

                MessageProducer producer = client.getProducer();

                producer.send(dest, message);
            }

            if (UMSServiceImpl.debug) {
                logger.info("*** SendServiceImpl sent text message... ");
            }
            
        } catch (Exception ex) {
            
            logger.log(Level.WARNING, ex.getMessage(), ex);

            if (ex instanceof JMSException) {
                throw (JMSException) ex;
            } else {
                JMSException jmse = new JMSException(ex.getMessage());
                jmse.setLinkedException(ex);
                throw jmse;
            }

        } finally {
            cache.returnClient(client);
        }
    }
    
    public void commit (SimpleMessage sm) throws JMSException {
        
        String sid = sm.getMessageProperty(Constants.CLIENT_ID);
        Map map = sm.getMessageProperties();
        
        if (sid == null) {
            throw new JMSException ("Cannot commit a transaction because sid is null.");
        }
        
        Client client = cache.getClient(sid, map);
        
        this.commit(client);
    }
    
     public void rollback (SimpleMessage sm) throws JMSException {
        
        String sid = sm.getMessageProperty(Constants.CLIENT_ID);
        Map map = sm.getMessageProperties();
        
         if (sid == null) {
             throw new JMSException("Cannot rollback a transaction because sid is null.");
         }
        
        Client client = cache.getClient(sid, map);
        
        this.rollback (client);
    }
    
    public void commit (SOAPMessage sm) throws JMSException {
        
        try {
            
            String sid = MessageUtil.getServiceClientId(sm);
            
            if (sid == null) {
                throw new JMSException("Cannot commit a transaction because sid is null.");
            }
        
            Client client = cache.getClient(sm);
        
            this.commit(client);
            
        } catch (SOAPException soape) {
            JMSException jmse = new JMSException (soape.getMessage());
            
            jmse.setLinkedException (soape);
            throw jmse;
        }
    }
    
    public void rollback(SOAPMessage sm) throws JMSException {

        try {


            String sid = MessageUtil.getServiceClientId(sm);

            if (sid == null) {
                throw new JMSException("Cannot rollback a transaction because sid is null.");
            }

            Client client = cache.getClient(sm);

            this.rollback(client);
        } catch (SOAPException soape) {
            JMSException jmse = new JMSException(soape.getMessage());

            jmse.setLinkedException(soape);
            throw jmse;
        }
    }
    
     private void commit (Client client) throws JMSException {

        try {
           
            //client = cache.getClient(sm);
            
            //Object syncObj = lock.getLock(client.getId());
            Object syncObj = client.getLock();
            
            if (UMSServiceImpl.debug) {
                logger.info("*** Commiting transaction, sid = " + client.getId());
            }
            
            synchronized (syncObj) {

                Session session = client.getSession();
                session.commit();
            }
               
            if (UMSServiceImpl.debug) {
                logger.info ("*** Transaction committed. sid=" + client.getId());
            }
            
        } catch (Exception ex) {

            if (ex instanceof JMSException) {
                throw (JMSException) ex;
            } else {
                JMSException jmse = new JMSException(ex.getMessage());
                jmse.setLinkedException(ex);
                throw jmse;
            }

        } finally {
            cache.returnClient(client);
        }
    }
     
    private void rollback (Client client) throws JMSException {

        try {
           
            //client = cache.getClient(sm);
            
            //Object syncObj = lock.getLock(client.getId());
            Object syncObj = client.getLock();
            
            if (UMSServiceImpl.debug) {
                logger.info("*** rolling back transaction, sid = " + client.getId());
            }
            
            synchronized (syncObj) {

                Session session = client.getSession();
                session.rollback();
            }
               
            if (UMSServiceImpl.debug) {
                logger.info ("*** Transaction rolled back. sid=" + client.getId());
            }
            
        } catch (Exception ex) {

            if (ex instanceof JMSException) {
                throw (JMSException) ex;
            } else {
                JMSException jmse = new JMSException(ex.getMessage());
                jmse.setLinkedException(ex);
                throw jmse;
            }

        } finally {
            cache.returnClient(client);
        }
    }

    public void close() {
        
        try {
            //sweeper.removeClientPool(cache);
            cache.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        
    }

}
