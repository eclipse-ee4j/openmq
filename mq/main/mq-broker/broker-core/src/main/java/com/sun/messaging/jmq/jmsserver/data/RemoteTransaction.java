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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.sun.messaging.jmq.jmsserver.core.BrokerAddress;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class RemoteTransaction extends BaseTransaction {

	TransactionAcknowledgement[] txnAcks;
	DestinationUID[] destIds;
	BrokerAddress txnHomeBroker;

	public RemoteTransaction() {
		super(BaseTransaction.REMOTE_TRANSACTION_TYPE);

	}

	public RemoteTransaction(TransactionUID id, TransactionState ts,
			TransactionAcknowledgement[] txnAcks, DestinationUID[] destIds, BrokerAddress txnHomeBroker) {
		this();
		transactionDetails.setTid(id);
		transactionDetails.setState(ts.getState());
		transactionDetails.setXid(ts.getXid());
		transactionState = ts;
		setTxnAcks(txnAcks);
		setDestIds(destIds);
		setTxnHomeBroker(txnHomeBroker);
	}

	public void readData(DataInputStream dis) throws IOException,
			BrokerException {
		transactionDetails.readContent(dis);

	}

	public void readObjects(ObjectInputStream ois) throws IOException,
			ClassNotFoundException {
		transactionState = (TransactionState) ois.readObject();
		txnAcks = (TransactionAcknowledgement[]) ois.readObject();
		destIds = (DestinationUID[])ois.readObject();
		txnHomeBroker = (BrokerAddress) ois.readObject();
	}

	public void writeData(DataOutputStream dos) throws IOException {
		transactionDetails.writeContent(dos);

	}

	public void writeObjects(ObjectOutputStream oos) throws IOException {
		oos.writeObject(transactionState);
		oos.writeObject(txnAcks);
		oos.writeObject(destIds);
		oos.writeObject(txnHomeBroker);
	}

	String getPrefix() {
		return "RemoteTransaction: " + Thread.currentThread().getName() + " "
				+ this.getTid();
	}

	public TransactionAcknowledgement[] getTxnAcks() {
		return txnAcks;
	}

	public void setTxnAcks(TransactionAcknowledgement[] txnAcks) {
		this.txnAcks = txnAcks;
	}

	public BrokerAddress getTxnHomeBroker() {
		return txnHomeBroker;
	}

	public void setTxnHomeBroker(BrokerAddress txnHomeBroker) {
		this.txnHomeBroker = txnHomeBroker;
	}

	public DestinationUID[] getDestIds() {
		return destIds;
	}

	public void setDestIds(DestinationUID[] destIds) {
		this.destIds = destIds;
	}
}
