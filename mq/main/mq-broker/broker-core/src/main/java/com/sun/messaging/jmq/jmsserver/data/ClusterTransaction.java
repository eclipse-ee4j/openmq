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

import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class ClusterTransaction extends BaseTransaction {

	TransactionBroker[] transactionBrokers;

	public ClusterTransaction() {
		super(BaseTransaction.CLUSTER_TRANSACTION_TYPE);

	}

	public ClusterTransaction(TransactionUID id, TransactionState ts,
			TransactionWork txnWork, TransactionBroker[] tbas) {
		this();
		
    	transactionDetails.setTid(id);
        transactionDetails.setXid(ts.getXid());
    	transactionDetails.setState(TransactionState.PREPARED);
    	setTransactionWork(txnWork);
    	setTransactionState(ts);		
    	setTransactionBrokers(tbas);

	}

	public void readData(DataInputStream dis) throws IOException,
			BrokerException {
		transactionDetails.readContent(dis);
		if(transactionWork==null)
			transactionWork = new TransactionWork();
		transactionWork.readWork(dis);

	}

	public void readObjects(ObjectInputStream ois) throws IOException,
			ClassNotFoundException {
		transactionState = (TransactionState) ois.readObject();
		transactionBrokers = (TransactionBroker[]) ois.readObject();

	}

	public void writeData(DataOutputStream dos) throws IOException {
		transactionDetails.writeContent(dos);
		transactionWork.writeWork(dos);

	}

	public void writeObjects(ObjectOutputStream oos) throws IOException {
		oos.writeObject(transactionState);

		oos.writeObject(transactionBrokers);
	}

	public TransactionBroker[] getTransactionBrokers() {
		return transactionBrokers;
	}

	public void setTransactionBrokers(TransactionBroker[] transactionBrokers) {
		this.transactionBrokers = transactionBrokers;
	}

	String getPrefix() {
		return "ClusterTransaction: " + Thread.currentThread().getName() + " "
				+ this.getTid();
	}

}
