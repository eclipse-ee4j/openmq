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
 */ 
package com.sun.messaging.jmq.jmsserver.persist.partition.api;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.util.log.Logger;
import org.jvnet.hk2.annotations.Contract;
import org.glassfish.hk2.api.PerLookup;

/**
 */
@Contract
@PerLookup
public abstract class AbstractPartitionedStore implements PartitionedStore {

    protected static final Logger logger = Globals.getLogger();
    protected static final BrokerResources br = Globals.getBrokerResources();

    /**
     * Variables to make sure no Store method is called after the
     * store is closed and that the close operation won't start
     * until all store operations are done.
     */
    // boolean flag indicating whether the store is closed or not
    protected boolean closed = false;
    private Object closedLock = new Object();	// lock for closed

    // number indicating the number of store operations in progress
    private int inprogressCount = 0;
    private Object inprogressLock = new Object(); // lock for inprogressCount

    protected Store parent = null;
    protected UID partitionid = null; 
    protected boolean isPrimary = false;

    /**
     */
    public AbstractPartitionedStore() {
    }

    public void init(Store store, UID id, boolean isPrimary)
    throws BrokerException {
        this.parent = store;
        this.partitionid = id;
        this.isPrimary = isPrimary;
    }


    public UID getPartitionID() {
        return partitionid;
    }

    public boolean isPrimaryPartition() {
        return isPrimary;
    }

    /**
     * Store a message, which is uniquely identified by it's SysMessageID,
     * and it's list of interests and their states.
     *
     * @param dID	the destination the message is associated with
     * @param message	the message to be persisted
     * @param iIDs	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the data
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     * @exception NullPointerException	if <code>message</code>,
     *			<code>iIDs</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public abstract void storeMessage(DestinationUID dID,
	Packet message, ConsumerUID[] iIDs,
	int[] states, boolean sync) throws IOException, BrokerException;

    /**
     * Store a message which is uniquely identified by it's SysMessageID.
     *
     * @param dID	the destination the message is associated with
     * @param message	the readonly packet to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the message
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     * @exception NullPointerException	if <code>message</code> is
     *			<code>null</code>
     */
    public abstract void storeMessage(DestinationUID dID,
	Packet message, boolean sync) throws IOException, BrokerException;

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException	if <code>dID</code> is
     *			<code>null</code>
     */
    @Override
    public void removeMessage(DestinationUID dID,
        SysMessageID mID, boolean sync) 
        throws IOException, BrokerException {    	 
    	  removeMessage(dID, mID, sync, false);    	    
    }

    /**
     * subclass that support this will override
     */
    @Override
    public void removeMessage(DestinationUID dID,
        String id, boolean sync) 
        throws IOException, BrokerException {    	 
        throw new UnsupportedOperationException(
            "Operation not supported by the " + parent.getStoreType() + " store" );
    }

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be removed
     * @param sync	if true, will synchronize data to disk
     * @param onRollback if true, removal is being requested as part of a transaction rollback
     * @exception IOException if an error occurs while removing the message
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException	if <code>dID</code> is
     *			<code>null</code>
     */
    @Override
    public abstract void removeMessage(DestinationUID dID,
    		SysMessageID mID, boolean sync, boolean onRollback) throws IOException, BrokerException;
   
    /**
     * Move the message from one destination to another.
     * The message will be stored in the target destination with the
     * passed in consumers and their corresponding states.
     * After the message is persisted successfully, the message in the
     * original destination will be removed.
     *
     * @param message	the message to be moved
     * @param fromDID	the destination the message is currently in
     * @param toDID	the destination to move the message to
     * @param iIDs	an array of interest ids whose states are to be
     *			stored with the message
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while moving the message
     * @exception BrokerException if the message is not found in source
     *		destination
     * @exception NullPointerException	if <code>message</code>, 
     *			<code>fromDID</code>, <code>toDID</code>,
     *			<code>iIDs</code>, or <code>states</code> is
     *			<code>null</code>
     */
    @Override
    public abstract void moveMessage(Packet message, DestinationUID fromDID,
	DestinationUID toDID, ConsumerUID[] iIDs, int[] states, boolean sync)
	throws IOException, BrokerException;

    /**
     * Update the corrected SysMessageID to the real SysMessageID
     * in MQMSG table
     *
     * Subclass supports this will override
     *
     * @param realSysId  The real SysMessageID
     * @param badSysIdStr The corrected SysMessageID string
     * @param dudiStr  The destination UID 
     *
     */
    @Override
    public void repairCorruptedSysMessageID(
	SysMessageID realSysId, String badSysIdStr,
	String duidStr, boolean sync)
	throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + parent.getStoreType() + " store" );
    }

    /**
     * Return an enumeration of all persisted messages for the given
     * destination.
     * Use the Enumeration methods on the returned object to fetch
     * and load each message sequentially.
     *
     * <p>
     * This method is to be used at broker startup to load persisted
     * messages on demand.
     *
     * @param destination   the destination whose messages are to be returned
     * @return an enumeration of all persisted messages, an empty
     *		enumeration will be returned if no messages exist for the
     *		destionation
     * @exception BrokerException if an error occurs while getting the data
     */
    public abstract Enumeration messageEnumeration(Destination destination)
	throws BrokerException;

    /**
     * To close an enumeration retrieved from the store
     */ 
    public void closeEnumeration(Enumeration en) {
    }

    /**
     * Check if a a message has been acknowledged by all interests.
     * @param dst  the destination the message is associated with
     * @param id   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public abstract boolean hasMessageBeenAcked(DestinationUID dst,
        SysMessageID id) throws BrokerException;

    /**
     * Return the number of persisted messages and total number of bytes for
     * the given destination. The constant DestMetricsCounters.CURRENT_MESSAGES
     * and DestMetricsCounters.CURRENT_MESSAGE_BYTES will be used as keys for
     * the HashMap.
     *
     * @param destination the destination whose messages are to be counted
     * @return A HashMap of name value pair of information
     * @throws BrokerException if an error occurs while getting the data
     */
    public abstract HashMap getMessageStorageInfo(Destination destination)
    throws BrokerException;


    /**
     * Return the message with the specified system message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public abstract Packet getMessage(DestinationUID dID, String mID)
	throws BrokerException;

    /**
     * Return the message with the specified system message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public abstract Packet getMessage(DestinationUID dID, SysMessageID mID)
	throws BrokerException;

    /**
     * Store the given list of interests and their states with the
     * specified message.  The message should not have an interest
     * list associated with it yet.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message that the interest
     *			is associated with
     * @param iIDs	an array of interest ids whose states are to be stored
     * @param states	an array of states
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not in the store;
     *				if there's an interest list associated with
     *				the message already; or if an error occurs
     *				while persisting the data
     */
    public abstract void storeInterestStates(DestinationUID dID,
	SysMessageID mID, ConsumerUID[] iIDs, int[] states, boolean sync, Packet msg)
	throws BrokerException;
    
    /**
     * Update the state of the interest associated with the specified
     * message.  The interest should already be in the interest list
     * of the message.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message that the interest
     *			is associated with
     * @param iID	the interest id whose state is to be updated
     * @param state	state of the interest
     * @param sync	if true, will synchronize data to disk
     * @param txid	txId if in a transaction, otherwise null
     * @param isLastAck	Is this the last ack for this message. 
     * @exception BrokerException if the message is not in the store; if the
     *			interest is not associated with the message; or if
     *			an error occurs while persisting the data
     */
    public abstract void updateInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID, int state, boolean sync, TransactionUID txid, boolean islastAck)
	throws BrokerException;

    /**
     * Get the state of the interest associated with the specified message.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message that the interest
     *			is associated with
     * @param iID	the interest id whose state is to be returned
     * @return state of the interest
     * @exception BrokerException if the specified interest is not
     *		associated with the message; or if the message is not in the
     *		store
     */
    public abstract int getInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID) throws BrokerException;

    /**
     * Retrieve all interests and states associated with the specified message.
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message that the interest
     * @return HashMap of containing all consumer's state
     * @throws BrokerException
     */
    public abstract HashMap getInterestStates(DestinationUID dID,
        SysMessageID mID) throws BrokerException;

    /**
     * Retrieve all interest IDs associated with the message
     * <code>mID</code> in destination <code>dID</code>.
     * Note that the state of the interests returned is either
     * INTEREST_STATE_ROUTED or INTEREST_STATE_DELIVERED, and interest
     * whose state is INTEREST_STATE_ACKNOWLEDGED will not be returned in
     * the array.
     *
     * @param dID   the destination the message is associated with
     * @param mID   the system message id of the message whose interests
     *			are to be returned
     * @return an array of ConsumerUID objects associated with the message; a
     *		zero length array will be returned if no interest is
     *		associated with the message
     * @exception BrokerException if the message is not in the store or if
     *				an error occurs while getting the data
     */
    public abstract ConsumerUID[] getConsumerUIDs(
	DestinationUID dID, SysMessageID mID) throws BrokerException;


    /**
     * Store a Destination.
     *
     * @param destination   the destination to be persisted
     * @param sync	    if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the destination
     * @exception BrokerException if the same destination exists
     * in the store already
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public abstract void storeDestination(Destination destination, boolean sync)
	throws IOException, BrokerException;

    /**
     * Update the specified destination.
     *
     * @param destination   the destination to be updated
     * @param sync	    if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     *				or if an error occurs while updating the
     *				destination
     */
    public abstract void updateDestination(Destination destination, boolean sync)
	throws BrokerException;


    /**
     * Remove the destination from the persistent store.
     * All messages associated with the destination will be removed as well.
     *

     * @param destination   the destination to be removed
     * @param sync          if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the destination
     * @exception BrokerException if the destination is not found in the store
     */
    public abstract void removeDestination(Destination destination,
	boolean sync) throws IOException, BrokerException;


    /**
     * Retrieve the timestamp when a consumer (owner of the connection that
     * creates this temporary destination) connected/re-attached to a
     * temporary destination or when it was created (HA support).
     *
     * @param destination   the temporary destination
     * @return the timestamp
     * @exception BrokerException if the destination is not found in the store
     *            or if an error occurs while updating the destination
     */
    public long getDestinationConnectedTime(Destination destination)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + parent.getStoreType() + " store" );
    }

    /**
     * Retrieve a destination in the store.
     *
     * @param dID the destination ID
     * @return a Destination object or null if not exist
     * @throws BrokerException 
     */
    public abstract Destination getDestination(DestinationUID dID) 
        throws IOException, BrokerException;

    /**
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public abstract Destination[] getAllDestinations()
	throws IOException, BrokerException;

    /**
     * Store a transaction.
     *
     * @param txnID	id of the transaction to be persisted
     * @param txnState	the transaction state to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting
     *		the transaction
     * @exception BrokerException if the same transaction id exists
     *			the store already
     * @exception NullPointerException	if <code>txnID</code> is
     *			<code>null</code>
     */
    public abstract void storeTransaction(TransactionUID txnID,
        TransactionState txnState, boolean sync)
        throws IOException, BrokerException;

    /**
     * Store a cluster transaction.
     *
     * @param txnID	the id of the transaction to be persisted
     * @param txnState	the transaction's state to be persisted
     * @param txnBrokers the transaction's participant brokers
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the same transaction id exists
     *			the store already
     * @exception NullPointerException	if <code>txnID</code> is
     *			<code>null</code>
     */
    public abstract void storeClusterTransaction(TransactionUID txnID,
        TransactionState txnState, TransactionBroker[] txnBrokers, boolean sync)
        throws BrokerException;

    /**
     * Store a remote transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param txnState	the transaction's state to be persisted
     * @param txnAcks	the transaction's participant brokers
     * @param txnHomeBroker the transaction's home broker
    */
    public abstract void storeRemoteTransaction(TransactionUID id,
        TransactionState txnState, TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException;

    /**
     * Remove the transaction. The associated acknowledgements
     * will be removed if removeAcks is true.
     *
     * @param txnID	the id of transaction to be removed
     * @param removeAcks if true, will remove all associated acknowledgements
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the transaction
     * @exception BrokerException if the transaction is not found
     *			in the store
     */
    public abstract void removeTransaction(TransactionUID txnID,
        boolean removeAcks, boolean sync) throws IOException, BrokerException;

    /**
     * Update the state of a transaction
     *
     * @param txnID	the transaction id to be updated
     * @param state	the new transaction state
     * @param sync	if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting
     *		the transaction id
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     * @exception NullPointerException	if <code>txnID</code> is
     *			<code>null</code>
     */
    @Override
    public abstract void updateTransactionState(TransactionUID txnID,
        TransactionState state, boolean sync) throws IOException, BrokerException;

    @Override
    public abstract void updateTransactionStateWithWork(TransactionUID txnID,
        TransactionState state, TransactionWork txnwork, boolean sync) 
        throws IOException, BrokerException;

    /**
     * Update transaction's participant brokers for the specified cluster
     * transaction.
     *
     * @param txnUID       the id of the transaction to be updated
     * @param txnBrokers   the transaction's participant brokers
     * @exception BrokerException if the transaction is not found in the store
     */
    public abstract void updateClusterTransaction(TransactionUID txnUID,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException;

    
    /**
     * Update transaction's participant broker state for the specified cluster
     * transaction if the txn's state matches the expected state.
     *
     * @param txnUID the id of the transaction to be updated
     * @param expectedTxnState the expected transaction state
     * @param txnBroker the participant broker to be updated
     * @exception BrokerException if the transaction is not found in the store
     * or the txn's state doesn't match the expected state (Status.CONFLICT)
     */
    public abstract void updateClusterTransactionBrokerState(
        TransactionUID txnUID, int expectedTxnState, TransactionBroker txnBroker,
        boolean sync) throws BrokerException;

    /**
     * Update the transaction home broker for the specified remote transaction
     * (HA support).
     *
     * In HA mode, the txn is owned by another broker so we'll only update
     * the txn home broker.

     * @param txnUID the transaction ID
     * @param txnHomeBroker the home broker for a REMOTE txn
     * @throws BrokerException if transaction does not exists in the store
     */
    public void updateRemoteTransaction(TransactionUID txnUID, 
        TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + parent.getStoreType() + " store" );
    }

    /**
     * Retrieve the state of a transaction.
     *
     * @param txnID	the transaction id to be retrieved
     * @return the TransactionState
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     */
    public abstract TransactionState getTransactionState(TransactionUID txnID)
        throws BrokerException;

    /**
     * Return the number of messages and the number of consumer states that
     * that associate with the specified transaction ID (HA support).
     *
     * @param txnID the transaction ID
     * @return an array of int whose first element contains the number of messages
     * and the second element contains the number of consumer states.
     * @exception BrokerException if an error occurs while getting the data
     */
    public int[] getTransactionUsageInfo(TransactionUID txnID)
        throws BrokerException {
        throw new UnsupportedOperationException(
            "Operation not supported by the " + parent.getStoreType() + " store" );
    }

    /**
     * Return transaction's participant brokers for the specified transaction.
     *
     * @param txnID id of the transaction whose participant brokers are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public abstract TransactionBroker[] getClusterTransactionBrokers(
        TransactionUID txnID) throws BrokerException;

    /**
     * Return transaction home broker for the specified remote transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public abstract BrokerAddress getRemoteTransactionHomeBroker(
        TransactionUID txnID) throws BrokerException;

    /**
     * Return transaction info object for the specified transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public abstract TransactionInfo getTransactionInfo(TransactionUID txnID)
        throws BrokerException;

    /**
     * Retrieve all local and cluster transaction ids 
     *
     * @return A HashMap. The key of is a TransactionUID.
     * The value of each entry is a TransactionState.
     * @exception BrokerException if an error occurs while getting the data
     */
    public abstract HashMap getAllTransactionStates()
        throws IOException, BrokerException;

    /**
     * Retrieve all remote transaction ids in the store with their state;
     * transactions this broker participates in but doesn't owns.
     *
     * @return A HashMap. The key of is a TransactionUID.
     * The value of each entry is a TransactionState.
     * @exception BrokerException if an error occurs while getting the data
     */
    public abstract HashMap getAllRemoteTransactionStates()
        throws IOException, BrokerException;

    /**
     * Close the store partition and releases any system resources associated with
     * it. The store partition will be cleaned up. All data files trimed to the
     * length of valid data.
     */
    public void close() {
	close(true);
    }

    /**
     * Close the store and releases any system resources associated with
     * it.
     * @param cleanup if this is false, the store will not be cleaned up
     *			when it is closed.  The default behavior is that
     *			the store is cleaned up.
     */
    public abstract void close(boolean cleanup);

    /**
     * Store the acknowledgement for the specified transaction.
     *
     * @param txnID	the transaction id with which the acknowledgment is to
     *			be stored
     * @param txnAck	the acknowledgement to be stored
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction id is not found in the
     *				store, if the acknowledgement already
     *				exists, or if it failed to persist the data
     */
    public abstract void storeTransactionAck(TransactionUID txnID,
	TransactionAcknowledgement txnAck, boolean sync) throws BrokerException;

    /**
     * Remove all acknowledgements associated with the specified
     * transaction from the persistent store.
     *
     * @param txnID	the transaction id whose acknowledgements are
     *			to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if error occurs while removing the
     *			acknowledgements
     */
    public abstract void removeTransactionAck(TransactionUID txnID, boolean sync)
	throws BrokerException;

    /**
     * Retrieve all acknowledgements for the specified transaction.
     *
     * @param txnID	id of the transaction whose acknowledgements
     *			are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public abstract TransactionAcknowledgement[] getTransactionAcks(
	TransactionUID txnID) throws BrokerException;

    /**
     * Retrieve all acknowledgement list in the persistence store together
     * with their associated transaction id. The data is returned in the
     * form a HashMap. Each entry in the HashMap has the transaction id as
     * the key and an array of the associated TransactionAcknowledgement
     * objects as the value.
     * @return a HashMap object containing all acknowledgement lists in the
     *		persistence store
     */
    public abstract HashMap getAllTransactionAcks() throws BrokerException;

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public abstract Hashtable getDebugState()
	throws BrokerException;

    public LoadException getLoadDestinationException() {
	return null;
    }

    /**
     * Return the LoadException for loading transactions; null if there's none.
     */
    public LoadException getLoadTransactionException() {
	return null;
    }

    /**
     * Return the LoadException for loading transaction acknowledgements;
     * null if there's none.
     */
    public LoadException getLoadTransactionAckException() {
	return null;
    }

    // all close() and close(boolean) implemented by subclasses should
    // call this first to make sure all store operations are done
    // before preceeding to close the store.
    protected void setClosedAndWait() {
	// set closed to true so that no new operation will start
	synchronized (closedLock) {
	    closed = true;
	}

    beforeWaitOnClose();

	// wait until all current store operations are done
	synchronized (inprogressLock) {
	    while (inprogressCount > 0) {
		try {
		    inprogressLock.wait();
		} catch (Exception e) {
		}
	    }
	}
    }

    protected void beforeWaitOnClose() {
    }

    /**
     * This method should be called by all store apis to make sure
     * that the store is not closed before doing the operation.
     * @throws BrokerException
     */
    protected void checkClosedAndSetInProgress() throws BrokerException {
        checkClosedAndSetInProgress(Logger.ERROR); 
    }

    protected void checkClosedAndSetInProgress(int loglevel) throws BrokerException {
	synchronized (closedLock) {
	    if (closed) {
            logger.log(loglevel, BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED);
            throw new BrokerException(
                br.getString(BrokerResources.E_STORE_ACCESSED_AFTER_CLOSED),
                Status.NOT_ALLOWED);
	    } else {
            //increment inprogressCount
            setInProgress(true);
	    }
	}
    }

    /**
     * If the flag is true, the inprogressCount is incremented;
     * if the flag is false, the inprogressCount is decremented;
     * when inprogressCount reaches 0; it calls notify on inprogressLock
     * to wait up anyone waiting on that
     *
     * @param flag
     */
    protected void setInProgress(boolean flag) {
	synchronized (inprogressLock) {
	    if (flag) {
		inprogressCount++;
	    } else {
		inprogressCount--;
	    }

	    if (inprogressCount == 0) {
		inprogressLock.notifyAll();
	    }
	}
    }

    // used internally to check whether the store is closed
    public boolean isClosed() {
	synchronized (closedLock) {
	    return closed;
	}
    }

    public String toString() {
        return "StorePartition["+partitionid+"]closed="+closed;
    }

    public int hashCode() {
        return partitionid.hashCode();
    }

    public boolean equals(Object anObject) { 
        if (this == anObject) {
            return true;
	}
        if (!(anObject instanceof PartitionedStore)) {
            return false;
        }
        return getPartitionID().equals(
            ((PartitionedStore)anObject).getPartitionID());
    }
}

