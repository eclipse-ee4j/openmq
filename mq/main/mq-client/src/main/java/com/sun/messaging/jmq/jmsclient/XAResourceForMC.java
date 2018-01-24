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
 * @(#)XAResourceForMC.java	1.14 07/19/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.jmq.util.XidImpl;
import com.sun.messaging.jms.ra.api.JMSRAManagedConnection;
import com.sun.messaging.jms.ra.api.JMSRAResourceAdapter;
import com.sun.messaging.jms.ra.api.JMSRASessionAdapter;
import com.sun.messaging.jms.ra.api.JMSRAXASession;
import com.sun.messaging.jmq.jmsclient.XAResourceMapForRAMC;
import com.sun.messaging.jmq.io.Status;
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

public class XAResourceForMC implements XAResource, XAResourceForJMQ {

    //private SessionImpl _session;

    /* This XAResource depends on the connection being
     * valid across start,end,prepare,commit operations
     * as is the case for the j2ee 1.4 resource adapter
     * connection
     */
    private ConnectionImpl epConnection;
    //private Transaction _transaction = null;

    //Use Unique ID from broker
    private long resourceManagerId = 0L;
    private int transactionTimeout = 0; //transactions do not timeout

    //Id of the MC that this is associated with
    private JMSRAManagedConnection mc;

    private int id;

    //transaction ID - remains invalid until set by start
    private long transactionID = -1L;
    
    //this is set to true only the prepared protocol has returned successfully.
    //this flag is used in two phase verify transaction protocol to ensure
    //a transaction is actually in a prepared state.
    //replaced by xaTable.
    //private boolean twoPhasePrepared = false;

    //JmqXid
    private JMQXid jmqXid = null;

    protected boolean started = false;
    protected boolean active = false;
    
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
    
    //xaTable to store xa transaction state.  key/value=xid/xaState
    private static Hashtable xaTable = new Hashtable();
    
    /* Loggers */
    private static transient final String _className = "com.sun.messaging.jmq.jmsclient.XAResourceForMC";
    private static transient final String _lgrName = "com.sun.messaging.jmq.jmsclient.XAResourceForMC";
    private static transient final Logger _logger = Logger.getLogger(_lgrName);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_XARMC";
    private static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    private static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    private static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    private static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    private static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
    
    
    public XAResourceForMC(JMSRAManagedConnection mc, 
                           ConnectionImpl raConnection,
                           ConnectionImpl epConnection) throws JMSException {
        this.mc = mc;
        id = mc.getMCId();
        this.epConnection = epConnection;
        if (raConnection == null) {
            System.err.println("MQRA:XARFMC:constr:raConnectionNull:unable to acquire RMId:assuming distinct");
        } else {
            if (raConnection._isClosed()) {
                System.err.println("MQRA:XARFMC:constr:raConnectionClosed:unable to acquire RMId:assuming distinct");
            } else {
                try {
                    resourceManagerId = raConnection.getProtocolHandler().generateUID();
                    //System.out.println("MQRA:XAR4MC:Constr:id="+id);
                    //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:new:RMId="+resourceManagerId);
                } catch (Exception e) {
                    System.err.println("MQRA:XARFMC:constr:Exc aquiring RMId:assume distinct");
                }
            }
        }
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
    public synchronized void commit(Xid foreignXid, boolean onePhase) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
            _logger.fine(_lgrMID_INF+"XAResourceForMC ("+this.hashCode()+") Commit  "+
                         printXid(foreignXid)+" (onePhase="+onePhase+")");
        }
    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        //System.out.println("MQRA:XAR4MC:commit():mcId="+mc.getMCId()+":onePhase="+onePhase+" tid="+transactionID+" xid="+jmqXid.toString());
        //Debug.println("MQRA:XAR4RA:commit():onePhase="+onePhase+" tid="+transactionID+" xid="+jmqXid.toString());

        boolean checkrollback = false;
        boolean rbrollback = false;
        Exception rbrollbackex = null;
        try {
            if (!epConnection._isClosed()) {
            	
                if (onePhase) {
                    if ( epConnection.isConnectedToHABroker() ) {
                        HAOnePhaseCommit (foreignXid, jmqXid);
                    } else {
                        try {
                            epConnection.getProtocolHandler().commit(0L, XAResource.TMONEPHASE, jmqXid);
                        } catch (JMSException e) {
                            checkrollback = true;
                            throw e;
                        }
                    }
                } else {
                    if ( epConnection.isConnectedToHABroker() ) {
                        this.HATwoPhaseCommit (jmqXid);
                    } else {
                        epConnection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid);
                    }          
                }
                               
                JMSRASessionAdapter sa = mc.getConnectionAdapter().getJMSRASessionAdapter();
                if (sa != null) {
                    //System.out.println("MQRA:XAR4MC:commit:sa!=null");
                    sa.getJMSRAXASession().finishXATransactionForMC();
                }
                active = false;
            } else {
                System.err.println("MQRA:XARFMC:commit:ConnectionClosed:throw XAExc txn:1p="+onePhase+":xid="+jmqXid.toString());
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:commit:XAException");
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                throw xae;
            }
        } catch (XAException xaexception) {
            throw xaexception;
        } catch (Exception jmse) {
            System.err.println("MQRA:XARFMC:commit:XAException-Exception="+jmse.getMessage());
            Debug.printStackTrace(jmse);
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

                // finish up this resource and any others joined to it in this transaction
                boolean throwExceptionIfNotFound = false;
                XidImpl savedXid = this.jmqXid;
                XAResourceForJMQ[] resources = XAResourceMapForRAMC.getXAResources(this.jmqXid,throwExceptionIfNotFound);
                for (int i = 0; i < resources.length; i++) {
                    XAResourceForJMQ xari = resources[i]; 
                    try {
                        xari.clearTransactionInfo();
                    } catch (JMSException jmse) {
                        System.err.println("MQRA:XARFMC:commit:XAException-Exception="+jmse.getMessage());
                        Debug.printStackTrace(jmse);
                        XAException xae = new XAException(XAException.XAER_RMFAIL);
                        xae.initCause(jmse);
                        throw xae;
                    }
                }
                XAResourceMapForRAMC.unregister(savedXid);
            }
        }
        if (!rbrollback) {
            this.removeXid(jmqXid);
            return;
        }

        XAException xae;
        try {
            rollback(foreignXid, XAResourceMap.MAXROLLBACKS, 
                     XAResourceMap.DMQ_ON_MAXROLLBACKS);
            xae = new XAException(XAException.XA_RBROLLBACK);
            xae.initCause(rbrollbackex);
        } catch (Exception e) {
            SessionImpl.sessionLogger.log(Level.SEVERE,
            "Exception on rollback transaction "+jmqXid+" after 1-phase-commit failure", e);
            xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(rbrollbackex);
        }
        throw xae;
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
    private void HAOnePhaseCommit (Xid foreignXid, JMQXid jmqXid) throws JMSException, XAException {
    	
    	int tstate = Transaction.TRANSACTION_ENDED;
    	
    	try {
    		//prepare xa onephase commit
    		this.prepare(foreignXid, true);
    		
    		tstate = Transaction.TRANSACTION_PREPARED;
    		
    		if (isXATracking()) {
            	xaTable.put(jmqXid, XAResourceForRA.XA_PREPARE);
            }
    		
    		//param true is to indicate "JMQXAOnePhase" is needed
    		//for the commit protocol property.
    		epConnection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid, true);
    	} catch (Exception jmse) {
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
    		epConnection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid);
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
    
    /**
     * check prepared status
     * 
     * @param jmse
     * @param tstate -- transaction state when exception occurred
     * @param jmqXid -- 
     * @throws XAException if the transaction is not in prepared state.
     */
    private void checkPrepareStatus (XAException xae, JMQXid jmqXid) throws XAException {
    	
    	if (epConnection.imqReconnect == false) {
			throw xae;
		}
    	
    	try {
    		
			SessionImpl.yield();

			epConnection.checkReconnecting(null);

			if (epConnection.isCloseCalled || epConnection.connectionIsBroken) {
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
    	int state = epConnection.protocolHandler.verifyHATransaction(0L, Transaction.TRANSACTION_ENDED, jmqXid);

        switch (state) {
        case 6:
                //transaction is in prepared state
        	SessionImpl.sessionLogger.log(Level.INFO, "transaction in prepared state: " + this.transactionID);
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
    
    private void doCheckCommitStatus(Exception ex, int tstate, JMQXid jmqXid, boolean onePhase)
			throws Exception {

		if (epConnection.imqReconnect == false) {
			throw ex;
		}

		SessionImpl.yield();

		epConnection.checkReconnecting(null);

		if (epConnection.isCloseCalled || epConnection.connectionIsBroken) {
			throw ex;
		}

		verifyTransaction(tstate, jmqXid, onePhase);
	}

    private void verifyTransaction(int tstate, JMQXid jmqXid, boolean onePhase) throws JMSException, XAException {

        int state = epConnection.protocolHandler.verifyHATransaction(0L, tstate, jmqXid);

        switch (state) {

        case 7:
           //committed
            return;
        case 6:
            //transaction is in prepared state.  ask broker to commit.
            try {
                //protocolHandler.rollback(this.transactionID);
            	SessionImpl.sessionLogger.log(Level.INFO, "XA verifyTransaction(): transaction is in prepred state, committing the transaction: " + this.transactionID);
            	//epConnection.protocolHandler.commitHATransaction(this.transactionID);
            	epConnection.getProtocolHandler().commit(0L, XAResource.TMNOFLAGS, jmqXid, onePhase);
            	SessionImpl.sessionLogger.log(Level.INFO, "XA verifyTransaction(): prepared transaction committed successfully: " + this.transactionID);
            	//done if we can commit.
            	return;
            	
            } catch (JMSException jmse) {
                //in case this failed, we try one more time.
                //This is the third failure at this point.
                //If failed again, we log this and throw Exception.
                
                SessionImpl.yield();

                epConnection.checkReconnecting(null);

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
    public synchronized void end(Xid foreignXid, int flags) throws XAException {
    	   	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"XAResourceForMC ("+this.hashCode()+") End     "+printXid(foreignXid)+printFlags(flags));
        }

        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        //System.out.println("MQRA:XAR4MC:end():mcId="+mc.getMCId()+":flags="+flags+" xid="+jmqXid.toString());
        //Debug.println("MQRA:XAR4RA:end():flags="+flags+" tid="+transactionID+" xid="+jmqXid.toString());
        
        // update the resource state
        if (isFail(flags)){
        	resourceState=FAILED;
        } else if (isSuspend(flags)){
        	resourceState=INCOMPLETE;
        } else {
        	resourceState=COMPLETE;
        }
        
        if (JMSRAResourceAdapter.isRevert6882044()) {
        	// revert to pre-6882044 behaviour and forward all events to broker
        	sendEndToBroker(flags, false, jmqXid);
        } else {
	        // now decide based on the resource state whether to send a real
                // END packet, a noop END packet or to ignore the END packet altogether.
	        if (resourceState==COMPLETE){
	        	// This XAResource is complete. Send a real END packet if all
                        // other resources joined to this txn are complete, otherwise
                        // send a noop END packet to ensure that work associated with
                        // this XAResource has completed on the broker. See bug 12364646.
	        	boolean allComplete = true;
	       	 	XAResourceForJMQ[] resources = XAResourceMapForRAMC.getXAResources(jmqXid,true);
	       	 	for (int i = 0; i < resources.length; i++) {
	       	 	XAResourceForJMQ xari = resources[i]; 
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
        
        started = false;
        
        if (isXATracking()) {
        	xaTable.put(jmqXid, XAResourceForRA.XA_END);
        }
    }

    private void sendEndToBroker(int flags, boolean jmqnoop, JMQXid jmqXid) throws XAException {
		try {
            //System.out.println("MQRA:XAR4RA:end:sending 0L:tid="+transactionID+" xid="+jmqXid.toString());
        	
        	epConnection.protocolHandler.endTransaction(0L, jmqnoop, flags, jmqXid);
        		
            JMSRASessionAdapter sa = mc.getConnectionAdapter().getJMSRASessionAdapter();
            if (sa != null) {
                //System.out.println("MQRA:XAR4MC:end:sa!=null");
                sa.getJMSRAXASession().finishXATransactionForMC();
            }
        } catch (Exception jmse) {
            System.err.println("MQRA:XARFMC:end:XAException-Exception="+jmse.getMessage());
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
        //MQ does not support heuristically completed transaction branches
        //This is a NOP
        //convert to jmq xid
        //////JMQXid jmqXid = new JMQXid(foreignXid);
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:forget:txid=\n"+jmqXid.toString());
        XidImpl xidToForget = new XidImpl(foreignXid);
        XAResourceMapForRAMC.unregister(xidToForget);
        if (jmqXid!=null){
        	if (jmqXid.equals(xidToForget)){
        		try {
					clearTransactionInfo();
				} catch (JMSException jmse) {
		            System.err.println("MQRA:XARFMC:forget:XAException-Exception="+jmse.getMessage());
		            Debug.printStackTrace(jmse);
		            XAException xae = new XAException(XAException.XAER_RMFAIL);
		            xae.initCause(jmse);
		            throw xae;
				}
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
    	    	
    	if ((foreignXaRes instanceof XAResourceForMC)||(foreignXaRes instanceof XAResourceForRA)){
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
    	
    	if (JMSRAResourceAdapter.isSameRMAllowed()){
    		if ((getBrokerSessionID()!=0) && (getBrokerSessionID()==xaResource.getBrokerSessionID())){
    			result= true;
    		} else {
    			result=false;
    		}
    	} else {
    		result=false;
    	}
    	return result;
 
    }
    
    /**
     * Return the brokerSessionID of this object's connection
     * @return
     */
    public long getBrokerSessionID(){
    	return this.epConnection.getBrokerSessionID();
    }
    
    /**
     * two-phase commit prepare for HA.
     * 
     * 
     */
    public synchronized int prepare(Xid foreignXid) throws XAException {
    	
    	 //result code
    	 int result = XA_OK;
    	 
    	 //this.twoPhasePrepared = false;
    	 
    	 try {
    		//two phase commit 
    		this.prepare (foreignXid, false);
    	} catch (XAException xae) {
    		
    		if (this.epConnection.isConnectedToHABroker) {
    			this.checkPrepareStatus(xae, jmqXid);
    		} else {
    			//non HA -- propagate exception
    			throw xae;
    		}
    		
    	}
    	
    	//set to true so that in case we need to verify transaction later.
    	//this.twoPhasePrepared = true;
    	
    	if (isXATracking()) {
        	xaTable.put (jmqXid, XAResourceForRA.XA_PREPARE);
        }
    	
    	return result;
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
    private synchronized int prepare(Xid foreignXid, boolean onePhase) throws XAException {
    	    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"XAResourceForMC ("+this.hashCode()+") Prepare     "+printXid(foreignXid));
        }
    	
        //JMS does not do RDONLY transactions - right?
        int result = XA_OK;
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        //System.out.println("MQRA:XAR4MC:prepare():mcId="+mc.getMCId()+":xid="+jmqXid.toString());
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:txid=\n"+jmqXid.toString());
        try {
            if (!epConnection._isClosed()) {
            	epConnection.getProtocolHandler().prepare(0L, jmqXid, onePhase);        	
            } else {
                System.err.println("MQRA:XARFMC:prepare:ConnectionClosed:Rollback txn:xid="+jmqXid.toString());
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepareXAException:RB");
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                throw xae;
            }
        } catch (Exception jmse) {
            System.err.println("MQRA:XARFMC:prepare:XAException-Exception="+jmse.getMessage());
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepareXAException");
            Debug.printStackTrace(jmse);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);
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
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:recover:flags="+flags);
        try {
            if (!epConnection._isClosed()) {
                result = epConnection.getProtocolHandler().recover(flags);
            }
        } catch (Exception jmse) {
            System.err.println("MQRA:XARFMC:recover:XAException-Exception="+jmse.getMessage());
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
    public synchronized void rollback(Xid foreignXid) throws XAException {
        rollback(foreignXid, -1, false);
    }

    /**
     *
     * @param maxRollbacks maximum number of consecutive rollbacks
     *                     allowed for active consumers
     * @param dmqOnMaxRollbacks if true, place the consumed message
     *                          on DMQ if maxRollbacks reached
     */
    private synchronized void rollback(Xid foreignXid,
        int maxRollbacks, boolean dmqOnMaxRollbacks)
        throws XAException {
    	    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"XAResourceForMC ("+this.hashCode()+") Rollback  "+printXid(foreignXid)+")");
        }

        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        //System.out.println("MQRA:XAR4MC:rollback():mcId="+mc.getMCId()+":xid="+jmqXid.toString());
        //Debug.println("MQRA:XAR4RA:rollback():tid="+transactionID+" xid="+jmqXid.toString());
        JMSRAXASession xas = null;
        JMSRASessionAdapter sa = null;
        
        try {
            //send rollback w/redeliver
            if (!epConnection._isClosed()) {
                //System.out.println("MQRA:XAR4MC:rollback:Using epCon");
            	
            	sa = mc.getConnectionAdapter().getJMSRASessionAdapter();
            	
            	if (epConnection.isConnectedToHABroker()) {
            		//handle fail-over for HA connection
            		HARollback(jmqXid, maxRollbacks, dmqOnMaxRollbacks);
            	} else {
            		epConnection.getProtocolHandler().rollbackXA(
                            0L, jmqXid, true, false, maxRollbacks, dmqOnMaxRollbacks);
            	}
            	
                if (sa != null) {
                    //System.out.println("MQRA:XAR4MC:rollback:sa!=null");
                    //sa.getXASession().finishXATransactionForMC();
                    sa.getJMSRAXASession().finishXATransactionForMC();
                }
                active = false;
            } else {
                System.err.println("MQRA:XARFMC:rollback:ConnectionClosed:Rollback txn:xid="+jmqXid.toString());
                //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:prepare:XAException:RMFAIL");
                XAException xae = new XAException(XAException.XAER_RMFAIL);
                throw xae;
            }
        } catch (Exception jmse) {
            System.err.println("MQRA:XARFMC:rollback:XAException-Exception="+jmse.getMessage());
            //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:rollbackXAException");
            Debug.printStackTrace(jmse);
            XAException xae = new XAException(XAException.XAER_RMFAIL);
            xae.initCause(jmse);
            throw xae;
        } finally {
        	if ( sa != null ) {
        		xas = sa.getJMSRAXASession();
        		if (xas != null) {
                            xas.setFailoverOccurred(false);
        		}
        	}
        	
       	 	// finish up this resource and any others joined to it in this transaction
       	 	boolean throwExceptionIfNotFound = false;
       	 	XidImpl savedXid = this.jmqXid;
       	 	XAResourceForJMQ[] resources = XAResourceMapForRAMC.getXAResources(this.jmqXid,throwExceptionIfNotFound);
       	 	for (int i = 0; i < resources.length; i++) {
       	 		XAResourceForJMQ xari = resources[i]; 
       	 		try {
					xari.clearTransactionInfo();
				} catch (JMSException jmse) {
		            System.err.println("MQRA:XARFMC:rollback:XAException-Exception="+jmse.getMessage());
		            Debug.printStackTrace(jmse);
		            XAException xae = new XAException(XAException.XAER_RMFAIL);
		            xae.initCause(jmse);
		            throw xae;
				}
            }
            XAResourceMapForRAMC.unregister(savedXid);
        	 	
        }
        
        this.removeXid(jmqXid);
    }
    
    private void HARollback(JMQXid jmqXid, 
        int maxRollbacks, boolean dmqOnMaxRollbacks) 
        throws JMSException, XAException {
    	
    	try {
    		epConnection.getProtocolHandler().rollbackXA(0L, jmqXid, 
                    true, false, maxRollbacks, dmqOnMaxRollbacks);
    	} catch (JMSException jmse) {
    		
    		//yield/pause
    		SessionImpl.yield();
    		//block until fail-over/re-connected/closed
    		this.epConnection.checkReconnecting(null);
    		
    		//check if we still connected.
    		if (epConnection.isCloseCalled || epConnection.connectionIsBroken) {
    			throw jmse;
    		}
    		
    		//re-send with I bit set to true
    		this.retryRollBack(jmqXid, maxRollbacks, dmqOnMaxRollbacks);
    	}
    	
    	this.removeXid(jmqXid);
    }
    
    private void retryRollBack (JMQXid jmqXid, 
        int maxRollbacks, boolean dmqOnMaxRollbacks) 
        throws JMSException, XAException {
    	
    	try {
    		epConnection.getProtocolHandler().rollbackXA(0L, jmqXid, 
                    true, true, maxRollbacks, dmqOnMaxRollbacks);
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

    /**
	 * <P>
	 * Sets the current transaction timeout value for this <CODE>XAResource</CODE>
	 * instance. Once set, this timeout value is effective until
	 * <code>setTransactionTimeout</code> is invoked again with a different
	 * value. To reset the timeout value to the default value used by the
	 * resource manager, set the value to zero.
	 * 
	 * If the timeout operation is performed successfully, the method returns
	 * <i>true</i>; otherwise <i>false</i>. If a resource manager does not
	 * support explicitly setting the transaction timeout value, this method
	 * returns <i>false</i>.
	 * 
	 * @param transactionTimeout
	 *            The transaction timeout value in seconds.
	 * 
	 * @return <i>true</i> if the transaction timeout value is set
	 *         successfully; otherwise <i>false</i>.
	 * 
	 * @exception XAException
	 *                An error has occurred. Possible exception values are
	 *                XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
	 */
    public boolean setTransactionTimeout(int transactionTimeout) throws XAException {
        //Debug.println("*=*=*=*=*=*=*=*=*=*=XAR:setTransactionTimeout:timeout="+transactionTimeout);
        return false;
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
    public synchronized void start(Xid foreignXid, int flags) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
        		_logger.fine(_lgrMID_INF+"XAResourceForMC ("+this.hashCode()+") Start   "+printXid(foreignXid)+printFlags(flags));
        }
    	    	
        //convert to jmq xid
        JMQXid jmqXid = new JMQXid(foreignXid);
        //System.out.println("MQRA:XAR4MC:start():mcId="+mc.getMCId()+":flags="+flags+" xid="+jmqXid.toString());
        //Debug.println("MQRA:XAR4RA:start()
        
        //reset flag in start transaction
        //this.twoPhasePrepared = false;
        
        // if we're reverting to the pre-6882044 behaviour always send the START to the broker
        // otherwise send the START to the broker only if this is not a TMRESUME
	    if (!isResume(flags) || JMSRAResourceAdapter.isRevert6882044()){	    	
        
	        try {
	            transactionID = epConnection.protocolHandler.startTransaction(transactionID, flags, jmqXid);
	            this.jmqXid = jmqXid;
	            //System.out.println("MQRA:XAR4MC:start:transactionID="+transactionID+" xid="+jmqXid.toString());
	
	            JMSRASessionAdapter sa = mc.getConnectionAdapter().getJMSRASessionAdapter();
	            if (sa != null) {
	                //System.out.println("MQRA:XAR4MC:start():sa!=null");
	                sa.getJMSRAXASession().initXATransactionForMC(transactionID);
	            }
	        } catch (Exception jmse) {
	            System.err.println("MQRA:XARFMC:start:XAException-Exception="+jmse.getMessage());
	            Debug.printStackTrace(jmse);
	            XAException xae = new XAException(XAException.XAER_RMFAIL);
	            xae.initCause(jmse);
	            throw xae;
	        }
    
	        XAResourceMapForRAMC.register(jmqXid, this, isJoin(flags));
        
	    }
	    
        started = true;
        active = true;
	    
        // update the resource state
        resourceState=STARTED;
        
        //add xa state to table
        if (isXATracking()) {
        	xaTable.put(jmqXid, XAResourceForRA.XA_START);
        }
    }

    public int getId() {
        return id;
    }

    public synchronized long getTransactionID() {
        return transactionID;
    }

    public boolean started() {
        return started;
    }

    public boolean isActive() {
        return active;
    }
    
	public void clearTransactionInfo() throws JMSException {
		mc.getConnectionAdapter().closeForPoolingIfClosed();

		this.resourceState = CREATED;
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
     * XATracking default is set to true.
     * @return true if is connected to HA broker and XATracking flag is set to true.
     */
    private boolean isXATracking() {
    	return (epConnection.isConnectedToHABroker() && (XAResourceForRA.XATracking));
    }
    
    /**
     * remove xid in the XATable after commit/rollback successfully.
     * @param jmqXid
     */
    private void removeXid (JMQXid jmqXid) {
    	
    	if (isXATracking()) {
    		//System.out.println("***** removing xid: " + jmqXid + " ,xatable size: " + xaTable.size());
    		xaTable.remove(jmqXid);
    		//System.out.println("***** removed xid: " + jmqXid + " ,xatable size: " + xaTable.size());
    	}
    }
    
    private boolean isJoin(int flags){
    	return((flags & XAResource.TMJOIN) == XAResource.TMJOIN);
    }
    
    private boolean isResume(int flags){
    	return((flags & XAResource.TMRESUME) == XAResource.TMRESUME);
    }

    private boolean isFail(int flags){
    	return((flags & XAResource.TMFAIL) == XAResource.TMFAIL);
    }
    
    private boolean isSuspend(int flags){
    	return((flags & XAResource.TMSUSPEND) == XAResource.TMSUSPEND);
    }       
    
    // Used for debugging only
    private String printXid(Xid foreignXid){
    	return ("(GlobalTransactionID="+foreignXid.getGlobalTransactionId()) +
    	        ", BranchQualifier="+foreignXid.getBranchQualifier()+") ";
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
    
    // Used for debugging only
    private boolean isNoFlags(int flags){
    	return((flags & XAResource.TMNOFLAGS) == XAResource.TMNOFLAGS);
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
    private boolean isTMENDRSCAN(int flags){
    	return((flags & XAResource.TMENDRSCAN) == XAResource.TMENDRSCAN);
    }      
    // Used for debugging only
    private boolean TMSTARTRSCAN(int flags){
    	return((flags & XAResource.TMSTARTRSCAN) == XAResource.TMSTARTRSCAN);
    }

	public boolean isComplete() {
		return this.resourceState==COMPLETE;
	}  
}
