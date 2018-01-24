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
package com.sun.messaging.jmq.jmsserver.persist.partition.jdbc;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.JDBCStore;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.MessageEnumeration;
import com.sun.messaging.jmq.jmsserver.persist.partition.api.AbstractPartitionedStore;
import com.sun.messaging.jmq.jmsserver.persist.api.TransactionInfo;
import com.sun.messaging.jmq.jmsserver.persist.api.LoadException;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

/**
 */
@Service(name = "com.sun.messaging.jmq.jmsserver.persist.partition.jdbc.JDBCPartitionedStoreImpl")
@PerLookup
public class JDBCPartitionedStoreImpl extends AbstractPartitionedStore {

    /**
     */
    public JDBCPartitionedStoreImpl() {
        super();
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
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     * @exception NullPointerException	if <code>message</code>,
     *			<code>iIDs</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public void storeMessage(DestinationUID dID,
	Packet message, ConsumerUID[] iIDs,
	int[] states, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();

        try {
            ((JDBCStore)parent).storeMessageInternal(dID, message, iIDs, states, 
                               getPartitionID().longValue(), true);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Store a message which is uniquely identified by it's SysMessageID.
     *
     * @param dID	the destination the message is associated with
     * @param message	the readonly packet to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if a message with the same id exists
     *			in the store already
     * @exception NullPointerException	if <code>message</code> is
     *			<code>null</code>
     */
    public void storeMessage(DestinationUID dID,
	Packet message, boolean sync) throws BrokerException {
        storeMessage(dID, message, null, null, sync);
    }

    /**
     * Remove the message from the persistent store.
     * If the message has an interest list, the interest list will be
     * removed as well.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be removed
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException	if <code>dID</code> is
     *			<code>null</code>
     */
    public void removeMessage(DestinationUID dID,
	String id, boolean sync) throws BrokerException {    	 
        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).removeMessageInternal(dID, id, sync, false);
        } finally {
            setInProgress(false);
        }
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
     * @exception BrokerException if the message is not found in the store
     * @exception NullPointerException	if <code>dID</code> is
     *			<code>null</code>
     */
    public void removeMessage(DestinationUID dID,
        SysMessageID mID, boolean sync)
        throws BrokerException {
        removeMessage(dID, mID, sync, false);
    }

    public void removeMessage(DestinationUID dID,
        SysMessageID mID, boolean sync, boolean onRollback)
        throws BrokerException {
        String id = mID.getUniqueName();

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).removeMessageInternal(dID, id, sync, onRollback);
        } finally {
            setInProgress(false);
        }
    }
   
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
     * @exception BrokerException if the message is not found in source
     *		destination
     * @exception NullPointerException	if <code>message</code>, 
     *			<code>fromDID</code>, <code>toDID</code>,
     *			<code>iIDs</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public void moveMessage(Packet message, DestinationUID fromDID,
	DestinationUID toDID, ConsumerUID[] iIDs, int[] states, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).moveMessageInternal(message, fromDID, toDID,
                                       iIDs, states, sync);
        } finally {
            setInProgress(false);
        }
    }

    @Override
    public void repairCorruptedSysMessageID(
        SysMessageID realSysId, String badSysIdStr,
        String duidStr, boolean sync)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).repairCorruptedSysMessageIDInternal(
                                realSysId, badSysIdStr, duidStr, sync);
        } finally {
            setInProgress(false);
        }
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
    public Enumeration messageEnumeration(Destination destination)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).messageEnumerationInternal(destination, 
                          Long.valueOf(getPartitionID().longValue()));
        } catch (BrokerException e) {
            setInProgress(false);
            throw e;
        } finally {
            if (!((JDBCStore)parent).getMessageEnumUseCursor()) {
                setInProgress(false);
            }
        }
    }

    /**
     * To close an enumeration retrieved from the store
     */ 
    public void closeEnumeration(Enumeration en) {
        if (!(en instanceof MessageEnumeration)) {
            return;
        }
        try {
            ((JDBCStore)parent).closeEnumerationInternal(en);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Check if a a message has been acknowledged by all interests.
     * @param dst  the destination the message is associated with
     * @param id   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked(DestinationUID dst, SysMessageID id) 
    throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).hasMessageBeenAckedInternal(dst, id);
        } finally {
            setInProgress(false);
        }
    }

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
    public HashMap getMessageStorageInfo(Destination destination)
    throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getMessageStorageInfoInternal(destination, 
                          Long.valueOf(getPartitionID().longValue()));
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Return the message with the specified system message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dID, String mID)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getMessageInternal(dID, mID); 
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Return the message with the specified system message id.
     *
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message to be retrieved
     * @return a message
     * @exception BrokerException if the message is not found in the store
     *			or if an error occurs while getting the data
     */
    public Packet getMessage(DestinationUID dID, SysMessageID mID)
	throws BrokerException {
        
        if (mID == null) {
            throw new NullPointerException();
        }
        return getMessage(dID, mID.toString());
    }

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
    public void storeInterestStates(DestinationUID dID,
	SysMessageID mID, ConsumerUID[] iIDs, int[] states, boolean sync, Packet msg)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeInterestStatesInternal(dID, mID, iIDs, states, sync, msg);
        } finally {
            setInProgress(false);
        }
    }
    
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
    public void updateInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID, int state, boolean sync, TransactionUID txid, boolean islastAck)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateInterestStateInternal(dID, mID, iID, state, sync, txid, islastAck);
        } finally {
            setInProgress(false);
        }
    }
    
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
    public int getInterestState(DestinationUID dID,
	SysMessageID mID, ConsumerUID iID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getInterestStateInternal(dID, mID, iID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve all interests and states associated with the specified message.
     * @param dID	the destination the message is associated with
     * @param mID	the system message id of the message that the interest
     * @return HashMap of containing all consumer's state
     * @throws BrokerException
     */
    public HashMap getInterestStates(DestinationUID dID,
        SysMessageID mID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getInterestStatesInternal(dID, mID);
        } finally {
            setInProgress(false);
        }
    }

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
    public ConsumerUID[] getConsumerUIDs(
	DestinationUID dID, SysMessageID mID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getConsumerUIDsInternal(dID, mID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Store a Destination.
     *
     * @param destination   the destination to be persisted
     * @param sync	    if true, will synchronize data to disk
     * @exception BrokerException if the same destination exists
     * in the store already
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void storeDestination(Destination destination, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeDestinationInternal(destination, getPartitionID().longValue());
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Update the specified destination.
     *
     * @param destination   the destination to be updated
     * @param sync	    if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     *				or if an error occurs while updating the
     *				destination
     */
    public void updateDestination(Destination destination, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateDestinationInternal(destination);
        } finally {
            setInProgress(false);
        }
    }


    /**
     * Remove the destination from the persistent store.
     * All messages associated with the destination will be removed as well.
     *
     * @param destination   the destination to be removed
     * @param sync          if true, will synchronize data to disk
     * @exception BrokerException if the destination is not found in the store
     */
    public void removeDestination(Destination destination,
	boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).removeDestinationInternal(
                destination, Long.valueOf(getPartitionID().longValue())); 
        } finally {
            setInProgress(false);
        }
    }


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

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getDestinationConnectedTimeInternal(destination); 
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve a destination in the store.
     *
     * @param dID the destination ID
     * @return a Destination object or null if not exist
     * @throws BrokerException 
     */
    public Destination getDestination(DestinationUID dID) 
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getDestinationInternal(dID); 
        } finally {
            setInProgress(false);
        }
    }

    /**
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception BrokerException if an error occurs while getting the data
     */
    public Destination[] getAllDestinations()
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getAllDestinationsInternal(
                       Long.valueOf(getPartitionID().longValue())); 
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Store a transaction.
     *
     * @param txnID	id of the transaction to be persisted
     * @param txnState	the transaction state to be persisted
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the same transaction id exists
     *			the store already
     * @exception NullPointerException	if <code>txnID</code> is
     *			<code>null</code>
     */
    public void storeTransaction(TransactionUID txnID,
        TransactionState txnState, boolean sync)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeTransactionInternal(
             txnID, txnState, sync, getPartitionID().longValue());
        } finally {
            setInProgress(false);
        }
    }

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
    public void storeClusterTransaction(TransactionUID txnID,
        TransactionState txnState, TransactionBroker[] txnBrokers, boolean sync)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeClusterTransactionInternal(
             txnID, txnState, txnBrokers, sync, getPartitionID().longValue());
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Store a remote transaction.
     *
     * @param id	the id of the transaction to be persisted
     * @param txnState	the transaction's state to be persisted
     * @param txnAcks	the transaction's participant brokers
     * @param txnHomeBroker the transaction's home broker
    */
    public void storeRemoteTransaction(TransactionUID id,
        TransactionState txnState, TransactionAcknowledgement[] txnAcks,
        BrokerAddress txnHomeBroker, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeRemoteTransactionInternal(
             id, txnState, txnAcks, txnHomeBroker, sync, getPartitionID().longValue());
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Remove the transaction. The associated acknowledgements
     * will be removed if removeAcks is true.
     *
     * @param txnID	the id of transaction to be removed
     * @param removeAcks if true, will remove all associated acknowledgements
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction is not found
     *			in the store
     */
    public void removeTransaction(TransactionUID txnID,
        boolean removeAcks, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).removeTransactionInternal(txnID, removeAcks, sync);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Update the state of a transaction
     *
     * @param txnID	the transaction id to be updated
     * @param state	the new transaction state
     * @param sync	if true, will synchronize data to disk
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     * @exception NullPointerException	if <code>txnID</code> is
     *			<code>null</code>
     */
    @Override
    public void updateTransactionState(TransactionUID txnID,
        TransactionState state, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateTransactionStateInternal(txnID, state, sync);
        } finally {
            setInProgress(false);
        }
    }

    @Override
    public void updateTransactionStateWithWork(TransactionUID txnID,
        TransactionState state, TransactionWork txnwork, boolean sync)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateTransactionStateWithWorkInternal(
                txnID, state, txnwork, getPartitionID().longValue(), sync);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Update transaction's participant brokers for the specified cluster
     * transaction.
     *
     * @param txnUID       the id of the transaction to be updated
     * @param txnBrokers   the transaction's participant brokers
     * @exception BrokerException if the transaction is not found in the store
     */
    public void updateClusterTransaction(TransactionUID txnUID,
        TransactionBroker[] txnBrokers, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateClusterTransactionInternal(txnUID, txnBrokers, sync);
        } finally {
            setInProgress(false);
        }
    }

    
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
    public void updateClusterTransactionBrokerState(
        TransactionUID txnUID, int expectedTxnState, TransactionBroker txnBroker,
        boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateClusterTransactionBrokerStateInternal(
             txnUID, expectedTxnState, txnBroker, sync);
        } finally {
            setInProgress(false);
        }
    }

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

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).updateRemoteTransactionInternal(
             txnUID, txnAcks, txnHomeBroker, sync);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve the state of a transaction.
     *
     * @param txnID	the transaction id to be retrieved
     * @return the TransactionState
     * @exception BrokerException if the transaction id does NOT exists in
     *			the store already
     */
    public TransactionState getTransactionState(TransactionUID txnID)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getTransactionStateInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

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

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getTransactionUsageInfoInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Return transaction's participant brokers for the specified transaction.
     *
     * @param txnID id of the transaction whose participant brokers are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionBroker[] getClusterTransactionBrokers(
        TransactionUID txnID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getClusterTransactionBrokersInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Return transaction home broker for the specified remote transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public BrokerAddress getRemoteTransactionHomeBroker(
        TransactionUID txnID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getRemoteTransactionHomeBrokerInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Return transaction info object for the specified transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionInfo getTransactionInfo(TransactionUID txnID)
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getTransactionInfoInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve all local and cluster transaction ids 
     *
     * @return A HashMap. The key of is a TransactionUID.
     * The value of each entry is a TransactionState.
     * @exception BrokerException if an error occurs while getting the data
     */
    public HashMap getAllTransactionStates()
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getAllTransactionStatesInternal(
                    Long.valueOf(getPartitionID().longValue()));
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve all remote transaction ids in the store with their state;
     * transactions this broker participates in but doesn't owns.
     *
     * @return A HashMap. The key of is a TransactionUID.
     * The value of each entry is a TransactionState.
     * @exception BrokerException if an error occurs while getting the data
     */
    public HashMap getAllRemoteTransactionStates()
        throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getAllRemoteTransactionStatesInternal(
                    Long.valueOf(getPartitionID().longValue()));
        } finally {
            setInProgress(false);
        }
    }

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
    public void storeTransactionAck(TransactionUID txnID,
	TransactionAcknowledgement txnAck, boolean sync) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).storeTransactionAckInternal(txnID, txnAck, sync);
        } finally {
            setInProgress(false);
        }
    }

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
    public void removeTransactionAck(TransactionUID txnID, boolean sync)
	throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            ((JDBCStore)parent).removeTransactionAckInternal(txnID, sync);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve all acknowledgements for the specified transaction.
     *
     * @param txnID	id of the transaction whose acknowledgements
     *			are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionAcknowledgement[] getTransactionAcks(
	TransactionUID txnID) throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getTransactionAcksInternal(txnID);
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Retrieve all acknowledgement list in the persistence store together
     * with their associated transaction id. The data is returned in the
     * form a HashMap. Each entry in the HashMap has the transaction id as
     * the key and an array of the associated TransactionAcknowledgement
     * objects as the value.
     * @return a HashMap object containing all acknowledgement lists in the
     *		persistence store
     */
    public HashMap getAllTransactionAcks() throws BrokerException {

        checkClosedAndSetInProgress();
        try {
            return ((JDBCStore)parent).getAllTransactionAcksInternal(
                    Long.valueOf(getPartitionID().longValue()));
        } finally {
            setInProgress(false);
        }
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public Hashtable getDebugState()
	throws BrokerException {
        return parent.getDebugState();
    }

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

    /**
     * Close the store and releases any system resources associated with
     * it.
     * @param cleanup if this is false, the store will not be cleaned up
     *			when it is closed.  The default behavior is that
     *			the store is cleaned up.
     */
    public void close(boolean cleanup) {
        logger.log(logger.INFO, "XXXClosing store partition: "+this);
        setClosedAndWait();
        logger.log(logger.INFO, "XXXClosed store partition: "+this);
    }

    @Override
    public String toString() {
        return "["+parent.getStoreType()+":"+partitionid+"]";
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

