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
import com.sun.messaging.jmq.util.JMQXid;

public class LocalTransaction extends BaseTransaction {

	public LocalTransaction() {
		super(BaseTransaction.LOCAL_TRANSACTION_TYPE);
		

	}
	
	public LocalTransaction(TransactionUID id, int state, JMQXid xid,
			TransactionWork txnWork) {
		super(BaseTransaction.LOCAL_TRANSACTION_TYPE);

		setTransactionWork(txnWork);
		transactionDetails.setTid(id);
		transactionDetails.setState(state);
		transactionDetails.setXid(xid);
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

		// need to reset state
		try {
			transactionState.setState(transactionDetails.getState());
		} catch (BrokerException e) {
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}

	}

	
	public void writeData(DataOutputStream dos) throws IOException {
		transactionDetails.writeContent(dos);
		transactionWork.writeWork(dos);
	}

	public void writeObjects(ObjectOutputStream oos) throws IOException {
			oos.writeObject(transactionState);
	}

	String getPrefix() {
		return "LocalTransaction: " + Thread.currentThread().getName() + " "
				+ this.getTid();
	}

}
