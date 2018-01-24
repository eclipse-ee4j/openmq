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

import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

public class TransactionWorkMessageAck {

	DestinationUID destUID;
	SysMessageID sysMessageID;
	ConsumerUID consumerID;

        //for non-newTxnLog processing and persistence
        TransactionAcknowledgement ta = null;

	public TransactionWorkMessageAck() {

	}

	public TransactionWorkMessageAck(DestinationUID dest, SysMessageID sysMessageID,
			ConsumerUID consumerID) {
		this.destUID = dest;
		this.sysMessageID = sysMessageID;
		this.consumerID = consumerID;
	}

	public DestinationUID getDestUID() {
		return destUID;
	}

	public void setDest(DestinationUID dest) {
		this.destUID = dest;
	}

	public SysMessageID getSysMessageID() {
		return sysMessageID;
	}

	public void setSysMessageID(SysMessageID sysMessageID) {
		this.sysMessageID = sysMessageID;
	}

	public ConsumerUID getConsumerID() {
		return consumerID;
	}

	public void setConsumerID(ConsumerUID consumerID) {
		this.consumerID = consumerID;
	}
	
        public void setTransactionAcknowledgement(TransactionAcknowledgement ta) {
            this.ta = ta; 
        }

        public TransactionAcknowledgement getTransactionAcknowledgement() {
            return ta; 
        }

	public String toString()
	{
		StringBuffer result = new StringBuffer("dest=").append(destUID);
		result.append(" sysMessageID=").append(sysMessageID);
		result.append(" consumerID=").append(consumerID);		
		return result.toString();
	}
	
	public void writeWork(DataOutputStream dos) throws IOException {
		dos.writeUTF(destUID.toString()); 
		sysMessageID.writeID(dos);
		dos.writeLong(consumerID.longValue()); 
	}
	
	public void readWork(DataInputStream dis) throws IOException,
			BrokerException {
		String dest = dis.readUTF();
		destUID = new DestinationUID(dest);
		sysMessageID = new SysMessageID();
		sysMessageID.readID(dis);
		long cid = dis.readLong();
		consumerID = new ConsumerUID(cid);
	}
}
