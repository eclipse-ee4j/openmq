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

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.LocalTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionAcknowledgement;
import com.sun.messaging.jmq.jmsserver.data.TransactionState;
import com.sun.messaging.jmq.jmsserver.data.TransactionWork;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

public abstract class LocalTransactionEvent extends TransactionEvent {

	public static final byte Type1PCommitEvent = 0;
	public static final byte Type2PPrepareEvent = 1;
	public static final byte Type2PCompleteEvent = 2;

	LocalTransaction localTransaction;

	int getType() {
		return BaseTransaction.LOCAL_TRANSACTION_TYPE;
	}

	abstract int getSubType();

	static TransactionEvent create(byte subtype) {
		TransactionEvent result = null;
		switch (subtype) {
		case Type1PCommitEvent:
			result = new LocalTransaction1PCommitEvent();
			break;
		case Type2PPrepareEvent:
			result = new LocalTransaction2PPrepareEvent();
			break;
		case Type2PCompleteEvent:
			result = new LocalTransaction2PCompleteEvent();
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return result;
	}

	public LocalTransaction getLocalTransaction() {
		return localTransaction;
	}

	public void setLocalTransaction(LocalTransaction localTransaction) {
		this.localTransaction = localTransaction;
	}
}

class LocalTransaction1PCommitEvent extends LocalTransactionEvent {

	int getSubType() {
		return Type1PCommitEvent;
	}

	// no need to store transaction info as this will not be long lasting

	// write transaction details
	// write work

	byte[] writeToBytes() throws IOException {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(BaseTransaction.LOCAL_TRANSACTION_TYPE);
		dos.writeByte(Type1PCommitEvent);
		localTransaction.getTransactionDetails().writeContent(dos);
		localTransaction.getTransactionWork().writeWork(dos);

		byte[] data = bos.toByteArray();
		dos.close();
		bos.close();
		return data;
	}

	void readFromBytes(byte[] data) throws IOException, BrokerException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bais);

		localTransaction = new LocalTransaction();
		dis.skip(2);
		localTransaction.getTransactionDetails().readContent(dis);
		TransactionWork work = new TransactionWork();
		work.readWork(dis);
		localTransaction.setTransactionWork(work);
		dis.close();
		bais.close();
	}

}

class LocalTransaction2PPrepareEvent extends LocalTransactionEvent {

	int getSubType() {
		return Type2PPrepareEvent;
	}

	byte[] writeToBytes() throws IOException {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(BaseTransaction.LOCAL_TRANSACTION_TYPE);
		dos.writeByte(Type2PPrepareEvent);
		localTransaction.getTransactionDetails().writeContent(dos);
		localTransaction.getTransactionWork().writeWork(dos);

		ByteArrayOutputStream baos2 = new ByteArrayOutputStream(1024);
		ObjectOutputStream oos = new ObjectOutputStream(baos2);

		oos.writeObject(localTransaction.getTransactionState());
		oos.close();

		byte[] data = baos2.toByteArray();
		int length = data.length;
		dos.writeInt(length);
		dos.write(data);

		baos2.close();

		byte[] data2 = bos.toByteArray();
		dos.close();
		bos.close();
		return data2;
	}

	void readFromBytes(byte[] data) throws IOException, BrokerException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bais);

		localTransaction = new LocalTransaction();
		dis.skip(2);
		localTransaction.getTransactionDetails().readContent(dis);
		TransactionWork work = new TransactionWork();
		work.readWork(dis);
		localTransaction.setTransactionWork(work);

		// need to write transaction info here
		int objectBodySize = dis.readInt();

		byte[] objectBody = new byte[objectBodySize];
		dis.read(objectBody);

		ByteArrayInputStream bais2 = new ByteArrayInputStream(objectBody);
		ObjectInputStream ois = new FilteringObjectInputStream(bais2);

		try {

			TransactionState ts = (TransactionState) ois.readObject();
			
			localTransaction.setTransactionState(ts);

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		ois.close();
		bais2.close();

		dis.close();
		bais.close();
	}
}

class LocalTransaction2PCompleteEvent extends LocalTransactionEvent {

	int getSubType() {
		return Type2PCompleteEvent;
	}

	byte[] writeToBytes() throws IOException {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(BaseTransaction.LOCAL_TRANSACTION_TYPE);
		dos.writeByte(Type2PCompleteEvent);
		localTransaction.getTransactionDetails().writeContent(dos);

		byte[] data = bos.toByteArray();
		dos.close();
		bos.close();
		return data;
	}

	void readFromBytes(byte[] data) throws IOException, BrokerException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bais);

		localTransaction = new LocalTransaction();
		dis.skip(2);
		localTransaction.getTransactionDetails().readContent(dis);

		dis.close();
		bais.close();
	}
}
