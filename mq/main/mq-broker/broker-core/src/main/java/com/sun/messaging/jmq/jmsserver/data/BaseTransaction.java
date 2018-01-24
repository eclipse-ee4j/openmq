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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.io.FilteringObjectInputStream;

public abstract class BaseTransaction {
	
	
	public static final long FORMAT_VERSION_1 = 1;
	// add a new format version whenever the format of transactions 
	// or transaction events changes.
	// This version will be stored in the headers of incompleteTransactionStore and txnLog files
	// so we can check if the file is compatible with current software version
	
	
	public static final long CURRENT_FORMAT_VERSION= FORMAT_VERSION_1;
	
	

	public static final int UNDEFINED_TRANSACTION_TYPE = 0;
	public static final int LOCAL_TRANSACTION_TYPE = 1;
	public static final int REMOTE_TRANSACTION_TYPE = 2;
	public static final int CLUSTER_TRANSACTION_TYPE = 3;
	public static final int NON_TRANSACTED_MSG_TYPE = 4;
	public static final int NON_TRANSACTED_ACK_TYPE = 5;
	public static final int MSG_REMOVAL_TYPE = 6;

	TransactionDetails transactionDetails;
	TransactionWork transactionWork;
	TransactionState transactionState;

	byte[] data;

	public BaseTransaction(int type) {
		transactionDetails = new TransactionDetails();
		transactionDetails.setType(type);
	}

	public int getType() {
		return transactionDetails.getType();
	}

	public int getState() {
		return transactionDetails.getState();
	}

	public TransactionUID getTid() {
		return transactionDetails.getTid();
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public String toString() {

		return transactionDetails.toString();
	}

	public TransactionWork getTransactionWork() {
		return transactionWork;
	}

	public void setTransactionWork(TransactionWork transactionWork) {
		this.transactionWork = transactionWork;
	}

	public TransactionDetails getTransactionDetails() {
		return transactionDetails;
	}

	public void setTransactionDetails(TransactionDetails transactionDetails) {
		this.transactionDetails = transactionDetails;
	}
	
	public TransactionState getTransactionState() {
		return transactionState;
	}

	public void setTransactionState(TransactionState transactionState) {
		this.transactionState = transactionState;
	}

	String getPrefix() {
		return "BaseTransaction: " + Thread.currentThread().getName() + " "
				+ this.getTid();
	}

	// io methods to read and write to byte array
	public void readFromBytes(byte[] data) throws IOException, BrokerException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bais);

		readData(dis);

		int objectBodySize = dis.readInt();

		byte[] objectBody = new byte[objectBodySize];
		dis.read(objectBody);

		ByteArrayInputStream bais2 = new ByteArrayInputStream(objectBody);
		ObjectInputStream ois = new FilteringObjectInputStream(bais2);

		try {
			readObjects(ois);

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		ois.close();
		bais2.close();

		dis.close();
		bais.close();
	}

	public abstract void readData(DataInputStream dis) throws IOException,
			BrokerException;

	public abstract void readObjects(ObjectInputStream ois) throws IOException,
			BrokerException, ClassNotFoundException;

	public byte[] writeToBytes() throws IOException {
		// Log all msgs and acks for producing and consuming txn
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		writeData(dos);

		ByteArrayOutputStream baos2 = new ByteArrayOutputStream(1024);
		ObjectOutputStream oos = new ObjectOutputStream(baos2);

		writeObjects(oos);
		oos.close();

		byte[] data = baos2.toByteArray();
		int length = data.length;
		dos.writeInt(length);
		dos.write(data);

		baos2.close();

		dos.close();
		baos.close();

		byte[] data2 = baos.toByteArray();
		return data2;

	}

	public abstract void writeData(DataOutputStream dos) throws IOException;

	public abstract void writeObjects(ObjectOutputStream oos)
			throws IOException;

	

}
