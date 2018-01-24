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
 *  @(#)XAResourceImpl.java	1.16 03/14/08
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.*;
import javax.transaction.xa.*;

import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
  * JMQ implementation class for XAResource
  *
  * <p>The XAResource interface is a Java mapping of the industry standard
  * XA interface based on the X/Open CAE Specification (Distributed
  * Transaction Processing: The XA Specification).
  *
  * <p>The XA interface defines the contract between a Resource Manager
  * and a Transaction Manager in a distributed transaction processing
  * (DTP) environment. A JDBC driver or a JMS provider implements
  * this interface to support the association between a global transaction
  * and a database or message service connection.
  *
  * <p>The XAResource interface can be supported by any transactional
  * resource that is intended to be used by application programs in an
  * environment where transactions are controlled by an external
  * transaction manager. An example of such a resource is a database
  * management system. An application may access data through multiple
  * database connections. Each database connection is enlisted with
  * the transaction manager as a transactional resource. The transaction
  * manager obtains an XAResource for each connection participating
  * in a global transaction. The transaction manager uses the
  * <code>start</code> method
  * to associate the global transaction with the resource, and it uses the
  * <code>end</code> method to disassociate the transaction from
  * the resource. The resource
  * manager is responsible for associating the global transaction to all
  * work performed on its data between the start and end method invocations.
  *
  * <p>At transaction commit time, the resource managers are informed by
  * the transaction manager to prepare, commit, or rollback a transaction
  * according to the two-phase commit protocol.</p>
  *
  * @see javax.transaction.xa.XAResource
  */

public class XAResourceImpl implements XAResource, XAResourceForJMQ {

    private SessionImpl _session;
    private ConnectionImpl _connection;
    private Transaction _transaction = null;
    //Use Unique ID from broker
    private long resourceManagerId = 0L;
    private int transactionTimeout = 0; //transactions do not timeout
    
    //HACC -- transaction state table
    private final static Hashtable xaTable = new Hashtable();
    
    //transaction state (xaState) stored in the xaTable
    public static final Integer XA_START = Integer.valueOf(Transaction.TRANSACTION_STARTED);
    public static final Integer XA_END = Integer.valueOf(Transaction.TRANSACTION_ENDED);
    public static final Integer XA_PREPARE = Integer.valueOf(Transaction.TRANSACTION_PREPARED);
    public static final Integer XA_ROLLBACK_ONLY = Integer.valueOf(Transaction.TRANSACTION_ROLLBACK_ONLY);
    
    /** 
     * Possible states of this XAResource
     */
    public static final int CREATED    = 0; // after first creation, or after commit() or rollback()
    public static final int STARTED    = 1; // after start() called
    public static final int FAILED     = 2; // after end(fail) called
    public static final int INCOMPLETE = 3; // after end(suspend) called
    public static final int COMPLETE   = 4; // after end (success) called
    public static final int PREPARED   = 5; // after prepare() called
    
    /**
     * State of this XAresource
     */
    private int resourceState = CREATED;
    
    //use this property to turn off xa transaction tracking
    public static final boolean turnOffXATracking = Boolean.getBoolean("imq.ra.turnOffXATracking");
    //set to true by default - track xa transaction state
    public static final boolean XATracking = !turnOffXATracking;
    //end HACC

    //cache last self rolled back txn during prepare() failure
    private static Map<XAResourceImpl, JMQXid> lastInternalRBCache = 
        Collections.synchronizedMap(new LinkedHashMap<XAResourceImpl, JMQXid>());

    //true if this XAResource has put entry into lastInternalRBCache
    private boolean lastInternalRB = false;

    private  ConnectionConsumerImpl connectionConsumer = null;
    
    JMQXid currentJMQXid = null;
    
    /* Loggers */
    private static transient final String _className = "com.sun.messaging.jmq.jmsclient.XAResourceImpl";
    private static transient final String _lgrName = "com.sun.messaging.jmq.jmsclient.XAResourceImpl";
    private static transient final Logger _logger = Logger.getLogger(_lgrName);
    private static transient final String _lgrPrefix = "XAResourceImpl: ";
    
    public XAResourceImpl(SessionImpl session) throws JMSException {
        this._session = session;
        this._connection = session.connection;
        resourceManagerId = _connection.protocolHandler.generateUID();
        
        if (Debug.debug) {
        	Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:new:RMId="+resourceManagerId);
        }
    }

    protected void setConnectionConsumer(ConnectionConsumerImpl cc) {
        connectionConsumer = cc; 
    }

    protected ConnectionConsumerImpl getConnectionConsumer() {
        return connectionConsumer; 
    }

    /**
     * Commits the global transaction specified by xid.
     *
     * @param foreignXid A global transaction identifier
     *
     * @param onePhase If true, the resource manager should use a one-phase
     * commit protocol to commit the work done on behalf of xid.
     *
     * @exception XAException An error has occurred. Possible XAExceptions
     * are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     * XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     * <P>If the resource manager did not commit the transaction and the
     *  parameter onePhase is set to true, the resource manager may throw
     *  one of the XA_RB* exceptions. Upon return, the resource manager has
     *  rolled back the branch's work and has released all held resources.
     */
    public void commit(Xid foreignXid, boolean onePhase) throws XAException {
    	
        boolean insyncstate = false;
        boolean checkrollback = false;
        boolean rbrollback = false;
        Exception rbrollbackex = null;

        if (_logger.isLoggable(Level.FINE)){
            _logger.fine(_lgrPrefix+"("+this.hashCode()+") Commit  "+
                         printXid(foreignXid)+" (onePhase="+onePhase+")");
        }
    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        
        if (Debug.debug) {
            Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:commit:onePhase="+onePhase+"\txid=\n"+jmqXid.toString());
        }
        
        // HACC
        if (this._session.isRollbackOnly) {
            Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:forcing Rollback due to:" + this._session.rollbackCause.getMessage());
            //Debug.printStackTrace(this._session.rollbackCause);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(this._session.rollbackCause);
            throw xae;
        }
        
        try {
            try {
                //Re-open if needed
                //_connection.openConnection(true);
            	
                //Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            } catch (Exception oce) {
                //XXX:RFE:Configuration needed
                //For now-retry once after a sec
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}

                //_connection.openConnection(true);
                //Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            }
            if (_transaction == null) {
                if (Debug.debug) {
                    Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:commit:using 0 as txnID");
                }
            	
                if (_connection.isConnectedToHABroker) {
                    HACommit(foreignXid, jmqXid, onePhase, insyncstate);
                } else {
                    try {
                        _connection.getProtocolHandler().commit(0L, 
                            (onePhase ? XAResource.TMONEPHASE : XAResource.TMNOFLAGS), jmqXid);
                    } catch (JMSException e) {
                        if (onePhase) {
                            checkrollback = true;
                        }
                        throw e;
                    }
                }
            } else {
                if (Debug.debug) {
                    Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:commit:using real txnID");
                }
            	
                //Setup protocol Handler
                _transaction.setProtocolHandler(_connection.getProtocolHandler());

                //Need to ack msgs if this session has not been closed.
                if (!_session.isClosed) {
                    //set sync flag
                    _session.setInSyncState();
                    insyncstate = true;
                    //ack all messages received in this session
                    _session.receiveCommit();
                }
                //Perform XA commit
                if (this._connection.isConnectedToHABroker) {
                    this.HACommit(foreignXid, jmqXid, onePhase, insyncstate);
                    _session.clearUnackedMessageQ();
                } else {
                    try {
                        _transaction.commitXATransaction(jmqXid, onePhase);
                    } catch (JMSException e) {
                        if (onePhase) {
                            checkrollback = true;
                        }
                        throw e;
                    }
                }
            }
            //_connection.closeConnection();
            //Bug6664278 - must synced
            _connection.closeConnectionFromRA();
            return;
        } catch (Exception jmse) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:commitXAException");
            Debug.printStackTrace(jmse);
            if (jmse instanceof XAException) {
                throw (XAException)jmse;
            }
            if (jmse instanceof RemoteAcknowledgeException) {
                if (checkrollback) {
                    rbrollbackex = jmse;
                    rbrollback = true;
                }
            }
            if (!rbrollback && checkrollback && (jmse instanceof JMSException)) {
                if (((JMSException)jmse).getErrorCode().equals(
                    ClientResources.X_SERVER_ERROR)) {
                    Exception e1 = ((JMSException)jmse).getLinkedException();
                    if (e1 != null && (e1 instanceof JMSException) &&
                        !((JMSException)e1).getErrorCode().equals(Status.getString(Status.NOT_FOUND))) {
                        SessionImpl.sessionLogger.log(Level.WARNING,
                        "Exception on 1-phase commit transaction "+jmqXid+", will rollback", jmse); 
                        rbrollbackex = jmse;
                        rbrollback = true;
                    }
                }
            }
            if (!rbrollback) {
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                xae.initCause(jmse);
                throw xae;
            }
        } finally {
            if (!rbrollback) {

            //finish up this resource and any others joined to it in this transaction
            boolean throwExceptionIfNotFound = false;
            XAResourceImpl[] resources = XAResourceMap.getXAResources(jmqXid,throwExceptionIfNotFound);
            for (int i = 0; i < resources.length; i++) {
                XAResourceImpl xari = resources[i];
                xari.clearTransactionInfo();
                xari.finishCommit();
            }
            XAResourceMap.unregister(jmqXid);

            } //!rbrollback

            if (insyncstate) {
                _session.releaseInSyncState();
            }
        }
        if (rbrollback) {
            XAException xae;
            try {
                rollback(foreignXid);
                lastInternalRBCache.put(this, jmqXid);
                lastInternalRB = true;
                xae = new XAException(XAException.XA_RBROLLBACK);
                xae.initCause(rbrollbackex);
            } catch (Throwable t) {
                SessionImpl.sessionLogger.log(Level.SEVERE,
                "Exception on rollback transaction "+jmqXid+" after 1-phase-commit failure", t); 
                xae = new XAException(XAException.XAER_RMFAIL);
                xae.initCause(rbrollbackex);
            }
            throw xae;
        }
    }

    protected void close(){
    	
    	if (currentJMQXid!=null) {
    		SessionImpl.sessionLogger.log(Level.INFO, "Closing XA session with a transaction pending");
    		
    		// unregister resource to avoid memory leak
    		XAResourceMap.unregisterResource(this, currentJMQXid);
                clearTransactionInfo();
    		currentJMQXid=null;
    	}
        connectionConsumer = null;
    }
    
    protected void finishCommit(){

        _session.switchOffXATransaction();
        
        currentJMQXid=null;
        connectionConsumer = null;
    }
    
    protected void finishRollback(){
    	//HACC -- must clear this flag.
    	_session.failoverOccurred = false;
    	_session.switchOffXATransaction();
    
    	//HACC
    	_session.isRollbackOnly = false;
    	_session.rollbackCause = null;
    	
    	currentJMQXid=null;
        connectionConsumer = null;
    }
        
    /**
     * Ends the work performed on behalf of a transaction branch.
     * The resource manager disassociates the XA resource from the
     * transaction branch specified and lets the transaction
     * complete.
     *
     * <p>If TMSUSPEND is specified in the flags, the transaction branch
     * is temporarily suspended in an incomplete state. The transaction
     * context is in a suspended state and must be resumed via the
     * <code>start</code> method with TMRESUME specified.</p>
     *
     * <p>If TMFAIL is specified, the portion of work has failed.
     * The resource manager may mark the transaction as rollback-only</p>
     *
     * <p>If TMSUCCESS is specified, the portion of work has completed
     * successfully.</p>
     *
     * @param foreignXid A global transaction identifier that is the same as
     * the identifier used previously in the <code>start</code> method.
     *
     * @param flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
     *
     * @exception XAException An error has occurred. Possible XAException
     * values are XAER_RMERR, XAER_RMFAILED, XAER_NOTA, XAER_INVAL,
     * XAER_PROTO, or XA_RB*.
     */
    public void end(Xid foreignXid, int flags) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrPrefix+"XAResourceImpl ("+this.hashCode()+") End     "+printXid(foreignXid)+printFlags(flags));
        }
    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
            
        if (_connection._isClosed()) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:end:XAException");
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            throw xae;
        }
        
        // update the resource state
        if (isFail(flags)){
            resourceState=FAILED;
        } else if (isSuspend(flags)){
            resourceState=INCOMPLETE;
        } else {
            resourceState=COMPLETE;
        }
            
        // now decide based on the resource state whether to send a real
        // END packet, a noop END packet or to ignore the END packet altogether.
        if (resourceState==COMPLETE) {
            // This XAResource is complete. Send a real END packet if all
            // other resources joined to this txn are complete, otherwise
            // send a noop END packet to ensure that work associated with
            // this XAResource has completed on the broker. See bug 12364646.
            boolean allComplete = true;
            XAResourceImpl[] resources = XAResourceMap.getXAResources(jmqXid,false);
            for (int i = 0; i < resources.length; i++) {
                XAResourceImpl xari = resources[i];
                if (!xari.isComplete()){
                    allComplete = false;
                }
            }

            if (allComplete){
                // All resources complete.  Send real END packet.
                sendEndToBroker(flags, false, jmqXid);
            } else {
                // One or more resources are not complete. Send a noop END
                // packet to the broker.
                sendEndToBroker(flags, true, jmqXid);
            }
        } else if (resourceState==FAILED){
            // This resource has failed.  Send a real END packet regardless
            // of the state of any other joined resources.
            sendEndToBroker(flags, false, jmqXid);
        } else if (resourceState==INCOMPLETE){
            // Don't send the END to the broker. See Glassfish issue 7118.
        }
    }
    
    private void sendEndToBroker(int flags, boolean jmqnoop, JMQXid jmqXid) throws XAException {
        try {
            //System.out.println("MQRA:XAR4RA:end:sending 0L:tid="+transactionID+" xid="+jmqXid.toString());
            _connection.getProtocolHandler().endTransaction(0L, jmqnoop, flags, jmqXid);
        } catch (JMSException jmse) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:end:XAException");
            Debug.printStackTrace(jmse);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);
            throw xae;
        }
    }

    /**
     * Tells the resource manager to forget about a heuristically
     * completed transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
     * XAER_PROTO.
     */
    public void forget(Xid foreignXid) throws XAException {
        //iMQ does not support heuristically completed transaction branches
        //This is a NOP
        //convert to jmq xid
        //JMQXid jmqXid = new JMQXid(foreignXid);
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:forget:txid=\n"+jmqXid.toString());
    	
    	// Despite the above, clean up currentJMQXid and the XAResourceMap 
        JMQXid jmqXid = new JMQXid(foreignXid);        
    	XAResourceMap.unregister(jmqXid);
        if (currentJMQXid!=null){
        	if (currentJMQXid.equals(jmqXid)){
        		currentJMQXid=null;
                connectionConsumer = null;
                clearTransactionInfo();
        	}
        }
    }

    /**
     * Obtains the current transaction timeout value set for this
     * XAResource instance. If <CODE>XAResource.setTransactionTimeout</CODE>
     * was not used prior to invoking this method, the return value
     * is the default timeout set for the resource manager; otherwise,
     * the value used in the previous <CODE>setTransactionTimeout</CODE>
     * call is returned.
     *
     * @return the transaction timeout value in seconds.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     */
    public int getTransactionTimeout() throws XAException {
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:getTransactionTimeout");
        return transactionTimeout;
    }

    /**
     * This method is called to determine if the resource manager
     * instance represented by the target object is the same as the
     * resource manager instance represented by the parameter <i>foreignXaRes</i>.
     *
     * @param foreignXaRes An XAResource object whose resource manager instance
     *      is to be compared with the resource manager instance of the
     *      target object.
     *
     * @return <i>true</i> if it's the same RM instance; otherwise
     *       <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are XAER_RMERR and XAER_RMFAIL.
     *
     */
    public boolean isSameRM(XAResource foreignXaRes) throws XAException {
    	
    	// don't allow a XAResourceImpl to be joined to a XAResourceForMC or a XAResourceForRA
    	// (even though they all implement XAResourceForJMQ)
    	// as this would imply joining of JMSJCA and JMSRA resources, which has not been tested
       	
    	if ((foreignXaRes instanceof XAResourceImpl)) {
    		return isSameJMQRM((XAResourceForJMQ)foreignXaRes);
    	} else {
        	return false;
    	}

    }
    
    /**
     * Return whether this XAResourceForJMQ and the specified XAResourceForJMQ
     * represent the same resource manager instance.
     * 
     * This is determined by checking whether the two resources 
     * have the same brokerSessionID
     *
     * @param xaResource XAResourceForJMQ  
     * @return true if same RM instance, otherwise false.
     */  
    public boolean isSameJMQRM(XAResourceForJMQ xaResource) {
        
    	boolean result;
    	if ((getBrokerSessionID()!=0) && (getBrokerSessionID()==xaResource.getBrokerSessionID())){
    		result= true;
    	} else {
    		result=false;
    	}
    	
    	// logging
    	if (SessionImpl.sessionLogger.isLoggable(Level.FINE)){
    		long myBrokerSessionID = getBrokerSessionID();
    		long otherBrokerSessionID = xaResource.getBrokerSessionID();
  			SessionImpl.sessionLogger.log(Level.FINE, "myBrokerSessionID="+myBrokerSessionID+" otherBrokerSessionID="+otherBrokerSessionID+" isSameRM()="+result);
    	}
    	
    	return result;
 
    }
    
    /**
     * Return the brokerSessionID of this object's connection
     * @return
     */
    public long getBrokerSessionID(){
    	return _connection.getBrokerSessionID();
    }

    /**
     * Ask the resource manager to prepare for a transaction commit
     * of the transaction specified in xid.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred. Possible exception
     * values are: XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL,
     * or XAER_PROTO.
     *
     * @return A value indicating the resource manager's vote on the
     * outcome of the transaction. The possible values are: XA_RDONLY
     * or XA_OK. If the resource manager wants to roll back the
     * transaction, it should do so by raising an appropriate XAException
     * in the prepare method.
     */
    private int prepare(Xid foreignXid, boolean onePhase, boolean insyncstate)
    throws XAException {
        
    	if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrPrefix+"XAResourceImpl ("+this.hashCode()+") Prepare     "+printXid(foreignXid));
        }
    	
        //JMS does not do RDONLY transactions - right?
        int result = XA_OK;
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        
        if (Debug.debug) {
        	Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:txid=\n"+jmqXid.toString());
        }
        
        try {

            // HACC
            if (this._session.isRollbackOnly) {
                Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:forcing Rollback due to:" + this._session.rollbackCause.getMessage());
                //Debug.printStackTrace(this._session.rollbackCause);
                if (connectionConsumer != null &&
                    this._session.rollbackCause instanceof  RemoteAcknowledgeException) {
                    throw this._session.rollbackCause;
                }
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                xae.initCause(this._session.rollbackCause);
                throw xae;
		    }
        
            try {
                //Re-open if needed
                //_connection.openConnection(true);
            	//Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            } catch (Exception oce) {
                //XXX:RFE:Configuration needed
                //For now-retry once after a sec
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
                //_connection.openConnection(true);
                //Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            }
            if (_transaction == null) {
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:using 0 as txnID");
                _connection.getProtocolHandler().prepare(0L, jmqXid, onePhase);
            } else {
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:using real txnID");
                //Setup protocol Handler
                _transaction.setProtocolHandler(_connection.getProtocolHandler());
                //Perform XA prepare
                if ( onePhase ) {
                	_connection.getProtocolHandler().prepare(0L, jmqXid, onePhase);
                } else {
                	_transaction.prepareXATransaction(jmqXid);
                }
            }
            //_connection.closeConnection();
            //Bug6664278 - must synced
            _connection.closeConnectionFromRA();
        } catch (Throwable jmse) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepareXAException");
            Debug.printStackTrace(jmse);

            if (jmse instanceof XAException) throw (XAException)jmse;

            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);

            if (jmse instanceof RemoteAcknowledgeException) {
                if (connectionConsumer != null) {
                    ConnectionConsumerImpl cc = connectionConsumer;
                    if (!cc.canRecreate()) throw xae; 
                    try {
                        this.rollback(foreignXid);
                        xae = new XAException(XAException.XA_RBROLLBACK);
                        xae.initCause(jmse);
                        lastInternalRBCache.put(this, jmqXid);
                        lastInternalRB = true;
                    } catch (Throwable t) {
                       SessionImpl.sessionLogger.log(Level.SEVERE,
                           "Exception on rollback transaction "+jmqXid+" after prepared failed with remote exception", t); 
                    } finally { 
                        cc.notifyRecreation((RemoteAcknowledgeException)jmse);
                    }
                } else if (_session.isRemoteException((RemoteAcknowledgeException)jmse)) {
                    try {
                        this.rollback(foreignXid);
                        xae = new XAException(XAException.XA_RBROLLBACK);
                        xae.initCause(jmse);
                        lastInternalRBCache.put(this, jmqXid);
                        lastInternalRB = true;
                    } catch (Throwable t) {
                        SessionImpl.sessionLogger.log(Level.SEVERE,
                        "Exception on rollback transaction "+jmqXid+" after prepare failed with remote exception", t); 
                    }
                    if (!insyncstate) {
                        try {
                            _session.setInSyncState();
                        } catch (Throwable t) {
                            SessionImpl.sessionLogger.log(Level.SEVERE,
                            "Exception on setting sync state after prepare "+jmqXid+" failed with remote exception", t); 
                            throw xae;
                        }
                    }
                    try {
                        _session.recreateConsumers(true);
                    } catch (Throwable t) {
                        SessionImpl.sessionLogger.log(Level.SEVERE,
                        "Exception on recreating consumers after prepare "+jmqXid+" failed with remote exception", t); 
                        throw xae; 
                    } finally {
                        if (!insyncstate) {
                            _session.releaseInSyncState();
                        }
                    }
                }
            } else if (jmse instanceof TransactionPrepareStateFAILEDException) {
                if (onePhase) {
                    if (_transaction == null || _session.isClosed ||
                        connectionConsumer != null) {
                        try {
            		    _connection.getProtocolHandler().rollback(0L, jmqXid,
                                                       connectionConsumer != null);
                            xae = new XAException(XAException.XA_RBROLLBACK);
                            xae.initCause(jmse);
                            lastInternalRBCache.put(this, jmqXid);
                            lastInternalRB = true;
                        } catch (Throwable t) {
                            SessionImpl.sessionLogger.log(Level.SEVERE,
                            "Exception on rollback after TransactionPrepareStateFAILEDException "+jmqXid, t); 
                            throw xae;
                        }
                    } else {
                        if (!insyncstate) {
                            try {
                                _session.setInSyncState();
                            } catch (Throwable t) {
                                SessionImpl.sessionLogger.log(Level.SEVERE,
                                "Exception on setting sync state on TransactionPrepareStateFAILEDException "+jmqXid, t); 
                                throw xae;
                            }
                        }
                        try {
                            _session.rollbackAfterReceiveCommit(jmqXid);
                            xae = new XAException(XAException.XA_RBROLLBACK);
                            xae.initCause(jmse);
                            lastInternalRBCache.put(this, jmqXid);
                            lastInternalRB = true;
                        } catch (Throwable t) {
                            SessionImpl.sessionLogger.log(Level.SEVERE,
                            "Exception on rollback after TransactionPrepareStateFAILEDException "+jmqXid, t); 
                            throw xae; 
                        } finally {
                            if (!insyncstate) {
                                _session.releaseInSyncState();
                            }
                        }
                    }

                } else {
                    xae = new XAException(XAException.XAER_RMERR);
                    xae.initCause(jmse);
                }
            }

            throw xae;
        } 
        
        // update the resource state
        resourceState=PREPARED;
        
        return result;
    }

    /**
     * Obtains a list of prepared transaction branches from a resource
     * manager. The transaction manager calls this method during recovery
     * to obtain the list of transaction branches that are currently in
     * prepared or heuristically completed states.
     *
     * @param flags One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMNOFLAGS
     * must be used when no other flags are set in the parameter.
     *
     * @exception XAException An error has occurred. Possible values are
     * XAER_RMERR, XAER_RMFAIL, XAER_INVAL, and XAER_PROTO.
     *
     * @return The resource manager returns zero or more XIDs of the
     * transaction branches that are currently in a prepared or
     * heuristically completed state. If an error occurs during the
     * operation, the resource manager should throw the appropriate
     * XAException.
     *
     */
    public Xid[] recover(int flags) throws XAException {
        Xid[] result = null;
        
        if (Debug.debug) {
        	Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:recover:flags="+flags);
        }
        
        try {
            try {
                //Re-open if needed
                //_connection.openConnection(true);
            	//Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            } catch (Exception oce) {
                //XXX:RFE:Configuration needed
                //For now-retry once after a sec
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
                //_connection.openConnection(true);
                //Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            }
            //Perform XA recover
            result = _connection.getProtocolHandler().recover(flags);
            //_connection.closeConnection();
            //Bug6664278 - must synced
            _connection.closeConnectionFromRA();
        } catch (Exception jmse) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:recoverXAException");
            Debug.printStackTrace(jmse);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);
            throw xae;
        }
        return result;
    }

    /**
     * Informs the resource manager to roll back work done on behalf
     * of a transaction branch.
     *
     * @param foreignXid A global transaction identifier.
     *
     * @exception XAException An error has occurred.
     */
    public void rollback(Xid foreignXid) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrPrefix+"("+this.hashCode()+") Rollback  "+printXid(foreignXid)+")");
        } 
    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        
        if (Debug.debug) {
        	Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:rollback:txid=\n"+jmqXid.toString());
        }
        	
        try {
            try {
                //Re-open if needed
                //_connection.openConnection(true);
            	//Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            } catch (Exception oce) {
                //XXX:RFE:Configuration needed
                //For now-retry once after a sec
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
                //_connection.openConnection(true);
                //Bug6664278 - must synced
                _connection.openConnectionFromRA (true);
            }
            if (_transaction == null) {
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:rollback:using 0 as txnID");
                try {

            	if (this._connection.isConnectedToHABroker) {
            		this.HARollback(jmqXid, false);
            	} else {
            		_connection.getProtocolHandler().rollback(0L, jmqXid);
            	}

                } catch (JMSException e) {
                checkInternalRB(e, jmqXid);
                }
            } else {
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:rollback:using real txnID");
                //Setup protocol Handler
                _transaction.setProtocolHandler(_connection.getProtocolHandler());

                //Need to set redeliver on msgs if this session has not been closed.
                if (!_session.isClosed) {
                    //set sync flag
                    _session.setInSyncState();
                    //redeliver all messages received in this session
                    _session.receiveRollback();
                }
                try {

                //Perform XA rollback
                if (this._connection.isConnectedToHABroker) {
            		this.HARollback(jmqXid, false);
            	} else {
            		_transaction.rollbackXATransaction(jmqXid);
            	}

                } catch (JMSException e) {
                checkInternalRB(e, jmqXid);
                }
            }
            //_connection.closeConnection();
            //Bug6664278 - must synced
            _connection.closeConnectionFromRA();
        } catch (JMSException jmse) {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:rollbackXAException");
            Debug.printStackTrace(jmse);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);
            throw xae;
        } finally {
        	// finish up this resource and any others joined to it in this transaction
        	boolean throwExceptionIfNotFound = false;
        	XAResourceImpl[] resources = XAResourceMap.getXAResources(jmqXid,throwExceptionIfNotFound);
            for (int i = 0; i < resources.length; i++) {
            	XAResourceImpl xari = resources[i];
            	xari.finishRollback();
                xari.clearTransactionInfo();
			}
            XAResourceMap.unregister(jmqXid);
        	_session.releaseInSyncState();
        }
    }

    private void checkInternalRB(JMSException e, JMQXid jmqXid) throws JMSException {
        if (e.getErrorCode().equals(ClientResources.X_SERVER_ERROR)) {

            Exception e1 = e.getLinkedException();

            if (e1 != null && (e1 instanceof JMSException) &&
                ((JMSException)e1).getErrorCode().equals(Status.getString(Status.NOT_FOUND))) {

                if (lastInternalRBCache.containsValue(jmqXid)) {

                    _connection.connectionLogger.log(Level.INFO,
                                "Transaction "+jmqXid+" has already been rolled back");

                    lastInternalRBCache.remove(this);
                    lastInternalRB = false;

                    return;
                }
            }
        }
        throw e;
    }


    /**
     * <P>Sets the current transaction timeout value for this <CODE>XAResource</CODE>
     * instance. Once set, this timeout value is effective until
     * <code>setTransactionTimeout</code> is invoked again with a different
     * value. To reset the timeout value to the default value used by the resource
     * manager, set the value to zero.
     *
     * If the timeout operation is performed successfully, the method returns
     * <i>true</i>; otherwise <i>false</i>. If a resource manager does not
     * support explicitly setting the transaction timeout value, this method
     * returns <i>false</i>.
     *
     * @param transactionTimeout The transaction timeout value in seconds.
     *
     * @return <i>true</i> if the transaction timeout value is set successfully;
     *       otherwise <i>false</i>.
     *
     * @exception XAException An error has occurred. Possible exception values
     * are XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
     */
    public boolean setTransactionTimeout(int transactionTimeout) throws XAException {
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:setTransactionTimeout:timeout="+transactionTimeout);
        //XXX:GT RFE - transactionTimeout not supported for now
        return false;
        /*
        if (trannsactionTimeout != null && transactionTimeout >= 0) {
            this.transactionTimeout = transactionTimeout;
        } else {
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:setTransactionTimeoutXAException");
            //XXX:GT TBF I18N
            XAException xae = new XAException("Invalid transactionTimeout");
            xae.initCause(jmse);
            throw xae;
        }
        */
    }

    /**
     * Starts work on behalf of a transaction branch specified in
     * <code>foreignXid</code>.
     *
     * If TMJOIN is specified, the start applies to joining a transaction
     * previously seen by the resource manager. If TMRESUME is specified,
     * the start applies to resuming a suspended transaction specified in the
     * parameter <code>foreignXid</code>.
     *
     * If neither TMJOIN nor TMRESUME is specified and the transaction
     * specified by <code>foreignXid</code> has previously been seen by the resource
     * manager, the resource manager throws the XAException exception with
     * XAER_DUPID error code.
     *
     * @param foreignXid A global transaction identifier to be associated
     * with the resource.
     *
     * @param flags One of TMNOFLAGS, TMJOIN, or TMRESUME.
     *
     * @exception XAException An error has occurred. Possible exceptions
     * are XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_DUPID, XAER_OUTSIDE,
     * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     */
    public void start(Xid foreignXid, int flags) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrPrefix+"("+this.hashCode()+") Start   "+printXid(foreignXid)+printFlags(flags));
        }
    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);

        if (lastInternalRB) {
            lastInternalRBCache.remove(this);
            lastInternalRB = false;
        }

        if (Debug.debug) {
        	Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:start:flags="+flags+"\txid=\n"+jmqXid.toString());
        }
        
            try {
                _session.switchOnXATransaction();
                _transaction = _session.transaction;
                _transaction.startXATransaction(flags, jmqXid);
            
    	    if (!isResume(flags)){
                XAResourceMap.register(jmqXid, this, isJoin(flags));
    	    }
    	    
                currentJMQXid=jmqXid;
            } catch (JMSException jmse) {
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:startXAException");
                Debug.printStackTrace(jmse);
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                xae.initCause(jmse);
                throw xae;
            }
            
            // update the resource state
            resourceState=STARTED;
        }
        
    /**
      * Ends a recovery scan.
      */
    //public final static int TMENDRSCAN =   0x00800000;

    /**
      * Disassociates the caller and marks the transaction branch
      * rollback-only.
      */
    //public final static int TMFAIL =       0x20000000;

    /**
      * Caller is joining existing transaction branch.
      */
    //public final static int TMJOIN =       0x00200000;

    /**
      * Use TMNOFLAGS to indicate no flags value is selected.
      */
    //public final static int TMNOFLAGS =     0x00000000;

    /**
      * Caller is using one-phase optimization.
      */
    //public final static int TMONEPHASE =   0x40000000;

    /**
      * Caller is resuming association with a suspended
      * transaction branch.
      */
    //public final static int TMRESUME =     0x08000000;

    /**
      * Starts a recovery scan.
      */
    //public final static int TMSTARTRSCAN = 0x01000000;


    /**
      * Disassociates caller from a transaction branch.
      */
    //public final static int TMSUCCESS =    0x04000000;


    /**
      * Caller is suspending (not ending) its association with
      * a transaction branch.
      */
    //public final static int TMSUSPEND =    0x02000000;

    /**
     * The transaction branch has been read-only and has been committed.
     */
    //public final static int XA_RDONLY = 0x00000003;

    /**
     * The transaction work has been prepared normally.
     */
    //public final static int XA_OK = 0;

    /****** XaResourceImpl private methods *****/
    
    /**
     * 
     * @param key -- JMQXid
     * @param value -- transaction state.
     * 
     * HACC -- set rollbackOnly/prepared transaction state. 
     */
    protected static synchronized void setState (Object key, Object value) {
    	xaTable.put(key, value);
    }
    
    /**
     * HACC
     * @param key
     * @return
     */
    protected static synchronized Integer getState (Object key) {
    	return (Integer) xaTable.get(key);
    }
    
    /**
     * HACC
     * remove transaction states.  Called after commit/rollback successfully.
     * @param key
     * @return
     */
    protected static synchronized Object removeXid(Object key) {
    	return xaTable.remove(key);
    }
    
    /**
     * HACC
     * @param key
     * @return
     */
    protected static synchronized boolean isPrepared (Object key) {
    	boolean st = false;
    	
    	Integer value = (Integer) xaTable.get(key);
    	
    	if ( value != null ) {
    		if (value.intValue() == XA_PREPARE.intValue()) {
    			st = true;
    		}
    	}
    	
    	return st;
    }
    
    /**
     * HACC
     * @param key
     * @return
     */
    protected static synchronized boolean isRollbackOnly (Object key) {
    	boolean st = false;
    	
    	Integer value = (Integer) xaTable.get(key);
    	
    	if ( value != null ) {
    		if (value.intValue() == XA_ROLLBACK_ONLY.intValue()) {
    			st = true;
    		}
    	}
    	
    	return st;
    }
    
    /**
     * two-phase commit prepare for HA.
     * 
     */
    public synchronized int prepare(Xid foreignXid) throws XAException {
    	
    	 //result code
    	 int result = XA_OK;
    	 
    	 //this.twoPhasePrepared = false;
    	 JMQXid jmqXid = null;
    	 
    	 if (_connection.isConnectedToHABroker) {
    		 jmqXid = new JMQXid(foreignXid);
    	 }
    	 
    	 try {
    		//two phase commit 
    		this.prepare (foreignXid, false, false);
    	} catch (XAException xae) {
    		
    		if (_connection.isConnectedToHABroker) {
    			checkPrepareStatus(xae, jmqXid);
    		} else {
    			//non HA -- propagate exception
    			throw xae;
    		}
    		
    	}
    	
    	//set to true so that in case we need to verify transaction later.
    	//this.twoPhasePrepared = true;
    	
    	if (isXATracking()) {
        	xaTable.put (jmqXid, XA_PREPARE);
        }
    	
    	return result;
    }
    
    private boolean isXATracking() {
    	return (_connection.isConnectedToHABroker() && (XATracking));
    }
    
    /**
     * check prepared status
     * 
     * @param jmse
     * @param tstate -- transaction state when exception occurred
     * @param jmqXid -- 
     * @throws XAException if the transaction is not in prepared state.
     */
    private void checkPrepareStatus (XAException xae, JMQXid jmqXid) throws XAException {
    	
    	if (_connection.imqReconnect == false) {
			throw xae;
		}
    	
    	try {
    		
			SessionImpl.yield();

			_connection.checkReconnecting(null);

			if (_connection.isCloseCalled || _connection.connectionIsBroken) {
				throw xae;
			}
			//check failover broker to see the status
			verifyPrepare(jmqXid);
			
		} catch (XAException xae2) {
			//if any xaexception we simply propagate up
			throw xae2;
		} catch (Exception e) {
			//for any other exceptions, throws an XAException.
			XAException xae3 = new XAException(XAException.XAER_RMFAIL);
            xae3.initCause(e);
            throw xae3;
    	}
		
    }
    
    private void verifyPrepare (JMQXid jmqXid) throws XAException, JMSException {
    	
    	SessionImpl.sessionLogger.log(Level.INFO, "XA verifyPrepare(), jmqXid: " + jmqXid);
    	int state = _connection.protocolHandler.verifyHATransaction(0L, Transaction.TRANSACTION_ENDED, jmqXid);

        switch (state) {
        case 6:
                //transaction is in prepared state
        	SessionImpl.sessionLogger.log(Level.INFO, "transaction in prepared state: " + jmqXid);
            	return;           
        case 8:
        case 9:
        default:
            //for the rest of the state, the transaction was rolled back
            //by the broker.
        	XAException xae = new XAException(XAException.XA_RBROLLBACK);
        	throw xae;
        }
    }
    
    /**
     * For XA onePhase commit, if RA is connected to HA brokers, 
     * we use two phase MQ protocol to commit a transaction.
     * 
     * "JMQXAOnePhase" property is set to true for prepare and commit pkts.
     * 
     * "TMNOFLAGS" is used in the onePhase commit pkt.
     * 
     * 
     * @param foreignXid
     * @param jmqXid
     * @throws JMSException
     * @throws XAException
     */
    private void HAOnePhaseCommit (Xid foreignXid, JMQXid jmqXid, boolean insyncstate)
    throws JMSException, XAException {
    	
    	int tstate = Transaction.TRANSACTION_ENDED;
    		
    	try {
    		//prepare xa onephase commit
    		this.prepare(foreignXid, true, insyncstate);
    			
    		tstate = Transaction.TRANSACTION_PREPARED;
    		
    		if (isXATracking()) {
            	xaTable.put(jmqXid, XAResourceForRA.XA_PREPARE);
                }
    		
    		//param true is to indicate "JMQXAOnePhase" is needed
    		//for the commit protocol property.
    		_connection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid, true);
    	} catch (Exception jmse) {
                if ((jmse instanceof XAException) && 
                    ((XAException)jmse).errorCode == XAException.XA_RBROLLBACK) {
                    throw (XAException)jmse;
                }
                //check onephase commit status
                this.checkCommitStatus(jmse, tstate, jmqXid, true);
    	}
    	
    	this.removeXid(jmqXid);
    }
    
    private void HATwoPhaseCommit (JMQXid jmqXid) throws JMSException, XAException {
    	
    	//if (this.twoPhasePrepared == false) {
    	//	throw new XAException (XAException.XAER_PROTO);
    	//}
    	
    	try {
    		_connection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid);
    	} catch (JMSException jmse) {
    		
    		if ( this.isXATracking() ) {
    			
    			Integer ts = (Integer) xaTable.get(jmqXid);
    			//we must gaurantee that the transaction was indeed in prepared state.
    			if (ts != null && ts.intValue() == (Transaction.TRANSACTION_PREPARED)) {
    				this.checkCommitStatus(jmse, Transaction.TRANSACTION_PREPARED, jmqXid, false);
    			}
    			
    		} else {
    			//propagate the exception
    			throw jmse;
    		}
    		
    	}
    	
    	//transaction has successfully committed
    	//this.twoPhasePrepared = false;
    	
    	this.removeXid(jmqXid);
    }
    
    private void checkCommitStatus(Exception cause, int tstate, JMQXid jmqXid, boolean onePhase)
	throws JMSException, XAException {
    	
    	try {
    		
    		doCheckCommitStatus (cause, tstate, jmqXid, onePhase);
    	
    	} catch (Exception ex) {
    		//throw the original type of exception.
    		if (ex instanceof JMSException) {
				throw (JMSException) ex;
			} else if (ex instanceof XAException) {
				throw (XAException) ex;
			} else {
				XAException xae = new XAException (XAException.XAER_RMFAIL);
				xae.initCause(ex);
				throw xae;
			}
    	}
    }
    
    private void doCheckCommitStatus(Exception cause, int tstate, JMQXid jmqXid, boolean onePhase)
			throws Exception {

		if (_connection.imqReconnect == false) {
			throw cause;
		}

		SessionImpl.yield();

		_connection.checkReconnecting(null);

		if (_connection.isCloseCalled || _connection.connectionIsBroken) {
			throw cause;
		}

		verifyTransaction(tstate, jmqXid, onePhase);
	}

    private void verifyTransaction(int tstate, JMQXid jmqXid, boolean onePhase) throws JMSException, XAException {

        int state = _connection.protocolHandler.verifyHATransaction(0L, tstate, jmqXid);

        switch (state) {

        case 7:
           //committed
            return;
        case 6:
            //transaction is in prepared state.  ask broker to commit.
            try {
                //protocolHandler.rollback(this.transactionID);
            	SessionImpl.sessionLogger.log(Level.INFO, "XA verifyTransaction(): transaction is in prepred state, committing the transaction: " + jmqXid);
            	//epConnection.protocolHandler.commitHATransaction(this.transactionID);
            	_connection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid, onePhase);
            	SessionImpl.sessionLogger.log(Level.INFO, "XA verifyTransaction(): prepared transaction committed successfully: " + jmqXid);
            	//done if we can commit.
            	return;
            	
            } catch (JMSException jmse) {
                //in case this failed, we try one more time.
                //This is the third failure at this point.
                //If failed again, we log this and throw Exception.
                
                SessionImpl.yield();

                _connection.checkReconnecting(null);

                //if ( epConnection.isCloseCalled || epConnection.connectionIsBroken) {
                throw jmse;
                //}
                
                //we should rollback at this point.
                //epConnection.protocolHandler.rollback(this.transactionID);
            }
           
        case 8:
        case 9:
        default:
            //for the rest of the state, the transaction was rolled back
            //by the broker.
            //String errorString = AdministeredObject.cr.getKString(
            //    AdministeredObject.cr.X_TRANSACTION_FAILOVER_OCCURRED);

            //TransactionRolledBackException tre =
            //    new TransactionRolledBackException(errorString,
            //                                       AdministeredObject.cr.
            //                                       X_TRANSACTION_FAILOVER_OCCURRED);

            //throwRollbackException(tre);
        	XAException xae = new XAException(XAException.XA_RBROLLBACK);
        	throw xae;
        }
    }
    
    private void HACommit (Xid foreignXid, JMQXid jmqXid, boolean onePhase, boolean insyncstate) 
    	throws JMSException, XAException {
    	
    	if (onePhase) {
    		this.HAOnePhaseCommit(foreignXid, jmqXid, insyncstate);
    	} else {
    		this.HATwoPhaseCommit(jmqXid);
    	}
    }
    
private void HARollback(JMQXid jmqXid, boolean redeliverMsgs) throws JMSException, XAException {
    	
    	try {
    		_connection.getProtocolHandler().rollback(0L, jmqXid, redeliverMsgs);
    	} catch (JMSException jmse) {
    		
    		//yield/pause
    		SessionImpl.yield();
    		//block until fail-over/re-connected/closed
    		this._connection.checkReconnecting(null);
    		
    		//check if we still connected.
    		if (_connection.isCloseCalled || _connection.connectionIsBroken) {
    			throw jmse;
    		}
    		
    		//re-send with I bit set to true
    		this.retryRollBack(jmqXid, redeliverMsgs);
    	} finally {
    		//remove this xid
    		this.removeXid(jmqXid);
    	}
    }
    
    private void retryRollBack (JMQXid jmqXid, boolean redeliverMsgs) throws JMSException, XAException {
    	
    	try {
    		_connection.getProtocolHandler().rollback(0L, jmqXid, redeliverMsgs, true);
    	} catch (JMSException jmse) {
    		
    		if (isXATracking()) {
    			
				Integer tstate = (Integer) xaTable.get(jmqXid);

				if (tstate != null && tstate.intValue() != Transaction.TRANSACTION_PREPARED) {
					// the transaction is not in prepared state, we log the info
					// and continue.
					ConnectionImpl.connectionLogger.log(Level.WARNING, jmse.toString());
				} else {
					throw jmse;
				}
			} else {
				throw jmse;
			}
    	}
    }
    
    private boolean isJoin(int flags){
    	return((flags & XAResource.TMJOIN) == XAResource.TMJOIN);
    }
    
    private boolean isResume(int flags){
    	return((flags & XAResource.TMRESUME) == XAResource.TMRESUME);
    }

    public boolean isComplete() {
	return this.resourceState==COMPLETE;
    }
    
    public void clearTransactionInfo(){
    	this.resourceState=CREATED;
    }
    
    // Used for debugging only
    private String printXid(Xid foreignXid){
    	return ("(GlobalTransactionID="+foreignXid.getGlobalTransactionId()) +
    	        ", BranchQualifier="+foreignXid.getBranchQualifier()+") ";
    }
        
    // Used for debugging only
    private boolean isNoFlags(int flags){
    	return((flags & XAResource.TMNOFLAGS) == XAResource.TMNOFLAGS);
    }
    // Used for debugging only
    private boolean isFail(int flags){
    	return((flags & XAResource.TMFAIL) == XAResource.TMFAIL);
    }
    // Used for debugging only
    private boolean isOnePhase(int flags){
    	return((flags & XAResource.TMONEPHASE) == XAResource.TMONEPHASE);
    }
    // Used for debugging only
    private boolean isSuccess(int flags){
    	return((flags & XAResource.TMSUCCESS) == XAResource.TMSUCCESS);
    }
    // Used for debugging only
    private boolean isSuspend(int flags){
    	return((flags & XAResource.TMSUSPEND) == XAResource.TMSUSPEND);
    }    
    // Used for debugging only
    private boolean isTMENDRSCAN(int flags){
    	return((flags & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN);
    }      
    // Used for debugging only
    private boolean TMSTARTRSCAN(int flags){
    	return((flags & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN);
    }  
    
    // Used for debugging only
    private String printFlags(int flags){
    	String result = ("(Flags: ");
        if (isJoin(flags)){
        	result=result+("JOIN ");
        }
        if (isNoFlags(flags)){
        	result=result+("TMNOFLAGS ");
        }
        if (isFail(flags)){
        	result=result+("TMFAIL ");
        }
        if (isOnePhase(flags)){
        	result=result+("TMONEPHASE ");
        }
        if (isResume(flags)){
        	result=result+("TMRESUME ");
        }
        if (isSuccess(flags)){
        	result=result+("TMSUCCESS ");
        }
        if (isSuspend(flags)){
        	result=result+("TMSUSPEND ");
        }    
        if (isTMENDRSCAN(flags)){
        	result=result+("TMENDRSCAN ");
        }      
        if (TMSTARTRSCAN(flags)){
        	result=result+("TMSTARTRSCAN ");
        } 
        result=result+(")");
        return result;
    }
    
    
}
