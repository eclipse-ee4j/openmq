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

package com.sun.messaging.jmq.jmsclient;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.IllegalStateRuntimeException;
import javax.jms.InvalidClientIDRuntimeException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.JMSSecurityException;
import javax.jms.JMSSecurityRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TransactionInProgressException;
import javax.jms.TransactionRolledBackException;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jms.MQIllegalStateRuntimeException;
import com.sun.messaging.jms.MQInvalidDestinationRuntimeException;
import com.sun.messaging.jms.MQInvalidSelectorRuntimeException;
import com.sun.messaging.jms.MQRuntimeException;
import com.sun.messaging.jms.MQSecurityRuntimeException;
import com.sun.messaging.jms.MQTransactionInProgressRuntimeException;
import com.sun.messaging.jms.MQTransactionRolledBackRuntimeException;

public class JMSContextImpl implements JMSContext, Traceable {
	private static final String ROOT_LOGGER_NAME = "javax.jms";
	protected static final String JMSCONTEXT_LOGGER_NAME =
				ROOT_LOGGER_NAME + ".jmscontext";

	protected static final Logger contextLogger =
		Logger.getLogger(JMSCONTEXT_LOGGER_NAME,
			ClientResources.CLIENT_RESOURCE_BUNDLE_NAME);

	Connection connection;
	Session session;
	MessageProducer messageProducer;
	boolean autoStart=true;
	boolean closed=false;
	
	// Set shared by all the JMSContext objects using the same Connection as this JMSContext
	// which contains all the JMSContext objects using the same Connection as this JMSContext
	Set<JMSContext> contextSet;
	
	Set<JMSConsumer> consumers = new HashSet<JMSConsumer>();
	
	ContainerType containerType;
	
	protected ContainerType getContainerType() {
		return containerType;
	}

	/**
	 * Flag that determines whether calling setClientID is allowed
	 */
	private boolean allowToSetClientID = true;

	public JMSContextImpl(ConnectionFactory connectionFactory, ContainerType containerType, String userName, String password) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(cf@"+connectionFactory.hashCode()+", "+containerType+", "+userName+",)");
		this.containerType=containerType;
		
		// create connection
		try {
			connection = connectionFactory.createConnection(userName,password);
		} catch (SecurityException e) {
			JMSSecurityRuntimeException jsre = new com.sun.messaging.jms.MQSecurityRuntimeException(e.getMessage(),null,e);
			ExceptionHandler.throwJMSRuntimeException(jsre);
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create session
		try {
			session = connection.createSession();
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}

	public JMSContextImpl(ConnectionFactory connectionFactory, ContainerType containerType) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(cf@"+connectionFactory.hashCode()+", "+containerType+")");
		this.containerType=containerType;
		
		// create connection
		try {
			connection = connectionFactory.createConnection();
		} catch (SecurityException e) {
			JMSSecurityRuntimeException jsre = new com.sun.messaging.jms.MQSecurityRuntimeException(e.getMessage(),null,e);
			ExceptionHandler.throwJMSRuntimeException(jsre);
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create session
		try {
			session = connection.createSession();
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}

	public JMSContextImpl(ConnectionFactory connectionFactory, ContainerType containerType, String userName, String password, int sessionMode) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(cf@"+connectionFactory.hashCode()+", "+containerType+
			", "+userName+",, "+sessionMode+")");
		validateSessionMode(sessionMode);
		this.containerType=containerType;
		
		// create connection
		try {
			connection = connectionFactory.createConnection(userName,password);
		} catch (SecurityException e) {
			JMSSecurityRuntimeException jmssre = new JMSSecurityRuntimeException(e.getMessage(),null,e);
			throw jmssre;
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create session
		try {
			session = connection.createSession(sessionMode);
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}

	public JMSContextImpl(ConnectionFactory connectionFactory, ContainerType containerType, int sessionMode) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(cf@"+connectionFactory.hashCode()+", "+containerType+", "+sessionMode+")");
		validateSessionMode(sessionMode);
		this.containerType=containerType;
		
		// create connection
		try {
			connection = connectionFactory.createConnection();
		} catch (SecurityException e) {
			throw new JMSSecurityRuntimeException(e.getMessage(),null,e);
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create session
		try {
			session = connection.createSession(sessionMode);
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}
	


	/**
	 * Create a new JMSContextImpl using an existing Connection
	 * 
	 * @param containerType
	 * @param connection
	 * @param sessionMode
	 */
	public JMSContextImpl(ContainerType containerType, Set<JMSContext> contextSet, Connection connection, int sessionMode) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"("+containerType+", contextSet@"+contextSet.hashCode()+
			", connection@"+connection.hashCode()+", "+sessionMode+")");
		validateSessionMode(sessionMode);
		this.containerType=containerType;
		
		// use the specified connection
		this.connection=connection;
		// create session
		try {
			session = connection.createSession(sessionMode);
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForExistingConnection(contextSet);
	}
	
	public JMSContextImpl(XAConnectionFactory xaConnectionFactory, ContainerType containerType, String userName, String password) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(xacf@"+xaConnectionFactory.hashCode()+", "+containerType+", "+userName+",)");
		this.containerType=containerType;
		// create XA connection
		try {
			connection = xaConnectionFactory.createXAConnection(userName,password);
		} catch (SecurityException e) {
			JMSSecurityRuntimeException jsre = new com.sun.messaging.jms.MQSecurityRuntimeException(e.getMessage(),null,e);
			ExceptionHandler.throwJMSRuntimeException(jsre);
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create XA session
		try {
			session = ((XAConnection) connection).createXASession();
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}
	
	public JMSContextImpl(XAConnectionFactory xaConnectionFactory, ContainerType containerType) {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			"(xacf@"+xaConnectionFactory.hashCode()+", "+containerType+")");
		this.containerType=containerType;
		// create XA connection
		try {
			connection = xaConnectionFactory.createXAConnection();
		} catch (SecurityException e) {
			JMSSecurityRuntimeException jsre = new com.sun.messaging.jms.MQSecurityRuntimeException(e.getMessage(),null,e);
			ExceptionHandler.throwJMSRuntimeException(jsre);
		} catch (JMSSecurityException e) {
			throw new MQSecurityRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		// create XA session
		try {
			session = ((XAConnection) connection).createXASession();
		} catch (JMSException e) {
			try {
				connection.close();
			} catch (JMSException e1) {	
			}
			throw new MQRuntimeException(e);	
		}
		initializeForNewConnection();
	}
	
	public JMSContextImpl() {
		contextLogger.fine("JMSContext@"+this.hashCode()+"()");
	}
	
	private void validateSessionMode(int sessionMode) {
		if (sessionMode != JMSContext.AUTO_ACKNOWLEDGE && sessionMode != JMSContext.CLIENT_ACKNOWLEDGE && sessionMode != JMSContext.DUPS_OK_ACKNOWLEDGE
				&& sessionMode != JMSContext.SESSION_TRANSACTED) {
			// "Invalid session mode {0}"
			String errorString = AdministeredObject.cr.getKString(ClientResources.X_INVALID_SESSION_MODE,sessionMode);
			JMSRuntimeException jmsre = new com.sun.messaging.jms.MQRuntimeException(errorString, ClientResources.X_INVALID_SESSION_MODE);
			ExceptionHandler.throwJMSRuntimeException(jmsre);
		}
	}

	/**
	 * Initialize a newly-created JMSContext that has created a new Connection
	 */
	protected void initializeForNewConnection() {
		contextSet = new HashSet<JMSContext>();
		contextSet.add(this);
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			".initializeForNewConnection(): connection@"+
			 connection.hashCode()+", session@"+session.hashCode()+", contextSet@"+
			 contextSet.hashCode()+"("+contextSet.size()+")");
	}
	
	/**
	 * Initialize a newly-created JMSContext that we have created from an existing JMSContext
	 */
	private void initializeForExistingConnection(Set<JMSContext> existingContextSet) {
		int size = 0;            
		int hashcode = 0;
		synchronized(existingContextSet){
			size = existingContextSet.size();
			hashcode = existingContextSet.hashCode();
			contextSet = existingContextSet;
			contextSet.add(this);
		}
	        contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			".initializeForExistingConnection(): connection@"+
			connection.hashCode()+", session@"+session.hashCode()+
			", contextSet@"+hashcode+"("+size+")");
	}

	@Override
	public void dump(PrintStream ps) {
		ps.println ("------ JMSContextImpl dump start ------");
		ps.println("autoStart="+autoStart);
		ps.println("closed="+closed);
		ps.println("containerType="+containerType);
		ps.println("contextSet contains "+contextSet.size()+" JMSContexts");
		ps.println("Here is the Connection:");
		if (connection instanceof Traceable) ((Traceable)connection).dump(ps);
		ps.println("Here is the Session:");
		if (session instanceof Traceable) ((Traceable)session).dump(ps);
		ps.println("Here is the MessageProducer:");
		if (messageProducer instanceof Traceable) ((Traceable)messageProducer).dump(ps);
		ps.println ("------ JMSContextImpl dump end ------");
	}

	@Override
	public JMSContext createContext(int sessionMode) {
		// this method is not permitted in the Java EE web or EJB containers
		if (containerType==ContainerType.JavaEE_Web_or_EJB){
			// "This method may not be called in a Java EE web or EJB container"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
			throw new MQRuntimeException(errorString);
		}
		checkNotClosed();
		disallowSetClientID();
		return new JMSContextImpl(containerType, contextSet, connection, sessionMode);
	}

	@Override
	public JMSProducer createProducer() {
		checkNotClosed();
		disallowSetClientID();
		return new JMSProducerImpl(this);
	}

	@Override
	public String getClientID() {
		checkNotClosed();
		try {
			return connection.getClientID();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void setClientID(String clientID) {
		// this method is not permitted in the Java EE web or EJB containers
		if (containerType==ContainerType.JavaEE_Web_or_EJB){
			// "This method may not be called in a Java EE web or EJB container"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
            JMSRuntimeException jmsre = new com.sun.messaging.jms.MQRuntimeException(errorString,ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
			ExceptionHandler.throwJMSRuntimeException(jmsre);
		}
		
		// may throw JMSRuntimeException
		checkNotClosed();
		// may throw IllegalStateRuntimeException
		checkSetClientIDAllowed();
		// may throw InvalidClientIDRuntimeException
		checkClientID(clientID);
		if (connection instanceof ContextableConnection){
			((ContextableConnection)connection)._setClientIDForContext(clientID);
		} else {
			// for debugging
			throw new RuntimeException("Not yet implemented for "+connection.getClass());
		}

		disallowSetClientID();
	}

	@Override
	public ConnectionMetaData getMetaData() {
		checkNotClosed();

		try {
			return connection.getMetaData();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public ExceptionListener getExceptionListener() {
		checkNotClosed();

		try {
			return connection.getExceptionListener();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void setExceptionListener(ExceptionListener listener) {
		checkNotClosed();
		try {
			connection.setExceptionListener(listener);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		disallowSetClientID();
	}

	@Override
	public void start() {
		checkNotClosed();
		disallowSetClientID();
		try {
			connection.start();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void stop() {
		checkNotClosed();
		disallowSetClientID();

		try {
			connection.stop();
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e); 		
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void setAutoStart(boolean autoStart) {
		checkNotClosed();
		disallowSetClientID();

		this.autoStart=autoStart;
	}

	@Override
	public boolean getAutoStart() {
		checkNotClosed();

		return autoStart;
	}

	@Override
	public void close() {
		contextLogger.log(Level.FINE, "JMSContext@"+this.hashCode()+
			".close(): connection@"+(connection == null ? "null":connection.hashCode()));
		if (closed) return;
		closed=true;

		// close all JMSConsumer objects associated with this JMSContext 
		for (JMSConsumer consumer : consumers) consumer.close();
		if (!consumers.isEmpty()) throw new RuntimeException("Debug: consumers not empty");
		
		// close the anonymous MessageProducer associated with this JMSContext 
		if (messageProducer!=null){
			try {
				messageProducer.close();
				messageProducer=null;
			} catch (IllegalStateException e) {
				throw new MQIllegalStateRuntimeException(e);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
		
		// close the Session
		try {
			session.close();
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		
		// if this is the only JMSContext using the connection, close it
		synchronized(contextSet){
			// debug
			if (!contextSet.contains(this)) throw new RuntimeException("I am not in the context set");	
			contextSet.remove(this);
			if (contextSet.contains(this)) throw new RuntimeException("I am not in the context set");	
			
			if (contextSet.isEmpty()){
				try {
					connection.close();
				} catch (IllegalStateException e) {
					throw new MQIllegalStateRuntimeException(e); 		
				} catch (JMSException e) {
					throw new MQRuntimeException(e);
				}
			}
 		}
		
		closed=true;
	}

	@Override
	public BytesMessage createBytesMessage() {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createBytesMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public MapMessage createMapMessage() {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createMapMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public Message createMessage() {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public ObjectMessage createObjectMessage() {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createObjectMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public ObjectMessage createObjectMessage(Serializable object) {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createObjectMessage(object);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public StreamMessage createStreamMessage() {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createStreamMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public TextMessage createTextMessage() {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createTextMessage();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public TextMessage createTextMessage(String text) {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createTextMessage(text);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public boolean getTransacted() {
		checkNotClosed();

		try {
			return session.getTransacted();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public int getSessionMode() {
		checkNotClosed();

		try {
			return session.getAcknowledgeMode();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void commit() {
		checkNotClosed();
		
		disallowSetClientID();
		
		try {
			session.commit();
		} catch (TransactionRolledBackException e) {
			throw new MQTransactionRolledBackRuntimeException(e);
		} catch (TransactionInProgressException e) {
			throw new MQTransactionInProgressRuntimeException(e);
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		
	}

	@Override
	public void rollback() {
		checkNotClosed();
		disallowSetClientID();

		try { 
			session.rollback();
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void recover() {
		checkNotClosed();
		disallowSetClientID();

		try {
			session.recover();
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public JMSConsumer createConsumer(Destination destination) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseConsumer(this, destination);
		addConsumer(consumer);
		return consumer;
	}

	@Override
	public JMSConsumer createConsumer(Destination destination, String messageSelector) {
		checkNotClosed();
		disallowSetClientID();
		JMSConsumerImpl consumer =  new JMSConsumerImpl();
		consumer.initialiseConsumer(this, destination, messageSelector);
		addConsumer(consumer);
		return consumer;
	}

	@Override
	public JMSConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
		checkNotClosed();
		disallowSetClientID();
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseConsumer(this, destination, messageSelector,noLocal);
		addConsumer(consumer);
		return consumer;
	}
	
	@Override
	public Queue createQueue(String queueName) {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createQueue(queueName);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public Topic createTopic(String topicName) {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createTopic(topicName);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public JMSConsumer createDurableConsumer(Topic topic, String name) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseDurableConsumer(this,topic,name);
		addConsumer(consumer);
		return consumer;
	}

	@Override
	public JMSConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseDurableConsumer(this,topic,name,messageSelector,noLocal);
		addConsumer(consumer);
		return consumer;
	}

	@Override
        public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseSharedDurableConsumer(this,topic,name);
		addConsumer(consumer);
		return consumer;
        }

	@Override
        public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseSharedDurableConsumer(this,topic,name,messageSelector);
		addConsumer(consumer);
		return consumer;
        }

	@Override
	public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseSharedConsumer(this,topic,sharedSubscriptionName);
		addConsumer(consumer);
		return consumer;
	}

	@Override
	public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) {
		checkNotClosed();
		disallowSetClientID();	
		JMSConsumerImpl consumer = new JMSConsumerImpl();
		consumer.initialiseSharedConsumer(this,topic,sharedSubscriptionName,messageSelector);
		addConsumer(consumer);
		return consumer;
	}

	@Override
	public QueueBrowser createBrowser(Queue queue) {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createBrowser(queue);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public QueueBrowser createBrowser(Queue queue, String messageSelector) {
		checkNotClosed();
		disallowSetClientID();
		
		try {
			return session.createBrowser(queue, messageSelector);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);	
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public TemporaryQueue createTemporaryQueue() {
		checkNotClosed();
		disallowSetClientID();

		try {
			return session.createTemporaryQueue();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public TemporaryTopic createTemporaryTopic() {
		try {
			return session.createTemporaryTopic();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void unsubscribe(String name) {
		checkNotClosed();

		try {
			session.unsubscribe(name);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);	
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void acknowledge() {
		// check context is not closed
		// don't call checkNotClosed() as that throws the wrong exception
		if (closed){
			// "JMSContext is closed"
	        String errorString = AdministeredObject.cr.getKString(ClientResources.X_JMSCONTEXT_CLOSED);
			throw new IllegalStateRuntimeException(errorString,ClientResources.X_JMSCONTEXT_CLOSED);
		}
			
		// If the session is transacted or has an acknowledgement mode of
		// AUTO_ACKNOWLEDGE or DUPS_OK_ACKNOWLEDGE calling this method has no
		// effect.
		try {
			if (session.getAcknowledgeMode()!=Session.CLIENT_ACKNOWLEDGE) return;
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		
		// Note that if a DirectSession is created with Session.CLIENT_ACKNOWLEDGE
		// then the sessionMode will have been overridden to Session.AUTO_ACKNOWLEDGE, 
		// so the preceding code should have caused this method to exit
		
		disallowSetClientID();
				
		if (session instanceof ContextableSession){
			try {
				((ContextableSession)session).clientAcknowledge();
			} catch (IllegalStateException e) {
				throw new MQIllegalStateRuntimeException(e);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		} else {
			// shouldn't happen
			throw new IllegalStateRuntimeException("Session implementation "+session.getClass()+" not yet supported");
		}
	}
	
	private void addConsumer(JMSConsumer consumer){
		consumers.add(consumer);
	}
	
	protected void removeConsumer(JMSConsumer consumer){
		consumers.remove(consumer);
	}
	
    /**
     * Check that setClientID is allowed to be called. 
     * If it isn't, log and throw a IllegalStateRuntimeException.
     */
    private void checkSetClientIDAllowed() {
        boolean throwex = false;
        JMSException ex = null;
        try {
            if (allowToSetClientID == false || connection.getClientID() != null) {
                throwex = true;
            }
        } catch (JMSException e) {
            ex = e;
        }
        if (throwex || ex != null) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_SET_CLIENT_ID);
            IllegalStateRuntimeException isre = new javax.jms.IllegalStateRuntimeException
                      (errorString, ClientResources.X_SET_CLIENT_ID, ex);
            ExceptionHandler.throwJMSRuntimeException(isre);
        }
    }
    
    /**
     * Check that the specified clientID is not null or an empty string. 
     * If it is, log and throw a InvalidClientIDRuntimeException.
     */
    protected void checkClientID(String clientID) {
        if ( clientID == null || (clientID.trim().length() == 0) ) {
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_INVALID_CLIENT_ID, "\"\"");
            InvalidClientIDRuntimeException jmse = new javax.jms.InvalidClientIDRuntimeException
                      (errorString, ClientResources.X_INVALID_CLIENT_ID);
            ExceptionHandler.throwJMSRuntimeException(jmse);
        }
    }
	
    /**
     * Disallow the use of setClientID by applications
     */
    protected void disallowSetClientID() {
        allowToSetClientID = false;
    }
	
	/**
	 * return the anonymous MessageProducer associated with this JMSContext 
	 * 
	 * @return the anonymous MessageProducer associated with this JMSContext 
	 */
	protected MessageProducer getMessageProducer() {
		if (messageProducer==null){
			try {
				messageProducer = session.createProducer(null);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
		return messageProducer;
	}
	
	/**
	 * If the context is closed, throw a JMSRuntimeException
	 */
	protected void checkNotClosed(){
		
		if (closed){
			// "JMSContext is closed"
	        String errorString = AdministeredObject.cr.getKString(ClientResources.X_JMSCONTEXT_CLOSED);
			throw new MQRuntimeException(errorString,ClientResources.X_JMSCONTEXT_CLOSED);
		}
	}

	protected Session _getSession() {
		return session;
	}
	
	public Connection _getConnection(){
		return connection;
	}
	
}
