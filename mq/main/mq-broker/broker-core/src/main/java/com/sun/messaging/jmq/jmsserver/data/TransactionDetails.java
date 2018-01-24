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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.JMQXid;

public class TransactionDetails {

	private int type;
	private TransactionUID tid;
	private JMQXid xid;
	private int state;
	private boolean complete; //used for cluster transactions

	public TransactionDetails() {

	}

	public int getState() {
		return state;
	}

	public void setState(int state) {
		this.state = state;
	}

	public TransactionUID getTid() {
		return tid;
	}

	public void setTid(TransactionUID tid) {
		this.tid = tid;
	}

	public void readContent(DataInputStream dis) throws IOException,
			BrokerException {

		//read Type
		type = dis.readByte();

		// read txnID
		long tid = dis.readLong();
		TransactionUID txUID = new TransactionUID(tid);
		this.setTid(txUID);

		// read state
		int state = dis.readInt();
		this.setState(state);
		
		// complete stored as int 
		int completeVal = dis.readInt();
		complete = completeVal==1;

		// read xid if present
		readXid(dis);

	}

	public void writeContent(DataOutputStream dos) throws IOException {
		// write type
		dos.writeByte(type);

		// write transaction
		dos.writeLong(getTid().longValue()); // Transaction ID (8
		// bytes)
		// write txn state
		dos.writeInt(getState());
		// write txn state
		if (complete)
			dos.writeInt(1);
		else
			dos.writeInt(0);

		// write xid if present
		writeXid(dos);

	}

	protected void readXid(DataInputStream dis) throws IOException {

		boolean xidExists = dis.readBoolean();

		if (xidExists) {
			xid = JMQXid.read(dis);
		}
	}

	protected void writeXid(DataOutputStream dos) throws IOException {
		if (getXid() == null) {
			dos.writeBoolean(false);
		} else {
			dos.writeBoolean(true);
			xid.write(dos);
		}

	}

	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append("type=").append(type);
		s.append(" state=").append(TransactionState.toString(state));
		s.append(" txnId = ").append(getTid());
		s.append(" Xid = ").append(getXid());
		s.append(" complete = ").append(isComplete());
		return new String(s);
	}

	public JMQXid getXid() {
		return xid;
	}

	public void setXid(JMQXid xid) {
		this.xid = xid;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public boolean isComplete() {
		return complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}

}
