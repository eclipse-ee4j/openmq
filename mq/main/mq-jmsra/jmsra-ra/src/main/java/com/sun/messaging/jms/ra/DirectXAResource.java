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

package com.sun.messaging.jms.ra;

import javax.jms.JMSException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;

import com.sun.messaging.jmq.util.XidImpl;
import com.sun.messaging.jmq.jmsclient.Debug;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.sun.messaging.jmq.jmsservice.JMSServiceReply.Status;
import com.sun.messaging.jms.ra.util.DirectXAResourceMap;
import com.sun.messaging.jms.ra.api.JMSRAResourceAdapter;

/**
 *  DirectXAResource encapsulates XAResource functionality for DIRECT mode.<p>
 * 
 *  The XAResource interface is a Java mapping of the industry standard
 *  XA interface based on the X/Open CAE Specification (Distributed
 *  Transaction Processing: The XA Specification).
 * 
 *  <p>The XA interface defines the contract between a Resource Manager
 *  and a Transaction Manager in a distributed transaction processing
 *  (DTP) environment. A JDBC driver or a JMS provider implements
 *  this interface to support the association between a global transaction
 *  and a database or message service connection.
 * 
 *  <p>The XAResource interface can be supported by any transactional
 *  resource that is intended to be used by application programs in an
 *  environment where transactions are controlled by an external
 *  transaction manager. An example of such a resource is a database
 *  management system. An application may access data through multiple
 *  database connections. Each database connection is enlisted with
 *  the transaction manager as a transactional resource. The transaction
 *  manager obtains an XAResource for each connection participating
 *  in a global transaction. The transaction manager uses the
 *  <code>start</code> method
 *  to associate the global transaction with the resource, and it uses the
 *  <code>end</code> method to disassociate the transaction from
 *  the resource. The resource
 *  manager is responsible for associating the global transaction to all
 *  work performed on its data between the start and end method invocations.
 * 
 *  <p>At transaction commit time, the resource managers are informed by
 *  the transaction manager to prepare, commit, or rollback a transaction
 *  according to the two-phase commit protocol.</p>
 */
public class DirectXAResource
implements XAResource {
	
    /** The jmsservice that is assciated with this DirectXAResource */
    private JMSService jmsservice;

    /** The DirectConnection that is associated with this DirectXAResource */
    private DirectConnection dc;

    /** The connectionId (from DirectConnection) for this DirectXAResource */
    private long connectionId;

    /** The default transactionId and xid for this XAResource */
    private long mTransactionId = 0L;
    private XidImpl mXid = null;
    private int id = 0;

    /** Indicates whether this DirectXAResource has been enlisted or not */
    private boolean isEnlisted = false;

    /** Indicates whether this DirectXAResource is used by an MDB */
    private boolean usedByMDB = false;

    /** The xid / transactionId mapping table */
    private HashMap<String, Long> transactionIds = null;
    /**
     *  Logging
     */
    private static transient final String _className = "com.sun.messaging.jms.ra.DirectXAResource";
    private static transient final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSXAResource = "javax.resourceadapter.mqjmsra.xa";
    private static transient final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
    private static transient final Logger _loggerJX = Logger.getLogger(_lgrNameJMSXAResource);
    private static transient final String _lgrName = "com.sun.messaging.jms.ra.DirectXAResource";
    private static transient final Logger _logger = Logger.getLogger(_lgrName);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_DXA";
    private static transient final String _lgrMID_EET = _lgrMIDPrefix+"1001: ";
    private static transient final String _lgrMID_INF = _lgrMIDPrefix+"1101: ";
    private static transient final String _lgrMID_WRN = _lgrMIDPrefix+"2001: ";
    private static transient final String _lgrMID_ERR = _lgrMIDPrefix+"3001: ";
    private static transient final String _lgrMID_EXC = _lgrMIDPrefix+"4001: ";

    private static int idCounter = 0;

    /** For optimized logging */
    protected static final int _logLevel;
    protected static final boolean _logFINE;
    
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
        
	static {                                                                        
//        _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
//        _loggerJX = Logger.getLogger(_lgrNameJMSXAResource);
        java.util.logging.Level _level = _loggerJX.getLevel();
        int tmplevel = java.util.logging.Level.INFO.intValue();
        boolean tmplogfine = false;
        if (_level != null) {
            tmplevel = _level.intValue();
            if (tmplevel <= java.util.logging.Level.FINE.intValue()){
                tmplogfine = true;
            }
        }
        _logLevel = tmplevel; 
        _logFINE = tmplogfine;
    }

    /**
     * Creates a new instance of DirectXAResource
     */
    public DirectXAResource(DirectConnection dc,
            JMSService jmsservice, long connectionId) {
        Object params[] = new Object[3];
        params[0] = dc;
        params[1] = jmsservice;
        params[2] = connectionId;
        _loggerOC.entering(_className, "constructor()", params);        
        this.dc = dc;
        this.jmsservice = jmsservice;
        this.connectionId = dc.getConnectionId();
        this.isEnlisted = false;
        this.id = incrementIdCounter();
    }
    
    private static synchronized int incrementIdCounter(){
    	return ++idCounter;
    }

    /**
     *  Commit the global transaction specified by xid.
     *
     *  @param  foreignXid A global transaction identifier
     *
     *  @param  onePhase If true, the resource manager should use a one-phase
     *          commit protocol to commit the work done on behalf of xid.
     *
     *  @throws XAException An error has occurred. Possible XAExceptions
     *          are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB, XA_HEURMIX, XAER_RMERR,
     *          XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     *  <P>If the resource manager did not commit the transaction and the
     *  parameter onePhase is set to true, the resource manager may throw
     *  one of the XA_RB* exceptions. Upon return, the resource manager has
     *  rolled back the branch's work and has released all held resources.
     */
    public synchronized void commit(Xid foreignXid, boolean onePhase)
    throws XAException {

        if (_logger.isLoggable(Level.FINE)){
           _logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") Commit  "+
               printXid(foreignXid)+" (onePhase="+onePhase+"), connectionId="+connectionId);
        }
    	
        //convert to XidImpl
        //XidImpl mqxid = new XidImpl(foreignXid);
        String methodName = "commit()";
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName + ":onePhase=" + onePhase +
                ":transactionId=" + this.mTransactionId //+"Xid="+mqxid.toString()
                );
        }
            	
        boolean rbrollback = false;
        Exception rbrollbackex = null;

         //JMSServiceReply reply = null;
         JMSServiceReply.Status status;
         try {
             //reply = jmsservice.commitTransaction(this.connectionId,
             jmsservice.commitTransaction(this.connectionId,
                    this.mTransactionId, foreignXid,
                    (onePhase ? XAResource.TMONEPHASE : XAResource.TMNOFLAGS)
                    );
             this.setEnlisted(false);
             if (_logFINE){
                 _loggerJX.fine(_lgrMID_INF + methodName +
                        ":connectionId=" + this.connectionId +
                        ":committed transactionId=" + this.mTransactionId
                        //+"Xid="+mqxid.toString()
                        );
             }
         } catch (JMSServiceException jse) {
             status = jse.getJMSServiceReply().getStatus();
             String failure_cause = getFailureCauseAsString(status, jse);
             //XXX:tharakan:This message should be in the JMSServiceException
             String exerrmsg = 
                    "commitTransaction (XA) on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " and onePhase:" + onePhase +
                    //"and Xid:" + mqxid.toString() +
                    " due to " + failure_cause;
             _loggerOC.severe(exerrmsg);
             if (onePhase) {
                 rbrollback = true;
                 rbrollbackex = jse;
             }
             if (!rbrollback) {
                 XAException xae = new XAException(XAException.XAER_RMFAIL);
                 xae.initCause(jse);
                 throw xae;
             }
         } finally {
             if (!rbrollback) {

                 // finish up this resource and any others joined to it in this transaction
                 boolean throwExceptionIfNotFound = false;
                 XidImpl savedXid = this.mXid;
                 DirectXAResource[] resources = DirectXAResourceMap.
                     getXAResources(this.mXid,throwExceptionIfNotFound);
                 for (int i = 0; i < resources.length; i++) {
                     DirectXAResource xari = resources[i];
                     try {
                         xari.clearTransactionInfo();
                     } catch (JMSException jmse) {
                         _loggerJX.log(Level.SEVERE, 
                             "MQRA:DXAR:commit:XAException-Exception="+jmse.getMessage(),jmse);
                          Debug.printStackTrace(jmse);
                          XAException xae = new XAException(XAException.XAER_RMFAIL);
                          xae.initCause(jmse);
                          throw xae;
                     }
                 }
                 DirectXAResourceMap.unregister(savedXid);
             }
         }
         if (rbrollback) {
             XAException xae;
             try {
                 rollback(foreignXid, DirectXAResourceMap.MAXROLLBACKS,
                          DirectXAResourceMap.DMQ_ON_MAXROLLBACKS);
                 xae = new XAException(XAException.XA_RBROLLBACK);
                 xae.initCause(rbrollbackex);
             } catch (Exception e) {
                 _loggerJX.log(Level.SEVERE, "Exception on rollback transaction "+
                     foreignXid+"["+mTransactionId+"] after 1-phase-commit failure", e);
                xae = new XAException(XAException.XAER_RMFAIL);
                xae.initCause(rbrollbackex);
             }
             throw xae;
         }
     }

	private String getFailureCauseAsString(JMSServiceReply.Status status, JMSServiceException jse) {
		String failure_cause;
		if (jse.getCause()==null){
			failure_cause = jse.toString();
		} else {
			failure_cause = jse.getCause().toString();
		}
		switch (status) {
		    case CONFLICT:
		        failure_cause = "CONFLICT: "+failure_cause;
		        break;
		    default:
		        failure_cause = "Unknown JMSService server error "+status+": "+failure_cause;
		 }
		return failure_cause;
	}
    
    public synchronized void clearTransactionInfo() throws JMSException{
    	dc.deleteTemporaryDestinationsIfClosed();

    	this.resourceState=CREATED;
        this.mTransactionId = 0L;
        this.mXid = null;
    }

    /**
     *  End the work performed on behalf of a transaction branch.
     *  The resource manager disassociates the XA resource from the
     *  transaction branch specified and lets the transaction
     *  complete.
     *
     *  <p>If TMSUSPEND is specified in the flags, the transaction branch
     *  is temporarily suspended in an incomplete state. The transaction
     *  context is in a suspended state and must be resumed via the
     *  <code>start</code> method with TMRESUME specified.</p>
     *
     *  <p>If TMFAIL is specified, the portion of work has failed.
     *  The resource manager may mark the transaction as rollback-only</p>
     *
     *  <p>If TMSUCCESS is specified, the portion of work has completed
     *  successfully.</p>
     *
     *  @param  foreignXid A global transaction identifier that is the same as
     *          the identifier used previously in the <code>start</code> method.
     *
     *  @param  flags One of TMSUCCESS, TMFAIL, or TMSUSPEND.
     *
     *  @throws XAException If an error has occurred. Possible XAException
     *          values are XAER_RMERR, XAER_RMFAILED, XAER_NOTA, XAER_INVAL,
     *          XAER_PROTO, or XA_RB*.
     */
    public synchronized void end(Xid foreignXid, int flags)
    throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") End     "+printXid(foreignXid)+printFlags(flags)+", connectionId="+connectionId);
        }
    	
        String methodName = "end()";
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName + ":flags=" + flags +
                    ":transactionId=" + this.mTransactionId //+
                    //":Xid="+mqxid.toString()
                    );
        }
        
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
        	sendEndToBroker(foreignXid, flags);
        } else {
	        // now decide whether to send an END packet to the broker on the basis of the resource state
	        if (resourceState==COMPLETE ){
	        	// only send an END packet to the broker when all joined resources are complete
	        	// this works around Glassfish issue 7118
	        	boolean allComplete = true;
	       	 	DirectXAResource[] resources = DirectXAResourceMap.getXAResources(this.mXid,true);
	       	 	for (int i = 0; i < resources.length; i++) {
	       	 		DirectXAResource xari = resources[i]; 
	       	 		if (xari.getResourceState()!=COMPLETE){
	       	 			allComplete = false;
	       	 		}
	       	 	}
	       	 	if (allComplete){
	       	 		sendEndToBroker(foreignXid, flags);
	       	 	}
	        } else if (resourceState==FAILED){
	        	sendEndToBroker(foreignXid, flags);
	        }
	        // if resourceState is neither COMPLETE nor FAILED then it must be INCOMPLETE
	        // in which case we don't send the END to the broker
        }
    	    	
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName +
                   ":connectionId=" + this.connectionId +
                   ":ended transactionId=" + this.mTransactionId
                   //+"Xid="+mqxid.toString()
                   );
        }
    	
    }
    
    /**
     * Notify the broker than end() has been called
     * 
     * @param foreignXid
     * @param flags
     * @throws XAException
     */
    public synchronized void sendEndToBroker(Xid foreignXid, int flags)
    throws XAException {

        //convert to XidImpl
        //XidImpl mqxid = new XidImpl(foreignXid);
        String methodName = "endToBroker()";
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName + ":flags=" + flags +
                    ":transactionId=" + this.mTransactionId //+
                    //":Xid="+mqxid.toString()
                    );
        }
        //JMSServiceReply reply = null;
        JMSServiceReply.Status status;
        try {
        		//reply = jmsservice.endTransaction(this.connectionId,
        		jmsservice.endTransaction(this.connectionId,
                    this.mTransactionId, foreignXid, flags);
            if (_logFINE){
                _loggerJX.fine(_lgrMID_INF + methodName +
                       ":connectionId=" + this.connectionId +
                       ":ended transactionId=" + this.mTransactionId
                       //+"Xid="+mqxid.toString()
                       );
            }
        } catch (JMSServiceException jse) {
            status = jse.getJMSServiceReply().getStatus();
            String failure_cause = getFailureCauseAsString(status, jse);
            //XXX:tharakan:This message should be in the JMSServiceException
            String exerrmsg = 
                    "endTransaction (XA) on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    //"and Xid:" + mqxid.toString() +
                    " and flags=" + flags +
                    " due to " + failure_cause;
            _loggerOC.severe(exerrmsg);
            XAException xae = new XAException(XAException.XAER_RMERR);
            xae.initCause(jse);
            throw xae;
        }
    }

    /**
     *  Tell the resource manager to forget about a heuristically
     *  completed transaction branch.
     *
     *  @param  foreignXid A global transaction identifier.
     *
     *  @throws XAException If an error has occurred. Possible exception
     *          values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
     *          XAER_PROTO.
     */
    public synchronized void forget(Xid foreignXid)
    throws XAException {
        //convert to XidImpl
        //XidImpl mqxid = new XidImpl(foreignXid);
        if (_logFINE){
            _loggerJX.warning(_lgrMID_INF+"forget()UNSUPPORTED"+
                    ":Xid="+foreignXid.toString()+", connectionId="+connectionId);
         }
        XidImpl xidToForget = new XidImpl(foreignXid);
        DirectXAResourceMap.unregister(xidToForget);
        if (mXid!=null){
        	if (mXid.equals(xidToForget)){
        		try {
					clearTransactionInfo();
				} catch (JMSException jmse) {
					_loggerJX.log(Level.SEVERE,"MQRA:DXAR:forget:XAException-Exception=" + jmse.getMessage(),jmse);
					Debug.printStackTrace(jmse);
					XAException xae = new XAException(XAException.XAER_RMFAIL);
					xae.initCause(jmse);
					throw xae;
				}
        	}
        }
    }

    /**
     *  Obtains the current transaction timeout value set for this
     *  XAResource instance. If <CODE>XAResource.setTransactionTimeout</CODE>
     *  was not used prior to invoking this method, the return value
     *  is the default timeout set for the resource manager; otherwise,
     *  the value used in the previous <CODE>setTransactionTimeout</CODE>
     *  call is returned.
     *
     *  @return The transaction timeout value in seconds.
     *
     *  @throws XAException If an error has occurred. Possible exception
     *          values are XAER_RMERR and XAER_RMFAIL.
     */
    public int getTransactionTimeout()
    throws XAException {
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF+"getTransactionTimeout() = 0");
        }
        return 0;
    }
    
    private synchronized int getResourceState() {
        return resourceState;
    }

    /**
     *  This method is called to determine if the resource manager
     *  instance represented by the target object is the same as the
     *  resouce manager instance represented by the parameter <i>xares</i>.
     *
     *  @param  foreignXaRes An XAResource object whose resource manager
     *          instance is to be compared with the resource manager instance
     *          of the target object.
     *
     *  @return <i>true</i> if it's the same RM instance; otherwise
     *          <i>false</i>.
     *
     *  @throws XAException If an error has occurred. Possible exception
     *          values are XAER_RMERR and XAER_RMFAIL.
     *
     */
    public boolean isSameRM(XAResource foreignXaRes)
    throws XAException {
    	boolean result;
        if (JMSRAResourceAdapter.isSameRMAllowed() && (foreignXaRes instanceof DirectXAResource)){
            result= true;
        } else {
            result= false;
            }
        
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") comparing with ("+foreignXaRes.hashCode()+") result="+result+", connectionId="+connectionId);
        }
              
        return result;
        
    }

    /**
     *  Ask the resource manager to prepare for a transaction commit
     *  of the transaction specified in xid.
     *
     *  @param  foreignXid A global transaction identifier.
     *
     *  @return A value indicating the resource manager's vote on the
     *          outcome of the transaction. The possible values are: XA_RDONLY
     *          or XA_OK. If the resource manager wants to roll back the
     *          transaction, it should do so by raising an appropriate
     *          XAException in the prepare method.
     *
     *  @throws XAException If an error has occurred. Possible exception
     *          values are: XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_NOTA,
     *          XAER_INVAL, or XAER_PROTO.
     */
    public synchronized int prepare(Xid foreignXid)
    throws XAException {

        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") Prepare     "+printXid(foreignXid)+", connectionId="+connectionId);
        }
    	
        //convert to XidImpl
        //XidImpl mqxid = new XidImpl(foreignXid);
        String methodName = "prepare()";
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName +
                    ":transactionId=" + this.mTransactionId //+
                    //:Xid="+mqxid.toString()
                    );
        }

        //JMS does not do RDONLY transactions
        int result = XA_OK;

        //JMSServiceReply reply = null;
        JMSServiceReply.Status status;
        try {
            //reply = jmsservice.prepareTransaction(this.connectionId,
            jmsservice.prepareTransaction(this.connectionId,
                    this.mTransactionId, foreignXid);
            if (_logFINE){
                _loggerJX.fine(_lgrMID_INF + methodName +
                       ":connectionId=" + this.connectionId +
                       ":prepared transactionId=" + this.mTransactionId
                       //+"Xid="+mqxid.toString()
                       );
            }
        } catch (JMSServiceException jse) {
            status = jse.getJMSServiceReply().getStatus();
            String failure_cause = getFailureCauseAsString(status, jse);
            //XXX:tharakan:This message should be in the JMSServiceException
            String exerrmsg = 
                    "prepareTransaction (XA) on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    //"and Xid:" + mqxid.toString() +
                    " due to " + failure_cause;
            _loggerOC.severe(exerrmsg);
            XAException xae = new XAException(XAException.XAER_RMERR);
            xae.initCause(jse);
            throw xae;
        }
        
        // update the resource state
        resourceState=PREPARED;
        
        return result;
    }

    /**
     *  Obtain a list of prepared transaction branches from a resource
     *  manager. The transaction manager calls this method during recovery
     *  to obtain the list of transaction branches that are currently in
     *  prepared or heuristically completed states.
     *
     *  @param  flags One of TMSTARTRSCAN, TMENDRSCAN, TMNOFLAGS. TMNOFLAGS
     *          must be used when no other flags are set in the parameter.
     *
     *  @return The resource manager returns zero or more XIDs of the
     *          transaction branches that are currently in a prepared or
     *          heuristically completed state. If an error occurs during the
     *          operation, the resource manager should throw the appropriate
     *          XAException.
     *
     *  @throws XAException If an error has occurred. Possible values are
     *          XAER_RMERR, XAER_RMFAIL, XAER_INVAL, and XAER_PROTO.
     */
    public Xid[] recover(int flags)
    throws XAException {
        if (_logFINE){
            //String methodName = "recover()";
            _loggerJX.fine(_lgrMID_INF+"recover():flags="+flags+", connectionId="+connectionId);
        }
        javax.transaction.xa.Xid[] result = null;
        JMSServiceReply.Status status;
        try {
            result = jmsservice.recoverXATransactions(this.connectionId, flags);
        } catch (JMSServiceException jse) {
            status = jse.getJMSServiceReply().getStatus();
            String failure_cause = getFailureCauseAsString(status, jse);
            //XXX:tharakan:This message should be in the JMSServiceException
            String exerrmsg = 
                    "recoverXATransactions (XA) on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    " due to " + failure_cause;
            _loggerOC.severe(exerrmsg);
            XAException xae = new XAException(XAException.XAER_RMERR);
            xae.initCause(jse);
            throw xae;
        }
        return result;
    }

    /**
     *  Inform the resource manager to roll back work done on behalf
     *  of a transaction branch.
     *
     *  @param  foreignXid A global transaction identifier.
     *
     *  @throws XAException If an error has occurred.
     */
    public synchronized void rollback(Xid foreignXid)
    throws XAException {
        rollback(foreignXid, -1, false);
    }

    private synchronized void rollback(Xid foreignXid, 
        int maxRollbacks, boolean dmqOnMaxRollbacks)
        throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") Rollback  "+printXid(foreignXid)+"), connectionId="+connectionId);
        }  
    	
        //convert to XidImpl
        //XidImpl mqxid = new XidImpl(foreignXid);
        String methodName = "rollback()";
        if (_logFINE){
            _loggerJX.fine(_lgrMID_INF + methodName +
                    ":transactionId="+ this.mTransactionId //+
                    //":Xid="+mqxid.toString());
                    );
        }
                      
        //JMSServiceReply reply = null;
        JMSServiceReply.Status status;
        try {
            //reply = jmsservice.rollbackTransaction(this.connectionId,
            jmsservice.rollbackTransaction(this.connectionId,
                    this.mTransactionId, foreignXid,
                    true, true, maxRollbacks, dmqOnMaxRollbacks
                    );
            this.setEnlisted(false);
            if (_logFINE){
                _loggerJX.fine(_lgrMID_INF + methodName +
                       ":connectionId=" + this.connectionId +
                       ":rolled back transactionId=" + this.mTransactionId
                       //+"Xid="+mqxid.toString()
                       );
            }
        } catch (JMSServiceException jse) {
            status = jse.getJMSServiceReply().getStatus();
            String failure_cause = getFailureCauseAsString(status, jse);
            //XXX:tharakan:This message should be in the JMSServiceException
            String exerrmsg = 
                    "rollbackTransaction (XA) on JMSService:" +
                    jmsservice.getJMSServiceID() +
                    " failed for connectionId:"+ connectionId +
                    ":transactionId=" + this.mTransactionId +
                    //"and Xid:" + mqxid.toString() +
                    " due to " + failure_cause;
            _loggerOC.severe(exerrmsg);
            XAException xae = new XAException(XAException.XAER_RMERR);
            xae.initCause(jse);
            throw xae;
        } finally {
       	 	// finish up this resource and any others joined to it in this transaction
        	boolean throwExceptionIfNotFound = false;
       	 	DirectXAResource[] resources = DirectXAResourceMap.getXAResources(this.mXid,throwExceptionIfNotFound);
       	    XidImpl savedXid = this.mXid;
       	 	for (int i = 0; i < resources.length; i++) {
       	 		DirectXAResource xari = resources[i]; 
       	 		try {
					xari.clearTransactionInfo();
				} catch (JMSException jmse) {
					_loggerJX.log(Level.SEVERE,"MQRA:DXAR:rollback:XAException-Exception=" + jmse.getMessage(),jmse);
					Debug.printStackTrace(jmse);
					XAException xae = new XAException(XAException.XAER_RMFAIL);
					xae.initCause(jmse);
					throw xae;
				}
       	 	}
            DirectXAResourceMap.unregister(savedXid);   
        }
    }

    /**
     *  Set the current transaction timeout value for this <CODE>XAResource</CODE>
     *  instance. Once set, this timeout value is effective until
     *  <code>setTransactionTimeout</code> is invoked again with a different
     *  value. To reset the timeout value to the default value used by the resource
     *  manager, set the value to zero.
     *
     *  If the timeout operation is performed successfully, the method returns
     *  <i>true</i>; otherwise <i>false</i>. If a resource manager does not
     *  support explicitly setting the transaction timeout value, this method
     *  returns <i>false</i>.
     *
     *  @param  transactionTimeout The transaction timeout value in seconds.
     *
     *  @return <i>true</i> if the transaction timeout value is set successfully;
     *          otherwise <i>false</i>.
     *
     *  @throws XAException If an error has occurred. Possible exception values
     *  are XAER_RMERR, XAER_RMFAIL, or XAER_INVAL.
     */
    public boolean setTransactionTimeout(int transactionTimeout)
    throws XAException {
        if (_logFINE){
            //String methodName = "setTransactionTimeout()";
            _loggerJX.fine(_lgrMID_INF+"setTransactionTimeout()="+
                    transactionTimeout+
                    ":returning false.");
        }
        return false;
    }
    
    /**
     *  Start work on behalf of a transaction branch specified in
     *  <code>foreignXid</code>.
     *
     *  If TMJOIN is specified, the start applies to joining a transaction
     *  previously seen by the resource manager. If TMRESUME is specified,
     *  the start applies to resuming a suspended transaction specified in the
     *  parameter <code>foreignXid</code>.
     *
     *  If neither TMJOIN nor TMRESUME is specified and the transaction
     *  specified by <code>foreignXid</code> has previously been seen by the resource
     *  manager, the resource manager throws the XAException exception with
     *  XAER_DUPID error code.
     *
     *  @param  foreignXid A global transaction identifier to be associated
     *          with the resource.
     *
     *  @param  flags One of TMNOFLAGS, TMJOIN, or TMRESUME.
     *
     *  @throws XAException If an error has occurred. Possible exceptions
     *          are XA_RB*, XAER_RMERR, XAER_RMFAIL, XAER_DUPID, XAER_OUTSIDE,
     *          XAER_NOTA, XAER_INVAL, or XAER_PROTO.
     *
     */
    public synchronized void start(Xid foreignXid, int flags) throws XAException {
    	
        if (_logger.isLoggable(Level.FINE)){
    		_logger.fine(_lgrMID_INF+"DirectXAResource ("+this.hashCode()+") Start   "+printXid(foreignXid)+printFlags(flags)+", connectionId="+connectionId);
        }
    	
		// convert to XidImpl
		// XidImpl mqxid = new XidImpl(foreignXid);
		String methodName = "start()";
		if (_logFINE) {
			_loggerJX.fine(_lgrMID_INF + methodName + ":flags=" + flags + ":connectioId=" + this.connectionId
			// +"Xid="+mqxid.toString()
					);
		}
		
        // if we're reverting to the pre-6882044 behaviour always send the START to the broker
        // otherwise send the START to the broker only if this is not a TMRESUME
	    if (!isResume(flags) || JMSRAResourceAdapter.isRevert6882044()){
	    	long transactionId = sendStartToBroker(foreignXid, flags);
	        validateAndSaveXidTransactionID(foreignXid, transactionId);
	        DirectXAResourceMap.register(mXid, this,isJoin(flags));
	    }
	     
		this.setEnlisted(true);
		if (_logFINE) {
			_loggerJX.fine(_lgrMID_INF + methodName + ":connectionId=" + this.connectionId + ":started transactionId="
					+ this.mTransactionId
			// +"Xid="+mqxid.toString()
					);
		}
	}

	private long sendStartToBroker(Xid foreignXid, int flags)
			throws XAException {
		long transactionId;
		try {
			// We must be sessionId agnostic
			JMSServiceReply reply = jmsservice.startTransaction(connectionId, 0L, foreignXid, flags,
					JMSService.TransactionAutoRollback.UNSPECIFIED, 0L);
			try {
				transactionId = reply.getJMQTransactionID();
			} catch (NoSuchFieldException nsfe) {
				String exerrmsg = _lgrMID_EXC + "JMSServiceException:Missing JMQTransactionID";
				XAException xae = new XAException(XAException.XAER_RMFAIL);
				xae.initCause(nsfe);
				_loggerOC.severe(exerrmsg);
				throw xae;
			}

		} catch (JMSServiceException jse) {
			JMSServiceReply.Status status = jse.getJMSServiceReply().getStatus();
			String failure_cause;
			if (status==Status.NOT_IMPLEMENTED){
				failure_cause = "TransactionAutoRollback not implemented.";
			} else {
				failure_cause = getFailureCauseAsString(status, jse);
			}
			String exerrmsg = "startTransaction (XA) on JMSService:" + jmsservice.getJMSServiceID()
					+ " failed for connectionId:" + connectionId +
					// "and Xid:" + mqxid.toString() +
					" due to " + failure_cause;
			_loggerOC.severe(exerrmsg);
			XAException xae = new XAException(XAException.XAER_RMFAIL);
			xae.initCause(jse);
			throw xae;
		}
		return transactionId;
	}

	/**
	 * Validate the specified foreignXid and transactionID
	 * Convert the specified foreignXid to our own implementation
	 * and save in the fields mTransactionId and mXid
	 * 
	 * @param foreignXid
	 * @param transactionId
	 */
	private synchronized void validateAndSaveXidTransactionID(Xid foreignXid, long transactionId) {
		assert transactionId != 0L;
		if ((this.mTransactionId == 0L) && (this.mXid == null)) {
			this.mTransactionId = transactionId;
			this.mXid = new XidImpl(foreignXid); // mqxid;
		} else {
			if (this.mXid.equals(foreignXid)) {
				if (this.mTransactionId != transactionId) {
					_loggerJX.log(Level.INFO,"DXAR:start():Warning: XAResource with state "+getStateAsString()+" received diff txId for same Xid:"
							+ "switching transactionId:" + "\nDXAR TXid=" + this.mTransactionId + "\ngot  TXid="
							+ transactionId + "\nFor   Xid=" + printXid(mXid));
					this.mTransactionId = transactionId;
				}
			} else {
				_loggerJX.log(Level.INFO,"DXAR:start():Warning: XAResource with state "+getStateAsString()+" received diff Xid for open txnId:"
								+ "switching transactionId:" + "\nDXAR  Xid=" + printXid(mXid) + "\nDXAR TXid="
								+ this.mTransactionId + "\ngot   Xid=" + printXid(foreignXid) + "\ngot  TXid="
								+ transactionId);
				// remove transactionId and xid as it will be wrong if used
				this.mTransactionId = transactionId;
				this.mXid = new XidImpl(foreignXid); // mqxid;
			}
		}
	}

    /**
     *  Set the enlisted flag on this DirectXAResource
     *
     *  @param  value The boolean indicating whether it is enlisted or not
     */
    public synchronized void setEnlisted(boolean value) {
        this.isEnlisted = value;
        //Only change the enlisted state on the connection if it is not an MDB
        if (!this.usedByMDB  &&  (this.dc != null)) {
            this.dc.setEnlisted(value);
        }
    }

    protected void _setUsedByMDB(boolean value){
        this.usedByMDB = value;
    }

    /**
     *  Get the value of the enlisted flag for this DirectXAResource
     *
     *  @return The enlisted state of this DirectXAResource.
     *          {@code true} If it has been enlisted in an XA transaction.<b>
     *          {@code false} If it currently not enlistedin an XA transaction.
     */
    public synchronized boolean isEnlisted() {
        return this.isEnlisted;
    }
    
    public synchronized void setRollback(boolean state, Throwable cause){
        
    }

    public int _getId(){
        return this.id;
    }

    public long _getTransactionId(){
        return this.mTransactionId;
    }
    
    // Used for debugging only
    private String printXid(Xid foreignXid){
    	return ("(GlobalTransactionID="+foreignXid.getGlobalTransactionId()) +
    	        ", BranchQualifier="+foreignXid.getBranchQualifier()+") ";
    }
    
    private boolean isJoin(int flags){
    	return((flags & XAResource.TMJOIN) == XAResource.TMJOIN);
    }
    
    private boolean isResume(int flags){
    	return((flags & XAResource.TMRESUME) == XAResource.TMRESUME);
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
    
	private String getStateAsString() {
		switch (getResourceState()) {
		case CREATED: // after first creation, or after commit() or rollback()
			return "CREATED";
		case STARTED: // after start() called
			return "STARTED"; 
		case FAILED:  // after end(fail) called
			return "FAILED";
		case INCOMPLETE: // after end(suspend) called
			return "INCOMPLETE"; 
		case COMPLETE: // after end (success) called
			return "COMPLETE"; 
		case PREPARED: // after prepare() called
			return "PREPARED";
		default:
			return Integer.toString(getResourceState());
		}
	}
    
}
