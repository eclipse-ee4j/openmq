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

import com.sun.messaging.jmq.jmsserver.data.BaseTransaction;
import com.sun.messaging.jmq.jmsserver.data.TransactionWorkMessage;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class NonTransactedMsgEvent extends TransactionEvent {

	TransactionWorkMessage message;

	static TransactionEvent create(byte subtype) {
		TransactionEvent result = null;

		result = new NonTransactedMsgEvent();
		return result;
	}

	int getType() {
		return BaseTransaction.NON_TRANSACTED_MSG_TYPE;
	}

	int getSubType() {
		return 0;
	}
	
	public NonTransactedMsgEvent()
	{
		
	}
	
	public NonTransactedMsgEvent(TransactionWorkMessage message)
	{
		this.message=message;
	}

	public byte[] writeToBytes() throws IOException {
		// Log all msgs and acks for producing and consuming txn
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);

		dos.writeByte(BaseTransaction.NON_TRANSACTED_MSG_TYPE);
		dos.writeByte(0);

		message.writeWork(dos);		

		dos.close();
		baos.close();

		byte[] data = baos.toByteArray();
		return data;

	}

	public void readFromBytes(byte[] data) throws IOException, BrokerException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bais);
		
		dis.skip(2);
		message=new TransactionWorkMessage();
		message.readWork(dis);		
		
		dis.close();
		bais.close();
	}

}
