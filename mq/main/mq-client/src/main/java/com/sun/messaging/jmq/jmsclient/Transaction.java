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
 *  @(#)Transaction.java	1.41 03/14/08
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Hashtable;
import java.util.logging.Level;

import javax.jms.*;
import javax.transaction.xa.*;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.JMQXid;
import com.sun.messaging.AdministeredObject;

import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * A transacted session deligates it's transaction to this object.
 * An instance of this class is instantiated when a transacted session
 * is created.
 */

public class Transaction extends Object {

    protected SessionImpl session = null;
    protected ProtocolHandler protocolHandler = null;

    protected WriteChannel writeChannel = null;

    //transaction ID - remains invalid until set by START
    private long transactionID = -1;
    
    // Performance optimisation:
    // nextTransactionID returned by commit or rollback
    // This means no need to send a startTransaction method to broker
    private long nextTransactionID = -1;

    //JmqXid
    private JMQXid jmqXid = null;

    //JMSXProducerTXID
    private boolean setJMSXProducerTXID = false;

    private boolean debug = Debug.debug;

    private boolean resetFailOverFlag = false;

    protected static final int TRANSACTION_STARTED= 0;
    protected static final int TRANSACTION_ENDED = 1;
    protected static final int TRANSACTION_PREPARED = 2;
    protected static final int TRANSACTION_COMMITTED = 3;
    
    //HACC -- transaction cannot be committed if transaction is
    //in this state.
    protected static final int TRANSACTION_ROLLBACK_ONLY = 4;
    
    protected static final int TRANSACTION_VERIFY_STATUS_COMMITTED = 7;
    
    protected static final int TRANSACTION_VERIFY_STATUS_ROLLEDBACK = 8;
    
    //set to true if this is a local transaction
    //this is not used at this time, but may be used in the future.
    //protected boolean isLocalTransaction = false;

    protected Transaction(SessionImpl session, boolean startLocal)  throws JMSException {
        this.session = session;
        //this.isLocalTransaction = startLocal;

        this.writeChannel = session.connection.getWriteChannel();

        this.protocolHandler = session.protocolHandler;
        setJMSXProducerTXID = session.connection.connectionMetaData.setJMSXProducerTXID;
        if (startLocal) {

            if ( session.connection.isConnectedToHABroker ) {
                session.protocolHandler.twoPhaseCommitFlag = true;
            }

            startNewLocalTransaction();
        }
    }

    protected void setProtocolHandler(ProtocolHandler pHandler) {
        this.protocolHandler = pHandler;
    }

    /**
     *
     */
     protected void init () throws JMSException {
        startNewLocalTransaction();

        setJMSXProducerTXID = session.connection.connectionMetaData.setJMSXProducerTXID;
     }

    /**
     * Method to handle transacted session, message producer for commit()
     *
     * <p>This will also cause the acked messages be committed.
     */
    protected synchronized void commit() throws JMSException {
    	
    	try {
    		
    		if (session.connection.isConnectedToHABroker) {

				commitHATransaction();
				
				//bug 6423696 - we only clean the Q after commit successfully.
	    		this.session.clearUnackedMessageQ();
				
				this.startHANewLocalTransaction();
				// try {
				// startNewLocalTransaction();
				// } catch (Exception jmse) {
				// The transaction was committed successfully.
				// But can not start a new transaction.
				// The application will get a JMSException for each message sent
				// and received.
				// session.sessionLogger.log (Level.WARNING ,
				// ClientResources.X_TRANSACTION_START_FAILED, jmse);
				// }

			} else {
				// Commit a local transaction - use null xid
				nextTransactionID = protocolHandler.commit(transactionID, -1, null);
				
				//bug 6423696 - we only clean the Q after commit successfully.
	    		this.session.clearUnackedMessageQ();
	    		
				// JMQ implementation requires client to send a new transaction
				// pkt after commit
				startNewLocalTransaction();
			}
    		
    	} catch (JMSException jmse) { 
    		//this will clear unacked q if network error.
    		this.checkCommitException(jmse);
    	} 

    }
    

    /**
     * This clears unacked message queue if 
     * rolled back or network error occurred.
     * 
     * @param jmse 
     * @throws JMSException
     */
    private void checkCommitException (JMSException jmse) throws JMSException {
    	
    	try {
    		
    		if ( jmse instanceof TransactionRolledBackException ) {
    			
    			//broker rolled back the transaction.
    			this.session.clearUnackedMessageQ();
    			
    		} else {
    		
    			String ecode = jmse.getErrorCode();

				if (ClientResources.X_NET_WRITE_PACKET.equals(ecode)
						|| ClientResources.X_NET_ACK.equals(ecode)) {
					this.session.clearUnackedMessageQ();
				}
				
    		}
    	
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	//re-throw the original JMSException
    	throw jmse;
    }


    private void commitHATransaction() throws JMSException {
        resetFailOverFlag = false;

        int tstate = -1;

        try {

            protocolHandler.endHATransaction(transactionID);

            tstate = TRANSACTION_ENDED;

            protocolHandler.prepareHATransaction(transactionID);

            tstate = TRANSACTION_PREPARED;

            protocolHandler.commitHATransaction(transactionID);

            tstate = TRANSACTION_COMMITTED;

            resetFailOverFlag = true;
        } catch (javax.jms.TransactionRolledBackException tre) {
            resetFailOverFlag = true;
            throwRollbackException(tre);
        } catch (JMSException e) {

            String ecode = e.getErrorCode();
            if ( ClientResources.X_NET_WRITE_PACKET.equals(ecode) ||
                 ClientResources.X_NET_ACK.equals(ecode) ) {
                //wait and verify commit status after reconnect
                checkCommitStatus (e, tstate);

                resetFailOverFlag = true;
            } else {
                throw e;
            }

        } finally {
            if ( resetFailOverFlag ) {
                session.failoverOccurred = false;
                resetFailOverFlag = false;
            }
        }
    }

    private void startHANewLocalTransaction () throws JMSException {

        try {
            startNewLocalTransaction();
            if ( session.failoverOccurred ) {
                session.failoverOccurred = false;
            }
        } catch (JMSException jmse) {

            SessionImpl.yield();
            session.connection.checkReconnecting(null);

            if ( session.connection.isCloseCalled || session.connection.connectionIsBroken) {
               throw jmse;
            }

            String ecode = jmse.getErrorCode();

            if ( ClientResources.X_NET_WRITE_PACKET.equals(ecode) ||
                ClientResources.X_NET_ACK.equals(ecode) ) {
                //retry once after reconnect
                //If this failed, we just log the exception and return.
                //This transaction was committed.
                //
                //application must call Session.rollback() to clean up
                //session states in order to
                //successfully commit the next transaction.
                try {

                    startNewLocalTransaction();

                    if ( session.failoverOccurred ) {
                        session.failoverOccurred = false;
                    }

                } catch (JMSException jmse2) {
                    //The transaction was committed successfully.
                    //But can not start a new transaction.
                    //The application will get a JMSException for each message sent
                    //and received.
                    SessionImpl.sessionLogger.log(Level.WARNING,
                    ClientResources.X_TRANSACTION_START_FAILED, jmse2);
                }
            }
        }
    }

    private void checkCommitStatus (JMSException jmse, int tstate) throws JMSException {

        if ( session.connection.imqReconnect == false ) {
            throw jmse;
        }

        SessionImpl.yield();

        session.connection.checkReconnecting(null);

        if ( session.connection.isCloseCalled || session.connection.connectionIsBroken) {
            throw jmse;
        }

        try {
            //verify transaction
            verifyTransaction(tstate);
        } catch (TransactionRolledBackException tre) {
            //set the flag so that we can proceed without calling rollback again.
            this.resetFailOverFlag = true;
            session.failoverOccurred = false;
            if ( debug ) {
                Debug.println("*** in checkCommitStatus(), reset session.failoverOccurred flag to false.");
            }

            throw tre;
        } catch (JMSException jmsexception) {
            //this transaction cannot be resolved.
            throw jmsexception;
        }
    }

    /**
     * XXX Hawk: error message.
     * @throws JMSException
     */
    private void verifyTransaction(int tstate) throws JMSException {

        if ( tstate < TRANSACTION_ENDED ) {
            //transaction is not prepared, broker roll back transaction
            //throw roll back exception
            this.createAndThrowFailoverRollbackException();
        }

        int state = protocolHandler.verifyHATransaction(transactionID, tstate);

        switch (state) {

        case 7:
            //commit successfully
            if (debug) {
                Debug.println("transaction verified: state is successful");
            }
            return;

        case 6:
            //transaction is in prepared state.  ask broker to commit.
            try {
                //protocolHandler.rollback(this.transactionID);
            	SessionImpl.sessionLogger.log(Level.INFO, "verifyTransaction(): transaction is in prepred state, committing the transaction: " + this.transactionID);
            	this.protocolHandler.commitHATransaction(this.transactionID);
            	SessionImpl.sessionLogger.log(Level.INFO, "verifyTransaction(): prepared transaction committed successfully: " + this.transactionID);
            	//done if we can commit.
            	return;
            	
            } catch (JMSException jmse) {
                //in case this failed, we try one more time.
                //This is the third failure at this point.
                //If failed again, we log this and throw Exception.
                if ( session.connection.imqReconnect == false ) {
                   throw jmse;
                }

                SessionImpl.yield();

                session.connection.checkReconnecting(null);

                if ( session.connection.isCloseCalled || session.connection.connectionIsBroken) {
                   throw jmse;
                }
                //we should rollback at this point.
                protocolHandler.rollback(this.transactionID);
            }
            
            //fall through and throw TransactionRolledBackException.
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
            this.createAndThrowFailoverRollbackException();
        }
    }

    private void
        createAndThrowFailoverRollbackException()
        throws TransactionRolledBackException {

        String errorString = AdministeredObject.cr.getKString(
        		ClientResources.X_TRANSACTION_FAILOVER_OCCURRED);

        TransactionRolledBackException tre =
            new TransactionRolledBackException(errorString,
                                               ClientResources.
                                               X_TRANSACTION_FAILOVER_OCCURRED);

        throwRollbackException(tre);
    }

    private void throwRollbackException (TransactionRolledBackException tre)
                                       throws TransactionRolledBackException {

       try {
           startNewLocalTransaction();
       } catch (Exception e) {
           //mark transaction as invalid
           this.transactionID = -1;
           Debug.printStackTrace(e);
       }

       throw tre;

   }



    /**
     * Call by Session.rollback().
     *
     * <p>This destroy all messages sent to the broker.
     */
    protected synchronized void rollback() throws JMSException {
        //don't send the protocol if fail-over just occurred.
        //broker will not know the current transactionID (belong to
        //the failed broker).
        if ( session.failoverOccurred == false ) {
            protocolHandler.rollback(transactionID);
        } else {
            /**
             * failover flag is still true.  this means that no messages
             * were produced (app will get exception if attempt to produce
             * messages).  messages may be consumed but the ack would have
             * failed (either threw JMSException to Message.receive() or
             * caught by client runtime if there is a message listener).
             *
             * When app called Session.rollback() after failover, client
             * runtime only needs to handle consumer side rollback since
             * there were no messages can be produced.
             *
             * The current transactionID is no longer valid to the failover
             * broker.  Sending a rollback pkt will cause broker to throw an
             * internal error exception.
             *
             * Client runtime sent redeliver pkts to the broker in
             * SessionImpl.rollback() before this method is called.
             *
             * We simply required to send a START_TRANSACTION packet to
             * broker in this condition.
             */
            Debug.println("*** rollback pkt not sent because failover occurred.");
        }

        //JMQ implementation requires client to send a new transaction
        //pkt after rollback
        startNewLocalTransaction();
    }

    /**
     * called when switching from a local txn to a glbl txn
     *
     */
    protected void rollbackToXA() throws JMSException {
        protocolHandler.rollback(transactionID);
        //No need to send txn start - will be sent by XA start
        //Do need to reset txnID, so that we get a new txnID on start
        transactionID = -1;
    }

    /*
     * Send a transacted message to the broker.
     */
    protected void send(Message message, AsyncSendCallback asynccb)
    throws JMSException {
         if (asynccb != null) {
             asynccb.setTransactionID(transactionID);
         }

         MessageImpl messageImpl = (MessageImpl) message;

         //set property if requested.
         if ( setJMSXProducerTXID ) {
            messageImpl.setStringProperty(
                ConnectionMetaDataImpl.JMSXProducerTXID,
                String.valueOf(transactionID)
            );
         }
         //System.out.println("Txn:send:msg with TxnID="+transactionID);
         ReadWritePacket pkt = messageImpl.getPacket();
         pkt.setTransactionID(transactionID);
         writeChannel.writeJMSMessage(message, asynccb);
    }

    /**
     * recover XA Transactions (returns Xids)
     */
    protected JMQXid[] recoverXATransactions(int flags) throws JMSException {
        return protocolHandler.recover(flags);
    }

    /**
     * prepare an XA Transaction
     */
    protected void prepareXATransaction(JMQXid xid) throws JMSException {
        protocolHandler.prepare(( (jmqXid != null && jmqXid.equals(xid))
                                  ? transactionID
                                  : 0L
                                ), xid);
    }

    /**
     * commit an XA Transaction
     */
    protected void commitXATransaction(JMQXid xid, boolean onePhase) throws JMSException {
        int flags;
        
        try {

			flags = onePhase ? XAResource.TMONEPHASE : XAResource.TMNOFLAGS;
			protocolHandler.commit(
					((jmqXid != null && jmqXid.equals(xid)) ? transactionID
							: 0L), flags, xid);

			// bug 6423696 - we only clean the Q after commit successfully.
			this.session.clearUnackedMessageQ();
			
		} catch (JMSException jmse) {
			// this clears unack q if network error.
			this.checkCommitException(jmse);
		}
    }

    /**
	 * rollback an XA Transaction
	 */
    protected void rollbackXATransaction(JMQXid xid) throws JMSException {
        protocolHandler.rollback(( (jmqXid != null && jmqXid.equals(xid))
                                   ? transactionID
                                   : 0L
                                 ), xid);
    }

    /**
     * start a new XA transaction
     */
    protected void startXATransaction(int xaflags, JMQXid xid) throws JMSException {
        startTransaction(xaflags, xid);
    }

    /**
     * end an XA transaction
     */
    protected void endXATransaction(int xaflags, JMQXid xid) throws JMSException {
        protocolHandler.endTransaction(( (jmqXid != null && jmqXid.equals(xid))
                                         ? transactionID
                                         : 0L
                                       ), xaflags, xid);
        //Do not start a new local transaction
        //Note that this does not support a Session migrating back from a glbl txn to
        //a local txn
        //XXX: TBF when Session protocol changes in 3.1
        //startNewLocalTransaction();
    }

    /**
     * start a new Local transaction
     */
    protected void startNewLocalTransaction() throws JMSException {
        //reset transactionID so a new one can be obtained
        transactionID = -1;
        jmqXid = null;
        //Pass -1 for flags, null for xid
        startTransaction(-1, null);
    }

    /**
     * start a new transaction
     */
    protected synchronized void startTransaction(int flags, JMQXid xid) throws JMSException {
    	 
    	if(nextTransactionID != -1)
         {
    		// Performance optimisation.
         	// A new transaction has already been started by the broker
         	// at the end of commit or rollback.
    		// So no need to send a start message.
         	transactionID = nextTransactionID;
         	nextTransactionID = -1;
         	return;
         }
     	
     	if (transactionID == -1){
            //System.out.println("Txn:strtTxn:getting a new Txn ID");
            //found new transaction id?
            boolean found = false;
            while ( !found ) {
                try {
                    //send to broker. In Falcon broker generates transactionID
                    if (xid == null) {
                        //XXX PROTOCOL3.5
                        // Send the brokerSessionID for local
                        // transactions only..
                    	
                    	//bug6664213 --
                    	//client should pass JMQTransactionID=0 to broker on 
                    	//start TMJOIN
                        //transactionID = protocolHandler.startTransaction(
                        //    getNextTransactionID(), flags, xid,
                        //    session.getBrokerSessionID());
                        transactionID = protocolHandler.startTransaction(
                                0, flags, xid,
                                session.getBrokerSessionID());
                    }
                    else {
                    	//bug6664213 --
                    	//client should pass JMQTransactionID=0 to broker on 
                    	//start TMJOIN
                        //transactionID = protocolHandler.startTransaction(
                        //    getNextTransactionID(), flags, xid);
                        
                        transactionID = protocolHandler.startTransaction(
                                0, flags, xid);
                    }
                    found = true;
                } catch ( JMSException jmse ) {
                    //if ID is in use, we keep trying until we get one.
                    String errorCode = jmse.getErrorCode();
                    if ( errorCode != ClientResources.X_TRANSACTION_ID_INUSE ) {
                        //System.out.println("Txn:strtTxn:got exception"+jmse.getMessage());
                        //if error code is not conflict, we rethrow exception.
                        throw jmse;
                    }
                }
            }
            if (xid != null) {
                //Set local jmqXid to invalidate transactionID if not same in subsequent methods
                jmqXid = xid;
            }
        } else {
            try {
                transactionID = protocolHandler.startTransaction(( (jmqXid != null && jmqXid.equals(xid))
                                                                   ? transactionID
                                                                   : 0L
                                                                 ), flags, xid);
                if (xid != null) {
                    //Set local jmqXid to invalidate transactionID if not same in subsequent methods
                    jmqXid = xid;
                }
            } catch (JMSException ste) {
                //System.out.println("Txn:strtTxn:got Exception"+ste.getMessage());
                throw ste;
            }
        }

        if ( debug ) {
            Debug.println("*** in Transaction.startTransaction(), new txID: " + this.transactionID);
        }
    }

    /**
     *
     */
    protected void releaseBrokerResource() throws JMSException {
        //Local transactions only
        protocolHandler.rollback (transactionID, true);
    }

    /**
     * Get next available transaction ID.  Each transaction is required
     * to have a new transaction ID.  This ID is unique in the space of
     * client ID (must be unique per connection).
     */
    /*
    private int getNextTransactionID() throws JMSException {
        return session.connection.getNextTransactionID();
    }
    */

    /**
     * get the current transactionID
     */
    protected synchronized long getTransactionID() {
        return transactionID;
    }

    /**
     * set the current transactionID - used by SessionImpl
     * under ManagedConnection scenario
     */
    public synchronized void setTransactionID(long transactionID) {
        this.transactionID = transactionID;
    }
    
    
}
