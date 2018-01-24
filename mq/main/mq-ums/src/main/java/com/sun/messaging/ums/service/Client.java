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


import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class Client {

    private String sid = null;
    private CachedConnection cc = null;
    private Session session = null;
    private MessageProducer producer = null;
    private MessageConsumer consumer = null;
    private long timestamp = 0;
    
    private Logger logger = UMSServiceImpl.logger;
    
    private Object lock;
    private boolean sweeped = false;
    //XXX use this for sweep
    private boolean inuse = false;
    private CachedConnectionPool ccpool = null;
    
    //do not cache me if set to true. default set to false.
    private boolean noCache = false;
    
    private boolean transacted = false;
    
    /**
     * the destination name that current consumer is associated with.
     * Each client consumer can only associate with one destination at a 
     * time. 
     */
    private String consumerOnDestName = null;

    public Client(String sid, CachedConnectionPool ccPool, boolean transacted)
            throws JMSException {

        this.sid = sid;

        this.ccpool = ccPool;
        
        this.transacted = transacted;

        this.cc = ccPool.getCachedConnection();

        cc.add(this);
        
        this.lock = new Object();

        if (UMSServiceImpl.debug) {
            logger.info ("client created: " + sid + ", transacted=" + transacted);
        }
    }

    public String getId() {
        return this.sid;
    }
    
    public Object getLock() {
        return this.lock;
    }
    
    /**
     * set to true if no cache
     * @param flag
     */
    public void setNoCache (boolean flag) {
        this.noCache = flag;
    }
    
    /**
     * get if no cache mode is true
     * @return
     */
    public boolean getNoCache() {
        return this.noCache;
    }
    
    public void setTransacted (boolean flag) {
        this.transacted = flag;
    }
    
    public boolean getTransacted () {
        return this.transacted;
    }

    public synchronized Session getSession() throws JMSException {

        if (session == null) {
            session = cc.getConnection().createSession(transacted, Session.AUTO_ACKNOWLEDGE);
        }

        this.setTimestamp();

        return session;
    }

    public synchronized MessageProducer getProducer() throws JMSException {

        if (producer == null) {
            getSession();
            producer = session.createProducer(null);
        }

        return producer;
    }

    public synchronized MessageConsumer getConsumer(boolean isTopic, String destName) throws JMSException {

        if (consumer == null) {
            this.createConsumer(isTopic, destName);
        } else {
            
            if (UMSServiceImpl.debug) {
                logger.info("consumer in cache for clientId ... " + sid + ", on dest: " + destName);
            }
            
            if (destName.equals(this.consumerOnDestName) == false) {
            //app tries ti receive on diff dest.
                this.recreateConsumer(isTopic, destName);
            }
        }

        return consumer;
    }

    private synchronized void recreateConsumer(boolean isTopic, String destName) throws JMSException {
        this.consumer.close();
        this.createConsumer (isTopic, destName);
    }

    private synchronized void createConsumer(boolean isTopic, String destName) throws JMSException {
        
        //set current associated dest name
        this.consumerOnDestName = destName;

        Destination dest = null;

        getSession();

        if (UMSServiceImpl.debug) {
            logger.info ("got session ..." + session.toString());
        }
        
        if (isTopic) {
            //debugLog("Creating topic: " + destName);
            dest = session.createTopic(destName);
        } else {
            //debugLog("Creating queue: " + destName);
            dest = session.createQueue(destName);
        }

        //debugLog("Creating consumer on dest: " + destName);
        consumer = session.createConsumer(dest);

        if (UMSServiceImpl.debug) {
            logger.info ("created consumer for clientId=" + sid + ", dest=" + dest + ", isTopic=" + isTopic);
        }
    }

    public synchronized void setTimestamp() {
        this.timestamp = System.currentTimeMillis();
        this.sweeped = false;
    }

    public synchronized long getTimestamp() {
        return this.timestamp;
    }

    public synchronized boolean getSweeped() {
        return this.sweeped;
    }

    public synchronized void setSweeped(boolean flag) {
        this.sweeped = flag;
    }

    public synchronized void setInuse(boolean flag) {
        this.inuse = flag;
    }

    public synchronized boolean getInUse() {
        return this.inuse;
    }

    /**
     * XXX close
     */
    public synchronized void close() {

        try {
            
            //remove uuid from authenticator
            //ccpool.removeSid(this.clientId);
            
            //decrease semaphore
            ccpool.releaseConnection(cc);
            
            //remove myself from cc table
            this.cc.remove(this);
            
            //close session
            if (session != null) {
                this.session.close();
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }
    
    public String toString() {
        return "Name=" + this.getClass().getName() + ", ClientId=" + this.sid + ", inuse=" + this.inuse + ", timestamp=" + this.timestamp;
    }
}
