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

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

public class CheckpointBrokerHandler extends AdminCmdHandler {
	private static boolean DEBUG = getDEBUG();

	public CheckpointBrokerHandler(AdminDataHandler parent) {
		super(parent);
	}

	/**
	 * Handle the incomming administration message.
	 * 
	 * @param con
	 *            The Connection the message came in on.
	 * @param cmd_msg
	 *            The administration message
	 * @param cmd_props
	 *            The properties from the administration message
	 */

	public void checkpoint() throws BrokerException{
            DL.doCheckpoint(null, true);
	}

	public boolean handle(IMQConnection con, Packet cmd_msg, Hashtable cmd_props) {

		if (DEBUG) {
			logger.log(Logger.DEBUG, this.getClass().getName() + ": "
					+ cmd_props);
		}

		int status = Status.OK;
		String errMsg = null;

		logger.log(Logger.INFO, BrokerResources.I_CHECKPOINT_BROKER);
		try {
			checkpoint();
		} catch (Throwable e) {
			logger.log(Logger.ERROR, this.getClass().getName() + ": "
					+ cmd_props, e);
			status = Status.ERROR;
			errMsg = e.getMessage();
		}

		// Send reply
		sendReply(con, cmd_msg, MessageType.CHECKPOINT_BROKER_REPLY, status,
				errMsg);
		return true;
	}
	
	private void sendReply(IMQConnection con, Packet cmd_msg, int replyType,
			int status, String errMsg) {

		// Send reply
		Packet reply = new Packet(con.useDirectBuffers());
		reply.setPacketType(PacketType.OBJECT_MESSAGE);

		setProperties(reply, replyType, status, errMsg);

		parent.sendReply(con, cmd_msg, reply);

	}
}
