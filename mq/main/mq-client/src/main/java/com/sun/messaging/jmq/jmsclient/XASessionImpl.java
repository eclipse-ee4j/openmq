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

/*
 * @(#)XASessionImpl.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import javax.jms.*;
import javax.transaction.xa.XAResource;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;

/**
 * An XASession provides a regular Session which can be used to
 * create QueueReceivers, QueueSenders and QueueBrowsers (optional).
 *
 * <P>XASession extends the capability of Session by adding access to a JMS
 * provider's support for JTA (optional). This support takes the form of a
 * <CODE>javax.transaction.xa.XAResource</CODE> object. The functionality of
 * this object closely resembles that defined by the standard X/Open XA
 * Resource interface.
 *
 * <P>An application server controls the transactional assignment of an
 * XASession by obtaining its XAResource. It uses the XAResource to assign
 * the session to a transaction; prepare and commit work on the
 * transaction; etc.
 *
 * <P>An XAResource provides some fairly sophisticated facilities for
 * interleaving work on multiple transactions; recovering a list of
 * transactions in progress; etc. A JTA aware JMS provider must fully
 * implement this functionality. This could be done by using the services
 * of a database that supports XA or a JMS provider may choose to implement
 * this functionality from scratch.
 *
 * <P>A client of the application server is given what it thinks is a
 * regular JMS Session. Behind the scenes, the application server controls
 * the transaction management of the underlying XASession.
 *
 * @see         javax.jms.XASession javax.jms.XASession
 * @see         javax.jms.XAQueueSession javax.jms.XAQueueSession
 */

public class XASessionImpl extends UnifiedSessionImpl implements XASession {



    //private static boolean imqRecreateConsumerEnabled = false;
	
    //flag to indicate if RA encounter remote ack failed.
    private volatile boolean raRemoteAckFailedFlag = false;
    
    //for RA remote ack failed notify/re-create consumer sync usage.
    private Object remoteAckSyncObj = new Object();
    
    private XAResourceImpl xar;

    public XASessionImpl
            (ConnectionImpl connection, boolean transacted, int ackMode) throws JMSException {

        super (connection, transacted, ackMode);
        xar = new XAResourceImpl(this);
    }    
 
    public XASessionImpl
            (ConnectionImpl connection, boolean transacted,
             int ackMode, JMSRAManagedConnection mc) throws JMSException {

        super (connection, transacted, ackMode, mc);
        xar = new XAResourceImpl(this);
    }    
 
    /**
     * Return an XA resource to the caller.
     * 
     * @return an XA resource to the caller
    */
    public XAResource
    getXAResource() {
         return (XAResource) xar;
    }
 
    /**
     * Is the session in transacted mode?
     *
     * @return true
     *
     * @exception JMSException if JMS fails to return the transaction
     *                         mode due to internal error in JMS Provider.
     */
    public boolean
    getTransacted() throws JMSException {
        checkSessionState();
        if (xaTxnMode) {
            return  true;
        }
        return isTransacted;
    }
     
    /**
     * Throws TransactionInProgressException since it should not be called
     * for an XASession object.
     *
     * @exception TransactionInProgressException if method is called on
     *                         a XASession.
     *
     */
    public void
    commit() throws JMSException {
        if (xaTxnMode) {
            String error = AdministeredObject.cr.getKString(AdministeredObject.cr.X_COMMIT_ROLLBACK_XASESSION);
            throw new TransactionInProgressException(error);
        }
        super.commit();
    }
    
    
 
    @Override
	public void close() throws JMSException {
    	super.close();
        if (xar != null) xar.close();
	}

	/**
     * Throws TransactionInProgressException since it should not be called
     * for an XASession object.
     *
     * @exception TransactionInProgressException if method is called on
     *                         a XASession.
     *
     */
    public void
    rollback() throws JMSException {
        if (xaTxnMode) {
            String error = AdministeredObject.cr.getKString(AdministeredObject.cr.X_COMMIT_ROLLBACK_XASESSION);
            throw new TransactionInProgressException(error);
        }
        super.rollback();
    }
 
    /**
     * Get the queue session associated with this XAQueueSession.
     *  
     * @return the queue session object.
     *  
     * @exception JMSException if a JMS error occurs.
     */ 
    public Session
    getSession() throws JMSException {
        return (Session) this;
    }
    
    /**
     * Perform recreate consumer for RA.
     * 
     * Called by SessionReader@ra.MessageListener when detects that  remoteAckFailedFlag 
     * is set to true.
     * 
     * @throws JMSException
     */
    public void recreateConsumerForRA () {
    	//1. sync on some object -- objA.
    	//2. call this.recreateConsumers()
    	//3. reset remoteAckFailedFlag to false.
    	synchronized (this.remoteAckSyncObj) {
    	    
    		try {
    			
    			sessionLogger.log(java.util.logging.Level.FINEST, "Re-create message consumer for RA starting ...");
    			
    			recreateConsumers();
    			
    			sessionLogger.log(java.util.logging.Level.FINEST, "Re-create message consumer for RA finished ...");
    			
    			this.raRemoteAckFailedFlag = false;
    		} catch (Exception jmse) {
    			sessionLogger.log(java.util.logging.Level.SEVERE, jmse.getMessage(), jmse);
    		}
    	    
    	}
    	
    }
    
    /**
     * This is called from XAResourceForRA when a remote ack failed occurred.
     * 
     * @param rae the remote exception that contains consumer UID
     */
    public void notifyRemoteAcknowledgeException (RemoteAcknowledgeException rae) {
    	
    	//if (imqRecreateConsumerEnabled == false) {
    	//	return;
    	//}
    	
    	//1. sync on some object -- objA.
    	//2. check if remoteAckFailedFlag is set.  If already set, return. 
    	//3. If not, check if the current CID in session matches the one in rae.
    	//4. If yes, set the flag to true, else return.
    	
    	if ( this.raRemoteAckFailedFlag) {
    		return;
    	}
    	
    	synchronized (this.remoteAckSyncObj) {
    		
    		//if already set, simply return.
    		if (this.raRemoteAckFailedFlag) {
    		    return;	
    		}
    		
    		/**
    		 * This is to prevent multiple re-create consumers.  
    		 * After consumer(s) are re-created, the match will return false.
    		 */
    		boolean matched = matchConsumerIDs(rae, consumers, sessionLogger);
    		
    		if (matched) {
    			this.raRemoteAckFailedFlag = true;
    		}
    	}
    }
    
    public boolean isRemoteAckFailed() {
    	return this.raRemoteAckFailedFlag;
    }
    
    public void stopSession() throws JMSException {
    	super.stopSession();
    }
    
    public void logException (Exception ex) {
    	sessionLogger.log (java.util.logging.Level.SEVERE , ex.getMessage(), ex);
    }

}
 
