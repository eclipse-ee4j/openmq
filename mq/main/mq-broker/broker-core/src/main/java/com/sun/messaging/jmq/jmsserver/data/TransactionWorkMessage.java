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

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.PacketReference;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class TransactionWorkMessage {

	private DestinationUID destUID;
	private Packet message;
	private ConsumerUID[] storedInterests;
	private PacketReference packetRef;

	public TransactionWorkMessage(DestinationUID destUID, Packet message, ConsumerUID[] storedInterests) {
		this.destUID = destUID;
		this.message = message;
		this.storedInterests = storedInterests;
	}
	
	public TransactionWorkMessage() {			
	}

	public DestinationUID getDestUID() {
		return destUID;
	}

	public void setDestUID(DestinationUID destUID) {
		this.destUID = destUID;
	}

	public Packet getMessage() {
		return message;
	}
	
	public PacketReference getPacketReference() {
		return packetRef;
	}

	public void setPacketReference(PacketReference ref) {
		this.packetRef= ref;
		this.message = ref.getPacket();
	}

	public String toString()
	{
		StringBuffer result= new StringBuffer("sysMessageID=").append(message.getMessageID());
		result.append(" destUID=").append(destUID);
		result.append(" dest=").append(message.getDestination());
	
		return result.toString();
	}

	public ConsumerUID[] getStoredInterests() {
		return storedInterests;
	}

	public void setStoredInterests(ConsumerUID[] storedInterests) {
		this.storedInterests = storedInterests;
	}
	
	public void writeWork(DataOutputStream dos) throws IOException {

		byte[] data = getMessage().getBytes();

		dos.write(data); // Message			
		ConsumerUID[] storedInterests = getStoredInterests();
		if (storedInterests == null) {
			dos.writeInt(0);
		} else {
			dos.writeInt(storedInterests.length);
			for (int i = 0; i < storedInterests.length; i++) {
				dos.writeLong(storedInterests[i].longValue());
			}
		}
	}
	
	public void readWork(DataInputStream dis) throws IOException,
			BrokerException {
		message = new Packet(false);
		message.generateTimestamp(false);
		message.generateSequenceNumber(false);
		message.readPacket(dis);

		int numStoredInterests = dis.readInt();
		storedInterests = new ConsumerUID[numStoredInterests];
		for (int j = 0; j < numStoredInterests; j++) {
			long cuid = dis.readLong();
			storedInterests[j] = new ConsumerUID(cuid);
		}

		// Make sure dest exists; auto-create if possible
		// this is because we will need to add messages to this
		// destination
		destUID = DestinationUID.getUID(message.getDestination(), message
				.getIsQueue());

	}
	
}
