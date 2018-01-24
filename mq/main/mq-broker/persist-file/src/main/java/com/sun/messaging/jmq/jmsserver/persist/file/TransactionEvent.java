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

import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public abstract class TransactionEvent {

	static public TransactionEvent createFromBytes(byte[] data)
			throws IOException, BrokerException {

		TransactionEvent result = null;
		byte type = data[0];
		byte subType = data[1];
		result = create(type, subType);
		result.readFromBytes(data);
		return result;
	}

	static TransactionEvent create(byte type, byte subtype) {
		TransactionEvent result = null;
		switch (type) {
		case BaseTransaction.LOCAL_TRANSACTION_TYPE:
			result = LocalTransactionEvent.create(subtype);
			break;
		case BaseTransaction.CLUSTER_TRANSACTION_TYPE:
			result = ClusterTransactionEvent.create(subtype);
			break;
		case BaseTransaction.REMOTE_TRANSACTION_TYPE:
			result = RemoteTransactionEvent.create(subtype);
			break;
		case BaseTransaction.NON_TRANSACTED_MSG_TYPE:
			result = NonTransactedMsgEvent.create(subtype);
			break;		
		case BaseTransaction.NON_TRANSACTED_ACK_TYPE:
			result = NonTransactedMsgAckEvent.create(subtype);
			break;		
		case BaseTransaction.MSG_REMOVAL_TYPE:
			result = MsgRemovalEvent.create(subtype);
			break;		
			

		default:
			throw new UnsupportedOperationException();
		}
		return result;
	}

	abstract int getType();

	abstract void readFromBytes(byte[] data) throws IOException,
			BrokerException;

	abstract byte[] writeToBytes() throws IOException, BrokerException;

}
