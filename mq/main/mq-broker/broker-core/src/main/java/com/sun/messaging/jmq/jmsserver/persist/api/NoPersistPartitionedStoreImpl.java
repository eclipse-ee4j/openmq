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
package com.sun.messaging.jmq.jmsserver.persist.api;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.util.DestMetricsCounters;
import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.ClusterTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionBroker;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

/**
 */
public class NoPersistPartitionedStoreImpl implements PartitionedStore {

    UID partitionid = null;
    boolean closed = false;

    public NoPersistPartitionedStoreImpl(UID id) {
        partitionid = id;
    }

    public void init(Store store, UID id, boolean isPrimary)
    throws BrokerException {
        throw new UnsupportedOperationException("NoPersistPartitionedStoreImpl.init(,,)");
    }

    public UID getPartitionID() {
        return partitionid;
    }

    /**
     * A broker has 1 only 1 primary partition at any time
     */
    public boolean isPrimaryPartition() {
        return false;
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
    public void storeMessage(DestinationUID dID,
	Packet message, ConsumerUID[] iIDs,
	int[] states, boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeMessage");
    }

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
    public void storeMessage(DestinationUID dID,
	Packet message, boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeMessage");
    }

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
    public void removeMessage(DestinationUID dID,
	SysMessageID mID, boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeMessage");
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
    public void removeMessage(DestinationUID dID,
    		SysMessageID mID, boolean sync, boolean onRollback) 
                throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeMessage");
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
     * @exception IOException if an error occurs while moving the message
     * @exception BrokerException if the message is not found in source
     *		destination
     * @exception NullPointerException	if <code>message</code>, 
     *			<code>fromDID</code>, <code>toDID</code>,
     *			<code>iIDs</code>, or <code>states</code> is
     *			<code>null</code>
     */
    public void moveMessage(Packet message, DestinationUID fromDID,
	DestinationUID toDID, ConsumerUID[] iIDs, int[] states, boolean sync)
	throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeMessage");
    }

    @Override
    public void repairCorruptedSysMessageID(SysMessageID realSysId,
	String badSysIdStr, String duidStr, boolean sync)
	throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":repairCorruptedSysMessageID");
    }

    @Override
    public void removeMessage(DestinationUID dudi,
	String sysid, boolean sync)
	throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeMessage(,String,)");
    }

    /**
     * Remove all messages associated with the specified destination
     * from the persistent store.
     *
     * @param destination   the destination whose messages are to be removed
     * @param sync          if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the messages
     * @exception BrokerException if the destination is not found in the store
     * @exception NullPointerException	if <code>destination</code> is
     *			<code>null</code>
     */
    public void removeAllMessages(Destination destination,
	boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeAllMessages");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":messageEnumeration");
    }

    /**
     * To close an enumeration retrieved from the store
     */ 
    public void closeEnumeration(Enumeration en) {
        throw new UnsupportedOperationException(
        getClass().getName()+":closeEnumeration");
    }

    /**
     * Check if a a message has been acknowledged by all interests (HA support).
     *
     * @param mID   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked(SysMessageID mID)
        throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":hasMessageBeenAcked");
    }

    /**
     * Check if a a message has been acknowledged by all interests.
     * @param dst  the destination the message is associated with
     * @param id   the system message id of the message to be checked
     * @return true if all interests have acknowledged the message;
     * false if message has not been routed or acknowledge by all interests
     * @throws BrokerException
     */
    public boolean hasMessageBeenAcked(DestinationUID dst,
        SysMessageID id) throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":hasMessageBeenAcked");
    }


    /**
     * Return the number of persisted messages for the given broker (HA support).
     *
     * @return the number of persisted messages for the given broker
     * @exception BrokerException if an error occurs while getting the data
     */
    public int getMessageCount() throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getMessageCount");
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
        HashMap map = new HashMap(2);
        map.put(DestMetricsCounters.CURRENT_MESSAGES, Integer.valueOf(0));
        map.put(DestMetricsCounters.CURRENT_MESSAGE_BYTES, Long.valueOf(0L));
        return map;
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getMessage");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getMessage");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":storeInterestStates");
    }
    
    public void storeInterestStates(DestinationUID dID,
    		SysMessageID mID, ConsumerUID[] iIDs, int[] states, boolean sync)
    		throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeInterestStates");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":updateInterestState");
    }

    
    /**
     * @deprecated
     * keep to support tests for old API
     * Now use method with transaction parameter
     */
    public void updateInterestState(DestinationUID dID,
    		SysMessageID mID, ConsumerUID iID, int state,  boolean sync)
    		throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":updateInterestState");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getInterestState");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getInterestStates");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getConsumerUIDs");
    }


    /**
     * Store an Consumer which is uniquely identified by it's id.
     *
     * @param interest  the interest to be persisted
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while persisting the interest
     * @exception BrokerException if an interest with the same id exists in
     *                  the store already
     * @exception NullPointerException  if <code>interest</code> is
     *                  <code>null</code>
     */
    public void storeInterest(Consumer interest, boolean sync)
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeInterest");
    }

    /**
     * Remove the interest from the persistent store.
     *
     * @param interest  the interest to be removed from persistent store
     * @param sync      if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the interest
     * @exception BrokerException if the interest is not found in the store
     */
    public void removeInterest(Consumer interest, boolean sync)
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeInterest");
    }

    /**
     * Retrieve all interests in the store.
     *
     * @return an array of Interest objects; a zero length array is
     * returned if no interests exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Consumer[] getAllInterests()
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getAllInterests");
    }

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
    public void storeDestination(Destination destination, boolean sync)
	throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeDestination");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":updateDestination");
    }


    /**
     * Remove the destination from the persistent store.
     * All messages associated with the destination will be removed as well.
     *

     * @param destination   the destination to be removed
     * @param sync          if true, will synchronize data to disk
     * @exception IOException if an error occurs while removing the destination
     * @exception BrokerException if the destination is not found in the store
     */
    public void removeDestination(Destination destination,
	boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeDestination");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getDestinationConnectedTime");
    }

    /**
     * Retrieve a destination in the store.
     *
     * @param dID the destination ID
     * @return a Destination object or null if not exist
     * @throws BrokerException 
     */
    public Destination getDestination(DestinationUID dID) 
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getDestination");
    }

    /**
     * @return an array of Destination objects; a zero length array is
     * returned if no destinations exist in the store
     * @exception IOException if an error occurs while getting the data
     */
    public Destination[] getAllDestinations()
	throws IOException, BrokerException {
        return new Destination[0];
    }


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
    public void storeTransaction(TransactionUID txnID,
        TransactionState txnState, boolean sync)
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":storeTransaction");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":storeClusterTransaction");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":storeRemoteTransaction");
    }

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
    public void removeTransaction(TransactionUID txnID,
        boolean removeAcks, boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":removeTransaction");
    }

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
    public void updateTransactionState(TransactionUID txnID,
        TransactionState state, boolean sync) throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":updateTransactionState");
    }

    @Override
    public void updateTransactionStateWithWork(TransactionUID txnID,
        TransactionState state, TransactionWork txnwork, boolean sync) 
        throws IOException, BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":updateTransactionStateWithWork");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":updateClusterTransaction");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":updateClusterTransactionBrokerState");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":updateRemoteTransaction");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getTransactionState");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getTransactionUsageInfo");
    }

    /**
     * Return transaction's participant brokers for the specified transaction.
     *
     * @param txnID id of the transaction whose participant brokers are to be returned
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionBroker[] getClusterTransactionBrokers(
        TransactionUID txnID) throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getClusterTransactionBrokers");
    }

    /**
     * Return transaction home broker for the specified remote transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public BrokerAddress getRemoteTransactionHomeBroker(
        TransactionUID txnID) throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getRemoteTransactionHomeBroker");
    }

    /**
     * Return transaction info object for the specified transaction.
     *
     * @param txnID the transaction ID
     * @exception BrokerException if the transaction id is not in the store
     */
    public TransactionInfo getTransactionInfo(TransactionUID txnID)
        throws BrokerException {
        throw new UnsupportedOperationException(
        getClass().getName()+":getTransactionInfo");
    }

    /**
     * Retrieve all local and cluster transaction ids 
     *
     * @return A HashMap. The key of is a TransactionUID.
     * The value of each entry is a TransactionState.
     * @exception BrokerException if an error occurs while getting the data
     */
    public HashMap getAllTransactionStates()
        throws IOException, BrokerException {
        return new HashMap();
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
        throws IOException, BrokerException {
        return new HashMap();
    }

    /**
     * Close the store partition and releases any system resources associated with
     * it. The store partition will be cleaned up. All data files trimed to the
     * length of valid data.
     */
    public void close() {
        closed = true;
    }

    /**
     * Close the store and releases any system resources associated with
     * it.
     * @param cleanup if this is false, the store will not be cleaned up
     *			when it is closed.  The default behavior is that
     *			the store is cleaned up.
     */
    public void close(boolean cleanup) {
        closed = true;
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
        throw new UnsupportedOperationException(
        getClass().getName()+":storeTransactionAck");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":removeTransactionAck");
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
        throw new UnsupportedOperationException(
        getClass().getName()+"::getTransactionAcks");
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
        throw new UnsupportedOperationException(
        getClass().getName()+":getAllTransactionAcks");
    }

    /**
     * Get debug information about the store.
     * @return A Hashtable of name value pair of information
     */
    public Hashtable getDebugState() throws BrokerException {
        Hashtable ht = new Hashtable();
        ht.put("NoPersistPartition", partitionid.toString());
        return ht;
    }

    public LoadException getLoadDestinationException() {
        return null;
    }

    /**
     * Return the LoadException for loading consumers; null if there's none.
     */
    public LoadException getLoadConsumerException() {
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

    public boolean isClosed() {
        return closed;
    }

    public String toString() {
        return "NP["+partitionid+"]";
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

