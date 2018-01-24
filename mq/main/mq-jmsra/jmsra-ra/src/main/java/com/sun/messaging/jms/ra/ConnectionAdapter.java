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

package com.sun.messaging.jms.ra;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.jms.ConnectionConsumer;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateRuntimeException;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;

import com.sun.messaging.jmq.jmsclient.ContextableConnection;
import com.sun.messaging.jmq.jmsclient.XAQueueSessionImpl;
import com.sun.messaging.jmq.jmsclient.XASessionImpl;
import com.sun.messaging.jmq.jmsclient.XATopicSessionImpl;
import com.sun.messaging.jms.MQInvalidClientIDRuntimeException;
import com.sun.messaging.jms.MQRuntimeException;
import com.sun.messaging.jms.ra.api.JMSRAConnectionAdapter;
import com.sun.messaging.jms.ra.api.JMSRASessionAdapter;
 
/**
 * Implements the JMS Connection interface for the GlassFish Message Queue Resource Adapter
 *
 */
public class ConnectionAdapter implements javax.jms.Connection, javax.jms.QueueConnection,
javax.jms.TopicConnection, JMSRAConnectionAdapter, ContextableConnection {
	
    /** The ResourceAdapter instance associated with this instance */
    private com.sun.messaging.jms.ra.ResourceAdapter ra = null;

    /** The ManagedConnection instance that created this instance */
    private com.sun.messaging.jms.ra.ManagedConnection mc = null;

    /** The XAConection instance wrapped by this ConnectionAdapter */
    public com.sun.messaging.jmq.jmsclient.XAConnectionImpl xac = null;

    /** The XASession instance created by this ConnectionAdapter */
    private com.sun.messaging.jms.ra.SessionAdapter sa = null;

    /** Flags whether further creation of session is allowed per J2EE1.4 */
    private boolean sessions_allowed = true;

    /** The set of sessions created by this connection */
    private Set<SessionAdapter> sessions = null;

    /** Flags whether the connection has been destroyed */
    private boolean destroyed = false;

    /** Flags whether the adapter has been closed */
    private boolean closed = false;

    /** Flags whether the connection is in an Application Client Container */
    private boolean inACC = true;

	/* Loggers */
	private static transient final String _className = "com.sun.messaging.jms.ra.ConnectionAdapter";
	protected static transient final String _lgrNameJMSConnection = "javax.jms.Connection.mqjmsra";
	protected static transient final String _lgrNameOutboundConnection = "javax.resourceadapter.mqjmsra.outbound.connection";
	protected static transient final Logger _loggerOC = Logger.getLogger(_lgrNameOutboundConnection);
	protected static transient final Logger _loggerJC = Logger.getLogger(_lgrNameJMSConnection);
	protected static transient final String _lgrMIDPrefix = "MQJMSRA_CA";
	protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
	protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
	protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
	protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
	protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";
 
 
	/**
	 * @param mc
	 * @param xac
	 * @param ra
	 * @throws ResourceException
	 */
	public ConnectionAdapter(com.sun.messaging.jms.ra.ManagedConnection mc,
			com.sun.messaging.jmq.jmsclient.XAConnectionImpl xac, com.sun.messaging.jms.ra.ResourceAdapter ra)
			throws ResourceException {
		
		Object params[] = new Object[3];
		params[0] = mc;
		params[1] = xac;
		params[2] = ra;

		_loggerOC.entering(_className, "constructor()", params);

		this.mc = mc;
		this.xac = xac;
		this.ra = ra;
		sessions = new HashSet<SessionAdapter>();

		if (ra != null) {
			inACC = ra.getInAppClientContainer();
			if (ra.getInClusteredContainer()
					&& this.getManagedConnection().getManagedConnectionFactory()
							.isUseSharedSubscriptionInClusteredContainer()) {
				xac.setRANamespaceUID(ra._getRAUID());
			}
		}
		// System.out.println("MQRA:CA:Constructor-inACC="+inACC);
		// xac.dump(System.out);
	}

	/* 
	 * @see javax.jms.Connection#setClientID(java.lang.String)
	 */
	public void setClientID(String clientId) throws JMSException {
		_loggerJC.entering(_className, "setClientID()", clientId);
		// System.out.println("MQRA:CA:setClientID-to-"+clientId);
		if (!inACC) {
			throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-setClientID()");
		}
		checkClosed();
		xac.setClientID(clientId);
	}
	
    /**
     * Set clientID to the specified value, bypassing any checks as to whether calling setClientID is allowed at this time
     * @param clientID
     */
	@Override
	public void _setClientIDForContext(String clientID) {
		_loggerJC.entering(_className, "_setClientIDForContext()", clientID);
		checkClosed2();
		try {
			xac._setClientID(clientID);
		} catch (InvalidClientIDException ice) {
			throw new MQInvalidClientIDRuntimeException(ice);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	/* 
	 * @see javax.jms.Connection#getClientID()
	 */
	public String getClientID() throws JMSException {
		_loggerJC.entering(_className, "getClientID()");
		checkClosed();
		return xac.getClientID();
	}

	/* 
	 * @see javax.jms.Connection#setExceptionListener(javax.jms.ExceptionListener)
	 */
	public void setExceptionListener(ExceptionListener listener) throws JMSException {
		_loggerJC.entering(_className, "setExceptionListener()", listener);
		if (!inACC) {
			throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-setExceptionListener()");
		}
		checkClosed();
		xac.setExceptionListener(listener);
	}

	/* 
	 * @see javax.jms.Connection#getExceptionListener()
	 */
	public ExceptionListener getExceptionListener() throws JMSException {
		checkClosed();
		return xac.getExceptionListener();
	}

	/* 
	 * @see javax.jms.Connection#getMetaData()
	 */
	public javax.jms.ConnectionMetaData getMetaData() throws JMSException {
		checkClosed();
		return xac.getMetaData();
	}

	/* 
	 * @see javax.jms.Connection#start()
	 */
	public void start() throws JMSException {
		checkClosed();
		xac.start();
	}

	/* 
	 * @see javax.jms.Connection#stop()
	 */
	public void stop() throws JMSException {
		if (!inACC) {
			throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-stop()");
		}
		checkClosed();
		xac.stop();
	}

	/* 
	 * @see javax.jms.Connection#close()
	 */
	public void close() throws JMSException {
		_loggerOC.fine(_lgrMID_INF + "close():xacId=" + xac._getConnectionID() + ":clientId=" + xac.getClientID());

		synchronized (this) {
			if (closed) {
				return;
			}
			closed = true;
			// System.out.println("MQRA:CA:close:xac has clientID="+xac.getClientID());
			if (!xac._isClosed()) {
				xac.stop();
			}
			// Close Sessions
			synchronized (sessions) {
				Iterator<SessionAdapter> it = sessions.iterator();

				while (it.hasNext()) {
					SessionAdapter sa = it.next();
					if (sa != null) {
						sa.close(true);
						it.remove();
					}
				}
				// Clear HashSet
				sessions.clear();
				sessions_allowed = true;
				this.sa = null;
			}
			// System.out.println("MQRA:CA:close:call xac._closeForPooling()");
			try {
				if (!getManagedConnection().xaTransactionActive()){
					_loggerOC.fine(_lgrMID_INF + "close():xacId=" + xac._getConnectionID() + ":unsetting clientId");
					xac._closeForPooling();
				} else {
					// there's still an uncommitted transaction: defer the call to _closeForPooling() until after the commit
				}

			} catch (JMSException jmse) {
				System.err.println("MQRA:CA:close:Got JMSExc during _closeForPooling:ignoring:" + jmse.getMessage());
				jmse.printStackTrace();
			}
			// System.out.println("MQRA:CA:close:Sending ConClosedEvent");
			mc.sendEvent(ConnectionEvent.CONNECTION_CLOSED, null, this);
//			closed = true;
//			if (false) {
//				if (inACC) {
//					// System.out.println("MQRA:CA:close:inACC:Sending ConErrEvent");
//					mc.sendEvent(ConnectionEvent.CONNECTION_ERROR_OCCURRED, null, this);
//				}
//			}
//			if (false) { // !(inACC)
//				// Force the connection to close if it has a clientID so that the cientID can be re-used
//				// try {
//				// System.out.println("MQRA:CA:close:xac is closed="+xac._isClosed());
//				if (!xac._isClosed() && (xac._getClientID() != null)) {
//					// System.out.println("MQRA:CA:close:Sending ConErrEvent");
//					mc.sendEvent(ConnectionEvent.CONNECTION_ERROR_OCCURRED, null, this);
//				}
//				// } catch (JMSException jmse) {
//				// System.out.println("MQRA:CA:close:Got JMSExc while sending ERROR EVT");
//				// }
//			}
		}
	}

	/* 
	 * @see javax.jms.Connection#createConnectionConsumer(javax.jms.Destination, java.lang.String, javax.jms.ServerSessionPool, int)
	 */
	public ConnectionConsumer createConnectionConsumer(Destination dest, String messageSelector,
			ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createConnectionConsumer", code, uoex);
		// return xac.createConnectionConsumer(dest, messageSelector, sessionPool, maxMessages);
	}
 
	/* 
	 * @see javax.jms.QueueConnection#createConnectionConsumer(javax.jms.Queue, java.lang.String, javax.jms.ServerSessionPool, int)
	 */
	public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
			ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createConnectionConsumer", code, uoex);
		// return xac.createConnectionConsumer(queue, messageSelector, sessionPool, maxMessages);
	}

	/* 
	 * @see javax.jms.TopicConnection#createConnectionConsumer(javax.jms.Topic, java.lang.String, javax.jms.ServerSessionPool, int)
	 */
	public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector,
			ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createConnectionConsumer", code, uoex);
		// return xac.createConnectionConsumer(topic, messageSelector, sessionPool, maxMessages);
	}

	/* 
	 * @see javax.jms.Connection#createDurableConnectionConsumer(javax.jms.Topic, java.lang.String, java.lang.String, javax.jms.ServerSessionPool, int)
	 */
	public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createDurableConnectionConsumer", code, uoex);
	}
	
	@Override
	public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createSharedConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createSharedConnectionConsumer", code, uoex);
	}

	@Override
	public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName,
			String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
		UnsupportedOperationException uoex = new UnsupportedOperationException("createSharedDurableConnectionConsumer");
		String code = "2";
		checkClosed();
		throw new com.sun.messaging.jms.JMSException("MQRA:CA:Unsupported-createSharedDurableConnectionConsumer", code, uoex);
	}

	/*
	 * @see javax.jms.Connection#createSession(boolean, int)
	 */
	public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
		// System.out.println("MQRA:CA:createSession()-"+transacted+":"+acknowledgeMode);
		checkClosed();
		if (sessions_allowed == false) {
			throw new com.sun.messaging.jms.JMSException(
					"MQRA:CA:createSession failed-Only one JMS Session allowed when managed connection is involved in a transaction");
		}
		try {
			if (!inACC && mc.xaTransactionActive()) {
				// no more sessions if we are not in the application client container & XA txn is active
				sessions_allowed = true; // false;
			}
		} catch (Exception ee) {
			sessions_allowed = true; // false;
		}
		
		XASessionImpl xas;
		if (ResourceAdapter._isFixCR6760301()){
			// Override arguments
			xas = (XASessionImpl) xac.createSession(overrideTransacted(transacted),
					overrideAcknowledgeMode(acknowledgeMode), (inACC ? null : mc));
		} else {
			xas = (XASessionImpl) xac.createSession((mc.xaTransactionStarted() ? true : transacted),
			acknowledgeMode, (inACC ? null : mc));
		}
		
		SessionAdapter sess_adapter = new SessionAdapter(this, xac, xas);
		addSessionAdapter(sess_adapter);
		return sess_adapter;
	}
	
	/* (non-Javadoc)
	 * @see javax.jms.Connection#createSession(int)
	 */
	@Override
	public Session createSession(int sessionMode) throws JMSException {
		if (sessionMode==Session.SESSION_TRANSACTED){
			return createSession(true,sessionMode);
		} else {
			return createSession(false,sessionMode);
		}
	}

	/* (non-Javadoc)
	 * @see javax.jms.Connection#createSession()
	 */
	@Override
	public Session createSession() throws JMSException {
		return createSession(false,Session.AUTO_ACKNOWLEDGE);
	}

	/* 
	 * @see javax.jms.QueueConnection#createQueueSession(boolean, int)
	 */
	public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException {
		// System.out.println("MQRA:CA:createQueueSession()-"+transacted+":"+acknowledgeMode);
		checkClosed();
		if (sessions_allowed == false) {
			throw new com.sun.messaging.jms.JMSException(
					"MQRA:CA:createQueueSession failed-Only one JMS Session allowed when managed connection is involved in a transaction");
		}
		try {
			if (!inACC && mc.xaTransactionActive()) {
				// no more sessions if we are not in the application client container & XA txn is active
				sessions_allowed = true; // false;
			}
		} catch (Exception ee) {
			sessions_allowed = true; // false;
		}
		
		XAQueueSessionImpl xas;
		if (ResourceAdapter._isFixCR6760301()){
			// Override arguments
			xas = (XAQueueSessionImpl) xac.createQueueSession(overrideTransacted(transacted),
					overrideAcknowledgeMode(acknowledgeMode), (inACC ? null : mc));
		} else {
			xas = (XAQueueSessionImpl) xac.createQueueSession((mc.xaTransactionStarted() ? true
			: transacted), acknowledgeMode, (inACC ? null : mc));
		}
		
		SessionAdapter sess_adapter = new SessionAdapter(this, xac, xas);
		sess_adapter.setQueueSession();
		addSessionAdapter(sess_adapter);
		return sess_adapter;
	}


	/* 
	 * @see javax.jms.TopicConnection#createTopicSession(boolean, int)
	 */
	public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException {
		// System.out.println("MQRA:CA:createTopicSession()-"+transacted+":"+acknowledgeMode);
		checkClosed();
		if (sessions_allowed == false) {
			throw new com.sun.messaging.jms.JMSException(
					"MQRA:CA:createTopicSession failed-Only one JMS Session allowed when managed connection is involved in a transaction");
		}
		try {
			if (!inACC && mc.xaTransactionActive()) {
				// no more sessions if we are not in the application client container & XA txn is active
				sessions_allowed = true; // false;
			}
		} catch (Exception ee) {
			sessions_allowed = true; // false;
		}
		
		XATopicSessionImpl xas;
		if (ResourceAdapter._isFixCR6760301()){
			// Override arguments
			xas = (XATopicSessionImpl) xac.createTopicSession(overrideTransacted(transacted),
					overrideAcknowledgeMode(acknowledgeMode), (inACC ? null : mc));
		} else {
			xas = (XATopicSessionImpl) xac.createTopicSession((mc.xaTransactionStarted() ? true
			: transacted), acknowledgeMode, (inACC ? null : mc));
		}
				
		SessionAdapter sess_adapter = new SessionAdapter(this, xac, xas);
		sess_adapter.setTopicSession();
		addSessionAdapter(sess_adapter);
		return sess_adapter;
	}
	
	private boolean overrideTransacted(boolean suppliedTransactedArgument) {
		
		if (mc.xaTransactionStarted()) return true;
		
		boolean actualTransactedArg;
		if (inACC) {
			actualTransactedArg = suppliedTransactedArgument;
		} else {
			// override args to createSession() when in an EJB or web container
			// in accordance with EJB spec section 13.3.5 "Use of JMS APIs in Transactions"
			// This isn't very clear on what happens when there is no transaction,
			// but I (Nigel) have made the decision that if XA is not being used, 
			// the session should be non-transacted
			actualTransactedArg = false;
		}
		return actualTransactedArg;
	}
	
	private int overrideAcknowledgeMode(int suppliedAcknowledgeModeArgument){
		int actualAcknowledgeModeArg;
		if (inACC){
			actualAcknowledgeModeArg=suppliedAcknowledgeModeArgument;
		} else {
			if (suppliedAcknowledgeModeArgument==Session.CLIENT_ACKNOWLEDGE) {
				// EJB spec section 13.3.5 "Use of JMS APIs in Transactions" says
				// "The Bean Provider should not use the JMS acknowledge method either within a transaction 
				// or within an unspecified transaction context. Message acknowledgment in an unspecified 
				// transaction context is handled by the container." 
				// 
				// The same restriction applies in web container: JavaEE Spec: "EE.6.7 Java Message Service (JMS) 1.1 Requirements" says
				// "In general, the behavior of a JMS provider should be the same in both the EJB container and the web container.
				// The EJB specification describes restrictions on the use of JMS in an EJB container, 
				// as well as the interaction of JMS with transactions in an EJB container. 
				// Applications running in the web container should follow the same restrictions.
				actualAcknowledgeModeArg=Session.AUTO_ACKNOWLEDGE;
			} else {
				// allow auto-ack and dups-OK 
				actualAcknowledgeModeArg=suppliedAcknowledgeModeArgument;
			}
		}
		return actualAcknowledgeModeArg;
	}

	/**
	 * Check whether closed and if so throw an IllegalStateException
	 * @throws JMSException
	 */
	void checkClosed() throws JMSException {
		if (closed) {
			throw new com.sun.messaging.jms.IllegalStateException("MQRA:CA:Illegal:Connection is closed");
		}
	}
	
	/**
	 * Check whether closed and if so throw an IllegalStateRuntimeException
	 * @throws JMSException
	 */
	void checkClosed2() {
		if (closed) {
			throw new IllegalStateRuntimeException("MQRA:CA:Illegal:Connection is closed");
		}
	}

	/**
	 * Add the specified SessionAdapter to the set of Sessions
	 * @param sa
	 */
	void addSessionAdapter(SessionAdapter sa) {
		synchronized (sessions) {
			sessions.add(sa);
			if (this.sa == null) {
				this.sa = sa;
			}
			if (!inACC) {
				// no more sessions if we are not in the application client container
				sessions_allowed = false;
			}
		}
	}

	/**
	 * Remove the specified SessionAdapter from the set of Sessions
	 * @param sa
	 */
	void removeSessionAdapter(SessionAdapter sa) {
		synchronized (sessions) {
			sessions.remove(sa);
			// Allow sessions if the only one got closed
			if (sessions_allowed == false && sessions.isEmpty()) {
				sessions_allowed = true;
				this.sa = null;
			}
		}
	}

	/**
	 * @param newmc
	 * @throws ResourceException
	 */
	public void associateManagedConnection(com.sun.messaging.jms.ra.ManagedConnection newmc) throws ResourceException {
		if (destroyed) {
			System.err.println("MQRA:CA:associateMC:cnxn is destroyed. DebugState="
					+ xac.getDebugState(false).toString());
			throw new javax.resource.spi.IllegalStateException(
					"MQRA:CA:unable to associate ManagedConnection - Connection is destoryed");
		}
		// Switch association from current mc to newmc
		if (newmc != null) {
			this.mc = newmc;
		}
	}

	/**
	 * 
	 */
	public void destroy() {
		// System.out.println("MQRA:CA:destroy()");
		try {
			xac.close();
		} catch (JMSException jmse) {
			System.err.println("MQRA:CA:destroy:Exception on phys cnxn close-ignoring:" + jmse.getMessage());
		}
	}

	/**
	 * 
	 */
	public void cleanup() {
		// System.out.println("MQRA:CA:cleanup()");
		// Close Sessions
		synchronized (sessions) {
			Iterator<SessionAdapter> it = sessions.iterator();

			while (it.hasNext()) {
				SessionAdapter sa = (SessionAdapter) it.next();
				if (sa != null) {
					// try {
					sa.closeAdapter();
					// } catch (JMSException jmse) {
					// System.err.println("MQRA:CA:cleanup():Exception on SessionAdapter close-ignoring"+jmse.getMessage());
					// jmse.printStackTrace();
					// }
				}
			}
			// Clear HashSet
			sessions.clear();
			sessions_allowed = true;
			this.sa = null;
		}
	}

	/**
	 * @return
	 */
	protected com.sun.messaging.jms.ra.ResourceAdapter getResourceAdapter() {
		return ra;
	}

	/**
	 * @return
	 */
	public SessionAdapter getSessionAdapter() {
		return sa;
	}

	public JMSRASessionAdapter getJMSRASessionAdapter() {
		return (JMSRASessionAdapter)sa;
	}

	public com.sun.messaging.jms.ra.ManagedConnection getManagedConnection() {
		return mc;
	}

	/**
	 * @param clientId
	 * @throws JMSException
	 */
	public void open(String clientId) throws JMSException {
		synchronized (this) {
//			System.out.println("MQRA:CA:open():inACC=" + inACC + ":cid=" + clientId);
//			if (false) {
//				if ("cts".equals(clientId)) {
//					if (inACC) {
//						// System.out.println("MQRA:CA:open():inACC:setting-cts1");
//						xac._setClientID("cts1");
//					} else {
//						// System.out.println("MQRA:CA:open():NOTinACC:setting-cts2");
//						xac._setClientID("cts2");
//					}
//				} else {
//
//					xac._setClientID(clientId);
//
//				}
//			}
			xac._setClientID(clientId);
			closed = false;
		}
	}

	/**
	 * 
	 */
	public void open() {
		closed = false;
	}

	/**
	 * Check whether the connection adapter has been closed and, 
	 * if it has been, restore it to a state ready for returning to the pool
	 * 
	 * @throws JMSException
	 */
	public void closeForPoolingIfClosed() throws JMSException {
		if (closed){
    		xac._closeForPooling();
		}
	}


}
