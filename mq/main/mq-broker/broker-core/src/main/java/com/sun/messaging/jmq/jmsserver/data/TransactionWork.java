/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

public class TransactionWork {
	List<TransactionWorkMessage> sentMessages;
	List<TransactionWorkMessageAck> messageAcknowledgments;

	public TransactionWork() {
		sentMessages = new ArrayList<TransactionWorkMessage>();
	}

	public void addMessage(TransactionWorkMessage msg) {
		if (sentMessages == null)
			sentMessages = new ArrayList<TransactionWorkMessage>();
		sentMessages.add(msg);
	}

	public void addMessageAcknowledgement(TransactionWorkMessageAck ack) {
		if (messageAcknowledgments == null)
			messageAcknowledgments = new ArrayList<TransactionWorkMessageAck>();
		messageAcknowledgments.add(ack);
	}

	public List<TransactionWorkMessage> getSentMessages() {
		return sentMessages;
	}

	public void setMessages(List<TransactionWorkMessage> sentMessages) {
		this.sentMessages = sentMessages;
	}

	public List<TransactionWorkMessageAck> getMessageAcknowledgments() {
		return messageAcknowledgments;
	}

	public void setMessageAcknowledgments(
			List<TransactionWorkMessageAck> messageAcknowledgments) {
		this.messageAcknowledgments = messageAcknowledgments;
	}

	public int numSentMessages() {
		if (sentMessages == null)
			return 0;
		return sentMessages.size();
	}

	public int numMessageAcknowledgments() {
		if (messageAcknowledgments == null)
			return 0;
		return messageAcknowledgments.size();
	}

	public void readWork(DataInputStream dis) throws IOException,
			BrokerException {
		
		
		// read sent messages
		int numSentMessages = dis.readInt();
		if (Store.getDEBUG()) {
			Globals.getLogger().log(Logger.DEBUG,
					getPrefix() + "readWork numSentMessages="+ numSentMessages);
		}
		List<TransactionWorkMessage> sentMessages = new ArrayList<TransactionWorkMessage>(
				numSentMessages);
		for (int i = 0; i < numSentMessages; i++) {
			// Reconstruct the message
			
			TransactionWorkMessage workMessage = new TransactionWorkMessage();
			workMessage.readWork(dis);					
			sentMessages.add(workMessage);
		}
		this.setMessages(sentMessages);

		// read message acknowledgements
		int numConsumedMessages = dis.readInt();
		List<TransactionWorkMessageAck> consumedMessages = new ArrayList<TransactionWorkMessageAck>(
				numConsumedMessages);
		for (int i = 0; i < numConsumedMessages; i++) {
			TransactionWorkMessageAck messageAck = new TransactionWorkMessageAck();
			messageAck.readWork(dis);			
			consumedMessages.add(messageAck);
		}
		this.setMessageAcknowledgments(consumedMessages);
	}

	public void writeWork(DataOutputStream dos) throws IOException {
		// Msgs produce section
		dos.writeInt(numSentMessages());
		// Number of msgs (4 bytes)
		if (Store.getDEBUG()) {
			String msg = getPrefix() + " writeWork numSentMessages="
					+ numSentMessages() + " numMessageAcknowledgments="
					+ numMessageAcknowledgments();
			Globals.getLogger().log(Logger.DEBUG, msg);
		}

		if (numSentMessages() > 0) {
			Iterator<TransactionWorkMessage> itr = getSentMessages().iterator();
			while (itr.hasNext()) {
				TransactionWorkMessage workMessage = itr.next();
				workMessage.writeWork(dos);
			}
		}

		// Msgs consume section
		dos.writeInt(numMessageAcknowledgments());
		// Number of acks (4 bytes)
		if (numMessageAcknowledgments() > 0) {
			Iterator<TransactionWorkMessageAck> ackItr = getMessageAcknowledgments()
					.iterator();
			while (ackItr.hasNext()) {
				TransactionWorkMessageAck messageAck = ackItr.next();
				messageAck.writeWork(dos);
			}
		}
	}

	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append(" num messages ").append(numSentMessages());
		s.append(" num acks ").append(numMessageAcknowledgments());
		String result = super.toString() + new String(s);
		return result;
	}
	
	String getPrefix() {
		return "TransactionWork: " + Thread.currentThread().getName();
	}

}
