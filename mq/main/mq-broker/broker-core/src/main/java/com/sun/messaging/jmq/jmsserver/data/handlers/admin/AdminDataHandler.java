/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

/*
 * @(#)AdminDataHandler.java	1.50 06/28/07
 */

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.io.IOException;
import java.util.Hashtable;

import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.io.SysMessageID;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Broker;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Producer;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.handlers.DataHandler;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * Handler class that deals with JMS administration messages. We first check if a message is an administartion message.
 * If it is we intercept it and process it via the AdminCmdHandlers. If it is not then we pass it to the DataHandler for
 * standard handling.
 */
public class AdminDataHandler extends DataHandler {

	private static boolean DEBUG = getDEBUG();

	private AdminCmdHandler[] handlers = null;

	private BrokerResources rb = Globals.getBrokerResources();

	// Need this so that admin message handlers can locate standard JMS
	// message handlers.
	public PacketRouter adminPktRtr = null;

	private int activeHandlers = 0;

	public AdminDataHandler() {
		super();
		initHandlers();
	}
        public AdminCmdHandler getHandler(int typ) {
            if (typ < 0 || typ >= handlers.length) {
                return null;
            }
            return handlers[typ];
        }

	public void setPacketRouter(PacketRouter pktrtr) {
		adminPktRtr = pktrtr;
	}

	/**
	 * Method to handle administration messages
	 */
	public boolean handle(IMQConnection con, Packet msg) throws BrokerException {
		if (DEBUG) {
			logger.log(Logger.DEBUGMED, "AdminDataHandler: handle() [ Received JMS Admin Message] {0} ", msg.toString());
			if (logger.level >= Logger.DEBUGHIGH) {
				msg.dump(System.out);
			}
		}

		String dest = msg.getDestination();

		// Administration messages are sent to a well known
		// administration Queue
		if (!msg.getIsQueue()
				|| (!dest.equals(MessageType.JMQ_ADMIN_DEST) && !dest.equals(MessageType.JMQ_BRIDGE_ADMIN_DEST))) {
			// Normal message. Let standard JMS data handler deal with it.
			return super.handle(con, msg);
		}

		boolean bridgeAdmin = false;
		if (dest.equals(MessageType.JMQ_BRIDGE_ADMIN_DEST)) {
			if (DEBUG) {
				logger.log(Logger.INFO, "Received bridge admin message");
			}
			bridgeAdmin = true;
		}

		Hashtable props = null;
		Integer msgType = null;
		try {
			props = msg.getProperties();
			msgType = (Integer) props.get(MessageType.JMQ_MESSAGE_TYPE);
		} catch (Exception e) {
			// Programming error. No need to I18N
			String emsg = rb.getString(rb.E_INTERNAL_BROKER_ERROR, "Admin: Could not extract properties from pkt");
			logger.logStack(Logger.WARNING, emsg, e);
			throw new BrokerException(emsg, e);
		}
		if (msgType == null) {
			// Programming error. No need to I18N
			String emsg = rb.getString(rb.E_INTERNAL_BROKER_ERROR, "Message received on administration destination "
					+ dest + " has no " + MessageType.JMQ_MESSAGE_TYPE + " property ignoring it.");
			logger.log(Logger.WARNING, emsg);
			throw new BrokerException(emsg);
		}

		/**
     */
		if (bridgeAdmin) {
			if (msgType.intValue() != MessageType.HELLO) {
				return super.handle(con, msg);
			}
		}
		// send the reply (if necessary)
		if (msg.getSendAcknowledge()) {
			Packet pkt = new Packet(con.useDirectBuffers());
			pkt.setPacketType(PacketType.SEND_REPLY);
			pkt.setConsumerID(msg.getConsumerID());
			Hashtable hash = new Hashtable();
			hash.put("JMQStatus", Integer.valueOf(Status.OK));
			pkt.setProperties(hash);
			con.sendControlMessage(pkt);
		}
		
		Producer pausedProducer = checkFlow(msg, con);	
		if (pausedProducer!=null){
			DestinationUID duid = DestinationUID.getUID(msg.getDestination(),
                msg.getIsQueue());
			Destination[] ds = DL.findDestination(DL.getAdminPartition(), duid);
                        Destination d = ds[0];
			pauseProducer(d, duid, pausedProducer, con);
		}

		// Administrative message. Process it.
		// Get message type property

		// Get AdminCmdHandler for this message type
		int t = msgType.intValue();
		AdminCmdHandler ach = null;

		/*
		 * If the connection is authenticated using admin key authentication then it is considered "restricted" and can
		 * only perform minimal operations. Anything else is forbidden.
		 */
		if (con.getAccessController().isRestrictedAdmin() && t != MessageType.SHUTDOWN && t != MessageType.HELLO
				&& t != MessageType.RESTART) {

			logger.log(Logger.WARNING, BrokerResources.W_FORBIDDEN_ADMIN_OP, MessageType.getString(t));

			Packet reply = new Packet(con.useDirectBuffers());
			reply.setPacketType(PacketType.OBJECT_MESSAGE);

			// By convention reply message is the message type + 1
			AdminCmdHandler.setProperties(reply, t + 1, Status.FORBIDDEN, null);

			sendReply(con, msg, reply);

			return true; // done
		}

		// if we arent shutdown .. track our handler cnt
		if (t != MessageType.SHUTDOWN && t != MessageType.MIGRATESTORE_BROKER)
			incrementActiveHandlers();

		try {
			if (BrokerStateHandler.isShuttingDown()) {

				String message = Globals.getBrokerResources().getKString(BrokerResources.I_ADMIN_BKR_SHUTTINGDOWN,
						MessageType.getString(t));

				logger.log(Logger.WARNING, message);

				Packet reply = new Packet(con.useDirectBuffers());
				reply.setPacketType(PacketType.OBJECT_MESSAGE);

				// By convention reply message is the message type + 1
				AdminCmdHandler.setProperties(reply, t + 1, Status.UNAVAILABLE, message);

				sendReply(con, msg, reply);

				return true; // done
			}
			if (!Broker.getBroker().startupComplete) {
				String message = Globals.getBrokerResources().getKString(BrokerResources.I_ADMIN_BKR_NOT_READY,
						MessageType.getString(t));

				logger.log(Logger.WARNING, message);

				Packet reply = new Packet(con.useDirectBuffers());
				reply.setPacketType(PacketType.OBJECT_MESSAGE);
				// By convention reply message is the message type + 1
				AdminCmdHandler.setProperties(reply, t + 1, Status.UNAVAILABLE, message);

				sendReply(con, msg, reply);

				return true; // done
			}

			try {
				ach = handlers[t];
			} catch (IndexOutOfBoundsException e) {
				logger.logStack(Logger.ERROR, 
					BrokerResources.E_INTERNAL_BROKER_ERROR, 
					"Bad " + MessageType.JMQ_MESSAGE_TYPE
						+ ": " + t, e);
				return true;
			}
			if (ach == null) {
				logger.log(Logger.ERROR, BrokerResources.E_INTERNAL_BROKER_ERROR,
						"No administration handler found for message type " + msgType + ". Ignoring.");
				return true;
			} else {
				// Call command handler to handle message
				try {
					return ach.handle(con, msg, props);
				} catch (Exception e) {// Excepion before sendReply
					int estatus = Status.ERROR;
					String emsg = e.getMessage();
					if (e instanceof BrokerException) {
						estatus = ((BrokerException) e).getStatusCode();
					} else {
						emsg = Globals.getBrokerResources().getKString(BrokerResources.X_INTERNAL_EXCEPTION,
								e.toString());
					}
					logger.logStack(Logger.ERROR, emsg, e);
					Packet reply = new Packet(con.useDirectBuffers());
					reply.setPacketType(PacketType.OBJECT_MESSAGE);
					AdminCmdHandler.setProperties(reply, t + 1, estatus, emsg);
					sendReply(con, msg, reply);
					return true; // done
				}
			}
		} finally {
			if (t != MessageType.SHUTDOWN && t != MessageType.MIGRATESTORE_BROKER)
				decrementActiveHandlers();
		}

	}

	/**
	 * Initialize the array of AdminCmdHandlers. Each administration message has its own handler.
	 */
	private void initHandlers() {

		// This may be a sparse array
		handlers = new AdminCmdHandler[MessageType.LAST];

		// Initialize. Base class (AdminCmdHandler) does a default no-op
		// implementation of a handler.
		handlers[MessageType.CREATE_DESTINATION] = new CreateDestinationHandler(this);
		handlers[MessageType.DESTROY_CONNECTION] = new DestroyConnectionsHandler(this);
		handlers[MessageType.DESTROY_DESTINATION] = new DestroyDestinationHandler(this);
		handlers[MessageType.DESTROY_DURABLE] = new DestroyDurableHandler(this);
		handlers[MessageType.GET_CONNECTIONS] = new GetConnectionsHandler(this);
		handlers[MessageType.GET_CONSUMERS] = new GetConsumersHandler(this);
		handlers[MessageType.GET_DESTINATIONS] = new GetDestinationsHandler(this);
		handlers[MessageType.GET_DURABLES] = new GetDurablesHandler(this);
		handlers[MessageType.GET_LOGS] = new GetLogsHandler(this);
		handlers[MessageType.GET_SERVICES] = new GetServicesHandler(this);
		handlers[MessageType.HELLO] = new HelloHandler(this);
		handlers[MessageType.PAUSE] = new PauseHandler(this);
		handlers[MessageType.PURGE_DESTINATION] = new PurgeDestinationHandler(this);
		handlers[MessageType.RESUME] = new ResumeHandler(this);
		handlers[MessageType.SHUTDOWN] = new ShutdownHandler(this);
		handlers[MessageType.UPDATE_DESTINATION] = new UpdateDestinationHandler(this);
		handlers[MessageType.UPDATE_SERVICE] = new UpdateServiceHandler(this);
		handlers[MessageType.VIEW_LOG] = new ViewLogHandler(this);
		handlers[MessageType.GET_METRICS] = new GetMetricsHandler(this);
		handlers[MessageType.GET_BROKER_PROPS] = new GetBrokerPropsHandler(this);
		handlers[MessageType.UPDATE_BROKER_PROPS] = new UpdateBrokerPropsHandler(this);
		handlers[MessageType.RELOAD_CLUSTER] = new ReloadClusterHandler(this);
		handlers[MessageType.GET_TRANSACTIONS] = new GetTransactionsHandler(this);
		handlers[MessageType.ROLLBACK_TRANSACTION] = new RollbackCommitHandler(this);
		handlers[MessageType.COMMIT_TRANSACTION] = new RollbackCommitHandler(this);
		handlers[MessageType.PURGE_DURABLE] = new PurgeDurableHandler(this);
		handlers[MessageType.COMPACT_DESTINATION] = new CompactDestinationHandler(this);
		handlers[MessageType.DEBUG] = new DebugHandler(this);
		handlers[MessageType.QUIESCE_BROKER] = new QuiesceHandler(this);
		handlers[MessageType.TAKEOVER_BROKER] = new TakeoverHandler(this);
		handlers[MessageType.GET_CLUSTER] = new GetClusterHandler(this);
		handlers[MessageType.GET_JMX] = new GetJMXConnectorsHandler(this);
		handlers[MessageType.UNQUIESCE_BROKER] = new UnquiesceHandler(this);
		handlers[MessageType.RESET_BROKER] = new ResetMetricsHandler(this);
		handlers[MessageType.GET_MESSAGES] = new GetMessagesHandler(this);
		handlers[MessageType.DELETE_MESSAGE] = new DeleteMessageHandler(this);
		handlers[MessageType.REPLACE_MESSAGE] = new ReplaceMessageHandler(this);
		handlers[MessageType.CHECKPOINT_BROKER] = new CheckpointBrokerHandler(this);
		handlers[MessageType.UPDATE_CLUSTER_BROKERLIST] = new UpdateClusterBrokerListHandler(this);
		handlers[MessageType.CHANGE_CLUSTER_MASTER_BROKER] = new ChangeClusterMasterBrokerHandler(this);
		handlers[MessageType.MIGRATESTORE_BROKER] = new MigrateStoreHandler(this);
	}

	/**
	 * Send a reply to an administration command message.
	 * 
	 * @param con
	 *            Connection cmd_msg came in on
	 * @param cmd_msg
	 *            Administrative command message from client
	 * @param reply_msg
	 *            Broker's reply to cmd_msg.
	 */
	public SysMessageID sendReply(IMQConnection con, Packet cmd_msg, Packet reply_msg) {

		try {

			// We send message to the ReplyTo destination
			String destination = cmd_msg.getReplyTo();
			if (destination == null) {
				// Programming error. No need to I18N
				logger.log(Logger.ERROR, rb.E_INTERNAL_BROKER_ERROR,
						"Administration message has no ReplyTo destination. Not replying.");
				return null;
			}

			reply_msg.setDestination(destination);
			reply_msg.setIsQueue(true);

			// Make sure message is not persistent, in a transaction, or ACK'd
			reply_msg.setPersistent(false);
			reply_msg.setTransactionID(0);
			reply_msg.setSendAcknowledge(false);

			// Set port number and IP of packet
			if (con instanceof IMQBasicConnection) {
				reply_msg.setPort(((IMQBasicConnection) con).getLocalPort());
			}

			reply_msg.setIP(Globals.getBrokerInetAddress().getAddress());

			// Set sequence number and timestamp on packet. This is typically
			// done by writePacket(), but since we're simulating an incomming
			// packet we must to it now.
			reply_msg.updateTimestamp();
			reply_msg.updateSequenceNumber();

			// Turn off auto-generation of timestamp and sequence for this packet
			// This ensures the values we just set are never overwritten.
			reply_msg.generateTimestamp(false);
			reply_msg.generateSequenceNumber(false);

			if (DEBUG) {
				try {
					logger.log(Logger.DEBUG, "AdminDataHandler: REPLY: " + reply_msg + ": " + reply_msg.getProperties());
				} catch (IOException e) {
					// Programming error. No need to I18N
					logger.logStack(Logger.WARNING, rb.E_INTERNAL_BROKER_ERROR,
							"Admin: Could not extract properties from pkt", e);
				} catch (ClassNotFoundException e) {
					logger.logStack(Logger.WARNING, rb.E_INTERNAL_BROKER_ERROR,
							"Admin: Could not extract properties from pkt", e);
				}
			}
            SysMessageID mid = reply_msg.getSysMessageID();

			// DataHandler's handle method will treat this as a message being
			// sent by a client.
			// XXX REVISIT 08/30/00 dipol: can 'con' be null to indicate
			// message is originating from an internal source?
			super.handle(con, reply_msg, true /* we are admin */);
            return mid;
		} catch (Exception e) {
			logger.logStack(Logger.ERROR, rb.E_INTERNAL_BROKER_ERROR, "Could not reply to administrative message.", e);
		}
        return null;
	}

	private void incrementActiveHandlers() {
		synchronized (this) {
			activeHandlers++;
		}
	}

	public void decrementActiveHandlers() {
		synchronized (this) {
			activeHandlers--;
			if (activeHandlers == 0) {
				this.notifyAll();
			}
		}
	}

	public void waitForHandlersToComplete(int secs) {
		// if we have more than 0 active handlers wait
		// the seconds passed in
		synchronized (this) {
			try {
				if (activeHandlers > 0)
					wait(secs * 1000L);
			} catch (Exception ex) {
			}
		}
	}

}
