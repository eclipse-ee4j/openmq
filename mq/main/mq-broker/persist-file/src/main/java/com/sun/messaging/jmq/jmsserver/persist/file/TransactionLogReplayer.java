/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationList;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.core.MessageDeliveryTimeInfo;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessageAck;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.lists.RemoveReason;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.selector.SelectorFormatException;

public class TransactionLogReplayer 
{
	
	public static final Logger logger = Globals.getLogger();

	MsgStore msgStore;

	public TransactionLogReplayer(MsgStore msgStore) {
		this.msgStore = msgStore;
	}

	String getPrefix() {
		return "TransactionLogReplayer: " + Thread.currentThread().getName();
	}
	
	void replayTransactionWork(TransactionWork txnWork, TransactionUID tid,
			Set dstLoadedSet) throws IOException, BrokerException {

		if (Store.getDEBUG()) {
			String msg = getPrefix() + " replayTransactionWork for tid " + tid
					+ " txnWork=" + txnWork;
			logger.log(Logger.INFO, msg);
		}
		replaySentMessages(txnWork.getSentMessages(), dstLoadedSet);
		replayAcknowledgedMessages(txnWork.getMessageAcknowledgments(),
				dstLoadedSet);

	}
	
    private void replaySentMessages(List<TransactionWorkMessage> sentMessages,
                                    Set dstLoadedSet) 
                                    throws IOException, BrokerException {
        if (Store.getDEBUG()) {
            logger.log(Logger.INFO, getPrefix() + " replaySentMessages");
        }

        for (int i = 0; i < sentMessages.size(); i++) {
             TransactionWorkMessage workMessage = sentMessages.get(i);
             replaySentMessage(workMessage, dstLoadedSet);
        }
    }
	
    private void replaySentMessage(TransactionWorkMessage workMessage,
                                   Set dstLoadedSet) 
                                   throws IOException, BrokerException {

        // Reconstruct the message
        Packet pkt = workMessage.getMessage();
        SysMessageID mid = pkt.getSysMessageID();
        if (Store.getDEBUG()) {
            String msg = getPrefix() + " replaying sent message: "
                         + workMessage + " dest= "+pkt.getDestination();
            logger.log(Logger.INFO, msg);
        }
        // Make sure destination exists; auto-create if necessary
        // TODO
        // recreating destinations may cause a bug if a destination had been
        // deleted by a command after the message was logged.
        // It will now reappear - (as an auto destination?)
		
        String dname = pkt.getDestination();
        int dtype = (pkt.getIsQueue() ? DestType.DEST_TYPE_QUEUE : DestType.DEST_TYPE_TOPIC);

        DestinationList DL= Globals.getDestinationList();
        List[] dss = DL.findMatchingIDs(msgStore.parent, DestinationUID.getUID(dname, dtype));
        List dlist = dss[0];

        DestinationUID did = null;
        Iterator itr = dlist.iterator();
        while (itr.hasNext()) {
            did = (DestinationUID)itr.next();
            Destination[] ds = DL.getDestination(msgStore.parent, did, dtype, true, true); 
            Destination dst = ds[0];
            did = dst.getDestinationUID();

            // Load all msgs in order to verify if any msgs are missing
            if (!dstLoadedSet.contains(dst)) {
                dst.load();
                dstLoadedSet.add(dst); // Keep track of what has been loaded
            }

            // Check to see if the msg is in the store
            boolean exists = msgStore.containsMessage(did, mid);
            if (exists) {
                if (Store.getDEBUG()) {
                    String msg = getPrefix() + " stored message exists " + mid;
                    logger.log(Logger.INFO, msg);
                }
                // check if stored interests are the same as logged interests
                HashMap storedInterests = msgStore.getInterestStates(did, mid);
                boolean matched = compareStoredConsumers(mid, 
                                      workMessage.getStoredInterests(), 
                                      storedInterests);
                if (!matched) {
                    logger.log(Logger.FORCE, BrokerResources.I_REPLACE_MSG_TXNLOG, mid);
		    dst.removeMessage(mid, RemoveReason.REMOVED_OTHER);
                    if (msgStore.containsMessage(did, mid)) {
                        msgStore.removeMessage(did, mid, false);
                    }
                    rerouteMessage(pkt, workMessage.getStoredInterests(), mid, dst);
                }
            } else {
                if (Store.getDEBUG()) {
                    String msg = getPrefix() + " stored message does not exist " + mid;
                    logger.log(Logger.INFO, msg);
                }
                rerouteMessage(pkt, workMessage.getStoredInterests(),mid,dst);
            }

        } //while
    }
	
    private void rerouteMessage(Packet pkt, ConsumerUID[] storedInterests, 
                                SysMessageID mid, Destination dst) 
                                throws BrokerException {
        // TO DO 
        // This should use existing routing
		
        logger.log(Logger.FORCE, BrokerResources.I_RECONSTRUCT_MSG_TXNLOG, mid, dst+" [reroute]");
        PacketReference pr = PacketReference.createReferenceWithDestination(
                                 msgStore.parent, pkt, dst, null);
        if (pr.isExpired()) {
            String msg ="not routing expired message on transaction log replay "+mid;
            logger.log(Logger.INFO, msg);
            return;
        }
        try {
            dst.queueMessage(pr, false);
            MessageDeliveryTimeInfo di = pr.getDeliveryTimeInfo();
            if (di == null || di.getOnTimerState() == null) {
                dst.routeNewMessage(pr);
            } else {
               di.setDeliveryReady();
            }
        } catch (SelectorFormatException e) {
            // shouldn't happens
            throw new BrokerException(Globals.getBrokerResources().getString(
                BrokerResources.E_ROUTE_RECONSTRUCTED_MSG_FAILED, mid), e);
        }
    }
	
	private boolean compareStoredConsumers(SysMessageID mid,
			ConsumerUID[] logged, HashMap storedmap) {
		boolean match = true;
		int loggedLength = 0;
		if (logged != null)
			loggedLength = logged.length;
		if (loggedLength < storedmap.size()) {
			match = false;
			logger.log(Logger.ERROR,
					"Mismatch in number of logged and stored consumers for "
							+ mid + " logged=" + loggedLength + " stored="
							+ storedmap.size());
		}
		if (loggedLength > storedmap.size()) {
			match = false;
			logger.log(Logger.WARNING,
					"Mismatch in number of logged and stored consumers for "
							+ mid + " logged=" + loggedLength + " stored="
							+ storedmap.size());
		}
		HashSet loggedSet = new HashSet(loggedLength);
		for (int i = 0; i < loggedLength; i++) {

			ConsumerUID c = logged[i];
			loggedSet.add(c);
			if (!storedmap.containsKey(c)) {
				logger.log(Logger.WARNING,
						"stored interest does not contain logged interest. sysid= "
								+ mid + " ConsumerUID=" + c);
				match = false;
			}
		}
		Iterator iter = storedmap.keySet().iterator();
		while (iter.hasNext()) {
			Object storedConsumer = iter.next();
			if (!loggedSet.contains(storedConsumer)) {
				logger.log(Logger.ERROR,
						"logged interests does not contain stored interst. sysid= "
								+ mid + " consumerid=" + storedConsumer);
				match = false;
			}
		}

		if (match) {
			if (Store.getDEBUG()) {
				String msg = getPrefix()
						+ " stored consumers match. numConsumers= "
						+ loggedLength;

				logger.log(Logger.INFO, msg);
			}
		}
		return match;
	}
	
	
	public void replayNonTxnMsgAck(NonTransactedMsgAckEvent event,
			Set dstLoadedSet) throws IOException, BrokerException {

		replayAcknowledgedMessage(event.messageAck, dstLoadedSet);
	}

	public void replayNonTxnMsg(NonTransactedMsgEvent event,
			Set dstLoadedSet) throws IOException, BrokerException {

		replaySentMessage(event.message, dstLoadedSet);

	}
	
	private void replayAcknowledgedMessages(
			List<TransactionWorkMessageAck> acknowledgedMessages,
			Set dstLoadedSet) throws IOException, BrokerException {

		for (int i = 0; i < acknowledgedMessages.size(); i++) {
			TransactionWorkMessageAck messageAck = acknowledgedMessages.get(i);
			replayAcknowledgedMessage(messageAck, dstLoadedSet);

		}
	}

	
	private void replayAcknowledgedMessage(
			TransactionWorkMessageAck messageAck, Set dstLoadedSet)
			throws IOException, BrokerException {

	
		DestinationUID did = messageAck.getDestUID();
		SysMessageID mid = messageAck.getSysMessageID();
		ConsumerUID iid = messageAck.getConsumerID();
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " replaying acknowledged message "
					+ messageAck;
			logger.log(Logger.INFO, msg);
		}
		// Make sure dst exists; autocreate if possible
		Destination[] ds = Globals.getDestinationList().getDestination(msgStore.parent, did.getName(), did
				.isQueue() ? DestType.DEST_TYPE_QUEUE
				: DestType.DEST_TYPE_TOPIC, true, true);
                Destination dst = ds[0];

		// Load all msgs inorder to update consumer states
		if (!dstLoadedSet.contains(dst)) {
			dst.load();
			dstLoadedSet.add(dst); // Keep track of what has been loaded
		}

		if (msgStore.containsMessage(did, mid)) {
			logger.log(logger.FORCE, BrokerResources.I_UPDATE_INT_STATE_TXNLOG,
					iid, mid);
			// For Queue, ensure the stored ConsumerUID is 0 otherwise
			// use try using the correct value; see bug 6516160
			if (dst.isQueue() && iid.longValue() != 0) {
				msgStore.updateInterestState(did, mid, PacketReference
                                    .getQueueUID(), PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
			} else {
				msgStore.updateInterestState(did, mid, iid,
                                    PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
			}

			acknowledgeOnReplay(dst, mid, iid);

		} else {
			logger.log(logger.FORCE,
					BrokerResources.I_DISREGARD_INT_STATE_TXNLOG, iid, mid);
		}
	}
	
	

	public void replayMessageRemoval(MsgRemovalEvent event,
			Set dstLoadedSet) throws IOException, BrokerException {
		DestinationUID did = event.destUID;
		SysMessageID mid = event.sysMessageID;
		
		// Make sure dst exists; autocreate if possible
		Destination[] ds = Globals.getDestinationList().getDestination(msgStore.parent, did.getName(), did
				.isQueue() ? DestType.DEST_TYPE_QUEUE
				: DestType.DEST_TYPE_TOPIC, true, true);
                Destination dst = ds[0];

		// Load all msgs inorder to update consumer states
		if (!dstLoadedSet.contains(dst)) {
			dst.load();
			dstLoadedSet.add(dst); // Keep track of what has been loaded
		}

		logger.log(Logger.FORCE, Globals.getBrokerResources().getKString(
                   BrokerResources.I_RM_MSG_ON_REPLAY_MSG_REMOVAL, mid, dst));
        dst.removeMessage(mid, RemoveReason.REMOVED_OTHER);

		if (msgStore.containsMessage(did, mid)) {
			msgStore.removeMessage(did, mid, false);
		}
		
	}

	
	public void acknowledgeOnReplay(Destination dst, SysMessageID mid,
			ConsumerUID iid) throws IOException, BrokerException {
		PacketReference ref = dst.getMessage(mid);
		boolean allAcked = false;
		if (ref != null) {
			allAcked = ref.acknowledgedOnReplay(iid, iid);
			if (Store.getDEBUG()) {
				String msg = getPrefix() + " acknowledgedOnReplay  " + mid
						+ " allAcked =" + allAcked;
				logger.log(Logger.INFO, msg);
			}
		} else {
			if (Store.getDEBUG()) {
				String msg = getPrefix()
						+ " did not find packet in destination " + mid;
				logger.log(Logger.INFO, msg);
			}
		}
		if (allAcked) {
			dst.removeMessage(mid, RemoveReason.ACKNOWLEDGED);
			if (Store.getDEBUG()) {
				String msg = getPrefix() + " removed message from destination "
						+ mid;
				logger.log(Logger.INFO, msg);
			}
		}
	}
	
	public void replayRemoteAcks(TransactionAcknowledgement[] txnAcks, DestinationUID[] destIds,
			TransactionUID tid, HashSet dstLoadedSet)
                        throws IOException,BrokerException {
		// TO DO
		// store destUID alongside acks so that we can replay more directly

		if (Store.getDEBUG()) {
			String msg = getPrefix() + " replayRemoteAcks ";
			logger.log(Logger.INFO, msg);
		}
                DestinationList DL = Globals.getDestinationList();
		for (int i = 0; i < txnAcks.length; i++) {
			TransactionAcknowledgement txnAck = txnAcks[i];
			DestinationUID destId = destIds[i];
			SysMessageID mid = txnAck.getSysMessageID();
			ConsumerUID iid = txnAck.getStoredConsumerUID();
			
			//Destination dest = DL.getDestination(msgStore.parent, destId);
			// make sure it is loaded
			
			int type = (destId.isQueue() ? DestType.DEST_TYPE_QUEUE
					: DestType.DEST_TYPE_TOPIC);
			Destination[] ds = DL.getDestination(msgStore.parent, destId.getName(),
					type, true, true);
                        Destination dest = ds[0];
			dest.load();

			PacketReference pr = dest.getMessage(mid);
			if (pr == null) {
				// could have been acknowledged already?
				
				// TO DO check this further
				String msg = " could not find packet for replayed message ack "
						+ txnAck + " dest " + destId + " in transaction " + tid;
				logger.log(Logger.WARNING, msg);
			} else {
				if (msgStore.containsMessage(destId, mid)) {
					logger
							.log(logger.FORCE,
									BrokerResources.I_UPDATE_INT_STATE_TXNLOG,
									iid, mid);
					// For Queue, ensure the stored ConsumerUID is 0 otherwise
					// use try using the correct value; see bug 6516160
					if (dest.isQueue() && iid.longValue() != 0) {
						msgStore.updateInterestState(destId, mid, PacketReference
								.getQueueUID(),
								PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
					} else {
						msgStore.updateInterestState(destId, mid, iid,
								PartitionedStore.INTEREST_STATE_ACKNOWLEDGED, false);
					}
					
					acknowledgeOnReplay(dest,mid,iid);
				} else {
					logger.log(logger.FORCE,
							BrokerResources.I_DISREGARD_INT_STATE_TXNLOG, iid,
							mid);
				}
			}

		}

	}

}
