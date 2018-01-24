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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.InvalidClientIDRuntimeException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidDestinationRuntimeException;
import javax.jms.InvalidSelectorException;
import javax.jms.InvalidSelectorRuntimeException;
import javax.jms.IllegalStateException;
import javax.jms.JMSConsumer;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.sun.messaging.jms.*;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

public class JMSConsumerImpl implements JMSConsumer, Traceable {
	
	JMSContextImpl context;
	MQMessageConsumer messageConsumer;
	boolean closed=false;
			
	protected void initialiseConsumer(JMSContextImpl context, Destination destination) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createConsumer(destination);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}

	protected void initialiseConsumer(JMSContextImpl context, Destination destination, String messageSelector) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createConsumer(destination,messageSelector);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}

	protected void initialiseConsumer(JMSContextImpl context, Destination destination, String messageSelector,
			boolean noLocal) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createConsumer(destination,messageSelector,noLocal);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}
	
	protected void initialiseDurableConsumer(JMSContextImpl context, Topic topic, String name) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createDurableConsumer(topic,name);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}

	protected void initialiseDurableConsumer(JMSContextImpl context, Topic topic, String name, String messageSelector, boolean noLocal) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createDurableConsumer(topic,name,messageSelector,noLocal);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}

	protected void initialiseSharedDurableConsumer(JMSContextImpl context, Topic topic, String name) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer)context._getSession().createSharedDurableConsumer(topic,name);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}

	protected void initialiseSharedDurableConsumer(JMSContextImpl context, Topic topic, String name, String messageSelector) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createSharedDurableConsumer(topic,name,messageSelector);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}
	
	protected void initialiseSharedConsumer(JMSContextImpl context, Topic topic, String sharedSubscriptionName) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createSharedConsumer(topic,sharedSubscriptionName);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);		
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}
	
	protected void initialiseSharedConsumer(JMSContextImpl context, Topic topic, String sharedSubscriptionName, String messageSelector) {
		context.checkNotClosed();
		this.context=context;
		try {
			messageConsumer = (MQMessageConsumer) context._getSession().createSharedConsumer(topic,sharedSubscriptionName,messageSelector);
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (InvalidSelectorException e) {
			throw new MQInvalidSelectorRuntimeException(e);			
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		if (context.getAutoStart()) context.start();
	}
		
	@Override
	public void dump(PrintStream ps) {
		ps.println ("------ JMSConsumerImpl dump start ------");
		ps.println("closed="+closed);
		ps.println("Here is the MessageConsumer:");
		if (messageConsumer instanceof Traceable) ((Traceable)messageConsumer).dump(ps);
		ps.println ("------ JMSConsumerImpl dump end ------");
	}
	
	@Override
	public String getMessageSelector() {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.getMessageSelector();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public MessageListener getMessageListener() throws JMSRuntimeException {
		// this method is not permitted in the Java EE web or EJB containers
		if (context.getContainerType()==ContainerType.JavaEE_Web_or_EJB){
			// "This method may not be called in a Java EE web or EJB container"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
			throw new JMSRuntimeException(errorString);
		}
		
		checkNotClosed();
		context.checkNotClosed();
				
		try {
			return messageConsumer.getMessageListener();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public void setMessageListener(MessageListener listener) throws JMSRuntimeException {
		// this method is not permitted in the Java EE web or EJB containers
		if (context.getContainerType()==ContainerType.JavaEE_Web_or_EJB){
			// "This method may not be called in a Java EE web or EJB container"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
			throw new JMSRuntimeException(errorString);
		}
		
		checkNotClosed();
		context.checkNotClosed();
				
		try {
			messageConsumer.setMessageListener(listener);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public Message receive() {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receive();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public Message receive(long timeout) {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receive(timeout);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public Message receiveNoWait() {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receiveNoWait();
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}

	@Override
	public <T> T receiveBody(Class<T> c) {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receiveBody(c);
		} catch (MessageFormatException mfe) {
			throw new MQMessageFormatRuntimeException(mfe);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
	}

	@Override
	public <T> T receiveBody(Class<T> c, long timeout) {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receiveBody(c,timeout);
		} catch (MessageFormatException mfe) {
			throw new MQMessageFormatRuntimeException(mfe);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
	}
	
	@Override
	public <T> T receiveBodyNoWait(Class<T> c) {
		checkNotClosed();
		context.checkNotClosed();
		try {
			return messageConsumer.receiveBodyNoWait(c);
		} catch (MessageFormatException mfe) {
			throw new MQMessageFormatRuntimeException(mfe);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}
		
	@Override
	public void close() {
		if (closed) return;
		closed=true;
		context.removeConsumer(this);
		try {
			messageConsumer.close();
		} catch (IllegalStateException e) {
			throw new MQIllegalStateRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}
	
	/**
	 * If the JMSConsumer is closed, throw a JMSRuntimeException
	 */
	protected void checkNotClosed(){
		if (closed){
			// "JMSConsumer is closed"
	        String errorString = AdministeredObject.cr.getKString(ClientResources.X_JMSCONSUMER_CLOSED);
			throw new JMSRuntimeException(errorString);
		}
	}
		
}
